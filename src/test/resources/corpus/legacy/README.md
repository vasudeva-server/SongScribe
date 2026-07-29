# Hand-maintained legacy corpus

Unlike `synthetic/`, nothing here is produced by `MusicXmlCorpusGenerator`. These
`.mssw` documents carry constructs the generator **cannot** emit because the model
no longer has anything to emit them from — retired enum values that survive only as
read-side mappings.

Edit them by hand. Never regenerate them.

## retired-accidentals.mssw

Covers the three retired `Accidental` values, whose only remaining trace in the code
is `songscribe.io.LegacyAccidentals`:

| Legacy `<prefix>` | Reads as |
| ----------------- | -------- |
| `NATURAL_FLAT`    | `FLAT`   |
| `NATURAL_SHARP`   | `SHARP`  |
| `DOUBLE_NATURAL`  | `NATURAL`|

The second line pairs a parenthesized retired accidental with the live `FLAT` /
`SHARP` / `NATURAL` values, so the file also proves converted and non-converted
accidentals coexist on one line.

Without this file the legacy `.mssw` conversion path has no fixture exercising it:
`synthetic/note-pitches-accidentals.mssw` used to, but once the values were retired
the generator stopped producing them, so regenerating that fixture silently dropped
the coverage.

Two tests read this file, and they check different things:

- `SongLoaderTest.testLoadFileWithRetiredAccidentalsConvertsThemAndReportsIt` asserts the
  mapping in the table above, and that the load reports the conversion so the reopened
  song counts as modified. This is what makes the table a claim rather than a comment.
- `MusicXmlCorpusLosslessnessTest` picks the file up automatically, along with every other
  corpus `.mssw`, and checks only that it survives the write/read/write cycle unchanged. It
  asserts nothing about *which* accidental each note became, so it would stay green if the
  mapping were wrong but applied consistently.
