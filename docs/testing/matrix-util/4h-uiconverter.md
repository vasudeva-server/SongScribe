### 4H. `uiconverter`

Audited by reading all three production class bodies; `ChooseDirectoryAction` is pure Swing wiring with no logic, but `UIConverter.isLegalFileName` is a pure predicate and `ConvertAction.ConvertThread` contains an image-scale formula that is unit-testable in isolation.

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| `UIConverter` | `isLegalFileName`: rejects names shorter than 10 chars | unit | none | missing | add unit: names of length 9 or less must return false | ⬜ |
| `UIConverter` | `isLegalFileName`: rejects name that does not end with `.mssw` | unit | none | missing | add unit: name with wrong extension returns false | ⬜ |
| `UIConverter` | `isLegalFileName`: rejects name whose first three chars are not all digits | unit | none | missing | add unit: each non-digit position in chars 0-2 returns false | ⬜ |
| `UIConverter` | `isLegalFileName`: accepts space separator at char 3 | unit | none | missing | add unit: `"001 Title.mssw"` returns true | ⬜ |
| `UIConverter` | `isLegalFileName`: accepts dash separator at char 3 | unit | none | missing | add unit: `"001-Title.mssw"` returns true | ⬜ |
| `UIConverter` | `isLegalFileName`: rejects any other char 3 (e.g. `'a'`) | unit | none | missing | add unit: `"001aTitl.mssw"` (length >= 10) returns false | ⬜ |
| `UIConverter` | `isLegalFileName`: boundary — exactly 10-char valid name accepted | unit | none | missing | add unit: `"001 a.mssw"` (length=10) returns true | ⬜ |
| `UIConverter` | `main`: public static entry point; called from `SongScribe.main` | none | none | — | entry point; not dead; no unit test warranted (Swing bootstrap) | — |
| `ConvertAction` | image scale formula: `scale = (IMAGE_WIDTH[i] - 2*LEFT_RIGHT_MARGIN[i]) / sheetWidthPx` | unit | none | missing | add unit with known IMAGE_WIDTH, LEFT_RIGHT_MARGIN, and sheetWidthPx; assert exact double scale | ⬜ |
| `ConvertAction` | `actionPerformed`: empty directory text → error path (no crash, no conversion) | unit | none | missing | add unit: mock `songsDirectory.getText()` returning empty string; verify early return (or via public observable) | ⬜ |
| `ConvertAction` | `actionPerformed`: non-existent directory → error path | unit | none | missing | add unit: supply non-existent path; verify early return | ⬜ |
| `ConvertAction` | `actionPerformed`: directory with zero legal files → error path | unit | none | missing | add unit: real temp directory with no `.mssw` files; verify early return | ⬜ |
| `ConvertAction` | `ConvertThread.run`: full batch conversion (file I/O, ScoreView, MIDI, image write) | e2e | none | missing | reserve for e2e; requires real Swing + file I/O pipeline — too costly to mock completely | ⬜ |
| `ChooseDirectoryAction` | `actionPerformed`: fires `DIRECTORY_CHANGE_PROPERTY` when dialog confirms | none | none | — | pure Swing event dispatch; no computation; not warranted | — |
| `UIConverter/DirectorySelectionChangeListener` | `handleDirectoryChange`: null listFiles → error path | none | none | — | Swing state mutation (table model, text field) — risk is wiring, not logic; none | — |
| `UIConverter/DirectorySelectionChangeListener` | `handleDirectoryChange`: populates accepted/rejected tables per `isLegalFileName` | none | none | — | file-enumeration result depends on isLegalFileName (already unit-tested above) and Swing model mutation; none | — |
| `UIConverter/NumberSongAction` | `handleNumberSong`: null input (user cancels dialog) → returns silently | none | none | — | Swing dialog interaction; none | — |
| `UIConverter/NumberSongAction` | `handleNumberSong`: invalid number string → NumberFormatException path | none | none | — | Swing dialog interaction; none | — |
| `UIConverter/NumberSongAction` | `handleNumberSong`: out-of-range number (< 1 or > 999) → error path | none | none | — | Swing dialog interaction; none | — |
| `UIConverter/NumberSongAction` | `handleNumberSong`: zero-padding format `%03d` and `isLegalFileName` re-check on renamed file | unit | none | missing | extract `buildNumberedFileName(String baseName, int number)` to a package-private helper; test format and legality | ⬜ |

**4H notes (quality concerns):**

The highest-risk dark gap is `UIConverter.isLegalFileName`: it is called from three sites (directory scan, file filter in ConvertAction, rename validation in NumberSongAction) and has six independent branch conditions, all untested. A single wrong char-index or off-by-one in the length guard would silently accept or reject files at every call site. The image-scale formula in `ConvertAction.ConvertThread` is pure arithmetic (`(IMAGE_WIDTH[i] - 2 * LEFT_RIGHT_MARGIN[i]) / sheetWidthPx`) but is currently private and embedded in a thread body; extracting it to a package-private static would allow a direct unit test without mocking the entire pipeline. The `NumberSongAction.handleNumberSong` zero-padding and re-validation logic is also pure computation that cannot currently be tested without Swing; that logic should be extracted to be testable. The `ConvertThread.run` full-pipeline path (open file → write mssw → produce images → produce MIDI → optional zip) genuinely requires the real ScoreView and file system and warrants a single e2e test for the happy path and the per-file error paths. `ChooseDirectoryAction` and the `DirectorySelectionChangeListener` table-population logic are Swing wiring with no testable computation beyond `isLegalFileName`. `UIConverter.main` is a legitimate entry point (dispatched from `SongScribe.main`) — not dead, and not a candidate for unit testing.

