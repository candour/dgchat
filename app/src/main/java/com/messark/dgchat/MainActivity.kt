package com.messark.dgchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.messark.dgchat.ui.theme.DgChatTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DgChatTheme {
                val messages by viewModel.messages.collectAsState()
                val isConnected by viewModel.isConnected.collectAsState()
                val isJoining by viewModel.isJoining.collectAsState()
                val connectionLog by viewModel.connectionLog.collectAsState()
                var showSettings by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Dg Chat") },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        if (!isConnected) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (isJoining) {
                                    CircularProgressIndicator()
                                } else {
                                    Button(onClick = { viewModel.connectAndJoin() }) {
                                        Text("Connect")
                                    }
                                }

                                if (connectionLog.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SelectionContainer {
                                        Text(
                                            text = connectionLog,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState()),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(messages) { message ->
                                    MessageItem(message)
                                }
                            }

                            ChatInput(onSendMessage = { viewModel.sendMessage(it) })
                        }
                    }

                    if (showSettings) {
                        SettingsDialog(
                            onDismiss = { showSettings = false },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${message.type} ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(message.timestamp * 1000))}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(text = message.content)
        }
    }
}

@Composable
fun ChatInput(onSendMessage: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") }
        )
        IconButton(onClick = {
            if (text.isNotBlank()) {
                onSendMessage(text)
                text = ""
            }
        }) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, viewModel: MainViewModel) {
    val currentSettings = viewModel.getSettings()
    var baseDomain by remember { mutableStateOf(currentSettings["base_domain"] ?: "") }
    var dnsServer by remember { mutableStateOf(currentSettings["dns_server"] ?: "") }
    var channel by remember { mutableStateOf(currentSettings["channel"] ?: "") }
    var nickname by remember { mutableStateOf(currentSettings["nickname"] ?: "") }
    var dnsTestResult by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = baseDomain, onValueChange = { baseDomain = it }, label = { Text("Base Domain") })
                TextField(value = dnsServer, onValueChange = { dnsServer = it }, label = { Text("DNS Server") })
                TextField(value = channel, onValueChange = { channel = it }, label = { Text("Channel") })
                TextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname") })

                Button(onClick = {
                    viewModel.testDns(baseDomain, dnsServer) { success ->
                        dnsTestResult = success
                    }
                }) {
                    Text("Test DNS")
                }
                dnsTestResult?.let {
                    Text(if (it) "DNS Test Success!" else "DNS Test Failed", color = if (it) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateSettings(baseDomain, dnsServer, channel, nickname)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
