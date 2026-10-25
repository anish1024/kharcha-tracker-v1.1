package com.kharcha.tracker.models.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

@Entity(tableName = "expenses", indices = [
    Index(value = ["group_id"], unique = false),
    Index(value = ["user_id"], unique = false),
    Index(value = ["status"], unique = false),
    Index(value = ["date"], unique = false)
], foreignKey = [
    ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = Cascade.SETNULLS)
])
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val expenseId: String,  // API expense_id
    val name: String,
    val description: String? = null,
    val amount: Double,
    val currency: String = "INR",
    val groupId: Long,
    val groupName: String,
    val userId: String,
    val payerId: String? = null,
    val payerName: String? = null,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val status: String = "pending",  // PENDING, SUBMITTED, APPROVED, REJECTED, SETTLED, PAID, etc.
    val category: String? = null,
    val categoryId: String? = null,
    val subcategory: String? = null,
    val subTotal: Double? = null,
    val taxRate: Double? = null,
    val taxAmount: Double? = null,
    val discountRate: Double? = null,
    val discountAmount: Double? = null,
    val finalAmount: Double? = null,
    val expenseType: String = "expense",  // EXPENSE, INCOME, BOTH
    val incomeAmount: Double? = null,
    val receiptImage: String? = null,
    val bankCharges: Double? = null,
    val date: String? = null,
    val paymentDate: String? = null,
    val paymentMode: String? = null,
    val referenceNumber: String? = null,
    val notes: String? = null,
    val metadata: Map<String, String>? = null,
    val tags: List<String>? = null,
    val location: String? = null,  // latitude, longitude, address
    val items: List<Item>? = null,
    val attachments: List<String>? = null,
    val settlementStatus: String? = null,
    val milestoneId: String? = null,
    val projectId: String? = null,
    val invoiceId: String? = null,
    val vendorId: String? = null,
    val customerId: String? = null,
    val approvalStatus: String? = null,
    val approvedBy: String? = null,
    val approvalNotes: String? = null,
    val submittedAt: String? = null,
    val approvedAt: String? = null,
    
    val totalAmount: Double? = null,
    val itemsCount: Int = 0,
    val categoryAmount: Double = 0.0,
    val totalAmountCategory: Double? = null,
    
    val isOnline: Boolean = true,
    val isSynced: Boolean = true,
    val lastSyncedAt: String? = null
)

// Expense line items
@Entity(tableName = "expense_items", indices = [
    Index(value = ["expense_id"], unique = false)
])
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val itemId: String,  // API item_id
    val expenseId: Long,
    val expenseName: String,
    val quantity: Double = 1.0,
    val unitPrice: Double,
    val taxRate: Double? = null,
    val taxAmount: Double? = null,
    val discountRate: Double? = null,
    val discountAmount: Double? = null,
    val rateType: String = "rate",  // RATE, PERCENTAGE
    val currency: String = "INR",
    val taxInclusive: Boolean = false,
    val discountInclusive: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val isSynced: Boolean = true,
    val lastSyncedAt: String? = null
)

// Expense attachments (receipt images, documents)
@Entity(tableName = "expense_attachments", indices = [
    Index(value = ["expense_id"], unique = false),
    Index(value = ["user_id"], unique = false)
])
data class ExpenseAttachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val attachmentId: String,
    val expenseId: Long,
    val userId: String,
    val url: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val type: String = "receipt",  // receipt, note, comment, screenshot, document
    val status: String = "uploaded",  // uploaded, processing, failed
    val thumbnailUrl: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    enum class AttachmentType(val value: String) {
        RECEIPT("receipt"),
        NOTE("note"),
        COMMENT("comment"),
        SCREENSHOT("screenshot"),
        DOCUMENT("document"),
        OTHER("other")
    }
}

// Expense approval history
@Entity(tableName = "expense_approvals", indices = [
    Index(value = ["expense_id"], unique = false),
    Index(value = ["approver_user_id"], unique = false),
    Index(value = ["status"], unique = false)
])
data class ExpenseApproval(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val expenseId: Long,
    val expenseName: String,
    val approverId: String,
    val approverName: String,
    val status: String = "approved",  // approved, rejected, pending
    val notes: String? = null,
    val timestamp: String,
    val deleted: Boolean = false
)