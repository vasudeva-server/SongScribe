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

import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.Attributes;
import org.audiveris.proxymusic.Barline;
import org.audiveris.proxymusic.Direction;
import org.audiveris.proxymusic.FormattedTextId;
import org.audiveris.proxymusic.LeftCenterRight;
import org.audiveris.proxymusic.Metronome;
import org.audiveris.proxymusic.MetronomeNote;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.Print;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.Wedge;
import org.audiveris.proxymusic.WedgeType;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

/**
 * Maps the first {@code <part>} of an unmarshalled {@code <score-partwise>} onto the
 * {@link Song}'s {@link Line}s: the line split, the running key signature, the barlines
 * and voltas, the measure-level {@code <direction>}s (hairpins, tempo marks, metric
 * modulations, annotations) and the per-note range spans. Each {@code <note>} itself is
 * mapped by {@link NoteMapper}.
 *
 * <p><b>Shape.</b> One structural walk, then one resolution pass per mark that pairs two
 * positions — the read-side mirror of {@code ScorePartwiseBuilder}'s adjustment passes.
 * The walk builds only what a single child settles on its own: it starts a line at each
 * new-system marker, advances the key signature, appends an element per barline and calls
 * {@link NoteMapper} per note. What each child produced is recorded in its
 * {@link ChildSite}, so a pass can ask where an element sits in the document without
 * re-deriving it.
 *
 * <p>The passes then run over the finished sites. A mark whose two ends are different
 * positions — a beam, a tie, a tuplet bracket, a trill, a glissando, a hairpin, a volta —
 * is settled by reading both ends, because the whole part is already parsed. Nothing is
 * held on the mapper: each pass keeps its own cursor as a local, and its "still open at
 * the end" check is the last statement of the method that opened it.
 *
 * <p>Four rules are order-sensitive and live in the passes that own them: a tie's stop
 * resolves before its start on the same note; a hairpin's stop before its start, except
 * when one note both opens and closes one; the first tempo mark of the first measure is
 * the song's own; and a tie may cross a line boundary, while a hairpin and a volta may
 * not.
 *
 * <p>A {@link Line} is committed to the song only once its elements are complete. While a
 * line is detached it is never the song's last line, which is what makes
 * {@code Line.addElement} append in document order instead of inserting before a valid
 * terminal barline that lands mid-line. Spans are added afterwards, to the committed
 * line: a load suspends mutation tracking, so the terminal invariant is not maintained
 * while the passes run and no element list moves underneath them.
 */
final class MeasureMapper {

    private static final Logger LOG = LoggerFactory.getLogger(MeasureMapper.class);

    // A SongScribe metric modulation always relates exactly two note values
    // (duration equals beat), so the writer emits exactly two <metronome-note>s.
    private static final int METRIC_MODULATION_NOTE_COUNT = 2;

    // The markers named in the pairing diagnostics, one pair per span kind.
    private static final String SLIDE_START_MARKER = "<slide type=\"start\">";
    private static final String BEAM_BEGIN_MARKER = "<beam>begin</beam>";
    private static final String BEAM_END_MARKER = "<beam>end</beam>";
    private static final String TIED_START_MARKER = "<tied type=\"start\">";
    private static final String TIED_STOP_MARKER = "<tied type=\"stop\">";
    private static final String WAVY_LINE_START_MARKER = "<wavy-line type=\"start\">";
    private static final String WAVY_LINE_STOP_MARKER = "<wavy-line type=\"stop\">";

    // -------------------------------------------------------------------------
    // What the walk records
    // -------------------------------------------------------------------------

    /** One mapped {@code <note>}: the parsed node, its element, and the line it landed on. */
    private record NoteSite(Note pmNote, StaffElement element, Line line) {}

    /**
     * One child of one measure, together with what the structural walk built from it.
     * The resolution passes read these rather than the {@link Line}s, so each pass sees
     * every element in document order with no live index lookups.
     */
    private static final class ChildSite {

        private final int measureIndex;
        private final Object child;

        /** The line current when this child was walked; null before the first line. */
        @Nullable
        private Line line = null;

        /** The last element this child appended, or null when it appended none. */
        @Nullable
        private StaffElement lastElement = null;

        /** The element this barline's {@code <ending>} marker attaches to. */
        @Nullable
        private StaffElement endingHost = null;

        /** The element an annotation {@code <direction>} before this child binds to. */
        @Nullable
        private StaffElement annotationHost = null;

        /** Non-null exactly when this child is a mapped {@code <note>}. */
        @Nullable
        private NoteSite noteSite = null;

        /** True when this backward repeat merges into a later forward-left one. */
        private boolean mergedIntoLaterBarline = false;

        /** The backward half this forward-left repeat merges with, when there is one. */
        @Nullable
        private ChildSite mergedBackwardHalf = null;

        private ChildSite(int measureIndex, Object child) {
            this.measureIndex = measureIndex;
            this.child = child;
        }
    }

    /** The parts of a {@code <direction>} the resolution passes read. */
    private record DirectionContent(
        Annotation.@Nullable Placement placement,
        @Nullable FormattedTextId words,
        @Nullable Metronome metronome
    ) {}

    /** A {@code <wedge>} start's kind and its user-adjustable shifts. */
    private record WedgeStart(Hairpin.Kind kind, int x1ShiftSs, int yShiftSs) {}

