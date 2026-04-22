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


/**
 * Layout information for a single lyric syllable.
 * <p>
 * Syllables are horizontally aligned with their associated note and share
 * a common baseline with other syllables in the same lyrics row.
 */
public final class SyllableLayout {

    private final int elementIndex;
    private final int x;
    private final String text;
    private final ElementBoundsSs bounds;
    private final boolean hasMelisma;

    /**
     * Creates syllable layout.
     *
     * @param elementIndex  Index of the associated element
     * @param x             Horizontal position (aligned with element)
     * @param text       Syllable text
     * @param bounds     Element bounds with horizontal margins
     * @param hasMelisma True if followed by melisma line (held syllable)
     */
    public SyllableLayout(
        int elementIndex,
        int x,
        String text,
        ElementBoundsSs bounds,
        boolean hasMelisma
    ) {
        this.elementIndex = elementIndex;
        this.x = x;
        this.text = text;
        this.bounds = bounds;
        this.hasMelisma = hasMelisma;
    }

    /**
     * Creates syllable layout without melisma.
     */
    public SyllableLayout(
        int elementIndex,
        int x,
        String text,
        ElementBoundsSs bounds
    ) {
        this(elementIndex, x, text, bounds, false);
    }

    /**
     * Returns the index of the associated element.
     */
    public int getElementIndex() {
        return elementIndex;
    }

    /**
     * Returns the horizontal position.
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the syllable text.
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the element bounds.
     */
    public ElementBoundsSs getBounds() {
        return bounds;
    }

    /**
     * Returns whether this syllable is followed by a melisma line.
     */
    public boolean hasMelisma() {
        return hasMelisma;
    }

    /**
     * Returns whether the given point is within hit testing bounds.
     */
    public boolean containsPoint(double xSs, double ySs) {
        return bounds.containsForHitTest(xSs, ySs);
    }

    @Override
    public String toString() {
        return "SyllableLayout{" +
            "elementIndex=" + elementIndex +
            ", x=" + x +
            ", text='" + text + "'" +
            (hasMelisma ? ", melisma" : "") +
            "}";
    }
}
