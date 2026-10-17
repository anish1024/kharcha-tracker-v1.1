# Kharcha Tracker — Python / FastAPI Implementation

## 1. Purpose

This repository is a Python implementation of the language-agnostic
`kharcha_tracker.yaml` blueprint.

The YAML remains the authoritative application blueprint. This README captures
Python-specific implementation and operational details and does not redefine
the application contract.

## 2. Blueprint-to-Python Mapping

The current `kharcha_tracker.yaml` blueprint is the authoritative source for
the filesystem hierarchy. Parent relationships are used to derive paths;
logical nodes with `layout: function` are not turned into separate files.

The current blueprint root is `kharcha_v1`. There is no `kharcha_api` node in
this version of the blueprint.

### Complete YAML-defined filesystem structure

`# implemented` marks a YAML-defined file for which implementation code exists
in the current generated project. Files without the marker are defined by the
YAML but have not been implemented in the current generated set.

```text
kharcha_v1/
└── kharcha_gateway/
    ├── kharcha_service/
    │   ├── expense_service.py  # implemented (stub)
    │   ├── group_service.py  # implemented
    │   ├── settlement_engine.py  # implemented (stub)
    │   ├── notification_service.py  # implemented
    │   ├── subscription_service.py  # implemented
    │   ├── invitation_service.py  # implemented
    │   ├── storage_service/
    │   │   ├── schema/
    │   │   │   ├── base_record.py  # implemented
    │   │   │   ├── user_record.py  # implemented
    │   │   │   ├── group_record.py  # implemented
    │   │   │   ├── expense_record.py  # implemented
    │   │   │   ├── payout_record.py  # implemented
    │   │   │   ├── settlement_round_record.py  # implemented
    │   │   │   ├── notification_record.py  # implemented
    │   │   │   ├── invitation_record.py  # implemented
    │   │   │   └── audit_log.py  # implemented
    │   │   ├── storage_interface.py  # implemented (stub)
    │   │   ├── flat_file.py
    │   │   ├── sql_lite.py
    │   │   └── postgres.py
    │   ├── models/
    │   │   ├── base.py  # implemented
    │   │   ├── user.py  # implemented
    │   │   ├── group.py  # implemented
    │   │   ├── expense.py  # implemented
    │   │   ├── expense_update.py  # implemented
    │   │   ├── payout.py  # implemented
    │   │   ├── settlement_round.py  # implemented
    │   │   ├── notification.py  # implemented
    │   │   ├── invitation.py  # implemented
    │   │   ├── row_payout.py  # implemented
    │   │   ├── matrix_row_key.py  # implemented
    │   │   ├── settlement_preview.py  # implemented
    │   │   ├── expense_share.py  # implemented
    │   │   └── expense_payment.py  # implemented
    │   ├── auth_service.py  # implemented
    │   └── constants/
    │       ├── expense_category.py  # implemented
    │       ├── expense_status.py  # implemented
    │       ├── payout_status.py  # implemented
    │       ├── settlement_kind.py  # implemented
    │       ├── settlement_round_status.py  # implemented
    │       ├── notification_kind.py  # implemented
    │       ├── plan.py  # implemented
    │       ├── plan_status.py  # implemented
    │       ├── invitation_status.py  # implemented
    │       ├── settlement_error_code.py  # implemented
    │       ├── group_status.py  # implemented
    │       └── system_config.py  # implemented
    ├── frontend_service/  # implemented (vanilla SPA)
    │   ├── index.html
    │   ├── css/app.css
    │   └── js/
    │       ├── app.js
    │       ├── api.js
    │       └── icons.js
    └── app/
        └── main.py  # implemented
```

The extension shown above is derived from the YAML language inheritance: the
service/model/storage/constants/application nodes are Python, while
`frontend_service` is JavaScript. The tree intentionally does not add
implementation-only files such as `__init__.py`, because they are not YAML
nodes.

Generated project-level artifacts:

```text
kharcha_tracker.yaml  # canonical blueprint/regeneration manifest
requirement.txt       # Python runtime dependencies
README.md             # implementation documentation
```

Package-support `__init__.py` files are generated where required for reliable
Python package imports. They are implementation-support files and are not YAML
nodes.

The FastAPI entrypoint is:

```text
kharcha_v1/kharcha_gateway/app/main.py
```

Run target:

```text
kharcha_v1.kharcha_gateway.app.main:app
```

