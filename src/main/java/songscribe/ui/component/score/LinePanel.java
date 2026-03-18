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


import songscribe.music.Composition;
import songscribe.music.Line;

/**
 * Panel containing a single staff line and its associated lyrics.
 * <p>
 * This panel uses BoxLayout.Y_AXIS to arrange:
 * <ul>
 *   <li>{@link LineComponent} - the staff and notes</li>
 *   <li>{@link LineLyricsComponent} - the in-line lyrics</li>
 * </ul>
 */
public class LinePanel extends JPanel {

    /** The staff line component. */
    private final LineComponent lineComponent;

    /** The lyrics component. */
    private final LineLyricsComponent lyricsComponent;

    /** The line model. */
    private Line line;

    /** Index of this line within the composition. */
    private int lineIndex;

    /**
     * Creates a new LinePanel.
     *
     * @param composition The composition model
     * @param line        The line to render
     * @param lineIndex   Index of the line
     */
    public LinePanel(
        Composition composition,
        Line line,
        int lineIndex
    ) {
        this(composition, line, lineIndex, lineIndex == composition.lineCount() - 1);
    }

    /**
     * Creates a new LinePanel with explicit isLastLine flag.
     *
     * @param composition The composition model
     * @param line        The line to render
     * @param lineIndex   Index of the line
     * @param isLastLine  Whether this is the last line in the composition
     */
    public LinePanel(
        Composition composition,
        Line line,
        int lineIndex,
        boolean isLastLine
    ) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        this.line = line;
        this.lineIndex = lineIndex;

        // Create child components
        lineComponent = new LineComponent();
        lineComponent.setComposition(composition);
        lineComponent.setLine(line, lineIndex);
        lineComponent.setAlignmentX(LEFT_ALIGNMENT);

        lyricsComponent = new LineLyricsComponent();
        lyricsComponent.setComposition(composition);
        lyricsComponent.setLine(line, lineIndex);
        lyricsComponent.setIsLastLine(isLastLine);
        lyricsComponent.setAlignmentX(LEFT_ALIGNMENT);

        add(lineComponent);
        add(lyricsComponent);
    }

    /**
     * Returns the line component.
     */
    public LineComponent getLineComponent() {
        return lineComponent;
    }

    /**
     * Returns the lyrics component.
     */
    public LineLyricsComponent getLyricsComponent() {
        return lyricsComponent;
    }

    /**
     * Returns the line model.
     */
    public Line getLine() {
        return line;
    }

    /**
     * Returns the line index.
     */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Updates the composition for all child components.
     *
     * @param composition The composition
     */
    public void setComposition(Composition composition) {
        lineComponent.setComposition(composition);
        lyricsComponent.setComposition(composition);
    }

    /**
     * Updates the line for all child components.
     *
     * @param line      The line
     * @param lineIndex Index of the line
     */
    public void setLine(Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        lineComponent.setLine(line, lineIndex);
        lyricsComponent.setLine(line, lineIndex);
    }

    /**
     * Sets the middle line Y coordinate.
     *
     * @param middleLineYSs Y coordinate of middle staff line in staff-space units
     */
    public void setMiddleLineYSs(double middleLineYSs) {
        lineComponent.setMiddleLineYSs(middleLineYSs);
    }

    @Override
    public Dimension getPreferredSize() {
        var lineSize = lineComponent.getPreferredSize();
        var lyricsSize = lyricsComponent.getPreferredSize();

        var width = Math.max(lineSize.width, lyricsSize.width);
        var height = lineSize.height + lyricsSize.height;

        return new Dimension(width, height);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
