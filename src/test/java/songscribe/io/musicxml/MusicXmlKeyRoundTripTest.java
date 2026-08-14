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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.dom.TestKeys.B_FLAT_MAJOR;
import static songscribe.dom.TestKeys.C_MAJOR;
import static songscribe.dom.TestKeys.D_MAJOR;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.build;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.countOccurrences;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.marshal;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.audiveris.proxymusic.Attributes;
import org.audiveris.proxymusic.ScorePartwise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.KeyChange;
import songscribe.dom.KeyChangeKind;
import songscribe.dom.KeySignatureElement;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;

/**
 * Round-trip tests for key signatures through MusicXML, on both sides of the mapping stated in
 * {@code docs/musicxml-object-model.md}: a {@code <key>} lives in the {@code <attributes>} of the
 * measure its key takes effect in, so a line's own key goes into that line's first measure and a
 * {@link KeySignatureElement} goes into the measure its preceding barline opened.
 *
 * <p>What this class is responsible for is the <em>codec</em>: which measure a {@code <key>} is
 * written into, what it carries, what a read makes of one, and which documents the reader refuses.
 * The cancellation policy itself belongs to {@code KeyChangeTest} and is not retested here — what
 * is asserted is that the writer's {@code <cancel>} agrees with that policy, once per kind of
 * change the element distinguishes, which is the drift this codec could introduce and that class
 * cannot.
 *
 * <p>Cases needing a document this program's own writer never produces — a mid-measure
 * {@code <key>} with no barline before it, a {@code <fifths>} out of range, a {@code <cancel>}
 * that contradicts the policy — are hand-built fixtures. They earn their place because the reader
 * is the one entry point that takes key signatures from a file rather than from the editing UI,
 * where the position invariant is maintained rather than checked.
 */
class MusicXmlKeyRoundTripTest extends UnitTest {

    /** Staff position of F4, whose pitch class every sharp key alters and C major does not. */
    private static final int F4_STAFF_POSITION = 3;

    /** Line 0 (the default key), two key changes, then a line that keeps the last key. */
    private static final int EXPECTED_LINE_COUNT = 4;

    /** Element index of the {@link KeySignatureElement} in {@link #buildMidLineKeyChangeSong}. */
    private static final int MID_LINE_KEY_INDEX = 4;

    /**
     * Measure index that change opens: it is the line's third measure, and the line starts at
     * measure index 0.
     */
    private static final int MID_LINE_CHANGE_MEASURE_INDEX = 2;

    /**
     * Element index the hand-built two-measure fixture's mid-measure {@code <key>} lands at: its
     * first measure contributes a note and the barline that closes it.
     */
    private static final int FIXTURE_KEY_INDEX = 2;

    private static final String MODE_MAJOR_ELEMENT = "<mode>major</mode>";

    // -------------------------------------------------------------------------
    // The fifths mapping, over the whole domain
    // -------------------------------------------------------------------------

