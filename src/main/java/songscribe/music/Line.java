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
package songscribe.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jetbrains.annotations.NotNull;

import kotlin.Pair;

import songscribe.data.BeamInterval;
import songscribe.data.DynamicsInterval;
import songscribe.data.EndingInterval;
import songscribe.data.IntervalSet;
import songscribe.data.TieInterval;
import songscribe.data.TupletInterval;
import songscribe.midi.PlaybackSettings;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.MidiMetaMessageTypes;

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };

    // MIDI constants for playback
    private static final int PPQ = 96;
    private static final int GRACE_QUAVER_DURATION = PPQ / 8;
    private static final int NOTE_VELOCITY = 98;
    private static final int ACCENTED_NOTE_VELOCITY = 127;
    private final IntervalSet<BeamInterval> beamings = new IntervalSet<>();
    private final IntervalSet<TieInterval> ties = new IntervalSet<>();
    private final IntervalSet<TupletInterval> tuplets = new IntervalSet<>();
    private final IntervalSet<EndingInterval> firstSecondEndings = new IntervalSet<>();
    private final IntervalSet<DynamicsInterval> crescendo = new IntervalSet<>();
    private final IntervalSet<DynamicsInterval> diminuendo = new IntervalSet<>();
    @SuppressWarnings("rawtypes")
    private final IntervalSet[] intervalSets = new IntervalSet[]{
        beamings,
        ties,
        tuplets,
        firstSecondEndings,
        crescendo,
        diminuendo,
    };

    // =========================================================================
    // New storage for Phase 4+ layout redesign
    // These will replace IntervalSets after Phase 7 (IO) migration
    // =========================================================================

    /** Range elements (ties, trills, crescendo, diminuendo, tuplets, endings). */
    private final List<RangeElement> rangeElements = new ArrayList<>();

    // acceleration
    public StaffElement.SyllableRelation beginRelation = StaffElement.SyllableRelation.NO;
    private Composition composition = null;
    private int keys = 0;
    private KeyType keyType = null;
    private final List<StaffElement> elements = new ArrayList<>();

    // ---------------------------------------------------------------------
    // Legacy View Properties (Y positions relative to middleLineY)
    // ---------------------------------------------------------------------
    // DEPRECATED: These line-level Y position fields are retained for backward
    // compatibility with legacy documents (pre-Phase 11). New code should use
    // per-instance offsets on the element objects themselves:
    //   - Tempo/BeatChange: use Attachment.getUserYOffset()
    //   - Endings: use Ending.getYPosition()
    //   - Trills: use Trill.getYPosition()
    //   - Annotations: use Annotation.getUserYOffset()
    //
    // When loading legacy documents, FormatMigrator converts these line-level
    // offsets to per-instance offsets. LineIO no longer writes these fields
    // to new documents.
    // ---------------------------------------------------------------------

    /**
     * Y offset for tempo change display. Line 0 default: -40, others: -24.
     *
     * @deprecated Use per-instance userYOffset on TempoAttachment instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int tempoChangeYPosPx = 0;

    /**
     * Y offset for beat change display (default: -24, above staff).
     *
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int beatChangeYPosPx = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.BEAT_CHANGE_DEFAULT_Y);

    /**
     * Y offset for lyrics display (default: 50, below staff).
     * <p>
     * Note: This field is still in active use for line-level lyrics positioning.
     * Per-instance lyrics offsets are not yet implemented.
     */
    private double lyricsYPosSs = LayoutStylesheet.LYRICS_DEFAULT_Y;

    /**
     * Y offset for first/second ending display (default: -25, above staff).
     *
     * @deprecated Use per-instance yPosition on Ending objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int firstSecondEndingYPosPx = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.ENDING_DEFAULT_Y);

    /**
     * Y offset for trill display (default: -27, above staff).
     *
     * @deprecated Use per-instance yPosition on Trill objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int trillYPosPx = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TRILL_DEFAULT_Y);

    /** Ratio multiplier for horizontal element spacing (default: 1.0, user-adjustable). */
    private float elementDistChangeRatio = 1f;


    public Composition getComposition() {
        return composition;
    }

    public void setComposition(Composition composition) {
        this.composition = composition;
    }

    public int getKeyAccidentalCount() {
        return keys;
    }

    public void setKeyAccidentalCount(int keys) {
        modifiedComposition();
        this.keys = keys;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(KeyType keyType) {
        modifiedComposition();
        this.keyType = keyType;
    }

    public void addElement(StaffElement element) {
        element.setLine(this);
        elements.add(element);
        attachInitialTempoIfNeeded(element);
        modifiedComposition();
    }

    public void addElement(int index, StaffElement element) {
        element.setLine(this);
        elements.add(index, element);
        shiftIntervals(intervalSets, index, 1);
        attachInitialTempoIfNeeded(element);
        modifiedComposition();
    }

    public void setElement(int index, StaffElement element) {
        modifiedComposition();
        element.setLine(this);
        elements.set(index, element);
        attachInitialTempoIfNeeded(element);
    }

    /**
     * Replaces the element at the given index without posting LayoutChangeMessage.
     * The caller is responsible for posting a single LayoutChangeMessage after batch operations.
     */
    public void replaceElementQuietly(int index, StaffElement element) {
        element.setLine(this);
        elements.set(index, element);
    }

    /**
     * Attaches the composition's initial tempo to the first element of the first line
     * if it doesn't already have a tempo change.
     */
    private void attachInitialTempoIfNeeded(StaffElement element) {
        if (composition == null) {
            return;
        }

        // Check if this is the first element of the first line
        boolean isFirstLine = composition.indexOfLine(this) == 0;
        boolean isFirstElement = elements.size() == 1 && elements.get(0) == element;

        if (isFirstLine && isFirstElement && element.getTempoChange() == null) {
            var initialTempo = composition.getTempo();

            if (initialTempo != null) {
                element.setTempoChange(initialTempo);
            }
        }
    }

    public StaffElement getElement(int index) {
        return elements.get(index);
    }

    public List<StaffElement> getElements() {
        return elements;
    }

    // Returns a sublist of elements from start to end inclusive
    public List<StaffElement> getElements(int start, int end) {
        // subList is exclusive of the end index, so we add 1
        return elements.subList(start, end + 1);
    }

    public void removeElement(int index) {
        modifiedComposition();
        elements.remove(index);
        shiftIntervals(intervalSets, index, -1);
    }

    private void modifiedComposition() {
        if (composition != null) {
            composition.setModified(true);

            MessageCenter.post(new LayoutChangeMessage(
                LayoutChangeMessage.Section.SCORE,
                LayoutChangeMessage.ChangeType.CONTENT,
                true,
                this
            ));
        }
    }

    public int elementCount() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int getElementIndex(StaffElement element) {
        return elements.indexOf(element);
    }

    /**
     * Returns whether inserting at the given index would conflict with a paired grace note.
     * A grace note is paired when it has a {@link StaffElement.Glissando.Type#CONNECTED} glissando
     * linking it to the following note.
     * <p>
     * This returns {@code true} in two cases:
     * <ul>
     *   <li>The element at {@code index} is itself a paired grace note (clicking on it)</li>
     *   <li>The element at {@code index - 1} is a paired grace note (clicking between it and its host)</li>
     * </ul>
     */
    public boolean isInsideGraceHostPair(int index) {
        return isPairedGraceNote(index) || isPairedGraceNote(index - 1);
    }

    private boolean isPairedGraceNote(int index) {
        if (index < 0 || index >= elements.size()) {
            return false;
        }

        var element = elements.get(index);

        if (!element.getType().isGraceNote()) {
            return false;
        }

        var glissando = element.getGlissando();
        //noinspection ObjectEquality
        return glissando != StaffElement.NO_GLISSANDO
            && glissando.type == StaffElement.Glissando.Type.CONNECTED;
    }

    /**
     * @param pitchType 0 for B, 1 for C, 2 for D, ..., 6 for A
     * @return true if there is a leading key for that pitch type
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean keyExists(int pitchType) {
        return IntStream.range(0, keys).anyMatch(
            i -> FLAT_SHARP_ORDINAL[keyType.ordinal() - 1][i] == pitchType
        );
    }

    /**
     * @deprecated Use per-instance userYOffset on TempoAttachment instead.
     */
    @Deprecated
    public int getTempoChangeYPosPx() {
        return tempoChangeYPosPx;
    }

    /**
     * @deprecated Use per-instance userYOffset on TempoAttachment instead.
     */
    @Deprecated
    public void setTempoChangeYPosPx(int tempoChangeYPosPx) {
        this.tempoChangeYPosPx = tempoChangeYPosPx;
        modifiedComposition();
    }

    /**
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     */
    @Deprecated
    public int getBeatChangeYPosPx() {
        return beatChangeYPosPx;
    }

    /**
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     */
    @Deprecated
    public void setBeatChangeYPosPx(int beatChangeYPosPx) {
        this.beatChangeYPosPx = beatChangeYPosPx;
        modifiedComposition();
    }

    public double getLyricsYPosSs() {
        return lyricsYPosSs;
    }

    public void setLyricsYPosSs(double lyricsYPosSs) {
        this.lyricsYPosSs = lyricsYPosSs;
        modifiedComposition();
    }

    /**
     * @deprecated Use per-instance yPosition on Ending objects instead.
     */
    @Deprecated
    public int getFirstSecondEndingYPosPx() {
        return firstSecondEndingYPosPx;
    }

    /**
     * @deprecated Use per-instance yPosition on Ending objects instead.
     */
    @Deprecated
    public void setFirstSecondEndingYPosPx(int fsEndingYPosPx) {
        firstSecondEndingYPosPx = fsEndingYPosPx;
        modifiedComposition();
    }

    /**
     * @deprecated Use per-instance yPosition on Trill objects instead.
     */
    @Deprecated
    public int getTrillYPosPx() {
        return trillYPosPx;
    }

    /**
     * @deprecated Use per-instance yPosition on Trill objects instead.
     */
    @Deprecated
    public void setTrillYPosPx(int trillYPosPx) {
        this.trillYPosPx = trillYPosPx;
        modifiedComposition();
    }

    public void mulElementDistChange(float ratio) {
        elementDistChangeRatio *= ratio;
        modifiedComposition();
    }

    public float getElementDistChangeRatio() {
        return elementDistChangeRatio;
    }

    public IntervalSet<BeamInterval> getBeamings() {
        return beamings;
    }

    public IntervalSet<TieInterval> getTies() {
        return ties;
    }

    public IntervalSet<TupletInterval> getTuplets() {
        return tuplets;
    }

    public IntervalSet<EndingInterval> getFirstSecondEndings() {
        return firstSecondEndings;
    }

    public IntervalSet<DynamicsInterval> getCrescendos() {
        return crescendo;
    }

    public IntervalSet<DynamicsInterval> getDiminuendos() {
        return diminuendo;
    }

    public void removeInterval(int a, int b) {
        for (var is : intervalSets) {
            is.removeInterval(a, b);
        }
    }

    public IntervalSet<?>[] copyIntervals(int a, int b) {
        var retIs = Arrays.stream(intervalSets)
            .map(intervalSet -> intervalSet.copyInterval(a, b))
            .toArray(IntervalSet[]::new); //noinspection unchecked

        shiftIntervals(retIs, 0, -a);
        return retIs;
    }

    public void pasteIntervals(IntervalSet<?>[] copyIntervalSets, int xIndex) {
        shiftIntervals(copyIntervalSets, 0, xIndex);

        for (var i = 0; i < intervalSets.length; i++) {
            for (var li = copyIntervalSets[i].listIterator(); li.hasNext(); ) {
                var iv = li.next();
                //noinspection unchecked,rawtypes
                ((IntervalSet) intervalSets[i]).addInterval(iv);
            }
        }

        shiftIntervals(copyIntervalSets, 0, -xIndex);
    }

    private void shiftIntervals(IntervalSet<?>[] iss, int from, int shift) {
        for (var is : iss) {
            is.shiftValues(from, shift);
            is.removeInterval(Integer.MIN_VALUE, 0);
            is.removeInterval(elements.size() - 1, Integer.MAX_VALUE);
        }
    }

    public int getFirstTempoChange() {
        if ((composition.indexOfLine(this) == 0) && (elementCount() > 0)) {
            return 0;
        }

        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).getTempoChange() != null)
            .findFirst()
            .orElse(-1);
    }

    public boolean isAnnotation() {
        return IntStream.range(0, elementCount()).anyMatch(
            n -> getElement(n).getAnnotation() != null
        );
    }

    public int getFirstTrill() {
        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).isTrill())
            .findFirst()
            .orElse(-1);
    }

    public int getFirstBeatChange() {
        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).getBeatChange() != null)
            .findFirst()
            .orElse(-1);
    }

    // =========================================================================
    // Range Element Management (Phase 4+)
    // =========================================================================

    /**
     * Adds a range element to this line.
     *
     * @param element The range element to add
     */
    public void addRangeElement(@NotNull RangeElement element) {
        element.setParentLine(this);
        rangeElements.add(element);
        modifiedComposition();
    }

    /**
     * Removes a range element from this line.
     *
     * @param element The range element to remove
     * @return true if the element was removed
     */
    public boolean removeRangeElement(@NotNull RangeElement element) {
        if (rangeElements.remove(element)) {
            element.setParentLine(null);
            modifiedComposition();

            return true;
        }

        return false;
    }

    /**
     * Returns an unmodifiable view of the range elements in this line.
     */
    public @NotNull List<RangeElement> getRangeElements() {
        return Collections.unmodifiableList(rangeElements);
    }

    /**
     * Finds all range elements that include the specified element index.
     *
     * @param elementIndex The element index to search for
     * @return List of range elements containing the element
     */
    public @NotNull List<RangeElement> findRangeElementsAt(int elementIndex) {
        var result = new ArrayList<RangeElement>();

        for (var element : rangeElements) {
            int start = element.getAnchorElementIndex();
            int end = element.getEndElementIndex();

            if (elementIndex >= start && elementIndex <= end) {
                result.add(element);
            }
        }

        return result;
    }

    /**
     * Finds range elements of a specific type.
     *
     * @param type The class of range element to find
     * @return List of range elements of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T extends RangeElement> @NotNull List<T> findRangeElements(@NotNull Class<T> type) {
        var result = new ArrayList<T>();

        for (var element : rangeElements) {
            if (type.isInstance(element)) {
                result.add((T) element);
            }
        }

        return result;
    }

    // =========================================================================
    // Beam Group Management (Phase 4+)
    // =========================================================================

    // =========================================================================
    // MIDI Duration Calculation (Phase 1: Score Cleanup)
    // =========================================================================

    /**
     * Returns the duration of an element adjusted for tuplet membership.
     *
     * @param elementIndex Index of the element
     * @param referenceTempo The tempo providing the reference note duration
     * @return Duration in ticks, adjusted for tuplet if applicable
     */
    public int getElementDurationWithTuplet(int elementIndex, Tempo referenceTempo) {
        return Math.round(getElement(elementIndex).getDuration() * getTupletFactor(elementIndex, referenceTempo));
    }

    /**
     * Calculates the tuplet scaling factor for an element.
     *
     * @param elementIndex Index of the element
     * @param referenceTempo The tempo providing the reference note duration
     * @return Scaling factor (1.0 if not in a tuplet)
     */
    private float getTupletFactor(int elementIndex, Tempo referenceTempo) {
        var tupletInt = tuplets.findInterval(elementIndex);

        if (tupletInt == null) {
            return 1;
        }

        var tupletDuration = 0f;

        for (var i = tupletInt.getStart(); i <= tupletInt.getEnd(); i++) {
            tupletDuration += getElement(i).getDuration();
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
    public Pair<Integer, Tempo> addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings, 0, elementCount() - 1);
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
    public Pair<Integer, Tempo> addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        int startElement,
        int endElement
    ) throws InvalidMidiDataException {
        var ticks = startTicks;
        var currentTempo = initialTempo;

        var actualEnd = Math.min(endElement, elementCount() - 1);

        for (var i = startElement; i <= actualEnd; i++) {
            var element = getElement(i);

            // Add tempo change if present
            if (element.getTempoChange() != null) {
                currentTempo = element.getTempoChange();
                addTempoMetaMessage(track, ticks, currentTempo, settings.tempoChangePercent());
            }

            // Add colorize message if enabled
            if (settings.colorizeNotes()) {
                addColorizeMetaMessage(track, lineIndex, i, ticks);
            }

            // Add note on/off messages and update ticks
            ticks = addNoteMessages(track, i, ticks, currentTempo, settings);
        }

        return new Pair<>(ticks, currentTempo);
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
        int elementIndex,
        int ticks,
        Tempo currentTempo,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        var element = getElement(elementIndex);
        var type = element.getType();
        var trackTicks = ticks;

        if (type.isGraceNote()) {
            addNoteOn(track, trackTicks, element);
            trackTicks += GRACE_QUAVER_DURATION;
            addNoteOff(track, trackTicks, element);
        } else if (type.isNote() || type.isRest()) {
            var duration = getElementDurationWithTuplet(elementIndex, currentTempo);

            if (type.isNote()) {
                var interval = ties.findInterval(elementIndex);

                if ((interval == null) || (interval.getStart() == elementIndex)) {
                    addNoteOn(track, trackTicks, element);
                }

                if ((interval == null) || (interval.getEnd() == elementIndex)) {
                    var midiOverride = element.findMidiDurationOverride();
                    var currDuration = (midiOverride < 0)
                        ? settings.noteDurationPercent()
                        : midiOverride;
                    addNoteOff(
                        track,
                        (int) (trackTicks + ((duration * currDuration) / 100f)),
                        element
                    );
                }
            }

            trackTicks += duration;
        }

        return trackTicks;
    }

    /**
     * Adds a note-on MIDI message to the track.
     */
    private void addNoteOn(Track track, int ticks, StaffElement note) throws InvalidMidiDataException {
        var down = new ShortMessage();
        down.setMessage(
            ShortMessage.NOTE_ON, 0,
            note.getPitch(),
            note.hasArticulation(ArticulationType.ACCENT)
                ? ACCENTED_NOTE_VELOCITY
                : NOTE_VELOCITY
        );
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
