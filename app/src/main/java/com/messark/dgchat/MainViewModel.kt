package com.messark.dgchat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MessageStatus {
    SENDING, RECEIVED, FAILED
}

sealed class LogEntry {
    abstract val key: String

    data class Message(
        val chatMessage: ChatMessage,
        val status: MessageStatus = MessageStatus.RECEIVED,
        val localId: String = java.util.UUID.randomUUID().toString()
    ) : LogEntry() {
        override val key: String = localId
    }

    data class Technical(
        val timestamp: Long,
        val message: String,
        val id: String = java.util.UUID.randomUUID().toString()
    ) : LogEntry() {
        override val key: String = id
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dgchat_prefs", Context.MODE_PRIVATE)

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _baseDomain = MutableStateFlow(prefs.getString("base_domain", "dg.cx") ?: "dg.cx")
    val baseDomain: StateFlow<String> = _baseDomain.asStateFlow()

    private val _isJoining = MutableStateFlow(false)
    val isJoining: StateFlow<Boolean> = _isJoining.asStateFlow()

    private val _showTechnicalLogs = MutableStateFlow(prefs.getBoolean("show_tech_logs", true))
    val showTechnicalLogs: StateFlow<Boolean> = _showTechnicalLogs.asStateFlow()

    private var chatClient: ChatClient? = null
    private var pollJob: Job? = null
    private var messageCollectionJob: Job? = null
    private var logCollectionJob: Job? = null

    init {
        // Load keys from prefs or generate new ones
        val privKeyHex = prefs.getString("priv_key", null)
        val (priv, pub) = if (privKeyHex == null) {
            val pair = Crypto.generateKeyPair()
            prefs.edit().putString("priv_key", Base32Crockford.encode(pair.first)).apply()
            pair
        } else {
            val priv = Base32Crockford.decode(privKeyHex)
            priv to Crypto.derivePublicKey(priv)
        }

        if (prefs.getString("nickname", null) == null) {
            prefs.edit().putString("nickname", NicknameUtils.generateDefaultNickname()).apply()
        }

        val baseDomain = prefs.getString("base_domain", "dg.cx") ?: "dg.cx"
        val dnsServer = prefs.getString("dns_server", "8.8.8.8")

        initChatClient(baseDomain, dnsServer, priv, pub)
    }

    private fun initChatClient(baseDomain: String, dnsServer: String?, priv: ByteArray, pub: ByteArray) {
        messageCollectionJob?.cancel()
        logCollectionJob?.cancel()

        chatClient = ChatClient(baseDomain, dnsServer, priv, pub)

        messageCollectionJob = viewModelScope.launch {
            chatClient?.messages?.collect { msg ->
                handleIncomingMessage(msg)
            }
        }

        logCollectionJob = viewModelScope.launch {
            chatClient?.technicalLogs?.collect { logMsg ->
                technicalLog(logMsg)
            }
        }
    }

    private fun handleIncomingMessage(msg: ChatMessage) {
        val currentEntries = _logEntries.value.toMutableList()
        val existingIndex = currentEntries.indexOfFirst {
            it is LogEntry.Message && it.chatMessage.id != null && it.chatMessage.id == msg.id
        }

        if (existingIndex != -1) {
            val existingEntry = currentEntries[existingIndex] as LogEntry.Message
            currentEntries[existingIndex] = existingEntry.copy(
                chatMessage = msg.copy(isMe = existingEntry.chatMessage.isMe),
                status = MessageStatus.RECEIVED
            )
        } else {
            // Check if it's one of our own messages that we are currently "sending" or recently sent
            val matchingIndex = currentEntries.indexOfFirst {
                it is LogEntry.Message && it.chatMessage.isMe &&
                        it.chatMessage.content == msg.content && it.chatMessage.type == msg.type &&
                        (it.status == MessageStatus.SENDING || it.chatMessage.id == msg.id)
            }
            if (matchingIndex != -1) {
                val existingEntry = currentEntries[matchingIndex] as LogEntry.Message
                currentEntries[matchingIndex] = existingEntry.copy(
                    chatMessage = msg.copy(isMe = true),
                    status = MessageStatus.RECEIVED
                )
            } else {
                currentEntries.add(LogEntry.Message(msg))
            }
        }
        _logEntries.value = currentEntries

        // Persist last seen ID
        msg.id?.let { id ->
            val channel = prefs.getString("channel", "#test") ?: "#test"
            val lastId = prefs.getLong("last_id_$channel", 0L)
            if (id > lastId) {
                prefs.edit().putLong("last_id_$channel", id).apply()
            }
        }
    }

    private fun technicalLog(message: String) {
        val entry = LogEntry.Technical(System.currentTimeMillis() / 1000, message)
        _logEntries.value = _logEntries.value + entry
    }

    fun connectAndJoin() {
        val channel = prefs.getString("channel", "#test") ?: "#test"
        val nickname = prefs.getString("nickname", "") ?: ""
        val baseDomain = chatClient?.baseDomain ?: "unknown"

        viewModelScope.launch {
            _isJoining.value = true
            technicalLog("Starting connection to $baseDomain...")

            try {
                if (chatClient?.connect() == true) {
                    technicalLog("Connected to server. Joining channel $channel as $nickname...")
                    if (chatClient?.join(channel, nickname) == true) {
                        technicalLog("Successfully joined channel.")

                        // Restore last seen message ID for this channel
                        val lastId = prefs.getLong("last_id_$channel", 0L)
                        chatClient?.lastMessageId = lastId
                        if (lastId > 0) {
                            technicalLog("Restored last message ID: $lastId")
                        }

                        _isConnected.value = true
                        pollJob?.cancel()
                        pollJob = viewModelScope.launch {
                            chatClient?.pollMessages()
                        }
                    }
                } else {
                    technicalLog("Connection failed: Unknown error")
                }
            } catch (e: Exception) {
                technicalLog("Error: ${e.message}\n${android.util.Log.getStackTraceString(e)}")
            } finally {
                _isJoining.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val nickname = prefs.getString("nickname", "") ?: ""
        val baseDomain = chatClient?.baseDomain ?: "dg.cx"
        val limit = MessageUtils.calculateLimit(baseDomain)
        val parts = MessageUtils.splitMessage(text, limit)

        viewModelScope.launch {
            parts.forEach { part ->
                val displayContent = if (nickname.isNotEmpty()) "$nickname: $part" else part
                val chatMsg = ChatMessage(
                    timestamp = System.currentTimeMillis() / 1000,
                    type = 'M',
                    content = displayContent,
                    isMe = true
                )
                val entry = LogEntry.Message(chatMsg, MessageStatus.SENDING)
                _logEntries.value = _logEntries.value + entry

                val id = chatClient?.send('M', part) ?: 0L
                if (id == 0L) {
                    updateMessageStatus(entry.localId, MessageStatus.FAILED)
                } else {
                    updateMessageId(entry.localId, id)
                }
            }
        }
    }

    private fun updateMessageStatus(localId: String, status: MessageStatus) {
        val currentEntries = _logEntries.value.toMutableList()
        val index = currentEntries.indexOfFirst { it is LogEntry.Message && it.localId == localId }
        if (index != -1) {
            currentEntries[index] = (currentEntries[index] as LogEntry.Message).copy(status = status)
            _logEntries.value = currentEntries
        }
    }

    private fun updateMessageId(localId: String, id: Long) {
        val currentEntries = _logEntries.value.toMutableList()
        val index = currentEntries.indexOfFirst { it is LogEntry.Message && it.localId == localId }
        if (index != -1) {
            val msgEntry = currentEntries[index] as LogEntry.Message
            currentEntries[index] = msgEntry.copy(chatMessage = msgEntry.chatMessage.copy(id = id))
            _logEntries.value = currentEntries

            // Also persist last seen ID
            val channel = prefs.getString("channel", "#test") ?: "#test"
            val lastId = prefs.getLong("last_id_$channel", 0L)
            if (id > lastId) {
                prefs.edit().putLong("last_id_$channel", id).apply()
            }
        }
    }

    fun updateSettings(baseDomain: String, dnsServer: String, channel: String, nickname: String, privKeyHex: String, showTechLogs: Boolean) {
        prefs.edit()
            .putString("base_domain", baseDomain)
            .putString("dns_server", dnsServer)
            .putString("channel", channel)
            .putString("nickname", nickname)
            .putString("priv_key", privKeyHex)
            .putBoolean("show_tech_logs", showTechLogs)
            .apply()

        _showTechnicalLogs.value = showTechLogs
        _baseDomain.value = baseDomain

        // Re-init client
        val priv = Base32Crockford.decode(privKeyHex)
        val pub = Crypto.derivePublicKey(priv)

        initChatClient(baseDomain, dnsServer, priv, pub)
        _isConnected.value = false
        _logEntries.value = emptyList()
        pollJob?.cancel()
    }

    fun getSettings() = mapOf(
        "base_domain" to (prefs.getString("base_domain", "dg.cx") ?: "dg.cx"),
        "dns_server" to (prefs.getString("dns_server", "8.8.8.8") ?: "8.8.8.8"),
        "channel" to (prefs.getString("channel", "#test") ?: "#test"),
        "nickname" to (prefs.getString("nickname", "") ?: ""),
        "priv_key" to (prefs.getString("priv_key", "") ?: ""),
        "show_tech_logs" to prefs.getBoolean("show_tech_logs", true)
    )

    fun testDns(baseDomain: String, dnsServer: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                if (baseDomain.isBlank()) {
                    false
                } else {
                    val helper = DnsHelper(dnsServer)
                    val (resp, _) = helper.queryTxt(baseDomain)
                    resp.isNotEmpty()
                }
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                callback(success)
            }
        }
    }
}
