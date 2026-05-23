## 2. `io` (audited 2026-05-21)

Audited all 15 production classes (excl. `package-info`) via four parallel production-first sub-audits: **orchestration & XML**; **element & annotation serialization**; **line & view serialization**; **migration & legacy import**. Read-only; e2e assessed from source only; no `io` behavior warranted e2e (serialization/migration is data-driven logic — prime unit territory). Coverage checked across unit (mirrored + cross-package) and e2e. Five verdicts reclassified from the sub-audits' `wrong-level` (vocabulary reserves that for unit↔e2e mismatches; these are unit tests covered only indirectly).

### 2A. orchestration & XML — `SongIO`, `SongLoader`, `SongLoadResult`, `XML`

#### SongIO

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| SongIO | `writeSong` — emits XML header and `<song>` root with version attribute (`IO_MAJOR_VERSION.IO_MINOR_VERSION`) | unit | `SongIOTest` (round-trip, indirect) | inadequate | literal version value never asserted; add direct serialized-string assertion that version attribute equals current `IO_MAJOR.IO_MINOR` |
| SongIO | `writeSong` — tempo block emitted only when song tempo non-null | unit | — | missing | null tempo → no `<tempo>`; with tempo → present |
| SongIO | `writeSong` — title/place/year/attribution/underlyrics/banglaLyrics/translatedLyrics omitted when empty | unit | — | missing | parametrized: empty → tag absent; non-empty → present, XML-escaped |
| SongIO | `writeSong` — month/day omitted when ≤ 0; emitted when > 0 | unit | — | missing | month=0 → absent; month=3 → `<month>3</month>` |
| SongIO | `writeSong` — `unofficialTranslation` emitted only when true | unit | — | missing | false → absent; true → present |
| SongIO | `writeSong` — `topspace` emitted only when `userSetTopPadding()` | unit | — | missing | false → absent; true → present |
| SongIO | `writeSong` — `rowheight` omitted when exactly 0 | unit | — | missing | 0 → absent; non-zero → present |
| SongIO | `writeSong` — `dynamicLayout=true` always written | unit | — | missing | output always contains `<dynamicLayout>true</dynamicLayout>` |
| SongIO | `writeSong` — `linewidth` always written | unit | `SongIOTest.LegacyMigrationWiring.*` (round-trip) | adequate | round-trip recovers value; direct serialized-string assertion optional |
| SongIO | `writeSong` — all lines serialized in order via `LineIO.writeLine` | unit | `SongIOTest.testParsedLinesHaveSongSet` (parse only) | inadequate | multi-line round-trip asserting per-line element counts |
| SongIO | `writeSong` — `<view>` block always written | unit | — (`writeView` never called by any test, per 2C) | missing | add direct write test: serialized output contains `<view>…</view>` |
| SongIO | `DocumentReader.startElement` — v1.0 dispatch creates `StaffElementReader`+`TempoReader`, not `LineReader` | unit | — | missing | parse v1.0 `<song>`, assert notes load |
| SongIO | `DocumentReader.startElement` — v1.1 dispatch creates `LineReader`+`ViewReader` | unit | `StaffElementIOTest` (v1.1 fixtures) | adequate | keep |
| SongIO | `DocumentReader.startElement` — 2.x up to `IO_MINOR_VERSION` accepted; `2.(IO_MINOR+1)` throws `NewerVersionException` | unit | `SongIOTest.testOpeningNewerVersionFileThrowsNewerVersionException` | adequate | keep; add boundary test `2.IO_MINOR` accepted |
| SongIO | `DocumentReader.startElement` — non-numeric version → `SAXException` wrapping `NumberFormatException` | unit | — | missing | version="abc" → SAXException |
| SongIO | `endElement10` — `<notes>`/`<tempo_changes>` restore `where=SONG` | unit | — | missing | part of v1.0 load test |
| SongIO | `endElement10` — tempo at pos 0 → song-level; pos N → attached to element across multi-line flat layout | unit | — | missing | two-line v1.0 doc with multiple tempo-change positions |
| SongIO | `endElement10` — empty `parsedLines` → first `Line` created on first note | unit | — | missing | part of v1.0 load test |
| SongIO | `endElement11` — grace notes set `upper=true` (v1.1-only post-processing) | unit | — | missing | parse v1.1 grace note, assert `isUpper()==true` |
| SongIO | `endElement12` — `<lines>`/`<view>` restore `where=SONG` | unit | `SongIOTest.*` (modern versions) | adequate | keep |
| SongIO | `endElement12` — field mapping (keys, keytype, number, title empty→"Untitled", place, year, month, day, underLyrics, banglaLyrics, translatedLyrics, attribution, footnotes, unofficialTranslation) | unit | — | missing | round-trip all fields non-default; title-empty→"Untitled" branch separately |
| SongIO | `endElement12` — `parseVersionedDouble` (<2.1 `Integer.parseInt`; ≥2.1 `Double.parseDouble`) for topspace/rightinfostarty/rowheight/linewidth | unit | `SongIOTest.LegacyMigrationWiring.testPre21Converts…` + `testBuggyLineWidthIsCorrectedOnLoad` | adequate | keep |
| SongIO | `getSong` — `parsingSong==null` → `IllegalStateException` | unit | — | missing | empty XML (no root) → ISE |
| SongIO | `getSong` — migration pipeline runs pre- and post-assembly | unit | `SongIOTest.LegacyMigrationWiring` (5) | adequate | keep |
| SongIO | `getDocumentFonts` — returns `ViewReader` fonts when present, else `DocumentFonts.defaultsFromPrefs()` (v1.0) | unit | `ViewIOTest` (v1.1+); v1.0 fallback untested | inadequate | parse v1.0 doc (no `<view>`), assert defaults returned |
| SongIO | `NewerVersionException` message text | none | — | none | trivial static message |

#### SongLoader

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| SongLoader | `load` — missing file → `IoError` w/ `IOException` cause | unit | `SongLoaderTest.testLoadNonExistentFileReturnsIoError` | adequate | keep |
| SongLoader | `load` — corrupt XML → `ParseError` w/ `SAXException` cause | unit | `SongLoaderTest.testLoadDamagedFileReturnsParseError` | adequate | keep |
| SongLoader | `load` — newer version → `NewerVersion` w/ cause | unit | `SongLoaderTest.testLoadNewerVersionFileReturnsNewerVersion` | adequate | keep |
| SongLoader | `load` — valid file → `Success` w/ non-null song+fonts | unit | `SongLoaderTest.testLoadValidFileReturnsSuccess` | adequate | keep (isNotNull substantive: null ⇒ broken read path) |
| SongLoader | `load` — `ParserConfigurationException` → `ParseError` | unit | — | missing | branch exists; may not be unit-testable without env manipulation — note if so |
| SongLoader | `load` — `Success.song()` fully assembled (fields preserved) | unit | `SongLoaderTest.testLoadValidFileReturnsSuccess` (isNotNull only) | inadequate | add field-level assertions (≥ line count, line-0 element count) vs fixture |
| SongLoader | `load` — `Success.fonts()` non-null w/ expected roles from `<view>` | unit | same (isNotNull only) | inadequate | assert a known `FontKey` resolves from the fixture's `<view>` block |

