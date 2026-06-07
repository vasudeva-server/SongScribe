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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Line2D;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.layout.LayoutResult;

class DynamicsRendererTest extends UnitTest {

    // A hairpin with a clear left, top, height, and width so we can compute
    // all expected endpoint coordinates from first principles.
    private static final double HAIRPIN_X_SS = 2.0;
    private static final double HAIRPIN_LAYOUT_Y_SS = -1.0;  // layout-space Y
    private static final double HAIRPIN_WIDTH_SS = 8.0;
    private static final double HAIRPIN_HEIGHT_SS = 2.0;

    private static LayoutResult.DecorationLayout hairpinLayout() {
        return new LayoutResult.DecorationLayout(
            HAIRPIN_X_SS, HAIRPIN_LAYOUT_Y_SS, HAIRPIN_WIDTH_SS, HAIRPIN_HEIGHT_SS, 0.0);
    }

    // ==========================================================================
    // computeHairpinLines — crescendo vs diminuendo endpoints (row 40)
    // ==========================================================================

    @Test
    void testComputeHairpinLinesCrescendoStartsAtMiddleY() {
        // Crescendo: both lines originate at the middle Y of the left edge.
        // middleYSs = topYSs + height/2
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        // middleLineYSs=0 → topYSs = 0 + HAIRPIN_LAYOUT_Y_SS
        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var middleYSs = topYSs + HAIRPIN_HEIGHT_SS / 2.0;

        var lines = DynamicsRenderer.computeHairpinLines(layout, true, invariants);

        assertThat(lines).hasSize(2);
        assertThat(lines[0].y1).isEqualTo(middleYSs);
        assertThat(lines[1].y1).isEqualTo(middleYSs);
    }

    @Test
    void testComputeHairpinLinesCrescendoEndsAtTopAndBottomY() {
        // Crescendo: upper line ends at topYSs, lower line ends at bottomYSs.
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var bottomYSs = topYSs + HAIRPIN_HEIGHT_SS;

        var lines = DynamicsRenderer.computeHairpinLines(layout, true, invariants);

        assertThat(lines[0].y2).isEqualTo(topYSs);
        assertThat(lines[1].y2).isEqualTo(bottomYSs);
    }

    @Test
    void testComputeHairpinLinesDiminuendoEndsAtMiddleY() {
        // Diminuendo: both lines converge at the middle Y of the right edge.
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var middleYSs = topYSs + HAIRPIN_HEIGHT_SS / 2.0;

        var lines = DynamicsRenderer.computeHairpinLines(layout, false, invariants);

        assertThat(lines).hasSize(2);
        assertThat(lines[0].y2).isEqualTo(middleYSs);
        assertThat(lines[1].y2).isEqualTo(middleYSs);
    }

    @Test
    void testComputeHairpinLinesDiminuendoStartsAtTopAndBottomY() {
        // Diminuendo: upper line starts at topYSs, lower line starts at bottomYSs.
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var bottomYSs = topYSs + HAIRPIN_HEIGHT_SS;

        var lines = DynamicsRenderer.computeHairpinLines(layout, false, invariants);

        assertThat(lines[0].y1).isEqualTo(topYSs);
        assertThat(lines[1].y1).isEqualTo(bottomYSs);
    }

    @Test
    void testComputeHairpinLinesBothTypesShareSameXCoordinates() {
        // Both crescendo and diminuendo span the same X range (x1 to x2 = x1 + width).
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();
        var expectedX2 = HAIRPIN_X_SS + HAIRPIN_WIDTH_SS;

        var crescendoLines = DynamicsRenderer.computeHairpinLines(layout, true, invariants);
        var diminuendoLines = DynamicsRenderer.computeHairpinLines(layout, false, invariants);

        for (Line2D.Double line : crescendoLines) {
            assertThat(line.x1).isEqualTo(HAIRPIN_X_SS);
            assertThat(line.x2).isEqualTo(expectedX2);
        }

        for (Line2D.Double line : diminuendoLines) {
            assertThat(line.x1).isEqualTo(HAIRPIN_X_SS);
            assertThat(line.x2).isEqualTo(expectedX2);
        }
    }

    @Test
    void testComputeHairpinLinesCrescendoAndDiminuendoEndpointsAreMirrored() {
        // The two shapes are mirror images: crescendo opens right, diminuendo opens left.
        // crescendo line[0]: (x1,mid)→(x2,top)  vs  diminuendo line[0]: (x1,top)→(x2,mid)
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var crescendoLines = DynamicsRenderer.computeHairpinLines(layout, true, invariants);
        var diminuendoLines = DynamicsRenderer.computeHairpinLines(layout, false, invariants);

        // Upper line: crescendo starts at middle, diminuendo starts at top (differs)
        assertThat(crescendoLines[0].y1).isNotEqualTo(diminuendoLines[0].y1);

        // Upper line: crescendo ends at top, diminuendo ends at middle (differs)
        assertThat(crescendoLines[0].y2).isNotEqualTo(diminuendoLines[0].y2);

        // The start of crescendo == the end of diminuendo (mirror symmetry)
        assertThat(crescendoLines[0].y1).isEqualTo(diminuendoLines[0].y2);
        assertThat(crescendoLines[0].y2).isEqualTo(diminuendoLines[0].y1);
    }
}
