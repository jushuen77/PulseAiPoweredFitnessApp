package com.fitnessapp.pulse.ui.macro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.theme.*

@Composable
fun MealDetailScreen(
    mealId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: MacroViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(mealId) { viewModel.loadMealDetail(mealId) }

    Scaffold(
        topBar = { PulseTopBar(title = "Meal Detail Screen", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (uiState.isDetailLoading) {
                CircularProgressIndicator(color = Ember)
            } else {
                uiState.selectedMeal?.let { meal ->
                    meal.photoPath?.let { path ->
                        AsyncImage(
                            model = viewModel.photoUrl(path),
                            contentDescription = "Meal photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(meal.foodName, style = MaterialTheme.typography.headlineMedium, color = Paper)
                    Spacer(modifier = Modifier.height(16.dp))

                    PulseCard(modifier = Modifier.fillMaxWidth()) {
                        if (uiState.isEditing) {
                            var foodNameText by remember(meal.id) { mutableStateOf(meal.foodName) }
                            var caloriesText by remember(meal.id) { mutableStateOf(meal.calories.toString()) }
                            var proteinText by remember(meal.id) { mutableStateOf(meal.proteinG.toString()) }
                            var carbsText by remember(meal.id) { mutableStateOf(meal.carbsG.toString()) }
                            var fatText by remember(meal.id) { mutableStateOf(meal.fatG.toString()) }
                            val keyboardController = LocalSoftwareKeyboardController.current
                            val dismissKeyboard = KeyboardActions(onDone = { keyboardController?.hide() })

                            Column {
                                OutlinedTextField(
                                    value = foodNameText,
                                    onValueChange = { foodNameText = it },
                                    label = { Text("Food name") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = dismissKeyboard,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MacroNumberField(
                                        label = "Calories",
                                        value = caloriesText,
                                        onValueChange = { caloriesText = it },
                                        keyboardActions = dismissKeyboard,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MacroNumberField(
                                        label = "Protein",
                                        value = proteinText,
                                        onValueChange = { proteinText = it },
                                        keyboardActions = dismissKeyboard,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MacroNumberField(
                                        label = "Carbs",
                                        value = carbsText,
                                        onValueChange = { carbsText = it },
                                        keyboardActions = dismissKeyboard,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MacroNumberField(
                                        label = "Fat",
                                        value = fatText,
                                        onValueChange = { fatText = it },
                                        keyboardActions = dismissKeyboard,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                uiState.editSaveError?.let {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                val hasFieldError = listOf(caloriesText, proteinText, carbsText, fatText)
                                    .any { macroFieldError(it) != null }
                                Button(
                                    onClick = {
                                        viewModel.updateSelectedMealField(
                                            foodName = foodNameText.ifBlank { meal.foodName },
                                            calories = parseMacroField(caloriesText, meal.calories),
                                            protein = parseMacroField(proteinText, meal.proteinG),
                                            carbs = parseMacroField(carbsText, meal.carbsG),
                                            fat = parseMacroField(fatText, meal.fatG)
                                        )
                                        viewModel.saveMealEdits()
                                    },
                                    enabled = !hasFieldError,
                                    colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Paper),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Save Changes") }
                            }
                        } else {
                            Column {
                                Text(
                                    "${meal.calories.formatMacro()} calories",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Paper,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${meal.proteinG.formatMacro()}g protein · ${meal.carbsG.formatMacro()}g carbs · ${meal.fatG.formatMacro()}g fat",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleEditMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text(if (uiState.isEditing) "Cancel" else "Edit Entry") }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Ember),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Delete Entry") }
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete this meal log?") },
                            text = { Text("This can't be undone.") },
                            confirmButton = {
                                TextButton(onClick = { viewModel.deleteMeal(onDeleted) }) {
                                    Text("Delete", color = Ember)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                } ?: Text("Meal not found", color = Muted)
            }
        }
    }
}