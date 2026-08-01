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

import org.jspecify.annotations.Nullable;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.io.XML;
import songscribe.layout.LayoutResult;

final class MusicXmlNotationsWriter {

    private MusicXmlNotationsWriter() {}

    static void writeNotations(PrintWriter pw, NoteWriteContext ctx) {
        var note = ctx.note();
        var nextIsBreathMark = ctx.nextIsBreathMark();
        var pendingStopGlissandoNote = ctx.pendingStopGlissandoNote();
        var type = note.getType();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();
        var spanMarkers = ctx.spanMarkers();

        // A grace note's glissando must target its host note, but a glissando
        // never terminates on a grace note (the stop always lands on the true host).
        var startGlissando = note.getGlissando();
        var hasSlideStop = !isGrace && pendingStopGlissandoNote != null;
        var hasSlideStart = startGlissando != null;

        var articulations = note.getArticulations();
        var hasFermata = note.findAttachment(FermataAttachment.class) != null;
        var dynamic = note.findAttachment(DynamicAttachment.class);

        var hasArticulationsBlock = !articulations.isEmpty() || note.hasFall() || nextIsBreathMark;

        // Tied (notation ties): emitted for the same cases as the sound <tie>.
        var hasTiedStop = !isRest && spanMarkers.tieStopsHere();
        var hasTiedStart = !isRest && spanMarkers.tieStartsHere();

        // Tuplet bracket: start on the anchor, stop on the end note.
        var isTupletAnchor = spanMarkers.isTupletAnchor();
        var isTupletEnd = spanMarkers.isTupletEnd();

        // Ornaments (trill): not emitted for rests.
        // isTrillAnchor → emit <trill-mark/> + <wavy-line type="start">
        // isTrillEnd    → emit <wavy-line type="stop">
        // For a single-note trill anchor == end, so both flags are set on one note.
        var isTrillAnchor = !isRest && spanMarkers.isTrillAnchor();
        var isTrillEnd = !isRest && spanMarkers.isTrillEnd();
        var hasOrnaments = isTrillAnchor || isTrillEnd;

        var hasNotations = hasSlideStop || hasSlideStart || hasFermata
            || hasArticulationsBlock || dynamic != null
            || hasTiedStop || hasTiedStart
            || isTupletAnchor || isTupletEnd
            || hasOrnaments;

        if (!hasNotations) {
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.NOTATIONS);
        XML.indent();

        // <tied> — notation counterpart of the sound <tie>, emitted first.
        // Interior notes of a chain emit stop before start. Each carries a write-forward
        // orientation ("over"/"under") from Tie.isAbove(); the reader ignores it (round-trip
        // loss stays benign while direction is fully deterministic from stems).
        if (hasTiedStop) {
            XML.writeEmptyTag(pw, MusicXmlTags.TIED, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_STOP,
                MusicXmlTags.ATTR_ORIENTATION, tiedOrientation(spanMarkers.tieStop()));
        }

        if (hasTiedStart) {
            XML.writeEmptyTag(pw, MusicXmlTags.TIED, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START,
                MusicXmlTags.ATTR_ORIENTATION, tiedOrientation(spanMarkers.tieStart()));
        }

        // <slide type="stop"> on the destination note of a glissando.
        // Carries the computed end-point coordinates for external-renderer
        // fidelity (write-forward only; the reader ignores them).
        if (hasSlideStop) {
            writeSlide(pw, MusicXmlTags.SLIDE_STOP, pendingStopGlissandoNote, ctx.layoutResult());
        }

        // <slide type="start"> on the source note of a glissando.
        // Carries the computed start-point coordinates (write-forward only).
        if (hasSlideStart) {
            writeSlide(pw, MusicXmlTags.SLIDE_START, note, ctx.layoutResult());
        }

        // <tuplet> bracket: start on the anchor, stop on the end note.
        // relative-y carries verticalPositionSs, only when non-zero.
        if (isTupletAnchor) {
            var tuplet = spanMarkers.tuplet();
            var verticalPositionSs = tuplet != null ? tuplet.getVerticalPositionSs() : 0;
            MusicXmlDirectionWriter.writeNumberedMarker(pw, MusicXmlTags.TUPLET, MusicXmlTags.TYPE_START, verticalPositionSs);
        }

        if (isTupletEnd) {
            MusicXmlDirectionWriter.writeNumberedMarker(pw, MusicXmlTags.TUPLET, MusicXmlTags.TYPE_STOP, 0);
        }

        // <ornaments>: trill-mark + wavy-line start/stop.
        // Only emitted when this note participates in a trill span.
        if (hasOrnaments) {
            XML.writeBeginTag(pw, MusicXmlTags.ORNAMENTS);
            XML.indent();

            // <trill-mark/> appears on the anchor (and on the end note of a
            // single-note trill, where anchor == end so isTrillAnchor is also true).
            if (isTrillAnchor) {
                XML.writeEmptyTag(pw, MusicXmlTags.TRILL_MARK);
            }

            // <wavy-line type="start"> on the anchor, carrying yPositionSs as
            // relative-y, only when non-zero.
            if (isTrillAnchor) {
                var trill = spanMarkers.trill();
                var yPositionSs = trill != null ? trill.getYPositionSs() : 0;
                MusicXmlDirectionWriter.writeNumberedMarker(pw, MusicXmlTags.WAVY_LINE, MusicXmlTags.TYPE_START, yPositionSs);
            }

            // <wavy-line type="stop"> on the end note.
            if (isTrillEnd) {
                MusicXmlDirectionWriter.writeNumberedMarker(pw, MusicXmlTags.WAVY_LINE, MusicXmlTags.TYPE_STOP, 0);
            }

            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.ORNAMENTS);
        }

