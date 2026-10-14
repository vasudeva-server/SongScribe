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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jspecify.annotations.Nullable;

/**
 * Calculation and MIDI message generation methods for slide playback.
 */
public final class SlideMidiHelper {

    public static final double SUSTAIN_RATIO = 0.5;

    public static final double SLIDE_RATIO = 0.5;

    private static final int BEND_STEP_TICKS = 2;

    public static final int PITCH_BEND_CENTER = 8192;

    public static final int PITCH_BEND_MAX = 16383;

    public static final double GRACE_NOTE_CURVE_EXPONENT = 1.0;

    public static final double CONNECTED_CURVE_EXPONENT = 1.0;

    public static final double FALL_CURVE_EXPONENT = 2.0;

    private static final int SIXTEENTH_TRIPLETS_PER_QUARTER = 6;

    public static final int GRACE_SLIDE_TICKS = MidiSequenceBuilder.PPQ / SIXTEENTH_TRIPLETS_PER_QUARTER;

    public static final double GRACE_SLIDE_IN_START_RATIO = 0.25;

    static final int EXPRESSION_CC = 11;

    static final int EXPRESSION_MAX = 127;

    public static final int FALL_SEMITONES = 4;

    /** Null means unset. */
    private @Nullable Integer currentSensitivity = null;

    /** Null means none. */
    private @Nullable Integer pendingGracePitch = null;

    private boolean needsPitchBendReset;
    private boolean needsExpressionReset;

    public static int resolveTargetPitch(int sourcePitch) {
        return sourcePitch - FALL_SEMITONES;
    }

    public static int calculateSensitivity(int sourcePitch, int targetPitch) {
        return Math.max(1, Math.abs(targetPitch - sourcePitch));
    }

    /**
     * @param t normalized position along the slide (0.0 = start, 1.0 = end)
     */
    public static int calculateBendValue(double t, int sourcePitch, int targetPitch, int sensitivity,
            boolean linear) {
        var effectiveT = Math.pow(t, linear ? CONNECTED_CURVE_EXPONENT : FALL_CURVE_EXPONENT);
        var semitoneOffset = effectiveT * (targetPitch - sourcePitch);
        var bendFraction = semitoneOffset / sensitivity;
        var bendValue = (int) Math.round(PITCH_BEND_CENTER + bendFraction * PITCH_BEND_CENTER);
        return Math.clamp(bendValue, 0, PITCH_BEND_MAX);
    }

    public static int calculateSlideTicks(int noteDuration) {
        return (int) Math.round(noteDuration * SLIDE_RATIO);
    }

    public static int calculateSustainTicks(int noteDuration) {
        return noteDuration - calculateSlideTicks(noteDuration);
    }

    public void resetSensitivity() {
        currentSensitivity = null;
    }

    public void setNeedsPitchBendReset() {
        needsPitchBendReset = true;
    }

    public void setNeedsExpressionReset() {
        needsExpressionReset = true;
    }

    /**
     * Call this before each note-on so the previous slide's state doesn't bleed into the next
     * note.
     */
    public void createPendingResets(Track track, int tick, int channel)
            throws InvalidMidiDataException {
        if (needsPitchBendReset) {
            createPitchBendReset(track, tick, channel);
            needsPitchBendReset = false;
        }

        if (needsExpressionReset) {
            createExpressionReset(track, tick, channel);
            needsExpressionReset = false;
        }
    }

    public void setPendingGracePitch(int pitch) {
        pendingGracePitch = pitch;
    }

    public boolean hasPendingGracePitch() {
        return pendingGracePitch != null;
    }

    /**
     * @return {@code null} if none
     */
    public @Nullable Integer consumePendingGracePitch() {
        var pitch = pendingGracePitch;
        pendingGracePitch = null;
        return pitch;
    }

    /**
     * @param t normalized position along the slide (0.0 = start, 1.0 = end)
     */
    public static int calculateSlideInBendValue(double t, int gracePitch, int notePitch, int sensitivity) {
        var curvedT = Math.pow(t, GRACE_NOTE_CURVE_EXPONENT);
        var semitoneOffset = (1.0 - curvedT) * (gracePitch - notePitch);
        var bendFraction = semitoneOffset / sensitivity;
        var bendValue = (int) Math.round(PITCH_BEND_CENTER + bendFraction * PITCH_BEND_CENTER);
        return Math.clamp(bendValue, 0, PITCH_BEND_MAX);
    }

