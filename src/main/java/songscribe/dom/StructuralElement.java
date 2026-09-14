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
 * A non-sounding element: a rest, a barline, a repeat, a breath mark or a key change. Has no
 * pitch, and so none of the state a pitch brings with it: no staff position, no accidental, no
 * stem, no slide. Each leaf holds the appearance arm it is drawn from — {@link RestElement} for a
 * rest, which sits at its own seat and takes dots; {@link BreathMarkElement} for a breath mark,
 * which sits at its own seat and takes nothing; {@link BarElement}; {@link KeyChangeElement} —
 * and answers from that arm what its own kind can answer.
 */
public abstract sealed class StructuralElement extends StaffElement
    permits RestElement, BreathMarkElement, BarElement, KeyChangeElement {

    protected StructuralElement(ElementType type) {
        super(type);
    }
}
