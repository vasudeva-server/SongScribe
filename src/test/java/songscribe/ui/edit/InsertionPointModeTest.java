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

package songscribe.ui.edit;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InsertionPointMode}, the operation-independent "pick a spot on a line"
 * interaction.
 *
 * <p>The contract this class is responsible for is the one stated on {@code InsertionPointMode}
 * itself, exercised through a recording {@link RecordingClient} standing in for a real
 * operation:
 *
 * <ul>
 *   <li><b>Exactly one end-of-placement report.</b> {@code insertionPointModeDidEnd} arrives
 *       once — PLACED on a completed placement, CANCELLED on an abandoned one — never both,
 *       never twice, never neither. Asserted by count, on every terminal path.
 *   <li><b>At most one pending placement.</b> A second client asking while one is running is
 *       refused and hears nothing.
 *   <li><b>The client's predicate decides which indices exist.</b> A rejected index is not
 *       tracked, hides the marker, and can never reach {@code insertionPointChosen}.
 *   <li><b>The mode's own geometry.</b> The staff header and everything past the staff's right
 *       edge hold no insertion point, whoever is placing.
 *   <li><b>Marker transitions.</b> Hidden while idle, shown once a point is tracked,
 *       repositioned (never resized) as the index moves, hidden again on clear and on exit.
 *   <li><b>The activity signal.</b> {@code isActive()} and the notification that drives the
 *       blanket action disable.
 * </ul>
 *
 * <p>What the paste-specific half of the old paste-mode tests promised — the banner's lifecycle
 * and the mapping from a fragment-insert outcome to COMPLETED/DECLINED — lives in
 * {@code PasteModeManagerTest}, which is one client of this mode rather than the mode itself.
 */
class InsertionPointModeTest extends UnitTest {

    // A line with no key-signature accidentals and an arbitrary width wide enough to hold
    // several insertion points, shared by every target-tracking fixture below.
    private static final Key HEADER_KEY = new Key(KeyType.NONE, 0);
    private static final double LINE_WIDTH_SS = 200.0;

    // Added past the content span's edges to build a mouse x unambiguously outside it.
    private static final double OUTSIDE_MARGIN_SS = 20.0;

    private static final int FIRST_INSERTION_INDEX = 2;
    private static final int SECOND_INSERTION_INDEX = 5;

    /** The value {@code getTargetIndex()} answers with when nothing is tracked. */
    private static final int NO_INDEX = -1;

    private ScoreView scoreView;
    private InsertionPointMode mode;
    private RecordingClient client;

    /**
     * A real, unrealized overlay host component for the insertion marker: enough for
     * {@code SwingUtilities.isDescendingFrom}/{@code convertPoint} (both just walk the parent
     * chain) without a visible window. Stubbed as {@code scoreView}'s host unconditionally —
     * every {@code updateTarget}/{@code clearTarget} call recomputes the marker's bounds via
     * {@code LineOverlayComponent.updateHostCursor}, which dereferences the host component
     * directly, so an unstubbed (null) host NPEs even in tests that never look at the marker.
     */
    private JPanel overlayHost;

    @BeforeEach
    void setUp() {
        scoreView = mock(ScoreView.class);
        overlayHost = new JPanel();
        when(scoreView.getHostComponent()).thenReturn(overlayHost);
        mode = new InsertionPointMode(scoreView);
        client = new RecordingClient();
    }

    @AfterEach
    void tearDown() {
        // The constructor sets the static instance as a side effect; reset it so a later test
        // class's isActive() calls don't see this test's torn-down mode.
        InsertionPointMode.setInstance(null);
    }

    // -------------------------------------------------------------------------
    // enter()
    // -------------------------------------------------------------------------

    @Nested
    class Enter {

        @Test
        void testEnterActivatesTheModeForItsClient() {
            assertThat(mode.enter(client)).isTrue();

            assertThat(mode.isInProgress()).isTrue();
            assertThat(InsertionPointMode.isActive()).isTrue();
        }

