package com.kharcha.tracker.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure edit-expense form logic (Stage 7a): date seeding and
 * validation (leap-year aware), the whole-form validation rule (a
 * non-blank category can't be removed), the "has changes" gate, and the
 * wire-request builder that must only carry fields the user actually
 * edited (the gateway treats absent keys as "no change").
 */
class EditExpenseFormTest {

    // -- initialDateOf ---------------------------------------------------------

    @Test
    fun `seeds the yyyy-mm-dd prefix from an iso datetime`() {
        assertEquals("2025-11-01", initialDateOf("2025-11-01T10:30:00Z"))
    }

    @Test
    fun `seeds a date-only value as is`() {
        assertEquals("2025-11-01", initialDateOf("2025-11-01"))
    }

    @Test
    fun `returns null for a missing or malformed date`() {
        assertNull(initialDateOf(null))
        assertNull(initialDateOf(""))
        assertNull(initialDateOf("11/01/2025"))
        // The 10-char prefix must be a well-formed date, not just present.
        assertNull(initialDateOf("2025-1-01T10:30:00Z"))
    }

    // -- validateEditDate ---------------------------------------------------------

    @Test
    fun `accepts a valid date and a blank keep-original value`() {
        assertNull(validateEditDate("2025-11-01"))
        assertNull(validateEditDate("  "))
        assertNull(validateEditDate(""))
    }

    @Test
    fun `rejects a malformed format`() {
        assertEquals("Use the format YYYY-MM-DD", validateEditDate("01-11-2025"))
        assertEquals("Use the format YYYY-MM-DD", validateEditDate("2025-11-01 extra"))
    }

    @Test
    fun `rejects years out of range`() {
        assertEquals("Year out of range", validateEditDate("1989-12-31"))
        assertEquals("Year out of range", validateEditDate("2101-01-01"))
        assertNull(validateEditDate("1990-01-01"))
        assertNull(validateEditDate("2100-12-31"))
    }

    @Test
    fun `rejects months out of range`() {
        assertEquals("Month must be 01-12", validateEditDate("2025-00-10"))
        assertEquals("Month must be 01-12", validateEditDate("2025-13-10"))
    }

    @Test
    fun `rejects days out of range for the month`() {
        assertEquals("Day must be 01-31 for that month", validateEditDate("2025-01-00"))
        assertEquals("Day must be 01-31 for that month", validateEditDate("2025-01-32"))
        assertEquals("Day must be 01-30 for that month", validateEditDate("2025-04-31"))
        assertEquals("Day must be 01-30 for that month", validateEditDate("2025-11-31"))
    }

    @Test
    fun `handles leap years per the gregorian rule`() {
        assertNull(validateEditDate("2024-02-29")) // divisible by 4
        assertEquals("Day must be 01-28 for that month", validateEditDate("2023-02-29"))
        assertNull(validateEditDate("2000-02-29")) // divisible by 400
        assertEquals("Year out of range", validateEditDate("1900-02-29")) // century rule, out of range anyway
        assertEquals("Day must be 01-28 for that month", validateEditDate("2025-02-29"))
    }

    // -- validateEditExpenseForm -----------------------------------------------------

    private fun form(
        date: String = "",
        initialDate: String? = null,
        category: String = "",
        initialCategory: String? = null,
        description: String = "",
        initialDescription: String = "",
        payerId: String? = null,
        initialPayerId: String? = null,
        amount: Double = 100.0
    ) = EditExpenseFormState(
        initialDate = initialDate,
        initialCategory = initialCategory,
        initialDescription = initialDescription,
        initialPayerId = initialPayerId,
        amount = amount,
        date = date,
        category = category,
        description = description,
        payerId = payerId
    )

    @Test
    fun `a valid form passes`() {
        assertNull(validateEditExpenseForm(form(date = "2025-11-02", initialDate = "2025-11-01")))
        assertNull(validateEditExpenseForm(form(category = "FOOD", initialCategory = "TRAVEL")))
    }

    @Test
    fun `an invalid date fails with the date message`() {
        assertEquals(
            "Day must be 01-30 for that month",
            validateEditExpenseForm(form(date = "2025-04-31"))
        )
    }

    @Test
    fun `a non-blank category can't be blanked out`() {
        assertEquals(
            "The category can't be removed - keep it or enter a new one",
            validateEditExpenseForm(form(category = "", initialCategory = "FOOD"))
        )
    }

    @Test
    fun `a blank original category can stay blank`() {
        assertNull(validateEditExpenseForm(form(category = "", initialCategory = null)))
        assertNull(validateEditExpenseForm(form(category = "", initialCategory = "")))
    }

    // -- hasChanges -------------------------------------------------------------------

    @Test
    fun `an unedited form has no changes`() {
        assertFalse(hasChanges(form()))
        assertFalse(
            hasChanges(
                form(
                    date = "2025-11-01",
                    initialDate = "2025-11-01",
                    category = "FOOD",
                    initialCategory = "FOOD",
                    description = "Groceries",
                    initialDescription = "Groceries",
                    payerId = "aa11",
                    initialPayerId = "aa11"
                )
            )
        )
    }

    @Test
    fun `editing any field counts as a change`() {
        assertTrue(hasChanges(form(date = "2025-11-02", initialDate = "2025-11-01")))
        assertTrue(hasChanges(form(category = "FOOD", initialCategory = "TRAVEL")))
        assertTrue(hasChanges(form(description = "", initialDescription = "Groceries")))
        assertTrue(hasChanges(form(payerId = "bb22", initialPayerId = "aa11")))
    }

    @Test
    fun `whitespace-only edits are not changes`() {
        assertFalse(hasChanges(form(date = "  ", initialDate = null)))
        assertFalse(hasChanges(form(category = "  ", initialCategory = null)))
    }

    // -- buildExpenseUpdateRequest ------------------------------------------------------

    @Test
    fun `an unedited form builds an empty request`() {
        val request = buildExpenseUpdateRequest(
            form(
                date = "2025-11-01",
                initialDate = "2025-11-01",
                category = "FOOD",
                initialCategory = "FOOD",
                description = "Groceries",
                initialDescription = "Groceries",
                payerId = "aa11",
                initialPayerId = "aa11"
            )
        )
        assertNull(request.dateTime)
        assertNull(request.category)
        assertNull(request.description)
        assertNull(request.paidBy)
    }

    @Test
    fun `an edited date goes on the wire`() {
        val request = buildExpenseUpdateRequest(form(date = "2025-11-02", initialDate = "2025-11-01"))
        assertEquals("2025-11-02", request.dateTime)
        assertNull(request.category)
        assertNull(request.description)
        assertNull(request.paidBy)
    }

    @Test
    fun `an edited category goes on the wire`() {
        val request = buildExpenseUpdateRequest(form(category = "FOOD", initialCategory = "TRAVEL"))
        assertEquals("FOOD", request.category)
        assertNull(request.dateTime)
        assertNull(request.paidBy)
    }

    @Test
    fun `clearing the description sends an empty string`() {
        val request = buildExpenseUpdateRequest(form(description = "", initialDescription = "Groceries"))
        assertEquals("", request.description)
        assertNull(request.dateTime)
        assertNull(request.category)
        assertNull(request.paidBy)
    }

    @Test
    fun `a new payer sends the full amount on that one payer`() {
        val request = buildExpenseUpdateRequest(
            form(payerId = "bb22", initialPayerId = "aa11", amount = 450.5)
        )
        val paidBy = request.paidBy
        assertEquals(1, paidBy!!.size)
        assertEquals("bb22", paidBy.first().userId)
        assertEquals(450.5, paidBy.first().amount, 0.0)
        assertNull(request.dateTime)
        assertNull(request.category)
        assertNull(request.description)
    }

    @Test
    fun `a full edit carries every changed field`() {
        val request = buildExpenseUpdateRequest(
            form(
                date = "2025-12-01",
                initialDate = "2025-11-01",
                category = "TRAVEL",
                initialCategory = "FOOD",
                description = "",
                initialDescription = "Groceries",
                payerId = "cc33",
                initialPayerId = "aa11",
                amount = 99.0
            )
        )
        assertEquals("2025-12-01", request.dateTime)
        assertEquals("TRAVEL", request.category)
        assertEquals("", request.description)
        assertEquals(1, request.paidBy!!.size)
        assertEquals("cc33", request.paidBy!!.first().userId)
        assertEquals(99.0, request.paidBy!!.first().amount, 0.0)
    }
}
