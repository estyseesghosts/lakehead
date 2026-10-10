# ProtoLaunch — agent operating manual

ProtoLaunch is a **real Android HOME launcher** under application ID and namespace `me.foxtails.lakehead`; its display name is **ProtoLaunch**. It is currently a buildable foundation with a Compose placeholder, **not yet a HOME-capable launcher**. The purpose of this repository is to implement the launcher in small, verified slices without collapsing Android integration, placement, persistence and UI into one application class or screen.

This file is the authoritative agent workflow. `docs/architecture.md`, `docs/design.md` and `docs/platform-capabilities.md` define the respective stable contracts; `docs/first_draft.md` is the roadmap, not evidence that later features exist. `docs/agents/tasks/` holds durable slice specifications. `state-logs/` holds current and historical execution state. Source and tests decide what is actually implemented. If any document disagrees with code, verify and resolve the discrepancy; do not silently act on stale claims.

## 1. Start every coding session from the repository

1. Read this file and `state-logs/CURRENT.md`, then the handoff linked there.
2. Read the relevant task spec in `docs/agents/tasks/`, architecture/design/platform docs as needed, and the affected source and tests.
3. Inspect `git status --short` and the relevant diff. The repository may have no commits yet; do not mistake an untracked tree for an empty project. Treat unfamiliar edits as user or concurrent work.
4. Run `python3 tools/scripts/repo_map.py` and `python3 tools/scripts/module_graph.py` when changing a module boundary. Use `trace_symbol.py <symbol>` or grep to locate callers, and audits to locate review candidates.
5. State one bounded objective, its owning module, inputs/outputs, acceptance conditions and verification before editing. Start a state-log entry for a multi-step coding session.

Do not rely on remembered conversation context for task state. A new agent must be able to continue from repository files alone.

## 2. Scope and phase gates

The first milestone is ordered: **0A foundation (done) → 0B launcher platform → 1A grid → 1B design primitives → 1C minimal persisted home → 1D folders**. Do not start Message Center, search, widgets or grouped All Apps to make the placeholder look finished. Complete the exit gate of one slice before claiming the next. Break a large slice into independently verifiable changes; do not keep multiple half-built subsystems in flight.

The initial 0B exit gate is: ProtoLaunch can be selected as HOME; display installed launchable activities across accessible profiles; launch an activity under the correct profile; and react to package additions/removals without restarting. A manifest filter alone or a list of package names is not this gate. Later phases are specified in `docs/first_draft.md` and `docs/design.md`.

**Subagent requests must be narrow.** The orchestrator must give each agent one concrete deliverable or question, specific files or a small search area, acceptance conditions, validation and explicit exclusions. Never delegate “implement a phase,” “review the whole repo,” or combined platform + UI + persistence work. Use focused exploration first when ownership is unknown, then a separate implementation and a separate targeted review. Read `.opencode/agents/orchestrator.md` for the agent roster and delegation contract.

## 3. Module and state ownership

Current Gradle modules (check `settings.gradle.kts` for the actual list):

| Owner | Responsibility |
| --- | --- |
| `:app` | Activity, manifest and composition wiring; no catalogue, packing or business logic |
| `:core:model` | Android/Compose-free identifiers and domain models; app identity includes component **and user/profile** |
| `:core:grid` | Platform-agnostic coordinate, span, collision, movement, packing and adaptation rules; no tiles or framework objects |
| `:core:designsystem` | Custom launcher-owned Compose components with small state/event contracts; no database or Android services |
| `:platform:launcherapps` | `LauncherApps` discovery, profile access, launches and callback invalidation behind domain-facing contracts |
| `:feature:home` | Home presentation and gestures; requests placement from `:core:grid` |

Dependencies point `app → feature → core` and `app → platform → core`; `core:grid → core:model`. Feature modules do not depend on other features' implementations. `:app` wires contracts; it does not become a global manager. Planned data, platform and feature owners are mapped in `docs/architecture.md`; add a Gradle module when real code needs that boundary, not as an empty directory exercise.

One owner for each mutable state stream: installed-app catalogue in the platform gateway, logical persisted placement in the future data layer, transient gesture/presentation state in its feature, and legality/packing in grid. Persist stable identifiers and logical cells, not `Drawable`, `PendingIntent`, `UserHandle`, `AppWidgetHostView`, Compose `State` or pixels. Resolve framework resources at a platform boundary. Cross-layer changes must name their state owner and dependency direction before implementation.

## 4. Launcher-specific invariants

