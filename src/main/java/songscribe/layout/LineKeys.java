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

package songscribe.layout;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Key;
import songscribe.dom.Line;

/**
 * The three keys a horizontal solve reads off a line, gathered so a solve can be run against keys
 * the document does not hold — an edit measured before it is committed.
 * <p>
 * A solve of a line as it stands takes {@link #of(Line)}. A pre-check states instead what the edit
 * would produce, which is why all three components travel together: changing one line's key moves
 * its header, the key it leaves off in, and the key the line after it begins in, and a check that
 * projected one of those while reading the other two off the unedited document would measure a
 * line that will never exist.
 *
 * @param headerKey      the key the line's header draws, which fixes where its first element sits
 *                       ({@link HorizontalSpacingCalculator#calculateFirstElementXSs})
 * @param keyAtEndOfLine the key the line leaves off in — the left-hand side of any cautionary key
 *                       signature drawn at its end
 * @param nextRunningKey the key the following line begins in, or null when the line has no line
 *                       after it and so warns about nothing
 */
public record LineKeys(Key headerKey, Key keyAtEndOfLine, @Nullable Key nextRunningKey) {

    /**
     * Returns the keys {@code line} holds right now — what the committed layout solves against.
     *
     * @param line the line to read
     * @return its header key, the key it leaves off in, and the key the next line begins in
     */
    public static LineKeys of(Line line) {
        return new LineKeys(line.getRunningKey(), line.keyAtEndOfLine(), line.nextLineRunningKey());
    }
}
