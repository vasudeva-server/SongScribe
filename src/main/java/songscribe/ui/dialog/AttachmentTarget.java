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
package songscribe.ui.dialog;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * The element an attachment dialog edits, together with the line holding it.
 *
 * <p><strong>Invariant: {@link #element()} is an element of {@link #line()}.</strong> Established
 * at construction and relied on by {@link #elementIndex()}.
 */
public record AttachmentTarget(Line line, StaffElement element) {

    public AttachmentTarget {
        if (!line.contains(element)) {
            throw new IllegalArgumentException("element is not in the given line");
        }
    }

    /**
     * @param element the candidate element; {@code null} is accepted and answers {@code null}
     * @return the target, or {@code null} when {@code element} is null or not in {@code line}
     */
    public static @Nullable AttachmentTarget forElement(Line line, @Nullable StaffElement element) {
        if (element == null || !line.contains(element)) {
            return null;
        }

        return new AttachmentTarget(line, element);
    }

    /**
     * Re-read on each call, so an insertion or deletion elsewhere in the line cannot leave it
     * stale.
     */
    public int elementIndex() {
        return line.getElementIndex(element);
    }
}
