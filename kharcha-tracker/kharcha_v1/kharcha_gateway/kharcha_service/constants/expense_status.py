# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class ExpenseStatus(str, Enum):
    OPEN = "Open"
    LOCKED = "Locked"
    SETTLED = "Settled"