        @Test
        void testEnterPostsNotificationWithActiveTrue() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                mode.enter(client);

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue()).isInstanceOf(InsertionPointModeDidChangeNotification.class);
                assertThat(((InsertionPointModeDidChangeNotification) captor.getValue()).isActive()).isTrue();
            }
        }

        @Test
        void testSecondClientIsRefusedWhileAPlacementIsPending() {
            var otherClient = new RecordingClient();

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                mode.enter(client);

                assertThat(mode.enter(otherClient))
                    .as("a placement is already pending, so the second client must be refused")
                    .isFalse();
                messageCenterMock.verify(() -> MessageCenter.post(any()), times(1));
            }

            mode.cancel();

            assertThat(otherClient.endReasons)
                .as("a refused client never entered, so it hears nothing")
                .isEmpty();
            assertThat(client.endReasons).containsExactly(InsertionPointMode.EndReason.CANCELLED);
        }
    }

    // -------------------------------------------------------------------------
    // cancel() when no placement is pending
    // -------------------------------------------------------------------------

    @Nested
    class CancelWhenNotActive {

        @Test
        void testCancelWithNoPendingPlacementIsSafeNoOp() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                mode.cancel();

                assertThat(mode.isInProgress()).isFalse();
                messageCenterMock.verify(() -> MessageCenter.post(any()), never());
            }

            assertThat(client.endReasons).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // exit() (reached via cancel()) tears down enter()'s state
    // -------------------------------------------------------------------------

    @Nested
    class ExitTeardown {

        @Test
        void testCancelClearsTrackedTargetState() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);

            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(mode.getTargetLineComponent()).isNotNull();

            mode.cancel();

            assertThat(mode.getTargetLineComponent()).isNull();
            assertThat(mode.getTargetIndex()).isEqualTo(NO_INDEX);
        }

        @Test
        void testCancelPostsNotificationWithActiveFalse() {
            mode.enter(client);

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                mode.cancel();

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue()).isInstanceOf(InsertionPointModeDidChangeNotification.class);
                assertThat(((InsertionPointModeDidChangeNotification) captor.getValue()).isActive()).isFalse();
            }
        }

        @Test
        void testModeIsAlreadyIdleWhenTheClientHearsItEnded() {
            mode.enter(client);

            mode.cancel();

            assertThat(client.wasInProgressAtEnd)
                .as("the client must be told after the mode has gone inactive, not before")
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // The exactly-once end-of-placement report
    // -------------------------------------------------------------------------

    @Nested
    class EndOfPlacementIsReportedExactlyOnce {

        @Test
        void testCompletedPlacementReportsPlacedExactlyOnce() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            mode.place();

            assertThat(client.endReasons)
                .as("a completed placement ends the mode once, as PLACED, and never as CANCELLED")
                .containsExactly(InsertionPointMode.EndReason.PLACED);
        }

        @Test
        void testCancellationReportsCancelledExactlyOnce() {
            mode.enter(client);

            mode.cancel();

            assertThat(client.endReasons)
                .as("an abandoned placement ends the mode once, as CANCELLED, and never as PLACED")
                .containsExactly(InsertionPointMode.EndReason.CANCELLED);
        }

        @Test
        void testASecondCancelAfterPlacementReportsNothingFurther() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            mode.place();

            mode.cancel();

            assertThat(client.endReasons)
                .as("the mode has already ended, so a later cancel adds no second report")
                .containsExactly(InsertionPointMode.EndReason.PLACED);
        }

        @Test
        void testDeclinedPlacementKeepsTheModeLiveAndReportsNothingYet() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            client.placement = InsertionPointMode.Placement.DECLINED;
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            mode.place();
            mode.place();

            assertThat(mode.isInProgress())
                .as("a declined point leaves the placement pending for another try")
                .isTrue();
            assertThat(client.chosenIndices)
                .as("each try reaches the client")
                .containsExactly(FIRST_INSERTION_INDEX, FIRST_INSERTION_INDEX);
            assertThat(client.endReasons)
                .as("no try completed, so the mode has not ended")
                .isEmpty();
        }

        @Test
        void testDeclinedThenCancelledReportsCancelledExactlyOnce() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            client.placement = InsertionPointMode.Placement.DECLINED;
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            mode.place();

            mode.cancel();

            assertThat(client.endReasons).containsExactly(InsertionPointMode.EndReason.CANCELLED);
        }
    }

    // -------------------------------------------------------------------------
    // updateTarget (via mouseMoved) — insertion-point tracking
    // -------------------------------------------------------------------------

    @Nested
    class TargetTracking {

        private LineComponent lineComponent;

        @BeforeEach
        void setUp() {
            lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            mode.enter(client);
        }

        @Test
        void testMouseMovedReturnsFalseWhenNoPlacementIsPending() {
            mode.cancel();

            var consumed = mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(consumed).isFalse();
            assertThat(mode.getTargetLineComponent()).isNull();
        }

        @Test
        void testMouseInsideContentSpanTracksInsertionIndex() {
            var consumed = mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(consumed).isTrue();
            assertThat(mode.getTargetLineComponent()).isEqualTo(lineComponent);
            assertThat(mode.getTargetIndex()).isEqualTo(FIRST_INSERTION_INDEX);
        }

        @Test
        void testMouseLeftOfHeaderClearsTarget() {
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(mode.getTargetLineComponent()).isNotNull();

            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, leftOfHeaderXPx()));

            assertThat(mode.getTargetLineComponent()).isNull();
            assertThat(mode.getTargetIndex()).isEqualTo(NO_INDEX);
        }

        @Test
        void testMouseRightOfStaffClearsTarget() {
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(mode.getTargetLineComponent()).isNotNull();

            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, rightOfStaffXPx()));

            assertThat(mode.getTargetLineComponent()).isNull();
            assertThat(mode.getTargetIndex()).isEqualTo(NO_INDEX);
        }

        @Test
        void testTargetTracksAcrossLineComponentChange() {
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(mode.getTargetLineComponent()).isEqualTo(lineComponent);

            var otherLineComponent = trackedLineComponent(SECOND_INSERTION_INDEX);

            mode.mouseMoved(otherLineComponent, mouseMovedEvent(otherLineComponent, insideContentXPx()));

            assertThat(mode.getTargetLineComponent()).isEqualTo(otherLineComponent);
            assertThat(mode.getTargetIndex()).isEqualTo(SECOND_INSERTION_INDEX);
        }

        @Test
        void testMouseExitedOnTheTrackedLineClearsTarget() {
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            mode.mouseExited(lineComponent);

            assertThat(mode.getTargetLineComponent()).isNull();
            assertThat(mode.getTargetIndex()).isEqualTo(NO_INDEX);
        }

        @Test
        void testMouseExitedOnAnotherLineLeavesTheTrackedPointAlone() {
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            mode.mouseExited(trackedLineComponent(SECOND_INSERTION_INDEX));

            assertThat(mode.getTargetLineComponent())
                .as("crossing into another line must not undo that line's own mouseMoved")
                .isEqualTo(lineComponent);
            assertThat(mode.getTargetIndex()).isEqualTo(FIRST_INSERTION_INDEX);
        }
    }

    // -------------------------------------------------------------------------
    // The client's index predicate
    // -------------------------------------------------------------------------

    @Nested
    class IndexPredicate {

        @Test
        void testAnIndexTheClientRejectsIsNotTracked() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            client.rejectedIndex = FIRST_INSERTION_INDEX;
            mode.enter(client);

            var consumed = mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(consumed)
                .as("the event is still the mode's, whether or not the index was acceptable")
                .isTrue();
            assertThat(mode.getTargetLineComponent()).isNull();
            assertThat(mode.getTargetIndex()).isEqualTo(NO_INDEX);
        }

        @Test
        void testARejectedIndexCanNeverBeChosen() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);
            client.rejectedIndex = FIRST_INSERTION_INDEX;
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            mode.mouseClicked(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            mode.place();

            assertThat(client.chosenIndices).isEmpty();
            assertThat(client.endReasons)
                .as("clicking a rejected position places nothing and abandons nothing")
                .isEmpty();
        }

        @Test
        void testMovingOffARejectedIndexOntoAnAcceptedOneTracksAgain() {
            var rejecting = trackedLineComponent(FIRST_INSERTION_INDEX);
            var accepting = trackedLineComponent(SECOND_INSERTION_INDEX);
            client.rejectedIndex = FIRST_INSERTION_INDEX;
            mode.enter(client);
            mode.mouseMoved(rejecting, mouseMovedEvent(rejecting, insideContentXPx()));

            mode.mouseMoved(accepting, mouseMovedEvent(accepting, insideContentXPx()));

            assertThat(mode.getTargetLineComponent()).isEqualTo(accepting);
            assertThat(mode.getTargetIndex()).isEqualTo(SECOND_INSERTION_INDEX);
        }
    }

    // -------------------------------------------------------------------------
    // InsertionMarkerOverlay — driven by updateTarget (via mouseMoved), clearTarget, and exit
    // -------------------------------------------------------------------------

    @Nested
    class InsertionMarkerOverlayTransitions {

        private static final double FIRST_INSERTION_X_SS = 5.0;
        private static final double SECOND_INSERTION_X_SS = 15.0;

        private Line line;
        private LayoutResult layoutResult;
        private LineComponent lineComponent;

        @BeforeEach
        void setUp() {
            line = lineStub();
            layoutResult = mock(LayoutResult.class);
            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(FIRST_INSERTION_INDEX);
            when(layoutResult.calculateInsertionXSs(anyInt(), anyDouble(), any(), eq(line), eq(true)))
                .thenReturn(FIRST_INSERTION_X_SS, SECOND_INSERTION_X_SS);

            lineComponent = lineComponentFor(line, layoutResult);
            // A real descendant of the stubbed overlay host, so LineOverlayComponent's
            // isDescendingFrom/convertPoint bounds computation actually resolves to real bounds
            // instead of the always-hidden fallback.
            when(lineComponent.getParent()).thenReturn(overlayHost);
        }

        /**
         * The marker is constructed and added to the host in {@code InsertionPointMode}'s
         * constructor rather than lazily on activation, and a fresh Swing {@code JComponent}
         * defaults to visible — so "hidden while no placement is pending" is a real claim about
         * {@code mouseMoved}'s inactive early-return, not something Swing gives for free. Every
         * other test in this class asserts visibility only after {@code enter}.
         */
        @Test
        void testMouseMovedLeavesMarkerHiddenWhileNoPlacementIsPending() {
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(mode.getInsertionMarkerOverlay().isVisible())
                .as("the insertion marker must stay hidden while no placement is pending")
                .isFalse();
        }

        @Test
        void testUpdateTargetShowsMarkerWithRealBounds() {
            mode.enter(client);

            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            var overlay = mode.getInsertionMarkerOverlay();

            assertThat(overlay.isVisible())
                .as("updateTarget must show the marker once a target is tracked")
                .isTrue();
            assertThat(overlay.getBounds().width)
                .as("the marker has a real, non-empty width once shown")
                .isPositive();
            assertThat(overlay.getBounds().height)
                .as("the marker has a real, non-empty height once shown")
                .isPositive();
        }

        @Test
        void testUpdateTargetIndexChangeRepositionsWithoutResizing() {
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var overlay = mode.getInsertionMarkerOverlay();
            var boundsBefore = overlay.getBounds();

            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(SECOND_INSERTION_INDEX);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var boundsAfter = overlay.getBounds();

            assertThat(boundsAfter.x)
                .as("a different insertion index moves the marker horizontally")
                .isNotEqualTo(boundsBefore.x);
            assertThat(boundsAfter.width)
                .as("the marker's height is identical on every line, so a reposition must not resize it")
                .isEqualTo(boundsBefore.width);
            assertThat(boundsAfter.height)
                .as("a reposition must not resize the marker")
                .isEqualTo(boundsBefore.height);
        }

        @Test
        void testClearTargetHidesMarker() {
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var overlay = mode.getInsertionMarkerOverlay();
            assertThat(overlay.isVisible()).isTrue();

            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, leftOfHeaderXPx()));

            assertThat(overlay.isVisible())
                .as("moving outside the content span clears the target and hides the marker")
                .isFalse();
        }

        @Test
        void testExitHidesMarker() {
            mode.enter(client);
            mode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var overlay = mode.getInsertionMarkerOverlay();
            assertThat(overlay.isVisible()).isTrue();

            mode.cancel();

            assertThat(overlay.isVisible())
                .as("exit() must hide the marker via clearTarget()")
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // place() no-op preconditions: no placement pending, or pending with no tracked target
    // -------------------------------------------------------------------------

    @Nested
    class PlaceIsNoOp {

        @Test
        void testPlaceWithNoTrackedTargetIsNoOp() {
            mode.enter(client);

            mode.place();

            assertThat(mode.isInProgress())
                .as("place() with no tracked insertion point must not end the placement")
                .isTrue();
            assertThat(client.chosenIndices).isEmpty();
        }

        @Test
        void testPlaceWithNoPendingPlacementIsNoOp() {
            mode.place();

            assertThat(mode.isInProgress()).isFalse();
            assertThat(client.chosenIndices).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // mouseClicked — resolve the index under the click, then place
    // -------------------------------------------------------------------------

    @Nested
    class ClickPlacement {

        @Test
        void testClickChoosesTheIndexUnderThePointer() {
            var lineComponent = trackedLineComponent(SECOND_INSERTION_INDEX);
            mode.enter(client);

            var consumed = mode.mouseClicked(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(consumed).isTrue();
            assertThat(client.chosenIndices).containsExactly(SECOND_INSERTION_INDEX);
            assertThat(client.endReasons).containsExactly(InsertionPointMode.EndReason.PLACED);
        }

        @Test
        void testClickReturnsFalseWhenNoPlacementIsPending() {
            var lineComponent = trackedLineComponent(FIRST_INSERTION_INDEX);

            var consumed = mode.mouseClicked(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(consumed).isFalse();
            assertThat(client.chosenIndices).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // isActive() / isInProgress()
    // -------------------------------------------------------------------------

    @Nested
    class ActiveStateTransitions {

        @Test
        void testIsActiveFalseBeforeEnter() {
            assertThat(InsertionPointMode.isActive()).isFalse();
            assertThat(mode.isInProgress()).isFalse();
        }

        @Test
        void testIsActiveTrueAfterEnter() {
            mode.enter(client);

            assertThat(InsertionPointMode.isActive()).isTrue();
            assertThat(mode.isInProgress()).isTrue();
        }

        @Test
        void testIsActiveFalseAfterCancel() {
            mode.enter(client);
            mode.cancel();

            assertThat(InsertionPointMode.isActive()).isFalse();
            assertThat(mode.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * A client that records everything the mode tells it, so the exactly-once promise can be
     * asserted by count rather than by "it fired". {@link #placement} chooses what it answers
     * to an offered index; {@link #rejectedIndex}, when set, is the one index its predicate
     * refuses.
     */
    private final class RecordingClient implements InsertionPointMode.Client {

        private final List<Integer> chosenIndices = new ArrayList<>();
        private final List<InsertionPointMode.EndReason> endReasons = new ArrayList<>();

        private InsertionPointMode.Placement placement = InsertionPointMode.Placement.COMPLETED;

        @Nullable
        private Integer rejectedIndex = null;

        // Whether the mode still reported itself in progress at the moment it said it had ended.
        private boolean wasInProgressAtEnd = false;

        @Override
        public boolean acceptsInsertionIndex(Line line, int index) {
            return rejectedIndex == null || rejectedIndex != index;
        }

        @Override
        public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
            chosenIndices.add(index);
            return placement;
        }

        @Override
        public void insertionPointModeDidEnd(InsertionPointMode.EndReason reason) {
            endReasons.add(reason);
            wasInProgressAtEnd = mode.isInProgress();
        }
    }

    /** A Line stub with a fixed running key and line width. */
    private static Line lineStub() {
        var song = mock(Song.class);
        when(song.getLineWidthSs()).thenReturn(LINE_WIDTH_SS);
        var line = mock(Line.class);
        when(line.getSong()).thenReturn(song);
        when(line.getRunningKey()).thenReturn(HEADER_KEY);
        return line;
    }

    /** A LineComponent stub whose layout resolves every mouse x to {@code insertionIndex}. */
    private static LineComponent trackedLineComponent(int insertionIndex) {
        var line = lineStub();
        var layoutResult = mock(LayoutResult.class);
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(insertionIndex);
        return lineComponentFor(line, layoutResult);
    }

    /** A LineComponent stub wired to the given line/layout at an identity (100%) zoom. */
    private static LineComponent lineComponentFor(Line line, LayoutResult layoutResult) {
        var lineComponent = mock(LineComponent.class);
        var lineScoreView = mock(ScoreView.class);
        when(lineScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lineComponent.getScoreView()).thenReturn(lineScoreView);
        when(lineComponent.getLine()).thenReturn(line);
        when(lineComponent.getLayoutResult()).thenReturn(layoutResult);
        // Unrelated to the mode's own math (which goes through getScoreView().getViewScale()
        // above), but LineOverlayComponent.updateBounds() reads this directly to convert the
        // insertion marker's ink from staff spaces to pixels; left unstubbed it is Mockito's
        // double default (0), which floors every computed bound to the same pixel regardless of
        // the underlying staff-space position.
        when(lineComponent.getViewPixelsPerStaffSpace()).thenReturn(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        return lineComponent;
    }

    /**
     * The absolute-coordinate constructor is required here: the short form derives
     * screen coordinates from the source component, which a non-showing mock cannot
     * supply, so it throws instead of building the event.
     */
    private static MouseEvent mouseMovedEvent(LineComponent source, int xViewPx) {
        return new MouseEvent(
            source, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
            xViewPx, 0, xViewPx, 0, 0, false, MouseEvent.NOBUTTON);
    }

    /** The left edge, in staff spaces, of the insertable content span used by {@link #lineStub()}. */
    private static double contentLeftSs() {
        return HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(HEADER_KEY);
    }

    /** A view-pixel x that lands inside the content span (header right edge .. line width). */
    private static int insideContentXPx() {
        var midSs = (contentLeftSs() + LINE_WIDTH_SS) / 2.0;
        return viewPxForSs(midSs);
    }

    /** A view-pixel x to the left of the header's right edge — outside the content span. */
    private static int leftOfHeaderXPx() {
        return viewPxForSs(contentLeftSs() - OUTSIDE_MARGIN_SS);
    }

    /** A view-pixel x to the right of the staff's right edge — outside the content span. */
    private static int rightOfStaffXPx() {
        return viewPxForSs(LINE_WIDTH_SS + OUTSIDE_MARGIN_SS);
    }

    private static int viewPxForSs(double ss) {
        return ViewScale.IDENTITY.toViewPx(new Ss(ss)).roundedPx();
    }
}
