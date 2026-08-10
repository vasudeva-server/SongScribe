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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LineOverlayComponent;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.OverlayHost;

/**
 * Unit tests for the phase-1 hosting surface: {@link ScoreView#addOverlay}'s z-order contract
 * (T1) and the {@link ScoreView#validateTree()} relayout hook (T2).
 * <p>
 * T2 uses a minimal {@link StackLayout} stand-in rather than the real {@code MainPanel} /
 * {@code StaffPanel} hierarchy: {@link LineComponent#getPreferredSize()} runs the real layout
 * engine (or requires a {@code Song}/{@code Line}), and {@code StaffLinesLayout} positions lines
 * from a package-private {@code LineComponent} field this test's package cannot reach. Neither
 * is what T2 is testing — the claim under test is {@code ScoreView.validateTree()}'s own hook
 * (call {@code super.validateTree()}, then refresh every registered overlay), which is
 * independent of which layout manager actually moved the target. {@code StackLayout} reproduces
 * the two documented relayout shapes (a layout manager repositioning its own child; an outer
 * layout manager moving a child without touching the child's own layout manager) with fully
 * controllable, engine-free geometry.
 */
class ScoreViewOverlayHostingTest extends UnitTest {

    /**
     * Stacks children top-to-bottom using explicitly assigned per-child heights, standing in
     * for {@code BoxLayout} (used by {@code MainPanel}) and {@code StaffLinesLayout} (used by
     * {@code StaffPanel}) without consulting {@link Component#getPreferredSize()}.
     */
    private static final class StackLayout implements LayoutManager {

        private final Map<Component, Integer> heightsPx = new LinkedHashMap<>();

        void setHeightPx(Component component, int heightPx) {
            heightsPx.put(component, heightPx);
        }

        @Override
        public void layoutContainer(Container parent) {
            var y = 0;

            for (var child : parent.getComponents()) {
                int heightPx = heightsPx.getOrDefault(child, 0);
                child.setBounds(0, y, parent.getWidth(), heightPx);
                y += heightPx;
            }
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
            // No-op: children are registered via setHeightPx, not by name.
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            heightsPx.remove(comp);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }
    }

    /** A concrete overlay whose ink bounds are fixed, so only its target's position matters. */
    private static final class FixedInkOverlay extends LineOverlayComponent {

        private final Rectangle2D inkBoundsSs;

        FixedInkOverlay(OverlayHost host, Rectangle2D inkBoundsSs) {
            super(host);
            this.inkBoundsSs = inkBoundsSs;
        }

        @Override
        protected Rectangle2D getInkBoundsSs() {
            return inkBoundsSs;
        }

        @Override
        protected void renderOverlay(Graphics2D g2) {
            // Not exercised by these tests.
        }
    }

    /** Builds a headless, interactive-mode {@link ScoreView} (non-null callback → BorderLayout). */
    private static ScoreView newScoreView() {
        return new ScoreView(ignored -> {});
    }

    /** Builds a real, empty {@link MainPanel} and registers it exactly as {@code initMainPanel()} would. */
    private static ScoreView scoreViewWithMainPanel() {
        var scoreView = newScoreView();
        var song = new Song();
        scoreView.setSong(song);

        var mainPanel = new MainPanel();
        mainPanel.setScoreView(scoreView);
        mainPanel.setSong(song);
        scoreView.setMainPanel(mainPanel);
        scoreView.add(mainPanel, BorderLayout.CENTER);

        return scoreView;
    }

    /**
     * Constructs a real {@link LyricEditor} on a detached line and registers it exactly as
     * {@link LyricEditor#openOn} does — {@code addOverlay}, reclaim the topmost z-order index,
     * mark it active — without the layout/focus/repaint side effects {@code openOn} also
     * performs, which this test does not need.
     */
    private static LyricEditor registerLyricEditor(ScoreView scoreView) {
        var line = detachedLine();
        var element = crotchet();
        line.addElement(element);

        var editor = new LyricEditor(scoreView, line, element);
        scoreView.addOverlay(editor);
        scoreView.setComponentZOrder(editor, ScoreView.LYRIC_EDITOR_Z_ORDER);
        scoreView.setActiveLyricEditor(editor);

        return editor;
    }

    @Nested
    class AddOverlayZOrder {

        private ScoreView scoreView;

        @BeforeEach
        void setUp() throws Exception {
            installFlatLafDefaults();
            scoreView = scoreViewWithMainPanel();
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());
        }

        @Test
        void testAddOverlayRegistersChildWithoutEvictingMainPanelFromBorderLayout() {
            var overlay = new JPanel();

            scoreView.addOverlay(overlay);

            var layout = (BorderLayout) scoreView.getLayout();
            assertThat(layout.getLayoutComponent(BorderLayout.CENTER))
                .as("mainPanel must remain the BorderLayout.CENTER child after addOverlay")
                .isSameAs(scoreView.getMainPanel());
            assertThat(overlay.getParent())
                .as("the overlay itself must have been added as a child")
                .isSameAs(scoreView);
        }

