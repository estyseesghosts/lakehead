# D1 — Logical placement schema

Status: completed  
Phase: D / 1C persisted Home  
Owner: `:core:model` for domain identifiers/references; `:data:database` for Room entities, DAO, and schema

## Objective and boundary

Define and implement the smallest persistence foundation for logical Home placements. The domain contract remains framework-free in `:core:model`; Room-specific storage belongs to `:data:database`. This slice establishes durable identifiers, records, schema integrity, and tested persistence behavior. It does not create repository/use-case operations; those belong to D2.

Use independently keyed `HomeId` terminology. A Home is its own vertical canvas; placements are scoped by `homeId`, not by a global `Page`/page-coordinate model. D1 does not implement Home selection or migration between Homes.

## Domain and record contract

- `HomeId` identifies an independent Home and is not a grid coordinate or global page number.
- `ItemId` is a stable, opaque identity for one placed item. It is separate from the referenced app identity: one app can have distinct placement items, including placements in separate Homes.
- `AppIdentity` remains the existing profile-aware identity composed from the full `ProfileId` and `ComponentIdentity`. `ComponentIdentity` retains its package and class identity. Neither persistence nor unavailable-app handling may replace, truncate, or infer away the profile component of this identity.
- D1 supports exactly one item type: `APP`. Shortcuts, folders, widgets, and built-ins are deferred; do not add speculative type records or extension fields for them.
- The persisted placement record contains `homeId`, `itemId`, item type, the referenced app identity, logical position, and logical span. Position/span must use `:core:grid`-equivalent primitive fields (zero-based column/row and column/row span dimensions); persistence stores logical values, not a grid legality policy.
- Preserve the existing framework-free grid concepts where compatible; do not make Room entities or Android/Compose types part of `:core:model`.
- An app placement has explicit availability `AVAILABLE` or `UNAVAILABLE`. If its installed app is absent, retain the same placement, `ItemId`, and full `AppIdentity` and report `UNAVAILABLE`; do not rewrite identity, delete the placement, or silently substitute another profile/component. Availability is state, not part of the app's identity.
- No pixels, `Drawable`, `UserHandle`, `PendingIntent`, `AppWidgetHostView`, Compose state, or other Android/Compose framework object is persisted.
- Define and enforce database referential integrity for the placement's Home and app reference. Invalid or orphan references must fail predictably rather than produce a partially valid placement.

## Ownership and dependency boundaries

- `:core:model` owns `HomeId`, `ItemId`, and framework-free domain references/placement concepts, including the profile-aware app reference.
- `:data:database` owns Room entities, DAO, database schema/version, migrations, and mapping to/from domain types. It depends inward on `:core:model`; domain code does not depend on Room.
- D1 provides only the persistence primitives needed to create/read/update records for tests and the next slice. No repository is introduced; repository operations (load Home, place, move, resize, remove, replacement) are D2.
- `:core:grid` remains the sole owner of placement legality, collision, movement, packing, and viewport adaptation. D1 stores logical coordinates and dimensions but does not validate legal spans, grid bounds, overlap, placement, or auto-compact records.
- UI and Home rendering are D3. D1 does not add presentation, Compose wiring, or app-catalogue observation.

## Schema and integrity contract

- Establish schema version 1 as an explicit Room schema, export/check its schema artifact, and test fresh database creation against the version-1 tables, columns, keys, and constraints.
- Use stable serialized primitive values for IDs and app identity components. The app reference must include both `ProfileId` and the complete `ComponentIdentity`; profile identity must be part of the uniqueness/reference key so the same component in two profiles remains two distinct app references.
- Enforce unique placement `ItemId` identity and valid required references. Database constraints and/or DAO transaction validation must reject duplicate IDs, missing referenced app records, and missing Homes. Choose the smallest schema representation that can enforce the contract without moving validation into a future UI.
- Add only explicit, data-preserving Room migrations when a schema change exists. Destructive fallback is prohibited. The initial D1 database has no legacy production database to migrate: prove fresh v1 creation now, and require a migration test from every supported prior exported schema whenever the version advances. Keep migration coverage as part of the schema contract rather than inventing a fictitious pre-v1 schema.
- Writes that establish/update mutually dependent Home, app-reference, and placement rows must be atomic: either all required rows and references commit, or none do.
- Persist enough information to retain an unavailable app placement without requiring the app to be currently installed. Availability refresh must not alter its stored app identity or placement.

## Acceptance criteria

