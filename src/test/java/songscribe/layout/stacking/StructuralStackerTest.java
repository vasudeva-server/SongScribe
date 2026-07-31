/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.layout.stacking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;

/**
 * Unit tests for {@link StructuralStacker}'s pure tuplet-slope and rest-skipping helpers.
 * <p>
 * Coordinate convention under test: layout Y is up-negative, so a smaller (more negative)
 * {@code getAbsoluteTopYSs()} means a physically higher note.
 */
class StructuralStackerTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;
    private static final double ANCHOR_X_SS = 0.0;
    private static final double WIDTH_SS = 4.0;

    /** No column carries an above-staff articulation, so each column is governed solely by its tip. */
    private static final Function<ElementColumn, List<StructuralStacker.ScriptObstacle>> NO_ARTICULATIONS =
        column -> List.of();

    private static ElementColumn mockColumn(double xSs, double absoluteTopYSs, int staffPosition) {
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(staffPosition);

        var column = mock(ElementColumn.class);
        when(column.getElement()).thenReturn(element);
        when(column.getXSs()).thenReturn(xSs);
        when(column.getAbsoluteTopYSs()).thenReturn(absoluteTopYSs);
        // Bound-edge geometry reads the column's own head width, as a real column reports it.
        when(column.getNoteheadWidthSs()).thenReturn(element.getType().getElementWidthSs());

        return column;
    }

    // ======================================================================
    // computeTupletSlopeDySs (Phase 2, Steps 1-3)
    // ======================================================================

    @Nested
    class SlopeHelper {

        @Test
        void testAscendingContourProducesNegativeDySs() {
            // Right note higher (smaller/more negative top Y) and staff position agrees
            // (smaller staffPosition = higher pitch) -> slope preserved, sign negative.
            var left = mockColumn(0.0, -2.0, 0);
            var right = mockColumn(WIDTH_SS, -3.0, -2);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isNegative();
        }

        @Test
        void testDescendingContourProducesOppositeSign() {
            // Right note lower (larger top Y) and staff position agrees (larger staffPosition).
            // Both tops sit above the staff-floor ceiling so the floor doesn't mask the contour.
            var left = mockColumn(0.0, -4.0, 0);
            var right = mockColumn(WIDTH_SS, -3.0, 2);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isPositive();
        }

        @Test
        void testFlatNotesProduceZeroDySs() {
            var left = mockColumn(0.0, -2.0, 0);
            var right = mockColumn(WIDTH_SS, -2.0, 0);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isEqualTo(0.0);
        }

        @Test
        void testContourVsStaffPositionSignDisagreementVetoesToZero() {
            // tipRise negative (right top is smaller/higher) but staffRise positive (right
            // staffPosition larger = lower pitch) -- the two coordinate systems disagree,
            // so Step 2 must force the slope flat rather than trust either signal alone.
            var left = mockColumn(0.0, -2.0, 0);
            var right = mockColumn(WIDTH_SS, -3.0, 2);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isEqualTo(0.0);
        }

        @Test
        void testSlopeExceedingMaxFactorIsClamped() {
            // Raw slope = (-100 - 0) / 4 = -25, far past MAX_SLOPE_FACTOR (0.5); the
            // contour veto agrees in sign (right staffPosition is very negative too), so
            // clamping -- not vetoing -- must be what caps the result.
            var left = mockColumn(0.0, 0.0, 0);
            var right = mockColumn(WIDTH_SS, -100.0, -50);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isCloseTo(-Tuplet.MAX_SLOPE_FACTOR * WIDTH_SS, within(TOLERANCE));
        }

        @Test
        void testAllRestSpanProducesZeroDySs() {
            // No non-rest columns at all -- defensive early-out.
            var dySs = StructuralStacker.computeTupletSlopeDySs(List.of(), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isEqualTo(0.0);
        }

        @Test
        void testSingleNonRestColumnProducesZeroDySs() {
            // A single non-rest tip has no tip run to divide by -- defensive early-out
            // avoids the NaN that (rightRun == 0) would otherwise produce.
            var only = mockColumn(0.0, -5.0, 0);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(only), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isEqualTo(0.0);
        }

        // -- Flipped-stem stress cases: mixed stem directions between the endpoints decouple the
        // TIP contour (getAbsoluteTopYSs) from the PITCH contour (staff position), stressing the
        // Step 2 contour veto that guards the slope sign. --

        @Test
        void testDescendingPitchWithFlippedStemsVetoesSlopeToFlat() {
            // Pitches clearly descend (staffRise > 0), but the stems are flipped: the high-pitch
            // left note is down-stem (low tip) and the low-pitch right note is up-stem (high tip),
            // so the TIP contour ascends. The tip/pitch sign disagreement must veto the slope to
            // flat rather than sloping the bracket the wrong way.
            var left = mockColumn(0.0, -0.5, 0);         // high pitch, down-stem -> low tip
            var right = mockColumn(WIDTH_SS, -2.0, 3);   // low pitch, up-stem -> high tip

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isEqualTo(0.0);
        }

        @Test
        void testAscendingPitchWithFlippedStemsVetoesSlopeToFlat() {
            // Mirror image: pitches ascend (staffRise < 0) but flipped stems make the tip contour
            // descend, so the veto must still force flat.
            var left = mockColumn(0.0, -3.5, 0);          // low pitch, up-stem -> high tip
            var right = mockColumn(WIDTH_SS, -2.0, -3);   // high pitch, down-stem -> low tip

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(dySs).isEqualTo(0.0);
        }

        @Test
        void testFlippedStemsWithAgreeingSignsPreserveSlope() {
            // Mixed stems (left up, right down) but the tip contour and the pitch contour agree in
            // sign, so the veto must NOT fire and a genuine sub-clamp slope is preserved. The left
            // endpoint is an up-stem, so its bound edge is the stem (headRight - STEM_SS); the right
            // endpoint's bound is always its head's right edge regardless of direction (Phase 2a).
            var leftTopYSs = -3.5;   // higher pitch, up-stem -> high tip
            var rightTopYSs = -2.5;  // lower pitch, down-stem -> lower tip (still above the staff floor)
            var left = mockColumn(0.0, leftTopYSs, 0);
            left.getElement().setUpper(true);
            var right = mockColumn(WIDTH_SS, rightTopYSs, 2);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            var tipRiseSs = rightTopYSs - leftTopYSs;
            var edgeRunSs = WIDTH_SS + LineThickness.STEM_SS;
            var expectedDySs = tipRiseSs * WIDTH_SS / edgeRunSs;
            // Precondition: genuinely sub-clamp, so the slope is preserved exactly (not clamped).
            assertThat(Math.abs(expectedDySs / WIDTH_SS)).isLessThan(Tuplet.MAX_SLOPE_FACTOR);
            assertThat(dySs).isCloseTo(expectedDySs, within(TOLERANCE));
        }

        // -- Edge-run basis (Phase 2a): the bracket's total rise is spread over the head/stem-edge
        // run (x0..x1), which is wider than the note-center span, so the drawn slope is
        // proportionally shallower than the raw note-center contour. --

        @Test
        void testBothEndpointsDownStemUsesHeadEdgeToHeadEdgeRun() {
            // Neither endpoint is an up-stem, so both bounds are head edges: x0 = leftX (head
            // left), x1 = rightX + NOTE_HEAD_WIDTH_SS (head right) -- wider than WIDTH_SS alone.
            // Both tops sit above the staff-floor ceiling so the floor doesn't mask the edge-run math.
            var leftTopYSs = -3.0;
            var rightTopYSs = -5.0;
            var left = mockColumn(0.0, leftTopYSs, 0);
            var right = mockColumn(WIDTH_SS, rightTopYSs, -2);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            var tipRiseSs = rightTopYSs - leftTopYSs;
            var edgeRunSs = WIDTH_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS;
            var expectedDySs = tipRiseSs * WIDTH_SS / edgeRunSs;

            assertThat(dySs).isCloseTo(expectedDySs, within(TOLERANCE));
            // The edge run is wider than WIDTH_SS, so the reduced slope is shallower than the raw
            // note-center rise expressed over WIDTH_SS.
            assertThat(Math.abs(dySs)).isLessThan(Math.abs(tipRiseSs));
        }

        @Test
        void testBothEndpointsUpStemUsesStemEdgeToHeadEdgeRun() {
            // Both endpoints are up-stems: x0 = leftX + NOTE_HEAD_WIDTH_SS - STEM_SS (left's stem
            // edge), x1 = rightX + NOTE_HEAD_WIDTH_SS (right's bound is always its head edge).
            // Both tops sit above the staff-floor ceiling so the floor doesn't mask the edge-run math.
            var leftTopYSs = -3.0;
            var rightTopYSs = -5.0;
            var left = mockColumn(0.0, leftTopYSs, 0);
            left.getElement().setUpper(true);
            var right = mockColumn(WIDTH_SS, rightTopYSs, -2);
            right.getElement().setUpper(true);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            var tipRiseSs = rightTopYSs - leftTopYSs;
            var x0 = SMuFLConstants.NOTE_HEAD_WIDTH_SS - LineThickness.STEM_SS;
            var x1 = WIDTH_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS;
            var edgeRunSs = x1 - x0;
            var expectedDySs = tipRiseSs * WIDTH_SS / edgeRunSs;

            assertThat(dySs).isCloseTo(expectedDySs, within(TOLERANCE));
        }

        @Test
        void testMixedEndpointsUpStemLeftDownStemRightUsesMixedEdgeRun() {
            // Mixed-endpoint case: the left (up-stem) endpoint's bound is its stem edge, while the
            // right (down-stem) endpoint's bound is its head edge -- the two rules apply
            // independently per endpoint.
            // Both tops sit above the staff-floor ceiling so the floor doesn't mask the edge-run math.
            var leftTopYSs = -3.0;
            var rightTopYSs = -5.0;
            var left = mockColumn(0.0, leftTopYSs, 0);
            left.getElement().setUpper(true);
            var right = mockColumn(WIDTH_SS, rightTopYSs, -2);
            // right stays default DOWN -- bound is the head edge.

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            var tipRiseSs = rightTopYSs - leftTopYSs;
            var x0 = SMuFLConstants.NOTE_HEAD_WIDTH_SS - LineThickness.STEM_SS;
            var x1 = WIDTH_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS;
            var edgeRunSs = x1 - x0;
            var expectedDySs = tipRiseSs * WIDTH_SS / edgeRunSs;

            assertThat(dySs).isCloseTo(expectedDySs, within(TOLERANCE));
        }

        // -- Staff-floor (RC2, Phase 1): an outer endpoint whose own tip sits inside the staff floors
        // to the staff-top ceiling before the rise is taken, mirroring LilyPond's unite(staff). --

        @Test
        void testLowEndpointInsideStaffFloorsToStaffCeilingForSlope() {
            // Left endpoint's raw tip (-1.0) sits below the staff-top ceiling (-2.25), i.e. inside
            // the staff, so it floors up to the ceiling. The right endpoint's tip (-3.0) is already
            // above the ceiling and stays untouched by the same Math.min.
            var leftRawTopYSs = -1.0;
            var rightRawTopYSs = -3.0;
            var left = mockColumn(0.0, leftRawTopYSs, 0);
            var right = mockColumn(WIDTH_SS, rightRawTopYSs, -4);

            var dySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            var staffTopCeilingYSs = StackingUtils.STAFF_TOP_Y_SS - StructuralStacker.TUPLET_STAFF_PADDING_SS;
            var flooredLeftTopYSs = Math.min(leftRawTopYSs, staffTopCeilingYSs);
            var flooredRightTopYSs = Math.min(rightRawTopYSs, staffTopCeilingYSs);
            var tipRiseSs = flooredRightTopYSs - flooredLeftTopYSs;
            var edgeRunSs = WIDTH_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS;
            var expectedDySs = tipRiseSs * WIDTH_SS / edgeRunSs;

            // Precondition: the floor actually engaged for the left endpoint (raw tip is lower than
            // the ceiling), so this test exercises the floor rather than passing vacuously.
            assertThat(flooredLeftTopYSs).isCloseTo(staffTopCeilingYSs, within(TOLERANCE));
            assertThat(flooredRightTopYSs).isCloseTo(rightRawTopYSs, within(TOLERANCE));
            // Precondition: genuinely sub-clamp, so the slope is preserved exactly (not clamped).
            assertThat(Math.abs(expectedDySs / WIDTH_SS)).isLessThan(Tuplet.MAX_SLOPE_FACTOR);
            assertThat(dySs).isCloseTo(expectedDySs, within(TOLERANCE));
        }

        @Test
        void testInteriorStemDirectionDoesNotAffectEndpointDrivenSlope() {
            // The slope is derived from the endpoint tips only. A wild interior tip -- here a very
            // tall down-stem that dwarfs both endpoints -- must not change the result.
            var left = mockColumn(0.0, -3.5, 0);
            var right = mockColumn(WIDTH_SS, -5.5, -4);
            var tallInteriorDownStem = mockColumn(WIDTH_SS / 2.0, -8.0, 0);

            var endpointsOnlyDySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);
            var withInteriorDySs = StructuralStacker.computeTupletSlopeDySs(
                List.of(left, tallInteriorDownStem, right), ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(withInteriorDySs).isCloseTo(endpointsOnlyDySs, within(TOLERANCE));
        }
    }

    // ======================================================================
    // collectNonRestSpannedColumns (rest-skipping)
    // ======================================================================

    @Nested
    class RestSkipping {

        @Test
        void testLeadingAndTrailingRestsAreSkipped() {
            var line = detachedLine();
            var leadingRest = ElementType.CROTCHET_REST.newInstance();
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            var trailingRest = ElementType.CROTCHET_REST.newInstance();
            line.addElement(leadingRest);
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(trailingRest);

            var tuplet = Tuplet.withUnresolvedRatio(leadingRest, trailingRest, 3);
            line.addRangeElement(tuplet);

            var restColumn = mock(ElementColumn.class);
            when(restColumn.isRest()).thenReturn(true);
            var columnA = mock(ElementColumn.class);
            when(columnA.isRest()).thenReturn(false);
            var columnB = mock(ElementColumn.class);
            when(columnB.isRest()).thenReturn(false);

            var columnsByElement = new HashMap<StaffElement, ElementColumn>();
            columnsByElement.put(leadingRest, restColumn);
            columnsByElement.put(noteA, columnA);
            columnsByElement.put(noteB, columnB);
            columnsByElement.put(trailingRest, restColumn);

            var result = StructuralStacker.collectNonRestSpannedColumns(line, tuplet, columnsByElement);

            assertThat(result).containsExactly(columnA, columnB);
        }
    }

    // ======================================================================
    // computeTupletClearanceLeftYSs (Phase 2, Step 1): the bracket floats above every column's
    // actual tip (getAbsoluteTopYSs()) -- reverted from the removed bracket-driven trim, which
    // used to substitute a baked min-stem-tip constraint for interior up-stems. Every column is
    // now treated uniformly regardless of stem direction or beam membership.
    // ======================================================================

    @Nested
    class ClearanceHelper {

        // A weak (low, near the middle line) obstacle. It sits below the top staff line, so the
        // staff-top clamp raises it to STAFF_TOP_Y_SS -- never binding over a taller interior or
        // endpoint tip. Placed at the span ends so the subject under test is the interior column.
        private static final double WEAK_ENDPOINT_TOP_YSS = -0.5;

        private static ElementColumn weakEndpoint(double xSs) {
            return mockColumn(xSs, WEAK_ENDPOINT_TOP_YSS, 0);
        }

        @Test
        void testIntermediateTallerNoteForcesLineUpToClearBothEndpoints() {
            // Sloped line descending to the right (dySs positive). The middle tip is much
            // taller (smaller/more negative top) than either endpoint, so it alone must
            // determine leftYSs -- an endpoint-only calculation would let the line dip
            // below this tip once tilted.
            var dySs = 2.0;
            var slope = dySs / WIDTH_SS;

            var leftTip = mockColumn(0.0, -5.0, 0);
            var middleTip = mockColumn(WIDTH_SS / 2.0, -8.0, 0);
            var rightTip = mockColumn(WIDTH_SS, -3.0, 0);

            // leftYSs is the raw tip ceiling (no margin baked in) -- the caller's
            // placeAndReserve applies the single marginSs gap; baking a margin in here too
            // would double it.
            var expectedLeftYSs = middleTip.getAbsoluteTopYSs()
                - slope * (middleTip.getXSs() - ANCHOR_X_SS);

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(leftTip, middleTip, rightTip), NO_ARTICULATIONS, dySs, ANCHOR_X_SS, WIDTH_SS,
                ANCHOR_X_SS, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(leftYSs).isCloseTo(expectedLeftYSs, within(TOLERANCE));

            // Both endpoints must clear their own tips once projected along the resolved line.
            var rightYSs = leftYSs + dySs;
            var leftLineAtLeftTipYSs = leftYSs + slope * (leftTip.getXSs() - ANCHOR_X_SS);
            var leftLineAtRightTipYSs = leftYSs + slope * (rightTip.getXSs() - ANCHOR_X_SS);

            assertThat(leftLineAtLeftTipYSs).isLessThanOrEqualTo(leftTip.getAbsoluteTopYSs());
            assertThat(leftLineAtRightTipYSs).isLessThanOrEqualTo(rightTip.getAbsoluteTopYSs());
            assertThat(rightYSs).isEqualTo(leftYSs + dySs);
        }

        @Test
        void testInteriorUpStemObstacleIsItsOwnNaturalTip() {
            // The now-removed trim pass used to substitute a baked min-stem-tip constraint for an
            // interior up-stem. Reverted: it contributes its own actual getAbsoluteTopYSs(), the
            // same as any other column.
            var tallTopYSs = -6.0;
            var interior = mockColumn(WIDTH_SS / 2.0, tallTopYSs, 0);
            interior.getElement().setUpper(true);

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(weakEndpoint(0.0), interior, weakEndpoint(WIDTH_SS)),
                NO_ARTICULATIONS, 0.0, 0.0, WIDTH_SS, 0.0, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(leftYSs).isCloseTo(tallTopYSs, within(TOLERANCE));
        }

        @Test
        void testInteriorDownStemObstacleIsItsOwnNaturalTip() {
            var tallTopYSs = -3.0;  // well above STAFF_TOP_Y_SS so the staff-top clamp is inert
            var interior = mockColumn(WIDTH_SS / 2.0, tallTopYSs, 0);
            // Default direction is DOWN.

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(weakEndpoint(0.0), interior, weakEndpoint(WIDTH_SS)),
                NO_ARTICULATIONS, 0.0, 0.0, WIDTH_SS, 0.0, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(leftYSs).isCloseTo(tallTopYSs, within(TOLERANCE));
        }

        @Test
        void testClearanceFoldsTheObstacleResolverTopNotTheColumnTip() {
            // A beamed up-stem's stem tucks inside the beam, so the tuplet must clear the beam's
            // outer edge, not the shorter natural tip getAbsoluteTopYSs() reports (issue #556). The
            // resolver overload supplies that beam top; the clearance must fold the resolved value.
            var naturalTipYSs = -3.0;
            var beamTopYSs = -5.0;  // beam edge sits above the tucked-in stem tip
            var interior = mockColumn(WIDTH_SS / 2.0, naturalTipYSs, 0);
            interior.getElement().setUpper(true);

            ToDoubleFunction<ElementColumn> beamTopForInterior =
                column -> column == interior ? beamTopYSs : column.getAbsoluteTopYSs();

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(weakEndpoint(0.0), interior, weakEndpoint(WIDTH_SS)),
                NO_ARTICULATIONS, 0.0, 0.0, WIDTH_SS, 0.0, WIDTH_SS, beamTopForInterior);

            assertThat(leftYSs).isCloseTo(beamTopYSs, within(TOLERANCE));
        }

        @Test
        void testStaffTopClampRaisesAWeakObstacleRegardlessOfDirection() {
            // A tip below the top staff line is clamped up to the staff-padding-widened staff top so
            // the bracket never dips below the staff, even for a flat span with no taller obstacle.
            var staffTopCeilingYSs = StackingUtils.STAFF_TOP_Y_SS - StructuralStacker.TUPLET_STAFF_PADDING_SS;

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(weakEndpoint(0.0), weakEndpoint(WIDTH_SS)),
                NO_ARTICULATIONS, 0.0, 0.0, WIDTH_SS, 0.0, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(leftYSs).isCloseTo(staffTopCeilingYSs, within(TOLERANCE));
        }

        @Test
        void testInteriorArticulationAboveTipRaisesTheLine() {
            // A short interior note whose accent sits above the tip: the accent, not the tip,
            // binds the flat bracket line. Mirrors LilyPond folding scripts into the clearance
            // points (tuplet-bracket.cc, "avoid-scripts").
            var tipTopYSs = -3.0;  // above STAFF_TOP_Y_SS so the staff-top clamp is inert
            var accentTopYSs = -5.5;
            var interior = mockColumn(WIDTH_SS / 2.0, tipTopYSs, 0);

            Function<ElementColumn, List<StructuralStacker.ScriptObstacle>> accentOnInterior =
                column -> column == interior
                    ? List.of(new StructuralStacker.ScriptObstacle(accentTopYSs, interior.getXSs()))
                    : List.of();

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(weakEndpoint(0.0), interior, weakEndpoint(WIDTH_SS)),
                accentOnInterior, 0.0, 0.0, WIDTH_SS, 0.0, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(leftYSs).isCloseTo(accentTopYSs, within(TOLERANCE));
        }

        @Test
        void testArticulationBelowTipDoesNotLowerTheLine() {
            // When the tip is already higher (smaller Y) than the articulation, the tip wins:
            // the min never lowers the bracket below a tall stem to sit on a lower dot.
            var tipTopYSs = -6.0;
            var staccatoTopYSs = -4.0;
            var interior = mockColumn(WIDTH_SS / 2.0, tipTopYSs, 0);

            Function<ElementColumn, List<StructuralStacker.ScriptObstacle>> staccatoOnInterior =
                column -> column == interior
                    ? List.of(new StructuralStacker.ScriptObstacle(staccatoTopYSs, interior.getXSs()))
                    : List.of();

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(weakEndpoint(0.0), interior, weakEndpoint(WIDTH_SS)),
                staccatoOnInterior, 0.0, 0.0, WIDTH_SS, 0.0, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            assertThat(leftYSs).isCloseTo(tipTopYSs, within(TOLERANCE));
        }

        @Test
        void testDownhillRightArmReachClearsBareNoteheadTipAtArmX() {
            // Down-sloping bracket whose end note is a bare notehead (no articulation). The right arm
            // extends past the end column, so on the downhill side the descending arm would dip into
            // the notehead tip if cleared only over the column's own X. The arm-edge fold (RC1: tip
            // only, never an articulation) re-projects that tip at the arm's X, lowering the line by
            // the extra slope-run to clear the extended arm. Mirrors LilyPond fitting the bracket to
            // its visual X-bounds (tuplet-bracket.cc).
            var dySs = 2.0;
            var slope = dySs / WIDTH_SS;

            var anchor = weakEndpoint(0.0);
            var end = weakEndpoint(WIDTH_SS);
            // weakEndpoint's tip (-0.5) is below the top staff line, so it clamps up to the
            // staff-padding-widened staff top.
            var endTipTopYSs = StackingUtils.STAFF_TOP_Y_SS - StructuralStacker.TUPLET_STAFF_PADDING_SS;

            var rightArmReachSs = 1.4;
            var rightArmXSs = WIDTH_SS + rightArmReachSs;

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(anchor, end), NO_ARTICULATIONS, dySs, 0.0, WIDTH_SS, 0.0, rightArmXSs, ElementColumn::getAbsoluteTopYSs);

            // Cleared at the arm's X, not the end column's: the tip projected the full anchor→arm run.
            var expectedLeftYSs = endTipTopYSs - slope * rightArmXSs;
            assertThat(leftYSs).isCloseTo(expectedLeftYSs, within(TOLERANCE));

            // Without the arm reach (arm at the end column) the line would sit higher, so the extended
            // arm would have dipped below the tip — confirm the reach genuinely lowered the line.
            var withoutArmReachYSs = endTipTopYSs - slope * WIDTH_SS;
            assertThat(leftYSs).isLessThan(withoutArmReachYSs);
        }

        @Test
        void testEndColumnArticulationBindsAtItsOwnCenterXNotTheArmEdge() {
            // Root cause 1 (script-at-center fix): an end column's accent must be folded at the
            // column's own X by the interior loop, not projected from the further-out arm edge. A
            // weak tip at the arm edge would let a wrongly-arm-projected accent bind even lower;
            // here the accent alone -- evaluated at the end column's X -- must be the binding ceiling.
            var dySs = 2.0;
            var slope = dySs / WIDTH_SS;
            var accentTopYSs = -6.0;

            var anchor = weakEndpoint(0.0);
            var end = weakEndpoint(WIDTH_SS);

            Function<ElementColumn, List<StructuralStacker.ScriptObstacle>> accentOnEnd =
                column -> column == end
                    ? List.of(new StructuralStacker.ScriptObstacle(accentTopYSs, end.getXSs()))
                    : List.of();

            var rightArmReachSs = 1.4;
            var rightArmXSs = WIDTH_SS + rightArmReachSs;

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(anchor, end), accentOnEnd, dySs, 0.0, WIDTH_SS, 0.0, rightArmXSs, ElementColumn::getAbsoluteTopYSs);

            // Bound by the accent projected from its center X (here the end column's own X), by the
            // interior loop -- not the further-out arm edge.
            var expectedLeftYSs = accentTopYSs - slope * end.getXSs();
            assertThat(leftYSs).isCloseTo(expectedLeftYSs, within(TOLERANCE));

            // Had the accent instead been folded at the arm edge, the line would sit lower still --
            // confirm the actual result is strictly higher than that (wrong) projection.
            var wrongArmEdgeProjectionYSs = accentTopYSs - slope * rightArmXSs;
            assertThat(leftYSs).isGreaterThan(wrongArmEdgeProjectionYSs);
        }

        @Test
        void testScriptFoldsAtItsCenterXNotTheColumnReferenceX() {
            // A script's clearance point is projected from its own center X (LilyPond
            // script_x.center()), not the column reference X (the notehead's left edge). On a
            // descending bracket the two differ by slope*(centerX - refX); folding at the column X
            // would under-clear the script and drop the downhill arm.
            var dySs = 2.0;
            var slope = dySs / WIDTH_SS;
            var accentTopYSs = -6.0;

            var anchor = weakEndpoint(0.0);
            var end = weakEndpoint(WIDTH_SS);
            // The accent centers half a notehead right of the column reference (its left edge).
            var accentCenterXSs = end.getXSs() + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2.0;

            Function<ElementColumn, List<StructuralStacker.ScriptObstacle>> accentAtCenter =
                column -> column == end
                    ? List.of(new StructuralStacker.ScriptObstacle(accentTopYSs, accentCenterXSs))
                    : List.of();

            var leftYSs = StructuralStacker.computeTupletClearanceLeftYSs(
                List.of(anchor, end), accentAtCenter, dySs, 0.0, WIDTH_SS, 0.0, WIDTH_SS, ElementColumn::getAbsoluteTopYSs);

            // Bound by the accent projected from its center X, not the column's own X.
            var expectedLeftYSs = accentTopYSs - slope * accentCenterXSs;
            assertThat(leftYSs).isCloseTo(expectedLeftYSs, within(TOLERANCE));

            // Folding at the column reference X would place the line higher (less clearance at the
            // true center) -- confirm the center projection binds strictly lower.
            var columnXProjectionYSs = accentTopYSs - slope * end.getXSs();
            assertThat(leftYSs).isLessThan(columnXProjectionYSs);
        }
    }

    // ======================================================================
    // rawObstacleTopSs: the obstacle top a column presents to the always-above tuplet (issue #556).
    // Only a beamed up-stem column with a resolved StemLayout reports the beam's outer edge;
    // every other combination falls back to the column's own natural tip.
    // ======================================================================

    @Nested
    class RawObstacleTopSs {

        private static StructuralStacker newStacker(LayoutResult.Builder builder) {
            var context = new StackingContext(List.of(), detachedLine(), builder);
            return new StructuralStacker(context, new StaffExtents(WIDTH_SS));
        }

        @Test
        void testUnbeamedUpStemColumnUsesItsOwnNaturalTip() {
            var naturalTopYSs = -3.0;
            var column = mockColumn(0.0, naturalTopYSs, 0);
            column.getElement().setUpper(true);
            when(column.isBeamed()).thenReturn(false);

            var stacker = newStacker(new LayoutResult.Builder());

            assertThat(stacker.rawObstacleTopSs(column)).isCloseTo(naturalTopYSs, within(TOLERANCE));
        }

        @Test
        void testBeamedUpStemColumnWithNoResolvedStemLayoutFallsBackToNaturalTip() {
            var naturalTopYSs = -3.0;
            var column = mockColumn(0.0, naturalTopYSs, 0);
            column.getElement().setUpper(true);
            when(column.isBeamed()).thenReturn(true);

            // No stem layout put into the builder for this element -- getStemLayout returns null.
            var stacker = newStacker(new LayoutResult.Builder());

            assertThat(stacker.rawObstacleTopSs(column)).isCloseTo(naturalTopYSs, within(TOLERANCE));
        }

        @Test
        void testBeamedUpStemColumnUsesTheResolvedBeamTopNotTheNaturalTip() {
            var naturalTopYSs = -3.0;
            var beamTopYSs = -5.0;  // beam edge sits above the tucked-in stem tip
            var column = mockColumn(0.0, naturalTopYSs, 0);
            column.getElement().setUpper(true);
            when(column.isBeamed()).thenReturn(true);

            var builder = new LayoutResult.Builder();
            builder.putStemLayout(column.getElement(),
                new LayoutResult.StemLayout(beamTopYSs, 0.0, 0.0, 0.0, false, 0));
            var stacker = newStacker(builder);

            assertThat(stacker.rawObstacleTopSs(column)).isCloseTo(beamTopYSs, within(TOLERANCE));
        }

        @Test
        void testBeamedDownStemColumnUsesItsOwnNaturalTipNotTheBeam() {
            // A down-stem beam sits below the noteheads, so it is never the ceiling an always-above
            // tuplet needs to clear -- only a beamed UP-stem column is special-cased.
            var naturalTopYSs = -3.0;
            var beamTopYSs = -99.0;
            var column = mockColumn(0.0, naturalTopYSs, 0);
            // Default direction is DOWN.
            when(column.isBeamed()).thenReturn(true);

            var builder = new LayoutResult.Builder();
            builder.putStemLayout(column.getElement(),
                new LayoutResult.StemLayout(beamTopYSs, 0.0, 0.0, 0.0, false, 0));
            var stacker = newStacker(builder);

            assertThat(stacker.rawObstacleTopSs(column)).isCloseTo(naturalTopYSs, within(TOLERANCE));
        }
    }
}
