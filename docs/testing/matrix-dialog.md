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

