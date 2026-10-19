"""
kharcha_v1/kharcha_gateway/kharcha_service/settlement_engine.py
Matrix-based settlement engine (YAML node `settlement_engine`).

Balances live in ONE matrix derived from the expense table and the payout
table; nothing is ever written back to the expense table. All money math
happens in integer minor units (10 ** DECIMAL_PRECISION).
"""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone
from typing import Dict, Iterable, List, Optional, Sequence, Tuple
from uuid import UUID

from .constants.group_status import GroupStatus
from .constants.notification_kind import NotificationKind
from .constants.payout_status import PayoutStatus
from .constants.settlement_error_code import SettlementErrorCode
from .constants.settlement_kind import SettlementKind
from .constants.settlement_round_status import SettlementRoundStatus
from .constants.system_config import DECIMAL_PRECISION, PAYOUT_ESCALATION_DAYS
from .models.expense import Expense
from .models.expense_payment import ExpensePayment
from .models.expense_share import ExpenseShare
from .models.group import Group
from .models.matrix_row_key import MatrixRowKey, mrk_from_expense, mrk_from_payout
from .models.payout import Payout
from .models.row_payout import RowPayout
from .models.settlement_preview import SettlementPreview
from .models.settlement_round import SettlementRound
from .models.user import User
from .storage_service.schema.expense_record import ExpenseRecord
from .storage_service.schema.payout_record import PayoutRecord
from .storage_service.schema.settlement_round_record import SettlementRoundRecord
from .storage_service.storage_interface import StorageInterface as storage_interface


class SettlementError(Exception):
    """The only exception the settlement engine raises."""

    def __init__(self, code: SettlementErrorCode, message: str):
        self.code = code
        self.message = message
        super().__init__(f"{code}: {message}")


# ---------------------------------------------------------------------------
# Small money helpers
# ---------------------------------------------------------------------------
def _to_minor(amount: float) -> int:
    return int(round(float(amount) * (10 ** DECIMAL_PRECISION)))


def _from_minor(amount_minor: int) -> float:
    return amount_minor / (10 ** DECIMAL_PRECISION)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _new_id() -> UUID:
    return uuid.uuid4()


# ---------------------------------------------------------------------------
# DTO <-> record converters
# ---------------------------------------------------------------------------
def _payout_from_record(record: PayoutRecord) -> Payout:
    return Payout(
        id=record.id,
        group_id=record.group_id,
        payor_id=record.payor_id,
        payee_id=record.payee_id,
        amt=float(record.amt),
        status=record.status,
        expense_ids=[int(t) for t in (record.expense_ids or [])],
        is_adhoc=bool(record.is_adhoc),
        settlement_round_id=record.settlement_round_id,
        replaces_payout_ids=[UUID(str(x)) for x in (record.replaces_payout_ids or [])],
        payor_evidence=record.payor_evidence,
        payee_evidence=record.payee_evidence,
        status_reason=record.status_reason,
        paid_at=record.paid_at,
        settled_at=record.settled_at,
    )


def _payout_to_record(payout: Payout, actor_user_id: UUID) -> PayoutRecord:
    return PayoutRecord(
        id=payout.id,
        group_id=payout.group_id,
        payor_id=payout.payor_id,
        payee_id=payout.payee_id,
        amt=float(payout.amt),
        status=payout.status,
        expense_ids=list(payout.expense_ids),
        is_adhoc=bool(payout.is_adhoc),
        settlement_round_id=payout.settlement_round_id,
        replaces_payout_ids=list(payout.replaces_payout_ids),
        payor_evidence=payout.payor_evidence,
        payee_evidence=payout.payee_evidence,
        status_reason=payout.status_reason,
        paid_at=payout.paid_at,
        settled_at=payout.settled_at,
        created_by=actor_user_id,
        modified_by=actor_user_id,
    )


def _round_from_record(record: SettlementRoundRecord) -> SettlementRound:
    return SettlementRound(
        id=record.id,
        group_id=record.group_id,
        kind=record.kind,
        status=record.status,
        requested_for=record.requested_for,
        completed_at=record.completed_at,
    )


def _expense_from_record(record: ExpenseRecord) -> Expense:
    return Expense(
        trans_id=int(record.trans_id),
        group_id=record.group_id,
        date_time=record.date_time,
        expn_ctgry=record.expn_ctgry,
        expn_desc=record.expn_desc,
        expn_amnt=float(record.expn_amnt),
        expn_splt=[
            share if isinstance(share, ExpenseShare) else ExpenseShare.model_validate(share)
            for share in (record.expn_splt or [])
        ],
        paid_by=[
            payment
            if isinstance(payment, ExpensePayment)
            else ExpensePayment.model_validate(payment)
            for payment in (record.paid_by or [])
        ],
        expn_bill_screenshot=record.expn_bill_screenshot or "",
        expn_payment_screenshot=record.expn_payment_screenshot or "",
        status=record.status,
    )


# ---------------------------------------------------------------------------
# Storage loaders
# ---------------------------------------------------------------------------
def _load_group(storage: storage_interface, group_id: UUID) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise SettlementError(SettlementErrorCode.GROUP_NOT_FOUND, f"Group {group_id} not found")
    return Group(
        id=record.id,
        name=record.name,
        created_by_user_id=record.created_by,
        invite_code=record.invite_code or "",
        members=record.members or [],
        status=GroupStatus(record.status),
        currency=record.currency,
        time_created=record.created_at,
        last_updated=record.modified_at or record.created_at,
    )


