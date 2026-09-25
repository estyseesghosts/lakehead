# State-log protocol

State logs are tracked handoffs for long-horizon agent work, not a replacement for code, tests or the stable contracts in `docs/`. Start each session with `AGENTS.md` → `state-logs/CURRENT.md` → the linked handoff → the relevant task spec. Verify all claims against the worktree.

- `CURRENT.md`: one active objective, its owner, task-spec path, latest handoff link, gate status and one exact next operation. Update the pointer only after writing the handoff it points to.
- `handoffs/YYYY-MM-DD-<short-topic>.md`: a chronological handoff for a completed or interrupted meaningful slice. Use a new filename for each handoff; do not overwrite older records. If several occur on a day, add `-02`, `-03`, etc.
- `docs/agents/tasks/`: durable acceptance criteria for a slice, not a play-by-play or a substitute for `CURRENT.md`.

Handoff template:

```md
# YYYY-MM-DD — <single bounded objective>
Status: completed | in progress | blocked
Owner/slice: <module and task-spec path>

## Baseline and worktree
<starting state, any existing changes, commit status>

## Completed and decisions
<facts, touched paths, state/dependency ownership and reasoning>

## Validation
<exact commands and PASS/FAIL/NOT RUN; device evidence separately>

## Remaining and next action
<known issues and incomplete work; one concrete next step, affected owner and exit condition>
```

Begin a multi-step coding session with a short baseline/intended-scope entry, then update it at checkpoints and close it truthfully. Never invent results, silently erase failures or paste secrets, contact data, notification contents or raw device dumps. Hand off partial work explicitly. Preserve old logs as history; correct stale claims in a new handoff and update CURRENT.
