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
 * <p>A gated edit walks the accidental chains and measures the line's fit over the song as the
 * edit will leave it before anything commits, and both read the same descriptions of the one
 * edit — the song with the line's elements swapped, where on the line the swap stands, and the
 * key the line starts in. All are derived here from the live line and the edit, and the
 * projected view exists nowhere else, so the walk and the measurement are never handed a
 * projection of one line and an edit on another.
 *
 * <p>The key the line starts in is part of the edit because an edit can move it: a paste at the
 * start of the song drops a leading bar line together with the key change behind it, and that
 * key becomes the line's own key. Every edit states the key, and an edit that moves none states
 * the key the line starts in now.
 *
 * <p>Built before the edit and read before it commits: the removed elements and the projected
 * list are taken from the line when this is built.
 */
public final class ProjectedEdit {

    /**
     * The live song with the elements of one line replaced by what the edit will leave there,
     * and that line starting in the edit's {@link #startKey}. Every other line holds its live
     * elements and its live own key; a line after the edited one that has no own key starts in
     * the key the projected lines before it end in.
     */
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

        /**
         * The live running key before the edited line, which the edit does not touch; the edit's
         * key on the edited line; and after it, a line's own key, else the key the line before it
         * ends in under the projection.
         */
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

        /**
         * The edited line's own key as the edit leaves it: unchanged when the edit moves no key,
         * and otherwise the moved key, so that the walk entering the line rekeys to it.
         */
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
     * The edit that inserts {@code inserted} into {@code line} at {@code index}, removing nothing,
     * and leaves {@code line} starting in {@code startKey}.
     *
     * @param line     the line the edit lands on; one of its song's lines
     * @param index    the index the first inserted element will stand at, in
     *                 {@code 0..line.elementCount()}
     * @param inserted the elements to insert, in line order
     * @param startKey the key {@code line} starts in once the edit lands; {@code line}'s
     *                 {@link Line#getRunningKey() running key} for an edit that moves no key
     * @return the projected edit
     */
    public static ProjectedEdit inserting(
        Line line, int index, List<? extends StaffElement> inserted, Key startKey) {

        return new ProjectedEdit(line, index, List.of(), inserted, startKey);
    }

    /**
     * The edit that replaces the elements of {@code line} in {@code replaced} with
     * {@code inserted}, which need not be as many, and leaves {@code line} starting in
     * {@code startKey}.
     *
     * @param line     the line the edit lands on; one of its song's lines
     * @param replaced the inclusive range of {@code line}'s elements the edit removes
     * @param inserted the elements standing from {@code replaced.begin()} once the edit lands, in
     *                 line order
     * @param startKey the key {@code line} starts in once the edit lands; {@code line}'s
     *                 {@link Line#getRunningKey() running key} for an edit that moves no key
     * @return the projected edit
     */
    public static ProjectedEdit replacing(
        Line line, ElementRange replaced, List<? extends StaffElement> inserted, Key startKey) {

        return new ProjectedEdit(
            line, replaced.begin(), line.getElements(replaced), inserted, startKey);
    }

    /**
     * The line the edit lands on.
     *
     * @return the live line
     */
    public Line line() {
        return line;
    }

    /**
     * Where on the line the edit stands.
     *
     * @return the index the first removed element stands at now, and the first inserted element
     *     stands at once the edit lands
     */
    public int index() {
        return index;
    }

    /**
     * The line's elements the edit removes.
     *
     * @return the removed elements, in line order, taken when this was built; unmodifiable
     */
    public List<StaffElement> removed() {
        return removed;
    }

    /**
     * The elements the edit puts in place of {@link #removed()}.
     *
     * @return the inserted elements, in line order; unmodifiable
     */
    public List<StaffElement> inserted() {
        return inserted;
    }

    /**
     * The elements the line will hold once the edit lands.
     *
     * @return the projected elements, in line order; unmodifiable
     */
    public List<StaffElement> elements() {
        return elements;
    }

    /**
     * The key the line starts in once the edit lands.
     *
     * @return the key stated when this was built
     */
    public Key startKey() {
        return startKey;
    }

    /**
     * Walks every accidental chain the edit reaches, over the song as the edit will leave it.
     * Touches no note.
     *
     * <p>An edit that moves the key the line starts in is walked as
     * {@link ChainEdit#lineRekeyedAndReplaced}: from the line's start to the next key change, as a
     * key set on the line by hand is, because every note up to there is decided against the moved
     * key.
     *
     * @return the restamps {@link AccidentalChain#walk} decides for the edit, naming each note at
     *     most once
     */
    public List<AccidentalChain.Restamp> walk() {
        var start = new Cursor(line.index(), index);
        var edit = movesKey()
            ? ChainEdit.lineRekeyedAndReplaced(start, removed, inserted)
            : ChainEdit.replaced(start, removed, inserted);

        return AccidentalChain.walk(new Projected(), edit);
    }

    /** Whether the edit leaves the line starting in a key other than the one it starts in now. */
    private boolean movesKey() {
        return !startKey.equals(line.getRunningKey());
    }
}
