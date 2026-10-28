package com.kharcha.tracker.data.remote

import com.kharcha.tracker.data.remote.dto.AddExpenseRequest
import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.CreateGroupRequest
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.InviteAcceptRequest
import com.kharcha.tracker.data.remote.dto.InviteCodeResponse
import com.kharcha.tracker.data.remote.dto.LoginRequest
import com.kharcha.tracker.data.remote.dto.MagicLinkRequest
import com.kharcha.tracker.data.remote.dto.MagicLinkResponse
import com.kharcha.tracker.data.remote.dto.MagicLinkVerifyRequest
import com.kharcha.tracker.data.remote.dto.RefreshRequest
import com.kharcha.tracker.data.remote.dto.SettlementPreviewDto
import com.kharcha.tracker.data.remote.dto.SignupRequest
import com.kharcha.tracker.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the kharcha gateway (FastAPI, base path `/api/v1`).
 *
 * Endpoint contracts verified against `kharcha_gateway/app/main.py`:
 * - `POST /auth/login`          -> 200 `Base` ([AuthTokens])
 * - `POST /auth/refresh`        -> 200 `Base` ([AuthTokens]; refresh token is single-use, rotated)
 * - `POST /auth/signup`         -> 201 `User` ([UserDto])
 * - `POST /auth/magic-link`     -> 202 `{magic_token, ...}` ([MagicLinkResponse])
 * - `POST /auth/magic-link/verify` -> 200 `Base` ([AuthTokens])
 * - `GET  /users/me`            -> 200 `User` ([UserDto]; requires `Authorization: Bearer`)
 * - `GET  /groups`              -> 200 `List[Group]`
 * - `GET  /groups/{id}`         -> 200 `Group`
 * - `GET  /groups/{id}/expenses` -> 200 `List[Expense]`
 * - `GET  /groups/{id}/settle`  -> 200 `SettlementPreview`
 *
 * Stage 6 mutations:
 * - `POST   /groups`                 -> 201 `Group` ([GroupDto])
 * - `POST   /groups/{id}/invite`     -> 200 `{invite_code}` ([InviteCodeResponse])
 * - `POST   /groups/{id}/invite/accept` -> 200 `Group` (404 on a bad code)
 * - `GET    /groups/{id}/members`    -> 200 `List[{id,name,email,avatar_url}]`
 * - `DELETE /groups/{id}/members/me` -> 200 `Group` (422 while the balance is open)
 * - `POST   /expenses`               -> 201 `Expense` ([ExpenseDto])
 */
interface ApiService {

    @POST("${ApiConstants.API_VERSION}/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthTokens>

    @POST("${ApiConstants.API_VERSION}/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthTokens>

    @POST("${ApiConstants.API_VERSION}/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<UserDto>

    /** Passwordless login, step 1: request a single-use magic token for the email. */
    @POST("${ApiConstants.API_VERSION}/auth/magic-link")
    suspend fun requestMagicLink(@Body request: MagicLinkRequest): Response<MagicLinkResponse>

    /** Passwordless login, step 2: exchange the magic token for a session. */
    @POST("${ApiConstants.API_VERSION}/auth/magic-link/verify")
    suspend fun verifyMagicLink(@Body request: MagicLinkVerifyRequest): Response<AuthTokens>

    @GET("${ApiConstants.API_VERSION}/users/me")
    suspend fun me(): Response<UserDto>

    /** Groups the signed-in user belongs to (membership enforced server-side). */
    @GET("${ApiConstants.API_VERSION}/groups")
    suspend fun myGroups(): Response<List<GroupDto>>

    @GET("${ApiConstants.API_VERSION}/groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: String): Response<GroupDto>

    /** Recent expenses of a group; the server clamps `limit` to 1..1000. */
    @GET("${ApiConstants.API_VERSION}/groups/{groupId}/expenses")
    suspend fun groupExpenses(
        @Path("groupId") groupId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<ExpenseDto>>

    /** Net dues per member: positive = owed, negative = owes (see [SettlementPreviewDto]). */
    @GET("${ApiConstants.API_VERSION}/groups/{groupId}/settle")
    suspend fun settlementPreview(@Path("groupId") groupId: String): Response<SettlementPreviewDto>

    // ---- Stage 6: group mutations -----------------------------------------

    /** `POST /groups` - create a group; the caller becomes its first member. */
    @POST("${ApiConstants.API_VERSION}/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<GroupDto>

    /** `POST /groups/{groupId}/invite` - derive this member's invite code. */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/invite")
    suspend fun generateInviteCode(@Path("groupId") groupId: String): Response<InviteCodeResponse>

    /**
     * `POST /groups/{groupId}/invite/accept` - join the group with a valid
     * code (any current member's derived code is accepted; 404 otherwise).
     */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/invite/accept")
    suspend fun acceptInvite(
        @Path("groupId") groupId: String,
        @Body request: InviteAcceptRequest
    ): Response<GroupDto>

    /** `GET /groups/{groupId}/members` - member profiles (id + name + contact). */
    @GET("${ApiConstants.API_VERSION}/groups/{groupId}/members")
    suspend fun groupMembers(@Path("groupId") groupId: String): Response<List<GroupMemberDto>>

    /**
     * `DELETE /groups/{groupId}/members/me` - leave the group. The server
     * refuses with 422 while the caller has an outstanding balance or open
     * payouts, or the group is settled.
     */
    @DELETE("${ApiConstants.API_VERSION}/groups/{groupId}/members/me")
    suspend fun leaveGroup(@Path("groupId") groupId: String): Response<GroupDto>

    /**
     * `POST /expenses` - top-level expense creation. `group_id` scopes it;
     * an omitted split means the server splits equally across all members,
     * and an omitted payer means the caller pays the full amount.
     */
    @POST("${ApiConstants.API_VERSION}/expenses")
    suspend fun addExpense(@Body request: AddExpenseRequest): Response<ExpenseDto>

    @GET("${ApiConstants.API_VERSION}/health")
    suspend fun healthCheck(): Response<Map<String, String>>
}
