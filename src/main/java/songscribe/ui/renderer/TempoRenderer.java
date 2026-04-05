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
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.TempoAttachment;

/**
 * Renders tempo change indicators (note = number format).
 * <p>
 * Tempo markings show the beat note and tempo in BPM, e.g., "♩ = 120".
 * They appear above the staff at the specified note position.
 */
public class TempoRenderer extends BaseElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Bravura font scaled for tempo note display. */
    private static final Font TEMPO_NOTE_FONT =
        MUSIC_FONT.deriveFont(FONT_SIZE * TempoAttachment.NOTE_SCALE);

    /** Gap between tempo note glyph and "= NNN" text, in staff-space units. */
    private static final float GLYPH_TEXT_GAP_SS =
        FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_GLYPH_TEXT_GAP);

    // Singleton instance
    private static final TempoRenderer INSTANCE = new TempoRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TempoRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static TempoRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the SMuFL metronome glyph for the given element type,
     * or exits if no mapping exists.
     */
    private static SMuFLGlyph requireMetronomeGlyph(ElementType type) {
        var glyph = TempoAttachment.metronomeGlyphFor(type);

        if (glyph == null) {
            throw RuntimeError.exit("No metronome glyph for element type: " + type);
        }

        return glyph;
    }

    // ==========================================================================
    // Tempo Note Painting
    // ==========================================================================

    /**
     * Paints a tempo note glyph (metronome mark) at the given baseline position.
     * <p>
     * The caller must set the desired Bravura-derived font on {@code g2} before
     * calling. This method draws the metronome glyph and augmentation dot (if
     * the tempo type is dotted), using the font size to scale SMuFL advance
     * widths for dot placement.
     *
     * @param g2        graphics context with a Bravura-derived font already set
     * @param tempoType the tempo type whose note glyph to draw
     * @param x         x coordinate of the glyph origin
     * @param y         y coordinate of the glyph baseline
     */
    public static void paintTempoNote(
        Graphics2D g2,
        Tempo.Type tempoType,
        float x,
        float y
    ) {
        var note = tempoType.getNote();
        var metGlyph = requireMetronomeGlyph(note.getType());

        g2.drawString(metGlyph.asString(), x, y);

        if (note.getDotCount() > 0) {
            float fontScale = g2.getFont().getSize2D() / FONT_SIZE;
            float dotX = x
                + (float) (SMuFLMetadata.getInstance().requireAdvanceWidth(metGlyph) * fontScale);
            g2.drawString(SMuFLGlyph.MET_AUGMENTATION_DOT.asString(), dotX, y);
        }
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var tempo = element.getTempoChange();

        if (tempo == null) {
            return;
        }

        renderTempoChange(g2, tempo, element, ctx);
    }

    /**
     * Renders a tempo change for a note if it has one.
     *
     * @param g2   Graphics context
     * @param note The note
     * @param ctx  Render context
     */
    public void renderTempo(
        Graphics2D g2,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Renders the initial tempo marking (used for first note of first line).
     * <p>
     * This method is for rendering the composition's default tempo, which is
     * stored separately from note tempo changes.
     *
     * @param g2    Graphics context
     * @param note  The note at which to position the tempo
     * @param tempo The tempo to render
     * @param ctx   Render context
     */
    public void renderInitialTempo(
        Graphics2D g2,
        StaffElement note,
        Tempo tempo,
        ElementRenderContext ctx
    ) {
        renderTempoChange(g2, tempo, note, ctx);
    }

    /**
     * Renders the tempo change indicator.
     */
    private void renderTempoChange(
        Graphics2D g2,
        Tempo tempo,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var composition = ctx.getComposition();
        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        // Get position from DecorationLayout (in staff-space units)
        var decorationLayout = getTempoDecorationLayout(note, ctx);
        double ySs = layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        double xSs = decorationLayout.xSs();

        // Build tempo text
        var tempoBuilder = new StringBuilder(25);
        var tempoType = tempo.getTempoType();
        var tempoTypeNote = tempoType.getNote();

        // Compute the text baseline Y: aligned with the bottom of the tempo note glyph
        var metadata = SMuFLMetadata.getInstance();
        double textBaselineYSs = ySs
            + TempoAttachment.QUARTER_NOTE_BBOX.height() * TempoAttachment.NOTE_SCALE;

        if (tempo.shouldShowTempo()) {
            drawTempoChangeNote(g2, tempoType, xSs, ySs);
            tempoBuilder.append("= ");
            tempoBuilder.append(tempo.getVisibleTempo());
            tempoBuilder.append(' ');
        }

        tempoBuilder.append(tempo.getTempoDescription());

        // Scale the attribution font to staff-space units
        var attrFont = composition.getAttributionFont();
        var scale = ScaleContext.getInstance();
        g2.setFont(attrFont.deriveFont((float) scale.fromPixels(attrFont.getSize())));
        g2.setColor(ELEMENT_COLOR);

        if (tempo.shouldShowTempo()) {
            // Advance past the metronome glyph using SMuFL advance widths (scaled)
            var metGlyph = requireMetronomeGlyph(tempoTypeNote.getType());
            xSs += metadata.requireAdvanceWidth(metGlyph) * TempoAttachment.NOTE_SCALE;

            if (tempoTypeNote.getDotCount() > 0) {
                xSs += metadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT)
                    * TempoAttachment.NOTE_SCALE;
            }

            xSs += GLYPH_TEXT_GAP_SS;
        }

        var tempoText = tempoBuilder.toString();
        g2.drawString(tempoText, (float) xSs, (float) textBaselineYSs);
    }

    /**
     * Draws a tempo note at the layout position.
     * {@code ySs} is the top of the decoration area; the glyph origin is offset
     * so the visual top aligns with that position.
     */
    private void drawTempoChangeNote(
        Graphics2D g2,
        Tempo.Type tempoType,
        double xSs,
        double ySs
    ) {
        var metGlyph = requireMetronomeGlyph(tempoType.getNote().getType());

        // Convert layout top Y to glyph origin Y (baseline), accounting for font scale
        var bbox = SMuFLMetadata.getInstance().requireBBox(metGlyph);
        double originYSs = ySs - bbox.top() * TempoAttachment.NOTE_SCALE;

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(ELEMENT_COLOR);
            g2.setFont(TEMPO_NOTE_FONT);
            paintTempoNote(g2, tempoType, (float) xSs, (float) originYSs);
        }
    }

    /**
     * Gets the Y position for tempo change from layout result.
     * <p>
     * Reads the {@link songscribe.ui.layout.LayoutResult.DecorationLayout} written
     * by the vertical stacking calculator. Converts from layout coordinates
     * (relative to middleLineY=0) to component coordinates.
     */
    private LayoutResult.DecorationLayout getTempoDecorationLayout(
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var decorationLayout = layoutResult.findAttachmentDecorationLayout(
            note, TempoAttachment.class);

        if (decorationLayout == null) {
            throw new IllegalStateException("No DecorationLayout found for TempoAttachment on note");
        }

        return decorationLayout;
    }
}
