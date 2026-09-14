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
package songscribe.layout;

import songscribe.dom.BarElement;
import songscribe.dom.BreathMarkElement;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.NoteElement;
import songscribe.dom.RestElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.StaffPosition;

/**
 * Where an element's ink sits vertically on the staff — a different question from where a
 * note's pitch sits, which only a note can answer. Every element has ink, so every element has
 * a seat: a note's is its staff position, a rest's and a breath mark's is the seat their
 * appearance declares, and a barline or a key change spans the staff and is seated on its
 * middle line.
 *
 * <p>The stackers, the hit geometry and the renderers ask this of a column whose element may
 * be of any kind. A site that asks about a note's pitch narrows to {@link NoteElement}
 * instead and reads the staff position directly.
 */
public final class ElementSeat {

    private ElementSeat() {
    }

    /**
     * Returns the staff position an element's ink is seated at.
     *
     * @param element the element
     * @return the seat
     */
    public static StaffPosition seatSp(StaffElement element) {
        return switch (element) {
            case NoteElement note -> note.getStaffPosition();
            case RestElement rest -> rest.seatSp();
            case BreathMarkElement breathMark -> breathMark.seatSp();
            case BarElement _, KeyChangeElement _ -> new StaffPosition(0);
        };
    }

    /**
     * Returns the seat of an element's ink as a distance from the middle staff line.
     *
     * @param element the element
     * @return {@link StaffPosition#toSs} of {@link #seatSp}
     */
    public static double seatSs(StaffElement element) {
        return seatSp(element).toSs();
    }
}
