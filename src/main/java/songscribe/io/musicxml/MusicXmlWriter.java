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

import java.io.PrintWriter;
import java.time.Clock;
import java.util.List;

import org.jspecify.annotations.Nullable;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.XML;
import songscribe.io.musicxml.MusicXmlSpanIndex.EndingMarker;

public final class MusicXmlWriter {

    private MusicXmlWriter() {}

    /**
     * Writes {@code song} to {@code pw} as MusicXML, using {@code fonts} for the
     * document-level font roles. Uses the system-default {@link Clock} for the
     * write-forward {@code <rights>} year and {@code <encoding-date>}.
     *
     * @param song  the song to serialize
     * @param fonts the document fonts to emit under {@code <defaults>}/{@code <credit>}
     * @param pw    the writer to emit the MusicXML document to
     */
    public static void writeSong(Song song, DocumentFontsHolder fonts, PrintWriter pw) {
        writeSong(song, fonts, pw, Clock.systemDefaultZone());
    }

    /**
     * Writes {@code song} to {@code pw} as MusicXML. The {@code clock} is
     * injectable so the write-forward {@code <rights>} year and
     * {@code <encoding-date>} are deterministic under test.
     *
     * @param song  the song to serialize
     * @param fonts the document fonts to emit under {@code <defaults>}/{@code <credit>}
     * @param pw    the writer to emit the MusicXML document to
     * @param clock the clock supplying the current date for write-forward fields
     */
    public static void writeSong(Song song, DocumentFontsHolder fonts, PrintWriter pw, Clock clock) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        XML.resetIndent();
        XML.writeBeginTag(pw, MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.ATTR_VERSION, MusicXmlTags.VERSION_VALUE);
        XML.indent();

        // Resolve every clock- and date-derived header value once, so the
        // <miscellaneous> block and the <credit> list share a single source for
        // the composition/lyrics dates and the <rights>/<encoding-date> strings.
        var headerText = MusicXmlHeaderWriter.HeaderText.of(song, clock);

        MusicXmlHeaderWriter.writeMovementInfo(song, pw);
        MusicXmlHeaderWriter.writeIdentification(song, fonts, headerText, pw);
        MusicXmlHeaderWriter.writeDefaults(song, fonts, pw);
        MusicXmlHeaderWriter.writeCredits(song, fonts, headerText, pw);

        XML.writeBeginTag(pw, MusicXmlTags.PART_LIST);
        XML.indent();

