# Key Signatures

How a key reaches a line, what a change to one costs, and why key edits reach
further than any other edit in the program. For the key-versus-signature naming
rule, see [vocabulary.md](../.claude/guides/vocabulary.md).

## There is no song-wide key

A song does not carry a key. **Every line has one** — either its own, or the key
in effect at the end of the line before it. The first line always establishes its
own, having nothing to inherit from.

That is the key invariant, and the song maintains it rather than callers doing
so. Every change that can move a key — a line's own key changing, a mid-line
change added, removed or edited, or a line inserted or deleted so that a later
line inherits differently — is brought back into line immediately after it
happens, in the one place all of them route through, undo and redo replay
included. See [mutations.md](mutations.md).

## Two representations, one question

A key change at a line boundary is the line's own key. A key change in the middle
of a line is an element sitting in the line's element list. These are two
different pieces of storage, and **nothing outside the line needs to know that**:
every consumer asks one question — what key is in effect at this position? — and
gets an answer that already accounts for both.

A line's *own* key is stored on the line. What it *inherits* is not: that is a
fact about where the line sits in the song, not about the line, so the song holds
it, keyed by line identity. A line removed from the song loses its entry and
cannot go on reporting what it inherited from a position it no longer occupies.

That query is **total**. A line that establishes no key and inherits none — one
not in a song, or any line while a file is part-way through loading — is in the
default key, which is the key a document naming no key anywhere is in, not a
stand-in for a broken invariant. Nothing about the key chain can break badly
enough to leave a caller without an answer.

## A line holds a key only where one changes

Setting a line's key to the key it would inherit anyway stores nothing: the line
is left inheriting. **"This line restates the key it already had" is deliberately
not representable.**

Every line's header draws its running key either way, so a keyed-but-equal line
would be invisible on screen while silently blocking inheritance — an upstream
edit meant to reach every line downstream would stop there, with nothing visible
to explain why.

## The stopping rule

Re-deriving inherited keys after an edit walks forward from the line that changed
and **stops at the first line establishing a key of its own**: that line's running
key cannot have moved, so nothing past it can either.

A line holding a *mid-line* change but no key of its own does not stop the walk.
Its own running key can still move, because it is not keyed — even though the
mid-line change itself, being an absolute key rather than a transposition, goes on
drawing the same accidentals regardless.

```
                 line 0   line 1   line 2            line 3   line 4   line 5
own key:         C        —        —                 —        D        —
mid-line:                          → G
inherited:       —        C        C                 G        G        D
runs (start):    C        C        C                 G        D        D
runs (end):      C        C        G                 G        D        D
```

None of lines 1–3 establishes a key, so the walk passes through all three — line
2 included, whose mid-line change is separate storage from its (absent) own key
and stops nothing by itself. The walk stops *after* line 4: that line's inherited
entry is still brought up to date before the check, but because it has a key of
its own the entry is never read, and line 5 needs nothing re-derived.

**This one rule governs five things that never call each other**: inherited-key
propagation, how far accidental reconciliation reaches, where a cautionary is
derived, which lines a view must re-solve, and which measure a key is written into
on save. Stating it once is the point; five restatements would drift.

## An edit moves more lines than it names

A change is recorded against one line, and a view caching per-line geometry
invalidates that line. A key move is the one edit for which that is not enough,
because a line's geometry is solved from keys that are not its own storage: its
header signature and the spacing behind it come from the key it *runs* in, which
the previous line may have supplied, and the room kept clear at its end comes from
the cautionary it leads into, which the *next* line decides.

So the lines needing a re-solve run from one line before the change through the
end of the inheritance chain. That set is derived from the same enumeration of
key-moving changes that drives propagation, so the two cannot disagree about what
moves a key. A second enumeration would fail quietly — the line ahead of the
change would redraw its cautionary from the live document while keeping the
spacing it was solved with, and an inheriting line would go on drawing the key it
was in before the edit.

## A mid-line change always follows a barline

A mid-line key change is never the first element of a line, and always sits
immediately after a barline or repeat.

The user is not restricted to positions that already satisfy this: inserting a key
change elsewhere inserts a barline in front of it as part of the same edit, and
the two are deleted together. Every path that can create one **keeps** the
invariant rather than checking it afterwards — the editing UI, the deletion
pairing, and the file reader, which rejects a document violating it.

The pair is closed. Neither half may be written over, and nothing may be inserted
between them. Both questions — may I write over what is here, may I write in
front of it — are asked once each by everything that puts content on a line, so
note entry and click-placement get the same answer rather than each restating the
rule. The same closure protects a grace note and the note it decorates: they mean
nothing apart, so nothing goes between them either.

## What a change draws depends on where it is

A key signature's drawn width is never a property of the key alone: changing to a
key with one sharp draws one sharp coming out of a key with no accidentals, and a
cancelling natural *plus* that sharp coming out of a key with flats.

So the width is a function of **two** keys, and one answer serves everything that
needs it — the column the layout reserves, the glyphs drawn, the double-click
target, and the rectangle that selects it. A signature on a line reads the key it
changes from off that line every time, so its extent tracks the line around it.
One on no line has no line to ask, and answers only if it was told the key when it
was built — which is how an edit is sized before it is committed, and how a pasted
fragment's key change is measured against the key in effect where it lands. Asked
with neither, it refuses rather than guessing: a guessed width is too narrow for
every change that cancels, and it would be written into the document as though it
had been measured.

