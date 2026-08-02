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
  `isEligibleForLyric`, `LyricConnectorLayout.verseIndex`) rather than defaulting
  to 1 — the active verse will not always be 1.

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
