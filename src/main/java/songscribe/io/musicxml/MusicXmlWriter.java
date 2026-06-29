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
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.io.XML;

public final class MusicXmlWriter {

    // DIVISIONS is defined in NoteTypeMapping (which owns the tick math).
    // DIVISIONS = 480 ensures that the smallest representable note fraction
    // — a double-dotted 32nd — produces an exact integer tick count:
    //   (480 / 8) × 7/4  =  60 × 7/4  =  105 ticks  (exact)
    private static final int DIVISIONS = NoteTypeMapping.DIVISIONS;

    // Measure numbering starts at 1 (MusicXML spec requires positive integers).
    private static final int FIRST_MEASURE_NUMBER = 1;

    // Each diatonic step = ½ staff space = 5 tenths.  Used to compute the
    // grace-note stem-tip Y: staffPosition × -5 gives tenths above the middle
    // staff line (positive = up in MusicXML; positions increase downward in
    // SongScribe, so the sign is negated).
    private static final int TENTHS_PER_STAFF_POSITION = 5;

    // Standard upward stem extension above a grace notehead: 3.5 staff spaces
    // = 35 tenths. Added to the note's tenths-from-middle-line to give the
    // stem-tip default-y.
    private static final int GRACE_STEM_EXTENSION_TENTHS = 35;

    private MusicXmlWriter() {}

