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
package songscribe.io.musicxml;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;

import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

/**
 * Measure-level metronome {@code <direction>} state machine. Handles both
 * {@code <metronome>} forms of one {@code <direction>}:
 *
 * <ul>
 *   <li><b>Tempo</b> (beat-unit form): beat-unit + dots, per-minute, an optional
 *       {@code <words>} description, and the {@code print-object} hide flag →
 *       a {@link Tempo}, held pending until the next {@code <note>} binds it as a
 *       {@link TempoChangeAttachment}.</li>
 *   <li><b>Metric modulation</b> (two-note-relation form): two
 *       {@code <metronome-note>}s (each a {@code <metronome-type>} + dots) related
 *       by {@code <metronome-relation>} → a {@link BeatChange}, held pending until
 *       the next {@code <note>} binds it as a {@link BeatChangeAttachment}.</li>
 * </ul>
 *
 * <p>The form is distinguished at {@code </direction>} by which children were
 * accumulated: a {@code <beat-unit>} marks the tempo form, {@code <metronome-note>}s
 * mark the modulation form.
 *
 * <p>Inverts the writer's placement: {@code MusicXmlWriter} emits both the tempo
 * and the metric-modulation {@code <direction>} immediately before their bound
 * {@code <note>}, so the reader uses one uniform rule — a finished direction binds
 * to the next note after it.
 *
 * <p>The one exception is the song tempo: the first tempo direction of the first
 * {@code <measure>} is the score's own tempo, so it binds to no note and produces no
 * {@link TempoChangeAttachment}. It waits in {@link #takePendingSongTempo} for the caller
 * to apply it to the {@link songscribe.dom.Song}. A metric modulation is never a song
 * tempo, and every later tempo direction — including a second one inside measure 1 —
 * binds to its note as usual.
 */
