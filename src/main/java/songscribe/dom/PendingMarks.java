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

import java.util.List;

/**
 * The marks a reader found in a file and left for the load stages to judge and place.
 */
public record PendingMarks(
    List<PendingTuplet> tuplets,
    List<PendingEnding> endings,
    List<PendingHairpin> hairpins,
    List<PendingBeam> beams,
    List<PendingTrill> trills,
    List<PendingTie> ties
) {

    public PendingMarks {
        tuplets = List.copyOf(tuplets);
        endings = List.copyOf(endings);
        hairpins = List.copyOf(hairpins);
        beams = List.copyOf(beams);
        trills = List.copyOf(trills);
        ties = List.copyOf(ties);
    }
}
