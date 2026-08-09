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
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Unit tests for {@link Hairpin#isInvalidatedByReplacement} and its wiring through
 * {@link Line#setElement(int, StaffElement)}.
 *
 * <p>{@code setElement} re-points a span at whatever replaces its endpoint, so without the
 * override a hairpin quietly comes to rest on an element the menu would never have let the user
 * end or anchor it on. The rules are {@link Line#hairpinSurvivesReplacement}'s, which are the
 * menu's and the deletion reshaping's too.
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

    /** Asks the predicate what replacing the element at {@code index} with {@code type} would do. */
    private boolean isInvalidatedByReplacing(int index, ElementType type) {
        return crescendo.isInvalidatedByReplacement(line.getElement(index), type.newInstance(), line);
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
