package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.AuthPreferences
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.repository.AuthRepository
import com.fitnessapp.pulse.data.repository.SignInResult
import com.fitnessapp.pulse.util.isValidEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val verificationEmail: String? = null
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
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    fun onRememberMeChange(value: Boolean) = _uiState.update { it.copy(rememberMe = value) }

    fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repo.signIn(_uiState.value.username, _uiState.value.password)) {
                is SignInResult.Success -> {
                    AuthPreferences.setRememberMe(getApplication(), _uiState.value.rememberMe)
                    SessionManager.login(getApplication(), result.user.uid, result.user.username, _uiState.value.rememberMe)
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is SignInResult.NeedsVerification -> {
                    _uiState.update { it.copy(isLoading = false, verificationEmail = result.email) }
                }
                is SignInResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun signUp() {
        if (_uiState.value.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a username") }
            return
        }
        if (!isValidEmail(_uiState.value.email)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email") }
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
            repo.signUp(_uiState.value.username, _uiState.value.email, _uiState.value.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, verificationEmail = _uiState.value.email.trim()) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign up failed") } }
        }
    }
}
