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

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * An edit to one line's elements that has not happened yet: the elements it removes from the
 * line, and the elements it puts in their place.
 *
 * <p>A gated edit walks the accidental chains over the song as the edit will leave it before
 * anything commits, and that walk reads two descriptions of the one edit — the song with the
 * line's elements swapped, and where on the line the swap stands. Both are derived here from the
 * live line and the swap, so the walk is never handed a projection of one line and an edit on
 * another.
 *
 * <p>Built before the edit and read before it commits: the removed elements and the projected
 * list are taken from the line when this is built.
 */
public final class ProjectedEdit {

    private final Line line;
    private final int index;
    private final List<StaffElement> removed;
    private final List<StaffElement> inserted;
    private final List<StaffElement> elements;

    private ProjectedEdit(
        Line line, int index, List<StaffElement> removed, List<? extends StaffElement> inserted) {

        this.line = line;
        this.index = index;
        this.removed = List.copyOf(removed);
        this.inserted = List.copyOf(inserted);

        var projected = new ArrayList<StaffElement>(line.getElements());
        projected.subList(index, index + removed.size()).clear();
        projected.addAll(index, inserted);

        elements = List.copyOf(projected);
    }

    /**
     * The edit that inserts {@code inserted} into {@code line} at {@code index}, removing nothing.
     *
     * @param line     the line the edit lands on; one of its song's lines
     * @param index    the index the first inserted element will stand at, in
     *                 {@code 0..line.elementCount()}
     * @param inserted the elements to insert, in line order
     * @return the projected edit
     */
    public static ProjectedEdit inserting(Line line, int index, List<? extends StaffElement> inserted) {
        return new ProjectedEdit(line, index, List.of(), inserted);
    }

    /**
     * The edit that replaces the elements of {@code line} in {@code replaced} with
     * {@code inserted}, which need not be as many.
     *
     * @param line     the line the edit lands on; one of its song's lines
     * @param replaced the inclusive range of {@code line}'s elements the edit removes
     * @param inserted the elements standing from {@code replaced.begin()} once the edit lands, in
     *                 line order
     * @return the projected edit
     */
    public static ProjectedEdit replacing(
        Line line, ElementRange replaced, List<? extends StaffElement> inserted) {

        return new ProjectedEdit(
            line, replaced.begin(), line.getElements(replaced), inserted);
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
     * Walks every accidental chain the edit reaches, over the song as the edit will leave it.
     * Touches no note.
     *
     * @return the restamps {@link AccidentalChain#walk} decides for the edit
     */
    public List<AccidentalChain.Restamp> walk() {
        var lineIndex = line.index();

        return AccidentalChain.walk(
            new AccidentalScanView.Projected(line.getSong(), lineIndex, elements),
            ChainEdit.replaced(lineIndex, index, removed, inserted));
    }
}
