## 3. `layout` (audited 2026-05-21)

Audited all 37 production classes (excl. 2 `package-info`) via six parallel production-first sub-audits: **orchestration & accumulation**; **horizontal spacing & columns**; **geometry primitives & metrics**; **lyric layout**; **ranges/endings/collision**; **stacking subsystem**. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. One verdict reclassified from a sub-audit's `wrong-level` (`LineEndingSupport.findEndingReplacementEffect`): the vocabulary reserves `wrong-level` for unit↔e2e mismatches; a unit behavior covered only indirectly is `inadequate`.

### 3A. orchestration & accumulation — `LayoutEngine`, `LayoutAccumulator`, `LayoutResult`, `LayoutLayer`, `SectionLayout`, `PageModel`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LayoutEngine | `layout()` returns non-null with clef at `CLEF_X_POSITION_SS` | unit | `LayoutEngineTest.testLayoutStoresClefAtStandardPosition` | adequate | keep |
| LayoutEngine | `layout()` places key signature immediately after clef (type + accidental count) | unit | `LayoutEngineTest.testLayoutStoresKeySignatureAfterClef` | adequate | keep |
| LayoutEngine | `layout(line, true)` pins FINAL_DOUBLE_BARLINE flush-right | unit | `LayoutEngineTest.testFinalBarlineFlushRightOnLastLine` | adequate | keep |
| LayoutEngine | `layout(line, true)` pins REPEAT_RIGHT terminal flush-right | unit | `LayoutEngineTest.testRightRepeatTerminalFlushRightOnLastLine` | adequate | keep |
| LayoutEngine | `layout(line, false)` does NOT place barline flush-right | unit | `LayoutEngineTest.testFinalBarlineNotFlushRightOnNonLastLine` | inadequate | negative `isNotCloseTo(flushRight)` survives any wrong value (incl. X=0); assert exact expected X from horizontal spacing |
| LayoutEngine | empty line returns non-null result with `MIN_LINE_HEIGHT_SS` | unit | `LineHeightTest.testEmptyLineZeroReturnsMinimumHeight`, `testEmptyNonLastLineReturnsMinimumHeight` | adequate | keep |
| LayoutEngine | empty-line result still contains clef + key signature | unit | — | missing | assert `getClef()`/`getKeySignature()` non-null on empty line |
| LayoutEngine | un-justifiable line → `layout()` returns null, `getLastError()` non-null | unit | — | missing | over-stuffed line → null result + descriptive error |
| LayoutEngine | 3-arg `layout(line,false,true)` threads `hasLeadingLyricContinuation` to lyric layout | unit | — | missing | extending melisma → leading lyric connector at x=0 |
| LayoutEngine | unbeamed note below middle line (sp>0) → stem up | unit | — | missing | crotchet at sp=2 → stem-up geometry |
| LayoutEngine | unbeamed note above middle line (sp≤0) → stem down | unit | — | missing | crotchet at sp=-2 → stem-down |
| LayoutEngine | unbeamed grace note always stem up | unit | — | missing | grace at sp=-4 still stem-up, length `GRACE_NOTE_STEM_LENGTH_SS` |
| LayoutEngine | manual stem override not auto-corrected | unit | — | missing | `upper=false`,`stemDirectionAuto=false` at sp=4 → stays down |
| LayoutEngine | beamed group auto-direction (above→down, below→up) | unit | — | missing | two tests on `BeamLayout.stemsUp()` |
| LayoutEngine | beamed group manual override: first explicit direction wins for whole group | unit | — | missing | first note `upper=true` → `stemsUp=true` |
| LayoutEngine | beam slope hyperbolic dampening clamps below `BEAM_SLOPE_MAX` | unit | — | missing | large pitch diff → `abs(slope) < BEAM_SLOPE_MAX` |
| LayoutEngine | beam slope-reduction loop: all stems ≥ `MIN_STEM_SS` | unit | — | missing | large contour → every stem ≥ MIN_STEM_SS |
| LayoutEngine | flat-beam snapping: slope<0.05 snaps `startYSs` to 0.5 grid | unit | — | missing | equal-position quavers → startYSs multiple of 0.5 |
| LayoutEngine | beam thickening: non-zero slope → `thickeningSs` in `(0, BEAM_DEPTH_SS*0.088]` | unit | — | missing | sloped group → bounded thickening |
| LayoutEngine | stub direction: isolated semiquaver gets stub-right | unit | — | missing | quaver+semiquaver beam → `stubRight=true` |
| LayoutEngine | tie geometry: `startXSs = noteX + TIE_NOTEHEAD_HALF_WIDTH_SS` | unit | — | missing | adjacent-note tie offset |
| LayoutEngine | tie shoulder height clamped to `[TIE_MIN, TIE_MAX]` | unit | — | missing | narrow→min, wide→max |
| LayoutEngine | tie collision: interior note deflects arc outward | unit | — | missing | 3-note tie over intersecting note → larger outer control Y |
| LayoutEngine | tie direction: stem-up note ties below (+1) | unit | — | missing | stem-up note → arc bulges down |
| LayoutEngine | `createHeaderElements` null `keyType` → `KeyType.NONE` | unit | `LayoutEngineTest.testLayoutStoresKeySignatureAfterClef` (non-null only) | missing | null keyType → keySig type NONE |
| LayoutEngine | `beamCount` → 1/2/3 for QUAVER/SEMIQUAVER/DEMI_SEMIQUAVER | unit | — | missing | widen to package-private; assert each |
| LayoutResult | `Builder.setClef`/`setKeySignature` round-trip; default null | unit | `LayoutResultTest.testBuilderClefRoundTrip`, `…KeySignatureRoundTrip`, `…DefaultsToNullHeaderElements` | adequate | keep |
| LayoutResult | `getLyricAnchor` box-anchored centerX+baselineY; column fallback; Y==`verseYSsInLine(1)`; throws ISE w/ neither | unit | `LayoutResultTest.testGetLyricAnchor*` (4) | adequate | keep |
| LayoutResult | `hitTestLyric` inside-box hit / outside-box miss | unit | `LayoutResultTest.testHitTestLyric*` (2) | adequate | keep |
| LayoutResult | `findElementAtXSs` returns index within head bounds / -1 in gap | unit | — | missing | two known-X columns; hit + gap |
| LayoutResult | `findInsertionIndex` over-head / before-first(0) / after-last(`effectiveElementCount`) / in-gap(slot) | unit | — | missing | write 4 tests |
| LayoutResult | `calculateInsertionXSs` empty / over-head snap / terminal right-align / after-last spacing / between-midpoint | unit | — | missing | write 5 tests |
| LayoutResult | `getBelowStaffReservationSs` = `lineHeight - aboveStaff - STAFF_HEIGHT_SS` | unit | — | missing | known-values test |
| LayoutResult | `lyricAreaBaseYSs` shifts with `aboveStaffSs`/`belowContentSs` | unit | `LayoutResultTest.testHitTestLyricHitsInsideBounds` (indirect) | inadequate | focused test pinning the formula |
| LayoutResult | `findAttachmentBounds` correct owner/type; null unknown owner | unit | — (stacking tests use `findAttachmentDecorationLayout`) | missing | two same-type attachments on different owners |
| LayoutResult | `findRangeElementBounds` by anchor+end+type | unit | — | missing | write test |
| LayoutResult | `findAttachment` matching owner/type else null | unit | — | missing | write test |
| LayoutResult | `findRangeElementDecorationLayout` by anchor+type | unit | covered transitively (`FermataTrillStackingTest` etc. use attachment variant) | inadequate | focused range-element test |
| LayoutResult | `contains` true iff `elementBounds` has element | unit | — | missing | write test |
| LayoutResult | `getDecorationLayoutsByType` filters by class | unit | — | missing | two types → each filtered list correct |
| LayoutResult | `getElementXSs` 0 / `getElementPosition` null for unknown element | unit | — | missing | write tests |
| LayoutAccumulator | `add`/`intersects` (Rectangle2D + Area), overlap true / non-overlap false | unit | — | missing | write tests |
| LayoutAccumulator | `clear` → `isEmpty` true and `intersects` false; fresh `isEmpty` true | unit | — | missing | write tests |
| LayoutAccumulator | `getArea()` returns defensive copy | unit | — | missing | mutate return; accumulator unchanged |
| LayoutAccumulator | union of two rects intersects a spanning rect | unit | — | missing | write test |
| SectionLayout | `hasContent()` true non-empty / false empty list / false empty first line | unit | — | missing | 3 tests |
| SectionLayout | `getText()` first line / "" when empty | unit | — | missing | write tests |
| SectionLayout | `getHeight()` from content bounds | unit | — | missing | known-bounds test |
| SectionLayout | `empty()` factory: zero size, no lines, null font | unit | — | missing | assert each property |
| SectionLayout | 2-arg string ctor wraps text in single-element list | unit | — | missing | round-trip via `lines()` |
| SectionLayout | `lines()` immutable (defensive copy) | unit | — | missing | mutate source; `lines()` unchanged |
| PageModel | `Size.LETTER`/`A4` dimensions | unit | `PageModelTest.SizeEnum.*` (2) | adequate | keep |
| PageModel | `getSize()` default LETTER / "a4" / case-insensitive / unknown→LETTER | unit | `PageModelTest.PageSizeFromPrefs.*` (4) | adequate | keep |
| PageModel | `getPageWidthPx`/`getPageHeightPx` for LETTER+A4 | unit | `PageModelTest.PageDimensionsPx` (4) | adequate | keep |
| PageModel | top/bottom margins = 0.5"; horizontal centers; 0 when line ≥ page | unit | `PageModelTest.Margins.*` (5) | adequate | keep |
| PageModel | `getContentAreaWidthPx` = `pageWidth - 2*defaultMargin` | unit | `PageModelTest.ContentArea.contentAreaWidthAccountsForDefaultMargins` | inadequate | self-referential (expected uses same formula); pin concrete px for LETTER |
| PageModel | `getMaxLineWidthInches`=7.77 / `getMinLineWidthInches`=5.0 | unit | `PageModelTest.LineWidthConstants.*` (2) | adequate | keep |
| PageModel | `getDefaultLineWidthSs` = `pxToSs(contentAreaWidthPx)` | unit | `PageModelTest.DefaultLineWidth.defaultLineWidthSsMatchesContentArea` | inadequate | self-referential oracle; pin explicit LETTER constant |
| PageModel | size changes reactively on pref change; A4 width < LETTER | unit | `PageModelTest.PageSizeChange.*` (2) | adequate | keep |
| LayoutLayer | enum constants (ELEMENT, TIE, …, LYRICS) | none | — | none | pure enum, no derivation |
| LayoutEngine/VSC | high/low note increases line height | unit | `LineHeightTest.testHighNoteAboveStaffIncreasesLineHeight`, `testLowNoteBelowStaffIncreasesLineHeight` | inadequate | `>=MIN_LINE_HEIGHT_SS` passes even if extension broken; assert exact height for the staff position |