**The cancellation policy:** cancelling naturals are drawn ahead of the new
signature exactly when the previous key had accidentals of its own *and* the new
key uses a different accidental type — sharps to flats, flats to sharps, or either
down to none. Widening or narrowing within one type draws the new signature alone,
and so does starting from a key that had nothing to cancel. Where naturals are
drawn they always come first, so a caller can read the policy's outcome by looking
at what comes first rather than re-implementing the policy to find out.

## The cautionary is rendering only

When a line ends in a different key from the one the next line begins in, a
cautionary signature is drawn at the end of the current line, warning the
performer ahead of the turn. It is **derived** — stored nowhere, written to no
file — and re-derived on every paint. It is still editable: double-clicking it
edits the *next* line's key, which is the change it depicts.

One answer states what it draws, how much room layout keeps clear, and where each
part lands; three consumers read that answer and none derives its own, because a
second derivation would fail silently as a hit target off the glyphs or a reserved
span the renderer does not fill.

A cautionary is a barline followed by a run of accidentals, and it draws its own
barline **only when the line does not already end in one** — a line that does ends
in one barline, not two. On a line that already overflows, the trailing space it
would occupy sits behind the last element rather than at the true line end, so
drawing there would collide with the music; it starts one lead-in past the
rightmost element instead, extending the overflow rather than sitting inside it.

The double-click target is the run of accidentals alone, never the barline ahead
of it: on a line ending in its own barline the cautionary draws none, and clicking
that element must go on selecting it.

## A change must reach a note

A mid-line key change is worth what its pitches are worth, so **every position
past a line's last note is refused**: the change would govern no note, saying
nothing the next line's own key does not already say, and the cautionary already
draws that key at this line's end.

The rule is stated against the last *note*, never the last *element*, and three
positions fall out of that single test without needing rules of their own: the end
of the line, the position before a trailing barline, and the position before a
trailing rest. The position of the last note itself is accepted — a change there
puts that note in the new key, which the next line's key cannot do. Grace notes
count, carrying a pitch like any other note.

Every position rule lives in the placement predicate, never in whether the command
is available. An unavailable position shows itself as an insertion marker that
disappears, not as a menu item that will not run.

## Changing a key changes pitches

A key change cancels accidentals the way a barline does: a later note at the same
staff position inherits nothing across it and falls back to the signature.

And because it moves what every uncovered note sounds, it is reconciled like any
other edit that disturbs accidental context — except in reach. Its reach is the
inheritance chain, which makes a key edit **the only edit in the program whose
reconciliation can span more than one line**, and the user sees one prompt for the
whole range rather than one per line. See [clipboard.md](clipboard.md) for the
reconciliation itself.

Four edits move a key and owe that reach: changing a line's own key, writing a
mid-line change, changing one already written, and deleting one. The sequence the
reach forces — reconcile the whole range, ask once, reconcile again under the
answer, record every line reached — is written in one place, and each edit
supplies only how its own head is handled.

A deletion computes its reach unconditionally rather than testing what it is about
to remove. A deletion removing no key change leaves the line's end key where it
was, so the walk reaches no following line and the reach is one line — the same
path, degenerate.

## An edit that would not fit is refused

A cautionary widens the previous line's trailing space; a mid-line change widens
its own line, along with any barline inserted beside it. Before either commits,
every line the change touches — the inheritance chain again — is measured, and the
edit is rejected rather than committed and left to overflow.

Every other path keeps the program's ordinary best-effort behaviour: a document
whose key change stops fitting after a page-size or font change renders
overflowing and flagged rather than being silently corrected or refused, because
refusing there would make an existing document unopenable.

This diverges deliberately from lyric editing, where an edit that leaves a line
overflowing is allowed to commit — the user may be shortening a syllable to
*recover* from an overflow, and refusing would block the fix. A key edit has no
equivalent recovery shape, so it is refused on an already-overflowing line too.

## A key is one signed number

A key is identified by its position on the circle of fifths: negative for flats,
positive for sharps, zero for neither. **There is no separate accidental-type
component.** The type and the count are one value, so no pair has to be held
consistent, and a key outside the possible range cannot be written down at all
rather than being rejected at run time.

Both the file format's and MIDI's encodings of a key signature are that same
number, which is why it is a domain fact rather than either format's: both
writers read it off the key rather than deriving it.

Keys are named for what they hold — one flat, five flats, three sharps — never for
a tonic. Tonic naming is display. Prose may still say "C major" where it explains
what a value means musically; that is explanation, not identity.

**Every key is major, and mode is not modelled.** The file format admits a mode,
so the writer emits one for the benefit of software reading our output, but the
reader ignores it: the provenance gate means no minor or modal key can enter in
the first place (see [musicxml-object-model.md](musicxml-object-model.md), *Only
SongScribe documents are read*). Nothing in rendering, spacing, accidental
resolution or editing has a mode dimension, and none should be added on the
strength of one caller wanting it — mode is out of scope for the feature, not
merely unimplemented.

The legacy format is the one place a key is still a pair of values, and the
conversion both ways stays with that reader, so the format's vocabulary does not
leak into the domain.
