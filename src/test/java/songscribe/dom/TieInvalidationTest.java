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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.undo.UndoTestSupport;

/**
 * Unit tests for {@link Tie#outcomeFor} under an insertion and under a replacement,
 * and their wiring through {@link Line#addElement(int, StaffElement)} and
 * {@link Line#setElement(int, StaffElement)} (issue #726). An element inserted between the two
 * tied notes removes the tie unless it is a legal separator; a replacement removes it unless the
 * incoming element leaves the tie as legal as it found it.
 *
 * <p>Adjacent layout — the two tied notes with nothing between them:
 * <pre>
 *  idx:  0        1        2        3
 *        CROTCHET CROTCHET CROTCHET FINAL_DOUBLE_BARLINE
 *        (anchor) (end)             (auto-maintained terminal)
 * </pre>
 *
 * <p>Separated layout — a barline between them, which a tie may legally straddle:
 * <pre>
 *  idx:  0        1              2        3
 *        CROTCHET SINGLE_BARLINE CROTCHET FINAL_DOUBLE_BARLINE
 *        (anchor) (separator)    (end)    (auto-maintained terminal)
 * </pre>
 */
class TieInvalidationTest extends UnitTest {

    /** Index of the tie's anchor note in both layouts. */
    private static final int ANCHOR_INDEX = 0;

    /** Index of the tie's end note in the adjacent layout — also the point between the two. */
    private static final int END_INDEX = 1;

    /** Index just past the tie's end note in the adjacent layout. */
    private static final int AFTER_END_INDEX = 2;

    /** Index of the barline between the two notes in the separated layout. */
    private static final int SEPARATOR_INDEX = 1;

    /** Index of the tie's end note in the separated layout. */
    private static final int SEPARATED_END_INDEX = 2;

    /** A staff position other than the default the built notes carry. */
    private static final int DIFFERENT_STAFF_POSITION = 2;

    private Song song;
    private Line line;

    /** The plain three-note layout the adjacent tie and the half-detached tie both sit on. */
    private static final ElementType[] THREE_NOTES = {
        ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET
    };

    /** Lays down a line of {@code types}, with mutation tracking suspended so setup emits nothing. */
    private void buildLine(ElementType... types) {
        song.withoutMutationTracking(() -> {
            for (var type : types) {
                line.addElement(type.newInstance());
            }
        });
    }

    /** Ties {@code anchor} to {@code end} and adds the tie to the line, emitting nothing. */
    private Tie tieBetween(StaffElement anchor, StaffElement end) {
        var tie = new Tie(anchor, end);
        song.withoutMutationTracking(() -> line.addTie(tie));

        return tie;
    }

    /**
     * Builds a line of {@code types} in {@code song} and ties the elements at
     * {@code anchorIndex} and {@code endIndex}.
     */
    private Tie buildTiedLine(int anchorIndex, int endIndex, ElementType... types) {
        buildLine(types);

        return tieBetween(line.getElement(anchorIndex), line.getElement(endIndex));
    }

    /** Two tied notes with nothing between them, followed by a third note. */
    private Tie buildAdjacentTie() {
        return buildTiedLine(ANCHOR_INDEX, END_INDEX, THREE_NOTES);
    }

    /**
     * A tie over the same three-note layout whose anchor was never put in the line, so the
     * anchor resolves to -1 while the end note at {@link #AFTER_END_INDEX} resolves normally.
     * Only the unresolvable-endpoint guard can decide such a tie's fate.
     */
    private Tie buildDetachedAnchorTie() {
        buildLine(THREE_NOTES);

        return tieBetween(crotchet(), line.getElement(AFTER_END_INDEX));
    }

