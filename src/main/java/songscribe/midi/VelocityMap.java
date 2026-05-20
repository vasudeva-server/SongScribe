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

package songscribe.midi;

import songscribe.dom.ArticulationType;
import songscribe.dom.Song;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;

/**
 * Pre-computed MIDI velocity map for a song. Velocities are derived
 * from point dynamic markings ({@link DynamicAttachment}) on notes, with
 * forward propagation within and across lines.
 * <p>
 * The map is built in a single O(n) forward pass before playback starts
 * and looked up per-note during MIDI sequence generation.
 */
public final class VelocityMap {

    /** Maximum MIDI velocity (the ceiling for ff at full master volume). */
    public static final int MAX_VELOCITY = 127;

    /** Default velocity fraction when no dynamic marking is present (mf). */
    static final double DEFAULT_VELOCITY_FRACTION = DynamicType.MEZZO_FORTE.getVelocityFraction();

    /** Accent boost (fixed velocity addition). */
    static final int ACCENT_BOOST = 30;

    private final int[][] velocities;

    private VelocityMap(int[][] velocities) {
        this.velocities = velocities;
    }

    /**
     * Builds a velocity map for the given song.
     *
     * @param song     the song to map
     * @param masterVelocity  the ceiling velocity (typically {@link #MAX_VELOCITY})
     * @return a fully computed velocity map
     */
    public static VelocityMap build(Song song, int masterVelocity) {
        var lines = song.getLines();
        var velocities = new int[lines.size()][];
        var lastFraction = DEFAULT_VELOCITY_FRACTION;

        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            var line = lines.get(lineIndex);
            var noteCount = line.effectiveElementCount();
            velocities[lineIndex] = new int[noteCount];

            for (var noteIndex = 0; noteIndex < noteCount; noteIndex++) {
                var element = line.getElement(noteIndex);

                // Update fraction if this note has a dynamic marking
                var dynamic = element.findAttachment(DynamicAttachment.class);

                if (dynamic != null) {
                    lastFraction = dynamic.getType().getVelocityFraction();
                }

                var baseVelocity = (int) Math.round(masterVelocity * lastFraction);

                // Apply accent boost
                if (element.hasArticulation(ArticulationType.ACCENT)) {
                    baseVelocity = Math.min(baseVelocity + ACCENT_BOOST, MAX_VELOCITY);
                }

                velocities[lineIndex][noteIndex] = baseVelocity;
            }
        }

        return new VelocityMap(velocities);
    }

    /**
     * Returns the pre-computed MIDI velocity for a note.
     *
     * @param lineIndex  the line index in the song
     * @param noteIndex  the element index within the line
     * @return MIDI velocity (1–127)
     */
    public int getVelocity(int lineIndex, int noteIndex) {
        return velocities[lineIndex][noteIndex];
    }
}