def _list_expenses(storage: storage_interface, group_id: UUID) -> List[Expense]:
    records = storage.list_records(
        schema="expense_record",
        filters={"group_id": [str(group_id)]},
        sort_by="trans_id",
        ascending=True,
    )
    return [_expense_from_record(r) for r in records if not getattr(r, "is_deleted", False)]


def _list_payout_records(storage: storage_interface, group_id: UUID) -> List[PayoutRecord]:
    records = storage.list_records(
        schema="payout_record",
        filters={"group_id": [str(group_id)]},
        sort_by="created_at",
        ascending=True,
    )
    return list(records)


def _load_payout_record(storage: storage_interface, group_id: UUID, payout_id: UUID) -> PayoutRecord:
    for record in _list_payout_records(storage, group_id):
        if record.id == payout_id:
            return record
    raise SettlementError(SettlementErrorCode.PAYOUT_NOT_FOUND, f"Payout {payout_id} not found")


def _list_round_records(storage: storage_interface, group_id: UUID) -> List[SettlementRoundRecord]:
    return list(
        storage.list_records(
            schema="settlement_round_record",
            filters={"group_id": [str(group_id)]},
            sort_by="created_at",
            ascending=True,
        )
    )


# ---------------------------------------------------------------------------
# Pure matrix helpers
# ---------------------------------------------------------------------------
def build_expense_matrix(
    expenses: Sequence[Expense],
    users: Sequence[UUID],
) -> Dict[MatrixRowKey, Dict[UUID, float]]:
    """
    Pure. {MatrixRowKey(trans_id): {user_id: paid - share}} for every group
    member (zero-filler for members not involved). Shares are computed in
    minor units via largest remainder so each row sums to exactly 0.
    Raises SettlementError(ACCOUNTING) on any invariant violation.
    """
    seen_trans_ids: set[int] = set()
    matrix: Dict[MatrixRowKey, Dict[UUID, float]] = {}
    members = set(users)

    for exp in expenses:
        trans_id = int(exp.trans_id)
        if trans_id in seen_trans_ids:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING, f"Duplicate trans_id {trans_id}"
            )
        seen_trans_ids.add(trans_id)

        amount_minor = _to_minor(exp.expn_amnt)
        if amount_minor < 0:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Expense {trans_id} has negative amount",
            )

        # Shares (percentage -> minor units, largest remainder so sum == amount)
        pct_by_user: Dict[UUID, float] = {}
        total_pct = 0.0
        for share in exp.expn_splt:
            uid = share.user_id
            if uid not in members:
                raise SettlementError(
                    SettlementErrorCode.ACCOUNTING,
                    f"Expense {trans_id}: split user {uid} is not a member",
                )
            if share.percentage < 0:
                raise SettlementError(
                    SettlementErrorCode.ACCOUNTING,
                    f"Expense {trans_id}: negative percentage for {uid}",
                )
            pct_by_user[uid] = pct_by_user.get(uid, 0.0) + float(share.percentage)
            total_pct += float(share.percentage)
        if abs(total_pct - 100.0) > 0.01:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Expense {trans_id}: split percentages sum to {total_pct}, expected 100",
            )

        exact_minor = {
            uid: (pct_by_user[uid] / 100.0) * amount_minor for uid in pct_by_user
        }
        floor_minor = {uid: int(exact_minor[uid]) for uid in exact_minor}
        allocated = sum(floor_minor.values())
        remainder = amount_minor - allocated
        # Largest remainder: distribute remainder cents to users with the
        # biggest fractional part; ties broken by str(uid) for determinism.
        order = sorted(
            exact_minor.keys(),
            key=lambda uid: (-(exact_minor[uid] - floor_minor[uid]), str(uid)),
        )
        shares_minor = dict(floor_minor)
        for uid in order[:remainder]:
            shares_minor[uid] += 1

        # Payments
        paid_minor_by_user: Dict[UUID, int] = {}
        total_paid_minor = 0
        for payment in exp.paid_by:
            uid = payment.user_id
            if uid not in members:
                raise SettlementError(
                    SettlementErrorCode.ACCOUNTING,
                    f"Expense {trans_id}: payer {uid} is not a member",
                )
            p_minor = _to_minor(payment.amt)
            if p_minor < 0:
                raise SettlementError(
                    SettlementErrorCode.ACCOUNTING,
                    f"Expense {trans_id}: negative payment from {uid}",
                )
            paid_minor_by_user[uid] = paid_minor_by_user.get(uid, 0) + p_minor
            total_paid_minor += p_minor
        if total_paid_minor != amount_minor:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Expense {trans_id}: paid total {_from_minor(total_paid_minor)} "
                f"does not match amount {_from_minor(amount_minor)}",
            )

        row: Dict[UUID, float] = {uid: 0.0 for uid in members}
        involved = set(paid_minor_by_user) | set(shares_minor)
        for uid in involved:
            paid = paid_minor_by_user.get(uid, 0)
            share = shares_minor.get(uid, 0)
            row[uid] = _from_minor(paid - share)

        matrix[mrk_from_expense(trans_id, [trans_id])] = row

    return matrix


def validate_matrix_balance(
    expense_matrix: Dict[MatrixRowKey, Dict[UUID, float]],
) -> bool:
    """Pure. Every row must sum to 0.0 in minor units, else ACCOUNTING."""
    for row_key, row in expense_matrix.items():
        total_minor = sum(_to_minor(v) for v in row.values())
        if total_minor != 0:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Matrix row {row_key} sums to {_from_minor(total_minor)}, expected 0",
            )
    return True


