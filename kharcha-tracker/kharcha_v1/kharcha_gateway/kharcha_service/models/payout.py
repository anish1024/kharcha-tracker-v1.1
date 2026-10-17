from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class Payout(BaseModel):
    id: UUID
    group_id: UUID
    payor_id: UUID
    payee_id: UUID
    amt: float
    # YAML type: payout_status. The concrete enum belongs to constants/payout_status.py.
    status: str
    expense_ids: list[int]
    is_adhoc: bool
    settlement_round_id: UUID | None = None
    replaces_payout_ids: list[UUID]
    payor_evidence: str | None = None
    payee_evidence: str | None = None
    status_reason: str | None = None
    paid_at: datetime | None = None
    settled_at: datetime | None = None
