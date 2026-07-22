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

package songscribe.ui.component.score;

import module java.desktop;

import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.renderer.SlideRenderer;

/**
 * The glissando-tool hover preview: the connecting line between a source note and the note
 * immediately following it, drawn by {@link SlideRenderer#renderPreviewGlissando}.
 * <p>
 * {@code renderPreviewGlissando} resolves both a source and a target note context and draws
 * between their resolved attach points, so unlike {@link FallPreviewOverlay} this overlay's ink
 * varies in length and angle. Both endpoints are anchored to note positions rather than the
 * mouse, so the shape snaps when the integer {@code xIndex} changes — discrete, not
 * continuous — rather than tracking the mouse continuously.
 */
public final class GlissandoPreviewOverlay extends SlidePreviewOverlay {

    GlissandoPreviewOverlay(OverlayHost host) {
        super(host);
    }

    @Override
    protected SlideZone getZone() {
        return SlideZone.GLISSANDO;
    }

    @Override
    protected void recordSlide(
        Graphics2D g2, Line domLine, int sourceIndex, LineInvariants invariants
    ) {
        SlideRenderer.getInstance().renderPreviewGlissando(g2, sourceIndex, domLine, invariants);
    }
}
