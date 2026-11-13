# Parity corpus

Generated from the Python implementation in `darts-coach-windows`. Do not edit
these files by hand - regenerate them.

```
cd ../darts-coach-windows
python tools/parity_gen.py --out ../dart-coach-android/corpus
```

The files here are the shared specification between the two implementations:

| File | What it pins |
|---|---|
| `parser.json` | Transcript -> `ParseResult` |
| `planner.json` | Remaining score -> checkout route and setup plan |
| `state.json` | A sequence of turns -> the resulting game state |

`planner.json` covers every remaining score from 1 to 501. The search is
deterministic, so this is a complete characterisation rather than a sample.

See `docs/PARITY.md` for the format and for why this exists.