    /** A hairpin whose start wedge has bound to {@code anchor} and awaits its stop. */
    private record OpenHairpin(StaffElement anchor, WedgeStart start) {}

    /** A tuplet bracket whose start has bound to {@code anchor} and awaits its stop. */
    private record OpenTuplet(StaffElement anchor, NoteMapper.TupletMarker marker) {}

    /** A trill whose start wavy line has bound to {@code anchor} and awaits its stop. */
    private record OpenTrill(StaffElement anchor, int yPositionSs) {}

    // -------------------------------------------------------------------------
    // Walk state — the whole of it
    // -------------------------------------------------------------------------

    private final Song song;

    @Nullable
    private Line currentLine = null;

    // The signed-fifths key signature currently in effect. Measure 1 seeds it from
    // the song default; a later line's <key> advances it. It is applied to each new
    // line so a key persists across lines until restated — mirroring the writer,
    // which emits a <key> only when a line's key differs from this running value.
    private int runningFifths = 0;

    // True once the measure-1 <key> has set the song default and seeded
    // runningFifths, distinguishing the song-default key from a per-line change.
    private boolean songDefaultKeySet = false;

    // True once any note's <accidental> token was a retired legacy token converted
    // to its replacement; carried into SongLoadResult.Success by the caller.
    private boolean accidentalsConverted = false;

    private MeasureMapper(Song song) {
        this.song = song;
    }

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Maps {@code score}'s first part onto {@code song}'s lines.
     *
     * @return true when any note's {@code <accidental>} was a retired legacy token
     *         converted to its replacement
     * @throws SAXException if the document is corrupt (a tuplet with no valid span,
     *                      a note before any line has been started)
     */
    static boolean map(ScorePartwise score, Song song) throws SAXException {
        var mapper = new MeasureMapper(song);
        var part = ProxyMusicAccess.firstPart(score);

        if (part != null) {
            mapper.mapPart(part);
        }

        return mapper.accidentalsConverted;
    }

    private void mapPart(ScorePartwise.Part part) throws SAXException {
        var sites = flatten(part);
        pairMergedRepeats(sites);
        walk(sites);

        var notes = new ArrayList<NoteSite>();

        for (var site : sites) {
            var noteSite = site.noteSite;

            if (noteSite != null) {
                notes.add(noteSite);
            }
        }

        resolveEndings(sites);
        resolveAnnotations(sites);
        resolveMetronomes(sites);
        resolveHairpins(sites);
        resolveSlides(notes);
        resolveBeams(notes);
        resolveTies(notes);
        resolveTuplets(notes);
        resolveTrills(notes);
    }

    // -------------------------------------------------------------------------
    // Structural walk
    // -------------------------------------------------------------------------

    /**
     * The part's measure children as one sequence. Flattening is what lets the
     * {@code REPEAT_LEFT_RIGHT} pairing see across the measure boundary the pair always
     * straddles, and what gives every child a measure index the song-tempo rule can read.
     */
    private static List<ChildSite> flatten(ScorePartwise.Part part) {
        var sites = new ArrayList<ChildSite>();
        var measures = part.getMeasure();

        for (var measureIndex = 0; measureIndex < measures.size(); measureIndex++) {
            for (var child : measures.get(measureIndex).getNoteOrBackupOrForward()) {
                sites.add(new ChildSite(measureIndex, child));
            }
        }

        return sites;
    }

    private void walk(List<ChildSite> sites) throws SAXException {
        for (var site : sites) {
            var child = site.child;

            if (child instanceof Print print) {
                if (ProxyMusicAccess.startsNewSystem(print)) {
                    startNewLine();
                }
            } else if (child instanceof Attributes attributes) {
                mapAttributes(attributes);
            } else if (child instanceof Barline barline) {
                walkBarline(site, barline);
            } else if (child instanceof Note pmNote) {
                walkNote(site, pmNote);
            }

            site.line = currentLine;
        }

        commitCurrentLine();
    }

    // -------------------------------------------------------------------------
    // Lines and key signature
    // -------------------------------------------------------------------------

    /**
     * Commits the line that just ended and starts the next one detached from the
     * song. The new line is not added until it, too, is complete.
     */
    private void startNewLine() {
        commitCurrentLine();

        var line = new Line(song);
        currentLine = line;

        // A key signature persists until restated. For every line after the first,
        // seed the new line with the running key so lines that keep it (the writer
        // emits no <key> for them) still round-trip; a <key> in this line's first
        // measure overrides it. The first line is skipped: its key is the song
        // default, materialized when the line is added.
        if (songDefaultKeySet) {
            applyFifthsToLine(line, runningFifths);
        }
    }

    private void commitCurrentLine() {
        if (currentLine != null) {
            song.addLine(currentLine);
        }
    }

    private void mapAttributes(Attributes attributes) {
        for (var key : attributes.getKey()) {
            var fifths = ProxyMusicAccess.keyFifths(key);

            if (fifths != null) {
                applyFifths(fifths);
            }
        }
    }

