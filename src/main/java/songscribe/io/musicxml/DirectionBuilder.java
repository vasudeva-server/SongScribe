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

import java.math.BigDecimal;
import java.util.List;

import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.Direction;
import org.audiveris.proxymusic.DirectionType;
import org.audiveris.proxymusic.Empty;
import org.audiveris.proxymusic.LeftCenterRight;
import org.audiveris.proxymusic.Metronome;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.Wedge;
import org.audiveris.proxymusic.WedgeType;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.Hairpin;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

/**
 * Builds the {@code <direction>} nodes of the {@link org.audiveris.proxymusic.ScorePartwise}
 * graph: tempo marks, metric modulations, hairpin wedges and text annotations.
 *
 * <p><b>Every {@code <direction>} belongs immediately before the {@code <note>} it marks, and
 * the reader binds a direction to the next note.</b> That is not a limitation the object graph
 * lifted — it is the binding mechanism MusicXML provides. {@code <direction>} is a sibling of
 * {@code <note>} inside {@code <measure>}, and MusicXML 4.0 has no attribute by which a
 * direction names the note it applies to; position is the only binding available, and
 * direction-before-note is the universal convention across notation programs. Working from a
 * finished graph does not make the placement negotiable: a direction emitted after its note
 * binds to the following one, in this reader and in every other.
 *
 * <p>Each method here <em>returns</em> the {@link Direction} it builds rather than appending it
 * anywhere. That return value is the handle: the caller places it in the measure's child list
 * at the position that expresses the binding, and Phase 8's {@code applyHairpins} pass reaches
 * the wedge directions the same way — it builds them from {@code line.getHairpins()} and holds
 * what {@link #buildHairpinStartDirection} and {@link #buildHairpinStopDirection} hand back.
 * A builder that appended to a measure itself would have to know where in that measure the
 * bound note sits, which is exactly the ordering knowledge the orchestrator already has.
 */
final class DirectionBuilder {

    /**
     * Every hairpin wedge SongScribe writes is number 1 — the model has one hairpin level, so
     * no two wedges are ever open at once and the number never has to distinguish them.
     */
    private static final int WEDGE_NUMBER = 1;

    private DirectionBuilder() {}

    // -------------------------------------------------------------------------
    // Tempo
    // -------------------------------------------------------------------------

    /**
     * Builds a tempo {@code <direction>}: a {@code <metronome>} beat-unit form
     * ({@code <beat-unit>} + any {@code <beat-unit-dot/>} + {@code <per-minute>}), an optional
     * {@code <words>} description direction-type, and a write-forward {@code <sound tempo>}.
     * A hidden tempo carries {@code print-object="no"} on the {@code <metronome>}; the
     * beat-unit/per-minute are still emitted so the visible tempo survives.
     *
     * <p>Returns {@code null} when the tempo's duration has no beat-unit mapping. Every
     * {@code Tempo.tempoType} is one of the seven mapped {@link Duration}s, so that is
     * unreachable; the guard keeps the builder null-safe.
     */
    static @Nullable Direction buildTempoDirection(BuildContext context, Tempo tempo) {
        var beatUnit = BeatUnitMapping.forDuration(tempo.getTempoType());

        if (beatUnit == null) {
            return null;
        }

        var factory = context.factory();
        var direction = factory.createDirection();
        direction.getDirectionType().add(buildMetronomeDirectionType(factory, tempo, beatUnit));

        var description = tempo.getTempoDescription();

        if (!description.isEmpty()) {
            direction.getDirectionType().add(buildWordsDirectionType(factory, description));
        }

        // <sound tempo> is write-forward only; the reader recovers the visible tempo from
        // <metronome>/<per-minute> and ignores this playback value.
        var sound = factory.createSound();
        sound.setTempo(BigDecimal.valueOf(tempo.getRealTempo()));
        direction.setSound(sound);

        return direction;
    }

    /**
     * Builds the tempo {@code <direction>} for {@code element}'s own
     * {@link TempoChangeAttachment} — a per-note tempo — or {@code null} when the element
     * carries no tempo change.
     */
    static @Nullable Direction buildElementTempoDirection(BuildContext context, StaffElement element) {
        var tempoAttachment = element.findAttachment(TempoChangeAttachment.class);

        if (tempoAttachment == null) {
            return null;
        }

        return buildTempoDirection(context, tempoAttachment.getTempo());
    }

