#!/usr/bin/env python3
"""List declared Gradle modules and their source files."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
settings = (ROOT / "settings.gradle.kts").read_text()
for module in re.findall(r'"(:[\w:]+)"', settings):
    folder = ROOT.joinpath(*module.strip(":").split(":"))
    print(f"{module} ({folder.relative_to(ROOT)})")
    for source in sorted((folder / "src").rglob("*.kt")):
        print(f"  {source.relative_to(ROOT)}")
