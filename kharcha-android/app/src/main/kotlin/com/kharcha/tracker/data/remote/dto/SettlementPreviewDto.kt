package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `SettlementPreview` model (returned by
 * `GET /groups/{group_id}/settle`).
 *
 * Only the fields the app needs are mapped; Gson silently ignores the
 * `open_payouts` / `proposed_payouts` / `orphan_payouts` lists.
 *
 * `netDues` maps user id -> net position, with the sign convention
 * documented by `settlement_engine.calculate_net_dues`:
 * **positive = the group owes the user (they are owed), negative = they
 * owe the group, ~0 = settled up.**
 */
data class SettlementPreviewDto(
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("group_status")
    val groupStatus: String? = null,
    @SerializedName("net_dues")
    val netDues: Map<String, Double> = emptyMap()
)
