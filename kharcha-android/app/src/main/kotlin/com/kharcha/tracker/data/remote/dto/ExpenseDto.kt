package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `Expense` model (returned by
 * `GET /groups/{group_id}/expenses`).
 *
 * Wire shape (verified against `kharcha_service/models/expense.py`):
 * ```json
 * {
 *   "trans_id": 17,
 *   "group_id": "<uuid>",
 *   "date_time": "<iso-8601>",
 *   "expn_ctgry": "<expense_category>",
 *   "expn_desc": "Groceries",
 *   "expn_amnt": 450.5,
 *   "expn_splt": [{"user_id": "<uuid>", "percentage": 50.0}],
 *   "paid_by": [{"user_id": "<uuid>", "amt": 450.5}],
 *   "expn_bill_screenshot": "<url or empty>",
 *   "expn_payment_screenshot": "<url or empty>",
 *   "status": "<expense_status>"
 * }
 * ```
 *
 * `expn_splt` / `paid_by` entries reference users by id only - name
 * resolution is a Stage 6 concern.
 */
data class ExpenseDto(
    @SerializedName("trans_id")
    val transId: Int,
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("date_time")
    val dateTime: String? = null,
    @SerializedName("expn_ctgry")
    val category: String? = null,
    @SerializedName("expn_desc")
    val description: String? = null,
    @SerializedName("expn_amnt")
    val amount: Double,
    @SerializedName("expn_splt")
    val sharesRaw: List<ExpenseShareDto>? = null,
    @SerializedName("paid_by")
    val paymentsRaw: List<ExpensePaymentDto>? = null,
    @SerializedName("expn_bill_screenshot")
    val billScreenshot: String? = null,
    @SerializedName("expn_payment_screenshot")
    val paymentScreenshot: String? = null,
    @SerializedName("status")
    val status: String? = null
) {
    /**
     * [sharesRaw] or an empty list. Plain Gson does not apply Kotlin
     * default values (this class has no no-arg constructor), so an
     * absent `expn_splt` arrives as `null` rather than `emptyList()`.
     */
    val shares: List<ExpenseShareDto>
        get() = sharesRaw.orEmpty()

    /** [paymentsRaw] or an empty list (same Gson caveat as [shares]). */
    val payments: List<ExpensePaymentDto>
        get() = paymentsRaw.orEmpty()
}

/** One member's share of an expense (`expn_splt` entry). */
data class ExpenseShareDto(
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("percentage")
    val percentage: Double
)

/** One member's payment towards an expense (`paid_by` entry). */
data class ExpensePaymentDto(
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("amt")
    val amount: Double
)
