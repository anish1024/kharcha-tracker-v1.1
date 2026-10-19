from datetime import datetime

from pydantic import BaseModel

from .expense_payment import ExpensePayment


class ExpenseUpdate(BaseModel):
    date_time: datetime | None = None
    # YAML type: optional expense_category.
    expn_ctgry: str | None = None
    expn_desc: str | None = None
    expn_bill_screenshot: str | None = None
    paid_by: list[ExpensePayment] | None = None
