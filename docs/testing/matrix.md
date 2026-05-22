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
| 2 | `io` | 16 | done |
| 3 | `layout` | 39 | done |
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

## 2. `io` (audited 2026-05-21)

Audited all 15 production classes (excl. `package-info`) via four parallel production-first sub-audits: **orchestration & XML**; **element & annotation serialization**; **line & view serialization**; **migration & legacy import**. Read-only; e2e assessed from source only; no `io` behavior warranted e2e (serialization/migration is data-driven logic — prime unit territory). Coverage checked across unit (mirrored + cross-package) and e2e. Five verdicts reclassified from the sub-audits' `wrong-level` (vocabulary reserves that for unit↔e2e mismatches; these are unit tests covered only indirectly).

### 2A. orchestration & XML — `SongIO`, `SongLoader`, `SongLoadResult`, `XML`

#### SongIO

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| SongIO | `writeSong` — emits XML header and `<song>` root with version attribute (`IO_MAJOR_VERSION.IO_MINOR_VERSION`) | unit | `SongIOTest` (round-trip, indirect) | inadequate | literal version value never asserted; add direct serialized-string assertion that version attribute equals current `IO_MAJOR.IO_MINOR` |
| SongIO | `writeSong` — tempo block emitted only when song tempo non-null | unit | — | missing | null tempo → no `<tempo>`; with tempo → present |
| SongIO | `writeSong` — title/place/year/attribution/underlyrics/banglaLyrics/translatedLyrics omitted when empty | unit | — | missing | parametrized: empty → tag absent; non-empty → present, XML-escaped |
| SongIO | `writeSong` — month/day omitted when ≤ 0; emitted when > 0 | unit | — | missing | month=0 → absent; month=3 → `<month>3</month>` |
| SongIO | `writeSong` — `unofficialTranslation` emitted only when true | unit | — | missing | false → absent; true → present |
| SongIO | `writeSong` — `topspace` emitted only when `userSetTopPadding()` | unit | — | missing | false → absent; true → present |
| SongIO | `writeSong` — `rowheight` omitted when exactly 0 | unit | — | missing | 0 → absent; non-zero → present |
| SongIO | `writeSong` — `dynamicLayout=true` always written | unit | — | missing | output always contains `<dynamicLayout>true</dynamicLayout>` |
| SongIO | `writeSong` — `linewidth` always written | unit | `SongIOTest.LegacyMigrationWiring.*` (round-trip) | adequate | round-trip recovers value; direct serialized-string assertion optional |
| SongIO | `writeSong` — all lines serialized in order via `LineIO.writeLine` | unit | `SongIOTest.testParsedLinesHaveSongSet` (parse only) | inadequate | multi-line round-trip asserting per-line element counts |
| SongIO | `writeSong` — `<view>` block always written | unit | — (`writeView` never called by any test, per 2C) | missing | add direct write test: serialized output contains `<view>…</view>` |
| SongIO | `DocumentReader.startElement` — v1.0 dispatch creates `StaffElementReader`+`TempoReader`, not `LineReader` | unit | — | missing | parse v1.0 `<song>`, assert notes load |
| SongIO | `DocumentReader.startElement` — v1.1 dispatch creates `LineReader`+`ViewReader` | unit | `StaffElementIOTest` (v1.1 fixtures) | adequate | keep |
| SongIO | `DocumentReader.startElement` — 2.x up to `IO_MINOR_VERSION` accepted; `2.(IO_MINOR+1)` throws `NewerVersionException` | unit | `SongIOTest.testOpeningNewerVersionFileThrowsNewerVersionException` | adequate | keep; add boundary test `2.IO_MINOR` accepted |
| SongIO | `DocumentReader.startElement` — non-numeric version → `SAXException` wrapping `NumberFormatException` | unit | — | missing | version="abc" → SAXException |
| SongIO | `endElement10` — `<notes>`/`<tempo_changes>` restore `where=SONG` | unit | — | missing | part of v1.0 load test |
| SongIO | `endElement10` — tempo at pos 0 → song-level; pos N → attached to element across multi-line flat layout | unit | — | missing | two-line v1.0 doc with multiple tempo-change positions |
| SongIO | `endElement10` — empty `parsedLines` → first `Line` created on first note | unit | — | missing | part of v1.0 load test |
| SongIO | `endElement11` — grace notes set `upper=true` (v1.1-only post-processing) | unit | — | missing | parse v1.1 grace note, assert `isUpper()==true` |
| SongIO | `endElement12` — `<lines>`/`<view>` restore `where=SONG` | unit | `SongIOTest.*` (modern versions) | adequate | keep |
| SongIO | `endElement12` — field mapping (keys, keytype, number, title empty→"Untitled", place, year, month, day, underLyrics, banglaLyrics, translatedLyrics, attribution, footnotes, unofficialTranslation) | unit | — | missing | round-trip all fields non-default; title-empty→"Untitled" branch separately |
| SongIO | `endElement12` — `parseVersionedDouble` (<2.1 `Integer.parseInt`; ≥2.1 `Double.parseDouble`) for topspace/rightinfostarty/rowheight/linewidth | unit | `SongIOTest.LegacyMigrationWiring.testPre21Converts…` + `testBuggyLineWidthIsCorrectedOnLoad` | adequate | keep |
| SongIO | `getSong` — `parsingSong==null` → `IllegalStateException` | unit | — | missing | empty XML (no root) → ISE |
| SongIO | `getSong` — migration pipeline runs pre- and post-assembly | unit | `SongIOTest.LegacyMigrationWiring` (5) | adequate | keep |
| SongIO | `getDocumentFonts` — returns `ViewReader` fonts when present, else `DocumentFonts.defaultsFromPrefs()` (v1.0) | unit | `ViewIOTest` (v1.1+); v1.0 fallback untested | inadequate | parse v1.0 doc (no `<view>`), assert defaults returned |
| SongIO | `NewerVersionException` message text | none | — | none | trivial static message |

#### SongLoader

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| SongLoader | `load` — missing file → `IoError` w/ `IOException` cause | unit | `SongLoaderTest.testLoadNonExistentFileReturnsIoError` | adequate | keep |
| SongLoader | `load` — corrupt XML → `ParseError` w/ `SAXException` cause | unit | `SongLoaderTest.testLoadDamagedFileReturnsParseError` | adequate | keep |
| SongLoader | `load` — newer version → `NewerVersion` w/ cause | unit | `SongLoaderTest.testLoadNewerVersionFileReturnsNewerVersion` | adequate | keep |
| SongLoader | `load` — valid file → `Success` w/ non-null song+fonts | unit | `SongLoaderTest.testLoadValidFileReturnsSuccess` | adequate | keep (isNotNull substantive: null ⇒ broken read path) |
| SongLoader | `load` — `ParserConfigurationException` → `ParseError` | unit | — | missing | branch exists; may not be unit-testable without env manipulation — note if so |
| SongLoader | `load` — `Success.song()` fully assembled (fields preserved) | unit | `SongLoaderTest.testLoadValidFileReturnsSuccess` (isNotNull only) | inadequate | add field-level assertions (≥ line count, line-0 element count) vs fixture |
| SongLoader | `load` — `Success.fonts()` non-null w/ expected roles from `<view>` | unit | same (isNotNull only) | inadequate | assert a known `FontKey` resolves from the fixture's `<view>` block |

#### SongLoadResult

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| SongLoadResult | `Success`/`IoError`/`ParseError`/`NewerVersion`/`LineWidthTooLarge` records — carry their components | none | — | none | pure data records |
| SongLoadResult | `songOrThrow()` — `Success` branch returns the song | unit | `UnitTest.loadFixture` (implicit, no assert) | inadequate | direct: `new Success(song,fonts).songOrThrow()` returns same instance |
| SongLoadResult | `songOrThrow()` — `IoError` branch throws wrapped `IOException` | unit | — | missing | write test |
| SongLoadResult | `songOrThrow()` — `ParseError` branch throws wrapped `SAXException` | unit | — | missing | write test |
| SongLoadResult | `songOrThrow()` — `NewerVersion` branch throws `NewerVersionException` | unit | — | missing | write test |
| SongLoadResult | `songOrThrow()` — `LineWidthTooLarge` branch throws `IOException` w/ both inch values in message | unit | — | missing | assert message includes `actualInches` and `maxInches` |
| SongLoadResult | `Failure` sealed `file()` accessor on all variants | none | — | none | compiler-enforced |

#### XML

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| XML | `escapeXML` — `&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`, `"`→`&quot;` (four specials) | unit | — | missing | one test per special + a combined-all-four test |
| XML | `escapeXML` — no-specials passthrough; empty → empty | unit | — | missing | write both |
| XML | `writeValue(pw,tag,value)` — `<tag>escaped</tag>` on its own line | unit | — | missing | `StringWriter` test (indent + tag + escape + close) |
| XML | `writeValue` — indent prefix applied | unit | — | missing | `setIndent(2)` → line starts with two spaces |
| XML | `writeEmptyTag` → `<tag />`; `writeBeginTag` → `<tag>`; `writeEndTag` → `</tag>` (each indented, own line) | unit | — | missing | write test each |
| XML | `setIndent`/`printIndent` — shared static state | unit | — | missing | `setIndent(4)` → 4-space prefix; flag static-field non-thread-safe (production concern) |
| XML | `writeValue` round-trip — content read back unchanged by SAX | unit | `SongIOTest.*` (indirect, escape-safe fixtures only) | inadequate | direct XML test faster/pinpoints; current fixtures never contain `& < > "` so escaping is unverified |

