## 10. `ui/dialog` (audited 2026-05-22)

- [10A — Dialog Infrastructure & Lifecycle](10a-dialog-infrastructure-lifecycle.md)
- [10B — Input & Validation Dialogs](10b-input-validation-dialogs.md)
- [10C — Settings, Export & Informational Dialogs](10c-settings-export-informational-dialogs.md)
- [10D — Font Chooser Core & Model](10d-font-chooser-core-model.md)
- [10E — Font Chooser Panes & Listeners](10e-font-chooser-panes-listeners.md)

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
