from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class Invitation(BaseModel):
    id: UUID
    group_id: UUID
    inviter_id: UUID
    invitee_email: str
    invite_code: str
    # YAML type: invitation_status. The concrete enum belongs to constants/invitation_status.py.
    status: str
    created_at: datetime
    expires_at: datetime | None = None
    # E1: resolved group name so a non-member invitee can see which group invited them.
    group_name: str | None = None
