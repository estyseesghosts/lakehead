# D2 — Home placement repository operations

Status: completed — repository contract and operations implemented; focused tests, required builds/tests/lint, and repository audits passed.  
Phase: D / 1C persisted Home  
Owner: new `:data:repository` module

Completion evidence: repository tests 9 passed; database tests 8 passed; grid tests 36 passed; repository lint, `assembleDebug`, root `test` (131 tests, 0 failures), root `lint`, and all four repository audits passed. Module graph confirms `:data:repository` depends on `:core:model`, `:core:grid`, and `:data:database`. See the [D2 closeout handoff](../../../state-logs/handoffs/2026-09-24-d2-repository-operations-complete.md) for the recorded commands, device installation evidence, and remaining scope. D3 presentation behavior and D4+ remain open.

## Objective and ownership

Implement the smallest domain-facing repository that loads and edits persisted app placements for an explicitly identified Home. Add `:data:repository` when implementing this slice, with inward dependencies on `:core:model`, `:core:grid`, and `:data:database` only. `:data:database` continues to own Room entities, DAOs, schema/versioning, migrations, and Room/domain mapping. The repository owns the operation boundary, transactional coordination, and conversion between persisted `AppPlacement` records and grid inputs; it must not duplicate grid policy.

The contract is framework-free. It accepts domain identifiers and logical cell values, never Room/SQLite types, Android services or framework types, Compose types, pixels, or UI state. No repository implementation or interface belongs in `:data:database` or `:app`.

## Public operation contract

Expose a small domain-facing contract with these operations and arguments:

```text
load(homeId, bounds)
place(homeId, appIdentity, position, span, bounds, replacingId? = null)
move(homeId, itemId, position, bounds)
resize(homeId, itemId, span, bounds)
remove(homeId, itemId)
```

Use existing framework-free `HomeId`, `ItemId`, `AppIdentity`, `LogicalPosition`, and `LogicalSpan` concepts from `:core:model`; use finite grid bounds represented by the existing grid columns/rows inputs or an equivalently small framework-free bounds value. The caller supplies positive, finite `columns` and `rows` on every operation that needs placement legality; the repository does not infer viewport bounds, adapt columns, or choose a Home.

Results must be explicit and framework-free:

- `load` returns the selected Home's placements (including their persisted availability) or an explicit `NotFound`/error when `homeId` does not exist. It must never create a Home as a side effect.
- `place`, `move`, and `resize` must preserve and expose the corresponding `GridRules.Result`/`GridRules.Rejection` unchanged for legality outcomes. Represent accepted, rejected, and lookup/ownership failures in the contract without converting a grid rejection into success or silently dropping its reason. The caller can distinguish `NotFound` (unknown Home or target) and `WrongHome` (the globally identified item exists but belongs to another Home) from grid rejection.
- `remove` reports whether removal succeeded and distinguishes an unknown Home/target from an item owned by another Home; it does not perform a grid operation.

Do not add Flow, observable streams, callbacks, Home selection, catalogue observation, or a general-purpose query API in D2.

## Home and identity invariants

- Every operation is scoped to its supplied `HomeId`. There is no implicit Home creation, default Home, or active-Home selection.
- An `ItemId` is globally unique as established by D1. The same `ItemId` cannot move, be replaced, or otherwise migrate across Homes. A request naming the wrong Home must fail without changing either Home.
- The same full `AppIdentity` (including `ProfileId` and complete component identity) may appear in different Homes, but each placement uses a distinct `ItemId`. Do not deduplicate placements by app identity or lose profile identity.
- A successful replace is one atomic operation against the same Home: pass `replacingId` to `GridRules.place`, persist the new candidate using the returned accepted grid result, and remove/replace the old placement in the same database transaction. It is not a transfer or migration between Homes. Missing/cross-Home `replacingId`, duplicate candidate ID, or any grid rejection writes nothing.
- New app placements are `AVAILABLE` initially; later catalogue/availability reconciliation is outside D2. Existing `UNAVAILABLE` records remain retained, with the same `ItemId`, full `AppIdentity`, position and span, unless the caller explicitly edits/removes/replaces that item. Loading never filters unavailable placements.
- Ordinary place, move, resize, and remove preserve all unrelated placements and their logical coordinates/spans/availability. They never compact, reorder, or rewrite unrelated rows. Keep a stable loaded ordering; replacement occupies the replaced item's position in that ordering, consistent with `GridRules.place`.

## Legality and atomicity

- `:core:grid` is the sole owner of supported spans, finite bounds checks, collisions, and placement/move/resize legality. Map all items for the requested Home to `GridPlacement<ItemId>` and call `GridRules.place`, `GridRules.move`, or `GridRules.resize` with the caller's bounds. Do not reproduce checks in repository code or database queries.
- Validate Home/item ownership before changing persisted state. For a found Home and target item, compute the grid result before any write. On `GridRules.Result.Rejected`, return the exact rejection and perform zero writes.
- For accepted placement edits, commit all records affected by one operation in a single database transaction. Grid evaluation and commit must not permit stale placement state to bypass collision checks; perform read/validation/write under the same transaction or otherwise guarantee equivalent serialization.
- `place` without replacement, `move`, `resize`, `remove`, and `place` with replacement are each atomic: either their intended operation commits completely or persisted state remains unchanged. An exception/failure during writes must roll back.
- Preserve existing Home and app-reference ownership/integrity. Removing/replacing a placement must not delete a Home or shared app-reference row. Do not auto-create a Home or app-reference as a workaround for a failed request; establish any required app-reference and placement rows atomically using D1 persistence primitives.

