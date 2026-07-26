# Lyrics and Verses

## Verses are languages, not stanzas

A song may carry several verses, but **only one verse is ever displayed at a
time**. Multi-verse support exists so a song can hold its lyrics in more than one
language; the user picks one, and that one is the active verse. Verses are not
stanzas stacked under the staff, and no feature should assume they are.

Consequences for any code that touches verses:

- There is exactly one active verse at any moment. `LyricEditor.CURRENT_VERSE`
  is the placeholder for it until language selection is built.
- Never design UI or layout that shows two verses at once, and never treat
  "verse 2" as "the row below verse 1".
- Verse indices are 1-based. Verse 0 does not exist.
- Code that iterates verses is iterating *available languages*, not rows to
  paint. Pass the verse index explicitly (`getLyricForVerse`,
  `isEligibleForLyric`, `LyricConnectorLayout.verseIndex`) rather than defaulting
  to 1 — the active verse will not always be 1.

## Current state

Layout does not yet implement the one-at-a-time rule. `LyricLayoutBuilder`
derives `verseCount` from the highest verse index present and emits boxes and
connectors for every verse, and `LayoutResult.verseYSsInLine` assigns each verse
its own stacked baseline. A file read from MusicXML with two verses therefore
lays out both rows today. This is a known gap, not the intended model: when
language selection lands, layout should lay out the active verse only.

Until then, code that filters by verse (for example, deciding whether a
rendering effect tied to the lyric editor applies to a given connector) must
still compare against the active verse rather than assuming there is only one.
