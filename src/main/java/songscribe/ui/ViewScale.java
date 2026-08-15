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
import songscribe.dom.ScaleContext;
import songscribe.dom.Ss;
import songscribe.dom.ViewPx;

/**
 * Per-view zoom state and the typed conversions across its boundary.
 * <p>
 * The document is authored at a fixed scale
 * ({@link ScaleContext#DEFAULT_PIXELS_PER_STAFF_SPACE} pixels per staff space,
 * i.e. 100% zoom): {@link Ss} staff spaces and {@link DocPx} document pixels
 * both live at that scale. A {@code ViewScale} folds the current on-screen zoom
 * on top of it, converting either regime to {@link ViewPx} view pixels — the
 * unit Swing components, mouse input, and overlay bounds operate in.
 * <p>
 * A {@code ViewScale} is owned by a single {@code ScoreView} and is the sole
 * source of truth for that view's zoom. Off-score consumers with no view (dialog
 * previews, exporters) read the shared read-only {@link #IDENTITY} instead.
 * <p>
 * EDT-only by contract: all reads and writes must occur on the AWT
 * event-dispatch thread. No locking is performed.
 */
public final class ViewScale {

    /**
     * Shared, <b>read-only</b> identity scale (100% zoom). Never mutated: it is
     * only ever <em>read</em> through the {@code getViewScale()} null fallback of
     * off-score components, never stored in a mutable field, so no consumer can
     * change it. Do not call {@link #setZoomPercent} on this instance.
     */
    public static final ViewScale IDENTITY = new ViewScale();

    /** Divisor converting an integer zoom percentage to a fraction. */
    private static final double ZOOM_PERCENT_SCALE = 100.0;

    private int zoomPercent = 100;

    public int getZoomPercent() {
        return zoomPercent;
    }

    public void setZoomPercent(int zoomPercent) {
        this.zoomPercent = zoomPercent;
    }

    /** The current zoom as a fraction of the document scale (1.0 at 100%). */
    public double factor() {
        return zoomPercent / ZOOM_PERCENT_SCALE;
    }

    /** Converts a staff-space distance to view pixels at the current zoom. */
    public ViewPx toViewPx(Ss ss) {
        return new ViewPx(ss.value() * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE * factor());
    }

    /** Converts a view-pixel distance back to staff spaces at the current zoom. */
    public Ss toSs(ViewPx viewPx) {
        return new Ss(viewPx.value() / (ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE * factor()));
    }

    /** Converts a document-pixel distance to view pixels at the current zoom. */
    public ViewPx toViewPx(DocPx docPx) {
        return new ViewPx(docPx.value() * factor());
    }

    /** Converts a view-pixel distance back to document pixels at the current zoom. */
    public DocPx toDocPx(ViewPx viewPx) {
        return new DocPx(viewPx.value() / factor());
    }

    /** Returns {@code baseFont} scaled by the current zoom factor. */
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
