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

import songscribe.music.StaffElement.Glissando;

/**
 * Calculation and MIDI message generation methods for glissando playback.
 * <p>
 * Phase 1 methods (pure calculations) are static with no MIDI dependencies.
 * Phase 2 methods generate MIDI events on a {@link Track}.
 */
public final class GlissandoMidiHelper {

    /** Fraction of note duration spent sustaining at the original pitch. */
    public static final double SUSTAIN_RATIO = 2.0 / 3.0;

    /** Fraction of note duration spent sliding to the target pitch. */
    public static final double SLIDE_RATIO = 1.0 / 3.0;

    /** Tick interval between successive pitch bend messages during a slide. */
    public static final int BEND_STEP_TICKS = 2;

    /** MIDI pitch bend center value (no bend). */
    public static final int PITCH_BEND_CENTER = 8192;

    /** MIDI pitch bend maximum value. */
    public static final int PITCH_BEND_MAX = 16383;

    /** Exponent for the quadratic ease-in curve. */
    public static final double CURVE_EXPONENT = 2.0;

    /** Fixed slide duration for grace note glissandos (16th note at 96 PPQ). */
    public static final int GRACE_SLIDE_TICKS = 12;

    /** Starting expression/velocity ratio for grace note slide-in glissandos. */
    public static final double GRACE_SLIDE_IN_START_RATIO = 0.25;

    /** MIDI CC number for Expression (per-note volume shaping). */
    private static final int EXPRESSION_CC = 11;

    /** Maximum Expression CC value. */
    private static final int EXPRESSION_MAX = 127;

    /** Number of semitones to slide down for SLIDE_OUT glissandos. */
    public static final int SLIDE_OUT_SEMITONES = 4;

    /** Current pitch bend sensitivity in semitones, -1 means unset. */
    private int currentSensitivity = -1;

    /** Pending grace note pitch for slide-in glissando, -1 means none. */
    private int pendingGracePitch = -1;

    /** Whether pitch bend and/or expression need resetting before the next note. */
    private boolean needsPitchBendReset;
    private boolean needsExpressionReset;

    public GlissandoMidiHelper() {
    }

    /**
     * Returns the target MIDI pitch based on the glissando type.
     *
     * @param sourcePitch   the MIDI pitch of the note with the glissando
     * @param type          the glissando type
     * @param nextNotePitch the MIDI pitch of the next note (used only for CONNECTED)
     * @return the target pitch to slide toward
     */
    public static int resolveTargetPitch(int sourcePitch, Glissando.Type type, int nextNotePitch) {
        return switch (type) {
            case CONNECTED -> nextNotePitch;
            case SLIDE_OUT -> sourcePitch - SLIDE_OUT_SEMITONES;
        };
    }

    /**
     * Returns the pitch bend sensitivity (in semitones) needed to cover the interval
     * between the source and target pitches.
     *
     * @param sourcePitch the starting MIDI pitch
     * @param targetPitch the ending MIDI pitch
     * @return the semitone range needed for RPN 0 (always >= 1)
     */
    public static int calculateSensitivity(int sourcePitch, int targetPitch) {
        return Math.max(1, Math.abs(targetPitch - sourcePitch));
    }

    /**
     * Calculates the MIDI pitch bend value for a given position along the slide.
     * When {@code linear} is false, uses a quadratic ease-in curve (t^2) so the slide
     * starts slow and accelerates. When {@code linear} is true, the bend is proportional to t.
     *
     * @param t           normalized position along the slide (0.0 = start, 1.0 = end)
     * @param sourcePitch the starting MIDI pitch
     * @param targetPitch the ending MIDI pitch
     * @param sensitivity the pitch bend sensitivity in semitones (from RPN 0)
     * @param linear      if true, use linear interpolation; if false, use quadratic ease-in
     * @return the MIDI pitch bend value (0-16383), clamped to valid range
     */
    public static int calculateBendValue(double t, int sourcePitch, int targetPitch, int sensitivity,
            boolean linear) {
        var effectiveT = linear ? t : Math.pow(t, CURVE_EXPONENT);
        var semitoneOffset = effectiveT * (targetPitch - sourcePitch);
        var bendFraction = semitoneOffset / sensitivity;
        var bendValue = (int) Math.round(PITCH_BEND_CENTER + bendFraction * PITCH_BEND_CENTER);
        return Math.clamp(bendValue, 0, PITCH_BEND_MAX);
    }

