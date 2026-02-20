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

import java.awt.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.renderer.ElementRenderContext;
import songscribe.ui.renderer.LyricsRenderer;

/**
 * Component that renders the in-line lyrics for a staff line.
 * <p>
 * Renders syllables positioned under their corresponding notes.
 * Uses {@link LyricsRenderer} for complete lyrics rendering including:
 * <ul>
 *   <li>Syllable text centered under notes</li>
 *   <li>Dashes between syllables of the same word</li>
 *   <li>Single dash/hyphen between words</li>
 *   <li>Extender lines for held syllables</li>
 *   <li>Begin-of-line continuation</li>
 * </ul>
 */
public class LineLyricsComponent extends ScoreComponent {

    /** The line whose lyrics to render. */
    private Line line;

    /** Index of the line within the composition. */
    private int lineIndex;

    /** Whether this is the last line in the composition. */
    private boolean isLastLine;

    /**
     * Creates a new LineLyricsComponent.
     */
    public LineLyricsComponent() {
        super();
        setMarginTop(LayoutStylesheet.toPixels(LayoutStylesheet.LYRICS_ROW_MARGIN));
    }

    /**
     * Sets the line whose lyrics to render.
     *
     * @param line      The line
     * @param lineIndex Index of the line
     */
    public void setLine(@NotNull Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        revalidate();
        repaint();
    }

    /**
     * Returns the line whose lyrics are being rendered.
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
     * Sets whether this is the last line in the composition.
     *
     * @param isLastLine true if this is the last line
     */
    public void setIsLastLine(boolean isLastLine) {
        this.isLastLine = isLastLine;
    }

    /**
     * Returns whether this is the last line in the composition.
     */
    public boolean isLastLine() {
        return isLastLine;
    }

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null || line == null) {
            return;
        }

        if (line.noteCount() == 0) {
            return;
        }

        // Check if line has any lyrics
        if (!hasLyrics()) {
            return;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT
        )) {
            var font = composition.getLyricsFont();
            g2.setFont(font);
            var metrics = g2.getFontMetrics();

            // Create render context with adjusted middleLineY
            // LyricsRenderer calculates: lyricsY = middleLineY + line.getLyricsYPos()
            // We want lyricsY to be metrics.getAscent() (baseline relative to component top)
            // So: middleLineY = metrics.getAscent() - line.getLyricsYPos()
            var ctx = new ElementRenderContext(composition);
            ctx.setCurrentLine(line);
            ctx.setLineIndex(lineIndex);
            ctx.setMiddleLineYSs((int) (metrics.getAscent() - line.getLyricsYPos()));

            // Delegate to LyricsRenderer for complete lyrics rendering
            LyricsRenderer.getInstance().renderLyrics(g2, line, ctx, isLastLine);
        }
    }

    /**
     * Checks if the line has any lyrics to render.
     *
     * @return true if the line has lyrics
     */
    private boolean hasLyrics() {
        for (var i = 0; i < line.noteCount(); i++) {
            var syllable = line.getNote(i).properties.syllable;

            if (syllable != null && !syllable.isEmpty() && !syllable.equals("_")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Dimension getPreferredSize() {
        if (composition == null || line == null) {
            return new Dimension(0, 0);
        }

        if (!hasLyrics()) {
            return new Dimension(0, 0);
        }

        var font = composition.getLyricsFont();
        var metrics = getFontMetrics(font);
        var height = metrics.getHeight();

        return new Dimension((int) composition.getLineWidth(), height + marginTop);
    }
}
