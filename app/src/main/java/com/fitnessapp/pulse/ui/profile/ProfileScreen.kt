package com.fitnessapp.pulse.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage
import com.fitnessapp.pulse.ui.theme.Surface2

@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToQnA: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onLogout: () -> Unit,
    viewModel: EditProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Spacer(modifier = Modifier.height(20.dp))

        PulseCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 48.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        uiState.displayName.takeIf { it.isNotBlank() } ?: "Pulse User",
                        style = MaterialTheme.typography.titleMedium,
                        color = Paper
                    )
                    Text(
                        SessionManager.username ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfileMenuItem("Edit Profile", Icons.Default.Person, onNavigateToEditProfile)
        ProfileMenuItem("Settings", Icons.Default.Settings, onNavigateToSettings)
        ProfileMenuItem("History", Icons.Default.History, onNavigateToHistory)
        ProfileMenuItem("Q&A", Icons.Default.HelpOutline, onNavigateToQnA)
        ProfileMenuItem("Terms & Privacy", Icons.Default.Description, onNavigateToTerms)

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Ember),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Log Out")
        }
    }
}

@Composable
private fun ProfileMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    PulseCard(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(icon, Sage, size = 36.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Paper)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}