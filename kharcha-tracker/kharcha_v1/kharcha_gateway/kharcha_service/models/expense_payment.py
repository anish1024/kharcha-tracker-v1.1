from uuid import UUID

from pydantic import BaseModel


class ExpensePayment(BaseModel):
    user_id: UUID
    amt: float
