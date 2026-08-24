# Key Signatures

This document states the settled rules for mid-line and per-line key signatures —
the ones that span more than one class, so no method's Javadoc can own them.
Method Javadoc links here rather than restating any of it; a paraphrase is a
second copy, and the second copy is the one that goes stale.

## Naming: key versus signature

**A `Key` is the key. A key signature is what gets drawn from it.** The notator
does not think of changing the signature; they think of changing the key, and the
signature follows. Every name in this feature is chosen against that rule, and it
applies to user-facing strings as much as to code:

- **`Key`, `KeyChange…`** — the value, and anything that establishes or edits it.
  `KeyChangeElement` is the mid-line change, `KeyChangeDialog` and
  `KeyChangeAction` edit a key, `KeyDisplay` names one for the user. On screen the
  notator reads "Key Change", never "Key Signature Change".
- **`KeySignature…`** — only what is rendered. `KeySignature` is the header's
  positioned layout box; `KeySignatureRenderer` paints it. Nothing else may take
  the name.

The tell that a name is wrong is that it describes the value or the edit while
using "signature", or describes glyphs on a staff while using "key". Where the
words genuinely describe drawn accidentals — "· 5 flats" in a key's display name
— "signature" stays correct.

## There is no song-wide key

`Song` does not carry a key. Every line has one: either its own (`Line.getKey()`
non-null) or the key in effect at the end of the previous line. Line 0 always
establishes its own key — there is nothing before it to inherit from. This is
the key invariant, and `Song` maintains it, not the caller: every mutation that
can move a key — a line's own
key changing, a mid-line key signature being added, removed or edited, or a line
insertion or deletion that shifts what a later line inherits — is brought back
into line by `Song.applyChange` immediately after the mutator runs, undo and
redo replay included. See `docs/mutations.md`.

## Two representations, one query

A key change at a line boundary is the line's own `Key`. A key change in the
middle of a line is a `KeyChangeElement` sitting in the line's element list.
These are two different pieces of storage, and nothing outside `Line` needs to
know that: every consumer asks `Line.keyAt(int elementIndex)`, which returns the
line's own key overridden by the last `KeyChangeElement` at or before that
index. The bound is inclusive — a key signature at `elementIndex` is already in
effect there — and the domain runs `0..elementCount()` inclusive, so a caller
can ask what key an element about to be appended would land in.
`keyAt(0)` always equals the line's running key, because index 0 can never
itself hold a key signature (see below).

Two derived queries save a caller from re-deriving the edge cases:
`Line.getRunningKey()` is the key at the very start of the line, and
`Line.keyAtEndOfLine()` is the key in effect after the line's last element — the
key the next line inherits.

## Where the inherited key is stored

A line's *own* key is on the line. What it *inherits* is not: `Song` holds it, in
an `IdentityHashMap<Line, Key>` keyed by line identity, because what a line
inherits is a fact about where it sits in the line list rather than about the
line. `Song.runningKeyAt(line)` is the query, and `Line.getRunningKey()` is the
per-line delegate that bottoms out in it.

`runningKeyAt` is **total**. A line that establishes no key and inherits none —
one not in the song, or any line while a reader is part-way through a load — is
in `Key.DEFAULT`, which is the key a document naming no key anywhere is in, not a
stand-in for a broken invariant. Nothing about the key chain can be broken badly
enough to leave a caller without an answer.

A line removed from the song loses its entry, so it cannot go on reporting what
it inherited from a position it no longer occupies.

## The inheritance chain and its stopping rule

`Song` keeps every line's inherited key up to date by walking forward from the
line a mutation touched and stopping the moment it reaches a line that
establishes a key of its own: that line's running key cannot have moved, so
nothing past it can either. A line holding a *mid-line* change but no key of its
own does not stop the walk — its own running key can still move, because it is
not keyed — even though the mid-line change itself, being an absolute
key rather than a transposition, keeps drawing the same accidentals regardless.

```
                 line 0      line 1      line 2                   line 3      line 4      line 5
own key:         C           —           —                        —           D           —
mid-line change:                         → G (after a barline)
inherited:       —           C           C                        G           G           D
runs (start):    C           C           C                        G           D           D
runs (end):      C           C           G                        G           D           D
```

None of lines 1–3 establishes a key of its own, so the walk that re-derives the inherited
keys after an edit passes through all three — line 2 included, whose mid-line change is a
separate representation from its (null) own key and does not by itself stop anything. The
walk stops *after* line 4: line 4's entry is still brought up to date (`G`, from line 3's
end) before the check, but because line 4 has a key of its own, that entry is never
consulted — `getRunningKey()` reads the own key first — and the walk returns without
touching line 5, which needs nothing re-derived: its inherited key was already correct.

