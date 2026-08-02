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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link Span}'s predicate factories ({@link Span#containing},
 * {@link Span#overlapping}, {@link Span#exactly}) and {@link Span#matches}, plus the guard
 * behavior change {@link SpanLookup} inherits from {@link Span#containing}: a span with an
 * unresolvable endpoint ({@code -1}) contains nothing, while {@link Span#overlapping} stays
 * unguarded so the trill removal sweeps in {@link Line#addTrill} and
 * {@link Line#removeTrillsOverlapping} keep finding half-detached trills.
 *
 * <p>{@link Line}, the sole {@link SpanLookup} implementor, resolves the indices these
 * predicates operate on.
 */
class SpanLookupTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Shared layout for the predicate-factory tests below:
    //   idx: 0    1    2(anchor) 3    4(end) 5    6
    //   `tie` spans [ANCHOR_IDX, END_IDX] inclusive.
    // -----------------------------------------------------------------------

    private static final int NOTE_COUNT = 7;
    private static final int ANCHOR_IDX = 2;
    private static final int END_IDX = 4;
    private static final int BEFORE_ANCHOR_IDX = ANCHOR_IDX - 1;
    private static final int AFTER_END_IDX = END_IDX + 1;
    private static final int LAST_IDX = NOTE_COUNT - 1;

    private Song song;
    private Line line;
    private Tie tie;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            }
        });

        tie = new Tie(line.getElement(ANCHOR_IDX), line.getElement(END_IDX));
        song.withoutMutationTracking(() -> line.addTie(tie));
    }

    // -----------------------------------------------------------------------
    // Span.containing(int) — guarded containment
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContainingPredicate {

        @Test
        void testAtAnchorMatches() {
            assertThat(tie.matches(Span.containing(ANCHOR_IDX)))
                .as("the anchor index is inclusive")
                .isTrue();
        }

        @Test
        void testInteriorMatches() {
            assertThat(tie.matches(Span.containing(ANCHOR_IDX + 1)))
                .as("an index strictly between anchor and end matches")
                .isTrue();
        }

        @Test
        void testAtEndMatches() {
            assertThat(tie.matches(Span.containing(END_IDX)))
                .as("the end index is inclusive")
                .isTrue();
        }

        @Test
        void testOneBeforeAnchorDoesNotMatch() {
            assertThat(tie.matches(Span.containing(BEFORE_ANCHOR_IDX)))
                .as("one index before the anchor is outside the range")
                .isFalse();
        }

        @Test
        void testOneAfterEndDoesNotMatch() {
            assertThat(tie.matches(Span.containing(AFTER_END_IDX)))
                .as("one index after the end is outside the range")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Span.overlapping(int, int) — unguarded overlap
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OverlappingPredicate {

        @Test
        void testQueryEndingAtSpanAnchorOverlaps() {
            // Query [0, ANCHOR_IDX] touches the span at its left boundary.
            assertThat(tie.matches(Span.overlapping(0, ANCHOR_IDX)))
                .as("a query range ending exactly at the span's anchor overlaps it")
                .isTrue();
        }

        @Test
        void testQueryStartingAtSpanEndOverlaps() {
            // Query [END_IDX, LAST_IDX] touches the span at its right boundary.
            assertThat(tie.matches(Span.overlapping(END_IDX, LAST_IDX)))
                .as("a query range starting exactly at the span's end overlaps it")
                .isTrue();
        }

        @Test
        void testQueryEndingJustBeforeAnchorDoesNotOverlap() {
            // Query [0, BEFORE_ANCHOR_IDX] falls entirely left of the span.
            assertThat(tie.matches(Span.overlapping(0, BEFORE_ANCHOR_IDX)))
                .as("a query range ending just before the anchor does not overlap")
                .isFalse();
        }

        @Test
        void testQueryStartingJustAfterEndDoesNotOverlap() {
            // Query [AFTER_END_IDX, LAST_IDX] falls entirely right of the span.
            assertThat(tie.matches(Span.overlapping(AFTER_END_IDX, LAST_IDX)))
                .as("a query range starting just after the end does not overlap")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Span.exactly(int, int) — disambiguates chained ties sharing an endpoint
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ExactlyPredicate {

        // A second tie [0, ANCHOR_IDX] chained onto `tie`'s anchor at ANCHOR_IDX, so both
        // ties resolve `containing(ANCHOR_IDX)` to true and only `exactly` distinguishes them.
        private Tie chainedTie;

        @BeforeEach
        void addChainedTie() {
            chainedTie = new Tie(line.getElement(0), line.getElement(ANCHOR_IDX));
            song.withoutMutationTracking(() -> line.addTie(chainedTie));
        }

        @Test
        void testExactRangeMatchesOnlyItsOwnTie() {
            assertAll(
                () -> assertThat(chainedTie.matches(Span.exactly(0, ANCHOR_IDX)))
                    .as("the chained tie's own exact range matches")
                    .isTrue(),
                () -> assertThat(tie.matches(Span.exactly(0, ANCHOR_IDX)))
                    .as("the other tie's different range does not match")
                    .isFalse()
            );
        }

        @Test
        void testFindExactTieDisambiguatesWhatFindTieAtCannot() {
            // Both ties contain ANCHOR_IDX, so findTieAt(ANCHOR_IDX) can only return one —
            // whichever was added first — which is why findExactTie exists.
            assertAll(
                () -> assertThat(line.findTieAt(ANCHOR_IDX))
                    .as("findTieAt is ambiguous at the shared endpoint; it returns the first match, "
                        + "which is `tie` — added before `chainedTie` in setUp")
                    .isSameAs(tie),
                () -> assertThat(line.findExactTie(0, ANCHOR_IDX))
                    .as("findExactTie resolves the chained tie by its exact range")
                    .isSameAs(chainedTie),
                () -> assertThat(line.findExactTie(ANCHOR_IDX, END_IDX))
                    .as("findExactTie resolves the other tie by its exact range")
                    .isSameAs(tie)
            );
        }
    }

    // -----------------------------------------------------------------------
    // The one behavior change: Span.containing is guarded, Span.overlapping is not.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GuardBehavior {

        private static final int GUARD_NOTE_COUNT = 5;
        private static final int GUARD_ANCHOR_IDX = 1;
        private static final int GUARD_END_IDX = 3;

        private Song guardSong;
        private Line guardLine;
        private Ending ending;
        private Trill trill;
        private StaffElement endElement;

        @BeforeEach
        void setUpHalfDetachedSpans() {
            guardSong = new Song();
            guardLine = guardSong.getLine(0);

            guardSong.withoutMutationTracking(() -> {
                for (var i = 0; i < GUARD_NOTE_COUNT; i++) {
                    guardLine.addElement(new StaffElement(ElementType.CROTCHET));
                }
            });

            var anchorElement = guardLine.getElement(GUARD_ANCHOR_IDX);
            endElement = guardLine.getElement(GUARD_END_IDX);
            ending = new Ending(anchorElement, endElement);
            trill = new Trill(anchorElement, endElement);
            guardSong.withoutMutationTracking(() -> {
                guardLine.addSpan(ending);
                guardLine.addSpan(trill);
            });

            // withReplay skips the invalidation sweep that would otherwise remove `ending`
            // and `trill` along with their anchor — see Line.removeElement's comment on why
            // a replayed batch must not re-derive companion removals. That leaves both spans
            // in guardLine.getSpans() with an anchor that no longer resolves to a position:
            // exactly the half-detached state the containing/overlapping guard distinction is about.
            guardSong.withoutMutationTracking(
                () -> guardSong.withReplay(() -> guardLine.removeElement(GUARD_ANCHOR_IDX)));
        }

        @Test
        void testAnchorIndexIsUnresolvedAfterDetachment() {
            // Precondition: if this stops holding, the rest of this test class fails loudly
            // rather than silently passing on a fixture that no longer detaches anything.
            assertAll(
                () -> assertThat(ending.getAnchorElementIndex())
                    .as("the ending's anchor must no longer resolve to a position")
                    .isEqualTo(-1),
                () -> assertThat(trill.getAnchorElementIndex())
                    .as("the trill's anchor must no longer resolve to a position")
                    .isEqualTo(-1)
            );
        }

        @Test
        void testHalfDetachedEndingIsNoLongerReported() {
            // The end element is still attached; query at its (shifted) current position.
            var endIndex = guardLine.getElementIndex(endElement);

            assertAll(
                () -> assertThat(guardLine.findEndingAt(endIndex))
                    .as("containing is guarded: an unresolvable anchor contains nothing")
                    .isNull(),
                () -> assertThat(guardLine.isInsideAnyEnding(endIndex))
                    .as("containing is guarded: an unresolvable anchor contains nothing")
                    .isFalse()
            );
        }

        @Test
        void testHalfDetachedTrillIsStillFoundByOverlap() {
            // The end element is still attached; query at its (shifted) current position.
            var endIndex = guardLine.getElementIndex(endElement);

            assertAll(
                () -> assertThat(guardLine.findTrillsOverlapping(0, endIndex))
                    .as("overlapping is deliberately unguarded so removal sweeps still find it")
                    .containsExactly(trill),
                () -> assertThat(guardLine.hasTrillOverlapping(0, endIndex))
                    .as("overlapping is deliberately unguarded so removal sweeps still find it")
                    .isTrue()
            );
        }
    }

    // -----------------------------------------------------------------------
    // hasTrillOverlapping's documented short-circuit
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasTrillOverlappingShortCircuit {

        private static final int SHORT_CIRCUIT_NOTE_COUNT = 4;
        private static final int TRILL_A_END_IDX = 2;
        private static final int TRILL_B_ANCHOR_IDX = 1;
        private static final int TRILL_B_END_IDX = 3;

        @Test
        void testTwoOverlappingTrillsReturnTrueWithoutMaterializingAList() {
            var shortCircuitSong = new Song();
            var shortCircuitLine = shortCircuitSong.getLine(0);

            shortCircuitSong.withoutMutationTracking(() -> {
                for (var i = 0; i < SHORT_CIRCUIT_NOTE_COUNT; i++) {
                    shortCircuitLine.addElement(new StaffElement(ElementType.CROTCHET));
                }

                // addSpan (not addTrill) so both overlapping trills coexist — addTrill would
                // remove the first as an overlap-replace side effect before adding the second.
                shortCircuitLine.addSpan(
                    new Trill(shortCircuitLine.getElement(0), shortCircuitLine.getElement(TRILL_A_END_IDX)));
                shortCircuitLine.addSpan(new Trill(
                    shortCircuitLine.getElement(TRILL_B_ANCHOR_IDX), shortCircuitLine.getElement(TRILL_B_END_IDX)));
            });

            // hasSpan (which findTrillsOverlapping/hasTrillOverlapping both route through via
            // findFirstSpan) documents that it "short-circuits on the first match and allocates
            // nothing" — this pins the observable half of that claim: the boolean result is
            // correct with two overlapping trills present. The allocation half is not
            // independently instrumented; it is enforced by code review of findFirstSpan.
            assertThat(shortCircuitLine.hasTrillOverlapping(0, TRILL_B_END_IDX))
                .as("two overlapping trills — presence must still be reported")
                .isTrue();
        }
    }
}