    /**
     * Applies a {@code <key>}: measure 1's key is the song default and seeds the
     * running key; every later one is a per-line key change.
     */
    private void applyFifths(int fifths) {
        if (songDefaultKeySet) {
            runningFifths = fifths;
            var line = currentLine;

            if (line != null) {
                applyFifthsToLine(line, fifths);
            }

            return;
        }

        // Line 1 itself is materialized from the default when it is added, so it is
        // not set here.
        song.setDefaultKeyAccidentalCount(KeySignatureMapping.accidentalCount(fifths));
        song.setDefaultKeyType(KeySignatureMapping.keyType(fifths));
        runningFifths = fifths;
        songDefaultKeySet = true;
    }

    /** Applies a signed-fifths key signature to {@code line} — the writer's inverse. */
    private static void applyFifthsToLine(Line line, int fifths) {
        line.setKeyType(KeySignatureMapping.keyType(fifths));
        line.setKeyAccidentalCount(KeySignatureMapping.accidentalCount(fifths));
    }

    // -------------------------------------------------------------------------
    // Barlines
    // -------------------------------------------------------------------------

    /**
     * Marks each backward repeat that pairs with an immediately following forward-left
     * one: the two are the halves of a single {@code REPEAT_LEFT_RIGHT}, created at the
     * forward half's position so it lands where the document puts it.
     *
     * <p>The scan stops at the first child that would have separated the two halves in
     * document order: a note, a line break, or any other barline. Everything it skips
     * ({@code <attributes>}, {@code <direction>}) sits between the two halves of a real
     * pair.
     */
    private static void pairMergedRepeats(List<ChildSite> sites) {
        for (var index = 0; index < sites.size(); index++) {
            var site = sites.get(index);

            if (!(site.child instanceof Barline barline)
                || barlineElementType(barline) != ElementType.REPEAT_RIGHT) {
                continue;
            }

            var partner = repeatLeftPartner(sites, index);

            if (partner != null) {
                site.mergedIntoLaterBarline = true;
                partner.mergedBackwardHalf = site;
            }
        }
    }

    @Nullable
    private static ChildSite repeatLeftPartner(List<ChildSite> sites, int index) {
        for (var scan = index + 1; scan < sites.size(); scan++) {
            var site = sites.get(scan);
            var child = site.child;

            if (child instanceof Barline barline) {
                var isForwardLeft = barlineElementType(barline) == ElementType.REPEAT_LEFT
                    && BarlineStyleMapping.LOCATION_LEFT.equals(ProxyMusicAccess.barlineLocationToken(barline));
                return isForwardLeft ? site : null;
            }

            if (child instanceof Note) {
                return null;
            }

            if (child instanceof Print print && ProxyMusicAccess.startsNewSystem(print)) {
                return null;
            }
        }

        return null;
    }

    private void walkBarline(ChildSite site, Barline barline) throws SAXException {
        if (site.mergedIntoLaterBarline) {
            // The merged element is created at the forward half's position; this half's
            // <ending> marker and the annotation before it are bound to it there.
            return;
        }

        var elementType = barlineElementType(barline);

        if (elementType == null) {
            // Invisible barline (bar-style none or absent), or an unknown style/repeat
            // combination: no element. An invisible LEFT barline can still host a
            // note-anchored volta marker, which carries no stored value.
            return;
        }

        var backwardHalf = site.mergedBackwardHalf;
        var element = appendToCurrentLine(backwardHalf == null ? elementType : ElementType.REPEAT_LEFT_RIGHT);
        site.lastElement = element;
        site.endingHost = element;

        if (backwardHalf == null) {
            site.annotationHost = element;
        } else {
            // Both halves' <ending> markers attach to the merged element, and the
            // ending pass reads them in document order — the backward half's first.
            // The annotation slot belongs to the backward half alone, so a direction
            // sitting between the two halves binds past the merged element instead.
            backwardHalf.endingHost = element;
            backwardHalf.annotationHost = element;
        }
    }

    /**
     * The {@link ElementType} a barline contributes, or {@code null} for one that
     * contributes none: an invisible barline (a line-break marker), or a
     * style/repeat combination this reader does not know. A missing
     * {@code <bar-style>} is treated exactly like {@code none} rather than throwing
     * or inserting a default barline the file never asked for.
     */
    @Nullable
    private static ElementType barlineElementType(Barline barline) {
        var barStyle = ProxyMusicAccess.barStyleToken(barline);

        if (barStyle == null || BarlineStyleMapping.BAR_STYLE_NONE.equals(barStyle)) {
            return null;
        }

        return BarlineStyleMapping.forBarStyle(barStyle, ProxyMusicAccess.repeatDirectionToken(barline));
    }

    /**
     * Appends a structural (non-duration) element to the current line.
     *
     * @throws SAXException if no line has been started yet
     */
    private StaffElement appendToCurrentLine(ElementType elementType) throws SAXException {
        var line = requireCurrentLine("Barline element encountered before any line was started");
        var element = elementType.newInstance();
        line.addElement(element);
        return element;
    }

    private Line requireCurrentLine(String message) throws SAXException {
        var line = currentLine;

        if (line == null) {
            throw new SAXException(message);
        }

        return line;
    }

    // -------------------------------------------------------------------------
    // Notes
    // -------------------------------------------------------------------------

