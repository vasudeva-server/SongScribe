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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.smufl.SMuFLGlyph;
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

    private static final double TEMPO_CHANGE_ZOOM_X = BaseElementRenderer.TEMPO_CHANGE_ZOOM_X;
    private static final double TEMPO_CHANGE_ZOOM_Y = BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y;

    /**
     * Maps note types to their SMuFL metronome glyph (stem-up, since tempo notes
     * always display stem-up).
     */
    private static final Map<ElementType, SMuFLGlyph> METRONOME_GLYPHS;

    static {
        METRONOME_GLYPHS = new EnumMap<>(ElementType.class);
        METRONOME_GLYPHS.put(ElementType.SEMIBREVE, SMuFLGlyph.MET_NOTE_WHOLE);
        METRONOME_GLYPHS.put(ElementType.MINIM, SMuFLGlyph.MET_NOTE_HALF_UP);
        METRONOME_GLYPHS.put(ElementType.CROTCHET, SMuFLGlyph.MET_NOTE_QUARTER_UP);
        METRONOME_GLYPHS.put(ElementType.QUAVER, SMuFLGlyph.MET_NOTE_8TH_UP);
        METRONOME_GLYPHS.put(ElementType.SEMIQUAVER, SMuFLGlyph.MET_NOTE_16TH_UP);
        METRONOME_GLYPHS.put(ElementType.DEMI_SEMIQUAVER, SMuFLGlyph.MET_NOTE_32ND_UP);
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
    public static @NotNull TempoRenderer getInstance() {
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
        @NotNull StaffElement element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
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
        @NotNull Graphics2D g2,
        @NotNull StaffElement note,
        @NotNull ElementRenderContext ctx
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
        @NotNull Graphics2D g2,
        @NotNull StaffElement note,
        @NotNull Tempo tempo,
        @NotNull ElementRenderContext ctx
    ) {
        renderTempoChange(g2, tempo, note, ctx);
    }

    /**
     * Renders the tempo change indicator.
     */
    private void renderTempoChange(
        @NotNull Graphics2D g2,
        @NotNull Tempo tempo,
        @NotNull StaffElement note,
        @NotNull ElementRenderContext ctx
    ) {
        var composition = ctx.getComposition();
        var line = ctx.getCurrentLine();
        int lineIndex = ctx.getLineIndex();

        if (line == null) {
            return;
        }

        double middleLineYSs = ctx.getMiddleLineYSs();

        // Calculate Y position (above the staff)
        int yPosSs = getEffectiveTempoChangeYPosSs(note, ctx);

        // Build tempo text
        var tempoBuilder = new StringBuilder(25);
        var tempoTypeNote = tempo.getTempoType().getNote();

        double xPosSs = note.getXPosSs();

        if (tempo.shouldShowTempo()) {
            drawTempoChangeNote(g2, tempoTypeNote, (int) xPosSs, yPosSs);
            tempoBuilder.append("= ");
            tempoBuilder.append(tempo.getVisibleTempo());
            tempoBuilder.append(' ');
        }

        tempoBuilder.append(tempo.getTempoDescription());

        g2.setFont(composition.getAttributionFont());
        g2.setColor(ELEMENT_COLOR);

        if (tempo.shouldShowTempo()) {
            // Compute the scaled width of the metronome glyph to position the text after it
            var metGlyph = METRONOME_GLYPHS.get(tempoTypeNote.getType());

            if (metGlyph != null) {
                var frc = g2.getFontRenderContext();
                var gv = BRAVURA_FONT.createGlyphVector(frc, metGlyph.asString());
                double glyphWidth = gv.getVisualBounds().getWidth() * TEMPO_CHANGE_ZOOM_X;
                xPosSs += glyphWidth + 3;

                if (tempoTypeNote.getDotCount() > 0) {
                    xPosSs += 4;
                }
            }
        }

        var tempoText = tempoBuilder.toString();
        g2.drawString(tempoText, (float) xPosSs, (float) yPosSs);
    }

    /**
     * Draws a tempo note (scaled smaller than regular notes).
     */
    private void drawTempoChangeNote(
        @NotNull Graphics2D g2,
        @NotNull StaffElement tempoNote,
        int x,
        int y
    ) {
        var transform = g2.getTransform();

        g2.translate(x, y - ((FONT_SIZE * TEMPO_CHANGE_ZOOM_Y) / 8.0));
        g2.scale(TEMPO_CHANGE_ZOOM_X, TEMPO_CHANGE_ZOOM_Y);

        paintSimpleTempoNote(g2, tempoNote);

        g2.setTransform(transform);
    }

    /**
     * Paints a simple note for tempo display using a pre-composed SMuFL
     * metronome glyph (notehead + stem + flag in a single codepoint).
     */
    private void paintSimpleTempoNote(@NotNull Graphics2D g2, @NotNull StaffElement note) {
        var metGlyph = METRONOME_GLYPHS.get(note.getType());

        if (metGlyph == null) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(ELEMENT_COLOR);
            g2.setFont(BRAVURA_FONT);
            g2.drawString(metGlyph.asString(), 0f, 0f);

            // Draw augmentation dot using the SMuFL metronome dot glyph
            if (note.getDotCount() > 0) {
                var frc = g2.getFontRenderContext();
                var noteGv = BRAVURA_FONT.createGlyphVector(frc, metGlyph.asString());
                float dotX = (float) noteGv.getVisualBounds().getMaxX() + 2f;
                g2.drawString(SMuFLGlyph.MET_AUGMENTATION_DOT.asString(), dotX, 0f);
            }
        }
    }

    /**
     * Gets the Y position for tempo change from layout result.
     * <p>
     * Converts from layout coordinates (relative to middleLineY=0) to
     * component coordinates (relative to component top).
     */
    private int getEffectiveTempoChangeYPosSs(
        @NotNull StaffElement note,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.findAttachmentBounds(note, TempoAttachment.class);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for TempoAttachment on note");
        }

        // Convert from layout coordinates to component coordinates
        int componentY = (int) layoutYToComponentYSs(bounds, ctx);

        return componentY;
    }
}
