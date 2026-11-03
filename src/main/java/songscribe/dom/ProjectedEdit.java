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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * An edit to one line's elements that has not happened yet: the elements it removes from the
 * line, the elements it puts in their place, and the key the line starts in once it lands.
 *
 * <p>The key the line starts in is part of the edit because an edit can move it
 * ({@link #movingLineKeyTo}).
 *
 * <p>Built before the edit and read before it commits: the removed elements and the projected
 * list are taken from the line when this is built.
 */
public final class ProjectedEdit {

    private final class Projected implements AccidentalScanView {

        private final Song song = line.getSong();
        private final int lineIndex = line.index();

        @Override
        public int lineCount() {
            return song.lineCount();
        }

        @Override
        public Line line(int index) {
            return song.getLine(index);
        }

        @Override
        public StaffElementRun run(int index) {
            return (index == lineIndex) ? run : song.getLine(index);
        }

        @Override
        public @Nullable Key ownKey(int index) {
            return (index == lineIndex) ? projectedOwnKey() : song.getLine(index).getKey();
        }

        @Override
        public Key startKey(int index) {
            if (index < lineIndex) {
                return song.getLine(index).getRunningKey();
            }

            if (index == lineIndex) {
                return ProjectedEdit.this.startKey;
            }

            var ownKey = ownKey(index);

            if (ownKey != null) {
                return ownKey;
            }

            var previousIndex = index - 1;
            return keyBefore(new Cursor(previousIndex, run(previousIndex).elementCount()));
        }

        private @Nullable Key projectedOwnKey() {
            return movesKey() ? line.normalizedOwnKey(startKey) : line.getKey();
        }
    }

    private final class ProjectedRun implements StaffElementRun {

        @Override
        public StaffElement getElement(int elementIndex) {
            return elements.get(elementIndex);
        }

        @Override
        public int elementCount() {
            return elements.size();
        }

        @Override
        public int effectiveElementCount() {
            return elements.size() - (line.elementCount() - line.effectiveElementCount());
        }

        /**
         * The song-start slot depends only on the lines before this one, and the edit changes
         * none of them.
         */
        @Override
        public boolean isSongStartAt(int elementIndex) {
            return line.isSongStartAt(elementIndex);
        }
    }

    private final Line line;
    private final int index;
    private final List<StaffElement> removed;
    private final List<StaffElement> inserted;
    private final List<StaffElement> elements;
    private final Key startKey;
    private final StaffElementRun run = new ProjectedRun();

    private ProjectedEdit(
        Line line, int index, List<StaffElement> removed, List<? extends StaffElement> inserted) {

        this.line = line;
        this.index = index;
        this.removed = List.copyOf(removed);
        this.inserted = List.copyOf(inserted);
        startKey = line.getRunningKey();

        var projected = new ArrayList<StaffElement>(line.getElements());
        projected.subList(index, index + removed.size()).clear();
        projected.addAll(index, inserted);

        elements = List.copyOf(projected);
    }

    private ProjectedEdit(ProjectedEdit edit, Key startKey) {
        line = edit.line;
        index = edit.index;
        removed = edit.removed;
        inserted = edit.inserted;
        elements = edit.elements;
        this.startKey = startKey;
    }

    /**
     * @param line  one of its song's lines
     * @param index in {@code 0..line.elementCount()}
     */
    public static ProjectedEdit inserting(Line line, int index, List<? extends StaffElement> inserted) {
        return new ProjectedEdit(line, index, List.of(), inserted);
    }

    /**
     * @param line one of its song's lines
     */
    public static ProjectedEdit replacing(Line line, ElementRange replaced, List<? extends StaffElement> inserted) {
        return new ProjectedEdit(line, replaced.begin(), line.getElements(replaced), inserted);
    }

    /**
     * @param key the key the edit gives the line as its own when it lands
     * @return this edit, with its line starting in {@code key}
     */
    public ProjectedEdit movingLineKeyTo(Key key) {
        return new ProjectedEdit(this, key);
    }

    public Line line() {
        return line;
    }

    public int index() {
        return index;
    }

    public List<StaffElement> removed() {
        return removed;
    }

    public List<StaffElement> inserted() {
        return inserted;
    }

    public List<StaffElement> elements() {
        return elements;
    }

    public Key startKey() {
        return startKey;
    }

    /**
     * As {@link StaffElementRun#keyBefore(int, Key)} over {@link #elements()}, with the line
     * starting in {@link #startKey()}.
     *
     * @param elementIndex an insertion index, {@code 0..elements().size()}
     */
    public Key keyBefore(int elementIndex) {
        return run.keyBefore(elementIndex, startKey);
    }

    public Key keyAtEndOfLine() {
        return keyBefore(elements.size());
    }

    /**
     * @return null when the line is its song's last line
     */
    public @Nullable Key nextLineStartKey() {
        return line.isLast() ? null : new Projected().startKey(line.index() + 1);
    }

    /**
     * Touches no note.
     */
    public List<AccidentalChain.Restamp> walk() {
        var start = new Cursor(line.index(), index);
        var edit = movesKey()
            ? ChainEdit.lineRekeyedAndReplaced(start, removed, inserted)
            : ChainEdit.replaced(start, removed, inserted);

        return AccidentalChain.walk(new Projected(), edit);
    }

    private boolean movesKey() {
        return !startKey.equals(line.getRunningKey());
    }
}
