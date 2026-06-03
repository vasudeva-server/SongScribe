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

package songscribe.ui.adjustment;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Point;

import org.jspecify.annotations.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.component.ScoreView;

/**
 * Tests for {@link Adjustment} base-class mouse-event dispatch, enabled guard,
 * and X/Y clamping arithmetic in {@link Adjustment#mouseDragged}.
 *
 * <p>Because {@code Adjustment} is abstract, tests use the minimal
 * {@link TestableAdjustment} concrete subclass defined below, which exposes
 * protected state and records whether each abstract callback was invoked.
 */
class AdjustmentTest extends UnitTest {

    /** Minimal concrete subclass that exposes protected state for assertions. */
    static final class TestableAdjustment extends Adjustment {

        boolean startedDragCalled;
        boolean dragCalled;
        boolean finishedDragCalled;

        /**
         * Override {@link Adjustment#startedDrag()} so tests can control whether
         * the drag is accepted: if {@code acceptDrag} is false, reset
         * {@code startedDrag} to simulate a subclass cancelling the drag on
         * finding no eligible rect.
         */
        boolean acceptDrag = true;

        TestableAdjustment(ScoreView scoreView) {
            super(scoreView);
        }

        @Override
        protected void startedDrag() {
            startedDragCalled = true;

            if (!acceptDrag) {
                startedDrag = false;
            }
        }

        @Override
        protected void drag() {
            dragCalled = true;
        }

        @Override
        protected void finishedDrag() {
            finishedDragCalled = true;
        }

        @Override
        public void repaint(Graphics2D g2) {
            // not under test
        }

        /** Expose {@code endPoint} for clamping assertions. */
        Point getEndPoint() {
            return endPoint;
        }

        /** Expose {@code startPoint} for assertion. */
        @Nullable
        Point getStartPoint() {
            return startPoint;
        }

        /** Expose {@code startedDrag} flag for assertion. */
        boolean isStartedDrag() {
            return startedDrag;
        }

        /** Programmatically set drag bounds (simulates what {@code startedDrag()} normally does). */
        void setDragBounds(int leftX, int topY, int rightX, int bottomY) {
            topLeftDragBounds.setLocation(leftX, topY);
            bottomRightDragBounds.setLocation(rightX, bottomY);
        }
    }

    private ScoreView scoreView;
    private TestableAdjustment adjustment;

