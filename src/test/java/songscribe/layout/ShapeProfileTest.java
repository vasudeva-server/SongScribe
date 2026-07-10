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
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.shape.AccentShape;

/**
 * Guards the assumptions {@code NoteAttachedStacker.ACCENT_PROFILE_*} rests on. If accent.svg is
 * ever redrawn, these are what will notice.
 */
class ShapeProfileTest extends UnitTest {

    // The profile's zero must be exact: it is what keeps an untied accent resting on the notehead
    // exactly where its bounding box put it, so a flattening residue here becomes a visible shift.
    private static final double ZERO_TOLERANCE = 1e-12;

    // Flattening tolerance is 0.001 ss and the two boundaries are flattened independently, so their
    // sampled x-breakpoints differ slightly. Symmetry can only be claimed to that order.
    private static final double MIRROR_TOLERANCE_SS = 1e-4;

    // The tip is a curve, so the flattened polyline stops just short of the true apex. The error is
    // bounded by ShapeProfile's own flattening tolerance.
    private static final double FLATNESS_TOLERANCE_SS = 0.01;

    // The wedge is 1.48 ss wide; sampling every 0.02 ss walks the arm, both caps and the tip.
    private static final double SAMPLE_STEP_SS = 0.02;

    // Where the open end's round cap gives way to the straight arm: the offset dips to zero at the
    // glyph's topmost point, a little right of its leftmost point, and climbs from there.
    private static final double CAP_END_SS = 0.10;

    private static double offsetAtSs(StaffExtents.Profile profile, double localXSs) {
        for (var segment : profile.segments()) {
            if (localXSs >= segment.xStartSs() && localXSs <= segment.xEndSs()) {
                var spanSs = segment.xEndSs() - segment.xStartSs();

                if (spanSs <= 0.0) {
                    return segment.yOffsetStartSs();
                }

                var t = (localXSs - segment.xStartSs()) / spanSs;
                return segment.yOffsetStartSs()
                    + t * (segment.yOffsetEndSs() - segment.yOffsetStartSs());
            }
        }

        return Double.NaN;
    }

    private static double minOffsetSs(StaffExtents.Profile profile) {
        var minSs = Double.MAX_VALUE;

        for (var segment : profile.segments()) {
            minSs = Math.min(minSs, Math.min(segment.yOffsetStartSs(), segment.yOffsetEndSs()));
        }

        return minSs;
    }

    @Test
    void testFlatProfileIsOneZeroOffsetSegmentSpanningTheWidth() {
        var widthSs = 3.0;
        var profile = StaffExtents.Profile.flat(widthSs);

        assertThat(profile.segments()).hasSize(1);
        assertThat(profile.segments().getFirst().xStartSs()).isZero();
        assertThat(profile.segments().getFirst().xEndSs()).isEqualTo(widthSs);
        assertThat(profile.segments().getFirst().yOffsetStartSs()).isZero();
        assertThat(profile.segments().getFirst().yOffsetEndSs()).isZero();
    }

    @Test
    void testAccentProfileTouchesItsInnerBoundingEdge() {
        // The offset is measured from the inner bounding edge, so its minimum is zero by definition.
        // Flattening leaves the polyline a hair inside the true bound; ShapeProfile renormalizes.
        assertThat(minOffsetSs(ShapeProfile.innerEdge(AccentShape.accent(), true)))
            .describedAs("above-staff accent profile must touch its own bounding edge")
            .isCloseTo(0.0, within(ZERO_TOLERANCE));

        assertThat(minOffsetSs(ShapeProfile.innerEdge(AccentShape.accent(), false)))
            .describedAs("below-staff accent profile must touch its own bounding edge")
            .isCloseTo(0.0, within(ZERO_TOLERANCE));
    }

    @Test
    void testAccentProfileStaysWithinTheGlyphBounds() {
        var bounds = AccentShape.accent().getBounds2D();
        var profile = ShapeProfile.innerEdge(AccentShape.accent(), false);

        for (var segment : profile.segments()) {
            assertThat(segment.xStartSs()).isBetween(0.0, bounds.getWidth());
            assertThat(segment.xEndSs()).isBetween(0.0, bounds.getWidth());
            assertThat(segment.yOffsetStartSs()).isBetween(0.0, bounds.getHeight());
            assertThat(segment.yOffsetEndSs()).isBetween(0.0, bounds.getHeight());
        }
    }

