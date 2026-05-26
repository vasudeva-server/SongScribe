### 2A. orchestration & XML — `SongIO`, `SongLoader`, `SongLoadResult`, `XML`

#### SongIO

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| SongIO | `writeSong` — emits XML header and `<song>` root with version attribute (`IO_MAJOR_VERSION.IO_MINOR_VERSION`) | unit | `SongIOTest` (round-trip, indirect) | inadequate | literal version value never asserted; add direct serialized-string assertion that version attribute equals current `IO_MAJOR.IO_MINOR` | ⬜ |
| SongIO | `writeSong` — tempo block emitted only when song tempo non-null | unit | — | missing | null tempo → no `<tempo>`; with tempo → present | ⬜ |
| SongIO | `writeSong` — title/place/year/attribution/underlyrics/banglaLyrics/translatedLyrics omitted when empty | unit | — | missing | parametrized: empty → tag absent; non-empty → present, XML-escaped | ⬜ |
| SongIO | `writeSong` — month/day omitted when ≤ 0; emitted when > 0 | unit | — | missing | month=0 → absent; month=3 → `<month>3</month>` | ⬜ |
| SongIO | `writeSong` — `unofficialTranslation` emitted only when true | unit | — | missing | false → absent; true → present | ⬜ |
| SongIO | `writeSong` — `topspace` emitted only when `userSetTopPadding()` | unit | — | missing | false → absent; true → present | ⬜ |
| SongIO | `writeSong` — `rowheight` omitted when exactly 0 | unit | — | missing | 0 → absent; non-zero → present | ⬜ |
| SongIO | `writeSong` — `dynamicLayout=true` always written | unit | — | missing | output always contains `<dynamicLayout>true</dynamicLayout>` | ⬜ |
| SongIO | `writeSong` — `linewidth` always written | unit | `SongIOTest.LegacyMigrationWiring.*` (round-trip) | adequate | round-trip recovers value; direct serialized-string assertion optional | — |
| SongIO | `writeSong` — all lines serialized in order via `LineIO.writeLine` | unit | `SongIOTest.testParsedLinesHaveSongSet` (parse only) | inadequate | multi-line round-trip asserting per-line element counts | ⬜ |
| SongIO | `writeSong` — `<view>` block always written | unit | — (`writeView` never called by any test, per 2C) | missing | add direct write test: serialized output contains `<view>…</view>` | ⬜ |
| SongIO | `DocumentReader.startElement` — v1.0 dispatch creates `StaffElementReader`+`TempoReader`, not `LineReader` | unit | — | missing | parse v1.0 `<song>`, assert notes load | ⬜ |
| SongIO | `DocumentReader.startElement` — v1.1 dispatch creates `LineReader`+`ViewReader` | unit | `StaffElementIOTest` (v1.1 fixtures) | adequate | keep | — |
| SongIO | `DocumentReader.startElement` — 2.x up to `IO_MINOR_VERSION` accepted; `2.(IO_MINOR+1)` throws `NewerVersionException` | unit | `SongIOTest.testOpeningNewerVersionFileThrowsNewerVersionException` | adequate | keep; add boundary test `2.IO_MINOR` accepted | — |
| SongIO | `DocumentReader.startElement` — non-numeric version → `SAXException` wrapping `NumberFormatException` | unit | — | missing | version="abc" → SAXException | ⬜ |
| SongIO | `endElement10` — `<notes>`/`<tempo_changes>` restore `where=SONG` | unit | — | missing | part of v1.0 load test | ⬜ |
| SongIO | `endElement10` — tempo at pos 0 → song-level; pos N → attached to element across multi-line flat layout | unit | — | missing | two-line v1.0 doc with multiple tempo-change positions | ⬜ |
| SongIO | `endElement10` — empty `parsedLines` → first `Line` created on first note | unit | — | missing | part of v1.0 load test | ⬜ |
| SongIO | `endElement11` — grace notes set `upper=true` (v1.1-only post-processing) | unit | — | missing | parse v1.1 grace note, assert `isUpper()==true` | ⬜ |
| SongIO | `endElement12` — `<lines>`/`<view>` restore `where=SONG` | unit | `SongIOTest.*` (modern versions) | adequate | keep | — |
| SongIO | `endElement12` — field mapping (keys, keytype, number, title empty→"Untitled", place, year, month, day, underLyrics, banglaLyrics, translatedLyrics, attribution, footnotes, unofficialTranslation) | unit | — | missing | round-trip all fields non-default; title-empty→"Untitled" branch separately | ⬜ |
| SongIO | `endElement12` — `parseVersionedDouble` (<2.1 `Integer.parseInt`; ≥2.1 `Double.parseDouble`) for topspace/rightinfostarty/rowheight/linewidth | unit | `SongIOTest.LegacyMigrationWiring.testPre21Converts…` + `testBuggyLineWidthIsCorrectedOnLoad` | adequate | keep | — |
| SongIO | `getSong` — `parsingSong==null` → `IllegalStateException` | unit | — | missing | empty XML (no root) → ISE | ⬜ |
| SongIO | `getSong` — migration pipeline runs pre- and post-assembly | unit | `SongIOTest.LegacyMigrationWiring` (5) | adequate | keep | — |
| SongIO | `getDocumentFonts` — returns `ViewReader` fonts when present, else `DocumentFonts.defaultsFromPrefs()` (v1.0) | unit | `ViewIOTest` (v1.1+); v1.0 fallback untested | inadequate | parse v1.0 doc (no `<view>`), assert defaults returned | ⬜ |
| SongIO | `NewerVersionException` message text | none | — | none | trivial static message | — |