    public static void createSlideInPitchBendMessages(Track track, int slideStartTick, int slideDurationTicks,
            int channel, int gracePitch, int notePitch, int sensitivity) throws InvalidMidiDataException {
        if (slideDurationTicks <= 0) {
            return;
        }

        for (var elapsed = 0; elapsed <= slideDurationTicks; elapsed += BEND_STEP_TICKS) {
            var t = (double) elapsed / slideDurationTicks;
            var bendValue = calculateSlideInBendValue(t, gracePitch, notePitch, sensitivity);
            addPitchBend(track, slideStartTick + elapsed, channel, bendValue);
        }
    }

    public void createRpnMessagesIfNeeded(Track track, int tick, int channel, int semitones)
            throws InvalidMidiDataException {
        if (currentSensitivity != null && semitones == currentSensitivity) {
            return;
        }

        currentSensitivity = semitones;
        createRpnMessages(track, tick, channel, semitones);
    }

    public static void createRpnMessages(Track track, int tick, int channel, int semitones)
            throws InvalidMidiDataException {
        addControlChange(track, tick, channel, 101, 0);
        addControlChange(track, tick, channel, 100, 0);
        addControlChange(track, tick, channel, 6, semitones);
        addControlChange(track, tick, channel, 38, 0);
    }

    public static void createPitchBendMessages(Track track, int slideStartTick, int slideDurationTicks,
            int channel, int sourcePitch, int targetPitch, int sensitivity, boolean linear)
            throws InvalidMidiDataException {
        if (slideDurationTicks <= 0) {
            return;
        }

        for (var elapsed = 0; elapsed <= slideDurationTicks; elapsed += BEND_STEP_TICKS) {
            var t = (double) elapsed / slideDurationTicks;
            var bendValue = calculateBendValue(t, sourcePitch, targetPitch, sensitivity, linear);
            addPitchBend(track, slideStartTick + elapsed, channel, bendValue);
        }
    }

    public static void createPitchBendReset(Track track, int tick, int channel)
            throws InvalidMidiDataException {
        addPitchBend(track, tick, channel, PITCH_BEND_CENTER);
    }

    /**
     * Follows the pitch bend's curve, so the volume swells with the pitch.
     */
    public static void createSlideInExpressionMessages(Track track, int slideStartTick,
            int slideDurationTicks, int channel) throws InvalidMidiDataException {
        if (slideDurationTicks <= 0) {
            return;
        }

        var startExpression = GRACE_SLIDE_IN_START_RATIO * EXPRESSION_MAX;
        var expressionRange = EXPRESSION_MAX - startExpression;

        for (var elapsed = 0; elapsed <= slideDurationTicks; elapsed += BEND_STEP_TICKS) {
            var t = (double) elapsed / slideDurationTicks;
            var curvedT = Math.pow(t, GRACE_NOTE_CURVE_EXPONENT);
            var expression = (int) Math.round(startExpression + curvedT * expressionRange);
            addControlChange(track, slideStartTick + elapsed, channel, EXPRESSION_CC,
                    Math.clamp(expression, 0, EXPRESSION_MAX));
        }
    }

    /**
     * Follows the pitch bend's curve, so the volume fades with the pitch.
     */
    public static void createFallExpressionMessages(Track track, int slideStartTick,
            int slideDurationTicks, int channel) throws InvalidMidiDataException {
        if (slideDurationTicks <= 0) {
            return;
        }

        for (var elapsed = 0; elapsed <= slideDurationTicks; elapsed += BEND_STEP_TICKS) {
            var t = (double) elapsed / slideDurationTicks;
            var curvedT = Math.pow(t, FALL_CURVE_EXPONENT);
            var expression = (int) Math.round((1.0 - curvedT) * EXPRESSION_MAX);
            addControlChange(track, slideStartTick + elapsed, channel, EXPRESSION_CC,
                    Math.clamp(expression, 0, EXPRESSION_MAX));
        }
    }

    /**
     * The reset keeps subsequent notes unaffected.
     */
    public static void createExpressionReset(Track track, int tick, int channel)
            throws InvalidMidiDataException {
        addControlChange(track, tick, channel, EXPRESSION_CC, EXPRESSION_MAX);
    }

    private static void addControlChange(Track track, int tick, int channel, int controller, int value)
            throws InvalidMidiDataException {
        var msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value);
        track.add(new MidiEvent(msg, tick));
    }

    private static void addPitchBend(Track track, int tick, int channel, int bendValue)
            throws InvalidMidiDataException {
        var msg = new ShortMessage();
        msg.setMessage(ShortMessage.PITCH_BEND, channel, bendValue & 0x7F, (bendValue >> 7) & 0x7F);
        track.add(new MidiEvent(msg, tick));
    }
}
