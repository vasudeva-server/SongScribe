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

import songscribe.dom.ArticulationType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlaybackController;

public class LineTrackBuilder {

    private static final double GRACE_GLISSANDO_VELOCITY_RATIO = 0.85;
    private static final int PERCENT = 100;

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
    float getTupletFactor(int elementIndex, Tempo referenceTempo) {
        var tuplet = line.findTupletAt(elementIndex);

        if (tuplet == null) {
            return 1;
        }

        var tupletDuration = 0f;

        for (var i = tuplet.getAnchorElementIndex(); i <= tuplet.getEndElementIndex(); i++) {
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
     * @param track        The MIDI track to add to
     * @param lineIndex    This line's index in the song (for colorize messages)
     * @param startTicks   Starting tick position
     * @param initialTempo Tempo at the start of this line
     * @param settings     Playback settings
     */
    public void addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            0, line.effectiveElementCount() - 1, (VelocityMap) null);
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
            0, line.effectiveElementCount() - 1, velocityMap);
    }

    /**
     * Adds a range of this line's elements to a MIDI track.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the song (for colorize messages)
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
        var slideHelper = new SlideMidiHelper();
        var result = addToTrack(
            track, lineIndex, startTicks, initialTempo, settings,
            startElement, endElement, slideHelper, velocityMap
        );

        // Flush pending pitch bend/expression resets so the state
        // is clean when the sequence loops or the next line starts.
        slideHelper.createPendingResets(track, result.ticks(), 0);

        return result;
    }

    /**
     * Adds a range of this line's elements to a MIDI track using an externally
     * managed {@link SlideMidiHelper}. This overload is used by the repeat
     * path, which processes notes one at a time but needs slide state
     * (e.g. pending grace pitch) to survive across calls. The caller is
     * responsible for flushing pending resets when done.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the song (for colorize messages)
     * @param startTicks Starting tick position
     * @param initialTempo Tempo at the start of this range
     * @param settings Playback settings
     * @param startElement Index of the first element to add
     * @param endElement Index of the last element to add
     * @param slideHelper Shared slide state across calls
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
        SlideMidiHelper slideHelper
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            startElement, endElement, slideHelper, null);
    }

    /**
     * Adds a range of this line's elements to a MIDI track using an externally
     * managed {@link SlideMidiHelper} and an optional {@link VelocityMap}
     * for dynamic-aware note velocities. This overload is used by the repeat
     * path, which processes notes one at a time but needs slide state
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
        SlideMidiHelper slideHelper,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var ticks = startTicks;
        var currentTempo = initialTempo;

        var actualEnd = Math.min(endElement, line.elementCount() - 1);

        for (var i = startElement; i <= actualEnd; i++) {
            var element = line.getElement(i);

            // Add tempo change if present
            var tempoAttachment = element.findAttachment(TempoChangeAttachment.class);

            if (tempoAttachment != null) {
                currentTempo = tempoAttachment.getTempo();
                MidiEventFactory.addTempoEvent(track, ticks, currentTempo, settings.tempoChangePercent());
            }

            // Always emit a colorize meta message for playback highlighting
            addColorizeMetaMessage(track, lineIndex, i, ticks);

            // Add note on/off messages and update ticks
            ticks = addNoteMessages(track, lineIndex, i, ticks, currentTempo, settings,
                slideHelper, velocityMap);
        }

        return new TrackPosition(ticks, currentTempo);
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
        SlideMidiHelper slideHelper,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var element = line.getElement(elementIndex);
        var type = element.getType();
        var trackTicks = ticks;

        if (type.isGraceNote()) {
            // Grace notes always have a connected glissando: zero duration,
            // just store the pitch for the next note's slide-in
            slideHelper.setPendingGracePitch(element.getPitch());
        } else if (type.isNote() || type.isRest()) {
            var duration = getElementDurationWithTuplet(elementIndex, currentTempo);

            if (type.isNote()) {
                var tieSpan = line.findTieAt(elementIndex);
                var velocity = noteVelocity(element, velocityMap, lineIndex, elementIndex);

                if ((tieSpan == null) || (tieSpan.getAnchorElementIndex() == elementIndex)) {
                    slideHelper.createPendingResets(track, trackTicks, 0);

                    if (slideHelper.hasPendingGracePitch()) {
                        addGraceGlissandoSlideIn(
                            track, trackTicks, duration, element, velocity, slideHelper
                        );
                    } else {
                        addNoteOn(track, trackTicks, element, velocity);
                    }
                }

                if ((tieSpan == null) || (tieSpan.getEndElementIndex() == elementIndex)) {
                    var slide = element.getSlide();

                    if (slide != null) {
                        addSlideMessages(
                            track, trackTicks, duration, elementIndex,
                            element, settings, slideHelper, slide instanceof StaffElement.Glissando
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
     * Adds slide pitch bend messages and note-off for a note with a slide.
     * For connecting glissandos, the note sustains for its full written duration (ignoring
     * staccato) and slides toward the next note's pitch. For a fall, the sounding
     * duration respects staccato/noteDurationPercent and the slide goes down.
     */
    private void addSlideMessages(
        Track track,
        int trackTicks,
        int duration,
        int elementIndex,
        StaffElement element,
        PlaybackSettings settings,
        SlideMidiHelper slideHelper,
        boolean connecting
    ) throws InvalidMidiDataException {
        var sourcePitch = element.getPitch();
        int targetPitch;
        int soundingDuration;

        if (connecting) {
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
            targetPitch = SlideMidiHelper.resolveTargetPitch(sourcePitch);
            soundingDuration = calculateSoundingDuration(duration, element, settings);
        }

        var sustainTicks = SlideMidiHelper.calculateSustainTicks(soundingDuration);
        var slideTicks = SlideMidiHelper.calculateSlideTicks(soundingDuration);
        var sensitivity = SlideMidiHelper.calculateSensitivity(sourcePitch, targetPitch);

        slideHelper.createRpnMessagesIfNeeded(track, trackTicks, 0, sensitivity);

        var slideStartTick = trackTicks + sustainTicks;
        SlideMidiHelper.createPitchBendMessages(
            track, slideStartTick, slideTicks, 0, sourcePitch, targetPitch, sensitivity, connecting
        );

        // For a fall, fade expression along the same curve as the pitch
        if (!connecting) {
            SlideMidiHelper.createFallExpressionMessages(
                track, slideStartTick, slideTicks, 0
            );
        }

        // For connecting glissandos, the note-off, pitch bend reset, and next
        // note-on all land on the same tick. MIDI event ordering within a tick
        // is indeterminate, so the reset can fire while this note is still
        // audible, causing a snap back to the original pitch. Offset the
        // note-off by 1 tick so this note is silenced before the reset.
        var noteOffTick = trackTicks + soundingDuration;

        if (connecting) {
            noteOffTick--;
        }

        addNoteOff(track, noteOffTick, element);

        // Don't reset pitch bend/expression at note-off — MIDI event ordering
        // within a tick is indeterminate, so resets can fire before the note-off
        // and cause an audible blip. Instead, defer resets to the next note-on.
        slideHelper.setNeedsPitchBendReset();

        if (!connecting) {
            slideHelper.setNeedsExpressionReset();
        }
    }

    /**
     * Adds slide-in pitch bend and expression messages for a grace note slide-in.
     * The note starts at half velocity with expression at zero, then both pitch
     * and volume ramp up over a fixed 16th-note duration.
     */
    private void addGraceGlissandoSlideIn(
        Track track,
        int trackTicks,
        int duration,
        StaffElement element,
        int velocity,
        SlideMidiHelper slideHelper
    ) throws InvalidMidiDataException {
        var gracePitch = slideHelper.consumePendingGracePitch();
        var notePitch = element.getPitch();
        var slideTicks = Math.min(SlideMidiHelper.GRACE_SLIDE_TICKS, duration);
        var sensitivity = SlideMidiHelper.calculateSensitivity(gracePitch, notePitch);

        // Set pitch bend sensitivity and initial bend before NOTE_ON
        slideHelper.createRpnMessagesIfNeeded(track, trackTicks, 0, sensitivity);
        SlideMidiHelper.createSlideInPitchBendMessages(
            track, trackTicks, slideTicks, 0, gracePitch, notePitch, sensitivity
        );

        // Ramp expression up along the same curve as the pitch
        SlideMidiHelper.createSlideInExpressionMessages(
            track, trackTicks, slideTicks, 0
        );

        // NOTE_ON at reduced velocity for a soft attack
        addNoteOn(track, trackTicks, element, (int) (velocity * GRACE_GLISSANDO_VELOCITY_RATIO));

        // Reset pitch bend and expression at end of slide
        SlideMidiHelper.createPitchBendReset(track, trackTicks + slideTicks, 0);
        SlideMidiHelper.createExpressionReset(track, trackTicks + slideTicks, 0);
    }

    /**
     * Adds a normal note-off (no slide) respecting staccato and articulation overrides.
     */
    private void addNormalNoteOff(
        Track track,
        int trackTicks,
        int duration,
        StaffElement element,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        addNoteOff(track, trackTicks + calculateSoundingDuration(duration, element, settings), element);
    }

    /**
     * Returns the sounding duration in ticks, applying staccato/articulation overrides.
     */
    int calculateSoundingDuration(
        int duration,
        StaffElement element,
        PlaybackSettings settings
    ) {
        return (int) ((duration * (long) calculateSoundingPercent(element, settings)) / PERCENT);
    }

    /**
     * Returns the sounding duration percentage for a note, considering articulation
     * overrides and the global noteDurationPercent setting.
     */
    static int calculateSoundingPercent(StaffElement element, PlaybackSettings settings) {
        var midiOverride = element.findMidiDurationOverride();
        return (midiOverride < 0) ? settings.noteDurationPercent() : midiOverride;
    }

    /**
     * Returns the MIDI velocity for a note. When a {@link VelocityMap} is available
     * (during normal playback), the pre-computed dynamic-aware velocity is used.
     * Otherwise falls back to the legacy binary logic (accented or not).
     */
    static int noteVelocity(
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
