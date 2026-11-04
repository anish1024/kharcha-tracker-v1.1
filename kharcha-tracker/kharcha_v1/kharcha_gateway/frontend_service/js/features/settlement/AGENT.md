# AGENT.md — Kharcha Tracker feature: settlement

## Module

- `js/features/settlement.js`

## Exports

- `bindSettlementActions(reloadCb)` — delegated on `#groups-list`
- `mount()` — called from app.js boot

## DOM IDs used

- `.settle-panel` (per group, `data-settle-for`)
- Settlement preview rows, payout rows, ad-hoc payment forms
- Corpus fund panel

## State touched

- `state.settleCache`, `state.expensesCache`, `state.membersCache`

## Endpoints

- `GET /groups/{id}/settle`, `POST /groups/{id}/settle`,
  `POST /groups/{id}/settle/me`, `POST /groups/{id}/payments`
- `POST /groups/{id}/payouts/{payoutId}/{paid|settled|remind|failed|cancel|escalate}`,
  `POST /groups/{id}/payouts/{payoutId}/expenses`

## CSS sections

- Settlement, Corpus fund, Members + expenses

## Files to touch for settlement bugs

1. `views/groups/groups.html` (settlement rendered into group cards)
2. `js/features/settlement.js`
3. `js/features/groups.js` (calls bindSettlementActions)
4. `css/app.css`

## Notes

- One-way import: groups.js imports settlement.js (no cycles)
- Rendered dynamically into group cards, not a static view