#### SongLoader

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| SongLoader | `load` — missing file → `IoError` w/ `IOException` cause | unit | `SongLoaderTest.testLoadNonExistentFileReturnsIoError` | adequate | keep | — |
| SongLoader | `load` — corrupt XML → `ParseError` w/ `SAXException` cause | unit | `SongLoaderTest.testLoadDamagedFileReturnsParseError` | adequate | keep | — |
| SongLoader | `load` — newer version → `NewerVersion` w/ cause | unit | `SongLoaderTest.testLoadNewerVersionFileReturnsNewerVersion` | adequate | keep | — |
| SongLoader | `load` — valid file → `Success` w/ non-null song+fonts | unit | `SongLoaderTest.testLoadValidFileReturnsSuccess` | adequate | keep (isNotNull substantive: null ⇒ broken read path) | — |
| SongLoader | `load` — `ParserConfigurationException` → `ParseError` | unit | — | missing | `PARSER_FACTORY` is private static final; triggering `newSAXParser()` to throw requires env manipulation — not unit-testable; noted | ✅ |
| SongLoader | `load` — `Success.song()` fully assembled (fields preserved) | unit | `SongLoaderTest.testLoadValidFileReturnsSuccess` | adequate | added `lineCount() >= 1` and `line(0).elementCount() >= 22` assertions vs `full-line` fixture | ✅ |
| SongLoader | `load` — `Success.fonts()` non-null w/ expected roles from `<view>` | unit | same (isNotNull only) | inadequate | assert a known `FontKey` resolves from the fixture's `<view>` block | ⬜ |

