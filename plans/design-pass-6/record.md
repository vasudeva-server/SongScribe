# Design Pass — `dom`: `Line`, `ElementChange`, `ProjectedElements`, `StaffElementRun`

Run by `/design-pass 6` — register row `6`, *Line*.

**Status:** 🔄 in progress

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | with 2 | — | `findings-1-2.md` |
| 2 Class design | ✅ | ✅ | [class-design.md](./class-design.md) | Landed in `f7309389` together with the fixes from `plans/check-findings.md` |
| 3 Unrepresentable states | ✅ | ✅ | [step-3-unrepresentable-states.md](./step-3-unrepresentable-states.md) | `findings-3.md`. Execution reaches past the target: the `-1` sweep (3.13) across `dom`, `ui`, `layout`, `midi` and `io`, and rows 7, 12, 16 and 25's files. During review the user removed the dead `@Nullable` from `Line.getElementIndex` and from `LineSpans.boundOf`, with `boundOf`'s null branch (no caller passes null; the tree compiled both times) |
| 4 Extraction | ✅ | ✅ | [step-4-extraction.md](./step-4-extraction.md) | `findings-4.md`. During review the user lifted the gate for one change: `Song.isAutoMaintainedTerminal` → `isTerminal` and `isAutoMaintainedTerminalOfItsSong` → `isTerminalOfItsSong` (row 12's file), then `TerminalMaintainer.isAutoMaintainedTerminal` → `isTerminal`; the tree compiled both times |
| 5 Contracts | ✅ | ✅ | — | `findings-5.md`. Settled at the gate: items 1–3 and 6 as proposed. Item 4: key-change insertion put a breath mark behind a key change, so `KeyChangeAction.acceptsInsertionIndex` refuses the slot in front of a breath mark, and the `deleteElement` cascade goes. Item 5: a grace note may be entered in front of a paired grace note; grace mode's press asks `canInsertElementAt` and `isInsideGraceHostPair` goes; a grace-note click never reaches plain note entry, because `PreviewElementInserter` refuses a grace preview outside grace mode; grace mode inserts through its own entry point, which never replaces and returns what it inserted, in place of `forceInsert`. Lone grace notes are not implemented now; they will enter through grace mode (Return commits the grace note alone) |
| 6 Test triage | ✅ | ✅ | — | `findings-6.md`. The deletion checkpoint settled three promises, stated in `EditableRun.deleteRange`'s contract and in `docs/lyrics.md`, *Deleting elements closes the chains up*: a word closes up around the gap, a melisma follows its syllable, and a paired grace note deleted alone takes its syllable with it. `deleteRange` is one path: it removes the range, then settles the chains across the gap from the lyrics that remain. An insertion that interrupts settles them by the same rule, so both find a neighbor past a rest, a barline, a breath mark or a key change; `LyricRunTest` states that for an insertion |
| 7 Test-only surface | ✅ | ✅ | — | `findings-7.md`, not opened in roughdraft at the user's request. Settled: item 1 as proposed, item 2 with option (b). `lastKeyChangeKey` is deleted, and `MusicXmlStrandedKeyChangeTest` asserts the key changes a line holds. `strandedKeyChangeIndices` is inlined into `redundantKeyChangeRanges(int, Key)`, and `StrandedKeyChangeTest`'s stranded cases assert ranges |
| 8a Suite gate | ✅ | n/a | — | |
| 8b Visual gate | ✅ | n/a | — | |
| 9 Diagrams | ✅ | ✅ | — | `findings-9.md`. The target holds no diagram. The one diagram that touches its types, `docs/key-changes.md:70–77`, stays with row 0 (user's choice); nothing changed |
| 10 Coverage | ✅ | n/a | — | Seven contract cases had no test, and the user approved a test for each: the hairpin join after a deletion, the end-side absorb, endpoint placement (`SpanBound`), a cross-line tie in both registries, the re-point in `setElement`, `canReplaceElementAt`, and undo and redo of a deletion with the span it removes. Settled: only one ending can report an effect, so `Replacement.endingEffect` gets no test and its contract says so. Done in this pass at the user's decision (rows 12 and 15's files, and `ui/selection`): `ElementRangeDeletion` is gone, `ElementDeletion` carries an `ElementRange`, and `removeElement` is a default over `removeRange`. No dead code found |
| 11 Mutation | ✅ | n/a | — | Run over `songscribe.dom.*` with the `dom` tests (user's choice). Twelve test cases added at the user's approval, as new methods and as rows in existing tables, each for a contract case a survivor showed untested: membership of a line and of what hangs on an element; `setKey`'s normalization; the sweep on insertion and on modification; four hairpin joins; begin-side and flush end-side absorption; a paired grace note first on its line; the terminal policy through `Line`, including `isLast`; `needsBarlineBeforeKeyChangeAt`. Also: `isHostOfPairedGraceNote`'s redundant guard went, `lastNoteIndex` is private, and `applySpanRun`'s sort went, because undo re-adds at the end of the list and no hairpin reader depends on list order. The survivors left in the target are equivalent, plus `EffectiveRange.equals` and `ElementRange.shiftedBy`, which the floor gives no test |
| 12 Adversarial review | ⏳ | n/a | — | complete only when the user says it passed |
| 13 Close out | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Target

Production: `src/main/java/songscribe/dom/Line.java`, `ElementChange.java`,
`ProjectedElements.java`, `StaffElementRun.java`, and the two types step 2 split
out of `Line`: `EditableRun.java`, `LineSpans.java`.
Tests: `src/test/java/songscribe/dom/LineTest.java`, `LineDeletionTest.java`,
`StrandedKeyChangeTest.java` (exercises `StaffElementRun`'s key queries).

## Baseline

| | Before |
|---|---|
| Commit | `13075b18` |
| Production lines | 2766 (Line 2055, ElementChange 209, ProjectedElements 69, StaffElementRun 433) |
| Test lines | 751 (LineTest 230, LineDeletionTest 162, StrandedKeyChangeTest 359) |
| Passing tests | 68 (`LineTest` + `LineDeletionTest` 47, `StrandedKeyChangeTest` 21) |

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

- `StaffElementRun.getElement` is contracted as *"The element at `index`."* — no
  range, no `@return`, no `@throws`. `Line`'s implementation is a bare
  `elements.get(index)`, so out of bounds throws by inheritance from the field's
  type rather than by promise, and any caller guarding on `elementCount()` is
  guessing. Step 5's. Closed at step 5: the contract is written, and the
  register's copy of this line is deleted.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis — the latter is picked up by that step's
findings document rather than argued where it was found. Harvested to the
register at step 13, before this file is deleted.

- **→ Step 5.** `plans/check-findings.md` contract items 8–19. Several landed in
  the check-fix snapshot; step 5 re-reads each against the tree rather than trusting
  either list.
- **→ Step 5.** `EditableRun.deleteRange`'s Javadoc (`:112–114`) claims the deletion
  repairs "the x-offsets of the elements that close the gap", and `deleteElement`'s
  comments (`:208`, `:264`) name a "gap-fill". No statement in either method touches an
  x-offset; step 5 checks the claim against `LyricRun`'s repairs and rewrites it.
- **→ Step 5.** What a deletion takes with it differs by path: `deleteElement` (`:262–272`)
  cascade-deletes a breath mark that lands at the deletion's left edge, so a second breath
  mark after a widened range's end goes with the elementwise path and survives the
  contiguous `deleteRange` path. The contract for `deleteRange` must state one answer.
- **→ Step 5.** `LineSpans.removeOverlapping`'s contract carries the detached-anchor
  sentence now on `removeTrillsOverlapping` (`:552–553`), for every kind (step 4 item 8).
- **→ Step 6.** `plans/check-findings.md` #23: the promises the pass wrote that no
  test asserts — `Span.addTo`'s displacement policy and replay skip,
  `Span.removeFrom`'s no-op, `removesConfirmableSpan`, the three
  `TerminalMaintainer` checks.
- **Closed at step 7.** `StaffElementRun.lastKeyChangeKey` has no production caller; only
  `MusicXmlStrandedKeyChangeTest` calls it. `strandedKeyChangeIndices` has one production
  caller, `redundantKeyChangeRanges` in the same interface, and `StrandedKeyChangeTest` calls
  it directly. Found at step 5.
- **→ Step 6.** Step 5 changed behavior that no test covers: `KeyChangeAction.acceptsInsertionIndex`
  refuses the slot in front of a breath mark; `PreviewElementInserter.handleClick` refuses a
  grace-note preview; `insertForGraceMode` inserts in front of the element under the pointer,
  refuses the two slots inside a pair, and returns what it inserted; grace mode enters in front
  of a paired grace note.
- **Fixed at step 5.** The host note's fit was checked on entry to `GRACE_NOTE_INSERT` without
  the accidental chain walk, so the insertion's walked gate could refuse a host the entry check
  accepted, and the pairing then joined the glissando to the wrong element. The preview
  insertion now splits into a gate (`PreviewElementInserter.accept`, returning an
  `AcceptedInsertion`) and a landing. Grace mode gates the host before the pairing bracket
  opens, and a refused host aborts the interaction and removes the grace note (user's choice).
- **Fixed at step 5.** The static `GraceModeManager.deferInsertionRepairs()` handshake is gone:
  grace mode lands its grace note through `AcceptedInsertion.landWithoutRepairs`, and owes the
  repairs when the insertion was not an append.
- **→ Step 6.** Also untested: a refused host aborts the pairing; `AcceptedInsertion.land` and
  `landWithoutRepairs`.
- **Carried to the register, → Pass 9.** Found at step 6 and after step 7: `AcceptedInsertion.land`
  skips `insertRun`'s repairs on an append, and the lyric chain repairs stop at the line edge. The
  user decided that a line break is a gap and that words and melismas both cross it, and deferred
  the lyric-system redesign to pass 9. The register's pass 9 item on chains crossing a line break
  holds the finding and the correct structure.
- **Fixed after step 7, → Row 24 (user lifted the gate).** Found at step 7. `KeyChangeAction.acceptsInsertionIndex` (`ui/action/KeyChangeAction.java:133`)
  is a function of the line and the index alone: it reads no state of the action. It sits on the
  action because the action is the `InsertionPointMode.Client`, so `KeyChangeActionTest:53` builds a
  full mock environment (`MockEnvHelper.setupMockEnv().frame()`) to ask a question about a line. The
  rule of where a key change may stand is a placement rule on the run, beside `canInsertElementAt` and
  `canTrailRunAt`; the action's override then delegates to it, and the test needs no frame. Done:
  `StaffElementRun.canInsertKeyChangeAt` holds the rule and also asks `canInsertElementAt`; the
  action delegates; `StaffElementRunTest` carries the cases, with the two slots inside a pair added;
  `KeyChangeActionTest` is deleted. Nothing for row 24 to carry.
- **→ Row 15.** Found at step 10. `LineSpans.sweep` returns at once during replay (`LineSpans.java:369`).
  A batch replays whole and in order, so at each replayed element change the companion span
  changes are already applied, and the sweep finds nothing to do. No test can make the replay
  check fail. Row 15 decides whether the check is dead, or names the batch that needs it.
- **Carried to the register at step 11, → Row 6b.** The accidental walk (`Accidental`, `AccidentalChain`,
  `AccidentalScanView`, `AccidentalState`, `ChainEdit`, `ProjectedEdit`) had no register row. Row 6b
  is new, after row 6 (user's choice), and its carry-forward item lists `ChainEdit`'s 26 survivors.
- **Carried to the register at step 11, → Row 7.** Whether `LineSpans.getSpans` promises an order: only
  the tie queries depend on one.
- **Not carried, step 11.** `LyricRun`'s own survivors are row 9's, and row 9's step 11 finds them
  again from its own tree (user's choice).
- **→ Row 12.** `plans/check-findings.md` #7: `Line` reaches a concrete `Song`,
  and `UnitTest.minimalSongMock()` grows a stub per forwarding method.
- **→ Row 7.** `Ending.hasRepeatSplit(line, int, int)` takes a transposable index
  pair (`MeasureMapper:710`).
- **→ Step 13.** `plans/check-findings.md` is this pass's working memory and goes
  with `plans/design-pass-6/`. Its #19 notes that phases 4 and 7 reread
  `docs/hairpin-editing.md` and `docs/mutations.md` and found nothing to change.
