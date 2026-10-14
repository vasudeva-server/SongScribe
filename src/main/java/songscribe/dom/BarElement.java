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
 * A barline or a repeat sign: a non-sounding element drawn as a sequence of strokes across the
 * staff.
 *
 * <p>{@link ElementType#newInstance()} builds one for every type whose appearance is a bar and
 * for no other, which is what makes its narrowing of the appearance sound.
 */
public final class BarElement extends StructuralElement {

    BarElement(ElementType type) {
        super(type);
    }

    public BarAppearance bar() {
        return (BarAppearance) getType().appearance();
    }

    @Override
    public BarElement clone() {
        var copy = new BarElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }
}
