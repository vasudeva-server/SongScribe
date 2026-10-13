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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * The repeat sections of a song: which bar opens the section each right repeat closes, and
 * which repeat bars are left unmatched. It is the one model of repeat sections. Playback reads
 * it to find where a right repeat loops back to, the section rule of {@link Ending} reads it to
 * judge an ending's split, and the lint rule for balanced repeats reads it to report the
 * unmatched bars, so the three agree by construction.
 *
 * <p>The model reads the song's elements in song order, line by line, each line's elements in
 * order, the terminal included. An <em>opener</em> is a bar that
 * {@link ElementType#opensRepeatSection() opens a section} ({@code |:} or {@code :|:}); a
 * <em>right repeat</em> is a bar that {@link ElementType#closesRepeat() closes one and plays
 * back} ({@code :|} or {@code :|:}); a <em>closing bar</em> is any other bar that
 * {@link ElementType#closesRepeatSection() ends a section} without playing back ({@code ||}, a
 * final barline). The rules:
 *
 * <ul>
 *   <li>A line break neither opens nor closes a section: a section may open any number of
 *       lines before the right repeat that closes it.</li>
 *   <li>The song start opens a section, but only for the first right repeat before any opener
 *       or closing bar. In {@code A :| B :|} the first {@code :|} closes the section the song
 *       start opens, and the second is a closer with no open section. An opener or a closing
 *       bar ends the song-start section without any finding.</li>
 *   <li>A right repeat closes the innermost open section. With none open, it is a closer with
 *       no open section.</li>
 *   <li>A {@code ||} closes every open section, as a final barline does. In {@code A || B :|}
 *       the {@code :|} has no open section, and in {@code |: A || B} the {@code |:} is never
 *       closed.</li>
 *   <li>A {@code :|:} first closes the open section as a right repeat, then opens a new one.
 *       {@code |: A :|: B :|} is two sections. At the start of the song it closes the section
 *       the song start opens, so {@code A :|: B :|} is two sections and leaves nothing
 *       unmatched.</li>
 *   <li>A song whose first element is a {@code :|} or a {@code :|:} — a state only a deletion
 *       can leave, and which the {@code LEADING_BAR} lint rule flags ({@link SongStart}) — is
 *       read by the same rules. That bar closes the section the song start opens, which holds
 *       no element: it is a {@link Section} with a {@code null} opener, it is not unmatched, and
 *       its {@link #sectionStart} is the bar itself. A {@code :|:} there then opens a new section
 *       as anywhere else.</li>
 *   <li>An opener that comes while a section is already open is nested; it still opens a
 *       section, which the next right repeat closes. In {@code |: |: :| :|} only the second
 *       {@code |:} is nested: the first {@code :|} closes the inner section, and the last
 *       {@code :|} closes the outer one and is paired with the first {@code |:}.</li>
 *   <li>An opener whose section a closing bar or the end of the song ends first is never
 *       closed, unless it is nested: an opener already reported as nested is not also reported
 *       as never closed.</li>
 * </ul>
 *
 * <p>A caller builds one with {@link #of(Song)}, from the song as it is at that moment. The
 * model is a snapshot, not a view: it does not follow later edits, and nothing caches it. A
 * caller that asks several questions in one pass builds one model and keeps it for that pass
 * only.
 *
 * @invariant every right repeat of the song is the closer of exactly one {@link Section} or
 *            one of {@link #unopenedClosers()}, never both
 * @invariant every opener of the song that is not one of {@link #nestedOpeners()} is the
 *            opener of exactly one {@link Section} or one of {@link #unclosedOpeners()}, never
 *            both
 * @invariant every opener in {@link #nestedOpeners()} is the opener of at most one
 *            {@link Section}, and never one of {@link #unclosedOpeners()}
 * @invariant every list is in song order
 */
public final class RepeatSections {

    /**
     * One repeat section: the bar that opened it and the right repeat that closed it.
     *
     * @param opener the location of the {@code |:} or {@code :|:} that opened the section, or
     *               {@code null} when the song start opened it
     * @param closer the location of the {@code :|} or {@code :|:} that closed it
     */
    public record Section(@Nullable ElementLocation opener, ElementLocation closer) {
    }

    private final List<Section> sections;
    private final List<ElementLocation> unopenedClosers;
    private final List<ElementLocation> unclosedOpeners;
    private final List<ElementLocation> nestedOpeners;

    /** The start of the innermost section open just before each element; absent when none is. */
    private final Map<ElementLocation, ElementLocation> sectionStarts;

    private RepeatSections(Walk walk) {
        sections = List.copyOf(walk.sections);
        unopenedClosers = List.copyOf(walk.unopenedClosers);
        unclosedOpeners = List.copyOf(walk.unclosedOpeners);
        nestedOpeners = List.copyOf(walk.nestedOpeners);
        sectionStarts = Map.copyOf(walk.sectionStarts);
    }

    /**
     * Builds the repeat sections of {@code song} as it is now.
     *
     * @param song the song to read; read only, never changed
     * @return a new model of the song's repeat sections, which later edits to {@code song} do
     *     not change
     */
    public static RepeatSections of(Song song) {
        var walk = new Walk();
        var lines = song.getLines();

        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            var elements = lines.get(lineIndex).getElements();

            for (var elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                walk.visit(elements.get(elementIndex).getType(), new ElementLocation(lineIndex, elementIndex));
            }
        }

        walk.endAll();

        return new RepeatSections(walk);
    }

    /**
     * The sections a right repeat closes.
     *
     * @return every closed section, in the song order of its closer
     */
    public List<Section> sections() {
        return sections;
    }

    /**
     * The right repeats that find no open section to close, as the second {@code :|} of
     * {@code A :| B :|} does.
     *
     * @return their locations, in song order
     */
    public List<ElementLocation> unopenedClosers() {
        return unopenedClosers;
    }

    /**
     * The openers whose section a closing bar or the end of the song ends before any right
     * repeat closes it, leaving out the openers in {@link #nestedOpeners()}.
     *
     * @return their locations, in song order
     */
    public List<ElementLocation> unclosedOpeners() {
        return unclosedOpeners;
    }

    /**
     * The openers that come while a section opened by an earlier opener is still open. The
     * section the song start opens does not make an opener nested.
     *
     * @return their locations, in song order
     */
    public List<ElementLocation> nestedOpeners() {
        return nestedOpeners;
    }

    /**
     * The first element of the repeat section that is open just before {@code location}: the
     * element a {@code :|} at {@code location} plays back from, and the passage an ending split
     * there repeats.
     *
     * @param location an element of the song this model was built from, named by its line
     *     index and element index; it must exist
     * @return the element just after the innermost open section's {@code |:} or {@code :|:},
     *     which may be on an earlier line, or the song's first element when the section the
     *     song start opens is still open; {@code null} when no section is open just before
     *     {@code location}
     * @invariant only elements strictly before {@code location} decide the answer: the element
     *     at {@code location} never opens or closes the section it asks about
     * @invariant when the opener is the last element of its line, the answer is the first
     *     element of the next line that has elements
     * @invariant the song's first element is element 0 of {@link Song#firstNonEmptyLineIndex()},
     *     so a leading empty line is skipped
     * @invariant a non-null answer is never after {@code location}; it equals {@code location}
     *     exactly when the opener, or the song start, is immediately before it
     */
    public @Nullable ElementLocation sectionStart(ElementLocation location) {
        return sectionStarts.get(location);
    }

    /** One open section on the walk's stack. */
    private static final class OpenSection {

        /** The opener's location, or {@code null} for the section the song start opens. */
        private final @Nullable ElementLocation opener;
        private final boolean nested;

        /** The element just after the opener, once the walk has reached it. */
        private @Nullable ElementLocation start = null;

        OpenSection(@Nullable ElementLocation opener, boolean nested) {
            this.opener = opener;
            this.nested = nested;
        }

        boolean opensAtSongStart() {
            return opener == null;
        }
    }

    /** The state of the forward walk that builds a model. */
    private static final class Walk {

        private final Deque<OpenSection> open = new ArrayDeque<>();
        private final List<Section> sections = new ArrayList<>();
        private final List<ElementLocation> unopenedClosers = new ArrayList<>();
        private final List<ElementLocation> unclosedOpeners = new ArrayList<>();
        private final List<ElementLocation> nestedOpeners = new ArrayList<>();
        private final Map<ElementLocation, ElementLocation> sectionStarts = new HashMap<>();

        Walk() {
            open.push(new OpenSection(null, false));
        }

        void visit(ElementType type, ElementLocation location) {
            recordSectionStart(location);

            if (type.closesRepeat()) {
                closeInnermost(location);
            } else if (type.closesRepeatSection()) {
                endAll();
            }

            if (type.opensRepeatSection()) {
                openAt(location);
            }
        }

        /**
         * Records the start of the innermost open section as the answer for {@code location}.
         * Only the section opened just before {@code location} can lack a start, and
         * {@code location} is its start.
         */
        private void recordSectionStart(ElementLocation location) {
            var innermost = open.peek();

            if (innermost == null) {
                return;
            }

            var start = innermost.start;

            if (start == null) {
                start = location;
                innermost.start = start;
            }

            sectionStarts.put(location, start);
        }

        private void closeInnermost(ElementLocation closer) {
            var innermost = open.poll();

            if (innermost == null) {
                unopenedClosers.add(closer);
            } else {
                sections.add(new Section(innermost.opener, closer));
            }
        }

        private void openAt(ElementLocation opener) {
            var innermost = open.peek();

            if (innermost != null && innermost.opensAtSongStart()) {
                open.pop();
            }

            var nested = !open.isEmpty();

            if (nested) {
                nestedOpeners.add(opener);
            }

            open.push(new OpenSection(opener, nested));
        }

        /** Ends every open section, at a closing bar or at the end of the song. */
        void endAll() {
            var outermostFirst = open.descendingIterator();

            while (outermostFirst.hasNext()) {
                var section = outermostFirst.next();
                var opener = section.opener;

                if (opener != null && !section.nested) {
                    unclosedOpeners.add(opener);
                }
            }

            open.clear();
        }
    }
}