**3A notes (quality concerns):** **The highest-risk gap is the total absence of tests for `LayoutEngine`'s three geometry engines** — beam slope/direction/stub logic, unbeamed stem-direction assignment, and tie Bézier geometry. This is the densest math in the package (hyperbolic dampening, iterative slope reduction, 20-iteration convergence, Bézier collision avoidance) with zero coverage; mutations to `< MIN_STEM_SS` or the `stemsUp ? pos<anchor : pos>anchor` branch would survive. `LayoutAccumulator` and `SectionLayout` have zero coverage despite real branching (`hasContent()`, `intersects()`, `clear()`) — trivially unit-testable, no mocking. Two `PageModelTest` tests are self-referential oracles (`contentAreaWidthAccountsForDefaultMargins`, `defaultLineWidthSsMatchesContentArea`). `LineHeightTest`'s high/low-note tests use `>=MIN_LINE_HEIGHT_SS` (the universal floor) — green even if the height extension returns exactly the minimum. `LayoutResult`'s hit-testing/insertion/lookup family (`findElementAtXSs`, `findInsertionIndex`, `calculateInsertionXSs`, `findAttachmentBounds`, `findRangeElementBounds`, `findAttachment`, `contains`, `getDecorationLayoutsByType`) is pure map-lookup logic, all untested, all straightforwardly unit-testable via `Builder`. `LayoutLayer` correctly classified `none`.

