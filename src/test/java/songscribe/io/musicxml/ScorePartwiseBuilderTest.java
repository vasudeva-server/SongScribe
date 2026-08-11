/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.build;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.countOccurrences;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.marshal;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.audiveris.proxymusic.Articulations;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.Notations;
import org.audiveris.proxymusic.Ornaments;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.StartStop;
import org.audiveris.proxymusic.Tied;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Beam;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.layout.LineLayoutProvider;

/**
 * Pins the properties of the write path that only a whole marshalled document shows.
 *
 * <h2>{@code <notations>} child order</h2>
 * Neither a round-trip nor the schema can see it: the content model is an unbounded choice, so
 * every order validates, and the reader is order-insensitive, so every order round-trips into
 * the same {@link Song}. The order is nonetheless observable in the file, and several adjustment
 * passes write into the one list, so only an assertion on the emitted sequence holds it.
 *
 * <h2>{@code <metronome>} child order</h2>
 * The opposite case: the schema is the only thing that sees it. A metric modulation's
 * {@code <metronome-relation>} must sit <em>between</em> the two {@code <metronome-note>}s, which
 * the generated model cannot express — the marshalled order is restored by
 * {@code MusicXmlSerializer}'s stream-writer delegate, and a round-trip is blind to whether it
 * happened.
 *
 * <h2>Where a breath mark lands</h2>
 * MusicXML has no standalone breath mark, so it is folded into the preceding note. Which note
 * that is takes a whole document to state, because the answer for an element with no note before
 * it is that the mark is dropped.
 *
 * <h2>Which lines are laid out</h2>
 * Nothing in the emitted document says whether a line was laid out, yet laying one out resolves
 * its automatic stem directions as a side effect. Only a {@link LineLayoutProvider} that records
 * what it was asked can see it.
 *
 * <h2>Which note a span's marker lands on</h2>
 * The passes reach their notes through {@code BuildIndex}, keyed by {@code StaffElement}. Two
 * elements that differ in nothing observable must still reach two different notes, which takes a
 * document holding a matched pair to state.
 */
class ScorePartwiseBuilderTest extends UnitTest {

    /** Grade 3 (triplet): 3 actual notes in the time of 2 normal notes. */
    private static final int TRIPLET_GRADE = 3;

    /** A triplet occupies the time of two notes of its written value. */
    private static final int TRIPLET_NORMAL_NOTES = 2;

    /** No dots on the tuplet's written note value. */
    private static final int NO_DOTS = 0;

    /** The one line of the two-line layout fixture that carries a glissando. */
    private static final int GLISSANDO_LINE_INDEX = 0;

    /** The attached end of the half-detached beam, in a line of two notes. */
    private static final int LAST_ELEMENT_OF_PAIR_INDEX = 1;


    @Test
    void testNotationsChildrenAreEmittedInPassOrder() throws Exception {
        var score = build(songWithEveryNotationOnOneNote());
        var members = firstNotations(score).getTiedOrSlurOrTuplet();

        // The first note carries a tie start, a tuplet-bracket start, a trill and an accent
        // simultaneously — four passes, one list.
        assertThat(members)
            .as("<notations> children of the first note")
            .extracting(Object::getClass)
            .containsExactly(
                Tied.class, org.audiveris.proxymusic.Tuplet.class, Ornaments.class, Articulations.class);
    }

    @Test
    void testNotationsChildrenMarshalInThatOrder() throws Exception {
        var xml = marshal(build(songWithEveryNotationOnOneNote()));

        // The order below is only worth asserting on a document that is valid to begin
        // with: <notations> content is an unbounded choice, so the schema is blind to the
        // order itself, and this is what keeps the fixture honest about everything else.
        new MusicXmlSchemaValidator().validate(xml);

        assertThat(childElementNames(xml, MusicXmlTags.NOTATIONS))
            .as("emitted <notations> child sequence")
            .containsExactly(
                MusicXmlTags.TIED, MusicXmlTags.TUPLET, MusicXmlTags.ORNAMENTS, MusicXmlTags.ARTICULATIONS);
    }

    @Test
    void testMetricModulationMarshalsTheRelationBetweenTheNoteGroups() throws Exception {
        var xml = marshal(build(songWithMetricModulation()));

        // The schema is the whole point here: it is what rejects the order the generated model
        // marshals on its own, <metronome-note/><metronome-note/><metronome-relation/>.
        new MusicXmlSchemaValidator().validate(xml);

        assertThat(childElementNames(xml, MusicXmlTags.METRONOME, MusicXmlTags.METRONOME_RELATION))
            .as("emitted metric-modulation <metronome> child sequence")
            .containsExactly(
                MusicXmlTags.METRONOME_NOTE, MusicXmlTags.METRONOME_RELATION, MusicXmlTags.METRONOME_NOTE);
    }

