### 3A. orchestration & accumulation — `LayoutEngine`, `LayoutAccumulator`, `LayoutResult`, `LayoutLayer`, `SectionLayout`, `PageModel`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| LayoutEngine | `layout()` returns non-null with clef at `CLEF_X_POSITION_SS` | unit | `LayoutEngineTest.testLayoutStoresClefAtStandardPosition` | adequate | keep | — |
| LayoutEngine | `layout()` places key signature immediately after clef (type + accidental count) | unit | `LayoutEngineTest.testLayoutStoresKeySignatureAfterClef` | adequate | keep | — |
| LayoutEngine | `layout(line, true)` pins FINAL_DOUBLE_BARLINE flush-right | unit | `LayoutEngineTest.testFinalBarlineFlushRightOnLastLine` | adequate | keep | — |
| LayoutEngine | `layout(line, true)` pins REPEAT_RIGHT terminal flush-right | unit | `LayoutEngineTest.testRightRepeatTerminalFlushRightOnLastLine` | adequate | keep | — |
| LayoutEngine | `layout(line, false)` does NOT place barline flush-right | unit | `LayoutEngineTest.testFinalBarlineNotFlushRightOnNonLastLine` | inadequate | negative `isNotCloseTo(flushRight)` survives any wrong value (incl. X=0); assert exact expected X from horizontal spacing | ✅ |
| LayoutEngine | empty line returns non-null result with `MIN_LINE_HEIGHT_SS` | unit | `LineHeightTest.testEmptyLineZeroReturnsMinimumHeight`, `testEmptyNonLastLineReturnsMinimumHeight` | adequate | keep | — |
| LayoutEngine | empty-line result still contains clef + key signature | unit | — | missing | assert `getClef()`/`getKeySignature()` non-null on empty line | ✅ |
| LayoutEngine | un-justifiable line → `layout()` returns null, `getLastError()` non-null | unit | — | missing | over-stuffed line → null result + descriptive error | ✅ |
| LayoutEngine | 3-arg `layout(line,false,true)` threads `hasLeadingLyricContinuation` to lyric layout | unit | — | missing | extending melisma → leading lyric connector at x=0 | ✅ |
| LayoutEngine | unbeamed note below middle line (sp>0) → stem up | unit | — | missing | crotchet at sp=2 → stem-up geometry | ✅ |
| LayoutEngine | unbeamed note above middle line (sp≤0) → stem down | unit | — | missing | crotchet at sp=-2 → stem-down | ✅ |
| LayoutEngine | unbeamed grace note always stem up | unit | — | missing | grace at sp=-4 still stem-up, length `GRACE_NOTE_STEM_LENGTH_SS` | ✅ |
| LayoutEngine | manual stem override not auto-corrected | unit | — | missing | `upper=false`,`stemDirectionAuto=false` at sp=4 → stays down | ✅ |
| LayoutEngine | beamed group auto-direction (above→down, below→up) | unit | — | missing | two tests on `BeamLayout.stemsUp()` | ✅ |
| LayoutEngine | beamed group manual override: first explicit direction wins for whole group | unit | — | missing | first note `upper=true` → `stemsUp=true` | ✅ |
| LayoutEngine | beam slope hyperbolic dampening clamps below `BEAM_SLOPE_MAX` | unit | — | missing | large pitch diff → `abs(slope) < BEAM_SLOPE_MAX` | ✅ |
| LayoutEngine | beam slope-reduction loop: all stems ≥ `MIN_STEM_SS` | unit | — | missing | large contour → every stem ≥ MIN_STEM_SS | ✅ |
| LayoutEngine | flat-beam snapping: slope<0.05 snaps `startYSs` to 0.5 grid | unit | — | missing | equal-position quavers → startYSs multiple of 0.5 | ✅ |
| LayoutEngine | beam thickening: non-zero slope → `thickeningSs` in `(0, BEAM_DEPTH_SS*0.088]` | unit | — | missing | sloped group → bounded thickening | ✅ |
| LayoutEngine | stub direction: isolated semiquaver gets stub-right | unit | — | missing | quaver+semiquaver beam → `stubRight=true` | ✅ |
| LayoutEngine | tie geometry: `startXSs = noteX + TIE_NOTEHEAD_HALF_WIDTH_SS` | unit | — | missing | adjacent-note tie offset | ✅ |
| LayoutEngine | tie shoulder height clamped to `[TIE_MIN, TIE_MAX]` | unit | — | missing | narrow→min, wide→max | ⬜ |
| LayoutEngine | tie collision: interior note deflects arc outward | unit | — | missing | 3-note tie over intersecting note → larger outer control Y | ⬜ |
| LayoutEngine | tie direction: stem-up note ties below (+1) | unit | — | missing | stem-up note → arc bulges down | ⬜ |
| LayoutEngine | `createHeaderElements` null `keyType` → `KeyType.NONE` | unit | `LayoutEngineTest.testLayoutStoresKeySignatureAfterClef` (non-null only) | missing | null keyType → keySig type NONE | ⬜ |
| LayoutEngine | `beamCount` → 1/2/3 for QUAVER/SEMIQUAVER/DEMI_SEMIQUAVER | unit | — | missing | widen to package-private; assert each | ⬜ |
| LayoutResult | `Builder.setClef`/`setKeySignature` round-trip; default null | unit | `LayoutResultTest.testBuilderClefRoundTrip`, `…KeySignatureRoundTrip`, `…DefaultsToNullHeaderElements` | adequate | keep | — |
| LayoutResult | `getLyricAnchor` box-anchored centerX+baselineY; column fallback; Y==`verseYSsInLine(1)`; throws ISE w/ neither | unit | `LayoutResultTest.testGetLyricAnchor*` (4) | adequate | keep | — |
| LayoutResult | `hitTestLyric` inside-box hit / outside-box miss | unit | `LayoutResultTest.testHitTestLyric*` (2) | adequate | keep | — |
| LayoutResult | `findElementAtXSs` returns index within head bounds / -1 in gap | unit | — | missing | two known-X columns; hit + gap | ⬜ |
| LayoutResult | `findInsertionIndex` over-head / before-first(0) / after-last(`effectiveElementCount`) / in-gap(slot) | unit | — | missing | write 4 tests | ⬜ |
| LayoutResult | `calculateInsertionXSs` empty / over-head snap / terminal right-align / after-last spacing / between-midpoint | unit | — | missing | write 5 tests | ⬜ |
| LayoutResult | `getBelowStaffReservationSs` = `lineHeight - aboveStaff - STAFF_HEIGHT_SS` | unit | — | missing | known-values test | ⬜ |
| LayoutResult | `lyricAreaBaseYSs` shifts with `aboveStaffSs`/`belowContentSs` | unit | `LayoutResultTest.testHitTestLyricHitsInsideBounds` (indirect) | inadequate | focused test pinning the formula | ⬜ |
| LayoutResult | `findAttachmentBounds` correct owner/type; null unknown owner | unit | — (stacking tests use `findAttachmentDecorationLayout`) | missing | two same-type attachments on different owners | ⬜ |
| LayoutResult | `findRangeElementBounds` by anchor+end+type | unit | — | missing | write test | ⬜ |
| LayoutResult | `findAttachment` matching owner/type else null | unit | — | missing | write test | ⬜ |
| LayoutResult | `findRangeElementDecorationLayout` by anchor+type | unit | covered transitively (`FermataTrillStackingTest` etc. use attachment variant) | inadequate | focused range-element test | ⬜ |
| LayoutResult | `contains` true iff `elementBounds` has element | unit | — | missing | write test | ⬜ |
| LayoutResult | `getDecorationLayoutsByType` filters by class | unit | — | missing | two types → each filtered list correct | ⬜ |
| LayoutResult | `getElementXSs` 0 / `getElementPosition` null for unknown element | unit | — | missing | write tests | ⬜ |
| LayoutAccumulator | `add`/`intersects` (Rectangle2D + Area), overlap true / non-overlap false | unit | — | missing | write tests | ⬜ |
| LayoutAccumulator | `clear` → `isEmpty` true and `intersects` false; fresh `isEmpty` true | unit | — | missing | write tests | ⬜ |
| LayoutAccumulator | `getArea()` returns defensive copy | unit | — | missing | mutate return; accumulator unchanged | ⬜ |
| LayoutAccumulator | union of two rects intersects a spanning rect | unit | — | missing | write test | ⬜ |
| SectionLayout | `hasContent()` true non-empty / false empty list / false empty first line | unit | — | missing | 3 tests | ⬜ |
| SectionLayout | `getText()` first line / "" when empty | unit | — | missing | write tests | ⬜ |
| SectionLayout | `getHeight()` from content bounds | unit | — | missing | known-bounds test | ⬜ |
| SectionLayout | `empty()` factory: zero size, no lines, null font | unit | — | missing | assert each property | ⬜ |
| SectionLayout | 2-arg string ctor wraps text in single-element list | unit | — | missing | round-trip via `lines()` | ⬜ |
| SectionLayout | `lines()` immutable (defensive copy) | unit | — | missing | mutate source; `lines()` unchanged | ⬜ |
| PageModel | `Size.LETTER`/`A4` dimensions | unit | `PageModelTest.SizeEnum.*` (2) | adequate | keep | — |
| PageModel | `getSize()` default LETTER / "a4" / case-insensitive / unknown→LETTER | unit | `PageModelTest.PageSizeFromPrefs.*` (4) | adequate | keep | — |
| PageModel | `getPageWidthPx`/`getPageHeightPx` for LETTER+A4 | unit | `PageModelTest.PageDimensionsPx` (4) | adequate | keep | — |
| PageModel | top/bottom margins = 0.5"; horizontal centers; 0 when line ≥ page | unit | `PageModelTest.Margins.*` (5) | adequate | keep | — |
| PageModel | `getContentAreaWidthPx` = `pageWidth - 2*defaultMargin` | unit | `PageModelTest.ContentArea.contentAreaWidthAccountsForDefaultMargins` | inadequate | self-referential (expected uses same formula); pin concrete px for LETTER | ⬜ |
| PageModel | `getMaxLineWidthInches`=7.77 / `getMinLineWidthInches`=5.0 | unit | `PageModelTest.LineWidthConstants.*` (2) | adequate | keep | — |
| PageModel | `getDefaultLineWidthSs` = `pxToSs(contentAreaWidthPx)` | unit | `PageModelTest.DefaultLineWidth.defaultLineWidthSsMatchesContentArea` | inadequate | self-referential oracle; pin explicit LETTER constant | ⬜ |
| PageModel | size changes reactively on pref change; A4 width < LETTER | unit | `PageModelTest.PageSizeChange.*` (2) | adequate | keep | — |
| LayoutLayer | enum constants (ELEMENT, TIE, …, LYRICS) | none | — | none | pure enum, no derivation | — |
| LayoutEngine/VSC | high/low note increases line height | unit | `LineHeightTest.testHighNoteAboveStaffIncreasesLineHeight`, `testLowNoteBelowStaffIncreasesLineHeight` | inadequate | `>=MIN_LINE_HEIGHT_SS` passes even if extension broken; assert exact height for the staff position | ⬜ |

