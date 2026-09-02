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

/**
 * Abstract base renderer for metronome-style markings (tempo and beat change).
 * <p>
 * Provides the shared font constant, the content-painting primitive
 * {@link #drawContent}, and decoration layout lookup used by
 * {@link TempoChangeRenderer} and {@link BeatChangeRenderer}.
 */
public abstract class MetronomeRenderer implements ElementRenderer<StaffElement> {

    /** Bravura font scaled for metronome glyph display. */
    protected static final Font TEMPO_NOTE_FONT = RenderingUtils.getMusicFont().deriveFont(RenderingUtils.FONT_SIZE * MetronomeAttachment.NOTE_SCALE);

    /**
     * Draws one metronome marking's positioned content at the given position.
     * <p>
     * The whole marking — glyphs, "=", BPM and description — was typeset by
     * {@link MetronomeContent} at layout time, so this walks the items and decides
     * nothing. It measures no advance, resolves no font and computes no position on
     * either axis; every item carries its own. That is the point of the method, not an
     * incidental property.
     * <p>
     * Deciding anything here would reintroduce the possibility of the drawn ink
     * disagreeing with the measured box that layout, hit testing and vertical stacking
     * all rely on — and measuring specifically would put a {@code TextLayout}
     * construction back into the paint loop, for every marking on screen, on every
     * scroll and every zoom step.
     *
     * @param g2      graphics context
     * @param content the positioned content to paint
     * @param xSs     left edge of the content (staff spaces)
     * @param ySs     decoration-layout top Y position (staff spaces)
     * @param color   the color for both the glyphs and the text
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

    /**
     * Draws the marking owned by the given attachment: the shared render sequence for
     * every metronome-style decoration that hangs off a note.
     *
     * @param element    the note owning the attachment
     * @param attachment the attachment being drawn
     */
    protected void renderAttachment(
        StaffElement element,
        MetronomeAttachment attachment,
        LineInvariants invariants,
        ElementFrame frame,
        Graphics2D g2
    ) {
        var decorationLayout = requireDecorationLayout(element, attachment.getClass(), invariants);
        var ySs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

        // A metronome marking is selectable in its own right, so its selection is folded into
        // the color resolved from the owner note rather than applied over it: an unselected
        // marking still follows its note through playback, hover and range selection.
        var color = RenderingUtils.decorationColor(
            new HitTarget.Attachment(attachment), element, invariants, frame);

        DecorationContentRenderer.draw(
            g2, decorationLayout.content(), decorationLayout.xSs(), ySs, color);
    }

    /**
     * Looks up the typeset decoration layout for the given attachment type on the given note.
     * <p>
     * The decoration map is keyed by element, so what comes back is untyped; a metronome marking's
     * layout always carries its content, because the stacker typesets it before the layout exists.
     * Anything else here is a layout bug, and drawing nothing would hide exactly the class of
     * failure that carrying the content exists to prevent.
     *
     * @throws IllegalStateException if the note carries no typeset layout for that attachment
     */
    protected LayoutResult.DecorationLayout.Typeset requireDecorationLayout(
        StaffElement note,
        Class<? extends MetronomeAttachment> attachmentClass,
        LineInvariants invariants
    ) {
        var decorationLayout = invariants.getLayoutResult().findAttachmentDecorationLayout(
            note, attachmentClass);

        if (!(decorationLayout instanceof LayoutResult.DecorationLayout.Typeset typeset)) {
            throw new IllegalStateException(
                "No typeset DecorationLayout found for " + attachmentClass.getSimpleName()
                    + " on note");
        }

        return typeset;
    }
}
