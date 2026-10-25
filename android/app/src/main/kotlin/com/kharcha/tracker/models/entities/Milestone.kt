package com.kharcha.tracker.models.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Milestones - track expenses against specific goals/projects
 */
@Entity(tableName = "milestones", indices = [
    Index(value = ["group_id"], unique = false),
    Index(value = ["user_id"], unique = false),
    Index(value = ["title"], unique = false)
], foreignKey = [
    ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = Cascade.SETNULLS)
])
data class Milestone(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val milestoneId: String,  // API milestone_id
    val groupId: Long,
    val groupName: String,
    val title: String,
    val description: String? = null,
    val userId: String,
    val createdBy: String,
    val budget: Double,
    val currency: String = "INR",
    val status: String = "active",  // ACTIVE, IN_PROGRESS, COMPLETED, ON_HOLD, CANCELLED, EXPIRED
    val startDate: String,
    val endDate: String?,
    val progress: Double = 0.0,
    val totalBudget: Double = 0.0,
    val spentAmount: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val expensesCount: Int = 0,
    val pendingExpensesCount: Int = 0,
    val approvedExpensesCount: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val isSynced: Boolean = true,
    val lastSyncedAt: String? = null,
    val metadata: Map<String, String>? = null
)

/**
 * Milestone goals - sub-goals within milestones
 */
@Entity(tableName = "milestone_goals", indices = [
    Index(value = ["milestone_id"], unique = false),
    Index(value = ["title"], unique = false)
])
data class MilestoneGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val goalId: String,  // API goal_id
    val milestoneId: Long,
    val milestoneTitle: String,
    val title: String,
    val description: String? = null,
    val targetAmount: Double,
    val actualAmount: Double = 0.0,
    val currency: String = "INR",
    val status: String = "pending",  // PENDING, IN_PROGRESS, COMPLETED, OVER_BUDGET
    val progress: Double = 0.0,
    val progressText: String = "0% Complete",
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String? = null,
    val isSynced: Boolean = true,
    val lastSyncedAt: String? = null
)

/**
 * Milestone transactions - expenses linked to milestone
 */
@Entity(tableName = "milestone_transactions", indices = [
    Index(value = ["milestone_id"], unique = false),
    Index(value = ["expense_id"], unique = false)
])
data class MilestoneTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val transactionId: String,  // API transaction_id
    val milestoneId: Long,
    val milestoneTitle: String,
    val expenseId: Long,
    val expenseName: String,
    val amount: Double,
    val currency: String = "INR",
    val type: String = "expense",  // EXPENSE, INCOME
    val status: String = "pending",  // PENDING, APPROVED, SETTLED, REJECTED
    val createdAt: String,
    val updatedAt: String,
    val settledAt: String? = null,
    val approvedAt: String? = null,
    val approvedBy: String? = null,
    val metadata: Map<String, String>? = null
)