    private void walkNote(ChildSite site, Note pmNote) throws SAXException {
        var line = requireCurrentLine("<note> encountered before any line was started");
        var mapped = NoteMapper.map(pmNote, line);
        var element = mapped.element();

        if (mapped.usedLegacyAccidental()) {
            accidentalsConverted = true;
        }

        site.noteSite = new NoteSite(pmNote, element, line);
        site.annotationHost = element;
        site.lastElement = element;

        // A breath mark attached to this note's <notations> becomes a standalone
        // BREATH_MARK element immediately after the note.
        if (mapped.hasBreathMark()) {
            site.lastElement = appendToCurrentLine(ElementType.BREATH_MARK);
        }
    }

    // -------------------------------------------------------------------------
    // Pass: endings (voltas)
    // -------------------------------------------------------------------------

    /**
     * Pairs each {@code <ending number="1" type="start">} with the following
     * {@code <ending number="2" type="stop">}, inverting the writer's split expansion:
     *
     * <pre>
     *   1 start ─► the ending's anchor
     *   2 stop  ─► the ending's end; the split is recomputed live
     *   1 stop  ─► ignored (the volta-1 split close)
     *   2 start ─► ignored (the volta-2 open)
     * </pre>
     *
     * <p>Either marker may ride on an invisible barline, which produces no element of
     * its own (issue #306): a note-anchored start then binds to the next mapped note,
     * and a note-terminated stop to the last element the line had at that point.
     *
     * <p>An ending is an intra-line span, so one still open when a new line starts is
     * dropped, as is one still open at the end of the part: a complete ending is built
     * on its {@code number="2"} stop, so anything left over is not a valid ending.
     */
    private static void resolveEndings(List<ChildSite> sites) {
        StaffElement anchor = null;
        var anchorFromNextNote = false;
        StaffElement lastElement = null;
        Line line = null;

        for (var site : sites) {
            if (site.line != line) {
                warnIncompleteEnding(anchor);
                anchor = null;
                lastElement = null;
                line = site.line;
            }

            var noteSite = site.noteSite;

            if (noteSite != null && anchorFromNextNote) {
                anchor = noteSite.element();
                anchorFromNextNote = false;
            }

            if (site.child instanceof Barline barline) {
                var marker = ProxyMusicAccess.endingMarker(barline);

                if (marker != null) {
                    var host = site.endingHost;
                    var isStop = MusicXmlTags.TYPE_STOP.equals(marker.type())
                        || MusicXmlTags.ENDING_DISCONTINUE.equals(marker.type());

                    if (MusicXmlTags.NUMBER_1.equals(marker.number())
                        && MusicXmlTags.TYPE_START.equals(marker.type())) {
                        warnIncompleteEnding(anchor);
                        // A note-anchored start defers to the next note, which the
                        // invisible barline carrying it always precedes — including
                        // across a line break, so the deferral outlives the drop above.
                        anchorFromNextNote = host == null;
                        anchor = host;
                    } else if (MusicXmlTags.NUMBER_2.equals(marker.number()) && isStop) {
                        var end = host != null ? host : lastElement;

                        if (anchor != null && end != null) {
                            buildEnding(line, anchor, end);
                            anchor = null;
                            anchorFromNextNote = false;
                        } else {
                            LOG.warn("Ignoring <ending number=\"2\" type=\"stop\"> with no open ending");
                        }
                    }

                    // A number="1" stop (the volta-1 split close) and a number="2" start
                    // (the volta-2 open) carry no state: the split is recomputed live
                    // from the element types between anchor and end.
                }
            }

            if (site.lastElement != null) {
                lastElement = site.lastElement;
            }
        }

        warnIncompleteEnding(anchor);
    }

    private static void warnIncompleteEnding(@Nullable StaffElement anchor) {
        if (anchor != null) {
            LOG.warn("Dropping incomplete <ending> with no number=\"2\" stop");
        }
    }

    /**
     * Builds one {@link Ending} over [{@code anchor}, {@code end}] and adds it to
     * {@code line}, unless it is split-less. Every ending must have a REPEAT splitting
     * its two brackets; a span with no such element is not a valid ending and is
     * dropped on import.
     */
    private static void buildEnding(@Nullable Line line, StaffElement anchor, StaffElement end) {
        if (line == null) {
            return;
        }

        var ending = new Ending(anchor, end);

        if (ending.findRepeatSplitElement(line) == null) {
            LOG.warn("Dropping split-less <ending> on import (no repeat between anchor and end)");
            return;
        }

        line.addSpan(ending);
    }

    // -------------------------------------------------------------------------
    // Pass: annotation directions
    // -------------------------------------------------------------------------

    /**
     * Binds each annotation {@code <direction>} to the next element appended after it —
     * a note or a barline. A second annotation arriving before that element replaces the
     * first, which is what this program's own documents never contain, so the loss is
     * logged rather than silent.
     */
    private static void resolveAnnotations(List<ChildSite> sites) {
        Annotation pending = null;

        for (var site : sites) {
            if (site.child instanceof Direction direction) {
                var annotation = annotationOf(direction);

                if (annotation != null) {
                    if (pending != null) {
                        LOG.warn(
                            "Dropping unbound annotation <direction>: a second one arrived before an element to bind it to");
                    }

                    pending = annotation;
                }

                continue;
            }

            var host = site.annotationHost;

            if (host != null && pending != null) {
                host.addAttachment(new AnnotationAttachment(host, pending));
                pending = null;
            }
        }

        if (pending != null) {
            LOG.warn("Dropping annotation <direction> with no bound note");
        }
    }

