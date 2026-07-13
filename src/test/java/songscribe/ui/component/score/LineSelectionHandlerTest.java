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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.layout.LayoutResult;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.SlideRenderer;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link LineSelectionHandler}.
 *
 * <p>{@code hitTest} tests mock {@link ElementHitTest} and {@link SlideRenderer} to control
 * which branch is taken without real geometry. {@code calculateLineSelectionFromDrag} is tested
 * through {@link LineSelectionHandler#handleDrag} (its only caller), using a real
 * {@link Song}/{@link Line} and an identity {@link ScaleContext} so the drag-rect/element-rect
 * intersection can be verified numerically.
 */
class LineSelectionHandlerTest extends UnitTest {

    private MockedStatic<ElementHitTest> elementHitTestMock;
    private MockedStatic<SlideRenderer> slideRendererMock;
    private MockedStatic<ScaleContext> scaleContextMock;

    private LineComponent lc;
    private ScoreView mockScoreView;
    private LineSelectionHandler handler;

    @BeforeEach
    void setUp() {
        elementHitTestMock = mockStatic(ElementHitTest.class);
        slideRendererMock = mockStatic(SlideRenderer.class);
        scaleContextMock = mockStatic(ScaleContext.class);

        lc = mock(LineComponent.class);
        mockScoreView = mock(ScoreView.class);

        when(lc.getScoreView()).thenReturn(mockScoreView);
        when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lc.getLineSelectionState()).thenReturn(null);
        when(lc.getLine()).thenReturn(null);

        handler = new LineSelectionHandler(lc);
    }

    @AfterEach
    void tearDown() {
        scaleContextMock.close();
        slideRendererMock.close();
        elementHitTestMock.close();
    }

    // -------------------------------------------------------------------------
    // hitTest — cascade branch coverage
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HitTest {

        // Identity scale: 1 px = 1 ss.
        // Middle-line Y chosen so we can control whether a test point is within ±STAFF_HIT_RADIUS_SS (2.0).
        private static final double MIDDLE_LINE_Y_SS = 5.0;

        @BeforeEach
        void configureCommonStubs() {
            // Default: element hit test misses.
            elementHitTestMock.when(() -> ElementHitTest.hitTestElement(any(), any()))
                .thenReturn(-1);

            // Identity scale so px coordinates in Point arguments equal ss in the code.
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));

            when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);

            // Default glissando renderer returns no hit
            var mockRenderer = mock(SlideRenderer.class);
            slideRendererMock.when(SlideRenderer::getInstance).thenReturn(mockRenderer);
            when(mockRenderer.hitTestSlide(anyDouble(), anyDouble(), any())).thenReturn(-1);
        }

        @Test
        void testPointOnElementHeadReturnsElementHead() {
            elementHitTestMock.when(() -> ElementHitTest.hitTestElement(any(), any()))
                .thenReturn(2);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.ElementHead.class);
            assertThat(((HitResult.ElementHead) result).index()).isEqualTo(2);
        }

        @Test
        void testPointAtGlissandoOnNonGraceNoteReturnsGlissando() {
            // Element hit misses; glissando hit succeeds at index 1 (a regular note).
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });
            var selState = new LineSelectionState(line);
            when(lc.getLineSelectionState()).thenReturn(selState);

            var mockRenderer = mock(SlideRenderer.class);
            slideRendererMock.when(SlideRenderer::getInstance).thenReturn(mockRenderer);
            when(mockRenderer.hitTestSlide(anyDouble(), anyDouble(), any())).thenReturn(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Slide.class);
            assertThat(((HitResult.Slide) result).elementIndex()).isEqualTo(1);
        }

        @Test
        void testPointAtGlissandoOnGraceNoteReturnsGraceGlissando() {
            // Glissando hit at index 0, which is a grace note → GraceGlissando.
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.GRACE_QUAVER.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });
            var selState = new LineSelectionState(line);
            when(lc.getLineSelectionState()).thenReturn(selState);

            var mockRenderer = mock(SlideRenderer.class);
            slideRendererMock.when(SlideRenderer::getInstance).thenReturn(mockRenderer);
            when(mockRenderer.hitTestSlide(anyDouble(), anyDouble(), any())).thenReturn(0);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.GraceGlissando.class);
        }

        @Test
        void testYExactlyAtRadiusBoundaryAndXInHeaderReturnsStaffLine() {
            // STAFF_HIT_RADIUS_SS = 2.0; point exactly at middleLineY + 2.0 should still hit
            // because the condition is |clickY - middleY| <= 2.0.
            // X = 0 is within any header (G_CLEF_WIDTH_SS > 0).
            var yPx = (int) (MIDDLE_LINE_Y_SS + 2.0);

            var result = handler.hitTest(new Point(0, yPx));

            assertThat(result).isInstanceOf(HitResult.StaffLine.class);
        }

        @Test
        void testYJustBeyondRadiusReturnsNothing() {
            // |clickY - middleY| must exceed 2.0 for Nothing. Use a large Y offset.
            var yPx = (int) (MIDDLE_LINE_Y_SS + 100);

            var result = handler.hitTest(new Point(0, yPx));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        @Test
        void testXPastHeaderRightEdgeReturnsNothing() {
            // Y within radius but X far past any clef header → Nothing.
            // G_CLEF_WIDTH_SS for 0 key accidentals is at most a few ss; 1000 is well past.
            var yPx = (int) MIDDLE_LINE_Y_SS; // exactly at middle line, within radius

            var result = handler.hitTest(new Point(1000, yPx));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        @Test
        void testNoHitReturnsNothing() {
            // Y far from middle line and no element/glissando hit → Nothing.
            var result = handler.hitTest(new Point(1000, 1000));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }
    }

    // -------------------------------------------------------------------------
    // calculateLineSelectionFromDrag — tested through handleDrag
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateLineSelectionFromDrag {

        // Three notes assigned distinct X positions via a mocked LayoutResult.
        // The drag rect (in px, converted 1:1 to ss) spans X=[0,30], Y=[0,30].
        // Only element 0 at X=10 falls within; elements at X=50 and X=90 do not.
        private static final int ELEMENT_0_X_SS = 10;
        private static final int ELEMENT_1_X_SS = 50;
        private static final int ELEMENT_2_X_SS = 90;
        private static final int DRAG_END_X = 30;
        private static final int DRAG_END_Y = 30;

        // Middle-line Y placed inside the drag rect so element Y-ranges fall within it.
        private static final double MIDDLE_LINE_Y_SS = 10.0;
        // Press Y placed far above middle line so hitTest → Nothing and pressHandled stays false.
        private static final int PRESS_Y_OUTSIDE_RADIUS = 0;

        @Test
        void testDragRectEnclosingOneElementSelectsThatElement() {
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.getElementXSs(line.getElement(0))).thenReturn((double) ELEMENT_0_X_SS);
            when(mockLayout.getElementXSs(line.getElement(1))).thenReturn((double) ELEMENT_1_X_SS);
            when(mockLayout.getElementXSs(line.getElement(2))).thenReturn((double) ELEMENT_2_X_SS);
            when(lc.getLayoutResult()).thenReturn(mockLayout);
            when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);
            when(lc.getLine()).thenReturn(line);
            when(lc.getLineIndex()).thenReturn(0);
            when(lc.getWidth()).thenReturn(1000);
            when(lc.getHeight()).thenReturn(1000);

            // Identity scale: 1 px = 1 ss
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));

            // Allow real buildElementHitRect to run inside calculateLineSelectionFromDrag
            elementHitTestMock.when(
                () -> ElementHitTest.buildElementHitRect(any(), any(), any(), any(Boolean.class))
            ).thenCallRealMethod();
            elementHitTestMock.when(
                () -> ElementHitTest.buildElementHitRect(any(), any(), any())
            ).thenCallRealMethod();
            // hitTestElement is only used for the anchor fallback; return -1 to use the distance fallback
            elementHitTestMock.when(() -> ElementHitTest.hitTestElement(any(), any()))
                .thenReturn(-1);

            var lineSelState = new LineSelectionState(line);
            when(lc.getLineSelectionState()).thenReturn(lineSelState);

            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);

            // Glissando renderer: no hits
            var mockRenderer = mock(SlideRenderer.class);
            slideRendererMock.when(SlideRenderer::getInstance).thenReturn(mockRenderer);
            when(mockRenderer.hitTestSlide(anyDouble(), anyDouble(), any())).thenReturn(-1);

            // Press at (0, PRESS_Y_OUTSIDE_RADIUS=0): |0 - MIDDLE_LINE_Y_SS(10)| = 10 > 2.0 →
            // hitTest → Nothing → pressHandled = false → handleDrag will proceed.
            handler.handlePress(pressEvent(0, PRESS_Y_OUTSIDE_RADIUS));

            // Drag to (30, 30): dragStart=(0,0), drag rect = [0, 0, 30, 30].
            // Element 0 at X=10, height rect from middleLineY+topOffset to +height:
            //   Y ≈ [10 + topOffset, 10 + topOffset + height] — topOffset is negative, so
            //   Y range is roughly [9.7, 10.3], within [0, 30]. X=[10, 10+~1.2], within [0,30].
            // Element 1 at X=50 > 30 → no intersection. Element 2 at X=90 > 30 → no intersection.
            handler.handleDrag(dragEvent(DRAG_END_X, DRAG_END_Y));

            assertThat(lineSelState.getSelectionBegin()).isEqualTo(0);
            assertThat(lineSelState.getSelectionEnd()).isEqualTo(0);
        }

        @Test
        void testDragRectEnclosingMultipleElementsSelectsRange() {
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            // Both elements at X within [0, 30]
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.getElementXSs(line.getElement(0))).thenReturn(5.0);
            when(mockLayout.getElementXSs(line.getElement(1))).thenReturn(15.0);
            when(lc.getLayoutResult()).thenReturn(mockLayout);
            when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);
            when(lc.getLine()).thenReturn(line);
            when(lc.getLineIndex()).thenReturn(0);
            when(lc.getWidth()).thenReturn(1000);
            when(lc.getHeight()).thenReturn(1000);

            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));

            elementHitTestMock.when(
                () -> ElementHitTest.buildElementHitRect(any(), any(), any(), any(Boolean.class))
            ).thenCallRealMethod();
            elementHitTestMock.when(
                () -> ElementHitTest.buildElementHitRect(any(), any(), any())
            ).thenCallRealMethod();
            elementHitTestMock.when(() -> ElementHitTest.hitTestElement(any(), any()))
                .thenReturn(-1);

            var lineSelState = new LineSelectionState(line);
            when(lc.getLineSelectionState()).thenReturn(lineSelState);

            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);

            var mockRenderer = mock(SlideRenderer.class);
            slideRendererMock.when(SlideRenderer::getInstance).thenReturn(mockRenderer);
            when(mockRenderer.hitTestSlide(anyDouble(), anyDouble(), any())).thenReturn(-1);

            handler.handlePress(pressEvent(0, PRESS_Y_OUTSIDE_RADIUS));
            handler.handleDrag(dragEvent(DRAG_END_X, DRAG_END_Y));

            // Both elements within drag rect → selection spans from 0 to 1
            assertThat(lineSelState.getSelectionBegin()).isEqualTo(0);
            assertThat(lineSelState.getSelectionEnd()).isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MouseEvent pressEvent(int x, int y) {
        // Use the 10-arg constructor that sets xAbs/yAbs (screen coords) so
        // MouseEvent.getXOnScreen() / getYOnScreen() do not NPE in handler code.
        return new MouseEvent(lc, MouseEvent.MOUSE_PRESSED, 0L, 0, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private MouseEvent dragEvent(int x, int y) {
        return new MouseEvent(lc, MouseEvent.MOUSE_DRAGGED, 0L, 0, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }
}
