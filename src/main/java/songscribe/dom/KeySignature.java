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
 * The key signature (sharps or flats) drawn at the start of a staff line, as a positioned
 * layout box.
 * <p>
 * This is a transient layout object rather than part of the document model: layout builds one
 * per line from the line's running key and the renderer paints it. A line's own key lives on
 * {@link Line}, and a change part-way through a line lives in a {@link KeyChangeElement}.
 * <p>
 * The KeySignature is positioned absolutely after the clef and does not contribute
 * to Staff's bounds. It has its own margin for spacing to the first note.
 * <p>
 * Its measurements are {@link Key#signatureWidthSs()}'s and {@link Key#signatureHeightSs()}'s —
 * the same run of accidentals the renderer draws, reached by the same calls — so the header and a
 * cautionary key change at the end of a line can never disagree about how large one signature is.
 */
public class KeySignature extends LineElement {

    /** The key this signature draws. */
    private final Key key;

    /**
     * Creates a header key signature for the given key.
     *
     * @param key the key to draw; {@link Key#NO_ACCIDENTALS} draws nothing and measures
     *            zero in both dimensions
     */
    public KeySignature(Key key) {
        this.key = key;
    }

    /**
     * Returns the key this signature draws.
     *
     * @return the key; never null
     */
    public Key getKey() {
        return key;
    }

}
