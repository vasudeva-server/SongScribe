/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.midi.Synthesizer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.playback.MidiController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PreferencesDialog}: {@code programToIndex},
 * {@code ensureInstrumentsLoaded} idempotency and alphabetic sort,
 * and {@code volumeToSliderIndex} nearest-stop snapping.
 */
class PreferencesDialogTest extends UnitTest {

    // Convenience aliases into the shared constant so tests stay in sync
    // with the production stops array automatically.
    private static final int[] STOPS = PreferencesDialog.VALID_VOLUME_STOPS;
    private static final int LAST_STOP_INDEX = STOPS.length - 1;

    @BeforeEach
    void resetInstruments() {
        // Always start from a clean slate so tests are independent
        PreferencesDialog.resetInstrumentsForTesting();
    }

    @AfterEach
    void cleanUpSynthesizer() {
        // Restore null synthesizer so other tests are not affected
        MidiController.synthesizer = null;
        PreferencesDialog.resetInstrumentsForTesting();
    }

    // ---- programToIndex ----

    @Nested
    class ProgramToIndex {

        /**
         * Sets up a mock synthesizer that returns the given instruments.
         * Instruments are returned by getMidiController.synthesizer.getLoadedInstruments().
         */
        private void setUpSynthesizerWith(String[] names, int[] programs)
                throws MidiUnavailableException {
            var synth = mock(Synthesizer.class);
            var instruments = new Instrument[names.length];

            for (var i = 0; i < names.length; i++) {
                var instr = mock(Instrument.class);
                var patch = new Patch(0, programs[i]);
                when(instr.getName()).thenReturn(names[i]);
                when(instr.getPatch()).thenReturn(patch);
                instruments[i] = instr;
            }

            when(synth.getLoadedInstruments()).thenReturn(instruments);
            MidiController.synthesizer = synth;
        }

        @Test
        void testReturnsZeroOnMiss() throws MidiUnavailableException {
            setUpSynthesizerWith(new String[] { "Piano", "Violin" }, new int[] { 0, 40 });

            // Program 99 is not in the list — must return 0
            assertThat(PreferencesDialog.programToIndex(99))
                .as("programToIndex returns 0 when program is not in the list")
                .isZero();
        }

        @Test
        void testReturnsCorrectIndexOnExactMatch() throws MidiUnavailableException {
            // After alphabetic sort: "Piano"→0, "Violin"→40 becomes ["Piano", "Violin"]
            // Piano (program 0) → index 0, Violin (program 40) → index 1
            setUpSynthesizerWith(new String[] { "Piano", "Violin" }, new int[] { 0, 40 });

            assertThat(PreferencesDialog.programToIndex(0))
                .as("programToIndex returns 0 for Piano (first instrument after sort)")
                .isZero();
            assertThat(PreferencesDialog.programToIndex(40))
                .as("programToIndex returns 1 for Violin (second instrument after sort)")
                .isEqualTo(1);
        }

        @Test
        void testReturnsFirstMatchWhenDuplicates() throws MidiUnavailableException {
            // Two instruments with the same program number — must return the first matching index
            setUpSynthesizerWith(
                new String[] { "Alpha", "Beta", "Gamma" },
                new int[] { 10, 10, 20 }
            );

            var index = PreferencesDialog.programToIndex(10);
            assertThat(index)
                .as("programToIndex returns first matching index when duplicates exist")
                .isEqualTo(0);
        }
    }

    // ---- ensureInstrumentsLoaded ----

    @Nested
    class EnsureInstrumentsLoaded {

        @Test
        void testLoadsOnlyOnce() throws MidiUnavailableException {
            var synth = mock(Synthesizer.class);
            var instr = mock(Instrument.class);
            when(instr.getName()).thenReturn("Piano");
            when(instr.getPatch()).thenReturn(new Patch(0, 0));
            when(synth.getLoadedInstruments()).thenReturn(new Instrument[] { instr });
            MidiController.synthesizer = synth;

            PreferencesDialog.ensureInstrumentsLoaded();
            // Second call must not re-read the synthesizer (idempotency guard)
            PreferencesDialog.ensureInstrumentsLoaded();

            // The synthesizer must be queried exactly once, confirming the guard works
            verify(synth, times(1)).getLoadedInstruments();

            assertThat(PreferencesDialog.getInstrumentStrings())
                .as("ensureInstrumentsLoaded is idempotent — loads only once")
                .hasSize(1)
                .containsExactly("Piano");
        }

