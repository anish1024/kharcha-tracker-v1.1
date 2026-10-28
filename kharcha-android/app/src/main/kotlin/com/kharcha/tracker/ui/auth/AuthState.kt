package com.kharcha.tracker.ui.auth

import com.kharcha.tracker.data.remote.dto.UserDto

/**
 * Top-level auth/session state the UI branches on.
 *
 * - [Authenticating]  - a stored session is being restored on app start
 * - [Authenticated]   - a valid session exists ([user] may be null when
 *                       `/users/me` was unreachable but the session itself
 *                       was accepted)
 * - [Unauthenticated] - no valid session (fresh start, sign-out, or a
 *                       session the server rejected);
 *                       [Unauthenticated.errorMessage] carries a readable
 *                       reason when there is one
 */
sealed class AuthState {
    object Authenticating : AuthState()
    data class Authenticated(val user: UserDto? = null) : AuthState()
    data class Unauthenticated(val errorMessage: String? = null) : AuthState()
}

/** State of the login form itself, independent of the session state. */
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
