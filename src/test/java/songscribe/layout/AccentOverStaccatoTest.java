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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.shape.AccentShape;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Pins the one thing that makes reserving the staccato dot's outline worth doing: an accent stacking
 * outside a round dot seats closer than it would outside the dot's box.
 * <p>
 * LilyPond builds a {@code Script}'s skyline from its stencil's outline
 * ({@code define-grobs.scm} {@code grob::always-vertical-skylines-from-stencil}) — the dot's included
 * — while a {@code NoteHead} keeps the box it inherits from {@code Grob::Grob}. So a script collides
 * with the circle the dot is, not the square it is drawn in.
 * <p>
 * These tests exercise {@link StaffExtents} directly rather than the stacking pipeline, so the two
 * reservations can be compared side by side with nothing else moving.
 */
class AccentOverStaccatoTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 100.0;

    // Well inside the line, so no reservation is clamped at either end.
    private static final double DOT_LEFT_X_SS = 10.0;

    // The dot's outer edge. Arbitrary: every quantity asserted here is a difference of two
    // placements taken against the same edge, so its value cancels.
    private static final double DOT_EDGE_Y_SS = -3.0;

    // LilyPond script.scm: (accent (padding . 0.20)).
    private static final double ACCENT_PADDING_SS = 0.20;

    // LilyPond define-grobs.scm: (Script (horizon-padding . 0.1)).
    private static final double HORIZON_PADDING_SS = 0.10;

    // Bravura draws the dot round to within 0.0006 ss of a true circle. The reserved outline is a
    // 4-chord approximation of that circle, so a breakpoint may sit a flattening tolerance inside it.
    private static final double CIRCLE_TOLERANCE_SS =
        NoteAttachedStacker.STACCATO_OUTLINE_FLATNESS_SS;

    private static final double DOT_WIDTH_SS =
        SMuFLMetadata.requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE).width();

    private static final double ACCENT_WIDTH_SS = AccentShape.accent().getBounds2D().getWidth();

    /** Both glyphs are centred on the notehead, so the dot sits centred within the accent's box. */
    private static final double ACCENT_LEFT_X_SS =
        DOT_LEFT_X_SS - (ACCENT_WIDTH_SS - DOT_WIDTH_SS) / 2.0;

    private static final StaffExtents.Profile ACCENT_INNER_EDGE =
        ShapeProfile.innerEdge(AccentShape.accent(), true);

    /** The very profile the stacker reserves, at the very tolerance it reserves it. */
    private static final StaffExtents.Profile DOT_OUTER_EDGE =
        ShapeProfile.outerEdge(BravuraFont.glyphOutline(SMuFLGlyph.ARTIC_STACCATO_ABOVE), true,
            NoteAttachedStacker.STACCATO_OUTLINE_FLATNESS_SS);

    /** The accent's inner edge when the dot beneath it reserves the flat top of its box. */
    private static double accentEdgeOverFlatDotSs() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySet(true, DOT_LEFT_X_SS, DOT_WIDTH_SS, DOT_EDGE_Y_SS);

        return extents
            .clearance(true, ACCENT_LEFT_X_SS, ACCENT_INNER_EDGE, ACCENT_PADDING_SS,
                HORIZON_PADDING_SS)
            .ySs();
    }

    /** The accent's inner edge when the dot beneath it reserves its round outline. */
    private static double accentEdgeOverRoundDotSs() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySetProfile(true, DOT_LEFT_X_SS, DOT_OUTER_EDGE, DOT_EDGE_Y_SS);

        return extents
            .clearance(true, ACCENT_LEFT_X_SS, ACCENT_INNER_EDGE, ACCENT_PADDING_SS,
                HORIZON_PADDING_SS)
            .ySs();
    }

    /** The slope of the accent's inner edge where the dot's support meets it — one straight segment. */
    private static double accentArmSlopeSs() {
        var insetSs = (ACCENT_WIDTH_SS - DOT_WIDTH_SS) / 2.0;
        var windowStartSs = insetSs - HORIZON_PADDING_SS;

        for (var segment : ACCENT_INNER_EDGE.segments()) {
            if (windowStartSs >= segment.xStartSs() && windowStartSs <= segment.xEndSs()) {
                return segment.slopeSs();
            }
        }

        throw new AssertionError("the accent's inner edge does not span the dot's support window");
    }

    /**
     * The closed form of the gain, derived from LilyPond's placement rule rather than measured from
     * SongScribe's output.
     * <p>
     * {@code Skyline::padded} (skyline.cc) dilates every building of the support by the horizon
     * padding, so the accent's straight arm — slope {@code m} — descends onto the dot's dilated
     * circle. The binding x is where the circle's own slope reaches {@code m}, at
     * {@code v = r(1 − m/√(1+m²))} from its apex; there the circle has dropped
     * {@code r(1 − 1/√(1+m²))} and the arm has climbed {@code m·v}. The two sum to a gain
     * independent of both the horizon padding and the dot's position:
     * <pre>
     *   gain = r · (1 + m − √(1 + m²))
     * </pre>
     */
    private static double closedFormGainSs() {
        var radiusSs = DOT_WIDTH_SS / 2.0;
        var slopeSs = accentArmSlopeSs();

        return radiusSs * (1.0 + slopeSs - Math.hypot(1.0, slopeSs));
    }

    @Test
    void testStaccatoDotOutlineIsACircle() {
        var radiusSs = DOT_WIDTH_SS / 2.0;

        // Sample the reserved outline at each segment breakpoint and compare it against the circle
        // the dot is meant to be. The offset is measured inward from the box top, so at the apex
        // (x = r) it is 0 and at either edge it is r.
        for (var segment : DOT_OUTER_EDGE.segments()) {
            var xSs = segment.xStartSs();
            var dxSs = xSs - radiusSs;
            var expectedOffsetSs = radiusSs - Math.sqrt(radiusSs * radiusSs - dxSs * dxSs);

            assertThat(segment.yOffsetStartSs())
                .describedAs("dot outline at x = %s must lie on a circle of radius %s", xSs, radiusSs)
                .isCloseTo(expectedOffsetSs, within(CIRCLE_TOLERANCE_SS));
        }
    }

    @Test
    void testRoundDotIsReservedEntirelyInsideItsBox() {
        // Every chord of the outer edge lies at or inside the flat box top, so reserving the outline
        // can only ever give space back. This is what lets the accent move closer and nothing else
        // move further away.
        for (var segment : DOT_OUTER_EDGE.segments()) {
            assertThat(segment.yOffsetStartSs()).isGreaterThanOrEqualTo(0.0);
            assertThat(segment.yOffsetEndSs()).isGreaterThanOrEqualTo(0.0);
        }

        assertThat(accentEdgeOverRoundDotSs())
            .describedAs("a round dot may only let the accent descend, never push it out")
            .isGreaterThan(accentEdgeOverFlatDotSs());
    }

    /**
     * The chords lie inside the circle, so the reserved dot is always a little shorter than the real
     * one and the accent always seats a little closer than the closed form. The error is one-sided
     * and bounded by the flattening tolerance, which makes the assertion a derived bracket rather
     * than a recorded number: {@code closedForm < gain < closedForm + flatness}.
     */
    private static void assertGainWithinFlatteningBudget(double gainSs, String description) {
        var closedFormSs = closedFormGainSs();

        assertThat(gainSs)
            .describedAs("%s: chords under-reserve, so the accent seats at least as close", description)
            .isGreaterThan(closedFormSs);

        assertThat(gainSs)
            .describedAs("%s: and no closer than the flattening tolerance allows", description)
            .isLessThan(closedFormSs + NoteAttachedStacker.STACCATO_OUTLINE_FLATNESS_SS);
    }

    @Test
    void testAccentSeatsCloserOverARoundDotThanOverItsBox() {
        // Fails outright if the dot is reserved flat: the gain is then exactly zero, well below the
        // closed form's 0.038 ss.
        assertGainWithinFlatteningBudget(
            accentEdgeOverRoundDotSs() - accentEdgeOverFlatDotSs(),
            "reserving the dot's round outline must seat the accent closer");
    }

    @Test
    void testRoundDotGainDoesNotDependOnTheHorizonPadding() {
        // The closed form cancels the horizon padding: dilating the circle slides the contact point
        // along the arm by exactly as much as it raises the support. A gain that moved with the
        // padding would mean the binding had left the arm.
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySetProfile(true, DOT_LEFT_X_SS, DOT_OUTER_EDGE, DOT_EDGE_Y_SS);

        var flatExtents = new StaffExtents(LINE_WIDTH_SS);
        flatExtents.ySet(true, DOT_LEFT_X_SS, DOT_WIDTH_SS, DOT_EDGE_Y_SS);

        var widerHorizonSs = HORIZON_PADDING_SS * 2;

        var roundSs = extents
            .clearance(true, ACCENT_LEFT_X_SS, ACCENT_INNER_EDGE, ACCENT_PADDING_SS, widerHorizonSs)
            .ySs();
        var flatSs = flatExtents
            .clearance(true, ACCENT_LEFT_X_SS, ACCENT_INNER_EDGE, ACCENT_PADDING_SS, widerHorizonSs)
            .ySs();

        assertGainWithinFlatteningBudget(
            roundSs - flatSs, "the gain must not move when the horizon padding does");
    }

    /**
     * The converse of {@link #testAccentSeatsCloserOverARoundDotThanOverItsBox}, and the reason the
     * accent reserves a flat box rather than its wedge.
     * <p>
     * A flat inner edge that spans a support binds at that support's extreme, so the support's shape
     * is invisible to it. The accent's outer edge peaks at its <em>left</em> end and recedes 0.42 ss
     * by its tip; every element that stacks above an accent — dynamic, hairpin, tuplet bracket — is
     * flat-bottomed and centred on the note column, so it covers that peak and reads box height.
     * Reserving the accent's wedge would move none of them.
     * <p>
     * Note the exactness: this is not "close to zero", it is zero. The same theorem makes a
     * <em>box</em> accent gain nothing from a <em>round</em> dot, which is what LilyPond 2.26 shows
     * when only the accent's stencil is boxified.
     */
    @Test
    void testAFlatElementAboveTheAccentCannotSeeItsWedge() {
        var accentWidthSs = ACCENT_WIDTH_SS;
        var accentTopYSs = DOT_EDGE_Y_SS;
        var accentOuterEdge = ShapeProfile.outerEdge(AccentShape.accent(), true);

        // Wider than the accent and centred on it: a dynamic, a hairpin, a tuplet bracket.
        var elementWidthSs = accentWidthSs + 1.0;
        var elementXSs = DOT_LEFT_X_SS - (elementWidthSs - accentWidthSs) / 2.0;
        var elementProfile = StaffExtents.Profile.flat(elementWidthSs);

        var boxed = new StaffExtents(LINE_WIDTH_SS);
        boxed.ySet(true, DOT_LEFT_X_SS, accentWidthSs, accentTopYSs);

        var wedged = new StaffExtents(LINE_WIDTH_SS);
        wedged.ySetProfile(true, DOT_LEFT_X_SS, accentOuterEdge, accentTopYSs);

        // The structural horizon is the widest any of these elements uses.
        var horizonSs = 0.75;

        assertThat(wedged.clearance(true, elementXSs, elementProfile, ACCENT_PADDING_SS, horizonSs).ySs())
            .describedAs("a flat, centred element binds at the accent's apex either way")
            .isEqualTo(
                boxed.clearance(true, elementXSs, elementProfile, ACCENT_PADDING_SS, horizonSs).ySs());
    }

    @Test
    void testDotIsReservedAsFourChords() {
        // The count is the whole cost: every chord is a reservation the rest of the line is scanned
        // against. Eight would move the accent 0.0056 ss and cost 3.6 us more per line.
        assertThat(DOT_OUTER_EDGE.segments())
            .describedAs("the dot's reserved outline must stay cheap")
            .hasSize(4);
    }
}
