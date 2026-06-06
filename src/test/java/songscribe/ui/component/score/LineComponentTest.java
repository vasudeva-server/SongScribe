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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.layout.LayoutResult;
import songscribe.layout.SongLayoutMetrics;
import songscribe.layout.StaffExtents;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link LineComponent} coordinate conversions and layout-state semantics.
 *
 * <p>Tests use a real {@link LineComponent} instance (trivial constructor) and inject
 * dependencies via public setters or package-private fields to avoid triggering the
 * heavyweight layout engine.
 */
class LineComponentTest extends UnitTest {

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
    // calculateMiddleLineYSs — aboveStaffSs + STAFF_HALF_SS
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateMiddleLineYSs {

        /**
         * When {@code layoutResult} is already present (not dirty), {@code getMiddleLineYSs()}
         * computes {@code aboveStaffSs + STAFF_HALF_SS} without re-running layout.
         *
         * <p>The test injects a mocked {@link LayoutResult} with a known {@code aboveStaffSs}
         * and drives the lazy computation via {@link LineComponent#getMiddleLineYSs()}.
         */
        @Test
        void testReturnsAboveStaffSsPlusHalfStaff() {
            final double aboveStaffSs = 3.0;
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.getAboveStaffSs()).thenReturn(aboveStaffSs);

            // Inject layout state: result present and not dirty, so performLayout() is skipped.
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;
            // song must be non-null to trigger the lazy-calculation branch.
            lc.song = mock(Song.class);
            // middleLineYSs starts at 0.0 (JVM default) → lazy calc fires.

            assertThat(lc.getMiddleLineYSs())
                .as("calculateMiddleLineYSs returns aboveStaffSs + STAFF_HALF_SS")
                .isEqualTo(aboveStaffSs + StaffExtents.STAFF_HALF_SS);
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
         * ceiling-rounded pixel dimensions from the layout's line width and the song's total
         * line height.
         */
        @Test
        void testNonNullInputsReturnCeilingRoundedDimension() {
            final double pxPerSs = 10.0;
            ScaleContext.setPixelsPerStaffSpace(pxPerSs);

            final double totalLineHeightSs = 9.5;
            final double aboveStaffSs = 2.0;

            // Build a real SongLayoutMetrics with a known totalLineHeightSs.
            var metrics = new SongLayoutMetrics(aboveStaffSs, 0, 0, 0, 0, 0, 0, totalLineHeightSs);

            // Mock ScoreView to return our metrics.
            var mockScoreView = mock(ScoreView.class);
            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);
            when(mockScoreView.getSongLayoutMetrics()).thenReturn(metrics);

            // Set ScoreView before setting line (lineSelectionState is null → no coordinator call).
            lc.setScoreView(mockScoreView);

            // Set a real song/line to pass the null guards.
            var song = new Song();
            lc.song = song;

            var line = song.getLine(0);
            // Use setLine so lineSelectionState is created.
            lc.setLine(line, 0);

            // Inject a mock layout result so performLayout() is not called.
            // getLineWidthSs() on an empty result returns 0.
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.getLineWidthSs()).thenReturn(0.0);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;

            var size = lc.getPreferredSize();

            // ceil(ssToPx(0.0)) = 0, ceil(ssToPx(9.5)) = ceil(95.0) = 95
            assertThat(size.width)
                .as("width = ceil(ssToPx(lineWidthSs))")
                .isEqualTo((int) Math.ceil(pxPerSs * 0.0));

            assertThat(size.height)
                .as("height = ceil(ssToPx(totalLineHeightSs))")
                .isEqualTo((int) Math.ceil(pxPerSs * totalLineHeightSs));
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
}
