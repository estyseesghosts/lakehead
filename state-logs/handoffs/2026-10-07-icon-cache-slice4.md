# Icon cache slice 4: colour-mask derivation (2026-10-07)

Baseline: HEAD `7148d51` (slice 3 committed).

## Completed
- `ColourMask` (internal, pure, `:platform:launcherapps`): transparent border -> icon's own alpha; opaque border -> per-pixel strength from the largest RGB difference to the mean border colour (ramp 12%..35%); near-empty result -> keep the opaque shape.
- `BoundedLauncherAppIconPngEncoder`: reduces every source to a white alpha mask before the bounding-box trim (monochrome: own alpha; foreground/legacy: `ColourMask`). `DrawableLauncherAppIcon` now carries `isMonochrome` from the resolved `AppSymbolSource`.
- `ENCODER_VERSION` 2 -> 3 (existing cached entries re-encode once).
- Docs: `docs/grid.md` §6 (usable monochrome) and §7 (alpha-mask pipeline, incremental cache); `docs/design.md` symbol pipeline bullet.

## Validation
- `./gradlew test`, `assembleDebug`, `lint`: PASS. `ColourMaskTest` (7 cases) PASS.
- `function_audit.py`: no flags in changed files. `module_graph.py`: no change expected (no dependency edits), not re-run.

## Not verified
- Device: how derived masks look for real icons (thresholds 12%/35% are untuned judgement values); tinted rendering in Home/All Apps; heap with 500+ icons; install/update re-encode; slice 1's >64-app label-only check.
- Gradients or multi-colour backdrops may leave faint residue; icons whose glyph colour is close to the backdrop lose detail.

## Worktree
Slice 4 committed after this handoff; the icon-cache series (slices 1-4) is complete.

## Next action
Install on R5CN70E2SGV and review All Apps/Home icons; tune `ColourMask` thresholds if icons look wrong. Exit: no solid-tile or blank icons across the installed set.
