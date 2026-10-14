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
 * The one model of a song's repeat sections: which bar opens the section each right repeat
 * closes, and which repeat bars are left unmatched. Playback, the section rule of {@link Ending}
 * and the lint rule for balanced repeats all read it, so they agree by construction.
 *
 * <p>An <em>opener</em> is a bar that {@link ElementType#opensRepeatSection() opens a section}
 * ({@code |:} or {@code :|:}); a <em>right repeat</em> {@link ElementType#closesRepeat() closes
 * one and plays back} ({@code :|} or {@code :|:}); a <em>closing bar</em> is any other bar that
 * {@link ElementType#closesRepeatSection() ends a section} ({@code ||}, a final barline).
 *
 * <ul>
 *   <li>A line break neither opens nor closes a section.</li>
 *   <li>The song start opens a section, but only for the first right repeat before any opener
 *       or closing bar; an opener or a closing bar ends it without any finding.</li>
 *   <li>A right repeat closes the innermost open section; with none open, it is an unopened
 *       closer.</li>
 *   <li>A {@code ||} closes every open section, as a final barline does.</li>
 *   <li>A {@code :|:} first closes the open section as a right repeat, then opens a new one.</li>
 *   <li>A song whose first element is a {@code :|} or {@code :|:} (flagged by the
 *       {@code LEADING_BAR} lint rule, {@link SongStart}) closes the song-start section: a
 *       {@link Section} with a {@code null} opener, not unmatched, whose {@link #sectionStart} is
 *       the bar itself.</li>
 *   <li>An opener that comes while a section is open is nested; it still opens a section, which
 *       the next right repeat closes.</li>
 *   <li>An opener whose section a closing bar or the end of the song ends first is never closed,
 *       unless it is nested: a nested opener is not also reported as never closed.</li>
 * </ul>
 *
 * <p>A snapshot of the song when {@link #of(Song)} built it; it does not follow later edits.
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
     * @param opener {@code null} when the song start opened the section
     */
    public record Section(@Nullable ElementLocation opener, ElementLocation closer) {
    }

    private final List<Section> sections;
    private final List<ElementLocation> unopenedClosers;
    private final List<ElementLocation> unclosedOpeners;
    private final List<ElementLocation> nestedOpeners;

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

    public List<Section> sections() {
        return sections;
    }

    public List<ElementLocation> unopenedClosers() {
        return unopenedClosers;
    }

    /**
     * Leaves out the openers in {@link #nestedOpeners()}.
     */
    public List<ElementLocation> unclosedOpeners() {
        return unclosedOpeners;
    }

    /**
     * The section the song start opens does not make an opener nested.
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

    private static final class OpenSection {

        private final @Nullable ElementLocation opener;
        private final boolean nested;

        private @Nullable ElementLocation start = null;

        OpenSection(@Nullable ElementLocation opener, boolean nested) {
            this.opener = opener;
            this.nested = nested;
        }

        boolean opensAtSongStart() {
            return opener == null;
        }
    }

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
