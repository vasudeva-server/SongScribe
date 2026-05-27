### 3B. horizontal spacing & columns — `ElementColumn`, `ElementColumnBuilder`, `HorizontalSpacingCalculator`, `InsertionSpacingCalculator`, `LineJustificationCalculator`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| ElementColumn | ctor stores all fields (graceNotes defensively copied) | unit | fixture-only in stacking tests, fields never asserted | inadequate | `ElementColumnTest`: assert field storage + defensive copy | ✅ |
| ElementColumn | `getWidthSs` = `abs(leftExtent)+rightExtent` | unit | — | missing | test formula | ✅ |
| ElementColumn | `getLeftEdgeXSs` = `xSs+leftExtent`; `getRightEdgeXSs` = `xSs+rightExtent` | unit | — | missing | test (incl. negative leftExtent / accidental) | ✅ |
| ElementColumn | `hasSyllable()` false when null or empty | unit | — | missing | null + "" cases | ✅ |
| ElementColumn | `minGapToNextSyllableSs` round-trip; default `LyricRenderMetrics.MIN_SYLLABLE_GAP_SS` | unit | — | missing | default + setter | ✅ |
| ElementColumn | `isRest`/`isBarline`/`isBeamed`/`hasGraceNotes`/`hasGlissando` delegation | unit | — | missing | one delegation test each | ✅ |
| ElementColumnBuilder | `calculateRightExtentSs` unbeamed quaver > notehead-only | unit | `ElementColumnBuilderTest.testUnbeamedQuaverExtentExceedsNoteheadOnly` | adequate | keep | — |
| ElementColumnBuilder | beamed quaver = notehead-only (flag suppressed) | unit | `testBeamedQuaverExtentEqualsNoteheadOnly` | adequate | keep | — |
| ElementColumnBuilder | non-flagged types unchanged by beamed/upper | unit | `testNonFlaggedTypesUnchanged` | adequate | keep | — |
| ElementColumnBuilder | stem-up vs stem-down differ (unbeamed quaver) | unit | `testStemUpVsStemDownProduceDifferentExtents` | inadequate | `isNotEqualTo` survives constant swap; pin exact values | ✅ |
| ElementColumnBuilder | grace quaver < regular quaver | unit | `testGraceQuaverExtentSmallerThanRegularQuaver` | inadequate | `isLessThan` only; pin exact values | ✅ |
| ElementColumnBuilder | dotted quaver = max(dots-extent, flag-extent) | unit | `testDottedQuaverExtentIsMaxOfDotsAndFlag` | inadequate | `>=` survives extra dot width; assert exact | ✅ |
| ElementColumnBuilder | two-dot extent includes two gap+dot pairs | unit | — | missing | double-dotted test | ✅ |
| ElementColumnBuilder | rest/barline → `type.getElementWidthSs()` unchanged | unit | — | missing | REST + BARLINE test | ✅ |
| ElementColumnBuilder | `calculateLeftExtentSs` 0 without accidental; negative `-(accW+ACCIDENTAL_GAP_SS)` with | unit | — | missing | both cases | ✅ |
| ElementColumnBuilder | `buildColumn` minGap = hyphen width (BEGIN/MIDDLE) vs space width (END/SINGLE) | unit | — | missing | hyphenated + non-hyphenated lyric | ✅ |
| ElementColumnBuilder | `buildColumns` empty line → empty list | unit | — | missing | empty-line edge | ✅ |
| ElementColumnBuilder | `calculateStemTop/BottomSs` for up/down/stemless | unit | — | missing | widen to package-private; stem geometry | ✅ |
| HorizontalSpacingCalculator | `calculateFirstElementXSs(n)` = clef + n·keyAcc + firstNoteOffset | unit | `HorizontalSpacingCalculatorTest.testFirstNoteXMatchesCalculateFirstElementXSs` | inadequate | **self-referential**: compares `calculatePositions` to same formula; pin concrete value | ✅ |
| HorizontalSpacingCalculator | `calculateHeaderRightEdgeSs(n)` = clef + n·keyAcc | unit | — | missing | 0/3/7 accidentals | ✅ |
| HorizontalSpacingCalculator | `calculateNextColumnXSs` min spacing = prevRight+MIN_GAP+abs(currLeft) | unit | — | missing | two plain columns, exact value | ✅ |
| HorizontalSpacingCalculator | default gap floor dominates without lyrics | unit | — | missing | verify DEFAULT_GAP floor | ✅ |
| HorizontalSpacingCalculator | lyric spacing dominates with wide syllables | unit | — | missing | wide-syllable columns | ✅ |
| HorizontalSpacingCalculator | accidental push when next column accidental would overlap | unit | — | missing | construct triggering case | ✅ |
| HorizontalSpacingCalculator | grace→host tight gap | unit | — | missing | grace+host columns | ✅ |
| HorizontalSpacingCalculator | glissando spacing enforced (`ensureGlissandoSpacing`) | unit | — | missing | prev-has-glissando | ✅ |
| HorizontalSpacingCalculator | `calculatePositions` empty list returns (no exception) | unit | — | missing | guard test | ✅ |
| HorizontalSpacingCalculator | beam-group tight spacing + even lyric expansion (`identifyBeamGroupRanges`/`handleBeamGroup`) | unit | — | missing | critical multi-branch; with/without lyrics | ✅ |
| HorizontalSpacingCalculator | single-column beam group → normal spacing | unit | — | missing | edge case | ✅ |
| InsertionSpacingCalculator | `calculateInsertion` out-of-bounds → IAE | unit | — | missing | negative + > count | ✅ |
| InsertionSpacingCalculator | `calculateAppendPositionSs` empty line → `calculateFirstElementXSs` | unit | `FitsWithinLine.testAppendToEmptyLine` (asserts `fitsWithinLine(500)`) | inadequate | assert exact X = `calculateFirstElementXSs(keyAccidentalCount)` | ✅ |
| InsertionSpacingCalculator | `fitsWithinLine` exact margin+DEFAULT_GAP boundary → false | unit | `testInsertIntoNearlyFullLine` (uses width-1) | inadequate | test the exact `DEFAULT_COLUMN_GAP_SS` boundary | ✅ |
| InsertionSpacingCalculator | `hasRoomForGraceNote` empty/full/plenty | unit | `HasRoomForGraceNote.*` (3) | adequate | keep | — |
| InsertionSpacingCalculator | `hasRoomForHostNoteAfterGrace` room/no-room | unit | `HasRoomForHostNoteAfterGrace.*` (2) | adequate | keep | — |
| InsertionSpacingCalculator | `calculateInsertion` at index 0 correct X + shift | unit | — | missing | verify X and downstream shift | ✅ |
| InsertionSpacingCalculator | mid-insertion shift = max(0, required), never negative | unit | — | missing | non-negative shift | ⬜ |
| InsertionSpacingCalculator | `calculateNextElementXSs` delegates via xOffset | unit | — | missing | equals `calculateNextColumnXSs` | ⬜ |
| InsertionSpacingCalculator | `InsertionResult.newLineWidthSs` = max(inserted right edge, shifted last) | unit | round-trip tests check only `fitsWithinLine` | inadequate | assert `newLineWidthSs()` directly | ⬜ |
| LineJustificationCalculator | empty list → success | unit | — | missing | guard | ⬜ |
| LineJustificationCalculator | line fits → success, no compression | unit | — | missing | assert `!wasCompressionApplied()` | ⬜ |
| LineJustificationCalculator | compression ratio = (target-extentOffset)/centerSpan | unit | — | missing | two columns over margin; verify ratio + positions | ⬜ |
| LineJustificationCalculator | `applyCompression` first column fixed, rest scale by ratio | unit | — | missing | exact compressed positions | ⬜ |
| LineJustificationCalculator | `validateCompression` rejects gap < `COMPRESSED_MIN_COLUMN_GAP_SS` | unit | — | missing | tight columns → failure | ⬜ |
| LineJustificationCalculator | rejects syllable gap < `COMPRESSED_MIN_SYLLABLE_GAP_SS` | unit | — | missing | wide-syllable columns | ⬜ |
| LineJustificationCalculator | `success`/`successWithCompression`/`failure` factories + errorMessage null contract | unit | — | missing | guard on getErrorMessage | ⬜ |
| LineJustificationCalculator | line-too-full → user-facing error at insert | e2e | `ElementInsertionTest.FullLine.testInsertIntoFullLineShowsError` | adequate | keep | — |

**3B notes (quality concerns):** Four critical gaps. (1) **`HorizontalSpacingCalculatorTest` is entirely self-referential** — its single test asserts `calculatePositions` equals `calculateFirstElementXSs` (same formula), so zeroing `FIRST_NOTE_OFFSET_SS` would stay green; the whole spacing class is "covered" by a tautology. (2) **`LineJustificationCalculator` has zero tests** despite non-trivial float math (compression ratio, gap-after-compression, two min-gap validators); `LayoutEngineTest` never constructs an over-margin line so the compression path is dark everywhere. (3) **`ElementColumnBuilderTest` uses relational assertions** (`isNotEqualTo`/`isLessThan`/`>=`) where the values are statically computable from SMuFL/Engraving constants — magnitude-perturbing mutations survive. (4) `InsertionSpacingCalculator` append-to-empty and `fitsWithinLine` boundary tests are weak, and `InsertionResult.newLineWidthSs` is never directly asserted. Out-of-scope production observation: `HorizontalSpacingCalculator.needsAccidentalPush` ignores its `prevColumn`/`currXSs` parameters and returns true whenever the current element has any accidental — the real clearance check lives in the caller; the method signature implies a pre-check it doesn't perform (unused params, code smell — review, don't act blindly).

