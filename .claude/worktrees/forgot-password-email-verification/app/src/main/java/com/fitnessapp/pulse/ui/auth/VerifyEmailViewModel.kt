package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerifyEmailUiState(
    val code: String = "",
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val errorMessage: String? = null,
    val isVerified: Boolean = false,
    val resendCooldownSeconds: Int = 0
)

class VerifyEmailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository()

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value, errorMessage = null) }

    fun verify(email: String) {
        if (_uiState.value.code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter the code we emailed you") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
            repo.verifyEmailCode(email, _uiState.value.code)
                .onSuccess { _uiState.update { it.copy(isVerifying = false, isVerified = true) } }
                .onFailure { e -> _uiState.update { it.copy(isVerifying = false, errorMessage = e.message ?: "Verification failed") } }
        }
    }

    fun resendCode(email: String) {
        if (_uiState.value.resendCooldownSeconds > 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, errorMessage = null) }
            repo.sendVerificationCode(email)
                .onSuccess {
                    _uiState.update { it.copy(isResending = false) }
                    startResendCooldown()
                }
                .onFailure { e -> _uiState.update { it.copy(isResending = false, errorMessage = e.message ?: "Couldn't resend code") } }
        }
    }

    private fun startResendCooldown() {
        viewModelScope.launch {
            _uiState.update { it.copy(resendCooldownSeconds = 30) }
            while (_uiState.value.resendCooldownSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(resendCooldownSeconds = it.resendCooldownSeconds - 1) }
            }
        }
    }
}
