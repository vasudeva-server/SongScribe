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
 * {@link Span#overlapping}, {@link Span#exactly}) driven through {@link Line}, the sole
 * {@link SpanLookup} implementor and the thing that resolves an endpoint to a
 * {@link SpanBound}. {@link SpanBoundPredicateTest} covers the predicates as pure functions
 * over every bound combination; this covers what {@code Line} actually hands them.
 *
 * <p>That includes the guard behavior {@link SpanLookup} inherits from
 * {@link Span#containing}: a span with an {@link SpanBound#ABSENT} endpoint contains nothing,
 * while {@link Span#overlapping} stays unguarded so the trill removal sweeps in
 * {@link Line#addTrill} and {@link Line#removeTrillsOverlapping} keep finding half-detached
 * trills.
 */
class SpanLookupTest extends UnitTest {

    /** Applies {@code predicate} to {@code span}'s endpoints as {@code lookupLine} resolves them. */
    private static boolean matches(Span.IndexPredicate predicate, Line lookupLine, Span span) {
        return predicate.test(lookupLine.anchorIndexOf(span), lookupLine.endIndexOf(span));
    }

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
    // findSpans(Class) parity with findSpans(Class, alwaysTrue) — Phase 3 gave the
    // type-only overload its own loop; this pins that it still returns the same spans,
    // in the same order, as the predicate-based overload it stopped delegating to.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindSpansTypeOnlyParity {

        private static final int PARITY_NOTE_COUNT = 14;
        private static final int TIE_ANCHOR_IDX = 0;
        private static final int TIE_END_IDX = 1;
        private static final int BEAM_ANCHOR_IDX = 2;
        private static final int BEAM_END_IDX = 3;
        private static final int TUPLET_ANCHOR_IDX = 4;
        private static final int TUPLET_END_IDX = 5;
        private static final int TUPLET_GRADE = 3;
        private static final int TUPLET_NORMAL_NOTES = 2;
        private static final int CRESCENDO_ANCHOR_IDX = 6;
        private static final int CRESCENDO_END_IDX = 7;
        private static final int DIMINUENDO_ANCHOR_IDX = 8;
        private static final int DIMINUENDO_END_IDX = 9;
        private static final int ENDING_ANCHOR_IDX = 10;
        private static final int ENDING_END_IDX = 11;
        // The two trills reuse other spans' elements — a trill legitimately overlaps a
        // tied or beamed note — and coexist as two Trills so ordering is actually exercised.
        private static final int TRILL_A_ANCHOR_IDX = TIE_ANCHOR_IDX;
        private static final int TRILL_A_END_IDX = BEAM_ANCHOR_IDX;
        private static final int TRILL_B_ANCHOR_IDX = TUPLET_ANCHOR_IDX;
        private static final int TRILL_B_END_IDX = CRESCENDO_ANCHOR_IDX;
        // Elements not referenced by any other span, so removing the anchor below shifts
        // no index any other span depends on.
        private static final int HALF_DETACHED_ANCHOR_IDX = 12;
        private static final int HALF_DETACHED_END_IDX = 13;

        private Song paritySong;
        private Line parityLine;

        @BeforeEach
        void setUpMixedSpanTypes() {
            paritySong = new Song();
            parityLine = paritySong.getLine(0);

            paritySong.withoutMutationTracking(() -> {
                for (var i = 0; i < PARITY_NOTE_COUNT; i++) {
                    parityLine.addElement(new StaffElement(ElementType.CROTCHET));
                }

                parityLine.addSpan(
                    new Tie(parityLine.getElement(TIE_ANCHOR_IDX), parityLine.getElement(TIE_END_IDX)));
                parityLine.addSpan(
                    new Beam(parityLine.getElement(BEAM_ANCHOR_IDX), parityLine.getElement(BEAM_END_IDX)));
                parityLine.addSpan(new Tuplet(parityLine.getElement(TUPLET_ANCHOR_IDX),
                    parityLine.getElement(TUPLET_END_IDX), TUPLET_GRADE, TUPLET_NORMAL_NOTES, ElementType.CROTCHET,
                    0));
                parityLine.addSpan(new Crescendo(
                    parityLine.getElement(CRESCENDO_ANCHOR_IDX), parityLine.getElement(CRESCENDO_END_IDX)));
                parityLine.addSpan(new Diminuendo(
                    parityLine.getElement(DIMINUENDO_ANCHOR_IDX), parityLine.getElement(DIMINUENDO_END_IDX)));
                parityLine.addSpan(
                    new Ending(parityLine.getElement(ENDING_ANCHOR_IDX), parityLine.getElement(ENDING_END_IDX)));
                parityLine.addSpan(
                    new Trill(parityLine.getElement(TRILL_A_ANCHOR_IDX), parityLine.getElement(TRILL_A_END_IDX)));
                parityLine.addSpan(
                    new Trill(parityLine.getElement(TRILL_B_ANCHOR_IDX), parityLine.getElement(TRILL_B_END_IDX)));
                parityLine.addSpan(new Trill(parityLine.getElement(HALF_DETACHED_ANCHOR_IDX),
                    parityLine.getElement(HALF_DETACHED_END_IDX)));
            });

            // withReplay skips the invalidation sweep that would otherwise remove the last
            // trill along with its anchor — see GuardBehavior.setUpHalfDetachedSpans below for
            // the full explanation of the technique. That leaves a half-detached span in the
            // mix: the case where an endpoint-resolving path could diverge from one that
            // resolves nothing.
            paritySong.withoutMutationTracking(
                () -> paritySong.withReplay(() -> parityLine.removeElement(HALF_DETACHED_ANCHOR_IDX)));
        }

        @Test
        void testFindSpansTypeOnlyMatchesAlwaysTruePredicateOverload() {
            assertAll(
                () -> assertParity(Tie.class),
                () -> assertParity(Beam.class),
                () -> assertParity(Tuplet.class),
                () -> assertParity(Crescendo.class),
                () -> assertParity(Diminuendo.class),
                () -> assertParity(Ending.class),
                () -> assertParity(Trill.class)
            );
        }

        private <T extends Span> void assertParity(Class<T> type) {
            assertThat(parityLine.findSpans(type, (anchorBound, endBound) -> true))
                .as("findSpans(%s, alwaysTrue) must match findSpans(%s) element-for-element in order",
                    type.getSimpleName(), type.getSimpleName())
                .containsExactlyElementsOf(parityLine.findSpans(type));
        }
    }

    // -----------------------------------------------------------------------
    // Span.containing(int) — guarded containment
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContainingPredicate {

        @Test
        void testAtAnchorMatches() {
            assertThat(matches(Span.containing(ANCHOR_IDX), line, tie))
                .as("the anchor index is inclusive")
                .isTrue();
        }

        @Test
        void testInteriorMatches() {
            assertThat(matches(Span.containing(ANCHOR_IDX + 1), line, tie))
                .as("an index strictly between anchor and end matches")
                .isTrue();
        }

        @Test
        void testAtEndMatches() {
            assertThat(matches(Span.containing(END_IDX), line, tie))
                .as("the end index is inclusive")
                .isTrue();
        }

        @Test
        void testOneBeforeAnchorDoesNotMatch() {
            assertThat(
                matches(Span.containing(BEFORE_ANCHOR_IDX), line, tie))
                .as("one index before the anchor is outside the range")
                .isFalse();
        }

        @Test
        void testOneAfterEndDoesNotMatch() {
            assertThat(matches(Span.containing(AFTER_END_IDX), line, tie))
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
            assertThat(
                matches(Span.overlapping(0, ANCHOR_IDX), line, tie))
                .as("a query range ending exactly at the span's anchor overlaps it")
                .isTrue();
        }

        @Test
        void testQueryStartingAtSpanEndOverlaps() {
            // Query [END_IDX, LAST_IDX] touches the span at its right boundary.
            assertThat(
                matches(Span.overlapping(END_IDX, LAST_IDX), line, tie))
                .as("a query range starting exactly at the span's end overlaps it")
                .isTrue();
        }

        @Test
        void testQueryEndingJustBeforeAnchorDoesNotOverlap() {
            // Query [0, BEFORE_ANCHOR_IDX] falls entirely left of the span.
            assertThat(
                matches(Span.overlapping(0, BEFORE_ANCHOR_IDX), line, tie))
                .as("a query range ending just before the anchor does not overlap")
                .isFalse();
        }

        @Test
        void testQueryStartingJustAfterEndDoesNotOverlap() {
            // Query [AFTER_END_IDX, LAST_IDX] falls entirely right of the span.
            assertThat(
                matches(Span.overlapping(AFTER_END_IDX, LAST_IDX), line, tie))
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
                () -> assertThat(matches(Span.exactly(0, ANCHOR_IDX), line, chainedTie))
                    .as("the chained tie's own exact range matches")
                    .isTrue(),
                () -> assertThat(
                    matches(Span.exactly(0, ANCHOR_IDX), line, tie))
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

        @Test
        void testTieMatchingOnlyOneEndpointDoesNotSatisfyExactly() {
            // Shares `tie`'s anchor (ANCHOR_IDX) but not its end, so the conjunction in
            // Span.exactly has exactly one true half — && and || diverge here, unlike the
            // two ties above, which differ in both endpoints at once.
            var partialMatchTie = new Tie(line.getElement(ANCHOR_IDX), line.getElement(AFTER_END_IDX));
            song.withoutMutationTracking(() -> line.addTie(partialMatchTie));

            assertAll(
                () -> assertThat(matches(Span.exactly(ANCHOR_IDX, END_IDX), line, partialMatchTie))
                    .as("matching only the anchor is not an exact match")
                    .isFalse(),
                () -> assertThat(line.findExactTie(ANCHOR_IDX, END_IDX))
                    .as("findExactTie must still resolve the original tie, not the partial-anchor match")
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

        /**
         * Builds a line with an {@link Ending} and a {@link Trill} sharing an anchor/end
         * pair, then detaches whichever endpoint {@code detachedElementIdx} names —
         * leaving the other endpoint resolvable. {@code withReplay} skips the invalidation
         * sweep that would otherwise remove the spans along with the detached element — see
         * {@code Line.removeElement}'s comment on why a replayed batch must not re-derive
         * companion removals. That leaves both spans in {@code getSpans()} with one endpoint
         * that no longer resolves to a position: exactly the half-detached state the
         * containing/overlapping guard distinction is about.
         */
        private HalfDetachedFixture buildHalfDetachedSpans(int detachedElementIdx) {
            var guardSong = new Song();
            var guardLine = guardSong.getLine(0);

            guardSong.withoutMutationTracking(() -> {
                for (var i = 0; i < GUARD_NOTE_COUNT; i++) {
                    guardLine.addElement(new StaffElement(ElementType.CROTCHET));
                }
            });

            var anchorElement = guardLine.getElement(GUARD_ANCHOR_IDX);
            var endElement = guardLine.getElement(GUARD_END_IDX);
            var ending = new Ending(anchorElement, endElement);
            var trill = new Trill(anchorElement, endElement);
            guardSong.withoutMutationTracking(() -> {
                guardLine.addSpan(ending);
                guardLine.addSpan(trill);
            });

            guardSong.withoutMutationTracking(
                () -> guardSong.withReplay(() -> guardLine.removeElement(detachedElementIdx)));

            return new HalfDetachedFixture(guardSong, guardLine, ending, trill, anchorElement, endElement);
        }

        private record HalfDetachedFixture(
            Song song, Line line, Ending ending, Trill trill, StaffElement anchorElement, StaffElement endElement) {
        }

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class AnchorDetached {

            private HalfDetachedFixture fixture;

            @BeforeEach
            void setUp() {
                fixture = buildHalfDetachedSpans(GUARD_ANCHOR_IDX);
            }

            @Test
            void testAnchorIndexIsUnresolvedAfterDetachment() {
                // Precondition: if this stops holding, the rest of this test class fails
                // loudly rather than silently passing on a fixture that no longer detaches
                // anything.
                assertAll(
                    () -> assertThat(fixture.ending().getAnchorElementIndex())
                        .as("the ending's anchor must no longer resolve to a position")
                        .isEqualTo(-1),
                    () -> assertThat(fixture.trill().getAnchorElementIndex())
                        .as("the trill's anchor must no longer resolve to a position")
                        .isEqualTo(-1)
                );
            }

            @Test
            void testHalfDetachedEndingIsNoLongerReported() {
                // The end element is still attached; query at its (shifted) current position.
                var endIndex = fixture.line().getElementIndex(fixture.endElement());

                assertAll(
                    () -> assertThat(fixture.line().findEndingAt(endIndex))
                        .as("containing is guarded: an unresolvable anchor contains nothing")
                        .isNull(),
                    () -> assertThat(fixture.line().isInsideAnyEnding(endIndex))
                        .as("containing is guarded: an unresolvable anchor contains nothing")
                        .isFalse()
                );
            }

            @Test
            void testHalfDetachedTrillIsStillFoundByOverlap() {
                // The end element is still attached; query at its (shifted) current position.
                var endIndex = fixture.line().getElementIndex(fixture.endElement());

                assertAll(
                    () -> assertThat(fixture.line().findTrillsOverlapping(0, endIndex))
                        .as("overlapping is deliberately unguarded so removal sweeps still find it")
                        .containsExactly(fixture.trill()),
                    () -> assertThat(fixture.line().hasTrillOverlapping(0, endIndex))
                        .as("overlapping is deliberately unguarded so removal sweeps still find it")
                        .isTrue()
                );
            }
        }

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class EndDetached {

            private HalfDetachedFixture fixture;

            @BeforeEach
            void setUp() {
                fixture = buildHalfDetachedSpans(GUARD_END_IDX);
            }

            @Test
            void testEndIndexIsUnresolvedAfterDetachment() {
                // Precondition: if this stops holding, the rest of this test class fails
                // loudly rather than silently passing on a fixture that no longer detaches
                // anything. The anchor must still resolve — otherwise this is the
                // AnchorDetached case, not its mirror.
                assertAll(
                    () -> assertThat(fixture.ending().getEndElementIndex())
                        .as("the ending's end must no longer resolve to a position")
                        .isEqualTo(-1),
                    () -> assertThat(fixture.trill().getEndElementIndex())
                        .as("the trill's end must no longer resolve to a position")
                        .isEqualTo(-1),
                    () -> assertThat(fixture.ending().getAnchorElementIndex())
                        .as("the anchor must remain resolvable — only the end is detached")
                        .isNotEqualTo(-1)
                );
            }

            @Test
            void testHalfDetachedEndingIsNoLongerReported() {
                // The anchor element is still attached; query at its current position.
                var anchorIndex = fixture.line().getElementIndex(fixture.anchorElement());

                assertAll(
                    () -> assertThat(fixture.line().findEndingAt(anchorIndex))
                        .as("containing is guarded: an unresolvable end contains nothing, "
                            + "same as an unresolvable anchor — see Span.containing's endIndex >= 0 check")
                        .isNull(),
                    () -> assertThat(fixture.line().isInsideAnyEnding(anchorIndex))
                        .as("containing is guarded: an unresolvable end contains nothing")
                        .isFalse()
                );
            }
        }
    }

    // -----------------------------------------------------------------------
    // A span whose endpoints have been reparented to another line resolves against the
    // line that still holds the span to a direction, never a position. Resolving through
    // the endpoint's own line instead would answer with a position in *that* line — a
    // plausible index no caller could tell apart from a real one.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CrossLineEndpoints {

        private static final int CROSS_NOTE_COUNT = 5;
        private static final int CROSS_ANCHOR_IDX = 1;
        private static final int CROSS_END_IDX = 3;

        private Song crossSong;
        private Line sourceLine;
        private Line otherLine;
        private Ending ending;
        private Trill trill;
        private StaffElement anchorElement;
        private StaffElement endElement;

        @BeforeEach
        void setUp() {
            crossSong = new Song();
            sourceLine = crossSong.getLine(0);
            otherLine = new Line(crossSong);

            crossSong.withoutMutationTracking(() -> {
                for (var i = 0; i < CROSS_NOTE_COUNT; i++) {
                    sourceLine.addElement(new StaffElement(ElementType.CROTCHET));
                }

                crossSong.addLine(otherLine);
            });

            anchorElement = sourceLine.getElement(CROSS_ANCHOR_IDX);
            endElement = sourceLine.getElement(CROSS_END_IDX);
            ending = new Ending(anchorElement, endElement);
            trill = new Trill(anchorElement, endElement);

            crossSong.withoutMutationTracking(() -> {
                sourceLine.addSpan(ending);
                sourceLine.addSpan(trill);
            });

            reparentToOtherLine(anchorElement);
            reparentToOtherLine(endElement);
        }

        /**
         * Moves {@code element} out of {@code sourceLine} and into {@code otherLine}, which is
         * what a paste or a line split does transiently. {@code withReplay} skips the
         * invalidation sweep that would otherwise delete the spans along with their removed
         * endpoint, leaving both spans in {@code sourceLine.getSpans()} pointing at elements
         * that now belong to another line.
         */
        private void reparentToOtherLine(StaffElement element) {
            crossSong.withoutMutationTracking(() -> {
                var index = sourceLine.getElementIndex(element);
                crossSong.withReplay(() -> sourceLine.removeElement(index));
                otherLine.addElement(element);
            });
        }

        @Test
        void testEndpointsInAnotherLineResolveToADirectionNotAPosition() {
            assertAll(
                // Preconditions: the spans are still in the source line, and the endpoints
                // really did move — so the receiver-blind resolution had a plausible
                // index available to return.
                () -> assertThat(sourceLine.getSpans())
                    .as("the spans must still belong to the source line")
                    .contains(ending, trill),
                () -> assertThat(otherLine.getElementIndex(anchorElement))
                    .as("the anchor must now resolve in the other line")
                    .isNotEqualTo(-1),
                () -> assertThat(otherLine.getElementIndex(endElement))
                    .as("the end must now resolve in the other line")
                    .isNotEqualTo(-1),

                // otherLine was appended after sourceLine, so both endpoints are off its
                // right edge — a direction, never an index of this line.
                () -> assertThat(sourceLine.anchorIndexOf(ending))
                    .as("an anchor in a later line is off this line's right edge")
                    .isEqualTo(SpanBound.AFTER_LINE),
                () -> assertThat(sourceLine.endIndexOf(ending))
                    .as("an end in a later line is off this line's right edge")
                    .isEqualTo(SpanBound.AFTER_LINE)
            );
        }

        @Test
        void testCrossLineEndingContainsNothing() {
            for (var i = 0; i < sourceLine.elementCount(); i++) {
                var index = i;

                assertAll(
                    () -> assertThat(sourceLine.findEndingAt(index))
                        .as("no index of the source line is inside a cross-line ending")
                        .isNull(),
                    () -> assertThat(sourceLine.isInsideAnyEnding(index))
                        .as("no index of the source line is inside a cross-line ending")
                        .isFalse()
                );
            }
        }

        @Test
        void testCrossLineTrillOverlapsNothing() {
            var lastIndex = sourceLine.elementCount() - 1;

            assertAll(
                () -> assertThat(sourceLine.findTrillsOverlapping(0, lastIndex))
                    .as("a trill with both endpoints in another line overlaps no range here")
                    .isEmpty(),
                () -> assertThat(sourceLine.hasTrillOverlapping(0, lastIndex))
                    .as("a trill with both endpoints in another line overlaps no range here")
                    .isFalse()
            );
        }
    }

    // -----------------------------------------------------------------------
    // The feature this type exists for: one Tie in two lines' span lists, each line
    // owning one endpoint and seeing the other as a direction off its own edge.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CrossLineTie {

        private static final int LINE_NOTE_COUNT = 4;

        private Song crossSong;
        private Line lineA;
        private Line lineB;
        private Tie crossTie;
        private StaffElement anchorElement;
        private StaffElement endElement;

        @BeforeEach
        void setUp() {
            crossSong = new Song();
            lineA = crossSong.getLine(0);
            lineB = new Line(crossSong);

            crossSong.withoutMutationTracking(() -> {
                crossSong.addLine(lineB);

                for (var i = 0; i < LINE_NOTE_COUNT; i++) {
                    lineA.addElement(new StaffElement(ElementType.CROTCHET));
                    lineB.addElement(new StaffElement(ElementType.CROTCHET));
                }
            });

            anchorElement = lineA.getElement(lineA.elementCount() - 1);
            endElement = lineB.getElement(0);
            crossTie = new Tie(anchorElement, endElement);
            crossSong.withoutMutationTracking(() -> lineA.addTie(crossTie));
        }

        @Test
        void testEachLineResolvesItsOwnEndpointAndTheOtherAsADirection() {
            var anchorIndex = lineA.getElementIndex(anchorElement);
            var endIndex = lineB.getElementIndex(endElement);

            assertAll(
                // Precondition: one tie, both lists — otherwise each line would be resolving
                // a span it never sees.
                () -> assertThat(lineA.getSpans())
                    .as("the anchor's line holds the tie")
                    .contains(crossTie),
                () -> assertThat(lineB.getSpans())
                    .as("the end's line holds the same tie")
                    .contains(crossTie),

                () -> assertThat(lineA.anchorIndexOf(crossTie))
                    .as("line A owns the anchor, so it resolves to a real position there")
                    .isEqualTo(new SpanBound.At(anchorIndex)),
                () -> assertThat(lineA.endIndexOf(crossTie))
                    .as("line A's half exits through its right edge")
                    .isEqualTo(SpanBound.AFTER_LINE),
                () -> assertThat(lineB.anchorIndexOf(crossTie))
                    .as("line B's half enters through its left edge")
                    .isEqualTo(SpanBound.BEFORE_LINE),
                () -> assertThat(lineB.endIndexOf(crossTie))
                    .as("line B owns the end, so it resolves to a real position there")
                    .isEqualTo(new SpanBound.At(endIndex))
            );
        }

        @Test
        void testEachHalfCoversOnlyItsOwnSideOfTheBoundary() {
            var anchorIndex = lineA.getElementIndex(anchorElement);
            var endIndex = lineB.getElementIndex(endElement);

            assertAll(
                () -> assertThat(lineA.findTieAt(anchorIndex))
                    .as("line A's half covers the element it is anchored to")
                    .isSameAs(crossTie),
                () -> assertThat(lineA.findTieAt(anchorIndex - 1))
                    .as("line A's half starts at its anchor, not before it")
                    .isNull(),
                () -> assertThat(lineB.findTieAt(endIndex))
                    .as("line B's half covers the element it ends on")
                    .isSameAs(crossTie),
                () -> assertThat(lineB.findTieAt(endIndex + 1))
                    .as("line B's half stops at its end, not after it")
                    .isNull()
            );
        }

        @Test
        void testHalfNeverAnswersAnExactRangeQuery() {
            var anchorIndex = lineA.getElementIndex(anchorElement);
            var lastIndexOfLineA = lineA.elementCount() - 1;

            assertAll(
                () -> assertThat(lineA.findExactTie(anchorIndex, anchorIndex))
                    .as("an off-edge end is not the anchor's own index")
                    .isNull(),
                () -> assertThat(lineA.findExactTie(anchorIndex, lastIndexOfLineA))
                    .as("an off-edge end is not this line's last index either")
                    .isNull(),
                () -> assertThat(lineB.findExactTie(0, lineB.getElementIndex(endElement)))
                    .as("an off-edge anchor is not index 0")
                    .isNull()
            );
        }

        @Test
        void testDeletedFarEndpointResolvesToAbsentRatherThanADirection() {
            // withReplay skips the invalidation sweep, leaving the tie in both lists with an
            // end element that now belongs to no line — see GuardBehavior above.
            crossSong.withoutMutationTracking(
                () -> crossSong.withReplay(() -> lineB.removeElement(lineB.getElementIndex(endElement))));

            assertAll(
                () -> assertThat(lineA.endIndexOf(crossTie))
                    .as("an endpoint in no line has no direction to offer")
                    .isEqualTo(SpanBound.ABSENT),
                () -> assertThat(lineA.findTieAt(lineA.getElementIndex(anchorElement)))
                    .as("the half stops covering anything rather than running off an edge")
                    .isNull()
            );
        }

        @Test
        void testFarEndpointInALineTheSongNoLongerHoldsResolvesToAbsent() {
            // Song.removeLine leaves the deleted line's elements pointing at it, so the far
            // endpoint still names a Line — one with no earlier-or-later answer.
            crossSong.withoutMutationTracking(() -> crossSong.removeLine(crossSong.indexOfLine(lineB)));

            assertAll(
                () -> assertThat(endElement.getParentLine())
                    .as("precondition: the deleted line still owns its elements")
                    .isSameAs(lineB),
                () -> assertThat(lineA.endIndexOf(crossTie))
                    .as("a line outside the song has no position in the running order")
                    .isEqualTo(SpanBound.ABSENT)
            );
        }

        @Test
        void testMergeCandidateWithAnOffLineBoundIsNotAbsorbed() {
            // A hairpin whose endpoints both sit in line B, yet which line A's span list also
            // holds — the shape a cross-line span leaves behind. Read as indices, its bounds
            // would drag the added hairpin's anchor to a position line A does not have.
            var strayHairpin = new Crescendo(lineB.getElement(0), lineB.getElement(1));
            crossSong.withoutMutationTracking(() -> lineA.addSpan(strayHairpin));

            var addedHairpin = new Crescendo(lineA.getElement(0), lineA.getElement(1));
            crossSong.withoutMutationTracking(() -> lineA.addCrescendo(addedHairpin));

            assertAll(
                () -> assertThat(lineA.getSpans())
                    .as("the off-line hairpin is not absorbed into the added one")
                    .contains(strayHairpin, addedHairpin),
                () -> assertThat(lineA.anchorIndexOf(addedHairpin))
                    .as("the added hairpin keeps the anchor it was drawn with")
                    .isEqualTo(new SpanBound.At(0)),
                () -> assertThat(lineA.endIndexOf(addedHairpin))
                    .as("the added hairpin keeps the end it was drawn with")
                    .isEqualTo(new SpanBound.At(1))
            );
        }
    }
}
