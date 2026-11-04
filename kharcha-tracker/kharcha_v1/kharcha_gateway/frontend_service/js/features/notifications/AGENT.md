# AGENT.md — Kharcha Tracker feature: notifications

## Module

- `js/features/notifications.js`

## Exports

- `mount()` — shell bell/persistent bindings
- `loadNotifications()`, `renderNotifications()`
- `toggleNotifPanel()`, `markNotificationsRead()`
- `bindShellNotifications()` — bell + outside-click handlers

## DOM IDs used

- `#btn-bell`, `#notif-panel`, `#notif-list`, `#notif-mark-read` (shell)

## State touched

- `state.currentUser`

## Endpoints

- `GET /users/me/notifications`, `POST /users/me/notifications/read`

## CSS sections

- App shell, Notification panel

## Files to touch for notifications bugs

1. `index.html` (shell: bell, panel)
2. `js/features/notifications.js`
3. `js/app.js` (bindShellNotifications from boot)
4. `css/app.css` (Shell / navigation)

## Notes

- Persistent shell — not a route view
- Bell panel opened/closed via `#btn-bell`
- Mark-all-read handler on `#notif-mark-read`
- Outside-click handler dismisses panel