### 10A — Dialog Infrastructure & Lifecycle

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| `BaseDialog` | `isAnyBlockingDialogVisible()` false initially, true after open, false after close | unit | `BaseDialogCounterTest.testIsAnyBlockingDialogVisible*` | adequate | — | — |
| `BaseDialog` | Nested blocking dialogs: counter tracks all levels; stays true until all closed | unit | `BaseDialogCounterTest.testNestedDialogsCounterTracksAllLevels` | adequate | — | — |
| `BaseDialog` | `INFORMATIONAL` category does not increment/decrement counter | unit | `BaseDialogCounterTest.testInformationalDialog*`, `testMixedDialogs*` | adequate | — | — |
| `BaseDialog` | `EXCLUSIVE` category is blocking (counter increments/decrements) | unit | none — `TestDialog` uses default `OPERATIONAL`; `EXCLUSIVE` is never exercised | missing | Add test asserting `EXCLUSIVE` dialog increments counter | ✅ |
| `BaseDialog` | `DialogVisibilityDidChangeNotification(true)` posted on 0→1 transition only | unit | `BaseDialogCounterTest.NotificationTransitionTests.testNotification*` | adequate | — | — |
| `BaseDialog` | `DialogVisibilityDidChangeNotification(false)` posted on 1→0 transition only | unit | `BaseDialogCounterTest.NotificationTransitionTests.testNotification*` | adequate | — | — |
| `BaseDialog` | No notification posted for `INFORMATIONAL` open/close | unit | `BaseDialogCounterTest.InformationalNotificationTests.testInformationalDialogDoesNotPostNotification` | adequate | — | — |
| `BaseDialog` | `getData()` returning false cancels show (dialog disposed, not shown, counter not incremented) | unit | none | missing | Add test: override `getData()` → false; assert `isAnyBlockingDialogVisible()` is false and `JDialog.setVisible(true)` never called | ✅ |
| `BaseDialog` | Tab iteration in `getData()`: first tab returning false short-circuits (later tabs not called) | unit | none | missing | Add test with two tabs where tab[0] returns false; assert tab[1].getData not called | ✅ |
| `BaseDialog` | First open uses default position (`UIUtils.positionDialog`) | unit | `BaseDialogPositionTest.testFirstOpenUsesDefaultPosition` | adequate | — | — |
| `BaseDialog` | Second open after close restores saved location (no `positionDialog`) | unit | `BaseDialogPositionTest.testSecondOpenRestoresSavedLocation` | adequate | — | — |
| `BaseDialog` | Position not restored if first instance was never closed | unit | `BaseDialogPositionTest.testPositionNotRestoredIfNeverClosed` | adequate | — | — |
| `BaseDialog` | Distinct dialog classes have independent saved positions | unit | `BaseDialogPositionTest.testDistinctClassesHaveIndependentPositions` | adequate | — | — |
| `BaseDialog` | Non-resizable close: saves `x`/`y` only (no `width`/`height`) | unit | `BaseDialogPositionTest.GeometryPersistence.testPersistOnCloseNonResizable` | adequate | — | — |
| `BaseDialog` | Resizable close: saves `x`, `y`, `width`, `height` | unit | `BaseDialogPositionTest.GeometryPersistence.testPersistOnCloseResizable` | adequate | — | — |
| `BaseDialog` | `loadGeometryFromPrefs`: restores location from prefs on first open | unit | `BaseDialogPositionTest.GeometryPersistence.testRestoreFromPrefs` | adequate | — | — |
| `BaseDialog` | `loadGeometryFromPrefs`: empty prefs key → falls back to default position | unit | `BaseDialogPositionTest.GeometryPersistence.testMissingKeyFallsBackToDefaultPosition` | adequate | — | — |
| `BaseDialog` | `loadGeometryFromPrefs`: entry present but not a `Map<?,?>` → falls back (malformed prefs) | unit | none | missing | Add test: prefs entry is a `String`; assert `positionDialog` still called | ✅ |
| `BaseDialog` | `loadGeometryFromPrefs`: entry is a map but x/y are non-`Number` → falls back | unit | none | missing | Add test: prefs map has `x`="bad"; assert `positionDialog` still called | ✅ |
| `BaseDialog` | `applyGeometry` resizable floor semantics: restored size clamped to `max(packed, restored)` per dimension | unit | none | missing | Add test: packed=300×200, restored=200×400 → applied width=300, height=400 | ✅ |
| `BaseDialog` | `applyGeometry` resizable: restores location+size (calls `setBounds`, not `setLocation`) | unit | none | missing | Add test verifying `setBounds` called with floor'd dimensions when dialog is resizable | ✅ |
| `BaseDialog` | `GeometryResetSubscriber`: `PrefsDidChangeNotification` with key `DIALOG_GEOMETRY` clears `SAVED_GEOMETRY` | unit | none | missing | Add test: save geometry, post notification with `DIALOG_GEOMETRY` key, reopen → `positionDialog` called again | ✅ |
| `BaseDialog` | `GeometryResetSubscriber`: `PrefsDidChangeNotification` with key `ALL` clears `SAVED_GEOMETRY` | unit | none | missing | Add test: same as above with `ALL` key | ✅ |
| `BaseDialog` | `createTabbedPane`: first call registers top-level pane + lifecycle listener; second call returns new pane without overwriting | unit | none | missing | Add test: call twice; assert `tabbedPane` field holds first-call instance | ✅ |
| `BaseDialog` | `tabWillShow`/`tabWillHide` fired on tab switch via `ChangeListener` | unit | none | missing | Add test with two tabs; simulate selection change; assert correct callbacks fired | ✅ |
| `BaseDialog` | `tabWillShow` fired for initially-selected tab on `setVisible(true)` | unit | none | missing | Add test | ✅ |
| `BaseDialog` | `tabWillHide` called for all tabs on `setVisible(false)` | unit | none | missing | Add test | ✅ |
| `BaseDialog` | `getContentPaddingKey`: returns buttons-padding key when `hasButtons()` true, std-padding key when false | unit | none | missing | Add test on concrete subclass pairs | ✅ |
| `BaseDialog` | `getScoreView()` returns null when scoreView not initialized (nullable contract) | unit | none | missing | Add test: mock `mainFrame.getScoreView()` → null; assert returns null | ✅ |
| `BaseDialog` | `requireScoreView()` throws when scoreView null (`RuntimeError.exit`) | unit | none | missing | Add test: mock `mainFrame.requireScoreView()` → throw; assert propagates | ✅ |
| `BaseDialog` | `getSong()` delegates to `requireScoreView().getSong()` | unit | none | missing | Add test | ✅ |
| `BaseDialog` (inner `Tab`) | `build()` appends fill-glue unless `addExpanding` called first (`hasFillItem`) | none | — | — | Pure layout wiring | — |
| `BaseDialog` (inner `Tab`) | `Tab.getData()` returns true by default (no branching, override hook only) | none | — | — | Trivial default; only testable behavior is in overrides | — |
| `BaseDialog` (inner `TitledSection`) | `addSeparator()` axis dispatch (Y→vertical strut, X→horizontal strut) | unit | none | missing | Add test: construct X-axis and Y-axis sections; call addSeparator; verify layout component added | ✅ |
| `StandardDialog` | OK click: `isValidData()` false → `setData()` not called, dialog stays open | unit | none | missing | Add test: override `isValidData()` → false; click OK; assert `setData` not called and dialog still visible | ✅ |
| `StandardDialog` | OK click: `isValidData()` true → `setData()` called, then `setVisible(false)` | unit | none | missing | Add test | ✅ |
| `StandardDialog` | Cancel click: `setVisible(false)` without calling `setData()` | unit | none | missing | Add test | ✅ |
| `StandardDialog` | `modifyButtonPanel` called exactly once on first `setVisible(true)` (once-only guard via `buttonPanelAttached`) | unit | none | missing | Add test: open twice; assert `modifyButtonPanel` called once (spy subclass) | ✅ |
| `StandardDialog` | `isValidData()` iterates tabs: first failing tab short-circuits | unit | none | missing | Add test with two tabs where tab[0] returns invalid | ✅ |
| `StandardDialog` | `setData()` iterates all registered tabs | unit | none | missing | Add test | ✅ |
| `StandardDialog` | `repaintScore()` null-safe: no-op when `getScoreView()` returns null | unit | none | missing | Add test: mock scoreView null; click OK with valid data; assert no NPE | ✅ |
| `DialogCategory` | `isBlocking()` true for `EXCLUSIVE` and `OPERATIONAL`, false for `INFORMATIONAL` | unit | Indirectly via counter tests (`INFORMATIONAL` + `OPERATIONAL`); `EXCLUSIVE` never tested directly | inadequate | Add direct `isBlocking()` enum test covering all three constants | ✅ |
| `DialogGeometry` | Pure data record, no logic | none | — | — | — | — |
| `PropertiesStateStore` | `put(key, null)` calls `prefs.remove(key)` instead of putting null | unit | none | missing | Add test: call `put("k", null)`; verify `prefs.remove("k")` called (mock `Preferences`) | ✅ |
| `PropertiesStateStore` | `put(key, value)` with non-null calls `prefs.put(key, value)` | unit | none | missing | Add test | ✅ |
| `PropertiesStateStore` | `get(key, def)` delegates to `prefs.get(key, def)` | none | — | — | Trivial delegation, no logic | — |
| `Step` | Pure container: `getInfo()` returns null, `start()`/`end()` are no-ops | none | — | — | No logic in base class | — |
| `PaperSizeStep` | `getValueInPixels`: converts spinner double value to pixels using current unit | unit | `PaperSizeStepTest.testGetValueInPixelsConvertsInchesToPixelsUsingCurrentUnit` | missing | Add test: set unit to INCH, set spinner to 8.5; assert pixels match `Unit.INCH.convertToPixels(8.5)` | ✅ |
| `PaperSizeStep` | Unit switch (INCH→CM): scales all spinner values by `MM_PER_IN` multiplier | unit | `PaperSizeStepTest.testUnitSwitchInchToCmScalesAllSpinnersBy25_4` | missing | Add test: set to INCH with value 1.0; switch to CM; assert spinner values ≈ 25.4 | ✅ |
| `PaperSizeStep` | Unit switch (CM→INCH): scales values by `1/MM_PER_IN` | unit | `PaperSizeStepTest.testUnitSwitchCmToInchScalesAllSpinnersByInverseOf25_4` | missing | Add test | ✅ |
| `PaperSizeStep` | `TemplateObject` parsing: splits on `;`, assigns name/width/height/margin/unit/metric | unit | `PaperSizeStepTest.TemplateObjectParsing.testFullLineAssignsAllFields` | missing | Add test: parse a template line; assert all fields | ✅ |
| `PaperSizeStep` | `TemplateObject` parsing: partial line (fewer than 6 fields) uses defaults | unit | `PaperSizeStepTest.TemplateObjectParsing.testPartialLineFewerThanSixFieldsUsesDefaults` | missing | Add test | ✅ |
| `PaperSizeStep` | Template selection populates all six spinners with template values | unit | `PaperSizeStepTest.testTemplateSelectionPopulatesAllSixSpinners` | missing | Add test | ✅ |
| `PaperSizeStep` | `end()` writes all six pixel values + `mirrored` flag to `pageLayoutData` | unit | `PaperSizeStepTest.testEndWritesAllSixPixelValuesAndMirroredFlagToPageLayoutData` | missing | Add test: set up spinners; call `end()`; assert `pageLayoutData` fields | ✅ |
| `PaperSizeStep` | `setValues()` round-trip: pixel values converted to current unit for display | unit | none | missing | Add test: call `setValues` with known pixel values; assert spinner values match conversion | ✅ |
| `PaperSizeStep` | `MirroredAction`: labels switch between Left/Inner and Right/Outer | unit | none | missing | Add test: toggle checkbox; assert label text | ✅ |
| `PaperSizeStep` | `start()` selects first template matching metric pref | unit | none | missing | Add test: set pref METRIC=false; call `start()`; assert selected template is imperial | ✅ |
| `TempoSection` | `setTempo`/getters round-trip: all four controls reflect passed `Tempo` | unit | none | missing | Add test: call `setTempo(t)`; assert `getTempoType`, `getVisibleTempo`, `getTempoDescription`, `isShowOnlyDescription` | ✅ |
| `TempoSection` | `getTempoType()` throws `IllegalStateException` when combo selection is null | unit | none | missing | Add test: clear combo selection; assert ISE thrown | ✅ |
| `TempoSection` | `getTempoDescription()` returns empty string when combo selection is null | unit | none | missing | Add test: clear combo; assert returns `""` | ✅ |

