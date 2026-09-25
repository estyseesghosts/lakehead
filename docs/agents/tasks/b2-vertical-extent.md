# B2 — Grid vertical extent and stable ordering

Status: complete  
Owner: `:core:grid`  
Phase: B / 1A grid engine

## Objective and boundary

Measure the vertical extent needed by a logical grid from its placements, working margin, and viewport. This is pure framework-free grid logic. It does not pack, move, reorder, validate legality, or impose a product-specific maximum row count.

## Contract

- `GridVerticalExtent` represents the exclusive occupied bottom boundary and the measured scrollable extent.
- `GridVerticalLayout.measure` computes the exclusive occupied bottom from placement row plus span height, adds the requested working margin, and takes the maximum of that result and the viewport row count.
- The calculation is `Long`-safe and does not impose an arbitrary maximum. Overflow is reported rather than silently wrapping.
- Measurement preserves caller placement input and order; it does not rewrite placement coordinates.
- The API remains independent of Android, Compose, persistence, and tile kinds.

## Acceptance criteria — satisfied

- Distant occupied rows and large sparse gaps produce the correct exclusive bottom.
- The working margin and viewport floor are both included correctly, including empty and multi-row-span cases.
- No arbitrary maximum row is imposed; arithmetic does not silently overflow.
- Repeated measurements are deterministic and input order/content remain unchanged.
- Eight focused tests pass.
- Focused `:core:grid` test and lint, plus the function audit, pass.
- Full validation passes: `assembleDebug`, `test` with 65 XML unit tests and zero failures, `lint` with zero errors and 31 warnings, and all four repository audits.

## Evidence

See [`state-logs/handoffs/2026-09-24-b2-grid-vertical-extent.md`](../../../state-logs/handoffs/2026-09-24-b2-grid-vertical-extent.md) for the B2 implementation record. Final cumulative Phase B validation is recorded in [`state-logs/handoffs/2026-09-24-b3-grid-complete.md`](../../../state-logs/handoffs/2026-09-24-b3-grid-complete.md).

No device work was performed or needed for this pure grid slice.
