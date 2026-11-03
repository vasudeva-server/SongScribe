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

import java.util.ArrayList;
import java.util.List;

import songscribe.Strings;
import songscribe.dom.ElementLocation;
import songscribe.dom.RepeatSections;
import songscribe.dom.Song;

/**
 * The check of {@link LintRule#BALANCED_REPEATS}: every repeat section is opened once and
 * closed once, by repeats, and no section opens inside another. What opens, closes and nests a
 * section is {@link RepeatSections}' rule.
 *
 * <p>Each violation carries the heading {@link LintHeading#REPEATS_UNBALANCED} and the mark
 * {@link LintMark.Flagged} on the offending bar:
 *
 * <ul>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NOT_OPENED} on each closer with no open section.</li>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NESTED} on each opener inside an open section.</li>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NOT_CLOSED} on each opener never closed.</li>
 * </ul>
 */
final class BalancedRepeatsCheck implements LintCheck {

    /**
     * Finds every unbalanced repeat in {@code song}.
     *
     * @param song the song to examine; read only
     * @return the violations
     * @invariant a balanced song, and a song with no repeat, gives no violation
     * @invariant every violation's mark is a {@link LintMark.Flagged} on a bar for which
     *     {@code ElementType.closesRepeat()} or {@code ElementType.opensRepeatSection()} holds
     */
    @Override
    public List<Violation> check(Song song) {
        var sections = RepeatSections.of(song);
        var violations = new ArrayList<Violation>();
        addViolations(violations, song, sections.unopenedClosers(), Strings.LINT_REASON_REPEAT_NOT_OPENED);
        addViolations(violations, song, sections.nestedOpeners(), Strings.LINT_REASON_REPEAT_NESTED);
        addViolations(violations, song, sections.unclosedOpeners(), Strings.LINT_REASON_REPEAT_NOT_CLOSED);

        return violations;
    }

    private static void addViolations(List<Violation> violations, Song song, List<ElementLocation> locations, String reasonKey) {
        for (var location : locations) {
            violations.add(new Violation(LintHeading.REPEATS_UNBALANCED,
                                         Strings.get(reasonKey),
                                         new LintMark.Flagged(song.elementAt(location))));
        }
    }
}
