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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.midi.MidiSequenceBuilder;

class StaffElementTest extends UnitTest {

    // ------------------------------------------------------------------
    // Constants extracted from production to allow absolute assertions
    // ------------------------------------------------------------------

    // MIDI_PITCHES[0..6] = B4..A5 as documented in StaffElement
    private static final int MIDI_B4 = 71;
    private static final int MIDI_C5 = 72;
    private static final int MIDI_D5 = 74;
    private static final int MIDI_E5 = 76;
    private static final int MIDI_F5 = 77;
    private static final int MIDI_G5 = 79;
    private static final int MIDI_A5 = 81;

    // Semitones per octave — used when verifying the octave-shift formula
    private static final int SEMITONES_PER_OCTAVE = 12;

    // Staff positions span 7 diatonic steps per octave
    private static final int STAFF_STEPS_PER_OCTAVE = 7;

    // DOTTED_DURATION multipliers (from the production constant, expressed as ratios for clarity)
    private static final float DURATION_PLAIN = 1.0f;
    private static final float DURATION_SINGLE_DOT = 1.5f;     // adds half the base value
    private static final float DURATION_DOUBLE_DOT = 1.75f;    // adds 3/4 of the base value

    // Fermata extends duration by this factor
    private static final float FERMATA_EXTENSION = 1.5f;

    // Quarter note (crotchet) default duration in MIDI ticks
    private static final int CROTCHET_TICKS = MidiSequenceBuilder.PPQ;

    // Staccato MIDI duration percentage, matching ArticulationType.STACCATO(33)
    private static final int STACCATO_MIDI_PERCENT = 33;

    // No-override sentinel from findMidiDurationOverride
    private static final int NO_MIDI_OVERRIDE = -1;

    // ------------------------------------------------------------------
    // T10: getPitch / calculatePitch — absolute MIDI-value assertions
    // ------------------------------------------------------------------

    // T10a: staffPosition 0 (B4) with NATURAL accidental → MIDI 71
    // NATURAL is set explicitly because getPitch() falls back to findLastAccidental()
    // (which requires a parent Line) when accidental is null; setting NATURAL avoids
    // that path while still asserting the unmodified base pitch.
    @Test
    void testGetPitchStaffPosition0NaturalIsB4() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setStaffPosition(0);
        element.setAccidental(StaffElement.Accidental.NATURAL);

