# Accidentals

Read `AccidentalChain` first.

## Membership

- `AccidentalChain`
- `AccidentalState`
- `AccidentalScanView`
- `ChainedAccidental`
- `ChainEdit`
- `ProjectedEdit`
- `AccidentalFitGate`
- `AccidentalAction`

## Rules

- Decide which accidental a note writes only through the chain walk, and never ask the notator
  which accidental to write or to confirm one.
- Draw an accidental from the note's stored state alone, and never read the notes to its left at
  render time.
- Measure an edit that is checked for fit before it mutates anything (preview insertion, preview
  replacement, paste, a selection action that widens a column) together with the accidentals its
  projected chain walk decides, through `AccidentalFitGate`, because a written accidental is layout
  width.
- Let every other edit leave a line wider than its fit, to render overflowing and flagged, rather
  than refuse it.
- Give an accidental no hit region of its own, and change it only through the accidental actions
  on the note that carries it.