This single rule — *forward to the first line with its own key* — governs five
subsystems that never call each other and would otherwise each have had to
restate it: inherited-key propagation above, accidental-reconciliation reach
(below), cautionary derivation (below), view invalidation (below), and where a
MusicXML `<key>` gets written (below). This document states it once; the picture
is the one copy all five can point at.

### An edit that moves a key invalidates more lines than it names

A mutation is recorded against one line, and a view that caches per-line
geometry invalidates that line. A key move is the one edit for which that is
not enough, because a line's geometry is solved from keys that are not its own
storage: its header signature and the spacing behind it come from the key it
*runs* in, which the line before it may have supplied, and the room kept clear
at its end comes from the cautionary it leads into, which the line *after* it
decides. So the lines needing a re-solve run from one line before the change
through the end of the inheritance chain.

`Song.keyMoveReach(Mutation)` is that set — every line whose running key the
mutation moved, plus the line before the first of those — and it is derived
from the same enumeration of key-moving mutations that drives the propagation
above, so the two cannot disagree about what moves a key. A second enumeration
would fail quietly: the line ahead of the change redraws its cautionary from
the live document (`LineRenderer` derives it per paint) while keeping the
spacing it was solved with, and an inheriting line goes on drawing the key it
was in before the edit.

Only the propagation and this query read that enumeration; the reconciliation
reach is computed *before* its edit commits, from the projected keys, and is
`AccidentalReconciliation`'s to derive.

## A line holds a key only where one changes

`Line.setKey` normalizes: when the key passed in equals the key the line would
inherit anyway, the line's own key is set to `null` instead of to that value.
"This line restates the key it already had" is deliberately not representable.
Every line's header draws its running key either way, so a keyed-but-equal line
would be invisible in rendering while silently blocking inheritance: an upstream
edit meant to reach every line downstream would stop at that line instead, with
nothing on screen to explain why.

## A mid-line key change is always preceded by a barline

A `KeyChangeElement` is never the element at index 0 of a line, and is always
immediately preceded by an element whose `ElementType.isBarLine()` or
`isRepeat()` is true — `KeyChangeElement`'s position invariant. The user is
not restricted to positions that already satisfy it: inserting a key signature
elsewhere inserts a `SINGLE_BARLINE` immediately before it, as part of the same
edit, and the two are deleted together. Every path that can create a mid-line
key signature keeps the invariant rather than checking it after the fact — the
editing UI, the deletion pairing, and the MusicXML reader, which rejects a
document whose mid-measure `<key>` has no barline before it.

The pair is closed. Neither half may be written over — replacing the key signature
would drop the key it carries, which nothing else on the line records, and replacing
its barline would leave the key signature preceded by whatever arrived — and nothing
may be inserted between them, whoever is placing. `Line.canReplaceElementAt` answers
the first, for the pointer over either half; `Line.canInsertElementAt` answers the
second, since the insertion slot in front of a key signature *is* the gap between it
and its barline.

Those two methods are the pair of questions a line answers about a position — may I
write over what is here, may I write in front of it — and each is one method, asked by
every way content reaches a line rather than restated per operation. Note entry asks
in `PreviewElementManager.trackMouse`: the ghost preview is hidden and the click does
nothing. Everything placed by clicking — a pasted fragment, another key change — asks
in `InsertionPointMode.updateTarget`, alongside the staff-header and right-edge
exclusions, so the insertion marker disappears over the gap.

`canInsertElementAt` also refuses the slot inside a grace-note pair, on the same
reasoning: a grace note and the note it decorates mean nothing apart, so nothing goes
between them either. Both pairs are refused for every operation, which is what keeps a
paste from splitting a pair that note entry may not split.

## What a mid-line key change draws

A key signature's drawn width is never a property of the key alone: changing to G
major draws one sharp coming out of C major and a cancelling natural plus that
sharp coming out of D major. `KeySignatureExtent` is that pair of keys, and it is
the one answer to both questions — `KeyChangeElement.drawnAccidentals()` is its
accidentals and `getContentWidthSs()` is its width, so the cancelling naturals the
policy calls for are drawn and the column reserved for them is exactly as wide as
the run. The cautionary at the end of a line reads the same type.

A key signature on a line reads the key it changes from off that line, every time,
so its extent tracks the line around it. One on no line has no line to ask, and
answers only if it was told the key when it was built
(`KeyChangeElement.forMeasurement`) — which is how an edit is sized before it is
committed, and how a pasted fragment's key signature is measured against the key in
effect where it lands. Asked with neither, it throws rather than guessing: a guessed
width is too narrow for every change that cancels, and it would be written into the
document as though it had been measured.

