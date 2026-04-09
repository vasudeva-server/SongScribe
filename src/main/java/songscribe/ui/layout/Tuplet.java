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

package songscribe.ui.layout;

import songscribe.music.StaffElement;

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

    private int grade = 3;
    private int verticalPositionSs = 0;

    /**
     * Creates a tuplet grouping.
     *
     * @param anchorNote The first note in the tuplet
     * @param endNote    The last note in the tuplet
     * @param grade      The tuplet number (3 for triplet, 5 for quintuplet, etc.)
     */
    public Tuplet(StaffElement anchorNote, StaffElement endNote, int grade) {
        super(anchorNote, endNote);
        this.grade = grade;
    }

    @Override
    public int getElementCount() {
        // For tuplets, the note count is the grade (e.g., 3 for triplet)
        return grade;
    }

    @Override
    public double getContentHeightSs() {
        return 0.7;  // LilyPond vertical arm height
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
        this.verticalPositionSs = verticalPosition;
    }

    @Override
    public double getContentWidthPx() {
        var anchor = getAnchorElement();
        var endElement = getEndElement();

        if (anchor == null || endElement == null) {
            return 0;
        }

        return Math.abs(endElement.getXSs() - anchor.getXSs()) + endElement.getContentWidthPx();
    }

    @Override
    public double getContentHeightPx() {
        // Height of tuplet bracket with number
        return 12.0;
    }
}
