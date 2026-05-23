## 9. `ui/renderer` (audited 2026-05-22)

Audited via three production-first sub-audits run in one wave: **9A** renderer
infrastructure + note-area geometry; **9B** span / connector renderers; **9C**
glyph / element painters. Read-only; e2e assessed from source only; coverage
checked across unit (mirrored + cross-package) and e2e. Scope: 29 production
classes (+ 1 `package-info`). Tallies below are parsed directly from the verdict
column of each table (the sub-audits' own prose self-counts drifted and were
corrected to match).

### 9A — Renderer infrastructure + note-area geometry

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| ElementRenderer | Strategy interface — no logic | none | — | none | — |
| ElementFrame | `hasOverrideElementX()` — NaN vs. finite | unit | `testHasOverrideElementXFalseForNaN`, `testHasOverrideElementXTrueForFiniteValue` | adequate | — |
| ElementFrame | `hasPreviewShift()` — negative vs. non-negative index | unit | `testHasPreviewShiftFalseForNegativeIndex`, `testHasPreviewShiftTrueForNonNegativeIndex` | adequate | — |
| ElementFrame | `LINE_LEVEL` constant values | unit | `testLineLevelHasNoElementOverrideOrShift` | adequate | — |
| ElementFrame | `lineLevelWithPreviewShift()` — copies LINE_LEVEL indices, attaches shift | unit | — | missing | Add unit test: verify currentElementIndex==-1, overrideXSs==NaN, fromIndex/shiftSs match args |
| ElementFrame | `withElement()` — creates per-element frame, inherits preview shift | unit | — | missing | Add unit test: verify element index + override set, previewShift inherited from parent |
| GraphicsState | `save()` + `close()` restore contract (bitmask-gated, per-property) | unit | — | missing | Add unit test with a mocked/real Graphics2D: set properties, enter try-with-resources, modify, confirm restore on close |
| GraphicsState | `Property` enum / `has()` bitmask — no separate logic beyond branching in save/close | none | — | none | — |
| RenderContext | Pure interface — no logic | none | — | none | — |
| RenderingUtils | `getDecorationColor()` — null line → preview color | unit | `testGetDecorationColorNullLineReturnsPreviewColor` | adequate | — |
| RenderingUtils | `getDecorationColor()` — element not in line → preview color | unit | `testGetDecorationColorElementNotInLineReturnsPreviewColor` | adequate | — |
| RenderingUtils | `getDecorationColor()` — element in line → `invariants.getElementColor(index)` | unit | `testGetDecorationColorElementInLineReturnsCtxColor` | adequate | — |
| RenderingUtils | `getDecorationColor()` fast path — frame has valid element index (≥0) bypasses line scan | unit | — | missing | Add unit test: construct frame with valid elementIndex, verify fast-path color returned without consulting line |
| RenderingUtils | `noteStaffPositionToCoordinateSs()` — trivial delegation to `spToSs` + offset | none | — | none | — |
| RenderingUtils | `forEachLedgerLineYSs()` — parity normalization + stepping loop, both above and below staff | unit | — | missing | Add unit tests: positions above/below staff, on-staff (no callback), parity normalization edge cases |
| RenderingUtils | `centeredGlyphX()` — multi-term centering: noteheadCenter + xOffset − bBoxLeft − glyphWidth/2 | unit | — | missing | Add unit test: assert computed X equals expected arithmetic result for known inputs |
| RenderingUtils | `glyphOriginYFromLayoutTop()` — trivial subtraction (layoutTop − bbox.top) | none | — | none | — |
| RenderingUtils | `stemCenterXOffsetSs()` — branches on minim vs. black notehead, upper vs. lower | unit | — | missing | Add unit tests: all 4 combinations (minim-up, minim-down, black-up, black-down) |
| RenderingUtils | `layoutYToComponentYSs()` — trivial addition | none | — | none | — |
| RenderingUtils | `drawLedgerLine`, `drawBravuraGlyph`, `applyDecorationColor` — pure painting | none | — | none | — |
| LineInvariants | `getElementColor()` — not in edit mode → BLACK | unit | `testNotEditModeReturnsBlack` | adequate | — |
| LineInvariants | `getElementColor()` — playing note → playing color | unit | `testPlayingElementReturnsPlayingColor` | adequate | — |
| LineInvariants | `getElementColor()` — grace note playing → playing color | unit | `testGraceNoteCountsAsPlaying` | adequate | — |
| LineInvariants | `getElementColor()` — element in playing tie → playing color | unit | `testElementInPlayingTieReturnsPlayingColor` | adequate | — |
| LineInvariants | `getElementColor()` — selected element → selectionColor | unit | `testSelectedElementReturnsSelectionColor` | adequate | — |
| LineInvariants | `getElementColor()` — hovered (replaced-element) → REPLACED_ELEMENT_COLOR | unit | — | missing | Add unit test: mockStatic PreviewElementManager to return matching location; verify semi-transparent red returned |
| LineInvariants | `getElementColor()` — default (none of the above) → BLACK | unit | `testDefaultReturnsBlack` | adequate | — |
| LineInvariants | `isElementPlaying()` — both primary and grace note | unit | `testIsElementPlayingFalseForUnrelatedIndex`, `testGraceNoteCountsAsPlaying` | adequate | — |
| LineInvariants | `isElementInPlayingTie()` — in tie vs. no playing note | unit | `testElementInPlayingTieReturnsPlayingColor`, `testIsElementInPlayingTieFalseWithoutPlayingNote` | adequate | — |
| LineInvariants | `getLyricColor()` + span-aware `isLyricSpanPlaying()` — melisma/BEGIN-MIDDLE/tied spans | unit | — | missing | Add unit tests covering: anchor playing, tied anchor, melisma extender carrier playing, BEGIN/MIDDLE continuation, span end boundary, no lyric on element |
| LineInvariants | `getLyricConnectorColor()` — 3 branches (sourceIndex<0, no line, delegate to colorFor) | unit | — | missing | Add unit tests for each branch |
| LineInvariants | `Builder.build()` validation — throws `IllegalStateException` when required fields unset | unit | — | missing | Add unit test: assert `assertThatThrownBy` when any of layoutResult/songLayoutMetrics/lyricRenderMetrics is null |
| LineInvariants | Trivial getters (getSong, getFonts, getCurrentLine, getMiddleLineYSs, getLineIndex, etc.) | none | — | none | — |
| NoteArea | Pure data record holder | none | — | none | — |
| NoteAreaBuilder | `getOrBuildArea()` cache hit — same instance returned when note unchanged | unit | `testAreaCacheReturnsSameInstanceWhenNoteUnchanged` | adequate | — |
| NoteAreaBuilder | `getOrBuildArea()` cache invalidation — all 7 attribute-change cases | unit | `testAreaCacheRebuilds*` (7 tests) | adequate | — |
| NoteAreaBuilder | `getOrBuildArea()` cache stable — on-staff position change within same ledger tier | unit | `testAreaCacheRetainsCacheWhenStaffPositionChangesWithinStaff` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — quarter note (only asserts `isEmpty()==false`) | unit | `testBuildNoteAreaQuarterNoteNoExtras` | inadequate | Strengthen: assert bounds height > 0 and bounds width > 0 (or compare with a known baseline geometry) |
| NoteAreaBuilder | `buildNoteArea()` — with accidental extends left | unit | `testBuildNoteAreaWithAccidentalExtendsLeft` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — dots extend right (one dot, two dots) | unit | `testBuildNoteAreaWithDotsIsWider`, `testBuildNoteAreaWithTwoDotsIsWiderThanOne` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — ledger lines above staff extend bounds width | unit | `testBuildNoteAreaWithLedgerLinesAboveStaff` | adequate | — |
| NoteAreaBuilder | `buildNoteArea()` — ledger lines below staff (only asserts `isEmpty()==false`) | unit | `testBuildNoteAreaWithLedgerLinesBelowStaff` | inadequate | Strengthen: verify bounds width is wider than note on-staff, mirroring the above-staff test |
| NoteAreaBuilder | `buildNoteArea()` — whole note / half note / grace note noteheads | unit | — | missing | Add tests for SEMIBREVE, MINIM, grace noteType variants (different shape constants are selected) |
| NoteAreaBuilder | `buildNoteArea()` — beamed flag suppression (flag absent when beamed=true) | unit | — | missing | Add test: beamed area max-Y should be smaller than non-beamed (flag suppressed) for a quaver stem-up |
| NoteAreaBuilder | `createOffsetArea()` — contains original, expands bounds | unit | `testCreateOffsetAreaContainsOriginal`, `testCreateOffsetAreaExpandsShape` | adequate | — |
| NoteAreaBuilder | `getLedgerLineCount` boundary tests (tested here, belong in StaffElementTest) | unit | `testGetLedgerLineCount*` (3 tests) | redundant | Move to `StaffElementTest`; they test `StaffElement.getLedgerLineCount()`, not `NoteAreaBuilder` |

