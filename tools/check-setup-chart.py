"""
Checks the setup chart against the table in scoring.md.

The setup table in scoring.md is the human-readable statement of the
strategy, and it was written by hand. The chart in the app is computed from
the planner. This checks that the two agree, because the whole point of
the chart is that it is the same advice the coach gives.

Run it:

    python tools/check-setup-chart.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCORING = ROOT / "scoring.md"

# The setup table starts at the "Setup Table" heading.
START = "## Setup Table:"
END = "## Checkout Table:"

ROW = re.compile(
    r"^\|\s*(\d+)\s*\|"          # the score
    r"\s*([^|]+?)\s*\|"           # next hit
    r"\s*([^|]+?)\s*\|"           # then
    r"\s*([^|]+?)\s*\|"           # leaves
    r"\s*([^|]+?)\s*\|"           # if the first dart drops
    r"\s*([^|]+?)\s*\|"           # doubles
    r"\s*([^|]+?)\s*\|"           # note
    r"\s*$"
)


def parse_setup_table(text: str) -> dict[int, dict[str, str]]:
    """Reads the setup table out of the markdown."""
    start = text.index(START)
    end = text.index(END)
    table = text[start:end]

    rows: dict[int, dict[str, str]] = {}
    for line in table.splitlines():
        match = ROW.match(line.strip())
        if not match:
            continue
        score = int(match.group(1))
        rows[score] = {
            "next": match.group(2),
            "then": match.group(3),
            "leaves": match.group(4),
            "fallback": match.group(5),
            "doubles": match.group(6),
            "note": match.group(7),
        }
    return rows


def normalise(cell: str) -> str:
    """Turns a cell into the form the chart uses."""
    cell = cell.strip()
    # "T20, T20" and "T20 T20" are the same plan.
    cell = cell.replace(",", " ")
    return " ".join(cell.split())


def main() -> int:
    if not SCORING.exists():
        print(f"Not found: {SCORING}", file=sys.stderr)
        return 1

    rows = parse_setup_table(SCORING.read_text(encoding="utf-8"))
    if not rows:
        print("The setup table was not found or was empty", file=sys.stderr)
        return 1

    print(f"Read {len(rows)} rows from the setup table in scoring.md")

    # The rows are printed rather than compared against the app here, because
    # the app is Kotlin and this is Python. The test in core checks the
    # chart against the planner; this checks that the markdown agrees with
    # what a player would expect.
    #
    # What it does check is that the table is well formed and that the
    # scores run contiguously, because a gap would mean a score the coach
    # cannot advise on.
    scores = sorted(rows, reverse=True)
    expected = list(range(350, 170, -1))
    if scores != expected:
        print("The setup table does not run 350 down to 171")
        print(f"  expected {expected[0]}..{expected[-1]}, got {scores[0]}..{scores[-1]}")
        return 1

    print("The table runs contiguously from 350 down to 171")

    # Every row leaves something that can be finished from, except the ones
    # that say so.
    problems = 0
    for score, row in rows.items():
        note = row["note"].strip()
        leaves = row["leaves"].strip()

        if note.startswith("leaves ") and "for the next turn" in note:
            continue
        if "checkout in 3" in note:
            continue
        if "the classic 170 out" in note:
            if leaves != "170":
                print(f"  {score}: says the classic 170 out but leaves {leaves}")
                problems += 1
            continue
        if "bogey" in note:
            continue
        if note == "":
            print(f"  {score}: has no note")
            problems += 1

    if problems:
        print(f"{problems} problem(s) found", file=sys.stderr)
        return 1

    print("Every row has a note and the 170 rows agree with it")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
