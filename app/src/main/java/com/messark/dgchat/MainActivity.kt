package com.messark.dgchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
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
                val logEntries by viewModel.logEntries.collectAsState()
                val isConnected by viewModel.isConnected.collectAsState()
                val isJoining by viewModel.isJoining.collectAsState()
                val showTechnicalLogs by viewModel.showTechnicalLogs.collectAsState()
                var showSettings by remember { mutableStateOf(false) }

                val filteredEntries = remember(logEntries, showTechnicalLogs) {
                    if (showTechnicalLogs) logEntries else logEntries.filter { it is LogEntry.Message }
                }

                val reversedEntries = remember(filteredEntries) {
                    filteredEntries.asReversed()
                }

                val listState = rememberLazyListState()
                LaunchedEffect(reversedEntries.size) {
                    if (reversedEntries.isNotEmpty()) {
                        if (listState.firstVisibleItemIndex <= 1) {
                            listState.animateScrollToItem(0)
                        }
                    }
                }

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
                            .imePadding()
                            .fillMaxSize()
                    ) {
                        if (!isConnected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isJoining) {
                                    CircularProgressIndicator()
                                } else {
                                    Button(onClick = { viewModel.connectAndJoin() }) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            reverseLayout = true
                        ) {
                            items(reversedEntries, key = { it.key }) { entry ->
                                LogEntryItem(entry)
                            }
                        }

                        if (isConnected) {
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
fun LogEntryItem(entry: LogEntry) {
    when (entry) {
        is LogEntry.Message -> MessageItem(entry)
        is LogEntry.Technical -> TechnicalLogItem(entry)
    }
}

@Composable
fun MessageItem(entry: LogEntry.Message) {
    val message = entry.chatMessage
    val statusText = when (entry.status) {
        MessageStatus.SENDING -> " (sending)"
        MessageStatus.FAILED -> " (failed)"
        MessageStatus.RECEIVED -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (message.isMe) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${message.type} ${
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(message.timestamp * 1000))
                    }$statusText",
                    style = MaterialTheme.typography.labelSmall
                )
                if (message.id != null) {
                    Text(
                        text = "ID: ${message.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            SelectionContainer {
                Text(text = AnsiParser.parseAnsi(message.content))
            }
        }
    }
}

@Composable
fun TechnicalLogItem(entry: LogEntry.Technical) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        SelectionContainer {
            Text(
                text = "[${
                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(entry.timestamp * 1000))
                }] ${entry.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    var baseDomain by remember { mutableStateOf(currentSettings["base_domain"] as String) }
    var dnsServer by remember { mutableStateOf(currentSettings["dns_server"] as String) }
    var channel by remember { mutableStateOf(currentSettings["channel"] as String) }
    var nickname by remember { mutableStateOf(currentSettings["nickname"] as String) }
    var showTechLogs by remember { mutableStateOf(currentSettings["show_tech_logs"] as Boolean) }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show Technical Logs")
                    Switch(checked = showTechLogs, onCheckedChange = { showTechLogs = it })
                }

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
                viewModel.updateSettings(baseDomain, dnsServer, channel, nickname, showTechLogs)
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
