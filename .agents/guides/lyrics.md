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

- `ElementColumnBuilder` reads it off the line and measures each column's
  syllable in that verse. `buildDetachedColumn` takes it as a parameter, because
  a fragment clone is on no line and so cannot find its song.
- `LayoutEngine` passes it to `LyricLayoutBuilder.build`, which lays out that
  verse and no other. It must be the same verse the columns were built for — the
  builder reuses their cached syllable widths instead of re-measuring.
- `LyricEditor` captures it once when the session opens, so a whole edit lands
  in one language even if the active verse changes meanwhile.

## One row, always

The lyrics band is one row deep, whatever the song carries. `LayoutResult` has a
single `lyricBaselineYSsInLine`, `lyricsBandHeightSs` reserves one row's ink, and
the hit-test row is that same row. A song with lyrics in three languages is
exactly as tall as one with lyrics in one.

The row is reserved even on a line with no lyrics yet, so entering the first
syllable does not re-space the song.

## Not built yet

There is no UI for choosing the active verse, so nothing calls
`Song.setActiveVerse` outside tests and every song shows verse 1. The picker —
along with naming verses by language and round-tripping those names through
MusicXML — is separate work.
