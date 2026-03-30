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

import module java.desktop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import kotlin.Pair;

import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.midi.GlissandoMidiHelper;
import songscribe.midi.PlaybackSettings;
import songscribe.midi.VelocityMap;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlaybackController;

import static songscribe.midi.MidiSequenceBuilder.PPQ;

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };


    private static final double GRACE_GLISSANDO_VELOCITY_RATIO = 0.85;
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
    @SuppressWarnings("NullAway") // set by setComposition() before the Line is used
    private Composition composition = null;
    private int keys = 0;
    @Nullable
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
    private int beatChangeYPosPx = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.BEAT_CHANGE_DEFAULT_Y_SS);

    /**
     * Y offset for lyrics display (default: 50, below staff).
     * <p>
     * Note: This field is still in active use for line-level lyrics positioning.
     * Per-instance lyrics offsets are not yet implemented.
     */
    private double lyricsYPosSs = LayoutStylesheet.LYRICS_DEFAULT_Y_SS;

    /**
     * Y offset for first/second ending display (default: -25, above staff).
     *
     * @deprecated Use per-instance yPosition on Ending objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int firstSecondEndingYPosPx = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.ENDING_DEFAULT_Y_SS);

    /**
     * Y offset for trill display (default: -27, above staff).
     *
     * @deprecated Use per-instance yPosition on Trill objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int trillYPosPx = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TRILL_DEFAULT_Y_SS);

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
        compositionWasModified();
        this.keys = keys;
    }

    public @Nullable KeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(@Nullable KeyType keyType) {
        compositionWasModified();
        this.keyType = keyType;
    }

    public void addElement(StaffElement element) {
        element.setLine(this);
        elements.add(element);
        attachInitialTempoIfNeeded(element);
        compositionWasModified();
    }

    public void addElement(int index, StaffElement element) {
        element.setLine(this);
        elements.add(index, element);
        shiftIntervals(intervalSets, index, 1);
        attachInitialTempoIfNeeded(element);
        compositionWasModified();
    }

    public void setElement(int index, StaffElement element) {
        compositionWasModified();
        element.setLine(this);
        elements.set(index, element);
        attachInitialTempoIfNeeded(element);
    }

    /**
     * Replaces the element at the given index without posting CompositionChangedMessage.
     * The caller is responsible for posting a single CompositionChangedMessage after batch operations.
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
        compositionWasModified();
        elements.remove(index);
        shiftIntervals(intervalSets, index, -1);
    }

    private void compositionWasModified() {
        if (composition != null) {
            composition.setModified(true);
            MessageCenter.post(new CompositionDidChangeNotification(
                CompositionDidChangeNotification.ChangeType.CONTENT, composition, this
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

    /**
     * If the element immediately before {@code index} is a grace note, returns its index.
     * Returns -1 otherwise.
     */
    public int precedingGraceNoteIndex(int index) {
        int candidateIndex = index - 1;

        if (candidateIndex < 0) {
            return -1;
        }

        return getElement(candidateIndex).getType().isGraceNote() ? candidateIndex : -1;
    }

    public boolean isPairedGraceNote(int index) {
        if (index < 0 || index >= elements.size()) {
            return false;
        }

        var element = elements.get(index);

        if (!element.getType().isGraceNote()) {
            return false;
        }

        var glissando = element.getGlissando();
        return glissando != null
            && glissando.type == StaffElement.Glissando.Type.CONNECTED;
    }

    /**
     * @param pitchType 0 for B, 1 for C, 2 for D, ..., 6 for A
     * @return true if there is a leading key for that pitch type
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean keyExists(int pitchType) {
        if (keyType == null) {
            return false;
        }

        var kt = keyType;
        return IntStream.range(0, keys).anyMatch(
            i -> FLAT_SHARP_ORDINAL[kt.ordinal() - 1][i] == pitchType
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
        compositionWasModified();
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
        compositionWasModified();
    }

    public double getLyricsYPosSs() {
        return lyricsYPosSs;
    }

    public void setLyricsYPosSs(double lyricsYPosSs) {
        this.lyricsYPosSs = lyricsYPosSs;
        compositionWasModified();
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
        compositionWasModified();
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
        compositionWasModified();
    }

    public void mulElementDistChange(float ratio) {
        elementDistChangeRatio *= ratio;
        compositionWasModified();
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

    /**
     * Returns true if the given note index falls within any hairpin (crescendo or diminuendo) range.
     */
    public boolean isInHairpinRange(int noteIndex) {
        return crescendo.isInsideAnyInterval(noteIndex) ||
            diminuendo.isInsideAnyInterval(noteIndex);
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
        if (composition != null && (composition.indexOfLine(this) == 0) && (elementCount() > 0)) {
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
    public void addRangeElement(RangeElement element) {
        element.setParentLine(this);
        rangeElements.add(element);
        compositionWasModified();
    }

    /**
     * Removes a range element from this line.
     *
     * @param element The range element to remove
     * @return true if the element was removed
     */
    public boolean removeRangeElement(RangeElement element) {
        if (rangeElements.remove(element)) {
            element.setParentLine(null);
            compositionWasModified();

            return true;
        }

        return false;
    }

    /**
     * Returns an unmodifiable view of the range elements in this line.
     */
    public List<RangeElement> getRangeElements() {
        return Collections.unmodifiableList(rangeElements);
    }

    /**
     * Finds all range elements that include the specified element index.
     *
     * @param elementIndex The element index to search for
     * @return List of range elements containing the element
     */
    public List<RangeElement> findRangeElementsAt(int elementIndex) {
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
    public <T extends RangeElement> List<T> findRangeElements(Class<T> type) {
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
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            0, elementCount() - 1, (VelocityMap) null);
    }

    /**
     * Adds all of this line's elements to a MIDI track, using a velocity map
     * for dynamic-aware note velocities.
     */
    public Pair<Integer, Tempo> addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            0, elementCount() - 1, velocityMap);
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
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings,
            startElement, endElement, (VelocityMap) null);
    }

    /**
     * Adds a range of this line's elements to a MIDI track, using a velocity map
     * for dynamic-aware note velocities.
     */
    public Pair<Integer, Tempo> addToTrack(
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
        glissandoHelper.createPendingResets(track, result.getFirst(), 0);

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
    public Pair<Integer, Tempo> addToTrack(
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
    public Pair<Integer, Tempo> addToTrack(
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

        var actualEnd = Math.min(endElement, elementCount() - 1);

        for (var i = startElement; i <= actualEnd; i++) {
            var element = getElement(i);

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
        int lineIndex,
        int elementIndex,
        int ticks,
        Tempo currentTempo,
        PlaybackSettings settings,
        GlissandoMidiHelper glissandoHelper,
        @Nullable VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var element = getElement(elementIndex);
        var type = element.getType();
        var trackTicks = ticks;

        if (type.isGraceNote()) {
            // Grace notes always have a connected glissando: zero duration,
            // just store the pitch for the next note's slide-in
            glissandoHelper.setPendingGracePitch(element.getPitch());
        } else if (type.isNote() || type.isRest()) {
            var duration = getElementDurationWithTuplet(elementIndex, currentTempo);

            if (type.isNote()) {
                var interval = ties.findInterval(elementIndex);
                var velocity = noteVelocity(element, velocityMap, lineIndex, elementIndex);

                if ((interval == null) || (interval.getStart() == elementIndex)) {
                    glissandoHelper.createPendingResets(track, trackTicks, 0);

                    if (glissandoHelper.hasPendingGracePitch()) {
                        addGraceGlissandoSlideIn(
                            track, trackTicks, duration, element, velocity, glissandoHelper
                        );
                    } else {
                        addNoteOn(track, trackTicks, element, velocity);
                    }
                }

                if ((interval == null) || (interval.getEnd() == elementIndex)) {
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
            if (elementIndex + 1 >= elementCount()) {
                addNormalNoteOff(track, trackTicks, duration, element, settings);
                return;
            }

            var nextElement = getElement(elementIndex + 1);

            if (!nextElement.getType().isNote()) {
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
