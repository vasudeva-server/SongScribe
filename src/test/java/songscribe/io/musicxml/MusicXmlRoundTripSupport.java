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

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.audiveris.proxymusic.ScorePartwise;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.SongFileWriter;
import songscribe.io.SongLoadResult;
import songscribe.layout.LineLayoutProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.UnitTest.FIXED_CLOCK;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.XmlFixtures.openTag;

/**
 * Shared plumbing for the MusicXML test classes: the write/read cycle, song
 * construction, the fixed clock every date-bearing case writes under, and the
 * cross-cutting assertion helpers used by more than one concern-specific test file.
 *
 * <p>Static helpers rather than a base class. Tests that need them already extend
 * {@link songscribe.UnitTest} directly, so making them inherited would leave every such
 * caller re-declaring its own copy.
 */
final class MusicXmlRoundTripSupport {

    private MusicXmlRoundTripSupport() {
    }

    static final String PART_LIST      = "part-list";
    static final String SCORE_PART     = "score-part";
    static final String PART           = "part";
    static final String MEASURE        = "measure";
    static final String ATTRIBUTES     = "attributes";
    static final String KEY            = "key";
    static final String FIFTHS         = "fifths";
    static final String PRINT          = "print";
    static final String BARLINE        = "barline";
    static final String BAR_STYLE      = "bar-style";
    static final String PITCH          = "pitch";
    static final String STEP           = "step";
    static final String OCTAVE         = "octave";
    static final String REST           = "rest";
    static final String GRACE          = "grace";
    static final String DURATION       = "duration";
    static final String NOTE_TYPE      = "type";
    static final String NOTATIONS      = "notations";
    static final String LYRIC          = "lyric";
    static final String SYLLABIC       = "syllabic";
    static final String LYRIC_TEXT     = "text";
    static final String BEAM           = "beam";
    static final String TIED           = "tied";
    static final String TIME_MOD       = "time-modification";
    static final String ACTUAL_NOTES   = "actual-notes";
    static final String NORMAL_NOTES   = "normal-notes";
    static final String NORMAL_TYPE    = "normal-type";
    static final String TUPLET         = "tuplet";
    static final String DIRECTION      = "direction";
    static final String DIRECTION_TYPE = "direction-type";
    static final String WEDGE          = "wedge";
    static final String ORNAMENTS      = "ornaments";
    static final String ENDING         = "ending";
    static final String BEAT_UNIT      = "beat-unit";
    static final String WORDS          = "words";
    static final String IDENTIFICATION = "identification";
    static final String ENCODING       = "encoding";

    static final String ATTR_NEW_SYSTEM   = "new-system";
    static final String ATTR_LOCATION     = "location";
    static final String ATTR_NUMBER       = "number";
    static final String ATTR_ID           = "id";
    static final String ATTR_DEFAULT_X    = "default-x";
    static final String ATTR_TYPE         = "type";
    static final String ATTR_PRINT_OBJECT = "print-object";

    static final String YES             = "yes";
    static final String NO              = "no";
    static final String BEAM_BEGIN      = "begin";
    static final String BEAM_END        = "end";
    static final String TYPE_START      = "start";
    static final String TYPE_STOP       = "stop";
    static final String WEDGE_CRESCENDO = "crescendo";

    /**
     * A non-zero X offset (in pixels) that survives the px → ss → tenths → ss → px
     * round-trip without rounding loss.  Any integer number of pixels is exact at the
     * fixed document scale (8 px per staff space), so 2 staff spaces = 16 px is a clean choice.
     */
    static final int X_OFFSET_PX = DocumentScale.ssToPx(2.0).positionPx();

    /**
     * Staff position of C4 — six steps below the B4 origin (staffPosition 0) in the
     * descending-pitch lattice.
     */
    static final int C4_STAFF_POSITION = 6;

