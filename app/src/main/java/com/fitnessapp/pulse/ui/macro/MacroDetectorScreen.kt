package com.fitnessapp.pulse.ui.macro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Gold
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage
import com.fitnessapp.pulse.ui.theme.Surface2
import java.io.File

@Composable
fun MacroDetectorScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToManualEntry: () -> Unit,
    viewModel: MacroViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, it)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            }
            viewModel.onImageSelected(bitmap)
        }
    }

    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val source =
                        android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                viewModel.onImageSelected(bitmap)
            }
        }
    }

    fun createCameraImageUri(context: Context): Uri {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(imagesDir, "meal_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }

    fun launchCamera() {
        val uri = createCameraImageUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Macro Detector",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Paper
                )
                Text(
                    "Snap a meal, get an instant estimate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onNavigateToHistory) {
                Text("History", color = Sage)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PulseCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (uiState.selectedImage != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = uiState.selectedImage!!.asImageBitmap(),
                            contentDescription = "Selected meal photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                        IconButton(
                            onClick = { viewModel.clearImage() },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove photo",
                                tint = Paper
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No photo selected yet", color = Muted)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                launchCamera()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Surface2,
                            contentColor = Paper
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Photo")
                    }
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Surface2,
                            contentColor = Paper
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Choose Photo")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        uiState.errorMessage?.let {
            Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = { viewModel.analyzeFood() },
            enabled = !uiState.isLoading && uiState.selectedImage != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Ember,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text("Analyze Food", style = MaterialTheme.typography.bodyLarge)
            }
        }

        uiState.result?.let { result ->
            Spacer(modifier = Modifier.height(20.dp))
            PulseCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(icon = Icons.Default.Restaurant, tint = Ember)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            result.foodName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Paper,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.toggleResultEditMode() }) {
                            Icon(
                                imageVector = if (uiState.isEditingResult) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (uiState.isEditingResult) "Cancel edit" else "Edit entry",
                                tint = Muted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.isEditingResult) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroStat(Icons.Default.LocalFireDepartment, Ember, "Calories", result.calories.formatMacro(), Modifier.weight(1f))
                            MacroStat(Icons.Default.FitnessCenter, Sage, "Protein", "${result.proteinG.formatMacro()}g", Modifier.weight(1f))
                            MacroStat(Icons.Default.Grain, Gold, "Carbs", "${result.carbsG.formatMacro()}g", Modifier.weight(1f))
                            MacroStat(Icons.Default.Opacity, Muted, "Fat", "${result.fatG.formatMacro()}g", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "AI estimate — not medical or dietary advice",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted
                        )
                        uiState.editSaveError?.let {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (result.id == null) {
                            Button(
                                onClick = { viewModel.saveResultEdits() },
                                enabled = !uiState.isSavingResult,
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

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onNavigateToManualEntry) {
            Text("Or enter manually instead", color = Sage)
        }
    }
}




@Composable
private fun MacroStat(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconChip(icon = icon, tint = tint, size = 32.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}