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
 * The one place a {@link DecorationContent} is told apart from its siblings, so no renderer
 * casts. The switch has no {@code default} arm, so a new kind of content fails to compile here
 * rather than silently drawing nothing.
 */
final class DecorationContentRenderer {

    private DecorationContentRenderer() {
    }

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
