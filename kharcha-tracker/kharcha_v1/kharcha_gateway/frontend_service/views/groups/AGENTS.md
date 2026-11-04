# AGENT.md — Kharcha Tracker view: groups

## View file

- `views/groups/groups.html`

## Root element

- `<section id="view-groups" class="view">`

## Dominant DOM IDs

- `#btn-new-group`, `#btn-new-group-icon` — create-group form toggle
- `#form-create-group`, `#new-group-name`, `#new-group-currency`,
  `#create-group-error`, `#btn-cancel-create-group` — create form
- `#btn-join-group`, `#join-icon` — join-group form toggle
- `#form-join-group`, `#join-group-id`, `#join-code`,
  `#join-error`, `#join-submit` — join form
- `#btn-invite-email`, `#invite-icon` — invite form toggle
- `#form-invite-email`, `#invite-group`, `#invite-email`,
  `#invite-error`, `#invite-submit` — email invite form
- `#groups-list` — delegated click/submit container for group cards
- `#invites-list` — invitation cards list

## Owning feature modules

- `js/features/groups.js` — group cards, CRUD, notes, mount
- `js/features/invitations.js` — invitation cards, accept/decline, mount
- `js/features/settlement.js` — settlement panel, payouts, ad-hoc, corpus
- `js/features/expenses.js` — `expensesListHtml()` shared with groups
- `js/features/notifications.js` — refreshes after invite accept/decline

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
- `GET /users/me/invitations`, `POST /invitations/{id}/accept`,
  `POST /invitations/{id}/decline`

## CSS sections

- Groups, Members + expenses, Group notes, Settlement, Corpus fund

## Files to touch for groups bugs

1. `views/groups/groups.html`
2. `js/features/groups.js`
3. `js/features/invitations.js`
4. `js/features/settlement.js`
5. `js/features/expenses.js` (shared `expensesListHtml`)
6. `css/app.css` (Groups, Members + expenses, Settlement, Group notes, Corpus fund)