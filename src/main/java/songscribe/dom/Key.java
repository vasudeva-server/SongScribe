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

import java.util.ArrayList;
import java.util.List;

/**
 * A key signature: a {@link KeyType} and how many accidentals it carries.
 *
 * <p>The domain is exactly {@code (NONE, 0)}, {@code (FLATS, 1..}
 * {@value #MAX_ACCIDENTAL_COUNT}{@code )}, and {@code (SHARPS, 1..}
 * {@value #MAX_ACCIDENTAL_COUNT}{@code )} — see {@link #allSignatures()}.
 * {@code keyType} is {@link KeyType#NONE} if and only if {@code accidentalCount}
 * is 0; {@code (NONE, 0)} is C major, a real key rather than an "unset" marker,
 * and {@code keyType} is never null.
 *
 * <p>There is no mode. Every key this program represents is major: MusicXML's
 * {@code <key>} carries an optional {@code <mode>} child, and the writer emits
 * {@code major} for it, but nothing in this record stores one, because this
 * program reads only MusicXML it wrote itself (see "Only SongScribe documents
 * are read" in {@code docs/musicxml-object-model.md}), so no minor or modal key
 * can ever enter the model.
 *
 * @param keyType         the kind of accidental this key carries; never null
 * @param accidentalCount the number of accidentals, {@code 0..}
 *                        {@value #MAX_ACCIDENTAL_COUNT}
 */
public record Key(KeyType keyType, int accidentalCount) {

    /** Maximum number of accidentals in any standard key signature. */
    public static final int MAX_ACCIDENTAL_COUNT = 7;

    /**
     * The key a new song's line 0 starts in: 5 flats.
     */
    public static final Key DEFAULT = new Key(KeyType.FLATS, 5);

    // FLATS order: B E A D G C F; SHARPS order: F C G D A E B.
    // Indexed as [keyType.ordinal() - 1][i]: valid only because KeyType.NONE
    // is ordinal 0, so FLATS (1) and SHARPS (2) land at rows 0 and 1. Reordering
    // KeyType silently corrupts this table.
    private static final int[][] FLAT_SHARP_ORDINAL = {
        {0, 3, 6, 2, 5, 1, 4},
        {4, 1, 5, 2, 6, 3, 0},
    };

    /**
     * @param keyType         the kind of accidental this key carries; never null
     * @param accidentalCount the number of accidentals, {@code 0..}
     *                        {@value #MAX_ACCIDENTAL_COUNT}
     * @throws IllegalArgumentException if {@code keyType == KeyType.NONE} and
     *                                  {@code accidentalCount != 0}, if
     *                                  {@code keyType != KeyType.NONE} and
     *                                  {@code accidentalCount == 0}, or if
     *                                  {@code accidentalCount} is outside
     *                                  {@code 0..}{@value #MAX_ACCIDENTAL_COUNT}
     */
    public Key {
        if (accidentalCount < 0 || accidentalCount > MAX_ACCIDENTAL_COUNT) {
            throw new IllegalArgumentException(
                "accidentalCount must be 0.." + MAX_ACCIDENTAL_COUNT + ", got " + accidentalCount
            );
        }

        if ((keyType == KeyType.NONE) != (accidentalCount == 0)) {
            throw new IllegalArgumentException(
                "keyType == NONE iff accidentalCount == 0, got " + keyType + " with count " + accidentalCount
            );
        }
    }

    private static final List<Key> ALL_SIGNATURES = buildAllSignatures();

    /**
     * Returns every valid key signature, exactly once.
     *
     * <p>The same immutable list is returned on every call, so a caller that holds
     * it — a combo model or a cell renderer's entry list — pays for it once and may
     * keep the reference rather than copying it. Attempting to modify it throws
     * {@link UnsupportedOperationException}.
     *
     * @return every valid {@link Key}, in a stable order: {@code (NONE, 0)} first,
     *         then {@code FLATS} with 1..{@value #MAX_ACCIDENTAL_COUNT}
     *         accidentals, then {@code SHARPS} with 1..
     *         {@value #MAX_ACCIDENTAL_COUNT} accidentals
     */
    public static List<Key> allSignatures() {
        return ALL_SIGNATURES;
    }

    private static List<Key> buildAllSignatures() {
        var keys = new ArrayList<Key>(2 * MAX_ACCIDENTAL_COUNT + 1);
        keys.add(new Key(KeyType.NONE, 0));

        for (var count = 1; count <= MAX_ACCIDENTAL_COUNT; count++) {
            keys.add(new Key(KeyType.FLATS, count));
        }

        for (var count = 1; count <= MAX_ACCIDENTAL_COUNT; count++) {
            keys.add(new Key(KeyType.SHARPS, count));
        }

        return List.copyOf(keys);
    }

    /**
     * Returns whether this key places an accidental on the given pitch class.
     *
     * @param pitchIndex the pitch class: 0 for B, 1 for C, 2 for D, 3 for E,
     *                   4 for F, 5 for G, 6 for A
     * @return {@code true} when this key's accidentals include {@code pitchIndex};
     *         always {@code false} for {@code (NONE, 0)}
     */
    public boolean altersPitchClass(int pitchIndex) {
        if (keyType == KeyType.NONE) {
            return false;
        }

        var ordinals = FLAT_SHARP_ORDINAL[keyType.ordinal() - 1];

        for (var i = 0; i < accidentalCount; i++) {
            if (ordinals[i] == pitchIndex) {
                return true;
            }
        }

        return false;
    }
}
