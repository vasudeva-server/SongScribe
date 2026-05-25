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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import songscribe.UnitTest;

/** Tests factory methods and geometry queries on {@link NoteBounds}. */
class NoteBoundsTest extends UnitTest {

    // --- Sample note head rectangle (x, y, width, height) ---
    private static final double HEAD_X = 10.0;
    private static final double HEAD_Y = 20.0;
    private static final double HEAD_WIDTH = 4.0;
    private static final double HEAD_HEIGHT = 3.0;

    // --- Sample stem-extended rectangle (taller, stem going up) ---
    private static final double STEM_X = 10.0;
    private static final double STEM_Y = 5.0;         // extends above the head
    private static final double STEM_WIDTH = 4.0;
    private static final double STEM_HEIGHT = 18.0;   // covers head + stem above

    // --- Sample articulations rectangle (taller than stem; articulation below head for stem-up) ---
    private static final double ART_X = 10.0;
    private static final double ART_Y = 5.0;
    private static final double ART_WIDTH = 4.0;
    private static final double ART_HEIGHT = 22.0;   // extends below head for articulations

    // --- Separate "full" rectangle for row 40 — deliberately different from head so a
    //     getCenterX/Y regression that reads the wrong rect will fail. ---
    private static final double FULL_X = 2.0;
    private static final double FULL_Y = 1.0;
    private static final double FULL_WIDTH = 30.0;
    private static final double FULL_HEIGHT = 50.0;

    // --- Translation offsets ---
    private static final double TRANSLATE_DX = 7.5;
    private static final double TRANSLATE_DY = -3.0;

    private static final double DELTA = 1e-9;

    private static Rectangle2D headRect() {
        return new Rectangle2D.Double(HEAD_X, HEAD_Y, HEAD_WIDTH, HEAD_HEIGHT);
    }

    private static Rectangle2D stemRect() {
        return new Rectangle2D.Double(STEM_X, STEM_Y, STEM_WIDTH, STEM_HEIGHT);
    }

    private static Rectangle2D artRect() {
        return new Rectangle2D.Double(ART_X, ART_Y, ART_WIDTH, ART_HEIGHT);
    }

    private static Rectangle2D fullRect() {
        return new Rectangle2D.Double(FULL_X, FULL_Y, FULL_WIDTH, FULL_HEIGHT);
    }

    // -----------------------------------------------------------------------
    // Row 33 — headOnly factory
    // -----------------------------------------------------------------------

    @Test
    void testHeadOnlyAllBoundsEqualHeadBounds() {
        var head = headRect();
        var nb = NoteBounds.headOnly(head, true);

        assertThat(nb.noteHeadBounds()).isEqualTo(head);
        assertThat(nb.noteWithStemBounds()).isEqualTo(head);
        assertThat(nb.noteWithArticulationsBounds()).isEqualTo(head);
    }

    // -----------------------------------------------------------------------
    // Row 39 — translate
    // -----------------------------------------------------------------------

    @Test
    void testTranslateShiftsBoundsAndPreservesStemUp() {
        var head = headRect();
        var stem = stemRect();
        var art  = artRect();
        var original = new NoteBounds(head, stem, art, true);

        var translated = original.translate(TRANSLATE_DX, TRANSLATE_DY);

        // All three rectangles must be shifted by (TRANSLATE_DX, TRANSLATE_DY).
        assertThat(translated.noteHeadBounds().getX()).isCloseTo(HEAD_X + TRANSLATE_DX, within(DELTA));
        assertThat(translated.noteHeadBounds().getY()).isCloseTo(HEAD_Y + TRANSLATE_DY, within(DELTA));
        assertThat(translated.noteHeadBounds().getWidth()).isCloseTo(HEAD_WIDTH, within(DELTA));
        assertThat(translated.noteHeadBounds().getHeight()).isCloseTo(HEAD_HEIGHT, within(DELTA));

        assertThat(translated.noteWithStemBounds().getX()).isCloseTo(STEM_X + TRANSLATE_DX, within(DELTA));
        assertThat(translated.noteWithStemBounds().getY()).isCloseTo(STEM_Y + TRANSLATE_DY, within(DELTA));
        assertThat(translated.noteWithStemBounds().getWidth()).isCloseTo(STEM_WIDTH, within(DELTA));
        assertThat(translated.noteWithStemBounds().getHeight()).isCloseTo(STEM_HEIGHT, within(DELTA));

        assertThat(translated.noteWithArticulationsBounds().getX()).isCloseTo(ART_X + TRANSLATE_DX, within(DELTA));
        assertThat(translated.noteWithArticulationsBounds().getY()).isCloseTo(ART_Y + TRANSLATE_DY, within(DELTA));
        assertThat(translated.noteWithArticulationsBounds().getWidth()).isCloseTo(ART_WIDTH, within(DELTA));
        assertThat(translated.noteWithArticulationsBounds().getHeight()).isCloseTo(ART_HEIGHT, within(DELTA));

        // stemUp flag must survive translation.
        assertThat(translated.stemUp()).isTrue();

        // Original must be unmodified (records are immutable, but confirm the constructor did not alias).
        assertThat(original.noteHeadBounds().getX()).isCloseTo(HEAD_X, within(DELTA));
        assertThat(original.noteHeadBounds().getY()).isCloseTo(HEAD_Y, within(DELTA));
    }

