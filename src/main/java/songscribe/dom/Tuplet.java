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

import module java.desktop;

import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Represents a tuplet grouping (triplet, quintuplet, etc.).
 * <p>
 * Tuplets group notes that are played in the time normally occupied
 * by a different number of notes. The most common is a triplet (3 notes
 * in the time of 2).
 * <p>
 * The tuplet bracket is typically placed above if stems point down,
 * below if stems point up.
 */
public class Tuplet extends RangeElement {

    /** Inward shortening at each bracket endpoint (LilyPond: -0.2ss). */
    public static final double ARM_EXTENSION_SS = 0.2;  // 1.6px

    /** Italic serif font for tuplet numbers (font size in staff-spaces). */
    public static final float TUPLET_FONT_SIZE_SS = 1.8f;

    /** Italic serif font for tuplet numbers. */
    public static final Font TUPLET_FONT = MyFontUtils.getLocalFont("C059-Italic.otf", TUPLET_FONT_SIZE_SS);

    /** Vertical arm height of bracket endpoints (LilyPond: 0.7ss). */
    public static final double BRACKET_ARM_HEIGHT_SS = 0.7;  // 5.6px

    /** Measured ink height of a tuplet number in staff-spaces (representative digit "3"). */
    public static final double TUPLET_NUMBER_INK_HEIGHT_SS = measureNumberInkHeightSs();

    private static double measureNumberInkHeightSs() {
        var inkHeightSs = GraphicUtils.inkHeight(
            TUPLET_FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, "3").getVisualBounds());

        if (inkHeightSs <= 0) {
            throw new IllegalStateException(
                "Tuplet number font produced zero ink height; the font may have failed to load");
        }

        return inkHeightSs;
    }

    private int grade;
    private int verticalPositionSs = 0;

    /**
     * Creates a tuplet grouping.
     *
     * @param anchorElement The first note in the tuplet
     * @param endElement    The last note in the tuplet
     * @param grade      The tuplet number (3 for triplet, 5 for quintuplet, etc.)
     */
    public Tuplet(StaffElement anchorElement, StaffElement endElement, int grade) {
        super(anchorElement, endElement);
        this.grade = grade;
    }

    @Override
    public int getElementCount() {
        // For tuplets, the note count is the grade (e.g., 3 for triplet)
        return grade;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the <em>bracketed</em> reserved height. A number-only tuplet
     * (see {@link #isNumberOnly(Line)}) reserves {@link #numberOnlyHeightSs()}
     * instead. Because that distinction requires a {@link Line}, callers laying
     * out tuplets must branch on {@code isNumberOnly} rather than rely on this
     * context-free value.
     */
    @Override
    public double getContentHeightSs() {
        return bracketedHeightSs();
    }

    /**
     * Reserved vertical height for a bracketed tuplet (bracket line + arm).
     */
    public static double bracketedHeightSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0 + BRACKET_ARM_HEIGHT_SS;
    }

    /**
     * Reserved vertical height for a number-only tuplet (beamed, stems up).
     */
    public static double numberOnlyHeightSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS;
    }

    /**
     * Offset from the box top to the bracket line center in staff-spaces.
     */
    public static double bracketLineOffsetSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0;
    }

    /**
     * Returns true when only the number is drawn (no bracket): the tuplet is beamed
     * at both its anchor and end index and its anchor note has upward stems.
     */
    public boolean isNumberOnly(Line line) {
        var anchor = getAnchorElement();

        if (anchor == null) {
            return false;
        }

        return line.findBeamAt(getAnchorElementIndex()) != null
            && line.findBeamAt(getEndElementIndex()) != null
            && anchor.getDirection().isUp();
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }

    /**
     * Returns the tuplet grade (3 for triplet, 5 for quintuplet, etc.).
     */
    public int getGrade() {
        return grade;
    }

    /**
     * Sets the tuplet grade.
     */
    public void setGrade(int grade) {
        this.grade = grade;
    }

    /**
     * Returns the user-adjustable Y offset for this tuplet bracket.
     */
    public int getVerticalPositionSs() {
        return verticalPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this tuplet bracket.
     */
    public void setVerticalPositionSs(int verticalPosition) {
        verticalPositionSs = verticalPosition;
    }

    @Override
    public String toIndexString() {
        var base = getAnchorElementIndex() + "," + getEndElementIndex() + "," + grade;

        if (verticalPositionSs != 0) {
            return base + "," + verticalPositionSs + ";";
        }

        return base + ";";
    }
}
