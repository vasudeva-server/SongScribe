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
import java.util.Comparator;
import java.util.List;

import songscribe.Strings;
import songscribe.dom.ElementLocation;
import songscribe.dom.RepeatSections;
import songscribe.dom.Song;

/**
 * The check of {@link LintRule#BALANCED_REPEATS}: every repeat section is opened once and
 * closed once, by repeats, and no section opens inside another. What opens, closes and nests a
 * section is {@link RepeatSections}' rule, which playback reads too.
 *
 * <p>Each violation carries the heading {@link Strings#LINT_REPEATS_UNBALANCED} and the mark
 * {@link LintMark.Flagged} on the offending bar:
 *
 * <ul>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NOT_OPENED} on each of
 *       {@link RepeatSections#unopenedClosers()}.</li>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NESTED} on each of
 *       {@link RepeatSections#nestedOpeners()}.</li>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NOT_CLOSED} on each of
 *       {@link RepeatSections#unclosedOpeners()}.</li>
 * </ul>
 */
final class BalancedRepeatsCheck implements LintCheck {

    private static final Comparator<ElementLocation> SONG_ORDER =
        Comparator.comparingInt(ElementLocation::lineIndex).thenComparingInt(ElementLocation::elementIndex);

    /**
     * Finds every unbalanced repeat in {@code song}.
     *
     * @param song the song to examine; read only
     * @return the violations, in the song order of the bars they flag
     * @invariant a balanced song, and a song with no repeat, gives no violation
     * @invariant every violation's mark is a {@link LintMark.Flagged} on a bar for which
     *     {@code ElementType.closesRepeat()} or {@code ElementType.opensRepeatSection()} holds
     */
    @Override
    public List<Violation> check(Song song) {
        var sections = RepeatSections.of(song);
        var flags = new ArrayList<Flag>();
        addFlags(flags, sections.unopenedClosers(), Strings.LINT_REASON_REPEAT_NOT_OPENED);
        addFlags(flags, sections.nestedOpeners(), Strings.LINT_REASON_REPEAT_NESTED);
        addFlags(flags, sections.unclosedOpeners(), Strings.LINT_REASON_REPEAT_NOT_CLOSED);

        // A stable sort, so two flags on one :|: keep the order they were added in.
        flags.sort(Comparator.comparing(Flag::location, SONG_ORDER));

        return flags.stream().map(flag -> flag.toViolation(song)).toList();
    }

    /** One bar to flag, with the {@code Strings} key of the reason. */
    private record Flag(ElementLocation location, String reasonKey) {

        Violation toViolation(Song song) {
            var element = song.getLine(location.lineIndex()).getElement(location.elementIndex());

            return new Violation(Strings.get(Strings.LINT_REPEATS_UNBALANCED),
                                 Strings.get(reasonKey),
                                 new LintMark.Flagged(element));
        }
    }

    private static void addFlags(List<Flag> flags, List<ElementLocation> locations, String reasonKey) {
        for (var location : locations) {
            flags.add(new Flag(location, reasonKey));
        }
    }
}