    // -----------------------------------------------------------------------
    // Row 34 — withStem factory
    // -----------------------------------------------------------------------

    @Test
    void testWithStemArticulationsBoundsEqualsStemBounds() {
        var head = headRect();
        var stem = stemRect();
        var nb = NoteBounds.withStem(head, stem, true);

        assertThat(nb.noteHeadBounds()).isEqualTo(head);
        assertThat(nb.noteWithStemBounds()).isEqualTo(stem);
        assertThat(nb.noteWithArticulationsBounds()).isEqualTo(stem);
    }

    // -----------------------------------------------------------------------
    // Row 40 — getCenterX / getCenterY read from noteHeadBounds
    // -----------------------------------------------------------------------

    @Nested
    class GetCenterXAndCenterY {

        /**
         * Row 40 — centers are computed from noteHeadBounds, not from the full/articulations bounds.
         *
         * <p>fullRect is deliberately offset and sized differently from headRect, so a regression
         * that reads either the stem or articulations rectangle instead of the head will produce
         * a wrong value and fail.
         */
        @Test
        void testCentersComputedFromHeadBoundsNotFullBounds() {
            var head = headRect();
            var full = fullRect();
            // Use full as both noteWithStemBounds and noteWithArticulationsBounds.
            var nb = new NoteBounds(head, full, full, true);

            double expectedCenterX = head.getCenterX(); // HEAD_X + HEAD_WIDTH / 2
            double expectedCenterY = head.getCenterY(); // HEAD_Y + HEAD_HEIGHT / 2

            assertThat(nb.getCenterX()).isCloseTo(expectedCenterX, within(DELTA));
            assertThat(nb.getCenterY()).isCloseTo(expectedCenterY, within(DELTA));

            // Confirm the full rect's centers differ so the assertions above are non-trivial.
            assertThat(full.getCenterX()).isNotCloseTo(expectedCenterX, within(DELTA));
            assertThat(full.getCenterY()).isNotCloseTo(expectedCenterY, within(DELTA));
        }
    }

    // -----------------------------------------------------------------------
    // Rows 37–38 — getOppositeFromStemBounds
    // -----------------------------------------------------------------------

    @Nested
    class GetOppositeFromStemBounds {

        /**
         * Row 37 — stem up: returns the LOWER half (from headCenterY to artBounds.maxY).
         *
         * <p>Expected: x=ART_X, y=headCenterY, w=ART_WIDTH, h=(artBounds.maxY - headCenterY)
         */
        @Test
        void testStemUpReturnsLowerHalf() {
            var head = headRect();
            var art  = artRect();
            var nb = new NoteBounds(head, art, art, true);

            var result = nb.getOppositeFromStemBounds();

            double headCenterY    = head.getCenterY();
            double artMaxY        = art.getMaxY();
            double expectedHeight = artMaxY - headCenterY;

            assertThat(result.getX()).isCloseTo(ART_X, within(DELTA));
            assertThat(result.getY()).isCloseTo(headCenterY, within(DELTA));
            assertThat(result.getWidth()).isCloseTo(ART_WIDTH, within(DELTA));
            assertThat(result.getHeight()).isCloseTo(expectedHeight, within(DELTA));
        }

