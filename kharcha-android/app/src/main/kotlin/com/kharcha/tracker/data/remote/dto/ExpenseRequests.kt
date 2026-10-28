package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for `POST /expenses`.
 *
 * Wire shape (verified against the expense normalisation helpers in
 * `kharcha_gateway/app/main.py`):
 * - `group_id` (required) and `expn_amnt` (must be > 0)
 * - `expn_desc` / `expn_ctgry` optional - omitted when null
 * - Payer: `paid_by_user_id` marks that member as paying the full amount.
 *   Omitted (null) means the signed-in actor pays the full amount.
 * - Split: `expn_splt` (a list of [ExpenseShareDto] with `user_id` +
 *   `percentage`) for a percentage split. Omitted (null) means the server
 *   splits the amount EQUALLY across all current group members - the
 *   gateway's default when no split information is supplied.
 *
 * Gson omits null fields on serialise, so "not set" and "not sent" are
 * indistinguishable here - which is exactly the contract the gateway
 * expects (its defaults kick in only for absent keys).
 */
data class AddExpenseRequest(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("expn_amnt")
    val amount: Double,
    @SerializedName("expn_desc")
    val description: String? = null,
    @SerializedName("expn_ctgry")
    val category: String? = null,
    @SerializedName("paid_by_user_id")
    val paidByUserId: String? = null,
    @SerializedName("expn_splt")
    val shares: List<ExpenseShareDto>? = null
)
