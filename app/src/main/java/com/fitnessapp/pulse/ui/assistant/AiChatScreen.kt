package com.fitnessapp.pulse.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Line
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage
import com.fitnessapp.pulse.ui.theme.Surface2

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onNavigate: (destination: String, goal: String?) -> Unit = { _, _ -> },
    /** When set, this screen continues that past session instead of starting a fresh one. */
    resumeSessionId: String? = null,
    onSessionDeleted: () -> Unit = {},
    viewModel: AiChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(resumeSessionId) {
        resumeSessionId?.let { viewModel.resumeSession(it) }
    }

    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        val lastIndex = uiState.messages.size - 1 + if (uiState.isLoading) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    LaunchedEffect(uiState.pendingNavigation) {
        uiState.pendingNavigation?.let { destination ->
            onNavigate(destination, uiState.pendingGoal)
            viewModel.clearPendingNavigation()
        }
    }

    Scaffold(
        topBar = {
            if (resumeSessionId != null) {
                PulseTopBar(
                    title = "AI Assistant",
                    onBack = onBack,
                    actionLabel = "Delete",
                    onAction = { showDeleteConfirm = true }
                )
            } else {
                PulseTopBar(
                    title = "AI Assistant",
                    onBack = onBack,
                    actionLabel = "History",
                    onAction = onOpenHistory
                )
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Ask me anything", style = MaterialTheme.typography.titleLarge, color = Paper)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Fitness tips, nutrition advice, or just a general question — I'm here to help.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.messages) { message ->
                        ChatBubble(message)
                    }
                    if (uiState.isLoading) {
                        item { TypingBubble() }
                    }
                }
            }

            uiState.errorMessage?.let {
                Text(
                    it,
                    color = Ember,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message…", color = Muted) },
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Paper,
                        unfocusedTextColor = Paper,
                        focusedBorderColor = Ember,
                        unfocusedBorderColor = Line,
                        cursorColor = Ember
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = uiState.inputText.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Ember, RoundedCornerShape(14.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this conversation?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteSession(onSessionDeleted) }) {
                        Text("Delete", color = Ember)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
internal fun ChatBubble(message: ChatUiMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Ember else Surface2,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            FormattedMessage(
                text = message.text,
                color = if (isUser) Color.White else Paper,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// Matches "**bold**" so the model's markdown renders as real bold instead of literal asterisks.
private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")

// Matches a leading "- ", "* ", "• ", or "1. " list marker to strip before rendering the line as a bullet.
private val bulletPrefixRegex = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s+")

// Matches a leading "#".."######" heading marker.
private val headingPrefixRegex = Regex("^#{1,6}\\s+")

private fun parseInlineMarkdown(line: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    boldRegex.findAll(line).forEach { match ->
        append(line.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        lastIndex = match.range.last + 1
    }
    append(line.substring(lastIndex))
}

/** Renders the model's reply with basic markdown (bold, bullets, headings) turned into real formatting. */
@Composable
private fun FormattedMessage(text: String, color: Color, modifier: Modifier = Modifier) {
    val lines = remember(text) { text.split("\n") }
    Column(modifier = modifier) {
        lines.forEachIndexed { index, rawLine ->
            if (rawLine.isBlank()) {
                if (index != 0 && index != lines.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                return@forEachIndexed
            }
            if (index > 0) Spacer(modifier = Modifier.height(2.dp))

            val heading = headingPrefixRegex.find(rawLine)
            val bullet = bulletPrefixRegex.find(rawLine)
            when {
                heading != null -> Text(
                    parseInlineMarkdown(rawLine.removeRange(heading.range)),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                bullet != null -> Row {
                    Text("•  ", color = color, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        parseInlineMarkdown(rawLine.removeRange(bullet.range)),
                        color = color,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> Text(parseInlineMarkdown(rawLine), color = color, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = Surface2,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Sage, strokeWidth = 2.dp)
            }
        }
    }
}
