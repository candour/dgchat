package com.messark.dgchat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

data class ChatMessage(
    val id: Long? = null,
    val timestamp: Long,
    val type: Char,
    val content: String,
    val isMe: Boolean = false
)

class ChatClient(
    val baseDomain: String,
    val dnsServer: String?,
    private val clientPrivateKey: ByteArray,
    private val clientPublicKey: ByteArray
) {
    private val dnsHelper = DnsHelper(dnsServer)
    private var serverPublicKey: ByteArray? = null
    private var uid: Long = 0
    private var channelName: String? = null
    private var channelPublicKey: ByteArray? = null
    private var channelPrivateKey: ByteArray? = null
    private var channelMsgPrivateKey: ByteArray? = null
    private var namePrivateKey: ByteArray? = null

    private var clientCipherKey: ByteArray? = null
    private var channelCipherKey: ByteArray? = null

    private var lastMilli: Long = 0
    var lastMessageId: Long = 0

    private val _messages = MutableSharedFlow<ChatMessage>()
    val messages: SharedFlow<ChatMessage> = _messages

    private val _technicalLogs = MutableSharedFlow<String>()
    val technicalLogs: SharedFlow<String> = _technicalLogs

    private val seenIds = LinkedHashSet<Long>()
    private val MAX_SEEN_IDS = 1000

    private suspend fun technicalLog(message: String) {
        _technicalLogs.emit(message)
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val (response, _) = dnsHelper.queryTxt(baseDomain) { technicalLog(it) }
        if (response.isEmpty()) {
            throw Exception("Empty TXT response from $baseDomain")
        }

        val responseStr = response.toString(Charsets.UTF_8)
        technicalLog("DNS Response: $responseStr")
        val parts = responseStr.split(" ")
        val pubKeyEncoded = parts[0]
        serverPublicKey = Base32Crockford.decode(pubKeyEncoded)

        clientCipherKey = Crypto.ecdh(clientPrivateKey, serverPublicKey!!)
        true
    }

    suspend fun join(channel: String, nickname: String): Boolean = withContext(Dispatchers.IO) {
        channelName = if (channel.startsWith("#")) channel else "#$channel"

        // PBKDF2 to derive name private key
        val namePrivBytes = Crypto.pbkdf2(
            channelName!!.lowercase().toByteArray(),
            serverPublicKey!!,
            4_000_000,
            32
        )
        namePrivateKey = namePrivBytes
        val namePublicKey = Crypto.derivePublicKey(namePrivBytes)

        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)

        val joinMsg = ByteBuffer.allocate(2 + nickname.length + 32)
            .put("1J".toByteArray())
            .put(nickname.toByteArray())
            .put(namePublicKey)
            .array()

        val encrypted = Crypto.encryptGcm(clientCipherKey!!, nonce, joinMsg)

        val query = "${makeDnsLabels(Base32Crockford.encode(encrypted))}.${Base32Crockford.encode(nonce)}.${Base32Crockford.encode(clientPublicKey)}.$baseDomain"

        technicalLog("DNS Join Query Length: ${query.length}")
        val (response, _) = dnsHelper.queryTxt(query) { technicalLog(it) }
        technicalLog("DNS Join Response size: ${response.size}")

        if (response.isEmpty()) {
            throw Exception("Join failed: No DNS response. This likely means the nickname is already taken or the key is invalid for this nickname.")
        }

        if (response.size <= 12) throw Exception("Invalid join response size: ${response.size}")

        val respNonce = response.sliceArray(0 until 12)
        val respCiphertext = response.sliceArray(12 until response.size)

        val plaintext = Crypto.decryptGcm(clientCipherKey!!, respNonce, respCiphertext)
        technicalLog("DNS Join plaintext size: ${plaintext.size}")
        if (plaintext.size < 76) throw Exception("Invalid join plaintext size: ${plaintext.size}")

        val buffer = ByteBuffer.wrap(plaintext).order(ByteOrder.BIG_ENDIAN)
        uid = buffer.getLong()
        channelPublicKey = ByteArray(32).also { buffer.get(it) }
        channelPrivateKey = ByteArray(32).also { buffer.get(it) }
        val chGen = buffer.getInt()

        technicalLog("Join success: uid=$uid, chGen=$chGen")

        channelCipherKey = Crypto.ecdh(channelPrivateKey!!, serverPublicKey!!)

        val newGen = if (chGen == 0) 1 else chGen
        val msgPrivBytes = Crypto.hkdf(channelPrivateKey!!, namePrivateKey!!, "msg $newGen", 32)
        channelMsgPrivateKey = msgPrivBytes
        technicalLog("Derived channelMsgPrivateKey for gen $newGen")

        if (chGen == 0) {
            val msgPrivPub = Crypto.derivePublicKey(channelMsgPrivateKey!!)
            send('O', "+K ${Base32Crockford.encode(msgPrivPub)}")
        }

        true
    }

    suspend fun send(msgType: Char, message: String): Long = withContext(Dispatchers.IO) {
        try {
            val authPayload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(uid).array()
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)

            val encryptedAuth = Crypto.encryptGcm(channelCipherKey!!, nonce, authPayload)

            var milli = System.currentTimeMillis()
            if (milli <= lastMilli) {
                milli = lastMilli + 1
            }
            lastMilli = milli

            val msgBytes = message.toByteArray()
            val payload = ByteBuffer.allocate(8 + 1 + msgBytes.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(milli)
                .put(msgType.code.toByte())
                .put(msgBytes)
                .array()

            val encryptedMessage = Crypto.encryptGcm(clientCipherKey!!, nonce, payload)

            val query = "${makeDnsLabels(Base32Crockford.encode(encryptedMessage))}.${Base32Crockford.encode(nonce)}.${Base32Crockford.encode(encryptedAuth)}.$baseDomain"

            technicalLog("DNS Send Query Length: ${query.length}")
            val (_, cname) = dnsHelper.queryTxt(query) { technicalLog(it) }
            if (cname == null) {
                technicalLog("DNS Send Response: No CNAME")
                return@withContext 0L
            }

            val idStr = cname.substringBefore(".")
            technicalLog("DNS Send Response CNAME: $cname")
            val id = idStr.toLongOrNull() ?: 0L
            id
        } catch (e: Exception) {
            Log.e("ChatClient", "Send failed", e)
            0L
        }
    }

    private suspend fun processMessage(id: Long, encrypted: ByteArray, decryptKey: ByteArray): Boolean {
        val nonce = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .putLong(id)
            .array()

        try {
            val plaintext = Crypto.decryptGcm(decryptKey, nonce, encrypted)
            if (plaintext.size >= 9) {
                val buffer = ByteBuffer.wrap(plaintext).order(ByteOrder.BIG_ENDIAN)
                val ts = buffer.getLong()
                val msgType = buffer.get().toInt().toChar()
                val content = plaintext.sliceArray(9 until plaintext.size).toString(Charsets.UTF_8)

                if (id !in seenIds) {
                    seenIds.add(id)
                    if (seenIds.size > MAX_SEEN_IDS) {
                        val first = seenIds.iterator().next()
                        seenIds.remove(first)
                    }
                    lastMessageId = max(lastMessageId, id)
                    _messages.emit(ChatMessage(id, ts, msgType, content))
                    return true
                }
            }
        } catch (e: Exception) {
            // Decrypt failed
        }
        return false
    }

    suspend fun fetchMessage(id: Long): Boolean {
        val channelPubEncoded = Base32Crockford.encode(channelPublicKey!!)
        val decryptKey = Crypto.ecdh(channelMsgPrivateKey!!, serverPublicKey!!)
        val query = "$id.$channelPubEncoded.$baseDomain"
        val (message, _) = dnsHelper.queryTxt(query) { technicalLog(it) }
        if (message.isNotEmpty()) {
            return processMessage(id, message, decryptKey)
        }
        return false
    }

    suspend fun pollMessages() = withContext(Dispatchers.IO) {
        val channelPubEncoded = Base32Crockford.encode(channelPublicKey!!)
        val decryptKey = Crypto.ecdh(channelMsgPrivateKey!!, serverPublicKey!!)

        var backoff = 1000L
        var firstPoll = true

        while (true) {
            try {
                val query = "$channelPubEncoded.$baseDomain"
                val (message, cname) = dnsHelper.queryTxt(query) { technicalLog(it) }

                if (message.isNotEmpty() && cname != null) {
                    technicalLog("DNS Poll Response CNAME: $cname")
                    val idStr = cname.substringBefore(".")
                    val newId = idStr.toLongOrNull() ?: continue

                    if (firstPoll) {
                        val startId = max(1L, newId - 19)
                        for (id in startId until newId) {
                            fetchMessage(id)
                        }
                        firstPoll = false
                    } else if (newId > lastMessageId + 1) {
                        val startId = if ((newId - lastMessageId) > 20) {
                            newId - 19
                        } else {
                            lastMessageId + 1
                        }
                        for (id in startId until newId) {
                            fetchMessage(id)
                        }
                    }

                    val processed = processMessage(newId, message, decryptKey)

                    if (!processed && newId <= lastMessageId) {
                        backoff = min(32000L, backoff + 1000L)
                    } else {
                        backoff = 1000L
                    }
                } else {
                    backoff = min(32000L, backoff + 1000L)
                }
            } catch (e: Exception) {
                Log.e("ChatClient", "Poll failed", e)
                backoff = min(32000L, backoff + 1000L)
            }
            delay(backoff)
        }
    }
}
