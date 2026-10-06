#!/usr/bin/env python3
"""Tests for function_audit.py's Kotlin function-body measurement.

Run with: python3 -m unittest discover -s tools/scripts -t tools/scripts
"""
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from function_audit import find_functions, long_functions, mask_kotlin, repository_root

FIXTURE = Path(__file__).resolve().parent / "testdata" / "GridShapedSignature.kt"
GRID_SOURCE = (repository_root() / "feature/home/src/main/java/me/foxtails/lakehead/feature/home/HomeCanvasGrid.kt")


def spans_by_name(source: str) -> dict:
    return {span.name: span for span in find_functions(source)}


class MaskingTest(unittest.TestCase):
    def test_masking_preserves_length_and_line_numbering(self):
        source = FIXTURE.read_text()
        masked = mask_kotlin(source)
        self.assertEqual(len(masked), len(source))
        self.assertEqual(masked.count("\n"), source.count("\n"))

    def test_masking_replaces_literals_and_comments_with_spaces(self):
        masked = mask_kotlin('val a = "{"  // }\nval b = \'{\'\n/* { */\n')
        self.assertNotIn("{", masked)
        self.assertNotIn("}", masked)
        self.assertIn("val a", masked)
        self.assertIn("val b", masked)
        self.assertEqual(masked.count("\n"), 3)


class FunctionExtentTest(unittest.TestCase):
    """Every expectation here is a line number in the checked-in fixture."""

    def setUp(self):
        self.spans = spans_by_name(FIXTURE.read_text())

    def test_lambda_default_signature_is_not_truncated(self):
        """The regression: a lambda default balances inside the parameter list.

        Counting braces from the `fun` keyword without skipping the signature
        stopped at the first such default and reported ~5 lines.
        """
        span = self.spans["lambdaDefaultsInSignature"]
        self.assertEqual(span.start_line, 18)
        self.assertEqual(span.body_start_line, 25)
        self.assertEqual(span.end_line, 28)
        self.assertEqual(span.line_count, 11)

    def test_grid_shaped_signature_is_measured_in_full(self):
        """A HomeCanvasGrid-shaped signature: 20 lines with six lambda defaults."""
        span = self.spans["gridShapedSignature"]
        self.assertEqual(span.start_line, 61)
        self.assertEqual(span.body_start_line, 81)
        self.assertEqual(span.end_line, 112)
        self.assertEqual(span.line_count, 52)

    def test_generic_functions_are_measured(self):
        """`fun <T> name(...)` must not be skipped as a nameless declaration."""
        where_clause = self.spans["genericWithWhereClause"]
        self.assertEqual((where_clause.start_line, where_clause.end_line), (114, 117))
        nested = self.spans["genericOverNestedTypes"]
        self.assertEqual((nested.start_line, nested.end_line), (119, 120))

    def test_braces_in_comments_strings_and_raw_strings_are_ignored(self):
        """Bodies whose first line holds a brace inside a string still end right.

        `braceInsideBodyText` opens with `"} not a closing brace {"` and holds a
        lone `}` inside a raw string; its extent must still stop at its own brace.
        """
        for name in ("lambdaDefaultsInSignature", "braceInsideBodyText"):
            span = self.spans[name]
            self.assertGreater(span.line_count, 2, name)

    def test_nested_function_is_measured_separately(self):
        outer = self.spans["nestedBlocks"]
        inner = self.spans["inner"]
        self.assertEqual((outer.start_line, outer.end_line), (35, 41))
        self.assertEqual((inner.start_line, inner.end_line), (36, 39))

    def test_interface_declaration_is_not_a_function(self):
        self.assertNotIn("Callback", self.spans)

    def test_declaration_without_a_body_is_not_reported(self):
        """`fun invoke(value: Int)` has no body; it must not borrow a later brace."""
        self.assertNotIn("invoke", self.spans)

    def test_expression_body_ends_at_its_statement(self):
        self.assertEqual(self.spans["multiLineParameterList"].line_count, 4)
        self.assertEqual(self.spans["anonymousLambda"].line_count, 1)

    def test_every_fixture_function_is_found(self):
        self.assertEqual(
            sorted(self.spans),
            [
                "anonymousLambda",
                "braceInsideBodyText",
                "genericOverNestedTypes",
                "genericWithWhereClause",
                "gridShapedSignature",
                "inner",
                "lambdaContainingNamedFunction",
                "lambdaDefaultsInSignature",
                "multiLineParameterList",
                "nestedBlocks",
            ],
        )


class ReportPathTest(unittest.TestCase):
    def test_long_functions_reports_the_fixture_above_threshold(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copy(FIXTURE, root / FIXTURE.name)
            found = [(span.name, span.start_line, span.line_count)
                     for _, span in long_functions(root, threshold=40)]
        self.assertEqual(found, [("gridShapedSignature", 61, 52)])

    def test_long_functions_respects_a_higher_threshold(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copy(FIXTURE, root / FIXTURE.name)
            self.assertEqual(list(long_functions(root, threshold=1000)), [])


class HomeCanvasGridTest(unittest.TestCase):
    """Phase 0.3 exit gate against the real, not-yet-decomposed grid."""

    def test_home_canvas_grid_is_reported(self):
        spans = find_functions(GRID_SOURCE.read_text())
        declaration = next(span.start_line for span in spans if span.name == "HomeCanvasGrid")
        source_line = GRID_SOURCE.read_text().splitlines()[declaration - 1]
        self.assertIn("fun HomeCanvasGrid(", source_line)

    def test_home_canvas_grid_body_is_the_largest_in_the_repository(self):
        longest = max(
            (span for _, span in long_functions(repository_root())),
            key=lambda span: span.line_count,
        )
        self.assertEqual(longest.name, "HomeCanvasGrid")

    def test_home_canvas_grid_tripwire_size(self):
        """Deliberate tripwire: Phase 8 decomposition should delete this test.

        Until HomeCanvasGrid() is decomposed the audit must report a function
        of roughly the 1,882 lines the repair plan measured. When Phase 8
        replaces it with thin responsibility files, replace this assertion with
        a per-file upper bound rather than lowering it silently.
        """
        span = next(span for span in find_functions(GRID_SOURCE.read_text())
                    if span.name == "HomeCanvasGrid")
        self.assertGreater(span.line_count, 1500)
        self.assertLess(span.line_count, 2000)


if __name__ == "__main__":
    unittest.main()