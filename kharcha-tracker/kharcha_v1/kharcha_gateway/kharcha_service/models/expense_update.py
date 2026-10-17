from datetime import datetime

from pydantic import BaseModel


class ExpenseUpdate(BaseModel):
    date_time: datetime | None = None
    # YAML type: optional expense_category.
    expn_ctgry: str | None = None
    expn_desc: str | None = None
    expn_bill_screenshot: str | None = None
