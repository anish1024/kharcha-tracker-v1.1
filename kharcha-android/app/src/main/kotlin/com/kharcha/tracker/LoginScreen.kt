package com.kharcha.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kharcha.tracker.ui.auth.AuthState
import com.kharcha.tracker.ui.auth.AuthViewModel
import com.kharcha.tracker.ui.auth.LoginFlow
import com.kharcha.tracker.ui.auth.LoginUiState

/**
 * Email/password login wired to [AuthViewModel].
 *
 * This screen only renders the form; navigation is driven by the
 * [AuthState] observed in [AppNavHost] (Login -> Dashboard on success,
 * Dashboard -> Login on sign-out or server-side invalidation).
 */
@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val loginState by viewModel.loginState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val loading = loginState as? LoginUiState.Loading
    val isLoading = loading != null
    val passwordFlowLoading = loading?.flow == LoginFlow.PASSWORD
    val emailFlowLoading = loading?.flow == LoginFlow.EMAIL
    val serverError = (loginState as? LoginUiState.Error)?.message
    val sessionNotice = (authState as? AuthState.Unauthenticated)?.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kharcha Tracker",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!sessionNotice.isNullOrBlank()) {
            Text(
                text = sessionNotice,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                localError = null
                if (email.isBlank() || !email.contains("@")) {
                    localError = "Enter a valid email address"
                } else if (password.isBlank()) {
                    localError = "Enter your password"
                } else {
                    viewModel.clearLoginError()
                    viewModel.login(email, password)
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (passwordFlowLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(if (passwordFlowLoading) "Signing in..." else "Login")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                localError = null
                if (email.isBlank() || !email.contains("@")) {
                    localError = "Enter a valid email address"
                } else {
                    viewModel.clearLoginError()
                    viewModel.loginWithEmail(email)
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (emailFlowLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(if (emailFlowLoading) "Signing in..." else "Continue with email")
        }

        val error = localError ?: serverError
        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
