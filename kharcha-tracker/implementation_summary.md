**v1.2.0 is complete — all six enhancements plus the Docs phase.** Final state:

## Results

| Phase | Feature | Tests | Suite |
|---|---|---|---|
| E1 | Invite cards show the group name (server-resolved `invitation.group_name`) | +1 | 241 |
| E2 | Default "Personal Expenses" INR group (idempotent at signup + `GET /groups`, re-created after deletion) | +3 | 244 |
| E3 | Expenses screen pre-selects the last-used group (localStorage, first-group fallback) | — | 244 |
| E4 | Stacked bar chart; stable category→color shared with the pie (`colorFor`) | — | 244 |
| E5 | Group notes: CRUD + audited (`entity_type` `group_note`) + group-card UI | +16 | 260 |
| E6 | Corpus fund: `CORPUS` category deposits, read-only derived panel | +7 | **267** |
| Docs | README + `release_notes.md` v1.2.0 | — | **267** |

## Final validation
- **267 passed**, 156 warnings (expected Pydantic/Starlette deprecations)
- `node --check` clean on `app.js` + `charts.js`
- Blueprint: 138 nodes, no dangling refs; root mirror `cmp` byte-identical

## Docs phase details
- `kharcha-tracker/README.md` — API routes (4 note endpoints + `GET /groups` ensure annotation), views list, 5 new/updated feature-note bullets (invite group name, default group, notes, last-used group, corpus fund, stacked chart), filesystem trees gained the E5 `group_note` model/schema/service files (order matching the blueprint), corrected the stale `main.py` import block, §7 implemented-services list, and the `DEFAULT_GROUP_CURRENCY` constant mention.
- `kharcha-tracker/release_notes.md` — **v1.2.0 section at the top** (above v1.1.0) covering E1–E6; Bugs: None.
- `kharcha-tracker/implementation_log.md` — Docs-phase entry appended (Goal/Actions/Result).

## E6 recap (the phase you last saw in flight)
- `CORPUS` is a selectable expense category (10th enum member) — deposits are split across members; validation was enum-set driven so no service change was needed.
- The corpus balance in the settlement panel is **derived frontend-side** from the existing `GET /groups/{id}/settle` preview: **SETTLED orphan payouts + positive net balances** (group-owes-member = overpaid; orphans never enter `net_dues`, so the terms are disjoint). No new tables, no new endpoints, display-only.
- One documented pre-existing engine quirk (unchanged, out of scope): a group holding a *payor-recorded (PAID) unlinked* ad-hoc orphan fails the preview's accounting invariant — noted in the `test_corpus.py` header.

Everything is logged, the suite is green, and the repo is ready for a commit whenever you want it.
