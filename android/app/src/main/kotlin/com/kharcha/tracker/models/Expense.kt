package com.kharcha.tracker.models

import com.google.gson.annotations.SerializedName

/**
 * Expense model with all fields
 */
data class Expense(
    @SerializedName("expense_id")
    val expenseId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("payer_id")
    val payerId: String?,
    @SerializedName("created_by")
    val createdBy: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
    @SerializedName("status")
    val status: ExpenseStatus = ExpenseStatus.PENDING,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    @SerializedName("subcategory")
    val subcategory: String? = null,
    @SerializedName("sub_total")
    val subTotal: Double? = null,
    @SerializedName("tax_rate")
    val taxRate: Double? = null,
    @SerializedName("tax_amount")
    val taxAmount: Double? = null,
    @SerializedName("discount_rate")
    val discountRate: Double? = null,
    @SerializedName("discount_amount")
    val discountAmount: Double? = null,
    @SerializedName("final_amount")
    val finalAmount: Double? = null,
    @SerializedName("expense_type")
    val expenseType: ExpenseType = ExpenseType.EXPENSE,
    @SerializedName("income_amount")
    val incomeAmount: Double? = null,
    @SerializedName("receipt_image")
    val receiptImage: String? = null,
    @SerializedName("bank_charges")
    val bankCharges: Double? = null,
    @SerializedName("date")
    val date: String?,
    @SerializedName("payment_date")
    val paymentDate: String?,
    @SerializedName("payment_mode")
    val paymentMode: PaymentMode? = null,
    @SerializedName("reference_number")
    val referenceNumber: String? = null,
    @SerializedName("notes")
    val notes: String? = null,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null,
    @SerializedName("tags")
    val tags: List<String>? = null,
    @SerializedName("location")
    val location: Location? = null,
    @SerializedName("items")
    val items: List<Item>? = null,
    @SerializedName("attachments")
    val attachments: List<String>? = null,
    @SerializedName("settlement_status")
    val settlementStatus: SettlementStatus? = null,
    @SerializedName("milestone_id")
    val milestoneId: String?,
    @SerializedName("project_id")
    val projectId: String?,
    @SerializedName("invoice_id")
    val invoiceId: String? = null,
    @SerializedName("invoice")
    val invoice: Invoice? = null,
    @SerializedName("vendor_id")
    val vendorId: String? = null,
    @SerializedName("vendor")
    val vendor: Vendor? = null,
    @SerializedName("customer_id")
    val customerId: String? = null,
    @SerializedName("customer")
    val customer: Customer? = null,
    @SerializedName("approval_status")
    val approvalStatus: ApprovalStatus? = null,
    @SerializedName("approved_by")
    val approvedBy: String?,
    @SerializedName("approval_notes")
    val approvalNotes: String? = null,
    @SerializedName("submitted_at")
    val submittedAt: String?,
    @SerializedName("approved_at")
    val approvedAt: String? = null
) {
    val id: String = expenseId
    
    enum class ExpenseStatus(val value: String) {
        PENDING("pending"),
        SUBMITTED("submitted"),
        APPROVED("approved"),
        REJECTED("rejected"),
        SETTLED("settled"),
        PAID("paid"),
        CANCELLED("cancelled"),
        RETURNED("returned"),
        WRITTEN_OFF("written_off"),
        DISPUTED("disputed")
    }
    
    enum class ExpenseType(val value: String) {
        EXPENSE("expense"),
        INCOME("income"),
        BOTH("both")
    }
    
    enum class PaymentMode(val value: String) {
        CASH("cash"),
        UPI("upi"),
        BANK("bank"),
        CARD("card"),
        ONLINE("online"),
        WALLET("wallet"),
        NEFT("neft"),
        RTGS("rtgs"),
        IMPS("imps"),
        CREDIT("credit"),
        DEBIT("debit"),
        OTHER("other")
    }
    
    enum class SettlementStatus(val value: String) {
        PENDING("pending"),
        SUBMITTED("submitted"),
        APPROVED("approved"),
        DISPUTED("disputed"),
        RECONCILED("reconciled"),
        CLOSED("closed"),
        CANCELLED("cancelled"),
        EXPIRED("expired")
    }
    
    enum class ApprovalStatus(val value: String) {
        PENDING("pending"),
        SUBMITTED("submitted"),
        APPROVED("approved"),
        REJECTED("rejected"),
        TIMEOUT("timeout")
    }
    
    fun getFormattedAmount(): String = String.format("%,f", amount)
    
    fun isSettled(): Boolean = status == ExpenseStatus.SETTLED || status == ExpenseStatus.PAID
    
    fun isApproved(): Boolean = status == ExpenseStatus.APPROVED || status == ExpenseStatus.PAID
    
    fun isPendingSettlement(): Boolean = status == SettlementStatus?.value == "pending"
    
    fun getFormattedFinalAmount(): String {
        val finalAmount = finalAmount ?: calculateFinalAmount()
        return String.format("%,f", finalAmount)
    }
    
    private fun calculateFinalAmount(): Double {
        var amount = this.amount
        val subTotal = this.subTotal ?: 0.0
        val taxAmount = this.taxAmount ?: 0.0
        val discountAmount = this.discountAmount ?: 0.0
        val bankCharges = this.bankthis.bankbankCharges ?: 0.0
        val incomeAmount = this.incomeAmount ?: 0.0
        
        return (amount + subTotal + taxAmount + bankCharges - discountAmount + incomeAmount)
    }
    
    fun calculateTotalAmount(): Double {
        var total = this.amount
        val subTotal = this.subTotal ?: 0.0
        val taxAmount = this.taxAmount ?: 0.0
        val bankCharges = this.bankthis.bankbankCharges ?: 0.0
        return total + subTotal + taxAmount + bankCharges
    }
}

