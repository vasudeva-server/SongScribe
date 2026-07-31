/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.selection;

import songscribe.dom.Hairpin;
import songscribe.layout.Ending;

/**
 * The decoration selected on a line, if any.
 * <p>
 * A slide, an ending, and a hairpin are each selected on their own, mutually exclusive
 * with one another and with any element or line selection. One field of this type holds
 * whichever is selected, so exclusivity is a property of the data — assigning a new
 * decoration replaces the old one, and two cannot coexist.
 * <p>
 * Adding a variant here makes every {@code switch} over it fail to compile until it is
 * handled, which is the point: the consumers that must learn about a new decoration type
 * announce themselves instead of being found by hand.
 */
public sealed interface SelectedDecoration {

    /**
     * The slide owned by the element at {@code elementIndex}.
     * <p>
     * Carries the index rather than the {@code Slide} itself because a slide is an
     * attribute of its element, and removing it means asking the element to drop it.
     */
    record SlideSelection(int elementIndex) implements SelectedDecoration {}

    record EndingSelection(Ending ending) implements SelectedDecoration {}

    record HairpinSelection(Hairpin hairpin) implements SelectedDecoration {}
}
