package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `Payout` model (returned by
 * `GET /groups/{group_id}/payouts`, `POST /groups/{group_id}/settle`
 * and the payout-action endpoints).
 *
 * Wire shape (verified against `kharcha_service/models/payout.py`):
 * ```json
 * {
 *   "id": "<uuid>",
 *   "group_id": "<uuid>",
 *   "payor_id": "<uuid>",
 *   "payee_id": "<uuid>",
 *   "amt": 1200.0,
 *   "status": "PENDING",
 *   "expense_ids": [17, 18],
 *   "is_adhoc": false,
 *   "settlement_round_id": "<uuid or null>",
 *   "replaces_payout_ids": [],
 *   "payor_evidence": "UPI ref ...",
 *   "payee_evidence": null,
 *   "status_reason": "wrong amount",
 *   "paid_at": "<iso-8601 or null>",
 *   "settled_at": "<iso-8601 or null>"
 * }
 * ```
 *
 * `status` is one of `PENDING`, `PAID`, `SETTLED`, `FAILED`, `CANCELLED`
 * (see `constants/payout_status.py` on the gateway).
 */
data class PayoutDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("payor_id")
    val payorId: String? = null,
    @SerializedName("payee_id")
    val payeeId: String? = null,
    @SerializedName("amt")
    val amount: Double,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("expense_ids")
    val expenseIdsRaw: List<Int>? = null,
    @SerializedName("is_adhoc")
    val isAdhoc: Boolean? = null,
    @SerializedName("settlement_round_id")
    val settlementRoundId: String? = null,
    @SerializedName("replaces_payout_ids")
    val replacesPayoutIdsRaw: List<String>? = null,
    @SerializedName("payor_evidence")
    val payorEvidence: String? = null,
    @SerializedName("payee_evidence")
    val payeeEvidence: String? = null,
    @SerializedName("status_reason")
    val statusReason: String? = null,
    @SerializedName("paid_at")
    val paidAt: String? = null,
    @SerializedName("settled_at")
    val settledAt: String? = null
) {
    /** [expenseIdsRaw] or an empty list (plain Gson skips Kotlin defaults). */
    val expenseIds: List<Int>
        get() = expenseIdsRaw.orEmpty()

    /** [replacesPayoutIdsRaw] or an empty list. */
    val replacesPayoutIds: List<String>
        get() = replacesPayoutIdsRaw.orEmpty()
}

/**
 * `POST /groups/{groupId}/settle` body. The gateway reads
 * `kind` (`INDIVIDUAL` | `PERIODIC` | `FINAL`) and defaults to `PERIODIC`
 * when it is absent - so an empty request is a valid "settle the group".
 */
data class SettleRequest(
    @SerializedName("kind")
    val kind: String? = null
)

/**
 * Body for the payout-action endpoints. `evidence` is read by
 * `/paid`, `/settled` and `/failed`; `reason` by `/failed` and `/cancel`
 * (the gateway rejects those two with an empty/blank reason). Unset
 * fields stay null so Gson omits them from the wire.
 */
data class PayoutActionRequest(
    @SerializedName("evidence")
    val evidence: String? = null,
    @SerializedName("reason")
    val reason: String? = null
)

/**
 * `PATCH /expenses/{trans_id}` body (gateway `ExpenseUpdate`).
 *
 * The gateway treats **absent keys as "no change"**, so the request must
 * only carry the fields the user actually edited; nulls are omitted by
 * Gson (an unedited form serializes to `{}`, which the server accepts).
 *
 * - [dateTime] - `yyyy-MM-dd` (or full ISO); the gateway parses it as a
 *   datetime.
 * - [category] - must be a valid `ExpenseCategory` value server-side.
 * - [description] - an empty string clears the description (the service
 *   sets it whenever the key is present, so "no change" means omitted).
 * - [paidBy] - the **full** payment breakdown; the gateway requires the
 *   entries to sum exactly to the expense amount and every payer to be a
 *   member. Reusing [ExpensePaymentDto] (`user_id` / `amt`).
 */
data class ExpenseUpdateRequest(
    @SerializedName("date_time")
    val dateTime: String? = null,
    @SerializedName("expn_ctgry")
    val category: String? = null,
    @SerializedName("expn_desc")
    val description: String? = null,
    @SerializedName("paid_by")
    val paidBy: List<ExpensePaymentDto>? = null
)
