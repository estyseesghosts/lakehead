# Widgets slices 5–11

## Completed
- Slice 5: sealed `HomePlacement` (`AppPlacement`, `WidgetPlacement`), `widget_bindings` table, DB v3 with `MIGRATION_2_3`, repository `placeWidget` / `persistedAppWidgetIds`.
- Slice 6/7: `HomeRenderTile.appWidgetId`, widget tiles projected and rendered through the `HomeWidgetContent` slot; `AppWidgetHosting` in `:platform:widgets` (host, bind, configure, long-press host view, size updates).
- Slice 8/9: `AddButton` (`:core:designsystem`) shown in edit mode; `WidgetPickerOverlay` in `:feature:home`; picker holds the pager.
- Slice 10: `AddWidgetCoordinator` (allocate, bind or consent, configure, place); pending id/provider saved in instance state; removing a widget tile deletes its host id.

## Touched paths
See `git show --stat` of the commit that carries this file.

## Validation
- `./gradlew test lint --continue`: passed (no failures).
- `./gradlew :app:assembleRelease`: see commit message / final report.
- Device (SM-G986W): long-press background → (+) → picker → configure activity → widget placed and live. Verified by screenshot.
- Not run: `repo_map`, `module_graph`, `function_audit`, `compose_audit`.

## Unverified / known gaps
- Placed "Weather Forecast 4×2" appeared about 1.5 cells wide; span stored was not inspected. Check default span vs. clamp.
- Move, resize, remove of a widget tile, restart persistence and uninstalled-provider placeholder not exercised on device.
- No unit tests for `AddWidgetCoordinator` yet.
- Startup sweep of orphaned host ids not implemented (`AppWidgetHost` has no public list API).
- Widgets and long-press-then-drag in one gesture not verified.

## Next action
Log the span in `AddWidgetCoordinator.place` on device and compare with the picker label; exit when a 4×2 provider lands as 4×2. Then write `AddWidgetCoordinatorTest`.
