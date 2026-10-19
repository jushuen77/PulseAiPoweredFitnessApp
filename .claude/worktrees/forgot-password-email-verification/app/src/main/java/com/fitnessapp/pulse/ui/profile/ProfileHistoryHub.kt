package com.fitnessapp.pulse.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun ProfileHistoryHub(onBack: () -> Unit) {
    Scaffold(
        topBar = { PulseTopBar(title = "Profile History", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Text("Your History", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Workout, meal, and check-in history are available directly from each feature tab's History button.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}