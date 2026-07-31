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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

/**
 * Per-note range-span pending anchors (slide, beam, tie, tuplet, trill).
 *
 * <p>Each per-note span is a run over consecutive notes in [anchor, end]. The
 * writer distributes a begin/continue/end (or start/stop) marker per note; the
 * reader re-collapses a maximal marked run into one RangeElement by holding
 * the run's anchor note in a pending field and pairing it with the closing
 * marker when the next matching note is resolved. The per-note markers
 * themselves come from {@link NoteAccumulator#spanMarkers()}.
 *
 * <p>Per-span pending-anchor pairing (built per note, like the slide pairing):
 *
 * <pre>
 *   SLIDE    start ─► pendingSlideStart;      stop ─► setGlissando() on start
 *   BEAM     begin ─► pendingBeamStart;       end  ─► Beam(anchor, note)
 *   TIED     start ─► pendingTieStart;        stop ─► Tie(anchor, note)
 *              (interior note: stop then start closes one pair and opens
 *               the next, producing one Tie per adjacent pair in the chain)
 *   TUPLET   start ─► pendingTupletStart (ratio captured from
 *                     &lt;time-modification&gt;); stop ─► Tuplet(anchor, note, ratio)
 *   WAVY     start ─► pendingTrillStart;      stop ─► Trill(anchor, note)
 *              (anchor == note ─► single-note Trill(anchor))
 * </pre>
 *
 * <p>Lenient read (both directions) for slide, beam, tie, and trill: a dangling
 * start (no matching end) is dropped and logged at part-end flush; an orphan
 * stop/end (no pending anchor) is ignored and logged. A tuplet is not a
 * legitimate document state without both anchors and a span of at least two
 * non-rest notes (#518), so a dangling start, an orphan stop, or an
 * insufficient span is instead rejected with a {@link org.xml.sax.SAXException}.
 */