#### SongLoadResult

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| SongLoadResult | `Success`/`IoError`/`ParseError`/`NewerVersion`/`LineWidthTooLarge` records — carry their components | none | — | none | pure data records |
| SongLoadResult | `songOrThrow()` — `Success` branch returns the song | unit | `UnitTest.loadFixture` (implicit, no assert) | inadequate | direct: `new Success(song,fonts).songOrThrow()` returns same instance |
| SongLoadResult | `songOrThrow()` — `IoError` branch throws wrapped `IOException` | unit | — | missing | write test |
| SongLoadResult | `songOrThrow()` — `ParseError` branch throws wrapped `SAXException` | unit | — | missing | write test |
| SongLoadResult | `songOrThrow()` — `NewerVersion` branch throws `NewerVersionException` | unit | — | missing | write test |
| SongLoadResult | `songOrThrow()` — `LineWidthTooLarge` branch throws `IOException` w/ both inch values in message | unit | — | missing | assert message includes `actualInches` and `maxInches` |
| SongLoadResult | `Failure` sealed `file()` accessor on all variants | none | — | none | compiler-enforced |

#### XML

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| XML | `escapeXML` — `&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`, `"`→`&quot;` (four specials) | unit | — | missing | one test per special + a combined-all-four test |
| XML | `escapeXML` — no-specials passthrough; empty → empty | unit | — | missing | write both |
| XML | `writeValue(pw,tag,value)` — `<tag>escaped</tag>` on its own line | unit | — | missing | `StringWriter` test (indent + tag + escape + close) |
| XML | `writeValue` — indent prefix applied | unit | — | missing | `setIndent(2)` → line starts with two spaces |
| XML | `writeEmptyTag` → `<tag />`; `writeBeginTag` → `<tag>`; `writeEndTag` → `</tag>` (each indented, own line) | unit | — | missing | write test each |
| XML | `setIndent`/`printIndent` — shared static state | unit | — | missing | `setIndent(4)` → 4-space prefix; flag static-field non-thread-safe (production concern) |
| XML | `writeValue` round-trip — content read back unchanged by SAX | unit | `SongIOTest.*` (indirect, escape-safe fixtures only) | inadequate | direct XML test faster/pinpoints; current fixtures never contain `& < > "` so escaping is unverified |

