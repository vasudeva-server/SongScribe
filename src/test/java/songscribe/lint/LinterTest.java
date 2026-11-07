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

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order of {@link LintReport#findings()} after {@link Linter#lint()}: song order across
 * rules, and the declaration order of the rules for findings on one element.
 */
class LinterTest extends UnitTest {

    /** A finding as a test reads it: its rule, and where its element sits in song order. */
    private record Placed(LintRule rule, int position) {
    }

    @Test
    void testFindingsOfDifferentRulesComeInSongOrder() {
        // Both notes lack a lyric, and the second :| closes no section; BALANCED_REPEATS is declared
        // before MISSING_LYRIC, so declaration order would list it first.
        var song = LintSongs.song("N", "N :| :|");

        assertThat(placedFindings(song)).containsExactly(
            new Placed(LintRule.MISSING_LYRIC, 0),
            new Placed(LintRule.MISSING_LYRIC, 1),
            new Placed(LintRule.BALANCED_REPEATS, 3));
    }

    @Test
    void testFindingsOnOneElementComeInRuleDeclarationOrder() {
        // The second :| closes no section and carries a Fine that nothing refers to.
        var song = LintSongs.song("N :| :|@Fine N");

        assertThat(placedFindings(song)).containsExactly(
            new Placed(LintRule.MISSING_LYRIC, 0),
            new Placed(LintRule.BALANCED_REPEATS, 2),
            new Placed(LintRule.UNBALANCED_FINE, 2),
            new Placed(LintRule.MISSING_LYRIC, 3));
    }

    private static List<Placed> placedFindings(Song song) {
        var linter = song.getLinter();
        linter.lint();

        return linter.getReport()
                     .findings()
                     .stream()
                     .map(finding -> new Placed(finding.rule(), LintSongs.positionOf(song, finding.mark().element())))
                     .toList();
    }
}
