package com.kharcha.tracker.models.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Expense categories - hierarchical category structure
 */
@Entity(tableName = "categories", indices = [
    Index(value = ["user_id"], unique = false),
    Index(value = ["name"], unique = false),
    Index(value = ["slug"], unique = false),
    Index(value = ["parent_id"], unique = false)
], foreignKey = [
    ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["parent_id"], onDelete = Cascade.SETNULLS)
])
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val categoryId: String,  // API category_id
    val name: String,
    val description: String? = null,
    val userId: String,
    val parentCategoryId: Long? = null,
    val parentName: String? = null,
    val children: List<Category>? = null,
    val slug: String? = null,
    val icon: String? = null,
    val iconCode: Int? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val isSystem: Boolean = false,
    val orderIndex: Int = 0,
    val level: Int = 0,  // 0 for root, 1 for level 1, etc.
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val metadata: Map<String, String>? = null,
    val expenseCount: Int = 0,
    val totalAmount: Double = 0.0,
    
    val currency: String = "INR"
)

/**
 * Subcategories - child categories
 */
@Entity(tableName = "subcategories", indices = [
    Index(value = ["parent_category_id"], unique = false),
    Index(value = ["user_id"], unique = false),
    Index(value = ["name"], unique = false)
])
data class Subcategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val subcategoryId: String,  // API subcategory_id
    val name: String,
    val description: String? = null,
    val parentCategoryId: Long,
    val categoryName: String,
    val parentName: String,
    val parentSlug: String? = null,
    val userId: String,
    val slug: String? = null,
    val icon: String? = null,
    val iconCode: Int? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val orderIndex: Int = 0,
    val level: Int = 1,  // Always 1 for subcategories
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val metadata: Map<String, String>? = null,
    val expenseCount: Int = 0,
    val totalAmount: Double = 0.0,
    
    val currency: String = "INR"
)