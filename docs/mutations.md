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

The outermost bracket also holds the edit's **report**: the changes the song made
on its own during the edit, beyond what the user asked for. It is delivered after
the batch, and only when it is not empty. See [change-report.md](change-report.md).

The record types form a closed set, so the inventory is checkable and a new kind
cannot be introduced without appearing in it. Span changes are recorded by one
addition record and one removal record whatever the span's kind; a tie, which is
note state rather than a span, has an addition and a removal of its own.

## Rules at the close of an edit

Some rules cannot be judged one change at a time, because what they judge is the
whole of what the edit did. These **modification rules** wait for the outermost
bracket. There are three, and they run in a fixed order:

- the **tie rule** removes every tie that no longer joins two notes of the same
  pitch. It judges each line the batch touched and the line before each, since a
  tie from that line can end in the touched one. See
  [span-invalidation.md](span-invalidation.md#ties-are-note-state-judged-when-the-edit-is-over).
- the **section rule** removes every ending whose split no longer closes an open
  repeat section. It judges every ending in the song: an edit on one line can
  change the section of an ending any number of lines later. See
  [span-invalidation.md](span-invalidation.md#an-endings-section-is-judged-when-the-edit-is-over).
- the **beat rule** removes every tuplet that no longer fits the beat in effect
  where it stands. It runs only when a change in the batch redefines the beat — a
  song tempo whose beat changes, a tempo or beat change added, removed or edited,
  or a line holding one inserted or deleted — and judges from the earliest place the batch redefines
  it onward, together with the tuplet that place falls inside. A beat redefined
  there reaches nothing before it.

The set is closed: every rule is a rule of the model, and nothing registers one
from outside. Work that runs after every change — keeping line keys consistent,
the accidental chain walk — is not a rule here.

The rules run as the outermost bracket is about to close: after every change and
every accidental restamp in it has landed, before the batch is delivered, and
while the bracket is still open, so each removal they make is recorded in the
batch it closes and is undone with the edit. Each removal is also reported; see
[change-report.md](change-report.md). Each rule judges the song as the edit left
it, never what another rule removed.

They run only when the edit is a fresh one that recorded something:

- **not on an empty batch** — an edit that changed nothing broke nothing;
- **not during undo or redo replay** — the batch being replayed already carries
  the removals the edit made, and judging again would apply them twice;
- **not while tracking is suspended** — nothing is recorded, and a load judges
  what it reads by its own passes;
- **not when the edit threw part way** — its notes may not all be restamped, so a
  rule would judge a state the edit never meant to leave. The bracket still
  closes and the batch is still delivered.

Because the rules run after the fact, their removals **follow** the changes that
caused them in the batch — the reverse of the
[order below](#order-within-a-batch). Undo stays correct
because each rule judged the final state: everything a removal names is present
in that state, so reverse-order undo restores the removals first, against the
state they were judged in, and only then undoes the changes that broke them.

## Order within a batch

Undo replays a batch in reverse, and that makes emission order load-bearing in
one specific way.

**A record that removes dependent state is emitted before the change that
prompted it.** Reverse-order undo then restores the primary element *first*, so
when the dependent state is restored, whatever it was anchored to is alive again.
Emitting it the other way round would restore a span before the note it points
at.

**The removals of the [rules at the close of an edit](#rules-at-the-close-of-an-edit)
are the one exception.** Each is recorded after the change that caused it, and
undo is still correct because the rule judged the final state, which is the
state undo restores the removal against.

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
no modification rules and no report, so a loaded tie is trusted. It is what a
file reader runs under, and what the document's own construction runs under — but
it is not a load-only
mechanism, and live editing uses it too where a transient state should leave no
trace.

**Replay mode** is the opposite: records *are* still made. What it changes is that
the helpers apply raw state only, suppressing the companion side-work — terminal
maintenance, defaults, span invalidation, the modification rules, automatic
removals, merging — and bypassing guards that mid-replay intermediate states
legitimately violate. The recorded batch already contains every change;
re-deriving them would apply them twice. Nothing is reported: the edit reported
its changes when it was made.

**The reentrancy guard** is narrower still: it keeps the batch a replay produces
from being pushed as a new undo step.

One thing is deliberately *not* suppressed during replay: re-pointing a span's
anchor when an element is replaced. It is self-inverting and required for span
references to stay valid.
