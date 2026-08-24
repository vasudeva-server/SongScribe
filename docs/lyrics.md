# Lyrics and Verses

## Verses are languages, not stanzas

A song may carry several verses, but **only one verse is ever displayed at a
time**. Multi-verse support exists so a song can hold its lyrics in more than one
language; the user picks one, and that one is the active verse. Verses are not
stanzas stacked under the staff, and no feature should assume they are.

Consequences for any code that touches verses:

- There is exactly one active verse at any moment: `Song.getActiveVerse()`.
- Never design UI or layout that shows two verses at once, and never treat
  "verse 2" as "the row below verse 1".
- Verse indices are 1-based (`Lyric.FIRST_VERSE`). Verse 0 does not exist.
- Code that iterates verses is iterating *available languages*, not rows to
  paint. Pass the verse index explicitly (`getLyricForVerse`,
  `LyricConnectorLayout.verseIndex`) rather than defaulting to 1 — the active
  verse will not always be 1.

## Where the active verse lives

`Song` owns it. It is session state, not document state: it is deliberately not
written to MusicXML, so every file opens on its first verse. Changing it changes
what every line lays out, so a caller that sets it must invalidate layout
afterwards.

Each consumer reads it from the song rather than being told:

- `ElementColumnBuilder` reads it off the line, resolves each element's lyric for
  that verse once, and hands it to the column it builds.
  `buildDetachedColumn` takes the verse as a parameter, because a fragment clone
  is on no line and so cannot find its song.
- `LayoutEngine` passes it to `LyricLayoutBuilder.build`, which lays out that
  verse and no other.
- `LyricEditor` captures it once when the session opens, so a whole edit lands
  in one language even if the active verse changes meanwhile.

## The column is the verse

Past the column builder, nothing looks a lyric up on an element again. An element
holds every verse at once and cannot say which one is being laid out, so
`ElementColumn` carries the one lyric it was built from — `getLyric()`, with
`getSyllable()` reading its text and `getSyllableWidthSs()` holding the width
measured for it. The lyric is a constructor argument, so a column cannot exist
carrying text from one verse and a width measured for another.

Both later passes read it there: `HorizontalSpacingCalculator` for the grace–host
melisma it reserves room for, and `LyricLayoutBuilder` for the syllables, hyphens
and extenders it emits. A verse argument still reaches
`LyricLayoutBuilder.build`, but only to stamp the boxes and connectors it
produces; the words themselves come off the columns.

Switching the active verse therefore means rebuilding the columns. A layout pass
does this anyway, and tests that exercise a second verse must do the same rather
than reusing columns built for the first.

## Chain repair lives on `LyricRun`, not on `Line`

Two chains run through a verse's lyrics, both stored on the individual syllables
rather than as spans: the **syllabic chain** (`Lyric.Syllabic` + `compound()`,
which draws the hyphens) and the **melisma chain** (`Lyric.Extend`: a `START`,
text-less `CONTINUE` carriers, a closing `STOP`). Because a member names its
neighbors only by position, every edit that changes who a syllable's neighbors
are has to repair both.

Those rules — `adjustSyllablesForNeighborChange`, `adjustExtendsForDeletion`,
`backfillSyllabic`, `setSyllableBoundary`, `writeLyricForVerse`,
`syncGraceHostMelisma` and the rest — live on the `LyricRun` interface.
`Line implements LyricRun`, so every existing `line.someRepair(...)` call site is
unchanged; a run with no line uses `DetachedLyricRun`.

Only three things differ between the two, and they are all the interface leaves
abstract: how to reach an element (`getElement`), how many elements count
(`elementCount` and `effectiveElementCount` — a `Line` excludes its auto-maintained
terminal barline, a detached run has nothing to exclude), and what to do with a
repair once applied (`modifyElement`). `Line` records an `ElementModification` so it
can be undone; `DetachedLyricRun` records nothing, because a detached run is in no
document. Everything else, `isPairedGraceNote` included, is a `default` method
written once in terms of those.

