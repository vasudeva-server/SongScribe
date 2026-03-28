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

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;

/**
 * Represents a tie connecting two notes of the same pitch.
 * <p>
 * Ties connect exactly two notes and are rendered as a curved arc.
 * The placement (above or below) depends on the stem direction of the notes.
 */
public class Tie extends RangeElement {

    private @Nullable StaffElement endNote;

    /**
     * Creates a new tie between two notes.
     *
     * @param anchorNote The first (starting) note of the tie
     * @param endNote    The second (ending) note of the tie
     */
    public Tie(StaffElement anchorNote, StaffElement endNote) {
        setAnchorElement(anchorNote);
        this.endNote = endNote;
    }

    @Override
    public @Nullable StaffElement getEndElement() {
        return endNote;
    }

    /**
     * Sets the end note of this tie.
     */
    public void setEndNote(@Nullable StaffElement endNote) {
        this.endNote = endNote;
    }

    @Override
    public int getNoteCount() {
        var anchor = getAnchorElement();

        if (anchor == null || endNote == null) {
            return 0;
        }

        int startIndex = getAnchorElementIndex();
        int endIndex = getEndElementIndex();

        if (startIndex < 0 || endIndex < 0) {
            return 0;
        }

        return endIndex - startIndex + 1;
    }

    @Override
    public double getContentHeightSs() {
        return 1.0;  // 8px
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }

    @Override
    public boolean isAbove() {
        // Ties go above if stem points down (upper=true), below if stem points up
        var anchor = getAnchorElement();

        return anchor != null && anchor.isUpper();
    }

    @Override
    public double getContentWidthPx() {
        var anchor = getAnchorElement();

        if (anchor == null || endNote == null) {
            return 0;
        }

        // Width spans from anchor to end note
        return Math.abs(endNote.getXSs() - anchor.getXSs()) + endNote.getContentWidthPx();
    }

    @Override
    public double getContentHeightPx() {
        // Tie curve height - approximate arc height
        return 8.0;
    }
}
