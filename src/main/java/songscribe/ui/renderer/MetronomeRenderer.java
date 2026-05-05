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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;

import module java.desktop;

import songscribe.error.RuntimeError;
import songscribe.music.Duration;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.MetronomeAttachment;
import songscribe.ui.layout.ScaleContext;

/**
 * Abstract base renderer for metronome-style markings (tempo and beat change).
 * <p>
 * Provides the shared font constant, glyph drawing primitives, and decoration
 * layout lookup used by {@link TempoChangeRenderer} and {@link BeatChangeRenderer}.
 */
public abstract class MetronomeRenderer extends BaseElementRenderer<StaffElement> {

    /** Bravura font scaled for metronome glyph display. */
    protected static final Font TEMPO_NOTE_FONT;

    static {
        TEMPO_NOTE_FONT = getMusicFont().deriveFont(FONT_SIZE * MetronomeAttachment.NOTE_SCALE);
    }

    /**
     * Returns the SMuFL metronome glyph for the given element type,
     * or exits if no mapping exists.
     */
    protected static SMuFLGlyph requireMetronomeGlyph(ElementType type) {
        var glyph = MetronomeAttachment.metronomeGlyphFor(type);

        if (glyph == null) {
            throw RuntimeError.exit("No metronome glyph for element type: " + type);
        }

        return glyph;
    }

    /**
     * Shared rendering setup for metronome-style decorations.
     *
     * @param color            decoration color
     * @param decorationLayout layout result for this decoration
     * @param ySs              top Y of the decoration in component staff-space coordinates
     * @param attrFont         attribution font (unscaled, pixel units)
     */
    protected record RenderSetup(
        Color color,
        LayoutResult.DecorationLayout decorationLayout,
        double ySs,
        Font attrFont
    ) {}

    /**
     * Builds the shared render setup for a metronome-style decoration.
     * Exits if the current line is absent (invariant violation).
     */
    protected RenderSetup buildRenderSetup(
        StaffElement element,
        Class<? extends MetronomeAttachment> attachmentClass,
        ElementRenderContext ctx
    ) {
        if (ctx.getCurrentLine() == null) {
            throw RuntimeError.exit("No current line for metronome decoration on " + element);
        }

        var color = getDecorationColor(element, ctx);
        var decorationLayout = requireDecorationLayout(element, attachmentClass, ctx);
        var ySs = layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        var attrFont = ctx.getSong().getAnnotationFont();

        return new RenderSetup(color, decorationLayout, ySs, attrFont);
    }

    /**
     * Looks up the decoration layout for the given attachment type on the given note.
     * Throws {@link IllegalStateException} if not found.
     */
    protected LayoutResult.DecorationLayout requireDecorationLayout(
        StaffElement note,
        Class<? extends MetronomeAttachment> attachmentClass,
        ElementRenderContext ctx
    ) {
        var decorationLayout = ctx.getLayoutResult().findAttachmentDecorationLayout(
            note, attachmentClass);

        if (decorationLayout == null) {
            throw new IllegalStateException(
                "No DecorationLayout found for " + attachmentClass.getSimpleName() + " on note");
        }

        return decorationLayout;
    }

    /**
     * Draws a duration glyph followed by "=" using SMuFL metronome glyphs and the
     * supplied attribution font. Returns the x position (staff spaces) after the
     * "=" and its trailing gap, so the caller can append whatever follows.
     *
     * @param g2       graphics context
     * @param duration the duration whose metronome glyph to draw
     * @param xSs      starting X position (staff spaces)
     * @param ySs      decoration-layout top Y position (staff spaces)
     * @param attrFont the attribution font (unscaled, in pixel units)
     * @param color    the color for both glyph and "=" text
     * @return the new X position (staff spaces) after the "=" and trailing gap
     */
    protected double drawDurationEquals(
        Graphics2D g2,
        Duration duration,
        double xSs,
        double ySs,
        Font attrFont,
        Color color
    ) {
        var metadata = SMuFLMetadata.getInstance();
        var note = duration.getNote();
        var metGlyph = requireMetronomeGlyph(note.getType());
        var glyphOriginYSs = ySs - metadata.requireBBox(metGlyph).top() * MetronomeAttachment.NOTE_SCALE;
        var textBaselineYSs = ySs + MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS;

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(color);

            g2.setFont(TEMPO_NOTE_FONT);
            g2.drawString(metGlyph.asString(), (float) xSs, (float) glyphOriginYSs);
            xSs += metadata.requireAdvanceWidth(metGlyph) * MetronomeAttachment.NOTE_SCALE;
            var dotAdvanceSs = MetronomeAttachment.dotAdvanceWidthSs(metadata);
            xSs += dotAdvanceSs;

            if (note.getDotCount() > 0) {
                g2.drawString(
                    SMuFLGlyph.MET_AUGMENTATION_DOT.asString(),
                    (float) xSs,
                    (float) glyphOriginYSs);
                xSs += dotAdvanceSs;
            }

            var scale = ScaleContext.getInstance();
            g2.setFont(scale.scaleFont(attrFont));
            g2.drawString(MetronomeAttachment.EQUALS_STR, (float) xSs, (float) textBaselineYSs);

            var equalsWidthSs = scale.fromPixels(
                attrFont.getStringBounds(MetronomeAttachment.EQUALS_STR, g2.getFontRenderContext()).getWidth());
            xSs += equalsWidthSs;
        }

        return xSs;
    }

    /**
     * Draws a single metronome note glyph with optional augmentation dot.
     *
     * @param g2       graphics context
     * @param duration the duration whose metronome glyph to draw
     * @param xSs      starting X position (staff spaces)
     * @param ySs      decoration-layout top Y position (staff spaces)
     * @param color    the color for the glyph
     */
    protected void drawDurationGlyph(
        Graphics2D g2,
        Duration duration,
        double xSs,
        double ySs,
        Color color
    ) {
        var metadata = SMuFLMetadata.getInstance();
        var note = duration.getNote();
        var metGlyph = requireMetronomeGlyph(note.getType());
        var glyphOriginYSs = ySs - metadata.requireBBox(metGlyph).top() * MetronomeAttachment.NOTE_SCALE;

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(color);
            g2.setFont(TEMPO_NOTE_FONT);
            g2.drawString(metGlyph.asString(), (float) xSs, (float) glyphOriginYSs);

            if (note.getDotCount() > 0) {
                xSs += metadata.requireAdvanceWidth(metGlyph) * MetronomeAttachment.NOTE_SCALE;
                xSs += MetronomeAttachment.dotAdvanceWidthSs(metadata);
                g2.drawString(
                    SMuFLGlyph.MET_AUGMENTATION_DOT.asString(),
                    (float) xSs,
                    (float) glyphOriginYSs);
            }
        }
    }
}
