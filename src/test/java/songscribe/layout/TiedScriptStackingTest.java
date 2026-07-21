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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.Tie;
import songscribe.engraving.Staff;
import songscribe.layout.stacking.StackingUtils;
import songscribe.font.DocumentFonts;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.smufl.SMuFLMetadata;

/**
 * How a tie arc reshapes the scripts stacked over the notes it joins, driven through the full layout
 * pipeline.
 * <p>
 * These two questions share a fixture — the real {@code LayoutEngine} laying out a tied pair — but ask
 * different things, so they are separate {@link Nested} groups over one {@link #engine()} helper. The
 * pipeline is deliberate: {@code NoteAttachedStackerTest}'s ties are built from {@code flatTieLayout},
 * whose control points share one Y, and a flat "arc" can neither slope onto an accent's wedge
 * ({@link AccentWedgeClearance}) nor tell a reservation that tracks the arc from one stamped across
 * the notehead ({@link AttachmentModeIndependence}).
 */
@SuppressWarnings({"DataFlowIssue", "NullAway"})
class TiedScriptStackingTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var metrics = LyricRenderMetrics.forFont(lyricsFont);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    /**
     * The accent clears a tie arc by its own sloping wedge, not by the corners of its bounding box.
     * <p>
     * LilyPond builds a {@code Script}'s skyline from the stencil outline (define-grobs.scm,
     * {@code always-vertical-skylines-from-stencil}), so its accent meets a tie along the arm of the
     * {@code >} rather than along a rectangle the glyph only touches at two corners. Driving LilyPond
     * 2.24 with {@code e'4-> ~ e'4-.} and swapping the accent's stencil for a solid box of the
     * identical bounding box reproduces the bounding-box answer exactly, which is what pinned this as
     * the cause:
     *
     * <pre>
     *   stencil   untied top   tied top    push
     *   wedge       -2.7450     -3.0307    0.2857
     *   box         -2.7450     -3.3752    0.6302     (ratio 0.453)
     * </pre>
     *
     * Both engines agree that an <em>untied</em> accent does not move at all: horizon padding widens
     * the notehead's skyline (skyline.cc {@code internal_distance} pads {@code dim}, the support) until
     * it reaches the wedge's zero-offset left cap, so the wedge and the box seat identically.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AccentWedgeClearance {

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
        // placeAndReserveClamped call. LilyPond driven with a box stencil lands within 3.4% of it,
        // which is what identified the bounding box — not the tie, not the padding — as the culprit.
        private static final double FLAT_BOX_PUSH_SS = 0.6091;

        // The wedge must recover at least half of what the box gave away. A guard, not a measurement:
        // the flat-box regression scores 1.0 here and cannot squeak past.
        private static final double MAX_FRACTION_OF_BOX_PUSH = 0.5;

        // Our wedge is 1.430 x 0.815 ss against Feta's 1.500 x 0.840 — the same glyph, narrower than
        // before (#580) — so the absolute push still transfers between engines, but the wedge/box
        // ratio, which divides out the tie geometry the two engines do not share exactly, now needs a
        // wider berth: narrowing the glyph shrank the box baseline faster than the wedge's own push.
        private static final double ABSOLUTE_PUSH_TOLERANCE_SS = 0.02;
        private static final double PUSH_RATIO_TOLERANCE = 0.019;

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
         * The Y the accent's top must sit at when only the notehead supports it: the notehead's
         * bottom, one padding clear. The note is stem-up, so the stem reserves nothing below it.
         */
        private static double noteheadRestYSs() {
            return Staff.spToSs(BOTTOM_STAFF_LINE_SP)
                + SMuFLMetadata.noteHeadHeightSs() / 2.0
                + NoteAttachedStacker.ACCENT_PADDING_SS;
        }

        @Test
        void testUntiedAccentRestsOnTheNoteheadRegardlessOfItsSlopingEdge() {
            // Horizon padding widens the notehead's reservation past the wedge's zero-offset cap, so
            // the sloping inner edge buys the accent nothing here and it seats exactly where a box
            // would.
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

    /**
     * A tie moves a script only where the arc physically overlaps it, exactly as LilyPond's
     * {@code Skyline::distance} does — never merely by existing.
     * <p>
     * Both attachment modes are pinned, because they are the two halves of one rule. LilyPond's
     * {@code Script_engraver::acknowledge_tie} adds every tie as a script side-support, yet the tie
     * changes nothing for an edge-attached tie (which begins a {@code NOTE_HEAD_GAP_SS} beyond the
     * notehead, clear of the script) and pushes hard for a centre-attached one (whose endpoint recedes
     * to the notehead centre, landing on top of the script). Verified against LilyPond 2.24: tied and
     * untied {@code d'}, {@code g'} place their accent at staff-position -8.289 and -6.399
     * respectively, unchanged; centre-attached {@code a'} moves its staccato by a full staff space.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AttachmentModeIndependence {

        private static final double TOLERANCE = 0.001;

        // A ledger line below the staff: the tie seats within the head box, so its endpoints attach at
        // the notehead's facing edge — clear of the scripts (LayoutEngine.tieSeatSs edge branch).
        // Chosen below the staff so the scripts clear the staff-padding clamp and the tie is the only
        // support that can move them; a within-staff note would pass even with a phantom reservation,
        // because the clamp, not the tie, would be deciding.
        private static final int EDGE_ATTACH_SP = 6;

        // A space note whose arc-side row is a staff line: the seat is pushed past the head box, so the
        // endpoints recede to the notehead centre and the arc covers the scripts (centre attach).
        private static final int CENTRE_ATTACH_SP = 1;

        // Below-staff scripts (sp > 0 → stem up) are pushed to larger Y, since Y increases downward.
        //
        // The centre-attached tie lifts the dot clear of the notehead, and quantize-position then
        // snaps the dot's centre outward to the next off-line space — so the push is exactly one staff
        // space, not the arc's own depth. Matches LilyPond 2.24, which moves the same staccato by a
        // full space.
        private static final double CENTRE_ATTACH_STACCATO_PUSH_SS = 1.0;

        // Untied at this staff position the dot still sits inside the staff, so its outer edge never
        // reaches the accent: the staff clamp — the bottom line's ink edge plus the script's
        // staff-padding — is what holds the accent out. This is why the tie moves the accent less
        // than it moves the dot.
        private static final double UNTIED_ACCENT_TOP_YSS =
            StackingUtils.STAFF_BOT_INK_Y_SS + NoteAttachedStacker.SCRIPT_STAFF_PADDING_SS;

        private static final double STACCATO_HEIGHT_SS =
            new Articulation(ArticulationType.STACCATO).getContentHeightSs();

        private record ScriptYSs(double staccatoYSs, double accentYSs) {}

        /** Lays out two same-pitch notes, the first carrying a staccato and an accent. */
        private static ScriptYSs scriptYSs(int staffPosition, boolean tied) {
            var line = detachedLine();
            var note1 = ElementType.CROTCHET.newInstance();
            note1.setStaffPosition(staffPosition);
            var note2 = ElementType.CROTCHET.newInstance();
            note2.setStaffPosition(staffPosition);
            line.addElement(note1);
            line.addElement(note2);

            var staccato = new Articulation(ArticulationType.STACCATO);
            var accent = new Articulation(ArticulationType.ACCENT);
            note1.addArticulation(staccato);
            note1.addArticulation(accent);

            if (tied) {
                line.addRangeElement(new Tie(note1, note2));
            }

            var result = require(engine().layout(line), "LayoutResult");
            var staccatoLayout = require(result.getDecorationLayout(staccato), "staccato layout");
            var accentLayout = require(result.getDecorationLayout(accent), "accent layout");

            return new ScriptYSs(staccatoLayout.ySs(), accentLayout.ySs());
        }

        @Test
        void testEdgeAttachedTieLeavesScriptsWhereTheyWouldSitUntied() {
            var untied = scriptYSs(EDGE_ATTACH_SP, false);
            var tied = scriptYSs(EDGE_ATTACH_SP, true);

            assertThat(tied.staccatoYSs())
                .describedAs("edge-attached tie clears the notehead, so the staccato must not move")
                .isCloseTo(untied.staccatoYSs(), within(TOLERANCE));

            assertThat(tied.accentYSs())
                .describedAs("edge-attached tie clears the notehead, so the accent must not move")
                .isCloseTo(untied.accentYSs(), within(TOLERANCE));
        }

        @Test
        void testCentreAttachedTiePushesScriptsOutward() {
            var untied = scriptYSs(CENTRE_ATTACH_SP, false);
            var tied = scriptYSs(CENTRE_ATTACH_SP, true);

            assertThat(tied.staccatoYSs() - untied.staccatoYSs())
                .describedAs("centre-attached tie covers the notehead, pushing the staccato a full space")
                .isCloseTo(CENTRE_ATTACH_STACCATO_PUSH_SS, within(TOLERANCE));

            assertThat(untied.accentYSs())
                .describedAs("untied, the dot stays within the staff, so the staff clamp seats the accent")
                .isCloseTo(UNTIED_ACCENT_TOP_YSS, within(TOLERANCE));

            // Once the tie has driven the dot out of the staff, the dot — not the clamp — seats the
            // accent. The gap between the dot's box and the accent's box is *less* than
            // ACCENT_PADDING_SS, because the padding separates the two outlines (the dot's circle, the
            // accent's wedge), not the corners of the boxes that contain them. Reserving boxes would
            // force the full padding.
            var tiedDotOuterEdgeYSs = tied.staccatoYSs() + STACCATO_HEIGHT_SS;

            assertThat(tied.accentYSs())
                .describedAs("tied, the accent seats on the dot's outline, closer than its box allows")
                .isGreaterThan(tiedDotOuterEdgeYSs)
                .isLessThan(tiedDotOuterEdgeYSs + NoteAttachedStacker.ACCENT_PADDING_SS);
        }
    }
}
