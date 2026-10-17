"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/notification_record.py
Notification table row storage schema derived from Notification domain model.
"""
from __future__ import annotations

from typing import Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class NotificationRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key notification UUID")
    user_id: UUID = Field(..., description="Recipient user UUID")
    group_id: Optional[UUID] = Field(default=None, description="Group UUID context")
    payout_id: Optional[UUID] = Field(default=None, description="Associated payout UUID if relevant")
    kind: str = Field(..., description="Notification kind enum string")
    message: str = Field(..., description="Notification message text")
    is_read: bool = Field(default=False, description="Read/Unread indicator")