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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.assertSpanEquals;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

/**
 * Round-trip tests for the per-note range spans {@code Tie}, {@code Tuplet}, and
 * {@code Trill}, plus the mid-line and measure-boundary-crossing cases. (Beams
 * live in their own class because the hook/level logic is the densest part.)
 */
class MusicXmlSpanRoundTripTest extends UnitTest {

    /**
     * Returns the text content of the first {@code tagName} element in the
     * document, or {@code null} if the document has none.
     */
    private static @Nullable String firstElementText(String xml, String tagName) throws Exception {
        var nodes = parseDocument(xml).getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    /** Returns the number of {@code tagName} elements in the document. */
    private static int elementCount(String xml, String tagName) throws Exception {
        return parseDocument(xml).getElementsByTagName(tagName).getLength();
    }

    private static Document parseDocument(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Returns the text content of the {@code <actual-notes>} element from the
     * first {@code <time-modification>} in the document, or {@code null} if absent.
     */
    private static @Nullable String firstActualNotes(String xml) throws Exception {
        return firstElementText(xml, MusicXmlTags.ACTUAL_NOTES);
    }

    /**
     * Returns the text content of the {@code <normal-notes>} element from the
     * first {@code <time-modification>} in the document, or {@code null} if absent.
     */
    private static @Nullable String firstNormalNotes(String xml) throws Exception {
        return firstElementText(xml, MusicXmlTags.NORMAL_NOTES);
    }

    /**
     * Returns the text content of the {@code <normal-type>} element from the
     * first {@code <time-modification>} in the document, or {@code null} if absent.
     */
    private static @Nullable String firstNormalType(String xml) throws Exception {
        return firstElementText(xml, MusicXmlTags.NORMAL_TYPE);
    }

    /**
     * Returns the number of {@code <time-modification>} elements in the document — one per
     * note that the writer treats as a tuplet member.
     */
    private static int timeModificationCount(String xml) throws Exception {
        return elementCount(xml, MusicXmlTags.TIME_MOD);
    }

    /**
     * Asserts that {@code tuplet} carries exactly the ratio {@code N} notes in the
     * time of {@code M} notes of the given written value — the three fields the
     * {@code <time-modification>} round-trip has to preserve.
     */
    private static void assertTupletRatio(
            Tuplet tuplet, int expectedGrade, int expectedNormalNotes,
            ElementType expectedNoteValue, int expectedNoteValueDots) {

        assertThat(tuplet.getGrade()).as("tuplet: grade").isEqualTo(expectedGrade);
        assertThat(tuplet.getNormalNotes()).as("tuplet: normalNotes").isEqualTo(expectedNormalNotes);
        assertThat(tuplet.getNoteValue()).as("tuplet: noteValue").isEqualTo(expectedNoteValue);
        assertThat(tuplet.getNoteValueDots()).as("tuplet: noteValueDots").isEqualTo(expectedNoteValueDots);
    }

    /**
     * A one-line song of {@code noteCount} notes from {@code noteFactory}, under the single
     * tuplet {@code tupletFactory} builds from the first and the last of them.
     *
     * <p>Every tuplet fixture below has that shape; what differs between them is the note kind,
     * the count, the ratio — and, for the unresolved case, whether the tuplet states a ratio at
     * all, which is why the tuplet is supplied rather than described by parameters.
     */
    private static Song songWithTuplet(
            int noteCount,
            Supplier<StaffElement> noteFactory,
            BiFunction<StaffElement, StaffElement, Tuplet> tupletFactory) {
        return buildSong(line -> {
            var notes = new ArrayList<StaffElement>(noteCount);

            for (var i = 0; i < noteCount; i++) {
                var note = noteFactory.get();
                notes.add(note);
                line.addElement(note);
            }

            line.addTuplet(tupletFactory.apply(notes.getFirst(), notes.getLast()));
        });
    }

    /** The written value of the {@code <normal-dot/>} fixtures. */
    private static StaffElement dottedQuaver() {
        var note = quaver();
        note.setDotCount(ONE_DOT);

        return note;
    }

    /** Grade 3 (triplet): 3 actual notes in the time of 2 normal notes. */
    private static final int TRIPLET_GRADE = 3;

    /** A triplet occupies the time of two notes of its written value. */
    private static final int TRIPLET_NORMAL_NOTES = 2;

    /** Grade 5 (quintuplet): 5 actual notes in the time of 4 normal notes. */
    private static final int QUINTUPLET_GRADE = 5;

    /** A quintuplet occupies the time of four notes of its written value. */
    private static final int QUINTUPLET_NORMAL_NOTES = 4;

    /** Grade 2 (duplet) in compound time: 2 actual notes in the time of 3 normal notes. */
    private static final int COMPOUND_DUPLET_GRADE = 2;

    /** A compound duplet occupies three of its written value — the ratio 2:1 cannot express. */
    private static final int COMPOUND_DUPLET_NORMAL_NOTES = 3;

    /** Grade 7 (septuplet): 7 actual notes in the time of 4 normal notes. */
    private static final int SEPTUPLET_GRADE = 7;

    /** A septuplet occupies the time of four notes of its written value. */
    private static final int SEPTUPLET_NORMAL_NOTES = 4;

    /** Element count of the septuplet fixture — one note per actual note. */
    private static final int SEPTUPLET_LAST_INDEX = SEPTUPLET_GRADE - 1;

    /**
     * Grade of the ratio-less tuplet fixture. Six, because no other value separates the
     * power-of-two stand-in from {@code grade - 1} and {@code grade / 2} at once.
     */
    private static final int UNRESOLVED_TUPLET_GRADE = 6;

    /** The largest power of two below {@link #UNRESOLVED_TUPLET_GRADE}. */
    private static final int UNRESOLVED_TUPLET_STAND_IN_NORMAL_NOTES = 4;

    /**
     * A {@code <normal-notes>} value no derivation would ever produce for the fixture
     * that carries it, so trusting it and deriving it cannot be confused.
     */
    private static final int UNTRUSTED_STATED_NORMAL_NOTES = 5;

    /** No dots on the tuplets' written note value in these fixtures. */
    private static final int NO_DOTS = 0;

    /** One dot on the tuplet's written note value — the {@code <normal-dot/>} case. */
    private static final int ONE_DOT = 1;

    // Performed <duration> values: the written tick count scaled by M / N.
    // The scaling is exact at NoteTypeMapping.DIVISIONS, which is the property
    // these expected values pin down.

    /** A crotchet in a 3:2 tuplet: 13440 × 2 / 3. */
    private static final int TRIPLET_CROTCHET_PERFORMED_TICKS = 8960;

    /** A dotted quaver in a 3:2 tuplet: 6720 × 3/2 × 2 / 3. */
    private static final int TRIPLET_DOTTED_QUAVER_PERFORMED_TICKS = 6720;

    /** A semiquaver in a 7:4 tuplet: 3360 × 4 / 7. */
    private static final int SEPTUPLET_SEMIQUAVER_PERFORMED_TICKS = 1920;

    /** A non-zero vertical position (staff spaces) that survives the round-trip. */
    private static final int TUPLET_VERTICAL_POSITION_SS = 2;

    /** A non-zero Y position (staff spaces) that survives the round-trip. */
    private static final int TRILL_Y_POSITION_SS = 3;

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: tie round-trip tests
    //
    // A Tie is a pure index-pair span with no extra fields.  The reader's
    // addTie() merge logic collapses the interior stop+start pairs that the
    // writer emits on interior notes of a chain into one Tie(firstAnchor,
    // lastEnd).
    // -------------------------------------------------------------------------

    @Test
    void testTwoNoteTieRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addTie(new Tie(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var ties = line2.findTies();

        assertThat(ties).as("tie count after two-note tie round-trip").hasSize(1);
        assertSpanEquals(ties.getFirst(), 0, 1);
    }

    @Test
    void testThreeNoteTieChainRoundTrips() throws Exception {
        // The writer emits stop+start on the interior note (note1) for the two adjacent
        // ties; the reader closes the stop/start pair for each and produces two distinct
        // Tie spans — tie ranges never coalesce.
        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addTie(new Tie(note0, note1));
            line.addTie(new Tie(note1, note2));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var ties = line2.findTies();

        assertThat(ties).as("tie count after three-note chain round-trip").hasSize(2);
        assertSpanEquals(ties.get(0), 0, 1);
        assertSpanEquals(ties.get(1), 1, 2);
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: tuplet round-trip tests
    //
    // Asserts that the whole ratio (grade, normalNotes, note value + its dots)
    // and verticalPositionSs survive the write→read cycle, plus writer-output
    // assertions on the <time-modification> block itself and on the performed
    // <duration> the ratio scales.
    // -------------------------------------------------------------------------

    @Test
    void testTripletRoundTrips() throws Exception {
        var song = songWithTuplet(TRIPLET_GRADE, StaffElementFactory::crotchet,
            (anchor, end) -> new Tuplet(
                anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after triplet round-trip").hasSize(1);
        assertSpanEquals(tuplets.getFirst(), 0, 2, TRIPLET_GRADE, 0);
        assertTupletRatio(tuplets.getFirst(), TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
    }

    // A dotted written value is the only thing <normal-dot/> encodes, and three
    // corpus tuplets have one — without the element they cannot round-trip.
    @Test
    void testDottedNoteValueRoundTrips() throws Exception {
        var song = songWithTuplet(TRIPLET_GRADE, MusicXmlSpanRoundTripTest::dottedQuaver,
            (anchor, end) -> new Tuplet(
                anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.QUAVER, ONE_DOT));

        var xml = writeToString(song);

        assertThat(firstNormalType(xml))
            .as("<normal-type> must name the tuplet's written value")
            .isEqualTo(NoteTypeMapping.TYPE_EIGHTH);
        assertThat(elementCount(xml, MusicXmlTags.NORMAL_DOT))
            .as("one <normal-dot/> per dot, on each of the %d tupleted notes", TRIPLET_GRADE)
            .isEqualTo(TRIPLET_GRADE * ONE_DOT);

        var tuplets = parse(xml).getLine(0).findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after dotted-value round-trip").hasSize(1);
        assertTupletRatio(tuplets.getFirst(), TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.QUAVER, ONE_DOT);
    }

    // 7:4 is the ratio that forces the DIVISIONS choice: it must survive both the
    // <time-modification> round-trip and the performed-duration division exactly.
    @Test
    void testSeptupletRoundTripsExactly() throws Exception {
        var song = songWithTuplet(SEPTUPLET_GRADE, StaffElementFactory::semiquaver,
            (anchor, end) -> new Tuplet(anchor, end, SEPTUPLET_GRADE, SEPTUPLET_NORMAL_NOTES,
                ElementType.SEMIQUAVER, NO_DOTS));

        var xml = writeToString(song);

        assertThat(firstElementText(xml, MusicXmlTags.DURATION))
            .as("a septuplet semiquaver's performed <duration>")
            .isEqualTo(Integer.toString(SEPTUPLET_SEMIQUAVER_PERFORMED_TICKS));

        var tuplets = parse(xml).getLine(0).findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after septuplet round-trip").hasSize(1);
        assertSpanEquals(tuplets.getFirst(), 0, SEPTUPLET_LAST_INDEX, SEPTUPLET_GRADE, 0);
        assertTupletRatio(tuplets.getFirst(), SEPTUPLET_GRADE, SEPTUPLET_NORMAL_NOTES,
            ElementType.SEMIQUAVER, NO_DOTS);
    }

    // <duration> is the *performed* duration, so a tuplet member sounds shorter
    // than its <type> says while a note outside the span is unscaled.
    @Test
    void testTupletMemberDurationIsPerformedAndOutsiderIsNot() throws Exception {
        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            var outsider = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(outsider);
            line.addTuplet(new Tuplet(note0, note2, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));
        });

        var xml = writeToString(song);
        var durations = parseDocument(xml).getElementsByTagName(MusicXmlTags.DURATION);

        assertThat(durations.getLength()).as("<duration> count").isEqualTo(TRIPLET_GRADE + 1);

        for (var i = 0; i < TRIPLET_GRADE; i++) {
            assertThat(durations.item(i).getTextContent().trim())
                .as("tupleted crotchet %d: performed <duration>", i)
                .isEqualTo(Integer.toString(TRIPLET_CROTCHET_PERFORMED_TICKS));
        }

        assertThat(durations.item(TRIPLET_GRADE).getTextContent().trim())
            .as("a crotchet outside the tuplet keeps its written <duration>")
            .isEqualTo(Integer.toString(NoteTypeMapping.DIVISIONS));
    }

    @Test
    void testDottedTupletMemberDurationIsPerformed() throws Exception {
        var song = songWithTuplet(TRIPLET_GRADE, MusicXmlSpanRoundTripTest::dottedQuaver,
            (anchor, end) -> new Tuplet(
                anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.QUAVER, ONE_DOT));

        assertThat(firstElementText(writeToString(song), MusicXmlTags.DURATION))
            .as("a tripleted dotted quaver's performed <duration>")
            .isEqualTo(Integer.toString(TRIPLET_DOTTED_QUAVER_PERFORMED_TICKS));
    }

    // A file written before <normal-type> existed states a <normal-notes> that cannot
    // be trusted, so the reader leaves the ratio for the post-load pass to derive. The
    // fixture states a ratio no derivation would produce, so "trusted" and "derived"
    // cannot be confused: three quarters at a quarter beat can only derive M = 2.
    @Test
    void testTupletWithoutNormalTypeIsDerivedNotTrusted() throws Exception {
        var timeModification =
            "        <time-modification><actual-notes>" + TRIPLET_GRADE + "</actual-notes>"
            + "<normal-notes>" + UNTRUSTED_STATED_NORMAL_NOTES + "</normal-notes></time-modification>\n";
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            timeModification +
            "        <notations><tuplet type=\"start\" number=\"1\"/></notations>\n" +
            "      </note>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            timeModification +
            "      </note>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            timeModification +
            "        <notations><tuplet type=\"stop\" number=\"1\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var tuplets = parse(xml).getLine(0).findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count read from a <normal-type>-less file").hasSize(1);
        // parse() goes through the whole read seam, so the post-load pass has already
        // settled the ratio by the time the song comes back.
        assertTupletRatio(tuplets.getFirst(), TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
            ElementType.CROTCHET, NO_DOTS);
    }

    // The same file *with* <normal-type> states a complete ratio, so the reader takes
    // its <normal-notes> at face value — even the non-conventional M = 3 that a duplet
    // outside compound time would never derive. The stated V must still agree with
    // S / N or the file contradicts itself, so the two notes are written as dotted
    // quavers: S = 2 x 72 = 144 ticks, S / N = 72, which is the stated dotted quaver.
    @Test
    void testTupletWithNormalTypeIsTrustedAsStated() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>eighth</type>
                        <dot/>
                        <time-modification><actual-notes>2</actual-notes><normal-notes>3</normal-notes>\
                <normal-type>eighth</normal-type><normal-dot/></time-modification>
                        <notations><tuplet type="start" number="1"/></notations>
                      </note>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>eighth</type>
                        <dot/>
                        <time-modification><actual-notes>2</actual-notes><normal-notes>3</normal-notes>\
                <normal-type>eighth</normal-type><normal-dot/></time-modification>
                        <notations><tuplet type="stop" number="1"/></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var tuplets = parse(xml).getLine(0).findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count read from a <normal-type>-carrying file").hasSize(1);
        assertTupletRatio(tuplets.getFirst(), COMPOUND_DUPLET_GRADE, COMPOUND_DUPLET_NORMAL_NOTES,
            ElementType.QUAVER, ONE_DOT);
    }

    @Test
    void testTripletWithVerticalPositionRoundTrips() throws Exception {
        var song = songWithTuplet(TRIPLET_GRADE, StaffElementFactory::crotchet, (anchor, end) -> {
            var tuplet = new Tuplet(
                anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            tuplet.setVerticalPositionSs(TUPLET_VERTICAL_POSITION_SS);

            return tuplet;
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after triplet+verticalPos round-trip").hasSize(1);
        assertSpanEquals(tuplets.getFirst(), 0, 2, TRIPLET_GRADE, TUPLET_VERTICAL_POSITION_SS);
    }

    // #592: a grace note may sit inside a tuplet span without joining it, so it takes no
    // <time-modification> — only the three tupleted crotchets do.
    @Test
    void testGraceNoteInsideTupletSpanTakesNoTimeModification() throws Exception {
        var song = buildSong(line -> {
            var note0 = crotchet();
            var grace = graceQuaver();
            var note2 = crotchet();
            var note3 = crotchet();
            line.addElement(note0);
            line.addElement(grace);
            line.addElement(note2);
            line.addElement(note3);
            line.addTuplet(new Tuplet(note0, note3, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));
        });

        var xml = writeToString(song);

        assertThat(timeModificationCount(xml))
            .as("one <time-modification> per tupleted note, none for the grace note")
            .isEqualTo(TRIPLET_GRADE);

        // The reader must re-collapse the span across the gap the skipped grace note leaves.
        var line2 = roundTrip(song).getLine(0);
        var tuplets = line2.findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after grace-in-span round-trip").hasSize(1);
        assertSpanEquals(tuplets.getFirst(), 0, 3, TRIPLET_GRADE, 0);
        assertThat(line2.getElement(1).getType().isGraceNote())
            .as("interior grace note survives inside the tuplet span")
            .isTrue();
    }

    @Test
    void testQuintupletRoundTrips() throws Exception {
        var song = songWithTuplet(QUINTUPLET_GRADE, StaffElementFactory::crotchet,
            (anchor, end) -> new Tuplet(anchor, end, QUINTUPLET_GRADE, QUINTUPLET_NORMAL_NOTES,
                ElementType.CROTCHET, NO_DOTS));

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after quintuplet round-trip").hasSize(1);
        assertSpanEquals(tuplets.getFirst(), 0, 4, QUINTUPLET_GRADE, 0);
        assertTupletRatio(tuplets.getFirst(), QUINTUPLET_GRADE, QUINTUPLET_NORMAL_NOTES,
            ElementType.CROTCHET, NO_DOTS);
    }

    @Test
    void testQuintupletWithVerticalPositionRoundTrips() throws Exception {
        var song = songWithTuplet(QUINTUPLET_GRADE, StaffElementFactory::crotchet, (anchor, end) -> {
            var tuplet = new Tuplet(
                anchor, end, QUINTUPLET_GRADE, QUINTUPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            tuplet.setVerticalPositionSs(TUPLET_VERTICAL_POSITION_SS);

            return tuplet;
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after quintuplet+verticalPos round-trip").hasSize(1);
        assertSpanEquals(tuplets.getFirst(), 0, 4, QUINTUPLET_GRADE, TUPLET_VERTICAL_POSITION_SS);
    }

    @Test
    void testTripletTimeModificationInOutput() throws Exception {
        var song = songWithTuplet(TRIPLET_GRADE, StaffElementFactory::crotchet,
            (anchor, end) -> new Tuplet(
                anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));

        var xml = writeToString(song);

        assertThat(firstActualNotes(xml))
            .as("<actual-notes> for triplet must equal the grade (%d)", TRIPLET_GRADE)
            .isEqualTo(Integer.toString(TRIPLET_GRADE));
        assertThat(firstNormalNotes(xml))
            .as("<normal-notes> for triplet must be the stored M (%d)", TRIPLET_NORMAL_NOTES)
            .isEqualTo(Integer.toString(TRIPLET_NORMAL_NOTES));
        assertThat(firstNormalType(xml))
            .as("<normal-type> must name the stored written value")
            .isEqualTo(NoteTypeMapping.TYPE_QUARTER);
        assertThat(elementCount(xml, MusicXmlTags.NORMAL_DOT))
            .as("an undotted written value emits no <normal-dot/>")
            .isEqualTo(NO_DOTS);
    }

    @Test
    void testQuintupletTimeModificationInOutput() throws Exception {
        var song = songWithTuplet(QUINTUPLET_GRADE, StaffElementFactory::crotchet,
            (anchor, end) -> new Tuplet(anchor, end, QUINTUPLET_GRADE, QUINTUPLET_NORMAL_NOTES,
                ElementType.CROTCHET, NO_DOTS));

        var xml = writeToString(song);

        assertThat(firstActualNotes(xml))
            .as("<actual-notes> for quintuplet must equal the grade (%d)", QUINTUPLET_GRADE)
            .isEqualTo(Integer.toString(QUINTUPLET_GRADE));
        assertThat(firstNormalNotes(xml))
            .as("<normal-notes> for quintuplet must be the stored M (%d)", QUINTUPLET_NORMAL_NOTES)
            .isEqualTo(Integer.toString(QUINTUPLET_NORMAL_NOTES));
        assertThat(firstNormalType(xml))
            .as("<normal-type> must name the stored written value")
            .isEqualTo(NoteTypeMapping.TYPE_QUARTER);
    }

    /**
     * A tuplet that reached the writer without a ratio — the state
     * {@link Tuplet#withUnresolvedRatio} leaves one in until {@code TupletLoadPass} settles it.
     *
     * <p>Its own {@code normalNotes} is {@link Tuplet#UNRESOLVED_NORMAL_NOTES}, and writing that
     * through would emit a {@code <time-modification>} reading "6 notes in the time of 0" — which
     * the schema tolerates, {@code <normal-notes>} being a {@code nonNegativeInteger}, and which
     * our own reader would survive, since it derives the ratio it is not told. Nothing but this
     * test stands between that document and every other program that opens it. The writer
     * substitutes the conventional power-of-two stand-in instead, and omits
     * {@code <normal-type>}, which is the flag meaning "derive this ratio, do not trust the
     * stated one" — {@link #testTupletWithoutNormalTypeIsDerivedNotTrusted} is that reader half.
     *
     * <p>The grade is 6 so the stand-in cannot be confused with the two other values a wrong
     * fallback would plausibly produce: {@code grade - 1} is 5 and {@code grade / 2} is 3.
     */
    @Test
    void testUnresolvedTupletEmitsTheStandInRatioAndNoNormalType() throws Exception {
        var song = songWithTuplet(UNRESOLVED_TUPLET_GRADE, StaffElementFactory::crotchet,
            (anchor, end) -> Tuplet.withUnresolvedRatio(anchor, end, UNRESOLVED_TUPLET_GRADE));

        assertThat(song.getLine(0).findSpans(Tuplet.class).getFirst().getNormalNotes())
            .as("the fixture's tuplet must reach the writer with no ratio")
            .isEqualTo(Tuplet.UNRESOLVED_NORMAL_NOTES);

        var xml = writeToString(song);

        assertThat(firstActualNotes(xml))
            .as("<actual-notes> is the grade whether or not the ratio is known")
            .isEqualTo(Integer.toString(UNRESOLVED_TUPLET_GRADE));
        assertThat(firstNormalNotes(xml))
            .as("<normal-notes> falls back to the largest power of two below the grade")
            .isEqualTo(Integer.toString(UNRESOLVED_TUPLET_STAND_IN_NORMAL_NOTES));
        assertThat(elementCount(xml, MusicXmlTags.NORMAL_TYPE))
            .as("an unresolved tuplet states no written value, so it emits no <normal-type>")
            .isZero();
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: trill round-trip tests
    //
    // Single-note trills (anchor == end) and multi-note trills, with and
    // without a non-zero yPositionSs offset.
    // -------------------------------------------------------------------------

    @Test
    void testSingleNoteTrillRoundTrips() throws Exception {
        // anchor == end: the single-note constructor Trill(anchor) is equivalent.
        var song = buildSong(line -> {
            var note0 = crotchet();
            line.addElement(note0);
            line.addSpan(new Trill(note0));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findSpans(Trill.class);

        assertThat(trills).as("trill count after single-note trill round-trip").hasSize(1);
        assertSpanEquals(trills.getFirst(), 0, 0, 0);
    }

    @Test
    void testMultiNoteTrillRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addSpan(new Trill(note0, note2));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findSpans(Trill.class);

        assertThat(trills).as("trill count after multi-note trill round-trip").hasSize(1);
        assertSpanEquals(trills.getFirst(), 0, 2, 0);
    }

    @Test
    void testSingleNoteTrillWithYPositionRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = crotchet();
            line.addElement(note0);
            var trill = new Trill(note0);
            trill.setYPositionSs(TRILL_Y_POSITION_SS);
            line.addSpan(trill);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findSpans(Trill.class);

        assertThat(trills).as("trill count after single-note trill+yPos round-trip").hasSize(1);
        assertSpanEquals(trills.getFirst(), 0, 0, TRILL_Y_POSITION_SS);
    }

    @Test
    void testMultiNoteTrillWithYPositionRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            var trill = new Trill(note0, note1);
            trill.setYPositionSs(TRILL_Y_POSITION_SS);
            line.addSpan(trill);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findSpans(Trill.class);

        assertThat(trills).as("trill count after multi-note trill+yPos round-trip").hasSize(1);
        assertSpanEquals(trills.getFirst(), 0, 1, TRILL_Y_POSITION_SS);
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: mid-line and measure-boundary-crossing span tests
    //
    // Mid-line: a span surrounded by notes it does not cover — verifies that
    // the per-index lookup does not assign markers to the wrong notes.
    //
    // Measure-boundary: a SINGLE_BARLINE sits between anchor and end, splitting
    // the span across two MusicXML measures.  The reader must still re-collapse
    // the markers to the correct (anchor, end) index pair on the same line.
    // -------------------------------------------------------------------------

    @Test
    void testMidLineSpanRoundTrips() throws Exception {
        // Layout: note0 note1(anchor) note2(end) note3
        // Indices:   0      1            2          3
        // The trill covers only [1, 2]; notes 0 and 3 must not be included.
        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            var note3 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addSpan(new Trill(note1, note2));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findSpans(Trill.class);

        assertThat(trills).as("trill count after mid-line round-trip").hasSize(1);
        assertSpanEquals(trills.getFirst(), 1, 2, "mid-line trill");
    }

    @Test
    void testMeasureBoundaryCrossingTieRoundTrips() throws Exception {
        // Layout: note0(index 0) SINGLE_BARLINE(index 1) note1(index 2)
        // The tie crosses the barline; the writer emits the start on note0
        // (in measure 1) and the stop on note1 (in measure 2).  The reader
        // must re-collapse to Tie[0, 2].
        var song = buildSong(line -> {
            var note0 = crotchet();
            var barline = singleBarline();
            var note1 = crotchet();
            line.addElement(note0);
            line.addElement(barline);
            line.addElement(note1);
            line.addTie(new Tie(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var ties = line2.findTies();

        assertThat(ties).as("tie count after measure-boundary round-trip").hasSize(1);
        // note0 → index 0, SINGLE_BARLINE → index 1, note1 → index 2.
        assertSpanEquals(ties.getFirst(), 0, 2);
    }

    @Test
    void testMeasureBoundaryCrossingTupletRoundTrips() throws Exception {
        // Layout: note0(index 0) SINGLE_BARLINE(index 1) note1(index 2)
        // The triplet straddles the barline: the writer emits <time-modification>
        // on both notes and the <tuplet> start/stop notations on note0 (measure 1)
        // and note1 (measure 2).  The reader must recover the grade from
        // <time-modification> and re-collapse the notations to Tuplet[0, 2].
        // Three notes, not two: the subject here is recovering a span across a barline,
        // but the load pass still checks the ratio, and a grade-3 tuplet over two
        // quarters would state a written value of 192 / 3 = 64 ticks, which is not
        // notatable — the tuplet would be dropped for a reason unrelated to the test.
        var song = buildSong(line -> {
            var note0 = crotchet();
            var barline = singleBarline();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(barline);
            line.addElement(note1);
            line.addElement(note2);
            line.addTuplet(new Tuplet(note0, note2, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findSpans(Tuplet.class);

        assertThat(tuplets).as("tuplet count after measure-boundary round-trip").hasSize(1);
        // note0 → index 0, SINGLE_BARLINE → index 1, note1 → index 2, note2 → index 3.
        assertSpanEquals(tuplets.getFirst(), 0, 3, TRIPLET_GRADE, 0);
    }
}
