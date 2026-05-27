### 3C. geometry primitives & metrics — `ElementBoundsSs`, `InsetsSs`, `Size`, `Margin`, `MarginReference`, `LineThickness`, `NoteGeometry`, `StaffExtents`, `VerticalOrder`, `SongLayoutMetrics`, `SongLayoutMetricsBuilder`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| ElementBoundsSs | `uniform`/`withMargin`/`withMarginOnly`/`contentOnly` factories | unit | — | missing | assert exact layer rects per factory (incl. no-top-margin in `withMargin`) | ✅ |
| ElementBoundsSs | `collapsedMarginWith(below)` = `max(thisBottom, belowTop)` CSS collapse | unit | — | missing | this-wins / below-wins / equal | ✅ |
| ElementBoundsSs | `containsForHitTest` delegates to padding bounds | unit | — | missing | inside-padding / inside-content-outside-padding / outside | ✅ |
| ElementBoundsSs | `intersectsMargin`/`intersectsPadding` layer-specific | unit | — | missing | overlap + non-overlap per layer | ✅ |
| ElementBoundsSs | `translate(dx,dy)` shifts all four layers (incl. nullable visual) | unit | — | missing | verify exact shift | ⬜ |
| ElementBoundsSs | `getVisualBounds()` explicit-visual vs `marginBoundsSs` fallback | unit | — | missing | both branches | ⬜ |
| ElementBoundsSs | coordinate accessors (`getTop/Bottom/Left/Right/MarginTop/MarginBottom Ss`) | unit | — | missing | fold into factory tests | ⬜ |
| ElementBoundsSs | `formatCssSpacing` 5-branch shorthand (all-zero/all-same/2/3/4-value) | unit | — | missing | each branch; **see production observation re px/ss suffixes** | ⬜ |
| ElementBoundsSs | `getPaddingCss`/`getMarginCss` pass correct differentials | unit | — | missing | uniform + asymmetric | ⬜ |
| InsetsSs | `toInsetsPx()` rounds all four via `ScaleContext.ssToRoundedPx` | unit | — | missing | known scale → exact Insets fields | ⬜ |
| Size | pure record (`ZERO`, width/height) | none | — | none | trivial data holder | — |
| Margin | `uniform(m)` all sides equal; `NONE` all zero | unit | — | missing | one-liner asserting invariant | ⬜ |
| MarginReference | pure documentation enum | none | — | none | no branching | — |
| LineThickness | each field = `LILYPOND_BASE_THICKNESS_SS × multiplier` | unit | only `barlineSeparationSs()` exercised indirectly (`ElementTypeTest`) | inadequate | assert `stemSs`/`ledgerLineSs`/`hairpinSs`/`voltaBracketSs`/`tupletBracketSs` + multipliers | ⬜ |
| LineThickness | `barlineSeparationSs()` = `staffLineSs × BARLINE_SEPARATION_MULTIPLIER` | unit | `ElementTypeTest.testDoubleBarlineWidth` | adequate | keep | — |
| LineThickness | `repeatRightThinBarlineCenterXSs`/`repeatRightAfterThickXSs` arithmetic | unit | — | missing | known-constant tests | ⬜ |
| NoteGeometry | `initializeAccidentalWidths()` idempotent | unit | `NoteRendererTest`/`NoteAreaBuilderTest` (called twice) | adequate | keep | — |
| NoteGeometry | `getAccidentalWidthSs(note)` dispatch small/base/parens; 0 for none | unit | — | missing | each accidental kind; exact SMuFL width | ⬜ |
| NoteGeometry | `getAccidentalBoundsSs(note)` null/grace-null/table | unit | `NoteRendererTest` (directional only) | inadequate | weak `isNegative`/`isPositive`; pin exact for ≥1; add DOUBLE_SHARP/NATURAL_* variants | ⬜ |
| NoteGeometry | `getLedgerLineOverhangSs(note)` 0 in-staff / `LEDGER_LINE_EXTENSION_SS` out | unit | — | missing | |sp|≤5 vs >5 with `drawStaveLongitude` | ⬜ |
| NoteGeometry | `getNoteheadXOffsetSs(type,upper)` `-stemWidth/2` stem-down else 0 | unit | — | missing | stemmed up/down/non-stemmed | ⬜ |
| NoteGeometry | `getNoteheadRightEdgeSs(note)` SMuFL bbox + fallback | unit | — | missing | known bbox + null fallback | ⬜ |
| NoteGeometry | `walkAccidentalGlyphs` visitor advances/parens/kerning | unit | — | missing | verify emitted positions for known sequence | ⬜ |
| StaffExtents | `spToSs(sp)` = sp×0.5 | unit | fixture input only (`VerticalStackingCalculatorTest`) | missing | direct: spToSs(0/2/-4) + round-trip | ⬜ |
| StaffExtents | `ssToSp(ss)` = round(ss/0.5) | unit | — | missing | exact + rounding boundaries | ⬜ |
| StaffExtents | `xToStep` clamp (private, via ySet/yGet) | unit | `StaffExtentsTest` (clamping via ySet/yGet) | adequate | keep | — |
| StaffExtents | `ySet`/`yGet` reserve/query above/below | unit | `StaffExtentsTest` (defaults, overlaps, clamp, isolation) | adequate | keep | — |
| StaffExtents | `copyTopFrom` copies top, leaves bot | unit | `StaffExtentsTest.CopyTopFrom` | adequate | keep | — |
| StaffExtents | derived constants (`MIN_ABOVE/BELOW_STAFF_SS`, `MIN/MAX_STAFF_POSITION_SP`) | unit | used, never asserted | missing | pin computed values (catch `STAFF_LINES_ABOVE/BELOW` change) | ⬜ |
| VerticalOrder | `isAboveStaff`/`isBelowStaff` relative to `NOTE_STEM.order` | unit | — | missing | each constant; NOTE_STEM neither | ⬜ |
| VerticalOrder | `compareByOrder` | unit | — | missing | <0 / >0 / 0 cases | ⬜ |
| SongLayoutMetrics | `staffTopYSsInLine`/`staffBottomYSsInLine` | unit | `SongLayoutMetricsTest.testStaffYHelpers` | adequate | keep | — |
| SongLayoutMetrics | `verseYSsInLine(verse)` formula | unit | `SongLayoutMetricsTest.testVerseBaselineYHelper` | inadequate | **self-referential** (expected from same accessors); pin concrete literals | ⬜ |
| SongLayoutMetricsBuilder | `build()` max above/below/belowContent; floor at `MIN_*` | unit | `SongLayoutMetricsTest` (empty, max above/below/belowContent) | adequate | keep | — |
| SongLayoutMetricsBuilder | lyricsBand collapse when `verseCount==0` | unit | `SongLayoutMetricsTest.testVerseCountCollapsesWhenNoLyrics` | adequate | keep | — |
| SongLayoutMetricsBuilder | `staffToLyricsGapSs = LYRICS_ROW_MARGIN_SS + lyricAscentSs` | unit | `…testLyricsBandPopulatedWhenVersesPresent` (passes ascent=0) | inadequate | `lyricAscentSs` addend never tested (always 0); add non-zero ascent | ⬜ |
| SongLayoutMetricsBuilder | `totalLineHeightSs` includes lyricsBand | unit | `…testLyricsBandPopulatedWhenVersesPresent` | adequate | keep | — |
| SongLayoutMetricsBuilder | `MIN_LINE_HEIGHT_SS` constant | unit | `LineHeightTest` (`>=` weak) | inadequate | pin exact height for note at `MIN_STAFF_POSITION_SP` | ⬜ |

