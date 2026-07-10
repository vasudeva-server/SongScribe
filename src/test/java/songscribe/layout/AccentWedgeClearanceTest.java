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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tie;
import songscribe.engraving.Staff;
import songscribe.font.DocumentFonts;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.smufl.SMuFLMetadata;

/**
 * The accent clears a tie arc by its own sloping wedge, not by the corners of its bounding box.
 * <p>
 * LilyPond builds a {@code Script}'s skyline from the stencil outline (define-grobs.scm,
 * {@code always-vertical-skylines-from-stencil}), so its accent meets a tie along the arm of the
 * {@code >} rather than along a rectangle the glyph only touches at two corners. Driving LilyPond
 * 2.24 with {@code e'4-> ~ e'4-.} and swapping the accent's stencil for a solid box of the identical
 * bounding box reproduces the bounding-box answer exactly, which is what pinned this as the cause:
 *
 * <pre>
 *   stencil   untied top   tied top    push
 *   wedge       -2.7450     -3.0307    0.2857
 *   box         -2.7450     -3.3752    0.6302     (ratio 0.453)
 * </pre>
 *
 * Both engines agree that an <em>untied</em> accent does not move at all: horizon padding widens the
 * notehead's skyline (skyline.cc {@code internal_distance} pads {@code dim}, the support) until it
 * reaches the wedge's zero-offset left cap, so the wedge and the box seat identically.
 * <p>
 * Uses the full layout pipeline on purpose — {@code NoteAttachedStackerTest}'s ties are built from
 * {@code flatTieLayout}, whose control points share one Y, and a flat "arc" cannot tell a sloped
 * reservation from a flat one.
 */
@SuppressWarnings({"DataFlowIssue", "NullAway"})
class AccentWedgeClearanceTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 1e-9;

    // The bottom staff line: stem up, so the scripts sit below the staff, and far enough out that
    // the notehead — not the staff-padding clamp — is what the untied accent rests on. A note
    // further inside the staff would be clamp-bound, and the clamp, not the tie, would decide.
    private static final int BOTTOM_STAFF_LINE_SP = 4;

    // LilyPond 2.24, e'4-> ~ e'4-. : the wedge accent's push against the tie arc, and the push the
    // same accent takes once its stencil is swapped for a solid box of its own bounding box.
    private static final double LILYPOND_WEDGE_PUSH_SS = 0.2857;
    private static final double LILYPOND_BOX_PUSH_SS = 0.6302;

    // What this very case yields when the accent stacks as its bounding box — the behaviour before
    // ACCENT_PROFILE existed, reproducible by passing Profile.flat(widthSs) at the accent's
    // placeAndReserveClamped call. LilyPond driven with a box stencil lands within 1.8% of it, which
    // is what identified the bounding box — not the tie, not the padding — as the culprit.
    private static final double FLAT_BOX_PUSH_SS = 0.6190;

    // The wedge must recover at least half of what the box gave away. A guard, not a measurement:
    // the flat-box regression scores 1.0 here and cannot squeak past.
    private static final double MAX_FRACTION_OF_BOX_PUSH = 0.5;

    // Our wedge is 1.480 x 0.843 ss against Feta's 1.500 x 0.840 — the same glyph, aspect within
    // 1.5% — so the absolute push transfers between engines, and the wedge/box ratio more tightly
    // still, since the ratio divides out the tie geometry the two engines do not share exactly.
    private static final double ABSOLUTE_PUSH_TOLERANCE_SS = 0.02;
    private static final double PUSH_RATIO_TOLERANCE = 0.01;

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var hyphenWidthSs = ScaleContext.textWidthSs(lyricsFont, "-");
        var spaceWidthSs = ScaleContext.textWidthSs(lyricsFont, " ");
        var metrics = new LyricRenderMetrics(
            lyricsFont, ScaleContext.scaleFont(lyricsFont), hyphenWidthSs, spaceWidthSs);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    /**
     * Lays out {@code e'4-> ~ e'4-.} — the accent alone on the first note, so nothing but the
     * notehead and the arc lies under it — and returns the accent's top Y.
     */
    private static double accentTopYSs(boolean tied) {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(BOTTOM_STAFF_LINE_SP);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(BOTTOM_STAFF_LINE_SP);
        line.addElement(note1);
        line.addElement(note2);

        var accent = new Articulation(ArticulationType.ACCENT);
        note1.addArticulation(accent);
        note2.addArticulation(new Articulation(ArticulationType.STACCATO));

        if (tied) {
            line.addRangeElement(new Tie(note1, note2));
        }

        var result = require(engine().layout(line), "LayoutResult");
        return require(result.getDecorationLayout(accent), "accent layout").ySs();
    }

    /**
     * The Y the accent's top must sit at when only the notehead supports it: the notehead's bottom,
     * one padding clear. The note is stem-up, so the stem reserves nothing below it.
     */
    private static double noteheadRestYSs() {
        return Staff.spToSs(BOTTOM_STAFF_LINE_SP)
            + SMuFLMetadata.noteHeadHeightSs() / 2.0
            + NoteAttachedStacker.ACCENT_PADDING_SS;
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    @Test
    void testUntiedAccentRestsOnTheNoteheadRegardlessOfItsSlopingEdge() {
        // Horizon padding widens the notehead's reservation past the wedge's zero-offset cap, so the
        // sloping inner edge buys the accent nothing here and it seats exactly where a box would.
        assertThat(accentTopYSs(false))
            .describedAs("an untied accent rests on the notehead, one ACCENT_PADDING_SS clear")
            .isCloseTo(noteheadRestYSs(), within(TOLERANCE));
    }

    @Test
    void testTiedAccentIsPushedOutwardButFarLessThanItsBoundingBoxWouldBe() {
        var push = accentTopYSs(true) - accentTopYSs(false);

        assertThat(push)
            .describedAs("a centre-attached tie covers the notehead, so it must push the accent out")
            .isGreaterThan(0.0);

        assertThat(push)
            .describedAs("the wedge must clear the arc along its sloping arm, not along the corners "
                + "of a box it touches at two points")
            .isLessThan(MAX_FRACTION_OF_BOX_PUSH * FLAT_BOX_PUSH_SS);
    }

    @Test
    void testTiedAccentPushMatchesLilyPond() {
        var push = accentTopYSs(true) - accentTopYSs(false);

        assertThat(push)
            .describedAs("LilyPond 2.24 pushes its wedge accent %.4f ss against the same arc"
                .formatted(LILYPOND_WEDGE_PUSH_SS))
            .isCloseTo(LILYPOND_WEDGE_PUSH_SS, within(ABSOLUTE_PUSH_TOLERANCE_SS));

        // Dividing by each engine's own box push cancels the tie geometry and the font metrics they
        // do not share, leaving the wedge's shape as the only thing under test.
        assertThat(push / FLAT_BOX_PUSH_SS)
            .describedAs("wedge/box push ratio must match LilyPond's %.4f"
                .formatted(LILYPOND_WEDGE_PUSH_SS / LILYPOND_BOX_PUSH_SS))
            .isCloseTo(LILYPOND_WEDGE_PUSH_SS / LILYPOND_BOX_PUSH_SS, within(PUSH_RATIO_TOLERANCE));
    }
}