**Notes.** Rows: 46. Tally: adequate 21, missing 13, inadequate 2, none 9, redundant 1. No dead code found — all public/package-private symbols in scope have active callers in the production tree. Production observations: (1) `GraphicsState.close()` silently skips restoration when a saved value is `null` (e.g., `color`, `font`, `transform`) — this is intentional for rendering hints but means a `save(COLOR)` on a context whose `getColor()` returns `null` will never restore. In practice `Graphics2D` implementations do not return `null` from `getColor()`, but the guard is asymmetric: `CLIP` restores unconditionally while all other properties guard on `!= null`. A future implementor swapping in a custom `Graphics2D` could observe silent no-restore for `COLOR`/`STROKE`/`FONT`/`TRANSFORM`. (2) `NoteAreaBuilder.addAccidentalToArea()` uses `ACCIDENTAL_HEIGHT_SS` (derived from the sharp bbox) as a uniform height for all accidentals, which overestimates the natural bounding area. This is documented as an approximation, but a double-flat is taller than a sharp — so the area may understate the actual footprint for that accidental, potentially letting a glissando endpoint land too close to a double-flat. (3) In `LineInvariants.isLyricSpanPlaying()`, when iterating forward for a STOP/CONTINUE carrier, the loop returns on the first lyric found. If a note has no lyric (`next == null`) it is skipped, but the spanning end index (`spanEnd`) is computed only when a lyric is found. A STOP/CONTINUE carrier at index `i` correctly sets `spanEnd = i`, but a text-bearing lyric at `i` sets `spanEnd = i - 1` even if `i - 1 == anchorIndex`. That means a single-note syllable with no carriers would compute `spanEnd == anchorIndex`, and `playingNoteIndex <= anchorIndex` would have already returned `false` before entering the loop — so the edge case is harmless. However, the early-exit guard `playingNoteIndex <= anchorIndex` discards the case where the same note is both anchor and playing, which is handled higher up by `isElementPlaying(anchorIndex)`. The logic is correct but non-obvious and entirely without test coverage.

