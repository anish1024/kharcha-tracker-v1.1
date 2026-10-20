# Release Notes

## v1.1.0

### Features

- **Activity / Audit Log screen**: New Activity view with activity-sorted group picker (auto-selects most recently active group), date-range filter (inclusive), paginated table (20 per page) with total count, and CSV export of the current range (RFC4180-quoted, UTF-8 BOM, chunked fetch up to 10,000 rows).
- **Group activity API**: `GET /api/v1/groups/{group_id}/activity` now accepts `date_from`/`date_to` (ISO instants), returns `X-Total-Count`, and aggregates group + expense audit rows (union, deduplicated, newest first).
- **Groups sorted by activity**: `GET /api/v1/groups?sort=default|activity` orders groups by most recent audit activity.
- **Expense audit coverage**: Creating/updating/deleting expenses now writes audit rows linked to the group; activity view surfaces expense events alongside group events.
- **Frontend API metadata**: `api.js` adds `api.getMeta`/`withMeta` support to read response headers.

### Bugs

- None.

