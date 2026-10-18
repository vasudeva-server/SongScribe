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
 * A document that holds a stranded key change: a mid-line key change that restates the key
 * already in effect. The model cannot represent one, so the document cannot be read.
 */
public final class StrandedKeyChangeException extends Exception {

    private final int lineNumber;

    StrandedKeyChangeException(int lineNumber) {
        super("line " + lineNumber + " has a key change that restates the key already in effect");
        this.lineNumber = lineNumber;
    }

    /**
     * @return 1-based: the first line of the song that holds a stranded key change
     */
    public int lineNumber() {
        return lineNumber;
    }
}
