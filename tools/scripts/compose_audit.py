#!/usr/bin/env python3
"""Flag likely architecture leaks into Compose screens for human review."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
suspects = ("LauncherApps", "AppWidgetHost", "NotificationListenerService", "Room.databaseBuilder", "getActivityList(")
for source in sorted(ROOT.rglob("*.kt")):
    if "build" in source.relative_to(ROOT).parts:
        continue
    text = source.read_text()
    if "@Composable" not in text:
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if any(token in line for token in suspects):
            print(f"{source.relative_to(ROOT)}:{number}: possible platform/data access: {line.strip()}")