    @Test
    void testAccentProfileRecedesHalfItsHeightAtTheTip() {
        var bounds = AccentShape.accent().getBounds2D();
        var profile = ShapeProfile.innerEdge(AccentShape.accent(), false);

        // The two arms meet at the tip, so the upper boundary has descended to the glyph's vertical
        // centre there. This is the whole reason the wedge clears a tie so much better than its box.
        assertThat(profile.segments().getLast().yOffsetEndSs())
            .describedAs("the wedge's upper edge reaches mid-height at the tip")
            .isCloseTo(bounds.getHeight() / 2.0, within(FLATNESS_TOLERANCE_SS));
    }

    @Test
    void testAccentProfileIsNotMonotone() {
        var profile = ShapeProfile.innerEdge(AccentShape.accent(), false);

        // The round cap at the open end puts the glyph's topmost point slightly right of its
        // leftmost point, so the offset dips to zero before climbing the arm. NoteAttachedStacker
        // relies on that dip: it is what a padded notehead reservation reaches, and thus what keeps
        // an untied accent from creeping toward the note.
        assertThat(offsetAtSs(profile, 0.0))
            .describedAs("the cap's leftmost point sits below the glyph's topmost point")
            .isGreaterThan(offsetAtSs(profile, 0.07));
    }

    @Test
    void testAccentProfileClimbsSteadilyRightOfItsCap() {
        var profile = ShapeProfile.innerEdge(AccentShape.accent(), false);
        var widthSs = AccentShape.accent().getBounds2D().getWidth();
        var previousSs = profile.offsetSs(CAP_END_SS);

        // Right of the cap the edge is the wedge's arm, then its tip: it only ever recedes further
        // from the box. ArticulationStackingTest and NoteAttachedStackerTest lean on this to say the
        // least offset over a support's window is the one at the window's near edge.
        for (var xSs = CAP_END_SS; xSs <= widthSs; xSs += SAMPLE_STEP_SS) {
            var offsetSs = profile.offsetSs(xSs);
            assertThat(offsetSs)
                .describedAs("offset at x = %.3f must not dip below its neighbour".formatted(xSs))
                .isGreaterThanOrEqualTo(previousSs);
            previousSs = offsetSs;
        }
    }

    @Test
    void testAccentProfileIsSymmetricAboutItsHorizontalAxis() {
        // NoteAttachedStacker derives the above- and below-staff profiles independently rather than
        // flipping one, so this is an observation about accent.svg, not a premise of the code.
        var above = ShapeProfile.innerEdge(AccentShape.accent(), true);
        var below = ShapeProfile.innerEdge(AccentShape.accent(), false);
        var widthSs = AccentShape.accent().getBounds2D().getWidth();

        // Skip the outermost sample on each side: where one boundary's flattened outline reaches a
        // hair further in x than the other's, the envelope there is a sliver of the far boundary.
        for (var xSs = SAMPLE_STEP_SS; xSs < widthSs - SAMPLE_STEP_SS; xSs += SAMPLE_STEP_SS) {
            assertThat(offsetAtSs(above, xSs))
                .describedAs("offset at x = %.3f".formatted(xSs))
                .isCloseTo(offsetAtSs(below, xSs), within(MIRROR_TOLERANCE_SS));
        }
    }

    @Test
    void testOuterEdgeIsTheInnerEdgeSeenFromTheOtherSide() {
        // A shape's lower boundary, offsets measured up from its box's bottom, is at once the inner
        // edge of an element above the staff and the outer edge of one below it. ShapeProfile leans
        // on that identity to serve both from one envelope walk, so it is asserted here rather than
        // assumed. Exact, not approximate: it is the same walk, not two that happen to agree.
        var accent = AccentShape.accent();
        var widthSs = accent.getBounds2D().getWidth();

        var innerAbove = ShapeProfile.innerEdge(accent, true);
        var outerBelow = ShapeProfile.outerEdge(accent, false);
        var innerBelow = ShapeProfile.innerEdge(accent, false);
        var outerAbove = ShapeProfile.outerEdge(accent, true);

        for (var xSs = 0.0; xSs <= widthSs; xSs += SAMPLE_STEP_SS) {
            assertThat(offsetAtSs(outerBelow, xSs))
                .describedAs("outer edge below == inner edge above at x = %.3f".formatted(xSs))
                .isCloseTo(offsetAtSs(innerAbove, xSs), within(ZERO_TOLERANCE));

            assertThat(offsetAtSs(outerAbove, xSs))
                .describedAs("outer edge above == inner edge below at x = %.3f".formatted(xSs))
                .isCloseTo(offsetAtSs(innerBelow, xSs), within(ZERO_TOLERANCE));
        }
    }
}
