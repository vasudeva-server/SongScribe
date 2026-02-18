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

import songscribe.ui.layout.LayoutStylesheet;
import songscribe.util.StringUtils;

/**
 * Component that renders the composition title.
 * <p>
 * The title is centered horizontally and may wrap to multiple lines
 * if it exceeds 75% of the line width. Uses the composition's title font.
 */
public class TitleComponent extends ScoreComponent {

    /** Maximum percentage of line width the title can occupy before wrapping. */
    private static final double TITLE_MAX_WIDTH_PERCENTAGE = 0.75;

    /**
     * Creates a new TitleComponent.
     */
    public TitleComponent() {
        super();
        setMarginBottom(LayoutStylesheet.px(LayoutStylesheet.TITLE_MARGIN_BOTTOM_MU));
    }

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null) {
            return;
        }

        var title = composition.getTitle();

        if (title.isEmpty()) {
            return;
        }

        var number = composition.getNumber();

        if (!number.isEmpty()) {
            title = number + ". " + title;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT,
            songscribe.ui.renderer.GraphicsState.Property.COLOR
        )) {
            var font = composition.getTitleFont();
            g2.setFont(font);
            g2.setColor(Color.BLACK);
            var metrics = g2.getFontMetrics();

        // Wrap the title to fit in the maximum width
        var maxWidth = (int) (composition.getLineWidth() * TITLE_MAX_WIDTH_PERCENTAGE);
        var titleLines = StringUtils.wrapText(title, metrics, maxWidth);

        // Calculate max width of wrapped lines for centering
        var actualMaxWidth = 0;

        for (var titleLine : titleLines) {
            actualMaxWidth = Math.max(actualMaxWidth, metrics.stringWidth(titleLine));
        }

        // Center the title horizontally within the component
        var lineWidth = composition.getLineWidth();
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
        if (composition == null) {
            return new Dimension(0, 0);
        }

        var title = composition.getTitle();

        if (title.isEmpty()) {
            return new Dimension(0, 0);
        }

        var number = composition.getNumber();

        if (!number.isEmpty()) {
            title = number + ". " + title;
        }

        var font = composition.getTitleFont();
        var metrics = getFontMetrics(font);

        // Wrap the title to calculate height
        var maxWidth = (int) (composition.getLineWidth() * TITLE_MAX_WIDTH_PERCENTAGE);
        var titleLines = StringUtils.wrapText(title, metrics, maxWidth);

        var lineHeight = metrics.getHeight();
        var height = lineHeight * titleLines.size();

        return new Dimension(composition.getLineWidth(), height + marginBottom);
    }
}
