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
 * Defines the vertical stacking order for layout elements.
 * <p>
 * Elements are processed and rendered in the order defined here, from bottom to top.
 * This enum establishes the layering hierarchy for the line layout engine.
 */
public enum LayoutLayer {

    /** Note elements (head, stem, flag, ledger lines) - bottom layer */
    NOTE,

    /** Tie curves connecting notes */
    TIE,

    /** Articulations (staccato, then accent) */
    ARTICULATION,

    /** Tuplet markings (triplets, etc.) */
    TUPLET,

    /** Trill markings */
    TRILL,

    /** Fermata symbols */
    FERMATA,

    /** Dynamic markings (non-range and range) */
    DYNAMICS,

    /** First/second ending brackets */
    ENDING,

    /** Tempo and beat change markings */
    TEMPO,

    /** Text annotations */
    ANNOTATION,

    /** Attribution text */
    ATTRIBUTION,

    /** Lyrics (below staff) */
    LYRICS
}
