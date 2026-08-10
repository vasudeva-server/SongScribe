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
import static songscribe.dom.StaffElementFactory.semiquaver;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.TupletValidator.Reason;
import songscribe.dom.TupletValidator.Result;
import songscribe.dom.TupletValidator.Strictness;

/**
 * Unit tests for the constraints {@link TupletValidator} applies beyond the ratio itself —
 * the fermata, the beat barrier and the structural boundary — for the strict/lenient split
 * between them, and for the bulk forward walk {@link TupletValidator#validateFrom}.
 *
 * <p>The base fixture is three quarter notes under a quarter beat, which derives cleanly as
 * a 3:2 quarter-note triplet, so every rejection below is caused by the one fact the test
 * adds to it.
 */
class TupletValidatorConstraintsTest extends UnitTest {

    // Printed tuplet numbers (N)
    private static final int GRADE_TRIPLET = 3;
    private static final int GRADE_QUADRUPLET = 4;

    // Normal-note counts (M)
    private static final int NORMAL_TWO = 2;
    private static final int NORMAL_SIX = 6;

    /** An undotted written value. */
    private static final int NO_DOTS = 0;

    private static final int FIRST_LINE_INDEX = 0;
    private static final int SECOND_LINE_INDEX = 1;
    private static final int FIRST_INDEX = 0;

    /** The interior element of the three-note base fixture. */
    private static final int MIDDLE_INDEX = 1;

    /** Last element of the first tuplet in the {@link ValidateFrom} fixture. */
    private static final int FIRST_TUPLET_END_INDEX = 2;

    /** Anchor of the second tuplet in the {@link ValidateFrom} fixture. */
    private static final int SECOND_TUPLET_ANCHOR_INDEX = 3;

    /** A tempo whose BPM differs from {@link Tempo#DEFAULT_BPM} but whose beat does not. */
    private static final int FASTER_BPM = Tempo.DEFAULT_BPM * 2;

    /** Verdict counts the forward-walk tests assert on. */
    private static final int ALL_TUPLETS = 3;
    private static final int TUPLETS_AFTER_FIRST = 2;
    private static final int TUPLETS_ON_SECOND_LINE = 1;

    // Positions within the list of verdicts, in document order
    private static final int FIRST_VERDICT = 0;
    private static final int SECOND_VERDICT = 1;
    private static final int THIRD_VERDICT = 2;

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a one-line song whose beat is {@code beat} and whose line holds {@code types}
     * in order. The beat is carried by the song tempo, so the line's own elements carry
     * only whatever a test deliberately attaches to them.
     */
    private static Song songWithBeat(Duration beat, ElementType... types) {
        var song = new Song();
        var line = song.getLine(FIRST_LINE_INDEX);

        song.withoutMutationTracking(() -> {
            song.setTempo(
                new Tempo(Tempo.DEFAULT_BPM, beat, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)
            );

            for (var type : types) {
                line.addElement(type.newInstance());
            }
        });

        return song;
    }

    /** The base fixture: three quarter notes under a quarter beat. */
    private static Song threeQuartersAtSimpleBeat() {
        return songWithBeat(
            Duration.CROTCHET,
            ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET
        );
    }

    /**
     * The index of the last element a fixture actually placed. A {@link Song} maintains a
     * terminal barline at the end of its last line, and a span reaching it would end on a
     * structural element the fixture never asked for.
     */
    private static int lastFixtureIndex(Line line) {
        var lastIndex = line.elementCount() - 1;

        if (line.getElement(lastIndex).getType() == ElementType.FINAL_DOUBLE_BARLINE) {
            return lastIndex - 1;
        }

        return lastIndex;
    }

    /** Validates the whole of {@code song}'s first line as a tuplet of {@code grade}. */
    private static Result validateWholeLine(Song song, int grade, Strictness strictness) {
        var line = song.getLine(FIRST_LINE_INDEX);

        return TupletValidator.validateDerived(
            song, line, FIRST_LINE_INDEX, FIRST_INDEX, lastFixtureIndex(line), grade, strictness
        );
    }

