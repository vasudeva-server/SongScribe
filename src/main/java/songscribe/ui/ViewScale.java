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
 * The document is authored at a fixed scale
 * ({@link DocumentScale#PIXELS_PER_STAFF_SPACE} pixels per staff space,
 * i.e. 100% zoom): {@link Ss} staff spaces and {@link DocPx} document pixels
 * both live at that scale. A {@code ViewScale} folds the current on-screen zoom
 * on top of it, converting either regime to {@link ViewPx} view pixels — the
 * unit Swing components, mouse input, and overlay bounds operate in.
 * <p>
 * An immutable value: a zoom change produces a new instance through
 * {@link #withZoomPercent}, so a reference already handed out keeps reporting the
 * zoom it was taken at. The {@code ScoreView} that owns a zoom holds the current
 * instance and is the sole source of truth for it; off-score consumers with no
 * view (dialog previews, exporters) read the shared {@link #IDENTITY} instead.
 * A stored reference is therefore a snapshot — code that must follow a view's zoom
 * fetches the view's current instance at use.
 * <p>
 * EDT-only by contract: all reads must occur on the AWT event-dispatch thread. No
 * locking is performed.
 */
public final class ViewScale {

    /**
     * The zoom at which a view renders the document at its authored scale, as a
     * percentage — and so also the divisor converting any zoom percentage to a
     * fraction.
     */
    private static final int NATURAL_ZOOM_PERCENT = 100;

    /**
     * Shared identity scale (natural size). Read through the {@code getViewScale()}
     * null fallback of off-score components; sharing one instance is safe because
     * the class is immutable.
     */
    public static final ViewScale IDENTITY = new ViewScale();

    private final int zoomPercent;

    /** Creates a scale at natural size — the document's own authoring scale. */
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

    /** The current zoom as a fraction of the document scale (1.0 at natural size). */
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
