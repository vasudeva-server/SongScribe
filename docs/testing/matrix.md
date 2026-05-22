# Testing Matrix (disposable scaffolding)

> **Status: scaffolding.** This file drives a production-code-first test audit.
> It is **not** a living document — once the audit produces rewritten tests, the
> tests themselves carry the contract forward and this file is archived/deleted.
> The one durable output is the **rubric** below, which will be promoted into
> `.agents/guides/testing-common.md` at the end.

## Method

Audit proceeds **from production code, not from existing tests**. For each
production class in scope we enumerate its testable behaviors, classify each as
`unit` / `e2e` / `none` using the rubric, then check whether an adequate test
already exists (unit *or* e2e — both levels checked per behavior). No test code
is changed until the full matrix exists and a remediation order is approved.

E2E coverage is assessed by **reading e2e test source only**; the e2e suite is
never run during the audit.

Audit verdicts are **reading-based hypotheses**, not proof. An `inadequate`
verdict predicts a surviving mutant; it is not confirmed during the audit. PIT
(`./scripts/mutation-test.sh`) is the **verification step, applied during
remediation** — when a pure-logic class's tests are rewritten, a scoped PIT run
confirms the flagged weakness existed and that the rewrite kills the mutant.
Audit sessions stay read-only and do not run PIT.

---

## Rubric: unit vs. e2e vs. none

Derived from `testing-common.md`, `testing-unit.md`, `testing-e2e.md`. This is
the consistency anchor — every session classifies behaviors by these rules, not
by ad-hoc judgment.

> **Paramount: the Test Quality Principles in `testing-common.md` override
> everything else here.** The unit/e2e/none classification only decides *where*
> a behavior is tested; the Quality Principles decide whether a test is worth
> having at all. A test that cannot fail, asserts against a mock, has a
> name/behavior mismatch, or gives false confidence is a defect *regardless* of
> being at the correct level. When auditing each behavior, apply the Quality
> Principles (Correctness → Usefulness → Coverage) first; the level rubric
> second.

### Default: unit

Prefer a unit test. Unit tests are faster, run without approval, and localize
failures. A behavior is unit-testable if its risk is **logic, computation,
state, data transformation, or model mutation** — even when it requires:

- mocking the `MainFrame.getInstance()` singleton chain (see `testing-unit.md`),
- widening a member to package-private to test it directly (see *Testability
  Over Encapsulation* in `testing-common.md`), or
- constructing collaborators via `ReflectionTestHelper`.

Examples that are **unit**: format migration, serialization round-trips, layout
geometry/stacking math, MIDI generation, action enablement logic, selection
state machines, mutation records, derived model state, `@Nullable` contracts.

### Escalate to e2e ONLY when the risk *is* the integration

Use an e2e test only when the behavior **genuinely requires the real Swing
pipeline** and cannot be meaningfully verified with collaborators mocked:

- real mouse/keyboard event dispatch (click, drag, shift-click, type),
- cross-component integration where the bug lives in the wiring (action →
  model mutation → layout invalidation → repaint → selection reflection),
- behavior only observable after a real layout/repaint cycle,
- application lifecycle (boot, shutdown, file open/save through the UI).

If everything that matters can be asserted with the singleton mocked, it is
**not** an e2e case — putting it in e2e is the wrong level.

### Classify as none (no test warranted)

- trivial getters/setters with no logic,
- pure data holders (most `message.mutation` / `message.command` /
  `message.notification` records, unless they carry derivation logic),
- pure display/layout wiring with no branching logic (most dialogs, menus),
- framework behavior that cannot regress in our code,
- pure rendering to a `Graphics2D` with no computed geometry to assert
  (the geometry, if any, is unit-tested upstream).

### Verdict vocabulary (per existing test found)

- **adequate** — a test exists at the right level and can actually fail.
- **wrong-level** — covered, but as e2e what should be unit (or vice versa).
- **inadequate** — exists but can't fail / name-mismatch / asserts a mock /
  weak assertion (see *Correctness* + *Usefulness* in `testing-common.md`).
- **missing** — behavior warrants a test (unit or e2e) and none exists.
- **redundant** — duplicate coverage of a behavior already adequately tested.

---

## Existing test inventory (baseline)

- Unit: **1267** `@Test` methods across ~132 files (mirrors source packages).
- E2E: **79** `@Test` methods across 7 files in `songscribe/e2e/`
  (`ElementInsertionTest` 27, `SelectionTest` 24, `NoteConnectionTest` 14,
  `DynamicsMarkingTest` 5, `DialogsTest` 5, `ShutdownTest` 3, `E2ETest` 1).

---

## Audit progress

Risk-ordered. Data-loss / core-logic packages first; cosmetic rendering last.

| # | Package scope | Prod classes | Status |
|---|---------------|--------------|--------|
| 1 | `dom` | 39 | done |
| 2 | `io` | 16 | not yet audited |
| 3 | `layout` | 39 | not yet audited |
| 4 | `midi` + `converter` + `util` + `smufl` + `prefs` + `font` + `export` + `uiconverter` | ~58 | not yet audited |
| 5 | `ui/action` | 62 | not yet audited |
| 6 | `ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard` | 15 | not yet audited |
| 7 | `ui/component` | 65 | not yet audited |
| 8 | `message` (mutation/command/notification + core) | 86 | not yet audited |
| 9 | `ui/renderer` | 30 | not yet audited |
| 10 | `ui/dialog` | 53 | not yet audited |
| 11 | `ui/menu` + `ui/playback` + `ui/platform` + top-level `ui` | ~38 | not yet audited |
| 12 | `lifecycle` + `error` + top-level (`SongScribe`, `FileExtensions`) | 5 | not yet audited |
| 13 | e2e reconciliation (whole-suite redundancy / orphans) | — | not yet audited |

---

## Findings

> One section per audited package, appended as sessions complete. Schema per row:
> **class · behavior · required level · existing test · verdict · action**

<!-- Session findings appended below this line -->

## 1. `dom` (audited 2026-05-21)

Audited via six parallel production-first sub-audits: **Song**; **Line**; **element/note core**; **element-type & pitch system**; **attachment family**; **ornaments/dynamics/misc**. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

