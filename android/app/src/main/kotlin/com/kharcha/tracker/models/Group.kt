package com.kharcha.tracker.models

import com.google.gson.annotations.SerializedName

/**
 * Expense Group - categorize expenses
 */
data class Group(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("created_by")
    val createdBy: String,
    @SerializedName("budget")
    val budget: Double?,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("status")
    val status: GroupStatus = GroupStatus.ACTIVE,
    @SerializedName("color")
    val color: String = "#4285F4",
    @SerializedName("icon")
    val icon: String?,
    @SerializedName("icon_code")
    val iconCode: Int?,
    @SerializedName("is_favorite")
    val isFavorite: Boolean = false,
    @SerializedName("is_default")
    val isDefault: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
    @SerializedName("expenses_count")
    val expensesCount: Int = 0,
    @SerializedName("total_expense_amount")
    val totalExpenseAmount: Double = 0.0,
    @SerializedName("budget_status")
    val budgetStatus: BudgetStatus? = null,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    val id: String = groupId
    
    enum class GroupStatus {
        ACTIVE,
        INACTIVE,
        ARCHIVED,
        DELETED,
        DISABLED
    }
    
    enum class BudgetStatus(val value: String) {
        UNDER_BUDGET("under_budget"),
        ON_BUDGET("on_budget"),
        OVER_BUDGET("over_budget"),
        BUDGET_EXHAUSTED("budget_exhausted")
    }
    
    val statusValue: String
        get() = status.value
    
    fun getDisplayName(): String = name.ifEmpty { groupId }
    
    companion object {
        fun fromApiResponseMap(map: Map<String, Any>): Group {
            val builder = Group.builder()
            
            map["group_id"]?.toString()?.let { builder.groupId(it) }
            map["name"]?.toString()?.let { builder.name(it) }
            map["description"]?.toString()?.let { builder.description(it) }
            map["user_id"]?.toString()?.let { builder.userId(it) }
            map["created_by"]?.toString()?.let { builder.createdBy(it) }
            
            // Handle nullable budget
            val budgetValue = map["budget"]
            if (budgetValue != null && budgetValue.toString().isNotEmpty()) {
                try {
                    builder.budget(budgetValue.toString().toDouble())
                } catch (e: Exception) {
                    builder.budget(null)
                }
            }
            
            val statusValue = map["status"]?.toString()
            when (statusValue) {
                "active" -> builder.status(GroupStatus.ACTIVE)
                "inactive" -> builder.status(GroupStatus.INACTIVE)
                "archived" -> builder.status(GroupStatus.ARCHIVED)
                "deleted" -> builder.status(GroupStatus.DELETED)
                "disabled" -> builder.status(GroupStatus.DISABLED)
                else -> builder.status(GroupStatus.ACTIVE)
            }
            
            // Handle color (hex code or icon code)
            if (map["icon_code"] != null) {
                val colorCode = map["icon_code"] as Int
                builder.color("color:$colorCode")
                builder.iconCode(colorCode)
            }
            
            // Handle currency default
            if (!map.containsKey("currency")) {
                builder.currency("INR")
            }
            
            // Handle timestamps
            map["created_at"]?.toString()?.let { builder.createdAt(it) }
            map["updated_at"]?.toString()?.let { builder.updatedAt(it) }
            map["deleted_at"]?.toString()?.let { builder.deletedAt(it) }
            
            // Handle counts and amounts
            map["expenses_count"]?.toString()?.let { builder.expensesCount(it.toInt()) }
            map["total_expense_amount"]?.toString()?.let { builder.totalExpenseAmount(it.toDoubleOrNull()) }
            
            // Handle budget status if budget exists
            if (map["budget"] != null) {
                val budgetValue = map["budget"] as Any
                if (budgetValue is Map<*, *> && budgetValue["budget_amount"] != null) {
                    val budgetAmount = (budgetValue["budget_amount"] as? Double)
                    val totalExpense = map["total_expense_amount"] as? Double
                    val threshold = (budgetAmount - (totalExpense ?: 0.0))
                    
                    val budgetStatus = when {
                        threshold > 0 -> BudgetStatus.UNDER_BUDGET
                        threshold < 0 -> BudgetStatus.OVER_BUDGET
                        threshold == 0.0 -> BudgetStatus.BUDGET_EXHAUSTED
                        else -> null
                    }
                    builder.budgetStatus(budgetStatus)
                }
            }
            
            return builder.build()
        }
    }
    
    companion object {
        val EMPTY = Group(
            groupId = "",
            name = "",
            userId = "",
            createdAt = "",
            updatedAt = "",
            deletedAt = null,
            expensesCount = 0,
            totalExpenseAmount = 0.0,
            isFavorite = false
        )
    }
}

/**
 * Create group request
 */
data class CreateGroupRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("budget")
    val budget: Double? = null,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("color")
    val color: String = "#4285F4",
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("icon_code")
    val iconCode: Int? = null,
    @SerializedName("is_favorite")
    val isFavorite: Boolean = false
)

/**
 * Update group request
 */
data class UpdateGroupRequest(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("budget")
    val budget: Double? = null,
    @SerializedName("currency")
    val currency: String? = null,
    @SerializedName("color")
    val color: String? = null,
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("icon_code")
    val iconCode: Int? = null,
    @SerializedName("is_favorite")
    val isFavorite: Boolean? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null
)

/**
 * Group payment (settlement)
 */
data class GroupPayment(
    @SerializedName("payment_id")
    val paymentId: String,
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("group")
    val group: Group,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("payer_name")
    val payerName: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("reference")
    val reference: String?,
    @SerializedName("payment_mode")
    val paymentMode: PaymentMode = PaymentMode.CASH,
    @SerializedName("notes")
    val notes: String? = null,
    @SerializedName("payment_date")
    val paymentDate: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    enum class PaymentMode {
        CASH,
        UPI,
        BANK,
        CARD,
        ONLINE,
        WALLET,
        OTHER
    }
}

/**
 * Group expenses summary
 */
data class GroupExpenseSummary(
    @SerializedName("total_expenses")
    val totalExpenses: Double,
    @SerializedName("total_income")
    val totalIncome: Double,
    @SerializedName("net_balance")
    val netBalance: Double,
    @SerializedName("expense_count")
    val expenseCount: Int,
    @SerializedName("income_count")
    val incomeCount: Int,
    @SerializedName("period_start")
    val periodStart: String,
    @SerializedName("period_end")
    val periodEnd: String,
    @SerializedName("currency")
    val currency: String = "INR"
) {
    val balance: Double
        get() = netBalance
}