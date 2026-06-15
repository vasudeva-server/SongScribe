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

import org.jspecify.annotations.Nullable;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * Component that renders the song title.
 * <p>
 * The title is centered horizontally and may wrap to multiple lines
 * if it exceeds the line width. Uses the song's title font.
 */
public class TitleComponent extends ScoreComponent {

    /**
     * When non-null, overrides the title text drawn by the component. Lets the
     * song settings dialog render a live preview of the unsaved title without
     * mutating the song.
     */
    @Nullable
    private String previewTitle;

    /**
     * Overrides the rendered title with the given preview text, or restores
     * rendering from the song when {@code null}.
     */
    public void setPreviewTitle(@Nullable String previewTitle) {
        this.previewTitle = previewTitle;
        revalidate();
        repaint();
    }

    private String titleToRender() {
        return previewTitle != null ? previewTitle : getSong().getNumberedTitle();
    }

    @Override
    protected void render(Graphics2D g2) {
        if (song == null) {
            return;
        }

        // A bare JComponent does not fill its background even when opaque, so do
        // it here. On the score the component is transparent (opaque == false);
        // the settings-dialog preview makes it opaque to paint the page color.
        if (isOpaque()) {
            try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        }

        var title = titleToRender();

        if (title.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var font = getFont();
            g2.setFont(font);
            g2.setColor(Color.BLACK);
            var metrics = g2.getFontMetrics();

        // Wrap the title to fit in the line width
        var lineWidth = song.getLineWidthPx();
        var titleLines = StringUtils.wrapText(title, metrics, lineWidth);

        // Calculate max width of wrapped lines for centering
        var actualMaxWidth = 0;

        for (var titleLine : titleLines) {
            actualMaxWidth = Math.max(actualMaxWidth, metrics.stringWidth(titleLine));
        }

        // Center the title horizontally within the component
        var startX = (lineWidth - actualMaxWidth) / 2;

            // Draw each line
            var lineHeight = metrics.getHeight();
            var y = metrics.getAscent();

            for (var titleLine : titleLines) {
                var lineWidth2 = metrics.stringWidth(titleLine);
                var x = startX + ((actualMaxWidth - lineWidth2) / 2);
                g2.drawString(titleLine, x, y);
                y += lineHeight;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null) {
            return new Dimension(0, 0);
        }

        var title = titleToRender();

        if (title.isEmpty()) {
            return new Dimension(0, 0);
        }

        var font = getFont();
        var metrics = getFontMetrics(font);

        // Wrap the title to calculate height
        var titleLines = StringUtils.wrapText(title, metrics, song.getLineWidthPx());

        // Tight bounding box: each line is ascent + descent tall, with the font's
        // leading inserted only *between* lines, never below the last descender.
        var lineCount = titleLines.size();
        var glyphHeight = metrics.getAscent() + metrics.getDescent();
        var height = lineCount * glyphHeight + (lineCount - 1) * metrics.getLeading();

        return new Dimension(song.getLineWidthPx(), height);
    }
}