- Domain IDs and app references are framework-free and keep `HomeId`, opaque `ItemId`, and profile-aware `AppIdentity` distinct.
- A fresh Room database creates the reviewed v1 schema, and schema export/versioning is deterministic.
- Round-trip persistence retains Home ID, item ID, `APP` type, complete profile + component identity, logical coordinates/spans, and availability exactly.
- Transaction atomicity is tested by forcing a failure between related writes and proving no partial rows remain.
- Invalid/missing references and duplicate identity constraints are tested and rejected predictably.
- The same component in different profiles round-trips as distinct references and placements; same-profile duplicate identity behavior follows explicit database uniqueness constraints without conflating `ItemId` with app identity.
- A missing installed app remains as an `UNAVAILABLE` placement with unchanged `ItemId`, `ProfileId`, and `ComponentIdentity`; it can become available again without identity replacement.
- Schema/mapping inspection and tests demonstrate no pixels or Android/Compose framework objects are persisted.
- No legality, collision, packing, repository, UI, folder, shortcut, widget, or built-in behavior is claimed complete.

## Required tests

- Fresh v1 schema creation and assertions for schema version, columns, keys, foreign keys/constraints, and exported schema agreement.
- Migration contract: no destructive migration path; register and test every supported explicit migration from v1 (and any future supported prior version) when schema advances. At D1's initial v1, verify creation and pin the migration policy without fabricating a nonexistent prior schema.
- Domain/entity round trip for all persisted fields.
- Transaction atomicity, including rollback after an injected/forced failure.
- Invalid/missing Home and app references, duplicate `ItemId`, and profile-aware key/reference integrity.
- Same `ComponentIdentity` under two different `ProfileId` values remains distinct through storage and readback.
- Stale/unavailable app retention and restoration to `AVAILABLE` without identity rewriting or placement deletion.
- A persistence boundary assertion/test that no pixel values or Android/Compose framework types enter the stored/domain placement contract.

## Explicitly deferred

- Placement legality, supported-span policy, collisions, movement, packing, vertical extent, and viewport adaptation — `:core:grid`.
- Repository operations and their public domain contract — D2.
- Home rendering, viewport behavior, and UI state — D3.
- Shortcuts, folders, widgets, built-ins, tile-specific configuration (`TileAppearance` color role, `ConfiguredShortcut` slots), and folder membership. Per `docs/grid.md`, these arrive via explicit migration in their owning slice, not speculative D1 fields.
- Whether tile configuration is per Home, folder scoping/membership semantics, profile removal/recreation identity semantics, and any Home migration/selection behavior. Do not resolve these by adding speculative schema fields in D1; raise them to the owning future slice.
- Catalogue synchronization or Android profile/framework objects in the database.

## Future implementation validation

Run focused D1 tests first, including the actual database module test task and any instrumentation/schema tests introduced by the implementation. Then run the complete required implementation validation:

```text
./gradlew :data:database:testDebugUnitTest
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

If a focused test uses an instrumentation task instead of (or in addition to) the unit-test task, document and run its exact task. Record `NO-SOURCE` distinctly from passing tests. Validate that the module graph keeps Room in `:data:database`, domain contracts in `:core:model`, and grid policy in `:core:grid`.

## Narrow exit gate

D1 is complete when the framework-free domain contract and Room v1 schema persist profile-aware `APP` placements with stable `HomeId`/`ItemId`, logical position/span, explicit available/unavailable retention, enforced referential integrity, tested atomicity/round trips, and a non-destructive migration policy; focused tests, all commands above, and ownership audits pass. No D2 repository behavior, D3 UI behavior, placement legality, or deferred semantics are implied by this gate.

## Completion evidence

- The D1 domain and Room schema/persistence acceptance criteria are complete.
- `:data:database:testDebugUnitTest` passed 8 tests with 0 failures; `:core:model:test` passed 11 tests with 0 failures.
- `:data:database:lint`, `./gradlew assembleDebug`, `./gradlew test` (122 tests, 0 failures), and `./gradlew lint` passed.
- `repo_map.py`, `module_graph.py`, `function_audit.py`, and `compose_audit.py` passed; exact invocations and provenance are recorded in [`2026-09-24-d1-logical-placement-schema-complete.md`](../../../state-logs/handoffs/2026-09-24-d1-logical-placement-schema-complete.md).
- No device or instrumentation behavior was tested. D2, D3, and later slices remain open; C1 manual accessibility/visual checks remain follow-up.