### 1A. `Song`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| Song | Default ctor: one line w/ FINAL_DOUBLE_BARLINE, not modified, defaults (attribution, key, tempo) | unit | `SongDefaultsTest` (6 methods) | adequate | keep |
| Song | `getEffectiveTempo()` returns `new Tempo(120, CROTCHET)` when tempo null | unit | `SongDefaultsTest.testEffectiveTempoFallbackWhenTempoIsNull` | adequate | keep |
| Song | `getTempo()` `@Nullable` — can be null after `setTempo(null)` | unit | `SongDefaultsTest.testEffectiveTempoFallbackWhenTempoIsNull` | adequate | keep |
| Song | `getTempoAt(line, note)` walks backward to most-recent tempo change | unit | — | missing | write unit test: multi-line per-note `TempoChangeAttachment`; assert result + fallback to getEffectiveTempo |
| Song | `hasAnyTempoChange()` true iff any element carries a `TempoChangeAttachment` | unit | — | missing | write unit test (false empty, true after attach) |
| Song | `clearTempoIfOrphaned` — clears song tempo when element is first-of-first-line or no per-note changes remain | unit | — | missing | write unit tests for all 3 branches |
| Song | `normalizeTitle` — strip LF, collapse spaces, short-ă replacement | unit | — | missing | write unit test asserting all 3 transformations |
| Song | `processText` — conditional short-ă strip + always trim | unit | — | missing | write unit test w/ prefs mock, both branches |
| Song | `setTitle` normalizes before mutating (no-op if normalized==stored) | unit | `SongSetterMutationTest.testSetTitle*` | adequate | keep (normalized-equals-stored no-op branch still untested — optional case) |
| Song | `setPlace/Year/Attribution/Number/Footnotes/BanglaLyrics/TranslatedLyrics` — trim then compare/store | unit | `SongSetterMutationTest` (all pairs) | adequate | keep |
| Song | `setUnderLyrics` delegates to `processText` | unit | `SongSetterMutationTest.testSetUnderLyricsPostsMutation` | inadequate | asserts only the mutation record, not the processText transformation; strengthen or add processText test |
| Song | `setMonth/Day` — primitive idempotence | unit | `SongSetterMutationTest` | adequate | keep |
| Song | `setTempo/DefaultKeyAccidentalCount/DefaultKeyType/UnofficialTranslation` — mutation + no-op idempotence | unit | `SongSetterMutationTest` | adequate | keep |
| Song | `mutateMetadata` early-return uses `Objects.equals` (null/null) | unit | `SongSetterMutationTest.testSetTempoSameValuePostsNothing` | adequate | keep |
| Song | `setTopPaddingSs(_, true)` — sticky `userSetTopPadding` flag (OR-accumulate) | unit | `SongSetterMutationTest.testSetTopPaddingSsPostsMutation` (only `false`) | missing | write test: `(x,true)` then `(x,false)` → flag stays true |
| Song | `setTopPaddingSs` always runs apply block (posts even when value unchanged if setByUser differs) | unit | — | missing | write test: `(same,false)` then `(same,true)` → mutation posted, flag true |
| Song | `setAttributionStartYSs/RowHeightAdjustmentSs/LineWidthSs` — no-op idempotence | unit | `SongSetterMutationTest` | adequate | keep |
| Song | `getLyricsText` — assemble syllabified text (extend `_`, compound `--`, BEGIN/MIDDLE `-`, SINGLE/END space, line `\n`) | unit | — | missing | write unit test asserting each branch |
| Song | `loadFrom(SongData)` — apply all scalars atomically, clear lines, mark not-modified, attach initial tempo | unit | `SongLoadingTest.testLoadingLegacySongDoesNotDirtyDocument` | inadequate | only checks isModified; write test asserting each field mapping from a crafted SongData |
| Song | `applyLineDefaults` — default key when count=0/type null; tempo-change Y per first-vs-other line | unit | — | missing | write unit tests for all 4 cases |
| Song | `isEmpty()` — no lines→false; all empty→true; any non-empty→false | unit | — | missing | write unit test per variant |
| Song | `getLineWidthPx()` delegates to `ScaleContext.ssToRoundedPx` | none | — | none | trivial delegation |
| Song | `addLine(i, line)` validates `line.getSong()==this`, throws IAE for foreign line | unit | — | missing | write test asserting IAE for foreign line |
| Song | `addLine/removeLine` terminal-invariant maintenance (4 FINAL branches) | unit | `SongLineMaintenanceTest` | adequate (FINAL) | REPEAT_RIGHT carry-over untested → see next row |
| Song | `terminalTypeToInstall` — carry outgoing REPEAT_RIGHT to new last line; promote interior REPEAT_RIGHT | unit | — | missing | write unit tests for both REPEAT_RIGHT paths |
| Song | `maintainTerminalOnLastLineChange` coalesces element mutations into one bracket | unit | `SongLineMaintenanceTest` | adequate | keep |
| Song | `isAutoMaintainedTerminal` — true only last-of-last-line + valid terminal | unit | `LineMutationTest.SelectabilityPredicate` | adequate | keep |
| Song | `isInteractable` — false for auto-maintained terminal | unit | `LineMutationTest.SelectabilityPredicate`, `HorizontalAdjustmentTest.SnapToEndSkipped` | adequate | keep |
| Song | `currentTerminalType()` — type at last position; throws on empty last line | unit | `FinalBarlineActionEnablementTest`, `BarlineMenuTest` | adequate (happy) | error path (empty last line) untested → add test |
| Song | `canReplaceTerminal` — valid terminal AND differs from current | unit | `FinalBarlineActionEnablementTest` (indirect) | inadequate | write direct predicate test (3 cases) |
| Song | `replaceTerminal(type)` — no-op same; replace; throws IAE for non-terminal | unit | `HorizontalAdjustmentTest`, `FinalBarlineActionEnablementTest`, `BarlineMenuTest` | missing (error path) | write test: `replaceTerminal(non-terminal)` throws IAE |
| Song | `newTerminalElement(type)` — throws IAE for non-terminal | unit | — | missing | write test asserting IAE |
| Song | `withModification` — depth balanced on exception; no notify on empty body; body runs once | unit | `SongBracketTest.WithModificationLifecycle` | adequate | keep |
| Song | `applyChange` — throws outside bracket; accumulates inside | unit | `SongBracketTest` | adequate | keep |
| Song | nested brackets fire single `SongDidChangeNotification` at outermost close | unit | `SongBracketTest.NestedBrackets` | adequate | keep |
| Song | `endModification` posts only when accumulated != null | unit | `SongBracketTest.testEmptyBodyDoesNotPostNotification` | adequate | keep |
| Song | `withoutMutationTracking` — nested suspend/resume via depth counter | unit | — | missing | write nested-call test |
| Song | `endSuspendMutationTracking` — throws ISE without matching begin | unit | — | missing | write test asserting ISE |
| Song | `documentWasSaved(@Handler)` — sets modified=false | unit | — | missing | write handler test |
| Song | `tempoDidChange(@Handler)` — skip all-null; init when null; clone-before-mutate; emit `MetadataChange(TEMPO)` | unit | — | missing | write 4 handler tests |
| Song | `keySignatureDidChange(@Handler)` — song-level propagates to matching lines only; per-line changes one line | unit | — | missing | write both-branch tests |
| Song | `layoutDidChange(@Handler)` — dispatch to setters for non-null fields; setByUser from `topPaddingSetByUser` | unit | — | missing | write dispatch test |
| Song | `metadataDidChange(@Handler)` — coalesce field mutations into one notification | unit | `SongMetadataDialogFlowTest` | adequate | keep |
| Song | modified flag true after first real mutation | unit | `SongLineMaintenanceTest.ModifiedFlag` | adequate | keep |
| Song | `postWithModification` ≡ `withModification(post(message))` | none | — | none | trivial delegation |
| Song | `Song(SongData)` loading ctor — subscribes, no default line | unit | `SongLoadingTest` (fixture) | inadequate | covered by loadFrom improvement above |
| Song | `newParsingStub()` — stub skips default-ctor setup | none | — | none | internal factory; behavior only via loadFrom |

