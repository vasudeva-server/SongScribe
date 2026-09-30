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

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;

/** Reads a built MIDI {@link Sequence} for a test. */
final class MidiSequences {

    private MidiSequences() {}

    /**
     * The pitch of every note that sounds in {@code sequence}, track by track and in each track's
     * order. A note-on with no velocity is a note-off and is not a note that sounds.
     *
     * @param sequence the sequence to read
     * @return the pitch of each sounding note, in the order the tracks hold them
     */
    static List<Integer> noteOnPitches(Sequence sequence) {
        var pitches = new ArrayList<Integer>();

        for (var track : sequence.getTracks()) {
            for (var i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof ShortMessage message
                        && message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0) {
                    pitches.add(message.getData1());
                }
            }
        }

        return pitches;
    }
}
