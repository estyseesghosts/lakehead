# ProtoLaunch

Android launcher project (`me.foxtails.lakehead`). The current foundation opens a Compose placeholder; launcher capabilities arrive in the next slices.

## Build

Use JDK 17 or newer and Android SDK platform 36. From the project root:

```sh
./gradlew assembleDebug test lint
python3 tools/scripts/repo_map.py
python3 tools/scripts/module_graph.py
python3 tools/scripts/function_audit.py
python3 tools/scripts/compose_audit.py
```

The initial roadmap is [docs/first_draft.md](docs/first_draft.md). Agent rules are in [AGENTS.md](AGENTS.md); the next agent starts at [state-logs/CURRENT.md](state-logs/CURRENT.md). Module ownership is in [docs/architecture.md](docs/architecture.md), and the next platform slice is specified in [docs/agents/tasks/0b-launcher-platform.md](docs/agents/tasks/0b-launcher-platform.md).