def calculate_net_dues(
    expense_matrix: Dict[MatrixRowKey, Dict[UUID, float]],
    users: Sequence[UUID],
) -> Dict[UUID, float]:
    """Pure. Column sums of the matrix: positive = group owes user, negative = user owes."""
    net = {uid: 0.0 for uid in users}
    for row in expense_matrix.values():
        for uid, value in row.items():
            if uid in net:
                net[uid] += value
    return net


def apply_settled_payouts(
    expense_matrix: Dict[MatrixRowKey, Dict[UUID, float]],
    payouts: Sequence[Payout],
    users: Sequence[UUID],
) -> Dict[MatrixRowKey, Dict[UUID, float]]:
    """
    Pure. Returns a NEW matrix with one extra row per SETTLED, linked payout:
    payor +amt, payee -amt. Other statuses and orphan ad-hoc payments are
    ignored. Raises ACCOUNTING if payor/payee is not a member.
    """
    members = set(users)
    new_matrix = dict(expense_matrix)
    for payout in payouts:
        if payout.status != PayoutStatus.SETTLED.value:
            continue
        if not payout.expense_ids:
            continue  # orphan ad-hoc payment: no effect
        if payout.payor_id not in members:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Payout {payout.id}: payor {payout.payor_id} is not a member",
            )
        if payout.payee_id not in members:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Payout {payout.id}: payee {payout.payee_id} is not a member",
            )
        row = {uid: 0.0 for uid in users}
        amt = float(payout.amt)
        row[payout.payor_id] += amt
        row[payout.payee_id] -= amt
        new_matrix[mrk_from_payout(payout.id, payout.expense_ids)] = row
    return new_matrix


def generate_row_payouts(
    row_key: MatrixRowKey,
    row: Dict[UUID, float],
) -> List[RowPayout]:
    """
    Pure. Debtors (cell < 0) pay creditors (cell > 0) of this row only; zero
    cells never appear. Largest debtor matched to largest creditor, ties by
    str(user_id): exact in minor units, deterministic, at most
    debtors + creditors - 1 instructions.
    """
    debtors = sorted(
        ((uid, _to_minor(v)) for uid, v in row.items() if _to_minor(v) < 0),
        key=lambda item: (item[1], str(item[0])),  # most indebted first
    )
    creditors = sorted(
        ((uid, _to_minor(v)) for uid, v in row.items() if _to_minor(v) > 0),
        key=lambda item: (-item[1], str(item[0])),  # largest creditor first
    )

    payouts: List[RowPayout] = []
    i, j = 0, 0
    while i < len(debtors) and j < len(creditors):
        payor_id, debt = debtors[i]
        payee_id, credit = creditors[j]
        amount = min(-debt, credit)  # debt is negative
        if amount > 0:
            payouts.append(
                RowPayout(
                    row_key=row_key,
                    payor_id=payor_id,
                    payee_id=payee_id,
                    amount_minor=amount,
                )
            )
        debtors[i] = (payor_id, debt + amount)
        creditors[j] = (payee_id, credit - amount)
        if debtors[i][1] == 0:
            i += 1
        if creditors[j][1] == 0:
            j += 1
    return payouts


def consolidate_payouts(
    row_payouts: Sequence[RowPayout],
    group_id: UUID,
    open_payouts: Sequence[Payout],
) -> List[Payout]:
    """
    Pure. Nets all row payouts between each unordered user pair and subtracts
    open (PENDING / PAID, linked) payouts already issued (commitment rule),
    emitting at most one PENDING payout per pair for the remainder.
    expense_ids = every expense contributing to the pair in either direction.
    """
    # Signed net per canonical pair: positive => first(sorted) user owes second.
    pair_net_minor: Dict[Tuple[str, str], int] = {}
    pair_users: Dict[Tuple[str, str], Tuple[UUID, UUID]] = {}
    pair_expenses: Dict[Tuple[str, str], set] = {}
    for rp in row_payouts:
        a_str, b_str = str(rp.payor_id), str(rp.payee_id)
        key = tuple(sorted((a_str, b_str)))
        # payor owes payee => if payor is the canonical first user, +amount
        sign = 1 if a_str == key[0] else -1
        pair_net_minor[key] = pair_net_minor.get(key, 0) + sign * rp.amount_minor
        pair_users.setdefault(key, (UUID(key[0]), UUID(key[1])))
        pair_expenses.setdefault(key, set()).update(rp.row_key.expense_ids)

    # Subtract open (PENDING/PAID, linked) payouts already issued for the pair.
    open_by_pair: Dict[Tuple[str, str], int] = {}
    for payout in open_payouts:
        if payout.status not in (PayoutStatus.PENDING.value, PayoutStatus.PAID.value):
            continue
        if not payout.expense_ids:
            continue
        a_str, b_str = str(payout.payor_id), str(payout.payee_id)
        key = tuple(sorted((a_str, b_str)))
        sign = 1 if a_str == key[0] else -1
        open_by_pair[key] = open_by_pair.get(key, 0) + sign * _to_minor(payout.amt)

    proposed: List[Payout] = []
    for key, net_minor in pair_net_minor.items():
        open_net = open_by_pair.get(key, 0)
        remainder = net_minor - open_net
        if remainder == 0:
            continue
        first_user, second_user = pair_users[key]
        if remainder > 0:
            payor_id, payee_id = first_user, second_user
            amt_minor = remainder
        else:
            payor_id, payee_id = second_user, first_user
            amt_minor = -remainder
        proposed.append(
            Payout(
                id=_new_id(),
                group_id=group_id,
                payor_id=payor_id,
                payee_id=payee_id,
                amt=_from_minor(amt_minor),
                status=PayoutStatus.PENDING.value,
                expense_ids=sorted(pair_expenses[key]),
                is_adhoc=False,
                settlement_round_id=None,
                replaces_payout_ids=[],
            )
        )
    return proposed


