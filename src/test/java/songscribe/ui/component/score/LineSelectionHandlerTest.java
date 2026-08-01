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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;
// Disambiguates from java.awt.List (java.desktop module)
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.layout.Ending;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.hit.HitResult;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.HairpinRenderer;
import songscribe.ui.renderer.SlideRenderer;
import songscribe.ui.playback.MidiController;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectedDecoration;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link LineSelectionHandler}.
 *
 * <p>{@code hitTest} tests mock every {@link songscribe.ui.hit.HitTester} in the cascade —
 * {@link ElementHitTest}, {@link SlideRenderer}, {@link EndingRenderer} — to control which
 * one reports a hit without real geometry. {@code calculateLineSelectionFromDrag} is tested
 * through {@link LineSelectionHandler#handleDrag} (its only caller), using a real
 * {@link Song}/{@link Line} and an identity {@link ScaleContext} so the drag-rect/element-rect
 * intersection can be verified numerically.
 */
class LineSelectionHandlerTest extends UnitTest {

    private MockedStatic<ElementHitTest> elementHitTestMock;
    private MockedStatic<SlideRenderer> slideRendererMock;
    private MockedStatic<HairpinRenderer> hairpinRendererMock;
    private MockedStatic<EndingRenderer> endingRendererMock;
    private MockedStatic<ScaleContext> scaleContextMock;

    private LineComponent lc;
    private ScoreView mockScoreView;
    private SlideRenderer mockSlideRenderer;
    private HairpinRenderer mockHairpinRenderer;
    private EndingRenderer mockEndingRenderer;
    private LineSelectionHandler handler;

    @BeforeEach
    void setUp() {
        elementHitTestMock = mockStatic(ElementHitTest.class);
        slideRendererMock = mockStatic(SlideRenderer.class);
        hairpinRendererMock = mockStatic(HairpinRenderer.class);
        endingRendererMock = mockStatic(EndingRenderer.class);
        scaleContextMock = mockStatic(ScaleContext.class);

        lc = mock(LineComponent.class);
        mockScoreView = mock(ScoreView.class);

        when(lc.getScoreView()).thenReturn(mockScoreView);
        when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lc.getLineSelectionState()).thenReturn(null);
        when(lc.getLine()).thenReturn(null);

        // The handler captures both renderer singletons when it builds its tester list in the
        // constructor, so the static stubs must be in place first and must keep returning
        // these same instances afterwards. Tests vary the behavior of these mocks rather than
        // swapping in fresh ones.
        mockSlideRenderer = mock(SlideRenderer.class);
        slideRendererMock.when(SlideRenderer::getInstance).thenReturn(mockSlideRenderer);
        mockHairpinRenderer = mock(HairpinRenderer.class);
        hairpinRendererMock.when(HairpinRenderer::getInstance).thenReturn(mockHairpinRenderer);
        mockEndingRenderer = mock(EndingRenderer.class);
        endingRendererMock.when(EndingRenderer::getInstance).thenReturn(mockEndingRenderer);

        // Default every tester to a miss; each test opts into the hit it cares about.
        elementHitTestMock.when(() -> ElementHitTest.hit(any(), any())).thenReturn(null);
        stubSlideHit(-1);
        stubHairpinHit(null);
        stubEndingHit(null);

        handler = new LineSelectionHandler(lc);
    }

    @AfterEach
    void tearDown() {
        scaleContextMock.close();
        endingRendererMock.close();
        hairpinRendererMock.close();
        slideRendererMock.close();
        elementHitTestMock.close();
    }

    /**
     * Stubs the ending tester to report {@code ending} as hit, or no hit when null.
     */
    private void stubEndingHit(@Nullable Ending ending) {
        when(mockEndingRenderer.hitTestEnding(anyDouble(), anyDouble(), any(), any(), anyDouble()))
            .thenReturn(ending);
    }

    /**
     * Stubs the slide renderer to report the slide owned by the element at
     * {@code elementIndex} as hit. Pass -1 for no hit. Whether that slide is selectable is
     * the handler's call, not the renderer's, so it is decided by the element's type.
     */
    private void stubSlideHit(int elementIndex) {
        when(mockSlideRenderer.hitTestSlide(anyDouble(), anyDouble(), any())).thenReturn(elementIndex);
    }

    /**
     * Stubs the hairpin tester to report {@code hairpin} as hit, or no hit when null.
     */
    private void stubHairpinHit(@Nullable Hairpin hairpin) {
        when(mockHairpinRenderer.hitTestHairpin(anyDouble(), anyDouble(), any(), any(), anyDouble()))
            .thenReturn(hairpin);
    }

    /** Verse number used by lyric hit-test fixtures, chosen to not be verse 1. */
    private static final int LYRIC_VERSE = 2;

    /**
     * Stubs the layout the lyric tester reads to report {@code lyricHit} for {@code line}
     * (or no hit when null), and supplies non-null lyric render metrics so the tester runs
     * instead of declining for lack of them.
     */
    private void stubLyricHit(Line line, LayoutResult.@Nullable LyricHit lyricHit) {
        var mockLayout = mock(LayoutResult.class);
        when(mockLayout.hitTestLyric(any(), any(), any())).thenReturn(lyricHit);
        when(lc.readyLayout()).thenReturn(new LineComponent.ReadyLayout(line, mockLayout));
        when(lc.findLyricRenderMetrics()).thenReturn(mock(LyricRenderMetrics.class));
    }

    /**
     * Registers a two-note line and its selection state on the component, which
     * {@code buildContext} requires before any tester runs.
     */
    private Line givenLine() {
        return givenLine(ElementType.CROTCHET, ElementType.CROTCHET);
    }

    private Line givenLine(ElementType first, ElementType second) {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(first.newInstance());
            line.addElement(second.newInstance());
        });
        when(lc.getLine()).thenReturn(line);
        when(lc.getLineSelectionState()).thenReturn(new LineSelectionState(line));
        return line;
    }

    /**
     * Builds a standalone {@link Ending}; hit results carry it by reference only.
     */
    private static Ending newEnding() {
        return new Ending(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance());
    }

    /**
     * Builds a standalone {@link Hairpin}; hit results carry it by reference only.
     */
    private static Hairpin newHairpin() {
        return new Crescendo(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance());
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

        // X far past any clef header, so the staff-line tester cannot hit.
        private static final int X_PAST_HEADER = 1000;

        // Y far from the middle line, so the staff-line tester cannot hit.
        private static final int Y_OUTSIDE_RADIUS = 1000;

        private Line line;

        @BeforeEach
        void configureCommonStubs() {
            // Identity scale so px coordinates in Point arguments equal ss in the code.
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));

            when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);
            line = givenLine();
        }

        @Test
        void testPointAtEndingReturnsEnding() {
            var ending = newEnding();
            stubEndingHit(ending);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Ending.class);
            assertThat(((HitResult.Ending) result).ending()).isSameAs(ending);
        }

        // The cascade runs element heads first, so a point hitting both resolves to the head.
        @Test
        void testPointAtBothElementHeadAndSlideReturnsElementHead() {
            elementHitTestMock.when(() -> ElementHitTest.hit(any(), any()))
                .thenReturn(new HitResult.ElementHead(2));
            stubSlideHit(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isEqualTo(new HitResult.ElementHead(2));
        }

        // The cascade checks slides before endings, so a point hitting both resolves to the slide.
        @Test
        void testPointAtBothSlideAndEndingReturnsSlide() {
            stubEndingHit(newEnding());
            stubSlideHit(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Slide.class);
        }

        @Test
        void testHairpinHitIsReturnedUnchanged() {
            var hairpin = newHairpin();
            stubHairpinHit(hairpin);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isEqualTo(new HitResult.Hairpin(hairpin));
        }

        // The hairpin tester sits between the slide and ending testers, so these two tests
        // pin its position from both sides: a slide beats a hairpin…
        @Test
        void testPointAtBothSlideAndHairpinReturnsSlide() {
            stubHairpinHit(newHairpin());
            stubSlideHit(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Slide.class);
        }

        // …and a hairpin beats an ending.
        @Test
        void testPointAtBothHairpinAndEndingReturnsHairpin() {
            var hairpin = newHairpin();
            stubHairpinHit(hairpin);
            stubEndingHit(newEnding());

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Hairpin.class);
            assertThat(((HitResult.Hairpin) result).hairpin()).isSameAs(hairpin);
        }

        // An element head wins the whole cascade, hairpins included.
        @Test
        void testPointAtBothElementHeadAndHairpinReturnsElementHead() {
            elementHitTestMock.when(() -> ElementHitTest.hit(any(), any()))
                .thenReturn(new HitResult.ElementHead(2));
            stubHairpinHit(newHairpin());

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isEqualTo(new HitResult.ElementHead(2));
        }

        // The cascade checks endings before the staff line, so an ending over the staff
        // line wins even at a Y that would otherwise register as a staff-line hit.
        @Test
        void testPointAtEndingOverStaffLineReturnsEnding() {
            var ending = newEnding();
            stubEndingHit(ending);

            var result = handler.hitTest(new Point(0, (int) MIDDLE_LINE_Y_SS));

            assertThat(result).isInstanceOf(HitResult.Ending.class);
        }

        @Test
        void testPointOnLyricReturnsLyricResult() {
            var element = line.getElement(0);
            stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isEqualTo(new HitResult.Lyric(element, LYRIC_VERSE));
        }

        // The cascade checks the lyric tester before the element-head tester, so a point
        // hitting both resolves to the lyric — this documents the deliberate priority
        // (see LineSelectionHandler.handleEditModePress for why a lyric outranks insertion).
        @Test
        void testPointAtBothLyricAndElementHeadReturnsLyric() {
            var element = line.getElement(0);
            stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));
            elementHitTestMock.when(() -> ElementHitTest.hit(any(), any()))
                .thenReturn(new HitResult.ElementHead(0));

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isEqualTo(new HitResult.Lyric(element, LYRIC_VERSE));
        }

        // The ordinary case in real use: the layout and the lyric metrics are both there, and the
        // layout simply reports no lyric box under the point. Without this the lyric tester is
        // only ever exercised when it succeeds, so a tester that answered "lyric" for every point
        // would pass the rest of the suite.
        @Test
        void testPointWithNoLyricUnderItFallsThroughTheLyricTester() {
            stubLyricHit(line, null);

            var result = handler.hitTest(new Point(0, Y_OUTSIDE_RADIUS));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        // buildContext supplies a null layoutResult when the component has no ready layout;
        // the lyric tester must decline rather than dereference it.
        @Test
        void testNullLayoutResultLyricTesterDeclinesRatherThanThrows() {
            // lc.readyLayout() defaults to null (unstubbed), so layoutResult() is null.
            when(lc.findLyricRenderMetrics()).thenReturn(mock(LyricRenderMetrics.class));

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        // A ready layout with no lyric metrics yet must also decline rather than throw, even
        // though the layout itself would report a hit if asked.
        @Test
        void testNullLyricRenderMetricsLyricTesterDeclinesRatherThanThrows() {
            var element = line.getElement(0);
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.hitTestLyric(any(), any(), any()))
                .thenReturn(new LayoutResult.LyricHit(element, LYRIC_VERSE));
            when(lc.readyLayout()).thenReturn(new LineComponent.ReadyLayout(line, mockLayout));
            // lc.findLyricRenderMetrics() defaults to null (unstubbed).

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        /**
         * Stubs every tester to report a hit, so any result other than
         * {@link HitResult.Nothing} proves the guard let the cascade run.
         */
        private void stubEveryTesterToHit() {
            elementHitTestMock.when(() -> ElementHitTest.hit(any(), any()))
                .thenReturn(new HitResult.ElementHead(0));
            stubSlideHit(0);
            stubHairpinHit(newHairpin());
            stubEndingHit(newEnding());
        }

        private void assertCascadeDidNotRun() {
            var result = handler.hitTest(new Point(0, (int) MIDDLE_LINE_Y_SS));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
            verify(mockSlideRenderer, never()).hitTestSlide(anyDouble(), anyDouble(), any());
            verify(mockHairpinRenderer, never())
                .hitTestHairpin(anyDouble(), anyDouble(), any(), any(), anyDouble());
            verify(mockEndingRenderer, never())
                .hitTestEnding(anyDouble(), anyDouble(), any(), any(), anyDouble());
        }

        // buildContext bails out before any tester runs when the component has no line, so
        // even a stubbed hit must not surface.
        @Test
        void testNoLineAndNoSelectionStateReturnsNothing() {
            when(lc.getLine()).thenReturn(null);
            when(lc.getLineSelectionState()).thenReturn(null);
            stubEveryTesterToHit();

            assertCascadeDidNotRun();
        }

        // Each half of the guard is checked on its own, because a test that nulls both at
        // once passes just as happily if the guard's "or" is ever changed to an "and".
        @Test
        void testLinePresentButNoSelectionStateReturnsNothing() {
            givenLine();
            when(lc.getLineSelectionState()).thenReturn(null);
            stubEveryTesterToHit();

            assertCascadeDidNotRun();
        }

        @Test
        void testSelectionStatePresentButNoLineReturnsNothing() {
            var selectedLine = givenLine();
            when(lc.getLine()).thenReturn(null);
            when(lc.getLineSelectionState()).thenReturn(new LineSelectionState(selectedLine));
            stubEveryTesterToHit();

            assertCascadeDidNotRun();
        }

        @Test
        void testPointOnElementHeadReturnsElementHead() {
            elementHitTestMock.when(() -> ElementHitTest.hit(any(), any()))
                .thenReturn(new HitResult.ElementHead(2));

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.ElementHead.class);
            assertThat(((HitResult.ElementHead) result).index()).isEqualTo(2);
        }

        @Test
        void testSlideHitIsReturnedUnchanged() {
            stubSlideHit(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.Slide.class);
            assertThat(((HitResult.Slide) result).elementIndex()).isEqualTo(1);
        }

        // The renderer reports only which slide was hit. Deciding that a grace note's slide
        // is not selectable is the handler's job, so it is driven here by the element type.
        @Test
        void testSlideOwnedByGraceNoteReturnsGraceGlissando() {
            givenLine(ElementType.CROTCHET, ElementType.GRACE_QUAVER);
            stubSlideHit(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isInstanceOf(HitResult.GraceGlissando.class);
        }

        @Test
        void testSlideOwnedByNormalNoteReturnsSlide() {
            givenLine(ElementType.CROTCHET, ElementType.CROTCHET);
            stubSlideHit(1);

            var result = handler.hitTest(new Point(0, 0));

            assertThat(result).isEqualTo(new HitResult.Slide(1));
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
            var result = handler.hitTest(new Point(0, Y_OUTSIDE_RADIUS));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        @Test
        void testXPastHeaderRightEdgeReturnsNothing() {
            // Y within radius but X far past any clef header → Nothing.
            var yPx = (int) MIDDLE_LINE_Y_SS; // exactly at middle line, within radius

            var result = handler.hitTest(new Point(X_PAST_HEADER, yPx));

            assertThat(result).isInstanceOf(HitResult.Nothing.class);
        }

        @Test
        void testNoHitReturnsNothing() {
            // Y far from middle line and no element/slide/ending hit → Nothing.
            var result = handler.hitTest(new Point(X_PAST_HEADER, Y_OUTSIDE_RADIUS));

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

            // Press at (0, PRESS_Y_OUTSIDE_RADIUS=0): |0 - MIDDLE_LINE_Y_SS(10)| = 10 > 2.0 →
            // hitTest → Nothing → pressHandled = false → handleDrag will proceed.
            pressAt(pressEvent(0, PRESS_Y_OUTSIDE_RADIUS));

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

            pressAt(pressEvent(0, PRESS_Y_OUTSIDE_RADIUS));
            handler.handleDrag(dragEvent(DRAG_END_X, DRAG_END_Y));

            // Both elements within drag rect → selection spans from 0 to 1
            assertThat(lineSelState.getSelectionBegin()).isEqualTo(0);
            assertThat(lineSelState.getSelectionEnd()).isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // handlePress — HitResult.Ending arm
    // -------------------------------------------------------------------------

    @Test
    void testPressOnEndingSelectsItAndNotifiesScoreView() {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
        });
        var lineSelState = new LineSelectionState(line);
        when(lc.getLine()).thenReturn(line);
        when(lc.getLineSelectionState()).thenReturn(lineSelState);
        when(lc.getLineIndex()).thenReturn(0);
        when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        var ending = newEnding();
        stubEndingHit(ending);

        pressAt(pressEvent(0, 0));

        assertThat(lineSelState.isDecorationSelected(ending))
            .as("ending is selected after pressing on it")
            .isTrue();
        verify(mockScoreView).selectionChanged();
    }

    /**
     * A press that selects an ending must be marked handled, so a rubber-band drag
     * does not start and immediately replace the ending selection.
     */
    @Test
    void testPressOnEndingSuppressesRubberBandDrag() {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
        });
        var lineSelState = new LineSelectionState(line);
        when(lc.getLine()).thenReturn(line);
        when(lc.getLineSelectionState()).thenReturn(lineSelState);
        when(lc.getLineIndex()).thenReturn(0);
        when(lc.getWidth()).thenReturn(1000);
        when(lc.getHeight()).thenReturn(1000);
        when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        var ending = newEnding();
        stubEndingHit(ending);

        pressAt(pressEvent(0, 0));
        handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

        assertThat(lineSelState.isDecorationSelected(ending))
            .as("ending stays selected after a drag following the press")
            .isTrue();
        assertThat(lineSelState.hasElementSelection())
            .as("no rubber-band element selection was started")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // handlePress — HitResult.Slide arm
    // -------------------------------------------------------------------------

    /**
     * A slide is recorded by the index of the element that owns it, not by identity, so
     * carrying the wrong index through would select a different note's slide. The end-to-end
     * suite clicks a real glissando, but nothing at this level pinned down which element the
     * press arm actually records — hitting element 1 rather than 0 is what makes that visible.
     */
    @Test
    void testPressOnSlideSelectsItAndNotifiesScoreView() {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
        });
        var lineSelState = new LineSelectionState(line);
        when(lc.getLine()).thenReturn(line);
        when(lc.getLineSelectionState()).thenReturn(lineSelState);
        when(lc.getLineIndex()).thenReturn(0);
        when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        stubSlideHit(1);

        pressAt(pressEvent(0, 0));

        assertThat(lineSelState.getSelectedDecoration())
            .as("the slide owned by the element that was hit is selected")
            .isEqualTo(new SelectedDecoration.SlideSelection(1));
        verify(mockScoreView).selectionChanged();
    }

    /**
     * A grace note's glissando cannot be selected. Pressing on one warns the user and must
     * count the press as handled, so a rubber-band drag does not start instead — which
     * would silently select notes the user never meant to touch.
     */
    @Test
    void testPressOnGraceGlissandoWarnsAndSuppressesRubberBandDrag() {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.GRACE_QUAVER.newInstance());
        });
        var lineSelState = new LineSelectionState(line);
        when(lc.getLine()).thenReturn(line);
        when(lc.getLineSelectionState()).thenReturn(lineSelState);
        when(lc.getLineIndex()).thenReturn(0);
        when(lc.getWidth()).thenReturn(1000);
        when(lc.getHeight()).thenReturn(1000);
        when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        stubSlideHit(1);

        try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
            pressAt(pressEvent(0, 0));
            handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

            optionDialogsMock.verify(() -> OptionDialogs.showWarningMessage(
                any(),
                eq(Strings.ALERT_TITLE_GRACE_NOTE_WARNING),
                eq(Strings.WARNING_GRACE_GLISSANDO_NOT_SELECTABLE)
            ));
        }

        assertThat(lineSelState.hasElementSelection())
            .as("no rubber-band element selection was started")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // handlePress — HitResult.Lyric arm
    // -------------------------------------------------------------------------

    @Test
    void testPressOnLyricSelectsItAndNotifiesScoreView() {
        var line = givenLine();
        when(lc.getLineIndex()).thenReturn(0);
        var mockCoordinator = mock(SelectionCoordinator.class);
        when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);

        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        var element = line.getElement(0);
        stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));

        pressAt(pressEvent(0, 0));

        verify(mockCoordinator).selectLyric(element, LYRIC_VERSE);
        verify(mockScoreView).selectionChanged();
    }

    /**
     * A press that selects a lyric must be marked handled, so a rubber-band drag does not
     * start and immediately replace the lyric selection.
     */
    @Test
    void testPressOnLyricSuppressesRubberBandDrag() {
        var line = givenLine();
        when(lc.getLineIndex()).thenReturn(0);
        when(lc.getWidth()).thenReturn(1000);
        when(lc.getHeight()).thenReturn(1000);
        when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        var element = line.getElement(0);
        stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));

        pressAt(pressEvent(0, 0));
        handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

        assertThat(handler.isDragging())
            .as("no rubber-band drag started after a press on a lyric")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // handleEditModePress
    // -------------------------------------------------------------------------

    /**
     * The EDIT-mode entry point that lets a lyric, ending or hairpin be selected without
     * first switching to SELECT mode. The mode check itself lives in
     * {@code LineComponent.mousePressed}; this class only decides whether the press landed
     * on one of them.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleEditModePress {

        private LineSelectionState lineSelectionState;
        private SelectionCoordinator coordinator;
        private Line line;

        @BeforeEach
        void configureCommonStubs() {
            line = givenLine();
            lineSelectionState = new LineSelectionState(line);
            when(lc.getLineSelectionState()).thenReturn(lineSelectionState);
            when(lc.getLineIndex()).thenReturn(0);
            coordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
            scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
                .thenAnswer(inv -> inv.getArgument(0));
        }

        /**
         * Runs the cascade for a press at the origin and hands the result to the method
         * under test, the same way {@code LineComponent.mousePressed} does.
         */
        private boolean pressAtOrigin() {
            return handler.handleEditModePress(handler.hitTestViewPoint(new Point(0, 0)));
        }

        @Test
        void testPressOnEndingSelectsItAndReportsHandled() {
            var ending = newEnding();
            stubEndingHit(ending);

            assertThat(pressAtOrigin())
                .as("press on an ending is handled")
                .isTrue();

            assertThat(lineSelectionState.isDecorationSelected(ending))
                .as("ending is selected")
                .isTrue();
            verify(mockScoreView).selectionChanged();
            verify(lc).repaint();
        }

        @Test
        void testPressOnLyricSelectsItAndReportsHandled() {
            var element = line.getElement(0);
            stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));

            assertThat(pressAtOrigin())
                .as("press on a lyric is handled")
                .isTrue();

            verify(coordinator).selectLyric(element, LYRIC_VERSE);
            verify(mockScoreView).selectionChanged();
            verify(lc).repaint();
        }

        /**
         * Selecting a lyric mid-playback would move the selection out from under the
         * playing sequence, so the press is refused outright.
         */
        @Test
        void testPressOnLyricDuringPlaybackIsNotHandled() {
            var element = line.getElement(0);
            stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));

            try (var midiMock = mockStatic(MidiController.class)) {
                midiMock.when(MidiController::isPlaying).thenReturn(true);

                assertThat(pressAtOrigin())
                    .as("press on a lyric is refused while MIDI playback is running")
                    .isFalse();
            }

            verify(coordinator, never()).selectLyric(any(), anyInt());
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * Unlike a decoration, a lyric wins over the insertion preview (see
         * {@link LineSelectionHandler#handleEditModePress}) — but the preview can still reach
         * the lyric row when the mouse never moved to clear it, and this fallback guard is
         * what refuses the press in that case.
         */
        @Test
        void testPressOnLyricWithPreviewElementShowingIsNotHandled() {
            var element = line.getElement(0);
            stubLyricHit(line, new LayoutResult.LyricHit(element, LYRIC_VERSE));
            when(lc.hasPreviewElement()).thenReturn(true);

            assertThat(pressAtOrigin())
                .as("press is left to the insertion preview")
                .isFalse();

            verify(coordinator, never()).selectLyric(any(), anyInt());
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * An element head over the bracket wins the cascade, so the press falls through to
         * normal EDIT-mode handling rather than selecting the ending underneath it.
         */
        @Test
        void testElementHeadOverEndingIsNotHandled() {
            stubEndingHit(newEnding());
            elementHitTestMock.when(() -> ElementHitTest.hit(any(), any()))
                .thenReturn(new HitResult.ElementHead(0));

            assertThat(pressAtOrigin())
                .as("press on an element head is left to EDIT-mode handling")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        @Test
        void testPressOnNothingIsNotHandled() {
            assertThat(pressAtOrigin())
                .as("press that hits nothing is left to EDIT-mode handling")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * Selecting an ending mid-playback would move the selection out from under the
         * playing sequence, so the press is refused outright.
         */
        @Test
        void testPressDuringPlaybackIsNotHandled() {
            var ending = newEnding();
            stubEndingHit(ending);

            try (var midiMock = mockStatic(MidiController.class)) {
                midiMock.when(MidiController::isPlaying).thenReturn(true);

                assertThat(pressAtOrigin())
                    .as("press on an ending is refused while MIDI playback is running")
                    .isFalse();
            }

            assertThat(lineSelectionState.isDecorationSelected(ending))
                .as("ending was not selected")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        @Test
        void testPressOnHairpinSelectsItAndReportsHandled() {
            var hairpin = newHairpin();
            stubHairpinHit(hairpin);

            assertThat(pressAtOrigin())
                .as("press on a hairpin is handled")
                .isTrue();

            assertThat(lineSelectionState.getSelectedDecoration())
                .as("hairpin is selected")
                .isEqualTo(new SelectedDecoration.HairpinSelection(hairpin));
            verify(mockScoreView).selectionChanged();
            verify(lc).repaint();
        }

        /**
         * While the insertion preview is showing, the click position is a valid staff
         * position and inserting there simply pushes the decoration aside — so the press
         * belongs to insertion, not to selecting the hairpin underneath it.
         */
        @Test
        void testPressWithPreviewElementShowingIsNotHandled() {
            var hairpin = newHairpin();
            stubHairpinHit(hairpin);
            when(lc.hasPreviewElement()).thenReturn(true);

            assertThat(pressAtOrigin())
                .as("press is left to the insertion preview")
                .isFalse();

            assertThat(lineSelectionState.hasDecorationSelection())
                .as("hairpin was not selected")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        @Test
        void testPressOnHairpinDuringPlaybackIsNotHandled() {
            var hairpin = newHairpin();
            stubHairpinHit(hairpin);

            try (var midiMock = mockStatic(MidiController.class)) {
                midiMock.when(MidiController::isPlaying).thenReturn(true);

                assertThat(pressAtOrigin())
                    .as("press on a hairpin is refused while MIDI playback is running")
                    .isFalse();
            }

            assertThat(lineSelectionState.hasDecorationSelection())
                .as("hairpin was not selected")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * Only lyrics, endings and hairpins are handled here. Feeding every other
         * {@link HitResult} variant in directly — rather than driving them through the
         * cascade — keeps this exhaustive: adding a variant to the sealed interface without
         * deciding what this method should do with it will not slip through unnoticed.
         */
        @Test
        void testEveryUnhandledHitResultIsNotHandled() {
            var unhandledResults = List.of(
                new HitResult.ElementHead(0),
                new HitResult.Slide(0),
                new HitResult.GraceGlissando(),
                new HitResult.StaffLine(),
                new HitResult.Nothing()
            );

            for (var result : unhandledResults) {
                assertThat(handler.handleEditModePress(result))
                    .as("%s is not handled by handleEditModePress", result)
                    .isFalse();
            }

            verify(mockScoreView, never()).selectionChanged();
        }
    }

    // -------------------------------------------------------------------------
    // selectElementAtIndex — stale-highlight repaint of the outgoing line
    // -------------------------------------------------------------------------

    /**
     * {@code selectElementAtIndex} is called directly rather than through
     * {@link LineSelectionHandler#handlePress}, because the press path is not where the
     * outgoing-line repaint matters: {@code handlePress} calls
     * {@code ScoreView.clearSelection()} first, which resets the coordinator's active line
     * to -1 and repaints the outgoing line itself, leaving nothing for
     * {@code selectElementAtIndex} to do. The state exercised here — a *different* line
     * still active on entry — is produced by {@code NoteDragHandler}, which selects a
     * pressed note without pre-clearing and so bypasses {@code handlePress} entirely.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectElementAtIndex {

        private static final int FIRST_LINE_INDEX = 0;
        private static final int SECOND_LINE_INDEX = 1;

        /** Line index registered with no selection state, to force a null selection result. */
        private static final int UNREGISTERED_LINE_INDEX = 2;

        private Song song;
        private Line firstLine;
        private Line secondLine;
        private SelectionCoordinator coordinator;
        private LineSelectionState firstLineState;
        private LineSelectionState secondLineState;
        private LineComponent firstLineComponent;

        @BeforeEach
        void buildTwoLineSong() {
            song = new Song();
            firstLine = song.getLine(FIRST_LINE_INDEX);
            secondLine = new Line(song);
            song.withoutMutationTracking(() -> {
                firstLine.addElement(ElementType.CROTCHET_REST.newInstance());
                secondLine.addElement(ElementType.CROTCHET_REST.newInstance());
            });
            song.addLine(secondLine);

            coordinator = new SelectionCoordinator(mock(ScoreView.class));
            firstLineState = new LineSelectionState(firstLine);
            secondLineState = new LineSelectionState(secondLine);
            coordinator.registerLineState(FIRST_LINE_INDEX, firstLineState);
            coordinator.registerLineState(SECOND_LINE_INDEX, secondLineState);

            firstLineComponent = mock(LineComponent.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
            when(mockScoreView.getLineComponent(FIRST_LINE_INDEX)).thenReturn(firstLineComponent);

            when(lc.getLine()).thenReturn(secondLine);
            when(lc.getLineIndex()).thenReturn(SECOND_LINE_INDEX);
            when(lc.getLineSelectionState()).thenReturn(secondLineState);
        }

        /**
         * The stale highlight of issue #625: the outgoing line's state is cleared by
         * {@code selectSingleElement}, so without an explicit repaint it keeps painting a
         * selection that no longer exists.
         */
        @Test
        void testSelectingOnAnotherLineRepaintsThePreviouslyActiveLine() {
            coordinator.selectSingleElement(FIRST_LINE_INDEX, 0);

            handler.selectElementAtIndex(0);

            assertThat(firstLineState.hasElementSelection())
                .as("outgoing line's selection state was cleared")
                .isFalse();
            assertThat(secondLineState.getSingleSelectedElement())
                .as("target line's element is now selected")
                .isEqualTo(secondLine.getElement(0));
            verify(firstLineComponent).repaint();
            verify(mockScoreView).selectionChanged();
        }

        /**
         * Reselecting within the already-active line has no outgoing line, so the extra
         * repaint must be suppressed — this line is repainted by its own caller.
         */
        @Test
        void testSelectingOnTheAlreadyActiveLineDoesNotRepaintIt() {
            when(mockScoreView.getLineComponent(SECOND_LINE_INDEX)).thenReturn(lc);
            song.withoutMutationTracking(() -> secondLine.addElement(ElementType.CROTCHET_REST.newInstance()));
            coordinator.selectSingleElement(SECOND_LINE_INDEX, 0);

            handler.selectElementAtIndex(1);

            assertThat(secondLineState.getSingleSelectedElement())
                .as("selection moved to the newly clicked element")
                .isEqualTo(secondLine.getElement(1));
            verify(lc, never()).repaint();
        }

        /**
         * The first selection of a session: nothing was active, so
         * {@code getLineComponent(-1)} yields null and there is nothing to repaint.
         */
        @Test
        void testSelectingWithNoPreviouslyActiveLineRepaintsNothing() {
            when(mockScoreView.getLineComponent(-1)).thenReturn(null);

            handler.selectElementAtIndex(0);

            assertThat(secondLineState.getSingleSelectedElement())
                .as("target line's element is selected on the first click")
                .isEqualTo(secondLine.getElement(0));
            assertThat(coordinator.getActiveLineIndex())
                .as("target line became the active line")
                .isEqualTo(SECOND_LINE_INDEX);
            verify(firstLineComponent, never()).repaint();
        }

        /**
         * A line with no registered selection state cannot be selected, so no selection
         * change may be announced — but the outgoing line still repaints, since
         * {@code selectSingleElement} cleared it on the way through.
         */
        @Test
        void testSelectingOnALineWithNoRegisteredStateDoesNotNotify() {
            coordinator.selectSingleElement(FIRST_LINE_INDEX, 0);
            when(lc.getLineIndex()).thenReturn(UNREGISTERED_LINE_INDEX);

            handler.selectElementAtIndex(0);

            verify(mockScoreView, never()).selectionChanged();
            verify(firstLineComponent).repaint();
        }
    }

    private static final int DRAG_TARGET_X = 30;
    private static final int DRAG_TARGET_Y = 30;

    private MouseEvent pressEvent(int x, int y) {
        // Use the 10-arg constructor that sets xAbs/yAbs (screen coords) so
        // MouseEvent.getXOnScreen() / getYOnScreen() do not NPE in handler code.
        return new MouseEvent(lc, MouseEvent.MOUSE_PRESSED, 0L, 0, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }

    /** Presses at the event's point with the cascade result the production caller supplies. */
    private void pressAt(MouseEvent event) {
        handler.handlePress(event, handler.hitTestViewPoint(event.getPoint()));
    }

    private MouseEvent dragEvent(int x, int y) {
        return new MouseEvent(lc, MouseEvent.MOUSE_DRAGGED, 0L, 0, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }
}
