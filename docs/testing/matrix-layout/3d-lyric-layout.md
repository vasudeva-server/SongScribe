### 3D. lyric layout — `LyricBoxLayout`, `LyricConnectorLayout`, `LyricLayoutBuilder`, `LyricRenderMetrics`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| LyricBoxLayout | pure data record | none | — | none | no computation | — |
| LyricConnectorLayout | `Kind` enum + `NO_SOURCE_ELEMENT_INDEX` sentinel; discriminants drive rendering | none | `LyricConnectorRendererTest` (renderer level) | none | rendering assertion belongs to renderer | — |
| LyricRenderMetrics | `lyricBoxWidthSs("")` → 0.0 | unit | — | missing | empty guard | ✅ |
| LyricRenderMetrics | `lyricBoxWidthSs(text)` advance for non-empty | unit | `LyricRenderMetricsTest.testLyricBoxWidthSsMatchesLayoutBoxWidth` | inadequate | **self-referential**: builder stores `lyricBoxWidthSs(text)` then asserts equality; use independent oracle | ✅ |
| LyricRenderMetrics | `lyricBoxMetricsSs("")` → `LyricBoxMetrics.EMPTY` | unit | — | missing | empty guard | ✅ |
| LyricRenderMetrics | `lyricBoxMetricsSs(text)` advance/bearing/extent triple | unit | — | missing | fixed font or structural relations | ✅ |
| LyricRenderMetrics | `lyricBoxHeightSs()` positive ascent+descent | unit | — | missing | write test | ✅ |
| LyricRenderMetrics | `preferredHyphenCellWidthSs()` = `HYPHEN_WIDENING_FACTOR × hyphenWidthSs` | unit | — | missing | non-zero hyphen width | ✅ |
| LyricRenderMetrics | `COMPRESSED_MIN_SYLLABLE_GAP_SS < MIN_SYLLABLE_GAP_SS` invariant | unit | — | missing | ordering assertion | ✅ |
| LyricLayoutBuilder | empty line / no-lyrics → empty result | unit | `testEmptyLineProducesEmptyResult`, `testLineWithoutLyricsProducesEmptyResult` | adequate | keep | — |
| LyricLayoutBuilder | BEGIN/MIDDLE→opens HYPHEN, END closes (do-re-mi) | unit | `testDoReMiProducesThreeBoxesAndTwoHyphens` | inadequate | only count asserted; add HYPHEN start/end coords + sourceElementIndex | ✅ |
| LyricLayoutBuilder | SINGLE no-extend → box, no connector | unit | implicit via multi-element tests | adequate | keep | — |
| LyricLayoutBuilder | `computeLyricBoxLeftXSs` normal note: center − halfWidth | unit | — | missing | assert box X centering | ✅ |
| LyricLayoutBuilder | grace note: first glyph centred on grace notehead; host no box | unit | `testGraceLyricFirstGlyphCentredOnGraceNoteheadAndHostHasNoBox` | adequate | keep | — |
| LyricLayoutBuilder | `firstGraphemeClusterEndIndex` multi-codepoint cluster | unit | — | missing | combining mark + surrogate pair | ✅ |
| LyricLayoutBuilder | note no-lyric + active extender passes through | unit | `testExtenderSpansContinuationNotes` | adequate | keep | — |
| LyricLayoutBuilder | REST no-lyric → extender closed at rest left | unit | `testRestWithoutLyricBreaksExtender` | adequate | keep | — |
| LyricLayoutBuilder | REST + START → extender continues | unit | `testRestWithExtendingLyricContinuesExtender` | adequate | keep | — |
| LyricLayoutBuilder | REST + CONTINUE → extender continues (distinct sub-case) | unit | — | missing | CONTINUE on rest | ✅ |
| LyricLayoutBuilder | REST + STOP → closes `STOP_MELISMA_OVERSHOOT_SS` past rest right | unit | — | missing | assert ending = rightEdge + overshoot | ✅ |
| LyricLayoutBuilder | note + STOP → closes with overshoot, no box | unit | `testStopCarrierEndsExtenderAtNoteRightEdge` | adequate | keep (assertion uses constant; see stale-comment observation) | — |
| LyricLayoutBuilder | note + CONTINUE passes through | unit | `testContinueCarrierPassesThrough` | adequate | keep | — |
| LyricLayoutBuilder | BEGIN+START → hyphen, extender suppressed | unit | `testNonFinalSyllableWithMelismaEmitsHyphenOnly` | adequate | keep | — |
| LyricLayoutBuilder | extender opens SINGLE+START, closes at next text note | unit | `testExtenderSpansContinuationNotes` | adequate | keep (but `startXSs` unverified) | — |
| LyricLayoutBuilder | NONE-extend text note with active extender closes at box left | unit | `testContinueCarrierPassesThrough` | adequate | keep | — |
| LyricLayoutBuilder | dangling extender extends through CONTINUE/STOP not bare notes | unit | `testDanglingExtenderEndsAtStartNoteWhenNoContinueFollows`, `testDanglingExtenderExtendsThroughContinueMarkers` | adequate | keep | — |
| LyricLayoutBuilder | trailing continuation flag + leading stub from x=0 | unit | `testTrailingContinuationAndLeadingStub` | adequate | keep | — |
| LyricLayoutBuilder | `emitDanglingHyphen` no eligible follower → LOG.error, no connector | unit | — | missing | open BEGIN at line end → no DANGLING_HYPHEN | ✅ |
| LyricLayoutBuilder | DANGLING_HYPHEN emitted to next eligible element left edge | unit | — (renderer test uses hand-built record) | missing | builder coords for DANGLING_HYPHEN | ✅ |
| LyricLayoutBuilder | `sourceElementIndex` on HYPHEN/EXTENDER/DANGLING_* | unit | never asserted | missing | ≥1 assertion per kind | ✅ |
| LyricLayoutBuilder | multi-verse separate boxes/connectors by `verseIndex`; `verseCount` = max verse | unit | `testMultiVerseProducesSeparateBoxesPerVerse` | adequate | keep | — |
| LyricLayoutBuilder | verse-1 (`getSyllableWidthSs`) vs verse-≥2 (`lyricBoxWidthSs`) equal width for same text | unit | — | missing | catch divergence between cached and on-the-fly paths | ✅ |
| LyricLayoutBuilder | compound-word boundary (BEGIN+compound) opens HYPHEN | unit | `testCompoundWordBoundaryProducesHyphen` | adequate | keep | — |
| LyricLayoutBuilder | lyric boxes appear after insertion | e2e | — | missing (low priority) | optional rendering smoke; geometry fully unit-coverable | ⬜ |

