#!/usr/bin/env python3
"""Report long Kotlin functions as review candidates (not automatic refactors).

A function's body must be measured from the ``fun`` keyword through the brace
that closes the real body.  Brace counting that starts on the declaration line
itself stops inside the parameter list whenever a default argument contains a
lambda (``onMoveTile: (ItemId) -> Unit = { _, _ -> }`` balances on its own
line), so the largest function in the repository was reported as a few lines.
The scanner therefore masks comments and literals first, then skips the whole
signature — including the parameter list, return type and any lambda defaults —
before it begins counting braces.

Known approximation: an expression body (``fun f() = ...``) has no braces, so
its extent is measured to the end of the line where its delimiters rebalance,
extended while a line ends with a continuation character.  Block bodies, which
are what this audit exists to catch, are measured exactly.
"""
from dataclasses import dataclass
from pathlib import Path
import re
import sys

DEFAULT_THRESHOLD = 80
_FUN = re.compile(r"\bfun\b")
_IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
_CONTINUATION = frozenset(".,?:+-*/%=<>&|!(")


@dataclass(frozen=True)
class FunctionSpan:
    """One Kotlin function, measured from its `fun` keyword to its body end."""

    name: str
    start_line: int
    body_start_line: int
    end_line: int

    @property
    def line_count(self) -> int:
        return self.end_line - self.start_line + 1

    def report(self, label: str) -> str:
        return f"{label}:{self.start_line}: {self.line_count} lines (review ownership/complexity)"


def mask_kotlin(source: str) -> str:
    """Blank comments and literals so brace counting only sees real code.

    Every masked character becomes a space and newlines are preserved, so the
    result has the same length and line numbering as `source`.
    """
    out = list(source)
    length = len(source)
    index = 0
    state = "code"
    block_depth = 0

    def blank(offset: int) -> None:
        if source[offset] != "\n":
            out[offset] = " "

    while index < length:
        char = source[index]
        if state == "code":
            if source.startswith("//", index):
                blank(index)
                blank(index + 1)
                index += 2
                state = "line_comment"
            elif source.startswith("/*", index):
                blank(index)
                blank(index + 1)
                index += 2
                block_depth = 1
                state = "block_comment"
            elif source.startswith('"""', index):
                for offset in range(index, index + 3):
                    blank(offset)
                index += 3
                state = "raw_string"
            elif char == '"':
                blank(index)
                index += 1
                state = "string"
            elif char == "'":
                blank(index)
                index += 1
                state = "char"
            else:
                index += 1
            continue

        if state == "line_comment":
            if char == "\n":
                state = "code"
            else:
                blank(index)
            index += 1
            continue

        if state == "block_comment":
            if source.startswith("/*", index):
                blank(index)
                blank(index + 1)
                block_depth += 1
                index += 2
            elif source.startswith("*/", index):
                blank(index)
                blank(index + 1)
                block_depth -= 1
                index += 2
                if block_depth == 0:
                    state = "code"
            else:
                blank(index)
                index += 1
            continue

        if state == "string":
            if char == "\\" and index + 1 < length:
                blank(index)
                blank(index + 1)
                index += 2
            elif char == '"':
                blank(index)
                index += 1
                state = "code"
            else:
                blank(index)
                index += 1
            continue

        if state == "raw_string":
            if source.startswith('"""', index):
                for offset in range(index, index + 3):
                    blank(offset)
                index += 3
                state = "code"
            else:
                blank(index)
                index += 1
            continue

        # character literal; a newline means this was not one, so recover
        if char == "\\" and index + 1 < length:
            blank(index)
            blank(index + 1)
            index += 2
        elif char == "'":
            blank(index)
            index += 1
            state = "code"
        elif char == "\n":
            state = "code"
            index += 1
        else:
            blank(index)
            index += 1

    return "".join(out)


def _matching(masked: str, index: int, opener: str, closer: str) -> int:
    """Offset of the `closer` matching the `opener` at `index`, or -1."""
    depth = 0
    for cursor in range(index, len(masked)):
        char = masked[cursor]
        if char == opener:
            depth += 1
        elif char == closer:
            depth -= 1
            if depth == 0:
                return cursor
    return -1


def _skip_space(masked: str, index: int) -> int:
    cursor = index
    while cursor < len(masked) and masked[cursor].isspace():
        cursor += 1
    return cursor


