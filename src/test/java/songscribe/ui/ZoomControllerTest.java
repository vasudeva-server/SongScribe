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

package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.engio.mbassy.listener.Handler;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link ZoomController}: the pure level-stepping functions, the
 * no-active-view no-op contract, and (with a stubbed {@link ScoreView}) the
 * zoom-apply / notification-posting behavior.
 */
class ZoomControllerTest extends UnitTest {

    /** Records every ZoomDidChangeNotification posted while subscribed. */
    private static final class RecordingListener {
        final List<ZoomDidChangeNotification> received = new ArrayList<>();

        @Handler
        public void onZoomDidChange(ZoomDidChangeNotification message) {
            received.add(message);
        }
    }

    /** A full wheel notch toward the user zooms in (negative precise rotation). */
    private static final double ONE_NOTCH_ZOOM_IN = -1.0;

    /** A full wheel notch away from the user zooms out (positive precise rotation). */
    private static final double ONE_NOTCH_ZOOM_OUT = 1.0;

    /** Half a percent — the magnitude at which {@link Math#round} flips to the next integer. */
    private static final double ROUNDING_THRESHOLD_PERCENT = 0.5;

    /** A magnification delta large enough to visibly zoom in (positive). */
    private static final double MAGNIFICATION_ZOOM_IN = 0.1;

    /** A magnification delta large enough to visibly zoom out (negative). */
    private static final double MAGNIFICATION_ZOOM_OUT = -0.1;

    /** Exact percent {@link #MAGNIFICATION_ZOOM_IN} yields from the default: {@code round(100 × 1.1)}. */
    private static final int MAGNIFIED_IN_PERCENT = 110;

    /** Exact percent {@link #MAGNIFICATION_ZOOM_OUT} yields from the default: {@code round(100 × 0.9)}. */
    private static final int MAGNIFIED_OUT_PERCENT = 90;

    /** An arbitrary cursor anchor in ScoreView-local coordinates. */
    private static final Point ANCHOR = new Point(120, 340);

    private RecordingListener listener;
    private MockedStatic<MainFrame> mainFrameMock;
    private MainFrame mockFrame;

