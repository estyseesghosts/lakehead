# ProtoLaunch (`me.foxtails.lakehead`)

Real Android HOME launcher in Kotlin + Compose. `AGENTS.md` is the authoritative workflow; this file is the short version. Read `AGENTS.md`, `state-logs/CURRENT.md` and the handoff it links before coding. Source and tests decide what is implemented; docs (`docs/architecture.md`, `design.md`, `platform-capabilities.md`, `first_draft.md` roadmap) may be stale; verify before trusting.

## Session start

1. Read `AGENTS.md`, `state-logs/CURRENT.md`, the linked handoff, and the relevant spec in `docs/agents/tasks/`.
2. `git status --short` and inspect diffs; treat unfamiliar edits as user/concurrent work.
3. Run `python3 tools/scripts/repo_map.py` (and `module_graph.py` when touching module boundaries). Use `trace_symbol.py <symbol>` or grep for callers.
4. State one bounded objective, owning module, acceptance conditions and verification before editing. Start a state-log entry for multi-step work.

## Phase gates

Order: 0A foundation (done) -> 0B launcher platform -> 1A grid -> 1B design primitives -> 1C minimal persisted home -> 1D folders. Finish one slice's exit gate before the next. Do not start Message Center, search, widgets or grouped All Apps early. Check the state logs and recent commits for the real current slice.

## Modules and ownership

| Module | Owns |
| --- | --- |
| `:app` | Activity, manifest, composition wiring only |
| `:core:model` | Android/Compose-free IDs and models; app identity = component + user/profile |
| `:core:grid` | Coordinates, spans, collision, movement, packing, width adaptation |
| `:core:designsystem` | Custom Compose components; no DB or Android services |
| `:platform:launcherapps` | `LauncherApps` discovery, profiles, launches, callbacks |
| `:feature:home` | Home presentation and gestures |

Check `settings.gradle.kts` for the actual module list. Dependencies: `app -> feature -> core`, `app -> platform -> core`, `core:grid -> core:model`. Features do not depend on other features. One owner per mutable state stream; persist stable IDs and logical cells, never `Drawable`, `PendingIntent`, `UserHandle`, `AppWidgetHostView`, Compose `State` or pixels.

## Invariants

- Six square 1x1 cells across on an average phone; spans 1x1, 2x1, 1x2, 2x2, 4x2; variable columns for wider viewports. All packing lives in `:core:grid`.
- Folders are floating overlays over the current page, not navigation destinations. Expanded app tiles are not system widgets.
- Custom Compose UI from Foundation/graphics/animation/gestures; no Material NavigationBar/Card/FAB/SearchBar for core surfaces.
- `LauncherApps` is the catalogue across profiles (`PackageManager` alone is not enough). Keep profile identity through launch and persistence.
- No enumeration, icon decoding, DB queries or heavy parsing during recomposition or on the UI thread.
- Permissioned capabilities (notification listener, usage access, contacts, SAF, widget hosting) arrive with their owning feature; never pre-grant. Notification history is the launcher's own captured history.

## Code discipline

No generic `Utils.kt`, god classes/composables, or DB/service objects in UI state. Split on responsibility, not line count. Do not add speculative extension points or a second source of truth. Tests must assert real behavior (grid: edges, collisions, holes, spans, deterministic packing, width adaptation). Android-only claims need device/instrumentation evidence. Don't silently broaden a task; log follow-ups.

## Validation

Per implementation slice, run yourself and record exact command and result (distinguish NO-SOURCE from passing):

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

If the SDK is not found, set `ANDROID_HOME` or use an ignored `local.properties`; never commit machine paths. **One shell command per invocation**: no `;`, `&&`, `||`, pipes or substitutions. A sandbox denial is not a reason to quit; use a permitted equivalent and report only what stays blocked.

## Git

Assume concurrent work. Never reset --hard, clean, force-checkout, stash, amend or revert without explicit authorization. Do not commit, push or stage unless the user asks; if staging, name paths (never `git add .`). Keep secrets, raw notification content, contact data and device dumps out of tracked logs. `captures/`, SDK paths and build outputs stay ignored.

## State logs

`state-logs/CURRENT.md` points at the active objective and latest handoff; `state-logs/handoffs/` is append-only (format in `state-logs/README.md`). Update after each checkpoint and before ending. A handoff states: completed work, touched paths, decisions and reasons, exact validation with outcomes, unverified device behavior, known failures, worktree/commit state, and one next action with an exit condition. Say "not run" rather than implying a pass.

## Subagents (`.claude/agents/`)

- `finder`: read-only exploration; give it one question and a small search area.
- `implementer`: one narrowly scoped change with named files, acceptance conditions and validation.

Never delegate "implement a phase", "review the whole repo", or combined platform + UI + persistence work. Explore first, then implement, then review separately. Do complex work yourself.

## End of slice

Verify exit behavior, tests, lint, dependency graph and diff; update the task spec only if its contract changed; update state logs so a new agent can resume from files alone. Report what works now, not the roadmap.
