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

package songscribe.ui.clipboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;
import songscribe.layout.InsertionSpacingCalculator;

class PasteSpanReconciliationTest extends UnitTest {

    private static final int TRIPLET_GRADE = 3;

    /** Index of the element the six-note fixture's spans anchor to. */
    private static final int SPAN_ANCHOR_INDEX = 1;

    /** Index of the element the six-note fixture's spans end on. */
    private static final int SPAN_END_INDEX = 4;

    /** An index strictly inside {@code [SPAN_ANCHOR_INDEX, SPAN_END_INDEX]}. */
    private static final int INTERIOR_INDEX = 2;

    private static final int FIXTURE_NOTE_COUNT = 6;

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    /** A six-note line with no spans yet. Spans are added per test over [1, 4]. */
    private Line sixNoteLine() {
        var line = detachedLine();

        for (var i = 0; i < FIXTURE_NOTE_COUNT; i++) {
            line.addElement(crotchet());
        }

        return line;
    }

    private static Tuplet tupletOver(Line line) {
        return new Tuplet(
            line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX), TRIPLET_GRADE);
    }

    /** A two-note fragment carrying exactly the spans built by {@code spans}. */
    private static List<StaffElement> fragmentNotes() {
        return List.of(crotchet(), crotchet());
    }

    // -----------------------------------------------------------------------
    // Straddled target spans — pure insertion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StraddledTargetSpans {

        @Test
        void testTupletStraddledByInsertionIsRemovedAndDropsFragmentTuplets() {
            var line = sixNoteLine();
            var tuplet = tupletOver(line);
            line.addRangeElement(tuplet);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentTuplet));

            assertThat(result.targetSpansToRemove()).containsExactly(tuplet);
            assertThat(result.fragmentSpans())
                .as("a pasted tuplet dropped into a broken tuplet is equally wrong")
                .isEmpty();
        }

        @Test
        void testBeamStraddledByInsertionIsRemovedAndDropsFragmentBeams() {
            var line = sixNoteLine();
            var beam = new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(beam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).containsExactly(beam);
            assertThat(result.fragmentSpans()).isEmpty();
        }

        @Test
        void testTieStraddledByInsertionIsRemovedButFragmentTieSurvives() {
            var line = sixNoteLine();
            var tie = new Tie(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(tie);

            var notes = fragmentNotes();
            var fragmentTie = new Tie(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentTie));

            assertThat(result.targetSpansToRemove()).containsExactly(tie);
            assertThat(result.fragmentSpans())
                .as("the fragment's tie still binds the fragment's own notes")
                .containsExactly(fragmentTie);
        }

        @Test
        void testTrillStraddledByInsertionIsRemovedButFragmentTrillSurvives() {
            var line = sixNoteLine();
            var trill = new Trill(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(trill);

            var notes = fragmentNotes();
            var fragmentTrill = new Trill(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentTrill));

            assertThat(result.targetSpansToRemove()).containsExactly(trill);
            assertThat(result.fragmentSpans()).containsExactly(fragmentTrill);
        }

        @Test
        void testHairpinStraddledByInsertionIsKeptAndDropsFragmentHairpin() {
            var line = sixNoteLine();
            var hairpin = new Crescendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(hairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Crescendo(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentHairpin));

            assertThat(result.targetSpansToRemove())
                .as("a hairpin reads correctly over any span of notes, so the target's wins")
                .isEmpty();
            assertThat(result.fragmentSpans()).isEmpty();
        }

        @Test
        void testStraddledBeamDoesNotDropAnUnrelatedFragmentTuplet() {
            var line = sixNoteLine();
            var beam = new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(beam);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentTuplet));

            assertThat(result.targetSpansToRemove()).containsExactly(beam);
            assertThat(result.fragmentSpans())
                .as("the source drop is per-kind — a broken beam does not kill a pasted tuplet")
                .containsExactly(fragmentTuplet);
        }
    }

    // -----------------------------------------------------------------------
    // Spans the paste does not straddle
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UnstraddledTargetSpans {

        @Test
        void testInsertionAtSpanAnchorLeavesSpanAlone() {
            var line = sixNoteLine();
            var beam = new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(beam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            // Inserting *at* the anchor pushes the whole span right — it still covers
            // exactly its own notes, so nothing is broken.
            var result = PasteSpanReconciliation.reconcile(
                line, SPAN_ANCHOR_INDEX, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentBeam);
        }

        @Test
        void testInsertionPastSpanEndLeavesSpanAlone() {
            var line = sixNoteLine();
            var beam = new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(beam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, SPAN_END_INDEX + 1, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentBeam);
        }

        @Test
        void testFullyReplacedSpanIsLeftToTheDeletionSweepAndFragmentSpanSurvives() {
            var line = sixNoteLine();
            var tuplet = tupletOver(line);
            line.addRangeElement(tuplet);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE);

            // The selection covers the target tuplet exactly: its endpoints are deleted,
            // so Line.removeRange's own sweep drops it and the pasted tuplet replaces it.
            var deleteRange =
                new InsertionSpacingCalculator.DeletedRange(SPAN_ANCHOR_INDEX, SPAN_END_INDEX);

            var result = PasteSpanReconciliation.reconcile(
                line, SPAN_ANCHOR_INDEX, deleteRange, List.of(fragmentTuplet));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentTuplet);
        }

        @Test
        void testPartialReplacementLeavingBothEndpointsStraddlesAndRemovesTheSpan() {
            var line = sixNoteLine();
            var tuplet = tupletOver(line);
            line.addRangeElement(tuplet);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE);

            // Replacing only the tuplet's interior leaves both endpoints alive, so the
            // deletion sweep won't touch it — this is the case only the straddle test catches.
            var deleteRange =
                new InsertionSpacingCalculator.DeletedRange(INTERIOR_INDEX, INTERIOR_INDEX);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, deleteRange, List.of(fragmentTuplet));

            assertThat(result.targetSpansToRemove()).containsExactly(tuplet);
            assertThat(result.fragmentSpans()).isEmpty();
        }

        @Test
        void testHairpinFullyReplacedKeepsTheFragmentHairpin() {
            var line = sixNoteLine();
            var hairpin = new Crescendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(hairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Crescendo(notes.get(0), notes.get(1));

            var deleteRange =
                new InsertionSpacingCalculator.DeletedRange(SPAN_ANCHOR_INDEX, SPAN_END_INDEX);

            var result = PasteSpanReconciliation.reconcile(
                line, SPAN_ANCHOR_INDEX, deleteRange, List.of(fragmentHairpin));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans())
                .as("selecting the whole target hairpin replaces it with the source hairpin")
                .containsExactly(fragmentHairpin);
        }
    }

    // -----------------------------------------------------------------------
    // The invariant the per-kind rules exist to uphold
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NoSameKindOverlapSurvives {

        /**
         * For every span kind, a destination span covering [1, 4] and a fragment span
         * of the same kind must not both survive a paste landing inside it — whichever
         * rule applies, the result can never be two overlapping same-kind spans.
         *
         * <p>This is the structural guarantee, asserted independently of which side
         * each per-kind rule happens to favour: the kind-by-kind tests above pin down
         * <em>which</em> one survives, this one pins down that not both do.
         */
        private void assertAtMostOneSurvives(
            BiFunction<StaffElement, StaffElement, RangeElement> spanFactory
        ) {
            var line = sixNoteLine();
            var destinationSpan =
                spanFactory.apply(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationSpan);

            var notes = fragmentNotes();
            var fragmentSpan = spanFactory.apply(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentSpan));

            var destinationSurvives = !result.targetSpansToRemove().contains(destinationSpan);
            var fragmentSurvives = result.fragmentSpans().contains(fragmentSpan);

            assertThat(destinationSurvives && fragmentSurvives)
                .as("destination=%s fragment=%s must not both survive", destinationSurvives, fragmentSurvives)
                .isFalse();
        }

        @Test
        void testTupletsCannotBothSurvive() {
            assertAtMostOneSurvives((anchor, end) -> new Tuplet(anchor, end, TRIPLET_GRADE));
        }

        @Test
        void testBeamsCannotBothSurvive() {
            assertAtMostOneSurvives(Beam::new);
        }

        @Test
        void testTiesCannotBothSurvive() {
            assertAtMostOneSurvives(Tie::new);
        }

        @Test
        void testTrillsCannotBothSurvive() {
            assertAtMostOneSurvives(Trill::new);
        }

        @Test
        void testHairpinsCannotBothSurvive() {
            assertAtMostOneSurvives(Crescendo::new);
        }

        @Test
        void testEndingsCannotBothSurvive() {
            assertAtMostOneSurvives(Ending::new);
        }
    }

    // -----------------------------------------------------------------------
    // The full kind x placement matrix
    // -----------------------------------------------------------------------

    /** The span kinds the reconciler switches on, with their straddle policy. */
    private enum SpanKind {
        TUPLET((anchor, end) -> new Tuplet(anchor, end, TRIPLET_GRADE), true, false),
        BEAM(Beam::new, true, false),
        TIE(Tie::new, true, true),
        TRILL(Trill::new, true, true),
        CRESCENDO(Crescendo::new, false, false),
        DIMINUENDO(Diminuendo::new, false, false),
        ENDING(Ending::new, false, false);

        private final BiFunction<StaffElement, StaffElement, RangeElement> factory;
        private final boolean destinationRemovedOnStraddle;
        private final boolean fragmentKeptOnStraddle;

        SpanKind(
            BiFunction<StaffElement, StaffElement, RangeElement> factory,
            boolean destinationRemovedOnStraddle,
            boolean fragmentKeptOnStraddle
        ) {
            this.factory = factory;
            this.destinationRemovedOnStraddle = destinationRemovedOnStraddle;
            this.fragmentKeptOnStraddle = fragmentKeptOnStraddle;
        }

        RangeElement create(StaffElement anchor, StaffElement end) {
            return factory.apply(anchor, end);
        }
    }

    /**
     * One way a paste can sit relative to a destination span covering
     * {@code [SPAN_ANCHOR_INDEX, SPAN_END_INDEX]} on the six-note fixture.
     *
     * @param straddles Whether the paste lands strictly inside the span with both of
     *                  its endpoints surviving — the only case the reconciler acts on
     */
    private record Placement(
        String name,
        int insertIndex,
        InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
        boolean straddles
    ) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Placement> placements() {
        return List.of(
            new Placement("insert strictly inside", INTERIOR_INDEX, null, true),
            new Placement("insert at the span's anchor", SPAN_ANCHOR_INDEX, null, false),
            new Placement("insert just past the span's end", SPAN_END_INDEX + 1, null, false),
            new Placement("insert at the start of the line", 0, null, false),
            new Placement("insert at the end of the line", FIXTURE_NOTE_COUNT, null, false),
            new Placement(
                "replace the span exactly",
                SPAN_ANCHOR_INDEX,
                new InsertionSpacingCalculator.DeletedRange(SPAN_ANCHOR_INDEX, SPAN_END_INDEX),
                false),
            new Placement(
                "replace the span's interior only",
                INTERIOR_INDEX,
                new InsertionSpacingCalculator.DeletedRange(INTERIOR_INDEX, INTERIOR_INDEX),
                true),
            // The next two clip the span at an edge: one endpoint is deleted, so the
            // deletion sweep removes the destination span and the reconciler stays out
            // of it — deliberately keeping the pasted group, which lands contiguous at
            // the boundary rather than interleaved with the orphaned notes. See the
            // "Only a straddle counts" note on PasteSpanReconciliation.
            new Placement(
                "replace past the span's end, deleting its end element",
                SPAN_END_INDEX - 1,
                new InsertionSpacingCalculator.DeletedRange(SPAN_END_INDEX - 1, FIXTURE_NOTE_COUNT - 1),
                false),
            new Placement(
                "replace before the span, deleting its anchor",
                0,
                new InsertionSpacingCalculator.DeletedRange(0, SPAN_ANCHOR_INDEX),
                false),
            new Placement(
                "replace the whole line",
                0,
                new InsertionSpacingCalculator.DeletedRange(0, FIXTURE_NOTE_COUNT - 1),
                false)
        );
    }

    private static Stream<Arguments> kindAndPlacement() {
        return Arrays.stream(SpanKind.values())
            .flatMap(kind -> placements().stream().map(placement -> Arguments.of(kind, placement)));
    }

    /**
     * The reconciler acts on exactly one situation — a straddle — and its per-kind
     * policy there. Every other placement leaves both sides alone, because the span
     * either keeps covering only its own notes or loses an endpoint to the deletion
     * sweep, which removes it without the reconciler's help.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("kindAndPlacement")
    void testReconciliationMatrix(SpanKind kind, Placement placement) {
        var line = sixNoteLine();
        var destinationSpan =
            kind.create(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
        line.addRangeElement(destinationSpan);

        var notes = fragmentNotes();
        var fragmentSpan = kind.create(notes.get(0), notes.get(1));

        var result = PasteSpanReconciliation.reconcile(
            line, placement.insertIndex(), placement.deleteRange(), List.of(fragmentSpan));

        var expectedDestinationRemoved = placement.straddles() && kind.destinationRemovedOnStraddle;
        var expectedFragmentKept = !placement.straddles() || kind.fragmentKeptOnStraddle;

        assertThat(result.targetSpansToRemove().contains(destinationSpan))
            .as("destination %s removed", kind)
            .isEqualTo(expectedDestinationRemoved);
        assertThat(result.fragmentSpans().contains(fragmentSpan))
            .as("fragment %s kept", kind)
            .isEqualTo(expectedFragmentKept);
    }

    // -----------------------------------------------------------------------
    // Multi-span situations
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MultipleSpans {

        @Test
        void testBeamedTripletPastedInsideABeamedTupletDropsEveryGroupOnBothSides() {
            // The literal example from #614.
            var line = sixNoteLine();
            var destinationTuplet = tupletOver(line);
            var destinationBeam =
                new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationTuplet);
            line.addRangeElement(destinationBeam);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE);
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentTuplet, fragmentBeam));

            assertThat(result.targetSpansToRemove())
                .containsExactlyInAnyOrder(destinationTuplet, destinationBeam);
            assertThat(result.fragmentSpans())
                .as("beams and tuplets on both sides are removed")
                .isEmpty();
        }

        @Test
        void testStraddledCrescendoIsRemovedByAContradictingFragmentDiminuendo() {
            // The destination hairpin only wins while the fragment says nothing that
            // contradicts it. A diminuendo nested inside a crescendo is a contradiction
            // no widening can fix, so here the fragment's hairpin wins instead.
            var line = sixNoteLine();
            var destinationHairpin =
                new Crescendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationHairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Diminuendo(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentHairpin));

            assertThat(result.targetSpansToRemove()).containsExactly(destinationHairpin);
            assertThat(result.fragmentSpans()).containsExactly(fragmentHairpin);
        }

        @Test
        void testAContradictingFragmentHairpinAlsoRescuesItsSameTypeSibling() {
            // One different-type fragment hairpin settles the destination hairpin's
            // fate for the whole paste: it is removed, so nothing is left for a
            // same-type fragment hairpin to be redundant with.
            var line = sixNoteLine();
            var destinationHairpin =
                new Crescendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationHairpin);

            var notes = fragmentNotes();
            var fragmentCrescendo = new Crescendo(notes.get(0), notes.get(0));
            var fragmentDiminuendo = new Diminuendo(notes.get(1), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentCrescendo, fragmentDiminuendo));

            assertThat(result.targetSpansToRemove()).containsExactly(destinationHairpin);
            assertThat(result.fragmentSpans())
                .containsExactly(fragmentCrescendo, fragmentDiminuendo);
        }

        @Test
        void testStraddledDiminuendoSurvivesASameTypeFragmentHairpin() {
            var line = sixNoteLine();
            var destinationHairpin =
                new Diminuendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationHairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Diminuendo(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentHairpin));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans())
                .as("a shorter diminuendo inside a diminuendo is redundant")
                .isEmpty();
        }

        @Test
        void testOnlyTheStraddledKindsAreDroppedFromAMixedFragment() {
            var line = sixNoteLine();
            var destinationBeam =
                new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationBeam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));
            var fragmentTie = new Tie(notes.get(0), notes.get(1));
            var fragmentHairpin = new Crescendo(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of(fragmentBeam, fragmentTie, fragmentHairpin));

            assertThat(result.fragmentSpans())
                .as("only the beam matches the straddled kind")
                .containsExactly(fragmentTie, fragmentHairpin);
        }

        @Test
        void testSpansOnEitherSideOfTheInsertionAreBothLeftAlone() {
            var line = sixNoteLine();
            var before = new Beam(line.getElement(0), line.getElement(1));
            var after = new Beam(line.getElement(3), line.getElement(4));
            line.addRangeElement(before);
            line.addRangeElement(after);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = PasteSpanReconciliation.reconcile(line, 2, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentBeam);
        }

        @Test
        void testAFragmentWithNoSpansStillRemovesTheStraddledDestinationSpan() {
            var line = sixNoteLine();
            var destinationBeam =
                new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addRangeElement(destinationBeam);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null, List.of());

            assertThat(result.targetSpansToRemove()).containsExactly(destinationBeam);
            assertThat(result.fragmentSpans()).isEmpty();
        }
    }
}