# ---------------------------------------------------------------------------
# Shared loader + planner
# ---------------------------------------------------------------------------
class _GroupState:
    def __init__(
        self,
        group: Group,
        expenses: List[Expense],
        all_payouts: List[Payout],
        open_payouts: List[Payout],
        orphan_payouts: List[Payout],
        expense_matrix: Dict[MatrixRowKey, Dict[UUID, float]],
        net_dues: Dict[UUID, float],
    ):
        self.group = group
        self.expenses = expenses
        self.all_payouts = all_payouts
        self.open_payouts = open_payouts
        self.orphan_payouts = orphan_payouts
        self.expense_matrix = expense_matrix
        self.net_dues = net_dues


def _group_state(storage: storage_interface, group_id: UUID) -> _GroupState:
    group = _load_group(storage, group_id)
    users = list(group.members or [])
    expenses = _list_expenses(storage, group_id)
    payout_records = _list_payout_records(storage, group_id)
    all_payouts = [_payout_from_record(r) for r in payout_records]
    open_payouts = [
        p for p in all_payouts if p.status in (PayoutStatus.PENDING.value, PayoutStatus.PAID.value)
    ]
    orphan_payouts = [p for p in all_payouts if p.is_adhoc and not p.expense_ids]

    base_matrix = build_expense_matrix(expenses, users)
    full_matrix = apply_settled_payouts(base_matrix, all_payouts, users)
    validate_matrix_balance(full_matrix)
    net_dues = calculate_net_dues(full_matrix, users)
    return _GroupState(
        group=group,
        expenses=expenses,
        all_payouts=all_payouts,
        open_payouts=open_payouts,
        orphan_payouts=orphan_payouts,
        expense_matrix=full_matrix,
        net_dues=net_dues,
    )


def _assert_payouts_reproduce_net_dues(
    proposed: Sequence[Payout],
    open_payouts: Sequence[Payout],
    net_dues: Dict[UUID, float],
) -> None:
    """Proposed + open payouts must reproduce the net dues exactly."""
    remaining = dict(net_dues)
    for payout in list(open_payouts) + list(proposed):
        amt = float(payout.amt)
        remaining[payout.payor_id] = remaining.get(payout.payor_id, 0.0) + amt
        remaining[payout.payee_id] = remaining.get(payout.payee_id, 0.0) - amt
    for uid, value in remaining.items():
        if abs(value) > 0.001:
            raise SettlementError(
                SettlementErrorCode.ACCOUNTING,
                f"Payouts do not reproduce net dues for {uid}: residual {_from_minor(_to_minor(value))}",
            )


def _link_replacements(
    proposed: Sequence[Payout],
    all_payouts: Sequence[Payout],
) -> List[Payout]:
    """FAILED/CANCELLED linked payouts whose pairs are re-issued get
    replaces_payout_ids pointing at the new payout."""
    failed_or_cancelled = [
        p
        for p in all_payouts
        if p.status in (PayoutStatus.FAILED.value, PayoutStatus.CANCELLED.value)
    ]
    result: List[Payout] = []
    for payout in proposed:
        replaces: List[UUID] = []
        payout_pair = {str(payout.payor_id), str(payout.payee_id)}
        for old in failed_or_cancelled:
            old_pair = {str(old.payor_id), str(old.payee_id)}
            if old_pair == payout_pair:
                replaces.append(old.id)
        result.append(
            payout.model_copy(update={"replaces_payout_ids": replaces})
        )
    return result


def _propose(
    state: _GroupState,
) -> List[Payout]:
    """Pure-ish planner: row payouts -> consolidation -> invariant."""
    row_payouts: List[RowPayout] = []
    for row_key, row in state.expense_matrix.items():
        row_payouts.extend(generate_row_payouts(row_key, row))
    proposed = consolidate_payouts(row_payouts, state.group.id, state.open_payouts)
    proposed = _link_replacements(proposed, state.all_payouts)
    _assert_payouts_reproduce_net_dues(proposed, state.open_payouts, state.net_dues)
    return proposed


# ---------------------------------------------------------------------------
# Notifications
# ---------------------------------------------------------------------------
def _notify_payout(
    storage: storage_interface,
    payout: Payout,
    kind: NotificationKind,
    message: str,
    users: Iterable[UUID],
) -> None:
    for uid in users:
        from . import notification_service

        notification_service.create_notification(
            storage=storage,
            user_id=uid,
            kind=kind.value,
            message=message,
            group_id=payout.group_id,
            payout_id=payout.id,
        )


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------
def preview_settlement(
    storage: storage_interface,
    group_id: UUID,
    user_id: Optional[UUID] = None,
) -> SettlementPreview:
    """Backs GET /groups/{id}/settle. Any group status; writes nothing."""
    state = _group_state(storage, group_id)
    proposed = _propose(state)
    if user_id is not None:
        open_list = [
            p
            for p in state.open_payouts
            if p.payor_id == user_id or p.payee_id == user_id
        ]
        proposed_list = [
            p for p in proposed if p.payor_id == user_id or p.payee_id == user_id
        ]
        orphan_list = [
            p for p in state.orphan_payouts if p.payor_id == user_id or p.payee_id == user_id
        ]
    else:
        open_list = state.open_payouts
        proposed_list = proposed
        orphan_list = state.orphan_payouts
    return SettlementPreview(
        group_id=group_id,
        group_status=state.group.status.value if isinstance(state.group.status, GroupStatus) else str(state.group.status),
        net_dues=state.net_dues,
        open_payouts=open_list,
        proposed_payouts=proposed_list,
        orphan_payouts=orphan_list,
    )


