### 1F. ornaments / dynamics / misc — `Articulation`, `ArticulationType`, `Hairpin`, `Trill`, `Tuplet`, `Crescendo`, `Diminuendo`, `Lyric`, `Attribution`, `EndingValidationResult`, `CollisionRegion`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| Articulation | ctor wires owner/parent/line | unit | `ParentLinePropagationTest` | adequate | keep | — |
| Articulation | `getContentWidth/HeightSs()` branch on `isStaccato()` → correct bbox | unit | `ArticulationStackingTest.PrecomposedGlyph` | adequate | keep | — |
| Articulation | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write test (both types) | ⬜ |
| ArticulationType | `getMidiDurationPercent()` (STACCATO=33, ACCENT=−1) | unit | — | missing | write test per constant | ⬜ |
| ArticulationType | `hasMidiDurationOverride()` (STACCATO true, ACCENT false) | unit | — | missing | write test | ⬜ |
| ArticulationType | `getDrawingOrder(false)` ascending order | unit | — | missing | write test | ⬜ |
| ArticulationType | `getDrawingOrder(true)` reversed order | unit | — | missing | write test | ⬜ |
| Hairpin | `getContentHeightSs()` → HAIRPIN_OPENING_HEIGHT_SS | unit | — | missing | write test | ⬜ |
| Hairpin | `getSpanWidthSs()` — `max(opening, endX−anchorX+NOTE_HEAD_WIDTH)` | unit | `ManualOffsetStackingTest.HairpinOffsets` (offsets, not formula) | inadequate | write both-branch test | ⬜ |
| Hairpin | `x1ShiftSs`/`x2ShiftSs`/`yShiftSs` stored/retrieved | unit | `ManualOffsetStackingTest.HairpinOffsets` (2) | adequate | keep | — |
| Trill | `getSpanWidthSs()` — `max(glyphWidth, endX−anchorX+glyphWidth)` | unit | `FermataTrillStackingTest` (`>span`, not formula) | inadequate | write both-branch exact-value test | ⬜ |
| Trill | `getContentWidth/HeightSs()` SMuFL bbox for ORNAMENT_TRILL | unit | `FermataTrillStackingTest.testTrillHasPositiveDimensions` (`>0`) | inadequate | write exact-bbox test | ⬜ |
| Trill | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write test | ⬜ |
| Trill | single-note ctor sets anchor==end | unit | `FermataTrillStackingTest.testSingleNoteTrillPositionedAboveNote` | adequate | keep | — |
| Trill | `yPositionSs` stored + applied | unit | `ManualOffsetStackingTest.TrillOffsets.testTrillYPositionApplied` | adequate | keep | — |
| Tuplet | `getElementCount()` returns `grade` | unit | (tests assert `getGrade()`, never `getElementCount()`) | missing | write test (grade 3 and 5) | ⬜ |
| Tuplet | `getSpanWidthSs()` — `max(1.0, endX−anchorX)` | unit | — | missing | write both-branch test | ⬜ |
| Tuplet | `getContentHeightSs()` → TUPLET_BRACKET_HEIGHT_SS | unit | `TupletTest.testContentHeightSsMatchesStylesheetConstant` | adequate | keep | — |
| Tuplet | `getContentHeightPx()` via ssToPx | unit | `TupletTest.testContentHeightPxIsToPixelsOfSs` | adequate | keep | — |
| Crescendo | pass-through ctor to Hairpin | none | — | none | trivial delegation | — |
| Diminuendo | pass-through ctor to Hairpin | none | — | none | trivial delegation | — |
| Lyric | ctor rejects null syllabic on non-carrier | unit | `LyricTest.testInvariantRejectsNullSyllabicOnTextLyric` | adequate | keep | — |
| Lyric | ctor rejects non-null syllabic on carrier (STOP) | unit | `LyricTest.testInvariantRejectsCarrierWithSyllabic` | adequate | keep | — |
| Lyric | ctor rejects compound=true on carrier | unit | — | missing | write test w/ Extend.CONTINUE + compound (covers CONTINUE branch) | ⬜ |
| Lyric | record equality/hash across syllabic + compound | unit | `LyricTest` (2) | adequate | keep | — |
| Lyric | carrier (STOP) has syllabic null | unit | `LyricTest.testCarrierLyricWithNullSyllabicEqualsItself` | adequate | keep | — |
| Attribution | ctor sets ATTRIBUTION_MARGIN_BOTTOM_SS | unit | — | missing | write test | ⬜ |
| Attribution | `computeContentWidthSs(font)` via textWidthSs | unit | `AttributionTest.testComputeContentWidthSsUsesStringWidth` | adequate | keep | — |
| Attribution | `computeContentHeightSs(font)` via textHeightSs | unit | `AttributionTest.testComputeContentHeightSsUsesFontMetrics` | adequate | keep | — |
| Attribution | `getContentWidth/HeightSs/Px()` all throw UnsupportedOperationException | unit | — | missing | write test (all 4 throw) | ⬜ |
| Attribution | `isRightAligned` default true + round-trip | unit | — | missing | write test | ⬜ |
| EndingValidationResult | `invalid()` → isValid false | unit | `MusicEditOperationsMutationTest` (indirect) | inadequate | write direct test | ⬜ |
| EndingValidationResult | `valid(action, start, end)` → isValid true + accessors | unit | `MusicEditOperationsMutationTest`/`ScoreViewControllerCommandHandlerTest` (fixture only) | inadequate | write test asserting all 3 accessors per PrecedingAction (NONE/INSERT_BARLINE/EXTEND_SPAN) | ⬜ |
| CollisionRegion | pure data record | none | — | none | trivial record | — |

**1F notes (quality concerns):** `ArticulationType` MIDI + drawing-order logic is used in production (`StaffElement`) but entirely untested at unit level. `Tuplet.getElementCount()` and the `getSpanWidthSs` clamps across `Hairpin`/`Trill`/`Tuplet` are exercised only obliquely through wide-span stacking tests — narrow-span branches are dead from a test perspective. `EndingValidationResult` is used only as a fixture; its accessors are never asserted, and the production-emitted `EXTEND_SPAN` action is untested. `Attribution`'s four `UnsupportedOperationException` guards have no safety net.

