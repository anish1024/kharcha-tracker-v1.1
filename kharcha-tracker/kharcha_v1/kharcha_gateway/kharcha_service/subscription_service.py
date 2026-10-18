"""
kharcha_v1/kharcha_gateway/kharcha_service/subscription_service.py
Trial + mock subscription lifecycle (plan activation and status).
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any, Dict
from uuid import UUID

from .constants import POST_TRIAL_ENTRY_LIMIT, TRIAL_PERIOD_DAYS
from .constants.plan import Plan
from .constants.plan_status import PlanStatus
from .storage_service.storage_interface import StorageInterface as storage_interface

# Mock plan durations. Lifetime is handled separately (no expiry).
PLAN_DURATIONS = {
    Plan.MONTHLY.value: timedelta(days=30),
    Plan.SIX_MONTH.value: timedelta(days=180),
    Plan.YEARLY.value: timedelta(days=365),
}

VALID_PLAN_VALUES = {p.value for p in Plan if p is not Plan.NONE}


class SubscriptionRequiredError(PermissionError):
    """Raised when a limited user has exhausted their free entries."""

    code = "SUBSCRIPTION_REQUIRED"


def _load_user(storage: storage_interface, user_id: UUID):
    record = storage.get_record(record_id=str(user_id), schema="user_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("User not found")
    return record


def _plan_active(record: Any, now: datetime) -> bool:
    if getattr(record, "plan_status", PlanStatus.NONE.value) != PlanStatus.ACTIVE.value:
        return False
    plan = getattr(record, "plan", Plan.NONE.value)
    if plan == Plan.LIFETIME.value:
        return True
    ends_at = getattr(record, "plan_ends_at", None)
    return ends_at is not None and now < ends_at


def _trial_active(record: Any, now: datetime) -> bool:
    trial_ends_at = getattr(record, "trial_ends_at", None)
    return trial_ends_at is not None and now < trial_ends_at


def get_status(storage: storage_interface, user_id: UUID) -> Dict[str, Any]:
    """Return the subscription/trial status shape consumed by the UI."""
    record = _load_user(storage, user_id)
    now = datetime.now(timezone.utc)

    trial_active = _trial_active(record, now)
    plan_active = _plan_active(record, now)
    is_expired = not (trial_active or plan_active)
    is_limited = is_expired

    used = int(getattr(record, "limited_entries_used", 0) or 0)
    entries_remaining = max(0, POST_TRIAL_ENTRY_LIMIT - used) if is_limited else None

    return {
        "trial_ends_at": getattr(record, "trial_ends_at", None),
        "plan": getattr(record, "plan", Plan.NONE.value),
        "plan_status": getattr(record, "plan_status", PlanStatus.NONE.value),
        "plan_ends_at": getattr(record, "plan_ends_at", None),
        "entries_remaining": entries_remaining,
        "is_limited": is_limited,
        "is_expired": is_expired,
    }


def activate(storage: storage_interface, user_id: UUID, plan: Any) -> Dict[str, Any]:
    """Mock payment: activate the requested plan and reset the entry counter."""
    record = _load_user(storage, user_id)

    plan_value = plan.value if isinstance(plan, Plan) else str(plan or "").strip().lower()
    if plan_value not in VALID_PLAN_VALUES:
        raise ValueError(
            "Invalid plan; choose one of: " + ", ".join(sorted(VALID_PLAN_VALUES))
        )

    now = datetime.now(timezone.utc)
    was_active = _plan_active(record, now)
    current_end = getattr(record, "plan_ends_at", None)

    record.plan = plan_value
    record.plan_status = PlanStatus.ACTIVE.value
    if plan_value == Plan.LIFETIME.value:
        record.plan_ends_at = None
    else:
        # Renewals stack on top of any remaining time.
        base = current_end if (was_active and current_end and current_end > now) else now
        record.plan_ends_at = base + PLAN_DURATIONS[plan_value]

    record.limited_entries_used = 0
    record.modified_at = now
    record.modified_by = user_id
    storage.save_record(record, schema="user_record")

    return get_status(storage, user_id)


def consume_entry(storage: storage_interface, user_id: UUID) -> Dict[str, Any]:
    """Gate a single entry-creating action against the trial/plan state.

    Unlimited users (trial active or paid plan active) pass through untouched.
    Limited users consume one of ``POST_TRIAL_ENTRY_LIMIT`` free entries; once
    those are exhausted a ``SubscriptionRequiredError`` is raised so the caller
    can surface ``403 SUBSCRIPTION_REQUIRED``.
    """
    record = _load_user(storage, user_id)
    now = datetime.now(timezone.utc)

    if _trial_active(record, now) or _plan_active(record, now):
        return get_status(storage, user_id)

    used = int(getattr(record, "limited_entries_used", 0) or 0)
    if used >= POST_TRIAL_ENTRY_LIMIT:
        raise SubscriptionRequiredError(
            "Free entries exhausted; a subscription is required to continue."
        )

    record.limited_entries_used = used + 1
    record.modified_at = now
    record.modified_by = user_id
    storage.save_record(record, schema="user_record")

    return get_status(storage, user_id)
