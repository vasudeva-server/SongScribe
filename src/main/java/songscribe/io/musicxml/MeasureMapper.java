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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.Attributes;
import org.audiveris.proxymusic.Barline;
import org.audiveris.proxymusic.Direction;
import org.audiveris.proxymusic.FormattedTextId;
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

import songscribe.Strings;
import songscribe.dom.Annotation;
import songscribe.dom.Beam;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.ChangeLines;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.NotatableValue;
import songscribe.dom.NoteElement;
import songscribe.dom.NoteValue;
import songscribe.dom.ParsedSong;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingHairpin;
import songscribe.dom.PendingTuplet;
import songscribe.dom.RecordedChange;
import songscribe.dom.Slide;
import songscribe.dom.Song;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.TempoMarking;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletNormalTime;
import songscribe.io.DocumentValidation;
import songscribe.io.ReadAnnotation;

/**
 * Maps the first {@code <part>} of an unmarshalled {@code <score-partwise>} onto the
 * {@link Song}'s {@link Line}s: the line split, the running key, the barlines
 * and voltas, the measure-level {@code <direction>}s (hairpins, tempo marks, metric
 * modulations, annotations) and the per-note range spans. Each {@code <note>} itself is
 * mapped by {@link NoteMapper}.
 *
 * <p><b>Where a {@code <key>} lands</b> is decided by the measure it sits in: the measure that
 * opened the current line establishes that line's own {@link Line#setKey key}, and any other
 * measure appends a {@link KeyChangeElement}. A line the file gave no {@code <key>} inherits.
 *
 * <p><b>Two children of {@code <key>} are deliberately not read.</b> {@code <cancel>} is derived
 * by {@code songscribe.dom.Key#accidentalsFrom}, so a document renders under this program's
 * cancellation policy; {@code <mode>} is never read because no SongScribe key has a mode.
 *
 * <p>Three rules are order-sensitive and live in the passes that own them: a hairpin's stop
 * resolves before its start, except when one note both opens and closes one; the first tempo
 * mark of the first measure is the song's own; and a tie may cross a line boundary, while a
 * hairpin and a volta may not.
 *
 * <p><b>Damaged marks.</b> This writer pairs every span mark it emits, so a mark that does not
 * pair is damage. The pass that meets a damaged mark drops it, keeps the notes it sat on, adds
 * one entry to the reader's report entries and reads on, so a file damaged in one span still
 * opens and the user sees what was dropped.
 *
 * <p>A {@link Line} is committed to the song only once its elements are complete, so
 * {@code Line.addElement} appends in document order instead of inserting before a valid
 * terminal barline that lands mid-line.
 */
final class MeasureMapper {

    private static final Logger LOG = LoggerFactory.getLogger(MeasureMapper.class);

    // A SongScribe metric modulation always relates exactly two note values
    // (duration equals beat), so the writer emits exactly two <metronome-note>s.
    private static final int METRIC_MODULATION_NOTE_COUNT = 2;

    private enum Damage {

        NO_END(Strings.REPORT_REASON_DAMAGED_NO_END),

        NO_START(Strings.REPORT_REASON_DAMAGED_NO_START),

        LINE_BREAK(Strings.REPORT_REASON_DAMAGED_LINE_BREAK),

        UNBOUND(Strings.REPORT_REASON_DAMAGED_UNBOUND),

        WRONG_ELEMENT(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT),

        TUPLET_NUMBER(Strings.REPORT_REASON_DAMAGED_TUPLET_NUMBER),

        NORMAL_NOTES(Strings.REPORT_REASON_DAMAGED_NORMAL_NOTES);

        private final String reasonKey;

        Damage(String reasonKey) {
            this.reasonKey = reasonKey;
        }
    }

    private record NoteSite(Note pmNote, StaffElement element, Line line) {}

    /**
     * One child of one measure, together with what the structural walk built from it.
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

        @Nullable
        private StaffElement endingHost = null;

        @Nullable
        private StaffElement annotationHost = null;

        /** Non-null exactly when this child is a mapped {@code <note>}. */
        @Nullable
        private NoteSite noteSite = null;

        private boolean mergedIntoLaterBarline = false;

