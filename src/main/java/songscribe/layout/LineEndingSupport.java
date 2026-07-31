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

package songscribe.layout;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/** Static helpers for querying {@link Ending} elements on a {@link Line}. */
public final class LineEndingSupport {

    private LineEndingSupport() {}

    /** Returns all {@link Ending} range elements on {@code line}. */
    public static List<Ending> findEndings(Line line) {
        return line.findRangeElements(Ending.class);
    }

    /** Returns the {@link Ending} that spans {@code elementIndex}, or null if none. */
    public static @Nullable Ending findEndingAt(List<? extends Ending> endings, int elementIndex) {
        for (var ending : endings) {
            var start = ending.getAnchorElementIndex();
            var end = ending.getEndElementIndex();

            if (elementIndex >= start && elementIndex <= end) {
                return ending;
            }
        }

        return null;
    }

    /** Returns the {@link Ending} that spans {@code elementIndex}, or null if none. */
    public static @Nullable Ending findEndingAt(Line line, int elementIndex) {
        return findEndingAt(findEndings(line), elementIndex);
    }

    /** Returns true if {@code elementIndex} falls inside any ending. */
    public static boolean isInsideAnyEnding(List<Ending> endings, int elementIndex) {
        return findEndingAt(endings, elementIndex) != null;
    }

    /** Returns true if {@code elementIndex} falls inside any ending on {@code line}. */
    public static boolean isInsideAnyEnding(Line line, int elementIndex) {
        return isInsideAnyEnding(findEndings(line), elementIndex);
    }

    /** Returns true if {@code elementIndex} is the anchor of any ending. */
    public static boolean isStartOfAnyEnding(List<? extends Ending> endings, int elementIndex) {
        for (var ending : endings) {
            if (ending.getAnchorElementIndex() == elementIndex) {
                return true;
            }
        }

        return false;
    }

    /** Returns true if {@code elementIndex} is the anchor of any ending on {@code line}. */
    public static boolean isStartOfAnyEnding(Line line, int elementIndex) {
        return isStartOfAnyEnding(findEndings(line), elementIndex);
    }

    /** Returns true if {@code elementIndex} is the end of any ending. */
    public static boolean isEndOfAnyEnding(List<? extends Ending> endings, int elementIndex) {
        for (var ending : endings) {
            if (ending.getEndElementIndex() == elementIndex) {
                return true;
            }
        }

        return false;
    }

    /** Returns true if {@code elementIndex} is the end of any ending on {@code line}. */
    public static boolean isEndOfAnyEnding(Line line, int elementIndex) {
        return isEndOfAnyEnding(findEndings(line), elementIndex);
    }

    /**
     * Returns the effect of replacing the element at {@code index} with {@code newElement}
     * on any ending in {@code line}. Returns {@link Ending.EndingEffect.None} if no ending
     * is affected.
     * <p>
     * Call before {@link Line#setElement} to determine whether a confirmation dialog is needed.
     */
    public static Ending.EndingEffect findEndingReplacementEffect(
        Line line, int index, StaffElement newElement
    ) {
        var oldElement = line.getElement(index);

        return findEndings(line).stream()
            .map(e -> e.checkReplacement(oldElement, newElement, line))
            .filter(e -> !(e instanceof Ending.EndingEffect.None))
            .findFirst()
            .orElseGet(() -> Ending.EndingEffect.None.INSTANCE);
    }
}
