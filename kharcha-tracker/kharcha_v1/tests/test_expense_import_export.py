"""
kharcha_v1/tests/test_expense_import_export.py
Unit tests for expense export/import: envelope shape, name<->id mapping,
re-validation (category default, date validity) and partial import.
"""
import json
import logging
from datetime import datetime, timezone

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
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense import Expense
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_payment import (
    ExpensePayment,
)
from kharcha_v1.kharcha_gateway.kharcha_service.models.expense_share import ExpenseShare

EXPORT_ROW_KEYS = {"date", "description", "amount", "category", "paid_by", "split_by"}


def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def _group(storage, owner):
    return group_service.create_group(storage, "Flat", "INR", owner.id)


def _expense(group_id, payer_id, members, amount=100.0, category=ExpenseCategory.GROCERY.value):
    share = round(100.0 / len(members), 2)
    return Expense(
        trans_id=0,
        group_id=group_id,
        date_time=datetime(2024, 5, 2, 0, 0, 0, tzinfo=timezone.utc),
        expn_ctgry=category,
        expn_desc="Groceries",
        expn_amnt=amount,
        expn_splt=[ExpenseShare(user_id=m, percentage=share) for m in members],
        paid_by=[ExpensePayment(user_id=payer_id, amt=amount)],
        expn_bill_screenshot="",
        expn_payment_screenshot="",
        status=ExpenseStatus.OPEN.value,
    )


def _seed(storage, n_owner, n_friend):
    """Owner + friend group with one 50/50 expense paid by the owner."""
    owner = _user(storage, "ee1@x.com", n_owner)
    friend = _user(storage, "ee1b@x.com", n_friend)
    group = _group(storage, owner)
    group_service.add_member(storage, group.id, friend.email, owner.id)
    expense_service.add_expense(
        storage, _expense(group.id, owner.id, [owner.id, friend.id]), owner.id
    )
    return owner, friend, group


def test_export_envelope_shape(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)

    assert envelope["format"] == "kharcha-expenses"
    assert envelope["version"] == 1
    assert len(envelope["expenses"]) == 1
    row = envelope["expenses"][0]
    assert set(row.keys()) == EXPORT_ROW_KEYS
    assert row["description"] == "Groceries"
    assert row["amount"] == 100.0
    assert row["category"] == ExpenseCategory.GROCERY.value
    assert row["paid_by"] == [{"user_id": "Owner", "amt": 100.0}]
    assert row["split_by"] == [
        {"user_id": "Owner", "percentage": 50.0},
        {"user_id": "Friend", "percentage": 50.0},
    ]


