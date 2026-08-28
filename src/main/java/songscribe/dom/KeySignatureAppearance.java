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
 * A key signature, whose appearance is a property of the element rather than of its type.
 *
 * <p>This arm deliberately carries nothing. A key signature is drawn as a row of sharps or
 * flats whose count and placement come from the {@link Key} the element instance holds, so
 * the type cannot name a glyph or a width; the answer is "ask the element".
 */
public record KeySignatureAppearance() implements ElementAppearance {}
