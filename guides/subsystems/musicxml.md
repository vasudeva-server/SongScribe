# MusicXML I/O

Read `MusicXmlReader` and `MusicXmlWriter` first.

## Membership

- `MusicXmlReader`
- `MusicXmlWriter`
- `MusicXmlSerializer`
- `ScorePartwiseBuilder`
- `SongMapper`
- `HeaderBuilder`
- `HeaderMapper`
- `MeasureBuilder`
- `MeasureMapper`
- `NoteBuilder`
- `NoteMapper`
- `DirectionBuilder`
- `LyricBuilder`
- `BuildContext`
- `BuildIndex`
- `ProxyMusicAccess`
- `SoftwareProvenance`
- `SupportedFeature`
- `SafeXmlParser`

## Rules

- Spend no effort on foreign input: add no mapping, fallback, normalization, model field or test
  for a construct the writer never emits, and raise no "what if another application wrote X?"
  design question, because the provenance gate rejects any document `MusicXmlWriter` did not
  write before mapping runs.
- Run a load-time fix-up in a phase transition of `SongLoad`, never in a reader or in the UI,
  because both readers and the headless export route load through it. The phases are `RawLoad`,
  `KeyedLoad`, `StampedLoad` and `PlacedLoad`, in that order. A fix-up runs in the transition of
  the first phase whose state it needs.
- Never read back a value an earlier builder wrote to decide what to emit next; do that read in a
  later pass over the finished graph.
- Cover every construct the writer emits with a schema-validation test, because the generated
  classes admit documents the schema rejects and the reader accepts exactly what the writer
  emits, so a round-trip test cannot catch them.
