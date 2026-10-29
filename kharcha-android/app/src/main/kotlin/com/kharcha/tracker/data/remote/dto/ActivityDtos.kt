package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `AuditLog` model (returned by
 * `GET /groups/{group_id}/activity`, which also sends the `X-Total-Count`
 * response header for pagination).
 *
 * Wire shape (verified against
 * `storage_service/schema/audit_log.py` + `base_record.py`):
 * ```json
 * {
 *   "id": "<uuid>",
 *   "event": "CREATE|UPDATE|DELETE|LOCK|UNLOCK|SETTLE|CANCEL|RECOVER",
 *   "entity_type": "group|expense|user|payout|audit_log",
 *   "entity_id": "<uuid>",
 *   "group_id": "<uuid>",
 *   "action": "<specific action>",
 *   "field_name": "<field modified, if any>",
 *   "old_value": "<serialized old value>",
 *   "new_value": "<serialized new value>",
 *   "actor_user_id": "<uuid>",
 *   "actor_name": "<display name>",
 *   "actor_ip": "<ip>",
 *   "actor_user_agent": "<ua>",
 *   "reason": "<reason, if any>",
 *   "error_code": "<error code, if any>",
 *   "is_deleted": false,
 *   "created_by": "<uuid>",
 *   "created_at": "<iso-8601>",
 *   "modified_by": "<uuid>",
 *   "modified_at": "<iso-8601>"
 * }
 * ```
 *
 * UUIDs and datetimes arrive as strings and are kept as strings on the
 * client (same convention as [GroupDto]).
 */
data class ActivityLogDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("event")
    val event: String? = null,
    @SerializedName("entity_type")
    val entityType: String? = null,
    @SerializedName("entity_id")
    val entityId: String? = null,
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("action")
    val action: String? = null,
    @SerializedName("field_name")
    val fieldName: String? = null,
    @SerializedName("old_value")
    val oldValue: String? = null,
    @SerializedName("new_value")
    val newValue: String? = null,
    @SerializedName("actor_user_id")
    val actorUserId: String? = null,
    @SerializedName("actor_name")
    val actorName: String? = null,
    @SerializedName("actor_ip")
    val actorIp: String? = null,
    @SerializedName("actor_user_agent")
    val actorUserAgent: String? = null,
    @SerializedName("reason")
    val reason: String? = null,
    @SerializedName("error_code")
    val errorCode: String? = null,
    @SerializedName("is_deleted")
    val isDeleted: Boolean = false,
    @SerializedName("created_by")
    val createdBy: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("modified_by")
    val modifiedBy: String? = null,
    @SerializedName("modified_at")
    val modifiedAt: String? = null
)