def create_settlement_round(
    storage: storage_interface,
    group_id: UUID,
    kind: SettlementKind,
    payouts: Sequence[Payout],
    actor_user_id: UUID,
    requested_for: Optional[UUID] = None,
) -> SettlementRound:
    """Saves an ACTIVE round and its payouts as PENDING; notifies both sides."""
    now = _now()
    round_id = _new_id()
    round_record = SettlementRoundRecord(
        id=round_id,
        group_id=group_id,
        kind=kind.value,
        status=SettlementRoundStatus.ACTIVE.value,
        requested_for=requested_for,
        completed_at=None,
        created_by=actor_user_id,
        created_at=now,
        modified_by=actor_user_id,
        modified_at=now,
    )
    storage.save_record(round_record, schema="settlement_round_record")

    for payout in payouts:
        record = _payout_to_record(
            payout.model_copy(
                update={
                    "settlement_round_id": round_id,
                    "status": PayoutStatus.PENDING.value,
                }
            ),
            actor_user_id,
        )
        storage.save_record(record, schema="payout_record")
        _notify_payout(
            storage,
            payout,
            NotificationKind.PAYOUT_ISSUED,
            f"Payout of {payout.amt} issued: {payout.payor_id} -> {payout.payee_id}",
            users={payout.payor_id, payout.payee_id},
        )
    return SettlementRound(
        id=round_id,
        group_id=group_id,
        kind=kind.value,
        status=SettlementRoundStatus.ACTIVE.value,
        requested_for=requested_for,
        completed_at=None,
    )


def settle_user(
    storage: storage_interface,
    group_id: UUID,
    user_id: UUID,
    actor_user_id: UUID,
    include_receivables: bool = False,
) -> List[Payout]:
    """INDIVIDUAL round. Group OPEN or LOCKED; user must be a member. Issues
    the outstanding remainder where the user is payor (plus payee with
    include_receivables). Nothing new -> no round (idempotent)."""
    state = _group_state(storage, group_id)
    members = set(state.group.members or [])
    if user_id not in members:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, f"User {user_id} is not a member"
        )
    if actor_user_id not in members:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, f"Actor {actor_user_id} is not a member"
        )
    group_status = (
        state.group.status
        if isinstance(state.group.status, GroupStatus)
        else GroupStatus(state.group.status)
    )
    if group_status not in (GroupStatus.OPEN, GroupStatus.LOCKED):
        raise SettlementError(
            SettlementErrorCode.GROUP_STATE,
            f"Individual settlement requires an OPEN or LOCKED group, got {group_status}",
        )

    proposed = _propose(state)
    keep = [
        p
        for p in proposed
        if p.payor_id == user_id or (include_receivables and p.payee_id == user_id)
    ]
    if not keep:
        return []

    round_dto = create_settlement_round(
        storage, group_id, SettlementKind.INDIVIDUAL, keep, actor_user_id, requested_for=user_id
    )
    return [
        p.model_copy(update={"settlement_round_id": round_dto.id}) for p in keep
    ]


def initiate_settlement(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
    kind: SettlementKind,
) -> List[Payout]:
    """PERIODIC: OPEN or LOCKED, status unchanged; FINAL: LOCKED ->
    PARTIAL_SETTLEMENT (immediately SETTLED when nothing is owed)."""
    state = _group_state(storage, group_id)
    members = set(state.group.members or [])
    if actor_user_id not in members:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, f"Actor {actor_user_id} is not a member"
        )
    group_status = (
        state.group.status
        if isinstance(state.group.status, GroupStatus)
        else GroupStatus(state.group.status)
    )

    if kind == SettlementKind.FINAL:
        if group_status not in (GroupStatus.LOCKED, GroupStatus.PARTIAL_SETTLEMENT):
            raise SettlementError(
                SettlementErrorCode.GROUP_NOT_LOCKED,
                "FINAL settlement requires a LOCKED group",
            )
        if state.orphan_payouts:
            raise SettlementError(
                SettlementErrorCode.FINAL_SETTLEMENT,
                "Orphan ad-hoc payments must be linked before FINAL settlement",
            )
    elif group_status not in (GroupStatus.OPEN, GroupStatus.LOCKED):
        raise SettlementError(
            SettlementErrorCode.GROUP_STATE,
            f"Settlement requires an OPEN or LOCKED group, got {group_status}",
        )

    proposed = _propose(state)

    if not proposed:
        if kind == SettlementKind.FINAL and group_status == GroupStatus.LOCKED:
            finalize_settlement(
                storage, group_id, state.net_dues, state.all_payouts, actor_user_id
            )
        return []

    round_dto = create_settlement_round(storage, group_id, kind, proposed, actor_user_id)

    if kind == SettlementKind.FINAL and group_status == GroupStatus.LOCKED:
        group_record = storage.get_record(record_id=str(group_id), schema="group_record")
        group_record.status = GroupStatus.PARTIAL_SETTLEMENT.value
        group_record.modified_at = _now()
        group_record.modified_by = actor_user_id
        storage.save_record(group_record, schema="group_record")

    return [p.model_copy(update={"settlement_round_id": round_dto.id}) for p in proposed]


