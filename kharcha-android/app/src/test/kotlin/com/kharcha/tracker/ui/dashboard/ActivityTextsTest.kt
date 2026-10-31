package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Stage 7b activity feed display logic, mirroring the web frontend's
 * `activityActorName` / `prettifyActivity` / `eventPillClass` /
 * `activityRangeParams` / pager conventions.
 */
class ActivityTextsTest {

    private val resolver = ActivityTexts.memberNameResolver(
        listOf(
            GroupMemberDto(id = "aa11", name = "Sudeep"),
            GroupMemberDto(id = "bb22", name = null)
        )
    )

    // -- date range validation (web `activityRangeParams`) -------------------

    @Test
    fun `dateParams renders both bounds as full-day instants`() {
        val params = ActivityTexts.dateParams("2025-11-01", "2025-11-30")
        assertEquals("2025-11-01T00:00:00", params?.dateFrom)
        assertEquals("2025-11-30T23:59:59.999", params?.dateTo)
    }

    @Test
    fun `dateParams accepts a single bound`() {
        assertEquals("2025-11-01T00:00:00", ActivityTexts.dateParams("2025-11-01", null)?.dateFrom)
        assertNull(ActivityTexts.dateParams("2025-11-01", null)?.dateTo)
        assertNull(ActivityTexts.dateParams(null, "2025-11-30")?.dateFrom)
        assertEquals("2025-11-30T23:59:59.999", ActivityTexts.dateParams(null, "2025-11-30")?.dateTo)
    }

    @Test
    fun `dateParams treats blank bounds as unbounded`() {
        val params = ActivityTexts.dateParams("", "  ")
        assertNull(params?.dateFrom)
        assertNull(params?.dateTo)
    }

    @Test
    fun `dateParams rejects malformed dates`() {
        assertNull(ActivityTexts.dateParams("2025/11/01", null))
        assertNull(ActivityTexts.dateParams(null, "not-a-date"))
        assertNull(ActivityTexts.dateParams("2025-13-01", null))
        assertNull(ActivityTexts.dateParams("2025-11-32", null))
    }

    @Test
    fun `dateParams rejects a from date after the to date`() {
        assertNull(ActivityTexts.dateParams("2025-11-30", "2025-11-01"))
    }

    @Test
    fun `isIsoDate accepts two-digit ISO dates only`() {
        assertTrue(ActivityTexts.isIsoDate("2025-11-01"))
        assertTrue(ActivityTexts.isIsoDate("1999-12-31"))
        assertFalse(ActivityTexts.isIsoDate("2025-1-01"))
        assertFalse(ActivityTexts.isIsoDate("2025-11-01T00:00:00Z"))
        assertFalse(ActivityTexts.isIsoDate("20251101"))
    }

    // -- event pill (web `eventPillClass`) ------------------------------------

    @Test
    fun `eventPill maps known kinds case-insensitively`() {
        assertEquals("CREATE", ActivityTexts.eventPill("create"))
        assertEquals("CREATE", ActivityTexts.eventPill(" CREATE "))
        assertEquals("UPDATE", ActivityTexts.eventPill("UPDATE"))
        assertEquals("DELETE", ActivityTexts.eventPill("delete"))
        assertEquals("LOCK", ActivityTexts.eventPill("LOCK"))
        assertEquals("UNLOCK", ActivityTexts.eventPill("UNLOCK"))
    }

    @Test
    fun `eventPill buckets everything else as OTHER`() {
        assertEquals("OTHER", ActivityTexts.eventPill("SETTLE"))
        assertEquals("OTHER", ActivityTexts.eventPill(""))
        assertEquals("OTHER", ActivityTexts.eventPill(null))
    }

    // -- actor resolution (web `activityActorName`) ---------------------------

    @Test
    fun `actorName prefers the logged actor name`() {
        val log = activityLog(actorName = "Sudeep", actorUserId = "aa11")
        assertEquals("Sudeep", ActivityTexts.actorName(log, resolver))
    }

    @Test
    fun `actorName falls back to the member lookup`() {
        val log = activityLog(actorName = null, actorUserId = "aa11")
        assertEquals("Sudeep", ActivityTexts.actorName(log, resolver))
    }

