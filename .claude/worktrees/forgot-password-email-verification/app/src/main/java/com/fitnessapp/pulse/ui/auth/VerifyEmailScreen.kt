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
import androidx.compose.material.icons.filled.Numbers
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
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    onBack: () -> Unit,
    viewModel: VerifyEmailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) onVerified()
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
            Text("Verify your email", style = MaterialTheme.typography.titleLarge, color = Paper)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Enter the code we sent to $email, or resend it below",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            PulseTextField(
                label = "VERIFICATION CODE",
                value = uiState.code,
                onValueChange = viewModel::onCodeChange,
                leadingIcon = Icons.Default.Numbers
            )

            Spacer(modifier = Modifier.height(20.dp))
            ErrorBanner(uiState.errorMessage)
            if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

            AuthPrimaryButton(
                text = "Verify",
                isLoading = uiState.isVerifying,
                onClick = { viewModel.verify(email) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { viewModel.resendCode(email) },
                enabled = !uiState.isResending && uiState.resendCooldownSeconds == 0
            ) {
                val label = if (uiState.resendCooldownSeconds > 0)
                    "Resend code (${uiState.resendCooldownSeconds}s)"
                else
                    "Resend code"
                Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        AuthBackButton(onBack = onBack, modifier = Modifier.padding(top = 12.dp, start = 12.dp))
    }
}
