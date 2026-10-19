package com.fitnessapp.pulse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseTopBar(
    title: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(title, color = Paper) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Paper)
            }
        },
        actions = {
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = Sage)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
    )
}