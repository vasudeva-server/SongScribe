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
 * Where an element's ink sits vertically on the staff, as distinct from where a note's pitch
 * sits, which only a note can answer. Every element has a seat.
 */
public final class ElementSeat {

    private ElementSeat() {
    }

    public static StaffPosition seatSp(StaffElement element) {
        return switch (element) {
            case NoteElement note -> note.getStaffPosition();
            case RestElement rest -> rest.seatSp();
            case BreathMarkElement breathMark -> breathMark.seatSp();
            case BarElement _, KeyChangeElement _ -> new StaffPosition(0);
        };
    }

    public static double seatSs(StaffElement element) {
        return seatSp(element).toSs();
    }
}