final class MetronomeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MetronomeResolver.class);

    // A SongScribe metric modulation always relates exactly two note values
    // (duration equals beat), so the writer emits exactly two <metronome-note>s.
    private static final int METRIC_MODULATION_NOTE_COUNT = 2;

    /**
     * How far the parse has got relative to the one measure the song-tempo rule cares
     * about. There is no {@code </measure>} event, so {@link #IN_FIRST_MEASURE} holds
     * from the moment measure 1 opens until measure 2 does — which is exactly the same
     * thing at every point this is read, since a {@code <direction>} only ever appears
     * inside a measure.
     */
    private enum MeasurePhase {
        BEFORE_ANY_MEASURE,
        IN_FIRST_MEASURE,
        PAST_FIRST_MEASURE
    }

    // -------------------------------------------------------------------------
    // Per-direction accumulation — reset after every </direction> so a wedge (or
    // any non-metronome) direction cannot inherit a previous direction's state.
    // -------------------------------------------------------------------------

    // The <beat-unit> token; non-null marks the current direction as a metronome
    // tempo direction (vs a wedge direction, which never sets it).
    @Nullable
    private String beatUnitToken = null;

    private int beatUnitDotCount = 0;

    private int visibleTempo = 0;
    private boolean hasVisibleTempo = false;

    // The optional <words> description; null when the direction carries none.
    @Nullable
    private String words = null;

    // print-object="no" on <metronome> hides the mark; default is shown.
    private boolean showTempo = true;

    // Metric-modulation form: the finished <metronome-note>s in document order
    // (index 0 = duration/left value, index 1 = beat/right value); non-empty marks
    // the current direction as a metric modulation (vs the beat-unit tempo form).
    private final List<MetronomeNote> metronomeNotes = new ArrayList<>(METRIC_MODULATION_NOTE_COUNT);

    // The <metronome-note> currently being parsed; its token is set by
    // <metronome-type> and dots by each <metronome-dot/>, finalized at
    // </metronome-note>.
    @Nullable
    private String metronomeNoteToken = null;

    private int metronomeNoteDotCount = 0;

    // -------------------------------------------------------------------------
    // Pending marks — built at </direction>, awaiting their bound note.
    // -------------------------------------------------------------------------

    @Nullable
    private Tempo pendingTempo = null;

    @Nullable
    private BeatChange pendingBeatChange = null;

    // The song's own tempo, awaiting collection by takePendingSongTempo(). Bound to no
    // note — unlike pendingTempo, which waits for the next <note> to attach itself to.
    @Nullable
    private Tempo pendingSongTempo = null;

    // True once the song-level tempo has been claimed, so only the *first* tempo
    // direction of measure 1 becomes the song tempo; a second one in the same
    // measure is an ordinary tempo change.
    private boolean songTempoTaken = false;

    private MeasurePhase measurePhase = MeasurePhase.BEFORE_ANY_MEASURE;

    // -------------------------------------------------------------------------
    // Package API — accumulation
    // -------------------------------------------------------------------------

    /**
     * Records that a {@code <measure>} has opened, so the song-tempo rule can tell the
     * document's first measure from every later one. Tracked by document order rather
     * than by the {@code number} attribute, which a foreign file is not obliged to
     * start at 1.
     */
    void startMeasure() {
        measurePhase = measurePhase == MeasurePhase.BEFORE_ANY_MEASURE
            ? MeasurePhase.IN_FIRST_MEASURE
            : MeasurePhase.PAST_FIRST_MEASURE;
    }

    /**
     * Begins a {@code <metronome>} within the current direction, recovering the
     * hide flag: {@code print-object="no"} means the mark is not shown.
     */
    void beginMetronome(Attributes attributes) {
        showTempo = !MusicXmlTags.NO.equals(attributes.getValue(MusicXmlTags.ATTR_PRINT_OBJECT));
    }

    /** Records the {@code <beat-unit>} note-type token. */
    void setBeatUnitToken(String token) {
        beatUnitToken = token;
    }

    /** Records one {@code <beat-unit-dot/>} augmentation dot. */
    void addBeatUnitDot() {
        beatUnitDotCount++;
    }

    /** Records the {@code <per-minute>} value (the visible tempo in BPM). */
    void setVisibleTempo(int perMinute) {
        visibleTempo = perMinute;
        hasVisibleTempo = true;
    }

    /** Records the {@code <words>} tempo description. */
    void setWords(String description) {
        words = description;
    }

    /** Begins a {@code <metronome-note>}, clearing the per-note token and dots. */
    void beginMetronomeNote() {
        metronomeNoteToken = null;
        metronomeNoteDotCount = 0;
    }

    /** Records the current {@code <metronome-note>}'s {@code <metronome-type>} token. */
    void setMetronomeType(String token) {
        metronomeNoteToken = token;
    }

    /** Records one {@code <metronome-dot/>} on the current {@code <metronome-note>}. */
    void addMetronomeDot() {
        metronomeNoteDotCount++;
    }

    /**
     * Finalizes the current {@code <metronome-note>} at {@code </metronome-note>},
     * appending its token and dot count to the accumulated notes in document order.
     * A note missing its {@code <metronome-type>} is dropped.
     */
    void endMetronomeNote() {
        if (metronomeNoteToken != null) {
            metronomeNotes.add(new MetronomeNote(metronomeNoteToken, metronomeNoteDotCount));
        }

        metronomeNoteToken = null;
        metronomeNoteDotCount = 0;
    }

    /**
     * Finalizes the current {@code <direction>} at {@code </direction>}. Builds a
     * {@link BeatChange} from the two-note metric-modulation form, else a
     * {@link Tempo} from the complete beat-unit form (a beat-unit and a
     * per-minute); otherwise (e.g. a wedge direction) builds nothing. The first
     * tempo of the first measure waits in {@link #takePendingSongTempo} instead of
     * waiting for a note to bind to. Always clears the per-direction accumulation
     * so the next direction starts clean.
     */
    void endDirection() {
        if (!metronomeNotes.isEmpty()) {
            buildPendingBeatChange();
            resetAccumulation();
            return;
        }

        var token = beatUnitToken;

        if (token == null || !hasVisibleTempo) {
            // Not a metronome tempo direction (a wedge direction, or an
            // incomplete metronome) — nothing to build.
            resetAccumulation();
            return;
        }

        var duration = BeatUnitMapping.forBeatUnit(token, beatUnitDotCount);

        if (duration == null) {
            LOG.warn("Ignoring <metronome> with unrecognised beat-unit '{}' (dots={})",
                token, beatUnitDotCount);
            resetAccumulation();
            return;
        }

        var description = words != null ? words : "";
        var tempo = new Tempo(visibleTempo, duration, description, showTempo);

        // The first tempo direction of the first measure is the song's tempo, not
        // a tempo change, so it binds to no note. The test is purely positional --
        // "first tempo direction in measure 1" -- and deliberately says nothing
        // about where within the measure it sits: the writer now emits it as the
        // measure's first child, but files written before that change carry it
        // immediately before the first <note>, and both must load the same way.
        if (!songTempoTaken && measurePhase == MeasurePhase.IN_FIRST_MEASURE) {
            songTempoTaken = true;
            pendingSongTempo = tempo;
        } else {
            pendingTempo = tempo;
        }

        resetAccumulation();
    }

    /**
     * Builds a pending {@link BeatChange} from the metric-modulation form. The
     * writer emits exactly two {@code <metronome-note>}s (duration equals beat);
     * any other count, or an unrecognised note, is an unsupported metronome the
     * reader does not build.
     */
    private void buildPendingBeatChange() {
        if (metronomeNotes.size() != METRIC_MODULATION_NOTE_COUNT) {
            LOG.warn("Ignoring <metronome> metric modulation with {} note(s); expected {}",
                metronomeNotes.size(), METRIC_MODULATION_NOTE_COUNT);
            return;
        }

        var durationNote = metronomeNotes.get(0);
        var beatNote = metronomeNotes.get(1);
        var duration = BeatUnitMapping.forBeatUnit(durationNote.token(), durationNote.dotCount());
        var beat = BeatUnitMapping.forBeatUnit(beatNote.token(), beatNote.dotCount());

        if (duration == null || beat == null) {
            LOG.warn("Ignoring <metronome> metric modulation with unrecognised note ('{}' dots={} / '{}' dots={})",
                durationNote.token(), durationNote.dotCount(), beatNote.token(), beatNote.dotCount());
            return;
        }

        pendingBeatChange = new BeatChange(duration, beat);
    }

    // -------------------------------------------------------------------------
    // Package API — binding
    // -------------------------------------------------------------------------

    /**
     * Binds a finished tempo direction to {@code element} by attaching a
     * {@link TempoChangeAttachment}. No-op when no tempo is pending.
     */
    void resolveTempo(StaffElement element) {
        if (pendingTempo == null) {
            return;
        }

        element.addAttachment(new TempoChangeAttachment(element, pendingTempo));
        pendingTempo = null;
    }

    /**
     * Hands back the song's own tempo — the first tempo direction of the first measure —
     * and clears it, so it is applied exactly once. Returns null when none is waiting.
     *
     * <p>This resolver writes nothing to the document, exactly like its {@code WedgeResolver}
     * and {@code AnnotationResolver} siblings: it accumulates, and whoever holds the
     * {@link songscribe.dom.Song} applies. That keeps it testable on its own and keeps the
     * one thing here that belongs to no note from being a hidden side effect of parsing a
     * {@code </direction>}.
     */
    @Nullable Tempo takePendingSongTempo() {
        var songTempo = pendingSongTempo;
        pendingSongTempo = null;
        return songTempo;
    }

    /**
     * Binds a finished metric-modulation direction to {@code element} by attaching
     * a {@link BeatChangeAttachment}. No-op when no beat change is pending.
     */
    void resolveBeatChange(StaffElement element) {
        if (pendingBeatChange == null) {
            return;
        }

        element.addAttachment(new BeatChangeAttachment(element, pendingBeatChange));
        pendingBeatChange = null;
    }

    /**
     * Drops a pending tempo still unbound at a part boundary: a tempo direction
     * with no following note builds nothing.
     */
    void flushPendingTempo() {
        if (pendingTempo != null) {
            LOG.warn("Dropping tempo <direction> with no bound note");
            pendingTempo = null;
        }
    }

    /**
     * Drops a pending metric modulation still unbound at a part boundary: a
     * metric-modulation direction with no following note builds nothing.
     */
    void flushPendingBeatChange() {
        if (pendingBeatChange != null) {
            LOG.warn("Dropping metric-modulation <direction> with no bound note");
            pendingBeatChange = null;
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void resetAccumulation() {
        beatUnitToken = null;
        beatUnitDotCount = 0;
        visibleTempo = 0;
        hasVisibleTempo = false;
        words = null;
        showTempo = true;
        metronomeNotes.clear();
        metronomeNoteToken = null;
        metronomeNoteDotCount = 0;
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    /**
     * One accumulated {@code <metronome-note>}: its {@code <metronome-type>} token
     * paired with its {@code <metronome-dot/>} count, resolved to a {@code Duration}
     * via {@link BeatUnitMapping} when the metric modulation is built.
     */
    private record MetronomeNote(String token, int dotCount) {}
}
