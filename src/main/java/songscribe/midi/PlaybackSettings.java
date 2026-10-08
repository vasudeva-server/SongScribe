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
 * @param tempoChangePercent  the song's tempi scaled by this percentage: 100 plays them as
 *                            written, 120 plays 20% faster
 * @param noteDurationPercent the share of its written duration a note sounds for, 1 to 100,
 *                            unless the note carries its own override
 * @param playWithRepeats     how repeat marks and endings play. When {@code true}, a
 *                            {@code :|} or {@code :|:} sends playback back to the start of the
 *                            section it closes, which may lie on an earlier line, and the
 *                            second time through it plays on past the mark; a {@code :|} that
 *                            closes no open section plays as an ordinary bar; and on the second
 *                            time through, a first ending is skipped in favor of the second.
 *                            When {@code false}, the song plays straight through once, repeat
 *                            marks play as ordinary bars, and every first ending is skipped, so
 *                            only the common section and the second ending sound.
 */
public record PlaybackSettings(
    int instrument,
    int tempoChangePercent,
    int noteDurationPercent,
    boolean playWithRepeats
) {}
