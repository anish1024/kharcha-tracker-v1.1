"""
kharcha_v1/tests/test_corpus.py
E6: corpus fund — CORPUS expense category, corpus deposits (equal split)
flowing through the settlement preview, and the read-only corpus
derivation: settled orphan payouts + positive net balances.

Note: preview_settlement's accounting invariant rejects groups holding an
UNLINKED PAID orphan (recorded by the payor, awaiting the payee), so the
real-preview scenarios below use SETTLED orphans (payee-confirmed). The
SETTLED-only status filter of the derivation itself is unit-tested on a
synthetic preview, which is exactly what the frontend applies to the
unfiltered orphan_payouts list the engine returns.
"""
from datetime import datetime, timezone
from types import SimpleNamespace
from uuid import uuid4

from kharcha_v1.kharcha_gateway.kharcha_service import (
    auth_service,
    expense_service,
    group_service,
    settlement_engine,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.expense_category import (
    ExpenseCategory,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.expense_status import (
    ExpenseStatus,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.payout_status import (
    PayoutStatus,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense import Expense
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_payment import (
    ExpensePayment,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_share import (
    ExpenseShare,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.payout import Payout

SE = settlement_engine


# ---------------------------------------------------------------------------
# Helpers (mirroring test_settlement_engine.py conventions)
# ---------------------------------------------------------------------------
def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def _group(storage, owner, *others):
    group = group_service.create_group(storage, "Flat", "INR", owner.id)
    for other in others:
        group_service.add_member(storage, group.id, other.email, owner.id)
    return group


def _expense(
    group_id, payer_id, member_ids, amount=100.0, category="GROCERY", pct=None
):
    if pct is None:
        share = round(100.0 / len(member_ids), 2)
        pct = [share] * len(member_ids)
    return Expense(
        trans_id=0,
        group_id=group_id,
        date_time=datetime.now(timezone.utc),
        expn_ctgry=category,
        expn_desc="Expense",
        expn_amnt=amount,
        expn_splt=[
            ExpenseShare(user_id=uid, percentage=p) for uid, p in zip(member_ids, pct)
        ],
        paid_by=[ExpensePayment(user_id=payer_id, amt=amount)],
        expn_bill_screenshot="",
        expn_payment_screenshot="",
        status=ExpenseStatus.OPEN.value,
    )


def _add(storage, group, payer, member_ids, amount=100.0, category="GROCERY", pct=None):
    return expense_service.add_expense(
        storage,
        _expense(group.id, payer.id, member_ids, amount, category, pct),
        payer.id,
    )


def corpus_from_preview(preview):
    """Python mirror of corpusFromPreview in js/app.js (E6).

    corpus = sum of SETTLED orphan payouts + sum of positive net_dues.
    Positive net_dues = the group owes that member (overpaid); orphans
    never enter net_dues, so the two terms are disjoint.
    """
    settled = [
        p for p in preview.orphan_payouts if p.status == PayoutStatus.SETTLED.value
    ]
    orphan_total = sum(p.amt for p in settled)
    overpaid_total = sum(v for v in preview.net_dues.values() if v > 0)
    return orphan_total, overpaid_total, orphan_total + overpaid_total


def _orphan(payor_id, payee_id, amt, status):
    return Payout(
        id=uuid4(),
        group_id=uuid4(),
        payor_id=payor_id,
        payee_id=payee_id,
        amt=float(amt),
        status=status,
        expense_ids=[],
        is_adhoc=True,
        settlement_round_id=None,
        replaces_payout_ids=[],
    )


# ---------------------------------------------------------------------------
# Enum + validation
# ---------------------------------------------------------------------------
def test_corpus_enum_member():
    assert ExpenseCategory.CORPUS.value == "CORPUS"
    values = {c.value for c in ExpenseCategory}
    assert "CORPUS" in values
    assert len(values) == 10


def test_corpus_expense_can_be_created(storage):
    a = _user(storage, "c1a@x.com", "A")
    b = _user(storage, "c1b@x.com", "B")
    group = _group(storage, a, b)
    created = _add(
        storage,
        group,
        a,
        [a.id, b.id],
        amount=200.0,
        category=ExpenseCategory.CORPUS.value,
        pct=[50.0, 50.0],
    )
    assert created.expn_ctgry == "CORPUS"
    assert abs(created.expn_amnt - 200.0) < 0.001


# ---------------------------------------------------------------------------
# Corpus deposit (equal split) flowing through the settlement preview
# ---------------------------------------------------------------------------
def test_corpus_deposit_flows_through_settlement(storage):
    a = _user(storage, "c2a@x.com", "A")
    b = _user(storage, "c2b@x.com", "B")
    group = _group(storage, a, b)
    _add(
        storage,
        group,
        a,
        [a.id, b.id],
        amount=200.0,
        category=ExpenseCategory.CORPUS.value,
        pct=[50.0, 50.0],
    )

    preview = SE.preview_settlement(storage, group.id)
    # A paid 200 and bears 100 of the split -> group owes A 100.
    # Positive net_dues = overpaid (group owes the member).
    assert abs(preview.net_dues[a.id] - 100.0) < 0.001
    assert abs(preview.net_dues[b.id] + 100.0) < 0.001
    # A single proposed payout moves the 100 from B to A.
    assert len(preview.proposed_payouts) == 1
    assert preview.proposed_payouts[0].payor_id == b.id
    assert preview.proposed_payouts[0].payee_id == a.id
    assert abs(preview.proposed_payouts[0].amt - 100.0) < 0.001
    assert preview.orphan_payouts == []
    orphan_total, overpaid_total, corpus = corpus_from_preview(preview)
    assert abs(orphan_total) < 0.001
    assert abs(overpaid_total - 100.0) < 0.001
    assert abs(corpus - 100.0) < 0.001


# ---------------------------------------------------------------------------
# Orphan payouts: status by actor, presence in the preview
# ---------------------------------------------------------------------------
def test_adhoc_orphan_status_by_actor(storage):
    a = _user(storage, "c3a@x.com", "A")
    b = _user(storage, "c3b@x.com", "B")
    group = _group(storage, a, b)

    # Actor is the payee -> SETTLED immediately; visible in the preview.
    settled = SE.record_adhoc_payment(
        storage, group.id, a.id, b.id, 150.0, actor_user_id=b.id
    )
    assert settled.status == PayoutStatus.SETTLED.value
    assert settled.is_adhoc is True
    assert settled.expense_ids == []
    preview = SE.preview_settlement(storage, group.id)
    assert len(preview.orphan_payouts) == 1
    assert preview.orphan_payouts[0].status == PayoutStatus.SETTLED.value
    assert abs(preview.orphan_payouts[0].amt - 150.0) < 0.001
    # Corpus counts the confirmed (SETTLED) orphan only.
    orphan_total, overpaid_total, corpus = corpus_from_preview(preview)
    assert abs(orphan_total - 150.0) < 0.001
    assert abs(overpaid_total) < 0.001
    assert abs(corpus - 150.0) < 0.001

    # A separate group: actor is the payor -> PAID (payee reviews).
    c = _user(storage, "c3c@x.com", "C")
    d = _user(storage, "c3d@x.com", "D")
    group2 = _group(storage, c, d)
    paid = SE.record_adhoc_payment(
        storage, group2.id, c.id, d.id, 50.0, actor_user_id=c.id
    )
    assert paid.status == PayoutStatus.PAID.value
    assert paid.is_adhoc is True
    assert paid.expense_ids == []


# ---------------------------------------------------------------------------
# Corpus derivation: settled orphans + positive net balances
# ---------------------------------------------------------------------------
def test_corpus_derivation_full_scenario(storage):
    a = _user(storage, "c4a@x.com", "A")
    b = _user(storage, "c4b@x.com", "B")
    group = _group(storage, a, b)

    # Corpus deposit: A pays 200, 50/50 split -> A +100, B -100.
    _add(
        storage,
        group,
        a,
        [a.id, b.id],
        amount=200.0,
        category=ExpenseCategory.CORPUS.value,
        pct=[50.0, 50.0],
    )
    # Settled orphan: A tosses 150 into the fund, B confirms.
    SE.record_adhoc_payment(
        storage, group.id, a.id, b.id, 150.0, actor_user_id=b.id
    )

    preview = SE.preview_settlement(storage, group.id)
    # Orphans never enter net_dues: the deposit's net position stands alone.
    assert abs(preview.net_dues[a.id] - 100.0) < 0.001
    assert abs(preview.net_dues[b.id] + 100.0) < 0.001
    assert len(preview.orphan_payouts) == 1
    # corpus = settled orphans (150) + overpaid balances (100) = 250.
    orphan_total, overpaid_total, corpus = corpus_from_preview(preview)
    assert abs(orphan_total - 150.0) < 0.001
    assert abs(overpaid_total - 100.0) < 0.001
    assert abs(corpus - 250.0) < 0.001


def test_corpus_zero_on_empty_group(storage):
    a = _user(storage, "c5a@x.com", "A")
    b = _user(storage, "c5b@x.com", "B")
    group = _group(storage, a, b)
    preview = SE.preview_settlement(storage, group.id)
    orphan_total, overpaid_total, corpus = corpus_from_preview(preview)
    assert abs(orphan_total) < 0.001
    assert abs(overpaid_total) < 0.001
    assert abs(corpus) < 0.001


def test_corpus_status_filter_excludes_unsettled():
    """The derivation counts SETTLED orphans only, whatever other statuses
    the (unfiltered) preview orphan list carries."""
    a, b = uuid4(), uuid4()
    synthetic = SimpleNamespace(
        orphan_payouts=[
            _orphan(a, b, 150.0, PayoutStatus.SETTLED.value),
            _orphan(a, b, 50.0, PayoutStatus.PAID.value),
            _orphan(b, a, 20.0, PayoutStatus.PENDING.value),
        ],
        net_dues={a: 100.0, b: -100.0},
    )
    orphan_total, overpaid_total, corpus = corpus_from_preview(synthetic)
    assert abs(orphan_total - 150.0) < 0.001
    assert abs(overpaid_total - 100.0) < 0.001
    assert abs(corpus - 250.0) < 0.001

    # No settled orphans and no creditor balance -> corpus is zero.
    synthetic2 = SimpleNamespace(
        orphan_payouts=[_orphan(a, b, 50.0, PayoutStatus.PAID.value)],
        net_dues={a: -25.0, b: -25.0},
    )
    assert corpus_from_preview(synthetic2) == (0.0, 0.0, 0.0)