/**
 * Expense creation request
 */
data class CreateExpenseRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("expense_type")
    val expenseType: ExpenseType = ExpenseType.EXPENSE,
    @SerializedName("income_amount")
    val incomeAmount: Double? = null,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    @SerializedName("subcategory")
    val subcategory: String? = null,
    @SerializedName("tax_rate")
    val taxRate: Double? = null,
    @SerializedName("tax_amount")
    val taxAmount: Double? = null,
    @SerializedName("discount_amount")
    val discountAmount: Double? = null,
    @SerializedName("bank_charges")
    val bankCharges: Double? = null,
    @SerializedName("date")
    val date: String?,
    @SerializedName("payment_date")
    val paymentDate: String?,
    @SerializedName("payment_mode")
    val paymentMode: Expense.PaymentMode? = null,
    @SerializedName("reference_number")
    val referenceNumber: String? = null,
    @SerializedName("notes")
    val notes: String? = null,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null,
    @SerializedName("tags")
    val tags: List<String>? = null,
    @SerializedName("location")
    val location: CreateExpenseLocationRequest? = null,
    @SerializedName("items")
    val items: List<Item>? = null,
    @SerializedName("attachments")
    val attachments: List<String>? = null,
    @SerializedName("milestone_id")
    val milestoneId: String? = null,
    @SerializedName("project_id")
    val projectId: String? = null,
    @SerializedName("invoice_id")
    val invoiceId: String? = null,
    @SerializedName("vendor_id")
    val vendorId: String? = null,
    @SerializedName("customer_id")
    val customerId: String? = null,
    @SerializedName("approval_status")
    val approvalStatus: Expense.ApprovalStatus? = null,
    @SerializedName("approve_by_user_id")
    val approveByUserId: String? = null,
    @SerializedName("approve_by_role")
    val approveByRole: String? = null
)

/**
 * Expense update request
 */
