package com.kharcha.tracker.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the Stage 6 mutation DTOs serialize to the exact wire keys the
 * FastAPI gateway reads (verified against `main.py` and `group_service.py`),
 * including the "absent key means server default" contract for the
 * optional expense fields (`expn_desc`, `expn_ctgry`, `paid_by_user_id`,
 * `expn_splt`).
 */
class GroupMutationsDtosTest {

    private val gson = Gson()

    @Test
    fun `create group request serializes name and currency`() {
        val parsed = JsonParser.parseString(
            gson.toJson(CreateGroupRequest(name = "Flatmates", currency = "INR"))
        ).asJsonObject
        assertEquals("Flatmates", parsed.get("name").asString)
        assertEquals("INR", parsed.get("currency").asString)
    }

    @Test
    fun `invite accept request serializes the invite_code key`() {
        val parsed = JsonParser.parseString(
            gson.toJson(InviteAcceptRequest(inviteCode = "ABCD1234"))
        ).asJsonObject
        assertEquals("ABCD1234", parsed.get("invite_code").asString)
    }

    @Test
    fun `invite code response parses the gateway shape`() {
        val response = gson.fromJson(
            """{"invite_code":"ABCD1234"}""",
            InviteCodeResponse::class.java
        )
        assertEquals("ABCD1234", response.inviteCode)
    }

    @Test
    fun `group member parses the gateway shape and the fallback shape`() {
        val full = gson.fromJson(
            """{"id":"aa11","name":"Anish","email":"anish@example.com","avatar_url":null}""",
            GroupMemberDto::class.java
        )
        assertEquals("aa11", full.id)
        assertEquals("Anish", full.name)
        assertEquals("anish@example.com", full.email)
        assertNull(full.avatarUrl)

        // Missing user record: the gateway falls back to {id, name: <id>,
        // email: "", avatar_url: null}.
        val fallback = gson.fromJson(
            """{"id":"bb22","name":"bb22","email":"","avatar_url":null}""",
            GroupMemberDto::class.java
        )
        assertEquals("bb22", fallback.id)
        assertEquals("bb22", fallback.name)
        assertEquals("", fallback.email)
    }

    @Test
    fun `equal split expense omits split payer and optional text on the wire`() {
        val parsed = JsonParser.parseString(
            gson.toJson(AddExpenseRequest(groupId = "g1", amount = 450.5))
        ).asJsonObject
        assertEquals("g1", parsed.get("group_id").asString)
        assertEquals(450.5, parsed.get("expn_amnt").asDouble, 0.0)
        assertFalse(parsed.has("expn_desc"))
        assertFalse(parsed.has("expn_ctgry"))
        assertFalse(parsed.has("paid_by_user_id"))
        assertFalse(parsed.has("expn_splt"))
    }

    @Test
    fun `percentage split expense sends shares payer description and category`() {
        val parsed = JsonParser.parseString(
            gson.toJson(
                AddExpenseRequest(
                    groupId = "g1",
                    amount = 450.5,
                    description = "Groceries",
                    category = "FOOD",
                    paidByUserId = "aa11",
                    shares = listOf(
                        ExpenseShareDto(userId = "aa11", percentage = 60.0),
                        ExpenseShareDto(userId = "bb22", percentage = 40.0)
                    )
                )
            )
        ).asJsonObject
        assertEquals("Groceries", parsed.get("expn_desc").asString)
        assertEquals("FOOD", parsed.get("expn_ctgry").asString)
        assertEquals("aa11", parsed.get("paid_by_user_id").asString)

        val shares = parsed.getAsJsonArray("expn_splt")
        assertEquals(2, shares.size())
        assertEquals("aa11", shares[0].asJsonObject.get("user_id").asString)
        assertEquals(60.0, shares[0].asJsonObject.get("percentage").asDouble, 0.0)
        assertEquals("bb22", shares[1].asJsonObject.get("user_id").asString)
        assertEquals(40.0, shares[1].asJsonObject.get("percentage").asDouble, 0.0)
    }

    @Test
    fun `expense share serializes user_id and percentage`() {
        val parsed = JsonParser.parseString(
            gson.toJson(ExpenseShareDto(userId = "aa11", percentage = 100.0))
        ).asJsonObject
        assertEquals("aa11", parsed.get("user_id").asString)
        assertEquals(100.0, parsed.get("percentage").asDouble, 0.0)
    }
}