**3D notes (quality concerns):** Highest-risk defect: **`LyricRenderMetricsTest.testLyricBoxWidthSsMatchesLayoutBoxWidth` is a self-referential oracle** — verse-2 builder calls `lyricBoxWidthSs(text)` to populate `box.widthSs()`, then asserts `box.widthSs() ≈ lyricBoxWidthSs(text)`, i.e. `f(x) ≈ f(x)`; needs an independent oracle. Second: **HYPHEN/EXTENDER connector geometry** — `testDoReMi…` and `testExtenderSpansContinuationNotes` verify counts but never `startXSs`/`endXSs`, so an incorrectly anchored hyphen passes. Two untested branches carry real risk: **REST + STOP** (overshoot past rest right edge — distinct path from note-STOP) and **REST + CONTINUE** (distinct value in the compound condition from the tested START). **`emitDanglingHyphen`** has no builder-level test (happy path nor LOG.error path); the only DANGLING_HYPHEN test uses a hand-crafted record. `firstGraphemeClusterEndIndex` is tested only with ASCII so surrogate-pair/combining-mark regressions to naive `charAt(0)` would survive. Out-of-scope production observation: `LyricLayoutBuilder.java:68` comment says "Extends 0.25 ss past the column right edge" but `STOP_MELISMA_OVERSHOOT_SS = 0.5` — stale comment (the test comment repeats it; assertions correctly use the constant).