def finalize_settlement(
    storage: storage_interface,
    group_id: UUID,
    net_dues: Dict[UUID, float],
    payouts: Sequence[Payout],
    actor_user_id: UUID,
) -> Group:
    """Only place a group becomes SETTLED. FinalSettlementError via
    SettlementError(FINAL_SETTLEMENT) if any net due != 0, any payout is
    PENDING or PAID, or any SETTLED ad-hoc payment is unlinked."""
    for uid, value in net_dues.items():
        if abs(value) > 0.001:
            raise SettlementError(
                SettlementErrorCode.FINAL_SETTLEMENT,
                f"Net due for {uid} is non-zero: {_from_minor(_to_minor(value))}",
            )
    for payout in payouts:
        if payout.status in (PayoutStatus.PENDING.value, PayoutStatus.PAID.value):
            raise SettlementError(
                SettlementErrorCode.FINAL_SETTLEMENT,
                f"Payout {payout.id} is still {payout.status}",
            )
        if (
            payout.is_adhoc
            and payout.status == PayoutStatus.SETTLED.value
            and not payout.expense_ids
        ):
            raise SettlementError(
                SettlementErrorCode.FINAL_SETTLEMENT,
                f"Ad-hoc payout {payout.id} is unlinked",
            )

    now = _now()
    for record in _list_round_records(storage, group_id):
        if record.status == SettlementRoundStatus.ACTIVE.value:
            record.status = SettlementRoundStatus.COMPLETED.value
            record.completed_at = now
            record.modified_at = now
            record.modified_by = actor_user_id
            storage.save_record(record, schema="settlement_round_record")

    group_record = storage.get_record(record_id=str(group_id), schema="group_record")
    group_record.status = GroupStatus.SETTLED.value
    group_record.modified_at = now
    group_record.modified_by = actor_user_id
    storage.save_record(group_record, schema="group_record")

    for uid in group_record.members or []:
        from . import notification_service

        notification_service.create_notification(
            storage=storage,
            user_id=UUID(str(uid)),
            kind=NotificationKind.GROUP_SETTLED.value,
            message=f"Group {group_record.name} has been settled",
            group_id=group_id,
        )
    return _load_group(storage, group_id)


def _complete_rounds(storage: storage_interface, group_id: UUID) -> None:
    """Mark ACTIVE rounds COMPLETED once none of their payouts is PENDING."""
    now = _now()
    round_records = _list_round_records(storage, group_id)
    payout_records = _list_payout_records(storage, group_id)
    for record in round_records:
        if record.status != SettlementRoundStatus.ACTIVE.value:
            continue
        round_payouts = [p for p in payout_records if p.settlement_round_id == record.id]
        if round_payouts and all(p.status != PayoutStatus.PENDING.value for p in round_payouts):
            record.status = SettlementRoundStatus.COMPLETED.value
            record.completed_at = now
            record.modified_at = now
            storage.save_record(record, schema="settlement_round_record")


def _maybe_finalize(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> None:
    """During PARTIAL_SETTLEMENT: if nothing open, no orphans and all net
    dues 0, finalize the group."""
    group = _load_group(storage, group_id)
    group_status = (
        group.status if isinstance(group.status, GroupStatus) else GroupStatus(group.status)
    )
    if group_status != GroupStatus.PARTIAL_SETTLEMENT:
        return
    state = _group_state(storage, group_id)
    if state.open_payouts:
        return
    if state.orphan_payouts:
        return
    if any(abs(v) > 0.001 for v in state.net_dues.values()):
        return
    finalize_settlement(storage, group_id, state.net_dues, state.all_payouts, actor_user_id)


def _load_payout(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
) -> Tuple[PayoutRecord, Group]:
    group = _load_group(storage, group_id)
    group_status = (
        group.status if isinstance(group.status, GroupStatus) else GroupStatus(group.status)
    )
    if group_status == GroupStatus.SETTLED:
        raise SettlementError(SettlementErrorCode.GROUP_STATE, "Group is settled")
    record = _load_payout_record(storage, group_id, payout_id)
    return record, group


def mark_payout_paid(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
    evidence: Optional[str] = None,
) -> Payout:
    """Payor only. PENDING -> PAID, sets paid_at and payor_evidence."""
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    if str(record.payor_id) != str(actor_user_id):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_PERMISSION, "Only the payor can mark a payout paid"
        )
    if record.status != PayoutStatus.PENDING.value:
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, expected PENDING",
        )
    record.status = PayoutStatus.PAID.value
    record.paid_at = _now()
    record.payor_evidence = evidence or record.payor_evidence
    record.modified_at = _now()
    record.modified_by = actor_user_id
    storage.save_record(record, schema="payout_record")
    _notify_payout(
        storage,
        _payout_from_record(record),
        NotificationKind.PAYOUT_PAID,
        f"Payout {payout_id} marked paid by payor; awaiting payee review",
        users={record.payee_id},
    )
    return _payout_from_record(record)


