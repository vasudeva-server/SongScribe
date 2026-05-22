# Test Audit — Session Handoff

> **Start here in a new session.** This is the entry point for the
> production-first test audit. The full method, rubric, and session plan live in
> [`matrix.md`](./matrix.md); read it before doing anything. This file is the
> "where we are / what to do next" overlay. Both files are disposable scaffolding
> and will be deleted when the audit is done.

## What this effort is

Audit the test suite **from the production code, not from the existing tests** —
existing tests may be systematically testing the wrong things, so starting from
them would propagate that error. For each production class we enumerate testable
behaviors, classify each as `unit` / `e2e` / `none` per the rubric, then check
whether an adequate test (unit *or* e2e) already exists. The output is a complete
testing matrix that later drives a rewrite.

## Decisions already made (do not re-litigate)

- **Matrix is disposable scaffolding**, not a maintained living doc. The durable
  outputs are (1) rewritten tests and (2) the unit/e2e/none **rubric**, which
  gets promoted into `.agents/guides/testing-common.md` at the very end.
- **Plan first, fix later.** No test code changes until the *entire* matrix
  exists and a remediation order is approved. Audit sessions are read-only.
- **Quality Principles outrank the level rubric.** Apply Correctness →
  Usefulness → Coverage first; unit/e2e/none second. (See rubric in `matrix.md`.)
