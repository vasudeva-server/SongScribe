# Design pass 6b — step 9: the diagrams

## What it found

The target's doc is `docs/accidentals.md`. The eight target classes hold no diagram in their Javadoc. Two Javadoc blocks use HTML lists, and neither is a diagram:

- `AccidentalChain.java:57–77` is the walk's algorithm as a numbered list of prose rules.
- `ChainedAccidental.java:35–46` is the five states, the contract of `ChainType.decide`.

Step 5 settled both as contract prose (`findings-5.md`).

Two diagrams exist.

### 1. The worked example, `docs/accidentals.md:45–55`

A row of nine notes in C major at the staff position of F, numbered, with a paragraph that gives each note's state and the effect of three edits.

- **What it shows that prose cannot.** A chain across notes that are not adjacent, a note at another position (the G) that intervenes, a cancel that ends the chain, and how far one edit reaches: the natural on note 5 changes note 6, and the flat on note 7 changes note 9. The rules in _The five states_ are local to one note. Only the example shows how one note's state moves another note's state.
- **Is it the contract drawn a second time?** No. No contract states a sequence of notes. The example applies the rules. It does not restate them.
- **Can it go stale?** Not silently. `AccidentalChainTest.workedExampleCase` pins the nine states, and three tests pin the three edits: `testPressingNaturalOnTheFifthNoteMakesItACancelAndTheSixthAnAnchor`, `testDeselectingTheSecondNotesFlatMakesItInKeyAndTheThirdAnAnchor` and `testPressingFlatOnTheSeventhNoteMakesItImplicitAndTheNinthACancel`.
- **Is it correct?** Yes. I checked each of the nine states and each of the three edits against the rules in _The five states_.

**Verdict: keep.**

### 2. The line-key table, `docs/key-changes.md:70–77`

Six lines, each with its own key, its mid-line change, its inherited key, and the key it runs in at its start and at its end.

`docs/accidentals.md:4` links this doc for "what a key change does to accidentals". Register row 0 (_Keys_, ✅) owns the doc, not row 6b.

- **What it shows that prose cannot.** Four separate key facts for each line, side by side, over a walk that crosses lines. The prose after it (`:79–84`) says where the walk stops, and it reads the table to do so. In prose alone, the reader has to hold six lines × four facts in memory.
- **Is it the contract drawn a second time?** No. It is one instance of the rule, not the rule.
- **Is it correct?** Yes. Line 2's mid-line change to G sets line 2's end key and line 3's inherited key. Line 4's inherited key stays G under its own D. Line 5 inherits D.

**Verdict: keep.**

## What needs deciding

1. **The worked example in** `docs/accidentals.md`**: keep it as it is.** It is the only picture of how one note's state reaches another, and tests pin every state and edit it names. No change to the doc.
2. **The line-key table in** `docs/key-changes.md`**: keep it as it is.** Row 0 owns it and is closed. The pass judges it here because the target's doc links it, and no open row will read it again. The question: do you want this pass to judge a diagram in a doc that a closed row owns? If yes, the verdict is keep, with no change. If no, the item goes to the register as a carry-forward, tagged to whatever row next reads `docs/key-changes.md`.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: all approved
    by: user
    at: "2026-09-22T16:38:59.160Z"
    scope: document
