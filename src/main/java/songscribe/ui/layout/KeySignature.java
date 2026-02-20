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

import org.jetbrains.annotations.NotNull;

import songscribe.music.KeyType;

/**
 * Represents the key signature (sharps or flats) at the start of a staff line.
 * <p>
 * The KeySignature is positioned absolutely after the clef and does not contribute
 * to Staff's bounds. It has its own margin for spacing to the first note.
 * <p>
 * The width depends on the number of accidentals (0-7 sharps or flats).
 */
public class KeySignature extends LineElement {

    /** Width per accidental glyph in pixels. */
    private static final double ACCIDENTAL_WIDTH = 8.0;

    /** Height of accidental glyphs (may extend above/below staff). */
    private static final double ACCIDENTAL_HEIGHT = 24.0;

    /** The type of key signature (sharps or flats). */
    private @NotNull KeyType keyType;

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
    public KeySignature(@NotNull KeyType keyType, int accidentalCount) {
        this.keyType = keyType;
        this.accidentalCount = Math.max(0, Math.min(7, accidentalCount));

        // Default margin from key signature to first note
        setMarginRight(LayoutStylesheet.toPixels(1.0));
    }

    /**
     * Returns the key type (SHARPS, FLATS, or NONE).
     */
    public @NotNull KeyType getKeyType() {
        return keyType;
    }

    /**
     * Sets the key type.
     */
    public void setKeyType(@NotNull KeyType keyType) {
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
    public double getContentWidth() {
        if (!hasAccidentals()) {
            return 0;
        }

        return accidentalCount * ACCIDENTAL_WIDTH;
    }

    @Override
    public double getContentHeight() {
        if (!hasAccidentals()) {
            return 0;
        }

        return ACCIDENTAL_HEIGHT;
    }
}
