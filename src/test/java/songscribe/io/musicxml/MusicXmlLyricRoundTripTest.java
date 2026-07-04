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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Round-trip coverage for per-note {@code <lyric>} emission and parsing (Phase 6).
 * Each case builds a lyric-bearing song, {@link #roundTrip}s it through the writer
 * and reader, and asserts the reloaded {@link Lyric} matches field-by-field
 * (verse, text, syllabic, extend, compound). The compound-boundary and
 * extender-carrier cases exercise the reader's marker-stripping and text-less
 * carrier handling; the cross-note case guards {@code NoteAccumulator.reset()}.
 */
class MusicXmlLyricRoundTripTest extends MusicXmlRoundTripSupport {

    // The single line every built song uses; the reader appends a terminal
    // barline after it, but the notes keep their original 0-based indices.
    private static final int LINE_INDEX = 0;

    private static final int FIRST_VERSE = 1;
    private static final int SECOND_VERSE = 2;

    // The END syllable is the third note of the BEGIN/MIDDLE/END word (indices 0
    // and 1 hold BEGIN and MIDDLE); 2 is not a self-evident index literal.
    private static final int END_SYLLABLE_INDEX = 2;

    // Pre-#420 files marked a compound-word boundary with a zero-width space
    // (U+200B) rather than the current U+2011; the loader must still recognize it.
    private static final String LEGACY_ZERO_WIDTH_SPACE_MARKER = Character.toString(0x200B);

    // -------------------------------------------------------------------------
    // Assertion + build helpers
    // -------------------------------------------------------------------------

    private static void assertLyricEquals(@Nullable Lyric actual, Lyric expected, String context) {
        assertThat(actual).as("%s: present", context).isNotNull();

        // isNotNull() above fails the test on null; this guard narrows the type
        // for NullAway on the field accesses below.
        if (actual == null) {
            return;
        }

        assertThat(actual.verse()).as("%s: verse", context).isEqualTo(expected.verse());
        assertThat(actual.text()).as("%s: text", context).isEqualTo(expected.text());
        assertThat(actual.syllabic()).as("%s: syllabic", context).isEqualTo(expected.syllabic());
        assertThat(actual.extend()).as("%s: extend", context).isEqualTo(expected.extend());
        assertThat(actual.compound()).as("%s: compound", context).isEqualTo(expected.compound());
    }

    private static StaffElement noteAt(Song song, int elementIndex) {
        return song.getLine(LINE_INDEX).getElement(elementIndex);
    }

    /** Adds a fresh crotchet carrying {@code lyrics} to {@code line} and returns it. */
    private static StaffElement addNote(Line line, Lyric... lyrics) {
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);

        for (var lyric : lyrics) {
            note.lyrics.add(lyric);
        }

        return note;
    }

    // -------------------------------------------------------------------------
    // Cases
    // -------------------------------------------------------------------------

    @Test
    void singlePlainSyllableRoundTrips() throws Exception {
        var expected = new Lyric(FIRST_VERSE, "oh", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var song = roundTrip(buildSong(line -> addNote(line, expected)));

        assertLyricEquals(noteAt(song, 0).getLyricForVerse(FIRST_VERSE), expected, "plain syllable");
        assertThat(noteAt(song, 0).getLyrics()).as("only one lyric").hasSize(1);
    }

    @Test
    void multiSyllableWordRoundTrips() throws Exception {
        var begin = new Lyric(FIRST_VERSE, "Ky", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false);
        var middle = new Lyric(FIRST_VERSE, "ri", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false);
        var end = new Lyric(FIRST_VERSE, "e", Lyric.Extend.NONE, Lyric.Syllabic.END, false);

        var song = roundTrip(buildSong(line -> {
            addNote(line, begin);
            addNote(line, middle);
            addNote(line, end);
        }));

        assertLyricEquals(noteAt(song, 0).getLyricForVerse(FIRST_VERSE), begin, "begin syllable");
        assertLyricEquals(noteAt(song, 1).getLyricForVerse(FIRST_VERSE), middle, "middle syllable");
        assertLyricEquals(
            noteAt(song, END_SYLLABLE_INDEX).getLyricForVerse(FIRST_VERSE), end, "end syllable");
    }

    @Test
    void compoundWordBoundaryRoundTrips() throws Exception {
        // The writer appends the U+2011 compound marker; the reader must strip it
        // and restore compound=true while leaving the text itself intact.
        var expected = new Lyric(FIRST_VERSE, "self", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true);
        var song = roundTrip(buildSong(line -> addNote(line, expected)));

        assertLyricEquals(noteAt(song, 0).getLyricForVerse(FIRST_VERSE), expected, "compound boundary");
    }

    @Test
    void extenderStartWithStopCarrierRoundTrips() throws Exception {
        var start = new Lyric(FIRST_VERSE, "oh", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false);
        var stopCarrier = new Lyric(FIRST_VERSE, "", Lyric.Extend.STOP, null, false);

        var song = roundTrip(buildSong(line -> {
            addNote(line, start);
            addNote(line, stopCarrier);
        }));

        assertLyricEquals(noteAt(song, 0).getLyricForVerse(FIRST_VERSE), start, "extender start");
        assertLyricEquals(noteAt(song, 1).getLyricForVerse(FIRST_VERSE), stopCarrier, "stop carrier");
    }

    @Test
    void continueCarrierRoundTrips() throws Exception {
        var start = new Lyric(FIRST_VERSE, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false);
        var continueCarrier = new Lyric(FIRST_VERSE, "", Lyric.Extend.CONTINUE, null, false);

        var song = roundTrip(buildSong(line -> {
            addNote(line, start);
            addNote(line, continueCarrier);
        }));

        assertLyricEquals(noteAt(song, 0).getLyricForVerse(FIRST_VERSE), start, "extender start");
        assertLyricEquals(noteAt(song, 1).getLyricForVerse(FIRST_VERSE), continueCarrier, "continue carrier");
    }

    @Test
    void twoVerseNoteRoundTrips() throws Exception {
        var verse1 = new Lyric(FIRST_VERSE, "one", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var verse2 = new Lyric(SECOND_VERSE, "two", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        var song = roundTrip(buildSong(line -> addNote(line, verse1, verse2)));
        var note = noteAt(song, 0);

        assertLyricEquals(note.getLyricForVerse(FIRST_VERSE), verse1, "verse 1");
        assertLyricEquals(note.getLyricForVerse(SECOND_VERSE), verse2, "verse 2");
        assertThat(note.getLyrics()).as("both verses present").hasSize(2);
    }

    /**
     * Cross-note reset regression: two consecutive notes each carry a distinct
     * lyric. If {@code NoteAccumulator.reset()} failed to clear its pending-lyric
     * list at the second {@code <note>}, note B would inherit note A's lyric with
     * no exception and no schema error — a pure stale-data corruption. This asserts
     * note B carries only its own lyric.
     */
    @Test
    void consecutiveNotesDoNotBleedLyrics() throws Exception {
        var noteALyric = new Lyric(FIRST_VERSE, "alpha", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var noteBLyric = new Lyric(FIRST_VERSE, "beta", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        var song = roundTrip(buildSong(line -> {
            addNote(line, noteALyric);
            addNote(line, noteBLyric);
        }));

        var noteB = noteAt(song, 1);
        assertThat(noteB.getLyrics()).as("note B carries only its own lyric").hasSize(1);
        assertLyricEquals(noteB.getLyricForVerse(FIRST_VERSE), noteBLyric, "note B lyric");
        assertThat(noteB.getLyrics())
            .as("note A's lyric must not bleed onto note B")
            .noneMatch(lyric -> lyric.text().equals(noteALyric.text()));
    }

    /**
     * Missing-{@code number} lenience: a hand-authored {@code <lyric>} with no
     * {@code number} attribute must load as verse 1 (mirrors
     * {@code MusicXmlReaderLenienceTest}).
     */
    @Test
    void lyricWithoutNumberLoadsAsVerseOne() throws Exception {
        var body =
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <lyric>\n" +
            "          <syllabic>single</syllabic>\n" +
            "          <text>oh</text>\n" +
            "        </lyric>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n";

        var song = parse(scoreWithMeasureBody(body));
        var note = noteAt(song, 0);

        assertLyricEquals(
            note.getLyricForVerse(FIRST_VERSE),
            new Lyric(FIRST_VERSE, "oh", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false),
            "number-less lyric"
        );
        assertThat(note.getLyrics()).as("single verse-1 lyric").hasSize(1);
    }

    /**
     * Legacy compound marker on load: a pre-#420 document marks a compound-word
     * boundary with a zero-width space (U+200B), not the current U+2011. The reader
     * must still strip it and set {@code compound=true}. The writer only ever emits
     * U+2011, so no round-trip can reach this branch — it needs a hand-authored file.
     */
    @Test
    void legacyZeroWidthSpaceMarkerLoadsAsCompound() throws Exception {
        var body =
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <lyric number=\"1\">\n" +
            "          <syllabic>begin</syllabic>\n" +
            "          <text>self" + LEGACY_ZERO_WIDTH_SPACE_MARKER + "</text>\n" +
            "        </lyric>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n";

        var song = parse(scoreWithMeasureBody(body));

        assertLyricEquals(
            noteAt(song, 0).getLyricForVerse(FIRST_VERSE),
            new Lyric(FIRST_VERSE, "self", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true),
            "legacy U+200B compound boundary"
        );
    }

    /**
     * Non-continuing syllable lenience: a trailing compound marker is only a
     * boundary on a BEGIN/MIDDLE syllable. On a SINGLE syllable the reader must
     * leave the marker in the text and keep {@code compound=false} (the false branch
     * of the {@code syllabicContinues} guard, which the writer never produces).
     */
    @Test
    void trailingMarkerOnSingleSyllableIsNotStripped() throws Exception {
        var body =
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <lyric number=\"1\">\n" +
            "          <syllabic>single</syllabic>\n" +
            "          <text>oh" + Lyric.COMPOUND_WORD_MARKER + "</text>\n" +
            "        </lyric>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n";

        var song = parse(scoreWithMeasureBody(body));

        assertLyricEquals(
            noteAt(song, 0).getLyricForVerse(FIRST_VERSE),
            new Lyric(FIRST_VERSE, "oh" + Lyric.COMPOUND_WORD_MARKER, Lyric.Extend.NONE,
                Lyric.Syllabic.SINGLE, false),
            "trailing marker on SINGLE syllable"
        );
    }

    /**
     * XML-escaping round-trip: ampersand and angle brackets in lyric text must be
     * escaped on write and restored on read. If the writer emitted them raw, the
     * document would be malformed and the reader's parser would reject it, failing
     * this round-trip.
     */
    @Test
    void specialCharactersInTextAreEscapedAndRoundTrip() throws Exception {
        var expected = new Lyric(FIRST_VERSE, "R&B <lo>", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var song = roundTrip(buildSong(line -> addNote(line, expected)));

        assertLyricEquals(
            noteAt(song, 0).getLyricForVerse(FIRST_VERSE), expected, "escaped special characters");
    }

    // -------------------------------------------------------------------------
    // Hand-authored document helper
    // -------------------------------------------------------------------------

    /**
     * Wraps {@code measureBody} in a minimal score-partwise document with a
     * new-system {@code <print>} (so the reader starts a line) and standard
     * attributes. Mirrors {@code MusicXmlReaderLenienceTest.scoreWithMeasureBody}.
     */
    private static String scoreWithMeasureBody(String measureBody) {
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            measureBody +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";
    }
}
