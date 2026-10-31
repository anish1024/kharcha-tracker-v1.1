package com.kharcha.tracker.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kharcha.tracker.data.remote.dto.AuthTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authSessionStore by preferencesDataStore(name = "auth_session")

/**
 * The persisted auth session. Mirrors the backend `Base` response
 * ([AuthTokens]) field-for-field; datetimes are stored as the raw
 * ISO-8601 strings the server sent.
 */
data class AuthSession(
    val authToken: String,
    val refreshToken: String?,
    val authUserId: String?,
    val authTokenExpiry: String?,
    val refreshTokenExpiry: String?
)

/**
 * Persists the auth session in Jetpack DataStore (Preferences).
 *
 * Besides the reactive [session] flow, keeps volatile in-memory copies of
 * the tokens so [com.kharcha.tracker.data.remote.AuthInterceptor] can read
 * them synchronously from OkHttp dispatcher threads without blocking on
 * the DataStore. [load] seeds the cache from storage (app start);
 * [save] / [clear] keep it in sync on every change.
 */
class TokenStore(context: Context) {

    private val dataStore = context.authSessionStore

    private val _sessionInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /**
     * Emitted when the client force-invalidated the session because the
     * server rejected it (failed refresh / 401 on an authenticated call)
     * and the stored tokens were cleared. User-initiated sign-out does not
     * emit this event (the UI already knows).
     */
    val sessionInvalidated: SharedFlow<Unit> = _sessionInvalidated

    @Volatile
    private var cachedAuthToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    /** Emits the stored session, or null when signed out. */
    val session: Flow<AuthSession?> = dataStore.data.map { prefs ->
        val token = prefs[KEY_AUTH_TOKEN]
        if (token.isNullOrBlank()) {
            null
        } else {
            AuthSession(
                authToken = token,
                refreshToken = prefs[KEY_REFRESH_TOKEN],
                authUserId = prefs[KEY_AUTH_USER_ID],
                authTokenExpiry = prefs[KEY_AUTH_TOKEN_EXPIRY],
                refreshTokenExpiry = prefs[KEY_REFRESH_TOKEN_EXPIRY]
            )
        }
    }

    /** Reads the stored session and seeds the in-memory cache. */
    suspend fun load(): AuthSession? {
        val current = session.first()
        cachedAuthToken = current?.authToken
        cachedRefreshToken = current?.refreshToken
        return current
    }

    /** Persists a fresh session (login or refresh) and updates the cache. */
    suspend fun save(tokens: AuthTokens) {
        dataStore.edit { prefs ->
            // DataStore's `set` requires non-null values; nulls are stored as
            // empty strings and treated as absent everywhere (isNullOrBlank).
            prefs[KEY_AUTH_TOKEN] = tokens.authToken.orEmpty()
            prefs[KEY_REFRESH_TOKEN] = tokens.refreshToken.orEmpty()
            prefs[KEY_AUTH_USER_ID] = tokens.authUserId.orEmpty()
            prefs[KEY_AUTH_TOKEN_EXPIRY] = tokens.authTokenExpiry.orEmpty()
            prefs[KEY_REFRESH_TOKEN_EXPIRY] = tokens.refreshTokenExpiry.orEmpty()
        }
        cachedAuthToken = tokens.authToken
        cachedRefreshToken = tokens.refreshToken
    }

    /** Clears the stored session (sign-out / forced invalidation). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
        cachedAuthToken = null
        cachedRefreshToken = null
    }

    /** Synchronous read for [com.kharcha.tracker.data.remote.AuthInterceptor]. */
    fun authTokenNow(): String? = cachedAuthToken

    /** Synchronous read for [com.kharcha.tracker.data.remote.AuthInterceptor]. */
    fun refreshTokenNow(): String? = cachedRefreshToken

    /** Fire-and-forget invalidation event (try-buffered, see [sessionInvalidated]). */
    fun emitInvalidated() {
        _sessionInvalidated.tryEmit(Unit)
    }

    private companion object {
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        val KEY_AUTH_TOKEN_EXPIRY = stringPreferencesKey("auth_token_expiry")
        val KEY_REFRESH_TOKEN_EXPIRY = stringPreferencesKey("refresh_token_expiry")
    }
}
