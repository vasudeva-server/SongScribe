### 10C — Settings, Export & Informational Dialogs

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| PreferencesDialog | `programToIndex` — linear scan: returns 0 on miss, first matching index otherwise | unit | none | missing | Add `PreferencesDialogTest` testing: exact match, miss→0, first-of-duplicates | ⬜ |
| PreferencesDialog | `ensureInstrumentsLoaded` / `instrumentsLoaded` guard — loads only once, sorted by name | unit | none | missing | Add tests for idempotency and alphabetic sort | ⬜ |
| PreferencesDialog | `PlayTab.volumeToSliderIndex` — nearest-stop snap with tie-breaking | unit | none | missing | Add tests for each exact stop, midpoints, and values outside range (e.g. 0, 127) | ⬜ |
| PreferencesDialog | `GeneralTab`/`PlayTab`/`InstrumentsTab` getData/setData — pure Prefs read/write wiring, no branching | none | — | — | No test warranted (trivial field read from Prefs → component) | — |
| PreferencesDialog | Live preference writes via ActionListeners (page size, metric, appearance, startup action) — fire directly on radio click, no OK button | none | — | — | No test warranted (framework ActionListener wiring) | — |
| SongSettingsDialog | `TextTab.setData` — change-detection: skips `MetadataDidChangeNotification` when no field changed | unit | none | missing | Add test: setData with unchanged fields posts nothing; changed fields post notification | ⬜ |
| SongSettingsDialog | `TextTab.setData` — number/year `Integer.parseInt` validation: null set on `NumberFormatException` | unit | none | missing | Add tests: non-numeric number/year, empty (valid), numeric (valid) | ⬜ |
| SongSettingsDialog | `TextTab.isValidData` — delegates to `NonEmptyGuard.validate()` for title and attribution | unit | none | missing | Add tests: empty title → false, non-empty → true (suppressed OptionDialogs) | ⬜ |
| SongSettingsDialog | `TextTab.TakeFirstLyricsWordAction` — word extraction, capitalisation, hyphen handling, boundary trim | unit | none | missing | Add tests: normal lyrics, leading spaces, lyrics with hyphens, all-underscore lyrics (empty buffer → IOOBE bug) | ⬜ |
| SongSettingsDialog | `TextTab.AddDateAndPlaceAction` — date-string appended to attribution; empty attribution → `charAt(-1)` crash | unit | none | missing | Add tests: empty attribution (exposes IOOBE), attribution ending in `\n`, attribution not ending in `\n`; also year-required and place-required paths | ⬜ |
| SongSettingsDialog | `TextTab.getDateString` — format with month+day, month only, year only, empty year→"" | unit | none | missing | Add tests for all branches | ⬜ |
| SongSettingsDialog | `MusicTab.validateLineWidth` — parses double, converts metric↔inches, returns -1 on unparseable/out-of-range | unit | none | missing | Add tests: empty, non-numeric, below min, above max, valid inches, valid cm | ⬜ |
| SongSettingsDialog | `MusicTab.setKeyComboFromSong` — canonicalizes `(SHARPS, 0)` → `(FLATS, 0)` | unit | none | missing | Add test: song with 0 sharps maps to `(FLATS, 0)` selection | ⬜ |
| SongSettingsDialog | `MusicTab.setData` — tempo/key change-detection: posts only changed notifications inside single `withModification` bracket | unit | `SongMetadataDialogFlowTest` (bracketing pattern only; does NOT cover tempo/key) | missing | Add test: no-change → no message; tempo-only → one TempoDidChangeNotification; key-only; both → coalesced | ⬜ |
| SongSettingsDialog | `KeyCellRenderer.SELECTIONS` list — exactly 15 entries (no-accidentals + 7 flats + 7 sharps), in canonical order | unit | none | missing | Add test for list size and order | ⬜ |
| SongSettingsDialog | `FontTab.getData`/`setData`/`applyDefaultFonts` — pure display font assignment, no branching logic | none | — | — | No test warranted | — |
| SongSettingsDialog | Tabbed dialog pane built with `createTabbedPane()`, not `new JTabbedPane()` | none | — | — | No test warranted (structural/wiring) | — |
| ExportMidiDialog | `setData` — saves/restores playback settings around export; builds sequence with override instrument/tempo/repeats | unit | none | missing | Add test (mock `PlaybackController`, `requireScoreView`) verifying settings are restored even on exception | ⬜ |
| ExportMidiDialog | `getData` — loads instrument index from Prefs via `programToIndex`, loads `PLAY_WITH_REPEATS` pref | none | — | — | No test warranted (trivial pref read → component) | — |
| ExportPDFDialog | `getData`/`setData`/`getPaperSizeData` — delegates entirely to `PaperSizeStep`; `getPaperSizeData` is `@Nullable` until OK clicked | unit | none | missing | Add test: `getPaperSizeData` is null before setData, non-null after | ⬜ |
| PlatformFileDialog | `convertFilter` — strips ` (ext1, ext2)` suffix from description; no paren → unchanged | unit | none | missing | Add tests: description with paren, without paren, paren at index 0 | ⬜ |
| PlatformFileDialog | `getFileFilter` — extension-based lookup (higher priority) vs dropdown-based lookup vs fallback to first filter | unit | none | missing | Add tests: filename matches ext → returns matching filter; filename matches nothing → returns dropdown match; dropdown also no match → returns first | ⬜ |
| PlatformFileDialog | `showSaveDialog` (static) — appends first extension when no existing extension matches; handles leading-dot form | unit | none | missing | Add tests: already has matching ext, has no ext, leading-dot extension form, multi-extension array | ⬜ |
| PlatformFileDialog | Constructor overload initialFilterIndex clamping — `Math.clamp(initialFilterIndex, 0, len-1)` | unit | none | missing | Add test: negative index, over-length index, valid index | ⬜ |
| ProgressBarDialog | `nextValue(int)` — increments bar value by delta; `nextValue()` delegates to `nextValue(1)` | none | — | — | No test warranted (trivial delegation to JProgressBar, no branching) | — |
| DoNotShowMessage | `setVisible(true)` — suppresses show when `java.util.prefs` node already has `propName=true` | unit | none | missing | Add test: prefs not set → `super.setVisible(true)` called; prefs set → suppressed | ⬜ |
| DoNotShowMessage | `setData` — persists `propName=true` only if checkbox is selected | unit | none | missing | Add tests: checkbox checked → pref written; unchecked → pref not written | ⬜ |
| DoNotShowMessage | Hardcoded checkbox label `"Don't show this message again."` — bypasses Strings system | none | — | — | Production observation: not a test gap, but violates Strings convention (note only) | — |
| AboutDialog | Pure display/wiring, no branching logic | none | — | — | No test warranted | — |
| HelpDialog | Pure display/wiring (addToList, list→HTML load on selection); IO error path is framework-delegated | none | — | — | No test warranted | — |
| HTMLDialog | Pure display/wiring | none | — | — | No test warranted | — |
| KeyMapDialog | Pure display/wiring (subclass of HTMLDialog) | none | — | — | No test warranted | — |
| ReportBugDialog | Email URI construction — bug vs. feature-request branch, log file attachment conditional, version/OS interpolation | none | — | — | No test warranted (launches external URI via Desktop.mail; not unit-testable without significant mocking) | — |
| TutorialDialog | Pure display/wiring (subclass of HelpDialog) | none | — | — | No test warranted | — |
| WhatsNewDialog | `getData` returns `false` (suppresses show) when release-notes file is absent | none | — | — | No test warranted (depends on classpath resource presence; not worth mocking) | — |

