package com.kharcha.tracker.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the Stage 5 DTOs parse the exact JSON shapes the FastAPI
 * gateway emits (and round-trip back through Gson), including the fields
 * the app intentionally does not map (payout lists).
 */
class DashboardDtosTest {

    private val gson = Gson()

    @Test
    fun `group parses the gateway shape and round-trips`() {
        val json = """
            {
              "id": "3b5d3c2e-9f6a-4e1b-8c2d-1a2b3c4d5e6f",
              "name": "Flatmates",
              "created_by_user_id": "aa11",
              "invite_code": "ABCD1234",
              "members": ["aa11", "bb22"],
              "status": "ACTIVE",
              "currency": "INR",
              "time_created": "2026-10-01T09:00:00+00:00",
              "last_updated": "2026-10-29T14:22:03+00:00"
            }
        """.trimIndent()

        val group = gson.fromJson(json, GroupDto::class.java)
        assertEquals("Flatmates", group.name)
        assertEquals("INR", group.currency)
        assertEquals(listOf("aa11", "bb22"), group.members)

        val roundTrip = gson.fromJson(gson.toJson(group), GroupDto::class.java)
        assertEquals(group, roundTrip)
    }

    @Test
    fun `group tolerates missing optional fields`() {
        val group = gson.fromJson(
            """{"id":"g1","name":"Solo"}""",
            GroupDto::class.java
        )
        assertEquals("g1", group.id)
        assertEquals("Solo", group.name)
        assertEquals(emptyList<String>(), group.members)
        assertEquals(null, group.currency)
    }

    @Test
    fun `expense round-trips shares and payments`() {
        val json = """
            {
              "trans_id": 17,
              "group_id": "g1",
              "date_time": "2026-10-29T14:22:03+00:00",
              "expn_ctgry": "FOOD",
              "expn_desc": "Groceries",
              "expn_amnt": 450.5,
              "expn_splt": [
                {"user_id": "aa11", "percentage": 60.0},
                {"user_id": "bb22", "percentage": 40.0}
              ],
              "paid_by": [{"user_id": "aa11", "amt": 450.5}],
              "expn_bill_screenshot": "",
              "expn_payment_screenshot": "",
              "status": "ACTIVE"
            }
        """.trimIndent()

        val expense = gson.fromJson(json, ExpenseDto::class.java)
        assertEquals(17, expense.transId)
        assertEquals("FOOD", expense.category)
        assertEquals(450.5, expense.amount, 0.0)
        assertEquals(2, expense.shares.size)
        assertEquals(60.0, expense.shares[0].percentage, 0.0)
        assertEquals(40.0, expense.shares[1].percentage, 0.0)
        assertEquals(1, expense.payments.size)
        assertEquals(450.5, expense.payments[0].amount, 0.0)

        val roundTrip = gson.fromJson(gson.toJson(expense), ExpenseDto::class.java)
        assertEquals(expense, roundTrip)
    }

    @Test
    fun `expense tolerates empty split and payment lists`() {
        val expense = gson.fromJson(
            """
            {"trans_id": 3, "group_id": "g1", "expn_desc": "Snacks", "expn_amnt": 20.0}
            """,
            ExpenseDto::class.java
        )
        assertEquals(3, expense.transId)
        assertEquals(emptyList<ExpenseShareDto>(), expense.shares)
        assertEquals(emptyList<ExpensePaymentDto>(), expense.payments)
    }

    @Test
    fun `settlement preview parses net_dues and ignores payout lists`() {
        val json = """
            {
              "group_id": "g1",
              "group_status": "ACTIVE",
              "net_dues": {"aa11": -250.0, "bb22": 100.5, "cc33": 149.5},
              "open_payouts": [{"id": "p1", "amount": 50.0}],
              "proposed_payouts": [{"id": "p2", "amount": 250.0}],
              "orphan_payouts": []
            }
        """.trimIndent()

        val preview = gson.fromJson(json, SettlementPreviewDto::class.java)
        assertEquals("g1", preview.groupId)
        assertEquals(3, preview.netDues.size)
        assertEquals(-250.0, preview.netDues["aa11"]!!, 0.0)
        assertEquals(100.5, preview.netDues["bb22"]!!, 0.0)
        assertEquals(149.5, preview.netDues["cc33"]!!, 0.0)
    }

    @Test
    fun `settlement preview tolerates an empty net_dues map`() {
        val preview = gson.fromJson(
            """{"group_id": "g1", "group_status": "ACTIVE", "net_dues": {}}""",
            SettlementPreviewDto::class.java
        )
        assertEquals("g1", preview.groupId)
        assertEquals(emptyMap<String, Double>(), preview.netDues)
    }
}
