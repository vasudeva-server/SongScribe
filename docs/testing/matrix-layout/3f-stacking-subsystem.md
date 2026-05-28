### 3F. stacking subsystem — `NoteAttachedStacker`, `StackingContext`, `StackingUtils`, `StructuralStacker`, `SystemStacker`, `VerticalStackingCalculator`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| StackingUtils | `anchorCeilingSs(int)` within/below staff → top staff line | unit | — | missing | assert `STAFF_TOP_Y_SS` for sp > TOP_STAFF_LINE | ✅ |
| StackingUtils | `anchorCeilingSs(int)` at/above top line → above notehead | unit | — | missing | assert `sp*OFFSET - NOTE_HEAD_RADIUS_SS` for sp ≤ -4 | ✅ |
| StackingUtils | `stackAbove` collision-aware placement (query-expand-min-reserve) | unit | `ArticulationStackingTest` (integration) | adequate | keep | — |
| StackingUtils | `stackAboveWithRegions` multi-region min-ceiling + per-region reserve | unit | `SystemTierStackingTest` (`ySs<0` only) | inadequate | exact-value via controlled extents; `<0` can't catch region/reservation bug | ✅ |
| StackingUtils | `isRangeCovered(start,end)` | unit | — | missing | covered / uncovered / wrong-end | ✅ |
| StackingUtils | symmetric horizontal margin (`STRUCTURAL_HORIZONTAL_MARGIN_SS`) on query+reserve | unit | margin never checked | missing | assert margin applied to queryX/queryWidth | ✅ |
| StackingContext | `buildColumnMap` element→column | unit | — | missing | 2 columns → map per element | ✅ |
| StackingContext | `updateLowestNoteBotSs` max-accumulation | unit | — | missing | ascending then descending → max kept | ✅ |
| StackingContext | `updateBotContentExtentSs` max-accumulation | unit | — | missing | same pattern | ✅ |
| StackingContext | `notesWithUpwardTie` default empty / setter replaces | unit | — | missing (low priority) | drives downstream margin branch | ✅ |
| NoteAttachedStacker | `computeNoteBounds` stem-path vs type-geometry path | unit | indirect (`ArticulationStackingTest`, `<0` only) | inadequate | both paths; exact top/bot Ss | ✅ |
| NoteAttachedStacker | `seedNoteBounds` updates `lowestNoteBotSs`/`botContentExtentSs` | unit | — | missing | assert context fields after seeding | ✅ |
| NoteAttachedStacker | `seedTieBounds` upward arc → above; membership in `notesWithUpwardTie` only when protruding | unit | — | missing | controlled TieLayout; set membership + extents | ✅ |
| NoteAttachedStacker | `seedTieBounds` downward arc → below; `botContentExtentSs` updated | unit | — | missing | stem-up tie | ✅ |
| NoteAttachedStacker | `evaluateBezierYSs` cubic at t=0/0.5/1 | unit | — | missing | hand-computed control points | ✅ |
| NoteAttachedStacker | reduced `TIE_DECORATION_MARGIN_SS` for upward-tie notes | unit | — | missing | articulation Y delta == margin delta | ✅ |
| NoteAttachedStacker | `stackArticulations` precomposed staccato+accent; single glyphs; collision stacking | unit | `ArticulationStackingTest.PrecomposedGlyph`, `CollisionDetection.testAboveStaffArticulationsReserveSpaceInExtents` | adequate | keep | — |
| NoteAttachedStacker | `stackFermata` above articulations (ordering) | unit | `FermataTrillStackingTest.testFermataPositionedAboveArticulations` | adequate | keep | — |
| NoteAttachedStacker | `stackFermata` exact Y = `ceiling - margin - height` | unit | `FermataTrillStackingTest` (`ySs<0`) | inadequate | exact-value from controlled extents | ✅ |
| NoteAttachedStacker | `stackSingleTrill` single-note → `endXSs=anchorXSs` | unit | `FermataTrillStackingTest.testSingleNoteTrillPositionedAboveNote` (`ySs<0`, no width) | inadequate | add exact single-note width assertion | ✅ |
| NoteAttachedStacker | `stackSingleTrill` multi-note → spans anchor→end | unit | `FermataTrillStackingTest.testMultiNoteTrillReservesFullSpan` | adequate | keep | — |
| NoteAttachedStacker | `computePreviewDecorationLayouts` (static preview path) | unit | — | missing | fermata+staccato preview all above-staff | ✅ |
| StructuralStacker | `stackSpanElement` null anchor/end → skipped | unit | — | missing | null anchor → no layout | ✅ |
| StructuralStacker | `stackHairpins` crescendo/diminuendo above note-attached | unit | `StructuralTierStackingTest.HairpinStacking.*` (`ySs<0`) | inadequate | exact-value; `<0` passes at y=-0.001; consolidate redundant `…ProducesDecorationLayout` | ✅ |
| StructuralStacker | `stackTuplets` above note-attached | unit | `StructuralTierStackingTest.TupletStacking.testTupletRangeElementPositionedAboveStaff` (`ySs<0`) | inadequate | exact-value | ✅ |
| StructuralStacker | `stackTextDynamics` X centering = `noteheadCenterX - contentWidth/2` | unit | `StructuralTierStackingTest` (`ySs<0`, no X) | missing | assert centered `xSs` | ✅ |
| StructuralStacker | `stackEndings` above hairpins (ordering); `heightSs`=`VOLTA_TICK_HEIGHT_SS` | unit | `StructuralTierStackingTest.EndingStacking.testEndingPositionedAboveHairpins`, `testEndingHasPositiveDimensions` | adequate | keep | — |
| StructuralStacker | `testNonOverlappingHairpinsAtSameHeight` | unit | `StructuralTierStackingTest` (only `ySs<0` each, never compared) | inadequate | **name-mismatch**: add `isCloseTo` equality or rename | ✅ |
| SystemStacker | `stackAnnotations` X shifts with `xAlignment` (0/0.5/1) | unit | `SystemTierStackingTest` (`ySs<0`, no X) | missing | left/center/right → distinct formula-driven X | ✅ |
| SystemStacker | `stackMetronomeAttachment` (tempo/beat-change) regions placement | unit | `SystemTierStackingTest` (`ySs<0`, dims `>0`, cross-tier `isLessThan`) | inadequate | exact-value for ≥1 region case (cross-tier ordering adequate) | ✅ |
| SystemStacker | `testTempoAttachmentProducesLayout` | unit | `SystemTierStackingTest` (`isNotNull` only) | inadequate | fixture-only; merge with positioned test or add position/dim | ✅ |
| VerticalStackingCalculator | `seedAccidentalsIntoStructural` high-note top reservation | unit | `VerticalStackingCalculatorTest.testSeedAccidentalsTranslatesToStaffCoordinatesForHighNote` (exact) | adequate | keep | — |
| VerticalStackingCalculator | accidental bottom reservation | unit | `…testSeedAccidentalsReservesSpaceAtAccidentalXForSharp` (`>=`) | inadequate | `>=` allows any value; change to exact `isCloseTo(botSs+centerYSs)` | ✅ |
| VerticalStackingCalculator | grace note skipped | unit | `…testSeedAccidentalsIgnoresGraceNotes` (exact 0.0) | adequate | keep | — |
| VerticalStackingCalculator | `applyDecorationOffsets` Tuplet `getVerticalPositionSs` | unit | `ManualOffsetStackingTest` covers Trill/Ending/Hairpin/TempoChange/Fermata/Annotation; **Tuplet absent** | missing | add `TupletOffsets` test | ✅ |
| VerticalStackingCalculator | `calculate` tier copy propagation (`copyTopFrom`) | unit | `StaffExtentsTest.CopyTopFrom` (primitive) + integration | adequate | keep | — |
| VerticalStackingCalculator | `calculate` `aboveStaffSs` = `max(MIN_ABOVE_STAFF_SS, -topExtent - STAFF_HALF_SS)` | unit | `LineHeightTest` (`>=`) | inadequate | exact-value with known decoration | ✅ |
| VerticalStackingCalculator | `calculate` `belowStaffSs` max across 4 terms | unit | `LineHeightTest` (`>=`) | inadequate | pin exact `lineHeightSs` for below-staff note | ✅ |
| VerticalStackingCalculator | `calculate` `belowContentSs` distinct, uses `botContentExtentSs` | unit | — | missing | downward-stem note → non-zero belowContent | ✅ |
| VerticalStackingCalculator | `calculate` empty line → MIN above/below | unit | `LineHeightTest.testEmptyLineZeroReturnsMinimumHeight` (exact) | adequate | keep | — |