### 3B. horizontal spacing & columns — `ElementColumn`, `ElementColumnBuilder`, `HorizontalSpacingCalculator`, `InsertionSpacingCalculator`, `LineJustificationCalculator`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementColumn | ctor stores all fields (graceNotes defensively copied) | unit | fixture-only in stacking tests, fields never asserted | inadequate | `ElementColumnTest`: assert field storage + defensive copy |
| ElementColumn | `getWidthSs` = `abs(leftExtent)+rightExtent` | unit | — | missing | test formula |
| ElementColumn | `getLeftEdgeXSs` = `xSs+leftExtent`; `getRightEdgeXSs` = `xSs+rightExtent` | unit | — | missing | test (incl. negative leftExtent / accidental) |
| ElementColumn | `hasSyllable()` false when null or empty | unit | — | missing | null + "" cases |
| ElementColumn | `minGapToNextSyllableSs` round-trip; default `LyricRenderMetrics.MIN_SYLLABLE_GAP_SS` | unit | — | missing | default + setter |
| ElementColumn | `isRest`/`isBarline`/`isBeamed`/`hasGraceNotes`/`hasGlissando` delegation | unit | — | missing | one delegation test each |
| ElementColumnBuilder | `calculateRightExtentSs` unbeamed quaver > notehead-only | unit | `ElementColumnBuilderTest.testUnbeamedQuaverExtentExceedsNoteheadOnly` | adequate | keep |
| ElementColumnBuilder | beamed quaver = notehead-only (flag suppressed) | unit | `testBeamedQuaverExtentEqualsNoteheadOnly` | adequate | keep |
| ElementColumnBuilder | non-flagged types unchanged by beamed/upper | unit | `testNonFlaggedTypesUnchanged` | adequate | keep |
| ElementColumnBuilder | stem-up vs stem-down differ (unbeamed quaver) | unit | `testStemUpVsStemDownProduceDifferentExtents` | inadequate | `isNotEqualTo` survives constant swap; pin exact values |
| ElementColumnBuilder | grace quaver < regular quaver | unit | `testGraceQuaverExtentSmallerThanRegularQuaver` | inadequate | `isLessThan` only; pin exact values |
| ElementColumnBuilder | dotted quaver = max(dots-extent, flag-extent) | unit | `testDottedQuaverExtentIsMaxOfDotsAndFlag` | inadequate | `>=` survives extra dot width; assert exact |
| ElementColumnBuilder | two-dot extent includes two gap+dot pairs | unit | — | missing | double-dotted test |
| ElementColumnBuilder | rest/barline → `type.getElementWidthSs()` unchanged | unit | — | missing | REST + BARLINE test |
| ElementColumnBuilder | `calculateLeftExtentSs` 0 without accidental; negative `-(accW+ACCIDENTAL_GAP_SS)` with | unit | — | missing | both cases |
| ElementColumnBuilder | `buildColumn` minGap = hyphen width (BEGIN/MIDDLE) vs space width (END/SINGLE) | unit | — | missing | hyphenated + non-hyphenated lyric |
| ElementColumnBuilder | `buildColumns` empty line → empty list | unit | — | missing | empty-line edge |
| ElementColumnBuilder | `calculateStemTop/BottomSs` for up/down/stemless | unit | — | missing | widen to package-private; stem geometry |
| HorizontalSpacingCalculator | `calculateFirstElementXSs(n)` = clef + n·keyAcc + firstNoteOffset | unit | `HorizontalSpacingCalculatorTest.testFirstNoteXMatchesCalculateFirstElementXSs` | inadequate | **self-referential**: compares `calculatePositions` to same formula; pin concrete value |
| HorizontalSpacingCalculator | `calculateHeaderRightEdgeSs(n)` = clef + n·keyAcc | unit | — | missing | 0/3/7 accidentals |
| HorizontalSpacingCalculator | `calculateNextColumnXSs` min spacing = prevRight+MIN_GAP+abs(currLeft) | unit | — | missing | two plain columns, exact value |
| HorizontalSpacingCalculator | default gap floor dominates without lyrics | unit | — | missing | verify DEFAULT_GAP floor |
| HorizontalSpacingCalculator | lyric spacing dominates with wide syllables | unit | — | missing | wide-syllable columns |
| HorizontalSpacingCalculator | accidental push when next column accidental would overlap | unit | — | missing | construct triggering case |
| HorizontalSpacingCalculator | grace→host tight gap | unit | — | missing | grace+host columns |
| HorizontalSpacingCalculator | glissando spacing enforced (`ensureGlissandoSpacing`) | unit | — | missing | prev-has-glissando |
| HorizontalSpacingCalculator | `calculatePositions` empty list returns (no exception) | unit | — | missing | guard test |
| HorizontalSpacingCalculator | beam-group tight spacing + even lyric expansion (`identifyBeamGroupRanges`/`handleBeamGroup`) | unit | — | missing | critical multi-branch; with/without lyrics |
| HorizontalSpacingCalculator | single-column beam group → normal spacing | unit | — | missing | edge case |
| InsertionSpacingCalculator | `calculateInsertion` out-of-bounds → IAE | unit | — | missing | negative + > count |
| InsertionSpacingCalculator | `calculateAppendPositionSs` empty line → `calculateFirstElementXSs` | unit | `FitsWithinLine.testAppendToEmptyLine` (asserts `fitsWithinLine(500)`) | inadequate | assert exact X = `calculateFirstElementXSs(keyAccidentalCount)` |
| InsertionSpacingCalculator | `fitsWithinLine` exact margin+DEFAULT_GAP boundary → false | unit | `testInsertIntoNearlyFullLine` (uses width-1) | inadequate | test the exact `DEFAULT_COLUMN_GAP_SS` boundary |
| InsertionSpacingCalculator | `hasRoomForGraceNote` empty/full/plenty | unit | `HasRoomForGraceNote.*` (3) | adequate | keep |
| InsertionSpacingCalculator | `hasRoomForHostNoteAfterGrace` room/no-room | unit | `HasRoomForHostNoteAfterGrace.*` (2) | adequate | keep |
| InsertionSpacingCalculator | `calculateInsertion` at index 0 correct X + shift | unit | — | missing | verify X and downstream shift |
| InsertionSpacingCalculator | mid-insertion shift = max(0, required), never negative | unit | — | missing | non-negative shift |
| InsertionSpacingCalculator | `calculateNextElementXSs` delegates via xOffset | unit | — | missing | equals `calculateNextColumnXSs` |
| InsertionSpacingCalculator | `InsertionResult.newLineWidthSs` = max(inserted right edge, shifted last) | unit | round-trip tests check only `fitsWithinLine` | inadequate | assert `newLineWidthSs()` directly |
| LineJustificationCalculator | empty list → success | unit | — | missing | guard |
| LineJustificationCalculator | line fits → success, no compression | unit | — | missing | assert `!wasCompressionApplied()` |
| LineJustificationCalculator | compression ratio = (target-extentOffset)/centerSpan | unit | — | missing | two columns over margin; verify ratio + positions |
| LineJustificationCalculator | `applyCompression` first column fixed, rest scale by ratio | unit | — | missing | exact compressed positions |
| LineJustificationCalculator | `validateCompression` rejects gap < `COMPRESSED_MIN_COLUMN_GAP_SS` | unit | — | missing | tight columns → failure |
| LineJustificationCalculator | rejects syllable gap < `COMPRESSED_MIN_SYLLABLE_GAP_SS` | unit | — | missing | wide-syllable columns |
| LineJustificationCalculator | `success`/`successWithCompression`/`failure` factories + errorMessage null contract | unit | — | missing | guard on getErrorMessage |
| LineJustificationCalculator | line-too-full → user-facing error at insert | e2e | `ElementInsertionTest.FullLine.testInsertIntoFullLineShowsError` | adequate | keep |

**3B notes (quality concerns):** Four critical gaps. (1) **`HorizontalSpacingCalculatorTest` is entirely self-referential** — its single test asserts `calculatePositions` equals `calculateFirstElementXSs` (same formula), so zeroing `FIRST_NOTE_OFFSET_SS` would stay green; the whole spacing class is "covered" by a tautology. (2) **`LineJustificationCalculator` has zero tests** despite non-trivial float math (compression ratio, gap-after-compression, two min-gap validators); `LayoutEngineTest` never constructs an over-margin line so the compression path is dark everywhere. (3) **`ElementColumnBuilderTest` uses relational assertions** (`isNotEqualTo`/`isLessThan`/`>=`) where the values are statically computable from SMuFL/Engraving constants — magnitude-perturbing mutations survive. (4) `InsertionSpacingCalculator` append-to-empty and `fitsWithinLine` boundary tests are weak, and `InsertionResult.newLineWidthSs` is never directly asserted. Out-of-scope production observation: `HorizontalSpacingCalculator.needsAccidentalPush` ignores its `prevColumn`/`currXSs` parameters and returns true whenever the current element has any accidental — the real clearance check lives in the caller; the method signature implies a pre-check it doesn't perform (unused params, code smell — review, don't act blindly).