### 9B — Span / connector renderers

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| BeamGroupRenderer | `getBeamLevel`: scans a note range and returns the maximum beam level (quaver→0, semiquaver→1, demi-semiquaver→2) | unit | — | missing | Add unit test: build a Line with mixed note types, assert correct level returned |
| BeamGroupRenderer | `isNoteTypeInLevel`: determines whether a note type (or, for grace notes, its surrounding notes) qualifies at a beam recursion level | unit | — | missing | Add unit test: verify ordinary and grace-note dispatch; cover boundary conditions |
| BeamGroupRenderer | `stemTipYSsOffset`: returns stem-tip Y (in ss) from StemLayout if available, else estimates from staff position and standard stem length | unit | — | missing | Add unit test: verify both branches (with real StemLayout record and null fallback); assert exact Y arithmetic |
| BeamGroupRenderer | `getBeamHighlightColor`: evaluates selected/hovered notes against remaining-beamable-count threshold; returns selection color, preview color, or null | unit | — | missing | Add unit tests: ≥2 beamable remaining → null; selection-priority over hover; non-edit-mode → null |
| BeamGroupRenderer | `render`/`renderBeams` (pure paint dispatch) | none | — | none | No test warranted; painting delegates to drawBeam which calls Graphics2D |
| TieRenderer | `determineTieColor`: checks start-note color then end-note color; returns selection/playback color or ELEMENT_COLOR | unit | — | missing | Add unit test with mocked LineInvariants; verify start-takes-priority over end; verify fallback |
| TieRenderer | `renderTie`: reads pre-computed TieLayout and paints cubic Bezier — geometry is upstream in layout | none | — | none | No test warranted; all geometry is from TieLayout |
| TupletRenderer | `renderTupletsFromLine` — `numberOnly` branch decision: `allBeamed && isUpper` controls whether bracket arms are drawn | unit | — | missing | Add unit test (mocked LineInvariants + minimal DecorationLayout): verify numberOnly=true suppresses bracket drawing; numberOnly=false draws arms |
| TupletRenderer | Bracket X coordinate computation: `leftXSs`/`rightXSs` from `anchorXSs + decorLayout.widthSs()` with ARM_EXTENSION_SS and stem adjustments | unit | — | missing | Add unit test: build a minimal fixture, assert leftXSs/rightXSs values for both stem-up and stem-down |
| TupletRenderer | `renderTuplet` / `drawTupletNumber` (pure paint) | none | — | none | No test warranted |
| EndingRenderer | `getEffectiveEndingYSs`: translates DecorationLayout ySs → component ySs via `layoutYToComponentYSs`; throws IllegalStateException if layout absent | unit | — | missing | Add unit test: verify correct Y with layout present; verify exception when layout absent |
| EndingRenderer | `drawEnding` (pure paint with bracket lines and glyph) | none | — | none | No test warranted |
| GlissandoRenderer | `computeFarBoundsT`: bounding-box ray distance along diagonal / axis-aligned directions | unit | GlissandoRendererTest.testComputeFarBoundsTDiagonal, testComputeFarBoundsTRightward | adequate | — |
| GlissandoRenderer | `findNoteAreaEntryPoint`: inward-search for offset-area entry (circle, composite, fallback, zero-direction) | unit | GlissandoRendererTest.testFindEntryPoint_circle, testFindEntryPoint_compositeArea, testFindEntryPoint_fallback, testFindEntryPoint_zeroDirection | adequate | — |
| GlissandoRenderer | `hitTestGlissando`: local-coordinate hit test using cached geometry (diagonal, no-cache, before/after, beside, on-line, second-note) | unit | GlissandoRendererTest.testHitTestGlissando_* (6 tests) | adequate | — |
| GlissandoRenderer | Unison-glissando suppression in `renderGlissando` (`src.note().getPitch() == tgt.note().getPitch()` → early return) | unit | GlissandoRendererTest.testNonUnison*, testUnison* | inadequate | Three "unison" tests assert only on model pitch values (StaffElement.getPitch()), never invoking the renderer; the suppression branch is untested. Add a test that calls renderGlissando (or computeEndpoints indirectly) and verifies no drawing occurs for same-pitch pairs |
| GlissandoRenderer | `determineGlissandoColor`: standalone glissando selection; implied target selection for CONNECTED | unit | — | missing | Add unit tests: verify standalone glissando selection color; verify implied target-note selection; verify non-edit-mode fallback |
| GlissandoRenderer | `getGlissandoX1Ss` / `getGlissandoX2Ss`: public endpoint accessors used by HorizontalAdjustment | unit | — | missing | Covered indirectly by endpoint computation but not by name; add targeted tests confirming fallback to notehead center when endpoints null |
| GlissandoRenderer | `computeEndpoints`: full endpoint calculation including direction normalization, x1Translate clamping, length/crossing check, slide-out fixed length | unit | — | missing | `computeEndpoints` is private and exercised only through rendering; widen to package-private and add direct tests for: translate clamping; crossing rejection; slide-out length; zero-length guard |
| LyricConnectorRenderer | `drawHyphen` (count ≤ 1 → single centered hyphen; hyphen centered, Y at verse baseline) | unit | LyricConnectorRendererTest.testHyphenDrawnCenteredAtMidpoint, testDanglingHyphenDrawnCenteredInGap | adequate | — |
| LyricConnectorRenderer | `drawHyphen` (count > 1 → multiple evenly-spaced hyphens with offset): `count = floor(gap / preferred)`, `offsetSs = (gap - count*cell) / 2` | unit | — | missing | Add unit test: wide connector with gap >> 2×preferredCell; assert correct count of drawGlyphVector calls and X positions |
| LyricConnectorRenderer | `drawExtender` / `drawDanglingExtender`: stroke width, Y at verse baseline | unit | LyricConnectorRendererTest.testExtenderDrawnFromStartToEnd, testDanglingExtenderDrawnFromStartToEnd, testExtenderUsesExtenderStroke, testDistinctVersesRenderAtDistinctY | adequate | — |
| LyricConnectorRenderer | Selection color routing | unit | LyricConnectorRendererTest.testSelectedSourceElementRendersConnectorInSelectionColor | adequate | — |
| LyricConnectorRenderer | No-connectors early exit | unit | LyricConnectorRendererTest.testNoConnectorsIsNoOp | adequate | — |
| LyricTextRenderer | Single verse draw at baseline | unit | LyricTextRendererTest.testDrawsSingleBoxAtVerseBaseline | adequate | — |
| LyricTextRenderer | Multi-verse distinct baselines | unit | LyricTextRendererTest.testDrawsMultipleVersesAtDistinctBaselines | adequate | — |
| LyricTextRenderer | No-boxes no-op | unit | LyricTextRendererTest.testNoBoxesIsNoOp | adequate | — |
| LyricTextRenderer | Suppress actively-edited element | unit | LyricTextRendererTest.testSkipsActivelyEditedElementButRendersOthers | adequate | — |
| LyricTextRenderer | Selected lyric / selected note → selection color | unit | LyricTextRendererTest.testSelectedLyricPaintsInSelectionColor, testSelectedElementPaintsLyricInSelectionColor | adequate | — |
| AnnotationRenderer | Annotation baseline Y computation: `ascentSs` from FontMetrics + `layoutYToComponentYSs` with `decorationLayout.ySs()` | unit | — | missing | Add unit test with mocked LineInvariants + DecorationLayout: verify drawString receives correct x and y |
| AnnotationRenderer | Missing layout → IllegalStateException | unit | — | missing | Add unit test: element with AnnotationAttachment but no DecorationLayout → expect IllegalStateException |
| AnnotationRenderer | No attachment → no-op | none | — | none | Trivial null guard, no logic to assert |

