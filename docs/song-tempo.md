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

## `shouldShowTempo` is not a visibility flag

`Tempo.shouldShowTempo()` corresponds to the Tempo Change dialog's "Show only the tempo
description" checkbox, inverted. When it is false, the mark still renders — just without the
glyph and the BPM number, description only. The mark disappears entirely only when its computed
content width is zero, which happens when `showTempo` is false *and* the description is empty.
It is never a switch for "hide the tempo mark" on its own.

That content width is `MetronomeContent.forTempo`'s `widthSs`, and it is where the two cases part:
with `showTempo` true the content always begins with a note glyph, so the width can never be zero;
with it false the content is the description alone, and an empty description appends no item at
all. `SystemStacker.stackTempoMark` reads the zero width and stacks nothing. For how that content
is built and why the whole marking is typeset in layout rather than at paint time, see
[Metronome Typesetting](metronome-typesetting.md).

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

## Why `Tempo` still has no `equals`/`hashCode`

`Tempo` is mutable — four setters — and `Song.tempoDidChange` mutates the live instance in place
rather than replacing it. Giving `Tempo` value equality would be unsafe the moment an instance
ever entered a hash-based collection (a `HashSet`, a `HashMap` key) and then had one of its
fields mutated out from under its stored hash code.

`Tempo.haveSameValue` is the single sanctioned way to compare two tempos by value instead, and it
now sits in `Song.setTempo` and `Song.tempoDidChange` — both need to tell whether an incoming
tempo actually differs from the current one before recording a change and running the
beat-defining machinery that a tempo edit can trigger. The same question for other mutable
value-ish DOM types is broader than this feature and is tracked separately as #747.