**3C notes (quality concerns):** **`ElementBoundsSs`** — the central CSS-like box-model type used throughout layout — has zero unit tests: all factories, `collapsedMarginWith`, `containsForHitTest`, both intersections, `translate`, `getVisualBounds` fallback, and the 5-branch `formatCssSpacing` are untested (and `formatCssSpacing` has a confirmed px/ss suffix bug — see production observations). Second: **`NoteGeometry` accidental geometry** — `getAccidentalBoundsSs` is covered only directionally (`isNegative`/`isPositive`) so a double-width regression stays green, and `getAccidentalWidthSs` (the width that positions notes horizontally) has no test at all; `getLedgerLineOverhangSs`/`getNoteheadXOffsetSs`/`getNoteheadRightEdgeSs` untested. `SongLayoutMetricsTest.testVerseBaselineYHelper` is a self-referential oracle. `SongLayoutMetricsBuilder` is never tested with non-zero `lyricAscentSs`, so the `+ lyricAscentSs` addend is dead from a test view. **`StaffExtents.spToSs`/`ssToSp`** — the project-canonical Sp↔Ss converters — have no direct assertion (echoes the `dom` `ScaleContext` finding). `InsetsSs.toInsetsPx` (the only non-trivial Ss→Px conversion among the records) is untested. `VerticalOrder`'s branching predicates (`isAboveStaff`/`isBelowStaff`/`compareByOrder`) drive stacking yet are untested. `LineThickness` non-barline fields and the two repeat-barline helpers are untested. `LineHeightTest` high/low-note tests use `>=MIN_LINE_HEIGHT_SS` (weak).

