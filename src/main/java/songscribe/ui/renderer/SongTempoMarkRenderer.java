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

import songscribe.dom.SongTempoMark;

/**
 * Draws the song's tempo at the right edge of the first line's staff header.
 * <p>
 * The mark is inert: it owns no note, registers no hit region, and is edited only through
 * Song Settings. There is therefore no {@code HitTarget} to resolve a color from and it can never
 * be selected, hovered or played back, so it always paints in
 * {@link RenderingUtils#ELEMENT_COLOR}.
 * <p>
 * It draws through the same {@link MetronomeRenderer#drawContent} core as every per-note tempo
 * change, inside the staff-space transform, so the song's tempo cannot look different from its
 * own siblings.
 */
public final class SongTempoMarkRenderer implements ElementRenderer<SongTempoMark> {

    /** Singleton instance. */
    private static final SongTempoMarkRenderer INSTANCE = new SongTempoMarkRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private SongTempoMarkRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static SongTempoMarkRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        SongTempoMark element,
        Graphics2D g2
    ) {
        var decorationLayout = invariants.getLayoutResult().getDecorationLayout(element);

        // Absent means the mark was never stacked — a line other than the first, or a tempo with
        // nothing to draw.
        if (decorationLayout == null) {
            return;
        }

        // Present but contentless is a layout bug, not a mark with nothing to draw — that case
        // is an absent layout, handled above. Drawing nothing here would hide it.
        var content = decorationLayout.requireContent();
        var ySs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

        MetronomeRenderer.drawContent(
            g2, content, decorationLayout.xSs(), ySs, RenderingUtils.ELEMENT_COLOR);
    }
}
