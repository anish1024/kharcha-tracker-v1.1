"""
kharcha_v1/kharcha_gateway/kharcha_service/expense_service.py
Expense CRUD (FR-12..15). Every function takes storage first.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID, uuid4

from .constants.expense_category import ExpenseCategory
from .constants.expense_status import ExpenseStatus
from .constants.group_status import GroupStatus
from .constants.payout_status import PayoutStatus
from .constants.system_config import FLOAT_EPSILON
from .models.expense import Expense
from .models.expense_payment import ExpensePayment
from .models.expense_share import ExpenseShare
from .models.expense_update import ExpenseUpdate
from .storage_service.schema.audit_log import AuditLog
from .storage_service.schema.expense_record import ExpenseRecord
from .storage_service.schema.user_record import UserRecord
from .storage_service.storage_interface import StorageInterface as storage_interface


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _next_trans_id(storage: storage_interface) -> int:
    max_id = 0
    for record in storage.list_records(schema="expense_record"):
        trans_id = getattr(record, "trans_id", None)
        if trans_id is not None and int(trans_id) > max_id:
            max_id = int(trans_id)
    return max_id + 1


def _to_expense_dto(record: ExpenseRecord) -> Expense:
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
            payment if isinstance(payment, ExpensePayment) else ExpensePayment.model_validate(payment)
            for payment in (record.paid_by or [])
        ],
        expn_bill_screenshot=record.expn_bill_screenshot or "",
        expn_payment_screenshot=record.expn_payment_screenshot or "",
        status=record.status,
    )


def _record_expense_audit(
    storage: storage_interface,
    action: str,
    record: ExpenseRecord,
    actor_user_id: UUID,
    old_value: Optional[str] = None,
) -> None:
    now = _now()
    actor = storage.get_record(record_id=str(actor_user_id), schema="user_record")
    actor_name = (
        actor.name if isinstance(actor, UserRecord) and not actor.is_deleted else None
    )
    desc = getattr(record, "expn_desc", "") or ""
    amount = f"{float(record.expn_amnt):g}"
    label = f"“{desc}” ({amount})" if desc else f"({amount})"
    verb = {"CREATE": "Added", "UPDATE": "Updated", "DELETE": "Deleted"}.get(action, action)
    audit = AuditLog(
        id=uuid4(),
        event=action,
        entity_type="expense",
        entity_id=record.id,
        action=action,
        old_value=old_value,
        new_value=f"{verb} expense {label}",
        actor_user_id=actor_user_id,
        actor_name=actor_name,
        group_id=record.group_id,
        created_by=actor_user_id,
        created_at=now,
        modified_at=now,
    )
    storage.save_record(audit, schema="audit_log")


def _load_group(storage: storage_interface, group_id: UUID):
    group = storage.get_record(record_id=str(group_id), schema="group_record")
    if not group or getattr(group, "is_deleted", False):
        raise LookupError(f"Group {group_id} not found")
    return group


def _find_expense_record(storage: storage_interface, trans_id: int) -> ExpenseRecord:
    for record in storage.list_records(schema="expense_record"):
        if getattr(record, "trans_id", None) is not None and int(record.trans_id) == int(trans_id):
            if getattr(record, "is_deleted", False):
                break
            return record  # type: ignore[return-value]
    raise LookupError(f"Expense {trans_id} not found")


def add_expense(
    storage: storage_interface,
    expense: Expense,
    actor_user_id: UUID,
) -> Expense:
    """FR-12/13. Group must be OPEN; every split/payer user must be a member;
    expn_ctgry must be an expense_category value (SETTLEMENT is retired —
    payments go through the payout table). trans_id is assigned by storage."""
    group = _load_group(storage, expense.group_id)
    if group.status != GroupStatus.OPEN.value:
        raise ValueError(f"Cannot add expense to group in {group.status} status")

    members = set(group.members or [])
    if actor_user_id not in members:
        raise PermissionError("Only group members can add expenses")

    if expense.expn_ctgry not in {c.value for c in ExpenseCategory}:
        raise ValueError(f"Invalid expense category: {expense.expn_ctgry}")
    if expense.expn_amnt <= 0:
        raise ValueError("Expense amount must be positive")
    if not expense.expn_splt:
        raise ValueError("Expense must have at least one split share")
    if not expense.paid_by:
        raise ValueError("Expense must record at least one payment")

    seen: set[UUID] = set()
    total_pct = 0.0
    for share in expense.expn_splt:
        if share.user_id not in members:
            raise ValueError(f"Split user {share.user_id} is not a member of the group")
        if share.percentage < 0:
            raise ValueError("Split percentages cannot be negative")
        if share.user_id in seen:
            raise ValueError(f"Duplicate split entry for user {share.user_id}")
        seen.add(share.user_id)
        total_pct += share.percentage
    if abs(total_pct - 100.0) > FLOAT_EPSILON:
        raise ValueError(f"Split percentages must sum to 100 (got {total_pct})")

    payers: set[UUID] = set()
    paid_total = 0.0
    for payment in expense.paid_by:
        if payment.user_id not in members:
            raise ValueError(f"Payer {payment.user_id} is not a member of the group")
        if payment.amt < 0:
            raise ValueError("Payment amounts cannot be negative")
        if payment.user_id in payers:
            raise ValueError(f"Duplicate payment entry for user {payment.user_id}")
        payers.add(payment.user_id)
        paid_total += payment.amt
    if abs(paid_total - expense.expn_amnt) > FLOAT_EPSILON:
        raise ValueError(
            f"Paid total {paid_total} does not match expense amount {expense.expn_amnt}"
        )

    now = _now()
    record = ExpenseRecord(
        trans_id=_next_trans_id(storage),
        group_id=expense.group_id,
        date_time=expense.date_time or now,
        expn_ctgry=expense.expn_ctgry,
        expn_desc=expense.expn_desc,
        expn_amnt=expense.expn_amnt,
        expn_splt=list(expense.expn_splt),
        paid_by=list(expense.paid_by),
        expn_bill_screenshot=expense.expn_bill_screenshot or None,
        expn_payment_screenshot=expense.expn_payment_screenshot or None,
        status=ExpenseStatus.OPEN.value,
        is_deleted=False,
        created_by=actor_user_id,
        created_at=now,
        modified_by=actor_user_id,
        modified_at=now,
    )
    saved = storage.save_record(record, schema="expense_record")
    _record_expense_audit(storage, "CREATE", saved, actor_user_id)
    return _to_expense_dto(saved)


def list_group_expenses(
    storage: storage_interface,
    group_id: UUID,
    limit: int = 100,
    offset: int = 0,
) -> List[Expense]:
    """Non-deleted expenses of a group, newest first."""
    _load_group(storage, group_id)
    records = storage.list_records(
        schema="expense_record",
        filters={"group_id": [str(group_id)]},
        sort_by="trans_id",
        ascending=False,
        limit=limit,
        offset=offset,
    )
    return [_to_expense_dto(record) for record in records]  # type: ignore[arg-type]


def get_expense(
    storage: storage_interface,
    trans_id: int,
) -> Expense:
    """LookupError if missing or deleted."""
    return _to_expense_dto(_find_expense_record(storage, trans_id))


def update_expense(
    storage: storage_interface,
    trans_id: int,
    changes: ExpenseUpdate,
    actor_user_id: UUID,
) -> Expense:
    """FR-15. Descriptive fields (date_time, expn_ctgry, expn_desc,
    expn_bill_screenshot) plus optional paid_by re-contribution. ValueError
    unless the group is OPEN."""
    record = _find_expense_record(storage, trans_id)
    group = _load_group(storage, record.group_id)
    if group.status != GroupStatus.OPEN.value:
        raise ValueError(f"Cannot update expense while group is {group.status}")
    members = set(group.members or [])
    if actor_user_id not in members:
        raise PermissionError("Only group members can update expenses")

    if changes.expn_ctgry is not None:
        if changes.expn_ctgry not in {c.value for c in ExpenseCategory}:
            raise ValueError(f"Invalid expense category: {changes.expn_ctgry}")
        record.expn_ctgry = changes.expn_ctgry
    if changes.date_time is not None:
        record.date_time = changes.date_time
    if changes.expn_desc is not None:
        record.expn_desc = changes.expn_desc
    if changes.expn_bill_screenshot is not None:
        record.expn_bill_screenshot = changes.expn_bill_screenshot

    if changes.paid_by is not None:
        if not changes.paid_by:
            raise ValueError("Expense must record at least one payment")
        payers: set[UUID] = set()
        paid_total = 0.0
        for payment in changes.paid_by:
            if payment.user_id not in members:
                raise ValueError(f"Payer {payment.user_id} is not a member of the group")
            if payment.amt < 0:
                raise ValueError("Payment amounts cannot be negative")
            if payment.user_id in payers:
                raise ValueError(f"Duplicate payment entry for user {payment.user_id}")
            payers.add(payment.user_id)
            paid_total += payment.amt
        if abs(paid_total - float(record.expn_amnt)) > FLOAT_EPSILON:
            raise ValueError(
                f"Paid total {paid_total} does not match expense amount {record.expn_amnt}"
            )
        record.paid_by = list(changes.paid_by)

    record.modified_at = _now()
    record.modified_by = actor_user_id
    saved = storage.save_record(record, schema="expense_record")
    _record_expense_audit(storage, "UPDATE", saved, actor_user_id)
    return _to_expense_dto(saved)  # type: ignore[arg-type]


def delete_expense(
    storage: storage_interface,
    trans_id: int,
    actor_user_id: UUID,
) -> bool:
    """FR-15. Soft delete. ValueError unless the group is OPEN, or when any
    PAID or SETTLED payout lists this expense in expense_ids."""
    record = _find_expense_record(storage, trans_id)
    group = _load_group(storage, record.group_id)
    if group.status != GroupStatus.OPEN.value:
        raise ValueError(f"Cannot delete expense while group is {group.status}")
    if actor_user_id not in set(group.members or []):
        raise PermissionError("Only group members can delete expenses")

    open_statuses = {PayoutStatus.PAID.value, PayoutStatus.SETTLED.value}
    for payout in storage.list_records(schema="payout_record"):
        if getattr(payout, "status", None) not in open_statuses:
            continue
        linked = [int(t) for t in (getattr(payout, "expense_ids", None) or [])]
        if int(trans_id) in linked:
            raise ValueError(
                f"Cannot delete expense {trans_id}: payout {payout.id} is {payout.status}"
            )

    record.is_deleted = True
    record.modified_at = _now()
    record.modified_by = actor_user_id
    storage.save_record(record, schema="expense_record")
    _record_expense_audit(storage, "DELETE", record, actor_user_id)
    return True
