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

/**
 * Types of articulations that can be applied to notes.
 * <p>
 * Articulations are drawn opposite the stem, outward from the staff:
 * <ul>
 *   <li>For downward stems: articulations go above the staff</li>
 *   <li>For upward stems: articulations go below the staff</li>
 * </ul>
 */
public enum ArticulationType {

    /**
     * Staccato - short, detached notes.
     * Drawn closest to the note head.
     */
    STACCATO(33),

    /**
     * Accent - emphasized notes.
     * Drawn outside of staccato when both are present.
     */
    ACCENT(-1);

    /** MIDI duration as a percentage of the note's full duration, or -1 for no override. */
    private final int midiDurationPercent;

    ArticulationType(int midiDurationPercent) {
        this.midiDurationPercent = midiDurationPercent;
    }

    /**
     * Returns the MIDI duration as a percentage of the note's full duration,
     * or -1 if this articulation does not override duration.
     */
    public int getMidiDurationPercent() {
        return midiDurationPercent;
    }

    /**
     * Returns true if this articulation overrides the note's MIDI duration.
     */
    public boolean hasMidiDurationOverride() {
        return midiDurationPercent >= 0;
    }
}
