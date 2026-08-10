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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Duration;
import songscribe.dom.ElementChange;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.SpanOutcome;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.Ending;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;

class PasteSpanReconciliationTest extends UnitTest {

    private static final int TRIPLET_GRADE = 3;

    /** A triplet occupies the time of two notes of its written value. */
    private static final int TRIPLET_NORMAL_NOTES = 2;

    private static final int NO_DOTS = 0;

    /** Index of the element the six-note fixture's spans anchor to. */
    private static final int SPAN_ANCHOR_INDEX = 1;

    /** Index of the element the six-note fixture's spans end on. */
    private static final int SPAN_END_INDEX = 4;

    /** An index strictly inside {@code [SPAN_ANCHOR_INDEX, SPAN_END_INDEX]}. */
    private static final int INTERIOR_INDEX = 2;

    private static final int FIXTURE_NOTE_COUNT = 6;

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
            line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX), TRIPLET_GRADE,
            TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
    }

    /** A two-note fragment carrying exactly the spans built by {@code spans}. */
    private static List<StaffElement> fragmentNotes() {
        return List.of(crotchet(), crotchet());
    }

    /**
     * Reconciles a paste of two ordinary notes carrying {@code spans} — what every test says
     * unless it names the elements itself. Only the tie rule reads the pasted elements, and
     * only to ask whether they are separators a tie may cross; two notes are not.
     */
    private static PasteSpanReconciliation reconcileNotePaste(
        Line line,
        int insertIndex,
        InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
        List<Span> spans
    ) {
        return PasteSpanReconciliation.reconcile(
            line, insertIndex, deleteRange, fragmentNotes(), spans);
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
            line.addSpan(tuplet);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);

            var result = reconcileNotePaste(
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
            line.addSpan(beam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
                line, INTERIOR_INDEX, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).containsExactly(beam);
            assertThat(result.fragmentSpans()).isEmpty();
        }

        @Test
        void testTieStraddledByInsertionIsRemovedButFragmentTieSurvives() {
            var line = sixNoteLine();
            var tie = new Tie(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(tie);

            var notes = fragmentNotes();
            var fragmentTie = new Tie(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
                line, INTERIOR_INDEX, null, List.of(fragmentTie));

            assertThat(result.targetSpansToRemove()).containsExactly(tie);
            assertThat(result.fragmentSpans())
                .as("the fragment's tie still binds the fragment's own notes")
                .containsExactly(fragmentTie);
        }

        /**
         * The straddle rule and the per-clone sweep in {@code Line.addElement} answer the same
         * question about the same barline landing between the same two tied notes, so they
         * have to answer it the same way.
         */
        @Test
        void testPastingOnlyABarlineBetweenTiedNotesLeavesTheTieAlone() {
            var line = sixNoteLine();
            var tie = new Tie(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(tie);

            var insertion = ElementChange.forInsertion(
                line, INTERIOR_INDEX, singleBarline());

            assertThat(tie.outcomeFor(insertion, line))
                .as("drawing a barline in between the tied notes leaves the tie")
                .isSameAs(SpanOutcome.Simple.KEEP);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null,
                List.of(singleBarline()), List.of());

            assertThat(result.targetSpansToRemove())
                .as("so pasting that same barline must leave it too")
                .isEmpty();
        }

        @Test
        void testPastingANoteAmongSeparatorsBetweenTiedNotesStillRemovesTheTie() {
            var line = sixNoteLine();
            var tie = new Tie(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(tie);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null,
                List.of(singleBarline(), crotchet()), List.of());

            assertThat(result.targetSpansToRemove())
                .as("one sounding note between the tied notes is enough to break the tie")
                .containsExactly(tie);
        }

        @Test
        void testPastingAFinalDoubleBarlineBetweenTiedNotesRemovesTheTie() {
            var line = sixNoteLine();
            var tie = new Tie(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(tie);

            var result = PasteSpanReconciliation.reconcile(
                line, INTERIOR_INDEX, null,
                List.of(finalDoubleBarline()), List.of());

            assertThat(result.targetSpansToRemove())
                .as("the one barline a tie may not cross, per Tie.isLegalSeparator")
                .containsExactly(tie);
        }

        @Test
        void testTrillStraddledByInsertionIsRemovedButFragmentTrillSurvives() {
            var line = sixNoteLine();
            var trill = new Trill(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(trill);

            var notes = fragmentNotes();
            var fragmentTrill = new Trill(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
                line, INTERIOR_INDEX, null, List.of(fragmentTrill));

            assertThat(result.targetSpansToRemove()).containsExactly(trill);
            assertThat(result.fragmentSpans()).containsExactly(fragmentTrill);
        }

        @Test
        void testHairpinStraddledByInsertionIsKeptAndDropsFragmentHairpin() {
            var line = sixNoteLine();
            var hairpin = new Crescendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(hairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Crescendo(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
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
            line.addSpan(beam);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);

            var result = reconcileNotePaste(
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
            line.addSpan(beam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            // Inserting *at* the anchor pushes the whole span right — it still covers
            // exactly its own notes, so nothing is broken.
            var result = reconcileNotePaste(
                line, SPAN_ANCHOR_INDEX, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentBeam);
        }

        @Test
        void testInsertionPastSpanEndLeavesSpanAlone() {
            var line = sixNoteLine();
            var beam = new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(beam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
                line, SPAN_END_INDEX + 1, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentBeam);
        }

        @Test
        void testFullyReplacedSpanIsLeftToTheDeletionSweepAndFragmentSpanSurvives() {
            var line = sixNoteLine();
            var tuplet = tupletOver(line);
            line.addSpan(tuplet);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);

            // The selection covers the target tuplet exactly: its endpoints are deleted,
            // so Line.removeRange's own sweep drops it and the pasted tuplet replaces it.
            var deleteRange =
                new InsertionSpacingCalculator.DeletedRange(SPAN_ANCHOR_INDEX, SPAN_END_INDEX);

            var result = reconcileNotePaste(
                line, SPAN_ANCHOR_INDEX, deleteRange, List.of(fragmentTuplet));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentTuplet);
        }

        @Test
        void testPartialReplacementLeavingBothEndpointsStraddlesAndRemovesTheSpan() {
            var line = sixNoteLine();
            var tuplet = tupletOver(line);
            line.addSpan(tuplet);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);

            // Replacing only the tuplet's interior leaves both endpoints alive, so the
            // deletion sweep won't touch it — this is the case only the straddle test catches.
            var deleteRange =
                new InsertionSpacingCalculator.DeletedRange(INTERIOR_INDEX, INTERIOR_INDEX);

            var result = reconcileNotePaste(
                line, INTERIOR_INDEX, deleteRange, List.of(fragmentTuplet));

            assertThat(result.targetSpansToRemove()).containsExactly(tuplet);
            assertThat(result.fragmentSpans()).isEmpty();
        }

        @Test
        void testHairpinFullyReplacedKeepsTheFragmentHairpin() {
            var line = sixNoteLine();
            var hairpin = new Crescendo(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(hairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Crescendo(notes.get(0), notes.get(1));

            var deleteRange =
                new InsertionSpacingCalculator.DeletedRange(SPAN_ANCHOR_INDEX, SPAN_END_INDEX);

            var result = reconcileNotePaste(
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
            BiFunction<? super StaffElement, ? super StaffElement, ? extends Span> spanFactory
        ) {
            var line = sixNoteLine();
            var destinationSpan =
                spanFactory.apply(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(destinationSpan);

            var notes = fragmentNotes();
            var fragmentSpan = spanFactory.apply(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
                line, INTERIOR_INDEX, null, List.of(fragmentSpan));

            var destinationSurvives = !result.targetSpansToRemove().contains(destinationSpan);
            var fragmentSurvives = result.fragmentSpans().contains(fragmentSpan);

            assertThat(destinationSurvives && fragmentSurvives)
                .as("destination=%s fragment=%s must not both survive", destinationSurvives, fragmentSurvives)
                .isFalse();
        }

        @Test
        void testTupletsCannotBothSurvive() {
            assertAtMostOneSurvives((anchor, end) -> new Tuplet(anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));
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
        TUPLET((anchor, end) -> new Tuplet(anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS), true, false),
        BEAM(Beam::new, true, false),
        TIE(Tie::new, true, true),
        TRILL(Trill::new, true, true),
        CRESCENDO(Crescendo::new, false, false),
        DIMINUENDO(Diminuendo::new, false, false),
        ENDING(Ending::new, false, false);

        private final BiFunction<? super StaffElement, ? super StaffElement, ? extends Span> factory;
        private final boolean destinationRemovedOnStraddle;
        private final boolean fragmentKeptOnStraddle;

        SpanKind(
            BiFunction<? super StaffElement, ? super StaffElement, ? extends Span> factory,
            boolean destinationRemovedOnStraddle,
            boolean fragmentKeptOnStraddle
        ) {
            this.factory = factory;
            this.destinationRemovedOnStraddle = destinationRemovedOnStraddle;
            this.fragmentKeptOnStraddle = fragmentKeptOnStraddle;
        }

        Span create(StaffElement anchor, StaffElement end) {
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
        line.addSpan(destinationSpan);

        var notes = fragmentNotes();
        var fragmentSpan = kind.create(notes.get(0), notes.get(1));

        var result = reconcileNotePaste(
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
            line.addSpan(destinationTuplet);
            line.addSpan(destinationBeam);

            var notes = fragmentNotes();
            var fragmentTuplet = new Tuplet(notes.get(0), notes.get(1), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
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
            line.addSpan(destinationHairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Diminuendo(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
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
            line.addSpan(destinationHairpin);

            var notes = fragmentNotes();
            var fragmentCrescendo = new Crescendo(notes.get(0), notes.get(0));
            var fragmentDiminuendo = new Diminuendo(notes.get(1), notes.get(1));

            var result = reconcileNotePaste(
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
            line.addSpan(destinationHairpin);

            var notes = fragmentNotes();
            var fragmentHairpin = new Diminuendo(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
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
            line.addSpan(destinationBeam);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));
            var fragmentTie = new Tie(notes.get(0), notes.get(1));
            var fragmentHairpin = new Crescendo(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(
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
            line.addSpan(before);
            line.addSpan(after);

            var notes = fragmentNotes();
            var fragmentBeam = new Beam(notes.get(0), notes.get(1));

            var result = reconcileNotePaste(line, 2, null, List.of(fragmentBeam));

            assertThat(result.targetSpansToRemove()).isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(fragmentBeam);
        }

        @Test
        void testAFragmentWithNoSpansStillRemovesTheStraddledDestinationSpan() {
            var line = sixNoteLine();
            var destinationBeam =
                new Beam(line.getElement(SPAN_ANCHOR_INDEX), line.getElement(SPAN_END_INDEX));
            line.addSpan(destinationBeam);

            var result = reconcileNotePaste(
                line, INTERIOR_INDEX, null, List.of());

            assertThat(result.targetSpansToRemove()).containsExactly(destinationBeam);
            assertThat(result.fragmentSpans()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Ties straddling a line boundary
    // -----------------------------------------------------------------------

    /**
     * A tie is the only span whose two endpoints can sit in different lines, so it is the only
     * one the straddle rule can be asked about while holding an index it cannot compare. The
     * rule declines those and leaves them to the per-clone sweep in {@code Line.addElement}.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CrossLineTies {

        private static final int FIRST_LINE_NOTE_COUNT = 2;

        /** The tie's anchor: the last note of the first line. */
        private static final int CROSS_LINE_ANCHOR_INDEX = FIRST_LINE_NOTE_COUNT - 1;

        /** Legal separators heading the second line, so the tie's end is not at index 0. */
        private static final int SECOND_LINE_SEPARATOR_COUNT = 2;

        /** The tie's end: the first note of the second line, behind those separators. */
        private static final int CROSS_LINE_END_INDEX = SECOND_LINE_SEPARATOR_COUNT;

        private Song song = new Song();
        private Line firstLine = song.getLine(0);
        private Line secondLine = new Line(song);
        private Tie crossLineTie =
            new Tie(crotchet(), crotchet());

        /**
         * Two lines joined by a tie, shaped so the endpoints cannot be compared by luck: the
         * anchor's index in the first line is <em>lower</em> than the end's index in the
         * second, which is what an index test that mixes the two lines needs to get wrong.
         */
        @BeforeEach
        void setUpCrossLineTie() {
            song = new Song();
            firstLine = song.getLine(0);
            secondLine = new Line(song);

            song.withoutMutationTracking(() -> {
                song.addLine(secondLine);

                for (var i = 0; i < FIRST_LINE_NOTE_COUNT; i++) {
                    firstLine.addElement(crotchet());
                }

                for (var i = 0; i < SECOND_LINE_SEPARATOR_COUNT; i++) {
                    secondLine.addElement(singleBarline());
                }

                secondLine.addElement(crotchet());
            });

            crossLineTie = new Tie(
                firstLine.getElement(CROSS_LINE_ANCHOR_INDEX),
                secondLine.getElement(CROSS_LINE_END_INDEX));

            song.withoutMutationTracking(() -> firstLine.addTie(crossLineTie));
        }

        @Test
        void testPasteBeforeTheEndNoteLeavesTheCrossLineTieToTheInsertionSweep() {
            // Squarely between the tied notes, and the shape that makes the mixed-line
            // comparison read as a straddle: anchor 1 in the first line is below the paste
            // index in the second, and the end sits at that index.
            var result = reconcileNotePaste(
                secondLine, CROSS_LINE_END_INDEX, null, List.of());

            assertThat(result.targetSpansToRemove())
                .as("the straddle rule cannot compare an index in one line against another's")
                .isEmpty();
        }

        @Test
        void testPasteAfterTheAnchorLeavesTheCrossLineTieToTheInsertionSweep() {
            var result = reconcileNotePaste(
                firstLine, FIRST_LINE_NOTE_COUNT, null, List.of());

            assertThat(result.targetSpansToRemove()).isEmpty();
        }

        @Test
        void testASameLineSpanOnALineHostingACrossLineTieIsStillReconciled() {
            var beam = new Beam(
                secondLine.getElement(0), secondLine.getElement(CROSS_LINE_END_INDEX));
            song.withoutMutationTracking(() -> secondLine.addSpan(beam));

            var result = reconcileNotePaste(
                secondLine, CROSS_LINE_END_INDEX, null, List.of());

            assertThat(result.targetSpansToRemove())
                .as("skipping the tie must not stop the loop reaching the rest of the line")
                .containsExactly(beam);
        }
    }

    // -----------------------------------------------------------------------
    // Pasted tuplets the destination's beat context rejects (#604)
    // -----------------------------------------------------------------------

    /**
     * A tuplet on the clipboard already carries the ratio it was created or loaded with, so
     * pasting does not re-derive one from the destination's beat. Re-deriving would delete
     * the very tuplets the model preserves deliberately — a document may legitimately state
     * a ratio the convention would not choose, and copying such a group and pasting it back
     * where it came from would destroy it.
     * <p>
     * What the destination can still break is the span's <em>placement</em>: a beat barrier,
     * or a barline, repeat or breath mark now sitting inside it. Those drop the bracket.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TupletsRejectedByTheTarget {

        /** Three notes under a 3 — the fragment every test here pastes. */
        private static final int TRIPLET_NOTE_COUNT = TRIPLET_GRADE;

        /** Where the fragment lands on an otherwise empty destination line. */
        private static final int EMPTY_LINE_PASTE_INDEX = 0;

        /** A song whose initial tempo puts {@code beat} in effect throughout. */
        private Song songWithBeat(Duration beat) {
            var song = new Song();

            song.withoutMutationTracking(() -> song.setTempo(new Tempo(
                Tempo.DEFAULT_BPM, beat, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)));

            return song;
        }

        private List<StaffElement> tripletQuavers() {
            var notes = new ArrayList<StaffElement>(TRIPLET_NOTE_COUNT);

            for (var i = 0; i < TRIPLET_NOTE_COUNT; i++) {
                notes.add(quaver());
            }

            return notes;
        }

        private Tuplet tripletOver(List<? extends StaffElement> notes) {
            return new Tuplet(notes.getFirst(), notes.getLast(), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.QUAVER, NO_DOTS);
        }

        /**
         * Pastes in the order production does: reconcile against the pre-mutation line,
         * insert the fragment's elements, then apply the target-context rule to what the
         * straddle rule left.
         */
        private PasteSpanReconciliation paste(
            Line line, int insertIndex, List<? extends StaffElement> notes, List<Span> spans
        ) {
            var reconciliation = reconcileNotePaste(line, insertIndex, null, spans);

            for (var i = 0; i < notes.size(); i++) {
                line.addElement(insertIndex + i, notes.get(i));
            }

            return reconciliation.dropTupletsRejectedByTarget(line);
        }

        /**
         * Three quavers under a 3 are 3:2 eighths. A dotted-quarter beat would never derive
         * that ratio — three eighths already fill the beat — but the group states it, and a
         * stated ratio means the same thing wherever it is written.
         */
        @Test
        void testATripletCopiedFromAQuarterBeatKeepsItsRatioUnderADottedQuarterBeat() {
            var song = songWithBeat(Duration.CROTCHET_DOTTED);
            var line = song.getLine(0);
            var notes = tripletQuavers();
            var tuplet = tripletOver(notes);

            var result = song.withModificationResult(
                () -> paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet)));

            assertThat(result.fragmentSpans())
                .as("the ratio travels with the tuplet rather than being re-derived")
                .containsExactly(tuplet);
            assertThat(result.tupletsRejectedByTarget()).isEmpty();
        }

        /**
         * The round trip the stated-ratio rule exists to protect. A 3:2 over three eighths
         * under a dotted-quarter beat is a ratio the convention would not derive, so
         * re-deriving on paste would delete a group the user merely moved.
         */
        @Test
        void testCopyingATupletAndPastingItBackDoesNotDestroyIt() {
            var song = songWithBeat(Duration.CROTCHET_DOTTED);
            var line = song.getLine(0);
            var notes = tripletQuavers();
            var tuplet = tripletOver(notes);

            var result = song.withModificationResult(
                () -> paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet)));

            assertThat(result.tupletsRejectedByTarget())
                .as("pasting a group back into the context it came from must not delete it")
                .isEmpty();
            assertThat(result.fragmentSpans()).containsExactly(tuplet);
        }

        /**
         * The destination can still break the span's placement. A breath mark inside the
         * pasted group splits it, so the bracket goes even though the ratio is untouched —
         * and the notes stay, because only the bracket was ever at stake.
         */
        @Test
        void testABreathMarkInsideThePastedSpanDropsTheBracket() {
            var song = songWithBeat(Duration.CROTCHET);
            var line = song.getLine(0);
            var notes = new ArrayList<>(tripletQuavers());
            var tuplet = new Tuplet(notes.getFirst(), notes.getLast(), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.QUAVER, NO_DOTS);
            notes.add(1, breathMark());

            var result = song.withModificationResult(
                () -> paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet)));

            assertThat(result.tupletsRejectedByTarget())
                .as("a breath mark inside the span breaks it wherever it was pasted")
                .containsExactly(tuplet);
            assertThat(result.fragmentSpans()).isEmpty();
            assertThat(line.getElements(EMPTY_LINE_PASTE_INDEX, notes.size() - 1))
                .as("the notes survive — only the bracket goes")
                .containsExactlyElementsOf(notes);
        }

        @Test
        void testTheSameTripletIsKeptUnderACompatibleBeat() {
            var song = songWithBeat(Duration.CROTCHET);
            var line = song.getLine(0);
            var notes = tripletQuavers();
            var tuplet = tripletOver(notes);

            var result = song.withModificationResult(
                () -> paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet)));

            assertThat(result.fragmentSpans()).containsExactly(tuplet);
            assertThat(result.tupletsRejectedByTarget()).isEmpty();
        }

        @Test
        void testOnlyTheTupletIsJudged() {
            // A beam says nothing about timing, so the rule has no opinion about it: it
            // survives the paste that costs the tuplet its bracket.
            var song = songWithBeat(Duration.CROTCHET);
            var line = song.getLine(0);
            var notes = new ArrayList<>(tripletQuavers());
            var tuplet = new Tuplet(notes.getFirst(), notes.getLast(), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.QUAVER, NO_DOTS);
            var beam = new Beam(notes.getFirst(), notes.getLast());
            notes.add(1, breathMark());

            var result = song.withModificationResult(
                () -> paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet, beam)));

            assertThat(result.fragmentSpans()).containsExactly(beam);
            assertThat(result.tupletsRejectedByTarget()).containsExactly(tuplet);
        }

        @Test
        void testATupletTheStraddleRuleAlreadyDroppedIsNotCountedAsRejectedByTheTarget() {
            // The two drop reasons stay tellable apart: this triplet would pass the
            // target-context test outright — it is three crotchets at a quarter beat —
            // and is gone purely because it landed inside a straddled destination tuplet.
            var song = songWithBeat(Duration.CROTCHET);
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < FIXTURE_NOTE_COUNT; i++) {
                    line.addElement(crotchet());
                }

                line.addSpan(tupletOver(line));
            });

            var notes = List.of(crotchet(), crotchet(), crotchet());
            var fragmentTuplet = new Tuplet(notes.getFirst(), notes.getLast(), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);

            var result = song.withModificationResult(
                () -> paste(line, INTERIOR_INDEX, notes, List.of(fragmentTuplet)));

            assertThat(result.fragmentSpans()).isEmpty();
            assertThat(result.tupletsRejectedByTarget())
                .as("dropped by the straddle rule, not by the destination's beat")
                .isEmpty();
        }

        @Test
        void testNothingIsRejectedDuringReplay() {
            // Undo and redo re-apply a recorded batch that already reflects every drop
            // the paste made, so the rule must stay out of the replay entirely. The span
            // used here is one the rule would otherwise reject outright.
            var song = songWithBeat(Duration.CROTCHET);
            var line = song.getLine(0);
            var notes = new ArrayList<>(tripletQuavers());
            var tuplet = new Tuplet(notes.getFirst(), notes.getLast(), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.QUAVER, NO_DOTS);
            notes.add(1, breathMark());

            var result = song.withModificationResult(() -> {
                var holder = new PasteSpanReconciliation[1];
                song.withReplay(
                    () -> holder[0] = paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet)));

                return holder[0];
            });

            assertThat(result.fragmentSpans()).containsExactly(tuplet);
            assertThat(result.tupletsRejectedByTarget()).isEmpty();
        }

        /**
         * A rejected bracket is never added, so it records no mutation of its own: the
         * paste's single bracket holds the paste and nothing else, and one undo of that
         * bracket therefore restores the pre-paste line — pasted notes gone, dropped
         * bracket still absent — with no second step to reach for. The undo engine itself
         * is driven from {@code songscribe.undo}, whose test-only reset is package-private
         * there; what is checkable here is the batch that engine would replay.
         */
        @Test
        void testARejectedTupletAddsNothingToThePastesUndoStep() {
            var song = songWithBeat(Duration.CROTCHET_DOTTED);
            var line = song.getLine(0);
            var notes = tripletQuavers();
            var tuplet = tripletOver(notes);

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                song.withModification(
                    () -> paste(line, EMPTY_LINE_PASTE_INDEX, notes, List.of(tuplet)));

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());

                var mutations = captor.getAllValues().stream()
                    .filter(SongDidChangeNotification.class::isInstance)
                    .map(SongDidChangeNotification.class::cast)
                    .flatMap(notification -> notification.getMutations().stream())
                    .toList();

                assertThat(mutations)
                    .as("the paste's own insertions are recorded")
                    .hasAtLeastOneElementOfType(ElementInsertion.class);
                assertThat(mutations)
                    .as("the rejected bracket was never added, so there is nothing to undo")
                    .doesNotHaveAnyElementsOfTypes(TupletAddition.class, TupletRemoval.class);
            }
        }
    }
}