    /**
     * Judges {@code song}'s first line from its start through {@code endIndex} against a
     * ratio the caller states, as a file would. Leniently, because that is the load path:
     * the beat barrier and the structural boundary constrain creation, not a document that
     * already exists.
     */
    private static Result validateStated(
        Song song, Line line, int endIndex, int grade, int normalNotes, ElementType noteValue
    ) {
        var context = TupletValidator.describeSpan(
            song, line, FIRST_LINE_INDEX, FIRST_INDEX, endIndex);

        return TupletValidator.validateStated(
            context, grade, normalNotes, noteValue, NO_DOTS, Strictness.LENIENT);
    }

    private static void attach(Song song, int elementIndex, Attachment attachment) {
        song.withoutMutationTracking(
            () -> song.getLine(FIRST_LINE_INDEX).getElement(elementIndex).addAttachment(attachment)
        );
    }

    /** A metric modulation whose new beat is a dotted quarter. */
    private static BeatChangeAttachment toCompoundBeat() {
        return new BeatChangeAttachment(
            new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED)
        );
    }

    private static TempoChangeAttachment tempoMarking(int bpm, Duration beat) {
        return new TempoChangeAttachment(
            new Tempo(bpm, beat, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)
        );
    }

    // -----------------------------------------------------------------------
    // Constraint 4 — no fermata in the span
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Fermata {

        /**
         * A fermata is excluded under both strictnesses because it inflates performed
         * duration by half again, which would break the exact M:N relationship between the
         * written and the performed span.
         */
        @Test
        void testFermataInSpanIsRejectedUnderBothStrictnesses() {
            assertAll(
                () -> assertRejects(
                    validateWholeLine(songWithFermata(), GRADE_TRIPLET, Strictness.STRICT),
                    Reason.FERMATA
                ),
                () -> assertRejects(
                    validateWholeLine(songWithFermata(), GRADE_TRIPLET, Strictness.LENIENT),
                    Reason.FERMATA
                )
            );
        }

        /** The stated-ratio load path applies the same exclusion. */
        @Test
        void testFermataInSpanIsRejectedForAStatedRatio() {
            var song = songWithFermata();
            var line = song.getLine(FIRST_LINE_INDEX);

            var result = validateStated(
                song, line, lastFixtureIndex(line),
                GRADE_TRIPLET, NORMAL_TWO, ElementType.CROTCHET);

            assertRejects(result, Reason.FERMATA);
        }

        private Song songWithFermata() {
            var song = threeQuartersAtSimpleBeat();
            attach(song, MIDDLE_INDEX, new FermataAttachment());

            return song;
        }
    }

    // -----------------------------------------------------------------------
    // Constraint 6 — no beat barrier inside the span
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BeatBarrier {

        /** A metric modulation inside the span splits it; strict editing refuses. */
        @Test
        void testMetricModulationInsideSpanIsRejected() {
            var song = threeQuartersAtSimpleBeat();
            attach(song, MIDDLE_INDEX, toCompoundBeat());

            assertRejects(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT), Reason.BEAT_BARRIER
            );
        }

        /**
         * The same modulation on the anchor defines the tuplet's beat rather than splitting
         * it. This one restates the quarter beat, so the derivation must be unchanged.
         */
        @Test
        void testMetricModulationOnAnchorIsExempt() {
            var song = threeQuartersAtSimpleBeat();
            attach(
                song, FIRST_INDEX,
                new BeatChangeAttachment(new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET))
            );

            assertDerives(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                NORMAL_TWO, ElementType.CROTCHET
            );
        }

        /** A tempo marking that changes only the BPM leaves the beat, and the span, intact. */
        @Test
        void testTempoMarkingChangingOnlyBpmIsNotABarrier() {
            var song = threeQuartersAtSimpleBeat();
            attach(song, MIDDLE_INDEX, tempoMarking(FASTER_BPM, Duration.CROTCHET));

            assertDerives(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                NORMAL_TWO, ElementType.CROTCHET
            );
        }

        /** A tempo marking that changes the beat's note value is a barrier. */
        @Test
        void testTempoMarkingChangingBeatValueIsABarrier() {
            var song = threeQuartersAtSimpleBeat();
            attach(song, MIDDLE_INDEX, tempoMarking(Tempo.DEFAULT_BPM, Duration.CROTCHET_DOTTED));

            assertRejects(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT), Reason.BEAT_BARRIER
            );
        }
    }

    // -----------------------------------------------------------------------
    // Constraint 7 — no barline, repeat or interior breath mark
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StructuralBoundary {

        @Test
        void testBarlineInsideSpanIsRejected() {
            assertRejects(
                validateWholeLine(
                    songInterruptedBy(ElementType.SINGLE_BARLINE), GRADE_TRIPLET, Strictness.STRICT
                ),
                Reason.STRUCTURAL_BOUNDARY
            );
        }

        @Test
        void testRepeatInsideSpanIsRejected() {
            assertRejects(
                validateWholeLine(
                    songInterruptedBy(ElementType.REPEAT_LEFT), GRADE_TRIPLET, Strictness.STRICT
                ),
                Reason.STRUCTURAL_BOUNDARY
            );
        }

        @Test
        void testInteriorBreathMarkIsRejected() {
            assertRejects(
                validateWholeLine(
                    songInterruptedBy(ElementType.BREATH_MARK), GRADE_TRIPLET, Strictness.STRICT
                ),
                Reason.STRUCTURAL_BOUNDARY
            );
        }

        /**
         * A selection that ends on a note sweeps in a breath mark immediately following it,
         * so a trailing breath mark must not disable the tuplet control.
         */
        @Test
        void testTrailingBreathMarkIsAccepted() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET,
                ElementType.BREATH_MARK
            );

            assertDerives(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                NORMAL_TWO, ElementType.CROTCHET
            );
        }

        /** Three quarters with {@code interruption} between the first two. */
        private Song songInterruptedBy(ElementType interruption) {
            return songWithBeat(
                Duration.CROTCHET,
                ElementType.CROTCHET, interruption, ElementType.CROTCHET, ElementType.CROTCHET
            );
        }
    }

    // -----------------------------------------------------------------------
    // The strict/lenient split
    // -----------------------------------------------------------------------

    /**
     * Constraints 6 and 7 are creation rules: they say where this editor is willing to
     * write a tuplet, not what an existing document is allowed to contain, so each is
     * rejected under {@link Strictness#STRICT} and accepted under {@link Strictness#LENIENT}.
     *
     * <p>Constraint 5 does <em>not</em> split that way, and the first two tests pin why.
     * Reaching it at all means M is being derived, and a derived M that lands on no
     * conventional span — or on a power-of-two ratio — means the printed number states
     * nothing worth preserving. Accepting it leniently would not keep information, it
     * would invent a re-timing: three quavers under a dotted-crotchet beat would load as
     * 3:6 and play at half speed. A ratio the file itself states is kept by
     * {@code validateStated}, which never consults the conventional spans.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StrictVersusLenient {

        /** Constraint 5, redundancy: four eighths in the time of two is "read as sixteenths". */
        @Test
        void testPowerOfTwoRatioIsRejectedUnderBothStrictnesses() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER
            );

            assertAll(
                () -> assertRejects(
                    validateWholeLine(song, GRADE_QUADRUPLET, Strictness.STRICT),
                    Reason.POWER_OF_TWO_RATIO
                ),
                () -> assertRejects(
                    validateWholeLine(song, GRADE_QUADRUPLET, Strictness.LENIENT),
                    Reason.POWER_OF_TWO_RATIO
                )
            );
        }

        /**
         * Constraint 5, the conventional span: three eighths under a dotted-quarter beat
         * have no span below three. The beat already divides into exactly those three
         * eighths, so the printed 3 says nothing and the tuplet is dropped rather than
         * stretched onto the next span up.
         */
        @Test
        void testMissingConventionalSpanIsRejectedUnderBothStrictnesses() {
            var song = songWithBeat(
                Duration.CROTCHET_DOTTED,
                ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER
            );

            assertAll(
                () -> assertRejects(
                    validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                    Reason.NO_CONVENTIONAL_SPAN
                ),
                () -> assertRejects(
                    validateWholeLine(song, GRADE_TRIPLET, Strictness.LENIENT),
                    Reason.NO_CONVENTIONAL_SPAN
                )
            );
        }

        /**
         * The lenient counterpart to the two tests above: the same non-conventional ratio,
         * this time <em>stated</em> by the file rather than derived, survives — which is
         * what {@link Strictness#LENIENT} exists to protect.
         */
        @Test
        void testStatedNonConventionalRatioSurvives() {
            var song = songWithBeat(
                Duration.CROTCHET_DOTTED,
                ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER
            );
            var line = song.getLine(0);

            var result = validateStated(
                song, line, GRADE_TRIPLET - 1, GRADE_TRIPLET, NORMAL_SIX, ElementType.QUAVER);

            assertThat(result.valid())
                .as("a ratio the file states is trusted even where derivation would refuse")
                .isTrue();
            assertThat(result.normalNotes()).as("the stated M is kept verbatim").isEqualTo(NORMAL_SIX);
        }

        /** Constraint 6. */
        @Test
        void testBeatBarrierIsStrictOnly() {
            var song = threeQuartersAtSimpleBeat();
            attach(song, MIDDLE_INDEX, toCompoundBeat());

            assertAll(
                () -> assertRejects(
                    validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT), Reason.BEAT_BARRIER
                ),
                () -> assertDerives(
                    validateWholeLine(song, GRADE_TRIPLET, Strictness.LENIENT),
                    NORMAL_TWO, ElementType.CROTCHET
                )
            );
        }

        /** Constraint 7. */
        @Test
        void testStructuralBoundaryIsStrictOnly() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.CROTCHET, ElementType.SINGLE_BARLINE,
                ElementType.CROTCHET, ElementType.CROTCHET
            );

            assertAll(
                () -> assertRejects(
                    validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                    Reason.STRUCTURAL_BOUNDARY
                ),
                () -> assertDerives(
                    validateWholeLine(song, GRADE_TRIPLET, Strictness.LENIENT),
                    NORMAL_TWO, ElementType.CROTCHET
                )
            );
        }
    }

    // -----------------------------------------------------------------------
    // The bulk forward walk
    // -----------------------------------------------------------------------

    /**
     * The fixture is deliberately built so the beat changes between the first and the second
     * tuplet: the same three quarter notes derive a 3:2 triplet under the quarter beat that
     * opens the song, and have no conventional span at all once the beat is a dotted
     * quarter. A walk that failed to carry the beat forward would give both the same verdict.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateFrom {

        private Song song;
        private Tuplet firstTuplet;
        private Tuplet secondTuplet;
        private Tuplet thirdTuplet;

        /**
         * Line 0 holds six quarters carrying two triplets, with a metric modulation to a
         * dotted-quarter beat on the second triplet's anchor. Line 1 holds three sixteenths
         * carrying a third triplet, judged against the beat carried over from line 0.
         */
        @BeforeEach
        void buildSong() {
            song = songWithBeat(
                Duration.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET
            );

            var firstLine = song.getLine(FIRST_LINE_INDEX);
            var secondLine = new Line(song);

            song.withoutMutationTracking(() -> {
                firstLine.getElement(SECOND_TUPLET_ANCHOR_INDEX).addAttachment(toCompoundBeat());
                song.addLine(secondLine);

                for (var i = 0; i < GRADE_TRIPLET; i++) {
                    secondLine.addElement(semiquaver());
                }
            });

            firstTuplet = Tuplet.withUnresolvedRatio(
                firstLine.getElement(FIRST_INDEX),
                firstLine.getElement(FIRST_TUPLET_END_INDEX),
                GRADE_TRIPLET
            );
            secondTuplet = Tuplet.withUnresolvedRatio(
                firstLine.getElement(SECOND_TUPLET_ANCHOR_INDEX),
                firstLine.getElement(lastFixtureIndex(firstLine)),
                GRADE_TRIPLET
            );
            thirdTuplet = Tuplet.withUnresolvedRatio(
                secondLine.getElement(FIRST_INDEX),
                secondLine.getElement(lastFixtureIndex(secondLine)),
                GRADE_TRIPLET
            );

            song.withoutMutationTracking(() -> {
                firstLine.addTuplet(firstTuplet);
                firstLine.addTuplet(secondTuplet);
                secondLine.addTuplet(thirdTuplet);
            });
        }

        /** Every tuplet in the song, in document order, each judged against its own beat. */
        @Test
        void testWholeSongWalkJudgesEachTupletAgainstItsRunningBeat() {
            var verdicts = TupletValidator.validateFrom(
                song, FIRST_LINE_INDEX, FIRST_INDEX, Strictness.STRICT
            );

            assertThat(verdicts).as("one verdict per tuplet").hasSize(ALL_TUPLETS);

            var first = verdicts.get(FIRST_VERDICT);
            var second = verdicts.get(SECOND_VERDICT);
            var third = verdicts.get(THIRD_VERDICT);

            assertAll(
                () -> assertThat(first.tuplet())
                    .as("verdicts come in document order").isSameAs(firstTuplet),
                () -> assertThat(first.lineIndex()).as("first line index").isEqualTo(FIRST_LINE_INDEX),
                () -> assertDerives(first.result(), NORMAL_TWO, ElementType.CROTCHET),

                () -> assertThat(second.tuplet()).as("second verdict").isSameAs(secondTuplet),
                () -> assertRejects(second.result(), Reason.NO_CONVENTIONAL_SPAN),

                () -> assertThat(third.tuplet()).as("third verdict").isSameAs(thirdTuplet),
                () -> assertThat(third.lineIndex())
                    .as("the walk reports the line each tuplet lives on")
                    .isEqualTo(SECOND_LINE_INDEX),
                () -> assertDerives(third.result(), NORMAL_TWO, ElementType.SEMIQUAVER)
            );
        }

        /** A start position inside line 0 excludes the tuplet anchored before it. */
        @Test
        void testWalkExcludesTupletsAnchoredBeforeTheStartPosition() {
            var verdicts = TupletValidator.validateFrom(
                song, FIRST_LINE_INDEX, SECOND_TUPLET_ANCHOR_INDEX, Strictness.STRICT
            );

            assertThat(tupletsOf(verdicts))
                .as("only tuplets anchored at or after the start position")
                .hasSize(TUPLETS_AFTER_FIRST)
                .containsExactly(secondTuplet, thirdTuplet);
        }

        /** A start position on a later line excludes every earlier line. */
        @Test
        void testWalkStartingOnALaterLineExcludesEarlierLines() {
            var verdicts = TupletValidator.validateFrom(
                song, SECOND_LINE_INDEX, FIRST_INDEX, Strictness.STRICT
            );

            assertThat(tupletsOf(verdicts))
                .as("only the tuplet on the line walked")
                .hasSize(TUPLETS_ON_SECOND_LINE)
                .containsExactly(thirdTuplet);
        }

        private static List<Tuplet> tupletsOf(List<TupletValidator.Verdict> verdicts) {
            return verdicts.stream().map(TupletValidator.Verdict::tuplet).toList();
        }
    }

    // -----------------------------------------------------------------------
    // Shared assertions
    // -----------------------------------------------------------------------

    private static void assertDerives(Result result, int normalNotes, ElementType noteValue) {
        assertAll(
            () -> assertThat(result.valid())
                .as("span must be valid, but was rejected with %s", result.reason())
                .isTrue(),
            () -> assertThat(result.normalNotes()).as("derived M").isEqualTo(normalNotes),
            () -> assertThat(result.noteValue()).as("derived V").isEqualTo(noteValue)
        );
    }

    private static void assertRejects(Result result, Reason reason) {
        assertAll(
            () -> assertThat(result.valid()).as("span must be rejected").isFalse(),
            () -> assertThat(result.reason()).as("rejection reason").isEqualTo(reason)
        );
    }
}
