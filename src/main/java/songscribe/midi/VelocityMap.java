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
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;

/**
 * Pre-computed MIDI velocity map for a song. Velocities are derived
 * from point dynamic markings ({@link DynamicAttachment}) on notes, with
 * forward propagation within and across lines.
 */
public final class VelocityMap {

    public static final int MAX_VELOCITY = 127;

    static final double DEFAULT_VELOCITY_FRACTION = DynamicType.MEZZO_FORTE.getVelocityFraction();

    static final int ACCENT_BOOST = 30;

    private final int[][] velocities;

    private VelocityMap(int[][] velocities) {
        this.velocities = velocities;
    }

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

                var dynamic = element.findAttachment(DynamicAttachment.class);

                if (dynamic != null) {
                    lastFraction = dynamic.getType().getVelocityFraction();
                }

                var baseVelocity = (int) Math.round(masterVelocity * lastFraction);

                if (element instanceof NoteElement note && note.hasArticulation(ArticulationType.ACCENT)) {
                    baseVelocity = Math.min(baseVelocity + ACCENT_BOOST, MAX_VELOCITY);
                }

                velocities[lineIndex][noteIndex] = baseVelocity;
            }
        }

        return new VelocityMap(velocities);
    }

    public int getVelocity(int lineIndex, int noteIndex) {
        return velocities[lineIndex][noteIndex];
    }
}