    /**
     * A breath mark attaches to the note immediately before it, and one with no such note is
     * dropped.
     *
     * <p>None of the dropped positions is reachable by editing: the UI does not let a breath
     * mark be placed where there is no preceding note — after a barline, after another breath
     * mark, or at the start of a line. They are constructed directly here because a
     * hand-edited file can still hold them, and because the alternative rule — walking
     * backwards to the nearest note — would move a mark across a barline into the previous
     * measure, or double it onto a note that already carries one. Dropping is what the
     * streaming writer did, and this pins it.
     */
    @Test
    void testBreathMarkFoldsOnlyIntoTheImmediatelyPrecedingNote() throws Exception {
        var xml = marshal(build(songWithBreathMarksInEveryPosition()));
        assertThat(countOccurrences(xml, '<' + MusicXmlTags.BREATH_MARK + "/>"))
            .as("emitted <breath-mark/> count")
            .isEqualTo(1);

        assertThat(childElementNames(xml, MusicXmlTags.ARTICULATIONS))
            .as("the surviving mark folds into the articulations of the note before it")
            .containsExactly(MusicXmlTags.BREATH_MARK);
    }

    /**
     * A note that already carries an articulation of its own and then absorbs a following
     * breath mark grows one {@code <articulations>} element, not two.
     *
     * <p>The note is reached twice — once while it is built, once when the breath mark after it
     * is folded in — and the second visit has to find the element the first one created. Two
     * elements would be schema-valid and would round-trip into the same song, since the reader
     * reads every {@code <notations>} child of a note as one set; it is the file that would be
     * wrong.
     */
    @Test
    void testAnAbsorbedBreathMarkJoinsTheNotesOwnArticulations() throws Exception {
        var xml = marshal(build(buildSong(line -> {
            var note = crotchet();
            line.addElement(note);
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
            line.addElement(breathMark());
        })));

        assertThat(countOccurrences(xml, '<' + MusicXmlTags.ARTICULATIONS + '>'))
            .as("emitted <articulations> element count")
            .isEqualTo(1);
        assertThat(childElementNames(xml, MusicXmlTags.ARTICULATIONS))
            .as("the note's own accent and the folded-in mark share one element")
            .containsExactly(MusicXmlTags.ACCENT, MusicXmlTags.BREATH_MARK);
    }

