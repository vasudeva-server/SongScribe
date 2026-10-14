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

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

import org.audiveris.proxymusic.BeamValue;
import org.audiveris.proxymusic.Direction;
import org.audiveris.proxymusic.LineType;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.Ornaments;
import org.audiveris.proxymusic.OverUnder;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.ScorePartwise.Part.Measure;
import org.audiveris.proxymusic.StartStop;
import org.audiveris.proxymusic.StartStopContinue;
import org.audiveris.proxymusic.TiedType;
import org.audiveris.proxymusic.WavyLine;
import org.jspecify.annotations.Nullable;

// The Song-model span types, not their ProxyMusic namesakes. org.audiveris.proxymusic
// declares Beam, Tie and Tuplet too; none of those is named here, so the simple names belong
// to the document model and every ProxyMusic node of those types is created through the
// ObjectFactory and held in a var.
import songscribe.dom.Beam;
import songscribe.dom.BeamLevel;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineLayoutProvider;

/**
 * Builds the whole {@link ScorePartwise} graph for a {@link Song}: the header, the single
 * part, its measures, and then the adjustment passes that attach everything spanning more
 * than one node.
 */
final class ScorePartwiseBuilder {

    private static final String EMPTY_PART_NAME = "";

    /**
     * SongScribe has one span level, so the number never has to distinguish spans.
     */
    private static final int SINGLE_SPAN_NUMBER = 1;

    private ScorePartwiseBuilder() {}

    /**
     * {@code clock} supplies the date for the write-forward {@code <rights>} year and
     * {@code <encoding-date>}, so both are deterministic under test.
     */
    static ScorePartwise build(
            Song song, DocumentFontsHolder fonts, LineLayoutProvider layoutProvider, Clock clock) {
        var factory = new ObjectFactory();

        // Must be an IdentityHashMap; see BuildIndex.
        var index = new BuildIndex(new IdentityHashMap<>());
        var context = new BuildContext(song, fonts, factory, layoutProvider, index);

        var scorePartwise = factory.createScorePartwise();
        scorePartwise.setVersion(MusicXmlTags.VERSION_VALUE);

        // Resolve every clock- and date-derived header value once, so the <miscellaneous>
        // block and the <credit> list share a single source for the composition/lyrics dates
        // and the <rights>/<encoding-date> strings.
        var headerText = HeaderBuilder.HeaderText.of(song, clock);

        HeaderBuilder.buildMovementInfo(context, scorePartwise);
        HeaderBuilder.buildIdentification(context, scorePartwise, headerText);
        HeaderBuilder.buildDefaults(context, scorePartwise);
        HeaderBuilder.buildCredits(context, scorePartwise, headerText);

        var part = buildPart(context, scorePartwise);

        if (song.lineCount() == 0) {
            part.getMeasure().add(buildEmptySongMeasure(context));
        } else {
            buildLines(context, part);
        }

        return scorePartwise;
    }

    /**
     * The {@code ScorePart} instance itself is passed to {@code Part.setId} (an {@code IDREF});
     * the string {@code "P1"} marshals as a reference to nothing.
     */
    private static ScorePartwise.Part buildPart(BuildContext context, ScorePartwise scorePartwise) {
        var factory = context.factory();

        var scorePart = factory.createScorePart();
        scorePart.setId(MusicXmlTags.PART_ID);

        var partName = factory.createPartName();
        partName.setValue(EMPTY_PART_NAME);
        scorePart.setPartName(partName);

        var partList = factory.createPartList();
        partList.getPartGroupOrScorePart().add(scorePart);
        scorePartwise.setPartList(partList);

        var part = factory.createScorePartwisePart();
        part.setId(scorePart);
        scorePartwise.getPart().add(part);

        return part;
    }

    private static Measure buildEmptySongMeasure(BuildContext context) {
        var measure = MeasureBuilder.buildMeasure(context, MusicXmlUnits.FIRST_MEASURE_NUMBER);
        var items = measure.getNoteOrBackupOrForward();
        var tempo = DirectionBuilder.buildTempoDirection(context, context.song().getTempo());

        if (tempo != null) {
            items.add(tempo);
        }

        items.add(MeasureBuilder.buildAttributes(context));

        return measure;
    }