    /**
     * The {@link Annotation} a {@code <direction>} carries, or {@code null} when it
     * carries none. A direction is an annotation iff it has a {@code placement}
     * attribute: the writer emits {@code placement} only on annotation directions, so
     * its presence is the unambiguous discriminator, and a words-only direction without
     * it is a tempo description rather than a phantom annotation.
     */
    @Nullable
    private static Annotation annotationOf(Direction direction) {
        var content = readDirection(direction);
        var placement = content.placement();
        var words = content.words();

        if (placement == null || words == null) {
            return null;
        }

        var text = words.getValue();

        if (text == null) {
            return null;
        }

        var annotation = new Annotation(text);
        annotation.setXAlignment(
            TextAlignmentMapping.xAlignment(ProxyMusicAccess.token(words.getHalign(), LeftCenterRight::value))
        );
        annotation.setPlacement(placement);
        annotation.setUserYOffsetSs(ProxyMusicAccess.tenthsToSs(words.getRelativeY()));
        return annotation;
    }

    /**
     * Reads the three parts of a {@code <direction>} the passes consume. Exactly one of
     * the two contents is ever built from one direction: a metronome direction carries
     * no placement, an annotation direction carries no {@code <metronome>}.
     */
    private static DirectionContent readDirection(Direction direction) {
        FormattedTextId words = null;
        Metronome metronome = null;

        for (var directionType : direction.getDirectionType()) {
            var directionMetronome = directionType.getMetronome();

            if (directionMetronome != null) {
                metronome = directionMetronome;
            }

            for (var wordsOrSymbol : directionType.getWordsOrSymbol()) {
                if (words == null && wordsOrSymbol instanceof FormattedTextId formatted) {
                    words = formatted;
                }
            }
        }

        return new DirectionContent(
            PlacementMapping.placementFor(ProxyMusicAccess.token(direction.getPlacement(), AboveBelow::value)),
            words,
            metronome
        );
    }

    @Nullable
    private static String textOf(@Nullable FormattedTextId words) {
        return words == null ? null : words.getValue();
    }

    // -------------------------------------------------------------------------
    // Pass: metronome directions — tempo marks and metric modulations
    // -------------------------------------------------------------------------

    /**
     * Binds each {@code <metronome>} direction to the next note. The first tempo mark of
     * the first measure is the score's own tempo: it binds to no note and is applied to
     * the song directly.
     *
     * <p>That test is purely positional and deliberately says nothing about where within
     * the measure the direction sits — the writer emits it as the measure's first child,
     * but files written before that change carry it immediately before the first note,
     * and both must load the same way.
     */
    private void resolveMetronomes(List<ChildSite> sites) throws SAXException {
        if (sites.isEmpty()) {
            return;
        }

        // Which measure is "the first" is taken from document order rather than from the
        // number attribute, which a foreign file is not obliged to start at 1.
        var firstMeasureIndex = sites.getFirst().measureIndex;
        var songTempoTaken = false;
        Tempo pendingTempo = null;
        BeatChange pendingBeatChange = null;

        for (var site : sites) {
            if (site.child instanceof Direction direction) {
                var content = readDirection(direction);
                var metronome = content.metronome();

                if (metronome == null) {
                    continue;
                }

                var metronomeNotes = metronome.getMetronomeNote();

                if (!metronomeNotes.isEmpty()) {
                    var beatChange = buildBeatChange(metronomeNotes);

                    if (beatChange != null) {
                        pendingBeatChange = beatChange;
                    }

                    continue;
                }

                // A non-annotation direction's <words> is the tempo mark's description.
                var tempo = buildTempo(metronome, content.placement() == null ? textOf(content.words()) : null);

                if (tempo == null) {
                    continue;
                }

                if (!songTempoTaken && site.measureIndex == firstMeasureIndex) {
                    songTempoTaken = true;
                    song.setTempo(tempo);
                } else {
                    pendingTempo = tempo;
                }

                continue;
            }

            var noteSite = site.noteSite;

            if (noteSite == null) {
                continue;
            }

            var element = noteSite.element();

            if (pendingTempo != null) {
                element.addAttachment(new TempoChangeAttachment(element, pendingTempo));
                pendingTempo = null;
            }

            if (pendingBeatChange != null) {
                element.addAttachment(new BeatChangeAttachment(element, pendingBeatChange));
                pendingBeatChange = null;
            }
        }

        if (pendingTempo != null) {
            LOG.warn("Dropping tempo <direction> with no bound note");
        }

        if (pendingBeatChange != null) {
            LOG.warn("Dropping metric-modulation <direction> with no bound note");
        }
    }

    /**
     * The {@link Tempo} a beat-unit {@code <metronome>} states, or {@code null} when it
     * is incomplete or names a beat unit this reader does not know.
     */
    @Nullable
    private static Tempo buildTempo(Metronome metronome, @Nullable String description) throws SAXException {
        var beatUnit = metronome.getBeatUnit();
        var perMinute = metronome.getPerMinute();
        var perMinuteValue = perMinute == null ? null : perMinute.getValue();

        if (beatUnit == null || perMinuteValue == null) {
            // Not a complete metronome tempo direction — nothing to build.
            return null;
        }

        var beatUnitDotCount = metronome.getBeatUnitDot().size();
        var duration = BeatUnitMapping.forBeatUnit(beatUnit.trim(), beatUnitDotCount);

        if (duration == null) {
            LOG.warn("Ignoring <metronome> with unrecognised beat-unit '{}' (dots={})", beatUnit, beatUnitDotCount);
            return null;
        }

        var visibleTempo = MusicXmlUnits.parseIntOrThrow(MusicXmlTags.PER_MINUTE, perMinuteValue);
        // print-object="no" hides the mark; the default is shown.
        var showTempo = metronome.getPrintObject() != YesNo.NO;
        return new Tempo(visibleTempo, duration, description == null ? "" : description, showTempo);
    }

