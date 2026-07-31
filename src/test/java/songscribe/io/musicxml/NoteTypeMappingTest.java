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

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;

/**
 * Verifies {@link NoteTypeMapping#ticks(ElementType, int)} over all six
 * note-value types × three dot counts (0, 1, 2), asserting exact integer
 * results and correct tick values, and checks that {@link ElementType#GRACE_QUAVER}
 * (which carries no {@code <duration>}) throws as expected.
 */
class NoteTypeMappingTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Constants derived from DIVISIONS (13440 quarter-note ticks).
    //
    // Whole-note base:     QUARTERS_PER_WHOLE × DIVISIONS
    // Half-note base:      2 × DIVISIONS           (2 is a rule exception)
    // Quarter-note base:   DIVISIONS
    // Eighth-note base:    DIVISIONS / 2            (2 is a rule exception)
    // Sixteenth-note base: DIVISIONS / SIXTEENTHS_PER_QUARTER
    // 32nd-note base:      DIVISIONS / THIRTY_SECONDS_PER_QUARTER
    //
    // Dot augmentation: base × (2^(d+1) − 1) / 2^d
    //   1 dot:  × ONE_DOT_NUMERATOR / 2            (denominator 2 is exception)
    //   2 dots: × TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR
    // -------------------------------------------------------------------------

    // Multiplier for whole note: a whole note equals QUARTERS_PER_WHOLE quarter notes.
    private static final int QUARTERS_PER_WHOLE = 4;

    // Subdivisions per quarter note (denominators for shorter note values).
    private static final int SIXTEENTHS_PER_QUARTER     = 4;
    private static final int THIRTY_SECONDS_PER_QUARTER = 8;

    // Dot augmentation fraction numerators (denominators are 2 and 4;
    // 4 is the TWO_DOT_DENOMINATOR; 2 is a rule exception used inline).
    private static final int ONE_DOT_NUMERATOR   = 3;
    private static final int TWO_DOT_NUMERATOR   = 7;
    private static final int TWO_DOT_DENOMINATOR = 4;

    // -------------------------------------------------------------------------
    // Base (undotted) tick counts
    // -------------------------------------------------------------------------

    private static final int BASE_WHOLE         = QUARTERS_PER_WHOLE * NoteTypeMapping.DIVISIONS;        // 53760
    private static final int BASE_HALF          = 2 * NoteTypeMapping.DIVISIONS;                          // 26880
    private static final int BASE_QUARTER       = NoteTypeMapping.DIVISIONS;                              // 13440
    private static final int BASE_EIGHTH        = NoteTypeMapping.DIVISIONS / 2;                          //  6720
    private static final int BASE_SIXTEENTH     = NoteTypeMapping.DIVISIONS / SIXTEENTHS_PER_QUARTER;    //  3360
    private static final int BASE_THIRTY_SECOND = NoteTypeMapping.DIVISIONS / THIRTY_SECONDS_PER_QUARTER;//  1680

    // -------------------------------------------------------------------------
    // Expected dotted tick counts (computed from base values)
    // -------------------------------------------------------------------------

    // Absolute tick invariants pinned to raw literals, NOT derived from DIVISIONS:
    // the derived constants above move with DIVISIONS, so they cannot catch a
    // regression in DIVISIONS itself. These can. 2940 is the entire justification
    // for DIVISIONS = 13440 (the smallest fraction stays an exact integer even
    // after a tuplet scales it by M/N for every N in {2..7}).
    private static final int WHOLE_NOTE_TICKS_LITERAL            = 53760;
    private static final int DOUBLE_DOTTED_THIRTY_SECOND_LITERAL = 2940;

    private static final int WHOLE_1DOT         = BASE_WHOLE         * ONE_DOT_NUMERATOR / 2;            // 80640
    private static final int WHOLE_2DOT         = BASE_WHOLE         * TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR; // 94080
    private static final int HALF_1DOT          = BASE_HALF          * ONE_DOT_NUMERATOR / 2;            // 40320
    private static final int HALF_2DOT          = BASE_HALF          * TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR; // 47040
    private static final int QUARTER_1DOT       = BASE_QUARTER       * ONE_DOT_NUMERATOR / 2;            // 20160
    private static final int QUARTER_2DOT       = BASE_QUARTER       * TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR; // 23520
    private static final int EIGHTH_1DOT        = BASE_EIGHTH        * ONE_DOT_NUMERATOR / 2;            // 10080
    private static final int EIGHTH_2DOT        = BASE_EIGHTH        * TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR; // 11760
    private static final int SIXTEENTH_1DOT     = BASE_SIXTEENTH     * ONE_DOT_NUMERATOR / 2;            //  5040
    private static final int SIXTEENTH_2DOT     = BASE_SIXTEENTH     * TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR; //  5880
    private static final int THIRTY_SECOND_1DOT = BASE_THIRTY_SECOND * ONE_DOT_NUMERATOR / 2;            //  2520
    private static final int THIRTY_SECOND_2DOT = BASE_THIRTY_SECOND * TWO_DOT_NUMERATOR / TWO_DOT_DENOMINATOR; //  2940

    // -------------------------------------------------------------------------
    // Tests: all 6 note types × 3 dot counts, undotted and rests
    // -------------------------------------------------------------------------

    @Test
    void testTicksForWhole() {
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIBREVE, 0)).isEqualTo(BASE_WHOLE);
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIBREVE, 1)).isEqualTo(WHOLE_1DOT);
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIBREVE, 2)).isEqualTo(WHOLE_2DOT);
    }

    @Test
    void testTicksForHalf() {
        assertThat(NoteTypeMapping.ticks(ElementType.MINIM, 0)).isEqualTo(BASE_HALF);
        assertThat(NoteTypeMapping.ticks(ElementType.MINIM, 1)).isEqualTo(HALF_1DOT);
        assertThat(NoteTypeMapping.ticks(ElementType.MINIM, 2)).isEqualTo(HALF_2DOT);
    }

    @Test
    void testTicksForQuarter() {
        assertThat(NoteTypeMapping.ticks(ElementType.CROTCHET, 0)).isEqualTo(BASE_QUARTER);
        assertThat(NoteTypeMapping.ticks(ElementType.CROTCHET, 1)).isEqualTo(QUARTER_1DOT);
        assertThat(NoteTypeMapping.ticks(ElementType.CROTCHET, 2)).isEqualTo(QUARTER_2DOT);
    }

    @Test
    void testTicksForEighth() {
        assertThat(NoteTypeMapping.ticks(ElementType.QUAVER, 0)).isEqualTo(BASE_EIGHTH);
        assertThat(NoteTypeMapping.ticks(ElementType.QUAVER, 1)).isEqualTo(EIGHTH_1DOT);
        assertThat(NoteTypeMapping.ticks(ElementType.QUAVER, 2)).isEqualTo(EIGHTH_2DOT);
    }

    @Test
    void testTicksForSixteenth() {
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIQUAVER, 0)).isEqualTo(BASE_SIXTEENTH);
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIQUAVER, 1)).isEqualTo(SIXTEENTH_1DOT);
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIQUAVER, 2)).isEqualTo(SIXTEENTH_2DOT);
    }

    @Test
    void testTicksForThirtySecond() {
        assertThat(NoteTypeMapping.ticks(ElementType.DEMI_SEMIQUAVER, 0)).isEqualTo(BASE_THIRTY_SECOND);
        assertThat(NoteTypeMapping.ticks(ElementType.DEMI_SEMIQUAVER, 1)).isEqualTo(THIRTY_SECOND_1DOT);
        assertThat(NoteTypeMapping.ticks(ElementType.DEMI_SEMIQUAVER, 2)).isEqualTo(THIRTY_SECOND_2DOT);
    }

    @Test
    void testTicksForRestsMatchNoteCounterparts() {
        // Each *_REST maps to the same base-tick value as its pitched counterpart;
        // the <rest/> child distinguishes a rest in MusicXML, not a separate token.
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIBREVE_REST,       0)).isEqualTo(BASE_WHOLE);
        assertThat(NoteTypeMapping.ticks(ElementType.MINIM_REST,           0)).isEqualTo(BASE_HALF);
        assertThat(NoteTypeMapping.ticks(ElementType.CROTCHET_REST,        0)).isEqualTo(BASE_QUARTER);
        assertThat(NoteTypeMapping.ticks(ElementType.QUAVER_REST,          0)).isEqualTo(BASE_EIGHTH);
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIQUAVER_REST,      0)).isEqualTo(BASE_SIXTEENTH);
        assertThat(NoteTypeMapping.ticks(ElementType.DEMI_SEMIQUAVER_REST, 0)).isEqualTo(BASE_THIRTY_SECOND);
    }

    @Test
    void testTicksThrowsForGraceQuaverBecauseGraceHasNoDuration() {
        // GRACE_QUAVER carries no <duration> in MusicXML output; calling ticks()
        // on it is a caller error and must throw.
        assertThatIllegalArgumentException()
            .isThrownBy(() -> NoteTypeMapping.ticks(ElementType.GRACE_QUAVER, 0))
            .withMessageContaining("GRACE_QUAVER");
    }

    @Test
    void testTicksThrowsForDotCountBelowZero() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> NoteTypeMapping.ticks(ElementType.CROTCHET, -1))
            .withMessageContaining("dotCount");
    }

    @Test
    void testTicksThrowsForDotCountAboveMax() {
        final int overMax = 3;
        assertThatIllegalArgumentException()
            .isThrownBy(() -> NoteTypeMapping.ticks(ElementType.CROTCHET, overMax))
            .withMessageContaining("dotCount");
    }

    @Test
    void testAbsoluteTickInvariantsArePinnedToLiterals() {
        assertThat(NoteTypeMapping.ticks(ElementType.SEMIBREVE, 0))
            .as("a whole note must be exactly %d ticks at DIVISIONS=%d", WHOLE_NOTE_TICKS_LITERAL, NoteTypeMapping.DIVISIONS)
            .isEqualTo(WHOLE_NOTE_TICKS_LITERAL);
        // The double-dotted 32nd is the smallest representable fraction.
        assertThat(NoteTypeMapping.ticks(ElementType.DEMI_SEMIQUAVER, 2))
            .as("a double-dotted 32nd must be exactly %d ticks (exact integer)", DOUBLE_DOTTED_THIRTY_SECOND_LITERAL)
            .isEqualTo(DOUBLE_DOTTED_THIRTY_SECOND_LITERAL);
    }

    // -------------------------------------------------------------------------
    // forTypeToken / typeToken / hasDuration — direct branch coverage
    // -------------------------------------------------------------------------

    @Test
    void testForTypeTokenMapsPitchedNote() {
        assertThat(NoteTypeMapping.forTypeToken(NoteTypeMapping.TYPE_QUARTER, false, false))
            .isEqualTo(ElementType.CROTCHET);
    }

    @Test
    void testForTypeTokenMapsRestWhenRestFlagSet() {
        assertThat(NoteTypeMapping.forTypeToken(NoteTypeMapping.TYPE_QUARTER, true, false))
            .isEqualTo(ElementType.CROTCHET_REST);
    }

    @Test
    void testForTypeTokenReturnsGraceQuaverRegardlessOfToken() {
        // The grace flag overrides the token entirely.
        assertThat(NoteTypeMapping.forTypeToken(NoteTypeMapping.TYPE_WHOLE, false, true))
            .as("grace flag forces GRACE_QUAVER irrespective of the <type> token")
            .isEqualTo(ElementType.GRACE_QUAVER);
    }

    @Test
    void testForTypeTokenReturnsNullForUnknownToken() {
        assertThat(NoteTypeMapping.forTypeToken("bogus", false, false))
            .as("an unrecognised <type> token must map to null")
            .isNull();
    }

    @Test
    void testTypeTokenMapsKnownTypeAndNullForOthers() {
        assertThat(NoteTypeMapping.typeToken(ElementType.CROTCHET))
            .isEqualTo(NoteTypeMapping.TYPE_QUARTER);
        assertThat(NoteTypeMapping.typeToken(ElementType.SINGLE_BARLINE))
            .as("a non-note type has no <type> token")
            .isNull();
    }

    @Test
    void testHasDurationTrueForNotesAndFalseForGrace() {
        assertThat(NoteTypeMapping.hasDuration(ElementType.CROTCHET))
            .as("notes emit <duration>")
            .isTrue();
        assertThat(NoteTypeMapping.hasDuration(ElementType.GRACE_QUAVER))
            .as("grace notes emit no <duration>")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // DIVISIONS must survive every tuplet ratio, not just the plain note values.
    //
    // A tuplet scales a written duration by M/N (M normal-notes, N actual-notes).
    // Every ElementType with a tick mapping, at every supported dot count, must
    // stay an exact integer once scaled by M/N for every ratio the validator can
    // accept. M is not known to this class, so this asserts the stronger,
    // sufficient property: for every M in {MIN_TUPLET_RATIO..MAX_TUPLET_RATIO}
    // with M != N, ticks(type, dots) * M is divisible by N.
    // -------------------------------------------------------------------------

    // The validator's ratio bounds (Shared Reference, constraint 1): N and M
    // are positive integers, N != M, N <= 7. 1 is a rule exception (MIN_TUPLET_RATIO).
    private static final int MIN_TUPLET_RATIO = 1;
    private static final int MAX_TUPLET_RATIO = 7;

    private static final ElementType[] NOTE_TYPES_WITH_TICK_MAPPING = {
        ElementType.SEMIBREVE,
        ElementType.MINIM,
        ElementType.CROTCHET,
        ElementType.QUAVER,
        ElementType.SEMIQUAVER,
        ElementType.DEMI_SEMIQUAVER,
    };

    @Test
    void testTicksScaleExactlyForEveryTupletRatioAndDotCount() {
        for (var type : NOTE_TYPES_WITH_TICK_MAPPING) {
            for (var dotCount = 0; dotCount <= NoteTypeMapping.MAX_DOT_COUNT; dotCount++) {
                var ticks = NoteTypeMapping.ticks(type, dotCount);

                for (var actualNotes = MIN_TUPLET_RATIO; actualNotes <= MAX_TUPLET_RATIO; actualNotes++) {
                    for (var normalNotes = MIN_TUPLET_RATIO; normalNotes <= MAX_TUPLET_RATIO; normalNotes++) {
                        if (normalNotes == actualNotes) {
                            continue;
                        }

                        assertThat(ticks * normalNotes % actualNotes)
                            .as(
                                "ticks(%s, dots=%d)=%d scaled by M=%d/N=%d must be an exact integer",
                                type, dotCount, ticks, normalNotes, actualNotes
                            )
                            .isZero();
                    }
                }
            }
        }
    }
}
