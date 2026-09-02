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
import java.awt.Graphics2D;

import songscribe.layout.AttributionContent;
import songscribe.layout.DecorationContent;
import songscribe.layout.MetronomeContent;

/**
 * The one place a {@link DecorationContent} is told apart from its siblings.
 * <p>
 * A decoration's renderer knows which element it draws but not, from the element's layout alone,
 * which kind of content that layout carries — the decoration map is keyed by element, so the
 * content comes back as the sealed interface. Routing every such draw through here means no
 * renderer casts: each arm hands the content on to the renderer that owns that kind, already
 * typed. The switch has no {@code default} arm, so a third kind of content fails to compile here
 * rather than reaching a runtime branch that silently draws nothing.
 */
final class DecorationContentRenderer {

    private DecorationContentRenderer() {
    }

    /**
     * Draws {@code content} with its top-left corner at the given position, inside the staff-space
     * transform.
     *
     * @param xSs   left edge of the content, in staff spaces
     * @param ySs   top edge of the content in component space, in staff spaces
     * @param color the color the whole decoration draws in
     */
    static void draw(
        Graphics2D g2,
        DecorationContent content,
        double xSs,
        double ySs,
        Color color) {

        switch (content) {
            case MetronomeContent metronome ->
                MetronomeRenderer.drawContent(g2, metronome, xSs, ySs, color);
            case AttributionContent attribution ->
                AttributionRenderer.drawContent(g2, attribution, xSs, ySs, color);
        }
    }
}