**Notes.** The eight classes break cleanly into two groups. `LyricConnectorRenderer` and `LyricTextRenderer` are well-covered by existing unit tests — all major behaviors are exercised with concrete assertions. `GlissandoRenderer` has strong coverage of its geometry primitives (`computeFarBoundsT`, `findNoteAreaEntryPoint`, `hitTestGlissando`) but three gaps remain: the unison-suppression rendering branch is tested only at the model level (inadequate); `determineGlissandoColor`'s branching is untested; and `computeEndpoints` (the core cross-note geometry including clamping, crossing detection, and slide-out length) is private and has no direct coverage. The remaining five classes (`BeamGroupRenderer`, `TieRenderer`, `TupletRenderer`, `EndingRenderer`, `AnnotationRenderer`) have zero tests: they each contain non-trivial logic that is fully testable in isolation — `getBeamLevel`, `isNoteTypeInLevel`, `stemTipYSsOffset`, `getBeamHighlightColor`, `determineTieColor`, the `numberOnly` branch, bracket X coordinate arithmetic, `getEffectiveEndingYSs`, and annotation baseline Y calculation.

**Row count: 32 rows.** Tally: adequate 12 / missing 14 / inadequate 1 / wrong-level 0 / none 5.

**Dead code:** None. All 8 classes are imported and called from `LineRenderer.java`. Note: `BeamGroupRenderer` declares a `private static final Logger LOG` field (line 65) that is never referenced anywhere in the class body — the field and its `LoggerFactory.getLogger()` call are unused. This is a minor production observation, not a test gap.

