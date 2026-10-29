package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.NotificationDto
import com.kharcha.tracker.data.remote.dto.SubscriptionStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Stage 7b settings display logic, mirroring the web frontend's
 * `PLAN_LABELS` / `planLabel` / `formatDate` / `planSummary` / `shortPlan` /
 * `renderSubscriptionBanner` / `NOTIFICATION_LABELS` / `notificationText`,
 * and the plan catalog in `index.html`.
 */
class SettingsTextsTest {

    private fun status(
        planStatus: String? = null,
        plan: String? = null,
        planEndsAt: String? = null,
        entriesRemaining: Int? = null,
        isExpired: Boolean = false,
        trialEndsAt: String? = null
    ) = SubscriptionStatusDto(
        trialEndsAt = trialEndsAt,
        plan = plan,
        planStatus = planStatus,
        planEndsAt = planEndsAt,
        entriesRemaining = entriesRemaining,
        isLimited = isExpired,
        isExpired = isExpired
    )

    // -- plan labels & dates ---------------------------------------------------

    @Test
    fun `planLabel maps known plans and passes through unknown ones`() {
        assertEquals("Free (trial)", SettingsTexts.planLabel("none"))
        assertEquals("Monthly", SettingsTexts.planLabel("monthly"))
        assertEquals("6 Months", SettingsTexts.planLabel("6month"))
        assertEquals("Yearly", SettingsTexts.planLabel("yearly"))
        assertEquals("Lifetime", SettingsTexts.planLabel("lifetime"))
        assertEquals("custom", SettingsTexts.planLabel("custom"))
        assertEquals("—", SettingsTexts.planLabel(null))
    }

    @Test
    fun `formatDate renders ISO dates and keeps raw values otherwise`() {
        assertEquals("—", SettingsTexts.formatDate(null))
        assertEquals("—", SettingsTexts.formatDate(""))
        assertEquals("15 Jan 2026", SettingsTexts.formatDate("2026-01-15T10:00:00Z"))
        assertEquals("hello", SettingsTexts.formatDate("hello"))
    }

    // -- plan summary / banner (web `planSummary` / `renderSubscriptionBanner`)

    @Test
    fun `planSummary phrases active plans`() {
        assertEquals(
            "Lifetime plan — active, never expires",
            SettingsTexts.planSummary(status(planStatus = "active", plan = "lifetime"))
        )
        assertEquals(
            "Monthly plan — active until 31 Jan 2026",
            SettingsTexts.planSummary(
                status(planStatus = "active", plan = "monthly", planEndsAt = "2026-01-31T00:00:00Z")
            )
        )
    }

    @Test
    fun `planSummary phrases expired and trial states`() {
        assertEquals(
            "Free trial ended — 3 free entries left",
            SettingsTexts.planSummary(status(isExpired = true, entriesRemaining = 3))
        )
        assertEquals(
            "Free trial ended — 0 free entries left",
            SettingsTexts.planSummary(status(isExpired = true, entriesRemaining = null))
        )
        assertEquals(
            "Free trial — unlimited until 20 Jan 2026",
            SettingsTexts.planSummary(status(trialEndsAt = "2026-01-20T00:00:00Z"))
        )
    }

    @Test
    fun `shortPlan names the active plan or the state`() {
        assertEquals("Monthly", SettingsTexts.shortPlan(status(planStatus = "active", plan = "monthly")))
        assertEquals("Expired", SettingsTexts.shortPlan(status(isExpired = true)))
        assertEquals("Trial", SettingsTexts.shortPlan(status()))
    }

    @Test
    fun `subscriptionBanner phrases all three states`() {
        assertEquals(
            "Current plan: Monthly plan — active until 31 Jan 2026.",
            SettingsTexts.subscriptionBanner(
                status(planStatus = "active", plan = "monthly", planEndsAt = "2026-01-31T00:00:00Z")
            )
        )
        assertEquals(
            "Your trial has ended. 3 free entries remaining — choose a plan to keep going.",
            SettingsTexts.subscriptionBanner(status(isExpired = true, entriesRemaining = 3))
        )
        assertEquals(
            "You're on the free trial — unlimited until 20 Jan 2026.",
            SettingsTexts.subscriptionBanner(status(trialEndsAt = "2026-01-20T00:00:00Z"))
        )
    }

