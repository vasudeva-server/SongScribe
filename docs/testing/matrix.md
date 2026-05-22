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
| 4 | `midi` + `converter` + `util` + `smufl` + `prefs` + `font` + `export` + `uiconverter` | ~58 | done |
| 5 | `ui/action` | 62 | done |
| 6 | `ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard` | 15 | done |
| 7 | `ui/component` | 65 | done |
| 8 | `message` (mutation/command/notification + core) | 86 | done |
| 9 | `ui/renderer` | 30 | done |
| 10 | `ui/dialog` | 53 | done |
| 11 | `ui/menu` + `ui/playback` + `ui/platform` + top-level `ui` | 31 | done |
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

- **`FermataAttachment(@Nullable StaffElement)`** calls `setOwnerElement(parent)` twice (lines 59 and 63): once unconditionally, then again inside the `if (parent != null)` block. The second call is a redundant no-op (idempotent for the same owner) — harmless, not behavior-affecting, so no regression test is warranted. **RESOLVED 2026-05-22:** redundant call removed.

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

## 4. `midi` + `converter` + `util` + `smufl` + `prefs` + `font` + `export` + `uiconverter` (audited 2026-05-21)

Audited in two waves of parallel production-first sub-audits (Wave 1: midi, converter, smufl; Wave 2: util, prefs, font, export, uiconverter). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

### 4A. `midi`

Audited production-code-first: read every method body in all 7 classes via serena `jet_brains_find_symbol` with `include_body=true`, enumerated testable behaviors, then read all 3 existing test files in `src/test/java/songscribe/midi/` and confirmed zero coverage of `midi` classes in `src/test/java/songscribe/e2e/`.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| GlissandoMidiHelper | `resolveTargetPitch` — CONNECTED returns nextNotePitch; SLIDE_OUT returns sourcePitch − 4 | unit | `GlissandoMidiHelperTest.ResolveTargetPitch` (3 methods: testConnectedReturnsNextNotePitch, testSlideOutReturnsSourceMinusFour, testSlideOutIgnoresNextNotePitch) | adequate | none |
| GlissandoMidiHelper | `calculateSensitivity` — abs(target−source), floor at 1 | unit | `GlissandoMidiHelperTest.CalculateSensitivity` (4 methods) | adequate | none |
| GlissandoMidiHelper | `calculateBendValue` — linear (CONNECTED_CURVE_EXPONENT=1.0) and quadratic (SLIDE_OUT_CURVE_EXPONENT=2.0) interpolation; clamp to [0, 16383] | unit | `GlissandoMidiHelperTest.CalculateBendValue` (7 methods) | adequate | none |
| GlissandoMidiHelper | `calculateSlideTicks` / `calculateSustainTicks` — correct tick splits from SLIDE_RATIO/SUSTAIN_RATIO | unit | `GlissandoMidiHelperTest.CalculateSlideTicks` + `.CalculateSustainTicks` (3+3 methods) | adequate | none |
| GlissandoMidiHelper | `createRpnMessages` — emits CC 101, 100, 6 (semitones), 38=0 at correct tick on correct channel | unit | `GlissandoMidiHelperTest.CreateRpnMessages` (4 methods) | adequate | none |
| GlissandoMidiHelper | `createRpnMessagesIfNeeded` — de-dups by currentSensitivity; re-emits after `resetSensitivity()` | unit | `GlissandoMidiHelperTest.CreateRpnMessagesIfNeeded` (4 methods) | adequate | none |
| GlissandoMidiHelper | `createPitchBendMessages` — correct event count, tick positions, zero-duration guard | unit | `GlissandoMidiHelperTest.CreatePitchBendMessages` (6 methods) | inadequate — `testProgressivelyIncreasingBend` asserts only `>=` monotonicity rather than exact bend values at specific ticks; a mutation flipping `sourcePitch`/`targetPitch` or swapping the curve exponent will not be caught by monotonicity alone. Specific values tested in `CalculateBendValue` do not cover the full event-sequence output. | add exact bend-value assertions at key ticks (t=0, t=0.5, t=1.0) for both up and down slides |
| GlissandoMidiHelper | `createPitchBendReset` — emits single pitch bend at PITCH_BEND_CENTER | unit | `GlissandoMidiHelperTest.CreatePitchBendReset` (2 methods) | adequate | none |
| GlissandoMidiHelper | `createPendingResets` — conditional emission based on needsPitchBendReset / needsExpressionReset flags; clears flags after emit | unit | none found | missing | add unit test: set flags via `setNeedsPitchBendReset`/`setNeedsExpressionReset`, call `createPendingResets`, assert CC/bend events emitted and second call produces nothing |
| GlissandoMidiHelper | `calculateSlideInBendValue` — grace-note curve: starts at full offset, eases to center; clamp | unit | none found | missing | add unit test for t=0.0 (full offset), t=1.0 (center), t=0.5 (half-curved) |
| GlissandoMidiHelper | `createSlideInPitchBendMessages` — zero-duration guard; event count and tick range; t=0 at full grace offset | unit | none found (integration test checks `isNotEmpty()` only) | inadequate — `GlissandoMidiIntegrationTest.GraceHostPair.testNoteOnCountMatchesNonGracePitchedNotes` only asserts `isNotEmpty()` on bend events and correct NOTE_ON count; does not verify event tick positions, bend values, or expression CC values | add unit test with a small fixed slide verifying event count, first/last bend values, and expression CC ramp |
| GlissandoMidiHelper | `createSlideInExpressionMessages` — ramp from GRACE_SLIDE_IN_START_RATIO×127 to 127; zero-duration guard | unit | none found | missing | add unit test: verify event count, first event CC11 value ≈ 0.25×127, last event = 127 |
| GlissandoMidiHelper | `createSlideOutExpressionMessages` — fade from 127 to 0 along quadratic curve; zero-duration guard | unit | none found | missing | add unit test: verify event count, first event CC11 = 127, last event = 0 |
| GlissandoMidiHelper | `createExpressionReset` — emits single CC11=127 | unit | none found | missing | add unit test |
| GlissandoMidiHelper | `setPendingGracePitch` / `hasPendingGracePitch` / `consumePendingGracePitch` — state machine: set, detect, consume clears to -1 | unit | none found (used transitively in integration test but never tested directly) | missing | add unit test for all three: set, assert has=true, consume returns correct value and clears |
| GlissandoMidiHelper | `resetSensitivity` — resets currentSensitivity to -1 so next `createRpnMessagesIfNeeded` always emits | unit | `GlissandoMidiHelperTest.CreateRpnMessagesIfNeeded.testResetCausesReEmit` | adequate | none |
| LineTrackBuilder | `getElementDurationWithTuplet` — returns element duration × tuplet factor; non-tuplet path returns raw duration | unit | none found | missing | add unit test: non-tuplet element, and a simple 3-in-2 tuplet span |
| LineTrackBuilder | `getTupletFactor` — complex branch: tupletDuration ≥ 1 (floor, minus-1 edge), tupletDuration < 1 (log2 power-of-2 floor); non-tuplet returns 1.0 | unit | none found | missing — this is the highest-risk dark gap in the package: floating-point rounding, three branches, and a labelled-loop back-reference; any mutation in the log2 or floor paths will survive undetected | add parameterized unit tests covering: no tuplet, 3-in-2 (tupletDuration < 1), triplet spanning one beat (tupletDuration = 1), quintuplet (tupletDuration > 1), edge where newDuration == tupletDuration > 1 (the newDuration-- branch) |
| LineTrackBuilder | `calculateSoundingDuration` / `calculateSoundingPercent` — override vs. settings.noteDurationPercent(); staccato path | unit | none found | missing | add unit test: no override uses settings percent; override present ignores settings |
| LineTrackBuilder | `noteVelocity` — VelocityMap present returns map value; null map uses ACCENT/non-accent fallback | unit | none found | missing | add unit test for both paths |
| LineTrackBuilder | `addNoteMessages` — grace note stores pitch (no NOTE_ON); note with tie anchor/end logic; rest advances ticks; glissando vs. normal note-off dispatch | unit | partially covered by `GlissandoMidiIntegrationTest.GraceHostPair` (grace+host NOTE_ON count) | inadequate — integration test verifies NOTE_ON count and `isNotEmpty()` bend; does not test tie span logic, rest tick advance, or the fallback when connected glissando's next element is non-pitched | add unit tests per dispatch branch |
| LineTrackBuilder | `addGlissandoMessages` — CONNECTED: full duration, next-note pitch, noteOff at duration−1; SLIDE_OUT: sounding duration, staccato, expression fade; fallbacks when next element missing or non-pitched | unit | `GlissandoMidiIntegrationTest.ConnectedGlissando` + `.SlideOut` | inadequate — both nested classes call `buildMidiTrack(line, tempo)` on the same fixture line; `ConnectedGlissando` asserts RPN sequence prefix `hasSizeGreaterThanOrEqualTo(4)` (weak lower bound) and `SlideOut.testPitchBendEventsPresent` asserts only `isNotEmpty()`; neither verifies noteOff tick, bend direction, bend values, or expression CC events; the `SlideOut.testRpnSensitivityIncludesSlideOutSemitones` check is the only quantitative assertion | add unit tests with constructed lines verifying: tick of noteOff for CONNECTED (duration−1), fallback to normal note-off when next element is rest, expression CC events for SLIDE_OUT, staccato shortening |
| LineTrackBuilder | `addGraceGlissandoSlideIn` — reduced velocity (×0.85), pitch bend ramp from grace pitch, expression ramp, reset at slide end | unit | `GlissandoMidiIntegrationTest.GraceHostPair.testNoteOnCountMatchesNonGracePitchedNotes` | inadequate — asserts NOTE_ON count and `isNotEmpty()` on bend events; does not verify reduced velocity, expression CC ramp from CC11=≈32 to 127, or pitch bend reset at end of slide | add unit test verifying NOTE_ON velocity ≈ 0.85×default, bend reset CC at slideStartTick+GRACE_SLIDE_TICKS, expression reset CC at same tick |
| LineTrackBuilder | `addToTrack` overloads — tempo change attachment triggers `MidiEventFactory.addTempoEvent`; colorize meta message emitted per element; glissandoHelper state flushed at end of overload[3]; overload[4] leaves flush to caller | unit | none found | missing | add unit tests: tempo-change element causes SET_TEMPO meta event; range start/end boundaries respected; overload[3] flushes pending resets; overload[4] does not |
| MidiEventFactory | `addTempoEvent(Track, int, Tempo, int)` — BPM×percent/100 scaling then encodes as 3-byte big-endian microseconds-per-beat | unit | none found | missing — pure arithmetic on a critical playback path; a mutation swapping `>>16`/`>>8`/`>>0` byte order will produce wrong tempo silently | add unit test: verify SET_TEMPO meta message bytes for known BPM (e.g. 120 BPM → 500000 μs/beat → bytes [0x07, 0xA1, 0x20]); verify tempoChangePercent scaling (100% = unchanged, 200% = double speed) |
| MidiEventFactory | `addTempoEvent(Track, int, int)` — MICROSECONDS_PER_MINUTE / BPM → 3-byte encoding | unit | none found | missing | same test class as above; cover direct-BPM variant |
| MidiSequenceBuilder | `buildFullSequence` — delegates to `buildSequence(0, 0, −1, −1, effectiveTempo)` | unit | none found | missing |add unit test with a simple song: assert non-null Sequence, at least one MIDI track, PPQ=96, program-change event at tick 0 |
| MidiSequenceBuilder | `buildFromNoteToEnd` — picks correct startTempo via `song.getTempoAt()` | unit | none found | missing | add unit test: song with mid-line tempo change, start from that note, assert SET_TEMPO meta at tick 0 matches that tempo |
| MidiSequenceBuilder | `buildSequence` — linear path (no repeats / hard end boundary): bank select + program change at tick 0; initial tempo meta; velocity map pre-computation; line range slicing; END_OF_TRACK at final tick | unit | none found | missing — the full integration of bank select, program change, velocity map, and END_OF_TRACK placement is untested; wrong END_OF_TRACK tick would shorten audio silently | add unit tests for each setup event (bank select CC0/32, program change) and END_OF_TRACK tick |
| MidiSequenceBuilder | `buildSequenceWithRepeats` — repeat-right jumps back to repeat-left (or song start); repeating flag skips first ending on second pass; glissandoHelper state survives across note-by-note calls; final flush of pending resets | unit | none found | missing — highest risk in MidiSequenceBuilder; the repeat search backward loop with labelled break is hard to read and easy to mutate; first-ending skip logic has two separate branches | add unit tests: simple repeat (A–A'), repeat with first/second endings, repeat with no explicit start (returns to beginning), grace-note glissando spanning a repeat boundary |
| MidiSequenceBuilder | `addProgramChange` — emits PROGRAM_CHANGE on channel 0 at tick 0 | unit | none found | missing | covered by buildSequence unit test above |
| MidiSequenceBuilder | `addBankSelect` — emits CC 0 (MSB) and CC 32 (LSB) at tick 0 | unit | none found | missing | covered by buildSequence unit test above |
| VelocityMap | `build` — no dynamic: DEFAULT_VELOCITY_FRACTION; dynamic marking overrides; forward propagation within line; cross-line propagation; accent boost; accent boost capped at MAX_VELOCITY | unit | `VelocityMapTest` (8 methods across 4 nested classes) | adequate | none |
| VelocityMap | `getVelocity` — simple array lookup; no bounds guard | unit | exercised by every VelocityMapTest method | adequate (covered transitively) | none |
| VelocityMap | `build` with custom masterVelocity < MAX_VELOCITY — scales all velocities proportionally | unit | none found | missing — all tests call `VelocityMap.build(song, VelocityMap.MAX_VELOCITY)`; the `masterVelocity` parameter is never varied | add test with masterVelocity=64 to confirm velocities scale from ceiling |
| PlaybackSettings | pure data record — no logic | none | n/a | adequate | none |
| TrackPosition | pure data record — no logic | none | n/a | adequate | none |
| GlissandoMidiIntegrationTest `.testNoPitchBendWithoutGlissando` | asserts fixture model property (`getGlissando() == null`), not MIDI output | unit (misclassified) | exists | inadequate — name says "no pitch bend" but the test never builds a MIDI track; it only reads the fixture model; a mutation in the MIDI generation path would leave this test green | either delete (fixture integrity is not a MIDI generation concern) or rewrite to build the track and assert zero pitch bend events |

**4A notes (quality concerns):**

The highest-risk dark gap is `LineTrackBuilder.getTupletFactor`. It contains three branches (no tuplet, tupletDuration < 1, tupletDuration ≥ 1 with a secondary edge case for exact integer durations > 1), floating-point log2 arithmetic, and is the only thing controlling note timing for all tuplet playback. It has zero tests. A mutation that, for example, swaps `Math.floor` for `Math.ceil` in the tupletDuration ≥ 1 path would shift all tuplet durations without any test failing.

The second highest-risk gap is `MidiSequenceBuilder.buildSequenceWithRepeats`. The repeat logic contains a backward linear search with a labelled loop break, a `repeating` flag state machine, and two separate first/second-ending branches. None of this logic is tested. A mutation that inverts the `repeating` check would cause notes to be doubled instead of played once through a repeat, and it would survive the entire test suite.

`MidiEventFactory.addTempoEvent` is untested despite being the only path that converts BPM to the 3-byte MIDI SET_TEMPO message. The byte-order encoding (`>> 16`, `>> 8`, bare cast) is the kind of logic where a mutation (e.g. swapping `>> 16` and `>> 8`) produces a wrong but plausible-looking value that would make playback run at the wrong tempo without any assertion catching it.

Weak-but-green tests in `GlissandoMidiIntegrationTest`: the `ConnectedGlissando` nested class uses `hasSizeGreaterThanOrEqualTo(4)` for its CC event count check (passes even if the fixture produces 400 CC events), and both `ConnectedGlissando.testPitchBendEventsPresent` and `SlideOut.testPitchBendEventsPresent` assert only `isNotEmpty()` — neither can detect a wrong bend direction, wrong tick position, or wrong value. These tests provide false confidence; they pass for any non-zero output.

`GlissandoMidiIntegrationTest.testNoPitchBendWithoutGlissando` is a name-behavior mismatch: it asserts a model property (`getGlissando() == null`), not MIDI output. Renaming it `testFixtureElementHasNoGlissandoAnnotation` or deleting it in favour of a MIDI-output assertion would resolve the mismatch.

`VelocityMap.build` is well tested for all dynamic and accent permutations, but the `masterVelocity` scaling path (the parameter is always `MAX_VELOCITY` in every test) is a silent gap: if the multiplication or rounding formula were mutated, no test would catch it until someone uses a volume-slider-style feature.

### 4B. `converter`

Audited by reading every production class body symbol-by-symbol via serena `jet_brains_find_symbol` with `include_body=true`, then confirming zero test coverage by searching the entire test tree for references to any converter class, method, or annotation name; no `src/test/java/songscribe/converter/` directory exists.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ArgumentReader` | `parseArguments` — constructs target object via reflection, iterates fields for `@FileArgument` to set `fileType`/`fileField` | unit | none | missing | add unit test with a simple annotated POJO |
| `ArgumentReader` | `parseArguments` — named flag parsing: splits on `=`, looks up field by name, rejects unknown flag (prints + exits) | unit | none | missing | add unit test for valid flag, unknown flag branch |
| `ArgumentReader` | `parseArguments` — `-?` / `-help` flag triggers `infoBuilder()` + `System.exit(-1)` | unit | none | missing | add unit test with `SecurityManager` or exit-trap |
| `ArgumentReader` | `parseArguments` — stops scanning flags when first non-`-` arg encountered | unit | none | missing | add unit test: arg without leading dash breaks the flag loop |
| `ArgumentReader` | `parseArguments` — file collection: exists → added; not found → prints + exits; `SINGLE` stops after first file; empty list → prints + exits | unit | none | missing | add unit tests for each branch (use temp files or mock `File.exists`) |
| `ArgumentReader` | `parseArguments` — `NONE` file type: file-collection block skipped entirely | unit | none | missing | add unit test with a POJO carrying no `@FileArgument` field |
| `ArgumentReader` | `setField` — `int` field: valid string → `field.setInt`; null value → logs error, no throw | unit | none | missing | add unit test for int field with value, and with null |
| `ArgumentReader` | `setField` — `boolean` field: null value → `true`; `"true"`/`"false"` string → parsed; non-parseable still parsed by `Boolean.parseBoolean` (always non-throwing) | unit | none | missing | add unit tests for boolean with null, "true", "false" |
| `ArgumentReader` | `setField` — `String` (or other) field: value passed through via `field.set` | unit | none | missing | add unit test for String field |
| `ArgumentReader` | `setField` — `NumberFormatException` on bad int string → logs, does not throw, field stays default | unit | none | missing | add unit test: `-count=abc` on an int field |
| `ArgumentReader` | `findField` — known field name → returns `Field`; unknown → returns `null` | unit | none | missing | covered implicitly by `parseArguments` tests; no separate test needed |
| `ArgumentReader` | `getObj` — lazy: calls `parseArguments` once on first call, caches result | unit | none | missing | add unit test verifying `getObj()` returns same instance on two calls |
| `ArgumentReader` | `infoBuilder` — builds usage string: `SINGLE` → "file", `MANY` → "file1 [file2]…"; includes field names with descriptions; `@NoDefault` fields omit `(default=…)` | unit | none | missing | add unit test asserting on string content for each `FileType` variant |
| `Converter` | `applyExportExclusions` — `withoutLyrics=true` clears both lyrics fields; `withoutSongTitle=true` clears title; both false → no change | unit | none | missing | straightforward unit test with a mock/real `Song` |
| `Converter` | `loadSong` — delegates to `score.openFile` + `score.getSong()`; no branching | none | none | adequate (none) | no test warranted — pure delegation, risk is integration |
| `MidiConverter` | `convert` — instrument out of `[0,127]` → warn + return early | unit | none | missing | unit test: instrument=-1, instrument=128 |
| `MidiConverter` | `convert` — tempoChange out of `[1,200]` → warn + return early | unit | none | missing | unit test: tempoChange=0, tempoChange=201 |
| `MidiConverter` | `convert` — valid params → delegates to `PlaybackController` + `MidiSystem.write` (file I/O + MIDI stack) | none/e2e | none | adequate (none) | orchestration; risk is integration, not logic |
| `PDFConverter` | `convert` — `paperSize=null` → early return | unit | none | missing | unit test with null paperSize |
| `PDFConverter` | `convert` — paper size switch: `a4`, `letter`, `legal` → assigns named dimension constants; `default` → warn + return | unit | none | missing | unit tests for each case branch (assert resulting `paperWidth`/`paperHeight`) |
| `PDFConverter` | `convert` — `custom` paper size with valid `paperWidth`/`paperHeight` → proceeds | unit | none | missing | unit test |
| `PDFConverter` | `convert` — `custom` paper size with `paperWidth<=0` or `paperHeight<=0` → warn + return | unit | none | missing | unit test |
| `PDFConverter` | `convert` — `files.length==0` → log error + return | unit | none | missing | unit test |
| `PDFConverter` | `convert` — per-file loop: loads song, applies export exclusions, calls `ExportPDFAction.createPDF` (file I/O + render) | none | none | adequate (none) | orchestration |
| `PDFConverter` | margin override wiring (`applyMarginOverrides` delegation in `convert`) | none | none | adequate (none) | `PageLayoutData.applyMarginOverrides` is separately testable and is pure logic in `export` package |
| `SVGConverter` | `convert` — per-file loop: loads song, applies export exclusions, calls `score.createSVG` | none | none | adequate (none) | orchestration; no branching logic |
| `SVGConverter` | `main` — package-private (not `public static void main`) — unreachable as a JVM entry point | none | none | adequate (none) — but flag | note: `main` has package-private visibility (no `public`), so it cannot be launched as a JVM entry point; likely a bug or intentional limitation; zero in-repo callers confirmed |
| `AbcConverter` | `convert` — `file==null` → log error + return | unit | none | missing | unit test |
| `AbcConverter` | `convert` — non-null file → delegates to `SongLoader.load` + `ExportABCAction.writeABC` (file I/O) | none | none | adequate (none) | orchestration |
| `ImageConverter` | `convert` — stub body: only logs "not yet implemented" | none | none | adequate (none) — stub | no assertions possible; no production logic exists yet |
| `ArgumentDescribe` | annotation retention/value | none | none | adequate (none) | trivial annotation; framework behavior |
| `FileArgument` | annotation retention | none | none | adequate (none) | trivial marker annotation |
| `NoDefault` | annotation retention | none | none | adequate (none) | trivial marker annotation |

**4B notes (quality concerns):**

The converter package has **zero tests of any kind** — no `src/test/java/songscribe/converter/` directory exists and no cross-package test references any converter symbol. The highest-risk dark gap is `ArgumentReader`, which is the only class in the package with real logic: reflection-based argument parsing with eight distinct branches across `parseArguments` and `setField` covering flag parsing, file collection, type coercion, error handling, and `System.exit` paths. Every one of these branches is completely untested. The `int`-field `NumberFormatException` path and the boolean null-means-true coercion are the most mutation-invisible: they are easy to break silently because nothing observes the field value after the fact. `PDFConverter.convert` is the second-highest risk: it contains a five-branch `switch` on paper size (including a `custom` validation path with two sub-conditions), a `files.length==0` guard, and a null guard on `paperSize` — all missing. The paper-size switch is pure string-comparison logic with named constant assignments, exactly what unit tests are suited for. `Converter.applyExportExclusions` has trivial `if`-branches that set Song fields to empty strings; this is the simplest missing test in the package and its omission is surprising. `SVGConverter.main` being package-private (no `public` modifier) is a likely latent bug: it cannot be invoked as a standard JVM entry point, and no in-repo caller compensates for this.

### 4C. `util`

Audited by reading every production class symbol-by-symbol with serena `jet_brains_find_symbol` (bodies), then checking `src/test/java/songscribe/util/` for mirrored tests and searching cross-package tests for references; dead-code candidates verified with `jet_brains_find_referencing_symbols`.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StringUtils | `capitalizeSentence` — uppercases first char, lowercases rest; empty-string guard | unit | none | missing | add unit tests: empty, all-caps, already capitalized |
| StringUtils | `toKebabCase` — strips non-alphanumeric, collapses hyphens, lowercases; empty-string guard | unit | none | missing | add unit tests: spaces, accented chars, leading/trailing hyphens, empty |
| StringUtils | `stripDiacritics` — NFD normalize + remove combining marks | unit | none | missing | add unit tests: accented Latin, non-diacritic stays |
| StringUtils | `stripLinefeeds` — replaces `\n` with space | unit | none | missing | add trivial unit test |
| StringUtils | `trimEnd` — trims trailing whitespace | unit | none | missing | add unit tests: trailing space/tab/newline, no trailing whitespace |
| StringUtils | `collapseMultipleSpaces` — collapses internal runs of spaces (lookahead skips line start) | unit | none | missing | add unit tests: multiple spaces, leading spaces preserved, single space unchanged |
| StringUtils | `removeSyllabifyMarkings` — removes parenthesized groups and hyphens/underscores; **DEAD CODE** (zero callers in production) | unit | none | missing | verify dead, then delete or add caller; if retained, add tests |
| StringUtils | `wrapText` — word-wrap with min-word-count rebalancing; complex branching | unit | none | missing | add unit tests: single word wider than maxWidth, rebalancing triggered, empty input |
| FileUtils | `getExtension` — returns extension without dot; no-extension case returns `""`; uses `Paths.get` so path separators matter | unit | none | missing | add unit tests: plain filename, path with dir, no extension, dot-only filename |
| FileUtils | `getPathWithoutExtension` (String overload) — strips last dot and beyond; no-dot returns whole path | unit | none | missing | add unit tests: with extension, without extension, multiple dots |
| FileUtils | `getFilename` — returns filename component from path | unit | none | missing | add unit test |
| FileUtils | `getDirectory` — returns parent dir string; null parent returns `""` | unit | none | missing | add unit tests: with parent, no parent (bare filename) |
| FileUtils | `ensureExtension` — appends extension if not already present; multi-extension variant; uses case-insensitive match | unit | none | missing | add unit tests: already has ext (all variants), missing ext, dot-prefixed ext arg |
| FileUtils | `toDotExt` (private) — tested indirectly via `ensureExtension`; ext with and without leading dot | unit | none | missing | covered by `ensureExtension` tests |
| FileUtils | `getDocumentsDirectory` — platform-conditional path; Windows reads `USERPROFILE` env var | unit | none | missing | add unit test for non-Windows path; Windows branch is harder to isolate but non-Windows is trivially testable |
| FileUtils | `zipFile` — streams file into zip entry; `@Nullable` requestName branch | unit | none | missing | add unit test with a temp file; verify entry name for null vs non-null requestName |
| ExtensionFileFilter | constructor — description appends `(ext1, ext2)` suffix | unit | none | missing | add unit test |
| ExtensionFileFilter | `accept(File)` — directories always accepted; files matched by extension (case-insensitive) | unit | none | missing | add unit tests: directory, matching ext, non-matching ext, no extension |
| ExtensionFileFilter | `accept(File, String)` / `accept(String)` (private) — delegates to extension check | unit | none | missing | covered by `accept(File)` tests (file-name branch) |
| ExtensionFileFilter | `getExtension(int)`, `getExtensions()` — simple accessors | none | none | adequate | no test needed |
| ExtensionFileFilter | `getDescription()`, `toString()` — trivial accessors | none | none | adequate | no test needed |
| GraphicUtils | `Unit.create(boolean)` — maps `isMetric` boolean to `CM`/`INCH` | unit | none | missing | add unit test |
| GraphicUtils | `Unit.fromValue(int)` — maps int to enum; unknown value → `UNDETERMINED` | unit | none | missing | add unit tests: known values, unknown value |
| GraphicUtils | `Unit.description()` — `"inch"` / `"cm"` / `""` per variant | unit | none | missing | add unit tests for all three variants |
| GraphicUtils | `Unit.isMetric()` — `true` only for `CM` | unit | none | missing | add unit tests |
| GraphicUtils | `convertFromPixels` — pixel→inch or pixel→mm with rounding; branches on `isMetric()` | unit | none (used as helper in `PageModelTest` but not tested in isolation) | missing | add unit tests: inch rounding, mm rounding, metric vs non-metric branch |
| GraphicUtils | `convertToPixels` — inch/mm→pixel; metric divides by `CM_PER_INCH * 10` | unit | none (same as above) | missing | add unit tests for both branches |
| GraphicUtils | `clampToScreen(Rectangle)` — clamps size then position; multi-monitor path | unit | `GraphicUtilsClampTest` (6 rectangle tests + 2 point/dimension tests) | adequate | multi-monitor path (different screen contains point) not covered — consider adding |
| GraphicUtils | `clampToScreen(Point, Dimension)` — delegates to Rectangle overload | unit | `GraphicUtilsClampTest.ClampPointDimension` | adequate | — |
| GraphicUtils | `setRenderingHints` — pure rendering setup on `Graphics2D`; `isRetina` branch | none | none | adequate | rendering setup; no geometry to assert |
| GraphicUtils | `fillHorizontalLine` / `fillVerticalLine` — pure draw calls | none | none | adequate | no geometry to assert |
| GraphicUtils | `readImageResource` / `readImage` — I/O delegation | none | none | adequate | framework I/O; no logic to assert |
| GraphicUtils | `getTextBlockWidth` — iterates `\n`-split lines, measures each with `TextLayout`, returns max; requires `Graphics2D` | unit | none | missing | testable by passing a mock/stub `Graphics2D` with a fixed `FontRenderContext`; `empty → 0` branch is trivially testable |
| GraphicUtils | `glyphOutline` — delegates to `Font.createGlyphVector().getOutline()` | none | none | adequate | pure delegation |
| ModifierState | `isAltPressed` — platform dispatch (`isMac` → JNA call; `isWindows` → JNA call; else `false`) | none | none | adequate | logic is only a platform guard; JNA calls cannot be unit-tested without the native library |
| MyFontUtils | `parsePSName` — parses PostScript font name; `_`-split, `-`-split, and no-separator branches | unit | none | missing | add unit tests: `Family-Style`, `Family_Style`, style with hyphens, no separator |
| MyFontUtils | `parseStyle` (private) — OSF normalization, compound-style normalization, camel-case split, abbreviation expansion | unit | none | missing | exercisable through `getStyleDescription` or by widening; add representative unit tests |
| MyFontUtils | `getStyleDescription` — delegates to `parsePSName`/`parseStyle`; Damascus-style (style embedded in family) branch | unit | none | missing | add unit tests mocking a real `Font` or using registered test fonts |
| MyFontUtils | `getFullFontDescription` — `family + ' ' + style + ' ' + size + " pt"` | unit | none | missing | trivially testable once `getStyleDescription` is tested |
| MyFontUtils | `createFont` — by PS name with size; fallback on miss | unit | `MyFontUtilsTest.testCreateFontWithKnownPsNameReturnsCorrectSize` / `testCreateFontWithUnknownPsNameReturnsFallback` | inadequate | Both tests assert `font != null` and `font.getSize()` only; the unknown-name test asserts `getPSName() != bogus` (which is an inverse, not a positive contract). No test exercises font matching accuracy. |
| MyFontUtils | `getFontMetrics` — creates offscreen `BufferedImage`, returns `FontMetrics` | none | none | adequate | pure framework delegation; metrics correctness is tested where it is used |
| MyFontUtils | `getXHeight` — **DEAD CODE** (zero callers found by `jet_brains_find_referencing_symbols`) | unit | none | missing | verify dead, then delete |
| Utils | `arrayIndexOf` — linear search on `Object[]`, returns `-1` on miss | unit | none | missing | add unit tests: found, not found, null element |
| Utils | `lineCount` — empty → 0; trims then splits on `\n` | unit | none | missing | add unit tests: empty, single line, multi-line, whitespace-only |
| Utils | `roundToTwoDecimalPlaces` — `Math.round(v * 100) / 100.0` | unit | none | missing | add unit tests: 0.005 boundary, negative value, already-rounded value |
| Utils | `getPlatformKeyStrokeString` — platform-conditional modifier symbols + key-code branches | unit | none | missing | add unit tests: Mac vs non-Mac modifiers, special key codes (ENTER, BACKSPACE, etc.) |
| Utils | `getResourcePath` — strips leading `/`, looks up via classloader, falls back to classpath root | unit | none | missing | testable in unit context; add tests: with leading `/`, without, non-existent resource throws |
| Utils | `withDesktop` / `openWebPage` / `openEmail` — orchestration around `DesktopUtils`; UI error dialog on failure | none | none | adequate | error-dialog path is framework wiring; no pure logic to assert |
| Utils | `sleep` — wraps `Thread.sleep`, swallows `InterruptedException` | none | none | adequate | trivial wrapper; no logic |
| Utils | `getCurrentYear` — delegates to `Calendar` | none | none | adequate | trivial; would be flaky |
| UIUtils | `makeTooltipWithKeystroke` — appends `" (keystroke)"` or returns name unchanged when null | unit | none | missing | add unit tests: null accelerator, non-null accelerator |
| UIUtils | `positionDialog` — positions dialog at 3/8 down parent, centered, clamped; `@Nullable` parent | unit | `BaseDialogPositionTest` verifies `positionDialog` is *called* (mocked out), but the placement arithmetic itself is never asserted | inadequate | `BaseDialogPositionTest` mocks `UIUtils` entirely — no test verifies the `x`/`y` computation; add a focused unit test for the placement math |
| UIUtils | `getTaggedString` — parses `@`/`#` prefix + optional `/baselineShift` suffix | unit | none | missing | add unit tests: `@icon`, `#music`, no prefix, with baseline shift, no prefix with slash |
| UIUtils | `padComponent` overloads — pure Swing wiring | none | none | adequate | no logic to assert |
| UIUtils | `setCanGrow` / `setFlexibleWidth` / `setCanShrink` — set min/max sizes | none | none | adequate | pure component sizing setup |
| UIUtils | `beep`, `initToolbarButton`, `addStandardDialogKeyBindings`, `bindKey`, `preWarmDialogPeer`, `initLaf`, `readComboValuesFromFile` | none | none | adequate | framework wiring / side-effects; not unit-testable meaningfully |
| UIUtils | `getApplicationFrame`, `getParentFrame`, `getFocusedFrame`, `getDeepestComponentAt`, `getComponentUnderMouse`, `getParentWindow`, `getScreenBounds` | none | none | adequate | pure Swing delegation; no logic |
| UIUtils | `isEditingTextIn` — focus-owner check; `isShowing()` guard | unit | `EditLyricActionTest` (mocks `UIUtils.isEditingTextIn`) + direct call with `new JFrame()` at line 92 | inadequate | the direct call at line 92 (`assertThat(UIUtils.isEditingTextIn(new JFrame())).isFalse()`) asserts only the `false` branch in a trivial no-focus context; the `JTextComponent.isShowing()` guard and window-match branch are untested |
| DesktopUtils | `isDesktopSupported` / `getDesktop` / `browse` / `mail` / `open` / `invokeDesktopMethod` — all JNA/reflection-based platform interaction | none | none | adequate | reflection on `java.awt.Desktop`; testable only via real system integration |

**4C notes (quality concerns):**

The highest-risk dark gaps in this package are all in the pure-logic classes that have **zero unit tests**: `StringUtils` (8 methods including the complex `wrapText` rebalancing algorithm, which silently truncates lines when the previous line is too short to donate words), `FileUtils` (path/extension handling including the multi-extension `ensureExtension` with a case-insensitive match), `Utils.getPlatformKeyStrokeString` (a multi-branch string formatter with different output per platform), and `GraphicUtils.convertFromPixels`/`convertToPixels` (a pixel↔physical-unit conversion that feeds the paper-size dialog — a rounding error here produces silent data corruption in saved documents). The `wrapText` method in particular has non-trivial state (`MINIMUM_WRAPPED_WORD_COUNT` rebalancing) that is not tested at all; a mutant that inverts the comparison or drops the rebalancing pass would never be caught. `MyFontUtils.parsePSName` and `parseStyle` drive font-name display throughout the UI and have a number of undocumented edge cases (Damascus-style embedded style, compound style normalization) with no tests. The two existing tests — `MyFontUtilsTest` and `GraphicUtilsClampTest` — are structurally sound but narrow: `MyFontUtilsTest` only checks that `createFont` returns a non-null `Font` with the requested size, which cannot catch wrong-font selection; `GraphicUtilsClampTest` is adequate for `clampToScreen` but leaves the multi-monitor branch and all conversion methods untested. `UIUtils.positionDialog` has the most misleading coverage: `BaseDialogPositionTest` stubs out `UIUtils` entirely, so the positioning arithmetic (3/8 formula, `SCREEN_MARGIN_PX` clamping) has never been asserted against — it is a green but hollow test. Two confirmed dead methods: `StringUtils.removeSyllabifyMarkings` (zero callers in the whole codebase) and `MyFontUtils.getXHeight` (zero callers confirmed by `jet_brains_find_referencing_symbols`); both should be deleted or have a caller introduced before tests are written.

### 4D. `smufl`

Audited by reading each production class body symbol-by-symbol with serena `jet_brains_find_symbol` (include_body=true), enumerating all testable behaviors, then searching `src/test/java/songscribe/smufl/` and cross-package test files via grep for any existing coverage of each behavior.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `BBox` | `width()` = right − left | unit | none found | missing | Add `BBoxTest.testWidthIsRightMinusLeft` |
| `BBox` | `height()` = bottom − top | unit | none found | missing | Add `BBoxTest.testHeightIsBottomMinusTop` |
| `BBox` | `translateX(dx)` shifts left and right by dx, leaves top/bottom unchanged | unit | none found | missing | Add `BBoxTest.testTranslateXShiftsHorizontallyOnly` |
| `BBox` | `union` returns smallest enclosing box (min left/top, max right/bottom) | unit | none found | missing | Add `BBoxTest.testUnionReturnsSmallestEnclosingBox` |
| `BBox` | `fromSMuFL` flips Y-up to Y-down (top=−neY, bottom=−swY) | unit | none found | missing | Add `BBoxTest.testFromSmuflFlipsYConvention` |
| `BBox` | record component accessors (left, top, right, bottom) | none | — | adequate (none warranted) | — |
| `GlyphAnchors` | `requireStemUpSE` returns anchor when present | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemUpSEReturnsAnchorWhenPresent` |
| `GlyphAnchors` | `requireStemUpSE` throws when stemUpSE is null | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemUpSEThrowsWhenNull` |
| `GlyphAnchors` | `requireStemDownNW` returns anchor when present | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemDownNWReturnsAnchorWhenPresent` |
| `GlyphAnchors` | `requireStemDownNW` throws when stemDownNW is null | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemDownNWThrowsWhenNull` |
| `GlyphAnchors/Anchor` | `fromSMuFL` flips Y (y becomes −y) | unit | none found | missing | Add test for `Anchor.fromSMuFL` Y-flip |
| `GlyphAnchors` | record component accessors (stemUpSE, stemDownNW, cutOutNW, cutOutSE — all `@Nullable`) | none | — | adequate (none warranted) | — |
| `SMuFLData` | pure data record, no logic | none | — | adequate (none warranted) | — |
| `SMuFLGlyph` | `smuflName()` returns canonical SMuFL name string | unit | none found | missing | Add `SMuFLGlyphTest.testSmuflNameMatchesSpec` (spot-check a few constants) |
| `SMuFLGlyph` | `codepoint()` returns correct Unicode codepoint | unit | none found | missing | Add `SMuFLGlyphTest.testCodepointMatchesSpec` (spot-check a few constants) |
| `SMuFLGlyph` | `asString()` returns single-character string of codepoint | unit | none found | missing | Add `SMuFLGlyphTest.testAsStringIsSingleCharOfCodepoint` |
| `SMuFLMetadata` | `getBBox` returns populated BBox for a known glyph | unit | indirect via `KeySignatureTest`, `DynamicAttachmentTest`, `ArticulationStackingTest` | inadequate (self-referential: tests use `requireBBox` as their own oracle) | Add direct assertion with concrete numeric value |
| `SMuFLMetadata` | `getBBox` returns null for a glyph absent from the metadata | unit | none found | missing | Add `SMuFLMetadataTest.testGetBBoxReturnsNullForUnknownGlyph` |
| `SMuFLMetadata` | `requireBBox` throws when glyph absent from metadata | unit | none found | missing | Add `SMuFLMetadataTest.testRequireBBoxThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `noteHeadWidthSs` returns correct notehead width in staff spaces | unit | none found | missing | Add `SMuFLMetadataTest.testNoteHeadWidthSsIsPositiveAndPlausible` with concrete bounds |
| `SMuFLMetadata` | `noteHeadHeightSs` returns correct notehead height in staff spaces | unit | none found | missing | Add `SMuFLMetadataTest.testNoteHeadHeightSsIsPositiveAndPlausible` with concrete bounds |
| `SMuFLMetadata` | `getAnchors` returns populated `GlyphAnchors` for a known glyph | unit | none found | missing | Add `SMuFLMetadataTest.testGetAnchorsReturnsAnchorsForKnownGlyph` |
| `SMuFLMetadata` | `getAnchors` returns null for a glyph absent from anchors data | unit | none found | missing | Add `SMuFLMetadataTest.testGetAnchorsReturnsNullForGlyphWithNoAnchors` |
| `SMuFLMetadata` | `requireAnchors` throws when glyph absent from anchors | unit | none found | missing | Add `SMuFLMetadataTest.testRequireAnchorsThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `getAdvanceWidth` returns width for a known glyph | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthReturnsValueForKnownGlyph` |
| `SMuFLMetadata` | `getAdvanceWidth` returns null for a glyph absent from advance widths | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthReturnsNullForAbsentGlyph` |
| `SMuFLMetadata` | `getAdvanceWidthOrZero` returns 0.0 when glyph absent | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthOrZeroReturnsFallbackForAbsentGlyph` |
| `SMuFLMetadata` | `requireAdvanceWidth` throws when glyph absent | unit | none found | missing | Add `SMuFLMetadataTest.testRequireAdvanceWidthThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `getEngravingDefaults` returns SMuFLData with plausible non-zero values | unit | none found | missing | Add `SMuFLMetadataTest.testEngravingDefaultsAreNonZero` |
| `SMuFLMetadata` | `Holder.load()` loads from classpath resource without exception (singleton initializes) | unit | implied by every test that touches `SMuFLMetadata.*` | adequate (singleton load tested implicitly) | — |
| `Engraving` | `G_CLEF_WIDTH_SS` is derived from SMuFL advance width, not hardcoded | unit | `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth` | inadequate (self-referential: expected value is `SMuFLMetadata.requireAdvanceWidth(G_CLEF)` — same call as the production code, so the test cannot detect a wrong value) | Rewrite with a concrete numeric bound or cross-check against a known Bravura value |
| `Engraving` | `BEAM_THICKNESS_SS` / `BEAM_SPACING_SS` / `LEDGER_LINE_THICKNESS_SS` etc. are positive non-zero plausible values | unit | none found | missing | Add `EngravingTest` assertions with concrete plausible bounds |
| `Engraving` | `NOTEHEAD_BLACK_STEM_UP_SE` / `NOTEHEAD_BLACK_STEM_DOWN_NW` anchors are loaded correctly | unit | none found | missing | Add `EngravingTest` assertions checking x/y are non-zero with expected sign |
| `Engraving` | private constructor prevents instantiation | none | — | adequate (none warranted) | — |

**4D notes (quality concerns):**

The most critical gap is that `BBox` — the geometry primitive used in every bounding-box computation across the codebase — has zero direct tests. `translateX` and `union` carry real arithmetic that can silently regress (e.g., a wrong coordinate axis or off-by-one in `union`'s `min`/`max` calls), and the Y-flip in `fromSMuFL` is a sign-convention conversion that is invisible in integration tests. All five `BBox` behaviors are pure functions with no dependencies and are trivial to test.

The sole existing `smufl` package test, `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth`, is self-referential: both the production constant and the test's expected value are computed from the same `SMuFLMetadata.requireAdvanceWidth(G_CLEF)` call, so the test passes even if the constant were set to any value from that same lookup. It cannot catch a wrong glyph mapping, a unit-conversion error, or a metadata parse regression.

The cross-package tests in `DynamicAttachmentTest` and `KeySignatureTest` that use `SMuFLMetadata.requireBBox(...)` as the oracle for their expected values exhibit the same self-referential defect: they verify structural wiring but cannot detect an incorrectly parsed bbox coordinate.

`GlyphAnchors.requireStemUpSE` and `requireStemDownNW` both have null-guard branches that throw via `RuntimeError.exit`. Neither branch has any test. The same pattern applies to `SMuFLMetadata.requireBBox`, `requireAnchors`, and `requireAdvanceWidth` — the "absent glyph throws" path is completely untested in all four methods. These are all plausible regression sites if the metadata JSON is changed or a new glyph mapping is added.

`SMuFLGlyph` enum accessors (`smuflName()`, `codepoint()`, `asString()`) are spot-checked nowhere. A transposed codepoint or misspelled SMuFL name would silently corrupt rendered glyphs and font metric lookups without any test failing.

### 4E. `prefs`

Audited from production code outward: enumerated every testable behavior in `Prefs`, `PrefsKey`, `RecentDocumentsManager`, and `StartupAction`, classified each by the rubric, then checked `src/test/java/songscribe/prefs/PrefsTest.java` (the only test file in the mirrored package) and cross-package unit tests for coverage.

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|---------------|---------|--------|
| `Prefs` | `getOrDefault`: returns store value when present, falls back to `getDefault` when absent | unit | none | missing | add tests for store-hit and store-miss paths |
| `Prefs` | `getDefault`: throws `IllegalArgumentException` for unknown key (no default in `defaults.json`) | unit | none | missing | add test; critical contract for all scalar getters |
| `Prefs` | `getString`: returns stored string value | unit | none | missing | add round-trip test |
| `Prefs` | `getInt`: casts stored value through `Number.intValue()` — survives if value is `Long` | unit | none | missing | add test; int-stored-as-Long contract matters |
| `Prefs` | `getLong`: analogous to `getInt` | unit | none | missing | add test |
| `Prefs` | `getBoolean`: casts to `Boolean` | unit | none | missing | add test |
| `Prefs` | `getStringList`: returns list from store; returns empty list (not default) when absent | unit | none | missing | add tests for both paths; empty-list contract must be verified |
| `Prefs` | `getStringList`: ignores defaults for list keys (unlike scalar getters) | unit | none | missing | this asymmetry is a likely bug-hiding point |
| `Prefs` | `getMap`: returns store value when present (Map); falls to default when absent; returns empty map when absent and no default | unit | `PrefsTest.testGetMapReturnsEmptyMapForMissingKey`, `testGetMapOnNonMapValueReturnsEmptyMap` | inadequate | `testGetMapReturnsEmptyMapForMissingKey` name is wrong — `DIALOG_GEOMETRY` has a default `{}` in `defaults.json`; the test happens to pass because `{}` deserializes as empty, but it is not testing the "no default" path |
| `Prefs` | `putMap`: merges new entries into existing map | unit | `PrefsTest.testPutMapMergesEntries` | inadequate | asserts only `containsKey` — does not verify values are correct; a mutation that stores the wrong values passes |
| `Prefs` | `putMap` + `getMap` round-trip: stored value is retrievable | unit | `PrefsTest.testPutMapAndGetMapRoundTrip` | inadequate | asserts only `containsKey("TestDialog")` — not the nested map values |
| `Prefs` | `put(PrefsKey, String)`: stores string, triggers save+notification | unit | none | missing | add test |
| `Prefs` | `put(PrefsKey, int)`: stores as `Long` (documented type coercion) | unit | none | missing | critical: only `getInt` works after this if value is `Long`; needs explicit assertion |
| `Prefs` | `put(PrefsKey, long)` and `put(PrefsKey, boolean)`: store and retrieve | unit | none | missing | add tests |
| `Prefs` | `putStringList`: replaces list wholesale (not merge) | unit | none | missing | add test |
| `Prefs` | `reset`: removes key from store, restores default | unit | none (only used in `@AfterEach` teardown, not as a behavior under test) | missing | add test verifying value reverts to default after reset |
| `Prefs` | `resetAll`: clears all overrides | unit | none | missing | add test |
| `Prefs` | `parseJsonValue`: dispatches by JSON type (boolean / number stored as Long / string / object as Map / array → null) | unit | none | missing | high-risk: number-as-Long contract underpins all numeric getters; array→null gap means array values in defaults.json are silently dropped |
| `Prefs` | `writeTyped`: parses string to typed value (Boolean / Long / String) based on default type; ignores invalid numeric strings | unit | none | missing | migration correctness depends on this |
| `Prefs` | `migrate`: reads old `.properties` file, maps keys via `MIGRATION_MAP`, scans `showwhatsnew*` keys for highest version | unit | none | missing | high-risk legacy migration; no test |
| `Prefs` | `removeObsoleteKeys`: strips keys in `OBSOLETE_KEYS` from store and saves | unit | none | missing | add test |
| `Prefs` | `allKeysExistInDefaults`: every `PrefsKey` (except `ALL`) has entry in `defaults.json` | unit | `PrefsTest.testAllKeysExistInDefaults` | adequate | well-written contract guard |
| `PrefsKey` | `key()` returns the camelCase JSON string matching the enum constant | unit | `PrefsTest.testAllKeysExistInDefaults` (indirectly exercises `key()`) | adequate | implicitly covered by the defaults check |
| `PrefsKey` | enum is purely a typed-key holder with no value logic | none | — | — | — |
| `RecentDocumentsManager` | `add`: deduplicates (existing entry moves to front), adds at front of MRU list | unit | none | missing | core MRU logic; no test |
| `RecentDocumentsManager` | `add`: enforces `MAX_SIZE` cap by removing last entries | unit | none | missing | off-by-one risk |
| `RecentDocumentsManager` | `add`: normalizes path before insert | unit | none | missing | normalization correctness |
| `RecentDocumentsManager` | `add`: posts `RecentDocumentsDidChangeNotification` after persist | unit | none | missing | notification contract |
| `RecentDocumentsManager` | `remove`: removes matching normalized path; posts notification | unit | none | missing | add test |
| `RecentDocumentsManager` | `remove`: no-op when path absent (should still persist+notify) | unit | none | missing | verify idempotency |
| `RecentDocumentsManager` | `clear`: empties list, persists, posts notification | unit | none | missing | add test |
| `RecentDocumentsManager` | `getRecents`: returns unmodifiable copy | unit | none | missing | verifies defensive copy |
| `RecentDocumentsManager` | constructor: strips non-existent paths from loaded list and persists if any removed | unit | none | missing | startup cleanup logic; untested |
| `RecentDocumentsManager` | constructor: gracefully skips malformed path strings | unit | none | missing | robustness under corrupt prefs |
| `StartupAction` | pure enum — `DO_NOTHING`, `SHOW_FILE_CHOOSER`, `OPEN_MOST_RECENT` | none | — | — | — |

**4E notes (quality concerns):**

The darkest gap in this package is `RecentDocumentsManager` — it has zero tests despite containing real MRU logic (dedup, cap enforcement, path normalization, constructor-time stale-path pruning) and notification side effects. `Prefs` itself has only five test methods, covering exclusively the `getMap`/`putMap` family; every scalar getter, every `put` overload, `putStringList`, `getStringList`, `reset`, `resetAll`, `parseJsonValue`, `writeTyped`, `migrate`, and `removeObsoleteKeys` are all completely untested. The `migrate` method in particular is high-risk: it touches a one-time destructive file operation (deleting the old `.properties` file) and uses `writeTyped` string-to-typed coercion, both of which could silently corrupt prefs on first launch from an old installation. Three of the four existing tests are inadequate by the Quality Principles: `testGetMapReturnsEmptyMapForMissingKey` has a name mismatch (the key has a default), and both round-trip/merge tests assert only `containsKey` rather than verifying actual stored values — a mutant that stores wrong values would survive all of them. The `Prefs` singleton's all-static API makes it straightforwardly unit-testable (the real singleton initializes from classpath resources during tests); no mocking of the singleton chain is needed here.

### 4F. `font`

Audited from production code outward: enumerated every testable behavior in `DocumentFonts`, `DocumentFontsHolder`, `FontKey`, and `SourceSans3Font`, classified each by the rubric, then checked `src/test/java/songscribe/font/DocumentFontsTest.java` (the only test file in the mirrored package) and e2e test source for coverage.

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|---------------|---------|--------|
| `DocumentFonts` | `getFont(FontKey)`: returns stored font | unit | `DocumentFontsTest.GetSet.testGetFontRoundTrip` | adequate | parameterized over all `FontKey` values; asserts exact font identity |
| `DocumentFonts` | `getFont(FontKey)`: throws `IllegalStateException` (with key name in message) when font not set | unit | `DocumentFontsTest.GetSet.testGetFontThrowsWhenNotSet` | adequate | parameterized; verifies exception type and message content |
| `DocumentFonts` | `setFont(FontKey, Font)`: stores font retrievable by key | unit | `DocumentFontsTest.GetSet.testGetFontRoundTrip` (exercises `setFont(FontKey, Font)`) | adequate | covered as part of round-trip |
| `DocumentFonts` | `setFont(FontKey, String, int)`: resolves font by PS name and size, stores it | unit | `DocumentFontsTest.GetSet.testSetFontByNameRoundTripSize` | inadequate | asserts `font.getSize()` == expected size (adequate), but `assertThat(font.getPSName()).isNotEmpty()` is a weak assertion — does not verify that the resolved PS name matches `BASE_NAME`; a mutant that resolves the wrong font passes |
| `DocumentFonts` | copy constructor: produces independent copy (mutations to copy do not affect original) | unit | `DocumentFontsTest.CopyConstructor.testMutatingCopyDoesNotAffectOriginal` | adequate | |
| `DocumentFonts` | copy constructor: mutations to original do not affect copy | unit | `DocumentFontsTest.CopyConstructor.testMutatingOriginalDoesNotAffectCopy` | adequate | |
| `DocumentFonts` | `equals`: identical content → equal | unit | `DocumentFontsTest.Equals.testEqualIdenticalContent` | adequate | |
| `DocumentFonts` | `equals`: reflexive | unit | `DocumentFontsTest.Equals.testEqualReflexive` | adequate | |
| `DocumentFonts` | `equals`: not equal when font name differs for any key | unit | `DocumentFontsTest.Equals.testNotEqualWhenNameDiffers` | adequate | parameterized over `FontKey` |
| `DocumentFonts` | `equals`: not equal when font size differs for any key | unit | `DocumentFontsTest.Equals.testNotEqualWhenSizeDiffers` | adequate | parameterized over `FontKey` |
| `DocumentFonts` | `equals`: `null` object → not equal | unit | none | missing | add test for `equals(null)` returning false |
| `DocumentFonts` | `equals`: different type → not equal | unit | none | missing | low risk given `instanceof` pattern, but worth one line |
| `DocumentFonts` | `hashCode`: consistent with `equals` | unit | `DocumentFontsTest.Equals.testHashCodeConsistentWithEquals` | adequate | |
| `DocumentFonts` | `defaultsFromPrefs()`: populates all six roles from `Prefs` | unit | `DocumentFontsTest.DefaultsFromPrefs.testAllRolesPopulated` | inadequate | only verifies `getSize()` per role equals the prefs font-size value — does not check the font family name; a mutant that calls the wrong `PrefsKey` font string (or resolves the wrong family) while preserving sizes passes |
| `DocumentFonts` | `defaultsFromPrefs()`: maps each `FontKey` to the correct `PrefsKey` pair (e.g., `TITLE` → `TITLE_FONT` + `TITLE_FONT_SIZE`, not some other key) | unit | none | missing | the authoritative FontKey→PrefsKey mapping is untested; wrong-key bugs are invisible |
| `DocumentFontsHolder` | default methods (`getTitleFont`, etc.): each delegates to `getFont` with the matching `FontKey` | none | — | — | trivial one-liners; delegating to `getFont` already tested |
| `FontKey` | pure enum — six constants, no logic | none | — | — | — |
| `SourceSans3Font` | `installLazy()`: registers family loader via FlatLaf `FontUtils` | none | — | — | risk is real Swing/FlatLaf integration; cannot be meaningfully unit-tested |
| `SourceSans3Font` | `install()`: delegates to `installBasic()` | none | — | — | thin wrapper over FlatLaf font registration |
| `SourceSans3Font` | `installBasic()`: installs six font styles via `MyFontUtils.installLocalFont` | none | — | — | font installation is an integration behavior; testing it requires the AWT font subsystem |
| `SourceSans3Font` | String constants (`FAMILY`, `STYLE_*`): correct PS name strings | unit | none | missing | constant values are the contract for font resolution everywhere in the app; a typo silently falls back to a system font; verify each constant matches the bundled font file name |

**4F notes (quality concerns):**

The highest-risk gap is `defaultsFromPrefs` coverage: the existing `testAllRolesPopulated` checks only that each role's font size equals its prefs value. It does not verify the family name, which means any wrong-key assignment in the six-line mapping (e.g., swapping `TITLE_FONT` and `LYRICS_FONT`) goes undetected — a plausible cut-paste error given the repetitive structure. The test should additionally assert `font.getFamily()` (or `font.getPSName()`) against `Prefs.getString(PrefsKey.TITLE_FONT)` etc. for each role. Similarly, `testSetFontByNameRoundTripSize` uses `isNotEmpty()` for the PS name instead of asserting the exact resolved value — a weak assertion that survives any font being substituted. The `SourceSans3Font` constant strings (`FAMILY`, `STYLE_REGULAR`, etc.) are the contract for all font lookups across the application; a typo in any constant causes silent font fallback at runtime, yet no test verifies them against the bundled file names. `DocumentFontsHolder` default methods are trivial enough to classify as `none`. No dead code was identified: `DocumentFontsHolder` is implemented by both `ScoreView` and `DocumentFonts`, and `SourceSans3Font` is called from `UIUtils.initLaf`.

### 4G. `export`

Audited by reading all seven production class bodies; stub/IO-dispatch classes have no branching logic worth testing, but `PageLayoutData.applyMarginOverrides` and `PDFExporter.createPDF` contain real conditional computation.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ExportOptions` | record construction; ALL / NONE constants encode correct boolean triples | unit | none | missing | add unit: verify ALL=(true,true,true), NONE=(false,false,false), and round-trip equality |
| `PageLayoutData` | `applyMarginOverrides`: default applied to all four margins when overrides are -1 | unit | none | missing | add unit: call with all -1 overrides, assert all four fields equal defaultMargin |
| `PageLayoutData` | `applyMarginOverrides`: per-edge override replaces default when value > -1 | unit | none | missing | add unit: supply distinct per-edge values, assert each field independently |
| `PageLayoutData` | `applyMarginOverrides`: boundary — value exactly 0 overrides (> -1) | unit | none | missing | add unit: value=0 should override (currently: 0 > -1 is true) |
| `PDFExporter` | `createPDF`: returns early (no crash) when `data.scoreView` is null | unit | none | missing | add unit: construct PageLayoutData with scoreView=null, call createPDF, assert no exception |
| `PDFExporter` | `createPDF`: scale = min(horizontalScale, verticalScale) — horizontal-constrained branch | unit | none | missing | add unit with mock ScoreView; verify scale and leftMargin under each branch |
| `PDFExporter` | `createPDF`: leftMargin redistribution when `horizontalScale >= verticalScale` | unit | none | missing | same unit as above; assert leftMargin = scaledMargin * (leftInner / (leftInner + rightOuter)) |
| `ABCExporter` | `createABC`: stub — shows error dialog, no logic | none | none | — | no test warranted (pure dialog dispatch, no computation) |
| `ImageExporter` | `createImageForExport[0]`: image dimensions = (sheetWidthPx * scale + borderWidth, sheetHeightPx(opts) * scale + borderHeight) | unit | none | missing | add unit with mock ScoreView and border; assert BufferedImage dimensions |
| `ImageExporter` | `createImageForExport[1]`: renders without exception (stub body, but dimensions/type are real) | none | none | — | body is a stub ("not yet implemented" drawString); no assertion value until implemented |
| `SVGExporter` | `createSVG`: stub — shows error dialog | none | none | — | no test warranted |
| `ExportUtils` | `openExportedFile`: Swing dialog dispatch; no computation | none | none | — | no test warranted |

**4G notes (quality concerns):**

The highest-risk dark gaps are `PageLayoutData.applyMarginOverrides` (four independent conditional branches, all untested — any off-by-one in the threshold guard or a field assignment to the wrong variable would survive indefinitely) and `PDFExporter.createPDF` (non-trivial margin redistribution math under the `horizontalScale >= verticalScale` branch, also completely untested). The `ExportOptions` record is trivial but its constants are contract-defining — a future edit that silently flips a boolean in ALL or NONE would have no safety net. `ImageExporter.createImageForExport[0]` does compute the output image dimensions from scale and border, making the dimension formula testable right now even though the rendering body is a stub. `ABCExporter`, `SVGExporter`, and `ExportUtils` are pure dialog-dispatch stubs with no branching logic and correctly classify as none. There is no dead code in this package: all classes are reachable from UI action paths.

### 4H. `uiconverter`

Audited by reading all three production class bodies; `ChooseDirectoryAction` is pure Swing wiring with no logic, but `UIConverter.isLegalFileName` is a pure predicate and `ConvertAction.ConvertThread` contains an image-scale formula that is unit-testable in isolation.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `UIConverter` | `isLegalFileName`: rejects names shorter than 10 chars | unit | none | missing | add unit: names of length 9 or less must return false |
| `UIConverter` | `isLegalFileName`: rejects name that does not end with `.mssw` | unit | none | missing | add unit: name with wrong extension returns false |
| `UIConverter` | `isLegalFileName`: rejects name whose first three chars are not all digits | unit | none | missing | add unit: each non-digit position in chars 0-2 returns false |
| `UIConverter` | `isLegalFileName`: accepts space separator at char 3 | unit | none | missing | add unit: `"001 Title.mssw"` returns true |
| `UIConverter` | `isLegalFileName`: accepts dash separator at char 3 | unit | none | missing | add unit: `"001-Title.mssw"` returns true |
| `UIConverter` | `isLegalFileName`: rejects any other char 3 (e.g. `'a'`) | unit | none | missing | add unit: `"001aTitl.mssw"` (length >= 10) returns false |
| `UIConverter` | `isLegalFileName`: boundary — exactly 10-char valid name accepted | unit | none | missing | add unit: `"001 a.mssw"` (length=10) returns true |
| `UIConverter` | `main`: public static entry point; called from `SongScribe.main` | none | none | — | entry point; not dead; no unit test warranted (Swing bootstrap) |
| `ConvertAction` | image scale formula: `scale = (IMAGE_WIDTH[i] - 2*LEFT_RIGHT_MARGIN[i]) / sheetWidthPx` | unit | none | missing | add unit with known IMAGE_WIDTH, LEFT_RIGHT_MARGIN, and sheetWidthPx; assert exact double scale |
| `ConvertAction` | `actionPerformed`: empty directory text → error path (no crash, no conversion) | unit | none | missing | add unit: mock `songsDirectory.getText()` returning empty string; verify early return (or via public observable) |
| `ConvertAction` | `actionPerformed`: non-existent directory → error path | unit | none | missing | add unit: supply non-existent path; verify early return |
| `ConvertAction` | `actionPerformed`: directory with zero legal files → error path | unit | none | missing | add unit: real temp directory with no `.mssw` files; verify early return |
| `ConvertAction` | `ConvertThread.run`: full batch conversion (file I/O, ScoreView, MIDI, image write) | e2e | none | missing | reserve for e2e; requires real Swing + file I/O pipeline — too costly to mock completely |
| `ChooseDirectoryAction` | `actionPerformed`: fires `DIRECTORY_CHANGE_PROPERTY` when dialog confirms | none | none | — | pure Swing event dispatch; no computation; not warranted |
| `UIConverter/DirectorySelectionChangeListener` | `handleDirectoryChange`: null listFiles → error path | none | none | — | Swing state mutation (table model, text field) — risk is wiring, not logic; none |
| `UIConverter/DirectorySelectionChangeListener` | `handleDirectoryChange`: populates accepted/rejected tables per `isLegalFileName` | none | none | — | file-enumeration result depends on isLegalFileName (already unit-tested above) and Swing model mutation; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: null input (user cancels dialog) → returns silently | none | none | — | Swing dialog interaction; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: invalid number string → NumberFormatException path | none | none | — | Swing dialog interaction; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: out-of-range number (< 1 or > 999) → error path | none | none | — | Swing dialog interaction; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: zero-padding format `%03d` and `isLegalFileName` re-check on renamed file | unit | none | missing | extract `buildNumberedFileName(String baseName, int number)` to a package-private helper; test format and legality |

**4H notes (quality concerns):**

The highest-risk dark gap is `UIConverter.isLegalFileName`: it is called from three sites (directory scan, file filter in ConvertAction, rename validation in NumberSongAction) and has six independent branch conditions, all untested. A single wrong char-index or off-by-one in the length guard would silently accept or reject files at every call site. The image-scale formula in `ConvertAction.ConvertThread` is pure arithmetic (`(IMAGE_WIDTH[i] - 2 * LEFT_RIGHT_MARGIN[i]) / sheetWidthPx`) but is currently private and embedded in a thread body; extracting it to a package-private static would allow a direct unit test without mocking the entire pipeline. The `NumberSongAction.handleNumberSong` zero-padding and re-validation logic is also pure computation that cannot currently be tested without Swing; that logic should be extracted to be testable. The `ConvertThread.run` full-pipeline path (open file → write mssw → produce images → produce MIDI → optional zip) genuinely requires the real ScoreView and file system and warrants a single e2e test for the happy path and the per-file error paths. `ChooseDirectoryAction` and the `DirectorySelectionChangeListener` table-population logic are Swing wiring with no testable computation beyond `isLegalFileName`. `UIConverter.main` is a legitimate entry point (dispatched from `SongScribe.main`) — not dead, and not a candidate for unit testing.

### §4 — summary

Audited all ~50 production classes across the eight packages (excl. `package-info`) in two waves of parallel production-first sub-audits. Dominant patterns to drive remediation:

1. **Whole packages / subsystems are dark.** `converter` has **zero** tests of any kind (no test dir, no cross-package references). `prefs.RecentDocumentsManager` (full MRU logic) has zero tests, and `Prefs`' five existing methods cover only the `getMap`/`putMap` family — every scalar getter, `put` overload, `parseJsonValue`, `writeTyped`, `migrate`, and `removeObsoleteKeys` are untested. Most of `util`'s pure-logic helpers and all of `smufl`'s geometry/lookup logic are untested.
2. **Pure computation is the biggest blind spot — and it is high-risk.** `midi.LineTrackBuilder.getTupletFactor` (log2 tuplet timing, 3 branches), `MidiSequenceBuilder.buildSequenceWithRepeats` (repeat state machine + first/second endings), `MidiEventFactory.addTempoEvent` (3-byte big-endian SET_TEMPO — byte-order mutation = silent wrong tempo), `smufl.BBox` (`union`/`translateX`/`fromSMuFL` Y-flip), `util.StringUtils.wrapText` + `GraphicUtils` px↔unit conversions + `Utils.getPlatformKeyStrokeString`, `export.PageLayoutData.applyMarginOverrides` + `PDFExporter.createPDF` margin math, `uiconverter.UIConverter.isLegalFileName` (6 branches, 3 call sites), and `Prefs` typed coercion + `migrate` — all dark.
3. **Weak-but-green / self-referential tests persist (same pattern as Sessions 1/3).** Self-referential oracles: `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth` and the cross-package `requireBBox`-as-its-own-oracle usages. Hollow assertions: midi `isNotEmpty()`/`hasSizeGreaterThanOrEqualTo(4)` on bend/CC events, `MyFontUtilsTest` (`isNotNull()`+`getSize()` only), `Prefs` map tests (`containsKey`-only + a name-mismatch on the "missing key" test), `DocumentFonts.defaultsFromPrefs` (size-only, family unverified), `setFont`-by-name (`isNotEmpty()`). Mocked-out-and-hollow: `UIUtils.positionDialog` is "covered" by `BaseDialogPositionTest`, which stubs `UIUtils` entirely — the 3/8 + `SCREEN_MARGIN_PX` arithmetic is never asserted.
4. **Name/behavior mismatch:** `GlissandoMidiIntegrationTest.testNoPitchBendWithoutGlissando` reads only the fixture model and never builds a MIDI track — MIDI mutations leave it green.
5. **Untested error/throw paths:** `smufl` `require*` absent-glyph throws (all four) + `GlyphAnchors` null guards, `converter.ArgumentReader` unknown-flag/file-not-found/`System.exit` paths, `Prefs.getDefault` IAE for unknown key.
6. **Dead code surfaced (delete in remediation, don't test), verified zero refs:** `util.StringUtils.removeSyllabifyMarkings` (+ its `HYPHEN_UNDERSCORE_PATTERN`/`IN_PARENTHESES_PATTERN`) and `util.MyFontUtils.getXHeight`.
7. **Production observations filed as a tracked issue (#409):** dead code (#1); `converter.SVGConverter.main` is package-private (cannot serve as a JVM entry point — latent bug); `Prefs.parseJsonValue` maps JSON arrays to `null`, silently dropping any array-valued default in `defaults.json`; `Prefs.getStringList` ignores defaults (asymmetric with scalar getters).
8. **Testability-over-encapsulation extractions recommended:** widen `ConvertAction`'s embedded image-scale formula and `UIConverter.NumberSongAction.buildNumberedFileName` to package-private statics for direct unit tests.
9. **Only one genuine e2e escalation:** `uiconverter.ConvertAction.ConvertThread.run` (full batch pipeline through real `ScoreView` + filesystem). Everything else in scope is unit or none; `converter`/`export`/`uiconverter` file-IO/render orchestration with no branching is correctly `none`.

## 5. `ui/action` (audited 2026-05-21)

Audited via six parallel production-first sub-audits run in two waves of three: **5A** base/infrastructure (the `UIAction` enablement machinery, action groups, `Actions` registry, mode/dialog-open base); **5B** note/element insertion + duration/articulation; **5C** markings (dynamics/annotation/structural); **5D** clipboard/selection/line; **5E** file/app lifecycle; **5F** export + misc dialog-open. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.


### 5A. base/infrastructure — UIAction, SelectableUIAction, StickyUIAction, ControlAction, ActionGroup, DurationActionGroup, NonDurationActionGroup, Actions, ModeAction, LaunchAction, DialogOpenAction

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| UIAction | Constructor rejects null mainFrame | unit | `UIActionFlagBehaviorTest.testConstructorRejectsNullMainFrame` | adequate | keep |
| UIAction | `setFlags` disables on construction when REQUIRES_SELECTION / REQUIRES_SINGLE_SELECTION / REQUIRES_MULTIPLE_SELECTION / DISABLE_WHEN_SONG_EMPTY | unit | — | missing | write test: construct with each of those four flags; assert `isEnabled() == false` |
| UIAction | `hasFlag` detects a set vs. unset flag | unit | `UIActionFlagBehaviorTest` (implicit via updateEnabledState calls) | adequate (indirect) | keep |
| UIAction | `updateEnabledState` returns false and disables when `getScoreView()` is null | unit | — | missing | write test: stub `getScoreView()` → null; assert returns false and `isEnabled() == false` |
| UIAction | `enableInAdjustmentMode` — DISABLE_IN_ADJUSTMENT_MODE + adjustment mode disables; no flag or non-adjustment mode enables | unit | `UIActionReflectableGuardTest.testNonReflectableWithSelectionRunsNormalLogic` (DISABLE_IN_ADJUSTMENT_MODE only) | adequate | keep |
| UIAction | `enableInSelectMode` — DISABLE_IN_SELECT_MODE + isInSelectMode disables | unit | — | missing | write test: stub `isInSelectMode()` → true; assert false; and false when not in select mode |
| UIAction | `enableFromSelectionSize` — REQUIRES_SELECTION: size 0 → false, size > 0 → true | unit | — | missing | write test for each size-flag variant (see below rows) |
| UIAction | `enableFromSelectionSize` — REQUIRES_EMPTY_SELECTION: size 0 → true, size > 0 → false | unit | — | missing | write test |
| UIAction | `enableFromSelectionSize` — REQUIRES_SINGLE_SELECTION: size 1 → true, size ≠ 1 → false | unit | — | missing | write test |
| UIAction | `enableFromSelectionSize` — REQUIRES_OPTIONAL_SINGLE_SELECTION: size 0 or 1 → true, size > 1 → false | unit | — | missing | write test |
| UIAction | `enableFromSelectionSize` — REQUIRES_MULTIPLE_SELECTION: size > 1 → true, size ≤ 1 → false | unit | `TupletActionTest` (indirect — size=2 case only) | inadequate | write direct test covering both branches |
| UIAction | `enableFromSelectionSize` — REQUIRES_OPTIONAL_MULTIPLE_SELECTION: size 0 or > 1 → true, size 1 → false | unit | — | missing | write test |
| UIAction | `enableInRestMode` — DISABLE_IN_REST_MODE + REST_ACTION.isSelected → false | unit | — | missing | write test: stub `REST_ACTION.isSelected()==true`; assert false |
| UIAction | `enableInRestMode` — DISABLE_IN_REST_MODE + selectionHasRests → false | unit | — | missing | write test: stub `selectionHasRests()==true`; assert false |
| UIAction | `enableInRestMode` — no flag → always true | unit | — | missing | write test |
| UIAction | `enableFromPlaybackState` — DISABLE_WHEN_PLAYING + isPlaying → false; no flag → true | unit | — | missing | write test mocking `PlaybackController.isPlaying()` |
| UIAction | `enableFromDialogVisibility` — OPENS_DIALOG + dialog visible → false; dialog closed → true; no flag → true | unit | `UIActionFlagBehaviorTest` (3 tests) | adequate | keep |
| UIAction | `enableFromGraceModeState` — DISABLE_IN_GRACE_MODE + `GraceModeManager.isActive()` → false | unit | `UIActionFlagBehaviorTest.testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses` (combined with OPENS_DIALOG) | inadequate | write isolated test for DISABLE_IN_GRACE_MODE alone |
| UIAction | `enableFromTextEditingState` — DISABLE_WHEN_EDITING_TEXT + editing → false; not editing → true | unit | `EditLyricActionTest.testEnableFromTextEditingState*` (via EditLyricAction subclass) | adequate | keep |
| UIAction | `enableFromBarSelection` — DISABLE_WHEN_BAR_SELECTED + no active selection + bar selected → false | unit | `EnableFromSelectionTest` (3 tests) | adequate | keep |
| UIAction | `enableFromSelection` — non-Reflectable, no active selection → true | unit | `EnableFromSelectionTest.testNoSelectionReturnsTrue` | adequate | keep |
| UIAction | `enableFromSelection` — non-Reflectable + DISABLE_WHEN_BAR_SELECTED + selection has durations → true / no durations → false | unit | `EnableFromSelectionTest` (2 tests) | adequate | keep |
| UIAction | `enableFromSelection` — non-Reflectable + no DISABLE_WHEN_BAR_SELECTED → true | unit | `EnableFromSelectionTest.testNonReflectableWithoutFlagReturnsTrue` | adequate | keep |
| UIAction | `enableFromSelection` — Reflectable, `isApplicableToSelection()` true/false | unit | `EnableFromSelectionTest` (2 tests) | adequate | keep |
| UIAction | `enableFromDurationSelection` — ENABLE_WHEN_DURATION_SELECTED + active selection → defers (true) | unit | `EnableFromSelectionTest.testEnableFromDurationSelectionDefersWithActiveSelection` | adequate | keep |
| UIAction | `enableFromDurationSelection` — ENABLE_WHEN_DURATION_SELECTED + no selection + grace/glissando/slide-out selected → false | unit | — | missing | write test: set `DURATION_ACTION_GROUP` to `GRACE_EIGHTH_NOTE_ACTION`; assert false; repeat for GLISSANDO_ACTION and SLIDE_OUT_ACTION |
| UIAction | `enableFromDurationSelection` — ENABLE_WHEN_DURATION_SELECTED + no selection + normal duration selected → true | unit | — | missing | write test: set `DURATION_ACTION_GROUP` to `QUARTER_NOTE_ACTION`; assert true |
| UIAction | `enableFromSongState` — DISABLE_WHEN_SONG_EMPTY + song empty → false; non-empty → true | unit | — | missing | write test mocking `score.isInitialized()` and `score.getSong().isEmpty()` |
| UIAction | `enableFromSongState` — no flag → true regardless | unit | — | missing | write test |
| UIAction | `applyToSelectionIfActive` — non-Reflectable → false | unit | `ApplyToSelectionInterceptTest.testNonReflectableReturnsFalse` | adequate | keep |
| UIAction | `applyToSelectionIfActive` — Reflectable + no selection → false, no dispatch | unit | `ApplyToSelectionInterceptTest.testReflectableWithNoSelectionReturnsFalse` | adequate | keep |
| UIAction | `applyToSelectionIfActive` — Reflectable + active selection → true, dispatches selected state | unit | `ApplyToSelectionInterceptTest` (2 tests: selected=false, selected=true) | adequate | keep |
| UIAction | `perform(source)` — delegates to `actionPerformed` with correct ActionEvent | unit | — | missing | write test: override `actionPerformed` in anonymous subclass; assert it was called with correct source |
| UIAction | `setIcon` with null → no-op; SVG path → puts LARGE_ICON_KEY; tagged unicode → puts FONT_ICON_KEY and FONT_KEY | none | — | none | pure display wiring, no branching logic worth testing |
| UIAction | `@Handler` methods call `updateEnabledState` (modeDidChange, musicSelectionDidChange, etc.) | none | — | none | trivial delegation to `updateEnabledState`; no independent logic |
| UIAction | `dialogVisibilityDidChange` — only calls `updateEnabledState` when OPENS_DIALOG flag present | unit | — | missing | write test: action without flag, post notification; assert `isEnabled()` unchanged (or use spy to verify no call) |
| SelectableUIAction | `isSelected()` defaults false on construction | unit | `ActionsResetOnDocumentLoadTest` (implicit via `isSelected()` assertion post-reset) | inadequate | write direct test: construct `SelectableUIAction` subclass; assert `isSelected() == false` immediately after construction |
| SelectableUIAction | `setSelected(true/false)` updates `SELECTED_KEY` putValue | unit | `DynamicMarkingActionTest.ActionGroupBehavior` (indirect via ActionGroup) | adequate | keep |
| SelectableUIAction | `reset()` sets selected to false | unit | `ActionsResetOnDocumentLoadTest` (via resetToDefaults which calls group reset, not action reset directly) | inadequate | write direct test: `action.setSelected(true); action.reset(); assertThat(action.isSelected()).isFalse()` |
| SelectableUIAction | `prefsKey` ctor: `setSelected(Prefs.getBoolean(prefsKey))` when key non-null | unit | — | missing | write test with mocked `Prefs.getBoolean()` returning true; assert `isSelected() == true` |
| StickyUIAction | `doActionPerformed` — source is JRootPane + already selected → returns false (no toggle) | unit | — | missing | write test: create StickyUIAction subclass, `setSelected(true)`, call `doActionPerformed` with JRootPane source; assert returns false and `isSelected()` still true |
| StickyUIAction | `doActionPerformed` — source is JRootPane + not selected → calls `toggleOnKeyboardShortcut`, returns true | unit | — | missing | write test: source JRootPane, `isSelected()==false`; assert returns true and `isSelected()` toggled to true |
| StickyUIAction | `doActionPerformed` — source is NOT JRootPane → always calls toggleOnKeyboardShortcut, returns true | unit | — | missing | write test: source is e.g. a JButton; assert returns true |
| ControlAction | `actionPerformed` — posts `ControlDidChangeNotification` with correct control | unit | — | missing | write test: subscribe handler, call `actionPerformed`; assert correct notification posted |
| ControlAction | Factory methods bind correct `Control` enum value | unit | — | missing | write test asserting `createMouseControlAction().control == Control.MOUSE` etc. |
| ActionGroup | `setSelected(action, true)` deselects previous, updates `selected` | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testSelectingOneActionDeselectsPrevious` | adequate | keep |
| ActionGroup | `setSelected(action, false)` on currently-selected clears `selected` | unit | — | missing | write test: `group.setSelected(a, true); group.setSelected(a, false); assertThat(group.getSelected()).isNull()` |
| ActionGroup | `setSelected(action, true)` captures `previousSelected` | unit | — | missing | write test: select A; select B; assert `getPreviousSelected() == A` |
| ActionGroup | `clearSelection` sets `selected` to null, deselects action | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testClearSelectionClearsAll` | adequate | keep |
| ActionGroup | `reset` with default action → selects default | unit | `ActionsResetOnDocumentLoadTest` (via `DURATION_ACTION_GROUP.reset` → quarter note) | adequate (indirect) | keep |
| ActionGroup | `reset` without default → clears selection | unit | `ActionsResetOnDocumentLoadTest` (e.g. ACCIDENTAL_ACTION_GROUP.reset clears) | adequate (indirect) | keep |
| ActionGroup | `add` is idempotent (adding same action twice doesn't duplicate) | unit | — | missing | write test: add same action twice; assert `getActions().size() == 1` |
| ActionGroup | `insert(index, action)` inserts at correct index; idempotent | unit | — | missing | write test |
| ActionGroup | `remove(action)` removes from list and detaches property listener | unit | — | missing | write test: add action, remove it; select it; assert `getSelected() == null` (listener detached) |
| ActionGroup | `contains(action)` true after add, false otherwise | unit | — | missing | write test |
| ActionGroup | `anySelected` — true when at least one action selected | unit | — | missing | write test: group with two Selectable actions; select one; assert true; clear; assert false |
| ActionGroup | `selectNext` — wraps from last to first | unit | — | missing | write test |
| ActionGroup | `selectNext` — advances to next when selected is not last | unit | — | missing | write test |
| ActionGroup | `selectNext` — when selected is null → selects first | unit | — | missing | write test |
| ActionGroup | `select(action, source)` — calls `perform(source)` only when selection changes | unit | — | missing | write test: select same action twice; verify perform called once (first call); second call no-op |
| ActionGroup | `propertyChange` — external `putValue(SELECTED_KEY, true)` on a member is intercepted and enforces exclusivity | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testSelectingOneActionDeselectsPrevious` (only via `setSelected`; `propertyChange` not exercised directly) | inadequate | write test: directly call `action.putValue(SELECTED_KEY, true)` and assert the group's previous selection is cleared |
| ActionGroup | `isSelected(action)` returns true only for currently selected action | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testSelectingOneActionDeselectsPrevious` | adequate | keep |
| DurationActionGroup | `barWasSelected` handler clears duration selection at HIGH_PRIORITY | unit | — | missing | write test: select a duration action; post `BarWasSelectedNotification`; assert `DURATION_ACTION_GROUP.getSelected() == null` |
| NonDurationActionGroup | `durationWasSelected` handler clears non-duration selection at HIGH_PRIORITY | unit | — | missing | write test: select a non-duration action; post `DurationWasSelectedNotification`; assert `NON_DURATION_ACTION_GROUP.getSelected() == null` |
| Actions | Static initializer sets QUARTER_NOTE as default for DURATION_ACTION_GROUP | unit | `ActionsResetOnDocumentLoadTest` (verifies reset goes to quarter note) | adequate | keep |
| Actions | `resetToDefaults` resets all groups and standalones on `DocumentDidLoadNotification` | unit | `ActionsResetOnDocumentLoadTest.testDocumentDidLoadResetsAllActionStateToDefaults` | adequate | keep |
| Actions | `getAppMenuActions` returns all `AppMenuAction` fields with correct native titles | unit | `ActionsAppMenuTest` (3 tests) | adequate | keep |
| Actions | `getAppMenuActions` result is cached (same list instance) | unit | `ActionsAppMenuTest.testGetAppMenuActionsReturnsCachedList` | adequate | keep |
| ModeAction | Factory methods bind correct `Mode` value | unit | — | missing | write test: `createSelectModeAction(mf).getMode() == Mode.SELECT` etc. |
| ModeAction | `actionPerformed` — posts `ModeDidChangeNotification` with self as source | unit | — | missing | write test: subscribe handler; call `actionPerformed`; assert message posted with correct `ModeAction` instance |
| ModeAction | `toggleOnKeyboardShortcut` toggles when source is JRootPane | unit | — | missing | write test: `setSelected(false)`, call `actionPerformed` with JRootPane source; assert `isSelected() == true` |
| ModeAction | Hard-wired flags: DISABLE_WHEN_PLAYING + DISABLE_IN_GRACE_MODE | unit | — | missing | write test: assert both flags present via `hasFlag()` |
| LaunchAction | `actionPerformed` — spawns ProcessBuilder using current process info plus `app.command` | none | — | none | involves `ProcessHandle` and OS process spawning; untestable without real process machinery; risk is `IOException` silently swallowed (see notes) |
| LaunchAction | `App` enum maps correct command suffix (sb/ss) | none | — | none | pure data constant; no logic |
| DialogOpenAction | Constructor auto-sets `OPENS_DIALOG` flag | unit | `UIActionFlagBehaviorTest.testDialogOpenActionAutoSetsOpensDialogFlag` | adequate | keep |
| DialogOpenAction | Constructor derives `actionCommand` via `toKebabCase(name)` | unit | — | missing | write test: `new DialogOpenAction(mf, "Song Settings", ...)`: assert `getActionCommand().equals("song-settings")` |
| DialogOpenAction | `getDialog` lazy-initializes on first call and caches | unit | — | missing | write test: call `getDialog()` twice; assert both return same non-null instance; also test reflective instantiation failure path returns null |
| DialogOpenAction | `actionPerformed` calls `dialog.setVisible(true)` | none | — | none | pure Swing delegation, no logic |

**5A notes (quality concerns):**

**Highest-risk gaps:**

1. `UIAction.enableFromSelectionSize` — the six selection-size flag variants (`REQUIRES_SELECTION`, `REQUIRES_EMPTY_SELECTION`, `REQUIRES_SINGLE_SELECTION`, `REQUIRES_OPTIONAL_SINGLE_SELECTION`, `REQUIRES_MULTIPLE_SELECTION`, `REQUIRES_OPTIONAL_MULTIPLE_SELECTION`) have **zero direct unit coverage**. Each branch is a small predicate (`size == 1`, `size > 1`, etc.) that a surviving mutant (e.g. `>` → `>=`) would trivially expose. The only indirect coverage is `TupletActionTest` which happens to exercise `REQUIRES_MULTIPLE_SELECTION` with size=2 but does not test the boundary (size=1 → false).

2. `UIAction.enableFromDurationSelection` grace/glissando/slide-out branches — the three identity comparisons (`duration != GRACE_EIGHTH_NOTE_ACTION`, `!= GLISSANDO_ACTION`, `!= SLIDE_OUT_ACTION`) are untested at unit level. The e2e test in `ElementInsertionTest` exercises this path but at wrong level for a pure-logic predicate.

3. `DurationActionGroup.barWasSelected` and `NonDurationActionGroup.durationWasSelected` — the mutual-exclusion handlers (both marked `HIGH_PRIORITY`) have **no unit tests** verifying the deselection contract. This is the core correctness guarantee of these two classes.

4. `StickyUIAction.doActionPerformed` — the sticky toggle-suppression logic (JRootPane source + already selected → return false without toggling) has **zero test coverage**.

5. `ActionGroup.propertyChange` — the re-entrancy guard (`selectLevel > 0`) and the external `putValue(SELECTED_KEY, …)` path are exercised only via `setSelected` in `DynamicMarkingActionTest`; the `propertyChange` path itself is never directly tested, meaning the exclusivity wiring via the property listener is unverified.

**Inadequate tests:**

- `UIActionFlagBehaviorTest.testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses` combines `OPENS_DIALOG` and `DISABLE_IN_GRACE_MODE` but never tests `DISABLE_IN_GRACE_MODE` in isolation — if `enableFromGraceModeState` were dropped, this test would still fail for a different reason (`OPENS_DIALOG` path), masking the regression.
- `SelectableUIAction` initial-false default and `reset()` are exercised only via `ActionsResetOnDocumentLoadTest` through the global `Actions` singletons; a direct construction test is needed to distinguish a regression in `SelectableUIAction` itself from a regression in `Actions.resetToDefaults`.

**Name mismatch:**

- `UIActionFlagBehaviorTest.testDialogOpenActionAutoSetsOpensDialogFlag` has a stale comment above the test that reads "-- DialogOpenAction does NOT auto-set OPENS_DIALOG --" but the test title and body assert the opposite (that it does auto-set). This is a doc-comment bug — the test itself is correct.

**Production bug / observation worth filing:**

- `LaunchAction.actionPerformed` silently swallows all exceptions, including `IOException` when the spawned process fails to start. The user gets no feedback. This is worth a GitHub issue: add at minimum a `LOG.error` (or user-facing notification) in the catch block.
- `UIAction.dialogVisibilityDidChange` only calls `updateEnabledState()` when `hasFlag(OPENS_DIALOG)`. This is intentional per the source comment, but there is no test asserting the negative case (an action *without* `OPENS_DIALOG` is unaffected by this notification), leaving this guard invisible to tests.

### 5B. note/element insertion + duration/articulation — ElementTypeAction, AccidentalAction, AccidentalInParensAction, DotAction, NoteOnlyAction, DurationArticulationAction, ForceArticulationAction, FermataAction, FlipStemDirectionAction, TupletAction, ToggleTrillAction

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementTypeAction | `appliesTo` — DURATION applies to notes and rests, not barlines or grace notes | unit | `ElementTypeActionTest.testDurationAppliesToNote`, `testDurationAppliesToRest`, `testDurationDoesNotApplyToBarline`, `testDurationDoesNotApplyToGraceNote` | adequate | — |
| ElementTypeAction | `appliesTo` — NON_DURATION (non-breath-mark) applies to barlines/repeats, not notes or rests | unit | `ElementTypeActionTest.testNonDurationAppliesToBarline`, `testNonDurationDoesNotApplyToNote`, `testNonDurationDoesNotApplyToRest` | adequate | — |
| ElementTypeAction | `appliesTo` — BREATH_MARK action applies only to BREATH_MARK elements, not to barlines | unit | none | missing | add `testBreathMarkAppliesToBreathMark`, `testBreathMarkDoesNotApplyToBarline` |
| ElementTypeAction | `matchesElement` — returns true when `element.getType().toNote() == type`, false otherwise | unit | `ElementTypeActionTest.testMatchesElementWhenTypeMatches`, `testDoesNotMatchElementWhenTypeDiffers` | adequate | — |
| ElementTypeAction | `matchesGlissandoType` — returns true only when glissando type matches action's type | unit | none | missing | add `testMatchesGlissandoTypeWhenMatches`, `testMatchesGlissandoTypeWhenDiffers` |
| ElementTypeAction | `createReplacement` — note stays note, rest stays rest, grace note maps to grace | unit | `ElementTypeActionTest.testCreateReplacementPreservesNoteKind`, `testCreateReplacementPreservesRestKind`, `testCreateReplacementWithGraceNote` | adequate | — |
| ElementTypeAction | `actionPerformed` — posts `DurationWasSelectedNotification` for DURATION kind when no active selection | unit | none | missing | add test verifying correct message posted |
| ElementTypeAction | `actionPerformed` — posts `BarWasSelectedNotification` for NON_DURATION kind when no active selection | unit | none | missing | add test verifying correct message posted |
| ElementTypeAction | flags — DURATION kind lacks `DISABLE_IN_REST_MODE`; NON_DURATION has it | unit | `ElementTypeActionTest.testDurationKindHasCorrectFlags`, `testNonDurationKindHasCorrectFlags` | adequate | — |
| AccidentalAction | `applyToElement(el, true)` — sets accidental on element | unit | `AccidentalActionTest.testApplyToNoteAppliesAccidental` | adequate | — |
| AccidentalAction | `applyToElement(el, false)` — removes accidental (sets to null) | unit | `AccidentalActionTest.testApplyToNoteRemovesAccidental` | adequate | — |
| AccidentalAction | `matchesElement` — true when accidental matches, false when different | unit | `AccidentalActionTest.testMatchesWhenAccidentalMatches`, `testDoesNotMatchWhenAccidentalDiffers` | adequate | — |
| AccidentalAction | `actionPerformed` — plays selected note when `PLAY_SELECTED_NOTE` pref is set and selection is a single note | unit | none | missing | add test verifying `PlayThread` is started / not started per pref |
| AccidentalInParensAction | `applyToElement(el, true)` — sets `accidentalInParentheses` when accidental is non-null | unit | `AccidentalInParensActionTest.testApplyToNoteAppliesParentheses` | adequate | — |
| AccidentalInParensAction | `applyToElement(el, false)` — clears `accidentalInParentheses` | unit | `AccidentalInParensActionTest.testApplyToNoteRemovesParentheses` | adequate | — |
| AccidentalInParensAction | `applyToElement(el, true)` on element with no accidental is a no-op (`setAccidentalInParentheses` guards on `getAccidental() != null`) | unit | none | missing | add `testApplyToNoteWithNoAccidentalIsNoOp` |
| AccidentalInParensAction | `matchesElement` — true when in parens, false otherwise | unit | `AccidentalInParensActionTest.testMatchesWhenInParentheses`, `testDoesNotMatchWhenNotInParentheses` | adequate | — |
| DotAction | `applyToElement(el, true)` — single dot sets dotCount=1, double dot sets dotCount=2 | unit | `DotActionTest.testApplyToNoteSingleDotApplies`, `testApplyToNoteDoubleDotApplies` | adequate | — |
| DotAction | `applyToElement(el, false)` — removes dot (sets dotCount=0) | unit | `DotActionTest.testApplyToNoteRemovesDot` | adequate | — |
| DotAction | `matchesElement` — single/double correctly matched against dotCount values | unit | `DotActionTest.testSingleDotMatchesDotCount1`, `testDoubleDotMatchesDotCount2`, `testSingleDotDoesNotMatchDotCount0`, `testDoubleDotDoesNotMatchDotCount1` | adequate | — |
| DotAction | `matchesElement` — single-dot action does not match dotCount=2 | unit | none | missing | add `testSingleDotDoesNotMatchDotCount2` |
| DotAction | `appliesTo` — applies to notes and rests, not barlines | unit | `DotActionTest.testAppliesToNote`, `testAppliesToRest`, `testDoesNotApplyToBarline` | adequate | — |
| NoteOnlyAction | `appliesTo` — true for pitched notes, false for rests and barlines | unit | `NoteOnlyActionAppliesToTest.testAppliesToNote`, `testDoesNotApplyToRest`, `testDoesNotApplyToBarline` | adequate | — |
| NoteOnlyAction | `appliesTo` — grace notes (isNote() returns true for grace notes; no DISABLE_IN_GRACE_MODE flag; behavior is intentional but untested) | unit | none | missing | add `testAppliesToGraceNote` to document the intentional contract |
| DurationArticulationAction | `applyToElement(el, true)` — adds articulation | unit | `DurationArticulationActionTest.testApplyToNoteAppliesArticulation` | adequate | — |
| DurationArticulationAction | `applyToElement(el, false)` — removes articulation | unit | `DurationArticulationActionTest.testApplyToNoteRemovesArticulation` | adequate | — |
| DurationArticulationAction | `applyToElement(el, true)` when articulation already present — removes old instance and re-adds (not a double-add) | unit | none | missing | add `testApplyToNoteReplacesDuplicateArticulation` |
| DurationArticulationAction | `matchesElement` — true when articulation present, false otherwise | unit | `DurationArticulationActionTest.testMatchesWhenArticulationMatches`, `testDoesNotMatchWhenArticulationNull` | adequate | — |
| ForceArticulationAction | `applyToElement(el, true/false)` and `matchesElement` — identical logic to `DurationArticulationAction` | unit | `ForceArticulationActionTest.*` (4 tests) | adequate | flag duplication: `ForceArticulationAction` and `DurationArticulationAction` have byte-for-byte identical `applyToElement` and `matchesElement` bodies; refactor opportunity |
| FermataAction | `applyToElement(el, true)` — adds `FermataAttachment` when none present | unit | `FermataActionTest.testApplyToNoteAppliesFermata` | adequate | — |
| FermataAction | `applyToElement(el, false)` — removes existing `FermataAttachment` | unit | `FermataActionTest.testApplyToNoteRemovesFermata` | adequate | — |
| FermataAction | `applyToElement(el, true)` idempotence — calling twice should not add a duplicate (guard branch: `if (findAttachment == null)`) | unit | none | missing | add `testApplyToNoteWithExistingFermataIsIdempotent` |
| FermataAction | `matchesElement` — true when fermata present, false when absent | unit | `FermataActionTest.testMatchesWhenFermataTrue`, `testDoesNotMatchWhenFermataFalse` | adequate | — |
| FlipStemDirectionAction | `musicSelectionDidChange` — disables when `canFlipStemDirection()` returns false (all-rest selection) | unit | none | missing | add `FlipStemDirectionActionEnablementTest` with mocked `ctrl.canFlipStemDirection()` |
| FlipStemDirectionAction | `musicSelectionDidChange` — enables when `canFlipStemDirection()` returns true (has at least one note in selection) | unit | none | missing | same new test class |
| FlipStemDirectionAction | `actionPerformed` — posts `FlipStemDirectionCommand` | unit | `ScoreViewControllerCommandHandlerTest.testHandleFlipStemDirectionEmitsOneNotificationWithModificationsPerNote` (tests command handler, not the action dispatch) | wrong-level (handler test, not action dispatch) | add unit test asserting `FlipStemDirectionCommand` is posted |
| TupletAction | `handleChange` — canToggle=false disables all | unit | `TupletActionTest.testNotUniformDisablesEverything` | adequate | — |
| TupletAction | `handleChange` — canToggle=true, no existing: enables add-actions, disables remove | unit | `TupletActionTest.testUniformNoTupletEnablesAddActionsDisablesRemove` | adequate | — |
| TupletAction | `handleChange` — canToggle=true, partial coverage: enables remove, disables all add-actions | unit | `TupletActionTest.testPartialCoverageOfTripletEnablesRemoveDisablesAllAddActions` | adequate | — |
| TupletAction | `handleChange` — canToggle=true, full coverage, existing grade matches action: disable that action | unit | `TupletActionTest.testFullCoverageOfTripletDisablesTripletEnablesOthersAndRemove`, `testFullCoverageOfQuintupletDisablesQuintupletEnablesOthersAndRemove` | adequate | — |
| TupletAction | `handleChange` — `songDidChange` and `documentDidLoad` trigger same `handleChange` path | unit | none | missing | add tests verifying `songDidChange` / `documentDidLoad` handlers update enabled state |
| TupletAction | `actionPerformed` — posts `ToggleTupletCommand` with correct tuplet reference | unit | none | missing | add test asserting command posted with correct `getTuplet()` |
| TupletAction | `getTuplet()` / `Tuplet.getSize()` — enum size values correct | unit | indirectly via `TupletActionTest` which calls `makeTuplet(TupletAction.Tuplet.TRIPLET.getSize())` | adequate (implicit) | — |
| ToggleTrillAction | `musicSelectionDidChange` — disables when `canToggleTrill()` returns false | unit | none | missing | add `ToggleTrillActionEnablementTest` with mocked `ctrl.canToggleTrill()` |
| ToggleTrillAction | `musicSelectionDidChange` — enables when `canToggleTrill()` returns true | unit | none | missing | same new test class |
| ToggleTrillAction | `actionPerformed` — posts `ToggleTrillCommand` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTrillEmitsOneNotificationWithSingleRangeElementAddition` (tests command handler, not action dispatch) | wrong-level | add unit test asserting `ToggleTrillCommand` is posted |

**5B notes (quality concerns):**

The highest-risk gaps are the complete absence of enablement tests for `FlipStemDirectionAction` and `ToggleTrillAction`. Both delegate to `ctrl.canFlipStemDirection()` / `ctrl.canToggleTrill()` after the flag checks, and that extra guard is the most specific piece of logic these actions own. No test at any level covers whether these actions correctly enable/disable based on their predicates; `ScoreViewControllerCommandHandlerTest` tests the downstream command handler but not the action's own enablement flow. Both belong at unit level (mock `canFlipStemDirection` / `canToggleTrill` on the mocked `ScoreViewController`), following the pattern established by `TupletActionTest`. Similarly, `TupletAction.songDidChange` and `documentDidLoad` handlers both delegate to `handleChange` — the same enablement logic path — but are never directly exercised in tests, leaving a surviving mutant risk if either handler body is deleted.

`DurationArticulationAction` and `ForceArticulationAction` have byte-for-byte identical `applyToElement` and `matchesElement` bodies, differing only in the captured `articulationType` field. This is not a test defect but a refactoring opportunity worth a GitHub issue: the two classes could share a common `ToggleArticulationAction` base, reducing duplication and the risk of one class drifting from the other.

`DotAction.applyToElement` uses `dotLevel.ordinal() + 1` as a business value for the dot count. Relying on enum ordinal order for correctness violates the no-magic-numbers rule and makes the code brittle — if `DotLevel.DOUBLE` is ever reordered, the mutation silently produces the wrong dot count without any compile error. No existing test would catch this because the tests assert on the enum constants' names, not the ordinal. This is worth a GitHub issue (use explicit constants `SINGLE_DOT_COUNT = 1` / `DOUBLE_DOT_COUNT = 2` and match in the switch).

`AccidentalInParensAction.applyToElement(el, true)` on an element with no accidental is a silent no-op (because `setAccidentalInParentheses` guards on `getAccidental() != null`). The existing test `testApplyToNoteAppliesParentheses` always pre-sets an accidental, so the guard is never exercised — a mutant deleting that guard would survive.

`AccidentalAction.actionPerformed` contains a path that starts `PlayThread` when `PLAY_SELECTED_NOTE` pref is set. This path is completely untested. It uses `requireScoreView().getSingleSelectedElement()` which can return `null`, and the null-guard (`if (element != null && element.getType().isNote())`) is the only protection. No test verifies this guard.

`ElementTypeAction.appliesTo` has a three-branch structure: DURATION (uses `isDuration()`), BREATH_MARK (uses `== BREATH_MARK`), and NON_DURATION-barline-or-repeat (uses `isBarLine() || isRepeat()`). Only the DURATION branch and the NON_DURATION barline branch are tested. The BREATH_MARK branch — specifically that a BREATH_MARK action does NOT apply to a SINGLE_BARLINE and vice versa — is completely untested.

No e2e tests exist at any level for `FlipStemDirectionAction`, `ToggleTrillAction`, or `TupletAction` (the mutation side is tested in `MusicEditOperationsMutationTest`, but not the action-to-command dispatch path). The command-handler tests in `ScoreViewControllerCommandHandlerTest` cover the downstream effect but cannot detect a broken or missing `MessageCenter.post()` call in the actions themselves.

### 5C. markings — dynamics / annotation / structural — AnnotationAction, AddDynamicsAction, RemoveDynamicsAction, DynamicMarkingAction, BeatChangeAction, TempoChangeAction, KeySignatureChangeAction, FirstSecondEndingAction, FinalTerminalAction, PreviewElementAction

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| AnnotationAction | constructor sets `Flag.REQUIRES_SINGLE_SELECTION`, `OPENS_DIALOG`, `DISABLE_IN_GRACE_MODE`, etc. | none | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` (instantiation only) | adequate | keep (flag audit is sufficient; `actionPerformed` opens a dialog — no model mutation to assert) |
| AnnotationAction | `actionPerformed` opens `AnnotationDialog` | none | — | adequate (none warranted) | keep — pure dialog open, no model logic |
| AddDynamicsAction | constructor correctly wires `isCrescendo` field and factory methods | none | — | adequate (none warranted) | keep — trivial factory |
| AddDynamicsAction | `isCrescendo()` returns the value the action was constructed with | unit | — | missing | write unit: `createCrescendoAction` → `isCrescendo()==true`; `createDiminuendoAction` → `isCrescendo()==false` |
| AddDynamicsAction | `actionPerformed` posts `AddDynamicsCommand(isCrescendo)` with correct flag | unit | `ScoreViewControllerCommandHandlerTest.testHandleAddDynamicsEmitsOneAddition` (wiring test) | wrong-level | the command-handler test verifies wiring at the `ScoreViewController` layer but never exercises `AddDynamicsAction.actionPerformed` directly; add a unit test asserting the right `AddDynamicsCommand` is posted |
| AddDynamicsAction | enablement: `Flag.REQUIRES_MULTIPLE_SELECTION`, `DISABLE_IN_REST_MODE`, `DISABLE_WHEN_BAR_SELECTED` | unit | — | missing | write unit tests for the critical flag combinations (no selection → disabled; single note → disabled; multiple notes → enabled; rest mode → disabled) |
| RemoveDynamicsAction | `musicSelectionDidChange`: enabled only when selection contains at least one crescendo or diminuendo span | unit | — | missing | write unit: selection with no hairpins → disabled; selection overlapping a crescendo → enabled; selection overlapping a diminuendo → enabled |
| RemoveDynamicsAction | `actionPerformed` posts `RemoveDynamicsCommand` | unit | `ScoreViewControllerCommandHandlerTest.testHandleRemoveDynamicsEmitsRemovals` (handler wiring) | wrong-level | same gap as AddDynamicsAction — the action's `actionPerformed` path is not directly exercised; add unit test asserting `RemoveDynamicsCommand` is posted |
| DynamicMarkingAction | `applyToElement`: add dynamic to note with no existing dynamic | unit | `DynamicMarkingActionTest.ApplyToElement.testAddDynamicToNoteWithNone` | adequate | keep |
| DynamicMarkingAction | `applyToElement`: no-op when `selected=false` and no existing dynamic | unit | `DynamicMarkingActionTest.ApplyToElement.testNoOpWhenNotSelectedAndNoDynamic` | adequate | keep |
| DynamicMarkingAction | `applyToElement`: replaces different dynamic type | unit | `DynamicMarkingActionTest.ApplyToElement.testReplaceDifferentType` | adequate | keep |
| DynamicMarkingAction | `applyToElement`: toggles off same dynamic type (idempotent remove) | unit | `DynamicMarkingActionTest.ApplyToElement.testToggleOffSameType` | adequate | keep |
| DynamicMarkingAction | `applyToElement`: `selected=false` with an existing dynamic of same type — removes it (implicit deselect path) | unit | — | missing | write unit: note with FORTE dynamic, `applyToElement(note, false)` → no attachment remains |
| DynamicMarkingAction | `matchesElement`: matches when note has same dynamic type | unit | `DynamicMarkingActionTest.MatchesElement.testMatchesWhenNoteHasMatchingType` | adequate | keep |
| DynamicMarkingAction | `matchesElement`: no match when note has different type | unit | `DynamicMarkingActionTest.MatchesElement.testNoMatchWhenNoteHasDifferentType` | adequate | keep |
| DynamicMarkingAction | `matchesElement`: no match when no dynamic | unit | `DynamicMarkingActionTest.MatchesElement.testNoMatchWhenNoteHasNoDynamic` | adequate | keep |
| DynamicMarkingAction | `updateEnabledState`: disabled when no selection | unit | `DynamicMarkingActionTest.EnabledState.testDisabledWhenNoSelection` | adequate | keep |
| DynamicMarkingAction | `updateEnabledState`: disabled when multiple notes selected | unit | `DynamicMarkingActionTest.EnabledState.testDisabledWhenMultipleNotesSelected` | adequate | keep |
| DynamicMarkingAction | `updateEnabledState`: enabled when single note not in hairpin | unit | `DynamicMarkingActionTest.EnabledState.testEnabledWhenSingleNoteNotInHairpin` | adequate | keep |
| DynamicMarkingAction | `updateEnabledState`: disabled when note is inside crescendo range | unit | `DynamicMarkingActionTest.EnabledState.testDisabledWhenNoteInsideCrescendoRange` | adequate | keep |
| DynamicMarkingAction | `updateEnabledState`: disabled when note is inside diminuendo range | unit | `DynamicMarkingActionTest.EnabledState.testDisabledWhenNoteInsideDiminuendoRange` | adequate | keep |
| DynamicMarkingAction | `updateEnabledState`: disabled when note is at hairpin boundary (anchor or end index inclusive) | unit | — | missing | write unit: note at index 0 with hairpin [0..3] → disabled; note at index 3 → disabled (boundary inclusivity of `isInHairpinRange`) |
| DynamicMarkingAction | `ActionGroup` selection/deselection behavior | unit | `DynamicMarkingActionTest.ActionGroupBehavior.*` (2 tests) | adequate | keep |
| DynamicMarkingAction | add/remove dynamic with real model via e2e — `testFermataAndDynamicCoexistOnSameNote` | e2e | `DynamicsMarkingTest.Regression.testFermataAndDynamicCoexistOnSameNote` | adequate | keep |
| DynamicMarkingAction | crescendo added via real UI pipeline | e2e | `DynamicsMarkingTest.Regression.testCrescendoStillWorks` | adequate | keep |
| DynamicMarkingAction | diminuendo added via real UI pipeline | e2e | `DynamicsMarkingTest.Regression.testDiminuendoStillWorks` | adequate | keep |
| BeatChangeAction | constructor sets `Flag.REQUIRES_SINGLE_SELECTION`, `OPENS_DIALOG`, `DISABLE_IN_GRACE_MODE`, etc. | none | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` | adequate | keep |
| BeatChangeAction | `actionPerformed` opens `BeatChangeDialog` | none | — | adequate (none warranted) | keep — pure dialog open |
| TempoChangeAction | constructor flag set | none | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` | adequate | keep |
| TempoChangeAction | `musicSelectionDidChange`: enabled only when a single non-bar element is selected (`canChangeTempo`) | unit | — | missing | write unit: no selection → disabled; single bar element → disabled (DISABLE_WHEN_BAR_SELECTED flag); single note → enabled |
| TempoChangeAction | `actionPerformed` opens `TempoChangeDialog` | none | — | adequate (none warranted) | keep — pure dialog open |
| KeySignatureChangeAction | constructor flag set | none | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` | adequate | keep |
| KeySignatureChangeAction | `musicSelectionDidChange`: enabled only when a line is selected (`getSelectedLine() != -1`) | unit | — | missing | write unit: no line selected → disabled; line selected → enabled |
| KeySignatureChangeAction | `musicSelectionDidChange`: does NOT require `ctrl != null` guard (uses `message.getScoreView()` directly, not null-safe) | unit | — | missing | potential NPE: `musicSelectionDidChange` calls `message.getScoreView().getSelectedLine()` without null-checking the score view (unlike `TempoChangeAction` which guards on `ctrl != null`). Write unit verifying the guard path, and file a bug. |
| KeySignatureChangeAction | `actionPerformed` opens `KeySignatureChangeDialog` | none | — | adequate (none warranted) | keep |
| FirstSecondEndingAction | `validate`: sets `cachedResult` and calls `setEnabled(result.isValid())` | unit | — | missing | write unit: mocked `ctrl` returning a valid result → action enabled + cachedResult set; invalid result → disabled |
| FirstSecondEndingAction | `getCachedResult()` returns null before `validate()` is called | unit | — | missing | write unit asserting initial `@Nullable` contract |
| FirstSecondEndingAction | `actionPerformed` posts `FirstSecondEndingCommand` | unit | `ScoreViewControllerCommandHandlerTest.testHandleFirstSecondEndingEmitsOneNotificationWithBarlineAndEnding` (handler level) | wrong-level | the action's own `actionPerformed` is never directly exercised; the handler test uses reflection to inject `cachedResult` into `Actions.MAKE_ENDING_ACTION` and invokes `handleFirstSecondEnding` directly, bypassing `actionPerformed`; add unit for `actionPerformed` |
| FirstSecondEndingAction | `canMakeFirstSecondEnding` — 4-stage validation logic (structure, overlap, enclosing repeat, preceding element) | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.*` (4 tests), `CanMakeFirstSecondEndingAtSongEnd.*` (1 test), `HasEnclosingRepeatRules.*` (4 tests) | adequate | keep |
| FirstSecondEndingAction | `makeFirstSecondEnding` mutation — INSERT_BARLINE path | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingEmitsElementInsertionAndRangeElementAddition` | adequate | keep |
| FirstSecondEndingAction | `makeFirstSecondEnding` mutation — NONE path (existing barline) | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingWithExistingLeadingBarlineEmitsOnlyRangeElementAddition` | adequate | keep |
| FinalTerminalAction | `enableFromSongState`: `isSelected` reflects `currentTerminalType()` — FINAL_DOUBLE_BARLINE | unit | `FinalBarlineActionEnablementTest.testActionSelectedWhenTerminalIsFinalDoubleBarline` | adequate | keep |
| FinalTerminalAction | `enableFromSongState`: not selected when terminal is REPEAT_RIGHT | unit | `FinalBarlineActionEnablementTest.testActionNotSelectedWhenTerminalIsRightRepeat` | adequate | keep |
| FinalTerminalAction | `enableFromSongState`: selected again after replace back to FINAL_DOUBLE_BARLINE | unit | `FinalBarlineActionEnablementTest.testActionSelectedAgainAfterReplaceBackToFinalBarline` | adequate | keep |
| FinalTerminalAction | `actionPerformed` calls `song.replaceTerminal(type)` without confirm dialog | unit | `FinalBarlineActionEnablementTest.testActionReplacesTerminalDirectlyWithoutConfirm` | adequate | keep |
| FinalTerminalAction | menu item for FINAL_DOUBLE_BARLINE replaces terminal without confirm | unit | `BarlineMenuTest.testFinalDoubleBarlineItemReplacesTerminalWithoutConfirm` | redundant | covered by `FinalBarlineActionEnablementTest`; the menu test adds no new behavior; keep for menu-wiring assurance, or merge |
| FinalTerminalAction | menu item for REPEAT_RIGHT replaces terminal without confirm | unit | `BarlineMenuTest.testFinalRightRepeatItemReplacesTerminalWithoutConfirm` | adequate | keep (distinct from the double-barline test) |
| FinalTerminalAction | radio selection reflects current terminal — FINAL_DOUBLE_BARLINE selected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForFinalBarline` | adequate | keep |
| FinalTerminalAction | radio selection reflects current terminal — REPEAT_RIGHT selected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForRightRepeat` | adequate | keep |
| FinalTerminalAction | `replaceTerminal` no-op when incoming type matches current | unit | — | missing | write unit: call `replaceTerminal(FINAL_DOUBLE_BARLINE)` on a fresh Song (already FINAL_DOUBLE_BARLINE) → terminal unchanged, no mutation emitted |
| FinalTerminalAction | `replaceTerminal` throws `IllegalArgumentException` for invalid type | unit | — | missing | write unit: `replaceTerminal(CROTCHET)` → `IllegalArgumentException` |
| PreviewElementAction | `actionPerformed`: `toggleOnKeyboardShortcut` called when source is `JRootPane` | unit | — | missing | write unit: action with `JRootPane` as source → `isSelected()` toggled |
| PreviewElementAction | `actionPerformed`: `applyToSelectionIfActive()` returns true → posts no `UpdatePreviewElementCommand` | unit | — | missing | write unit: mock coordinator with active selection → `UpdatePreviewElementCommand` not posted |
| PreviewElementAction | `actionPerformed`: no active selection → posts `UpdatePreviewElementCommand` | unit | — | missing | write unit: mock coordinator with no selection → `UpdatePreviewElementCommand` posted |
| PreviewElementAction | class is non-final superclass for all duration/note insertion actions; base behavior is inherited | none | `PreviewElementManagerAttachmentTest`, `PreviewElementManagerTerminalRoutingTest`, etc. (manager-level tests) | adequate | keep — manager tests exercise the insertion pipeline downstream |

**5C notes (quality concerns):**

`DynamicMarkingAction.applyToElement(note, false)` when a same-type dynamic exists: the branch `existing != null && isSameType && selected==false` falls through the `removeAttachment(existing)` call then skips the `if (selected)` re-add, so the dynamic is silently removed. This is a third logical branch of `applyToElement` that `DynamicMarkingActionTest` does not test (all existing tests call with `selected=true` or with no existing dynamic when `selected=false`).

`KeySignatureChangeAction.musicSelectionDidChange` calls `message.getScoreView().getSelectedLine()` with no null-guard on the score view, unlike the pattern used by `TempoChangeAction` which guards on `ctrl != null`. If `getScoreView()` returns null the method will throw NPE. This is a probable production bug — worth a GitHub issue. The method also skips the guard `if (updateEnabledState())` on the `setEnabled` call that the other sibling actions use, though the current implementation passes `updateEnabledState()` implicitly before setting enabled.

`AddDynamicsAction` and `RemoveDynamicsAction` have no unit test for `actionPerformed`. `ScoreViewControllerCommandHandlerTest` does exercise the downstream handler for both, but never fires the action itself. A mutant that changed the `isCrescendo` argument in `AddDynamicsAction.actionPerformed(e)` would survive all existing tests.

`FirstSecondEndingAction.actionPerformed` is tested only via handler-reflection injection in `ScoreViewControllerCommandHandlerTest.testHandleFirstSecondEndingEmitsOneNotificationWithBarlineAndEnding`, which bypasses the action's `actionPerformed` entirely. The action itself is never invoked.

`DynamicsMarkingTest` has `@Order(5)` on its only nested class with no `@Order(1-4)` siblings — a structural artifact of deleted tests. The `@TestClassOrder(ClassOrderer.OrderAnnotation.class)` annotation on the outer class is now a no-op.

`FinalTerminalAction.replaceTerminal` no-op branch (same-type guard) and `IllegalArgumentException` for non-terminal types are untested. The no-op is particularly important: a mutant removing the early return would cause spurious mutation records.

`TempoChangeAction.musicSelectionDidChange` enablement for the bar-element case is untested — `Flag.DISABLE_WHEN_BAR_SELECTED` is declared in the constructor flags but the custom `canChangeTempo()` chain (`SelectionCoordinator.canChangeTempo` checks `getSingleSelectedElement() != null` without type filtering) means a bar element with a single selection would pass `canChangeTempo` while the flag rejects it at the `updateEnabledState` layer. The interaction between the flag and the custom predicate is unverified.

### 5D. clipboard / selection / line — CopyAction, CutAction, PasteAction, PasteboardAction, DeleteAction, DeselectAction, SelectLineAction, InsertLineAction, EditLyricAction, CycleModeAction, RestModeAction, ToggleNotationAction

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| PasteboardAction | `musicSelectionDidChange`: COPY/CUT enabled iff `selectionSize > 0`; PASTE enabled iff `pasteboardSize > 0`; DELETE deferred to `DeleteAction.updateEnabledState()` | unit | — | missing | write unit tests: mock `MusicSelectionDidChangeNotification` and `ScoreView.getPasteboardSize()`/`getSelectionSize()`; verify each branch |
| PasteboardAction | `actionPerformed` dispatches `PasteboardOpCommand(op)` for all four ops | unit | — | missing (wrong-level) | action dispatch not tested; write unit test verifying `MessageCenter.post(new PasteboardOpCommand(op))` for COPY, CUT, DELETE, PASTE (mock `MessageCenter`) |
| CopyAction | inherits `PasteboardAction` with `Operation.COPY`; no own logic | none | — | none | trivial delegating constructor |
| CutAction | inherits `PasteboardAction` with `Operation.CUT`; no own logic | none | — | none | trivial delegating constructor |
| PasteAction | inherits `PasteboardAction` with `Operation.PASTE`; no own logic | none | — | none | trivial delegating constructor |
| DeleteAction | `updateEnabledState`: enabled iff lyricSelection OR activeSelection OR glissandoSelection OR canDeleteLine | unit | `DeleteActionTest.testDeleteEnabledForLyricSelection` | inadequate | only lyric-selection branch tested; missing: activeSelection branch, glissandoSelection branch, canDeleteLine branch, and all-false (disabled) case |
| DeleteAction | `actionPerformed` (inherited): dispatches `PasteboardOpCommand(DELETE)` | unit | — | missing (wrong-level) | same gap as PasteboardAction above — mutant in action body survives |
| DeselectAction | `actionPerformed` dispatches `DeselectCommand` | unit | — | missing (wrong-level) | write unit test verifying `MessageCenter.post(new DeselectCommand())` |
| SelectLineAction | `actionPerformed` dispatches `SelectLineCommand` | unit | — | missing (wrong-level) | write unit test verifying `MessageCenter.post(new SelectLineCommand())` |
| InsertLineAction | `getActionCommand(shift)` branch: ADD→"add-line", 0→"insert-line-before", 1→"insert-line-after" | unit | — | missing | write unit test asserting all three action-command strings |
| InsertLineAction | `actionPerformed` dispatches `InsertLineCommand(shift)` for all three variants | unit | — | missing (wrong-level) | write unit test verifying `MessageCenter.post(new InsertLineCommand(shift))` for shift ∈ {-1, 0, 1} |
| EditLyricAction | `enableFromSelection`: false when no single selected element; delegates to `LyricEditor.isLyricTargetEligible` | unit | `EditLyricActionGraceNoteTest` (4 tests) | adequate | keep |
| EditLyricAction | `actionPerformed`: deselects then opens `LyricEditor.openOn` for the selected element | unit | `EditLyricActionTest.testActionPerformedOpensEditorForSelectedElement` | adequate | keep |
| EditLyricAction | `actionPerformed`: throws `IllegalStateException` when fired with no selection (REQUIRES_SINGLE_SELECTION violated) | unit | — | missing | write unit test asserting ISE |
| EditLyricAction | carries `DISABLE_WHEN_EDITING_TEXT` flag | unit | `LyricEditorActionAuditTest`, `EditLyricActionTest.testCarriesDisableWhenEditingTextFlag` | adequate | keep |
| EditLyricAction | `enableFromTextEditingState`: false when editing text | unit | `EditLyricActionTest.testDisabledWhenEditingText` / `testEnabledWhenNotEditingText` | adequate | keep |
| CycleModeAction | `actionPerformed`: increments `currentIndex` modulo 2, then calls `MODES[newIndex].perform()` | unit | — | missing | write unit test: first call → index=1 (SELECT mode performed); second call → index=0 (EDIT mode performed) |
| CycleModeAction | `modeDidChange`: skips adjustment modes; syncs `currentIndex` to the matching `ModeAction` in MODES | unit | — | missing | write unit test: send EDIT mode notification → index=0; SELECT mode → index=1; adjustment mode → index unchanged |
| CycleModeAction | carries `DISABLE_WHEN_EDITING_TEXT` flag | unit | `LyricEditorActionAuditTest` | adequate | keep |
| RestModeAction | `actionPerformed`: `toggleOnKeyboardShortcut` toggles selected state when triggered from `JRootPane`; posts `RestModeDidChangeNotification` | unit | — | missing | write unit test: keyboard source (JRootPane) → selected state toggled AND notification posted; button source → state NOT toggled (handled by Swing) AND notification posted |
| RestModeAction | enablement predicate: REQUIRES_EMPTY_SELECTION AND DISABLE_WHEN_PLAYING AND DISABLE_IN_ADJUSTMENT_MODE AND DISABLE_WHEN_BAR_SELECTED AND ENABLE_WHEN_DURATION_SELECTED AND DISABLE_WHEN_EDITING_TEXT AND DISABLE_IN_GRACE_MODE | unit | `LyricEditorActionAuditTest` (only DISABLE_WHEN_EDITING_TEXT) | inadequate | ENABLE_WHEN_DURATION_SELECTED branch and REQUIRES_EMPTY_SELECTION branch untested |
| RestModeAction | `Reflectable.appliesTo`: returns true iff `element.getType().isDuration()` | unit | — | missing | write test (note→true, barline→false) |
| RestModeAction | `Reflectable.matchesElement`: returns true iff `element.getType().isRest()` | unit | — | missing | write test (rest→true, note→false) |
| ToggleNotationAction | `handleChange` (beam): enabled iff `REQUIRES_MULTIPLE_SELECTION` met AND `canToggleBeaming()` true | unit | `ToggleConflictTest` (canToggleBeaming via LineSelectionState), `ScoreViewControllerCommandHandlerTest.testHandleToggleBeamEmitsOneBeamingAddition` (handler) | wrong-level | `handleChange` is never invoked on ToggleNotationAction in any unit test; write unit test for `handleChange` with mocked `ScoreViewController` |
| ToggleNotationAction | `handleChange` (tie): enabled iff `REQUIRES_MULTIPLE_SELECTION` met AND `canToggleTie()` true | unit | `ToggleConflictTest` (canToggleTie only), `ScoreViewControllerCommandHandlerTest.testHandleToggleTieEmitsOneTieAddition` | wrong-level | same gap as beam: write unit test |
| ToggleNotationAction | `actionPerformed` (beam): dispatches `ToggleBeamCommand` via `commandFactory.get()` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleBeamEmitsOneBeamingAddition` | wrong-level | command handler tested, not the action dispatch; write unit test verifying `MessageCenter.post(new ToggleBeamCommand())` |
| ToggleNotationAction | `actionPerformed` (tie): dispatches `ToggleTieCommand` via `commandFactory.get()` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTieEmitsOneTieAddition` | wrong-level | same gap; write unit test |
| ToggleNotationAction | carries `DISABLE_WHEN_EDITING_TEXT` flag | unit | `LyricEditorActionAuditTest` | adequate | keep |
| ToggleNotationAction | re-evaluates enabled state on `songDidChange` and `documentDidLoad` notifications | unit | — | missing | write unit tests (song-change → canToggle predicate re-evaluated; document-load → re-evaluated) |

**5D notes (quality concerns):** **Cross-cutting wrong-level pattern (confirmed here, matches 5B/5C/5E):** `CopyAction`, `CutAction`, `PasteAction` (all via `PasteboardAction`), `DeleteAction`, `DeselectAction`, `SelectLineAction`, `InsertLineAction`, and both `ToggleNotationAction` variants have `actionPerformed` bodies that post a single command to `MessageCenter`; every one is untested at the action level — existing tests cover only the downstream command handler or `ClipboardManager`, never the action dispatch, so a mutant swapping/deleting the posted command class survives. **`DeleteAction.updateEnabledState` has three untested branches** (activeSelection, glissandoSelection, canDeleteLine) plus the all-false disabled case; only the lyric-selection branch is covered. **`CycleModeAction.modeDidChange`** has no test on any code path; `currentIndex` can silently desync from the real mode if a mode not in `MODES` is set. **`RestModeAction`'s `ENABLE_WHEN_DURATION_SELECTED`** — the only enabling-rather-than-disabling flag in the set — is unexercised; a mutant removing it would leave rest mode disabled when a duration is selected with no test failing. **`ToggleNotationAction.handleChange`** sits between the `LineSelectionState` predicate level (`ToggleConflictTest`) and the command-handler level (`ScoreViewControllerCommandHandlerTest`) with zero coverage of its own enablement chain. **`EditLyricAction.actionPerformed` ISE guard** (throws when `getSingleSelectedElement()` is null after firing) is an untested defensive contract — trivial, high-value lock. **Production observation worth a GitHub issue:** `ScoreViewController.handlePaste()` is a dead-code stub (TODO comment only, no body), so `PasteAction` and the PASTE enablement branch are real and enable-able but invoking paste is silently a no-op — paste is unimplemented while appearing enabled whenever the clipboard is non-empty. **Scope note (deferred, not lost):** the command *handlers* these actions post to — `ScoreViewController.handleDelete`/`handleCopy`/`handleCut`/`handlePaste`/`handleDeselect`/`handleSelectLine`/`handleInsertLine` — live in `ui/component` (→ Session 7) and are out of `ui/action` scope. Their status from this read, to carry forward: `handleDelete` lyric+element branches are **adequate** (`DeleteLyricTest`, `ScoreViewControllerTest.DeleteNote` 8 tests, `EndingConfirmsTest`) but its glissando-only and canDeleteLine branches are **missing**; `handleCopy` (clone [begin..end] into `ClipboardManager`, null/empty no-op), `handleCut` (copy-then-`withModification(handleDelete)`), `handleDeselect` (focus-owner guard), `handleSelectLine` (null/non-null state), and `handleInsertLine` (ADD vs before/after, and the no-line-selected `OptionDialogs.showErrorMessage` error branch) are all **missing** and should be audited/written under Session 7.

### 5E. file / app lifecycle — NewAction, OpenAction, OpenRecentAction, ClearRecentsAction, SaveAction, SaveAsAction, CloseWindowAction, QuitAction, PrintAction

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `NewAction` | `actionPerformed` posts `NewFileCommand` on the message bus | unit | none | missing | write test: subscribe handler; call `actionPerformed`; assert `NewFileCommand` was posted |
| `NewAction` | constructor sets `DISABLE_WHEN_PLAYING` flag | unit | none | missing | assert `hasFlag(Flag.DISABLE_WHEN_PLAYING) == true` |
| `OpenAction` | `actionPerformed` posts `ShowOpenDialogCommand` on the message bus | unit | none | missing | write test: subscribe handler; call `actionPerformed`; assert `ShowOpenDialogCommand` was posted |
| `OpenAction` | constructor sets `DISABLE_WHEN_PLAYING` and `OPENS_DIALOG` flags | unit | none | missing | assert both flags present |
| `OpenRecentAction` | `actionPerformed` — path exists → posts `OpenFileCommand` with correct file | unit | none | missing | write test: temp file on disk; call `actionPerformed`; assert command posted with matching path |
| `OpenRecentAction` | `actionPerformed` — path does not exist → shows error, calls `RecentDocumentsManager.remove`, does NOT post `OpenFileCommand` | unit | none | missing | write test: non-existent path; mock `OptionDialogs`; assert `remove` called and command not posted |
| `OpenRecentAction` | constructor sets `DISABLE_WHEN_PLAYING` and `DISABLE_IN_GRACE_MODE` flags | unit | none | missing | assert both flags present via `hasFlag()` |
| `ClearRecentsAction` | `actionPerformed` calls `RecentDocumentsManager.clear()` | unit | none | missing | write test: populate recents, call `actionPerformed`, assert list empty |
| `ClearRecentsAction` | constructor sets `DISABLE_WHEN_PLAYING` and `DISABLE_IN_GRACE_MODE` flags | unit | none | missing | assert both flags present |
| `SaveAction` | constructor sets **no** disabling flags (Save is always enabled, even during playback / in grace mode) | unit | none | missing | assert `!hasFlag(DISABLE_WHEN_PLAYING)` etc. — documents the deliberate design decision |
| `SaveAction` | `perform(source)` overrides base to bypass the message bus and return `mainFrame.save()` result synchronously | unit | none | missing | mock `MainFrame.save()` → true/false; call `perform(null)`; assert returned boolean matches |
| `SaveAction` | `actionPerformed` posts `SaveCommand` on the message bus | unit | none | missing | write test: subscribe handler; call `actionPerformed`; assert `SaveCommand` posted |
| `SaveAsAction` | `actionPerformed` posts `SaveAsCommand` on the message bus | unit | none | missing | write test: subscribe handler; call `actionPerformed`; assert `SaveAsCommand` posted |
| `SaveAsAction` | constructor sets `DISABLE_WHEN_PLAYING`, `DISABLE_IN_GRACE_MODE`, and `OPENS_DIALOG` flags | unit | none | missing | assert all three flags present |
| `CloseWindowAction` | `actionPerformed` calls `Shutdown.now()` (same entry point as `QuitAction`) | e2e | `ShutdownTest.quitActionTriggersShutdown` (tests `QuitAction`, not `CloseWindowAction`) | missing | add e2e test: dispatch `CloseWindowAction.actionPerformed` on clean doc; assert sentinel fires |
| `QuitAction` | `actionPerformed` calls `Shutdown.now()` | e2e | `ShutdownTest.quitActionTriggersShutdown` | adequate | keep |
| `QuitAction` | constructor sets platform-appropriate name and accelerator (none on macOS, Alt+F4 elsewhere) | unit | none | missing | assert `NAME` and accelerator match platform |
| `QuitAction` | constructor sets no disabling flags (quit always enabled) | unit | none | missing | assert `!hasFlag(DISABLE_WHEN_PLAYING)` etc. |
| `PrintAction` | `actionPerformed` posts `PrintCommand` on the message bus | unit | none | missing | write test: subscribe handler; call `actionPerformed`; assert `PrintCommand` posted |
| `PrintAction` | constructor sets `DISABLE_WHEN_PLAYING`, `DISABLE_IN_GRACE_MODE`, and `OPENS_DIALOG` flags | unit | none | missing | assert all three flags present |

**5E notes (quality concerns):** Every lifecycle action's `actionPerformed` is a thin bus post (`NewFileCommand`/`ShowOpenDialogCommand`/`OpenFileCommand`/`SaveCommand`/`SaveAsCommand`/`PrintCommand`) or a `Shutdown.now()` call, and **none** has a dispatch-level test — the same package-wide `missing`/`wrong-level` command-dispatch gap found in 5B/5C/5D: a mutant changing which command an action posts would survive. The command classes themselves are pure data-holder `Message` subclasses (none warranted). Highest action-layer risks: (1) `OpenRecentAction.actionPerformed`'s missing-file branch (detect non-existence → error → `RecentDocumentsManager.remove` → no open) is the *only* production caller of `RecentDocumentsManager.remove`, which Session 4 §4E already flagged as untested; (2) `SaveAction.perform(source)` overrides `UIAction.perform()` to bypass the bus and return `mainFrame.save()` synchronously — a contract `showSaveDialog` depends on to decide whether shutdown proceeds; if a refactor drops the override, save failures would silently report success, and no test pins this; (3) `CloseWindowAction.actionPerformed` is a documented quit entry point identical to `QuitAction` yet has zero coverage (`ShutdownTest` exercises only `QuitAction`). **Scope note (deferred, not lost):** the substantive save/confirm/lifecycle *decision* logic these actions trigger lives in `MainFrame.save()`/`saveCurrentFile()`/`saveAsNewFile()`/`showSaveDialog()`/`handleNewFile()`/`handleOpenFile()`/`getDisplayName()`/`updateTitle()` (package `ui/component` → Session 7) and `Shutdown` (`lifecycle` → Session 12); the sub-audit enumerated those but they are out of `ui/action` scope and would double-count if listed here. Carry forward for Session 7: `MainFrame.showSaveDialog()`'s four branches (clean fast-path, Save, Don't-Save, Cancel/`CLOSED_OPTION`) and the three save-path methods (`IOException` handling, `isModified` clearing, `RecentDocumentsManager.add`, `DocumentWasSavedNotification`) are the sole guard against data loss and are entirely untested except indirectly via `ShutdownTest`'s dialog-suppression e2e (`windowCloseOnDirtyDocCancelKeepsAppAlive`, `windowCloseOnCleanDocProgressesPastSaveCheck` — both adequate). **Production observation worth a GitHub issue (in `MainFrame`, not `ui/action`):** `MainFrame.saveAsNewFile()` passes an empty string as the suggested filename when `currentFile != null`, forcing the user to retype the document name on every Save-As of an existing file (fix: `currentFile == null ? scoreView.getSuggestedFileName() : FileUtils.getPathWithoutExtension(currentFile.getName())`).

### 5F. export + misc dialog-open — ExportABCAction, ExportImageAction, ExportMidiAction, ExportPDFAction, ExportSVGAction, AboutOpenAction, PreferencesOpenAction, TipAction

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ExportABCAction | `actionPerformed` — opens PlatformFileDialog then calls `writeABC`; null-return exits early; IOException shows error dialog | none | — | none | pure dialog-open + file-chooser wiring with no branching logic worth testing independently; the meaningful logic is in the static helper methods below |
| ExportABCAction | `determineSongUnitLength(song)` — counts pitched-note durations, returns most-frequent duration; returns `PPQ*4` for empty song | unit | — | missing | write unit tests: empty song → PPQ*4; single-note song → that note's duration; tie-break between two equal counts (either is acceptable) |
| ExportABCAction | `getUnitLengthMap(song)` — accumulates pitched-note default-duration histogram (skips rests, barlines) | unit | — | missing | write unit test asserting map contents: mixed notes produce correct counts |
| ExportABCAction | `translateKey(keyType, number)` — index into SHARP_KEYS or FLAT_KEYS, append " major" | unit | — | missing | write test for SHARPS index 0..7 and FLATS index 0..7; assert known values (e.g. SHARPS/2→"D major", FLATS/2→"Bb major") |
| ExportABCAction | `translateTempo(tempo)` — `shouldShowTempo()==false` path returns quoted description; `true` path returns fraction=BPM "description" | unit | — | missing | write both branches; verify the closing `'` (not `"`) in the true-path output (potential display inconsistency — see notes) |
| ExportABCAction | `translateUnitLength(duration, unitLength)` — GCD reduction to lowest-terms Fraction | unit | — | missing | write test: known pairs (e.g. 480/1920→1/4, 720/1920→3/8) |
| ExportABCAction | `Fraction.asAbcString()` — `numerator + "/" + denominator` | unit | — | missing | write test: Fraction(1,4) → "1/4" |
| ExportABCAction | `translatePitch(staffPosition)` — staff-position to ABC letter + octave commas/ticks; boundary at 0/-7/+7 | unit | — | missing | write test for staffPosition=0 (B), ±1, ±7, ±8 including octave comma/tick appending |
| ExportABCAction | `getPitchType(staffPosition)` — maps staff-position to 0–6; boundary at 0 and every ±7 | unit | — | missing | write test: sp=0→0(B), sp=1→6(A), sp=-1→1(C), sp=7→0, sp=14→0 |
| ExportABCAction | `translateAccidental(accidental)` — maps each accidental component to ACCIDENTAL_MAP entries | unit | — | missing | write test for NATURAL, FLAT, SHARP, DOUBLE_SHARP; multi-component accidentals |
| ExportABCAction | `translateNoteLength(duration, songUnitLength)` — empty string for 1/1; "/N" for 1/N; "N" for N/1; fraction otherwise | unit | — | missing | write test for each of the 4 branches with known duration/unitLength pairs |
| ExportABCAction | `translateRepeatAndBarLine(noteType)` — each barline/repeat type → correct ABC symbol | unit | — | missing | write test: all 6 cases + default ("") for a non-barline type |
| ExportABCAction | `translateDecorations(note)` — accent, staccato, fermata, trill (each independently; combinations) | unit | — | missing | write test per articulation type and a combined case |
| ExportABCAction | `translateAnnotation(annotation)` — null→""; above vs below position disambiguation; `^`/`_` prefix | unit | — | missing | write test: null, yPos closer to ABOVE, yPos closer to BELOW |
| ExportABCAction | `translateNote(note, songUnitLength)` — composes tempo-change prefix, annotation, decorations, pitch, note-length; grace-note brackets; rest `z`; barline/repeat; breath mark | unit | — | missing | write tests for pitched note, rest, barline, breath mark, grace note, note with TempoChangeAttachment |
| ExportABCAction | `translateLine(line, songUnitLength)` — beam spacing, ending brackets, tuplet prefix, glissando slurs, tie dash, ending marker | unit | — | missing | write test with a line containing known elements; assert exact output string |
| ExportABCAction | `isGlissandoBegin(line, n)` — element has non-null glissando | unit | — | missing | write test (true/false cases) |
| ExportABCAction | `isGlissandoEnd(line, n)` — n>0 AND preceding element has glissando | unit | — | missing | write test: n=0→false, n=1 with/without glissando on preceding note |
| ExportABCAction | `translateLyrics(line)` — STOP/CONTINUE extend → `_`; START extend → `_` suffix; grace note → `\` suffix; BEGIN/MIDDLE syllabic → `-` separator; SINGLE/END → space | unit | — | missing | write test for each lyric path; use a crafted Line with known Lyric values |
| ExportABCAction | `translateSyllable(syllable)` — null/"_"/"−" → ""; non-breaking hyphen → `\-`; normal → unchanged | unit | — | missing | write test for all 4 cases |
| ExportABCAction | `translateSong(writer, song, songUnitLength)` — one `w:` line per staff line | unit | — | missing | write integration-style test: a minimal Song → assert printed output matches expected ABC header + body |
| ExportImageAction | `actionPerformed` — shows multi-format file dialog; persists selected filter index to Prefs; applies jpg/jpeg dual-extension logic; launches ResolutionDialog; builds ExportOptions; dispatches to scoreView/GraphicUtils; 3-way error handling (IOException, OutOfMemoryError, write failure) | none/e2e | — | none | file-chooser + dialog sequence: pure wiring. The only branching with risk is in `ensureExtension` (see ExportImageAction/FileUtils row below). The lazy-init `resolutionDialog` pattern is pure wiring with no logic to assert. |
| ExportImageAction | `FileUtils.ensureExtension` — already-correct extension → unchanged; missing extension → append; jpg case accepts both `.jpg` and `.jpeg` | unit | — | missing | write unit test for `FileUtils.ensureExtension`: (a) file already has `.jpg` → unchanged, (b) file has `.jpeg` → unchanged, (c) file has neither → `.jpg` appended; and same three for `.png`. Note: `ExportImageAction` duplicates the jpg/jpeg logic; `ensureExtension` should subsume it. |
| ExportImageAction | filter-index pref roundtrip: selected filter index saved to `PrefsKey.IMAGE_EXPORT_FILTER` and restored on next construction | unit | — | missing | write unit test mocking Prefs: set filter index in Prefs, construct action, verify initial fileDialog is initialised with that filter index; then simulate selection and verify Prefs.put call |
| ExportMidiAction | `actionPerformed` — shows save dialog with "mid" extension; null-return exits early; passes file to `ExportMidiDialog` | none | — | none | pure dialog-open wiring, no logic to assert |
| ExportPDFAction | `actionPerformed` — shows save dialog, lazy-inits `ExportPDFDialog`, null `getPaperSizeData()` exits early, calls `createPDF` | none | — | none | pure dialog-open wiring; the branching on null paperSizeData is trivial Swing guard |
| ExportPDFAction | `createPDF(data, file)` — static pass-through to `PDFExporter.createPDF` | none | — | none | trivial delegation; PDFExporter tested in Session 4 |
| ExportSVGAction | `actionPerformed` — shows save dialog with "svg" extension; null-return exits early; calls `scoreView.createSVG` | none | — | none | pure wiring; filter description string wrong (see notes) |
| AboutOpenAction | `getNativeMenuTitle()` returns "About" | unit | `ActionsAppMenuTest.testAppMenuActionsHaveCorrectNativeTitles` | adequate | keep |
| AboutOpenAction | `DialogOpenAction` base: lazy-init dialog via reflection; null on construction failure | none | `UIActionFlagBehaviorTest.testDialogOpenActionAutoSetsOpensDialogFlag` | adequate (flag aspect) | keep |
| PreferencesOpenAction | `getNativeMenuTitle()` returns "Settings" | unit | `ActionsAppMenuTest.testAppMenuActionsHaveCorrectNativeTitles` | adequate | keep |
| TipAction | `actionPerformed` — constructs `TipFrame(mainFrame)`; IOException shows error dialog | none | — | none | pure UI construction wiring; no branching logic worth testing |

**5F notes (quality concerns):**

The highest-risk gap in this group is `ExportABCAction`: it contains 18+ `static` or package-private methods implementing a complete ABC 2.1 serializer — key translation, pitch encoding, octave comma/tick notation, note-length GCD reduction, lyric syllabic mapping, decorations, glissando-as-slur rendering, and more. Every one of these is **pure computation with no UI dependency**, yet not a single test exists for any of them. The methods are already `static` and many are package-private, so they are directly testable without any mock. The risk is high: a systematic off-by-one in `getPitchType` or `translatePitch` would produce silently wrong ABC output that passes compilation but plays wrong pitches — exactly the silent-corruption failure mode that justifies the `missing` verdict for every row above. Priority order for writing: `translateKey`, `getPitchType`, `translatePitch`, `translateUnitLength`/`translateNoteLength`, `translateLyrics`/`translateSyllable`, then the composite methods.

**Production bug (worth a GitHub issue):** `ExportSVGAction.actionPerformed` passes the hardcoded string `"Portable Document Format"` as the file-filter description to `PlatformFileDialog.showSaveDialog` — a copy/paste error from the PDF action. The file dialog for SVG export will label its filter "Portable Document Format" rather than something like "Scalable Vector Graphics". This is user-visible. Method: `ExportSVGAction/actionPerformed`, line passing `"Portable Document Format"` as the `filterDescription` argument.

**Production concern (worth noting):** `ExportABCAction.translateTempo` — the `true` branch of `shouldShowTempo()` builds the string with a closing `'` (single-quote) instead of `"` (double-quote) at the end of the description: `"... \"" + description + '\''`. The `false` branch wraps correctly in single quotes. Whether the mixed-quote output (`=120 "Allegro'`) is valid ABC 2.1 is questionable; the spec calls for matching delimiters. This is worth a targeted test so it becomes a failing test if the format is wrong.

**`ExportImageAction` duplication:** The `actionPerformed` method manually handles the `.jpg`/`.jpeg` dual-extension case via `FileUtils.ensureExtension(file, "jpg", "jpeg")` for JPEG and `FileUtils.ensureExtension(file, extension)` for PNG, but `PlatformFileDialog.showSaveDialog` (used by every other export action) already appends the extension itself. `ExportImageAction` uses the stateful `PlatformFileDialog` instance (not the static `showSaveDialog`) so it bypasses the auto-append, making this dual-call necessary. The logic is correct but is the only place in the codebase that needs to call `FileUtils.ensureExtension` after a dialog, which makes it an easy target for regression if the dialog API changes. A unit test for `FileUtils.ensureExtension` directly would guard this.

**`ActionsAppMenuTest`** covers the `getNativeMenuTitle()` contracts for both `AboutOpenAction` and `PreferencesOpenAction` adequately — these rows are `adequate`.

**`UIActionFlagBehaviorTest`** covers the base `OPENS_DIALOG` flag behavior adequately for all five export actions (all set that flag). The export actions themselves add no additional enablement flags beyond `OPENS_DIALOG`, so no per-class enablement test is warranted.

### §5 summary (`ui/action`, 62 classes)

**The package's defining shape: actions are thin dispatchers, and the dispatch is untested.** The single dominant, cross-cutting finding — independently surfaced by 5B, 5C, 5D, and 5E — is the **wrong-level / missing command-dispatch gap**: an action's `actionPerformed` posts one `Command` (or `Notification`) to the bus, and the only existing coverage is the downstream *handler* (`ScoreViewControllerCommandHandlerTest`) or the model operation, never the action dispatch itself. A mutant that swaps or deletes the posted command class in the action body survives the entire suite. Confirmed instances: `AddDynamicsAction`, `RemoveDynamicsAction`, `FirstSecondEndingAction`, `FlipStemDirectionAction`, `ToggleTrillAction` (5B/5C); `PasteboardAction`+`CopyAction`/`CutAction`/`PasteAction`, `DeleteAction`, `DeselectAction`, `SelectLineAction`, `InsertLineAction`, `ToggleNotationAction` (5D); all nine lifecycle actions (5E). Remediation should establish a single reusable pattern (subscribe a probe handler, fire `actionPerformed`, assert the exact command/payload posted) and apply it package-wide.

**Where the real pure-logic risk concentrates:**
- `UIAction.enableFromSelectionSize` (5A) — all six selection-size flag predicates (`REQUIRES_SELECTION`/`_EMPTY`/`_SINGLE`/`_OPTIONAL_SINGLE`/`_MULTIPLE`/`_OPTIONAL_MULTIPLE`) have zero direct coverage; trivial boundary mutants survive. The shared `UIAction` enablement chain is the highest-leverage place to add tests — every action inherits it.
- `ExportABCAction` (5F) — ~20 `static`/package-private methods implementing a complete ABC 2.1 serializer (key mapping, `getPitchType`/`translatePitch` octave encoding, GCD note-length reduction, lyric syllabic mapping, decorations, glissando-as-slur). All pure, all directly testable without mocks, and **not one has a test**. Failure mode is silent (syntactically valid ABC with wrong pitches). This is the single richest untested-computation block in the package.
- `DurationActionGroup`/`NonDurationActionGroup` mutual-exclusion handlers and `StickyUIAction.doActionPerformed` (5A); `DeleteAction.updateEnabledState`'s three uncovered branches and `RestModeAction`'s `ENABLE_WHEN_DURATION_SELECTED` (5D); `TupletAction`/`ToggleTrillAction`/`FlipStemDirectionAction` enablement predicates (5B).

**Level distribution holds the rubric:** the package is overwhelmingly `unit` (enablement predicates, model mutations via `applyToElement`/`matchesElement`, command-dispatch, ABC serialization) — all assertable with the `MainFrame.getInstance()` singleton mocked. Genuine `e2e` is limited to the shutdown/window-close lifecycle (`QuitAction` adequate via `ShutdownTest`; `CloseWindowAction` is a missing e2e for the same `Shutdown.now()` entry point). Pure dialog-opening `actionPerformed` bodies (`AnnotationAction`, `BeatChangeAction`, `TempoChangeAction`/`KeySignatureChangeAction` dialog open, `AboutOpenAction`, `PreferencesOpenAction`, `TipAction`, the export-chooser wiring) are correctly `none`.

**Scope corrections during assembly (no findings lost):** the 5D and 5E sub-audits over-reached into collaborators — `ScoreViewController.handle*` (5D) and `MainFrame.save*/showSaveDialog/handle*` + `Shutdown` (5E). Those classes live in `ui/component` and `lifecycle`, so their rows were trimmed from §5 and folded into forward-pointers for **Session 7** (`ui/component`) and **Session 12** (`lifecycle`) to avoid double-counting. The most important carry-forward is that `MainFrame.showSaveDialog()` + the three save-path methods — the sole guard against silent data loss — are untested except indirectly via `ShutdownTest`.

**Dead code:** `ScoreViewController.handlePaste()` is a TODO-only stub, so `PasteAction` is a silent no-op while appearing enabled whenever the clipboard is non-empty (handler is Session 7 scope, but the user-visible defect originates here).

**Production observations filed as a tracked GitHub issue (#410; do not fix during audit):**
1. `LaunchAction.actionPerformed` swallows `IOException` silently (no log, no user feedback) when process spawn fails (5A).
2. `KeySignatureChangeAction.musicSelectionDidChange` calls `getScoreView().getSelectedLine()` with no null-guard on the score view, unlike the sibling `TempoChangeAction` — probable NPE (5C).
3. `MainFrame.saveAsNewFile()` passes an empty suggested filename on Save-As of an existing document, forcing the user to retype the name (5E).
4. `ExportSVGAction.actionPerformed` labels its save-dialog filter "Portable Document Format" (copy/paste from `ExportPDFAction`); export works, label is wrong (5F).
5. `ExportABCAction.translateTempo` emits mismatched quote delimiters (`"…'`) in the show-tempo branch — likely invalid ABC 2.1; needs an exact-output test to confirm (5F).
6. `DotAction.applyToElement` derives dot count from `DotLevel.ordinal() + 1` (magic numbers, ordinal-fragility) while `matchesElement` hardcodes `1`/`2`; introduce named constants (5B).
7. `DurationArticulationAction` and `ForceArticulationAction` have byte-for-byte identical `applyToElement`/`matchesElement` bodies; extract a shared base to remove drift risk (5B).
8. `ScoreViewController.handlePaste()` is an unimplemented stub (paste is dead) (5D) — note for Session 7.

**Cosmetic/test-hygiene (fix during remediation, not issues):** `UIActionFlagBehaviorTest` has a comment contradicting its own (correct) assertion about `DialogOpenAction` auto-setting `OPENS_DIALOG` (5A); `DynamicsMarkingTest` carries a now-meaningless `@TestClassOrder`/`@Order(5)` artifact from deleted siblings (5C).

## 6. `ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard` (audited 2026-05-21)

Audited via four parallel production-first sub-audits in a single wave: **6A** `SelectionCoordinator` (the 998-LOC selection engine, cross-checked against all 19 referencing test files); **6B** selection data holders + clipboard (`LineSelectionState`, `ElementSelection`, `TupletToggleInfo`, `ClipboardManager`); **6C** `ui/edit` (`GraceModeManager`, `EditModeManager`, `ScoreActions`); **6D** `ui/adjustment` (`VerticalAdjustment`, `HorizontalAdjustment`, `Adjustment`). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. 11 production classes (the matrix's "15" counts the 4 `package-info.java` files); 259 behavior rows.

### 6A. `SelectionCoordinator`

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|---------------|---------|--------|
| SelectionCoordinator | registerLineState sets the selection-change callback on the state | unit | none | missing | add unit test: verify callback is wired so that when state fires its selection-change callback, lyricSelection is cleared |
| SelectionCoordinator | unregisterLineState removes state from registry | unit | none | missing | add unit test: register, unregister, assert getLineState returns null |
| SelectionCoordinator | clearLineStates clears all states and resets activeLineIndex to -1 | unit | none | missing | add unit test: register two states, clearLineStates, assert both gone and getActiveLineIndex == -1 |
| SelectionCoordinator | getLineState returns state for registered index, null for unregistered | unit | SelectionCoordinatorLyricSelectionTest (uses getLineState(0)) | adequate | — |
| SelectionCoordinator | getActiveLineIndex returns -1 when no line active, correct index when active | unit | none directly; implicitly exercised by many tests via clearSelection postconditions | inadequate | add explicit test for getActiveLineIndex == -1 before activation and correct index after |
| SelectionCoordinator | activateLine sets activeLineIndex and clears previous line's selection | unit | none directly | missing | add unit test with two registered lines: activate line 0, select some elements, activate line 1, verify line 0 selection is cleared and activeLineIndex == 1 |
| SelectionCoordinator | activateLine clears lyric selection | unit | none directly (selectLyric tests show clearSelection clears lyric; activateLine path not tested) | missing | add unit test: selectLyric, then activateLine, assert hasLyricSelection is false |
| SelectionCoordinator | clearSelection clears active line's element selection and resets activeLineIndex to -1 | unit | DeselectTest.testClearSelectionRemovesAllSelectedElements, testClearSelectionOnSingleElement, testClearSelectionWhenNothingSelected | adequate | — |
| SelectionCoordinator | clearSelection clears lyric selection | unit | SelectionCoordinatorLyricSelectionTest.testSelectLyricClearsElementSelection (reverse direction); no direct test of clearSelection → clears lyric | missing | add test: selectLyric, then clearSelection, assert hasLyricSelection false |
| SelectionCoordinator | selectLyric clears element selection, sets activeLineIndex, and sets lyric selection record | unit | SelectionCoordinatorLyricSelectionTest.testSelectLyricClearsElementSelection | adequate | — |
| SelectionCoordinator | selectLyric on unknown line (not registered) returns -1 activeLineIndex | unit | none | missing | edge case: selectLyric on element whose line is not registered — findLineIndex returns -1; should be documented/tested |
| SelectionCoordinator | clearLyricSelection nulls out lyricSelection | unit | implicit in testSelectLyricClearsElementSelection and testElementSelectionClearsLyricSelection | adequate | — |
| SelectionCoordinator | getLyricSelection returns the selection record | unit | SelectionCoordinatorLyricSelectionTest (asserts getLyricSelection equals expected record) | adequate | — |
| SelectionCoordinator | hasLyricSelection returns false when null, true when set | unit | SelectionCoordinatorLyricSelectionTest | adequate | — |
| SelectionCoordinator | elementSelectionFromClick clears lyricSelection via callback | unit | SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection | adequate | — |
| SelectionCoordinator | isInSelectMode / setInSelectMode getter-setter pair | unit | none | none | trivial boolean field with no branching logic; not worth testing |
| SelectionCoordinator | isElementSelected: returns false when lineIndex != activeLineIndex | unit | none | missing | add test: register two lines, activate line 0, select element on line 0; verify isElementSelected returns false for line 1 |
| SelectionCoordinator | isElementSelected: returns false when state has no element selection | unit | none | missing | add test: activate line with no selection, assert isElementSelected false |
| SelectionCoordinator | isElementSelected: delegates to state when lineIndex matches and state has selection | unit | none direct; indirectly used in e2e SelectionTest via scoreView().isElementSelected | inadequate | add direct unit test |
| SelectionCoordinator | isLineSelected: returns false when lineIndex != activeLineIndex | unit | none | missing | add test similar to isElementSelected cross-line case |
| SelectionCoordinator | isLineSelected: delegates to state.isLineSelected for active line | unit | none unit (e2e SelectionTest.testClickInStaffHeaderSelectsLine covers end-to-end) | wrong-level | add unit test: mock/set up a state that returns isLineSelected=true, verify coordinator.isLineSelected returns true for that index |
| SelectionCoordinator | isGlissandoSelected: returns false for wrong line, delegates to state for correct line | unit | none | missing | add unit test for cross-line guard and delegation |
| SelectionCoordinator | isLyricSelected: returns false when activeLineIndex != lineIndex or no lyric selection | unit | none | missing | add unit tests for both early-return paths |
| SelectionCoordinator | isLyricSelected: returns true only when element reference matches and verse matches | unit | none | missing | add test: selectLyric(e, 2), assert isLyricSelected(e, 2, …) true; false for wrong verse; false for different element |
| SelectionCoordinator | hasGlissandoSelection delegates to active state's hasGlissandoSelection | unit | GlissandoReflectionTest (exercises via selectGlissando + triggerReflection flow) | adequate | — |
| SelectionCoordinator | canDeleteLine: false when no active line | unit | none | missing | add unit test |
| SelectionCoordinator | canDeleteLine: false when active line is not a line-selection (isLineSelected false) | unit | none | missing | add unit test |
| SelectionCoordinator | canDeleteLine: false when line is selected but song has only one line | unit | none | missing | add unit test |
| SelectionCoordinator | canDeleteLine: true when line selected and song has > 1 line | unit | none | missing | add unit test |
| SelectionCoordinator | canChangeTempo: false when no active selection | unit | none | missing | add unit test |
| SelectionCoordinator | canChangeTempo: false when active selection but no single selected element | unit | none | missing | add unit test (multi-element selection) |
| SelectionCoordinator | canChangeTempo: true when exactly one element is selected | unit | none | missing | add unit test |
| SelectionCoordinator | getSelectionSize: 0 when no active selection, delegates to state otherwise | unit | DeselectTest (verifies size 3 → 0) | adequate | — |
| SelectionCoordinator | getSelection: null when no active line or state, delegates to state otherwise | unit | SelectionApplyIntegrationTest (uses getSelection in assertions) | adequate | — |
| SelectionCoordinator | getSingleSelectedElement: null when no active selection, delegates to state otherwise | unit | SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection | adequate | — |
| SelectionCoordinator | getSelectedLine: returns activeLineIndex when that line has a line-selection, -1 otherwise | unit | none | missing | add unit test |
| SelectionCoordinator | getSelectedElements: returns empty list when no selection | unit | none explicit | missing | add test |
| SelectionCoordinator | getSelectedElements: returns correct elements for selection range | unit | none explicit; behavior tested implicitly via applyActionToSelection results | inadequate | add direct test asserting element identity/order |
| SelectionCoordinator | hasActiveSelection: false when no element selection, true when selection exists | unit | SelectionContentTest.testNoSelectionReturnsNoActiveSelection, testWithSelectionReturnsActiveSelection | adequate | — |
| SelectionCoordinator | selectionHasDurations: false when no selection | unit | SelectionContentTest.testNoSelectionHasDurationsReturnsFalse | adequate | — |
| SelectionCoordinator | selectionHasDurations: true when selection contains at least one duration element | unit | SelectionContentTest.testSelectionWithMixedContentHasDurations, testSelectionWithOnlyDurationsHasDurations | adequate | — |
| SelectionCoordinator | selectionHasDurations: false when selection contains only non-durations | unit | SelectionContentTest.testSelectionWithOnlyNonDurationsHasNoDurations | adequate | — |
| SelectionCoordinator | selectionHasRests: false when no selection | unit | none | missing | add test: no selection → selectionHasRests returns false |
| SelectionCoordinator | selectionHasRests: true when selection contains at least one rest | unit | none | missing | add test with a rest in selection |
| SelectionCoordinator | selectionHasRests: false when selection contains only notes (no rests) | unit | none | missing | add test: note-only selection → selectionHasRests false |
| SelectionCoordinator | isApplicableToSelection: false when no selection | unit | SelectionContentTest.testNoSelectionIsNotApplicable | adequate | — |
| SelectionCoordinator | isApplicableToSelection: true when any element in selection matches action | unit | SelectionContentTest.testApplicableActionWithApplicableNotesReturnsTrue, testApplicableActionWithMixedNotesReturnsTrue | adequate | — |
| SelectionCoordinator | isApplicableToSelection: false when no element in selection matches action | unit | SelectionContentTest.testActionNotApplicableToBarlines, testApplicableActionWithNoApplicableNotesReturnsFalse | adequate | — |
| SelectionCoordinator | applicability cache is invalidated when selection changes | unit | none | missing | add test: select range A, check applicability (cached), change selection to range B, check applicability reflects new range — verifies invalidation logic |
| SelectionCoordinator | content cache (hasDurations/hasRests) is invalidated after applyActionToSelection | unit | none explicit | missing | add test: select notes, check selectionHasDurations true, apply duration change that converts notes to rests, check selectionHasRests becomes true after invalidation |
| SelectionCoordinator | applyActionToSelection: no-op when no active selection | unit | none | missing | add test: call applyActionToSelection with no selection, assert no exception and no mutations |
| SelectionCoordinator | applyActionToSelection wraps all mutations in a single modification bracket | unit | ApplyActionToSelectionMutationTest + BatchMutationTest (verify mutations emitted) | adequate | — |
| SelectionCoordinator | applyActionToSelection skips inapplicable elements (ElementReplaceable path) | unit | ApplyActionToSelectionMutationTest.testElementReplaceableSkipsInapplicableElements, BatchMutationTest.testAccidentalSkipsRests | adequate | — |
| SelectionCoordinator | applyActionToSelection with selected=false skips ElementReplaceable actions (continue guard) | unit | SelectionApplyIntegrationTest.testApplyThenRemoveAttribute (tests ElementModifiable; the ElementReplaceable `selected=false → continue` guard is not separately tested) | missing | add test: apply ElementReplaceable with selected=false, assert no elements changed |
| SelectionCoordinator | applyActionToSelection applies ElementReplaceable (duration change, barline change) correctly | unit | ApplyActionToSelectionMutationTest, SelectionApplyIntegrationTest | adequate | — |
| SelectionCoordinator | applyActionToSelection applies ElementModifiable (accidental, fermata, dot, staccato) correctly | unit | BatchMutationTest, SelectionApplyIntegrationTest | adequate | — |
| SelectionCoordinator | applyActionToSelection with EndingEffect.Invalidate: skips replacement when user declines confirm | unit | EndingConfirmsTest (covers the confirm-invalidation path) | adequate | — |
| SelectionCoordinator | applyActionToSelection with EndingEffect.CompensateEnd: applies compensating change when confirmed | unit | EndingConfirmsTest | adequate | — |
| SelectionCoordinator | applyActionToSelection with EndingEffect.CompensateSplit: applies compensating change when confirmed | unit | EndingConfirmsTest | adequate | — |
| SelectionCoordinator | repairBeamings: beam unchanged when all elements remain beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamUnchangedWhenAllElementsRemainBeamable, BatchMutationTest.testBeamDissolvedWhenAllNonBeamable | adequate | — |
| SelectionCoordinator | repairBeamings: beam trimmed from left when start becomes non-beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamTrimmedWhenStartElementBecomesNonBeamable, BatchMutationTest.testBeamShrunkFromStart | adequate | — |
| SelectionCoordinator | repairBeamings: beam trimmed from right when end becomes non-beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamTrimmedWhenEndElementBecomesNonBeamable, BatchMutationTest.testBeamShrunkFromEnd | adequate | — |
| SelectionCoordinator | repairBeamings: beam killed when interior element becomes non-beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamKilledWhenInteriorElementBecomesNonBeamable | adequate | — |
| SelectionCoordinator | repairBeamings: beam killed when trimmed span has fewer than two elements | unit | SelectionCoordinatorValidateSpansTest.testBeamKilledWhenTrimLeavesFewerThanTwoElements, BatchMutationTest.testBeamDissolvedWhenSubgroupTooSmall | adequate | — |
| SelectionCoordinator | validateSpans removes overlapping tuplets | unit | SelectionCoordinatorValidateSpansTest, BatchMutationTest tuplet tests | adequate | — |
| SelectionCoordinator | saveActionStates saves selected+enabled for all managed actions; does nothing if already saved | unit | ReflectionHandlerTest.testNewSelectionSavesState, testSaveRestoreRoundTripPreservesBothSelectedAndEnabled; idempotency tested indirectly by testChangedSelectionDoesNotResave | adequate | — |
| SelectionCoordinator | restoreActionStates restores saved selected+enabled and clears saved map | unit | ReflectionHandlerTest.testClearSelectionRestoresEnabledState, testClearSelectionRestoresSavedState, testSaveRestoreRoundTripPreservesBothSelectedAndEnabled | adequate | — |
| SelectionCoordinator | restoreActionStates is no-op when nothing saved | unit | ReflectionHandlerTest.testNoSelectionNoSavedStateIsNoOp | adequate | — |
| SelectionCoordinator | clearSavedActionStates clears map without restoring | unit | none | missing | add test: save states, manually mutate action, clearSavedActionStates, verify action has mutated value (not restored), and map is empty |
| SelectionCoordinator | restoreActionStatesWithFlag: restores only actions with given flag; clears all saved states | unit | none | missing | add test: save states for two actions (one with DISABLE_IN_GRACE_MODE, one without), call restoreActionStatesWithFlag(DISABLE_IN_GRACE_MODE), verify only flagged action is restored and map is cleared |
| SelectionCoordinator | dragDidStart stores the dragging line and registers global AWT listener | unit | none | none | the AWT Toolkit registration is framework plumbing with no observable logic we own; dragDidStart's body is trivially verifiable only through side effects on the real AWT event queue — e2e-only, and already exercised by SelectionTest.testDragSelect (rubber-band drag) |
| SelectionCoordinator | globalMouseReleasedListener: clears drag rectangle and nulls dragging line on MOUSE_RELEASED | unit | — (e2e SelectionTest.testDragSelect exercises it but never asserts the cleanup) | missing | the cleanup logic (clearDragRectangle + null dragging line) is ours and directly invokable via package-private/reflection — the AWT dispatch is not our risk; write a unit test invoking the listener directly and assert both effects |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: saves on new non-null selection (selection changed from lastReflected) | unit | ReflectionHandlerTest.testNewSelectionSavesState (exercises save via full handler path indirectly) | adequate | — |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: restores when selection becomes null and no glissando | unit | ReflectionHandlerTest.testClearSelectionRestoresSavedState | adequate | — |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: saves (not restores) when selection null but glissando active | unit | GlissandoReflectionTest.SaveRestore.testSaveOccursOnGlissandoSelection | adequate | — |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: no-op when selection unchanged (equals lastReflectedSelection) | unit | none | missing | add test: trigger handler twice with same selection, assert restoreActionStates is not called (saved state remains non-empty or verify action not reverted) |
| SelectionCoordinator | triggerReflection: skips when selection equals lastReflectedSelection | unit | none | missing | add test: call triggerReflection twice with same selection, assert action selected state from first call is not reset on second call (deduplication guard) |
| SelectionCoordinator | triggerReflection: reflects correct selected state onto all reflectable actions for element selection | unit | ReflectionHandlerTest, ReflectionIntegrationTest (comprehensive per-action matrix) | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=true when all applicable elements match | unit | ReflectionHandlerTest.testAllApplicableAllMatchSelected | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=false when any applicable element mismatches | unit | ReflectionHandlerTest.testAllApplicableFirstMismatchDeselected, testAllApplicableSecondMismatchDeselected | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=false when no applicable elements | unit | ReflectionHandlerTest.testAllInapplicableDeselected | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=true when only non-applicable element mismatches (rest in mixed selection) | unit | ReflectionHandlerTest.testNoteAndRestNoteMatchesSelected | adequate | — |
| SelectionCoordinator | triggerReflection: clears lastReflectedSelection and routes to reflectGlissandoSelection when selection null + glissando active | unit | GlissandoReflectionTest.testConnectedGlissandoSelectedReflectsGlissandoAction | adequate | — |
| SelectionCoordinator | triggerReflection: sets lastReflectedSelection and skips glissando path on normal element selection | unit | GlissandoReflectionTest.testNoteWithGlissandoSelectedDoesNotTriggerGlissandoReflection | adequate | — |
| SelectionCoordinator | reflectGlissandoSelection: enables and selects only the matching glissando action | unit | GlissandoReflectionTest.testConnectedGlissandoSelectedReflectsGlissandoAction, testSlideOutSelectedReflectsSlideOutAction | adequate | — |
| SelectionCoordinator | reflectGlissandoSelection: disables non-matching glissando type | unit | GlissandoReflectionTest.testNonMatchingGlissandoToolDisabled | adequate | — |
| SelectionCoordinator | reflectGlissandoSelection: restores all actions on glissando deselection | unit | GlissandoReflectionTest.testClearGlissandoSelectionRestoresState | adequate | — |
| SelectionCoordinator | reflectElement: sets selected on all reflectable actions based on single element | unit | none | missing | add test: reflectElement on a note with known attributes, assert matching actions selected and non-matching deselected |
| SelectionCoordinator | updateGraceNoteActionEnabled: disables grace note action in select mode | unit | none | missing | add test: setInSelectMode(true), call updateGraceNoteActionEnabled(true), assert GRACE_EIGHTH_NOTE_ACTION disabled |
| SelectionCoordinator | updateGraceNoteActionEnabled: enabled only when not in select mode and hasGraceNote | unit | none | missing | add test: setInSelectMode(false), call updateGraceNoteActionEnabled(true), assert enabled; then updateGraceNoteActionEnabled(false), assert disabled |
| SelectionCoordinator | triggerReflection updates grace note action enabled state based on selection content | unit | none | missing | add test: selection containing a grace note triggers updateGraceNoteActionEnabled(true); selection without grace notes triggers false |
| SelectionCoordinator | collectActions lazily discovers reflectable/managed actions via reflection over Actions fields | unit | ReflectionTestHelper bypasses collectActions by injecting; no test of real collectActions path | none | collectActions scans Actions via reflection — that is framework wiring over a well-defined field set; production smoke of the real Actions scan is not warranted as a dedicated unit test |
| SelectionCoordinator | getReflectableActions / getManagedActions are lazily initialized and cached | unit | none | none | pure lazy-init memoization with no branching logic once list is computed; not worth testing directly |

**6A notes (quality concerns):**

The highest-risk gap is the complete absence of tests for `selectionHasRests` — the three branches (no selection, rests present, rests absent) have zero coverage despite `selectionHasRests` being used by `UIAction` to gate the `DISABLE_IN_RESTS_ONLY` flag path. `canDeleteLine` and `canChangeTempo` are similarly entirely untested; both have meaningful conditional logic (song.lineCount() > 1, single-element check) that could silently regress.

`restoreActionStatesWithFlag` (the grace-mode-specific partial restore) has no unit test at all. The method has a distinct code path from `restoreActionStates` — it filters by flag and always clears the map — and a bug there would corrupt action state on grace note completion without any signal from the test suite.

`clearSavedActionStates` also has no test. It is the "commit" path that prevents a stale restore, called from `ScoreViewController.handleDelete`; a test is straightforward.

The `isElementSelected`, `isLineSelected`, and `isGlissandoSelected` cross-line guard (`activeLineIndex != lineIndex → false`) has no direct unit coverage. These guards exist specifically to prevent rendering artifacts on inactive lines, and the only coverage is the e2e `SelectionTest.testClickInStaffHeaderSelectsLine` (which calls `scoreView().isLineSelected` through the full Swing stack).

`triggerReflection`'s deduplication guard (`selection.equals(lastReflectedSelection) → skip`) has no test. If broken, every redundant notification would reset toolbar state unnecessarily.

`reflectElement` (used by `GraceModeManager` to mirror host-note attributes after pairing) has no unit test.

The e2e `SelectionTest.testDragSelect` assertion uses `isGreaterThanOrEqualTo(3)` — a weak lower-bound oracle that cannot detect if the drag yields too many elements selected (off-by-one in hit detection). The exact count should be asserted.

`activateLine`'s behavior of clearing the previous line's selection (the cross-line-switch contract) is entirely untested. All tests use `ReflectionTestHelper` which activates only line 0, so the multi-line path that clears a prior active line is dead code from the test suite's perspective.

### 6B. selection data holders + `ClipboardManager`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ElementSelection` | pure data record (line, begin, end) — no logic | none | — | none | no test needed; pure carrier |
| `TupletToggleInfo` | compact-record guard: throws when `coversExisting=true` and `existing=null` | unit | none | missing | add unit test asserting the `IllegalArgumentException` |
| `TupletToggleInfo` | `canToggle`, `existing`, `coversExisting` fields accessible as record components | none | — | none | pure data accessors; no logic |
| `LineSelectionState` | `clearSelection()` resets all five fields and fires callback | unit | none | missing | add unit test with callback capture and field assertions |
| `LineSelectionState` | `setLineSelected(true)` clears `selectedGlissandoElementIndex` and fires callback | unit | none | missing | add unit test asserting glissando index reset |
| `LineSelectionState` | `setLineSelected(false)` sets `lineSelected=false` and fires callback | unit | none | missing | add unit test |
| `LineSelectionState` | `selectGlissando()` clears element + line selection, sets glissando index, fires callback | unit | `NoteConnectionTest.GlissandoSelection.testClickSelectGlissando` (e2e) | wrong-level | add unit test; e2e is also acceptable as an integration check but the logic can be exercised unit |
| `LineSelectionState` | `isGlissandoSelected(index)` returns true iff index matches stored glissando index | unit | none | missing | add unit test |
| `LineSelectionState` | `hasGlissandoSelection()` returns true iff `selectedGlissandoElementIndex != -1` | unit | `NoteConnectionTest` (e2e, asserts against real LSS) | wrong-level | add unit test |
| `LineSelectionState` | `hasElementSelection()` returns false when no selection, true after click-select | unit | `LineSelectionStateTest.testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing` (indirect) | adequate | — |
| `LineSelectionState` | `isElementSelected(index)` correctly bounds-checks with inclusive range | unit | `NoteConnectionTest.testSelectSourceNote` / `testSelectTargetNote` (e2e) | wrong-level | add unit test; selection range logic is pure state |
| `LineSelectionState` | `getSelectionSize()` returns 0 when no selection, N=(end-begin+1) otherwise | unit | none (only mocked indirectly in other tests) | missing | add unit test |
| `LineSelectionState` | `getSelection()` returns `null` when no element and no line selection | unit | none | missing | add unit test |
| `LineSelectionState` | `getSelection()` returns `ElementSelection` spanning full line when `lineSelected=true` | unit | none | missing | add unit test |
| `LineSelectionState` | `getSelection()` returns `ElementSelection` from element range when element-selected | unit | used via mock in `ApplyToSelectionInterceptTest` / `DynamicMarkingActionTest` | inadequate | tests only stub the return on a mock coordinator; add unit test on LSS directly |
| `LineSelectionState` | `getSingleSelectedElement()` returns null when >1 selected, element when exactly 1 | unit | `SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection` (indirect, asserts coordinator wrapper) | adequate | — |
| `LineSelectionState` | `setSelectionFromClick()` sets begin=end=anchor=index, clears glissando, fires callback | unit | used as setup in `LineSelectionStateTest` and `ToggleConflictTest` but never directly asserted | inadequate | add unit test asserting all five side effects |
| `LineSelectionState` | `extendSelectionTo()` with anchor < index: sets begin=anchor, end=index | unit | used as setup in `LineSelectionStateTest` but never directly asserted on its own | inadequate | add unit test asserting correct [begin,end] ordering in both directions |
| `LineSelectionState` | `extendSelectionTo()` with anchor > index (reversed drag): begin=index, end=anchor | unit | none | missing | add unit test |
| `LineSelectionState` | `extendSelectionTo()` no-op when anchor is -1 | unit | none | missing | add unit test |
| `LineSelectionState` | `extendSelection()` starts new selection when begin=-1 | unit | none | missing | add unit test |
| `LineSelectionState` | `extendSelection()` extends end when selection exists (begin unchanged) | unit | none | missing | add unit test |
| `LineSelectionState` | `resetElementSelection()` sets begin=end=-1 (does not touch lineSelected/glissando), fires callback | unit | none | missing | add unit test |
| `LineSelectionState` | `setSelectionAnchor()` / `getSelectionAnchor()` round-trip | unit | none | missing | add unit test |
| `LineSelectionState` | `selectAll()` excludes auto-maintained terminal (FINAL_DOUBLE_BARLINE) | unit | `LineSelectionStateTest.testSelectAllExcludesAutoMaintainedFinalBarlineOnLastLine` | adequate | — |
| `LineSelectionState` | `selectAll()` excludes REPEAT_RIGHT terminal on last line | unit | `LineSelectionStateTest.testSelectAllExcludesAutoMaintainedRightRepeatTerminalOnLastLine` | adequate | — |
| `LineSelectionState` | `selectAll()` on empty line (only terminal) selects nothing | unit | `LineSelectionStateTest.testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing` | adequate | — |
| `LineSelectionState` | `selectAll()` on non-last line includes all elements | unit | `LineSelectionStateTest.testSelectAllOnNonLastLineIncludesAllElements` | adequate | — |
| `LineSelectionState` | `selectionChangeCallback` fires on every state-mutating call | unit | none | missing | add unit test with a counter callback |
| `LineSelectionState` | `canToggleBeaming()` — size < 2 returns false | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` (indirectly; quarter notes are not beamable so size check and beamable check overlap) | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — non-beamable element in range returns false | unit | `ToggleConflictTest.testChangeDurationToQuarterDisablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — all beamable, no existing beam (add mode) | unit | `ToggleConflictTest.testChangeDurationToEighthBothEnabled` | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — existing beam covering selection (remove mode) | unit | `ToggleConflictTest.testToggleTieOnDisablesBeam` / `testToggleTieOffReenablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — blocked when would connect same span as existing tie | unit | `ToggleConflictTest.testToggleTieOnDisablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleTie()` — size != 2 returns false and sets canTie=false | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` (size=2 path) + lacks explicit size!=2 assertion | inadequate | add explicit test for size < 2 and size > 2 setting canTie=false |
| `LineSelectionState` | `canToggleTie()` — non-pitched element in range returns false | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTie()` — pitch mismatch returns false | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTie()` — elements in different ties returns false | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTie()` — two same-pitch notes with no tie (add mode): canTie=true, existingTie=null | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` + `testToggleTieOnDisablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleTie()` — two notes sharing existing tie (remove mode): canTie=true, existingTie set | unit | `ToggleConflictTest.testToggleTieOffReenablesBeam` (uses getExistingTie) | adequate | — |
| `LineSelectionState` | `canToggleTie()` — blocked when would connect same span as existing beam | unit | `ToggleConflictTest.testToggleBeamOnDisablesTie` | adequate | — |
| `LineSelectionState` | `resetTieState()` clears canTie and existingTie | unit | `ToggleConflictTest.toggleTie()` calls resetTieState but doesn't assert fields directly | inadequate | add unit test asserting `getCanTie()` and `getExistingTie()` return null after reset |
| `LineSelectionState` | `canToggleTuplet()` — size < 2 returns (false, null, false) | unit | `LineSelectionStateTest.testEmptySelectionCannotToggleTuplet` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — non-pitched element returns (false, null, false) | unit | `LineSelectionStateTest.testSelectionContainingNonPitchedElementCannotToggle` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — two pitched notes, no tuplet: (true, null, false) | unit | `LineSelectionStateTest.testTwoPitchedNotesNoTupletCanToggle` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — selection spans two different tuplets: (false, null, false) | unit | `LineSelectionStateTest.testSelectionSpanningTwoDifferentTupletsCannotToggle` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — full coverage of existing triplet: (true, tuplet, true) | unit | `LineSelectionStateTest.testFullCoverageOfTripletReportsCoversExisting` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — partial coverage of existing tuplet: (true, tuplet, false) | unit | `LineSelectionStateTest.testPartialCoverageOfTripletDoesNotCoverExisting` | adequate | — |
| `LineSelectionState` | `canToggleTrill()` — returns false when no selection | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTrill()` — returns true when at least one pitched note in range | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTrill()` — returns false when selection contains only rests | unit | none | missing | add unit test |
| `LineSelectionState` | `canFlipStemDirection()` — returns false when nothing selected | unit | none | missing | add unit test |
| `LineSelectionState` | `canFlipStemDirection()` — returns true when at least one non-rest in range | unit | none | missing | add unit test |
| `LineSelectionState` | `canFlipStemDirection()` — returns false when selection is all rests | unit | none | missing | add unit test |
| `ClipboardManager` | `addElement()` normalizes FINAL_DOUBLE_BARLINE → DOUBLE_BARLINE | unit | `ClipboardManagerTest.AddElement.testFinalDoubleBarlineNormalizedToDoubleBarline` | adequate | — |
| `ClipboardManager` | `addElement()` passes non-FINAL_DOUBLE_BARLINE through unchanged | unit | `ClipboardManagerTest.AddElement.testDoubleBarlinePassedThrough` + `testNotePassedThrough` | adequate | — |
| `ClipboardManager` | `addElement()` does not mutate original song element | unit | `ClipboardManagerTest.testSongFinalBarlineUntouched` + `testSongRightRepeatTerminalUntouched` | adequate | — |
| `ClipboardManager` | `getSize()` returns correct count after adds | unit | none (only used via `getElement(0)` by index in tests) | missing | add unit test |
| `ClipboardManager` | `isEmpty()` returns true initially, false after add | unit | none | missing | add unit test |
| `ClipboardManager` | `getFirstElement()` / `getLastElement()` return correct elements from multi-element pasteboard | unit | none | missing | add unit test |
| `ClipboardManager` | `clear()` empties pasteboard and isEmpty() returns true | unit | none | missing | add unit test |

**6B notes (quality concerns):**

**Pure data holders:** `ElementSelection` is a pure 3-field record with no logic; correct verdict is `none` for all its components. `TupletToggleInfo` is 95% a data carrier but has a compact-constructor guard (throws on `coversExisting=true, existing=null`) — that one invariant is worth a unit test.

**Highest-risk missing tests:**

1. `LineSelectionState.clearSelection()` and `resetElementSelection()` are called constantly by the coordinator but their post-conditions (which fields are zeroed, callback fires) are never directly asserted. A stray bug there would silently corrupt selection state everywhere.
2. `extendSelectionTo()` reversed-drag branch (`anchor > elementIndex` → `begin=elementIndex, end=anchor`) is completely uncovered — the test setup always passes `anchor < end`.
3. `canToggleTie()` failure paths (non-pitched element, pitch mismatch, elements in different ties) have no direct unit coverage — the happy path is tested but none of the four early-return conditions are.
4. `canToggleTrill()` and `canFlipStemDirection()` have zero tests at any level.
5. `ClipboardManager` pasteboard accessors (`isEmpty`, `getSize`, `getFirstElement`, `getLastElement`, `clear`) are never asserted in tests; `ClipboardManagerTest` exercises only `addElement` normalization.

**Weak tests noted:**

- `testEmptySelectionCannotToggleTuplet` in `LineSelectionStateTest`: the name says "empty selection" but the state object was never given a selection — `selectionBegin` defaults to -1. The test is correct but tests the default-state condition, not a deliberate "was selected, then cleared" scenario.
- The `fullCoverageOfTriplet` test uses `.extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)` rather than a direct `.isEqualTo(expectedTuplet)` — the null guard in the lambda is superfluous since `isNotNull()` was already asserted; minor style noise, not a correctness risk.
- `ToggleConflictTest.testToggleTieOffReenablesBeam`: calls `state.canToggleTie()` internally via the `toggleTie()` helper in order to populate `existingTie`, which means `resetTieState()` semantics are exercised but never directly asserted on `getCanTie()` / `getExistingTie()`.

**e2e coverage classified wrong-level:** `hasGlissandoSelection`, `isElementSelected`, and `selectGlissando` side-effects are only tested through full Swing robot clicks in `NoteConnectionTest`. All three are pure in-memory state changes with no rendering dependency and should have unit tests.

### 6C. `ui/edit`

| class | behavior | required level | existing test | verdict |  action |
|---|---|---|---|---|---|
| GraceModeManager | `isActive()` returns false when no instance / INACTIVE state | unit | UIActionFlagBehaviorTest stubs `GraceModeManager::isActive` via mockStatic — asserts nothing about GraceModeManager itself | missing | unit test: construct instance, verify isActive() reflects state |
| GraceModeManager | `isInProgress()` returns false in INACTIVE, true in non-INACTIVE states | unit | none | missing | unit test: construct, check INACTIVE→false; transition→true |
| GraceModeManager | `isPendingCancel(element)` returns true only for the active grace note when pendingCancel flag is set | unit | none | missing | unit test: reflectively set pendingCancel, verify per-element identity check |
| GraceModeManager | `getCancelThresholdPx()` / `getConnectThresholdPx()` return -1 when inactive, correct px offsets (±GRACE_SLOP_PX) when layout available | unit | none | missing | unit test: mock layout result, assert threshold math |
| GraceModeManager | `getLockedInsertionXSs()` returns 0 when any required field is null; returns graceColumn.xSs + rightExtent + gap + hostLeftExtent otherwise | unit | none | missing | unit test: null-guard paths and computed value |
| GraceModeManager | `mousePressed()`: returns false when state != INACTIVE or not GRACE_QUAVER selected; returns true and consumes in GRACE_NOTE_INSERT; enters GRACE_NOTE when room check passes | unit | ElementInsertionTest exercises full pipeline via real robot clicks (e2e) | wrong-level | unit test: mock InsertionSpacingCalculator, verify state transitions |
| GraceModeManager | `mouseReleased()`: click (< slop or short duration) → enterGraceNoteInsert; drag-left → finish(cancel); drag-right with eligible host → enterGraceNotePaired | unit | ElementInsertionTest (drag-left, drag-right e2e) — timing-dependent real mouse | wrong-level | unit test: set state to GRACE_NOTE, synthesize events, assert transitions |
| GraceModeManager | `mouseDragged()`: pendingCancel computed from time + left-of-threshold; pendingConnect computed from right-of-threshold + eligible host; glissando added/removed on graceNote | unit | none | missing | unit test: mock layout, verify pendingCancel/pendingConnect flags and glissando mutations |
| GraceModeManager | `mouseClicked()`: consumed when not in GRACE_NOTE_INSERT; justEnteredInsert flag suppresses first click after transition; different-line click → finish(cancel); left-of-grace click → finish(cancel); same-pitch → error + finish; different pitch → insert host + connect glissando + enterGraceNotePaired | unit | ElementInsertionTest (click-click insertion, same-pitch error — e2e) | wrong-level | unit test: mock PreviewElementManager.handleClick, assert state and mutation |
| GraceModeManager | `keyPressed()`: Escape key → finish(cancel=true) | e2e | ElementInsertionTest.GraceNoteCancellation.testEscapeCancels — uses real key dispatch, asserts element count and mode inactive | adequate | — |
| GraceModeManager | drag-left cancel path: exceeds GRACE_SLOP_PX left of grace note → grace note removed | e2e | ElementInsertionTest.GraceNoteCancellation.testDragLeftCancels — real robot drag to cancelThresholdPx, asserts count and mode inactive | adequate | — |
| GraceModeManager | drag-right connect path: exceeds GRACE_SLOP_PX right of grace note with eligible host → paired with glissando | e2e | ElementInsertionTest.GraceNoteDragConnect.testDragConnectToStandaloneNote — real robot drag, asserts grace type, glissando, mode inactive | adequate | — |
| GraceModeManager | Escape cancels grace mode and restores element count | e2e | ElementInsertionTest.GraceNoteCancellation.testEscapeCancels | adequate | — |
| GraceModeManager | same-pitch error: both grace and host would be at same staff position → error dialog + both removed | e2e | ElementInsertionTest.GraceNoteCancellation.testSamePitchError — asserts element count restored and mode inactive | adequate | — |
| GraceModeManager | click-click insertion: grace inserted then host inserted with glissando, mode exits | e2e | ElementInsertionTest.GraceNoteInsertion.testClickClickInsertion — asserts types, glissando, count, mode inactive, actions re-enabled | adequate | — |
| GraceModeManager | toolbar state during grace mode: DISABLE_IN_GRACE_MODE actions disabled (glissando, rest, grace button) | e2e | ElementInsertionTest.GraceNoteCancellation.testEscapeCancels (partial: checks isEnabled in assertAll) | adequate | — |
| GraceModeManager | duration change during grace flow produces host note of new duration | e2e | ElementInsertionTest.GraceNoteInsertion.testDurationChangeDuringFlow — asserts host type and toolbar selection state | adequate | — |
| GraceModeManager | `enterGraceNoteInsert()` aborts with error dialog when host note would not fit on line | unit | none | missing | unit test: mock InsertionSpacingCalculator to return no-fit, assert finish(cancel) called |
| GraceModeManager | `finish(cancel=true)` wraps grace note removal in withModification bracket (mutation emitted) | unit | none | missing | unit test: mock line, verify removeElement called inside modification bracket |
| GraceModeManager | `finish(cancel=false)` resets all state fields and posts GraceModeStateDidChangeNotification(false) | unit | none | missing | unit test: verify state reset and message posted |
| GraceModeManager | `hasEligibleHostNote()` returns true only when next element exists and isPitchedNote | unit | none | missing | unit test: construct line with various next-element types |
| GraceModeManager | `GraceModeStateDidChangeNotification(true)` posted on enterGraceNote; `(false)` posted on finish | unit | none | missing | unit test: mock MessageCenter.post, verify notification payloads |
| GraceModeManager | entering GRACE_NOTE selects QUARTER_NOTE duration and deselects embellishment action groups | unit | none | missing | unit test: set embellishments, enter grace note, verify action group selections |
| EditModeManager | `init()` + static `instance()` — throws RuntimeError if called before init | unit | none | missing | unit test: call instance() without init, expect RuntimeError/IllegalStateException |
| EditModeManager | `makePreviewElement()`: resolves type from DURATION_ACTION_GROUP if selected; falls back to NON_DURATION_ACTION_GROUP; defaults to CROTCHET | unit | none | missing | unit test: mock action groups, verify returned element type |
| EditModeManager | `makePreviewElement(ElementType)`: converts pitched note to rest type when REST_ACTION is selected | unit | none | missing | unit test: mock REST_ACTION.isSelected=true, verify rest type returned |
| EditModeManager | `decorateElement()`: applies selected dot level; skips non-dot decorations for rests; applies accidental; applies accidental-in-parentheses; adds/removes FermataAttachment; clears and resets articulations | unit | none | missing | unit test: mock each action, assert element fields after decoration |
| EditModeManager | `elementWasModified()`: REPEAT_LEFT adjacent to REPEAT_RIGHT → coalesce to REPEAT_LEFT_RIGHT at previous index | unit | none | missing | unit test: construct line with REPEAT_RIGHT then click REPEAT_LEFT, verify coalesced |
| EditModeManager | `elementWasModified()`: REPEAT_RIGHT adjacent to existing REPEAT_LEFT → coalesce | unit | none | missing | unit test: construct line with REPEAT_LEFT then click REPEAT_RIGHT, verify coalesced |
| EditModeManager | `elementWasModified()` returns false for non-repeat preview elements | unit | PreviewElementManagerTestBase stubs this as `thenReturn(false)` — tests PreviewElementManager, not EditModeManager | missing | unit test: direct call with non-repeat preview element |
| EditModeManager | `previewElementDidChange()`: turns off FERMATA_ACTION and ACCIDENTAL_IN_PARENS after insert; calls scoreActions.setPreviewElement with decorated next element; calls drawWidthIfWiderLine and repaint | unit | none | missing | unit test: mock scoreActions and Actions, verify callbacks and action state |
| EditModeManager | `previewElementDidChange()`: starts PlayThread when playInsertedNote=true and inserted element is a note | unit | none | missing | unit test: mock PlayThread construction, verify it starts |
| ScoreActions | `clearSelection()` / `repaint()` / `setPreviewElement()` / `drawWidthIfWiderLine()` / `syncPlaybackPrefs()` / `updatePageLayout()` / `setKeyBindingsEnabled()` — all are pure interface callbacks, no logic | none | — | none | no test warranted; contract tested via ScoreView which implements it |

**6C notes (quality concerns):** The `ui/edit` package has no mirrored test file — `src/test/java/songscribe/ui/edit/` contains only `package-info.java`. Zero unit tests exist for `GraceModeManager` or `EditModeManager`; all coverage is either (a) e2e tests in `ElementInsertionTest` that exercise GraceModeManager's happy paths through real robot clicks, or (b) fixture-level mocks in `PreviewElementManagerTestBase` and `UIActionFlagBehaviorTest` that stub these classes to test other components. The e2e tests for cancellation, drag-connect, click-click insertion, same-pitch error, and duration change are well-structured and assert meaningful model state — they are adequate for their scope — but they do not cover the state machine logic, flag transitions, `pendingCancel`/`pendingConnect` computation, `mouseDragged` glissando mutation, `enterGraceNoteInsert` abort path, `finish` mutation bracketing, `GraceModeStateDidChangeNotification` payloads, or the complete `decorateElement`/`makePreviewElement`/`elementWasModified` branches in `EditModeManager`. The `UIActionFlagBehaviorTest.testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses` uses `mockStatic(GraceModeManager.class)` and stubs `isActive()` — it asserts nothing about `GraceModeManager` itself; it is adequate for its own target (`UIAction` flag interaction) but provides zero coverage here. The biggest gaps are the state-transition logic in `mouseReleased` (click vs. drag detection is time-based and subtle), `mouseDragged` pendingCancel/pendingConnect logic, `enterGraceNoteInsert` abort, `decorateElement` branch coverage, and `elementWasModified` repeat-coalescing — all unit-testable with mocked collaborators.

### 6D. `ui/adjustment`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| Adjustment | `mousePressed`: ignores event when `enabled=false` | unit | none | missing | Add unit test: mock scoreView, setEnabled(false), fire mousePressed, assert startedDrag remains false and scoreView.setDragDisabled never called |
| Adjustment | `mousePressed`: sets startedDrag=true, captures startPoint, calls startedDrag(), disables ScoreView drag when startedDrag flag survives startedDrag() | unit | none | missing | Add unit test via concrete subclass or spy; assert startedDrag=true and setDragDisabled(true) called |
| Adjustment | `mouseReleased`: ignores event when `enabled=false` | unit | none | missing | Add unit test: verify finishedDrag not called and drag not re-enabled |
| Adjustment | `mouseReleased`: clears startedDrag, calls finishedDrag(), re-enables ScoreView drag | unit | none | missing | Add unit test |
| Adjustment | `mouseDragged`: ignores event when `enabled=false` | unit | none | missing | Add unit test |
| Adjustment | `mouseDragged`: clamps X to [topLeftDragBounds.x, bottomRightDragBounds.x-1] | unit | none | missing | Critical arithmetic: test exact boundary values — at bound, one-past-bound, below bound; assert endPoint.x is clamped precisely |
| Adjustment | `mouseDragged`: clamps Y to [topLeftDragBounds.y, bottomRightDragBounds.y-1] | unit | none | missing | Same — exact value assertions, not just sign |
| Adjustment | `mouseDragged`: skips drag() when startedDrag=false | unit | none | missing | Add unit test |
| HorizontalAdjustment | `startedDrag()`: sets startedDrag=false when no AdjustRect contains startPoint | unit | none | missing | Unit test with populated adjustRects, click outside all — assert startedDrag=false |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: left bound = prev note x + rect.width; right bound = next note x - rect.width | unit | none | missing | Exact arithmetic; mock line with known note positions; assert topLeftDragBounds.x and bottomRightDragBounds.x to exact pixel values |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: left bound = 20 + rect.width when xIndex=0 (no predecessor) | unit | none | missing | Edge case: first note |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: right bound = lineWidth when xIndex = last note | unit | none | missing | Edge case: last note |
| HorizontalAdjustment | `startedDrag()` TO_END_OF_LINE: bounds computation | unit | none | missing | Exact arithmetic |
| HorizontalAdjustment | `startedDrag()` STRETCH_NOTE_SPACING: stretchHelper populated with note x positions; reallocated when too small | unit | none | missing | Assert stretchHelper values equal note xOffsets |
| HorizontalAdjustment | `startedDrag()` GLISSANDO_START: right bound = next AdjustRect's rect.x | unit | none | missing | Exact index lookup |
| HorizontalAdjustment | `startedDrag()` GLISSANDO_END: left bound = prev AdjustRect's rect.x + rect.width | unit | none | missing | |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x NOT adjusted when `!isInteractable` (terminal node) | unit | HorizontalAdjustmentTest.SnapToEndSkipped.testFinalDoubleBarlineTerminalSkipsSnap | inadequate | Existing test only asserts `isInteractable=false` and `snapToEnd=true` on the model; it never exercises `drag()` — endPoint.x is never set and the snap branch is never reached. The test verifies preconditions of the guard, not that drag() actually skips the snap. |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x NOT adjusted when `!isInteractable` (REPEAT_RIGHT terminal) | unit | HorizontalAdjustmentTest.SnapToEndSkipped.testRepeatRightTerminalSkipsSnap | inadequate | Same issue as above — precondition-only assertion, drag() never called |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x IS adjusted to `lineWidth - contentWidthPx` when interactable, snapToEnd, and within END_SNAP_LIMIT | unit | none | missing | The actual snap arithmetic is completely untested |
| HorizontalAdjustment | `drag()` SINGLE_NOTE: `note.setXOffsetPx(endPoint.x)` | unit | none | missing | Verify model mutation; exact value |
| HorizontalAdjustment | `drag()` TO_END_OF_LINE: all notes from xIndex forward shifted by (endPoint.x - diffX) | unit | none | missing | Multi-note delta arithmetic; assert each element's x exactly |
| HorizontalAdjustment | `drag()` STRETCH_NOTE_SPACING: each element x = firstX + (stretchHelper[i]-firstX)*ratio; `changeElementSpacingRatio` called | unit | none | missing | Non-trivial float ratio arithmetic |
| HorizontalAdjustment | `drag()` START_OF_LINE: all lines' notes shifted by diff from new x | unit | none | missing | Multi-line cascading offset math |
| HorizontalAdjustment | `drag()` GLISSANDO_START: `glissando.x1Translate += endPoint.x - diffX` | unit | none | missing | |
| HorizontalAdjustment | `drag()` GLISSANDO_END: `glissando.x2Translate += endPoint.x - diffX` | unit | none | missing | |
| HorizontalAdjustment | `drag()` CRESCENDO_START/DIMINUENDO_START: `setX1ShiftSs` incremented by delta | unit | none | missing | |
| HorizontalAdjustment | `drag()` CRESCENDO_END/DIMINUENDO_END: `setX2ShiftSs` incremented by delta | unit | none | missing | |
| HorizontalAdjustment | `drag()`: `song.setModified(true)` called on every drag event | unit | none | missing | |
| HorizontalAdjustment | `finishedDrag()`: draggingRect set to null | unit | none | missing | Trivial but verifiable |
| HorizontalAdjustment | `setEnabled(true)`: adjustRects populated with correct types and counts | unit | none | missing | Verify per type: SINGLE_NOTE count = effectiveElementCount, STRETCH = 1 per line, etc. |
| HorizontalAdjustment | `setEnabled(false)`: adjustRects cleared | unit | none | missing | |
| HorizontalAdjustment | `setEnabled(true)`: GLISSANDO_END rect NOT added for SLIDE_OUT glissando | unit | none | missing | Important conditional: CONNECTED vs SLIDE_OUT |
| HorizontalAdjustment | `findHairpinByAnchor`: returns crescendo/diminuendo matching anchorElementIndex | unit | none | missing | |
| HorizontalAdjustment | `findHairpinByEnd`: returns crescendo/diminuendo matching endElementIndex | unit | none | missing | |
| HorizontalAdjustment | `drag()` diffX calculation: `diffX = draggingRect.rect.x + (rect.width/2)` — midpoint arithmetic | unit | none | missing | Exact mid-point computation drives all delta calculations |
| VerticalAdjustment | `startedDrag()`: sets startedDrag=false when startPoint=null | unit | none | missing | Null guard path |
| VerticalAdjustment | `startedDrag()`: sets startedDrag=false when no AdjustRect contains startPoint | unit | none | missing | |
| VerticalAdjustment | `startedDrag()` ROW_HEIGHT: topLeft.y = noteYPosPx(6, 0); bottomRight.y = Integer.MAX_VALUE | unit | none | missing | Exact bound values |
| VerticalAdjustment | `startedDrag()` TEMPO_CHANGE / FIRST_SECOND_ENDING / TRILL / BEAT_CHANGE: upLeft.y = noteYPosPx(6, line-1); downRight.y = noteYPosPx(-4, line) | unit | none | missing | Exact bound arithmetic |
| VerticalAdjustment | `startedDrag()` ANNOTATION / TUPLET / CRESCENDO_Y / DIMINUENDO_Y: upLeft.y = noteYPosPx(6, line-1); downRight.y = noteYPosPx(-6, line+1) | unit | none | missing | Exact bound arithmetic |
| VerticalAdjustment | `drag()` diffY calculation: `(endPoint.y - dragRect.rect.y) + midPoint.y` | unit | none | missing | Core delta arithmetic driving ALL adjust* calls; should be tested with exact values |
| VerticalAdjustment | `drag()` diffX calculation: `(endPoint.x - dragRect.rect.x) + midPoint.x` — only used internally, rect.y updated | unit | none | missing | |
| VerticalAdjustment | `adjustAttribution(diffY)`: posts LayoutDidChangeNotification with attributionStartYSs + diffY | unit | none | missing | Exact new value |
| VerticalAdjustment | `adjustTopSpace(diffY)`: posts LayoutDidChangeNotification with topPaddingSs + diffY | unit | none | missing | Exact new value |
| VerticalAdjustment | `adjustRowHeight(diffY)`: posts LayoutDidChangeNotification with rowHeightAdjustmentSs + diffY | unit | none | missing | Exact new value |
| VerticalAdjustment | `adjustTempoChange(line, diffY)`: all TempoChangeAttachment.userYOffsetSs incremented by diffY | unit | none | missing | Accumulation for multi-note lines |
| VerticalAdjustment | `adjustBeatChange(line, diffY)`: all BeatChangeAttachment.userYOffsetSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustFirstSecondEnding(line, diffY)`: all Ending.yPositionSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustAnnotation(line, diffY)`: both userYOffsetSs AND legacy yPosPx incremented by diffY | unit | none | missing | Dual-update is a subtle correctness requirement |
| VerticalAdjustment | `adjustAnnotation(line, diffY)`: no-op when dragRect has no AnnotationAttachment | unit | none | missing | Null-guard path |
| VerticalAdjustment | `adjustTrill(line, diffY)`: all Trill.yPositionSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustDynamics(line, diffY)`: Hairpin.yShiftSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustDynamics(line, diffY)`: no-op when dragRect=null or hairpin not found | unit | none | missing | |
| VerticalAdjustment | `adjustTuplet(line, diffY)`: Tuplet.verticalPositionSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustTuplet(line, diffY)`: no-op when tuplet not found | unit | none | missing | |
| VerticalAdjustment | `finishedDrag()`: dragRect set to null | unit | none | missing | |
| VerticalAdjustment | `setEnabled(true)`: ATTRIBUTION rect added only when attribution non-empty | unit | none | missing | Conditional enrollment |
| VerticalAdjustment | `setEnabled(true)`: TOP_SPACE rect added only when lineCount > 0 | unit | none | missing | |
| VerticalAdjustment | `setEnabled(true)`: ROW_HEIGHT rect added only when lineCount > 1 | unit | none | missing | |
| VerticalAdjustment | `setEnabled(true)`: per-line rects (TEMPO_CHANGE, ANNOTATION, ENDING, TRILL, BEAT_CHANGE, CRESCENDO_Y, DIMINUENDO_Y, TUPLET) populated for matching elements | unit | none | missing | |
| VerticalAdjustment | `setEnabled(false)`: adjustRects cleared | unit | none | missing | |
| VerticalAdjustment | `getAttributionAdjustRect`: x = sheetWidthPx - HANDLE_SIZE_PX; y = attributionStartYSs | unit | none | missing | Exact pixel placement |
| VerticalAdjustment | `getHeightAdjustRect`: x = 0; y = noteYPosPx(0, line) - HANDLE_SIZE_PX/2 | unit | none | missing | Exact pixel placement (HANDLE_SIZE_PX/2 rounding matters) |
| VerticalAdjustment | `getRangeElementAdjustRect`: x = startNote.x - xOffsetPx; y = bounds.topSs - HANDLE_SIZE_PX | unit | none | missing | Exact geometry |
| VerticalAdjustment | `getRangeElementAdjustRect`: returns false and skips when rangeElement/startNote/endNote is null | unit | none | missing | Null-guard correctness |
| VerticalAdjustment | dynamics handle x = (startX + endX + DYNAMICS_HANDLE_CENTER_BIAS_PX) / 2 | unit | none | missing | Integer midpoint arithmetic with bias constant |
| VerticalAdjustment | tuplet handle x = startNote.x (upper stem) vs startNote.x + TUPLET_LOWER_HANDLE_X_OFFSET_PX (lower stem) | unit | none | missing | Branch on isUpper() |
| VerticalAdjustment | `findHairpinByAnchor`: returns crescendo/diminuendo matching anchorElementIndex | unit | none | missing | |
| VerticalAdjustment | `getLayoutResultForLine`: throws IllegalStateException when mainPanel null or layoutResult null | unit | none | missing | Error-path contracts |
| VerticalAdjustment | `repaint()`: renders all AdjustRect handles with correct colors | none | none | none | Pure Graphics2D rendering, no branching logic worth testing |
| HorizontalAdjustment | `repaint()`: renders all AdjustRect handles | none | none | none | Pure rendering |
| Adjustment | constructor: registers self as MouseListener and MouseMotionListener on scoreView | none | none | none | Pure listener registration — no branching or computation; framework wiring, not our risk |

**6D notes (quality concerns):**

The two existing tests in `HorizontalAdjustmentTest` (`testFinalDoubleBarlineTerminalSkipsSnap` and `testRepeatRightTerminalSkipsSnap`) are **inadequate** in a particularly misleading way: they are correctly named ("SnapToEndSkipped") but they never call `drag()` and never set `endPoint`. They verify only that the model properties which *would* allow the snap to fire happen to be false/true — i.e., they test preconditions on `Song.isInteractable` and `ElementType.snapToEnd`, not the behavior of `drag()` itself. The snap branch in `drag()` is entirely uncovered: a test asserting `endPoint.x` was NOT modified (or WAS modified for an interactable note near the end) would actually exercise the code. The class has zero additional tests for any of the ~10 drag operation types, the bounds computation, the stretchHelper ratio arithmetic, or the `setModified` call.

`VerticalAdjustment` has **zero tests** of any kind despite containing non-trivial logic: the `diffY` calculation `(endPoint.y - dragRect.rect.y) + midPoint.y` drives all model mutations; the `adjustAnnotation` dual-update (both `userYOffsetSs` and legacy `yPosPx`); the conditional HANDLE_SIZE_PX/2 midpoint in `getHeightAdjustRect`; the dynamics center-bias arithmetic; and the `isUpper()` branch in the tuplet handle placement. Any silent regression in these calculations would go undetected.

`Adjustment` (the abstract base) has zero tests for its mouse-event dispatch, the enabled guard, and most critically the X/Y clamping arithmetic in `mouseDragged` — the only shared geometry computation in the hierarchy.

The `HorizontalAdjustment` GLISSANDO_END omission for SLIDE_OUT glissandos (`setEnabled` conditional) is a correctness rule that is completely untested.

The unit-conversion guide identifies `diffY` and `diffX` as values arriving in pixels (`endPoint` is an AWT pixel coordinate) applied as-is to `Ss`-suffixed fields (e.g. `getUserYOffsetSs() + diffY`). This is a potential mixed-unit bug that no test currently guards.

### §6 summary (`ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard`, 11 classes)

**Verdict distribution (259 rows):** 78 adequate, 154 missing, 10 inadequate, 7 wrong-level, 10 none, 0 redundant. `missing` dominates because three of the four packages have little or no dedicated coverage; the well-covered class (`SelectionCoordinator`) accounts for 47 of the 78 `adequate` rows.

**The defining shape: drag/adjustment geometry and selection-predicate logic are the dark zones; only the selection *engine* is well-tested.** Coverage is sharply bimodal — `SelectionCoordinator` (6A) has real, level-appropriate tests for ~half its surface, while `ui/adjustment` (6D) and `ui/edit` (6C) are almost entirely untested, and the few existing adjustment tests are actively misleading.

**Where the real pure-logic risk concentrates:**
- **`ui/adjustment` (6D) is the single largest dark zone** — 72 rows, every non-`none` row `missing`, and the two existing `HorizontalAdjustmentTest` tests are *inadequate* in a misleading way: correctly named (`…SnapToEndSkipped`) but they never call `drag()` or set `endPoint`, so they assert only the model *preconditions* that would gate the snap, never the snap arithmetic. `VerticalAdjustment` and the `Adjustment` base have **zero** tests. All of it is pure geometry — `diffY = (endPoint.y - dragRect.rect.y) + midPoint.y`, the `Adjustment.mouseDragged` X/Y clamping (off-by-one `bound - 1` semantics), per-`AdjustType` drag bounds, stretch-ratio arithmetic, the SLIDE_OUT GLISSANDO_END omission rule — `unit`-level and mockable. This is the highest-leverage surviving-mutant risk in the session.
- **`ui/edit` (6C) has no mirrored test file at all.** `GraceModeManager`/`EditModeManager` are covered only by e2e happy-path robot tests (adequate for their scope) plus fixture mocks that assert nothing about these classes (`UIActionFlagBehaviorTest`'s `mockStatic(GraceModeManager)` stub contributes zero coverage here). The subtle logic — `mouseReleased` time-based click-vs-drag detection, `mouseDragged` pendingCancel/pendingConnect, `enterGraceNoteInsert` abort path, `finish(cancel)` mutation bracketing, `GraceModeStateDidChangeNotification` payloads, and `EditModeManager.decorateElement`/`makePreviewElement`/`elementWasModified` (REPEAT_LEFT+RIGHT → REPEAT_LEFT_RIGHT coalescing) branches — is all unit-testable with mocked collaborators and entirely missing.
- **`SelectionCoordinator` (6A) enablement predicates that gate toolbar flags are dark** even though the class is otherwise well-covered: `selectionHasRests` (gates the `DISABLE_IN_RESTS_ONLY` flag — three branches, zero coverage), `canDeleteLine` (three guards incl. `lineCount() > 1`), `canChangeTempo`, the grace-mode `restoreActionStatesWithFlag`/`clearSavedActionStates`/`reflectElement` paths, and the `triggerReflection` dedup guard (`equals(lastReflectedSelection) → skip`).

**Two systemic gaps recur across 6A and 6B:** (1) **cross-line selection guards** — `isElementSelected`/`isLineSelected`/`isGlissandoSelected`'s `activeLineIndex != lineIndex → false` guard, and `activateLine`'s clear-previous-line contract, are untested because every fixture activates only line 0; (2) **reversed-drag** — `LineSelectionState.extendSelectionTo`'s `anchor > index` branch is uncovered (setups always pass `anchor < index`). Also dark: `clearSelection`/`resetElementSelection` post-conditions, `canToggleTie/Trill`, `canFlipStemDirection`, and the `ClipboardManager` pasteboard accessors (`isEmpty`/`getSize`/`getFirstElement`/`getLastElement`/`clear` — only `addElement` normalization is tested).

**Wrong-level (7): pure in-memory state tested only through the Swing pipeline.** `selectGlissando`/`hasGlissandoSelection`/`isElementSelected` (e2e `NoteConnectionTest` robot clicks), `isLineSelected` (e2e `SelectionTest`), and the `ui/edit` mouse state-machine logic (6C) carry no rendering dependency and should be unit tests. Separately, `e2e SelectionTest.testDragSelect` asserts `isGreaterThanOrEqualTo(3)` — a weak lower-bound oracle that passes even if the rubber-band selects too many elements (inadequate).

**Level distribution holds the rubric:** overwhelmingly `unit` (selection state machines, enablement predicates, adjustment geometry/clamping, clipboard normalization) — all assertable with the `MainFrame.getInstance()` singleton mocked. Genuine `none` (10): `ElementSelection` record components, `TupletToggleInfo` field accessors (its compact-constructor guard is `missing`, not `none`), `ScoreActions` (pure interface — all seven callbacks), the two `repaint(Graphics2D)` methods, and the `Adjustment` constructor (pure listener registration). No behavior in scope genuinely required e2e beyond the robot-driven cases already noted as wrong-level.

**Two classifications resolved during assembly:** (1) the `Adjustment` constructor was downgraded `missing → none` after reading lines 41–45 — it only stores `scoreView` and registers two listeners, no branching or computation; (2) `SelectionCoordinator.globalMouseReleasedListener` was reclassified `e2e/inadequate → unit/missing` — its cleanup body (clear drag rectangle + null the dragging line) is our logic and directly invokable via package-private/reflection, so the AWT dispatch is not the risk.

**No production dead code found** (contrast Sessions 3–5): `ScoreActions` is a live interface implemented by `ScoreView`; `activateLine`'s cross-line path is "dead from the test suite's perspective" — a coverage gap, not unreachable production code.

**Production observation filed as a tracked GitHub issue (#411; do not fix during audit):** `VerticalAdjustment.drag()` derives `diffY` from AWT **pixel** mouse coordinates (`endPoint.y`, from `MouseEvent.getY()`) and adds it — with no `pxToSs` conversion — to staff-space `…Ss` fields (`getAttributionStartYSs`/`getTopPaddingSs`/`getRowHeightAdjustmentSs`/`getUserYOffsetSs`/`getYPositionSs`/`getYShiftSs`/`getVerticalPositionSs`, lines 146–240), while line 207 adds the same value to the legacy pixel field `setYPosPx`. Verified against the unit-conversion guide — off by ~8× at `DEFAULT_PIXELS_PER_STAFF_SPACE = 8.0`, and the `rect.y`-from-`(int) getTopSs()` assignments (line 387+) make the subtraction itself unit-mixed; exact runtime severity needs a repro during remediation.

## 7. `ui/component` (audited 2026-05-22)

Audited via nine production-first sub-audits run in three waves of three: **7A** control plane (`ScoreViewController` + input dispatch); **7B** `ScoreView`; **7C** hit-test / drag / selection / preview routing; **7D** `MainFrame` (window singleton, save & data-loss guard); **7E** line/score rendering geometry; **7F** score panels & text components; **7G** toolbars; **7H** input & text widgets; **7I** buttons/borders/frames & navigation helpers. Scope was 62 production classes (+3 `package-info`). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

**Two scope corrections during assembly:** (1) `ScoreComponent` was audited by both 7E and 7F — its rows are kept under **7E** only (more complete; includes `setMargin`) and removed from 7F. (2) The 7G sub-audit over-reached into `songscribe.ui.action` collaborators referenced by toolbar constructors — `DurationActionGroup.barWasSelected()` and the `ActionGroup` base-class branches (`setSelected(action,false)`, `selectNext()` wrap-around, `select()` idempotency, `getPreviousSelected()`) plus `LyricEditorActionAuditTest` — those classes belong to `ui/action` and were already audited in **§5A**; their rows were trimmed here to avoid double-counting (carry-forward already recorded in §5).

### 7A. Score-view control plane (ScoreViewController + input dispatch)

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ScoreViewController | `deleteNote` removes a single element and decrements element count | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteRemovesOneElement` | adequate | keep |
| ScoreViewController | `deleteNote` removes preceding paired grace note and returns count 2 | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteRemovesPrecedingPairedGraceNote` | adequate | keep |
| ScoreViewController | `deleteNote` does NOT remove an unpaired (standalone) grace note | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteDoesNotRemoveUnpairedGraceNote` | adequate | keep |
| ScoreViewController | `deleteNote` strips incoming glissando from the previous note | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteRemovesGlissandoFromPreviousNote` | adequate | keep |
| ScoreViewController | `deleteNote` shifts subsequent elements by the correct offset when a paired grace note is also deleted | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteShiftsSubsequentElementsWhenGraceNoteRemoved` | adequate | keep |
| ScoreViewController | `deleteSelection` loop skips the grace-note index to avoid double-processing | unit | `ScoreViewControllerTest.DeleteNote.testSelectionLoopSkipsGraceNoteIndex` | adequate | keep |
| ScoreViewController | `deleteSelection` loop terminates correctly when grace pair straddles selection start | unit | `ScoreViewControllerTest.DeleteNote.testSelectionLoopWithGraceNoteAtSelectionStart` | adequate | keep |
| ScoreViewController | `deleteSelection` removes grace note that precedes the selection | unit | `ScoreViewControllerTest.DeleteNote.testSelectionLoopWithGraceNoteBeforeSelection` | adequate | keep |
| ScoreViewController | `deleteNote` breaks syllable relation when deleted note is a syllable terminus | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteBreaksSyllableRelationWhenDeletedNoteIsTerminus` | adequate | keep |
| ScoreViewController | `deleteNote` preserves syllable relation when deleted note is a chain member (not terminus) | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNotePreservesSyllableRelationWhenDeletedNoteIsChainMember` | adequate | keep |
| ScoreViewController | `handleDelete` on lyric selection removes the lyric and clears the lyric selection | unit | `ScoreViewControllerTest.DeleteLyric.testDeleteRemovesSelectedLyricAndClearsLyricSelection` | inadequate | strengthen — invokes `handleDelete` via raw reflection instead of widening to package-private; also only covers the lyric branch |
| ScoreViewController | `handleDelete` on element selection with contiguous range: strips glissando from preceding note, shifts elements, cleans syllable relations, removes range | unit | — | missing | write unit test (widen `handleDelete` to package-private; use `ReflectionTestHelper`) |
| ScoreViewController | `handleDelete` on element selection with paired-grace-note at selection start: falls back to per-element loop via `deleteSelection` | unit | — | missing | write unit test |
| ScoreViewController | `handleDelete` on glissando selection: removes the glissando from the source element | unit | — | missing | write unit test |
| ScoreViewController | `handleDelete` when no element or glissando selection and `canDeleteLine()` is true: removes the selected line | unit | — | missing | write unit test |
| ScoreViewController | `handleDelete` confirms before deleting when selection invalidates an ending | unit | — | missing | write unit test (mock `EndingConfirms.confirmInvalidation()` returning false → no deletion) |
| ScoreViewController | `handleCopy` copies selected element range into `ClipboardManager` (correct bounds) | unit | — | missing | write unit test |
| ScoreViewController | `handleCopy` is a no-op when there is no active element selection | unit | — | missing | write unit test |
| ScoreViewController | `handleCut` copies selection then deletes it (delegates to `handleCopy` + `handleDelete`) | unit | — | missing | write unit test |
| ScoreViewController | `handlePaste` is a TODO-only stub — `PasteboardOpCommand(PASTE)` is a silent no-op | unit | — | missing | record as known defect; write a test that asserts clipboard contents are inserted once implemented |
| ScoreViewController | `handlePasteboardOp` routes CUT/COPY/DELETE/PASTE to correct handler | unit | — | missing | write unit test (mock `score.isFocusOwner()` true/false; verify each branch) |
| ScoreViewController | `handlePasteboardOp` is a no-op when `score.isFocusOwner()` returns false | unit | — | missing | write unit test |
| ScoreViewController | `handleDeselect` calls `score.deselect()` only when score has focus | unit | — | missing | write unit test (both focus states) |
| ScoreViewController | `handleSelectLine` calls `state.selectAll()` and notifies score when active selection exists | unit | — | missing | write unit test |
| ScoreViewController | `handleSelectLine` is a no-op when no active selection | unit | — | missing | write unit test |
| ScoreViewController | `handleInsertLine` inserts line at `selectedLine + shift` when a line is selected | unit | — | missing | write unit test |
| ScoreViewController | `handleInsertLine` with `shift == ADD` inserts line at end regardless of selection | unit | — | missing | write unit test |
| ScoreViewController | `handleInsertLine` shows an error dialog when no line is selected and shift != ADD | unit | — | missing | write unit test (mock `OptionDialogs`) |
| ScoreViewController | `modeDidChange`: clears selection when mode != SELECT | unit | — | missing | write unit test |
| ScoreViewController | `modeDidChange`: syncs preview element on EDIT entry | unit | — | missing | write unit test (verify `score.setPreviewElement` called) |
| ScoreViewController | `modeDidChange`: enables/disables horizontal and vertical adjustment controls per mode | unit | — | missing | write unit test |
| ScoreViewController | `hasLineLayoutMutation` returns true for `LineScopedMutation`, `LineInsertion`, `LineDeletion`; false otherwise | unit | `ScoreViewControllerTest.LayoutInvalidation` (6 tests) | adequate | keep |
| ScoreViewController | `hasFullRelayoutMutation` returns true for `FontChange`, `MetadataChange`, `LayoutChange`; false for others | unit | — | missing | write unit test |
| ScoreViewController | `songDidChange` calls `lineComponent.invalidateLayout()` only for the target line when mutation is line-scoped with a non-null target | unit | — | missing | write unit test |
| ScoreViewController | `songDidChange` calls `score.viewChanged()` when full-relayout mutation is present | unit | — | missing | write unit test |
| ScoreViewController | `songDidChange` restarts repaint debounce timer | unit | — | missing | write unit test (capture timer restart; verify score.repaint() eventually called) |
| ScoreViewController | `canToggleTuplet` returns cached value when cache is populated; delegates to `operations` when null | unit | — | missing | write unit test |
| ScoreViewController | `warmTupletCache` is invoked on `MusicSelectionDidChangeNotification` before HIGH_PRIORITY handlers | unit | — | missing | write unit test (verify priority ordering: cache is warm when TupletAction reads it) |
| ScoreViewController | `prefsDidChange(LOOP_PLAYBACK)` calls `scoreActions.syncPlaybackPrefs()` | unit | — | missing | write unit test |
| ScoreViewController | `prefsDidChange(PAGE_SIZE)` calls `scoreActions.updatePageLayout()` when score is initialized | unit | — | missing | write unit test |
| ScoreViewController | `prefsDidChange(ALL)` triggers both `syncPlaybackPrefs` and `updatePageLayout` | unit | — | missing | write unit test |
| ScoreViewController | `textEditingDidChange` disables key bindings when editing; enables when not | unit | — | missing | write unit test (both true/false) |
| ScoreViewController | `handleToggleBeam` delegates to `operations.toggleBeaming()` and emits one `BeamingAddition` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleBeamEmitsOneBeamingAddition` | adequate | keep |
| ScoreViewController | `handleToggleBeam` is a no-op when there is no active selection | unit | — | missing | write unit test |
| ScoreViewController | `handleToggleTie` delegates to `operations.toggleTie()` and emits one `TieAddition` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTieEmitsOneTieAddition` | adequate | keep |
| ScoreViewController | `handleToggleTuplet` emits a `TupletAddition` for a fresh triplet | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTupletEmitsOneTupletAddition` | adequate | keep |
| ScoreViewController | `handleToggleTuplet` emits `TupletRemoval` + `TupletAddition` when changing tuplet grade | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTupletEmitsRemovalAndAdditionForGradeChange` | adequate | keep |
| ScoreViewController | `handleAddDynamics` emits `CrescendoAddition` or `DiminuendoAddition` per flag | unit | `ScoreViewControllerCommandHandlerTest.testHandleAddDynamicsEmitsOneAddition` | adequate | keep |
| ScoreViewController | `handleRemoveDynamics` emits at least one removal mutation | unit | `ScoreViewControllerCommandHandlerTest.testHandleRemoveDynamicsEmitsRemovals` | inadequate | strengthen — asserts only `isNotEmpty()`; should assert specific removal mutation types and count |
| ScoreViewController | `handleToggleTrill` emits one `RangeElementAddition` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTrillEmitsOneNotificationWithSingleRangeElementAddition` | adequate | keep |
| ScoreViewController | `handleFlipStemDirection` emits one `ElementModification` per selected note with `ElementField.UPPER` | unit | `ScoreViewControllerCommandHandlerTest.testHandleFlipStemDirectionEmitsOneNotificationWithModificationsPerNote` | adequate | keep |
| ScoreViewController | `handleFirstSecondEnding` emits `ElementInsertion` + `RangeElementAddition(Ending)` when cached result is valid | unit | `ScoreViewControllerCommandHandlerTest.testHandleFirstSecondEndingEmitsOneNotificationWithBarlineAndEnding` | adequate | keep |
| ScoreViewController | `handleFirstSecondEnding` is a no-op when cached result is null or invalid | unit | — | missing | write unit test |
| ScoreViewState | All getters/setters (mode, control, horizontalAdjustment, verticalAdjustment) | none | — | — | no test needed — pure data holder |
| ScoreInputHandler | `mouseClicked` with non-BUTTON1 event: no focus request | e2e | `SelectionTest.ClickAndMode` (implicit) | wrong-level | lower to unit — mock callback, construct real `MouseEvent`, assert `requestFocusInWindow` not called |
| ScoreInputHandler | `mouseClicked` with BUTTON1: calls `callback.requestFocusInWindow()` | e2e | `SelectionTest.ClickAndMode` (implicit) | wrong-level | lower to unit — mock callback, construct real `MouseEvent`, assert `requestFocusInWindow` called |
| ScoreInputHandler | `mousePressed` / `mouseReleased`: shows popup when `isPopupTrigger()` | e2e | — | missing | write unit test (mock callback; stub popup non-null; verify `popup.show()` called) |
| ScoreInputHandler | `mousePressed` / `mouseReleased`: is a no-op when `isPopupTrigger()` is false | unit | — | missing | write unit test |
| ScoreInputHandler | `keyPressed(ALT)`: clears preview element and sets `altPressed=true`, triggers repaint | e2e | — | missing | write unit test (mock callback; verify `repaint()` called) |
| ScoreInputHandler | `keyPressed(ESCAPE)` in SELECT mode with no active text editing: posts `DeselectCommand` | e2e | `SelectionTest.BasicSelection.testClickEmptySpaceDeselects` (indirect) | wrong-level | lower to unit — mock callback returning `Mode.SELECT`; verify `MessageCenter.post(DeselectCommand)` |
| ScoreInputHandler | `keyPressed(ESCAPE)` when grace mode is in progress: delegates to grace mode manager | unit | — | missing | write unit test |
| ScoreInputHandler | `keyReleased(ALT)`: sets `altPressed=false` | unit | — | missing | write unit test |
| ScoreInputHandler | `KeyAction.handlePitchAdjustment` UP: decrements staff position if above lower bound | unit | — | missing | write unit test (mock callback returning EDIT/KEYBOARD; verify staffPosition decremented) |
| ScoreInputHandler | `KeyAction.handlePitchAdjustment` DOWN: increments staff position if below upper bound | unit | — | missing | write unit test |
| ScoreInputHandler | `KeyAction.handlePitchAdjustment`: no-op when mode != EDIT or control != KEYBOARD | unit | — | missing | write unit test |
| ScoreInputHandler | `installKeyBindings` registers one binding per key code in component input/action maps | unit | — | missing | write unit test (construct real `JComponent`; verify map sizes and action types) |
| InputUtils | `CustomDocumentFilter.insertString` rejects text not matching regex; emits beep | unit | — | missing | write unit test (construct filter with pattern; invoke `insertString`; verify beep and no insertion) |
| InputUtils | `CustomDocumentFilter.insertString` accepts text matching regex | unit | — | missing | write unit test |
| InputUtils | `CustomDocumentFilter.replace` rejects non-matching replacement; accepts matching | unit | — | missing | write unit test |
| InputUtils | `DecimalDocumentFilter.insertString` rejects insertion that would make document non-decimal | unit | — | missing | write unit test |
| InputUtils | `DecimalDocumentFilter.replace` rejects replacement that would make document non-decimal | unit | — | missing | write unit test |
| InputUtils | `DecimalDocumentFilter` accepts partial decimal in progress (e.g. "1.", "0.5") | unit | — | missing | write unit test |
| InputUtils | `RegexFormatter.stringToValue` throws `ParseException` when input does not match pattern | unit | — | missing | write unit test |
| InputUtils | `RegexFormatter.stringToValue` returns value when input matches | unit | — | missing | write unit test |
| InputUtils | `addNumericFilter(component)` installs integer-only document filter on `JTextField` | unit | — | missing | write unit test |
| InputUtils | `addNumericFilter(component, true)` installs decimal-allowing filter on `JTextField` | unit | — | missing | write unit test |
| InputUtils | `addInputFilter` on `JSpinner` installs `RegexFormatter` on the spinner's text field | unit | — | missing | write unit test |
| InputHandlerCallback | Interface — pure abstraction with no logic | none | — | — | no test needed |

**7A notes (quality concerns):**

The most significant defect confirmed by this audit is `handlePaste()`, which is a body-only TODO comment — every `PasteboardOpCommand(PASTE)` is silently swallowed with no effect, making the paste feature completely non-functional. Sessions 5 and 6 flagged `PasteAction` as a silent no-op; this audit confirms the root cause is in `ScoreViewController.handlePaste()`. There is no existing test for it, and the correct action is to both document the defect and write a failing test that guards against the stub remaining once the implementation lands.

Two broader quality concerns apply across the suite. First, the sole `handleDelete` unit test (`DeleteLyric`) invokes the private method via raw `java.lang.reflect` rather than widening it to package-private — this couples the test to the internal method name and access level and means a rename or access-change silently breaks the test at runtime rather than compile-time. Second, `testHandleRemoveDynamicsEmitsRemovals` asserts only `isNotEmpty()` on the mutation list, which would pass if the implementation emitted unrelated mutation types; it should assert specific removal-mutation classes and count.

The `ScoreInputHandler` key-press and mouse-click behaviors are logically unit-testable (the `InputHandlerCallback` interface is already designed for mocking) but are either untested or buried in e2e at the wrong level. `InputUtils` filter logic — particularly the `DecimalDocumentFilter` prospective-text validation and `CustomDocumentFilter` regex gating — is pure state-logic with no Swing dependencies and is entirely untested, representing a concrete gap in a subsystem that guards all text input fields across the application.

### 7B. ScoreView

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ScoreView | `setFonts` no-op when `newFonts.equals(documentFonts)` — no mutation, no cascade | unit | `ScoreViewSetFontsTest.testSetFontsWithEqualSnapshotIsNoOp` | adequate | — |
| ScoreView | `setFonts` with one role changed emits exactly one `FontChange` mutation inside modification bracket, updates `documentFonts` reference | unit | `ScoreViewSetFontsTest.testSetFontsWithSingleRoleChangePostsOneFontChange` | adequate | — |
| ScoreView | `setFonts` with all roles changed still emits exactly one `FontChange` (not one per role) | unit | `ScoreViewSetFontsTest.testSetFontsWithAllRolesChangedPostsExactlyOneFontChange` | adequate | — |
| ScoreView | `setFonts` replay with old snapshot restores prior `documentFonts` (undo-path simulation) | unit | `ScoreViewSetFontsTest.testReplayWithOldFontsRestoresPriorState` | adequate | — |
| ScoreView | `getDocumentFonts` throws `IllegalStateException` before `documentFonts` is initialized | unit | — | missing | Add unit test: construct headless `ScoreView(null)`, call `getDocumentFonts()`, assert `IllegalStateException` |
| ScoreView | `installDocumentFonts` sets `documentFonts` field (non-undoable load path, no mutation recorded) | unit | `ScoreViewSetFontsTest` (calls it in `setUp` only as fixture, never asserts non-mutation contract) | inadequate | Promote to an explicit test: call `installDocumentFonts`, assert no `FontChange` is posted and `getDocumentFonts()` returns the installed instance |
| ScoreView | `rebuildLyricRenderMetrics` — early exit when `song == null` or `documentFonts == null` | unit | — | missing | Add unit test: headless `ScoreView(null)`, call `rebuildLyricRenderMetrics()`, assert no exception and `getLyricRenderMetrics()` guard path unchanged |
| ScoreView | `rebuildLyricRenderMetrics` — skips rebuild when lyrics font is unchanged (idempotency guard) | unit | — | missing | Add unit test: bootstrap metrics with font F, call again with same font, assert same `LyricRenderMetrics` instance returned |
| ScoreView | `rebuildLyricRenderMetrics` — rebuilds when lyrics font changes | unit | — | missing | Add unit test: set one lyrics font, rebuild, change font, rebuild again, assert new metrics instance with new font |
| ScoreView | `getSuggestedFileName` — numeric song number zero-padded to three digits, combined with diacritic-stripped title | unit | — | missing | Add unit test with numeric `number` and title containing diacritics; assert `"003 Foo"` form |
| ScoreView | `getSuggestedFileName` — non-numeric song number used verbatim, followed by title | unit | — | missing | Add unit test with non-numeric `number` (e.g. `"A"`); assert `"A Title"` form |
| ScoreView | `getSuggestedFileName` — empty song number omits leading separator | unit | — | missing | Add unit test: `number = ""`, assert result equals diacritic-stripped title only (no leading space) |
| ScoreView | `defaultUpperNote` — `staffPosition > 0` → returns `true` | unit | `PreviewElementManagerTestBase` mocks it, never tests it directly | missing | Add direct unit test: static method, no setup needed; assert `true` for positive staff position |
| ScoreView | `defaultUpperNote` — `staffPosition == 0` non-grace note → returns `false` | unit | — | missing | Add unit test: `staffPosition = 0`, non-grace element type; assert `false` |
| ScoreView | `defaultUpperNote` — grace note at any staff position → returns `true` | unit | — | missing | Add unit test: `isGraceNote() == true`; assert `true` regardless of staff position |
| ScoreView | `getNoteYPosPx` — computed coordinate formula: `middleLineYPx + staffPosition * STAFF_POSITION_OFFSET_PX + lineIndex * rowHeightPx` | unit | — | missing | Add unit test: construct headless `ScoreView`, inject `middleLineYPx`, `rowHeightPx` via setters, assert formula for non-trivial inputs |
| ScoreView | `drawWidthIfWiderLine` — when last element extends past `lineWidth - idealSpace`, rescales all intermediate x-offsets proportionally | unit | — | missing | Add unit test: build a `Line` with elements at known x positions that exceed the threshold; call `drawWidthIfWiderLine`, assert rescaled positions |
| ScoreView | `drawWidthIfWiderLine` — when last element does NOT exceed threshold, x-offsets unchanged | unit | — | missing | Add unit test: elements within threshold; assert no x-offset changes |
| ScoreView | `drawWidthIfWiderLine` — line with ≤ 1 effective element is a no-op | unit | — | missing | Add unit test: single-element line; call `drawWidthIfWiderLine`, assert no mutation |
| ScoreView | `openFile` returns `false` and shows error dialog on `NewerVersion` / `ParseError` / `IoError` / `LineWidthTooLarge` result | unit | — | missing | Add unit tests (one per failure branch) using a fixture or mocked `SongLoader`; assert return value `false` and correct log/dialog call |
| ScoreView | `openFile` returns `true`, calls `installDocumentFonts` then `setSong`, then fires `onFileOpened` callback on success | unit | — | missing | Add unit test with a real `.mssw` fixture; assert `true` returned and callback invoked |
| ScoreView | `openFile` refuses a line-width-too-large file and returns `false` | unit | — | missing | Covered by the `LineWidthTooLarge` branch above; may share a test |
| ScoreView | `setKeyBindingsEnabled(false)` replaces each registered binding with `"none"` sentinel | unit | — | missing | Add unit test: register synthetic key bindings, call `setKeyBindingsEnabled(false)`, assert each stroke maps to `"none"` in the input map |
| ScoreView | `setKeyBindingsEnabled(true)` restores all bindings from the stored map | unit | — | missing | Add unit test: disable then re-enable; assert original action keys restored |
| ScoreView | `isInitialized` returns `false` before `init()` and `true` after (note: method actually checks `song != null`, body says `return song != null`) | unit | Mocked in unrelated action tests (`isInitialized()` stub) but never tested on real instance | missing | Add unit test: headless `ScoreView(null)`, assert `isInitialized() == false`; after `setSong(new Song())` assert `true` |
| ScoreView | `getDocumentFonts` / `getFont(FontKey)` delegation — `getFont` delegates to `documentFonts.getFont(key)` | unit | `ScoreViewSetFontsTest.testSetFontsWithSingleRoleChangePostsOneFontChange` asserts this | adequate | — |
| ScoreView | `paintComponent` / `drawEditElements` — pure Swing rendering, no computed geometry asserted | none | — | — | No test warranted |
| ScoreView | `updateUI` / `updateScoreSurroundBackground` — pure Swing wiring | none | — | — | No test warranted |
| ScoreView | `addOverlay` / `initEditPopup` / `initScorePanel` / `initMainPanel` — Swing component wiring | none | — | — | No test warranted |
| ScoreView | `selectionChanged` — posts `MusicSelectionDidChangeNotification` | none | Post is a one-liner delegation; behavior is trivial wiring | — | No test warranted |
| ScoreView | `getWindow` / `createSVG` / `createImageForExport` — pure delegation to Swing/export collaborators | none | — | — | No test warranted |

**7B notes (quality concerns):**

`ScoreViewSetFontsTest` is thorough and correct for the `setFonts` mutation-bracket contract. The main gap is that `installDocumentFonts` has no independent test asserting its non-undoable contract — it is used only as fixture setup in `setUp()`, so the contract (no `FontChange` posted, `documentFonts` field set) is never directly verified and could regress silently. The `rebuildLyricRenderMetrics` idempotency guard (skip-if-font-unchanged) and its early-exit branches are completely untested; this method is called on every font change and every layout pass, making its correctness critical. The `getSuggestedFileName` method has three distinct branches (numeric, non-numeric, empty number) and calls `StringUtils.stripDiacritics` — all pure string logic, no test warranted under any reading other than unit. `defaultUpperNote` is a static pure boolean but is mocked away in `PreviewElementManagerTestBase` rather than tested directly, meaning any logic inversion would go undetected. `getNoteYPosPx` encodes the core coordinate formula that all rendering depends on, yet has no test. `drawWidthIfWiderLine` contains a proportional-rescaling loop with boundary conditions, also untested. The `openFile` failure branches (four distinct sealed-record arms) carry user-visible error dialogs and return values that are never verified at the unit level.

### 7C. Hit-test, drag, selection & preview routing

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ElementHitTest` | `hitTestElement`: iterates all elements, converts point px→ss, returns first index whose hit rect contains the point (or -1) | unit | — | missing | Add unit test: point inside first element returns 0; point between elements returns -1; non-interactable element skipped; empty line returns -1 |
| `ElementHitTest` | `buildElementHitRect(expandToMinimum=true)`: symmetric expansion — narrow/short elements padded to `MIN_HIT_SIZE_PX` on both sides (geometry math) | unit | — | missing | Add unit test: element narrower than 8px receives symmetric x-expansion; element at or above threshold is not expanded; verify exact rect coords |
| `ElementHitTest` | `buildElementHitRect(expandToMinimum=false)`: no expansion (used for drag-selection intersection) | unit | — | missing | Add unit test asserting no expansion applied regardless of element width; verify rect equals natural width/height |
| `HitResult` | Sealed record hierarchy (data holders only) | none | — | — | — |
| `LineSelectionHandler` | `hitTest` cascade: element head → glissando → staff-line (Y within `STAFF_HIT_RADIUS_SS=2.0` and X ≤ header right edge) → nothing | unit | — | missing | Add unit tests for each branch: point on element returns `ElementHead`; point at glissando returns `Glissando`; grace glissando returns `GraceGlissando`; Y within radius + header X returns `StaffLine`; outside all returns `Nothing` |
| `LineSelectionHandler` | `hitTest` staff-line branch: boundary — `STAFF_HIT_RADIUS_SS` included; X past header not selected | unit | — | missing | Add boundary tests: Y exactly at ±2.0 ss hits; Y at 2.0+ε does not hit; X past `headerRightEdgeSs()` returns `Nothing` |
| `LineSelectionHandler` | `handleDrag` rubber-band rect: pixel clamping to component bounds, rect computed from `dragStart` and clamped mouse coords | e2e | `SelectionTest.testDragSelect` | inadequate | `testDragSelect` asserts `isGreaterThanOrEqualTo(3)` — weak; change to `isEqualTo(3)` so a clamping regression that selects the wrong count is caught |
| `LineSelectionHandler` | `calculateLineSelectionFromDrag`: converts px drag rect to ss, intersects with element hit rects (no-expand), sets anchor by proximity | unit | — | missing | Add unit test: drag rect enclosing elements 0 and 2 (skipping 1) — selection contains exactly 0 and 2; anchor set to endpoint closer to drag start |
| `LineSelectionHandler` | `handlePress` routing: shifts press to selection, preserves multi-select on shift+ElementHead, delegates to selection/glissando/line-select branches | e2e | `SelectionTest` (BasicSelection, RangeSelection, LineSelection) | adequate | — |
| `LineSelectionHandler` | `handleClick` shift-extend: shift+click on note head extends selection from anchor to clicked index | e2e | `SelectionTest.testShiftClickExtendsSelection`, `testShiftClickShrinksSelection` | adequate | — |
| `NoteDragHandler` | `handleDrag` delta arithmetic: `deltaYPx = screenY - pressScreenY` → `pxToSs` → `ssToSp` → `newPos = originalSp + deltaSp` | unit | `NoteDragHandlerTest` (`DeltaComputationAndClamping`) | adequate | — |
| `NoteDragHandler` | `handleDrag` group clamping: delta clamped so no group note exits `[MIN_STAFF_POSITION_SP, MAX_STAFF_POSITION_SP]` | unit | `NoteDragHandlerTest.testClampingAtLower/UpperBoundary` | adequate | — |
| `NoteDragHandler` | `handlePress` group building: multi-selection + tie-chain expansion into `DragEntry` list | unit | `NoteDragHandlerTest` (`DragGroupBuilding`) | adequate | — |
| `NoteDragHandler` | `handleRelease` mutation emission: `ElementModification` with `beforeClone` at press-time pitch and `PITCH` field set | unit | `NoteDragHandlerTest.testBeforeCloneHasOriginalPitchAfterRelease` | adequate | — |
| `NoteDragHandler` | `handleRelease` unison-glissando removal: CONNECTED glissando from/to dragged note removed when pitches become equal after drag | unit | `NoteDragHandlerTest` (`GlissandoCleanup`) | adequate | — |
| `NoteDragHandler` | `handleRelease` grace-note cleanup: grace removed when dragged to host pitch; host note preserved; host dragged to match grace also removes grace | unit | `NoteDragHandlerTest` (`GraceNoteValidity`) | adequate | — |
| `NoteDragHandler` | `handleRelease` no-drag on preserved multi-selection collapses selection to clicked note without re-playing | unit | — | missing | Add test: press on selected note in multi-selection, release without drag → single note selected (selection collapsed), no second note-on event |
| `NoteDragHandler` | `handlePress` guards: not SELECT mode returns false; MIDI playing returns false; shift+click returns false; hit miss returns false | unit | — | missing | Add tests for each early-return guard to ensure press does not activate drag in disallowed states |
| `PreviewElementManager` | `computeGlissandoZone`: all branches (xIndex≤0, null type, source rest, target rest, same pitch, CONNECTED requires right note, SLIDE_OUT does not) | unit | `PreviewElementManagerGlissandoZoneTest` | adequate | — |
| `PreviewElementManager` | `calculateStaffPositionFromMouse`: `ssToSp(mouseYss - middleLineYSs)` — converts Y offset to staff-position integer | unit | — | missing | Add unit test: mouseYss == middleLineYSs returns 0; mouseYss one staff-space below returns expected positive sp; verify rounding behaviour at half-step boundary |
| `PreviewElementManager` | `isValidStaffPosition`: bounds check `[MIN_STAFF_POSITION_SP, MAX_STAFF_POSITION_SP]` inclusive | unit | — | missing | Add boundary tests: MIN and MAX are valid; MIN-1 and MAX+1 are invalid |
| `PreviewElementManager` | `applyStaffPosition`: rest snaps to type default; pitched note takes mouse sp | unit | — | missing | Add unit test: rest element → `getDefaultStaffPosition()`; pitched note → exact staff position passed in |
| `PreviewElementManager` | `isPositionBlockedByTerminal`: three-branch routing — empty line (false); mouse directly on terminal with non-replaceable preview (blocked); mouse directly on terminal with replaceable preview (unblocked); append slot past terminal (always blocked) | unit | `PreviewElementManagerTerminalRoutingTest` | adequate | — |
| `PreviewElementManager` | `handleClick` glissando-placeholder path: `zoneType == null` is a no-op; valid zone applies glissando to source note at `xIndex - 1` | unit | — | missing | Add unit test: valid zone → source note has glissando of zone type; null zone → no modification |
| `PreviewElementManager` | `handleClick` terminal-replace path: `REPEAT_RIGHT` preview on terminal → `replaceTerminal`; non-terminal type → blocked | unit | `PreviewElementManagerTerminalRoutingTest` (`TerminalReplacement`) | adequate | — |
| `PreviewElementManager` | `handleClick` `forceInsert=true` path (grace mode): always inserts even when `xPosSsMatchesElement`; normal path (`forceInsert=false`) modifies | unit | — | missing | Add unit test: `handleClick(lc, true)` with xMatchesElement=true inserts a new element (count increases); `handleClick(lc, false)` modifies in-place (count unchanged) |
| `PreviewElementManager` | `modifyExistingElement` decoration preservation: fermata, trill, articulation, dynamic attachment carried over | unit | `PreviewElementManagerAttachmentTest` | adequate | — |
| `PreviewElementManager` | `modifyExistingElement` tuplet removal: different type/dot-count removes tuplet; same type+dot-count preserves it | unit | `PreviewElementManagerTupletTest` (`ModifyExistingElement`) | adequate | — |
| `PreviewElementManager` | `modifyExistingElement` grace-note cleanup: when host note is paired with grace note and replacement is not a pitched note, the grace note is removed | unit | — | missing | Add unit test: paired grace + host → replace host with a rest → grace removed, element count decreases by 1 |
| `PreviewElementManager` | `insertElement` tuplet removal: inserting within tuplet span removes it; inserting after span preserves it | unit | `PreviewElementManagerTupletTest` (`InsertElement`) | adequate | — |
| `PreviewElementManager` | `insertElement` syllable/extend adjustment: `adjustSyllablesForNeighborChange`, `adjustExtendsForInsertion`, `adjustSyllablesForSuccessorAfterInsertion` called with correct indices | unit | — | missing | Add unit test verifying syllable state on adjacent elements is correct after insertion (integration with Line's lyric model) |
| `PreviewElementManager` | `trackMouse` alt-key clears preview; grace-mode locks X to `lockedInsertionXSs`; out-of-range sp clears preview; terminal block suppresses preview; grace-inside-pair suppresses preview | e2e | `ElementInsertionTest` (partial) | inadequate | The e2e tests exercise happy paths for insertion and drag-connect but do not systematically cover alt-key, grace-inside-pair suppression, or the "layout null during drag" guard; add unit tests for these pure-logic branches by driving `trackMouse` with a mocked `LineComponent` |
| `PreviewElementManager` | `validateAndGetPreviewElement`: returns null when `elementWasModified` is true (stale-preview guard) | unit | — | missing | Add unit test: `elementWasModified` returns true → method returns null and `previewElementDidChange` is called |

**7C notes (quality concerns):**

The most significant gap is that `ElementHitTest` and `LineSelectionHandler` have no unit tests whatsoever despite containing non-trivial geometry computations. `buildElementHitRect` performs symmetric expansion arithmetic that could silently regress (off-by-one in expansion, wrong axis affected), and the `hitTest` cascade in `LineSelectionHandler` has five distinct branches including a radius comparison in staff-space units that could fail if the constant or coordinate system drifts. The rubber-band drag assertion in `SelectionTest.testDragSelect` uses `isGreaterThanOrEqualTo(3)` — a classic weak-assertion pattern flagged in prior sessions — which would pass even if clamping or intersection logic selected the wrong count of elements. On `NoteDragHandler`, the no-drag-on-preserved-multi-selection collapse path and the handlePress guard conditions are untested; a future refactor could inadvertently break one of the early-return guards (shift/play/mode) and nothing would catch it. For `PreviewElementManager`, the `handleClick` glissando-placeholder path, the `forceInsert=true` (grace-mode) routing divergence, the `modifyExistingElement` grace-note cleanup branch, and the `validateAndGetPreviewElement` stale-preview guard all lack unit coverage; the last is a silent data-loss guard whose failure would only manifest as an accidental double-application of a modification. The `trackMouse` method's pure-logic branches (alt-key, grace-inside-pair, out-of-range staff position) are reachable only through mouse events in e2e but are straightforwardly unit-testable with a mocked `LineComponent`; the current e2e tests touch insertion happy paths only and do not exercise these suppression conditions.

### 7D. MainFrame (window singleton, save & data-loss guard)

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|--------------|---------|--------|
| MainFrame | `showSaveDialog()` — returns `true` immediately when `scoreView` is null (no document open) | unit | — | missing | Add unit test: mock `scoreView == null`, call `showSaveDialog()`, assert `true` returned with no dialog shown |
| MainFrame | `showSaveDialog()` — returns `true` immediately when `song.isModified()` is false (clean doc) | unit | `ShutdownTest.windowCloseOnCleanDocProgressesPastSaveCheck` (e2e, indirectly via sentinel) | wrong-level | Add unit test: mock `isModified() == false`, assert `true` returned without `OptionDialogs` call |
| MainFrame | `showSaveDialog()` — shows Save/Don't Save/Cancel dialog on dirty doc; "Save" branch delegates to `SaveAction.createAction().perform()` and returns its result | unit | `ShutdownTest.windowCloseOnDirtyDocCancelKeepsAppAlive` (e2e, only suppressed-dialog path) | wrong-level | Add unit tests for all three branches: mock `OptionDialogs`, verify Save → calls `SaveAction.perform()` and returns its boolean; Don't Save → returns `true`; Cancel → returns `false`. Three separate unit tests. |
| MainFrame | `showSaveDialog()` — "Don't Save" branch returns `true` without writing anything | unit | — | missing | Add unit test: answer = `dontSaveIdx`, assert `true` returned and `SaveAction` not invoked |
| MainFrame | `showSaveDialog()` — Cancel / dialog dismissed returns `false` | unit | `ShutdownTest.windowCloseOnDirtyDocCancelKeepsAppAlive` (e2e, only CLOSED_OPTION path) | wrong-level | Add dedicated unit test: answer = CLOSED_OPTION (neither saveIdx nor dontSaveIdx), assert `false` |
| MainFrame | `save()` — when `currentFile == null`, delegates to `saveAsNewFile()` | unit | — | missing | Add unit test: mock `currentFile` null, verify `saveAsNewFile()` path taken (e.g. via interaction or return value) |
| MainFrame | `save()` — when `currentFile` is set, delegates to `saveCurrentFile()` | unit | — | missing | Add unit test: mock non-null `currentFile`, verify `saveCurrentFile()` called |
| MainFrame | `saveCurrentFile()` — writes song, clears `isModified`, posts `DocumentWasSavedNotification`, returns `true` | unit | — | missing | Add unit test: real or mocked `SongIO.writeSong`, assert `isModified` cleared and notification posted |
| MainFrame | `saveCurrentFile()` — returns `false` and shows error dialog on `IOException` | unit | — | missing | Add unit test: stub write to throw `IOException`, assert error dialog shown and `false` returned |
| MainFrame | `saveCurrentFile()` — returns `false` immediately when `currentFile` or `scoreView` is null | unit | — | missing | Add unit tests for both null guards |
| MainFrame | `saveAsNewFile()` — returns `false` immediately when `scoreView == null` | unit | — | missing | Add unit test |
| MainFrame | `saveAsNewFile()` — when `currentFile == null`, uses `scoreView.getSuggestedFileName()` as suggested name | unit | — | missing | Add unit test: assert `getSuggestedFileName()` is used when `currentFile` is null; empty string used when `currentFile` is set |
| MainFrame | `saveAsNewFile()` — returns `false` when user cancels file chooser (dialog returns null) | unit | — | missing | Add unit test: mock `PlatformFileDialog.showSaveDialog()` returning null, assert `false` |
| MainFrame | `saveAsNewFile()` — on successful save, adds path to `RecentDocumentsManager` | unit | — | missing | Add unit test: mock successful write, verify `RecentDocumentsManager.add()` called with absolute path |
| MainFrame | `handleNewFile` — calls `showSaveDialog()` and aborts if it returns `false` (data-loss guard) | unit | — | missing | Add unit test: mock `showSaveDialog()` returning `false`, assert song is not replaced |
| MainFrame | `handleNewFile` — on confirmed save, resets `currentFile` to null and installs a fresh `Song` | unit | — | missing | Add unit test: mock `showSaveDialog()` returning `true`, assert `currentFile == null` and new `Song` set |
| MainFrame | `handleOpenFile(File)` — calls `showSaveDialog()` and aborts if `false` (data-loss guard) | unit | — | missing | Add unit test: mock `showSaveDialog()` returning `false`, assert `scoreView.openFile()` not called |
| MainFrame | `handleOpenFile(File)` — on success adds path to `RecentDocumentsManager`; on failure removes it | unit | — | missing | Add unit tests for both add/remove branches |
| MainFrame | `installShutdownTasks()` — registers `showSaveDialog` as the `"save-dirty-doc"` confirm task | e2e | `ShutdownTest` (e2e) — E2 & E3 tests indirectly confirm registration by observing veto/pass behavior | adequate | No action needed — this is genuinely wiring that belongs in e2e; ShutdownTest exercises both the veto and clean-doc pass paths |
| MainFrame | `getDisplayName()` — returns localized "Untitled" when `currentFile` is null | unit | — | missing | Add unit test: mock `currentFile == null`, assert return equals `Strings.get(Strings.DOCUMENT_UNTITLED)` |
| MainFrame | `getDisplayName()` — returns filename without extension when `currentFile` is set | unit | — | missing | Add unit test: set `currentFile` to a known file path, assert extension stripped correctly |
| MainFrame | `updateTitle()` — prefixes `•` and modified-color HTML when `isModified` is true | unit | — | missing | Add unit test: mock `isModified == true`, assert title string starts with `•` |
| MainFrame | `updateTitle()` — sets plain name (no prefix) when doc is clean | unit | — | missing | Add unit test: `isModified == false`, assert no `•` prefix |
| MainFrame | `updateTitle()` — short-circuits when `scoreView` is null or not initialized | unit | — | missing | Add unit tests for both null and `!isInitialized()` guards |
| MainFrame | `songDidChange`, `documentDidLoad`, `documentWasSaved` handlers — each calls `updateTitle()` | unit | — | missing | Add unit tests (or one parameterized test) asserting `updateTitle()` is triggered by each notification/command |
| MainFrame | `setCurrentFile()` — stores file and calls `updateTitle()` | unit | — | missing | Add unit test: call `setCurrentFile(file)`, assert `currentFile` updated and title reflects new name |
| MainFrame | `performStartupAction()` — `DO_NOTHING` branch posts nothing | unit | — | missing | Add unit test: stub prefs → `DO_NOTHING`, assert no message posted |
| MainFrame | `performStartupAction()` — Alt-key pressed forces `DO_NOTHING` regardless of pref | unit | — | missing | Add unit test: mock `ModifierState.isAltPressed() == true`, assert no message posted even when pref = `OPEN_MOST_RECENT` |
| MainFrame | `performStartupAction()` — `OPEN_MOST_RECENT` with existing file posts `OpenFileCommand` | unit | — | missing | Add unit test: stub path exists, assert `OpenFileCommand` posted |
| MainFrame | `performStartupAction()` — `OPEN_MOST_RECENT` with missing file shows error dialog | unit | — | missing | Add unit test: stub `mostRecentPath.toFile().exists() == false`, assert `OptionDialogs.showErrorMessage()` called |
| MainFrame | `performStartupAction()` — `OPEN_MOST_RECENT` with `mostRecentPath == null` returns early | unit | — | missing | Add unit test: pass `null`, assert no dialog and no message posted |
| MainFrame | `performStartupAction()` — `SHOW_FILE_CHOOSER` posts `ShowOpenDialogCommand` | unit | — | missing | Add unit test |
| MainFrame | `handleToggleLoopPlayback` — persists `LOOP_PLAYBACK` pref from command | unit | — | missing | Add unit test: post `ToggleLoopPlaybackCommand(true/false)`, assert `Prefs.getBoolean(LOOP_PLAYBACK)` matches |
| MainFrame | `handleTogglePlayWithRepeats` — persists `PLAY_WITH_REPEATS` pref from command | unit | — | missing | Add unit test: post `TogglePlayWithRepeatsCommand(true/false)`, assert `Prefs.getBoolean(PLAY_WITH_REPEATS)` matches |
| MainFrame | App quit via QuitAction triggers `Shutdown.now()` | e2e | `ShutdownTest.quitActionTriggersShutdown` | adequate | — |
| MainFrame | Window-close on dirty doc with suppressed dialog vetoes shutdown (app stays alive) | e2e | `ShutdownTest.windowCloseOnDirtyDocCancelKeepsAppAlive` | adequate (partial) | Note: this test only covers the CLOSED_OPTION (dialog dismissed) path, not the explicit Cancel button — but this is the limit of what dialog suppression can simulate; distinct Cancel branch should be unit-tested |
| MainFrame | Window-close on clean doc progresses past save check (sentinel fires) | e2e | `ShutdownTest.windowCloseOnCleanDocProgressesPastSaveCheck` | adequate | — |
| MainFrame | `print()` — `Printable.print()` returns `NO_SUCH_PAGE` for pageIndex ≥ 1 | unit | — | missing | Add unit test: call `print(g, pf, 1)`, assert `Printable.NO_SUCH_PAGE` returned |
| MainFrame | `print()` — throws when `printerJob` is null | unit | — | missing | Add unit test: call without prior `handlePrint()`, assert `RuntimeError` thrown |
| MainFrame | `handlePrint()` / `handlePrint(PrintCommand)` — pure Swing print dialog wiring | none | — | — | No test warranted — risk is the OS print dialog, not our code |
| MainFrame | `handleShowOpenDialog` / `handlePrefs` / `installDesktopHandlers` — pure wiring, no branching logic other than `Desktop.isSupported()` | none | — | — | No test warranted |
| MainFrame | `getInstance()` singleton / `InstanceHolder` / `firstRun()` stub | none | — | — | Trivial; no test warranted |

**7D notes (quality concerns):**

The data-loss guard (`showSaveDialog`) is the single most critical piece of logic in the application — a wrong return value silently discards the user's work — yet it has zero direct tests. The three `ShutdownTest` e2e tests confirm that quit paths funnel through `Shutdown.now()` and that the shutdown registry is wired correctly, but they never assert *the dialog's own branching logic*: specifically, they cannot distinguish between "Save chosen → save succeeded" vs "Save chosen → save failed → guard incorrectly returned true". The suppressed-dialog mechanism makes CLOSED_OPTION the only response, so the "Don't Save" branch and the "Save → perform()" branch are entirely unexercised. All three answer branches of `showSaveDialog` are unit-testable (mock `OptionDialogs`, mock `SaveAction`, assert return value) and should be covered as unit tests. Separately, the entire `save()` / `saveCurrentFile()` / `saveAsNewFile()` chain — including error handling and `RecentDocumentsManager` side-effects — carries moderate data-loss and data-corruption risk (a false-`true` return would fool `handleOpenFile` or `handleNewFile` into discarding a dirty doc) and has no tests at any level. The `performStartupAction()` method has three meaningful branches (including the Alt-key override) that are fully unit-testable via `mockStatic(ModifierState.class)` and pref mocking, and none is covered. The `updateTitle` / `getDisplayName` logic also has no unit tests; while UI rendering is usually `none`, the `•` prefix and the HTML-color template are string-construction logic that can silently regress.

### 7E. Line/score rendering geometry (LineComponent, LineRenderer, ScoreComponent)

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LineComponent | `staffPositionToYPx(sp)` — converts staff position to pixel Y via `getMiddleLineYPx()` + `staffPositionOffset * sp`; formula is the pixel-domain bridge for callers not yet in staff-space | unit | `ElementInsertionTest` calls it as a coordinate helper (e2e, not a unit assertion) | missing | Add unit test: given a known `middleLineYSs` and scale factor, assert the pixel result for sp=0, positive, and negative positions |
| LineComponent | `getMiddleLineYPx()` — rounds `ScaleContext.ssToPx(middleLineYSs)` to int; rounding mode matters for pixel accuracy | unit | — | missing | Unit-test: verify rounding to nearest int (e.g., 0.5 Ss rounds correctly) |
| LineComponent | `calculateMiddleLineYSs()` — `aboveStaffSs + STAFF_HALF_SS`; depends on layout result's `getAboveStaffSs()`; potential issue #411 territory (Ss arithmetic) | unit | — | missing | Unit-test: construct a minimal `LayoutResult` with known `aboveStaffSs`, assert result equals `aboveStaffSs + StaffExtents.STAFF_HALF_SS` |
| LineComponent | `getPreferredSize()` — converts `lineWidthSs` and `totalLineHeightSs` to px via `Math.ceil(ssToPx(…))`; null guard when `song == null` or `line == null` returns `(0,0)` | unit | — | missing | Unit-test: (a) null song/line yields `Dimension(0,0)`; (b) known Ss dimensions yield correct ceiling-rounded px Dimension |
| LineComponent | `ensureLayout()` / `invalidateLayout()` — dirty-flag semantics: `invalidateLayout` sets `layoutDirty=true` and nulls result; `ensureLayout` re-runs layout if dirty | unit | — | missing | Unit-test: after `invalidateLayout()`, `layoutResult` is null and `layoutDirty` is true; after `ensureLayout()`, result is non-null |
| LineComponent | `setLine()` — marks layout dirty, nulls result, creates new `LineSelectionState`, registers it with coordinator if scoreView present | unit | `PreviewElementManagerTestBase` mocks `lc`; no test for `setLine` itself | missing | Unit-test the state transitions: `layoutDirty=true`, `layoutResult=null`, `lineSelectionState` is a new instance for the supplied line |
| LineComponent | `isYInLyricBounds(y)` — delegates to `layoutResult.isYInLyricBounds(...)`; returns false when `readyLayout()` is null | unit | — | missing | Unit-test: null line → returns false; non-null with mocked layout returns delegate's result |
| LineComponent | `mouseClicked` routing — lyric double-click path vs. `selectionHandler.handleClick` vs. `PreviewElementManager.handleClick`; right-button ignored | e2e | `SelectionTest`, `ElementInsertionTest` exercise click paths implicitly | wrong-level | The routing logic itself (lyric hit vs. selection vs. preview) is a state machine — the branches could be unit-tested via mocked collaborators rather than relying solely on e2e coverage |
| LineComponent | `mousePressed` routing — Alt+click mode-switch, lyric-hit selection, note drag start, selection area drag start | e2e | `SelectionTest`, `NoteDragHandlerTest` (unit, for drag handler only) | wrong-level | Alt+click mode switch branch and lyric-hit branch are each unit-testable with a mocked `ScoreView` and mocked collaborators |
| LineComponent | `gracePreviewLineFrame()` — returns `LINE_LEVEL` or a shifted frame depending on whether this LC is the active grace line | unit | — | missing | Unit-test: when `graceModeManager.getGraceLineComponent() != this`, returns `LINE_LEVEL`; when equal and preview non-null, returns frame with correct shift index and amount |
| LineComponent | `readyLayout()` null contract — returns null when `line == null` or layout fails; otherwise wraps into `ReadyLayout` | unit | — | missing | Unit-test: `line == null` → null; valid line with layout → non-null `ReadyLayout` |
| LineRenderer | `drawStaffLines` — computes 5 staff Y positions as `middleLineYSs + i` for i ∈ {-2..2}; selects selection vs. default color based on `isLineSelected` | unit | — | missing | Unit-test `drawStaffLines` indirectly via color-selection logic in `LineInvariants`; the coordinate arithmetic (+i offset) is trivially correct but the staff-selected color branch has no unit coverage |
| LineRenderer | `renderWithPreviewShiftIfNeeded` — applies `g2.translate(previewShiftSs, 0)` when `spanStart >= previewShiftFromIndex`; otherwise calls render directly | unit | `ElementFrameTest` tests `ElementFrame` fields; no test for `renderWithPreviewShiftIfNeeded` itself | missing | Unit-test: verify that when `hasPreviewShift=true` and `spanStart >= fromIndex`, the render lambda receives a translated context; otherwise it does not |
| LineRenderer | `getElementColor` — adds grace-cancel red coloring on top of `invariants.getElementColor`; returns Color.RED when `isPendingCancelElement` | unit | `LineInvariantsTest` covers `invariants.getElementColor`; the grace-cancel override layer in `LineRenderer.getElementColor` is untested | missing | Unit-test: element that is a pending-cancel element → Color.RED; element that is playing but not pending-cancel → playing color (not RED) |
| LineRenderer | `renderDragRectangle` — skips when not dragging or rectangle is empty; otherwise draws a `RoundRectangle2D` with arc diameter `SELECTION_RECT_ARC_PX` | none | — | — | Pure paint call; the drag-rectangle data itself is tested via `NoteDragHandlerTest`/selection drag path |
| LineRenderer | `renderElements` — X-override arithmetic for preview-shifted elements (`layoutResult.getElementXSs(element) + lineFrame.previewShiftSs()`) when element index ≥ shift boundary | unit | — | missing | Unit-test the override-X computation: given a frame with preview shift and an element index ≥ boundary, verify `overrideXSs` equals layout X + shift (not NaN) |
| LineRenderer | `renderPreviewElement` — `calculateInsertionXSs` called with current mouse X and index; also covers grace-mode locked-X path and glissando placeholder path | unit | `PreviewElementManagerTerminalRoutingTest`, `PreviewElementManagerGlissandoZoneTest` cover `PreviewElementManager` state; the actual X computation path in `renderPreviewElement` is untested | missing | Unit-test: in standard (non-grace) mode, verify `x` is read from `layoutResult.calculateInsertionXSs(…)`; in grace mode, verify `x = getGraceModeLockedXSs()` |
| LineRenderer | `renderLineBeginning` — null-guards clef and key signature before delegating; no computed geometry | none | — | — | Purely conditional delegation; no geometry to assert |
| LineRenderer | `renderKeyChanges` — early return when `lineIndex + 1 >= song.lineCount()` (last line guard) | unit | — | missing | Unit-test: last-line guard fires and rendering is skipped; non-last line with matching key → no-op; non-last line with different key → delegates to `KeySignatureRenderer` |
| ScoreComponent | `resolveContentX` — returns `contentXPx` when ≥ 0; otherwise computes centered X as `(lineWidthPx - textWidth) / 2` | unit | — | missing | Unit-test: (a) explicit `contentXPx ≥ 0` returns it unchanged; (b) `contentXPx < 0` with a known text-width returns the centering formula result |
| ScoreComponent | `setMargin(int)` / `setMargin(int,int,int,int)` — uniform vs. CSS-style margin assignment | unit | — | missing | Unit-test both overloads: verify all four margin fields are set correctly, including that uniform overload sets all sides equally |
| ScoreComponent | `getMaximumSize()` — returns `getPreferredSize()` (BoxLayout expansion suppression) | none | — | — | Framework-delegation contract; no logic to assert |
| ScoreComponent | `initGraphics` — applies four rendering hints to a Graphics2D | none | — | — | Pure rendering hint setup; no computed values to assert |

**7E notes (quality concerns):**

The most significant gap is in `LineComponent`: the three coordinate/geometry methods (`staffPositionToYPx`, `getMiddleLineYPx`, `calculateMiddleLineYSs`) that form the pixel-coordinate chain from staff-space to screen Y have zero unit coverage. These are precisely the methods implicated in issue #411 (pixel deltas added to Ss fields without `pxToSs`), yet every call path in the e2e tests uses them as a black-box coordinate helper rather than asserting their arithmetic. `getPreferredSize` is similarly untested despite computing px dimensions via `ceil(ssToPx(…))` in both axes.

In `LineRenderer`, the `getElementColor` method adds a grace-cancel (Color.RED) override layer on top of `LineInvariants.getElementColor`, but only `LineInvariants.getElementColor` is covered by `LineInvariantsTest`; the renderer-level override is invisible to tests. The `renderElements` preview-shift X override arithmetic (`getElementXSs + previewShiftSs`) and the `renderWithPreviewShiftIfNeeded` conditional translate are both untested at the unit level — `ElementFrameTest` tests the data carrier but not the rendering dispatch.

The mouse-event routing in `LineComponent` (`mouseClicked`, `mousePressed`) is classified as wrong-level because the routing branches (lyric hit, Alt+click mode switch, note drag initiation) are driven by mocked state machines and do not require real Swing dispatch; they are presently covered only implicitly by e2e tests, making branch-level failures invisible without a full UI run.

`ScoreComponent.resolveContentX` centering arithmetic is entirely untested and is a classic off-by-one risk (`lineWidthPx - textWidth`) with no unit safety net.

### 7F. Score panels & text components

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `TitleComponent` | `getPreferredSize`: with song null or empty title returns `(0, 0)` | unit | none | missing | Cover null-song and empty-title early-returns |
| `TitleComponent` | `getPreferredSize`: wraps title at 75 % of line width, height = `lineHeight * wrappedLineCount + marginBottom` | unit | none | missing | Title long enough to wrap: assert height grows with wrapped line count |
| `TitleComponent` | `render` prepends number + ". " when `song.getNumber()` is non-empty before wrapping | unit | none | missing | Verify number prefix is included in the text handed to `wrapText`; a missing prefix would silently drop the song number |
| `TitleComponent` | `render` centers each wrapped line individually within the max-width block | none | — | — | Pure Swing `drawString` with no assertable geometry outside the render pass; `none` |
| `FootnotesComponent` | `getPreferredSize`: null/empty footnotes returns `(0, 0)`; non-empty returns `marginTop + lineHeight * lineCount` | unit | none | missing | Cover both branches; multi-line footnotes (newline-split count) |
| `FootnotesComponent` | width cap: `actualWidth = min(textWidth, lineWidth * 2/3)` only applies to the render X position, not `getPreferredSize` (which always returns full `lineWidthPx`) — potential centering inconsistency | unit | none | missing | Assert that for very wide footnotes `x` is non-negative and stays within bounds |
| `TranslationComponent` | `getTextWidth`: returns max of official/unofficial header width and translation text width; early-return on null song or empty translation | unit | none | missing | Test with unofficial flag, official flag, and empty translation |
| `TranslationComponent` | `getPreferredSize`: height = `marginTop + headerMetrics.height + fontSize/4 + textMetrics.height * lineCount` | unit | none | missing | Assert height formula for one-line and multi-line translations; `fontSize/4` inter-header gap is a non-obvious computed value |
| `TranslationComponent` | `render` selects `TRANSLATION_HEADER_UNOFFICIAL` vs. `TRANSLATION_HEADER_OFFICIAL` based on `song.isUnofficialTranslation()` | unit | none | missing | Both branches (the wrong header string is invisible in CI without a test) |
| `LyricsComponent` | `getTextWidth`: returns 0 when song null or lyrics empty; otherwise delegates to `GraphicUtils.getTextBlockWidth` | unit | none | missing | Null-song and empty-lyrics guards |
| `LyricsComponent` | `getPreferredSize`: height = `lineHeight * splitByNewline.length + marginTop`; width = `song.getLineWidthPx()` | unit | none | missing | Single-line and multi-line lyrics; assert height scales with line count |
| `LyricsComponent` | `render` splits on `\n` and draws each line advancing by `lineHeight` | none | — | — | Pure paint loop; no geometry assertable outside the Swing render pass |
| `BanglaLyricsComponent` | `getLyrics` delegates to `song.getBanglaLyrics()`; `getLyricsFont` delegates to `getFont()` | none | — | — | Trivial one-liner delegations; no test warranted |
| `BanglaLyricsComponent` | `BANGLA_LYRICS_TOP_MARGIN` applied via `setMarginTop` in constructor | none | — | — | Wiring-only; no test warranted |
| `UnderLyricsComponent` | `getLyrics` delegates to `song.getUnderLyrics()`; `getLyricsFont` delegates to `getFont()` | none | — | — | Trivial one-liner delegations; no test warranted |
| `TextPanel` | `calculateUnionWidth`: returns max across all three child `getTextWidth` calls | unit | none | missing | Mock children or use real instances with a mock song; assert the maximum wins |
| `TextPanel` | `paintComponent`: when `unionWidth > 0`, sets identical `contentX` on all three children; when 0 resets to -1 | unit | none | missing | Both branches; this is the central alignment invariant for all text sections |
| `TextPanel` | `getPreferredSize`: sums heights of all three child preferred sizes; width = max of three | unit | none | missing | Cover null-song early-return and non-empty case |
| `TextPanel` | `getSong` throws when song not initialized | none | — | — | Trivial guard; no test warranted |
| `StaffPanel` | `rebuildLayout`: creates one `LinePanel` per song line, adds spacing strut between all but the last | unit | none | missing | Zero lines, one line (no strut), three lines (two struts): assert `linePanels.size()` and component count |
| `StaffPanel` | `getLinePanel(index)`: returns null for out-of-bounds (both negative and >= size) | unit | none | missing | Boundary conditions at -1, 0, size-1, size |
| `StaffPanel` | `getLinePanelAt(point)`: returns the `LinePanel` whose bounds contain the point, null otherwise | unit | none | missing | Point inside first panel, last panel, gap between panels (should return null) |
| `StaffPanel` | `getPreferredSize`: aggregates line heights + inter-line margins (n-1 margins); returns `(0,0)` when empty | unit | none | missing | Zero lines, one line, multiple lines; assert margin count |
| `StaffPanel` | `getLayoutResults` threads `hasLeadingLyricContinuation` across line boundaries | unit | none | missing | Multi-line scenario where one line's `hasTrailingLyricContinuation()` feeds the next; null layout result resets flag to false |
| `StaffPanel` | `updateSongMetrics` side-effect: calls `rebuildLyricRenderMetrics()` then `setSongLayoutMetrics()` on `ScoreView` | unit | none | missing | Verify the call order (lyric metrics rebuilt before layout runs); mock `ScoreView` and verify |
| `LinePanel` | `getPreferredSize` delegates to `lineComponent.getPreferredSize()` | none | — | — | One-liner delegation; no test warranted |
| `LinePanel` | `setLine` propagates both `line` and `lineIndex` to `lineComponent` | none | — | — | Trivial setter; no test warranted |
| `MainPanel` | `getPreferredSize`: conditional `scoreMarginTop` added only when both title and score heights > 0 | unit | none | missing | Three cases: title empty (no gap), score empty (no gap), both non-empty (gap added) |
| `MainPanel` | `getLinePanelAt(point)`: transforms point to `staffPanel`-local coords, delegates to `StaffPanel.getLinePanelAt`; returns null when point outside `staffPanel` bounds | unit | none | missing | Point inside staffPanel vs. outside (title region, below score) |
| `MainPanel` | `rebuildLayout` delegates to `staffPanel.rebuildLayout()` then revalidates | none | — | — | Pure wiring delegation; no test warranted |
| `ScorePanel` | `getPreferredSize`: width = max(contentWidth, parentWidth) — prevents horizontal shrink below parent | unit | none | missing | Content wider than parent (content width wins), content narrower (parent width wins) |
| `ScorePanel` | `getPreferredScrollableViewportSize` returns `content.getPreferredSize()` (not `getPreferredSize()`) to break viewport-feedback loop | unit | none | missing | Assert returns content size, not panel size (the distinction is the documented bug guard) |
| `ScorePanel` | `getScrollableBlockIncrement`: vertical = `visibleRect.height - 10`; horizontal = `visibleRect.width - 20` | unit | none | missing | Both orientations; the asymmetric constants must not regress |
| `ScorePanel` | `getScrollableUnitIncrement` always returns 30 | none | — | — | Magic number but constant; no branch or computation to test |
| `ScorePanel` | `updateUI`: sets background from `FlatLafProps` before fields initialized — early-exit safety | none | — | — | Framework bootstrap; no test warranted |

**7F notes (quality concerns):**

The entire `songscribe.ui.component.score` package has zero dedicated unit tests. The classes contain substantial non-trivial logic — conditional height and width computations in `getPreferredSize`, a two-branch centering system (`resolveContentX` / `calculateUnionWidth` / `paintComponent` of `TextPanel`), a line-boundary lyric-continuation threading algorithm in `StaffPanel.getLayoutResults`, and a viewport-feedback-loop guard in `ScorePanel.getPreferredScrollableViewportSize` — none of which are exercised by any test. The most fragile behaviors are: (1) `TextPanel.calculateUnionWidth` + `paintComponent` reset/set of `contentX`, whose failure produces silently wrong centering; (2) `StaffPanel.getLayoutResults` threading `hasLeadingLyricContinuation`, where off-by-one or reset-on-null mistakes propagate across the entire song rendering; (3) `MainPanel.getPreferredSize` conditional gap insertion, whose bug would cause layout drift only in specific title/score combinations. The helper method `StringUtils.wrapText` (consumed by `TitleComponent`) has its own non-trivial word-rebalancing pass and is also completely untested — a gap that predates this package but is surfaced here as a direct dependency. All warranted tests are classifiable as unit (no Swing pipeline required; `Song` and collaborators can be mocked or constructed lightweight).

### 7G. Toolbars (toolbar/ + MainToolbarPanel)

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `Toolbar` | Constructor wires `JToolBar` with floatable=false, rollover=true, zero margin — pure Swing config, no branching logic | none | — | adequate | No test warranted |
| `Toolbar` | `BUTTON_DIMENSION` constant (36×36) available to subclasses | none | — | adequate | Pure data constant, framework behavior |
| `MainToolbarPanel` | Constructor assembles tool sections (West) + strut (Center) + playback (East) — pure layout wiring, no branching | none | — | adequate | No test warranted |
| `MainToolbarPanel` | `updateUI()` sets background from `UIManager` key `"ToolBar.background"` — theme-aware repaint | none | — | adequate | Framework delegation, no app logic to assert |
| `MainToolbarPanel` | `createToolbarPanel(int borders)` computes left/right border widths from two bitmask flags (`LEFT_BORDER=1`, `RIGHT_BORDER=2`) — four combinations | unit | — | missing | Unit test: assert that each of the four flag combinations (`NO_BORDER`, `LEFT_BORDER`, `RIGHT_BORDER`, `LEFT_BORDER\|RIGHT_BORDER`) produces the correct inset widths on the returned panel's `MatteBorder` |
| `AccidentalToolbar` | Constructor adds four `ToolbarToggleButton` wrappers for accidental actions — pure button-assembly wiring | none | — | adequate | No test warranted |
| `ArticulationToolbar` | Constructor adds two `ToolbarToggleButton` wrappers for articulation actions — pure wiring | none | — | adequate | No test warranted |
| `BarToolbar` | Constructor iterates `REPEAT_ACTIONS` then `BARLINE_ACTIONS`, adding a `StickyToggleButton` per action — pure wiring over fixed arrays | none | — | adequate | No test warranted |
| `DotRestToolbar` | Constructor adds two `ToolbarToggleButton` wrappers for dot/rest actions — pure wiring | none | — | adequate | No test warranted |
| `DurationToolbar` | Constructor scans `DURATION_ACTION_GROUP` actions and, when the quarter-note action is found, selects it as the default, then calls `perform()` — real initialization logic with a conditional | unit | `ActionsResetOnDocumentLoadTest` covers the post-load reset to quarter note but not the initial construction-time selection path; `LyricEditorActionAuditTest` only audits the `DISABLE_WHEN_EDITING_TEXT` flag | missing | Unit test: construct a `DurationToolbar` with a mocked `MainFrame` and assert that `Actions.DURATION_ACTION_GROUP.getSelected()` is `Actions.QUARTER_NOTE_ACTION` immediately after construction |
| `DurationToolbar` | The `if (defaultButton != null)` guard protects the selection call — logically unreachable because `QUARTER_NOTE_ACTION` is always in the group, making it dead-code | none | — | adequate | Dead-code guard; no test value; could be removed as a separate cleanup |
| `ModifyNoteToolbar` | Constructor adds `ToolbarButton` and `TupletPopupButton` wrappers — pure wiring | none | — | adequate | No test warranted |
| `PlaybackToolbar` | Constructor adds two `ToolbarButton` and two `ToolbarToggleButton` wrappers for playback actions — pure wiring | none | — | adequate | No test warranted |

**7G notes (quality concerns):**

The subclasses that are pure button-assembly wiring (`AccidentalToolbar`, `ArticulationToolbar`, `BarToolbar`, `DotRestToolbar`, `ModifyNoteToolbar`, `PlaybackToolbar`) contain no branching logic and are correctly left untested. The two genuine logic gaps are `DurationToolbar`'s construction-time quarter-note selection (distinct from the post-load reset already covered by `ActionsResetOnDocumentLoadTest` — that test fires `DocumentDidLoadNotification`, not the constructor path) and `MainToolbarPanel.createToolbarPanel`'s bitmask-to-border-width conversion (four reachable flag combinations, no test). Everything else in the toolbar package is pure button-assembly wiring. **Scope note:** the original sub-audit also surfaced `DurationActionGroup.barWasSelected()` and several `ActionGroup` base-class branches (`setSelected(action,false)`, `selectNext()` wrap-around, `select()` idempotency, `getPreviousSelected()`); those classes live in `songscribe.ui.action` and were already audited in **§5A** (Session 5), so their rows are not repeated here to avoid double-counting.

### 7H. Input & text widgets (LyricEditor, text fields, caret, focus)

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `LyricEditor` | `filterInsertion`: insertion within cap is allowed | unit | `LyricEditorBehaviorMatrixTest.w1_printableCharWithinCapInserts` | adequate | — |
| `LyricEditor` | `filterInsertion`: insertion that would exceed MAX_LENGTH_CHARS is rejected with beep | unit | `LyricEditorBehaviorMatrixTest.w2_printableCharExceedingCapBeepsAndRejects`; `LyricEditorTest.testThirtyThirdCharacterBeepsAndIsNotInserted` | adequate (minor redundancy) | — |
| `LyricEditor` | `filterInsertion`: replacement that stays within cap is allowed (net length check: currentLength − replacedLength + text.length) | unit | — | missing | Add unit test for replace-within-cap and replace-over-cap (the replacedLength subtraction branch is exercised by no test) |
| `LyricEditor` | `MyPlainDocument.insertString`: newline in pasted string is silently stripped before super call | unit | `LyricEditorBehaviorMatrixTest.w3_pasteWithNewlineSilentlyDropped` | adequate | — |
| `LyricEditor` | `commit()`: new text on empty element emits one LYRIC mutation | unit | `LyricEditorTest.testCommitEmitsSingleElementModificationForNewText` | adequate | — |
| `LyricEditor` | `commit()`: empty text on element with existing lyric removes lyric | unit | `LyricEditorTest.testCommitEmptyTextRemovesExistingLyric` | adequate | — |
| `LyricEditor` | `commit()`: empty text on element with no lyric emits zero mutations | unit | `LyricEditorTest.testCommitEmptyTextOnEmptyElementEmitsNoMutations` | adequate | — |
| `LyricEditor` | `commit()`: same text as existing lyric emits zero mutations | unit | `LyricEditorTest.testCommitSameTextEmitsNoMutations` | adequate | — |
| `LyricEditor` | `commitInner`: no-op when all fields (text, extend, wantsContinues, wantsCompound) are unchanged | unit | `LyricEditorTest.testCommitSameTextEmitsNoMutations`; `LyricEditorBehaviorMatrixTest.k2_unchangedTextPreservesShapeAndAdvances` | adequate | — |
| `LyricEditor` | `navigationCommitSpec`: text unchanged → reuse stored syllabic/compound/extend; text changed → default to WORD_FINAL/NONE | unit | `LyricEditorTest.testAdvanceWithUnchangedTextPreservesExistingBoundaryAndExtend`; `LyricEditorBehaviorMatrixTest.k1_changedTextCommitsWordFinalAndAdvances` | adequate | — |
| `LyricEditor` | `navigationCommitSpec`: carrier extend (CONTINUE/STOP) is re-committed as WORD_FINAL with the same extend | unit | `LyricEditorTest.testEnterOnUnchangedTextPreservesShape`; `LyricEditorBehaviorMatrixTest.k4_unchangedTextOnBeginStartPreservesShape` | adequate | — |
| `LyricEditor` | Tab key: commits and advances without inserting a tab character | unit | `LyricEditorTest.testTabKeyCommitsAndAdvancesWithoutInsertingTabCharacter`; `LyricEditorBehaviorMatrixTest.TabKey` | adequate (minor redundancy) | — |
| `LyricEditor` | Shift+Tab key: commits and retreats without inserting a tab character | unit | `LyricEditorBehaviorMatrixTest.ShiftTabKey.k3_changedTextCommitsWordFinalAndRetreats`; `LyricEditorTest.testRetreatWithUnchangedTextPreservesExistingBoundary` | adequate | — |
| `LyricEditor` | Enter key: commits and dismisses without inserting newline | unit | `LyricEditorTest.testEnterKeyCommitsAndDismissesWithoutInsertingNewline`; `LyricEditorBehaviorMatrixTest.EnterKey` | adequate | — |
| `LyricEditor` | Escape key: runs `applyDismissAdjustment`, dismisses, does not commit | unit | `LyricEditorTest.testEscapeKeyCancelsWithoutMutationOrAdvance`; `LyricEditorBehaviorMatrixTest.EscapeKey` | adequate | — |
| `LyricEditor` | Space key: full 8-cell matrix (S1–S8) — changed/unchanged text, carrier/plain, with/without next eligible | unit | `LyricEditorBehaviorMatrixTest.Space` (s1–s8); `LyricEditorTest.testSpaceKeyCommitsAndAdvancesWithoutInsertingSpaceCharacter` | adequate | — |
| `LyricEditor` | Hyphen key: openedAsExtender=true, text empty → beep | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h1_openedAsExtenderEmptyTextBeeps`; `LyricEditorTest.testHyphenOnExtenderCarrierBeeps` | adequate (minor redundancy) | — |
| `LyricEditor` | Hyphen key: openedAsExtender=true, text non-empty, no next eligible → beep | unit | — | missing | The branch `openedAsExtender && !getText().isEmpty() && nextIndex < 0` is not covered by any test |
| `LyricEditor` | Hyphen key: openedAsExtender=true, text non-empty, next eligible → `breakChainCommitAndAdvance` as WORD_CONTINUING_HYPHEN | unit | `LyricEditorTest.testHyphenOnCarrierWithTextBreaksPredecessorChain` (partial: asserts chain break but not that syllabic is BEGIN / that it advances to next) | inadequate | The assertion is `hasSizeGreaterThanOrEqualTo(2)` (weak size bound) and does not verify that the new editor opens on the next element; add a dedicated matrix case `h_openedAsExtenderNonEmptyNextEligible` |
| `LyricEditor` | Hyphen key: text non-empty, next eligible → commits as BEGIN syllabic and advances (full H2 path, non-carrier) | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h2_nonEmptyTextNextEligibleCommitsAndAdvances`; `LyricEditorTest.testHyphenOnNonEmptyTextCommitsAndAdvances` | adequate | — |
| `LyricEditor` | Hyphen key: text non-empty, no next eligible → beep | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h3_nonEmptyTextNoNextEligibleBeeps`; `LyricEditorTest.testHyphenNoNextEligibleBeeps` | adequate | — |
| `LyricEditor` | Hyphen key: text empty, existing lyric non-null → beep | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h4_emptyTextNonNullLyricBeeps`; `LyricEditorTest.testHyphenOnEmptyEditorWithExistingLyricBeeps` | adequate | — |
| `LyricEditor` | Hyphen key: text empty, lyric null, no predecessor → beep | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h5_emptyTextNullLyricNoPredecessorBeeps`; `LyricEditorTest.testHyphenOnEmptyEditorWithNoPredecessorBeeps` | adequate (minor redundancy) | — |
| `LyricEditor` | Hyphen key: text empty, lyric null, predecessor SINGLE/END → beep | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h6_emptyTextNullLyricEndSinglePredecessorBeeps`; `LyricEditorTest.testHyphenOnEmptyEditorWithWordFinalPredecessorBeeps` | adequate | — |
| `LyricEditor` | Hyphen key: text empty, lyric null, predecessor BEGIN/MIDDLE, next eligible → advance without mutating current | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h7`; `LyricEditorTest.testHyphenOnEmptyEditorWithBeginPredecessorAdvancesWithoutMutation` | adequate | — |
| `LyricEditor` | Hyphen key: text empty, lyric null, predecessor BEGIN/MIDDLE, no next eligible → beep | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h8`; `LyricEditorTest.testHyphenOnEmptyBeginPredecessorNoNextEligibleBeeps` | adequate | — |
| `LyricEditor` | Equals key: full 6-cell matrix (E1–E6) | unit | `LyricEditorBehaviorMatrixTest.Equals` (e1–e6); `LyricEditorTest` (partial) | adequate | — |
| `LyricEditor` | Underscore key: full 11-cell matrix (U1–U11) | unit | `LyricEditorBehaviorMatrixTest.Underscore` (u1–u11); `LyricEditorTest` (partial overlap) | adequate | — |
| `LyricEditor` | `applyDismissAdjustment`: suppressDismissAdjustment=true → clears flag, returns immediately with no mutation | unit | `LyricEditorTest.testSuppressedDismissAdjustmentEmitsNoMutations`; `LyricEditorBehaviorMatrixTest.EscapeKey.k6_suppressDismissAdjustmentNoMutationsAtAll` | adequate | — |
| `LyricEditor` | `applyDismissAdjustment`: openedAsExtender=true, text empty → no-op (chain already well-formed) | unit | `LyricEditorTest.testOpenOnContinueCarrierEscWithoutTypingEmitsNoMutations` | adequate | — |
| `LyricEditor` | `applyDismissAdjustment`: openedAsExtender=true, text non-empty → calls `breakChainAtCurrentElement` | unit | `LyricEditorTest.testTypingIntoMidChainCarrierFlipsPredecessorAndClearsForwardCarriers`; `LyricEditorBehaviorMatrixTest.Space.s4_unchangedCarrierWithTextBreaksChainAndOpensNext` | adequate | — |
| `LyricEditor` | `applyDismissAdjustment`: non-carrier, no dangling chain → no mutation | unit | `LyricEditorTest.testDismissAdjustmentNoOpWhenPredecessorHasNoCarrier` | adequate | — |
| `LyricEditor` | `applyDismissAdjustment`: non-carrier, predecessor dangling BEGIN → demoted to SINGLE | unit | `LyricEditorTest.testDismissDemotesDanglingBeginToSingle` | adequate | — |
| `LyricEditor` | `breakChainAtCurrentElement`: predecessor CONTINUE → flipped to STOP; predecessor START (chain root) → flipped to NONE | unit | `LyricEditorTest.testHyphenOnCarrierWithTextBreaksPredecessorChain` (START→no-flip path); `LyricEditorTest.testTypingIntoMidChainCarrierFlipsPredecessorAndClearsForwardCarriers` (CONTINUE→STOP path) | adequate | — |
| `LyricEditor` | `clearForwardCarriers`: halts at text-bearing element (extend=NONE or START), clears CONTINUE/STOP carriers | unit | Exercised by `testTypingIntoMidChainCarrierFlipsPredecessorAndClearsForwardCarriers` (e3 cleared); `LyricEditorBehaviorMatrixTest.Space.s4` (forwardCarrier cleared) | adequate | — |
| `LyricEditor` | `extendChainBackward`: predecessor STOP carrier → flipped to CONTINUE | unit | `LyricEditorBehaviorMatrixTest.Underscore.u10_emptyStopCarrierPredecessorNextEligibleFlipsAndAdvances` | adequate | — |
| `LyricEditor` | `extendChainBackward`: predecessor CONTINUE carrier → left unchanged | unit | `LyricEditorBehaviorMatrixTest.Underscore.u9_emptyContinueCarrierPredecessorNextEligibleAdvances` | adequate | — |
| `LyricEditor` | `extendChainBackward`: suppresses dismiss adjustment so built chain is not torn down | unit | `LyricEditorTest.testUnderscoreEmptyNoNextEligibleBuildsChainAndDismisses` (verifies no further mutation) | adequate | — |
| `LyricEditor` | `isLyricTargetEligible`: grace note → eligible; host of paired grace note → not eligible; normal note → eligible | unit | `LyricEditorEligibilityTest` (4 cases) | adequate | — |
| `LyricEditor` | `findNextEligibleIndex` / `findPreviousEligibleIndex`: skip host of paired grace note | unit | `LyricEditorEligibilityTest` | adequate | — |
| `LyricEditor` | `advance()`: rest without lyric skipped; rest with lyric is eligible | unit | `LyricEditorTest.testAdvanceSkipsRestsAndLandsOnNextNote`; `testAdvanceTreatsRestWithExistingLyricAsEligible` | adequate | — |
| `LyricEditor` | `advance()`: at end of line → dismiss without opening new editor | unit | `LyricEditorTest.testAdvanceAtEndOfLineDismissesWithoutOpeningNewEditor` | adequate | — |
| `LyricEditor` | `advance()`: populates next editor with existing text and selects all | unit | `LyricEditorTest.testAdvanceIntoPopulatedElementPrefillsTextAndSelectsAll` | adequate | — |
| `LyricEditor` | Constructor: opens on element with existing lyric → prefills text, selectAll gives caret at end | unit | `LyricEditorTest.testAdvanceIntoPopulatedElementPrefillsTextAndSelectsAll` (via advance path) | adequate | — |
| `LyricEditor` | `openedAsExtender`: set true only when opening lyric has CONTINUE or STOP extend | unit | `LyricEditorBehaviorMatrixTest.Hyphen.h1` (CONTINUE → extender); `LyricEditorBehaviorMatrixTest.e5` (CONTINUE → extender); `LyricEditorTest.testOpenOnContinueCarrierEscWithoutTypingEmitsNoMutations` | adequate | — |
| `LyricEditor` | `focusLost`: focused=false → no-op | unit | `LyricEditorBehaviorMatrixTest.FocusLoss.f1_focusedFalseIsNoOp`; `LyricEditorTest.testFocusLostWithoutFocusIsNoOp` | adequate (minor redundancy) | — |
| `LyricEditor` | `focusLost`: focused=true → commits with `navigationCommitSpec`, applies dismiss adjustment, dismisses | unit | `LyricEditorBehaviorMatrixTest.FocusLoss.f2_focusedTrueCommitsAndDismisses` | adequate | — |
| `LyricEditor` | Outside-click listener: `MOUSE_PRESSED` on non-child component → commits and dismisses | unit | `LyricEditorBehaviorMatrixTest.FocusLoss.f3_outsideMousePressedParentedAndFocusedCommitsAndDismisses` | adequate | — |
| `LyricEditor` | Outside-click listener: `MOUSE_PRESSED` on editor itself → no-op (source==this guard) | unit | — | missing | No test covers the self-click guard (`source == this` branch in `installOutsideClickListener`) |
| `LyricEditor` | `commitAndDismiss` re-entrant guard: focused=false or parent=null → no-op | unit | `LyricEditorBehaviorMatrixTest.FocusLoss.f1_focusedFalseIsNoOp` covers focused=false; parent=null path untested | inadequate | Add a case where focused=true but parent=null (editor already removed) |
| `LyricEditor` | `recomputeBounds`: lineComponent=null → early return without NPE | unit | Implicitly exercised (score mock returns null for `getLineComponent`) | adequate | — |
| `LyricEditor` | `LeadingSlackFieldView.keepAllocationAtContentOrigin`: when `adjusted.x < input.x`, x is clamped to input.x | unit | — | missing | The null-guard and x-clamping inside `keepAllocationAtContentOrigin` are pure logic with no test |
| `LyricEditor` | `LeadingSlackFieldView.adjustAllocation`: during paint pass, allocation is shifted by `LEADING_PAINT_SLACK_PX` and clamped | unit | — | missing | Pure geometry branch untested; could be exercised without a full Swing pipeline |
| `NumericTextField` | Integer filter: digits allowed, non-digit rejected with beep | unit | — | missing | `InputUtils.CustomDocumentFilter` (used by integer `NumericTextField`) has no unit tests; the pattern `\d+` means multi-character non-digit input is rejected |
| `NumericTextField` | Decimal filter: valid decimal prospective string allowed; would-be invalid (two dots) rejected with beep | unit | — | missing | `InputUtils.DecimalDocumentFilter.isProspectiveTextValid` performs whole-document validation; the prospective-string computation and two-dot rejection are untested logic |
| `NumericTextField` | `addDecimalFilter` variant: `allowDecimal=true` routes to `DecimalDocumentFilter` for spinner; `allowDecimal=false` routes to `CustomDocumentFilter` | unit | — | missing | No test distinguishes the two filter types; a regression in routing would go undetected |
| `NonEmptyGuard` | `validate()`: non-blank text → returns true, no dialog shown | unit | — | missing | |
| `NonEmptyGuard` | `validate()`: blank text, no defaultValueKey → `showWarningAndRefocus`, returns false | unit | — | missing | |
| `NonEmptyGuard` | `validate()`: blank text, defaultValueKey set, user chooses "use default" → fills field, returns true | unit | — | missing | Both modes (with and without default) carry real logic and need tests; `showDefaultValueDialog` has a non-trivial index/option-result mapping |
| `NonEmptyGuard` | `validate()`: blank text, defaultValueKey set, user dismisses/chooses "continue editing" → refocuses, returns false | unit | — | missing | |
| `NonEmptyGuard` | `install()`: temporary focus-lost event → guard skipped | unit | — | missing | `e.isTemporary()` guard |
| `NonEmptyGuard` | `install()`: focus-lost to exempt component → guard skipped | unit | — | missing | `exemptComponents.contains(e.getOppositeComponent())` guard |
| `TextFocusDelegate` | `focusGained`: sets `ignoreTabKey=true`, posts `TextEditingDidChangeNotification(true)` | unit | — | missing | |
| `TextFocusDelegate` | `focusLost`: posts `TextEditingDidChangeNotification(false)` | unit | — | missing | |
| `TextFocusDelegate` | `processKeyEvent` for JTextField host: returns false (no tab handling), clears ignoreTabKey | unit | — | missing | |
| `TextFocusDelegate` | `processKeyEvent` for JTextArea host, Tab on first key after focus: consumed but focus NOT transferred (ignoreTabKey=true) | unit | — | missing | The `ignoreTabKey` guard on the first Tab after focus-gained is a documented subtle behavior with no test |
| `TextFocusDelegate` | `processKeyEvent` for JTextArea host, Tab (not first): consumed, `transferFocus()` called | unit | — | missing | |
| `TextFocusDelegate` | `processKeyEvent` for JTextArea host, Shift+Tab: consumed, `transferFocusBackward()` called | unit | — | missing | |
| `TextFocusDelegate` | `processKeyEvent`: non-Tab key → returns false | unit | — | missing | |
| `SelectionHidingCaret` | `paint`: when dot != mark (active selection) → caret NOT painted | none | — | — | Pure Swing rendering override; no geometry to compute, behavior is only observable via a real paint context; classify as none |
| `SelectionHidingCaret` | `damage`: when selection active → repaint suppressed | none | — | — | Same as above; Swing integration behavior, not logic |
| `SelectionHidingCaret` | `isSelectionActive`: getComponent()=null → returns false; dot==mark → returns false; dot!=mark → returns true | unit | — | missing | The three branches of `isSelectionActive` are pure predicate logic and can be tested without a paint context by setting up a real `DefaultCaret` on a `JTextField` |
| `MyJTextField` | Constructor installs `SelectionHidingCaret` and calls `createFocusDelegate` | none | — | — | Trivial wiring; no logic to test |
| `MyJTextField` | `processKeyEvent`: if delegate handled it → skips `super.processKeyEvent` | unit | — | missing | The delegation short-circuit logic is untested; a regression where it stops delegating would silently affect all MyJTextField users |
| `MyJTextArea` | `processKeyEvent`: if delegate handled it → skips `super.processKeyEvent`; if not → calls super | unit | — | missing | Same as above for JTextArea variant |
| `DurationListCellRenderer` | `noteGlyphFor`: each Duration value maps to the correct SMuFL glyph (SEMI_BREVE→WHOLE, MINIM/DOTTED→HALF_UP, CROTCHET/DOTTED→QUARTER_UP, QUAVER/DOTTED→8TH_UP) | unit | — | missing | This is a data-transform switch; a wrong mapping would silently display an incorrect glyph |
| `DurationListCellRenderer` | `getListCellRendererComponent`: cell reuse — first call creates NoteLabel, subsequent calls reuse and reconfigure | none | — | — | Cell-renderer identity/reuse is a Swing optimization detail; no application logic at risk |
| `DurationListCellRenderer` | `NoteLabel.configure`: selection background/foreground applied; index==-1 uses list background (non-selected row in combo header) | none | — | — | Pure visual wiring with no computed data to assert; classify as none |
| `DurationListCellRenderer` | `NoteLabel.paintComponent`: dotted durations append AUGMENTATION_DOT glyph with correct offset (QUAVER_DOTTED uses EIGHTH_DOT_OFFSET, others use DOT_OFFSET) | unit | — | missing | The offset-selection branch is logic (not rendering). The glyph-selection and offset values are testable by inspecting `duration.getNote().getDotCount()` and the offset constant choice without actually painting; however, the output is a `Graphics2D` drawString call — observable only via rendering. Classify as none given the output is purely visual. |

**7H notes (quality concerns):**

LyricEditor is the most thoroughly tested class in this package. The three existing test classes (LyricEditorTest, LyricEditorBehaviorMatrixTest, LyricEditorEligibilityTest) provide dense, assertion-rich coverage of commit semantics, all five navigation keys, the full boundary-character matrices (Space, Hyphen, Equals, Underscore), focus-loss paths, and the dismiss-adjustment state machine. Assertions are specific (exact syllabic, compound, extend values rather than presence checks), and the support infrastructure is clean.

Two gaps stand out in LyricEditor itself. First, the `handleHyphen` branch `openedAsExtender=true + non-empty text + no next eligible` (should beep and stay open) has no test at all; the companion branch with next-eligible (LyricEditorTest line 851) exists but uses a weak `hasSizeGreaterThanOrEqualTo(2)` assertion and does not verify that a new editor opens on the correct element. Second, the `commitAndDismiss` re-entrant guard for the `parent=null` path and the self-click guard inside `installOutsideClickListener` are untested. The `LeadingSlackFieldView` geometry helpers (`keepAllocationAtContentOrigin`, `adjustAllocation`) carry testable branching logic but have no tests.

The supporting classes are entirely untested: `TextFocusDelegate` has non-trivial `ignoreTabKey` logic that silently suppresses the first Tab after focus-gained; `NonEmptyGuard` has a two-mode validation strategy with option-dialog index arithmetic; `InputUtils.DecimalDocumentFilter` performs whole-document prospective validation with no tests; and `DurationListCellRenderer.noteGlyphFor` is a pure mapping switch with no tests. `SelectionHidingCaret.isSelectionActive` is a simple predicate that can be tested without a paint context. `MyJTextField` and `MyJTextArea` each have an untested `processKeyEvent` delegation short-circuit. These classes are all at missing status and represent concrete, low-cost test targets.

### 7I. Buttons, borders, frames & navigation helpers

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `BaseLabel` | Constructor: index == -1 uses list background (not selection background), otherwise uses selection/normal background | unit | none | missing | Add `BaseLabel` unit tests: index==-1 path, isSelected==true path, isSelected==false path |
| `BaseLabel` | `paintComponent` — pure rendering (fills background rect, sets foreground) | none | — | — | — |
| `BorderPanel` | `getMyBorder()` returns `MyBorder(borderSpinner.getValue())` when in simple mode | unit | none | missing | Unit-test `getMyBorder` in simple and expert modes (requires Swing components, but logic is pure construction) |
| `BorderPanel` | `getMyBorder()` returns per-edge `MyBorder(top,bottom,left,right)` when in expert mode | unit | none | missing | (same test class as above) |
| `BorderPanel` | `setExpertBorder(true)` copies uniform spinner value into all four edge spinners | unit | none | missing | Assert that toggling to expert mode populates edge spinners from the uniform value |
| `BorderPanel` | `setExpertBorder(false/true)` toggles panel visibility and button label | none | — | — | Pure wiring/rendering, no computed value to assert |
| `MyBorder` | Constructor `(size)` sets all four edges to `size` | unit | none | missing | Parameterized unit tests for all four constructors and `withOverrides` |
| `MyBorder` | Constructor `(horizontal, vertical)` sets left/right = horizontal, top/bottom = vertical | unit | none | missing | (same class) |
| `MyBorder` | Constructor `(top, bottom, left, right)` sets each edge independently | unit | none | missing | (same class) |
| `MyBorder` | `withOverrides(defaultSize, top, left, bottom, right)`: override applied when value > -1, skipped when == -1 | unit | none | missing | Test that -1 leaves edge at defaultSize, non-negative value overrides |
| `MyBorder` | `getWidth()` returns `left + right`; `getHeight()` returns `top + bottom` | unit | none | missing | (same class) |
| `ThemeAwareMatteBorder` | `getBorderInsets()` returns correct insets matching constructor arguments | unit | none | missing | Construct with known values, assert insets are correct |
| `ThemeAwareMatteBorder` | `paintBorder` falls back to `DEFAULT_COLOR` when UIManager key not found | none | — | — | Pure rendering; color selection logic is trivial one-liner, no computable state to assert without Graphics mock |
| `ThemeAwareMatteBorder` | `isBorderOpaque()` always returns true | none | — | — | Trivial constant return, framework behavior |
| `ModeCycleButton` | `modeDidChange`: button NOT updated when `isAdjustmentMode()` is true | unit | none | missing | Mock `ModeDidChangeNotification`; verify `updateButton` is skipped in adjustment mode |
| `ModeCycleButton` | `modeDidChange`: button IS updated when `isAdjustmentMode()` is false | unit | none | missing | (same test class) |
| `ModeCycleButton` | `playbackStateDidChange`: button disabled when playing, enabled otherwise | unit | none | missing | Mock `PlaybackController.isPlaying()`; assert `isEnabled()` |
| `ModeCycleButton` | `graceModeStateDidChange`: button disabled when grace mode active | unit | none | missing | Mock `GraceModeManager.isActive()`; assert `isEnabled()` |
| `StickyToggleButton` | `actionPerformed`: when button is NOT selected after click, reselects it (sticky behavior) | unit | none | missing | Call `actionPerformed` with button in unselected state; verify `isSelected()` is re-set to true and action is NOT performed |
| `StickyToggleButton` | `actionPerformed`: when button IS selected after click, marks action selected and fires `actionPerformed` | unit | none | missing | (same test class) |
| `PopupButton` | `setCurrentAction(null)` is a no-op (no NPE, currentAction set to null, no configureButtonFromAction call) | unit | none | missing | Pass null; assert method returns without throwing |
| `PopupButton` | `setCurrentAction(non-null Selectable)` calls `setSelected(true)` on the action and deselects button | unit | none | missing | Mock a `UIAction.Selectable`; verify `setSelected(true)` is called and button becomes deselected |
| `PopupButton` | `actionPerformed`: `popupWasCanceledByButton` true → clears flag, deselects button, popup not shown again | unit | none | missing | Set flag via `popupMenuCanceled`, fire `actionPerformed`; verify flag reset and `isSelected()==false` |
| `PopupButton` | `actionPerformed`: popup already visible → hides popup | none | — | — | Requires real Swing popup visibility; e2e cost exceeds value for this sub-branch |
| `PopupButton` | `popupMenuWillBecomeInvisible`: deselects button | unit | none | missing | Call listener method directly; assert `isSelected()==false` |
| `PopupButton` | `popupMenuCanceled`: sets `popupWasCanceledByButton` correctly based on component under mouse | none | — | — | Requires real Swing mouse position; cannot be meaningfully unit-tested |
| `StaffAnnotationPopupButton` | `musicSelectionDidChange`: button enabled iff at least one `STAFF_ANNOTATION_ACTIONS` action is enabled | unit | none | missing | Mock actions: all disabled → button disabled; one enabled → button enabled |
| `TupletPopupButton` | `musicSelectionDidChange`: button disabled when no tuplet action is enabled | unit | none | missing | Same pattern as `StaffAnnotationPopupButton` |
| `TupletPopupButton` | `configureButtonFromAction` overrides tooltip to fixed tuplet string regardless of action | unit | none | missing | Call `setCurrentAction` with a mock action; assert tooltip is the tuplet fixed string |
| `TickSlider` | Change listener: `tickDidChange` fired only when new value is in `stopSet` AND differs from `lastCommittedValue` | unit | none | missing | Construct concrete subclass; programmatically fire `setValue()` to a stop value, then same value again; verify callback count |
| `TickSlider` | `setSnappedValue`: selects the nearest stop when given an exact hit | unit | none | missing | Assert that `getValue()` equals the nearest stop; assert no spurious `tickDidChange` fires |
| `TickSlider` | `setSnappedValue`: selects the nearest stop when given an off-stop value | unit | none | missing | (same class — off-stop input) |
| `TickSlider` | `setSnappedValue`: updates `lastCommittedValue` to suppress a spurious `tickDidChange` on the subsequent `setValue` call | unit | none | missing | Verify no callback fires after `setSnappedValue` even if the value changes |
| `ComponentNames` | `line(index)` concatenates `LINE_PREFIX` + index correctly | unit | none | missing | Assert `line(0).equals("line-0")`, `line(3).equals("line-3")` — guards against future constant changes |
| `ComponentHierarchyNavigator` | `getLineComponent(index)`: returns null when `mainPanel` is null | unit | none | missing | Mock provider returning null; assert null result |
| `ComponentHierarchyNavigator` | `getLineComponent(index)`: returns matching `LineComponent` when found | unit | none | missing | Mock panel hierarchy with known line index; assert correct component returned |
| `ComponentHierarchyNavigator` | `getActualLineMiddleYPx`: returns 0 when `mainPanel` is null | unit | none | missing | (same test class — null panel path) |
| `ComponentHierarchyNavigator` | `getActualLineMiddleYPx`: sums Y offsets from mainPanel + staffPanel + linePanel + lineComponent + middleLineY | unit | none | missing | Mock all contributors with known Y values; assert sum |
| `ComponentHierarchyNavigator` | `findLineIndexAtPoint`: uses formula when `mainPanel` is null | unit | none | missing | Mock null panel + mock song with `topPaddingSs`; verify fallback formula |
| `ComponentHierarchyNavigator` | `findLineIndexAtPoint`: finds correct line panel containing the Y coordinate | unit | none | missing | Mock panels with known bounds; assert correct index |
| `ComponentHierarchyNavigator` | `findLineIndexAtPoint`: returns -1 when Y is outside all panels | unit | none | missing | (same class) |
| `ComponentHierarchyNavigator` | `updateLayoutFromComponents`: single panel fallback uses height + margin | unit | none | missing | Mock one panel; assert `rowHeightPx` uses the single-line formula |
| `ComponentHierarchyNavigator` | `updateLayoutFromComponents`: with >= 2 panels, rowHeight = midY[1] - midY[0] | unit | none | missing | Mock two panels with known midpoints; assert difference |
| `ActivationGate` | `activate()` makes glass pane visible; `deactivate()` hides it and stops timer | unit | none | missing | Call `install` with a real (hidden) `JFrame`; call `activate()`/`deactivate()` and assert glass pane visibility + timer state |
| `ActivationGate` | `appRaisedToForeground()` restarts the cmd+Tab timer | unit | none | missing | (same test class — assert timer restarts) |
| `ToolbarButton` | `propertyChange`: updates button from action when `FONT_ICON_KEY` or `FONT_KEY` changes | none | — | — | Pure Swing property dispatch wiring; no computable value beyond delegation |
| `ToolbarToggleButton` | `configurePropertiesFromAction`: delegates to `UIUtils` for `UIAction`, Swing default otherwise | none | — | — | Pure delegation wiring |
| `SplashWindow` | `loadSplashImage` throws `RuntimeError.exit` when image resource not found | none | — | — | Tests that interact with resource loading or `RuntimeError.exit` are impractical in unit context |
| `SplashWindow` | `closeSplash` is threadsafe: calls `invokeAndWait` when off EDT | none | — | — | Swing threading; not practically unit-testable without real EDT |
| `StartFrame` | `startFrame` — pure Swing frame construction and wiring | none | — | — | No logic beyond Swing setup |
| `TipFrame` | `showTip` reads tips file sequentially, wraps index to 0 when buffer is empty (end of file) | unit | none | missing | Supply a fixture tips file; verify wrap-around and index advancement |
| `TipFrame` | `previousButton` handler decrements `index` by 2 before calling `showTip` (and guards index > 1) | unit | none | missing | (same test class; exercise boundary at index==1 and index==2) |
| `TipFrame` | `closeWindow` persists `showTip` checkbox state to `Prefs` | unit | none | missing | Mock `Prefs`; assert correct key/value written |

**7I notes (quality concerns):**

The package has zero dedicated unit tests for any of the classes in scope — every class is untested. The highest-priority gaps are the pure-logic classes that carry genuine branching or computed state: `MyBorder` (constructor and `withOverrides` branching is completely unguarded), `TickSlider` (the stop-filtering and `setSnappedValue` snap-and-suppress logic are the whole point of the class and have no coverage), `ComponentHierarchyNavigator` (null-panel fallbacks, coordinate sum, bounds search — all missing), and `StickyToggleButton`/`PopupButton` (interaction-state machines with non-trivial branching). `ActivationGate` has stateful glass-pane and timer logic that is unit-testable with a hidden `JFrame` and deserves tests. `TipFrame.showTip` and the wrap-around logic are file-I/O-coupled but could be extracted or tested with a fixture file. `ComponentNames.line()` is a one-liner but its constant is referenced by e2e tests by name, making a change invisible at compile time — a single unit test is warranted to prevent silent string drift.

### 7 summary

**387 behavior rows** across 62 production classes: by required level **319 unit / 15 e2e / 53 none**; of the 334 testable rows, **231 missing · 88 adequate · 8 wrong-level · 7 inadequate**. The package is ~69% dark — a few dense, genuinely-adequate clusters embedded in a large untested mass.

**Defining shape: the riskiest user-facing guarantees in the app are dark, while the tested clusters are narrow.** Confirmed highest-risk gaps, in order:

1. **Data-loss guard untested (7D).** `MainFrame.showSaveDialog()` and the entire `save()`/`saveCurrentFile()`/`saveAsNewFile()` chain — the sole guard against silently discarding the user's work, plus `IOException` handling, the `isModified` clear, and the `RecentDocumentsManager` side-effect — have **zero direct tests** at any level. `ShutdownTest` (e2e) only confirms shutdown wiring and forces the CLOSED_OPTION answer, so the "Don't Save" and "Save→propagate result" branches are entirely unexercised; those branches are `wrong-level` (unit-testable with `OptionDialogs`/`SaveAction` mocked).
2. **Paste is a confirmed silent no-op (7A).** `ScoreViewController.handlePaste()` is a body-only TODO, so every `PasteboardOpCommand(PASTE)` is swallowed — the root cause of the Session-5/6 `PasteAction` no-op. This is a production defect, not just a test gap.
3. **px↔staff-space coordinate chain dark (7E).** `LineComponent.staffPositionToYPx` / `getMiddleLineYPx` / `calculateMiddleLineYSs` — the complete layout-result→screen-Y path, exactly the issue-#411 territory — have no unit tests; e2e uses them only as opaque coordinate helpers.
4. **Hit-test & selection geometry dark (7C).** `ElementHitTest` (symmetric-expansion rect math) and `LineSelectionHandler` (5-branch `hitTest` cascade + staff-radius compare) have no unit tests; `SelectionTest.testDragSelect` uses the recurring weak `isGreaterThanOrEqualTo(3)` assertion.
5. **Score-panel layout invariants dark (7F).** `TextPanel.calculateUnionWidth`/`paintComponent` centering, `StaffPanel.getLayoutResults` cross-line lyric-continuation threading, `MainPanel`'s conditional `scoreMarginTop` gap, and `ScorePanel.getPreferredScrollableViewportSize` (documented viewport-feedback-loop guard) — all untested computed geometry.
6. **Widget pure-logic dark (7H/7I).** `TextFocusDelegate`'s `ignoreTabKey` first-Tab guard; `NonEmptyGuard`'s two validation modes + option-dialog index arithmetic; `InputUtils` document filters (also surfaced via `NumericTextField`); `MyBorder` constructors + `withOverrides` -1 sentinel; `TickSlider` snap-and-suppress; `ComponentHierarchyNavigator` (7 behaviors incl. null-panel fallbacks + multi-level Y-sum); `DurationListCellRenderer.noteGlyphFor` glyph-mapping switch.

**Genuinely-adequate clusters (the bright spots):** `LyricEditor` (7H) is the standout — three test classes deliver dense, exact-value coverage of commit semantics, the full navigation-key matrix, and all five boundary-character state machines; gaps are narrow. `NoteDragHandler` and the `PreviewElementManager*` family (7C), `ScoreView.setFonts` (7B), and `ScoreViewController`'s command-handlers + `deleteNote` (7A) are also adequate.

**Cross-cutting `wrong-level` (8):** mouse-event routing in `LineComponent` (`mouseClicked`/`mousePressed` branches driven by mockable state) and the `showSaveDialog` answer branches — covered only via e2e but unit-testable with collaborators mocked. Reinforces the Session-5/6 themes (thin-dispatcher dispatch untested; real logic stranded in e2e happy-paths).

**`inadequate` (7):** `ScoreView.installDocumentFonts` (fixture-only, contract never asserted); `ScoreViewController` `DeleteLyric` (raw `java.lang.reflect` instead of package-private; one of five branches) and `handleRemoveDynamics` (`isNotEmpty()` only); `LyricEditor` hyphen-break (weak `>=2`, target element unasserted) and `commitAndDismiss` parent=null guard; `SelectionTest.testDragSelect` (weak `>=3`); the `MainFrame` window-close-cancel row (only CLOSED_OPTION exercised).

**Dead code:** no dead *classes* found this session (contrast Sessions 3–5). One dead *branch*: `DurationToolbar`'s `if (defaultButton != null)` guard is logically unreachable (`QUARTER_NOTE_ACTION` is always in the group) — candidate cleanup, not unreachable-class removal.

**Production observations filed as a tracked GitHub issue (#412; do not fix during audit):** (a) `ScoreViewController.handlePaste()` TODO-only stub → paste is completely non-functional; (b) `DurationToolbar` dead-code guard; (c) `LineComponent` coordinate chain is adjacent to the already-filed #411 px↔Ss class of bug (read-only assessment; severity needs a repro during remediation).

## 8. `message` (audited 2026-05-22)

Audited via five production-first sub-audits run in two waves: **8A** core message bus; **8B** mutation infrastructure & field-enum validation; **8C** structural mutation records; **8D** `command` messages; **8E** `notification` messages. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. Scope: 82 production classes (5 core + 34 `mutation` + 22 `command` + 21 `notification`) + 4 `package-info`.

### 8A. core message bus — `Message`, `MessageCenter`, `MessageLogger`, `SelectableMessage`, `SongData`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `Message` | `toString()` returns `getClass().getSimpleName()` | unit | — | missing | write unit test: assert `new SaveCommand().toString()` equals `"SaveCommand"` (and a subclass with a nested name) |
| `Message` | Priority constants have expected integer values (HIGH=27, MEDIUM=13, LOW=0) | none | — | none | trivial constants — no test needed |
| `MessageCenter` | `post()` synchronously dispatches to all `@Handler` subscribers before returning | unit | — | missing | write unit test: subscribe a recording listener, post a message, assert handler was called exactly once before `post()` returns |
| `MessageCenter` | `post()` dispatches in handler priority order (higher `@Handler(priority)` first) | unit | — | missing | write unit test: two subscribers with different priorities on the same message type, assert invocation order matches priority |
| `MessageCenter` | `subscribe()` registers a listener that receives subsequent posts | unit | — | missing | covered implicitly by dispatch test above; explicitly: subscribe after post — assert handler NOT called for messages posted before subscription |
| `MessageCenter` | `handlePublicationError` — when handler throws, wraps as `RuntimeError.exit` and rethrows | unit | — | missing | write unit test: subscribe a handler that throws; call `post()`; assert a `RuntimeException` (or `Error`) propagates out (bus may eat it — this is the real risk) |
| `MessageCenter` | `handlePublicationError` — null-safe formatting of listener/handler/message/cause in error detail | unit | — | missing | write unit test: construct `PublicationError` with nulls, invoke `handlePublicationError` via reflection, assert error message contains `"<null>"` placeholders |
| `MessageCenter` | No EDT enforcement — `post()` called off-EDT does not throw or warn | none | — | none | this is a production observation, not a behavior to test; see notes |
| `MessageCenter` | Weak-reference semantics — a subscriber with no strong reference is eventually GC'd and stops receiving | none | — | none | framework behavior (MBassador); cannot meaningfully assert GC timing |
| `MessageLogger` | `init()` creates the singleton and subscribes it to `MessageCenter` | unit | — | missing | write unit test: call `init()`, assert `instance != null` and that posting a `Message` causes a TRACE log line (use `LogbackCapture` or equivalent test appender) |
| `MessageLogger` | `onMessage` logs every `Message` at TRACE level with the message's `toString()` | unit | — | missing | covered by above; if logger is already tested, strengthen: assert the logged string equals the message's `toString()` |
| `SelectableMessage` | `isSelected()` returns the value passed at construction (via implementors) | unit | — | missing | write unit test: `new ToggleLoopPlaybackCommand(true).isSelected()` is `true`; `new ToggleLoopPlaybackCommand(false).isSelected()` is `false` (same for `TogglePlayWithRepeatsCommand`) |
| `SongData` | Record accessor fields roundtrip (all fields stored and returned) | none | — | none | pure Java record with no logic — accessor correctness guaranteed by compiler |
| `SongData` | `tempo` field is nullable — record permits null | none | — | none | no branching logic in the record itself; tested end-to-end via `SongIOTest` |

**Notes.** The most significant gap is that `MessageCenter` has zero unit tests of its own: every test in the suite mocks it statically and uses it as a conduit, so its dispatch, ordering, and error-propagation behaviors are entirely untested. The production observation worth filing: `MessageCenter.post()` is documented as "EDT-only" in `messages.md`, but there is no runtime assertion (`SwingUtilities.isEventDispatchThread()` check) in the production code — an off-thread post will silently succeed, making threading bugs invisible. `MessageLogger.instance` is declared `public static @Nullable` and non-final with a `@SuppressWarnings("StaticNonFinalField")` suppression; the field is only ever written once by `init()`, but nothing prevents a second call from silently replacing the subscribed instance and leaking the original subscriber (MBassador holds it weakly, so it will eventually be collected, but during the window between the second `init()` call and GC the old instance is still subscribed and will receive duplicate dispatches). `SongData` is a pure record holder with no derivation logic; no tests are warranted beyond what `SongIOTest` already exercises through the full load path.

### 8B. mutation infrastructure & field-enum validation — `Mutation`, `LineScopedMutation`, `FieldTypeValidator`, field enums, validated-value records

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `Mutation` (sealed interface) | `permits` list is the exhaustive inventory of all subtypes | unit | — | missing | Add a test that compiles against each permitted subtype (or reflectively verifies every concrete subtype is `instanceof Mutation`) to ensure `permits` stays in sync |
| `LineScopedMutation` | `getLine()` contract (default method shape — every implementor must return its `line` component) | unit | `MutationRecordsTest.testElementMutationsAreLineScoped` / `testSongScopedMutationsAreNotLineScoped` | adequate | — |
| `LineScopedMutation` | `FontChange` is song-scoped (must NOT implement `LineScopedMutation`) | unit | — (omitted from `testSongScopedMutationsAreNotLineScoped`) | missing | Add `FontChange` to the existing `isNotInstanceOf(LineScopedMutation.class)` assertion block |
| `FieldTypeValidator` | null values bypass the check (both old and new) → accepted silently | unit | `testLayoutChangeAcceptsNullValues`, `testMetadataChangeAcceptsNullValues` (construct only, don't explicitly assert null path through validator) | inadequate | Add a dedicated test that passes `(null, null)` to `validate` directly — or via a record constructor — and asserts no exception; this makes the null-pass-through contract explicit and can fail if the logic changes |
| `FieldTypeValidator` | type mismatch on `oldValue` → `IllegalArgumentException` with message identifying record, field, parameter, and both type names | unit | — | missing | Add unit test constructing e.g. `new MetadataChange(MetadataField.TITLE, 42, "x")` and asserting `IllegalArgumentException` with message content |
| `FieldTypeValidator` | type mismatch on `newValue` → `IllegalArgumentException` | unit | — | missing | Same as above but flip which value is wrong |
| `FieldTypeValidator` | correct type on both values → no exception | unit | Covered implicitly by every happy-path construction test in `MutationRecordsTest` | adequate | — |
| `KeyField` | `ACCIDENTAL_COUNT.getExpectedType()` returns `Integer.class` | unit | — | missing | Add parameterized test asserting each constant's `getExpectedType()` return value |
| `KeyField` | `KEY_TYPE.getExpectedType()` returns `KeyType.class` | unit | — | missing | (covered by same parameterized test above) |
| `LayoutField` | all four constants return `Double.class` from `getExpectedType()` | unit | — | missing | Add parameterized test for `LayoutField` constants |
| `LineLayoutField` | `TEMPO_CHANGE_Y_POS_PX`, `BEAT_CHANGE_Y_POS_PX`, `FIRST_SECOND_ENDING_Y_POS_PX`, `TRILL_Y_POS_PX` return `Integer.class` | unit | — | missing | Add parameterized test for `LineLayoutField` constants |
| `LineLayoutField` | `LYRICS_Y_POS_SS` returns `Double.class` | unit | — | missing | (covered by same parameterized test) |
| `LineLayoutField` | `ELEMENT_SPACING_RATIO` returns `Float.class` | unit | — | missing | (covered by same parameterized test) |
| `MetadataField` | each constant returns the correct boxed type from `getExpectedType()` (12 constants, varied types) | unit | — | missing | Add parameterized test for all `MetadataField` constants |
| `LyricsField` | pure enum — no `getExpectedType()`, no logic beyond identity | none | — | — | No test warranted |
| `ElementField` | `DURATION_AFFECTING` constant contains exactly `{DOT_COUNT}` | unit | — | missing | Add a test asserting `ElementField.DURATION_AFFECTING.equals(EnumSet.of(DOT_COUNT))`; this guards against accidental additions that would silently change tuplet-removal policy |
| `ElementField` | other constants are pure labels (no logic) | none | — | — | No test warranted |
| `LineKeyChange` | valid construction: accessors return provided values and `getLine()` delegates to `line` component | unit | `testLineKeyChangeExposesFields` | adequate | — |
| `LineKeyChange` | validation fires on type mismatch (e.g. passing `String` for `ACCIDENTAL_COUNT`) | unit | — | missing | Add `assertThrows(IllegalArgumentException.class, () -> new LineKeyChange(line, KeyField.ACCIDENTAL_COUNT, "bad", 1))` |
| `LineKeyChange` | null/null accepted (unset key signature) | unit | — | missing | Add construction with `(null, null)` and assert no exception |
| `LineKeyChange` | implements both `Mutation` and `LineScopedMutation` | unit | `testElementMutationsAreLineScoped` covers element mutations; key-change line-scoped membership is only incidentally covered via `getLine()` accessor check | adequate | — |
| `LineLayoutChange` | valid construction: accessors return values, `getLine()` delegates | unit | `testLineLayoutChangeExposesFields` | adequate | — |
| `LineLayoutChange` | validation fires on type mismatch (e.g. passing `Integer` for `LYRICS_Y_POS_SS` which expects `Double`) | unit | — | missing | Add `assertThrows(IllegalArgumentException.class, () -> new LineLayoutChange(line, LineLayoutField.LYRICS_Y_POS_SS, 1, 2))` |
| `LineLayoutChange` | null/null accepted | unit | — | missing | Add construction with `(null, null)` and assert no exception |
| `MetadataChange` | valid construction: accessors return values | unit | `testMetadataChangeExposesFields` | adequate | — |
| `MetadataChange` | null old value accepted (string field going from unset) | unit | `testMetadataChangeAcceptsNullValues` | adequate | — |
| `MetadataChange` | validation fires on type mismatch (e.g. passing `Integer` for `TITLE` which expects `String`) | unit | — | missing | Add `assertThrows(IllegalArgumentException.class, () -> new MetadataChange(MetadataField.TITLE, 42, "x"))` |
| `LayoutChange` | valid construction: accessors return values | unit | `testLayoutChangeExposesFields` | adequate | — |
| `LayoutChange` | null old value accepted | unit | `testLayoutChangeAcceptsNullValues` | adequate | — |
| `LayoutChange` | validation fires on type mismatch (e.g. passing `Integer` for `LINE_WIDTH_SS` which expects `Double`) | unit | — | missing | Add `assertThrows(IllegalArgumentException.class, () -> new LayoutChange(LayoutField.LINE_WIDTH_SS, 1, 2))` |
| `FontChange` | pure data holder — no validation, no derived methods, accessors only | none | `testFontChangeExposesFields` (accessor check) | adequate | Accessor test is fine; no extra tests needed since no logic present |
| `LyricsChange` | pure typed-value record — no validation, accessors only | none | `testLyricsChangeExposesFields` | adequate | — |
| `ElementModification` | valid construction: all four components accessible via record accessors | unit | `testElementModificationExposesFields` | adequate | — |
| `ElementModification` | `getLine()` returns `line` component (satisfies `LineScopedMutation`) | unit | `testElementModificationExposesFields` (explicitly asserts `getLine()`) | adequate | — |
| `ElementModification` | `fields` EnumSet is the caller-supplied set (no defensive copy, mutable aliasing risk) | unit | `testElementModificationExposesFields` asserts referential equality | adequate | — |

**Notes.** The most critical gap is that `FieldTypeValidator`'s core contract — throwing `IllegalArgumentException` on a type mismatch — is entirely untested; every validated record (`LineKeyChange`, `LineLayoutChange`, `MetadataChange`, `LayoutChange`) is missing a test for the mismatch path. The field-enum `getExpectedType()` methods carry real logic (they map each constant to a boxed type used at runtime for validation) yet no test asserts a single return value, meaning a copy-paste error on a new constant (e.g. using `int.class` instead of `Integer.class`) would silently break all validation for that field without any test failure. A latent production risk exists in `FieldTypeValidator` itself: `Class.isInstance` never matches primitive types, so if any field enum constant were ever changed to return `int.class` or `double.class`, the validator would silently accept any value rather than throwing — this is documented in the Javadoc ("boxed reference types") but there is no test that guards the contract. `FontChange` is also absent from the `testSongScopedMutationsAreNotLineScoped` assertion block, leaving an unverified assumption that it does not implement `LineScopedMutation`.

### 8C. mutation records — structural add/remove/insert/delete holders

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| BeamingAddition, BeamingRemoval, CrescendoAddition, CrescendoRemoval, DiminuendoAddition, DiminuendoRemoval, TieAddition, TieRemoval, TupletAddition, TupletRemoval | pure holder: stores `(line, spanObj)` + `getLine()` delegating to `line` field; no logic | none | `MutationRecordsTest.SpanMutations.testSpanMutationIsLineScoped` (parameterized) | inadequate | Test confirms `instanceof LineScopedMutation` and `getLine()` identity but never asserts the span payload accessor (`.beam()`, `.tie()`, `.tuplet()`, `.crescendo()`, `.diminuendo()`); extend the parameterized test to extract and assert the span field from each record |
| RangeElementAddition | pure holder: stores `(line, element)` + `getLine()` | none | `MutationRecordsTest.RangeElementMutations.testRangeElementAdditionExposesFields` | adequate | — |
| RangeElementRemoval | pure holder: stores `(line, element)` + `getLine()` | none | `MutationRecordsTest.RangeElementMutations.testRangeElementRemovalExposesFields` | adequate | — |
| ElementDeletion | pure holder: stores `(line, index, deletedElement)` + `getLine()` | none | `MutationRecordsTest.ElementMutations.testElementDeletionExposesFields` | adequate | — |
| ElementInsertion | pure holder: stores `(line, index, element)` + `getLine()` | none | `MutationRecordsTest.ElementMutations.testElementInsertionExposesFields` | adequate | — |
| ElementRangeDeletion | pure holder: stores `(line, from, to, deletedElements: List<StaffElement>)` + `getLine()`; no defensive copy in the record (caller in `Line.removeRange` uses `List.copyOf` before passing) | none | `MutationRecordsTest.ElementMutations.testElementRangeDeletionExposesFields` | adequate | Record-level unmodifiability of `deletedElements` is not tested, but the contract is enforced by the single caller; adequate as-is |
| ElementReplacement | pure holder: stores `(line, index, oldElement, newElement)` + `getLine()` | none | — in `MutationRecordsTest`; fields accessed in `SongLineMaintenanceTest` (lines 120–122, 283–285) and `ApplyActionToSelectionMutationTest` (line 174) as integration assertions | adequate | Field round-trip is covered adequately by integration tests; a dedicated record-level test in `MutationRecordsTest` would be consistent but is not strictly required |
| LineDeletion | pure holder: stores `(lineIndex, deletedLine)`; song-scoped (does not implement `LineScopedMutation`) | none | `MutationRecordsTest.StructuralMutations.testLineDeletionExposesFields`; `LineScopedInterfaceMembership.testSongScopedMutationsAreNotLineScoped` | adequate | — |
| LineInsertion | pure holder: stores `(lineIndex, line)`; song-scoped | none | `MutationRecordsTest.StructuralMutations.testLineInsertionExposesFields`; `LineScopedInterfaceMembership.testSongScopedMutationsAreNotLineScoped` | adequate | — |

All 18 records in scope are genuinely pure data holders — none carries validation, defensive-copy logic in its own canonical constructor, computed accessors, or custom equals/hashCode beyond Java record defaults. The main coverage gap is the parameterized `SpanMutations` test, which checks `getLine()` identity for all ten span records but never verifies the span payload accessor (e.g., `mutation.beam()`) — a test that cannot distinguish a record that accidentally stores the wrong field. `ElementReplacement` has no entry in `MutationRecordsTest` but its fields are adequately exercised by `SongLineMaintenanceTest` and `ApplyActionToSelectionMutationTest`. Production observation: `ElementRangeDeletion` accepts a mutable `List<StaffElement>` with no enforcement in the record itself; the unmodifiability guarantee currently relies entirely on the single caller in `Line.removeRange` passing `List.copyOf()` — a fragile contract if new callers are added.

### 8D. `command` — `*Command` messages

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `CloseWindowCommand`, `DeselectCommand`, `FirstSecondEndingCommand`, `FlipStemDirectionCommand`, `NewFileCommand`, `PrintCommand`, `RemoveDynamicsCommand`, `SaveAsCommand`, `SaveCommand`, `SelectLineCommand`, `ShowOpenDialogCommand`, `ToggleBeamCommand`, `ToggleTieCommand`, `ToggleTrillCommand`, `UpdatePreviewElementCommand` | payloadless marker — no logic | none | — | adequate | no action |
| `AddDynamicsCommand` | single boolean getter `isCrescendo()` — pure data, no logic | none | `ScoreViewControllerCommandHandlerTest` (dispatch vehicle, no accessor assertion) | adequate | no action |
| `InsertLineCommand` | single int getter `getShift()` — pure data, no logic | none | — | adequate | no action |
| `OpenFileCommand` | single `File` getter — pure data, no defensive copy | none | — | adequate | no action (`File` is not safely immutable but no copy is idiomatic Java convention; note only) |
| `PasteboardOpCommand` | single enum getter `getOperation()` (`COPY/CUT/DELETE/PASTE`) — pure data, no logic | none | `DeleteLyricTest`, `EndingConfirmsTest` (dispatch only, `DELETE` op used; other ops never tested as dispatch) | adequate | no action on command class itself |
| `ToggleLoopPlaybackCommand` | implements `SelectableMessage.isSelected()` — trivial getter on stored boolean | none | — | adequate | no action |
| `TogglePlayWithRepeatsCommand` | implements `SelectableMessage.isSelected()` — trivial getter on stored boolean | none | — | adequate | no action |
| `ToggleTupletCommand` | `getTupletSize()` delegates to `action.getTuplet().getSize()` — computed/derived accessor; `toString()` override; payload is a full `TupletAction` object | unit | `ScoreViewControllerCommandHandlerTest.tupletCommand` constructs via mocked `TupletAction`, but asserts on handler mutations not on `getTupletSize()` directly | missing | add unit test: verify `getTupletSize()` returns `action.getTuplet().getSize()` for each `Tuplet` enum value |

**Notes.** The vast majority of the 22 commands are payloadless markers or trivial single-field carriers; none require tests of their own. The single exception is `ToggleTupletCommand`, which exposes a derived accessor `getTupletSize()` that delegates through two method calls on the injected `TupletAction`; that delegation chain is untested at the unit level (the handler test mocks the action but never asserts on the accessor). `PasteboardOpCommand` carries an enum payload but adds no logic, so it remains `none`. **Production observation:** `CloseWindowCommand.java` is syntactically invalid — the file contains only a package declaration and an unused `import songscribe.message.Message;` with no class body; however, the class has zero usages in production code, so it does not currently break compilation.

### 8E. `notification` — `*DidChangeNotification` messages

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `SongDidChangeNotification` | `getLine()` — returns `null` when mutation list is empty | unit | `SongDidChangeNotificationTest.GetLine.testEmptyMutationsReturnsNull` | adequate | — |
| `SongDidChangeNotification` | `getLine()` — returns `null` when all mutations are song-scoped (no `LineScopedMutation`) | unit | `SongDidChangeNotificationTest.GetLine.testAllSongScopedReturnsNull` | adequate | — |
| `SongDidChangeNotification` | `getLine()` — returns `null` when line-scoped mutations target different lines | unit | `SongDidChangeNotificationTest.GetLine.testMultipleLineScopedDifferentLinesReturnsNull` | adequate | — |
| `SongDidChangeNotification` | `getLine()` — returns the shared `Line` when all line-scoped mutations target the same line (song-scoped mutations ignored) | unit | `SongDidChangeNotificationTest.GetLine.testLineScopedPlusSongScopedReturnsLine`, `testMultipleLineScopedSameLineReturnsLine`, `testSingleLineScopedReturnsLine` | adequate | — |
| `SongDidChangeNotification` | `getLine()` — lazy cache: repeated calls return the same instance without recomputing | unit | `SongDidChangeNotificationTest.GetLine.testRepeatedCallsReturnSameInstance` | adequate | — |
| `SongDidChangeNotification` | `hasMutationOf()` — returns `true` when the list contains ≥1 instance of the given subclass | unit | `SongDidChangeNotificationTest.HasMutationOf.testTrueForPresentSubclass` | adequate | — |
| `SongDidChangeNotification` | `hasMutationOf()` — returns `false` when the given subclass is absent | unit | `SongDidChangeNotificationTest.HasMutationOf.testFalseForAbsentSubclass` | adequate | — |
| `SongDidChangeNotification` | `hasMutationOf()` — returns `false` for an empty mutation list | unit | `SongDidChangeNotificationTest.HasMutationOf.testFalseForEmptyMutationList` | adequate | — |
| `SongDidChangeNotification` | `getMutations()` — returned list is unmodifiable (rejects `add`) | unit | `SongDidChangeNotificationTest.testGetMutationsIsUnmodifiable` | adequate | — |
| `ModeDidChangeNotification` | `isAdjustmentMode()` — derived boolean: `true` iff action command starts with `"adjust-"` | unit | — | missing | `ModeAction` requires a `MainFrame`; test via mock `ModeAction` with a stubbed `getActionCommand()`; add `ModeDidChangeNotificationTest` |
| `DocumentDidLoadNotification` | carries `Song` reference — `getSong()` returns the exact instance passed to constructor | unit | `DocumentDidLoadNotificationTest.testCarriesSong` | adequate | — |
| `MusicSelectionDidChangeNotification` | `hasLyricSelection()` — derived: `true` iff `lyricSelection != null` (captured from `ScoreView` at construction) | unit | — | missing | Pure derivation (`lyricSelection != null → true`); test via a unit test that constructs with a mocked `ScoreView`; add `MusicSelectionDidChangeNotificationTest` |
| All remaining pure payload carriers: `BarWasSelectedNotification`, `ControlDidChangeNotification`, `DialogVisibilityDidChangeNotification`, `DocumentWasSavedNotification`, `DurationWasSelectedNotification`, `ElementTypeWasSelectedNotification`, `GraceModeStateDidChangeNotification`, `KeySignatureDidChangeNotification`, `LayoutDidChangeNotification`, `MenuWillOpenNotification`, `MetadataDidChangeNotification`, `PlaybackStateDidChangeNotification`, `PrefsDidChangeNotification`, `RecentDocumentsDidChangeNotification`, `RestModeDidChangeNotification`, `TempoDidChangeNotification`, `TextEditingDidChangeNotification` | store-and-return of constructor-injected values; no derivation, no defensive copies, no collections | none | — | adequate | — |

**Notes.** `SongDidChangeNotification` is thoroughly covered: all three `getLine()` branches (empty list, all song-scoped, same-line, different-lines) are explicitly exercised, the lazy-cache contract is validated, `hasMutationOf()` has three cases (present, absent, empty), and `getMutations()` unmodifiability is tested — no gaps. `ModeDidChangeNotification.isAdjustmentMode()` is the only logic in a pure notification that is completely untested: the field is derived in the constructor from `action.getActionCommand().startsWith("adjust-")`, which is a real two-branch predicate; since `ModeAction` requires a live `MainFrame`, the test must use a mock `ModeAction` with a stubbed `getActionCommand()`. `MusicSelectionDidChangeNotification.hasLyricSelection()` is a trivial null-check derivation captured at construction time; it is untested and a mock-`ScoreView` unit test is straightforward. Production observation: `MusicSelectionDidChangeNotification` captures live UI state (`ScoreView.getSelectionSize()`, `getController()`, etc.) directly in its constructor rather than accepting pre-extracted values — this tightly couples the message class to the UI and makes it impossible to unit-test without a running `ScoreView`; the `hasLyricSelection()` derivation is the only non-trivial piece affected.

### §8 — summary

**79 behavior rows** across 82 production classes: by required level **53 unit / 0 e2e / 26 none**; of the 53 testable rows, **30 missing · 22 adequate · 1 inadequate · 0 wrong-level** (~57% of testable behavior dark). **Zero e2e in the entire package** — message classes are pure logic/data with no integration risk intrinsic to them, so nothing escalates. The 27 `none` rows are pure data holders; many carry incidental existing tests (8 adequate in 8C, 7 adequate in 8D), plus one `none`-level row whose existing guard is itself weak (the parameterized `SpanMutations` test).

**Defining shape: a small well-covered logic core embedded in a large trivial-holder mass.** Predicted exactly by the pre-audit triage — the 86-file count is dominated by pure carriers that collapse to `none`, while the genuine testable surface is ~53 rows concentrated in three places.

**Bright spot (the model for good coverage): `SongDidChangeNotification` (8E)** is thoroughly tested — all four `getLine()` outcomes (empty list, all-song-scoped, different-lines, shared-line), the lazy-cache contract, `hasMutationOf()` (present/absent/empty), and `getMutations()` unmodifiability. No gaps. This is what the rest of the package should look like.

**Highest-concentration gap: validation is structurally untested (8B).** `FieldTypeValidator`'s core contract — throwing `IllegalArgumentException` on a runtime type mismatch — has **zero tests across all four validated records** (`LineKeyChange`, `LineLayoutChange`, `MetadataChange`, `LayoutChange`); only the happy-path construction is exercised. Compounding it, the field-enum `getExpectedType()` mappings (`KeyField`, `LayoutField`, `LineLayoutField`, `MetadataField` — ~20 constants) have **no assertion of a single return value**, so a copy-paste slip to `int.class` would silently disable validation for that field with no test failure (see production observation 3). `ElementField.DURATION_AFFECTING` set membership (drives tuplet-removal policy) and `FontChange`'s song-scoped membership (omitted from the line-scope test) are the other 8B misses.

**Core bus dark (8A).** `MessageCenter` — synchronous dispatch, `@Handler` priority ordering, and `handlePublicationError` propagation — is entirely untested: every test in the suite uses it as a static conduit, none asserts its own behavior. `Message.toString()`, `MessageLogger.init()`/`onMessage`, and `SelectableMessage.isSelected()` are also missing (all unit, all small).

**Trivial mass, three real holdouts.** The `command` package (8D) and the structural `mutation` records (8C) are pure holders → `none`. Only three carriers have derivation worth a unit test: `ToggleTupletCommand.getTupletSize()` (delegates through `action.getTuplet().getSize()`), `ModeDidChangeNotification.isAdjustmentMode()` (`"adjust-"`-prefix predicate), and `MusicSelectionDidChangeNotification.hasLyricSelection()` (null-check derivation).

**`inadequate` (1 testable + 1 none-level guard):** `FieldTypeValidator` null-bypass (8B — records constructed with nulls but the null-pass-through is never explicitly asserted); and at `none` level, the parameterized `SpanMutations` test (8C) asserts `getLine()` identity for all ten span records but never the span payload accessor (`.beam()`/`.tie()`/etc.), so it cannot catch a wrong-field-storage bug.

**Dead/orphan code:** no dead *classes*. One orphan file — `CloseWindowCommand.java` declares no type (license + `package` + unused import only) and has zero usages (verified); see production observation 5.

### message — production observations (out of test-audit scope)

Filed as a tracked GitHub issue (#413; do not fix during audit). Recorded read-only:

1. **`MessageCenter.post()` has no EDT assertion** despite the documented "EDT-only" contract — an off-thread post silently succeeds, hiding threading bugs.
2. **`MessageLogger` double-`init()`** replaces the subscribed instance while the prior one remains weakly subscribed in MBassador, producing duplicate TRACE dispatches until GC reclaims it.
3. **`FieldTypeValidator` primitive-type latent risk** — `Class.isInstance` never matches primitives, so a field enum returning `int.class`/`double.class` would silently accept any value. Not a present bug (all enums use boxed types); nothing guards the regression.
4. **`ElementRangeDeletion` no record-level defensive copy** — accepts a mutable `List<StaffElement>`; the unmodifiability guarantee relies solely on the single `Line.removeRange` caller passing `List.copyOf`. Fragile if a new caller is added.
5. **`CloseWindowCommand.java` declares no type** — license header + `package` + an unused `import songscribe.message.Message` only; the command class does not exist and has zero usages (verified — `CloseWindowAction` does not reference it). Complete or delete.
6. **`MusicSelectionDidChangeNotification` captures live UI state in its constructor** (`ScoreView.getSelectionSize()`/`getController()`/etc.) rather than pre-extracted values, tightly coupling the message to the UI and complicating unit testing of `hasLyricSelection()`.

## 9. `ui/renderer` (audited 2026-05-22)

Audited via three production-first sub-audits run in one wave: **9A** renderer
infrastructure + note-area geometry; **9B** span / connector renderers; **9C**
glyph / element painters. Read-only; e2e assessed from source only; coverage
checked across unit (mirrored + cross-package) and e2e. Scope: 29 production
classes (+ 1 `package-info`). Tallies below are parsed directly from the verdict
column of each table (the sub-audits' own prose self-counts drifted and were
corrected to match).

### 9A — Renderer infrastructure + note-area geometry

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| ElementRenderer | Strategy interface — no logic | none | — | none | — |
| ElementFrame | `hasOverrideElementX()` — NaN vs. finite | unit | `testHasOverrideElementXFalseForNaN`, `testHasOverrideElementXTrueForFiniteValue` | adequate | — |
| ElementFrame | `hasPreviewShift()` — negative vs. non-negative index | unit | `testHasPreviewShiftFalseForNegativeIndex`, `testHasPreviewShiftTrueForNonNegativeIndex` | adequate | — |
| ElementFrame | `LINE_LEVEL` constant values | unit | `testLineLevelHasNoElementOverrideOrShift` | adequate | — |
| ElementFrame | `lineLevelWithPreviewShift()` — copies LINE_LEVEL indices, attaches shift | unit | — | missing | Add unit test: verify currentElementIndex==-1, overrideXSs==NaN, fromIndex/shiftSs match args |
| ElementFrame | `withElement()` — creates per-element frame, inherits preview shift | unit | — | missing | Add unit test: verify element index + override set, previewShift inherited from parent |
| GraphicsState | `save()` + `close()` restore contract (bitmask-gated, per-property) | unit | — | missing | Add unit test with a mocked/real Graphics2D: set properties, enter try-with-resources, modify, confirm restore on close |
| GraphicsState | `Property` enum / `has()` bitmask — no separate logic beyond branching in save/close | none | — | none | — |
| RenderContext | Pure interface — no logic | none | — | none | — |
| RenderingUtils | `getDecorationColor()` — null line → preview color | unit | `testGetDecorationColorNullLineReturnsPreviewColor` | adequate | — |
| RenderingUtils | `getDecorationColor()` — element not in line → preview color | unit | `testGetDecorationColorElementNotInLineReturnsPreviewColor` | adequate | — |
| RenderingUtils | `getDecorationColor()` — element in line → `invariants.getElementColor(index)` | unit | `testGetDecorationColorElementInLineReturnsCtxColor` | adequate | — |
| RenderingUtils | `getDecorationColor()` fast path — frame has valid element index (≥0) bypasses line scan | unit | — | missing | Add unit test: construct frame with valid elementIndex, verify fast-path color returned without consulting line |
| RenderingUtils | `noteStaffPositionToCoordinateSs()` — trivial delegation to `spToSs` + offset | none | — | none | — |
| RenderingUtils | `forEachLedgerLineYSs()` — parity normalization + stepping loop, both above and below staff | unit | — | missing | Add unit tests: positions above/below staff, on-staff (no callback), parity normalization edge cases |
| RenderingUtils | `centeredGlyphX()` — multi-term centering: noteheadCenter + xOffset − bBoxLeft − glyphWidth/2 | unit | — | missing | Add unit test: assert computed X equals expected arithmetic result for known inputs |
| RenderingUtils | `glyphOriginYFromLayoutTop()` — trivial subtraction (layoutTop − bbox.top) | none | — | none | — |
| RenderingUtils | `stemCenterXOffsetSs()` — branches on minim vs. black notehead, upper vs. lower | unit | — | missing | Add unit tests: all 4 combinations (minim-up, minim-down, black-up, black-down) |
| RenderingUtils | `layoutYToComponentYSs()` — trivial addition | none | — | none | — |
| RenderingUtils | `drawLedgerLine`, `drawBravuraGlyph`, `applyDecorationColor` — pure painting | none | — | none | — |
| LineInvariants | `getElementColor()` — not in edit mode → BLACK | unit | `testNotEditModeReturnsBlack` | adequate | — |
| LineInvariants | `getElementColor()` — playing note → playing color | unit | `testPlayingElementReturnsPlayingColor` | adequate | — |
| LineInvariants | `getElementColor()` — grace note playing → playing color | unit | `testGraceNoteCountsAsPlaying` | adequate | — |
| LineInvariants | `getElementColor()` — element in playing tie → playing color | unit | `testElementInPlayingTieReturnsPlayingColor` | adequate | — |
| LineInvariants | `getElementColor()` — selected element → selectionColor | unit | `testSelectedElementReturnsSelectionColor` | adequate | — |
| LineInvariants | `getElementColor()` — hovered (replaced-element) → REPLACED_ELEMENT_COLOR | unit | — | missing | Add unit test: mockStatic PreviewElementManager to return matching location; verify semi-transparent red returned |
| LineInvariants | `getElementColor()` — default (none of the above) → BLACK | unit | `testDefaultReturnsBlack` | adequate | — |
| LineInvariants | `isElementPlaying()` — both primary and grace note | unit | `testIsElementPlayingFalseForUnrelatedIndex`, `testGraceNoteCountsAsPlaying` | adequate | — |
| LineInvariants | `isElementInPlayingTie()` — in tie vs. no playing note | unit | `testElementInPlayingTieReturnsPlayingColor`, `testIsElementInPlayingTieFalseWithoutPlayingNote` | adequate | — |
| LineInvariants | `getLyricColor()` + span-aware `isLyricSpanPlaying()` — melisma/BEGIN-MIDDLE/tied spans | unit | — | missing | Add unit tests covering: anchor playing, tied anchor, melisma extender carrier playing, BEGIN/MIDDLE continuation, span end boundary, no lyric on element |
| LineInvariants | `getLyricConnectorColor()` — 3 branches (sourceIndex<0, no line, delegate to colorFor) | unit | — | missing | Add unit tests for each branch |
| LineInvariants | `Builder.build()` validation — throws `IllegalStateException` when required fields unset | unit | — | missing | Add unit test: assert `assertThatThrownBy` when any of layoutResult/songLayoutMetrics/lyricRenderMetrics is null |
| LineInvariants | Trivial getters (getSong, getFonts, getCurrentLine, getMiddleLineYSs, getLineIndex, etc.) | none | — | none | — |
| NoteArea | Pure data record holder | none | — | none | — |
| NoteAreaBuilder | `getOrBuildArea()` cache hit — same instance returned when note unchanged | unit | `testAreaCacheReturnsSameInstanceWhenNoteUnchanged` | adequate | — |
| NoteAreaBuilder | `getOrBuildArea()` cache invalidation — all 7 attribute-change cases | unit | `testAreaCacheRebuilds*` (7 tests) | adequate | — |
| NoteAreaBuilder | `getOrBuildArea()` cache stable — on-staff position change within same ledger tier | unit | `testAreaCacheRetainsCacheWhenStaffPositionChangesWithinStaff` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — quarter note (only asserts `isEmpty()==false`) | unit | `testBuildNoteAreaQuarterNoteNoExtras` | inadequate | Strengthen: assert bounds height > 0 and bounds width > 0 (or compare with a known baseline geometry) |
| NoteAreaBuilder | `buildNoteArea()` — with accidental extends left | unit | `testBuildNoteAreaWithAccidentalExtendsLeft` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — dots extend right (one dot, two dots) | unit | `testBuildNoteAreaWithDotsIsWider`, `testBuildNoteAreaWithTwoDotsIsWiderThanOne` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — ledger lines above staff extend bounds width | unit | `testBuildNoteAreaWithLedgerLinesAboveStaff` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — ledger lines below staff (only asserts `isEmpty()==false`) | unit | `testBuildNoteAreaWithLedgerLinesBelowStaff` | inadequate | Strengthen: verify bounds width is wider than note on-staff, mirroring the above-staff test |
| NoteAreaBuilder | `buildNoteArea()` — whole note / half note / grace note noteheads | unit | — | missing | Add tests for SEMIBREVE, MINIM, grace noteType variants (different shape constants are selected) |
| NoteAreaBuilder | `buildNoteArea()` — beamed flag suppression (flag absent when beamed=true) | unit | — | missing | Add test: beamed area max-Y should be smaller than non-beamed (flag suppressed) for a quaver stem-up |
| NoteAreaBuilder | `createOffsetArea()` — contains original, expands bounds | unit | `testCreateOffsetAreaContainsOriginal`, `testCreateOffsetAreaExpandsShape` | adequate | — |
| NoteAreaBuilder | `getLedgerLineCount` boundary tests (tested here, belong in StaffElementTest) | unit | `testGetLedgerLineCount*` (3 tests) | redundant | Move to `StaffElementTest`; they test `StaffElement.getLedgerLineCount()`, not `NoteAreaBuilder` |

**Notes.** Rows: 46. Tally: adequate 21, missing 13, inadequate 2, none 9, redundant 1. No dead code found — all public/package-private symbols in scope have active callers in the production tree. Production observations: (1) `GraphicsState.close()` silently skips restoration when a saved value is `null` (e.g., `color`, `font`, `transform`) — this is intentional for rendering hints but means a `save(COLOR)` on a context whose `getColor()` returns `null` will never restore. In practice `Graphics2D` implementations do not return `null` from `getColor()`, but the guard is asymmetric: `CLIP` restores unconditionally while all other properties guard on `!= null`. A future implementor swapping in a custom `Graphics2D` could observe silent no-restore for `COLOR`/`STROKE`/`FONT`/`TRANSFORM`. (2) `NoteAreaBuilder.addAccidentalToArea()` uses `ACCIDENTAL_HEIGHT_SS` (derived from the sharp bbox) as a uniform height for all accidentals, which overestimates the natural bounding area. This is documented as an approximation, but a double-flat is taller than a sharp — so the area may understate the actual footprint for that accidental, potentially letting a glissando endpoint land too close to a double-flat. (3) In `LineInvariants.isLyricSpanPlaying()`, when iterating forward for a STOP/CONTINUE carrier, the loop returns on the first lyric found. If a note has no lyric (`next == null`) it is skipped, but the spanning end index (`spanEnd`) is computed only when a lyric is found. A STOP/CONTINUE carrier at index `i` correctly sets `spanEnd = i`, but a text-bearing lyric at `i` sets `spanEnd = i - 1` even if `i - 1 == anchorIndex`. That means a single-note syllable with no carriers would compute `spanEnd == anchorIndex`, and `playingNoteIndex <= anchorIndex` would have already returned `false` before entering the loop — so the edge case is harmless. However, the early-exit guard `playingNoteIndex <= anchorIndex` discards the case where the same note is both anchor and playing, which is handled higher up by `isElementPlaying(anchorIndex)`. The logic is correct but non-obvious and entirely without test coverage.

### 9B — Span / connector renderers

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| BeamGroupRenderer | `getBeamLevel`: scans a note range and returns the maximum beam level (quaver→0, semiquaver→1, demi-semiquaver→2) | unit | — | missing | Add unit test: build a Line with mixed note types, assert correct level returned |
| BeamGroupRenderer | `isNoteTypeInLevel`: determines whether a note type (or, for grace notes, its surrounding notes) qualifies at a beam recursion level | unit | — | missing | Add unit test: verify ordinary and grace-note dispatch; cover boundary conditions |
| BeamGroupRenderer | `stemTipYSsOffset`: returns stem-tip Y (in ss) from StemLayout if available, else estimates from staff position and standard stem length | unit | — | missing | Add unit test: verify both branches (with real StemLayout record and null fallback); assert exact Y arithmetic |
| BeamGroupRenderer | `getBeamHighlightColor`: evaluates selected/hovered notes against remaining-beamable-count threshold; returns selection color, preview color, or null | unit | — | missing | Add unit tests: ≥2 beamable remaining → null; selection-priority over hover; non-edit-mode → null |
| BeamGroupRenderer | `render`/`renderBeams` (pure paint dispatch) | none | — | none | No test warranted; painting delegates to drawBeam which calls Graphics2D |
| TieRenderer | `determineTieColor`: checks start-note color then end-note color; returns selection/playback color or ELEMENT_COLOR | unit | — | missing | Add unit test with mocked LineInvariants; verify start-takes-priority over end; verify fallback |
| TieRenderer | `renderTie`: reads pre-computed TieLayout and paints cubic Bezier — geometry is upstream in layout | none | — | none | No test warranted; all geometry is from TieLayout |
| TupletRenderer | `renderTupletsFromLine` — `numberOnly` branch decision: `allBeamed && isUpper` controls whether bracket arms are drawn | unit | — | missing | Add unit test (mocked LineInvariants + minimal DecorationLayout): verify numberOnly=true suppresses bracket drawing; numberOnly=false draws arms |
| TupletRenderer | Bracket X coordinate computation: `leftXSs`/`rightXSs` from `anchorXSs + decorLayout.widthSs()` with ARM_EXTENSION_SS and stem adjustments | unit | — | missing | Add unit test: build a minimal fixture, assert leftXSs/rightXSs values for both stem-up and stem-down |
| TupletRenderer | `renderTuplet` / `drawTupletNumber` (pure paint) | none | — | none | No test warranted |
| EndingRenderer | `getEffectiveEndingYSs`: translates DecorationLayout ySs → component ySs via `layoutYToComponentYSs`; throws IllegalStateException if layout absent | unit | — | missing | Add unit test: verify correct Y with layout present; verify exception when layout absent |
| EndingRenderer | `drawEnding` (pure paint with bracket lines and glyph) | none | — | none | No test warranted |
| GlissandoRenderer | `computeFarBoundsT`: bounding-box ray distance along diagonal / axis-aligned directions | unit | GlissandoRendererTest.testComputeFarBoundsTDiagonal, testComputeFarBoundsTRightward | adequate | — |
| GlissandoRenderer | `findNoteAreaEntryPoint`: inward-search for offset-area entry (circle, composite, fallback, zero-direction) | unit | GlissandoRendererTest.testFindEntryPoint_circle, testFindEntryPoint_compositeArea, testFindEntryPoint_fallback, testFindEntryPoint_zeroDirection | adequate | — |
| GlissandoRenderer | `hitTestGlissando`: local-coordinate hit test using cached geometry (diagonal, no-cache, before/after, beside, on-line, second-note) | unit | GlissandoRendererTest.testHitTestGlissando_* (6 tests) | adequate | — |
| GlissandoRenderer | Unison-glissando suppression in `renderGlissando` (`src.note().getPitch() == tgt.note().getPitch()` → early return) | unit | GlissandoRendererTest.testNonUnison*, testUnison* | inadequate | Three "unison" tests assert only on model pitch values (StaffElement.getPitch()), never invoking the renderer; the suppression branch is untested. Add a test that calls renderGlissando (or computeEndpoints indirectly) and verifies no drawing occurs for same-pitch pairs |
| GlissandoRenderer | `determineGlissandoColor`: standalone glissando selection; implied target selection for CONNECTED | unit | — | missing | Add unit tests: verify standalone glissando selection color; verify implied target-note selection; verify non-edit-mode fallback |
| GlissandoRenderer | `getGlissandoX1Ss` / `getGlissandoX2Ss`: public endpoint accessors used by HorizontalAdjustment | unit | — | missing | Covered indirectly by endpoint computation but not by name; add targeted tests confirming fallback to notehead center when endpoints null |
| GlissandoRenderer | `computeEndpoints`: full endpoint calculation including direction normalization, x1Translate clamping, length/crossing check, slide-out fixed length | unit | — | missing | `computeEndpoints` is private and exercised only through rendering; widen to package-private and add direct tests for: translate clamping; crossing rejection; slide-out length; zero-length guard |
| LyricConnectorRenderer | `drawHyphen` (count ≤ 1 → single centered hyphen; hyphen centered, Y at verse baseline) | unit | LyricConnectorRendererTest.testHyphenDrawnCenteredAtMidpoint, testDanglingHyphenDrawnCenteredInGap | adequate | — |
| LyricConnectorRenderer | `drawHyphen` (count > 1 → multiple evenly-spaced hyphens with offset): `count = floor(gap / preferred)`, `offsetSs = (gap - count*cell) / 2` | unit | — | missing | Add unit test: wide connector with gap >> 2×preferredCell; assert correct count of drawGlyphVector calls and X positions |
| LyricConnectorRenderer | `drawExtender` / `drawDanglingExtender`: stroke width, Y at verse baseline | unit | LyricConnectorRendererTest.testExtenderDrawnFromStartToEnd, testDanglingExtenderDrawnFromStartToEnd, testExtenderUsesExtenderStroke, testDistinctVersesRenderAtDistinctY | adequate | — |
| LyricConnectorRenderer | Selection color routing | unit | LyricConnectorRendererTest.testSelectedSourceElementRendersConnectorInSelectionColor | adequate | — |
| LyricConnectorRenderer | No-connectors early exit | unit | LyricConnectorRendererTest.testNoConnectorsIsNoOp | adequate | — |
| LyricTextRenderer | Single verse draw at baseline | unit | LyricTextRendererTest.testDrawsSingleBoxAtVerseBaseline | adequate | — |
| LyricTextRenderer | Multi-verse distinct baselines | unit | LyricTextRendererTest.testDrawsMultipleVersesAtDistinctBaselines | adequate | — |
| LyricTextRenderer | No-boxes no-op | unit | LyricTextRendererTest.testNoBoxesIsNoOp | adequate | — |
| LyricTextRenderer | Suppress actively-edited element | unit | LyricTextRendererTest.testSkipsActivelyEditedElementButRendersOthers | adequate | — |
| LyricTextRenderer | Selected lyric / selected note → selection color | unit | LyricTextRendererTest.testSelectedLyricPaintsInSelectionColor, testSelectedElementPaintsLyricInSelectionColor | adequate | — |
| AnnotationRenderer | Annotation baseline Y computation: `ascentSs` from FontMetrics + `layoutYToComponentYSs` with `decorationLayout.ySs()` | unit | — | missing | Add unit test with mocked LineInvariants + DecorationLayout: verify drawString receives correct x and y |
| AnnotationRenderer | Missing layout → IllegalStateException | unit | — | missing | Add unit test: element with AnnotationAttachment but no DecorationLayout → expect IllegalStateException |
| AnnotationRenderer | No attachment → no-op | none | — | none | Trivial null guard, no logic to assert |

**Notes.** The eight classes break cleanly into two groups. `LyricConnectorRenderer` and `LyricTextRenderer` are well-covered by existing unit tests — all major behaviors are exercised with concrete assertions. `GlissandoRenderer` has strong coverage of its geometry primitives (`computeFarBoundsT`, `findNoteAreaEntryPoint`, `hitTestGlissando`) but three gaps remain: the unison-suppression rendering branch is tested only at the model level (inadequate); `determineGlissandoColor`'s branching is untested; and `computeEndpoints` (the core cross-note geometry including clamping, crossing detection, and slide-out length) is private and has no direct coverage. The remaining five classes (`BeamGroupRenderer`, `TieRenderer`, `TupletRenderer`, `EndingRenderer`, `AnnotationRenderer`) have zero tests: they each contain non-trivial logic that is fully testable in isolation — `getBeamLevel`, `isNoteTypeInLevel`, `stemTipYSsOffset`, `getBeamHighlightColor`, `determineTieColor`, the `numberOnly` branch, bracket X coordinate arithmetic, `getEffectiveEndingYSs`, and annotation baseline Y calculation.

**Row count: 32 rows.** Tally: adequate 12 / missing 14 / inadequate 1 / wrong-level 0 / none 5.

**Dead code:** None. All 8 classes are imported and called from `LineRenderer.java`. Note: `BeamGroupRenderer` declares a `private static final Logger LOG` field (line 65) that is never referenced anywhere in the class body — the field and its `LoggerFactory.getLogger()` call are unused. This is a minor production observation, not a test gap.

**Production observations for the Session 9 GitHub issue:**
1. `BeamGroupRenderer` line 65: `private static final Logger LOG = LoggerFactory.getLogger(BeamGroupRenderer.class)` is declared but never invoked. Candidates for removal.
2. `EndingRenderer.getEffectiveEndingYSs` (line 157–165) throws `IllegalStateException` when no `DecorationLayout` is found for an ending. Unlike most renderers which silently skip null layouts, this hard-fail path is invisible without a test and could surface as an uncaught exception if layout invalidation races rendering.
3. `GlissandoRenderer.computeEndpoints` contains two `//noinspection ConstantValue` suppression comments around redundant null checks on `tgt` (lines 479–480, 521–522). These guard against an impossible state that the compiler cannot prove away — a structural smell worth eliminating by extracting the slide-out and connected branches into separate methods.

### 9C — Glyph / element painters

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `NoteRenderer` | `getNoteHeadGlyph(ElementType)` — map lookup, returns glyph or null | unit | — | missing | Add: all 7 note types return correct glyph; non-note type returns null |
| `NoteRenderer` | `getNoteHeadChar(ElementType)` — derives String from glyph or null | unit | — | missing | Add: null when type has no glyph, non-null for standard types |
| `NoteRenderer` | `computeBaseStemGeometry(ElementType, boolean)` — derives stemLeftX, anchorY, length by type/direction | unit | — | missing | Add: minim vs. black head, up vs. down, grace note uses separate anchor |
| `NoteRenderer.StemGeometry` | `stemTipYSs(boolean)` — tip = anchorY ∓ length | unit | — | missing | Add: up tip = anchorY - length; down tip = anchorY + length |
| `NoteRenderer` | `forEachDotPosition(note, beamed, upper, consumer)` — xAdjust branching by note type, yOffset by staff position parity | unit | — | missing | Add: semibreve/minim offsets, beamable+unbeamed+upper offsets, on-line vs. space yOffset, dotCount loop |
| `NoteRenderer` | `getLedgerLineCenterXSs(note)` — rightEdge / 2 | unit | — | missing | Add: simple arithmetic verified against known notehead width |
| `NoteRenderer` | `getLedgerLineWidthSs(note, extensionSs)` — rightEdge + 2×extension | unit | — | missing | Add: verify additive formula |
| `NoteRenderer` | Accidental bounds (via `NoteGeometry.getAccidentalBoundsSs`) — null for grace/no accidental; sensible extents per type; widens when parenthesized | unit | `NoteRendererTest.*` (6 tests) | inadequate | Tests are correct and can fail, but they test `NoteGeometry` not `NoteRenderer` — name mismatch (class should be `NoteGeometryTest` or tests should be moved); also assertions are directional-only (`isNegative`/`isPositive`/`isGreater`) with no expected values from independent calculation |
| `NoteRenderer` | Pure painting (render, renderNoteHead, renderStem, renderFlags, renderDots, renderLedgerLines, renderAccidental, renderBreathMark) | none | — | none | — |
| `RestRenderer` | `getRestGlyph(ElementType)` — map lookup | unit | — | missing | Add: each rest type maps to correct glyph; non-rest returns null |
| `RestRenderer` | `calculateRestYSs(note, middleLineYSs)` — branching by SEMIBREVE_REST / MINIM_REST / other | unit | — | missing | Add: all three branches with exact expected Y offsets |
| `RestRenderer` | Pure painting (render, renderDots) | none | — | none | — |
| `ClefRenderer` | `render` — `baseline = middleLineYSs + 1.0`, no branching | none | — | none | Trivial single-expression positioning; geometry is a named constant offset |
| `KeySignatureRenderer` | `render` no-op when `hasAccidentals()` is false | unit | `KeySignatureRendererTest.testRenderIsNoOpForCMajor` | adequate | — |
| `KeySignatureRenderer` | `render` draw loop — correct staff positions for flats (BEADGCF order) and sharps (FCGDAEB order), accidentalCount iterations | unit | — | missing | Add: verify FLAT/SHARP_STAFF_POSITIONS arrays encode correct staff positions for 1–7 accidentals |
| `KeySignatureRenderer` | `renderKeyChange` — 4 branches: same type adding, same type removing (naturals for removed), different type (naturals then new key), identical keys (no-op) | unit | — | missing | Add: each branch; verify correct keyType arrays, accidentalCounts, startingOffsets, isNaturals flags |
| `KeySignatureRenderer` | `getGlyphForKeyType` — switch on FLATS/SHARPS/default throws | unit | — | missing | Add: FLATS → FLAT glyph, SHARPS → SHARP glyph, NONE throws |
| `KeySignatureRenderer` | Pure painting (drawString calls in render/renderKeySignatureChange) | none | — | none | — |
| `BarRenderer` | `renderBarLineOrRepeat` — switch on 6 barline/repeat types selects correct drawing primitives | unit | — | missing | Add: verify each case (SINGLE, DOUBLE, FINAL_DOUBLE, REPEAT_LEFT, REPEAT_RIGHT, REPEAT_LEFT_RIGHT) calls the right draw helpers |
| `BarRenderer` | `drawRightRepeat` — returns x after thick bar; accumulates dots-advance + sep + thin + sep + thick | unit | — | missing | Add: verify returned x is correct |
| `BarRenderer` | Pure painting (drawBar, drawRepeatDots, resolveBarXSs) | none | — | none | — |
| `ArticulationRenderer` | `render` — combo detection (hasStaccato && hasAccent → ACCENT_STACCATO glyph); solo staccato → STACCATO; solo accent → ACCENT | unit | — | missing | Add: three combinations; verify correct glyph selected via layout-position path |
| `ArticulationRenderer` | Pure painting (drawBravuraGlyph calls) | none | — | none | — |
| `FermataRenderer` | `render` — guard (no FermataAttachment → no-op); layout lookup; delegates to drawBravuraGlyph | none | — | none | The only logic is a null guard; placement is entirely delegated to `NoteAttachedStacker` and `RenderingUtils` — no computable geometry owned here |
| `TrillRenderer` | `drawWavyLine` — segment count = `max(1, round(length / WIGGLE_SEGMENT_WIDTH_SS))`; scale = length/segWidth/segments | unit | — | missing | Add: zero/negative length no-op; normal length computes correct segment count; rounding edge case |
| `TrillRenderer` | `renderTrillAtPosition` — branches on endNote != null && endNote != anchor | unit | — | missing | Add: single-note trill (NaN endX); multi-note trill (endX = endNote X + noteheadWidth) |
| `TrillRenderer` | Pure painting (renderTrill, drawString) | none | — | none | — |
| `BeatChangeRenderer` | `render` — null guard on attachment; delegates to `drawDurationEquals` + `drawDurationGlyph` | none | — | none | All branching logic lives in `MetronomeRenderer` base methods |
| `MetronomeRenderer` | `requireMetronomeGlyph(ElementType)` — 6-way mapping + throws on unmapped type | unit | — | missing | Add: each note type maps to correct SMuFL glyph; unmapped type throws RuntimeError |
| `MetronomeRenderer` | `drawDurationEquals` — advances xSs by glyph advance + dotAdvance (×2 if dotted) + equals string width | unit | — | missing | Add: dotted and non-dotted duration; verify returned xSs accounts for all advances |
| `MetronomeRenderer` | `drawDurationGlyph` — draws glyph + optional dot | none | — | none | Pure painting delegating to already-tested geometry |
| `TempoChangeRenderer` | `renderTempoChange` — `shouldShowTempo` branch: with tempo shows "visibleTempo + space + description + glyph"; without shows description only | unit | — | missing | Add: verify StringBuilder contents for showTempo=true vs false |
| `TempoChangeRenderer` | Pure painting (drawString) | none | — | none | — |
| `DynamicMarkingRenderer` | `render` — null guard on attachment; `glyph = dynamicType.getGlyph()` (null → return) | none | — | none | Glyph selection is an enum property on `DynamicAttachment.DynamicType`, already verified there; renderer itself has no logic |
| `DynamicMarkingRenderer` | Pure painting | none | — | none | — |
| `DynamicsRenderer` | `renderSingleHairpin` — type branch: crescendo → two lines from left-middle to right-top/bottom; diminuendo → two lines from left-top/bottom to right-middle | unit | — | missing | Add: verify line endpoints differ between crescendo and diminuendo (could test via a recording Graphics2D or by extracting coordinate logic to a pure method) |
| `DynamicsRenderer` | Pure painting (g2.draw calls) | none | — | none | — |

**Notes.**

The audit covers 13 production classes. Across the 37 rows above (23 testable, 14 `none`), the tally is:

- **adequate**: 1  
- **inadequate**: 1 (`NoteRendererTest` — name mismatch: class is `NoteRendererTest` but all tests call `NoteGeometry` methods, not `NoteRenderer`; assertions use directional-only comparisons)  
- **missing**: 21  
- **none**: 14  

**Dead code:** No dead code identified. Every method has either a rendering call path, a utility call from collaborator classes (`computeBaseStemGeometry` is called by `GlissandoRenderer`), or a public API used from `LineComponent`/`FughettaRenderer` equivalents. `FermataRenderer.renderFermata` is a thin forwarding wrapper — its usages should be confirmed if removal is considered.

**Production observations for the Session 9 GitHub issue:**

1. **`NoteRendererTest` is misclassified** — all six tests in `NoteRendererTest` exercise `NoteGeometry.getAccidentalBoundsSs`, not `NoteRenderer` itself. The class has no import for `NoteRenderer`. This file should be renamed `NoteGeometryTest` and moved to `src/test/…/layout/` alongside the other `NoteGeometry` tests (or absorbed into a future `NoteGeometryTest`). The tests themselves are sound.

2. **`KeySignatureRenderer.renderKeyChange` "adding accidentals" branch is ambiguous** — when `nextLine.count > line.count`, the code sets `accidentalCounts[0] = nextLine.getKeyAccidentalCount()` (the full new count) but the comment says "just show the new ones." It is unclear whether this draws the full new key signature starting at the beginning (rendering all accidentals) or only the incremental ones. A unit test of this branch is the only way to confirm the intent is correctly implemented; the absence of one leaves this ambiguous.

3. **`DynamicsRenderer.renderSingleHairpin` lacks a pure-function extraction** — the hairpin line-endpoint logic (two `Line2D.Double` constructions that differ only in which corners go to the apex) is directly inside a method that also sets stroke and color. If the line construction ever regresses, the only way to catch it is to mock `Graphics2D.draw()` and inspect the shapes passed — awkward. Extracting endpoint computation to a package-private method would make it trivially unit-testable.

(37 rows: 1 adequate / 1 inadequate / 21 missing / 14 none)

### §9 summary

**115 behavior rows: 87 testable / 28 `none`; of the 87 testable, 34 adequate ·
48 missing · 4 inadequate · 1 redundant · 0 wrong-level (~60% dark).** Zero
genuine e2e escalations in the entire package — every testable behavior is
`unit`, consistent with the rubric (renderers either paint or compute; the
integration risk lives upstream in `layout`/`ui/component`).

**The rubric's "pure painting → `none`" prediction held but was narrower than
expected.** The 28 `none` rows concentrate in 9C glyph painters (14/37), yet the
audit's defining finding is that substantial *computed* logic hides inside
classes named like painters and is almost entirely untested: glyph-selection
maps, staff-position arithmetic, barline-type switches, and duration-advance
math that the rubric does **not** excuse as paint.

**Darkest zone — 9C glyph painters (only 1 of 23 testable rows adequate).**
Untested computation spans `NoteRenderer` (stem/dot/ledger geometry helpers,
`computeBaseStemGeometry`, `forEachDotPosition`), `KeySignatureRenderer`
(flat/sharp staff-position arrays + the 4-branch `renderKeyChange`),
`BarRenderer` (6-way barline/repeat-type switch + `drawRightRepeat` advance),
`MetronomeRenderer`/`TempoChangeRenderer` (glyph mapping, dotted-duration
advance arithmetic, tempo-string assembly), and `RestRenderer`/
`ArticulationRenderer`/`TrillRenderer`/`DynamicsRenderer` (rest-Y branch,
combo-articulation glyph selection, wavy-line segment count, hairpin endpoints).

**9B span / connector renderers — every cross-element geometry helper dark:**
`BeamGroupRenderer` (`getBeamLevel`, `stemTipYSsOffset`, `getBeamHighlightColor`),
`TupletRenderer` bracket-X arithmetic + `numberOnly` branch, `EndingRenderer`
`getEffectiveEndingYSs`, `TieRenderer.determineTieColor`, `AnnotationRenderer`
baseline-Y. Bright spots: the two lyric renderers (`LyricConnectorRenderer`,
`LyricTextRenderer`) are well-covered with falsifiable assertions, and
`GlissandoRenderer`'s geometry primitives (`computeFarBoundsT`,
`findNoteAreaEntryPoint`, `hitTestGlissando`) are adequate.

**9A infrastructure — strongest existing coverage in the package**
(`LineInvariants.getElementColor` color-resolution matrix, `NoteAreaBuilder`
cache hit/invalidation matrix, `RenderingUtils.getDecorationColor`). The single
riskiest dark path in §9 is `LineInvariants.isLyricSpanPlaying()` — five exit
points, feeding two other untested color methods (`getLyricColor`,
`getLyricConnectorColor`). `GraphicsState.save/close` restore contract is also
untested.

**inadequate (4):** (1) `GlissandoRenderer` unison-suppression tests assert on
model `getPitch()` and never invoke the renderer's early-return branch (9B);
(2,3) two `NoteAreaBuilder.buildNoteArea` tests assert only `isEmpty()==false`
with no geometry (9A); (4) `NoteRendererTest` is a **name mismatch** — all six
tests exercise `NoteGeometry`, not `NoteRenderer`, with directional-only
(`isNegative`/`isPositive`) assertions and no independently-computed expected
values (9C). **redundant (1):** the `NoteAreaBuilder` `getLedgerLineCount` trio
tests `StaffElement.getLedgerLineCount()` (9A).

**Cross-session attribution (for remediation, not new rows here):**
`NoteRendererTest` belongs in `layout` (`NoteGeometry`, Session 3) when rewritten;
the `getLedgerLineCount` trio belongs in `StaffElementTest` (`dom`, Session 1).

**No dead classes** (all 29 actively used by `LineRenderer`). One unused symbol:
`BeamGroupRenderer`'s `LOG` field is declared but never invoked.

### §9 production observations (filed as GitHub issue #414)

1. **`GraphicsState.close()` asymmetric null guard.** `CLIP` is restored
   unconditionally while `COLOR`/`STROKE`/`FONT`/`TRANSFORM`/hints guard on
   `!= null`. Harmless with real `Graphics2D` (those getters never return null)
   but a custom/stub `Graphics2D` could silently skip restoration. Normalize or
   comment.
2. **`NoteAreaBuilder.addAccidentalToArea()` uniform accidental height.** Uses
   the sharp bbox height for all accidentals; a double-flat is taller, so the
   composite note area can understate the visual footprint, potentially letting
   a glissando endpoint land too close. Documented as an approximation but no
   follow-up exists.
3. **`EndingRenderer.getEffectiveEndingYSs()` hard-fails on missing layout.**
   Throws `IllegalStateException` when no `DecorationLayout` is found, diverging
   from every peer span renderer (which silently skip null layouts) — an
   uncaught-exception risk if layout invalidation races rendering.
4. **`GlissandoRenderer.computeEndpoints()` structural smell.** Two
   `//noinspection ConstantValue` suppressions around redundant `tgt` null guards
   (the compiler can't prove non-null inside the `!isSlideOut` branch). Splitting
   into distinct slide-out vs. connected branches would eliminate them.
5. **`KeySignatureRenderer.renderKeyChange` "adding accidentals" comment
   contradicts the code.** When `nextLine.count > line.count` the comment says
   "just show the new ones" but `accidentalCounts[0]` is set to the full new
   count. Intent (full redraw vs. delta) is ambiguous and untested.
6. **`DynamicsRenderer.renderSingleHairpin` endpoint logic is not extractable.**
   The crescendo/diminuendo `Line2D.Double` corner selection lives inside a
   method that also sets stroke/color, so the branch can only be observed by
   mocking `Graphics2D.draw()`. Extract endpoint computation to a package-private
   method.
7. **`BeamGroupRenderer` unused `LOG` field** (declared, never invoked) —
   candidate for removal.

## 10. `ui/dialog` (audited 2026-05-22)

### 10A — Dialog Infrastructure & Lifecycle

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `BaseDialog` | `isAnyBlockingDialogVisible()` false initially, true after open, false after close | unit | `BaseDialogCounterTest.testIsAnyBlockingDialogVisible*` | adequate | — |
| `BaseDialog` | Nested blocking dialogs: counter tracks all levels; stays true until all closed | unit | `BaseDialogCounterTest.testNestedDialogsCounterTracksAllLevels` | adequate | — |
| `BaseDialog` | `INFORMATIONAL` category does not increment/decrement counter | unit | `BaseDialogCounterTest.testInformationalDialog*`, `testMixedDialogs*` | adequate | — |
| `BaseDialog` | `EXCLUSIVE` category is blocking (counter increments/decrements) | unit | none — `TestDialog` uses default `OPERATIONAL`; `EXCLUSIVE` is never exercised | missing | Add test asserting `EXCLUSIVE` dialog increments counter |
| `BaseDialog` | `DialogVisibilityDidChangeNotification(true)` posted on 0→1 transition only | unit | `BaseDialogCounterTest.NotificationTransitionTests.testNotification*` | adequate | — |
| `BaseDialog` | `DialogVisibilityDidChangeNotification(false)` posted on 1→0 transition only | unit | `BaseDialogCounterTest.NotificationTransitionTests.testNotification*` | adequate | — |
| `BaseDialog` | No notification posted for `INFORMATIONAL` open/close | unit | `BaseDialogCounterTest.InformationalNotificationTests.testInformationalDialogDoesNotPostNotification` | adequate | — |
| `BaseDialog` | `getData()` returning false cancels show (dialog disposed, not shown, counter not incremented) | unit | none | missing | Add test: override `getData()` → false; assert `isAnyBlockingDialogVisible()` is false and `JDialog.setVisible(true)` never called |
| `BaseDialog` | Tab iteration in `getData()`: first tab returning false short-circuits (later tabs not called) | unit | none | missing | Add test with two tabs where tab[0] returns false; assert tab[1].getData not called |
| `BaseDialog` | First open uses default position (`UIUtils.positionDialog`) | unit | `BaseDialogPositionTest.testFirstOpenUsesDefaultPosition` | adequate | — |
| `BaseDialog` | Second open after close restores saved location (no `positionDialog`) | unit | `BaseDialogPositionTest.testSecondOpenRestoresSavedLocation` | adequate | — |
| `BaseDialog` | Position not restored if first instance was never closed | unit | `BaseDialogPositionTest.testPositionNotRestoredIfNeverClosed` | adequate | — |
| `BaseDialog` | Distinct dialog classes have independent saved positions | unit | `BaseDialogPositionTest.testDistinctClassesHaveIndependentPositions` | adequate | — |
| `BaseDialog` | Non-resizable close: saves `x`/`y` only (no `width`/`height`) | unit | `BaseDialogPositionTest.GeometryPersistence.testPersistOnCloseNonResizable` | adequate | — |
| `BaseDialog` | Resizable close: saves `x`, `y`, `width`, `height` | unit | `BaseDialogPositionTest.GeometryPersistence.testPersistOnCloseResizable` | adequate | — |
| `BaseDialog` | `loadGeometryFromPrefs`: restores location from prefs on first open | unit | `BaseDialogPositionTest.GeometryPersistence.testRestoreFromPrefs` | adequate | — |
| `BaseDialog` | `loadGeometryFromPrefs`: empty prefs key → falls back to default position | unit | `BaseDialogPositionTest.GeometryPersistence.testMissingKeyFallsBackToDefaultPosition` | adequate | — |
| `BaseDialog` | `loadGeometryFromPrefs`: entry present but not a `Map<?,?>` → falls back (malformed prefs) | unit | none | missing | Add test: prefs entry is a `String`; assert `positionDialog` still called |
| `BaseDialog` | `loadGeometryFromPrefs`: entry is a map but x/y are non-`Number` → falls back | unit | none | missing | Add test: prefs map has `x`="bad"; assert `positionDialog` still called |
| `BaseDialog` | `applyGeometry` resizable floor semantics: restored size clamped to `max(packed, restored)` per dimension | unit | none | missing | Add test: packed=300×200, restored=200×400 → applied width=300, height=400 |
| `BaseDialog` | `applyGeometry` resizable: restores location+size (calls `setBounds`, not `setLocation`) | unit | none | missing | Add test verifying `setBounds` called with floor'd dimensions when dialog is resizable |
| `BaseDialog` | `GeometryResetSubscriber`: `PrefsDidChangeNotification` with key `DIALOG_GEOMETRY` clears `SAVED_GEOMETRY` | unit | none | missing | Add test: save geometry, post notification with `DIALOG_GEOMETRY` key, reopen → `positionDialog` called again |
| `BaseDialog` | `GeometryResetSubscriber`: `PrefsDidChangeNotification` with key `ALL` clears `SAVED_GEOMETRY` | unit | none | missing | Add test: same as above with `ALL` key |
| `BaseDialog` | `createTabbedPane`: first call registers top-level pane + lifecycle listener; second call returns new pane without overwriting | unit | none | missing | Add test: call twice; assert `tabbedPane` field holds first-call instance |
| `BaseDialog` | `tabWillShow`/`tabWillHide` fired on tab switch via `ChangeListener` | unit | none | missing | Add test with two tabs; simulate selection change; assert correct callbacks fired |
| `BaseDialog` | `tabWillShow` fired for initially-selected tab on `setVisible(true)` | unit | none | missing | Add test |
| `BaseDialog` | `tabWillHide` called for all tabs on `setVisible(false)` | unit | none | missing | Add test |
| `BaseDialog` | `getContentPaddingKey`: returns buttons-padding key when `hasButtons()` true, std-padding key when false | unit | none | missing | Add test on concrete subclass pairs |
| `BaseDialog` | `getScoreView()` returns null when scoreView not initialized (nullable contract) | unit | none | missing | Add test: mock `mainFrame.getScoreView()` → null; assert returns null |
| `BaseDialog` | `requireScoreView()` throws when scoreView null (`RuntimeError.exit`) | unit | none | missing | Add test: mock `mainFrame.requireScoreView()` → throw; assert propagates |
| `BaseDialog` | `getSong()` delegates to `requireScoreView().getSong()` | unit | none | missing | Add test |
| `BaseDialog` (inner `Tab`) | `build()` appends fill-glue unless `addExpanding` called first (`hasFillItem`) | none | — | — | Pure layout wiring |
| `BaseDialog` (inner `Tab`) | `Tab.getData()` returns true by default (no branching, override hook only) | none | — | — | Trivial default; only testable behavior is in overrides |
| `BaseDialog` (inner `TitledSection`) | `addSeparator()` axis dispatch (Y→vertical strut, X→horizontal strut) | unit | none | missing | Add test: construct X-axis and Y-axis sections; call addSeparator; verify layout component added |
| `StandardDialog` | OK click: `isValidData()` false → `setData()` not called, dialog stays open | unit | none | missing | Add test: override `isValidData()` → false; click OK; assert `setData` not called and dialog still visible |
| `StandardDialog` | OK click: `isValidData()` true → `setData()` called, then `setVisible(false)` | unit | none | missing | Add test |
| `StandardDialog` | Cancel click: `setVisible(false)` without calling `setData()` | unit | none | missing | Add test |
| `StandardDialog` | `modifyButtonPanel` called exactly once on first `setVisible(true)` (once-only guard via `buttonPanelAttached`) | unit | none | missing | Add test: open twice; assert `modifyButtonPanel` called once (spy subclass) |
| `StandardDialog` | `isValidData()` iterates tabs: first failing tab short-circuits | unit | none | missing | Add test with two tabs where tab[0] returns invalid |
| `StandardDialog` | `setData()` iterates all registered tabs | unit | none | missing | Add test |
| `StandardDialog` | `repaintScore()` null-safe: no-op when `getScoreView()` returns null | unit | none | missing | Add test: mock scoreView null; click OK with valid data; assert no NPE |
| `DialogCategory` | `isBlocking()` true for `EXCLUSIVE` and `OPERATIONAL`, false for `INFORMATIONAL` | unit | Indirectly via counter tests (`INFORMATIONAL` + `OPERATIONAL`); `EXCLUSIVE` never tested directly | inadequate | Add direct `isBlocking()` enum test covering all three constants |
| `DialogGeometry` | Pure data record, no logic | none | — | — | — |
| `PropertiesStateStore` | `put(key, null)` calls `prefs.remove(key)` instead of putting null | unit | none | missing | Add test: call `put("k", null)`; verify `prefs.remove("k")` called (mock `Preferences`) |
| `PropertiesStateStore` | `put(key, value)` with non-null calls `prefs.put(key, value)` | unit | none | missing | Add test |
| `PropertiesStateStore` | `get(key, def)` delegates to `prefs.get(key, def)` | none | — | — | Trivial delegation, no logic |
| `Step` | Pure container: `getInfo()` returns null, `start()`/`end()` are no-ops | none | — | — | No logic in base class |
| `PaperSizeStep` | `getValueInPixels`: converts spinner double value to pixels using current unit | unit | none | missing | Add test: set unit to INCH, set spinner to 8.5; assert pixels match `Unit.INCH.convertToPixels(8.5)` |
| `PaperSizeStep` | Unit switch (INCH→CM): scales all spinner values by `MM_PER_IN` multiplier | unit | none | missing | Add test: set to INCH with value 1.0; switch to CM; assert spinner values ≈ 25.4 |
| `PaperSizeStep` | Unit switch (CM→INCH): scales values by `1/MM_PER_IN` | unit | none | missing | Add test |
| `PaperSizeStep` | `TemplateObject` parsing: splits on `;`, assigns name/width/height/margin/unit/metric | unit | none | missing | Add test: parse a template line; assert all fields |
| `PaperSizeStep` | `TemplateObject` parsing: partial line (fewer than 6 fields) uses defaults | unit | none | missing | Add test |
| `PaperSizeStep` | Template selection populates all six spinners with template values | unit | none | missing | Add test |
| `PaperSizeStep` | `end()` writes all six pixel values + `mirrored` flag to `pageLayoutData` | unit | none | missing | Add test: set up spinners; call `end()`; assert `pageLayoutData` fields |
| `PaperSizeStep` | `setValues()` round-trip: pixel values converted to current unit for display | unit | none | missing | Add test: call `setValues` with known pixel values; assert spinner values match conversion |
| `PaperSizeStep` | `MirroredAction`: labels switch between Left/Inner and Right/Outer | unit | none | missing | Add test: toggle checkbox; assert label text |
| `PaperSizeStep` | `start()` selects first template matching metric pref | unit | none | missing | Add test: set pref METRIC=false; call `start()`; assert selected template is imperial |
| `TempoSection` | `setTempo`/getters round-trip: all four controls reflect passed `Tempo` | unit | none | missing | Add test: call `setTempo(t)`; assert `getTempoType`, `getVisibleTempo`, `getTempoDescription`, `isShowOnlyDescription` |
| `TempoSection` | `getTempoType()` throws `IllegalStateException` when combo selection is null | unit | none | missing | Add test: clear combo selection; assert ISE thrown |
| `TempoSection` | `getTempoDescription()` returns empty string when combo selection is null | unit | none | missing | Add test: clear combo; assert returns `""` |

**Notes:**

The blocking-counter logic in `BaseDialog` is thoroughly tested (13 tests across `BaseDialogCounterTest`), and geometry persistence (save + restore from static map/prefs) is well covered in `BaseDialogPositionTest`. However, the critical `getData()` cancellation path — which prevents `setVisible(true)` from proceeding and is the primary lifecycle gate — has zero test coverage, as does all of `StandardDialog`'s OK/Cancel/validation lifecycle. These are the highest-priority gaps: they guard data integrity on every dialog commit.

The `GeometryResetSubscriber` (`PrefsDidChangeNotification` → `SAVED_GEOMETRY.clear()`) is never tested; it is the mechanism that allows geometry to be reset from Preferences, and the subscriber is wired in a static initializer making it easy to miss.

The `applyGeometry` floor semantics for resizable dialogs (`Math.max(packedSize, restoredSize)`) are untested — the existing `TestResizableDialog` is used only to verify that size keys are written, not that the restore correctly applies the floor.

`DialogCategory.isBlocking()` is exercised indirectly (INFORMATIONAL + OPERATIONAL paths) but `EXCLUSIVE` is never instantiated in any test, leaving a gap in the enum coverage.

`PaperSizeStep` and `TempoSection` have zero test coverage despite carrying genuine computation logic (unit conversion, spinner round-trips, template parsing). `PropertiesStateStore`'s null-remove branch is also untested.

**Production observation:** `PaperSizeStep` uses a raw `new Insets(5, 5, 0, 5)` magic number directly in GridBagConstraints rather than a FlatLaf prop or named constant — this violates the no-magic-numbers rule.

### 10B — Input & Validation Dialogs

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| AttachmentDialog | `getData()` — when `selectedElement` is null, fetches selected element + line from score; when already set (e.g. `showForElement`), skips fetch | unit | none | missing | Write unit test: mock `requireScoreView()` chain; verify `selectedElement`/`selectedLine` are set from selection on first call, left intact on second call |
| AttachmentDialog | `getData()` — `adding` flag correctly derived from `getExistingChange` returning null vs non-null; `removeButton` visibility toggled; `okButton` text switched between Add and Modify | unit | none | missing | Write unit test: stub `getExistingChange` returning null/non-null; assert button text and `removeButton` visibility |
| AttachmentDialog | `getData()` — returns `true` unconditionally (never cancels dialog) | unit | none | missing | Trivially verifiable; include in the above test |
| AttachmentDialog | `setData()` — wraps `applyChange` in `line.withModification` → `line.modifyElement` on the correct element index | unit | none | missing | Write unit test: stub line + element; verify `modifyElement` called with correct index and `ElementField` |
| AttachmentDialog | Remove button action — calls `clearChange` inside `withModification` on correct index and hides dialog | unit | none | missing | Write unit test: fire the remove action listener; verify `clearChange` invoked via mutation and dialog hidden |
| AttachmentDialog | `setData()`/remove button — throws `IllegalStateException` when `element` or `line` is null | unit | none | missing | Write unit test verifying the guard |
| AnnotationDialog | `populateControls(null)` — defaults to `DEFAULT_ANNOTATION` text, left alignment, above position | unit | none | missing | Write unit test: call `populateControls(null)`; assert combo text and radio selections |
| AnnotationDialog | `populateControls(existing)` — correctly maps `CENTER_ALIGNMENT` → centerRadio, `RIGHT_ALIGNMENT` → rightRadio, other → leftRadio; `yPosPx < 0` → aboveRadio else belowRadio | unit | none | missing | Write unit test with three alignment values and two yPosPx values |
| AnnotationDialog | `applyChange` — empty/null annotation text removes existing attachment if present, and is a no-op if absent | unit | none | missing | Write unit test: stub `findAttachment` returning non-null; verify `removeAttachment` called when text empty |
| AnnotationDialog | `applyChange` — builds `Annotation` with correct alignment float from radio selection and sets `yPosPx` to `ABOVE` or `BELOW` | unit | none | missing | Write unit test for each radio combination; assert annotation fields |
| AnnotationDialog | `applyChange` — updates existing attachment vs adds new one | unit | none | missing | Test both branches: stub findAttachment returning existing vs null |
| AnnotationDialog | `clearChange` — removes `AnnotationAttachment` if present; no-op if absent | unit | none | missing | Write unit test for both branches |
| BeatChangeDialog | `populateControls(null)` — defaults to `CROTCHET_DOTTED` for duration and `CROTCHET` for beat | unit | none | missing | Write unit test: call with null; assert combo selections |
| BeatChangeDialog | `populateControls(existing)` — sets both combos from `BeatChange.duration()` and `BeatChange.beat()` | unit | none | missing | Write unit test with a real `BeatChange` |
| BeatChangeDialog | `applyChange` — skips mutation if either combo returns null | unit | none | missing | Write unit test: stub null return; verify neither `addAttachment` nor `setBeatChange` called |
| BeatChangeDialog | `applyChange` — updates existing `BeatChangeAttachment` vs adds new one | unit | none | missing | Test both branches of findAttachment |
| BeatChangeDialog | `clearChange` — removes attachment if present; no-op if absent | unit | none | missing | Same pattern as Annotation |
| KeySignatureChangeDialog | `getData()` — pre-populates label from `indexOfLine + 1`, combo from `line.getKeyType()`, spinner from `line.getKeyAccidentalCount()` | unit | none | missing | Write unit test: mock score/song/line; assert label text and control values |
| KeySignatureChangeDialog | `setData()` — skips post if `keysCombo.getSelectedItem()` is null | unit | none | missing | Write unit test: force combo to null; verify no post |
| KeySignatureChangeDialog | `setData()` — posts `KeySignatureDidChangeNotification` with selected key type and spinner integer value | unit | none | missing | Write unit test: set known values; verify notification posted with correct fields |
| TempoChangeDialog | `populateControls(null)` — default Tempo: BPM=120, `CROTCHET`, "Moderate", showTempo=true | unit | none | missing | Write unit test: call with null; assert `TempoSection.setTempo` arg fields |
| TempoChangeDialog | `populateControls(existing)` — forwards existing attachment's Tempo to `TempoSection.setTempo` | unit | none | missing | Write unit test with a real attachment |
| TempoChangeDialog | `applyChange` — builds `Tempo` from `TempoSection` getters; `showTempo = !isShowOnlyDescription()` | unit | none | missing | Write unit test; verify Tempo construction and flag inversion |
| TempoChangeDialog | `applyChange` — updates existing attachment vs adds new one | unit | none | missing | Test both branches |
| TempoChangeDialog | `clearChange` — removes attachment, then calls `clearTempoIfOrphaned` | unit | none | missing | Write unit test: verify both `removeAttachment` and `clearTempoIfOrphaned` called |
| TempoChangeDialog | `showForElement` — static factory pre-sets `selectedElement`/`selectedLine` before showing | unit | none | missing | Write unit test verifying fields are set correctly (widen to package-private if needed) |
| ResolutionDialog | `handleResolutionChange()` — width = `round(scale * sheetWidthPx) + border.width`; scale = `resolution / screenDpi` | unit | none | missing | Write pure-logic unit test: inject known sheetWidthPx and mock `getDpi()`; assert widthField text |
| ResolutionDialog | `handleResolutionChange()` — height subtracts `sheetHeightWithoutLyricsPx` when `withoutLyricsCheck` selected | unit | none | missing | Test with checkbox selected vs deselected; assert heightField text |
| ResolutionDialog | `handleResolutionChange()` — height subtracts `sheetHeightWithoutTitlePx` when `exportWithoutTitleCheckBox` selected | unit | none | missing | Same pattern for title checkbox |
| ResolutionDialog | `handleResolutionChange()` — both deductions can combine additively | unit | none | missing | Test with both checked |
| ResolutionDialog | `getData()` — `withoutLyricsCheck` disabled (and deselected) when both lyrics collections empty | unit | none | missing | Mock song with empty lyrics; assert disabled state |
| ResolutionDialog | `getData()` — `exportWithoutTitleCheckBox` disabled (and deselected) when title is empty | unit | none | missing | Mock song with empty title; assert disabled state |
| ResolutionDialog | `getData()` — resets `approved = false` on each show | unit | none | missing | Verify approved is false before `setData` runs |
| ResolutionDialog | `setData()` — sets `approved = true` and persists DPI to `Prefs` | unit | none | missing | Write unit test: mock `Prefs`; verify put and approved flag |
| ResolutionDialog | `isApproved()` / `getResolution()` / `isWithoutLyrics()` / `isWithoutTitle()` / `getBorder()` — simple state accessors | none | none | adequate | No test needed — trivial getters |
| FontDialog | `getData()` — passes `selectedFont` to `chooser.setSelectedFont` | unit | none | missing | Write unit test: set initial font; call getData; assert chooser.getSelectedFont equals it |
| FontDialog | `setData()` — harvests `chooser.getSelectedFont()` into `selectedFont` | unit | none | missing | Write unit test: set chooser font; call setData; assert getSelectedFont() |
| FontDialog | `showDialog` — returns `selectedFont` unchanged when dialog is cancelled (setData not called) | unit | none | missing | Verify font remains initial value when OK is not pressed |
| FontDialog | `getExtraHeight()` returns `EXTRA_PREVIEW_HEIGHT` constant; `isResizable()` returns true | none | none | adequate | Pure display/layout wiring |

**Notes:**

Zero tests exist for any of the seven classes in this slice. The only tests in `src/test/java/songscribe/ui/dialog/` cover `BaseDialog` infrastructure (counter, position, geometry persistence) — the concrete dialog classes are untouched.

**Key gaps by priority:**

1. `ResolutionDialog.handleResolutionChange()` is the richest pure-logic target: it performs floating-point scale multiplication and pixel arithmetic with two independent boolean flags; four distinct test cases cover the cross-product of the checkbox flags. The `stateChanged` listener delegates directly to this method, making it straightforwardly testable by calling `handleResolutionChange()` with known field state.

2. `AttachmentDialog` is the abstract base for four concrete dialogs; its `getData()` add/modify branching (button text, removeButton visibility) and `setData()` mutation delegation are shared risks. Testing this base class with a minimal concrete subclass stub covers the shared plumbing once.

3. `AnnotationDialog.populateControls` and `applyChange` have three-way alignment branching and sign-based above/below selection — exactly the kind of branching mutation testing would kill.

4. `KeySignatureChangeDialog.setData()` posts a `KeySignatureDidChangeNotification`; the null-guard on `keysCombo.getSelectedItem()` is a silent no-op that could mask bugs.

5. `TempoChangeDialog.clearChange` has a two-step side effect: `removeAttachment` then `clearTempoIfOrphaned`; both steps must be verified together.

**Production observation (do not fix here):** `KeySignatureChangeDialog` constructs its own button panel by manually adding `okButton`/`cancelButton` to a `JPanel` inside `contentPanel` instead of using `modifyButtonPanel()` — this is a divergence from the `StandardDialog` convention documented in `dialogs.md` and may mean the button panel is not attached via the standard `BorderLayout.SOUTH` constraint.

### 10C — Settings, Export & Informational Dialogs

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| PreferencesDialog | `programToIndex` — linear scan: returns 0 on miss, first matching index otherwise | unit | none | missing | Add `PreferencesDialogTest` testing: exact match, miss→0, first-of-duplicates |
| PreferencesDialog | `ensureInstrumentsLoaded` / `instrumentsLoaded` guard — loads only once, sorted by name | unit | none | missing | Add tests for idempotency and alphabetic sort |
| PreferencesDialog | `PlayTab.volumeToSliderIndex` — nearest-stop snap with tie-breaking | unit | none | missing | Add tests for each exact stop, midpoints, and values outside range (e.g. 0, 127) |
| PreferencesDialog | `GeneralTab`/`PlayTab`/`InstrumentsTab` getData/setData — pure Prefs read/write wiring, no branching | none | — | — | No test warranted (trivial field read from Prefs → component) |
| PreferencesDialog | Live preference writes via ActionListeners (page size, metric, appearance, startup action) — fire directly on radio click, no OK button | none | — | — | No test warranted (framework ActionListener wiring) |
| SongSettingsDialog | `TextTab.setData` — change-detection: skips `MetadataDidChangeNotification` when no field changed | unit | none | missing | Add test: setData with unchanged fields posts nothing; changed fields post notification |
| SongSettingsDialog | `TextTab.setData` — number/year `Integer.parseInt` validation: null set on `NumberFormatException` | unit | none | missing | Add tests: non-numeric number/year, empty (valid), numeric (valid) |
| SongSettingsDialog | `TextTab.isValidData` — delegates to `NonEmptyGuard.validate()` for title and attribution | unit | none | missing | Add tests: empty title → false, non-empty → true (suppressed OptionDialogs) |
| SongSettingsDialog | `TextTab.TakeFirstLyricsWordAction` — word extraction, capitalisation, hyphen handling, boundary trim | unit | none | missing | Add tests: normal lyrics, leading spaces, lyrics with hyphens, all-underscore lyrics (empty buffer → IOOBE bug) |
| SongSettingsDialog | `TextTab.AddDateAndPlaceAction` — date-string appended to attribution; empty attribution → `charAt(-1)` crash | unit | none | missing | Add tests: empty attribution (exposes IOOBE), attribution ending in `\n`, attribution not ending in `\n`; also year-required and place-required paths |
| SongSettingsDialog | `TextTab.getDateString` — format with month+day, month only, year only, empty year→"" | unit | none | missing | Add tests for all branches |
| SongSettingsDialog | `MusicTab.validateLineWidth` — parses double, converts metric↔inches, returns -1 on unparseable/out-of-range | unit | none | missing | Add tests: empty, non-numeric, below min, above max, valid inches, valid cm |
| SongSettingsDialog | `MusicTab.setKeyComboFromSong` — canonicalizes `(SHARPS, 0)` → `(FLATS, 0)` | unit | none | missing | Add test: song with 0 sharps maps to `(FLATS, 0)` selection |
| SongSettingsDialog | `MusicTab.setData` — tempo/key change-detection: posts only changed notifications inside single `withModification` bracket | unit | `SongMetadataDialogFlowTest` (bracketing pattern only; does NOT cover tempo/key) | missing | Add test: no-change → no message; tempo-only → one TempoDidChangeNotification; key-only; both → coalesced |
| SongSettingsDialog | `KeyCellRenderer.SELECTIONS` list — exactly 15 entries (no-accidentals + 7 flats + 7 sharps), in canonical order | unit | none | missing | Add test for list size and order |
| SongSettingsDialog | `FontTab.getData`/`setData`/`applyDefaultFonts` — pure display font assignment, no branching logic | none | — | — | No test warranted |
| SongSettingsDialog | Tabbed dialog pane built with `createTabbedPane()`, not `new JTabbedPane()` | none | — | — | No test warranted (structural/wiring) |
| ExportMidiDialog | `setData` — saves/restores playback settings around export; builds sequence with override instrument/tempo/repeats | unit | none | missing | Add test (mock `PlaybackController`, `requireScoreView`) verifying settings are restored even on exception |
| ExportMidiDialog | `getData` — loads instrument index from Prefs via `programToIndex`, loads `PLAY_WITH_REPEATS` pref | none | — | — | No test warranted (trivial pref read → component) |
| ExportPDFDialog | `getData`/`setData`/`getPaperSizeData` — delegates entirely to `PaperSizeStep`; `getPaperSizeData` is `@Nullable` until OK clicked | unit | none | missing | Add test: `getPaperSizeData` is null before setData, non-null after |
| PlatformFileDialog | `convertFilter` — strips ` (ext1, ext2)` suffix from description; no paren → unchanged | unit | none | missing | Add tests: description with paren, without paren, paren at index 0 |
| PlatformFileDialog | `getFileFilter` — extension-based lookup (higher priority) vs dropdown-based lookup vs fallback to first filter | unit | none | missing | Add tests: filename matches ext → returns matching filter; filename matches nothing → returns dropdown match; dropdown also no match → returns first |
| PlatformFileDialog | `showSaveDialog` (static) — appends first extension when no existing extension matches; handles leading-dot form | unit | none | missing | Add tests: already has matching ext, has no ext, leading-dot extension form, multi-extension array |
| PlatformFileDialog | Constructor overload initialFilterIndex clamping — `Math.clamp(initialFilterIndex, 0, len-1)` | unit | none | missing | Add test: negative index, over-length index, valid index |
| ProgressBarDialog | `nextValue(int)` — increments bar value by delta; `nextValue()` delegates to `nextValue(1)` | none | — | — | No test warranted (trivial delegation to JProgressBar, no branching) |
| DoNotShowMessage | `setVisible(true)` — suppresses show when `java.util.prefs` node already has `propName=true` | unit | none | missing | Add test: prefs not set → `super.setVisible(true)` called; prefs set → suppressed |
| DoNotShowMessage | `setData` — persists `propName=true` only if checkbox is selected | unit | none | missing | Add tests: checkbox checked → pref written; unchecked → pref not written |
| DoNotShowMessage | Hardcoded checkbox label `"Don't show this message again."` — bypasses Strings system | none | — | — | Production observation: not a test gap, but violates Strings convention (note only) |
| AboutDialog | Pure display/wiring, no branching logic | none | — | — | No test warranted |
| HelpDialog | Pure display/wiring (addToList, list→HTML load on selection); IO error path is framework-delegated | none | — | — | No test warranted |
| HTMLDialog | Pure display/wiring | none | — | — | No test warranted |
| KeyMapDialog | Pure display/wiring (subclass of HTMLDialog) | none | — | — | No test warranted |
| ReportBugDialog | Email URI construction — bug vs. feature-request branch, log file attachment conditional, version/OS interpolation | unit | none | missing | Add test: answer=bug → attachment appended; answer=feature → no attachment; cancel → no open |
| TutorialDialog | Pure display/wiring (subclass of HelpDialog) | none | — | — | No test warranted |
| WhatsNewDialog | `getData` returns `false` (suppresses show) when release-notes file is absent | unit | none | missing | Add test: `noReleaseNotes=true` path → `getData()` returns `false` (needs package-private visibility on field) |

#### Notes

**Key gaps.** All fourteen classes in this slice have zero test coverage at both unit and e2e level. The highest-value gaps are:

1. **`SongSettingsDialog.TextTab`** contains two crash-risk production bugs: `TakeFirstLyricsWordAction` calls `words.charAt(words.length() - 1)` without an empty-buffer guard (throws `StringIndexOutOfBoundsException` when lyrics contain only separators); `AddDateAndPlaceAction` calls `attribution.charAt(attribution.length() - 1)` without an empty-attribution guard. Both are caught by unit tests before any fix is written.

2. **`PlatformFileDialog.getFileFilter`** has a two-path disambiguation algorithm (extension-based vs dropdown) with a fallback that is entirely untested.

3. **`PreferencesDialog.programToIndex`** and **`PlayTab.volumeToSliderIndex`** are static pure-logic methods exposed as `public`/`package-private` that can be tested directly without any UI setup.

4. **`DoNotShowMessage`** uses `java.util.prefs.Preferences` directly (bypasses the project's `Prefs` wrapper) and has a hardcoded checkbox label `"Don't show this message again."` (violates the Strings convention). The suppression logic (`setVisible`) is the one real branching behavior worth a unit test.

5. **`SongMetadataDialogFlowTest`** covers the `Song.metadataDidChange` bracketing contract (relevant to `TextTab.setData`) but does NOT cover the `MusicTab.setData` tempo/key change-detection or the `TextTab` validation/boundary paths — those remain missing.

**Existing tests in `src/test/java/songscribe/ui/dialog/`** (`BaseDialogCounterTest`, `BaseDialogPositionTest`) cover `BaseDialog` infrastructure only; none touch any class in this slice.

### 10D — Font Chooser Core & Model

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `FontNameComparator` | `compare` delegates to `Font.getName().compareTo()` — ordering by logical name, case-sensitive | unit | none | missing | Write unit test: verify ordering of fonts whose names differ only by case, and that identical names compare as 0 |
| `FontFamily` | `add` accumulates fonts into a `TreeSet` ordered by `FontNameComparator`; `getStyles()` returns them in that order | unit | none | missing | Write unit test: add fonts with names in reverse order; assert `getStyles()` returns them in ascending name order |
| `FontFamily` | `getName()` returns the family name passed to constructor (trivial getter — no logic) | none | — | — | No test warranted |
| `FontFamilies` | `add(Font)` groups fonts by family (`font.getFamily()`), creating a new `FontFamily` on first encounter and appending to the existing one thereafter (dedup-by-family) | unit | none | missing | Write unit test: add two fonts with same family, one with different family; assert `size()==2` and each `FontFamily` holds correct fonts |
| `FontFamilies` | `get(String)` returns `@Nullable FontFamily` — null when family absent | unit | none | missing | Write unit test: assert `get` returns the correct `FontFamily` for a known name and `null` for an unknown name |
| `FontFamilies` | `iterator()` iterates over family values; `size()` reflects count (delegation to `TreeMap` — trivial) | none | — | — | No test warranted |
| `FontFamilies` | `getInstance()` singleton — holds a static `FontFamilies` built at class-load time from system fonts (not directly testable without env coupling) | none | — | — | No test warranted |
| `FontFamiliesFactory` | `create()` filters out fonts whose family name starts with `"."` (macOS hidden-font prefix) | unit | none | missing | Write unit test: supply a controlled font list via `mockStatic(MyFontUtils.class)` that includes dot-prefixed and normal families; assert dot families are excluded from result |
| `FontFamiliesFactory` | `create()` groups remaining fonts by family into `FontFamilies` | unit | none | missing | Covered by the filtering test above if it also asserts grouping; or add a dedicated grouping assertion |
| `FamilyListModel` | `initialize()` lazy-builds `fontFamilyNames` from `FontFamilies`, sorted ascending by natural order | unit | none | missing | Write unit test: construct model backed by a `FontFamilies` containing families in non-alphabetical order; assert `getElementAt` returns them sorted |
| `FamilyListModel` | `getSize()` / `getElementAt(int)` delegate to initialized names (pure Swing `ListModel` wiring after initialization) | none | — | — | No test warranted |
| `FamilyListModel` | `findFirst(CharSequence)` — case-insensitive substring search over family names; returns first match or `null` when none | unit | none | missing | Write unit tests: exact match, prefix match, substring match (mixed case), no match → `null` |
| `DefaultFontSelectionModel` | `setSelectedFont` fires `ChangeEvent` when new font differs from current | unit | none | missing | Write unit test: attach a `ChangeListener`, call `setSelectedFont` with a different font, assert listener `stateChanged` called once |
| `DefaultFontSelectionModel` | `setSelectedFont` fires NO event when font equals current | unit | none | missing | Write unit test: attach a `ChangeListener`, call `setSelectedFont` with the same font, assert listener never called |
| `DefaultFontSelectionModel` | `getSelectedFontName` / `getSelectedFontFamily` / `getSelectedFontSize` return correct values from the wrapped `Font` | unit | none | missing | Write unit test: construct model with a known font; assert all three getters return expected values |
| `DefaultFontSelectionModel` | `changeEvent` lazy-initialised (created on first fire, reused thereafter) — implementation detail, no external contract | none | — | — | No test warranted |
| `DefaultFontSelectionModel` | `addChangeListener` / `removeChangeListener` / `getChangeListeners` — standard `EventListenerList` wiring | none | — | — | No test warranted |
| `FontSelectionModel` | Interface — contract tested via `DefaultFontSelectionModel` (the only impl) | none | — | — | No test warranted |
| `FontContainer` | Interface — pure wiring contract; implemented by `FontChooser` (Swing composition) | none | — | — | No test warranted |
| `FontChooser` | Swing layout and listener wiring (`initPanes`, `addComponents`, `setSelectionModel`) — pure display wiring, no branching logic | none | — | — | No test warranted |
| `FontChooser` | `setSelectedFont` temporarily removes all three `ListSelectionListener`s before updating the model, then re-adds via `initPanes` — cross-component Swing wiring; bug only observable in the real event pipeline | e2e | none | missing | Write e2e test (requires user approval): set a font on `FontChooser`, verify no listener-triggered re-entry occurs and the pane selections reflect the new font |

**Notes**

All high-value behaviors in this subsystem are completely untested. No test file anywhere under `src/test` references any class in `songscribe.ui.dialog.fontchooser` or its `model` sub-package.

Key gaps by priority:

1. **`DefaultFontSelectionModel`** — the state machine (fires vs. no-op on `setSelectedFont`) is the most regress-prone logic and the easiest to unit-test with no Swing dependency beyond constructing a `Font`.
2. **`FamilyListModel.findFirst`** — case-insensitive substring search has clear edge cases (empty string, case mismatch, no match) that are trivially unit-tested with a `FontFamilies` constructed in-test (no singletons involved).
3. **`FamilyListModel` sort order** — the lazy `initialize()` sorts family names; the sort itself is cheap to verify by constructing a `FontFamilies` directly.
4. **`FontFamiliesFactory.create` dot-filter** — the `startsWith(".")` exclusion is platform-specific behaviour (macOS hidden fonts) with no test guard; should be mocked via `mockStatic(MyFontUtils.class)`.
5. **`FontNameComparator`** — the comparator drives the ordering of styles within a `FontFamily` `TreeSet`; a pure two-line method but its comparison contract (case-sensitive, by logical name) is worth pinning.

Production observation (do not fix here): `FontFamilies.INSTANCE` is initialised at class-load time via a static field calling `FontFamiliesFactory.create()` → `MyFontUtils.getAllFonts()`. This makes `FontFamilies.getInstance()` untestable in isolation and means any test that constructs `FamilyListModel` will pull real system fonts from the JVM. Tests for `FamilyListModel` must therefore construct the model with a custom `FontFamilies` instance directly (bypassing the singleton), which requires either widening `FamilyListModel.fontFamilies` to package-private or adding an injectable constructor — a testability gap.

### 10E — Font Chooser Panes & Listeners

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| FamilyPane, PreviewPane, StylePane | Widget assembly, layout wiring, listener delegation — no branching logic | none | none | n/a | none |
| SizePane | `initSizeListModel()` step-doubling loop (pure layout data); `getSelectedSize()` list-vs-spinner branch; spinner↔list sync listener — all Swing state delegation | none | none | n/a | none |
| SearchListener | `keyTyped`: lowercases text, delegates to `FamilyListModel.findFirst()`, calls `setSelectedFamily` if non-null — all logic lives in collaborators; listener itself is pure wiring (the `findFirst` search logic is audited under 10D) | none | none | n/a | none |
| StyleCellRenderer | `getListCellRendererComponent`: extracts `entry.getName()`, passes to super — pure delegation, no logic | none | none | n/a | none |
| StyleEntry | Constructor: delegates style derivation to `MyFontUtils.getStyleDescription()` — no independent logic | none | none | n/a | none |
| StyleEntry | `equals`: compares by `font.getPSName()` | unit | none | missing | Add `StyleEntryTest.testEqualsComparesOnPsName`: two entries same PS name → equal; different PS name → not equal |
| StyleEntry | `hashCode`: delegates to `font.hashCode()` — inconsistent with `equals` (equals by PSName, hash by Font identity); breaks equals/hashCode contract when same PSName but different Font instances | unit | none | missing | Add `StyleEntryTest.testHashCodeConsistentWithEquals` (will expose the contract violation as a production bug) |
| FamilyListSelectionListener | `valueChanged` guard: skips when `getValueIsAdjusting()`; builds new `Font(family, oldStyle, oldSize)` from container state; calls `setSelectedFont` + `setPreviewFont` | unit | none | missing | Add `FamilyListSelectionListenerTest`: adjusting event → no calls; non-adjusting → correct Font constructed and set on container |
| SizeListSelectionListener | `valueChanged` guard: skips when `getValueIsAdjusting()`; derives font at new size; calls `setSelectedFont` + `setPreviewFont` | unit | none | missing | Add `SizeListSelectionListenerTest`: adjusting → no calls; non-adjusting → `deriveFont(newSize)` applied |
| StyleListSelectionListener | `valueChanged` guard: skips when `getValueIsAdjusting()`; derives font from `selectedStyle.getFont()` at current size; calls `setSelectedFont` + `setPreviewFont` | unit | none | missing | Add `StyleListSelectionListenerTest`: adjusting → no calls; non-adjusting → font derived from selected style at current size |

**Notes.**

Five behaviors warrant unit tests; all are missing. The three `*ListSelectionListener` classes share the same pattern (guard + font construction) and can be covered in a single test class each, mocking `FontContainer`. `StyleEntry.hashCode` is inconsistent with its `equals`: `equals` compares by `Font.getPSName()`, but `hashCode` delegates to `Font.hashCode()` — two `StyleEntry` instances with the same PS name but different `Font` objects will be `equals` yet have different hash codes, violating the Java contract; a test for this should be written (it will fail, exposing a production bug). `FamilyListModel.findFirst` is not in this slice but is the real logic behind `SearchListener`; it is untested and should be covered in a separate `FamilyListModelTest`. The `getStyleDescription` logic in `MyFontUtils` is complex but already partially tested in `MyFontUtilsTest`; that test covers `createFont` only and does not exercise style description derivation — a gap worth addressing in a future session.

### §10 summary (`ui/dialog`, 48 prod classes + 5 `package-info`)

Run as two waves of parallel sub-audits (Wave 1: 10A infrastructure & lifecycle;
10B input & validation dialogs; 10C settings, export & informational dialogs —
Wave 2: 10D font-chooser core & model; 10E font-chooser panes & listeners).
**164 behavior rows: 131 unit / 1 e2e / 32 none; of 132 testable, 14 adequate ·
117 missing · 1 inadequate · 0 wrong-level · 0 redundant (~89% dark).**

**Defining shape — the inverse of `message` (§8): one well-covered island in an
almost entirely dark package.** The lone bright spot is `BaseDialog`'s
infrastructure — the blocking-dialog counter (`BaseDialogCounterTest`) and
geometry persistence (`BaseDialogPositionTest`) account for **all 14 adequate
verdicts in the section**. Everything that runs *inside* a concrete dialog is
dark.

Key gaps, by theme:

1. **The validate-then-commit lifecycle is universally untested.**
   `StandardDialog`'s entire OK/Cancel path — `isValidData()` blocking,
   `setData()` tab iteration, the Cancel-without-commit branch, the
   `modifyButtonPanel()` once-only guard, `repaintScore()` null-safety — has zero
   coverage, and so does `BaseDialog.getData()`-returns-false cancellation (the
   gate that aborts showing a dialog) and the `tabWillShow`/`tabWillHide`
   lifecycle dispatch. Every concrete dialog's `getData`/`setData`/`applyChange`/
   `clearChange` (the model-mutation commit) is `missing`.

2. **Richest pure-logic targets (all `unit`, all `missing`):**
   `ResolutionDialog.handleResolutionChange()` (scale = dpi-ratio, pixel
   arithmetic, two independent checkbox deductions — the single densest
   computation); `PaperSizeStep` (unit conversion + `;`-delimited template
   parsing + mirror-label switching); `PlatformFileDialog.getFileFilter` /
   `showSaveDialog` / `convertFilter` (extension-vs-dropdown disambiguation +
   extension appending + index clamping); `SongSettingsDialog.TextTab`
   (`getDateString` branches, line-width metric↔inch validation, change-detection
   gating of notifications) — plus the two crash bugs below;
   `PreferencesDialog.programToIndex` / `PlayTab.volumeToSliderIndex` (static
   pure logic); `DefaultFontSelectionModel.setSelectedFont` (fire-vs-no-op on
   change); `FamilyListModel.findFirst` (case-insensitive search) + lazy sort;
   `FontFamiliesFactory.create` (macOS dot-prefix filter); `FontNameComparator`.

3. **`AttachmentDialog` is the shared base for four attachment dialogs**
   (`Annotation`/`BeatChange`/`Tempo` + itself); its `getData()` add-vs-modify
   branching (OK-button text, remove-button visibility) and `setData()`
   `withModification` delegation are shared, high-leverage risks.

4. **fontchooser is mostly view/model wiring → `none`** (32 of the section's
   `none` rows concentrate here and in the informational dialogs). The thin layer
   of real logic — selection-model change events, family grouping/search,
   comparator ordering, the three `*ListSelectionListener` guard+derive bodies —
   is `unit`/`missing`, and `StyleEntry` carries a genuine equals/hashCode
   contract bug (see observations).

**Only one genuine e2e** in the whole package: `FontChooser.setSelectedFont`
temporarily detaches its three `ListSelectionListener`s before re-applying them —
re-entrancy correctness only observable in the real Swing pipeline. **inadequate
(1):** `DialogCategory.isBlocking` — the `EXCLUSIVE` constant is never
instantiated in the counter tests, so its blocking contract is only assumed.
**No dead classes found.**

**Scope/dedup during assembly:** `FamilyListModel.findFirst` surfaced in both 10D
(its owning `model` slice) and 10E (where `SearchListener` delegates to it); kept
under 10D only. `MyFontUtils.getStyleDescription` (backing `StyleEntry`) is out
of scope — it belongs to `util` (Session 4); `MyFontUtilsTest` covers `createFont`
but not style-description derivation, a gap noted for that package.

### §10 production observations (filed as GitHub issue #415)

Recorded during the audit, **not fixed** (audit is read-only):

1. **`SongSettingsDialog.TextTab.TakeFirstLyricsWordAction`** —
   `words.charAt(words.length() - 1)` has no empty-buffer guard; lyrics composed
   only of separators leave the buffer empty and throw
   `StringIndexOutOfBoundsException`. Real crash bug.
2. **`SongSettingsDialog.TextTab.AddDateAndPlaceAction`** —
   `attribution.charAt(attribution.length() - 1)` has no empty-attribution guard;
   an empty attribution field throws `StringIndexOutOfBoundsException`. Real crash
   bug.
3. **`StyleEntry`** breaks the `equals`/`hashCode` contract: `equals` compares by
   `font.getPSName()` but `hashCode` delegates to `font.hashCode()`. Two entries
   with the same PostScript name but different `Font` instances are `equals` yet
   hash differently — corrupts hash-based collections.
4. **`DoNotShowMessage`** bypasses the project `Prefs` wrapper, writing directly
   to `java.util.prefs.Preferences`, and hardcodes the checkbox label
   `"Don't show this message again."` instead of resolving it through `Strings`.
5. **`KeySignatureChangeDialog`** adds its OK/Cancel buttons to a `JPanel` inside
   `contentPanel` rather than overriding `modifyButtonPanel()`, deviating from the
   `StandardDialog` convention in `dialogs.md` (the button row may not attach via
   the standard `BorderLayout.SOUTH` path).
6. **`PaperSizeStep`** uses raw magic-number insets `new Insets(5, 5, 0, 5)`
   (minor; `development.md` no-magic-numbers).
7. **Testability gap (for remediation):** `FontFamilies.INSTANCE` is built at
   class-load from real system fonts and `FamilyListModel` hardcodes
   `FontFamilies.getInstance()`. Unit-testing `FamilyListModel` sort/`findFirst`
   requires widening `fontFamilyNames` or adding an injectable constructor before
   tests can supply a controlled `FontFamilies`.

## 11. `ui/menu` + `ui/playback` + `ui/platform` + top-level `ui` (audited 2026-05-22)

Audited via six parallel production-first sub-audits across two waves — Wave 1: **11A** `ui/menu`; **11B** `ui/playback`; **11C** `ui/platform/mac` — Wave 2: **11D** `MusicEditOperations`; **11E** appearance & dialog helpers (`OptionDialogs`, `EndingConfirms`, `AppearanceManager`, `Appearance`, `LafOperations`); **11F** display & constants (`KeySignatureDisplay`, `Constants`, `Control`, `Mode`, `FlatLafProps`). 31 production classes (+5 `package-info`), matching the ~38 estimate. None of the top-level `ui` classes had its own audit row before this session — prior sessions referenced them only as collaborators. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.


### 11A — `ui/menu` (Menu Construction & Controller)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MenuController` | `buildLabels` — unique filenames: each path returns its filename as label | unit | none | missing | Add `MenuControllerTest.testBuildLabelsUniqueFilenames` — pass list of distinct filenames, assert each label equals the filename |
| `MenuController` | `buildLabels` — duplicate filenames: appends shortest unique parent suffix to disambiguate | unit | none | missing | Add `testBuildLabelsDuplicateFilenames` — two paths with same filename, different parent dirs; assert label = `filename — parentDir` |
| `MenuController` | `buildLabels` — duplicate filenames requiring multiple depth levels: falls back to deeper suffix when depth-1 parent is also identical | unit | none | missing | Add `testBuildLabelsTwoLevelDisambiguation` |
| `MenuController` | `buildLabels` — all-duplicate fallback: uses full path with `~` substitution when no depth resolves uniqueness | unit | none | missing | Add `testBuildLabelsFallbackToFullPath` |
| `MenuController` | `tildeSubstitute` — path under home directory replaced with `~/...` | unit | none | missing | Add `testTildeSubstituteUnderHome` |
| `MenuController` | `tildeSubstitute` — path outside home directory returned unchanged | unit | none | missing | Add `testTildeSubstituteOutsideHome` |
| `MenuController` | `tildeSubstitute` — path exactly equal to home directory returns `~` | unit | none | missing | Add `testTildeSubstituteExactlyHome` |
| `MenuController` | `rebuildOpenRecentMenu` — empty recents list: menu contains a single disabled "No recent documents" item | unit | none | missing | Add `testRebuildOpenRecentMenuEmpty` — call `rebuildOpenRecentMenu` via reflection (or extract to package-private); assert item count = 1, disabled |
| `MenuController` | `rebuildOpenRecentMenu` — non-empty recents list: menu contains one item per path + separator + Clear Recents action | unit | none | missing | Add `testRebuildOpenRecentMenuNonEmpty` |
| `MenuController` | `recentDocumentsDidChange` handler rebuilds the open-recent menu when the MBassador notification fires | unit | none | missing | Add `testRecentDocumentsDidChangeRebuildsMen` — post `RecentDocumentsDidChangeNotification` via `MessageCenter`, assert menu is updated |
| `MenuController` | `initFileMenu` — non-macOS: Quit action is present in file menu | unit | none | missing | Add `testQuitActionPresentOnNonMac` (mock `SystemInfo.isMacOS = false`) |
| `MenuController` | `initFileMenu` — macOS: Quit action is absent from file menu | unit | none | missing | Add `testQuitActionAbsentOnMac` (mock `SystemInfo.isMacOS = true`) |
| `MenuController` | `initEditMenu` — non-macOS: Preferences action is present in edit menu | unit | none | missing | Add `testPreferencesActionPresentOnNonMac` |
| `MenuController` | `initEditMenu` — macOS: Preferences action is absent from edit menu | unit | none | missing | Add `testPreferencesActionAbsentOnMac` |
| `MenuController` | `initMenus` — macOS: `setJMenuBar` is called on `mainFrame`; non-macOS: it is not | unit | none | missing | Add `testJMenuBarSetOnMacOnly` — two cases, mock `SystemInfo.isMacOS` |
| `MenuController` | `initHelpMenu` / `addCommonHelpItems` (dead — commented out in `initMenus`) | none | none | none | N/A — unreachable code |
| `MenuController` | `initLaunchMenu` (dead — referenced only in commented-out code) | none | none | none | N/A — unreachable code |
| `NotationMenu` | Constructor wires all action groups into submenus in the expected order | none | none | none | Pure declarative wiring; no branching |
| `NotationMenu` | `menuSelected` listener: when a `ScoreView` with a controller is present, `MAKE_ENDING_ACTION.validate(ctrl)` is called | unit | none | missing | Add `NotationMenuTest.testMenuSelectedCallsValidateWhenControllerPresent` — construct `NotationMenu` with a mock frame; fire the `menuSelected` event; verify `MAKE_ENDING_ACTION.isEnabled()` reflects validation result |
| `NotationMenu` | `menuSelected` listener: when `ScoreView` is null or has no controller, `MAKE_ENDING_ACTION` is disabled | unit | none | missing | Add `testMenuSelectedDisablesMakeEndingWhenNoController` |
| `NotationMenu` | `createTupletMenu` — separator separates tuplet add-actions from remove action | none | none | none | Pure layout wiring |
| `NotationMenu` | `createDynamicsMenu` — all dynamic marking radio items added from `DYNAMIC_MARKING_ACTION_GROUP` | none | none | none | Pure declarative wiring |
| `BarlineMenu` | `FinalTerminalAction.createFinalDoubleBarline` — action fires and replaces terminal to `FINAL_DOUBLE_BARLINE` without showing a confirm dialog | unit | `BarlineMenuTest.testFinalDoubleBarlineItemReplacesTerminalWithoutConfirm` | adequate | — |
| `BarlineMenu` | `FinalTerminalAction.createFinalRightRepeat` — action fires and replaces terminal to `REPEAT_RIGHT` without showing a confirm dialog | unit | `BarlineMenuTest.testFinalRightRepeatItemReplacesTerminalWithoutConfirm` | adequate | — |
| `BarlineMenu` | Radio selection reflects current terminal: `FINAL_DOUBLE_BARLINE` selected, right-repeat unselected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForFinalBarline` | adequate | — |
| `BarlineMenu` | Radio selection reflects current terminal: right-repeat selected, final-double unselected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForRightRepeat` | adequate | — |
| `BarlineMenu` | Terminal items are in the same `ButtonGroup` — selecting one deselects the other | unit | none | missing | Add `testTerminalItemsAreMutuallyExclusive` — check that when you set one selected, the other becomes deselected via the shared `ButtonGroup` |
| `BarlineMenu` | `BARLINE_ACTIONS` items are added as `JRadioButtonMenuItem`s before the separator | none | none | none | Pure declarative wiring |
| `FermataMenuItem` | Entire class — superseded by `FERMATA_ACTION` (`FermataAction`); has zero references in production code | none | none | none | Dead class — no tests warranted; should be deleted |
| `FermataMenuItem` | `actionPerformed` — selected: adds `FermataAttachment` to preview element | none | none | none | Logic duplicated by `FermataAction.applyToElement`; dead path |
| `FermataMenuItem` | `actionPerformed` — deselected: removes existing `FermataAttachment` from preview element | none | none | none | Logic duplicated by `FermataAction.applyToElement`; dead path |
| `AccidentalMenu` | Constructor: accidental radio items from `ACCIDENTAL_ACTION_GROUP` + separator + `ACCIDENTAL_IN_PARENS_ACTION` checkbox | none | none | none | Pure declarative wiring |
| `ArticulationMenu` | Constructor: `ACCENT_ACTION` checkbox first, then articulation radio items from `ARTICULATION_ACTION_GROUP` | none | none | none | Pure declarative wiring |
| `DotMenu` | Constructor: dot radio items from `DOT_ACTION_GROUP` | none | none | none | Pure declarative wiring |
| `DurationMenu` | Constructor: note duration radio items from `NOTE_DURATION_ACTIONS` | none | none | none | Pure declarative wiring |
| `GlissandoMenu` | Constructor: glissando + slide-out as radio items | none | none | none | Pure declarative wiring |
| `RepeatsMenu` | Constructor: repeat radio items from `REPEAT_ACTIONS` | none | none | none | Pure declarative wiring |

**Notes:**

The most critical gap in this package is the complete absence of tests for `MenuController.buildLabels` and its helpers `disambiguate` and `tildeSubstitute`. These are non-trivial pure-static methods with multiple branching paths (unique vs. duplicate filenames, iterative depth search, home-directory path substitution, full-path fallback) and no test coverage whatsoever. A bug here produces silently wrong menu labels for recently-opened files — a regression that would be invisible until a user notices duplicate labels in the Open Recent submenu.

The second significant gap is `rebuildOpenRecentMenu` and the `recentDocumentsDidChange` MBassador handler. The "empty recents" vs "non-empty recents" code paths and the notification-driven rebuild are entirely untested. These behaviors are straightforward to unit-test with a mocked `RecentDocumentsManager` and are the core runtime logic of the Open Recent submenu. The `NotationMenu` `menuSelected` dynamic enable/disable of `MAKE_ENDING_ACTION` is also missing a test, though it is lower risk since `validate()` itself is well-tested at the action level.

`FermataMenuItem` is dead code: it has zero references in `src/main` (confirmed by `find_referencing_symbols`) and its functionality is already covered by `FERMATA_ACTION` / `FermataAction`. `MenuController.initHelpMenu`, `addCommonHelpItems`, and `initLaunchMenu` are similarly unreachable (callers are commented out with no active path). `BarlineMenuTest` is a bright spot — it tests the only non-trivial wiring logic in `BarlineMenu` (action binding and radio state) at the right level with real assertions.

**Tally:** 37 rows — 4 adequate · 18 missing · 0 inadequate · 0 wrong-level · 15 none · 0 redundant.

**Dead code:**
- `FermataMenuItem` — zero references in `src/main` or `src/test`; superseded by `FermataAction`.
- `MenuController.initHelpMenu` — zero callers; its invocation in `initMenus` is commented out.
- `MenuController.addCommonHelpItems` — only called from `initHelpMenu` (itself dead).
- `MenuController.initLaunchMenu` — zero callers; its invocation in `initMenus` is commented out.

**Production observations:**
- `FermataMenuItem` (dead class) and the three commented-out methods in `MenuController` constitute accumulated dead code that should be deleted to avoid future confusion.
- `MenuController.buildLabels` is `private static` yet contains 30+ lines of complex path-disambiguation logic. Its access modifier prevents direct unit-testing without reflection or a package-private helper; widening to package-private would unblock tests without changing production behavior.

### 11B — `ui/playback` (Transport, MIDI Controller & Play Thread)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `PlaybackController` | `selectionDidChange` — does nothing when not paused (PLAYING state) | unit | `PlaybackControllerTest.testDoesNothingWhenPlaying` | adequate | — |
| `PlaybackController` | `selectionDidChange` — does nothing when not paused (STOPPED state) | unit | `PlaybackControllerTest.testDoesNothingWhenStopped` | adequate | — |
| `PlaybackController` | `selectionDidChange` — clears highlight and updates `activeSelection` when paused with new selection | unit | `PlaybackControllerTest.testClearsHighlightAndUpdatesSelectionWhenPausedWithSelection` | adequate | — |
| `PlaybackController` | `selectionDidChange` — stops when selection cleared (null) while paused | unit | `PlaybackControllerTest.testStopsWhenSelectionClearedWhilePaused` | adequate | — |
| `PlaybackController` | `togglePlayPause` — transitions STOPPED → PLAYING (calls `play(null)`) | unit | none | missing | Add unit test: mock sequencer, verify state becomes PLAYING and `PlaybackStateDidChangeNotification` posted |
| `PlaybackController` | `togglePlayPause` — transitions PLAYING → PAUSED (calls `playbackDidPause`) | unit | none | missing | Add unit test: mock sequencer, assert state becomes PAUSED |
| `PlaybackController` | `togglePlayPause` — PAUSED with same selection calls `resume()` | unit | none | missing | Add unit test: confirm resume path taken (tick position restored) |
| `PlaybackController` | `togglePlayPause` — PAUSED with changed selection calls `play(newSelection)` | unit | none | missing | Add unit test: verify `activeSelection` updated to new selection |
| `PlaybackController` | `playbackDidStart` — sets state to PLAYING and posts `PlaybackStateDidChangeNotification` | unit | none | missing | Add unit test: assert state and notification |
| `PlaybackController` | `playbackDidPause` — sets state to PAUSED, saves tick position, posts notification | unit | none | missing | Add unit test: mock sequencer tick, verify saved `pausedTickPosition` |
| `PlaybackController` | `stop` — sets state to STOPPED, clears `activeSelection` and `pausedTickPosition`, posts notification | unit | none | missing | Add unit test via `stop()` directly |
| `PlaybackController` | `rewindToBeginning` — while PLAYING: clears highlight and seeks sequencer to tick 0 | unit | none | missing | Add unit test: mock sequencer, verify `setTickPosition(0)` called |
| `PlaybackController` | `rewindToBeginning` — while PAUSED: calls stop (state becomes STOPPED) | unit | none | missing | Add unit test: set state PAUSED, assert state becomes STOPPED |
| `PlaybackController` | `rewindToBeginning` — while STOPPED: no-op | unit | none | missing | Add unit test: state remains STOPPED, no exceptions |
| `PlaybackController` | `handleMetaMessage` — SEQUENCE_NUMBER message decodes line/note indices and calls `updatePlayingNote` | unit | none | missing | Add unit test: construct a `MetaMessage` with packed line+note bytes, mock `ScoreView`, verify `setPlayingIndices` called correctly |
| `PlaybackController` | `handleMetaMessage` — END_OF_TRACK message calls `stop()` | unit | none | missing | Add unit test: assert state becomes STOPPED and notification posted |
| `PlaybackController` | `updatePlayingNote` — clears previous line highlight when line changes | unit | none | missing | Add unit test: set `previousPlayingLine`, call `updatePlayingNote` with different line, verify old `setPlayingIndices(-1,-1)` |
| `PlaybackController` | `updatePlayingNote` — does not clear previous line when line unchanged | unit | none | missing | Add unit test: same line index, verify previous line component NOT cleared |
| `PlaybackController` | `applyPrefsDuringPlayback` — does nothing when not PLAYING | unit | none | missing | Add unit test: set state STOPPED or PAUSED, assert no sequencer interaction |
| `PlaybackController` | `applyPrefsDuringPlayback` — while PLAYING: stops, rebuilds sequence, restores tick, restarts | unit | none | missing | Add unit test: mock sequencer, verify stop/setSequence/setTickPosition/start sequence |
| `PlaybackController` | `setLoopSequence` — sets loop continuously when pref LOOP_PLAYBACK=true and selection is not a single note | unit | none | missing | Add unit test: mock `Prefs.getBoolean`, verify `setLoopCount(Sequencer.LOOP_CONTINUOUSLY)` |
| `PlaybackController` | `setLoopSequence` — does not loop when selection is a single note (begin==end), even if pref is true | unit | none | missing | Add unit test: selection with begin==end, assert `setLoopCount(0)` |
| `PlaybackController` | `buildSequenceForSelection` — null selection builds full sequence | unit | none | missing | Add unit test: verify `MidiSequenceBuilder.buildFullSequence()` path |
| `PlaybackController` | `buildSequenceForSelection` — non-null selection builds from note to end | unit | none | missing | Add unit test: verify `buildFromNoteToEnd(lineIndex, begin)` path |
| `PlaybackController` | `getPlaybackSettings` / `applySettings` round-trip preserves all fields | unit | none | missing | Add unit test: set fields, `getPlaybackSettings()`, `applySettings()`, verify fields restored |
| `PlaybackController` | `applyVolumeFromPrefs` — delegates to `MidiController.setPlaybackVolume` with pref value | unit | none | missing | Add unit test: mock `Prefs.getInt` and `MidiController`, verify forwarding |
| `MidiController` | `setPlaybackVolume` — percent 50..100 linearly scales to MIDI CC7 values ~64..127 (boundary/midpoint values) | unit | none | missing | Pure arithmetic: add unit test for boundary values (50→64, 100→127, 75→~96) |
| `MidiController` | `setPlaybackVolume` — percent below 50 clamps to 50; above 100 clamps to 100 | unit | none | missing | Add unit test for out-of-range inputs |
| `MidiController` | `setPlaybackInstrument` — sends PROGRAM_CHANGE on channel 0 with clamped program number | unit | none | missing | Add unit test: mock `Receiver`, verify `ShortMessage.PROGRAM_CHANGE` with correct channel and data |
| `MidiController` | `isPlaying` — returns false when sequencer is null | unit | none | missing | Add unit test: null sequencer path |
| `MidiController` | `isPlaying` — delegates to `sequencer.isRunning()` when sequencer is non-null | unit | none | missing | Add unit test: mock sequencer |
| `MidiController` | `closeMidi` — idempotent: second call does not close resources again | unit | none | missing | Add unit test: call twice, verify `midiReceiver.close()` called exactly once |
| `MidiController` | `openMidi` / `openSynthesizerWithSoundbank` / `loadBundledSoundbank` / `extractSoundfontToTempFile` — full MIDI init path requires real MIDI hardware | none | — | none | Real hardware I/O; cannot be meaningfully mocked in unit or e2e context |
| `MidiController` | `initChannels` / `initChannel` / `reinitChannels` — GM reset + CC setup; all wired to real `Receiver` | none | — | none | Side-effect-only hardware output; no pure-logic testable path |
| `PlayThread` | `run` — when `playNoteOn=true` sends NOTE_ON, waits `NOTE_DURATION_MS`, sends NOTE_OFF | unit | none | missing | Add unit test: mock `MidiController.midiReceiver`, run thread, verify message sequence |
| `PlayThread` | `run` — when `playNoteOn=false` skips NOTE_ON but still sends NOTE_OFF after delay | unit | none | missing | Add unit test: same setup, verify only NOTE_OFF sent |
| `PlayThread` | `sendNoteOn` — no-op when `midiReceiver` is null | unit | none | missing | Add unit test: null receiver, no exception |
| `PlayThread` | `sendNoteOff` — no-op when `midiReceiver` is null | unit | none | missing | Add unit test: null receiver, no exception |
| `PlayThread` | `sendNoteOn` — sends bank-select + program-change + NOTE_ON messages with correct pitch and velocity | unit | none | missing | Add unit test: mock receiver, verify message types and values |
| `PlayThread` | `sendNoteOff` — sends NOTE_OFF with correct pitch | unit | none | missing | Add unit test: mock receiver, verify NOTE_OFF message |
| `PlayPauseAction` | `actionPerformed` — toggles action icon/name then calls `PlaybackController.togglePlayPause()` | unit | none | missing | Add unit test: verify both icon toggle and `togglePlayPause` called |
| `PlayPauseAction` | `playbackStateDidChange` (STOPPED) — calls `toggleToPlay` (sets play name/icon/tooltip) | unit | none | missing | Add unit test: set state to PAUSE name, post STOPPED notification, verify name reverts to PLAY_NAME |
| `PlayPauseAction` | `toggleAction` — when name is PLAY_NAME switches to pause labels; when pause name switches back | unit | none | missing | Add unit test: call toggleAction twice, verify round-trip |
| `PlayPauseAction` | `DISABLE_WHEN_PLAYING` flag not set — action stays enabled during playback (it is the pause button) | unit | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` | inadequate | Audit test only checks `DISABLE_WHEN_EDITING_TEXT`; no test verifies the action remains enabled during PLAYING state |
| `RewindAction` | `actionPerformed` — calls `PlaybackController.rewindToBeginning()` (thin dispatcher) | unit | none | missing | Add unit test: mock `PlaybackController`, verify `rewindToBeginning()` called |
| `LoopPlaybackAction` | `actionPerformed` — posts `ToggleLoopPlaybackCommand` with `isSelected()` value | unit | none | missing | Add unit test: mock `MessageCenter`, invoke action, verify command posted with correct payload |
| `PlayWithRepeatsAction` | `actionPerformed` — posts `TogglePlayWithRepeatsCommand` with `isSelected()` value | unit | none | missing | Add unit test: same pattern |
| `SequencerAction` | constructor delegation to `UIAction` | none | — | none | Pure super-call delegation with no own logic |
| `MidiMetaMessageTypes` | Constants hold correct MIDI spec hex values | none | — | none | Pure constants holder; no logic |

**Notes:**

The `PlaybackController` class is the highest-risk gap in the entire package. It is a static-method singleton implementing a multi-state transport machine (STOPPED/PAUSED/PLAYING) with six distinct state-transition paths (`togglePlayPause` alone has four branches) and a non-trivial meta-message callback that decodes packed binary data into line/note indices. Not one of these behaviors has a unit test. The four `selectionDidChange` tests that exist are the only coverage. Every state transition, every notification post, every highlight-coordination sequence, and the `applyPrefsDuringPlayback` spin-wait restart path are completely untested. Because all public methods are static and all dependencies (`MidiController.sequencer`, `registeredScore`) are settable via test-visible setters/statics, these are straightforward unit targets — no e2e or real hardware is required.

`MidiController.setPlaybackVolume` contains a concrete arithmetic formula (`Math.round(Math.clamp(percent, 50, 100) / 100f * 127)`) whose boundary behavior (50%→64, 100%→127) and clamping could silently regress. Similarly, `PlayThread.sendNoteOn`/`sendNoteOff` are static utility methods that can be unit-tested by injecting a mock `Receiver` into `MidiController.midiReceiver`. The action thin-dispatcher gap is present for all four `*Action` classes: `actionPerformed` on `PlayPauseAction`, `RewindAction`, `LoopPlaybackAction`, and `PlayWithRepeatsAction` each contain dispatch logic (icon toggle, command post, direct controller call) that is never exercised by any test.

The `LyricEditorActionAuditTest` (T25) provides coverage of `DISABLE_WHEN_EDITING_TEXT` for all four playback actions, which is a useful structural audit. `NoteDragHandlerTest` references `PlayThread` and `MidiController` only as mocked-out infrastructure to suppress side effects — no behavior of those classes is validated. The midi-package tests (`GlissandoMidiHelperTest`, `VelocityMapTest`, `GlissandoMidiIntegrationTest`) are well-structured and test adjacent MIDI logic adequately, but they do not touch any class in this package.

**Tally:** 49 rows — 4 adequate · 40 missing · 1 inadequate · 0 wrong-level · 4 none · 0 redundant.

**Dead code:** none found. All classes and public methods have verified callers in `src/main` or `src/test`.

**Production observations:** `PlaybackController.setSequenceToPlayFromSelection` uses identity comparison (`sequence != sequencer.getSequence()`) guarded by `//noinspection ObjectEquality` — this is correct for reference equality on `Sequence` objects but is easy to misread; worth a clarifying comment. `PlayThread` extends `Thread` directly rather than implementing `Runnable`; minor style issue but not a bug. The `setupInstrument()` method in `PlayThread` throws `RuntimeError.exit(...)` when `midiReceiver` is null, making it fatal in a path that `sendNoteOn` already guards with a null check and silent return — the guard in `sendNoteOn` makes the fatal path unreachable, but it is confusing.

### 11C — `ui/platform/mac` (Native macOS Menu Integration)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MacNativeMenuController` | Constructor subscribes to `MessageCenter` and stores strong reference so the MBassador weak-ref rule is satisfied | unit | none | missing | Add unit test: construct, post `DialogVisibilityDidChangeNotification`, verify `setEnabled` called on each managed item — use Mockito `@Mock NSMenuItem` injected via a constructor overload or reflective field set |
| `MacNativeMenuController` | `dialogVisibilityDidChange` sets all `managedItems` to `!notification.isVisible()` (enables on hide, disables on show) | unit | none | missing | Test with a small list of mock `NSMenuItem`s: post notification with `isVisible=true` → verify `setEnabled(false)`; `isVisible=false` → verify `setEnabled(true)` |
| `MacNativeMenuController` | `dialogVisibilityDidChange` iterates all managed items, not just the first | unit | none | missing | Post one notification with two mock items in the list; verify both receive `setEnabled` |
| `MacNativeMenuController` | `discoverNativeItems` returns empty list and logs warning when `appMenuItem.hasSubmenu()` is false | unit | none | missing | Mock the Rococoa chain (or factor discovery behind an interface) to return a top-level item with `hasSubmenu()=false`; assert result is empty |
| `MacNativeMenuController` | `discoverNativeItems` matches each `AppMenuAction` by `startsWith` prefix against item titles; unmatched actions produce a warning log | unit | none | missing | Provide mock `NSMenuItem`s with known titles, one matching and one not; verify matched item is in result, unmatched triggers LOG.warn |
| `MacNativeMenuController` | `discoverNativeItems` wraps the whole native call sequence in a broad `try/catch(Exception)`; any exception returns empty list | unit | none | missing | Have `NSApplication.sharedApplication()` throw a `RuntimeException`; assert no exception propagates and the returned list is empty |
| `MacNativeMenuController` | `discoverNativeItems` calls `setAutoenablesItems(false)` on the app menu before iterating items | unit | none | missing | Verify this side-effect on the mock `NSMenu` |
| `MacNativeMenuController` | `Actions.getAppMenuActions()` result is fetched correctly (correct count, correct native titles) | unit | `ActionsAppMenuTest.testGetAppMenuActionsReturnsExpectedActions`, `testAppMenuActionsHaveCorrectNativeTitles` | adequate | — (already covered at the right level in `ActionsAppMenuTest`; not a `MacNativeMenuController` behavior per se, but the dependency is tested) |
| `NSApplication` | `sharedApplication()` delegates to `CLASS.sharedApplication()` (pure Rococoa pass-through) | none | none | none | Native JNI/Rococoa bridge — no assertable logic on our side without a live macOS runtime |
| `NSApplication` | `mainMenu()` abstract method (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenu` | `numberOfItems()`, `itemAtIndex()`, `setAutoenablesItems()` abstract methods (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenu` | `title()` abstract method | none | none | none | Pure native pass-through — `title()` has no callers in production code (see Dead code) |
| `NSMenu` | `itemWithTitle()` abstract method | none | none | none | Pure native pass-through — no callers in production code (see Dead code) |
| `NSMenuItem` | `title()`, `hasSubmenu()`, `submenu()`, `setEnabled()` abstract methods (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenuItem` | `isEnabled()` abstract method | none | none | none | Pure native pass-through — `isEnabled()` has no callers in production code (see Dead code) |

**Notes:**

`NSApplication`, `NSMenu`, and `NSMenuItem` are thin Rococoa abstract-class wrappers. Every method on them is `abstract` and is dispatched directly to the native Objective-C runtime via the Rococoa/JNA bridge — there is no Java-side logic, no branching, and no transformation. These are correctly classified `none`: they cannot be unit-asserted without a live macOS runtime, and testing that Rococoa calls the right native method would be testing the Rococoa framework, not our code.

All testable behavior lives in `MacNativeMenuController`. The two most important gaps are (1) the `dialogVisibilityDidChange` handler — this is the entire runtime purpose of the controller and has zero test coverage — and (2) the `discoverNativeItems` discovery logic, which contains several distinct branches (no-submenu guard, prefix-`startsWith` matching, exception swallowing) that can all be exercised by mocking the NS* interfaces. The `MacNativeMenuController` constructor takes no parameters today, making injection of mock NS* objects awkward; the recommended approach is either a package-private constructor that accepts a pre-built `List<NSMenuItem>` for testing, or extracting the NS* chain calls behind a narrow functional interface. The OS-conditional path in `MenuController.initMenus` (wraps construction in `if (SystemInfo.isMacOS)`) is in a different class and out of scope here; note it also swallows the `Throwable` silently in headless mode, which means test runs on non-macOS CI will never exercise the controller at all — reinforcing the need for injectable mocking.

`BaseDialogCounterTest` exercises `DialogVisibilityDidChangeNotification` dispatch thoroughly at the sender side (verifying the message is posted on first-open and last-close). That is the upstream dependency of `dialogVisibilityDidChange`; what is missing is the handler side — verifying that the controller correctly reacts to those notifications by enabling/disabling the managed native items.

**Tally:** 15 rows — 1 adequate · 7 missing · 0 inadequate · 0 wrong-level · 7 none · 0 redundant.

**Dead code:**
- `NSMenu._Class.alloc()` — declared but never called anywhere in `src/main` or `src/test`. Likely copied from a template; the factory pattern is unused because `NSMenu` instances are obtained only via `NSApplication.mainMenu()` and `NSMenuItem.submenu()`.
- `NSMenuItem._Class.alloc()` — same situation; never called.
- `NSMenu.CLASS` field — never read (the `NSMenu._Class` factory is never invoked, so the Rococoa-registered class object is unused).
- `NSMenuItem.CLASS` field — same.
- `NSMenu.title()` — no callers in `src/main` or `src/test`.
- `NSMenu.itemWithTitle(String)` — no callers in `src/main` or `src/test`.
- `NSMenuItem.isEnabled()` — no callers in `src/main` or `src/test`.

Note: these symbols are in an OS-conditional package, but the dead-code determination is based on verified reference searches across all of `src/main` and `src/test`, not just conditional paths. There is no reflective usage of these specific methods found.

**Production observations:**
- `NSMenu.CLASS` is annotated `@SuppressWarnings("unused")` and `NSMenuItem.CLASS` likewise, indicating the authors are aware these fields have no callers — but the `_Class.alloc()` methods and the `title()`/`itemWithTitle()`/`isEnabled()` methods have no such suppression, suggesting they were included speculatively for future use.
- `MacNativeMenuController` is not a singleton per the project's `singletons.md` pattern (no `private static final INSTANCE`). It is instead held as a `@Nullable private static` field on `MenuController` with a `@SuppressWarnings({"FieldCanBeLocal", "unused"})` annotation to prevent GC — this is a deliberate strong-reference anchor. The pattern deviates from the singleton guide but is intentional (the field only exists to prevent the MBassador weak-reference from being collected).

### 11D — `MusicEditOperations` (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MusicEditOperations` | `canToggleBeaming()` delegates to `state.canToggleBeaming()`; returns false when state is null | unit | none | missing | Add test: null state → false; non-null state delegates to `LineSelectionState` (the delegation itself is a one-liner but the null branch is dark) |
| `MusicEditOperations` | `toggleBeaming()` — null state guard (early return, no mutation) | unit | none | missing | Add test: invoking with no active selection emits no notification |
| `MusicEditOperations` | `toggleBeaming()` — add beam (no existing beam or split across different beams) | unit | `MusicEditOperationsMutationTest.testToggleBeamingAddEmitsBeamingAddition`, `BeamToggleTest.ToggleBeam.testToggleBeamOn` | adequate | None |
| `MusicEditOperations` | `toggleBeaming()` — remove beam (begin and end in same beam group) | unit | `MusicEditOperationsMutationTest.testToggleBeamingRemoveEmitsBeamingRemoval`, `BeamToggleTest.ToggleBeam.testToggleBeamOff` | adequate | None |
| `MusicEditOperations` | `canToggleTie()` delegates to `state.canToggleTie()`; returns false when state is null | unit | none | missing | Add test: null state → false |
| `MusicEditOperations` | `toggleTie()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `toggleTie()` — add tie (no existing tie) | unit | `MusicEditOperationsMutationTest.testToggleTieAddEmitsTieAddition`, `TieToggleTest.testTieCreationAndRemoval` | adequate | None |
| `MusicEditOperations` | `toggleTie()` — remove tie (existing tie found) | unit | `MusicEditOperationsMutationTest.testToggleTieRemoveEmitsTieRemoval`, `TieToggleTest.testTieCreationAndRemoval` | adequate | None |
| `MusicEditOperations` | `canToggleTuplet()` — returns default `TupletToggleInfo(false, null, false)` when state is null | unit | none | missing | Add test: null state returns false-info |
| `MusicEditOperations` | `toggleTuplet()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `toggleTuplet()` — guard: `info.canToggle() == false` throws `IllegalStateException` | unit | none | missing | Add test: throws with appropriate message |
| `MusicEditOperations` | `toggleTuplet(size=0, info)` — remove tuplet (existing tuplet, size==0 path) | unit | `MusicEditOperationsMutationTest.testToggleTupletRemoveEmitsTupletRemoval` (uses matching-grade, not size=0) | inadequate | Tests the same net effect via grade-match but does NOT exercise the `tupletSize == 0` branch. Add `toggleTuplet(REMOVE.getSize(), info)` test. |
| `MusicEditOperations` | `toggleTuplet(size=0, info)` — size=0 with no existing tuplet throws `IllegalStateException` | unit | none | missing | Add test: `toggleTuplet(0, infoWithNoExisting)` throws |
| `MusicEditOperations` | `toggleTuplet(size>0, null existing)` — add new tuplet | unit | `MusicEditOperationsMutationTest.testToggleTupletAddEmitsTupletAddition` | adequate | None |
| `MusicEditOperations` | `toggleTuplet(size>0, full-coverage same grade)` — remove only | unit | `MusicEditOperationsMutationTest.testToggleTupletMatchingGradeRemovesOnly` | adequate | None |
| `MusicEditOperations` | `toggleTuplet(size>0, full-coverage different grade)` — remove then add | unit | `MusicEditOperationsMutationTest.testToggleTupletGradeChangeEmitsRemovalThenAddition` | adequate | None |
| `MusicEditOperations` | `toggleTuplet()` — sub-range of existing tuplet throws `IllegalStateException` | unit | `MusicEditOperationsMutationTest.testToggleTupletPartialCoverageInExistingTupletThrows` | adequate | None |
| `MusicEditOperations` | `addDynamicsToSelection(true)` — adds crescendo | unit | `MusicEditOperationsMutationTest.testAddDynamicsEmitsOneAddition` (parameterized), `ScoreViewControllerCommandHandlerTest.testHandleAddDynamicsEmitsOneAddition` | adequate | None |
| `MusicEditOperations` | `addDynamicsToSelection(false)` — adds diminuendo | unit | `MusicEditOperationsMutationTest.testAddDynamicsEmitsOneAddition` (parameterized) | adequate | None |
| `MusicEditOperations` | `addDynamicsToSelection()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns false when state null or no element selection | unit | none | missing | Add test for both null-state and hasElementSelection=false paths |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns false when selection has no dynamics | unit | none | missing | Add test: selection with only notes returns false |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns true when crescendo overlaps selection | unit | none | missing | Add test |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns true when diminuendo overlaps selection | unit | none | missing | Add test |
| `MusicEditOperations` | `removeDynamicsFromSelection()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `removeDynamicsFromSelection()` — removes all overlapping crescendos and diminuendos | unit | `MusicEditOperationsMutationTest.testRemoveDynamicsEmitsRemovalPerSpan` | inadequate | Test asserts `isNotEmpty()` instead of exact counts; `ScoreViewControllerCommandHandlerTest.testHandleRemoveDynamicsEmitsRemovals` also uses `isNotEmpty()`. Neither pins the exact number of removals emitted. |
| `MusicEditOperations` | `getDynamicsFromSelection()` — partial overlap: span starting before selection and ending inside | unit | none | missing | Add test: hairpin whose anchor is before selectionBegin but end is within range is included |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — null/no-element-selection returns invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — auto-maintained terminal extension | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingAtSongEnd.testSelectionEndingBeforeAutoMaintainedTerminalIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: insufficient content (< MIN_CONTENT_ELEMENTS) returns invalid | unit | none | missing | Add test: selection with fewer than 4 content elements |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: multiple right-repeats within selection returns invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: no right-repeat found returns invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: first ending region has barline/repeat → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: first ending region empty → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: second ending region has barline/repeat → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, REPEAT_RIGHT terminal valid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testRepeatRightTerminalIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, REPEAT_LEFT_RIGHT terminal valid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testRepeatLeftRightTerminalIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, SINGLE_BARLINE terminal invalid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testSingleBarlineTerminalIsInvalid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, REPEAT_LEFT terminal invalid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testRepeatLeftTerminalIsInvalid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: SINGLE_BARLINE as leading element adjusts firstEndingStart | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingWithExistingLeadingBarlineEmitsOnlyRangeElementAddition` (exercises NONE path, line has SINGLE_BARLINE at start) | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasOverlap: existing ending in selection range returns invalid | unit | none | missing | Add test: selection overlapping an existing `Ending` span returns false |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: double barline blocks scan | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testDoubleBarlineBlocksScan` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: FINAL_DOUBLE_BARLINE blocks scan | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testFinalDoubleBarlineBlocksScan` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: first-line song-start is valid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testFirstLineNoRepeatIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: non-first line, no repeat found → invalid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testNonFirstLineNoRepeatIsInvalid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: REPEAT_LEFT on previous line → valid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testRepeatLeftOnPreviousLineIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: beginning of song (no preceding element) → NONE, valid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testFirstLineNoRepeatIsValid` (exercises song-start path indirectly) | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is content element, selection starts with SINGLE_BARLINE or REPEAT_LEFT → NONE | unit | none | missing | Add test: preceding content + selection starts with barline/repeat → NONE action |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is content element, selection starts with note → INSERT_BARLINE | unit | none (only tested via `makeFirstSecondEnding`) | inadequate | `canMakeFirstSecondEnding` predicate is never directly asserted to return `PrecedingAction.INSERT_BARLINE`; tests only pass a pre-built result to `makeFirstSecondEnding` |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is SINGLE_BARLINE or REPEAT_LEFT → EXTEND_SPAN | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is right-repeat/double-barline/final → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `makeFirstSecondEnding()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `makeFirstSecondEnding()` — INSERT_BARLINE: inserts barline, adjusts span bounds, adds Ending | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingEmitsElementInsertionAndRangeElementAddition` | adequate | None |
| `MusicEditOperations` | `makeFirstSecondEnding()` — NONE: no barline inserted, adds Ending directly | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingWithExistingLeadingBarlineEmitsOnlyRangeElementAddition` | adequate | None |
| `MusicEditOperations` | `makeFirstSecondEnding()` — EXTEND_SPAN: span already extended (no insertion), adds Ending | unit | none | missing | Add test: verify only one `RangeElementAddition` emitted with correct span bounds when action is `EXTEND_SPAN` |
| `MusicEditOperations` | `canToggleTrill()` delegates to `state.canToggleTrill()`; returns false when state null | unit | none | missing | Add test: null state → false |
| `MusicEditOperations` | `toggleTrill()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `toggleTrill()` — no existing trills → add single trill | unit | `MusicEditOperationsMutationTest.testToggleTrillEmitsRangeElementAddition` | adequate | None |
| `MusicEditOperations` | `toggleTrill()` — one overlapping trill exists → remove it | unit | `MusicEditOperationsMutationTest.testToggleTrillOffResultsInNoTrill` | adequate | None |
| `MusicEditOperations` | `toggleTrill()` — multiple overlapping trills exist → remove all in one bracket | unit | none | missing | Add test: two trills whose spans overlap the selection are both removed in a single notification |
| `MusicEditOperations` | `canFlipStemDirection()` delegates to `state.canFlipStemDirection()`; returns false when state null | unit | none | missing | Add test: null state → false |
| `MusicEditOperations` | `flipStemDirection()` — null state guard: shows info dialog | unit | none | missing | Add test: null selection shows `OptionDialogs.showInfoMessage` and emits no mutation |
| `MusicEditOperations` | `flipStemDirection()` — rest elements in selection are skipped (no mutation emitted for rests) | unit | none | missing | Add test: selection containing a rest; verify rest emits no `ElementModification`, only note does |
| `MusicEditOperations` | `flipStemDirection()` — unbeamed notes: flips each individually | unit | `MusicEditOperationsMutationTest.testFlipStemDirectionEmitsElementModificationPerAffectedIndex`, `BeamToggleTest.FlipStemDirection.testFlipStemUnbeamedWithPersistence` | adequate | None |
| `MusicEditOperations` | `flipStemDirection()` — beamed notes: flips whole beam group together (single pass per group) | unit | `BeamToggleTest.FlipStemDirection.testFlipStemWhileBeamedChangesDirection` | adequate | None |
| `MusicEditOperations` | `flipStemDirection()` — deduplication: beam group partially inside selection flipped only once | unit | none | missing | Add test: selection spanning only part of a beam group; group flipped once, not per-selected-note |
| `MusicEditOperations` | `flipStemDirection()` — tie partners outside selection are also flipped | unit | none | missing | Add test: note with a tie whose partner is outside the selection; partner's stem is also flipped |
| `MusicEditOperations` | `canChangeTempo()` delegates to `coordinator.canChangeTempo()` | unit | none | missing | Add test: verifies delegation; trivial but needed to guard the null state |
| `MusicEditOperations` | `setSong()` — replaces the song field (allows reuse across document loads) | none | `ScoreViewSetFontsTest` indirectly via `ScoreView.setSong()` (production path, not a direct test) | none | Trivial setter; no behavioral logic |

**Notes:**

The existing `MusicEditOperationsMutationTest` is the strongest part of the suite: it covers all five `toggleTuplet` branches correctly, the two beaming branches, both tie branches, both dynamics-add branches, and the main validation paths for `canMakeFirstSecondEnding`. The mutation record fields are asserted precisely (anchor/end index, grade, line identity), so these are genuinely adequate tests, not just type-checks.

The highest-risk gaps cluster in two areas. First, every operation's null-state guard is untested — all six operations silently return or show a dialog when `state == null`, but no test ever exercises this. The `flipStemDirection` null path is especially risky because it invokes `OptionDialogs.showInfoMessage`, a side effect that is invisible if the test never runs. Second, the `canMakeFirstSecondEnding` predicate's four `checkPrecedingElement` branches — EXTEND_SPAN, NONE with barline at start, INSERT_BARLINE (directly asserted), and invalid preceding element — are dark: the test suite only passes pre-built `EndingValidationResult` objects into `makeFirstSecondEnding`, so the predicate's logic is tested only in the `HasEnclosingRepeatRules` / `CanMakeFirstSecondEndingWithRepeatLeftRightSplit` groups, not for all `checkPrecedingElement` outcomes. The `makeFirstSecondEnding` EXTEND_SPAN branch is also completely untested.

Three tests are marked inadequate for weak assertions: `testRemoveDynamicsEmitsRemovalPerSpan` uses `isNotEmpty()` when it should assert exact counts (one crescendo removal, one diminuendo removal); `testHandleRemoveDynamicsEmitsRemovals` in `ScoreViewControllerCommandHandlerTest` also only checks `isNotEmpty()`; and the INSERT_BARLINE path of `checkPrecedingElement` is never directly asserted — the `canMakeFirstSecondEnding()` return value is not examined in those tests, only `makeFirstSecondEnding()` is called with a hand-crafted result.

**Tally:** 69 rows — 28 adequate · 37 missing · 3 inadequate · 0 wrong-level · 1 none · 0 redundant. (Null-state guards are classified `unit`/`missing` in the table — each is a real guard branch whose removal would NPE — not `none`.)

**Dead code:** none found. All private helpers (`validateEndingStructure`, `validateEndingRegionContent`, `hasOverlap`, `hasEnclosingRepeat`, `checkPrecedingElement`, `getDynamicsFromSelection`) are referenced from `canMakeFirstSecondEnding` and `removeDynamicsFromSelection`/`canRemoveDynamicsFromSelection` respectively. All public methods are referenced from `ScoreViewController` or tests.

**Production observations:** `flipStemDirection()` shows a dialog (`OptionDialogs.showInfoMessage`) when `state == null`, while every other operation silently returns. This inconsistency suggests either the dialog branch is dead in practice (the UI gate should prevent invoking the method with no selection) or the dialog is intentional UX feedback, but the asymmetry with all sibling operations is a smell worth reviewing.

### 11E — Appearance & Dialog Helpers (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `OptionDialogs` | `showInfoMessage` — pure pass-through to `showMessageDialog`; no return value, no branching | none | `DialogsTest.testShowInfoMessageDelegatesToJOptionPane` | none | no action |
| `OptionDialogs` | `showWarningMessage` — pure pass-through to `showMessageDialog`; no return value, no branching | none | none | none | no action |
| `OptionDialogs` | `showErrorMessage` — pass-through; beep before display (error path) | none | `DialogsTest.testShowErrorMessageDelegatesToJOptionPane` | none | no action |
| `OptionDialogs` | `showErrorMessageWithString` — pure pass-through; no return mapping | none | none (covered by delegation from `showErrorMessage`) | none | no action |
| `OptionDialogs` | `showConfirmDialog` — maps `CLOSED_OPTION` to `CANCEL_OPTION` when `optionType == YES_NO_CANCEL_OPTION` | unit | `DialogsTest.testShowConfirmDialogTranslatesClosedOptionToCancelForYesNoCancelOption` (unit + e2e `DialogsTest.testCloseWithYesNoCancelOptionReturnsCancelOption`) | adequate | — |
| `OptionDialogs` | `showConfirmDialog` — maps `CLOSED_OPTION` to `NO_OPTION` when `optionType == YES_NO_OPTION` | unit | `DialogsTest.testShowConfirmDialogTranslatesClosedOptionToNoForYesNoOption` (unit + e2e `DialogsTest.testCloseWithYesNoOptionReturnsNoOption`) | adequate | — |
| `OptionDialogs` | `showConfirmDialog` (5-arg) — suppressed default is `NO_OPTION` | unit | `DialogsTest.testShowConfirmDialogReturnsNoOptionByDefault` | adequate | — |
| `OptionDialogs` | `showConfirmDialog` (6-arg) — suppressed default is caller-supplied value | unit | `DialogsTest.testShowConfirmDialogReturnsSuppressedDefault` | adequate | — |
| `OptionDialogs` | `showInputDialog` — returns user-typed string | unit | `DialogsTest.testShowInputDialogReturnsUserInput` (unit + e2e `DialogsTest.testReturnsTypedText`) | adequate | — |
| `OptionDialogs` | `showInputDialog` — `UNINITIALIZED_VALUE` maps to `null` (cancel) | unit | `DialogsTest.testShowInputDialogReturnsCancelAsNull` | adequate | — |
| `OptionDialogs` | `showInputDialog` (3-arg) — suppressed default is `null` | unit | `DialogsTest.testShowInputDialogReturnsNullByDefault` | adequate | — |
| `OptionDialogs` | `showInputDialog` (4-arg) — suppressed default is caller-supplied string | unit | `DialogsTest.testShowInputDialogReturnsSuppressedDefault` | adequate | — |
| `OptionDialogs` | `showOptionDialog` — `getOptionPaneResult` with options array: returns array index of clicked option, `CLOSED_OPTION` when no match | unit | `DialogsTest` (WhenSuppressed only asserts `CLOSED_OPTION` on suppressed path; e2e `DialogsTest.testReturnsIndexOfClickedOption` covers the live index-mapping path) | adequate | — |
| `OptionDialogs` | `showOptionDialog` — suppressed returns `CLOSED_OPTION` | unit | `DialogsTest.testShowOptionDialogReturnsClosedOption` — passes raw strings `"Title"`/`"Message"` instead of `Strings.*` keys; harmless since suppressed path returns before `Strings.get()`, but violates project convention | inadequate | Replace `"Title"` / `"Message"` with valid `Strings.*` key constants |
| `OptionDialogs` | Suppression side-effect: `show*Message` does not construct `JOptionPane` when suppressed | unit | `DialogsTest.testShowErrorMessageDoesNotShowDialog`, `testShowInfoMessageDoesNotShowDialog` | adequate | — |
| `EndingConfirms` | `confirmInvalidation` — returns `true` (proceed) when user clicks Yes (index 0), `false` when dialog returns anything else | unit | `EndingConfirmsTest` — covered indirectly through full `SelectionCoordinator`/`ScoreViewController` integration; `simulateYes()` stubs `showOptionDialog` returning 0, default (no stub) returns 0 from suppressed `CLOSED_OPTION` which ≠ 0, so false branch covered | adequate | — |
| `EndingConfirms` | `confirmCompensateEnd` — selects `CONFIRM_ENDING_SPLIT_RIGHT_TO_LEFT_RIGHT` key when `newEndType == REPEAT_RIGHT`, otherwise `CONFIRM_ENDING_SPLIT_LEFT_RIGHT_TO_RIGHT` | unit | `EndingConfirmsTest` exercises both branches indirectly (primary line → REPEAT_RIGHT path; secondary line → other path) | adequate | — |
| `EndingConfirms` | `confirmCompensateSplit` — selects `CONFIRM_ENDING_END_TO_REPEAT_REQUIRES_LEFT_RIGHT_SPLIT` key when `newSplitType == REPEAT_LEFT_RIGHT`, otherwise `CONFIRM_ENDING_END_TO_BARLINE_REQUIRES_RIGHT_SPLIT` | unit | `EndingConfirmsTest` exercises both branches indirectly | adequate | — |
| `EndingConfirms` | `applyCompensatingEndChange` / `applyCompensatingSplitChange` — applies element substitution; null guard on `targetEl` skips silently | unit | `EndingConfirmsTest` exercises happy path; null `targetEl` guard is never directly tested | missing | Add a unit test for the `targetEl == null` early-return in `applyCompensatingChange` |
| `EndingConfirms` | `typeNameFor` — maps `ElementType` to display string key; four branches (`REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT`, `REPEAT_LEFT`, default=barline) | unit | Exercised implicitly via `confirmCompensateSplit` during `EndingConfirmsTest`, but which branch fires depends on fixture — `REPEAT_LEFT` branch may not be reachable via current fixtures | missing | Add a direct unit test for `typeNameFor` covering all four `ElementType` branches |
| `AppearanceManager` | `createLaf(true)` — returns `FlatMacDarkLaf` on macOS, `FlatDarkLaf` elsewhere | unit | `AppearanceManagerTest.CreateLaf.testDarkReturnsCorrectLafClass` | adequate | — |
| `AppearanceManager` | `createLaf(false)` — returns `FlatMacLightLaf` on macOS, `FlatLightLaf` elsewhere | unit | `AppearanceManagerTest.CreateLaf.testLightReturnsCorrectLafClass` | adequate | — |
| `AppearanceManager` | `resolveIsDark(DARK)` → `true` | unit | `AppearanceManagerTest.ResolveIsDark.testDarkPreferenceReturnsTrue` | adequate | — |
| `AppearanceManager` | `resolveIsDark(LIGHT)` → `false` | unit | `AppearanceManagerTest.ResolveIsDark.testLightPreferenceReturnsFalse` | adequate | — |
| `AppearanceManager` | `resolveIsDark(SYSTEM)` — delegates to `OsThemeDetector.isDark()` | unit | `AppearanceManagerTest.ResolveIsDark.testSystemPreferenceDelegatesToOsDetector` | adequate | — |
| `AppearanceManager` | `resolveIsDark(SYSTEM)` — falls back to `false` when detector throws | unit | `AppearanceManagerTest.ResolveIsDark.testSystemPreferenceFallsBackToLightOnDetectorFailure` | adequate | — |
| `AppearanceManager` | `init` — installs LAF from preference and registers OS listener when `SYSTEM` | unit | `AppearanceManagerTest.Init.*` (three tests) | adequate | — |
| `AppearanceManager` | `init` — throws `IllegalStateException` when `installLaf` fails | unit | none | missing | Add test: stub `installLaf` to throw `UnsupportedLookAndFeelException`; assert `init()` throws `IllegalStateException` |
| `AppearanceManager` | `switchTheme` — no-op when new preference equals current | unit | `AppearanceManagerTest.SwitchTheme.testNoOpWhenPreferenceUnchanged` | adequate | — |
| `AppearanceManager` | `switchTheme` — calls LAF ops in order: `showSnapshot → installLaf → updateUI → hideSnapshotWithAnimation` | unit | `AppearanceManagerTest.SwitchTheme.testSwitchCallsLafOpsInOrder` | adequate | — |
| `AppearanceManager` | `switchTheme` — saves new preference before attempting the switch | unit | `AppearanceManagerTest.SwitchTheme.testSwitchSavesNewPreference` | adequate | — |
| `AppearanceManager` | `switchTheme` — reverts preference when `installLaf` throws | unit | none | missing | Add test: stub `installLaf` to throw; assert that `Prefs.put` is called a second time with the *old* preference key to revert |
| `AppearanceManager` | `switchTheme` — registers OS listener when switching to `SYSTEM` | unit | `AppearanceManagerTest.SwitchTheme.testSwitchToSystemRegistersOsListener` | adequate | — |
| `AppearanceManager` | `switchTheme` — unregisters OS listener when switching away from `SYSTEM` | unit | `AppearanceManagerTest.SwitchTheme.testSwitchFromSystemUnregistersOsListener` | adequate | — |
| `AppearanceManager` | `registerOsListener` — guard against double-registration (`listenerRegistered` flag) | unit | none | missing | Add test: call `switchTheme(SYSTEM)` twice; verify `registerListener` is called only once |
| `AppearanceManager` | `getPreference` — reads `PrefsKey.APPEARANCE` and delegates to `Appearance.fromKey` | unit | covered implicitly by all `SwitchTheme` tests that stub `Prefs.getString` | adequate | — |
| `Appearance` | `fromKey` — returns matching enum constant for each valid key | unit | none (only used indirectly inside `AppearanceManagerTest` via `AppearanceManager.getPreference`) | missing | Add a focused unit test for `Appearance.fromKey` covering all three valid keys and the unknown-key fallback to `SYSTEM` |
| `Appearance` | `fromKey` — unknown key falls back to `SYSTEM` | unit | none | missing | (same test as above — covered as one row) |
| `Appearance` | `key()` — returns the string key for each enum constant | none | — | none | Pure data accessor |
| `LafOperations` | Interface definition — no logic | none | — | none | Package-private interface; only behavior is in `DefaultLafOperations` inside `AppearanceManager`, exercised through `AppearanceManagerTest` via mock injection |

**Notes:**

The highest-risk gap is the `switchTheme` preference-revert path: when `installLaf` throws, the production code calls `Prefs.put(currentPreference.key())` to roll back the optimistic write, but no test verifies this. A silent regression here would leave the pref file permanently out of sync with the actually-installed LAF. The missing `registerOsListener` double-registration guard test is lower risk but still behaviorally important — repeated calls to `switchTheme(SYSTEM)` would silently register multiple listeners without it. The `init` failure path (throws `IllegalStateException`) is also untested; callers that expect a well-defined error contract would be surprised if the message changed.

`AppearanceManagerTest` is largely adequate for the happy path and covers the LAF-ops ordering, OS listener registration/unregistration, and `resolveIsDark` fallback. Its main weakness is that it never exercises the error-recovery branches (installLaf failure in both `init` and `switchTheme`). The missing `Appearance.fromKey` test is minor but easy to add; the enum is only a few lines and the fallback-to-`SYSTEM` behavior is load-bearing for the prefs system.

`OptionDialogs` methods that are pure `void` pass-throughs (`showInfoMessage`, `showWarningMessage`) carry no return-value mapping and correctly receive `none` verdicts. All the `showConfirmDialog` and `showInputDialog` return-mapping behaviors are well covered at both unit and e2e levels. The one inadequacy is `testShowOptionDialogReturnsClosedOption`, which passes raw string literals `"Title"`/`"Message"` instead of `Strings.*` keys — the test passes only because suppression fires before `Strings.get()` is reached, masking the convention violation. `EndingConfirms` is adequately covered at the integration level by `EndingConfirmsTest`; the missing items are isolated unit-level gaps (`typeNameFor` branches, `targetEl == null` null-guard) rather than whole-behavior holes.

**Tally:** 40 rows — 26 adequate · 7 missing · 1 inadequate · 0 wrong-level · 6 none · 0 redundant.

**Dead code:** none found. `LafOperations` is package-private and used by `AppearanceManager` and `AppearanceManagerTest`; all methods in all five classes have callers in `src/main` or `src/test`.

**Production observations:** In `DialogsTest.WhenSuppressed.testShowOptionDialogReturnsClosedOption`, the test passes raw string literals `"Title"` and `"Message"` as `titleKey`/`messageKey` arguments to `showOptionDialog`, which expects `Strings.*` key constants. The test works because the suppressed path returns before `Strings.get()` is called, but the convention violation is a latent correctness risk if suppression is ever removed or refactored. This is the only instance of the anti-pattern in the test suite.

### 11F — Display & Constants (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `KeySignatureDisplay` | `tonicFor`: returns correct tonic string for each SHARPS key (0–7) | unit | none | missing | Test all 8 SHARPS entries against `SHARP_TONICS` table |
| `KeySignatureDisplay` | `tonicFor`: returns correct tonic string for each FLATS key (0–7) | unit | none | missing | Test all 8 FLATS entries against `FLAT_TONICS` table |
| `KeySignatureDisplay` | `suffixFor`: returns empty string when `KeyType.NONE` or count == 0 | unit | none | missing | Verify both `NONE`-type and zero-count paths return `""` |
| `KeySignatureDisplay` | `suffixFor`: returns non-empty suffix containing count for SHARPS | unit | none | missing | Check suffix for SHARPS count > 0 contains the count and right plural form |
| `KeySignatureDisplay` | `suffixFor`: returns non-empty suffix containing count for FLATS | unit | none | missing | Check suffix for FLATS count > 0 contains the count and right plural form |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for FLATS count < 2, true for count >= 2 | unit | none | missing | Boundary at `MIN_FLAT_COUNT_WITH_ACCIDENTAL` = 2 |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for SHARPS count < 6, true for count >= 6 | unit | none | missing | Boundary at `MIN_SHARP_COUNT_WITH_ACCIDENTAL` = 6 |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for `KeyType.NONE` regardless of count | unit | none | missing | NONE branch must return false even with a nonzero count |
| `KeySignatureDisplay` | `getDisplayName` with count == 0 / NONE type: returns `AttributedString` over empty string | unit | none | missing | Empty-string guard path (lines 61–63) |
| `KeySignatureDisplay` | `getDisplayName` with a key that has NO tonic accidental: applies single label font only | unit | none | missing | E.g. SHARPS/1 (G major) — no secondary font attribute ranges |
| `KeySignatureDisplay` | `getDisplayName` with a key that HAS a tonic accidental: applies letter-gap tracking + glyph font at correct indices | unit | none | missing | E.g. FLATS/3 (E♭ major) — verify font attribute ranges on the correct character positions |
| `Constants` | All fields are pure compile-time string/value constants (no logic) | none | none | none | Pure constants holder — no testable behavior |
| `Control` | `MOUSE.getDescription()` returns the string for `ACTION_CONTROL_MOUSE` | unit | none | missing | Needs `installFlatLafDefaults`; assert description is non-blank and matches Strings key |
| `Control` | `KEYBOARD.getDescription()` returns the string for `ACTION_CONTROL_KEYBOARD` | unit | none | missing | Parallel to MOUSE case |
| `Mode` | `isAdjustmentMode()` returns true for `ADJUSTMENT` and `VERTICAL_ADJUSTMENT` | unit | none | missing | Both adjustment variants must satisfy predicate |
| `Mode` | `isAdjustmentMode()` returns false for `SELECT` and `EDIT` | unit | none | missing | Non-adjustment variants must not satisfy predicate |
| `FlatLafProps` | `get`: throws `RuntimeError` when key is absent from UIManager | unit | none | missing | Set up a mock UIManager or install FlatLaf without the key; assert exit is called |
| `FlatLafProps` | `get`: returns typed value when key is present | unit | none | missing | Install a known property; assert returned value equals expected with correct type |

**Notes:**

`KeySignatureDisplay` is the highest-risk gap. It contains two parallel lookup tables (`FLAT_TONICS`, `SHARP_TONICS`), two threshold constants (`MIN_FLAT_COUNT_WITH_ACCIDENTAL` = 2, `MIN_SHARP_COUNT_WITH_ACCIDENTAL` = 6), and `AttributedString` font-attribute range logic — all pure computation with zero test coverage. An off-by-one in either threshold or a wrong glyph index in the accidental-font assignment would be invisible until the key-signature picker renders incorrectly on screen. `tonicFor` and `tonicHasAccidental` are `private` static methods, but they are fully exercisable through the public `getDisplayName` method — the private helpers are the real test targets, accessed indirectly. The `getDisplayName` tests that inspect `AttributedString` attribute ranges will need `installFlatLafDefaults()` (from `UnitTest`) because the method calls `MyFontUtils.getUIFont("Label.font")` and `RenderingUtils.getMusicFont()`.

`Mode.isAdjustmentMode()` is used in at least four production call sites across `LineComponent`, `ModeCycleButton`, `UIAction`, and `CycleModeAction`, yet has no direct unit test. The logic is a two-constant OR (`this == ADJUSTMENT || this == VERTICAL_ADJUSTMENT`) and is trivially testable; omitting a test means the method could silently be broken by an enum refactor that renames or adds values. `Control.getDescription()` likewise dispatches a `switch` over two constants to `Strings.get()`; a straightforward two-case test suffices.

`Constants` is a pure string-constants holder (`none`). `FlatLafProps` contains a single method with real logic — a null guard and a typed unchecked cast — which warrants two unit tests. The class is referenced across 66 production call sites, so silent misbehavior (wrong null-check path, or a cast exception from a wrong witness) would be broadly impactful. The missing-key throw path in particular is untested. `FlatLafProps` is not a constants holder in the rubric sense: it has a method body with branching, so `none` would be wrong.

**Tally:** 18 rows — 0 adequate · 17 missing · 0 inadequate · 0 wrong-level · 1 none · 0 redundant.

**Dead code:** `Constants.ACCELERATOR_KEYS` and `Constants.SONG_SCRIBE_JAR` have zero references outside their own definition file in both `src/main` and `src/test`.

**Production observations:** `Constants.NON_BREAKING_HYPHEN` is assigned `Character.toString('­')`, which is U+00AD SOFT HYPHEN — a zero-width formatting character that browsers and many renderers treat as invisible. The true NON-BREAKING HYPHEN is U+2011. This naming/value mismatch may cause ABC export (`ExportABCAction`) to silently fail to replace what it believes are non-breaking hyphens in lyric syllables, since any lyrics actually containing U+2011 would not match the constant. Whether lyrics in practice ever contain U+00AD vs U+2011 determines the real-world impact.

### §11 summary

**228 behavior rows: 194 testable / 34 none; of 194 testable, 63 adequate · 126 missing · 5 inadequate · 0 wrong-level · 0 redundant (~68% dark).** Two well-covered islands sit in an otherwise dark periphery: §11E (appearance/dialog helpers — `OptionDialogs` return-mapping via `DialogsTest` at unit *and* e2e, plus `AppearanceManager` LAF/theme state via `AppearanceManagerTest`, 26/40 adequate) and the mutation core of §11D (`MusicEditOperations` — `MusicEditOperationsMutationTest` asserts mutation-record fields precisely, 28/69 adequate). Everything else is largely untested.

**Defining gap — null-state guards and thin-dispatcher action bodies, recurring from Sessions 5/6.** In `MusicEditOperations` every operation's null-`activeSelection` guard is dark (six operations). In `ui/playback` all four `*Action.actionPerformed` bodies (icon toggle / `Command` post / direct controller call) are untested — the same "action posts a `Command`, only the downstream handler is tested" pattern flagged across §5. The lesson holds: the dispatch itself is never exercised.

**Riskiest dark computation:** (a) `PlaybackController` transport state machine — STOPPED/PAUSED/PLAYING, six transition paths (`togglePlayPause` alone has four), and `handleMetaMessage` binary line/note index decoding — covered only by its four `selectionDidChange` tests; (b) `MusicEditOperations.canMakeFirstSecondEnding`'s `checkPrecedingElement` branches (INSERT_BARLINE / EXTEND_SPAN / NONE / invalid) are never asserted at the predicate level — tests only feed pre-built `EndingValidationResult`s into `makeFirstSecondEnding`, leaving the `EXTEND_SPAN` makeFirstSecondEnding arm dark too; (c) `MenuController.buildLabels`/`disambiguate`/`tildeSubstitute` Open-Recent label disambiguation (multi-branch, zero coverage); (d) `KeySignatureDisplay` parallel tonic tables + accidental-count thresholds (2 flats / 6 sharps) + `AttributedString` font-attribute ranges; (e) `MidiController.setPlaybackVolume` 50–100→64–127 scaling and clamps, and `PlayThread.sendNoteOn`/`sendNoteOff`.

**Menu/platform are mostly wiring (`none`), as predicted.** §11A is 15/37 `none` (declarative submenu construction) with the real logic concentrated in `MenuController`; §11C is 7/15 `none` (the NS* classes are pure Rococoa native pass-throughs that cannot be unit-asserted without a live macOS runtime), with all real logic in `MacNativeMenuController` — the `dialogVisibilityDidChange` receiver (the controller's whole runtime purpose) and the 3-branch `discoverNativeItems`, both dark. `BaseDialogCounterTest` covers the *sender* side of `DialogVisibilityDidChangeNotification`; the *receiver* side here is untested.

**inadequate (5):** `PlayPauseAction` DISABLE_WHEN_PLAYING (the audit test only checks `DISABLE_WHEN_EDITING_TEXT`, never that the pause button stays enabled during playback); `MusicEditOperations` ×3 — `toggleTuplet` `size==0` removal branch never exercised (only the grade-match path), and `removeDynamicsFromSelection` asserted with `isNotEmpty()` instead of exact removal counts in both `MusicEditOperationsMutationTest` and `ScoreViewControllerCommandHandlerTest`, and the `checkPrecedingElement`→INSERT_BARLINE result never directly asserted via `canMakeFirstSecondEnding`; plus a test-side convention violation in `DialogsTest.testShowOptionDialogReturnsClosedOption` (raw `"Title"`/`"Message"` literals instead of `Strings.*` keys, masked by the suppressed early return).

**Dead code (verified zero refs):** §11A — `FermataMenuItem` (whole class, superseded by `FermataAction`) and `MenuController.initHelpMenu`/`addCommonHelpItems`/`initLaunchMenu` (call sites commented out). §11C — `NSMenu._Class.alloc()`, `NSMenuItem._Class.alloc()`, `NSMenu.CLASS`, `NSMenuItem.CLASS`, `NSMenu.title()`, `NSMenu.itemWithTitle()`, `NSMenuItem.isEnabled()` (speculative Rococoa scaffolding). §11F — `Constants.ACCELERATOR_KEYS`, `Constants.SONG_SCRIBE_JAR` (dead fields). §11B/D/E — none.

### §11 production observations (filed as GitHub issue #416)

1. **(real bug — highest severity)** `Constants.NON_BREAKING_HYPHEN` is assigned `Character.toString('­')` = U+00AD SOFT HYPHEN, a zero-width formatting character, not the true NON-BREAKING HYPHEN U+2011. `ExportABCAction` may silently fail to escape genuine U+2011 hyphens in lyric syllables (and conversely treats soft hyphens as non-breaking). Real-world impact depends on whether lyrics ever contain U+2011 vs U+00AD. (11F)
2. **(dead code → delete in remediation)** `FermataMenuItem` (superseded by `FermataAction`) and the three never-called `MenuController` methods `initHelpMenu`/`addCommonHelpItems`/`initLaunchMenu` (their call sites in `initMenus` are commented out). (11A)
3. **(testability)** `MenuController.buildLabels` is `private static` despite 30+ lines of path-disambiguation logic; widen to package-private to unit-test the Open-Recent label logic without reflection. (11A)
4. **(design smell)** `MusicEditOperations.flipStemDirection` shows a user-facing info dialog when `state == null`, while every sibling operation silently returns on the same condition. Either the dialog branch is effectively dead (the UI gate prevents calling with no selection) or it is intentional UX feedback — the inconsistency warrants review. (11D)
5. **(speculative Rococoa scaffolding)** The NS* unused members in observation/dead-code above appear copied from a Rococoa template (`CLASS` fields carry `@SuppressWarnings("unused")` but the `_Class.alloc()`/`title()`/`itemWithTitle()`/`isEnabled()` members do not). Separately, `MacNativeMenuController`'s `@Nullable private static` strong-reference anchor on `MenuController` is an *intentional* deviation from the singleton guide to satisfy MBassador's weak-reference rule — not a bug. (11C)
6. **(readability / style)** `PlaybackController.setSequenceToPlayFromSelection` uses a `//noinspection ObjectEquality` identity comparison on `Sequence` (correct but easy to misread — add a clarifying comment); `PlayThread` extends `Thread` rather than implementing `Runnable`; `PlayThread.setupInstrument` throws `RuntimeError.exit` on a null receiver in a path that `sendNoteOn` already guards with a silent null return, making the fatal branch unreachable but confusing. (11B)
7. **(dead fields)** `Constants.ACCELERATOR_KEYS` and `Constants.SONG_SCRIBE_JAR` have zero references in `src/main` or `src/test`. (11F)
