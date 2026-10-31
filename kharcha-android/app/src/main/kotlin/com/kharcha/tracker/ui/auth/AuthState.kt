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

/**
 * The two login flows the form offers. [Loading] carries one of these so the
 * UI can show a busy indicator on the button that actually started the
 * request (instead of both buttons sharing a single "loading" flag).
 */
enum class LoginFlow {
    PASSWORD,
    EMAIL
}

/** State of the login form itself, independent of the session state. */
sealed class LoginUiState {
    object Idle : LoginUiState()
    /** A request is in flight; [flow] identifies which button started it. */
    data class Loading(val flow: LoginFlow) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
