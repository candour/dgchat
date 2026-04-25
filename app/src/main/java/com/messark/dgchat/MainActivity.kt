package com.messark.dgchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
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
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
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
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
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
    var textFieldValue by remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    var activeStyles by remember { mutableStateOf(setOf<SpanStyle>()) }
    val context = LocalContext.current
    val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val isConnected by viewModel.isConnected.collectAsState()
    val baseDomain by viewModel.baseDomain.collectAsState()

    var lastPartCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(textFieldValue.annotatedString, baseDomain) {
        val ansiText = AnsiParser.toAnsiString(textFieldValue.annotatedString)
        val limit = MessageUtils.calculateLimit(baseDomain)
        val parts = MessageUtils.splitMessage(ansiText, limit)
        val currentPartCount = parts.size

        if (currentPartCount != lastPartCount) {
            Toast.makeText(context, "Message $currentPartCount", Toast.LENGTH_SHORT).show()
            lastPartCount = currentPartCount
        }
    }

    val boldStyle = remember { SpanStyle(fontWeight = FontWeight.Bold) }
    val italicStyle = remember { SpanStyle(fontStyle = FontStyle.Italic) }
    val underlineStyle = remember { SpanStyle(textDecoration = TextDecoration.Underline) }
    val redStyle = remember { SpanStyle(color = AnsiParser.basicColors[1]) }
    val greenStyle = remember { SpanStyle(color = AnsiParser.basicColors[2]) }
    val blueStyle = remember { SpanStyle(color = AnsiParser.basicColors[4]) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StyleToggleButton(
                label = "B",
                isActive = activeStyles.any { it.fontWeight == FontWeight.Bold },
                onClick = {
                    activeStyles = if (activeStyles.any { it.fontWeight == FontWeight.Bold }) {
                        activeStyles.filter { it.fontWeight == null }.toSet()
                    } else {
                        activeStyles + boldStyle
                    }
                }
            )
            StyleToggleButton(
                label = "I",
                isActive = activeStyles.any { it.fontStyle == FontStyle.Italic },
                onClick = {
                    activeStyles = if (activeStyles.any { it.fontStyle == FontStyle.Italic }) {
                        activeStyles.filter { it.fontStyle == null }.toSet()
                    } else {
                        activeStyles + italicStyle
                    }
                }
            )
            StyleToggleButton(
                label = "U",
                isActive = activeStyles.any { it.textDecoration == TextDecoration.Underline },
                onClick = {
                    activeStyles = if (activeStyles.any { it.textDecoration == TextDecoration.Underline }) {
                        activeStyles.filter { it.textDecoration == null }.toSet()
                    } else {
                        activeStyles + underlineStyle
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            ColorToggleButton(color = AnsiParser.basicColors[1], isActive = activeStyles.any { it.color == redStyle.color }, onClick = {
                activeStyles = if (activeStyles.any { it.color == redStyle.color }) {
                    activeStyles.filter { it.color == Color.Unspecified }.toSet()
                } else {
                    activeStyles.filter { it.color == Color.Unspecified }.toSet() + redStyle
                }
            })
            ColorToggleButton(color = AnsiParser.basicColors[2], isActive = activeStyles.any { it.color == greenStyle.color }, onClick = {
                activeStyles = if (activeStyles.any { it.color == greenStyle.color }) {
                    activeStyles.filter { it.color == Color.Unspecified }.toSet()
                } else {
                    activeStyles.filter { it.color == Color.Unspecified }.toSet() + greenStyle
                }
            })
            ColorToggleButton(color = AnsiParser.basicColors[4], isActive = activeStyles.any { it.color == blueStyle.color }, onClick = {
                activeStyles = if (activeStyles.any { it.color == blueStyle.color }) {
                    activeStyles.filter { it.color == Color.Unspecified }.toSet()
                } else {
                    activeStyles.filter { it.color == Color.Unspecified }.toSet() + blueStyle
                }
            })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val selectionChanged = newValue.selection != textFieldValue.selection
                    val textChanged = newValue.text != textFieldValue.text

                    if (textChanged) {
                        val updatedAnnotatedString = RichTextEditorUtils.updateAnnotatedString(
                            oldValue = textFieldValue,
                            newValue = newValue,
                            activeStyles = activeStyles
                        )
                        textFieldValue = newValue.copy(annotatedString = updatedAnnotatedString)
                    } else {
                        textFieldValue = newValue.copy(annotatedString = textFieldValue.annotatedString)
                    }

                    if (selectionChanged && textFieldValue.selection.collapsed) {
                        val pos = textFieldValue.selection.start
                        if (pos > 0 && pos <= textFieldValue.text.length) {
                            // Extract styles from the character BEFORE the cursor
                            val stylesAtPos = textFieldValue.annotatedString.spanStyles
                                .filter { it.start < pos && it.end >= pos }
                                .map { it.item }

                            val newActiveStyles = mutableSetOf<SpanStyle>()

                            // Check for Bold
                            if (stylesAtPos.any { it.fontWeight == FontWeight.Bold }) {
                                newActiveStyles.add(boldStyle)
                            }
                            // Check for Italic
                            if (stylesAtPos.any { it.fontStyle == FontStyle.Italic }) {
                                newActiveStyles.add(italicStyle)
                            }
                            // Check for Underline
                            if (stylesAtPos.any { it.textDecoration == TextDecoration.Underline }) {
                                newActiveStyles.add(underlineStyle)
                            }
                            // Check for Colors
                            val colorStyle = stylesAtPos.find { it.color != Color.Unspecified }
                            if (colorStyle != null) {
                                newActiveStyles.add(SpanStyle(color = colorStyle.color))
                            }

                            activeStyles = newActiveStyles
                        } else if (pos == 0) {
                            activeStyles = emptySet()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") }
            )
            IconButton(onClick = {
                if (textFieldValue.text.isNotBlank()) {
                    onSendMessage(AnsiParser.toAnsiString(textFieldValue.annotatedString))
                    textFieldValue = TextFieldValue(AnnotatedString(""))
                    activeStyles = emptySet()
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun StyleToggleButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = if (isActive) IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                 else IconButtonDefaults.filledTonalIconButtonColors()
    ) {
        Text(
            text = label,
            style = when(label) {
                "B" -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                "I" -> MaterialTheme.typography.labelLarge.copy(fontStyle = FontStyle.Italic)
                "U" -> MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline)
                else -> MaterialTheme.typography.labelLarge
            }
        )
    }
}

@Composable
fun ColorToggleButton(color: Color, isActive: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(if (isActive) color.copy(alpha = 0.3f) else Color.Transparent, shape = MaterialTheme.shapes.small)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentSettings = viewModel.getSettings()
    var baseDomain by remember { mutableStateOf(currentSettings["base_domain"] as String) }
    var dnsServer by remember { mutableStateOf(currentSettings["dns_server"] as String) }
    var channel by remember { mutableStateOf(currentSettings["channel"] as String) }
    var nickname by remember { mutableStateOf(currentSettings["nickname"] as String) }
    var privKey by remember { mutableStateOf(currentSettings["priv_key"] as String) }
    var showTechLogs by remember { mutableStateOf(currentSettings["show_tech_logs"] as Boolean) }
    var dnsTestResult by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(value = baseDomain, onValueChange = { baseDomain = it }, label = { Text("Base Domain") })
                TextField(value = dnsServer, onValueChange = { dnsServer = it }, label = { Text("DNS Server") })
                TextField(value = channel, onValueChange = { channel = it }, label = { Text("Channel") })
                TextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname") })
                TextField(value = privKey, onValueChange = { privKey = it }, label = { Text("Private Key") })

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
                if (NicknameUtils.isValid(nickname)) {
                    viewModel.updateSettings(baseDomain, dnsServer, channel, nickname, privKey, showTechLogs)
                    onDismiss()
                } else {
                    Toast.makeText(context, "Invalid nick. 32 chars max, no punctuation except -", Toast.LENGTH_LONG).show()
                }
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
