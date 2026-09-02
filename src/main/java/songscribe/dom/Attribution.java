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

package songscribe.dom;

/**
 * Block element that represents the song's attribution block above the first staff line.
 * <p>
 * It carries no size of its own — the block's extent belongs to the lines the layout pass typesets
 * for it, and is measured there on every pass. What lives here is the stable identity the layout
 * is keyed by, the margins the stacker places it with, and the user Y offset that nudges it.
 * <p>
 * X positioning is always right-aligned to the staff right edge; the user
 * cannot shift it horizontally. Accordingly, {@link #getUserXOffsetSs()} is
 * overridden to return {@code 0.0} so that
 * {@code VerticalStackingCalculator.applyManualOffsets} never applies an X
 * shift to this element.
 */
public class Attribution extends LineElement {

    /** Margin from the attribution block's bottom edge to the top of the staff. */
    public static final double ATTRIBUTION_MARGIN_BOTTOM_SS = 2.0;

    /** Margin from the staff right edge to the attribution block's right edge. */
    public static final double ATTRIBUTION_RIGHT_MARGIN_SS = 0.5;

    public Attribution() {
        setMarginSs(0, 0, ATTRIBUTION_MARGIN_BOTTOM_SS, 0);
    }

    /**
     * Always returns {@code 0.0}. The attribution is always right-aligned to the
     * staff right edge; horizontal position is never user-adjustable.
     */
    @Override
    public double getUserXOffsetSs() {
        return 0.0;
    }
}
