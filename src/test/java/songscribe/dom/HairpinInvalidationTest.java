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
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Unit tests for {@link Hairpin#outcomeFor} and its wiring through
 * {@link Line#setElement(int, StaffElement)} and {@link Line#addElement(int, StaffElement)}.
 *
 * <p>{@code setElement} re-points a span at whatever replaces its endpoint, and
 * {@code addElement} can push a span's end into second place in its run, so without the
 * override a hairpin quietly comes to rest on an element the menu would never have let the user
 * end or anchor it on. The rules are {@link Hairpin#canAnchorAt}, {@link Hairpin#canEndAt} and
 * {@link Hairpin#hasEnoughColumns}, which are the menu's and the deletion reshaping's too.
 *
 * <p>The fixture, with the crescendo running from index 0 to the rest at index 3:
 * <pre>
 *  idx:  0        1        2        3            4
 *        CROTCHET CROTCHET CROTCHET CROTCHET_REST FINAL_DOUBLE_BARLINE
 *        (anchor)                   (end)         (auto-maintained terminal)
 * </pre>
 */
class HairpinInvalidationTest extends UnitTest {

    /** Index of the crescendo's anchor note. */
    private static final int ANCHOR_INDEX = 0;

    /** Index of an element strictly inside the crescendo. */
    private static final int INTERIOR_INDEX = 1;

    /** Index of the note just before the crescendo's end. */
    private static final int BEFORE_END_INDEX = 2;

    /** Index of the crescendo's end, a rest — the endpoint the reported case replaces. */
    private static final int END_INDEX = 3;

    private Song song;
    private Line line;
    private Crescendo crescendo;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(new StaffElement(ElementType.CROTCHET));
            line.addElement(new StaffElement(ElementType.CROTCHET));
            line.addElement(new StaffElement(ElementType.CROTCHET));
            line.addElement(new StaffElement(ElementType.CROTCHET_REST));
        });

        crescendo = new Crescendo(line.getElement(ANCHOR_INDEX), line.getElement(END_INDEX));
        song.withoutMutationTracking(() -> line.addCrescendo(crescendo));
    }

    /** Asks {@link Hairpin#outcomeFor} what replacing the element at {@code index} with {@code type} would do. */
    private boolean isInvalidatedByReplacing(int index, ElementType type) {
        var change = ElementChange.forReplacement(line, index, type.newInstance());
        return crescendo.outcomeFor(change, line) == SpanOutcome.Simple.REMOVE;
    }

    /** Asks {@link Hairpin#outcomeFor} what inserting {@code type} at {@code index} would do. */
    private boolean isInvalidatedByInserting(int index, ElementType type) {
        var change = ElementChange.forInsertion(line, index, type.newInstance());
        return crescendo.outcomeFor(change, line) == SpanOutcome.Simple.REMOVE;
    }

    /** Asks {@link Hairpin#outcomeFor} what deleting the run {@code [from, to]} would do. */
    private SpanOutcome outcomeForDeleting(int from, int to) {
        return crescendo.outcomeFor(ElementChange.forDeletion(line, from, to), line);
    }

    /** Asks {@link Hairpin#outcomeFor} what deleting the element at {@code index} would do. */
    private SpanOutcome outcomeForDeleting(int index) {
        return outcomeForDeleting(index, index);
    }

    // -----------------------------------------------------------------------
    // isInvalidatedByReplacement — the end
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenTheEndIsReplaced {

        @Test
        void testGraceNoteInvalidates() {
            // The reported case: a hairpin ending on a rest must not come to end on the grace
            // note that replaces it. The grace note's host does not exist yet at this point,
            // so there is nothing to move the end to even if that were wanted.
            assertThat(isInvalidatedByReplacing(END_INDEX, ElementType.GRACE_QUAVER))
                .as("a grace note cannot end a hairpin, so the hairpin must go")
                .isTrue();
        }

        @Test
        void testBarlineInvalidates() {
            assertThat(isInvalidatedByReplacing(END_INDEX, ElementType.SINGLE_BARLINE))
                .as("a barline is not a duration, so it cannot end a hairpin")
                .isTrue();
        }

        @Test
        void testPitchedNoteSurvives() {
            assertThat(isInvalidatedByReplacing(END_INDEX, ElementType.CROTCHET))
                .as("a pitched note is the plainest thing a hairpin may end on")
                .isFalse();
        }

        @Test
        void testRestSurvives() {
            assertThat(isInvalidatedByReplacing(END_INDEX, ElementType.MINIM_REST))
                .as("the end was already a rest, and a rest after a note may end a hairpin")
                .isFalse();
        }

        @Test
        void testRestAfterAnotherRestInvalidates() {
            // Makes the element before the end a rest too, so the end becomes the second of a
            // run — the shape canEndHairpin refuses.
            song.withoutMutationTracking(() ->
                line.setElement(BEFORE_END_INDEX, new StaffElement(ElementType.CROTCHET_REST)));

            assertThat(isInvalidatedByReplacing(END_INDEX, ElementType.MINIM_REST))
                .as("a hairpin ends on at most one rest, so a second one cannot hold the end")
                .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // isInvalidatedByReplacement — the anchor and everything else
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenTheAnchorOrInteriorIsReplaced {

        @Test
        void testAnchorBecomingARestInvalidates() {
            assertThat(isInvalidatedByReplacing(ANCHOR_INDEX, ElementType.CROTCHET_REST))
                .as("a rest may end a hairpin but may never anchor one — LilyPond's rest rule is "
                    + "right-side only")
                .isTrue();
        }

        @Test
        void testAnchorBecomingAGraceNoteWithAPitchedHostSurvives() {
            // canAnchorHairpin lets a grace note anchor on its host's behalf, and the host here
            // is the untouched note at INTERIOR_INDEX.
            assertThat(isInvalidatedByReplacing(ANCHOR_INDEX, ElementType.GRACE_QUAVER))
                .as("a grace note whose host is a pitched note may anchor a hairpin")
                .isFalse();
        }

        @Test
        void testInteriorRestSurvives() {
            assertThat(isInvalidatedByReplacing(INTERIOR_INDEX, ElementType.CROTCHET_REST))
                .as("the endpoint rules govern where a wedge stops, not what it crosses")
                .isFalse();
        }

        @Test
        void testReplacementOutsideTheHairpinSurvives() {
            var outsideIndex = line.elementCount() - 1;

            assertThat(isInvalidatedByReplacing(outsideIndex, ElementType.CROTCHET))
                .as("an element the hairpin does not reach cannot invalidate it")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor — insertion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenAnElementIsInserted {

        @Test
        void testInsertingARestAtTheEndIndexInvalidates() {
            // The reported bug: inserting a rest at the end index pushes the crescendo's end
            // rest into second place in its run, a shape canEndAt forbids.
            assertThat(isInvalidatedByInserting(END_INDEX, ElementType.CROTCHET_REST))
                .as("the end rest is pushed into second place in its run")
                .isTrue();
        }

        @Test
        void testInsertingANoteAtTheEndIndexSurvives() {
            assertThat(isInvalidatedByInserting(END_INDEX, ElementType.CROTCHET))
                .as("a note before the end rest leaves it first in its run")
                .isFalse();
        }

        @Test
        void testInsertingARestAtTheInteriorIndexSurvives() {
            assertThat(isInvalidatedByInserting(INTERIOR_INDEX, ElementType.CROTCHET_REST))
                .as("an interior rest is what a wedge crosses, not where it stops")
                .isFalse();
        }

        @Test
        void testInsertingARestAtTheAnchorIndexSurvives() {
            assertThat(isInvalidatedByInserting(ANCHOR_INDEX, ElementType.CROTCHET_REST))
                .as("the inserted rest lands before the anchor, which still holds")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor — a deletion reshapes the span
    // -----------------------------------------------------------------------

    @Test
    void testDeletingTheEndRestReshapesTheCrescendoOntoTheNoteBeforeIt() {
        assertThat(outcomeForDeleting(END_INDEX))
            .as("the end is pulled in to the last surviving note, which is still pitched")
            .isEqualTo(new SpanOutcome.Reshape(ANCHOR_INDEX, BEFORE_END_INDEX));
    }

    @Test
    void testDeletingDownToTwoNotesReshapesTheCrescendo() {
        assertThat(outcomeForDeleting(BEFORE_END_INDEX, END_INDEX))
            .as("two pitched notes are two columns, which a wedge can still slope across")
            .isEqualTo(new SpanOutcome.Reshape(ANCHOR_INDEX, INTERIOR_INDEX));
    }

    @Test
    void testDeletingTheAnchorReportsTheReshapeInProjectedPositions() {
        // Deleting ahead of what survives is what tells the two numberings apart. The anchor
        // is pulled in to the first note that can begin a hairpin and the end rest survives,
        // so in the projected line they sit at 0 and one before where the rest was — while a
        // reshape mistakenly carrying pre-deletion positions would report 1 and END_INDEX.
        assertThat(outcomeForDeleting(ANCHOR_INDEX))
            .as("a reshape reports positions in the projected line, not in the old one")
            .isEqualTo(new SpanOutcome.Reshape(ANCHOR_INDEX, END_INDEX - 1));
    }

    @Test
    void testDeletingDownToAGraceHostPairRemovesTheCrescendo() {
        // Anchoring on a grace note is legal while the crescendo still reaches the end
        // rest, so this leaves the deletion to be the thing that narrows it.
        song.withoutMutationTracking(() ->
            line.setElement(ANCHOR_INDEX, new StaffElement(ElementType.GRACE_QUAVER)));

        assertThat(outcomeForDeleting(BEFORE_END_INDEX, END_INDEX))
            .as("a grace note and its host are two elements but one column, too narrow to "
                + "slope across")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @Test
    void testAHairpinCoveringOnlyTheFirstElementIsNotReadAsBelongingToAnotherLine() {
        // Position 0 is a real position, not the sentinel that means "this endpoint is not in
        // this line". A hairpin spanning a single element cannot be drawn and the menu will
        // not create one, but an older file can carry one — and reading its end position as
        // absent would keep it untouched forever instead of cleaning it up.
        var degenerate = new Crescendo(line.getElement(ANCHOR_INDEX), line.getElement(ANCHOR_INDEX));

        assertThat(degenerate.outcomeFor(ElementChange.forDeletion(line, END_INDEX, END_INDEX), line))
            .as("a hairpin over a single element is one column, which no wedge can slope across")
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    // -----------------------------------------------------------------------
    // outcomeFor — the span is left too narrow to slope across
    // -----------------------------------------------------------------------

    /**
     * The crescendo narrowed to the two notes at {@code ANCHOR_INDEX} and
     * {@code INTERIOR_INDEX} — the narrowest span the menu will create, where losing a
     * single column leaves nothing to slope across.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenTheHairpinCoversTwoNotes {

        @BeforeEach
        void narrowTheCrescendo() {
            song.withoutMutationTracking(() -> {
                line.removeCrescendo(crescendo);
                crescendo = new Crescendo(line.getElement(ANCHOR_INDEX), line.getElement(INTERIOR_INDEX));
                line.addCrescendo(crescendo);
            });
        }

        @Test
        void testTheAnchorBecomingAGraceNoteInvalidates() {
            // The grace note may anchor on its host's behalf, and the host is the end, so
            // both endpoint rules pass and only the column count catches this.
            assertThat(isInvalidatedByReplacing(ANCHOR_INDEX, ElementType.GRACE_QUAVER))
                .as("a grace note shares its host's column, leaving one column to slope across")
                .isTrue();
        }

        @Test
        void testTheAnchorBecomingAnotherNoteSurvives() {
            assertThat(isInvalidatedByReplacing(ANCHOR_INDEX, ElementType.CROTCHET))
                .as("two pitched notes are still two columns")
                .isFalse();
        }

        @Test
        void testInsertingANoteBetweenTheEndpointsSurvives() {
            assertThat(isInvalidatedByInserting(INTERIOR_INDEX, ElementType.CROTCHET))
                .as("an insertion only ever widens the span, so it cannot starve it of columns")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Line.setElement wiring
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetElementWiring {

        @BeforeEach
        void mockBus() {
            mockMessageCenter();
        }

        @AfterEach
        void closeBusMock() {
            closeMessageCenterMock();
        }

        @Test
        void testReplacingTheEndRestWithAGraceNoteRemovesTheHairpin() {
            song.withModification(() ->
                line.setElement(END_INDEX, new StaffElement(ElementType.GRACE_QUAVER)));

            var mutations = captureSingleDidChange().getMutations();
            assertAll(
                () -> assertThat(line.findSpans(Crescendo.class))
                    .as("the crescendo must not survive its end rest becoming a grace note")
                    .isEmpty(),
                () -> assertThat(mutations)
                    .filteredOn(CrescendoRemoval.class::isInstance)
                    .extracting(mutation -> ((CrescendoRemoval) mutation).crescendo())
                    .as("the removal must be recorded so undo can restore the crescendo")
                    .containsExactly(crescendo),
                // Reverse-order undo re-adds the hairpin only once its end element is back,
                // which holds only if the removal was recorded before the replacement.
                () -> assertThat(mutations)
                    .as("the crescendo removal must precede the replacement that caused it")
                    .element(0)
                    .isInstanceOf(CrescendoRemoval.class),
                () -> assertThat(mutations)
                    .filteredOn(ElementReplacement.class::isInstance)
                    .as("the grace note must still be installed")
                    .hasSize(1)
            );
        }

        @Test
        void testReplacingTheEndRestWithANoteKeepsTheHairpin() {
            var replacement = new StaffElement(ElementType.CROTCHET);
            song.withModification(() -> line.setElement(END_INDEX, replacement));

            assertAll(
                () -> assertThat(line.findSpans(Crescendo.class))
                    .as("a pitched note can hold the end, so the crescendo stays")
                    .containsExactly(crescendo),
                () -> assertThat(crescendo.getEndElement())
                    .as("the surviving crescendo must point at the note that replaced its end")
                    .isSameAs(replacement),
                () -> assertThat(captureSingleDidChange().getMutations())
                    .filteredOn(CrescendoRemoval.class::isInstance)
                    .as("no crescendo removal must be recorded")
                    .isEmpty()
            );
        }
    }

    // -----------------------------------------------------------------------
    // Line.addElement wiring
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElementWiring {

        @BeforeEach
        void mockBus() {
            mockMessageCenter();
        }

        @AfterEach
        void closeBusMock() {
            closeMessageCenterMock();
        }

        @Test
        void testInsertingARestAtTheEndIndexRemovesTheHairpin() {
            song.withModification(() ->
                line.addElement(END_INDEX, new StaffElement(ElementType.CROTCHET_REST)));

            var mutations = captureSingleDidChange().getMutations();
            assertAll(
                () -> assertThat(line.findSpans(Crescendo.class))
                    .as("the crescendo must not survive its end rest being pushed into second "
                        + "place")
                    .isEmpty(),
                () -> assertThat(mutations)
                    .filteredOn(CrescendoRemoval.class::isInstance)
                    .extracting(mutation -> ((CrescendoRemoval) mutation).crescendo())
                    .as("the removal must be recorded so undo can restore the crescendo")
                    .containsExactly(crescendo),
                // Reverse-order undo re-adds the hairpin only once the line is back to its
                // previous shape, which holds only if the removal was recorded before the
                // insertion.
                () -> assertThat(mutations)
                    .as("the crescendo removal must precede the insertion that caused it")
                    .element(0)
                    .isInstanceOf(CrescendoRemoval.class),
                () -> assertThat(mutations)
                    .filteredOn(ElementInsertion.class::isInstance)
                    .as("the inserted rest must still be installed")
                    .hasSize(1)
            );
        }

        @Test
        void testInsertingANoteBetweenTheEndpointsRecordsNoRemoval() {
            song.withModification(() ->
                line.addElement(INTERIOR_INDEX, new StaffElement(ElementType.CROTCHET)));

            assertAll(
                () -> assertThat(line.findSpans(Crescendo.class))
                    .as("an insertion between the endpoints only widens the span, so it holds")
                    .containsExactly(crescendo),
                // A removal recorded for an insertion the hairpin survives would put a step in
                // the undo history that undoes something that never happened.
                () -> assertThat(captureSingleDidChange().getMutations())
                    .filteredOn(CrescendoRemoval.class::isInstance)
                    .as("no crescendo removal must be recorded")
                    .isEmpty()
            );
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
