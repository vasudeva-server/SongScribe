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

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import songscribe.UnitTest;
import songscribe.music.StaffElement.Glissando;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.midi.GlissandoMidiHelper.*;

class GlissandoMidiHelperTest extends UnitTest {

    @Nested
    class CalculateBendValue {

        @Test
        void testAtEndReachesFull() {
            // 7 semitones up, sensitivity = 7 => full bend at t=1.0
            var bend = calculateBendValue(1.0, 60, 67, 7);
            assertThat(bend).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testAtStartReturnsCenter() {
            var bend = calculateBendValue(0.0, 60, 72, 12);
            assertThat(bend).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testClampedToMaximum() {
            // Sensitivity smaller than interval would overshoot — should clamp
            var bend = calculateBendValue(1.0, 60, 72, 6);
            assertThat(bend).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testClampedToMinimum() {
            // Slide down with sensitivity smaller than interval
            var bend = calculateBendValue(1.0, 72, 60, 6);
            assertThat(bend).isEqualTo(0);
        }

        @Test
        void testDownwardSlide() {
            // Slide down: target < source => bend below center
            var bend = calculateBendValue(1.0, 67, 60, 7);
            assertThat(bend).isEqualTo(0);
        }

        @Test
        void testQuadraticCurveAt50Percent() {
            // t=0.5, curve = 0.25, interval = 12, sensitivity = 12
            // bend = 8192 + 0.25 * 8192 = 10240
            var bend = calculateBendValue(0.5, 60, 72, 12);
            assertThat(bend).isEqualTo(10240);
        }

        @Test
        void testQuadraticCurveAt75Percent() {
            // t=0.75, curve = 0.5625, interval = 12, sensitivity = 12
            // bend = 8192 + 0.5625 * 8192 = 12800
            var bend = calculateBendValue(0.75, 60, 72, 12);
            assertThat(bend).isEqualTo(12800);
        }

        @Test
        void testQuarterPosition() {
            // t=0.25, curve = 0.0625, interval = 12, sensitivity = 12
            // bend = 8192 + 0.0625 * 8192 = 8704
            var bend = calculateBendValue(0.25, 60, 72, 12);
            assertThat(bend).isEqualTo(8704);
        }
    }

    @Nested
    class CreatePitchBendMessages {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEmitsCorrectNumberOfEvents() throws InvalidMidiDataException {
            // slideDuration=12, step=3 => ticks 0,3,6,9,12 => 5 events
            createPitchBendMessages(track, 100, 12, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            assertThat(events).hasSize(5);
        }

        @Test
        void testFirstEventIsAtSlideStart() throws InvalidMidiDataException {
            createPitchBendMessages(track, 100, 12, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            assertThat(events.getFirst().getTick()).isEqualTo(100);
        }

        @Test
        void testFirstEventIsCenterBend() throws InvalidMidiDataException {
            createPitchBendMessages(track, 100, 12, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            assertThat(getBendValue(events.getFirst())).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testLastEventIsAtSlideEnd() throws InvalidMidiDataException {
            createPitchBendMessages(track, 100, 12, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            assertThat(events.getLast().getTick()).isEqualTo(112);
        }

        @Test
        void testNoEventsForZeroDuration() throws InvalidMidiDataException {
            createPitchBendMessages(track, 100, 0, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            assertThat(events).isEmpty();
        }

        @Test
        void testProgressivelyIncreasingBend() throws InvalidMidiDataException {
            createPitchBendMessages(track, 0, 12, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            var bendValues = events.stream().map(GlissandoMidiHelperTest::getBendValue).toList();

            // Each successive bend value should be >= the previous (monotonically increasing for upward slide)
            for (var i = 1; i < bendValues.size(); i++) {
                assertThat(bendValues.get(i)).isGreaterThanOrEqualTo(bendValues.get(i - 1));
            }
        }
    }

    @Nested
    class CreatePitchBendReset {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEmitsCenterBend() throws InvalidMidiDataException {
            createPitchBendReset(track, 200, 0);
            var events = getPitchBendEvents(track);
            assertThat(events).hasSize(1);
            assertThat(getBendValue(events.getFirst())).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testEmitsAtCorrectTick() throws InvalidMidiDataException {
            createPitchBendReset(track, 200, 0);
            var events = getPitchBendEvents(track);
            assertThat(events.getFirst().getTick()).isEqualTo(200);
        }
    }

    @Nested
    class CreateRpnMessages {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEmitsFourControlChangeMessages() throws InvalidMidiDataException {
            createRpnMessages(track, 0, 0, 12);
            var events = getControlChangeEvents(track);
            assertThat(events).hasSize(4);
        }

        @Test
        void testCorrectControllerNumbers() throws InvalidMidiDataException {
            createRpnMessages(track, 0, 0, 7);
            var events = getControlChangeEvents(track);
            var controllers = events.stream()
                    .map(e -> ((ShortMessage) e.getMessage()).getData1())
                    .toList();
            assertThat(controllers).containsExactly(101, 100, 6, 38);
        }

        @Test
        void testCorrectControllerValues() throws InvalidMidiDataException {
            createRpnMessages(track, 0, 0, 7);
            var events = getControlChangeEvents(track);
            var values = events.stream()
                    .map(e -> ((ShortMessage) e.getMessage()).getData2())
                    .toList();
            assertThat(values).containsExactly(0, 0, 7, 0);
        }

        @Test
        void testEventsAtCorrectTick() throws InvalidMidiDataException {
            createRpnMessages(track, 50, 0, 12);
            var events = getControlChangeEvents(track);
            assertThat(events).allSatisfy(e -> assertThat(e.getTick()).isEqualTo(50));
        }
    }

    @Nested
    class CreateRpnMessagesIfNeeded {

        private Track track;
        private GlissandoMidiHelper helper;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
            helper = new GlissandoMidiHelper();
        }

        @Test
        void testEmitsOnFirstCall() throws InvalidMidiDataException {
            helper.createRpnMessagesIfNeeded(track, 0, 0, 12);
            assertThat(getControlChangeEvents(track)).hasSize(4);
        }

        @Test
        void testSkipsWhenSensitivityUnchanged() throws InvalidMidiDataException {
            helper.createRpnMessagesIfNeeded(track, 0, 0, 12);
            helper.createRpnMessagesIfNeeded(track, 100, 0, 12);
            // Still only 4 CC events from the first call
            assertThat(getControlChangeEvents(track)).hasSize(4);
        }

        @Test
        void testEmitsWhenSensitivityChanges() throws InvalidMidiDataException {
            helper.createRpnMessagesIfNeeded(track, 0, 0, 12);
            helper.createRpnMessagesIfNeeded(track, 100, 0, 7);
            // 4 from first call + 4 from second call
            assertThat(getControlChangeEvents(track)).hasSize(8);
        }

        @Test
        void testResetCausesReEmit() throws InvalidMidiDataException {
            helper.createRpnMessagesIfNeeded(track, 0, 0, 12);
            helper.resetSensitivity();
            helper.createRpnMessagesIfNeeded(track, 100, 0, 12);
            // 4 + 4 because reset forced re-emit
            assertThat(getControlChangeEvents(track)).hasSize(8);
        }
    }

    @Nested
    class CalculateSensitivity {

        @Test
        void testDownwardInterval() {
            assertThat(calculateSensitivity(72, 60)).isEqualTo(12);
        }

        @Test
        void testMinimumSensitivityIsOne() {
            assertThat(calculateSensitivity(60, 60)).isEqualTo(1);
        }

        @Test
        void testSmallInterval() {
            assertThat(calculateSensitivity(60, 62)).isEqualTo(2);
        }

        @Test
        void testUpwardOctave() {
            assertThat(calculateSensitivity(60, 72)).isEqualTo(12);
        }
    }

    @Nested
    class CalculateSlideTicks {

        @Test
        void testRoundsCorrectly() {
            // 100 * 1/3 = 33.33 -> rounds to 33
            assertThat(calculateSlideTicks(100)).isEqualTo(33);
        }

        @Test
        void testStandardDuration() {
            // 120 * 1/3 = 40
            assertThat(calculateSlideTicks(120)).isEqualTo(40);
        }

        @Test
        void testZeroDuration() {
            assertThat(calculateSlideTicks(0)).isEqualTo(0);
        }
    }

    @Nested
    class CalculateSustainTicks {

        @Test
        void testComplementsSlide() {
            var duration = 120;
            assertThat(calculateSustainTicks(duration) + calculateSlideTicks(duration))
                    .isEqualTo(duration);
        }

        @Test
        void testStandardDuration() {
            // 120 - 40 = 80
            assertThat(calculateSustainTicks(120)).isEqualTo(80);
        }

        @Test
        void testZeroDuration() {
            assertThat(calculateSustainTicks(0)).isEqualTo(0);
        }
    }

    @Nested
    class ResolveTargetPitch {

        @Test
        void testConnectedReturnsNextNotePitch() {
            assertThat(resolveTargetPitch(60, Glissando.Type.CONNECTED, 67))
                    .isEqualTo(67);
        }

        @Test
        void testSlideOutReturnsSourceMinusFour() {
            assertThat(resolveTargetPitch(60, Glissando.Type.SLIDE_OUT, 67))
                    .isEqualTo(56);
        }

        @Test
        void testSlideOutIgnoresNextNotePitch() {
            assertThat(resolveTargetPitch(72, Glissando.Type.SLIDE_OUT, 80))
                    .isEqualTo(68);
        }
    }

    // -- Test helpers --

    private static List<MidiEvent> getControlChangeEvents(Track track) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.CONTROL_CHANGE) {
                events.add(event);
            }
        }

        return events;
    }

    private static List<MidiEvent> getPitchBendEvents(Track track) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.PITCH_BEND) {
                events.add(event);
            }
        }

        return events;
    }

    private static int getBendValue(MidiEvent event) {
        var sm = (ShortMessage) event.getMessage();
        return sm.getData1() | (sm.getData2() << 7);
    }
}