    /**
     * Returns the tick count for the slide portion of a note.
     *
     * @param noteDuration the total note duration in ticks
     * @return the number of ticks allocated to the slide
     */
    public static int calculateSlideTicks(int noteDuration) {
        return (int) Math.round(noteDuration * SLIDE_RATIO);
    }

    /**
     * Returns the tick count for the sustain portion of a note.
     *
     * @param noteDuration the total note duration in ticks
     * @return the number of ticks allocated to sustaining at the original pitch
     */
    public static int calculateSustainTicks(int noteDuration) {
        return noteDuration - calculateSlideTicks(noteDuration);
    }

    /**
     * Resets the tracked pitch bend sensitivity so that the next call to
     * {@link #createRpnMessagesIfNeeded} will always emit RPN messages.
     */
    public void resetSensitivity() {
        currentSensitivity = -1;
    }

    /**
     * Marks that pitch bend and/or expression need resetting before the next note-on.
     */
    public void setNeedsPitchBendReset() {
        needsPitchBendReset = true;
    }

    public void setNeedsExpressionReset() {
        needsExpressionReset = true;
    }

    /**
     * Emits any pending pitch bend and expression resets, then clears the flags.
     * Call this before each note-on so the previous glissando's state doesn't
     * bleed into the next note.
     *
     * @param track   the MIDI track to add events to
     * @param tick    the tick position for the reset events
     * @param channel the MIDI channel
     * @throws InvalidMidiDataException if MIDI message creation fails
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

    /**
     * Stores the pitch of a grace note that has a connected glissando.
     * The next regular note will use this to produce a slide-in effect.
     *
     * @param pitch the MIDI pitch of the grace note
     */
    public void setPendingGracePitch(int pitch) {
        pendingGracePitch = pitch;
    }

    /**
     * Returns whether a grace note pitch is pending for a slide-in glissando.
     */
    public boolean hasPendingGracePitch() {
        return pendingGracePitch >= 0;
    }

    /**
     * Returns and clears the pending grace note pitch.
     *
     * @return the pending grace pitch, or -1 if none
     */
    public int consumePendingGracePitch() {
        var pitch = pendingGracePitch;
        pendingGracePitch = -1;
        return pitch;
    }

    /**
     * Calculates the MIDI pitch bend value for a slide-in (grace note glissando).
     * The curve starts at the grace pitch (full offset) and eases toward the note's
     * actual pitch (center). Uses the same quadratic curve as regular glissandos.
     *
     * @param t           normalized position along the slide (0.0 = start, 1.0 = end)
     * @param gracePitch  the starting MIDI pitch (from the grace note)
     * @param notePitch   the target MIDI pitch (the note's actual pitch)
     * @param sensitivity the pitch bend sensitivity in semitones (from RPN 0)
     * @return the MIDI pitch bend value (0-16383), clamped to valid range
     */
    public static int calculateSlideInBendValue(double t, int gracePitch, int notePitch, int sensitivity) {
        var curvedT = Math.pow(t, CURVE_EXPONENT);
        var semitoneOffset = (1.0 - curvedT) * (gracePitch - notePitch);
        var bendFraction = semitoneOffset / sensitivity;
        var bendValue = (int) Math.round(PITCH_BEND_CENTER + bendFraction * PITCH_BEND_CENTER);
        return Math.clamp(bendValue, 0, PITCH_BEND_MAX);
    }

    /**
     * Generates pitch bend events for a slide-in glissando (grace note to regular note).
     * The bend starts at the grace pitch offset and eases to center (no bend) over the
     * slide duration.
     *
     * @param track              the MIDI track to add events to
     * @param slideStartTick     the tick at which the slide begins
     * @param slideDurationTicks the total number of ticks for the slide
     * @param channel            the MIDI channel
     * @param gracePitch         the starting MIDI pitch (from the grace note)
     * @param notePitch          the target MIDI pitch (the note's actual pitch)
     * @param sensitivity        the pitch bend sensitivity in semitones (from RPN 0)
     * @throws InvalidMidiDataException if MIDI message creation fails
     */
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

    /**
     * Emits RPN 0 (pitch bend sensitivity) messages on the track only if the
     * requested sensitivity differs from the currently tracked value.
     *
     * @param track      the MIDI track to add events to
     * @param tick       the tick position for the events
     * @param channel    the MIDI channel
     * @param semitones  the desired pitch bend sensitivity in semitones
     * @throws InvalidMidiDataException if MIDI message creation fails
     */
    public void createRpnMessagesIfNeeded(Track track, int tick, int channel, int semitones)
            throws InvalidMidiDataException {
        if (semitones == currentSensitivity) {
            return;
        }

        currentSensitivity = semitones;
        createRpnMessages(track, tick, channel, semitones);
    }

