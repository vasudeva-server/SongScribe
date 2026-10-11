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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * The check of {@link LintRule#BALANCED_REPEATS}: every repeat section is opened once and
 * closed once, by repeats, and no section opens inside another.
 *
 * <p>A <em>left repeat</em> is a bar that opens a repeat section ({@code |:} or {@code :|:}); a
 * <em>right repeat</em> is a bar that closes one and plays back ({@code :|} or {@code :|:}); a
 * <em>closer</em> is any other bar that ends a section without playing back ({@code ||}, a final
 * barline). The elements are read in song order, across every line; a line break neither opens
 * nor closes a section. The semantics agree with {@code Song.repeatSectionStart}, which answers
 * where playback loops back to:
 *
 * <ul>
 *   <li>The song start opens a section, but only for the first right repeat before any left
 *       repeat or closer. In {@code A :| B :|} the first {@code :|} balances with the song
 *       start and the second has no opener.</li>
 *   <li>A closer closes the open section, including the song-start section. In
 *       {@code A || B :|} the {@code :|} has no opener.</li>
 *   <li>A {@code :|:} first closes the open section as a right repeat, then opens a new one.
 *       {@code |: A :|: B :|} is balanced.</li>
 * </ul>
 *
 * <p>Each violation carries the mark {@link LintMark.Flagged} on the offending bar:
 *
 * <ul>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NOT_OPENED} on a right repeat that no open section,
 *       and not the song start, lets it close.</li>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NESTED} on a left repeat that comes while a section is
 *       already open. In {@code |: |: :| :|} the second {@code |:} is flagged; it is taken to
 *       open an inner section, which the first {@code :|} closes, so the second {@code :|}
 *       closes the outer one and is not flagged as well.</li>
 *   <li>{@link Strings#LINT_REASON_REPEAT_NOT_CLOSED} on a left repeat whose section no right
 *       repeat closes: a closer or the end of the song ends it first. In {@code |: A :|: B} the
 *       {@code :|:} is flagged. A left repeat already flagged as nested is not flagged again.</li>
 * </ul>
 */
final class BalancedRepeatsCheck implements LintCheck {

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
        return new Pass().run(song);
    }

    /** One open left repeat, with whether it was already flagged as nested. */
    private record OpenSection(StaffElement opener, int position, boolean nested) {
    }

    /** The state of one forward pass over a song. */
    private static final class Pass {

        private final Deque<OpenSection> open = new ArrayDeque<>();
        private final SongOrderedViolations found = new SongOrderedViolations();
        private boolean songStartOpen = true;

        List<Violation> run(Song song) {
            LintCheck.walk(song, this::visit);
            closeAll();
            return found.inSongOrder();
        }

        private void visit(StaffElement element, int position) {
            var type = element.getType();

            if (type.closesRepeat()) {
                closeWithRightRepeat(element, position);
            } else if (type.closesRepeatSection()) {
                closeAll();
                songStartOpen = false;
            }

            if (type.opensRepeatSection()) {
                var nested = !open.isEmpty();

                if (nested) {
                    flag(element, position, Strings.LINT_REASON_REPEAT_NESTED);
                }

                open.push(new OpenSection(element, position, nested));
                songStartOpen = false;
            }
        }

        private void closeWithRightRepeat(StaffElement element, int position) {
            if (!open.isEmpty()) {
                open.pop();
            } else if (!songStartOpen) {
                flag(element, position, Strings.LINT_REASON_REPEAT_NOT_OPENED);
            }

            songStartOpen = false;
        }

        private void closeAll() {
            for (var section : open) {
                if (!section.nested()) {
                    flag(section.opener(), section.position(), Strings.LINT_REASON_REPEAT_NOT_CLOSED);
                }
            }

            open.clear();
        }

        private void flag(StaffElement element, int position, String reasonKey) {
            found.add(position, new Violation(
                Strings.LINT_REPEATS_UNBALANCED, Strings.get(reasonKey), new LintMark.Flagged(element)));
        }
    }
}