## Scope exclusions

This slice is repository contract and operations only. Explicitly defer:

- D3 viewport-aware Home rendering and presentation state.
- D4 move UI/gesture editing and D5 resize UI editing.
- D6 remove/reset UI behavior, catalogue synchronization, and availability refresh policy.
- Folder placement/membership, shortcuts, widgets, built-ins, and tile configuration (`TileAppearance`, `ConfiguredShortcut` slots per `docs/grid.md` — explicit future migration, no D2 contract change).
- Grid legality generalization to the open span set (B1 follow-up; D2 keeps passing the grid result through unchanged).
- Home creation, Home selection, transfer/migration between Homes, or adding more Home behavior.
- Flow/observation and app-catalogue synchronization.

## Acceptance criteria

- `:data:repository` depends inward only on `:core:model`, `:core:grid`, and `:data:database`; database mapping and Room ownership remain in `:data:database`.
- The public contract is framework-free and exposes exactly the bounded load/place/move/resize/remove operations above with explicit outcomes.
- Load is isolated by `HomeId`, stable in order, includes unavailable placements, and returns explicit `NotFound` for an unknown Home without creating it.
- All edits delegate legality to `:core:grid`; the same rejection reason is returned and no persisted row changes on rejection.
- Accepted edits preserve unrelated placement rows/order and leave holes; remove affects only the requested placement in the requested Home.
- Replacement removes/replaces only its same-Home target and commits atomically; failed validation or forced write failure leaves the prior Home unchanged.
- Unknown Home, unknown item, and wrong-Home item requests fail explicitly with no writes.
- The same profile-aware app identity can have distinct `ItemId`s in separate Homes; a single `ItemId` cannot cross Homes.
- No implicit Home creation/selection, catalogue sync, observation/Flow, or later feature is implemented or claimed complete.

## Required focused tests

Use repository-level tests against the real D1 database/DAO implementation where practical (Robolectric/in-memory Room is already used by D1); add a narrow fake only for contract-specific isolation where a real database cannot provide a meaningful assertion.

1. **Load isolation and unknown Home:** seed at least two Homes with distinct placements, load each with finite bounds, and prove only its own records are returned in stable order, including `UNAVAILABLE`; load an unknown ID and prove `NotFound` and no Home/placement count change.
2. **Place accepted/rejected:** accepted placement persists the expected full `AppIdentity`, logical position/span and availability. For unsupported span, out-of-bounds, collision, and invalid/unknown replacement target as applicable, assert the exact grid rejection/error and snapshot every relevant Home/app-reference/placement row before and after to prove zero writes.
3. **Move accepted/rejected:** accepted move changes only the target position. Collision/out-of-bounds (and missing target) preserve the full database snapshot and unrelated ordering/holes.
4. **Resize accepted/rejected:** accepted resize changes only target span. Unsupported span, collision, and out-of-bounds preserve the full database snapshot.
5. **Remove isolation:** remove an item and prove only that placement is removed; retain its Home and app reference and retain every row in other Homes. Unknown target/Home and wrong-Home request make no changes.
6. **Atomic replacement:** successful same-Home replacement uses `replacingId`, retains unrelated rows/order, removes the old item and adds the requested new `ItemId`/identity. Verify missing target, cross-Home target, collision, and other grid rejection perform zero writes. Force a failure during the transaction and prove rollback restores the exact prior state.
7. **Home ownership and identity:** an item found under another Home yields explicit `WrongHome` with neither Home changed. The same full `AppIdentity` appears under distinct `ItemId`s in two Homes without deduplication; attempt to reuse one globally unique `ItemId` across Homes and prove it cannot migrate or overwrite.
8. **Unavailable retention:** loading and unrelated operations retain an unavailable placement unchanged; no catalogue call or availability rewrite occurs.
9. **Contract purity:** assert the public repository contract/domain result types contain no Android, Room/SQLite, Compose, or pixel/frame types, and inspect/verify the Gradle dependency direction. Keep grid policy tests in `:core:grid`; repository tests prove delegation and persisted outcomes rather than recopying the grid algorithm.

## Implementation validation plan

Run the focused tests first, including `./gradlew :data:repository:testDebugUnitTest` and relevant `:data:database` / `:core:grid` tests. Then run:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Record exact outcomes, distinguishing `NO-SOURCE` from tests that ran. Confirm the module graph shows `:data:repository → :core:model`, `:core:grid`, `:data:database`, with no outward dependency from data/database and no Room/Android types in the public contract. D2 has no device-only acceptance behavior.

## Exit gate

D2 is complete when the domain-facing repository can safely load and mutate explicitly selected Homes under caller-supplied finite bounds; grid legality is delegated; rejected or failed edits leave persistent state untouched; same-Home replacement is atomic; ownership/profile identity and unavailable-retention rules are tested; and the focused tests, required build/test/lint checks, and repository audits pass. D3 and all deferred behavior remain open.