### 3C. geometry primitives & metrics — `ElementBoundsSs`, `InsetsSs`, `Size`, `Margin`, `MarginReference`, `LineThickness`, `NoteGeometry`, `StaffExtents`, `VerticalOrder`, `SongLayoutMetrics`, `SongLayoutMetricsBuilder`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementBoundsSs | `uniform`/`withMargin`/`withMarginOnly`/`contentOnly` factories | unit | — | missing | assert exact layer rects per factory (incl. no-top-margin in `withMargin`) |
| ElementBoundsSs | `collapsedMarginWith(below)` = `max(thisBottom, belowTop)` CSS collapse | unit | — | missing | this-wins / below-wins / equal |
| ElementBoundsSs | `containsForHitTest` delegates to padding bounds | unit | — | missing | inside-padding / inside-content-outside-padding / outside |
| ElementBoundsSs | `intersectsMargin`/`intersectsPadding` layer-specific | unit | — | missing | overlap + non-overlap per layer |
| ElementBoundsSs | `translate(dx,dy)` shifts all four layers (incl. nullable visual) | unit | — | missing | verify exact shift |
| ElementBoundsSs | `getVisualBounds()` explicit-visual vs `marginBoundsSs` fallback | unit | — | missing | both branches |
| ElementBoundsSs | coordinate accessors (`getTop/Bottom/Left/Right/MarginTop/MarginBottom Ss`) | unit | — | missing | fold into factory tests |
| ElementBoundsSs | `formatCssSpacing` 5-branch shorthand (all-zero/all-same/2/3/4-value) | unit | — | missing | each branch; **see production observation re px/ss suffixes** |
| ElementBoundsSs | `getPaddingCss`/`getMarginCss` pass correct differentials | unit | — | missing | uniform + asymmetric |
| InsetsSs | `toInsetsPx()` rounds all four via `ScaleContext.ssToRoundedPx` | unit | — | missing | known scale → exact Insets fields |
| Size | pure record (`ZERO`, width/height) | none | — | none | trivial data holder |
| Margin | `uniform(m)` all sides equal; `NONE` all zero | unit | — | missing | one-liner asserting invariant |
| MarginReference | pure documentation enum | none | — | none | no branching |
| LineThickness | each field = `LILYPOND_BASE_THICKNESS_SS × multiplier` | unit | only `barlineSeparationSs()` exercised indirectly (`ElementTypeTest`) | inadequate | assert `stemSs`/`ledgerLineSs`/`hairpinSs`/`voltaBracketSs`/`tupletBracketSs` + multipliers |
| LineThickness | `barlineSeparationSs()` = `staffLineSs × BARLINE_SEPARATION_MULTIPLIER` | unit | `ElementTypeTest.testDoubleBarlineWidth` | adequate | keep |
| LineThickness | `repeatRightThinBarlineCenterXSs`/`repeatRightAfterThickXSs` arithmetic | unit | — | missing | known-constant tests |
| NoteGeometry | `initializeAccidentalWidths()` idempotent | unit | `NoteRendererTest`/`NoteAreaBuilderTest` (called twice) | adequate | keep |
| NoteGeometry | `getAccidentalWidthSs(note)` dispatch small/base/parens; 0 for none | unit | — | missing | each accidental kind; exact SMuFL width |
| NoteGeometry | `getAccidentalBoundsSs(note)` null/grace-null/table | unit | `NoteRendererTest` (directional only) | inadequate | weak `isNegative`/`isPositive`; pin exact for ≥1; add DOUBLE_SHARP/NATURAL_* variants |
| NoteGeometry | `getLedgerLineOverhangSs(note)` 0 in-staff / `LEDGER_LINE_EXTENSION_SS` out | unit | — | missing | |sp|≤5 vs >5 with `drawStaveLongitude` |
| NoteGeometry | `getNoteheadXOffsetSs(type,upper)` `-stemWidth/2` stem-down else 0 | unit | — | missing | stemmed up/down/non-stemmed |
| NoteGeometry | `getNoteheadRightEdgeSs(note)` SMuFL bbox + fallback | unit | — | missing | known bbox + null fallback |
| NoteGeometry | `walkAccidentalGlyphs` visitor advances/parens/kerning | unit | — | missing | verify emitted positions for known sequence |
| StaffExtents | `spToSs(sp)` = sp×0.5 | unit | fixture input only (`VerticalStackingCalculatorTest`) | missing | direct: spToSs(0/2/-4) + round-trip |
| StaffExtents | `ssToSp(ss)` = round(ss/0.5) | unit | — | missing | exact + rounding boundaries |
| StaffExtents | `xToStep` clamp (private, via ySet/yGet) | unit | `StaffExtentsTest` (clamping via ySet/yGet) | adequate | keep |
| StaffExtents | `ySet`/`yGet` reserve/query above/below | unit | `StaffExtentsTest` (defaults, overlaps, clamp, isolation) | adequate | keep |
| StaffExtents | `copyTopFrom` copies top, leaves bot | unit | `StaffExtentsTest.CopyTopFrom` | adequate | keep |
| StaffExtents | derived constants (`MIN_ABOVE/BELOW_STAFF_SS`, `MIN/MAX_STAFF_POSITION_SP`) | unit | used, never asserted | missing | pin computed values (catch `STAFF_LINES_ABOVE/BELOW` change) |
| VerticalOrder | `isAboveStaff`/`isBelowStaff` relative to `NOTE_STEM.order` | unit | — | missing | each constant; NOTE_STEM neither |
| VerticalOrder | `compareByOrder` | unit | — | missing | <0 / >0 / 0 cases |
| SongLayoutMetrics | `staffTopYSsInLine`/`staffBottomYSsInLine` | unit | `SongLayoutMetricsTest.testStaffYHelpers` | adequate | keep |
| SongLayoutMetrics | `verseYSsInLine(verse)` formula | unit | `SongLayoutMetricsTest.testVerseBaselineYHelper` | inadequate | **self-referential** (expected from same accessors); pin concrete literals |
| SongLayoutMetricsBuilder | `build()` max above/below/belowContent; floor at `MIN_*` | unit | `SongLayoutMetricsTest` (empty, max above/below/belowContent) | adequate | keep |
| SongLayoutMetricsBuilder | lyricsBand collapse when `verseCount==0` | unit | `SongLayoutMetricsTest.testVerseCountCollapsesWhenNoLyrics` | adequate | keep |
| SongLayoutMetricsBuilder | `staffToLyricsGapSs = LYRICS_ROW_MARGIN_SS + lyricAscentSs` | unit | `…testLyricsBandPopulatedWhenVersesPresent` (passes ascent=0) | inadequate | `lyricAscentSs` addend never tested (always 0); add non-zero ascent |
| SongLayoutMetricsBuilder | `totalLineHeightSs` includes lyricsBand | unit | `…testLyricsBandPopulatedWhenVersesPresent` | adequate | keep |
| SongLayoutMetricsBuilder | `MIN_LINE_HEIGHT_SS` constant | unit | `LineHeightTest` (`>=` weak) | inadequate | pin exact height for note at `MIN_STAFF_POSITION_SP` |

