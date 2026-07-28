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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.jspecify.annotations.Nullable;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.ui.hit.HitResult;
import songscribe.ui.hit.HitTestContext;

/**
 * Unit tests for {@link ElementHitTest}.
 *
 * <p>Tests for {@code hitTestElement} drive the method by mocking {@code ScaleContext.pxToSs}
 * and providing a real {@link Song}/{@link Line} so that {@code isInteractable} behaves
 * correctly without further mocking. The mocked {@link LineComponent} returns a null
 * layout result (causing {@code elementXSs = 0}), a fixed middle-line Y, and the real line.
 *
 * <p>Tests for {@code buildElementHitRect} use a real {@link ElementType} so that the
 * natural-size assertions reference the actual SMuFL-derived values.
 */
class ElementHitTestTest extends UnitTest {

    private MockedStatic<ScaleContext> scaleContextMock;

    // Middle-line Y coordinate fed to all mocked LineComponents, in staff spaces
    private static final double MIDDLE_LINE_Y_SS = 5.0;

    @BeforeEach
    void setUp() {
        scaleContextMock = mockStatic(ScaleContext.class);
    }

    @AfterEach
    void tearDown() {
        scaleContextMock.close();
    }

    // -------------------------------------------------------------------------
    // hitTestElement
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HitTestElement {

        /**
         * Sets up pxToSs as an identity mapping (1 px = 1 ss) so that pixel coordinates
         * fed as the Point argument translate directly to the staff-space values used in
         * the hit rectangle.
         */
        @BeforeEach
        void useIdentityScale() {
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void testPointInsideFirstElementReturnsZero() {
            // Single note at staff position 0; element X = 0 because layout result is null.
            // With identity scale and middleLineY=5.0, the notehead top Y is 5 + topOffset.
            // Place the hit point at the element's natural center so it falls inside the rect.
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(0);
            song.withoutMutationTracking(() -> line.addElement(note));

            var lc = lcFor(line);

            // The hit rect center X is 0 + naturalWidthSs/2; center Y is 5 + topOffset + heightSs/2.
            var type = ElementType.CROTCHET;
            var centerXSs = type.getElementWidthSs() / 2;
            var centerYSs = MIDDLE_LINE_Y_SS + type.getNoteheadTopOffsetSs() + type.getFullElementHeightSs() / 2;

            var result = ElementHitTest.hitTestElement(lc, new Point((int) centerXSs, (int) centerYSs));

            assertThat(result).isEqualTo(0);
        }

        @Test
        void testPointBetweenElementsReturnsNegativeOne() {
            // Two notes at X=0 (width ~1.2 ss each); point placed far to the right misses both.
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            var lc = lcFor(line);

            // Both elements land at X=0 (null layout). A point at x=1000 is far outside both rects.
            var result = ElementHitTest.hitTestElement(lc, new Point(1000, (int) MIDDLE_LINE_Y_SS));

            assertThat(result).isEqualTo(-1);
        }

        @Test
        void testNonInteractableAutoTerminalSkippedAndReturnsNegativeOne() {
            // A new Song already has one FINAL_DOUBLE_BARLINE at index 0 of its initial line,
            // which is the auto-maintained terminal (last element of the last line).
            // hitTestElement must skip it and return -1 even when the point hits its rect.
            var song = new Song();
            var line = song.getLine(0);
            var lc = lcFor(line);

            // Point placed at the terminal's center — should be skipped, returning -1.
            var type = ElementType.FINAL_DOUBLE_BARLINE;
            var centerXSs = type.getElementWidthSs() / 2;
            var centerYSs = MIDDLE_LINE_Y_SS + type.getNoteheadTopOffsetSs() + type.getFullElementHeightSs() / 2;

            var result = ElementHitTest.hitTestElement(lc, new Point((int) centerXSs, (int) centerYSs));

            assertThat(result).isEqualTo(-1);
        }

        @Test
        void testNullLineReturnsNegativeOne() {
            var lc = mock(LineComponent.class);
            when(lc.getLine()).thenReturn(null);

            var result = ElementHitTest.hitTestElement(lc, new Point(0, 0));

            assertThat(result).isEqualTo(-1);
        }
    }

    // -------------------------------------------------------------------------
    // hit
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Hit {

        @BeforeEach
        void useIdentityScale() {
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void testHitReturnsElementHead() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(0);
            song.withoutMutationTracking(() -> line.addElement(note));

            var lc = lcFor(line);

            var type = ElementType.CROTCHET;
            var centerXSs = type.getElementWidthSs() / 2;
            var centerYSs = MIDDLE_LINE_Y_SS + type.getNoteheadTopOffsetSs() + type.getFullElementHeightSs() / 2;
            var pointPx = new Point((int) centerXSs, (int) centerYSs);
            var context = new HitTestContext(pointPx, line, null, MIDDLE_LINE_Y_SS);

            var result = ElementHitTest.hit(lc, context);

            assertThat(result).isEqualTo(new HitResult.ElementHead(0));
        }

        @Test
        void testMissReturnsNull() {
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

            var lc = lcFor(line);

            var pointPx = new Point(1000, (int) MIDDLE_LINE_Y_SS);
            var context = new HitTestContext(pointPx, line, null, MIDDLE_LINE_Y_SS);

            var result = ElementHitTest.hit(lc, context);

            assertThat(result).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // buildElementHitRect
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BuildElementHitRect {

        // A large value so pxToSs(MIN_HIT_SIZE_PX) >> any natural element size,
        // ensuring symmetric expansion will always trigger.
        private static final double LARGE_MIN_HIT_SIZE_SS = 100.0;

        @Test
        void testExpandToMinimumExpandsNarrowElement() {
            // Make minHitSizeSs very large so expansion is guaranteed.
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenReturn(LARGE_MIN_HIT_SIZE_SS);

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(0);

            var lc = lcFor(null); // null line → elementXSs = 0 (via null layoutResult)
            when(lc.getMiddleLineYSs()).thenReturn(0.0);

            var out = new Rectangle2D.Double();
            ElementHitTest.buildElementHitRect(lc, note, out, true);

            var type = ElementType.CROTCHET;
            var naturalWidthSs = type.getElementWidthSs();
            var naturalHeightSs = type.getFullElementHeightSs();
            var xExpansionSs = (LARGE_MIN_HIT_SIZE_SS - naturalWidthSs) / 2;
            var yExpansionSs = (LARGE_MIN_HIT_SIZE_SS - naturalHeightSs) / 2;

            assertThat(out.x).isEqualTo(-xExpansionSs);
            assertThat(out.width).isEqualTo(naturalWidthSs + 2 * xExpansionSs);
            assertThat(out.height).isEqualTo(naturalHeightSs + 2 * yExpansionSs);
        }

        @Test
        void testExpandToMinimumDoesNotExpandElementAtOrAboveThreshold() {
            // Return 0 for pxToSs so minHitSizeSs = 0 — no element can be narrower than 0.
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble())).thenReturn(0.0);

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(0);

            var lc = lcFor(null);
            when(lc.getMiddleLineYSs()).thenReturn(0.0);

            var out = new Rectangle2D.Double();
            ElementHitTest.buildElementHitRect(lc, note, out, true);

            var type = ElementType.CROTCHET;

            // With min = 0, expansion = max(0, negative) = 0 → rect equals natural size
            assertThat(out.x).isEqualTo(0.0);
            assertThat(out.width).isEqualTo(type.getElementWidthSs());
            assertThat(out.height).isEqualTo(type.getFullElementHeightSs());
        }

        @Test
        void testNoExpansionReturnsNaturalRect() {
            // expandToMinimum=false must not call ScaleContext at all (no pxToSs stub needed).
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(0);

            var lc = lcFor(null);
            when(lc.getMiddleLineYSs()).thenReturn(0.0);

            var out = new Rectangle2D.Double();
            ElementHitTest.buildElementHitRect(lc, note, out, false);

            var type = ElementType.CROTCHET;
            var expectedYSs = type.getNoteheadTopOffsetSs(); // middleLineY(0) + spToSs(0) + topOffset

            assertThat(out.x).isEqualTo(0.0);   // elementXSs = 0 (null layout result)
            assertThat(out.y).isEqualTo(expectedYSs);
            assertThat(out.width).isEqualTo(type.getElementWidthSs());
            assertThat(out.height).isEqualTo(type.getFullElementHeightSs());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a mock {@link LineComponent} that returns the given line and a fixed middle-line Y.
     * Layout result is null so {@code elementXSs = 0}.
     */
    private static LineComponent lcFor(@Nullable Line line) {
        var lc = mock(LineComponent.class);
        when(lc.getLine()).thenReturn(line);
        when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);
        when(lc.getLayoutResult()).thenReturn(null);
        return lc;
    }
}
