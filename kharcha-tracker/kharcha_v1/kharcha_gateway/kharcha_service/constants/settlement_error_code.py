# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class SettlementErrorCode(str, Enum):
    GROUP_NOT_FOUND = "GROUP_NOT_FOUND"
    PAYOUT_NOT_FOUND = "PAYOUT_NOT_FOUND"
    INVALID_REQUEST = "INVALID_REQUEST"
    ACCOUNTING = "ACCOUNTING"
    PAYOUT_PERMISSION = "PAYOUT_PERMISSION"
    GROUP_STATE = "GROUP_STATE"
    GROUP_NOT_LOCKED = "GROUP_NOT_LOCKED"
    PAYOUT_STATE = "PAYOUT_STATE"
    FINAL_SETTLEMENT = "FINAL_SETTLEMENT"

