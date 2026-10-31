package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.PayoutDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payout action matrix (Stage 7a), keyed off the gateway's
 * permission rules in `settlement_engine.py`: which transitions each role
 * gets per payout status, which actions require a reason, and the
 * user-facing texts.
 */
class PayoutActionsTest {

    private fun payout(status: String, payorId: String = "aa11", payeeId: String = "bb22") =
        PayoutDto(
            id = "p1",
            groupId = "g1",
            payorId = payorId,
            payeeId = payeeId,
            amount = 100.0,
            status = status
        )

    // -- role matrix -----------------------------------------------------------

    @Test
    fun `pending payout offers mark paid and cancel to the payor`() {
        assertEquals(
            listOf(PayoutAction.MARK_PAID, PayoutAction.CANCEL),
            PayoutActions.actionsFor(payout("PENDING"), myUserId = "aa11")
        )
    }

    @Test
    fun `pending payout offers confirm settled and cancel to the payee`() {
        assertEquals(
            listOf(PayoutAction.CONFIRM_SETTLED, PayoutAction.CANCEL),
            PayoutActions.actionsFor(payout("PENDING"), myUserId = "bb22")
        )
    }

    @Test
    fun `pending payout offers nothing to a third party`() {
        assertTrue(
            PayoutActions.actionsFor(payout("PENDING"), myUserId = "cc33").isEmpty()
        )
    }

    @Test
    fun `no signed-in user gets no actions`() {
        assertTrue(PayoutActions.actionsFor(payout("PENDING"), myUserId = null).isEmpty())
        assertTrue(PayoutActions.actionsFor(payout("PENDING"), myUserId = "").isEmpty())
    }

    @Test
    fun `paid payout offers only mark failed to the payor`() {
        assertEquals(
            listOf(PayoutAction.MARK_FAILED),
            PayoutActions.actionsFor(payout("PAID"), myUserId = "aa11")
        )
    }

    @Test
    fun `paid payout offers confirm settled and mark failed to the payee`() {
        assertEquals(
            listOf(PayoutAction.CONFIRM_SETTLED, PayoutAction.MARK_FAILED),
            PayoutActions.actionsFor(payout("PAID"), myUserId = "bb22")
        )
    }

    @Test
    fun `paid payout offers nothing to a third party`() {
        assertTrue(
            PayoutActions.actionsFor(payout("PAID"), myUserId = "cc33").isEmpty()
        )
    }

    @Test
    fun `terminal statuses offer no actions to either role`() {
        for (status in listOf("SETTLED", "FAILED", "CANCELLED")) {
            assertTrue(
                "expected no $status actions for the payor",
                PayoutActions.actionsFor(payout(status), myUserId = "aa11").isEmpty()
            )
            assertTrue(
                "expected no $status actions for the payee",
                PayoutActions.actionsFor(payout(status), myUserId = "bb22").isEmpty()
            )
        }
    }

    @Test
    fun `unknown status offers no actions`() {
        assertTrue(
            PayoutActions.actionsFor(payout("SOME_NEW_STATUS"), myUserId = "aa11").isEmpty()
        )
    }

    // -- reason requirement ------------------------------------------------------

    @Test
    fun `mark failed and cancel require a reason`() {
        assertTrue(PayoutActions.requiresReason(PayoutAction.MARK_FAILED))
        assertTrue(PayoutActions.requiresReason(PayoutAction.CANCEL))
        assertFalse(PayoutActions.requiresReason(PayoutAction.MARK_PAID))
        assertFalse(PayoutActions.requiresReason(PayoutAction.CONFIRM_SETTLED))
    }

    // -- user-facing texts --------------------------------------------------------

    @Test
    fun `labels are distinct and non-blank`() {
        val labels = PayoutAction.entries.map { PayoutActions.label(it) }
        assertEquals(4, labels.size)
        assertEquals(labels.toSet().size, labels.size)
        labels.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `dialog titles are distinct and non-blank`() {
        val titles = PayoutAction.entries.map { PayoutActions.dialogTitle(it) }
        assertEquals(4, titles.size)
        assertEquals(titles.toSet().size, titles.size)
        titles.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `confirm text is present for every action`() {
        PayoutAction.entries.forEach { action ->
            assertTrue(PayoutActions.confirmText(action).isNotBlank())
            assertTrue(PayoutActions.allowsEvidence(action))
        }
    }
}
