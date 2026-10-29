package com.kharcha.tracker.data.remote

import com.kharcha.tracker.data.remote.dto.AddExpenseRequest
import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.CreateGroupRequest
import com.kharcha.tracker.data.remote.dto.ExpenseDto
import com.kharcha.tracker.data.remote.dto.GroupDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import com.kharcha.tracker.data.remote.dto.ExpenseUpdateRequest
import com.kharcha.tracker.data.remote.dto.InviteAcceptRequest
import com.kharcha.tracker.data.remote.dto.InviteCodeResponse
import com.kharcha.tracker.data.remote.dto.LoginRequest
import com.kharcha.tracker.data.remote.dto.MagicLinkRequest
import com.kharcha.tracker.data.remote.dto.MagicLinkResponse
import com.kharcha.tracker.data.remote.dto.MagicLinkVerifyRequest
import com.kharcha.tracker.data.remote.dto.PayoutActionRequest
import com.kharcha.tracker.data.remote.dto.PayoutDto
import com.kharcha.tracker.data.remote.dto.RefreshRequest
import com.kharcha.tracker.data.remote.dto.SettleRequest
import com.kharcha.tracker.data.remote.dto.SettlementPreviewDto
import com.kharcha.tracker.data.remote.dto.SignupRequest
import com.kharcha.tracker.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
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
 *
 * Stage 7a (expense edit/delete, settlements & payouts):
 * - `GET    /expenses/{trans_id}`    -> 200 `Expense` ([ExpenseDto])
 * - `PATCH  /expenses/{trans_id}`    -> 200 `Expense` (absent keys = no change)
 * - `DELETE /expenses/{trans_id}`    -> 200 `bool` (422 while the group isn't OPEN)
 * - `POST   /groups/{id}/settle`     -> 200 `List[Payout]` ([PayoutDto]; body `{}` = PERIODIC)
 * - `GET    /groups/{id}/payouts`    -> 200 `List[Payout]`
 * - `POST   /groups/{id}/payouts/{pid}/paid`     -> 200 `Payout` (payor; optional `evidence`)
 * - `POST   /groups/{id}/payouts/{pid}/settled`  -> 200 `Payout` (payee; optional `evidence`)
 * - `POST   /groups/{id}/payouts/{pid}/failed`   -> 200 `Payout` (payor or payee; `reason` required)
 * - `POST   /groups/{id}/payouts/{pid}/cancel`   -> 200 `Payout` (payor or payee; `reason` required)
 * - `POST   /groups/{id}/lock`       -> 200 `Group` (OPEN -> LOCKED)
 * - `POST   /groups/{id}/unlock`     -> 200 `Group` (LOCKED -> OPEN)
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

    // ---- Stage 7a: expense edit/delete, settlements & payouts ---------------

    /** `GET /expenses/{transId}` - one expense (membership enforced server-side). */
    @GET("${ApiConstants.API_VERSION}/expenses/{transId}")
    suspend fun getExpense(@Path("transId") transId: Int): Response<ExpenseDto>

    /**
     * `PATCH /expenses/{transId}` - edit an expense. The gateway treats
     * absent keys as "no change", so only edited fields may be set
     * ([ExpenseUpdateRequest] omits nulls); a 422 comes back when the
     * group isn't OPEN, a payer isn't a member, the paid total doesn't
     * match the amount, or the category is invalid.
     */
    @PATCH("${ApiConstants.API_VERSION}/expenses/{transId}")
    suspend fun updateExpense(
        @Path("transId") transId: Int,
        @Body request: ExpenseUpdateRequest
    ): Response<ExpenseDto>

    /**
     * `DELETE /expenses/{transId}` - soft delete. Refused with 422 while
     * the group isn't OPEN or a PAID/SETTLED payout lists this expense.
     */
    @DELETE("${ApiConstants.API_VERSION}/expenses/{transId}")
    suspend fun deleteExpense(@Path("transId") transId: Int): Response<Boolean>

    /**
     * `POST /groups/{groupId}/settle` - run the group's settlement. An
     * omitted `kind` defaults to `PERIODIC` (works while OPEN or LOCKED;
     * the group status is unchanged for that kind).
     */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/settle")
    suspend fun settleGroup(
        @Path("groupId") groupId: String,
        @Body request: SettleRequest
    ): Response<List<PayoutDto>>

    /** `GET /groups/{groupId}/payouts` - the group's payout records. */
    @GET("${ApiConstants.API_VERSION}/groups/{groupId}/payouts")
    suspend fun groupPayouts(@Path("groupId") groupId: String): Response<List<PayoutDto>>

    /**
     * `POST /groups/{groupId}/payouts/{payoutId}/paid` - payor only;
     * PENDING -> PAID. `evidence` is optional.
     */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/payouts/{payoutId}/paid")
    suspend fun markPayoutPaid(
        @Path("groupId") groupId: String,
        @Path("payoutId") payoutId: String,
        @Body request: PayoutActionRequest
    ): Response<PayoutDto>

    /**
     * `POST /groups/{groupId}/payouts/{payoutId}/settled` - payee only;
     * PENDING (cash/direct) or PAID -> SETTLED. `evidence` is optional.
     */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/payouts/{payoutId}/settled")
    suspend fun confirmPayoutSettled(
        @Path("groupId") groupId: String,
        @Path("payoutId") payoutId: String,
        @Body request: PayoutActionRequest
    ): Response<PayoutDto>

    /**
     * `POST /groups/{groupId}/payouts/{payoutId}/failed` - payor or payee;
     * PAID -> FAILED. `reason` is required (422 when blank).
     */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/payouts/{payoutId}/failed")
    suspend fun failPayout(
        @Path("groupId") groupId: String,
        @Path("payoutId") payoutId: String,
        @Body request: PayoutActionRequest
    ): Response<PayoutDto>

    /**
     * `POST /groups/{groupId}/payouts/{payoutId}/cancel` - payor or payee;
     * PENDING -> CANCELLED. `reason` is required (422 when blank).
     */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/payouts/{payoutId}/cancel")
    suspend fun cancelPayout(
        @Path("groupId") groupId: String,
        @Path("payoutId") payoutId: String,
        @Body request: PayoutActionRequest
    ): Response<PayoutDto>

    /** `POST /groups/{groupId}/lock` - OPEN -> LOCKED (closes the group to changes). */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/lock")
    suspend fun lockGroup(@Path("groupId") groupId: String): Response<GroupDto>

    /** `POST /groups/{groupId}/unlock` - LOCKED -> OPEN (reopens the group). */
    @POST("${ApiConstants.API_VERSION}/groups/{groupId}/unlock")
    suspend fun unlockGroup(@Path("groupId") groupId: String): Response<GroupDto>

    @GET("${ApiConstants.API_VERSION}/health")
    suspend fun healthCheck(): Response<Map<String, String>>
}
