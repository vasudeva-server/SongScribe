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
 * Result of validating a first-second ending selection. Encodes whether
 * validation passed and the final span bounds.
 */
public final class EndingValidationResult {

    private static final EndingValidationResult INVALID = new EndingValidationResult(false, 0, 0);

    private final boolean valid;
    private final int spanStart;
    private final int spanEnd;

    private EndingValidationResult(
        boolean valid,
        int spanStart,
        int spanEnd
    ) {
        this.valid = valid;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
    }

    public static EndingValidationResult invalid() {
        return INVALID;
    }

    public static EndingValidationResult valid(
        int spanStart,
        int spanEnd
    ) {
        return new EndingValidationResult(true, spanStart, spanEnd);
    }

    public boolean isValid() {
        return valid;
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }
}