data class UpdateExpenseRequest(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("amount")
    val amount: Double? = null,
    @SerializedName("currency")
    val currency: String? = null,
    @SerializedName("group_id")
    val groupId: String? = null,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    @SerializedName("subcategory")
    val subcategory: String? = null,
    @SerializedName("tax_rate")
    val taxRate: Double? = null,
    @SerializedName("tax_amount")
    val taxAmount: Double? = null,
    @SerializedName("discount_rate")
    val discountRate: Double? = null,
    @SerializedName("discount_amount")
    val discountAmount: Double? = null,
    @SerializedName("final_amount")
    val finalAmount: Double? = null,
    @SerializedName("expense_type")
    val expenseType: Expense.ExpenseType? = null,
    @SerializedName("income_amount")
    val incomeAmount: Double? = null,
    @SerializedName("payment_mode")
    val paymentMode: Expense.PaymentMode? = null,
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("payment_date")
    val paymentDate: String? = null,
    @SerializedName("reference_number")
    val referenceNumber: String? = null,
    @SerializedName("notes")
    val notes: String? = null,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null,
    @SerializedName("tags")
    val tags: List<String>? = null,
    @SerializedName("approval_status")
    val approvalStatus: Expense.ApprovalStatus? = null,
    @SerializedName("approve_by_user_id")
    val approveByUserId: String? = null,
    @SerializedName("approve_by_role")
    val approveByRole: String? = null
)

/**
 * Delete expense request (soft delete)
 */
data class DeleteExpenseRequest(
    @SerializedName("expense_id")
    val expenseId: String,
    @SerializedName("reason")
    val reason: String? = null
)

/**
 * Bulk expense creation request
 */
data class BulkCreateExpenseRequest(
    @SerializedName("expenses")
    val expenses: List<ExpensePayload>
) {
    data class ExpensePayload(
        @SerializedName("name")
        val name: String,
        @SerializedName("amount")
        val amount: Double,
        @SerializedName("currency")
        val currency: String = "INR",
        @SerializedName("group_id")
        val groupId: String,
        @SerializedName("expense_type")
        val expenseType: Expense.ExpenseType = ExpenseType.EXPENSE
    )
}

/**
 * Location for expense
 */
data class CreateExpenseLocationRequest(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("place_id")
    val placeId: String? = null
)

/**
 * Location update request
 */
data class UpdateExpenseLocationRequest(
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("place_id")
    val placeId: String? = null
)

/**
 * Expense item (for multi-item expenses)
 */
data class Item(
    @SerializedName("item_id")
    val itemId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("quantity")
    val quantity: Double = 1.0,
    @SerializedName("unit_price")
    val unitPrice: Double,
    @SerializedName("tax_rate")
    val taxRate: Double? = null,
    @SerializedName("tax_amount")
    val taxAmount: Double? = null,
    @SerializedName("discount_rate")
    val discountRate: Double? = null,
    @SerializedName("discount_amount")
    val discountAmount: Double? = null,
    @SerializedName("rate_type")
    val rateType: RateType = RateType.RATE,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("tax_inclusive")
    val taxInclusive: Boolean = false,
    @SerializedName("discount_inclusive")
    val discountInclusive: Boolean = false,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    enum class RateType(val value: String) {
        RATE("rate"),
        PERCENTAGE("percentage")
    }
    
    val id: String = itemId
    
    fun getLineTotal(): Double {
        var total = unitPrice * quantity
        val taxAmount = this.taxAmount ?: calculateTax((unitPrice * quantity).toDouble(), this.taxRate)
        val discountAmount = this.discountAmount ?: calculateDiscount((unitPrice * quantity).toDouble(), this.discountRate)
        return total + taxAmount - discountAmount
    }
    
    private fun calculateTax(basedAmount: Double, taxRate: Double?): Double {
        if (taxRate == null) return 0.0
        return if (taxRate >= 100) {
            basedAmount * (taxRate / 100.0)
        } else {
            basedAmount * (taxRate / 100.0)
        }
    }
    
    private fun calculateDiscount(basedAmount: Double, discountRate: Double?): Double {
        if (discountRate == null) return 0.0
        return if (discountRate >= 100) {
            basedAmount * (discountRate / 100.0)
        } else {
            basedAmount * (discountRate / 100.0)
        }
    }
    
    fun getTotal(): Double {
        val lineTotal = unitPrice * quantity
        val taxAmount = this.taxAmount ?: 0.0
        val discountAmount = this.discountAmount ?: 0.0
        return lineTotal + taxAmount - discountAmount
    }
}