    public static void writeSong(Song song, PrintWriter pw) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        XML.resetIndent();
        XML.writeBeginTag(pw, MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.ATTR_VERSION, MusicXmlTags.VERSION_VALUE);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.PART_LIST);
        XML.indent();

        // <score-part> and its child are emitted inline on one line.
        XML.printIndent(pw);
        pw.println("<" + MusicXmlTags.SCORE_PART + " " + MusicXmlTags.ATTR_ID + "=\"" + MusicXmlTags.PART_ID + "\"><part-name></part-name></" + MusicXmlTags.SCORE_PART + ">");

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
        openMeasure(pw, FIRST_MEASURE_NUMBER);
        writeAttributes(song, pw);
        closeMeasure(pw);
    }

    /**
     * Line-driven emission: each {@link Line} contributes one or more
     * {@code <measure>}s, segmented at every barline/repeat element and at
     * every line break.
     */
    private static void writeLineDrivenMeasures(Song song, PrintWriter pw) {
        int measureNumber = 0;

        for (Line line : song.getLines()) {
            // Glissandos are intra-line — they cannot span a system break.
            // Reset the pending-stop state at the start of each line so a
            // dangling glissando from a malformed song does not bleed across.
            StaffElement.@Nullable Glissando pendingGlissando = null;

            // Open the line-starting measure. Every such measure carries a system-
            // break marker so the reader has one uniform rule:
            // new-system="yes" always starts a new line.
            measureNumber++;
            openMeasure(pw, measureNumber);
            writePrintNewSystem(pw);

            if (measureNumber == FIRST_MEASURE_NUMBER) {
                writeAttributes(song, pw);
            }

            // measureOpen tracks whether the current measure tag is still open.
            // A measure is open after we write its opening tag and closed after
            // we write its closing tag.
            boolean measureOpen = true;

            var elements = line.getElements();
            var lastElement = elements.isEmpty() ? null : elements.getLast();

            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                var type = element.getType();

                if (type == ElementType.REPEAT_LEFT) {
                    // REPEAT_LEFT opens a new measure (the forward-repeat barline
                    // is a left barline, not a right barline). Close the current
                    // measure with an invisible right barline to preserve the
                    // line boundary, then open the new measure.
                    writeInvisibleRightBarline(pw);
                    measureNumber = openForwardRepeatMeasure(pw, measureNumber);

                } else if (type == ElementType.REPEAT_LEFT_RIGHT) {
                    // REPEAT_LEFT_RIGHT straddles a measure boundary:
                    // - a backward-repeat right barline closes the current measure,
                    // - a forward-repeat left barline opens the next one.
                    // The reader reconstructs the REPEAT_LEFT_RIGHT from this pair.
                    writeBackwardRepeatRightBarline(pw);
                    measureNumber = openForwardRepeatMeasure(pw, measureNumber);

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

                    writeBarline(pw, entry);
                    closeMeasure(pw);
                    measureOpen = false;

                    // Peek ahead: if this barline is not the last element on the
                    // line, there are more elements to place, so open the next
                    // measure now. If it is the last element, measureOpen stays
                    // false and the end-of-line block below is skipped — no
                    // spurious empty measure is emitted.
                    if (element != lastElement) {
                        measureNumber++;
                        openMeasure(pw, measureNumber);
                        measureOpen = true;
                    }

                } else if (type.isBreathMark()) {
                    // Already serialized inside the preceding note's <notations>.
                    // Skip here so the breath mark is not emitted a second time.

                } else {
                    // Note, rest, grace, or other element. Emit a <note> only when
                    // a <type> token exists; other types (SLIDE standalone, etc.)
                    // are silently skipped via the null-token guard.
                    var typeToken = NoteTypeMapping.typeToken(type);

                    if (typeToken != null) {
                        var nextElement = (i + 1 < elements.size()) ? elements.get(i + 1) : null;
                        var nextIsBreathMark = nextElement != null && nextElement.getType().isBreathMark();
                        writeNote(pw, element, typeToken, nextIsBreathMark, pendingGlissando);
                        // getGlissando() is null unless this note starts a glissando.
                        pendingGlissando = element.getGlissando();
                    }
                }
            }

            // If the current measure is still open at end of line, the line break
            // ends it. An invisible right barline marks the break so the reader can
            // reconstruct the line boundary without inserting a barline StaffElement.
            // If measureOpen is false, the last element was a real barline that
            // already closed its measure — no spurious empty measure is emitted.
            if (measureOpen) {
                writeInvisibleRightBarline(pw);
                closeMeasure(pw);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Note emission
    //
    // Strict <note> child-order pipeline (MusicXML 4.0, musicxml.xsd §5174–5223):
    //
    //   <note [default-x="…"] [relative-x="…"]>
    //     (<grace slash="no"/>)?             — grace notes only, no steal-time-following
    //     (<rest/>  |  <pitch>…</pitch>)    — rest has no pitch; grace has pitch
    //     (<duration>…</duration>)?         — omitted for grace (zero playback time)
    //     <type>…</type>
    //     (<dot/>)*                          — dotCount times; grace: always 0
    //     (<accidental …>…</accidental>)?   — not for rests
    //     (<stem [default-y="…"]>…</stem>)? — grace: always "up"; otherwise only when !auto
    //     (<notations>                       — emitted only when non-empty
    //       (<slide …/>)*                   — stop slide before start slide
    //       (<fermata/>)?
    //       (<articulations>
    //         (<accent/>|<staccato/>|<falloff/>|<breath-mark/>)*
    //       </articulations>)?
    //       (<dynamics><…/></dynamics>)?
    //     </notations>)?
    //   </note>
    // -------------------------------------------------------------------------

    private static void writeNote(
            PrintWriter pw,
            StaffElement note,
            String typeToken,
            boolean nextIsBreathMark,
            StaffElement.@Nullable Glissando pendingStopGlissando
    ) {
        var type = note.getType();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();

        // Compute position in tenths.
        // default-x: the base layout position (getXSs() stores the layout-assigned
        //   position; for notes this is set per the new layout system).
        // relative-x: the user-set horizontal offset, emitted only when non-zero
        //   (mirrors the legacy writeElement guard).
        var baseXTenths = note.getXSs() * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
        var xOffsetPx = note.getXOffsetPx();
        var relativeXTenths = ScaleContext.pxToSs(xOffsetPx) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;

        // Open <note> tag with optional position attributes. relative-x is emitted
        // only when the note carries a user offset (mirrors the legacy guard).
        if (xOffsetPx != 0) {
            XML.writeBeginTag(pw, MusicXmlTags.NOTE,
                MusicXmlTags.ATTR_DEFAULT_X, formatTenths(baseXTenths),
                MusicXmlTags.ATTR_RELATIVE_X, formatTenths(relativeXTenths)
            );
        } else {
            XML.writeBeginTag(pw, MusicXmlTags.NOTE,
                MusicXmlTags.ATTR_DEFAULT_X, formatTenths(baseXTenths)
            );
        }

        XML.indent();

        // 1. <grace slash="no"/> — grace notes only.
        //    No steal-time-following: SongScribe gives grace notes zero playback
        //    duration and never shortens the host, so emitting a steal would
        //    misrepresent the song.
        if (isGrace) {
            XML.writeEmptyTag(pw, MusicXmlTags.GRACE, MusicXmlTags.ATTR_SLASH, MusicXmlTags.NO);
        }

        // 2. <rest/> | <pitch> — rest has no pitch; pitched and grace notes do.
        if (isRest) {
            XML.writeEmptyTag(pw, MusicXmlTags.REST);
        } else {
            writePitch(pw, note);
        }

        // 3. <duration> — omitted for grace notes (zero playback time).
        if (NoteTypeMapping.hasDuration(type)) {
            var ticks = NoteTypeMapping.ticks(type, note.getDotCount());
            XML.writeValue(pw, MusicXmlTags.DURATION, Integer.toString(ticks));
        }

        // 4. <type>
        XML.writeValue(pw, MusicXmlTags.NOTE_TYPE, typeToken);

        // 5. <dot/>×n — grace notes never carry dots.
        var dotCount = isGrace ? 0 : note.getDotCount();

        for (var i = 0; i < dotCount; i++) {
            XML.writeEmptyTag(pw, MusicXmlTags.DOT);
        }

        // 6. <accidental> — not for rests.
        if (!isRest) {
            writeAccidental(pw, note);
        }

        // 7. <stem>
        writeStem(pw, note, isGrace);

        // 8. <notations>
        writeNotations(pw, note, nextIsBreathMark, pendingStopGlissando);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.NOTE);
    }

    // -------------------------------------------------------------------------
    // <pitch>: step / alter (when non-zero) / octave via the Phase 2 helper.
    // -------------------------------------------------------------------------

    private static void writePitch(PrintWriter pw, StaffElement note) {
        var pitch = PitchSpelling.spell(note.getStaffPosition());
        var alterSemitones = PitchSpelling.soundingAlterFor(note);

        XML.writeBeginTag(pw, MusicXmlTags.PITCH);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.STEP, String.valueOf(pitch.step()));

        if (alterSemitones != 0) {
            XML.writeValue(pw, MusicXmlTags.ALTER, Integer.toString(alterSemitones));
        }

        XML.writeValue(pw, MusicXmlTags.OCTAVE, Integer.toString(pitch.octave()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PITCH);
    }

    // -------------------------------------------------------------------------
    // <accidental>: emitted only when the note has an explicit accidental glyph.
    // cautionary="yes" parentheses="yes" are driven by isAccidentalInParentheses().
    // DOUBLE_NATURAL has no MusicXML mapping and is silently skipped.
    // -------------------------------------------------------------------------

    private static void writeAccidental(PrintWriter pw, StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return;
        }

        var entry = AccidentalMapping.forAccidental(accidental);

        if (entry == null) {
            // DOUBLE_NATURAL has no MusicXML representation — skip silently.
            return;
        }

        // A parenthesized accidental is a cautionary: cautionary="yes" parentheses="yes".
        if (note.isAccidentalInParentheses()) {
            XML.writeValue(pw, MusicXmlTags.ACCIDENTAL, entry.token(),
                MusicXmlTags.ATTR_CAUTIONARY, MusicXmlTags.YES,
                MusicXmlTags.ATTR_PARENTHESES, MusicXmlTags.YES
            );
        } else {
            XML.writeValue(pw, MusicXmlTags.ACCIDENTAL, entry.token());
        }
    }

    // -------------------------------------------------------------------------
    // <stem>: grace notes always emit "up" with a computed stem-tip default-y;
    // non-grace notes emit the direction only when stem direction is manual
    // (isStemDirectionAuto() == false).
    // -------------------------------------------------------------------------

    private static void writeStem(PrintWriter pw, StaffElement note, boolean isGrace) {
        if (isGrace) {
            // Grace-note stems always go up. Emit a computed stem-tip position so
            // external renderers can draw the stem without re-running layout.
            // Stem-tip tenths = note-head tenths above middle line + extension.
            //   noteHeadTenths = staffPosition × -TENTHS_PER_STAFF_POSITION
            //   (negative because staffPosition increases downward but MusicXML Y
            //    is positive upward, and origin B4 = staffPosition 0 = middle line)
            var stemTipTenths = note.getStaffPosition() * -TENTHS_PER_STAFF_POSITION + GRACE_STEM_EXTENSION_TENTHS;
            XML.writeValue(pw, MusicXmlTags.STEM, MusicXmlTags.STEM_UP,
                MusicXmlTags.ATTR_DEFAULT_Y, Integer.toString(stemTipTenths)
            );

        } else if (!note.isStemDirectionAuto()) {
            var stemDir = note.isUpper() ? MusicXmlTags.STEM_UP : MusicXmlTags.STEM_DOWN;
            XML.writeValue(pw, MusicXmlTags.STEM, stemDir);
        }
    }

    // -------------------------------------------------------------------------
    // <notations>: emitted only when at least one child will be written.
    //
    // Schema order within <notations> (xs:choice — any order is valid):
    //   <slide type="stop"/>?   — destination note of a glissando
    //   <slide type="start"/>?  — source note of a glissando
    //   <fermata/>?
    //   <articulations>?        — accent, staccato, falloff, breath-mark
    //   <dynamics>?
    // -------------------------------------------------------------------------

    private static void writeNotations(
            PrintWriter pw,
            StaffElement note,
            boolean nextIsBreathMark,
            StaffElement.@Nullable Glissando pendingStopGlissando
    ) {
        var type = note.getType();
        var isGrace = type.isGraceNote();

        // Glissando slides are not applicable to grace notes.
        var startGlissando = isGrace ? null : note.getGlissando();
        var hasSlideStop = !isGrace && pendingStopGlissando != null;
        var hasSlideStart = startGlissando != null;

        var articulations = note.getArticulations();
        var hasFermata = note.findAttachment(FermataAttachment.class) != null;
        var dynamic = note.findAttachment(DynamicAttachment.class);

        var hasArticulationsBlock = !articulations.isEmpty() || note.hasFall() || nextIsBreathMark;
        var hasNotations = hasSlideStop || hasSlideStart || hasFermata || hasArticulationsBlock || dynamic != null;

        if (!hasNotations) {
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.NOTATIONS);
        XML.indent();

        // <slide type="stop"> on the destination note of a glissando.
        // Carries the computed end-point coordinates for external-renderer
        // fidelity (write-forward only; the reader ignores them).
        if (hasSlideStop) {
            writeSlide(pw, MusicXmlTags.SLIDE_STOP, pendingStopGlissando);
        }

        // <slide type="start"> on the source note of a glissando.
        // Carries the computed start-point coordinates (write-forward only).
        if (hasSlideStart) {
            writeSlide(pw, MusicXmlTags.SLIDE_START, startGlissando);
        }

        // <fermata/>
        if (hasFermata) {
            XML.writeEmptyTag(pw, MusicXmlTags.FERMATA);
        }

        // <articulations>: accent, staccato, fall (falloff), breath-mark.
        if (hasArticulationsBlock) {
            XML.writeBeginTag(pw, MusicXmlTags.ARTICULATIONS);
            XML.indent();

            for (var articulation : articulations) {
                switch (articulation.getType()) {
                    case ACCENT -> XML.writeEmptyTag(pw, MusicXmlTags.ACCENT);
                    case STACCATO -> XML.writeEmptyTag(pw, MusicXmlTags.STACCATO);
                }
            }

            if (note.hasFall()) {
                XML.writeEmptyTag(pw, MusicXmlTags.FALLOFF);
            }

            if (nextIsBreathMark) {
                XML.writeEmptyTag(pw, MusicXmlTags.BREATH_MARK);
            }

            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.ARTICULATIONS);
        }

        // <dynamics>
        if (dynamic != null) {
            XML.writeBeginTag(pw, MusicXmlTags.DYNAMICS);
            XML.indent();
            XML.writeEmptyTag(pw, dynamic.getType().getSymbol());
            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.DYNAMICS);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.NOTATIONS);
    }

    // -------------------------------------------------------------------------
    // <slide> helper: emits a start or stop slide with optional endpoint
    // coordinates from the glissando's cached render geometry.
    //
    // The glissando lives on the *source* note via StaffElement.slide.
    // For the start slide: glissando.cachedStartX/Y is the endpoint.
    // For the stop slide: the end is cachedStartX + cachedLength × cos/sin.
    // Coordinates are in staff-space units; multiply by TENTHS_PER_STAFF_SPACE
    // to produce MusicXML tenths.  These are write-forward only — the reader
    // ignores them and re-derives geometry from layout.
    // -------------------------------------------------------------------------

    private static void writeSlide(
            PrintWriter pw,
            String slideType,
            StaffElement.@Nullable Glissando glissando
    ) {
        if (glissando == null || !glissando.hasCachedGeometry) {
            // No cached geometry available — emit a minimal slide without coordinates.
            XML.writeEmptyTag(pw, MusicXmlTags.SLIDE,
                MusicXmlTags.ATTR_TYPE, slideType,
                MusicXmlTags.ATTR_LINE_TYPE, MusicXmlTags.LINE_SOLID
            );
            return;
        }

        double xSs;
        double ySs;

        // The stop slide's endpoint is the *end* of the glissando line; the start
        // slide's is the line's start.
        var isStop = MusicXmlTags.SLIDE_STOP.equals(slideType);

        if (isStop) {
            // The stop slide's default-x/y is the *end* of the glissando line,
            // computed from the start-note's cached geometry.
            xSs = glissando.cachedStartX + glissando.cachedLength * glissando.cachedCos;
            ySs = glissando.cachedStartY + glissando.cachedLength * glissando.cachedSin;
        } else {
            xSs = glissando.cachedStartX;
            ySs = glissando.cachedStartY;
        }

        XML.writeEmptyTag(pw, MusicXmlTags.SLIDE,
            MusicXmlTags.ATTR_TYPE, slideType,
            MusicXmlTags.ATTR_LINE_TYPE, MusicXmlTags.LINE_SOLID,
            MusicXmlTags.ATTR_DEFAULT_X, formatTenths(xSs * MusicXmlTags.TENTHS_PER_STAFF_SPACE),
            MusicXmlTags.ATTR_DEFAULT_Y, formatTenths(ySs * MusicXmlTags.TENTHS_PER_STAFF_SPACE)
        );
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Formats a MusicXML tenths value as a two-decimal-place string.
     * MusicXML tenths are decimal numbers; two decimal places are sufficient
     * precision for all position values. {@link Locale#ROOT} forces a period
     * decimal separator so the output stays valid {@code xs:decimal} regardless
     * of the JVM default locale.
     */
    private static String formatTenths(double tenths) {
        return String.format(Locale.ROOT, "%.2f", tenths);
    }

    /**
     * Closes the current measure, increments the measure counter, opens a new
     * measure, and writes the forward-repeat left barline into it. Returns the
     * updated measure number.
     */
    private static int openForwardRepeatMeasure(PrintWriter pw, int measureNumber) {
        closeMeasure(pw);
        measureNumber++;
        openMeasure(pw, measureNumber);
        writeForwardRepeatLeftBarline(pw);
        return measureNumber;
    }

    /**
     * Writes {@code <measure number="N">} at the current indent and pushes one
     * level so subsequent measure-body content is indented correctly.
     */
    private static void openMeasure(PrintWriter pw, int measureNumber) {
        XML.writeBeginTag(pw, MusicXmlTags.MEASURE, MusicXmlTags.ATTR_NUMBER, Integer.toString(measureNumber));
        XML.indent();
    }

    /**
     * Pops one indent level and writes the {@code </measure>} closing tag.
     */
    private static void closeMeasure(PrintWriter pw) {
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.MEASURE);
    }

    // -------------------------------------------------------------------------
    // Attribute block
    // -------------------------------------------------------------------------

    private static void writeAttributes(Song song, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.ATTRIBUTES);
        XML.indent();

        XML.writeValue(pw, "divisions", Integer.toString(DIVISIONS));

        // <key> with inline child <fifths>
        // Encode as signed fifths: negative for flats (MusicXML convention).
        int fifths = song.getDefaultKeyType() == KeyType.FLATS
            ? -song.getDefaultKeyAccidentalCount()
            : song.getDefaultKeyAccidentalCount();
        XML.printIndent(pw);
        pw.println("<key><fifths>" + fifths + "</fifths></key>");

        // <time print-object="no"> with inline self-closing child <senza-misura/>
        XML.printIndent(pw);
        pw.println("<time print-object=\"no\"><senza-misura/></time>");

        // <clef> with inline children
        XML.printIndent(pw);
        pw.println("<clef><sign>G</sign><line>2</line></clef>");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ATTRIBUTES);
    }

    // -------------------------------------------------------------------------
    // System-break marker
    // -------------------------------------------------------------------------

    private static void writePrintNewSystem(PrintWriter pw) {
        XML.writeEmptyTag(pw, MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES);
    }

    // -------------------------------------------------------------------------
    // Barline helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a forward-repeat left barline (heavy-light style, forward direction).
     */
    private static void writeForwardRepeatLeftBarline(PrintWriter pw) {
        writeBarlineFor(pw, ElementType.REPEAT_LEFT);
    }

    /**
     * Emits a backward-repeat right barline (light-heavy style, backward direction).
     */
    private static void writeBackwardRepeatRightBarline(PrintWriter pw) {
        writeBarlineFor(pw, ElementType.REPEAT_RIGHT);
    }

    /**
     * Looks up the {@link BarlineStyleMapping.BarlineEntry} for the given
     * {@link ElementType} and delegates to {@link #writeBarline(PrintWriter, BarlineStyleMapping.BarlineEntry)}.
     * The type must have a forward-map entry; types without one (e.g.
     * {@code REPEAT_LEFT_RIGHT}) are handled by their own callers before this
     * method is reached.
     */
    private static void writeBarlineFor(PrintWriter pw, ElementType type) {
        var entry = BarlineStyleMapping.forElementType(type);

        if (entry == null) {
            return;
        }

        writeBarline(pw, entry);
    }

    /**
     * Emits a {@code <barline>} using the location stored in the
     * {@link BarlineStyleMapping.BarlineEntry}.
     */
    private static void writeBarline(PrintWriter pw, BarlineStyleMapping.BarlineEntry entry) {
        writeBarline(pw, entry.location(), entry.barStyle(), entry.repeatDirection());
    }

    /** Emits {@code <barline location="right"><bar-style>none</bar-style></barline>}. */
    private static void writeInvisibleRightBarline(PrintWriter pw) {
        writeBarline(pw, BarlineStyleMapping.LOCATION_RIGHT, BarlineStyleMapping.BAR_STYLE_NONE, null);
    }

    /**
     * Emits a full {@code <barline>} element with {@code <bar-style>} and,
     * when non-null, a {@code <repeat direction="..."/>} child.
     */
    private static void writeBarline(PrintWriter pw, String location, String barStyle, @Nullable String repeatDirection) {
        XML.writeBeginTag(pw, MusicXmlTags.BARLINE, MusicXmlTags.ATTR_LOCATION, location);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.BAR_STYLE, barStyle);

        if (repeatDirection != null) {
            XML.writeEmptyTag(pw, MusicXmlTags.REPEAT, MusicXmlTags.ATTR_DIRECTION, repeatDirection);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.BARLINE);
    }
}
