package com.kharcha.tracker.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Stage 7a settlement DTOs parse the gateway's payout shapes
 * (verified against `kharcha_service/models/payout.py`) and serialize to
 * the exact wire keys the FastAPI routes read - including the
 * "absent key means no change / server default" contracts for the PATCH
 * body and the payout-action bodies.
 */
class SettlementDtosTest {

    private val gson = Gson()

    // -- PayoutDto (response) ----------------------------------------------

    @Test
    fun `payout parses the gateway shape`() {
        val payout = gson.fromJson(
            """
            {
              "id": "p1",
              "group_id": "g1",
              "payor_id": "aa11",
              "payee_id": "bb22",
              "amt": 1200.5,
              "status": "PENDING",
              "expense_ids": [17, 18],
              "is_adhoc": false,
              "settlement_round_id": null,
              "replaces_payout_ids": [],
              "payor_evidence": null,
              "payee_evidence": "cash received",
              "status_reason": null,
              "paid_at": null,
              "settled_at": null
            }
            """,
            PayoutDto::class.java
        )
        assertEquals("p1", payout.id)
        assertEquals("g1", payout.groupId)
        assertEquals("aa11", payout.payorId)
        assertEquals("bb22", payout.payeeId)
        assertEquals(1200.5, payout.amount, 0.0)
        assertEquals("PENDING", payout.status)
        assertEquals(listOf(17, 18), payout.expenseIds)
        assertEquals(false, payout.isAdhoc)
        assertNull(payout.settlementRoundId)
        assertTrue(payout.replacesPayoutIds.isEmpty())
        assertNull(payout.payorEvidence)
        assertEquals("cash received", payout.payeeEvidence)
        assertNull(payout.statusReason)
    }

    @Test
    fun `payout with absent list fields exposes empty lists`() {
        val payout = gson.fromJson(
            """{"id":"p2","amt":42.0,"status":"SETTLED"}""",
            PayoutDto::class.java
        )
        assertTrue(payout.expenseIds.isEmpty())
        assertTrue(payout.replacesPayoutIds.isEmpty())
        assertNull(payout.payorId)
    }

    @Test
    fun `failed payout carries the status reason`() {
        val payout = gson.fromJson(
            """{"id":"p3","amt":10.0,"status":"FAILED","status_reason":"wrong amount","paid_at":"2025-11-01T10:00:00Z"}""",
            PayoutDto::class.java
        )
        assertEquals("FAILED", payout.status)
        assertEquals("wrong amount", payout.statusReason)
        assertEquals("2025-11-01T10:00:00Z", payout.paidAt)
    }

    // -- SettleRequest ------------------------------------------------------

    @Test
    fun `settle request with no kind serializes to an empty object`() {
        val json = JsonParser.parseString(gson.toJson(SettleRequest())).asJsonObject
        assertTrue(json.entrySet().isEmpty())
    }

    @Test
    fun `settle request with a kind serializes the kind key`() {
        val parsed = JsonParser.parseString(
            gson.toJson(SettleRequest(kind = "PERIODIC"))
        ).asJsonObject
        assertEquals("PERIODIC", parsed.get("kind").asString)
    }

    // -- PayoutActionRequest ------------------------------------------------

    @Test
    fun `payout action request with no fields serializes to an empty object`() {
        val json = JsonParser.parseString(
            gson.toJson(PayoutActionRequest())
        ).asJsonObject
        assertTrue(json.entrySet().isEmpty())
    }

    @Test
    fun `payout action request serializes only the set fields`() {
        val evidenceOnly = JsonParser.parseString(
            gson.toJson(PayoutActionRequest(evidence = "UPI ref 4821"))
        ).asJsonObject
        assertEquals("UPI ref 4821", evidenceOnly.get("evidence").asString)
        assertFalse(evidenceOnly.has("reason"))

        val reasonOnly = JsonParser.parseString(
            gson.toJson(PayoutActionRequest(reason = "wrong amount"))
        ).asJsonObject
        assertEquals("wrong amount", reasonOnly.get("reason").asString)
        assertFalse(reasonOnly.has("evidence"))
    }

    // -- ExpenseUpdateRequest -----------------------------------------------

    @Test
    fun `unmodified expense update serializes to an empty object`() {
        val json = JsonParser.parseString(
            gson.toJson(ExpenseUpdateRequest())
        ).asJsonObject
        assertTrue(json.entrySet().isEmpty())
    }

    @Test
    fun `expense update carries only the edited fields`() {
        val parsed = JsonParser.parseString(
            gson.toJson(
                ExpenseUpdateRequest(
                    dateTime = "2025-11-01",
                    category = "FOOD",
                    description = "Groceries",
                    paidBy = listOf(ExpensePaymentDto(userId = "aa11", amount = 450.5))
                )
            )
        ).asJsonObject
        assertEquals("2025-11-01", parsed.get("date_time").asString)
        assertEquals("FOOD", parsed.get("expn_ctgry").asString)
        assertEquals("Groceries", parsed.get("expn_desc").asString)

        val paidBy = parsed.getAsJsonArray("paid_by")
        assertEquals(1, paidBy.size())
        assertEquals("aa11", paidBy[0].asJsonObject.get("user_id").asString)
        assertEquals(450.5, paidBy[0].asJsonObject.get("amt").asDouble, 0.0)
    }

    @Test
    fun `expense update with a blank description sends the key to clear it`() {
        val parsed = JsonParser.parseString(
            gson.toJson(ExpenseUpdateRequest(description = ""))
        ).asJsonObject
        // The gateway sets `expn_desc` whenever the key is present, so an
        // empty string is the "clear the description" signal - it must NOT
        // be omitted.
        assertTrue(parsed.has("expn_desc"))
        assertEquals("", parsed.get("expn_desc").asString)
    }

    // -- SettlementPreviewDto (response) -------------------------------------

    @Test
    fun `settlement preview parses net dues and the payout lists`() {
        val preview = gson.fromJson(
            """
            {
              "group_id": "g1",
              "group_status": "OPEN",
              "net_dues": {"aa11": 250.0, "bb22": -250.0},
              "open_payouts": [],
              "proposed_payouts": [
                {"id":"p1","amt":250.0,"payor_id":"bb22","payee_id":"aa11","status":"PENDING"}
              ],
              "orphan_payouts": null
            }
            """,
            SettlementPreviewDto::class.java
        )
        assertEquals("g1", preview.groupId)
        assertEquals("OPEN", preview.groupStatus)
        assertEquals(2, preview.netDues.size)
        assertEquals(250.0, preview.netDues.getValue("aa11"), 0.0)
        assertEquals(-250.0, preview.netDues.getValue("bb22"), 0.0)
        assertTrue(preview.openPayouts.isEmpty())
        assertEquals(1, preview.proposedPayouts.size)
        assertEquals("p1", preview.proposedPayouts.first().id)
        assertEquals(250.0, preview.proposedPayouts.first().amount, 0.0)
        assertTrue(preview.orphanPayouts.isEmpty())
    }

    @Test
    fun `settlement preview with absent payout lists exposes empty lists`() {
        val preview = gson.fromJson(
            """{"group_id":"g2","net_dues":{}}""",
            SettlementPreviewDto::class.java
        )
        assertTrue(preview.openPayouts.isEmpty())
        assertTrue(preview.proposedPayouts.isEmpty())
        assertTrue(preview.orphanPayouts.isEmpty())
    }
}
