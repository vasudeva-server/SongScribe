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
 * Block element that represents the attribution pane above the first staff line.
 * <p>
 * Carries only the block geometry (width/height in staff-spaces) and a user Y
 * offset. The layout engine measures the {@code AttributionPane}'s preferred
 * size, converts it from pixels to staff-spaces, and stores the result here via
 * {@link #setDimensionsSs(double, double)} before handing the dimensions to the
 * stacker.
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

    private double widthSs;
    private double heightSs;

    /**
     * Creates an attribution block element with zero initial dimensions.
     * Call {@link #setDimensionsSs(double, double)} after measuring the pane.
     */
    public Attribution() {
        setMarginSs(0, 0, ATTRIBUTION_MARGIN_BOTTOM_SS, 0);
    }

    /**
     * Sets the block dimensions from the measured {@code AttributionPane}
     * preferred size (already converted from pixels to staff-spaces by the
     * caller via {@code ScaleContext.pxToSs}).
     *
     * @param widthSs  pane width in staff-spaces
     * @param heightSs pane height in staff-spaces
     */
    public void setDimensionsSs(double widthSs, double heightSs) {
        this.widthSs = widthSs;
        this.heightSs = heightSs;
    }

    @Override
    public double getContentWidthSs() {
        return widthSs;
    }

    @Override
    public double getContentHeightSs() {
        return heightSs;
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.ssToPx(widthSs);
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.ssToPx(heightSs);
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
