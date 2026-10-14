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
import java.awt.Font;
import java.awt.Graphics2D;

import songscribe.dom.MetronomeAttachment;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.layout.GlyphItem;
import songscribe.layout.LayoutResult;
import songscribe.layout.MetronomeContent;
import songscribe.layout.TextItem;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

public abstract class MetronomeRenderer implements ElementRenderer<StaffElement> {

    protected static final Font TEMPO_NOTE_FONT = RenderingUtils.getMusicFont().deriveFont(RenderingUtils.FONT_SIZE * MetronomeAttachment.NOTE_SCALE);

    /**
     * {@link MetronomeContent} typeset the whole marking at layout time, so this decides nothing:
     * deciding here could let the drawn ink disagree with the measured box that layout, hit
     * testing and vertical stacking rely on, and measuring would put a {@code TextLayout}
     * construction back into the paint loop.
     */
    static void drawContent(
        Graphics2D g2,
        MetronomeContent content,
        double xSs,
        double ySs,
        Color color
    ) {
        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(color);

            for (var item : content.items()) {
                String text;

                switch (item) {
                    case GlyphItem glyphItem -> {
                        g2.setFont(TEMPO_NOTE_FONT);
                        text = glyphItem.glyph().asString();
                    }
                    case TextItem textItem -> {
                        g2.setFont(textItem.scaledFont());
                        text = textItem.text();
                    }
                }

                g2.drawString(
                    text,
                    (float) (xSs + item.xSs()),
                    (float) (ySs + item.baselineOffsetSs()));
            }
        }
    }

    protected void renderAttachment(
        StaffElement element,
        MetronomeAttachment attachment,
        LineInvariants invariants,
        ElementFrame frame,
        Graphics2D g2
    ) {
        var decorationLayout = invariants.getLayoutResult().decorationLayouts().requireTypeset(attachment);
        var ySs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

        // A metronome marking is selectable in its own right, so its selection is folded into
        // the color resolved from the owner note rather than applied over it: an unselected
        // marking still follows its note through playback, hover and range selection.
        var color = RenderingUtils.decorationColor(
            new HitTarget.Attachment(attachment), element, invariants, frame);

        DecorationContentRenderer.draw(
            g2, decorationLayout.content(), decorationLayout.xSs(), ySs, color);
    }
}
