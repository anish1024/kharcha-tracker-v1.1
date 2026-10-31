# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class ExpenseCategory(str, Enum):
    GROCERY = "GROCERY"
    UTILITY = "UTILITY"
    TRAVEL = "TRAVEL"
    ENTERTAINMENT = "ENTERTAINMENT"
    FOOD_N_DINING = "FOOD_N_DINING"
    OP_EXP = "OP_EXP"
    CAP_EXP = "CAP_EXP"
    ACCOMMODATION = "ACCOMMODATION"
    CUSTOM = "CUSTOM"
    CORPUS = "CORPUS"