def _continues(masked: str, line_end: int) -> bool:
    """True when the line ending at `line_end` carries an unfinished statement."""
    line_start = masked.rfind("\n", 0, line_end) + 1
    return masked[line_start:line_end + 1].rstrip()[-1:] in _CONTINUATION


def _statement_end(masked: str, start: int) -> int:
    """End offset of an expression body, which has no braces of its own."""
    depth = 0
    cursor = start
    end = start
    while cursor < len(masked):
        char = masked[cursor]
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "\n" and depth <= 0:
            if _continues(masked, cursor - 1):
                cursor += 1
                continue
            break
        elif not char.isspace():
            end = cursor
        cursor += 1
    return end


def _line_starts(source: str) -> list[int]:
    starts = [0]
    for index, char in enumerate(source):
        if char == "\n":
            starts.append(index + 1)
    return starts


def _line_of(line_starts: list[int], offset: int) -> int:
    low, high = 0, len(line_starts) - 1
    while low < high:
        mid = (low + high + 1) // 2
        if line_starts[mid] <= offset:
            low = mid
        else:
            high = mid - 1
    return low + 1


_BLOCK = "block"
_EXPRESSION = "expression"


def _body_start(masked: str, close_paren: int) -> tuple[str, int] | None:
    """Locate the body that follows a parameter list.

    Returns `(_BLOCK, offset-of-'{')`, `(_EXPRESSION, offset-of-'=')`, or None
    when the declaration has no body at all (an interface or abstract member),
    which must not borrow the brace of whatever comes next.
    """
    cursor = _skip_space(masked, close_paren + 1)
    depth = 0
    while cursor < len(masked):
        char = masked[cursor]
        if char in "([":
            depth += 1
        elif char in ")]":
            depth -= 1
        elif depth == 0:
            if char == "{":
                return _BLOCK, cursor
            if char == "=":
                return _EXPRESSION, cursor
            if char == "\n" and not _continues(masked, cursor - 1):
                return None
        cursor += 1
    return None


def find_functions(source: str) -> list[FunctionSpan]:
    """Every named Kotlin function in `source`, with its real body extent."""
    masked = mask_kotlin(source)
    line_starts = _line_starts(source)
    spans = []
    for match in _FUN.finditer(masked):
        name_start = _skip_space(masked, match.end())
        if masked[name_start:name_start + 1] == "<":
            # `fun <T> name(...)`: step over the type-parameter list first
            close_angle = _matching(masked, name_start, "<", ">")
            if close_angle < 0:
                continue
            name_start = _skip_space(masked, close_angle + 1)
        name_match = _IDENTIFIER.match(masked, name_start)
        if name_match is None:
            continue  # `fun interface`, or an anonymous `fun(...)`
        open_paren = _skip_space(masked, name_match.end())
        if open_paren >= len(masked) or masked[open_paren] != "(":
            continue
        close_paren = _matching(masked, open_paren, "(", ")")
        if close_paren < 0:
            continue
        located = _body_start(masked, close_paren)
        if located is None:
            continue
        kind, body_start = located
        if kind == _BLOCK:
            body_end = _matching(masked, body_start, "{", "}")
            if body_end < 0:
                continue
        else:
            body_end = _statement_end(masked, body_start + 1)
        spans.append(FunctionSpan(
            name=name_match.group(),
            start_line=_line_of(line_starts, match.start()),
            body_start_line=_line_of(line_starts, body_start),
            end_line=_line_of(line_starts, body_end),
        ))
    return spans


def repository_root() -> Path:
    """Repository root, derived from this script's location."""
    return Path(__file__).resolve().parents[2]


def iter_kotlin_sources(root: Path):
    for source in sorted(root.rglob("*.kt")):
        if "build" in source.relative_to(root).parts:
            continue
        yield source


def long_functions(root: Path, threshold: int = DEFAULT_THRESHOLD):
    for source in iter_kotlin_sources(root):
        for span in find_functions(source.read_text()):
            if span.line_count > threshold:
                yield source, span


def main() -> int:
    root = repository_root()
    for source, span in long_functions(root):
        print(span.report(str(source.relative_to(root))))
    return 0


if __name__ == "__main__":
    sys.exit(main())