**1A notes (quality concerns):** `getTempoAt` (backward walk across line/note boundaries) and the three `@Handler` methods (`tempoDidChange`, `keySignatureDidChange` with its propagation loop, `layoutDidChange`) have **zero** coverage — the highest-risk gaps. `SongLineMaintenanceTest` never uses `REPEAT_RIGHT` as the outgoing terminal, so both non-default `terminalTypeToInstall` paths are untested. `loadFrom` is only exercised via fixture round-trip, not by a direct field-mapping unit test. `SongDefaultsTest.testDefaultTempo`'s `isNotNull()` is substantive (it reads the tempo's fields afterward) — not a defect.

### 1B. `Line`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| Line | `addElement(e)` inserts before auto-maintained terminal when present, else appends | unit | — | missing | write test: lands at `count-1` w/ terminal, `count` otherwise |
| Line | `addElement(0, e)` migrates initial TempoChangeAttachment to new first element (line 0) | unit | — | missing | write test asserting migration |
| Line | `addElement(i, e)` rejects FINAL_DOUBLE_BARLINE on non-last line / non-end index | unit | `LineMutationTest.TerminalGuards` (3) | adequate | keep |
| Line | `addElement(i, e)` removes tuplet spanning insertion index | unit | — | missing | write test |
| Line | `addElement(i, e)` removes Endings invalidated by insertion | unit | `LineMutationTest.EndingInvalidationConditions` | adequate | keep |
| Line | `removeElement(i)` fires `ElementDeletion`, shrinks list | unit | `LineMutationTest.RemoveElement.testFiresSingleElementDeletion` | adequate | keep |
| Line | `removeElement(i)` removes range elements invalidated by anchor/end deletion | unit | `LineMutationTest.RemoveElement` (2) | adequate | keep |
| Line | `removeElement(i)` rejects auto-maintained terminal removal | unit | `LineMutationTest.TerminalGuards.testRemoveFinalBarlineOnLastLineThrows` | adequate | keep |
| Line | `removeRange(a,b)` fires `ElementRangeDeletion` w/ correct indices/list | unit | `LineMutationTest.RemoveRange` (2) | adequate | keep |
| Line | `removeRange(a,b)` removes range elements invalidated by range deletion | unit | `LineMutationTest.RemoveRange` (2) | adequate | keep |
| Line | `removeRange(a,b)` rejects range including auto-maintained terminal | unit | `LineMutationTest.TerminalGuards.testRemoveRangeIncluding...` | adequate | keep |
| Line | `setElement(i, e)` fires `ElementReplacement` | unit | — | missing | write test asserting single replacement w/ correct old/new |
| Line | `setElement` updates surviving range elements' anchor/end refs after swap | unit | — | missing | write test: tie anchored at 0; `setElement(0,new)`; assert `tie.anchor==new` |
| Line | `setElement` guard: FINAL_DOUBLE_BARLINE on non-last line/position | unit | `LineMutationTest.TerminalGuards` (2) | adequate | keep |
| Line | `setElement` removes Endings invalidated by replacement | unit | `LineMutationTest.EndingInvalidationConditions.*SetElement*` (6) | adequate | keep |
| Line | `modifyElement` clones before mutation (pre-snapshot for `ElementModification.beforeElement`) | unit | — | missing | write test asserting pre-mutation snapshot |
| Line | `modifyElement` w/ DURATION_AFFECTING field removes overlapping tuplets | unit | — | missing | write test |
| Line | `effectiveElementCount()` excludes trailing auto-maintained terminal | unit | (only e2e helper, never asserted) | missing | write unit test comparing `elementCount` vs `effectiveElementCount` |
| Line | `isInHairpinRange(i)` — inside/outside/boundary of cresc/dim | unit | `LineIsInHairpinRangeTest` (5) | adequate | keep |
| Line | `addBeaming` — no overlap: added as-is | unit | `BeamToggleTest.ToggleBeam.testToggleBeamOn` (via MusicEditOperations) | adequate | keep |
| Line | `addBeaming` — adjacent/overlapping merge (absorb shared endpoint) | unit | — | missing | write test: [0,2]+[2,4]→[0,4] |
| Line | `addBeaming` — subsumed beam removed when new span covers it | unit | — | missing | write test: [1,3]+[0,4]→[0,4] |
| Line | `removeBeaming` — absent beam is no-op | unit | — | missing | write test |
| Line | `isStartOfAnyBeam`/`isEndOfAnyBeam` | unit | — | missing | write tests |
| Line | `findBeamAt(i)` — beam in range else null | unit | `BeamToggleTest`/`BatchMutationTest` | adequate | keep |
| Line | `findBeamsOverlapping(a,b)` | unit | — | missing | write test |
| Line | `addTie` merge: adjacent/overlapping ties merge | unit | — | missing | write test: [0,1]+[1,2]→[0,2] |
| Line | `findTieAt(i)` | unit | `TieToggleTest.testTieCreationAndRemoval` | adequate | keep |
| Line | `findTies()` | unit | — | missing | write test |
| Line | `addTuplet`/`removeTuplet` fire `TupletAddition`/`TupletRemoval` | unit | `MusicEditOperationsMutationTest.testToggleTuplet*` | adequate | keep |
| Line | `findTupletAt(i)` | unit | `SelectionCoordinatorValidateSpansTest` (setup only) | inadequate | write dedicated return-value test |
| Line | `findTupletsOverlapping(a,b)` | unit | — | missing | write test |
| Line | `removeOverlappingTuplets(a,b)` — one `TupletRemoval` per tuplet | unit | — | missing | write test |
| Line | `addCrescendo`/`addDiminuendo` — same-type hairpin merge | unit | `DynamicsMarkingTest` (e2e, non-merging) | wrong-level | write unit test for merge; e2e smoke fine to keep |
| Line | `isInsideGraceHostPair(i)` | unit | — | missing | write test |
| Line | `isPairedGraceNote(i)` — grace w/ CONNECTED glissando | unit | (indirect via isHostOfPairedGraceNote) | missing | write direct test |
| Line | `precedingGraceNoteIndex(i)` | unit | — | missing | write test |
| Line | `isHostOfPairedGraceNote(i)` | unit | `LineGraceNotePairingTest` (5) | adequate | keep |
| Line | `keyExists(pitchType)` | unit | — | missing | write test (flats/sharps, various counts) |
| Line | `setKeyAccidentalCount` fires `LineKeyChange`; no-op when unchanged | unit | — | missing | write test |
| Line | `setKeyType` fires `LineKeyChange` | unit | `LayoutEngineTest` (setup only) | inadequate | write test asserting mutation |
| Line | `attachInitialTempoIfNeeded()` | unit | — | missing | write test |
| Line | `changeElementSpacingRatio(f)` fires `LineLayoutChange` w/ accumulated ratio | unit | — | missing | write test |
| Line | legacy Y-pos setters (`setTempoChangeYPosPx`/`setBeatChangeYPosPx`/`setLyricsYPosSs`/`setFirstSecondEndingYPosPx`/`setTrillYPosPx`) | none | — | none | trivial setters |
| Line | `adjustSyllablesForNeighborChange` — insertion breaks BEGIN/MIDDLE chain; deletion preserves | unit | `LineMutationTest.SyllableAdjustment` (8) | adequate | keep |
| Line | `adjustSyllablesForSuccessorAfterInsertion` — MIDDLE→BEGIN, END→SINGLE | unit | `LineMutationTest.SyllableAdjustmentOnInsertion` (6) | adequate | keep |
| Line | `adjustExtendsForDeletion` — START cascade-clear, CONTINUE heal, STOP promote | unit | `LineMutationTest.ExtendAdjustment` (9) | adequate | keep |
| Line | `adjustExtendsForInsertion` — START→NONE, CONTINUE→STOP, STOP/NONE no-op | unit | `LineMutationTest.ExtendAdjustmentOnInsertion` (6) | adequate | keep |
| Line | `backfillSyllabic()` — normalize chain after legacy load (idempotent) | unit | — | missing | write test on stale markers |
| Line | `setSyllableBoundary(...)` — derive syllabic + propagate to next | unit | — | missing | write test |
| Line | `adjustNeighborsForLyricDeletion(...)` | unit | — | missing | write test |
| Line | `deriveSyllabic(prev, this)` — pure 4-quadrant truth table | unit | `SyllabicDerivationTest` (implicit via callers) + `LineMutationTest.SyllableAdjustment` | adequate | keep (4-case fn well-exercised by callers) |
| Line | `applyChange` — throws ISE outside bracket when tracking active | unit | `LineMutationTest.LineConstructorInvariants.testApplyChangeThrowsWhenNotInBracket` | adequate | keep |
| Line | `applyChange` — runs mutator directly when tracking suspended | unit | — | missing | write test via `withoutMutationTracking` |
| Line | `hasEndingInvalidatedByDeletion`/`...ByInsertion` pre-flight checks | unit | — | missing | write tests |
| Line | `findRangeElementsAt(i)` | unit | — | missing | write test |
| Line | `findRangeElements(Class)` | unit | `BatchMutationTest` | adequate | keep |
| Line | `getFirstTempoChange()` — 0 for line 0; first index otherwise | unit | — | missing | write test |
| Line | `getFirstBeatChange()`/`getFirstTrill()`/`isAnnotation()` | unit | — | missing | write test each |
| Line | parentLine propagation on add (`setLine`/`setParentLine`) | unit | `ParentLinePropagationTest` (3) | adequate | keep |
| Line | `addElement[0]` fires `ElementInsertion` | unit | — | missing | write test |

