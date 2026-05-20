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

import module java.desktop;


import songscribe.dom.Song;
import songscribe.dom.Line;

/**
 * Thin wrapper panel that hosts a single {@link LineComponent}.
 */
public class LinePanel extends JPanel {

    private final LineComponent lineComponent;

    private Line line;

    private int lineIndex;

    public LinePanel(
        Song song,
        Line line,
        int lineIndex
    ) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        this.line = line;
        this.lineIndex = lineIndex;

        lineComponent = new LineComponent();
        lineComponent.setSong(song);
        lineComponent.setLine(line, lineIndex);
        lineComponent.setAlignmentX(LEFT_ALIGNMENT);

        add(lineComponent);
    }

    public LineComponent getLineComponent() {
        return lineComponent;
    }

    public Line getLine() {
        return line;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setSong(Song song) {
        lineComponent.setSong(song);
    }

    public void setLine(Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        lineComponent.setLine(line, lineIndex);
    }

    public void setMiddleLineYSs(double middleLineYSs) {
        lineComponent.setMiddleLineYSs(middleLineYSs);
    }

    @Override
    public Dimension getPreferredSize() {
        return lineComponent.getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