    @BeforeEach
    void setUp() {
        scoreView = mock(ScoreView.class);
        adjustment = new TestableAdjustment(scoreView);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MouseEvent pressEvent(int x, int y) {
        return new MouseEvent(
            mock(Component.class), MouseEvent.MOUSE_PRESSED, 0L, 0, x, y, x, y, 1, false,
            MouseEvent.BUTTON1
        );
    }

    private MouseEvent releaseEvent(int x, int y) {
        return new MouseEvent(
            mock(Component.class), MouseEvent.MOUSE_RELEASED, 0L, 0, x, y, x, y, 1, false,
            MouseEvent.BUTTON1
        );
    }

    private MouseEvent dragEvent(int x, int y) {
        return new MouseEvent(
            mock(Component.class), MouseEvent.MOUSE_DRAGGED, 0L, 0, x, y, x, y, 1, false,
            MouseEvent.BUTTON1
        );
    }

    // -------------------------------------------------------------------------
    // mousePressed — rows 1-2
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MousePressed {

        /** Row 1: disabled adjustment ignores mousePressed entirely. */
        @Test
        void testIgnoresEventWhenDisabled() {
            // enabled defaults to false
            adjustment.mousePressed(pressEvent(10, 20));

            assertThat(adjustment.isStartedDrag()).isFalse();
            assertThat(adjustment.startedDragCalled).isFalse();
            verify(scoreView, never()).setDragDisabled(true);
        }

        /** Row 2: enabled adjustment sets state, calls startedDrag(), and disables scoreView drag. */
        @Test
        void testStartsDragWhenEnabled() {
            adjustment.setEnabled(true);
            adjustment.mousePressed(pressEvent(42, 17));

            assertThat(adjustment.isStartedDrag())
                .as("startedDrag flag must be set by startedDrag() accepting the drag")
                .isTrue();
            assertThat(adjustment.getStartPoint())
                .as("startPoint must capture the press coordinates")
                .isEqualTo(new Point(42, 17));
            assertThat(adjustment.startedDragCalled)
                .as("startedDrag() callback must be invoked")
                .isTrue();
            verify(scoreView).setDragDisabled(true);
        }
    }

    // -------------------------------------------------------------------------
    // mouseReleased — rows 3-4
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseReleased {

        /** Row 3: disabled adjustment ignores mouseReleased entirely. */
        @Test
        void testIgnoresEventWhenDisabled() {
            // Simulate a drag in progress by setting the flag directly
            adjustment.startedDrag = true;
            // But adjustment is disabled — release must be a no-op
            adjustment.mouseReleased(releaseEvent(50, 60));

            assertThat(adjustment.finishedDragCalled).isFalse();
            verify(scoreView, never()).setDragDisabled(false);
        }

        /** Row 4: enabled adjustment clears startedDrag, calls finishedDrag(), re-enables drag. */
        @Test
        void testFinishesDragWhenEnabled() {
            adjustment.setEnabled(true);
            // Simulate an active drag
            adjustment.startedDrag = true;

            adjustment.mouseReleased(releaseEvent(50, 60));

            assertThat(adjustment.isStartedDrag())
                .as("startedDrag must be cleared after release")
                .isFalse();
            assertThat(adjustment.finishedDragCalled)
                .as("finishedDrag() must be called")
                .isTrue();
            verify(scoreView).setDragDisabled(false);
        }
    }

    // -------------------------------------------------------------------------
    // mouseDragged — rows 5-7
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseDragged {

        private static final int LEFT_BOUND = 100;
        private static final int TOP_BOUND = 50;
        private static final int RIGHT_BOUND = 300;
        private static final int BOTTOM_BOUND = 200;

        @BeforeEach
        void startDrag() {
            adjustment.setEnabled(true);
            adjustment.setDragBounds(LEFT_BOUND, TOP_BOUND, RIGHT_BOUND, BOTTOM_BOUND);
            adjustment.startedDrag = true;
        }

        /** Row 5: disabled adjustment ignores mouseDragged entirely. */
        @Test
        void testIgnoresEventWhenDisabled() {
            adjustment.setEnabled(false);
            adjustment.mouseDragged(dragEvent(150, 100));

            assertThat(adjustment.dragCalled).isFalse();
        }

        // Row 6: X clamping

        /** Row 6a: X inside bounds passes through unchanged. */
        @Test
        void testXInsideBoundsPassesThrough() {
            int insideX = LEFT_BOUND + 10;
            adjustment.mouseDragged(dragEvent(insideX, TOP_BOUND + 10));

            assertThat(adjustment.getEndPoint().x)
                .as("X inside bounds must not be clamped")
                .isEqualTo(insideX);
        }

        /** Row 6b: X at left bound passes through (inclusive lower boundary). */
        @Test
        void testXAtLeftBoundIsNotClamped() {
            adjustment.mouseDragged(dragEvent(LEFT_BOUND, TOP_BOUND + 10));

            assertThat(adjustment.getEndPoint().x)
                .as("X exactly at left bound is within range")
                .isEqualTo(LEFT_BOUND);
        }

        /** Row 6c: X below left bound is clamped to left bound. */
        @Test
        void testXBelowLeftBoundClampsToLeftBound() {
            adjustment.mouseDragged(dragEvent(LEFT_BOUND - 1, TOP_BOUND + 10));

            assertThat(adjustment.getEndPoint().x)
                .as("X below left bound must be clamped to topLeftDragBounds.x")
                .isEqualTo(LEFT_BOUND);
        }

        /** Row 6d: X at right bound is clamped to rightBound - 1 (exclusive upper boundary). */
        @Test
        void testXAtRightBoundClampsToRightBoundMinusOne() {
            adjustment.mouseDragged(dragEvent(RIGHT_BOUND, TOP_BOUND + 10));

            assertThat(adjustment.getEndPoint().x)
                .as("X at right bound must be clamped to bottomRightDragBounds.x - 1")
                .isEqualTo(RIGHT_BOUND - 1);
        }

        /** Row 6e: X beyond right bound is clamped to rightBound - 1. */
        @Test
        void testXBeyondRightBoundClampsToRightBoundMinusOne() {
            adjustment.mouseDragged(dragEvent(RIGHT_BOUND + 5, TOP_BOUND + 10));

            assertThat(adjustment.getEndPoint().x)
                .as("X beyond right bound must be clamped to bottomRightDragBounds.x - 1")
                .isEqualTo(RIGHT_BOUND - 1);
        }

        // Row 7: Y clamping

        /** Row 7a: Y inside bounds passes through unchanged. */
        @Test
        void testYInsideBoundsPassesThrough() {
            int insideY = TOP_BOUND + 10;
            adjustment.mouseDragged(dragEvent(LEFT_BOUND + 10, insideY));

            assertThat(adjustment.getEndPoint().y)
                .as("Y inside bounds must not be clamped")
                .isEqualTo(insideY);
        }

        /** Row 7b: Y at top bound passes through (inclusive lower boundary). */
        @Test
        void testYAtTopBoundIsNotClamped() {
            adjustment.mouseDragged(dragEvent(LEFT_BOUND + 10, TOP_BOUND));

            assertThat(adjustment.getEndPoint().y)
                .as("Y exactly at top bound is within range")
                .isEqualTo(TOP_BOUND);
        }

        /** Row 7c: Y above top bound is clamped to top bound. */
        @Test
        void testYAboveTopBoundClampsToTopBound() {
            adjustment.mouseDragged(dragEvent(LEFT_BOUND + 10, TOP_BOUND - 1));

            assertThat(adjustment.getEndPoint().y)
                .as("Y above top bound must be clamped to topLeftDragBounds.y")
                .isEqualTo(TOP_BOUND);
        }

        /** Row 7d: Y at bottom bound is clamped to bottomBound - 1 (exclusive upper boundary). */
        @Test
        void testYAtBottomBoundClampsToBottomBoundMinusOne() {
            adjustment.mouseDragged(dragEvent(LEFT_BOUND + 10, BOTTOM_BOUND));

            assertThat(adjustment.getEndPoint().y)
                .as("Y at bottom bound must be clamped to bottomRightDragBounds.y - 1")
                .isEqualTo(BOTTOM_BOUND - 1);
        }

        /** Row 7e: Y beyond bottom bound is clamped to bottomBound - 1. */
        @Test
        void testYBeyondBottomBoundClampsToBottomBoundMinusOne() {
            adjustment.mouseDragged(dragEvent(LEFT_BOUND + 10, BOTTOM_BOUND + 5));

            assertThat(adjustment.getEndPoint().y)
                .as("Y beyond bottom bound must be clamped to bottomRightDragBounds.y - 1")
                .isEqualTo(BOTTOM_BOUND - 1);
        }

        /** Row 5 extension: drag() is not called when startedDrag=false, even when enabled. */
        @Test
        void testSkipsDragWhenStartedDragIsFalse() {
            adjustment.startedDrag = false;
            adjustment.mouseDragged(dragEvent(150, 100));

            assertThat(adjustment.dragCalled)
                .as("drag() must not be called when startedDrag=false")
                .isFalse();
        }
    }
}
