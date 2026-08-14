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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.ui.ViewScale;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.component.ScoreViewController.FragmentInsertOutcome;
import songscribe.ui.component.score.LineComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasteModeManager} as a client of {@link InsertionPointMode}: the
 * paste-mode banner's lifecycle, paste's index rule, and the mapping from a fragment-insert
 * outcome onto "this placement is done" or "try again".
 *
 * <p>Driven through a real {@code InsertionPointMode} rather than a mock of it, because the
 * promises under test are about what the client does when the mode calls it, and the mode is
 * what decides when that is. The mode's own contract — the exactly-once end-of-placement
 * report, insertion-point tracking, the marker — belongs to {@code InsertionPointModeTest} and
 * is not restated here.
 */
class PasteModeManagerTest extends UnitTest {

    // A line with no key-signature accidentals and an arbitrary width wide enough to hold
    // several insertion points, shared by every fixture below.
    private static final Key HEADER_KEY = new Key(KeyType.NONE, 0);
    private static final double LINE_WIDTH_SS = 200.0;

    private static final int INSERTION_INDEX = 2;

    /** Elements on the fixture line, so the index domain the predicate is asked about is finite. */
    private static final int ELEMENT_COUNT = 6;

    private MockedStatic<MainFrame> mainFrameMock;
    private JLayeredPane layeredPane;
    private ScoreView scoreView;
    private InsertionPointMode insertionPointMode;
    private PasteModeManager pasteModeManager;

    /**
     * A real, unrealized overlay host component for the insertion marker: enough for
     * {@code SwingUtilities.isDescendingFrom}/{@code convertPoint} (both just walk the parent
     * chain) without a visible window. Stubbed as {@code scoreView}'s host unconditionally —
     * every target update recomputes the marker's bounds via
     * {@code LineOverlayComponent.updateHostCursor}, which dereferences the host component
     * directly, so an unstubbed (null) host NPEs even in tests that never look at the marker.
     */
    private JPanel overlayHost;

    @BeforeEach
    void setUp() {
        // A real layered pane (not a mock) so tests can observe the banner component and
        // bounds listener that enter()/end actually add and remove.
        layeredPane = new JLayeredPane();
        var mockFrame = mock(MainFrame.class);
        when(mockFrame.getLayeredPane()).thenReturn(layeredPane);
        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);

