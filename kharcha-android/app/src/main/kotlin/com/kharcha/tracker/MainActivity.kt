package com.kharcha.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kharcha.tracker.data.local.ThemeMode
import com.kharcha.tracker.data.local.ThemePreferenceStore
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.ui.auth.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must happen before any composable that touches the network stack.
        NetworkModule.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()

            // The theme choice (settings screen) is applied app-wide and
            // live: writing it republishes this flow, re-resolving the
            // scheme below.
            val themeStore = remember { ThemePreferenceStore(applicationContext) }
            val themeMode by themeStore.theme.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Restores a stored session once per process start (splash until
            // it resolves).
            LaunchedEffect(Unit) {
                authViewModel.restoreSession()
            }

            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(authState = authState, authViewModel = authViewModel)
                }
            }
        }
    }
}
