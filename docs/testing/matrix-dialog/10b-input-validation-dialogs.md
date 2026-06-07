### 10B — Input & Validation Dialogs

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| AttachmentDialog | `getData()` — when `selectedElement` is null, fetches selected element + line from score; when already set (e.g. `showForElement`), skips fetch | unit | none | missing | Write unit test: mock `requireScoreView()` chain; verify `selectedElement`/`selectedLine` are set from selection on first call, left intact on second call | ✅ |
| AttachmentDialog | `getData()` — `adding` flag correctly derived from `getExistingChange` returning null vs non-null; `removeButton` visibility toggled; `okButton` text switched between Add and Modify | unit | none | missing | Write unit test: stub `getExistingChange` returning null/non-null; assert button text and `removeButton` visibility | ✅ |
| AttachmentDialog | `getData()` — returns `true` unconditionally (never cancels dialog) | unit | none | missing | Trivially verifiable; include in the above test | ✅ |
| AttachmentDialog | `setData()` — wraps `applyChange` in `line.withModification` → `line.modifyElement` on the correct element index | unit | none | missing | Write unit test: stub line + element; verify `modifyElement` called with correct index and `ElementField` | ✅ |
| AttachmentDialog | Remove button action — calls `clearChange` inside `withModification` on correct index and hides dialog | unit | none | missing | Write unit test: fire the remove action listener; verify `clearChange` invoked via mutation and dialog hidden | ✅ |
| AttachmentDialog | `setData()`/remove button — throws `IllegalStateException` when `element` or `line` is null | unit | none | missing | Write unit test verifying the guard | ✅ |
| AnnotationDialog | `populateControls(null)` — defaults to `DEFAULT_ANNOTATION` text, left alignment, above position | unit | none | missing | Write unit test: call `populateControls(null)`; assert combo text and radio selections | ✅ |
| AnnotationDialog | `populateControls(existing)` — correctly maps `CENTER_ALIGNMENT` → centerRadio, `RIGHT_ALIGNMENT` → rightRadio, other → leftRadio; `yPosPx < 0` → aboveRadio else belowRadio | unit | none | missing | Write unit test with three alignment values and two yPosPx values | ✅ |
| AnnotationDialog | `applyChange` — empty/null annotation text removes existing attachment if present, and is a no-op if absent | unit | none | missing | Write unit test: stub `findAttachment` returning non-null; verify `removeAttachment` called when text empty | ✅ |
| AnnotationDialog | `applyChange` — builds `Annotation` with correct alignment float from radio selection and sets `yPosPx` to `ABOVE` or `BELOW` | unit | none | missing | Write unit test for each radio combination; assert annotation fields | ✅ |
| AnnotationDialog | `applyChange` — updates existing attachment vs adds new one | unit | none | missing | Test both branches: stub findAttachment returning existing vs null | ✅ |
| AnnotationDialog | `clearChange` — removes `AnnotationAttachment` if present; no-op if absent | unit | none | missing | Write unit test for both branches | ✅ |
| BeatChangeDialog | `populateControls(null)` — defaults to `CROTCHET_DOTTED` for duration and `CROTCHET` for beat | unit | none | missing | Write unit test: call with null; assert combo selections | ✅ |
| BeatChangeDialog | `populateControls(existing)` — sets both combos from `BeatChange.duration()` and `BeatChange.beat()` | unit | none | missing | Write unit test with a real `BeatChange` | ✅ |
| BeatChangeDialog | `applyChange` — skips mutation if either combo returns null | unit | none | missing | Write unit test: stub null return; verify neither `addAttachment` nor `setBeatChange` called | ✅ |
| BeatChangeDialog | `applyChange` — updates existing `BeatChangeAttachment` vs adds new one | unit | none | missing | Test both branches of findAttachment | ✅ |
| BeatChangeDialog | `clearChange` — removes attachment if present; no-op if absent | unit | none | missing | Same pattern as Annotation | ✅ |
| KeySignatureChangeDialog | `getData()` — pre-populates label from `indexOfLine + 1`, combo from `line.getKeyType()`, spinner from `line.getKeyAccidentalCount()` | unit | none | missing | Write unit test: mock score/song/line; assert label text and control values | ✅ |
| KeySignatureChangeDialog | `setData()` — skips post if `keysCombo.getSelectedItem()` is null | unit | none | missing | Write unit test: force combo to null; verify no post | ✅ |
| KeySignatureChangeDialog | `setData()` — posts `KeySignatureDidChangeNotification` with selected key type and spinner integer value | unit | none | missing | Write unit test: set known values; verify notification posted with correct fields | ✅ |
| TempoChangeDialog | `populateControls(null)` — default Tempo: BPM=120, `CROTCHET`, "Moderate", showTempo=true | unit | none | missing | Write unit test: call with null; assert `TempoSection.setTempo` arg fields | ✅ |
| TempoChangeDialog | `populateControls(existing)` — forwards existing attachment's Tempo to `TempoSection.setTempo` | unit | none | missing | Write unit test with a real attachment | ✅ |
| TempoChangeDialog | `applyChange` — builds `Tempo` from `TempoSection` getters; `showTempo = !isShowOnlyDescription()` | unit | none | missing | Write unit test; verify Tempo construction and flag inversion | ✅ |
| TempoChangeDialog | `applyChange` — updates existing attachment vs adds new one | unit | none | missing | Test both branches | ✅ |
| TempoChangeDialog | `clearChange` — removes attachment, then calls `clearTempoIfOrphaned` | unit | none | missing | Write unit test: verify both `removeAttachment` and `clearTempoIfOrphaned` called | ⬜ |
| TempoChangeDialog | `showForElement` — static factory pre-sets `selectedElement`/`selectedLine` before showing | unit | none | missing | Write unit test verifying fields are set correctly (widen to package-private if needed) | ⬜ |
| ResolutionDialog | `handleResolutionChange()` — width = `round(scale * sheetWidthPx) + border.width`; scale = `resolution / screenDpi` | unit | none | missing | Write pure-logic unit test: inject known sheetWidthPx and mock `getDpi()`; assert widthField text | ⬜ |
| ResolutionDialog | `handleResolutionChange()` — height subtracts `sheetHeightWithoutLyricsPx` when `withoutLyricsCheck` selected | unit | none | missing | Test with checkbox selected vs deselected; assert heightField text | ⬜ |
| ResolutionDialog | `handleResolutionChange()` — height subtracts `sheetHeightWithoutTitlePx` when `exportWithoutTitleCheckBox` selected | unit | none | missing | Same pattern for title checkbox | ⬜ |
| ResolutionDialog | `handleResolutionChange()` — both deductions can combine additively | unit | none | missing | Test with both checked | ⬜ |
| ResolutionDialog | `getData()` — `withoutLyricsCheck` disabled (and deselected) when both lyrics collections empty | unit | none | missing | Mock song with empty lyrics; assert disabled state | ⬜ |
| ResolutionDialog | `getData()` — `exportWithoutTitleCheckBox` disabled (and deselected) when title is empty | unit | none | missing | Mock song with empty title; assert disabled state | ⬜ |
| ResolutionDialog | `getData()` — resets `approved = false` on each show | unit | none | missing | Verify approved is false before `setData` runs | ⬜ |
| ResolutionDialog | `setData()` — sets `approved = true` and persists DPI to `Prefs` | unit | none | missing | Write unit test: mock `Prefs`; verify put and approved flag | ⬜ |
| ResolutionDialog | `isApproved()` / `getResolution()` / `isWithoutLyrics()` / `isWithoutTitle()` / `getBorder()` — simple state accessors | none | none | adequate | No test needed — trivial getters | — |
| FontDialog | `getData()` — passes `selectedFont` to `chooser.setSelectedFont` | unit | none | missing | Write unit test: set initial font; call getData; assert chooser.getSelectedFont equals it | ⬜ |
| FontDialog | `setData()` — harvests `chooser.getSelectedFont()` into `selectedFont` | unit | none | missing | Write unit test: set chooser font; call setData; assert getSelectedFont() | ⬜ |
| FontDialog | `showDialog` — returns `selectedFont` unchanged when dialog is cancelled (setData not called) | unit | none | missing | Verify font remains initial value when OK is not pressed | ⬜ |
| FontDialog | `getExtraHeight()` returns `EXTRA_PREVIEW_HEIGHT` constant; `isResizable()` returns true | none | none | adequate | Pure display/layout wiring | — |

