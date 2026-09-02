/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents a trill marking that can span one or more notes.
 * <p>
 * Trills are always displayed above the staff. Even single-note trills
 * are represented as Spans where the anchor and end note are the same.
 */
public class Trill extends Span implements IntrinsicHeight {

    // SMuFL bbox-derived dimensions in staff-space units for the "tr" glyph
    private static final double TRILL_GLYPH_WIDTH_SS;
    private static final double TRILL_GLYPH_HEIGHT_SS;

    static {
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.ORNAMENT_TRILL);
        TRILL_GLYPH_WIDTH_SS = bbox.widthSs();
        TRILL_GLYPH_HEIGHT_SS = bbox.heightSs();
    }

    private int yPositionSs = 0;

    /**
     * Creates a trill spanning multiple elements.
     *
     * @param anchorElement The first element of the trill
     * @param endElement    The last element of the trill
     */
    public Trill(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    /**
     * Creates a single-note trill.
     *
     * @param anchorElement The note with the trill
     */
    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    public Trill(StaffElement anchorElement) {
        this(anchorElement, anchorElement);
    }

    @Override
    protected Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = new Trill(newAnchor, newEnd);
        copy.yPositionSs = yPositionSs;
        return copy;
    }

    /**
     * Returns the user-adjustable Y offset for this trill.
     */
    public int getYPositionSs() {
        return yPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this trill.
     */
    public void setYPositionSs(int yPositionSs) {
        this.yPositionSs = yPositionSs;
    }

    /**
     * Returns the width of the trill "tr" glyph in staff-space units — the glyph's own width,
     * not the span the wavy line covers, which is {@link #getSpanWidthSs}.
     * <p>
     * For vertical stacking, only the glyph width matters since the wavy
     * extension is drawn at the same Y level.
     */
    public double intrinsicWidthSs() {
        return TRILL_GLYPH_WIDTH_SS;
    }

    @Override
    public double intrinsicHeightSs() {
        return TRILL_GLYPH_HEIGHT_SS;
    }

    /**
     * The left edge of the drawn "tr" glyph, in staff-space units.
     * <p>
     * The glyph is centered on the notehead rather than on the note column, so it starts a little
     * left of the column. Written once here and used by everything that has to agree on where the
     * glyph lands: the stacker's reservation, the renderer's ink, and the hit region.
     *
     * @param columnXSs         X of the anchor note's column
     * @param noteheadCenterXSs the notehead's center, measured from the column's left edge
     */
    public static double glyphLeftXSs(double columnXSs, double noteheadCenterXSs) {
        return columnXSs + noteheadCenterXSs - TRILL_GLYPH_WIDTH_SS / 2.0;
    }

    /**
     * Returns the full horizontal span the trill's wavy line occupies, in staff spaces: from the
     * anchor note's X to one glyph width past the end note's, since the line is drawn in whole
     * glyphs and the last one starts at the end note rather than finishing there.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return the span width in staff spaces, never less than one {@link #TRILL_GLYPH_WIDTH_SS}
     *     however close the two elements sit
     */
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(TRILL_GLYPH_WIDTH_SS, endXSs - anchorXSs + TRILL_GLYPH_WIDTH_SS);
    }
}
