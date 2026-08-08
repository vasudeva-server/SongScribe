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

/**
 * Base class for hairpin dynamic markings (crescendo and diminuendo).
 * <p>
 * A hairpin is a wedge-shaped marking placed above the staff that indicates
 * a gradual change in volume. The user can adjust the horizontal endpoints
 * and vertical position.
 */
public abstract sealed class Hairpin extends Span
        permits Crescendo, Diminuendo {

    /**
     * Height of the hairpin opening in staff-space units. LilyPond's {@code Hairpin.height}
     * grob property ({@code scm/define-grobs.scm}) is {@code 0.6666}, a <em>half</em>-height:
     * {@code Hairpin::print} ({@code lily/hairpin.cc:335-336}) draws the two wedge segments at
     * {@code Offset(x, starth)} and {@code Offset(x, -starth)} where {@code starth == height},
     * so the full mouth opening is twice the property value.
     */
    public static final double HAIRPIN_OPENING_HEIGHT_SS = 1.3332;

    /**
     * Minimum drawn length of a hairpin in staff spaces, from LilyPond's
     * {@code Hairpin.minimum-length} (scm/define-grobs.scm). LilyPond applies this
     * as a spacing rod via {@code ly:spanner::set-spacing-rods}, so the notes are
     * pushed apart rather than the wedge being squashed.
     */
    public static final double MINIMUM_LENGTH_SS = 2.0;

    /**
     * The gap a hairpin endpoint keeps from an adjacent text dynamic or barline,
     * from LilyPond's {@code Hairpin.bound-padding} (scm/define-grobs.scm).
     */
    public static final double BOUND_PADDING_SS = 1.0;

    /**
     * The gap each of two back-to-back hairpins keeps from their shared column
     * center. {@code Hairpin::print} uses {@code padding / 3} for this case
     * ("we're not adjacent to a text-dynamic, and we may move closer"), so it is
     * derived from {@link #BOUND_PADDING_SS} rather than written as a literal.
     */
    public static final double BACK_TO_BACK_PADDING_SS = BOUND_PADDING_SS / 3;


    /** User-controlled left-endpoint horizontal shift in staff-space units. */
    private double x1ShiftSs;

    /** User-controlled right-endpoint horizontal shift in staff-space units. */
    private double x2ShiftSs;

    /** User-controlled vertical shift in staff-space units. */
    private double yShiftSs;

    /**
     * Creates a hairpin spanning from anchor to end element.
     *
     * @param anchorElement The starting element of the hairpin
     * @param endElement    The ending element of the hairpin
     */
    protected Hairpin(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    /**
     * Creates a new instance of the concrete hairpin subtype (Crescendo or Diminuendo),
     * anchored to the given elements. Called by {@link #createCopy}, which then carries
     * over the Hairpin fields shared by both subtypes in one place.
     */
    protected abstract Hairpin createHairpin(StaffElement newAnchor, StaffElement newEnd);

    @Override
    protected final Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = createHairpin(newAnchor, newEnd);
        copy.x1ShiftSs = x1ShiftSs;
        copy.x2ShiftSs = x2ShiftSs;
        copy.yShiftSs = yShiftSs;
        return copy;
    }

    public double getX1ShiftSs() {
        return x1ShiftSs;
    }

    public void setX1ShiftSs(double x1ShiftSs) {
        this.x1ShiftSs = x1ShiftSs;
    }

    public double getX2ShiftSs() {
        return x2ShiftSs;
    }

    public void setX2ShiftSs(double x2ShiftSs) {
        this.x2ShiftSs = x2ShiftSs;
    }

    public double getYShiftSs() {
        return yShiftSs;
    }

    public void setYShiftSs(double yShiftSs) {
        this.yShiftSs = yShiftSs;
    }

    /**
     * Returns the height of the hairpin opening in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return HAIRPIN_OPENING_HEIGHT_SS;
    }

    /**
     * Returns the horizontal span width for collision detection. The span runs past the end
     * element's head, so it is measured with that element's own width
     * ({@link #getEndElementWidthSs()}).
     * <p>
     * No longer used to place a hairpin — {@code HairpinEndpoints} resolves both tips from the
     * neighbouring columns instead — but {@link Span#getSpanWidthSs} is abstract, so every span
     * must answer it.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(HAIRPIN_OPENING_HEIGHT_SS, endXSs - anchorXSs + getEndElementWidthSs());
    }

    @Override
    public String toIndexString() {
        var base = getAnchorElementIndex() + "," + getEndElementIndex();
        if (x1ShiftSs != 0 || x2ShiftSs != 0 || yShiftSs != 0) {
            return base + ',' + x1ShiftSs + ',' + x2ShiftSs + ',' + yShiftSs + ';';
        }
        return base + ';';
    }
}
