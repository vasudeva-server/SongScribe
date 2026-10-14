/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.midi;

/**
 * The listener's choices for one playback, applied by {@link MidiSequenceBuilder}.
 *
 * @param instrument          the General MIDI program number, 0 to 127
 * @param noteDurationPercent the share of its written duration a note sounds for, 1 to 100,
 *                            unless the note carries its own override
 * @param playWithRepeats     when {@code false}, repeat marks play as ordinary bars and every
 *                            first ending is skipped
 */
public record PlaybackSettings(
    int instrument,
    int tempoChangePercent,
    int noteDurationPercent,
    boolean playWithRepeats
) {}