    @BeforeEach
    void setUp() {
        listener = new RecordingListener();
        MessageCenter.subscribe(listener);

        mainFrameMock = mockStatic(MainFrame.class);
        mockFrame = mock(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
    }

    @AfterEach
    void tearDown() {
        MessageCenter.unsubscribe(listener);
        mainFrameMock.close();
    }

    /**
     * Stubs the active {@link ScoreView} with a real {@link ViewScale} and wires
     * {@code applyZoomPercent} to mutate it, mirroring the real
     * {@code ScoreView.applyZoomPercent} contract without needing a live Swing tree.
     */
    private ScoreView stubActiveScoreView() {
        var scoreView = mock(ScoreView.class);
        var viewScale = new ViewScale();
        when(scoreView.getViewScale()).thenReturn(viewScale);
        doAnswer(invocation -> {
            viewScale.setZoomPercent(invocation.getArgument(0));
            return null;
        }).when(scoreView).applyZoomPercent(anyInt(), any());
        when(mockFrame.getScoreView()).thenReturn(scoreView);

        return scoreView;
    }

    // -------------------------------------------------------------------------
    // nextLevelAbove / nextLevelBelow — pure stepping functions
    // -------------------------------------------------------------------------

    @Nested
    class NextLevelAbove {

        @Test
        void testStepsToNextTableEntry() {
            assertThat(ZoomController.nextLevelAbove(100)).isEqualTo(150);
        }

        @Test
        void testLandingBetweenLevelsStepsToNextHigherLevel() {
            assertThat(ZoomController.nextLevelAbove(120)).isEqualTo(150);
        }

        @Test
        void testJumpsFrom400To800() {
            assertThat(ZoomController.nextLevelAbove(400)).isEqualTo(800);
        }

        @Test
        void testAtMaxReturnsUnchanged() {
            assertThat(ZoomController.nextLevelAbove(ZoomController.MAX_ZOOM_PERCENT))
                .isEqualTo(ZoomController.MAX_ZOOM_PERCENT);
        }

        @Test
        void testAboveMaxReturnsUnchanged() {
            assertThat(ZoomController.nextLevelAbove(ZoomController.MAX_ZOOM_PERCENT + 1))
                .isEqualTo(ZoomController.MAX_ZOOM_PERCENT + 1);
        }
    }

    @Nested
    class NextLevelBelow {

        @Test
        void testStepsToPreviousTableEntry() {
            assertThat(ZoomController.nextLevelBelow(150)).isEqualTo(100);
        }

        @Test
        void testLandingBetweenLevelsStepsToNextLowerLevel() {
            assertThat(ZoomController.nextLevelBelow(120)).isEqualTo(100);
        }

        @Test
        void testDropsFrom800To400() {
            assertThat(ZoomController.nextLevelBelow(800)).isEqualTo(400);
        }

        @Test
        void testAtMinReturnsUnchanged() {
            assertThat(ZoomController.nextLevelBelow(ZoomController.MIN_ZOOM_PERCENT))
                .isEqualTo(ZoomController.MIN_ZOOM_PERCENT);
        }

        @Test
        void testBelowMinReturnsUnchanged() {
            assertThat(ZoomController.nextLevelBelow(ZoomController.MIN_ZOOM_PERCENT - 1))
                .isEqualTo(ZoomController.MIN_ZOOM_PERCENT - 1);
        }
    }

    // -------------------------------------------------------------------------
    // No active ScoreView — every mutator no-ops silently
    // -------------------------------------------------------------------------

    @Nested
    class NoActiveScoreView {

        @BeforeEach
        void noScoreView() {
            when(mockFrame.getScoreView()).thenReturn(null);
        }

        @Test
        void testGetZoomPercentFallsBackToDefault() {
            assertThat(ZoomController.getZoomPercent()).isEqualTo(ZoomController.DEFAULT_ZOOM_PERCENT);
        }

        @Test
        void testZoomInDoesNotThrowAndPostsNoNotification() {
            assertThatCode(ZoomController::zoomIn).doesNotThrowAnyException();
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testZoomOutDoesNotThrowAndPostsNoNotification() {
            assertThatCode(ZoomController::zoomOut).doesNotThrowAnyException();
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testResetZoomDoesNotThrowAndPostsNoNotification() {
            assertThatCode(ZoomController::resetZoom).doesNotThrowAnyException();
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testSetZoomPercentDoesNotThrowAndPostsNoNotification() {
            assertThatCode(() -> ZoomController.setZoomPercent(200)).doesNotThrowAnyException();
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testZoomByWheelDoesNotThrowAndPostsNoNotification() {
            assertThatCode(() -> ZoomController.zoomByWheel(ONE_NOTCH_ZOOM_IN, ANCHOR))
                .doesNotThrowAnyException();
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testZoomByMagnificationDoesNotThrowAndPostsNoNotification() {
            assertThatCode(() -> ZoomController.zoomByMagnification(MAGNIFICATION_ZOOM_IN, ANCHOR))
                .doesNotThrowAnyException();
            assertThat(listener.received).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Active ScoreView — zoomIn / zoomOut stepping
    // -------------------------------------------------------------------------

    @Nested
    class ZoomInStepping {

        @Test
        void testZoomInStepsToNextLevel() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomIn();

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(150);
        }

        @Test
        void testZoomInAtMaxSilentlyNoOps() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(ZoomController.MAX_ZOOM_PERCENT);

            ZoomController.zoomIn();

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MAX_ZOOM_PERCENT);
            assertThat(listener.received).isEmpty();
        }
    }

    @Nested
    class ZoomOutStepping {

        @Test
        void testZoomOutStepsToPreviousLevel() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(150);

            ZoomController.zoomOut();

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(100);
        }

        @Test
        void testZoomOutAtMinSilentlyNoOps() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(ZoomController.MIN_ZOOM_PERCENT);

            ZoomController.zoomOut();

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MIN_ZOOM_PERCENT);
            assertThat(listener.received).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // resetZoom
    // -------------------------------------------------------------------------

    @Test
    void testResetZoomSetsDefaultPercent() {
        var scoreView = stubActiveScoreView();
        scoreView.getViewScale().setZoomPercent(250);

        ZoomController.resetZoom();

        assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.DEFAULT_ZOOM_PERCENT);
    }

    @Test
    void testResetZoomAtDefaultIsNoOp() {
        var scoreView = stubActiveScoreView();
        scoreView.getViewScale().setZoomPercent(ZoomController.DEFAULT_ZOOM_PERCENT);

        ZoomController.resetZoom();

        assertThat(listener.received).isEmpty();
        verify(scoreView, never()).applyZoomPercent(anyInt(), any());
    }

    // -------------------------------------------------------------------------
    // setZoomPercent — clamping
    // -------------------------------------------------------------------------

    @Nested
    class SetZoomPercentClamping {

        @Test
        void testSetZoomPercentBelowMinClampsToMin() {
            var scoreView = stubActiveScoreView();

            ZoomController.setZoomPercent(ZoomController.MIN_ZOOM_PERCENT - 1);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MIN_ZOOM_PERCENT);
        }

        @Test
        void testSetZoomPercentAboveMaxClampsToMax() {
            var scoreView = stubActiveScoreView();

            ZoomController.setZoomPercent(ZoomController.MAX_ZOOM_PERCENT + 1);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MAX_ZOOM_PERCENT);
        }

        @Test
        void testSetZoomPercentWithinRangeSetsExactValue() {
            var scoreView = stubActiveScoreView();

            ZoomController.setZoomPercent(200);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(200);
        }
    }

    // -------------------------------------------------------------------------
    // Notification payload and no-op-when-unchanged behavior
    // -------------------------------------------------------------------------

    @Test
    void testZoomChangePostsNotificationWithOldAndNewPercent() {
        stubActiveScoreView();

        ZoomController.zoomIn();

        assertThat(listener.received).hasSize(1);
        var message = listener.received.get(0);
        assertThat(message.getOldZoomPercent()).isEqualTo(100);
        assertThat(message.getNewZoomPercent()).isEqualTo(150);
    }

    @Test
    void testSetZoomPercentToSameEffectivePercentDoesNotPostNotification() {
        var scoreView = stubActiveScoreView();
        scoreView.getViewScale().setZoomPercent(200);

        ZoomController.setZoomPercent(200);

        assertThat(listener.received).isEmpty();
        verify(scoreView, never()).applyZoomPercent(anyInt(), any());
    }

    @Test
    void testSetZoomPercentClampingToCurrentValueDoesNotPostNotification() {
        // Requesting a percent above MAX that clamps to the value already in effect
        // must still be treated as unchanged.
        var scoreView = stubActiveScoreView();
        scoreView.getViewScale().setZoomPercent(ZoomController.MAX_ZOOM_PERCENT);

        ZoomController.setZoomPercent(ZoomController.MAX_ZOOM_PERCENT + 1);

        assertThat(listener.received).isEmpty();
        verify(scoreView, never()).applyZoomPercent(anyInt(), any());
    }

    // -------------------------------------------------------------------------
    // zoomByWheel — multiplicative, cursor-anchored gesture zoom
    // -------------------------------------------------------------------------

    @Nested
    class WheelZoom {

        @Test
        void testNegativeRotationZoomsInWithoutSnappingToNextLevel() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByWheel(ONE_NOTCH_ZOOM_IN, ANCHOR);

            // Continuous multiplicative step: strictly past the current percent but
            // short of the next discrete level a keyboard zoom-in would jump to.
            assertThat(scoreView.getViewScale().getZoomPercent())
                .isGreaterThan(ZoomController.DEFAULT_ZOOM_PERCENT)
                .isLessThan(ZoomController.nextLevelAbove(ZoomController.DEFAULT_ZOOM_PERCENT));
        }

        @Test
        void testPositiveRotationZoomsOutWithoutSnappingToPreviousLevel() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByWheel(ONE_NOTCH_ZOOM_OUT, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent())
                .isLessThan(ZoomController.DEFAULT_ZOOM_PERCENT)
                .isGreaterThan(ZoomController.nextLevelBelow(ZoomController.DEFAULT_ZOOM_PERCENT));
        }

