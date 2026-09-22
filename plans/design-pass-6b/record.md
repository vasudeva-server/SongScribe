# Design Pass — `dom`: `Accidental`, `AccidentalChain`, `AccidentalScanView`, `AccidentalState`, `ChainEdit`, `ProjectedEdit`

Run by `/design-pass 6b` — register row `6b`, *Accidentals*.

**Status:** 🔄 in progress

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | with 2 | — | `findings-1-2.md`, *What it found* |
| 2 Class design | ✅ | ✅ | — | `findings-1-2.md`. Decided and done: `ChainedAccidental` joins row 6b and implements `AccidentalState` (`Stamped` deleted); `Cursor` is a public `dom` record and `ChainEdit` a final class over `Cursor start, int length, Reach reach` with factories `rekeyingFrom` and `lineInserted`; `AccidentalScanView` is package-private, `Projected` private in `ProjectedEdit`, `walk(Song, ChainEdit)` and `walkSong(Song)` public; `semitoneAdjustmentOf` replaced by `Accidental.semitones()`, `DiatonicPitch.midi` non-null. `ChainType.decide` keeps its signature (item 7). |
| 3 Unrepresentable states | ✅ | ✅ | — | `findings-3.md`. Done: the five `ChainEdit` factories take a `Cursor`; `Walk` is built only when an element exists at or after the edit's start, and seeds `key` and `previous` in its constructor. Decided against: `AccidentalScanView.elementAt(Cursor)` — it keeps `(int, int)`. |
| 4 Extraction | ✅ | ✅ | — | `findings-4.md`. Done: `Restamp.stamp()`; `Line.keyBefore(int)` with its eight callers moved off `keyAt(i - 1)`; `Key.alterationAt(StaffPosition)`; `ElementType.isTransparentInChain()`. |
| 5 Contracts | ✅ | ✅ | — | `findings-5.md`. Done: `ChainType.decide`'s summary cites the class Javadoc instead of restating it; `AccidentalChain`'s *The policy* section is cut and replaced by a citation to `ChainedAccidental`'s class Javadoc and `docs/accidentals.md`. |
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

## Target

Production, in `src/main/java/songscribe/dom/`: `Accidental.java`,
`AccidentalChain.java`, `AccidentalScanView.java`, `AccidentalState.java`,
`ChainEdit.java`, `ProjectedEdit.java`.

Tests, in `src/test/java/songscribe/dom/`: `AccidentalChainTest.java`,
`SongAccidentalWalkTest.java`.

## Baseline

| Measure | Before | After |
|---|---|---|
| Start commit | `71abd397` | |
| Production lines | 1121 | |
| Test lines | 454 | |
| Tests passing | 21 | |

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

- Pass 6's mutation run over `songscribe.dom`, with the `dom` tests only, left 26
  mutants in `ChainEdit` that no test detects. `lineRekeyed` (`:171`) and
  `lineRemoved` (`:188`) can return null, and `lineRemoved`'s rekey clause
  (`:189`) can be negated, so no test sends a line key change or a line removal
  through the walk. Also undetected: `replaced`'s range end and rekey clause
  (`:135–136`), `modified`'s vacated-position condition, range end and rekey
  clause (`:155–161`), `of`'s rekey for a line insertion with its own key
  (`:229`), every clause of `isRestamp` (`:265–269`), and `anyRekeys` and
  `endsInForce` returning true (`:285`, `:289`). Each is a promise in
  `ChainEdit`'s own contract.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis — the latter is picked up by that step's
findings document rather than argued where it was found. Harvested to the
register at step 13, before this file is deleted.

- **Register, row 6 (`Line`).** `Line.keyAt(int)`'s inclusive bound has no
  caller that needs it: its two remaining callers, `KeyChangeSite.keyInEffect`
  (`NEW_POSITION`, an index no key change stands at) and
  `AccidentalAction.keyAlterationAt` (a note's index), get the same answer from
  `keyBefore`. Its `elementCount()` case is `keyBefore(elementCount())`.
  `LineTest` covers `keyAt`. Deleting `keyAt` leaves one key query with one
  bound.
- **Register, the row that owns `NoteElement`.** "A note on a line of a song
  is `Stamped`" (`AccidentalState.java:27–32`) is prose;
  `NoteElement.setAccidentalState` is public with 14 production call sites in
  9 classes.
