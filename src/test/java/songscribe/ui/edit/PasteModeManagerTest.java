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
import java.util.function.Supplier;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PasteModeDidChangeNotification;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.component.score.LineComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static songscribe.dom.InitialTempoTestSupport.ORIGINAL_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.attachBeatChange;
import static songscribe.dom.InitialTempoTestSupport.attachTempo;
import static songscribe.dom.InitialTempoTestSupport.tempoOf;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Unit tests for {@link PasteModeManager}, the paste-mode state machine documented in its
 * class-level diagram: enter/exit lifecycle, mouse-driven insertion-point tracking, and
 * placement outcome handling.
 */
class PasteModeManagerTest extends UnitTest {

    // A line with no key-signature accidentals and an arbitrary width wide enough to hold
    // several insertion points, shared by every target-tracking fixture below.
    private static final KeyType KEY_TYPE = KeyType.NONE;
    private static final int KEY_ACCIDENTAL_COUNT = 0;
    private static final double LINE_WIDTH_SS = 200.0;

    // Added past the content span's edges to build a mouse x unambiguously outside it.
    private static final double OUTSIDE_MARGIN_SS = 20.0;

    private static final int FIRST_INSERTION_INDEX = 2;
    private static final int SECOND_INSERTION_INDEX = 5;

    // Placing here takes over as the song's first element; FIRST_INSERTION_INDEX does not.
    private static final int SONG_FIRST_ELEMENT_INDEX = 0;

    private MockedStatic<MainFrame> mainFrameMock;
    private JLayeredPane layeredPane;
    private ScoreView scoreView;
    private PasteModeManager pasteModeManager;

    /**
     * A real, unrealized {@link OverlayHost} component for the insertion marker: enough for
     * {@code SwingUtilities.isDescendingFrom}/{@code convertPoint} (both just walk the parent
     * chain) without a visible window. Stubbed as {@code scoreView}'s host unconditionally —
     * every {@code updateTarget}/{@code clearTarget} call recomputes the marker's bounds via
     * {@code LineOverlayComponent.updateHostCursor}, which dereferences the host component
     * directly, so an unstubbed (null) host NPEs even in tests that never look at the marker.
     */
    private JPanel overlayHost;

    @BeforeEach
    void setUp() {
        // A real layered pane (not a mock) so tests can observe the overlay component and
        // bounds listener that enter()/exit() actually add and remove.
        layeredPane = new JLayeredPane();
        var mockFrame = mock(MainFrame.class);
        when(mockFrame.getLayeredPane()).thenReturn(layeredPane);
        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);

