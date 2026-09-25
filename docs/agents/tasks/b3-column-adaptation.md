# B3 — Grid column-count adaptation

Status: amendment in progress  
Owner: `:core:grid`  
Phase: B / 1A grid engine

## Objective and boundary

Provide pure viewport-width-to-column-count adaptation without changing logical placements. The API answers only the column count; it does not perform placement projection, reflow, or coordinate rewriting.

## Contract

- `GridColumns` represents the adapted column count.
- Six columns is the normal-phone reference result and sweet spot, not a minimum. `GridViewportAdaptation.columnsFor` uses the documented positive-count formula:

  ```text
  max(1, floor((effectiveContentWidth + gap) / (minimumCellWidth + gap)))
  ```

- Narrow or split viewports may return fewer than six columns; wider viewports may return more than six, according to available width.

- Effective content width is viewport width minus combined horizontal inset and twice the horizontal padding:

  ```text
  effectiveContentWidth = viewportWidth - combinedHorizontalInset - 2 * padding
  ```

- Arithmetic is `Long`-safe and the result has no arbitrary maximum cap.
- This is a count-only API. It does not rewrite or adapt logical placement coordinates.
- The API remains pure and independent of Android, Compose, persistence, and tile kinds.

## Acceptance criteria — amendment in progress

- Produces six for normal-phone reference inputs while allowing fewer/more according to available width.
- Narrow and split viewport inputs can produce counts below six.
- The returned column count is always positive, including when available width is too small for one minimum-width cell.
- Insets and padding reduce effective width as specified.
- Long arithmetic avoids intermediate overflow, and no maximum column cap is imposed.
- Back-and-forth width adaptation leaves logical placement unchanged because the API returns only a count.
- Add focused narrow-count and positivity test cases for the amended behavior.
- Re-run focused `:core:grid` tests and lint, the function audit, and full validation after source and tests are updated.

## Evidence

See [`state-logs/handoffs/2026-09-24-b3-grid-complete.md`](../../../state-logs/handoffs/2026-09-24-b3-grid-complete.md) for the original implementation and cumulative Phase B validation evidence. That evidence predates this contract amendment and does not validate the amended narrow-count and positivity behavior.

No device work was performed or needed for this pure grid slice.