The YAML defines the `main` node as the Web API router and specifies the
`/api/v1` endpoint set.

## Constants implementation

The YAML-defined `constants` folder has now been implemented. It contains the
following generated modules:

```text
kharcha_v1/kharcha_gateway/kharcha_service/constants/
├── expense_category.py  # implemented
├── expense_status.py  # implemented
├── payout_status.py  # implemented
├── settlement_kind.py  # implemented
├── settlement_round_status.py  # implemented
├── notification_kind.py  # implemented
├── plan.py  # implemented
├── plan_status.py  # implemented
├── invitation_status.py  # implemented
├── settlement_error_code.py  # implemented
├── group_status.py  # implemented
└── system_config.py  # implemented
```

Enum members and constant values are generated strictly from `kharcha_tracker.yaml`.
No additional values are introduced by the implementation. The package
`__init__.py` re-exports the generated constants and is implementation support,
not a YAML filesystem node.

The `expense_category` implementation intentionally does not include `SETTLEMENT`:
the current YAML marks that category as retired and directs user-to-user payments
to the payout table.

`system_config.py` also carries the trial/subscription constants
(`TRIAL_PERIOD_DAYS`, `POST_TRIAL_ENTRY_LIMIT`) and the invitation lifetime
(`INVITATION_TTL_DAYS`). `auth_token_config.py` holds the access- and refresh-token
lifetimes.

## 3. Python Implementation Structure

The current generated implementation corresponds to the implemented entries
marked above. The domain models are located exactly under the YAML-defined
`models` folder; they are not defined in `main.py`.

### Domain model modules

```text
kharcha_v1/kharcha_gateway/kharcha_service/models/
├── base.py
├── user.py
├── group.py
├── expense.py
├── expense_update.py
├── expense_share.py
├── expense_payment.py
├── payout.py
├── settlement_round.py
├── notification.py
├── invitation.py
├── row_payout.py
├── matrix_row_key.py
└── settlement_preview.py
```

Each module corresponds to a `parent: models`, `model_kind: dto`,
`layout: file` node in `kharcha_tracker.yaml`. The fields and declared
types are derived from the YAML model definitions.

The YAML's `model_kind: dto` is metadata describing the model role; it does not
force a Python `DTO` class-name suffix. Python classes therefore use the node's
semantic name directly, such as `User`, `Expense`, and `SettlementRound`.

`main.py` imports the public models through:

```python
from ..kharcha_service.models import (
    Base,
    Expense,
    ExpenseUpdate,
    Group,
    Invitation,
    Notification,
    Payout,
    SettlementPreview,
    SettlementRound,
    User,
)
```

The `models/__init__.py` package export also exposes the other YAML-defined
models (`ExpenseShare`, `ExpensePayment`, `RowPayout`, and
`MatrixRowKey`) for service-layer use. `__init__.py` itself is an
implementation-support file and is not a YAML node.

### Storage schema model modules

The YAML separately defines data-model nodes under `storage_service/schema`. Their
current generated files are:

```text
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/
├── base_record.py
├── user_record.py
├── group_record.py
├── expense_record.py
├── payout_record.py
├── settlement_round_record.py
├── notification_record.py
├── invitation_record.py
└── audit_log.py
```

Where the YAML has `model_fields: []`, the implementation does not invent a
new schema contract; those fields remain derived from the referenced model as
stated by the blueprint.

## 4. Progressive YAML Back-Update / Regeneration Contract

The blueprint is progressively back-updated as implementation advances. Generated
filesystem nodes receive an `implementation` block in `kharcha_tracker.yaml`.
That block records the generated path, Python module path, imports, classes/functions,
and regeneration guidance.

Folder nodes are treated as module/package containers. Their implementation metadata
records the container path, module path, package-support status, and YAML-defined
children. Sub-folders follow the same rule recursively. This allows a deleted file
tree to be reconstructed from the YAML with empty stubs while preserving import
paths and dependency relationships.

The regeneration rule is:

1. Use `parent` hierarchy to reconstruct filesystem paths.
2. Use `layout` to distinguish folders, files, and logical functions.
3. Do not create filesystem files for `layout: function` nodes.
4. Use the node contract for fields, parameters, return types, and dependencies.
5. Use `implementation` metadata when present to restore imports, module paths,
   classes/functions, and package/container structure.
6. Restore unresolved business logic as empty or minimal stubs rather than inventing
   behavior not defined by the blueprint.
7. Back-update the node again whenever the corresponding implementation materially
   changes.

