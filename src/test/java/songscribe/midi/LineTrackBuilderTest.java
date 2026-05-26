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

package songscribe.midi;

import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.Tuplet;
import songscribe.ui.playback.PlaybackController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

class LineTrackBuilderTest extends UnitTest {

    // Quarter-note tempo — reference note duration == PPQ
    private static final Tempo CROTCHET_TEMPO = new Tempo(120, Duration.CROTCHET, "", false);

    // -------------------------------------------------------------------------
    // Row 23 — getElementDurationWithTuplet
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetElementDurationWithTuplet {

        @Test
        void testNonTupletElementReturnsRawDuration() {
            // A crotchet (PPQ ticks) with no tuplet → raw duration returned unchanged.
            var line = detachedLine();
            line.addElement(crotchet());
            var builder = new LineTrackBuilder(line);

            var result = builder.getElementDurationWithTuplet(0, CROTCHET_TEMPO);

            assertThat(result).isEqualTo(PPQ);
        }

        @Test
        void testTripletElementDurationIsScaledDown() {
            // 3 quavers as a triplet over a crotchet reference (3-in-2 feel):
            //   tupletDuration = (3 × PPQ/2) / PPQ = 1.5 → ≥1 branch
            //   factor = floor(1.5) / 1.5 = 1/1.5 ≈ 0.6667
            //   rounded duration of one quaver (PPQ/2) × 0.6667 ≈ PPQ/3
            var line = detachedLine();
            var q1 = ElementType.QUAVER.newInstance();
            var q2 = ElementType.QUAVER.newInstance();
            var q3 = ElementType.QUAVER.newInstance();
            line.addElement(q1);
            line.addElement(q2);
            line.addElement(q3);
            line.addRangeElement(new Tuplet(q1, q3, 3));

            var builder = new LineTrackBuilder(line);
            var result = builder.getElementDurationWithTuplet(0, CROTCHET_TEMPO);

            // Expected: Math.round((PPQ/2) × (2/3)) = Math.round(PPQ/3)
            var expected = Math.round((PPQ / 2f) * (2f / 3f));
            assertThat(result).isEqualTo(expected);
        }
    }

    // -------------------------------------------------------------------------
    // Row 24 — getTupletFactor  (all five branches)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetTupletFactor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("tupletFactorCases")
        void testTupletFactor(String description, float expected, float actual) {
            assertThat(actual).isCloseTo(expected, offset(0.0001f));
        }

