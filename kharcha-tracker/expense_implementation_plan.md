# Expense / Settlement Implementation Plan — Matrix-Based Settlement Engine

**Status: APPROVAL PENDING.** No settlement-engine code will be written until
this plan is approved. The three fixes that preceded it (invite message format,
group member list, add-expense UI) are already implemented and tested; this
document covers only what remains — the settlement engine and its wiring.

Authoritative source for everything below: `kharcha-tracker.yaml` (nodes
`settlement_engine` and its children, DTOs `payout` / `settlement_round` /
`row_payout` / `matrix_row_key` / `settlement_preview`, enums
`payout_status` / `settlement_kind` / `settlement_round_status` /
`settlement_error_code` / `notification_kind`, constants
`system_config`). Where this plan restates the YAML it is summarising, never
extending; the closed-schema rule in `AGENTS.md` applies throughout.

---

## 1. The model in one picture

A group's balances live in ONE matrix, derived from two tables:

```
                 members →
expense  ↓      u1      u2      u3      u4
row A        [ +30.00   -10.00  -10.00  -10.00 ]   u1 paid, three others split
row B        [   0      -40.00  +20.00  +20.00 ]   u2 paid; u1 not involved (0.00)
row S (payout) [ -10.00  +10.00   0       0    ]   SETTLED payout row (u2 → u1)
```

- **Rows** = one per expense (`trans_id`) plus one extra row per SETTLED,
  linked payout.
- **Columns** = every current group member; every member appears in every row.
- **Cell** = `paid_by_user_total − share_amount`, where `share_amount =
  (expn_splt[user].percentage / 100.0) × expn_amnt` (shares given in
  percentages). For an expense, a user may have split share `s_i` and
  multiple payers (`paid_by` list): `paid_total_i` is summed across payers
  if needed, but the canonical cell is `p_i − s_i` with `s_i = (pct_i/100)*A`.
  **< 0** = that user owes, **> 0** = that user overpaid (is owed),
  **= 0** = not involved — and a `0.00` cell never produces a payout
  instruction. Default split: if the split is not provided, it is
  **EQUAL** among all participating members of the group at the time of
  normalisation.
- A settled payout row clears the debt: **payor +amt**, **payee −amt**.
- Nothing is ever written to the expense table by settlement — PRD 4.2.6
  "settlement expense record creation" is retired. Payments are derived from
  the payout table only.

All money math is in **integer minor units** (`10 ** DECIMAL_PRECISION`,
i.e. paise/cents) internally; floats appear only at the DTO boundary. Share
allocation uses **largest-remainder** so every row sums to exactly 0.

## 2. Row payouts → one payout per pair (consolidation)

Per matrix row, debtors pay the creditors of *that row only*:
`generate_row_payouts` matches the largest debtor to the largest creditor
(ties broken by `user_id`), producing exact, deterministic instructions —
at most `debtors + creditors − 1` per row.

`consolidate_payouts` then nets all row-level instructions for each
**unordered user pair** (A→B and B→A cancel) and subtracts the pair's
already-open (PENDING/PAID, linked) payouts — the **commitment rule**:

```
outstanding(pair) = pair net from the matrix − open payouts already issued
```

so re-running a settlement never bills anyone twice; a run with nothing new
is a no-op. Each emitted payout carries `expense_ids` = every expense that
contributed to the pair's balance in either direction. The planner rejects
any proposal without expense ids (that would look like an orphan ad-hoc
payment and be re-issued).

## 3. Settlement kinds (each persisted as a `settlement_round`)

| Kind | Trigger | Group status effect |
|---|---|---|
| `INDIVIDUAL` | `settle_user` — one user clears their dues, any time | Group stays OPEN or LOCKED |
| `PERIODIC` | `initiate_settlement(kind=PERIODIC)` — e.g. month end | Status unchanged; new round only if something is outstanding |
| `FINAL` | `initiate_settlement(kind=FINAL)` | Requires LOCKED; → `PARTIAL_SETTLEMENT`; → `SETTLED` (closed for good) once every payout is SETTLED and all net dues are 0 |