Four subsystems read that one answer — `ElementColumnBuilder` for
the column, `KeySignatureRenderer.renderMidLine` for the glyphs,
`LayoutHitTester.hitTestMidLineKeyEdit` for the double-click target, which is
that same column, and `ElementHitGeometry` for the click rect that selects it.
`ElementColumnBuilder` and `ElementHitGeometry` reach it through
`StaffElement.getGlyphWidthSs()`, which every other element answers from its own
type: a key signature is the one whose drawn width the type cannot state, so its
type width is only a floor and a rect built from it would leave every accidental
past the first unclickable.

### Where it sits

A mid-line key signature stands `StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS` behind the
barline its position invariant puts in front of it — the same padding a cautionary leaves
between its barline and its first accidental, so a key signature reads the same wherever it
falls. Ahead of it the ordinary line rest applies, so the following note clears the last
accidental the way it would clear any other element.

That padding is an exact distance rather than an ideal. The gap is
`HorizontalSpacingCalculator.GapKind.BARLINE_TO_KEY_SIGNATURE`, which gives it the padding
as its rest *and* as its collision floor, which freezes it — the solver never compresses a
gap below its floor — and marks it lift-exempt, so it takes no share of the lyric lift. The
accidentals therefore sit the same distance behind their barline on a crowded, lyric-heavy
line and on an empty one. See `docs/layout-geometry.md` for the spring recipe this is one
kind of.

It is painted from the line's ordinary element loop, dispatched by
`StaffElementRenderer` alongside rests, barlines and breath marks, in the color that loop
set. A mid-line key change is an element of the line, so selection, hover and
playback color it exactly as they color its neighbors. The header signature and
the cautionary have no such caller and set the element color themselves.

## The notator clicks where the key changes

Key Change does not open a dialog. It enters `InsertionPointMode` — the same
"click to place" interaction a paste with no selection uses — with `KeyChangeAction`
as the client. The mode raises its banner over the score, reading "Insert key change /
Click or Return to insert, Esc to cancel"; `KeyChangeAction.overlayText()` supplies
that wording and nothing else about the banner. See section 5 of `docs/clipboard.md`
for the interaction itself.

The two halves are strictly sequential. Choosing the point only records it;
`KeyChangeAction` opens the dialog from `insertionPointModeDidEnd`, after the mode has
taken the banner and the marker down. Opening it from the callback that chose the point
would put a modal dialog in front of a banner still saying "Click or Return to insert,
Esc to cancel". Paste is the other way round for the same reason: its dialog is a
retryable "line full" error, and the banner staying up is what tells the user to pick
again.

Every position rule this operation has lives in `acceptsInsertionIndex`, never in
the action's enablement. An unavailable position shows itself as a marker that
disappears, not as a command that will not run.

## A mid-line key change must reach a note

A mid-line key change is worth what its pitches are worth. Every position past a
line's last note is refused: the change would govern no note, so it says nothing
the next line's own key does not say, and the cautionary already draws that key
at this line's end.

The rule is stated against the last *note*, never the last *element*.
`Line.lastNoteIndex()` is the one query, and `KeyChangeAction.acceptsInsertionIndex`
is the one caller. Three positions fall out of the single test, and none of them
needs a rule of its own: the end of the line, the position before a trailing
barline, and the position before a trailing rest. The index of the last note
itself is taken — a change there puts that note in the new key, which the next
line's key cannot do, because it leaves that note in the old one.

Grace notes count. A grace note carries a pitch, so a key change ahead of a
trailing grace note reaches it.

## A key change cancels and propagates accidentals

`ElementType.cancelsAccidentals()` is true for `KEY_CHANGE` alongside every
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

Four edits move a key and therefore owe that reach: changing a line's own key,
writing a mid-line key signature, changing the key of one already written, and
deleting one. `KeyChangeReconciliation` is the one place the sequence the reach
forces is written — reconcile the whole reach, ask once, reconcile again under
the answer, record every reached line — and each edit supplies only how its own
head is reconciled: an insertion, a replacement and a deletion each project the
line they land on, while a line-key change modifies it in place.

A deletion computes its reach unconditionally, from
`Line.keyAtEndOfLineAfterRemoving`. A deletion that removes no key signature
leaves the line's end key where it was, so the walk reaches no following line and
the reach is the one line — the same code path, degenerate. Nothing tests what
the deleted range holds. That deletion's prompt covers the elements going away
and the accidentals the reconciliation clears, together, so one Delete asks once.

## The cautionary key signature is rendering only

When a line ends in a different key than the one the next line begins in, a
cautionary key signature is drawn at the end of the current line, warning the
performer ahead of the turn. It is derived — `LineKeys.of(Line)` compares
`keyAtEndOfLine()` against `nextLineRunningKey()` — stored nowhere on the
document, and never written to MusicXML. It is still editable: double-clicking
it edits the *next* line's key, which is the change it depicts.

