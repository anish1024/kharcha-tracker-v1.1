# AGENT.md — Kharcha Tracker feature: subscription

## Module

- `js/features/subscription.js`

## Exports

- `mount()`, `onEnter()`
- `loadSubscription()`, `refreshPlanStat()`, `refreshSettingsPlan()`
- `bindSubscriptionActions()`, `updatePlanStat()`

## DOM IDs used

- `#stat-plan` — shared between dashboard and subscription
- Plan card elements in `views/subscription/subscription.html`
- `#btn-cancel-current-plan`, `#btn-switch-plan-*` — plan switch

## State touched

- `state.currentUser`
- `state.dashboard.groupId` (data flow between dashboard/subscription)

## Endpoints

- `GET /users/me/subscription`, `POST /subscription`

## CSS sections

- Plans, Cards

## Files to touch for subscription bugs

1. `views/subscription/subscription.html`
2. `js/features/subscription.js`
3. `js/features/settings.js` (#stat-plan sync)
4. `css/app.css` (Plans section)

## Notes

- `#stat-plan` updated both from dashboard and subscription modules.
- Plan purchase buttons exist only in Subscription view.