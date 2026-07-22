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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
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
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.message.MessageCenter;
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
                var heightPx = heightsPx.getOrDefault(child, 0);
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
        protected void renderOverlay(java.awt.Graphics2D g2) {
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
        var element = ElementType.CROTCHET.newInstance();
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
            final int firstLineHeightPx = 40;
            final int firstLineGrownHeightPx = 90;
            final int targetLineHeightPx = 30;
            final int stackWidthPx = 300;
            final int stackHeightPx = 300;

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
            final int titleHeightPx = 20;
            final int titleGrownHeightPx = 70;
            final int innerStackHeightPx = 60;
            final int targetLineHeightPx = 50;
            final int outerWidthPx = 300;
            final int outerHeightPx = 300;

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
    }
}
