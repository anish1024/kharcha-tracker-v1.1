package com.kharcha.tracker.api

import com.kharcha.tracker.models.Category
import com.kharcha.tracker.models.entities.Category as CategoryEntity
import retrofit2.Response
import retrofit2.http.*

interface CategoryApiService {
    
    @GET("/api/v1/categories")
    suspend fun getCategories(@Header("Authorization") token: String): Response<CategoriesResponse>
    
    @GET("/api/v1/categories/{categoryId}")
    suspend fun getCategory(@Header("Authorization") token: String, @Path("categoryId") categoryId: String): Response<Category>
    
    @POST("/api/v1/categories")
    suspend fun createCategory(@Header("Authorization") token: String, @Body request: CreateCategoryRequest): Response<Category>
    
    @PUT("/api/v1/categories/{categoryId}")
    suspend fun updateCategory(@Header("Authorization") token: String, @Path("categoryId") categoryId: String, @Body request: UpdateCategoryRequest): Response<Category>
    
    @DELETE("/api/v1/categories/{categoryId}")
    suspend fun deleteCategory(@Header("Authorization") token: String, @Path("categoryId") categoryId: String): Response<Unit>
    
    @GET("/api/v1/categories/hierarchy")
    suspend fun getHierarchy(@Header("Authorization") token: String): Response<CategoryHierarchyResponse>
    
    @GET("/api/v1/categories/{categoryId}/expenses")
    suspend fun getCategoryExpenses(@Header("Authorization") token: String, @Path("categoryId") categoryId: String): Response<CategoryExpensesResponse>
    
    @DataClass("CategoriesResponse")
    data class CategoriesResponse(
        val categories: List<Category>,
        val total: Int,
        val limit: Int,
        val page: Int,
        val hasMore: Boolean
    )
    
    @DataClass("CategoryExpensesResponse")
    data class CategoryExpensesResponse(
        val expenses: List<Expense>,
        val totalAmount: Double,
        val expenseCount: Int,
        val startDate: String,
        val endDate: String
    )
    
    @DataClass("CategoryHierarchyResponse")
    data class CategoryHierarchyResponse(
        val rootCategories: List<Category>,
        val subCategories: Map<String, List<Category>>
    )
}