    private static void buildLines(BuildContext context, ScorePartwise.Part part) {
        var state = MeasureBuilder.newScoreState(context.song());
        var lines = context.song().getLines();
        var lineOutputs = new ArrayList<MeasureBuilder.LineOutput>(lines.size());

        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            var lineOutput = MeasureBuilder.buildLine(context, state, lines.get(lineIndex), lineIndex);
            lineOutputs.add(lineOutput);
            part.getMeasure().addAll(lineOutput.measures());
        }

        addSongTempoDirection(context, part);
        runAdjustmentPasses(context, lineOutputs);
    }

    /**
     * The song tempo is not bound to a note; the reader recovers it as the first tempo
     * {@code <direction>} in the first {@code <measure>}.
     */
    private static void addSongTempoDirection(BuildContext context, ScorePartwise.Part part) {
        var tempo = DirectionBuilder.buildTempoDirection(context, context.song().getTempo());

        if (tempo != null) {
            part.getMeasure().getFirst().getNoteOrBackupOrForward().add(0, tempo);
        }
    }

    /**
     * Members of <em>equal</em> rank in {@code <notations>} keep their arrival order, so the two
     * places where that matters ({@code <tied>} stop before start, {@code <slide>} stop before
     * start) are each resolved inside a single pass. Endings are attached by
     * {@link MeasureBuilder}, not here.
     */
    private static void runAdjustmentPasses(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        applyBeams(context, lines);
        applyTies(context, lines);
        applyTuplets(context, lines);
        applyTrills(context, lines);
        applyGlissandos(context, glissandoSites(lines));
        applyHairpins(context, lines);
    }

    private static List<GlissandoSite> glissandoSites(List<MeasureBuilder.LineOutput> lines) {
        var sites = new ArrayList<GlissandoSite>();

        for (var lineOutput : lines) {
            var emitted = lineOutput.emitted();

            for (var i = 0; i < emitted.size(); i++) {
                if (emitted.get(i) instanceof NoteElement note && note.hasGlissando()) {
                    sites.add(new GlissandoSite(lineOutput, i));
                }
            }
        }

        return sites;
    }

    /**
     * A grace note inside the span is not a member, so it gets no {@code <beam>}: the beam
     * passes over it.
     */
    private static void applyBeams(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            for (var beam : entry.line().spanRegistry().findSpans(Beam.class)) {
                for (var member : beam.members()) {
                    addBeams(context, member.note(), beamValues(member));
                }
            }
        }
    }

    private static void addBeams(
        BuildContext context, StaffElement element, EnumMap<BeamLevel, BeamValue> levelValues) {
        var pmNote = context.index().notes().get(element);

        if (pmNote == null) {
            return;
        }

        for (var levelValue : levelValues.entrySet()) {
            var beam = context.factory().createBeam();
            beam.setValue(levelValue.getValue());
            beam.setNumber(levelValue.getKey().number());
            pmNote.getBeam().add(beam);
        }
    }

    private static EnumMap<BeamLevel, BeamValue> beamValues(Beam.Member member) {
        var values = new EnumMap<BeamLevel, BeamValue>(BeamLevel.class);

        for (@Nullable BeamLevel level = BeamLevel.EIGHTH; level != null; level = level.next()) {
            var run = member.runAt(level);

            if (run == null) {
                continue;
            }

            values.put(level, run.isSingle() ? hookValue(member.stub()) : runValue(member.position(), run));
        }

        return values;
    }

    private static BeamValue hookValue(Beam.Stub stub) {
        return switch (stub) {
            case LEFT -> BeamValue.BACKWARD_HOOK;
            case RIGHT -> BeamValue.FORWARD_HOOK;
            case NONE -> throw new IllegalStateException("a member alone in a run always has a stub");
        };
    }

    private static BeamValue runValue(int position, Beam.MemberRun run) {
        if (position == run.first()) {
            return BeamValue.BEGIN;
        }

        if (position == run.last()) {
            return BeamValue.END;
        }

        return BeamValue.CONTINUE;
    }

    private static void applyTies(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            var line = entry.line();
            var count = line.elementCount();

            for (var i = 0; i < count; i++) {
                if (!(line.getElement(i) instanceof NoteElement note)) {
                    continue;
                }

                var pmNote = context.index().notes().get(note);

                if (pmNote == null) {
                    continue;
                }

                addTie(context, pmNote, note.incomingTie(), StartStop.STOP, TiedType.STOP);
                addTie(context, pmNote, note.tie(), StartStop.START, TiedType.START);
            }
        }
    }

    /**
     * {@code <tied orientation>} is write-forward: the reader ignores it.
     */
    private static void addTie(
            BuildContext context,
            Note pmNote,
            @Nullable Tie tie,
            StartStop soundType,
            TiedType notationType) {
        if (tie == null) {
            return;
        }

        var factory = context.factory();

        var pmTie = factory.createTie();
        pmTie.setType(soundType);
        pmNote.getTie().add(pmTie);

        var pmTied = factory.createTied();
        pmTied.setType(notationType);
        pmTied.setOrientation(tie.isAbove() ? OverUnder.OVER : OverUnder.UNDER);
        NoteBuilder.addNotation(context, pmNote, pmTied);
    }

    private static void applyTuplets(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            var line = entry.line();

            for (var tuplet : line.spanRegistry().findSpans(Tuplet.class)) {
                var range = tuplet.range();
                var start = addTupletBracket(context, line.getElement(range.begin()), StartStop.START);

                if (start != null) {
                    start.setRelativeY(MusicXmlUnits.shiftTenths(tuplet.getUserYOffsetSs()));
                }

                addTupletBracket(context, line.getElement(range.end()), StartStop.STOP);
            }
        }
    }

    /**
     * Returns null when the element has no note.
     */
    private static org.audiveris.proxymusic.@Nullable Tuplet addTupletBracket(
            BuildContext context, StaffElement element, StartStop type) {
        var pmNote = context.index().notes().get(element);

        if (pmNote == null) {
            return null;
        }

        var pmTuplet = context.factory().createTuplet();
        pmTuplet.setType(type);
        pmTuplet.setNumber(SINGLE_SPAN_NUMBER);
        NoteBuilder.addNotation(context, pmNote, pmTuplet);

        return pmTuplet;
    }

    private static void applyTrills(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            var line = entry.line();

            for (var trill : line.spanRegistry().findSpans(Trill.class)) {
                var range = trill.range();
                addTrillStart(context, line.getElement(range.begin()), trill);
                addWavyLine(context, line.getElement(range.end()), StartStopContinue.STOP);
            }
        }
    }

    private static void addTrillStart(BuildContext context, StaffElement element, Trill trill) {
        var pmNote = trilledNote(context, element);

        if (pmNote == null) {
            return;
        }

        var factory = context.factory();
        ornamentsOf(context, pmNote).getTrillMarkOrTurnOrDelayedTurn()
            .add(factory.createOrnamentsTrillMark(factory.createEmptyTrillSound()));

        var start = addWavyLine(context, element, StartStopContinue.START);

        if (start != null) {
            start.setRelativeY(MusicXmlUnits.shiftTenths(trill.getUserYOffsetSs()));
        }
    }

    /**
     * Returns null when the note does not take one.
     */
    private static @Nullable WavyLine addWavyLine(
            BuildContext context, StaffElement element, StartStopContinue type) {
        var pmNote = trilledNote(context, element);

        if (pmNote == null) {
            return null;
        }

        var factory = context.factory();
        var wavyLine = factory.createWavyLine();
        wavyLine.setType(type);
        wavyLine.setNumber(SINGLE_SPAN_NUMBER);
        ornamentsOf(context, pmNote).getTrillMarkOrTurnOrDelayedTurn()
            .add(factory.createOrnamentsWavyLine(wavyLine));

        return wavyLine;
    }

    /** Null when {@code element}'s note cannot carry a trill — a rest never can. */
    private static @Nullable Note trilledNote(BuildContext context, StaffElement element) {
        if (element.getType().isRest()) {
            return null;
        }

        return context.index().notes().get(element);
    }

    /**
     * A note reached twice by one trill grows one element rather than two.
     */
    private static Ornaments ornamentsOf(BuildContext context, Note pmNote) {
        for (var member : NoteBuilder.notationsOf(context, pmNote).getTiedOrSlurOrTuplet()) {
            if (member instanceof Ornaments ornaments) {
                return ornaments;
            }
        }

        var created = context.factory().createOrnaments();
        NoteBuilder.addNotation(context, pmNote, created);

        return created;
    }

    /**
     * <b>Iterates glissandos, never lines:</b> a line with no glissando must not be laid out,
     * because layout resolves the line's automatic stem directions as a side effect and would
     * change that line's output.
     *
     * <p>Glissandos are intra-line: a dangling one on the last note of a line emits no stop.
     */
    private static void applyGlissandos(BuildContext context, List<GlissandoSite> glissandos) {
        var layouts = new IdentityHashMap<Line, LayoutResult>();

        for (var site : glissandos) {
            var line = site.owner().line();

            if (!layouts.containsKey(line)) {
                layouts.put(line, context.layoutProvider().layoutFor(line));
            }

            var layout = layouts.get(line);
            var emitted = site.owner().emitted();
            var source = emitted.get(site.emittedIndex());
            var targetIndex = site.emittedIndex() + 1;

            // The stop always lands on the true host: a glissando may start on a grace note,
            // but it never terminates on one.
            if (targetIndex < emitted.size()) {
                var target = emitted.get(targetIndex);

                if (!target.getType().isGraceNote()) {
                    addSlide(context, target, StartStop.STOP, source, layout);
                }
            }

            addSlide(context, source, StartStop.START, source, layout);
        }
    }

    /**
     * The coordinates are write-forward only. A glissando too short to draw is emitted without
     * coordinates rather than dropped, since it is still musically present.
     */
    private static void addSlide(
            BuildContext context,
            StaffElement element,
            StartStop type,
            StaffElement glissandoOwner,
            @Nullable LayoutResult layout) {
        var pmNote = context.index().notes().get(element);

        if (pmNote == null) {
            return;
        }

        var slide = context.factory().createSlide();
        slide.setType(type);
        slide.setLineType(LineType.SOLID);

        var slideLayout = layout == null ? null : layout.getSlideLayout(glissandoOwner);
        var endpoints = slideLayout == null ? null : slideLayout.glissando();

        if (endpoints != null) {
            var isStop = type == StartStop.STOP;
            slide.setDefaultX(MusicXmlUnits.ssAsTenths(isStop ? endpoints.endXSs() : endpoints.startXSs()));
            slide.setDefaultY(MusicXmlUnits.ssAsTenths(isStop ? endpoints.endYSs() : endpoints.startYSs()));
        }

        NoteBuilder.addNotation(context, pmNote, slide);
    }

    /**
     * Stops precede starts on a note that carries both, so one hairpin's close and the next
     * one's open keep a natural order.
     */
    private static void applyHairpins(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            var line = entry.line();

            // Only the indices that actually carry a hairpin get an entry — a long line with one
            // crescendo should not allocate a list per element.
            var starting = new HashMap<Integer, List<Hairpin>>();
            var ending = new HashMap<Integer, List<Hairpin>>();

            for (var hairpin : line.spanRegistry().findSpans(Hairpin.class)) {
                var range = hairpin.range();
                starting.computeIfAbsent(range.begin(), unusedKey -> new ArrayList<>()).add(hairpin);
                ending.computeIfAbsent(range.end(), unusedKey -> new ArrayList<>()).add(hairpin);
            }

            if (starting.isEmpty()) {
                continue;
            }

            var count = line.elementCount();
            var outputs = entry.elements();

            for (var i = 0; i < count; i++) {
                for (var hairpin : ending.getOrDefault(i, List.of())) {
                    insertWedge(outputs.get(i), DirectionBuilder.buildHairpinStopDirection(context, hairpin));
                }

                for (var hairpin : starting.getOrDefault(i, List.of())) {
                    insertWedge(outputs.get(i), DirectionBuilder.buildHairpinStartDirection(context, hairpin));
                }
            }
        }
    }

    /**
     * A wedge bound to an element that produced no note is dropped, not emitted loose.
     */
    private static void insertWedge(MeasureBuilder.ElementOutput output, Direction wedge) {
        var measure = output.measure();
        var anchor = output.wedgeAnchor();

        if (measure == null || anchor == null) {
            return;
        }

        var items = measure.getNoteOrBackupOrForward();
        var position = MeasureBuilder.positionOf(items, anchor);

        if (position == null) {
            return;
        }

        items.add(position, wedge);
    }

    private record GlissandoSite(MeasureBuilder.LineOutput owner, int emittedIndex) {}
}
