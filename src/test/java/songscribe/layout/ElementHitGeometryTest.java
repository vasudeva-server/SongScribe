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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mockStatic;

import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;

/**
 * Unit tests for {@link ElementHitGeometry}.
 *
 * <p>One function serves two callers with opposite needs — the hit registry wants a rect a
 * user can realistically click, the rubber band wants the element's true extent — so what is
 * pinned here is the difference the {@code expandToMinimum} flag makes. A real
 * {@link ElementType} is used so the natural-size assertions reference the actual
 * SMuFL-derived values, and {@link ScaleContext} is stubbed so the minimum hit size is a
 * number the test chose rather than one it has to re-derive.
 */
class ElementHitGeometryTest extends UnitTest {

    /**
     * A stubbed {@code pxToSs} result far larger than any element's natural size, so
     * expansion is guaranteed to trigger.
     */
    private static final double LARGE_MIN_HIT_SIZE_SS = 100.0;

    /** A stubbed {@code pxToSs} result no element can be smaller than, so nothing expands. */
    private static final double NO_MIN_HIT_SIZE_SS = 0.0;

    /** Column origin X of the element under test, in line-local staff spaces. */
    private static final double ELEMENT_X_SS = 0.0;

    private MockedStatic<ScaleContext> scaleContextMock;

    @BeforeEach
    void setUp() {
        scaleContextMock = mockStatic(ScaleContext.class);
    }

    @AfterEach
    void tearDown() {
        scaleContextMock.close();
    }

    private void stubMinHitSizeSs(double minHitSizeSs) {
        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble())).thenReturn(minHitSizeSs);
    }

    /** A crotchet on the middle line, so its layout-space Y is its notehead top offset alone. */
    private static StaffElement middleLineCrotchet() {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(0);
        return note;
    }

    @Test
    void testExpandToMinimumExpandsANarrowElementSymmetrically() {
        stubMinHitSizeSs(LARGE_MIN_HIT_SIZE_SS);

        var out = new Rectangle2D.Double();
        ElementHitGeometry.elementHitRectSs(ELEMENT_X_SS, middleLineCrotchet(), out, true);

        var naturalWidthSs = ElementType.CROTCHET.getElementWidthSs();
        var naturalHeightSs = ElementType.CROTCHET.getFullElementHeightSs();
        var xExpansionSs = (LARGE_MIN_HIT_SIZE_SS - naturalWidthSs) / 2;
        var yExpansionSs = (LARGE_MIN_HIT_SIZE_SS - naturalHeightSs) / 2;

        assertThat(out.x)
            .as("expansion is split evenly on both sides, so the rect stays centered")
            .isEqualTo(ELEMENT_X_SS - xExpansionSs);
        assertThat(out.width).isEqualTo(naturalWidthSs + (2 * xExpansionSs));
        assertThat(out.height).isEqualTo(naturalHeightSs + (2 * yExpansionSs));
    }

    @Test
    void testExpandToMinimumLeavesAnElementAtOrAboveTheMinimumAlone() {
        stubMinHitSizeSs(NO_MIN_HIT_SIZE_SS);

        var out = new Rectangle2D.Double();
        ElementHitGeometry.elementHitRectSs(ELEMENT_X_SS, middleLineCrotchet(), out, true);

        assertThat(out.x).isEqualTo(ELEMENT_X_SS);
        assertThat(out.width).isEqualTo(ElementType.CROTCHET.getElementWidthSs());
        assertThat(out.height).isEqualTo(ElementType.CROTCHET.getFullElementHeightSs());
    }

    /**
     * The unexpanded rect is what the rubber band intersects against, so it must be the
     * element's drawn extent and nothing more — and it must not consult {@link ScaleContext}
     * at all, which is why no minimum is stubbed here.
     */
    @Test
    void testWithoutExpansionTheRectIsTheElementsNaturalExtent() {
        var out = new Rectangle2D.Double();
        ElementHitGeometry.elementHitRectSs(ELEMENT_X_SS, middleLineCrotchet(), out, false);

        assertThat(out.x).isEqualTo(ELEMENT_X_SS);
        assertThat(out.y)
            .as("layout space puts the midline at Y = 0, so a middle-line note's Y is its top offset")
            .isEqualTo(ElementType.CROTCHET.getNoteheadTopOffsetSs());
        assertThat(out.width).isEqualTo(ElementType.CROTCHET.getElementWidthSs());
        assertThat(out.height).isEqualTo(ElementType.CROTCHET.getFullElementHeightSs());
    }
}
