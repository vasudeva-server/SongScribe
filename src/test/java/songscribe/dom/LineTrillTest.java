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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.RangeElementRemoval;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Unit tests for {@link Line} trill management: {@link Line#findTrillsOverlapping},
 * {@link Line#addTrill} (overlap-replace semantics), and
 * {@link Line#removeTrillsOverlapping}.
 *
 * <p>All element/trill setup uses {@link Song#withoutMutationTracking} so setup
 * produces no notification. Tests that must observe emitted mutations mock
 * {@link MessageCenter} after setup and use {@link Song#withModification} for the
 * call under test.
 */
class LineTrillTest extends UnitTest {

    // Indices into the line fixture built in setUp()
    private static final int IDX_0 = 0;
    private static final int IDX_1 = 1;
    private static final int IDX_2 = 2;
    private static final int IDX_3 = 3;
    private static final int IDX_4 = 4;
    private static final int IDX_5 = 5;

    // Number of note elements placed in the line fixture
    private static final int NOTE_COUNT = 6;

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
    // findTrillsOverlapping(a, b)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindTrillsOverlapping {

        // Two non-overlapping trills: left [0,1] and right [3,4]
        private Trill trillLeft;
        private Trill trillRight;

        @BeforeEach
        void addTrills() {
            trillLeft = new Trill(line.getElement(IDX_0), line.getElement(IDX_1));
            trillRight = new Trill(line.getElement(IDX_3), line.getElement(IDX_4));
            song.withoutMutationTracking(() -> {
                line.addTrill(trillLeft);
                line.addTrill(trillRight);
            });
        }

        @Test
        void testQuerySpanningBothTrillsReturnsBoth() {
            assertThat(line.findTrillsOverlapping(IDX_0, IDX_4))
                .as("query [0,4] must return both trills")
                .containsExactlyInAnyOrder(trillLeft, trillRight);
        }

        @Test
        void testQueryTouchingOnlyLeftTrillReturnsLeftOnly() {
            assertThat(line.findTrillsOverlapping(IDX_0, IDX_1))
                .as("query [0,1] must return only the left trill")
                .containsExactly(trillLeft);
        }

        @Test
        void testQueryTouchingOnlyRightTrillReturnsRightOnly() {
            assertThat(line.findTrillsOverlapping(IDX_3, IDX_4))
                .as("query [3,4] must return only the right trill")
                .containsExactly(trillRight);
        }

        // Inclusive boundary: a query beginning exactly at the left trill's end index
        // still overlaps it (end-index >= begin comparison).
        @Test
        void testQueryBeginningAtLeftTrillEndOverlaps() {
            assertThat(line.findTrillsOverlapping(IDX_1, IDX_2))
                .as("query [1,2] touches the left trill's end at index 1")
                .containsExactly(trillLeft);
        }

        @Test
        void testQueryInGapBetweenTrillsReturnsEmpty() {
            // Index 2 sits in the gap between [0,1] and [3,4].
            assertThat(line.findTrillsOverlapping(IDX_2, IDX_2))
                .as("query [2,2] in the gap must return no trills")
                .isEmpty();
        }

        @Test
        void testQueryOnLineWithNoTrillsReturnsEmpty() {
            var freshSong = new Song();
            var freshLine = freshSong.getLine(0);
            freshSong.withoutMutationTracking(() ->
                freshLine.addElement(new StaffElement(ElementType.CROTCHET)));

            assertThat(freshLine.findTrillsOverlapping(IDX_0, IDX_0))
                .as("no trills on line — findTrillsOverlapping must return empty")
                .isEmpty();
        }

        @Test
        void testHasTrillOverlappingIsTrueWhenTouchingTrillEnd() {
            assertThat(line.hasTrillOverlapping(IDX_1, IDX_2))
                .as("range [1,2] touches the left trill's end at index 1")
                .isTrue();
        }

        @Test
        void testHasTrillOverlappingIsFalseInGap() {
            assertThat(line.hasTrillOverlapping(IDX_2, IDX_2))
                .as("range [2,2] sits in the gap between trills")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // addTrill(Trill) — overlap-replace: removes overlapping trills, then adds
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddTrill {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void startMessageMock() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void stopMessageMock() {
            messageCenterMock.close();
        }

        @Test
        void testAddTrillWithNoOverlapEmitsSingleAddition() {
            var trill = new Trill(line.getElement(IDX_1), line.getElement(IDX_2));

            song.withModification(() -> line.addTrill(trill));

            var mutations = captureSingleDidChange().getMutations();
            assertAll(
                () -> assertThat(line.findRangeElements(Trill.class))
                    .as("the new trill must be the only trill on the line")
                    .containsExactly(trill),
                () -> assertThat(mutations)
                    .as("adding a non-overlapping trill records exactly one RangeElementAddition")
                    .singleElement()
                    .isInstanceOfSatisfying(RangeElementAddition.class,
                        addition -> assertThat(addition.element()).isSameAs(trill))
            );
        }

        @Test
        void testAddTrillOverlappingExistingReplacesIt() {
            var existing = new Trill(line.getElement(IDX_1), line.getElement(IDX_3));
            song.withoutMutationTracking(() -> line.addTrill(existing));
            var replacement = new Trill(line.getElement(IDX_2), line.getElement(IDX_4));

            song.withModification(() -> line.addTrill(replacement));

            var mutations = captureSingleDidChange().getMutations();
            assertAll(
                () -> assertThat(line.findRangeElements(Trill.class))
                    .as("the overlapping trill must be replaced by the new one")
                    .containsExactly(replacement),
                () -> assertThat(mutations)
                    .filteredOn(RangeElementRemoval.class::isInstance)
                    .extracting(m -> ((RangeElementRemoval) m).element())
                    .as("the displaced trill must be removed")
                    .containsExactly(existing),
                () -> assertThat(mutations)
                    .filteredOn(RangeElementAddition.class::isInstance)
                    .extracting(m -> ((RangeElementAddition) m).element())
                    .as("the replacement trill must be added")
                    .containsExactly(replacement)
            );
        }

        @Test
        void testAddTrillRemovesAllOverlappingTrills() {
            var left = new Trill(line.getElement(IDX_0), line.getElement(IDX_1));
            var right = new Trill(line.getElement(IDX_3), line.getElement(IDX_4));
            song.withoutMutationTracking(() -> {
                line.addTrill(left);
                line.addTrill(right);
            });
            var spanning = new Trill(line.getElement(IDX_1), line.getElement(IDX_3));

            song.withModification(() -> line.addTrill(spanning));

            var removed = captureSingleDidChange().getMutations().stream()
                .filter(RangeElementRemoval.class::isInstance)
                .map(m -> ((RangeElementRemoval) m).element())
                .toList();
            assertAll(
                () -> assertThat(line.findRangeElements(Trill.class))
                    .as("both overlapping trills replaced by the spanning one")
                    .containsExactly(spanning),
                () -> assertThat(removed)
                    .as("both overlapping trills must be removed")
                    .containsExactlyInAnyOrder(left, right)
            );
        }

        @Test
        void testAddSingleNoteTrillSpansOneElement() {
            var trill = new Trill(line.getElement(IDX_2));

            song.withModification(() -> line.addTrill(trill));

            assertAll(
                () -> assertThat(trill.getAnchorElementIndex())
                    .as("single-note trill anchor index")
                    .isEqualTo(IDX_2),
                () -> assertThat(trill.getEndElementIndex())
                    .as("single-note trill end index equals its anchor")
                    .isEqualTo(IDX_2)
            );
        }

        // Helper: capture the single SongDidChangeNotification posted via the mock.
        private SongDidChangeNotification captureSingleDidChange() {
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));

            assertThat(captor.getValue())
                .as("the single post must be a SongDidChangeNotification")
                .isInstanceOf(SongDidChangeNotification.class);

            return (SongDidChangeNotification) captor.getValue();
        }
    }

    // -----------------------------------------------------------------------
    // removeTrillsOverlapping(a, b) — fires RangeElementRemoval per removed trill
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RemoveTrillsOverlapping {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void startMessageMock() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void stopMessageMock() {
            messageCenterMock.close();
        }

        @Test
        void testRemoveOverlappingRemovesBothTrills() {
            var left = new Trill(line.getElement(IDX_0), line.getElement(IDX_1));
            var right = new Trill(line.getElement(IDX_4), line.getElement(IDX_5));
            song.withoutMutationTracking(() -> {
                line.addTrill(left);
                line.addTrill(right);
            });

            song.withModification(() -> line.removeTrillsOverlapping(IDX_0, IDX_5));

            var removed = captureSingleDidChange().getMutations().stream()
                .filter(RangeElementRemoval.class::isInstance)
                .map(m -> ((RangeElementRemoval) m).element())
                .toList();
            assertAll(
                () -> assertThat(removed)
                    .as("both trills must be removed")
                    .containsExactlyInAnyOrder(left, right),
                () -> assertThat(line.findRangeElements(Trill.class))
                    .as("no trills must remain on the line")
                    .isEmpty()
            );
        }

        @Test
        void testRemoveOverlappingLeavesNonOverlappingTrillIntact() {
            var overlapping = new Trill(line.getElement(IDX_0), line.getElement(IDX_1));
            var untouched = new Trill(line.getElement(IDX_4), line.getElement(IDX_5));
            song.withoutMutationTracking(() -> {
                line.addTrill(overlapping);
                line.addTrill(untouched);
            });

            song.withModification(() -> line.removeTrillsOverlapping(IDX_0, IDX_1));

            assertThat(line.findRangeElements(Trill.class))
                .as("only the trill overlapping [0,1] is removed")
                .containsExactly(untouched);
        }

        // Inclusive boundary: a removal range beginning exactly at a trill's end index
        // still removes it.
        @Test
        void testRemoveRangeTouchingTrillEndRemovesIt() {
            var trill = new Trill(line.getElement(IDX_0), line.getElement(IDX_1));
            song.withoutMutationTracking(() -> line.addTrill(trill));

            song.withModification(() -> line.removeTrillsOverlapping(IDX_1, IDX_2));

            assertThat(line.findRangeElements(Trill.class))
                .as("range [1,2] touches the trill's end at index 1, so it is removed")
                .isEmpty();
        }

        @Test
        void testRemoveWithNoOverlapEmitsNoMutations() {
            var trill = new Trill(line.getElement(IDX_0), line.getElement(IDX_1));
            song.withoutMutationTracking(() -> line.addTrill(trill));

            song.withModification(() -> line.removeTrillsOverlapping(IDX_3, IDX_4));

            assertAll(
                () -> messageCenterMock.verify(() -> MessageCenter.post(any()), never()),
                () -> assertThat(line.findRangeElements(Trill.class))
                    .as("the non-overlapping trill must remain")
                    .containsExactly(trill)
            );
        }

        // Helper: capture the single SongDidChangeNotification posted via the mock.
        private SongDidChangeNotification captureSingleDidChange() {
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));

            assertThat(captor.getValue())
                .as("the single post must be a SongDidChangeNotification")
                .isInstanceOf(SongDidChangeNotification.class);

            return (SongDidChangeNotification) captor.getValue();
        }
    }
}
