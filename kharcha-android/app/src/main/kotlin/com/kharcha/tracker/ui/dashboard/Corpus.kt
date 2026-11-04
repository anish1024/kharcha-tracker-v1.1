package com.kharcha.tracker.ui.dashboard

/**
 * Pure corpus calculator derived from the settlement preview `GET /groups/{id}/settle`.
 *
 * Formula: corpus = SUM(SETTLED orphan payouts) + SUM(max(0, net_dues[user]))
 * The two terms are disjoint: orphan payouts never enter net_dues.
 */
data class CorpusTotals(
    val orphanTotal: Double = 0.0,
    val overpaidTotal: Double = 0.0,
    val total: Double = 0.0
)

fun corpusFromPreview(
    preview: com.kharcha.tracker.data.remote.dto.SettlementPreviewDto
): CorpusTotals {
    val orphanTotal = preview.orphanPayouts
        .filter { it.status == "SETTLED" }
        .sumOf { it.amount }

    val overpaidTotal = preview.netDues
        .mapNotNull { (_, value) -> if (value > 0) value else null }
        .sum()

    return CorpusTotals(
        orphanTotal = orphanTotal,
        overpaidTotal = overpaidTotal,
        total = orphanTotal + overpaidTotal
    )
}