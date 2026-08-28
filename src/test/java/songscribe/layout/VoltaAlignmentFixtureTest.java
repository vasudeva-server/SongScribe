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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElement;
import songscribe.font.DocumentFonts;
import songscribe.io.SongFileWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * Generates {@code src/test/resources/fixtures/volta-alignments.musicxml}, one line per way a
 * volta bracket can be aligned. Running this class rewrites the fixture; the fixture is then
 * opened in the application and looked at, because where a bracket's arms land against the
 * barline beside them is a judgement only a person makes.
 *
 * <p>{@code EndingBracketGeometry} positions a bracket from three independent inputs — what the
 * bracket opens on, what repeat splits its two halves, and what it closes on — so the lines
 * below vary exactly one of the three at a time, and each line writes its own case name under
 * its first note.
 *
 * <p>This is a generator, not a check: it asserts that the file was written and nothing about
 * where any bracket landed.
 */
class VoltaAlignmentFixtureTest extends UnitTest {

    /** Where the generated fixture lands, relative to the project directory the tests run in. */
    private static final Path FIXTURE_PATH =
        Path.of("src", "test", "resources", "fixtures", "volta-alignments.musicxml");

    private static final String SONG_TITLE = "Volta bracket alignments";

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

    /** One line of the fixture: the case name written under its first note, and the line itself. */
    private record VoltaCase(String label, SongFactory.LineBuilder builder) {}

    @Test
    void testWritesTheVoltaAlignmentFixture() throws IOException {
        var builders = voltaCases().stream()
            .map(VoltaCase::builder)
            .toArray(SongFactory.LineBuilder[]::new);
        var song = SongFactory.buildSong(builders);

        song.withoutMutationTracking(() -> song.setMetadata(song.getMetadata().withTitle(SONG_TITLE)));

        var fonts = DocumentFonts.defaultFonts();

        Files.createDirectories(FIXTURE_PATH.getParent());

        // The save path's own entry point, so the fixture is a song saved the way the
        // application saves one rather than one assembled by an imitation of it.
        var written = SongFileWriter.write(song, fonts, FIXTURE_PATH.toFile());

        assertThat(written).as("the fixture was written without error").isTrue();
        assertThat(FIXTURE_PATH).exists();
        assertThat(Files.size(FIXTURE_PATH)).as("fixture size in bytes").isPositive();
    }

    /**
     * The lines of the fixture, in the order they appear in the song. The line closing on a
     * {@code FINAL_DOUBLE_BARLINE} is last because that barline may only end the last line.
     */
    private static List<VoltaCase> voltaCases() {
        var notes = new NoteRun();

        return List.of(
            // What the bracket opens on: the element behind the anchor, when that element is a
            // bar or a repeat, otherwise the anchor note itself.
            standardCase(notes, "open=SINGLE", singleBarline(), repeatRight(), singleBarline()),
            standardCase(notes, "open=DOUBLE", doubleBarline(), repeatRight(), singleBarline()),
            standardCase(notes, "open=REPEAT_LEFT", repeatLeft(), repeatRight(), singleBarline()),
            standardCase(notes, "open=REPEAT_RIGHT", repeatRight(), repeatRight(), singleBarline()),
            standardCase(notes, "open=RLR", repeatLeftRight(), repeatRight(), singleBarline()),
            noteOpeningCase(notes),

            // What the bracket closes on: the end element, or the bar or repeat immediately
            // after it when the end element is neither.
            standardCase(notes, "close=SINGLE", singleBarline(), repeatRight(), singleBarline()),
            standardCase(notes, "close=DOUBLE", singleBarline(), repeatRight(), doubleBarline()),
            standardCase(notes, "close=REPEAT_LEFT", singleBarline(), repeatRight(), repeatLeft()),
            // These two close on a right repeat, which only a REPEAT_LEFT_RIGHT split may do:
            // a REPEAT_RIGHT split already closes its section, so it cannot also be closed on.
            // Ending.isValidEnd states the pairing, and the editor refuses the other one.
            standardCase(notes, "close=REPEAT_RIGHT", singleBarline(), repeatLeftRight(), repeatRight()),
            standardCase(notes, "close=RLR", singleBarline(), repeatLeftRight(), repeatLeftRight()),
            noteClosingCase(notes),
            noteThenBarClosingCase(notes),

            // What splits the two halves.
            standardCase(notes, "split=RLR", singleBarline(), repeatLeftRight(), repeatRight()),

            // An anchor that is itself a repeat, with a note behind it.
            repeatAnchorCase(notes),

            standardCase(notes, "close=FINAL_DOUBLE", singleBarline(), repeatRight(), finalDoubleBarline())
        );
    }

    /**
     * The fixture's standard shape — opening bar, two notes, the split repeat, two notes,
     * closing bar — with the ending anchored on the first note and ended on the closing bar.
     * The element behind the anchor is a bar or a repeat, so the bracket opens on that.
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
     * The anchor is itself a repeat, with a note behind it: the bracket opens on the anchor
     * without pulling back, and the split is the repeat further along the line.
     */
    private static VoltaCase repeatAnchorCase(NoteRun notes) {
        var anchor = repeatLeftRight();
        var closing = singleBarline();

        return endingCase("anchor=RLR", anchor, closing,
            notes.next(), notes.next(), anchor, notes.next(), notes.next(),
            repeatRight(), notes.next(), notes.next(), closing);
    }

    /**
     * A line of {@code elements} carrying one ending over [{@code anchor}, {@code end}], with
     * {@code label} written under the line's first note so the case names itself on the page.
     */
    private static VoltaCase endingCase(
        String label, StaffElement anchor, StaffElement end, StaffElement... elements
    ) {
        return new VoltaCase(label, line -> {
            for (var element : elements) {
                line.addElement(element);
            }

            labelFirstNote(line, label);
            line.addSpan(new Ending(anchor, end));
        });
    }

    /**
     * Writes {@code label} as a syllable under the first note of {@code line}, so a reader who
     * opens the fixture reads which case a line is rather than counting barlines to work it out.
     */
    private static void labelFirstNote(Line line, String label) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            if (element.getType().isDuration()) {
                element.setLyricForVerse(
                    Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, label, Lyric.Extend.NONE);

                return;
            }
        }
    }
}