    static Stream<Key> allKeys() {
        return Key.allSignatures().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allKeys")
    void testEveryKeySignatureRoundTripsAsTheSongStartingKey(Key key) throws Exception {
        var reloaded = roundTrip(buildSong(line -> {
            line.setKey(key);
            line.addElement(crotchet());
        }));

        assertThat(reloaded.getStartingKey()).as("the key the song starts in").isEqualTo(key);
    }

    // -------------------------------------------------------------------------
    // Line-boundary key changes
    // -------------------------------------------------------------------------

    /**
     * Four lines, each holding one note so each contributes exactly one measure: line 0 in the
     * default key, line 1 changing to sharps, line 2 changing to flats, and line 3 keeping line
     * 2's key — so the writer emits no {@code <key>} for line 3 and the reader can only get it
     * right by leaving that line inheriting.
     */
    private static Song buildLineKeyChangeSong() {
        return buildSong(
            line -> {
                line.setKey(Key.DEFAULT);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(D_MAJOR);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(B_FLAT_MAJOR);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(B_FLAT_MAJOR);
                line.addElement(crotchet());
            }
        );
    }

    @Test
    void testLineKeyChangesRoundTrip() throws Exception {
        var reloaded = roundTrip(buildLineKeyChangeSong());

        assertThat(reloaded.getLines()).as("line count").hasSize(EXPECTED_LINE_COUNT);
        assertThat(reloaded.getLine(0).getRunningKey()).as("line 0 key").isEqualTo(Key.DEFAULT);
        assertThat(reloaded.getLine(1).getRunningKey()).as("line 1 key").isEqualTo(D_MAJOR);
        assertThat(reloaded.getLine(2).getRunningKey()).as("line 2 key").isEqualTo(B_FLAT_MAJOR);
        assertThat(reloaded.getLine(3).getRunningKey()).as("line 3 key").isEqualTo(B_FLAT_MAJOR);
    }

    @Test
    void testALineKeyChangeIsWrittenIntoThatLinesFirstMeasure() {
        var song = buildLineKeyChangeSong();

        // One note per line, so line N contributes exactly measure index N. A change is written
        // into the line it starts, never into the line before it.
        assertThat(keyMeasureIndices(build(song)))
            .as("measure index of every written <key>")
            .isEqualTo(linesThatChangeKey(song));
    }

    @Test
    void testNoKeyIsWrittenForTheCautionaryAtTheEndOfALine() {
        var song = buildLineKeyChangeSong();
        var score = build(song);

        // A cautionary is drawn at the end of lines 0 and 1, and it is rendering only: the
        // document carries one <key> per line that actually changes key, and not one more.
        assertThat(writtenKeys(score))
            .as("<key> elements written")
            .hasSameSizeAs(linesThatChangeKey(song));
        assertThat(countOccurrences(marshal(score), '<' + MusicXmlTags.KEY + '>'))
            .as("<key> elements surviving into the marshalled document")
            .isEqualTo(writtenKeys(score).size());
    }

    /**
     * The index of every line the writer owes a {@code <key>} for — line 0, plus every line whose
     * running key differs from the key the line before it ends in. Derived from the song so the
     * expectation cannot go stale if the fixture gains a line.
     */
    private static List<Integer> linesThatChangeKey(Song song) {
        var indices = new ArrayList<Integer>();

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            var previous = lineIndex == 0 ? null : song.getLine(lineIndex - 1).keyAtEndOfLine();

            if (!song.getLine(lineIndex).getRunningKey().equals(previous)) {
                indices.add(lineIndex);
            }
        }

        return indices;
    }

