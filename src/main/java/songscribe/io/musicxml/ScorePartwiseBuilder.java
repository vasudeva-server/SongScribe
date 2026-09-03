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
import java.util.Arrays;
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
import org.jspecify.annotations.Nullable;

// The Song-model span types, not their ProxyMusic namesakes. org.audiveris.proxymusic
// declares Beam, Tie and Tuplet too; none of those is named here, so the simple names belong
// to the document model and every ProxyMusic node of those types is created through the
// ObjectFactory and held in a var.
import songscribe.dom.Beam;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.SpanBound;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.BeamMath;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineLayoutProvider;

/**
 * Builds the whole {@link ScorePartwise} graph for a {@link Song}: the header, the single
 * part, its measures, and then the adjustment passes that attach everything spanning more
 * than one node.
 *
 * <p><b>This class is the reason for the object-model rewrite.</b> The streaming writer had
 * to decide, at the moment it passed each element, everything that element would ever carry —
 * so a span whose far end had not been reached yet became loop state
 * ({@code pendingGlissandoNote}, {@code lastNoteEndingMarkers}), and a span whose near end had
 * already been flushed became a precompute pass ({@code MusicXmlSpanIndex}) that bucketed six
 * span types onto element indices purely so the loop could read one array slot per element.
 * Here the graph is finished before any span is looked at: each span is walked once and
 * attached to the nodes it touches, through the handles {@link BuildIndex} recorded as the
 * builders emitted them.
 *
 * <p>Nothing here knows about files, streams or containers — it takes a {@code Song} and
 * returns a {@code ScorePartwise}. {@link MusicXmlSerializer} owns the transition to text, so
 * compressed {@code .mxl} output (issue #765) can reuse this class untouched.
 */
final class ScorePartwiseBuilder {

    /** {@code <part-name>} is emitted empty: SongScribe songs are single, unnamed parts. */
    private static final String EMPTY_PART_NAME = "";

    /**
     * The {@code number} every tuplet bracket and wavy line carries. SongScribe has one span
     * level, so no two are ever open at once and the number never has to distinguish them.
     */
    private static final int SINGLE_SPAN_NUMBER = 1;

    /** The lowest beam level, whose {@code <beam number="1">} is the primary beam. */
    private static final int PRIMARY_BEAM_LEVEL = 0;

    private ScorePartwiseBuilder() {}

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Builds the complete {@code <score-partwise>} graph for {@code song}.
     *
     * <p>The emitted sequence mirrors the streaming writer's: movement info, identification,
     * defaults, credits, a {@code <part-list>} holding one {@code <score-part id="P1">}, then
     * the part itself.
     *
     * @param song the song to build
     * @param fonts the document fonts, emitted under {@code <defaults>} and {@code <credit>}
     * @param layoutProvider supplies the line geometry the glissando endpoints come from
     * @param clock supplies the current date for the write-forward {@code <rights>} year and
     *     {@code <encoding-date>}, so both are deterministic under test
     * @return the finished graph, adjustment passes already applied
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
     * Creates the {@code <part-list>} with its single {@code <score-part id="P1">} and the
     * matching {@code <part>}, and returns the part.
     *
     * <p>{@code Part.setId} takes an {@code Object} because the attribute is an XML
     * {@code IDREF}: the {@code ScorePart} instance itself is passed and JAXB resolves it to
     * that part's {@code id}. Passing the string {@code "P1"} here marshals as a reference to
     * nothing.
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

    /**
     * The empty-song fallback: one measure carrying only the song-level tempo
     * {@code <direction>} and the {@code <attributes>} block — no {@code <print>} and no
     * {@code <barline>}, since there is no line to start or to break.
     */
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

    /**
     * Builds every line's measures into {@code part}, then runs the adjustment passes over the
     * finished graph.
     */
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
     * Inserts the song tempo {@code <direction>} as the very first child of measure 1, ahead
     * of its {@code <print>}.
     *
     * <p>The song tempo is a property of the score, not of any note, so it is not bound to the
     * first {@code <note>} the way a per-note tempo change is. The reader recovers it by
     * position: the first tempo {@code <direction>} in the first {@code <measure>}.
     */
    private static void addSongTempoDirection(BuildContext context, ScorePartwise.Part part) {
        var tempo = DirectionBuilder.buildTempoDirection(context, context.song().getTempo());

        if (tempo != null) {
            part.getMeasure().getFirst().getNoteOrBackupOrForward().add(0, tempo);
        }
    }

