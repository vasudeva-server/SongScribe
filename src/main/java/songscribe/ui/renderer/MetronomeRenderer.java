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

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

import module java.desktop;

import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.MetronomeContent;
import songscribe.dom.MetronomeAttachment;
import songscribe.util.GraphicsState;

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
                    case MetronomeContent.GlyphItem glyphItem -> {
                        g2.setFont(TEMPO_NOTE_FONT);
                        text = glyphItem.glyph().asString();
                    }
                    case MetronomeContent.TextItem textItem -> {
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
        var content = decorationLayout.requireContent();
        var ySs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

        // A metronome marking is selectable in its own right, so its selection is folded into
        // the color resolved from the owner note rather than applied over it: an unselected
        // marking still follows its note through playback, hover and range selection.
        var color = RenderingUtils.decorationColor(
            new HitTarget.Attachment(attachment), element, invariants, frame);

        drawContent(g2, content, decorationLayout.xSs(), ySs, color);
    }

    /**
     * Looks up the decoration layout for the given attachment type on the given note.
     * Throws {@link IllegalStateException} if not found.
     */
    protected LayoutResult.DecorationLayout requireDecorationLayout(
        StaffElement note,
        Class<? extends MetronomeAttachment> attachmentClass,
        LineInvariants invariants
    ) {
        var decorationLayout = invariants.getLayoutResult().findAttachmentDecorationLayout(
            note, attachmentClass);

        if (decorationLayout == null) {
            throw new IllegalStateException(
                "No DecorationLayout found for " + attachmentClass.getSimpleName() + " on note");
        }

        return decorationLayout;
    }
}
