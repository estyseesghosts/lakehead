---
# 2026-09-29 — nav viewport guard
Status: guard + unit proof done, device-gesture pending
Owner/slice: `:feature:home` — nav viewport guard (follow-up to d11945b review)
Baseline: prior commits 126e0db..d11945b (d11945b nav viewport WIP)
Touched paths:
- feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCanvas.kt 204-221 usableCanvasHeightPx guard (zero transient returns 0, positive F<=C throws)
- feature/home/src/test/java/me/foxtails/lakehead/feature/home/HomeNormalViewportTest.kt 38-51 rejection tests (F=100/C=200 throws, F=C=200 throws, F=0/C=0 returns 0)
Decisions/reasons: fail-fast over silent-zero (null rest disables scroll, hides bad viewport); owner :feature:home only, no :core:grid/:app/persistence; F=0 transient preserved.
Validation: :feature:home:testDebugUnitTest HomeNormalViewportTest BUILD SUCCESSFUL; :feature:home:compileDebugKotlin BUILD SUCCESSFUL; assembleDebug BUILD SUCCESSFUL 363 tasks; :app:installDebug on SM-G986W R5CN70E2SGV Installed on 1 device; No hierarchies blocker not encountered; gestures unverified (install alone proves nothing).
Known failures/incomplete: device gesture pass (tiles scroll beneath fixed pill + lowest tile reachable) still unverified; auto-scroll proximity, 6-col caller, repo recovery, 02 B1 explicitly excluded.
Worktree: guard files modified unstaged, concurrent M/?? preserved, no overwrite.
One next action: device gesture check on SM-G986W — scroll grid beneath fixed pill and reach lowest tile; exit when confirmed or bounded failure filed.
Exit gate: guard merged + unit proof + device gesture confirmed.
---
