package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure add-expense logic (Stage 6): [seedPercentages] (prefilled
 * splits that always sum to 100), [validateExpenseForm] (the exact
 * user-facing messages) and [buildAddExpenseRequest] (the wire mapping,
 * including "blank text is dropped" and "EQUAL sends no split").
 */
class AddExpenseFormTest {

    private fun member(id: String) = GroupMemberDto(id = id, name = id)

    private fun members(vararg ids: String) = ids.map { member(it) }

    // -- seedPercentages --------------------------------------------------

    @Test
    fun `seed percentages sum to exactly 100 for one through twelve members`() {
        for (n in 1..12) {
            val ids = (1..n).map { "m$it" }
            val seeded = seedPercentages(ids)
            assertEquals(n, seeded.size)
            val sum = seeded.values.map { it.toDouble() }.sum()
            assertEquals(100.0, sum, 0.0001)
        }
    }

    @Test
    fun `seed percentages split evenly with the remainder first`() {
        val seeded = seedPercentages(listOf("a", "b", "c"))
        assertEquals("33.34", seeded["a"])
        assertEquals("33.33", seeded["b"])
        assertEquals("33.33", seeded["c"])
    }

    @Test
    fun `seed percentages for an empty list is empty`() {
        assertEquals(emptyMap<String, String>(), seedPercentages(emptyList()))
    }

    // -- validateExpenseForm ------------------------------------------------

    @Test
    fun `validation rejects a blank or non numeric amount`() {
        assertEquals(
            "Enter a valid amount",
            validateExpenseForm(members("a"), ExpenseFormState(amountText = ""))
        )
        assertEquals(
            "Enter a valid amount",
            validateExpenseForm(members("a"), ExpenseFormState(amountText = "abc"))
        )
    }

    @Test
    fun `validation rejects zero and negative amounts`() {
        assertEquals(
            "The amount must be greater than zero",
            validateExpenseForm(members("a"), ExpenseFormState(amountText = "0"))
        )
        assertEquals(
            "The amount must be greater than zero",
            validateExpenseForm(members("a"), ExpenseFormState(amountText = "-5"))
        )
    }

    @Test
    fun `validation accepts a minimal equal split form`() {
        assertNull(validateExpenseForm(members("a", "b"), ExpenseFormState(amountText = "42")))
    }

    @Test
    fun `percentage split needs an entry for every member`() {
        val form = ExpenseFormState(
            amountText = "42",
            splitMode = SplitMode.PERCENTAGE,
            percentages = mapOf("a" to "100")
        )
        assertEquals(
            "Enter a percentage for each member",
            validateExpenseForm(members("a", "b"), form)
        )
    }

    @Test
    fun `percentage split rejects non numeric entries`() {
        val form = ExpenseFormState(
            amountText = "42",
            splitMode = SplitMode.PERCENTAGE,
            percentages = mapOf("a" to "abc", "b" to "100")
        )
        assertEquals(
            "Enter a valid percentage for each member",
            validateExpenseForm(members("a", "b"), form)
        )
    }

    @Test
    fun `percentage split requires a sum of 100 within tolerance`() {
        val tooLow = ExpenseFormState(
            amountText = "42",
            splitMode = SplitMode.PERCENTAGE,
            percentages = mapOf("a" to "60", "b" to "30")
        )
        assertEquals(
            "Percentages must add up to 100% (currently 90.00%)",
            validateExpenseForm(members("a", "b"), tooLow)
        )

        // Within the 0.01 rounding tolerance even though neither is "round".
        val withinTolerance = ExpenseFormState(
            amountText = "42",
            splitMode = SplitMode.PERCENTAGE,
            percentages = mapOf("a" to "50.005", "b" to "49.995")
        )
        assertNull(validateExpenseForm(members("a", "b"), withinTolerance))
    }

    @Test
    fun `percentage split is unavailable without a member list`() {
        val form = ExpenseFormState(
            amountText = "42",
            splitMode = SplitMode.PERCENTAGE,
            percentages = mapOf("a" to "100")
        )
        assertEquals(
            "Member list unavailable - use an equal split",
            validateExpenseForm(emptyList(), form)
        )
    }

    // -- buildAddExpenseRequest ----------------------------------------------

    @Test
    fun `build request omits split payer and text for an equal split`() {
        val request = buildAddExpenseRequest("g1", ExpenseFormState(amountText = "42"))
        assertEquals("g1", request.groupId)
        assertEquals(42.0, request.amount, 0.0)
        assertNull(request.shares)
        assertNull(request.paidByUserId)
        assertNull(request.description)
        assertNull(request.category)
    }

    @Test
    fun `build request maps percentage entries to shares and trims raw text`() {
        val request = buildAddExpenseRequest(
            "g1",
            ExpenseFormState(
                amountText = "100",
                splitMode = SplitMode.PERCENTAGE,
                percentages = mapOf("a" to "60", "b" to " 40 ")
            )
        )
        val shares = request.shares
        assertNotNull(shares)
        assertEquals(2, shares!!.size)
        assertEquals("a", shares.first { it.userId == "a" }.userId)
        assertEquals(60.0, shares.first { it.userId == "a" }.percentage, 0.0)
        assertEquals("b", shares.first { it.userId == "b" }.userId)
        assertEquals(40.0, shares.first { it.userId == "b" }.percentage, 0.0)
    }

    @Test
    fun `build request trims and drops blank description and category`() {
        val blank = buildAddExpenseRequest(
            "g1",
            ExpenseFormState(amountText = "1", description = "   ", category = "")
        )
        assertNull(blank.description)
        assertNull(blank.category)

        val trimmed = buildAddExpenseRequest(
            "g1",
            ExpenseFormState(amountText = "1", description = "  Groceries  ", category = " FOOD ")
        )
        assertEquals("Groceries", trimmed.description)
        assertEquals("FOOD", trimmed.category)
    }

    @Test
    fun `build request carries the selected payer through`() {
        val request = buildAddExpenseRequest(
            "g1",
            ExpenseFormState(amountText = "1", payerId = "bb22")
        )
        assertEquals("bb22", request.paidByUserId)
    }
}