This makes `kharcha_tracker.yaml` both the application contract and the progressive
regeneration manifest.

## 5. FastAPI Entry Point

The application object is:

```python
app
```

in:

```text
kharcha_v1/kharcha_gateway/app/main.py
```

The API prefix is:

```text
/api/v1
```

## 6. Python Model Placement

Model definitions are kept in the files represented by the YAML data-model nodes. They
are not defined inside `main.py`.

### Domain models

```text
kharcha_v1/kharcha_gateway/kharcha_service/models/
├── base.py
├── user.py
├── group.py
├── expense.py
├── expense_update.py
├── expense_share.py
├── expense_payment.py
├── payout.py
├── settlement_round.py
├── notification.py
├── invitation.py
├── row_payout.py
├── matrix_row_key.py
└── settlement_preview.py
```

Each file corresponds to a `parent: models` data-model node in the YAML. The YAML
fields are the source of truth for the model fields.

### Storage schema models

The YAML also defines data-model nodes under `schema`, so their implementations are
kept in the corresponding files:

```text
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/
├── base_record.py
├── user_record.py
├── group_record.py
├── expense_record.py
├── payout_record.py
├── settlement_round_record.py
├── notification_record.py
├── invitation_record.py
└── audit_log.py
```

Where the YAML explicitly has `model_fields: []`, the implementation does not
invent additional fields; those fields remain derived from the referenced model
according to the blueprint.

`main.py` imports the domain models from `kharcha_service.models` and contains no
model class definitions. Its current stub accepts HTTP request bodies as JSON
dictionaries rather than introducing request-model classes into a YAML node that
is not designated as a model file.

## 7. Current Stub Behavior

`main.py` is a thin HTTP boundary. The following service areas are implemented
and wired to routes: authentication (signup/login, password reset, magic-link
SSO, refresh tokens), users, groups (including derived invite codes), expenses
(with trial/plan entry gating), notifications, subscriptions (mock plans,
trial + grace entries), and invitations.

Still stubbed: the settlement engine surface (`expense_service` settlement
helpers, settlement-round/payout money movement), the `sql_lite`/`postgres`
storage adapters, and any endpoint whose service call is not yet present.

Where an endpoint's corresponding service is not yet implemented, its handler
follows the original stub convention:

```text
object  -> {}
list    -> []
boolean -> False
string  -> ""
integer -> 0
```

For operations whose contract returns `none`, the method contains:

```python
pass
```

`main.py` remains a thin HTTP boundary: it parses the request, delegates to a
`kharcha_service` function, and maps domain errors to HTTP responses. Business
rules live in the service layer, not in the router.

## 8. API Routes

The implementation exposes the routes defined by the YAML:

```text
GET    /api/v1/health    → {"status": "ok"}

POST   /api/v1/auth/signup
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh                 → rotate the refresh token
POST   /api/v1/auth/magic-link              → request a magic-link (dev returns link)
POST   /api/v1/auth/magic-link/verify       → exchange token for a session
POST   /api/v1/auth/password-reset/request
POST   /api/v1/auth/password-reset/confirm

GET    /api/v1/users/me
PATCH  /api/v1/users/me
GET    /api/v1/users/me/subscription        → plan, trial/grace state, entries used
POST   /api/v1/subscription                 → activate a mock plan
GET    /api/v1/users/me/invitations         → invitations addressed to me

POST   /api/v1/groups
GET    /api/v1/groups
GET    /api/v1/groups/{group_id}
DELETE /api/v1/groups/{group_id}

POST   /api/v1/groups/{group_id}/invite
POST   /api/v1/groups/{group_id}/invite/accept
POST   /api/v1/groups/{group_id}/invitations         → invite a user by email
GET    /api/v1/groups/{group_id}/invitations         → list group invitations
POST   /api/v1/invitations/{invitation_id}/accept
POST   /api/v1/invitations/{invitation_id}/decline
POST   /api/v1/groups/{group_id}/members
DELETE /api/v1/groups/{group_id}/members/me
POST   /api/v1/groups/{group_id}/lock
POST   /api/v1/groups/{group_id}/unlock
GET    /api/v1/groups/{group_id}/activity

POST   /api/v1/expenses
GET    /api/v1/groups/{group_id}/expenses
GET    /api/v1/expenses/{trans_id}
PATCH  /api/v1/expenses/{trans_id}
DELETE /api/v1/expenses/{trans_id}

GET    /api/v1/groups/{group_id}/settle
POST   /api/v1/groups/{group_id}/settle
POST   /api/v1/groups/{group_id}/settle/me
GET    /api/v1/groups/{group_id}/settlement-rounds
GET    /api/v1/groups/{group_id}/payouts

POST   /api/v1/groups/{group_id}/payouts/{payout_id}/paid
POST   /api/v1/groups/{group_id}/payouts/{payout_id}/settled
POST   /api/v1/groups/{group_id}/payouts/{payout_id}/failed
POST   /api/v1/groups/{group_id}/payouts/{payout_id}/cancel
POST   /api/v1/groups/{group_id}/payouts/{payout_id}/remind
POST   /api/v1/groups/{group_id}/payouts/{payout_id}/escalate
POST   /api/v1/groups/{group_id}/payouts/{payout_id}/expenses

POST   /api/v1/groups/{group_id}/payments

GET    /api/v1/users/me/payouts
GET    /api/v1/users/me/notifications
POST   /api/v1/users/me/notifications/read
```

