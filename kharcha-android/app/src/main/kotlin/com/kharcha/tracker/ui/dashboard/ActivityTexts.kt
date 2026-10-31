package com.kharcha.tracker.ui.dashboard

import com.kharcha.tracker.data.remote.dto.ActivityLogDto
import com.kharcha.tracker.data.remote.dto.GroupMemberDto
import java.util.Locale

/**
 * Pure display logic for the Stage 7b activity feed, mirroring the web
 * frontend's `app.js` (activityActorName / activityMemberName /
 * eventPillClass / prettifyActivity / activityRangeParams / pager).
 *
 * Everything is string-based and unit-testable on the JVM (no `java.time`):
 * a [memberName] resolver callback carries the `/groups/{id}/members`
 * lookup into the pure helpers, so the tests can inject names directly.
 */
object ActivityTexts {

    /** The web UI's `ACTIVITY_PAGE_SIZE`. */
    const val PAGE_SIZE = 20

    /**
     * Validates a `yyyy-MM-dd` from/to pair and returns the ISO instants to
     * send as `date_from` / `date_to` (from at the start of its day, to at
     * the end of its - mirrors `activityRangeParams`). Returns null when
     * either date is malformed, or when both are present and `from` is
     * after `to` (ISO dates compare correctly as strings).
     */
    fun dateParams(from: String?, to: String?): DateParams? {
        val fromValue = from?.takeIf { it.isNotBlank() }
        val toValue = to?.takeIf { it.isNotBlank() }
        if (fromValue != null && !isIsoDate(fromValue)) return null
        if (toValue != null && !isIsoDate(toValue)) return null
        if (fromValue != null && toValue != null && fromValue > toValue) return null
        return DateParams(
            dateFrom = fromValue?.let { "$it T00:00:00".replace(" T", "T") },
            dateTo = toValue?.let { "$it T23:59:59.999".replace(" T", "T") }
        )
    }

    /** True for a well-formed `yyyy-MM-dd` date string. */
    fun isIsoDate(value: String): Boolean {
        val parts = value.split("-")
        if (parts.size != 3) return false
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        if (year !in 1000..9999 || month !in 1..12 || day !in 1..31) return false
        // Two-digit month/day only (ISO-8601 basic calendar dates).
        return parts[1].length == 2 && parts[2].length == 2
    }

    /** Normalised from/to instants for the activity query (nulls when unset). */
    data class DateParams(
        val dateFrom: String?,
        val dateTo: String?
    )

    /**
     * Pill category for an event kind - the web's `eventPillClass`, as a
     * stable key the UI maps to colours (CREATE/UPDATE/DELETE/LOCK/UNLOCK
     * / other).
     */
    fun eventPill(event: String?): String =
        when (event?.trim()?.uppercase(Locale.US)) {
            "CREATE" -> "CREATE"
            "UPDATE" -> "UPDATE"
            "DELETE" -> "DELETE"
            "LOCK" -> "LOCK"
            "UNLOCK" -> "UNLOCK"
            else -> "OTHER"
        }

    /**
     * Resolves an actor id to a display name, mirroring `activityMemberName`:
     * the member's name when it is set and differs from the id, otherwise
     * the id truncated to 8 chars; blank ids render as "unknown".
     */
    fun memberName(userId: String?, memberName: (String) -> String?): String {
        val id = userId?.takeIf { it.isNotBlank() } ?: return "unknown"
        val name = memberName(id)?.takeIf { it.isNotBlank() && it != id }
        return name ?: id.take(8)
    }

    /**
     * Actor display name, mirroring `activityActorName`: the logged
     * `actor_name` first, then the member lookup, then an 8-char id prefix,
     * then a dash when there is no actor at all.
     */
    fun actorName(
        log: ActivityLogDto,
        memberName: (userId: String) -> String?
    ): String {
        log.actorName?.takeIf { it.isNotBlank() }?.let { return it }
        val actorId = log.actorUserId
        if (actorId.isNullOrBlank()) return "—"
        return memberName(actorId)
            ?.takeIf { it.isNotBlank() && it != actorId }
            ?: actorId.take(8)
    }

    /**
     * Human-readable detail text, mirroring `prettifyActivity`: group
     * entity text is interpreted (member joins/leaves, locks), everything
     * else falls back to the raw `new_value` and finally a generic verb.
     */
    fun prettify(
        log: ActivityLogDto,
        memberName: (userId: String) -> String?
    ): String {
        val type = log.entityType.orEmpty()
        val event = (log.event ?: log.action).orEmpty().uppercase(Locale.US)
        val text = log.newValue.orEmpty()

        if (type == "group") {
            if (event == "CREATE") return "Created group “${text.ifBlank { "group" }}”"
            groupPattern("Added member", text)?.let { return "Added member ${memberName(it)}" }
            groupPattern("User joined", text)?.let { return "Joined (${memberName(it)})" }
            Regex("^User (.+) left group$").find(text)?.let {
                return "${memberName(it.groupValues[1])} left group"
            }
            when (event) {
                "LOCK" -> return "Group locked"
                "UNLOCK" -> return "Group unlocked"
                "DELETE" -> return "Group deleted"
            }
            return text.ifBlank { event.ifBlank { "—" } }
        }

        if (text.isNotBlank()) return text
        return when (event) {
            "CREATE" -> "Added"
            "UPDATE" -> "Updated"
            "DELETE" -> "Deleted"
            else -> event.ifBlank { "—" }
        }
    }

    /**
     * Resolves the "N events" count line: blank when there is nothing,
     * otherwise the singular/plural form the web UI shows - "(1 event)"
     * / "(12 events)".
     */
    fun totalLabel(total: Int): String =
        if (total <= 0) "" else "($total event" + if (total == 1) ")" else "s)"

    /**
     * Total page count for the pager (min 1), the web's
     * `Math.max(1, Math.ceil(total / PAGE_SIZE))`.
     */
    fun pageCount(total: Int, pageSize: Int = PAGE_SIZE): Int {
        if (pageSize <= 0) return 1
        val pages = (total + pageSize - 1) / pageSize
        return pages.coerceAtLeast(1)
    }

    /** Whether the pager is visible at all (hidden when one page suffices). */
    fun showPager(total: Int, pageSize: Int = PAGE_SIZE): Boolean =
        total > pageSize

    private fun groupPattern(prefix: String, text: String): String? {
        // `Added member <user id>` / `User joined <user id>`
        return Regex("^" + Regex.escape(prefix) + " (.+)$").find(text)?.groupValues?.get(1)
    }

    /**
     * Builds the member-id -> name resolver for a group's member list
     * (the `/groups/{id}/members` result), returning null for ids without
     * a usable name so the 8-char fallback kicks in.
     */
    fun memberNameResolver(members: List<GroupMemberDto>): (String) -> String? {
        val byId = members.associate { it.id to it.name }
        return { userId ->
            byId[userId]?.takeIf { it.isNotBlank() && it != userId }
        }
    }
}
