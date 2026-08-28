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

package songscribe.dom;

/**
 * How an element type is drawn.
 *
 * <p>Every {@link ElementType} has exactly one appearance, supplied at its declaration,
 * so the question "what is this drawn from?" is answered by a field the compiler forces
 * every constant to fill in rather than by a lookup that can be missing an entry.
 *
 * <p>The four arms cover every drawn element kind, so a switch over an appearance is
 * exhaustive and needs no default: a new kind of drawn element is a new arm, and the
 * compiler names every switch that has to account for it.
 */
public sealed interface ElementAppearance
    permits NoteheadAppearance, GlyphAppearance, BarAppearance, KeySignatureAppearance {}