The browser SPA is served from the same FastAPI app via `StaticFiles` at `/`
(`kharcha_v1/kharcha_gateway/frontend_service/`), so the API routes above live
under `/api/v1` while the frontend is served at the site root.

### Frontend SPA

The frontend is framework-free HTML/CSS/JavaScript. Its YAML representation is
the `frontend_service` service with a `frontend_js` folder module hosting three
`browser-js` leaf nodes (`app_js`, `api_js`, `icons_js`). On disk it is:

```text
kharcha_v1/kharcha_gateway/frontend_service/
├── index.html      # single page + views
├── css/app.css     # responsive styling
└── js/
    ├── api.js      # fetch wrapper + token handling
    ├── icons.js    # inline SVG icon set
    └── app.js      # view routing, rendering, event wiring
```

Views: login/signup, dashboard, groups, group detail, settings, and
subscription. The top bar hosts an account menu and a notifications bell with
an unread badge.

### Feature notes

- **Sessions**: access + refresh JWTs. `api.js` transparently retries once via
  `POST /api/v1/auth/refresh` on a 401.
- **Magic-link SSO**: `POST /api/v1/auth/magic-link` returns the link/token in
  the response body in dev; `verify` auto-creates the account if needed.
- **Trial & subscription**: a one-year trial; after it, three grace entries are
  allowed before `POST /expenses` returns the subscription-required error. Plans
  and payments are mocked (`none`/`monthly`/`6month`/`yearly`/`lifetime`).
- **Invite codes**: group codes are derived deterministically from
  `(group_id, inviter_id)` and are not rotated; joining matches any current
  member's derived code via `POST /groups/{id}/invite/accept`.
- **Invitations**: inviting by email creates a pending `invitation_record`;
  the invitee accepts/declines, and the owner receives a `GROUP_INVITE_ACCEPTED`
  / `GROUP_INVITE_DECLINED` notification surfaced in the bell panel.

## 9. Prerequisites

Recommended Python version:

```text
Python 3.11+
```

Create the virtual environment from the project root.

### Windows

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
```

If PowerShell execution policy prevents activation:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.venv\Scripts\Activate.ps1
```

### Linux / macOS

```bash
python3 -m venv .venv
source .venv/bin/activate
```

Verify:

```bash
python --version
python -m pip --version
```

## 10. Install Dependencies

With the virtual environment activated:

```bash
python -m pip install --upgrade pip
python -m pip install -r requirement.txt
```

The generated `requirement.txt` contains:

```text
fastapi
uvicorn[standard]
pydantic
```

Then install with:

```bash
python -m pip install -r requirement.txt
```

## 11. Run in Development

From the project root:

```bash
uvicorn kharcha_v1.kharcha_gateway.app.main:app --reload
```

The API will normally be available at:

```text
http://127.0.0.1:8000
```

Interactive API documentation:

```text
http://127.0.0.1:8000/docs
```

Alternative OpenAPI documentation:

```text
http://127.0.0.1:8000/redoc
```

The blueprint specifies the storage selection environment variable:

```text
KHARCHA_STORAGE=flat_file|sql_lite|postgres
```

Example:

### Windows PowerShell

```powershell
$env:KHARCHA_STORAGE="flat_file"
uvicorn kharcha_v1.kharcha_gateway.app.main:app --reload
```

### Linux / macOS