**3F notes (quality concerns):** **Systemic weak-assertion pattern (highest risk).** Most behaviors in `NoteAttachedStacker`/`StructuralStacker`/`SystemStacker` are covered only by `isLessThan(0.0)`/`isGreaterThan(0.0)` — they pass for any negative/positive value and cannot catch sign errors in the ceiling formula, wrong margin application, bad extents import, or off-by-a-constant bugs; a `-marginSs`→`+marginSs` mutation in `stackAbove` would survive every such test. The pattern pervades `FermataTrillStackingTest`, `StructuralTierStackingTest`, `SystemTierStackingTest`. **Name-mismatch:** `testNonOverlappingHairpinsAtSameHeight` asserts nothing about equal height (only `ySs<0` each). Weak disjunction: `testFermataAndTrillDoNotOverlap` OR-asserts two booleans (low diagnostic value). Fixture-only: `testTempoAttachmentProducesLayout` asserts `isNotNull` only. Redundant: crescendo/diminuendo/fermata `…ProducesDecorationLayout` duplicate the positioned tests. **Entirely uncovered:** `StackingUtils.anchorCeilingSs` (both branches), `isRangeCovered`, `NoteAttachedStacker.evaluateBezierYSs` (pure math), the tie-seeding paths + reduced-margin branch, `VerticalStackingCalculator.belowContentSs`, the Tuplet manual offset, `SystemStacker.stackAnnotations` X-alignment arithmetic, `computePreviewDecorationLayouts`. `VerticalStackingCalculatorTest` is the model to follow — it uses exact `isEqualTo` (except the one `>=` bottom-reservation assertion). `StaffExtents` (out of 3F scope) is in sound shape with exact assertions.

