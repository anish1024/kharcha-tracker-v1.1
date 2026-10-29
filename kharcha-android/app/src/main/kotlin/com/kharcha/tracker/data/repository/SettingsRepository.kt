package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.ActivatePlanRequest
import com.kharcha.tracker.data.remote.dto.NotificationDto
import com.kharcha.tracker.data.remote.dto.ProfileUpdateRequest
import com.kharcha.tracker.data.remote.dto.SubscriptionStatusDto
import com.kharcha.tracker.data.remote.dto.UserDto

/**
 * Data access for the Stage 7b settings screen: profile editing, the
 * subscription status / mock plan activation, and the notification list.
 *
 * [com.kharcha.tracker.data.repository.AuthRepository.signOut] is the
 * single sign-out path (the settings screen reuses it); there is no
 * logout endpoint on the gateway.
 */
class SettingsRepository(
    private val api: ApiService
) {

    /** `GET /users/me` - the signed-in profile (name/email/avatar). */
    suspend fun me(): NetworkResult<UserDto> =
        safeCall { api.me() }

    /**
     * `PATCH /users/me` - edit name / avatar (email is read-only). Absent
     * (null) values are left unchanged server-side; the server rejects a
     * blank `name` with 422 (the UI validates it inline first).
     */
    suspend fun updateProfile(
        name: String?,
        avatarUrl: String?
    ): NetworkResult<UserDto> =
        safeCall { api.updateProfile(ProfileUpdateRequest(name, avatarUrl)) }

    /** `GET /users/me/subscription` - trial + plan status. */
    suspend fun subscription(): NetworkResult<SubscriptionStatusDto> =
        safeCall { api.subscriptionStatus() }

    /**
     * `POST /subscription` - mock payment: activates [plan] and returns the
     * fresh status (same shape as [subscription]; the screen swaps it in).
     * The server rejects invalid plans with 422.
     */
    suspend fun activatePlan(plan: String): NetworkResult<SubscriptionStatusDto> =
        safeCall { api.activatePlan(ActivatePlanRequest(plan)) }

    /** `GET /users/me/notifications?unread_only=` - newest first. */
    suspend fun notifications(unreadOnly: Boolean = false): NetworkResult<List<NotificationDto>> =
        safeCall { api.notifications(unreadOnly) }

    /** `POST /users/me/notifications/read` - returns the count marked. */
    suspend fun markAllNotificationsRead(): NetworkResult<Int> =
        safeCall { api.markNotificationsRead() }
}