        // <score-part> and its child are emitted inline on one line.
        XML.printIndent(pw);
        pw.println('<' + MusicXmlTags.SCORE_PART + ' ' + MusicXmlTags.ATTR_ID + "=\"" + MusicXmlTags.PART_ID + "\"><part-name></part-name></" + MusicXmlTags.SCORE_PART + '>');

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PART_LIST);

        XML.writeBeginTag(pw, MusicXmlTags.PART, MusicXmlTags.ATTR_ID, MusicXmlTags.PART_ID);
        XML.indent();

        if (song.lineCount() == 0) {
            writeEmptySongMeasure(song, pw);
        } else {
            writeLineDrivenMeasures(song, pw);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PART);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.SCORE_PARTWISE);
    }

    /**
     * Empty-song fallback: a single attributes-only measure with no
     * {@code <print>} and no {@code <barline>}, matching Phase 1 behavior.
     */
    private static void writeEmptySongMeasure(Song song, PrintWriter pw) {
        MusicXmlMeasureWriter.openMeasure(pw, MusicXmlUnits.FIRST_MEASURE_NUMBER);
        MusicXmlMeasureWriter.writeAttributes(song, pw);
        MusicXmlMeasureWriter.closeMeasure(pw);
    }

    /**
     * Line-driven emission: each {@link Line} contributes one or more
     * {@code <measure>}s, segmented at every barline/repeat element and at
     * every line break.
     */
    private static void writeLineDrivenMeasures(Song song, PrintWriter pw) {
        var measureNumber = 0;

        // The key signature currently in effect. Measure 1 emits the song default
        // (see writeAttributes); each later line whose effective key differs from
        // this running value emits a key-only <attributes> and advances it. A
        // MusicXML key persists until restated, so lines that keep the running key
        // emit nothing — the reader carries it forward.
        var runningFifths = KeySignatureMapping.toFifths(song.getDefaultKeyType(), song.getDefaultKeyAccidentalCount());

        // The first element of the first line anchors the song base tempo (see
        // Line.isInitialTempoAnchor): its emitted tempo is its own
        // TempoChangeAttachment if present, else song.getTempo().
        var firstSongElement = song.initialTempoAnchor();

        for (var line : song.getLines()) {
            // Glissandos are intra-line — they cannot span a system break.
            // Reset the pending-stop state at the start of each line so a
            // dangling glissando from a malformed song does not bleed across.
            StaffElement.@Nullable Glissando pendingGlissando = null;

            // Open the line-starting measure. Every such measure carries a system-
            // break marker so the reader has one uniform rule:
            // new-system="yes" always starts a new line.
            measureNumber++;
            MusicXmlMeasureWriter.openMeasure(pw, measureNumber);
            MusicXmlMeasureWriter.writePrintNewSystem(pw);

            if (measureNumber == MusicXmlUnits.FIRST_MEASURE_NUMBER) {
                MusicXmlMeasureWriter.writeAttributes(song, pw);
            } else {
                var lineFifths = MusicXmlMeasureWriter.effectiveKeyFifths(song, line);

                if (lineFifths != runningFifths) {
                    MusicXmlMeasureWriter.writeKeyOnlyAttributes(pw, lineFifths);
                    runningFifths = lineFifths;
                }
            }

            // measureOpen tracks whether the current measure tag is still open.
            // A measure is open after we write its opening tag and closed after
            // we write its closing tag.
            var measureOpen = true;

            var elements = line.getElements();
            var lastElement = elements.isEmpty() ? null : elements.getLast();

            // Build the per-element span index once per line. Anchor/end indices
            // for all six span types are resolved here so the element loop can do
            // O(1) lookups instead of calling getAnchorElementIndex() (ArrayList.indexOf,
            // O(n)) per element per span.
            var spanIndex = MusicXmlSpanIndex.buildSpanIndex(line);

            // A note-terminated ending end (issue #306) whose boundary note is the
            // line's last element folds its <ending type="discontinue"> onto the
            // end-of-line invisible right barline, avoiding a redundant second one.
            List<EndingMarker> lastNoteEndingMarkers = List.of();

            for (var i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                var type = element.getType();
                var markers = spanIndex[i];

                if (type == ElementType.REPEAT_LEFT) {
                    // REPEAT_LEFT opens a new measure (the forward-repeat barline
                    // is a left barline, not a right barline). Close the current
                    // measure with an invisible right barline to preserve the
                    // line boundary, then open the new measure. An ending anchored
                    // (or ended) on the REPEAT_LEFT rides on the forward-left barline.
                    MusicXmlMeasureWriter.writeInvisibleRightBarline(pw);
                    measureNumber = MusicXmlMeasureWriter.openForwardRepeatMeasure(pw, measureNumber, markers.endingLeftBarlineMarkers());

                } else if (type == ElementType.REPEAT_LEFT_RIGHT) {
                    // REPEAT_LEFT_RIGHT straddles a measure boundary:
                    // - a backward-repeat right barline closes the current measure,
                    // - a forward-repeat left barline opens the next one.
                    // The reader reconstructs the REPEAT_LEFT_RIGHT from this pair.
                    // For an ending split here, <ending number="1" type="stop">
                    // rides on the backward-right barline and <ending number="2"
                    // type="start"> on the forward-left barline.
                    MusicXmlMeasureWriter.writeBackwardRepeatRightBarline(pw, markers.endingRightBarlineMarkers());
                    measureNumber = MusicXmlMeasureWriter.openForwardRepeatMeasure(pw, measureNumber, markers.endingLeftBarlineMarkers());

                } else if (type.isBarLine() || type.isRepeat()) {
                    // All other barline/repeat types close the current measure
                    // with a right barline. If this is not the last element on the
                    // line, a new measure is opened immediately for subsequent
                    // elements. If it is the last element, the outer end-of-line
                    // check will not emit a spurious empty measure.
                    var entry = BarlineStyleMapping.forElementType(type);
                    // entry is non-null here: REPEAT_LEFT and REPEAT_LEFT_RIGHT
                    // are handled in the branches above; all remaining barline/
                    // repeat types have forward-map entries.
                    if (entry == null) {
                        continue;
                    }

                    MusicXmlMeasureWriter.writeBarline(pw, entry, markers.endingRightBarlineMarkers());
                    MusicXmlMeasureWriter.closeMeasure(pw);
                    measureOpen = false;

                    // Peek ahead: if this barline is not the last element on the
                    // line, there are more elements to place, so open the next
                    // measure now. If it is the last element, measureOpen stays
                    // false and the end-of-line block below is skipped — no
                    // spurious empty measure is emitted.
                    if (element != lastElement) {
                        measureNumber++;
                        MusicXmlMeasureWriter.openMeasure(pw, measureNumber);
                        measureOpen = true;

                        // A REPEAT_RIGHT split (not its own dedicated branch) closes
                        // volta 1 on the right barline above; volta 2 begins in this
                        // new measure, so its <ending number="2" type="start"> rides
                        // on an invisible (style-none) left barline — the first child
                        // of the measure per the barline-location schema rule.
                        var leftBarlineMarkers = markers.endingLeftBarlineMarkers();

                        if (!leftBarlineMarkers.isEmpty()) {
                            MusicXmlMeasureWriter.writeInvisibleLeftBarline(pw, leftBarlineMarkers);
                        }
                    }

                } else //noinspection StatementWithEmptyBody
                    if (type.isBreathMark()) {
                    // Already serialized inside the preceding note's <notations>.
                    // Skip here so the breath mark is not emitted a second time.
                    // A breath mark cannot anchor or end an ending, so it never
                    // carries ending markers to drain.

                } else {
                    // Note, rest, grace, or other element. Emit a <note> only when
                    // a <type> token exists; other types (SLIDE standalone, etc.)
                    // are silently skipped via the null-token guard.
                    var typeToken = NoteTypeMapping.typeToken(type);

                    if (typeToken != null) {
                        // A note-anchored ending start (issue #306) has no barline
                        // element to host it, so it rides on an invisible left
                        // barline emitted immediately before the note.
                        MusicXmlMeasureWriter.writeNoteAnchoredEndingStart(pw, markers);

                        // A tempo <direction> precedes the note it marks. The first
                        // element of the first line carries the song base tempo; any
                        // element with its own TempoChangeAttachment carries a
                        // per-note tempo.
                        var tempo = tempoForElement(song, firstSongElement, element);

                        if (tempo != null) {
                            MusicXmlDirectionWriter.writeTempoDirection(pw, tempo);
                        }

                        // A metric-modulation <direction> also precedes the note it
                        // marks (the note carrying the BeatChangeAttachment), so the
                        // reader binds it to the next note with the same rule.
                        var beatChangeAttachment = element.findAttachment(BeatChangeAttachment.class);

                        if (beatChangeAttachment != null) {
                            MusicXmlDirectionWriter.writeMetricModulationDirection(pw, beatChangeAttachment.getBeatChange());
                        }

                        // Hairpin wedges bind to the next <note>: both the start
                        // wedge (on the anchor) and the stop wedge (on the end) are
                        // emitted as <direction> siblings immediately before their
                        // bound note, giving the reader one uniform look-ahead rule.
                        MusicXmlHairpinWriter.writeHairpinWedges(pw, markers);

                        // An annotation <direction placement="above|below"> also
                        // binds to the next note, so it too is emitted immediately
                        // before its <note> (after the tempo/wedge directions).
                        var annotationAttachment = element.findAttachment(AnnotationAttachment.class);

                        if (annotationAttachment != null) {
                            MusicXmlDirectionWriter.writeAnnotationDirection(pw, annotationAttachment.getAnnotation());
                        }

                        var nextElement = (i + 1 < elements.size()) ? elements.get(i + 1) : null;
                        var nextIsBreathMark = nextElement != null && nextElement.getType().isBreathMark();
                        var ctx = new NoteWriteContext(
                            element, typeToken, nextIsBreathMark,
                            pendingGlissando, markers.noteMarkers()
                        );
                        MusicXmlNoteWriter.writeNote(pw, ctx);
                        // getGlissando() is null unless this note starts a glissando.
                        pendingGlissando = element.getGlissando();

                        // A note-terminated ending end (issue #306) has no barline
                        // element to host its <ending ... type="discontinue">. When
                        // the boundary note is the line's last element, defer the
                        // marker to the end-of-line invisible right barline; else
                        // emit an invisible right barline immediately after the note.
                        lastNoteEndingMarkers = MusicXmlMeasureWriter.writeOrDeferNoteEndingEnd(
                            pw, markers, element, lastElement, lastNoteEndingMarkers);
                    }
                }
            }

            // If the current measure is still open at end of line, the line break
            // ends it. An invisible right barline marks the break so the reader can
            // reconstruct the line boundary without inserting a barline StaffElement.
            // If measureOpen is false, the last element was a real barline that
            // already closed its measure — no spurious empty measure is emitted.
            if (measureOpen) {
                MusicXmlMeasureWriter.writeInvisibleRightBarline(pw, lastNoteEndingMarkers);
                MusicXmlMeasureWriter.closeMeasure(pw);
            }
        }
    }

    /**
     * Resolves the tempo to emit before {@code element}, or null when it carries
     * none. An element's own {@link TempoChangeAttachment} is a per-note tempo;
     * the first element of the first line falls back to {@code song.getTempo()}
     * (mirroring {@code Line.attachInitialTempoIfNeeded} at write time so the base
     * tempo is emitted even for a not-yet-materialized song).
     */
    private static @Nullable Tempo tempoForElement(
            Song song, @Nullable StaffElement firstSongElement, StaffElement element) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);

        if (attachment != null) {
            return attachment.getTempo();
        }

        if (element == firstSongElement) {
            return song.getTempo();
        }

        return null;
    }
}