    /**
     * The {@link BeatChange} a metric-modulation {@code <metronome>} states, or
     * {@code null} when it is one this reader does not support. The writer emits exactly
     * two {@code <metronome-note>}s (duration equals beat); any other count, or an
     * unrecognised note, is an unsupported metronome.
     */
    @Nullable
    private static BeatChange buildBeatChange(List<MetronomeNote> metronomeNotes) {
        if (metronomeNotes.size() != METRIC_MODULATION_NOTE_COUNT) {
            LOG.warn("Ignoring <metronome> metric modulation with {} note(s); expected {}",
                metronomeNotes.size(), METRIC_MODULATION_NOTE_COUNT);
            return null;
        }

        var durationNote = metronomeNotes.get(0);
        var beatNote = metronomeNotes.get(1);
        var durationToken = durationNote.getMetronomeType();
        var beatToken = beatNote.getMetronomeType();

        if (durationToken == null || beatToken == null) {
            LOG.warn("Ignoring <metronome> metric modulation with a <metronome-note> missing its <metronome-type>");
            return null;
        }

        var durationDotCount = durationNote.getMetronomeDot().size();
        var beatDotCount = beatNote.getMetronomeDot().size();
        var duration = BeatUnitMapping.forBeatUnit(durationToken.trim(), durationDotCount);
        var beat = BeatUnitMapping.forBeatUnit(beatToken.trim(), beatDotCount);

        if (duration == null || beat == null) {
            LOG.warn("Ignoring <metronome> metric modulation with unrecognised note ('{}' dots={} / '{}' dots={})",
                durationToken, durationDotCount, beatToken, beatDotCount);
            return null;
        }

        return new BeatChange(duration, beat);
    }

    // -------------------------------------------------------------------------
    // Pass: hairpin wedges
    // -------------------------------------------------------------------------

    /**
     * Pairs each {@code <wedge>} start with its stop. The writer emits both wedges of a
     * hairpin immediately before their bound {@code <note>}, so one uniform rule
     * applies: every wedge binds to the next note after it.
     *
     * <p>A hairpin is an intra-line span, so one still open when a new line starts is
     * dropped, as is one still open at the end of the part.
     */
    private static void resolveHairpins(List<ChildSite> sites) {
        var pendingWedges = new ArrayList<Wedge>();
        OpenHairpin open = null;
        Line line = null;

        for (var site : sites) {
            if (site.line != line) {
                dropUnboundWedges(pendingWedges);
                warnDanglingStart(open, "hairpin");
                open = null;
                line = site.line;
            }

            if (site.child instanceof Direction direction) {
                for (var directionType : direction.getDirectionType()) {
                    var wedge = directionType.getWedge();

                    if (wedge != null) {
                        pendingWedges.add(wedge);
                    }
                }

                continue;
            }

            var noteSite = site.noteSite;

            if (noteSite == null || pendingWedges.isEmpty()) {
                continue;
            }

            open = applyWedges(open, noteSite, pendingWedges);
            pendingWedges.clear();
        }

        dropUnboundWedges(pendingWedges);
        warnDanglingStart(open, "hairpin");
    }

    private static void dropUnboundWedges(List<Wedge> wedges) {
        for (var wedge : wedges) {
            LOG.warn("Dropping <wedge type=\"{}\"> with no bound note",
                ProxyMusicAccess.token(wedge.getType(), WedgeType::value));
        }

        wedges.clear();
    }

    /**
     * Applies the wedges bound to one note and returns the hairpin left open after it.
     *
     * <p>When this note opens a hairpin nothing else has open, its start is applied
     * before its stop, so a note carrying both bounds a single-note hairpin. Otherwise
     * the stop is applied first, so the hairpin already open closes at this note before
     * the next one opens on it — back-to-back hairpins sharing a note.
     */
    @Nullable
    private static OpenHairpin applyWedges(@Nullable OpenHairpin open, NoteSite noteSite, List<Wedge> wedges) {
        WedgeStart start = null;
        var hasStop = false;
        var stopX2ShiftSs = 0;

        for (var wedge : wedges) {
            var type = wedge.getType();

            if (type == WedgeType.STOP) {
                hasStop = true;
                stopX2ShiftSs = ProxyMusicAccess.tenthsToSs(wedge.getRelativeX());
                continue;
            }

            var typeToken = ProxyMusicAccess.token(type, WedgeType::value);

            if (typeToken == null) {
                continue;
            }

            var kind = WedgeTypeMapping.wedgeKind(typeToken);

            if (kind == null) {
                // Not a crescendo/diminuendo start (nor a stop) — unrecognised; ignore.
                continue;
            }

            // Defensive overlap drop: only one hairpin is ever open.
            if (start != null || (open != null && !hasStop)) {
                LOG.warn("Dropping overlapping <wedge> start; a hairpin is already open");
                continue;
            }

            start = new WedgeStart(kind,
                ProxyMusicAccess.tenthsToSs(wedge.getRelativeX()),
                ProxyMusicAccess.tenthsToSs(wedge.getRelativeY()));
        }

        if (open == null && start != null) {
            return closeHairpin(new OpenHairpin(noteSite.element(), start), noteSite, hasStop, stopX2ShiftSs);
        }

        var stillOpen = closeHairpin(open, noteSite, hasStop, stopX2ShiftSs);

        // The overlap drop above guarantees a start reaching here found nothing open.
        return start == null ? stillOpen : new OpenHairpin(noteSite.element(), start);
    }

