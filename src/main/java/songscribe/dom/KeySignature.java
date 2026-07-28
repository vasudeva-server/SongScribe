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

import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents the key signature (sharps or flats) at the start of a staff line.
 * <p>
 * The KeySignature is positioned absolutely after the clef and does not contribute
 * to Staff's bounds. It has its own margin for spacing to the first note.
 * <p>
 * The width depends on the number of accidentals (0-7 sharps or flats).
 */
public class KeySignature extends LineElement {

    /** Maximum number of accidentals in any standard key signature (0–7). */
    public static final int MAX_ACCIDENTAL_COUNT = 7;

    /** The type of key signature (sharps or flats). */
    private KeyType keyType;

    /** Number of accidentals (0–{@link #MAX_ACCIDENTAL_COUNT}). */
    private int accidentalCount;

    /**
     * Creates a key signature with no accidentals.
     */
    public KeySignature() {
        this(KeyType.NONE, 0);
    }

    /**
     * Creates a key signature with the specified type and accidental count.
     *
     * @param keyType         Type of accidentals (SHARPS or FLATS)
     * @param accidentalCount Number of accidentals (0-7)
     */
    public KeySignature(KeyType keyType, int accidentalCount) {
        this.keyType = keyType;
        this.accidentalCount = Math.clamp(accidentalCount, 0, MAX_ACCIDENTAL_COUNT);
    }

    /**
     * Returns the key type (SHARPS, FLATS, or NONE).
     */
    public KeyType getKeyType() {
        return keyType;
    }

    /**
     * Sets the key type.
     */
    public void setKeyType(KeyType keyType) {
        this.keyType = keyType;
    }

    /**
     * Returns the number of accidentals (0-7).
     */
    public int getAccidentalCount() {
        return accidentalCount;
    }

    /**
     * Sets the number of accidentals.
     *
     * @param accidentalCount Number of accidentals (clamped to 0-7)
     */
    public void setAccidentalCount(int accidentalCount) {
        this.accidentalCount = Math.clamp(accidentalCount, 0, MAX_ACCIDENTAL_COUNT);
    }

    /**
     * Returns whether this key signature has any accidentals.
     */
    public boolean hasAccidentals() {
        return accidentalCount > 0 && keyType != KeyType.NONE;
    }

    @Override
    public double getContentWidthSs() {
        return widthSs(keyType, accidentalCount);
    }

    /**
     * Returns the width of a key signature with the given type and accidental count,
     * in staff-space units. Zero when there is nothing to draw — a null or
     * {@link KeyType#NONE} type, or a count that is not positive.
     *
     * @param keyType         Type of accidentals, may be null
     * @param accidentalCount Number of accidentals
     */
    public static double widthSs(@Nullable KeyType keyType, int accidentalCount) {
        if (keyType == null || keyType == KeyType.NONE || accidentalCount <= 0) {
            return 0;
        }

        return accidentalCount * StaffHeaderMetrics.accidentalInkBboxSs(accidentalGlyph(keyType));
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.ssToPx(getContentWidthSs());
    }

    /**
     * Returns the content height in staff-space units — the bbox height of the active
     * accidental glyph. Kerning and inter-glyph vertical variation are out of scope.
     */
    @Override
    public double getContentHeightSs() {
        if (!hasAccidentals()) {
            return 0;
        }

        return SMuFLMetadata.requireBBox(accidentalGlyph(keyType)).height();
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.ssToPx(getContentHeightSs());
    }

    /**
     * Returns the accidental glyph a key signature of the given type is drawn with.
     */
    private static SMuFLGlyph accidentalGlyph(KeyType keyType) {
        return keyType == KeyType.FLATS ? SMuFLGlyph.ACCIDENTAL_FLAT : SMuFLGlyph.ACCIDENTAL_SHARP;
    }
}