**Production observations for the Session 9 GitHub issue:**
1. `BeamGroupRenderer` line 65: `private static final Logger LOG = LoggerFactory.getLogger(BeamGroupRenderer.class)` is declared but never invoked. Candidates for removal.
2. `EndingRenderer.getEffectiveEndingYSs` (line 157–165) throws `IllegalStateException` when no `DecorationLayout` is found for an ending. Unlike most renderers which silently skip null layouts, this hard-fail path is invisible without a test and could surface as an uncaught exception if layout invalidation races rendering.
3. `GlissandoRenderer.computeEndpoints` contains two `//noinspection ConstantValue` suppression comments around redundant null checks on `tgt` (lines 479–480, 521–522). These guard against an impossible state that the compiler cannot prove away — a structural smell worth eliminating by extracting the slide-out and connected branches into separate methods.

### 9C — Glyph / element painters

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `NoteRenderer` | `getNoteHeadGlyph(ElementType)` — map lookup, returns glyph or null | unit | — | missing | Add: all 7 note types return correct glyph; non-note type returns null |
| `NoteRenderer` | `getNoteHeadChar(ElementType)` — derives String from glyph or null | unit | — | missing | Add: null when type has no glyph, non-null for standard types |
| `NoteRenderer` | `computeBaseStemGeometry(ElementType, boolean)` — derives stemLeftX, anchorY, length by type/direction | unit | — | missing | Add: minim vs. black head, up vs. down, grace note uses separate anchor |
| `NoteRenderer.StemGeometry` | `stemTipYSs(boolean)` — tip = anchorY ∓ length | unit | — | missing | Add: up tip = anchorY - length; down tip = anchorY + length |
| `NoteRenderer` | `forEachDotPosition(note, beamed, upper, consumer)` — xAdjust branching by note type, yOffset by staff position parity | unit | — | missing | Add: semibreve/minim offsets, beamable+unbeamed+upper offsets, on-line vs. space yOffset, dotCount loop |
| `NoteRenderer` | `getLedgerLineCenterXSs(note)` — rightEdge / 2 | unit | — | missing | Add: simple arithmetic verified against known notehead width |
| `NoteRenderer` | `getLedgerLineWidthSs(note, extensionSs)` — rightEdge + 2×extension | unit | — | missing | Add: verify additive formula |
| `NoteRenderer` | Accidental bounds (via `NoteGeometry.getAccidentalBoundsSs`) — null for grace/no accidental; sensible extents per type; widens when parenthesized | unit | `NoteRendererTest.*` (6 tests) | inadequate | Tests are correct and can fail, but they test `NoteGeometry` not `NoteRenderer` — name mismatch (class should be `NoteGeometryTest` or tests should be moved); also assertions are directional-only (`isNegative`/`isPositive`/`isGreater`) with no expected values from independent calculation |
| `NoteRenderer` | Pure painting (render, renderNoteHead, renderStem, renderFlags, renderDots, renderLedgerLines, renderAccidental, renderBreathMark) | none | — | none | — |
| `RestRenderer` | `getRestGlyph(ElementType)` — map lookup | unit | — | missing | Add: each rest type maps to correct glyph; non-rest returns null |
| `RestRenderer` | `calculateRestYSs(note, middleLineYSs)` — branching by SEMIBREVE_REST / MINIM_REST / other | unit | — | missing | Add: all three branches with exact expected Y offsets |
| `RestRenderer` | Pure painting (render, renderDots) | none | — | none | — |
| `ClefRenderer` | `render` — `baseline = middleLineYSs + 1.0`, no branching | none | — | none | Trivial single-expression positioning; geometry is a named constant offset |
| `KeySignatureRenderer` | `render` no-op when `hasAccidentals()` is false | unit | `KeySignatureRendererTest.testRenderIsNoOpForCMajor` | adequate | — |
| `KeySignatureRenderer` | `render` draw loop — correct staff positions for flats (BEADGCF order) and sharps (FCGDAEB order), accidentalCount iterations | unit | — | missing | Add: verify FLAT/SHARP_STAFF_POSITIONS arrays encode correct staff positions for 1–7 accidentals |
| `KeySignatureRenderer` | `renderKeyChange` — 4 branches: same type adding, same type removing (naturals for removed), different type (naturals then new key), identical keys (no-op) | unit | — | missing | Add: each branch; verify correct keyType arrays, accidentalCounts, startingOffsets, isNaturals flags |
| `KeySignatureRenderer` | `getGlyphForKeyType` — switch on FLATS/SHARPS/default throws | unit | — | missing | Add: FLATS → FLAT glyph, SHARPS → SHARP glyph, NONE throws |
| `KeySignatureRenderer` | Pure painting (drawString calls in render/renderKeySignatureChange) | none | — | none | — |
| `BarRenderer` | `renderBarLineOrRepeat` — switch on 6 barline/repeat types selects correct drawing primitives | unit | — | missing | Add: verify each case (SINGLE, DOUBLE, FINAL_DOUBLE, REPEAT_LEFT, REPEAT_RIGHT, REPEAT_LEFT_RIGHT) calls the right draw helpers |
| `BarRenderer` | `drawRightRepeat` — returns x after thick bar; accumulates dots-advance + sep + thin + sep + thick | unit | — | missing | Add: verify returned x is correct |
| `BarRenderer` | Pure painting (drawBar, drawRepeatDots, resolveBarXSs) | none | — | none | — |
| `ArticulationRenderer` | `render` — combo detection (hasStaccato && hasAccent → ACCENT_STACCATO glyph); solo staccato → STACCATO; solo accent → ACCENT | unit | — | missing | Add: three combinations; verify correct glyph selected via layout-position path |
| `ArticulationRenderer` | Pure painting (drawBravuraGlyph calls) | none | — | none | — |
| `FermataRenderer` | `render` — guard (no FermataAttachment → no-op); layout lookup; delegates to drawBravuraGlyph | none | — | none | The only logic is a null guard; placement is entirely delegated to `NoteAttachedStacker` and `RenderingUtils` — no computable geometry owned here |
| `TrillRenderer` | `drawWavyLine` — segment count = `max(1, round(length / WIGGLE_SEGMENT_WIDTH_SS))`; scale = length/segWidth/segments | unit | — | missing | Add: zero/negative length no-op; normal length computes correct segment count; rounding edge case |
| `TrillRenderer` | `renderTrillAtPosition` — branches on endNote != null && endNote != anchor | unit | — | missing | Add: single-note trill (NaN endX); multi-note trill (endX = endNote X + noteheadWidth) |
| `TrillRenderer` | Pure painting (renderTrill, drawString) | none | — | none | — |
| `BeatChangeRenderer` | `render` — null guard on attachment; delegates to `drawDurationEquals` + `drawDurationGlyph` | none | — | none | All branching logic lives in `MetronomeRenderer` base methods |
| `MetronomeRenderer` | `requireMetronomeGlyph(ElementType)` — 6-way mapping + throws on unmapped type | unit | — | missing | Add: each note type maps to correct SMuFL glyph; unmapped type throws RuntimeError |
| `MetronomeRenderer` | `drawDurationEquals` — advances xSs by glyph advance + dotAdvance (×2 if dotted) + equals string width | unit | — | missing | Add: dotted and non-dotted duration; verify returned xSs accounts for all advances |
| `MetronomeRenderer` | `drawDurationGlyph` — draws glyph + optional dot | none | — | none | Pure painting delegating to already-tested geometry |
| `TempoChangeRenderer` | `renderTempoChange` — `shouldShowTempo` branch: with tempo shows "visibleTempo + space + description + glyph"; without shows description only | unit | — | missing | Add: verify StringBuilder contents for showTempo=true vs false |
| `TempoChangeRenderer` | Pure painting (drawString) | none | — | none | — |
| `DynamicMarkingRenderer` | `render` — null guard on attachment; `glyph = dynamicType.getGlyph()` (null → return) | none | — | none | Glyph selection is an enum property on `DynamicAttachment.DynamicType`, already verified there; renderer itself has no logic |
| `DynamicMarkingRenderer` | Pure painting | none | — | none | — |
| `DynamicsRenderer` | `renderSingleHairpin` — type branch: crescendo → two lines from left-middle to right-top/bottom; diminuendo → two lines from left-top/bottom to right-middle | unit | — | missing | Add: verify line endpoints differ between crescendo and diminuendo (could test via a recording Graphics2D or by extracting coordinate logic to a pure method) |
| `DynamicsRenderer` | Pure painting (g2.draw calls) | none | — | none | — |

