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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link Tuplet#outcomeFor} under a replacement (issue #726), which decides whether
 * replacing one element with another leaves a tuplet bracket rhythmically invalid. The rule moved
 * here from a hand-written sweep in the preview-element click path, so that every caller of
 * {@link Line#setElement} gets it rather than only that one.
 *
 * <p>Layout — a triplet over three notes, with a barline inside it that contributes no time, and
 * a fourth note outside it:
 * <pre>
 *  idx:  0      1              2      3      4
 *        QUAVER SINGLE_BARLINE QUAVER QUAVER QUAVER
 *        (anchor)              ·····  (end)  (outside)
 * </pre>
 */
class TupletInvalidationTest extends UnitTest {

    /** Index of the triplet's anchor note. */
    private static final int ANCHOR_INDEX = 0;

    /** Index of the barline inside the triplet, which contributes no written time. */
    private static final int INNER_BARLINE_INDEX = 1;

    /** Index of a plain note inside the triplet, between the anchor and the end. */
    private static final int INNER_NOTE_INDEX = 2;

    /** Index of the triplet's end note. */
    private static final int END_INDEX = 3;

    /** Index of the note just past the triplet. */
    private static final int OUTSIDE_INDEX = 4;

    /** Number of notes under the triplet. */
    private static final int TRIPLET_SIZE = 3;

    /** A dot count other than the undotted quavers the layout lays down. */
    private static final int ONE_DOT = 1;

    private Song song;
    private Line line;
    private Tuplet triplet;

    @BeforeEach
    void buildLine() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.QUAVER.newInstance());
            line.addElement(ElementType.SINGLE_BARLINE.newInstance());
            line.addElement(ElementType.QUAVER.newInstance());
            line.addElement(ElementType.QUAVER.newInstance());
            line.addElement(ElementType.QUAVER.newInstance());
        });

        triplet = Tuplet.withUnresolvedRatio(
            line.getElement(ANCHOR_INDEX), line.getElement(END_INDEX), TRIPLET_SIZE);
        song.withoutMutationTracking(() -> line.addTuplet(triplet));
    }

    /**
     * The outcome {@code tuplet} reports for replacing the element at {@code index} of
     * {@link #line} with {@code replacement}, judged against the projected line.
     */
    private SpanOutcome outcomeForReplacing(Tuplet tuplet, int index, StaffElement replacement) {
        return tuplet.outcomeFor(
            ElementChange.forReplacement(line, index, replacement), line);
    }

    /** Replaces the element at {@code index} with a fresh, undotted {@code type}. */
    private SpanOutcome outcomeForReplacing(int index, ElementType type) {
        return outcomeForReplacing(triplet, index, type.newInstance());
    }

    // -----------------------------------------------------------------------
    // Duration-carrying elements inside the bracket
    // -----------------------------------------------------------------------

    @Test
    void testInnerNoteChangingValueInvalidates() {
        assertThat(outcomeForReplacing(INNER_NOTE_INDEX, ElementType.CROTCHET))
            .as("a longer note under the bracket unbalances the group")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @Test
    void testInnerNoteGainingDotInvalidates() {
        var replacement = ElementType.QUAVER.newInstance();
        replacement.setDotCount(ONE_DOT);

        assertThat(outcomeForReplacing(triplet, INNER_NOTE_INDEX, replacement))
            .as("a dot lengthens the note, so the group no longer adds up")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @Test
    void testInnerNoteBecomingRestInvalidates() {
        // Same written duration, but a tuplet needs non-rest notes to bracket
        // (Tuplet.hasValidSpan), so this goes too rather than being second-guessed here.
        assertThat(outcomeForReplacing(INNER_NOTE_INDEX, ElementType.QUAVER_REST))
            .as("a rest where the group needs a note drops the bracket")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @Test
    void testInnerNoteReplacedByAnIdenticalNoteRetains() {
        assertThat(outcomeForReplacing(INNER_NOTE_INDEX, ElementType.QUAVER))
            .as("nothing about the group's rhythm changed")
            .isSameAs(SpanOutcome.Simple.KEEP);
    }

    @Test
    void testEndpointChangingValueInvalidates() {
        assertThat(outcomeForReplacing(END_INDEX, ElementType.CROTCHET))
            .as("the bracket's own end note is as much part of the group as its middle")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    // -----------------------------------------------------------------------
    // Elements that carry no written time
    // -----------------------------------------------------------------------

    @Test
    void testInnerBarlineBecomingRepeatRetains() {
        assertThat(outcomeForReplacing(INNER_BARLINE_INDEX, ElementType.REPEAT_LEFT))
            .as("neither a barline nor a repeat takes time the group is built on")
            .isSameAs(SpanOutcome.Simple.KEEP);
    }

    @Test
    void testInnerBarlineBecomingNoteInvalidates() {
        assertThat(outcomeForReplacing(INNER_BARLINE_INDEX, ElementType.QUAVER))
            .as("a note where there was no duration adds time the bracket never covered")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @Test
    void testInnerNoteBecomingBarlineInvalidates() {
        assertThat(outcomeForReplacing(INNER_NOTE_INDEX, ElementType.SINGLE_BARLINE))
            .as("the group loses a note it was counting")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    // -----------------------------------------------------------------------
    // Outside the bracket, and an unresolvable endpoint
    // -----------------------------------------------------------------------

    @Test
    void testReplacementOutsideTupletRetains() {
        assertThat(outcomeForReplacing(OUTSIDE_INDEX, ElementType.CROTCHET))
            .as("the note past the end is no part of the group")
            .isSameAs(SpanOutcome.Simple.KEEP);
    }

    @Test
    void testDetachedAnchorRetains() {
        // A tuplet whose anchor was never put in the line resolves the anchor to -1. Without
        // the guard, every index up to the end would read as inside the bracket.
        var detached = Tuplet.withUnresolvedRatio(
            ElementType.QUAVER.newInstance(), line.getElement(END_INDEX), TRIPLET_SIZE);
        song.withoutMutationTracking(() -> line.addTuplet(detached));

        assertThat(outcomeForReplacing(detached, INNER_NOTE_INDEX, ElementType.CROTCHET.newInstance()))
            .as("a tuplet with an unresolvable anchor contains nothing")
            .isSameAs(SpanOutcome.Simple.KEEP);
    }

    // -----------------------------------------------------------------------
    // Line.setElement wiring
    // -----------------------------------------------------------------------

    @Test
    void testSetElementDropsTheInvalidatedTuplet() {
        song.withoutMutationTracking(
            () -> line.setElement(INNER_NOTE_INDEX, ElementType.CROTCHET.newInstance()));

        assertThat(line.findSpans(Tuplet.class))
            .as("setElement must drop the tuplet the replacement invalidated")
            .isEmpty();
    }

    @Test
    void testSetElementKeepsTheTupletAnUntimedSwapLeavesValid() {
        song.withoutMutationTracking(
            () -> line.setElement(INNER_BARLINE_INDEX, ElementType.REPEAT_LEFT.newInstance()));

        assertThat(line.findSpans(Tuplet.class))
            .as("swapping a barline for a repeat changes no duration, so the group stands")
            .containsExactly(triplet);
    }
}
