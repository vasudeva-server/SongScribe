# Song-Level Tempo

Background and rationale for the song-level tempo mark implemented for issue #740 — the design
decisions behind it, not a walkthrough of the code.

------------------------------------------------------------------------

## One representation, always present

The song's tempo is a single field, `Song.tempo`, non-null and seeded with `Tempo`'s defaults —
a brand-new blank score already has a tempo (`♩ = 120 Moderate`) before a single note exists.
There is no "no tempo" state and `Song.getTempo()` never returns null.

This replaces a design that mirrored the same value in two places: `Song.tempo` and a
`TempoChangeAttachment` sitting on the "anchor" — the first element of the song's first
non-empty line — kept in sync by machinery that re-attached the mark to whatever element became
first after every edit. That machinery is gone. A `TempoChangeAttachment` anywhere in the song
now — including on the element that happens to be first — is an ordinary tempo change, no
different in kind from any other. Nothing tracks "the first element" as a DOM-level concept
anymore.

------------------------------------------------------------------------

## Where it renders

The song tempo renders as a mark one notehead width right of line 0's staff header — always
line 0, even when line 0 is empty or the song has no notes at all. It reserves no horizontal
space, overhanging the music rather than pushing the first note right. It is non-interactive: never
selectable, never hit-tested, never a target for a click. The only way to change it is through
Song Settings → Music tab.

------------------------------------------------------------------------

## Why it copies `Attribution`, not `Attachment`

`Attribution` is the existing precedent for an ownerless, non-hittable, first-line-only,
collision-stacked decoration, and the header tempo mark (`SongTempoMark`) copies its mechanism
rather than being modeled as another kind of attachment.

The mark needs collision stacking — it has to be pushed up out of the way by ledger lines, high
notes, or anything else on the first column, and it has to reserve the vertical space it needs
by feeding into `contentAboveStaffSs` like every other stacked decoration. But it must never be
hit-testable. `HitRegionBuilder.addAttachments` only registers the five concrete `Attachment`
subtypes, so an ownerless `LineElement` — one with no owner and no attachment role — is simply
never offered to the hit-testing pass. The mark gets stacking without hit-testing by
construction, not by carrying a flag that says "don't hit-test me" that some future call site
could forget to check.

------------------------------------------------------------------------

## A marking always draws something

What a tempo draws is a `TempoMarking`, a sealed pair of records on the `Tempo`:

- **`Metronome`** — the glyph, an `=`, the speed, and then the description when it carries one.
- **`TextOnly`** — the description on its own, with no glyph and no speed. The speed and the beat
  unit still live on the `Tempo`; they are not drawn, and they still drive beaming and playback.

The "Show only description" checkbox in the tempo panel chooses between the two. It is not a
switch for hiding the tempo mark: a `TextOnly` still renders, just without the glyph and the
number.

**The marking that would draw nothing is unrepresentable.** No glyph and no text is not one of the
two cases: a `Metronome` always begins with a glyph, and `TextOnly`'s constructor throws on blank
text. So nothing downstream asks whether a tempo is visible, and no guard anywhere repeats the
rule. `MetronomeContent.forTempo` states the consequence as a result invariant — its width is
never zero — and `SystemStacker.stackTempoMark` stacks whatever it builds with no zero-width case.
For how the content is built and why the whole marking is typeset in layout rather than at paint
time, see [Metronome Typesetting](metronome-typesetting.md).

That leaves two places where a caller could still *try* to describe the pair, and each answers it
in the way its own inputs allow:

- **The dialog cannot describe it.** `TempoSection.getTempo()` would build a `TextOnly` from a
  blank description and throw, so two bindings keep the controls out of that pair, one per
  direction: the checkbox is disabled while the description is blank, and the description combo's
  `(none)` row is barred while the checkbox is checked.

  Barring a row means refusing it, not painting it grey. `OtherValueComboBox` installs a
  selection model on the list its drop-down shows, so a barred row can never become that list's
  selection — which is what the Enter key commits. Its `setSelectedIndex` override covers the
  one route that does not go through the drop-down: an arrow key pressed while the drop-down is
  closed.