**3C notes (quality concerns):** **`ElementBoundsSs`** — the central CSS-like box-model type used throughout layout — has zero unit tests: all factories, `collapsedMarginWith`, `containsForHitTest`, both intersections, `translate`, `getVisualBounds` fallback, and the 5-branch `formatCssSpacing` are untested (and `formatCssSpacing` has a confirmed px/ss suffix bug — see production observations). Second: **`NoteGeometry` accidental geometry** — `getAccidentalBoundsSs` is covered only directionally (`isNegative`/`isPositive`) so a double-width regression stays green, and `getAccidentalWidthSs` (the width that positions notes horizontally) has no test at all; `getLedgerLineOverhangSs`/`getNoteheadXOffsetSs`/`getNoteheadRightEdgeSs` untested. `SongLayoutMetricsTest.testVerseBaselineYHelper` is a self-referential oracle. `SongLayoutMetricsBuilder` is never tested with non-zero `lyricAscentSs`, so the `+ lyricAscentSs` addend is dead from a test view. **`StaffExtents.spToSs`/`ssToSp`** — the project-canonical Sp↔Ss converters — have no direct assertion (echoes the `dom` `ScaleContext` finding). `InsetsSs.toInsetsPx` (the only non-trivial Ss→Px conversion among the records) is untested. `VerticalOrder`'s branching predicates (`isAboveStaff`/`isBelowStaff`/`compareByOrder`) drive stacking yet are untested. `LineThickness` non-barline fields and the two repeat-barline helpers are untested. `LineHeightTest` high/low-note tests use `>=MIN_LINE_HEIGHT_SS` (weak).

### 3D. lyric layout — `LyricBoxLayout`, `LyricConnectorLayout`, `LyricLayoutBuilder`, `LyricRenderMetrics`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| LyricBoxLayout | pure data record | none | — | none | no computation |
| LyricConnectorLayout | `Kind` enum + `NO_SOURCE_ELEMENT_INDEX` sentinel; discriminants drive rendering | none | `LyricConnectorRendererTest` (renderer level) | none | rendering assertion belongs to renderer |
| LyricRenderMetrics | `lyricBoxWidthSs("")` → 0.0 | unit | — | missing | empty guard |
| LyricRenderMetrics | `lyricBoxWidthSs(text)` advance for non-empty | unit | `LyricRenderMetricsTest.testLyricBoxWidthSsMatchesLayoutBoxWidth` | inadequate | **self-referential**: builder stores `lyricBoxWidthSs(text)` then asserts equality; use independent oracle |
| LyricRenderMetrics | `lyricBoxMetricsSs("")` → `LyricBoxMetrics.EMPTY` | unit | — | missing | empty guard |
| LyricRenderMetrics | `lyricBoxMetricsSs(text)` advance/bearing/extent triple | unit | — | missing | fixed font or structural relations |
| LyricRenderMetrics | `lyricBoxHeightSs()` positive ascent+descent | unit | — | missing | write test |
| LyricRenderMetrics | `preferredHyphenCellWidthSs()` = `HYPHEN_WIDENING_FACTOR × hyphenWidthSs` | unit | — | missing | non-zero hyphen width |
| LyricRenderMetrics | `COMPRESSED_MIN_SYLLABLE_GAP_SS < MIN_SYLLABLE_GAP_SS` invariant | unit | — | missing | ordering assertion |
| LyricLayoutBuilder | empty line / no-lyrics → empty result | unit | `testEmptyLineProducesEmptyResult`, `testLineWithoutLyricsProducesEmptyResult` | adequate | keep |
| LyricLayoutBuilder | BEGIN/MIDDLE→opens HYPHEN, END closes (do-re-mi) | unit | `testDoReMiProducesThreeBoxesAndTwoHyphens` | inadequate | only count asserted; add HYPHEN start/end coords + sourceElementIndex |
| LyricLayoutBuilder | SINGLE no-extend → box, no connector | unit | implicit via multi-element tests | adequate | keep |
| LyricLayoutBuilder | `computeLyricBoxLeftXSs` normal note: center − halfWidth | unit | — | missing | assert box X centering |
| LyricLayoutBuilder | grace note: first glyph centred on grace notehead; host no box | unit | `testGraceLyricFirstGlyphCentredOnGraceNoteheadAndHostHasNoBox` | adequate | keep |
| LyricLayoutBuilder | `firstGraphemeClusterEndIndex` multi-codepoint cluster | unit | — | missing | combining mark + surrogate pair |
| LyricLayoutBuilder | note no-lyric + active extender passes through | unit | `testExtenderSpansContinuationNotes` | adequate | keep |
| LyricLayoutBuilder | REST no-lyric → extender closed at rest left | unit | `testRestWithoutLyricBreaksExtender` | adequate | keep |
| LyricLayoutBuilder | REST + START → extender continues | unit | `testRestWithExtendingLyricContinuesExtender` | adequate | keep |
| LyricLayoutBuilder | REST + CONTINUE → extender continues (distinct sub-case) | unit | — | missing | CONTINUE on rest |
| LyricLayoutBuilder | REST + STOP → closes `STOP_MELISMA_OVERSHOOT_SS` past rest right | unit | — | missing | assert ending = rightEdge + overshoot |
| LyricLayoutBuilder | note + STOP → closes with overshoot, no box | unit | `testStopCarrierEndsExtenderAtNoteRightEdge` | adequate | keep (assertion uses constant; see stale-comment observation) |
| LyricLayoutBuilder | note + CONTINUE passes through | unit | `testContinueCarrierPassesThrough` | adequate | keep |
| LyricLayoutBuilder | BEGIN+START → hyphen, extender suppressed | unit | `testNonFinalSyllableWithMelismaEmitsHyphenOnly` | adequate | keep |
| LyricLayoutBuilder | extender opens SINGLE+START, closes at next text note | unit | `testExtenderSpansContinuationNotes` | adequate | keep (but `startXSs` unverified) |
| LyricLayoutBuilder | NONE-extend text note with active extender closes at box left | unit | `testContinueCarrierPassesThrough` | adequate | keep |
| LyricLayoutBuilder | dangling extender extends through CONTINUE/STOP not bare notes | unit | `testDanglingExtenderEndsAtStartNoteWhenNoContinueFollows`, `testDanglingExtenderExtendsThroughContinueMarkers` | adequate | keep |
| LyricLayoutBuilder | trailing continuation flag + leading stub from x=0 | unit | `testTrailingContinuationAndLeadingStub` | adequate | keep |
| LyricLayoutBuilder | `emitDanglingHyphen` no eligible follower → LOG.error, no connector | unit | — | missing | open BEGIN at line end → no DANGLING_HYPHEN |
| LyricLayoutBuilder | DANGLING_HYPHEN emitted to next eligible element left edge | unit | — (renderer test uses hand-built record) | missing | builder coords for DANGLING_HYPHEN |
| LyricLayoutBuilder | `sourceElementIndex` on HYPHEN/EXTENDER/DANGLING_* | unit | never asserted | missing | ≥1 assertion per kind |
| LyricLayoutBuilder | multi-verse separate boxes/connectors by `verseIndex`; `verseCount` = max verse | unit | `testMultiVerseProducesSeparateBoxesPerVerse` | adequate | keep |
| LyricLayoutBuilder | verse-1 (`getSyllableWidthSs`) vs verse-≥2 (`lyricBoxWidthSs`) equal width for same text | unit | — | missing | catch divergence between cached and on-the-fly paths |
| LyricLayoutBuilder | compound-word boundary (BEGIN+compound) opens HYPHEN | unit | `testCompoundWordBoundaryProducesHyphen` | adequate | keep |
| LyricLayoutBuilder | lyric boxes appear after insertion | e2e | — | missing (low priority) | optional rendering smoke; geometry fully unit-coverable |