**Adding a repair:** put it on `LyricRun`, route every write through
`modifyElement`, and it works on both. Never reach into `element.lyrics` from a
call site — the write would escape the mutation bracket and undo would not see it.

`LyricRun.endDanglingChains()` is the entry point for a run lifted out of a longer
one (`Fragment.capture`): it ends every chain that would otherwise point at an
element the run no longer contains. It is composed from the deletion repairs, since
"everything around this run is gone" is exactly what a deletion produces.

## What a chain runs through

Two facts about an element's type decide where a syllable can go and how far a
chain reaches, and everything about lyric chains and element types is one of
them:

- **bears syllable text** (`ElementType.bearsSyllableText()`) — a note or a
  grace note. Only these. A syllable is sung on a pitch, so a rest cannot hold
  one and neither can any structural marker.
- **bears a lyric chain** — everything but a repeat. A rest, a barline, a breath
  mark and a key change all carry a melisma or a hyphenated word onward without
  ever taking a syllable of their own. A repeat ends a section, so nothing runs
  through it. This one has no public predicate of its own; it is read only
  through `interruptsLyricChain()` below.

The type is only half of "can a syllable go here". The other half is the
grace-host pairing — a note with a paired grace note in front of it takes no
syllable, because that syllable is the grace's. `StaffElement.canBearSyllable`
states the two halves together, taking the element in front as a parameter, and
that is what every caller asks: `Line.canBearSyllableAt` supplies the neighbor
from a line, and `LyricLayoutBuilder` supplies it from the columns it is laying
out, so a run of elements belonging to no line — a clipboard fragment, a
projected insertion — gets the same answer as a live line.

**Inserting an element breaks the chains around it only when it interrupts
them** — `ElementType.interruptsLyricChain()`, which is the pair read together.
An element that carries a chain on but can never take a syllable is transparent:
the word and the melisma are left exactly as they were. Everything else
interrupts, for one of two opposite reasons. A note or a grace note is a syllable
slot that arrived empty, so the word can no longer be sung as one word. A repeat
carries nothing at all.

`LyricRun`'s two insertion repairs read that rule, and they judge the whole run
at once: one interrupting element in it breaks the chains for the run. The
glissando strip and the grace-host melisma sync in the same repair do not read
it — they are about which element a pairing points at rather than about who a
syllable's neighbors are, and a barline standing between a note and its
glissando target orphans it however transparent it is to a word.

Layout says the same thing, and has to: a column that bears no syllable leaves an
active extender running and a pending hyphen open, so a melisma is drawn straight
through a rest, a barline and a key change. The two walks that close out the end
of a line — `LyricLayoutBuilder.emitDanglingExtender` and `emitDanglingHyphen` —
pass over those columns for the same reason.

## The editor's chain rewrites live on `LyricChainEditor`

`LyricRun`'s repairs answer "an element changed, make the chains around it
well-formed again". The rewrites an open editor session *asks for* — clearing a
placeholder ends the word or gives up the carrier, `_` builds a chain backward from
a predecessor or forward to a new carrier — are a level above that, and live on
`LyricChainEditor`, constructed by `LyricEditor` with the line and the verse the
session captured.

Every one of them is written in terms of the `LyricRun` repairs and
`modifyElement`, takes element indices rather than components, and touches no
Swing, so it can be exercised without a text field. Only `buildBackwardChain` opens
its own modification bracket; the rest must be called inside one the editor has
already opened, because the editor pairs them with its own commit in a single
undoable step.

## One row, always

The lyrics band is one row deep, whatever the song carries. `LayoutResult` has a
single `lyricBaselineYSsInLine`, `lyricsBandHeightSs` reserves one row's ink, and
`hitTestLyric` tests that same row. A song with lyrics in three languages is
exactly as tall as one with lyrics in one.

The row is reserved even on a line with no lyrics yet, so entering the first
syllable does not re-space the song.

## Not built yet

There is no UI for choosing the active verse, so nothing calls
`Song.setActiveVerse` outside tests and every song shows verse 1. The picker —
along with naming verses by language and round-tripping those names through
MusicXML — is separate work.