    // -------------------------------------------------------------------------
    // Adjustment passes
    // -------------------------------------------------------------------------

    /**
     * Runs every adjustment pass, in order.
     *
     * <p><b>This order is observable in the output.</b> Several passes write into the same
     * {@code Notations.getTiedOrSlurOrTuplet()} list, which marshals in list order, and
     * nothing else would catch a wrong one: {@code <notations>} content is an unbounded choice
     * in the schema so any order validates, and the reader is order-insensitive so a
     * round-trip agrees with itself either way. {@link NoteBuilder#addNotation} therefore
     * inserts by element-type rank rather than by arrival, which makes the sequence a property
     * of that ranking rather than of this list — but members of <em>equal</em> rank keep their
     * arrival order, so the two places where that matters ({@code <tied>} stop before start,
     * {@code <slide>} stop before start) are each resolved inside a single pass.
     *
     * <p>Endings are absent from this list on purpose: {@code <ending>} is a child of
     * {@code <barline>}, and {@link MeasureBuilder} owns the barlines, so it attaches them as
     * it closes each line.
     */
    private static void runAdjustmentPasses(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        applyBeams(context, lines);
        applyTies(context, lines);
        applyTuplets(context, lines);
        applyTrills(context, lines);
        applyGlissandos(context, glissandoSites(lines));
        applyHairpins(context, lines);
    }

    /**
     * Every glissando in the song, as the position of its owning note within its line's emitted
     * notes. The destination is the following entry in that list, which is why a site is a
     * position rather than a pair.
     */
    private static List<GlissandoSite> glissandoSites(List<MeasureBuilder.LineOutput> lines) {
        var sites = new ArrayList<GlissandoSite>();

        for (var lineOutput : lines) {
            var emitted = lineOutput.emitted();

            for (var i = 0; i < emitted.size(); i++) {
                if (emitted.get(i).getGlissando() != null) {
                    sites.add(new GlissandoSite(lineOutput, i));
                }
            }
        }

        return sites;
    }

    /**
     * Attaches one {@code <beam number="N">} per active beam level to every note in each beam
     * group.
     *
     * <p>A degenerate single-note beam produces nothing — one note cannot be beamed — and a
     * grace note inside the span is not a member: the beam passes over it (refs #592).
     */
    private static void applyBeams(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        forEachSpanInRange(lines, Beam.class, (line, beam, anchorIndex, endIndex) -> {
            if (anchorIndex == endIndex) {
                return;
            }

            for (var i = anchorIndex; i <= endIndex; i++) {
                var element = line.getElement(i);

                if (element.getType().isGraceNote()) {
                    continue;
                }

                addBeams(context, element, beamValues(line, i, anchorIndex, endIndex));
            }
        });
    }

    /** Adds one {@code <beam>} per level whose value is not the "no beam here" sentinel. */
    private static void addBeams(BuildContext context, StaffElement element, String[] levelValues) {
        var pmNote = context.index().notes().get(element);

        if (pmNote == null) {
            return;
        }

        for (var level = 0; level < levelValues.length; level++) {
            var value = levelValues[level];

            if (value.isEmpty()) {
                continue;
            }

            var beam = context.factory().createBeam();
            beam.setValue(BeamValue.fromValue(value));
            beam.setNumber(level + 1);
            pmNote.getBeam().add(beam);
        }
    }

