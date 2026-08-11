# LyricEditor Lifecycle

`songscribe.ui.component.LyricEditor` is the in-place lyric editor overlay parented to
`ScoreView`. It edits the active-verse lyric of a single `StaffElement`, with width and
baseline matching the rendered lyric box. This document is the reference for its
lifecycle and key handling; the class itself carries only a prose summary.

See also [Lyrics and Verses](lyrics.md) for the conventions that govern
syllables, hyphen chains, and melismas.

---

## Opening

`EditLyricAction`, Enter, or a double-click constructs the editor:

```
   EditLyricAction / Enter / double-click
           │
           ▼
   new LyricEditor(line, element)
   editor.setBounds(...)
   if existing lyric: setText, selectAll, caret end
   else if on a melisma carrier:  setText("_"), selectAll
   else if inside a hyphen chain: setText("-"), selectAll
   editor.attachListeners()
   score.add(editor); setVisible(true); requestFocus
```

An element with no syllable of its own opens prefilled with a selected placeholder naming
the role it plays for a neighbor: `-` when a word's hyphen spans it (given "a" "-" "mi",
the middle note), `_` when it carries a melisma's extender. A placeholder is not text:
while it is intact every state below reads the editor as empty, so committing it leaves
the chain as it was. Clearing it — with Space, or by deleting it and committing — is what
breaks the chain: a hyphen chain ends the word at the predecessor, a melisma gives up this
carrier and closes the chain behind it.

## While active

Ordinary keystrokes:

- Character insert or delete recomputes the editor width.
- Text longer than `MAX_LENGTH_CHARS` is rejected.
- Newlines are stripped.

Commit and dismiss keys:

| Key | Effect |
| --- | ------ |
| Tab, Space | commit + advance |
| Enter | commit + dismiss |
| Escape | `applyDismissAdjustment` + dismiss (no commit) |
| focus-lost | commit + `applyDismissAdjustment` + dismiss |
| Alt-A | insert `ā` (Alt-Shift-A: `Ā`) |
| Alt-N | insert `ñ` (Alt-Shift-N: `Ñ`) |

## Boundary keys

These are the keys that end a syllable and decide what kind of chain it joins. "adv" means
`advance()` runs afterwards.

| Key | State | Effect |
| --- | ----- | ------ |
| `-` | non-empty | commit as syllable → adv |
| `-` | empty | advance only |
| `=`, `+` | non-empty, caret at end | commit as compound → adv |
| `=`, `+` | empty or caret mid-text | beep, stay open |
| `_` | non-empty, all selected | drop text, extend chain backward → adv |
| `_` | non-empty, caret at end, next element has no syllable | commit as START, next becomes STOP → adv past it |
| `_` | non-empty, otherwise | beep, stay open |
| `_` | empty | extend chain backward → adv |

## Closing

`advance()` scans forward for an eligible element — one that is not a rest, or a rest that
already has a lyric. If it finds one, it dismisses this editor and opens a new
`LyricEditor(line, next)`; otherwise it just dismisses.

`applyDismissAdjustment()` runs on every dismiss, walking back to repair a dangling
extender or syllable chain. It is suppressed when `extendChainBackward` has just built a
well-formed chain.

`dismiss()` removes the editor from the score and clears the editor reference.

## Invariant

While the editor is active, no external code path may mutate the song or fire any toolbar
keystroke. This is enforced by `DISABLE_WHEN_EDITING_TEXT` on every toolbar `UIAction`, and
`LyricEditorActionAuditTest` locks the whitelist.
