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

package songscribe.ui.layout;


import songscribe.music.KeyType;
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

    /** The type of key signature (sharps or flats). */
    private KeyType keyType;

    /** Number of accidentals (0-7). */
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
        this.accidentalCount = Math.max(0, Math.min(7, accidentalCount));

        // Default margin from key signature to first note
        setMarginRightSs(1.0);
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
        this.accidentalCount = Math.max(0, Math.min(7, accidentalCount));
    }

    /**
     * Returns whether this key signature has any accidentals.
     */
    public boolean hasAccidentals() {
        return accidentalCount > 0 && keyType != KeyType.NONE;
    }

    @Override
    public double getContentWidthSs() {
        if (!hasAccidentals()) {
            return 0;
        }

        return accidentalCount * SMuFLMetadata.getInstance().requireBBox(accidentalGlyph()).width();
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.getInstance().toPixels(getContentWidthSs());
    }

    /**
     * Returns the content height in staff-space units — the bbox height of the active
     * accidental glyph. Kerning and inter-glyph vertical variation are out of scope.
     */
    public double getContentHeightSs() {
        if (!hasAccidentals()) {
            return 0;
        }

        return SMuFLMetadata.getInstance().requireBBox(accidentalGlyph()).height();
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }

    private SMuFLGlyph accidentalGlyph() {
        return keyType == KeyType.FLATS ? SMuFLGlyph.ACCIDENTAL_FLAT : SMuFLGlyph.ACCIDENTAL_SHARP;
    }
}
