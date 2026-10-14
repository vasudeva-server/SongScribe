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

package songscribe.ui;

import java.awt.Font;

import songscribe.dom.DocPx;
import songscribe.dom.DocumentScale;
import songscribe.dom.Ss;
import songscribe.dom.ViewPx;

/**
 * A zoom level and the typed conversions across the view boundary it defines.
 * <p>
 * Immutable: a zoom change produces a new instance through {@link #withZoomPercent}, so a
 * stored reference is a snapshot — code that must follow a view's zoom fetches the view's
 * current instance at use.
 * <p>
 * EDT-only by contract; no locking.
 */
public final class ViewScale {

    private static final int NATURAL_ZOOM_PERCENT = 100;

    public static final ViewScale IDENTITY = new ViewScale();

    private final int zoomPercent;

    public ViewScale() {
        this(NATURAL_ZOOM_PERCENT);
    }

    private ViewScale(int zoomPercent) {
        this.zoomPercent = zoomPercent;
    }

    public int getZoomPercent() {
        return zoomPercent;
    }

    /**
     * Returns the scale that converts at {@code zoomPercent}, leaving this one
     * unchanged.
     *
     * @param zoomPercent the zoom of the returned scale, as an integer percentage
     * @return a scale at {@code zoomPercent}; never this instance
     */
    public ViewScale withZoomPercent(int zoomPercent) {
        return new ViewScale(zoomPercent);
    }

    public double factor() {
        return zoomPercent / (double) NATURAL_ZOOM_PERCENT;
    }

    /** Converts a staff-space distance to view pixels at the current zoom. */
    public ViewPx toViewPx(Ss ss) {
        return new ViewPx(ss.value() * DocumentScale.PIXELS_PER_STAFF_SPACE * factor());
    }

    /** Converts a view-pixel distance back to staff spaces at the current zoom. */
    public Ss toSs(ViewPx viewPx) {
        return new Ss(viewPx.value() / (DocumentScale.PIXELS_PER_STAFF_SPACE * factor()));
    }

    /** Converts a document-pixel distance to view pixels at the current zoom. */
    public ViewPx toViewPx(DocPx docPx) {
        return new ViewPx(docPx.value() * factor());
    }

    /** Converts a view-pixel distance back to document pixels at the current zoom. */
    public DocPx toDocPx(ViewPx viewPx) {
        return new DocPx(viewPx.value() / factor());
    }

    public Font zoomedFont(Font baseFont) {
        var zoomFactor = factor();

        // At the default (100%) zoom the derived font is size-identical to the base;
        // skip the Font.deriveFont allocation on this common per-paint / per-layout path.
        if (zoomFactor == 1.0) {
            return baseFont;
        }

        return baseFont.deriveFont((float) (baseFont.getSize2D() * zoomFactor));
    }
}