def test_export_has_no_uuids_and_date_is_iso_z(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    text = json.dumps(expense_service.export_group_expenses(storage, group.id))
    # No 8-4-4-4-12 hex pattern (UUID) anywhere in the export.
    import re

    assert re.search(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", text) is None
    row = expense_service.export_group_expenses(storage, group.id)["expenses"][0]
    assert row["date"].endswith("Z")
    assert datetime.fromisoformat(row["date"].replace("Z", "+00:00")).tzinfo is not None


def test_export_unknown_group_raises(storage):
    owner = _user(storage, "ee2@x.com", "Owner")
    group = _group(storage, owner)
    group_service.delete_group(storage, group.id, owner.id)

    with pytest.raises(LookupError):
        expense_service.export_group_expenses(storage, group.id)


def test_import_maps_names_to_member_ids(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)

    result = expense_service.import_group_expenses(
        storage, group.id, envelope["expenses"], owner.id
    )
    assert result["imported"] == 1
    assert result["skipped"] == []

    listed = expense_service.list_group_expenses(storage, group.id)
    assert len(listed) == 2
    imported_row = listed[0]
    split_ids = {s.user_id for s in imported_row.expn_splt}
    assert split_ids == {owner.id, friend.id}
    assert imported_row.paid_by[0].user_id == owner.id
    assert imported_row.status == ExpenseStatus.OPEN.value


def test_import_unknown_category_defaults_to_custom(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)
    for row in envelope["expenses"]:
        row["category"] = "NOT_A_CATEGORY"

    result = expense_service.import_group_expenses(
        storage, group.id, envelope["expenses"], owner.id
    )
    assert result["imported"] == 1
    imported_row = expense_service.list_group_expenses(storage, group.id)[0]
    assert imported_row.expn_ctgry == ExpenseCategory.CUSTOM.value


def test_import_invalid_date_skipped(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)
    for row in envelope["expenses"]:
        row["date"] = "not-a-date"

    result = expense_service.import_group_expenses(
        storage, group.id, envelope["expenses"], owner.id
    )
    assert result["imported"] == 0
    assert len(result["skipped"]) == 1
    assert result["skipped"][0]["index"] == 0
    assert "date" in result["skipped"][0]["reason"]
    assert len(expense_service.list_group_expenses(storage, group.id)) == 1


def test_import_unknown_user_skipped(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    entry = {
        "date": "2024-05-02T00:00:00Z",
        "description": "Mystery",
        "amount": 50.0,
        "category": "GROCERY",
        "paid_by": [{"user_id": "Ghost", "amt": 50.0}],
        "split_by": [{"user_id": "Ghost", "percentage": 100.0}],
    }
    result = expense_service.import_group_expenses(storage, group.id, [entry], owner.id)
    assert result["imported"] == 0
    assert "Ghost" in result["skipped"][0]["reason"]


def test_import_names_match_case_insensitively(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)
    for row in envelope["expenses"]:
        for entry in row["paid_by"] + row["split_by"]:
            entry["user_id"] = str(entry["user_id"]).upper()

    result = expense_service.import_group_expenses(
        storage, group.id, envelope["expenses"], owner.id
    )
    assert result["imported"] == 1


def test_import_partial_reports_each_bad_row(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)
    rows = [json.loads(json.dumps(envelope["expenses"][0])) for _ in range(3)]
    rows[1]["date"] = "not-a-date"
    rows[2]["paid_by"][0]["user_id"] = "Ghost"

    result = expense_service.import_group_expenses(storage, group.id, rows, owner.id)
    assert result["imported"] == 1
    assert [s["index"] for s in result["skipped"]] == [1, 2]


def test_import_requires_membership(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    outsider = _user(storage, "ee3@x.com", "Outsider")
    envelope = expense_service.export_group_expenses(storage, group.id)

    with pytest.raises(PermissionError):
        expense_service.import_group_expenses(
            storage, group.id, envelope["expenses"], outsider.id
        )


def test_roundtrip_export_then_import_into_new_group(storage):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)

    # A fresh deployment: same two people, brand-new group.
    owner2 = _user(storage, "ee4@x.com", "Owner")
    friend2 = _user(storage, "ee4b@x.com", "Friend")
    group2 = _group(storage, owner2)
    group_service.add_member(storage, group2.id, friend2.email, owner2.id)

    result = expense_service.import_group_expenses(
        storage, group2.id, envelope["expenses"], owner2.id
    )
    assert result["imported"] == 1

    original = expense_service.list_group_expenses(storage, group.id)[0]
    moved = expense_service.list_group_expenses(storage, group2.id)[0]
    assert moved.expn_desc == original.expn_desc
    assert moved.expn_amnt == original.expn_amnt
    assert moved.expn_ctgry == original.expn_ctgry
    assert moved.date_time == original.date_time
    assert {s.percentage for s in moved.expn_splt} == {50.0, 50.0}
    assert moved.paid_by[0].amt == original.paid_by[0].amt


_EXPENSE_LOGGER = (
    "kharcha_v1.kharcha_gateway.kharcha_service.expense_service"
)


def test_import_skipped_rows_logged_at_warning(storage, caplog):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    row = json.loads(
        json.dumps(
            expense_service.export_group_expenses(storage, group.id)["expenses"][0]
        )
    )
    row["date"] = "not-a-date"

    with caplog.at_level(logging.WARNING, logger=_EXPENSE_LOGGER):
        result = expense_service.import_group_expenses(storage, group.id, [row], owner.id)

    assert result["imported"] == 0
    warnings = [r for r in caplog.records if r.levelno == logging.WARNING]
    assert len(warnings) == 1
    message = warnings[0].getMessage()
    assert "skipped=1" in message
    assert "not valid ISO 8601" in message


def test_import_success_logged_at_info(storage, caplog):
    owner, friend, group = _seed(storage, "Owner", "Friend")
    envelope = expense_service.export_group_expenses(storage, group.id)

    with caplog.at_level(logging.INFO, logger=_EXPENSE_LOGGER):
        result = expense_service.import_group_expenses(
            storage, group.id, envelope["expenses"], owner.id
        )

    assert result["imported"] == 1
    infos = [r for r in caplog.records if r.levelno == logging.INFO]
    assert any("imported=1" in r.getMessage() for r in infos)
    assert not any(r.levelno == logging.WARNING for r in caplog.records)
