# Slice 0A — Foundation

Status: initialized and validated. Root Gradle build, application placeholder, module boundaries, docs, analysis/capture scripts, agent definitions, CI and Git repository created. `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug test lint` passed locally; all four repository audits and Python compilation passed. The first build needed `ANDROID_HOME` because this machine does not have a project `local.properties`.

Next: Slice 0B — add HOME eligibility and request, profile-aware `LauncherApps` catalogue, callbacks and launch handling. Exit gate: select ProtoLaunch as HOME, list installed launchable activities across accessible profiles, launch them, and see installs/removals without restart. Then implement grid primitive with unit tests before building the home UI.
