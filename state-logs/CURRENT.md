# Current execution state
- Phase: Move/twin-controls — **CODE-COMPLETE, DEVICE-PENDING**.
- Active objective: User/device verification of move, resize, and unpin flows.
- Owner: user/device validation.
- Latest handoff: [`handoffs/2026-09-26-move-twin-controls.md`](handoffs/2026-09-26-move-twin-controls.md).
- Device: NOT-RUN — verify actual long-press body drag commit, collision snapback, resize commit/block, edge controls, and unpin persistence.
- Build/test/lint: **SUCCESS** — feature home unit, assembleDebug, test, lint, androidTest Kotlin compile, repo_map/module_graph. Audits only have pre-existing findings.
- Known issues: Device/instrumentation proof remains outstanding; do not claim move, resize, or unpin behavior is device-verified.
- Exact next operation: User/device verifies move, resize, and unpin flows; exit when those proofs are recorded PASS/FAIL, then create a bounded follow-up for any failures.
