package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for `PATCH /users/me`. The gateway reads `name` and
 * `avatar_url` and treats absent values as "no change"
 * (`auth_service.update_profile` only writes the fields that are present).
 */
data class ProfileUpdateRequest(
    @SerializedName("name")
    val name: String?,
    @SerializedName("avatar_url")
    val avatarUrl: String?
)

/**
 * Wire shape of `GET /users/me/subscription` and `POST /subscription`
 * (both return the same object, verified against
 * `subscription_service.get_status` / `activate`):
 * ```json
 * {
 *   "trial_ends_at": "<iso-8601 or null>",
 *   "plan": "none|monthly|6month|yearly|lifetime",
 *   "plan_status": "none|active|...",
 *   "plan_ends_at": "<iso-8601 or null>",
 *   "entries_remaining": null,
 *   "is_limited": false,
 *   "is_expired": false
 * }
 * ```
 * `entries_remaining` is server-computed: null while unlimited, otherwise
 * the remaining free entries after the trial expires.
 */
data class SubscriptionStatusDto(
    @SerializedName("trial_ends_at")
    val trialEndsAt: String? = null,
    @SerializedName("plan")
    val plan: String? = null,
    @SerializedName("plan_status")
    val planStatus: String? = null,
    @SerializedName("plan_ends_at")
    val planEndsAt: String? = null,
    @SerializedName("entries_remaining")
    val entriesRemaining: Int? = null,
    @SerializedName("is_limited")
    val isLimited: Boolean = false,
    @SerializedName("is_expired")
    val isExpired: Boolean = false
)

/**
 * Request body for `POST /subscription` (mock payment). Valid plans:
 * `monthly`, `6month`, `yearly`, `lifetime`; anything else (including
 * `none`) is rejected with 422 "Invalid plan; choose one of: ...".
 */
data class ActivatePlanRequest(
    @SerializedName("plan")
    val plan: String
)

/**
 * Mirrors the backend `Notification` model (returned by
 * `GET /users/me/notifications?unread_only=`; newest first).
 */
data class NotificationDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("payout_id")
    val payoutId: String? = null,
    @SerializedName("kind")
    val kind: String? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null
)
