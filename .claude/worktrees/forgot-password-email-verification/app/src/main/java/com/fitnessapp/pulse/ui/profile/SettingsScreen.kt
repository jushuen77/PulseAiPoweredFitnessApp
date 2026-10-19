package com.fitnessapp.pulse.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var notificationsEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { PulseTopBar(title = "Settings", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Spacer(modifier = Modifier.height(20.dp))

            PulseCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notifications", color = Paper, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Sage)
                    )
                }
            }
        }
    }
}

@Composable
fun QnAScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { PulseTopBar(title = "QnA Screen", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Text("Q&A", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Spacer(modifier = Modifier.height(16.dp))
            PulseCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Is Pulse a medical app?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Paper
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "No. Pulse's AI features are informational estimates and wellness support only, not medical, dietary, or clinical advice.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TermsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { PulseTopBar(title = "Terms Screen", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Text("Terms & Privacy", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Spacer(modifier = Modifier.height(16.dp))
            PulseCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Pulse is a student project built for AMIT3353. Data you enter is stored locally on your device and sent to OpenAI's API solely to generate the requested AI response.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}