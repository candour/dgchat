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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dgchat_prefs", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isJoining = MutableStateFlow(false)
    val isJoining: StateFlow<Boolean> = _isJoining.asStateFlow()

    private val _connectionLog = MutableStateFlow("")
    val connectionLog: StateFlow<String> = _connectionLog.asStateFlow()

    private var chatClient: ChatClient? = null
    private var pollJob: Job? = null

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

        val baseDomain = prefs.getString("base_domain", "dg.cx") ?: "dg.cx"
        val dnsServer = prefs.getString("dns_server", "8.8.8.8")

        chatClient = ChatClient(baseDomain, dnsServer, priv, pub)

        viewModelScope.launch {
            chatClient?.messages?.collect { msg ->
                _messages.value = _messages.value + msg
            }
        }
    }

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _connectionLog.value += "[$timestamp] $message\n"
    }

    fun connectAndJoin() {
        val channel = prefs.getString("channel", "#test") ?: "#test"
        val nickname = prefs.getString("nickname", "android_user") ?: "android_user"
        val baseDomain = chatClient?.baseDomain ?: "unknown"

        viewModelScope.launch {
            _isJoining.value = true
            _connectionLog.value = ""
            log("Starting connection to $baseDomain...")

            try {
                if (chatClient?.connect() == true) {
                    log("Connected to server. Joining channel $channel as $nickname...")
                    if (chatClient?.join(channel, nickname) == true) {
                        log("Successfully joined channel.")
                        _isConnected.value = true
                        pollJob?.cancel()
                        pollJob = viewModelScope.launch {
                            chatClient?.pollMessages()
                        }
                    }
                } else {
                    log("Connection failed: Unknown error")
                }
            } catch (e: Exception) {
                log("Error: ${e.message}\n${android.util.Log.getStackTraceString(e)}")
            } finally {
                _isJoining.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            chatClient?.send('M', text)
        }
    }

    fun updateSettings(baseDomain: String, dnsServer: String, channel: String, nickname: String) {
        prefs.edit()
            .putString("base_domain", baseDomain)
            .putString("dns_server", dnsServer)
            .putString("channel", channel)
            .putString("nickname", nickname)
            .apply()

        // Re-init client
        val privKeyHex = prefs.getString("priv_key", null)!!
        val priv = Base32Crockford.decode(privKeyHex)
        val pub = Crypto.derivePublicKey(priv)

        chatClient = ChatClient(baseDomain, dnsServer, priv, pub)
        _isConnected.value = false
        _messages.value = emptyList()
        pollJob?.cancel()
    }

    fun getSettings() = mapOf(
        "base_domain" to (prefs.getString("base_domain", "dg.cx") ?: "dg.cx"),
        "dns_server" to (prefs.getString("dns_server", "8.8.8.8") ?: "8.8.8.8"),
        "channel" to (prefs.getString("channel", "#test") ?: "#test"),
        "nickname" to (prefs.getString("nickname", "android_user") ?: "android_user")
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
