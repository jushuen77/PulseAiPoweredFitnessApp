package com.fitnessapp.pulse.ui.macro

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.remote.model.MealRow
import com.fitnessapp.pulse.data.repository.MacroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class MacroUiState(
    val selectedImage: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: MealRow? = null,
    val isEditingResult: Boolean = false,
    val history: List<MealRow> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val selectedMeal: MealRow? = null,
    val isDetailLoading: Boolean = false,
    val isEditing: Boolean = false,
    val editSaveError: String? = null,
    /** Guards saveResultEdits() against a second tap firing before the first save's id comes back,
     *  which would otherwise insert the same not-yet-saved result twice. */
    val isSavingResult: Boolean = false
)

class MacroViewModel(
    private val repo: MacroRepository = MacroRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacroUiState())
    val uiState: StateFlow<MacroUiState> = _uiState.asStateFlow()

    fun onImageSelected(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                selectedImage = bitmap,
                result = null,
                isEditingResult = false,
                errorMessage = null
            )
        }
    }

    fun clearImage() {
        _uiState.update {
            it.copy(
                selectedImage = null,
                result = null,
                isEditingResult = false,
                errorMessage = null
            )
        }
    }

    fun analyzeFood() {
        val bitmap = _uiState.value.selectedImage ?: return
        val userId = SessionManager.userId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isEditingResult = false) }
            try {
                val bytes = bitmapToJpegBytes(bitmap)
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val result = repo.estimateFood(base64, userId)
                _uiState.update { it.copy(isLoading = false, result = result) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to analyze food"
                    )
                }
            }
        }
    }

    fun loadHistory() {
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoryLoading = true) }
            try {
                val history = repo.getMealHistory(userId)
                _uiState.update { it.copy(history = history, isHistoryLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isHistoryLoading = false) }
            }
        }
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    fun loadMealDetail(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            try {
                val meal = repo.getMealById(id)
                _uiState.update { it.copy(selectedMeal = meal, isDetailLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDetailLoading = false) }
            }
        }
    }

    fun toggleEditMode() = _uiState.update { it.copy(isEditing = !it.isEditing, editSaveError = null) }

    fun photoUrl(path: String): String = repo.mealPhotoUrl(path)

    fun updateSelectedMealField(foodName: String, calories: Int, protein: Int, carbs: Int, fat: Int) {
        _uiState.value.selectedMeal?.let { meal ->
            _uiState.update {
                it.copy(
                    selectedMeal = meal.copy(
                        foodName = foodName,
                        calories = calories,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat
                    )
                )
            }
        }
    }

    fun saveMealEdits() {
        val meal = _uiState.value.selectedMeal ?: return
        viewModelScope.launch {
            try {
                val saved = repo.updateMeal(meal)
                _uiState.update {
                    it.copy(
                        selectedMeal = saved,
                        isEditing = false,
                        editSaveError = null,
                        result = if (it.result?.id == saved.id) saved else it.result,
                        history = it.history.map { m -> if (m.id == saved.id) saved else m }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(editSaveError = e.message ?: "Failed to save changes") }
            }
        }
    }

    fun deleteMeal(onDeleted: () -> Unit) {
        val meal = _uiState.value.selectedMeal ?: return
        val id = meal.id ?: return
        viewModelScope.launch {
            try {
                repo.deleteMeal(meal)
                _uiState.update {
                    val matchesCurrentResult = it.result?.id == id
                    it.copy(
                        result = if (matchesCurrentResult) null else it.result,
                        selectedImage = if (matchesCurrentResult) null else it.selectedImage,
                        isEditingResult = if (matchesCurrentResult) false else it.isEditingResult,
                        history = it.history.filterNot { m -> m.id == id }
                    )
                }
                onDeleted()
            } catch (_: Exception) { }
        }
    }

    fun analyzeFoodByDescription(description: String) {
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isEditingResult = false) }
            try {
                val result = repo.estimateFoodText(description, userId)
                _uiState.update { it.copy(isLoading = false, result = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to analyze") }
            }
        }
    }

    fun toggleResultEditMode() = _uiState.update { it.copy(isEditingResult = !it.isEditingResult, editSaveError = null) }

    fun updateResultField(foodName: String, calories: Int, protein: Int, carbs: Int, fat: Int) {
        _uiState.value.result?.let { meal ->
            _uiState.update {
                it.copy(
                    result = meal.copy(
                        foodName = foodName,
                        calories = calories,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat
                    )
                )
            }
        }
    }

    /**
     * Saves the current [MacroUiState.result] to history: inserts it if not yet saved (id == null,
     * uploading the pending photo first if this came from the photo-analysis flow), else updates
     * the existing row.
     */
    fun saveResultEdits() {
        if (_uiState.value.isSavingResult) return
        val meal = _uiState.value.result ?: return
        _uiState.update { it.copy(isSavingResult = true, editSaveError = null) }
        viewModelScope.launch {
            try {
                val pendingImage = _uiState.value.selectedImage
                val saved = when {
                    meal.id != null -> repo.updateMeal(meal)
                    pendingImage != null -> {
                        val userId = SessionManager.userId ?: run {
                            _uiState.update { it.copy(isSavingResult = false) }
                            return@launch
                        }
                        repo.savePhotoMeal(meal, userId, bitmapToJpegBytes(pendingImage))
                    }
                    else -> repo.saveMeal(meal)
                }
                _uiState.update {
                    val alreadyInHistory = it.history.any { m -> m.id == saved.id }
                    it.copy(
                        result = saved,
                        isEditingResult = false,
                        isSavingResult = false,
                        editSaveError = null,
                        selectedMeal = if (it.selectedMeal?.id == saved.id) saved else it.selectedMeal,
                        history = if (alreadyInHistory) {
                            it.history.map { m -> if (m.id == saved.id) saved else m }
                        } else {
                            listOf(saved) + it.history
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingResult = false, editSaveError = e.message ?: "Failed to save changes") }
            }
        }
    }


}