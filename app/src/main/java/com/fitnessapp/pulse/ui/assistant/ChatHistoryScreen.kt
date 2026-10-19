package com.fitnessapp.pulse.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.HistoryEmptyState
import com.fitnessapp.pulse.ui.components.HistoryEntryCard
import com.fitnessapp.pulse.ui.components.HistoryLoadingState
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.fitnessapp.pulse.ui.theme.Sage

@Composable
fun ChatHistoryScreen(
    onBack: () -> Unit,
    onSelectSession: (String) -> Unit,
    viewModel: ChatHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSessions() }

    Scaffold(
        topBar = { PulseTopBar(title = "Chat History", onBack = onBack) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isSessionsLoading && uiState.sessions.isEmpty() -> HistoryLoadingState()
                uiState.sessions.isEmpty() -> HistoryEmptyState(
                    icon = Icons.Default.Chat,
                    title = "No past chats yet",
                    message = "Conversations you have with the AI Assistant will show up here."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.sessions) { session ->
                        HistoryEntryCard(
                            icon = Icons.Default.Chat,
                            iconTint = Sage,
                            title = session.title,
                            subtitle = "${session.messageCount} message${if (session.messageCount == 1) "" else "s"}",
                            dateText = formatHistoryDate(session.lastMessageAt),
                            onClick = { onSelectSession(session.sessionId) }
                        )
                    }
                }
            }
        }
    }
}
