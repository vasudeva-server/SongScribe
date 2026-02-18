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
import songscribe.music.Note;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Component that renders the in-line lyrics for a staff line.
 * <p>
 * Renders syllables positioned under their corresponding notes.
 * The vertical position is determined by {@link Line#getLyricsYPos()}.
 */
public class LineLyricsComponent extends ScoreComponent {

    /** The line whose lyrics to render. */
    private Line line;

    /** Index of the line within the composition. */
    private int lineIndex;

    /**
     * Creates a new LineLyricsComponent.
     */
    public LineLyricsComponent() {
        super();
        setMarginTop(LayoutStylesheet.px(LayoutStylesheet.LYRICS_ROW_MARGIN));
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

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null || line == null) {
            return;
        }

        var noteCount = line.noteCount();

        if (noteCount == 0) {
            return;
        }

        var font = composition.getLyricsFont();
        g2.setFont(font);
        g2.setColor(Color.BLACK);
        var metrics = g2.getFontMetrics();

        // Y position is relative to component top (baseline)
        var baselineY = metrics.getAscent();

        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var syllable = note.acceleration.syllable;

            if (syllable == null || syllable.isEmpty() || syllable.equals("_")) {
                continue;
            }

            var syllableWidth = metrics.stringWidth(syllable);

            // Center syllable under note hotspot
            var x = (note.getXPos() + Note.HOT_SPOT.x) - (syllableWidth / 2) + note.getSyllableMovement();
            g2.drawString(syllable, x, baselineY);

            // Draw melisma extender if needed
            if (note.acceleration.syllableRelation == Note.SyllableRelation.EXTENDER) {
                var extenderX = x + syllableWidth + 2;
                var extenderY = baselineY;
                var extenderEndX = extenderX + 20;
                g2.drawLine(extenderX, extenderY, extenderEndX, extenderY);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (composition == null || line == null) {
            return new Dimension(0, 0);
        }

        // Check if line has any lyrics
        var hasLyrics = false;

        for (var i = 0; i < line.noteCount(); i++) {
            var syllable = line.getNote(i).acceleration.syllable;

            if (syllable != null && !syllable.isEmpty() && !syllable.equals("_")) {
                hasLyrics = true;
                break;
            }
        }

        if (!hasLyrics) {
            return new Dimension(0, 0);
        }

        var font = composition.getLyricsFont();
        var metrics = getFontMetrics(font);
        var height = metrics.getHeight();

        return new Dimension(composition.getLineWidth(), height + marginTop);
    }
}