        @Test
        void testInstrumentsSortedAlphabetically() throws MidiUnavailableException {
            var synth = mock(Synthesizer.class);
            // Deliberately out of alphabetical order
            var instrZ = mock(Instrument.class);
            when(instrZ.getName()).thenReturn("Zither");
            when(instrZ.getPatch()).thenReturn(new Patch(0, 99));

            var instrA = mock(Instrument.class);
            when(instrA.getName()).thenReturn("Accordion");
            when(instrA.getPatch()).thenReturn(new Patch(0, 21));

            var instrM = mock(Instrument.class);
            when(instrM.getName()).thenReturn("Marimba");
            when(instrM.getPatch()).thenReturn(new Patch(0, 12));

            when(synth.getLoadedInstruments())
                .thenReturn(new Instrument[] { instrZ, instrA, instrM });
            MidiController.synthesizer = synth;

            PreferencesDialog.ensureInstrumentsLoaded();

            assertThat(PreferencesDialog.getInstrumentStrings())
                .as("instruments sorted alphabetically by name")
                .containsExactly("Accordion", "Marimba", "Zither");
        }

        @Test
        void testWithNoSynthesizerProducesEmptyArrays() {
            // MidiController.synthesizer is null (default in tests)
            PreferencesDialog.ensureInstrumentsLoaded();

            assertThat(PreferencesDialog.getInstrumentStrings())
                .as("no instruments loaded when synthesizer is null")
                .isEmpty();
            assertThat(PreferencesDialog.getInstrumentPrograms())
                .as("no programs loaded when synthesizer is null")
                .isEmpty();
        }
    }

    // ---- volumeToSliderIndex ----

    @Nested
    class VolumeToSliderIndex {

        @Test
        void testExactStopReturnsItsOwnIndex() {
            for (var i = 0; i < STOPS.length; i++) {
                var index = i;
                assertThat(PreferencesDialog.volumeToSliderIndex(STOPS[i]))
                    .as("exact stop value %d must map to its own index %d", STOPS[i], index)
                    .isEqualTo(index);
            }
        }

        @Test
        void testValueBelowAllStopsSnapsToFirst() {
            // 0 is below all stops; nearest is STOPS[0]
            assertThat(PreferencesDialog.volumeToSliderIndex(0))
                .as("value below all stops snaps to index 0")
                .isZero();
        }

        @Test
        void testValueAboveAllStopsSnapsToLast() {
            // 127 is above all stops; nearest is STOPS[LAST_STOP_INDEX]
            assertThat(PreferencesDialog.volumeToSliderIndex(127))
                .as("value above all stops snaps to last index")
                .isEqualTo(LAST_STOP_INDEX);
        }

        @Test
        void testMidpointSnapsBetween50And63() {
            // STOPS = { 50, 63, ... }; midpoint is 56.5
            // dist(56, STOPS[0])=6, dist(56, STOPS[1])=7 → snaps to STOPS[0] (index 0)
            var between50and63Lower = STOPS[0] + (STOPS[1] - STOPS[0]) / 2;
            assertThat(PreferencesDialog.volumeToSliderIndex(between50and63Lower))
                .as("%d is closer to %d than %d — snaps to index 0", between50and63Lower, STOPS[0], STOPS[1])
                .isZero();

            // dist(57, STOPS[0])=7, dist(57, STOPS[1])=6 → snaps to STOPS[1] (index 1)
            var between50and63Upper = between50and63Lower + 1;
            assertThat(PreferencesDialog.volumeToSliderIndex(between50and63Upper))
                .as("%d is closer to %d than %d — snaps to index 1", between50and63Upper, STOPS[1], STOPS[0])
                .isEqualTo(1);
        }

        @Test
        void testMidpointSnapsBetween75And88() {
            // STOPS = { ..., 75, 88, ... }; midpoint is 81.5
            // dist(81, STOPS[2])=6, dist(81, STOPS[3])=7 → snaps to STOPS[2] (index 2)
            var between75and88Lower = STOPS[2] + (STOPS[3] - STOPS[2]) / 2;
            assertThat(PreferencesDialog.volumeToSliderIndex(between75and88Lower))
                .as("%d is closer to %d — snaps to index 2", between75and88Lower, STOPS[2])
                .isEqualTo(2);

            // dist(82, STOPS[2])=7, dist(82, STOPS[3])=6 → snaps to STOPS[3] (index 3)
            var between75and88Upper = between75and88Lower + 1;
            assertThat(PreferencesDialog.volumeToSliderIndex(between75and88Upper))
                .as("%d is closer to %d — snaps to index 3", between75and88Upper, STOPS[3])
                .isEqualTo(3);
        }
    }
}
