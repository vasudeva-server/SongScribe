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

package songscribe.ui.renderer;

import java.awt.Color;
import java.awt.Graphics2D;

import songscribe.layout.AttributionContent;
import songscribe.layout.LayoutResult;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

/**
 * Draws the song's attribution block above the right end of the first line's staff. The block is
 * inert, with no {@code HitTarget} to resolve a color from, so it always paints in
 * {@link RenderingUtils#ELEMENT_COLOR}. It decides nothing: {@link AttributionContent} resolved
 * every font and position at layout time.
 */
public final class AttributionRenderer {

    private static final AttributionRenderer INSTANCE = new AttributionRenderer();

    private AttributionRenderer() {
    }

    public static AttributionRenderer getInstance() {
        return INSTANCE;
    }

    public void render(LineInvariants invariants, Graphics2D g2) {
        var typeset = invariants.getLayoutResult().decorationLayouts()
            .getTypeset(invariants.getSong().getAttributionElement());

        // No layout means the block was never stacked: any line but the first.
        if (typeset == null) {
            return;
        }

        DecorationContentRenderer.draw(
            g2,
            typeset.content(),
            typeset.xSs(),
            RenderingUtils.layoutYToComponentYSs(typeset.ySs(), invariants),
            RenderingUtils.ELEMENT_COLOR);
    }

    /**
     * The caller supplies the staff-space transform the score view would otherwise establish.
     */
    public void renderAtOrigin(Graphics2D g2, AttributionContent content) {
        drawContent(g2, content, 0, 0, RenderingUtils.ELEMENT_COLOR);
    }

    static void drawContent(
        Graphics2D g2,
        AttributionContent content,
        double xSs,
        double ySs,
        Color color) {

        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(color);

            for (var line : content.lines()) {
                g2.setFont(line.scaledFont());
                g2.drawString(
                    line.text(),
                    (float) (xSs + line.xSs()),
                    (float) (ySs + line.baselineOffsetSs()));
            }
        }
    }
}