**1B notes (quality concerns):** Beam/tie **merge logic** — the most complex code in `addBeaming`/`addTie` — is entirely untested (`BeamToggleTest`/`TieToggleTest` only build non-overlapping 2-element spans and test through `MusicEditOperations`). `SyllabicDerivationTest` is a **name-mismatch**: it tests `StaffElement.getLyricForVerse`/`Lyric` field storage, not `Line` derivation; harmless but misnamed and arguably misfiled. `LineGraceNotePairingTest` covers only `isHostOfPairedGraceNote`; its three companion predicates are untested. `addCrescendo`/`addDiminuendo` merge is covered only by a non-overlapping e2e smoke (`DynamicsMarkingTest`) — wrong level for the merge logic.

### 1C. element/note core — `StaffElement`, `LineElement`, `NoteBounds`, `AccidentalBounds`, `Beam`, `Tie`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StaffElement | `setLyricForVerse` truth-table (replace/remove/carrier/throw) | unit | `StaffElementTest` (9) | adequate | keep |
| StaffElement | `getLyricForVerse`/`getMainLyric`/`getLyrics` lookup + null | unit | `StaffElementCopyConstructorTest`, `LyricEditorTest` | adequate | keep |
| StaffElement | `isEligibleForLyric` — non-rest always; rest only if non-blank lyric for verse | unit | — | missing | write test (3 cases) |
| StaffElement | `getLedgerLineCount` boundary math | unit | `NoteAreaBuilderTest` (3, in ui/renderer) | adequate | keep (correct level, misfiled package) |
| StaffElement | `hasLedgerLines` delegates to count>0 | unit | — | missing | write test |
| StaffElement | `getPitch`/`calculatePitch` — MIDI pitch from staff pos + accidental + octave | unit | `GlissandoRendererTest` (3, relative only) | inadequate | add absolute MIDI-value assertions for known notes + accidental table |
| StaffElement | `getPitchIndex` — staff pos → 0–6 w/ octave wrap | unit | — | missing | write test for sp=0…±8 |
| StaffElement | `findLastAccidental` — inherit from same-position predecessor, else key sig | unit | `TiePitchValidationTest` (fixture, via canToggleTie) | wrong-level | add direct unit test on a 2-note line |
| StaffElement | `getDefaultDurationWithDots` — `DOTTED_DURATION[dotCount]` for 0/1/2 | unit | — | missing | write test (base, 1.5×, 1.75×) |
| StaffElement | `getDuration` — fermata extends 1.5× | unit | — | missing | write test w/ and w/o FermataAttachment |
| StaffElement | `findMidiDurationOverride` — first articulation's % override else -1 | unit | — | missing | write test (none→-1, staccato→%) |
| StaffElement | `setAccidental(null)` clears `isAccidentalInParentheses` | unit | `AccidentalInParensActionTest`, `StaffElementCopyConstructorTest` | inadequate | add: set accidental+parens, `setAccidental(null)`, assert parens false |
| StaffElement | `setAccidentalInParentheses` no-ops when accidental null | unit | `AccidentalInParensActionTest` (indirect) | inadequate | add direct null-accidental test |
| StaffElement | copy ctor `(ElementType, StaffElement)` — 4 note/rest combos + deep-copy isolation | unit | `StaffElementCopyConstructorTest` (6) | adequate | keep |
| StaffElement | clone ctor `(StaffElement)` — full-field deep copy | unit | `StaffElementCopyConstructorTest.testCloneCopyConstructorDeepCopiesLyrics` | inadequate | only lyrics isolation checked; add articulation + attachment isolation tests |
| StaffElement | `addArticulation`/`removeArticulation` — wire owner/parent/line, maintain children | unit | `ParentLinePropagationTest` (attachments only) | inadequate | add `removeArticulation` owner-unset + child-removal test |
| StaffElement | `clearArticulations` — unset owner each, remove children | unit | — | missing | write test |
| StaffElement | `clearAttachments` — unset owner each, remove children | unit | — | missing | write test |
| StaffElement | `hasArticulation(type)` | unit | — | missing | write test |
| StaffElement | `setLine` propagates to all attachments + articulations | unit | — | missing | write test |
| LineElement | `getMarginBounds` — origin−margins, size+margins | unit | — | missing | write test |
| LineElement | `collapsedVerticalMarginWith` — CSS max-collapse | unit | — | missing | write test (a>b, a<b, a==b) |
| LineElement | `collapsedHorizontalMarginWith` — CSS max-collapse | unit | — | missing | write test (3 cases) |
| LineElement | `addChild` — set parentElement + parentLine | unit | `ParentLinePropagationTest` (indirect) | adequate | keep |
| LineElement | `removeChild` — clear parentElement; ignore non-child | unit | `ParentLinePropagationTest` (indirect) | inadequate | ignore-non-child path untested; add test |
| LineElement | `clearChildren` — clear each parentElement; empty list | unit | — | missing | write test |
| LineElement | `setMarginSs(d)` — uniform all four | unit | — | missing | write test |
| LineElement | `setMarginSs(t,r,b,l)` — CSS shorthand | unit | — | missing | write test |
| NoteBounds | `headOnly` factory — all three bounds equal head bounds | unit | — | missing | write test |
| NoteBounds | `withStem` factory — articulations bounds == stem bounds | unit | — | missing | write test |
| NoteBounds | `getStemSideBounds` stem-up → upper half | unit | — | missing | write test (known geometry) |
| NoteBounds | `getStemSideBounds` stem-down → lower half | unit | — | missing | write test |
| NoteBounds | `getOppositeFromStemBounds` stem-up → lower half | unit | — | missing | write test |
| NoteBounds | `getOppositeFromStemBounds` stem-down → upper half | unit | — | missing | write test |
| NoteBounds | `translate(dx,dy)` — new instance shifted, stemUp preserved | unit | — | missing | write test |
| NoteBounds | `getCenterX`/`getCenterY` — from head bounds (not full) | unit | — | missing | write test (distinct head vs full) |
| NoteBounds | `getTop`/`getBottom`/`getAttachmentTopY`/`getAttachmentBottomY` — from articulations bounds | unit | — | missing | write test |
| AccidentalBounds | pure data record | none | — | none | trivial record |
| Beam | `getSpanWidthSs` — `max(1.0, end−anchor)` clamp | unit | — | missing | write test (3 branches) |
| Beam | `getContentHeightSs`/`getContentWidthPx`/`getContentHeightPx` → 0 sentinels | none | — | none | trivial constants |
| Tie | `getContentHeightSs` → `TIE_ARC_HEIGHT_SS` | unit | `TieTest.testContentHeightSsMatchesStylesheetConstant` | adequate | keep |
| Tie | `getContentHeightPx` → ssToPx of constant | unit | `TieTest.testContentHeightPxIsToPixelsOfSs` | adequate | keep |
| Tie | `getSpanWidthSs` — `max(1.0, end−anchor)` clamp | unit | — | missing | write test (3 branches) |
| Tie | `isAbove` — anchor `isUpper()`→true; stem-up→false; null anchor→false | unit | — | missing | write test (3 cases) |
| Tie | creation/removal/persistence round-trip | unit | `TieToggleTest` (2) | adequate | keep |

