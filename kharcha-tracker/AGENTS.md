# AGENTS.md — how to read and act on `graph.yaml`

This file is for an agent (human or AI) that has been handed a `graph.yaml`
and asked to generate, extend, or modify the codebase it describes. It is
not a tutorial on the editor UI — it's the contract the YAML itself
promises, so you can act on a file directly, without the editor open.

If you only read one section, read **0 (the schema is closed)**, **6
(type descriptors)**, **8 (layout)**, and **10 (what to generate for a
given node)** — those four determine whether what you produce is even
structurally valid.

---

## 0. The schema is closed — never invent a field, value, or protocol

**This document is the complete list.** Every top-level key (§2), every
node-level key (§4), every `interface.*` key per protocol (§3/§5), every
enum value (`status` §10, `layout` §8, `boundary`/`model_kind`/type-
descriptor kinds throughout), and every protocol (§3) that exists in this
schema is named somewhere in this file. If something you need isn't
named here, **it doesn't exist** — don't add it anyway.

**Why this matters more than it might seem to:** nothing validating this
file rejects an unrecognized key. There is no "reject any field I don't
recognize" check — at the top level, on a node, or inside `interface`.
A field you invent, a typo'd protocol name, a made-up enum value — none
of it gets caught. It will sit in the YAML silently, forever,
indistinguishable from something that was actually designed in.
**A clean validation result is not confirmation that everything in the
file is legitimate — it only confirms the file doesn't violate a rule
that was written down.** Treat the absence of a validation error as
"didn't break anything checked," not as "this is correct."

### If the schema can't express what you need

