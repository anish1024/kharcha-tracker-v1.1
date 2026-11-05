package com.kharcha.tracker.ui.dashboard

enum class ExpenseCategory(val label: String, val code: String) {
    GROCERY("Grocery", "GROCERY"),
    UTILITY("Utility", "UTILITY"),
    TRAVEL("Travel", "TRAVEL"),
    ENTERTAINMENT("Entertainment", "ENTERTAINMENT"),
    FOOD_N_DINING("Food & Dining", "FOOD_N_DINING"),
    OP_EXP("Operating Expense", "OP_EXP"),
    CAP_EXP("Capital Expense", "CAP_EXP"),
    ACCOMMODATION("Accommodation", "ACCOMMODATION"),
    CUSTOM("Custom", "CUSTOM"),
    CORPUS("Corpus", "CORPUS");

    companion object {
        val values: List<ExpenseCategory> = values().toList()
        fun fromCode(code: String?): ExpenseCategory? = values.firstOrNull {
            it.code == code
        }
    }
}