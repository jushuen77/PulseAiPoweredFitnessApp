package com.fitnessapp.pulse.ui.macro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.auth.PulseTextField
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.theme.*

@Composable
fun ManualEntryScreen(
    onBack: () -> Unit,
    viewModel: MacroViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var description by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { PulseTopBar(title = "Manual Entry Screen", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Manual Entry", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Text(
                "Describe your meal, e.g. \"grilled chicken with rice\"",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Spacer(modifier = Modifier.height(20.dp))

            PulseTextField(
                label = "FOOD DESCRIPTION",
                value = description,
                onValueChange = { description = it })

            Spacer(modifier = Modifier.height(16.dp))

            uiState.errorMessage?.let {
                Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { viewModel.analyzeFoodByDescription(description) },
                enabled = !uiState.isLoading && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ember,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (uiState.isLoading) CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
                else Text("Estimate Macros")
            }

            uiState.result?.let { result ->
                Spacer(modifier = Modifier.height(20.dp))
                PulseCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        if (result.id == null) {
                            var foodNameText by rememberSaveable(result.id) { mutableStateOf(result.foodName) }
                            var caloriesText by rememberSaveable(result.id) { mutableStateOf(result.calories.toString()) }
                            var proteinText by rememberSaveable(result.id) { mutableStateOf(result.proteinG.toString()) }
                            var carbsText by rememberSaveable(result.id) { mutableStateOf(result.carbsG.toString()) }
                            var fatText by rememberSaveable(result.id) { mutableStateOf(result.fatG.toString()) }
                            val keyboardController = LocalSoftwareKeyboardController.current
                            val dismissKeyboard = KeyboardActions(onDone = { keyboardController?.hide() })

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
                            uiState.editSaveError?.let {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            val hasFieldError = listOf(caloriesText, proteinText, carbsText, fatText)
                                .any { macroFieldError(it) != null }
                            Button(
                                onClick = {
                                    viewModel.updateResultField(
                                        foodName = foodNameText.ifBlank { result.foodName },
                                        calories = parseMacroField(caloriesText, result.calories),
                                        protein = parseMacroField(proteinText, result.proteinG),
                                        carbs = parseMacroField(carbsText, result.carbsG),
                                        fat = parseMacroField(fatText, result.fatG)
                                    )
                                    viewModel.saveResultEdits()
                                },
                                enabled = !hasFieldError && !uiState.isSavingResult,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Ember,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isSavingResult) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Text("Save Changes")
                                }
                            }
                        } else {
                            Text(
                                result.foodName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Paper
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${result.calories.formatMacro()} cal · ${result.proteinG.formatMacro()}g protein · " +
                                    "${result.carbsG.formatMacro()}g carbs · ${result.fatG.formatMacro()}g fat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Sage, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Saved to history", color = Sage, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}