/**
 * Vendor model
 */
data class Vendor(
    @SerializedName("vendor_id")
    val vendorId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("contact_person")
    val contactPerson: String?,
    @SerializedName("contact_number")
    val contactNumber: String?,
    @SerializedName("email")
    val email: String?,
    @SerializedName("address")
    val address: String?,
    @SerializedName("city")
    val city: String?,
    @SerializedName("state")
    val state: String?,
    @SerializedName("country")
    val country: String?,
    @SerializedName("pincode")
    val pincode: String?,
    @SerializedName("bank_name")
    val bankName: String?,
    @SerializedName("account_number")
    val accountNumber: String?,
    @SerializedName("ifsc_code")
    val ifscCode: String?,
    @SerializedName("gstin")
    val gstin: String?,
    @SerializedName("pan")
    val pan: String?,
    @SerializedName("vendor_code")
    val vendorCode: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("is_default")
    val isDefault: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    val id: String = vendorId
}

/**
 * Customer model
 */
data class Customer(
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("contact_person")
    val contactPerson: String?,
    @SerializedName("contact_number")
    val contactNumber: String?,
    @SerializedName("email")
    val email: String?,
    @SerializedName("address")
    val address: String?,
    @SerializedName("city")
    val city: String?,
    @SerializedName("state")
    val state: String?,
    @SerializedName("country")
    val country: String?,
    @SerializedName("pincode")
    val pincode: String?,
    @SerializedName("gst_number")
    val gstNumber: String?,
    @SerializedName("pan")
    val pan: String?,
    @SerializedName("customer_code")
    val customerCode: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    val id: String = customerId
}

/**
 * Invoice model
 */
data class Invoice(
    @SerializedName("invoice_id")
    val invoiceId: String,
    @SerializedName("invoice_number")
    val invoiceNumber: String,
    @SerializedName("expense_id")
    val expenseId: String,
    @SerializedName("vendor_id")
    val vendorId: String,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("tax_amount")
    val taxAmount: Double,
    @SerializedName("discount_amount")
    val discountAmount: Double,
    @SerializedName("total_amount")
    val totalAmount: Double,
    @SerializedName("currency")
    val currency: String = "INR",
    @SerializedName("due_date")
    val dueDate: String,
    @SerializedName("issued_date")
    val issuedDate: String,
    @SerializedName("status")
    val status: InvoiceStatus,
    @SerializedName("payment_status")
    val paymentStatus: PaymentStatus?,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("terms_conditions")
    val termsConditions: String?,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?
) {
    enum class InvoiceStatus(val value: String) {
        DRAFT("draft"),
        ISSUED("issued"),
        PAID("paid"),
        OVERDUE("overdue"),
        PARTIAL("partial"),
        CANCELLED("cancelled"),
        DELETED("deleted")
    }
    
    enum class PaymentStatus(val value: String) {
        PAID("paid"),
        PARTIAL("partial"),
        UNPAID("unpaid"),
        PENDING("pending")
    }
    
    val id: String = invoiceId
}

enum class InvoiceStatus(val value: String) {
    DRAFT("draft"),
    ISSUED("issued"),
    PAID("paid"),
    OVERDUE("overdue"),
    PARTIAL("partial"),
    CANCELLED("cancelled"),
    DELETED("deleted")
}

enum class PaymentStatus(val value: String) {
    PAID("paid"),
    PARTIAL("partial"),
    UNPAID("unpaid"),
    PENDING("pending")
}

/**
 * Expense category
 */
data class ExpenseCategory(
    @SerializedName("category_id")
    val categoryId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("parent_id")
    val parentId: String? = null,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("slug")
    val slug: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("is_default")
    val isDefault: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    val id: String = categoryId
}