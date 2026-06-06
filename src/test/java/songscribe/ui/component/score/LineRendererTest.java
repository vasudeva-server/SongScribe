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
import static org.mockito.Mockito.mock;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.SongLayoutMetrics;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.LineInvariants;
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
}
