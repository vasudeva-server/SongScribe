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

    /**
     * No bar line or repeat stands first in the song; the song's terminal is exempt. It blocks
     * playback so that playback never meets a leading repeat, which closes a section that holds
     * nothing. A leading plain bar line blocks playback too, because one rule covers every bar
     * type.
     */
    LEADING_BAR(new LeadingBarCheck(), EnumSet.of(Flag.BLOCKS_EXPORT, Flag.BLOCKS_PLAYBACK)),

    /**
     * Every repeat section is opened and closed exactly once, and none is nested. It blocks
     * playback because playback cannot follow repeats that this rule found broken.
     */
    BALANCED_REPEATS(new BalancedRepeatsCheck(), EnumSet.of(Flag.BLOCKS_EXPORT, Flag.BLOCKS_PLAYBACK)),

    /** Every slot carries a lyric in the active script, or an open lyric chain covers it. */
    MISSING_LYRIC(new MissingLyricCheck(), EnumSet.of(Flag.BLOCKS_EXPORT)),

    /** Every Fine stands on a bar, before the end, with a later reference to it, and the reverse. */
    UNBALANCED_FINE(new UnbalancedFineCheck(), EnumSet.of(Flag.BLOCKS_EXPORT));

    /**
     * What a finding of a rule prevents the user from doing until the finding is fixed. This
     * is the one statement of what each flag blocks; the code that enforces a flag links here.
     */
    public enum Flag {

        /**
         * An export of a song with this finding is refused. A save of it asks the user for
         * confirmation first, and writes only when the user confirms: the saved file keeps the
         * work, and the finding stays for the user to fix.
         */
        BLOCKS_EXPORT,

        /**
         * Play is disabled while playback is stopped and the playback settings play repeats.
         * With repeats off, or while playback already runs, Play is not affected, so a running
         * playback can always be stopped. Why a rule carries this flag is stated on the rule.
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
     * @param flag the flag to look for
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
