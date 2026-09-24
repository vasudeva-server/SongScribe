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
 * The editor's verdict on whether a first-second ending can be made from the current
 * selection. A valid verdict carries the ending itself, so the one who acts on it adds what
 * validation built rather than rebuilding it from indices.
 */
public sealed interface EndingValidationResult
    permits EndingValidationResult.Invalid, EndingValidationResult.Valid {

    /** No ending can be made from the selection. */
    enum Invalid implements EndingValidationResult { INSTANCE }

    /**
     * An ending can be made from the selection.
     *
     * @param line   the line the selection is on
     * @param ending an ending built over {@code line}'s elements and not yet added to its span
     *     registry
     */
    record Valid(Line line, Ending ending) implements EndingValidationResult {}
}
