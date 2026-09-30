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

import songscribe.engraving.StaffPosition;

/** Shared factory methods for {@link StaffElement} instances used across test classes. */
public final class StaffElementFactory {

    private StaffElementFactory() {}

    /**
     * A crotchet at {@code staffPosition}, {@link AccidentalState.Chosen Chosen} to sound
     * {@code accidental}, or bare and {@link AccidentalState.Adopting Adopting} what is in force
     * where it lands.
     *
     * @param staffPosition the note's staff position
     * @param accidental the accidental it is pitched with, or null for a note that adopts what is
     *     in force where it lands
     * @return the note
     */
    public static NoteElement note(int staffPosition, @Nullable Accidental accidental) {
        var note = placed(crotchet(), staffPosition);

        if (accidental != null) {
            note.choose(accidental);
        }

        return note;
    }

    /**
     * A crotchet at {@code staffPosition}, bare — one that adopts whatever is in force where it
     * lands.
     *
     * @param staffPosition the note's staff position
     * @return the note
     */
    public static NoteElement note(int staffPosition) {
        return note(staffPosition, (Accidental) null);
    }

    /**
     * A crotchet at {@code staffPosition} whose stem is decided by {@code stem}.
     *
     * @param staffPosition the note's staff position
     * @param stem the forced direction, or {@link Stem#AUTO}
     * @return the note
     */
    public static NoteElement note(int staffPosition, Stem stem) {
        var note = placed(crotchet(), staffPosition);
        note.setStem(stem);
        return note;
    }

    /**
     * A quaver at {@code staffPosition} whose stem is decided by {@code stem}.
     *
     * @param staffPosition the note's staff position
     * @param stem the forced direction, or {@link Stem#AUTO}
     * @return the note
     */
    public static NoteElement quaver(int staffPosition, Stem stem) {
        var note = placed(quaver(), staffPosition);
        note.setStem(stem);
        return note;
    }

    /**
     * A grace quaver at {@code staffPosition}.
     *
     * @param staffPosition the note's staff position
     * @return the note
     */
    public static NoteElement graceQuaver(int staffPosition) {
        return placed(graceQuaver(), staffPosition);
    }

    private static NoteElement placed(NoteElement note, int staffPosition) {
        note.setStaffPosition(new StaffPosition(staffPosition));
        return note;
    }

    public static NoteElement crotchet() {
        return (NoteElement) ElementType.CROTCHET.newInstance();
    }

    public static RestElement crotchetRest() {
        return (RestElement) ElementType.CROTCHET_REST.newInstance();
    }

    public static RestElement quaverRest() {
        return (RestElement) ElementType.QUAVER_REST.newInstance();
    }

    public static RestElement minimRest() {
        return (RestElement) ElementType.MINIM_REST.newInstance();
    }

    public static NoteElement quaver() {
        return (NoteElement) ElementType.QUAVER.newInstance();
    }

    public static NoteElement semiquaver() {
        return (NoteElement) ElementType.SEMIQUAVER.newInstance();
    }

    public static NoteElement demiSemiquaver() {
        return (NoteElement) ElementType.DEMI_SEMIQUAVER.newInstance();
    }

    public static NoteElement dottedQuaver() {
        var note = quaver();
        note.setDots(Dots.ONE);

        return note;
    }

    public static NoteElement graceQuaver() {
        return (NoteElement) ElementType.GRACE_QUAVER.newInstance();
    }

    public static BreathMarkElement breathMark() {
        return (BreathMarkElement) ElementType.BREATH_MARK.newInstance();
    }

    public static BarElement repeatLeft() {
        return (BarElement) ElementType.REPEAT_LEFT.newInstance();
    }

    public static BarElement repeatRight() {
        return (BarElement) ElementType.REPEAT_RIGHT.newInstance();
    }

    public static BarElement repeatLeftRight() {
        return (BarElement) ElementType.REPEAT_LEFT_RIGHT.newInstance();
    }

    public static BarElement singleBarline() {
        return (BarElement) ElementType.SINGLE_BARLINE.newInstance();
    }

    public static BarElement doubleBarline() {
        return (BarElement) ElementType.DOUBLE_BARLINE.newInstance();
    }

    public static BarElement finalDoubleBarline() {
        return (BarElement) ElementType.FINAL_DOUBLE_BARLINE.newInstance();
    }

    /**
     * A bar of {@code type}, for a case table whose rows are derived from {@link ElementType}.
     *
     * @param type a type whose {@link ElementType#isBar()} is {@code true}
     * @return a new bar of {@code type}
     */
    public static BarElement bar(ElementType type) {
        return (BarElement) type.newInstance();
    }

    /**
     * A mid-line key change establishing {@code key}.
     *
     * @param key the key it establishes
     * @return the key change
     */
    public static KeyChangeElement keyChange(Key key) {
        return new KeyChangeElement(key);
    }
}