- **E2E assessed by reading source only.** Never run the e2e suite during the
  audit (it needs approval and isn't necessary for planning).
- **PIT confirms verdicts during remediation, not during the audit.** Audit
  `inadequate` verdicts are reading-based hypotheses (predicted surviving
  mutants). PIT (`./scripts/mutation-test.sh`, pure-logic + unit only) is run
  when a class's tests are rewritten — to prove the weakness and prove the
  rewrite kills the mutant. Audit sessions remain read-only.
- **Both levels checked per behavior.** When auditing a package, look for
  existing coverage in unit *and* e2e tests — not just the mirrored unit file.

## Per-session procedure

1. Open `matrix.md`, find the next package with status `not yet audited`.
2. Spawn a **subagent** (Java work → its prompt must begin with
   `MANDATORY: Read .agents/rules/serena.md`) to read that package's production
   classes and any tests touching them, and return a findings table with rows:
   **class · behavior · required level · existing test · verdict · action**.
   The subagent does the heavy reading so the main session keeps only the table.
3. Sanity-check the subagent's table against the rubric and Quality Principles;
   resolve any ambiguous classifications.
4. Append the table to the **Findings** section of `matrix.md` under a heading
   for the package, and flip that package's row in the progress table to `done`.
5. Stop and report. One package per session unless a package is small.

**Context-management pattern (proven in Session 4, ~50 classes across 8
packages).** For any large scope, run the sub-audits in **waves of ~3 parallel
subagents** and checkpoint between waves. Have each subagent write its
**authoritative findings table to a temp file** (`/tmp/session<N>_<pkg>.md`,
with the exact `### ` heading + table + notes paragraph it should occupy in
`matrix.md`) **and** return the same table in its final message. The main
session sanity-checks from the returned message but assembles via a cheap
`cat /tmp/session<N>_*.md >> matrix.md` in heading order — so full table text is
never re-emitted as model output, only read once. This kept main-session context
comfortable across 50 classes; `ui/action` (62) will likely want 3+ waves.

## Status

- **Session 0 (charter): DONE** — committed `ce2faf4b`. Rubric written, matrix
  scaffolded, `testing-common.md` reordered to lead with Quality Principles.
- **Session 1 (`dom`, 38 classes): DONE** — six parallel sub-audits; full
  findings appended to `matrix.md` §1, progress row flipped to `done`. Key
  cross-cutting gaps: untested pure conversion/geometry math (`ScaleContext`,
  `getSpanWidthSs`/`get*Px` clamps), weak-but-green tests (relative-only
  `getPitch`, `>=`/`>0` where exact values matter, self-referential oracles,
  tautologies), and untested branch/error paths (`Song` `@Handler` methods,
  `Line` merge logic, `BeatChange.fromLegacyName` happy paths).
- **Session 2 (`io`, 15 classes): DONE** — four parallel sub-audits
  (orchestration & XML; element & annotation serialization; line & view
  serialization; migration & legacy import); full findings appended to
  `matrix.md` §2, progress row flipped to `done`. Key gaps: serialization
  **write** paths almost entirely untested (conditional emission + `XML.escapeXML`);
  `LineIO` (largest IO class) has no dedicated test file; v1.0/legacy decode
  paths dark; migration **per-line** conversions tested only on empty lists.
  Production observations filed as a tracked GitHub issue (incl. `LineIO`
  `Ending.Type` round-trip data loss).
- **Session 3 (`layout`, 37 classes excl. 2 `package-info`): DONE** — six
  parallel sub-audits (orchestration & accumulation; horizontal spacing &
  columns; geometry primitives & metrics; lyric layout; ranges/endings/collision;
  stacking subsystem); full findings appended to `matrix.md` §3, progress row
  flipped to `done`. Key gaps: the riskiest math in the app is dark —
  `LayoutEngine` beam/stem/tie geometry has **zero** tests, `ElementBoundsSs` box
  model untested, `LineJustificationCalculator` zero tests; **weak-but-green
  assertions are systemic** (entire stacking-test family asserts only `ySs<0`/`>0`;
  pervasive `>=`/`isNotNull`/fixture-only); four self-referential oracles
  (`HorizontalSpacingCalculatorTest` entirely tautological, `PageModel`,
  `SongLayoutMetrics.verseBaselineY`, `LyricRenderMetrics.lyricBoxWidth`).
  **Dead code found** (verified zero refs): `AttachmentLayout`, `CollisionDetector`,
  `RangeLayout` → delete in remediation. Production observations filed as a
  tracked GitHub issue (#408; CSS unit-suffix bug, stale comment, unused params).
- **Session 4 (`midi`+`converter`+`util`+`smufl`+`prefs`+`font`+`export`+
  `uiconverter`, ~50 classes): DONE** — run in two waves of three parallel
  sub-audits each; full findings appended to `matrix.md` §4 (tables 4A–4H +
  a §4 summary), progress row flipped to `done`. Key gaps: **`converter` has
  zero tests of any kind**; `prefs.RecentDocumentsManager` (full MRU logic) and
  most of `Prefs` are dark; the riskiest **pure computation** is untested
  (`LineTrackBuilder.getTupletFactor` log2 timing, `MidiSequenceBuilder.buildSequenceWithRepeats`,
  `MidiEventFactory.addTempoEvent` byte encoding, `smufl.BBox` geometry,
  `StringUtils.wrapText`, `GraphicUtils` px↔unit conversions,
  `PageLayoutData`/`PDFExporter` margin math, `UIConverter.isLegalFileName`);
  weak-but-green/self-referential tests persist (`EngravingTest` self-referential,
  midi `isNotEmpty()`/`>=4` bend asserts, `MyFontUtilsTest` `isNotNull`,
  `UIUtils.positionDialog` mocked-out-and-hollow, `Prefs` map tests
  `containsKey`-only + name-mismatch, `DocumentFonts.defaultsFromPrefs` size-only).
  **Dead code found** (verified zero refs): `StringUtils.removeSyllabifyMarkings`,
  `MyFontUtils.getXHeight` → delete in remediation. Production observations
  filed as a tracked GitHub issue (#409; dead code, `SVGConverter.main`
  package-private latent bug, `Prefs.parseJsonValue` drops JSON arrays to null,
  `Prefs.getStringList` ignores defaults). Only one genuine e2e escalation:
  `ConvertAction.ConvertThread.run`.
- **Session 5 (`ui/action`, 62 classes): DONE** — run in two waves of three
  parallel sub-audits each (5A base/infrastructure; 5B note/element insertion +
  duration/articulation; 5C markings; 5D clipboard/selection/line; 5E file/app
  lifecycle; 5F export + misc dialog-open); full findings appended to
  `matrix.md` §5 (tables 5A–5F + a §5 summary), progress row flipped to `done`.
  **Defining shape: actions are thin dispatchers and the dispatch is untested** —
  the cross-cutting `wrong-level`/`missing` gap (action `actionPerformed` posts a
  `Command`, only the downstream handler is tested, never the action body) recurs
  across 5B/5C/5D/5E. Richest pure-logic gaps: `UIAction.enableFromSelectionSize`
  (all six selection-size predicates dark), `ExportABCAction` (~20 untested static
  ABC-serializer methods — the single largest untested-computation block),
  `DurationActionGroup`/`NonDurationActionGroup` mutual-exclusion handlers,
  `StickyUIAction.doActionPerformed`. Genuine e2e limited to shutdown/window-close
  (`QuitAction` adequate; `CloseWindowAction` missing). **Scope corrections during
  assembly:** 5D and 5E over-reached into collaborators — `ScoreViewController.*`
  (5D) and `MainFrame.save*/showSaveDialog/handle*`+`Shutdown` (5E); those rows
  were trimmed and folded into forward-pointers for Session 7 (`ui/component`) and
  Session 12 (`lifecycle`) to avoid double-counting (key carry-forward: the
  save/confirm data-loss guard in `MainFrame.showSaveDialog()` + save paths is
  untested). **Dead code:** `ScoreViewController.handlePaste()` is a TODO-only stub
  → `PasteAction` is a silent no-op (Session 7 scope). Eight production
  observations filed as a tracked GitHub issue (#410; incl. `LaunchAction`
  silent `IOException` swallow, `KeySignatureChangeAction` un-guarded
  `getScoreView()` NPE risk, `MainFrame.saveAsNewFile` empty Save-As filename,
  `ExportSVGAction` wrong filter label, `ExportABCAction.translateTempo` mismatched
  quote delimiters).
- **Session 6 (`ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard`,
  11 production classes): DONE** — one wave of four parallel sub-audits (6A
  `SelectionCoordinator`; 6B selection data holders + `ClipboardManager`; 6C
  `ui/edit`; 6D `ui/adjustment`); full findings appended to `matrix.md` §6
  (tables 6A–6D + a §6 summary), progress row flipped to `done`. 259 behavior
  rows: 78 adequate, 154 missing, 10 inadequate, 7 wrong-level, 10 none. ("15" in
  the progress table counts 4 `package-info.java` files.) Key gaps:
  **`ui/adjustment` is the largest dark zone** — `VerticalAdjustment` and the
  `Adjustment` base have zero tests, and the two existing `HorizontalAdjustmentTest`
  tests are *inadequate* (named `…SnapToEndSkipped` but never call `drag()`;
  assert only model preconditions, not the snap arithmetic); **`ui/edit` has no
  mirrored test file** (`GraceModeManager`/`EditModeManager` state machines
  covered only by e2e happy-path robot tests + assertion-free fixture mocks);
  `SelectionCoordinator` is well-covered (47/93 adequate) but its flag-gating
  predicates are dark (`selectionHasRests`, `canDeleteLine`, `canChangeTempo`,
  `restoreActionStatesWithFlag`, the `triggerReflection` dedup guard). Two
  systemic gaps recur across 6A/6B: cross-line selection guards and the
  reversed-drag branch of `extendSelectionTo`. **No production dead code found.**
  Two classifications resolved during assembly: `Adjustment` ctor `missing→none`
  (pure listener registration); `SelectionCoordinator.globalMouseReleasedListener`
  `e2e/inadequate→unit/missing` (cleanup body is directly invokable). One
  production observation filed as a tracked GitHub issue (#411): `VerticalAdjustment`
  adds the pixel-derived `diffY` to `…Ss` staff-space fields without `pxToSs`
  conversion (off by ~8× at the default scale).
- **Session 7 (`ui/component`, 62 production classes + 3 `package-info`): DONE** —
  three waves of three parallel sub-audits (7A control plane; 7B `ScoreView`; 7C
  hit-test/drag/selection/preview; 7D `MainFrame`; 7E line/score rendering geometry;
  7F score panels & text components; 7G toolbars; 7H input & text widgets; 7I
  buttons/borders/frames & navigation helpers); full findings appended to
  `matrix.md` §7 (tables 7A–7I + a §7 summary), progress row flipped to `done`.
  **387 behavior rows: 319 unit / 15 e2e / 53 none; of 334 testable, 231 missing ·
  88 adequate · 8 wrong-level · 7 inadequate** (~69% dark). Confirmed highest-risk
  gaps: **(1)** the data-loss guard is untested — `MainFrame.showSaveDialog()` +
  the whole `save`/`saveCurrentFile`/`saveAsNewFile` chain have zero direct tests
  (`ShutdownTest` only covers wiring + the forced CLOSED_OPTION answer; the
  Don't-Save / Save-propagate branches are `wrong-level`); **(2)** `ScoreViewController.handlePaste()`
  is a body-only TODO — paste is a confirmed silent no-op (root cause of the
  Session-5/6 `PasteAction` finding, a real production defect); **(3)** the
  px↔staff-space coordinate chain in `LineComponent` (`staffPositionToYPx`/
  `getMiddleLineYPx`/`calculateMiddleLineYSs`) is dark — issue-#411 territory;
  **(4)** `ElementHitTest` + `LineSelectionHandler` geometry have no unit tests
  (and `SelectionTest.testDragSelect` uses weak `>=3`); **(5)** score-panel layout
  invariants (`TextPanel` centering, `StaffPanel` lyric-continuation threading,
  `MainPanel` gap, `ScorePanel` viewport-loop guard) untested; **(6)** widget
  pure-logic dark (`TextFocusDelegate` first-Tab guard, `NonEmptyGuard` modes,
  `InputUtils` filters, `MyBorder`, `TickSlider` snap, `ComponentHierarchyNavigator`,
  `DurationListCellRenderer` glyph map). **Bright spots (adequate):** `LyricEditor`
  (dense exact-value matrix), `NoteDragHandler` + `PreviewElementManager*` family,
  `ScoreView.setFonts`, `ScoreViewController` command-handlers. **Two scope
  corrections during assembly:** `ScoreComponent` (audited by both 7E and 7F) kept
  under 7E only; `ActionGroup`/`DurationActionGroup`/`LyricEditorActionAuditTest`
  rows raised by 7G belong to `ui/action` and were trimmed (already in §5A).
  **No dead classes found**; one dead *branch* (`DurationToolbar`'s unreachable
  `defaultButton != null` guard). Production observations (handlePaste stub;
  `DurationToolbar` guard; `LineComponent` #411-adjacent coordinate chain) recorded
  in the §7 summary and filed as a tracked GitHub issue (#412).
- **Next: Session 8 — `message` (mutation/command/notification + core, 86
  classes).** Large; will need multiple waves. Per the rubric most `message.mutation`
  / `message.command` / `message.notification` records are pure data holders →
  `none`, **unless they carry derivation logic** — focus the audit on records with
  computed/derived state and on the core message-bus plumbing, not the trivial
  carriers. Read `.agents/guides/messages.md` and `.agents/guides/mutations.md`
  before starting.

## Session order (risk-ordered)

See the progress table in `matrix.md` for the authoritative list. Summary:
`dom` → `io` → `layout` → (`midi`/`converter`/`util`/`smufl`/`prefs`/`font`/
`export`/`uiconverter`) → `ui/action` → (`ui/selection`/`edit`/`adjustment`/
`clipboard`) → `ui/component` → `message` → `ui/renderer` → `ui/dialog` →
(`ui/menu`/`playback`/`platform`/top-level `ui`) → (`lifecycle`/`error`/
top-level) → e2e reconciliation.

## Baseline counts

Unit: 1267 `@Test` methods (~132 files). E2E: 79 methods (7 files). Production:
~507 classes. These are gap-finders, not grades.
