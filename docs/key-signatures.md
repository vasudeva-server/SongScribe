# Key Signatures

This document states the settled rules for mid-line and per-line key signatures —
the ones that span more than one class, so no method's Javadoc can own them.
Method Javadoc links here rather than restating any of it; a paraphrase is a
second copy, and the second copy is the one that goes stale.

## There is no song-wide key

`Song` does not carry a key. Every line has one: either its own (`Line.getKey()`
non-null) or the key in effect at the end of the previous line
(`Line.getInheritedKey()`). Line 0 always establishes its own key — there is
nothing before it to inherit from. This is the key invariant, and `Song`
maintains it, not the caller: every mutation that can move a key — a line's own
key changing, a mid-line key signature being added, removed or edited, or a line
insertion or deletion that shifts what a later line inherits — is brought back
into line by `Song.applyChange` immediately after the mutator runs, undo and
redo replay included. See `docs/mutations.md`.

## Two representations, one query

A key change at a line boundary is the line's own `Key`. A key change in the
middle of a line is a `KeySignatureElement` sitting in the line's element list.
These are two different pieces of storage, and nothing outside `Line` needs to
know that: every consumer asks `Line.keyAt(int elementIndex)`, which returns the
line's own key overridden by the last `KeySignatureElement` at or before that
index. The bound is inclusive — a key signature at `elementIndex` is already in
effect there — and the domain runs `0..elementCount()` inclusive, so a caller
can ask what key an element about to be appended would land in.
`keyAt(0)` always equals the line's running key, because index 0 can never
itself hold a key signature (see below).

Two derived queries save a caller from re-deriving the edge cases:
`Line.getRunningKey()` is the key at the very start of the line, and
`Line.keyAtEndOfLine()` is the key in effect after the line's last element — the
key the next line inherits.

## The inheritance chain and its stopping rule

`Song` keeps every line's inherited key up to date by walking forward from the
line a mutation touched and stopping the moment it reaches a line that
establishes a key of its own: that line's running key cannot have moved, so
nothing past it can either. A line holding a *mid-line* change but no key of its
own does not stop the walk — its own running key can still move, because it has
nothing pinning it — even though the mid-line change itself, being an absolute
key rather than a transposition, keeps drawing the same accidentals regardless.

```
                 line 0      line 1      line 2                   line 3      line 4      line 5
own key:         C           —           —                        —           D           —
mid-line change:                         → G (after a barline)
inheritedKey:    —           C           C                        G           G           D
runs (start):    C           C           C                        G           D           D
runs (end):      C           C           G                        G           D           D
```

None of lines 1–3 establishes a key of its own, so the walk that re-derives `inheritedKey`
after an edit passes through all three — line 2 included, whose mid-line change is a
separate representation from its (null) own key and does not by itself stop anything. The
walk stops *after* line 4: line 4's `inheritedKey` field is still brought up to date (`G`,
from line 3's end) before the check, but because line 4 has a key of its own, that field is
never consulted — `getRunningKey()` reads the own key first — and the walk returns without
touching line 5, which needs nothing re-derived: its inherited key was already correct.

This single rule — *forward to the first line with its own key* — governs four
subsystems that never call each other and would otherwise each have had to
restate it: `inheritedKey` propagation above, accidental-reconciliation reach
(below), cautionary derivation (below), and where a MusicXML `<key>` gets
written (below). This document states it once; the picture is the one copy all
four can point at.

## A line holds a key only where one changes

`Line.setKey` normalizes: when the key passed in equals the key the line would
inherit anyway, the line's own key is set to `null` instead of to that value.
"This line restates the key it already had" is deliberately not representable.
Every line's header draws its running key either way, so a pinned-but-equal key
would be invisible in rendering while silently blocking inheritance: an upstream
edit meant to reach every line downstream would stop at the pinned line instead,
with nothing on screen to explain why.

## A mid-line key change is always preceded by a barline

A `KeySignatureElement` is never the element at index 0 of a line, and is always
immediately preceded by an element whose `ElementType.isBarLine()` or
`isRepeat()` is true — `KeySignatureElement`'s position invariant. The user is
not restricted to positions that already satisfy it: inserting a key signature
elsewhere inserts a `SINGLE_BARLINE` immediately before it, as part of the same
edit, and the two are deleted together. Every path that can create a mid-line
key signature keeps the invariant rather than checking it after the fact — the
editing UI, the deletion pairing, and the MusicXML reader, which rejects a
document whose mid-measure `<key>` has no barline before it.

## A key change cancels and propagates accidentals

`ElementType.cancelsAccidentals()` is true for `KEY_SIGNATURE` alongside every
barline and repeat: a later note at the same staff position inherits nothing
across a key change and falls back to the key signature instead, exactly as it
would across a barline.

