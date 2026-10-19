"""
kharcha_v1/tests/test_expense_service.py
Unit tests for expense CRUD and its validation rules (FR-12..15).
"""
from datetime import datetime, timezone
from uuid import UUID, uuid4

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import (
    auth_service,
    expense_service,
    group_service,
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
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense import Expense
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_payment import (
    ExpensePayment,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_share import ExpenseShare
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_update import ExpenseUpdate
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.payout_record import (
    PayoutRecord,
)


def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def _group(storage, owner):
    return group_service.create_group(storage, "Flat", "INR", owner.id)


def _expense(group_id, payer_id, members, amount=100.0):
    share = round(100.0 / len(members), 2)
    return Expense(
        trans_id=0,
        group_id=group_id,
        date_time=datetime.now(timezone.utc),
        expn_ctgry=ExpenseCategory.GROCERY.value,
        expn_desc="Groceries",
        expn_amnt=amount,
        expn_splt=[ExpenseShare(user_id=m, percentage=share) for m in members],
        paid_by=[ExpensePayment(user_id=payer_id, amt=amount)],
        expn_bill_screenshot="",
        expn_payment_screenshot="",
        status=ExpenseStatus.OPEN.value,
    )


def test_add_expense_happy_path(storage):
    owner = _user(storage, "e1@x.com", "Owner")
    friend = _user(storage, "e1b@x.com", "Friend")
    group = _group(storage, owner)
    group_service.add_member(storage, group.id, friend.email, owner.id)

    created = expense_service.add_expense(
        storage,
        _expense(group.id, owner.id, [owner.id, friend.id]),
        owner.id,
    )
    assert created.trans_id == 1
    assert created.status == ExpenseStatus.OPEN.value
    assert created.expn_amnt == 100.0
    assert len(created.expn_splt) == 2

    listed = expense_service.list_group_expenses(storage, group.id)
    assert [e.trans_id for e in listed] == [1]
    fetched = expense_service.get_expense(storage, 1)
    assert fetched.expn_desc == "Groceries"


def test_trans_id_increments(storage):
    owner = _user(storage, "e2@x.com", "Owner")
    group = _group(storage, owner)
    first = expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id]), owner.id
    )
    second = expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id]), owner.id
    )
    assert (first.trans_id, second.trans_id) == (1, 2)


def test_add_expense_requires_open_group(storage):
    owner = _user(storage, "e3@x.com", "Owner")
    group = _group(storage, owner)
    group_service.lock_group(storage, group.id, owner.id)

    with pytest.raises(ValueError):
        expense_service.add_expense(
            storage, _expense(group.id, owner.id, [owner.id]), owner.id
        )


def test_add_expense_rejects_non_member_split(storage):
    owner = _user(storage, "e4@x.com", "Owner")
    outsider = _user(storage, "e4b@x.com", "Outsider")
    group = _group(storage, owner)

    with pytest.raises(ValueError):
        expense_service.add_expense(
            storage, _expense(group.id, owner.id, [owner.id, outsider.id]), owner.id
        )


def test_add_expense_rejects_percentages_not_100(storage):
    owner = _user(storage, "e5@x.com", "Owner")
    group = _group(storage, owner)
    expense = _expense(group.id, owner.id, [owner.id])
    expense.expn_splt = [ExpenseShare(user_id=owner.id, percentage=90.0)]

    with pytest.raises(ValueError):
        expense_service.add_expense(storage, expense, owner.id)


def test_add_expense_rejects_paid_amount_mismatch(storage):
    owner = _user(storage, "e6@x.com", "Owner")
    group = _group(storage, owner)
    expense = _expense(group.id, owner.id, [owner.id])
    expense.paid_by = [ExpensePayment(user_id=owner.id, amt=50.0)]

    with pytest.raises(ValueError):
        expense_service.add_expense(storage, expense, owner.id)


def test_add_expense_rejects_retired_settlement_category(storage):
    owner = _user(storage, "e7@x.com", "Owner")
    group = _group(storage, owner)
    expense = _expense(group.id, owner.id, [owner.id])
    expense.expn_ctgry = "SETTLEMENT"

    with pytest.raises(ValueError):
        expense_service.add_expense(storage, expense, owner.id)


def test_add_expense_requires_membership(storage):
    owner = _user(storage, "e8@x.com", "Owner")
    outsider = _user(storage, "e8b@x.com", "Outsider")
    group = _group(storage, owner)

    with pytest.raises(PermissionError):
        expense_service.add_expense(
            storage, _expense(group.id, owner.id, [owner.id]), outsider.id
        )


def test_update_expense_descriptive_fields_only(storage):
    owner = _user(storage, "e9@x.com", "Owner")
    group = _group(storage, owner)
    created = expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id]), owner.id
    )

    updated = expense_service.update_expense(
        storage,
        created.trans_id,
        ExpenseUpdate(expn_desc="Weekly groceries", expn_ctgry=ExpenseCategory.FOOD_N_DINING.value),
        owner.id,
    )
    assert updated.expn_desc == "Weekly groceries"
    assert updated.expn_ctgry == ExpenseCategory.FOOD_N_DINING.value
    assert updated.expn_amnt == created.expn_amnt


def test_update_expense_rejected_when_group_locked(storage):
    owner = _user(storage, "e10@x.com", "Owner")
    group = _group(storage, owner)
    created = expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id]), owner.id
    )
    group_service.lock_group(storage, group.id, owner.id)

    with pytest.raises(ValueError):
        expense_service.update_expense(
            storage, created.trans_id, ExpenseUpdate(expn_desc="nope"), owner.id
        )


def test_delete_expense_soft_deletes(storage):
    owner = _user(storage, "e11@x.com", "Owner")
    group = _group(storage, owner)
    created = expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id]), owner.id
    )

    assert expense_service.delete_expense(storage, created.trans_id, owner.id) is True
    with pytest.raises(LookupError):
        expense_service.get_expense(storage, created.trans_id)
    assert expense_service.list_group_expenses(storage, group.id) == []


def test_delete_expense_blocked_by_settled_payout(storage):
    owner = _user(storage, "e12@x.com", "Owner")
    group = _group(storage, owner)
    created = expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id]), owner.id
    )
    storage.save_record(
        PayoutRecord(
            id=uuid4(),
            group_id=group.id,
            payor_id=owner.id,
            payee_id=owner.id,
            amt=100.0,
            status=PayoutStatus.SETTLED.value,
            expense_ids=[created.trans_id],
        ),
        schema="payout_record",
    )

    with pytest.raises(ValueError):
        expense_service.delete_expense(storage, created.trans_id, owner.id)


def test_get_expense_unknown_trans_id(storage):
    with pytest.raises(LookupError):
        expense_service.get_expense(storage, 9999)