**3D notes (quality concerns):** Highest-risk defect: **`LyricRenderMetricsTest.testLyricBoxWidthSsMatchesLayoutBoxWidth` is a self-referential oracle** — verse-2 builder calls `lyricBoxWidthSs(text)` to populate `box.widthSs()`, then asserts `box.widthSs() ≈ lyricBoxWidthSs(text)`, i.e. `f(x) ≈ f(x)`; needs an independent oracle. Second: **HYPHEN/EXTENDER connector geometry** — `testDoReMi…` and `testExtenderSpansContinuationNotes` verify counts but never `startXSs`/`endXSs`, so an incorrectly anchored hyphen passes. Two untested branches carry real risk: **REST + STOP** (overshoot past rest right edge — distinct path from note-STOP) and **REST + CONTINUE** (distinct value in the compound condition from the tested START). **`emitDanglingHyphen`** has no builder-level test (happy path nor LOG.error path); the only DANGLING_HYPHEN test uses a hand-crafted record. `firstGraphemeClusterEndIndex` is tested only with ASCII so surrogate-pair/combining-mark regressions to naive `charAt(0)` would survive. Out-of-scope production observation: `LyricLayoutBuilder.java:68` comment says "Extends 0.25 ss past the column right edge" but `STOP_MELISMA_OVERSHOOT_SS = 0.5` — stale comment (the test comment repeats it; assertions correctly use the constant).

### 3E. ranges, endings, attachments, collision — `AttachmentLayout`, `CollisionDetector`, `Ending`, `LineEndingSupport`, `RangeLayout`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| AttachmentLayout | `getVerticalOrder()` switch maps Type→VerticalOrder | unit | — | missing | **dead code (zero refs)** — resolve by deletion, not test (see observations) |
| AttachmentLayout | `isAboveStaff`/`containsPoint` delegations | none | — | none | trivial delegation |
| AttachmentLayout | `getDataAs` null-safe cast | unit | — | missing | dead code; delete |
| CollisionDetector | `calculateNoteExtent` accumulates min/max Y over notes/attachments/articulations/ranges | unit | — | missing | **dead code (zero refs)** — resolve by deletion |
| CollisionDetector | `COLLISION_PADDING_SS` constant | none | — | none | numeric constant |
| Ending | `getLabel()` "1."/"2." | unit | — | missing | two-case test |
| Ending | `getContentHeightSs()` = `VOLTA_TICK_HEIGHT_SS` | none | `StructuralTierStackingTest` pins value indirectly | none | constant return |
| Ending | `getSpanWidthSs()` = `max(NOTE_HEAD_WIDTH_SS, endX-anchorX+NOTE_HEAD_WIDTH_SS)` | unit | — | missing | zero-span + positive span |
| Ending | `findRepeatSplitElement()` scans for REPEAT_RIGHT/REPEAT_LEFT_RIGHT | unit | indirect via invalidation tests | missing | direct: no-split / each split type / invalid indices |
| Ending | `computeBracketRanges()` start-adjust, split detection, two-bracket geometry | unit | — | missing | **high-risk**: no-split, split→two brackets, start-adjust from barline, end-extend, closing-stroke per end type |
| Ending | `computeCollisionRegions()` bar/tick(s)/label decomposition | unit | — | missing | region count (3 vs 4 by `hasClosingStroke`), x-offsets, label inset |
| Ending | `labelBoundsSs(int)` cached glyph bounds | none | — | none | static lookup |
| Ending | `isInvalidatedByDeletion()` split + all-content cases | unit | `EndingInvalidationTest.IsInvalidatedByDeletion` (6) | adequate | keep |
| Ending | `isInvalidatedByReplacement()` / `checkReplacement()` all outcomes | unit | `EndingInvalidationTest.IsInvalidatedByReplacement` (15), `CheckReplacement` | adequate | keep |
| Ending | `isInvalidatedByInsertion()` guards + interior/split logic | unit | `EndingInvalidationTest.IsInvalidatedByInsertion` (5) | inadequate | missing split-boundary exemption (`insertedIndex==splitIndex`→false) and `splitEl==null` interior branch |
| Ending | stacking above staff/hairpins | unit | `StructuralTierStackingTest.EndingStacking` (4) | adequate | keep (directional `isLessThan(0)` correct for the claim) |
| Ending | `setYPositionSs`/`getYPositionSs` applied in stacking | unit | `ManualOffsetStackingTest.EndingOffsets.testEndingYPositionApplied` | adequate | keep |
| Ending | base `isInvalidatedBy` anchor/end deleted | unit | `RangeElementInvalidationTest` (parametrized incl. Ending) | adequate | keep |
| Ending | Line-mutation wiring removes invalidated Ending | unit | `LineMutationTest.EndingInvalidationConditions` (10+) | adequate | keep |
| Ending | confirmation UI wiring (abort/proceed/dual change) | unit (integration) | `EndingConfirmsTest` (9, mocked dialogs) | adequate | keep |
| LineEndingSupport | `findEndings()` extracts Ending range elements | unit | indirect only | missing | 0/1/2 endings, verify content |
| LineEndingSupport | `findEndingAt(List,int)` span inclusion [start,end] | unit | — | missing | before/at-start/inside/at-end/after/empty |
| LineEndingSupport | `findEndingAt(Line,int)` overload | none | — | none | trivial delegation |
| LineEndingSupport | `isInsideAnyEnding` null-safe | unit | — | missing | positive + negative |
| LineEndingSupport | `isStartOfAnyEnding` anchor equality | unit | — | missing | start / inside-not-start / empty |
| LineEndingSupport | `isEndOfAnyEnding` end equality | unit | — | missing | end / inside-not-end / empty |
| LineEndingSupport | `findEndingReplacementEffect()` first non-None effect | unit | `EndingConfirmsTest` via `SelectionCoordinator.applyActionToSelection` | inadequate | indirect only (reclassified from wrong-level); add direct 0/1/2-affected test |
| RangeLayout | `getVerticalOrder()` ENDINGS / RANGE_ABOVE / RANGE_BELOW | unit | — | missing | **dead code (zero refs)** — resolve by deletion |
| RangeLayout | `getElementCount()` = end-start+1 | unit | — | missing | dead code |
| RangeLayout | `containsElement(int)` range-inclusive | unit | — | missing | dead code |
| RangeLayout | `containsPoint`/`getDataAs` | none/unit | — | none/missing | dead code |