**Notes:**

Zero tests exist for any of the seven classes in this slice. The only tests in `src/test/java/songscribe/ui/dialog/` cover `BaseDialog` infrastructure (counter, position, geometry persistence) — the concrete dialog classes are untouched.

**Key gaps by priority:**

1. `ResolutionDialog.handleResolutionChange()` is the richest pure-logic target: it performs floating-point scale multiplication and pixel arithmetic with two independent boolean flags; four distinct test cases cover the cross-product of the checkbox flags. The `stateChanged` listener delegates directly to this method, making it straightforwardly testable by calling `handleResolutionChange()` with known field state.

2. `AttachmentDialog` is the abstract base for four concrete dialogs; its `getData()` add/modify branching (button text, removeButton visibility) and `setData()` mutation delegation are shared risks. Testing this base class with a minimal concrete subclass stub covers the shared plumbing once.

3. `AnnotationDialog.populateControls` and `applyChange` have three-way alignment branching and sign-based above/below selection — exactly the kind of branching mutation testing would kill.

4. `KeySignatureChangeDialog.setData()` posts a `KeySignatureDidChangeNotification`; the null-guard on `keysCombo.getSelectedItem()` is a silent no-op that could mask bugs.

5. `TempoChangeDialog.clearChange` has a two-step side effect: `removeAttachment` then `clearTempoIfOrphaned`; both steps must be verified together.

**Production observation (do not fix here):** `KeySignatureChangeDialog` constructs its own button panel by manually adding `okButton`/`cancelButton` to a `JPanel` inside `contentPanel` instead of using `modifyButtonPanel()` — this is a divergence from the `StandardDialog` convention documented in `dialogs.md` and may mean the button panel is not attached via the standard `BorderLayout.SOUTH` constraint.

