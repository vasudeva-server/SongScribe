# Full Test-Only-Surface Sweep — Phase 10 Record

Produced by [`contract-driven-rollout.md`](./contract-driven-rollout.md) Phase 10.
Companion to the 31-member name-based inventory in
[`contract-driven-testing.md`](./contract-driven-testing.md) §6, which found only
members *named* `*ForTest*`/`*ForTesting*`. This sweep works from the tests
instead: every production class with a test file was checked for non-`private`
members, and every candidate was run through `jet_brains_find_referencing_symbols`
to see whether every reference resolves under `src/test/`. Its purpose is to
surface the *invisible* violations the name-based sweep could not find —
`FontDialog.java:37` (a package-private field commented "Widened to
package-private for testing" but named plainly) is the confirmed example that
motivated this phase.

**This phase records; it fixes nothing.** Each package's own contract phase
(Phase 11 onward, per the "Remaining phases" table and "Per-area procedure" in
`contract-driven-rollout.md`) fixes what's recorded here for its package, using
step 5, "Fix test-only surface per its category."

13 parallel sweeps covered every package with a test directory: `dom`,
`io`+`midi` (incl. `io/musicxml`), `layout` (incl. `stacking`), `ui` root+`action`,
`ui/component` (incl. `score`, `toolbar`), `ui/dialog` (incl. `fontchooser`),
`ui/renderer`, `ui/selection`, `ui/clipboard`+`edit`+`menu`+`playback`, `undo`+`util`,
`message`+`prefs`+`smufl`, `engraving`+`error`+`font`+`hit`+`lifecycle`+`shape`,
and `e2e`. `converter`, `uiconverter`, `export` are excluded per D15 (no tests
written for them).

---

## A. Genuinely test-only surface (delete or restructure)

Confirmed: every reference outside the declaring class resolves under
`src/test/`, and no legitimate production caller exists at any visibility.

### `dom`

- **`AttributionPane.measure(Font, Font, double)`** and its return type
  **`AttributionPane.MeasuredCache`** (`AttributionPane.java:371`, `:168`) —
  package-private; every production caller (`getContentWidthPx`,
  `getContentHeightPx`, `getContentSizePx`, `render`) is intra-class, so
  `private` would satisfy production. Own Javadoc: "so the measurement test can
  assert on the zoom-scaled layout without going through a mocked
  `Graphics2D`." Only external reference: `AttributionPaneTest`. The
  computation is real, load-bearing logic — restructure (assert through the
  public surface, or give the pre-render-layout need a real public contract),
  not delete.
- **`AttributionPane.LINE_BOX_REFERENCE`** (`:97`) — package-private
  `static final String`, own Javadoc: "so the measurement test can reference
  the same string rather than duplicating the literal." Only external
  reference: `AttributionPaneTest.heightOf`. Fix: keep `private`, let the test
  hold its own literal.
- **`AttributionPane.LEADING_SS`** (`:79`) and **`SUB_ATTRIBUTION_GAP_SS`**
  (`:86`) — package-private constants, same cluster, no "for tests" comment
  but the same mechanical shape (production use is intra-class only).

All five `AttributionPane` members are one cluster: a single test class
(`AttributionPaneTest`) was granted broad access to internal measurement
machinery instead of asserting through the class's public contract.

### `io` / `midi`

- **`LineIO.LineReader.getLine()`**, **`getLastTag()`**, **`getWhere()`**
  (`LineIO.java`) — package-private accessors into the SAX parser's in-progress
  state. Only caller: `LineIOTest.LineReaderStateMachine`. Contrast:
  `getLegacyOffsets()` on the same class *is* called from production
  (`SongIO.DocumentReader.endElement12`) and is correctly not flagged — these
  three have no such caller, existing solely so the test can step through the
  state machine instead of asserting on parse output.
- **`SlideMidiHelper.resetSensitivity()`** (`SlideMidiHelper.java:149`) —
  `public`, zero production references anywhere in the project. Only caller:
  `SlideMidiHelperTest.CreateRpnMessagesIfNeeded.testResetCausesReEmit`. Every
  production `SlideMidiHelper` is constructed fresh per use, so nothing needs
  mid-lifetime reset.

### `layout`

- **`ElementColumn`'s 9-arg constructor** (`ElementColumn.java:149`,
  `ElementColumn[1]`) — package-private, own Javadoc already admits it:
  "reachable only from tests that do not care about augmentation-driven
  spacing. Production callers must use the full constructor." All production
  call sites use the 10-arg constructor; the 9-arg one is called only from 15
  test classes. The constructor-overload analogue of `FontDialog.chooser` —
  invisible to the name-based sweep because constructors can't carry a
  `*ForTest*` name. Fix: a test builder/factory method (an overload on the
  already-test-side `ElementColumnTestHelper`) replaces it; delete the
  constructor.

