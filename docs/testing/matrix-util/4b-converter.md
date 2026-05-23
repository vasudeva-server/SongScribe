### 4B. `converter`

Audited by reading every production class body symbol-by-symbol via serena `jet_brains_find_symbol` with `include_body=true`, then confirming zero test coverage by searching the entire test tree for references to any converter class, method, or annotation name; no `src/test/java/songscribe/converter/` directory exists.

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| `ArgumentReader` | `parseArguments` — constructs target object via reflection, iterates fields for `@FileArgument` to set `fileType`/`fileField` | unit | none | missing | add unit test with a simple annotated POJO | ⬜ |
| `ArgumentReader` | `parseArguments` — named flag parsing: splits on `=`, looks up field by name, rejects unknown flag (prints + exits) | unit | none | missing | add unit test for valid flag, unknown flag branch | ⬜ |
| `ArgumentReader` | `parseArguments` — `-?` / `-help` flag triggers `infoBuilder()` + `System.exit(-1)` | unit | none | missing | add unit test with `SecurityManager` or exit-trap | ⬜ |
| `ArgumentReader` | `parseArguments` — stops scanning flags when first non-`-` arg encountered | unit | none | missing | add unit test: arg without leading dash breaks the flag loop | ⬜ |
| `ArgumentReader` | `parseArguments` — file collection: exists → added; not found → prints + exits; `SINGLE` stops after first file; empty list → prints + exits | unit | none | missing | add unit tests for each branch (use temp files or mock `File.exists`) | ⬜ |
| `ArgumentReader` | `parseArguments` — `NONE` file type: file-collection block skipped entirely | unit | none | missing | add unit test with a POJO carrying no `@FileArgument` field | ⬜ |
| `ArgumentReader` | `setField` — `int` field: valid string → `field.setInt`; null value → logs error, no throw | unit | none | missing | add unit test for int field with value, and with null | ⬜ |
| `ArgumentReader` | `setField` — `boolean` field: null value → `true`; `"true"`/`"false"` string → parsed; non-parseable still parsed by `Boolean.parseBoolean` (always non-throwing) | unit | none | missing | add unit tests for boolean with null, "true", "false" | ⬜ |
| `ArgumentReader` | `setField` — `String` (or other) field: value passed through via `field.set` | unit | none | missing | add unit test for String field | ⬜ |
| `ArgumentReader` | `setField` — `NumberFormatException` on bad int string → logs, does not throw, field stays default | unit | none | missing | add unit test: `-count=abc` on an int field | ⬜ |
| `ArgumentReader` | `findField` — known field name → returns `Field`; unknown → returns `null` | unit | none | missing | covered implicitly by `parseArguments` tests; no separate test needed | ⬜ |
| `ArgumentReader` | `getObj` — lazy: calls `parseArguments` once on first call, caches result | unit | none | missing | add unit test verifying `getObj()` returns same instance on two calls | ⬜ |
| `ArgumentReader` | `infoBuilder` — builds usage string: `SINGLE` → "file", `MANY` → "file1 [file2]…"; includes field names with descriptions; `@NoDefault` fields omit `(default=…)` | unit | none | missing | add unit test asserting on string content for each `FileType` variant | ⬜ |
| `Converter` | `applyExportExclusions` — `withoutLyrics=true` clears both lyrics fields; `withoutSongTitle=true` clears title; both false → no change | unit | none | missing | straightforward unit test with a mock/real `Song` | ⬜ |
| `Converter` | `loadSong` — delegates to `score.openFile` + `score.getSong()`; no branching | none | none | adequate (none) | no test warranted — pure delegation, risk is integration | — |
| `MidiConverter` | `convert` — instrument out of `[0,127]` → warn + return early | unit | none | missing | unit test: instrument=-1, instrument=128 | ⬜ |
| `MidiConverter` | `convert` — tempoChange out of `[1,200]` → warn + return early | unit | none | missing | unit test: tempoChange=0, tempoChange=201 | ⬜ |
| `MidiConverter` | `convert` — valid params → delegates to `PlaybackController` + `MidiSystem.write` (file I/O + MIDI stack) | none/e2e | none | adequate (none) | orchestration; risk is integration, not logic | — |
| `PDFConverter` | `convert` — `paperSize=null` → early return | unit | none | missing | unit test with null paperSize | ⬜ |
| `PDFConverter` | `convert` — paper size switch: `a4`, `letter`, `legal` → assigns named dimension constants; `default` → warn + return | unit | none | missing | unit tests for each case branch (assert resulting `paperWidth`/`paperHeight`) | ⬜ |
| `PDFConverter` | `convert` — `custom` paper size with valid `paperWidth`/`paperHeight` → proceeds | unit | none | missing | unit test | ⬜ |
| `PDFConverter` | `convert` — `custom` paper size with `paperWidth<=0` or `paperHeight<=0` → warn + return | unit | none | missing | unit test | ⬜ |
| `PDFConverter` | `convert` — `files.length==0` → log error + return | unit | none | missing | unit test | ⬜ |
| `PDFConverter` | `convert` — per-file loop: loads song, applies export exclusions, calls `ExportPDFAction.createPDF` (file I/O + render) | none | none | adequate (none) | orchestration | — |
| `PDFConverter` | margin override wiring (`applyMarginOverrides` delegation in `convert`) | none | none | adequate (none) | `PageLayoutData.applyMarginOverrides` is separately testable and is pure logic in `export` package | — |
| `SVGConverter` | `convert` — per-file loop: loads song, applies export exclusions, calls `score.createSVG` | none | none | adequate (none) | orchestration; no branching logic | — |
| `SVGConverter` | `main` — package-private (not `public static void main`) — unreachable as a JVM entry point | none | none | adequate (none) — but flag | note: `main` has package-private visibility (no `public`), so it cannot be launched as a JVM entry point; likely a bug or intentional limitation; zero in-repo callers confirmed | — |
| `AbcConverter` | `convert` — `file==null` → log error + return | unit | none | missing | unit test | ⬜ |
| `AbcConverter` | `convert` — non-null file → delegates to `SongLoader.load` + `ExportABCAction.writeABC` (file I/O) | none | none | adequate (none) | orchestration | — |
| `ImageConverter` | `convert` — stub body: only logs "not yet implemented" | none | none | adequate (none) — stub | no assertions possible; no production logic exists yet | — |
| `ArgumentDescribe` | annotation retention/value | none | none | adequate (none) | trivial annotation; framework behavior | — |
| `FileArgument` | annotation retention | none | none | adequate (none) | trivial marker annotation | — |
| `NoDefault` | annotation retention | none | none | adequate (none) | trivial marker annotation | — |

**4B notes (quality concerns):**

The converter package has **zero tests of any kind** — no `src/test/java/songscribe/converter/` directory exists and no cross-package test references any converter symbol. The highest-risk dark gap is `ArgumentReader`, which is the only class in the package with real logic: reflection-based argument parsing with eight distinct branches across `parseArguments` and `setField` covering flag parsing, file collection, type coercion, error handling, and `System.exit` paths. Every one of these branches is completely untested. The `int`-field `NumberFormatException` path and the boolean null-means-true coercion are the most mutation-invisible: they are easy to break silently because nothing observes the field value after the fact. `PDFConverter.convert` is the second-highest risk: it contains a five-branch `switch` on paper size (including a `custom` validation path with two sub-conditions), a `files.length==0` guard, and a null guard on `paperSize` — all missing. The paper-size switch is pure string-comparison logic with named constant assignments, exactly what unit tests are suited for. `Converter.applyExportExclusions` has trivial `if`-branches that set Song fields to empty strings; this is the simplest missing test in the package and its omission is surprising. `SVGConverter.main` being package-private (no `public` modifier) is a likely latent bug: it cannot be invoked as a standard JVM entry point, and no in-repo caller compensates for this.

