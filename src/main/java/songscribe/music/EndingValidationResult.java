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
package songscribe.music;

/**
 * Result of validating a first-second ending selection. Encodes whether
 * validation passed, what action is needed for the preceding element,
 * and the final interval bounds.
 */
public class EndingValidationResult {

    public enum PrecedingAction {
        /** No adjustment needed (preceding element is already suitable). */
        NONE,
        /** A single barline must be inserted at the interval start. */
        INSERT_BARLINE,
        /** The interval start must be extended backward to include the preceding element. */
        EXTEND_INTERVAL
    }

    private static final EndingValidationResult INVALID = new EndingValidationResult(
        false, PrecedingAction.NONE, 0, 0
    );

    private final boolean valid;
    private final PrecedingAction precedingAction;
    private final int intervalStart;
    private final int intervalEnd;

    private EndingValidationResult(
        boolean valid,
        PrecedingAction precedingAction,
        int intervalStart,
        int intervalEnd
    ) {
        this.valid = valid;
        this.precedingAction = precedingAction;
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalEnd;
    }

    public static EndingValidationResult invalid() {
        return INVALID;
    }

    public static EndingValidationResult valid(
        PrecedingAction precedingAction,
        int intervalStart,
        int intervalEnd
    ) {
        return new EndingValidationResult(true, precedingAction, intervalStart, intervalEnd);
    }

    public boolean isValid() {
        return valid;
    }

    public PrecedingAction getPrecedingAction() {
        return precedingAction;
    }

    public int getIntervalStart() {
        return intervalStart;
    }

    public int getIntervalEnd() {
        return intervalEnd;
    }
}
