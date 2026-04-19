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

import module java.desktop;

import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.ui.layout.MetronomeAttachment;
import songscribe.ui.layout.TempoAttachment;

/**
 * Renders tempo change indicators (note = number format).
 * <p>
 * Tempo markings show the beat note and tempo in BPM, e.g., "♩ = 120".
 * They appear above the staff at the specified note position.
 */
public class TempoRenderer extends MetronomeRenderer {

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
        var setup = buildRenderSetup(note, TempoAttachment.class, ctx);
        double xSs = setup.decorationLayout().xSs();
        double textBaselineYSs = setup.ySs() + MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS;
        var tempoBuilder = new StringBuilder(25);
        var tempoType = tempo.getTempoType();

        if (tempo.shouldShowTempo()) {
            tempoBuilder.append(tempo.getVisibleTempo());
            tempoBuilder.append(' ');
        }

        tempoBuilder.append(tempo.getTempoDescription());

        if (tempo.shouldShowTempo()) {
            xSs = drawDurationEquals(g2, tempoType, xSs, setup.ySs(), setup.attrFont(), setup.color());
        }

        g2.setFont(scaleAttrFont(setup.attrFont()));
        g2.setColor(setup.color());
        g2.drawString(tempoBuilder.toString(), (float) xSs, (float) textBaselineYSs);
    }

}
