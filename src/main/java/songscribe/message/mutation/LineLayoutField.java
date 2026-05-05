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

package songscribe.message.mutation;

/**
 * Identifies which line-level layout property changed in a {@link LineLayoutChange} mutation.
 * Each constant carries the expected runtime type of {@code oldValue} and {@code newValue};
 * {@link LineLayoutChange}'s canonical constructor validates values against this type.
 */
public enum LineLayoutField {
    /** Y-position of tempo markings (pixels). Retained for deserializing legacy document formats. */
    TEMPO_CHANGE_Y_POS_PX(Integer.class),

    /** Y-position of beat-change markings (pixels). Retained for deserializing legacy document formats. */
    BEAT_CHANGE_Y_POS_PX(Integer.class),

    /** Y-position of first/second ending spans (pixels). Retained for deserializing legacy document formats. */
    FIRST_SECOND_ENDING_Y_POS_PX(Integer.class),

    /** Y-position of trill markings (pixels). Retained for deserializing legacy document formats. */
    TRILL_Y_POS_PX(Integer.class),

    /** Y-position of lyrics baseline (staff spaces). */
    LYRICS_Y_POS_SS(Double.class),

    /** Ratio multiplier for horizontal element spacing. */
    ELEMENT_SPACING_RATIO(Float.class);

    private final Class<?> expectedType;

    LineLayoutField(Class<?> expectedType) {
        this.expectedType = expectedType;
    }

    public Class<?> getExpectedType() {
        return expectedType;
    }
}
