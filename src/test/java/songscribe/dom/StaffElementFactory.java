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

package songscribe.dom;

import org.jspecify.annotations.Nullable;

/** Shared factory methods for {@link StaffElement} instances used across test classes. */
public final class StaffElementFactory {

    private StaffElementFactory() {}

    /**
     * A crotchet at {@code staffPosition} carrying {@code accidental}.
     *
     * @param staffPosition the note's staff position
     * @param accidental the accidental to write on it, or null for a note that carries none
     * @return the note
     */
    public static StaffElement note(int staffPosition, StaffElement.@Nullable Accidental accidental) {
        var note = crotchet();
        note.setStaffPosition(staffPosition);
        note.setAccidental(accidental);
        return note;
    }

    /**
     * A crotchet at {@code staffPosition} carrying no accidental — one that sounds whatever the
     * key in effect there says it does.
     *
     * @param staffPosition the note's staff position
     * @return the note
     */
    public static StaffElement note(int staffPosition) {
        return note(staffPosition, null);
    }

    public static StaffElement semibreve() {
        return ElementType.SEMIBREVE.newInstance();
    }

    public static StaffElement semibreveRest() {
        return ElementType.SEMIBREVE_REST.newInstance();
    }

    public static StaffElement minim() {
        return ElementType.MINIM.newInstance();
    }

    public static StaffElement minimRest() {
        return ElementType.MINIM_REST.newInstance();
    }

    public static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    public static StaffElement crotchetRest() {
        return ElementType.CROTCHET_REST.newInstance();
    }

    public static StaffElement quaver() {
        return ElementType.QUAVER.newInstance();
    }

    public static StaffElement quaverRest() {
        return ElementType.QUAVER_REST.newInstance();
    }

    public static StaffElement semiquaver() {
        return ElementType.SEMIQUAVER.newInstance();
    }

    public static StaffElement demiSemiquaver() {
        return ElementType.DEMI_SEMIQUAVER.newInstance();
    }

    public static StaffElement graceQuaver() {
        return ElementType.GRACE_QUAVER.newInstance();
    }

    public static StaffElement breathMark() {
        return ElementType.BREATH_MARK.newInstance();
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
