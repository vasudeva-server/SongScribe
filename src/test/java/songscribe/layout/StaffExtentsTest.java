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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class StaffExtentsTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;

    // Both top and bot arrays default to the middle staff line (Y-down, middle line = 0),
    // the same coordinate system StackingUtils' anchor calculations use.
    private static final double DEFAULT_EXTENT_SS = 0.0;

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class YSetProfile {

        private static final double RESERVE_X_SS = 10.0;
        private static final double RESERVE_WIDTH_SS = 5.0;
        private static final double RESERVE_EDGE_Y_SS = -3.0;

        // A wedge-shaped outer edge: flat at its left end, receding a full staff space toward the
        // staff at its right.
        private static final double SLOPED_INNER_OFFSET_SS = 1.0;

        @Test
        void testFlatProfileReservesExactlyWhatYSetDoes() {
            // Every element but the staccato reserves a flat profile, so this identity is what keeps
            // the outer-edge reservation from moving anything it should not.
            var profiled = new StaffExtents(LINE_WIDTH_SS);
            profiled.ySetProfile(true, RESERVE_X_SS,
                StaffExtents.Profile.flat(RESERVE_WIDTH_SS), RESERVE_EDGE_Y_SS);

            var flat = new StaffExtents(LINE_WIDTH_SS);
            flat.ySet(true, RESERVE_X_SS, RESERVE_WIDTH_SS, RESERVE_EDGE_Y_SS);

            assertThat(profiled.yGet(true, RESERVE_X_SS, RESERVE_WIDTH_SS))
                .isEqualTo(flat.yGet(true, RESERVE_X_SS, RESERVE_WIDTH_SS));
        }

        @Test
        void testProfileOffsetsGrowTowardTheStaff() {
            // Above the staff, an outer-edge offset moves the reservation to a larger Y — inward.
            // Getting this sign backwards would reserve outside the element's own box.
            var extents = new StaffExtents(LINE_WIDTH_SS);
            var profile = new StaffExtents.Profile(List.of(
                new StaffExtents.Profile.Segment(
                    0.0, RESERVE_WIDTH_SS, 0.0, SLOPED_INNER_OFFSET_SS)));

            extents.ySetProfile(true, RESERVE_X_SS, profile, RESERVE_EDGE_Y_SS);

            // At its left end the reservation sits on the edge; at its right it has receded inward.
            assertThat(extents.yGet(true, RESERVE_X_SS, 0.0)).isEqualTo(RESERVE_EDGE_Y_SS);
            assertThat(extents.yGet(true, RESERVE_X_SS + RESERVE_WIDTH_SS, 0.0))
                .isEqualTo(RESERVE_EDGE_Y_SS + SLOPED_INNER_OFFSET_SS);
        }

        @Test
        void testProfileOffsetsGrowTowardTheStaffBelow() {
            // Below the staff the same offset moves the reservation to a smaller Y — inward again.
            var extents = new StaffExtents(LINE_WIDTH_SS);
            var profile = new StaffExtents.Profile(List.of(
                new StaffExtents.Profile.Segment(
                    0.0, RESERVE_WIDTH_SS, 0.0, SLOPED_INNER_OFFSET_SS)));

            extents.ySetProfile(false, RESERVE_X_SS, profile, -RESERVE_EDGE_Y_SS);

            assertThat(extents.yGet(false, RESERVE_X_SS, 0.0)).isEqualTo(-RESERVE_EDGE_Y_SS);
            assertThat(extents.yGet(false, RESERVE_X_SS + RESERVE_WIDTH_SS, 0.0))
                .isEqualTo(-RESERVE_EDGE_Y_SS - SLOPED_INNER_OFFSET_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Clearance {

        private static final double PADDING_SS = 0.5;
        private static final double HORIZON_PADDING_SS = 1.0;

        private static final double ELEMENT_X_SS = 10.0;
        private static final double ELEMENT_WIDTH_SS = 5.0;

        private static final double SUPPORT_ABOVE_Y_SS = -3.0;
        private static final double SUPPORT_BELOW_Y_SS = 3.0;

        // Further from the staff than SUPPORT_ABOVE_Y_SS, so it is the binding one of the two.
        private static final double OUTER_SUPPORT_ABOVE_Y_SS = -4.0;

        // Far enough right that even dilated by HORIZON_PADDING_SS it cannot reach the element.
        private static final double DISTANT_X_SS = 30.0;

        private static final double MIDDLE_LINE_Y_SS = 0.0;

        @Test
        void testNoReservationAnywhereReportsAbsentSupport() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            var support = extents.clearance(true, ELEMENT_X_SS,
                StaffExtents.Profile.flat(ELEMENT_WIDTH_SS), PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.present())
                .describedAs("nothing is reserved, so only the caller's staff clamp may apply")
                .isFalse();
        }

        @Test
        void testReservationBeyondTheHorizonReportsAbsentSupport() {
            // A line holds hundreds of reservations. Only those the element's own profile reaches,
            // dilated by the horizon padding, may move it.
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, DISTANT_X_SS, ELEMENT_WIDTH_SS, SUPPORT_ABOVE_Y_SS);

            var support = extents.clearance(true, ELEMENT_X_SS,
                StaffExtents.Profile.flat(ELEMENT_WIDTH_SS), PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.present())
                .describedAs("a reservation elsewhere on the line is not a support")
                .isFalse();
        }

        @Test
        void testSupportAtTheMiddleLineIsPresentUnlikeNoSupportAtAll() {
            // yGet reports 0.0 for both cases; only Support.present() tells them apart. Resolving a
            // script's supports through yGet's 0.0 would silently clamp it to the middle staff line.
            var reserved = new StaffExtents(LINE_WIDTH_SS);
            reserved.ySet(true, ELEMENT_X_SS, ELEMENT_WIDTH_SS, MIDDLE_LINE_Y_SS);

            var empty = new StaffExtents(LINE_WIDTH_SS);

            assertThat(reserved.yGet(true, ELEMENT_X_SS, ELEMENT_WIDTH_SS))
                .describedAs("yGet cannot distinguish these two")
                .isEqualTo(empty.yGet(true, ELEMENT_X_SS, ELEMENT_WIDTH_SS));

            var flat = StaffExtents.Profile.flat(ELEMENT_WIDTH_SS);
            var reservedSupport =
                reserved.clearance(true, ELEMENT_X_SS, flat, PADDING_SS, HORIZON_PADDING_SS);

            assertThat(reservedSupport.present()).isTrue();
            assertThat(reservedSupport.ySs()).isEqualTo(MIDDLE_LINE_Y_SS - PADDING_SS);

            assertThat(empty.clearance(true, ELEMENT_X_SS, flat, PADDING_SS, HORIZON_PADDING_SS)
                .present()).isFalse();
        }

        @Test
        void testFlatProfileOverFlatSupportReducesToYGetPlusPadding() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, ELEMENT_X_SS, ELEMENT_WIDTH_SS, SUPPORT_ABOVE_Y_SS);

            var support = extents.clearance(true, ELEMENT_X_SS,
                StaffExtents.Profile.flat(ELEMENT_WIDTH_SS), PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.present()).isTrue();
            assertThat(support.ySs())
                .describedAs("above the staff the element's inner edge sits padding above the support")
                .isEqualTo(SUPPORT_ABOVE_Y_SS - PADDING_SS);
        }

        @Test
        void testFlatProfileBelowTheStaffMirrorsTheAboveCase() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(false, ELEMENT_X_SS, ELEMENT_WIDTH_SS, SUPPORT_BELOW_Y_SS);

            var support = extents.clearance(false, ELEMENT_X_SS,
                StaffExtents.Profile.flat(ELEMENT_WIDTH_SS), PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.present()).isTrue();
            assertThat(support.ySs()).isEqualTo(SUPPORT_BELOW_Y_SS + PADDING_SS);
        }

        @Test
        void testTheMostOutwardOfSeveralOverlappingSupportsBinds() {
            // The fold across buildings, which nothing else exercises: two supports under one
            // profile, and the element must clear the further one.
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, ELEMENT_X_SS, ELEMENT_WIDTH_SS, SUPPORT_ABOVE_Y_SS);
            extents.ySet(true, ELEMENT_X_SS, ELEMENT_WIDTH_SS, OUTER_SUPPORT_ABOVE_Y_SS);

            var support = extents.clearance(true, ELEMENT_X_SS,
                StaffExtents.Profile.flat(ELEMENT_WIDTH_SS), PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.ySs())
                .describedAs("clearing the nearer support alone would leave the element inside the further one")
                .isEqualTo(OUTER_SUPPORT_ABOVE_Y_SS - PADDING_SS);
        }

        // ---------------------------------------------------------------------
        // A sloped support under a sloped profile: the case only clearance handles, and the only one
        // where the demand curve kinks away from the ends of the overlap.
        //
        // The element sits at WEDGE_X_SS with an inner edge sloping outward at +1 (offset 0 at its
        // left end, WEDGE_OUTER_OFFSET_SS at its right). The support is a chord falling at -2 per ss.
        // Dilated by HORIZON_PADDING_SS the chord holds its endpoint height flat on either side, so
        // demand(x) = height(x) + offset(x) runs:
        //
        //   [19, 20]  flat -2, offset rising  ->  demand rises  -2 .. -1
        //   [20, 24]  height -2 -> -10, offset rising -> demand falls  -1 .. -5
        //   [24, 25]  flat -10, offset rising ->  demand rises  -5 .. -4
        //
        // The minimum is -5, at the chord's own right end. Evaluating only the overlap's two ends
        // would find min(-2, -4) = -4 and seat the element a full staff space too close.
        private static final double WEDGE_X_SS = 19.0;
        private static final double WEDGE_WIDTH_SS = 6.0;
        private static final double WEDGE_OUTER_OFFSET_SS = 6.0;

        private static final double CHORD_START_X_SS = 20.0;
        private static final double CHORD_END_X_SS = 24.0;
        private static final double CHORD_START_Y_SS = -2.0;
        private static final double CHORD_END_Y_SS = -10.0;

        private static final double KINK_DEMAND_Y_SS = -5.0;
        private static final double OVERLAP_END_DEMAND_Y_SS = -4.0;

        @Test
        void testSlopedSupportBindsAtItsOwnEndpointInsideTheOverlap() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySetSloped(true, CHORD_START_X_SS, CHORD_END_X_SS,
                CHORD_START_Y_SS, CHORD_END_Y_SS);

            var wedge = new StaffExtents.Profile(List.of(new StaffExtents.Profile.Segment(
                0.0, WEDGE_WIDTH_SS, 0.0, WEDGE_OUTER_OFFSET_SS)));

            var support = extents.clearance(true, WEDGE_X_SS, wedge, PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.present()).isTrue();
            assertThat(support.ySs())
                .describedAs("the binding x is the chord's right end, not either end of the overlap")
                .isEqualTo(KINK_DEMAND_Y_SS - PADDING_SS);

            assertThat(support.ySs())
                .describedAs("dropping the chord's endpoints as breakpoints would seat it here")
                .isNotEqualTo(OVERLAP_END_DEMAND_Y_SS - PADDING_SS);
        }

        // ---------------------------------------------------------------------
        // Horizon padding dilates the support, never the element's own profile (skyline.cc pads
        // `dim`). A flat support lying right of the profile's zero-offset end, close enough for the
        // horizon to reach:
        //
        //   dilated support  -> reaches x = 21, where the profile's offset is only 2  -> demand -1
        //   widened profile  -> support still starts at x = 22, offset there is 3     -> demand  0
        //
        // Padding both would double the padding; padding the profile instead lets the element slip a
        // full staff space closer than LilyPond puts it.
        private static final double NEAR_SUPPORT_START_X_SS = 22.0;
        private static final double NEAR_SUPPORT_WIDTH_SS = 1.0;
        private static final double DILATED_SUPPORT_DEMAND_Y_SS = -1.0;
        private static final double WIDENED_PROFILE_DEMAND_Y_SS = 0.0;

        @Test
        void testHorizonPaddingDilatesTheSupportNotTheProfile() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, NEAR_SUPPORT_START_X_SS, NEAR_SUPPORT_WIDTH_SS, SUPPORT_ABOVE_Y_SS);

            var wedge = new StaffExtents.Profile(List.of(new StaffExtents.Profile.Segment(
                0.0, WEDGE_WIDTH_SS, 0.0, WEDGE_OUTER_OFFSET_SS)));

            var support = extents.clearance(true, WEDGE_X_SS, wedge, PADDING_SS, HORIZON_PADDING_SS);

            assertThat(support.ySs())
                .describedAs("the dilated support reaches where the wedge's offset is smaller")
                .isEqualTo(DILATED_SUPPORT_DEMAND_Y_SS - PADDING_SS);

            assertThat(support.ySs())
                .describedAs("widening the profile instead would seat the element here")
                .isNotEqualTo(WIDENED_PROFILE_DEMAND_Y_SS - PADDING_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CopyTopFrom {

        @Test
        void testCopiedExtentsMatchSource() {
            var source = new StaffExtents(LINE_WIDTH_SS);
            source.ySet(true, 10.0, 5.0, -3.0);
            source.ySet(true, 30.0, 8.0, -5.0);

            var target = new StaffExtents(LINE_WIDTH_SS);
            target.copyTopFrom(source);

            assertThat(target.yGet(true, 10.0, 5.0)).isEqualTo(-3.0);
            assertThat(target.yGet(true, 30.0, 8.0)).isEqualTo(-5.0);
        }

        @Test
        void testCopyDoesNotAffectBotArray() {
            var source = new StaffExtents(LINE_WIDTH_SS);
            source.ySet(true, 10.0, 5.0, -2.0);
            source.ySet(false, 10.0, 5.0, 6.0);

            var target = new StaffExtents(LINE_WIDTH_SS);
            target.copyTopFrom(source);

            assertThat(target.yGet(false, 10.0, 5.0)).isEqualTo(DEFAULT_EXTENT_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InitializationDefaults {

        @Test
        void testBotDefaultsToMiddleLine() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            assertThat(extents.yGet(false, 0.0, LINE_WIDTH_SS)).isEqualTo(DEFAULT_EXTENT_SS);
        }

        @Test
        void testTopDefaultsToMiddleLine() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            assertThat(extents.yGet(true, 0.0, LINE_WIDTH_SS)).isEqualTo(DEFAULT_EXTENT_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OverlappingReservations {

        @Test
        void testAboveOverlapKeepsHigherExtent() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            // First reservation at Y = -2.0
            extents.ySet(true, 10.0, 10.0, -2.0);
            // Overlapping reservation at Y = -4.0 (higher, since Y-down)
            extents.ySet(true, 15.0, 10.0, -4.0);

            // In the overlap region (15-20), the higher extent (-4.0) wins
            assertThat(extents.yGet(true, 15.0, 5.0)).isEqualTo(-4.0);
            // In the non-overlap region (10-15), only the first reservation
            assertThat(extents.yGet(true, 10.0, 4.0)).isEqualTo(-2.0);
        }

        @Test
        void testAboveOverlapIgnoresLowerExtent() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            // First reservation at Y = -4.0
            extents.ySet(true, 10.0, 10.0, -4.0);
            // Overlapping reservation at Y = -1.0 (lower, does not override)
            extents.ySet(true, 10.0, 10.0, -1.0);

            assertThat(extents.yGet(true, 10.0, 10.0)).isEqualTo(-4.0);
        }

        @Test
        void testBelowOverlapKeepsLowerExtent() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            extents.ySet(false, 10.0, 10.0, 5.0);
            extents.ySet(false, 15.0, 10.0, 7.0);

            // In the overlap region, the lower extent (7.0) wins
            assertThat(extents.yGet(false, 15.0, 5.0)).isEqualTo(7.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StepClamping {

        @Test
        void testNegativeXClampedToFirstStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, -5.0, 3.0, -2.0);

            // Should be readable starting from X = 0
            assertThat(extents.yGet(true, 0.0, 3.0)).isEqualTo(-2.0);
        }

        @Test
        void testWidthBeyondLineEndClampedToLastStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 60.0, 10.0, -3.0);

            // Query at the end of the line should see the reservation
            assertThat(extents.yGet(true, 60.0, 10.0)).isEqualTo(-3.0);
        }

        @Test
        void testXAtExactLineWidthClampedToLastStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, LINE_WIDTH_SS, 5.0, -1.5);

            assertThat(extents.yGet(true, LINE_WIDTH_SS, 5.0)).isEqualTo(-1.5);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class YGetBasicOperations {

        @Test
        void testAboveQueryReturnsHighestExtentAcrossRange() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            // Two reservations at different heights in the same query range
            extents.ySet(true, 10.0, 5.0, -2.0);
            extents.ySet(true, 13.0, 5.0, -5.0);

            // Query spanning both: should return the highest (most negative)
            assertThat(extents.yGet(true, 10.0, 8.0)).isEqualTo(-5.0);
        }

        @Test
        void testBelowQueryReturnsLowestExtentAcrossRange() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            extents.ySet(false, 10.0, 5.0, 5.0);
            extents.ySet(false, 13.0, 5.0, 8.0);

            assertThat(extents.yGet(false, 10.0, 8.0)).isEqualTo(8.0);
        }

        @Test
        void testQueryOutsideReservationReturnsDefault() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 10.0, 5.0, -3.0);

            // Query a different region — should still be at default
            assertThat(extents.yGet(true, 30.0, 5.0)).isEqualTo(DEFAULT_EXTENT_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class YSetBasicOperations {

        @Test
        void testAboveReservationUpdatesTopArray() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 10.0, 5.0, -2.0);

            assertThat(extents.yGet(true, 10.0, 5.0)).isEqualTo(-2.0);
        }

        @Test
        void testBelowReservationUpdatesBotArray() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(false, 10.0, 5.0, 6.0);

            assertThat(extents.yGet(false, 10.0, 5.0)).isEqualTo(6.0);
        }

        @Test
        void testReservationDoesNotAffectOtherDirection() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 10.0, 5.0, -2.0);

            // Below direction should still be at default
            assertThat(extents.yGet(false, 10.0, 5.0)).isEqualTo(DEFAULT_EXTENT_SS);
        }

        @Test
        void testZeroWidthReservationAffectsSingleStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 32.0, 0.0, -1.0);

            assertThat(extents.yGet(true, 32.0, 0.0)).isEqualTo(-1.0);
        }
    }

}
