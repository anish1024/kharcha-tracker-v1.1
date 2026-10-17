"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/payout_record.py
Payout table row storage schema derived from Payout domain model.
"""
from __future__ import annotations

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class PayoutRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key payout UUID")
    group_id: UUID = Field(..., description="Group UUID")
    payor_id: UUID = Field(..., description="User ID sending the payout")
    payee_id: UUID = Field(..., description="User ID receiving the payout")
    amt: float = Field(..., description="Payout amount")
    status: str = Field(..., description="Payout status: PENDING, PAID, SETTLED, FAILED, CANCELLED")
    expense_ids: List[int] = Field(
        default_factory=list,
        description="Associated expense trans_ids contributing to this payout",
    )
    is_adhoc: bool = Field(default=False, description="True if payment recorded outside a settlement run")
    settlement_round_id: Optional[UUID] = Field(default=None, description="Associated settlement round UUID")
    replaces_payout_ids: List[UUID] = Field(
        default_factory=list,
        description="IDs of FAILED/CANCELLED payouts re-issued by this record",
    )
    payor_evidence: Optional[str] = Field(default=None, description="Payor transfer reference or proof URL")
    payee_evidence: Optional[str] = Field(default=None, description="Payee confirmation reference or proof URL")
    status_reason: Optional[str] = Field(default=None, description="Reason required for FAILED and CANCELLED states")
    paid_at: Optional[datetime] = Field(default=None, description="Timestamp when payor marked money sent")
    settled_at: Optional[datetime] = Field(default=None, description="Timestamp when payee acknowledged receipt")