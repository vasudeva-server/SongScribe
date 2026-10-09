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
on its own during the edit, beyond what the user asked for. How the batch and the
report are delivered is stated once, on `ModificationSession.deliver`: the change
notification that carries the batch carries the edit's report, empty when the edit
changed nothing on its own. See
[change-report.md](change-report.md).

The record types form a closed set, so the inventory is checkable and a new kind
cannot be introduced without appearing in it. Span changes are recorded by one
addition record and one removal record whatever the span's kind; a tie, which is
note state rather than a span, has an addition and a removal of its own.

## Rules at the close of an edit

Some rules cannot be judged one change at a time, because what they judge is the
whole of what the edit did. These **modification rules** wait for the outermost
bracket, and each owns one kind of state that no single change can judge:

- the **tie rule** (`TieRule`) owns ties, whose end is derived from the notes
  around them and can cross a line break. See
  [span-invalidation.md](span-invalidation.md#ties-are-note-state-judged-when-the-edit-is-over).
- the **section rule** (`EndingSectionRule`) owns whether an ending's split
  closes an open repeat section, which reads the song across lines. See
  [span-invalidation.md](span-invalidation.md#an-endings-section-is-judged-when-the-edit-is-over).

Each rule states what it judges and how far it looks on its own class. When the
rules run, and when they do not, is stated once, on
`ModificationSession.endModification`; what a rule may assume and what it records
is stated on `ModificationRule.apply`; `ModificationRules` holds the order they
run in.

The set is closed: every rule is a rule of the model, and nothing registers one
from outside. A rule exists only for state that no single change can judge
against the projection of its own line, which is why each of the two reads
across lines. Work that runs after every change — keeping line keys consistent,
the accidental chain walk — is not a rule here.

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
undo is still correct because each rule judged the final state: everything a
removal names is present in that state, so reverse-order undo restores the
removals first, against the state they were judged in, and only then undoes the
changes that broke them. This is the one statement of that argument; the tie and
section rules both rest on it.

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
the record names only its own line. Which neighboring lines a record reaches that
way is stated on `Song.tieReach`.

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

**Suspension** records nothing at all: no batch, no undo entry, no modified flag
and no report. With no batch, no modification rule runs, so a loaded tie is
trusted. It is what a
file reader runs under, and what the document's own construction runs under — but
it is not a load-only
mechanism, and live editing uses it too where a transient state should leave no
trace.

**Replay mode** is the opposite: records *are* still made. What it changes is that
the helpers apply raw state only, suppressing the companion side-work — terminal
maintenance, defaults, span invalidation, the modification rules, automatic
removals, merging — and bypassing guards that mid-replay intermediate states
legitimately violate. The recorded batch already contains every change;
re-deriving them would apply them twice. A replay records no new changes into a
report: an undo delivers an empty report, and a redo delivers again the report of
the edit it applies.

**The reentrancy guard** is narrower still: it keeps the batch a replay produces
from being pushed as a new undo step.

One thing is deliberately *not* suppressed during replay: re-pointing a span's
anchor when an element is replaced. It is self-inverting and required for span
references to stay valid.
