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

import module java.desktop;

import java.util.ArrayList;
import java.util.List;

import songscribe.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.midi.SlideMidiHelper.EXPRESSION_CC;
import static songscribe.midi.SlideMidiHelper.EXPRESSION_MAX;
import static songscribe.midi.SlideMidiHelper.GRACE_SLIDE_IN_START_RATIO;
import static songscribe.midi.SlideMidiHelper.PITCH_BEND_CENTER;
import static songscribe.midi.SlideMidiHelper.PITCH_BEND_MAX;
import static songscribe.midi.SlideMidiHelper.calculateBendValue;
import static songscribe.midi.SlideMidiHelper.calculateSensitivity;
import static songscribe.midi.SlideMidiHelper.calculateSlideInBendValue;
import static songscribe.midi.SlideMidiHelper.calculateSlideTicks;
import static songscribe.midi.SlideMidiHelper.calculateSustainTicks;
import static songscribe.midi.SlideMidiHelper.createPitchBendMessages;
import static songscribe.midi.SlideMidiHelper.createPitchBendReset;
import static songscribe.midi.SlideMidiHelper.createRpnMessages;
import static songscribe.midi.SlideMidiHelper.createExpressionReset;
import static songscribe.midi.SlideMidiHelper.createSlideInExpressionMessages;
import static songscribe.midi.SlideMidiHelper.createSlideInPitchBendMessages;
import static songscribe.midi.SlideMidiHelper.createFallExpressionMessages;
import static songscribe.midi.SlideMidiHelper.resolveTargetPitch;

class SlideMidiHelperTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateBendValue {

