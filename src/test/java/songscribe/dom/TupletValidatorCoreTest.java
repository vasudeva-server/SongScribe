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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import songscribe.UnitTest;
import songscribe.dom.TupletValidator.Reason;
import songscribe.dom.TupletValidator.Strictness;

/**
 * Unit tests for the derivation half of {@link TupletValidator}: the written duration of
 * a span, the derived note value V, the conventional spans under a beat, the choice of M
 * and the redundancy test.
 *
 * <p>Every fixture is a single line whose beat comes from the song-level tempo, so no
 * element carries a beat-defining attachment and {@link Song#resolveBeatAt} falls through
 * to the tempo's note value.
 */
class TupletValidatorCoreTest extends UnitTest {

    // Printed tuplet numbers (N)
    private static final int GRADE_DUPLET = 2;
    private static final int GRADE_TRIPLET = 3;
    private static final int GRADE_QUADRUPLET = 4;
    private static final int GRADE_QUINTUPLET = 5;
    private static final int GRADE_SEXTUPLET = 6;
    private static final int GRADE_SEPTUPLET = 7;

    /** Above the largest printable number; only a file can present it. */
    private static final int GRADE_OVER_MAXIMUM = TupletValidator.MAX_GRADE + 1;

    // Derived normal-note counts (M)
    private static final int NORMAL_TWO = 2;
    private static final int NORMAL_THREE = 3;
    private static final int NORMAL_FOUR = 4;
    private static final int NORMAL_SIX = 6;

    /** Normal-note count carried by an M that the derivation must reject outright. */
    private static final int NO_NORMAL_NOTES = 0;

    /** An undotted written value. */
    private static final int NO_DOTS = 0;

    /** A written value carrying a single dot. */
    private static final int ONE_DOT = 1;

    /** The only line these fixtures build. */
    private static final int LINE_INDEX = 0;

    private static final int FIRST_INDEX = 0;

    /**
     * A row of the expected-results tables: the grade under test and either the M it must
     * derive (with {@code reason} null) or the reason it must be rejected (with
     * {@code normalNotes} zero).
     */
    private record Expected(int grade, int normalNotes, @Nullable Reason reason) {

        static Expected derives(int grade, int normalNotes) {
            return new Expected(grade, normalNotes, null);
        }

        static Expected rejects(int grade, Reason reason) {
            return new Expected(grade, NO_NORMAL_NOTES, reason);
        }
    }

    /** One column of an expected-results table: a beat, a written value, and six rows. */
    private record Column(Duration beat, ElementType noteValue, List<Expected> rows) {}

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a one-line song whose beat is {@code beat} and whose line holds {@code types}
     * in order. The beat is carried by the song tempo rather than by an attachment so the
     * span itself contains no beat-defining element.
     */
    private static Song songWithBeat(Duration beat, ElementType... types) {
        var song = new Song();
        var line = song.getLine(LINE_INDEX);

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

    /** Builds a song of {@code count} notes of {@code type} and validates the whole line. */
    private static TupletValidator.Result validateRepeated(
        Duration beat, ElementType type, int count, int grade, Strictness strictness
    ) {
        var types = new ElementType[count];

        Arrays.fill(types, type);

        return validateWholeLine(songWithBeat(beat, types), grade, strictness);
    }

    /** Validates the entire line of {@code song} as a tuplet of {@code grade}. */
    private static TupletValidator.Result validateWholeLine(
        Song song, int grade, Strictness strictness
    ) {
        var line = song.getLine(LINE_INDEX);

        return TupletValidator.validateDerived(
            song, line, LINE_INDEX, FIRST_INDEX, line.elementCount() - 1, grade, strictness
        );
    }

    // -----------------------------------------------------------------------
    // V = S / N and its notatability
    // -----------------------------------------------------------------------

    /**
     * Two sixteenths plus an eighth under a 3 gives V = 32 ticks, which is not a written
     * note value at any dot count, so the span cannot carry a triplet.
     */
    @Test
    void testNonNotatableNoteValueIsRejected() {
        var song = songWithBeat(
            Duration.CROTCHET,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER, ElementType.QUAVER
        );

        var result = validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT);

        assertAll(
            () -> assertThat(result.valid())
                .as("a span whose S/N is not a written value must be rejected")
                .isFalse(),
            () -> assertThat(result.reason())
                .as("rejection reason")
                .isEqualTo(Reason.NOT_NOTATABLE)
        );
    }

