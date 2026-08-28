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

import songscribe.smufl.Notehead;

/**
 * A note, drawn from a notehead.
 *
 * <p>The notehead rather than its glyph is what is carried, because a note's bounds and
 * its stem attachment point are both asked for by notehead: only the stemmed arm of
 * {@link Notehead} has stem anchors, and carrying the glyph alone would throw that
 * distinction away.
 *
 * @param notehead the notehead the note is drawn with
 */
public record NoteheadAppearance(Notehead notehead) implements ElementAppearance {}