    /**
     * The {@code <beam>} text-content values for the note at {@code noteIndex} within the beam
     * group [{@code anchorIndex}, {@code endIndex}], indexed by {@code number - 1}. An empty
     * entry means no {@code <beam>} at that level.
     *
     * <p>Level 0 (the primary beam) is always {@code begin}/{@code continue}/{@code end},
     * never a hook. For each secondary level, the maximal contiguous run of notes at that
     * level containing {@code noteIndex} decides: a run of two or more emits
     * {@code begin}/{@code continue}/{@code end}, and a run of one is a partial-beam hook
     * whose direction {@link BeamMath#stubRight} owns.
     */
    private static String[] beamValues(Line line, int noteIndex, int anchorIndex, int endIndex) {
        var values = new String[BeamMath.LEVEL_COUNT];
        Arrays.fill(values, MusicXmlUnits.NO_BEAM_AT_LEVEL);
        values[PRIMARY_BEAM_LEVEL] = runValue(noteIndex, anchorIndex, endIndex);

        for (var level = PRIMARY_BEAM_LEVEL + 1; level < BeamMath.LEVEL_COUNT; level++) {
            if (!BeamMath.noteTypeInLevel(line, noteIndex, level)) {
                continue;
            }

            var runStart = noteIndex;

            while (runStart > anchorIndex && BeamMath.noteTypeInLevel(line, runStart - 1, level)) {
                runStart--;
            }

            var runEnd = noteIndex;

            while (runEnd < endIndex && BeamMath.noteTypeInLevel(line, runEnd + 1, level)) {
                runEnd++;
            }

            if (runStart == runEnd) {
                values[level] = BeamMath.stubRight(line, noteIndex, anchorIndex, endIndex)
                    ? MusicXmlTags.BEAM_FORWARD_HOOK
                    : MusicXmlTags.BEAM_BACKWARD_HOOK;
            } else {
                values[level] = runValue(noteIndex, runStart, runEnd);
            }
        }

        return values;
    }

    /** The {@code begin}/{@code continue}/{@code end} value for a position within a beam run. */
    private static String runValue(int noteIndex, int runStart, int runEnd) {
        if (noteIndex == runStart) {
            return MusicXmlTags.BEAM_BEGIN;
        }

        if (noteIndex == runEnd) {
            return MusicXmlTags.BEAM_END;
        }

        return MusicXmlTags.BEAM_CONTINUE;
    }

