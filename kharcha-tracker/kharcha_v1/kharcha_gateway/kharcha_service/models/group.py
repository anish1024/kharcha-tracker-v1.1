from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class Group(BaseModel):
    id: UUID
    name: str
    created_by_user_id: UUID
    invite_code: str
    members: list[UUID]
    # YAML type: group_status. The concrete enum belongs to constants/group_status.py.
    status: str
    currency: str
    time_created: datetime
    last_updated: datetime
