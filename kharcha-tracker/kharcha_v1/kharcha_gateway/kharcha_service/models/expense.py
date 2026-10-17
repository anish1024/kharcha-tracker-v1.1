from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from .expense_payment import ExpensePayment
from .expense_share import ExpenseShare


class Expense(BaseModel):
    trans_id: int
    group_id: UUID
    date_time: datetime
    # YAML type: expense_category. The concrete enum belongs to constants/expense_category.py.
    expn_ctgry: str
    expn_desc: str
    expn_amnt: float
    expn_splt: list[ExpenseShare]
    paid_by: list[ExpensePayment]
    expn_bill_screenshot: str
    expn_payment_screenshot: str
    # YAML type: expense_status. The concrete enum belongs to constants/expense_status.py.
    status: str