    /**
     * Attaches the sound {@code <tie>} and the notation {@code <tied>} for every tie.
     *
     * <p>Each note is visited once and emits its stop before its start, so an interior note of
     * a chain — the end of one tie and the anchor of the next — closes the loop the way the
     * streaming writer did. Rests cannot be tied.
     *
     * <p>The endpoints are resolved <em>relative to the line</em>, through
     * {@code anchorIndexOf}/{@code endIndexOf}, not read off the tie. Reading them off the tie
     * would hand a cross-line tie the same two indices in both lines, so each line would emit
     * a start <em>and</em> a stop and the file would carry unpaired markers.
     */
    private static void applyTies(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            var line = entry.line();
            var count = line.elementCount();
            var ties = line.findTies();

            if (ties.isEmpty()) {
                continue;
            }

            var starts = new Tie[count];
            var stops = new Tie[count];

            for (var tie : ties) {
                // No range check: an At bound is a position in this line by construction.
                if (line.anchorIndexOf(tie) instanceof SpanBound.At(var anchorIndex)) {
                    starts[anchorIndex] = tie;
                }

                if (line.endIndexOf(tie) instanceof SpanBound.At(var endIndex)) {
                    stops[endIndex] = tie;
                }
            }

            for (var i = 0; i < count; i++) {
                var element = line.getElement(i);

                if (element.getType().isRest()) {
                    continue;
                }

                var pmNote = context.index().notes().get(element);

                if (pmNote == null) {
                    continue;
                }

                addTie(context, pmNote, stops[i], StartStop.STOP, TiedType.STOP);
                addTie(context, pmNote, starts[i], StartStop.START, TiedType.START);
            }
        }
    }

    /**
     * Adds the {@code <tie>}/{@code <tied>} pair for one end of {@code tie}, or nothing when
     * this note is not that end.
     *
     * <p>{@code <tied orientation>} is write-forward: the reader ignores it, because tie
     * direction is fully determined by the stems it is redrawn from.
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

    /**
     * Attaches the {@code <tuplet>} bracket: {@code type="start"} on the anchor, carrying the
     * group's vertical position as {@code relative-y} when non-zero, and {@code type="stop"}
     * on the end note.
     *
     * <p>The {@code <time-modification>} every member carries is not this pass's business —
     * that is decided by the element's own position in the line, so {@link NoteBuilder} sets
     * it while building the note.
     */
    private static void applyTuplets(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        forEachSpanInRange(lines, Tuplet.class, (line, tuplet, anchorIndex, endIndex) -> {
            addTupletBracket(context, line.getElement(anchorIndex), StartStop.START,
                tuplet.getVerticalPositionSs());
            addTupletBracket(context, line.getElement(endIndex), StartStop.STOP, 0);
        });
    }

    /** Attaches one {@code <tuplet>} bracket marker to {@code element}'s note, if it has one. */
    private static void addTupletBracket(
            BuildContext context, StaffElement element, StartStop type, double verticalShiftSs) {
        var pmNote = context.index().notes().get(element);

        if (pmNote == null) {
            return;
        }

        var pmTuplet = context.factory().createTuplet();
        pmTuplet.setType(type);
        pmTuplet.setNumber(SINGLE_SPAN_NUMBER);
        pmTuplet.setRelativeY(MusicXmlUnits.shiftTenths(verticalShiftSs));
        NoteBuilder.addNotation(context, pmNote, pmTuplet);
    }

    /**
     * Attaches the trill {@code <ornaments>}: {@code <trill-mark/>} plus
     * {@code <wavy-line type="start">} on the anchor, and {@code <wavy-line type="stop">} on
     * the end note.
     *
     * <p>A single-note trill has anchor == end, so all three land in one {@code <ornaments>}
     * element — which is why {@link #ornamentsOf} reuses the note's existing one rather than
     * creating a second. Rests carry no trill.
     */
    private static void applyTrills(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        forEachSpanInRange(lines, Trill.class, (line, trill, anchorIndex, endIndex) -> {
            addTrillStart(context, line.getElement(anchorIndex), trill);
            addWavyLine(context, line.getElement(endIndex), StartStopContinue.STOP, 0);
        });
    }

    /** Attaches {@code <trill-mark/>} and the opening {@code <wavy-line>} to the anchor note. */
    private static void addTrillStart(BuildContext context, StaffElement element, Trill trill) {
        var pmNote = trilledNote(context, element);

        if (pmNote == null) {
            return;
        }

        var factory = context.factory();
        ornamentsOf(context, pmNote).getTrillMarkOrTurnOrDelayedTurn()
            .add(factory.createOrnamentsTrillMark(factory.createEmptyTrillSound()));

        addWavyLine(context, element, StartStopContinue.START, trill.getYPositionSs());
    }

    /** Attaches one {@code <wavy-line>} marker to {@code element}'s note, if it takes one. */
    private static void addWavyLine(
            BuildContext context, StaffElement element, StartStopContinue type, double verticalShiftSs) {
        var pmNote = trilledNote(context, element);

        if (pmNote == null) {
            return;
        }

        var factory = context.factory();
        var wavyLine = factory.createWavyLine();
        wavyLine.setType(type);
        wavyLine.setNumber(SINGLE_SPAN_NUMBER);
        wavyLine.setRelativeY(MusicXmlUnits.shiftTenths(verticalShiftSs));
        ornamentsOf(context, pmNote).getTrillMarkOrTurnOrDelayedTurn()
            .add(factory.createOrnamentsWavyLine(wavyLine));
    }

    /** {@code element}'s note when it can carry a trill, else null — a rest never can. */
    private static @Nullable Note trilledNote(BuildContext context, StaffElement element) {
        if (element.getType().isRest()) {
            return null;
        }

        return context.index().notes().get(element);
    }

    /**
     * Returns {@code pmNote}'s {@code <ornaments>}, creating and attaching it on first use, so
     * a note reached twice by one trill grows one element rather than two.
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
     * Attaches the {@code <slide>} pair of every glissando: {@code type="start"} on the note
     * that owns it and {@code type="stop"} on the next emitted note.
     *
     * <p><b>This pass iterates glissandos, never lines, and the difference is not stylistic.</b>
     * Slide endpoints are the only geometry the writer emits, so a line with no glissando is
     * deliberately never laid out: a layout costs real work <em>and</em> resolves the line's
     * automatic stem directions as a side effect, so laying out a line the old path never
     * touched would change that line's output. Walking lines and asking {@code layoutProvider}
     * for each would lay out the whole song on every save. Each site reaches its line through
     * the glissando that needs it, so a line with none is never reached; the cache keeps a line
     * carrying several glissandos to one layout.
     *
     * <p>Glissandos are intra-line and cannot span a system break — the sites are recorded per
     * line as the notes are emitted, so a dangling glissando on the last note of a line finds
     * no target and simply emits no stop, exactly as the streaming writer's per-line reset did.
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
     * Attaches one {@code <slide>} to {@code element}'s note, carrying the endpoint
     * {@code type} names — the drawn line's end for a stop, its start for a start.
     *
     * <p>The coordinates come from the line's layout, so they are the ones the score is
     * painted from. They are write-forward only; nothing on the read side looks at them. A
     * glissando too short to draw stores no geometry, and is emitted without coordinates
     * rather than dropped, since it is still musically present.
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
     * Inserts each hairpin's wedge {@code <direction>}s immediately before the note they bind
     * to — the start wedge before the anchor note, the stop wedge before the end note.
     *
     * <p>Both wedges bind <em>forward</em>, which is what gives the reader one uniform
     * look-ahead rule. Stops precede starts on a note that carries both, so one hairpin's
     * close and the next one's open keep a natural order.
     */
    private static void applyHairpins(BuildContext context, List<MeasureBuilder.LineOutput> lines) {
        for (var entry : lines) {
            var line = entry.line();

            // Crescendos and diminuendos share one pair of buckets; the wedge type is
            // recovered from the Hairpin subtype when the direction is built. Only the
            // indices that actually carry a hairpin get an entry — a long line with one
            // crescendo should not allocate a list per element.
            var starting = new HashMap<Integer, List<Hairpin>>();
            var ending = new HashMap<Integer, List<Hairpin>>();

            forEachSpanInRange(line, Hairpin.class, (unusedLine, hairpin, anchorIndex, endIndex) -> {
                starting.computeIfAbsent(anchorIndex, unusedKey -> new ArrayList<>()).add(hairpin);
                ending.computeIfAbsent(endIndex, unusedKey -> new ArrayList<>()).add(hairpin);
            });

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
     * Inserts {@code wedge} immediately before everything the element contributed apart from
     * its tempo and metric-modulation directions, so it lands before the annotation direction —
     * the streaming writer's order.
     *
     * <p>An element that produced no note has no anchor, and a wedge bound to it has nothing to
     * bind to, so it is dropped rather than emitted loose.
     */
    private static void insertWedge(MeasureBuilder.ElementOutput output, Direction wedge) {
        var measure = output.measure();
        var anchor = output.wedgeAnchor();

        if (measure == null || anchor == null) {
            return;
        }

        var items = measure.getNoteOrBackupOrForward();
        var position = MeasureBuilder.positionOf(items, anchor);

        if (position < 0) {
            return;
        }

        items.add(position, wedge);
    }

    /**
     * One glissando, as the position within its line's emitted notes of the note that owns it.
     * The destination is the following entry in that list, which is why the site is a position
     * rather than a pair.
     */
    private record GlissandoSite(MeasureBuilder.LineOutput owner, int emittedIndex) {}


    // -------------------------------------------------------------------------
    // The span-pass skeleton
    // -------------------------------------------------------------------------

    /**
     * What one span pass does to a single span whose endpoints both land inside {@code line}.
     * The indices are passed in because {@link #forEachSpanInRange} has already read and
     * checked them; re-reading them from the span would be asking the same question twice.
     */
    @FunctionalInterface
    private interface SpanAction<T extends Span> {
        void apply(Line line, T span, int anchorIndex, int endIndex);
    }

    /**
     * Runs {@code action} for every {@code spanType} span in {@code line} whose two endpoints
     * both fall inside the line's element range.
     *
     * <p>A span that fails the check is skipped silently, and that is the decision this method
     * exists to hold in one place: {@link Span#getAnchorElementIndex()} answers from whichever
     * line the endpoint element belongs to, so a span left dangling by an edit reports an index
     * that means nothing here. Writing its marker anyway would attach a beam or a bracket to an
     * unrelated note, which is worse than omitting a span the model should not have been
     * holding.
     */
    private static <T extends Span> void forEachSpanInRange(
            Line line, Class<T> spanType, SpanAction<T> action) {
        var count = line.elementCount();

        for (var span : line.findSpans(spanType)) {
            var anchorIndex = span.getAnchorElementIndex();
            var endIndex = span.getEndElementIndex();

            if (!MeasureBuilder.indicesInRange(anchorIndex, endIndex, count)) {
                continue;
            }

            action.apply(line, span, anchorIndex, endIndex);
        }
    }

    /** {@link #forEachSpanInRange(Line, Class, SpanAction)} over every line in document order. */
    private static <T extends Span> void forEachSpanInRange(
            List<MeasureBuilder.LineOutput> lines, Class<T> spanType, SpanAction<T> action) {
        for (var entry : lines) {
            forEachSpanInRange(entry.line(), spanType, action);
        }
    }
}