        @Test
        void testZoomInAtMaxClampsAndPostsNoNotification() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(ZoomController.MAX_ZOOM_PERCENT);

            ZoomController.zoomByWheel(ONE_NOTCH_ZOOM_IN, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MAX_ZOOM_PERCENT);
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testZoomOutAtMinClampsAndPostsNoNotification() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(ZoomController.MIN_ZOOM_PERCENT);

            ZoomController.zoomByWheel(ONE_NOTCH_ZOOM_OUT, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MIN_ZOOM_PERCENT);
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testSubStepZoomOutStillStepsDownByOne() {
            var scoreView = stubActiveScoreView();

            // A rotation whose multiplicative change stays under the rounding
            // threshold at the default percent would, by rounding alone, leave the
            // percent unchanged — the nudge must still advance it one step.
            ZoomController.zoomByWheel(subStepRotation(ZoomController.DEFAULT_ZOOM_PERCENT), ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent())
                .isEqualTo(ZoomController.DEFAULT_ZOOM_PERCENT - 1);
        }

        @Test
        void testSubStepZoomInStillStepsUpByOne() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByWheel(-subStepRotation(ZoomController.DEFAULT_ZOOM_PERCENT), ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent())
                .isEqualTo(ZoomController.DEFAULT_ZOOM_PERCENT + 1);
        }

        /**
         * A positive (zoom-out) rotation small enough that its multiplicative percent
         * change at {@code atPercent} rounds back to {@code atPercent}. Derived from
         * {@link ZoomController#WHEEL_ZOOM_FACTOR_PER_NOTCH} so it stays a genuine
         * sub-step regardless of the factor's value; half the threshold keeps it well
         * inside the dead zone.
         */
        private double subStepRotation(int atPercent) {
            return ROUNDING_THRESHOLD_PERCENT / (atPercent * ZoomController.WHEEL_ZOOM_FACTOR_PER_NOTCH) / 2;
        }
    }

    // -------------------------------------------------------------------------
    // zoomByMagnification — continuous, cursor-anchored trackpad-pinch zoom
    // -------------------------------------------------------------------------

    @Nested
    class MagnificationZoom {

        @Test
        void testPositiveMagnificationZoomsIn() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByMagnification(MAGNIFICATION_ZOOM_IN, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(MAGNIFIED_IN_PERCENT);
        }

        @Test
        void testNegativeMagnificationZoomsOut() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByMagnification(MAGNIFICATION_ZOOM_OUT, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(MAGNIFIED_OUT_PERCENT);
        }

        @Test
        void testZoomInAtMaxClampsAndPostsNoNotification() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(ZoomController.MAX_ZOOM_PERCENT);

            ZoomController.zoomByMagnification(MAGNIFICATION_ZOOM_IN, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MAX_ZOOM_PERCENT);
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testZoomOutAtMinClampsAndPostsNoNotification() {
            var scoreView = stubActiveScoreView();
            scoreView.getViewScale().setZoomPercent(ZoomController.MIN_ZOOM_PERCENT);

            ZoomController.zoomByMagnification(MAGNIFICATION_ZOOM_OUT, ANCHOR);

            assertThat(scoreView.getViewScale().getZoomPercent()).isEqualTo(ZoomController.MIN_ZOOM_PERCENT);
            assertThat(listener.received).isEmpty();
        }

        @Test
        void testSubThresholdMagnificationIsNoOp() {
            var scoreView = stubActiveScoreView();

            // Unlike zoomByWheel, there is no dead-gesture nudge: a magnification too
            // small to move the rounded percent must leave applyZoomPercent uncalled.
            ZoomController.zoomByMagnification(subStepMagnification(ZoomController.DEFAULT_ZOOM_PERCENT), ANCHOR);

            verify(scoreView, never()).applyZoomPercent(anyInt(), any());
            assertThat(listener.received).isEmpty();
        }

        /**
         * A magnification small enough that its proportional percent change at
         * {@code atPercent} rounds back to {@code atPercent}; half the rounding
         * threshold keeps it well inside the dead zone.
         */
        private double subStepMagnification(int atPercent) {
            return ROUNDING_THRESHOLD_PERCENT / atPercent / 2;
        }
    }

    // -------------------------------------------------------------------------
    // Anchor-point forwarding to ScoreView.applyZoomPercent
    // -------------------------------------------------------------------------

    @Nested
    class AnchorForwarding {

        @Test
        void testZoomByWheelForwardsAnchorPoint() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByWheel(ONE_NOTCH_ZOOM_IN, ANCHOR);

            assertThat(capturedAnchor(scoreView)).isEqualTo(ANCHOR);
        }

        @Test
        void testZoomByMagnificationForwardsAnchorPoint() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomByMagnification(MAGNIFICATION_ZOOM_IN, ANCHOR);

            assertThat(capturedAnchor(scoreView)).isEqualTo(ANCHOR);
        }

        @Test
        void testSetZoomPercentWithAnchorForwardsAnchorPoint() {
            var scoreView = stubActiveScoreView();

            ZoomController.setZoomPercent(200, ANCHOR);

            assertThat(capturedAnchor(scoreView)).isEqualTo(ANCHOR);
        }

        @Test
        void testKeyboardZoomForwardsNullAnchor() {
            var scoreView = stubActiveScoreView();

            ZoomController.zoomIn();

            assertThat(capturedAnchor(scoreView)).isNull();
        }

        private Point capturedAnchor(ScoreView scoreView) {
            var captor = ArgumentCaptor.forClass(Point.class);
            verify(scoreView).applyZoomPercent(anyInt(), captor.capture());

            return captor.getValue();
        }
    }
}
