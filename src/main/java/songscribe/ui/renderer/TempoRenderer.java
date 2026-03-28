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

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

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

    /**
     * Maps note types to their SMuFL metronome glyph (stem-up, since tempo notes
     * always display stem-up).
     */
    private static final Map<ElementType, SMuFLGlyph> METRONOME_GLYPHS;

    /** Bravura font scaled for tempo note display. */
    private static final Font TEMPO_NOTE_FONT;

    /** Gap between tempo note glyph and "= NNN" text, in staff-space units. */
    private static final float GLYPH_TEXT_GAP_SS;

    static {
        METRONOME_GLYPHS = new EnumMap<>(ElementType.class);
        METRONOME_GLYPHS.put(ElementType.SEMIBREVE, SMuFLGlyph.MET_NOTE_WHOLE);
        METRONOME_GLYPHS.put(ElementType.MINIM, SMuFLGlyph.MET_NOTE_HALF_UP);
        METRONOME_GLYPHS.put(ElementType.CROTCHET, SMuFLGlyph.MET_NOTE_QUARTER_UP);
        METRONOME_GLYPHS.put(ElementType.QUAVER, SMuFLGlyph.MET_NOTE_8TH_UP);
        METRONOME_GLYPHS.put(ElementType.SEMIQUAVER, SMuFLGlyph.MET_NOTE_16TH_UP);
        METRONOME_GLYPHS.put(ElementType.DEMI_SEMIQUAVER, SMuFLGlyph.MET_NOTE_32ND_UP);

        TEMPO_NOTE_FONT = BRAVURA_FONT.deriveFont(FONT_SIZE * TempoAttachment.NOTE_SCALE);
        GLYPH_TEXT_GAP_SS = FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_GLYPH_TEXT_GAP);
    }

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

    // ==========================================================================
    // Tempo Note Bounds Calculation
    // ==========================================================================

    /**
     * Calculate the bounding box for a tempo note in the local coordinate
     * system (before scaling and translation). Uses the pre-composed SMuFL
     * metronome glyph which includes notehead, stem, and flag in one codepoint.
     *
     * @param frc  the font render context
     * @param font the Bravura font (unused, kept for API compatibility)
     * @param note the tempo note
     * @return the bounding rectangle, or null if the note type has no glyph
     */
    @Nullable
    public static Rectangle2D getTempoNoteBounds(
        FontRenderContext frc,
        Font font,
        StaffElement note
    ) {
        var metGlyph = METRONOME_GLYPHS.get(note.getType());

        if (metGlyph == null) {
            return null;
        }

        // The metronome glyph includes head + stem + flag as a single codepoint
        var gv = BRAVURA_FONT.createGlyphVector(frc, metGlyph.asString());
        var bounds = new Rectangle2D.Double();
        bounds.setRect(gv.getVisualBounds());

        // Add dot bounds if note has dots
        if (note.getDotCount() > 0) {
            var dotGv = BRAVURA_FONT.createGlyphVector(frc, SMuFLGlyph.MET_AUGMENTATION_DOT.asString());
            var dotBounds = dotGv.getVisualBounds();
            var dotX = bounds.getMaxX() + 2;
            bounds.add(new Rectangle2D.Double(
                dotX,
                dotBounds.getY(),
                dotBounds.getWidth() * note.getDotCount(),
                dotBounds.getHeight()
            ));
        }

        return bounds;
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
        var tempoTypeNote = tempo.getTempoType().getNote();

        // Compute the text baseline Y: aligned with the bottom of the tempo note glyph
        var metadata = SMuFLMetadata.getInstance();
        double textBaselineYSs = ySs
            + TempoAttachment.QUARTER_NOTE_BBOX.height() * TempoAttachment.NOTE_SCALE;

        if (tempo.shouldShowTempo()) {
            drawTempoChangeNote(g2, tempoTypeNote, xSs, ySs);
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
            var metGlyph = METRONOME_GLYPHS.get(tempoTypeNote.getType());

            if (metGlyph != null) {
                xSs += metadata.requireAdvanceWidth(metGlyph) * TempoAttachment.NOTE_SCALE;

                if (tempoTypeNote.getDotCount() > 0) {
                    xSs += metadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT)
                        * TempoAttachment.NOTE_SCALE;
                }

                xSs += GLYPH_TEXT_GAP_SS;
            }
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
        StaffElement tempoNote,
        double xSs,
        double ySs
    ) {
        var metGlyph = METRONOME_GLYPHS.get(tempoNote.getType());

        if (metGlyph == null) {
            return;
        }

        // Convert layout top Y to glyph origin Y (baseline), accounting for font scale
        var bbox = SMuFLMetadata.getInstance().requireBBox(metGlyph);
        double originYSs = ySs - bbox.top() * TempoAttachment.NOTE_SCALE;

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(ELEMENT_COLOR);
            g2.setFont(TEMPO_NOTE_FONT);
            g2.drawString(metGlyph.asString(), (float) xSs, (float) originYSs);

            if (tempoNote.getDotCount() > 0) {
                double dotX = xSs + SMuFLMetadata.getInstance()
                    .requireAdvanceWidth(metGlyph) * TempoAttachment.NOTE_SCALE;
                g2.drawString(SMuFLGlyph.MET_AUGMENTATION_DOT.asString(),
                    (float) dotX, (float) originYSs);
            }
        }
    }

    /**
     * Paints a simple note for tempo display using a pre-composed SMuFL
     * metronome glyph (notehead + stem + flag in a single codepoint).
     */

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