    /**
     * Builds the {@code <direction-type><metronome>} beat-unit form for {@code tempo}, carrying
     * {@code print-object="no"} when the tempo is hidden.
     */
    private static DirectionType buildMetronomeDirectionType(
            ObjectFactory factory, Tempo tempo, BeatUnitMapping.BeatUnitEntry beatUnit) {
        var metronome = factory.createMetronome();

        if (!tempo.shouldShowTempo()) {
            metronome.setPrintObject(YesNo.NO);
        }

        metronome.setBeatUnit(beatUnit.token());
        addDots(factory, metronome.getBeatUnitDot(), beatUnit.dotCount());

        var perMinute = factory.createPerMinute();
        perMinute.setValue(Integer.toString(tempo.getVisibleTempo()));
        metronome.setPerMinute(perMinute);

        var directionType = factory.createDirectionType();
        directionType.setMetronome(metronome);

        return directionType;
    }

    /** Builds a {@code <direction-type><words>} carrying {@code text} with no styling. */
    private static DirectionType buildWordsDirectionType(ObjectFactory factory, String text) {
        var words = factory.createFormattedTextId();
        words.setValue(text);

        var directionType = factory.createDirectionType();
        directionType.getWordsOrSymbol().add(words);

        return directionType;
    }

    // -------------------------------------------------------------------------
    // Metric modulation
    // -------------------------------------------------------------------------

    /**
     * Builds the metric-modulation {@code <direction>} for {@code element}'s own
     * {@link BeatChangeAttachment}, or {@code null} when the element carries no beat change.
     *
     * <p>Like every other direction, it precedes the note it marks and the reader binds it to
     * the next note.
     */
    static @Nullable Direction buildElementMetricModulationDirection(
            BuildContext context, StaffElement element) {
        var beatChangeAttachment = element.findAttachment(BeatChangeAttachment.class);

        if (beatChangeAttachment == null) {
            return null;
        }

        return buildMetricModulationDirection(context, beatChangeAttachment.getBeatChange());
    }

    /**
     * Builds a metric-modulation {@code <direction>}: a {@code <metronome>} carrying two
     * {@code <metronome-note>}s related by
     * {@code <metronome-relation>equals</metronome-relation>} — the first for
     * {@code beatChange.duration()} (the left note value), the second for
     * {@code beatChange.beat()} (the right). Tokens and dots come from
     * {@link BeatUnitMapping}. Reuses the same {@code <direction>} envelope as the tempo form;
     * the reader distinguishes the two by the presence of {@code <metronome-note>} vs
     * {@code <beat-unit>}.
     *
     * <p><b>Known defect — the generated {@code Metronome} cannot express this form in a
     * schema-valid order.</b> The schema is
     * {@code metronome-note+, (metronome-relation, metronome-note+)?}, so the relation must sit
     * <em>between</em> the two notes. XJC collapsed both note groups into one
     * {@code List<MetronomeNote>} and made the relation a separate property, and the generated
     * {@code @XmlType(propOrder = …)} places {@code metronomeRelation} <em>after</em>
     * {@code metronomeNote}. Whatever order the calls below are made in, JAXB marshals
     * {@code <metronome-note/><metronome-note/><metronome-relation/>}, which Xerces rejects
     * with {@code Element 'metronome': Missing child element(s). Expected is (
     * metronome-note )}. The construction order below is the one the document needs; the
     * reordering has to be undone where the graph becomes text, in
     * {@code MusicXmlSerializer}'s stream-writer delegate.
     */
    static Direction buildMetricModulationDirection(BuildContext context, BeatChange beatChange) {
        var factory = context.factory();
        var metronome = factory.createMetronome();

        addMetronomeNote(factory, metronome, beatChange.duration());
        metronome.setMetronomeRelation(MusicXmlTags.RELATION_EQUALS);
        addMetronomeNote(factory, metronome, beatChange.beat());

        var directionType = factory.createDirectionType();
        directionType.setMetronome(metronome);

        var direction = factory.createDirection();
        direction.getDirectionType().add(directionType);

        return direction;
    }

    /**
     * Appends one {@code <metronome-note>} to {@code metronome}: a {@code <metronome-type>}
     * token plus one {@code <metronome-dot/>} per augmentation dot, both from
     * {@link BeatUnitMapping}.
     *
     * <p>A duration with no beat-unit mapping appends nothing. Every {@link BeatChange}
     * duration is one of the seven mapped values, so that is unreachable; the guard keeps the
     * builder null-safe.
     */
    private static void addMetronomeNote(
            ObjectFactory factory, Metronome metronome, Duration duration) {
        var beatUnit = BeatUnitMapping.forDuration(duration);

        if (beatUnit == null) {
            return;
        }

        var metronomeNote = factory.createMetronomeNote();
        metronomeNote.setMetronomeType(beatUnit.token());
        addDots(factory, metronomeNote.getMetronomeDot(), beatUnit.dotCount());
        metronome.getMetronomeNote().add(metronomeNote);
    }