    /**
     * Emits RPN 0 (pitch bend sensitivity) messages on the track:
     * CC 101=0, CC 100=0, CC 6=semitones, CC 38=0.
     *
     * @param track      the MIDI track to add events to
     * @param tick       the tick position for the events
     * @param channel    the MIDI channel
     * @param semitones  the desired pitch bend sensitivity in semitones
     * @throws InvalidMidiDataException if MIDI message creation fails
     */
    public static void createRpnMessages(Track track, int tick, int channel, int semitones)
            throws InvalidMidiDataException {
        addControlChange(track, tick, channel, 101, 0);
        addControlChange(track, tick, channel, 100, 0);
        addControlChange(track, tick, channel, 6, semitones);
        addControlChange(track, tick, channel, 38, 0);
    }

    /**
     * Generates pitch bend events along the slide curve, one every {@link #BEND_STEP_TICKS} ticks.
     *
     * @param track              the MIDI track to add events to
     * @param slideStartTick     the tick at which the slide begins
     * @param slideDurationTicks the total number of ticks for the slide
     * @param channel            the MIDI channel
     * @param sourcePitch        the starting MIDI pitch
     * @param targetPitch        the ending MIDI pitch
     * @param sensitivity        the pitch bend sensitivity in semitones (from RPN 0)
     * @param linear             if true, use linear interpolation; if false, use quadratic ease-in
     * @throws InvalidMidiDataException if MIDI message creation fails
     */
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

    /**
     * Emits a pitch bend reset (center value) message on the track.
     *
     * @param track   the MIDI track to add the event to
     * @param tick    the tick position for the event
     * @param channel the MIDI channel
     * @throws InvalidMidiDataException if MIDI message creation fails
     */
    public static void createPitchBendReset(Track track, int tick, int channel)
            throws InvalidMidiDataException {
        addPitchBend(track, tick, channel, PITCH_BEND_CENTER);
    }

    /**
     * Generates Expression CC messages that ramp from {@link #GRACE_SLIDE_IN_START_RATIO}
     * to max along the same quadratic ease-in curve as the pitch bend. Used for grace
     * note slide-in glissandos so the volume swells with the pitch.
     *
     * @param track              the MIDI track to add events to
     * @param slideStartTick     the tick at which the slide begins
     * @param slideDurationTicks the total number of ticks for the slide
     * @param channel            the MIDI channel
     * @throws InvalidMidiDataException if MIDI message creation fails
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
            var curvedT = Math.pow(t, CURVE_EXPONENT);
            var expression = (int) Math.round(startExpression + curvedT * expressionRange);
            addControlChange(track, slideStartTick + elapsed, channel, EXPRESSION_CC,
                    Math.clamp(expression, 0, EXPRESSION_MAX));
        }
    }

    /**
     * Generates Expression CC messages that fade from max to 0 along
     * the same quadratic ease-in curve as the pitch bend. Used for
     * slide-out glissandos so the volume fades with the pitch.
     *
     * @param track              the MIDI track to add events to
     * @param slideStartTick     the tick at which the slide begins
     * @param slideDurationTicks the total number of ticks for the slide
     * @param channel            the MIDI channel
     * @throws InvalidMidiDataException if MIDI message creation fails
     */
    public static void createSlideOutExpressionMessages(Track track, int slideStartTick,
            int slideDurationTicks, int channel) throws InvalidMidiDataException {
        if (slideDurationTicks <= 0) {
            return;
        }

        for (var elapsed = 0; elapsed <= slideDurationTicks; elapsed += BEND_STEP_TICKS) {
            var t = (double) elapsed / slideDurationTicks;
            var curvedT = Math.pow(t, CURVE_EXPONENT);
            var expression = (int) Math.round((1.0 - curvedT) * EXPRESSION_MAX);
            addControlChange(track, slideStartTick + elapsed, channel, EXPRESSION_CC,
                    Math.clamp(expression, 0, EXPRESSION_MAX));
        }
    }

    /**
     * Resets Expression CC to maximum so subsequent notes are unaffected.
     *
     * @param track   the MIDI track to add the event to
     * @param tick    the tick position for the event
     * @param channel the MIDI channel
     * @throws InvalidMidiDataException if MIDI message creation fails
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
