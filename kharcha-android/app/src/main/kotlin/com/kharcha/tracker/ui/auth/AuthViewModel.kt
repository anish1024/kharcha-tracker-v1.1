package com.kharcha.tracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.data.remote.NetworkModule
import com.kharcha.tracker.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the app-level auth state and the login form state.
 *
 * No-arg constructor (default [ViewModel] factory): the network stack is a
 * process-wide singleton initialised in [com.kharcha.tracker.MainActivity]
 * before first use.
 */
class AuthViewModel : ViewModel() {

    private val repository = AuthRepository(
        api = NetworkModule.api,
        tokenStore = NetworkModule.tokens
    )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Authenticating)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    init {
        // The server invalidated the session while we were signed in (401
        // with a failed refresh, or a rejected session): force the UI back
        // to the login screen - but never clobber a more specific message a
        // restore/login failure already produced.
        viewModelScope.launch {
            repository.sessionInvalidated.collect {
                if (_authState.value !is AuthState.Unauthenticated) {
                    _authState.value = AuthState.Unauthenticated()
                }
                if (_loginState.value !is LoginUiState.Error) {
                    _loginState.value = LoginUiState.Idle
                }
            }
        }
    }

    /** Restores the persisted session on app start. */
    fun restoreSession() {
        viewModelScope.launch {
            _authState.value = AuthState.Authenticating
            when (val result = repository.restoreSession()) {
                is NetworkResult.Success ->
                    _authState.value = AuthState.Authenticated(user = result.data)
                is NetworkResult.Error ->
                    _authState.value = AuthState.Unauthenticated(errorMessage = result.message)
                else ->
                    _authState.value = AuthState.Unauthenticated()
            }
        }
    }

    /** Logs in with email/password; fetches the profile on success. */
    fun login(email: String, password: String) {
        if (_loginState.value is LoginUiState.Loading) return
        _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = repository.login(email, password)) {
                is NetworkResult.Error ->
                    _loginState.value = LoginUiState.Error(result.message)

                is NetworkResult.Success -> {
                    // Best-effort profile fetch. A 401 here means the server
                    // rejected the session right after login: stay on the
                    // login screen with the server's message. Any other
                    // failure still counts as signed in (profile null until
                    // Stage 5).
                    val me = repository.currentUser()
                    if (me is NetworkResult.Error && me.code == 401) {
                        _loginState.value = LoginUiState.Error(me.message)
                    } else {
                        _loginState.value = LoginUiState.Idle
                        _authState.value = AuthState.Authenticated(
                            user = (me as? NetworkResult.Success)?.data
                        )
                    }
                }

                else ->
                    _loginState.value = LoginUiState.Error("Unexpected error")
            }
        }
    }

    /** Signs the user out (client-side - the backend has no logout endpoint). */
    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _loginState.value = LoginUiState.Idle
            _authState.value = AuthState.Unauthenticated()
        }
    }

    /** Clears a displayed login error (e.g. before a new attempt). */
    fun clearLoginError() {
        if (_loginState.value is LoginUiState.Error) {
            _loginState.value = LoginUiState.Idle
        }
    }
}
