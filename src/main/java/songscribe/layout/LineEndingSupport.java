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

import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/** Checks the effect of replacing an element on {@link Ending} spans in a {@link Line}. */
public final class LineEndingSupport {

    private LineEndingSupport() {}

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

        return line.findEndings().stream()
            .map(e -> e.checkReplacement(oldElement, newElement, line))
            .filter(e -> !(e instanceof Ending.EndingEffect.None))
            .findFirst()
            .orElse(Ending.EndingEffect.None.INSTANCE);
    }
}