        /**
         * Row 38 — stem down: returns the UPPER half (from artBounds.y to headCenterY).
         *
         * <p>Expected: x=ART_X, y=ART_Y, w=ART_WIDTH, h=(headCenterY - ART_Y)
         */
        @Test
        void testStemDownReturnsUpperHalf() {
            var head = headRect();
            var art  = artRect();
            var nb = new NoteBounds(head, art, art, false);

            var result = nb.getOppositeFromStemBounds();

            double headCenterY    = head.getCenterY();
            double expectedHeight = headCenterY - ART_Y;

            assertThat(result.getX()).isCloseTo(ART_X, within(DELTA));
            assertThat(result.getY()).isCloseTo(ART_Y, within(DELTA));
            assertThat(result.getWidth()).isCloseTo(ART_WIDTH, within(DELTA));
            assertThat(result.getHeight()).isCloseTo(expectedHeight, within(DELTA));
        }
    }

    // -----------------------------------------------------------------------
    // Row 41 — getTop / getBottom / getAttachmentTopY / getAttachmentBottomY
    //          all read from noteWithArticulationsBounds
    // -----------------------------------------------------------------------

    /**
     * Each of the four geometry getters must read from noteWithArticulationsBounds, not from
     * noteHeadBounds or noteWithStemBounds.  The three rectangles are deliberately distinct so
     * any wrong-rect regression produces a different value and fails.
     *
     * <p>artRect: y=5, height=22 → maxY=27
     * headRect:  y=20 → different top; stemRect: maxY=23 → different bottom.
     */
    @Test
    void testGetTopReadsArticulationsBoundsY() {
        var nb = new NoteBounds(headRect(), stemRect(), artRect(), true);

        assertThat(nb.getTop()).isCloseTo(ART_Y, within(DELTA));
    }

    @Test
    void testGetBottomReadsArticulationsBoundsMaxY() {
        var nb = new NoteBounds(headRect(), stemRect(), artRect(), true);
        double expectedMaxY = ART_Y + ART_HEIGHT;

        assertThat(nb.getBottom()).isCloseTo(expectedMaxY, within(DELTA));
    }

    @Test
    void testGetAttachmentTopYReadsArticulationsBoundsY() {
        var nb = new NoteBounds(headRect(), stemRect(), artRect(), true);

        assertThat(nb.getAttachmentTopY()).isCloseTo(ART_Y, within(DELTA));
    }

    @Test
    void testGetAttachmentBottomYReadsArticulationsBoundsMaxY() {
        var nb = new NoteBounds(headRect(), stemRect(), artRect(), true);
        double expectedMaxY = ART_Y + ART_HEIGHT;

        assertThat(nb.getAttachmentBottomY()).isCloseTo(expectedMaxY, within(DELTA));
    }

    // -----------------------------------------------------------------------
    // Rows 35–36 — getStemSideBounds
    // -----------------------------------------------------------------------

    @Nested
    class GetStemSideBounds {

        /**
         * Row 35 — stem up: upper half (from artBounds.y to headBounds.centerY).
         *
         * <p>Expected: x=ART_X, y=ART_Y, w=ART_WIDTH, h=(headCenterY - ART_Y)
         */
        @Test
        void testStemUpReturnsUpperHalf() {
            var head = headRect();
            var art  = artRect();
            var nb = new NoteBounds(head, art, art, true);

            var result = nb.getStemSideBounds();

            double headCenterY = head.getCenterY(); // HEAD_Y + HEAD_HEIGHT / 2
            double expectedHeight = headCenterY - ART_Y;

            assertThat(result.getX()).isCloseTo(ART_X, within(DELTA));
            assertThat(result.getY()).isCloseTo(ART_Y, within(DELTA));
            assertThat(result.getWidth()).isCloseTo(ART_WIDTH, within(DELTA));
            assertThat(result.getHeight()).isCloseTo(expectedHeight, within(DELTA));
        }

        /**
         * Row 36 — stem down: lower half (from headBounds.centerY to artBounds.maxY).
         *
         * <p>Expected: x=ART_X, y=headCenterY, w=ART_WIDTH, h=(artBounds.maxY - headCenterY)
         */
        @Test
        void testStemDownReturnsLowerHalf() {
            var head = headRect();
            var art  = artRect();
            var nb = new NoteBounds(head, art, art, false);

            var result = nb.getStemSideBounds();

            double headCenterY  = head.getCenterY();
            double artMaxY      = art.getMaxY();
            double expectedHeight = artMaxY - headCenterY;

            assertThat(result.getX()).isCloseTo(ART_X, within(DELTA));
            assertThat(result.getY()).isCloseTo(headCenterY, within(DELTA));
            assertThat(result.getWidth()).isCloseTo(ART_WIDTH, within(DELTA));
            assertThat(result.getHeight()).isCloseTo(expectedHeight, within(DELTA));
        }
    }
}