`FINAL` refuses (before any write) while orphan ad-hoc payments exist.
Re-running `FINAL` during `PARTIAL_SETTLEMENT` re-issues FAILED/CANCELLED
amounts into the active FINAL round (`replaces_payout_ids`). A group with
nothing owed goes straight to `SETTLED`.

## 4. Payout lifecycle

```
PENDING ──payor: mark_payout_paid(evidence?)──► PAID     notify payee
PENDING ──payee: settle_payout (cash/direct)──► SETTLED  notify payor
PAID    ──payee: settle_payout (received)─────► SETTLED  notify payor
PAID    ──payee: fail_payout (not received)───► FAILED   notify payor
PAID    ──payor: fail_payout (transfer bounce)► FAILED   notify payee
PENDING ──payor|payee: cancel_payout(reason)──► CANCELLED notify other
```

- `SETTLED` is final. `FAILED` and `CANCELLED` are final **for that record**
  but never forgive the debt — the next run re-issues the amount, with the
  replaced ids in `replaces_payout_ids`. A reason is required for both.
- Evidence (bank reference / screenshot URL) is optional; payor and payee
  each have their own slot.
- `remind_payout`: PENDING → payee reminds payor; PAID → payor reminds
  payee. Notification only, no state change.
- `escalate_payout`: any member, once a PAID payout is older than
  `PAYOUT_ESCALATION_DAYS` (7) since `paid_at`; notifies every member.
  Nothing is ever auto-settled.

## 5. Ad-hoc payments (`record_adhoc_payment`, `link_payout_expenses`)

Recorded outside a settlement run (`is_adhoc = true`, no round). Actor must
be payor or payee; both must be members; `amt > 0`.

- Recorded **by payor** → `PAID` (payee reviews); **by payee** → `SETTLED`.
- Without `expense_ids` the payment is an **orphan**: no effect on balances.
- `link_payout_expenses` adds links (ad-hoc payouts in PAID/SETTLED only);
  every expense must exist in the group and involve **both** users (split or
  payer). Linking may close the group during FINAL. Orphans must be zero
  before FINAL may proceed.

## 6. Architecture (all in one file)

`kharcha_v1/kharcha_gateway/kharcha_service/settlement_engine.py` — module
node, `layout: file`. Pure computation is separated from I/O:

```
_group_state(storage, group)                     shared loader (private)
  _list_group_records(expense_record, payout_record)
  → build_expense_matrix → apply_settled_payouts
  → validate_matrix_balance → calculate_net_dues

_propose(matrix, net_dues, open_payouts, all_payouts, group_id)   pure planner
  generate_row_payouts (each row) → consolidate_payouts
  → _link_replacements → _assert_payouts_reproduce_net_dues
```

| Public export | Role |
|---|---|
| `preview_settlement` | `_group_state` → `_propose`; writes nothing; narrows to `user_id` if given; includes `orphan_payouts`. Backs `GET /groups/{id}/settle`. |
| `settle_user` | `_group_state` → `_propose` → keep user's payouts → `create_settlement_round(INDIVIDUAL)` → notify both sides. Idempotent: nothing new → no round. |
| `initiate_settlement` | PERIODIC or FINAL as in §3. Returns every open payout of the group. |
| `mark_payout_paid` / `settle_payout` / `fail_payout` / `cancel_payout` | `_load_payout` (group open, role, state) → save → `_complete_rounds` → notify → `_maybe_finalize`. |
| `remind_payout` / `escalate_payout` | Notification-only paths (§4). |
| `record_adhoc_payment` / `link_payout_expenses` | `_validate_links` → save → notify → `_maybe_finalize` (§5). |
| `list_payouts` / `list_settlement_rounds` | Read models (all rounds/payouts of a group; payouts optionally narrowed to a user). |

