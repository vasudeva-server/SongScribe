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