**Notes.**

The audit covers 13 production classes. Across the 37 rows above (23 testable, 14 `none`), the tally is:

- **adequate**: 1  
- **inadequate**: 1 (`NoteRendererTest` — name mismatch: class is `NoteRendererTest` but all tests call `NoteGeometry` methods, not `NoteRenderer`; assertions use directional-only comparisons)  
- **missing**: 21  
- **none**: 14  

**Dead code:** No dead code identified. Every method has either a rendering call path, a utility call from collaborator classes (`computeBaseStemGeometry` is called by `GlissandoRenderer`), or a public API used from `LineComponent`/`FughettaRenderer` equivalents. `FermataRenderer.renderFermata` is a thin forwarding wrapper — its usages should be confirmed if removal is considered.

**Production observations for the Session 9 GitHub issue:**

1. **`NoteRendererTest` is misclassified** — all six tests in `NoteRendererTest` exercise `NoteGeometry.getAccidentalBoundsSs`, not `NoteRenderer` itself. The class has no import for `NoteRenderer`. This file should be renamed `NoteGeometryTest` and moved to `src/test/…/layout/` alongside the other `NoteGeometry` tests (or absorbed into a future `NoteGeometryTest`). The tests themselves are sound.

2. **`KeySignatureRenderer.renderKeyChange` "adding accidentals" branch is ambiguous** — when `nextLine.count > line.count`, the code sets `accidentalCounts[0] = nextLine.getKeyAccidentalCount()` (the full new count) but the comment says "just show the new ones." It is unclear whether this draws the full new key signature starting at the beginning (rendering all accidentals) or only the incremental ones. A unit test of this branch is the only way to confirm the intent is correctly implemented; the absence of one leaves this ambiguous.

