package com.kharcha.tracker.models.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    
    val userId: String,  // API user_id
    val username: String,
    val email: String,
    val fullName: String? = null,
    val phone: String? = null,
    val role: String,
    val organization: String? = null,
    val avatar: String? = null,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
    val twoFactorEnabled: Boolean? = null,
    val lastLoginAt: String? = null,
    
    val currency: String = "INR",
    val theme: String = "system",
    val language: String = "en",
    val darkMode: Boolean = false,
    val notificationEnabled: Boolean = true
) {
    fun isAdmin(): Boolean = role == "admin" || role == "super_admin"
    fun isManager(): Boolean = role == "manager" || role == "admin"
    fun isUser(): Boolean = role == "user" || role == "employee"
}

// User preferences stored locally
data class UserPreferences(
    val userId: String,
    val currency: String = "INR",
    val theme: String = "system",
    val darkMode: Boolean = false,
    val language: String = "en",
    val notificationEnabled: Boolean = true,
    val offlineMode: Boolean = false
)

// User sessions stored locally
data class UserSession(
    val userId: String,
    val deviceId: String,
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAt: String,
    val lastAccessedAt: String,
    val isLoggedIn: Boolean
)