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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.data.Offset;
import org.mockito.MockedStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.SongLayoutMetrics;
import songscribe.ui.Mode;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.KeySignatureRenderer;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.renderer.NoteRenderer;
import songscribe.ui.renderer.RenderingUtils;

/**
 * Unit tests for {@link LineRenderer} package-private methods:
 * {@link LineRenderer#drawStaffLines} and {@link LineRenderer#renderWithPreviewShiftIfNeeded}.
 */
class LineRendererTest extends UnitTest {

    private static final int FONT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);

    /** A real LineRenderer under test, wired to a mock LineComponent. */
    private LineRenderer renderer;

    @BeforeAll
    static void installFlatLaf() throws Exception {
        installFlatLafDefaults();
    }

    @BeforeEach
    void setUp() {
        renderer = new LineRenderer(mock(LineComponent.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code LineInvariants.Builder} seeded with minimal required fields
     * so each test only configures the state it cares about.
     */
    private static LineInvariants.Builder seededBuilder() {
        return LineInvariants.builder(new Song(), DocumentFonts.defaultsFromPrefs())
            .setLayoutResult(LayoutResult.builder().build())
            .setSongLayoutMetrics(new SongLayoutMetrics(0, 0, 0, 0, 0, 0, 0, 0))
            .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0));
    }

    /** Creates a real {@code Graphics2D} backed by an off-screen image, wrapped in a spy. */
    private static java.awt.Graphics2D spyGraphics() {
        var img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        return spy(img.createGraphics());
    }

    // -------------------------------------------------------------------------
    // drawStaffLines — color selection branch (row 16)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DrawStaffLines {

        /**
         * A neutral initial color that is neither {@link RenderingUtils#STAFF_LINE_COLOR}
         * nor the selection color. Setting this on g2 before calling drawStaffLines ensures
         * the restore-on-close call can be distinguished from the test-driven setColor call.
         */
        private static final Color INITIAL_COLOR = Color.BLUE;

        /**
         * When the line is NOT selected (default — no selection provider),
         * {@code drawStaffLines} sets the color to {@link RenderingUtils#STAFF_LINE_COLOR}
         * (black), not the selection color.
         */
        @Test
        void testNonSelectedLineUsesStaffLineColor() {
            var invariants = seededBuilder()
                .setEditMode(true)
                // no selectionProvider set → staffSelected = false
                .build();
            var g2 = spyGraphics();
            g2.setColor(INITIAL_COLOR);

            renderer.drawStaffLines(g2, invariants);

            // setColor(BLACK) once inside the block; restore sets INITIAL_COLOR back
            verify(g2).setColor(RenderingUtils.STAFF_LINE_COLOR);
            verify(g2, never()).setColor(ScoreView.getSelectionColor());
        }

        /**
         * When edit mode is true and the selection provider reports the line as selected,
         * {@code drawStaffLines} sets the color to {@link ScoreView#getSelectionColor()},
         * not the default staff-line color.
         */
        @Test
        void testSelectedLineInEditModeUsesSelectionColor() {
            final int lineIndex = 0;
            var selectionProvider = mock(LineComponent.SelectionProvider.class);
            when(selectionProvider.isLineSelected(lineIndex)).thenReturn(true);

            var invariants = seededBuilder()
                .setEditMode(true)
                .setLineIndex(lineIndex)
                .setSelectionProvider(selectionProvider)
                .build();
            var g2 = spyGraphics();
            g2.setColor(INITIAL_COLOR);

            renderer.drawStaffLines(g2, invariants);

            // setColor(selectionColor) once inside the block; restore sets INITIAL_COLOR back
            verify(g2).setColor(ScoreView.getSelectionColor());
            verify(g2, never()).setColor(RenderingUtils.STAFF_LINE_COLOR);
        }

        /**
         * When NOT in edit mode (even with a selection provider that reports selected),
         * the selection color is not used — only {@link RenderingUtils#STAFF_LINE_COLOR}.
         */
        @Test
        void testSelectedLineOutsideEditModeUsesStaffLineColor() {
            final int lineIndex = 0;
            var selectionProvider = mock(LineComponent.SelectionProvider.class);
            when(selectionProvider.isLineSelected(lineIndex)).thenReturn(true);

            var invariants = seededBuilder()
                .setEditMode(false)   // editMode is false
                .setLineIndex(lineIndex)
                .setSelectionProvider(selectionProvider)
                .build();
            var g2 = spyGraphics();
            g2.setColor(INITIAL_COLOR);

            renderer.drawStaffLines(g2, invariants);

            verify(g2).setColor(RenderingUtils.STAFF_LINE_COLOR);
            verify(g2, never()).setColor(ScoreView.getSelectionColor());
        }
    }

    // -------------------------------------------------------------------------
    // renderWithPreviewShiftIfNeeded — translation branching (row 17)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RenderWithPreviewShiftIfNeeded {

        private static final double SHIFT_SS = 2.5;
        private static final int FROM_INDEX = 3;
        private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

        private Graphics2D g2;
        private AffineTransform identityTransform;

        @BeforeEach
        void setUp() {
            var img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
            g2 = img.createGraphics();
            identityTransform = g2.getTransform();
        }

        @AfterEach
        void tearDown() {
            g2.dispose();
        }

        /**
         * When the frame has a preview shift and {@code spanStart >= fromIndex},
         * the render lambda receives a {@code Graphics2D} that has been translated
         * by {@code previewShiftSs} along the X axis.
         * Tested for both the boundary ({@code spanStart == fromIndex}) and
         * beyond it ({@code spanStart > fromIndex}).
         */
        @Test
        void testShiftAppliedWhenSpanStartAtOrAfterFromIndex() {
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);
            var capturedTransform = new AtomicReference<AffineTransform>();

            // Boundary: spanStart exactly equals fromIndex
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, frame, FROM_INDEX, () -> capturedTransform.set(g2.getTransform())
            );
            assertThat(capturedTransform.get().getTranslateX())
                .as("render lambda sees x-translation of previewShiftSs when spanStart == fromIndex")
                .isEqualTo(identityTransform.getTranslateX() + SHIFT_SS, TOLERANCE);

            // Beyond boundary: spanStart is one past fromIndex
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, frame, FROM_INDEX + 1, () -> capturedTransform.set(g2.getTransform())
            );
            assertThat(capturedTransform.get().getTranslateX())
                .as("render lambda sees x-translation of previewShiftSs when spanStart > fromIndex")
                .isEqualTo(identityTransform.getTranslateX() + SHIFT_SS, TOLERANCE);
        }

        /**
         * When the frame has a preview shift but {@code spanStart < fromIndex},
         * no translation is applied — the render lambda sees the original transform.
         */
        @Test
        void testShiftNotAppliedWhenSpanStartBeforeFromIndex() {
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);
            var capturedTransform = new AtomicReference<AffineTransform>();

            // spanStart is one less than fromIndex → below the boundary
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, frame, FROM_INDEX - 1, () -> capturedTransform.set(g2.getTransform())
            );

            assertThat(capturedTransform.get().getTranslateX())
                .as("render lambda sees unchanged x-translation when spanStart < fromIndex")
                .isEqualTo(identityTransform.getTranslateX(), TOLERANCE);
        }

        /**
         * When the frame has no preview shift ({@link ElementFrame#LINE_LEVEL}),
         * no translation is applied regardless of {@code spanStart}.
         */
        @Test
        void testShiftNotAppliedWhenFrameHasNoPreviewShift() {
            var capturedTransform = new AtomicReference<AffineTransform>();

            // LINE_LEVEL has no preview shift
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, ElementFrame.LINE_LEVEL, 0, () -> capturedTransform.set(g2.getTransform())
            );

            assertThat(capturedTransform.get().getTranslateX())
                .as("render lambda sees unchanged transform when frame has no preview shift")
                .isEqualTo(identityTransform.getTranslateX(), TOLERANCE);
        }

        /**
         * After {@code renderWithPreviewShiftIfNeeded} returns, the transform on
         * {@code g2} is restored to what it was before the call, even when a shift
         * was applied.
         */
        @Test
        void testTransformRestoredAfterShiftedRender() {
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);

            LineRenderer.renderWithPreviewShiftIfNeeded(g2, frame, FROM_INDEX, () -> {});

            assertThat(g2.getTransform().getTranslateX())
                .as("transform is restored to original after renderWithPreviewShiftIfNeeded")
                .isEqualTo(identityTransform.getTranslateX(), TOLERANCE);
        }
    }

    // -------------------------------------------------------------------------
    // getElementColor — grace-cancel coloring layer (row 18)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetElementColor {

        private LineComponent lc;
        private LineRenderer renderer;

        @BeforeEach
        void setUp() {
            lc = mock(LineComponent.class);
            renderer = new LineRenderer(lc);
        }

        /**
         * When the element is currently playing, {@code invariants.getElementColor}
         * returns the playing-note color (non-BLACK). The renderer must return that
         * color directly without applying the grace-cancel RED override.
         */
        @Test
        void testPlayingElementReturnsPlayingColorNotRed() {
            var invariants = seededBuilder()
                .setEditMode(true)
                .setPlayingNoteIndex(0)
                .build();

            var color = renderer.getElementColor(0, invariants);

            var playingColor = ScoreView.getPlayingNoteColor();
            assertThat(color)
                .as("playing element should return the playing color, not RED")
                .isEqualTo(playingColor)
                .isNotEqualTo(Color.RED);
        }

        /**
         * When {@code invariants.getElementColor} returns BLACK (no playing, no
         * selection) and the element is pending grace-note cancellation,
         * {@code getElementColor} must return {@link Color#RED}.
         */
        @Test
        void testPendingCancelElementReturnsRed() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            line.addElement(element);

            var invariants = seededBuilder()
                .setCurrentLine(line)
                .setEditMode(true)
                .build();

            when(lc.getLine()).thenReturn(line);
            when(lc.isPendingCancelElement(element)).thenReturn(true);

            var color = renderer.getElementColor(0, invariants);

            assertThat(color)
                .as("pending-cancel element should be colored RED")
                .isEqualTo(Color.RED);
        }

        /**
         * When {@code invariants.getElementColor} returns BLACK and the element is NOT
         * pending grace-note cancellation, the result must be BLACK (no override).
         */
        @Test
        void testNonPendingCancelBlackElementReturnsBlack() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            line.addElement(element);

            var invariants = seededBuilder()
                .setCurrentLine(line)
                .setEditMode(true)
                .build();

            when(lc.getLine()).thenReturn(line);
            when(lc.isPendingCancelElement(element)).thenReturn(false);

            var color = renderer.getElementColor(0, invariants);

            assertThat(color)
                .as("non-pending-cancel element with BLACK invariant should remain BLACK")
                .isEqualTo(Color.BLACK);
        }
    }

    // -------------------------------------------------------------------------
    // computeOverrideXSs — preview-shift X arithmetic (row 20)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ComputeOverrideXSs {

        private static final int FROM_INDEX = 2;
        private static final double SHIFT_SS = 3.0;
        private static final double ELEMENT_X_SS = 7.0;

        /**
         * When the frame carries a preview shift and the element index is at or after
         * the shift boundary, the override equals the element's layout X plus the shift.
         * Tested for the boundary case ({@code elementIndex == fromIndex}) and beyond.
         */
        @Test
        void testOverrideAppliedWhenIndexAtOrAfterBoundary() {
            var element = ElementType.CROTCHET.newInstance();
            // Use a mock LayoutResult to control getElementXSs without needing package-private setXSs
            var layoutResult = mock(LayoutResult.class);
            when(layoutResult.getElementXSs(element)).thenReturn(ELEMENT_X_SS);
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);

            // Boundary: elementIndex == fromIndex
            var overrideAtBoundary = LineRenderer.computeOverrideXSs(frame, FROM_INDEX, element, layoutResult);
            assertThat(overrideAtBoundary)
                .as("override X at boundary equals layout X + shift")
                .isEqualTo(ELEMENT_X_SS + SHIFT_SS);

            // Beyond boundary: elementIndex > fromIndex
            var overridePastBoundary = LineRenderer.computeOverrideXSs(frame, FROM_INDEX + 1, element, layoutResult);
            assertThat(overridePastBoundary)
                .as("override X past boundary equals layout X + shift")
                .isEqualTo(ELEMENT_X_SS + SHIFT_SS);
        }

        /**
         * When the element index is before the shift boundary, no override is applied
         * and {@link Double#NaN} is returned.
         */
        @Test
        void testNoOverrideWhenIndexBeforeBoundary() {
            var element = ElementType.CROTCHET.newInstance();
            var layoutResult = LayoutResult.builder().build();
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);

            var result = LineRenderer.computeOverrideXSs(frame, FROM_INDEX - 1, element, layoutResult);

            assertThat(result)
                .as("no override when element index is before the shift boundary")
                .isNaN();
        }

        /**
         * When the frame has no preview shift ({@link ElementFrame#LINE_LEVEL}),
         * no override is applied regardless of element index.
         */
        @Test
        void testNoOverrideWhenFrameHasNoPreviewShift() {
            var element = ElementType.CROTCHET.newInstance();
            var layoutResult = LayoutResult.builder().build();

            var result = LineRenderer.computeOverrideXSs(ElementFrame.LINE_LEVEL, FROM_INDEX, element, layoutResult);

            assertThat(result)
                .as("no override when frame has no preview shift")
                .isNaN();
        }

        /**
         * A {@link ElementType#FINAL_DOUBLE_BARLINE} element is never shifted —
         * the override must be {@link Double#NaN} even when all other conditions hold.
         */
        @Test
        void testNoOverrideForFinalDoubleBarline() {
            var element = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
            var layoutResult = LayoutResult.builder().build();
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);

            var result = LineRenderer.computeOverrideXSs(frame, FROM_INDEX, element, layoutResult);

            assertThat(result)
                .as("FINAL_DOUBLE_BARLINE is never shifted — override must be NaN")
                .isNaN();
        }
    }

    // -------------------------------------------------------------------------
    // renderPreviewElement — X-source routing (row 21)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RenderPreviewElementXRouting {

        private static final double LOCKED_X_SS = 5.0;

        private LineComponent lc;
        private LineRenderer renderer;
        private MockedStatic<NoteRenderer> nrMock;
        private MockedStatic<ScoreView> scoreMock;

        @BeforeEach
        void setUp() {
            lc = mock(LineComponent.class);
            renderer = new LineRenderer(lc);
            nrMock = mockStatic(NoteRenderer.class);
            scoreMock = mockStatic(ScoreView.class);

            var mockScore = mock(ScoreView.class);
            var nrInstance = mock(NoteRenderer.class);

            nrMock.when(NoteRenderer::getInstance).thenReturn(nrInstance);
            scoreMock.when(() -> ScoreView.defaultUpperNote(any())).thenReturn(true);
            scoreMock.when(ScoreView::getPreviewElementColor).thenReturn(Color.GRAY);

            when(lc.getScoreView()).thenReturn(mockScore);
            when(mockScore.getMode()).thenReturn(Mode.EDIT);
            when(lc.getPreviewElement()).thenReturn(ElementType.CROTCHET.newInstance());
            when(lc.isPreviewElementVisible()).thenReturn(true);

            PreviewElementManager.setCurrentPreviewLine(lc);
            PreviewElementManager.setCurrentStaffPosition(0);
        }

        @AfterEach
        void tearDown() {
            PreviewElementManager.setCurrentPreviewLine(null);
            scoreMock.close();
            nrMock.close();
        }

        /**
         * In grace mode, the locked X position from {@link LineComponent#getGraceModeLockedXSs()}
         * is used as the preview element X, not {@code calculateInsertionXSs}.
         */
        @Test
        void testGraceModeUsesLockedX() {
            when(lc.isGraceModeInProgress()).thenReturn(true);
            when(lc.getGraceModeLockedXSs()).thenReturn(LOCKED_X_SS);

            var g2 = spyGraphics();
            var invariants = seededBuilder().build();
            renderer.renderPreviewElement(g2, invariants, ElementFrame.LINE_LEVEL);

            // Grace-mode path reads the locked X
            verify(lc).getGraceModeLockedXSs();
            // Normal path: getLine() would be called for calculateInsertionXSs — must NOT happen
            verify(lc, never()).getLine();
        }

        /**
         * In normal (non-grace) mode, the X position comes from
         * {@code layoutResult.calculateInsertionXSs}, which requires the current line
         * from {@link LineComponent#getLine()}.
         */
        @Test
        void testNormalModeUsesLayoutInsertionX() {
            when(lc.isGraceModeInProgress()).thenReturn(false);
            var song = new Song();
            var line = song.getLine(0);
            when(lc.getLine()).thenReturn(line);

            var g2 = spyGraphics();
            var invariants = seededBuilder().build();
            renderer.renderPreviewElement(g2, invariants, ElementFrame.LINE_LEVEL);

            // Normal path reads getLine() to pass to calculateInsertionXSs
            verify(lc).getLine();
            // Grace path must NOT be taken
            verify(lc, never()).getGraceModeLockedXSs();
        }
    }

    // -------------------------------------------------------------------------
    // renderKeyChanges — last-line guard (row 23)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RenderKeyChanges {

        private MockedStatic<KeySignatureRenderer> ksrMock;
        private KeySignatureRenderer ksrInstance;

        @BeforeEach
        void setUp() {
            ksrMock = mockStatic(KeySignatureRenderer.class);
            ksrInstance = mock(KeySignatureRenderer.class);
            ksrMock.when(KeySignatureRenderer::getInstance).thenReturn(ksrInstance);
        }

        @AfterEach
        void tearDown() {
            ksrMock.close();
        }

        /** Builds a minimal {@link LineInvariants} for line 0 of the given song. */
        private LineInvariants invariantsFor(Song song) {
            return LineInvariants.builder(song, DocumentFonts.defaultsFromPrefs())
                .setLayoutResult(LayoutResult.builder().build())
                .setSongLayoutMetrics(new SongLayoutMetrics(0, 0, 0, 0, 0, 0, 0, 0))
                .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0))
                .setLineIndex(0)
                .setCurrentLine(song.getLine(0))
                .build();
        }

        /**
         * When the line is the last line in the song ({@code lineIndex + 1 >= lineCount}),
         * {@code renderKeyChanges} returns immediately without delegating to
         * {@link KeySignatureRenderer#renderKeyChange}.
         */
        @Test
        void testLastLineSkipsRendering() {
            // Song with 1 line → lineIndex=0 is the last line
            var song = new Song();
            var g2 = spyGraphics();

            renderer.renderKeyChanges(g2, invariantsFor(song));

            verify(ksrInstance, never()).renderKeyChange(any(), any(), any(), anyDouble(), any());
        }

        /**
         * When the line is NOT the last line ({@code lineIndex + 1 < lineCount}),
         * {@code renderKeyChanges} delegates to {@link KeySignatureRenderer#renderKeyChange}.
         */
        @Test
        void testNonLastLineDelegatesToRenderer() {
            // Song with 2 lines → lineIndex=0 is not the last line
            var song = new Song();
            song.addLine(new Line(song));  // appends a second line
            var g2 = spyGraphics();

            renderer.renderKeyChanges(g2, invariantsFor(song));

            verify(ksrInstance).renderKeyChange(any(), any(), any(), anyDouble(), any());
        }
    }
}
