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
 * validation passed, what action is needed for the preceding element,
 * and the final span bounds.
 */
public final class EndingValidationResult {

    /**
     * How the span start relates to the preceding element.
     * <p>
     * Since issue #306 removed the auto-inserted barline, {@code NONE} and
     * {@code EXTEND_SPAN} produce identical behavior in
     * {@code MusicEditOperations.makeFirstSecondEnding} — both anchor at the
     * pre-computed span bounds with no element insertion. The distinction is
     * retained because it still describes <em>why</em> a span is valid (note anchor
     * vs. backward-extended onto a preceding barline/repeat) and is asserted by the
     * validation tests; production code no longer branches on it.
     */
    public enum PrecedingAction {
        /** No adjustment needed (preceding element is already suitable). */
        NONE,
        /** The span start must be extended backward to include the preceding element. */
        EXTEND_SPAN
    }

    private static final EndingValidationResult INVALID = new EndingValidationResult(
        false, PrecedingAction.NONE, 0, 0
    );

    private final boolean valid;
    private final PrecedingAction precedingAction;
    private final int spanStart;
    private final int spanEnd;

    private EndingValidationResult(
        boolean valid,
        PrecedingAction precedingAction,
        int spanStart,
        int spanEnd
    ) {
        this.valid = valid;
        this.precedingAction = precedingAction;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
    }

    public static EndingValidationResult invalid() {
        return INVALID;
    }

    public static EndingValidationResult valid(
        PrecedingAction precedingAction,
        int spanStart,
        int spanEnd
    ) {
        return new EndingValidationResult(true, precedingAction, spanStart, spanEnd);
    }

    public boolean isValid() {
        return valid;
    }

    public PrecedingAction getPrecedingAction() {
        return precedingAction;
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }
}
