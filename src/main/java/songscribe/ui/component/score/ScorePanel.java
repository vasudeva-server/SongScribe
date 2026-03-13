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

import org.jetbrains.annotations.NotNull;

public class ScorePanel extends JPanel implements Scrollable {

    public static final String BACKGROUND_KEY = "SongScribe.scorePanel.background";

    private final Component content;

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

        var color = UIManager.getColor(BACKGROUND_KEY);
        setBackground(color != null ? color : Color.LIGHT_GRAY);
    }

    @Override
    @NotNull
    public Dimension getPreferredSize() {
        var contentSize = content.getPreferredSize();
        var parentSize = getParent().getSize();
        var width = Math.max(contentSize.width, parentSize.width);
        return new Dimension(width, contentSize.height);
    }

    @Override
    @NotNull
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
            ? (visibleRect.height - 10)
            : (visibleRect.width - 20);
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