def settle_payout(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
    evidence: Optional[str] = None,
) -> Payout:
    """Payee only. PENDING (cash/direct) or PAID -> SETTLED (final)."""
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    if str(record.payee_id) != str(actor_user_id):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_PERMISSION, "Only the payee can settle a payout"
        )
    if record.status not in (PayoutStatus.PENDING.value, PayoutStatus.PAID.value):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, expected PENDING or PAID",
        )
    record.status = PayoutStatus.SETTLED.value
    record.settled_at = _now()
    record.payee_evidence = evidence or record.payee_evidence
    record.modified_at = _now()
    record.modified_by = actor_user_id
    storage.save_record(record, schema="payout_record")
    _complete_rounds(storage, group_id)
    _notify_payout(
        storage,
        _payout_from_record(record),
        NotificationKind.PAYOUT_SETTLED,
        f"Payout {payout_id} confirmed as settled by payee",
        users={record.payor_id},
    )
    _maybe_finalize(storage, group_id, actor_user_id)
    return _payout_from_record(record)


def fail_payout(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
    reason: str,
    evidence: Optional[str] = None,
) -> Payout:
    """PAID -> FAILED (final). Reason required. Evidence to actor's slot."""
    if not reason or not reason.strip():
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, "A reason is required to fail a payout"
        )
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    if str(record.payor_id) != str(actor_user_id) and str(record.payee_id) != str(actor_user_id):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_PERMISSION,
            "Only the payor or payee can fail a payout",
        )
    if record.status != PayoutStatus.PAID.value:
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, expected PAID",
        )
    record.status = PayoutStatus.FAILED.value
    record.status_reason = reason
    record.modified_at = _now()
    record.modified_by = actor_user_id
    if str(record.payor_id) == str(actor_user_id):
        record.payor_evidence = evidence or record.payor_evidence
        other = record.payee_id
    else:
        record.payee_evidence = evidence or record.payee_evidence
        other = record.payor_id
    storage.save_record(record, schema="payout_record")
    _complete_rounds(storage, group_id)
    _notify_payout(
        storage,
        _payout_from_record(record),
        NotificationKind.PAYOUT_FAILED,
        f"Payout {payout_id} failed: {reason}",
        users={other},
    )
    _maybe_finalize(storage, group_id, actor_user_id)
    return _payout_from_record(record)


def cancel_payout(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
    reason: str,
) -> Payout:
    """Payor or payee. PENDING -> CANCELLED (final), reason required."""
    if not reason or not reason.strip():
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, "A reason is required to cancel a payout"
        )
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    if str(record.payor_id) != str(actor_user_id) and str(record.payee_id) != str(actor_user_id):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_PERMISSION,
            "Only the payor or payee can cancel a payout",
        )
    if record.status != PayoutStatus.PENDING.value:
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, expected PENDING",
        )
    record.status = PayoutStatus.CANCELLED.value
    record.status_reason = reason
    record.modified_at = _now()
    record.modified_by = actor_user_id
    storage.save_record(record, schema="payout_record")
    _complete_rounds(storage, group_id)
    other = record.payee_id if str(record.payor_id) == str(actor_user_id) else record.payor_id
    _notify_payout(
        storage,
        _payout_from_record(record),
        NotificationKind.PAYOUT_CANCELLED,
        f"Payout {payout_id} cancelled: {reason}",
        users={other},
    )
    _maybe_finalize(storage, group_id, actor_user_id)
    return _payout_from_record(record)


def remind_payout(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
) -> Payout:
    """PENDING: payee reminds payor. PAID: payor reminds payee."""
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    if record.status == PayoutStatus.PENDING.value:
        if str(record.payee_id) != str(actor_user_id):
            raise SettlementError(
                SettlementErrorCode.PAYOUT_PERMISSION,
                "Only the payee can remind the payor of a PENDING payout",
            )
        target = record.payor_id
        message = f"Reminder: payout {payout_id} is still pending payment"
    elif record.status == PayoutStatus.PAID.value:
        if str(record.payor_id) != str(actor_user_id):
            raise SettlementError(
                SettlementErrorCode.PAYOUT_PERMISSION,
                "Only the payor can remind the payee of a PAID payout",
            )
        target = record.payee_id
        message = f"Reminder: payout {payout_id} is awaiting your review"
    else:
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, cannot remind",
        )
    _notify_payout(
        storage,
        _payout_from_record(record),
        NotificationKind.PAYOUT_REMINDER,
        message,
        users={target},
    )
    return _payout_from_record(record)


def escalate_payout(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    actor_user_id: UUID,
) -> Payout:
    """Any member, once a PAID payout is older than PAYOUT_ESCALATION_DAYS
    since paid_at. Notifies every member."""
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    members = set(group.members or [])
    if actor_user_id not in members:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, "Only group members can escalate"
        )
    if record.status != PayoutStatus.PAID.value:
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, expected PAID",
        )
    if record.paid_at is None:
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE, "Payout has no paid_at timestamp"
        )
    paid_at = record.paid_at
    if paid_at.tzinfo is None:
        paid_at = paid_at.replace(tzinfo=timezone.utc)
    if paid_at + timedelta(days=PAYOUT_ESCALATION_DAYS) > _now():
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is not older than {PAYOUT_ESCALATION_DAYS} days",
        )
    _notify_payout(
        storage,
        _payout_from_record(record),
        NotificationKind.PAYOUT_ESCALATED,
        f"Payout {payout_id} escalated: payee has not reviewed for {PAYOUT_ESCALATION_DAYS} days",
        users=members,
    )
    return _payout_from_record(record)


