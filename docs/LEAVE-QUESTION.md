# The leave question

"What does that leave?" is the question a player at the oche asks when they
want the number the coach's plan leaves for the finish. The coach stopped
saying the leave with every suggestion, because the screen shows it and
saying it cost half a second - so this is the way to ask for it.

## What it answers

The leave is recomputed from the score standing rather than taken from the
last suggestion, so it is right even if the score has moved on.

It is the leave of the plan **from where the player actually is**, which
means the darts they have already thrown count.

| The player is | The leave is |
|---|---|
| At the start of a visit | the leave of the three-dart plan from the score standing |
| Part way through a visit | the leave of the plan from the score they are on, with the darts they have left |
| At the end of a visit | the leave of the next visit, three darts, from the score the turn leaves |

When the player has named a double, it is the route onto that double that is
meant: the leave of the route is the shot at their double.

## The darts in hand count

This is the part that was wrong, and it was wrong in a way that made the
coach look like it was ignoring the player.

Standing at 401, the coach suggests treble nineteen, treble nineteen and
treble nineteen, which leaves 230. The player hits a treble twenty instead
and says so. The dart is recorded - the coach is silent, because a visit
that is not over is not read back - and then the player asks what that
leaves.

The coach used to answer "That leaves 230", because it computed the leave
from the score standing and ignored the dart in the player's hand. The
player had told it about a treble twenty, and it answered with the number
from before the treble twenty.

It answers now:

> You're on three hundred and forty-one with 2 darts left. That leaves 227.

The score is said as well as the leave, because "what does that leave" can
be asked about the score or about the plan, and the answer covers both.

## A question does not throw the reported turn away

There is a read-back between the coach and a confirmed turn: the coach says
"you said one hundred and eighty, right?" and the turn counts when the
player agrees.

Asking a question used to throw that away. The player reported their turn,
the coach read it back, the player asked what it left, and the coach
discarded the turn and answered about the score from before it. The darts
were gone.

The leave question is a question **about** the turn the player has
reported, so it is answered with that turn in, and the read-back is re-asked
afterwards rather than dropped. A "yes" still confirms the turn, and a "no"
still refuses it.

Everything else still supersedes the read-back. A player who reports a turn
and then says something unrelated has moved on, and the score is not
silently waiting for a stray "yes" three minutes later.

## The tests

`LeaveQuestionTest` in the core module covers it: the dart in hand, the
bare total, the finished visit, the bust, the player's own double, and the
case where there is nothing in hand, where the answer is unchanged.

The leave command is one of the two commands that exist in the Kotlin core
and not in the Python one. See `docs/PARITY.md`.