- **A file that states it is repaired.** A document states the two values separately — a
  description, and a flag asking for the metronome to be left out — so it can state the pair that
  the type cannot hold. `TempoMarking.fromFile` is where those two become one marking, and it
  repairs the pair to a `Metronome` rather than discarding the tempo: only the flag is wrong, and
  the beat unit and speed beside it are good. A discarded song tempo would silently revert to the
  defaults, and a discarded tempo change would vanish. It strips the description first, so text of
  whitespace alone counts as none. Both readers — `MeasureMapper.buildTempo` and
  `TempoIO.TempoReader` — go through it and log the repair in their own words.

------------------------------------------------------------------------

## The first-element rule is UI-only

Creating a tempo change on the song's first element is forbidden, but only as a UI-only rule
enforced in `SelectionCoordinator.canChangeTempo()` — the command to add a tempo change there is
simply unavailable. The DOM itself does not track or defend this at all: if an edit makes an
element that already carries a `TempoChangeAttachment` become the song's new first element, that
attachment is left exactly where it is and renders as an ordinary tempo change sitting on the
first note.

This was a deliberate choice over DOM-side tracking. Following "the first element" across every
edit — deletes, line deletes, pastes, insertions — and keeping some attachment in sync with it is
precisely the machinery this design removed. Re-adding it in a different guise, even a smaller
one that only prevented an illegal state rather than actively transferring a tempo, would bring
back the same category of bug surface (undo brackets that have to span the transfer, edge cases
around emptied lines, cross-line displacement) that motivated removing it in the first place. A
UI-only guard costs one method and produces the same practical outcome — a user cannot casually
create a first-element tempo change through the normal editing commands — without any of that
machinery.

------------------------------------------------------------------------

## The MusicXML contract

**Write.** The song tempo is written as a `<direction>` that is the first child of the first
`<measure>`, ahead of `<print>` and `<attributes>`, bound to no note. It is emitted even for a
song with no notes at all. This is legal under the schema: `%music-data;`
(`docs/musicxml-4.0-schema/score.mod:402-405`) is an unordered `*` alternation, so nothing
requires `<attributes>` or a note to precede a `<direction>`.

**Read.** The first tempo `<direction>` found in the first `<measure>` becomes the song-level
tempo and produces no `TempoChangeAttachment`, regardless of where in that measure it actually
sits. This is what makes files written before this change — which placed the tempo direction
bound to the first note rather than as the measure's first child — continue to load correctly:
the reader doesn't care about position within the measure, only that it's the first tempo
direction found there. Any later tempo direction, including a second one still inside measure 1,
binds to the following note as an ordinary tempo change, exactly as it always has.

------------------------------------------------------------------------

## `Tempo` is a value

`Tempo` is a record: immutable, compares by value, and copied by handing back the same
instance. `Song.setTempo`, `Song.tempoDidChange` and `SongSettingsController.applyTempo` all
compare two tempos with `equals` before recording a change and running the beat-defining
machinery a tempo edit can trigger, and `TempoChangeController` inherits the same comparison
from `AttachmentDialogController` rather than stating its own.

`Tempo.haveSameBeat` stays, and is a narrower question than equality: the beat is the tempo
*type* alone, so a BPM or marking edit answers `true` and skips the tuplet revalidation that a
beat change forces.

**Every value-ish DOM type compares by value.** `Annotation`, `Tempo`, `BeatChange`,
`SongMetadata` and `SongAttribution` are records, `Key` and `Duration` are enums, and each
`TempoMarking` case is a record. A new type of this kind is a record.

This is a rule about DOM value types, not about every holder in the codebase. `DocumentFonts`
is a mutable holder the Fonts tab edits in place and it defines value equality anyway; it is a
dialog-side accumulator rather than something the document stores, and it never enters a
hash-based collection.
