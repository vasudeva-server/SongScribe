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
- **→ Step 7.** `StaffElementRun.lastKeyChangeKey` has no production caller; only
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
- **→ Row 12.** `plans/check-findings.md` #7: `Line` reaches a concrete `Song`,
  and `UnitTest.minimalSongMock()` grows a stub per forwarding method.
- **→ Row 7.** `Ending.hasRepeatSplit(line, int, int)` takes a transposable index
  pair (`MeasureMapper:710`).
- **→ Step 13.** `plans/check-findings.md` is this pass's working memory and goes
  with `plans/design-pass-6/`. Its #19 notes that phases 4 and 7 reread
  `docs/hairpin-editing.md` and `docs/mutations.md` and found nothing to change.