```bash
export KHARCHA_STORAGE=flat_file
uvicorn kharcha_v1.kharcha_gateway.app.main:app --reload
```

The current HTTP stubs do not yet perform storage operations; the variable is
reserved for the storage implementation defined by the blueprint.

## 12. Run Without Reload

```bash
uvicorn kharcha_v1.kharcha_gateway.app.main:app
```

Bind to all interfaces:

```bash
uvicorn kharcha_v1.kharcha_gateway.app.main:app \
    --host 0.0.0.0 \
    --port 8000
```

## 13. Deployment

For a Linux deployment host:

```bash
git clone <repository>
cd kharcha-tracker

python3 -m venv .venv
source .venv/bin/activate

python -m pip install --upgrade pip
python -m pip install -r requirement.txt

export KHARCHA_STORAGE=flat_file
```

Start the service:

```bash
uvicorn kharcha_v1.kharcha_gateway.app.main:app \
    --host 0.0.0.0 \
    --port 8000
```

For a multi-worker deployment:

```bash
uvicorn kharcha_v1.kharcha_gateway.app.main:app \
    --host 0.0.0.0 \
    --port 8000 \
    --workers 2
```

A reverse proxy such as Nginx or a cloud load balancer can be placed in
front of the Uvicorn process for TLS termination and external traffic
management.

The storage implementation must be selected and configured consistently with
the deployment environment:

```text
KHARCHA_STORAGE=flat_file
KHARCHA_STORAGE=sql_lite
KHARCHA_STORAGE=postgres
```

## 14. Production Environment

Do not run the development server with:

```text
--reload
```

in production.

At minimum, configure:

```text
KHARCHA_STORAGE
```

and keep secrets such as authentication credentials and database credentials
outside source code.

The blueprint itself does not define a specific secrets manager, reverse
proxy, container platform, process supervisor, or cloud provider. Those are
deployment decisions outside the YAML contract.

## 15. Implementation Sequence

The intended implementation order is:

1. Complete the domain models/models.
2. Complete the storage interface.
3. Implement the selected storage adapters.
4. Implement authentication services (including refresh tokens and magic-link SSO).
5. Implement group services (including derived invite codes).
6. Implement expense services (including trial/plan entry gating).
7. Implement notification services.
8. Implement subscription/plan services.
9. Implement invitation services.
10. Implement the settlement engine.
11. Replace the `main.py` stubs with service calls.
12. Add authentication and group-membership enforcement.
13. Add YAML-defined error mapping.
14. Build the vanilla HTML/CSS/JS frontend SPA.
15. Add automated API/service tests.
16. Deploy behind a production HTTP entrypoint.

`main.py` should remain a thin HTTP boundary as the service implementations
are added.

Status: steps 1–10 and 12–15 have an initial implementation; the settlement
engine and the `sql_lite`/`postgres` adapters remain stubbed (see §7). The
frontend SPA is served by the same FastAPI process.

## 16. YAML / README Boundary

The YAML is both the application contract and the progressive regeneration manifest.
As implementation progresses, each generated filesystem node is back-updated with
implementation metadata describing its generated path, module/package path, imports,
classes/functions, child containers, and regeneration guidance. Folder nodes are treated
as module/package containers and record their generated children and package support files.
This metadata is intended to allow the corresponding file tree to be reconstructed with
empty function/class stubs while preserving import paths and declared dependencies.

The following belong in the YAML:

- application hierarchy
- nodes and parent relationships
- interfaces
- contracts
- function parameters and return types
- dependencies
- API endpoint definitions
- business/service contract notes
- progressive implementation/regeneration metadata for generated nodes

The following belong in this README or Python source:

- Python package support files such as `__init__.py` that are not YAML nodes
- Python-specific implementation details
- FastAPI/Uvicorn commands
- virtual-environment setup
- dependency installation
- deployment commands

The YAML filename used by generated ZIPs is:

```text
kharcha_tracker.yaml
```

The implementation must not silently invent a YAML node, filesystem path, class,
function, or dependency. If a required detail is absent from the blueprint, it should
remain a stub or be explicitly identified as missing rather than inferred.

## 17. Source of Truth

This implementation is based on the canonical generated blueprint:

```text
kharcha_tracker.yaml
```

The same YAML file inside each generated ZIP is the source of truth and the
regeneration manifest for the implementation state captured in that ZIP.

The current blueprint root is:

```text
kharcha_v1
```

There is no `kharcha_api` node in the current blueprint.
