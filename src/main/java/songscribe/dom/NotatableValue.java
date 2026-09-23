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

/**
 * A written note value with its dots: the value a tuplet ratio counts its normal notes in.
 * <p>
 * It is not a {@link BeatUnit}: a beat allows one dot, and a ratio's value allows two.
 *
 * @param value the undotted note value
 * @param dots  the dots the value carries
 */
public record NotatableValue(NoteValue value, Dots dots) {

    /**
     * Returns this value's written duration.
     *
     * @return the note value's ticks lengthened by the dots, at
     *     {@link songscribe.midi.MidiSequenceBuilder#PPQ} ticks per quarter
     * @invariant exact for every {@link NoteValue} and {@link Dots} pair — see {@link Dots#augment}
     */
    public int ticks() {
        return dots.augment(value.ticks());
    }
}
