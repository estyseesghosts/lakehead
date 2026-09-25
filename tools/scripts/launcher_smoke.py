#!/usr/bin/env python3
"""Start installed ProtoLaunch and check that its process runs."""
import subprocess

PACKAGE = "me.foxtails.lakehead"
subprocess.run(["adb", "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity"], check=True)
result = subprocess.run(["adb", "shell", "pidof", PACKAGE], capture_output=True, text=True, check=True)
print(f"{PACKAGE} pid: {result.stdout.strip()}")
