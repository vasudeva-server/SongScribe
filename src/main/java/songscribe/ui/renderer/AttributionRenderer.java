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
 * Draws the song's attribution block above the right end of the first line's staff.
 * <p>
 * The block is inert: it owns no note and is edited only through Song Settings, so there is no
 * {@code HitTarget} to resolve a color from and it always paints in
 * {@link RenderingUtils#ELEMENT_COLOR}.
 * <p>
 * It draws inside the staff-space transform, like every other decoration, and decides nothing:
 * {@link AttributionContent} resolved every font, measured every line and centered every one of
 * them at layout time.
 */
public final class AttributionRenderer {

    /** Singleton instance. */
    private static final AttributionRenderer INSTANCE = new AttributionRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private AttributionRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static AttributionRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Draws the song's attribution block, if the line being rendered carries one.
     */
    public void render(LineInvariants invariants, Graphics2D g2) {
        var layout = invariants.getLayoutResult()
            .getDecorationLayout(invariants.getSong().getAttributionElement());

        // Anything but a typeset layout means the block was never stacked — any line but the
        // first, since the block is stacked with the content that gives it its size.
        if (!(layout instanceof LayoutResult.DecorationLayout.Typeset typeset)) {
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
     * Draws {@code content} with its top-left corner at the origin of the caller's transform, for
     * a preview outside any score view.
     * <p>
     * The caller supplies the staff-space transform the score view would otherwise establish, so
     * the block comes out at the size it will have in the score.
     */
    public void render(Graphics2D g2, AttributionContent content) {
        drawContent(g2, content, 0, 0, RenderingUtils.ELEMENT_COLOR);
    }

    /**
     * Paints one attribution block's typeset lines at the given position.
     * <p>
     * Each line carries its own font and its own centered X, so this walks them and computes
     * nothing — the same contract, for the same reason, as
     * {@link MetronomeRenderer#drawContent}.
     *
     * @param xSs   left edge of the block, in staff spaces
     * @param ySs   top edge of the block in component space, in staff spaces
     * @param color the color every line draws in
     */
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
