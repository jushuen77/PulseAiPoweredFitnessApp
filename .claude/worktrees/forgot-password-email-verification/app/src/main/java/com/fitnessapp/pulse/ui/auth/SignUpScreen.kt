package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun SignUpScreen(
    onSignUpSuccess: (email: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.verificationEmail) {
        uiState.verificationEmail?.let(onSignUpSuccess)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Create your account", style = MaterialTheme.typography.titleLarge, color = Paper)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Start tracking workouts, meals, and mood",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            PulseTextField(
                label = "USERNAME",
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                leadingIcon = Icons.Default.Person
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "EMAIL",
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                leadingIcon = Icons.Default.Email
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "PASSWORD",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                isPassword = true,
                leadingIcon = Icons.Default.Lock
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "CONFIRM PASSWORD",
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                isPassword = true,
                leadingIcon = Icons.Default.Lock
            )

            Spacer(modifier = Modifier.height(20.dp))
            ErrorBanner(uiState.errorMessage)
            if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

            AuthPrimaryButton(
                text = "Sign Up",
                isLoading = uiState.isLoading,
                onClick = { viewModel.signUp() }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateToLogin) {
                Text("Have an account? Log in", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        AuthBackButton(
            onBack = onNavigateToLogin,
            modifier = Modifier.padding(top = 12.dp, start = 12.dp)
        )
    }
}