#### Notes

**Key gaps.** All fourteen classes in this slice have zero test coverage at both unit and e2e level. The highest-value gaps are:

1. **`SongSettingsDialog.TextTab`** contains two crash-risk production bugs: `TakeFirstLyricsWordAction` calls `words.charAt(words.length() - 1)` without an empty-buffer guard (throws `StringIndexOutOfBoundsException` when lyrics contain only separators); `AddDateAndPlaceAction` calls `attribution.charAt(attribution.length() - 1)` without an empty-attribution guard. Both are caught by unit tests before any fix is written.

2. **`PlatformFileDialog.getFileFilter`** has a two-path disambiguation algorithm (extension-based vs dropdown) with a fallback that is entirely untested.

3. **`PreferencesDialog.programToIndex`** and **`PlayTab.volumeToSliderIndex`** are static pure-logic methods exposed as `public`/`package-private` that can be tested directly without any UI setup.

4. **`DoNotShowMessage`** uses `java.util.prefs.Preferences` directly (bypasses the project's `Prefs` wrapper) and has a hardcoded checkbox label `"Don't show this message again."` (violates the Strings convention). The suppression logic (`setVisible`) is the one real branching behavior worth a unit test.

5. **`SongMetadataDialogFlowTest`** covers the `Song.metadataDidChange` bracketing contract (relevant to `TextTab.setData`) but does NOT cover the `MusicTab.setData` tempo/key change-detection or the `TextTab` validation/boundary paths — those remain missing.

**Existing tests in `src/test/java/songscribe/ui/dialog/`** (`BaseDialogCounterTest`, `BaseDialogPositionTest`) cover `BaseDialog` infrastructure only; none touch any class in this slice.