Private helpers (`_group_state`, `_propose`, `_maybe_finalize`,
`_load_payout`, `_complete_rounds`, `_validate_links`, `_link_replacements`,
`_assert_payouts_reproduce_net_dues`) live only in this file and are
documented here, not in the YAML. Internal dataclasses `RowPayout` and
`MatrixRowKey` / `SettlementPreview` follow the DTO nodes (frozen,
hashable; never persisted).

**Invariants the engine asserts before any write:**

1. Every matrix row sums to exactly 0 in minor units
   (`validate_matrix_balance`; `ACCOUNTING` error names the offending
   `trans_id`).
2. Proposed + open payouts reproduce the net dues exactly
   (`_assert_payouts_reproduce_net_dues`).
3. Matrix-wide column sums (net dues) reconcile with FLOAT_EPSILON tolerance
   only at display boundaries — internally minor units are exact.

## 7. Errors

The engine raises exactly one exception type:
`settlement_engine.SettlementError(code, message)`. Codes from
`constants/settlement_error_code.py`; HTTP status mapping lives in `main.py`
(`_SETTLEMENT_ERROR_STATUS`), not in the engine:

| Code | HTTP |
|---|---|
| `GROUP_NOT_FOUND` | 404 |
| `PAYOUT_NOT_FOUND` | 404 |
| `INVALID_REQUEST` | 422 |
| `ACCOUNTING` | 422 |
| `PAYOUT_PERMISSION` | 403 |
| `GROUP_STATE` | 409 |
| `GROUP_NOT_LOCKED` | 409 |
| `PAYOUT_STATE` | 409 |
| `FINAL_SETTLEMENT` | 409 |

## 8. Persistence & DTOs (already present, no schema change)

