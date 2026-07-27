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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.layout.InsertionSpacingCalculator.InsertionResult;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;
import songscribe.engraving.Staff;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link LineComponent} coordinate conversions and layout-state semantics.
 *
 * <p>Tests use a real {@link LineComponent} instance (trivial constructor) and inject
 * dependencies via public setters or package-private fields to avoid triggering the
 * heavyweight layout engine.
 */
class LineComponentTest extends UnitTest {

    /**
     * Content beyond the minimum staff surround, used to build layouts whose measured
     * extents clear the {@link LineSpacing#MIN_ABOVE_MIDLINE_SS} floor — so the tests
     * observe the measured value rather than the floor.
     */
    private static final double CONTENT_HEADROOM_SS = 1.5;

    private static final int LYRICS_FONT_POINT_SIZE = 12;
    private static final Font LYRICS_FONT =
        new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_POINT_SIZE);

    /** Lyric geometry for sizing; the gap is 0 since no verse baseline is asserted. */
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** A clean LineComponent under test. */
    private LineComponent lc;

    @BeforeEach
    void setUp() {
        lc = new LineComponent();
        // Restore default scale so tests that set a custom scale don't pollute others.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    @AfterEach
    void tearDown() {
        // Reset to default scale after each test.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    // -------------------------------------------------------------------------
    // staffPositionToYPx — converts staff position to pixel Y
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPositionToYPx {

        /**
         * For sp=0, the Y is exactly {@code getMiddleLineYPx()} (zero offset).
         */
        @Test
        void testSpZeroReturnsMiddleLineYPx() {
            lc.setMiddleLineYSs(5.0);
            // Default scale: 8 px per ss → middleLineYPx = round(8.0 * 5.0) = 40
            final int expectedMiddleLineYPx = 40;

            assertThat(lc.staffPositionToYPx(0))
                .as("sp=0 → no offset, returns middleLineYPx")
                .isEqualTo(expectedMiddleLineYPx);
        }

        /**
         * For sp=2, offset = round(ssToPx(spToSs(2))) = round(8.0 * 0.5 * 2) = round(8.0) = 8 px.
         * So result = middleLineYPx + 8.
         */
        @Test
        void testPositiveSpAddsDownwardOffset() {
            lc.setMiddleLineYSs(5.0);
            final int expectedMiddleLineYPx = 40;
            final int expectedOffset = 8; // round(8.0 * STAFF_POSITION_OFFSET_SS * 2) = round(8.0)

            assertThat(lc.staffPositionToYPx(2))
                .as("sp=2 → middleLineYPx + 8")
                .isEqualTo(expectedMiddleLineYPx + expectedOffset);
        }

        /**
         * For sp=-2, offset = -8 (upward in Y-down coords).
         */
        @Test
        void testNegativeSpAddsUpwardOffset() {
            lc.setMiddleLineYSs(5.0);
            final int expectedMiddleLineYPx = 40;
            final int expectedOffset = 8; // magnitude

            assertThat(lc.staffPositionToYPx(-2))
                .as("sp=-2 → middleLineYPx - 8")
                .isEqualTo(expectedMiddleLineYPx - expectedOffset);
        }
    }

    // -------------------------------------------------------------------------
    // getMiddleLineYPx — rounds ssToPx(middleLineYSs) to int
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetMiddleLineYPx {

        /**
         * Default scale (8 px/ss): middleLineYSs=5.0 → round(40.0) = 40.
         */
        @Test
        void testExactValueRoundsCorrectly() {
            lc.setMiddleLineYSs(5.0);

            assertThat(lc.getMiddleLineYPx())
                .as("5.0 ss × 8 px/ss = 40 px (exact)")
                .isEqualTo(40);
        }

        /**
         * With a fractional ss value, the result rounds to nearest int.
         * E.g. 5.1 ss × 8 px/ss = 40.8 → rounds to 41.
         */
        @Test
        void testFractionalValueRoundsToNearestInt() {
            final double middleLineYSs = 5.1;
            lc.setMiddleLineYSs(middleLineYSs);
            // round(8.0 * 5.1) = round(40.8) = 41
            final int expected = (int) Math.round(ScaleContext.ssToPx(middleLineYSs));

            assertThat(lc.getMiddleLineYPx())
                .as("5.1 ss × 8 px/ss = 40.8 → rounded to 41")
                .isEqualTo(expected);
        }
    }

    // -------------------------------------------------------------------------
    // calculateMiddleLineYSs — the line's own painted above-midline extent
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateMiddleLineYSs {

        /**
         * When {@code layoutResult} is already present (not dirty), {@code getMiddleLineYSs()}
         * computes {@code aboveStaffSs + STAFF_HALF_SS} from this line's own layout, without
         * re-running layout and without consulting any song-wide value.
         *
         * <p>The layout is built with more content above the staff than the minimum staff
         * surround so the measured extent, not {@link LineSpacing#MIN_ABOVE_MIDLINE_SS},
         * is what the midline placement reports.
         */
        @Test
        void testReturnsAboveStaffSsPlusHalfStaff() {
            final var contentAboveStaffSs = Staff.MIN_ABOVE_STAFF_SS + CONTENT_HEADROOM_SS;
            var layout = LayoutResult.builder()
                .setContentAboveStaffSs(contentAboveStaffSs)
                .build();

            // Inject layout state: result present and not dirty, so performLayout() is skipped.
            lc.layoutResult = layout;
            lc.layoutDirty = false;
            // song must be non-null to trigger the lazy-calculation branch.
            lc.song = mock(Song.class);
            // middleLineYSs starts at 0.0 (JVM default) → lazy calc fires.

            assertThat(lc.getMiddleLineYSs())
                .as("midline sits at this line's own contentAboveStaffSs + STAFF_HALF_SS")
                .isEqualTo(contentAboveStaffSs + Staff.STAFF_HALF_SS);
        }

        /**
         * A line whose ink stops at the staff top still places its midline at the minimum
         * staff surround, not at {@code STAFF_HALF_SS}: {@code StaffLinesLayout} floors a
         * line's bounds there, and the staff must sit where that reservation put it or it
         * would be drawn clear of its own component.
         */
        @Test
        void testSparseLineUsesMinimumAboveMidlineFloor() {
            // No content above the staff at all.
            var layout = LayoutResult.builder().build();

            lc.layoutResult = layout;
            lc.layoutDirty = false;
            lc.song = mock(Song.class);

            assertThat(lc.getMiddleLineYSs())
                .as("no content above the staff → midline floored at MIN_ABOVE_MIDLINE_SS")
                .isEqualTo(LineSpacing.MIN_ABOVE_MIDLINE_SS);
        }
    }

    // -------------------------------------------------------------------------
    // getPreferredSize — null guard and pixel dimensions
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetPreferredSize {

        /**
         * When {@code song} is null, {@code getPreferredSize()} returns {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            // song is null by default on a fresh LineComponent.
            assertThat(lc.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When {@code line} is null (song set but no line), returns {@code (0, 0)}.
         */
        @Test
        void testNullLineReturnsDimensionZero() {
            lc.song = mock(Song.class);
            // line stays null (not set via setLine).

            assertThat(lc.getPreferredSize())
                .as("null line → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * With song, line, and an injected layout result, {@code getPreferredSize()} computes
         * ceiling-rounded pixel dimensions from the song's line width and this line's own
         * painted height.
         */
        @Test
        void testNonNullInputsReturnCeilingRoundedDimension() {
            // Sizing goes through the ViewScale seam, which scales by the fixed
            // DEFAULT_PIXELS_PER_STAFF_SPACE times the zoom factor — deliberately not the
            // mutable ScaleContext pps, so overriding that here would not affect the result.
            final double pxPerSs = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

            // The line's own painted height, not a song-wide one: each line is now sized
            // to its own content.
            final double paintLineHeightSs = 9.5;

            var mockScoreView = mock(ScoreView.class);
            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);
            when(mockScoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);

            // Set ScoreView before setting line (lineSelectionState is null → no coordinator call).
            lc.setScoreView(mockScoreView);

            // Set a real song/line to pass the null guards. The width is stubbed rather than
            // left at its default: outside the running app that default is a page-derived
            // value the test does not control (and is 0.0 when no provider is installed), so
            // a derived expectation could match a width read from the wrong source entirely.
            final double lineWidthSs = 42.5;
            var song = spy(new Song());
            doReturn(lineWidthSs).when(song).getLineWidthSs();
            lc.song = song;

            var line = song.getLine(0);
            // Use setLine so lineSelectionState is created.
            lc.setLine(line, 0);

            // Inject a mock layout result so performLayout() is not called. The layout drives
            // only the height — the width comes from the song, not from where the line's last
            // element happens to sit (issue #578).
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.paintLineHeightSs(LYRIC_RENDER_METRICS)).thenReturn(paintLineHeightSs);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;

            var size = lc.getPreferredSize();

            assertThat(size.width)
                .as("width = ceil(song lineWidthSs → view px)")
                .isEqualTo((int) Math.ceil(pxPerSs * lineWidthSs));

            assertThat(size.height)
                .as("height = ceil(this line's paintLineHeightSs → view px)")
                .isEqualTo((int) Math.ceil(pxPerSs * paintLineHeightSs));
        }
    }

    // -------------------------------------------------------------------------
    // ensureLayout / invalidateLayout — dirty-flag semantics
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutDirtySemantics {

        /**
         * After {@link LineComponent#invalidateLayout()}, {@code layoutResult} is null
         * and {@code layoutDirty} is true.
         */
        @Test
        void testInvalidateLayoutNullsResultAndSetsDirty() {
            // Prime with a non-null layout result.
            lc.layoutResult = mock(LayoutResult.class);
            lc.layoutDirty = false;

            lc.invalidateLayout();

            assertThat(lc.getLayoutResult())
                .as("invalidateLayout nulls the cached result")
                .isNull();

            assertThat(lc.layoutDirty)
                .as("invalidateLayout marks layout as dirty")
                .isTrue();
        }

        /**
         * When song and line are null, {@link LineComponent#ensureLayout()} is a no-op —
         * it does not attempt to run the layout engine.
         */
        @Test
        void testEnsureLayoutDoesNothingWhenSongAndLineAreNull() {
            // song and line are both null by default.
            lc.layoutResult = null;
            lc.layoutDirty = true;

            // No exception, and result stays null (layout engine not called).
            lc.ensureLayout();

            assertThat(lc.getLayoutResult())
                .as("ensureLayout with null song/line leaves result null")
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // setLine — state transitions
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetLine {

        /**
         * After {@link LineComponent#setLine}, {@code layoutDirty} is true, {@code layoutResult}
         * is null, and a new {@link songscribe.ui.selection.LineSelectionState} for the
         * supplied line is created.
         */
        @Test
        void testSetLineSetsLayoutDirtyAndNullsResultAndCreatesSelectionState() {
            var song = new Song();
            var line = song.getLine(0);

            // Prime with stale state to confirm reset.
            lc.layoutResult = mock(LayoutResult.class);
            lc.layoutDirty = false;

            lc.setLine(line, 0);

            assertThat(lc.layoutDirty)
                .as("setLine marks layout dirty")
                .isTrue();

            assertThat(lc.getLayoutResult())
                .as("setLine nulls the cached layout result")
                .isNull();

            var selectionState = lc.getLineSelectionState();
            assertThat(selectionState)
                .as("setLine creates a non-null LineSelectionState")
                .isNotNull();

            if (selectionState != null) {
                assertThat(selectionState.getLine())
                    .as("LineSelectionState wraps the supplied line")
                    .isSameAs(line);
            }
        }

        /**
         * When a {@link ScoreView} is already set and {@link LineComponent#setLine} is called,
         * the new {@link songscribe.ui.selection.LineSelectionState} is registered with
         * the selection coordinator.
         */
        @Test
        void testSetLineRegistersSelectionStateWithCoordinatorWhenScoreViewIsSet() {
            var song = new Song();
            var line = song.getLine(0);

            var mockScoreView = mock(ScoreView.class);
            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);

            // scoreView is set before line; setScoreView() only registers if lineSelectionState
            // is already set (it isn't yet), so no premature coordinator call.
            lc.setScoreView(mockScoreView);

            lc.setLine(line, 0);

            var lineSelectionState = lc.getLineSelectionState();
            assertThat(lineSelectionState)
                .as("setLine creates a non-null LineSelectionState")
                .isNotNull();

            if (lineSelectionState != null) {
                verify(mockCoordinator)
                    .registerLineState(0, lineSelectionState);
            }
        }
    }

    // -------------------------------------------------------------------------
    // readyLayout — null contract (row 15)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReadyLayout {

        /**
         * When {@code line} is null, {@code readyLayout()} returns null.
         */
        @Test
        void testNullLineReturnsNull() {
            // line is null by default
            assertThat(lc.readyLayout())
                .as("null line → readyLayout returns null")
                .isNull();
        }

        /**
         * When a line and a non-null layout result are both set, {@code readyLayout()}
         * returns a non-null {@link LineComponent.ReadyLayout} wrapping them.
         */
        @Test
        void testNonNullLineAndLayoutReturnsReadyLayout() {
            var song = new Song();
            var line = song.getLine(0);
            lc.song = song;
            lc.setLine(line, 0);

            var mockLayout = mock(LayoutResult.class);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;

            var ready = lc.readyLayout();
            assertThat(ready)
                .as("non-null line + layout → readyLayout is non-null")
                .isNotNull();

            if (ready != null) {
                assertThat(ready.line())
                    .as("ReadyLayout.line() returns the set line")
                    .isSameAs(line);
                assertThat(ready.layoutResult())
                    .as("ReadyLayout.layoutResult() returns the set layout")
                    .isSameAs(mockLayout);
            }
        }

        /**
         * When {@code line} is set but {@code layoutResult} is null and {@code song} is also null
         * (so {@code ensureLayout} is a no-op), {@code readyLayout()} returns null.
         */
        @Test
        void testNullLayoutAfterEnsureReturnsNull() {
            // Set line but leave song null so ensureLayout() is a no-op
            // (ensureLayout checks song != null before running layout)
            var song = new Song();
            lc.setLine(song.getLine(0), 0);
            // song is not set on lc so ensureLayout() does nothing; layoutResult stays null

            assertThat(lc.readyLayout())
                .as("line set but layout stays null after ensureLayout() no-op → null")
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // mouseClicked routing — right-button guard and grace-mode branch (row 12)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseClickedRouting {

        /**
         * A right-button click (BUTTON3) must return immediately, before any call to
         * {@code EditModeManager.getGraceModeManager()}.
         *
         * <p>Verifiable because: if the right-button guard is removed, the code calls
         * {@code getGraceModeManager()} which calls {@code EditModeManager.getGraceModeManager()};
         * without the static mock active the singleton is not initialized and that call throws
         * {@link AssertionError} (via {@code RuntimeError.exit}).
         * Passing without a static mock confirms the early return fires.
         */
        @Test
        void testRightButtonClickExitsBeforeGraceModeCheck() {
            var event = mouseEvent(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON3);
            // No mock for EditModeManager — if the right-button guard is removed this throws
            lc.mouseClicked(event);
            // Reaching here without AssertionError confirms the early return fired.
        }

        /**
         * When the grace-mode manager returns {@code true} from {@code mouseClicked},
         * the event is consumed and no further processing occurs.
         *
         * <p>Observable: if the grace-mode result is not respected, processing continues
         * to {@code selectionHandler.handleClick} which calls {@code lc.getScoreView()};
         * since {@code scoreView} is null this throws. Passing confirms the early return.
         */
        @Test
        void testGraceModeConsumingClickCausesEarlyReturn() {
            var event = mouseEvent(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1);
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.mouseClicked(any(LineComponent.class), any(MouseEvent.class)))
                .thenReturn(true);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                // scoreView is null — if not consumed this would throw via getScoreView()
                lc.mouseClicked(event);
            }
            // Reaching here confirms grace-mode consumed the event.
        }
    }

    // -------------------------------------------------------------------------
    // mousePressed routing — right-button guard and grace-mode branch (row 13)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MousePressedRouting {

        /**
         * A right-button press (BUTTON3) must return immediately before any call to
         * {@code EditModeManager.getGraceModeManager()}.
         */
        @Test
        void testRightButtonPressExitsBeforeGraceModeCheck() {
            var event = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3);
            // No static mock — if the guard fires the method returns cleanly.
            lc.mousePressed(event);
        }

        /**
         * When the grace-mode manager consumes the press ({@code mousePressed} returns true),
         * no further processing occurs.
         *
         * <p>Observable: without early return, {@code noteDragHandler.handlePress(e)} would
         * be called; that handler calls {@code lc.getScoreView()} (null → throws).
         */
        @Test
        void testGraceModeConsumingPressCausesEarlyReturn() {
            var event = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.mousePressed(any(LineComponent.class), any(MouseEvent.class)))
                .thenReturn(true);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                lc.mousePressed(event);
            }
            // Reaching here confirms grace-mode consumed the press.
        }
    }

    // -------------------------------------------------------------------------
    // mouseDragged routing — paste mode suppresses rubber-band selection
    // -------------------------------------------------------------------------

    /**
     * A rubber-band drag announces itself to the selection coordinator via
     * {@code dragDidStart} before it does anything else, so that call is what these tests
     * observe. Paste mode already suppresses the press that would start a band; without the
     * matching guard in {@code mouseDragged}, the drag would still band from a stale press
     * point.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseDraggedRouting {

        /** Bounds large enough to contain the helper event's point. */
        private static final Dimension DRAG_COMPONENT_SIZE = new Dimension(100, 50);

        private SelectionCoordinator coordinator;

        @BeforeEach
        void setUp() {
            coordinator = mock(SelectionCoordinator.class);

            var mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // SELECT mode is what makes selection handling active for the drag.
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            lc.setScoreView(mockScoreView);

            // A non-null line is the other half of the selection-active gate.
            var song = new Song();
            lc.song = song;
            lc.setLine(song.getLine(0), 0);

            // The drag clamps to the component bounds, which a zero-sized component
            // inverts into an empty range.
            lc.setSize(DRAG_COMPONENT_SIZE);
        }

        /** Runs {@code mouseDragged} with paste mode reporting the given in-progress state. */
        private void dragWithPasteInProgress(boolean inProgress) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);
            when(pasteMock.isInProgress()).thenReturn(inProgress);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);

                lc.mouseDragged(mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1));
            }
        }

        @Test
        void testDragDuringPasteModeDoesNotStartASelection() {
            dragWithPasteInProgress(true);

            verify(coordinator, never()).dragDidStart(any());
        }

        @Test
        void testDragOutsidePasteModeStartsASelection() {
            dragWithPasteInProgress(false);

            verify(coordinator).dragDidStart(lc);
        }
    }

    // -------------------------------------------------------------------------
    // gracePreviewLineFrame — LINE_LEVEL vs. shifted frame (row 14)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GracePreviewLineFrame {

        /**
         * When this {@code LineComponent} is NOT the active grace line,
         * {@link LineComponent#gracePreviewLineFrame()} returns the canonical
         * {@link ElementFrame#LINE_LEVEL} constant.
         */
        @Test
        void testReturnsLineLevelWhenNotActiveGraceLine() {
            var graceMock = mock(GraceModeManager.class);
            // getGraceLineComponent() returns a different lc (or null) → not this lc
            when(graceMock.getGraceLineComponent()).thenReturn(null);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);

                assertThat(lc.gracePreviewLineFrame())
                    .as("not the active grace line → returns LINE_LEVEL")
                    .isSameAs(ElementFrame.LINE_LEVEL);
            }
        }

        /**
         * When this {@code LineComponent} IS the active grace line and a host insertion
         * preview is available, {@code gracePreviewLineFrame()} returns a frame carrying
         * the preview shift amount and from-index.
         */
        @Test
        void testReturnsShiftedFrameWhenThisIsActiveGraceLineWithPreview() {
            final double shiftSs = 3.5;
            final int insertionIndex = 2;
            // Only the shift is read here; the projected spring chain the fit gate would
            // solve is irrelevant to the preview frame, so an empty chain stands in for it.
            var preview = new InsertionResult(0.0, shiftSs, 0.0, List.of(), 0.0, 0.0);

            var graceMock = mock(GraceModeManager.class);
            when(graceMock.getGraceLineComponent()).thenReturn(lc);
            when(graceMock.getHostInsertionPreview()).thenReturn(preview);
            // getHostInsertionIndex() returns graceNoteIndex + 1; mock directly
            when(graceMock.getHostInsertionIndex()).thenReturn(insertionIndex);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);

                var frame = lc.gracePreviewLineFrame();

                assertThat(frame.hasPreviewShift())
                    .as("active grace line with preview → frame has preview shift")
                    .isTrue();
                assertThat(frame.previewShiftSs())
                    .as("previewShiftSs equals the InsertionResult shiftSs")
                    .isEqualTo(shiftSs);
                assertThat(frame.previewShiftFromIndex())
                    .as("fromIndex equals the insertion index")
                    .isEqualTo(insertionIndex);
            }
        }

        /**
         * When this {@code LineComponent} IS the active grace line but the host insertion
         * preview is null (no preview available), {@code gracePreviewLineFrame()} returns
         * {@link ElementFrame#LINE_LEVEL}.
         */
        @Test
        void testReturnsLineLevelWhenThisIsActiveGraceLineButNoPreview() {
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.getGraceLineComponent()).thenReturn(lc);
            when(graceMock.getHostInsertionPreview()).thenReturn(null);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);

                assertThat(lc.gracePreviewLineFrame())
                    .as("active grace line but null preview → LINE_LEVEL")
                    .isSameAs(ElementFrame.LINE_LEVEL);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Double-click on an element opens the lyric editor
    // -------------------------------------------------------------------------

    /**
     * These tests drive the real {@code mouseClicked} routing and assert on whether the lyric
     * editor was asked to open. Two collaborators are stubbed so the tests isolate the routing
     * rather than re-testing logic covered elsewhere: which element lies under the cursor
     * (covered by {@link ElementHitTestTest}) and which element a gesture resolves to (covered
     * by {@code LyricEditorEligibilityTest}). Opening is observed by stubbing
     * {@link LyricEditor} statically, since the call to {@code deselectAndOpenOn} is the
     * method's only observable effect.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DoubleClickLyricEditing {

        /** Index the stubbed hit test reports as lying under the cursor. */
        private static final int HIT_INDEX = 0;

        private ScoreView mockScoreView;
        private Line line;

        @BeforeEach
        void setUp() {
            mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // SELECT mode with no editor open is the state the gesture is designed for.
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            when(mockScoreView.getActiveLyricEditor()).thenReturn(null);
            lc.setScoreView(mockScoreView);

            var song = new Song();
            line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));
            lc.song = song;
            lc.setLine(line, 0);
            // Inject a clean layout so the heavyweight layout engine never runs. The stub
            // reports no lyric under the cursor, so the click falls through to the element
            // branch under test rather than the double-click-on-lyric-text branch above it.
            lc.layoutResult = mock(LayoutResult.class);
            lc.layoutDirty = false;
        }

        /**
         * Runs {@code mouseClicked} with the edit-mode managers and the element hit test
         * stubbed, and hands the test the static LyricEditor stub to assert against.
         */
        private void clickWith(MouseEvent event, Consumer<MockedStatic<LyricEditor>> assertions) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                 MockedStatic<ElementHitTest> hitTest = mockStatic(ElementHitTest.class);
                 MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);
                hitTest.when(() -> ElementHitTest.hitTestElement(any(LineComponent.class), any(Point.class)))
                    .thenReturn(HIT_INDEX);

                lc.mouseClicked(event);

                assertions.accept(lyricEditor);
            }
        }

        @Test
        void testPlainDoubleClickOpensTheEditorOnTheClickedElement() {
            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                lyricEditor -> lyricEditor.verify(
                    () -> LyricEditor.deselectAndOpenOn(mockScoreView, line, HIT_INDEX)));
        }

        @Test
        void testSingleClickDoesNotOpenTheEditor() {
            // Without the click-count check every ordinary click on a note would pop open
            // a text field instead of selecting the note.
            clickWith(
                clickEvent(SINGLE_CLICK, NO_MODIFIERS),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testShiftDoubleClickDoesNotOpenTheEditor() {
            // Shift+click extends a selection. This method runs before the selection handler
            // sees the click, so without the guard it would discard the selection being built.
            clickWith(
                clickEvent(DOUBLE_CLICK, MouseEvent.SHIFT_DOWN_MASK),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testDoubleClickDoesNotOpenASecondEditorWhileOneIsOpen() {
            // Without this guard a second editor would be stacked on the first, losing
            // whatever the user had typed into it.
            when(mockScoreView.getActiveLyricEditor()).thenReturn(mock(LyricEditor.class));

            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                lyricEditor -> lyricEditor.verify(
                    () -> LyricEditor.deselectAndOpenOn(any(), any(), anyInt()), never()));
        }

        @Test
        void testDoubleClickOutsideSelectModeDoesNotOpenTheEditor() {
            // Adjustment modes are not a context for editing lyrics.
            when(mockScoreView.getMode()).thenReturn(Mode.ADJUSTMENT);

            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testDoubleClickThatHitsNoElementDoesNotOpenTheEditor() {
            // Without the miss guard the editor would be asked to open on element -1.
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                 MockedStatic<ElementHitTest> hitTest = mockStatic(ElementHitTest.class);
                 MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);
                hitTest.when(() -> ElementHitTest.hitTestElement(any(LineComponent.class), any(Point.class)))
                    .thenReturn(NO_HIT);

                lc.mouseClicked(clickEvent(DOUBLE_CLICK, NO_MODIFIERS));

                lyricEditor.verifyNoInteractions();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Click count identifying a single click in a {@link MouseEvent}. */
    private static final int SINGLE_CLICK = 1;

    /** Click count identifying a double-click in a {@link MouseEvent}. */
    private static final int DOUBLE_CLICK = 2;

    /** Empty modifier mask, for a click with no modifier key held. */
    private static final int NO_MODIFIERS = 0;

    /** Sentinel returned by the element hit test when the point hit no element. */
    private static final int NO_HIT = -1;

    /** Creates a left-button click event with the given click count and modifier mask. */
    private static MouseEvent clickEvent(int clickCount, int modifiers) {
        var source = new JPanel();
        return new MouseEvent(
            source,
            MouseEvent.MOUSE_CLICKED,
            0L,         // when (not examined by production code)
            modifiers,
            10, 10,     // x, y
            10, 10,     // xAbs, yAbs
            clickCount,
            false,      // popupTrigger
            MouseEvent.BUTTON1
        );
    }

    /** Creates a minimal mouse event with the given id and button on a fresh JPanel source. */
    private static MouseEvent mouseEvent(int id, int button) {
        var source = new JPanel();
        return new MouseEvent(
            source,
            id,
            0L,         // when (not examined by production code)
            0,          // modifiers
            10, 10,     // x, y
            10, 10,     // xAbs, yAbs
            1,          // clickCount
            false,      // popupTrigger
            button
        );
    }
}
