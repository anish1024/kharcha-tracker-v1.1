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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kharcha.tracker.ui.auth.AuthState
import com.kharcha.tracker.ui.auth.AuthViewModel
import com.kharcha.tracker.ui.dashboard.AddExpenseViewModel
import com.kharcha.tracker.ui.dashboard.DashboardViewModel
import com.kharcha.tracker.ui.dashboard.EditExpenseViewModel
import com.kharcha.tracker.ui.dashboard.GroupDetailViewModel

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"

    const val GROUP_DETAIL = "group/{groupId}"
    fun groupDetail(groupId: String) = "group/$groupId"

    const val GROUP_ADD_EXPENSE = "group/{groupId}/add-expense"
    fun groupAddExpense(groupId: String) = "group/$groupId/add-expense"

    const val GROUP_EDIT_EXPENSE = "group/{groupId}/expense/{transId}"
    fun groupEditExpense(groupId: String, transId: Int) = "group/$groupId/expense/$transId"
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
            val dashboardViewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = dashboardViewModel,
                onGroupClick = { groupId ->
                    navController.navigate(Routes.groupDetail(groupId))
                },
                onSignOut = { authViewModel.signOut() }
            )
        }
        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val groupDetailViewModel: GroupDetailViewModel = viewModel(
                factory = remember { GroupDetailViewModelFactory(groupId) }
            )
            GroupDetailScreen(
                groupId = groupId,
                viewModel = groupDetailViewModel,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.groupAddExpense(groupId)) },
                onEditExpense = { transId ->
                    navController.navigate(Routes.groupEditExpense(groupId, transId))
                }
            )
        }
        composable(
            route = Routes.GROUP_ADD_EXPENSE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val addExpenseViewModel: AddExpenseViewModel = viewModel(
                factory = remember { AddExpenseViewModelFactory(groupId) }
            )
            AddExpenseScreen(
                viewModel = addExpenseViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.GROUP_EDIT_EXPENSE,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("transId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val transId = backStackEntry.arguments?.getInt("transId") ?: 0
            val editExpenseViewModel: EditExpenseViewModel = viewModel(
                factory = remember { EditExpenseViewModelFactory(groupId, transId) }
            )
            EditExpenseScreen(
                viewModel = editExpenseViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Factory so [GroupDetailViewModel] can carry its [groupId] through the
 * default Compose [viewModel] call. Each back-stack entry has its own
 * store, so one factory instance per entry is correct.
 */
private class GroupDetailViewModelFactory(
    private val groupId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GroupDetailViewModel(groupId) as T
    }
}

/** Same pattern for [AddExpenseViewModel] (Stage 6). */
private class AddExpenseViewModelFactory(
    private val groupId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AddExpenseViewModel(groupId) as T
    }
}

/** Same pattern for [EditExpenseViewModel] (Stage 7a). */
private class EditExpenseViewModelFactory(
    private val groupId: String,
    private val transId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EditExpenseViewModel(groupId, transId) as T
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
