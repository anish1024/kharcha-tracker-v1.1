package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.data.remote.dto.NotificationDto
import com.kharcha.tracker.data.remote.dto.SubscriptionStatusDto

/**
 * Pure display logic for the Stage 7b settings screen, mirroring the web
 * frontend's `app.js` (PLAN_LABELS / planLabel / formatDate / planSummary /
 * shortPlan / renderSubscriptionBanner / NOTIFICATION_LABELS /
 * notificationText) and the plan catalog in `index.html`.
 *
 * Everything is string-based and unit-testable on the JVM (no `java.time`);
 * date rendering delegates to [Format.date], which returns its input
 * unchanged when it is not a parseable ISO date.
 */
object SettingsTexts {

    private val PLAN_LABELS = mapOf(
        "none" to "Free (trial)",
        "monthly" to "Monthly",
        "6month" to "6 Months",
        "yearly" to "Yearly",
        "lifetime" to "Lifetime"
    )

    /** Web `planLabel`: the friendly name, else the raw plan, else a dash. */
    fun planLabel(plan: String?): String =
        PLAN_LABELS[plan] ?: plan?.takeIf { it.isNotBlank() } ?: "—"

    /**
     * Web `formatDate`: null -> "—", unparseable -> the raw value,
     * otherwise `29 Oct 2026` ([Format.date]).
     */
    fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        val formatted = Format.date(iso)
        return if (formatted == iso) iso else formatted
    }

    /** Web `planSummary`, verbatim strings. */
    fun planSummary(status: SubscriptionStatusDto): String {
        if (status.planStatus == "active") {
            val label = planLabel(status.plan)
            return if (status.plan == "lifetime") {
                "$label plan — active, never expires"
            } else {
                "$label plan — active until ${formatDate(status.planEndsAt)}"
            }
        }
        if (status.isExpired) {
            return "Free trial ended — ${status.entriesRemaining ?: 0} free entries left"
        }
        return "Free trial — unlimited until ${formatDate(status.trialEndsAt)}"
    }

    /** Web `shortPlan`: active -> the plan name, expired -> "Expired", else "Trial". */
    fun shortPlan(status: SubscriptionStatusDto): String =
        when {
            status.planStatus == "active" -> planLabel(status.plan)
            status.isExpired -> "Expired"
            else -> "Trial"
        }

    /** Web `renderSubscriptionBanner` text (the banner colour is a UI concern). */
    fun subscriptionBanner(status: SubscriptionStatusDto): String {
        if (status.planStatus == "active") {
            return "Current plan: ${planSummary(status)}."
        }
        if (status.isExpired) {
            return "Your trial has ended. ${status.entriesRemaining ?: 0} " +
                "free entries remaining — choose a plan to keep going."
        }
        return "You're on the free trial — unlimited until ${formatDate(status.trialEndsAt)}."
    }

    // ---- Plan catalog (index.html `#view-subscription`, verbatim) ------------

    /** One purchasable plan (a `plan-grid` card in the web UI). */
    data class PlanOption(
        val plan: String,
        val name: String,
        val price: String,
        val period: String,
        val features: List<String>,
        val featured: Boolean = false
    )

    /** The web UI's plan grid, in order (the "Best value" card is featured). */
    val PLANS = listOf(
        PlanOption(
            plan = "monthly",
            name = "Monthly",
            price = "₹50",
            period = "/ month",
            features = listOf("All groups & settlements", "Cancel any time")
        ),
        PlanOption(
            plan = "6month",
            name = "6 Months",
            price = "₹70",
            period = "/ 6 months",
            features = listOf("All groups & settlements", "Save ₹30 vs monthly"),
            featured = true
        ),
        PlanOption(
            plan = "yearly",
            name = "Yearly",
            price = "₹100",
            period = "/ year",
            features = listOf("All groups & settlements", "Save ₹200 vs monthly")
        ),
        PlanOption(
            plan = "lifetime",
            name = "Lifetime",
            price = "₹300",
            period = "/ once",
            features = listOf("Pay once, use forever", "All future features included")
        )
    )

    /** True when [plan] is one of the mock-activatable plans (`POST /subscription`). */
    fun isPurchasable(plan: String?): Boolean = plan in PLANS.map { it.plan }

    /** The currently active plan key (for the "Current plan" tag), or null. */
    fun activePlan(status: SubscriptionStatusDto?): String? =
        status?.takeIf { it.planStatus == "active" }?.plan

    /** Fine print under the plan grid (index.html, verbatim). */
    const val PAYMENTS_FINE_PRINT =
        "Payments are simulated in this build — no real money moves."

    // ---- Notifications (app.js NOTIFICATION_LABELS / notificationText) --------

    private val NOTIFICATION_LABELS = mapOf(
        "GROUP_INVITE_ACCEPTED" to "accepted your group invitation",
        "GROUP_INVITE_DECLINED" to "declined your group invitation",
        "GROUP_SETTLED" to "settled a group",
        "ADHOC_PAYMENT" to "recorded a payment",
        "PAYOUT_ISSUED" to "issued a payout",
        "PAYOUT_PAID" to "marked a payout paid",
        "PAYOUT_SETTLED" to "settled a payout",
        "PAYOUT_FAILED" to "had a payout fail",
        "PAYOUT_CANCELLED" to "cancelled a payout",
        "PAYOUT_REMINDER" to "sent a payout reminder",
        "PAYOUT_ESCALATED" to "escalated a payout"
    )

    /**
     * Web `notificationText`: the server `message` when non-blank, else the
     * kind label ("Someone <label>."), else the generic fallback.
     */
    fun notificationText(note: NotificationDto): String {
        note.message?.takeIf { it.isNotBlank() }?.let { return it }
        val label = NOTIFICATION_LABELS[note.kind]
        return label?.let { "Someone $it." } ?: "New notification"
    }
}
