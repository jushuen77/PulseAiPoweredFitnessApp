package com.fitnessapp.pulse.ui.macro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
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
import com.fitnessapp.pulse.ui.theme.Gold

@Composable
fun MealHistoryScreen(
    onBack: () -> Unit,
    viewModel: MacroViewModel = viewModel(),
    onSelectMeal: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    Scaffold(
        topBar = { PulseTopBar(title = "Meal History", onBack = onBack) }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isHistoryLoading && uiState.history.isEmpty() -> HistoryLoadingState()
                uiState.history.isEmpty() -> HistoryEmptyState(
                    icon = Icons.Default.Restaurant,
                    title = "No meals logged yet",
                    message = "Snap or type a meal and it'll show up here."
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.history) { meal ->
                        HistoryEntryCard(
                            icon = Icons.Default.Restaurant,
                            iconTint = Gold,
                            title = meal.foodName,
                            subtitle = "${meal.calories.formatMacro()} cal · ${meal.proteinG.formatMacro()}g protein · " +
                                "${meal.carbsG.formatMacro()}g carbs · ${meal.fatG.formatMacro()}g fat",
                            dateText = formatHistoryDate(meal.loggedAt),
                            imageUrl = meal.photoPath?.let { viewModel.photoUrl(it) },
                            onClick = { meal.id?.let { onSelectMeal(it) } }
                        )
                    }
                }
            }
        }
    }
}