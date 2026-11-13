# Parity harness

The Kotlin core is a **port** of the Python one, not a rewrite. This directory
holds the mechanism that proves the port is faithful, because reading 1200
lines of Kotlin twice is not a verification method.

## The idea

The Windows repo (`darts-coach-windows`) is the specification. It has 300+
passing tests. The Kotlin port must produce *identical* output for identical
input.

So the harness runs the Python implementation and the Kotlin implementation over
the same corpus and compares the results byte for byte.

```
darts-coach-windows/          dart-coach-android/
  src/game/parser.py     -->   core/.../Parser.kt
  src/game/darts.py      -->   core/.../Darts.kt
  src/game/state.py      -->   core/.../GameState.kt
        |                          |
        |  tools/parity_gen.py     |  core/src/test/.../ParityTest.kt
        +------> corpus/*.json <---+
```

## Corpus format

One JSON file per area, in `corpus/`. Generated from the Python side, checked
in, and consumed by both.

### `corpus/parser.json`

```json
[
  {
    "id": "bare-number",
    "text": "125",
    "state": {"remaining": 501, "turns_played": 0},
    "expect": {
      "command": "turn_score",
      "score": 125,
      "darts": null,
      "double": null,
      "refused": false,
      "needs_confirmation": false
    }
  },
  {
    "id": "one-eighteen-ambiguity",
    "text": "one eighteen",
    "state": {"remaining": 501, "turns_played": 0},
    "expect": {
      "command": "turn_score",
      "score": 118,
      "darts": null,
      "double": null,
      "refused": false,
      "needs_confirmation": false
    }
  }
]
```

The `one eighteen` case is not arbitrary. It is the rule that the second
number must be 20 or more for "one eighty" to mean 180 - see the comment in
`src/game/parser.py` and `PLAN.md` Phase 1. It is the single most likely
thing to get subtly wrong in a port, so it is pinned explicitly.

### `corpus/planner.json`

```json
[
  {
    "id": "checkout-170",
    "remaining": 170,
    "preferred_double": null,
    "expect": {
      "labels": ["T20", "T20", "Bull"],
      "leaves": 0,
      "finishes": true,
      "fallback_labels": [],
      "fallback_leaves": null,
      "doubles": []
    }
  }
]
```

`planner.json` should cover every remaining score from 1 to 501. The search is
deterministic, so this is a complete characterisation, not a sample.

### `corpus/state.json`

Turn-by-turn sequences, so the state machine is checked over a whole leg
rather than one operation at a time:

```json
[
  {
    "id": "full-leg",
    "turns": [45, 26, 60, 0, 100, 41, 20],
    "expect": {
      "remaining": 209,
      "turns_played": 7,
      "preferred_double": null
    }
  }
]
```

## Running it

Generate the corpus from the Python repo:

```
cd ../darts-coach-windows
python tools/parity_gen.py --out ../dart-coach-android/corpus
```

Run the Kotlin side:

```
./gradlew :core:test --tests '*ParityTest*'
```

The Kotlin test reads the checked-in corpus, runs each case, and asserts the
result matches. A mismatch fails the build with the offending case id.

## What parity does not cover

Parity proves the *logic* is the same. It says nothing about:

- Whether the transcription is accurate on the device (Phase 1 spike).
- Whether the echo gate holds on the phone (Phase 4).
- Whether the timing of the half-duplex gate feels right.

Those are measured on the device, not against the Python.