**Notes:**

The blocking-counter logic in `BaseDialog` is thoroughly tested (13 tests across `BaseDialogCounterTest`), and geometry persistence (save + restore from static map/prefs) is well covered in `BaseDialogPositionTest`. However, the critical `getData()` cancellation path — which prevents `setVisible(true)` from proceeding and is the primary lifecycle gate — has zero test coverage, as does all of `StandardDialog`'s OK/Cancel/validation lifecycle. These are the highest-priority gaps: they guard data integrity on every dialog commit.

The `GeometryResetSubscriber` (`PrefsDidChangeNotification` → `SAVED_GEOMETRY.clear()`) is never tested; it is the mechanism that allows geometry to be reset from Preferences, and the subscriber is wired in a static initializer making it easy to miss.

The `applyGeometry` floor semantics for resizable dialogs (`Math.max(packedSize, restoredSize)`) are untested — the existing `TestResizableDialog` is used only to verify that size keys are written, not that the restore correctly applies the floor.

`DialogCategory.isBlocking()` is exercised indirectly (INFORMATIONAL + OPERATIONAL paths) but `EXCLUSIVE` is never instantiated in any test, leaving a gap in the enum coverage.

`PaperSizeStep` and `TempoSection` have zero test coverage despite carrying genuine computation logic (unit conversion, spinner round-trips, template parsing). `PropertiesStateStore`'s null-remove branch is also untested.

**Production observation:** `PaperSizeStep` uses a raw `new Insets(5, 5, 0, 5)` magic number directly in GridBagConstraints rather than a FlatLaf prop or named constant — this violates the no-magic-numbers rule.