    /**
     * Appends {@code dotCount} empty dot elements to {@code dots} — the augmentation dots of a
     * beat unit, whose element name ({@code <beat-unit-dot/>} or {@code <metronome-dot/>}) is
     * fixed by which list is passed in.
     */
    private static void addDots(ObjectFactory factory, List<Empty> dots, int dotCount) {
        for (var dot = 0; dot < dotCount; dot++) {
            dots.add(factory.createEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Hairpin wedges
    // -------------------------------------------------------------------------

    /**
     * Builds the start wedge {@code <direction>} for {@code hairpin}:
     * {@code <wedge type="crescendo|diminuendo" number="1">}, carrying {@code x1ShiftSs} as
     * {@code relative-x} and {@code yShiftSs} as {@code relative-y} (ss × 10 = tenths), each
     * only when non-zero. Belongs immediately before the hairpin's anchor note.
     */
    static Direction buildHairpinStartDirection(BuildContext context, Hairpin hairpin) {
        var factory = context.factory();
        var wedge = factory.createWedge();
        wedge.setType(WedgeType.fromValue(WedgeTypeMapping.wedgeType(hairpin)));
        wedge.setNumber(WEDGE_NUMBER);
        wedge.setRelativeX(MusicXmlUnits.shiftTenths(hairpin.getX1ShiftSs()));
        wedge.setRelativeY(MusicXmlUnits.shiftTenths(hairpin.getYShiftSs()));

        return buildWedgeDirection(factory, wedge);
    }

    /**
     * Builds the stop wedge {@code <direction>} for {@code hairpin}:
     * {@code <wedge type="stop" number="1">}, carrying {@code x2ShiftSs} as {@code relative-x}
     * (ss × 10 = tenths), only when non-zero. Belongs immediately before the hairpin's end
     * note — a stop wedge binds forward exactly like a start wedge, which is what gives the
     * reader one uniform look-ahead rule.
     */
    static Direction buildHairpinStopDirection(BuildContext context, Hairpin hairpin) {
        var factory = context.factory();
        var wedge = factory.createWedge();
        wedge.setType(WedgeType.STOP);
        wedge.setNumber(WEDGE_NUMBER);
        wedge.setRelativeX(MusicXmlUnits.shiftTenths(hairpin.getX2ShiftSs()));

        return buildWedgeDirection(factory, wedge);
    }

    /** Wraps {@code wedge} in its {@code <direction><direction-type>} envelope. */
    private static Direction buildWedgeDirection(ObjectFactory factory, Wedge wedge) {
        var directionType = factory.createDirectionType();
        directionType.setWedge(wedge);

        var direction = factory.createDirection();
        direction.getDirectionType().add(directionType);

        return direction;
    }

    // -------------------------------------------------------------------------
    // Annotations
    // -------------------------------------------------------------------------

    /**
     * Builds the {@code <direction>} for {@code element}'s {@link AnnotationAttachment}.
     *
     * @return the direction, or {@code null} when the element carries no annotation or the
     *         annotation has no text — an annotation with nothing to draw is not written, so a
     *         reload does not have to decide what an empty {@code <words>} means
     */
    static @Nullable Direction buildElementAnnotationDirection(
            BuildContext context, StaffElement element) {
        var annotationAttachment = element.findAttachment(AnnotationAttachment.class);

        if (annotationAttachment == null) {
            return null;
        }

        var annotation = annotationAttachment.getAnnotation();

        if (annotation.getAnnotation().isBlank()) {
            return null;
        }

        return buildAnnotationDirection(context, annotation);
    }

    /**
     * Builds an annotation {@code <direction placement="above|below">} to be placed immediately
     * before the annotated {@code <note>}: a single
     * {@code <direction-type><words halign="…" justify="…" relative-y="…">text</words>} from
     * {@code getAnnotation()} / {@code getXAlignment()} / {@code getUserYOffsetSs()}.
     * {@code halign} and {@code justify} share the one alignment token. {@code default-y} (the
     * computed base position) is write-forward only and intentionally omitted; the reader
     * recovers the annotation from {@code placement} + {@code halign} + {@code relative-y} and
     * binds it to the next note.
     */
    static Direction buildAnnotationDirection(BuildContext context, Annotation annotation) {
        var factory = context.factory();
        var alignment = LeftCenterRight.fromValue(TextAlignmentMapping.alignToken(annotation.getXAlignment()));

        var words = factory.createFormattedTextId();
        words.setValue(annotation.getAnnotation());
        words.setHalign(alignment);
        words.setJustify(alignment);
        words.setRelativeY(MusicXmlUnits.ssAsTenths(annotation.getUserYOffsetSs()));

        var directionType = factory.createDirectionType();
        directionType.getWordsOrSymbol().add(words);

        var direction = factory.createDirection();
        direction.getDirectionType().add(directionType);
        direction.setPlacement(AboveBelow.fromValue(PlacementMapping.placementToken(annotation.getPlacement())));

        return direction;
    }
}