**3E notes (quality concerns):** The most significant in-scope gap is **`Ending.computeBracketRanges()`** — the most complex method here (start-leftward-adjust, no-split single bracket, split→two brackets, per-end-type closing-stroke) — with zero direct coverage; bugs produce wrong visual geometry, not crashes. Its companion `computeCollisionRegions()` (3 vs 4 sub-regions) is also untested. `isInvalidatedByInsertion` has two survivable-mutant spots: the split-boundary exemption and the `splitEl==null` interior branch. **`LineEndingSupport`** is used by 8 production subsystems (MIDI, ABC export, IO, rendering, selection, vertical adjustment) but has no unit tests; its `findEndingAt` boundary comparators (`>=`/`<=`) are exactly where off-by-one hides. Out-of-scope production observation (**verified**): `AttachmentLayout`, `CollisionDetector`, `RangeLayout` have **zero references anywhere in `src/main` or `src/test`** (confirmed by grep + Serena) — dead scaffolding superseded by `LayoutResult.DecorationLayout`; resolve by deletion in remediation rather than writing the "missing" tests. Redundant: `StructuralTierStackingTest.EndingStacking.testEndingRangeElementProducesDecorationLayout` duplicates `testEndingPositionedAboveStaff`.

### 3F. stacking subsystem — `NoteAttachedStacker`, `StackingContext`, `StackingUtils`, `StructuralStacker`, `SystemStacker`, `VerticalStackingCalculator`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| StackingUtils | `anchorCeilingSs(int)` within/below staff → top staff line | unit | — | missing | assert `STAFF_TOP_Y_SS` for sp > TOP_STAFF_LINE |
| StackingUtils | `anchorCeilingSs(int)` at/above top line → above notehead | unit | — | missing | assert `sp*OFFSET - NOTE_HEAD_RADIUS_SS` for sp ≤ -4 |
| StackingUtils | `stackAbove` collision-aware placement (query-expand-min-reserve) | unit | `ArticulationStackingTest` (integration) | adequate | keep |
| StackingUtils | `stackAboveWithRegions` multi-region min-ceiling + per-region reserve | unit | `SystemTierStackingTest` (`ySs<0` only) | inadequate | exact-value via controlled extents; `<0` can't catch region/reservation bug |
| StackingUtils | `isRangeCovered(start,end)` | unit | — | missing | covered / uncovered / wrong-end |
| StackingUtils | symmetric horizontal margin (`STRUCTURAL_HORIZONTAL_MARGIN_SS`) on query+reserve | unit | margin never checked | missing | assert margin applied to queryX/queryWidth |
| StackingContext | `buildColumnMap` element→column | unit | — | missing | 2 columns → map per element |
| StackingContext | `updateLowestNoteBotSs` max-accumulation | unit | — | missing | ascending then descending → max kept |
| StackingContext | `updateBotContentExtentSs` max-accumulation | unit | — | missing | same pattern |
| StackingContext | `notesWithUpwardTie` default empty / setter replaces | unit | — | missing (low priority) | drives downstream margin branch |
| NoteAttachedStacker | `computeNoteBounds` stem-path vs type-geometry path | unit | indirect (`ArticulationStackingTest`, `<0` only) | inadequate | both paths; exact top/bot Ss |
| NoteAttachedStacker | `seedNoteBounds` updates `lowestNoteBotSs`/`botContentExtentSs` | unit | — | missing | assert context fields after seeding |
| NoteAttachedStacker | `seedTieBounds` upward arc → above; membership in `notesWithUpwardTie` only when protruding | unit | — | missing | controlled TieLayout; set membership + extents |
| NoteAttachedStacker | `seedTieBounds` downward arc → below; `botContentExtentSs` updated | unit | — | missing | stem-up tie |
| NoteAttachedStacker | `evaluateBezierYSs` cubic at t=0/0.5/1 | unit | — | missing | hand-computed control points |
| NoteAttachedStacker | reduced `TIE_DECORATION_MARGIN_SS` for upward-tie notes | unit | — | missing | articulation Y delta == margin delta |
| NoteAttachedStacker | `stackArticulations` precomposed staccato+accent; single glyphs; collision stacking | unit | `ArticulationStackingTest.PrecomposedGlyph`, `CollisionDetection.testAboveStaffArticulationsReserveSpaceInExtents` | adequate | keep |
| NoteAttachedStacker | `stackFermata` above articulations (ordering) | unit | `FermataTrillStackingTest.testFermataPositionedAboveArticulations` | adequate | keep |
| NoteAttachedStacker | `stackFermata` exact Y = `ceiling - margin - height` | unit | `FermataTrillStackingTest` (`ySs<0`) | inadequate | exact-value from controlled extents |
| NoteAttachedStacker | `stackSingleTrill` single-note → `endXSs=anchorXSs` | unit | `FermataTrillStackingTest.testSingleNoteTrillPositionedAboveNote` (`ySs<0`, no width) | inadequate | add exact single-note width assertion |
| NoteAttachedStacker | `stackSingleTrill` multi-note → spans anchor→end | unit | `FermataTrillStackingTest.testMultiNoteTrillReservesFullSpan` | adequate | keep |
| NoteAttachedStacker | `computePreviewDecorationLayouts` (static preview path) | unit | — | missing | fermata+staccato preview all above-staff |
| StructuralStacker | `stackSpanElement` null anchor/end → skipped | unit | — | missing | null anchor → no layout |
| StructuralStacker | `stackHairpins` crescendo/diminuendo above note-attached | unit | `StructuralTierStackingTest.HairpinStacking.*` (`ySs<0`) | inadequate | exact-value; `<0` passes at y=-0.001; consolidate redundant `…ProducesDecorationLayout` |
| StructuralStacker | `stackTuplets` above note-attached | unit | `StructuralTierStackingTest.TupletStacking.testTupletRangeElementPositionedAboveStaff` (`ySs<0`) | inadequate | exact-value |
| StructuralStacker | `stackTextDynamics` X centering = `noteheadCenterX - contentWidth/2` | unit | `StructuralTierStackingTest` (`ySs<0`, no X) | missing | assert centered `xSs` |
| StructuralStacker | `stackEndings` above hairpins (ordering); `heightSs`=`VOLTA_TICK_HEIGHT_SS` | unit | `StructuralTierStackingTest.EndingStacking.testEndingPositionedAboveHairpins`, `testEndingHasPositiveDimensions` | adequate | keep |
| StructuralStacker | `testNonOverlappingHairpinsAtSameHeight` | unit | `StructuralTierStackingTest` (only `ySs<0` each, never compared) | inadequate | **name-mismatch**: add `isCloseTo` equality or rename |
| SystemStacker | `stackAnnotations` X shifts with `xAlignment` (0/0.5/1) | unit | `SystemTierStackingTest` (`ySs<0`, no X) | missing | left/center/right → distinct formula-driven X |
| SystemStacker | `stackMetronomeAttachment` (tempo/beat-change) regions placement | unit | `SystemTierStackingTest` (`ySs<0`, dims `>0`, cross-tier `isLessThan`) | inadequate | exact-value for ≥1 region case (cross-tier ordering adequate) |
| SystemStacker | `testTempoAttachmentProducesLayout` | unit | `SystemTierStackingTest` (`isNotNull` only) | inadequate | fixture-only; merge with positioned test or add position/dim |
| VerticalStackingCalculator | `seedAccidentalsIntoStructural` high-note top reservation | unit | `VerticalStackingCalculatorTest.testSeedAccidentalsTranslatesToStaffCoordinatesForHighNote` (exact) | adequate | keep |
| VerticalStackingCalculator | accidental bottom reservation | unit | `…testSeedAccidentalsReservesSpaceAtAccidentalXForSharp` (`>=`) | inadequate | `>=` allows any value; change to exact `isCloseTo(botSs+centerYSs)` |
| VerticalStackingCalculator | grace note skipped | unit | `…testSeedAccidentalsIgnoresGraceNotes` (exact 0.0) | adequate | keep |
| VerticalStackingCalculator | `applyDecorationOffsets` Tuplet `getVerticalPositionSs` | unit | `ManualOffsetStackingTest` covers Trill/Ending/Hairpin/TempoChange/Fermata/Annotation; **Tuplet absent** | missing | add `TupletOffsets` test |
| VerticalStackingCalculator | `calculate` tier copy propagation (`copyTopFrom`) | unit | `StaffExtentsTest.CopyTopFrom` (primitive) + integration | adequate | keep |
| VerticalStackingCalculator | `calculate` `aboveStaffSs` = `max(MIN_ABOVE_STAFF_SS, -topExtent - STAFF_HALF_SS)` | unit | `LineHeightTest` (`>=`) | inadequate | exact-value with known decoration |
| VerticalStackingCalculator | `calculate` `belowStaffSs` max across 4 terms | unit | `LineHeightTest` (`>=`) | inadequate | pin exact `lineHeightSs` for below-staff note |
| VerticalStackingCalculator | `calculate` `belowContentSs` distinct, uses `botContentExtentSs` | unit | — | missing | downward-stem note → non-zero belowContent |
| VerticalStackingCalculator | `calculate` empty line → MIN above/below | unit | `LineHeightTest.testEmptyLineZeroReturnsMinimumHeight` (exact) | adequate | keep |

