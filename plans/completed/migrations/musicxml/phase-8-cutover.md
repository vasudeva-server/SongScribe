# Sub-plan: Phase 8 — Losslessness Gate & Cutover
**Type:** Sub-plan  
**Parent:** plans/migrations/musicxml/musicxml-conversion.md → Phase 8  
**Created:** 2026-07-06  
**Revised:** 2026-07-07 (review pass)  
**Status:** Complete  
**BlockedBy:** —

* * *
## Purpose
Make MusicXML the canonical on-disk save/open format, with the legacy `.mssw` reader kept as a one-way, read-only import path. On open, files are gated on two axes:

1. **Format/version** — the file must be a `<score-partwise>` document with `version >= 4.0`. A missing/wrong root element or a missing/unparseable/older version is refused as `UnsupportedFileFormat`.
2. **Provenance** — the document's `<software>` encoding tag must identify SongScribe itself. Foreign MusicXML (missing/blank/non-SongScribe `<software>`) is refused as `WrongSoftware` with a clear message.

## Open-dispatch decision tree
```
 file
  │
  ▼  SongFileLoader.load(file)                                    [NEW]
  │   hasExtension(file, …)  (case-insensitive)
  │
  ├─ .mssw ─────────────► SongLoader.load(file)   (unchanged legacy path)
  │                        └─► Success │ IoError │ ParseError │ NewerVersion
  │
  ├─ .musicxml | .xml ──► MusicXmlReader.read(file)
  │      startElement: root ≠ <score-partwise>            ─► UnsupportedFormatException
  │      startElement: version missing/unparseable/<4.0   ─► UnsupportedFormatException
  │      endDocument:  <software> null/blank/¬startsWith(PACKAGE_NAME) ─► ForeignSoftwareException
  │      (otherwise) ─► Success
  │      catch ForeignSoftwareException  ► WrongSoftware(file, software)
  │      catch UnsupportedFormatException ► UnsupportedFileFormat(file, detail)
  │      catch SAXException              ► ParseError(file, e)
  │      catch IOException               ► IoError(file, e)
  │
  └─ else (.pdf, .txt, none, …) ► UnsupportedFileFormat(file, ext)

 ScoreView.openFile result switch (adds two arms):
   Success + .mssw        ► onFileOpened.accept(null)   (untitled ⇒ first Save = Save-As .musicxml)
   Success + .musicxml/.xml ► onFileOpened.accept(file)
   WrongSoftware          ► error dialog(software │ "other notation software"), yield false
   UnsupportedFileFormat  ► error dialog(filename), yield false
   (IoError/ParseError/NewerVersion/LineWidthTooLarge — unchanged)
```

## Implementation Approach
### Key code touchpoints
Read (to model / edit):

- `src/main/java/songscribe/FileExtensions.java:24` — add `MUSICXML = "musicxml"` and `XML = "xml"`.

- `src/main/java/songscribe/Constants.java:28` — reuse existing `PACKAGE_NAME = "SongScribe"` as the provenance prefix (no new constant).

- `src/main/java/songscribe/io/SongLoadResult.java` — sealed `Failure` interface (permits list) + `songOrThrow()`; add `WrongSoftware` and `UnsupportedFileFormat` variants here.

- `src/main/java/songscribe/io/SongLoader.java:50,63-71` — the exception→`SongLoadResult` mapping to mirror in the new dispatcher.

- `src/main/java/songscribe/io/musicxml/MusicXmlReader.java:316-330` (root/version check, `case NONE`), `:373-377` + `:1084-1088` (`Where.SOFTWARE` capture), `read(File)`/`read(InputSource)`, and a new `endDocument()`.

- `src/main/java/songscribe/io/musicxml/MusicXmlWriter.java:316` — `"SongScribe " + Version.PUBLIC_VERSION` → `Constants.PACKAGE_NAME + " " + Version.PUBLIC_VERSION` (identical output).

- `src/main/java/songscribe/io/musicxml/MusicXmlTags.java:36,44,152` — `VERSION_VALUE`, `SCORE_PARTWISE`, `ATTR_VERSION`; add `MIN_SUPPORTED_VERSION = 4.0` (double).

- `src/main/java/songscribe/util/ExtensionFileFilter.java:34` — already varargs `(description, extensions…)`; `PlatformFileDialog` has an `ExtensionFileFilter[]` ctor (`PlatformFileDialog.java:78`).