### What it draws, and where it sits

`CautionaryKeySignature` is the one answer to what a cautionary draws, how much
room layout keeps clear for it, and where each part of it lands. Three
subsystems read that answer and none derives its own: the trailing reservation
in `HorizontalSpacingCalculator`, the painting in `KeySignatureRenderer`, and
the double-click target in `LayoutHitTester`. A second derivation would fail
silently — a hit target off the glyphs, or a reserved span the renderer does not
fill.

A cautionary is a barline followed by a run of accidentals, laid out like this
between the ink of the line's last element and the staff's right edge:

```
… last element  [line rest]  |  [padding]  ♯♯  [padding] │ staff right edge
```

The barline is the same one a mid-line key change stands behind, and it follows
the same rule: the cautionary draws one only when the line does not already end
in a barline or a repeat. A line that does ends in one barline, not two, and its
lead-in is the padding alone — the line rest ahead of a drawn barline separates
the music from a barline that is not there, and the rest before the line's own
barline is already part of the line's own spacing.

The padding either side of the accidentals is
`StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS`, the same value on both sides, so
the signature reads as one unit rather than leaning toward one of its neighbours.
The line rest ahead of a drawn barline is the song's own, which is what
`HorizontalSpacingCalculator.GapKind.NORMAL` gives a mid-line barline, so music
clears a barline by the same distance wherever the barline is.

A line that leads into a cautionary takes that whole run as its trailing gap:
`HorizontalSpacingCalculator.trailingReservationSs` returns
`CautionaryKeySignature.reservationSs()` in place of the ordinary line rest,
never the larger of the two, since the cautionary's own lead-in already carries
the separation the last element is owed and a maximum would leave a narrow
cautionary further from its barline than the padding. The accidentals are then
right-aligned to the line width less the padding. On a line that already
overflows, that padding sits behind the last element rather than at the true
line end, so drawing there would collide with the music; the cautionary instead
starts one lead-in past the rightmost element's edge, extending the overflow
rather than sitting inside it. `LayoutEngine.positionTerminalFlushRight` skips
an overflowing line's terminal barline for the same underlying reason —
flush-right positioning only makes sense against a margin the line hasn't
already passed.

The double-click target is the run of accidentals alone, never the barline
ahead of it: on a line that ends in its own barline the cautionary draws no
barline, and clicking that element must go on selecting it rather than opening
a key dialog.

## The cancellation policy

`Key.accidentalsFrom(sourceKey)` draws cancelling naturals
ahead of the new signature exactly when `sourceKey` had accidentals of its own *and*
`targetKey` uses a different accidental type — sharps to flats, flats to sharps, or either
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
at tick 0 and at every key change: `sf` is `Key.fifths()`, and `mi` (mode) is
always the constant for major.

## A key is one signed number

`Key` is an enum of fifteen constants, each identified by its position on the
circle of fifths: negative for a flat key, positive for a sharp key, zero for
`NO_ACCIDENTALS`. There is no accidental-type component. The type and the count
are one value, so no pair has to be held consistent and no key outside the
fifteen is expressible — an eight-sharp key cannot be written down, rather than
being rejected at run time.

MusicXML's `<fifths>` and MIDI's `sf` byte are the same encoding, which is why it
is a domain fact rather than either format's: both writers read `Key.fifths()`
off the key instead of deriving it, and the MusicXML reader turns one back into a
key with `Key.ofFifths`.

The constants are named for what they hold — `ONE_FLAT`, `FIVE_FLATS`,
`THREE_SHARPS` — never for a tonic. Tonic naming is display, and belongs to
`KeyDisplay` and its `FLAT_TONICS` / `SHARP_TONICS` tables. Prose may still say
"C major" where it explains what a value means musically; that is explanation,
not identity. `Key.DEFAULT` is the one alias, because "the key a song starts in"
is a policy and must not read as "five flats specifically".

The legacy `.mssw` format is the one place a key is still a pair, in its
`<keytype>` and `<keys>` tags. `songscribe.io.LegacyKeyType` owns those three
names and the conversion both ways, so the format's vocabulary stays in the
reader: when the domain owned the names, renaming a constant would have stopped
old files loading with nothing in the build to catch it.

## Every key is major, and mode is not modelled

`Key` has no mode component. MusicXML's
`<key>` element accepts a `<mode>` child, so the writer emits `major` for the
benefit of software that reads our output, but the reader ignores it on the way
in: the provenance gate means no minor or modal key can enter in the first place
(`docs/musicxml-object-model.md`, *Only SongScribe documents are read*). Nothing
in rendering, spacing, accidental resolution, or the editing UI has a mode
dimension to account for, and none should be added on the strength of a single
caller wanting one — mode is out of scope for the whole feature, not merely
unimplemented.