**3F notes (quality concerns):** **Systemic weak-assertion pattern (highest risk).** Most behaviors in `NoteAttachedStacker`/`StructuralStacker`/`SystemStacker` are covered only by `isLessThan(0.0)`/`isGreaterThan(0.0)` — they pass for any negative/positive value and cannot catch sign errors in the ceiling formula, wrong margin application, bad extents import, or off-by-a-constant bugs; a `-marginSs`→`+marginSs` mutation in `stackAbove` would survive every such test. The pattern pervades `FermataTrillStackingTest`, `StructuralTierStackingTest`, `SystemTierStackingTest`. **Name-mismatch:** `testNonOverlappingHairpinsAtSameHeight` asserts nothing about equal height (only `ySs<0` each). Weak disjunction: `testFermataAndTrillDoNotOverlap` OR-asserts two booleans (low diagnostic value). Fixture-only: `testTempoAttachmentProducesLayout` asserts `isNotNull` only. Redundant: crescendo/diminuendo/fermata `…ProducesDecorationLayout` duplicate the positioned tests. **Entirely uncovered:** `StackingUtils.anchorCeilingSs` (both branches), `isRangeCovered`, `NoteAttachedStacker.evaluateBezierYSs` (pure math), the tie-seeding paths + reduced-margin branch, `VerticalStackingCalculator.belowContentSs`, the Tuplet manual offset, `SystemStacker.stackAnnotations` X-alignment arithmetic, `computePreviewDecorationLayouts`. `VerticalStackingCalculatorTest` is the model to follow — it uses exact `isEqualTo` (except the one `>=` bottom-reservation assertion). `StaffExtents` (out of 3F scope) is in sound shape with exact assertions.

### layout — production observations (out of test-audit scope)

Filed as a single tracked GitHub issue ([#408](https://github.com/vasudeva-server/SongScribe/issues/408)) — these are real code observations, not test gaps, so the disposable matrix isn't their only home.

1. **⚠️ Dead code — `AttachmentLayout`, `CollisionDetector`, `RangeLayout`.** Verified zero references anywhere in `src/main` or `src/test` (grep for the bareword + Serena reference search). Appears to be scaffolding from an earlier layout architecture superseded by `LayoutResult.DecorationLayout`. Resolve by deletion in remediation rather than writing the ~12 "missing" tests their behaviors would otherwise warrant.
2. **`ElementBoundsSs.formatCssSpacing` — wrong unit suffixes (confirmed).** The multi-value branches emit `t + "px " + r + "ss"` (and 3-/4-value analogues), so all but the last token are labelled `px` even though the values are staff-spaces and the method's own javadoc shows all-`ss` output (`"4ss 8ss"`). Cosmetic (these CSS strings are inspection/debug output) but incorrect.
3. **`LyricLayoutBuilder` — stale comment.** Line 68: `// Extends 0.25 ss past the column right edge` while `STOP_MELISMA_OVERSHOOT_SS = 0.5`. The `{@value}` javadoc at lines 44/52 is correct; only the inline comment (and the echoing test comment) is stale.
4. **`HorizontalSpacingCalculator.needsAccidentalPush` — unused parameters / misleading contract.** Ignores `prevColumn` and `currXSs` and returns true whenever the current element has any accidental; the real clearance check lives in the caller. The signature implies a pre-check it doesn't perform. Likely intentional but a code smell — review.

### layout — summary

Audited all 37 production classes (excl. 2 `package-info`). Dominant patterns to drive remediation:

1. **Pure geometry/conversion/stacking math is the biggest blind spot — and it is the riskiest math in the app.** `LayoutEngine`'s beam/stem/tie engines, `ElementBoundsSs`' box model, `NoteGeometry`'s accidental widths, `StaffExtents.spToSs`/`ssToSp`, `StackingUtils.anchorCeilingSs`, `NoteAttachedStacker.evaluateBezierYSs`, `LineJustificationCalculator`'s compression math, and `LayoutResult`'s hit-testing family are exercised only as collaborators (or not at all) and asserted directly almost nowhere. These are cheap, high-value unit tests.
2. **"Weak-but-green" assertions are pervasive and systemic** — far more than in `dom`/`io`. The entire stacking-test family asserts `ySs<0`/`>0`; `LineHeightTest` and several builder/metrics tests assert `>=`/`>`/`isNotEqualTo`/`isLessThan` where exact values are statically computable; `isNotNull`/fixture-only tests stand in for behavioral assertions. A position/sign/constant mutation survives most of them.
3. **Self-referential oracles** — `HorizontalSpacingCalculatorTest` (entirely tautological), `PageModelTest` (contentArea + defaultLineWidthSs), `SongLayoutMetricsTest.verseBaselineY`, `LyricRenderMetricsTest.lyricBoxWidth`. Each compares production output to the same formula and cannot fail.
4. **Untested complex logic / branch & error paths** — `LineJustificationCalculator` (zero tests), `Ending.computeBracketRanges`/`computeCollisionRegions`, `LyricLayoutBuilder` dangling-hyphen + REST-extend (STOP/CONTINUE) branches, `isInvalidatedByInsertion` split-boundary, `LineEndingSupport` (8 production callers, no unit tests), `LayoutResult` insertion/lookup family.
5. **Dead code surfaced** — `AttachmentLayout`, `CollisionDetector`, `RangeLayout` (delete, don't test). Plus three minor code observations (CSS suffixes, stale comment, unused params).
6. **Misfiled-but-relevant:** many `dom`-class tests live under `layout/` (`TieTest`, `TupletTest`, `KeySignatureTest`, `RangeElementInvalidationTest`, `AnnotationAttachmentTest`, etc., already audited in Session 1) — relocate during the rewrite, not re-test.

