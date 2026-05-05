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
 * Defines the vertical stacking order for line elements (top to bottom).
 * <p>
 * Elements with lower order values are drawn higher on the page (further from staff).
 * Elements with higher order values are drawn lower on the page (closer to/below staff).
 * <p>
 * The staff and note heads are the reference point (order 8). Elements above
 * the staff have lower order values; elements below have higher values.
 */
public enum VerticalOrder {

    /** Tempo marking (e.g., "Allegro ♩= 120") - topmost above staff */
    TEMPO(1),

    /** Beat/time signature change */
    BEAT_CHANGE(2),

    /** First/second endings (volta brackets) */
    ENDINGS(3),

    /** Text annotations above staff */
    ANNOTATIONS_ABOVE(4),

    /** Trill marking */
    TRILL(5),

    /** Range elements (ties/tuplets) when placed above notes */
    RANGE_ELEMENTS_ABOVE(6),

    /** Articulations on stem-down notes (above note head) */
    ARTICULATIONS_STEM_DOWN(7),

    /** Note stems and heads - baseline reference */
    NOTE_STEM(8),

    /** Articulations on stem-up notes (below note head) */
    ARTICULATIONS_STEM_UP(9),

    /** Range elements (ties/tuplets) when placed below notes */
    RANGE_ELEMENTS_BELOW(10),

    /** Dynamic markings (p, f, ff, etc.) */
    DYNAMICS(11),

    /** Lyrics syllables */
    LYRICS(12),

    /** Translation text (below lyrics) */
    TRANSLATION(13);

    private final int order;

    VerticalOrder(int order) {
        this.order = order;
    }

    /**
     * Returns the numeric order value for sorting.
     * Lower values are drawn higher (further above staff).
     */
    public int getOrder() {
        return order;
    }

    /**
     * Returns whether this element is above the staff/notes.
     */
    public boolean isAboveStaff() {
        return order < NOTE_STEM.order;
    }

    /**
     * Returns whether this element is below the staff/notes.
     */
    public boolean isBelowStaff() {
        return order > NOTE_STEM.order;
    }

    /**
     * Compares two vertical orders for sorting.
     * Returns negative if this should be drawn above (higher on page).
     */
    public int compareByOrder(VerticalOrder other) {
        return Integer.compare(order, other.order);
    }
}