final class RangeSpanResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RangeSpanResolver.class);

    // Holds a <slide type="start"> note until the next note's stop is seen.
    @Nullable
    private StaffElement pendingSlideStart = null;

    // Per-span run anchors, held until the closing marker arrives (see class doc).
    @Nullable
    private StaffElement pendingBeamStart = null;

    @Nullable
    private StaffElement pendingTieStart = null;

    @Nullable
    private StaffElement pendingTupletStart = null;

    private int pendingTupletGrade = 0;
    private int pendingTupletNormalNotes = 0;
    private int pendingTupletNoteValueDots = 0;

    // Null when the anchor note's <time-modification> stated no <normal-type> —
    // the signal that the file's ratio is not trustworthy (see resolveTuplet).
    @Nullable
    private ElementType pendingTupletNoteValue = null;

    private int pendingTupletVerticalPositionSs = 0;

    @Nullable
    private StaffElement pendingTrillStart = null;

    private int pendingTrillYPositionSs = 0;

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Pairs this note's {@code <slide>} (if any) with its partner: a
     * {@code type="start"} note is held in {@link #pendingSlideStart} until the
     * next note's {@code type="stop"} is seen, at which point the held start note
     * is marked as a glissando. A glissando needs two notes, so a start is never
     * itself marked on read.
     */
    void resolveSlide(StaffElement element, NoteAccumulator.SpanMarkers markers) {
        var slideType = markers.slideType();

        if (MusicXmlTags.SLIDE_STOP.equals(slideType)) {
            if (pendingSlideStart != null) {
                pendingSlideStart.setGlissando();
                pendingSlideStart = null;
            }
            // A stop with no pending start is stray; ignore it.
        } else if (MusicXmlTags.SLIDE_START.equals(slideType)) {
            // A still-pending earlier start never received its stop — drop it.
            flushPendingSlideStart();
            pendingSlideStart = element;
        }
    }

    /**
     * Drops a dangling {@code <slide type="start">} whose matching
     * {@code type="stop"} never arrived (e.g. a truncated file). A glissando needs
     * two notes, so the start note keeps no glissando.
     */
    void flushPendingSlideStart() {
        if (pendingSlideStart != null) {
            LOG.warn("Dropping dangling <slide type=\"start\"> with no matching <slide type=\"stop\">");
            pendingSlideStart = null;
        }
    }

    /**
     * Collapses this note's primary {@code <beam number="1">} marker into the
     * active beam run: {@code begin} opens the run (holding this note as the
     * anchor), {@code continue} keeps it open, and {@code end} builds the
     * {@link Beam}. A dangling start (no {@code end}) is dropped; an orphan
     * {@code end} (no pending anchor) is ignored — both logged.
     */
    void resolveBeam(Line line, StaffElement element, NoteAccumulator.SpanMarkers markers) {
        var beamType = markers.beam1Type();

        if (beamType == null) {
            return;
        }

        if (MusicXmlTags.BEAM_BEGIN.equals(beamType)) {
            // A still-pending earlier begin never received its end — drop it.
            flushPendingBeamStart();
            pendingBeamStart = element;
        } else if (MusicXmlTags.BEAM_END.equals(beamType)) {
            if (pendingBeamStart != null) {
                line.addBeaming(new Beam(pendingBeamStart, element));
                pendingBeamStart = null;
            } else {
                LOG.warn("Ignoring <beam>end</beam> with no matching begin");
            }
        }
        // BEAM_CONTINUE keeps the run open — no action.
    }

    /**
     * Collapses this note's {@code <tied>} markers into the active tie run.
     * Stop is processed before start so an interior note (which emits stop then
     * start) closes its pair and immediately re-opens the next, producing one
     * {@link Tie} per adjacent pair in the chain. A dangling start is dropped,
     * an orphan stop ignored — both logged.
     */
    void resolveTie(Line line, StaffElement element, NoteAccumulator.SpanMarkers markers) {
        if (markers.tiedStop()) {
            if (pendingTieStart != null) {
                line.addTie(new Tie(pendingTieStart, element));
                pendingTieStart = null;
            } else {
                LOG.warn("Ignoring <tied type=\"stop\"> with no matching start");
            }
        }

        if (markers.tiedStart()) {
            flushPendingTieStart();
            pendingTieStart = element;
        }
    }

    /**
     * Collapses this note's {@code <tuplet>} bracket markers into a {@link Tuplet}.
     * Start is processed before stop so a single-note tuplet builds correctly. The
     * ratio is captured from the anchor note's {@code <time-modification>} (which
     * precedes {@code <notations>}); the repeated per-note copies on the interior
     * notes are ignored. The {@code verticalPositionSs} is restored from the start
     * marker's {@code relative-y}. Unlike the other per-note spans, a tuplet is not
     * a legitimate document state without both a start and an end anchor spanning
     * at least two non-rest notes (see #518), so a dangling start or an orphan stop
     * is rejected as a corrupt document rather than silently dropped.
     *
     * <p>{@code <normal-type>} is the trust discriminator. A file that carries it
     * states a complete {@code <time-modification>} — it was written either by this
     * writer or by another application that emits the full element — so its
     * {@code <normal-notes>} is taken at face value and the tuplet is built
     * resolved. A file without it predates this format, or states a
     * {@code <normal-type>} token this reader does not recognise; either way the
     * ratio is unknown rather than corrupt, so the tuplet is built unresolved and
     * the post-load pass derives the ratio from the beat in effect. Deriving it
     * here is impossible anyway: mid-parse, the beat may still change in a later
     * line.
     */
    void resolveTuplet(Line line, StaffElement element, NoteAccumulator.SpanMarkers markers) throws SAXException {
        if (markers.tupletStart()) {
            // A still-pending earlier start never received its stop — reject it.
            flushPendingTupletStart();
            pendingTupletStart = element;
            pendingTupletGrade = markers.actualNotes();
            pendingTupletNormalNotes = markers.normalNotes();
            pendingTupletNoteValue = statedNoteValue(markers);
            pendingTupletNoteValueDots = markers.normalDotCount();
            pendingTupletVerticalPositionSs = markers.tupletRelativeYPresent()
                ? MusicXmlUnits.tenthsToSs(markers.tupletRelativeYTenths())
                : 0;
        }

        if (markers.tupletStop()) {
            if (pendingTupletStart != null) {
                Tuplet tuplet;

                if (pendingTupletNoteValue == null) {
                    tuplet = Tuplet.withUnresolvedRatio(pendingTupletStart, element, pendingTupletGrade);
                } else {
                    tuplet = new Tuplet(pendingTupletStart, element, pendingTupletGrade,
                        pendingTupletNormalNotes, pendingTupletNoteValue, pendingTupletNoteValueDots);
                }

                if (!tuplet.hasValidSpan(line)) {
                    throw new SAXException("Corrupt document: tuplet does not span at least two non-rest notes");
                }

                if (pendingTupletVerticalPositionSs != 0) {
                    tuplet.setVerticalPositionSs(pendingTupletVerticalPositionSs);
                }

                line.addTuplet(tuplet);
                pendingTupletStart = null;
            } else {
                throw new SAXException("Corrupt document: <tuplet type=\"stop\"> with no matching start");
            }
        }
    }

    /**
     * The written note value the anchor note's {@code <time-modification>} states,
     * or {@code null} when the file states no usable one — no {@code <normal-type>},
     * a token this reader does not know, or a {@code <normal-notes>} that is not a
     * positive count. Each of those leaves the ratio to be derived rather than
     * trusted, so none of them is an error.
     */
    @Nullable
    private static ElementType statedNoteValue(NoteAccumulator.SpanMarkers markers) {
        var token = markers.normalTypeToken();

        if (token == null || markers.normalNotes() <= 0) {
            return null;
        }

        // <normal-type> names a written value, so it always maps to the pitched
        // ElementType: rest-ness and grace-ness belong to the note, not the ratio.
        return NoteTypeMapping.forTypeToken(token, false, false);
    }

    /**
     * Collapses this note's {@code <wavy-line>} markers into a {@link Trill}.
     * Start is processed before stop so a single-note trill (start and stop on one
     * note) builds the single-note {@link Trill}. The {@code yPositionSs} is
     * restored from the start marker's {@code relative-y}. A dangling start is
     * dropped, an orphan stop ignored — both logged.
     */
    void resolveTrill(Line line, StaffElement element, NoteAccumulator.SpanMarkers markers) {
        if (markers.trillStart()) {
            // A still-pending earlier start never received its stop — drop it.
            flushPendingTrillStart();
            pendingTrillStart = element;
            pendingTrillYPositionSs = markers.trillRelativeYPresent()
                ? MusicXmlUnits.tenthsToSs(markers.trillRelativeYTenths())
                : 0;
        }

        if (markers.trillStop()) {
            if (pendingTrillStart != null) {
                Trill trill;

                if (pendingTrillStart == element) {
                    trill = new Trill(pendingTrillStart);
                } else {
                    trill = new Trill(pendingTrillStart, element);
                }

                if (pendingTrillYPositionSs != 0) {
                    trill.setYPositionSs(pendingTrillYPositionSs);
                }

                line.addRangeElement(trill);
                pendingTrillStart = null;
            } else {
                LOG.warn("Ignoring <wavy-line type=\"stop\"> with no matching start");
            }
        }
    }

    /**
     * Drops any per-note range-span run still open at the end of the part — each
     * is a dangling start whose closing marker never arrived (e.g. truncated
     * input). A range needs both endpoints, so the run builds nothing. A dangling
     * tuplet start is rejected as a corrupt document rather than dropped (see
     * {@link #resolveTuplet}).
     */
    void flushPendingSpanStarts() throws SAXException {
        flushPendingBeamStart();
        flushPendingTieStart();
        flushPendingTupletStart();
        flushPendingTrillStart();
    }

    private void flushPendingBeamStart() {
        pendingBeamStart = warnIfDangling(pendingBeamStart, "Dropping dangling beam begin with no matching end");
    }

    private void flushPendingTieStart() {
        pendingTieStart = warnIfDangling(pendingTieStart, "Dropping dangling <tied type=\"start\"> with no matching stop");
    }

    private void flushPendingTupletStart() throws SAXException {
        if (pendingTupletStart != null) {
            throw new SAXException("Corrupt document: dangling <tuplet type=\"start\"> with no matching stop");
        }
    }

    private void flushPendingTrillStart() {
        pendingTrillStart = warnIfDangling(pendingTrillStart, "Dropping dangling <wavy-line type=\"start\"> with no matching stop");
    }

    /**
     * Logs {@code warnMessage} when {@code pending} is a dangling span start
     * (non-null, with no closing marker) and returns {@code null} so the caller
     * can clear its field. Shared by the four {@code flushPending*Start} methods,
     * which differ only in the field cleared and the message logged.
     */
    @Nullable
    private <T> T warnIfDangling(@Nullable T pending, String warnMessage) {
        if (pending != null) {
            LOG.warn(warnMessage);
        }

        return null;
    }
}
