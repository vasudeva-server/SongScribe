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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Unit tests for {@link Line} tuplet management: query and removal methods.
 *
 * <p>All element/tuplet setup uses {@link Song#withoutMutationTracking} so setup
 * produces no notification. Tests that must observe emitted mutations mock
 * {@link MessageCenter} after setup and use {@link Song#withModification} for the
 * call under test.
 */
class LineTupletTest extends UnitTest {

    // Indices into the 5-element line built in setUp()
    private static final int IDX_0 = 0;
    private static final int IDX_1 = 1;
    private static final int IDX_2 = 2;
    private static final int IDX_3 = 3;
    private static final int IDX_4 = 4;

    // Number of note elements placed in the line fixture
    private static final int NOTE_COUNT = 5;

    // Tuplet grades used in tests
    private static final int TRIPLET_GRADE = 3;
    private static final int QUINTUPLET_GRADE = 5;

    private Song song;
    private Line line;

    /** Adds {@value #NOTE_COUNT} quarter notes to {@code line} via suspended tracking. */
    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            }
        });
    }

    // -----------------------------------------------------------------------
    // findTupletAt(i) — return value (row 35)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindTupletAt {

        private Tuplet triplet;

        @BeforeEach
        void addTriplet() {
            // Triplet spanning [1, 3]
            triplet = Tuplet.withUnresolvedRatio(line.getElement(IDX_1), line.getElement(IDX_3), TRIPLET_GRADE);
            song.withoutMutationTracking(() -> line.addTuplet(triplet));
        }

        /**
         * An index at the anchor of the tuplet span must return the tuplet itself,
         * with the correct anchor and end indices.
         */
        @Test
        void testFindTupletAtAnchorIndexReturnsTuplet() {
            var result = line.findTupletAt(IDX_1);

            // Assert identity first; if this fails the span assertions below are moot.
            assertThat(result)
                .as("findTupletAt anchor index must return the triplet tuplet")
                .isSameAs(triplet);

            // result is confirmed non-null by isSameAs; read span via the known-non-null reference.
            assertAll(
                () -> assertThat(triplet.getAnchorElementIndex())
                    .as("returned tuplet anchor must be 1")
                    .isEqualTo(IDX_1),
                () -> assertThat(triplet.getEndElementIndex())
                    .as("returned tuplet end must be 3")
                    .isEqualTo(IDX_3)
            );
        }

        /**
         * An index strictly interior to the tuplet span must return the tuplet.
         */
        @Test
        void testFindTupletAtInteriorIndexReturnsTuplet() {
            var result = line.findTupletAt(IDX_2);

            assertThat(result)
                .as("findTupletAt interior index must return the triplet tuplet")
                .isSameAs(triplet);

            assertAll(
                () -> assertThat(triplet.getAnchorElementIndex())
                    .as("returned tuplet anchor must be 1")
                    .isEqualTo(IDX_1),
                () -> assertThat(triplet.getEndElementIndex())
                    .as("returned tuplet end must be 3")
                    .isEqualTo(IDX_3)
            );
        }

        /**
         * An index at the end of the tuplet span must return the tuplet.
         */
        @Test
        void testFindTupletAtEndIndexReturnsTuplet() {
            var result = line.findTupletAt(IDX_3);

            assertThat(result)
                .as("findTupletAt end index must return the triplet tuplet")
                .isSameAs(triplet);

            assertThat(triplet.getEndElementIndex())
                .as("returned tuplet end must be 3")
                .isEqualTo(IDX_3);
        }

        /**
         * An index before the tuplet span must return null.
         */
        @Test
        void testFindTupletAtIndexBeforeSpanReturnsNull() {
            assertThat(line.findTupletAt(IDX_0))
                .as("index 0 is before the tuplet span [1,3]")
                .isNull();
        }

        /**
         * An index after the tuplet span must return null.
         */
        @Test
        void testFindTupletAtIndexAfterSpanReturnsNull() {
            assertThat(line.findTupletAt(IDX_4))
                .as("index 4 is after the tuplet span [1,3]")
                .isNull();
        }

        /**
         * When there are no tuplets on the line, any index must return null.
         */
        @Test
        void testFindTupletAtOnLineWithNoTupletsReturnsNull() {
            var freshSong = new Song();
            var freshLine = freshSong.getLine(0);
            freshSong.withoutMutationTracking(() ->
                freshLine.addElement(new StaffElement(ElementType.CROTCHET)));

            assertThat(freshLine.findTupletAt(IDX_0))
                .as("no tuplets on line — findTupletAt must return null")
                .isNull();
        }
    }

    // -----------------------------------------------------------------------
    // findTupletsOverlapping(a, b) (row 36)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindTupletsOverlapping {

        // Two non-overlapping tuplets: triplet [0,1] and quintuplet [3,4]
        private Tuplet tupletLeft;
        private Tuplet tupletRight;

        @BeforeEach
        void addTuplets() {
            tupletLeft = Tuplet.withUnresolvedRatio(line.getElement(IDX_0), line.getElement(IDX_1), TRIPLET_GRADE);
            tupletRight = Tuplet.withUnresolvedRatio(line.getElement(IDX_3), line.getElement(IDX_4), QUINTUPLET_GRADE);
            var left = tupletLeft;
            var right = tupletRight;
            song.withoutMutationTracking(() -> {
                line.addTuplet(left);
                line.addTuplet(right);
            });
        }

        /**
         * A query spanning both tuplets must return both.
         */
        @Test
        void testQuerySpanningBothTupletsReturnsBoth() {
            var result = line.findTupletsOverlapping(IDX_0, IDX_4);

            assertThat(result)
                .as("query [0,4] must return both tuplets")
                .hasSize(2);
        }

        /**
         * A query matching only the left tuplet must return only the left tuplet.
         */
        @Test
        void testQueryTouchingOnlyLeftTupletReturnsLeftOnly() {
            var result = line.findTupletsOverlapping(IDX_0, IDX_1);

            assertThat(result)
                .as("query [0,1] must return only the left tuplet")
                .containsExactly(tupletLeft);
        }

        /**
         * A query matching only the right tuplet must return only the right tuplet.
         */
        @Test
        void testQueryTouchingOnlyRightTupletReturnsRightOnly() {
            var result = line.findTupletsOverlapping(IDX_3, IDX_4);

            assertThat(result)
                .as("query [3,4] must return only the right tuplet")
                .containsExactly(tupletRight);
        }

        /**
         * A query landing in the gap between the two tuplets must return nothing.
         */
        @Test
        void testQueryInGapBetweenTupletsReturnsEmpty() {
            // Index 2 sits in the gap between [0,1] and [3,4].
            var result = line.findTupletsOverlapping(IDX_2, IDX_2);

            assertThat(result)
                .as("query [2,2] in the gap must return no tuplets")
                .isEmpty();
        }

        /**
         * A query entirely outside all tuplets must return nothing.
         */
        @Test
        void testQueryOutsideAllTupletsReturnsEmpty() {
            // Build a fresh line with a single tuplet [1,2] and query entirely outside it.
            var song2 = new Song();
            var line2 = song2.getLine(0);
            song2.withoutMutationTracking(() -> {
                for (var i = 0; i < NOTE_COUNT; i++) {
                    line2.addElement(new StaffElement(ElementType.CROTCHET));
                }
                line2.addTuplet(Tuplet.withUnresolvedRatio(line2.getElement(IDX_1), line2.getElement(IDX_2), TRIPLET_GRADE));
            });

            // Query [3,4] — entirely to the right of [1,2].
            var result = line2.findTupletsOverlapping(IDX_3, IDX_4);

            assertThat(result)
                .as("query [3,4] outside tuplet [1,2] must return empty list")
                .isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // removeOverlappingTuplets(a,b) — fires TupletRemoval per removed tuplet (row 37)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RemoveOverlappingTuplets {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void startMessageMock() {
            // Construct and populate before mocking so Song constructor interactions
            // go to the real bus.
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void stopMessageMock() {
            messageCenterMock.close();
        }

        /**
         * Removing a range covering both tuplets must remove them both and emit exactly
         * one {@link TupletRemoval} mutation per removed tuplet.
         */
        @Test
        void testRemoveOverlappingTupletsRemovesBothAndEmitsTwoRemovals() {
            var tupletA = Tuplet.withUnresolvedRatio(line.getElement(IDX_0), line.getElement(IDX_1), TRIPLET_GRADE);
            var tupletB = Tuplet.withUnresolvedRatio(line.getElement(IDX_3), line.getElement(IDX_4), QUINTUPLET_GRADE);
            song.withoutMutationTracking(() -> {
                line.addTuplet(tupletA);
                line.addTuplet(tupletB);
            });

            song.withModification(() -> line.removeOverlappingTuplets(IDX_0, IDX_4));

            var notification = captureSingleDidChange();
            var removals = notification.getMutations().stream()
                .filter(m -> m instanceof TupletRemoval)
                .map(m -> (TupletRemoval) m)
                .toList();

            assertAll(
                () -> assertThat(removals)
                    .as("exactly two TupletRemoval mutations must be emitted")
                    .hasSize(2),
                () -> assertThat(removals.stream().map(TupletRemoval::tuplet).toList())
                    .as("the removed tuplets must be tupletA and tupletB")
                    .containsExactlyInAnyOrder(tupletA, tupletB),
                () -> assertThat(line.findTupletsOverlapping(IDX_0, IDX_4))
                    .as("no tuplets must remain on the line")
                    .isEmpty()
            );
        }

        /**
         * Removing a range covering only the left tuplet must remove it, emit exactly one
         * {@link TupletRemoval} for that tuplet, and leave the other tuplet untouched.
         */
        @Test
        void testRemoveOverlappingTupletsRemovesOnlyOverlappedTuplet() {
            var tupletA = Tuplet.withUnresolvedRatio(line.getElement(IDX_0), line.getElement(IDX_1), TRIPLET_GRADE);
            var tupletB = Tuplet.withUnresolvedRatio(line.getElement(IDX_3), line.getElement(IDX_4), QUINTUPLET_GRADE);
            song.withoutMutationTracking(() -> {
                line.addTuplet(tupletA);
                line.addTuplet(tupletB);
            });

            song.withModification(() -> line.removeOverlappingTuplets(IDX_0, IDX_1));

            var notification = captureSingleDidChange();
            var removals = notification.getMutations().stream()
                .filter(m -> m instanceof TupletRemoval)
                .map(m -> (TupletRemoval) m)
                .toList();

            assertAll(
                () -> assertThat(removals)
                    .as("exactly one TupletRemoval mutation must be emitted for tupletA")
                    .hasSize(1),
                () -> assertThat(removals.getFirst().tuplet())
                    .as("the removed tuplet must be tupletA")
                    .isSameAs(tupletA),
                () -> assertThat(line.findTupletAt(IDX_3))
                    .as("tupletB must remain on the line")
                    .isSameAs(tupletB)
            );
        }

        /**
         * Removing a range that overlaps no tuplet must emit no mutations.
         */
        @Test
        void testRemoveOverlappingTupletsWithNoOverlapEmitsNoMutations() {
            // Gap at index 2 — no tuplet there
            var tupletA = Tuplet.withUnresolvedRatio(line.getElement(IDX_0), line.getElement(IDX_1), TRIPLET_GRADE);
            song.withoutMutationTracking(() -> line.addTuplet(tupletA));

            song.withModification(() -> line.removeOverlappingTuplets(IDX_2, IDX_2));

            // withModification with no mutations fires nothing
            messageCenterMock.verify(() -> MessageCenter.post(any()), never());
        }

        // Helper: capture the single SongDidChangeNotification posted via the mock.
        private SongDidChangeNotification captureSingleDidChange() {
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            var didChanges = captor.getAllValues().stream()
                .filter(m -> m instanceof SongDidChangeNotification)
                .map(m -> (SongDidChangeNotification) m)
                .toList();

            assertThat(didChanges)
                .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
                .hasSize(1);

            return didChanges.getFirst();
        }
    }
}
