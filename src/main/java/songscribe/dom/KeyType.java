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

package songscribe.dom;

import songscribe.smufl.SMuFLGlyph;

public enum KeyType {
    NONE,
    FLATS,
    SHARPS,
    ;

    /**
     * Returns the glyph a key signature of this type is drawn with.
     *
     * @return the flat or sharp glyph
     * @throws IllegalStateException if this is {@link #NONE}, which draws no accidental and so has
     *                               no glyph
     */
    public SMuFLGlyph glyph() {
        return switch (this) {
            case FLATS -> SMuFLGlyph.ACCIDENTAL_FLAT;
            case SHARPS -> SMuFLGlyph.ACCIDENTAL_SHARP;
            case NONE -> throw new IllegalStateException("NONE draws no accidental");
        };
    }
}
