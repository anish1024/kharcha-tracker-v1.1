package com.kharcha.tracker.data.repository

import com.kharcha.tracker.core.util.NetworkResult
import com.kharcha.tracker.core.util.safeCall
import com.kharcha.tracker.data.remote.ApiService
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.SettlementPreviewDto
import com.kharcha.tracker.data.remote.dto.UserDto

/**
 * Read-only data access for the dashboard and group-detail screens
 * (Stage 5). Mutations - creating/joining groups, adding expenses -
 * arrive in Stage 6.
 *
 * Session handling is already covered by
 * [com.kharcha.tracker.data.remote.AuthInterceptor]: a 401 triggers a
 * single-flight refresh and one replay; if that fails the interceptor
 * clears the session and emits `sessionInvalidated`, which
 * [com.kharcha.tracker.ui.auth.AuthViewModel] turns into a force
 * sign-out. Nothing here needs extra auth logic.
 */
class DashboardRepository(
    private val api: ApiService
) {

    /** `GET /users/me` - the signed-in profile (name/email/avatar). */
    suspend fun me(): NetworkResult<UserDto> =
        safeCall { api.me() }

    /** `GET /groups` - every group the signed-in user belongs to. */
    suspend fun myGroups(): NetworkResult<List<GroupDto>> =
        safeCall { api.myGroups() }

    /** `GET /groups/{groupId}` - one group (membership enforced server-side). */
    suspend fun group(groupId: String): NetworkResult<GroupDto> =
        safeCall { api.getGroup(groupId) }

    /** `GET /groups/{groupId}/expenses?limit&offset` - recent expenses. */
    suspend fun groupExpenses(
        groupId: String,
        limit: Int = DEFAULT_EXPENSE_LIMIT
    ): NetworkResult<List<ExpenseDto>> =
        safeCall { api.groupExpenses(groupId, limit) }

    /** `GET /groups/{groupId}/settle` - net dues per member. */
    suspend fun settlementPreview(groupId: String): NetworkResult<SettlementPreviewDto> =
        safeCall { api.settlementPreview(groupId) }

    private companion object {
        const val DEFAULT_EXPENSE_LIMIT = 50
    }
}
