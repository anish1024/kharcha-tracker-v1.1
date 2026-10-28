package com.kharcha.tracker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kharcha.tracker.ui.auth.AuthState
import com.kharcha.tracker.ui.auth.AuthViewModel

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
}

/**
 * Auth-driven navigation.
 *
 * While a stored session is being restored ([AuthState.Authenticating]) a
 * splash screen is shown. Once the state resolves, the host starts on the
 * matching screen, and any later transition (login, sign-out, session
 * rejected by the server) is applied by the [LaunchedEffect] below using
 * `popUpTo(0)` so exactly one entry is on the back stack at all times.
 */
@Composable
fun AppNavHost(
    authState: AuthState,
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated ->
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }

            is AuthState.Unauthenticated ->
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }

            AuthState.Authenticating -> Unit
        }
    }

    if (authState is AuthState.Authenticating) {
        SplashScreen()
        return
    }

    val startDestination =
        if (authState is AuthState.Authenticated) Routes.DASHBOARD else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(viewModel = authViewModel)
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onSignOut = { authViewModel.signOut() })
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Kharcha Tracker",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}
