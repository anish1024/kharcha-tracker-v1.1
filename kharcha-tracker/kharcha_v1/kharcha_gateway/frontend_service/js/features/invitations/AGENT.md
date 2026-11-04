# AGENT.md — Kharcha Tracker feature: invitations

## Module

- `js/features/invitations.js`

## Exports

- `mount()` — binds `#invites-list` delegated actions
- `loadInvitations()`, `refreshInviteCount()` (both async)

## DOM IDs used

- `#invites-list` — invitation cards list

## State touched

- `state.groupsCache` (group count refresh), `state.currentUser`

## Endpoints

- `GET /users/me/invitations`
- `POST /invitations/{id}/accept`, `POST /invitations/{id}/decline`
- `POST /groups/{id}/invitations`, `POST /groups/{id}/invite/accept`

## Notes

- Invitations are a section within Groups, not a route.
- Mounted from `app.js` after groups view is mounted.

## Files to touch for invitations bugs

1. `views/groups/groups.html` (`#invites-list` section)
2. `js/features/invitations.js`
3. `js/features/groups.js` (refreshGroupCount shared)
4. `js/features/notifications.js` (refresh after accept/decline)
5. `css/app.css` (Groups, Cards)