        assertThat(element.getPitch()).isEqualTo(MIDI_B4);
    }

    // T10b: staffPosition -1 (C5) with NATURAL accidental → MIDI 72
    @Test
    void testGetPitchStaffPositionMinus1NaturalIsC5() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setStaffPosition(-1);
        element.setAccidental(StaffElement.Accidental.NATURAL);

        assertThat(element.getPitch()).isEqualTo(MIDI_C5);
    }

    // T10c: staffPosition -4 (F5) with SHARP → MIDI 78 (F#5)
    @Test
    void testGetPitchStaffPositionMinus4SharpIsFSharp5() {
        // staffPosition -4 → pitchIndex 4 → MIDI_PITCHES[4] = 77 (F5); sharp adds 1 → 78
        var element = new StaffElement(ElementType.CROTCHET);
        element.setStaffPosition(-4);
        element.setAccidental(StaffElement.Accidental.SHARP);

        assertThat(element.getPitch()).isEqualTo(MIDI_F5 + 1);
    }

    // T10d: staffPosition -1 (C5) with FLAT → MIDI 71 (B4, enharmonic)
    @Test
    void testGetPitchStaffPositionMinus1FlatIsCFlat5() {
        // C5 with flat → MIDI 72 - 1 = 71
        var element = new StaffElement(ElementType.CROTCHET);
        element.setStaffPosition(-1);
        element.setAccidental(StaffElement.Accidental.FLAT);

        assertThat(element.getPitch()).isEqualTo(MIDI_C5 - 1);
    }

    // T10e: staffPosition -7 is one octave above staffPosition 0 (both resolve to B)
    @Test
    void testGetPitchOctaveShiftUp7StepsIsOneSemitoneOctaveHigher() {
        // sp=0+NATURAL → B4 (71); sp=-7+NATURAL → same pitch index 0, one octave up = 83
        var elementBase = new StaffElement(ElementType.CROTCHET);
        elementBase.setStaffPosition(0);
        elementBase.setAccidental(StaffElement.Accidental.NATURAL);

        var elementUp = new StaffElement(ElementType.CROTCHET);
        elementUp.setStaffPosition(-STAFF_STEPS_PER_OCTAVE);
        elementUp.setAccidental(StaffElement.Accidental.NATURAL);

        assertThat(elementUp.getPitch()).isEqualTo(elementBase.getPitch() + SEMITONES_PER_OCTAVE);
    }

    // T10f: staffPosition 1 (A below middle) is MIDI_A5 minus one octave = A4 = 69
    @Test
    void testGetPitchStaffPositionPlusOneBelowNaturalIsA4() {
        // sp=1 → pitchIndex 6 → MIDI_A5(81) + octave-shift(-1) = 81 - 12 = 69 (A4)
        var element = new StaffElement(ElementType.CROTCHET);
        element.setStaffPosition(1);
        element.setAccidental(StaffElement.Accidental.NATURAL);

        assertThat(element.getPitch()).isEqualTo(MIDI_A5 - SEMITONES_PER_OCTAVE);
    }

    // T10g: verify full accidental table at a fixed staff position (sp=-1, C5 = MIDI 72)
    @Test
    void testGetPitchAccidentalTableAtC5() {
        // Verify each accidental ordinal maps to the correct MIDI adjustment at C5
        record Case(StaffElement.Accidental accidental, int expectedPitch) {}
        var cases = new Case[]{
            new Case(StaffElement.Accidental.NATURAL, MIDI_C5),           // +0
            new Case(StaffElement.Accidental.FLAT, MIDI_C5 - 1),          // -1
            new Case(StaffElement.Accidental.SHARP, MIDI_C5 + 1),         // +1
            new Case(StaffElement.Accidental.DOUBLE_NATURAL, MIDI_C5),    // +0
            new Case(StaffElement.Accidental.DOUBLE_FLAT, MIDI_C5 - 2),   // -2
            new Case(StaffElement.Accidental.DOUBLE_SHARP, MIDI_C5 + 2),  // +2
            new Case(StaffElement.Accidental.NATURAL_FLAT, MIDI_C5 - 1),  // -1
            new Case(StaffElement.Accidental.NATURAL_SHARP, MIDI_C5 + 1), // +1
        };

        for (var c : cases) {
            var element = new StaffElement(ElementType.CROTCHET);
            element.setStaffPosition(-1);
            element.setAccidental(c.accidental());

            assertThat(element.getPitch())
                .as("pitch with accidental %s", c.accidental())
                .isEqualTo(c.expectedPitch());
        }
    }

    // ------------------------------------------------------------------
    // T11: getPitchIndex — staff position → 0–6 with octave wrap
    // ------------------------------------------------------------------

    @Test
    void testGetPitchIndexCoversStaffPositionsAroundOctaveBoundary() {
        // Non-positive staff positions: pitchIndex = (-sp) % 7
        // sp=0 → 0 (B), sp=-1 → 1 (C), ..., sp=-6 → 6 (A), sp=-7 wraps back to 0 (B)
        record Case(int staffPosition, int expectedIndex) {}
        var cases = new Case[]{
            new Case(0, 0),
            new Case(-1, 1),
            new Case(-2, 2),
            new Case(-3, 3),
            new Case(-4, 4),
            new Case(-5, 5),
            new Case(-6, 6),
            // wrap at octave boundary going negative
            new Case(-STAFF_STEPS_PER_OCTAVE, 0),
            new Case(-STAFF_STEPS_PER_OCTAVE - 1, 1),
            // positive staff positions: index = (7 - sp%7) % 7
            new Case(1, 6),   // (7-1)%7 = 6
            new Case(2, 5),   // (7-2)%7 = 5
            new Case(6, 1),   // (7-6)%7 = 1
            new Case(STAFF_STEPS_PER_OCTAVE, 0),     // (7-0)%7 = 0 — another wrap
            new Case(STAFF_STEPS_PER_OCTAVE + 1, 6), // (7-1)%7 = 6
        };

        for (var c : cases) {
            var element = new StaffElement(ElementType.CROTCHET);
            element.setStaffPosition(c.staffPosition());

            assertThat(element.getPitchIndex())
                .as("pitchIndex for staffPosition %d", c.staffPosition())
                .isEqualTo(c.expectedIndex());
        }
    }

    // ------------------------------------------------------------------
    // T13: getDefaultDurationWithDots — DOTTED_DURATION[dotCount] scaling
    // ------------------------------------------------------------------

    // 0 dots → plain duration (CROTCHET = 96 ticks)
    @Test
    void testGetDefaultDurationWithDotsZeroDotsReturnsBareDefaultDuration() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDotCount(0);

        assertThat(element.getDefaultDurationWithDots())
            .isEqualTo((int) (CROTCHET_TICKS * DURATION_PLAIN));
    }

    // 1 dot → 1.5× base duration
    @Test
    void testGetDefaultDurationWithDotsSingleDotScalesByOnePointFive() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDotCount(1);

        assertThat(element.getDefaultDurationWithDots())
            .isEqualTo((int) (CROTCHET_TICKS * DURATION_SINGLE_DOT));
    }

    // 2 dots → 1.75× base duration
    @Test
    void testGetDefaultDurationWithDotsDoubleDotScalesByOnePointSevenFive() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDotCount(2);

        assertThat(element.getDefaultDurationWithDots())
            .isEqualTo((int) (CROTCHET_TICKS * DURATION_DOUBLE_DOT));
    }

    // ------------------------------------------------------------------
    // T14: getDuration — fermata extends by 1.5×
    // ------------------------------------------------------------------

    // Without fermata: getDuration == getDefaultDurationWithDots
    @Test
    void testGetDurationWithoutFermataEqualsDefaultDurationWithDots() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.getDuration()).isEqualTo(element.getDefaultDurationWithDots());
    }

    // With fermata: getDuration == floor(getDefaultDurationWithDots * 1.5)
    @Test
    void testGetDurationWithFermataExtendsByHalfAgain() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.addAttachment(new FermataAttachment(element));

        int expectedDuration = (int) (element.getDefaultDurationWithDots() * FERMATA_EXTENSION);
        assertThat(element.getDuration()).isEqualTo(expectedDuration);
    }

    // ------------------------------------------------------------------
    // T15: findMidiDurationOverride — articulation % override, or -1
    // ------------------------------------------------------------------

    // No articulations → -1
    @Test
    void testFindMidiDurationOverrideNoArticulationReturnsMinusOne() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.findMidiDurationOverride()).isEqualTo(NO_MIDI_OVERRIDE);
    }

    // ACCENT has no override (midiDurationPercent = -1) → still returns -1
    @Test
    void testFindMidiDurationOverrideAccentOnlyReturnsMinusOne() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.addArticulation(new Articulation(element, ArticulationType.ACCENT));

        assertThat(element.findMidiDurationOverride()).isEqualTo(NO_MIDI_OVERRIDE);
    }

    // STACCATO overrides with 33% → returns 33
    @Test
    void testFindMidiDurationOverrideStaccatoReturnsStaccatoPercent() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.addArticulation(new Articulation(element, ArticulationType.STACCATO));

        assertThat(element.findMidiDurationOverride()).isEqualTo(STACCATO_MIDI_PERCENT);
    }

    // First articulation wins: STACCATO + ACCENT → returns STACCATO percent
    @Test
    void testFindMidiDurationOverrideFirstArticulationWins() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
        element.addArticulation(new Articulation(element, ArticulationType.ACCENT));

        assertThat(element.findMidiDurationOverride()).isEqualTo(STACCATO_MIDI_PERCENT);
    }

    // ------------------------------------------------------------------
    // T7 (Row 7): isEligibleForLyric — non-rests are always eligible; rests only
    //             if they already carry a non-blank lyric for the given verse
    // ------------------------------------------------------------------

    // (a) A non-rest note is always eligible regardless of lyric state
    @Test
    void testIsEligibleForLyricNonRestIsAlwaysEligible() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.isEligibleForLyric(1)).isTrue();
    }

    // (b) A rest with no lyric for the verse is not eligible
    @Test
    void testIsEligibleForLyricRestWithNoLyricIsNotEligible() {
        var element = new StaffElement(ElementType.CROTCHET_REST);

        assertThat(element.isEligibleForLyric(1)).isFalse();
    }

    // (c) A rest that already carries a non-blank lyric for the verse is eligible
    @Test
    void testIsEligibleForLyricRestWithNonBlankLyricIsEligible() {
        var element = new StaffElement(ElementType.CROTCHET_REST);
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "la", Lyric.Extend.NONE);

        assertThat(element.isEligibleForLyric(1)).isTrue();
    }

    // ------------------------------------------------------------------
    // T9 (Row 9): hasLedgerLines — delegates to getLedgerLineCount() > 0
    // ------------------------------------------------------------------

    // Staff position within the 5-line staff (sp=0, middle) → no ledger lines
    @Test
    void testHasLedgerLinesReturnsFalseWhenCountIsZero() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setStaffPosition(0);

        assertThat(element.hasLedgerLines()).isFalse();
    }

    // Staff position sp=-6 is beyond the top staff line → 1 ledger line → true
    @Test
    void testHasLedgerLinesReturnsTrueWhenCountIsPositive() {
        // sp=-6: abs=6, even, (6-4)/2=1 ledger line
        var element = new StaffElement(ElementType.CROTCHET);

        // Staff position 6 half-steps above the staff requires one ledger line
        var staffPositionRequiringOneLedgerLine = -6;
        element.setStaffPosition(staffPositionRequiringOneLedgerLine);

        assertThat(element.hasLedgerLines()).isTrue();
    }

    // ------------------------------------------------------------------
    // T12 (Row 12): findLastAccidental — inherits from same-position predecessor,
    //               or falls back to the key signature if none exists
    // ------------------------------------------------------------------

    // (a) Same-position predecessor with an explicit accidental → that accidental
    //     is returned for the successor (which has no accidental of its own)
    @Test
    void testFindLastAccidentalInheritsPredecessorAccidental() {
        // Build a minimal two-note line: both notes at sp=-4 (F5 position).
        // The first note carries an explicit SHARP; the second has none.
        // findLastAccidental() on the second note must return SHARP (inherited).
        var song = new Song();
        var line = song.getLine(0);
        var predecessor = new StaffElement(ElementType.CROTCHET);
        predecessor.setStaffPosition(-4);
        predecessor.setAccidental(StaffElement.Accidental.SHARP);
        var successor = new StaffElement(ElementType.CROTCHET);
        successor.setStaffPosition(-4);
        song.withoutMutationTracking(() -> {
            line.addElement(predecessor);
            line.addElement(successor);
        });

        assertThat(successor.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // (b) No predecessor at the same position → falls back to key signature.
    //     Line is set to 1 sharp (F#); note is at sp=-4 (pitchIndex=4=F).
    //     keyExists(4) is true, so the fallback must return SHARP.
    @Test
    void testFindLastAccidentalFallsBackToKeySignature() {
        // 1 sharp in key sig covers pitchIndex 4 (F); note at sp=-4 has pitchIndex 4.
        // With no predecessor at the same staff position, the key-signature accidental
        // (SHARP) must be returned.
        var song = new Song();
        var line = song.getLine(0);
        var note = new StaffElement(ElementType.CROTCHET);
        note.setStaffPosition(-4);
        song.withoutMutationTracking(() -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(1);
            line.addElement(note);
        });

        assertThat(note.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // ------------------------------------------------------------------
    // T16 (Row 16): setAccidental(null) clears isAccidentalInParentheses
    // ------------------------------------------------------------------

    @Test
    void testSetAccidentalNullClearsIsAccidentalInParentheses() {
        // Set an accidental and mark it as being in parentheses, then clear the accidental.
        // The parentheses flag must be false afterwards, because it has no accidental to annotate.
        var element = new StaffElement(ElementType.CROTCHET);
        element.setAccidental(StaffElement.Accidental.SHARP);
        element.setAccidentalInParentheses(true);

        element.setAccidental(null);

        assertThat(element.isAccidentalInParentheses()).isFalse();
    }

    // ------------------------------------------------------------------
    // T17 (Row 17): setAccidentalInParentheses no-ops when accidental is null
    // ------------------------------------------------------------------

    @Test
    void testSetAccidentalInParenthesesNoOpsWhenAccidentalIsNull() {
        // With no accidental set, calling setAccidentalInParentheses(true) must not
        // activate the flag — there is nothing to parenthesize.
        var element = new StaffElement(ElementType.CROTCHET);

        element.setAccidentalInParentheses(true);

        assertThat(element.isAccidentalInParentheses()).isFalse();
    }

    // ------------------------------------------------------------------
    // T7: setLyricForVerse(1, ...) replaces existing verse-1 entry
    @Test
    void testSetLyricForVerseReplacesExisting() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "old", Lyric.Extend.NONE);

        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "new", Lyric.Extend.NONE);

        assertThat(element.getLyrics()).hasSize(1);
        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text")
            .isEqualTo("new");
    }

    // T8: setLyricForVerse(1, ..., "", NONE) removes verse-1 entry
    @Test
    void testSetLyricForVerseEmptyTextRemoves() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "text", Lyric.Extend.NONE);

        element.setLyricForVerse(1, null, false, "", Lyric.Extend.NONE);

        assertThat(element.getLyricForVerse(1)).isNull();
        assertThat(element.getLyrics()).isEmpty();
    }

    // T9: setLyricForVerse(2, ...) on element with verse-1 only adds verse-2 without disturbing verse-1
    @Test
    void testSetLyricForVerseAddsWithoutDisturbing() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "verse1", Lyric.Extend.NONE);

        element.setLyricForVerse(2, Lyric.Syllabic.SINGLE, false, "verse2", Lyric.Extend.NONE);

        assertThat(element.getLyrics()).hasSize(2);
        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text")
            .isEqualTo("verse1");
        assertThat(element.getLyricForVerse(2)).isNotNull()
            .extracting("text")
            .isEqualTo("verse2");
    }

    // Edge case: null text also removes
    @Test
    void testSetLyricForVerseNullTextRemoves() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "text", Lyric.Extend.NONE);

        element.setLyricForVerse(1, null, false, null, Lyric.Extend.NONE);

        assertThat(element.getLyricForVerse(1)).isNull();
    }

    // Edge case: blank text with whitespace also removes
    @Test
    void testSetLyricForVerseBlankTextRemoves() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "text", Lyric.Extend.NONE);

        element.setLyricForVerse(1, null, false, "   ", Lyric.Extend.NONE);

        assertThat(element.getLyricForVerse(1)).isNull();
    }

    // T8a: non-blank text + START → syllable entry + melisma start
    @Test
    void testSetLyricForVerseNonBlankStartAddsEntry() {
        var element = new StaffElement(ElementType.CROTCHET);

        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "la", Lyric.Extend.START);

        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text", "extend", "syllabic")
            .containsExactly("la", Lyric.Extend.START, Lyric.Syllabic.SINGLE);
    }

    // T8b: non-blank text + CONTINUE → throws
    @Test
    void testSetLyricForVerseNonBlankContinueThrows() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThatThrownBy(
            () -> element.setLyricForVerse(1, null, false, "la", Lyric.Extend.CONTINUE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("carrier");
    }

    // T8c: non-blank text + STOP → throws
    @Test
    void testSetLyricForVerseNonBlankStopThrows() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThatThrownBy(
            () -> element.setLyricForVerse(1, null, false, "la", Lyric.Extend.STOP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("carrier");
    }

    // T8d: blank text + START → throws
    @Test
    void testSetLyricForVerseBlankStartThrows() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThatThrownBy(
            () -> element.setLyricForVerse(1, null, false, "", Lyric.Extend.START))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("START");
    }

    // T8e: blank text + CONTINUE → extender-carrier entry with null syllabic
    @Test
    void testSetLyricForVerseBlankContinueAddsCarrier() {
        var element = new StaffElement(ElementType.CROTCHET);

        element.setLyricForVerse(1, null, false, "", Lyric.Extend.CONTINUE);

        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text", "extend", "syllabic", "compound")
            .containsExactly("", Lyric.Extend.CONTINUE, null, false);
    }

    // T8f: blank text + STOP → extender-carrier entry with null syllabic
    @Test
    void testSetLyricForVerseBlankStopAddsCarrier() {
        var element = new StaffElement(ElementType.CROTCHET);

        element.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);

        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text", "extend", "syllabic", "compound")
            .containsExactly("", Lyric.Extend.STOP, null, false);
    }
}
