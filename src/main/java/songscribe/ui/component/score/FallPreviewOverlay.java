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
 * The fall-tool hover preview: the trailing {@code brassFallLipShort} glyph shown one
 * {@link songscribe.layout.NoteGeometry#FALL_GAP_SS} past a source note's column, drawn by
 * {@link SlideRenderer#renderPreviewFall}.
 * <p>
 * {@code renderPreviewFall} resolves only a source note context — a fall has no target element —
 * so this overlay's ink is a constant size that changes only with zoom; only its anchor moves as
 * the source note changes.
 */
public final class FallPreviewOverlay extends SlidePreviewOverlay {

    FallPreviewOverlay(OverlayHost host) {
        super(host);
    }

    @Override
    protected SlideZone getZone() {
        return SlideZone.FALL;
    }

    @Override
    protected void recordSlide(
        Graphics2D g2, Line domLine, int sourceIndex, LineInvariants invariants
    ) {
        SlideRenderer.getInstance().renderPreviewFall(g2, sourceIndex, domLine, invariants);
    }
}
