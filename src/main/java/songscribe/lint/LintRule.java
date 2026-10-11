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
 * <p>A lint rule only reads the song and never changes it. That is what separates it from a
 * modification rule such as the tie rule in {@code songscribe.dom}, which repairs the song at
 * the close of an edit and reports what it changed. A lint rule repairs nothing: it reports
 * what is wrong and leaves the fix to the user.
 *
 * <p>{@link Linter} runs the rules in declaration order, so the declaration order is the order
 * of the findings in a {@link LintReport}. A new constant runs without any change to the linter.
 */
public enum LintRule {

    /** Every repeat section is opened and closed exactly once, and none is nested. */
    BALANCED_REPEATS(new BalancedRepeatsCheck(), EnumSet.of(Flag.BLOCKS_SAVE, Flag.BLOCKS_PLAYBACK)),

    /** Every slot carries a lyric in every verse, or an open lyric chain covers it. */
    MISSING_LYRIC(new MissingLyricCheck(), EnumSet.of(Flag.BLOCKS_SAVE)),

    /** Every Fine stands on a bar, before the end, with a later reference to it, and the reverse. */
    UNBALANCED_FINE(new UnbalancedFineCheck(), EnumSet.of(Flag.BLOCKS_SAVE));

    /**
     * What a finding of a rule prevents the user from doing until the finding is fixed.
     */
    public enum Flag {

        /** Every save is refused: a song with this finding is never written to a file. */
        BLOCKS_SAVE,

        /**
         * Play is disabled while the playback settings play repeats, because the song's repeats
         * cannot be followed. With repeats off, or while playback already runs, Play is not
         * affected.
         */
        BLOCKS_PLAYBACK
    }

    private final LintCheck check;
    private final Set<Flag> flags;

    LintRule(LintCheck check, EnumSet<Flag> flags) {
        this.check = check;
        this.flags = flags;
    }

    /**
     * Whether a finding of this rule blocks what {@code flag} names.
     *
     * @return {@code true} when this rule carries {@code flag}
     */
    public boolean hasFlag(Flag flag) {
        return flags.contains(flag);
    }

    /**
     * Finds every breach of this rule in {@code song}.
     *
     * @param song the song to examine; read only, never changed
     * @return the violations, in song order
     */
    List<Violation> check(Song song) {
        return check.check(song);
    }
}