        @Test
        void testAtEndReachesFull() {
            // 7 semitones up, sensitivity = 7 => full bend at t=1.0
            var bend = calculateBendValue(1.0, 60, 67, 7, true);
            assertThat(bend).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testAtStartReturnsCenter() {
            var bend = calculateBendValue(0.0, 60, 72, 12, true);
            assertThat(bend).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testClampedToMaximum() {
            // Sensitivity smaller than interval would overshoot — should clamp
            var bend = calculateBendValue(1.0, 60, 72, 6, true);
            assertThat(bend).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testClampedToMinimum() {
            // Slide down with sensitivity smaller than interval
            var bend = calculateBendValue(1.0, 72, 60, 6, true);
            assertThat(bend).isEqualTo(0);
        }

        @Test
        void testDownwardSlide() {
            // Slide down: target < source => bend below center
            var bend = calculateBendValue(1.0, 67, 60, 7, true);
            assertThat(bend).isEqualTo(0);
        }

        @Test
        void testQuadraticCurveAt50Percent() {
            // t=0.5, curve = 0.25, interval = 12, sensitivity = 12
            // bend = 8192 + 0.25 * 8192 = 10240
            var bend = calculateBendValue(0.5, 60, 72, 12, false);
            assertThat(bend).isEqualTo(10240);
        }

        @Test
        void testQuadraticCurveAt75Percent() {
            // t=0.75, curve = 0.5625, interval = 12, sensitivity = 12
            // bend = 8192 + 0.5625 * 8192 = 12800
            var bend = calculateBendValue(0.75, 60, 72, 12, false);
            assertThat(bend).isEqualTo(12800);
        }

        @Test
        void testQuarterPosition() {
            // t=0.25, curve = 0.0625, interval = 12, sensitivity = 12
            // bend = 8192 + 0.0625 * 8192 = 8704
            var bend = calculateBendValue(0.25, 60, 72, 12, false);
            assertThat(bend).isEqualTo(8704);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
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
            // slideDuration=12, step=2 => ticks 0,2,4,6,8,10,12 => 7 events
            createPitchBendMessages(track, 100, 12, 0, 60, 72, 12, false);
            var events = getPitchBendEvents(track);
            assertThat(events).hasSize(7);
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
            var bendValues = events.stream().map(SlideMidiHelperTest::getBendValue).toList();

            // Each successive bend value should be >= the previous (monotonically increasing for upward slide)
            for (var i = 1; i < bendValues.size(); i++) {
                assertThat(bendValues.get(i)).isGreaterThanOrEqualTo(bendValues.get(i - 1));
            }
        }

        @Test
        void testUpwardSlideExactBendValuesAtKeyTicks() throws InvalidMidiDataException {
            // linear=true, source=60, target=72, sensitivity=12, duration=12
            // t=0 (elapsed=0): bend = CENTER
            // t=0.5 (elapsed=6): effectiveT=0.5, offset=6, fraction=0.5, bend = 8192 + 4096 = 12288
            // t=1.0 (elapsed=12): effectiveT=1.0, offset=12, fraction=1.0, bend clamped to PITCH_BEND_MAX
            createPitchBendMessages(track, 0, 12, 0, 60, 72, 12, true);
            var events = getPitchBendEvents(track);
            // elapsed=0,2,4,6,8,10,12 => 7 events; indices 0,3,6 correspond to t=0, t=0.5, t=1.0
            assertThat(getBendValue(events.get(0))).isEqualTo(PITCH_BEND_CENTER);
            assertThat(getBendValue(events.get(3))).isEqualTo(12288);
            assertThat(getBendValue(events.get(6))).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testDownwardSlideExactBendValuesAtKeyTicks() throws InvalidMidiDataException {
            // linear=true, source=72, target=60, sensitivity=12, duration=12
            // t=0: bend = CENTER
            // t=0.5: effectiveT=0.5, offset=-6, fraction=-0.5, bend = 8192 - 4096 = 4096
            // t=1.0: effectiveT=1.0, offset=-12, fraction=-1.0, bend = 8192 - 8192 = 0
            createPitchBendMessages(track, 0, 12, 0, 72, 60, 12, true);
            var events = getPitchBendEvents(track);
            // elapsed=0,2,4,6,8,10,12 => 7 events; indices 0,3,6 correspond to t=0, t=0.5, t=1.0
            assertThat(getBendValue(events.get(0))).isEqualTo(PITCH_BEND_CENTER);
            assertThat(getBendValue(events.get(3))).isEqualTo(4096);
            assertThat(getBendValue(events.get(6))).isEqualTo(0);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class CreateRpnMessagesIfNeeded {

        private Track track;
        private SlideMidiHelper helper;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
            helper = new SlideMidiHelper();
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

    @SuppressWarnings("PackageVisibleInnerClass")
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

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateSlideTicks {

        @Test
        void testEvenDuration() {
            // 100 * 0.5 = 50
            assertThat(calculateSlideTicks(100)).isEqualTo(50);
        }

        @Test
        void testStandardDuration() {
            // 120 * 0.5 = 60
            assertThat(calculateSlideTicks(120)).isEqualTo(60);
        }

        @Test
        void testZeroDuration() {
            assertThat(calculateSlideTicks(0)).isEqualTo(0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
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
            // 120 - 60 = 60
            assertThat(calculateSustainTicks(120)).isEqualTo(60);
        }

        @Test
        void testZeroDuration() {
            assertThat(calculateSustainTicks(0)).isEqualTo(0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ResolveTargetPitch {

        @Test
        void testFallReturnsSourceMinusFallSemitones() {
            assertThat(resolveTargetPitch(60))
                    .isEqualTo(60 - SlideMidiHelper.FALL_SEMITONES);
        }

        @Test
        void testFallReturnsSourceMinusFallSemitonesHigherPitch() {
            assertThat(resolveTargetPitch(72))
                    .isEqualTo(72 - SlideMidiHelper.FALL_SEMITONES);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class CreatePendingResets {

        private Track track;
        private SlideMidiHelper helper;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
            helper = new SlideMidiHelper();
        }

        @Test
        void testEmitsPitchBendResetWhenFlagSet() throws InvalidMidiDataException {
            helper.setNeedsPitchBendReset();
            helper.createPendingResets(track, 50, 0);
            var events = getPitchBendEvents(track);
            assertThat(events).hasSize(1);
            assertThat(getBendValue(events.getFirst())).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testEmitsExpressionResetWhenFlagSet() throws InvalidMidiDataException {
            helper.setNeedsExpressionReset();
            helper.createPendingResets(track, 50, 0);
            var ccEvents = getControlChangeEvents(track);
            assertThat(ccEvents).hasSize(1);
            assertThat(((ShortMessage) ccEvents.getFirst().getMessage()).getData1()).isEqualTo(EXPRESSION_CC);
            assertThat(((ShortMessage) ccEvents.getFirst().getMessage()).getData2()).isEqualTo(EXPRESSION_MAX);
        }

        @Test
        void testEmitsBothResetsWhenBothFlagsSet() throws InvalidMidiDataException {
            helper.setNeedsPitchBendReset();
            helper.setNeedsExpressionReset();
            helper.createPendingResets(track, 50, 0);
            assertThat(getPitchBendEvents(track)).hasSize(1);
            assertThat(getControlChangeEvents(track)).hasSize(1);
        }

        @Test
        void testSecondCallProducesNoEventsAfterFlagsCleared() throws InvalidMidiDataException {
            helper.setNeedsPitchBendReset();
            helper.setNeedsExpressionReset();
            helper.createPendingResets(track, 50, 0);
            // Flags are cleared; second call should emit nothing more
            helper.createPendingResets(track, 100, 0);
            assertThat(getPitchBendEvents(track)).hasSize(1);
            assertThat(getControlChangeEvents(track)).hasSize(1);
        }

        @Test
        void testEmitsNothingWhenNoFlagsSet() throws InvalidMidiDataException {
            helper.createPendingResets(track, 50, 0);
            assertThat(getPitchBendEvents(track)).isEmpty();
            assertThat(getControlChangeEvents(track)).isEmpty();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateSlideInBendValue {

        @Test
        void testAtStartReturnsFullOffset() {
            // t=0.0, GRACE_NOTE_CURVE_EXPONENT=1.0 (linear): curvedT=0, offset=(1-0)*(64-60)=4
            // bendFraction=4/4=1.0, bend = 8192 + 8192 = 16384 => clamped to PITCH_BEND_MAX
            var bend = calculateSlideInBendValue(0.0, 64, 60, 4);
            assertThat(bend).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testAtEndReturnsCenter() {
            // t=1.0: curvedT=1, offset=0 => bend = PITCH_BEND_CENTER
            var bend = calculateSlideInBendValue(1.0, 64, 60, 4);
            assertThat(bend).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testAtMidpointReturnsHalfOffset() {
            // t=0.5, GRACE_NOTE_CURVE_EXPONENT=1.0: curvedT=0.5, offset=0.5*(64-60)=2
            // bendFraction=2/4=0.5, bend = 8192 + 0.5*8192 = 12288
            var bend = calculateSlideInBendValue(0.5, 64, 60, 4);
            assertThat(bend).isEqualTo(12288);
        }

        @Test
        void testDownwardGraceNoteAtStart() {
            // gracePitch < notePitch: t=0.0, offset=(1-0)*(56-60)=-4, fraction=-1.0
            // bend = 8192 - 8192 = 0
            var bend = calculateSlideInBendValue(0.0, 56, 60, 4);
            assertThat(bend).isEqualTo(0);
        }

        @Test
        void testDownwardGraceNoteAtEnd() {
            // t=1.0: offset=0 => center regardless of direction
            var bend = calculateSlideInBendValue(1.0, 56, 60, 4);
            assertThat(bend).isEqualTo(PITCH_BEND_CENTER);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class CreateSlideInPitchBendMessages {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEventCountForSmallSlide() throws InvalidMidiDataException {
            // duration=8, BEND_STEP_TICKS=2: elapsed 0,2,4,6,8 => 5 events
            createSlideInPitchBendMessages(track, 0, 8, 0, 64, 60, 4);
            assertThat(getPitchBendEvents(track)).hasSize(5);
        }

        @Test
        void testFirstBendValueIsFullOffset() throws InvalidMidiDataException {
            // t=0: full offset grace->note => PITCH_BEND_MAX (grace above note)
            createSlideInPitchBendMessages(track, 0, 8, 0, 64, 60, 4);
            var events = getPitchBendEvents(track);
            assertThat(getBendValue(events.getFirst())).isEqualTo(PITCH_BEND_MAX);
        }

        @Test
        void testLastBendValueIsCenter() throws InvalidMidiDataException {
            // t=1.0: eased to center (no bend)
            createSlideInPitchBendMessages(track, 0, 8, 0, 64, 60, 4);
            var events = getPitchBendEvents(track);
            assertThat(getBendValue(events.getLast())).isEqualTo(PITCH_BEND_CENTER);
        }

        @Test
        void testNoEventsForZeroDuration() throws InvalidMidiDataException {
            createSlideInPitchBendMessages(track, 0, 0, 0, 64, 60, 4);
            assertThat(getPitchBendEvents(track)).isEmpty();
        }

        @Test
        void testExpressionRampsFromStartRatioToMax() throws InvalidMidiDataException {
            // startExpression = round(0.25 * 127) = 32; last event = 127
            createSlideInExpressionMessages(track, 0, 8, 0);
            var ccEvents = getControlChangeEvents(track);
            // All events are CC11 (EXPRESSION_CC)
            assertThat(ccEvents).allSatisfy(e ->
                    assertThat(((ShortMessage) e.getMessage()).getData1()).isEqualTo(EXPRESSION_CC));
            var firstValue = ((ShortMessage) ccEvents.getFirst().getMessage()).getData2();
            var lastValue = ((ShortMessage) ccEvents.getLast().getMessage()).getData2();
            // First value ≈ GRACE_SLIDE_IN_START_RATIO * EXPRESSION_MAX
            assertThat(firstValue).isEqualTo((int) Math.round(GRACE_SLIDE_IN_START_RATIO * EXPRESSION_MAX));
            assertThat(lastValue).isEqualTo(EXPRESSION_MAX);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class CreateSlideInExpressionMessages {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEventCount() throws InvalidMidiDataException {
            // duration=8, BEND_STEP_TICKS=2: elapsed 0,2,4,6,8 => 5 events
            createSlideInExpressionMessages(track, 0, 8, 0);
            assertThat(getControlChangeEvents(track)).hasSize(5);
        }

        @Test
        void testFirstEventIsStartRatioValue() throws InvalidMidiDataException {
            // first value = round(GRACE_SLIDE_IN_START_RATIO * EXPRESSION_MAX) = round(0.25 * 127) = 32
            createSlideInExpressionMessages(track, 0, 8, 0);
            var events = getControlChangeEvents(track);
            var firstValue = ((ShortMessage) events.getFirst().getMessage()).getData2();
            assertThat(firstValue).isEqualTo((int) Math.round(GRACE_SLIDE_IN_START_RATIO * EXPRESSION_MAX));
        }

        @Test
        void testLastEventIsMax() throws InvalidMidiDataException {
            createSlideInExpressionMessages(track, 0, 8, 0);
            var events = getControlChangeEvents(track);
            var lastValue = ((ShortMessage) events.getLast().getMessage()).getData2();
            assertThat(lastValue).isEqualTo(EXPRESSION_MAX);
        }

        @Test
        void testAllEventsAreCC11() throws InvalidMidiDataException {
            createSlideInExpressionMessages(track, 0, 8, 0);
            var events = getControlChangeEvents(track);
            assertThat(events).allSatisfy(e ->
                    assertThat(((ShortMessage) e.getMessage()).getData1()).isEqualTo(EXPRESSION_CC));
        }

        @Test
        void testNoEventsForZeroDuration() throws InvalidMidiDataException {
            createSlideInExpressionMessages(track, 0, 0, 0);
            assertThat(getControlChangeEvents(track)).isEmpty();
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class CreateFallExpressionMessages {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEventCount() throws InvalidMidiDataException {
            // duration=8, BEND_STEP_TICKS=2: elapsed 0,2,4,6,8 => 5 events
            createFallExpressionMessages(track, 0, 8, 0);
            assertThat(getControlChangeEvents(track)).hasSize(5);
        }

        @Test
        void testFirstEventIsMax() throws InvalidMidiDataException {
            // elapsed=0, t=0, curvedT=0: expression = round((1-0)*127) = 127
            createFallExpressionMessages(track, 0, 8, 0);
            var events = getControlChangeEvents(track);
            var firstValue = ((ShortMessage) events.getFirst().getMessage()).getData2();
            assertThat(firstValue).isEqualTo(EXPRESSION_MAX);
        }

        @Test
        void testLastEventIsZero() throws InvalidMidiDataException {
            // elapsed=duration, t=1, curvedT=1: expression = round((1-1)*127) = 0
            createFallExpressionMessages(track, 0, 8, 0);
            var events = getControlChangeEvents(track);
            var lastValue = ((ShortMessage) events.getLast().getMessage()).getData2();
            assertThat(lastValue).isEqualTo(0);
        }

        @Test
        void testAllEventsAreCC11() throws InvalidMidiDataException {
            createFallExpressionMessages(track, 0, 8, 0);
            var events = getControlChangeEvents(track);
            assertThat(events).allSatisfy(e ->
                    assertThat(((ShortMessage) e.getMessage()).getData1()).isEqualTo(EXPRESSION_CC));
        }

        @Test
        void testNoEventsForZeroDuration() throws InvalidMidiDataException {
            createFallExpressionMessages(track, 0, 0, 0);
            assertThat(getControlChangeEvents(track)).isEmpty();
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class CreateExpressionReset {

        private Track track;

        @BeforeEach
        void setUp() throws Exception {
            var sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
        }

        @Test
        void testEmitsSingleCC11Event() throws InvalidMidiDataException {
            createExpressionReset(track, 50, 0);
            var events = getControlChangeEvents(track);
            assertThat(events).hasSize(1);
            assertThat(((ShortMessage) events.getFirst().getMessage()).getData1()).isEqualTo(EXPRESSION_CC);
        }

        @Test
        void testEmitsMaxExpressionValue() throws InvalidMidiDataException {
            createExpressionReset(track, 50, 0);
            var events = getControlChangeEvents(track);
            assertThat(((ShortMessage) events.getFirst().getMessage()).getData2()).isEqualTo(EXPRESSION_MAX);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PendingGracePitch {

        private SlideMidiHelper helper;

        @BeforeEach
        void setUp() {
            helper = new SlideMidiHelper();
        }

        @Test
        void testHasNoPendingPitchInitially() {
            assertThat(helper.hasPendingGracePitch()).isFalse();
        }

        @Test
        void testHasPendingPitchAfterSet() {
            helper.setPendingGracePitch(60);
            assertThat(helper.hasPendingGracePitch()).isTrue();
        }

        @Test
        void testConsumeReturnsPitch() {
            helper.setPendingGracePitch(60);
            assertThat(helper.consumePendingGracePitch()).isEqualTo(60);
        }

        @Test
        void testConsumeClears() {
            helper.setPendingGracePitch(60);
            helper.consumePendingGracePitch();
            assertThat(helper.hasPendingGracePitch()).isFalse();
        }

        @Test
        void testConsumeWithoutSetReturnsNegativeOne() {
            assertThat(helper.consumePendingGracePitch()).isEqualTo(-1);
        }
    }

    // -- Test helpers --

    private static List<MidiEvent> getEventsByCommand(Track track, int command) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == command) {
                events.add(event);
            }
        }

        return events;
    }

    private static List<MidiEvent> getControlChangeEvents(Track track) {
        return getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);
    }

    private static List<MidiEvent> getPitchBendEvents(Track track) {
        return getEventsByCommand(track, ShortMessage.PITCH_BEND);
    }

    private static int getBendValue(MidiEvent event) {
        var sm = (ShortMessage) event.getMessage();
        return sm.getData1() | (sm.getData2() << 7);
    }
}