Because a key change moves pitches — an accidental that read one way against
the old key signature can read differently against the new one — it is
reconciled the same way an added or removed barline is, so that every pitch the
user did not intend to change is preserved. Its reach is the inheritance chain
above: from the change point forward to the first line with its own key, which
makes a key-signature edit the only edit in the program whose reconciliation can
span more than one line. `AccidentalReconciliation` and `AccidentalRestatements`
take that range, not a single line, and the user sees one restatement prompt for
the whole range rather than one per line.

## The cautionary key signature is rendering only

When a line ends in a different key than the one the next line begins in, a
cautionary key signature is drawn at the end of the current line, warning the
performer ahead of the turn. It is derived — `LineKeys.of(Line)` compares
`keyAtEndOfLine()` against `nextLineRunningKey()` — stored nowhere on the
document, and never written to MusicXML. It is still editable: double-clicking
it edits the *next* line's key, which is the change it depicts.

### Where it sits

A line that fits reserves a trailing span for its cautionary:
`HorizontalSpacingCalculator.trailingReservationSs` widens the line's trailing
gap to the larger of the ordinary line rest and the cautionary's drawn width
plus `KeyChange.RIGHT_MARGIN_SS`, so the cautionary is right-aligned to the line
width less that margin. On a line that already overflows, that margin sits
behind the last element rather than at the true line end, so drawing there would
collide with the music; the cautionary instead starts one line rest past the
rightmost element's edge, extending the overflow rather than sitting inside it.
`LayoutEngine.positionTerminalFlushRight` skips an overflowing line's terminal
barline for the same underlying reason — flush-right positioning only makes
sense against a margin the line hasn't already passed. A key-edit hit target
follows the glyphs actually drawn, so it follows this placement rather than the
margin whenever the two diverge.

## The cancellation policy

`KeyChange.accidentals(previous, next)` draws cancelling naturals ahead of the
new signature exactly when `previous` had accidentals of its own *and* `next`
uses a different accidental type — sharps to flats, flats to sharps, or either
down to no accidentals. Two cases draw the new signature alone, uncancelled:
widening or narrowing within the same type (two more sharps, one fewer flat),
and starting from a key with no accidentals to begin with — there is nothing
there to cancel, so going from no accidentals into a key that has them draws
only the new signature too.

Naturals, when the policy calls for any, always come first in the returned
list — a caller (the MusicXML writer, deciding whether a `<cancel>` is owed)
can read the policy's outcome by asking whether the list opens with a natural,
rather than re-implementing the policy to find out.

## Interactive edits: refused when they would not fit, everywhere else best-effort

A cautionary widens the previous line's trailing reservation; a mid-line key
signature (and any barline inserted alongside it) widens its own line. Before
either edit commits, `KeyEditFitCalculator` asks the layout solver whether every
line the change touches — the inheritance chain above, again — still fits.
When one does not, the user is alerted and the edit is rejected rather than
committed and left to overflow.

Every other path keeps the program's existing best-effort behavior: a document
whose key change stops fitting after a page-size or font change renders
overflowing and flagged rather than being silently corrected or refused,
because refusing there would make an existing document unopenable.

This is a deliberate divergence from lyric editing, where an edit that leaves a
line overflowing is allowed to commit — the user may be shortening a syllable to
*recover* from an overflow, and refusing would block the fix. A key edit has no
equivalent recovery shape on an already-overflowing line, so
`KeyEditFitCalculator` refuses there too rather than only on edits that newly
cause it.

## MusicXML

A `<key>` is written into the measure where the key change actually takes
effect. For a line-boundary change, that is the *following* line's first
measure — the line whose header establishes the new key — never the previous
line, even though that line's end is where a cautionary rendering of the same
change may be drawn on screen. A `<cancel>` is written whenever the cancellation
policy calls for cancelling naturals (a type change, above), and ignored on
read: MusicXML documents can arrive with any cancellation convention, but only
SongScribe-authored files are read at all (`docs/musicxml-object-model.md`,
*Only SongScribe documents are read*), so the reader always derives cancellation
from the two keys itself rather than trusting what a writer claimed.

## MIDI

`MidiEventFactory.addKeySignatureEvent` emits a `FF 59` key-signature meta-event
at tick 0 and at every key change: `sf` is the signed fifths count from
`KeySignatureMapping.toFifths`, and `mi` (mode) is always the constant for major.

## Every key is major, and mode is not modelled

`Key` is a signature — a `KeyType` and an accidental count — with no mode
component; `KeyType` itself only distinguishes sharps, flats, and none. MusicXML's
`<key>` element accepts a `<mode>` child, so the writer emits `major` for the
benefit of software that reads our output, but the reader ignores it on the way
in: the provenance gate means no minor or modal key can enter in the first place
(`docs/musicxml-object-model.md`, *Only SongScribe documents are read*). Nothing
in rendering, spacing, accidental resolution, or the editing UI has a mode
dimension to account for, and none should be added on the strength of a single
caller wanting one — mode is out of scope for the whole feature, not merely
unimplemented.
