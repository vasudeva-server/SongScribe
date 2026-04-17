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
package songscribe.midi;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.music.ArticulationType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlaybackController;

public class LineTrackBuilder {

    private static final double GRACE_GLISSANDO_VELOCITY_RATIO = 0.85;

    private final Line line;

    public LineTrackBuilder(Line line) {
        this.line = line;
    }

    /**
     * Returns the duration of an element adjusted for tuplet membership.
     *
     * @param elementIndex Index of the element
     * @param referenceTempo The tempo providing the reference note duration
     * @return Duration in ticks, adjusted for tuplet if applicable
     */
    public int getElementDurationWithTuplet(int elementIndex, Tempo referenceTempo) {
        return Math.round(line.getElement(elementIndex).getDuration() * getTupletFactor(elementIndex, referenceTempo));
    }

    /**
     * Calculates the tuplet scaling factor for an element.
     *
     * @param elementIndex Index of the element
     * @param referenceTempo The tempo providing the reference note duration
     * @return Scaling factor (1.0 if not in a tuplet)
     */
    private float getTupletFactor(int elementIndex, Tempo referenceTempo) {
        var tupletInt = line.getTuplets().findSpan(elementIndex);

        if (tupletInt == null) {
            return 1;
        }

        var tupletDuration = 0f;

        for (var i = tupletInt.getStart(); i <= tupletInt.getEnd(); i++) {
            tupletDuration += line.getElement(i).getDuration();
        }

        tupletDuration /= referenceTempo.getTempoType().getNote().getDuration();
        float newDuration;

        if (tupletDuration >= 1) {
            newDuration = (float) Math.floor(tupletDuration);

            if ((newDuration == tupletDuration) && (newDuration > 1)) {
                newDuration--;
            }
        } else {
            var log2 = Math.log(2);
            newDuration = (float) Math.pow(
                2,
                Math.floor(Math.log(tupletDuration) / log2)
            );
        }

        return newDuration / tupletDuration;
    }

