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
package songscribe.io.musicxml;

import songscribe.dom.KeyType;

/**
 * Bidirectional mapping between a SongScribe key signature ({@link KeyType} plus
 * accidental count) and its MusicXML signed-{@code <fifths>} encoding. The writer
 * encodes via {@link #toFifths}; the reader decodes via {@link #keyType} and
 * {@link #accidentalCount}. Owning both directions here keeps the sign convention
 * — flats negative, sharps positive, no accidentals zero — from drifting apart
 * across the two files.
 */
final class KeySignatureMapping {

    private KeySignatureMapping() {}

    /**
     * Encodes a key signature as signed fifths per the MusicXML convention:
     * flats are negative, sharps positive, and {@code NONE} (no accidentals) zero.
     */
    static int toFifths(KeyType keyType, int accidentalCount) {
        if (keyType == KeyType.FLATS) {
            return -accidentalCount;
        }

        // SHARPS → +count; NONE → 0 (its accidental count is always 0).
        return accidentalCount;
    }

    /**
     * Decodes signed fifths to the corresponding {@link KeyType}: positive →
     * {@link KeyType#SHARPS}; zero or negative → {@link KeyType#FLATS}. Zero maps
     * to {@code FLATS} to match {@link songscribe.dom.Song#DEFAULT_KEY_TYPE}.
     */
    static KeyType keyType(int fifths) {
        if (fifths > 0) {
            return KeyType.SHARPS;
        }

        // fifths == 0 (no accidentals, default FLATS per Song.DEFAULT_KEY_TYPE)
        // or fifths < 0 (explicit flats).
        return KeyType.FLATS;
    }

    /** Decodes the accidental count encoded in signed fifths: its magnitude. */
    static int accidentalCount(int fifths) {
        return Math.abs(fifths);
    }
}
