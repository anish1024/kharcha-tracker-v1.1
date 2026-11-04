# AGENT.md — Kharcha Tracker feature: settings

## Module

- `js/features/settings.js`

## Exports

- `mount()`, `onEnter()`

## DOM IDs used

- `#user-name`, `#user-email`, `#user-avatar`
- `#form-profile` (no submit handler — do not invent)
- `#stat-plan` — subscription summary

## State touched

- `state.currentUser`

## Endpoints

- `GET /users/me/subscription` (summary only)

## CSS sections

- Settings, Plans

## Files to touch for settings bugs

1. `views/settings/settings.html`
2. `js/features/settings.js`
3. `js/features/subscription.js` (#stat-plan update)
4. `css/app.css` (Settings, Plans)

## Notes

- Per spec §6.19: do not invent a profile-update API / submit handler.
- Keep profile editing behaviour as a separate product issue.