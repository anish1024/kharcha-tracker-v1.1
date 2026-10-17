"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/expense_record.py
Expense table row storage schema derived from Expense domain model.
"""
from __future__ import annotations

from datetime import datetime
from typing import Any, List, Optional
from uuid import UUID

from pydantic import Field

from ...models.expense_payment import ExpensePayment
from ...models.expense_share import ExpenseShare
from .base_record import BaseRecord


class ExpenseRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key UUID for storage interface lookups")
    trans_id: Optional[int] = Field(default=None, description="uint32 auto-incrementing transaction ID")
    group_id: UUID = Field(..., description="Associated group UUID")
    date_time: datetime = Field(..., description="Timestamp of the expense")
    expn_ctgry: str = Field(..., description="Expense category enum value")
    expn_desc: str = Field(..., description="Description of the expense")
    expn_amnt: float = Field(..., description="Total expense amount")
    expn_splt: List[ExpenseShare] = Field(
        default_factory=list,
        description="List of split shares per participating user",
    )
    paid_by: List[ExpensePayment] = Field(
        default_factory=list,
        description="List of payment contributions per user",
    )
    expn_bill_screenshot: Optional[str] = Field(default=None, description="Screenshot/Receipt URL")
    expn_payment_screenshot: Optional[str] = Field(default=None, description="Payment confirmation screenshot URL")
    status: str = Field(default="Open", description="Expense lifecycle status: Open, Locked, Settled")