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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes every barline that stands first in a freshly parsed song.
 *
 * <p>The rule is the one {@link SongStart} states, and {@link SongStart#removeLeadingBars} applies
 * it. A key change behind a removed barline goes with it, and the song still sounds as the file
 * wrote it; nothing else is moved or repaired into a place the file did not say.
 */
final class LeadingBarLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(LeadingBarLoadPass.class);

    private LeadingBarLoadPass() {
    }

    /**
     * Removes each barline that stands first in {@code song}, until its first element is not a
     * barline or is its terminal, and records what it removed.
     *
     * @param song the song to settle, immediately after parsing and before it is installed
     * @invariant afterwards {@link SongStart#leadingBar} answers {@code null} for {@code song}
     * @invariant the song's terminal is never removed
     * @effects as {@link SongStart#removeLeadingBars}, reporting to the load's report
     */
    static void run(Song song) {
        var removedCount = SongStart.removeLeadingBars(song);

        if (removedCount > 0) {
            LOG.info("Leading bar load pass: {} removed", removedCount);
        }
    }
}
