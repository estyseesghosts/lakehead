#!/usr/bin/env python3
"""Print and verify declared project dependency edges."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
settings = (ROOT / "settings.gradle.kts").read_text()
modules = set(re.findall(r'"(:[\w:]+)"', settings))
errors = []
for module in sorted(modules):
    build = ROOT.joinpath(*module.strip(":").split(":"), "build.gradle.kts")
    for dependency in re.findall(r'project\("(:[\w:]+)"\)', build.read_text()):
        print(f"{module} -> {dependency}")
        if dependency not in modules:
            errors.append(f"unknown dependency: {module} -> {dependency}")
        if module.startswith(":core:") and not dependency.startswith(":core:"):
            errors.append(f"core depends outside core: {module} -> {dependency}")
        if module.startswith(":feature:") and dependency.startswith(":feature:"):
            errors.append(f"feature-to-feature dependency: {module} -> {dependency}")
if errors:
    raise SystemExit("\n".join(errors))
