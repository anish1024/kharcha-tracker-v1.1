package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.headerInt
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.core.util.safeCallWithHeaders
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.ActivityLogDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto

/**
 * Data access for the Stage 7b activity screen: the group picker list,
 * the group's member names (actor resolution fallback) and the paginated
 * audit-log feed.
 *
 * Session handling is already covered by
 * [com.kharcha.tracker.data.remote.AuthInterceptor] (401 -> single-flight
 * refresh + one replay; failure clears the session and emits
 * `sessionInvalidated`, which [com.kharcha.tracker.ui.auth.AuthViewModel]
 * turns into a force sign-out).
 */
class ActivityRepository(
    private val api: ApiService
) {

    /**
     * One page of the audit log plus the server-reported total (the
     * `X-Total-Count` header, which ignores the page offset).
     */
    data class ActivityPage(
        val logs: List<ActivityLogDto>,
        val total: Int
    )

    /** `GET /groups` - the groups the signed-in user belongs to (picker list). */
    suspend fun groups(): NetworkResult<List<GroupDto>> =
        safeCall { api.myGroups() }

    /** `GET /groups/{groupId}/members` - member profiles for actor-name fallback. */
    suspend fun members(groupId: String): NetworkResult<List<GroupMemberDto>> =
        safeCall { api.groupMembers(groupId) }

    /**
     * `GET /groups/{groupId}/activity?limit&offset[&date_from&date_to]`.
     * [pageSize] is the server-side `limit` (clamped to 1..1000 by the
     * gateway); [page] is 0-based.
     */
    suspend fun activity(
        groupId: String,
        page: Int,
        pageSize: Int = PAGE_SIZE,
        dateFrom: String? = null,
        dateTo: String? = null
    ): NetworkResult<ActivityPage> {
        val offset = (page.coerceAtLeast(0)) * pageSize
        return when (val result = safeCallWithHeaders {
            api.groupActivity(groupId, pageSize, offset, dateFrom, dateTo)
        }) {
            is NetworkResult.Success ->
                NetworkResult.Success(
                    ActivityPage(
                        logs = result.data.body,
                        total = result.data.headerInt("X-Total-Count") ?: result.data.body.size
                    )
                )

            is NetworkResult.Error ->
                NetworkResult.Error(message = result.message, code = result.code)

            else ->
                NetworkResult.Error(message = "Unexpected error")
        }
    }

    companion object {
        /** The web UI's `ACTIVITY_PAGE_SIZE`. */
        const val PAGE_SIZE = 20
    }
}
