/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.component.score;

import module java.desktop;

import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;

public class ScorePanel extends JPanel implements Scrollable {

    /** Subtracted from the visible height when computing a vertical block increment. */
    static final int VERTICAL_BLOCK_DECREMENT = 10;

    /** Subtracted from the visible width when computing a horizontal block increment. */
    static final int HORIZONTAL_BLOCK_DECREMENT = 20;

    private final Component content;

    /** Cached from the LAF in {@link #updateUI}, avoiding a property lookup on every layout pass. */
    private int minHorizontalPaddingPx;

    public ScorePanel(Component content) {
        this.content = content;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder());
        add(content);
    }

    @Override
    public void updateUI() {
        // setUI() (called by super) triggers updateUI() before our fields are initialized
        super.updateUI();

        setBackground(FlatLafProps.getColor(FlatLafKey.SCORE_PANEL_BACKGROUND));
        minHorizontalPaddingPx = FlatLafProps.getInt(FlatLafKey.SCORE_PANEL_MIN_HORIZONTAL_PADDING);
    }

    @Override
    public Dimension getPreferredSize() {
        var contentSize = content.getPreferredSize();
        var parentSize = getParent().getSize();
        var minWidth = contentSize.width + 2 * minHorizontalPaddingPx;
        var width = Math.max(minWidth, parentSize.width);
        return new Dimension(width, contentSize.height);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        // Return the content's natural preferred size so the JScrollPane can compute its own
        // preferred size without feedback from the current viewport width. If we delegated to
        // getPreferredSize() here, the viewport width would be included in the scroll pane's
        // preferred size, causing setFrameSize() to grow the window by one scroll bar width on
        // every "Open Recent" call.
        return content.getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(
        Rectangle visibleRect,
        int orientation,
        int direction
    ) {
        return 30;
    }

    @Override
    public int getScrollableBlockIncrement(
        Rectangle visibleRect,
        int orientation,
        int direction
    ) {
        return (orientation == SwingConstants.VERTICAL)
            ? (visibleRect.height - VERTICAL_BLOCK_DECREMENT)
            : (visibleRect.width - HORIZONTAL_BLOCK_DECREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
