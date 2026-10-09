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
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementLocationRange;
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
import songscribe.dom.ReportedChange;
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
import songscribe.error.RuntimeError;
import songscribe.io.DocumentValidation;
import songscribe.io.ReadAnnotation;

/**
 * Maps the first {@code <part>} of an unmarshalled {@code <score-partwise>} onto the
 * {@link Song}'s {@link Line}s: the line split, the running key, the barlines
 * and voltas, the measure-level {@code <direction>}s (hairpins, tempo marks, metric
 * modulations, annotations) and the per-note range spans. Each {@code <note>} itself is
 * mapped by {@link NoteMapper}.
 *
 * <p><b>Shape.</b> One structural walk, then one resolution pass per mark that pairs two
 * positions — the read-side mirror of {@code ScorePartwiseBuilder}'s adjustment passes.
 * The walk builds only what a single child settles on its own: it starts a line at each
 * new-system marker, applies each {@code <key>}, appends an element per barline and calls
 * {@link NoteMapper} per note. What each child produced is recorded in its
 * {@link ChildSite}, so a pass can ask where an element sits in the document without
 * re-deriving it.
 *
 * <p><b>Where a {@code <key>} lands</b> is decided by the measure it sits in: the measure that
 * opened the current line establishes that line's own {@link Line#setKey key}, and any other
 * measure appends a {@link KeyChangeElement} at the current position. Nothing carries a
 * running key forward — a line the file gave no {@code <key>} inherits, which is what
 * {@code Song.rebuildInheritedKeysAfterParsing} settles once the walk is done.
 *
 * <p><b>Two children of {@code <key>} are deliberately not read.</b> {@code <cancel>} is derived
 * from the key change itself by {@code songscribe.dom.Key#accidentalsFrom}, so a document renders under this
 * program's cancellation policy rather than under whichever one wrote it; {@code <mode>} is never
 * read because no SongScribe key has a mode and only this program's own files reach a mapper.
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
 * <p><b>Damaged marks.</b> This writer pairs every span mark it emits, so a mark that does not
 * pair is damage in a file of ours: a start no stop pairs with, a stop no start pairs with, a
 * beam, tuplet bracket or trill whose two ends land on different lines, or a wedge bound to no
 * note. A paired tuplet whose ratio cannot be read is damaged too, as is a tie, a beam mark or a
 * slide mark on an element that cannot carry it. The pass that meets a damaged mark drops it,
 * keeps the notes it sat on, adds one entry to the reader's report entries and reads the rest of
 * the document, so a file damaged in one span still opens and the user sees what was dropped.
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

    /**
     * How a damaged mark fails, each with the reason its report entry gives. The class Javadoc
     * states which marks are damaged.
     */
    private enum Damage {

        /** A start mark that no stop mark pairs with. */
        NO_END(Strings.REPORT_REASON_DAMAGED_NO_END),

        /** A stop mark that no start mark pairs with. */
        NO_START(Strings.REPORT_REASON_DAMAGED_NO_START),

        /** A start and a stop on different lines, for a span one line must hold whole. */
        LINE_BREAK(Strings.REPORT_REASON_DAMAGED_LINE_BREAK),

        /** A {@code <wedge>} with no note after it to bind to. */
        UNBOUND(Strings.REPORT_REASON_DAMAGED_UNBOUND),

        /** A tie, beam or slide mark on an element that cannot carry it. */
        WRONG_ELEMENT(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT),

        /** A tuplet whose {@code <actual-notes>} no {@link TupletGrade} holds. */
        TUPLET_NUMBER(Strings.REPORT_REASON_DAMAGED_TUPLET_NUMBER),

        /** A tuplet that states a {@code <normal-type>} without a positive {@code <normal-notes>}. */
        NORMAL_NOTES(Strings.REPORT_REASON_DAMAGED_NORMAL_NOTES);

        /** The {@code Strings} key of the reason, whose value is plain text the user reads. */
        private final String reasonKey;

        Damage(String reasonKey) {
            this.reasonKey = reasonKey;
        }
    }

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
        @Nullable AboveBelow placement,
        @Nullable FormattedTextId words,
        @Nullable Metronome metronome
    ) {}

    /**
     * A {@code <wedge>} start's kind and its user's offsets. The writer puts the hairpin's
     * horizontal offset on both of its wedges, so the start wedge's {@code relative-x} is the
     * hairpin's.
     */
    private record WedgeStart(Hairpin.Kind kind, double xOffsetSs, double yOffsetSs) {}

    /** A span mark whose start has been read from a note's site and awaits its stop. */
    private sealed interface OpenMark permits OpenHairpin, OpenSlide, OpenBeam, OpenTuplet, OpenTrill {

        /** The site the start mark was read from. */
        NoteSite opener();
    }

    /** A hairpin whose start wedge has bound to {@code opener}'s note and awaits its stop. */
    private record OpenHairpin(NoteSite opener, WedgeStart start) implements OpenMark {}

    /** A glissando whose start marker sits on {@code opener} and awaits its stop. */
    private record OpenSlide(NoteSite opener) implements OpenMark {}

    /** A beam whose begin marker sits on {@code opener} and awaits its end. */
    private record OpenBeam(NoteSite opener) implements OpenMark {}

    /** A tuplet bracket whose start sits on {@code opener} and awaits its stop. */
    private record OpenTuplet(NoteSite opener, NoteMapper.TupletMarker marker) implements OpenMark {}

    /** A trill whose start wavy line sits on {@code opener} and awaits its stop. */
    private record OpenTrill(NoteSite opener, double yOffsetSs) implements OpenMark {}

    /**
     * An ending whose {@code number="1"} start has been read and awaits its {@code number="2"}
     * stop.
     *
     * @param anchor    the element the ending starts on, or {@code null} while a note-anchored start
     *                  awaits the next note
     * @param yOffsetSs the start marker's vertical offset
     */
    private record OpenEnding(@Nullable StaffElement anchor, double yOffsetSs) {}

    // -------------------------------------------------------------------------
    // Walk state — the whole of it
    // -------------------------------------------------------------------------

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
    private final List<ReportedChange> readerEntries = new ArrayList<>();

    // The features the document declares; decides which per-note attributes the walk honors.
    private final Set<SupportedFeature> features;

    private MeasureMapper(Song song, Set<SupportedFeature> features) {
        this.song = song;
        this.features = features;
    }

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Maps {@code score}'s first part onto {@code song}'s lines, honoring only the
     * per-note attributes {@code features} declares. A damaged span mark does not refuse the
     * document: it is dropped and reported, as the class Javadoc states.
     *
     * @return {@code song}, with the tuplets, endings and hairpins the walk paired still pending,
     *     and the report entries the walk made: one for each damaged mark it dropped, in the order
     *     it dropped them, then the accidentals-converted entry when the walk converted a retired
     *     accidental
     * @throws SAXException if the document is corrupt, or states something SongScribe cannot
     *                      represent
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

    // -------------------------------------------------------------------------
    // Lines and keys
    // -------------------------------------------------------------------------

    /**
     * Commits the line that just ended and starts the next one detached from the
     * song. The new line is not added until it, too, is complete.
     *
     * <p>The new line establishes no key of its own. A MusicXML key persists until restated, and
     * that is what {@link Line}'s inheritance already means, so a line the file gave no
     * {@code <key>} is left inheriting rather than keyed to a carried-forward value. Keying it
     * would look identical and round-trip, and would then stop a later edit to an earlier line's
     * key from reaching it.
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
     * Applies one {@code <key>}, at the position the document puts it.
     *
     * <p>A {@code <key>} in the measure that opened the current line establishes that
     * <em>line's</em> key. Measure 1 is not a special case: a song has no key of its own, so the
     * key measure 1 names is the first line's key exactly as any later one is its own line's.
     *
     * <p>A {@code <key>} in any other measure is a mid-line change and appends a
     * {@link KeyChangeElement} at the current position, so only the notes <em>after</em> it are
     * re-spelled. Applying it to the whole line instead is what made a document with a mid-system
     * key change load wrong.
     *
     * <p>A {@code <key>} before any line has started is ignored, matching how this reader treats
     * every other child that arrives before its {@code <print new-system>}.
     *
     * @throws SAXException if a mid-measure {@code <key>} is not immediately preceded by a barline
     *                      or repeat element, or if {@code fifths} is out of range
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
     * Enforces {@link KeyChangeElement}'s position invariant on the way in.
     *
     * <p>The writer opens the measure a mid-line key change sits in with that change's barline, so
     * a mid-measure {@code <key>} with no barline before it describes a model this program cannot
     * build. Nothing downstream would notice: the deletion pairing and the editing UI both read
     * the invariant rather than re-checking it, so the document would simply load
     * invariant-violating and misbehave later, with no error and no visible symptom.
     *
     * @throws SAXException if {@code line} is empty, or its last element is neither a barline nor
     *                      a repeat
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
        var line = requireCurrentLine("barline element");
        var element = elementType.newInstance();
        line.addElement(element);
        return element;
    }

    /**
     * Returns the line being built, for a child that cannot be mapped without one.
     *
     * @param construct the document construct being mapped, named as a reader would recognize it
     *                  in the file — it is what the diagnostic reports as having arrived too early
     * @return the current line; never null
     * @throws SAXException if no line has been started yet
     */
    private Line requireCurrentLine(String construct) throws SAXException {
        var line = currentLine;

        if (line == null) {
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: {} encountered before any line was started", construct);
        }

        return line;
    }

    // -------------------------------------------------------------------------
    // Notes
    // -------------------------------------------------------------------------

    private void walkNote(ChildSite site, Note pmNote) throws SAXException {
        var line = requireCurrentLine("<note>");
        var mapped = NoteMapper.map(pmNote, line, features);
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
     * its own: a note-anchored start then binds to the next mapped note,
     * and a note-terminated stop to the last element the line had at that point.
     *
     * <p>An ending is an intra-line span, and a complete ending is built on its
     * {@code number="2"} stop, so a start still open when a new line starts or when the part
     * ends is a damaged mark, as is a {@code number="2"} stop with no start open on its line.
     * Each is dropped and reported. A note-anchored start awaiting its note is not yet on a
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
     * Drops an ending start that no stop pairs with, and reports it at the element it starts on.
     *
     * @param open the open ending, or {@code null} when none is open
     * @effects when {@code open} is not {@code null}, adds one entry to the reader's entries,
     *     with no location when the start still awaits its note
     */
    private void dropOpenEnding(@Nullable OpenEnding open) {
        if (open != null) {
            dropDamaged(Strings.REPORT_ENDINGS_REMOVED, Damage.NO_END, at(open.anchor()));
        }
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
     * The {@link Annotation} a {@code <direction>} carries, or {@code null} when it
     * carries none. A direction is an annotation iff it has a {@code placement}
     * attribute: the writer emits {@code placement} only on annotation directions, so
     * its presence is the unambiguous discriminator, and a words-only direction without
     * it is a tempo description rather than a phantom annotation.
     *
     * @return the annotation the direction describes and where it sits, or {@code null} when it
     *         carries none or no text
     * @log warn if the direction's words carry no value or a blank one
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

        return new DirectionContent(direction.getPlacement(), words, metronome);
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
     *
     * <p>A second tempo or metric modulation arriving before the note replaces the first,
     * which is what this program's own documents never contain, so the loss is logged
     * rather than silent. The note takes each through
     * {@link DocumentValidation#attachUnique}, so a note the document has already given a
     * tempo change or a beat change refuses the document.
     *
     * @throws SAXException if a bound note already carries a tempo change or a beat change
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
     * The {@link Tempo} a beat-unit {@code <metronome>} states.
     *
     * <p>A {@code print-object="no"} metronome with no description states a tempo that draws
     * nothing. This shows it instead of dropping it. See {@link TempoMarking#fromFile}.
     *
     * @param metronome   the element to read the beat unit and the speed from
     * @param description the text of the direction's {@code <words>}, or {@code null} when the
     *                    direction carries no words, or carries words that belong to an
     *                    annotation rather than to this tempo
     * @return the tempo the element states, or {@code null} when the element states no speed,
     *         no beat unit, or a beat unit this reader does not know
     * @throws SAXException when the {@code <per-minute>} text is not an integer
     * @log warn when it ignores an unrecognised beat unit
     * @log warn when it shows a metronome the file asked to hide
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
        var duration = BeatUnitMapping.beatUnitFor(durationToken.trim(), durationDotCount);
        var beat = BeatUnitMapping.beatUnitFor(beatToken.trim(), beatDotCount);

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
     * <p>A hairpin is an intra-line span, so a start still open when a new line starts or when
     * the part ends is a damaged mark, as is a stop with no start open on its line, a second
     * start while one is open, and a wedge no note follows on its line. Each is dropped and
     * reported.
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

    /**
     * Drops every wedge that no note followed on its line, and empties {@code wedges}.
     *
     * @effects adds one entry with no location to the reader's entries for each wedge
     */
    private void dropUnboundWedges(List<Wedge> wedges) {
        for (var _ : wedges) {
            dropDamaged(Strings.REPORT_HAIRPINS_REMOVED, Damage.UNBOUND, null);
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
     * Records a {@link PendingHairpin} over [{@code anchor}, {@code end}] carrying the
     * offsets of its start wedge. It builds no {@link Hairpin}: the span load pass judges each
     * record once the song is complete, and it merges two same-kind wedges that overlap or
     * merely touch into the one hairpin a file so written musically is.
     */
    private void buildHairpin(OpenHairpin open, StaffElement end) {
        var start = open.start();

        pendingHairpins.add(new PendingHairpin(
            start.kind(), open.opener().element(), end, start.xOffsetSs(), start.yOffsetSs()));
    }

    // -------------------------------------------------------------------------
    // Passes: per-note range spans
    // -------------------------------------------------------------------------
    //
    // Each runs over the part's whole note list in document order rather than line by
    // line, which is what lets a tie or a glissando cross a line boundary, and what lets
    // a beam, tuplet or trill pair split across one be seen as one damaged mark.
    // -------------------------------------------------------------------------

    /**
     * Pairs each {@code <slide type="start">} with the following {@code type="stop"}:
     * the start note is the glissando. A glissando needs two notes, so a start is never
     * itself marked on read. A start no stop pairs with, a stop no start pairs with, and a
     * pair either of whose marks sits on an element that is not a note are damaged, and each
     * is dropped and reported.
     */
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

    /**
     * Collapses each note's primary {@code <beam number="1">} marker into a {@link Beam}:
     * {@code begin} opens the run, {@code continue} keeps it open, and {@code end} closes it.
     * A {@code begin} no {@code end} pairs with and an {@code end} no {@code begin} pairs with
     * are damaged, and each is dropped and reported; {@link #closeBeam} judges a closed run.
     */
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
     * Adds the beam a closed run states, as {@link Beam#spanning} judges the range from its
     * {@code begin} to its {@code end}. A run whose two marks land on different lines, or whose
     * either mark sits on an element that is not a note, is damaged; so is a run the beam rule
     * refuses. Each is dropped and reported.
     *
     * @param open the run its {@code begin} opened
     * @param end  the site its {@code end} was read from
     * @effects adds the beam to {@code end}'s line, or adds one entry to the reader's entries
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
     * Ties each note whose {@code <tied>} markers carry a start and that {@link Tie#canAnchor}
     * accepts. The end is derived, not read from the file: {@link Line#addTie} anchors the tie
     * and {@link NoteElement#tie()} finds where it lands. A start on any other element — a rest
     * or a grace note — is damaged, and is dropped and reported.
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
     * Collapses each note's {@code <tuplet>} bracket markers into {@link PendingTuplet}s. The
     * reader judges only the pairing and the ratio's form; whether the span holds enough notes,
     * and whether its ratio fits the beat, the tuplet load pass judges once the song is
     * complete, as it does every pending tuplet.
     *
     * <p>A start no stop pairs with, a stop no start pairs with, a pair whose two ends land on
     * different lines, and a pair whose ratio cannot be read are damaged, and each is dropped and
     * reported. The ratio cannot be read when {@code <actual-notes>} names no
     * {@link TupletGrade}, or when a {@code <normal-type>} comes without a positive
     * {@code <normal-notes>}.
     *
     * <p>{@code <normal-type>} is the trust discriminator. A file that carries it states
     * a complete {@code <time-modification>}, so its {@code <normal-notes>} is taken at
     * face value and the pending tuplet carries that stated ratio. A file without it
     * predates this format, or states a token this reader does not recognise; either way
     * the ratio is unknown rather than damaged, so the pending tuplet carries none and the
     * tuplet load pass derives the ratio from the beat in effect.
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

    /**
     * Records the pending tuplet a closed bracket states, or drops and reports the bracket when
     * its two ends land on different lines or its ratio cannot be read.
     *
     * @param open the bracket its start opened
     * @param end  the site its stop was read from
     * @effects adds one pending tuplet, or one entry to the reader's entries
     */
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
     * The written note value the anchor note's {@code <time-modification>} states,
     * or {@code null} when the file states no {@code <normal-type>} or names a token
     * this reader does not know. Either leaves the ratio to be derived rather than
     * trusted, so neither is damage.
     */
    @Nullable
    private static NoteValue statedNoteValue(NoteMapper.TupletMarker marker) {
        var token = marker.normalTypeToken();

        return token == null ? null : NoteTypeMapping.noteValueForTypeToken(token.trim());
    }

    /**
     * Collapses each note's {@code <wavy-line>} markers into {@link Trill}s. Start is
     * processed before stop so a note carrying both builds the single-note trill. A start no
     * stop pairs with, a stop no start pairs with, and a pair whose two ends land on different
     * lines are damaged, and each is dropped and reported.
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

    // -------------------------------------------------------------------------
    // Damaged marks
    // -------------------------------------------------------------------------

    /**
     * Answers whether a span's two marks landed on one line, and drops and reports the span when
     * they did not. A line holds its own spans, so a span whose ends are on different lines has
     * no line to live on.
     *
     * @param headingKey the {@code Strings} key of the report heading of the span's kind
     * @param open       the span's start
     * @param end        the site the stop mark was read from
     * @return {@code true} if {@code end} is on the line {@code open} started on
     * @effects when the lines differ, adds one entry to the reader's entries, over the range
     *     from the start to the stop
     */
    private boolean pairsOnOneLine(String headingKey, OpenMark open, NoteSite end) {
        if (end.line() == open.opener().line()) {
            return true;
        }

        dropDamaged(headingKey, Damage.LINE_BREAK, through(open, end));
        return false;
    }

    /**
     * Drops a start mark that no stop pairs with, and reports it at the element it sits on.
     *
     * @param headingKey the {@code Strings} key of the report heading of the span's kind
     * @param open       the still-open start, or {@code null} when nothing is open
     * @effects when {@code open} is not {@code null}, adds one entry to the reader's entries
     */
    private void dropDangling(String headingKey, @Nullable OpenMark open) {
        if (open != null) {
            dropDamaged(headingKey, Damage.NO_END, at(open.opener().element()));
        }
    }

    /**
     * Drops a stop mark that no start pairs with, and reports it at the element it sits on.
     *
     * @param headingKey the {@code Strings} key of the report heading of the span's kind
     * @param stop       the site the stop mark was read from
     * @effects adds one entry to the reader's entries
     */
    private void dropOrphanStop(String headingKey, NoteSite stop) {
        dropDamaged(headingKey, Damage.NO_START, at(stop.element()));
    }

    /**
     * Drops a damaged mark and reports it for the reason {@code damage} gives.
     *
     * @param headingKey the {@code Strings} key of the report heading of the kind the mark would
     *                   have made
     * @param damage     how the mark is damaged
     * @param where      the elements the mark sat on, or {@code null} when it sat on none
     * @effects adds one entry to the reader's entries
     */
    private void dropDamaged(String headingKey, Damage damage, @Nullable ElementLocationRange where) {
        drop(new ReportedChange(headingKey, Strings.get(damage.reasonKey), where));
    }

    /**
     * Records the report entry for a mark this reader dropped.
     *
     * @effects adds {@code entry} to the reader's entries
     * @log WARN naming the entry
     */
    private void drop(ReportedChange entry) {
        LOG.warn("Dropping a damaged mark: {}", entry);
        readerEntries.add(entry);
    }

    /**
     * The elements from the one a span's start mark sits on to the one its stop mark sits on,
     * which may be on different lines.
     */
    private static ElementLocationRange through(OpenMark open, NoteSite end) {
        return new ElementLocationRange(locationOf(open.opener().element()), locationOf(end.element()));
    }

    /**
     * The location of {@code element} alone.
     *
     * @return the range naming only {@code element}, or {@code null} when {@code element} is
     *     {@code null}
     */
    private static @Nullable ElementLocationRange at(@Nullable StaffElement element) {
        return element == null ? null : ElementLocationRange.single(locationOf(element));
    }

    /**
     * Where {@code element} sits in the song. The passes run once the walk has committed every
     * line, so every element they read sits on a line of the song.
     */
    private static ElementLocation locationOf(StaffElement element) {
        var line = element.getParentLine();

        if (line == null) {
            throw RuntimeError.exit("A mapped element sits on no line: " + element);
        }

        return new ElementLocation(line.index(), line.getElementIndex(element));
    }

    /**
     * Narrows the element a beam, tie or slide mark sits on to a note. Each of those marks joins
     * notes, so a mark on any other element is damaged; the caller drops and reports it.
     *
     * @param note the site the mark was read from
     * @return the site's element when it is a {@link NoteElement} — any note, grace notes
     *     included — or {@code null} when it is not, as for a rest
     */
    private static @Nullable NoteElement markedNote(NoteSite note) {
        return note.element() instanceof NoteElement noteElement ? noteElement : null;
    }
}
