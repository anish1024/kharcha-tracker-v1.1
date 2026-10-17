from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class SettlementRound(BaseModel):
    id: UUID
    group_id: UUID
    # YAML type: settlement_kind. The concrete enum belongs to constants/settlement_kind.py.
    kind: str
    # YAML type: settlement_round_status.
    status: str
    requested_for: UUID | None = None
    completed_at: datetime | None = None