    @Nullable
    private static OpenHairpin closeHairpin(
        @Nullable OpenHairpin open,
        NoteSite noteSite,
        boolean hasStop,
        int x2ShiftSs
    ) {
        if (!hasStop) {
            return open;
        }

        if (open == null) {
            LOG.warn("Ignoring <wedge type=\"stop\"> with no open hairpin");
            return null;
        }

        buildHairpin(noteSite.line(), open, noteSite.element(), x2ShiftSs);
        return null;
    }

    /**
     * Builds a {@link Crescendo} or {@link Diminuendo} over [{@code anchor},
     * {@code end}]. Added with {@link Line#addCrescendo}/{@link Line#addDiminuendo}
     * rather than the raw {@code addSpan} the other span kinds use, so a file
     * carrying two same-type wedges that overlap or merely touch loads as the one
     * hairpin it musically is.
     */
    private static void buildHairpin(Line line, OpenHairpin open, StaffElement end, int x2ShiftSs) {
        if (open.start().kind() == Hairpin.Kind.CRESCENDO) {
            var crescendo = new Crescendo(open.anchor(), end);
            applyHairpinShifts(crescendo, open, x2ShiftSs);
            line.addCrescendo(crescendo);
        } else {
            var diminuendo = new Diminuendo(open.anchor(), end);
            applyHairpinShifts(diminuendo, open, x2ShiftSs);
            line.addDiminuendo(diminuendo);
        }
    }

    /** Restores the user-adjustable hairpin offsets from both of its wedges. */
    private static void applyHairpinShifts(Hairpin hairpin, OpenHairpin open, int x2ShiftSs) {
        var start = open.start();

        if (start.x1ShiftSs() != 0) {
            hairpin.setX1ShiftSs(start.x1ShiftSs());
        }

        if (x2ShiftSs != 0) {
            hairpin.setX2ShiftSs(x2ShiftSs);
        }

        if (start.yShiftSs() != 0) {
            hairpin.setYShiftSs(start.yShiftSs());
        }
    }

    // -------------------------------------------------------------------------
    // Passes: per-note range spans
    // -------------------------------------------------------------------------
    //
    // Each runs over the part's whole note list in document order rather than line by
    // line, and adds the span it builds to the line of its closing note — which is what
    // lets a tie cross a line boundary.
    // -------------------------------------------------------------------------

    /**
     * Pairs each {@code <slide type="start">} with the following {@code type="stop"}:
     * the start note is the glissando. A glissando needs two notes, so a start is never
     * itself marked on read.
     */
    private static void resolveSlides(List<NoteSite> notes) {
        StaffElement start = null;

        for (var note : notes) {
            var slideType = NoteMapper.slideType(note.pmNote());

            if (MusicXmlTags.SLIDE_STOP.equals(slideType)) {
                if (start != null) {
                    start.setGlissando();
                    start = null;
                }
                // A stop with no open start is stray; ignore it.
            } else if (MusicXmlTags.SLIDE_START.equals(slideType)) {
                warnDanglingStart(start, SLIDE_START_MARKER);
                start = note.element();
            }
        }

        warnDanglingStart(start, SLIDE_START_MARKER);
    }

    /**
     * Collapses each note's primary {@code <beam number="1">} marker into a
     * {@link Beam}: {@code begin} opens the run, {@code continue} keeps it open, and
     * {@code end} builds the span.
     */
    private static void resolveBeams(List<NoteSite> notes) {
        StaffElement begin = null;

        for (var note : notes) {
            var beamType = NoteMapper.primaryBeamType(note.pmNote());

            if (MusicXmlTags.BEAM_BEGIN.equals(beamType)) {
                warnDanglingStart(begin, BEAM_BEGIN_MARKER);
                begin = note.element();
            } else if (MusicXmlTags.BEAM_END.equals(beamType)) {
                if (begin == null) {
                    warnOrphanStop(BEAM_END_MARKER);
                } else {
                    note.line().addBeaming(new Beam(begin, note.element()));
                    begin = null;
                }
            }
            // BEAM_CONTINUE keeps the run open — no action.
        }

        warnDanglingStart(begin, BEAM_BEGIN_MARKER);
    }

    /**
     * Collapses each note's {@code <tied>} markers into {@link Tie}s. Stop is processed
     * before start so an interior note of a chain (which carries both) closes its pair
     * and immediately re-opens the next, producing one tie per adjacent pair.
     */
    private static void resolveTies(List<NoteSite> notes) {
        StaffElement start = null;

        for (var note : notes) {
            var marker = NoteMapper.tieMarker(note.pmNote());

            if (marker.stop()) {
                if (start == null) {
                    warnOrphanStop(TIED_STOP_MARKER);
                } else {
                    note.line().addTie(new Tie(start, note.element()));
                    start = null;
                }
            }

            if (marker.start()) {
                warnDanglingStart(start, TIED_START_MARKER);
                start = note.element();
            }
        }

        warnDanglingStart(start, TIED_START_MARKER);
    }

