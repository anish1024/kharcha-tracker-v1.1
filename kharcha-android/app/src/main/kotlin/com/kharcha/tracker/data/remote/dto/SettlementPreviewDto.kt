package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `SettlementPreview` model (returned by
 * `GET /groups/{group_id}/settle`).
 *
 * `netDues` maps user id -> net position, with the sign convention
 * documented by `settlement_engine.calculate_net_dues`:
 * **positive = the group owes the user (they are owed), negative = they
 * owe the group, ~0 = settled up.**
 *
 * The payout lists are the settlement's proposed/known payouts:
 * [proposedPayouts] is what a settlement would create (shown before the
 * "Settle group" action); [openPayouts] / [orphanPayouts] are the
 * group's current payout records.
 */
data class SettlementPreviewDto(
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("group_status")
    val groupStatus: String? = null,
    @SerializedName("net_dues")
    val netDues: Map<String, Double> = emptyMap(),
    @SerializedName("open_payouts")
    val openPayoutsRaw: List<PayoutDto>? = null,
    @SerializedName("proposed_payouts")
    val proposedPayoutsRaw: List<PayoutDto>? = null,
    @SerializedName("orphan_payouts")
    val orphanPayoutsRaw: List<PayoutDto>? = null
) {
    /** [openPayoutsRaw] or an empty list (plain Gson skips Kotlin defaults). */
    val openPayouts: List<PayoutDto>
        get() = openPayoutsRaw.orEmpty()

    /** [proposedPayoutsRaw] or an empty list. */
    val proposedPayouts: List<PayoutDto>
        get() = proposedPayoutsRaw.orEmpty()

    /** [orphanPayoutsRaw] or an empty list. */
    val orphanPayouts: List<PayoutDto>
        get() = orphanPayoutsRaw.orEmpty()
}