- `src/main/java/songscribe/util/FileUtils.java` — `getExtension(name)`; add `hasExtension(File, String…)` (case-insensitive).

- `src/main/java/songscribe/io/SongFileWriter.java` (**new, Phase 0**) — owns the `PrintWriter` lifecycle + `checkError()` result for the save path; `MainFrame.saveCurrentFile:985` delegates to it. Writes via `SongIO.writeSong` until Phase 3 swaps in `MusicXmlWriter.writeSong`.

Edit:

- `ScoreView.openFile` `src/main/java/songscribe/ui/component/ScoreView.java:419-476` (`onFileOpened` = `this::setCurrentFile`, `@Nullable`-safe, `MainFrame.java:498,805`). Switch is exhaustive — the two new variants force two new arms.

- `MainFrame.saveCurrentFile:985`, `saveAsNewFile:1012-1014`, `handleShowOpenDialog:861-864`.

- `AbcConverter.convert` `.../converter/AbcConverter.java:60`, `MidiConverter.convert` `.../converter/MidiConverter.java:83` — `SongLoader.load` → `SongFileLoader.load` (both use `songOrThrow()` in try/catch; the new variants map to `IOException`, already caught).

- The 5 hand-built-fixture reader tests (`MusicXmlReaderLenienceTest`, `MusicXmlAnnotationRoundTripTest`, `MusicXmlLyricRoundTripTest`, `MusicXmlBarlineRoundTripTest`, `MusicXmlHairpinRoundTripTest`) — their minimal `<score-partwise>` docs must gain a SongScribe `<software>` tag (prefer one shared builder in `MusicXmlRoundTripSupport`).

- `src/main/resources/songscribe/strings.properties` — `filter.musicxml` (between `filter.midi:348` and `filter.pdf:349`) and three `alert.musicxml.*` keys (sorted in the alert group). **Dead-key-audit constraint:** each new `Strings.*` key must be referenced under `src/` in the _same_ phase that adds it.

