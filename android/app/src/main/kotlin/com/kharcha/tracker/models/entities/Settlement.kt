package com.kharcha.tracker.models.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Expense settlement - tracks manual payments for expenses
 */
@Entity(tableName = "settlements", indices = [
    Index(value = ["created_by"], unique = false),
    Index(value = ["group_id"], unique = false),
    Index(value = ["status"], unique = false),
    Index(value = ["balance_amount"], unique = false)
])
data class Settlement(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val settlementId: String,  // API settlement_id
    val userId: String,
    val userName: String,
    val groupId: Long,
    val groupName: String,
    val amount: Double,
    val currency: String = "INR",
    val paymentMode: String = "cash",  // CASH, UPI, BANK, etc.
    val reference: String? = null,
    val notes: String? = null,
    val status: String = "pending",  // PENDING, APPROVED, REJECTED, CANCELLED, DISPUTED, CLOSED, CLOSED_WITH_BALANCE
    val balanceAmount: Double = 0.0,
    val totalReceived: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val expensesCount: Int = 0,
    val pendingExpensesCount: Int = 0,
    val pendingAmount: Double = 0.0,
    val createdAt: String,
    val updatedAt: String,
    val approvedAt: String? = null,
    val rejectedAt: String? = null,
    val cancelledAt: String? = null,
    val settledAt: String? = null,
    val disputedBy: String? = null,
    val disputedReason: String? = null,
    val disputedAt: String? = null,
    val resolvedBy: String? = null,
    val resolvedAt: String? = null,
    val resolutionNotes: String? = null,
    
    val isOnline: Boolean = true,
    val isSynced: Boolean = true,
    val lastSyncedAt: String? = null,
    val deleted: Boolean = false
) {
    fun isPending(): Boolean = status == "pending" || status == "submitted"
    fun isApproved(): Boolean = status == "approved" || status == "paid"
    fun isRejected(): Boolean = status == "rejected" || status == "cancelled" || status == "expired"
    fun isClosed(): Boolean = status == "closed"
    
    val displayStatus: String
        get() = when (status) {
            "pending" -> "Pending"
            "submitted" -> "Submitted"
            "approved" -> "Approved"
            "paid" -> "Paid"
            "rejected" -> "Rejected"
            "cancelled" -> "Cancelled"
            "disputed" -> "Disputed"
            "expired" -> "Expired"
            "closed" -> "Closed"
            "closed_with_balance" -> "Closed (with balance)"
            else -> status
        }
}

// Settlement payment records (individual transactions)
@Entity(tableName = "settlement_payments", indices = [
    Index(value = ["settlement_id"], unique = false),
    Index(value = ["payer_user_id"], unique = false),
    Index(value = ["payer_user_name"], unique = false)
])
data class SettlementPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val paymentId: String,  // API payment_id
    val settlementId: Long,
    val payerUserId: String,
    val payerUserName: String,
    val payerGroup: String?,
    val amount: Double,
    val currency: String = "INR",
    val paymentMode: String = "cash",  // CASH, UPI, BANK, etc.
    val reference: String? = null,
    val notes: String? = null,
    val status: String = "pending",  // PENDING, APPROVED, REJECTED, etc.
    val createdAt: String,
    val updatedAt: String,
    val approvedAt: String? = null,
    val rejectedAt: String? = null,
    val cancelledAt: String? = null,
    val approvedBy: String? = null,
    val rejectedBy: String? = null,
    val approvedNotes: String? = null,
    val rejectedReason: String? = null,
    
    val isOnline: Boolean = true,
    val isSynced: Boolean = true,
    val lastSyncedAt: String? = null,
    val deleted: Boolean = false
)