package com.kharcha.tracker.models.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

@Entity(tableName = "groups", indices = [Index(value = ["user_id"], unique = false), Index(value = ["name"], unique = false)])
data class Group(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val groupId: String,  // API group_id
    val name: String,
    val description: String? = null,
    val userId: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val budgetAmount: Double? = null,
    val currency: String = "INR",
    val status: String = "active",  // ACTIVE, INACTIVE, ARCHIVED, DELETED, DISABLED
    val color: String = "#4285F4",
    val icon: String? = null,
    val iconCode: Int? = null,
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val expensesCount: Int = 0,
    val totalExpenseAmount: Double = 0.0,
    
    // Derived fields not stored
    val budgetStatus: String? = null,
    val remainingBudget: Double? = null,
    val expenses: List<Expense>? = null,
    val totalExpenses: Double? = null,
    val incomeExpenses: List<Expense>? = null,
    val totalExpensesIncome: Double? = null,
    val balance: Double? = null,
    val pendingSettlements: List<Settlement>? = null,
    val pendingIncome: Double? = null,
    val pendingPayments: Double? = null
)

// Group payment/settlement entity
@Entity(tableName = "group_payments", indices = [
    Index(value = ["group_id"], unique = false),
    Index(value = ["payer_user_id"], unique = false)
], foreignKey = [
    ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = Cascade.SETNULLS)
])
data class GroupPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val paymentId: String,  // API payment_id
    val groupId: Long,
    val groupName: String,
    val payerUserId: String,
    val payerName: String,
    val amount: Double,
    val currency: String = "INR",
    val paymentMode: String = "cash",  // CASH, UPI, BANK, etc.
    val reference: String? = null,
    val notes: String? = null,
    val paymentDate: String,
    val createdAt: String,
    val updatedAt: String,
    val status: String = "pending",  // PENDING, APPROVED, REJECTED, etc.
    
    val groupIdRaw: String = "",  // Original API group_id
    val userGroup: Group = Group.EMPTY
)

// Group settlement record (manual settlement tracking)
@Entity(tableName = "group_settlements", indices = [
    Index(value = ["group_id"], unique = false),
    Index(value = ["created_by"], unique = false)
])
data class SettlementRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val settlementId: String,  // API settlement_id if generated
    val groupId: Long,
    val groupName: String,
    val createdBy: String,
    val amount: Double,
    val currency: String = "INR",
    val paymentMode: String = "cash",
    val reference: String? = null,
    val notes: String? = null,
    val status: String = "pending",  // PENDING, APPROVED, REJECTED, CANCELLED, DISPUTED, CLOSED
    val balanceAmount: Double = 0.0,
    val totalReceived: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val expensesCount: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val approvedAt: String? = null,
    val rejectedAt: String? = null,
    val cancelledAt: String? = null,
    val settledAt: String? = null,
    
    val groupIdRaw: String = ""
)