# AGENT.md — Kharcha Tracker feature: groups

## Module

- `js/features/groups.js`

## Exports

- `mount()` — group CRUD, notes, mount
- `loadGroups()`, `refreshGroupCount()` (loads into state)
- `bindGroupActions()` — delegated on `#groups-list`

## DOM IDs used

- `#groups-list` — delegated click/submit container
- `#btn-new-group`, `#btn-new-group-icon`
- `#form-create-group`, `#new-group-name`, `#new-group-currency`,
  `#create-group-error`, `#btn-cancel-create-group`
- `#btn-join-group`, `#join-icon`
- `#form-join-group`, `#join-group-id`, `#join-code`,
  `#join-error`, `#join-submit`
- `#btn-invite-email`, `#invite-icon`
- `#form-invite-email`, `#invite-group`, `#invite-email`,
  `#invite-error`, `#invite-submit`
- `#invites-list` — invitation cards list

## Notes

- Group notes rendering/delegation lives in groups.js
- Settlement actions delegated via `bindSettlementActions()`
- Exports `bindSettlementActions()` called from app.js boot

## State touched

- `state.groupsCache`, `state.membersCache`, `state.expensesCache`,
  `state.settleCache`, `state.notesCache`

## Endpoints

- `GET /groups`, `POST /groups`, `DELETE /groups/{id}`
- `GET /groups/{id}/members`, `DELETE /groups/{id}/members/me`
- `POST /groups/{id}/invite`, `POST /groups/{id}/invite/accept`
- `POST /groups/{id}/invitations`
- `GET /groups/{id}/notes`, `POST /groups/{id}/notes`,
  `PATCH /groups/{id}/notes/{noteId}`, `DELETE /groups/{id}/notes/{noteId}`
- `GET /groups/{id}/settle`, `POST /groups/{id}/settle`,
  `POST /groups/{id}/settle/me`, `POST /groups/{id}/payments`
- `POST /groups/{id}/payouts/{payoutId}/{paid|settled|remind|failed|cancel|escalate}`,
  `POST /groups/{id}/payouts/{payoutId}/expenses`

## Files to touch for groups bugs

1. `views/groups/groups.html`
2. `js/features/groups.js`
3. `js/features/invitations.js`
4. `js/features/settlement.js`
5. `js/features/expenses.js`
6. `css/app.css` (Groups, Members + expenses, Group notes, Settlement, Corpus fund)