**1C notes (quality concerns):** `getPitch`/`calculatePitch` tested only with **relative** equality (`GlissandoRendererTest`) — a systematic octave/pitch-table offset would pass; absolute MIDI assertions needed. `getLedgerLineCount` adequately tested but the test lives in `ui/renderer` though the logic is pure `dom`. `NoteBounds` stem-side/opposite geometry and `Beam`/`Tie` `getSpanWidthSs` clamp are entirely unasserted. `TieTest` (despite the name) covers only the height constant, not `getSpanWidthSs`/`isAbove`. `StaffElementCopyConstructorTest.testGetMainLyricReturnsFirstLyric` is a mild name-mismatch (contract is "verse-1", test data only has verse-1).

### 1D. element typing & pitch system — `ElementType`, `RangeElement`, `KeySignature`, `ScaleContext`, `StructuralElement`, `Clef`, `Duration`, `KeyType`, `ElementLocation`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementType | all types have non-zero bounds after static init | unit | `ElementTypeTest.testAllVisualTypesHaveNonZeroBounds` | adequate | keep |
| ElementType | `getFullElementCenterXSs()` = width/2 | unit | `ElementTypeTest.testCenterXIsHalfWidth` | adequate | keep |
| ElementType | `getFlagGlyph(upper)` per flagged type × direction | unit | `ElementTypeTest.testGetFlagGlyphReturnsCorrectGlyphForFlaggedTypes` | adequate | keep |
| ElementType | `getFlagGlyph(GRACE_QUAVER)` always FLAG_8TH_UP | unit | `ElementTypeTest.testGetFlagGlyphReturnsEighthFlagForGraceQuaver` | adequate | add explanatory comment (asymmetry intentional; assertion can fail — keep) |
| ElementType | `getFlagGlyph` null for non-flagged types | unit | `ElementTypeTest.testGetFlagGlyphReturnsNullForNonFlaggedTypes` | adequate | keep |
| ElementType | `isDuration()` — notes/rests true; grace/barline/breath/glissando false | unit | `ElementTypeTest.testIsDuration*` (3) | adequate | keep |
| ElementType | `toNote()`/`toRest()` — 6-pair bidirectional + identity | unit | `ElementTypeTest.testToNote*`/`testToRest*` (6) | adequate | keep |
| ElementType | stemmed `getElementHeightSs(true)` ≠ `(false)` | unit | `ElementTypeTest.testStemmedNoteHeightIsDirectionDependent` | inadequate | asserts only `>0`; add directional `!=` assertion for CROTCHET |
| ElementType | barline/repeat heights == STAFF_HEIGHT_SS | unit | `ElementTypeTest.ElementHeightTests` (2) | adequate | keep |
| ElementType | SEMIBREVE height same both directions | unit | `ElementTypeTest.ElementHeightTests.testSemibreveHeightIsSameBothDirections` | adequate | keep |
| ElementType | barline width arithmetic (single/double/final/repeat) | unit | `ElementTypeTest.ElementWidthTests` (4) | adequate | keep |
| ElementType | grace `fullWidthSs` < regular QUAVER | unit | `ElementTypeTest.ElementWidthTests.testGraceNoteWidthIsScaled` | adequate | keep |
| ElementType | stemmed flagged width > unflagged; notehead width consistent | unit | `ElementTypeTest.ElementWidthTests` (2) | inadequate | `testStemmedNoteWidthIncludesFlagExtent` uses `>=` (allows equality); should be `>` |
| ElementType | SEMIBREVE width == full width (no flag) | unit | `ElementTypeTest.ElementWidthTests.testSemibreveWidthFromBBox` | adequate | keep |
| ElementType | `isBeamable()` | unit | — | missing | write membership test |
| ElementType | `isRepeat()`/`isBarLine()` | unit | — | missing | write membership tests |
| ElementType | `isNonDuration()` (excludes GLISSANDO) | unit | — | missing | write membership test |
| ElementType | `isContentElement()`/`isNonContentElement()` | unit | — | missing | write membership tests |
| ElementType | `isTerminal()`/`isValidTerminal()`/`isReplaceableByTerminal()` (REPEAT_LEFT exclusion) | unit | — | missing | write membership tests (non-obvious exclusion) |
| ElementType | `snapToEnd()` membership | unit | `HorizontalAdjustmentTest` (integration only) | wrong-level | add dedicated membership unit test |
| ElementType | `drawStaveLongitude()` — false only BREATH_MARK | unit | — | missing | write test |
| ElementType | `endingAnchorXOffsetSs()` — 3-branch formula | unit | — | missing | write test per branch |
| ElementType | `terminalFlushRightXSs(lineWidth, type)` = lineWidth − baseWidth | unit | `LayoutEngineTest` (self-referential) | inadequate | write direct arithmetic test (LayoutEngineTest uses it as its own oracle) |
| ElementType | `getSMuFLGlyph()` mapping (barlines null) | unit | — | missing | write map-contents test |
| ElementType | `isPitchedNote()`/`isNote()`/`isNoteWithStem()`/`isGraceNote()` | unit | (used as loop predicate, not asserted) | missing | write predicate test across full type set |
| ElementType | alias types share bounds/instance with canonical | unit | `testAllVisualTypesHaveNonZeroBounds` (partial) | adequate (partial) | optional: add alias==canonical width equality |
| RangeElement | `isInvalidatedBy(deleted)` — anchor/end/both/middle/external | unit | `RangeElementInvalidationTest` (5 params × 6 subtypes, in layout/) | adequate | keep |
| RangeElement | `getElementCount()` — `end−start+1`; 0 when null/not-in-line | unit | — | missing | write test |
| RangeElement | `getContentWidthSs()` — `|endX−anchorX|+endWidth`; 0 when null | unit | — | missing | write test |
| RangeElement | base `isInvalidatedBy{Insertion,Deletion,Replacement}` return false (hooks) | none | — | none | trivial defaults; subclass overrides tested in `EndingInvalidationTest` |
| RangeElement | base `isAbove()` returns true | none | — | none | trivial default |
| KeySignature | default ctor → NONE/0 | unit | `KeySignatureTest.EmptySignature` (in layout/) | adequate | keep |
| KeySignature | ctor clamps accidentalCount to 0–7 | unit | — | missing | write test (−1→0, 8→7) |
| KeySignature | `hasAccidentals()` — count=0 false; NONE false; else true | unit | `KeySignatureTest` (indirect via dimensions) | inadequate | add direct assertions (3 conditions) |
| KeySignature | `getContentWidthSs()` — count × glyph bbox width; 0 when none | unit | `KeySignatureTest.Sharps`/`Flats`/`EmptySignature` | adequate | keep |
| KeySignature | `getContentHeightSs()` — glyph bbox height; 0 when none | unit | `KeySignatureTest.Sharps`/`Flats` | adequate | keep |
| KeySignature | px methods delegate to ssToPx | unit | `KeySignatureTest.testPxDerivesFromSs` | adequate | keep |
| KeySignature | `setAccidentalCount` clamps (same guard) | unit | — | missing | write test (−1→0, 8→7) |
| ScaleContext | `ssToPx(ss)` = pps × ss | unit | (used as collaborator only) | inadequate | write direct test w/ known pps |
| ScaleContext | `ssToRoundedPx(ss)` rounds to nearest int | unit | — | missing | write test (round down/up) |
| ScaleContext | `pxToSs(px)` = px / pps | unit | — | missing | write direct test |
| ScaleContext | `setPixelsPerStaffSpace` throws IAE for ≤0 | unit | — | missing | write test (0 and negative) |
| ScaleContext | `getScaleTransform()` correct scale factor | unit | — | missing | write test |
| ScaleContext | `scaleFont(font)` — size in ss units | unit | (test setup only) | inadequate | write direct test |
| ScaleContext | `textWidthSs`/`textHeightSs`/`fontAscentSs`/`fontDescentSs`/`fontMaxAscentSs` wrap pxToSs(metric) | unit | (helpers only, never tested directly) | missing | write a test each vs `pxToSs` of the pixel metric |
| StructuralElement | `getStaffPosition()` always type default (ignores stored pitch) | unit | `StaffElementCopyConstructorTest` (indirect) | inadequate | write direct test (CROTCHET_REST) |
| StructuralElement | `getDotCount()` — rests delegate to super; non-rests always 0 | unit | — | missing | write test (barline→0 even after setDotCount; rest preserves) |
| StructuralElement | `getAccidental()` always null | unit | `StaffElementCopyConstructorTest` | adequate | keep |
| StructuralElement | `clone()` returns `StructuralElement` w/ state copied | unit | `StaffElementCopyConstructorTest` (via copy ctor, not clone) | missing | write clone test asserting type + dot count |
| Clef | `getContentWidthPx`/`HeightPx` from G_CLEF bbox via ssToPx | unit | — | missing | write test vs bbox |
| Duration | `getNote()` returns a clone (not shared instance) | unit | — | missing | write identity-≠ test |
| Duration | dotted variants → dotCount=1, staffPosition=1 | unit | — | missing | write test (3 dotted constants) |
| Duration | non-dotted variants → dotCount=0 | unit | — | missing | write test |
| Duration | each constant's note has expected ElementType | unit | — | missing | write test (all 7) |
| KeyType | pure enum, no methods | none | — | none | no test warranted |
| ElementLocation | ctor rejects negative line/element index | unit | — | missing | write test (IAE both) |
| ElementLocation | `matches(l,e)` iff both equal | unit | — | missing | write test (3 cases) |
| ElementLocation | zero indices valid (boundary) | unit | — | missing | write test |

