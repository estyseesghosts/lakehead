# 2026-09-26 — onEnterWiggle move restoration + twin corner controls

Commits: 1995dbd move bridge, c554fb2 twin controls, e085869 obsolete offset test deletion.

Root cause fixed: onEnterEdit was dead; onEnterWiggle now remains sole long-press entry and selected Editing body drag synchronously bridges to Active MOVE; selected resize-corner drag bridges to Active RESIZE. Local session fixes prevent stale rememberUpdatedState reads; release commits use local last-valid session; idle/selected drag ownership is exclusive. onEnterEdit retired from production.

Controls: RemoveButton and ResizeHandle are matching 32dp themed circles with 48dp touch boxes; top-right unpin/remove and bottom-right resize, center-pinned; controls show for selected Editing and Active; parent remains sole resize detector; unpin has zero move/resize writes. Geometry tests cover signed/edge spans; android HomeCanvas controls assertions updated.

Validation: feature home unit, assembleDebug, test, lint, androidTest Kotlin compile SUCCESS; repo_map/module_graph SUCCESS with no new forbidden edges; function/compose audits only pre-existing findings. Device/instrumentation NOT-RUN: actual long-press body drag commit, collision snapback, resize commit/block, edge controls, unpin persistence.

Next action: user/device verifies move, resize, unpin flows; any failures become bounded follow-up slice. Do not claim device proof.