    @Test
    void testALineThatKeepsItsKeyIsLeftInheriting() throws Exception {
        var reloaded = roundTrip(buildLineKeyChangeSong());

        assertThat(reloaded.getLine(3).getKey())
            .as("a line the file gave no <key> establishes none of its own")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Mid-line key signatures
    // -------------------------------------------------------------------------

    /**
     * One line of four measures with a key change opening measure 3, so the elements read
     * {@code note | note | KEY note | note}. Every note sits on F4, which is what makes "did the
     * change re-spell the notes before it?" observable.
     */
    private static Song buildMidLineKeyChangeSong() {
        return buildSong(line -> {
            line.setKey(C_MAJOR);
            line.addElement(createNote(F4_STAFF_POSITION, true));
            line.addElement(singleBarline());
            line.addElement(createNote(F4_STAFF_POSITION, true));
            line.addElement(singleBarline());
            line.addElement(new KeySignatureElement(D_MAJOR));
            line.addElement(createNote(F4_STAFF_POSITION, true));
            line.addElement(singleBarline());
            line.addElement(createNote(F4_STAFF_POSITION, true));
        });
    }

    @Test
    void testAMidLineKeyChangeRoundTripsAtTheSameElementIndex() throws Exception {
        var element = roundTrip(buildMidLineKeyChangeSong()).getLine(0).getElement(MID_LINE_KEY_INDEX);

        assertThat(element)
            .as("the element at the mid-line key change's index")
            .isInstanceOf(KeySignatureElement.class);
        assertThat(((KeySignatureElement) element).getKey())
            .as("the key it establishes")
            .isEqualTo(D_MAJOR);
    }

    @Test
    void testAMidLineKeyChangeDoesNotRespellTheNotesBeforeIt() throws Exception {
        var line = roundTrip(buildMidLineKeyChangeSong()).getLine(0);
        var afterIndex = MID_LINE_KEY_INDEX + 1;

        // Reading a mid-measure <key> as the whole line's key is the defect this replaces: it
        // applied the three sharps retroactively, so every F in the line sounded as F sharp.
        assertThat(line.getElement(0).findEffectiveAccidental(line, 0))
            .as("measure 1's F, before the change")
            .isNull();
        assertThat(line.getElement(2).findEffectiveAccidental(line, 2))
            .as("measure 2's F, before the change")
            .isNull();
        assertThat(line.getElement(afterIndex).findEffectiveAccidental(line, afterIndex))
            .as("measure 3's F, after the change")
            .isEqualTo(StaffElement.Accidental.SHARP);
    }

    @Test
    void testAMidLineKeyChangeIsWrittenIntoTheMeasureItsBarlineOpened() {
        // Two <key> elements: measure index 0 carries the song's opening key, and the change goes
        // into the measure its own barline opened rather than into the line's first measure.
        assertThat(keyMeasureIndices(build(buildMidLineKeyChangeSong())))
            .as("measure index of every written <key>")
            .containsExactly(0, MID_LINE_CHANGE_MEASURE_INDEX);
    }

    @Test
    void testAMidLineKeyChangeSetsTheKeyTheNextLineInherits() throws Exception {
        var reloaded = roundTrip(buildSong(
            line -> {
                line.setKey(C_MAJOR);
                line.addElement(crotchet());
                line.addElement(singleBarline());
                line.addElement(new KeySignatureElement(D_MAJOR));
                line.addElement(crotchet());
            },
            line -> line.addElement(crotchet())
        ));

        assertThat(reloaded.getLine(1).getRunningKey())
            .as("the following line inherits the key the mid-line change left off in")
            .isEqualTo(D_MAJOR);
        assertThat(reloaded.getLine(1).getKey())
            .as("and establishes none of its own, so no <key> was written for it")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // <cancel>, asked of the policy rather than restated
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(KeyChangeKind.class)
    void testCancelIsWrittenExactlyWhenTheChangeDrawsNaturals(KeyChangeKind kind) {
        var song = buildSong(
            line -> {
                line.setKey(kind.previous());
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(kind.next());
                line.addElement(crotchet());
            }
        );

        var keys = writtenKeys(build(song));

        if (kind.previous().equals(kind.next())) {
            assertThat(keys).as("a key that does not change writes only measure 1's <key>").hasSize(1);
            return;
        }

        var drawn = KeyChange.accidentals(kind.previous(), kind.next());
        var policyCancels = drawn.getFirst().glyph() == SMuFLGlyph.ACCIDENTAL_NATURAL;
        var cancel = keys.get(1).getCancel();

        if (policyCancels) {
            assertThat(cancel).as("the policy draws naturals, so a <cancel> is owed").isNotNull();
            assertThat(cancel.getValue().intValue())
                .as("<cancel> carries the fifths of the key being cancelled")
                .isEqualTo(KeySignatureMapping.toFifths(kind.previous()));
        } else {
            assertThat(cancel).as("the policy draws no naturals, so no <cancel> is owed").isNull();
        }
    }

    @Test
    void testMeasureOneKeyCarriesNoCancel() {
        assertThat(writtenKeys(build(buildLineKeyChangeSong())).getFirst().getCancel())
            .as("nothing precedes measure 1's key, so it can cancel nothing")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // <mode>
    // -------------------------------------------------------------------------

    @Test
    void testEveryWrittenKeyCarriesMajorModeAfterItsFifths() {
        var xml = marshal(build(buildLineKeyChangeSong()));

        assertThat(countOccurrences(xml, MODE_MAJOR_ELEMENT))
            .as("<mode>major</mode> is written on every <key>")
            .isEqualTo(countOccurrences(xml, '<' + MusicXmlTags.KEY + '>'));
        assertThat(xml)
            .as("<mode> follows <fifths>, the order <key>'s content model requires")
            .containsPattern(
                '<' + MusicXmlTags.FIFTHS + ">-?\\d+</" + MusicXmlTags.FIFTHS + ">\\s*"
                    + MODE_MAJOR_ELEMENT);
    }

    // -------------------------------------------------------------------------
    // What the reader refuses, and what it ignores
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {-Key.MAX_ACCIDENTAL_COUNT, Key.MAX_ACCIDENTAL_COUNT})
    void testFifthsAtTheRangeBoundaryLoads(int fifths) throws Exception {
        var song = parse(twoMeasureScore(fifths, ""));

        assertThat(song.getStartingKey().accidentalCount())
            .as("a key signature at the largest accidental count loads")
            .isEqualTo(Key.MAX_ACCIDENTAL_COUNT);
    }

    @ParameterizedTest
    @ValueSource(ints = {-(Key.MAX_ACCIDENTAL_COUNT + 1), Key.MAX_ACCIDENTAL_COUNT + 1})
    void testFifthsPastTheRangeBoundaryFailsAsCorrupt(int fifths) {
        var exception = assertThrows(SAXException.class, () -> parse(twoMeasureScore(fifths, "")));

        assertThat(exception).hasMessageContaining("out of range");
    }

    @Test
    void testAMidMeasureKeyPrecededByABarlineLoads() throws Exception {
        var line = parse(twoMeasureScore(0, midMeasureKey(D_MAJOR, ""))).getLine(0);

        assertThat(line.getElement(FIXTURE_KEY_INDEX))
            .as("a mid-measure <key> becomes a key signature element after the barline")
            .isInstanceOf(KeySignatureElement.class);
    }

    @Test
    void testAMidMeasureKeyWithNoBarlineBeforeItFailsAsCorrupt() {
        // The barline closing measure 1 is removed, so the <key> follows a note. Nothing else
        // about the document changes, so nothing else can account for the failure.
        var xml = twoMeasureScore(0, midMeasureKey(D_MAJOR, ""))
            .replace(FIRST_MEASURE_BARLINE, "");

        var exception = assertThrows(SAXException.class, () -> parse(xml));

        assertThat(exception).hasMessageContaining("not preceded by a barline");
    }

    @Test
    void testAnIncomingCancelThatContradictsThePolicyIsIgnored() throws Exception {
        // C major to three sharps cancels nothing: the previous key has no accidentals to cancel.
        // This document claims a five-flat cancellation; the rendering must not follow it.
        var contradictoryCancel =
            '<' + MusicXmlTags.CANCEL + ">-5</" + MusicXmlTags.CANCEL + '>';
        var line = parse(twoMeasureScore(0, midMeasureKey(D_MAJOR, contradictoryCancel)))
            .getLine(0);
        var keySignature = (KeySignatureElement) line.getElement(FIXTURE_KEY_INDEX);

        assertThat(keySignature.getKey())
            .as("the key comes from <fifths>; <cancel> contributes nothing to it")
            .isEqualTo(D_MAJOR);
        assertThat(KeyChange.accidentals(line.keyAt(FIXTURE_KEY_INDEX - 1), keySignature.getKey()))
            .as("what is drawn follows the policy, which cancels nothing here")
            .noneMatch(accidental -> accidental.glyph() == SMuFLGlyph.ACCIDENTAL_NATURAL);
    }

    // -------------------------------------------------------------------------
    // Schema validity
    // -------------------------------------------------------------------------

    @Test
    void testKeyChangeOutputIsSchemaValid() {
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(writeToString(buildLineKeyChangeSong())))
            .as("a song with per-line key changes must be schema-valid")
            .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(writeToString(buildMidLineKeyChangeSong())))
            .as("a song with a mid-line key change must be schema-valid")
            .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Helpers: reading the built graph
    // -------------------------------------------------------------------------

    /** Every {@code <key>} the built score carries, in document order. */
    private static List<org.audiveris.proxymusic.Key> writtenKeys(ScorePartwise score) {
        var keys = new ArrayList<org.audiveris.proxymusic.Key>();

        for (var measure : score.getPart().getFirst().getMeasure()) {
            for (var child : measure.getNoteOrBackupOrForward()) {
                if (child instanceof Attributes attributes) {
                    keys.addAll(attributes.getKey());
                }
            }
        }

        return keys;
    }

    /** The measure index each {@code <key>} was written into, in document order. */
    private static List<Integer> keyMeasureIndices(ScorePartwise score) {
        var indices = new ArrayList<Integer>();
        var measures = score.getPart().getFirst().getMeasure();

        for (var measureIndex = 0; measureIndex < measures.size(); measureIndex++) {
            for (var child : measures.get(measureIndex).getNoteOrBackupOrForward()) {
                if (child instanceof Attributes attributes) {
                    for (var ignored : attributes.getKey()) {
                        indices.add(measureIndex);
                    }
                }
            }
        }

        return indices;
    }

    // -------------------------------------------------------------------------
    // Helpers: hand-built fixtures
    // -------------------------------------------------------------------------

    private static final String FIRST_MEASURE_BARLINE =
        "<barline location=\"right\"><bar-style>"
            + BarlineStyleMapping.BAR_STYLE_REGULAR + "</bar-style></barline>";

    private static final String CROTCHET_NOTE =
        "<note><pitch><step>B</step><octave>4</octave></pitch>"
            + "<duration>" + MusicXmlUnits.DIVISIONS + "</duration><type>quarter</type></note>";

    /** The {@code <attributes>} block a mid-measure key change arrives in. */
    private static String midMeasureKey(Key key, String cancel) {
        return '<' + MusicXmlTags.ATTRIBUTES + "><" + MusicXmlTags.KEY + '>'
            + cancel
            + '<' + MusicXmlTags.FIFTHS + '>' + KeySignatureMapping.toFifths(key)
            + "</" + MusicXmlTags.FIFTHS + '>'
            + "</" + MusicXmlTags.KEY + "></" + MusicXmlTags.ATTRIBUTES + '>';
    }

    /**
     * A two-measure, one-line document whose first measure opens in {@code startingFifths} and is
     * closed by a plain barline, and whose second measure begins with {@code secondMeasureHead}.
     * That is the shape a mid-line key change has on disk, so a fixture can put a {@code <key>}
     * there, or remove the barline to produce the one document the reader must refuse.
     */
    private static String twoMeasureScore(int startingFifths, String secondMeasureHead) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<score-partwise version=\"" + MusicXmlTags.VERSION_VALUE + "\">\n"
            + MusicXmlRoundTripSupport.SOFTWARE_IDENTIFICATION
            + "  <part-list><score-part id=\"P1\"><part-name></part-name></score-part></part-list>\n"
            + "  <part id=\"P1\">\n"
            + "    <measure number=\"1\">\n"
            + "      <print new-system=\"yes\"/>\n"
            + "      <attributes>\n"
            + "        <divisions>" + MusicXmlUnits.DIVISIONS + "</divisions>\n"
            + "        <key><fifths>" + startingFifths + "</fifths></key>\n"
            + "        <time print-object=\"no\"><senza-misura/></time>\n"
            + "        <clef><sign>G</sign><line>2</line></clef>\n"
            + "      </attributes>\n"
            + "      " + CROTCHET_NOTE + '\n'
            + "      " + FIRST_MEASURE_BARLINE + '\n'
            + "    </measure>\n"
            + "    <measure number=\"2\">\n"
            + "      " + secondMeasureHead + '\n'
            + "      " + CROTCHET_NOTE + '\n'
            + "      <barline location=\"right\"><bar-style>"
            + BarlineStyleMapping.BAR_STYLE_LIGHT_HEAVY + "</bar-style></barline>\n"
            + "    </measure>\n"
            + "  </part>\n"
            + "</score-partwise>\n";
    }
}