        var clipboardManager = mock(ClipboardManager.class);
        scoreView = mock(ScoreView.class);
        overlayHost = new JPanel();
        when(scoreView.getHostComponent()).thenReturn(overlayHost);
        pasteModeManager = new PasteModeManager(clipboardManager, scoreView);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
        // The constructor sets PasteModeManager's static instance as a side effect; reset it
        // so a later test class's isActive()/getActiveInstance() calls don't see this test's
        // torn-down manager.
        resetPasteModeManagerInstance(null);
    }

    // -------------------------------------------------------------------------
    // enter()
    // -------------------------------------------------------------------------

    @Nested
    class Enter {

        @Test
        void testEnterActivatesModeAndAddsOverlay() {
            pasteModeManager.enter();

            assertThat(pasteModeManager.isInProgress()).isTrue();
            assertThat(layeredPane.getComponentCount())
                .as("enter() must add exactly one overlay to the layered pane")
                .isEqualTo(1);
            assertThat(layeredPane.getComponentListeners())
                .as("enter() must add exactly one bounds listener to the layered pane")
                .hasSize(1);
        }

        @Test
        void testEnterPostsNotificationWithActiveTrue() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                pasteModeManager.enter();

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue()).isInstanceOf(PasteModeDidChangeNotification.class);
                assertThat(((PasteModeDidChangeNotification) captor.getValue()).isActive()).isTrue();
            }
        }

        @Test
        void testEnterWhenAlreadyActiveIsNoOp() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                pasteModeManager.enter();
                pasteModeManager.enter();

                assertThat(layeredPane.getComponentCount())
                    .as("a second enter() while already active must not add a duplicate overlay")
                    .isEqualTo(1);
                assertThat(layeredPane.getComponentListeners())
                    .as("a second enter() while already active must not add a duplicate bounds listener")
                    .hasSize(1);
                messageCenterMock.verify(() -> MessageCenter.post(any()), times(1));
            }
        }
    }

    // -------------------------------------------------------------------------
    // cancel() when not active
    // -------------------------------------------------------------------------

    @Nested
    class CancelWhenNotActive {

        @Test
        void testCancelWhenNotActiveIsSafeNoOp() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                pasteModeManager.cancel();

                assertThat(pasteModeManager.isInProgress()).isFalse();
                messageCenterMock.verify(() -> MessageCenter.post(any()), never());
            }

            assertThat(layeredPane.getComponentCount()).isZero();
        }
    }

    // -------------------------------------------------------------------------
    // exit() (reached via cancel()) tears down enter()'s state
    // -------------------------------------------------------------------------

    @Nested
    class ExitTeardown {

        @Test
        void testCancelRemovesOverlayAndBoundsListener() {
            pasteModeManager.enter();
            assertThat(layeredPane.getComponentCount()).isEqualTo(1);
            assertThat(layeredPane.getComponentListeners()).hasSize(1);

            pasteModeManager.cancel();

            assertThat(layeredPane.getComponentCount())
                .as("cancel() must remove the overlay added by enter()")
                .isZero();
            assertThat(layeredPane.getComponentListeners())
                .as("cancel() must remove the bounds listener added by enter()")
                .isEmpty();
        }

        @Test
        void testCancelClearsTrackedTargetState() {
            var line = lineStub();
            var layoutResult = mock(LayoutResult.class);
            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(FIRST_INSERTION_INDEX);
            var lineComponent = lineComponentFor(line, layoutResult);

            pasteModeManager.enter();
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(pasteModeManager.getTargetLineComponent()).isNotNull();

            pasteModeManager.cancel();

            assertThat(pasteModeManager.getTargetLineComponent()).isNull();
            assertThat(pasteModeManager.getTargetIndex()).isEqualTo(-1);
        }

        @Test
        void testCancelPostsNotificationWithActiveFalse() {
            pasteModeManager.enter();

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                pasteModeManager.cancel();

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue()).isInstanceOf(PasteModeDidChangeNotification.class);
                assertThat(((PasteModeDidChangeNotification) captor.getValue()).isActive()).isFalse();
            }
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
            var line = lineStub();
            var layoutResult = mock(LayoutResult.class);
            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(FIRST_INSERTION_INDEX);
            lineComponent = lineComponentFor(line, layoutResult);
            pasteModeManager.setActive(true);
        }

        @Test
        void testMouseMovedReturnsFalseWhenNotActive() {
            pasteModeManager.setActive(false);
            var consumed = pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(consumed).isFalse();
            assertThat(pasteModeManager.getTargetLineComponent()).isNull();
        }

        @Test
        void testMouseInsideContentSpanTracksInsertionIndex() {
            var consumed = pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(consumed).isTrue();
            assertThat(pasteModeManager.getTargetLineComponent()).isEqualTo(lineComponent);
            assertThat(pasteModeManager.getTargetIndex()).isEqualTo(FIRST_INSERTION_INDEX);
        }

        @Test
        void testMouseLeftOfHeaderClearsTarget() {
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(pasteModeManager.getTargetLineComponent()).isNotNull();

            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, leftOfHeaderXPx()));

            assertThat(pasteModeManager.getTargetLineComponent()).isNull();
            assertThat(pasteModeManager.getTargetIndex()).isEqualTo(-1);
        }

        @Test
        void testMouseRightOfStaffClearsTarget() {
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(pasteModeManager.getTargetLineComponent()).isNotNull();

            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, rightOfStaffXPx()));

            assertThat(pasteModeManager.getTargetLineComponent()).isNull();
            assertThat(pasteModeManager.getTargetIndex()).isEqualTo(-1);
        }

        @Test
        void testTargetTracksAcrossLineComponentChange() {
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            assertThat(pasteModeManager.getTargetLineComponent()).isEqualTo(lineComponent);

            var otherLine = lineStub();
            var otherLayoutResult = mock(LayoutResult.class);
            when(otherLayoutResult.findInsertionIndex(anyDouble(), eq(otherLine))).thenReturn(SECOND_INSERTION_INDEX);
            var otherLineComponent = lineComponentFor(otherLine, otherLayoutResult);

            pasteModeManager.mouseMoved(otherLineComponent, mouseMovedEvent(otherLineComponent, insideContentXPx()));

            assertThat(pasteModeManager.getTargetLineComponent()).isEqualTo(otherLineComponent);
            assertThat(pasteModeManager.getTargetIndex()).isEqualTo(SECOND_INSERTION_INDEX);
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

            pasteModeManager.setActive(true);
        }

        /**
         * The marker is constructed and added to the host in {@code PasteModeManager}'s
         * constructor rather than lazily on activation, and a fresh Swing {@code JComponent}
         * defaults to visible — so "hidden while paste mode is off" is a real claim about
         * {@code mouseMoved}'s inactive early-return, not something Swing gives for free. Every
         * other test in this class asserts visibility only after {@code setActive(true)}.
         */
        @Test
        void testMouseMovedLeavesMarkerHiddenWhilePasteModeIsInactive() {
            pasteModeManager.setActive(false);

            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(pasteModeManager.getInsertionMarkerOverlay().isVisible())
                .as("the insertion marker must stay hidden while paste mode is inactive")
                .isFalse();
        }

        @Test
        void testUpdateTargetShowsMarkerWithRealBounds() {
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            var overlay = pasteModeManager.getInsertionMarkerOverlay();

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
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var overlay = pasteModeManager.getInsertionMarkerOverlay();
            var boundsBefore = overlay.getBounds();

            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(SECOND_INSERTION_INDEX);
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
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
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var overlay = pasteModeManager.getInsertionMarkerOverlay();
            assertThat(overlay.isVisible()).isTrue();

            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, leftOfHeaderXPx()));

            assertThat(overlay.isVisible())
                .as("moving outside the content span clears the target and hides the marker")
                .isFalse();
        }

        @Test
        void testExitHidesMarker() {
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            var overlay = pasteModeManager.getInsertionMarkerOverlay();
            assertThat(overlay.isVisible()).isTrue();

            pasteModeManager.cancel();

            assertThat(overlay.isVisible())
                .as("exit() must hide the marker via clearTarget()")
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // place() no-op preconditions: not active, or active with no tracked target
    // -------------------------------------------------------------------------

    @Nested
    class PlaceIsNoOp {

        @Test
        void testPlaceWithNoTrackedTargetIsNoOp() {
            pasteModeManager.setActive(true);

            pasteModeManager.place();

            assertThat(pasteModeManager.isInProgress())
                .as("place() with no tracked insertion point must not exit paste mode")
                .isTrue();
            verifyNoInteractions(scoreView);
        }

        @Test
        void testPlaceWhenNotActiveIsNoOp() {
            pasteModeManager.place();

            assertThat(pasteModeManager.isInProgress()).isFalse();
            verifyNoInteractions(scoreView);
        }
    }

    // -------------------------------------------------------------------------
    // placeAtTarget() outcome handling (via place())
    // -------------------------------------------------------------------------

    @Nested
    class PlaceAtTargetOutcomes {

        private Line line;
        private LineComponent lineComponent;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            line = lineStub();
            when(line.withModificationResult(any())).thenAnswer(
                invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

            var layoutResult = mock(LayoutResult.class);
            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(FIRST_INSERTION_INDEX);
            lineComponent = lineComponentFor(line, layoutResult);

            controller = mock(ScoreViewController.class);
            when(scoreView.getController()).thenReturn(controller);

            pasteModeManager.setActive(true);
            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
        }

        @Test
        void testInsertedOutcomeExitsPasteMode() {
            when(controller.tryInsertFragment(eq(line), eq(FIRST_INSERTION_INDEX), isNull()))
                .thenReturn(ScoreViewController.FragmentInsertOutcome.INSERTED);

            pasteModeManager.place();

            assertThat(pasteModeManager.isInProgress()).isFalse();
            assertThat(pasteModeManager.getTargetLineComponent()).isNull();
        }

        @Test
        void testCancelledOutcomeExitsPasteMode() {
            when(controller.tryInsertFragment(eq(line), eq(FIRST_INSERTION_INDEX), isNull()))
                .thenReturn(ScoreViewController.FragmentInsertOutcome.CANCELLED);

            pasteModeManager.place();

            assertThat(pasteModeManager.isInProgress()).isFalse();
            assertThat(pasteModeManager.getTargetLineComponent()).isNull();
        }

        @Test
        void testLineFullOutcomeKeepsPasteModeActiveForRetry() {
            when(controller.tryInsertFragment(eq(line), eq(FIRST_INSERTION_INDEX), isNull()))
                .thenReturn(ScoreViewController.FragmentInsertOutcome.LINE_FULL);

            pasteModeManager.place();

            assertThat(pasteModeManager.isInProgress()).isTrue();
            assertThat(pasteModeManager.getTargetLineComponent()).isEqualTo(lineComponent);
            assertThat(pasteModeManager.getTargetIndex()).isEqualTo(FIRST_INSERTION_INDEX);
        }
    }

    // -------------------------------------------------------------------------
    // placeAtTarget()'s tempo-and-beat-change warning — the fourth call site of
    // InitialTempoConfirms.warnIfTempoAndBeatChange, and the only one this test class owns
    // -------------------------------------------------------------------------

    @Nested
    class PlaceAtTargetInitialTempoWarning {

        private Line line;
        private ScoreViewController controller;

        /**
         * A real {@link Song} and {@link Line} rather than {@code lineStub()}'s mocks: the
         * warning gate asks the song where its first element is and what that element carries,
         * and a mock answers neither.
         */
        @BeforeEach
        void setUp() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.setLineWidthSs(LINE_WIDTH_SS));
            line = song.getLine(0);

            var songFirstElement = crotchet();
            song.withoutMutationTracking(() -> {
                line.addElement(songFirstElement);
                line.addElement(crotchet());
                line.addElement(crotchet());
            });

            // The state the warning is about — reachable with no paste involved at all, since
            // the beat-change dialog attaches a beat change to any note the user picks.
            attachTempo(songFirstElement, tempoOf(ORIGINAL_TEMPO_BPM));
            attachBeatChange(songFirstElement);

            controller = mock(ScoreViewController.class);
            when(scoreView.getController()).thenReturn(controller);

            pasteModeManager.setActive(true);
        }

        /** Tracks the given insertion point, then places the fragment there. */
        private void placeAt(int insertionIndex) {
            var layoutResult = mock(LayoutResult.class);
            when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(insertionIndex);
            var lineComponent = lineComponentFor(line, layoutResult);
            when(controller.tryInsertFragment(eq(line), eq(insertionIndex), isNull()))
                .thenReturn(ScoreViewController.FragmentInsertOutcome.INSERTED);

            pasteModeManager.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
            pasteModeManager.place();
        }

        @Test
        void testPlacingAtTheSongsFirstElementWarnsAboutTheTempoAndBeatChange() {
            try (var dialogs = mockStatic(OptionDialogs.class)) {
                placeAt(SONG_FIRST_ELEMENT_INDEX);

                dialogs.verify(() -> OptionDialogs.showWarningMessage(any(), any(), any()));
            }
        }

        @Test
        void testPlacingAwayFromTheSongsFirstElementRaisesNoWarning() {
            try (var dialogs = mockStatic(OptionDialogs.class)) {
                placeAt(FIRST_INSERTION_INDEX);

                dialogs.verify(
                    () -> OptionDialogs.showWarningMessage(any(), any(), any()), never());
            }
        }
    }

    // -------------------------------------------------------------------------
    // isActive() / isInProgress() / getActiveInstance()
    // -------------------------------------------------------------------------

    @Nested
    class ActiveStateTransitions {

        @Test
        void testIsActiveFalseBeforeEnter() {
            assertThat(PasteModeManager.isActive()).isFalse();
            assertThat(pasteModeManager.isInProgress()).isFalse();
            assertThat(PasteModeManager.getActiveInstance()).isNull();
        }

        @Test
        void testIsActiveTrueAfterEnter() {
            pasteModeManager.enter();

            assertThat(PasteModeManager.isActive()).isTrue();
            assertThat(pasteModeManager.isInProgress()).isTrue();
            assertThat(PasteModeManager.getActiveInstance()).isEqualTo(pasteModeManager);
        }

        @Test
        void testIsActiveFalseAfterCancel() {
            pasteModeManager.enter();
            pasteModeManager.cancel();

            assertThat(PasteModeManager.isActive()).isFalse();
            assertThat(pasteModeManager.isInProgress()).isFalse();
            assertThat(PasteModeManager.getActiveInstance()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void resetPasteModeManagerInstance(@Nullable PasteModeManager value) {
        PasteModeManager.setInstance(value);
    }

    /** A Line stub with a fixed key-signature accidental count and line width. */
    private static Line lineStub() {
        var song = mock(Song.class);
        when(song.getLineWidthSs()).thenReturn(LINE_WIDTH_SS);
        var line = mock(Line.class);
        when(line.getSong()).thenReturn(song);
        when(line.getKeyAccidentalCount()).thenReturn(KEY_ACCIDENTAL_COUNT);
        return line;
    }

    /** A LineComponent stub wired to the given line/layout at an identity (100%) zoom. */
    private static LineComponent lineComponentFor(Line line, LayoutResult layoutResult) {
        var lineComponent = mock(LineComponent.class);
        var lineScoreView = mock(ScoreView.class);
        when(lineScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lineComponent.getScoreView()).thenReturn(lineScoreView);
        when(lineComponent.getLine()).thenReturn(line);
        when(lineComponent.getLayoutResult()).thenReturn(layoutResult);
        // Unrelated to PasteModeManager's own math (which goes through getScoreView().getViewScale()
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
        return HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(KEY_TYPE, KEY_ACCIDENTAL_COUNT);
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
