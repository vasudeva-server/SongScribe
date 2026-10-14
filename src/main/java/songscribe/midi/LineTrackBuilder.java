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

import java.math.RoundingMode;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ArticulationType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.error.RuntimeError;
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
     * Inside a tuplet the ratio is the one the tuplet stores, never one inferred from the
     * surrounding beat, so what is printed is what is heard.
     */
    public int getElementDurationWithTuplet(int elementIndex) {
        var element = line.getElement(elementIndex);
        var writtenTicks = element.playedDuration();
        var tuplet = line.spanRegistry().findTupletAt(elementIndex);

        if (tuplet == null) {
            return writtenTicks;
        }

        var writtenTicksBefore = 0L;

        for (var i = tuplet.requireAnchorElementIndex(); i < elementIndex; i++) {
            writtenTicksBefore += line.getElement(i).playedDuration();
        }

        var startOffset = tuplet.performedTicks(writtenTicksBefore, RoundingMode.HALF_UP);
        var endOffset = tuplet.performedTicks(writtenTicksBefore + writtenTicks, RoundingMode.HALF_UP);

        return (int) (endOffset - startOffset);
    }

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

    @SuppressWarnings("UnusedReturnValue")
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
     * The caller is responsible for flushing pending resets when done.
     */
    @SuppressWarnings("UnusedReturnValue")
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
     * The caller is responsible for flushing pending resets when done.
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

            // Emit a key signature event where this line establishes its own key (index 0,
            // per KeyChangeElement's position invariant a mid-line change can never land
            // there) and at every mid-line KeyChangeElement. This runs once per call, so
            // a replayed repeat passage re-emits the event exactly where it re-emits the notes.
            if (i == 0) {
                var lineKey = line.getKey();

                if (lineKey != null) {
                    MidiEventFactory.addKeySignatureEvent(track, ticks, lineKey);
                }
            } else if (element.getType().isKeyChange()) {
                MidiEventFactory.addKeySignatureEvent(
                    track, ticks, ((KeyChangeElement) element).getKey());
            }

            var tempoAttachment = element.findAttachment(TempoChangeAttachment.class);

            if (tempoAttachment != null) {
                currentTempo = tempoAttachment.getTempo();
                MidiEventFactory.addTempoEvent(track, ticks, currentTempo, settings.tempoChangePercent());
            }

            addColorizeMetaMessage(track, lineIndex, i, ticks);

            ticks = addNoteMessages(track, lineIndex, i, ticks, settings,
                slideHelper, velocityMap);
        }

        return new TrackPosition(ticks, currentTempo);
    }

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

    private int addNoteMessages(
        Track track,
        int lineIndex,
        int elementIndex,
        int ticks,
        PlaybackSettings settings,
        SlideMidiHelper slideHelper,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var element = line.getElement(elementIndex);
        var type = element.getType();
        var trackTicks = ticks;

        if (element instanceof NoteElement graceNote && type.isGraceNote()) {
            // Grace notes always have a connected glissando: zero duration,
            // just store the pitch for the next note's slide-in
            slideHelper.setPendingGracePitch(graceNote.getPitch());
        } else if (type.isDuration()) {
            var duration = getElementDurationWithTuplet(elementIndex);

            if (element instanceof NoteElement note) {
                var velocity = noteVelocity(note, velocityMap, lineIndex, elementIndex);
                var strikesNote = note.incomingTie() == null;
                var releasesNote = note.tie() == null;

                if (strikesNote) {
                    slideHelper.createPendingResets(track, trackTicks, 0);

                    if (slideHelper.hasPendingGracePitch()) {
                        addGraceGlissandoSlideIn(
                            track, trackTicks, duration, note, velocity, slideHelper
                        );
                    } else {
                        addNoteOn(track, trackTicks, note, velocity);
                    }
                }

                if (releasesNote) {
                    var slide = note.getSlide();

                    if (slide != null) {
                        var slideEvent = new SlideEvent(trackTicks, duration, elementIndex, note, slide);
                        addSlideMessages(track, slideEvent, settings, slideHelper);
                    } else {
                        addNormalNoteOff(track, trackTicks, duration, note, settings);
                    }
                }
            }

            trackTicks += duration;
        }

        return trackTicks;
    }

    private record SlideEvent(
        int trackTicks, int duration, int elementIndex, NoteElement element, Slide slide
    ) {}

    /**
     * For connecting glissandos, the note sustains for its full written duration (ignoring
     * staccato) and slides toward the next note's pitch. For a fall, the sounding
     * duration respects staccato/noteDurationPercent and the slide goes down.
     */
    private void addSlideMessages(
        Track track,
        SlideEvent event,
        PlaybackSettings settings,
        SlideMidiHelper slideHelper
    ) throws InvalidMidiDataException {
        var trackTicks = event.trackTicks();
        var duration = event.duration();
        var elementIndex = event.elementIndex();
        var element = event.element();
        var connecting = event.slide() == Slide.GLISSANDO;
        var sourcePitch = element.getPitch();
        int targetPitch;
        int soundingDuration;

        if (connecting) {
            if (elementIndex + 1 >= line.elementCount()
                || !(line.getElement(elementIndex + 1) instanceof NoteElement nextNote)
                || !nextNote.getType().isPitchedNote()) {
                addNormalNoteOff(track, trackTicks, duration, element, settings);
                return;
            }

            targetPitch = nextNote.getPitch();
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

    private void addGraceGlissandoSlideIn(
        Track track,
        int trackTicks,
        int duration,
        NoteElement element,
        int velocity,
        SlideMidiHelper slideHelper
    ) throws InvalidMidiDataException {
        var gracePitch = slideHelper.consumePendingGracePitch();

        if (gracePitch == null) {
            throw RuntimeError.exit("addGraceGlissandoSlideIn called with no pending grace pitch");
        }

        var notePitch = element.getPitch();
        var slideTicks = Math.min(SlideMidiHelper.GRACE_SLIDE_TICKS, duration);
        var sensitivity = SlideMidiHelper.calculateSensitivity(gracePitch, notePitch);

        slideHelper.createRpnMessagesIfNeeded(track, trackTicks, 0, sensitivity);
        SlideMidiHelper.createSlideInPitchBendMessages(
            track, trackTicks, slideTicks, 0, gracePitch, notePitch, sensitivity
        );

        SlideMidiHelper.createSlideInExpressionMessages(
            track, trackTicks, slideTicks, 0
        );

        addNoteOn(track, trackTicks, element, (int) (velocity * GRACE_GLISSANDO_VELOCITY_RATIO));

        SlideMidiHelper.createPitchBendReset(track, trackTicks + slideTicks, 0);
        SlideMidiHelper.createExpressionReset(track, trackTicks + slideTicks, 0);
    }

    private void addNormalNoteOff(
        Track track,
        int trackTicks,
        int duration,
        NoteElement element,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        addNoteOff(track, trackTicks + calculateSoundingDuration(duration, element, settings), element);
    }

    int calculateSoundingDuration(
        int duration,
        NoteElement element,
        PlaybackSettings settings
    ) {
        return (int) ((duration * (long) calculateSoundingPercent(element, settings)) / PERCENT);
    }

    static int calculateSoundingPercent(NoteElement element, PlaybackSettings settings) {
        var midiOverride = element.findMidiDurationOverride();
        return (midiOverride == null) ? settings.noteDurationPercent() : midiOverride;
    }

    static int noteVelocity(
        NoteElement note,
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

    private void addNoteOn(Track track, int ticks, NoteElement note, int velocity)
        throws InvalidMidiDataException {
        var down = new ShortMessage();
        down.setMessage(ShortMessage.NOTE_ON, 0, note.getPitch(), velocity);
        track.add(new MidiEvent(down, ticks));
    }

    private void addNoteOff(Track track, int ticks, NoteElement note) throws InvalidMidiDataException {
        var up = new ShortMessage();
        up.setMessage(ShortMessage.NOTE_OFF, 0, note.getPitch(), 0);
        track.add(new MidiEvent(up, ticks));
    }

}