    /**
     * The interior note of a tie chain emits its {@code <tied type="stop">} before its
     * {@code <tied type="start">}.
     *
     * <p>Both are {@code <notations>} children of equal rank, so the ranking in
     * {@code NoteBuilder.addNotation} does not decide between them — their arrival order inside
     * the tie pass does. Nothing else can see it: the schema accepts either order and the reader
     * is order-insensitive, so
     * {@code MusicXmlSpanRoundTripTest.testThreeNoteTieChainRoundTrips} recovers the same two
     * ties whichever way round they were written.
     */
    @Test
    void testAnInteriorTiedNoteMarshalsItsStopBeforeItsStart() throws Exception {
        var xml = marshal(build(buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addTie(new Tie(note0, note1));
            line.addTie(new Tie(note1, note2));
        })));

        // Four markers over three notes: the interior note carries two of them, and they are
        // the middle pair. Written start-before-stop the sequence would read start, start,
        // stop, stop.
        assertThat(typeAttributes(xml, MusicXmlTags.TIED))
            .as("emitted <tied type> sequence across the chain")
            .containsExactly(
                StartStop.START.value(), StartStop.STOP.value(),
                StartStop.START.value(), StartStop.STOP.value());
    }

    /**
     * A line carrying no glissando is never laid out.
     *
     * <p>Slide endpoints are the only geometry the writer emits, so the glissando pass reaches
     * its lines through the glissandos rather than by walking the song. That is not an
     * optimisation free to be undone: laying a line out resolves its automatic stem directions
     * as a side effect, so a writer that asked for every line would change the {@code <stem>} of
     * lines the streaming writer never touched. The two lines below emit identically either
     * way, so only a provider that records what it was asked can hold this.
     */
    @Test
    void testALineWithoutAGlissandoIsNeverLaidOut() {
        var song = buildSong(
            line -> {
                var source = crotchet();
                source.setGlissando();
                line.addElement(source);
                line.addElement(crotchet());
            },
            line -> {
                line.addElement(crotchet());
                line.addElement(crotchet());
            });

        var requestedLineIndices = new ArrayList<Integer>();
        var delegate = LineLayoutProvider.headless(song, DocumentFonts.defaultFonts());

        build(song, (line, lineIndex) -> {
            requestedLineIndices.add(lineIndex);
            return delegate.layoutFor(line, lineIndex);
        });

        assertThat(requestedLineIndices)
            .as("line indices the writer asked for a layout of")
            .containsExactly(GLISSANDO_LINE_INDEX);
    }

    /**
     * Two rests that differ in nothing observable, each anchoring its own tuplet, each keep
     * their own bracket.
     *
     * <p>{@code BuildIndex.notes} is keyed by {@code StaffElement}, and the only thing that
     * separates these two keys is their identity. The map is an {@code IdentityHashMap} — its
     * constructor refuses anything else — but a plain {@code HashMap} would behave identically
     * today, because {@code StaffElement} defines no {@code equals}/{@code hashCode}. This is
     * the case that would break if it ever gained them: the second rest would overwrite the
     * first's entry, both brackets would land on the second rest's note, and the first would
     * silently lose its tuplet. {@code Line.getElementIndex}, on the same path, keys the same
     * way for the same reason.
     *
     * <p>So it cannot fail against today's code — no single change makes it fail, since the
     * constructor stops the map substitution on its own. It states, in a form a later reader
     * can run, what identity keying is buying.
     */
    @Test
    void testStructurallyIdenticalRestsEachKeepTheirOwnSpan() {
        var score = build(songWithATupletAnchoredOnEachOfTwoIdenticalRests());

        // One start on each rest, one stop on each group's last note, nothing in between.
        assertThat(notesOf(score))
            .as("<tuplet> markers per note, in document order")
            .extracting(ScorePartwiseBuilderTest::tupletMarkerCount)
            .containsExactly(1, 0, 1, 1, 0, 1);
    }

    /**
     * A span whose endpoints resolve to no position in the line holding it is skipped, and the
     * rest of the document is written as if it were not there.
     *
     * <p>{@code Span.getAnchorElementIndex} answers from the line its endpoint element belongs
     * to, and an element a removal detached belongs to none — the state
     * {@code Span.indexInLine} documents and reports as -1. A span left in that shape is not
     * the model's intent, but the writer is not the place to discover it: attaching the marker
     * anyway would beam a note chosen by arithmetic rather than by the song, and without the
     * range check the write throws {@link IndexOutOfBoundsException} outright, turning a stale
     * span into a save that fails.
     *
     * <p>The four span passes share this skeleton, so a beam stands in for all of them. The
     * other half of the check — an index at or past the line's end — is what a legitimate
     * cross-line span produces, and both of its lines see it, so it is a span to place in the
     * <em>other</em> line rather than one to drop.
     */
    @Test
    void testASpanWithADetachedEndpointIsSkipped() throws Exception {
        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
        });

        // One endpoint is a note of the line, the other was never added to any line — the
        // asymmetry a removal leaves, and the shape that reaches the range check at all. A beam
        // with both ends detached reports the same index twice and is dropped a step earlier,
        // as the degenerate single-note beam it looks like.
        var line = song.getLine(0);
        var beam = new Beam(crotchet(), line.getElement(LAST_ELEMENT_OF_PAIR_INDEX));
        song.withoutMutationTracking(() -> line.addSpan(beam));

        var xml = marshal(build(song));

        new MusicXmlSchemaValidator().validate(xml);

        assertThat(elementsNamed(xml, MusicXmlTags.BEAM).getLength())
            .as("a beam whose endpoints are in no line contributes no <beam>")
            .isZero();
    }

    /**
     * Three crotchets, the first carrying every kind of {@code <notations>} child an
     * adjustment pass can attach: a tie start, a tuplet-bracket start, a trill start and an
     * accent.
     */
    private static Song songWithEveryNotationOnOneNote() {
        return buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            note0.addArticulation(new Articulation(note0, ArticulationType.ACCENT));
            line.addTie(new Tie(note0, note1));
            line.addTuplet(
                new Tuplet(note0, note2, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));
            line.addSpan(new Trill(note0, note2));
        });
    }

    /** One note carrying a dotted-crotchet-equals-minim metric modulation. */
    private static Song songWithMetricModulation() {
        return buildSong(line -> {
            var note = crotchet();
            line.addElement(note);
            note.addAttachment(
                new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM)));
        });
    }

    /**
     * A breath mark in each of the four positions it can occupy: opening the line, after a note,
     * after another breath mark, and after a barline. Only the second has a note to fold into.
     */
    private static Song songWithBreathMarksInEveryPosition() {
        return buildSong(line -> {
            line.addElement(breathMark());
            line.addElement(crotchet());
            line.addElement(breathMark());
            line.addElement(breathMark());
            line.addElement(singleBarline());
            line.addElement(breathMark());
            line.addElement(crotchet());
        });
    }

    /**
     * Two three-element tuplet groups, each anchored on a crotchet rest. The two rests are
     * constructed the same way and left untouched, so nothing but their identity tells them
     * apart.
     */
    private static Song songWithATupletAnchoredOnEachOfTwoIdenticalRests() {
        return buildSong(line -> {
            var firstRest = crotchetRest();
            var firstGroupEnd = crotchet();
            var secondRest = crotchetRest();
            var secondGroupEnd = crotchet();
            line.addElement(firstRest);
            line.addElement(crotchet());
            line.addElement(firstGroupEnd);
            line.addElement(secondRest);
            line.addElement(crotchet());
            line.addElement(secondGroupEnd);
            line.addTuplet(new Tuplet(
                firstRest, firstGroupEnd, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
                ElementType.CROTCHET, NO_DOTS));
            line.addTuplet(new Tuplet(
                secondRest, secondGroupEnd, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
                ElementType.CROTCHET, NO_DOTS));
        });
    }

    /** Every {@code <note>} of the score's single part, in document order. */
    private static List<Note> notesOf(ScorePartwise score) {
        var notes = new ArrayList<Note>();

        for (var measure : score.getPart().getFirst().getMeasure()) {
            for (var item : measure.getNoteOrBackupOrForward()) {
                if (item instanceof Note note) {
                    notes.add(note);
                }
            }
        }

        return notes;
    }

    /** How many {@code <tuplet>} bracket markers {@code note} carries. */
    private static int tupletMarkerCount(Note note) {
        var count = 0;

        for (var notations : note.getNotations()) {
            for (var member : notations.getTiedOrSlurOrTuplet()) {
                if (member instanceof org.audiveris.proxymusic.Tuplet) {
                    count++;
                }
            }
        }

        return count;
    }

    /** The {@code <notations>} of the first {@code <note>} in the score. */
    private static Notations firstNotations(ScorePartwise score) {
        var notes = notesOf(score);

        assertThat(notes).as("<note> count").isNotEmpty();

        return notes.getFirst().getNotations().getFirst();
    }

    /** The element names of the first {@code parentTag} element's children, in document order. */
    private static List<String> childElementNames(String xml, String parentTag) throws Exception {
        var parents = elementsNamed(xml, parentTag);

        assertThat(parents.getLength()).as("<%s> element count", parentTag).isPositive();

        return childElementNames(parents.item(0));
    }

    /**
     * The same, for the first {@code parentTag} element that has a {@code requiredChild} child.
     *
     * <p>A document holds more than one {@code <metronome>} — the song's own tempo mark as well
     * as any metric modulation — so the one under test has to be named by what it contains
     * rather than by where it sits.
     */
    private static List<String> childElementNames(String xml, String parentTag, String requiredChild)
        throws Exception {
        var parents = elementsNamed(xml, parentTag);

        for (var i = 0; i < parents.getLength(); i++) {
            var names = childElementNames(parents.item(i));

            if (names.contains(requiredChild)) {
                return names;
            }
        }

        throw new AssertionError("No <" + parentTag + "> in the document carries a <" + requiredChild + '>');
    }

    /** The {@code type} attribute of every {@code tag} element in the document, in order. */
    private static List<String> typeAttributes(String xml, String tag) throws Exception {
        var elements = elementsNamed(xml, tag);
        var types = new ArrayList<String>();

        for (var i = 0; i < elements.getLength(); i++) {
            types.add(((Element) elements.item(i)).getAttribute(MusicXmlTags.ATTR_TYPE));
        }

        return types;
    }

    private static NodeList elementsNamed(String xml, String tag) throws Exception {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)))
            .getElementsByTagName(tag);
    }

    private static List<String> childElementNames(Node parent) {
        var names = new ArrayList<String>();
        var children = parent.getChildNodes();

        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                names.add(((Element) child).getTagName());
            }
        }

        return names;
    }
}
