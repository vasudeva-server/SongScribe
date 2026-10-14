# Vocabulary

Read `~/.claude/guides/vocabulary.md` first. It bans "seam" for any code boundary and says what
to write instead. SongScribe approves two senses of the word, both naming a physical place on a
staff.

## Rules

- Use "lyric seam" only for the join between two syllables, where a hyphen chain or a melisma
  needs repair after an insert. See `ScoreViewController` and
  [clipboard](../subsystems/clipboard.md).
- Use "beams at the seams" only for the beat-context problem at the two edges of a paste. See
  `PasteSpanReconciliation` and [clipboard](../subsystems/clipboard.md).
- Call the whole of `songscribe.ui.dialog` "the dialog framework", and call how data crosses
  between a dialog and the rest of the application "the dialog interface", never a "seam". The
  rules that hold that boundary are in [dialogs](dialogs.md).
- Name what establishes or edits a key a "key", and name only what is drawn from it a "key
  signature". See [key changes](../concepts/key-changes.md).