#### SongLoadResult

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| SongLoadResult | `Success`/`IoError`/`ParseError`/`NewerVersion`/`LineWidthTooLarge` records — carry their components | none | — | none | pure data records | — |
| SongLoadResult | `songOrThrow()` — `Success` branch returns the song | unit | `SongLoadResultTest.testSongOrThrowOnSuccessReturnsSong` | adequate | `new Success(song,fonts).songOrThrow()` returns same instance | ✅ |
| SongLoadResult | `songOrThrow()` — `IoError` branch throws wrapped `IOException` | unit | `SongLoadResultTest.testSongOrThrowOnIoErrorThrowsIOException` | adequate | write test | ✅ |
| SongLoadResult | `songOrThrow()` — `ParseError` branch throws wrapped `SAXException` | unit | `SongLoadResultTest.testSongOrThrowOnParseErrorThrowsSAXException` | adequate | write test | ✅ |
| SongLoadResult | `songOrThrow()` — `NewerVersion` branch throws `NewerVersionException` | unit | `SongLoadResultTest.testSongOrThrowOnNewerVersionThrowsNewerVersionException` | adequate | write test | ✅ |
| SongLoadResult | `songOrThrow()` — `LineWidthTooLarge` branch throws `IOException` w/ both inch values in message | unit | `SongLoadResultTest.testSongOrThrowOnLineWidthTooLargeThrowsIOExceptionWithBothInchValues` | adequate | assert message includes `actualInches` and `maxInches` | ✅ |
| SongLoadResult | `Failure` sealed `file()` accessor on all variants | none | — | none | compiler-enforced | — |

#### XML

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| XML | `escapeXML` — `&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`, `"`→`&quot;` (four specials) | unit | `XMLTest.testEscapeXMLAmpersand`, `testEscapeXMLLessThan`, `testEscapeXMLGreaterThan`, `testEscapeXMLDoubleQuote`, `testEscapeXMLAllFourSpecialsCombined` | added | one test per special + a combined-all-four test | ✅ |
| XML | `escapeXML` — no-specials passthrough; empty → empty | unit | `XMLTest.testEscapeXMLNoSpecialsPassthrough`, `testEscapeXMLEmptyStringReturnsEmpty` | added | write both | ✅ |
| XML | `writeValue(pw,tag,value)` — `<tag>escaped</tag>` on its own line | unit | `XMLTest.testWriteValueProducesTaggedLine`, `testWriteValueEscapesSpecialCharacters` | added | `StringWriter` test (indent + tag + escape + close) | ✅ |
| XML | `writeValue` — indent prefix applied | unit | `XMLTest.testWriteValueRespectsIndent` | added | `setIndent(2)` → line starts with two spaces | ✅ |
| XML | `writeEmptyTag` → `<tag />`; `writeBeginTag` → `<tag>`; `writeEndTag` → `</tag>` (each indented, own line) | unit | `XMLTest.testWriteEmptyTagProducesSelfClosingTag`, `testWriteBeginTagProducesOpenTag`, `testWriteEndTagProducesCloseTag`, `testWriteTagsRespectIndent` | added | write test each | ✅ |
| XML | `setIndent`/`printIndent` — shared static state | unit | `XMLTest.testSetIndentFourProducesFourSpacePrefix` | added | `setIndent(4)` → 4-space prefix; flag static-field non-thread-safe (production concern) | ✅ |
| XML | `writeValue` round-trip — content read back unchanged by SAX | unit | `XMLTest.testWriteValueRoundTripViaXMLParser` | added | direct XML test faster/pinpoints; current fixtures never contain `& < > "` so escaping is unverified | ✅ |

**2A notes (quality concerns):** The three highest-risk gaps: (1) **`XML.escapeXML` has zero direct tests** — every saved string flows through it, and the `SongIOTest` round-trips use only escape-safe fixtures, so a regression mishandling `"`/`&`/`<`/`>` in a title or attribution would silently corrupt every saved file. (2) **`writeSong` conditional-emission logic is entirely untested** — presence/absence of tags depends on runtime conditions (null tempo, empty strings, month/day > 0, `userSetTopPadding`), none directly asserted; round-trips only verify value preservation, not correct omission. (3) **The v1.0 load path** (`startElement10`/`endElement10`, flat-notes + `<tempo_changes>` + positional mapping) has no test or fixture whatsoever. `SongLoaderTest` classifies errors well but `testLoadValidFileReturnsSuccess` only asserts non-null song/fonts (doesn't verify content). `SongLoadResult.songOrThrow()` — the primary load API for converters — has no direct assertion on any of its five branches, the `LineWidthTooLarge` compound-message branch being the riskiest.