        static Stream<Arguments> tupletFactorCases() {
            // Case 1: no tuplet → factor must be 1.0
            var line1 = detachedLine();
            line1.addElement(crotchet());
            var builder1 = new LineTrackBuilder(line1);
            var noTupletFactor = builder1.getTupletFactor(0, CROTCHET_TEMPO);

            // Case 2: tupletDuration < 1 — three semiquavers over a crotchet reference
            //   tupletDuration = (3 × PPQ/4) / PPQ = 3/4 = 0.75 → <1 branch
            //   log2(0.75) → floor = -1 → newDuration = 2^(-1) = 0.5
            //   factor = 0.5 / 0.75 = 2/3
            var line2 = detachedLine();
            var sq1 = ElementType.SEMIQUAVER.newInstance();
            var sq2 = ElementType.SEMIQUAVER.newInstance();
            var sq3 = ElementType.SEMIQUAVER.newInstance();
            line2.addElement(sq1);
            line2.addElement(sq2);
            line2.addElement(sq3);
            line2.addRangeElement(new Tuplet(sq1, sq3, 3));
            var builder2 = new LineTrackBuilder(line2);
            var smallTripletFactor = builder2.getTupletFactor(0, CROTCHET_TEMPO);

            // Case 3: tupletDuration = 1.0 (floor(1)=1, not >1 so no decrement) → factor = 1.0
            //   Two crotchets over a minim reference: (2 × PPQ) / (PPQ×2) = 1.0
            var mimTempo = new Tempo(120, Duration.MINIM, "", false);
            var line3 = detachedLine();
            var c3a = crotchet();
            var c3b = crotchet();
            line3.addElement(c3a);
            line3.addElement(c3b);
            line3.addRangeElement(new Tuplet(c3a, c3b, 2));
            var builder3 = new LineTrackBuilder(line3);
            var unitDurationFactor = builder3.getTupletFactor(0, mimTempo);

            // Case 4: tupletDuration > 1 — quintuplet: 5 quavers over crotchet reference
            //   tupletDuration = (5 × PPQ/2) / PPQ = 2.5 → ≥1 branch
            //   floor(2.5)=2, 2 ≠ 2.5 so no decrement → factor = 2/2.5 = 0.8
            var line4 = detachedLine();
            var qv1 = ElementType.QUAVER.newInstance();
            var qv2 = ElementType.QUAVER.newInstance();
            var qv3 = ElementType.QUAVER.newInstance();
            var qv4 = ElementType.QUAVER.newInstance();
            var qv5 = ElementType.QUAVER.newInstance();
            line4.addElement(qv1);
            line4.addElement(qv2);
            line4.addElement(qv3);
            line4.addElement(qv4);
            line4.addElement(qv5);
            line4.addRangeElement(new Tuplet(qv1, qv5, 5));
            var builder4 = new LineTrackBuilder(line4);
            var quintupletFactor = builder4.getTupletFactor(0, CROTCHET_TEMPO);

            // Case 5: newDuration == tupletDuration && newDuration > 1 → newDuration--
            //   4 quavers over crotchet: tupletDuration = (4 × PPQ/2)/PPQ = 2.0 (exact integer > 1)
            //   floor(2.0)=2.0; 2.0==2.0 && 2.0>1 → newDuration=1.0 → factor = 1.0/2.0 = 0.5
            var line5 = detachedLine();
            var ev1 = ElementType.QUAVER.newInstance();
            var ev2 = ElementType.QUAVER.newInstance();
            var ev3 = ElementType.QUAVER.newInstance();
            var ev4 = ElementType.QUAVER.newInstance();
            line5.addElement(ev1);
            line5.addElement(ev2);
            line5.addElement(ev3);
            line5.addElement(ev4);
            line5.addRangeElement(new Tuplet(ev1, ev4, 4));
            var builder5 = new LineTrackBuilder(line5);
            var decrementBranchFactor = builder5.getTupletFactor(0, CROTCHET_TEMPO);

            return Stream.of(
                Arguments.of("noTuplet returns 1.0", 1.0f, noTupletFactor),
                Arguments.of("tupletDuration<1 (3 semiquavers / crotchet ref) returns 2/3",
                    2f / 3f, smallTripletFactor),
                Arguments.of("tupletDuration=1.0 (2 crotchets / minim ref) returns 1.0",
                    1.0f, unitDurationFactor),
                Arguments.of("tupletDuration>1 (quintuplet) returns 0.8",
                    0.8f, quintupletFactor),
                Arguments.of("newDuration==tupletDuration>1 triggers newDuration-- returning 0.5",
                    0.5f, decrementBranchFactor)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Row 25 — calculateSoundingDuration / calculateSoundingPercent
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateSoundingDuration {

        @Test
        void testNoOverrideUsesSettingsPercent() {
            // A plain crotchet has no articulation that overrides duration (override == -1),
            // so the settings noteDurationPercent value must be used.
            var line = detachedLine();
            var note = crotchet();
            line.addElement(note);
            var builder = new LineTrackBuilder(line);
            var settings = new PlaybackSettings(0, 100, 80, false);

            var result = builder.calculateSoundingDuration(PPQ, note, settings);

            assertThat(result).isEqualTo((int) ((PPQ * (long) 80) / 100));
        }

        @Test
        void testOverridePresentIgnoresSettings() {
            // A staccato note has midiDurationPercent == 33 (ArticulationType.STACCATO).
            // That override must win regardless of settings.noteDurationPercent.
            var line = detachedLine();
            var note = crotchet();
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
            line.addElement(note);
            var builder = new LineTrackBuilder(line);
            var settings = new PlaybackSettings(0, 100, 80, false);

            var result = builder.calculateSoundingDuration(PPQ, note, settings);

            var staccatoPercent = ArticulationType.STACCATO.getMidiDurationPercent();
            assertThat(result).isEqualTo((int) ((PPQ * (long) staccatoPercent) / 100));
        }

        @Test
        void testCalculateSoundingPercentNoOverrideReturnsSettingsPercent() {
            // Static method: when override == -1, returns settings percent directly.
            var note = crotchet();
            var settings = new PlaybackSettings(0, 100, 75, false);

            var result = LineTrackBuilder.calculateSoundingPercent(note, settings);

            assertThat(result).isEqualTo(75);
        }

        @Test
        void testCalculateSoundingPercentWithStaccatoReturnsOverride() {
            // Static method: staccato articulation returns its midiDurationPercent (33).
            var note = crotchet();
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
            var settings = new PlaybackSettings(0, 100, 80, false);

            var result = LineTrackBuilder.calculateSoundingPercent(note, settings);

            assertThat(result).isEqualTo(ArticulationType.STACCATO.getMidiDurationPercent());
        }
    }

    // -------------------------------------------------------------------------
    // Row 26 — noteVelocity
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NoteVelocity {

        private static final int LINE_INDEX = 0;
        private static final int NOTE_INDEX = 0;

        @Test
        void testNullVelocityMapUsesNonAccentFallback() {
            // When velocityMap is null and the note has no accent, NOTE_VELOCITY is returned.
            var note = crotchet();

            var result = LineTrackBuilder.noteVelocity(note, null, LINE_INDEX, NOTE_INDEX);

            assertThat(result).isEqualTo(PlaybackController.NOTE_VELOCITY);
        }

        @Test
        void testNullVelocityMapWithAccentUsesAccentedVelocity() {
            // When velocityMap is null and the note carries an ACCENT articulation,
            // ACCENTED_NOTE_VELOCITY must be returned.
            var note = crotchet();
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));

            var result = LineTrackBuilder.noteVelocity(note, null, LINE_INDEX, NOTE_INDEX);

            assertThat(result).isEqualTo(PlaybackController.ACCENTED_NOTE_VELOCITY);
        }

        @Test
        void testVelocityMapPresentReturnsMapValue() {
            // When a VelocityMap is supplied, its pre-computed value must be used
            // regardless of any articulation on the note.
            var song = songWithOneCrotchet();
            var map = VelocityMap.build(song, VelocityMap.MAX_VELOCITY);
            var note = crotchet();

            var result = LineTrackBuilder.noteVelocity(note, map, LINE_INDEX, NOTE_INDEX);

            assertThat(result).isEqualTo(map.getVelocity(LINE_INDEX, NOTE_INDEX));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static songscribe.dom.Song songWithOneCrotchet() {
        var song = new songscribe.dom.Song();
        var line = detachedLine();
        line.addElement(crotchet());
        song.getLines().clear();
        song.getLines().add(line);
        return song;
    }
}
