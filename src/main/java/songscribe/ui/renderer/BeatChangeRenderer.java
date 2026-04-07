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
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import module java.desktop;


import songscribe.music.BeatChange;
import songscribe.music.StaffElement;
import songscribe.ui.layout.BeatChangeAttachment;

/**
 * Renders beat change indicators (note = note format).
 * <p>
 * Beat changes show equivalence between two note values, e.g., "♩ = ♪."
 * indicating that the quarter note of the old tempo equals the dotted
 * eighth note of the new tempo.
 */
public class BeatChangeRenderer extends BaseElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.FONT_SIZE;
    private static final double TEMPO_CHANGE_ZOOM_X = BaseElementRenderer.TEMPO_CHANGE_ZOOM_X;
    private static final double TEMPO_CHANGE_ZOOM_Y = BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y;
    private static final double CROTCHET_WIDTH_PX = NOTE_FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final BeatChangeRenderer INSTANCE = new BeatChangeRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private BeatChangeRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static BeatChangeRenderer getInstance() {
        return INSTANCE;
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
        var beatChange = element.getBeatChange();

        if (beatChange == null) {
            return;
        }

        renderBeatChange(g2, beatChange, element, ctx);
    }

    /**
     * Renders a beat change for a note if it has one.
     *
     * @param g2   Graphics context
     * @param note The note
     * @param ctx  Render context
     */
    public void renderBeatChange(
        Graphics2D g2,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Renders the beat change indicator.
     */
    private void renderBeatChange(
        Graphics2D g2,
        BeatChange beatChange,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var composition = ctx.getComposition();
        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        var color = getDecorationColor(note, ctx);
        int xPosSs = note.getXPosSs();
        int yPosPx = getEffectiveBeatChangeYPosPx(note, ctx);

        drawBeatChange(g2, beatChange, xPosSs, yPosPx, composition, color);
    }

    /**
     * Gets the Y position for beat change from layout result.
     * <p>
     * Reads the {@link songscribe.ui.layout.LayoutResult.DecorationLayout} written
     * by the vertical stacking calculator. Converts from layout coordinates
     * (relative to middleLineY=0) to component coordinates.
     */
    private int getEffectiveBeatChangeYPosPx(
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var decorationLayout = layoutResult.findAttachmentDecorationLayout(
            note, BeatChangeAttachment.class);

        if (decorationLayout == null) {
            throw new IllegalStateException("No DecorationLayout found for BeatChangeAttachment on note");
        }

        return (int) layoutYToComponentYSs(decorationLayout.ySs(), ctx);
    }

    /**
     * Draws a beat change at a specific position.
     *
     * Uses {@link #ELEMENT_COLOR} (black) because this entry point is used by
     * export renderers and other non-interactive contexts where selection
     * coloring does not apply.
     *
     * @param g2         Graphics context
     * @param beatChange The beat change data
     * @param xPosSs      X position (staff spaces)
     * @param yPosPx      Y position (pixels)
     * @param composition The composition (for font access)
     */
    public void drawBeatChange(
        Graphics2D g2,
        BeatChange beatChange,
        int xPosSs,
        int yPosPx,
        songscribe.music.Composition composition
    ) {
        drawBeatChange(g2, beatChange, xPosSs, yPosPx, composition, ELEMENT_COLOR);
    }

    private void drawBeatChange(
        Graphics2D g2,
        BeatChange beatChange,
        int xPosSs,
        int yPosPx,
        songscribe.music.Composition composition,
        Color color
    ) {
        drawTempoChangeNote(g2, beatChange.getFirstElement(), xPosSs, yPosPx, color);
        g2.setFont(composition.getAttributionFont());
        g2.setColor(color);
        int eqXPosSs = xPosSs + (int) CROTCHET_WIDTH_PX + 7;
        g2.drawString("=", (float) eqXPosSs, yPosPx);
        drawTempoChangeNote(g2, beatChange.getSecondElement(), (int) Math.round(eqXPosSs + 12), yPosPx, color);
    }

    /**
     * Draws a tempo note (scaled smaller than regular notes).
     */
    private void drawTempoChangeNote(
        Graphics2D g2,
        StaffElement tempoNote,
        int x,
        int y,
        Color color
    ) {
        try (var ignored = GraphicsState.save(g2, FONT, TRANSFORM)) {
            g2.setFont(MUSIC_FONT);

            g2.translate(x, y - ((NOTE_FONT_SIZE * TEMPO_CHANGE_ZOOM_Y) / 8.0));
            g2.scale(TEMPO_CHANGE_ZOOM_X, TEMPO_CHANGE_ZOOM_Y);

            // Draw note using simple rendering
            paintSimpleTempoNote(g2, tempoNote, color);
        }
    }

    /**
     * Paints a simple note for tempo display (no accidentals, ledger lines, etc.).
     */
    private void paintSimpleTempoNote(Graphics2D g2, StaffElement note, Color color) {
        var noteType = note.getType();
        String headChar = NoteRenderer.getNoteHeadChar(noteType);

        if (headChar == null) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            g2.drawString(headChar, 0f, 0f);

            // Draw stem if needed (tempo notes have stems up)
            if (noteType.isNoteWithStem()) {
                float stemX = (float) (NOTE_FONT_SIZE / 3.6056337d);
                float stemY1 = -NOTE_FONT_SIZE / 32f;
                float stemY2 = -NOTE_FONT_SIZE / 1.1429f + 2;

                g2.drawLine((int) stemX, (int) stemY1, (int) stemX, (int) stemY2);

                // Draw flags for 8th notes and smaller using SMuFL glyphs
                var flagGlyph = noteType.getFlagGlyph(true);

                if (flagGlyph != null) {
                    float flagX = (float) Math.round(NOTE_FONT_SIZE / 3.6834533d);
                    float flagY = (float) Math.round(-NOTE_FONT_SIZE / 1.6623377d + 2);
                    g2.drawString(flagGlyph.asString(), flagX, flagY);
                }
            }

            // Draw dots
            if (note.getDotCount() > 0) {
                double dotWidth = NOTE_FONT_SIZE / 9.142858d;
                g2.fillOval((int) 13.1d, (int) (-dotWidth / 2), (int) dotWidth, (int) dotWidth);
            }
        }
    }

}
