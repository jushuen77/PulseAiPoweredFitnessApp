package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onLoginSuccess()
    }
    LaunchedEffect(uiState.verificationEmail) {
        uiState.verificationEmail?.let(onNeedsVerification)
    }

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
        Text("Welcome back", style = MaterialTheme.typography.titleLarge, color = Paper)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Log in to keep your streak going",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        PulseTextField(
            label = "USERNAME",
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            leadingIcon = Icons.Default.Person
        )
        Spacer(modifier = Modifier.height(14.dp))
        PulseTextField(
            label = "PASSWORD",
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            isPassword = true,
            leadingIcon = Icons.Default.Lock
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.rememberMe,
                onCheckedChange = viewModel::onRememberMeChange,
                colors = CheckboxDefaults.colors(checkedColor = Ember)
            )
            Text("Remember me", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNavigateToForgotPassword) {
                Text("Forgot password?", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ErrorBanner(uiState.errorMessage)
        if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

        AuthPrimaryButton(
            text = "Log In",
            isLoading = uiState.isLoading,
            onClick = { viewModel.signIn() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToSignUp) {
            Text(
                "Don't have an account? Sign up",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
