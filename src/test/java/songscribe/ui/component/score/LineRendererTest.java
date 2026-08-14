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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.Tie;
import songscribe.engraving.Staff;
import songscribe.font.DocumentFonts;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.KeySignatureRenderer;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.renderer.RenderingUtils;

/**
 * Unit tests for {@link LineRenderer} package-private methods:
 * {@link LineRenderer#drawStaffLines} and {@link LineRenderer#renderWithPreviewShiftIfNeeded}.
 */
class LineRendererTest extends UnitTest {

    private static final int FONT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);

    /** A key a second line can establish that no line inherits, so the boundary really changes. */
    private static final Key NEXT_LINE_KEY = new Key(KeyType.SHARPS, 3);

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
     * Returns a {@code LineInvariants.LayoutResultBuilder} seeded with minimal required fields
     * so each test only configures the state it cares about.
     */
    private static LineInvariants.Builder seededBuilder() {
        return LineInvariants.builder(new Song(), DocumentFonts.defaultFonts())
            .setLayoutResult(LayoutResult.builder().build())
            .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0, 0));
    }

    /** {@link #seededBuilder} with a layout that could not fit the staff width. */
    private static LineInvariants.Builder overflowingBuilder() {
        return seededBuilder()
            .setLayoutResult(LayoutResult.builder().setOverflowsStaffWidth(true).build());
    }

    /** Creates a real {@code Graphics2D} backed by an off-screen image, wrapped in a spy. */
    private static Graphics2D spyGraphics() {
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
         * The color an over-full line's staff lines are drawn in. Read on demand rather than held in
         * a static field: the lookup needs the theme installed, and a field would run it while this
         * class initializes, which is not ordered against the {@code @BeforeAll} that installs it. A
         * lookup with no theme calls {@code RuntimeError.exit} rather than failing the test cleanly.
         * Matches how the neighbouring tests read {@link ScoreView#getSelectionColor()}.
         */
        private static Color overflowColor() {
            return FlatLafProps.getColor(FlatLafKey.SCORE_STAFF_LINE_OVERFLOW_COLOR);
        }

        /**
         * When the line is NOT selected (default — no selection provider),
         * {@code drawStaffLines} sets the color to {@link RenderingUtils#STAFF_LINE_COLOR}
         * (black), not the selection color.
         */
        @Test
        void testNonSelectedLineUsesStaffLineColor() {
            var invariants = seededBuilder()
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
         * When the selection provider reports the line as selected,
         * {@code drawStaffLines} sets the color to {@link ScoreView#getSelectionColor()},
         * not the default staff-line color.
         */
        @Test
        void testSelectedLineUsesSelectionColor() {
            final var lineIndex = 0;
            var selectionProvider = mock(LineComponent.SelectionProvider.class);
            when(selectionProvider.isSelected(new HitTarget.StaffLine(), lineIndex)).thenReturn(true);

            var invariants = seededBuilder()
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
         * A line whose content could not fit the staff draws its staff lines in the overflow
         * color, which is the user's standing indication that the tail of the line is clipped
         * (refs #696).
         */
        @Test
        void testOverflowingLineUsesOverflowColor() {
            var invariants = overflowingBuilder()
                // no selectionProvider set → staffSelected = false
                .build();
            var g2 = spyGraphics();
            g2.setColor(INITIAL_COLOR);

            renderer.drawStaffLines(g2, invariants);

            verify(g2).setColor(overflowColor());
            verify(g2, never()).setColor(RenderingUtils.STAFF_LINE_COLOR);
        }

        /**
         * Selection wins over overflow: it is the transient state the user is acting on, and the
         * overflow color returns the moment the line is deselected.
         */
        @Test
        void testSelectedOverflowingLineUsesSelectionColor() {
            final var lineIndex = 0;
            var selectionProvider = mock(LineComponent.SelectionProvider.class);
            when(selectionProvider.isSelected(new HitTarget.StaffLine(), lineIndex)).thenReturn(true);

            var invariants = overflowingBuilder()
                .setLineIndex(lineIndex)
                .setSelectionProvider(selectionProvider)
                .build();
            var g2 = spyGraphics();
            g2.setColor(INITIAL_COLOR);

            renderer.drawStaffLines(g2, invariants);

            verify(g2).setColor(ScoreView.getSelectionColor());
            verify(g2, never()).setColor(overflowColor());
        }
    }

    // -------------------------------------------------------------------------
    // previewShiftStartOf — which index the preview shift weighs a tie against
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PreviewShiftStartOf {

        private static final int ANCHOR_INDEX = 1;

        private Song song;
        private Line firstLine;
        private Line secondLine;
        private Tie tie;

        /**
         * A tie whose anchor is the last note of the first line and whose end is the first
         * element of the second — the only shape a cross-line tie takes (#493). The anchor is
         * deliberately not at index 0, so an answer of 0 for the anchor line would be visibly
         * wrong rather than accidentally right.
         */
        @BeforeEach
        void setUp() {
            song = new Song();
            firstLine = song.getLine(0);
            secondLine = new Line(song);

            var unrelated = crotchet();
            var anchor = crotchet();
            var end = crotchet();
            tie = new Tie(anchor, end);

            song.withoutMutationTracking(() -> {
                firstLine.addElement(unrelated);
                firstLine.addElement(anchor);
                song.addLine(secondLine);
                secondLine.addElement(end);
                firstLine.addTie(tie);
            });
        }

        @Test
        void testAnchorLineWeighsTheTieAgainstTheAnchorsOwnIndex() {
            assertThat(LineRenderer.previewShiftStartOf(tie, firstLine))
                .as("the anchor sits in this line, so its real position decides")
                .isEqualTo(ANCHOR_INDEX);
        }

        @Test
        void testContinuationHalfEntersAtTheStartOfItsLine() {
            // The anchor is off this line's left edge, so the half enters at element 0 and
            // shifts exactly when the whole line does. Reading the index off the tie instead
            // would hand this line the anchor's position in the *previous* line, and the half
            // would shift or not according to where that note happened to sit over there.
            assertThat(LineRenderer.previewShiftStartOf(tie, secondLine))
                .as("the continuation half enters at the start of its line")
                .isZero();
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
            var capturedTransform = new AtomicReference<@Nullable AffineTransform>();

            // Boundary: spanStart exactly equals fromIndex
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, frame, FROM_INDEX, () -> capturedTransform.set(g2.getTransform())
            );
            var atBoundary = capturedTransform.get();

            assertThat(atBoundary).as("the render lambda must have run").isNotNull();
            assertThat(atBoundary.getTranslateX())
                .as("render lambda sees x-translation of previewShiftSs when spanStart == fromIndex")
                .isEqualTo(identityTransform.getTranslateX() + SHIFT_SS, TOLERANCE);

            // Beyond boundary: spanStart is one past fromIndex
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, frame, FROM_INDEX + 1, () -> capturedTransform.set(g2.getTransform())
            );

            var pastBoundary = capturedTransform.get();

            assertThat(pastBoundary).as("the render lambda must have run").isNotNull();
            assertThat(pastBoundary.getTranslateX())
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
            var capturedTransform = new AtomicReference<@Nullable AffineTransform>();

            // spanStart is one less than fromIndex → below the boundary
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, frame, FROM_INDEX - 1, () -> capturedTransform.set(g2.getTransform())
            );

            var captured = capturedTransform.get();

            assertThat(captured).as("the render lambda must have run").isNotNull();
            assertThat(captured.getTranslateX())
                .as("render lambda sees unchanged x-translation when spanStart < fromIndex")
                .isEqualTo(identityTransform.getTranslateX(), TOLERANCE);
        }

        /**
         * When the frame has no preview shift ({@link ElementFrame#LINE_LEVEL}),
         * no translation is applied regardless of {@code spanStart}.
         */
        @Test
        void testShiftNotAppliedWhenFrameHasNoPreviewShift() {
            var capturedTransform = new AtomicReference<@Nullable AffineTransform>();

            // LINE_LEVEL has no preview shift
            LineRenderer.renderWithPreviewShiftIfNeeded(
                g2, ElementFrame.LINE_LEVEL, 0, () -> capturedTransform.set(g2.getTransform())
            );

            var captured = capturedTransform.get();

            assertThat(captured).as("the render lambda must have run").isNotNull();
            assertThat(captured.getTranslateX())
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
        private LineRenderer lcRenderer;

        @BeforeEach
        void setUp() {
            lc = mock(LineComponent.class);
            lcRenderer = new LineRenderer(lc);
        }

        /**
         * When the element is currently playing, {@code invariants.getElementColor}
         * returns the playing-note color (non-BLACK). The renderer must return that
         * color directly without applying the grace-cancel RED override.
         */
        @Test
        void testPlayingElementReturnsPlayingColorNotRed() {
            var invariants = seededBuilder()
                .setPlayingNoteIndex(0)
                .build();

            var color = lcRenderer.getElementColor(0, invariants);

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
            var element = crotchet();
            line.addElement(element);

            var invariants = seededBuilder()
                .setCurrentLine(line)
                .build();

            when(lc.getLine()).thenReturn(line);
            when(lc.isPendingCancelElement(element)).thenReturn(true);

            var color = lcRenderer.getElementColor(0, invariants);

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
            var element = crotchet();
            line.addElement(element);

            var invariants = seededBuilder()
                .setCurrentLine(line)
                .build();

            when(lc.getLine()).thenReturn(line);
            when(lc.isPendingCancelElement(element)).thenReturn(false);

            var color = lcRenderer.getElementColor(0, invariants);

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
            var element = crotchet();
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
            var element = crotchet();
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
            var element = crotchet();
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
            var element = finalDoubleBarline();
            var layoutResult = LayoutResult.builder().build();
            var frame = ElementFrame.lineLevelWithPreviewShift(FROM_INDEX, SHIFT_SS);

            var result = LineRenderer.computeOverrideXSs(frame, FROM_INDEX, element, layoutResult);

            assertThat(result)
                .as("FINAL_DOUBLE_BARLINE is never shifted — override must be NaN")
                .isNaN();
        }
    }

    // -------------------------------------------------------------------------
    // renderKeyChanges — the last-line guard and the running keys it compares
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
            return LineInvariants.builder(song, DocumentFonts.defaultFonts())
                .setLayoutResult(LayoutResult.builder().build())
                .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0, 0))
                .setLineIndex(0)
                .setCurrentLine(song.getLine(0))
                .build();
        }

        /** A two-line song whose second line establishes {@code nextKey}. */
        private Song songWithSecondLineIn(Key nextKey) {
            var song = new Song();
            song.addLine(new Line(song));  // appends a second line
            song.withModification(() -> song.getLine(1).setKey(nextKey));

            return song;
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
         * The two keys handed to {@link KeySignatureRenderer#renderKeyChange} are the running
         * keys — the key this line leaves off in, and the key the next line begins in — not
         * the lines' own keys, which the second line here does not have until it is given one.
         */
        @Test
        void testNonLastLinePassesTheRunningKeyOnEachSideOfTheBoundary() {
            var song = songWithSecondLineIn(NEXT_LINE_KEY);
            var g2 = spyGraphics();

            renderer.renderKeyChanges(g2, invariantsFor(song));

            verify(ksrInstance).renderKeyChange(
                any(),
                eq(song.getLine(0).keyAtEndOfLine()),
                eq(NEXT_LINE_KEY),
                anyDouble(),
                any());
        }

        /**
         * A line that inherits its key still reports that key as its running key, so the
         * boundary is handed two equal keys and the renderer is left to draw nothing —
         * the cautionary is not suppressed here.
         */
        @Test
        void testAnInheritingNextLineIsStillDelegatedWithMatchingKeys() {
            var song = new Song();
            song.addLine(new Line(song));  // appends a second line, which inherits
            var g2 = spyGraphics();
            var runningKey = song.getLine(0).keyAtEndOfLine();

            renderer.renderKeyChanges(g2, invariantsFor(song));

            verify(ksrInstance)
                .renderKeyChange(any(), eq(runningKey), eq(runningKey), anyDouble(), any());
        }
    }

    // -------------------------------------------------------------------------
    // renderSelectionBand — the selection band's painted geometry
    // -------------------------------------------------------------------------

    /**
     * The band is a pixel-space overlay drawn after the staff-space transform is restored, so
     * every number in the shape it strokes is arithmetic this method does itself. What is
     * checked here is that arithmetic: the handler publishes a staff-space interval and this
     * is the one place it becomes pixels, the vertical extent comes from the staff rather than
     * from the drag, only the horizontal edges are inset by half the stroke, a zero-width band
     * still draws, and the stroke and corner arc follow the zoom (issue #628).
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RenderSelectionBand {

        /**
         * The 100%-zoom stroke width and corner arc {@code LineRenderer} draws the band with.
         * Mirrored here because the production constants are private; a change to either side
         * that is not made on both fails these tests rather than passing quietly.
         */
        private static final double STROKE_WIDTH_PX = 2.0;
        private static final double ARC_PX = 2.0;

        /** The floor the drawn width is clamped up to, so a zero-width band stays visible. */
        private static final double MIN_WIDTH_PX = 1.0;

        /** Width of the component the band is painted into, in view pixels. */
        private static final int COMPONENT_WIDTH_PX = 200;

        /**
         * Distance from the top of the component to the staff midline, in staff spaces.
         * Non-zero on purpose: a renderer that measured the band's Y from the top of the
         * component would still pass with a zero midline.
         */
        private static final double MIDDLE_LINE_Y_SS = 5.0;

        /** A band well inside the component horizontally, so no inset can bite. */
        private static final double BAND_LEFT_SS = 6.0;
        private static final double BAND_WIDTH_SS = 5.0;

        /**
         * A band wider than {@link #COMPONENT_WIDTH_PX}, so both its edges are past the bounds
         * Swing would clip at and the inset has something to bite on.
         */
        private static final double WIDER_THAN_THE_COMPONENT_SS = 30.0;

        private static final int ZOOMED_PERCENT = 200;

        private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

        private LineComponent lc;
        private LineRenderer bandRenderer;

        @BeforeEach
        void setUp() {
            lc = mock(LineComponent.class);
            bandRenderer = new LineRenderer(lc);

            when(lc.getWidth()).thenReturn(COMPONENT_WIDTH_PX);
            when(lc.getStaffTopYSs()).thenReturn(MIDDLE_LINE_Y_SS - Staff.STAFF_HALF_SS);
            when(lc.getStaffBottomYSs()).thenReturn(MIDDLE_LINE_Y_SS + Staff.STAFF_HALF_SS);
            when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        }

        /** Points the component at a view zoomed to {@link #ZOOMED_PERCENT}. */
        private void givenZoomedView() {
            var viewScale = new ViewScale();
            viewScale.setZoomPercent(ZOOMED_PERCENT);
            when(lc.getViewScale()).thenReturn(viewScale);
        }

        /**
         * Renders the band the handler would publish for {@code [leftSs, leftSs + widthSs]} and
         * returns the rounded rectangle the renderer stroked.
         */
        private RoundRectangle2D renderBand(Graphics2D g2, double leftSs, double widthSs) {
            when(lc.getSelectionBand()).thenReturn(
                new LineSelectionHandler.SelectionBand(leftSs, leftSs + widthSs));

            bandRenderer.renderSelectionBand(g2);

            var captor = ArgumentCaptor.forClass(Shape.class);
            verify(g2).draw(captor.capture());

            assertThat(captor.getValue())
                .as("the band is stroked as a rounded rectangle")
                .isInstanceOf(RoundRectangle2D.class);

            return (RoundRectangle2D) captor.getValue();
        }

        /** A staff-space position as a view pixel at the zoom the component currently reports. */
        private double toViewPx(double ss) {
            return lc.getViewScale().toViewPx(new Ss(ss)).value();
        }

        /**
         * Nothing about the band is stored in pixels, so this method is the one place a
         * staff-space band becomes one. A renderer that read the wrong end of the interval, or
         * dropped the conversion, would put the band somewhere other than over the music it
         * describes.
         */
        @Test
        void testTheHorizontalEdgesAreTheBandsStaffSpaceIntervalConverted() {
            var g2 = spyGraphics();

            var band = renderBand(g2, BAND_LEFT_SS, BAND_WIDTH_SS);

            assertThat(band.getX())
                .as("the left edge is the band's own left, in view pixels")
                .isEqualTo(toViewPx(BAND_LEFT_SS), TOLERANCE);
            assertThat(band.getX() + band.getWidth())
                .as("and the right edge is its right")
                .isEqualTo(toViewPx(BAND_LEFT_SS + BAND_WIDTH_SS), TOLERANCE);
        }

        /**
         * The band's top and bottom are the staff's own, so its border straddles the top and
         * bottom staff lines at a constant height however the mouse moves. The half-stroke
         * inset of issue #643 is gone from this axis: the edges are well inside the component,
         * and insetting would pull the stroke off the lines it is meant to sit on.
         */
        @Test
        void testTheVerticalExtentIsTheStaffAndIsNotInsetByTheStroke() {
            var g2 = spyGraphics();

            var band = renderBand(g2, BAND_LEFT_SS, BAND_WIDTH_SS);

            var expectedTop = toViewPx(MIDDLE_LINE_Y_SS - Staff.STAFF_HALF_SS);
            var expectedBottom = toViewPx(MIDDLE_LINE_Y_SS + Staff.STAFF_HALF_SS);

            assertThat(band.getY())
                .as("the top edge sits exactly on the top staff line")
                .isEqualTo(expectedTop, TOLERANCE);
            assertThat(band.getHeight())
                .as("the band is exactly the staff's height")
                .isEqualTo(expectedBottom - expectedTop, TOLERANCE);
        }

        /**
         * The horizontal inset stays: a band's left and right edges can still reach the
         * component's bounds, where Swing would clip the outer half of a centered stroke.
         */
        @Test
        void testTheHorizontalEdgesAreInsetByHalfTheStroke() {
            var g2 = spyGraphics();

            var band = renderBand(g2, 0, WIDER_THAN_THE_COMPONENT_SS);

            var halfStrokeWidthPx = STROKE_WIDTH_PX / 2;

            assertThat(band.getX())
                .as("the left edge is held half a stroke inside the component")
                .isEqualTo(halfStrokeWidthPx, TOLERANCE);
            assertThat(band.getX() + band.getWidth())
                .as("and the right edge likewise")
                .isEqualTo(COMPONENT_WIDTH_PX - 1 - halfStrokeWidthPx, TOLERANCE);
        }

        /**
         * With the handler's old minimum-extent floor gone, a lead x that returns exactly to the
         * anchor x produces a legitimately zero-width band. It must still stroke a visible line
         * rather than blinking out mid-drag.
         */
        @Test
        void testAZeroWidthBandStillStrokesAtTheMinimumWidth() {
            var g2 = spyGraphics();

            var band = renderBand(g2, BAND_LEFT_SS, 0);

            assertThat(band.getWidth())
                .as("a zero-width band is drawn at the minimum width")
                .isEqualTo(MIN_WIDTH_PX, TOLERANCE);
        }

        /**
         * Nothing is painted when no drag is in progress. A null band is the only signal for
         * that — there is no empty rectangle to test, so a renderer that forgot the check would
         * throw rather than quietly draw nothing.
         */
        @Test
        void testNoBandIsPaintedWhenNoDragIsInProgress() {
            var g2 = spyGraphics();

            when(lc.getSelectionBand()).thenReturn(null);
            bandRenderer.renderSelectionBand(g2);

            verify(g2, never()).draw(any(Shape.class));
        }

        /**
         * The handler holds the band in staff spaces precisely so a zoom mid-drag re-renders it
         * over the same music. This is the half of that guarantee the renderer owns: the same
         * staff-space interval, drawn twice at different zooms, lands at scaled pixels rather
         * than the same ones.
         */
        @Test
        void testTheSameBandIsDrawnAtScaledPixelsWhenTheZoomChanges() {
            var atDefaultZoom = renderBand(spyGraphics(), BAND_LEFT_SS, BAND_WIDTH_SS);
            var widthAtDefaultZoom = atDefaultZoom.getWidth();

            givenZoomedView();

            var zoomed = renderBand(spyGraphics(), BAND_LEFT_SS, BAND_WIDTH_SS);
            var factor = lc.getViewScale().factor();

            assertThat(zoomed.getX())
                .as("the same musical x, at the new scale")
                .isEqualTo(toViewPx(BAND_LEFT_SS), TOLERANCE);
            assertThat(zoomed.getWidth())
                .as("covering the same music, so the drawn width scales with the zoom")
                .isEqualTo(widthAtDefaultZoom * factor, TOLERANCE);
        }

        /**
         * The band is drawn outside the staff-space transform, so nothing scales it implicitly:
         * the stroke width and corner arc have to be multiplied by the zoom factor by hand
         * (issue #628). At 100% the factor is 1.0 and would hide a dropped multiplication, so
         * both zooms are checked.
         */
        @Test
        void testTheStrokeWidthAndArcScaleWithTheZoomFactor() {
            var atDefaultZoom = renderBand(spyGraphics(), BAND_LEFT_SS, BAND_WIDTH_SS);

            assertThat(atDefaultZoom.getArcWidth())
                .as("arc at 100%")
                .isEqualTo(ARC_PX, TOLERANCE);

            givenZoomedView();

            var zoomedG2 = spyGraphics();
            var zoomed = renderBand(zoomedG2, BAND_LEFT_SS, BAND_WIDTH_SS);
            var factor = lc.getViewScale().factor();

            assertThat(zoomed.getArcWidth())
                .as("arc scales with the zoom")
                .isEqualTo(ARC_PX * factor, TOLERANCE);
            assertThat(zoomed.getArcHeight())
                .as("both arc axes scale alike")
                .isEqualTo(ARC_PX * factor, TOLERANCE);
            verify(zoomedG2).setStroke(new BasicStroke((float) (STROKE_WIDTH_PX * factor)));
        }
    }
}
