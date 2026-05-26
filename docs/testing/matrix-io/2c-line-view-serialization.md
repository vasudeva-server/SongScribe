### 2C. line & view serialization — `LineIO`, `ViewIO`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| LineIO | `writeLine` — key-signature delta (count+type) only when line differs from song default | unit | — | missing | differing key → `<keys>`/`<keytype>` present; same → absent | ✅ |
| LineIO | `writeLine` — omit `<notedistchange>` when ratio==1.0; write otherwise | unit | — | missing | ratio=1.5 present; 1.0 absent | ✅ |
| LineIO | `writeLine` — always write `<lyricsypos>` | unit | — | missing | tag present | ✅ |
| LineIO | `writeLine` — omit legacy Y-pos tags (tempoChangeypos/beatChangeypos/fsendingypos/trillypos) in new docs | unit | — | missing | none of the four appear | ✅ |
| LineIO | `beamsToString` — `anchor,end;` pairs | unit | — | missing | known beam list → exact string | ✅ |
| LineIO | `tiesToString` — `anchor,end;` pairs | unit | `TieToggleTest.testTiePersistsThroughSaveLoad` (round-trip) | adequate | round-trip preserves anchor/end; exact-format test optional | — |
| LineIO | `trillsToString` — `anchor,end;` w/o yPos when 0; incl. when non-zero | unit | — | missing | both branches | ✅ |
| LineIO | `tupletsToString` — `anchor,end,grade;` w/o vertPos when 0; incl. when non-zero | unit | — | missing | both branches | ✅ |
| LineIO | `hairpinsToString` — `anchor,end;` w/o shifts when all zero; incl. `x1,x2,y` when any non-zero | unit | — | missing | both branches | ✅ |
| LineIO | `endingsToString` — `anchor,end;` per ending; **does not serialize `Ending.Type`** | unit | — | missing | exact-string test; see production observation (type-loss bug) | ✅ |
| LineIO | `forEachSegment` — semicolon-delimited: empty→0 iters; single; multiple | unit | — | missing | all three cases (shared parser foundation) | ✅ |
| LineIO | `LineReader` state machine — `<line>`→new Line + WHERE.LINE; `<notes>`→WHERE.NOTES; else set `lastTag` | unit | — | missing | exercise transitions via start/end events | ✅ |
| LineIO | `endElement11` — `<keys>` → `setKeyAccidentalCount` | unit | `SongIOTest.LegacyMigrationWiring` (incidental, not asserted at line level) | inadequate | `<keys>5</keys>` → `getKeyAccidentalCount()==5` | ✅ |
| LineIO | `endElement11` — `<keytype>` → `setKeyType` | unit | — | missing | `<keytype>FLATS</keytype>` → `KeyType.FLATS` | ✅ |
| LineIO | `endElement11` — `<notedistchange>` → `changeElementSpacingRatio` | unit | — | missing | known float → `getElementSpacingRatio()` | ✅ |
| LineIO | `endElement11` — `<lyricsypos>` → `setLyricsYPosSs` | unit | `GraceNoteLyricRoundTripTest` (parsed, never asserted) | inadequate | assert `getLyricsYPosSs()==5.0` | ✅ |
| LineIO | `endElement11` — legacy Y-pos tags → correct setters (backward compat) | unit | — | missing | one test per legacy tag | ✅ |
| LineIO | `endElement11` — silently ignores `<slurs>` | unit | — | missing | no exception, line unaffected | ✅ |
| LineIO | beam round-trip (`parseBeamPairs`+`createBeamsFromPending`) | unit | `BeamToggleTest` (does not round-trip beams) | missing | round-trip → `findBeamAt(0)` correct anchor/end | ✅ |
| LineIO | `createBeamsFromPending` skips out-of-range pairs (anchor<0, end≥count, anchor>end) | unit | — | missing | malformed pairs → zero beams, no exception | ✅ |
| LineIO | tie round-trip (`parseTiePairs`+`createTiesFromPendingPairs`) | unit | `TieToggleTest.testTiePersistsThroughSaveLoad` | adequate | keep | — |
| LineIO | `createTiesFromPendingPairs` — no bounds guard (unlike beams) → AIOOBE on out-of-range | unit | — | missing | malformed tie pair → verify throws/skips; see production observation | ✅ |
| LineIO | tuplet — grade defaults to 3 when absent (legacy `<triplets>`) | unit | — | missing | `<triplets>0,2;</triplets>` → grade=3 | ✅ |
| LineIO | tuplet — explicit non-3 grade round-trip | unit | — | missing | `<tuplets>0,4,5;</tuplets>` → grade=5 | ✅ |
| LineIO | tuplet — `verticalPositionSs` round-trip (non-zero) | unit | — | missing | `<tuplets>0,2,3,7;</tuplets>` → vertPos=7 | ✅ |
| LineIO | `createTupletsFromPending` skips out-of-range pairs | unit | — | missing | analogous to beam bounds test | ✅ |
| LineIO | `parseTupletData` swallows NFE for grade (→3) and vertPos (→0) | unit | — | missing | non-numeric grade/vertPos → defaults | ✅ |
| LineIO | crescendo round-trip — all-zero shifts | unit | — | missing | `<crescendo>0,2;</crescendo>` → shifts 0 | ✅ |
| LineIO | crescendo round-trip — explicit x1/x2/y shifts | unit | — | missing | `<crescendo>0,2,1.5,-0.5,0.25;</crescendo>` preserved | ✅ |
| LineIO | diminuendo round-trip (same as crescendo) | unit | — | missing | analogous | ✅ |
| LineIO | `parseHairpinPairs` swallows partial shift data (<3 parts → all 0) | unit | — | missing | `<crescendo>0,2,1.5;</crescendo>` → shifts 0 | ✅ |
| LineIO | ending round-trip — always rebuilds as `Ending.Type.FIRST` regardless of actual type | unit | — | missing | write test; exposes type-loss bug (production observation) | ✅ |
| LineIO | `parseEndingPairs` clears `pendingEndingPairs` before accumulating (unlike others) | unit | — | missing | call twice → only second batch survives | ✅ |
| LineIO | trill round-trip — yPositionSs 0 and non-zero | unit | — | missing | `<trills>0,2;</trills>` and `<trills>0,2,5;</trills>` | ✅ |
| LineIO | `accumulateLegacyTrillFlag` — coalesces contiguous indices into one run; new run for non-contiguous | unit | — | missing | 2,3,4 → `[2,4,0]`; 2,4 → two pairs | ✅ |
| LineIO | `endElement11` returns completed `Line` on `</line>` (all create-methods invoked first) | unit | — | missing | full start/chars/end sequence → correct counts + range elements | ✅ |
| LineIO | `LineReader` `line==null`/`noteReader==null` guard → `endElement11` returns null | unit | — | missing | endElement before any startElement → null | ✅ |
| ViewIO | `writeView` — serializes all 6 font roles (name+size) | unit | — | missing | capture output, verify 6 name+size pairs | ✅ |
| ViewIO | `writeView` — uses PS name (not display name) | unit | — | missing | PSName ≠ family name → PSName in output | ✅ |
| ViewIO | `ViewReader` default ctor — all 6 roles from `Prefs` defaults | unit | `ViewIOTest.DocumentFontsLoad.testV10FallbackUsesDefaultsForAllRoles` | adequate | keep | — |
| ViewIO | `endElement11`+`getDocumentFonts()` — known tag updates name/size of role | unit | `ViewIOTest.DocumentFontsLoad.testPartialBlockOverridesOnlyPresentRoles` | adequate | keep | — |
| ViewIO | `ViewReader` ignores unknown tags (legacy `titlefontstyle`) silently | unit | `ViewIOTest.LegacyFontStyleElements.testDocumentWithFontStyleElementsLoadsWithoutError` | inadequate | `isNotNull()` trivially true; assert ignored tag didn't corrupt any font value | ✅ |
| ViewIO | `getDocumentFonts()` — defaults for roles absent from partial block | unit | `ViewIOTest.DocumentFontsLoad.testPartialBlockOverridesOnlyPresentRoles` | adequate | keep | — |
| ViewIO | `getDocumentFonts()` zero roles == `defaultsFromPrefs()` | unit | `ViewIOTest.DocumentFontsLoad.testV10FallbackUsesDefaultsForAllRoles` | adequate | keep | — |
| ViewIO | `DocumentFonts.defaultsFromPrefs()` idempotent | unit | `ViewIOTest.DocumentFontsLoad.testNewDocumentInstallsPrefsDefaults` | inadequate | tautology (`x.equals(x)`); rewrite to compare two independent calls | ✅ |
| ViewIO | `endElement11` legacy self-closing `<view/>` → all roles at defaults | unit | `ViewIOTest.FontXmlParsing.testLegacyDocumentWithoutFontXmlUsesPrefsDefaults` | adequate | keep | — |
| ViewIO | `writeView`+`ViewReader` full round-trip — all 6 roles preserved | unit | — | missing | primary correctness guarantee for the write path | ✅ |
| ViewIO | `ViewReader.StringFont.sizeAsInt()` on non-integer size string | unit | — | missing | size="abc" → verify NFE propagates or is swallowed (document) | ✅ |

**2C notes (quality concerns):** **`LineIO` — the largest IO class (~741 lines, 18-field serializer + ~450-line reader) — has no dedicated test file.** No test in `src/test/` references `LineIO`, its tag constants, or any internal parse method. The only coverage is incidental (`TieToggleTest` ties through `SongIO`; `BeamToggleTest` does not round-trip beams; `GraceNoteLyricRoundTripTest` parses `lyricsypos` without asserting it). Six of seven range-element serializers (beams, tuplets, endings, crescendos, diminuendos, trills) have zero coverage at any level, and the shared `forEachSegment` parser is untested. `ViewIO` is better served by a genuine `ViewIOTest`, but `writeView` is never called by any test (write path entirely untested), one test is an outright tautology, and the legacy-tolerance test asserts only `isNotNull()`.