## Plan
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 0   | [Extract Testable Save Path](#-phase-0-extract-testable-save-path) | ✅ Complete | Sonnet 4.6, low |
| 1   | [Provenance Gate, Format Validation & Result Types](#-phase-1-provenance-gate-format-validation--result-types) | ✅ Complete | Opus 4.8, medium |
| 2   | [Read Dispatch & Open Wiring](#-phase-2-read-dispatch--open-wiring) | ✅ Complete | Sonnet 4.6, medium |
| 3   | [Write Side & File Dialogs](#-phase-3-write-side--file-dialogs) | ✅ Complete | Sonnet 4.6, low |
| 4   | [Routing, Gate & Format Tests](#-phase-4-routing-gate--format-tests) | ✅ Complete | Sonnet 4.6, medium |

* * *
## ✅ Phase 0: Extract Testable Save Path
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Sonnet 4.6, low — a behavior-preserving extraction plus the `checkError()` guard and its test; no format change yet. Touches the `MainFrame` singleton, so keep the message-post / modified-flag logic in `MainFrame` and move only the write-to-disk mechanics.  
**Why:** save is now the canonical MusicXML path and `PrintWriter` silently swallows write errors, so a false "Saved" on a disk-full write is unacceptable. Pulling the write mechanics out of `MainFrame` into `SongFileWriter` (symmetric with `SongFileLoader`) makes that guard unit-testable.
### Tasks
1. Create `songscribe.io.SongFileWriter` (`private` ctor), symmetric with `SongFileLoader`:
   - `static boolean write(Song song, DocumentFontsHolder fonts, PrintWriter pw)` — calls `SongIO.writeSong(song, fonts, pw)`, then `pw.flush()`, then returns `!pw.checkError()`. Does **not** close `pw` (caller owns an injected writer's lifecycle — this is the unit-test seam).
   - `static boolean write(Song song, DocumentFontsHolder fonts, File file) throws IOException` — opens `new PrintWriter(file, StandardCharsets.UTF_8)`, delegates to the `PrintWriter` overload, closes in a `finally`, and returns the result. Only the open failure throws `IOException`.

2. `MainFrame.saveCurrentFile` (`:985`): replace the inline `PrintWriter` + `SongIO.writeSong` + `close` body with `if (!SongFileWriter.write(scoreView.getSong(), scoreView, currentFile)) { OptionDialogs.showErrorMessage(this, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_SAVE); return false; }`, then the existing success tail (`setModified(false)`, `LOG.info`, `MessageCenter.post(new DocumentWasSavedNotification())`, `return true`). Keep the surrounding `try/catch (IOException)` for the `File`-overload open failure (same `ERROR_FILE_SAVE` message).

3. Write `src/test/java/songscribe/io/SongFileWriterTest.java` (unit): (a) writing a valid song through a `PrintWriter` over a `StringWriter` → returns `true` and the captured text is parseable MusicXML/`.mssw`; (b) writing through a `PrintWriter` over a `Writer` whose `write`/`flush` throws → returns `false` (exercises the `checkError()` guard). Name literals as constants.

4. `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh unit` → green.

* * *
## ✅ Phase 1: Provenance Gate, Format Validation & Result Types
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Opus 4.8, medium — touches the sealed `SongLoadResult` hierarchy, the SAX reader lifecycle, and two new validation gates; this is the one conceptual piece of the cutover.  
**Why reader-embedded & uniform:** both gates (format/version and provenance) live inside `MusicXmlReader` rather than the `SongFileLoader` dispatcher, so there is a single reader path with no divergent behavior. The accepted cost is that every hand-built fixture must be well-formed SongScribe MusicXML (task 6). The provenance prefix reuses `Constants.PACKAGE_NAME` (no new constant), and version is validated as a double `>= MIN_SUPPORTED_VERSION` (forward-compatible) rather than the current exact-string `"4.0"` equality.
### Tasks
1. `SongLoadResult`: add `record WrongSoftware(File file, @Nullable String software) implements Failure` and `record UnsupportedFileFormat(File file, @Nullable String detail) implements Failure`. Add both to the `Failure` `permits` list and add a `case` for each to `songOrThrow()` — each throwing an `IOException` describing the refusal (mirroring how `LineWidthTooLarge` constructs a fresh `IOException`).

2. `MusicXmlWriter.java:316`: change `"SongScribe " + Version.PUBLIC_VERSION` → `Constants.PACKAGE_NAME + " " + Version.PUBLIC_VERSION` (byte-identical output; no new constant introduced).

3. `MusicXmlTags`: add `static final double MIN_SUPPORTED_VERSION = 4.0;` (named — no raw literal in the reader check).

4. `MusicXmlReader` — **format/version gate** (`startElement`, `case NONE`, `:316-330`): keep the `<score-partwise>` branch but (a) parse `version` as a `double` and throw `UnsupportedFormatException` when it is missing, unparseable (`NumberFormatException`), or `< MIN_SUPPORTED_VERSION`; and (b) add an `else` that throws `UnsupportedFormatException` when the root element is not `<score-partwise>` (carry a `detail` like `"root <" + qName + ">"`). This replaces the current exact-string `VERSION_VALUE` equality (which threw a bare `SAXException`).

5. `MusicXmlReader` — **provenance gate**: at the `Where.SOFTWARE` endElement (`:1084-1088`) capture `value.toString()` into a new `@Nullable String software` field instead of discarding it. Add an `endDocument()` override that throws `ForeignSoftwareException` when `software` is `null`, blank, or does not `startsWith(Constants.PACKAGE_NAME)`. Add both nested exceptions — `ForeignSoftwareException extends SAXException` (raw `software()` accessor, may be `null`/blank) and `UnsupportedFormatException extends SAXException` (`detail()` accessor) — following the class's `@Nullable`-on-own-line field style.

6. **Fixtures:** every hand-built `<score-partwise>` test document read via `MusicXmlReader.read(InputSource)` now hits the uniform provenance gate, so each must include `<identification><encoding><software>SongScribe …</software></encoding></identification>`. Factor a single minimal-doc wrapper into `MusicXmlRoundTripSupport` (emitting the `<software>` tag via `Constants.PACKAGE_NAME`) and route the 5 affected tests through it, so future fixtures inherit the tag instead of silently breaking. (`MusicXmlReaderLenienceTest`, `MusicXmlAnnotationRoundTripTest`, `MusicXmlLyricRoundTripTest`, `MusicXmlBarlineRoundTripTest`, `MusicXmlHairpinRoundTripTest`.)

7. `./scripts/compile.sh` → must report SUCCESS.

* * *
## ✅ Phase 2: Read Dispatch & Open Wiring
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, medium — fully-specified wiring; the subtle bits (clear `currentFile` on legacy import; two new failure dialog branches) are spelled out.  
**Why:** routing is an explicit allow-list (`.mssw` / `.musicxml` / `.xml`), not "else = MusicXML", so an unknown extension fails cleanly as `UnsupportedFileFormat`; the shared `hasExtension` predicate keeps the extension classification from being duplicated between the router and `ScoreView`. Opening a legacy `.mssw` passes `null` to `onFileOpened` (untitled ⇒ first Save writes a fresh `.musicxml`) — one-way, non-destructive import.
### Tasks
1. `FileExtensions.java`: add `public static final String MUSICXML = "musicxml";` and `public static final String XML = "xml";`.

2. `FileUtils`: add `public static boolean hasExtension(File file, String... extensions)` — compares `getExtension(file.getName()).toLowerCase()` against each (already-lowercase) candidate. This single predicate serves both the router and `ScoreView`'s legacy check (removes the duplicated case-folding).

3. Create `songscribe.io.SongFileLoader` — `private` ctor, one static `load(File): SongLoadResult`, with the **open-dispatch decision tree** (above) reproduced as an inline comment. Routing:
   - `hasExtension(file, FileExtensions.SONGWRITER)` → `SongLoader.load(file)`.
   - `hasExtension(file, FileExtensions.MUSICXML, FileExtensions.XML)` → `MusicXmlReader.read(file)` in a try/catch mapping, **in this order**, `ForeignSoftwareException` → `WrongSoftware(file, e.software())`, `UnsupportedFormatException` → `UnsupportedFileFormat(file, e.detail())`, `SAXException` → `ParseError`, `IOException` → `IoError` (the two nested subclasses must be caught before the generic `SAXException`).
   - otherwise → `new SongLoadResult.UnsupportedFileFormat(file, FileUtils.getExtension(file.getName()))`.

4. Read `.agents/guides/strings.md`; add (sorted in the alert group), all referenced in tasks 5–6:
   - `alert.musicxml.foreign = That file was created with {0}. SongScribe can only load files created with SongScribe.`
   - `alert.musicxml.foreign.other = other notation software`
   - `alert.musicxml.unsupported = SongScribe can’t open “{0}”. It isn’t a supported MusicXML file.`

5. `ScoreView.openFile` (`:420`): route through `SongFileLoader.load(file)`. In the `Success` branch (`:441-443`) call `onFileOpened.accept(FileUtils.hasExtension(file, FileExtensions.SONGWRITER) ? null : file)`. Add two arms:
   - `case SongLoadResult.WrongSoftware e ->`: `name = (e.software() != null && !e.software().isBlank()) ? e.software() : Strings.get(Strings.ALERT_MUSICXML_FOREIGN_OTHER)`, then `OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_FOREIGN, name)`; log + `yield false`.
   - `case SongLoadResult.UnsupportedFileFormat e ->`: `OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_UNSUPPORTED, file.getName())`; log (include `e.detail()`) + `yield false`.

6. Route `AbcConverter.convert` (`:60`) and `MidiConverter.convert` (`:83`) from `SongLoader.load` → `SongFileLoader.load`. Both call `songOrThrow()` inside a try/catch; `WrongSoftware`/`UnsupportedFileFormat` map to `IOException` there, which both already catch and log — no new handling needed.

7. `./scripts/compile.sh` → must report SUCCESS.

* * *
## ✅ Phase 3: Write Side & File Dialogs
**Status:** Complete  
**BlockedBy:** 0, 2  
**Recommended model/effort:** Sonnet 4.6, low — mechanical call/constant swaps; the save-failure guard already exists (Phase 0), so this only swaps the writer behind it.
### Tasks
1. Read `.agents/guides/strings.md`. Add `filter.musicxml = MusicXML files` to `strings.properties` between `filter.midi:348` and `filter.pdf:349`; `Strings.FILTER_MUSICXML` is referenced in tasks 3–4.

2. `SongFileWriter.write(…, PrintWriter)`: swap `SongIO.writeSong(...)` → `MusicXmlWriter.writeSong(...)` (identical signature — `(Song, DocumentFontsHolder, PrintWriter)`). Add the `MusicXmlWriter` import; remove the now-unused `SongIO` import once `writeSong` is its only reference. `MainFrame.saveCurrentFile` needs no change — it already routes through `SongFileWriter` (Phase 0), so the `checkError()` guard now covers the canonical MusicXML write for free.

3. `MainFrame.saveAsNewFile` (`:1012-1014`): change the filter label to `Strings.get(Strings.FILTER_MUSICXML)` and the extension arg to `FileExtensions.MUSICXML`.

4. `MainFrame.handleShowOpenDialog` (`:861-864`): give the open dialog two filters via the `PlatformFileDialog(…, ExtensionFileFilter[])` ctor — MusicXML (`new ExtensionFileFilter(Strings.get(Strings.FILTER_MUSICXML), FileExtensions.MUSICXML, FileExtensions.XML)` — both extensions, consistent with the loader allow-list) and the existing legacy filter (`FILTER_SONGSCRIBE` / `FileExtensions.SONGWRITER`).

5. `./scripts/compile.sh` → must report SUCCESS.

* * *
## ✅ Phase 4: Routing, Gate & Format Tests
**Status:** Complete  
**BlockedBy:** 1, 2, 3  
**Recommended model/effort:** Sonnet 4.6, medium — two new test focuses plus the full-suite gate.
### Tasks
1. Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md`.

2. Write `src/test/java/songscribe/io/SongFileLoaderTest.java` (unit) asserting routing, the provenance gate, and format/version validation. Build foreign/malformed fixtures by string-editing a valid writer projection; name any literals as constants. Cases:
   - (a) writer-produced `.musicxml` → `Success`.
   - (b) legacy `.mssw` corpus file (`src/test/resources/corpus/real/`) → `Success`.
   - (c) `.musicxml` with `<software>` rewritten to `Finale` → `WrongSoftware` with software `"Finale"`.
   - (d) `.musicxml` with `<software>` element removed → `WrongSoftware` with `null` software.
   - (e) `.musicxml` with `<software>` whitespace-only → `WrongSoftware` (blank).
   - (f) writer-produced `.xml` (same content, `.xml` name) → `Success`.
   - (g) foreign `.xml` (software = `Finale`) → `WrongSoftware`.
   - (h) `.pdf` (or extension-less) path → `UnsupportedFileFormat` with the extension in `detail`.
   - (i) `.musicxml` whose root is not `<score-partwise>` → `UnsupportedFileFormat`.
   - (j) `.musicxml` with `version="3.0"` → `UnsupportedFileFormat`; also `version` removed → `UnsupportedFileFormat`; also `version="x"` (unparseable) → `UnsupportedFileFormat`.
   - (k) malformed (not well-formed XML) `.musicxml` → `ParseError`.
   - (l) nonexistent `.musicxml` path → `IoError`.

3. Write a focused `ScoreView.openFile` test (`ScoreViewTest` or new): construct `new ScoreView(capturingConsumer)` where the consumer records the `@Nullable File`; assert opening a `.mssw` fixture yields `null` (untitled) and opening a writer-produced `.musicxml` yields the file. Guards the silent "legacy branch regresses to `file`" failure mode.

4. `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh unit` → green (includes `SongFileLoaderTest`, the new `openFile` test, `SongFileWriterTest` (Phase 0), the corpus gate, the 5 updated fixture tests, and `TempoIOTest`).

* * *
## Verification (whole sub-plan)
- `./scripts/compile.sh` reports SUCCESS.

- `./scripts/test.sh unit` is green — `MusicXmlCorpusLosslessnessTest` (the gate) and the 5 updated hand-built-fixture tests still pass, and `SongFileLoaderTest` + the `openFile` test pass.

- Save writes `.musicxml` via `MusicXmlWriter` (through `SongFileWriter`); a swallowed write error surfaces as a failed save (no false "Saved") — verified by `SongFileWriterTest`.

- Open routes `.musicxml`/`.xml` → `MusicXmlReader` and `.mssw` → legacy `SongLoader`; opening a legacy `.mssw` leaves the document untitled so the first Save produces a fresh `.musicxml`.

- A file that is not SongScribe-authored MusicXML is refused: missing/blank/foreign `<software>` → `alert.musicxml.foreign`; wrong root or `version < 4.0`/missing/unparseable → `alert.musicxml.unsupported`; unknown extension → `alert.musicxml.unsupported`.

- Legacy import still works headlessly through `AbcConverter` / `MidiConverter`.
