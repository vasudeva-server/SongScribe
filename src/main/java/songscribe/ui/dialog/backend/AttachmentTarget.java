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
package songscribe.ui.dialog.backend;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * The element an attachment dialog edits, together with the line holding it.
 *
 * <p>The two travel as one because every commit needs both: the element carries the attachment,
 * while the line owns the modification bracket and resolves the element's index for the mutation
 * record. Binding them separately is what allowed a stale element to reach
 * {@code Line.modifyElement(-1, …)} — a hard failure at OK, at the far end of a modal dialog,
 * rather than a refusal at the gesture that opened it.
 *
 * <p><strong>Invariant: {@link #element()} is an element of {@link #line()}.</strong> Established
 * at construction and relied on by {@link #elementIndex()}, so a target that could only fail later
 * cannot be built at all.
 *
 * @param line    the line holding {@code element}
 * @param element the element whose attachment is being edited
 */
public record AttachmentTarget(Line line, StaffElement element) {

    /**
     * @throws IllegalArgumentException if {@code element} is not an element of {@code line}
     */
    public AttachmentTarget {
        if (line.getElementIndex(element) < 0) {
            throw new IllegalArgumentException("element is not in the given line");
        }
    }

    /**
     * The target for {@code element} in {@code line}, when there is one.
     *
     * <p>The non-throwing door to the class invariant, for the callers that must decide whether to
     * open a dialog at all rather than assert that they may. Every such check goes through here,
     * so the rule for what makes a target valid is stated in one place.
     *
     * @param line    the line the element is expected to be in
     * @param element the candidate element; {@code null} is accepted and answers {@code null},
     *                since a selection that resolved to nothing is the common way to reach this
     * @return the target, or {@code null} when {@code element} is null or not in {@code line}
     */
    public static @Nullable AttachmentTarget forElement(Line line, @Nullable StaffElement element) {
        if (element == null || line.getElementIndex(element) < 0) {
            return null;
        }

        return new AttachmentTarget(line, element);
    }

    /**
     * The element's position in the line, as {@code Line}'s element-modification helpers index it.
     *
     * <p>Re-read on each call rather than captured at construction, so an insertion or deletion
     * elsewhere in the line cannot leave this answering a stale position.
     *
     * @return the index, never negative — the class invariant guarantees the element is present
     */
    public int elementIndex() {
        return line.getElementIndex(element);
    }
}