        /** The backward half this forward-left repeat merges with, when there is one. */
        @Nullable
        private ChildSite mergedBackwardHalf = null;

        private ChildSite(int measureIndex, Object child) {
            this.measureIndex = measureIndex;
            this.child = child;
        }
    }

    private record DirectionContent(
        @Nullable AboveBelow placement,
        @Nullable FormattedTextId words,
        @Nullable Metronome metronome
    ) {}

    /**
     * The writer puts the hairpin's horizontal offset on both of its wedges, so the start
     * wedge's {@code relative-x} is the hairpin's.
     */
    private record WedgeStart(Hairpin.Kind kind, double xOffsetSs, double yOffsetSs) {}

    private sealed interface OpenMark permits OpenHairpin, OpenSlide, OpenBeam, OpenTuplet, OpenTrill {

        NoteSite opener();
    }

    private record OpenHairpin(NoteSite opener, WedgeStart start) implements OpenMark {}

    private record OpenSlide(NoteSite opener) implements OpenMark {}

    private record OpenBeam(NoteSite opener) implements OpenMark {}

    private record OpenTuplet(NoteSite opener, NoteMapper.TupletMarker marker) implements OpenMark {}

    private record OpenTrill(NoteSite opener, double yOffsetSs) implements OpenMark {}

    /**
     * @param anchor {@code null} while a note-anchored start awaits the next note
     */
    private record OpenEnding(@Nullable StaffElement anchor, double yOffsetSs) {}

    private final Song song;

    @Nullable
    private Line currentLine = null;

    // The measure index at which the current line started, or null before any line has. A <key>
    // in that measure establishes the line's own key; a <key> in any other measure is a mid-line
    // change. No running key is carried: a line the file gave no <key> establishes none, so it
    // inherits, which is what Song.rebuildInheritedKeysAfterParsing settles once the walk is over.
    @Nullable
    private Integer lineStartMeasureIndex = null;

    // True once any note's <accidental> token was a retired legacy token converted to its
    // replacement. One report entry stands for every conversion, so map adds it once the walk is
    // over, after every entry the walk made.
    private boolean accidentalsConverted = false;

    // Every line on which a note lost a <lyric> whose number names no script, once each, in the
    // order the walk reached them. Each gets one report entry, which map adds after the
    // accidentals-converted entry.
    private final Set<Line> linesThatDroppedLyrics = new LinkedHashSet<>();

    // Every tuplet bracket the walk has closed, with no stated ratio when the file states
    // none; carried to the load passes in the ParsedSong — a mapper cannot build a Tuplet, since
    // the beat a stated-less ratio derives from may still change in a later measure while the
    // walk is mid-part.
    private final List<PendingTuplet> pendingTuplets = new ArrayList<>();

    // Every ending the walk paired a number="1" start with a number="2" stop; carried to the load
    // passes in the ParsedSong — a mapper does not build an Ending, since whether the range is
    // well formed is the span load pass's question, not the walk's.
    private final List<PendingEnding> pendingEndings = new ArrayList<>();

    // Every hairpin the walk paired a wedge start with a stop; carried to the load passes in the
    // ParsedSong — a mapper does not build a Hairpin, since whether the range is well formed, and
    // whether it merges with another, is the span load pass's question, not the walk's.
    private final List<PendingHairpin> pendingHairpins = new ArrayList<>();

    // Every report entry the walk made, one per damaged mark it dropped, in the order it made them.
    private final List<RecordedChange> readerEntries = new ArrayList<>();

    // The features the document declares; decides which per-note attributes the walk honors.
    private final Set<SupportedFeature> features;

    private MeasureMapper(Song song, Set<SupportedFeature> features) {
        this.song = song;
        this.features = features;
    }

    /**
     * A damaged span mark does not refuse the document: it is dropped and reported. Nor does a
     * {@code <lyric>} whose number names no script: each line that lost one is reported once.
     */
    static ParsedSong map(ScorePartwise score, Song song, Set<SupportedFeature> features)
        throws SAXException {
        var mapper = new MeasureMapper(song, features);
        var part = ProxyMusicAccess.firstPart(score);

        if (part != null) {
            mapper.mapPart(part);
        }

        if (mapper.accidentalsConverted) {
            mapper.readerEntries.add(ParsedSong.accidentalsConvertedEntry());
        }

        for (var line : mapper.linesThatDroppedLyrics) {
            mapper.readerEntries.add(ParsedSong.lyricDroppedEntry(line));
        }

        return new ParsedSong(
            song, mapper.pendingTuplets, mapper.pendingEndings, mapper.pendingHairpins, mapper.readerEntries);
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
        // Ties before beams: Beam.spanning refuses a beam over a tied pair, so a file that beams
        // two tied notes loses the beam, which only changes the look, and keeps the tie.
        resolveTies(notes);
        resolveBeams(notes);
        resolveTuplets(notes);
        resolveTrills(notes);
    }

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
                    startNewLine(site.measureIndex);
                }
            } else if (child instanceof Attributes attributes) {
                mapAttributes(attributes, site.measureIndex);
            } else if (child instanceof Barline barline) {
                walkBarline(site, barline);
            } else if (child instanceof Note pmNote) {
                walkNote(site, pmNote);
            }

            site.line = currentLine;
        }

        commitCurrentLine();
    }

    /**
     * The new line establishes no key of its own: keying it to a carried-forward value would
     * stop a later edit to an earlier line's key from reaching it.
     */
    private void startNewLine(int measureIndex) {
        commitCurrentLine();

        currentLine = new Line(song);
        lineStartMeasureIndex = measureIndex;
    }

    private void commitCurrentLine() {
        if (currentLine != null) {
            song.addLine(currentLine);
        }
    }

    private void mapAttributes(Attributes attributes, int measureIndex) throws SAXException {
        for (var key : attributes.getKey()) {
            var fifths = ProxyMusicAccess.keyFifths(key);

            if (fifths != null) {
                applyFifths(fifths, measureIndex);
            }
        }

        // <cancel> is deliberately not read. Which accidentals a key change cancels is derived
        // from the change itself by songscribe.dom.Key.accidentalsFrom, so a document is rendered under
        // this program's cancellation policy rather than under whichever one wrote the file.
        // <mode> is likewise not read: no SongScribe key has a mode, and only files this program
        // wrote get past the provenance gate, so an incoming <mode> is always the "major" this
        // writer emitted.
    }

    /**
     * A {@code <key>} in any measure other than the one that opened the line is a mid-line
     * change, so only the notes <em>after</em> it are re-spelled. A {@code <key>} before any
     * line has started is ignored.
     */
    private void applyFifths(int fifths, int measureIndex) throws SAXException {
        var line = currentLine;

        if (line == null) {
            return;
        }

        // Converted, not clamped: clamping would load a document as a song silently in the wrong
        // key, and the count reaches Key.altersPitchClass, which is indexed per note during pitch
        // resolution. Key owns the range; this turns its refusal into a corrupt-document error.
        Key key;

        try {
            key = Key.ofFifths(fifths);
        } catch (IllegalArgumentException e) {
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: <fifths> out of range: {}, maximum magnitude {}",
                fifths, Key.MAX_ACCIDENTAL_COUNT);
        }

        var startMeasureIndex = lineStartMeasureIndex;

        if (startMeasureIndex != null && startMeasureIndex == measureIndex) {
            line.setKey(key);
            return;
        }

        requireKeyChangePosition(line);
        line.addElement(new KeyChangeElement(key));
    }

    /**
     * A mid-measure {@code <key>} with no barline before it describes a model this program cannot
     * build, and nothing downstream re-checks the invariant it would violate.
     */
    private static void requireKeyChangePosition(Line line) throws SAXException {
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: mid-measure <key> is not preceded by a barline; the line is empty");
        }

        var previousType = line.getElement(elementCount - 1).getType();

        if (!previousType.isBar()) {
            throw DocumentValidation.corrupt(
                LOG,
                "Corrupt document: mid-measure <key> is not preceded by a barline; preceded by {}",
                previousType);
        }
    }

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
     * Returns {@code null} for a barline that contributes none: an invisible barline or a
     * style/repeat combination this reader does not know. A missing {@code <bar-style>} is
     * treated like {@code none}.
     */
    @Nullable
    private static ElementType barlineElementType(Barline barline) {
        var barStyle = ProxyMusicAccess.barStyleToken(barline);

        if (barStyle == null || BarlineStyleMapping.BAR_STYLE_NONE.equals(barStyle)) {
            return null;
        }

        return BarlineStyleMapping.forBarStyle(barStyle, ProxyMusicAccess.repeatDirectionToken(barline));
    }

    private StaffElement appendToCurrentLine(ElementType elementType) throws SAXException {
        var line = requireCurrentLine("barline element");
        var element = elementType.newInstance();
        line.addElement(element);
        return element;
    }

    private Line requireCurrentLine(String construct) throws SAXException {
        var line = currentLine;

        if (line == null) {
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: {} encountered before any line was started", construct);
        }

        return line;
    }

    private void walkNote(ChildSite site, Note pmNote) throws SAXException {
        var line = requireCurrentLine("<note>");
        var mapped = NoteMapper.map(pmNote, line, features);
        var element = mapped.element();

        if (mapped.usedLegacyAccidental()) {
            accidentalsConverted = true;
        }

        if (mapped.droppedLyric()) {
            linesThatDroppedLyrics.add(line);
        }

        site.noteSite = new NoteSite(pmNote, element, line);
        site.annotationHost = element;
        site.lastElement = element;

        if (mapped.hasBreathMark()) {
            site.lastElement = appendToCurrentLine(ElementType.BREATH_MARK);
        }
    }

    /**
     * An ending is an intra-line span built on its {@code number="2"} stop, so a start still open
     * when a new line starts or the part ends is a damaged mark, as is a {@code number="2"} stop
     * with no start open on its line. A note-anchored start awaiting its note is not yet on a
     * line, so a line break does not close it.
     */
    private void resolveEndings(List<ChildSite> sites) {
        OpenEnding open = null;
        StaffElement lastElement = null;
        Line line = null;

        for (var site : sites) {
            if (site.line != line) {
                if (open != null && open.anchor() != null) {
                    dropOpenEnding(open);
                    open = null;
                }

                lastElement = null;
                line = site.line;
            }

            var noteSite = site.noteSite;

            if (noteSite != null && open != null && open.anchor() == null) {
                open = new OpenEnding(noteSite.element(), open.yOffsetSs());
            }

            if (site.child instanceof Barline barline) {
                var marker = ProxyMusicAccess.endingMarker(barline);

                if (marker != null) {
                    var host = site.endingHost;
                    var isStop = MusicXmlTags.TYPE_STOP.equals(marker.type())
                        || MusicXmlTags.ENDING_DISCONTINUE.equals(marker.type());

                    if (MusicXmlTags.NUMBER_1.equals(marker.number())
                        && MusicXmlTags.TYPE_START.equals(marker.type())) {
                        dropOpenEnding(open);
                        // A note-anchored start (a null host) defers to the next note, which the
                        // invisible barline carrying it always precedes — including across a
                        // line break.
                        open = new OpenEnding(host, MusicXmlUnits.tenthsToExactSs(marker.relativeY()));
                    } else if (MusicXmlTags.NUMBER_2.equals(marker.number()) && isStop) {
                        var end = host != null ? host : lastElement;
                        var anchor = open == null ? null : open.anchor();

                        if (open != null && anchor != null && end != null) {
                            // The writer states no horizontal offset for an ending.
                            pendingEndings.add(new PendingEnding(anchor, end, 0, open.yOffsetSs()));
                        } else {
                            dropDamaged(Strings.REPORT_ENDINGS_REMOVED, Damage.NO_START, at(end));
                        }

                        open = null;
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

        dropOpenEnding(open);
    }

    /**
     * @param open {@code null} when none is open
     */
    private void dropOpenEnding(@Nullable OpenEnding open) {
        if (open != null) {
            dropDamaged(Strings.REPORT_ENDINGS_REMOVED, Damage.NO_END, at(open.anchor()));
        }
    }

    /**
     * A second annotation arriving before the element it binds to replaces the first, and the
     * loss is logged.
     */
    private static void resolveAnnotations(List<ChildSite> sites) throws SAXException {
        ReadAnnotation pending = null;

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
                pending.attachTo(host);
                pending = null;
            }
        }

        if (pending != null) {
            LOG.warn("Dropping annotation <direction> with no bound note");
        }
    }

    /**
     * A direction is an annotation iff it has a {@code placement} attribute; a words-only
     * direction without one is a tempo description.
     *
     * @return {@code null} when it carries none or no text
     */
    @Nullable
    private static ReadAnnotation annotationOf(Direction direction) {
        var content = readDirection(direction);
        var placement = content.placement();
        var words = content.words();

        if (placement == null || words == null) {
            return null;
        }

        var text = words.getValue();

        // A direction whose words are blank is dropped rather than attached: an annotation with
        // no text has nothing to draw.
        if (text == null || text.isBlank()) {
            LOG.warn("Corrupt document: annotation with no text, dropping it");
            return null;
        }

        // Only what the document actually carried is applied, so an absent halign takes
        // Annotation's own default rather than a second copy of it here.
        var halign = words.getHalign();
        var alignment = halign == null ? Annotation.DEFAULT_ALIGNMENT : halign;

        return new ReadAnnotation(
            new Annotation(text, alignment, placement),
            MusicXmlUnits.tenthsToExactSs(words.getRelativeY()));
    }

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

        return new DirectionContent(direction.getPlacement(), words, metronome);
    }

    @Nullable
    private static String textOf(@Nullable FormattedTextId words) {
        return words == null ? null : words.getValue();
    }

    /**
     * The first tempo mark of the first measure is the score's own tempo, wherever in the measure
     * it sits: it binds to no note. A second tempo or metric modulation arriving before the note
     * replaces the first, and the loss is logged.
     */
    private void resolveMetronomes(List<ChildSite> sites) throws SAXException {
        if (sites.isEmpty()) {
            return;
        }

        // "The first" measure is taken from document order, not from the number attribute.
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
                        if (pendingBeatChange != null) {
                            LOG.warn(
                                "Dropping unbound metric-modulation <direction>: a second one arrived before a note to bind it to");
                        }

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
                    if (pendingTempo != null) {
                        LOG.warn(
                            "Dropping unbound tempo <direction>: a second one arrived before a note to bind it to");
                    }

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
                DocumentValidation.attachUnique(LOG, element, new TempoChangeAttachment(pendingTempo));
                pendingTempo = null;
            }

            if (pendingBeatChange != null) {
                DocumentValidation.attachUnique(LOG, element, new BeatChangeAttachment(pendingBeatChange));
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
     * A {@code print-object="no"} metronome with no description states a tempo that draws
     * nothing; it is shown rather than dropped. See {@link TempoMarking#fromFile}.
     *
     * @param description {@code null} when the direction carries no words, or carries words that
     *                    belong to an annotation rather than to this tempo
     * @return {@code null} when the element states no speed, no beat unit, or a beat unit this
     *         reader does not know
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
        var duration = BeatUnitMapping.beatUnitFor(beatUnit.trim(), beatUnitDotCount);

        if (duration == null) {
            LOG.warn("Ignoring <metronome> with unrecognised beat-unit '{}' (dots={})", beatUnit, beatUnitDotCount);
            return null;
        }

        var visibleTempo = MusicXmlUnits.parseIntOrThrow(MusicXmlTags.PER_MINUTE, perMinuteValue);
        // print-object="no" hides the mark; the default is shown.
        var hideMetronome = metronome.getPrintObject() == YesNo.NO;
        var read = TempoMarking.fromFile(description == null ? "" : description, hideMetronome);

        if (read.repaired()) {
            LOG.warn("Showing a hidden <metronome> that carries no <words> description");
        }

        return new Tempo(visibleTempo, duration, read.marking());
    }

    /**
     * Returns {@code null} when the metronome is unsupported: not exactly two
     * {@code <metronome-note>}s, or an unrecognised note.
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
        var duration = BeatUnitMapping.beatUnitFor(durationToken.trim(), durationDotCount);
        var beat = BeatUnitMapping.beatUnitFor(beatToken.trim(), beatDotCount);

        if (duration == null || beat == null) {
            LOG.warn("Ignoring <metronome> metric modulation with unrecognised note ('{}' dots={} / '{}' dots={})",
                durationToken, durationDotCount, beatToken, beatDotCount);
            return null;
        }

        return new BeatChange(duration, beat);
    }

    /**
     * Every wedge binds to the next note after it. A hairpin is an intra-line span, so a start
     * still open when a new line starts or the part ends is a damaged mark, as is a stop with no
     * start open on its line, a second start while one is open, and a wedge no note follows on
     * its line.
     */
    private void resolveHairpins(List<ChildSite> sites) {
        var pendingWedges = new ArrayList<Wedge>();
        OpenHairpin open = null;
        Line line = null;

        for (var site : sites) {
            if (site.line != line) {
                dropUnboundWedges(pendingWedges);
                dropDangling(Strings.REPORT_HAIRPINS_REMOVED, open);
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
        dropDangling(Strings.REPORT_HAIRPINS_REMOVED, open);
    }

    private void dropUnboundWedges(List<Wedge> wedges) {
        for (var _ : wedges) {
            dropDamaged(Strings.REPORT_HAIRPINS_REMOVED, Damage.UNBOUND, null);
        }

        wedges.clear();
    }

    /**
     * When no hairpin is open, a note's start is applied before its stop, so a note carrying both
     * bounds a single-note hairpin. Otherwise the stop is applied first, so back-to-back
     * hairpins can share a note.
     */
    @Nullable
    private OpenHairpin applyWedges(@Nullable OpenHairpin open, NoteSite noteSite, List<Wedge> wedges) {
        WedgeStart start = null;
        var hasStop = false;

        for (var wedge : wedges) {
            var type = wedge.getType();

            if (type == WedgeType.STOP) {
                hasStop = true;
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

            // Only one hairpin is ever open, so a second start finds no stop of its own.
            if (start != null || (open != null && !hasStop)) {
                dropDamaged(Strings.REPORT_HAIRPINS_REMOVED, Damage.NO_END, at(noteSite.element()));
                continue;
            }

            start = new WedgeStart(
                kind,
                MusicXmlUnits.tenthsToExactSs(wedge.getRelativeX()),
                MusicXmlUnits.tenthsToExactSs(wedge.getRelativeY()));
        }

        if (open == null && start != null) {
            return closeHairpin(new OpenHairpin(noteSite, start), noteSite, hasStop);
        }

        var stillOpen = closeHairpin(open, noteSite, hasStop);

        // The overlap drop above guarantees a start reaching here found nothing open.
        return start == null ? stillOpen : new OpenHairpin(noteSite, start);
    }

    @Nullable
    private OpenHairpin closeHairpin(@Nullable OpenHairpin open, NoteSite noteSite, boolean hasStop) {
        if (!hasStop) {
            return open;
        }

        if (open == null) {
            dropDamaged(Strings.REPORT_HAIRPINS_REMOVED, Damage.NO_START, at(noteSite.element()));
            return null;
        }

        buildHairpin(open, noteSite.element());
        return null;
    }

    /**
     * Builds no {@link Hairpin}: the span load pass judges each record once the song is complete.
     */
    private void buildHairpin(OpenHairpin open, StaffElement end) {
        var start = open.start();

        pendingHairpins.add(new PendingHairpin(
            start.kind(), open.opener().element(), end, start.xOffsetSs(), start.yOffsetSs()));
    }

    // The per-note range-span passes each run over the part's whole note list in document order
    // rather than line by line, which is what lets a tie or a glissando cross a line boundary,
    // and what lets a beam, tuplet or trill pair split across one be seen as one damaged mark.

    private void resolveSlides(List<NoteSite> notes) {
        OpenSlide open = null;

        for (var note : notes) {
            var slideType = NoteMapper.slideType(note.pmNote());

            if (MusicXmlTags.SLIDE_STOP.equals(slideType)) {
                if (open == null) {
                    dropOrphanStop(Strings.REPORT_GLISSANDOS_REMOVED, note);
                    continue;
                }

                var glissando = markedNote(open.opener());

                if (glissando == null || markedNote(note) == null) {
                    dropDamaged(Strings.REPORT_GLISSANDOS_REMOVED, Damage.WRONG_ELEMENT, through(open, note));
                } else {
                    glissando.setSlide(Slide.GLISSANDO);
                }

                open = null;
            } else if (MusicXmlTags.SLIDE_START.equals(slideType)) {
                dropDangling(Strings.REPORT_GLISSANDOS_REMOVED, open);
                open = new OpenSlide(note);
            }
        }

        dropDangling(Strings.REPORT_GLISSANDOS_REMOVED, open);
    }

    private void resolveBeams(List<NoteSite> notes) {
        OpenBeam open = null;

        for (var note : notes) {
            var beamType = NoteMapper.primaryBeamType(note.pmNote());

            if (MusicXmlTags.BEAM_BEGIN.equals(beamType)) {
                dropDangling(Strings.REPORT_BEAMS_REMOVED, open);
                open = new OpenBeam(note);
            } else if (MusicXmlTags.BEAM_END.equals(beamType)) {
                if (open == null) {
                    dropOrphanStop(Strings.REPORT_BEAMS_REMOVED, note);
                } else {
                    closeBeam(open, note);
                    open = null;
                }
            }
            // BEAM_CONTINUE keeps the run open — no action.
        }

        dropDangling(Strings.REPORT_BEAMS_REMOVED, open);
    }

    /**
     * A run {@link Beam#spanning} refuses is damaged.
     */
    private void closeBeam(OpenBeam open, NoteSite end) {
        if (!pairsOnOneLine(Strings.REPORT_BEAMS_REMOVED, open, end)) {
            return;
        }

        var begin = open.opener();

        if (markedNote(begin) == null || markedNote(end) == null) {
            dropDamaged(Strings.REPORT_BEAMS_REMOVED, Damage.WRONG_ELEMENT, through(open, end));
            return;
        }

        var line = end.line();
        var range = new ElementRange(line.getElementIndex(begin.element()), line.getElementIndex(end.element()));

        switch (Beam.spanning(line, range)) {
            case Spanning.Spanned<Beam>(var beam, _) -> beam.addTo();
            case Spanning.Refused<Beam> refused -> drop(refused.reportedAt(through(open, end)));
        }
    }

    /**
     * The end is derived, not read from the file.
     */
    private void resolveTies(List<NoteSite> notes) {
        for (var note : notes) {
            if (!NoteMapper.startsTie(note.pmNote())) {
                continue;
            }

            var anchor = markedNote(note);

            if (anchor == null || !Tie.canAnchor(anchor)) {
                dropDamaged(Strings.REPORT_TIES_REMOVED, Damage.WRONG_ELEMENT, at(note.element()));
                continue;
            }

            note.line().addTie(anchor);
        }
    }

    /**
     * The reader judges only the pairing and the ratio's form; the tuplet load pass judges the
     * rest once the song is complete.
     *
     * <p>The ratio cannot be read when {@code <actual-notes>} names no {@link TupletGrade}, or
     * when a {@code <normal-type>} comes without a positive {@code <normal-notes>}.
     *
     * <p>{@code <normal-type>} is the trust discriminator. A file that carries it states a
     * complete {@code <time-modification>}, taken at face value. Without it the ratio is unknown,
     * not damaged: the pending tuplet carries none and the tuplet load pass derives it from the
     * beat in effect.
     */
    private void resolveTuplets(List<NoteSite> notes) throws SAXException {
        OpenTuplet open = null;

        for (var note : notes) {
            var marker = NoteMapper.tupletMarker(note.pmNote());

            if (marker.start()) {
                dropDangling(Strings.REPORT_TUPLETS_REMOVED, open);
                open = new OpenTuplet(note, marker);
            }

            if (!marker.stop()) {
                continue;
            }

            if (open == null) {
                dropOrphanStop(Strings.REPORT_TUPLETS_REMOVED, note);
                continue;
            }

            buildTuplet(open, note);
            open = null;
        }

        dropDangling(Strings.REPORT_TUPLETS_REMOVED, open);
    }

    private void buildTuplet(OpenTuplet open, NoteSite end) {
        if (!pairsOnOneLine(Strings.REPORT_TUPLETS_REMOVED, open, end)) {
            return;
        }

        var marker = open.marker();
        var noteValue = statedNoteValue(marker);
        TupletNormalTime statedRatio = null;

        if (noteValue != null) {
            var normalNotes = marker.normalNotes();

            // SongScribe writes <normal-notes> beside every <normal-type> that states a ratio, so
            // a stated ratio without a positive count is damaged.
            if (normalNotes <= 0) {
                dropDamaged(Strings.REPORT_TUPLETS_REMOVED, Damage.NORMAL_NOTES, through(open, end));
                return;
            }

            statedRatio = new TupletNormalTime(normalNotes, new NotatableValue(noteValue, marker.normalDots()));
        }

        var grade = TupletGrade.of(marker.actualNotes());

        if (grade == null) {
            dropDamaged(Strings.REPORT_TUPLETS_REMOVED, Damage.TUPLET_NUMBER, through(open, end));
            return;
        }

        pendingTuplets.add(new PendingTuplet(
            open.opener().element(), end.element(), grade, statedRatio, marker.yOffsetSs()));
    }

    /**
     * Returns {@code null} when the file states no {@code <normal-type>} or names a token this
     * reader does not know; neither is damage.
     */
    @Nullable
    private static NoteValue statedNoteValue(NoteMapper.TupletMarker marker) {
        var token = marker.normalTypeToken();

        return token == null ? null : NoteTypeMapping.noteValueForTypeToken(token.trim());
    }

    /**
     * Start is processed before stop so a note carrying both builds the single-note trill.
     */
    private void resolveTrills(List<NoteSite> notes) {
        OpenTrill open = null;

        for (var note : notes) {
            var marker = NoteMapper.trillMarker(note.pmNote());

            if (marker.start()) {
                dropDangling(Strings.REPORT_TRILLS_REMOVED, open);
                open = new OpenTrill(note, marker.yOffsetSs());
            }

            if (!marker.stop()) {
                continue;
            }

            if (open == null) {
                dropOrphanStop(Strings.REPORT_TRILLS_REMOVED, note);
                continue;
            }

            if (pairsOnOneLine(Strings.REPORT_TRILLS_REMOVED, open, note)) {
                var anchor = open.opener().element();
                var element = note.element();
                var trill = anchor == element ? new Trill(anchor) : new Trill(anchor, element);

                if (open.yOffsetSs() != 0) {
                    trill.setUserYOffsetSs(open.yOffsetSs());
                }

                trill.addTo();
            }

            open = null;
        }

        dropDangling(Strings.REPORT_TRILLS_REMOVED, open);
    }

    private boolean pairsOnOneLine(String headingKey, OpenMark open, NoteSite end) {
        if (end.line() == open.opener().line()) {
            return true;
        }

        dropDamaged(headingKey, Damage.LINE_BREAK, through(open, end));
        return false;
    }

    /**
     * @param open {@code null} when nothing is open
     */
    private void dropDangling(String headingKey, @Nullable OpenMark open) {
        if (open != null) {
            dropDamaged(headingKey, Damage.NO_END, at(open.opener().element()));
        }
    }

    private void dropOrphanStop(String headingKey, NoteSite stop) {
        dropDamaged(headingKey, Damage.NO_START, at(stop.element()));
    }

    /**
     * @param where {@code null} when the mark sat on no element
     */
    private void dropDamaged(String headingKey, Damage damage, @Nullable ChangeLines where) {
        drop(new RecordedChange(headingKey, Strings.get(damage.reasonKey), where));
    }

    private void drop(RecordedChange entry) {
        LOG.warn("Dropping a damaged mark: {}", entry);
        readerEntries.add(entry);
    }

    /**
     * Every element read here and in {@link #at} sits on a line of the song.
     */
    private static ChangeLines through(OpenMark open, NoteSite end) {
        return new ChangeLines(
            open.opener().element().requireParentLine(),
            end.element().requireParentLine()
        );
    }

    /**
     * @return {@code null} when {@code element} is {@code null}
     */
    private static @Nullable ChangeLines at(@Nullable StaffElement element) {
        return element == null ? null : ChangeLines.on(element.requireParentLine());
    }

    /**
     * @return {@code null} when the element is not a {@link NoteElement}, as for a rest
     */
    private static @Nullable NoteElement markedNote(NoteSite note) {
        return note.element() instanceof NoteElement noteElement ? noteElement : null;
    }
}