**1D notes (quality concerns):** **`ScaleContext` is the highest-leverage gap** — every pixel dimension in the app flows through `ssToPx`/`ssToRoundedPx`/`pxToSs`, yet it is used everywhere as a collaborator and unit-tested nowhere; a bug shifts all geometry silently. Three concrete test defects: `testStemmedNoteHeightIsDirectionDependent` asserts only `>0` (can't catch up/down swap); `testStemmedNoteWidthIncludesFlagExtent` uses `>=` where the contract is strictly `>`; `LayoutEngineTest` uses `terminalFlushRightXSs` as both expected value and code-under-test (self-referential). `ElementType`'s many predicate-membership methods (`isBeamable`, `isTerminal` family, `isPitchedNote` family) are used in production but never directly asserted. `ElementLocation` has no tests anywhere. (Note: `RangeElementInvalidationTest`, `KeySignatureTest` correctly test `dom` classes but live under `layout/`.)

### 1E. attachment family — `Attachment`, `Annotation`, `AnnotationAttachment`, `DynamicAttachment`, `FermataAttachment`, `MetronomeAttachment`, `BeatChangeAttachment`, `TempoChangeAttachment`, `BeatChange`, `Tempo`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| Attachment | `Alignment` enum + owner/alignment accessors | none | — | none | trivial |
| Attachment | `copy(StaffElement)` contract — same-typed instance re-owned by newOwner | unit | `StaffElementCopyConstructorTest` (presence only) | inadequate | write per-subclass tests: correct type, fields preserved, owner==newOwner |
| Annotation | `ABOVE`/`BELOW` constants derived from `ssToPx(−2)`/`ssToPx(4)` | unit | — | missing | write relative-position test (ABOVE<0, BELOW>0, BELOW>|ABOVE|) |
| Annotation | `userYOffsetSs` default 0 + round-trip | none | — | none | trivial accessors |
| Annotation | `yPosPx` default == ABOVE | unit | — | missing | write test |
| AnnotationAttachment | `computeContentWidthSs(font)` via `textWidthSs` | unit | `AnnotationAttachmentTest` (height only) | inadequate | add width test w/ known font/string |
| AnnotationAttachment | `computeContentHeightSs(font)` via `textHeightSs` | unit | `AnnotationAttachmentTest.testUsesProvidedFont` | adequate | keep (but see defect note) |
| AnnotationAttachment | `getContentWidth/HeightSs/Px()` throw UnsupportedOperationException | unit | — | missing | write test (all 4 throw — contract guard) |
| AnnotationAttachment | `setText`/`getText` via inner Annotation | none | — | none | trivial delegation |
| DynamicAttachment | `DynamicType` — symbol/glyph/velocityFraction for all 8 | unit | `DynamicAttachmentTest.DynamicTypeFields` | adequate | keep (but see defect note) |
| DynamicAttachment | `getContentWidth/HeightSs()` — bbox path + fallback path | unit | `DynamicAttachmentTest.Dimensions` (4) | adequate | keep |
| DynamicAttachment | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write px=ss×scale tests |
| DynamicAttachment | serialization round-trip preserves DynamicType | unit | `DynamicAttachmentPersistenceTest`, `StaffElementIOTest.DynamicSerialization` | adequate | keep |
| DynamicAttachment | `copy()` — new instance, same type + newOwner | unit | `StaffElementCopyConstructorTest` (presence only) | inadequate | add type + owner assertions |
| FermataAttachment | `getContentWidth/HeightSs()` SMuFL constants | unit | `FermataTrillStackingTest.testFermataHasPositiveDimensions` (`>0`) | inadequate | write direct test vs exact bbox values |
| FermataAttachment | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write px-matches-ssToPx test |
| FermataAttachment | `copy()` — new instance, owner preserved | unit | `StaffElementCopyConstructorTest` (presence only) | inadequate | add owner assertion |
| MetronomeAttachment | `metronomeGlyphFor(ElementType)` — 6 notes + default null | unit | — | missing | write parametrized test |
| MetronomeAttachment | `dotAdvanceWidthSs()` — bbox advance × NOTE_SCALE | unit | — | missing | write test |
| MetronomeAttachment | `noteWidthSs(el)` — 0 for unmapped; +dot width for dotted | unit | `BeatChangeAttachmentTest` (indirect) | inadequate | write direct tests (undotted, dotted, barline→0) |
| MetronomeAttachment | `getContentHeightSs()` — QUARTER_NOTE_HEIGHT_SS | unit | `BeatChangeAttachmentTest` | adequate | keep |
| BeatChangeAttachment | `computeContentMetrics(font)` — 3-region geometry, total width, descent | unit | `BeatChangeAttachmentTest` (7) | adequate | keep |
| BeatChangeAttachment | `copy()` — same beatChange + newOwner | unit | — | missing | write test |
| BeatChange | `fromLegacyName` — canonical names → duration pairs | unit | (only error path covered) | inadequate | write parametrized test for all 5 names + aliases |
| BeatChange | `fromLegacyName` unknown → IAE w/ message | unit | `StaffElementIOTest.testUnknownBeatChangeThrowsMeaningfulError` (wrapped) | adequate (indirect) | keep; optionally add direct test |
| TempoChangeAttachment | `computeContentMetrics` showTempo=true → glyph+text regions | unit | `SystemTierStackingTest` (`>0` only) | inadequate | write direct region/width test |
| TempoChangeAttachment | showTempo=false + description → text-only, glyph width 0 | unit | — | missing | write test |
| TempoChangeAttachment | showTempo=false + empty description → zero width, no regions | unit | — | missing | write test |
| TempoChangeAttachment | `copy()` — same tempo + newOwner | unit | — | missing | write test |
| Tempo | `getRealTempo()` — `(visibleTempo × noteDuration)/PPQ` | unit | — | missing | write test (CROTCHET + MINIM to catch divisor regression) |
| Tempo | default ctor → 120/CROTCHET/"Moderate"/show=true | unit | `SongDefaultsTest.testDefaultTempo` | adequate | keep |
| Tempo | `shouldShowTempo()` fallback when song tempo null | unit | `SongDefaultsTest.testEffectiveTempoFallbackWhenTempoIsNull` | adequate | keep |

**1E notes (quality concerns):** **`BeatChange.fromLegacyName` happy paths are completely untested** — only the error path is covered; no test maps a valid legacy name to its expected record (most significant gap here). Multiple `copy()` verdicts are `inadequate` because `StaffElementCopyConstructorTest` checks attachment *presence* but never that the copy is a new object with updated owner. Tautological tests: `DynamicAttachmentTest.testUiTypesHaveNonNullGlyphs` (`isNotNull()` on hard-coded enum fields — replace with bbox-resolves check); `AnnotationAttachmentTest.testUsesProvidedFont` (`isCloseTo(textHeightSs(font))` calls the production method as its own oracle). `Fermata`/`Tempo`/`TempoChange` dimension/metric contracts are asserted only at `>0` through stacking tests (wrong level / weak). Cross-package: `VelocityMapTest` (midi/) gives good `DynamicType.getVelocityFraction()` coverage end-to-end.

### 1F. ornaments / dynamics / misc — `Articulation`, `ArticulationType`, `Hairpin`, `Trill`, `Tuplet`, `Crescendo`, `Diminuendo`, `Lyric`, `Attribution`, `EndingValidationResult`, `CollisionRegion`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| Articulation | ctor wires owner/parent/line | unit | `ParentLinePropagationTest` | adequate | keep |
| Articulation | `getContentWidth/HeightSs()` branch on `isStaccato()` → correct bbox | unit | `ArticulationStackingTest.PrecomposedGlyph` | adequate | keep |
| Articulation | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write test (both types) |
| ArticulationType | `getMidiDurationPercent()` (STACCATO=33, ACCENT=−1) | unit | — | missing | write test per constant |
| ArticulationType | `hasMidiDurationOverride()` (STACCATO true, ACCENT false) | unit | — | missing | write test |
| ArticulationType | `getDrawingOrder(false)` ascending order | unit | — | missing | write test |
| ArticulationType | `getDrawingOrder(true)` reversed order | unit | — | missing | write test |
| Hairpin | `getContentHeightSs()` → HAIRPIN_OPENING_HEIGHT_SS | unit | — | missing | write test |
| Hairpin | `getSpanWidthSs()` — `max(opening, endX−anchorX+NOTE_HEAD_WIDTH)` | unit | `ManualOffsetStackingTest.HairpinOffsets` (offsets, not formula) | inadequate | write both-branch test |
| Hairpin | `x1ShiftSs`/`x2ShiftSs`/`yShiftSs` stored/retrieved | unit | `ManualOffsetStackingTest.HairpinOffsets` (2) | adequate | keep |
| Trill | `getSpanWidthSs()` — `max(glyphWidth, endX−anchorX+glyphWidth)` | unit | `FermataTrillStackingTest` (`>span`, not formula) | inadequate | write both-branch exact-value test |
| Trill | `getContentWidth/HeightSs()` SMuFL bbox for ORNAMENT_TRILL | unit | `FermataTrillStackingTest.testTrillHasPositiveDimensions` (`>0`) | inadequate | write exact-bbox test |
| Trill | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write test |
| Trill | single-note ctor sets anchor==end | unit | `FermataTrillStackingTest.testSingleNoteTrillPositionedAboveNote` | adequate | keep |
| Trill | `yPositionSs` stored + applied | unit | `ManualOffsetStackingTest.TrillOffsets.testTrillYPositionApplied` | adequate | keep |
| Tuplet | `getElementCount()` returns `grade` | unit | (tests assert `getGrade()`, never `getElementCount()`) | missing | write test (grade 3 and 5) |
| Tuplet | `getSpanWidthSs()` — `max(1.0, endX−anchorX)` | unit | — | missing | write both-branch test |
| Tuplet | `getContentHeightSs()` → TUPLET_BRACKET_HEIGHT_SS | unit | `TupletTest.testContentHeightSsMatchesStylesheetConstant` | adequate | keep |
| Tuplet | `getContentHeightPx()` via ssToPx | unit | `TupletTest.testContentHeightPxIsToPixelsOfSs` | adequate | keep |
| Crescendo | pass-through ctor to Hairpin | none | — | none | trivial delegation |
| Diminuendo | pass-through ctor to Hairpin | none | — | none | trivial delegation |
| Lyric | ctor rejects null syllabic on non-carrier | unit | `LyricTest.testInvariantRejectsNullSyllabicOnTextLyric` | adequate | keep |
| Lyric | ctor rejects non-null syllabic on carrier (STOP) | unit | `LyricTest.testInvariantRejectsCarrierWithSyllabic` | adequate | keep |
| Lyric | ctor rejects compound=true on carrier | unit | — | missing | write test w/ Extend.CONTINUE + compound (covers CONTINUE branch) |
| Lyric | record equality/hash across syllabic + compound | unit | `LyricTest` (2) | adequate | keep |
| Lyric | carrier (STOP) has syllabic null | unit | `LyricTest.testCarrierLyricWithNullSyllabicEqualsItself` | adequate | keep |
| Attribution | ctor sets ATTRIBUTION_MARGIN_BOTTOM_SS | unit | — | missing | write test |
| Attribution | `computeContentWidthSs(font)` via textWidthSs | unit | `AttributionTest.testComputeContentWidthSsUsesStringWidth` | adequate | keep |
| Attribution | `computeContentHeightSs(font)` via textHeightSs | unit | `AttributionTest.testComputeContentHeightSsUsesFontMetrics` | adequate | keep |
| Attribution | `getContentWidth/HeightSs/Px()` all throw UnsupportedOperationException | unit | — | missing | write test (all 4 throw) |
| Attribution | `isRightAligned` default true + round-trip | unit | — | missing | write test |
| EndingValidationResult | `invalid()` → isValid false | unit | `MusicEditOperationsMutationTest` (indirect) | inadequate | write direct test |
| EndingValidationResult | `valid(action, start, end)` → isValid true + accessors | unit | `MusicEditOperationsMutationTest`/`ScoreViewControllerCommandHandlerTest` (fixture only) | inadequate | write test asserting all 3 accessors per PrecedingAction (NONE/INSERT_BARLINE/EXTEND_SPAN) |
| CollisionRegion | pure data record | none | — | none | trivial record |

**1F notes (quality concerns):** `ArticulationType` MIDI + drawing-order logic is used in production (`StaffElement`) but entirely untested at unit level. `Tuplet.getElementCount()` and the `getSpanWidthSs` clamps across `Hairpin`/`Trill`/`Tuplet` are exercised only obliquely through wide-span stacking tests — narrow-span branches are dead from a test perspective. `EndingValidationResult` is used only as a fixture; its accessors are never asserted, and the production-emitted `EXTEND_SPAN` action is untested. `Attribution`'s four `UnsupportedOperationException` guards have no safety net.

### dom — production observations (out of test-audit scope)

- **`FermataAttachment(@Nullable StaffElement)`** calls `setOwnerElement(parent)` twice (lines 59 and 63): once unconditionally, then again inside the `if (parent != null)` block. The second call is a redundant no-op (idempotent for the same owner) — harmless, not behavior-affecting, so no regression test is warranted. Worth a one-line cleanup during remediation, not a matrix row.

### dom — summary

Audited all 38 production classes (excl. `package-info`). Dominant patterns to drive remediation:

1. **Pure conversion/geometry math is the biggest blind spot.** `ScaleContext` (ssToPx/pxToSs/rounding) and the `getSpanWidthSs`/`get*Px` clamp-and-convert methods across `Beam`/`Tie`/`Hairpin`/`Trill`/`Tuplet`/`NoteBounds` are exercised only as collaborators in higher-level tests — never asserted directly. These are cheap, high-value unit tests.
2. **"Weak-but-green" tests give false confidence:** relative-only pitch assertions (`getPitch`), `>0`/`>=` assertions where exact values matter (`ElementType` width/height, `Fermata`/`Trill` dimensions), self-referential oracles (`terminalFlushRightXSs` in `LayoutEngineTest`), and tautologies (`isNotNull()` on non-null enum fields; `isCloseTo` calling the production method). These are PIT-detectable (see methodology note below).
3. **Untested branch/error paths:** `Song` `@Handler` methods + `getTempoAt` + REPEAT_RIGHT terminal carry-over; `Line` beam/tie/hairpin **merge** logic + grace-pair predicates; `BeatChange.fromLegacyName` happy paths; numerous IAE/ISE guards.
4. **`copy()` contracts** verify presence but not new-object identity or owner re-wiring across the attachment family.
5. **Misfiled-but-adequate:** `getLedgerLineCount` (tested in `ui/renderer`), several `dom` classes tested under `layout/` (`RangeElementInvalidationTest`, `KeySignatureTest`) — relocate during rewrite, not re-test.
