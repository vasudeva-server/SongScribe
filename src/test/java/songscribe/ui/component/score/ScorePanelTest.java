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

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link ScorePanel}: the width-max logic in
 * {@link ScorePanel#getPreferredSize}, the viewport-feedback guard in
 * {@link ScorePanel#getPreferredScrollableViewportSize}, and the asymmetric
 * decrement constants in {@link ScorePanel#getScrollableBlockIncrement}.
 */
class ScorePanelTest extends UnitTest {

    // Reuse the production constants so this test cannot silently diverge if they change
    private static final int VERTICAL_BLOCK_DECREMENT = ScorePanel.VERTICAL_BLOCK_DECREMENT;
    private static final int HORIZONTAL_BLOCK_DECREMENT = ScorePanel.HORIZONTAL_BLOCK_DECREMENT;

    /**
     * Creates a ScorePanel whose content has the given preferred size, placed inside
     * a parent container whose size is set to {@code parentWidth × parentHeight}.
     * The parent is a plain JPanel — no layout manager validation needed because
     * we only call {@code getPreferredSize()} on the ScorePanel, not on the parent.
     */
    private ScorePanel scorePanelIn(
        int contentWidth, int contentHeight,
        int parentWidth, int parentHeight
    ) {
        var content = new JPanel();
        content.setPreferredSize(new Dimension(contentWidth, contentHeight));

        var scorePanel = new ScorePanel(content);

        var parent = new JPanel(null);  // null layout to honour setSize()
        parent.setSize(parentWidth, parentHeight);
        parent.add(scorePanel);

        return scorePanel;
    }

    // -----------------------------------------------------------------------
    // getPreferredSize
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetPreferredSize {

        /**
         * When content is wider than the parent, the content width is used.
         * Height always matches the content height.
         */
        @Test
        void testContentWiderThanParentUsesContentWidth() {
            var contentWidth = 800;
            var contentHeight = 600;
            var parentWidth = 500;

            var panel = scorePanelIn(contentWidth, contentHeight, parentWidth, 400);
            var size = panel.getPreferredSize();

            assertThat(size.width)
                .as("content wider than parent → content width wins")
                .isEqualTo(contentWidth);
            assertThat(size.height)
                .as("height always equals content height")
                .isEqualTo(contentHeight);
        }

        /**
         * When the parent is wider than the content, the parent width is used.
         * This prevents the score from shrinking horizontally below the viewport.
         */
        @Test
        void testParentWiderThanContentUsesParentWidth() {
            var contentWidth = 400;
            var contentHeight = 300;
            var parentWidth = 900;

            var panel = scorePanelIn(contentWidth, contentHeight, parentWidth, 500);
            var size = panel.getPreferredSize();

            assertThat(size.width)
                .as("parent wider than content → parent width wins")
                .isEqualTo(parentWidth);
            assertThat(size.height)
                .as("height always equals content height")
                .isEqualTo(contentHeight);
        }
    }

    // -----------------------------------------------------------------------
    // getPreferredScrollableViewportSize
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetPreferredScrollableViewportSize {

        /**
         * Must return the content's natural preferred size, not the panel's own
         * preferred size. If it delegated to getPreferredSize() the viewport width
         * would feed back into the scroll-pane's preferred size and grow the window
         * on every Open-Recent invocation.
         */
        @Test
        void testReturnsContentPreferredSizeNotPanelSize() {
            var contentWidth = 500;
            var contentHeight = 350;
            // Make the parent wider than the content so panel.getPreferredSize()
            // would return a larger width — confirming the two methods differ.
            var parentWidth = 900;

            var panel = scorePanelIn(contentWidth, contentHeight, parentWidth, 400);

            var viewportSize = panel.getPreferredScrollableViewportSize();
            var panelSize = panel.getPreferredSize();

            assertThat(viewportSize)
                .as("viewport size equals content preferred size")
                .isEqualTo(new Dimension(contentWidth, contentHeight));

            // The whole point of the guard: viewport size must differ from panel size
            // when the parent is wider than the content.
            assertThat(viewportSize.width)
                .as("viewport width must not equal the inflated panel width")
                .isNotEqualTo(panelSize.width);
        }
    }

    // -----------------------------------------------------------------------
    // getScrollableBlockIncrement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetScrollableBlockIncrement {

        // Arbitrary panel/content size — the block-increment methods only use the visibleRect
        private static final int DUMMY_SIZE = 200;

        private ScorePanel panel() {
            return scorePanelIn(DUMMY_SIZE, DUMMY_SIZE, DUMMY_SIZE, DUMMY_SIZE);
        }

        /**
         * Vertical block increment is {@code visibleRect.height - VERTICAL_BLOCK_DECREMENT}.
         */
        @Test
        void testVerticalIncrementIsHeightMinusDecrement() {
            var visibleHeight = 400;
            var visibleRect = new Rectangle(0, 0, 300, visibleHeight);

            var increment = panel().getScrollableBlockIncrement(
                visibleRect, SwingConstants.VERTICAL, 1
            );

            assertThat(increment)
                .as("vertical block increment = visibleRect.height - " + VERTICAL_BLOCK_DECREMENT)
                .isEqualTo(visibleHeight - VERTICAL_BLOCK_DECREMENT);
        }

        /**
         * Horizontal block increment is {@code visibleRect.width - HORIZONTAL_BLOCK_DECREMENT}.
         * The decrement is asymmetric (20 vs 10) and must not regress.
         */
        @Test
        void testHorizontalIncrementIsWidthMinusDecrement() {
            var visibleWidth = 600;
            var visibleRect = new Rectangle(0, 0, visibleWidth, 400);

            var increment = panel().getScrollableBlockIncrement(
                visibleRect, SwingConstants.HORIZONTAL, 1
            );

            assertThat(increment)
                .as("horizontal block increment = visibleRect.width - " + HORIZONTAL_BLOCK_DECREMENT)
                .isEqualTo(visibleWidth - HORIZONTAL_BLOCK_DECREMENT);
        }
    }
}
