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
package songscribe.dom;

/**
 * The beat in effect at a position, together with the position of the event that
 * defined it. Callers use {@code lineIndex}/{@code elementIndex} to recognize a beat
 * barrier — an element that redefines the beat — inside a span they are examining.
 *
 * <p>When the beat comes from the song's own tempo or from the quarter-note default,
 * no element defined it and both indexes are {@link #NO_DEFINING_EVENT}.
 *
 * @see TempoResolver#resolveBeatAt
 */
public record BeatAt(Duration beat, int lineIndex, int elementIndex) {

    /** Index value meaning "no element defined this beat". */
    public static final int NO_DEFINING_EVENT = -1;
}
