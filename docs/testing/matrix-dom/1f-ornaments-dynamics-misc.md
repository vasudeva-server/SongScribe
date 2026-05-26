### 1F. ornaments / dynamics / misc — `Articulation`, `ArticulationType`, `Hairpin`, `Trill`, `Tuplet`, `Crescendo`, `Diminuendo`, `Lyric`, `Attribution`, `EndingValidationResult`, `CollisionRegion`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| Articulation | ctor wires owner/parent/line | unit | `ParentLinePropagationTest` | adequate | keep | — |
| Articulation | `getContentWidth/HeightSs()` branch on `isStaccato()` → correct bbox | unit | `ArticulationStackingTest.PrecomposedGlyph` | adequate | keep | — |
| Articulation | `getContentWidth/HeightPx()` via ssToPx | unit | `ArticulationTest` | adequate | written | ✅ |
| ArticulationType | `getMidiDurationPercent()` (STACCATO=33, ACCENT=−1) | unit | `ArticulationTypeTest.GetMidiDurationPercent` | adequate | written | ✅ |
| ArticulationType | `hasMidiDurationOverride()` (STACCATO true, ACCENT false) | unit | `ArticulationTypeTest.HasMidiDurationOverride` | adequate | written | ✅ |
| ArticulationType | `getDrawingOrder(false)` ascending order | unit | `ArticulationTypeTest.GetDrawingOrderStemDown` | adequate | written | ✅ |
| ArticulationType | `getDrawingOrder(true)` reversed order | unit | `ArticulationTypeTest.GetDrawingOrderStemUp` | adequate | written | ✅ |
| Hairpin | `getContentHeightSs()` → HAIRPIN_OPENING_HEIGHT_SS | unit | `HairpinTest.testGetContentHeightSsEqualsHairpinOpeningHeightSs` | adequate | written | ✅ |
| Hairpin | `getSpanWidthSs()` — `max(opening, endX−anchorX+NOTE_HEAD_WIDTH)` | unit | `HairpinTest.GetSpanWidthSs` | adequate | written | ✅ |
| Hairpin | `x1ShiftSs`/`x2ShiftSs`/`yShiftSs` stored/retrieved | unit | `ManualOffsetStackingTest.HairpinOffsets` (2) | adequate | keep | — |
| Trill | `getSpanWidthSs()` — `max(glyphWidth, endX−anchorX+glyphWidth)` | unit | `TrillTest.GetSpanWidthSs` | adequate | written | ✅ |
| Trill | `getContentWidth/HeightSs()` SMuFL bbox for ORNAMENT_TRILL | unit | `TrillTest.testGetContentWidth/HeightSsEqualsOrnamentTrillBbox*` | adequate | written | ✅ |
| Trill | `getContentWidth/HeightPx()` via ssToPx | unit | `TrillTest.GetContentPx` | adequate | written | ✅ |
| Trill | single-note ctor sets anchor==end | unit | `FermataTrillStackingTest.testSingleNoteTrillPositionedAboveNote` | adequate | keep | — |
| Trill | `yPositionSs` stored + applied | unit | `ManualOffsetStackingTest.TrillOffsets.testTrillYPositionApplied` | adequate | keep | — |
| Tuplet | `getElementCount()` returns `grade` | unit | `TupletTest.testGetElementCountReturnsTripletGrade` + `testGetElementCountReturnsQuintupletGrade` | adequate | written | ✅ |
| Tuplet | `getSpanWidthSs()` — `max(1.0, endX−anchorX)` | unit | `TupletTest.GetSpanWidthSs` | adequate | written | ✅ |
| Tuplet | `getContentHeightSs()` → TUPLET_BRACKET_HEIGHT_SS | unit | `TupletTest.testContentHeightSsMatchesStylesheetConstant` | adequate | keep | — |
| Tuplet | `getContentHeightPx()` via ssToPx | unit | `TupletTest.testContentHeightPxIsToPixelsOfSs` | adequate | keep | — |
| Crescendo | pass-through ctor to Hairpin | none | — | none | trivial delegation | — |
| Diminuendo | pass-through ctor to Hairpin | none | — | none | trivial delegation | — |
| Lyric | ctor rejects null syllabic on non-carrier | unit | `LyricTest.testInvariantRejectsNullSyllabicOnTextLyric` | adequate | keep | — |
| Lyric | ctor rejects non-null syllabic on carrier (STOP) | unit | `LyricTest.testInvariantRejectsCarrierWithSyllabic` | adequate | keep | — |
| Lyric | ctor rejects compound=true on carrier | unit | `LyricTest.testInvariantRejectsCompoundOnContinueCarrier` | adequate | written | ✅ |
| Lyric | record equality/hash across syllabic + compound | unit | `LyricTest` (2) | adequate | keep | — |
| Lyric | carrier (STOP) has syllabic null | unit | `LyricTest.testCarrierLyricWithNullSyllabicEqualsItself` | adequate | keep | — |
| Attribution | ctor sets ATTRIBUTION_MARGIN_BOTTOM_SS | unit | `AttributionTest.testCtorSetsAttributionMarginBottomSs` | adequate | written | ✅ |
| Attribution | `computeContentWidthSs(font)` via textWidthSs | unit | `AttributionTest.testComputeContentWidthSsUsesStringWidth` | adequate | keep | — |
| Attribution | `computeContentHeightSs(font)` via textHeightSs | unit | `AttributionTest.testComputeContentHeightSsUsesFontMetrics` | adequate | keep | — |
| Attribution | `getContentWidth/HeightSs/Px()` all throw UnsupportedOperationException | unit | `AttributionTest.testGetContentDimensionsThrowUnsupportedOperationException` | adequate | written | ✅ |
| Attribution | `isRightAligned` default true + round-trip | unit | `AttributionTest.testIsRightAlignedDefaultTrueAndRoundTrip` | adequate | written | ✅ |
| EndingValidationResult | `invalid()` → isValid false | unit | `EndingValidationResultTest.testInvalidReturnsIsValidFalse` | adequate | written | ✅ |
| EndingValidationResult | `valid(action, start, end)` → isValid true + accessors | unit | `EndingValidationResultTest.testValidReturnsIsValidTrueAndAccessorsMatchInputs` | adequate | written | ✅ |
| CollisionRegion | pure data record | none | — | none | trivial record | — |

**1F notes (quality concerns):** `ArticulationType` MIDI + drawing-order logic is used in production (`StaffElement`) but entirely untested at unit level. `Tuplet.getElementCount()` and the `getSpanWidthSs` clamps across `Hairpin`/`Trill`/`Tuplet` are exercised only obliquely through wide-span stacking tests — narrow-span branches are dead from a test perspective. `EndingValidationResult` is used only as a fixture; its accessors are never asserted, and the production-emitted `EXTEND_SPAN` action is untested. `Attribution`'s four `UnsupportedOperationException` guards have no safety net.

