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

## Key versus signature

**A key is the key. A key signature is what gets drawn from it.** The notator
does not think of changing the signature; they think of changing the key, and the
signature follows. Every name in this area is chosen against that rule, and it
applies to user-facing strings as much as to code.

- **Key** — the value, and anything that establishes or edits one. On screen the
  notator reads "Key Change", never "Key Signature Change".
- **Key signature** — only what is rendered: the positioned box in a staff
  header, and the painting of it. Nothing else may take the name.

The tell that a name is wrong is that it describes the value or the edit while
saying "signature", or describes glyphs on a staff while saying "key". Where the
words genuinely describe drawn accidentals — "five flats" in a key's display
name — "signature" stays correct.

## The dialog framework and the dialog interface

Two terms, and they name different things. Neither is a "seam".

- **The dialog framework** — the whole of `songscribe.ui.dialog`: the dialog
  lifecycle and persisted geometry, the button row, tabs, dialog categories and
  the blocking counter. Named the way the message framework and the mutation
  framework are.
- **The dialog interface** — how data crosses between a dialog and the rest of
  the application: a record in, a record out, and a bundle of function references
  supplied by the controller that opened it.

The rules that hold that boundary are in [dialogs.md](dialogs.md).