    /** The year {@link UnitTest#FIXED_CLOCK} reports, for cases asserting on the rights credit. */
    static final int FIXED_CLOCK_YEAR = FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).getYear();

    /**
     * The {@code <encoding>} half of the provenance block, on its own so a fixture that
     * needs other {@code <identification>} children can put them in the <em>same</em>
     * element. A document may carry only one {@code <identification>}: a second is
     * schema-invalid, and a reader working from an object graph keeps just one of them,
     * so a fixture that emits two loses whichever it did not keep.
     */
    static final String SOFTWARE_ENCODING =
        "    " + openTag(ENCODING) + '\n' +
        "      " + element(MusicXmlTags.SOFTWARE, SoftwareProvenance.SOFTWARE) + '\n' +
        "    " + closeTag(ENCODING) + '\n';

    /**
     * Minimal SongScribe provenance block. Every hand-built {@code <score-partwise>}
     * fixture read through {@link MusicXmlReader} must carry the {@code <software>} value
     * {@link SoftwareProvenance#SOFTWARE}, or the reader's provenance gate
     * refuses the document before mapping any of it. Fixtures insert this immediately after the
     * root element so future fixtures inherit the tag instead of silently breaking.
     */
    static final String SOFTWARE_IDENTIFICATION =
        "  " + openTag(IDENTIFICATION) + '\n' +
        SOFTWARE_ENCODING +
        "  " + closeTag(IDENTIFICATION) + '\n';

    /** The single part these fixtures declare and then fill. */
    private static final String PART_ID = "P1";

    /** A written eighth note's {@code <duration>}, in the codec's divisions. */
    static final String EIGHTH_DURATION = Integer.toString(MusicXmlUnits.DIVISIONS / 2);

    private static final String MIDDLE_C_STEP = "C";
    private static final String MIDDLE_C_OCTAVE = "4";

    /** The {@code <pitch>} of middle C. */
    static final String MIDDLE_C_PITCH =
        element(PITCH, element(STEP, MIDDLE_C_STEP) + element(OCTAVE, MIDDLE_C_OCTAVE));

    private static final String NOTE_INDENT = "      ";
    private static final String NOTE_CHILD_INDENT = "        ";

    /**
     * Wraps {@code measureBody} in a minimal {@code <score-partwise>} document with a
     * SongScribe provenance tag, a new-system {@code <print>} (so the reader starts a
     * line), and standard attributes. Shared by the hand-built reader edge-case and
     * round-trip fixtures.
     */
    static String scoreWithMeasureBody(String measureBody) {
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            openTag(MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.ATTR_VERSION, MusicXmlTags.VERSION_VALUE) + '\n' +
            SOFTWARE_IDENTIFICATION +
            "  " + openTag(PART_LIST) + '\n' +
            "    " + openTag(SCORE_PART, ATTR_ID, PART_ID) +
                "<part-name></part-name>" + closeTag(SCORE_PART) + '\n' +
            "  " + closeTag(PART_LIST) + '\n' +
            "  " + openTag(PART, ATTR_ID, PART_ID) + '\n' +
            "    " + openTag(MEASURE, ATTR_NUMBER, "1") + '\n' +
            "      " + emptyTag(PRINT, ATTR_NEW_SYSTEM, YES) + '\n' +
            "      " + openTag(ATTRIBUTES) + '\n' +
            "        <divisions>" + MusicXmlUnits.DIVISIONS + "</divisions>\n" +
            "        " + element(KEY, element(FIFTHS, "0")) + '\n' +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      " + closeTag(ATTRIBUTES) + '\n' +
            measureBody +
            "    " + closeTag(MEASURE) + '\n' +
            "  " + closeTag(PART) + '\n' +
            closeTag(MusicXmlTags.SCORE_PARTWISE) + '\n';
    }

    /**
     * A {@code <note>} inside a measure body, holding {@code children} in order, one per line.
     * An empty child is left out, so a caller can pass a marker it may not need.
     */
    static String note(String... children) {
        var markup = new StringBuilder(NOTE_INDENT + openTag(MusicXmlTags.NOTE) + '\n');

        for (var child : children) {
            if (!child.isEmpty()) {
                markup.append(NOTE_CHILD_INDENT).append(child).append('\n');
            }
        }

        return markup.append(NOTE_INDENT).append(closeTag(MusicXmlTags.NOTE)).append('\n').toString();
    }

    /** A middle-C eighth note carrying {@code markers} after its {@code <type>}. */
    static String middleCEighth(String... markers) {
        var children = new ArrayList<>(List.of(
            MIDDLE_C_PITCH,
            element(DURATION, EIGHTH_DURATION),
            element(NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH)));
        children.addAll(List.of(markers));

        return note(children.toArray(String[]::new));
    }

    static String writeToString(Song song) throws Exception {
        return writeToString(song, DocumentFonts.defaultFonts());
    }

    // Goes through SongFileWriter rather than MusicXmlWriter directly: it is the save
    // path's entry point and the one place that decides to lay the song out headlessly
    // when the caller has no layout, which is exactly what a test wants.
    static String writeToString(Song song, DocumentFontsHolder fonts) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        SongFileWriter.write(song, fonts, pw);
        return sw.toString();
    }

    static Song parse(String xml) throws Exception {
        return parseResult(xml).song();
    }

    static SongLoadResult.Success parseResult(String xml) throws Exception {
        return MusicXmlReader.read(new InputSource(new StringReader(xml)));
    }

    static Song roundTrip(Song song) throws Exception {
        return roundTrip(song, DocumentFonts.defaultFonts()).song();
    }

    static SongLoadResult.Success roundTrip(Song song, DocumentFontsHolder fonts) throws Exception {
        return parseResult(writeToString(song, fonts));
    }

    /**
     * Builds the complete {@link ScorePartwise} graph for {@code song}, headless-laid-out
     * and dated from {@link UnitTest#FIXED_CLOCK}. This is the construction
     * {@code MusicXmlWriter.writeSong} drives internally, minus the marshal step — a case
     * that only needs the shape the builder produced has no reason to also exercise the
     * serializer.
     */
    static ScorePartwise build(Song song) {
        var fonts = DocumentFonts.defaultFonts();

        return build(song, fonts, LineLayoutProvider.headless(song, fonts));
    }

    /**
     * The same, with an explicit {@code layoutProvider} — for a case that needs to control
     * what layout the adjustment passes see (e.g. one reporting no geometry at all).
     */
    static ScorePartwise build(Song song, LineLayoutProvider layoutProvider) {
        return build(song, DocumentFonts.defaultFonts(), layoutProvider);
    }

    private static ScorePartwise build(Song song, DocumentFontsHolder fonts, LineLayoutProvider layoutProvider) {
        return ScorePartwiseBuilder.build(song, fonts, layoutProvider, FIXED_CLOCK);
    }

    /** Serializes a built graph, for the cases whose subject is the emitted text. */
    static String marshal(ScorePartwise score) {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlSerializer.marshal(score, pw);
        pw.flush();

        return sw.toString();
    }

    /** How many non-overlapping times {@code needle} occurs in {@code haystack}. */
    static int countOccurrences(String haystack, String needle) {
        var count = 0;
        var index = haystack.indexOf(needle);

        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }

        return count;
    }
}
