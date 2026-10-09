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

import songscribe.Strings;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents a trill marking that can span one or more notes.
 * <p>
 * Trills are always displayed above the staff. Even single-note trills
 * are represented as Spans where the anchor and end note are the same.
 */
public final class Trill extends Span<StaffElement> implements IntrinsicHeight {

    // SMuFL bbox-derived dimensions in staff-space units for the "tr" glyph
    public static final double TRILL_GLYPH_WIDTH_SS;
    private static final double TRILL_GLYPH_HEIGHT_SS;

    /** The heading a removed trill is reported under. */
    private static final String REPORT_HEADING_KEY = Strings.REPORT_TRILLS_REMOVED;

    static {
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.ORNAMENT_TRILL);
        TRILL_GLYPH_WIDTH_SS = bbox.widthSs();
        TRILL_GLYPH_HEIGHT_SS = bbox.heightSs();
    }

    /**
     * Creates a trill spanning multiple elements.
     *
     * @param anchorElement The first element of the trill
     * @param endElement    The last element of the trill
     */
    public Trill(StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
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

    /**
     * {@link Displacement#REPLACE_OVERLAPPING}: a new trill replaces every trill it overlaps.
     */
    @Override
    protected Displacement displacement() {
        return Displacement.REPLACE_OVERLAPPING;
    }

    @Override
    protected String reportHeadingKey() {
        return REPORT_HEADING_KEY;
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Trill(newAnchor, newEnd);
    }

    /**
     * Returns the width of the trill "tr" glyph in staff-space units — the glyph's own width,
     * not the extent the wavy line covers, which {@code TrillGeometry.inkExtentSs} computes.
     *
     * @return the glyph's width in staff spaces
     */
    public double intrinsicWidthSs() {
        return TRILL_GLYPH_WIDTH_SS;
    }

    @Override
    public double intrinsicHeightSs() {
        return TRILL_GLYPH_HEIGHT_SS;
    }
}