**2A notes (quality concerns):** The three highest-risk gaps: (1) **`XML.escapeXML` has zero direct tests** — every saved string flows through it, and the `SongIOTest` round-trips use only escape-safe fixtures, so a regression mishandling `"`/`&`/`<`/`>` in a title or attribution would silently corrupt every saved file. (2) **`writeSong` conditional-emission logic is entirely untested** — presence/absence of tags depends on runtime conditions (null tempo, empty strings, month/day > 0, `userSetTopPadding`), none directly asserted; round-trips only verify value preservation, not correct omission. (3) **The v1.0 load path** (`startElement10`/`endElement10`, flat-notes + `<tempo_changes>` + positional mapping) has no test or fixture whatsoever. `SongLoaderTest` classifies errors well but `testLoadValidFileReturnsSuccess` only asserts non-null song/fonts (doesn't verify content). `SongLoadResult.songOrThrow()` — the primary load API for converters — has no direct assertion on any of its five branches, the `LineWidthTooLarge` compound-message branch being the riskiest.

### 2B. element & annotation serialization — `StaffElementIO`, `AnnotationIO`, `TempoIO`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StaffElementIO | `writeElement` — `type` attribute = `ElementType` name | unit | `StaffElementIOTest.DynamicSerialization.testWritesDynamicElement` (round-trip) | adequate | keep |
| StaffElementIO | `writeElement` — omit `<xpos>` when `xOffsetPx==0`; emit when non-zero | unit | — | missing | assert absent/present around zero |
| StaffElementIO | `writeElement` — always emit `<staffposition>` | unit | — | missing | value preserved |
| StaffElementIO | `writeElement` — omit `<dotted>` when `dotCount==0`; emit for 1/2 | unit | — | missing | test dotCount 0/1/2 |
| StaffElementIO | `writeElement` — omit `<prefix>` when accidental null; emit `name()` otherwise | unit | — | missing | null→absent; SHARP→`<prefix>SHARP</prefix>` |
| StaffElementIO | `writeElement` — emit `<prefixinparenthesis>` only when `isAccidentalInParentheses()` | unit | — | missing | false→absent; true→present |
| StaffElementIO | `writeElement` — ACCENT→`<forcearticulation>`, STACCATO→`<durationarticulation>` | unit | — | missing | one tag per articulation type |
| StaffElementIO | `writeElement` — glissando type serialized; x1/x2 translates omit when 0, emit non-zero | unit | `GraceNoteLyricRoundTripTest` (type only) | inadequate | add translate omit/emit test (zero-translate regression undetectable today) |
| StaffElementIO | `writeElement` — `<stemDirectionAuto>` emitted when `!isStemDirectionAuto()` (inverted) | unit | — | missing | auto=true→absent; auto=false→present |
| StaffElementIO | `writeElement` — `<upper>` emitted when `isUpper()` | unit | — | missing | false→absent; true→present |
| StaffElementIO | `writeElement` — delegates to `TempoIO.writeTempo` when `TempoChangeAttachment` present | unit | — | missing | assert `<tempo>` block in element XML |
| StaffElementIO | `writeElement` — delegates to `AnnotationIO.writeAnnotation` when `AnnotationAttachment` present | unit | — | missing | assert `<annotation>` block |
| StaffElementIO | `writeElement` — `<fermata/>` when `FermataAttachment` present | unit | — | missing | absent without, present with |
| StaffElementIO | `writeElement` — `<dynamic type="…"/>` for `DynamicAttachment` | unit | `StaffElementIOTest.DynamicSerialization.testWritesDynamicElement` | adequate | keep |
| StaffElementIO | `writeElement` — `<beatchange duration beat/>` (new 2-attribute format) | unit | — | missing | assert both attributes correct |
| StaffElementIO | `writeElement` — STOP/CONTINUE carrier emits only `<extend type/>`, no syllabic/text | unit | `SongIOTest.testRoundTripMelismaWithStopCarrier` | adequate | keep |
| StaffElementIO | `writeElement` — syllabic→single/begin/middle/end; null→"single" | unit | `SongIOTest.testRoundTripPerNoteLyrics` (begin/single/end) | inadequate | `middle` and null-syllabic-non-carrier uncovered; add tests |
| StaffElementIO | `writeElement` — compound=true appends `COMPOUND_WORD_MARKER` to `<text>` | unit | `SongIOTest.testRoundTripPerNoteLyrics` | adequate | keep |
| StaffElementIO | `writeElement` — Extend.START emits `<extend type="start"/>` inside lyric | unit | `SongIOTest.testRoundTripPerNoteLyrics`, `testRoundTripMelismaWithStopCarrier` | adequate | keep |
| StaffElementIO | `writeElement` — multi-verse lyric (verse 2) round-trips w/ `number` attribute | unit | — | missing | two Lyric entries at verse 1 and 2 |
| StaffElementIO | `extendTypeAttr(NONE)` throws `IllegalArgumentException` | unit | — | missing | assert IAE (unguarded caller crash today) |
| StaffElementIO | `parseExtendType(null)` → START (legacy bare `<extend/>`) | unit | `SongIOTest.testLegacyExtendTagWithoutTypeLoadsAsStart` | adequate | keep |
| StaffElementIO | `parseExtendType("stop")` → STOP | unit | `SongIOTest.testMusicXmlStopExtendLoadsAsStopCarrier` | adequate | keep |
| StaffElementIO | `parseExtendType("continue")` → CONTINUE | unit | — | missing | `<extend type="continue"/>` mid-melisma → CONTINUE carrier |
| StaffElementIO | `parseExtendType(unknown)` → START (default) | unit | — | missing | `<extend type="bogus"/>` → START |
| StaffElementIO | `ACCIDENTAL_MAP` includes `DOUBLE_SHARP` and `DOUBLESHARP` (no-underscore alias) | unit | — | missing | round-trip each Accidental incl. compound legacy names |
| StaffElementIO | `ACCIDENTAL_MAP` unknown name → IAE wrapped in `SAXException` | unit | `StaffElementIOTest.InvalidMapLookups.testUnknownAccidentalThrowsMeaningfulError` | adequate | keep |
| StaffElementIO | `startElement10` — `NEWLINE`→`where=null` (ignored); `LINE`→SINGLE_BARLINE; `GRACESEMIQUAVER*`→GRACE_QUAVER | unit | — | missing | v1.0 type-alias tests |
| StaffElementIO | `startElement11` — `VERTICALLINE`→SINGLE_BARLINE; `GRACE_SEMIQUAVER*`→GRACE_QUAVER | unit | — | missing | v1.1 type-alias tests |
| StaffElementIO | `endElement11` — legacy `<ypos>` and `<staffposition>` both → `setStaffPosition` | unit | — | missing | v1.0 `<ypos>` yields correct staffPosition |
| StaffElementIO | `endElement11` — legacy `<volume>LOUDER</volume>` → ACCENT articulation | unit | — | missing | write test |
| StaffElementIO | `endElement11` — `<glissando>` numeric content (legacy) → CONNECTED | unit | — | missing | `<glissando>5</glissando>` → CONNECTED |
| StaffElementIO | `endElement11` — `<glissandox1translate>`/`x2translate` set when glissando present, ignored when null | unit | — | missing | non-zero survives; translate w/o glissando doesn't crash |
| StaffElementIO | `endElement11` — `<trill>` sets `trillFlagged=true` | unit | — | missing | `isTrillFlagged()` true |
| StaffElementIO | `endElement11` — `<fermata>` → `FermataAttachment` | unit | — | missing | round-trip test |
| StaffElementIO | `endElement11` — `<stemDirectionAuto>` → `setStemDirectionAuto(false)` (inverted) | unit | — | missing | tag → `isStemDirectionAuto()==false` |
| StaffElementIO | `endElement11` — `<invertfractionbeamorientation>` silently ignored | unit | — | missing | no-throw + no-side-effect |
| StaffElementIO | `endElement11` — legacy `<beatchange>` text-content → `fromLegacyName` | unit | `StaffElementIOTest.InvalidMapLookups.testUnknownBeatChangeThrowsMeaningfulError` (error only) | inadequate | happy paths untested; test each valid legacy name |
| StaffElementIO | `startElement11` — `<beatchange>` new 2-attribute format → `BeatChangeAttachment` directly | unit | — | missing | v2.5+ duration/beat attributes |
| StaffElementIO | `startElement11` — `<dynamic>` unknown type → warn + skip, no attachment | unit | `StaffElementIOTest.DynamicSerialization.testUnknownDynamicType*` (2) | adequate | keep |
| StaffElementIO | `startElement11` — `<dynamic>` valid types → correct `DynamicType` | unit | `StaffElementIOTest.DynamicSerialization.testRoundTripPreservesDynamicType` (6 of 8) | inadequate | `SFORZANDO`/`SFORZATO` excluded — confirm valid and add |
| StaffElementIO | `getSyllabic()` — carrier→null; SINGLE/BEGIN/MIDDLE/END mappings | unit | `SongIOTest.PerNoteLyricSerialization` (partial) | inadequate | `middle` and absent-syllabic-defaulting-to-SINGLE uncovered |
| StaffElementIO | `where==null` (NEWLINE) null-guard paths in endElement/characters | unit | — | missing | NEWLINE element absent; subsequent elements still parse |
| StaffElementIO | grace-note lyric round-trip + direct-load persistence | unit | `GraceNoteLyricRoundTripTest` (2) | adequate | keep |
| StaffElementIO | dynamic round-trip (6 types); no attachment when `<dynamic>` absent | unit | `StaffElementIOTest.DynamicSerialization` (2) | adequate | keep |
| AnnotationIO | `writeAnnotation` — emits `<name>`/`<alignment>`/`<ypos>` | unit | `SongIOTest.testPre23ConvertsAnnotationToDynamic` (parses then migrates away) | inadequate | no text/alignment/yPosPx round-trip; write direct test |
| AnnotationIO | `writeAnnotation` — omit `<useryoffset>` when 0; emit when non-zero | unit | — | missing | both branches |
| AnnotationIO | `AnnotationReader.endElement11` — `<name>`/`<alignment>`/`<ypos>`/`<useryoffset>` → setters | unit | — | missing | parametrized round-trip per field |
| AnnotationIO | `AnnotationReader` — null-guard (endElement before startElement) | unit | — | missing | no NPE |
| AnnotationIO | `AnnotationReader.startElement11`/`characters` — fresh `Annotation("")`; accumulate only when `lastTag!=null` | unit | — | missing | covered by round-trip test |
| TempoIO | `writeTempo` — emits `<visibletempo>`/`<tempotype>`/`<tempodescription>` | unit | — | missing | direct parse-back of all three |
| TempoIO | `writeTempo` — omit `<dontshowtempo>` when shown; emit when not | unit | — | missing | both branches |
| TempoIO | `writeTempo`+`endElement11` round-trip — all fields preserved | unit | — | missing | per-note `<tempo>` round-trip via `StaffElementReader` |
| TempoIO | `endElement10` (v1.0) — `<tempochange>` wrapper → `Tempo`; `<position>` via `getPos10()` | unit | — | missing | v1.0 parse asserting pos10 + fields |
| TempoIO | `endElement10` — legacy no-underscore duration names (MINIMDOTTED/CROTCHETDOTTED/QUAVERDOTTED/SEMIBREVE) → `Duration` | unit | — | missing | parametrized for all 4 |
| TempoIO | `endElement10` — canonical duration name → `Duration.valueOf` path | unit | — | missing | write test |
| TempoIO | `endElement10` — `<dontshowtempo>` → `setShowTempo(false)` | unit | — | missing | write test |
| TempoIO | `endElement11` (v1.1) — `<tempo>` wrapper → `Tempo`; canonical names only | unit | — | missing | covered by round-trip test |
| TempoIO | `endElement11` — legacy name → `Duration.valueOf` fails (no legacy map in v1.1) | unit | — | missing | legacy name in v1.1 path throws IAE |
| TempoIO | `endElement11` — null-guard (endElement before startElement) → null | unit | — | missing | write test |
| TempoIO | `characters` — accumulate only when `lastTag!=null` | none | — | none | trivial delegation |
| TempoIO | `getPos10()` returns v1.0 parse position | unit | — | missing | covered by v1.0 parse test |

**2B notes (quality concerns):** **`AnnotationIO` and `TempoIO` have zero dedicated IO round-trip tests** — the largest gap here. The only `AnnotationIO` touch (`SongIOTest.testPre23ConvertsAnnotationToDynamic`) parses an annotation then migrates it away, never asserting persistence; `TempoIO` is exercised only via song-level fixture headers, never a per-note `TempoChangeAttachment` round-trip. The **v1.0 legacy decode paths** (`startElement10`/`endElement10`, NEWLINE/LINE/GRACESEMIQUAVER renames, MINIMDOTTED/CROTCHETDOTTED durations) are completely untested. The **inverted `stemDirectionAuto` write/read asymmetry** (tag present ⇒ `false`) has no coverage in either direction — high regression risk. The `extendTypeAttr(NONE)` IAE guard, the legacy `<beatchange>` happy paths, the glissando translate omit/emit, and the `getSyllabic()` `middle`/default branches are all unguarded. `testRoundTripPreservesDynamicType` excludes `SFORZANDO`/`SFORZATO` (enum has 8, comment says "6 UI types") — confirm intent.

### 2C. line & view serialization — `LineIO`, `ViewIO`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LineIO | `writeLine` — key-signature delta (count+type) only when line differs from song default | unit | — | missing | differing key → `<keys>`/`<keytype>` present; same → absent |
| LineIO | `writeLine` — omit `<notedistchange>` when ratio==1.0; write otherwise | unit | — | missing | ratio=1.5 present; 1.0 absent |
| LineIO | `writeLine` — always write `<lyricsypos>` | unit | — | missing | tag present |
| LineIO | `writeLine` — omit legacy Y-pos tags (tempoChangeypos/beatChangeypos/fsendingypos/trillypos) in new docs | unit | — | missing | none of the four appear |
| LineIO | `beamsToString` — `anchor,end;` pairs | unit | — | missing | known beam list → exact string |
| LineIO | `tiesToString` — `anchor,end;` pairs | unit | `TieToggleTest.testTiePersistsThroughSaveLoad` (round-trip) | adequate | round-trip preserves anchor/end; exact-format test optional |
| LineIO | `trillsToString` — `anchor,end;` w/o yPos when 0; incl. when non-zero | unit | — | missing | both branches |
| LineIO | `tupletsToString` — `anchor,end,grade;` w/o vertPos when 0; incl. when non-zero | unit | — | missing | both branches |
| LineIO | `hairpinsToString` — `anchor,end;` w/o shifts when all zero; incl. `x1,x2,y` when any non-zero | unit | — | missing | both branches |
| LineIO | `endingsToString` — `anchor,end;` per ending; **does not serialize `Ending.Type`** | unit | — | missing | exact-string test; see production observation (type-loss bug) |
| LineIO | `forEachSegment` — semicolon-delimited: empty→0 iters; single; multiple | unit | — | missing | all three cases (shared parser foundation) |
| LineIO | `LineReader` state machine — `<line>`→new Line + WHERE.LINE; `<notes>`→WHERE.NOTES; else set `lastTag` | unit | — | missing | exercise transitions via start/end events |
| LineIO | `endElement11` — `<keys>` → `setKeyAccidentalCount` | unit | `SongIOTest.LegacyMigrationWiring` (incidental, not asserted at line level) | inadequate | `<keys>5</keys>` → `getKeyAccidentalCount()==5` |
| LineIO | `endElement11` — `<keytype>` → `setKeyType` | unit | — | missing | `<keytype>FLATS</keytype>` → `KeyType.FLATS` |
| LineIO | `endElement11` — `<notedistchange>` → `changeElementSpacingRatio` | unit | — | missing | known float → `getElementSpacingRatio()` |
| LineIO | `endElement11` — `<lyricsypos>` → `setLyricsYPosSs` | unit | `GraceNoteLyricRoundTripTest` (parsed, never asserted) | inadequate | assert `getLyricsYPosSs()==5.0` |
| LineIO | `endElement11` — legacy Y-pos tags → correct setters (backward compat) | unit | — | missing | one test per legacy tag |
| LineIO | `endElement11` — silently ignores `<slurs>` | unit | — | missing | no exception, line unaffected |
| LineIO | beam round-trip (`parseBeamPairs`+`createBeamsFromPending`) | unit | `BeamToggleTest` (does not round-trip beams) | missing | round-trip → `findBeamAt(0)` correct anchor/end |
| LineIO | `createBeamsFromPending` skips out-of-range pairs (anchor<0, end≥count, anchor>end) | unit | — | missing | malformed pairs → zero beams, no exception |
| LineIO | tie round-trip (`parseTiePairs`+`createTiesFromPendingPairs`) | unit | `TieToggleTest.testTiePersistsThroughSaveLoad` | adequate | keep |
| LineIO | `createTiesFromPendingPairs` — no bounds guard (unlike beams) → AIOOBE on out-of-range | unit | — | missing | malformed tie pair → verify throws/skips; see production observation |
| LineIO | tuplet — grade defaults to 3 when absent (legacy `<triplets>`) | unit | — | missing | `<triplets>0,2;</triplets>` → grade=3 |
| LineIO | tuplet — explicit non-3 grade round-trip | unit | — | missing | `<tuplets>0,4,5;</tuplets>` → grade=5 |
| LineIO | tuplet — `verticalPositionSs` round-trip (non-zero) | unit | — | missing | `<tuplets>0,2,3,7;</tuplets>` → vertPos=7 |
| LineIO | `createTupletsFromPending` skips out-of-range pairs | unit | — | missing | analogous to beam bounds test |
| LineIO | `parseTupletData` swallows NFE for grade (→3) and vertPos (→0) | unit | — | missing | non-numeric grade/vertPos → defaults |
| LineIO | crescendo round-trip — all-zero shifts | unit | — | missing | `<crescendo>0,2;</crescendo>` → shifts 0 |
| LineIO | crescendo round-trip — explicit x1/x2/y shifts | unit | — | missing | `<crescendo>0,2,1.5,-0.5,0.25;</crescendo>` preserved |
| LineIO | diminuendo round-trip (same as crescendo) | unit | — | missing | analogous |
| LineIO | `parseHairpinPairs` swallows partial shift data (<3 parts → all 0) | unit | — | missing | `<crescendo>0,2,1.5;</crescendo>` → shifts 0 |
| LineIO | ending round-trip — always rebuilds as `Ending.Type.FIRST` regardless of actual type | unit | — | missing | write test; exposes type-loss bug (production observation) |
| LineIO | `parseEndingPairs` clears `pendingEndingPairs` before accumulating (unlike others) | unit | — | missing | call twice → only second batch survives |
| LineIO | trill round-trip — yPositionSs 0 and non-zero | unit | — | missing | `<trills>0,2;</trills>` and `<trills>0,2,5;</trills>` |
| LineIO | `accumulateLegacyTrillFlag` — coalesces contiguous indices into one run; new run for non-contiguous | unit | — | missing | 2,3,4 → `[2,4,0]`; 2,4 → two pairs |
| LineIO | `endElement11` returns completed `Line` on `</line>` (all create-methods invoked first) | unit | — | missing | full start/chars/end sequence → correct counts + range elements |
| LineIO | `LineReader` `line==null`/`noteReader==null` guard → `endElement11` returns null | unit | — | missing | endElement before any startElement → null |
| ViewIO | `writeView` — serializes all 6 font roles (name+size) | unit | — | missing | capture output, verify 6 name+size pairs |
| ViewIO | `writeView` — uses PS name (not display name) | unit | — | missing | PSName ≠ family name → PSName in output |
| ViewIO | `ViewReader` default ctor — all 6 roles from `Prefs` defaults | unit | `ViewIOTest.DocumentFontsLoad.testV10FallbackUsesDefaultsForAllRoles` | adequate | keep |
| ViewIO | `endElement11`+`getDocumentFonts()` — known tag updates name/size of role | unit | `ViewIOTest.DocumentFontsLoad.testPartialBlockOverridesOnlyPresentRoles` | adequate | keep |
| ViewIO | `ViewReader` ignores unknown tags (legacy `titlefontstyle`) silently | unit | `ViewIOTest.LegacyFontStyleElements.testDocumentWithFontStyleElementsLoadsWithoutError` | inadequate | `isNotNull()` trivially true; assert ignored tag didn't corrupt any font value |
| ViewIO | `getDocumentFonts()` — defaults for roles absent from partial block | unit | `ViewIOTest.DocumentFontsLoad.testPartialBlockOverridesOnlyPresentRoles` | adequate | keep |
| ViewIO | `getDocumentFonts()` zero roles == `defaultsFromPrefs()` | unit | `ViewIOTest.DocumentFontsLoad.testV10FallbackUsesDefaultsForAllRoles` | adequate | keep |
| ViewIO | `DocumentFonts.defaultsFromPrefs()` idempotent | unit | `ViewIOTest.DocumentFontsLoad.testNewDocumentInstallsPrefsDefaults` | inadequate | tautology (`x.equals(x)`); rewrite to compare two independent calls |
| ViewIO | `endElement11` legacy self-closing `<view/>` → all roles at defaults | unit | `ViewIOTest.FontXmlParsing.testLegacyDocumentWithoutFontXmlUsesPrefsDefaults` | adequate | keep |
| ViewIO | `writeView`+`ViewReader` full round-trip — all 6 roles preserved | unit | — | missing | primary correctness guarantee for the write path |
| ViewIO | `ViewReader.StringFont.sizeAsInt()` on non-integer size string | unit | — | missing | size="abc" → verify NFE propagates or is swallowed (document) |

**2C notes (quality concerns):** **`LineIO` — the largest IO class (~741 lines, 18-field serializer + ~450-line reader) — has no dedicated test file.** No test in `src/test/` references `LineIO`, its tag constants, or any internal parse method. The only coverage is incidental (`TieToggleTest` ties through `SongIO`; `BeamToggleTest` does not round-trip beams; `GraceNoteLyricRoundTripTest` parses `lyricsypos` without asserting it). Six of seven range-element serializers (beams, tuplets, endings, crescendos, diminuendos, trills) have zero coverage at any level, and the shared `forEachSegment` parser is untested. `ViewIO` is better served by a genuine `ViewIOTest`, but `writeView` is never called by any test (write path entirely untested), one test is an outright tautology, and the legacy-tolerance test asserts only `isNotNull()`.

### 2D. migration subsystem & legacy import — `FormatMigrator`, `MigrationPipeline`, `MigrationContext`, `SongMigration`, `StageId`, `LegacyLyricsImporter`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| FormatMigrator | `migrate` skips when `formatVersion >= 2` | unit | `MigrationPipelineTest.LegacyFormatStage.testDoesNotApplyAtThreshold` (gate via pipeline) | inadequate | the in-`migrate` version guard never asserted directly; call w/ version=2, confirm untouched |
| FormatMigrator | `migrate(lines,1)` iterates calling `migrateLineLevelOffsets` per line | unit | `MigrationPipelineTest.LegacyFormatStage.testEffectRunsOnEmptyLines` | inadequate | smoke (empty list); test a line w/ non-zero `tempoChangeYPosPx`, verify `userYOffsetSs` updated |
| FormatMigrator | `migrateLineLevelOffsets` — non-zero `tempoChangeYPosPx` → delta to each `TempoChangeAttachment.userYOffsetSs` | unit | — | missing | line+attachment offset → verify delta |
| FormatMigrator | `migrateLineLevelOffsets` — `beatChangeYPosPx`≠default → delta to `BeatChangeAttachment.userYOffsetSs` | unit | — | missing | non-default + zero-delta no-op |
| FormatMigrator | `migrateLineLevelOffsets` — `firstSecondEndingYPosPx`≠default → delta to `Ending.yPositionSs` | unit | — | missing | write test |
| FormatMigrator | `migrateLineLevelOffsets` — `trillYPosPx`≠default → delta to `Trill.yPositionSs` | unit | — | missing | write test |
| FormatMigrator | `migrateAnnotationPositions` — below-staff (`yPosPx>0`) → above-staff + userYOffset | unit | — | missing | positive yPosPx → yPosPx=ABOVE, userYOffset += (old−ABOVE) |
| FormatMigrator | `migrateAnnotationPositions` — above-staff (`yPosPx<=0`) → unchanged | unit | — | missing | no-op |
| FormatMigrator | `migrateElementAttachments` — empty body | none | — | none | no behavior |
| FormatMigrator | `migrateAnnotationDynamics` — text matches dynamic symbol → replaced w/ `DynamicAttachment`, annotation removed | unit | `FormatMigratorTest.MigrateAnnotationDynamics` (forte/pianissimo/removal) | adequate | keep |
| FormatMigrator | `migrateAnnotationDynamics` — non-matching text → kept, no attachment | unit | `FormatMigratorTest.testAnnotation*NotConverted` (2) | adequate | keep |
| FormatMigrator | `migrateAnnotationDynamics` — pre-existing `DynamicAttachment` → annotation removed, no duplicate | unit | `FormatMigratorTest.testAnnotationRemovedWhenDynamicAlreadyExists` | adequate | keep |
| FormatMigrator | `migratePixelsToStaffSpace` — `lyricsYPosSs` /= pps per line | unit | `MigrationPipelineTest.PixelsToSsStage.testEffectDividesAllScalarsByPps` (scalars only) | missing | line w/ `lyricsYPosSs` → assert division |
| FormatMigrator | `migratePixelsToStaffSpace` — `Tuplet.verticalPositionSs` /= pps (non-zero only) | unit | — | missing | non-zero /= pps; zero no-op |
| FormatMigrator | `migratePixelsToStaffSpace` — Hairpin `x1/x2/yShiftSs` /= pps | unit | — | missing | non-zero shifts |
| FormatMigrator | `migratePixelsToStaffSpace` — Glissando `x1/x2Translate` /= pps | unit | — | missing | write test |
| FormatMigrator | `migratePixelsToStaffSpace` — attachment `userYOffsetSs` /= pps (non-zero) | unit | — | missing | note w/ non-zero offset |
| FormatMigrator | `migratePixelsToStaffSpace` — `note.xOffsetPx` reset to 0 unconditionally | unit | — | missing | non-zero → reset to 0 |
| FormatMigrator | `migratePixelsToStaffSpace` — `Ending.yPositionSs`/`Trill.yPositionSs` /= pps (non-zero) | unit | — | missing | write test each |
| FormatMigrator | `migrateFinalTerminal` — empty list → no-op | unit | `MigrationPipelineTest.FinalTerminalStage` (empty ctx) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — FINAL_DOUBLE_BARLINE on non-last lines stripped; last line's terminal preserved | unit | `FormatMigratorTest.MigrateFinalTerminal.testFinalBarlineOnNonLastLine*` (2) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — REPEAT_RIGHT on non-last line untouched | unit | `FormatMigratorTest.testRepeatRightOnNonLastLineIsPreserved` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — last line ends in replaceable (SINGLE/DOUBLE/REPEAT_LEFT_RIGHT) → replaced w/ FINAL_DOUBLE_BARLINE | unit | `FormatMigratorTest.test*AtEndIsReplaced` (3) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — last line ends in REPEAT_RIGHT (valid terminal) → no-op | unit | `FormatMigratorTest.testRepeatRightAtEndIsPreservedAsTerminal` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — last line already FINAL_DOUBLE_BARLINE → no-op | unit | `FormatMigratorTest.testAlreadyEndsInFinalBarlineIsNoOp` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — non-replaceable non-terminal (REPEAT_LEFT, note) → FINAL_DOUBLE_BARLINE appended | unit | `FormatMigratorTest.testRepeatLeftAtEnd…`/`testNoteAtEnd…` (2) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — empty last line → FINAL_DOUBLE_BARLINE appended | unit | `FormatMigratorTest.testEmptyLastLineGetsFinalBarlineAppended` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — misplaced FINAL_DOUBLE_BARLINE not at terminal pos stripped before decision | unit | `FormatMigratorTest.testMisplacedFinalBarline*` (2) | adequate | keep |
| MigrationPipeline | `PRE_ASSEMBLY` registration order + stage count | unit | `MigrationPipelineTest.testStageOrderingPreservesScalarInvariant` (ordering only) | inadequate | assert all 6 stages registered in StageId order |
| MigrationPipeline | `POST_ASSEMBLY` registration (LEGACY_LYRICS then SYLLABIC_BACKFILL) | unit | — | missing | assert list == `[LEGACY_LYRICS, SYLLABIC_BACKFILL]` |
| MigrationPipeline | `versioned` helper — `ctx.isBefore(major,minor)` as `appliesTo` | unit | `MigrationPipelineTest` (implicit) | adequate | keep |
| MigrationPipeline | LEGACY_FORMAT gate — applies <2.0, skips ≥2.0 | unit | `MigrationPipelineTest.LegacyFormatStage` (2) | adequate | keep |
| MigrationPipeline | LEGACY_FORMAT effect — delegates to `FormatMigrator.migrate` w/ non-empty lines | unit | `MigrationPipelineTest.LegacyFormatStage.testEffectRunsOnEmptyLines` | inadequate | smoke only; non-empty behavior covered by missing `migrateLineLevelOffsets` tests |
| MigrationPipeline | ANNOTATION_DYNAMICS gate — applies <2.3, skips ≥2.3 | unit | `MigrationPipelineTest.AnnotationDynamicsStage` (2) | adequate | keep |
| MigrationPipeline | ANNOTATION_DYNAMICS effect — delegates to `migrateAnnotationDynamics` | unit | `MigrationPipelineTest.AnnotationDynamicsStage.testEffectRunsOnEmptyLines` | inadequate | smoke only; add wiring test through a line w/ annotation |
| MigrationPipeline | FINAL_TERMINAL gate — applies <2.4, skips ≥2.4 | unit | `MigrationPipelineTest.FinalTerminalStage` (2) | adequate | keep |
| MigrationPipeline | FINAL_TERMINAL effect — FINAL_DOUBLE_BARLINE appended when last line ends in note | unit | `MigrationPipelineTest.FinalTerminalStage.testEffectAppliesFinalBarline` | adequate | keep |
| MigrationPipeline | PIXELS_TO_SS gate — applies <2.1, skips ≥2.1 | unit | `MigrationPipelineTest.PixelsToSsStage` (2) | adequate | keep |
| MigrationPipeline | PIXELS_TO_SS effect — four song-level scalars /= pps | unit | `MigrationPipelineTest.PixelsToSsStage.testEffectDividesAllScalarsByPps` | adequate | keep |
| MigrationPipeline | PIXELS_TO_SS effect — per-line fields also /= pps | unit | — | missing | covered by missing `migratePixelsToStaffSpace` line-level tests; add integration via `runPreAssembly` w/ non-empty line |
| MigrationPipeline | LINE_WIDTH_FIX gate — major=2 AND minor<3 AND `lineWidthSs>=MIN`; else skip (3 negative branches) | unit | `MigrationPipelineTest.LineWidthFixStage` (5) | adequate | keep |
| MigrationPipeline | LINE_WIDTH_FIX effect — `lineWidthSs /= pps` | unit | `MigrationPipelineTest.LineWidthFixStage.testEffectDividesLineWidthByPps` | adequate | keep |
| MigrationPipeline | TOP_PADDING_FALLBACK gate — applies when `topPaddingSs==0` | unit | `MigrationPipelineTest.TopPaddingFallbackStage` (2) | adequate | keep |
| MigrationPipeline | TOP_PADDING_FALLBACK effect — `(2·titleSize + lineCount·attributionSize) − ssToRoundedPx(2.0)` | unit | `MigrationPipelineTest.TopPaddingFallbackStage.testEffectComputesCorrectFallbackValue` (attribution="") | inadequate | attribution="" ⇒ lineCount term never exercised; add non-empty attribution test |
| MigrationPipeline | LEGACY_LYRICS gate — `!lyrics.isBlank()` AND `isBefore(2, PER_NOTE_LYRIC_VERSION)` | unit | `MigrationPipelineTest.LegacyLyricsStage` (3) | adequate | keep |
| MigrationPipeline | LEGACY_LYRICS effect — delegates to `LegacyLyricsImporter.importLegacyLyrics` | unit | (no direct effect test; indirect via `SongIOTest.testLegacyLyricsBlobPopulatesPerNoteRecords`) | inadequate | add direct effect test asserting lyric records populated |
| MigrationPipeline | SYLLABIC_BACKFILL gate — always applies | unit | `MigrationPipelineTest.SyllabicBackfillStage.testAlwaysAppliesRegardlessOfVersion` | adequate | keep |
| MigrationPipeline | SYLLABIC_BACKFILL effect — `line.backfillSyllabic()` per line | unit | `MigrationPipelineTest.SyllabicBackfillStage.testEffectRunsOnSongWithNoLines` | inadequate | smoke mocks an empty line list ⇒ call never fires; test a line w/ stale markers → normalized |
| MigrationPipeline | `requireSong(ctx)` throws ISE when `ctx.song==null` | unit | — | missing | post-assembly stage `apply()` w/ null song → ISE |
| MigrationPipeline | `runPreAssembly` executes applicable stages in order | unit | `MigrationPipelineTest.testPreAssemblyScalarConversion`, `testStageOrderingPreservesScalarInvariant` | adequate | keep |
| MigrationPipeline | `runPostAssembly` executes applicable stages | unit | (indirect via `SongIOTest.LegacyMigrationWiring`) | adequate | keep |
| MigrationPipeline | stage ordering — PIXELS_TO_SS before LINE_WIDTH_FIX | unit | `MigrationPipelineTest.testStageOrderingPreservesScalarInvariant` | adequate | keep |
| MigrationPipeline | `PER_NOTE_LYRIC_VERSION`=6, `LEGACY_LINE_WIDTH_PX_MIN`=400.0 boundaries | unit | `MigrationPipelineTest` (used in gate setup) | adequate | keep |
| MigrationContext | `isBefore` — cross-major true; same-major minor< true; at-threshold false; major> false | unit | `MigrationPipelineTest` (multiple stage gate tests) | adequate | keep |
| MigrationContext | default field values (empty lines/scalars/strings, null song) | none | — | none | pure data holder |
| SongMigration | record accessors | none | — | none | pure record |
| StageId | enum constants | none | — | none | compile-time identifier |
| LegacyLyricsImporter | blank/empty blob → no lyrics | unit | `LegacyLyricsImporterTest.test*BlobEmitsNothing` (2) | adequate | keep |
| LegacyLyricsImporter | more blob lines than song lines → surplus dropped; fewer → surplus lines unset | unit | `LegacyLyricsImporterTest.testMultiLineDoesNotOverrunShorterLines` (+ implicit) | adequate | keep |
| LegacyLyricsImporter | more tokens than elements → surplus dropped | unit | `LegacyLyricsImporterTest.testTrailingWordsBeyondElementCountAreDropped` | adequate | keep |
| LegacyLyricsImporter | `deriveSyllabic` — 4 quadrants → SINGLE/BEGIN/END/MIDDLE | unit | `LegacyLyricsImporterTest.testDoReMi…`, `testEqualsProducesCompoundWord` | adequate | keep |
| LegacyLyricsImporter | single-hyphen `-` → BEGIN/MIDDLE/END chain | unit | `LegacyLyricsImporterTest.testDoReMiProducesThreeSyllables` | adequate | keep |
| LegacyLyricsImporter | double-hyphen `--` → compound | unit | `LegacyLyricsImporterTest.testDoubleHyphen*` (2) | adequate | keep |
| LegacyLyricsImporter | equals `=` → compound | unit | `LegacyLyricsImporterTest.testEqualsProducesCompoundWord` | adequate | keep |
| LegacyLyricsImporter | leading `--` on a line → inWord init, first word MIDDLE/END | unit | `LegacyLyricsImporterTest.testMidWordLineContinuationPrefix` | adequate | keep |
| LegacyLyricsImporter | trailing `_` run (extend=START) advances elementIdx by run length | unit | `LegacyLyricsImporterTest.testExtenderWith…`, `testFullCombinedExample` | adequate | keep |
| LegacyLyricsImporter | standalone `_` run → elementIdx += runLen, no Lyric | unit | `LegacyLyricsImporterTest.testExtenderWithSpaceSeparatedUnderscores…` | adequate | keep |
| LegacyLyricsImporter | `_` run abutting next word → one underscore absorbed (`runLen--`) | unit | `LegacyLyricsImporterTest.testExtenderWith…` ("_garden") | adequate | keep |
| LegacyLyricsImporter | trailing `_` run abutting next word → one continuation absorbed | unit | `LegacyLyricsImporterTest.testFullCombinedExample` | adequate | keep |
| LegacyLyricsImporter | stray `-`/`=` without preceding word → skipped (no lyric, no advance) | unit | — | missing | blob `- word` → first note gets `word` |
| LegacyLyricsImporter | stray `--` without preceding word mid-line → skipped (two chars consumed) | unit | — | missing | blob `-- word` → first note gets `word` |
| LegacyLyricsImporter | `isWordChar` boundary (space/tab/`_`/`-`/`=`/`\n` false; ASCII true) | unit | (implicit via all paths) | adequate | no isolated gap given full path coverage |
| LegacyLyricsImporter | full combined scenario (extend + compound + multi-syllable) | unit | `LegacyLyricsImporterTest.testFullCombinedExample` | adequate | keep |

**2D notes (quality concerns):** The single biggest blind spot is **`FormatMigrator.migratePixelsToStaffSpace`** — the one effect test asserts only the four song-level scalar divisions; the entire per-line body (`lyricsYPosSs`, tuplet vertPos, hairpin shifts, glissando translates, attachment `userYOffsetSs`, `xOffsetPx` reset, ending/trill yPos) is unasserted, so a divisor off-by-one or a missed field passes silently. Second: **`migrateLineLevelOffsets`** (the v1→v2 stage) runs only against empty line lists in the pipeline test, so every per-type offset migration is untested. Three pipeline "effect" tests are no-crash smoke (`testEffectRunsOnEmptyLines` for LEGACY_FORMAT / ANNOTATION_DYNAMICS / SYLLABIC_BACKFILL); the SYLLABIC_BACKFILL one is especially misleading — it mocks a Song returning an empty list, so `backfillSyllabic()` never fires and a deleted `forEach` would still pass. TOP_PADDING_FALLBACK's effect test uses `attribution=""`, leaving the line-count term at 0 and the multi-line branch uncovered. LEGACY_LYRICS has no direct effect test (relies on `SongIOTest`). `requireSong`'s ISE guard is untested. `LegacyLyricsImporter` is otherwise strongly covered — only the stray-marker paths (`-`/`--` without a preceding word) are missing.

### io — production observations (out of test-audit scope)

Filed as a single tracked GitHub issue ([#407](https://github.com/vasudeva-server/SongScribe/issues/407)) — these are real code observations, not test gaps, so the disposable matrix isn't their only home:

1. **⚠️ `LineIO` — `Ending.Type` data loss (correctness bug).** `endingsToString` serializes only anchor/end indices; `createEndingsFromPendingPairs` hard-codes `Ending.Type.FIRST`. Any **SECOND** ending (a "2." volta bracket) is silently reset to FIRST on save/load. Needs a real fix (encode/decode the type), not just a regression test.
2. **`LineIO` — missing bounds guards.** `createBeamsFromPending`/`createTupletsFromPending` validate index ranges; `createTiesFromPendingPairs`, `createCrescendosFromPending`, `createDiminuendosFromPending`, `createTrillsFromPendingPairs`, `createEndingsFromPendingPairs` do not — they throw `IndexOutOfBoundsException` on truncated/corrupt files. Make uniform or document as fail-loud.
3. **`LineIO` — `parseEndingPairs` asymmetry.** It uniquely `.clear()`s its pending list at entry; all other `parse*` methods accumulate. Document or unify.
4. **`XML` — static mutable `indent`.** `setIndent`/`printIndent` use an unsynchronized static field: a thread-safety hazard in production and a test-isolation hazard (tests that call `setIndent` leak state). Make it a parameter or document not-thread-safe.
5. **`FormatMigrator` — pixel-vs-staff-space unit coupling.** `applyTopPaddingFallback` and `migrateLineLevelOffsets` compute pixel-valued quantities/deltas and assign them to `*Ss` fields, correct only because `migratePixelsToStaffSpace` divides by pps afterward. Tests written in terms of the same formula can't catch a unit mismatch. Verify against `SongIOTest.testTopPaddingFallbackValueReachesSong` and add an explanatory comment about the two-step dependency.
6. **`StaffElementIO` — `lenght` parameter misspelling** in `characters` (cosmetic; compiles and works).
7. **`TempoIO` — `endElement11` has no legacy-duration-name lookup** (only `endElement10` does); a v1.1 file with a legacy name (e.g. `MINIMDOTTED`) throws `IllegalArgumentException` from `Duration.valueOf`. Likely an intentional v1.1 contract, but untested.

### io — summary

Audited all 15 production classes (excl. `package-info`). Dominant patterns to drive remediation:

1. **Serialization *write* paths are the biggest blind spot.** Existing coverage is round-trip-via-`SongIO`, which verifies value preservation but never **conditional emission** (tag omitted when zero/null/empty) or exact serialized format. `XML.escapeXML`, `writeSong`'s conditional fields, `ViewIO.writeView`, and most of `LineIO`'s field/range-element writers are unasserted.
2. **`LineIO` (the largest IO class) has no dedicated test file** — six of seven range-element serializers and the shared `forEachSegment` parser are entirely untested.
3. **Legacy/v1.0 decode paths are dark:** `StaffElementIO`/`TempoIO` `*10` methods, legacy type/duration renames, `AnnotationIO`/`TempoIO` round-trips, and `BeatChange.fromLegacyName` happy paths (echoing the `dom` finding).
4. **Migration is best-covered, but its *per-line* conversions aren't:** `migratePixelsToStaffSpace`/`migrateLineLevelOffsets` bodies run only against empty line lists in tests; several pipeline "effect" tests are no-crash smoke (one mocks away the very call it claims to test).
5. **"Weak-but-green" tests:** tautologies (`ViewIO` `x.equals(x)`), `isNotNull()`-only assertions (`SongLoaderTest`, legacy-tolerance), and indirect round-trips standing in for direct behavioral assertions.
6. **Real code defects surfaced** (see production observations) — most notably the `Ending.Type` round-trip data loss.

## 3. `layout` (audited 2026-05-21)

Audited all 37 production classes (excl. 2 `package-info`) via six parallel production-first sub-audits: **orchestration & accumulation**; **horizontal spacing & columns**; **geometry primitives & metrics**; **lyric layout**; **ranges/endings/collision**; **stacking subsystem**. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. One verdict reclassified from a sub-audit's `wrong-level` (`LineEndingSupport.findEndingReplacementEffect`): the vocabulary reserves `wrong-level` for unit↔e2e mismatches; a unit behavior covered only indirectly is `inadequate`.

### 3A. orchestration & accumulation — `LayoutEngine`, `LayoutAccumulator`, `LayoutResult`, `LayoutLayer`, `SectionLayout`, `PageModel`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LayoutEngine | `layout()` returns non-null with clef at `CLEF_X_POSITION_SS` | unit | `LayoutEngineTest.testLayoutStoresClefAtStandardPosition` | adequate | keep |
| LayoutEngine | `layout()` places key signature immediately after clef (type + accidental count) | unit | `LayoutEngineTest.testLayoutStoresKeySignatureAfterClef` | adequate | keep |
| LayoutEngine | `layout(line, true)` pins FINAL_DOUBLE_BARLINE flush-right | unit | `LayoutEngineTest.testFinalBarlineFlushRightOnLastLine` | adequate | keep |
| LayoutEngine | `layout(line, true)` pins REPEAT_RIGHT terminal flush-right | unit | `LayoutEngineTest.testRightRepeatTerminalFlushRightOnLastLine` | adequate | keep |
| LayoutEngine | `layout(line, false)` does NOT place barline flush-right | unit | `LayoutEngineTest.testFinalBarlineNotFlushRightOnNonLastLine` | inadequate | negative `isNotCloseTo(flushRight)` survives any wrong value (incl. X=0); assert exact expected X from horizontal spacing |
| LayoutEngine | empty line returns non-null result with `MIN_LINE_HEIGHT_SS` | unit | `LineHeightTest.testEmptyLineZeroReturnsMinimumHeight`, `testEmptyNonLastLineReturnsMinimumHeight` | adequate | keep |
| LayoutEngine | empty-line result still contains clef + key signature | unit | — | missing | assert `getClef()`/`getKeySignature()` non-null on empty line |
| LayoutEngine | un-justifiable line → `layout()` returns null, `getLastError()` non-null | unit | — | missing | over-stuffed line → null result + descriptive error |
| LayoutEngine | 3-arg `layout(line,false,true)` threads `hasLeadingLyricContinuation` to lyric layout | unit | — | missing | extending melisma → leading lyric connector at x=0 |
| LayoutEngine | unbeamed note below middle line (sp>0) → stem up | unit | — | missing | crotchet at sp=2 → stem-up geometry |
| LayoutEngine | unbeamed note above middle line (sp≤0) → stem down | unit | — | missing | crotchet at sp=-2 → stem-down |
| LayoutEngine | unbeamed grace note always stem up | unit | — | missing | grace at sp=-4 still stem-up, length `GRACE_NOTE_STEM_LENGTH_SS` |
| LayoutEngine | manual stem override not auto-corrected | unit | — | missing | `upper=false`,`stemDirectionAuto=false` at sp=4 → stays down |
| LayoutEngine | beamed group auto-direction (above→down, below→up) | unit | — | missing | two tests on `BeamLayout.stemsUp()` |
| LayoutEngine | beamed group manual override: first explicit direction wins for whole group | unit | — | missing | first note `upper=true` → `stemsUp=true` |
| LayoutEngine | beam slope hyperbolic dampening clamps below `BEAM_SLOPE_MAX` | unit | — | missing | large pitch diff → `abs(slope) < BEAM_SLOPE_MAX` |
| LayoutEngine | beam slope-reduction loop: all stems ≥ `MIN_STEM_SS` | unit | — | missing | large contour → every stem ≥ MIN_STEM_SS |
| LayoutEngine | flat-beam snapping: slope<0.05 snaps `startYSs` to 0.5 grid | unit | — | missing | equal-position quavers → startYSs multiple of 0.5 |
| LayoutEngine | beam thickening: non-zero slope → `thickeningSs` in `(0, BEAM_DEPTH_SS*0.088]` | unit | — | missing | sloped group → bounded thickening |
| LayoutEngine | stub direction: isolated semiquaver gets stub-right | unit | — | missing | quaver+semiquaver beam → `stubRight=true` |
| LayoutEngine | tie geometry: `startXSs = noteX + TIE_NOTEHEAD_HALF_WIDTH_SS` | unit | — | missing | adjacent-note tie offset |
| LayoutEngine | tie shoulder height clamped to `[TIE_MIN, TIE_MAX]` | unit | — | missing | narrow→min, wide→max |
| LayoutEngine | tie collision: interior note deflects arc outward | unit | — | missing | 3-note tie over intersecting note → larger outer control Y |
| LayoutEngine | tie direction: stem-up note ties below (+1) | unit | — | missing | stem-up note → arc bulges down |
| LayoutEngine | `createHeaderElements` null `keyType` → `KeyType.NONE` | unit | `LayoutEngineTest.testLayoutStoresKeySignatureAfterClef` (non-null only) | missing | null keyType → keySig type NONE |
| LayoutEngine | `beamCount` → 1/2/3 for QUAVER/SEMIQUAVER/DEMI_SEMIQUAVER | unit | — | missing | widen to package-private; assert each |
| LayoutResult | `Builder.setClef`/`setKeySignature` round-trip; default null | unit | `LayoutResultTest.testBuilderClefRoundTrip`, `…KeySignatureRoundTrip`, `…DefaultsToNullHeaderElements` | adequate | keep |
| LayoutResult | `getLyricAnchor` box-anchored centerX+baselineY; column fallback; Y==`verseYSsInLine(1)`; throws ISE w/ neither | unit | `LayoutResultTest.testGetLyricAnchor*` (4) | adequate | keep |
| LayoutResult | `hitTestLyric` inside-box hit / outside-box miss | unit | `LayoutResultTest.testHitTestLyric*` (2) | adequate | keep |
| LayoutResult | `findElementAtXSs` returns index within head bounds / -1 in gap | unit | — | missing | two known-X columns; hit + gap |
| LayoutResult | `findInsertionIndex` over-head / before-first(0) / after-last(`effectiveElementCount`) / in-gap(slot) | unit | — | missing | write 4 tests |
| LayoutResult | `calculateInsertionXSs` empty / over-head snap / terminal right-align / after-last spacing / between-midpoint | unit | — | missing | write 5 tests |
| LayoutResult | `getBelowStaffReservationSs` = `lineHeight - aboveStaff - STAFF_HEIGHT_SS` | unit | — | missing | known-values test |
| LayoutResult | `lyricAreaBaseYSs` shifts with `aboveStaffSs`/`belowContentSs` | unit | `LayoutResultTest.testHitTestLyricHitsInsideBounds` (indirect) | inadequate | focused test pinning the formula |
| LayoutResult | `findAttachmentBounds` correct owner/type; null unknown owner | unit | — (stacking tests use `findAttachmentDecorationLayout`) | missing | two same-type attachments on different owners |
| LayoutResult | `findRangeElementBounds` by anchor+end+type | unit | — | missing | write test |
| LayoutResult | `findAttachment` matching owner/type else null | unit | — | missing | write test |
| LayoutResult | `findRangeElementDecorationLayout` by anchor+type | unit | covered transitively (`FermataTrillStackingTest` etc. use attachment variant) | inadequate | focused range-element test |
| LayoutResult | `contains` true iff `elementBounds` has element | unit | — | missing | write test |
| LayoutResult | `getDecorationLayoutsByType` filters by class | unit | — | missing | two types → each filtered list correct |
| LayoutResult | `getElementXSs` 0 / `getElementPosition` null for unknown element | unit | — | missing | write tests |
| LayoutAccumulator | `add`/`intersects` (Rectangle2D + Area), overlap true / non-overlap false | unit | — | missing | write tests |
| LayoutAccumulator | `clear` → `isEmpty` true and `intersects` false; fresh `isEmpty` true | unit | — | missing | write tests |
| LayoutAccumulator | `getArea()` returns defensive copy | unit | — | missing | mutate return; accumulator unchanged |
| LayoutAccumulator | union of two rects intersects a spanning rect | unit | — | missing | write test |
| SectionLayout | `hasContent()` true non-empty / false empty list / false empty first line | unit | — | missing | 3 tests |
| SectionLayout | `getText()` first line / "" when empty | unit | — | missing | write tests |
| SectionLayout | `getHeight()` from content bounds | unit | — | missing | known-bounds test |
| SectionLayout | `empty()` factory: zero size, no lines, null font | unit | — | missing | assert each property |
| SectionLayout | 2-arg string ctor wraps text in single-element list | unit | — | missing | round-trip via `lines()` |
| SectionLayout | `lines()` immutable (defensive copy) | unit | — | missing | mutate source; `lines()` unchanged |
| PageModel | `Size.LETTER`/`A4` dimensions | unit | `PageModelTest.SizeEnum.*` (2) | adequate | keep |
| PageModel | `getSize()` default LETTER / "a4" / case-insensitive / unknown→LETTER | unit | `PageModelTest.PageSizeFromPrefs.*` (4) | adequate | keep |
| PageModel | `getPageWidthPx`/`getPageHeightPx` for LETTER+A4 | unit | `PageModelTest.PageDimensionsPx` (4) | adequate | keep |
| PageModel | top/bottom margins = 0.5"; horizontal centers; 0 when line ≥ page | unit | `PageModelTest.Margins.*` (5) | adequate | keep |
| PageModel | `getContentAreaWidthPx` = `pageWidth - 2*defaultMargin` | unit | `PageModelTest.ContentArea.contentAreaWidthAccountsForDefaultMargins` | inadequate | self-referential (expected uses same formula); pin concrete px for LETTER |
| PageModel | `getMaxLineWidthInches`=7.77 / `getMinLineWidthInches`=5.0 | unit | `PageModelTest.LineWidthConstants.*` (2) | adequate | keep |
| PageModel | `getDefaultLineWidthSs` = `pxToSs(contentAreaWidthPx)` | unit | `PageModelTest.DefaultLineWidth.defaultLineWidthSsMatchesContentArea` | inadequate | self-referential oracle; pin explicit LETTER constant |
| PageModel | size changes reactively on pref change; A4 width < LETTER | unit | `PageModelTest.PageSizeChange.*` (2) | adequate | keep |
| LayoutLayer | enum constants (ELEMENT, TIE, …, LYRICS) | none | — | none | pure enum, no derivation |
| LayoutEngine/VSC | high/low note increases line height | unit | `LineHeightTest.testHighNoteAboveStaffIncreasesLineHeight`, `testLowNoteBelowStaffIncreasesLineHeight` | inadequate | `>=MIN_LINE_HEIGHT_SS` passes even if extension broken; assert exact height for the staff position |

**3A notes (quality concerns):** **The highest-risk gap is the total absence of tests for `LayoutEngine`'s three geometry engines** — beam slope/direction/stub logic, unbeamed stem-direction assignment, and tie Bézier geometry. This is the densest math in the package (hyperbolic dampening, iterative slope reduction, 20-iteration convergence, Bézier collision avoidance) with zero coverage; mutations to `< MIN_STEM_SS` or the `stemsUp ? pos<anchor : pos>anchor` branch would survive. `LayoutAccumulator` and `SectionLayout` have zero coverage despite real branching (`hasContent()`, `intersects()`, `clear()`) — trivially unit-testable, no mocking. Two `PageModelTest` tests are self-referential oracles (`contentAreaWidthAccountsForDefaultMargins`, `defaultLineWidthSsMatchesContentArea`). `LineHeightTest`'s high/low-note tests use `>=MIN_LINE_HEIGHT_SS` (the universal floor) — green even if the height extension returns exactly the minimum. `LayoutResult`'s hit-testing/insertion/lookup family (`findElementAtXSs`, `findInsertionIndex`, `calculateInsertionXSs`, `findAttachmentBounds`, `findRangeElementBounds`, `findAttachment`, `contains`, `getDecorationLayoutsByType`) is pure map-lookup logic, all untested, all straightforwardly unit-testable via `Builder`. `LayoutLayer` correctly classified `none`.

### 3B. horizontal spacing & columns — `ElementColumn`, `ElementColumnBuilder`, `HorizontalSpacingCalculator`, `InsertionSpacingCalculator`, `LineJustificationCalculator`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementColumn | ctor stores all fields (graceNotes defensively copied) | unit | fixture-only in stacking tests, fields never asserted | inadequate | `ElementColumnTest`: assert field storage + defensive copy |
| ElementColumn | `getWidthSs` = `abs(leftExtent)+rightExtent` | unit | — | missing | test formula |
| ElementColumn | `getLeftEdgeXSs` = `xSs+leftExtent`; `getRightEdgeXSs` = `xSs+rightExtent` | unit | — | missing | test (incl. negative leftExtent / accidental) |
| ElementColumn | `hasSyllable()` false when null or empty | unit | — | missing | null + "" cases |
| ElementColumn | `minGapToNextSyllableSs` round-trip; default `LyricRenderMetrics.MIN_SYLLABLE_GAP_SS` | unit | — | missing | default + setter |
| ElementColumn | `isRest`/`isBarline`/`isBeamed`/`hasGraceNotes`/`hasGlissando` delegation | unit | — | missing | one delegation test each |
| ElementColumnBuilder | `calculateRightExtentSs` unbeamed quaver > notehead-only | unit | `ElementColumnBuilderTest.testUnbeamedQuaverExtentExceedsNoteheadOnly` | adequate | keep |
| ElementColumnBuilder | beamed quaver = notehead-only (flag suppressed) | unit | `testBeamedQuaverExtentEqualsNoteheadOnly` | adequate | keep |
| ElementColumnBuilder | non-flagged types unchanged by beamed/upper | unit | `testNonFlaggedTypesUnchanged` | adequate | keep |
| ElementColumnBuilder | stem-up vs stem-down differ (unbeamed quaver) | unit | `testStemUpVsStemDownProduceDifferentExtents` | inadequate | `isNotEqualTo` survives constant swap; pin exact values |
| ElementColumnBuilder | grace quaver < regular quaver | unit | `testGraceQuaverExtentSmallerThanRegularQuaver` | inadequate | `isLessThan` only; pin exact values |
| ElementColumnBuilder | dotted quaver = max(dots-extent, flag-extent) | unit | `testDottedQuaverExtentIsMaxOfDotsAndFlag` | inadequate | `>=` survives extra dot width; assert exact |
| ElementColumnBuilder | two-dot extent includes two gap+dot pairs | unit | — | missing | double-dotted test |
| ElementColumnBuilder | rest/barline → `type.getElementWidthSs()` unchanged | unit | — | missing | REST + BARLINE test |
| ElementColumnBuilder | `calculateLeftExtentSs` 0 without accidental; negative `-(accW+ACCIDENTAL_GAP_SS)` with | unit | — | missing | both cases |
| ElementColumnBuilder | `buildColumn` minGap = hyphen width (BEGIN/MIDDLE) vs space width (END/SINGLE) | unit | — | missing | hyphenated + non-hyphenated lyric |
| ElementColumnBuilder | `buildColumns` empty line → empty list | unit | — | missing | empty-line edge |
| ElementColumnBuilder | `calculateStemTop/BottomSs` for up/down/stemless | unit | — | missing | widen to package-private; stem geometry |
| HorizontalSpacingCalculator | `calculateFirstElementXSs(n)` = clef + n·keyAcc + firstNoteOffset | unit | `HorizontalSpacingCalculatorTest.testFirstNoteXMatchesCalculateFirstElementXSs` | inadequate | **self-referential**: compares `calculatePositions` to same formula; pin concrete value |
| HorizontalSpacingCalculator | `calculateHeaderRightEdgeSs(n)` = clef + n·keyAcc | unit | — | missing | 0/3/7 accidentals |
| HorizontalSpacingCalculator | `calculateNextColumnXSs` min spacing = prevRight+MIN_GAP+abs(currLeft) | unit | — | missing | two plain columns, exact value |
| HorizontalSpacingCalculator | default gap floor dominates without lyrics | unit | — | missing | verify DEFAULT_GAP floor |
| HorizontalSpacingCalculator | lyric spacing dominates with wide syllables | unit | — | missing | wide-syllable columns |
| HorizontalSpacingCalculator | accidental push when next column accidental would overlap | unit | — | missing | construct triggering case |
| HorizontalSpacingCalculator | grace→host tight gap | unit | — | missing | grace+host columns |
| HorizontalSpacingCalculator | glissando spacing enforced (`ensureGlissandoSpacing`) | unit | — | missing | prev-has-glissando |
| HorizontalSpacingCalculator | `calculatePositions` empty list returns (no exception) | unit | — | missing | guard test |
| HorizontalSpacingCalculator | beam-group tight spacing + even lyric expansion (`identifyBeamGroupRanges`/`handleBeamGroup`) | unit | — | missing | critical multi-branch; with/without lyrics |
| HorizontalSpacingCalculator | single-column beam group → normal spacing | unit | — | missing | edge case |
| InsertionSpacingCalculator | `calculateInsertion` out-of-bounds → IAE | unit | — | missing | negative + > count |
| InsertionSpacingCalculator | `calculateAppendPositionSs` empty line → `calculateFirstElementXSs` | unit | `FitsWithinLine.testAppendToEmptyLine` (asserts `fitsWithinLine(500)`) | inadequate | assert exact X = `calculateFirstElementXSs(keyAccidentalCount)` |
| InsertionSpacingCalculator | `fitsWithinLine` exact margin+DEFAULT_GAP boundary → false | unit | `testInsertIntoNearlyFullLine` (uses width-1) | inadequate | test the exact `DEFAULT_COLUMN_GAP_SS` boundary |
| InsertionSpacingCalculator | `hasRoomForGraceNote` empty/full/plenty | unit | `HasRoomForGraceNote.*` (3) | adequate | keep |
| InsertionSpacingCalculator | `hasRoomForHostNoteAfterGrace` room/no-room | unit | `HasRoomForHostNoteAfterGrace.*` (2) | adequate | keep |
| InsertionSpacingCalculator | `calculateInsertion` at index 0 correct X + shift | unit | — | missing | verify X and downstream shift |
| InsertionSpacingCalculator | mid-insertion shift = max(0, required), never negative | unit | — | missing | non-negative shift |
| InsertionSpacingCalculator | `calculateNextElementXSs` delegates via xOffset | unit | — | missing | equals `calculateNextColumnXSs` |
| InsertionSpacingCalculator | `InsertionResult.newLineWidthSs` = max(inserted right edge, shifted last) | unit | round-trip tests check only `fitsWithinLine` | inadequate | assert `newLineWidthSs()` directly |
| LineJustificationCalculator | empty list → success | unit | — | missing | guard |
| LineJustificationCalculator | line fits → success, no compression | unit | — | missing | assert `!wasCompressionApplied()` |
| LineJustificationCalculator | compression ratio = (target-extentOffset)/centerSpan | unit | — | missing | two columns over margin; verify ratio + positions |
| LineJustificationCalculator | `applyCompression` first column fixed, rest scale by ratio | unit | — | missing | exact compressed positions |
| LineJustificationCalculator | `validateCompression` rejects gap < `COMPRESSED_MIN_COLUMN_GAP_SS` | unit | — | missing | tight columns → failure |
| LineJustificationCalculator | rejects syllable gap < `COMPRESSED_MIN_SYLLABLE_GAP_SS` | unit | — | missing | wide-syllable columns |
| LineJustificationCalculator | `success`/`successWithCompression`/`failure` factories + errorMessage null contract | unit | — | missing | guard on getErrorMessage |
| LineJustificationCalculator | line-too-full → user-facing error at insert | e2e | `ElementInsertionTest.FullLine.testInsertIntoFullLineShowsError` | adequate | keep |

**3B notes (quality concerns):** Four critical gaps. (1) **`HorizontalSpacingCalculatorTest` is entirely self-referential** — its single test asserts `calculatePositions` equals `calculateFirstElementXSs` (same formula), so zeroing `FIRST_NOTE_OFFSET_SS` would stay green; the whole spacing class is "covered" by a tautology. (2) **`LineJustificationCalculator` has zero tests** despite non-trivial float math (compression ratio, gap-after-compression, two min-gap validators); `LayoutEngineTest` never constructs an over-margin line so the compression path is dark everywhere. (3) **`ElementColumnBuilderTest` uses relational assertions** (`isNotEqualTo`/`isLessThan`/`>=`) where the values are statically computable from SMuFL/Engraving constants — magnitude-perturbing mutations survive. (4) `InsertionSpacingCalculator` append-to-empty and `fitsWithinLine` boundary tests are weak, and `InsertionResult.newLineWidthSs` is never directly asserted. Out-of-scope production observation: `HorizontalSpacingCalculator.needsAccidentalPush` ignores its `prevColumn`/`currXSs` parameters and returns true whenever the current element has any accidental — the real clearance check lives in the caller; the method signature implies a pre-check it doesn't perform (unused params, code smell — review, don't act blindly).

### 3C. geometry primitives & metrics — `ElementBoundsSs`, `InsetsSs`, `Size`, `Margin`, `MarginReference`, `LineThickness`, `NoteGeometry`, `StaffExtents`, `VerticalOrder`, `SongLayoutMetrics`, `SongLayoutMetricsBuilder`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementBoundsSs | `uniform`/`withMargin`/`withMarginOnly`/`contentOnly` factories | unit | — | missing | assert exact layer rects per factory (incl. no-top-margin in `withMargin`) |
| ElementBoundsSs | `collapsedMarginWith(below)` = `max(thisBottom, belowTop)` CSS collapse | unit | — | missing | this-wins / below-wins / equal |
| ElementBoundsSs | `containsForHitTest` delegates to padding bounds | unit | — | missing | inside-padding / inside-content-outside-padding / outside |
| ElementBoundsSs | `intersectsMargin`/`intersectsPadding` layer-specific | unit | — | missing | overlap + non-overlap per layer |
| ElementBoundsSs | `translate(dx,dy)` shifts all four layers (incl. nullable visual) | unit | — | missing | verify exact shift |
| ElementBoundsSs | `getVisualBounds()` explicit-visual vs `marginBoundsSs` fallback | unit | — | missing | both branches |
| ElementBoundsSs | coordinate accessors (`getTop/Bottom/Left/Right/MarginTop/MarginBottom Ss`) | unit | — | missing | fold into factory tests |
| ElementBoundsSs | `formatCssSpacing` 5-branch shorthand (all-zero/all-same/2/3/4-value) | unit | — | missing | each branch; **see production observation re px/ss suffixes** |
| ElementBoundsSs | `getPaddingCss`/`getMarginCss` pass correct differentials | unit | — | missing | uniform + asymmetric |
| InsetsSs | `toInsetsPx()` rounds all four via `ScaleContext.ssToRoundedPx` | unit | — | missing | known scale → exact Insets fields |
| Size | pure record (`ZERO`, width/height) | none | — | none | trivial data holder |
| Margin | `uniform(m)` all sides equal; `NONE` all zero | unit | — | missing | one-liner asserting invariant |
| MarginReference | pure documentation enum | none | — | none | no branching |
| LineThickness | each field = `LILYPOND_BASE_THICKNESS_SS × multiplier` | unit | only `barlineSeparationSs()` exercised indirectly (`ElementTypeTest`) | inadequate | assert `stemSs`/`ledgerLineSs`/`hairpinSs`/`voltaBracketSs`/`tupletBracketSs` + multipliers |
| LineThickness | `barlineSeparationSs()` = `staffLineSs × BARLINE_SEPARATION_MULTIPLIER` | unit | `ElementTypeTest.testDoubleBarlineWidth` | adequate | keep |
| LineThickness | `repeatRightThinBarlineCenterXSs`/`repeatRightAfterThickXSs` arithmetic | unit | — | missing | known-constant tests |
| NoteGeometry | `initializeAccidentalWidths()` idempotent | unit | `NoteRendererTest`/`NoteAreaBuilderTest` (called twice) | adequate | keep |
| NoteGeometry | `getAccidentalWidthSs(note)` dispatch small/base/parens; 0 for none | unit | — | missing | each accidental kind; exact SMuFL width |
| NoteGeometry | `getAccidentalBoundsSs(note)` null/grace-null/table | unit | `NoteRendererTest` (directional only) | inadequate | weak `isNegative`/`isPositive`; pin exact for ≥1; add DOUBLE_SHARP/NATURAL_* variants |
| NoteGeometry | `getLedgerLineOverhangSs(note)` 0 in-staff / `LEDGER_LINE_EXTENSION_SS` out | unit | — | missing | |sp|≤5 vs >5 with `drawStaveLongitude` |
| NoteGeometry | `getNoteheadXOffsetSs(type,upper)` `-stemWidth/2` stem-down else 0 | unit | — | missing | stemmed up/down/non-stemmed |
| NoteGeometry | `getNoteheadRightEdgeSs(note)` SMuFL bbox + fallback | unit | — | missing | known bbox + null fallback |
| NoteGeometry | `walkAccidentalGlyphs` visitor advances/parens/kerning | unit | — | missing | verify emitted positions for known sequence |
| StaffExtents | `spToSs(sp)` = sp×0.5 | unit | fixture input only (`VerticalStackingCalculatorTest`) | missing | direct: spToSs(0/2/-4) + round-trip |
| StaffExtents | `ssToSp(ss)` = round(ss/0.5) | unit | — | missing | exact + rounding boundaries |
| StaffExtents | `xToStep` clamp (private, via ySet/yGet) | unit | `StaffExtentsTest` (clamping via ySet/yGet) | adequate | keep |
| StaffExtents | `ySet`/`yGet` reserve/query above/below | unit | `StaffExtentsTest` (defaults, overlaps, clamp, isolation) | adequate | keep |
| StaffExtents | `copyTopFrom` copies top, leaves bot | unit | `StaffExtentsTest.CopyTopFrom` | adequate | keep |
| StaffExtents | derived constants (`MIN_ABOVE/BELOW_STAFF_SS`, `MIN/MAX_STAFF_POSITION_SP`) | unit | used, never asserted | missing | pin computed values (catch `STAFF_LINES_ABOVE/BELOW` change) |
| VerticalOrder | `isAboveStaff`/`isBelowStaff` relative to `NOTE_STEM.order` | unit | — | missing | each constant; NOTE_STEM neither |
| VerticalOrder | `compareByOrder` | unit | — | missing | <0 / >0 / 0 cases |
| SongLayoutMetrics | `staffTopYSsInLine`/`staffBottomYSsInLine` | unit | `SongLayoutMetricsTest.testStaffYHelpers` | adequate | keep |
| SongLayoutMetrics | `verseYSsInLine(verse)` formula | unit | `SongLayoutMetricsTest.testVerseBaselineYHelper` | inadequate | **self-referential** (expected from same accessors); pin concrete literals |
| SongLayoutMetricsBuilder | `build()` max above/below/belowContent; floor at `MIN_*` | unit | `SongLayoutMetricsTest` (empty, max above/below/belowContent) | adequate | keep |
| SongLayoutMetricsBuilder | lyricsBand collapse when `verseCount==0` | unit | `SongLayoutMetricsTest.testVerseCountCollapsesWhenNoLyrics` | adequate | keep |
| SongLayoutMetricsBuilder | `staffToLyricsGapSs = LYRICS_ROW_MARGIN_SS + lyricAscentSs` | unit | `…testLyricsBandPopulatedWhenVersesPresent` (passes ascent=0) | inadequate | `lyricAscentSs` addend never tested (always 0); add non-zero ascent |
| SongLayoutMetricsBuilder | `totalLineHeightSs` includes lyricsBand | unit | `…testLyricsBandPopulatedWhenVersesPresent` | adequate | keep |
| SongLayoutMetricsBuilder | `MIN_LINE_HEIGHT_SS` constant | unit | `LineHeightTest` (`>=` weak) | inadequate | pin exact height for note at `MIN_STAFF_POSITION_SP` |

**3C notes (quality concerns):** **`ElementBoundsSs`** — the central CSS-like box-model type used throughout layout — has zero unit tests: all factories, `collapsedMarginWith`, `containsForHitTest`, both intersections, `translate`, `getVisualBounds` fallback, and the 5-branch `formatCssSpacing` are untested (and `formatCssSpacing` has a confirmed px/ss suffix bug — see production observations). Second: **`NoteGeometry` accidental geometry** — `getAccidentalBoundsSs` is covered only directionally (`isNegative`/`isPositive`) so a double-width regression stays green, and `getAccidentalWidthSs` (the width that positions notes horizontally) has no test at all; `getLedgerLineOverhangSs`/`getNoteheadXOffsetSs`/`getNoteheadRightEdgeSs` untested. `SongLayoutMetricsTest.testVerseBaselineYHelper` is a self-referential oracle. `SongLayoutMetricsBuilder` is never tested with non-zero `lyricAscentSs`, so the `+ lyricAscentSs` addend is dead from a test view. **`StaffExtents.spToSs`/`ssToSp`** — the project-canonical Sp↔Ss converters — have no direct assertion (echoes the `dom` `ScaleContext` finding). `InsetsSs.toInsetsPx` (the only non-trivial Ss→Px conversion among the records) is untested. `VerticalOrder`'s branching predicates (`isAboveStaff`/`isBelowStaff`/`compareByOrder`) drive stacking yet are untested. `LineThickness` non-barline fields and the two repeat-barline helpers are untested. `LineHeightTest` high/low-note tests use `>=MIN_LINE_HEIGHT_SS` (weak).

### 3D. lyric layout — `LyricBoxLayout`, `LyricConnectorLayout`, `LyricLayoutBuilder`, `LyricRenderMetrics`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LyricBoxLayout | pure data record | none | — | none | no computation |
| LyricConnectorLayout | `Kind` enum + `NO_SOURCE_ELEMENT_INDEX` sentinel; discriminants drive rendering | none | `LyricConnectorRendererTest` (renderer level) | none | rendering assertion belongs to renderer |
| LyricRenderMetrics | `lyricBoxWidthSs("")` → 0.0 | unit | — | missing | empty guard |
| LyricRenderMetrics | `lyricBoxWidthSs(text)` advance for non-empty | unit | `LyricRenderMetricsTest.testLyricBoxWidthSsMatchesLayoutBoxWidth` | inadequate | **self-referential**: builder stores `lyricBoxWidthSs(text)` then asserts equality; use independent oracle |
| LyricRenderMetrics | `lyricBoxMetricsSs("")` → `LyricBoxMetrics.EMPTY` | unit | — | missing | empty guard |
| LyricRenderMetrics | `lyricBoxMetricsSs(text)` advance/bearing/extent triple | unit | — | missing | fixed font or structural relations |
| LyricRenderMetrics | `lyricBoxHeightSs()` positive ascent+descent | unit | — | missing | write test |
| LyricRenderMetrics | `preferredHyphenCellWidthSs()` = `HYPHEN_WIDENING_FACTOR × hyphenWidthSs` | unit | — | missing | non-zero hyphen width |
| LyricRenderMetrics | `COMPRESSED_MIN_SYLLABLE_GAP_SS < MIN_SYLLABLE_GAP_SS` invariant | unit | — | missing | ordering assertion |
| LyricLayoutBuilder | empty line / no-lyrics → empty result | unit | `testEmptyLineProducesEmptyResult`, `testLineWithoutLyricsProducesEmptyResult` | adequate | keep |
| LyricLayoutBuilder | BEGIN/MIDDLE→opens HYPHEN, END closes (do-re-mi) | unit | `testDoReMiProducesThreeBoxesAndTwoHyphens` | inadequate | only count asserted; add HYPHEN start/end coords + sourceElementIndex |
| LyricLayoutBuilder | SINGLE no-extend → box, no connector | unit | implicit via multi-element tests | adequate | keep |
| LyricLayoutBuilder | `computeLyricBoxLeftXSs` normal note: center − halfWidth | unit | — | missing | assert box X centering |
| LyricLayoutBuilder | grace note: first glyph centred on grace notehead; host no box | unit | `testGraceLyricFirstGlyphCentredOnGraceNoteheadAndHostHasNoBox` | adequate | keep |
| LyricLayoutBuilder | `firstGraphemeClusterEndIndex` multi-codepoint cluster | unit | — | missing | combining mark + surrogate pair |
| LyricLayoutBuilder | note no-lyric + active extender passes through | unit | `testExtenderSpansContinuationNotes` | adequate | keep |
| LyricLayoutBuilder | REST no-lyric → extender closed at rest left | unit | `testRestWithoutLyricBreaksExtender` | adequate | keep |
| LyricLayoutBuilder | REST + START → extender continues | unit | `testRestWithExtendingLyricContinuesExtender` | adequate | keep |
| LyricLayoutBuilder | REST + CONTINUE → extender continues (distinct sub-case) | unit | — | missing | CONTINUE on rest |
| LyricLayoutBuilder | REST + STOP → closes `STOP_MELISMA_OVERSHOOT_SS` past rest right | unit | — | missing | assert ending = rightEdge + overshoot |
| LyricLayoutBuilder | note + STOP → closes with overshoot, no box | unit | `testStopCarrierEndsExtenderAtNoteRightEdge` | adequate | keep (assertion uses constant; see stale-comment observation) |
| LyricLayoutBuilder | note + CONTINUE passes through | unit | `testContinueCarrierPassesThrough` | adequate | keep |
| LyricLayoutBuilder | BEGIN+START → hyphen, extender suppressed | unit | `testNonFinalSyllableWithMelismaEmitsHyphenOnly` | adequate | keep |
| LyricLayoutBuilder | extender opens SINGLE+START, closes at next text note | unit | `testExtenderSpansContinuationNotes` | adequate | keep (but `startXSs` unverified) |
| LyricLayoutBuilder | NONE-extend text note with active extender closes at box left | unit | `testContinueCarrierPassesThrough` | adequate | keep |
| LyricLayoutBuilder | dangling extender extends through CONTINUE/STOP not bare notes | unit | `testDanglingExtenderEndsAtStartNoteWhenNoContinueFollows`, `testDanglingExtenderExtendsThroughContinueMarkers` | adequate | keep |
| LyricLayoutBuilder | trailing continuation flag + leading stub from x=0 | unit | `testTrailingContinuationAndLeadingStub` | adequate | keep |
| LyricLayoutBuilder | `emitDanglingHyphen` no eligible follower → LOG.error, no connector | unit | — | missing | open BEGIN at line end → no DANGLING_HYPHEN |
| LyricLayoutBuilder | DANGLING_HYPHEN emitted to next eligible element left edge | unit | — (renderer test uses hand-built record) | missing | builder coords for DANGLING_HYPHEN |
| LyricLayoutBuilder | `sourceElementIndex` on HYPHEN/EXTENDER/DANGLING_* | unit | never asserted | missing | ≥1 assertion per kind |
| LyricLayoutBuilder | multi-verse separate boxes/connectors by `verseIndex`; `verseCount` = max verse | unit | `testMultiVerseProducesSeparateBoxesPerVerse` | adequate | keep |
| LyricLayoutBuilder | verse-1 (`getSyllableWidthSs`) vs verse-≥2 (`lyricBoxWidthSs`) equal width for same text | unit | — | missing | catch divergence between cached and on-the-fly paths |
| LyricLayoutBuilder | compound-word boundary (BEGIN+compound) opens HYPHEN | unit | `testCompoundWordBoundaryProducesHyphen` | adequate | keep |
| LyricLayoutBuilder | lyric boxes appear after insertion | e2e | — | missing (low priority) | optional rendering smoke; geometry fully unit-coverable |

**3D notes (quality concerns):** Highest-risk defect: **`LyricRenderMetricsTest.testLyricBoxWidthSsMatchesLayoutBoxWidth` is a self-referential oracle** — verse-2 builder calls `lyricBoxWidthSs(text)` to populate `box.widthSs()`, then asserts `box.widthSs() ≈ lyricBoxWidthSs(text)`, i.e. `f(x) ≈ f(x)`; needs an independent oracle. Second: **HYPHEN/EXTENDER connector geometry** — `testDoReMi…` and `testExtenderSpansContinuationNotes` verify counts but never `startXSs`/`endXSs`, so an incorrectly anchored hyphen passes. Two untested branches carry real risk: **REST + STOP** (overshoot past rest right edge — distinct path from note-STOP) and **REST + CONTINUE** (distinct value in the compound condition from the tested START). **`emitDanglingHyphen`** has no builder-level test (happy path nor LOG.error path); the only DANGLING_HYPHEN test uses a hand-crafted record. `firstGraphemeClusterEndIndex` is tested only with ASCII so surrogate-pair/combining-mark regressions to naive `charAt(0)` would survive. Out-of-scope production observation: `LyricLayoutBuilder.java:68` comment says "Extends 0.25 ss past the column right edge" but `STOP_MELISMA_OVERSHOOT_SS = 0.5` — stale comment (the test comment repeats it; assertions correctly use the constant).

### 3E. ranges, endings, attachments, collision — `AttachmentLayout`, `CollisionDetector`, `Ending`, `LineEndingSupport`, `RangeLayout`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| AttachmentLayout | `getVerticalOrder()` switch maps Type→VerticalOrder | unit | — | missing | **dead code (zero refs)** — resolve by deletion, not test (see observations) |
| AttachmentLayout | `isAboveStaff`/`containsPoint` delegations | none | — | none | trivial delegation |
| AttachmentLayout | `getDataAs` null-safe cast | unit | — | missing | dead code; delete |
| CollisionDetector | `calculateNoteExtent` accumulates min/max Y over notes/attachments/articulations/ranges | unit | — | missing | **dead code (zero refs)** — resolve by deletion |
| CollisionDetector | `COLLISION_PADDING_SS` constant | none | — | none | numeric constant |
| Ending | `getLabel()` "1."/"2." | unit | — | missing | two-case test |
| Ending | `getContentHeightSs()` = `VOLTA_TICK_HEIGHT_SS` | none | `StructuralTierStackingTest` pins value indirectly | none | constant return |
| Ending | `getSpanWidthSs()` = `max(NOTE_HEAD_WIDTH_SS, endX-anchorX+NOTE_HEAD_WIDTH_SS)` | unit | — | missing | zero-span + positive span |
| Ending | `findRepeatSplitElement()` scans for REPEAT_RIGHT/REPEAT_LEFT_RIGHT | unit | indirect via invalidation tests | missing | direct: no-split / each split type / invalid indices |
| Ending | `computeBracketRanges()` start-adjust, split detection, two-bracket geometry | unit | — | missing | **high-risk**: no-split, split→two brackets, start-adjust from barline, end-extend, closing-stroke per end type |
| Ending | `computeCollisionRegions()` bar/tick(s)/label decomposition | unit | — | missing | region count (3 vs 4 by `hasClosingStroke`), x-offsets, label inset |
| Ending | `labelBoundsSs(int)` cached glyph bounds | none | — | none | static lookup |
| Ending | `isInvalidatedByDeletion()` split + all-content cases | unit | `EndingInvalidationTest.IsInvalidatedByDeletion` (6) | adequate | keep |
| Ending | `isInvalidatedByReplacement()` / `checkReplacement()` all outcomes | unit | `EndingInvalidationTest.IsInvalidatedByReplacement` (15), `CheckReplacement` | adequate | keep |
| Ending | `isInvalidatedByInsertion()` guards + interior/split logic | unit | `EndingInvalidationTest.IsInvalidatedByInsertion` (5) | inadequate | missing split-boundary exemption (`insertedIndex==splitIndex`→false) and `splitEl==null` interior branch |
| Ending | stacking above staff/hairpins | unit | `StructuralTierStackingTest.EndingStacking` (4) | adequate | keep (directional `isLessThan(0)` correct for the claim) |
| Ending | `setYPositionSs`/`getYPositionSs` applied in stacking | unit | `ManualOffsetStackingTest.EndingOffsets.testEndingYPositionApplied` | adequate | keep |
| Ending | base `isInvalidatedBy` anchor/end deleted | unit | `RangeElementInvalidationTest` (parametrized incl. Ending) | adequate | keep |
| Ending | Line-mutation wiring removes invalidated Ending | unit | `LineMutationTest.EndingInvalidationConditions` (10+) | adequate | keep |
| Ending | confirmation UI wiring (abort/proceed/dual change) | unit (integration) | `EndingConfirmsTest` (9, mocked dialogs) | adequate | keep |
| LineEndingSupport | `findEndings()` extracts Ending range elements | unit | indirect only | missing | 0/1/2 endings, verify content |
| LineEndingSupport | `findEndingAt(List,int)` span inclusion [start,end] | unit | — | missing | before/at-start/inside/at-end/after/empty |
| LineEndingSupport | `findEndingAt(Line,int)` overload | none | — | none | trivial delegation |
| LineEndingSupport | `isInsideAnyEnding` null-safe | unit | — | missing | positive + negative |
| LineEndingSupport | `isStartOfAnyEnding` anchor equality | unit | — | missing | start / inside-not-start / empty |
| LineEndingSupport | `isEndOfAnyEnding` end equality | unit | — | missing | end / inside-not-end / empty |
| LineEndingSupport | `findEndingReplacementEffect()` first non-None effect | unit | `EndingConfirmsTest` via `SelectionCoordinator.applyActionToSelection` | inadequate | indirect only (reclassified from wrong-level); add direct 0/1/2-affected test |
| RangeLayout | `getVerticalOrder()` ENDINGS / RANGE_ABOVE / RANGE_BELOW | unit | — | missing | **dead code (zero refs)** — resolve by deletion |
| RangeLayout | `getElementCount()` = end-start+1 | unit | — | missing | dead code |
| RangeLayout | `containsElement(int)` range-inclusive | unit | — | missing | dead code |
| RangeLayout | `containsPoint`/`getDataAs` | none/unit | — | none/missing | dead code |

**3E notes (quality concerns):** The most significant in-scope gap is **`Ending.computeBracketRanges()`** — the most complex method here (start-leftward-adjust, no-split single bracket, split→two brackets, per-end-type closing-stroke) — with zero direct coverage; bugs produce wrong visual geometry, not crashes. Its companion `computeCollisionRegions()` (3 vs 4 sub-regions) is also untested. `isInvalidatedByInsertion` has two survivable-mutant spots: the split-boundary exemption and the `splitEl==null` interior branch. **`LineEndingSupport`** is used by 8 production subsystems (MIDI, ABC export, IO, rendering, selection, vertical adjustment) but has no unit tests; its `findEndingAt` boundary comparators (`>=`/`<=`) are exactly where off-by-one hides. Out-of-scope production observation (**verified**): `AttachmentLayout`, `CollisionDetector`, `RangeLayout` have **zero references anywhere in `src/main` or `src/test`** (confirmed by grep + Serena) — dead scaffolding superseded by `LayoutResult.DecorationLayout`; resolve by deletion in remediation rather than writing the "missing" tests. Redundant: `StructuralTierStackingTest.EndingStacking.testEndingRangeElementProducesDecorationLayout` duplicates `testEndingPositionedAboveStaff`.

### 3F. stacking subsystem — `NoteAttachedStacker`, `StackingContext`, `StackingUtils`, `StructuralStacker`, `SystemStacker`, `VerticalStackingCalculator`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StackingUtils | `anchorCeilingSs(int)` within/below staff → top staff line | unit | — | missing | assert `STAFF_TOP_Y_SS` for sp > TOP_STAFF_LINE |
| StackingUtils | `anchorCeilingSs(int)` at/above top line → above notehead | unit | — | missing | assert `sp*OFFSET - NOTE_HEAD_RADIUS_SS` for sp ≤ -4 |
| StackingUtils | `stackAbove` collision-aware placement (query-expand-min-reserve) | unit | `ArticulationStackingTest` (integration) | adequate | keep |
| StackingUtils | `stackAboveWithRegions` multi-region min-ceiling + per-region reserve | unit | `SystemTierStackingTest` (`ySs<0` only) | inadequate | exact-value via controlled extents; `<0` can't catch region/reservation bug |
| StackingUtils | `isRangeCovered(start,end)` | unit | — | missing | covered / uncovered / wrong-end |
| StackingUtils | symmetric horizontal margin (`STRUCTURAL_HORIZONTAL_MARGIN_SS`) on query+reserve | unit | margin never checked | missing | assert margin applied to queryX/queryWidth |
| StackingContext | `buildColumnMap` element→column | unit | — | missing | 2 columns → map per element |
| StackingContext | `updateLowestNoteBotSs` max-accumulation | unit | — | missing | ascending then descending → max kept |
| StackingContext | `updateBotContentExtentSs` max-accumulation | unit | — | missing | same pattern |
| StackingContext | `notesWithUpwardTie` default empty / setter replaces | unit | — | missing (low priority) | drives downstream margin branch |
| NoteAttachedStacker | `computeNoteBounds` stem-path vs type-geometry path | unit | indirect (`ArticulationStackingTest`, `<0` only) | inadequate | both paths; exact top/bot Ss |
| NoteAttachedStacker | `seedNoteBounds` updates `lowestNoteBotSs`/`botContentExtentSs` | unit | — | missing | assert context fields after seeding |
| NoteAttachedStacker | `seedTieBounds` upward arc → above; membership in `notesWithUpwardTie` only when protruding | unit | — | missing | controlled TieLayout; set membership + extents |
| NoteAttachedStacker | `seedTieBounds` downward arc → below; `botContentExtentSs` updated | unit | — | missing | stem-up tie |
| NoteAttachedStacker | `evaluateBezierYSs` cubic at t=0/0.5/1 | unit | — | missing | hand-computed control points |
| NoteAttachedStacker | reduced `TIE_DECORATION_MARGIN_SS` for upward-tie notes | unit | — | missing | articulation Y delta == margin delta |
| NoteAttachedStacker | `stackArticulations` precomposed staccato+accent; single glyphs; collision stacking | unit | `ArticulationStackingTest.PrecomposedGlyph`, `CollisionDetection.testAboveStaffArticulationsReserveSpaceInExtents` | adequate | keep |
| NoteAttachedStacker | `stackFermata` above articulations (ordering) | unit | `FermataTrillStackingTest.testFermataPositionedAboveArticulations` | adequate | keep |
| NoteAttachedStacker | `stackFermata` exact Y = `ceiling - margin - height` | unit | `FermataTrillStackingTest` (`ySs<0`) | inadequate | exact-value from controlled extents |
| NoteAttachedStacker | `stackSingleTrill` single-note → `endXSs=anchorXSs` | unit | `FermataTrillStackingTest.testSingleNoteTrillPositionedAboveNote` (`ySs<0`, no width) | inadequate | add exact single-note width assertion |
| NoteAttachedStacker | `stackSingleTrill` multi-note → spans anchor→end | unit | `FermataTrillStackingTest.testMultiNoteTrillReservesFullSpan` | adequate | keep |
| NoteAttachedStacker | `computePreviewDecorationLayouts` (static preview path) | unit | — | missing | fermata+staccato preview all above-staff |
| StructuralStacker | `stackSpanElement` null anchor/end → skipped | unit | — | missing | null anchor → no layout |
| StructuralStacker | `stackHairpins` crescendo/diminuendo above note-attached | unit | `StructuralTierStackingTest.HairpinStacking.*` (`ySs<0`) | inadequate | exact-value; `<0` passes at y=-0.001; consolidate redundant `…ProducesDecorationLayout` |
| StructuralStacker | `stackTuplets` above note-attached | unit | `StructuralTierStackingTest.TupletStacking.testTupletRangeElementPositionedAboveStaff` (`ySs<0`) | inadequate | exact-value |
| StructuralStacker | `stackTextDynamics` X centering = `noteheadCenterX - contentWidth/2` | unit | `StructuralTierStackingTest` (`ySs<0`, no X) | missing | assert centered `xSs` |
| StructuralStacker | `stackEndings` above hairpins (ordering); `heightSs`=`VOLTA_TICK_HEIGHT_SS` | unit | `StructuralTierStackingTest.EndingStacking.testEndingPositionedAboveHairpins`, `testEndingHasPositiveDimensions` | adequate | keep |
| StructuralStacker | `testNonOverlappingHairpinsAtSameHeight` | unit | `StructuralTierStackingTest` (only `ySs<0` each, never compared) | inadequate | **name-mismatch**: add `isCloseTo` equality or rename |
| SystemStacker | `stackAnnotations` X shifts with `xAlignment` (0/0.5/1) | unit | `SystemTierStackingTest` (`ySs<0`, no X) | missing | left/center/right → distinct formula-driven X |
| SystemStacker | `stackMetronomeAttachment` (tempo/beat-change) regions placement | unit | `SystemTierStackingTest` (`ySs<0`, dims `>0`, cross-tier `isLessThan`) | inadequate | exact-value for ≥1 region case (cross-tier ordering adequate) |
| SystemStacker | `testTempoAttachmentProducesLayout` | unit | `SystemTierStackingTest` (`isNotNull` only) | inadequate | fixture-only; merge with positioned test or add position/dim |
| VerticalStackingCalculator | `seedAccidentalsIntoStructural` high-note top reservation | unit | `VerticalStackingCalculatorTest.testSeedAccidentalsTranslatesToStaffCoordinatesForHighNote` (exact) | adequate | keep |
| VerticalStackingCalculator | accidental bottom reservation | unit | `…testSeedAccidentalsReservesSpaceAtAccidentalXForSharp` (`>=`) | inadequate | `>=` allows any value; change to exact `isCloseTo(botSs+centerYSs)` |
| VerticalStackingCalculator | grace note skipped | unit | `…testSeedAccidentalsIgnoresGraceNotes` (exact 0.0) | adequate | keep |
| VerticalStackingCalculator | `applyDecorationOffsets` Tuplet `getVerticalPositionSs` | unit | `ManualOffsetStackingTest` covers Trill/Ending/Hairpin/TempoChange/Fermata/Annotation; **Tuplet absent** | missing | add `TupletOffsets` test |
| VerticalStackingCalculator | `calculate` tier copy propagation (`copyTopFrom`) | unit | `StaffExtentsTest.CopyTopFrom` (primitive) + integration | adequate | keep |
| VerticalStackingCalculator | `calculate` `aboveStaffSs` = `max(MIN_ABOVE_STAFF_SS, -topExtent - STAFF_HALF_SS)` | unit | `LineHeightTest` (`>=`) | inadequate | exact-value with known decoration |
| VerticalStackingCalculator | `calculate` `belowStaffSs` max across 4 terms | unit | `LineHeightTest` (`>=`) | inadequate | pin exact `lineHeightSs` for below-staff note |
| VerticalStackingCalculator | `calculate` `belowContentSs` distinct, uses `botContentExtentSs` | unit | — | missing | downward-stem note → non-zero belowContent |
| VerticalStackingCalculator | `calculate` empty line → MIN above/below | unit | `LineHeightTest.testEmptyLineZeroReturnsMinimumHeight` (exact) | adequate | keep |

**3F notes (quality concerns):** **Systemic weak-assertion pattern (highest risk).** Most behaviors in `NoteAttachedStacker`/`StructuralStacker`/`SystemStacker` are covered only by `isLessThan(0.0)`/`isGreaterThan(0.0)` — they pass for any negative/positive value and cannot catch sign errors in the ceiling formula, wrong margin application, bad extents import, or off-by-a-constant bugs; a `-marginSs`→`+marginSs` mutation in `stackAbove` would survive every such test. The pattern pervades `FermataTrillStackingTest`, `StructuralTierStackingTest`, `SystemTierStackingTest`. **Name-mismatch:** `testNonOverlappingHairpinsAtSameHeight` asserts nothing about equal height (only `ySs<0` each). Weak disjunction: `testFermataAndTrillDoNotOverlap` OR-asserts two booleans (low diagnostic value). Fixture-only: `testTempoAttachmentProducesLayout` asserts `isNotNull` only. Redundant: crescendo/diminuendo/fermata `…ProducesDecorationLayout` duplicate the positioned tests. **Entirely uncovered:** `StackingUtils.anchorCeilingSs` (both branches), `isRangeCovered`, `NoteAttachedStacker.evaluateBezierYSs` (pure math), the tie-seeding paths + reduced-margin branch, `VerticalStackingCalculator.belowContentSs`, the Tuplet manual offset, `SystemStacker.stackAnnotations` X-alignment arithmetic, `computePreviewDecorationLayouts`. `VerticalStackingCalculatorTest` is the model to follow — it uses exact `isEqualTo` (except the one `>=` bottom-reservation assertion). `StaffExtents` (out of 3F scope) is in sound shape with exact assertions.

### layout — production observations (out of test-audit scope)

Filed as a single tracked GitHub issue ([#408](https://github.com/vasudeva-server/SongScribe/issues/408)) — these are real code observations, not test gaps, so the disposable matrix isn't their only home.

1. **⚠️ Dead code — `AttachmentLayout`, `CollisionDetector`, `RangeLayout`.** Verified zero references anywhere in `src/main` or `src/test` (grep for the bareword + Serena reference search). Appears to be scaffolding from an earlier layout architecture superseded by `LayoutResult.DecorationLayout`. Resolve by deletion in remediation rather than writing the ~12 "missing" tests their behaviors would otherwise warrant.
2. **`ElementBoundsSs.formatCssSpacing` — wrong unit suffixes (confirmed).** The multi-value branches emit `t + "px " + r + "ss"` (and 3-/4-value analogues), so all but the last token are labelled `px` even though the values are staff-spaces and the method's own javadoc shows all-`ss` output (`"4ss 8ss"`). Cosmetic (these CSS strings are inspection/debug output) but incorrect.
3. **`LyricLayoutBuilder` — stale comment.** Line 68: `// Extends 0.25 ss past the column right edge` while `STOP_MELISMA_OVERSHOOT_SS = 0.5`. The `{@value}` javadoc at lines 44/52 is correct; only the inline comment (and the echoing test comment) is stale.
4. **`HorizontalSpacingCalculator.needsAccidentalPush` — unused parameters / misleading contract.** Ignores `prevColumn` and `currXSs` and returns true whenever the current element has any accidental; the real clearance check lives in the caller. The signature implies a pre-check it doesn't perform. Likely intentional but a code smell — review.

### layout — summary

Audited all 37 production classes (excl. 2 `package-info`). Dominant patterns to drive remediation:

1. **Pure geometry/conversion/stacking math is the biggest blind spot — and it is the riskiest math in the app.** `LayoutEngine`'s beam/stem/tie engines, `ElementBoundsSs`' box model, `NoteGeometry`'s accidental widths, `StaffExtents.spToSs`/`ssToSp`, `StackingUtils.anchorCeilingSs`, `NoteAttachedStacker.evaluateBezierYSs`, `LineJustificationCalculator`'s compression math, and `LayoutResult`'s hit-testing family are exercised only as collaborators (or not at all) and asserted directly almost nowhere. These are cheap, high-value unit tests.
2. **"Weak-but-green" assertions are pervasive and systemic** — far more than in `dom`/`io`. The entire stacking-test family asserts `ySs<0`/`>0`; `LineHeightTest` and several builder/metrics tests assert `>=`/`>`/`isNotEqualTo`/`isLessThan` where exact values are statically computable; `isNotNull`/fixture-only tests stand in for behavioral assertions. A position/sign/constant mutation survives most of them.
3. **Self-referential oracles** — `HorizontalSpacingCalculatorTest` (entirely tautological), `PageModelTest` (contentArea + defaultLineWidthSs), `SongLayoutMetricsTest.verseBaselineY`, `LyricRenderMetricsTest.lyricBoxWidth`. Each compares production output to the same formula and cannot fail.
4. **Untested complex logic / branch & error paths** — `LineJustificationCalculator` (zero tests), `Ending.computeBracketRanges`/`computeCollisionRegions`, `LyricLayoutBuilder` dangling-hyphen + REST-extend (STOP/CONTINUE) branches, `isInvalidatedByInsertion` split-boundary, `LineEndingSupport` (8 production callers, no unit tests), `LayoutResult` insertion/lookup family.
5. **Dead code surfaced** — `AttachmentLayout`, `CollisionDetector`, `RangeLayout` (delete, don't test). Plus three minor code observations (CSS suffixes, stale comment, unused params).
6. **Misfiled-but-relevant:** many `dom`-class tests live under `layout/` (`TieTest`, `TupletTest`, `KeySignatureTest`, `RangeElementInvalidationTest`, `AnnotationAttachmentTest`, etc., already audited in Session 1) — relocate during the rewrite, not re-test.
