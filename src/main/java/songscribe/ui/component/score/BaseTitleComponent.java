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

import songscribe.dom.Ss;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * Abstract base for title-style text components (title, subtitle).
 * <p>
 * Handles wrap, centering, and measurement. Subclasses supply the text via
 * {@link #songText()} and an optional leading gap via {@link #topGapPx()}.
 * <p>
 * Layout (when {@link #songText()} is non-empty):
 * <pre>
 *   +---------------------------------------------------+  ---
 *   |                  (topGapPx rows)                  |   |  topGap
 *   +---------------------------------------------------+  ---
 *   |              centered, wrapped text               |   |  textBlock
 *   +---------------------------------------------------+  ---
 *   total height = topGap + textBlock
 * </pre>
 * When {@link #songText()} is empty the component collapses to {@code (0, 0)}
 * and no gap is emitted — a subtitle with no text takes up no space.
 * <p>
 * The preview text mechanism ({@link #setPreviewText}) lets settings dialogs
 * render unsaved text without mutating the song.
 */
public abstract class BaseTitleComponent extends ScoreComponent {

    /**
     * When non-null, overrides the song text drawn by the component. Lets the
     * song settings dialog render a live preview of the unsaved text without
     * mutating the song.
     */
    @Nullable
    private String previewText;

    /**
     * Overrides the rendered text with the given preview text, or restores
     * rendering from the song when {@code null}.
     */
    public void setPreviewText(@Nullable String previewText) {
        this.previewText = previewText;
        revalidate();
        repaint();
    }

    /**
     * The line width in pixels used for text wrapping, centering, and the component
     * width: the document line width scaled by this component's zoom. On the score it
     * tracks the view zoom; a detached preview (no {@code ScoreView}) resolves to
     * {@link songscribe.ui.ViewScale#IDENTITY} and so renders at natural size.
     */
    private int lineWidthPx() {
        var theSong = song;

        if (theSong == null) {
            return 0;
        }

        return toViewPx(new Ss(theSong.getLineWidthSs())).roundedPx();
    }

    /**
     * Returns the song text for this component.
     * <p>
     * Subclasses return the appropriate field from the song (e.g. numbered title,
     * subtitle). Called each time the component is painted or measured.
     *
     * @return the text to display, or empty string for none
     */
    protected abstract String songText();

    /**
     * Returns the top gap in pixels to prepend before the text block.
     * <p>
     * Default is {@code 0}. Override to insert spacing above this component
     * (e.g. the subtitle gap between title and subtitle).
     *
     * @return top gap in pixels
     */
    protected int topGapPx() {
        return 0;
    }

    /** Returns the text to render, using the preview override when set. */
    private String textToRender() {
        return previewText != null ? previewText : songText();
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

        var text = textToRender();

        if (text.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var font = zoomedFont(getFont());
            g2.setFont(font);
            g2.setColor(Color.BLACK);
            var metrics = g2.getFontMetrics();

            // Wrap the text to fit in the line width
            var lineWidthPx = lineWidthPx();
            var textLines = StringUtils.wrapText(text, metrics, lineWidthPx);

            // Calculate max width of wrapped lines for centering
            var actualMaxWidth = 0;

            for (var textLine : textLines) {
                actualMaxWidth = Math.max(actualMaxWidth, metrics.stringWidth(textLine));
            }

            // Center the text horizontally within the component
            var startX = (lineWidthPx - actualMaxWidth) / 2;

            // Draw each line, offset by topGapPx so the gap appears above the text block
            var lineHeight = metrics.getHeight();
            var topPadding = GraphicUtils.topInkPadding(textLines, metrics);
            var y = topGapPx() + topPadding + metrics.getAscent();

            for (var textLine : textLines) {
                var textLineWidth = metrics.stringWidth(textLine);
                var x = startX + ((actualMaxWidth - textLineWidth) / 2);
                g2.drawString(textLine, x, y);
                y += lineHeight;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null) {
            return new Dimension(0, 0);
        }

        var text = textToRender();

        if (text.isEmpty()) {
            return new Dimension(0, 0);
        }

        var font = zoomedFont(getFont());
        var metrics = getFontMetrics(font);

        // Wrap the text to calculate height
        var textLines = StringUtils.wrapText(text, metrics, lineWidthPx());

        var textBlockHeight = GraphicUtils.getTextBlockHeight(metrics, textLines.size());

        // Some fonts render ink beyond the nominal ascent/descent (e.g. deep script
        // descenders); pad the block so that ink is not clipped by the component bounds.
        var padding = GraphicUtils.inkPadding(textLines, metrics);

        // Top gap only applies when there is text to display.
        var height = topGapPx() + padding.top() + textBlockHeight + padding.bottom();

        return new Dimension(lineWidthPx(), height);
    }
}
