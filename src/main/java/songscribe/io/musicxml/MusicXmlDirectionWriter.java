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

import songscribe.dom.Annotation;
import songscribe.dom.BeatChange;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.io.XML;

final class MusicXmlDirectionWriter {

    private MusicXmlDirectionWriter() {}

    /**
     * Emits a tempo {@code <direction>}: a {@code <metronome>} beat-unit form
     * ({@code <beat-unit>} + any {@code <beat-unit-dot/>} + {@code <per-minute>}),
     * an optional {@code <words>} description direction-type, and a write-forward
     * {@code <sound tempo>}. A hidden tempo carries {@code print-object="no"} on
     * the {@code <metronome>}; the beat-unit/per-minute are still emitted so the
     * visible tempo survives.
     */
    static void writeTempoDirection(PrintWriter pw, Tempo tempo) {
        var beatUnit = BeatUnitMapping.forDuration(tempo.getTempoType());

        if (beatUnit == null) {
            // Every Tempo.tempoType is one of the seven mapped Durations, so this
            // is unreachable; the guard keeps the writer null-safe.
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION);
        XML.indent();

        writeMetronomeDirectionType(pw, tempo, beatUnit);

        var description = tempo.getTempoDescription();

        if (!description.isEmpty()) {
            writeWordsDirectionType(pw, description);
        }

        // <sound tempo> is write-forward only; the reader recovers the visible
        // tempo from <metronome>/<per-minute> and ignores this playback value.
        XML.writeEmptyTag(pw, MusicXmlTags.SOUND,
            MusicXmlTags.ATTR_TEMPO, Integer.toString(tempo.getRealTempo()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    /**
     * Emits the {@code <direction-type><metronome>} beat-unit form for
     * {@code tempo}, carrying {@code print-object="no"} when the tempo is hidden.
     */
    private static void writeMetronomeDirectionType(
            PrintWriter pw, Tempo tempo, BeatUnitMapping.BeatUnitEntry beatUnit) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        if (tempo.shouldShowTempo()) {
            XML.writeBeginTag(pw, MusicXmlTags.METRONOME);
        } else {
            XML.writeBeginTag(pw, MusicXmlTags.METRONOME,
                MusicXmlTags.ATTR_PRINT_OBJECT, MusicXmlTags.NO);
        }

        XML.indent();

        XML.writeValue(pw, MusicXmlTags.BEAT_UNIT, beatUnit.token());

        for (var dot = 0; dot < beatUnit.dotCount(); dot++) {
            XML.writeEmptyTag(pw, MusicXmlTags.BEAT_UNIT_DOT);
        }

        XML.writeValue(pw, MusicXmlTags.PER_MINUTE, Integer.toString(tempo.getVisibleTempo()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.METRONOME);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);
    }

    /** Emits a {@code <direction-type><words>} carrying the tempo description. */
    private static void writeWordsDirectionType(PrintWriter pw, String description) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.WORDS, description);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);
    }

    /**
     * Emits a metric-modulation {@code <direction>}: a {@code <metronome>} carrying
     * two {@code <metronome-note>}s related by {@code <metronome-relation>equals</metronome-relation>}
     * — the first for {@code beatChange.duration()} (the left note value), the second
     * for {@code beatChange.beat()} (the right). Tokens and dots come from
     * {@link BeatUnitMapping}. Reuses the same {@code <direction>} envelope as the
     * tempo form; the reader distinguishes the two by the presence of
     * {@code <metronome-note>} vs {@code <beat-unit>}.
     */
    static void writeMetricModulationDirection(PrintWriter pw, BeatChange beatChange) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.METRONOME);
        XML.indent();

        writeMetronomeNote(pw, beatChange.duration());
        XML.writeValue(pw, MusicXmlTags.METRONOME_RELATION, MusicXmlTags.RELATION_EQUALS);
        writeMetronomeNote(pw, beatChange.beat());

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.METRONOME);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    /**
     * Emits an annotation {@code <direction placement="above|below">} immediately
     * before the annotated {@code <note>}: a single
     * {@code <direction-type><words halign="…" justify="…" relative-y="…">text</words>}
     * from {@code getAnnotation()} / {@code getXAlignment()} / {@code getUserYOffsetSs()}.
     * {@code halign} and {@code justify} share the one alignment token. {@code default-y}
     * (the computed base position) is write-forward only and intentionally omitted;
     * the reader recovers the annotation from {@code placement} + {@code halign} +
     * {@code relative-y} and binds it to the next note.
     */
    static void writeAnnotationDirection(PrintWriter pw, Annotation annotation) {
        var placementToken = AnnotationResolver.placementToken(annotation.getPlacement());

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION, MusicXmlTags.ATTR_PLACEMENT, placementToken);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        var alignToken = TextAlignmentMapping.alignToken(annotation.getXAlignment());

        XML.writeValue(pw, MusicXmlTags.WORDS, annotation.getAnnotation(),
            MusicXmlTags.ATTR_HALIGN, alignToken,
            MusicXmlTags.ATTR_JUSTIFY, alignToken,
            MusicXmlTags.ATTR_RELATIVE_Y, MusicXmlUnits.formatSsAsTenths(annotation.getUserYOffsetSs())
        );

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    /**
     * Emits one {@code <metronome-note>}: a {@code <metronome-type>} token plus one
     * {@code <metronome-dot/>} per augmentation dot, both from {@link BeatUnitMapping}.
     */
    private static void writeMetronomeNote(PrintWriter pw, Duration duration) {
        var beatUnit = BeatUnitMapping.forDuration(duration);

        if (beatUnit == null) {
            // Every BeatChange Duration is one of the seven mapped values, so this
            // is unreachable; the guard keeps the writer null-safe.
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.METRONOME_NOTE);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.METRONOME_TYPE, beatUnit.token());

        for (var dot = 0; dot < beatUnit.dotCount(); dot++) {
            XML.writeEmptyTag(pw, MusicXmlTags.METRONOME_DOT);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.METRONOME_NOTE);
    }

    /**
     * Emits an empty {@code <tag type=… number="1">} marker, adding a
     * {@code relative-y} attribute ({@code verticalShiftSs} → tenths) only when
     * non-zero. Shared by the tuplet-bracket and wavy-line start/stop emitters,
     * which differ only in the tag name, the type token, and whether a vertical
     * shift applies (stop markers always pass zero).
     */
    static void writeNumberedMarker(PrintWriter pw, String tag, String type, double verticalShiftSs) {
        if (verticalShiftSs != 0) {
            XML.writeEmptyTag(pw, tag,
                MusicXmlTags.ATTR_TYPE, type,
                MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1,
                MusicXmlTags.ATTR_RELATIVE_Y, MusicXmlUnits.formatSsAsTenths(verticalShiftSs)
            );
        } else {
            XML.writeEmptyTag(pw, tag,
                MusicXmlTags.ATTR_TYPE, type,
                MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1
            );
        }
    }
}