    /**
     * Adds this line's elements to a MIDI track.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the composition (for colorize messages)
     * @param startTicks Starting tick position
     * @param initialTempo Tempo at the start of this line
     * @param settings Playback settings
     * @return Pair of (ending tick position, ending tempo)
     */
    public TrackPosition addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            0, line.elementCount() - 1, (VelocityMap) null);
    }

    /**
     * Adds all of this line's elements to a MIDI track, using a velocity map
     * for dynamic-aware note velocities.
     */
    public TrackPosition addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            0, line.elementCount() - 1, velocityMap);
    }

    /**
     * Adds a range of this line's elements to a MIDI track.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the composition (for colorize messages)
     * @param startTicks Starting tick position
     * @param initialTempo Tempo at the start of this range
     * @param settings Playback settings
     * @param startElement Index of the first element to add
     * @param endElement Index of the last element to add
     * @return Pair of (ending tick position, ending tempo)
     */
    public TrackPosition addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        int startElement,
        int endElement
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            startElement, endElement, (VelocityMap) null);
    }

    /**
     * Adds a range of this line's elements to a MIDI track, using a velocity map
     * for dynamic-aware note velocities.
     */
    public TrackPosition addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        int startElement,
        int endElement,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var glissandoHelper = new GlissandoMidiHelper();
        var result = addToTrack(
            track, lineIndex, startTicks, initialTempo, settings,
            startElement, endElement, glissandoHelper, velocityMap
        );

        // Flush pending pitch bend/expression resets so the state
        // is clean when the sequence loops or the next line starts.
        glissandoHelper.createPendingResets(track, result.ticks(), 0);

        return result;
    }

    /**
     * Adds a range of this line's elements to a MIDI track using an externally
     * managed {@link GlissandoMidiHelper}. This overload is used by the repeat
     * path, which processes notes one at a time but needs glissando state
     * (e.g. pending grace pitch) to survive across calls. The caller is
     * responsible for flushing pending resets when done.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the composition (for colorize messages)
     * @param startTicks Starting tick position
     * @param initialTempo Tempo at the start of this range
     * @param settings Playback settings
     * @param startElement Index of the first element to add
     * @param endElement Index of the last element to add
     * @param glissandoHelper Shared glissando state across calls
     * @return Pair of (ending tick position, ending tempo)
     */
    public TrackPosition addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        int startElement,
        int endElement,
        GlissandoMidiHelper glissandoHelper
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            startElement, endElement, glissandoHelper, null);
    }

    /**
     * Adds a range of this line's elements to a MIDI track using an externally
     * managed {@link GlissandoMidiHelper} and an optional {@link VelocityMap}
     * for dynamic-aware note velocities. This overload is used by the repeat
     * path, which processes notes one at a time but needs glissando state
     * (e.g. pending grace pitch) to survive across calls. The caller is
     * responsible for flushing pending resets when done.
     */
    public TrackPosition addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        int startElement,
        int endElement,
        GlissandoMidiHelper glissandoHelper,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var ticks = startTicks;
        var currentTempo = initialTempo;

        var actualEnd = Math.min(endElement, line.elementCount() - 1);

        for (var i = startElement; i <= actualEnd; i++) {
            var element = line.getElement(i);

            // Add tempo change if present
            if (element.getTempoChange() != null) {
                currentTempo = element.getTempoChange();
                addTempoMetaMessage(track, ticks, currentTempo, settings.tempoChangePercent());
            }

            // Always emit a colorize meta message for playback highlighting
            addColorizeMetaMessage(track, lineIndex, i, ticks);

            // Add note on/off messages and update ticks
            ticks = addNoteMessages(track, lineIndex, i, ticks, currentTempo, settings,
                glissandoHelper, velocityMap);
        }

        return new TrackPosition(ticks, currentTempo);
    }

    /**
     * Adds a tempo meta message to the track.
     */
    private void addTempoMetaMessage(
        Track track,
        int ticks,
        Tempo tempo,
        int tempoChangePercent
    ) throws InvalidMidiDataException {
        var realTempo = tempo.getRealTempo();
        var midiTempo = 60000000 / ((realTempo * tempoChangePercent) / 100);
        var tempoMessage = new MetaMessage();
        tempoMessage.setMessage(
            MidiMetaMessageTypes.SET_TEMPO,
            new byte[]{
                (byte) (midiTempo >> 16),
                (byte) (midiTempo >> 8),
                (byte) midiTempo,
            },
            3
        );
        track.add(new MidiEvent(tempoMessage, ticks));
    }

    /**
     * Adds a colorize meta message to the track for playback highlighting.
     */
    private void addColorizeMetaMessage(
        Track track,
        int lineIndex,
        int elementIndex,
        int ticks
    ) throws InvalidMidiDataException {
        var playNoteMessage = new MetaMessage();
        playNoteMessage.setMessage(
            MidiMetaMessageTypes.SEQUENCE_NUMBER,
            new byte[]{
                (byte) (lineIndex >> 8),
                (byte) lineIndex,
                (byte) (elementIndex >> 8),
                (byte) elementIndex,
            },
            4
        );
        track.add(new MidiEvent(playNoteMessage, ticks));
    }

    /**
     * Adds note on/off messages to the track and returns the updated tick position.
     */
    private int addNoteMessages(
        Track track,
        int lineIndex,
        int elementIndex,
        int ticks,
        Tempo currentTempo,
        PlaybackSettings settings,
        GlissandoMidiHelper glissandoHelper,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var element = line.getElement(elementIndex);
        var type = element.getType();
        var trackTicks = ticks;

        if (type.isGraceNote()) {
            // Grace notes always have a connected glissando: zero duration,
            // just store the pitch for the next note's slide-in
            glissandoHelper.setPendingGracePitch(element.getPitch());
        } else if (type.isNote() || type.isRest()) {
            var duration = getElementDurationWithTuplet(elementIndex, currentTempo);

            if (type.isNote()) {
                var tieSpan = line.getTies().findSpan(elementIndex);
                var velocity = noteVelocity(element, velocityMap, lineIndex, elementIndex);

                if ((tieSpan == null) || (tieSpan.getStart() == elementIndex)) {
                    glissandoHelper.createPendingResets(track, trackTicks, 0);

                    if (glissandoHelper.hasPendingGracePitch()) {
                        addGraceGlissandoSlideIn(
                            track, trackTicks, duration, element, velocity, glissandoHelper
                        );
                    } else {
                        addNoteOn(track, trackTicks, element, velocity);
                    }
                }

                if ((tieSpan == null) || (tieSpan.getEnd() == elementIndex)) {
                    var glissando = element.getGlissando();

                    if (glissando != null) {
                        addGlissandoMessages(
                            track, trackTicks, duration, elementIndex,
                            element, glissando, settings, glissandoHelper
                        );
                    } else {
                        addNormalNoteOff(track, trackTicks, duration, element, settings);
                    }
                }
            }

            trackTicks += duration;
        }

        return trackTicks;
    }

    /**
     * Adds glissando pitch bend messages and note-off for a note with a glissando.
     * For CONNECTED glissandos, the note sustains for its full written duration (ignoring
     * staccato) and slides toward the next note's pitch. For SLIDE_OUT, the sounding
     * duration respects staccato/noteDurationPercent and the slide goes down.
     */
    private void addGlissandoMessages(
        Track track,
        int trackTicks,
        int duration,
        int elementIndex,
        StaffElement element,
        StaffElement.Glissando glissando,
        PlaybackSettings settings,
        GlissandoMidiHelper glissandoHelper
    ) throws InvalidMidiDataException {
        var sourcePitch = element.getPitch();
        int targetPitch;
        int soundingDuration;

        if (glissando.type == StaffElement.Glissando.Type.CONNECTED) {
            // Need the next note's pitch; fall back to normal note-off if unavailable
            if (elementIndex + 1 >= line.elementCount()) {
                addNormalNoteOff(track, trackTicks, duration, element, settings);
                return;
            }

            var nextElement = line.getElement(elementIndex + 1);

            if (!nextElement.getType().isPitchedNote()) {
                addNormalNoteOff(track, trackTicks, duration, element, settings);
                return;
            }

            targetPitch = nextElement.getPitch();
            soundingDuration = duration;
        } else {
            targetPitch = GlissandoMidiHelper.resolveTargetPitch(
                sourcePitch, glissando.type, 0
            );
            soundingDuration = calculateSoundingDuration(duration, element, settings);
        }

        var sustainTicks = GlissandoMidiHelper.calculateSustainTicks(soundingDuration);
        var slideTicks = GlissandoMidiHelper.calculateSlideTicks(soundingDuration);
        var sensitivity = GlissandoMidiHelper.calculateSensitivity(sourcePitch, targetPitch);

        glissandoHelper.createRpnMessagesIfNeeded(track, trackTicks, 0, sensitivity);

        var slideStartTick = trackTicks + sustainTicks;
        var linear = glissando.type == StaffElement.Glissando.Type.CONNECTED;
        GlissandoMidiHelper.createPitchBendMessages(
            track, slideStartTick, slideTicks, 0, sourcePitch, targetPitch, sensitivity, linear
        );

        // For slide-out, fade expression along the same curve as the pitch
        if (glissando.type == StaffElement.Glissando.Type.SLIDE_OUT) {
            GlissandoMidiHelper.createSlideOutExpressionMessages(
                track, slideStartTick, slideTicks, 0
            );
        }

        // For CONNECTED glissandos, the note-off, pitch bend reset, and next
        // note-on all land on the same tick. MIDI event ordering within a tick
        // is indeterminate, so the reset can fire while this note is still
        // audible, causing a snap back to the original pitch. Offset the
        // note-off by 1 tick so this note is silenced before the reset.
        var noteOffTick = trackTicks + soundingDuration;

        if (glissando.type == StaffElement.Glissando.Type.CONNECTED) {
            noteOffTick--;
        }

        addNoteOff(track, noteOffTick, element);

        // Don't reset pitch bend/expression at note-off — MIDI event ordering
        // within a tick is indeterminate, so resets can fire before the note-off
        // and cause an audible blip. Instead, defer resets to the next note-on.
        glissandoHelper.setNeedsPitchBendReset();

        if (glissando.type == StaffElement.Glissando.Type.SLIDE_OUT) {
            glissandoHelper.setNeedsExpressionReset();
        }
    }

    /**
     * Adds slide-in pitch bend and expression messages for a grace note glissando.
     * The note starts at half velocity with expression at zero, then both pitch
     * and volume ramp up over a fixed 16th-note duration.
     */
    private void addGraceGlissandoSlideIn(
        Track track,
        int trackTicks,
        int duration,
        StaffElement element,
        int velocity,
        GlissandoMidiHelper glissandoHelper
    ) throws InvalidMidiDataException {
        var gracePitch = glissandoHelper.consumePendingGracePitch();
        var notePitch = element.getPitch();
        var slideTicks = Math.min(GlissandoMidiHelper.GRACE_SLIDE_TICKS, duration);
        var sensitivity = GlissandoMidiHelper.calculateSensitivity(gracePitch, notePitch);

        // Set pitch bend sensitivity and initial bend before NOTE_ON
        glissandoHelper.createRpnMessagesIfNeeded(track, trackTicks, 0, sensitivity);
        GlissandoMidiHelper.createSlideInPitchBendMessages(
            track, trackTicks, slideTicks, 0, gracePitch, notePitch, sensitivity
        );

        // Ramp expression up along the same curve as the pitch
        GlissandoMidiHelper.createSlideInExpressionMessages(
            track, trackTicks, slideTicks, 0
        );

        // NOTE_ON at reduced velocity for a soft attack
        addNoteOn(track, trackTicks, element, (int) (velocity * GRACE_GLISSANDO_VELOCITY_RATIO));

        // Reset pitch bend and expression at end of slide
        GlissandoMidiHelper.createPitchBendReset(track, trackTicks + slideTicks, 0);
        GlissandoMidiHelper.createExpressionReset(track, trackTicks + slideTicks, 0);
    }

    /**
     * Adds a normal note-off (no glissando) respecting staccato and articulation overrides.
     */
    private void addNormalNoteOff(
        Track track,
        int trackTicks,
        int duration,
        StaffElement element,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        addNoteOff(
            track,
            (int) (trackTicks + ((duration * (long) calculateSoundingPercent(element, settings)) / 100f)),
            element
        );
    }

    /**
     * Returns the sounding duration in ticks, applying staccato/articulation overrides.
     */
    private int calculateSoundingDuration(
        int duration,
        StaffElement element,
        PlaybackSettings settings
    ) {
        return (int) ((duration * (long) calculateSoundingPercent(element, settings)) / 100f);
    }

    /**
     * Returns the sounding duration percentage for a note, considering articulation
     * overrides and the global noteDurationPercent setting.
     */
    private static int calculateSoundingPercent(StaffElement element, PlaybackSettings settings) {
        var midiOverride = element.findMidiDurationOverride();
        return (midiOverride < 0) ? settings.noteDurationPercent() : midiOverride;
    }

    /**
     * Returns the MIDI velocity for a note. When a {@link VelocityMap} is available
     * (during normal playback), the pre-computed dynamic-aware velocity is used.
     * Otherwise falls back to the legacy binary logic (accented or not).
     */
    private static int noteVelocity(
        StaffElement note,
        @Nullable VelocityMap velocityMap,
        int lineIndex,
        int noteIndex
    ) {
        if (velocityMap != null) {
            return velocityMap.getVelocity(lineIndex, noteIndex);
        }

        return note.hasArticulation(ArticulationType.ACCENT)
            ? PlaybackController.ACCENTED_NOTE_VELOCITY
            : PlaybackController.NOTE_VELOCITY;
    }

    /**
     * Adds a note-on MIDI message to the track with an explicit velocity.
     */
    private void addNoteOn(Track track, int ticks, StaffElement note, int velocity)
        throws InvalidMidiDataException {
        var down = new ShortMessage();
        down.setMessage(ShortMessage.NOTE_ON, 0, note.getPitch(), velocity);
        track.add(new MidiEvent(down, ticks));
    }

    /**
     * Adds a note-off MIDI message to the track.
     */
    private void addNoteOff(Track track, int ticks, StaffElement note) throws InvalidMidiDataException {
        var up = new ShortMessage();
        up.setMessage(ShortMessage.NOTE_OFF, 0, note.getPitch(), 0);
        track.add(new MidiEvent(up, ticks));
    }

}
