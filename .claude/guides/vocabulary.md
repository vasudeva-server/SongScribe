# Vocabulary

Read `~/.claude/guides/vocabulary.md` first. It bans "seam" for any code
boundary and says what to write instead. This guide adds the senses
SongScribe approves for the word — both name a physical place on a staff.

## Approved senses

- **Lyric seam** — the join between two syllables, where a hyphen chain or a
  melisma needs repair after an insert. See `ScoreViewController` and
  `docs/clipboard.md`.
- **"Beams at the seams"** — the beat-context problem at the two edges of a
  paste. See `docs/clipboard.md` and `PasteSpanReconciliation`.

## The dialog framework's record boundary

The dialog framework's record boundary is the **dialog interface**; the
framework as a whole is the **dialog framework**. Both are defined at the top
of `plans/ui-dialog-interface.md`.
