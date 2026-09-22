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
| 6 Test triage | ✅ | ✅ | — | `findings-6.md`. Done: three `AccidentalChainTest` removal tests and all five `SongAccidentalWalkTest` tests each folded into one parameterized case table, the latter gaining four new cases (line rekey, line removal that rekeys, line insertion with its own key, element replacement); `ChainEditTest.java` and `ProjectedEditTest.java` added, one promise each. `./scripts/test.sh AccidentalChainTest SongAccidentalWalkTest ChainEditTest ProjectedEditTest`: 27 passed. |
| 7 Test-only surface | ✅ | ✅ | — | `findings-7.md`. Every member with a caller outside `src/test/java/` was checked; none exists solely for a test, no replaceable process-global, no misnamed internal API, no incomplete lifecycle. Nothing to fix. |
| 8a Suite gate | ✅ | n/a | — | `./scripts/compile.sh --test`: SUCCESS. `./scripts/test.sh AccidentalChainTest SongAccidentalWalkTest ChainEditTest ProjectedEditTest`: 27 passed. |
| 8b Visual gate | ✅ | n/a | — | |
| 9 Diagrams | ✅ | ✅ | — | `findings-9.md`. Both diagrams kept, no change: the worked example in `docs/accidentals.md` (pinned by `AccidentalChainTest`) and the line-key table in `docs/key-changes.md` (row 0's doc, judged here because the target's doc links it). |
| 10 Coverage | ✅ | n/a | — | `./scripts/coverage.sh unit` over the four target test classes, reported in the terminal. **Defect fixed:** the walk stopped at a key change past the edit's start even inside the edited range, so a pasted fragment holding one was projected without the notes after it; a key change inside the range now rekeys and the walk goes on (`AccidentalChain` rule 1 and `Walk.run`). Confirmed by the two cases below failing against the old rule. Deleted: `ChainEdit.length()` (no caller) and `Walk.inSpan`'s always-true `rangeStart` conjunct. Tests added: `AccidentalChainTest.testAnEditRedecidesTheNotesItReaches`, five cases (key change inside the range; in-force seed stopping at a key change and at an own-key line start; `previous` and span end skipping rests); `SongAccidentalWalkTest` rows for a key change altered in place and a line holding a key change removed; `ProjectedEditTest` rewritten as a case table asserting every note, with a fragment row. Every other unrun region left alone with its reason given at the step. `./scripts/test.sh AccidentalChainTest SongAccidentalWalkTest ChainEditTest ProjectedEditTest`: 35 passed; `AccidentalFitGateTest InheritedKeyPropagationTest`: 12 passed. |
| 11 Mutation | ✅ | n/a | — | `./scripts/mutation-test.sh songscribe.dom.ChainEdit`, run because the claimed carry-forward finding was itself a mutation result over this class. Every mutant that finding named is now detected, by the cases steps 6 and 10 added. What survives is in three groups, none a gap: the `Reach` widening direction (`anyRekeys`, `endsInForce`, and the rekey clauses at `ChainEdit.java:185`, `:250`, `:270`) — `reachesNextKeyChange` only keeps the walk running and marks more notes decided (`AccidentalChain.java:278`, `:313`), and a re-decided note restamps only on a differing type (`:283`), so a wider reach preserves the answer and no test can detect it; the null arm of `of` (`:308`, 13 mutants), whose exhaustive switch over the sealed `Mutation` hierarchy makes the compiler carry the promise; and the `ElementModification` branches at `:203–209`, already in *Findings raised*. Nothing to fix. |
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
`ChainedAccidental.java`, `ChainEdit.java`, `Cursor.java`, `ProjectedEdit.java`
(`ChainedAccidental` was folded in at the step 2 gate; `Cursor` was extracted at
step 4 — both missing from this list until now).

Tests, in `src/test/java/songscribe/dom/`: `AccidentalChainTest.java`,
`SongAccidentalWalkTest.java`, `ChainEditTest.java`, `ProjectedEditTest.java`
(the last two added at step 6, `findings-6.md`).

## Baseline

| Measure | Before | After |
|---|---|---|
| Start commit | `71abd397` | |
| Production lines | 1289 (1121 recorded at step 0, corrected for the two files the Target list was missing) | 1385 |
| Test lines | 454 | 821 |
| Tests passing | 21 | 35 |

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
- **Register, the row that owns `message/mutation`.** "`beforeElement` and
  `afterElement` are snapshots of one element" is prose on `ElementModification`;
  the record's public constructor accepts any pair. `Line.java:549` is the only
  production site, and `ChainEditTest.java:49` builds one by hand. So
  `ChainEdit.modified` and `ChainEdit.isRestamp` keep branches for pairs no
  production code records (an `after` that is not a note, `endsInForce(after)`
  differing from `endsInForce(before)`); they answer such a pair correctly, so
  they are not defects. The correct structure: `ElementModification` becomes a
  final class with a private constructor and one factory that takes the element
  and the mutator and makes both clones itself — not a record, for the JLS
  §8.10.4 reason `ChainedAccidental` states — and `ChainEdit.modified` then reads
  one element type. Raised at step 10.
