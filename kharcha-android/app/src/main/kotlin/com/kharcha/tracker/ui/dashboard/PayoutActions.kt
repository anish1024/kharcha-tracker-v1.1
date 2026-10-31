package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.PayoutDto

/**
 * The payout state transitions the app can offer, keyed off the server's
 * permission rules in `settlement_engine.py`:
 *
 * | status    | payor            | payee               |
 * |-----------|------------------|---------------------|
 * | PENDING   | mark paid, cancel| confirm settled, cancel |
 * | PAID      | mark failed      | confirm settled, mark failed |
 * | SETTLED / FAILED / CANCELLED | (none)   | (none)              |
 *
 * `mark failed` and `cancel` additionally require a non-blank reason
 * (the gateway rejects blank reasons with 422).
 */
enum class PayoutAction {
    MARK_PAID,
    CONFIRM_SETTLED,
    MARK_FAILED,
    CANCEL
}

/**
 * Pure helpers deciding which [PayoutAction]s apply to a payout for the
 * signed-in user. Unit-testable on the JVM (no Android dependencies).
 */
object PayoutActions {

    /** Actions available to [myUserId] for this [payout], in display order. */
    fun actionsFor(payout: PayoutDto, myUserId: String?): List<PayoutAction> {
        if (myUserId.isNullOrBlank()) return emptyList()
        val isPayor = payout.payorId == myUserId
        val isPayee = payout.payeeId == myUserId
        return when (payout.status) {
            "PENDING" -> {
                val actions = mutableListOf<PayoutAction>()
                if (isPayor) actions.add(PayoutAction.MARK_PAID)
                if (isPayee) actions.add(PayoutAction.CONFIRM_SETTLED)
                if (isPayor || isPayee) actions.add(PayoutAction.CANCEL)
                actions
            }

            "PAID" -> {
                val actions = mutableListOf<PayoutAction>()
                if (isPayee) actions.add(PayoutAction.CONFIRM_SETTLED)
                if (isPayor || isPayee) actions.add(PayoutAction.MARK_FAILED)
                actions
            }

            else -> emptyList()
        }
    }

    /** Whether the server requires a non-blank `reason` for this action. */
    fun requiresReason(action: PayoutAction): Boolean = when (action) {
        PayoutAction.MARK_FAILED, PayoutAction.CANCEL -> true
        else -> false
    }

    /** Button text for the action. */
    fun label(action: PayoutAction): String = when (action) {
        PayoutAction.MARK_PAID -> "Mark paid"
        PayoutAction.CONFIRM_SETTLED -> "Confirm settled"
        PayoutAction.MARK_FAILED -> "Mark failed"
        PayoutAction.CANCEL -> "Cancel"
    }

    /** Dialog title for the action. */
    fun dialogTitle(action: PayoutAction): String = when (action) {
        PayoutAction.MARK_PAID -> "Mark as paid"
        PayoutAction.CONFIRM_SETTLED -> "Confirm settlement"
        PayoutAction.MARK_FAILED -> "Mark as failed"
        PayoutAction.CANCEL -> "Cancel payout"
    }

    /** Whether the optional evidence note applies to this action. */
    fun allowsEvidence(action: PayoutAction): Boolean = true

    /** One-line description shown in the action's confirm dialog. */
    fun confirmText(action: PayoutAction): String = when (action) {
        PayoutAction.MARK_PAID ->
            "Mark this payout as paid. Add evidence (a UPI reference, for example) if you have it."

        PayoutAction.CONFIRM_SETTLED ->
            "Confirm you have received this payment and the payout is settled."

        PayoutAction.MARK_FAILED ->
            "Mark this payout as failed. A reason is required."

        PayoutAction.CANCEL ->
            "Cancel this pending payout. A reason is required."
    }
}
