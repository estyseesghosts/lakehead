#!/usr/bin/env python3
"""Report long Kotlin functions as review candidates (not automatic refactors)."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
for source in sorted(ROOT.rglob("*.kt")):
    if "build" in source.relative_to(ROOT).parts:
        continue
    lines = source.read_text().splitlines()
    for index, line in enumerate(lines):
        if not re.search(r"\bfun\s+\w+\s*\(", line):
            continue
        depth = 0
        started = False
        for end in range(index, len(lines)):
            depth += lines[end].count("{") - lines[end].count("}")
            started |= "{" in lines[end]
            if started and depth <= 0:
                if end - index + 1 > 80:
                    print(f"{source.relative_to(ROOT)}:{index + 1}: {end - index + 1} lines (review ownership/complexity)")
                break
