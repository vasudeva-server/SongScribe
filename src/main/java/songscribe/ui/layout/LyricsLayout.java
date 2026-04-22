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

import java.util.List;

import org.jspecify.annotations.Nullable;


/**
 * Layout information for a lyrics row (syllables under a staff line).
 * <p>
 * All syllables in a row share a common baseline Y coordinate.
 * Horizontal positions are aligned with their corresponding elements.
 */
public final class LyricsLayout {

    private final int baselineY;
    private final List<SyllableLayout> syllables;
    private final ElementBoundsSs bounds;

    /**
     * Creates lyrics layout with syllables.
     *
     * @param baselineY Shared baseline Y for all syllables
     * @param syllables List of syllable layouts
     * @param bounds    Combined bounds of all syllables
     */
    public LyricsLayout(
        int baselineY,
        List<SyllableLayout> syllables,
        ElementBoundsSs bounds
    ) {
        this.baselineY = baselineY;
        this.syllables = List.copyOf(syllables);
        this.bounds = bounds;
    }

    /**
     * Creates empty lyrics layout.
     */
    public static LyricsLayout empty() {
        var emptyBounds = ElementBoundsSs.contentOnly(
            new java.awt.geom.Rectangle2D.Double(0, 0, 0, 0)
        );
        return new LyricsLayout(0, List.of(), emptyBounds);
    }

    /**
     * Returns the shared baseline Y coordinate for all syllables.
     */
    public int getBaselineY() {
        return baselineY;
    }

    /**
     * Returns the list of syllable layouts.
     */
    public List<SyllableLayout> getSyllables() {
        return syllables;
    }

    /**
     * Returns the combined bounds of all syllables.
     */
    public ElementBoundsSs getBounds() {
        return bounds;
    }

    /**
     * Returns whether there are any syllables.
     */
    public boolean hasSyllables() {
        return !syllables.isEmpty();
    }

    /**
     * Returns the number of syllables.
     */
    public int getSyllableCount() {
        return syllables.size();
    }

    /**
     * Returns the syllable at the given index, or null if out of bounds.
     */
    @Nullable
    public SyllableLayout getSyllable(int index) {
        if (index >= 0 && index < syllables.size()) {
            return syllables.get(index);
        }

        return null;
    }

    /**
     * Returns the syllable for the given element index, or null if none.
     */
    @Nullable
    public SyllableLayout getSyllableForElement(int elementIndex) {
        return syllables.stream()
            .filter(s -> s.getElementIndex() == elementIndex)
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns whether the given point is within any syllable's hit testing bounds.
     */
    public boolean containsPoint(double xSs, double ySs) {
        return syllables.stream().anyMatch(s -> s.containsPoint(xSs, ySs));
    }

    @Override
    public String toString() {
        return "LyricsLayout{" +
            "baselineY=" + baselineY +
            ", syllables=" + syllables.size() +
            "}";
    }
}
