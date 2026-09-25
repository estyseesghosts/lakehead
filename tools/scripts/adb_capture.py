#!/usr/bin/env python3
"""Capture screen and device state into captures/ for debugging."""
from pathlib import Path
import subprocess
from datetime import datetime

ROOT = Path(__file__).resolve().parents[2]
folder = ROOT / "captures"
folder.mkdir(exist_ok=True)
stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
with (folder / f"screen-{stamp}.png").open("wb") as output:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=output, check=True)
with (folder / f"state-{stamp}.txt").open("w") as output:
    for command in (["adb", "shell", "dumpsys", "activity", "activities"], ["adb", "shell", "wm", "size"]):
        subprocess.run(command, stdout=output, check=True)
print(folder / f"screen-{stamp}.png")