- On an average non-folding phone, home and opened floating folders use **five square 1×1 cells across**. Support spans 1×1, 2×1, 1×2, 2×2 and 4×2 without changing the persistence model; use variable column counts for wider viewports. Do not implement a second packing system in a feature.
- Placement and rendering are separate. Custom expanded app tiles are **not** system widgets. A folder opens as an independently scrollable floating overlay over the current page, not as a navigation destination.
- Launcher-owned UI uses Kotlin and Compose with custom components built from Foundation, graphics, text, animation and gestures. Do not build core product surfaces around high-level Material NavigationBar, Card, FAB, SearchBar or stock chips and promise to replace them later.
- HOME eligibility, default-role prompting, catalogue, launches and callbacks belong to 0B. Use `LauncherApps` as the launcher-facing inventory across accessible profiles; `PackageManager` alone is not the complete catalogue. Keep profile identity through launch and persistence.
- Permissioned capabilities are introduced with their owning feature: notification listener access, usage access, contacts, SAF document grants and widget hosting must not be invented or pre-granted. The notification history is the launcher's **own** normalized history captured after access, not Android's entire past history. See `docs/platform-capabilities.md`.
- Avoid installed-app enumeration, icon decoding, widget discovery, classification or database queries during recomposition. Cache immutable metadata with explicit callback invalidation and stable IDs. Search and notification parsing must not block the UI thread.

## 5. Code and change discipline

Do not solve architectural problems by concentrating unrelated responsibility in one file, class, composable, ViewModel, repository, manager or function. No generic `Utils.kt`; no database access in composables, service objects in UI state, business logic hidden in layout or complex screen composables that both obtain state and implement every interaction. Split on responsibility or dependency, **never on line count alone**. Audit scripts are review signals, not mandatory refactor triggers.

Before editing, inspect the owner, callers and tests. Make one coherent change at a time and use concrete names for domain concepts. Tests must exercise meaningful behavior/invariants rather than merely echo the implementation; grid work needs edges, collisions, holes, spans, deterministic packing and width adaptation. Android-only claims need device/instrumentation evidence; a successful unit build does not prove HOME selection, profile launches or callback delivery.

Do not introduce future extension points, a second source of truth or temporary wrong ownership on the theory that it can be fixed later. If requirements conflict with the documented boundary, explain the conflict and resolve it before building on the wrong layer. Do not silently broaden a task when it uncovers another concern; log and hand off the follow-up.

## 6. Validation and tools

Use the checked-in wrapper and run these yourself for each **implementation** slice:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

Run relevant module and instrumentation tests in addition when warranted. `tools/scripts/trace_symbol.py <symbol>` traces Kotlin references; `adb_capture.py` and `launcher_smoke.py` require a connected device. Record the exact command and result, and distinguish NO-SOURCE tests from actual passing tests. If Android SDK discovery fails locally, supply `ANDROID_HOME` or a local, ignored `local.properties`; do not commit a machine-specific SDK path. CI is in `.github/workflows/android.yml`.

Use one shell command per invocation. Do not chain commands with `;`, `&&`, `||`, pipes or substitutions: separate results are easier to attribute and permissions easier to respect. A sandbox denial is not permission to quit or bypass it. Inspect the error, use a permitted tool or individually invoked equivalent, continue independent work, and report only the specific check/action still blocked. Read-only agents may use their explicitly allowed **read-only** Python audits and Git inspection; they must not edit via any alternative tool.

## 7. Git and parallel work

Assume concurrent changes. Never overwrite unfamiliar work, reset hard, clean, force checkout, stash, amend or revert without explicit authorization. Inspect the diff before touching a file. Do not commit, push or stage unless the user explicitly requests it; if staging is requested, specify paths rather than `git add .`. Do not copy secrets, raw notification contents, contact data or full device dumps into tracked logs. `captures/`, SDK paths and build outputs stay ignored.

## 8. State logs and handoffs — required for long-horizon work

`state-logs/CURRENT.md` is the single pointer to the active objective and latest handoff. `state-logs/handoffs/` contains chronological, append-only handoffs; `docs/agents/tasks/` describes the durable acceptance contract. Read `state-logs/README.md` for exact formatting. Keep one active objective, one owner and an explicit next operation. At the beginning of a multi-step implementation session, create an entry with the intended scope and baseline; update it after each meaningful checkpoint and before ending. Link it from CURRENT when it becomes the latest handoff. For a small docs-only change, update the existing state record only when it materially changes what the next agent needs to know.

Every handoff must state: factual completed work; touched paths; decisions and their reasons; exact validation performed with outcomes; unverified device behavior; known failures and incomplete work; worktree/commit state; and **one concrete next action with an exit condition**. State “not run” rather than implying a check passed. Stable design decisions belong in docs, not only in a log. Do not mark a gate complete because a plan exists. A blocked session still writes a truthful handoff explaining how to resume.

## 9. End-of-slice check

Before declaring a slice complete, verify its exit behavior, tests and lint; inspect the changed dependency graph, ownership and diff; update the task spec only if its contract/status changed; update state logs; and make sure a new agent can resume without chat history. Report what works **now**, not the eventual roadmap. Small incomplete features may be handed off explicitly; wrong ownership and ambiguous partial implementations may not be disguised as done.
