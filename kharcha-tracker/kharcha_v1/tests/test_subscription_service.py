"""
kharcha_v1/tests/test_subscription_service.py
Unit tests for the trial + mock subscription lifecycle.
"""
from datetime import datetime, timedelta, timezone

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import auth_service, subscription_service
from kharcha_v1.kharcha_gateway.kharcha_service.constants import (
    POST_TRIAL_ENTRY_LIMIT,
    TRIAL_PERIOD_DAYS,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.plan import Plan


def _new_user(storage, email="sub@example.com"):
    user = auth_service.signup(storage, "Sub User", email, "password123")
    return user


def _expire_trial(storage, user_id):
    record = storage.get_record(str(user_id), "user_record")
    record.trial_ends_at = datetime.now(timezone.utc) - timedelta(days=1)
    storage.save_record(record, schema="user_record")


def test_signup_starts_one_year_trial(storage):
    user = _new_user(storage)
    status = subscription_service.get_status(storage, user.id)

    assert status["plan"] == "none"
    assert status["plan_status"] == "none"
    assert status["trial_ends_at"] is not None
    assert status["is_expired"] is False
    assert status["is_limited"] is False
    assert status["entries_remaining"] is None

    record = storage.get_record(str(user.id), "user_record")
    expected_days = TRIAL_PERIOD_DAYS
    delta = record.trial_ends_at - datetime.now(timezone.utc)
    assert abs(delta.days - expected_days) <= 1


def test_expired_trial_is_limited(storage):
    user = _new_user(storage)
    _expire_trial(storage, user.id)

    status = subscription_service.get_status(storage, user.id)
    assert status["is_expired"] is True
    assert status["is_limited"] is True
    assert status["entries_remaining"] == POST_TRIAL_ENTRY_LIMIT


def test_activate_yearly_plan(storage):
    user = _new_user(storage)
    _expire_trial(storage, user.id)

    status = subscription_service.activate(storage, user.id, Plan.YEARLY)
    assert status["plan"] == "yearly"
    assert status["plan_status"] == "active"
    assert status["plan_ends_at"] is not None
    assert status["is_limited"] is False
    assert status["is_expired"] is False

    record = storage.get_record(str(user.id), "user_record")
    delta = record.plan_ends_at - datetime.now(timezone.utc)
    assert abs(delta.days - 365) <= 1


def test_activate_lifetime_has_no_expiry(storage):
    user = _new_user(storage)
    status = subscription_service.activate(storage, user.id, Plan.LIFETIME)
    assert status["plan"] == "lifetime"
    assert status["plan_status"] == "active"
    assert status["plan_ends_at"] is None
    assert status["is_limited"] is False


@pytest.mark.parametrize(
    "plan,days",
    [("monthly", 30), ("6month", 180), ("yearly", 365)],
)
def test_activate_plan_durations(storage, plan, days):
    user = _new_user(storage, email=f"{plan}@example.com")
    status = subscription_service.activate(storage, user.id, plan)
    assert status["plan"] == plan
    record = storage.get_record(str(user.id), "user_record")
    delta = record.plan_ends_at - datetime.now(timezone.utc)
    assert abs(delta.days - days) <= 1


def test_activate_rejects_invalid_plan(storage):
    user = _new_user(storage)
    for bad in ("", "none", "gold", "MONTHLYX"):
        with pytest.raises(ValueError):
            subscription_service.activate(storage, user.id, bad)


def test_activate_resets_entry_counter(storage):
    user = _new_user(storage)
    _expire_trial(storage, user.id)

    record = storage.get_record(str(user.id), "user_record")
    record.limited_entries_used = 3
    storage.save_record(record, schema="user_record")

    subscription_service.activate(storage, user.id, Plan.MONTHLY)
    record = storage.get_record(str(user.id), "user_record")
    assert record.limited_entries_used == 0


def test_renewal_stacks_on_existing_plan(storage):
    user = _new_user(storage)
    first = subscription_service.activate(storage, user.id, Plan.MONTHLY)
    first_end = storage.get_record(str(user.id), "user_record").plan_ends_at

    second = subscription_service.activate(storage, user.id, Plan.MONTHLY)
    second_end = storage.get_record(str(user.id), "user_record").plan_ends_at

    assert second["plan"] == "monthly"
    assert second_end > first_end
    # ~60 days of cumulative monthly coverage.
    delta = second_end - datetime.now(timezone.utc)
    assert abs(delta.days - 60) <= 1


def test_consume_entry_unlimited_during_trial(storage):
    user = _new_user(storage)
    for _ in range(5):
        subscription_service.consume_entry(storage, user.id)

    record = storage.get_record(str(user.id), "user_record")
    assert record.limited_entries_used == 0


def test_consume_entry_blocks_after_limit(storage):
    user = _new_user(storage)
    _expire_trial(storage, user.id)

    for expected_remaining in (2, 1, 0):
        status = subscription_service.consume_entry(storage, user.id)
        assert status["is_limited"] is True
        assert status["entries_remaining"] == expected_remaining

    with pytest.raises(subscription_service.SubscriptionRequiredError) as exc:
        subscription_service.consume_entry(storage, user.id)
    assert exc.value.code == "SUBSCRIPTION_REQUIRED"

    # The counter does not advance past the limit.
    record = storage.get_record(str(user.id), "user_record")
    assert record.limited_entries_used == POST_TRIAL_ENTRY_LIMIT


def test_consume_entry_unlimited_after_activation(storage):
    user = _new_user(storage)
    _expire_trial(storage, user.id)

    subscription_service.consume_entry(storage, user.id)
    subscription_service.activate(storage, user.id, Plan.MONTHLY)

    last = subscription_service.consume_entry(storage, user.id)
    for _ in range(4):
        last = subscription_service.consume_entry(storage, user.id)
    assert last["is_limited"] is False
    record = storage.get_record(str(user.id), "user_record")
    assert record.limited_entries_used == 0
