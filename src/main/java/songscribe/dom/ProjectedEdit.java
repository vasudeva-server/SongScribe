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
 * <p>The key the line starts in is part of the edit because an edit can move it; an edit that
 * moves none states the key the line starts in now.
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
        public int elementCount(int index) {
            return (index == lineIndex) ? elements.size() : song.getLine(index).elementCount();
        }

        @Override
        public StaffElement elementAt(int index, int elementIndex) {
            return (index == lineIndex) ? elements.get(elementIndex) : song.getLine(index).getElement(elementIndex);
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
            return keyBefore(new Cursor(previousIndex, elementCount(previousIndex)));
        }

        private @Nullable Key projectedOwnKey() {
            return movesKey() ? startKey : line.getKey();
        }
    }

    private final Line line;
    private final int index;
    private final List<StaffElement> removed;
    private final List<StaffElement> inserted;
    private final List<StaffElement> elements;
    private final Key startKey;

    private ProjectedEdit(
        Line line, int index, List<StaffElement> removed, List<? extends StaffElement> inserted, Key startKey) {

        this.line = line;
        this.index = index;
        this.removed = List.copyOf(removed);
        this.inserted = List.copyOf(inserted);
        this.startKey = startKey;

        var projected = new ArrayList<StaffElement>(line.getElements());
        projected.subList(index, index + removed.size()).clear();
        projected.addAll(index, inserted);

        elements = List.copyOf(projected);
    }

    /**
     * @param line     one of its song's lines
     * @param index    in {@code 0..line.elementCount()}
     * @param startKey {@code line}'s {@link Line#getRunningKey() running key} for an edit that
     *                 moves no key
     */
    public static ProjectedEdit inserting(
        Line line, int index, List<? extends StaffElement> inserted, Key startKey) {

        return new ProjectedEdit(line, index, List.of(), inserted, startKey);
    }

    /**
     * @param line     one of its song's lines
     * @param startKey {@code line}'s {@link Line#getRunningKey() running key} for an edit that
     *                 moves no key
     */
    public static ProjectedEdit replacing(
        Line line, ElementRange replaced, List<? extends StaffElement> inserted, Key startKey) {

        return new ProjectedEdit(
            line, replaced.begin(), line.getElements(replaced), inserted, startKey);
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