def _validate_links(
    storage: storage_interface,
    group_id: UUID,
    expense_ids: Sequence[int],
    payor_id: UUID,
    payee_id: UUID,
) -> None:
    """Every expense must exist in the group and involve both users."""
    expenses = {int(e.trans_id): e for e in _list_expenses(storage, group_id)}
    members = None
    for tid in expense_ids:
        exp = expenses.get(int(tid))
        if exp is None:
            raise SettlementError(
                SettlementErrorCode.INVALID_REQUEST,
                f"Expense {tid} does not exist in group {group_id}",
            )
        involved = {s.user_id for s in exp.expn_splt} | {p.user_id for p in exp.paid_by}
        if payor_id not in involved or payee_id not in involved:
            raise SettlementError(
                SettlementErrorCode.INVALID_REQUEST,
                f"Expense {tid} does not involve both {payor_id} and {payee_id}",
            )


def record_adhoc_payment(
    storage: storage_interface,
    group_id: UUID,
    payor_id: UUID,
    payee_id: UUID,
    amt: float,
    actor_user_id: UUID,
    expense_ids: Optional[Sequence[int]] = None,
    evidence: Optional[str] = None,
) -> Payout:
    """Actor must be payor or payee; both members; amt > 0. By payor ->
    PAID (payee reviews); by payee -> SETTLED. Without expense_ids the
    payment is an orphan with no effect until linked."""
    if amt <= 0:
        raise SettlementError(SettlementErrorCode.INVALID_REQUEST, "Amount must be positive")
    group = _load_group(storage, group_id)
    members = set(group.members or [])
    if payor_id not in members or payee_id not in members:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, "Both users must be group members"
        )
    if actor_user_id not in (payor_id, payee_id):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_PERMISSION,
            "Actor must be the payor or the payee",
        )
    ids = [int(t) for t in (expense_ids or [])]
    if ids:
        _validate_links(storage, group_id, ids, payor_id, payee_id)

    status = (
        PayoutStatus.PAID.value if actor_user_id == payor_id else PayoutStatus.SETTLED.value
    )
    payout = Payout(
        id=_new_id(),
        group_id=group_id,
        payor_id=payor_id,
        payee_id=payee_id,
        amt=float(amt),
        status=status,
        expense_ids=ids,
        is_adhoc=True,
        settlement_round_id=None,
        replaces_payout_ids=[],
        payor_evidence=evidence if actor_user_id == payor_id else None,
        payee_evidence=evidence if actor_user_id == payee_id else None,
        paid_at=_now() if status == PayoutStatus.PAID.value else None,
        settled_at=_now() if status == PayoutStatus.SETTLED.value else None,
    )
    storage.save_record(_payout_to_record(payout, actor_user_id), schema="payout_record")
    _notify_payout(
        storage,
        payout,
        NotificationKind.ADHOC_PAYMENT,
        f"Ad-hoc payment of {amt} recorded: {payor_id} -> {payee_id}",
        users={payor_id, payee_id},
    )
    _maybe_finalize(storage, group_id, actor_user_id)
    return payout


def link_payout_expenses(
    storage: storage_interface,
    group_id: UUID,
    payout_id: UUID,
    expense_ids: Sequence[int],
    actor_user_id: UUID,
) -> Payout:
    """Payor or payee; ad-hoc payouts in PAID or SETTLED only. Adds to
    existing links; an orphan becomes effective."""
    record, group = _load_payout(storage, group_id, payout_id, actor_user_id)
    if str(record.payor_id) != str(actor_user_id) and str(record.payee_id) != str(actor_user_id):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_PERMISSION,
            "Only the payor or payee can link expenses",
        )
    if not record.is_adhoc:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST,
            "Only ad-hoc payouts can have expenses linked",
        )
    if record.status not in (PayoutStatus.PAID.value, PayoutStatus.SETTLED.value):
        raise SettlementError(
            SettlementErrorCode.PAYOUT_STATE,
            f"Payout is {record.status}, expected PAID or SETTLED",
        )
    ids = [int(t) for t in (expense_ids or [])]
    if not ids:
        raise SettlementError(
            SettlementErrorCode.INVALID_REQUEST, "expense_ids must not be empty"
        )
    _validate_links(storage, group_id, ids, record.payor_id, record.payee_id)
    existing = [int(t) for t in (record.expense_ids or [])]
    merged = sorted(set(existing) | set(ids))
    record.expense_ids = merged
    record.modified_at = _now()
    record.modified_by = actor_user_id
    storage.save_record(record, schema="payout_record")
    _maybe_finalize(storage, group_id, actor_user_id)
    return _payout_from_record(record)


def list_payouts(
    storage: storage_interface,
    group_id: UUID,
    user_id: Optional[UUID] = None,
) -> List[Payout]:
    """All payouts of a group, every round and status. With user_id: only
    payouts where the user is payor or payee."""
    _load_group(storage, group_id)
    payouts = [_payout_from_record(r) for r in _list_payout_records(storage, group_id)]
    if user_id is not None:
        payouts = [p for p in payouts if p.payor_id == user_id or p.payee_id == user_id]
    return payouts


def list_settlement_rounds(
    storage: storage_interface,
    group_id: UUID,
) -> List[SettlementRound]:
    """All settlement rounds of a group."""
    _load_group(storage, group_id)
    return [_round_from_record(r) for r in _list_round_records(storage, group_id)]
