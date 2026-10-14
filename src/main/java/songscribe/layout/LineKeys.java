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
 * The three keys a horizontal solve reads off a line, so a solve can be run against keys the
 * document does not hold. They travel together because changing one line's key moves all three;
 * projecting one while reading the others off the unedited document would measure a line that will
 * never exist.
 *
 * @param nextRunningKey null when the line has no line after it and so warns about nothing
 */
public record LineKeys(Key headerKey, Key keyAtEndOfLine, @Nullable Key nextRunningKey) {

    public static LineKeys of(Line line) {
        return new LineKeys(line.getRunningKey(), line.keyAtEndOfLine(), line.nextLineRunningKey());
    }
}
