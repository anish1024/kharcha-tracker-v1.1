from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class User(BaseModel):
    id: UUID
    name: str
    password: str
    email: str
    avatar_url: Optional[str] = None
