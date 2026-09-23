# Recording Changes

Every structural change to a song is recorded as a typed record, and the records
made together are delivered as one batch. This is what undo replays, what layout
invalidates from, and what tells the document it has been modified — one
mechanism serving all three, rather than three notifications that could disagree.

For what happens to a recorded batch afterwards, see [undo.md](undo.md).

## Brackets

A record can only be made inside an open **modification bracket**. Brackets
**nest**, and the batch is delivered once, when the outermost one closes — so
what counts as a single edit is decided by whoever opens the outermost bracket,
not by the machinery. A dialog committing five fields in one bracket is one edit.

A bracket that accumulated nothing delivers nothing and does not mark the
document modified. That is what lets a commit path run unconditionally and still
leave a document clean when the user changed nothing.

One piece of work waits for the outermost bracket: the **tie rule**, which
removes every tie that no longer joins two notes of the same pitch. It runs once per
edit, as the outermost bracket is about to close — after every change and every
accidental restamp in it has landed, before the batch is delivered, and while the
bracket is still open, so its removals are recorded in the batch it closes. It
judges each line the batch touched and the line before each. An edit that throws
part way closes its bracket without the rule: its notes may not all be restamped,
and the batch is still delivered. See
[span-invalidation.md](span-invalidation.md#ties-are-note-state-judged-when-the-edit-is-over).

The record types form a closed set, so the inventory is checkable and a new kind
cannot be introduced without appearing in it. Span changes are recorded by one
addition record and one removal record whatever the span's kind; a tie, which is
note state rather than a span, has an addition and a removal of its own.

## Order within a batch

Undo replays a batch in reverse, and that makes emission order load-bearing in
one specific way.

**A record that removes dependent state is emitted before the change that
prompted it.** Reverse-order undo then restores the primary element *first*, so
when the dependent state is restored, whatever it was anchored to is alive again.
Emitting it the other way round would restore a span before the note it points
at.

**The tie rule's removals are the one exception.** The rule runs when the edit is
over, so each removal it records comes after the change that broke the tie.
Reverse-order undo is still correct: it restores those ties first, against the
final state, and a tie's record names only its anchor — the end is derived —
which the rule judged in its line in that final state. The changes that broke
the ties are undone after that.

**Line-terminal maintenance is emitted after** the line insertion or deletion
that prompted it, and for the same reason read the other way: it targets the
*other* line, so reverse-order undo should handle it before the line operation,
not after.

A record describing a modification carries a copy of the element from before the
change and a copy from after, so replay in either direction has a state to
restore rather than an instruction to re-derive.

## Scope

Some records name the line they affect; others are song-wide. A subscriber
receives the batch as an ordered list and can ask whether every line-scoped record
in it names the same line — which is the common case, and what lets a view
invalidate one line rather than the document.

Some edits have effects on lines their records do not name. For two of them the
model answers which lines those are, so a view can invalidate them too.

A key change can re-key lines beyond the one it names. See
[key-changes.md](key-changes.md).

A line draws the half of a tie that crosses into it from the line before, so an
edit near either edge of a line can change what the line beside it draws while
the record names only its own line — deleting a tied last note, inserting a
same-pitch note after an anchor, flipping the stem of a note a crossing tie
joins. The model answers which neighboring lines a record reaches that way from
the position it names: the next line when that position is at or after the
line's last element that is not a legal separator between tied notes, the
previous line when it is at or before the first, and both when the line has no
such element.

## Recording completeness

Undo replays the recorded batch mechanically, so **a change made inside a bracket
without being recorded is invisible to it** — and invisible is exact: the batch
still replays cleanly, with the unrecorded change simply missing. Nothing fails,
and the document is quietly wrong.

This is why dropping dependent state goes through the typed removals rather than
being done directly to the underlying collections, and why the obligation is
stated as a contract on the bracket itself rather than left to each caller to
remember.

## Suspension and replay are different things

Three mechanisms look similar and are not interchangeable.

**Suspension** records nothing at all: no batch, no undo entry, no modified flag,
and no tie rule, so a loaded tie is trusted. It is what a file reader runs under,
and what the document's own construction runs under — but it is not a load-only
mechanism, and live editing uses it too where a transient state should leave no
trace.

**Replay mode** is the opposite: records *are* still made. What it changes is that
the helpers apply raw state only, suppressing the companion side-work — terminal
maintenance, defaults, span invalidation, the tie rule, automatic removals,
merging — and
bypassing guards that mid-replay intermediate states legitimately violate. The
recorded batch already contains every change; re-deriving them would apply them
twice.

**The reentrancy guard** is narrower still: it keeps the batch a replay produces
from being pushed as a new undo step.

One thing is deliberately *not* suppressed during replay: re-pointing a span's
anchor when an element is replaced. It is self-inverting and required for span
references to stay valid.