### `ui` (root)

- **`AppearanceManager.setLafOperations(LafOperations)`**
  (`AppearanceManager.java:128`) — package-private static setter, doc: "Used
  by tests to inject mocks." Only reference: `AppearanceManagerTest.setUp`. A
  raw setter swapping the static `lafOps` field for a mock; the seam belongs on
  a constructor/factory path, not a package-private back door on a static
  utility class.

### `ui/action`

None (`FirstSecondEndingAction.setCachedResult` is dead code, not test-only —
see §E).

### `ui/component`

- **`BorderPanel.setUniformBorderValue`**, **`setEdgeBorderValues`** — doc:
  "Package-private for testing." All references in `BorderPanelTest`.
- **`ScoreView.scoreKeyBindings`** (`:226`, field, doc: "Package-private so
  tests can inject synthetic bindings directly") — production only calls
  instance methods on it; only external `.put()` access is
  `ScoreViewTest.SetKeyBindingsEnabled`.
- **`ScoreView.setMainPanel`** (`:391`) — zero production callers
  (`initMainPanel` assigns the field directly); referenced by
  `ScoreViewSetFontsTest`, `ScoreViewOverlayHostingTest`, `ScoreViewTest`.
- **`MainFrame.scoreView` / `currentFile`** (`:130,134`, `protected`) —
  `MainFrameTest`'s own class doc: "Fields ... are set directly from this
  package-sibling test class" to avoid the heavyweight `JFrame` constructor.
  Both fields are otherwise only touched inside `MainFrame` (aside from the
  already-public accessors).
- **`MainFrame.showSaveDialog`, `updateTitle`, `getDisplayName`,
  `saveCurrentFile`, `saveAsNewFile`** — each has a same-class production
  caller (`private` would suffice for production); `MainFrameTest` uses
  Mockito `spy()` + `doCallRealMethod()` to test each in isolation, which
  requires non-private visibility. One cluster, same root cause as the fields
  above (a heavyweight `JFrame` subclass that's hard to test any other way).
- **`MainFrame.drainStartupErrors`**, **`performStartupAction`** (static) —
  same-class production caller (`main()`/`reveal()`); `MainFrameTest` calls
  both directly, bypassing `main()`.
- **`ScoreViewController.handleCopy`**, **`handleDelete`** — sibling
  `handleCut`/`handlePaste` are `private`; both have a same-class caller
  (`handlePasteboardOp`), but `ScoreViewControllerTest` and
  `ScoreViewControllerDeleteTargetTest` call them directly, bypassing the
  focus-ownership/paste-mode gate. (One test comment: "handleDelete is
  package-private — no reflection needed" — a deliberate choice, not an
  oversight.)
- **`ActivationGate.glassPane`, `cmdTabDebounce`, `backgrounded`,
  `overlayArmed`** (static fields) — `ActivationGateTest.tearDown()` resets
  all four directly ("Reset static state so each test starts clean"). No
  production code needs external access. Same root cause as the Lifecycle
  finding in §C.
- **`ActivationGate.deactivate`** (static) — same-class caller exists;
  `ActivationGateTest` calls it directly in three tests.
- **`PreviewElementManager.instance()`**, **`setCurrentXIndex`**,
  **`setCurrentPreviewLine`**, **`setCurrentStaffPosition`**,
  **`setXPosSsMatchesElement`**, **`getOverlay()`**,
  **`getGraceGlissandoOverlay()`** (all package-private static) — one cluster:
  `PreviewElementManagerTestBase.baseSetUp`/`baseTearDown` arrange static
  state directly through raw setters rather than a real mutation path.
  Textbook "arranging state is where the pressure comes from" — the fix is a
  real constructor/factory API, not more raw static setters.

### `ui/dialog`

- **`AnnotationDialog.annotationCombo`, `leftRadio`, `centerRadio`,
  `rightRadio`, `aboveRadio`, `belowRadio`** — all read/written directly by
  `AnnotationDialogTest`, bypassing `populateControls`/`applyChange`.
- **`TempoSection.tempoTypeCombo`, `tempoDescriptionCombo`** — set directly by
  `TempoSectionTest` to force edge cases `setTempo()` cannot induce (e.g. a
  null selection).
- **`BeatChangeDialog.durationCombo`, `beatCombo`** — read/written directly by
  `BeatChangeDialogTest`, including a null-model edge case.
- **`KeySignatureChangeDialog.keysCombo`, `keysSpinner`,
  `indexOfSelectedElementLabel`** — read/written directly by
  `KeySignatureChangeDialogTest`.
- **`TempoChangeDialog.tempoSection`** — `TempoChangeDialogTest` calls methods
  on it directly instead of going through `populateControls`/`applyChange`.
- **`DoNotShowMessage.dontShowCheck`** — set directly by `DoNotShowMessageTest`.
- **`PaperSizeStep`** — a 12-field cluster (`templates`, six spinner models,
  `leftInnerLabel`, `rightOuterLabel`, `mirroredCheck`, `currentUnit`,
  `templateCombo`), all poked directly by `PaperSizeStepTest`. Worst instance:
  `testEndWritesAllSixPixelValuesAndMirroredFlagToPageLayoutData` does
  `step.currentUnit = GraphicUtils.Unit.INCH;` directly — no production code
  path ever sets `currentUnit` that way (only via `UnitAction`).
- **`BaseDialog.getTabList()`**, **`getTabbedContent()`** — doc: "Package-private:
  allows tests to read the sidebar list/composite without full dialog setup."
  Both raw internal-state exposers with no production caller; only reference
  is `BaseDialogTabsTest`.

### `ui/clipboard`+`edit`+`menu`+`playback`

- **`GraceModeManager.isPendingCancel()`, `isPendingConnect()`, `setState`,
  `setPendingCancel`, `getGraceNote`/`setGraceNote`, `getGraceLine`/`setGraceLine`,
  `setGraceLineComponent`, `getGraceNoteIndex`, `isJustEnteredInsert``** — nine
  members, one problem: `GraceModeManager` carries ~12 private fields
  describing an in-progress grace-note drag/pairing operation, and rather than
  a constructor/factory that takes this state, nearly every field got a
  package-private getter/setter so `GraceModeManagerTest` (~35 test cases)
  could arrange and assert them directly. The clearest concentration of
  test-only surface in the whole sweep — see also the reflection finding in
  §D, which reaches the fields this cluster doesn't even bother exposing.
- **`PasteModeManager.getInsertionMarkerOverlay()`** — doc, verbatim:
  "Package-private: test support only." All references in
  `PasteModeManagerTest`.
- **`MenuController.openRecentMenu`** (field) — every production use is
  intra-class; `MenuControllerTest` reaches into
  `controller.openRecentMenu.getMenuComponents()` directly instead of through
  an accessor.
- **`MidiController.closed`** (static field) — production reads/writes it only
  inside `closeMidi()`; sole external reference is `MidiControllerTest.tearDown`.
  One symptom of the lifecycle finding in §C.

### `ui/selection`

- **`ActionReflector.hasSavedActionStates()`** — doc: "for tests that verify
  clear/restore semantics." No internal caller anywhere; pure assertion probe.
- **`ActionReflector.setManagedActions(List<UIAction>)`** — `public`,
  undocumented. Zero production references; only two callers, both test
  fixture-setup methods (`DeleteLyricTest`, `EndingConfirmsTest`, both outside
  `ui/selection`).
- **`SelectionDragTracker.getDraggingLine()`** — doc: "for tests that verify
  drag-cleanup semantics." Inside the class, `draggingLine` is read directly as
  a field; the accessor exists only for the test.
- **`SelectionDragTracker.getGlobalMouseReleasedListener()`** — no doc. Sole
  reference: `SelectionCoordinatorMiscBehaviorTest`, letting a test grab the
  `AWTEventListener` lambda and invoke it directly, bypassing the real AWT
  event queue.

### `undo`

- **`UndoController.DEFAULT_UNDO_STACK_MAX_DEPTH`** (`:115`) — package-private,
  comment: "so UndoControllerTest can drive the eviction boundary without
  duplicating the literal." Read once in production (seeds
  `undoStackMaxDepth`); every other reference is in `UndoControllerSavePointTest`
  / `UndoControllerTest`.

### `message` / `smufl`

- **`MessageLogger.instance`** (`:33`, `public static @Nullable`) — every
  direct read/write outside the class is in `MessageLoggerTest`. Production
  only calls `MessageLogger.init()`, never the field. Also filed under §C —
  the field is a symptom of `MessageLogger` having no reset/dispose
  counterpart to `init()`.
- **`SMuFLMetadata.getAdvanceWidth`/`getAdvanceWidthOrZero`, 2-arg overloads**
  (`:111`, `:129`) — production calls each exactly once, always with the
  singleton's own map; own doc comments admit the seam exists "so a test can
  exercise the null-return case with a caller-supplied map... the null branch
  is otherwise unreachable." Only non-singleton-map callers are
  `SMuFLMetadataTest`. Contrast with `requireMapValue` (§H) — that one is
  genuinely polymorphic in production across three maps and is not flagged.

### `engraving` / `error`

- **`LineThickness.STEM_MULTIPLIER`, `VOLTA_BRACKET_MULTIPLIER`,
  `TUPLET_BRACKET_MULTIPLIER`, `LILYPOND_BASE_THICKNESS_SS`** — package-private,
  while every sibling multiplier constant not read by a test is `private`.
  Every external reference is `LineThicknessTest`.
- **`RuntimeError.MISSING_RESOURCE_USER_MESSAGE`** (`:42`) — package-private,
  comment: "so RuntimeErrorTest can assert against the wired value instead of
  a copy." Used internally by `missingResource()` (where `private` would
  suffice); externally only by `RuntimeErrorTest`.

### `e2e`

- **`Utils.sleep(long)`** (`songscribe.util.Utils`) — `public static`
  `Thread.sleep` wrapper. Zero `src/main` references; only caller is
  `ElementInsertionTest` (e2e), waiting out a drag-detection window.
- **`HairpinRenderer.hitTestHairpin(...)`** — a complete, well-documented
  public method whose only callers are `HairpinSelectionTest` (e2e) and
  `HairpinRendererTest` (unit). Production hairpin selection does **not** go
  through this method — it goes through `HitRegionBuilder.addHairpins`, a
  different geometry computation. Not a raw seam like the others in this
  section — a fully-formed method the real app never calls, kept alive only
  by tests exercising a code path production doesn't run. Needs a decision:
  reconcile the two hit-testing mechanisms, or remove the method and its
  test coverage as dead production code.
- **`OptionDialogs.setSuppressDialogs(boolean)`** — own Javadoc: "Controls
  dialog suppression for testing." Zero `src/main` callers, but flagged as
  *ambiguous* rather than a clean hit: dialog suppression during automated UI
  driving is a plausible legitimate need, so whether this becomes real
  automation infrastructure or gets deleted is a domain call this sweep
  doesn't settle.

---

## B. Misnamed internal API (rename and document)

Already a coherent unit — explicit arguments, a return value, and (mostly) a
real contract. The only flaw is a visibility/name that reads as scaffolding,
because every production caller of it happens to be intra-class.

### `midi`

- **`LineTrackBuilder.calculateSoundingDuration`**, **`calculateSoundingPercent`**,
  **`noteVelocity`** — each has exactly one production caller, always within
  `LineTrackBuilder` itself; `LineTrackBuilderTest` calls each directly.
- **`MidiEventFactory.MICROSECONDS_PER_MINUTE`**, **`SlideMidiHelper.EXPRESSION_CC`**,
  **`EXPRESSION_MAX`**, **`VelocityMap.DEFAULT_VELOCITY_FRACTION`**,
  **`ACCENT_BOOST`** — named tuning constants, each used only within their
  declaring class in production, each read directly by name in a test to
  avoid duplicating the literal (the project's own No Magic Numbers rule cuts
  both ways here — worth deciding case-by-case whether the fix is `private` +
  a test-local constant, or confirming package visibility as an intentional
  shared contract).

### `ui/component`

- **`ScoreView.computeAnchoredViewPosition`** (`:1369`, static) — real
  production caller (`applyZoomPercentAndReanchor`) and a precise doc comment
  ("Pure function of its arguments... so it can be unit-tested"). Already
  coherent; only flaw is living as a package-private static method on
  `ScoreView` rather than a promoted, independently-named internal API.
- **`ScoreViewController.deleteNote`** (`:1607`, static) — production caller
  (`deleteSelection` plus its recursive breath-mark cascade), full contract
  doc (`@return the number of elements removed (1 or 2)`).
- **`LyricEditor.keepAllocationAtContentOrigin`** (`:505`, static) — production
  caller is `LeadingSlackFieldView.adjustAllocation`, a *nested class of
  `LyricEditor`*, so `private` would actually suffice for production.
- **`SelectionHidingCaret.isSelectionActive`** (`:50`) — production callers
  (`paint`, `damage`) are both same-class; doc states its contract.
- **`MainFrame.remainingFloorMs` / `remainingCapMs`** (`:412,420`, static) —
  same-class production caller (`runStartupGate`), full doc comment describing
  the clamp contract.

---

## C. Lifecycle — new findings (not covered by Phases 8-9)

Same shape as the ~11 members Phases 8-9 already fixed (`Actions`,
`PlaybackController`, `UndoController`, `SelectionCoordinator`,
`Prefs.parseJsonValueForTest`, `SMuFLMetadata`'s three wrappers): a class with
`initialize()`/accumulating static state and no legitimate teardown path, so
tests reach for a raw reset hook instead.

- **`AppearanceManager.reset()`** (`ui`) — doc: "Resets internal state. Used
  by tests." `AppearanceManager` is a static, process-global utility
  (`lafOps`, `listenerRegistered`, an OS theme-change listener registration)
  with no production-facing teardown; `deinitialize()` exists on
  `Actions`/`PlaybackController` but not here.
- **`ActivationGate`** (`ui/component`) — `install`/`activate`/`deactivate`/
  `armForOverlay`/`disarmForOverlay` exist, but no supported way resets its
  static state (`glassPane`, `cmdTabDebounce`, `backgrounded`, `overlayArmed`)
  back to "never installed." `ActivationGateTest.tearDown()` reaches directly
  into all four fields (§A) for lack of a real API.
- **`EditModeManager.setInstance`, `GraceModeManager.setInstance`,
  `PasteModeManager.setInstance`** (`ui/edit`) — a tightly coupled singleton
  trio (constructed together via `EditModeManager.init`), each given an
  identical test-only `setInstance` escape hatch instead of a real
  `initialize()`/`deinitialize()` pair — the pre-Phase-9 `PlaybackController`
  shape.
- **`MidiController`'s static device fields** (`sequencer`, `midiReceiver`,
  `synthesizer`, `closed`) (`ui/playback`) — no single reset entry point, so
  four different test classes each hand-roll their own partial teardown
  (`MidiControllerTest` resets all four plus `failForTesting`;
  `PlaybackControllerTest` resets two; `PlaybackActionsTest` resets one;
  `PlayThreadTest` resets one). The inconsistency is itself evidence this
  should be one production method. `synthesizer` and `failForTesting` were
  already predicted by name in `plans/singleton-lifecycle-contracts.md` §7
  finding 7 as an expected Phase-10 catch; `sequencer`, `midiReceiver`, and
  `closed` are the additional, previously-undocumented members in the same
  cluster.
- **`MyFontUtils.resetFontCache()`** (`util`) — `public`, doc: "Clears the lazy
  font cache so tests can install fonts before the first load." A real,
  exercised reset seam (4 call sites across 3 packages) for a lazily-populated,
  never-invalidated cache with no production reload path — the pre-Phase-9
  `UndoController.resetForTest` shape, just already `public` rather than
  package-private.
- **`MessageLogger`** (`message`) — `init()` is a one-way lazy-singleton
  initializer with no reset/dispose counterpart; `MessageLoggerTest` reaches
  directly into the public `instance` field (§A) instead of a real API because
  there isn't one.
- **`Shutdown.reset()`** (`lifecycle`) — doc: "Clears all registered tasks and
  resets state. Package-private for test isolation." `Shutdown` accumulates
  confirm/EDT/JVM task lists across the app's lifetime with no production
  caller that ever needs to return it to baseline (quitting the app ends the
  process). Every reference is `ShutdownTest`. (`Shutdown.shutdown()` and
  `runJVMTasksFromHook()` are also package-private but genuinely dual-use —
  not flagged.)
- **`BaseDialog.resetVisibleBlockingDialogCount()`**, **`resetSavedGeometry()`**
  (`ui/dialog`) — each a one-line static-state reset (`visibleBlockingDialogCount
  = 0`, `SAVED_GEOMETRY.clear()`), called only from `src/test/` (14 and 13
  test classes respectively). No production caller for either. Same shape as
  the already-known `PreferencesDialog.resetInstrumentsForTesting` — strong
  candidates for whatever lifecycle mechanism eventually retires it.

---

## D. Reflection into production internals

Per the dev rules, this is "the same violation, not an escape from it" —
reaching a private field via `setAccessible` is worse than the accessor the
rule bans, because the honest (banned) version would at least break the build
on a rename.

- **`ReflectionTestHelper.java:228-236`** (`ui/selection`, already known) —
  reaches `ActionReflector`'s private `reflectableActions` and
  `managedActions` via `getDeclaredField`/`setAccessible`. Confirmed still the
  only reflection instance in `ui/selection`; fix deferred to that package's
  phase per the rollout plan.
- **`GraceModeManagerTest.java:2239-2272`** (`ui/edit`) — `setField`/`getField`/
  `findField` helpers, ~35 call sites, reaching `GraceModeManager`'s private
  fields that don't even have a package-private accessor (the ones that do
  are poked through §A's cluster instead). A proper test-construction API for
  `GraceModeManager`'s drag/pairing state would replace both this and the §A
  cluster at once.
- **`MessageCenterTest.java:173-175`** (`message`, `HandlePublicationError.resolveMethod()`)
  — reaches `MessageCenter`'s `private static handlePublicationError(PublicationError)`
  via `getDeclaredMethod`/`setAccessible`. The test's own comment explains why:
  MBassador wraps its registered error handler in `catch(Throwable)` and
  swallows what it throws, so `MessageCenter.post()` can never observe
  `handlePublicationError`'s behavior end-to-end — reflection is currently the
  only way to invoke it directly. Worth a real seam (e.g. package-private
  visibility with a documented contract) rather than reflection.

No other reflection hits were found in any of the 13 sweeps (`dom`, `io`,
`midi`, `layout`, `ui` root/`action`/`component`/`dialog`/`renderer`,
`ui/clipboard`+`menu`+`playback` — only `edit` had a hit —, `undo`, `util`,
`prefs`, `smufl`, `engraving`, `error`, `font`, `hit`, `lifecycle`, `shape`,
`e2e`).

---

## E. Dead code found incidentally (not test-only surface)

Zero references anywhere — not even from tests. Distinct from §A: nothing
exists here "for tests," it simply isn't called.

- **`FirstSecondEndingAction.setCachedResult(EndingValidationResult)`**
  (`ui/action`) — `cachedResult` is set directly by `validate()` instead; the
  paired `getCachedResult()` *is* used legitimately.
- **`ScoreView.setScorePanel`**, **`setScrollPane`** (`ui/component`) — zero
  references, production or test.
- **`LineComponent.getNoteDragHandler()`** (`ui/component/score`) — zero
  references, production or test.

---

## F. Borderline / nuanced — visibility widened for a test, but fails the strict "every reference is a test" bar

Each has a genuine production caller, but every such caller is a call from
another method of the *same* class — a same-class call never needs anything
above `private`; only the same-package test benefits from the wider
visibility. Recorded because the underlying defect (visibility raised for an
external caller that turns out to be test-only) is the same class of problem
as §A, even though it doesn't meet the letter of the sweep's criterion.

- **`ui/selection`**: `ActionReflector.triggerReflection()` (doc: "so tests can
  trigger reflection directly without a notification") and
  `ActionReflector.updateGraceNoteActionEnabled(boolean)` (doc: "so tests for
  rows 93/94 can exercise the logic directly") — both called only intra-class
  in production; every external caller is a test (~2 dozen call sites for the
  first).
- **`layout`**: `HairpinEndpoints.dynamicAdvanceRightEdgeSs` — its only
  production caller is `dynamicAdvanceLeftEdgeSs`, whose own only production
  caller is this method — so the pair is only reachable through tests
  transitively. Two honest readings recorded in the sweep: dead production
  code (delete, let the test assert the sub-expression directly), or an
  intentional symmetric pair kept for completeness. Domain call, not settled
  here.
- **`ui` root**: `ZoomController.WHEEL_ZOOM_FACTOR_PER_NOTCH` (doc: "so
  ZoomControllerTest can derive sub-step rotations without duplicating the
  literal"); `KeySignatureDisplay.FLAT_TONICS`/`SHARP_TONICS`/
  `MIN_FLAT_COUNT_WITH_ACCIDENTAL`/`MIN_SHARP_COUNT_WITH_ACCIDENTAL` (all four
  statically imported and indexed by `KeySignatureDisplayTest` across ~15
  methods, rather than asserting only through the public `getDisplayName`
  contract); `EndingConfirms.typeNameFor(ElementType)` (lower severity — a
  real pure function with its own contract; testing an internal helper
  directly is defensible, but the visibility is wider than production needs).
- **`ui/renderer`**: `KeySignatureRenderer.KEY_CHANGE_RIGHT_MARGIN_SS` — has a
  real production caller (`renderKeySignatureChange`), so **not** test-only,
  but its comment ("so KeySignatureRendererTest can assert against it rather
  than copy it") states the test motive directly, unlike its siblings.
- **`ui/component`** (minor — constants with a real production reader, widened
  only so a test can name them instead of duplicating the literal; likely just
  a `public` fix, not structural): `ScoreViewController.TUPLET_INFO_CACHE_PRIORITY`,
  `MainFrame.MIN_SPLASH_DURATION_MS`/`MIDI_INIT_TIMEOUT_MS`,
  `LyricEditor.MAX_LENGTH_CHARS`/`LONG_A`/`LONG_A_CAPITAL`/`N_TILDE`/
  `N_TILDE_CAPITAL`/`LEADING_PAINT_SLACK_PX`, `PopupToolbarButton.POPUP_GAP_PX`.

---

## G. Already known — confirmed still present, fix deferred elsewhere

Found by the original name-based sweep (`contract-driven-testing.md` §6) or by
`singleton-lifecycle-contracts.md`; re-confirmed present by this sweep but
**not re-analyzed**, per the task's "do not re-derive" instruction. Listed here
only so this document is a complete map of what future package phases inherit.

| Member | Confirmed at | Deferred to |
|---|---|---|
| `SongSettingsDialog.getLineWidthFieldForTest()` | `SongSettingsDialog.java:208` | dialog decoupling phase |
| `SongSettingsMusicTab.getLineWidthField()` | `SongSettingsMusicTab.java:185` | dialog decoupling phase — new: sole caller is the row above, same chain |
| `FontDialog.chooser` (widened field) | `FontDialog.java:37-38` | dialog decoupling phase |
| `PreferencesDialog.resetInstrumentsForTesting` | `PreferencesDialog.java:165` | `ui/playback`/`MidiController` phase |
| `MainFrame.clearStartupErrorsForTest` | `ui/component` | `ui/component` phase |
| `PreviewElementManager.resetOverlaysForTest()` | `PreviewElementManager.java:589-592` | `ui/component` phase |
| `RecentDocumentsManager.resetForTest`/`reloadForTest` | `prefs` (not encountered in this sweep's scope) | `prefs` phase |
| `Prefs.removeObsoleteKeysForTest` + 3 siblings (`removeSystemDefaultKeysFromStoreForTest`, `writeTypedForTest`, `migrateForTest`) | `Prefs.java:177,185,193,201` | `prefs` phase |
| `Prefs.getRawStored` (×2), `putRawStored` | `Prefs.java:149,159,169` | `prefs` phase — not in the original 31-member inventory (no `*ForTest*` name), confirmed here |
| `RuntimeError.setExitHandlerForTesting` / `resetAlertShownForTesting` | `RuntimeError.java:135,140` | wherever `error/` next gets a contract pass |

---

## H. Phase 9 verification — confirmed landed

Checked while sweeping the packages that touch these members, since a stale
rename would silently invalidate the Phase 8/9 record:

- **`Prefs.parseJsonValueForTest` → `JsonValues.toJavaValue`** — landed.
  `Prefs.java` has no `parseJsonValueForTest` member; `JsonValues.toJavaValue`
  is real, public, documented, with its own `JsonValuesTest`.
- **`SMuFLMetadata`'s three `*ForTesting` wrappers → `requireBBox`/`requireAnchors`/`requireAdvanceWidth`**
  — landed. No `*ForTesting` members remain; the three `require*` wrappers are
  public, documented, used by production. Their shared package-private helper
  `requireMapValue(Map<SMuFLGlyph, V>, SMuFLGlyph, String)` is **not**
  test-only — production uses it polymorphically across three distinct maps
  (bboxes, anchors, advance widths); a test using it too is incidental reuse
  of already-legitimate internal API.
- **`Actions`, `SelectionCoordinator`, `PlaybackController`** — spot-checked
  while sweeping `ui/action` and `ui/selection`; both confirmed already
  reworked (`initialize()`/`deinitialize()`, `dispose()`) with no stray
  pre-Phase-9 members remaining in their own classes (though `ui/edit`'s
  `EditModeManager`/`GraceModeManager`/`PasteModeManager` trio and
  `ui/playback`'s `MidiController` were *not* part of Phase 8/9's four-member
  list and still need the same treatment — see §C).

---

## I. Other observations (not test-only surface, flagged for awareness per project convention of never withholding a finding)

- **`SongMetadata.java:129-131`** (`dom`) — the section header comment
  "Normalization helpers (package-visible for tests in this package)" no
  longer matches the code under it: the only method there,
  `normalizeTitle` (`:142`), is `public` for a real, documented production
  reason. Stale comment, not a test-only-surface finding.
- **`ModificationSession.withoutMutationTracking`** (`dom`, `:169`) — its own
  Javadoc says "Intended for test setup... Production code should use
  `withModification(Runnable)` instead," but `docs/undo.md:106-108` documents
  real production callers (`MusicXmlReader`, `SongIO`, `ScoreView.setSong`).
  Confirmed real production use outside `dom/`, so **not** test-only — but the
  method's own doc comment now contradicts `docs/undo.md` and should be
  corrected.
- **`StaffElement.lyrics`** (`dom`, `:53`) — `public final List<Lyric>`, a raw
  mutable collection exposed as a field rather than through an accessor. Used
  throughout production (`LyricRun.java`, `StaffElementIO.java`,
  `io/musicxml/NoteMapper.java`, `MigrationPipeline.java`) as well as directly
  by `e2e/LyricLayoutTest.java:63`, so it fails the mechanical "all references
  under `src/test/`" test — not test-only surface, but a pre-existing
  encapsulation gap in `StaffElement`'s own design, independently surfaced by
  both the `dom` and `e2e` sweeps.
- **`StaffElement`'s `protected` fields** (`xOffset`, `staffPosition`,
  `dotCount`, `accidental`, `isAccidentalInParentheses`, `direction`) — no
  test assigns to them directly (all access is through public getters/setters,
  confirmed via search), so not test-only. But the one subclass,
  `StructuralElement`, never reads or writes them directly either — its
  overrides either delegate to `super` or return a computed/constant value.
  `protected` currently buys nothing for subclassing or testing; looks like a
  vestige worth a look when this area is next touched.

---

## Summary

| Category | Count | Packages |
|---|---:|---|
| §A Genuinely test-only (new) | ~55 members across 15 clusters | dom, io, midi, layout, ui root, ui/component, ui/dialog, ui/edit+menu+playback, ui/selection, undo, message, smufl, engraving, error, e2e |
| §B Misnamed internal API | 11 | midi (8), ui/component (5, one overlapping count above) |
| §C Lifecycle (new gaps) | 9 classes/clusters | ui root, ui/component, ui/edit, ui/playback, util, message, lifecycle, ui/dialog (×2) |
| §D Reflection | 3 instances (1 already known) | ui/selection, ui/edit, message |
| §E Dead code | 3 | ui/action, ui/component (×2) |
| §F Borderline (same-class production caller) | 8 | ui/selection, layout, ui root (×3), ui/renderer, ui/component (×7 constants) |
| §G Already known, deferred | 10 members/clusters | ui/dialog (×3), ui/component (×2), prefs (×3), error |
| §H Phase 9 verified landed | 2 renames + spot-check | prefs, smufl, ui/action, ui/selection |

Clean sweeps with **no findings in any category**: `ui/renderer`
(30 production classes, 30 test files — every candidate had a genuine
production caller).

`ui/component`, `ui/dialog`, and `ui/edit`+`playback` account for the large
majority of §A/§C findings — consistent with the rollout plan's own framing of
`ui/dialog` as needing an architectural track (D2, D4) and the "Remaining
phases" table sizing `ui/component` for its own sub-split.
