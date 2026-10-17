"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/settlement_round_record.py
Settlement round table row storage schema derived from SettlementRound domain model.
"""
from __future__ import annotations

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class SettlementRoundRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key settlement round UUID")
    group_id: UUID = Field(..., description="Group UUID")
    kind: str = Field(..., description="Settlement kind: INDIVIDUAL, PERIODIC, FINAL")
    status: str = Field(..., description="Settlement round status: ACTIVE, COMPLETED")
    requested_for: Optional[UUID] = Field(default=None, description="User UUID requesting individual settlement")
    completed_at: Optional[datetime] = Field(default=None, description="Timestamp when round was completed")