3. **`DynamicsRenderer.renderSingleHairpin` lacks a pure-function extraction** — the hairpin line-endpoint logic (two `Line2D.Double` constructions that differ only in which corners go to the apex) is directly inside a method that also sets stroke and color. If the line construction ever regresses, the only way to catch it is to mock `Graphics2D.draw()` and inspect the shapes passed — awkward. Extracting endpoint computation to a package-private method would make it trivially unit-testable.

(37 rows: 1 adequate / 1 inadequate / 21 missing / 14 none)

### §9 summary

**115 behavior rows: 87 testable / 28 `none`; of the 87 testable, 34 adequate ·
48 missing · 4 inadequate · 1 redundant · 0 wrong-level (~60% dark).** Zero
genuine e2e escalations in the entire package — every testable behavior is
`unit`, consistent with the rubric (renderers either paint or compute; the
integration risk lives upstream in `layout`/`ui/component`).

**The rubric's "pure painting → `none`" prediction held but was narrower than
expected.** The 28 `none` rows concentrate in 9C glyph painters (14/37), yet the
audit's defining finding is that substantial *computed* logic hides inside
classes named like painters and is almost entirely untested: glyph-selection
maps, staff-position arithmetic, barline-type switches, and duration-advance
math that the rubric does **not** excuse as paint.

**Darkest zone — 9C glyph painters (only 1 of 23 testable rows adequate).**
Untested computation spans `NoteRenderer` (stem/dot/ledger geometry helpers,
`computeBaseStemGeometry`, `forEachDotPosition`), `KeySignatureRenderer`
(flat/sharp staff-position arrays + the 4-branch `renderKeyChange`),
`BarRenderer` (6-way barline/repeat-type switch + `drawRightRepeat` advance),
`MetronomeRenderer`/`TempoChangeRenderer` (glyph mapping, dotted-duration
advance arithmetic, tempo-string assembly), and `RestRenderer`/
`ArticulationRenderer`/`TrillRenderer`/`DynamicsRenderer` (rest-Y branch,
combo-articulation glyph selection, wavy-line segment count, hairpin endpoints).

