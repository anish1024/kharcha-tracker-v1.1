package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.local.TokenStore
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.LoginRequest
import com.kharcha.tracker.data.remote.dto.UserDto
import kotlinx.coroutines.flow.SharedFlow

/**
 * Business rules around the auth endpoints: when tokens are persisted, when
 * they are cleared, and when a 401 means "the session is gone".
 * [com.kharcha.tracker.ui.auth.AuthViewModel] stays thin and delegates here.
 */
class AuthRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore
) {

    /** Emitted when the stored session was cleared because the server rejected it. */
    val sessionInvalidated: SharedFlow<Unit>
        get() = tokenStore.sessionInvalidated

    /**
     * `POST /auth/login`. On success the session is persisted; on failure
     * nothing is written and the server's message (e.g. "Invalid
     * credentials") is preserved in the [NetworkResult.Error].
     */
    suspend fun login(email: String, password: String): NetworkResult<AuthTokens> {
        val result = safeCall { api.login(LoginRequest(email.trim(), password)) }
        if (result is NetworkResult.Success) {
            tokenStore.save(result.data)
        }
        return result
    }

    /**
     * `GET /users/me`. A 401 here means the server no longer accepts the
     * session: stored tokens are cleared and [sessionInvalidated] is emitted
     * so the UI force-signs-out.
     */
    suspend fun currentUser(): NetworkResult<UserDto> {
        val result = safeCall { api.me() }
        if (result is NetworkResult.Error && result.code == 401) {
            forceSignOut()
        }
        return result
    }

    /**
     * Restores a previously stored session (app start):
     * - no stored session -> [NetworkResult.Error] with code 401
     * - stored session    -> [currentUser]; the auth interceptor refreshes an
     *   expired access token transparently (exactly once) before the call
     */
    suspend fun restoreSession(): NetworkResult<UserDto> {
        val session = tokenStore.load()
        if (session == null) {
            return NetworkResult.Error(message = "Not signed in", code = 401)
        }
        return currentUser()
    }

    /** Clears the stored session (user-initiated sign-out). */
    suspend fun signOut() {
        tokenStore.clear()
    }

    /** Clears the session and notifies observers (the server rejected it). */
    private suspend fun forceSignOut() {
        tokenStore.clear()
        tokenStore.emitInvalidated()
    }
}