    /** A span of nothing but grace notes has no written duration at all. */
    @Test
    void testGraceOnlySpanIsEmpty() {
        var song = songWithBeat(
            Duration.CROTCHET,
            ElementType.GRACE_QUAVER, ElementType.GRACE_QUAVER, ElementType.GRACE_QUAVER
        );

        var result = validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT);

        assertAll(
            () -> assertThat(result.valid())
                .as("a span with no written duration must be rejected")
                .isFalse(),
            () -> assertThat(result.reason())
                .as("rejection reason")
                .isEqualTo(Reason.EMPTY_SPAN)
        );
    }

    /**
     * A number above the largest printable one cannot be created here and cannot be
     * expressed exactly in MusicXML, so a file carrying it is rejected either way.
     */
    @Test
    void testGradeAboveMaximumIsRejectedUnderBothStrictnesses() {
        assertAll(
            () -> assertThat(
                validateRepeated(
                    Duration.CROTCHET, ElementType.QUAVER, GRADE_OVER_MAXIMUM,
                    GRADE_OVER_MAXIMUM, Strictness.STRICT
                ).reason()
            ).as("strict rejection reason").isEqualTo(Reason.BAD_RATIO),
            () -> assertThat(
                validateRepeated(
                    Duration.CROTCHET, ElementType.QUAVER, GRADE_OVER_MAXIMUM,
                    GRADE_OVER_MAXIMUM, Strictness.LENIENT
                ).reason()
            ).as("lenient rejection reason").isEqualTo(Reason.BAD_RATIO)
        );
    }

    // -----------------------------------------------------------------------
    // Mixed written values — the cases a gcd-based derivation would renumber
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MixedWrittenValues {

        /** Quarter, quarter, eighth, eighth under a 3 is a quarter-note triplet, not a 6. */
        @Test
        void testTwoQuartersAndTwoEighthsDeriveQuarterValue() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.QUAVER, ElementType.QUAVER
            );

            assertDerives(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                NORMAL_TWO, ElementType.CROTCHET
            );
        }

        /** An eighth plus four sixteenths under a 3 is an eighth-note triplet, not a 6. */
        @Test
        void testEighthAndFourSixteenthsDeriveEighthValue() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.QUAVER,
                ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
                ElementType.SEMIQUAVER, ElementType.SEMIQUAVER
            );

            assertDerives(
                validateWholeLine(song, GRADE_TRIPLET, Strictness.STRICT),
                NORMAL_TWO, ElementType.QUAVER
            );
        }
    }

    // -----------------------------------------------------------------------
    // Choosing M
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ChoosingNormalNotes {

        /** At a quarter beat the spans start at two, so a duplet has nothing below it. */
        @Test
        void testDupletAtSimpleBeatHasNoConventionalSpan() {
            assertRejects(
                validateRepeated(
                    Duration.CROTCHET, ElementType.CROTCHET, GRADE_DUPLET,
                    GRADE_DUPLET, Strictness.STRICT
                ),
                Reason.NO_CONVENTIONAL_SPAN
            );
        }

        /**
         * At a dotted-quarter beat, eighths span three, six, twelve — nothing below three,
         * and the fallback to a longer span is reserved for duplets.
         */
        @Test
        void testTripletOfEighthsAtCompoundBeatHasNoConventionalSpan() {
            assertRejects(
                validateRepeated(
                    Duration.CROTCHET_DOTTED, ElementType.QUAVER, GRADE_TRIPLET,
                    GRADE_TRIPLET, Strictness.STRICT
                ),
                Reason.NO_CONVENTIONAL_SPAN
            );
        }

        /** Four eighths in the time of two is just "read these as sixteenths". */
        @Test
        void testQuadrupletOfEighthsAtSimpleBeatIsRedundant() {
            assertRejects(
                validateRepeated(
                    Duration.CROTCHET, ElementType.QUAVER, GRADE_QUADRUPLET,
                    GRADE_QUADRUPLET, Strictness.STRICT
                ),
                Reason.POWER_OF_TWO_RATIO
            );
        }

        /** Six eighths in the time of three at a compound beat is likewise a renotation. */
        @Test
        void testSextupletOfEighthsAtCompoundBeatIsRedundant() {
            assertRejects(
                validateRepeated(
                    Duration.CROTCHET_DOTTED, ElementType.QUAVER, GRADE_SEXTUPLET,
                    GRADE_SEXTUPLET, Strictness.STRICT
                ),
                Reason.POWER_OF_TWO_RATIO
            );
        }

        /** The compound duplet is the one grade allowed to reach for a longer span. */
        @Test
        void testDupletOfEighthsAtCompoundBeatDerivesThree() {
            assertDerives(
                validateRepeated(
                    Duration.CROTCHET_DOTTED, ElementType.QUAVER, GRADE_DUPLET,
                    GRADE_DUPLET, Strictness.STRICT
                ),
                NORMAL_THREE, ElementType.QUAVER
            );
        }

        /** Three sixteenths occupy the half beat two sixteenths would. */
        @Test
        void testTripletOfSixteenthsAtSimpleBeatDerivesTwo() {
            assertDerives(
                validateRepeated(
                    Duration.CROTCHET, ElementType.SEMIQUAVER, GRADE_TRIPLET,
                    GRADE_TRIPLET, Strictness.STRICT
                ),
                NORMAL_TWO, ElementType.SEMIQUAVER
            );
        }
    }

    // -----------------------------------------------------------------------
    // The full expected-results tables
    // -----------------------------------------------------------------------

    /**
     * Reproduces both expected-results tables: a quarter beat, where all three written
     * values agree because the spans are the same powers of two in V-units, and a
     * dotted-quarter beat, where a value finer than the beat's third behaves differently
     * from one at or above it.
     */
    @Test
    void testExpectedResultsTables() {
        var simpleBeatRows = List.of(
            Expected.rejects(GRADE_DUPLET, Reason.NO_CONVENTIONAL_SPAN),
            Expected.derives(GRADE_TRIPLET, NORMAL_TWO),
            Expected.rejects(GRADE_QUADRUPLET, Reason.POWER_OF_TWO_RATIO),
            Expected.derives(GRADE_QUINTUPLET, NORMAL_FOUR),
            Expected.derives(GRADE_SEXTUPLET, NORMAL_FOUR),
            Expected.derives(GRADE_SEPTUPLET, NORMAL_FOUR)
        );

        var compoundBeatCoarseRows = List.of(
            Expected.derives(GRADE_DUPLET, NORMAL_THREE),
            Expected.rejects(GRADE_TRIPLET, Reason.NO_CONVENTIONAL_SPAN),
            Expected.derives(GRADE_QUADRUPLET, NORMAL_THREE),
            Expected.derives(GRADE_QUINTUPLET, NORMAL_THREE),
            Expected.rejects(GRADE_SEXTUPLET, Reason.POWER_OF_TWO_RATIO),
            Expected.derives(GRADE_SEPTUPLET, NORMAL_SIX)
        );

        var compoundBeatFineRows = List.of(
            Expected.derives(GRADE_DUPLET, NORMAL_SIX),
            Expected.derives(GRADE_TRIPLET, NORMAL_TWO),
            Expected.rejects(GRADE_QUADRUPLET, Reason.POWER_OF_TWO_RATIO),
            Expected.derives(GRADE_QUINTUPLET, NORMAL_TWO),
            Expected.derives(GRADE_SEXTUPLET, NORMAL_TWO),
            Expected.derives(GRADE_SEPTUPLET, NORMAL_SIX)
        );

        var columns = List.of(
            new Column(Duration.CROTCHET, ElementType.CROTCHET, simpleBeatRows),
            new Column(Duration.CROTCHET, ElementType.QUAVER, simpleBeatRows),
            new Column(Duration.CROTCHET, ElementType.SEMIQUAVER, simpleBeatRows),
            new Column(Duration.CROTCHET_DOTTED, ElementType.CROTCHET, compoundBeatCoarseRows),
            new Column(Duration.CROTCHET_DOTTED, ElementType.QUAVER, compoundBeatCoarseRows),
            new Column(Duration.CROTCHET_DOTTED, ElementType.SEMIQUAVER, compoundBeatFineRows)
        );

        var assertions = new ArrayList<Executable>();

        for (var column : columns) {
            for (var row : column.rows()) {
                assertions.add(() -> assertRow(column, row));
            }
        }

        assertAll(assertions);
    }

    /** Validates one table cell: N notes of the column's value under the column's beat. */
    private static void assertRow(Column column, Expected row) {
        var result = validateRepeated(
            column.beat(), column.noteValue(), row.grade(), row.grade(), Strictness.STRICT
        );

        var label = "beat %s, value %s, N = %d".formatted(
            column.beat(), column.noteValue(), row.grade()
        );

        if (row.reason() == null) {
            assertThat(result.valid()).as("%s must be valid", label).isTrue();
            assertThat(result.normalNotes()).as("%s: M", label).isEqualTo(row.normalNotes());
            assertThat(result.noteValue()).as("%s: V", label).isEqualTo(column.noteValue());
        } else {
            assertThat(result.valid()).as("%s must be invalid", label).isFalse();
            assertThat(result.reason()).as("%s: reason", label).isEqualTo(row.reason());
        }
    }

    // -----------------------------------------------------------------------
    // A ratio stated by a file
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StatedRatio {

        /**
         * Three eighths under a dotted-quarter beat has no conventional span, so the
         * derived path drops it — but a file that says 3:2 is asking for a real re-timing
         * and must be kept.
         */
        @Test
        void testNonConventionalStatedRatioIsAccepted() {
            var song = songWithBeat(
                Duration.CROTCHET_DOTTED,
                ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER
            );
            var line = song.getLine(LINE_INDEX);

            var result = validateStatedOverWholeLine(
                song, line, GRADE_TRIPLET, NORMAL_TWO, ElementType.QUAVER);

            assertAll(
                () -> assertThat(result.valid())
                    .as("a stated non-conventional ratio must be kept")
                    .isTrue(),
                () -> assertThat(result.normalNotes()).as("stated M is echoed").isEqualTo(NORMAL_TWO),
                () -> assertThat(result.noteValue()).as("stated V is echoed").isEqualTo(ElementType.QUAVER)
            );
        }

        /** A stated V that disagrees with S / N describes a different span than the file has. */
        @Test
        void testStatedNoteValueDisagreeingWithSpanIsRejected() {
            var song = songWithBeat(
                Duration.CROTCHET_DOTTED,
                ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER
            );
            var line = song.getLine(LINE_INDEX);

            var result = validateStatedOverWholeLine(
                song, line, GRADE_TRIPLET, NORMAL_TWO, ElementType.SEMIQUAVER);

            assertRejects(result, Reason.NOT_NOTATABLE);
        }

        /** N and M naming the same count is not a ratio. */
        @Test
        void testStatedRatioWithEqualCountsIsRejected() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER
            );
            var line = song.getLine(LINE_INDEX);

            var result = validateStatedOverWholeLine(
                song, line, GRADE_TRIPLET, NORMAL_THREE, ElementType.QUAVER);

            assertRejects(result, Reason.BAD_RATIO);
        }
    }

    /**
     * The derivation must be able to land on a <em>dotted</em> written value, not only an
     * undotted one. Three dotted eighths under a dotted-quarter beat total three dotted
     * eighths, so a 3 gives a written value of one dotted eighth and a conventional span of
     * two of them — a real 3:2 whose unit carries a dot.
     *
     * <p>This is the migration case for the three tuplets in the reference corpus whose
     * written value is dotted. If the dotted entries were missing from the validator's
     * table of notatable values, the derivation would call this span unwritable and the
     * load pass would silently drop the bracket.
     */
    @Test
    void testDerivationLandsOnADottedWrittenValue() {
        var song = dottedQuaverLine(GRADE_TRIPLET);
        var line = song.getLine(LINE_INDEX);

        var result = TupletValidator.validateDerived(
            song, line, LINE_INDEX, FIRST_INDEX, GRADE_TRIPLET - 1,
            GRADE_TRIPLET, Strictness.STRICT);

        assertAll(
            () -> assertThat(result.valid())
                .as("span must be valid, but was rejected with %s", result.reason())
                .isTrue(),
            () -> assertThat(result.normalNotes()).as("derived M").isEqualTo(NORMAL_TWO),
            () -> assertThat(result.noteValue()).as("derived V's base type")
                .isEqualTo(ElementType.QUAVER),
            () -> assertThat(result.noteValueDots()).as("derived V's dot count").isEqualTo(ONE_DOT)
        );
    }

    /** A line of {@code count} dotted eighths under a dotted-quarter beat. */
    private static Song dottedQuaverLine(int count) {
        var song = new Song();
        var line = song.getLine(LINE_INDEX);

        song.withoutMutationTracking(() -> {
            song.setTempo(new Tempo(
                Tempo.DEFAULT_BPM, Duration.CROTCHET_DOTTED,
                Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO));

            for (var i = 0; i < count; i++) {
                var note = ElementType.QUAVER.newInstance();
                note.setDotCount(ONE_DOT);
                line.addElement(note);
            }
        });

        return song;
    }

    // -----------------------------------------------------------------------
    // Shared assertions
    // -----------------------------------------------------------------------

    /**
     * Judges the line's whole span against a ratio the caller states, as a file would.
     * Leniently, because that is the load path: the beat barrier and the structural
     * boundary constrain creation, not a document that already exists.
     */
    private static TupletValidator.Result validateStatedOverWholeLine(
        Song song, Line line, int grade, int normalNotes, ElementType noteValue
    ) {
        var context = TupletValidator.describeSpan(
            song, line, LINE_INDEX, FIRST_INDEX, line.elementCount() - 1);

        return TupletValidator.validateStated(
            context, grade, normalNotes, noteValue, NO_DOTS, TupletValidator.Strictness.LENIENT);
    }

    private static void assertDerives(
        TupletValidator.Result result, int normalNotes, ElementType noteValue
    ) {
        assertAll(
            () -> assertThat(result.valid())
                .as("span must be valid, but was rejected with %s", result.reason())
                .isTrue(),
            () -> assertThat(result.normalNotes()).as("derived M").isEqualTo(normalNotes),
            () -> assertThat(result.noteValue()).as("derived V").isEqualTo(noteValue),
            () -> assertThat(result.noteValueDots()).as("derived dots on V").isEqualTo(NO_DOTS)
        );
    }

    private static void assertRejects(TupletValidator.Result result, Reason reason) {
        assertAll(
            () -> assertThat(result.valid()).as("span must be rejected").isFalse(),
            () -> assertThat(result.reason()).as("rejection reason").isEqualTo(reason),
            () -> assertThat(result.normalNotes())
                .as("a rejected span carries no M")
                .isEqualTo(NO_NORMAL_NOTES),
            () -> assertThat(result.noteValue()).as("a rejected span carries no V").isNull()
        );
    }
}
