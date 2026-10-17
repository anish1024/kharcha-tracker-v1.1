from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class Notification(BaseModel):
    id: UUID
    user_id: UUID
    group_id: UUID | None = None
    payout_id: UUID | None = None
    # YAML type: notification_kind. The concrete enum belongs to constants/notification_kind.py.
    kind: str
    message: str
    is_read: bool
    created_at: datetime
