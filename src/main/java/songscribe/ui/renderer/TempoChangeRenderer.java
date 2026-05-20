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

import songscribe.model.StaffElement;
import songscribe.model.Tempo;
import songscribe.ui.layout.MetronomeAttachment;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.TempoChangeAttachment;

/** Renders tempo change indicators (note = number format, e.g. "♩ = 120"). */
public final class TempoChangeRenderer extends MetronomeRenderer {

    private static final TempoChangeRenderer INSTANCE = new TempoChangeRenderer();

    private TempoChangeRenderer() {
    }

    public static TempoChangeRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);

        if (attachment == null) {
            return;
        }

        renderTempoChange(g2, attachment.getTempo(), element, ctx);
    }

    /** Renders the song's initial tempo, stored separately from per-note tempo changes. */
    public void renderInitialTempo(
        Graphics2D g2,
        StaffElement note,
        Tempo tempo,
        ElementRenderContext ctx
    ) {
        renderTempoChange(g2, tempo, note, ctx);
    }

    private void renderTempoChange(
        Graphics2D g2,
        Tempo tempo,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var setup = buildRenderSetup(note, TempoChangeAttachment.class, ctx);
        var xSs = setup.decorationLayout().xSs();
        var textBaselineYSs = setup.ySs() + MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS;
        var tempoBuilder = new StringBuilder(25);
        var showTempo = tempo.shouldShowTempo();

        if (showTempo) {
            tempoBuilder.append(tempo.getVisibleTempo());
            tempoBuilder.append(' ');
        }

        tempoBuilder.append(tempo.getTempoDescription());

        if (showTempo) {
            xSs = drawDurationEquals(g2, tempo.getTempoType(), xSs, setup.ySs(), setup.attrFont(), setup.color());
        }

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(ScaleContext.scaleFont(setup.attrFont()));
            g2.setColor(setup.color());
            g2.drawString(tempoBuilder.toString(), (float) xSs, (float) textBaselineYSs);
        }
    }

}
