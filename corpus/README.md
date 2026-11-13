# Parity corpus

Generated from the Python implementation in `darts-coach-windows`. Do not edit
these files by hand - regenerate them.

```
cd ../darts-coach-windows
python tools/parity/parity_gen.py --out ../dart-coach-android/corpus
```

The files here are the shared specification between the two implementations:

| File | Cases | What it pins |
|---|---|---|
| `parser.json` | 141 | Transcript -> `ParseResult`, and the two answer predicates |
| `plans.json` | 501 | Every remaining score -> the checkout or setup plan |
| `routes.json` | 6513 | Every score against every double a player asks for |
| `state.json` | 6 | Sequences of turns -> the resulting game state |
| `helpers.json` | 502 | The small pure functions: `canFinish`, `chain`, `numberToWords` |

`plans.json` covers every remaining score from 1 to 501, because the search is
deterministic and this is therefore a complete characterisation of the planner
rather than a sample of it. A port that differs on one score out of 501 is a
broken port, and a sample would not notice.

`routes.json` has 6513 cases and 1561 distinct behaviours among them, so a
sample there would miss things too.

The corpus is pretty-printed rather than minified, and that is deliberate. The
whole value of checking a corpus in is that a human can read the diff when the
Python behaviour changes on purpose. A minified file would save a megabyte and
lose the reason the corpus exists.

## Where the cases come from

The parser cases are not arbitrary. Each one is a rule that the Windows build
earned from a live failure:

- `score-one-eighteen` - "one eighteen" is 19, not 118. The second number has
  to be 20 or more for "one eighty" to mean 180.
- `cmd-bus` - whisper writes "bust" as "bus", so the bare spelling has to work.
- `answer-no-inside-number` - whisper writes "no" inside "one hundred", so a
  "no" that is only part of a sentence must not throw the score away.
- `answer-all-right` - "all right" is deliberately *not* an answer. A missed
  "yes" leaves the question open; an unwanted "yes" starts a new game.
- `cmd-new-game` and `cmd-undo` carry `needs_confirmation`, because a 440 Hz
  tone was once transcribed as "I'm going to go back" and undid a real turn.

See `docs/PARITY.md` for the format and for why this exists.