    /** Two tied notes separated by a single barline. */
    private Tie buildSeparatedTie() {
        return buildTiedLine(ANCHOR_INDEX, SEPARATED_END_INDEX,
            ElementType.CROTCHET, ElementType.SINGLE_BARLINE, ElementType.CROTCHET);
    }

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);
    }

    /**
     * The outcome {@code tie} reports for inserting a fresh {@code insertedType} at
     * {@code index} of {@link #line}, judged against the projected line.
     */
    private SpanOutcome outcomeForInserting(Tie tie, int index, ElementType insertedType) {
        return tie.outcomeFor(
            ElementChange.forInsertion(line, index, insertedType.newInstance()), line);
    }

    /**
     * The outcome {@code tie} reports for replacing the element at {@code index} of
     * {@link #line} with {@code replacement}, judged against the projected line.
     */
    private SpanOutcome outcomeForReplacing(Tie tie, int index, StaffElement replacement) {
        return tie.outcomeFor(
            ElementChange.forReplacement(line, index, replacement), line);
    }

    // -----------------------------------------------------------------------
    // outcomeFor, insertion — adjacent tie
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenTiedNotesAreAdjacent {

        private Tie tie;

        @BeforeEach
        void addTie() {
            tie = buildAdjacentTie();
        }

        @Test
        void testInsertedNoteInvalidates() {
            assertThat(outcomeForInserting(tie, END_INDEX, ElementType.CROTCHET))
                .as("a note between the tied notes must remove the tie")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testInsertedRestInvalidates() {
            assertThat(outcomeForInserting(tie, END_INDEX, ElementType.CROTCHET_REST))
                .as("a rest between the tied notes must remove the tie")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testInsertedGraceNoteInvalidates() {
            // A grace note takes no written time but is no legal separator either — a tie
            // over one is a state canToggleTie would never have let the user create.
            assertThat(outcomeForInserting(tie, END_INDEX, ElementType.GRACE_QUAVER))
                .as("a grace note between the tied notes must remove the tie")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testInsertedBarlineRetains() {
            assertThat(outcomeForInserting(tie, END_INDEX, ElementType.SINGLE_BARLINE))
                .as("a tie across a barline is ordinary notation")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testInsertedRepeatRetains() {
            assertThat(outcomeForInserting(tie, END_INDEX, ElementType.REPEAT_LEFT))
                .as("a tie across a repeat is ordinary notation")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testInsertedBreathMarkRetains() {
            assertThat(outcomeForInserting(tie, END_INDEX, ElementType.BREATH_MARK))
                .as("a breath mark takes no time, so the tied notes stay adjacent")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testInsertedFinalDoubleBarlineInvalidates() {
            // The one barline isLegalSeparator excludes. Tested on Tie directly because
            // Line.addElement's own guard would refuse a mid-line final double barline,
            // leaving this branch of the rule otherwise unreachable from the line API.
            assertThat(
                outcomeForInserting(tie, END_INDEX, ElementType.FINAL_DOUBLE_BARLINE))
                .as("nothing may sound across the end of the piece (refs #527)")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testInsertedNoteAtAnchorRetains() {
            // Inserting at the anchor's index displaces the anchor rightwards, landing the
            // new note before the tie rather than inside it.
            assertThat(outcomeForInserting(tie, ANCHOR_INDEX, ElementType.CROTCHET))
                .as("a note before the anchor is outside the tie")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testInsertedNoteAfterEndRetains() {
            assertThat(outcomeForInserting(tie, AFTER_END_INDEX, ElementType.CROTCHET))
                .as("a note after the end note is outside the tie")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor, insertion — tie straddling a separator
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenTiedNotesAreSeparatedByABarline {

        private Tie tie;

        @BeforeEach
        void addTie() {
            tie = buildSeparatedTie();
        }

        @Test
        void testInsertedNoteBeforeSeparatorInvalidates() {
            assertThat(outcomeForInserting(tie, SEPARATOR_INDEX, ElementType.CROTCHET))
                .as("a note between the anchor and the barline is inside the tie")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testInsertedNoteAfterSeparatorInvalidates() {
            assertThat(outcomeForInserting(tie, SEPARATED_END_INDEX, ElementType.CROTCHET))
                .as("a note between the barline and the end note is inside the tie")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testSecondInsertedBarlineRetains() {
            assertThat(
                outcomeForInserting(tie, SEPARATED_END_INDEX, ElementType.SINGLE_BARLINE))
                .as("a second barline still takes no time")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor, insertion — unresolvable endpoint
    // -----------------------------------------------------------------------

    @Test
    void testDetachedAnchorRetains() {
        // A half-detached tie (its anchor removed from the line) resolves the anchor to -1.
        // Without the guard, every index below the end would read as inside the tie.
        var tie = buildDetachedAnchorTie();

        assertThat(outcomeForInserting(tie, END_INDEX, ElementType.CROTCHET))
            .as("a tie with an unresolvable anchor contains nothing")
            .isSameAs(SpanOutcome.Simple.KEEP);
    }

    // -----------------------------------------------------------------------
    // outcomeFor, replacement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Replacement {

        private Tie tie;

        @BeforeEach
        void addTie() {
            tie = buildAdjacentTie();
        }

        /**
         * Replaces the element at {@code index} of the adjacent layout with a fresh
         * {@code type} left at its default staff position and with no explicit accidental —
         * matching the notes {@link #buildAdjacentTie} laid down.
         */
        private SpanOutcome outcomeForReplacing(int index, ElementType type) {
            return TieInvalidationTest.this.outcomeForReplacing(tie, index, type.newInstance());
        }

        @Test
        void testEndpointBecomingRestInvalidates() {
            assertThat(outcomeForReplacing(END_INDEX, ElementType.CROTCHET_REST))
                .as("a tie into a rest is a state canToggleTie would refuse")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testEndpointBecomingBarlineInvalidates() {
            assertThat(outcomeForReplacing(ANCHOR_INDEX, ElementType.SINGLE_BARLINE))
                .as("a tie out of a barline is a state canToggleTie would refuse")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testEndpointBecomingGraceNoteInvalidates() {
            assertThat(outcomeForReplacing(END_INDEX, ElementType.GRACE_QUAVER))
                .as("a grace note is no tie endpoint (refs #592)")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testEndpointChangingDurationRetains() {
            assertThat(outcomeForReplacing(END_INDEX, ElementType.MINIM))
                .as("the same note written longer sounds the same, so the tie stands")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testEndpointChangingStaffPositionInvalidates() {
            var replacement = crotchet();
            replacement.setStaffPosition(DIFFERENT_STAFF_POSITION);

            assertThat(TieInvalidationTest.this.outcomeForReplacing(tie, END_INDEX, replacement))
                .as("a tie joins two notes of one pitch, so a moved endpoint breaks it")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testEndpointGainingAccidentalInvalidates() {
            var replacement = crotchet();
            replacement.setAccidental(StaffElement.Accidental.SHARP);

            assertThat(TieInvalidationTest.this.outcomeForReplacing(tie, END_INDEX, replacement))
                .as("an accidental the note it replaces did not carry changes the pitch")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testReplacementOutsideTieRetains() {
            assertThat(outcomeForReplacing(AFTER_END_INDEX, ElementType.CROTCHET_REST))
                .as("the element after the end note is no part of the tie")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor, replacement — tie straddling a separator
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SeparatorReplacement {

        private Tie tie;

        @BeforeEach
        void addTie() {
            tie = buildSeparatedTie();
        }

        private SpanOutcome outcomeForReplacingSeparator(ElementType type) {
            return outcomeForReplacing(tie, SEPARATOR_INDEX, type.newInstance());
        }

        @Test
        void testSeparatorBecomingNoteInvalidates() {
            assertThat(outcomeForReplacingSeparator(ElementType.CROTCHET))
                .as("a note between the tied notes takes time they may not be parted by")
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testSeparatorBecomingRepeatRetains() {
            assertThat(outcomeForReplacingSeparator(ElementType.REPEAT_LEFT))
                .as("one legal separator swapped for another leaves the tie legal")
                .isSameAs(SpanOutcome.Simple.KEEP);
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor, replacement — unresolvable endpoint
    // -----------------------------------------------------------------------

    @Test
    void testDetachedAnchorRetainsThroughReplacement() {
        // The insertion side's guard, on the replacement side. With the anchor at -1 the
        // range test would otherwise read every element up to the end note as inside the
        // tie, and turning one of them into a rest would wrongly remove it.
        var tie = buildDetachedAnchorTie();

        assertThat(outcomeForReplacing(tie, END_INDEX, crotchetRest()))
            .as("a tie with an unresolvable anchor contains nothing")
            .isSameAs(SpanOutcome.Simple.KEEP);
    }

    // -----------------------------------------------------------------------
    // Confirm-dialog query
    // -----------------------------------------------------------------------

    @Test
    void testDisplacedTieRaisesNoEndingConfirm() {
        var tie = buildAdjacentTie();

        assertAll(
            () -> assertThat(outcomeForInserting(tie, END_INDEX, ElementType.CROTCHET))
                .as("precondition: this insertion does invalidate the tie")
                .isSameAs(SpanOutcome.Simple.REMOVE),
            () -> assertThat(line.hasEndingInvalidatedByInsertion(END_INDEX, ElementType.CROTCHET))
                .as("a tie goes silently, so it must not raise the ending confirm")
                .isFalse()
        );
    }

    // -----------------------------------------------------------------------
    // Line.addElement wiring
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElementWiring {

        private Tie tie;

        @BeforeEach
        void addTieAndMockBus() {
            tie = buildAdjacentTie();
            mockMessageCenter();
        }

        @AfterEach
        void closeBusMock() {
            closeMessageCenterMock();
        }

        @Test
        void testInsertingNoteBetweenTiedNotesRemovesTie() {
            song.withModification(() ->
                line.addElement(END_INDEX, new StaffElement(ElementType.CROTCHET)));

            var mutations = captureSingleDidChange().getMutations();
            assertAll(
                () -> assertThat(line.findTies())
                    .as("the tie must not survive a note landing between its notes")
                    .isEmpty(),
                () -> assertThat(mutations)
                    .filteredOn(TieRemoval.class::isInstance)
                    .extracting(mutation -> ((TieRemoval) mutation).tie())
                    .as("the removal must be recorded so undo can restore the tie")
                    .containsExactly(tie),
                // Reverse-order undo re-inserts the tie only after the note that displaced
                // it is gone, which holds only if the removal was recorded first.
                () -> assertThat(mutations)
                    .as("the tie removal must precede the insertion that caused it")
                    .element(0)
                    .isInstanceOf(TieRemoval.class),
                () -> assertThat(mutations)
                    .filteredOn(ElementInsertion.class::isInstance)
                    .as("the note must still be inserted")
                    .hasSize(1)
            );
        }

        @Test
        void testInsertingBarlineBetweenTiedNotesKeepsTie() {
            song.withModification(() ->
                line.addElement(END_INDEX, new StaffElement(ElementType.SINGLE_BARLINE)));

            assertAll(
                () -> assertThat(line.findTies())
                    .as("a barline between the notes must leave the tie in place")
                    .containsExactly(tie),
                () -> assertThat(captureSingleDidChange().getMutations())
                    .filteredOn(TieRemoval.class::isInstance)
                    .as("no tie removal must be recorded")
                    .isEmpty()
            );
        }
    }

    // -----------------------------------------------------------------------
    // Line.setElement wiring
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetElementWiring {

        private Tie tie;

        @BeforeEach
        void addTieAndMockBus() {
            tie = buildAdjacentTie();
            mockMessageCenter();
        }

        @AfterEach
        void closeBusMock() {
            closeMessageCenterMock();
        }

        @Test
        void testReplacingTiedNoteWithRestRemovesTie() {
            song.withModification(() ->
                line.setElement(END_INDEX, new StaffElement(ElementType.CROTCHET_REST)));

            var mutations = captureSingleDidChange().getMutations();
            assertAll(
                () -> assertThat(line.findTies())
                    .as("the tie must not survive its end note becoming a rest")
                    .isEmpty(),
                () -> assertThat(mutations)
                    .filteredOn(TieRemoval.class::isInstance)
                    .extracting(mutation -> ((TieRemoval) mutation).tie())
                    .as("the removal must be recorded so undo can restore the tie")
                    .containsExactly(tie),
                // Reverse-order undo re-adds the tie only once its note is back, which holds
                // only if the removal was recorded before the replacement.
                () -> assertThat(mutations)
                    .as("the tie removal must precede the replacement that caused it")
                    .element(0)
                    .isInstanceOf(TieRemoval.class),
                () -> assertThat(mutations)
                    .filteredOn(ElementReplacement.class::isInstance)
                    .as("the rest must still be installed")
                    .hasSize(1)
            );
        }

        @Test
        void testReplacingTiedNoteWithLongerNoteKeepsTie() {
            var replacement = new StaffElement(ElementType.MINIM);
            song.withModification(() -> line.setElement(END_INDEX, replacement));

            assertAll(
                () -> assertThat(line.findTies())
                    .as("the same pitch written longer must leave the tie in place")
                    .containsExactly(tie),
                () -> assertThat(tie.getEndElement())
                    .as("the surviving tie must point at the note that replaced its end")
                    .isSameAs(replacement),
                () -> assertThat(captureSingleDidChange().getMutations())
                    .filteredOn(TieRemoval.class::isInstance)
                    .as("no tie removal must be recorded")
                    .isEmpty()
            );
        }
    }

    // -----------------------------------------------------------------------
    // Cross-line tie — anchor in one line, end in the next (issue #493)
    // -----------------------------------------------------------------------

    /** Index of the tie's anchor in the cross-line fixture — the last note of the first line. */
    private static final int CROSS_LINE_ANCHOR_INDEX = THREE_NOTES.length - 1;

    /** Index of the tie's end in the cross-line fixture — the first note of the second line. */
    private static final int CROSS_LINE_END_INDEX = 0;

    /**
     * A tie whose anchor is the last note of one line and whose end is the first note of the
     * next. {@link Tie#outcomeFor}, under an insertion and under a replacement,
     * resolve both endpoints through {@link Tie#resolveIn}, which asks the line performing the
     * edit — so the far endpoint always resolves to an edge bound rather than to the -1 the far
     * line's own index would give. These tests exercise that through the {@link Line#addElement}
     * / {@link Line#setElement} wiring, so both lines' {@code spans} lists are checked, not just
     * the boolean the predicate returns.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CrossLineTie {

        private Line secondLine;
        private Tie tie;

        @BeforeEach
        void buildCrossLineTie() {
            secondLine = new Line(song);

            song.withoutMutationTracking(() -> {
                // Song() leaves an auto-added terminal barline on the only line it creates;
                // drop it so the first line ends with its last note, matching a real
                // cross-line boundary rather than a stray extra element.
                line.removeElement(0);

                for (var type : THREE_NOTES) {
                    line.addElement(type.newInstance());
                }

                song.addLine(secondLine);

                for (var type : THREE_NOTES) {
                    secondLine.addElement(type.newInstance());
                }
            });

            tie = tieBetween(
                line.getElement(CROSS_LINE_ANCHOR_INDEX), secondLine.getElement(CROSS_LINE_END_INDEX));
        }

        /** Asserts that the tie is in both lines' span lists, once each. */
        private void assertTieInBothLines() {
            assertThat(line.getSpans()).containsOnlyOnce(tie);
            assertThat(secondLine.getSpans()).containsOnlyOnce(tie);
        }

        /** Asserts that the tie is in neither line's span list. */
        private void assertTieInNeitherLine() {
            assertThat(line.getSpans()).doesNotContain(tie);
            assertThat(secondLine.getSpans()).doesNotContain(tie);
        }

        @Test
        void testAppendingNoteToFirstLineRemovesTieFromBothLines() {
            song.withModification(() ->
                line.addElement(line.elementCount(), new StaffElement(ElementType.CROTCHET)));

            assertTieInNeitherLine();
        }

        @Test
        void testAppendingNoteToFirstLineThroughTheIndexlessOverloadRemovesTieFromBothLines() {
            // Every other case here calls addElement(int, StaffElement). The indexless overload
            // is the one the UI's own append path uses, and it once inserted directly rather
            // than delegating — running no invalidation sweep at all. That was invisible while
            // every span ended inside its own line, since nothing appended past the last
            // element could land between a span's endpoints; a cross-line tie is the case
            // where it can (#493).
            song.withModification(() -> line.addElement(new StaffElement(ElementType.CROTCHET)));

            assertTieInNeitherLine();
        }

        @Test
        void testAppendingBarlineToFirstLineThroughTheIndexlessOverloadRetainsTieInBothLines() {
            song.withModification(() -> line.addElement(new StaffElement(ElementType.SINGLE_BARLINE)));

            assertTieInBothLines();
        }

        @Test
        void testInsertingNoteAtHeadOfSecondLineRemovesTieFromBothLines() {
            song.withModification(() ->
                secondLine.addElement(CROSS_LINE_END_INDEX, new StaffElement(ElementType.CROTCHET)));

            assertTieInNeitherLine();
        }

        @Test
        void testAppendingBarlineToFirstLineRetainsTieInBothLines() {
            song.withModification(() ->
                line.addElement(line.elementCount(), new StaffElement(ElementType.SINGLE_BARLINE)));

            assertTieInBothLines();
        }

        @Test
        void testInsertingBarlineAtHeadOfSecondLineRetainsTieInBothLines() {
            song.withModification(() ->
                secondLine.addElement(CROSS_LINE_END_INDEX, new StaffElement(ElementType.SINGLE_BARLINE)));

            assertTieInBothLines();
        }

        @Test
        void testAppendingRepeatToFirstLineRetainsTieInBothLines() {
            song.withModification(() ->
                line.addElement(line.elementCount(), new StaffElement(ElementType.REPEAT_LEFT)));

            assertTieInBothLines();
        }

        @Test
        void testAppendingGraceNoteToFirstLineRemovesTieFromBothLines() {
            song.withModification(() ->
                line.addElement(line.elementCount(), new StaffElement(ElementType.GRACE_QUAVER)));

            assertTieInNeitherLine();
        }

        @Test
        void testInsertingGraceNoteAtHeadOfSecondLineRemovesTieFromBothLines() {
            song.withModification(() ->
                secondLine.addElement(CROSS_LINE_END_INDEX, new StaffElement(ElementType.GRACE_QUAVER)));

            assertTieInNeitherLine();
        }

        @Test
        void testReplacingAnchorWithDifferentStaffPositionRemovesTieFromBothLines() {
            var replacement = crotchet();
            replacement.setStaffPosition(DIFFERENT_STAFF_POSITION);

            song.withModification(() -> line.setElement(CROSS_LINE_ANCHOR_INDEX, replacement));

            assertTieInNeitherLine();
        }

        @Test
        void testReplacingEndWithDifferentStaffPositionRemovesTieFromBothLines() {
            var replacement = crotchet();
            replacement.setStaffPosition(DIFFERENT_STAFF_POSITION);

            song.withModification(() -> secondLine.setElement(CROSS_LINE_END_INDEX, replacement));

            assertTieInNeitherLine();
        }

        @Test
        void testUndoRestoresTheTieIntoBothLines() {
            var batch = UndoTestSupport.captureBatch(song,
                () -> line.addElement(line.elementCount(), new StaffElement(ElementType.CROTCHET)));
            assertTieInNeitherLine();

            UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);

            assertTieInBothLines();
        }
    }

    // -----------------------------------------------------------------------
    // Message bus capture, shared by the wiring tests
    // -----------------------------------------------------------------------

    private @Nullable MockedStatic<MessageCenter> messageCenterMock = null;

    private void mockMessageCenter() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    private void closeMessageCenterMock() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    /**
     * Verifies that exactly one message reached the mocked bus and returns it as the
     * {@link SongDidChangeNotification} it must be.
     */
    private SongDidChangeNotification captureSingleDidChange() {
        assertThat(messageCenterMock).as("the bus mock must be open").isNotNull();

        var captor = ArgumentCaptor.forClass(Message.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));

        assertThat(captor.getValue())
            .as("the single post must be a SongDidChangeNotification")
            .isInstanceOf(SongDidChangeNotification.class);

        return (SongDidChangeNotification) captor.getValue();
    }
}