**3A notes (quality concerns):** **The highest-risk gap is the total absence of tests for `LayoutEngine`'s three geometry engines** — beam slope/direction/stub logic, unbeamed stem-direction assignment, and tie Bézier geometry. This is the densest math in the package (hyperbolic dampening, iterative slope reduction, 20-iteration convergence, Bézier collision avoidance) with zero coverage; mutations to `< MIN_STEM_SS` or the `stemsUp ? pos<anchor : pos>anchor` branch would survive. `LayoutAccumulator` and `SectionLayout` have zero coverage despite real branching (`hasContent()`, `intersects()`, `clear()`) — trivially unit-testable, no mocking. Two `PageModelTest` tests are self-referential oracles (`contentAreaWidthAccountsForDefaultMargins`, `defaultLineWidthSsMatchesContentArea`). `LineHeightTest`'s high/low-note tests use `>=MIN_LINE_HEIGHT_SS` (the universal floor) — green even if the height extension returns exactly the minimum. `LayoutResult`'s hit-testing/insertion/lookup family (`findElementAtXSs`, `findInsertionIndex`, `calculateInsertionXSs`, `findAttachmentBounds`, `findRangeElementBounds`, `findAttachment`, `contains`, `getDecorationLayoutsByType`) is pure map-lookup logic, all untested, all straightforwardly unit-testable via `Builder`. `LayoutLayer` correctly classified `none`.