**2A notes (quality concerns):** The three highest-risk gaps: (1) **`XML.escapeXML` has zero direct tests** — every saved string flows through it, and the `SongIOTest` round-trips use only escape-safe fixtures, so a regression mishandling `"`/`&`/`<`/`>` in a title or attribution would silently corrupt every saved file. (2) **`writeSong` conditional-emission logic is entirely untested** — presence/absence of tags depends on runtime conditions (null tempo, empty strings, month/day > 0, `userSetTopPadding`), none directly asserted; round-trips only verify value preservation, not correct omission. (3) **The v1.0 load path** (`startElement10`/`endElement10`, flat-notes + `<tempo_changes>` + positional mapping) has no test or fixture whatsoever. `SongLoaderTest` classifies errors well but `testLoadValidFileReturnsSuccess` only asserts non-null song/fonts (doesn't verify content). `SongLoadResult.songOrThrow()` — the primary load API for converters — has no direct assertion on any of its five branches, the `LineWidthTooLarge` compound-message branch being the riskiest.

### 2B. element & annotation serialization — `StaffElementIO`, `AnnotationIO`, `TempoIO`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StaffElementIO | `writeElement` — `type` attribute = `ElementType` name | unit | `StaffElementIOTest.DynamicSerialization.testWritesDynamicElement` (round-trip) | adequate | keep |
| StaffElementIO | `writeElement` — omit `<xpos>` when `xOffsetPx==0`; emit when non-zero | unit | — | missing | assert absent/present around zero |
| StaffElementIO | `writeElement` — always emit `<staffposition>` | unit | — | missing | value preserved |
| StaffElementIO | `writeElement` — omit `<dotted>` when `dotCount==0`; emit for 1/2 | unit | — | missing | test dotCount 0/1/2 |
| StaffElementIO | `writeElement` — omit `<prefix>` when accidental null; emit `name()` otherwise | unit | — | missing | null→absent; SHARP→`<prefix>SHARP</prefix>` |
| StaffElementIO | `writeElement` — emit `<prefixinparenthesis>` only when `isAccidentalInParentheses()` | unit | — | missing | false→absent; true→present |
| StaffElementIO | `writeElement` — ACCENT→`<forcearticulation>`, STACCATO→`<durationarticulation>` | unit | — | missing | one tag per articulation type |
| StaffElementIO | `writeElement` — glissando type serialized; x1/x2 translates omit when 0, emit non-zero | unit | `GraceNoteLyricRoundTripTest` (type only) | inadequate | add translate omit/emit test (zero-translate regression undetectable today) |
| StaffElementIO | `writeElement` — `<stemDirectionAuto>` emitted when `!isStemDirectionAuto()` (inverted) | unit | — | missing | auto=true→absent; auto=false→present |
| StaffElementIO | `writeElement` — `<upper>` emitted when `isUpper()` | unit | — | missing | false→absent; true→present |
| StaffElementIO | `writeElement` — delegates to `TempoIO.writeTempo` when `TempoChangeAttachment` present | unit | — | missing | assert `<tempo>` block in element XML |
| StaffElementIO | `writeElement` — delegates to `AnnotationIO.writeAnnotation` when `AnnotationAttachment` present | unit | — | missing | assert `<annotation>` block |
| StaffElementIO | `writeElement` — `<fermata/>` when `FermataAttachment` present | unit | — | missing | absent without, present with |
| StaffElementIO | `writeElement` — `<dynamic type="…"/>` for `DynamicAttachment` | unit | `StaffElementIOTest.DynamicSerialization.testWritesDynamicElement` | adequate | keep |
| StaffElementIO | `writeElement` — `<beatchange duration beat/>` (new 2-attribute format) | unit | — | missing | assert both attributes correct |
| StaffElementIO | `writeElement` — STOP/CONTINUE carrier emits only `<extend type/>`, no syllabic/text | unit | `SongIOTest.testRoundTripMelismaWithStopCarrier` | adequate | keep |
| StaffElementIO | `writeElement` — syllabic→single/begin/middle/end; null→"single" | unit | `SongIOTest.testRoundTripPerNoteLyrics` (begin/single/end) | inadequate | `middle` and null-syllabic-non-carrier uncovered; add tests |
| StaffElementIO | `writeElement` — compound=true appends `COMPOUND_WORD_MARKER` to `<text>` | unit | `SongIOTest.testRoundTripPerNoteLyrics` | adequate | keep |
| StaffElementIO | `writeElement` — Extend.START emits `<extend type="start"/>` inside lyric | unit | `SongIOTest.testRoundTripPerNoteLyrics`, `testRoundTripMelismaWithStopCarrier` | adequate | keep |
| StaffElementIO | `writeElement` — multi-verse lyric (verse 2) round-trips w/ `number` attribute | unit | — | missing | two Lyric entries at verse 1 and 2 |
| StaffElementIO | `extendTypeAttr(NONE)` throws `IllegalArgumentException` | unit | — | missing | assert IAE (unguarded caller crash today) |
| StaffElementIO | `parseExtendType(null)` → START (legacy bare `<extend/>`) | unit | `SongIOTest.testLegacyExtendTagWithoutTypeLoadsAsStart` | adequate | keep |
| StaffElementIO | `parseExtendType("stop")` → STOP | unit | `SongIOTest.testMusicXmlStopExtendLoadsAsStopCarrier` | adequate | keep |
| StaffElementIO | `parseExtendType("continue")` → CONTINUE | unit | — | missing | `<extend type="continue"/>` mid-melisma → CONTINUE carrier |
| StaffElementIO | `parseExtendType(unknown)` → START (default) | unit | — | missing | `<extend type="bogus"/>` → START |
| StaffElementIO | `ACCIDENTAL_MAP` includes `DOUBLE_SHARP` and `DOUBLESHARP` (no-underscore alias) | unit | — | missing | round-trip each Accidental incl. compound legacy names |
| StaffElementIO | `ACCIDENTAL_MAP` unknown name → IAE wrapped in `SAXException` | unit | `StaffElementIOTest.InvalidMapLookups.testUnknownAccidentalThrowsMeaningfulError` | adequate | keep |
| StaffElementIO | `startElement10` — `NEWLINE`→`where=null` (ignored); `LINE`→SINGLE_BARLINE; `GRACESEMIQUAVER*`→GRACE_QUAVER | unit | — | missing | v1.0 type-alias tests |
| StaffElementIO | `startElement11` — `VERTICALLINE`→SINGLE_BARLINE; `GRACE_SEMIQUAVER*`→GRACE_QUAVER | unit | — | missing | v1.1 type-alias tests |
| StaffElementIO | `endElement11` — legacy `<ypos>` and `<staffposition>` both → `setStaffPosition` | unit | — | missing | v1.0 `<ypos>` yields correct staffPosition |
| StaffElementIO | `endElement11` — legacy `<volume>LOUDER</volume>` → ACCENT articulation | unit | — | missing | write test |
| StaffElementIO | `endElement11` — `<glissando>` numeric content (legacy) → CONNECTED | unit | — | missing | `<glissando>5</glissando>` → CONNECTED |
| StaffElementIO | `endElement11` — `<glissandox1translate>`/`x2translate` set when glissando present, ignored when null | unit | — | missing | non-zero survives; translate w/o glissando doesn't crash |
| StaffElementIO | `endElement11` — `<trill>` sets `trillFlagged=true` | unit | — | missing | `isTrillFlagged()` true |
| StaffElementIO | `endElement11` — `<fermata>` → `FermataAttachment` | unit | — | missing | round-trip test |
| StaffElementIO | `endElement11` — `<stemDirectionAuto>` → `setStemDirectionAuto(false)` (inverted) | unit | — | missing | tag → `isStemDirectionAuto()==false` |
| StaffElementIO | `endElement11` — `<invertfractionbeamorientation>` silently ignored | unit | — | missing | no-throw + no-side-effect |
| StaffElementIO | `endElement11` — legacy `<beatchange>` text-content → `fromLegacyName` | unit | `StaffElementIOTest.InvalidMapLookups.testUnknownBeatChangeThrowsMeaningfulError` (error only) | inadequate | happy paths untested; test each valid legacy name |
| StaffElementIO | `startElement11` — `<beatchange>` new 2-attribute format → `BeatChangeAttachment` directly | unit | — | missing | v2.5+ duration/beat attributes |
| StaffElementIO | `startElement11` — `<dynamic>` unknown type → warn + skip, no attachment | unit | `StaffElementIOTest.DynamicSerialization.testUnknownDynamicType*` (2) | adequate | keep |
| StaffElementIO | `startElement11` — `<dynamic>` valid types → correct `DynamicType` | unit | `StaffElementIOTest.DynamicSerialization.testRoundTripPreservesDynamicType` (6 of 8) | inadequate | `SFORZANDO`/`SFORZATO` excluded — confirm valid and add |
| StaffElementIO | `getSyllabic()` — carrier→null; SINGLE/BEGIN/MIDDLE/END mappings | unit | `SongIOTest.PerNoteLyricSerialization` (partial) | inadequate | `middle` and absent-syllabic-defaulting-to-SINGLE uncovered |
| StaffElementIO | `where==null` (NEWLINE) null-guard paths in endElement/characters | unit | — | missing | NEWLINE element absent; subsequent elements still parse |
| StaffElementIO | grace-note lyric round-trip + direct-load persistence | unit | `GraceNoteLyricRoundTripTest` (2) | adequate | keep |
| StaffElementIO | dynamic round-trip (6 types); no attachment when `<dynamic>` absent | unit | `StaffElementIOTest.DynamicSerialization` (2) | adequate | keep |
| AnnotationIO | `writeAnnotation` — emits `<name>`/`<alignment>`/`<ypos>` | unit | `SongIOTest.testPre23ConvertsAnnotationToDynamic` (parses then migrates away) | inadequate | no text/alignment/yPosPx round-trip; write direct test |
| AnnotationIO | `writeAnnotation` — omit `<useryoffset>` when 0; emit when non-zero | unit | — | missing | both branches |
| AnnotationIO | `AnnotationReader.endElement11` — `<name>`/`<alignment>`/`<ypos>`/`<useryoffset>` → setters | unit | — | missing | parametrized round-trip per field |
| AnnotationIO | `AnnotationReader` — null-guard (endElement before startElement) | unit | — | missing | no NPE |
| AnnotationIO | `AnnotationReader.startElement11`/`characters` — fresh `Annotation("")`; accumulate only when `lastTag!=null` | unit | — | missing | covered by round-trip test |
| TempoIO | `writeTempo` — emits `<visibletempo>`/`<tempotype>`/`<tempodescription>` | unit | — | missing | direct parse-back of all three |
| TempoIO | `writeTempo` — omit `<dontshowtempo>` when shown; emit when not | unit | — | missing | both branches |
| TempoIO | `writeTempo`+`endElement11` round-trip — all fields preserved | unit | — | missing | per-note `<tempo>` round-trip via `StaffElementReader` |
| TempoIO | `endElement10` (v1.0) — `<tempochange>` wrapper → `Tempo`; `<position>` via `getPos10()` | unit | — | missing | v1.0 parse asserting pos10 + fields |
| TempoIO | `endElement10` — legacy no-underscore duration names (MINIMDOTTED/CROTCHETDOTTED/QUAVERDOTTED/SEMIBREVE) → `Duration` | unit | — | missing | parametrized for all 4 |
| TempoIO | `endElement10` — canonical duration name → `Duration.valueOf` path | unit | — | missing | write test |
| TempoIO | `endElement10` — `<dontshowtempo>` → `setShowTempo(false)` | unit | — | missing | write test |
| TempoIO | `endElement11` (v1.1) — `<tempo>` wrapper → `Tempo`; canonical names only | unit | — | missing | covered by round-trip test |
| TempoIO | `endElement11` — legacy name → `Duration.valueOf` fails (no legacy map in v1.1) | unit | — | missing | legacy name in v1.1 path throws IAE |
| TempoIO | `endElement11` — null-guard (endElement before startElement) → null | unit | — | missing | write test |
| TempoIO | `characters` — accumulate only when `lastTag!=null` | none | — | none | trivial delegation |
| TempoIO | `getPos10()` returns v1.0 parse position | unit | — | missing | covered by v1.0 parse test |

**2B notes (quality concerns):** **`AnnotationIO` and `TempoIO` have zero dedicated IO round-trip tests** — the largest gap here. The only `AnnotationIO` touch (`SongIOTest.testPre23ConvertsAnnotationToDynamic`) parses an annotation then migrates it away, never asserting persistence; `TempoIO` is exercised only via song-level fixture headers, never a per-note `TempoChangeAttachment` round-trip. The **v1.0 legacy decode paths** (`startElement10`/`endElement10`, NEWLINE/LINE/GRACESEMIQUAVER renames, MINIMDOTTED/CROTCHETDOTTED durations) are completely untested. The **inverted `stemDirectionAuto` write/read asymmetry** (tag present ⇒ `false`) has no coverage in either direction — high regression risk. The `extendTypeAttr(NONE)` IAE guard, the legacy `<beatchange>` happy paths, the glissando translate omit/emit, and the `getSyllabic()` `middle`/default branches are all unguarded. `testRoundTripPreservesDynamicType` excludes `SFORZANDO`/`SFORZATO` (enum has 8, comment says "6 UI types") — confirm intent.

### 2C. line & view serialization — `LineIO`, `ViewIO`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LineIO | `writeLine` — key-signature delta (count+type) only when line differs from song default | unit | — | missing | differing key → `<keys>`/`<keytype>` present; same → absent |
| LineIO | `writeLine` — omit `<notedistchange>` when ratio==1.0; write otherwise | unit | — | missing | ratio=1.5 present; 1.0 absent |
| LineIO | `writeLine` — always write `<lyricsypos>` | unit | — | missing | tag present |
| LineIO | `writeLine` — omit legacy Y-pos tags (tempoChangeypos/beatChangeypos/fsendingypos/trillypos) in new docs | unit | — | missing | none of the four appear |
| LineIO | `beamsToString` — `anchor,end;` pairs | unit | — | missing | known beam list → exact string |
| LineIO | `tiesToString` — `anchor,end;` pairs | unit | `TieToggleTest.testTiePersistsThroughSaveLoad` (round-trip) | adequate | round-trip preserves anchor/end; exact-format test optional |
| LineIO | `trillsToString` — `anchor,end;` w/o yPos when 0; incl. when non-zero | unit | — | missing | both branches |
| LineIO | `tupletsToString` — `anchor,end,grade;` w/o vertPos when 0; incl. when non-zero | unit | — | missing | both branches |
| LineIO | `hairpinsToString` — `anchor,end;` w/o shifts when all zero; incl. `x1,x2,y` when any non-zero | unit | — | missing | both branches |
| LineIO | `endingsToString` — `anchor,end;` per ending; **does not serialize `Ending.Type`** | unit | — | missing | exact-string test; see production observation (type-loss bug) |
| LineIO | `forEachSegment` — semicolon-delimited: empty→0 iters; single; multiple | unit | — | missing | all three cases (shared parser foundation) |
| LineIO | `LineReader` state machine — `<line>`→new Line + WHERE.LINE; `<notes>`→WHERE.NOTES; else set `lastTag` | unit | — | missing | exercise transitions via start/end events |
| LineIO | `endElement11` — `<keys>` → `setKeyAccidentalCount` | unit | `SongIOTest.LegacyMigrationWiring` (incidental, not asserted at line level) | inadequate | `<keys>5</keys>` → `getKeyAccidentalCount()==5` |
| LineIO | `endElement11` — `<keytype>` → `setKeyType` | unit | — | missing | `<keytype>FLATS</keytype>` → `KeyType.FLATS` |
| LineIO | `endElement11` — `<notedistchange>` → `changeElementSpacingRatio` | unit | — | missing | known float → `getElementSpacingRatio()` |
| LineIO | `endElement11` — `<lyricsypos>` → `setLyricsYPosSs` | unit | `GraceNoteLyricRoundTripTest` (parsed, never asserted) | inadequate | assert `getLyricsYPosSs()==5.0` |
| LineIO | `endElement11` — legacy Y-pos tags → correct setters (backward compat) | unit | — | missing | one test per legacy tag |
| LineIO | `endElement11` — silently ignores `<slurs>` | unit | — | missing | no exception, line unaffected |
| LineIO | beam round-trip (`parseBeamPairs`+`createBeamsFromPending`) | unit | `BeamToggleTest` (does not round-trip beams) | missing | round-trip → `findBeamAt(0)` correct anchor/end |
| LineIO | `createBeamsFromPending` skips out-of-range pairs (anchor<0, end≥count, anchor>end) | unit | — | missing | malformed pairs → zero beams, no exception |
| LineIO | tie round-trip (`parseTiePairs`+`createTiesFromPendingPairs`) | unit | `TieToggleTest.testTiePersistsThroughSaveLoad` | adequate | keep |
| LineIO | `createTiesFromPendingPairs` — no bounds guard (unlike beams) → AIOOBE on out-of-range | unit | — | missing | malformed tie pair → verify throws/skips; see production observation |
| LineIO | tuplet — grade defaults to 3 when absent (legacy `<triplets>`) | unit | — | missing | `<triplets>0,2;</triplets>` → grade=3 |
| LineIO | tuplet — explicit non-3 grade round-trip | unit | — | missing | `<tuplets>0,4,5;</tuplets>` → grade=5 |
| LineIO | tuplet — `verticalPositionSs` round-trip (non-zero) | unit | — | missing | `<tuplets>0,2,3,7;</tuplets>` → vertPos=7 |
| LineIO | `createTupletsFromPending` skips out-of-range pairs | unit | — | missing | analogous to beam bounds test |
| LineIO | `parseTupletData` swallows NFE for grade (→3) and vertPos (→0) | unit | — | missing | non-numeric grade/vertPos → defaults |
| LineIO | crescendo round-trip — all-zero shifts | unit | — | missing | `<crescendo>0,2;</crescendo>` → shifts 0 |
| LineIO | crescendo round-trip — explicit x1/x2/y shifts | unit | — | missing | `<crescendo>0,2,1.5,-0.5,0.25;</crescendo>` preserved |
| LineIO | diminuendo round-trip (same as crescendo) | unit | — | missing | analogous |
| LineIO | `parseHairpinPairs` swallows partial shift data (<3 parts → all 0) | unit | — | missing | `<crescendo>0,2,1.5;</crescendo>` → shifts 0 |
| LineIO | ending round-trip — always rebuilds as `Ending.Type.FIRST` regardless of actual type | unit | — | missing | write test; exposes type-loss bug (production observation) |
| LineIO | `parseEndingPairs` clears `pendingEndingPairs` before accumulating (unlike others) | unit | — | missing | call twice → only second batch survives |
| LineIO | trill round-trip — yPositionSs 0 and non-zero | unit | — | missing | `<trills>0,2;</trills>` and `<trills>0,2,5;</trills>` |
| LineIO | `accumulateLegacyTrillFlag` — coalesces contiguous indices into one run; new run for non-contiguous | unit | — | missing | 2,3,4 → `[2,4,0]`; 2,4 → two pairs |
| LineIO | `endElement11` returns completed `Line` on `</line>` (all create-methods invoked first) | unit | — | missing | full start/chars/end sequence → correct counts + range elements |
| LineIO | `LineReader` `line==null`/`noteReader==null` guard → `endElement11` returns null | unit | — | missing | endElement before any startElement → null |
| ViewIO | `writeView` — serializes all 6 font roles (name+size) | unit | — | missing | capture output, verify 6 name+size pairs |
| ViewIO | `writeView` — uses PS name (not display name) | unit | — | missing | PSName ≠ family name → PSName in output |
| ViewIO | `ViewReader` default ctor — all 6 roles from `Prefs` defaults | unit | `ViewIOTest.DocumentFontsLoad.testV10FallbackUsesDefaultsForAllRoles` | adequate | keep |
| ViewIO | `endElement11`+`getDocumentFonts()` — known tag updates name/size of role | unit | `ViewIOTest.DocumentFontsLoad.testPartialBlockOverridesOnlyPresentRoles` | adequate | keep |
| ViewIO | `ViewReader` ignores unknown tags (legacy `titlefontstyle`) silently | unit | `ViewIOTest.LegacyFontStyleElements.testDocumentWithFontStyleElementsLoadsWithoutError` | inadequate | `isNotNull()` trivially true; assert ignored tag didn't corrupt any font value |
| ViewIO | `getDocumentFonts()` — defaults for roles absent from partial block | unit | `ViewIOTest.DocumentFontsLoad.testPartialBlockOverridesOnlyPresentRoles` | adequate | keep |
| ViewIO | `getDocumentFonts()` zero roles == `defaultsFromPrefs()` | unit | `ViewIOTest.DocumentFontsLoad.testV10FallbackUsesDefaultsForAllRoles` | adequate | keep |
| ViewIO | `DocumentFonts.defaultsFromPrefs()` idempotent | unit | `ViewIOTest.DocumentFontsLoad.testNewDocumentInstallsPrefsDefaults` | inadequate | tautology (`x.equals(x)`); rewrite to compare two independent calls |
| ViewIO | `endElement11` legacy self-closing `<view/>` → all roles at defaults | unit | `ViewIOTest.FontXmlParsing.testLegacyDocumentWithoutFontXmlUsesPrefsDefaults` | adequate | keep |
| ViewIO | `writeView`+`ViewReader` full round-trip — all 6 roles preserved | unit | — | missing | primary correctness guarantee for the write path |
| ViewIO | `ViewReader.StringFont.sizeAsInt()` on non-integer size string | unit | — | missing | size="abc" → verify NFE propagates or is swallowed (document) |

**2C notes (quality concerns):** **`LineIO` — the largest IO class (~741 lines, 18-field serializer + ~450-line reader) — has no dedicated test file.** No test in `src/test/` references `LineIO`, its tag constants, or any internal parse method. The only coverage is incidental (`TieToggleTest` ties through `SongIO`; `BeamToggleTest` does not round-trip beams; `GraceNoteLyricRoundTripTest` parses `lyricsypos` without asserting it). Six of seven range-element serializers (beams, tuplets, endings, crescendos, diminuendos, trills) have zero coverage at any level, and the shared `forEachSegment` parser is untested. `ViewIO` is better served by a genuine `ViewIOTest`, but `writeView` is never called by any test (write path entirely untested), one test is an outright tautology, and the legacy-tolerance test asserts only `isNotNull()`.

### 2D. migration subsystem & legacy import — `FormatMigrator`, `MigrationPipeline`, `MigrationContext`, `SongMigration`, `StageId`, `LegacyLyricsImporter`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| FormatMigrator | `migrate` skips when `formatVersion >= 2` | unit | `MigrationPipelineTest.LegacyFormatStage.testDoesNotApplyAtThreshold` (gate via pipeline) | inadequate | the in-`migrate` version guard never asserted directly; call w/ version=2, confirm untouched |
| FormatMigrator | `migrate(lines,1)` iterates calling `migrateLineLevelOffsets` per line | unit | `MigrationPipelineTest.LegacyFormatStage.testEffectRunsOnEmptyLines` | inadequate | smoke (empty list); test a line w/ non-zero `tempoChangeYPosPx`, verify `userYOffsetSs` updated |
| FormatMigrator | `migrateLineLevelOffsets` — non-zero `tempoChangeYPosPx` → delta to each `TempoChangeAttachment.userYOffsetSs` | unit | — | missing | line+attachment offset → verify delta |
| FormatMigrator | `migrateLineLevelOffsets` — `beatChangeYPosPx`≠default → delta to `BeatChangeAttachment.userYOffsetSs` | unit | — | missing | non-default + zero-delta no-op |
| FormatMigrator | `migrateLineLevelOffsets` — `firstSecondEndingYPosPx`≠default → delta to `Ending.yPositionSs` | unit | — | missing | write test |
| FormatMigrator | `migrateLineLevelOffsets` — `trillYPosPx`≠default → delta to `Trill.yPositionSs` | unit | — | missing | write test |
| FormatMigrator | `migrateAnnotationPositions` — below-staff (`yPosPx>0`) → above-staff + userYOffset | unit | — | missing | positive yPosPx → yPosPx=ABOVE, userYOffset += (old−ABOVE) |
| FormatMigrator | `migrateAnnotationPositions` — above-staff (`yPosPx<=0`) → unchanged | unit | — | missing | no-op |
| FormatMigrator | `migrateElementAttachments` — empty body | none | — | none | no behavior |
| FormatMigrator | `migrateAnnotationDynamics` — text matches dynamic symbol → replaced w/ `DynamicAttachment`, annotation removed | unit | `FormatMigratorTest.MigrateAnnotationDynamics` (forte/pianissimo/removal) | adequate | keep |
| FormatMigrator | `migrateAnnotationDynamics` — non-matching text → kept, no attachment | unit | `FormatMigratorTest.testAnnotation*NotConverted` (2) | adequate | keep |
| FormatMigrator | `migrateAnnotationDynamics` — pre-existing `DynamicAttachment` → annotation removed, no duplicate | unit | `FormatMigratorTest.testAnnotationRemovedWhenDynamicAlreadyExists` | adequate | keep |
| FormatMigrator | `migratePixelsToStaffSpace` — `lyricsYPosSs` /= pps per line | unit | `MigrationPipelineTest.PixelsToSsStage.testEffectDividesAllScalarsByPps` (scalars only) | missing | line w/ `lyricsYPosSs` → assert division |
| FormatMigrator | `migratePixelsToStaffSpace` — `Tuplet.verticalPositionSs` /= pps (non-zero only) | unit | — | missing | non-zero /= pps; zero no-op |
| FormatMigrator | `migratePixelsToStaffSpace` — Hairpin `x1/x2/yShiftSs` /= pps | unit | — | missing | non-zero shifts |
| FormatMigrator | `migratePixelsToStaffSpace` — Glissando `x1/x2Translate` /= pps | unit | — | missing | write test |
| FormatMigrator | `migratePixelsToStaffSpace` — attachment `userYOffsetSs` /= pps (non-zero) | unit | — | missing | note w/ non-zero offset |
| FormatMigrator | `migratePixelsToStaffSpace` — `note.xOffsetPx` reset to 0 unconditionally | unit | — | missing | non-zero → reset to 0 |
| FormatMigrator | `migratePixelsToStaffSpace` — `Ending.yPositionSs`/`Trill.yPositionSs` /= pps (non-zero) | unit | — | missing | write test each |
| FormatMigrator | `migrateFinalTerminal` — empty list → no-op | unit | `MigrationPipelineTest.FinalTerminalStage` (empty ctx) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — FINAL_DOUBLE_BARLINE on non-last lines stripped; last line's terminal preserved | unit | `FormatMigratorTest.MigrateFinalTerminal.testFinalBarlineOnNonLastLine*` (2) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — REPEAT_RIGHT on non-last line untouched | unit | `FormatMigratorTest.testRepeatRightOnNonLastLineIsPreserved` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — last line ends in replaceable (SINGLE/DOUBLE/REPEAT_LEFT_RIGHT) → replaced w/ FINAL_DOUBLE_BARLINE | unit | `FormatMigratorTest.test*AtEndIsReplaced` (3) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — last line ends in REPEAT_RIGHT (valid terminal) → no-op | unit | `FormatMigratorTest.testRepeatRightAtEndIsPreservedAsTerminal` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — last line already FINAL_DOUBLE_BARLINE → no-op | unit | `FormatMigratorTest.testAlreadyEndsInFinalBarlineIsNoOp` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — non-replaceable non-terminal (REPEAT_LEFT, note) → FINAL_DOUBLE_BARLINE appended | unit | `FormatMigratorTest.testRepeatLeftAtEnd…`/`testNoteAtEnd…` (2) | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — empty last line → FINAL_DOUBLE_BARLINE appended | unit | `FormatMigratorTest.testEmptyLastLineGetsFinalBarlineAppended` | adequate | keep |
| FormatMigrator | `migrateFinalTerminal` — misplaced FINAL_DOUBLE_BARLINE not at terminal pos stripped before decision | unit | `FormatMigratorTest.testMisplacedFinalBarline*` (2) | adequate | keep |
| MigrationPipeline | `PRE_ASSEMBLY` registration order + stage count | unit | `MigrationPipelineTest.testStageOrderingPreservesScalarInvariant` (ordering only) | inadequate | assert all 6 stages registered in StageId order |
| MigrationPipeline | `POST_ASSEMBLY` registration (LEGACY_LYRICS then SYLLABIC_BACKFILL) | unit | — | missing | assert list == `[LEGACY_LYRICS, SYLLABIC_BACKFILL]` |
| MigrationPipeline | `versioned` helper — `ctx.isBefore(major,minor)` as `appliesTo` | unit | `MigrationPipelineTest` (implicit) | adequate | keep |
| MigrationPipeline | LEGACY_FORMAT gate — applies <2.0, skips ≥2.0 | unit | `MigrationPipelineTest.LegacyFormatStage` (2) | adequate | keep |
| MigrationPipeline | LEGACY_FORMAT effect — delegates to `FormatMigrator.migrate` w/ non-empty lines | unit | `MigrationPipelineTest.LegacyFormatStage.testEffectRunsOnEmptyLines` | inadequate | smoke only; non-empty behavior covered by missing `migrateLineLevelOffsets` tests |
| MigrationPipeline | ANNOTATION_DYNAMICS gate — applies <2.3, skips ≥2.3 | unit | `MigrationPipelineTest.AnnotationDynamicsStage` (2) | adequate | keep |
| MigrationPipeline | ANNOTATION_DYNAMICS effect — delegates to `migrateAnnotationDynamics` | unit | `MigrationPipelineTest.AnnotationDynamicsStage.testEffectRunsOnEmptyLines` | inadequate | smoke only; add wiring test through a line w/ annotation |
| MigrationPipeline | FINAL_TERMINAL gate — applies <2.4, skips ≥2.4 | unit | `MigrationPipelineTest.FinalTerminalStage` (2) | adequate | keep |
| MigrationPipeline | FINAL_TERMINAL effect — FINAL_DOUBLE_BARLINE appended when last line ends in note | unit | `MigrationPipelineTest.FinalTerminalStage.testEffectAppliesFinalBarline` | adequate | keep |
| MigrationPipeline | PIXELS_TO_SS gate — applies <2.1, skips ≥2.1 | unit | `MigrationPipelineTest.PixelsToSsStage` (2) | adequate | keep |
| MigrationPipeline | PIXELS_TO_SS effect — four song-level scalars /= pps | unit | `MigrationPipelineTest.PixelsToSsStage.testEffectDividesAllScalarsByPps` | adequate | keep |
| MigrationPipeline | PIXELS_TO_SS effect — per-line fields also /= pps | unit | — | missing | covered by missing `migratePixelsToStaffSpace` line-level tests; add integration via `runPreAssembly` w/ non-empty line |
| MigrationPipeline | LINE_WIDTH_FIX gate — major=2 AND minor<3 AND `lineWidthSs>=MIN`; else skip (3 negative branches) | unit | `MigrationPipelineTest.LineWidthFixStage` (5) | adequate | keep |
| MigrationPipeline | LINE_WIDTH_FIX effect — `lineWidthSs /= pps` | unit | `MigrationPipelineTest.LineWidthFixStage.testEffectDividesLineWidthByPps` | adequate | keep |
| MigrationPipeline | TOP_PADDING_FALLBACK gate — applies when `topPaddingSs==0` | unit | `MigrationPipelineTest.TopPaddingFallbackStage` (2) | adequate | keep |
| MigrationPipeline | TOP_PADDING_FALLBACK effect — `(2·titleSize + lineCount·attributionSize) − ssToRoundedPx(2.0)` | unit | `MigrationPipelineTest.TopPaddingFallbackStage.testEffectComputesCorrectFallbackValue` (attribution="") | inadequate | attribution="" ⇒ lineCount term never exercised; add non-empty attribution test |
| MigrationPipeline | LEGACY_LYRICS gate — `!lyrics.isBlank()` AND `isBefore(2, PER_NOTE_LYRIC_VERSION)` | unit | `MigrationPipelineTest.LegacyLyricsStage` (3) | adequate | keep |
| MigrationPipeline | LEGACY_LYRICS effect — delegates to `LegacyLyricsImporter.importLegacyLyrics` | unit | (no direct effect test; indirect via `SongIOTest.testLegacyLyricsBlobPopulatesPerNoteRecords`) | inadequate | add direct effect test asserting lyric records populated |
| MigrationPipeline | SYLLABIC_BACKFILL gate — always applies | unit | `MigrationPipelineTest.SyllabicBackfillStage.testAlwaysAppliesRegardlessOfVersion` | adequate | keep |
| MigrationPipeline | SYLLABIC_BACKFILL effect — `line.backfillSyllabic()` per line | unit | `MigrationPipelineTest.SyllabicBackfillStage.testEffectRunsOnSongWithNoLines` | inadequate | smoke mocks an empty line list ⇒ call never fires; test a line w/ stale markers → normalized |
| MigrationPipeline | `requireSong(ctx)` throws ISE when `ctx.song==null` | unit | — | missing | post-assembly stage `apply()` w/ null song → ISE |
| MigrationPipeline | `runPreAssembly` executes applicable stages in order | unit | `MigrationPipelineTest.testPreAssemblyScalarConversion`, `testStageOrderingPreservesScalarInvariant` | adequate | keep |
| MigrationPipeline | `runPostAssembly` executes applicable stages | unit | (indirect via `SongIOTest.LegacyMigrationWiring`) | adequate | keep |
| MigrationPipeline | stage ordering — PIXELS_TO_SS before LINE_WIDTH_FIX | unit | `MigrationPipelineTest.testStageOrderingPreservesScalarInvariant` | adequate | keep |
| MigrationPipeline | `PER_NOTE_LYRIC_VERSION`=6, `LEGACY_LINE_WIDTH_PX_MIN`=400.0 boundaries | unit | `MigrationPipelineTest` (used in gate setup) | adequate | keep |
| MigrationContext | `isBefore` — cross-major true; same-major minor< true; at-threshold false; major> false | unit | `MigrationPipelineTest` (multiple stage gate tests) | adequate | keep |
| MigrationContext | default field values (empty lines/scalars/strings, null song) | none | — | none | pure data holder |
| SongMigration | record accessors | none | — | none | pure record |
| StageId | enum constants | none | — | none | compile-time identifier |
| LegacyLyricsImporter | blank/empty blob → no lyrics | unit | `LegacyLyricsImporterTest.test*BlobEmitsNothing` (2) | adequate | keep |
| LegacyLyricsImporter | more blob lines than song lines → surplus dropped; fewer → surplus lines unset | unit | `LegacyLyricsImporterTest.testMultiLineDoesNotOverrunShorterLines` (+ implicit) | adequate | keep |
| LegacyLyricsImporter | more tokens than elements → surplus dropped | unit | `LegacyLyricsImporterTest.testTrailingWordsBeyondElementCountAreDropped` | adequate | keep |
| LegacyLyricsImporter | `deriveSyllabic` — 4 quadrants → SINGLE/BEGIN/END/MIDDLE | unit | `LegacyLyricsImporterTest.testDoReMi…`, `testEqualsProducesCompoundWord` | adequate | keep |
| LegacyLyricsImporter | single-hyphen `-` → BEGIN/MIDDLE/END chain | unit | `LegacyLyricsImporterTest.testDoReMiProducesThreeSyllables` | adequate | keep |
| LegacyLyricsImporter | double-hyphen `--` → compound | unit | `LegacyLyricsImporterTest.testDoubleHyphen*` (2) | adequate | keep |
| LegacyLyricsImporter | equals `=` → compound | unit | `LegacyLyricsImporterTest.testEqualsProducesCompoundWord` | adequate | keep |
| LegacyLyricsImporter | leading `--` on a line → inWord init, first word MIDDLE/END | unit | `LegacyLyricsImporterTest.testMidWordLineContinuationPrefix` | adequate | keep |
| LegacyLyricsImporter | trailing `_` run (extend=START) advances elementIdx by run length | unit | `LegacyLyricsImporterTest.testExtenderWith…`, `testFullCombinedExample` | adequate | keep |
| LegacyLyricsImporter | standalone `_` run → elementIdx += runLen, no Lyric | unit | `LegacyLyricsImporterTest.testExtenderWithSpaceSeparatedUnderscores…` | adequate | keep |
| LegacyLyricsImporter | `_` run abutting next word → one underscore absorbed (`runLen--`) | unit | `LegacyLyricsImporterTest.testExtenderWith…` ("_garden") | adequate | keep |
| LegacyLyricsImporter | trailing `_` run abutting next word → one continuation absorbed | unit | `LegacyLyricsImporterTest.testFullCombinedExample` | adequate | keep |
| LegacyLyricsImporter | stray `-`/`=` without preceding word → skipped (no lyric, no advance) | unit | — | missing | blob `- word` → first note gets `word` |
| LegacyLyricsImporter | stray `--` without preceding word mid-line → skipped (two chars consumed) | unit | — | missing | blob `-- word` → first note gets `word` |
| LegacyLyricsImporter | `isWordChar` boundary (space/tab/`_`/`-`/`=`/`\n` false; ASCII true) | unit | (implicit via all paths) | adequate | no isolated gap given full path coverage |
| LegacyLyricsImporter | full combined scenario (extend + compound + multi-syllable) | unit | `LegacyLyricsImporterTest.testFullCombinedExample` | adequate | keep |

**2D notes (quality concerns):** The single biggest blind spot is **`FormatMigrator.migratePixelsToStaffSpace`** — the one effect test asserts only the four song-level scalar divisions; the entire per-line body (`lyricsYPosSs`, tuplet vertPos, hairpin shifts, glissando translates, attachment `userYOffsetSs`, `xOffsetPx` reset, ending/trill yPos) is unasserted, so a divisor off-by-one or a missed field passes silently. Second: **`migrateLineLevelOffsets`** (the v1→v2 stage) runs only against empty line lists in the pipeline test, so every per-type offset migration is untested. Three pipeline "effect" tests are no-crash smoke (`testEffectRunsOnEmptyLines` for LEGACY_FORMAT / ANNOTATION_DYNAMICS / SYLLABIC_BACKFILL); the SYLLABIC_BACKFILL one is especially misleading — it mocks a Song returning an empty list, so `backfillSyllabic()` never fires and a deleted `forEach` would still pass. TOP_PADDING_FALLBACK's effect test uses `attribution=""`, leaving the line-count term at 0 and the multi-line branch uncovered. LEGACY_LYRICS has no direct effect test (relies on `SongIOTest`). `requireSong`'s ISE guard is untested. `LegacyLyricsImporter` is otherwise strongly covered — only the stray-marker paths (`-`/`--` without a preceding word) are missing.

### io — production observations (out of test-audit scope)

Filed as a single tracked GitHub issue ([#407](https://github.com/vasudeva-server/SongScribe/issues/407)) — these are real code observations, not test gaps, so the disposable matrix isn't their only home:

1. **⚠️ `LineIO` — `Ending.Type` data loss (correctness bug).** `endingsToString` serializes only anchor/end indices; `createEndingsFromPendingPairs` hard-codes `Ending.Type.FIRST`. Any **SECOND** ending (a "2." volta bracket) is silently reset to FIRST on save/load. Needs a real fix (encode/decode the type), not just a regression test.
2. **`LineIO` — missing bounds guards.** `createBeamsFromPending`/`createTupletsFromPending` validate index ranges; `createTiesFromPendingPairs`, `createCrescendosFromPending`, `createDiminuendosFromPending`, `createTrillsFromPendingPairs`, `createEndingsFromPendingPairs` do not — they throw `IndexOutOfBoundsException` on truncated/corrupt files. Make uniform or document as fail-loud.
3. **`LineIO` — `parseEndingPairs` asymmetry.** It uniquely `.clear()`s its pending list at entry; all other `parse*` methods accumulate. Document or unify.
4. **`XML` — static mutable `indent`.** `setIndent`/`printIndent` use an unsynchronized static field: a thread-safety hazard in production and a test-isolation hazard (tests that call `setIndent` leak state). Make it a parameter or document not-thread-safe.
5. **`FormatMigrator` — pixel-vs-staff-space unit coupling.** `applyTopPaddingFallback` and `migrateLineLevelOffsets` compute pixel-valued quantities/deltas and assign them to `*Ss` fields, correct only because `migratePixelsToStaffSpace` divides by pps afterward. Tests written in terms of the same formula can't catch a unit mismatch. Verify against `SongIOTest.testTopPaddingFallbackValueReachesSong` and add an explanatory comment about the two-step dependency.
6. **`StaffElementIO` — `lenght` parameter misspelling** in `characters` (cosmetic; compiles and works).
7. **`TempoIO` — `endElement11` has no legacy-duration-name lookup** (only `endElement10` does); a v1.1 file with a legacy name (e.g. `MINIMDOTTED`) throws `IllegalArgumentException` from `Duration.valueOf`. Likely an intentional v1.1 contract, but untested.

### io — summary

Audited all 15 production classes (excl. `package-info`). Dominant patterns to drive remediation:

1. **Serialization *write* paths are the biggest blind spot.** Existing coverage is round-trip-via-`SongIO`, which verifies value preservation but never **conditional emission** (tag omitted when zero/null/empty) or exact serialized format. `XML.escapeXML`, `writeSong`'s conditional fields, `ViewIO.writeView`, and most of `LineIO`'s field/range-element writers are unasserted.
2. **`LineIO` (the largest IO class) has no dedicated test file** — six of seven range-element serializers and the shared `forEachSegment` parser are entirely untested.
3. **Legacy/v1.0 decode paths are dark:** `StaffElementIO`/`TempoIO` `*10` methods, legacy type/duration renames, `AnnotationIO`/`TempoIO` round-trips, and `BeatChange.fromLegacyName` happy paths (echoing the `dom` finding).
4. **Migration is best-covered, but its *per-line* conversions aren't:** `migratePixelsToStaffSpace`/`migrateLineLevelOffsets` bodies run only against empty line lists in tests; several pipeline "effect" tests are no-crash smoke (one mocks away the very call it claims to test).
5. **"Weak-but-green" tests:** tautologies (`ViewIO` `x.equals(x)`), `isNotNull()`-only assertions (`SongLoaderTest`, legacy-tolerance), and indirect round-trips standing in for direct behavioral assertions.
6. **Real code defects surfaced** (see production observations) — most notably the `Ending.Type` round-trip data loss.

