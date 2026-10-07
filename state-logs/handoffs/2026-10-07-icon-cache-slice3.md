# Icon cache slice 3: monochrome usability decision (2026-10-07)

Baseline: HEAD `d9a60f2` (slice 2 committed). Scope: `AppSymbolSourceResolver` and its new helper only.

## Completed
- `MonochromeUsability` (internal, `:platform:launcherapps`): rasterizes a monochrome layer at 48 px and accepts it only if 1.5%..95% of pixels have alpha >= 16 (rejects blank, speck and solid-fill layers). `isUsableCoverage` is pure and unit-tested.
- `AppSymbolSourceResolver`: SDK 33+ returns `Monochrome` only when the layer exists and passes the check; otherwise `AdaptiveForeground`. The testable seam takes an explicit `isUsableMonochrome`.
- `BoundedLauncherAppIconPngEncoder.ENCODER_VERSION` bumped 1 -> 2 so apps whose icon source changes with this rule are re-encoded once. Slice 4 must bump to 3.

## Validation
- `./gradlew test`, `assembleDebug`, `lint`: PASS.
- New resolver tests: unusable mono falls back to foreground; usability judged on the mono layer; coverage thresholds; faint pixels ignored.

## Not verified
- Rendering of the real rasterized check on device (thresholds are judgement values, not tuned against real icons); which installed apps fall back.

## Worktree
Slice 3 changes uncommitted and unstaged.

## Next action
Slice 4: derive a colour-mask for foreground/legacy sources and bump `ENCODER_VERSION` to 3; update `docs/grid.md` §6/§7 and `docs/design.md`. Exit: unit tests for the derivation and a device look at flattened icons.