    // -- plan catalog (index.html `#view-subscription`) --------------------------

    @Test
    fun `PLANS lists the web catalog in order`() {
        assertEquals(
            listOf("monthly", "6month", "yearly", "lifetime"),
            SettingsTexts.PLANS.map { it.plan }
        )
        val byPlan = SettingsTexts.PLANS.associateBy { it.plan }
        assertEquals("₹50", byPlan.getValue("monthly").price)
        assertEquals("₹70", byPlan.getValue("6month").price)
        assertEquals("₹100", byPlan.getValue("yearly").price)
        assertEquals("₹300", byPlan.getValue("lifetime").price)
    }

    @Test
    fun `PLANS marks only 6 Months as best value`() {
        assertEquals(listOf("6month"), SettingsTexts.PLANS.filter { it.featured }.map { it.plan })
    }

    @Test
    fun `PLANS carries the web feature bullets`() {
        val byPlan = SettingsTexts.PLANS.associateBy { it.plan }
        assertEquals(
            listOf("All groups & settlements", "Cancel any time"),
            byPlan.getValue("monthly").features
        )
        assertEquals(
            listOf("All groups & settlements", "Save ₹30 vs monthly"),
            byPlan.getValue("6month").features
        )
        assertEquals(
            listOf("Pay once, use forever", "All future features included"),
            byPlan.getValue("lifetime").features
        )
    }

    @Test
    fun `isPurchasable accepts exactly the catalog plans`() {
        listOf("monthly", "6month", "yearly", "lifetime").forEach {
            assertTrue(SettingsTexts.isPurchasable(it))
        }
        listOf("none", "weekly", "", null).forEach {
            assertFalse(SettingsTexts.isPurchasable(it))
        }
    }

    @Test
    fun `activePlan reports only an active plan`() {
        assertEquals("yearly", SettingsTexts.activePlan(status(planStatus = "active", plan = "yearly")))
        assertNull(SettingsTexts.activePlan(status(planStatus = "none", plan = "monthly")))
        assertNull(SettingsTexts.activePlan(null))
    }

    @Test
    fun `payments fine print states the mock`() {
        assertTrue(SettingsTexts.PAYMENTS_FINE_PRINT.contains("simulated"))
    }

    // -- notifications (web `NOTIFICATION_LABELS` / `notificationText`) ---------

    @Test
    fun `notificationText prefers the server message`() {
        val note = NotificationDto(kind = "GROUP_SETTLED", message = "Custom text")
        assertEquals("Custom text", SettingsTexts.notificationText(note))
    }

    @Test
    fun `notificationText uses the kind label for known kinds`() {
        assertEquals(
            "Someone settled a group.",
            SettingsTexts.notificationText(NotificationDto(kind = "GROUP_SETTLED"))
        )
        assertEquals(
            "Someone accepted your group invitation.",
            SettingsTexts.notificationText(NotificationDto(kind = "GROUP_INVITE_ACCEPTED"))
        )
        assertEquals(
            "Someone had a payout fail.",
            SettingsTexts.notificationText(NotificationDto(kind = "PAYOUT_FAILED"))
        )
        assertEquals(
            "Someone escalated a payout.",
            SettingsTexts.notificationText(NotificationDto(kind = "PAYOUT_ESCALATED"))
        )
    }

    @Test
    fun `notificationText falls back for unknown or missing kinds`() {
        assertEquals("New notification", SettingsTexts.notificationText(NotificationDto(kind = "UNKNOWN_KIND")))
        assertEquals("New notification", SettingsTexts.notificationText(NotificationDto(kind = null)))
    }

    @Test
    fun `notificationText ignores blank messages`() {
        assertEquals(
            "Someone settled a group.",
            SettingsTexts.notificationText(NotificationDto(kind = "GROUP_SETTLED", message = "  "))
        )
    }
}
