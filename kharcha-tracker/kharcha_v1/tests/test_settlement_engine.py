"""
kharcha_v1/tests/test_settlement_engine.py
Unit tests for the matrix-based settlement engine (FR-17, plan S1-S5).
"""
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

import pytest

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
from kharcha_v1.kharcha_gateway.kharcha_service.constants.group_status import GroupStatus
from kharcha_v1.kharcha_gateway.kharcha_service.constants.payout_status import (
    PayoutStatus,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.settlement_error_code import (
    SettlementErrorCode,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.settlement_kind import (
    SettlementKind,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.settlement_round_status import (
    SettlementRoundStatus,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense import Expense
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_payment import (
    ExpensePayment,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_share import ExpenseShare
from kharcha_v1.kharcha_gateway.kharcha_service.models.matrix_row_key import (
    mrk_from_expense,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.payout import Payout
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.payout_record import (
    PayoutRecord,
)

SE = settlement_engine


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def _group(storage, owner, *others):
    group = group_service.create_group(storage, "Flat", "INR", owner.id)
    for other in others:
        group_service.add_member(storage, group.id, other.email, owner.id)
    return group


def _expense(group_id, payer_id, members, amount=100.0, pct=None):
    if pct is None:
        share = round(100.0 / len(members), 2)
        pct = [share] * len(members)
    return Expense(
        trans_id=0,
        group_id=group_id,
        date_time=datetime.now(timezone.utc),
        expn_ctgry=ExpenseCategory.GROCERY.value,
        expn_desc="Groceries",
        expn_amnt=amount,
        expn_splt=[
            ExpenseShare(user_id=m, percentage=p) for m, p in zip(members, pct)
        ],
        paid_by=[ExpensePayment(user_id=payer_id, amt=amount)],
        expn_bill_screenshot="",
        expn_payment_screenshot="",
        status=ExpenseStatus.OPEN.value,
    )


def _add(storage, group, payer, members, amount=100.0, pct=None):
    return expense_service.add_expense(
        storage,
        _expense(group.id, payer.id, [m.id for m in members], amount, pct),
        payer.id,
    )


def _pending_payout(group_id, payor_id, payee_id, amt, expense_ids, **kw):
    return Payout(
        id=uuid4(), group_id=group_id, payor_id=payor_id, payee_id=payee_id,
        amt=amt, status=PayoutStatus.PENDING.value, expense_ids=list(expense_ids),
        is_adhoc=False, settlement_round_id=None, replaces_payout_ids=[], **kw,
    )


# ---------------------------------------------------------------------------
# Pure matrix helpers
# ---------------------------------------------------------------------------
def test_matrix_row_sums_to_zero():
    u1, u2, u3 = uuid4(), uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=100.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=30.0),
                   ExpenseShare(user_id=u3, percentage=20.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=100.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    matrix = SE.build_expense_matrix([exp], [u1, u2, u3])
    SE.validate_matrix_balance(matrix)
    row = matrix[mrk_from_expense(1, [1])]
    assert abs(row[u1] - 50.0) < 0.001
    assert abs(row[u2] + 30.0) < 0.001
    assert abs(row[u3] + 20.0) < 0.001


def test_largest_remainder_rounding_three_way_sums_exactly():
    u1, u2, u3 = uuid4(), uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=100.0,
        expn_splt=[ExpenseShare(user_id=u, percentage=100.0 / 3) for u in (u1, u2, u3)],
        paid_by=[ExpensePayment(user_id=u1, amt=100.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    matrix = SE.build_expense_matrix([exp], [u1, u2, u3])
    row = matrix[mrk_from_expense(1, [1])]
    # Row must sum to exactly 0 in minor units
    assert sum(SE._to_minor(v) for v in row.values()) == 0
    # Shares are 3333/3333/3334 minor units (largest remainder puts the extra
    # cent on one user); payer cell = 10000 - own_share.
    shares_minor = sorted(
        SE._to_minor(exp.expn_amnt) - SE._to_minor(row[u]) if u == u1
        else SE._to_minor(-row[u])
        for u in row
    )
    assert shares_minor == [3333, 3333, 3334]
    assert sum(shares_minor) == 10000


def test_matrix_rejects_percentages_not_100():
    u1, u2 = uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=100.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=90.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=100.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    with pytest.raises(SE.SettlementError) as exc:
        SE.build_expense_matrix([exp], [u1, u2])
    assert exc.value.code == SettlementErrorCode.ACCOUNTING


def test_matrix_rejects_non_member_split():
    u1, u2, outsider = uuid4(), uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=100.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=outsider, percentage=50.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=100.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    with pytest.raises(SE.SettlementError) as exc:
        SE.build_expense_matrix([exp], [u1, u2])
    assert exc.value.code == SettlementErrorCode.ACCOUNTING


def test_matrix_rejects_paid_amount_mismatch():
    u1, u2 = uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=100.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=50.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=60.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    with pytest.raises(SE.SettlementError) as exc:
        SE.build_expense_matrix([exp], [u1, u2])
    assert exc.value.code == SettlementErrorCode.ACCOUNTING


def test_row_payouts_deterministic_and_balanced():
    u1, u2, u3 = uuid4(), uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=60.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=30.0),
                   ExpenseShare(user_id=u3, percentage=20.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=60.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    matrix = SE.build_expense_matrix([exp], [u1, u2, u3])
    key, row = next(iter(matrix.items()))
    payouts = SE.generate_row_payouts(key, row)
    assert len(payouts) == 2
    assert sum(p.amount_minor for p in payouts) == 3000
    assert all(p.amount_minor > 0 for p in payouts)
    assert all(p.row_key == key for p in payouts)


def test_consolidation_one_payout_per_pair():
    u1, u2, u3 = uuid4(), uuid4(), uuid4()
    gid = uuid4()
    exp1 = Expense(
        trans_id=1, group_id=gid, date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="a", expn_amnt=40.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=50.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=40.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    exp2 = Expense(
        trans_id=2, group_id=gid, date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="b", expn_amnt=60.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=50.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=60.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    matrix = SE.build_expense_matrix([exp1, exp2], [u1, u2, u3])
    row_payouts = []
    for key, row in matrix.items():
        row_payouts.extend(SE.generate_row_payouts(key, row))
    proposed = SE.consolidate_payouts(row_payouts, gid, [])
    assert len(proposed) == 1
    assert proposed[0].amt == 50.0
    assert set(proposed[0].expense_ids) == {1, 2}


def test_commitment_rule_subtracts_open_payouts():
    u1, u2 = uuid4(), uuid4()
    gid = uuid4()
    exp = Expense(
        trans_id=1, group_id=gid, date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=100.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=50.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=100.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    matrix = SE.build_expense_matrix([exp], [u1, u2])
    row_payouts = []
    for key, row in matrix.items():
        row_payouts.extend(SE.generate_row_payouts(key, row))
    open_payout = _pending_payout(gid, u2, u1, 30.0, [1])
    proposed = SE.consolidate_payouts(row_payouts, gid, [open_payout])
    assert len(proposed) == 1
    assert proposed[0].amt == 20.0


def test_apply_settled_payouts_adds_row():
    u1, u2 = uuid4(), uuid4()
    payout = Payout(
        id=uuid4(), group_id=uuid4(), payor_id=u2, payee_id=u1,
        amt=25.0, status=PayoutStatus.SETTLED.value, expense_ids=[1],
        is_adhoc=False, settlement_round_id=None, replaces_payout_ids=[],
    )
    matrix = SE.apply_settled_payouts({}, [payout], [u1, u2])
    assert len(matrix) == 1
    row = next(iter(matrix.values()))
    assert row[u2] == 25.0
    assert row[u1] == -25.0


def test_apply_settled_payouts_ignores_orphans_and_open():
    u1, u2 = uuid4(), uuid4()
    orphan = Payout(
        id=uuid4(), group_id=uuid4(), payor_id=u2, payee_id=u1,
        amt=10.0, status=PayoutStatus.SETTLED.value, expense_ids=[],
        is_adhoc=True, settlement_round_id=None, replaces_payout_ids=[],
    )
    pending = _pending_payout(uuid4(), u2, u1, 10.0, [1])
    matrix = SE.apply_settled_payouts({}, [orphan, pending], [u1, u2])
    assert matrix == {}


def test_net_dues_column_sums():
    u1, u2, u3 = uuid4(), uuid4(), uuid4()
    exp = Expense(
        trans_id=1, group_id=uuid4(), date_time=datetime.now(timezone.utc),
        expn_ctgry="GROCERY", expn_desc="t", expn_amnt=60.0,
        expn_splt=[ExpenseShare(user_id=u1, percentage=50.0),
                   ExpenseShare(user_id=u2, percentage=30.0),
                   ExpenseShare(user_id=u3, percentage=20.0)],
        paid_by=[ExpensePayment(user_id=u1, amt=60.0)],
        expn_bill_screenshot="", expn_payment_screenshot="", status="Open",
    )
    matrix = SE.build_expense_matrix([exp], [u1, u2, u3])
    net = SE.calculate_net_dues(matrix, [u1, u2, u3])
    assert abs(net[u1] - 30.0) < 0.001
    assert abs(net[u2] + 18.0) < 0.001
    assert abs(net[u3] + 12.0) < 0.001


def test_reproduce_net_dues_raises_on_residual():
    u1, u2 = uuid4(), uuid4()
    with pytest.raises(SE.SettlementError) as exc:
        SE._assert_payouts_reproduce_net_dues([], [], {u1: 40.0, u2: -40.0})
    assert exc.value.code == SettlementErrorCode.ACCOUNTING
    # Matching payouts pass silently
    payout = _pending_payout(uuid4(), u2, u1, 40.0, [1])
    SE._assert_payouts_reproduce_net_dues([payout], [], {u1: 40.0, u2: -40.0})


# ---------------------------------------------------------------------------
# preview_settlement
# ---------------------------------------------------------------------------
def test_preview_settlement_happy_path(storage):
    owner = _user(storage, "p1@x.com", "Owner")
    friend = _user(storage, "p1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    preview = SE.preview_settlement(storage, group.id)
    assert preview.group_status == GroupStatus.OPEN.value
    assert abs(preview.net_dues[owner.id] - 50.0) < 0.001
    assert abs(preview.net_dues[friend.id] + 50.0) < 0.001
    assert len(preview.proposed_payouts) == 1
    assert preview.proposed_payouts[0].payor_id == friend.id
    assert preview.proposed_payouts[0].payee_id == owner.id
    assert preview.open_payouts == []
    assert preview.orphan_payouts == []


def test_preview_settlement_filters_by_user(storage):
    owner = _user(storage, "p2@x.com", "Owner")
    a = _user(storage, "p2a@x.com", "A")
    b = _user(storage, "p2b@x.com", "B")
    group = _group(storage, owner, a, b)
    # owner pays 100, split equally 33.33/33.33/33.34 (rounding)
    _add(storage, group, owner, [owner, a, b], 100.0, pct=[33.33, 33.33, 33.34])

    preview = SE.preview_settlement(storage, group.id, user_id=a.id)
    assert all(
        p.payor_id == a.id or p.payee_id == a.id for p in preview.proposed_payouts
    )
    assert a.id in preview.net_dues


def test_preview_settlement_unknown_group(storage):
    with pytest.raises(SE.SettlementError) as exc:
        SE.preview_settlement(storage, uuid4())
    assert exc.value.code == SettlementErrorCode.GROUP_NOT_FOUND


# ---------------------------------------------------------------------------
# settle_user
# ---------------------------------------------------------------------------
def test_settle_user_issues_individual_round(storage):
    owner = _user(storage, "s1@x.com", "Owner")
    friend = _user(storage, "s1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    payouts = SE.settle_user(storage, group.id, friend.id, friend.id)
    assert len(payouts) == 1
    assert payouts[0].payor_id == friend.id
    assert payouts[0].amt == 50.0
    assert payouts[0].status == PayoutStatus.PENDING.value
    assert payouts[0].settlement_round_id is not None

    rounds = SE.list_settlement_rounds(storage, group.id)
    assert len(rounds) == 1
    assert rounds[0].kind == SettlementKind.INDIVIDUAL.value
    assert rounds[0].status == SettlementRoundStatus.ACTIVE.value
    assert rounds[0].requested_for == friend.id


def test_settle_user_idempotent_when_nothing_owed(storage):
    owner = _user(storage, "s2@x.com", "Owner")
    group = _group(storage, owner)
    _add(storage, group, owner, [owner], 50.0)

    payouts = SE.settle_user(storage, group.id, owner.id, owner.id)
    assert payouts == []
    assert SE.list_settlement_rounds(storage, group.id) == []


def test_settle_user_rejects_non_member(storage):
    owner = _user(storage, "s3@x.com", "Owner")
    outsider = _user(storage, "s3b@x.com", "Outsider")
    group = _group(storage, owner)

    with pytest.raises(SE.SettlementError) as exc:
        SE.settle_user(storage, group.id, outsider.id, owner.id)
    assert exc.value.code == SettlementErrorCode.INVALID_REQUEST


def test_settle_user_rejects_settled_group(storage):
    owner = _user(storage, "s4@x.com", "Owner")
    friend = _user(storage, "s4b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    group_service.lock_group(storage, group.id, owner.id)
    SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.FINAL)
    # settle the payout to complete
    for p in SE.list_payouts(storage, group.id):
        SE.settle_payout(storage, group.id, p.id, p.payee_id)

    with pytest.raises(SE.SettlementError) as exc:
        SE.settle_user(storage, group.id, friend.id, friend.id)
    assert exc.value.code == SettlementErrorCode.GROUP_STATE


# ---------------------------------------------------------------------------
# initiate_settlement — PERIODIC / FINAL
# ---------------------------------------------------------------------------
def test_periodic_creates_round_and_keeps_status(storage):
    owner = _user(storage, "i1@x.com", "Owner")
    friend = _user(storage, "i1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    payouts = SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.PERIODIC)
    assert len(payouts) == 1
    assert payouts[0].settlement_round_id is not None
    group_after = group_service.get_group(storage, group.id)
    assert group_after.status == GroupStatus.OPEN.value
    rounds = SE.list_settlement_rounds(storage, group.id)
    assert rounds[0].kind == SettlementKind.PERIODIC.value


def test_periodic_noop_when_nothing_outstanding(storage):
    owner = _user(storage, "i2@x.com", "Owner")
    group = _group(storage, owner)
    _add(storage, group, owner, [owner], 50.0)

    payouts = SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.PERIODIC)
    assert payouts == []
    assert SE.list_settlement_rounds(storage, group.id) == []


def test_final_requires_locked_group(storage):
    owner = _user(storage, "i3@x.com", "Owner")
    friend = _user(storage, "i3b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    with pytest.raises(SE.SettlementError) as exc:
        SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.FINAL)
    assert exc.value.code == SettlementErrorCode.GROUP_NOT_LOCKED


def test_final_locks_to_partial_and_settles_when_zero(storage):
    owner = _user(storage, "i4@x.com", "Owner")
    group = _group(storage, owner)
    _add(storage, group, owner, [owner], 50.0)
    group_service.lock_group(storage, group.id, owner.id)

    payouts = SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.FINAL)
    assert payouts == []
    group_after = group_service.get_group(storage, group.id)
    assert group_after.status == GroupStatus.SETTLED.value


def test_final_with_debts_goes_partial_then_settles(storage):
    owner = _user(storage, "i5@x.com", "Owner")
    friend = _user(storage, "i5b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    group_service.lock_group(storage, group.id, owner.id)

    payouts = SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.FINAL)
    assert len(payouts) == 1
    group_after = group_service.get_group(storage, group.id)
    assert group_after.status == GroupStatus.PARTIAL_SETTLEMENT.value

    p = payouts[0]
    SE.mark_payout_paid(storage, group.id, p.id, p.payor_id)
    SE.settle_payout(storage, group.id, p.id, p.payee_id)

    group_final = group_service.get_group(storage, group.id)
    assert group_final.status == GroupStatus.SETTLED.value


def test_final_refused_with_orphan_adhoc(storage):
    owner = _user(storage, "i6@x.com", "Owner")
    friend = _user(storage, "i6b@x.com", "Friend")
    group = _group(storage, owner, friend)
    group_service.lock_group(storage, group.id, owner.id)
    SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 10.0, friend.id)

    with pytest.raises(SE.SettlementError) as exc:
        SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.FINAL)
    assert exc.value.code == SettlementErrorCode.FINAL_SETTLEMENT


# ---------------------------------------------------------------------------
# Payout lifecycle
# ---------------------------------------------------------------------------
def test_full_payout_lifecycle_paid_then_settled(storage):
    owner = _user(storage, "l1@x.com", "Owner")
    friend = _user(storage, "l1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    payouts = SE.settle_user(storage, group.id, friend.id, friend.id)
    p = payouts[0]

    paid = SE.mark_payout_paid(storage, group.id, p.id, p.payor_id, evidence="UPI-123")
    assert paid.status == PayoutStatus.PAID.value
    assert paid.paid_at is not None
    assert paid.payor_evidence == "UPI-123"

    settled = SE.settle_payout(storage, group.id, p.id, p.payee_id, evidence="OK")
    assert settled.status == PayoutStatus.SETTLED.value
    assert settled.settled_at is not None
    assert settled.payee_evidence == "OK"


def test_settle_payout_direct_from_pending(storage):
    owner = _user(storage, "l2@x.com", "Owner")
    friend = _user(storage, "l2b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]
    settled = SE.settle_payout(storage, group.id, p.id, p.payee_id)
    assert settled.status == PayoutStatus.SETTLED.value


def test_mark_paid_only_payor(storage):
    owner = _user(storage, "l3@x.com", "Owner")
    friend = _user(storage, "l3b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    with pytest.raises(SE.SettlementError) as exc:
        SE.mark_payout_paid(storage, group.id, p.id, owner.id)
    assert exc.value.code == SettlementErrorCode.PAYOUT_PERMISSION


def test_settle_payout_only_payee(storage):
    owner = _user(storage, "l4@x.com", "Owner")
    friend = _user(storage, "l4b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    with pytest.raises(SE.SettlementError) as exc:
        SE.settle_payout(storage, group.id, p.id, friend.id)
    assert exc.value.code == SettlementErrorCode.PAYOUT_PERMISSION


def test_cancel_payout_pending(storage):
    owner = _user(storage, "l5@x.com", "Owner")
    friend = _user(storage, "l5b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    cancelled = SE.cancel_payout(storage, group.id, p.id, p.payor_id, reason="wrong split")
    assert cancelled.status == PayoutStatus.CANCELLED.value
    assert cancelled.status_reason == "wrong split"


def test_cancel_payout_requires_reason(storage):
    owner = _user(storage, "l6@x.com", "Owner")
    friend = _user(storage, "l6b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    with pytest.raises(SE.SettlementError) as exc:
        SE.cancel_payout(storage, group.id, p.id, p.payor_id, reason="  ")
    assert exc.value.code == SettlementErrorCode.INVALID_REQUEST


def test_fail_payout_paid_only(storage):
    owner = _user(storage, "l7@x.com", "Owner")
    friend = _user(storage, "l7b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    # cannot fail a PENDING payout
    with pytest.raises(SE.SettlementError) as exc:
        SE.fail_payout(storage, group.id, p.id, p.payor_id, reason="nope")
    assert exc.value.code == SettlementErrorCode.PAYOUT_STATE

    SE.mark_payout_paid(storage, group.id, p.id, p.payor_id)
    failed = SE.fail_payout(storage, group.id, p.id, p.payee_id, reason="never received")
    assert failed.status == PayoutStatus.FAILED.value
    assert failed.status_reason == "never received"


def test_failed_payout_reissued_with_replaces(storage):
    owner = _user(storage, "l8@x.com", "Owner")
    friend = _user(storage, "l8b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]
    SE.mark_payout_paid(storage, group.id, p.id, p.payor_id)
    SE.fail_payout(storage, group.id, p.id, p.payee_id, reason="bad ref")

    reissued = SE.settle_user(storage, group.id, friend.id, friend.id)
    assert len(reissued) == 1
    assert reissued[0].amt == 50.0
    assert p.id in reissued[0].replaces_payout_ids


def test_actions_rejected_when_group_settled(storage):
    owner = _user(storage, "l9@x.com", "Owner")
    friend = _user(storage, "l9b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    group_service.lock_group(storage, group.id, owner.id)
    SE.initiate_settlement(storage, group.id, owner.id, SettlementKind.FINAL)
    for p in SE.list_payouts(storage, group.id):
        SE.settle_payout(storage, group.id, p.id, p.payee_id)

    with pytest.raises(SE.SettlementError) as exc:
        SE.mark_payout_paid(storage, group.id, uuid4(), owner.id)
    assert exc.value.code == SettlementErrorCode.GROUP_STATE


# ---------------------------------------------------------------------------
# remind / escalate
# ---------------------------------------------------------------------------
def test_remind_pending_payee_to_payor(storage):
    owner = _user(storage, "r1@x.com", "Owner")
    friend = _user(storage, "r1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    # payor cannot remind on PENDING
    with pytest.raises(SE.SettlementError) as exc:
        SE.remind_payout(storage, group.id, p.id, p.payor_id)
    assert exc.value.code == SettlementErrorCode.PAYOUT_PERMISSION

    SE.remind_payout(storage, group.id, p.id, p.payee_id)
    notes = auth_service.list_notifications(storage, p.payor_id) if hasattr(auth_service, "list_notifications") else []


def test_remind_paid_payor_to_payee(storage):
    owner = _user(storage, "r2@x.com", "Owner")
    friend = _user(storage, "r2b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]
    SE.mark_payout_paid(storage, group.id, p.id, p.payor_id)

    # payee cannot remind on PAID
    with pytest.raises(SE.SettlementError) as exc:
        SE.remind_payout(storage, group.id, p.id, p.payee_id)
    assert exc.value.code == SettlementErrorCode.PAYOUT_PERMISSION

    reminded = SE.remind_payout(storage, group.id, p.id, p.payor_id)
    assert reminded.status == PayoutStatus.PAID.value


def test_escalate_too_early_rejected(storage):
    owner = _user(storage, "r3@x.com", "Owner")
    friend = _user(storage, "r3b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]
    SE.mark_payout_paid(storage, group.id, p.id, p.payor_id)

    with pytest.raises(SE.SettlementError) as exc:
        SE.escalate_payout(storage, group.id, p.id, owner.id)
    assert exc.value.code == SettlementErrorCode.PAYOUT_STATE


def test_escalate_after_window(storage):
    owner = _user(storage, "r4@x.com", "Owner")
    friend = _user(storage, "r4b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]
    SE.mark_payout_paid(storage, group.id, p.id, p.payor_id)

    # backdate paid_at beyond the escalation window
    record = storage.get_record(record_id=str(p.id), schema="payout_record")
    record.paid_at = datetime.now(timezone.utc) - timedelta(days=8)
    storage.save_record(record, schema="payout_record")

    escalated = SE.escalate_payout(storage, group.id, p.id, owner.id)
    assert escalated.status == PayoutStatus.PAID.value


# ---------------------------------------------------------------------------
# Ad-hoc payments + linking
# ---------------------------------------------------------------------------
def test_adhoc_by_payor_is_paid(storage):
    owner = _user(storage, "a1@x.com", "Owner")
    friend = _user(storage, "a1b@x.com", "Friend")
    group = _group(storage, owner, friend)

    payout = SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 20.0, friend.id)
    assert payout.is_adhoc is True
    assert payout.status == PayoutStatus.PAID.value
    assert payout.expense_ids == []


def test_adhoc_by_payee_is_settled(storage):
    owner = _user(storage, "a2@x.com", "Owner")
    friend = _user(storage, "a2b@x.com", "Friend")
    group = _group(storage, owner, friend)

    payout = SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 20.0, owner.id)
    assert payout.status == PayoutStatus.SETTLED.value


def test_adhoc_rejects_third_party_actor(storage):
    owner = _user(storage, "a3@x.com", "Owner")
    friend = _user(storage, "a3b@x.com", "Friend")
    third = _user(storage, "a3c@x.com", "Third")
    group = _group(storage, owner, friend, third)

    with pytest.raises(SE.SettlementError) as exc:
        SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 20.0, third.id)
    assert exc.value.code == SettlementErrorCode.PAYOUT_PERMISSION


def test_adhoc_rejects_non_member_or_bad_amount(storage):
    owner = _user(storage, "a4@x.com", "Owner")
    friend = _user(storage, "a4b@x.com", "Friend")
    outsider = _user(storage, "a4c@x.com", "Outsider")
    group = _group(storage, owner, friend)

    with pytest.raises(SE.SettlementError):
        SE.record_adhoc_payment(storage, group.id, friend.id, outsider.id, 20.0, friend.id)
    with pytest.raises(SE.SettlementError):
        SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 0.0, friend.id)


def test_link_expenses_to_orphan_adhoc(storage):
    owner = _user(storage, "a5@x.com", "Owner")
    friend = _user(storage, "a5b@x.com", "Friend")
    group = _group(storage, owner, friend)
    exp = _add(storage, group, owner, [owner, friend], 100.0)

    orphan = SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 50.0, friend.id)
    assert orphan.expense_ids == []
    assert orphan.status == PayoutStatus.PAID.value  # payor-recorded: payee must confirm

    linked = SE.link_payout_expenses(storage, group.id, orphan.id, [exp.trans_id], friend.id)
    assert linked.expense_ids == [exp.trans_id]

    # net dues unchanged while the ad-hoc is only PAID (not yet SETTLED)
    preview = SE.preview_settlement(storage, group.id)
    assert abs(preview.net_dues[friend.id] + 50.0) < 0.001

    # once the payee confirms, the settled ad-hoc offsets the net dues
    SE.settle_payout(storage, group.id, orphan.id, owner.id)
    preview = SE.preview_settlement(storage, group.id)
    assert abs(preview.net_dues[friend.id]) < 0.001  # paid 50, owes 50


def test_link_rejects_non_adhoc(storage):
    owner = _user(storage, "a6@x.com", "Owner")
    friend = _user(storage, "a6b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)
    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]

    with pytest.raises(SE.SettlementError) as exc:
        SE.link_payout_expenses(storage, group.id, p.id, [1], friend.id)
    assert exc.value.code == SettlementErrorCode.INVALID_REQUEST


def test_link_rejects_unrelated_expense(storage):
    owner = _user(storage, "a7@x.com", "Owner")
    friend = _user(storage, "a7b@x.com", "Friend")
    third = _user(storage, "a7c@x.com", "Third")
    group = _group(storage, owner, friend, third)
    exp = _add(storage, group, owner, [owner, third], 100.0)  # friend not involved

    orphan = SE.record_adhoc_payment(storage, group.id, friend.id, owner.id, 10.0, friend.id)
    with pytest.raises(SE.SettlementError) as exc:
        SE.link_payout_expenses(storage, group.id, orphan.id, [exp.trans_id], friend.id)
    assert exc.value.code == SettlementErrorCode.INVALID_REQUEST


# ---------------------------------------------------------------------------
# listing
# ---------------------------------------------------------------------------
def test_list_payouts_and_rounds(storage):
    owner = _user(storage, "n1@x.com", "Owner")
    friend = _user(storage, "n1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    SE.settle_user(storage, group.id, friend.id, friend.id)
    all_payouts = SE.list_payouts(storage, group.id)
    assert len(all_payouts) == 1
    friend_payouts = SE.list_payouts(storage, group.id, user_id=friend.id)
    assert len(friend_payouts) == 1
    owner_payouts = SE.list_payouts(storage, group.id, user_id=owner.id)
    assert len(owner_payouts) == 1  # owner is payee

    rounds = SE.list_settlement_rounds(storage, group.id)
    assert len(rounds) == 1


# ---------------------------------------------------------------------------
# Round completion
# ---------------------------------------------------------------------------
def test_round_completes_when_no_payouts_pending(storage):
    owner = _user(storage, "c1@x.com", "Owner")
    friend = _user(storage, "c1b@x.com", "Friend")
    group = _group(storage, owner, friend)
    _add(storage, group, owner, [owner, friend], 100.0)

    p = SE.settle_user(storage, group.id, friend.id, friend.id)[0]
    assert SE.list_settlement_rounds(storage, group.id)[0].status == (
        SettlementRoundStatus.ACTIVE.value
    )

    SE.settle_payout(storage, group.id, p.id, p.payee_id)
    assert SE.list_settlement_rounds(storage, group.id)[0].status == (
        SettlementRoundStatus.COMPLETED.value
    )
