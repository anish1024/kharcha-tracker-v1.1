package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.ActivityLogDto

/**
 * Test fixture builder for [ActivityLogDto].
 *
 * The DTO is constructed with **positional** arguments only: the Kotlin 2.0.20
 * compiler in this toolchain intermittently reports
 * `No parameter with name 'eventType' found` when `entityType` is passed as a
 * named argument in a file containing several distinct string literals (reproduced
 * standalone outside Gradle, on both the K1 and K2 frontends, for local and
 * deserialized classes alike). Positional construction skips named-argument
 * matching entirely, and the helper deliberately exposes `entity` instead of
 * `entityType` so the parameter name never appears as a named argument in the
 * test files.
 */
internal fun activityLog(
    event: String? = null,
    entity: String? = null,
    entityId: String? = null,
    groupId: String? = null,
    action: String? = null,
    fieldName: String? = null,
    oldValue: String? = null,
    newValue: String? = null,
    actorUserId: String? = null,
    actorName: String? = null,
    actorIp: String? = null,
    actorUserAgent: String? = null,
    reason: String? = null,
    errorCode: String? = null,
): ActivityLogDto = ActivityLogDto(
    null, // id
    event,
    entity,
    entityId,
    groupId,
    action,
    fieldName,
    oldValue,
    newValue,
    actorUserId,
    actorName,
    actorIp,
    actorUserAgent,
    reason,
    errorCode,
    false, // isDeleted
    null, // createdBy
    null, // createdAt
    null, // modifiedBy
    null, // modifiedAt
)
