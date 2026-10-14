# Lyrics

Read `LyricRun` first.

## Membership

- `Lyric`
- `LyricRun`
- `EditableRun`
- `DetachedLyricRun`
- `LyricsChange`
- `LyricEditor`
- `LyricChainEditor`
- `LyricLayoutBuilder`
- `LyricBoxLayout`
- `LyricConnectorLayout`
- `LyricBuilder`
- `MissingLyricCheck`

## Rules

- Lay out, paint and edit exactly one script at a time, and never treat a second script as a row
  below the first.
- Rebuild the columns in a test that exercises a second script, because columns built for the
  first one still carry its lyrics.
- Write a repair of a syllabic or melisma chain on `LyricRun`, never at a call site, because a
  lyric written straight onto an element escapes the modification bracket and undo misses it.
