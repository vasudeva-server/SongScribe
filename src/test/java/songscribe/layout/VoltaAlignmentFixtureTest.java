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

package songscribe.layout;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.SongFactory;
import songscribe.dom.SongMetadata;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.font.DocumentFonts;
import songscribe.io.musicxml.MusicXmlWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * Checks that the song built here — one line per way a volta bracket can be aligned — still
 * writes as {@code src/test/resources/fixtures/volta-alignments.musicxml}. What it pins is the
 * markup {@code MusicXmlWriter} emits for an ending, not where a bracket is drawn: the
 * fixture holds no coordinates a bracket's geometry decides. Bracket geometry is pinned by
 * {@code EndingBracketGeometryTest}. The fixture is the approved output, opened in the
 * application and looked at, and this test holds it against later changes to the writer. The
 * {@code <software>} version string is left out of the comparison, so a version change does not
 * fail it.
 *
 * <p>The ending's two ends are independent inputs — what the bracket opens on, and what it
 * closes on — so the lines below vary exactly one of the two at a
 * time, and each line writes its own case name under its first note. An ending's split is always
 * a right repeat, and that repeat closes the section the ending sits in. Every line after the
 * first therefore starts with a left repeat, which opens the section its own split closes: the
 * split before it, on the line above, closed the last one.
 *
 * <p>When a change to what a bracket emits is intended, regenerate the fixture with
 * {@code ./scripts/test.sh --generate-fixtures VoltaAlignmentFixtureTest}, which sets
 * {@value #UPDATE_FIXTURES_PROPERTY} in the test JVM, open the result in the application, and commit it once it looks
 * right. Nothing else writes the fixture.
 */
class VoltaAlignmentFixtureTest extends UnitTest {

    /** The checked-in fixture, relative to the project directory the tests run in. */
    private static final Path FIXTURE_PATH =
        Path.of("src", "test", "resources", "fixtures", "volta-alignments.musicxml");

    /** Set to {@code true} to rewrite the fixture from the current writer instead of checking it. */
    private static final String UPDATE_FIXTURES_PROPERTY = "songscribe.updateFixtures";

    private static final String SONG_TITLE = "Volta bracket alignments";

    /** The writer's {@code <software>} element, whose text is the application version. */
    private static final Pattern SOFTWARE_ELEMENT = Pattern.compile("<software>[^<]*</software>");

    /**
     * Hands out the fixture's notes, walking one staff position further along a repeating
     * melodic figure each time, so that two lines built to the same shape are still told apart
     * at a glance.
     */
    private static final class NoteRun {

        /**
         * The staff positions the walk visits before starting over. The lattice descends in
         * pitch as the position grows and staff position 6 is C4, so this figure climbs.
         */
        private static final List<Integer> STAFF_POSITIONS = List.of(6, 5, 4, 3, 2);

        private int index = 0;

        StaffElement next() {
            var staffPosition = STAFF_POSITIONS.get(index % STAFF_POSITIONS.size());
            index++;

            return note(staffPosition);
        }
    }

    /**
     * One line of the fixture.
     *
     * @param label    the case name, written under the line's first note
     * @param anchor   the element the ending is anchored on; one of {@code elements}
     * @param end      the element the ending ends on; one of {@code elements}
     * @param elements the line's elements, in order
     */
    private record VoltaCase(String label, StaffElement anchor, StaffElement end, List<StaffElement> elements) {}

    @Test
    void testWrittenVoltaAlignmentsMatchTheCheckedInFixture() throws IOException {
        var written = writeVoltaAlignments();

        if (Boolean.getBoolean(UPDATE_FIXTURES_PROPERTY)) {
            Files.createDirectories(FIXTURE_PATH.getParent());
            Files.writeString(FIXTURE_PATH, written, StandardCharsets.UTF_8);
            return;
        }

        var expected = Files.readString(FIXTURE_PATH, StandardCharsets.UTF_8);

        assertThat(withoutSoftwareVersion(written))
            .as("the volta alignments as written now, against %s — if the change is intended, "
                + "regenerate with ./scripts/test.sh --generate-fixtures VoltaAlignmentFixtureTest "
                + "and look at the result in the application",
                FIXTURE_PATH)
            .isEqualTo(withoutSoftwareVersion(expected));
    }

    /** {@code xml} with the {@code <software>} element's text blanked, whatever the version. */
    private static String withoutSoftwareVersion(String xml) {
        return SOFTWARE_ELEMENT.matcher(xml).replaceAll("<software></software>");
    }

    /** The volta-alignment song as the application saves it, dated from the fixed clock. */
    private static String writeVoltaAlignments() {
        var cases = voltaCases();
        var builders = new SongFactory.LineBuilder[cases.size()];

        for (var i = 0; i < builders.length; i++) {
            builders[i] = lineBuilderOf(cases.get(i), i == 0);
        }

        var song = SongFactory.buildSong(builders);

        // An ending is judged against the song's repeat sections, so it is added once every
        // line is in the song.
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < cases.size(); i++) {
                var line = song.getLine(i);
                var voltaCase = cases.get(i);
                var range = new ElementRange(
                    line.getElementIndex(voltaCase.anchor()), line.getElementIndex(voltaCase.end()));

                endingOver(line, range).addTo();
            }
        });

        var metadata = song.getMetadata();
        song.withoutMutationTracking(() -> song.setMetadata(new SongMetadata(
            SONG_TITLE,
            metadata.number(),
            metadata.place(),
            metadata.date(),
            metadata.composer(),
            metadata.lyricist(),
            metadata.lyricsSource(),
            metadata.arrangement(),
            metadata.unofficialTranslation(),
            metadata.subtitle(),
            metadata.wordsDate()
        )));

        var fonts = DocumentFonts.defaultFonts();
        var out = new StringWriter();

        // The save path's own writer with the save path's own headless layout, so the song is
        // written the way the application saves one rather than by an imitation of it.
        try (var pw = new PrintWriter(out)) {
            MusicXmlWriter.writeSong(song, fonts, LineLayoutProvider.headless(song, fonts), pw, FIXED_CLOCK);
        }

        return out.toString();
    }

    /**
     * The lines of the fixture, in the order they appear in the song. The line closing on a
     * {@code FINAL_DOUBLE_BARLINE} is last because that barline may only end the last line. The
     * line whose anchor is the line's first element is first, because every other line starts
     * with a left repeat, and a note anchor cannot follow one directly.
     */
    private static List<VoltaCase> voltaCases() {
        var notes = new NoteRun();

        return List.of(
            // What the bracket opens on: the single barline behind the anchor, otherwise the
            // anchor itself. A note or rest anchor behind any other bar is not an ending.
            noteOpeningCase(notes),
            // The standard shape with a single barline at both ends, which is also the case for
            // what the bracket closes on being a single barline.
            standardCase(notes, "open=SINGLE close=SINGLE", singleBarline(), repeatRight(), singleBarline()),
            noteAfterNoteOpeningCase(notes),
            restOpeningCase(notes),
            restAfterNoteOpeningCase(notes),

            // What the bracket closes on: the end element, or the bar or repeat immediately
            // after it when the end element is neither. A right repeat cannot close it: as the
            // split it already closes the section, so it cannot also be the bracket's boundary.
            standardCase(notes, "close=DOUBLE", singleBarline(), repeatRight(), doubleBarline()),
            standardCase(notes, "close=REPEAT_LEFT", singleBarline(), repeatRight(), repeatLeft()),
            noteClosingCase(notes),
            noteThenBarClosingCase(notes),

            // An anchor that is itself a single barline, with a note behind it.
            barAnchorCase(notes),

            standardCase(notes, "close=FINAL_DOUBLE", singleBarline(), repeatRight(), finalDoubleBarline())
        );
    }

    /**
     * The builder of one fixture line: {@code voltaCase}'s elements, a left repeat first when the
     * line is not the song's first, and the case's label under its first note.
     */
    private static SongFactory.LineBuilder lineBuilderOf(VoltaCase voltaCase, boolean isFirstLine) {
        return line -> {
            if (!isFirstLine) {
                line.addElement(repeatLeft());
            }

            for (var element : voltaCase.elements()) {
                line.addElement(element);
            }

            labelFirstNote(line, voltaCase.label());
        };
    }

    /**
     * The fixture's standard shape — opening bar, two notes, the split repeat, two notes,
     * closing bar — with the ending anchored on the first note and ended on the closing bar.
     * The bracket opens on the element behind the anchor when that element is a single
     * barline, and on the anchor note otherwise.
     */
    private static VoltaCase standardCase(
        NoteRun notes, String label,
        StaffElement opening, StaffElement split, StaffElement closing
    ) {
        var anchor = notes.next();

        return endingCase(label, anchor, closing,
            opening, anchor, notes.next(), split, notes.next(), notes.next(), closing);
    }

    /**
     * The bracket opens on a note: the anchor is the line's first element, so there is nothing
     * behind it for the bracket to pull back to.
     */
    private static VoltaCase noteOpeningCase(NoteRun notes) {
        var anchor = notes.next();
        var closing = singleBarline();

        return endingCase("open=NOTE", anchor, closing,
            anchor, notes.next(), repeatRight(), notes.next(), notes.next(), closing);
    }

    /**
     * The bracket opens on a note with another note behind it: only a single barline pulls
     * the bracket back, so it opens on the anchor and not on the note in front of it.
     */
    private static VoltaCase noteAfterNoteOpeningCase(NoteRun notes) {
        var before = notes.next();
        var anchor = notes.next();
        var closing = singleBarline();

        return endingCase("open=NOTE_AFTER_NOTE", anchor, closing,
            before, anchor, notes.next(), repeatRight(), notes.next(), notes.next(), closing);
    }

    /**
     * The bracket opens on the single barline behind a rest anchor, as it does behind a note
     * anchor.
     */
    private static VoltaCase restOpeningCase(NoteRun notes) {
        var anchor = crotchetRest();
        var closing = singleBarline();

        return endingCase("open=REST", anchor, closing,
            singleBarline(), anchor, notes.next(), repeatRight(), notes.next(), notes.next(), closing);
    }

    /**
     * The bracket opens on a rest with a note behind it: it opens on the rest, not on the note
     * in front of it.
     */
    private static VoltaCase restAfterNoteOpeningCase(NoteRun notes) {
        var before = notes.next();
        var anchor = crotchetRest();
        var closing = singleBarline();

        return endingCase("open=REST_AFTER_NOTE", anchor, closing,
            before, anchor, notes.next(), repeatRight(), notes.next(), notes.next(), closing);
    }

    /**
     * The bracket closes on a note: another note follows the end element rather than a bar or a
     * repeat, so there is nothing ahead of it for the bracket to run on to.
     */
    private static VoltaCase noteClosingCase(NoteRun notes) {
        var opening = singleBarline();
        var anchor = notes.next();
        var secondNote = notes.next();
        var thirdNote = notes.next();
        var end = notes.next();

        return endingCase("close=NOTE", anchor, end,
            opening, anchor, secondNote, repeatRight(), thirdNote, end, notes.next(), singleBarline());
    }

    /**
     * The bracket closes on a note that a barline stands immediately after, so the bracket runs
     * on past the note and closes on that barline instead. This is how a volta ends in most real
     * music, the end element being the last note of the ending's material.
     */
    private static VoltaCase noteThenBarClosingCase(NoteRun notes) {
        var opening = singleBarline();
        var anchor = notes.next();
        var secondNote = notes.next();
        var thirdNote = notes.next();
        var end = notes.next();

        return endingCase("close=NOTE_THEN_BAR", anchor, end,
            opening, anchor, secondNote, repeatRight(), thirdNote, end, singleBarline());
    }

    /**
     * The anchor is itself a single barline, with a note behind it: the bracket opens on the
     * anchor without pulling back.
     */
    private static VoltaCase barAnchorCase(NoteRun notes) {
        var anchor = singleBarline();
        var closing = singleBarline();

        return endingCase("anchor=SINGLE", anchor, closing,
            notes.next(), notes.next(), anchor, notes.next(), notes.next(),
            repeatRight(), notes.next(), notes.next(), closing);
    }

    /**
     * A case of {@code elements} carrying one ending over [{@code anchor}, {@code end}], with
     * {@code label} written under the line's first note so the case names itself on the page.
     */
    private static VoltaCase endingCase(
        String label, StaffElement anchor, StaffElement end, StaffElement... elements
    ) {
        return new VoltaCase(label, anchor, end, List.of(elements));
    }

    /**
     * Writes {@code label} as a syllable under the first note of {@code line}, so a reader who
     * opens the fixture reads which case a line is rather than counting barlines to work it out.
     */
    private static void labelFirstNote(Line line, String label) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            var type = element.getType();

            if (type.isDuration() && !type.isRest()) {
                element.putLyric(
                    new Lyric(Song.Script.LATIN, label, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

                return;
            }
        }
    }
}
