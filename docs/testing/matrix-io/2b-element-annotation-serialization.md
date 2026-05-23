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