    @Test
    fun `actorName falls back to an 8-char id prefix`() {
        val log = activityLog(actorName = null, actorUserId = "aaaaaaaa-bbbb-cccc")
        assertEquals("aaaaaaaa", ActivityTexts.actorName(log, resolver))
    }

    @Test
    fun `actorName with no actor at all renders a dash`() {
        assertEquals("—", ActivityTexts.actorName(activityLog(), resolver))
    }

    @Test
    fun `memberNameResolver skips id-equal and blank names`() {
        val resolve = ActivityTexts.memberNameResolver(
            listOf(
                GroupMemberDto(id = "aa11", name = "Sudeep"),
                GroupMemberDto(id = "bb22", name = "bb22"),
                GroupMemberDto(id = "cc33", name = "")
            )
        )
        assertEquals("Sudeep", resolve("aa11"))
        assertNull(resolve("bb22"))
        assertNull(resolve("cc33"))
        assertNull(resolve("unknown"))
    }

    // -- detail text (web `prettifyActivity`) ---------------------------------

    @Test
    fun `prettify names a created group`() {
        val log = activityLog(event = "CREATE", entity = "group", newValue = "Flatmates")
        assertEquals("Created group “Flatmates”", ActivityTexts.prettify(log, resolver))
    }

    @Test
    fun `prettify falls back to a generic group name`() {
        val log = activityLog(event = "CREATE", entity = "group", newValue = "")
        assertEquals("Created group “group”", ActivityTexts.prettify(log, resolver))
    }

    @Test
    fun `prettify resolves member joins and leaves`() {
        val added = activityLog(entity = "group", newValue = "Added member aa11")
        val joined = activityLog(entity = "group", newValue = "User joined aa11")
        val left = activityLog(entity = "group", newValue = "User aa11 left group")
        assertEquals("Added member Sudeep", ActivityTexts.prettify(added, resolver))
        assertEquals("Joined (Sudeep)", ActivityTexts.prettify(joined, resolver))
        assertEquals("Sudeep left group", ActivityTexts.prettify(left, resolver))
    }

    @Test
    fun `prettify phrases group lock states`() {
        assertEquals(
            "Group locked",
            ActivityTexts.prettify(activityLog(event = "LOCK", entity = "group"), resolver)
        )
        assertEquals(
            "Group unlocked",
            ActivityTexts.prettify(activityLog(event = "UNLOCK", entity = "group"), resolver)
        )
        assertEquals(
            "Group deleted",
            ActivityTexts.prettify(activityLog(event = "DELETE", entity = "group"), resolver)
        )
    }

    @Test
    fun `prettify returns the raw detail for other entities`() {
        assertEquals(
            "Expenses settled",
            ActivityTexts.prettify(
                activityLog(entity = "payout", newValue = "Expenses settled"),
                resolver
            )
        )
        assertEquals(
            "Added",
            ActivityTexts.prettify(activityLog(event = "CREATE", entity = "expense"), resolver)
        )
        assertEquals(
            "Updated",
            ActivityTexts.prettify(activityLog(event = "UPDATE", entity = "expense"), resolver)
        )
        assertEquals(
            "Deleted",
            ActivityTexts.prettify(activityLog(event = "DELETE", entity = "expense"), resolver)
        )
        assertEquals("—", ActivityTexts.prettify(activityLog(entity = "expense"), resolver))
    }

    // -- pager -----------------------------------------------------------------

    @Test
    fun `totalLabel renders singular and plural`() {
        assertEquals("", ActivityTexts.totalLabel(0))
        assertEquals("(1 event)", ActivityTexts.totalLabel(1))
        assertEquals("(21 events)", ActivityTexts.totalLabel(21))
    }

    @Test
    fun `pageCount rounds up and is at least 1`() {
        assertEquals(1, ActivityTexts.pageCount(0))
        assertEquals(1, ActivityTexts.pageCount(20))
        assertEquals(2, ActivityTexts.pageCount(21))
        assertEquals(5, ActivityTexts.pageCount(100))
        assertEquals(1, ActivityTexts.pageCount(0, pageSize = 5))
    }

    @Test
    fun `showPager is true only past one page`() {
        assertFalse(ActivityTexts.showPager(0))
        assertFalse(ActivityTexts.showPager(20))
        assertTrue(ActivityTexts.showPager(21))
    }
}