Stop. Don't:
- add a new top-level or `interface.*` field to work around it,
- repurpose an existing field for something other than its documented
  meaning (e.g. stuffing structured data into `notes`, or giving
  `layout` a value that isn't one of the four in §8),
- invent a new enum value, protocol, or relationship type,
- or silently attach your own out-of-band metadata block to nodes.

Instead, name the concrete thing you can't express and ask the user
whether to extend the schema for it. This is exactly how every real
extension to this schema got made — `union` (a type descriptor kind
that didn't exist until a concrete case needed it), `gateway`/`service`
(two new protocols), and `skip` (a new `layout` value) were each
proposed as a specific, scoped change and approved before this
specification changed to include it, never added quietly mid-task.

**A cautionary real example**: a top-level `implementation` block
(generation metadata — status, path, imports, etc.) has shown up on
nodes in this project from a run outside this process, without ever
being proposed or documented. It turned out to be harmless — pure
read-only bookkeeping nothing else depends on — but that was luck, not
a guarantee, and it was only caught by someone
manually diffing files and asking about it. Don't be the source of the
next one of these. If you genuinely need to persist your own metadata
about what you did, say so and propose it explicitly rather than just
writing it.

---

## 1. What this file is

`graph.yaml` is a typed DAG blueprint of a codebase. Every unit of the
system — a package, a function, a DTO, an interface, a class — is one
**node**. A node's `parent` field is the *only* containment relationship;
everything else (`dependencies`, `calls`, `maps_to`, `implements`,
`data_flows`) is a separate, non-structural edge with its own meaning (see
§9). The graph is read top-down to derive a real directory/file layout
(§8), and read node-by-node to produce one generation prompt per
generatable node (§10).

Treat the YAML as the single source of truth. Don't infer structure from
file-system conventions you'd normally assume (e.g. "every package has an
`__init__.py`") unless a node explicitly says so.

---

---

## 2. Top-level file shape

```yaml
project: <string>            # metadata only -- names the saved .yaml file; NOT used in generated paths (see sec. 8)
backbone: <node id>          # the ONE required root node -- must be protocol: entrypoint
default_boundary: module     # fallback for any node's unset `boundary` (see §9)
default_language: python     # fallback when no service/module ancestor declares `language`
protocol_registry:           # OPTIONAL -- custom (non-built-in) protocol definitions, if any exist
  <custom protocol id>:
    label: <string>
    color: <hex>
    allowedChildren: [...]
    fields: [...]
    leafCapable: <bool>
    language: <string>       # required for a custom protocol -- see §3
nodes:
  - id: ...
    ...
```

`backbone` must name a node whose `interface.protocol` is `entrypoint`, and
that node's `parent` must be `null`. There is exactly one entrypoint per
file — this is a hard structural assumption, not a convention.

---

## 3. The protocol tree

Every node has exactly one `interface.protocol`. Which protocol a node can
have as a parent, and which protocols it can itself parent, is fixed:

```
entrypoint   (exactly one; parent: null; purely structural, generates nothing)
  └─ gateway            (communication/transport boundary; also generates nothing)
       └─ service        (an independently deployable unit)
            └─ module | function
                 module  ──┬─ function
                            ├─ module          (nesting allowed)
                            ├─ data-model
                            ├─ contract
                            └─ class ── function
```

| Protocol | Can parent | Leaf-capable (generates its own code)? | Notes |
|---|---|---|---|
| `entrypoint` | `gateway` | No | The file's one root. No `interface` fields. |
| `gateway` | `service` | No | `interface.transport` (free text: `http`, `grpc`, `queue`, ...). Never becomes a real path segment (see §8). |
| `service` | `function`, `module` | No | `interface.language` — the highest point a language can be declared; cascades to everything below it. An independently deployable unit; becomes a real top-level folder. |
| `module` | `function`, `module`, `data-model`, `contract`, `class` | No | `interface.exports`, `.language` (overrides an ancestor service's), `.layout` (`file` or `folder`, see §8). |
| `function` | *(nothing — leaf)* | **Yes** | `interface.params`, `.returns`, `.http_method`+`.route` (both or neither), `.errors`: `[{code, description}]`, `.preconditions`/`.postconditions`: `[string]`. `.layout` is `file` or `function` (§8). |
| `data-model` | *(nothing — leaf)* | **Yes** | `interface.model_kind` (`dto`/`db_schema`/`enum`/`constant`) + kind-specific fields (§5). Always `layout: file`. |
| `contract` | *(nothing)* | No (but skeleton-eligible — see §10) | `interface.methods`: `[{name, params, returns}]`. Always `layout: file`. A pure interface declaration — no function children of its own. |
| `class` | `function` | No (but skeleton-eligible) | `interface.fields`: `[{name, type}]` (its own instance state). Always `layout: file`. |
| `browser-js` | *(nothing — leaf)* | **Yes** | `interface.exports`, `.stub_behavior`. Frontend surface; fixed `language: javascript`. Always `layout: file`. |

A node whose protocol isn't in this table (a custom, non-built-in
protocol) still has to declare `language` on registration, and its
`allowedChildren`/`layouts` come from wherever it was registered
(`protocol_registry` block in the YAML, if present).

---

## 4. Every node's top-level fields

These exist on every node regardless of protocol:

| Field | Meaning |
|---|---|
| `id` | Identity. Becomes the filename / import path / class name. |
| `title` | Display label only. Falls back to `id` if blank. |
| `status` | See §10 — drives whether/how you should generate anything for this node. |
| `boundary` | `module` \| `service` \| `null` (→ inherits `default_boundary`). Relevant only on a dependency edge's *source* node when that edge crosses languages — see §9. |
| `parent` | The one containment field. `null` only on the entrypoint. |
| `interface.protocol` | Fixed at node creation. Selects every other `interface.*` rule in §3/§5. |
| `interface.layout` | `file` \| `folder` \| `function` \| `skip`. See §8 — this determines the real path, and it's never optional. |
| `contract_version` | Defaults `"0.0.0"`. Informational; feeds the generation guardrail text only. |
| `notes` | Free text — intent, context, anything a human filled in. Read this before generating; it's often the most reliable spec. |
| `stub_behavior` | Free text, implementation guidance specifically for you (the generating agent). On `function`, `module`, `class`, `browser-js`. |
| `tests` | `[{id, description, given, expect | expect_raises}]`. Cases the implementation is expected to satisfy. On `function`, `module`, `class`, `browser-js`. |
| `prompt_template` | Optional override of the generation prompt template for this one node. Only meaningful on the 6 protocols that actually generate something: `function`, `module`, `class`, `browser-js`, `data-model`, `contract`. |
| `prompt` | The last-generated prompt text, if any. Not authoritative — regenerate rather than trust a stale value if you're unsure. |
| `dependencies` | `[]` by default on every node. Only `module`/`class` ever hold real entries — see §9. |
| `calls` | `[]` by default on every node. Any node may hold entries — see §9. |
| `data_flows` | `[]` by default on every node. Any node may hold entries — see §9. |
| `maps_to` | Initialized to `[]` only on `data-model` nodes at creation; other protocols don't get the key unless something put it there, but an empty `[]` is harmless on any protocol. Only `data-model` may hold real (non-empty) entries — see §9. |
| `implements` | Initialized to `[]` only on `class` nodes at creation; same tolerance as `maps_to` above. Only `class` may hold real entries — see §9. |

---

## 5. `data-model` — the four `model_kind` shapes

```yaml
interface:
  protocol: data-model
  model_kind: dto | db_schema | enum | constant
  layout: file
  # then, depending on model_kind:
  model_fields: [{name, type: <descriptor>, required?: bool, notes?}]   # dto / db_schema
  enum_type: string | int                                               # enum
  enum_values: [{name, value, notes?}]                                   # enum
  constants: [{name, type, value, scope, notes?}]                        # constant
```

`dto` and `db_schema` are structurally identical (both use `model_fields`)
— the distinction is purely semantic (is this a wire-format shape or a
storage-schema shape). Generate accordingly (e.g. a `db_schema` likely
wants ORM/ODM annotations a `dto` wouldn't).

---

## 6. Type descriptors — the only legal shapes

Every `type:` value — in `params`/`returns`, `model_fields`, `contract.methods`,
`class.fields` — is **one of exactly these**, recursively:

| Form | Meaning |
|---|---|
| `{type: <primitive>}` | one of `boolean`, `datetime`, `decimal`, `float`, `integer`, `none` (returns only), `string`, `uuid` |
| `{type: <node id>}` | a reference to a real `data-model`/`enum`/etc. node — resolve it as that node's shape |
| `{type: list, items: <descriptor>}` | homogeneous list. `items` is **required**, always — a bare `{type: list}` is invalid |
| `{type: map, key: <descriptor>, value: <descriptor>}` | both `key` and `value` **required** |
| `{type: optional, of: <descriptor>}` | may be absent/null |
| `{type: union, items: [<descriptor>, <descriptor>, ...]}` | "one of several whole shapes" — **2+ options required**, each a full descriptor in its own right |

**There is no `any`/`object`/generic type.** Every value has to resolve to
a concrete primitive or node id somewhere in its nesting. If you're
tempted to write `any`, you need `union` instead (list every concrete
alternative), or a real DTO node if the shape is actually fixed.

Nesting order changes meaning with `union` — read it carefully:
- `{type: union, items: [{type:list, items:{type:string}}, {type:list, items:{type:integer}}]}`
  → the **whole value** is EITHER a list of strings OR a list of
  integers (pick one list type wholesale).
- `{type: list, items: {type: union, items: [{type:string}, {type:integer}]}}`
  → **one list** whose individual entries can each independently be a
  string or an integer.

---

## 7. `contract`, `class`, `implements` — interfaces done right

A `contract` node declares a method signature set with no implementation:

```yaml
interface:
  protocol: contract
  methods:
    - name: save_record
      params: [{name: record, type: {type: base_record}}]
      returns: {type: base_record}
```

A `class` node fulfills one via top-level (**not** `interface`-nested)
`implements`:

```yaml
id: postgres
interface: {protocol: class, fields: [{name: connection_string, type: {type: string}}]}
implements:
  - node: storage_interface
```

Only `class` nodes may hold `implements`, and the target must be a
`contract` node — both enforced, not just conventional.

---

## 8. Layout — what actually becomes a file vs a directory

`interface.layout` is required on every node, never blank, and the legal
values are protocol-dependent:

| Protocol | Legal `layout` values |
|---|---|
| `entrypoint`, `gateway`, `service` | `folder` or `skip` (default `folder`) |
| `module` | `file`, `folder`, or `skip` (default `folder`) |
| `function` | `file` or `function` |
| `data-model`, `contract`, `class`, `browser-js` | `file` only |

**`folder`** → the node is a real directory; non-function children each
get their own nested path inside it.

**`file`** → the node is one file. **Every direct child must be a
`function`** (enforced) — a `file`-layout node can never have a nested
`module`/`class`/`data-model`/`contract` child.

**`function`** (function nodes only) → generates **no file of its own**.
Its code lives inside its parent's file. Requires the parent to be
`layout: file`. The opposite, a function with `layout: file`, requires its
parent to be anything other than `file` (`folder` or `skip`) — and gets
its own separate file.

**`skip`** (`entrypoint`/`gateway`/`service`/`module` only) → purely
organizational. The node is "logical, not physical": it contributes **no
path segment of its own**, but the walk continues straight through it to
whatever's above. Its own children are completely unaffected — they're
still walked and placed normally, just one level higher than they'd
otherwise land. Opt-in and rare — the directory structure mirrors the
yaml's node tree **exactly** (every ancestor is a real path segment)
unless a node is explicitly marked `skip`.

### Resolving a node's real path

The rule is deliberately simple: **the directory structure is the yaml's
node tree, 1:1.** No implicit root prefix, no hardcoded exceptions for any
particular protocol.

1. A `function` with `layout: function`: **its path IS its parent's
   path** — recurse on the parent instead.
2. Otherwise: walk `parent` all the way up to the true root (the
   `entrypoint`, whose own `parent` is `null`), including the entrypoint's
   own id as the outermost segment, and **skipping only a node whose own
   `layout` is explicitly `"skip"`** — join whatever's left:
   `<chain...>/<node id><language extension>`.

The top-level `project` field is **not** used in path construction at
all — it's metadata about the file (used only to name the saved `.yaml`
itself), not a node in the tree, so including it would violate "exactly
reflects the yaml structure." The root folder on disk is the entrypoint
node's own `id`.

So: `kharcha_v1/kharcha_gateway/kharcha_service/settlement_engine/preview_settlement.py`
— every ancestor appears, including the entrypoint (`kharcha_v1`) and the
gateway (`kharcha_gateway`), because nothing is implicitly invisible
anymore. If you want a node to NOT appear (e.g. a gateway you consider
purely organizational), set its `layout` to `skip` explicitly.

A node's effective **language** (which decides the extension and which
generator conventions apply) is resolved by walking the same `parent`
chain looking for the nearest `module` or `service` ancestor that
declares `interface.language`, falling back to the file's
`default_language`. `browser-js` is always `javascript` regardless.

---

## 9. Relationship fields — who can hold what, and what each means

These are siblings of `interface`, not nested inside it (except
`contract.methods`, which is structural, not a relationship).

Each list entry is written as `{node: <id>}` (`calls`/`data_flows` add
their own extra keys on top, per the table below). A bare string id is
also *accepted* when reading an existing entry, but the tooling itself
always *writes* the object form — prefer `{node: <id>}` in anything you
generate.

| Field | Meaning | Who can hold it (source) | Valid targets |
|---|---|---|---|
| `dependencies` | "This node's code imports/references that node's unit" — a real import, never containment | `module`, `class` only | `module`, `data-model`, `contract` |
| `maps_to` | "This shape maps to that shape" (e.g. a DB row → a DTO) — a type mapping, not a call | `data-model` only | any node |
| `implements` | "This class fulfills this contract's methods" | `class` only | `contract` only |
| `calls` | One named operation call: `{node, operation_name}`. Doesn't require a matching `dependencies` entry | any node | any node |
| `data_flows` | A data shape moving between nodes: `{node, data_model, direction}`, `direction` ∈ `read`/`write` | any node | any node |

**A `function` never holds its own `dependencies`.** It inherits whatever
its nearest `module`/`class` ancestor declares. If you're generating a
function's body and need to know what it can import, look at its
*container's* `dependencies`, not the function node itself. If two
functions in the same file call each other, that's `calls`, not
`dependencies` — they're already in the same generated file, there's
nothing to import.

**Cross-service/cross-language edges**: if a `dependencies` edge's source
and target resolve to different effective languages, the source node's
`boundary` (or the file's `default_boundary`) must be `service` — a
same-process call is impossible across languages. Same-language
dependencies across different `service` subtrees need no such flag; they're
treated as an ordinary shared-package import regardless of which service
each side lives in.

---

## 10. What to generate for a given node

### Status drives everything

```
unsplit → contract_defined → stub → implementing → implemented → tested → integrated
```

- **`stub`** → you should **CREATE**. The file/function doesn't exist yet.
- **`implementing`** → you should **UPDATE**. A previous pass already
  created it; this is a targeted, scoped change.
- Any other status → not a generation target. Leave it alone.

### Whether a node generates anything at all

- **Leaf-capable** (`function`, `data-model`, `browser-js` — real,
  standalone code) → **implementation** prompt kind: produce the actual
  working content.
- **Skeleton-eligible** (`contract` always; `class` always; `module` only
  when `layout: file`) → **skeleton** prompt kind: produce imports, class/
  module wiring, and **empty function stubs** for every function child
  that shares this file (`layout: function`) — do **not** implement
  bodies. Each stubbed function gets implemented later, in its own
  separate pass.
- Everything else (`entrypoint`, `gateway`, `service`, a `layout: folder`
  module) → generates nothing itself. It's structural only.

### The guardrail — don't clobber a shared file

A function's generated prompt tells you explicitly whether its file is
shared with its container/siblings. If it is: **the file may already
exist** (from a skeleton pass or a sibling's own pass) — open it and add
or replace **only this function's own body**. Do not regenerate the whole
file, and do not touch any other function already in it. Implement
sibling functions in a shared file **one at a time**, never in parallel
against the same file.

---

## 11. A pre-flight checklist

Before treating any generation as final, a graph should satisfy all of:

1. The `backbone` node exists.
2. No dangling `parent` references.
3. No dangling `dependencies`/`maps_to`/`implements`/`calls`/`data_flows` references.
4. `implements` targets are actually `contract` nodes.
5. Every type descriptor (func params/returns, contract methods, class
   fields, model_fields) resolves per §6 — no `any`, no bare `list`
   without `items`, union options each individually valid.
6. `layout` is set on every node, is a value that protocol allows, a
   `file`-layout node's children are all functions, and a function's
   layout fits its parent (§8).
7. `http_method` and `route` are set together or not at all.
8. `model_fields`/`enum_values`/`constants` have no duplicate names.
9. `dependencies`/`maps_to`/`implements` are only held by the protocols
   listed in §9, and resolve to a valid target protocol.
10. A node claiming `status: tested` or `integrated` actually has
    `tests` entries — a status with no backing tests is a sign of a
    stale/hand-edited field.
11. Cross-language `dependencies` edges declare `boundary: service` (§9).

If you're generating code programmatically, re-run this checklist after
every batch of edits — don't assume a graph you were handed was already
clean.

---

## 12. Quick reference — minimal valid node skeleton

```yaml
- id: my_function
  title: ''
  status: stub
  boundary: null
  parent: my_module
  interface:
    protocol: function
    layout: function          # or 'file' if it needs its own file
    params: [{name: x, type: {type: string}}]
    returns: {type: boolean}
  contract_version: 0.0.0
  dependencies: []            # functions can't hold these -- leave empty
  calls: []
  data_flows: []
  stub_behavior: ''
  tests: []
  notes: ''
  prompt_template: ''
  prompt: ''
```

Every field shown is expected to be present, even when empty — don't omit
keys just because their value is `[]` or `''`.
