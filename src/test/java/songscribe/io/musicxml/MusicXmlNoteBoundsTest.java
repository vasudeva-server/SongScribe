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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.FileExtensions;
import songscribe.UnitTest;
import songscribe.io.SongFileLoader;
import songscribe.io.SongLoadResult;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.openTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;

/**
 * A {@code <note>} whose per-note bounds fall outside what SongScribe can represent — more than
 * two augmentation dots, or a pitch off the {@code StaffPosition.MIN..MAX} grid — is
 * refused as an invalid document rather than silently clamped or crashing the mapper.
 */
class MusicXmlNoteBoundsTest extends UnitTest {

    /**
     * One out-of-bounds note and the value that put it out of bounds.
     *
     * @param description   the case, as the test's display name
     * @param noteBody      the {@code <note>} the document holds
     * @param offendingValue the value the refusal has to name, so that the reader of the message
     *                       can find the note the document was refused for. The wording around it
     *                       is the message's own business and is not pinned here
     */
    private record BoundsCase(String description, String noteBody, String offendingValue) {}

    private static final int VALID_OCTAVE = 4;

    private static final int OFF_GRID_OCTAVE = 20;

    private static final int TOO_MANY_DOTS = 3;

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundsCases")
    void testNoteOutsideItsBoundsIsInvalidFileFormat(BoundsCase testCase, @TempDir Path tempDir)
            throws IOException {
        var file = tempDir.resolve("bounds." + FileExtensions.MUSICXML);
        Files.writeString(
            file, scoreWithMeasureBody(testCase.noteBody()), StandardCharsets.UTF_8);

        var result = SongFileLoader.load(file.toFile());

        assertThat(result).isInstanceOfSatisfying(SongLoadResult.InvalidFileFormat.class,
            invalidFileFormat -> assertThat(invalidFileFormat.detail())
                .as("the refusal names the value the document was refused for")
                .contains(testCase.offendingValue()));
    }

    static Stream<BoundsCase> boundsCases() {
        return Stream.of(
            new BoundsCase(
                "a note with three augmentation dots is refused",
                note(VALID_OCTAVE, TOO_MANY_DOTS),
                Integer.toString(TOO_MANY_DOTS)),
            new BoundsCase(
                "a pitch spelling a staff position off the grid is refused",
                note(OFF_GRID_OCTAVE, 0),
                Integer.toString(OFF_GRID_OCTAVE))
        );
    }

    /** A {@code <note>} carrying {@code <step>C</step><octave>octave</octave>} and {@code dotCount} dots. */
    private static String note(int octave, int dotCount) {
        return
            "      " + openTag(MusicXmlTags.NOTE) + '\n' +
            "        " + openTag(MusicXmlTags.PITCH) + '\n' +
            "          " + element(MusicXmlTags.STEP, "C") + '\n' +
            "          " + element(MusicXmlTags.OCTAVE, Integer.toString(octave)) + '\n' +
            "        " + closeTag(MusicXmlTags.PITCH) + '\n' +
            "        " + element(MusicXmlTags.DURATION, Integer.toString(MusicXmlUnits.DIVISIONS)) + '\n' +
            "        " + element(MusicXmlTags.NOTE_TYPE, NoteTypeMapping.TYPE_QUARTER) + '\n' +
            "        <dot/>\n".repeat(dotCount) +
            "      " + closeTag(MusicXmlTags.NOTE) + '\n';
    }
}
