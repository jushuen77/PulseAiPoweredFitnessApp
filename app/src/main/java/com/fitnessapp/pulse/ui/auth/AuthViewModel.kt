package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.AuthPreferences
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repo: AuthRepository = AuthRepository()

    private val _uiState = MutableStateFlow(
        AuthUiState(rememberMe = AuthPreferences.getRememberMe(application))
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    fun onRememberMeChange(value: Boolean) = _uiState.update { it.copy(rememberMe = value) }

    fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.signIn(_uiState.value.username, _uiState.value.password)
                .onSuccess { user ->
                    AuthPreferences.setRememberMe(getApplication(), _uiState.value.rememberMe)
                    SessionManager.login(getApplication(), user.uid, user.username, _uiState.value.rememberMe)
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign in failed") } }
        }
    }

    fun signUp() {
        if (_uiState.value.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a username") }
            return
        }
        if (_uiState.value.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        if (_uiState.value.password != _uiState.value.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords don't match") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.signUp(_uiState.value.username, _uiState.value.password)
                .onSuccess { user ->
                    AuthPreferences.setRememberMe(getApplication(), true)
                    SessionManager.login(getApplication(), user.uid, user.username, remember = true)
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign up failed") } }
        }
    }
}