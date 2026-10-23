# Release Notes

## v1.2.0

### Features

- **Invitation cards show the group name**: pending invitations on the Groups screen now display the inviting group's name, resolved server-side (`invitation.group_name`; `null` if the group was deleted), matching the detail the owner-facing notification carries.
- **Default "Personal Expenses" group**: every user now has a personal group in INR — created idempotently at signup and re-ensured on `GET /api/v1/groups` (covering pre-existing and magic-link accounts); a deleted default group is re-created on the next ensure. The group has no special protection and remains deletable.
- **Last-used group pre-selected on the Expenses screen**: the last group the user selected is remembered (localStorage) and pre-selected on return; if that group is gone or the user is no longer a member, the first group in the list is selected instead.
- **Stacked spending chart with stable category colors**: the dashboard bar chart is now a stacked bar — one segment per expense category per bucket (largest at the bottom) with per-segment hover details — and the bar and pie charts share one stable category→color mapping, so a category keeps the same color in both charts and in the legend.
- **Group notes**: members can leave notes on a group — a message (≤500 chars), an author date, and up to 20 tags (≤50 chars each, comma-separated in the UI). New endpoints: `POST`/`GET /api/v1/groups/{group_id}/notes` and `PATCH`/`DELETE /api/v1/groups/{group_id}/notes/{note_id}`; create/update/delete are audited with `entity_type` `group_note`. Notes render on the group card with edit/delete actions.
- **Corpus fund (derived, read-only)**: each group has a corpus fund. Members record corpus deposits as expenses in the new `CORPUS` category (the deposit is split across members, e.g. equally), and the corpus balance is shown in the settlement panel, derived from the settlement preview: confirmed (SETTLED) orphan payouts plus overpaid (creditor) net balances. No new tables or endpoints — the fund is display-only (no offsets or withdrawals).

### Bugs

- None.

## v1.1.0

### Features

- **Activity / Audit Log screen**: New Activity view with activity-sorted group picker (auto-selects most recently active group), date-range filter (inclusive), paginated table (20 per page) with total count, and CSV export of the current range (RFC4180-quoted, UTF-8 BOM, chunked fetch up to 10,000 rows).
- **Group activity API**: `GET /api/v1/groups/{group_id}/activity` now accepts `date_from`/`date_to` (ISO instants), returns `X-Total-Count`, and aggregates group + expense audit rows (union, deduplicated, newest first).
- **Groups sorted by activity**: `GET /api/v1/groups?sort=default|activity` orders groups by most recent audit activity.
- **Expense audit coverage**: Creating/updating/deleting expenses now writes audit rows linked to the group; activity view surfaces expense events alongside group events.
- **Frontend API metadata**: `api.js` adds `api.getMeta`/`withMeta` support to read response headers.

### Bugs

- None.

