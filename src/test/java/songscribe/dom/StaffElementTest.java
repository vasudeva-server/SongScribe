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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    // Staff-position distances used in getLedgerLineCount boundary tests.
    // These are absolute values; negate for above-staff positions.
    // Formula: even abs(sp) → count = max(0,(abs-4)/2); odd rounds down first.
    private static final int ONE_LEDGER_EVEN_DIST = 6;   // (6-4)/2 = 1
    private static final int ONE_LEDGER_ODD_DIST = 7;    // rounds to 6 → 1
    private static final int TWO_LEDGER_EVEN_DIST = 8;   // (8-4)/2 = 2
    private static final int THREE_LEDGER_EVEN_DIST = 10; // (10-4)/2 = 3
    // Innermost on-staff boundary: |staffPosition| <= INNER_STAFF_EXTENT → 0 ledger lines
    private static final int INNER_STAFF_EXTENT = 5;

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
        //noinspection LimitedScopeInnerClass
        record Case(StaffElement.Accidental accidental, int expectedPitch) {}
        var cases = new Case[]{
            new Case(StaffElement.Accidental.NATURAL, MIDI_C5),           // +0
            new Case(StaffElement.Accidental.FLAT, MIDI_C5 - 1),          // -1
            new Case(StaffElement.Accidental.SHARP, MIDI_C5 + 1),         // +1
            new Case(StaffElement.Accidental.DOUBLE_FLAT, MIDI_C5 - 2),   // -2
            new Case(StaffElement.Accidental.DOUBLE_SHARP, MIDI_C5 + 2),  // +2
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
        //noinspection LimitedScopeInnerClass
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

        var expectedDuration = (int) (element.getDefaultDurationWithDots() * FERMATA_EXTENSION);
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
    // Direction — getDirection() defaults to DOWN and tracks setUpper(boolean)
    // ------------------------------------------------------------------

    @Test
    void testDirectionDefaultsToDown() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.getDirection()).isEqualTo(StaffElement.Direction.DOWN);
        assertThat(element.isUpper()).isFalse();
    }

    @Test
    void testSetUpperTrueSetsDirectionUp() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setUpper(true);

        assertThat(element.getDirection()).isEqualTo(StaffElement.Direction.UP);
        assertThat(element.isUpper()).isTrue();
    }

    @Test
    void testSetUpperFalseSetsDirectionDown() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setUpper(true);
        element.setUpper(false);

        assertThat(element.getDirection()).isEqualTo(StaffElement.Direction.DOWN);
        assertThat(element.isUpper()).isFalse();
    }

    @Test
    void testSetDirectionUpSetsIsUpperTrue() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDirection(StaffElement.Direction.UP);

        assertThat(element.isUpper()).isTrue();
    }

    @Test
    void testSetDirectionDownSetsIsUpperFalse() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDirection(StaffElement.Direction.UP);
        element.setDirection(StaffElement.Direction.DOWN);

        assertThat(element.isUpper()).isFalse();
    }

    // ------------------------------------------------------------------
    // Direction.opposite() — flips UP and DOWN
    // ------------------------------------------------------------------

    @Test
    void testOppositeReturnsDownForUp() {
        assertThat(StaffElement.Direction.UP.opposite()).isEqualTo(StaffElement.Direction.DOWN);
    }

    @Test
    void testOppositeReturnsUpForDown() {
        assertThat(StaffElement.Direction.DOWN.opposite()).isEqualTo(StaffElement.Direction.UP);
    }

    // ------------------------------------------------------------------
    // Direction.sign() — +1 for UP, -1 for DOWN
    // ------------------------------------------------------------------

    @Test
    void testSignReturnsPositiveOneForUp() {
        assertThat(StaffElement.Direction.UP.sign()).isEqualTo(1);
    }

    @Test
    void testSignReturnsNegativeOneForDown() {
        assertThat(StaffElement.Direction.DOWN.sign()).isEqualTo(-1);
    }

    // ------------------------------------------------------------------
    // defaultDirection — up for positive staff position or grace notes, down otherwise
    // ------------------------------------------------------------------

    @Test
    void testDefaultDirectionReturnsUpWhenStaffPositionIsPositive() {
        var note = crotchet();
        note.setStaffPosition(1);

        assertThat(StaffElement.defaultDirection(note)).isEqualTo(StaffElement.Direction.UP);
    }

    @Test
    void testDefaultDirectionReturnsDownWhenStaffPositionIsZeroAndNotGrace() {
        // staffPosition == 0 and a non-grace type → stem should point down.
        var note = crotchet();
        note.setStaffPosition(0);

        assertThat(StaffElement.defaultDirection(note)).isEqualTo(StaffElement.Direction.DOWN);
    }

    @Test
    void testDefaultDirectionReturnsUpForGraceNoteRegardlessOfStaffPosition() {
        // Grace notes always return UP regardless of staff position.
        var grace = graceQuaver();
        grace.setStaffPosition(0);

        assertThat(StaffElement.defaultDirection(grace)).isEqualTo(StaffElement.Direction.UP);
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
    // findEffectiveAccidental — barline/repeat barriers and the tie escape
    // ------------------------------------------------------------------

    // Staff position -4 → pitchIndex 4 (F), reused throughout this block so every
    // fixture resolves the same pitch class.
    private static final int STAFF_POSITION_F5 = -4;

    // A key signature covering only F (pitchIndex 4), matching the SHARPS-row fixture
    // already used by testFindLastAccidentalFallsBackToKeySignature above.
    private static final int KEY_ACCIDENTAL_COUNT_F_ONLY = 1;

    // A key signature covering all seven letters, so F is affected regardless of
    // FLATS/SHARPS ordering — used where the fallback value must differ from SHARP.
    private static final int KEY_ACCIDENTAL_COUNT_ALL = 7;

    // (a) An explicit accidental before a barline/repeat barrier is not inherited by
    //     a later note at the same staff position — the scan stops at the barrier and
    //     falls back to the key signature (none set here, so the result is null, which
    //     could not happen if the predecessor's SHARP had been found).
    @ParameterizedTest
    @EnumSource(
        value = ElementType.class,
        names = {
            "SINGLE_BARLINE", "DOUBLE_BARLINE", "FINAL_DOUBLE_BARLINE",
            "REPEAT_LEFT", "REPEAT_RIGHT", "REPEAT_LEFT_RIGHT"
        }
    )
    void testAccidentalNotInheritedAcrossBarrierType(ElementType barrierType) {
        var song = new Song();
        var line = song.getLine(0);
        var predecessor = new StaffElement(ElementType.CROTCHET);
        predecessor.setStaffPosition(STAFF_POSITION_F5);
        predecessor.setAccidental(StaffElement.Accidental.SHARP);
        var barrier = new StaffElement(barrierType);
        var successor = new StaffElement(ElementType.CROTCHET);
        successor.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.addElement(predecessor);
            line.addElement(barrier);
            line.addElement(successor);
        });

        assertThat(successor.findLastAccidental()).isNull();
    }

    // (b) A breath mark between the two notes is deliberately not a barrier — the
    //     accidental is still inherited across it.
    @Test
    void testBreathMarkDoesNotBlockAccidentalInheritance() {
        var song = new Song();
        var line = song.getLine(0);
        var predecessor = new StaffElement(ElementType.CROTCHET);
        predecessor.setStaffPosition(STAFF_POSITION_F5);
        predecessor.setAccidental(StaffElement.Accidental.SHARP);
        var breathMark = new StaffElement(ElementType.BREATH_MARK);
        var successor = new StaffElement(ElementType.CROTCHET);
        successor.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.addElement(predecessor);
            line.addElement(breathMark);
            line.addElement(successor);
        });

        assertThat(successor.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // (c) A tie whose anchor sits before a barrier escapes it: the end note resolves
    //     to the anchor's explicit accidental instead of stopping at the barrier.
    @Test
    void testTiedNoteAcrossBarlineInheritsAccidentalFromTieAnchor() {
        var song = new Song();
        var line = song.getLine(0);
        var anchor = new StaffElement(ElementType.CROTCHET);
        anchor.setStaffPosition(STAFF_POSITION_F5);
        anchor.setAccidental(StaffElement.Accidental.SHARP);
        var barline = new StaffElement(ElementType.SINGLE_BARLINE);
        var end = new StaffElement(ElementType.CROTCHET);
        end.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.addElement(anchor);
            line.addElement(barline);
            line.addElement(end);
            line.addTie(new Tie(anchor, end));
        });

        assertThat(end.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // (c, negative) Without the tie, the same layout falls back to the key signature
    // instead of inheriting across the barline. FLATS covering all seven letters gives
    // FLAT, which is distinguishable from the anchor's SHARP.
    @Test
    void testNoteAfterBarlineWithoutTieFallsBackToKeySignature() {
        var song = new Song();
        var line = song.getLine(0);
        var anchor = new StaffElement(ElementType.CROTCHET);
        anchor.setStaffPosition(STAFF_POSITION_F5);
        anchor.setAccidental(StaffElement.Accidental.SHARP);
        var barline = new StaffElement(ElementType.SINGLE_BARLINE);
        var end = new StaffElement(ElementType.CROTCHET);
        end.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.setKeyType(KeyType.FLATS);
            line.setKeyAccidentalCount(KEY_ACCIDENTAL_COUNT_ALL);
            line.addElement(anchor);
            line.addElement(barline);
            line.addElement(end);
        });

        assertThat(end.findLastAccidental()).isEqualTo(StaffElement.Accidental.FLAT);
    }

    // (d) A chain of ties (A~B~C) carries the accidental across two barriers, one link
    //     at a time.
    @Test
    void testTiedNoteChainAcrossTwoBarlinesInheritsAccidental() {
        var song = new Song();
        var line = song.getLine(0);
        var noteA = new StaffElement(ElementType.CROTCHET);
        noteA.setStaffPosition(STAFF_POSITION_F5);
        noteA.setAccidental(StaffElement.Accidental.SHARP);
        var firstBarline = new StaffElement(ElementType.SINGLE_BARLINE);
        var noteB = new StaffElement(ElementType.CROTCHET);
        noteB.setStaffPosition(STAFF_POSITION_F5);
        var secondBarline = new StaffElement(ElementType.SINGLE_BARLINE);
        var noteC = new StaffElement(ElementType.CROTCHET);
        noteC.setStaffPosition(STAFF_POSITION_F5);
        var tieAB = new Tie(noteA, noteB);
        song.withoutMutationTracking(() -> {
            line.addElement(noteA);
            line.addElement(firstBarline);
            line.addElement(noteB);
            line.addElement(secondBarline);
            line.addElement(noteC);
            line.addTie(tieAB);
            line.addTie(new Tie(noteB, noteC));
        });

        assertThat(noteC.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // (d, negative) Removing the A~B link breaks the chain, so C can no longer escape
    // either barrier and falls back to the key signature.
    @Test
    void testRemovingTieBreaksChainFallsBackToKeySignature() {
        var song = new Song();
        var line = song.getLine(0);
        var noteA = new StaffElement(ElementType.CROTCHET);
        noteA.setStaffPosition(STAFF_POSITION_F5);
        noteA.setAccidental(StaffElement.Accidental.SHARP);
        var firstBarline = new StaffElement(ElementType.SINGLE_BARLINE);
        var noteB = new StaffElement(ElementType.CROTCHET);
        noteB.setStaffPosition(STAFF_POSITION_F5);
        var secondBarline = new StaffElement(ElementType.SINGLE_BARLINE);
        var noteC = new StaffElement(ElementType.CROTCHET);
        noteC.setStaffPosition(STAFF_POSITION_F5);
        var tieAB = new Tie(noteA, noteB);
        song.withoutMutationTracking(() -> {
            line.setKeyType(KeyType.FLATS);
            line.setKeyAccidentalCount(KEY_ACCIDENTAL_COUNT_ALL);
            line.addElement(noteA);
            line.addElement(firstBarline);
            line.addElement(noteB);
            line.addElement(secondBarline);
            line.addElement(noteC);
            line.addTie(tieAB);
            line.addTie(new Tie(noteB, noteC));
            line.removeTie(tieAB);
        });

        assertThat(noteC.findLastAccidental()).isEqualTo(StaffElement.Accidental.FLAT);
    }

    // (e) A tie whose anchor sits after the barrier (between the barrier and the end
    //     note) does not escape it — tieAnchorBefore requires the anchor to precede the
    //     barrier itself.
    @Test
    void testTieAnchorAfterBarrierDoesNotEscape() {
        var song = new Song();
        var line = song.getLine(0);
        var predecessor = new StaffElement(ElementType.CROTCHET);
        predecessor.setStaffPosition(STAFF_POSITION_F5);
        predecessor.setAccidental(StaffElement.Accidental.SHARP);
        var barline = new StaffElement(ElementType.SINGLE_BARLINE);
        var anchor = new StaffElement(ElementType.CROTCHET);
        anchor.setStaffPosition(STAFF_POSITION_F5);
        var end = new StaffElement(ElementType.CROTCHET);
        end.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.addElement(predecessor);
            line.addElement(barline);
            line.addElement(anchor);
            line.addElement(end);
            line.addTie(new Tie(anchor, end));
        });

        assertThat(end.findLastAccidental()).isNull();
    }

    // (e) A note that merely starts a tie (is the anchor, not the end) gets no escape
    //     when its own accidental is resolved — the escape only applies to the tie's
    //     end element.
    @Test
    void testTieAnchorItselfGetsNoEscapeFromItsOwnTie() {
        var song = new Song();
        var line = song.getLine(0);
        var predecessor = new StaffElement(ElementType.CROTCHET);
        predecessor.setStaffPosition(STAFF_POSITION_F5);
        predecessor.setAccidental(StaffElement.Accidental.SHARP);
        var barline = new StaffElement(ElementType.SINGLE_BARLINE);
        var anchor = new StaffElement(ElementType.CROTCHET);
        anchor.setStaffPosition(STAFF_POSITION_F5);
        var end = new StaffElement(ElementType.CROTCHET);
        end.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.addElement(predecessor);
            line.addElement(barline);
            line.addElement(anchor);
            line.addElement(end);
            line.addTie(new Tie(anchor, end));
        });

        assertThat(anchor.findLastAccidental()).isNull();
    }

    // (f) With no explicit accidental and no barrier, an unrelated element at a
    //     different staff position in between does not disturb the key-signature
    //     fallback.
    @Test
    void testKeySignatureFallbackUnaffectedByOrdinaryElementsWithoutBarrier() {
        var song = new Song();
        var line = song.getLine(0);
        var unrelated = new StaffElement(ElementType.CROTCHET);
        unrelated.setStaffPosition(STAFF_POSITION_F5 + 1);
        var note = new StaffElement(ElementType.CROTCHET);
        note.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(KEY_ACCIDENTAL_COUNT_F_ONLY);
            line.addElement(unrelated);
            line.addElement(note);
        });

        assertThat(note.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // (g) A note's own explicit accidental wins over the scan: getPitch() checks it first and
    //     never calls findEffectiveAccidental() when it is non-null.
    //
    //     Asserting only on getPitch() would not reach the scan at all, making the barline and
    //     the flatted note in this fixture inert and the test a duplicate of
    //     testGetPitchStaffPositionMinus4SharpIsFSharp5 above. So the scan is asserted on
    //     directly, and it is set up to disagree: blocked by the barrier it yields no accidental
    //     at all, while the note itself is sharp. getPitch() following the scan instead of the
    //     note's own accidental would therefore fail here, which is the point.
    @Test
    void testGetPitchPrefersOwnExplicitAccidentalOverTheBarrierBlockedScan() {
        var song = new Song();
        var line = song.getLine(0);
        var predecessor = new StaffElement(ElementType.CROTCHET);
        predecessor.setStaffPosition(STAFF_POSITION_F5);
        predecessor.setAccidental(StaffElement.Accidental.FLAT);
        var barline = new StaffElement(ElementType.SINGLE_BARLINE);
        var note = new StaffElement(ElementType.CROTCHET);
        note.setStaffPosition(STAFF_POSITION_F5);
        note.setAccidental(StaffElement.Accidental.SHARP);
        song.withoutMutationTracking(() -> {
            line.addElement(predecessor);
            line.addElement(barline);
            line.addElement(note);
        });

        assertThat(note.findLastAccidental())
            .as("the barrier blocks the predecessor's flat, so the scan offers nothing")
            .isNull();
        assertThat(note.getPitch())
            .as("getPitch uses the note's own sharp, not the scan")
            .isEqualTo(MIDI_F5 + 1);
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

    // ------------------------------------------------------------------
    // Row 21: clearArticulations — unsets owner + removes from children
    // ------------------------------------------------------------------

    @Test
    void testClearArticulationsUnsetsOwnerAndRemovesChildren() {
        var element = new StaffElement(ElementType.CROTCHET);
        var staccato = new Articulation(element, ArticulationType.STACCATO);
        var accent = new Articulation(element, ArticulationType.ACCENT);
        element.addArticulation(staccato);
        element.addArticulation(accent);

        element.clearArticulations();

        assertThat(element.getArticulations()).isEmpty();
        // Each articulation must have had its owner cleared
        assertThat(staccato.getOwnerElement()).isNull();
        assertThat(accent.getOwnerElement()).isNull();
        // The element must no longer list them as children
        assertThat(element.getChildren()).doesNotContain(staccato, accent);
    }

    // ------------------------------------------------------------------
    // Row 22: clearAttachments — unsets owner + removes from children
    // ------------------------------------------------------------------

    @Test
    void testClearAttachmentsUnsetsOwnerAndRemovesChildren() {
        var element = new StaffElement(ElementType.CROTCHET);
        var fermata = new FermataAttachment(element);
        var annotation = new AnnotationAttachment("note");
        element.addAttachment(fermata);
        element.addAttachment(annotation);

        element.clearAttachments();

        assertThat(element.getAttachments()).isEmpty();
        // Each attachment must have had its owner cleared
        assertThat(fermata.getOwnerElement()).isNull();
        assertThat(annotation.getOwnerElement()).isNull();
        // The element must no longer list them as children
        assertThat(element.getChildren()).doesNotContain(fermata, annotation);
    }

    // ------------------------------------------------------------------
    // Row 23: hasArticulation(type) — present → true, absent → false
    // ------------------------------------------------------------------

    @Test
    void testHasArticulationReturnsFalseWhenAbsent() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.hasArticulation(ArticulationType.STACCATO)).isFalse();
    }

    @Test
    void testHasArticulationReturnsTrueWhenPresent() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.addArticulation(new Articulation(element, ArticulationType.STACCATO));

        assertThat(element.hasArticulation(ArticulationType.STACCATO)).isTrue();
        // A different type that was not added must still report absent
        assertThat(element.hasArticulation(ArticulationType.ACCENT)).isFalse();
    }

    // ------------------------------------------------------------------
    // findArticulation(type) — returns the matching articulation, or null when absent
    // ------------------------------------------------------------------

    @Test
    void testFindArticulationReturnsNullWhenAbsent() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.findArticulation(ArticulationType.STACCATO)).isNull();
    }

    @Test
    void testFindArticulationReturnsMatchingArticulation() {
        var element = new StaffElement(ElementType.CROTCHET);
        var staccato = new Articulation(element, ArticulationType.STACCATO);
        element.addArticulation(staccato);

        assertThat(element.findArticulation(ArticulationType.STACCATO)).isSameAs(staccato);
        // A different type that was not added must return null
        assertThat(element.findArticulation(ArticulationType.ACCENT)).isNull();
    }

    // ------------------------------------------------------------------
    // Row 20: removeArticulation — unsets owner + removes from children
    // ------------------------------------------------------------------

    @Test
    void testRemoveArticulationUnsetsOwnerAndRemovesChild() {
        var element = new StaffElement(ElementType.CROTCHET);
        var staccato = new Articulation(element, ArticulationType.STACCATO);
        element.addArticulation(staccato);

        element.removeArticulation(staccato);

        assertThat(element.getArticulations()).doesNotContain(staccato);
        // Owner must be cleared after removal
        assertThat(staccato.getOwnerElement()).isNull();
        // The articulation must no longer appear in the element's child list
        assertThat(element.getChildren()).doesNotContain(staccato);
    }

    // ------------------------------------------------------------------
    // getLedgerLineCount — boundary tests (relocated from NoteAreaBuilderTest)
    // ------------------------------------------------------------------

    @Test
    void testGetLedgerLineCountAboveStaff() {
        var note = crotchet();

        note.setStaffPosition(-ONE_LEDGER_EVEN_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(-ONE_LEDGER_ODD_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(-TWO_LEDGER_EVEN_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(2);

        note.setStaffPosition(-THREE_LEDGER_EVEN_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(3);
    }

    @Test
    void testGetLedgerLineCountBelowStaff() {
        var note = crotchet();

        note.setStaffPosition(ONE_LEDGER_EVEN_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(ONE_LEDGER_ODD_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(TWO_LEDGER_EVEN_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(2);

        note.setStaffPosition(THREE_LEDGER_EVEN_DIST);
        assertThat(note.getLedgerLineCount()).isEqualTo(3);
    }

    @Test
    void testGetLedgerLineCountOnStaff() {
        var note = crotchet();

        // All positions within the staff (|sp| <= INNER_STAFF_EXTENT) require no ledger lines
        for (var sp = -INNER_STAFF_EXTENT; sp <= INNER_STAFF_EXTENT; sp++) {
            note.setStaffPosition(sp);
            assertThat(note.getLedgerLineCount())
                .as("staffPosition %d", sp)
                .isEqualTo(0);
        }
    }

    // ------------------------------------------------------------------
    // Row 24: Line.addElement — propagates to all attachments + articulations
    // ------------------------------------------------------------------

    @Test
    void testAddElementPropagatesLineToAllAttachmentsAndArticulations() {
        // Build an element with both an attachment and an articulation before
        // it is placed in a line, so their parentLine starts null.
        var element = new StaffElement(ElementType.CROTCHET);
        var fermata = new FermataAttachment(element);
        var staccato = new Articulation(element, ArticulationType.STACCATO);
        element.addAttachment(fermata);
        element.addArticulation(staccato);

        assertThat(fermata.getParentLine()).isNull();
        assertThat(staccato.getParentLine()).isNull();

        var song = new Song();
        var targetLine = song.getLine(0);
        song.withoutMutationTracking(() -> targetLine.addElement(element));

        assertThat(element.getParentLine()).isSameAs(targetLine);
        assertThat(fermata.getParentLine()).isSameAs(targetLine);
        assertThat(staccato.getParentLine()).isSameAs(targetLine);
    }

    // ------------------------------------------------------------------
    // A removed element is detached: no line means no key context, no index
    // ------------------------------------------------------------------

    /**
     * Builds a line in the key of one sharp (covering F) holding a single note at
     * staff position {@code -4} — pitch class F — and returns that note.
     */
    private static StaffElement noteInSharpKeyLine(Song song) {
        var line = song.getLine(0);
        var note = new StaffElement(ElementType.CROTCHET);
        note.setStaffPosition(STAFF_POSITION_F5);
        song.withoutMutationTracking(() -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(KEY_ACCIDENTAL_COUNT_F_ONLY);
            line.addElement(note);
        });

        return note;
    }

    private static void removeFromItsLine(Song song, StaffElement note) {
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.removeElement(line.getElementIndex(note)));
    }

    @Test
    void testFindLastAccidentalReturnsNullForARemovedElement() {
        var song = new Song();
        var note = noteInSharpKeyLine(song);

        assertThat(note.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);

        removeFromItsLine(song, note);

        // No line, so no key signature and no predecessors to scan.
        assertThat(note.findLastAccidental()).isNull();
    }

    @Test
    void testGetPitchDropsTheKeyAccidentalForARemovedElement() {
        var song = new Song();
        var note = noteInSharpKeyLine(song);

        // In the line, the key signature sharpens F5.
        assertThat(note.getPitch()).isEqualTo(MIDI_F5 + 1);

        removeFromItsLine(song, note);

        // Detached, findLastAccidental() returns null, so the pitch is the unaltered F5.
        assertThat(note.getPitch()).isEqualTo(MIDI_F5);
    }

    /**
     * A tie whose anchor is the line's first element and whose end is its second, so both
     * endpoint indices are known exactly rather than merely known to be found.
     */
    private static Tie tieOverTheFirstTwoElements(Song song, StaffElement anchor) {
        var end = new StaffElement(ElementType.CROTCHET);
        song.withoutMutationTracking(() -> song.getLine(0).addElement(end));

        return new Tie(anchor, end);
    }

    @Test
    void testAnchorElementIndexIsMinusOneForARemovedAnchor() {
        var song = new Song();
        var anchor = noteInSharpKeyLine(song);
        var tie = tieOverTheFirstTwoElements(song, anchor);

        // Pinned to the exact index, not merely "found": an off-by-one that still
        // resolves to some element would slip past an isNotEqualTo(-1) check.
        assertThat(tie.getAnchorElementIndex()).isEqualTo(0);

        removeFromItsLine(song, anchor);

        assertThat(tie.getAnchorElementIndex()).isEqualTo(-1);
    }

    @Test
    void testEndElementIndexIsMinusOneForARemovedEndElement() {
        var song = new Song();
        var anchor = noteInSharpKeyLine(song);
        var tie = tieOverTheFirstTwoElements(song, anchor);
        var end = tie.getEndElement();
        assertThat(end).isNotNull();

        assertThat(tie.getEndElementIndex()).isEqualTo(1);

        removeFromItsLine(song, end);

        // The end element alone is detached, so only its index goes away.
        assertThat(tie.getEndElementIndex()).isEqualTo(-1);
        assertThat(tie.getAnchorElementIndex()).isEqualTo(0);
    }
}
