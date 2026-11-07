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

package songscribe.lint;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import songscribe.dom.Song;

/**
 * A rule the song as it is now must keep. Every breach is an error: there is no severity, and
 * what a breach blocks comes from the rule's {@link Flag flags}.
 *
 * <p>A lint rule only reads the song. Declaration order is the order of the findings on one
 * element in a {@link LintReport}.
 */
public enum LintRule {

    /** Every repeat section is opened and closed exactly once, and none is nested. Blocks playback, which cannot follow broken repeats. */
    BALANCED_REPEATS(new BalancedRepeatsCheck(), EnumSet.of(Flag.BLOCKS_OUTPUT, Flag.BLOCKS_PLAYBACK)),

    /** Every slot carries a lyric in the active script, or an open lyric chain covers it. */
    MISSING_LYRIC(new MissingLyricCheck(), EnumSet.of(Flag.BLOCKS_OUTPUT)),

    /** Every Fine stands on a bar, before the end, with a later reference to it, and the reverse. */
    UNBALANCED_FINE(new UnbalancedFineCheck(), EnumSet.of(Flag.BLOCKS_OUTPUT));

    /** What a finding prevents until it is fixed. */
    public enum Flag {

        /**
         * Until the finding is fixed, the song cannot be printed or exported, and a save needs the
         * user's consent.
         */
        BLOCKS_OUTPUT,

        /**
         * Until the finding is fixed, the song cannot start to play with repeats. A playback that
         * already runs is not stopped.
         */
        BLOCKS_PLAYBACK
    }

    private final LintCheck check;
    private final Set<Flag> flags;

    LintRule(LintCheck check, EnumSet<Flag> flags) {
        this.check = check;
        this.flags = flags;
    }

    public boolean hasFlag(Flag flag) {
        return flags.contains(flag);
    }

    List<Violation> check(Song song) {
        return check.check(song);
    }
}
