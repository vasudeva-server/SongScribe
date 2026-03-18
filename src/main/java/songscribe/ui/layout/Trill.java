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
 * Represents a trill marking that can span one or more notes.
 * <p>
 * Trills are always displayed above the staff. Even single-note trills
 * are represented as RangeElements where the anchor and end note are the same.
 */
public class Trill extends RangeElement {

    private @Nullable StaffElement endNote;
    private int yPositionSs = 0;

    /**
     * Creates a trill spanning multiple notes.
     *
     * @param anchorNote The first note of the trill
     * @param endNote    The last note of the trill
     */
    public Trill(StaffElement anchorNote, StaffElement endNote) {
        setAnchorElement(anchorNote);
        this.endNote = endNote;
    }

    /**
     * Creates a single-note trill.
     *
     * @param note The note with the trill
     */
    public Trill(StaffElement note) {
        this(note, note);
    }

    @Override
    public @Nullable StaffElement getEndElement() {
        return endNote;
    }

    /**
     * Sets the end note of this trill.
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

        // Single-note trill
        if (anchor == endNote) {
            return 1;
        }

        int startIndex = getAnchorElementIndex();
        int endIndex = getEndElementIndex();

        if (startIndex < 0 || endIndex < 0) {
            return 0;
        }

        return endIndex - startIndex + 1;
    }

    @Override
    public boolean isAbove() {
        // Trills are always above the staff
        return true;
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

    @Override
    public double getContentWidth() {
        var anchor = getAnchorElement();

        if (anchor == null || endNote == null) {
            return 0;
        }

        if (anchor == endNote) {
            return anchor.getContentWidth();
        }

        return Math.abs(endNote.getXSs() - anchor.getXSs()) + endNote.getContentWidth();
    }

    @Override
    public double getContentHeight() {
        // Height of trill symbol ("tr" + wavy line)
        return 12.0;
    }
}