- Tables: `payout_record`, `settlement_round_record` (registered in
  `conftest.py` and `main.py`'s `_FLAT_FILE_SCHEMA_REGISTRY`).
- DTOs in `models/`: `Payout`, `SettlementRound`, plus internal frozen
  dataclasses `RowPayout`, `MatrixRowKey`, `SettlementPreview` (per their
  YAML nodes — files exist as stubs).
- Enums: `PayoutStatus`, `SettlementKind`, `SettlementRoundStatus`,
  `SettlementErrorCode`, `NotificationKind` (the nine payout/settlement
  notification kinds already declared: `PAYOUT_ISSUED`, `PAYOUT_PAID`,
  `PAYOUT_SETTLED`, `PAYOUT_FAILED`, `PAYOUT_CANCELLED`, `PAYOUT_REMINDER`,
  `PAYOUT_ESCALATED`, `ADHOC_PAYMENT`, `GROUP_SETTLED`).
- Notifications go through the existing `notification_service`; no new
  kinds, no YAML edits.

## 9. API wiring (main.py stubs → real handlers)

The 15 settlement routes already exist as stubs; implementation replaces
their bodies and adds the error mapping:

```
GET  /groups/{id}/settle                     preview_settlement
POST /groups/{id}/settle                     initiate_settlement   {kind}
POST /groups/{id}/settle/me                  settle_user            {include_receivables}
GET  /groups/{id}/settlement-rounds          list_settlement_rounds
GET  /groups/{id}/payouts?user_id=           list_payouts
POST /groups/{id}/payouts/{pid}/paid         mark_payout_paid       {evidence?}
POST /groups/{id}/payouts/{pid}/settled      settle_payout          {evidence?}
POST /groups/{id}/payouts/{pid}/failed       fail_payout            {reason, evidence?}
POST /groups/{id}/payouts/{pid}/cancel       cancel_payout          {reason}
POST /groups/{id}/payouts/{pid}/remind       remind_payout
POST /groups/{id}/payouts/{pid}/escalate     escalate_payout
POST /groups/{id}/payouts/{pid}/expenses     link_payout_expenses   {expense_ids}
POST /groups/{id}/payments                   record_adhoc_payment   {payor_id, payee_id, amt, expense_ids?, evidence?}
GET  /users/me/payouts                       (across all groups)
```

## 10. Frontend (after backend is green)

- **Group card → “Settle up”**: opens a settle panel per group showing the
  preview (net dues per member, open payouts, proposed payouts, orphans)
  with buttons: *Settle my dues* (`/settle/me`), *Start periodic settle*,
  and (owner, when LOCKED) *Final settle*.
- **Payout rows**: status pill, payor → payee, amount, linked expense ids;
  actions per lifecycle (mark paid / confirm / fail+reason / cancel+reason /
  remind / escalate), driven entirely by payout status + current user's role.
- **Ad-hoc payment form**: payor, payee, amount, optional expense multi-select
  (expenses both users took part in).
- No new JS files — extend `app.js` render + delegated handlers, styles in
  `app.css`, matching the patterns used for the expense form.

## 11. Test plan

New `test_settlement_engine.py` (unit, pure-function heavy):

1. **Matrix**: single expense EQUAL split → row sums 0; largest-remainder
   rounding (e.g. 100 across 3 → 33.34/33.33/33.33); non-member / bad
   percentage / paid-mismatch → `ACCOUNTING`.
2. **Row payouts**: 1 debtor × 2 creditors → 2 instructions; 2×2 → exact
   match; zero cells absent; determinism under user_id tie-break.
3. **Consolidation**: A→B and B→A netted; open PENDING payout subtracted
   (commitment rule); FAILED/CANCELLED ignored → re-issued with
   `replaces_payout_ids`; expense_ids cover both directions.
4. **Lifecycle**: full state machine incl. illegal transitions →
   `PAYOUT_STATE`; wrong role → `PAYOUT_PERMISSION`; reason required.
5. **Ad-hoc**: orphan has no matrix effect until linked; link validation;
   FINAL refused while orphans exist.
6. **FINAL**: LOCKED required; PAID-but-unsettled payout blocks close;
   `_maybe_finalize` completes rounds and sets group SETTLED; re-run
   re-issues FAILED amounts.
7. **Idempotence**: second identical run creates no round/payouts.

API-level tests in `test_settlement_api.py` mirroring the route table,
using the existing `client`/`storage` fixtures. Target: suite grows from
**153** to roughly **195–210**, still green.

## 12. Phases (each logged to `implementation_log.md`)

| Phase | Scope | Gate |
|---|---|---|
| S1 | DTO/enum files promoted from stubs + pure helpers (`build_expense_matrix`, `validate_matrix_balance`, `calculate_net_dues`, `generate_row_payouts`, `consolidate_payouts`, `apply_settled_payouts`) | unit tests green |
| S2 | Loader + planner (`_group_state`, `_propose`, invariants) + `preview_settlement` + read APIs | preview e2e green |
| S3 | `create_settlement_round`, `settle_user`, `initiate_settlement` (PERIODIC/FINAL), `finalize_settlement`, notifications | round e2e green |
| S4 | Payout lifecycle (`mark_payout_paid` … `cancel_payout`), remind/escalate, `_maybe_finalize` | lifecycle e2e green |
| S5 | Ad-hoc payments + linking | ad-hoc e2e green |
| S6 | main.py handlers + `_SETTLEMENT_ERROR_STATUS` + full API tests | suite green |
| S7 | Frontend settle panel + payout actions | live uvicorn smoke |

## 13. Out of scope / explicit non-goals

- No YAML schema changes (closed schema; the engine fits the existing nodes).
- No writes to the expense table from settlement (retired FR-4.2.6).
- No auto-settlement, no background jobs, no email — in-app notifications only.
- `sql_lite` / `postgres` storage adapters remain stubs; FlatFileStorage only.
- Currency conversion — one currency per group, assumed.

---

**Approval requested.** Reply with approval (or amendments) and Phase S1
begins; progress is appended to `implementation_log.md` after each phase.
