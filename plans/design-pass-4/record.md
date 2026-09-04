# Design Pass — `dom`: `Duration`, `ElementType`, `ElementLocation`, `ElementAppearance`, `NoteheadAppearance`, `GlyphAppearance`, `BarAppearance`, `KeySignatureAppearance`

Run by `/design-pass 4` — register row `4`, *Durations and element types*.

**Status:** 🔄 in progress

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | with 2 | — | `findings-1-2.md` |
| 2 Class design | ✅ | ✅ | — | P1–P12 all accepted; Fork A→A1, Fork B→B1, Fork C not taken; `Duration`→`BeatUnit`; `name` field deleted, label derived from `Kind` and `NoteValue` `Strings` keys; `Kind.GRACE_NOTE` distinct |
| 3 Unrepresentable states | ✅ | ✅ | [step-3-unrepresentable-states.md](./step-3-unrepresentable-states.md) | `findings-3.md` is the approved spec: 7 guards, 6 nullables, 2 sentinels retired; 4 types introduced; all open questions settled |
| 4 Extraction | ⏳ | ⏳ | — | |
| 5 Contracts | ⏳ | ⏳ | — | |
| 6 Test triage | ⏳ | ⏳ | — | |
| 7 Test-only surface | ⏳ | ⏳ | — | |
| 8a Suite gate | ⏳ | n/a | — | |
| 8b Visual gate | ⏳ | n/a | — | |
| 9 Diagrams | ⏳ | ⏳ | — | |
| 10 Coverage | ⏳ | n/a | — | |
| 11 Mutation | ⏳ | n/a | — | opportunistic |
| 12 Adversarial review | ⏳ | n/a | — | complete only when the user says it passed |
| 13 Close out | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Baseline

| | Before | After |
|---|---|---|
| Commit | `48fde252` | |
| Production lines (8 files) | 1289 | |
| Test lines (`BarAppearanceTest`) | 172 | |
| Passing tests (`BarAppearanceTest`) | 9 | |

Production: `src/main/java/songscribe/dom/{Duration,ElementType,ElementLocation,ElementAppearance,NoteheadAppearance,GlyphAppearance,BarAppearance,KeySignatureAppearance}.java`.
Tests: `src/test/java/songscribe/dom/BarAppearanceTest.java` — the only test class
over the target.

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

- `ElementLocation.matches(int lineIndex, int elementIndex)` takes two same-typed
  parameters that transpose silently. Pass 0 built `KeyChangeSite` for the same
  shape — a place in a line, stated by whoever resolved it — but only for key
  changes. Whether the general type extends or displaces `ElementLocation` is the
  open question; `ElementLocation`'s own consumers are hover highlighting.
- Inside `ElementType`, each constant now declares an `ElementAppearance` — a
  sealed type over `NoteheadAppearance`, `GlyphAppearance`, `BarAppearance` and
  `KeySignatureAppearance` — supplied through the constructor, so the mapping is
  total by construction and the bounds computation is one exhaustive switch with
  no lookup that can miss. Open: whether `voltaOpeningXOffsetSs` and
  `voltaClosingXOffsetSs` belong on `ElementType` at all, or whether an element
  type owing layout the position of a volta bracket's tick is a dependency that
  should point the other way.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis — the latter is picked up by that step's
findings document rather than argued where it was found. Harvested to the
register at step 13, before this file is deleted.

- `Tuplet.getNoteValue()` returns a `@Nullable ElementType` — a pitched note type
  standing for the tuplet's written value. Once `dom.NoteValue` exists that is the
  type it should hold; `NoteBuilder.buildTimeModification` (L442) converts through
  `ElementType.noteValue()` until it does. Pass 7 (tuplets).
- `BeatUnitMapping`'s comment names a `BeatUnitMappingTest` that does not exist.
  Step 3 phase 2 replaces the comment; step 6 decides whether the injectivity it
  described is worth a test.
