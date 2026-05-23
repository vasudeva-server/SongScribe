## 4. `midi` + `converter` + `util` + `smufl` + `prefs` + `font` + `export` + `uiconverter` (audited 2026-05-21)

Audited in two waves of parallel production-first sub-audits (Wave 1: midi, converter, smufl; Wave 2: util, prefs, font, export, uiconverter). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

### 4A. `midi`

Audited production-code-first: read every method body in all 7 classes via serena `jet_brains_find_symbol` with `include_body=true`, enumerated testable behaviors, then read all 3 existing test files in `src/test/java/songscribe/midi/` and confirmed zero coverage of `midi` classes in `src/test/java/songscribe/e2e/`.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| GlissandoMidiHelper | `resolveTargetPitch` — CONNECTED returns nextNotePitch; SLIDE_OUT returns sourcePitch − 4 | unit | `GlissandoMidiHelperTest.ResolveTargetPitch` (3 methods: testConnectedReturnsNextNotePitch, testSlideOutReturnsSourceMinusFour, testSlideOutIgnoresNextNotePitch) | adequate | none |
| GlissandoMidiHelper | `calculateSensitivity` — abs(target−source), floor at 1 | unit | `GlissandoMidiHelperTest.CalculateSensitivity` (4 methods) | adequate | none |
| GlissandoMidiHelper | `calculateBendValue` — linear (CONNECTED_CURVE_EXPONENT=1.0) and quadratic (SLIDE_OUT_CURVE_EXPONENT=2.0) interpolation; clamp to [0, 16383] | unit | `GlissandoMidiHelperTest.CalculateBendValue` (7 methods) | adequate | none |
| GlissandoMidiHelper | `calculateSlideTicks` / `calculateSustainTicks` — correct tick splits from SLIDE_RATIO/SUSTAIN_RATIO | unit | `GlissandoMidiHelperTest.CalculateSlideTicks` + `.CalculateSustainTicks` (3+3 methods) | adequate | none |
| GlissandoMidiHelper | `createRpnMessages` — emits CC 101, 100, 6 (semitones), 38=0 at correct tick on correct channel | unit | `GlissandoMidiHelperTest.CreateRpnMessages` (4 methods) | adequate | none |
| GlissandoMidiHelper | `createRpnMessagesIfNeeded` — de-dups by currentSensitivity; re-emits after `resetSensitivity()` | unit | `GlissandoMidiHelperTest.CreateRpnMessagesIfNeeded` (4 methods) | adequate | none |
| GlissandoMidiHelper | `createPitchBendMessages` — correct event count, tick positions, zero-duration guard | unit | `GlissandoMidiHelperTest.CreatePitchBendMessages` (6 methods) | inadequate — `testProgressivelyIncreasingBend` asserts only `>=` monotonicity rather than exact bend values at specific ticks; a mutation flipping `sourcePitch`/`targetPitch` or swapping the curve exponent will not be caught by monotonicity alone. Specific values tested in `CalculateBendValue` do not cover the full event-sequence output. | add exact bend-value assertions at key ticks (t=0, t=0.5, t=1.0) for both up and down slides |
| GlissandoMidiHelper | `createPitchBendReset` — emits single pitch bend at PITCH_BEND_CENTER | unit | `GlissandoMidiHelperTest.CreatePitchBendReset` (2 methods) | adequate | none |
| GlissandoMidiHelper | `createPendingResets` — conditional emission based on needsPitchBendReset / needsExpressionReset flags; clears flags after emit | unit | none found | missing | add unit test: set flags via `setNeedsPitchBendReset`/`setNeedsExpressionReset`, call `createPendingResets`, assert CC/bend events emitted and second call produces nothing |
| GlissandoMidiHelper | `calculateSlideInBendValue` — grace-note curve: starts at full offset, eases to center; clamp | unit | none found | missing | add unit test for t=0.0 (full offset), t=1.0 (center), t=0.5 (half-curved) |
| GlissandoMidiHelper | `createSlideInPitchBendMessages` — zero-duration guard; event count and tick range; t=0 at full grace offset | unit | none found (integration test checks `isNotEmpty()` only) | inadequate — `GlissandoMidiIntegrationTest.GraceHostPair.testNoteOnCountMatchesNonGracePitchedNotes` only asserts `isNotEmpty()` on bend events and correct NOTE_ON count; does not verify event tick positions, bend values, or expression CC values | add unit test with a small fixed slide verifying event count, first/last bend values, and expression CC ramp |
| GlissandoMidiHelper | `createSlideInExpressionMessages` — ramp from GRACE_SLIDE_IN_START_RATIO×127 to 127; zero-duration guard | unit | none found | missing | add unit test: verify event count, first event CC11 value ≈ 0.25×127, last event = 127 |
| GlissandoMidiHelper | `createSlideOutExpressionMessages` — fade from 127 to 0 along quadratic curve; zero-duration guard | unit | none found | missing | add unit test: verify event count, first event CC11 = 127, last event = 0 |
| GlissandoMidiHelper | `createExpressionReset` — emits single CC11=127 | unit | none found | missing | add unit test |
| GlissandoMidiHelper | `setPendingGracePitch` / `hasPendingGracePitch` / `consumePendingGracePitch` — state machine: set, detect, consume clears to -1 | unit | none found (used transitively in integration test but never tested directly) | missing | add unit test for all three: set, assert has=true, consume returns correct value and clears |
| GlissandoMidiHelper | `resetSensitivity` — resets currentSensitivity to -1 so next `createRpnMessagesIfNeeded` always emits | unit | `GlissandoMidiHelperTest.CreateRpnMessagesIfNeeded.testResetCausesReEmit` | adequate | none |
| LineTrackBuilder | `getElementDurationWithTuplet` — returns element duration × tuplet factor; non-tuplet path returns raw duration | unit | none found | missing | add unit test: non-tuplet element, and a simple 3-in-2 tuplet span |
| LineTrackBuilder | `getTupletFactor` — complex branch: tupletDuration ≥ 1 (floor, minus-1 edge), tupletDuration < 1 (log2 power-of-2 floor); non-tuplet returns 1.0 | unit | none found | missing — this is the highest-risk dark gap in the package: floating-point rounding, three branches, and a labelled-loop back-reference; any mutation in the log2 or floor paths will survive undetected | add parameterized unit tests covering: no tuplet, 3-in-2 (tupletDuration < 1), triplet spanning one beat (tupletDuration = 1), quintuplet (tupletDuration > 1), edge where newDuration == tupletDuration > 1 (the newDuration-- branch) |
| LineTrackBuilder | `calculateSoundingDuration` / `calculateSoundingPercent` — override vs. settings.noteDurationPercent(); staccato path | unit | none found | missing | add unit test: no override uses settings percent; override present ignores settings |
| LineTrackBuilder | `noteVelocity` — VelocityMap present returns map value; null map uses ACCENT/non-accent fallback | unit | none found | missing | add unit test for both paths |
| LineTrackBuilder | `addNoteMessages` — grace note stores pitch (no NOTE_ON); note with tie anchor/end logic; rest advances ticks; glissando vs. normal note-off dispatch | unit | partially covered by `GlissandoMidiIntegrationTest.GraceHostPair` (grace+host NOTE_ON count) | inadequate — integration test verifies NOTE_ON count and `isNotEmpty()` bend; does not test tie span logic, rest tick advance, or the fallback when connected glissando's next element is non-pitched | add unit tests per dispatch branch |
| LineTrackBuilder | `addGlissandoMessages` — CONNECTED: full duration, next-note pitch, noteOff at duration−1; SLIDE_OUT: sounding duration, staccato, expression fade; fallbacks when next element missing or non-pitched | unit | `GlissandoMidiIntegrationTest.ConnectedGlissando` + `.SlideOut` | inadequate — both nested classes call `buildMidiTrack(line, tempo)` on the same fixture line; `ConnectedGlissando` asserts RPN sequence prefix `hasSizeGreaterThanOrEqualTo(4)` (weak lower bound) and `SlideOut.testPitchBendEventsPresent` asserts only `isNotEmpty()`; neither verifies noteOff tick, bend direction, bend values, or expression CC events; the `SlideOut.testRpnSensitivityIncludesSlideOutSemitones` check is the only quantitative assertion | add unit tests with constructed lines verifying: tick of noteOff for CONNECTED (duration−1), fallback to normal note-off when next element is rest, expression CC events for SLIDE_OUT, staccato shortening |
| LineTrackBuilder | `addGraceGlissandoSlideIn` — reduced velocity (×0.85), pitch bend ramp from grace pitch, expression ramp, reset at slide end | unit | `GlissandoMidiIntegrationTest.GraceHostPair.testNoteOnCountMatchesNonGracePitchedNotes` | inadequate — asserts NOTE_ON count and `isNotEmpty()` on bend events; does not verify reduced velocity, expression CC ramp from CC11=≈32 to 127, or pitch bend reset at end of slide | add unit test verifying NOTE_ON velocity ≈ 0.85×default, bend reset CC at slideStartTick+GRACE_SLIDE_TICKS, expression reset CC at same tick |
| LineTrackBuilder | `addToTrack` overloads — tempo change attachment triggers `MidiEventFactory.addTempoEvent`; colorize meta message emitted per element; glissandoHelper state flushed at end of overload[3]; overload[4] leaves flush to caller | unit | none found | missing | add unit tests: tempo-change element causes SET_TEMPO meta event; range start/end boundaries respected; overload[3] flushes pending resets; overload[4] does not |
| MidiEventFactory | `addTempoEvent(Track, int, Tempo, int)` — BPM×percent/100 scaling then encodes as 3-byte big-endian microseconds-per-beat | unit | none found | missing — pure arithmetic on a critical playback path; a mutation swapping `>>16`/`>>8`/`>>0` byte order will produce wrong tempo silently | add unit test: verify SET_TEMPO meta message bytes for known BPM (e.g. 120 BPM → 500000 μs/beat → bytes [0x07, 0xA1, 0x20]); verify tempoChangePercent scaling (100% = unchanged, 200% = double speed) |
| MidiEventFactory | `addTempoEvent(Track, int, int)` — MICROSECONDS_PER_MINUTE / BPM → 3-byte encoding | unit | none found | missing | same test class as above; cover direct-BPM variant |
| MidiSequenceBuilder | `buildFullSequence` — delegates to `buildSequence(0, 0, −1, −1, effectiveTempo)` | unit | none found | missing |add unit test with a simple song: assert non-null Sequence, at least one MIDI track, PPQ=96, program-change event at tick 0 |
| MidiSequenceBuilder | `buildFromNoteToEnd` — picks correct startTempo via `song.getTempoAt()` | unit | none found | missing | add unit test: song with mid-line tempo change, start from that note, assert SET_TEMPO meta at tick 0 matches that tempo |
| MidiSequenceBuilder | `buildSequence` — linear path (no repeats / hard end boundary): bank select + program change at tick 0; initial tempo meta; velocity map pre-computation; line range slicing; END_OF_TRACK at final tick | unit | none found | missing — the full integration of bank select, program change, velocity map, and END_OF_TRACK placement is untested; wrong END_OF_TRACK tick would shorten audio silently | add unit tests for each setup event (bank select CC0/32, program change) and END_OF_TRACK tick |
| MidiSequenceBuilder | `buildSequenceWithRepeats` — repeat-right jumps back to repeat-left (or song start); repeating flag skips first ending on second pass; glissandoHelper state survives across note-by-note calls; final flush of pending resets | unit | none found | missing — highest risk in MidiSequenceBuilder; the repeat search backward loop with labelled break is hard to read and easy to mutate; first-ending skip logic has two separate branches | add unit tests: simple repeat (A–A'), repeat with first/second endings, repeat with no explicit start (returns to beginning), grace-note glissando spanning a repeat boundary |
| MidiSequenceBuilder | `addProgramChange` — emits PROGRAM_CHANGE on channel 0 at tick 0 | unit | none found | missing | covered by buildSequence unit test above |
| MidiSequenceBuilder | `addBankSelect` — emits CC 0 (MSB) and CC 32 (LSB) at tick 0 | unit | none found | missing | covered by buildSequence unit test above |
| VelocityMap | `build` — no dynamic: DEFAULT_VELOCITY_FRACTION; dynamic marking overrides; forward propagation within line; cross-line propagation; accent boost; accent boost capped at MAX_VELOCITY | unit | `VelocityMapTest` (8 methods across 4 nested classes) | adequate | none |
| VelocityMap | `getVelocity` — simple array lookup; no bounds guard | unit | exercised by every VelocityMapTest method | adequate (covered transitively) | none |
| VelocityMap | `build` with custom masterVelocity < MAX_VELOCITY — scales all velocities proportionally | unit | none found | missing — all tests call `VelocityMap.build(song, VelocityMap.MAX_VELOCITY)`; the `masterVelocity` parameter is never varied | add test with masterVelocity=64 to confirm velocities scale from ceiling |
| PlaybackSettings | pure data record — no logic | none | n/a | adequate | none |
| TrackPosition | pure data record — no logic | none | n/a | adequate | none |
| GlissandoMidiIntegrationTest `.testNoPitchBendWithoutGlissando` | asserts fixture model property (`getGlissando() == null`), not MIDI output | unit (misclassified) | exists | inadequate — name says "no pitch bend" but the test never builds a MIDI track; it only reads the fixture model; a mutation in the MIDI generation path would leave this test green | either delete (fixture integrity is not a MIDI generation concern) or rewrite to build the track and assert zero pitch bend events |

**4A notes (quality concerns):**

The highest-risk dark gap is `LineTrackBuilder.getTupletFactor`. It contains three branches (no tuplet, tupletDuration < 1, tupletDuration ≥ 1 with a secondary edge case for exact integer durations > 1), floating-point log2 arithmetic, and is the only thing controlling note timing for all tuplet playback. It has zero tests. A mutation that, for example, swaps `Math.floor` for `Math.ceil` in the tupletDuration ≥ 1 path would shift all tuplet durations without any test failing.

The second highest-risk gap is `MidiSequenceBuilder.buildSequenceWithRepeats`. The repeat logic contains a backward linear search with a labelled loop break, a `repeating` flag state machine, and two separate first/second-ending branches. None of this logic is tested. A mutation that inverts the `repeating` check would cause notes to be doubled instead of played once through a repeat, and it would survive the entire test suite.

`MidiEventFactory.addTempoEvent` is untested despite being the only path that converts BPM to the 3-byte MIDI SET_TEMPO message. The byte-order encoding (`>> 16`, `>> 8`, bare cast) is the kind of logic where a mutation (e.g. swapping `>> 16` and `>> 8`) produces a wrong but plausible-looking value that would make playback run at the wrong tempo without any assertion catching it.

Weak-but-green tests in `GlissandoMidiIntegrationTest`: the `ConnectedGlissando` nested class uses `hasSizeGreaterThanOrEqualTo(4)` for its CC event count check (passes even if the fixture produces 400 CC events), and both `ConnectedGlissando.testPitchBendEventsPresent` and `SlideOut.testPitchBendEventsPresent` assert only `isNotEmpty()` — neither can detect a wrong bend direction, wrong tick position, or wrong value. These tests provide false confidence; they pass for any non-zero output.

`GlissandoMidiIntegrationTest.testNoPitchBendWithoutGlissando` is a name-behavior mismatch: it asserts a model property (`getGlissando() == null`), not MIDI output. Renaming it `testFixtureElementHasNoGlissandoAnnotation` or deleting it in favour of a MIDI-output assertion would resolve the mismatch.

`VelocityMap.build` is well tested for all dynamic and accent permutations, but the `masterVelocity` scaling path (the parameter is always `MAX_VELOCITY` in every test) is a silent gap: if the multiplication or rounding formula were mutated, no test would catch it until someone uses a volume-slider-style feature.

### 4B. `converter`

Audited by reading every production class body symbol-by-symbol via serena `jet_brains_find_symbol` with `include_body=true`, then confirming zero test coverage by searching the entire test tree for references to any converter class, method, or annotation name; no `src/test/java/songscribe/converter/` directory exists.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ArgumentReader` | `parseArguments` — constructs target object via reflection, iterates fields for `@FileArgument` to set `fileType`/`fileField` | unit | none | missing | add unit test with a simple annotated POJO |
| `ArgumentReader` | `parseArguments` — named flag parsing: splits on `=`, looks up field by name, rejects unknown flag (prints + exits) | unit | none | missing | add unit test for valid flag, unknown flag branch |
| `ArgumentReader` | `parseArguments` — `-?` / `-help` flag triggers `infoBuilder()` + `System.exit(-1)` | unit | none | missing | add unit test with `SecurityManager` or exit-trap |
| `ArgumentReader` | `parseArguments` — stops scanning flags when first non-`-` arg encountered | unit | none | missing | add unit test: arg without leading dash breaks the flag loop |
| `ArgumentReader` | `parseArguments` — file collection: exists → added; not found → prints + exits; `SINGLE` stops after first file; empty list → prints + exits | unit | none | missing | add unit tests for each branch (use temp files or mock `File.exists`) |
| `ArgumentReader` | `parseArguments` — `NONE` file type: file-collection block skipped entirely | unit | none | missing | add unit test with a POJO carrying no `@FileArgument` field |
| `ArgumentReader` | `setField` — `int` field: valid string → `field.setInt`; null value → logs error, no throw | unit | none | missing | add unit test for int field with value, and with null |
| `ArgumentReader` | `setField` — `boolean` field: null value → `true`; `"true"`/`"false"` string → parsed; non-parseable still parsed by `Boolean.parseBoolean` (always non-throwing) | unit | none | missing | add unit tests for boolean with null, "true", "false" |
| `ArgumentReader` | `setField` — `String` (or other) field: value passed through via `field.set` | unit | none | missing | add unit test for String field |
| `ArgumentReader` | `setField` — `NumberFormatException` on bad int string → logs, does not throw, field stays default | unit | none | missing | add unit test: `-count=abc` on an int field |
| `ArgumentReader` | `findField` — known field name → returns `Field`; unknown → returns `null` | unit | none | missing | covered implicitly by `parseArguments` tests; no separate test needed |
| `ArgumentReader` | `getObj` — lazy: calls `parseArguments` once on first call, caches result | unit | none | missing | add unit test verifying `getObj()` returns same instance on two calls |
| `ArgumentReader` | `infoBuilder` — builds usage string: `SINGLE` → "file", `MANY` → "file1 [file2]…"; includes field names with descriptions; `@NoDefault` fields omit `(default=…)` | unit | none | missing | add unit test asserting on string content for each `FileType` variant |
| `Converter` | `applyExportExclusions` — `withoutLyrics=true` clears both lyrics fields; `withoutSongTitle=true` clears title; both false → no change | unit | none | missing | straightforward unit test with a mock/real `Song` |
| `Converter` | `loadSong` — delegates to `score.openFile` + `score.getSong()`; no branching | none | none | adequate (none) | no test warranted — pure delegation, risk is integration |
| `MidiConverter` | `convert` — instrument out of `[0,127]` → warn + return early | unit | none | missing | unit test: instrument=-1, instrument=128 |
| `MidiConverter` | `convert` — tempoChange out of `[1,200]` → warn + return early | unit | none | missing | unit test: tempoChange=0, tempoChange=201 |
| `MidiConverter` | `convert` — valid params → delegates to `PlaybackController` + `MidiSystem.write` (file I/O + MIDI stack) | none/e2e | none | adequate (none) | orchestration; risk is integration, not logic |
| `PDFConverter` | `convert` — `paperSize=null` → early return | unit | none | missing | unit test with null paperSize |
| `PDFConverter` | `convert` — paper size switch: `a4`, `letter`, `legal` → assigns named dimension constants; `default` → warn + return | unit | none | missing | unit tests for each case branch (assert resulting `paperWidth`/`paperHeight`) |
| `PDFConverter` | `convert` — `custom` paper size with valid `paperWidth`/`paperHeight` → proceeds | unit | none | missing | unit test |
| `PDFConverter` | `convert` — `custom` paper size with `paperWidth<=0` or `paperHeight<=0` → warn + return | unit | none | missing | unit test |
| `PDFConverter` | `convert` — `files.length==0` → log error + return | unit | none | missing | unit test |
| `PDFConverter` | `convert` — per-file loop: loads song, applies export exclusions, calls `ExportPDFAction.createPDF` (file I/O + render) | none | none | adequate (none) | orchestration |
| `PDFConverter` | margin override wiring (`applyMarginOverrides` delegation in `convert`) | none | none | adequate (none) | `PageLayoutData.applyMarginOverrides` is separately testable and is pure logic in `export` package |
| `SVGConverter` | `convert` — per-file loop: loads song, applies export exclusions, calls `score.createSVG` | none | none | adequate (none) | orchestration; no branching logic |
| `SVGConverter` | `main` — package-private (not `public static void main`) — unreachable as a JVM entry point | none | none | adequate (none) — but flag | note: `main` has package-private visibility (no `public`), so it cannot be launched as a JVM entry point; likely a bug or intentional limitation; zero in-repo callers confirmed |
| `AbcConverter` | `convert` — `file==null` → log error + return | unit | none | missing | unit test |
| `AbcConverter` | `convert` — non-null file → delegates to `SongLoader.load` + `ExportABCAction.writeABC` (file I/O) | none | none | adequate (none) | orchestration |
| `ImageConverter` | `convert` — stub body: only logs "not yet implemented" | none | none | adequate (none) — stub | no assertions possible; no production logic exists yet |
| `ArgumentDescribe` | annotation retention/value | none | none | adequate (none) | trivial annotation; framework behavior |
| `FileArgument` | annotation retention | none | none | adequate (none) | trivial marker annotation |
| `NoDefault` | annotation retention | none | none | adequate (none) | trivial marker annotation |

**4B notes (quality concerns):**

The converter package has **zero tests of any kind** — no `src/test/java/songscribe/converter/` directory exists and no cross-package test references any converter symbol. The highest-risk dark gap is `ArgumentReader`, which is the only class in the package with real logic: reflection-based argument parsing with eight distinct branches across `parseArguments` and `setField` covering flag parsing, file collection, type coercion, error handling, and `System.exit` paths. Every one of these branches is completely untested. The `int`-field `NumberFormatException` path and the boolean null-means-true coercion are the most mutation-invisible: they are easy to break silently because nothing observes the field value after the fact. `PDFConverter.convert` is the second-highest risk: it contains a five-branch `switch` on paper size (including a `custom` validation path with two sub-conditions), a `files.length==0` guard, and a null guard on `paperSize` — all missing. The paper-size switch is pure string-comparison logic with named constant assignments, exactly what unit tests are suited for. `Converter.applyExportExclusions` has trivial `if`-branches that set Song fields to empty strings; this is the simplest missing test in the package and its omission is surprising. `SVGConverter.main` being package-private (no `public` modifier) is a likely latent bug: it cannot be invoked as a standard JVM entry point, and no in-repo caller compensates for this.

### 4C. `util`

Audited by reading every production class symbol-by-symbol with serena `jet_brains_find_symbol` (bodies), then checking `src/test/java/songscribe/util/` for mirrored tests and searching cross-package tests for references; dead-code candidates verified with `jet_brains_find_referencing_symbols`.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StringUtils | `capitalizeSentence` — uppercases first char, lowercases rest; empty-string guard | unit | none | missing | add unit tests: empty, all-caps, already capitalized |
| StringUtils | `toKebabCase` — strips non-alphanumeric, collapses hyphens, lowercases; empty-string guard | unit | none | missing | add unit tests: spaces, accented chars, leading/trailing hyphens, empty |
| StringUtils | `stripDiacritics` — NFD normalize + remove combining marks | unit | none | missing | add unit tests: accented Latin, non-diacritic stays |
| StringUtils | `stripLinefeeds` — replaces `\n` with space | unit | none | missing | add trivial unit test |
| StringUtils | `trimEnd` — trims trailing whitespace | unit | none | missing | add unit tests: trailing space/tab/newline, no trailing whitespace |
| StringUtils | `collapseMultipleSpaces` — collapses internal runs of spaces (lookahead skips line start) | unit | none | missing | add unit tests: multiple spaces, leading spaces preserved, single space unchanged |
| StringUtils | `removeSyllabifyMarkings` — removes parenthesized groups and hyphens/underscores; **DEAD CODE** (zero callers in production) | unit | none | missing | verify dead, then delete or add caller; if retained, add tests |
| StringUtils | `wrapText` — word-wrap with min-word-count rebalancing; complex branching | unit | none | missing | add unit tests: single word wider than maxWidth, rebalancing triggered, empty input |
| FileUtils | `getExtension` — returns extension without dot; no-extension case returns `""`; uses `Paths.get` so path separators matter | unit | none | missing | add unit tests: plain filename, path with dir, no extension, dot-only filename |
| FileUtils | `getPathWithoutExtension` (String overload) — strips last dot and beyond; no-dot returns whole path | unit | none | missing | add unit tests: with extension, without extension, multiple dots |
| FileUtils | `getFilename` — returns filename component from path | unit | none | missing | add unit test |
| FileUtils | `getDirectory` — returns parent dir string; null parent returns `""` | unit | none | missing | add unit tests: with parent, no parent (bare filename) |
| FileUtils | `ensureExtension` — appends extension if not already present; multi-extension variant; uses case-insensitive match | unit | none | missing | add unit tests: already has ext (all variants), missing ext, dot-prefixed ext arg |
| FileUtils | `toDotExt` (private) — tested indirectly via `ensureExtension`; ext with and without leading dot | unit | none | missing | covered by `ensureExtension` tests |
| FileUtils | `getDocumentsDirectory` — platform-conditional path; Windows reads `USERPROFILE` env var | unit | none | missing | add unit test for non-Windows path; Windows branch is harder to isolate but non-Windows is trivially testable |
| FileUtils | `zipFile` — streams file into zip entry; `@Nullable` requestName branch | unit | none | missing | add unit test with a temp file; verify entry name for null vs non-null requestName |
| ExtensionFileFilter | constructor — description appends `(ext1, ext2)` suffix | unit | none | missing | add unit test |
| ExtensionFileFilter | `accept(File)` — directories always accepted; files matched by extension (case-insensitive) | unit | none | missing | add unit tests: directory, matching ext, non-matching ext, no extension |
| ExtensionFileFilter | `accept(File, String)` / `accept(String)` (private) — delegates to extension check | unit | none | missing | covered by `accept(File)` tests (file-name branch) |
| ExtensionFileFilter | `getExtension(int)`, `getExtensions()` — simple accessors | none | none | adequate | no test needed |
| ExtensionFileFilter | `getDescription()`, `toString()` — trivial accessors | none | none | adequate | no test needed |
| GraphicUtils | `Unit.create(boolean)` — maps `isMetric` boolean to `CM`/`INCH` | unit | none | missing | add unit test |
| GraphicUtils | `Unit.fromValue(int)` — maps int to enum; unknown value → `UNDETERMINED` | unit | none | missing | add unit tests: known values, unknown value |
| GraphicUtils | `Unit.description()` — `"inch"` / `"cm"` / `""` per variant | unit | none | missing | add unit tests for all three variants |
| GraphicUtils | `Unit.isMetric()` — `true` only for `CM` | unit | none | missing | add unit tests |
| GraphicUtils | `convertFromPixels` — pixel→inch or pixel→mm with rounding; branches on `isMetric()` | unit | none (used as helper in `PageModelTest` but not tested in isolation) | missing | add unit tests: inch rounding, mm rounding, metric vs non-metric branch |
| GraphicUtils | `convertToPixels` — inch/mm→pixel; metric divides by `CM_PER_INCH * 10` | unit | none (same as above) | missing | add unit tests for both branches |
| GraphicUtils | `clampToScreen(Rectangle)` — clamps size then position; multi-monitor path | unit | `GraphicUtilsClampTest` (6 rectangle tests + 2 point/dimension tests) | adequate | multi-monitor path (different screen contains point) not covered — consider adding |
| GraphicUtils | `clampToScreen(Point, Dimension)` — delegates to Rectangle overload | unit | `GraphicUtilsClampTest.ClampPointDimension` | adequate | — |
| GraphicUtils | `setRenderingHints` — pure rendering setup on `Graphics2D`; `isRetina` branch | none | none | adequate | rendering setup; no geometry to assert |
| GraphicUtils | `fillHorizontalLine` / `fillVerticalLine` — pure draw calls | none | none | adequate | no geometry to assert |
| GraphicUtils | `readImageResource` / `readImage` — I/O delegation | none | none | adequate | framework I/O; no logic to assert |
| GraphicUtils | `getTextBlockWidth` — iterates `\n`-split lines, measures each with `TextLayout`, returns max; requires `Graphics2D` | unit | none | missing | testable by passing a mock/stub `Graphics2D` with a fixed `FontRenderContext`; `empty → 0` branch is trivially testable |
| GraphicUtils | `glyphOutline` — delegates to `Font.createGlyphVector().getOutline()` | none | none | adequate | pure delegation |
| ModifierState | `isAltPressed` — platform dispatch (`isMac` → JNA call; `isWindows` → JNA call; else `false`) | none | none | adequate | logic is only a platform guard; JNA calls cannot be unit-tested without the native library |
| MyFontUtils | `parsePSName` — parses PostScript font name; `_`-split, `-`-split, and no-separator branches | unit | none | missing | add unit tests: `Family-Style`, `Family_Style`, style with hyphens, no separator |
| MyFontUtils | `parseStyle` (private) — OSF normalization, compound-style normalization, camel-case split, abbreviation expansion | unit | none | missing | exercisable through `getStyleDescription` or by widening; add representative unit tests |
| MyFontUtils | `getStyleDescription` — delegates to `parsePSName`/`parseStyle`; Damascus-style (style embedded in family) branch | unit | none | missing | add unit tests mocking a real `Font` or using registered test fonts |
| MyFontUtils | `getFullFontDescription` — `family + ' ' + style + ' ' + size + " pt"` | unit | none | missing | trivially testable once `getStyleDescription` is tested |
| MyFontUtils | `createFont` — by PS name with size; fallback on miss | unit | `MyFontUtilsTest.testCreateFontWithKnownPsNameReturnsCorrectSize` / `testCreateFontWithUnknownPsNameReturnsFallback` | inadequate | Both tests assert `font != null` and `font.getSize()` only; the unknown-name test asserts `getPSName() != bogus` (which is an inverse, not a positive contract). No test exercises font matching accuracy. |
| MyFontUtils | `getFontMetrics` — creates offscreen `BufferedImage`, returns `FontMetrics` | none | none | adequate | pure framework delegation; metrics correctness is tested where it is used |
| MyFontUtils | `getXHeight` — **DEAD CODE** (zero callers found by `jet_brains_find_referencing_symbols`) | unit | none | missing | verify dead, then delete |
| Utils | `arrayIndexOf` — linear search on `Object[]`, returns `-1` on miss | unit | none | missing | add unit tests: found, not found, null element |
| Utils | `lineCount` — empty → 0; trims then splits on `\n` | unit | none | missing | add unit tests: empty, single line, multi-line, whitespace-only |
| Utils | `roundToTwoDecimalPlaces` — `Math.round(v * 100) / 100.0` | unit | none | missing | add unit tests: 0.005 boundary, negative value, already-rounded value |
| Utils | `getPlatformKeyStrokeString` — platform-conditional modifier symbols + key-code branches | unit | none | missing | add unit tests: Mac vs non-Mac modifiers, special key codes (ENTER, BACKSPACE, etc.) |
| Utils | `getResourcePath` — strips leading `/`, looks up via classloader, falls back to classpath root | unit | none | missing | testable in unit context; add tests: with leading `/`, without, non-existent resource throws |
| Utils | `withDesktop` / `openWebPage` / `openEmail` — orchestration around `DesktopUtils`; UI error dialog on failure | none | none | adequate | error-dialog path is framework wiring; no pure logic to assert |
| Utils | `sleep` — wraps `Thread.sleep`, swallows `InterruptedException` | none | none | adequate | trivial wrapper; no logic |
| Utils | `getCurrentYear` — delegates to `Calendar` | none | none | adequate | trivial; would be flaky |
| UIUtils | `makeTooltipWithKeystroke` — appends `" (keystroke)"` or returns name unchanged when null | unit | none | missing | add unit tests: null accelerator, non-null accelerator |
| UIUtils | `positionDialog` — positions dialog at 3/8 down parent, centered, clamped; `@Nullable` parent | unit | `BaseDialogPositionTest` verifies `positionDialog` is *called* (mocked out), but the placement arithmetic itself is never asserted | inadequate | `BaseDialogPositionTest` mocks `UIUtils` entirely — no test verifies the `x`/`y` computation; add a focused unit test for the placement math |
| UIUtils | `getTaggedString` — parses `@`/`#` prefix + optional `/baselineShift` suffix | unit | none | missing | add unit tests: `@icon`, `#music`, no prefix, with baseline shift, no prefix with slash |
| UIUtils | `padComponent` overloads — pure Swing wiring | none | none | adequate | no logic to assert |
| UIUtils | `setCanGrow` / `setFlexibleWidth` / `setCanShrink` — set min/max sizes | none | none | adequate | pure component sizing setup |
| UIUtils | `beep`, `initToolbarButton`, `addStandardDialogKeyBindings`, `bindKey`, `preWarmDialogPeer`, `initLaf`, `readComboValuesFromFile` | none | none | adequate | framework wiring / side-effects; not unit-testable meaningfully |
| UIUtils | `getApplicationFrame`, `getParentFrame`, `getFocusedFrame`, `getDeepestComponentAt`, `getComponentUnderMouse`, `getParentWindow`, `getScreenBounds` | none | none | adequate | pure Swing delegation; no logic |
| UIUtils | `isEditingTextIn` — focus-owner check; `isShowing()` guard | unit | `EditLyricActionTest` (mocks `UIUtils.isEditingTextIn`) + direct call with `new JFrame()` at line 92 | inadequate | the direct call at line 92 (`assertThat(UIUtils.isEditingTextIn(new JFrame())).isFalse()`) asserts only the `false` branch in a trivial no-focus context; the `JTextComponent.isShowing()` guard and window-match branch are untested |
| DesktopUtils | `isDesktopSupported` / `getDesktop` / `browse` / `mail` / `open` / `invokeDesktopMethod` — all JNA/reflection-based platform interaction | none | none | adequate | reflection on `java.awt.Desktop`; testable only via real system integration |

**4C notes (quality concerns):**

The highest-risk dark gaps in this package are all in the pure-logic classes that have **zero unit tests**: `StringUtils` (8 methods including the complex `wrapText` rebalancing algorithm, which silently truncates lines when the previous line is too short to donate words), `FileUtils` (path/extension handling including the multi-extension `ensureExtension` with a case-insensitive match), `Utils.getPlatformKeyStrokeString` (a multi-branch string formatter with different output per platform), and `GraphicUtils.convertFromPixels`/`convertToPixels` (a pixel↔physical-unit conversion that feeds the paper-size dialog — a rounding error here produces silent data corruption in saved documents). The `wrapText` method in particular has non-trivial state (`MINIMUM_WRAPPED_WORD_COUNT` rebalancing) that is not tested at all; a mutant that inverts the comparison or drops the rebalancing pass would never be caught. `MyFontUtils.parsePSName` and `parseStyle` drive font-name display throughout the UI and have a number of undocumented edge cases (Damascus-style embedded style, compound style normalization) with no tests. The two existing tests — `MyFontUtilsTest` and `GraphicUtilsClampTest` — are structurally sound but narrow: `MyFontUtilsTest` only checks that `createFont` returns a non-null `Font` with the requested size, which cannot catch wrong-font selection; `GraphicUtilsClampTest` is adequate for `clampToScreen` but leaves the multi-monitor branch and all conversion methods untested. `UIUtils.positionDialog` has the most misleading coverage: `BaseDialogPositionTest` stubs out `UIUtils` entirely, so the positioning arithmetic (3/8 formula, `SCREEN_MARGIN_PX` clamping) has never been asserted against — it is a green but hollow test. Two confirmed dead methods: `StringUtils.removeSyllabifyMarkings` (zero callers in the whole codebase) and `MyFontUtils.getXHeight` (zero callers confirmed by `jet_brains_find_referencing_symbols`); both should be deleted or have a caller introduced before tests are written.

### 4D. `smufl`

Audited by reading each production class body symbol-by-symbol with serena `jet_brains_find_symbol` (include_body=true), enumerating all testable behaviors, then searching `src/test/java/songscribe/smufl/` and cross-package test files via grep for any existing coverage of each behavior.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `BBox` | `width()` = right − left | unit | none found | missing | Add `BBoxTest.testWidthIsRightMinusLeft` |
| `BBox` | `height()` = bottom − top | unit | none found | missing | Add `BBoxTest.testHeightIsBottomMinusTop` |
| `BBox` | `translateX(dx)` shifts left and right by dx, leaves top/bottom unchanged | unit | none found | missing | Add `BBoxTest.testTranslateXShiftsHorizontallyOnly` |
| `BBox` | `union` returns smallest enclosing box (min left/top, max right/bottom) | unit | none found | missing | Add `BBoxTest.testUnionReturnsSmallestEnclosingBox` |
| `BBox` | `fromSMuFL` flips Y-up to Y-down (top=−neY, bottom=−swY) | unit | none found | missing | Add `BBoxTest.testFromSmuflFlipsYConvention` |
| `BBox` | record component accessors (left, top, right, bottom) | none | — | adequate (none warranted) | — |
| `GlyphAnchors` | `requireStemUpSE` returns anchor when present | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemUpSEReturnsAnchorWhenPresent` |
| `GlyphAnchors` | `requireStemUpSE` throws when stemUpSE is null | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemUpSEThrowsWhenNull` |
| `GlyphAnchors` | `requireStemDownNW` returns anchor when present | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemDownNWReturnsAnchorWhenPresent` |
| `GlyphAnchors` | `requireStemDownNW` throws when stemDownNW is null | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemDownNWThrowsWhenNull` |
| `GlyphAnchors/Anchor` | `fromSMuFL` flips Y (y becomes −y) | unit | none found | missing | Add test for `Anchor.fromSMuFL` Y-flip |
| `GlyphAnchors` | record component accessors (stemUpSE, stemDownNW, cutOutNW, cutOutSE — all `@Nullable`) | none | — | adequate (none warranted) | — |
| `SMuFLData` | pure data record, no logic | none | — | adequate (none warranted) | — |
| `SMuFLGlyph` | `smuflName()` returns canonical SMuFL name string | unit | none found | missing | Add `SMuFLGlyphTest.testSmuflNameMatchesSpec` (spot-check a few constants) |
| `SMuFLGlyph` | `codepoint()` returns correct Unicode codepoint | unit | none found | missing | Add `SMuFLGlyphTest.testCodepointMatchesSpec` (spot-check a few constants) |
| `SMuFLGlyph` | `asString()` returns single-character string of codepoint | unit | none found | missing | Add `SMuFLGlyphTest.testAsStringIsSingleCharOfCodepoint` |
| `SMuFLMetadata` | `getBBox` returns populated BBox for a known glyph | unit | indirect via `KeySignatureTest`, `DynamicAttachmentTest`, `ArticulationStackingTest` | inadequate (self-referential: tests use `requireBBox` as their own oracle) | Add direct assertion with concrete numeric value |
| `SMuFLMetadata` | `getBBox` returns null for a glyph absent from the metadata | unit | none found | missing | Add `SMuFLMetadataTest.testGetBBoxReturnsNullForUnknownGlyph` |
| `SMuFLMetadata` | `requireBBox` throws when glyph absent from metadata | unit | none found | missing | Add `SMuFLMetadataTest.testRequireBBoxThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `noteHeadWidthSs` returns correct notehead width in staff spaces | unit | none found | missing | Add `SMuFLMetadataTest.testNoteHeadWidthSsIsPositiveAndPlausible` with concrete bounds |
| `SMuFLMetadata` | `noteHeadHeightSs` returns correct notehead height in staff spaces | unit | none found | missing | Add `SMuFLMetadataTest.testNoteHeadHeightSsIsPositiveAndPlausible` with concrete bounds |
| `SMuFLMetadata` | `getAnchors` returns populated `GlyphAnchors` for a known glyph | unit | none found | missing | Add `SMuFLMetadataTest.testGetAnchorsReturnsAnchorsForKnownGlyph` |
| `SMuFLMetadata` | `getAnchors` returns null for a glyph absent from anchors data | unit | none found | missing | Add `SMuFLMetadataTest.testGetAnchorsReturnsNullForGlyphWithNoAnchors` |
| `SMuFLMetadata` | `requireAnchors` throws when glyph absent from anchors | unit | none found | missing | Add `SMuFLMetadataTest.testRequireAnchorsThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `getAdvanceWidth` returns width for a known glyph | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthReturnsValueForKnownGlyph` |
| `SMuFLMetadata` | `getAdvanceWidth` returns null for a glyph absent from advance widths | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthReturnsNullForAbsentGlyph` |
| `SMuFLMetadata` | `getAdvanceWidthOrZero` returns 0.0 when glyph absent | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthOrZeroReturnsFallbackForAbsentGlyph` |
| `SMuFLMetadata` | `requireAdvanceWidth` throws when glyph absent | unit | none found | missing | Add `SMuFLMetadataTest.testRequireAdvanceWidthThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `getEngravingDefaults` returns SMuFLData with plausible non-zero values | unit | none found | missing | Add `SMuFLMetadataTest.testEngravingDefaultsAreNonZero` |
| `SMuFLMetadata` | `Holder.load()` loads from classpath resource without exception (singleton initializes) | unit | implied by every test that touches `SMuFLMetadata.*` | adequate (singleton load tested implicitly) | — |
| `Engraving` | `G_CLEF_WIDTH_SS` is derived from SMuFL advance width, not hardcoded | unit | `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth` | inadequate (self-referential: expected value is `SMuFLMetadata.requireAdvanceWidth(G_CLEF)` — same call as the production code, so the test cannot detect a wrong value) | Rewrite with a concrete numeric bound or cross-check against a known Bravura value |
| `Engraving` | `BEAM_THICKNESS_SS` / `BEAM_SPACING_SS` / `LEDGER_LINE_THICKNESS_SS` etc. are positive non-zero plausible values | unit | none found | missing | Add `EngravingTest` assertions with concrete plausible bounds |
| `Engraving` | `NOTEHEAD_BLACK_STEM_UP_SE` / `NOTEHEAD_BLACK_STEM_DOWN_NW` anchors are loaded correctly | unit | none found | missing | Add `EngravingTest` assertions checking x/y are non-zero with expected sign |
| `Engraving` | private constructor prevents instantiation | none | — | adequate (none warranted) | — |

**4D notes (quality concerns):**

The most critical gap is that `BBox` — the geometry primitive used in every bounding-box computation across the codebase — has zero direct tests. `translateX` and `union` carry real arithmetic that can silently regress (e.g., a wrong coordinate axis or off-by-one in `union`'s `min`/`max` calls), and the Y-flip in `fromSMuFL` is a sign-convention conversion that is invisible in integration tests. All five `BBox` behaviors are pure functions with no dependencies and are trivial to test.

The sole existing `smufl` package test, `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth`, is self-referential: both the production constant and the test's expected value are computed from the same `SMuFLMetadata.requireAdvanceWidth(G_CLEF)` call, so the test passes even if the constant were set to any value from that same lookup. It cannot catch a wrong glyph mapping, a unit-conversion error, or a metadata parse regression.

The cross-package tests in `DynamicAttachmentTest` and `KeySignatureTest` that use `SMuFLMetadata.requireBBox(...)` as the oracle for their expected values exhibit the same self-referential defect: they verify structural wiring but cannot detect an incorrectly parsed bbox coordinate.

`GlyphAnchors.requireStemUpSE` and `requireStemDownNW` both have null-guard branches that throw via `RuntimeError.exit`. Neither branch has any test. The same pattern applies to `SMuFLMetadata.requireBBox`, `requireAnchors`, and `requireAdvanceWidth` — the "absent glyph throws" path is completely untested in all four methods. These are all plausible regression sites if the metadata JSON is changed or a new glyph mapping is added.

`SMuFLGlyph` enum accessors (`smuflName()`, `codepoint()`, `asString()`) are spot-checked nowhere. A transposed codepoint or misspelled SMuFL name would silently corrupt rendered glyphs and font metric lookups without any test failing.

### 4E. `prefs`

Audited from production code outward: enumerated every testable behavior in `Prefs`, `PrefsKey`, `RecentDocumentsManager`, and `StartupAction`, classified each by the rubric, then checked `src/test/java/songscribe/prefs/PrefsTest.java` (the only test file in the mirrored package) and cross-package unit tests for coverage.

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|---------------|---------|--------|
| `Prefs` | `getOrDefault`: returns store value when present, falls back to `getDefault` when absent | unit | none | missing | add tests for store-hit and store-miss paths |
| `Prefs` | `getDefault`: throws `IllegalArgumentException` for unknown key (no default in `defaults.json`) | unit | none | missing | add test; critical contract for all scalar getters |
| `Prefs` | `getString`: returns stored string value | unit | none | missing | add round-trip test |
| `Prefs` | `getInt`: casts stored value through `Number.intValue()` — survives if value is `Long` | unit | none | missing | add test; int-stored-as-Long contract matters |
| `Prefs` | `getLong`: analogous to `getInt` | unit | none | missing | add test |
| `Prefs` | `getBoolean`: casts to `Boolean` | unit | none | missing | add test |
| `Prefs` | `getStringList`: returns list from store; returns empty list (not default) when absent | unit | none | missing | add tests for both paths; empty-list contract must be verified |
| `Prefs` | `getStringList`: ignores defaults for list keys (unlike scalar getters) | unit | none | missing | this asymmetry is a likely bug-hiding point |
| `Prefs` | `getMap`: returns store value when present (Map); falls to default when absent; returns empty map when absent and no default | unit | `PrefsTest.testGetMapReturnsEmptyMapForMissingKey`, `testGetMapOnNonMapValueReturnsEmptyMap` | inadequate | `testGetMapReturnsEmptyMapForMissingKey` name is wrong — `DIALOG_GEOMETRY` has a default `{}` in `defaults.json`; the test happens to pass because `{}` deserializes as empty, but it is not testing the "no default" path |
| `Prefs` | `putMap`: merges new entries into existing map | unit | `PrefsTest.testPutMapMergesEntries` | inadequate | asserts only `containsKey` — does not verify values are correct; a mutation that stores the wrong values passes |
| `Prefs` | `putMap` + `getMap` round-trip: stored value is retrievable | unit | `PrefsTest.testPutMapAndGetMapRoundTrip` | inadequate | asserts only `containsKey("TestDialog")` — not the nested map values |
| `Prefs` | `put(PrefsKey, String)`: stores string, triggers save+notification | unit | none | missing | add test |
| `Prefs` | `put(PrefsKey, int)`: stores as `Long` (documented type coercion) | unit | none | missing | critical: only `getInt` works after this if value is `Long`; needs explicit assertion |
| `Prefs` | `put(PrefsKey, long)` and `put(PrefsKey, boolean)`: store and retrieve | unit | none | missing | add tests |
| `Prefs` | `putStringList`: replaces list wholesale (not merge) | unit | none | missing | add test |
| `Prefs` | `reset`: removes key from store, restores default | unit | none (only used in `@AfterEach` teardown, not as a behavior under test) | missing | add test verifying value reverts to default after reset |
| `Prefs` | `resetAll`: clears all overrides | unit | none | missing | add test |
| `Prefs` | `parseJsonValue`: dispatches by JSON type (boolean / number stored as Long / string / object as Map / array → null) | unit | none | missing | high-risk: number-as-Long contract underpins all numeric getters; array→null gap means array values in defaults.json are silently dropped |
| `Prefs` | `writeTyped`: parses string to typed value (Boolean / Long / String) based on default type; ignores invalid numeric strings | unit | none | missing | migration correctness depends on this |
| `Prefs` | `migrate`: reads old `.properties` file, maps keys via `MIGRATION_MAP`, scans `showwhatsnew*` keys for highest version | unit | none | missing | high-risk legacy migration; no test |
| `Prefs` | `removeObsoleteKeys`: strips keys in `OBSOLETE_KEYS` from store and saves | unit | none | missing | add test |
| `Prefs` | `allKeysExistInDefaults`: every `PrefsKey` (except `ALL`) has entry in `defaults.json` | unit | `PrefsTest.testAllKeysExistInDefaults` | adequate | well-written contract guard |
| `PrefsKey` | `key()` returns the camelCase JSON string matching the enum constant | unit | `PrefsTest.testAllKeysExistInDefaults` (indirectly exercises `key()`) | adequate | implicitly covered by the defaults check |
| `PrefsKey` | enum is purely a typed-key holder with no value logic | none | — | — | — |
| `RecentDocumentsManager` | `add`: deduplicates (existing entry moves to front), adds at front of MRU list | unit | none | missing | core MRU logic; no test |
| `RecentDocumentsManager` | `add`: enforces `MAX_SIZE` cap by removing last entries | unit | none | missing | off-by-one risk |
| `RecentDocumentsManager` | `add`: normalizes path before insert | unit | none | missing | normalization correctness |
| `RecentDocumentsManager` | `add`: posts `RecentDocumentsDidChangeNotification` after persist | unit | none | missing | notification contract |
| `RecentDocumentsManager` | `remove`: removes matching normalized path; posts notification | unit | none | missing | add test |
| `RecentDocumentsManager` | `remove`: no-op when path absent (should still persist+notify) | unit | none | missing | verify idempotency |
| `RecentDocumentsManager` | `clear`: empties list, persists, posts notification | unit | none | missing | add test |
| `RecentDocumentsManager` | `getRecents`: returns unmodifiable copy | unit | none | missing | verifies defensive copy |
| `RecentDocumentsManager` | constructor: strips non-existent paths from loaded list and persists if any removed | unit | none | missing | startup cleanup logic; untested |
| `RecentDocumentsManager` | constructor: gracefully skips malformed path strings | unit | none | missing | robustness under corrupt prefs |
| `StartupAction` | pure enum — `DO_NOTHING`, `SHOW_FILE_CHOOSER`, `OPEN_MOST_RECENT` | none | — | — | — |

**4E notes (quality concerns):**

The darkest gap in this package is `RecentDocumentsManager` — it has zero tests despite containing real MRU logic (dedup, cap enforcement, path normalization, constructor-time stale-path pruning) and notification side effects. `Prefs` itself has only five test methods, covering exclusively the `getMap`/`putMap` family; every scalar getter, every `put` overload, `putStringList`, `getStringList`, `reset`, `resetAll`, `parseJsonValue`, `writeTyped`, `migrate`, and `removeObsoleteKeys` are all completely untested. The `migrate` method in particular is high-risk: it touches a one-time destructive file operation (deleting the old `.properties` file) and uses `writeTyped` string-to-typed coercion, both of which could silently corrupt prefs on first launch from an old installation. Three of the four existing tests are inadequate by the Quality Principles: `testGetMapReturnsEmptyMapForMissingKey` has a name mismatch (the key has a default), and both round-trip/merge tests assert only `containsKey` rather than verifying actual stored values — a mutant that stores wrong values would survive all of them. The `Prefs` singleton's all-static API makes it straightforwardly unit-testable (the real singleton initializes from classpath resources during tests); no mocking of the singleton chain is needed here.

### 4F. `font`

Audited from production code outward: enumerated every testable behavior in `DocumentFonts`, `DocumentFontsHolder`, `FontKey`, and `SourceSans3Font`, classified each by the rubric, then checked `src/test/java/songscribe/font/DocumentFontsTest.java` (the only test file in the mirrored package) and e2e test source for coverage.

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|---------------|---------|--------|
| `DocumentFonts` | `getFont(FontKey)`: returns stored font | unit | `DocumentFontsTest.GetSet.testGetFontRoundTrip` | adequate | parameterized over all `FontKey` values; asserts exact font identity |
| `DocumentFonts` | `getFont(FontKey)`: throws `IllegalStateException` (with key name in message) when font not set | unit | `DocumentFontsTest.GetSet.testGetFontThrowsWhenNotSet` | adequate | parameterized; verifies exception type and message content |
| `DocumentFonts` | `setFont(FontKey, Font)`: stores font retrievable by key | unit | `DocumentFontsTest.GetSet.testGetFontRoundTrip` (exercises `setFont(FontKey, Font)`) | adequate | covered as part of round-trip |
| `DocumentFonts` | `setFont(FontKey, String, int)`: resolves font by PS name and size, stores it | unit | `DocumentFontsTest.GetSet.testSetFontByNameRoundTripSize` | inadequate | asserts `font.getSize()` == expected size (adequate), but `assertThat(font.getPSName()).isNotEmpty()` is a weak assertion — does not verify that the resolved PS name matches `BASE_NAME`; a mutant that resolves the wrong font passes |
| `DocumentFonts` | copy constructor: produces independent copy (mutations to copy do not affect original) | unit | `DocumentFontsTest.CopyConstructor.testMutatingCopyDoesNotAffectOriginal` | adequate | |
| `DocumentFonts` | copy constructor: mutations to original do not affect copy | unit | `DocumentFontsTest.CopyConstructor.testMutatingOriginalDoesNotAffectCopy` | adequate | |
| `DocumentFonts` | `equals`: identical content → equal | unit | `DocumentFontsTest.Equals.testEqualIdenticalContent` | adequate | |
| `DocumentFonts` | `equals`: reflexive | unit | `DocumentFontsTest.Equals.testEqualReflexive` | adequate | |
| `DocumentFonts` | `equals`: not equal when font name differs for any key | unit | `DocumentFontsTest.Equals.testNotEqualWhenNameDiffers` | adequate | parameterized over `FontKey` |
| `DocumentFonts` | `equals`: not equal when font size differs for any key | unit | `DocumentFontsTest.Equals.testNotEqualWhenSizeDiffers` | adequate | parameterized over `FontKey` |
| `DocumentFonts` | `equals`: `null` object → not equal | unit | none | missing | add test for `equals(null)` returning false |
| `DocumentFonts` | `equals`: different type → not equal | unit | none | missing | low risk given `instanceof` pattern, but worth one line |
| `DocumentFonts` | `hashCode`: consistent with `equals` | unit | `DocumentFontsTest.Equals.testHashCodeConsistentWithEquals` | adequate | |
| `DocumentFonts` | `defaultsFromPrefs()`: populates all six roles from `Prefs` | unit | `DocumentFontsTest.DefaultsFromPrefs.testAllRolesPopulated` | inadequate | only verifies `getSize()` per role equals the prefs font-size value — does not check the font family name; a mutant that calls the wrong `PrefsKey` font string (or resolves the wrong family) while preserving sizes passes |
| `DocumentFonts` | `defaultsFromPrefs()`: maps each `FontKey` to the correct `PrefsKey` pair (e.g., `TITLE` → `TITLE_FONT` + `TITLE_FONT_SIZE`, not some other key) | unit | none | missing | the authoritative FontKey→PrefsKey mapping is untested; wrong-key bugs are invisible |
| `DocumentFontsHolder` | default methods (`getTitleFont`, etc.): each delegates to `getFont` with the matching `FontKey` | none | — | — | trivial one-liners; delegating to `getFont` already tested |
| `FontKey` | pure enum — six constants, no logic | none | — | — | — |
| `SourceSans3Font` | `installLazy()`: registers family loader via FlatLaf `FontUtils` | none | — | — | risk is real Swing/FlatLaf integration; cannot be meaningfully unit-tested |
| `SourceSans3Font` | `install()`: delegates to `installBasic()` | none | — | — | thin wrapper over FlatLaf font registration |
| `SourceSans3Font` | `installBasic()`: installs six font styles via `MyFontUtils.installLocalFont` | none | — | — | font installation is an integration behavior; testing it requires the AWT font subsystem |
| `SourceSans3Font` | String constants (`FAMILY`, `STYLE_*`): correct PS name strings | unit | none | missing | constant values are the contract for font resolution everywhere in the app; a typo silently falls back to a system font; verify each constant matches the bundled font file name |

**4F notes (quality concerns):**

The highest-risk gap is `defaultsFromPrefs` coverage: the existing `testAllRolesPopulated` checks only that each role's font size equals its prefs value. It does not verify the family name, which means any wrong-key assignment in the six-line mapping (e.g., swapping `TITLE_FONT` and `LYRICS_FONT`) goes undetected — a plausible cut-paste error given the repetitive structure. The test should additionally assert `font.getFamily()` (or `font.getPSName()`) against `Prefs.getString(PrefsKey.TITLE_FONT)` etc. for each role. Similarly, `testSetFontByNameRoundTripSize` uses `isNotEmpty()` for the PS name instead of asserting the exact resolved value — a weak assertion that survives any font being substituted. The `SourceSans3Font` constant strings (`FAMILY`, `STYLE_REGULAR`, etc.) are the contract for all font lookups across the application; a typo in any constant causes silent font fallback at runtime, yet no test verifies them against the bundled file names. `DocumentFontsHolder` default methods are trivial enough to classify as `none`. No dead code was identified: `DocumentFontsHolder` is implemented by both `ScoreView` and `DocumentFonts`, and `SourceSans3Font` is called from `UIUtils.initLaf`.

### 4G. `export`

Audited by reading all seven production class bodies; stub/IO-dispatch classes have no branching logic worth testing, but `PageLayoutData.applyMarginOverrides` and `PDFExporter.createPDF` contain real conditional computation.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ExportOptions` | record construction; ALL / NONE constants encode correct boolean triples | unit | none | missing | add unit: verify ALL=(true,true,true), NONE=(false,false,false), and round-trip equality |
| `PageLayoutData` | `applyMarginOverrides`: default applied to all four margins when overrides are -1 | unit | none | missing | add unit: call with all -1 overrides, assert all four fields equal defaultMargin |
| `PageLayoutData` | `applyMarginOverrides`: per-edge override replaces default when value > -1 | unit | none | missing | add unit: supply distinct per-edge values, assert each field independently |
| `PageLayoutData` | `applyMarginOverrides`: boundary — value exactly 0 overrides (> -1) | unit | none | missing | add unit: value=0 should override (currently: 0 > -1 is true) |
| `PDFExporter` | `createPDF`: returns early (no crash) when `data.scoreView` is null | unit | none | missing | add unit: construct PageLayoutData with scoreView=null, call createPDF, assert no exception |
| `PDFExporter` | `createPDF`: scale = min(horizontalScale, verticalScale) — horizontal-constrained branch | unit | none | missing | add unit with mock ScoreView; verify scale and leftMargin under each branch |
| `PDFExporter` | `createPDF`: leftMargin redistribution when `horizontalScale >= verticalScale` | unit | none | missing | same unit as above; assert leftMargin = scaledMargin * (leftInner / (leftInner + rightOuter)) |
| `ABCExporter` | `createABC`: stub — shows error dialog, no logic | none | none | — | no test warranted (pure dialog dispatch, no computation) |
| `ImageExporter` | `createImageForExport[0]`: image dimensions = (sheetWidthPx * scale + borderWidth, sheetHeightPx(opts) * scale + borderHeight) | unit | none | missing | add unit with mock ScoreView and border; assert BufferedImage dimensions |
| `ImageExporter` | `createImageForExport[1]`: renders without exception (stub body, but dimensions/type are real) | none | none | — | body is a stub ("not yet implemented" drawString); no assertion value until implemented |
| `SVGExporter` | `createSVG`: stub — shows error dialog | none | none | — | no test warranted |
| `ExportUtils` | `openExportedFile`: Swing dialog dispatch; no computation | none | none | — | no test warranted |

**4G notes (quality concerns):**

The highest-risk dark gaps are `PageLayoutData.applyMarginOverrides` (four independent conditional branches, all untested — any off-by-one in the threshold guard or a field assignment to the wrong variable would survive indefinitely) and `PDFExporter.createPDF` (non-trivial margin redistribution math under the `horizontalScale >= verticalScale` branch, also completely untested). The `ExportOptions` record is trivial but its constants are contract-defining — a future edit that silently flips a boolean in ALL or NONE would have no safety net. `ImageExporter.createImageForExport[0]` does compute the output image dimensions from scale and border, making the dimension formula testable right now even though the rendering body is a stub. `ABCExporter`, `SVGExporter`, and `ExportUtils` are pure dialog-dispatch stubs with no branching logic and correctly classify as none. There is no dead code in this package: all classes are reachable from UI action paths.

### 4H. `uiconverter`

Audited by reading all three production class bodies; `ChooseDirectoryAction` is pure Swing wiring with no logic, but `UIConverter.isLegalFileName` is a pure predicate and `ConvertAction.ConvertThread` contains an image-scale formula that is unit-testable in isolation.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `UIConverter` | `isLegalFileName`: rejects names shorter than 10 chars | unit | none | missing | add unit: names of length 9 or less must return false |
| `UIConverter` | `isLegalFileName`: rejects name that does not end with `.mssw` | unit | none | missing | add unit: name with wrong extension returns false |
| `UIConverter` | `isLegalFileName`: rejects name whose first three chars are not all digits | unit | none | missing | add unit: each non-digit position in chars 0-2 returns false |
| `UIConverter` | `isLegalFileName`: accepts space separator at char 3 | unit | none | missing | add unit: `"001 Title.mssw"` returns true |
| `UIConverter` | `isLegalFileName`: accepts dash separator at char 3 | unit | none | missing | add unit: `"001-Title.mssw"` returns true |
| `UIConverter` | `isLegalFileName`: rejects any other char 3 (e.g. `'a'`) | unit | none | missing | add unit: `"001aTitl.mssw"` (length >= 10) returns false |
| `UIConverter` | `isLegalFileName`: boundary — exactly 10-char valid name accepted | unit | none | missing | add unit: `"001 a.mssw"` (length=10) returns true |
| `UIConverter` | `main`: public static entry point; called from `SongScribe.main` | none | none | — | entry point; not dead; no unit test warranted (Swing bootstrap) |
| `ConvertAction` | image scale formula: `scale = (IMAGE_WIDTH[i] - 2*LEFT_RIGHT_MARGIN[i]) / sheetWidthPx` | unit | none | missing | add unit with known IMAGE_WIDTH, LEFT_RIGHT_MARGIN, and sheetWidthPx; assert exact double scale |
| `ConvertAction` | `actionPerformed`: empty directory text → error path (no crash, no conversion) | unit | none | missing | add unit: mock `songsDirectory.getText()` returning empty string; verify early return (or via public observable) |
| `ConvertAction` | `actionPerformed`: non-existent directory → error path | unit | none | missing | add unit: supply non-existent path; verify early return |
| `ConvertAction` | `actionPerformed`: directory with zero legal files → error path | unit | none | missing | add unit: real temp directory with no `.mssw` files; verify early return |
| `ConvertAction` | `ConvertThread.run`: full batch conversion (file I/O, ScoreView, MIDI, image write) | e2e | none | missing | reserve for e2e; requires real Swing + file I/O pipeline — too costly to mock completely |
| `ChooseDirectoryAction` | `actionPerformed`: fires `DIRECTORY_CHANGE_PROPERTY` when dialog confirms | none | none | — | pure Swing event dispatch; no computation; not warranted |
| `UIConverter/DirectorySelectionChangeListener` | `handleDirectoryChange`: null listFiles → error path | none | none | — | Swing state mutation (table model, text field) — risk is wiring, not logic; none |
| `UIConverter/DirectorySelectionChangeListener` | `handleDirectoryChange`: populates accepted/rejected tables per `isLegalFileName` | none | none | — | file-enumeration result depends on isLegalFileName (already unit-tested above) and Swing model mutation; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: null input (user cancels dialog) → returns silently | none | none | — | Swing dialog interaction; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: invalid number string → NumberFormatException path | none | none | — | Swing dialog interaction; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: out-of-range number (< 1 or > 999) → error path | none | none | — | Swing dialog interaction; none |
| `UIConverter/NumberSongAction` | `handleNumberSong`: zero-padding format `%03d` and `isLegalFileName` re-check on renamed file | unit | none | missing | extract `buildNumberedFileName(String baseName, int number)` to a package-private helper; test format and legality |

**4H notes (quality concerns):**

The highest-risk dark gap is `UIConverter.isLegalFileName`: it is called from three sites (directory scan, file filter in ConvertAction, rename validation in NumberSongAction) and has six independent branch conditions, all untested. A single wrong char-index or off-by-one in the length guard would silently accept or reject files at every call site. The image-scale formula in `ConvertAction.ConvertThread` is pure arithmetic (`(IMAGE_WIDTH[i] - 2 * LEFT_RIGHT_MARGIN[i]) / sheetWidthPx`) but is currently private and embedded in a thread body; extracting it to a package-private static would allow a direct unit test without mocking the entire pipeline. The `NumberSongAction.handleNumberSong` zero-padding and re-validation logic is also pure computation that cannot currently be tested without Swing; that logic should be extracted to be testable. The `ConvertThread.run` full-pipeline path (open file → write mssw → produce images → produce MIDI → optional zip) genuinely requires the real ScoreView and file system and warrants a single e2e test for the happy path and the per-file error paths. `ChooseDirectoryAction` and the `DirectorySelectionChangeListener` table-population logic are Swing wiring with no testable computation beyond `isLegalFileName`. `UIConverter.main` is a legitimate entry point (dispatched from `SongScribe.main`) — not dead, and not a candidate for unit testing.

### §4 — summary

Audited all ~50 production classes across the eight packages (excl. `package-info`) in two waves of parallel production-first sub-audits. Dominant patterns to drive remediation:

1. **Whole packages / subsystems are dark.** `converter` has **zero** tests of any kind (no test dir, no cross-package references). `prefs.RecentDocumentsManager` (full MRU logic) has zero tests, and `Prefs`' five existing methods cover only the `getMap`/`putMap` family — every scalar getter, `put` overload, `parseJsonValue`, `writeTyped`, `migrate`, and `removeObsoleteKeys` are untested. Most of `util`'s pure-logic helpers and all of `smufl`'s geometry/lookup logic are untested.
2. **Pure computation is the biggest blind spot — and it is high-risk.** `midi.LineTrackBuilder.getTupletFactor` (log2 tuplet timing, 3 branches), `MidiSequenceBuilder.buildSequenceWithRepeats` (repeat state machine + first/second endings), `MidiEventFactory.addTempoEvent` (3-byte big-endian SET_TEMPO — byte-order mutation = silent wrong tempo), `smufl.BBox` (`union`/`translateX`/`fromSMuFL` Y-flip), `util.StringUtils.wrapText` + `GraphicUtils` px↔unit conversions + `Utils.getPlatformKeyStrokeString`, `export.PageLayoutData.applyMarginOverrides` + `PDFExporter.createPDF` margin math, `uiconverter.UIConverter.isLegalFileName` (6 branches, 3 call sites), and `Prefs` typed coercion + `migrate` — all dark.
3. **Weak-but-green / self-referential tests persist (same pattern as Sessions 1/3).** Self-referential oracles: `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth` and the cross-package `requireBBox`-as-its-own-oracle usages. Hollow assertions: midi `isNotEmpty()`/`hasSizeGreaterThanOrEqualTo(4)` on bend/CC events, `MyFontUtilsTest` (`isNotNull()`+`getSize()` only), `Prefs` map tests (`containsKey`-only + a name-mismatch on the "missing key" test), `DocumentFonts.defaultsFromPrefs` (size-only, family unverified), `setFont`-by-name (`isNotEmpty()`). Mocked-out-and-hollow: `UIUtils.positionDialog` is "covered" by `BaseDialogPositionTest`, which stubs `UIUtils` entirely — the 3/8 + `SCREEN_MARGIN_PX` arithmetic is never asserted.
4. **Name/behavior mismatch:** `GlissandoMidiIntegrationTest.testNoPitchBendWithoutGlissando` reads only the fixture model and never builds a MIDI track — MIDI mutations leave it green.
5. **Untested error/throw paths:** `smufl` `require*` absent-glyph throws (all four) + `GlyphAnchors` null guards, `converter.ArgumentReader` unknown-flag/file-not-found/`System.exit` paths, `Prefs.getDefault` IAE for unknown key.
6. **Dead code surfaced (delete in remediation, don't test), verified zero refs:** `util.StringUtils.removeSyllabifyMarkings` (+ its `HYPHEN_UNDERSCORE_PATTERN`/`IN_PARENTHESES_PATTERN`) and `util.MyFontUtils.getXHeight`.
7. **Production observations filed as a tracked issue (#409):** dead code (#1); `converter.SVGConverter.main` is package-private (cannot serve as a JVM entry point — latent bug); `Prefs.parseJsonValue` maps JSON arrays to `null`, silently dropping any array-valued default in `defaults.json`; `Prefs.getStringList` ignores defaults (asymmetric with scalar getters).
8. **Testability-over-encapsulation extractions recommended:** widen `ConvertAction`'s embedded image-scale formula and `UIConverter.NumberSongAction.buildNumberedFileName` to package-private statics for direct unit tests.
9. **Only one genuine e2e escalation:** `uiconverter.ConvertAction.ConvertThread.run` (full batch pipeline through real `ScoreView` + filesystem). Everything else in scope is unit or none; `converter`/`export`/`uiconverter` file-IO/render orchestration with no branching is correctly `none`.