    /**
     * Collapses each note's {@code <tuplet>} bracket markers into {@link Tuplet}s.
     * Unlike the other per-note spans, a tuplet is not a legitimate document state
     * without both brackets spanning at least two non-rest notes (see #518), so a
     * dangling start or an orphan stop is rejected as a corrupt document rather than
     * silently dropped.
     *
     * <p>{@code <normal-type>} is the trust discriminator. A file that carries it states
     * a complete {@code <time-modification>}, so its {@code <normal-notes>} is taken at
     * face value and the tuplet is built resolved. A file without it predates this
     * format, or states a token this reader does not recognise; either way the ratio is
     * unknown rather than corrupt, so the tuplet is built unresolved and the post-load
     * pass derives the ratio from the beat in effect.
     */
    private static void resolveTuplets(List<NoteSite> notes) throws SAXException {
        OpenTuplet open = null;

        for (var note : notes) {
            var marker = NoteMapper.tupletMarker(note.pmNote());

            if (marker.start()) {
                requireNoOpenTuplet(open);
                open = new OpenTuplet(note.element(), marker);
            }

            if (!marker.stop()) {
                continue;
            }

            if (open == null) {
                throw new SAXException("Corrupt document: <tuplet type=\"stop\"> with no matching start");
            }

            buildTuplet(open, note);
            open = null;
        }

        requireNoOpenTuplet(open);
    }

    private static void requireNoOpenTuplet(@Nullable OpenTuplet open) throws SAXException {
        if (open != null) {
            throw new SAXException("Corrupt document: dangling <tuplet type=\"start\"> with no matching stop");
        }
    }

    private static void buildTuplet(OpenTuplet open, NoteSite end) throws SAXException {
        var marker = open.marker();
        var noteValue = statedNoteValue(marker);
        var line = end.line();
        Tuplet tuplet;

        if (noteValue == null) {
            tuplet = Tuplet.withUnresolvedRatio(open.anchor(), end.element(), marker.actualNotes());
        } else {
            tuplet = new Tuplet(open.anchor(), end.element(), marker.actualNotes(),
                marker.normalNotes(), noteValue, marker.normalDotCount());
        }

        if (!tuplet.hasValidSpan(line)) {
            throw new SAXException("Corrupt document: tuplet does not span at least two non-rest notes");
        }

        if (marker.verticalPositionSs() != 0) {
            tuplet.setVerticalPositionSs(marker.verticalPositionSs());
        }

        line.addTuplet(tuplet);
    }

    /**
     * The written note value the anchor note's {@code <time-modification>} states,
     * or {@code null} when the file states no usable one — no {@code <normal-type>},
     * a token this reader does not know, or a {@code <normal-notes>} that is not a
     * positive count. Each of those leaves the ratio to be derived rather than
     * trusted, so none of them is an error.
     */
    @Nullable
    private static ElementType statedNoteValue(NoteMapper.TupletMarker marker) {
        var token = marker.normalTypeToken();

        if (token == null || marker.normalNotes() <= 0) {
            return null;
        }

        // <normal-type> names a written value, so it always maps to the pitched
        // ElementType: rest-ness and grace-ness belong to the note, not the ratio.
        return NoteTypeMapping.forTypeToken(token.trim(), false, false);
    }

    /**
     * Collapses each note's {@code <wavy-line>} markers into {@link Trill}s. Start is
     * processed before stop so a note carrying both builds the single-note trill.
     */
    private static void resolveTrills(List<NoteSite> notes) {
        OpenTrill open = null;

        for (var note : notes) {
            var marker = NoteMapper.trillMarker(note.pmNote());

            if (marker.start()) {
                warnDanglingStart(open, WAVY_LINE_START_MARKER);
                open = new OpenTrill(note.element(), marker.yPositionSs());
            }

            if (!marker.stop()) {
                continue;
            }

            if (open == null) {
                warnOrphanStop(WAVY_LINE_STOP_MARKER);
                continue;
            }

            var anchor = open.anchor();
            var element = note.element();
            var trill = anchor == element ? new Trill(anchor) : new Trill(anchor, element);

            if (open.yPositionSs() != 0) {
                trill.setYPositionSs(open.yPositionSs());
            }

            note.line().addSpan(trill);
            open = null;
        }

        warnDanglingStart(open, WAVY_LINE_START_MARKER);
    }

    // -------------------------------------------------------------------------
    // Pairing diagnostics
    // -------------------------------------------------------------------------

    /**
     * Warns that a run never received its closing marker, so it builds nothing.
     *
     * @param open   the still-open anchor or run, or null when nothing is open
     * @param marker what the missing closing marker would have closed
     */
    private static void warnDanglingStart(@Nullable Object open, String marker) {
        if (open != null) {
            LOG.warn("Dropping dangling {} with no matching stop", marker);
        }
    }

    /** Warns that a closing marker arrived with no run open for it to close. */
    private static void warnOrphanStop(String marker) {
        LOG.warn("Ignoring {} with no matching start", marker);
    }
}