        scoreView = mock(ScoreView.class);
        overlayHost = new JPanel();
        when(scoreView.getHostComponent()).thenReturn(overlayHost);
        insertionPointMode = new InsertionPointMode(scoreView);
        pasteModeManager = new PasteModeManager(scoreView, insertionPointMode);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
        // The mode's constructor sets its static instance as a side effect; reset it so a later
        // test class's isActive() calls don't see this test's torn-down mode.
        InsertionPointMode.setInstance(null);
    }

    // -------------------------------------------------------------------------
    // enter() — the banner goes up with the placement
    // -------------------------------------------------------------------------

    @Nested
    class Enter {

        @Test
        void testEnterStartsAPlacementAndRaisesTheBanner() {
            pasteModeManager.enter();

            assertThat(insertionPointMode.isInProgress()).isTrue();
            assertThat(layeredPane.getComponentCount())
                .as("enter() must add exactly one banner to the layered pane")
                .isEqualTo(1);
            assertThat(layeredPane.getComponentListeners())
                .as("enter() must add exactly one bounds listener to the layered pane")
                .hasSize(1);
        }

        @Test
        void testEnterWhenAPlacementIsAlreadyPendingIsNoOp() {
            pasteModeManager.enter();
            pasteModeManager.enter();

            assertThat(layeredPane.getComponentCount())
                .as("a second enter() must not add a duplicate banner")
                .isEqualTo(1);
            assertThat(layeredPane.getComponentListeners())
                .as("a second enter() must not add a duplicate bounds listener")
                .hasSize(1);
        }

        @Test
        void testEnterRaisesNoBannerWhenAnotherClientOwnsThePlacement() {
            insertionPointMode.enter(new SilentClient());

            pasteModeManager.enter();

            assertThat(layeredPane.getComponentCount())
                .as("paste never entered, so it must not leave a banner nobody will take down")
                .isZero();
            assertThat(layeredPane.getComponentListeners()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // insertionPointModeDidEnd — the banner comes down however the placement ended
    // -------------------------------------------------------------------------

    @Nested
    class BannerTeardown {

        @Test
        void testCancellingThePlacementRemovesTheBannerAndBoundsListener() {
            pasteModeManager.enter();
            assertThat(layeredPane.getComponentCount()).isEqualTo(1);

            insertionPointMode.cancel();

            assertThat(layeredPane.getComponentCount())
                .as("a cancelled placement must remove the banner enter() added")
                .isZero();
            assertThat(layeredPane.getComponentListeners())
                .as("a cancelled placement must remove the bounds listener enter() added")
                .isEmpty();
        }

        @Test
        void testCompletingThePlacementRemovesTheBannerAndBoundsListener() {
            var lineComponent = arrangeTrackedTarget(FragmentInsertOutcome.INSERTED);
            assertThat(layeredPane.getComponentCount()).isEqualTo(1);

            insertionPointMode.mouseClicked(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));

            assertThat(layeredPane.getComponentCount())
                .as("a completed placement must remove the banner enter() added")
                .isZero();
            assertThat(layeredPane.getComponentListeners()).isEmpty();
        }

        @Test
        void testADeclinedPlacementLeavesTheBannerUpForAnotherTry() {
            var lineComponent = arrangeTrackedTarget(FragmentInsertOutcome.LINE_FULL);

            insertionPointMode.place();

            assertThat(insertionPointMode.isInProgress()).isTrue();
            assertThat(layeredPane.getComponentCount())
                .as("the placement is still pending, so its banner must stay up")
                .isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // acceptsInsertionIndex — paste's index rule
    // -------------------------------------------------------------------------

    @Nested
    class PasteIndexRule {

        /**
         * A fragment may be pasted before any element of a line, first and last included, so
         * the predicate accepts the whole index domain the mode can hand it: 0 through
         * {@code effectiveElementCount()} inclusive. Enumerated rather than sampled, because
         * the domain is finite and the interesting cases are its ends.
         */
        @ParameterizedTest
        @MethodSource("songscribe.ui.edit.PasteModeManagerTest#everyInsertionIndex")
        void testEveryInsertionIndexOnTheLineIsAcceptedForPaste(int index) {
            var line = lineStub();

            assertThat(pasteModeManager.acceptsInsertionIndex(line, index)).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // insertionPointChosen — outcome mapping
    // -------------------------------------------------------------------------

    @Nested
    class PlacementOutcomes {

        @ParameterizedTest
        @MethodSource("songscribe.ui.edit.PasteModeManagerTest#outcomeToPlacement")
        void testFragmentInsertOutcomeDecidesWhetherThePlacementIsDone(
            FragmentInsertOutcome outcome, InsertionPointMode.Placement expected) {
            var line = lineStub();
            when(line.withModificationResult(any())).thenAnswer(
                invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
            var controller = mock(ScoreViewController.class);
            when(scoreView.getController()).thenReturn(controller);
            when(controller.tryInsertFragment(eq(line), eq(INSERTION_INDEX), isNull())).thenReturn(outcome);

            assertThat(pasteModeManager.insertionPointChosen(line, INSERTION_INDEX)).isEqualTo(expected);
        }

        /**
         * The table above claims to cover the outcome domain; nothing else connects the two, so
         * a new outcome must fail here rather than quietly go untested.
         */
        @Test
        void testTheOutcomeTableCoversEveryFragmentInsertOutcome() {
            var tabled = outcomeToPlacement().map(arguments -> arguments.get()[0]).toList();

            assertThat(tabled).containsExactlyInAnyOrder((Object[]) FragmentInsertOutcome.values());
        }

        /**
         * With no controller there is nothing to insert through, so the point is declined and
         * the placement stays pending — the same answer as "line full", and for the same reason:
         * nothing was mutated.
         */
        @Test
        void testNoControllerDeclinesTheChosenPoint() {
            var line = lineStub();
            when(scoreView.getController()).thenReturn(null);

            assertThat(pasteModeManager.insertionPointChosen(line, INSERTION_INDEX))
                .isEqualTo(InsertionPointMode.Placement.DECLINED);
        }
    }

    // -------------------------------------------------------------------------
    // Data sources
    // -------------------------------------------------------------------------

    static IntStream everyInsertionIndex() {
        return IntStream.rangeClosed(0, ELEMENT_COUNT);
    }

    static Stream<Arguments> outcomeToPlacement() {
        return Stream.of(
            Arguments.of(FragmentInsertOutcome.INSERTED, InsertionPointMode.Placement.COMPLETED),
            Arguments.of(FragmentInsertOutcome.CANCELLED, InsertionPointMode.Placement.COMPLETED),
            Arguments.of(FragmentInsertOutcome.LINE_FULL, InsertionPointMode.Placement.DECLINED),
            Arguments.of(FragmentInsertOutcome.EMPTY, InsertionPointMode.Placement.DECLINED));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A client that enters the mode and does nothing, so paste can be refused entry. */
    private static final class SilentClient implements InsertionPointMode.Client {

        @Override
        public boolean acceptsInsertionIndex(Line line, int index) {
            return true;
        }

        @Override
        public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
            return InsertionPointMode.Placement.COMPLETED;
        }

        @Override
        public void insertionPointModeDidEnd(InsertionPointMode.EndReason reason) {
            // Nothing to tear down.
        }
    }

    /**
     * Enters paste mode, tracks an insertion point on a stub line, and arms the controller to
     * answer {@code outcome}.
     *
     * @param outcome what {@code tryInsertFragment} will report for the tracked point
     * @return the line component the point is tracked on
     */
    private LineComponent arrangeTrackedTarget(FragmentInsertOutcome outcome) {
        var line = lineStub();
        when(line.withModificationResult(any())).thenAnswer(
            invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        var layoutResult = mock(LayoutResult.class);
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(INSERTION_INDEX);
        var lineComponent = lineComponentFor(line, layoutResult);

        var controller = mock(ScoreViewController.class);
        when(scoreView.getController()).thenReturn(controller);
        when(controller.tryInsertFragment(eq(line), eq(INSERTION_INDEX), isNull())).thenReturn(outcome);

        pasteModeManager.enter();
        insertionPointMode.mouseMoved(lineComponent, mouseMovedEvent(lineComponent, insideContentXPx()));
        return lineComponent;
    }

    /** A Line stub with a fixed running key, line width and element count. */
    private static Line lineStub() {
        var song = mock(Song.class);
        when(song.getLineWidthSs()).thenReturn(LINE_WIDTH_SS);
        var line = mock(Line.class);
        when(line.getSong()).thenReturn(song);
        when(line.getRunningKey()).thenReturn(HEADER_KEY);
        when(line.effectiveElementCount()).thenReturn(ELEMENT_COUNT);
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

    /** A view-pixel x that lands inside the content span (header right edge .. line width). */
    private static int insideContentXPx() {
        var contentLeftSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(HEADER_KEY);
        return ViewScale.IDENTITY.toViewPx(new Ss((contentLeftSs + LINE_WIDTH_SS) / 2.0)).roundedPx();
    }
}