        // <articulations>: accent, staccato, fall (falloff), breath-mark.
        if (hasArticulationsBlock) {
            XML.writeBeginTag(pw, MusicXmlTags.ARTICULATIONS);
            XML.indent();

            for (var articulation : articulations) {
                XML.writeEmptyTag(pw, switch (articulation.getType()) {
                        case ACCENT -> MusicXmlTags.ACCENT;
                        case STACCATO -> MusicXmlTags.STACCATO;
                    }
                );
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
            XML.writeBeginTag(pw, MusicXmlTags.DYNAMICS, MusicXmlTags.ATTR_PLACEMENT, MusicXmlTags.PLACEMENT_ABOVE);
            XML.indent();
            XML.writeEmptyTag(pw, dynamic.getType().getSymbol());
            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.DYNAMICS);
        }

        // <fermata/> — last per the schema ordering convention.
        if (hasFermata) {
            XML.writeEmptyTag(pw, MusicXmlTags.FERMATA);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.NOTATIONS);
    }

    static void writeLyrics(PrintWriter pw, StaffElement note) {
        var lyrics = note.getLyrics();

        if (lyrics.isEmpty()) {
            return;
        }

        for (var lyric : lyrics) {
            var extend = lyric.extend();

            XML.writeBeginTag(pw, MusicXmlTags.LYRIC,
                MusicXmlTags.ATTR_NUMBER, Integer.toString(lyric.verse()));
            XML.indent();

            if (lyric.isCarrier()) {
                XML.writeEmptyTag(pw, MusicXmlTags.EXTEND,
                    MusicXmlTags.ATTR_TYPE, SyllabicMapping.forExtend(extend));
            } else {
                XML.writeValue(pw, MusicXmlTags.SYLLABIC, SyllabicMapping.forSyllabic(lyric.syllabic()));

                var text = lyric.text();
                var lyricText = lyric.compound() ? text + Lyric.COMPOUND_WORD_MARKER : text;
                XML.writeValue(pw, MusicXmlTags.LYRIC_TEXT, lyricText);

                if (extend == Lyric.Extend.START) {
                    XML.writeEmptyTag(pw, MusicXmlTags.EXTEND,
                        MusicXmlTags.ATTR_TYPE, SyllabicMapping.forExtend(Lyric.Extend.START));
                }
            }

            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.LYRIC);
        }
    }

    /**
     * Emits one {@code <slide>} for the glissando owned by {@code glissandoNote}, carrying the
     * endpoint the {@code slideType} names: the drawn line's end for a stop, its start for a start.
     * <p>
     * The coordinates come from the line's layout, so they are the ones the score is painted from.
     * They are write-forward only — neither {@code MusicXmlReader} nor {@code MusicXmlNoteReader}
     * reads {@code default-x}/{@code default-y} back.
     *
     * @param glissandoNote the note owning the glissando, which keys its geometry
     * @param layoutResult  the owning line's layout, or null when none is available
     */
    private static void writeSlide(
            PrintWriter pw,
            String slideType,
            @Nullable StaffElement glissandoNote,
            @Nullable LayoutResult layoutResult
    ) {
        var slideLayout = (glissandoNote == null || layoutResult == null)
            ? null
            : layoutResult.getSlideLayout(glissandoNote);
        var endpoints = slideLayout == null ? null : slideLayout.glissando();

        if (endpoints == null) {
            // The layout stores no geometry for a glissando too short to draw — its two notes
            // sit closer together than the trimmed line's minimum visible length. Such a slide
            // is emitted without coordinates rather than dropped, since it is still musically
            // present.
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
            xSs = endpoints.endXSs();
            ySs = endpoints.endYSs();
        } else {
            xSs = endpoints.startXSs();
            ySs = endpoints.startYSs();
        }

        XML.writeEmptyTag(pw, MusicXmlTags.SLIDE,
            MusicXmlTags.ATTR_TYPE, slideType,
            MusicXmlTags.ATTR_LINE_TYPE, MusicXmlTags.LINE_SOLID,
            MusicXmlTags.ATTR_DEFAULT_X, MusicXmlUnits.formatSsAsTenths(xSs),
            MusicXmlTags.ATTR_DEFAULT_Y, MusicXmlUnits.formatSsAsTenths(ySs)
        );
    }

    /**
     * The write-forward {@code <tied orientation>} value for {@code tie}: {@code "over"} when the
     * tie arcs above its notes ({@link Tie#isAbove()}), else {@code "under"}. A null tie (which the
     * emit guards make unreachable) defaults to {@code "under"}.
     */
    private static String tiedOrientation(@Nullable Tie tie) {
        return tie != null && tie.isAbove()
            ? MusicXmlTags.ORIENTATION_OVER
            : MusicXmlTags.ORIENTATION_UNDER;
    }
}