**9B span / connector renderers — every cross-element geometry helper dark:**
`BeamGroupRenderer` (`getBeamLevel`, `stemTipYSsOffset`, `getBeamHighlightColor`),
`TupletRenderer` bracket-X arithmetic + `numberOnly` branch, `EndingRenderer`
`getEffectiveEndingYSs`, `TieRenderer.determineTieColor`, `AnnotationRenderer`
baseline-Y. Bright spots: the two lyric renderers (`LyricConnectorRenderer`,
`LyricTextRenderer`) are well-covered with falsifiable assertions, and
`GlissandoRenderer`'s geometry primitives (`computeFarBoundsT`,
`findNoteAreaEntryPoint`, `hitTestGlissando`) are adequate.

**9A infrastructure — strongest existing coverage in the package**
(`LineInvariants.getElementColor` color-resolution matrix, `NoteAreaBuilder`
cache hit/invalidation matrix, `RenderingUtils.getDecorationColor`). The single
riskiest dark path in §9 is `LineInvariants.isLyricSpanPlaying()` — five exit
points, feeding two other untested color methods (`getLyricColor`,
`getLyricConnectorColor`). `GraphicsState.save/close` restore contract is also
untested.

**inadequate (4):** (1) `GlissandoRenderer` unison-suppression tests assert on
model `getPitch()` and never invoke the renderer's early-return branch (9B);
(2,3) two `NoteAreaBuilder.buildNoteArea` tests assert only `isEmpty()==false`
with no geometry (9A); (4) `NoteRendererTest` is a **name mismatch** — all six
tests exercise `NoteGeometry`, not `NoteRenderer`, with directional-only
(`isNegative`/`isPositive`) assertions and no independently-computed expected
values (9C). **redundant (1):** the `NoteAreaBuilder` `getLedgerLineCount` trio
tests `StaffElement.getLedgerLineCount()` (9A).

**Cross-session attribution (for remediation, not new rows here):**
`NoteRendererTest` belongs in `layout` (`NoteGeometry`, Session 3) when rewritten;
the `getLedgerLineCount` trio belongs in `StaffElementTest` (`dom`, Session 1).

**No dead classes** (all 29 actively used by `LineRenderer`). One unused symbol:
`BeamGroupRenderer`'s `LOG` field is declared but never invoked.

### §9 production observations (filed as GitHub issue #414)

1. **`GraphicsState.close()` asymmetric null guard.** `CLIP` is restored
   unconditionally while `COLOR`/`STROKE`/`FONT`/`TRANSFORM`/hints guard on
   `!= null`. Harmless with real `Graphics2D` (those getters never return null)
   but a custom/stub `Graphics2D` could silently skip restoration. Normalize or
   comment.
2. **`NoteAreaBuilder.addAccidentalToArea()` uniform accidental height.** Uses
   the sharp bbox height for all accidentals; a double-flat is taller, so the
   composite note area can understate the visual footprint, potentially letting
   a glissando endpoint land too close. Documented as an approximation but no
   follow-up exists.
3. **`EndingRenderer.getEffectiveEndingYSs()` hard-fails on missing layout.**
   Throws `IllegalStateException` when no `DecorationLayout` is found, diverging
   from every peer span renderer (which silently skip null layouts) — an
   uncaught-exception risk if layout invalidation races rendering.
4. **`GlissandoRenderer.computeEndpoints()` structural smell.** Two
   `//noinspection ConstantValue` suppressions around redundant `tgt` null guards
   (the compiler can't prove non-null inside the `!isSlideOut` branch). Splitting
   into distinct slide-out vs. connected branches would eliminate them.
5. **`KeySignatureRenderer.renderKeyChange` "adding accidentals" comment
   contradicts the code.** When `nextLine.count > line.count` the comment says
   "just show the new ones" but `accidentalCounts[0]` is set to the full new
   count. Intent (full redraw vs. delta) is ambiguous and untested.
6. **`DynamicsRenderer.renderSingleHairpin` endpoint logic is not extractable.**
   The crescendo/diminuendo `Line2D.Double` corner selection lives inside a
   method that also sets stroke/color, so the branch can only be observed by
   mocking `Graphics2D.draw()`. Extract endpoint computation to a package-private
   method.
7. **`BeamGroupRenderer` unused `LOG` field** (declared, never invoked) —
   candidate for removal.

