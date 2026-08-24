# Song Tempo

A song's tempo is **one always-present value on the song**. There is no "no
tempo" state: a brand-new blank score already has one before a single note
exists.

This is worth stating because the obvious alternative is wrong in a way that is
hard to see. Mirroring the value onto whatever element happens to be first means
tracking "the first element" as a concept the document model maintains across
every edit — deletes, line deletes, pastes, insertions — and keeping something in
sync with it. Nothing does that. A tempo change sitting on the first element is
an ordinary tempo change, no different in kind from any other.

The one rule that survives is enforced **only in the UI**: the command to add a
tempo change on the song's first element is unavailable. The document model does
not defend it. If an edit makes an element that already carries a tempo change
become the new first element, the change stays exactly where it is and renders as
an ordinary tempo change. A guard costing one method produces the same practical
outcome as tracking would, without the undo brackets spanning a transfer, the
emptied-line edge cases, or the cross-line displacement that tracking brings.

## It is drawn like the attribution, not like an attachment

The song tempo renders at the start of the first line — always the first line,
even when it is empty or the song has no notes at all. It reserves no horizontal
space, overhanging the music rather than pushing the first note right, and it is
never selectable, never hit-tested, never a click target. It is changed only
through song settings.

It needs collision stacking, because high notes and ledger lines have to push it
out of the way and it has to reserve the vertical room it takes. But it must
never be hit-testable. Hit-testing only ever offers up things that are attachments
to a note, so a mark that belongs to no element is simply never offered — it gets
stacking without hit-testing **by construction**, rather than by carrying a flag
saying "skip me" that some later call site could forget to check.

That is why it copies the attribution's mechanism: the attribution is the existing
precedent for an ownerless, non-hittable, first-line-only, collision-stacked
decoration.

## A marking always draws something

What a tempo draws is one of two shapes: a metronome marking — glyph, equals,
speed, and a description when it has one — or a description on its own. The
speed and beat unit still exist in the second case; they are not drawn, but they
still drive beaming and playback.

**The marking that would draw nothing is unrepresentable.** No glyph and no text
is not one of the two shapes, so nothing downstream asks whether a tempo is
visible and no guard anywhere repeats the rule. Layout states the consequence as a
result invariant: a marking's width is never zero.

That leaves two places a caller could still try to describe the empty pair, and
each answers in the way its own inputs allow. **The dialog cannot describe it** —
two bindings keep the controls out of that pair, one per direction, and barring a
row means refusing it rather than painting it grey, so it can never become a
selection the keyboard commits. **A file that states it is repaired** rather than
discarded — a document holds the description and the "leave the metronome out"
flag as two separate values, so it can state a pair the type cannot hold. Only
the flag is wrong; the beat unit and speed beside it are good. Discarding instead
would silently revert a song tempo to defaults and make a tempo change vanish.

For how the marking is measured and carried to the renderer, see the metronome
section of [line-layout.md](line-layout.md).

## Value types compare by value

A tempo is a value: immutable, compared by equality, copied by handing back the
same instance. Edits compare before recording a change, so committing an
unchanged tempo records nothing.

**Every value-ish type in the document model compares by value**, and a new one
of that kind is a record. This is a rule about document values, not about every
holder in the codebase — a mutable accumulator a dialog edits in place is a
different thing, even when it defines equality anyway.

Asking whether two tempos share a *beat* is a narrower question than equality: the
beat is the tempo type alone, so a speed or wording edit answers yes and skips the
tuplet revalidation a real beat change forces.
