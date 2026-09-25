#!/usr/bin/env python3
"""Find a symbol's declarations and references across source files."""
import argparse
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("symbol", nargs="?", help="literal Kotlin identifier; omit to list declarations")
args = parser.parse_args()
ROOT = Path(__file__).resolve().parents[2]
pattern = (re.compile(r"\b" + re.escape(args.symbol) + r"\b") if args.symbol
           else re.compile(r"\b(?:class|interface|object|fun)\s+\w+"))
for source in sorted(ROOT.rglob("*.kt")):
    if "build" in source.relative_to(ROOT).parts:
        continue
    for number, line in enumerate(source.read_text().splitlines(), 1):
        if pattern.search(line):
            print(f"{source.relative_to(ROOT)}:{number}: {line.strip()}")
