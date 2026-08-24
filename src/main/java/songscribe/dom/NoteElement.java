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

/**
 * A sounding element: a note, with a pitch, a stem, an optional accidental and everything else
 * {@link StaffElement} holds. The one leaf that answers {@link StaffElement}'s questions as they
 * are asked, where {@link StructuralElement} answers several of them from its type instead.
 *
 * <p>Adds no state of its own — a note is exactly what {@link StaffElement} describes — which is
 * why its {@link #copySubtypeStateFrom} is empty. It exists so that {@link StaffElement} can be
 * abstract, and every element's class can therefore follow its {@link ElementType}.
 */
public final class NoteElement extends StaffElement {

    /**
     * @param type the note's type; a type for which {@link ElementType#isNote()} is true
     */
    public NoteElement(ElementType type) {
        super(type);
    }

    @Override
    public NoteElement clone() {
        var copy = new NoteElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }

    /** A note carries no state beyond {@link StaffElement#copyStateFrom}'s field list. */
    @Override
    protected void copySubtypeStateFrom(StaffElement source) {
    }
}