        @Test
        void testLyricEditorOpenedAfterExistingOverlaySitsAboveIt() {
            var overlay = new JPanel();
            scoreView.addOverlay(overlay);

            var editor = registerLyricEditor(scoreView);

            try {
                assertThat(scoreView.getComponentZOrder(editor))
                    .as("a lyric editor opened after an overlay must sit above it (lower z-order index)")
                    .isLessThan(scoreView.getComponentZOrder(overlay));
            } finally {
                MessageCenter.unsubscribe(editor);
            }
        }

        @Test
        void testZOrderDoesNotDependOnRegistrationOrder() {
            var editor = registerLyricEditor(scoreView);

            try {
                var overlay = new JPanel();
                scoreView.addOverlay(overlay);

                assertThat(scoreView.getComponentZOrder(editor))
                    .as("an overlay added after the editor must still land below it")
                    .isLessThan(scoreView.getComponentZOrder(overlay));
            } finally {
                MessageCenter.unsubscribe(editor);
            }
        }
    }

    @Nested
    class ValidateTreeRelayoutHook {

        private static final Rectangle2D FIXED_INK_SS = new Rectangle2D.Double(0, 0, 1, 1);

        /**
         * Runs {@code scoreView.validateTree()} while holding the AWT tree lock it requires
         * ({@code checkTreeLock()}), matching how {@code Container.validate()} would invoke it.
         */
        private static void runValidateTree(ScoreView scoreView) {
            synchronized (scoreView.getTreeLock()) {
                scoreView.validateTree();
            }
        }

        /**
         * A line-height change: the same {@link StackLayout} that positions the overlay's
         * target line re-runs and repositions it, because a preceding sibling within that same
         * stack grew.
         */
        @Test
        void testOverlayFollowsTargetAfterALineHeightChangeRepositionsItWithinItsOwnStack() {
            final var firstLineHeightPx = 40;
            final var firstLineGrownHeightPx = 90;
            final var targetLineHeightPx = 30;
            final var stackWidthPx = 300;
            final var stackHeightPx = 300;

            var scoreView = newScoreView();

            var stack = new JPanel();
            var stackLayout = new StackLayout();
            stack.setLayout(stackLayout);

            var firstLine = new LineComponent();
            var targetLine = new LineComponent();
            stack.add(firstLine);
            stack.add(targetLine);
            stackLayout.setHeightPx(firstLine, firstLineHeightPx);
            stackLayout.setHeightPx(targetLine, targetLineHeightPx);

            scoreView.add(stack, BorderLayout.CENTER);
            stack.setBounds(0, 0, stackWidthPx, stackHeightPx);

            var overlay = new FixedInkOverlay(scoreView, FIXED_INK_SS);
            scoreView.addOverlay(overlay);
            overlay.setTargetLine(targetLine);

            runValidateTree(scoreView);
            var boundsBefore = overlay.getBounds();

            stackLayout.setHeightPx(firstLine, firstLineGrownHeightPx);
            runValidateTree(scoreView);
            var boundsAfter = overlay.getBounds();

            assertThat(boundsAfter.y)
                .as("the target line moved down when the line before it in its stack grew, "
                    + "and the overlay must have followed it")
                .isGreaterThan(boundsBefore.y);
        }

        /**
         * A title-height change: an outer stack moves the inner stack (standing in for
         * {@code StaffPanel}) down without resizing it and without the inner stack's own
         * layout manager re-running (its child heights are never touched).
         */
        @Test
        void testOverlayFollowsTargetAfterATitleHeightChangeMovesItsStackWithoutResizingIt() {
            final var titleHeightPx = 20;
            final var titleGrownHeightPx = 70;
            final var innerStackHeightPx = 60;
            final var targetLineHeightPx = 50;
            final var outerWidthPx = 300;
            final var outerHeightPx = 300;

            var scoreView = newScoreView();

            var outer = new JPanel();
            var outerLayout = new StackLayout();
            outer.setLayout(outerLayout);

            var titleStub = new JComponent() {};
            var innerStack = new JPanel();
            var innerLayout = new StackLayout();
            innerStack.setLayout(innerLayout);

            var targetLine = new LineComponent();
            innerStack.add(targetLine);
            innerLayout.setHeightPx(targetLine, targetLineHeightPx);

            outer.add(titleStub);
            outer.add(innerStack);
            outerLayout.setHeightPx(titleStub, titleHeightPx);
            outerLayout.setHeightPx(innerStack, innerStackHeightPx);

            scoreView.add(outer, BorderLayout.CENTER);
            outer.setBounds(0, 0, outerWidthPx, outerHeightPx);

            var overlay = new FixedInkOverlay(scoreView, FIXED_INK_SS);
            scoreView.addOverlay(overlay);
            overlay.setTargetLine(targetLine);

            runValidateTree(scoreView);
            var boundsBefore = overlay.getBounds();
            var innerStackHeightBefore = innerStack.getHeight();

            // Grow only the title stub. The inner stack's own per-child heights are untouched:
            // its own layout manager has nothing new to compute, only its position moves.
            outerLayout.setHeightPx(titleStub, titleGrownHeightPx);
            runValidateTree(scoreView);
            var boundsAfter = overlay.getBounds();

            assertThat(innerStack.getHeight())
                .as("the stack standing in for StaffPanel must not have been resized")
                .isEqualTo(innerStackHeightBefore);
            assertThat(boundsAfter.y)
                .as("the overlay must have followed its target line down when the title above it grew")
                .isGreaterThan(boundsBefore.y);
        }

        /**
         * The set of overlays to refresh is derived from this view's children, not from a list
         * maintained by {@code addOverlay}/{@code removeOverlay} — so an overlay detached by any
         * route, including a bare {@link Container#remove} that bypasses {@code removeOverlay},
         * stops being visited. A maintained list would keep a stale entry here and go on
         * refreshing a component that is no longer in the hierarchy.
         */
        @Test
        void testOverlayDetachedByABareRemoveIsNoLongerRefreshed() {
            final var firstLineHeightPx = 40;
            final var firstLineGrownHeightPx = 90;
            final var targetLineHeightPx = 30;
            final var stackWidthPx = 300;
            final var stackHeightPx = 300;

            var scoreView = newScoreView();

            var stack = new JPanel();
            var stackLayout = new StackLayout();
            stack.setLayout(stackLayout);

            var firstLine = new LineComponent();
            var targetLine = new LineComponent();
            stack.add(firstLine);
            stack.add(targetLine);
            stackLayout.setHeightPx(firstLine, firstLineHeightPx);
            stackLayout.setHeightPx(targetLine, targetLineHeightPx);

            scoreView.add(stack, BorderLayout.CENTER);
            stack.setBounds(0, 0, stackWidthPx, stackHeightPx);

            var overlay = new FixedInkOverlay(scoreView, FIXED_INK_SS);
            scoreView.addOverlay(overlay);
            overlay.setTargetLine(targetLine);

            runValidateTree(scoreView);
            var boundsWhileAttached = overlay.getBounds();

            // Deliberately not removeOverlay(): the point is that the bypass route is safe too.
            scoreView.remove(overlay);

            stackLayout.setHeightPx(firstLine, firstLineGrownHeightPx);
            runValidateTree(scoreView);

            assertThat(overlay.getBounds())
                .as("a detached overlay must not be refreshed when its former target moves")
                .isEqualTo(boundsWhileAttached);
        }
    }

    @Nested
    class OverlayHostContract {

        /**
         * {@link OverlayHost}'s class doc requires every implementor to report {@code false}
         * here, because overlays are free-floating siblings whose bounds intersect the lines
         * beneath them. A {@code true} answer lets Swing skip repainting what an overlay
         * covers, so the ink underneath it would go stale with no other visible symptom —
         * cheap to revert accidentally, and nothing else in the suite would notice.
         */
        @Test
        void testScoreViewReportsUnoptimizedDrawingSoOverlappingChildrenAlwaysRepaint() {
            assertThat(newScoreView().isOptimizedDrawingEnabled())
                .as("an OverlayHost must report false from isOptimizedDrawingEnabled()")
                .isFalse();
        }
    }

    @Nested
    class ZoomRefreshHook {

        private static final Rectangle2D FIXED_INK_SS = new Rectangle2D.Double(0, 0, 4, 2);

        /**
         * A zoom change repositions and resizes an overlay that is already visible, without
         * waiting for a validation pass or a mouse move.
         * <p>
         * Asserts the geometric consequence rather than the pixel formula: the ink is a fixed
         * rectangle in staff spaces, so doubling the zoom must roughly double the overlay's
         * pixel width. The tolerance absorbs the outward floor/ceil rounding and the constant
         * 1px pad on each side, both of which are the subject of their own test elsewhere.
         */
        @Test
        void testVisibleOverlayResizesWhenTheZoomChanges() {
            final var initialZoomPercent = 100;
            final var zoomedPercent = 200;
            final var lineWidthPx = 500;
            final var lineHeightPx = 100;
            final var roundingTolerancePx = 4;

            var scoreView = newScoreView();
            scoreView.getViewScale().setZoomPercent(initialZoomPercent);

            var targetLine = new LineComponent();
            targetLine.setScoreView(scoreView);
            scoreView.add(targetLine, BorderLayout.CENTER);
            targetLine.setBounds(0, 0, lineWidthPx, lineHeightPx);

            var overlay = new FixedInkOverlay(scoreView, FIXED_INK_SS);
            scoreView.addOverlay(overlay);
            overlay.setTargetLine(targetLine);

            assertThat(overlay.isVisible())
                .as("precondition: the overlay must be visible before the zoom changes")
                .isTrue();
            var widthBefore = overlay.getWidth();

            scoreView.getViewScale().setZoomPercent(zoomedPercent);
            scoreView.zoomDidChangeRefreshOverlayBounds(
                new ZoomDidChangeNotification(initialZoomPercent, zoomedPercent, null));

            assertThat(overlay.getWidth())
                .as("doubling the zoom must roughly double the width of fixed staff-space ink")
                .isCloseTo(widthBefore * 2, within(roundingTolerancePx));
        }
    }
}
