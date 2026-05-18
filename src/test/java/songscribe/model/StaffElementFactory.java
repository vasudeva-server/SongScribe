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

package songscribe.model;

/** Shared factory methods for {@link StaffElement} instances used across test classes. */
public final class StaffElementFactory {

    private StaffElementFactory() {}

    public static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    public static StaffElement quaver() {
        return ElementType.QUAVER.newInstance();
    }

    public static StaffElement crotchetRest() {
        return ElementType.CROTCHET_REST.newInstance();
    }

    public static StaffElement repeatLeft() {
        return ElementType.REPEAT_LEFT.newInstance();
    }

    public static StaffElement repeatRight() {
        return ElementType.REPEAT_RIGHT.newInstance();
    }

    public static StaffElement repeatLeftRight() {
        return ElementType.REPEAT_LEFT_RIGHT.newInstance();
    }

    public static StaffElement singleBarline() {
        return ElementType.SINGLE_BARLINE.newInstance();
    }

    public static StaffElement doubleBarline() {
        return ElementType.DOUBLE_BARLINE.newInstance();
    }

    public static StaffElement finalDoubleBarline() {
        return ElementType.FINAL_DOUBLE_BARLINE.newInstance();
    }

    public static StaffElement createNote(int staffPosition, boolean upper) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosition);
        note.setUpper(upper);
        return note;
    }
}
