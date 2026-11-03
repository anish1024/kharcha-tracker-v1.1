package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.GroupDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * Test for resolveInitialExpenseGroupId function.
 * 
 * The function resolves the initial expense group ID:
 * - If a stored ID is provided and exists in the groups list, return the stored ID
 * - Otherwise, return the ID of the first group in the list
 */
class ExpenseGroupSelectionTest {

    @Test
    fun `resolveInitialExpenseGroupId returns stored ID when present`() {
        // Arrange
        val groups = listOf(
            GroupDto(id = "group-1", name = "Group 1", currency = "INR"),
            GroupDto(id = "group-2", name = "Group 2", currency = "INR")
        )
        val storedId = "group-1"

        // Act
        val result = resolveInitialExpenseGroupId(groups, storedId)

        // Assert
        assertEquals("group-1", result)
    }

    @Test
    fun `resolveInitialExpenseGroupId returns first group ID when no stored ID`() {
        // Arrange
        val groups = listOf(
            GroupDto(id = "group-1", name = "Group 1", currency = "INR"),
            GroupDto(id = "group-2", name = "Group 2", currency = "INR")
        )
        val storedId = null

        // Act
        val result = resolveInitialExpenseGroupId(groups, storedId)

        // Assert
        assertEquals("group-1", result)
    }

    @Test
    fun `resolveInitialExpenseGroupId returns null when groups is empty`() {
        // Arrange
        val groups = listOf<GroupDto>()
        val storedId = "group-1"

        // Act
        val result = resolveInitialExpenseGroupId(groups, storedId)

        // Assert
        assertEquals(null, result)
    }
}