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

import org.audiveris.proxymusic.Direction;
import org.audiveris.proxymusic.DirectionType;
import org.audiveris.proxymusic.Empty;
import org.audiveris.proxymusic.Metronome;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.Wedge;
import org.audiveris.proxymusic.WedgeType;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.BeatUnit;
import songscribe.dom.Hairpin;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.TempoMarking;

/**
 * Builds the {@code <direction>} nodes of the {@link org.audiveris.proxymusic.ScorePartwise}
 * graph: tempo marks, metric modulations, hairpin wedges and text annotations.
 *
 * <p><b>Every {@code <direction>} belongs immediately before the {@code <note>} it marks, and
 * the reader binds a direction to the next note.</b> MusicXML 4.0 has no other binding: a
 * direction emitted after its note binds to the following one.
 */
final class DirectionBuilder {

    /**
     * The model has one hairpin level, so the number never has to distinguish wedges.
     */
    private static final int WEDGE_NUMBER = 1;

    private DirectionBuilder() {}

    // -------------------------------------------------------------------------
    // Tempo
    // -------------------------------------------------------------------------

    /**
     * The {@code <sound tempo>} is write-forward. A hidden tempo still emits beat-unit and
     * per-minute, so the visible tempo survives.
     */
    static Direction buildTempoDirection(BuildContext context, Tempo tempo) {
        var beatUnit = BeatUnitMapping.entryFor(tempo.tempoType());
        var factory = context.factory();
        var direction = factory.createDirection();
        direction.getDirectionType().add(buildMetronomeDirectionType(factory, tempo, beatUnit));

        var description = tempo.marking().description();

        if (!description.isEmpty()) {
            direction.getDirectionType().add(buildWordsDirectionType(factory, description));
        }

        // <sound tempo> is write-forward only; the reader recovers the visible tempo from
        // <metronome>/<per-minute> and ignores this playback value.
        var sound = factory.createSound();
        sound.setTempo(BigDecimal.valueOf(tempo.realTempo()));
        direction.setSound(sound);

        return direction;
    }

    /**
     * Returns {@code null} when the element carries no tempo change.
     */
    static @Nullable Direction buildElementTempoDirection(BuildContext context, StaffElement element) {
        var tempoAttachment = element.findAttachment(TempoChangeAttachment.class);

        if (tempoAttachment == null) {
            return null;
        }

        return buildTempoDirection(context, tempoAttachment.getTempo());
    }

    private static DirectionType buildMetronomeDirectionType(
            ObjectFactory factory, Tempo tempo, BeatUnitMapping.BeatUnitEntry beatUnit) {
        var metronome = factory.createMetronome();

        if (tempo.marking() instanceof TempoMarking.TextOnly) {
            metronome.setPrintObject(YesNo.NO);
        }

        metronome.setBeatUnit(beatUnit.token());
        addDots(factory, metronome.getBeatUnitDot(), beatUnit.dotCount());

        var perMinute = factory.createPerMinute();
        perMinute.setValue(Integer.toString(tempo.visibleTempo()));
        metronome.setPerMinute(perMinute);

        var directionType = factory.createDirectionType();
        directionType.setMetronome(metronome);

        return directionType;
    }

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
     * Returns {@code null} when the element carries no beat change.
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
     * The reader distinguishes this from the tempo form by {@code <metronome-note>} vs
     * {@code <beat-unit>}.
     *
     * <p>The generated {@code Metronome} cannot express this form in a schema-valid order,
     * whatever the construction order; {@code MusicXmlSerializer} restores it when the graph
     * becomes text.
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

    private static void addMetronomeNote(
            ObjectFactory factory, Metronome metronome, BeatUnit duration) {
        var beatUnit = BeatUnitMapping.entryFor(duration);
        var metronomeNote = factory.createMetronomeNote();
        metronomeNote.setMetronomeType(beatUnit.token());
        addDots(factory, metronomeNote.getMetronomeDot(), beatUnit.dotCount());
        metronome.getMetronomeNote().add(metronomeNote);
    }

    private static void addDots(ObjectFactory factory, List<Empty> dots, int dotCount) {
        for (var dot = 0; dot < dotCount; dot++) {
            dots.add(factory.createEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Hairpin wedges
    // -------------------------------------------------------------------------

    /**
     * Belongs immediately before the hairpin's anchor note.
     */
    static Direction buildHairpinStartDirection(BuildContext context, Hairpin hairpin) {
        var factory = context.factory();
        var wedge = factory.createWedge();
        wedge.setType(WedgeType.fromValue(WedgeTypeMapping.wedgeType(hairpin)));
        wedge.setNumber(WEDGE_NUMBER);
        wedge.setRelativeX(hairpinXOffsetTenths(hairpin));
        wedge.setRelativeY(MusicXmlUnits.shiftTenths(hairpin.getUserYOffsetSs()));

        return buildWedgeDirection(factory, wedge);
    }

    /**
     * Belongs immediately before the hairpin's end note: a stop wedge binds forward like a start
     * wedge.
     */
    static Direction buildHairpinStopDirection(BuildContext context, Hairpin hairpin) {
        var factory = context.factory();
        var wedge = factory.createWedge();
        wedge.setType(WedgeType.STOP);
        wedge.setNumber(WEDGE_NUMBER);
        wedge.setRelativeX(hairpinXOffsetTenths(hairpin));

        return buildWedgeDirection(factory, wedge);
    }

    /**
     * A wedge's {@code relative-x} moves only its own end; the same value on both moves the whole
     * hairpin.
     */
    private static @Nullable BigDecimal hairpinXOffsetTenths(Hairpin hairpin) {
        return MusicXmlUnits.shiftTenths(hairpin.getUserXOffsetSs());
    }

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
     * @return {@code null} when the element carries no annotation or the annotation has no text;
     *         such an annotation is not written
     */
    static @Nullable Direction buildElementAnnotationDirection(
            BuildContext context, StaffElement element) {
        var annotationAttachment = element.findAttachment(AnnotationAttachment.class);

        if (annotationAttachment == null) {
            return null;
        }

        var annotation = annotationAttachment.getAnnotation();

        if (annotation.text().isBlank()) {
            return null;
        }

        var factory = context.factory();
        var alignment = annotation.alignment();

        var words = factory.createFormattedTextId();
        words.setValue(annotation.text());
        words.setHalign(alignment);
        words.setJustify(alignment);
        words.setRelativeY(MusicXmlUnits.ssAsTenths(annotationAttachment.getUserYOffsetSs()));

        var directionType = factory.createDirectionType();
        directionType.getWordsOrSymbol().add(words);

        var direction = factory.createDirection();
        direction.getDirectionType().add(directionType);
        direction.setPlacement(annotation.placement());

        return direction;
    }
}
