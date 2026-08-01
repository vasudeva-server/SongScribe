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
package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.io.musicxml.MusicXmlWriter;

/**
 * Reader parity for the post-load tuplet pass: one document, expressed once as legacy
 * {@code .mssw} and once as MusicXML, must survive loading with the same tuplets and lose
 * the same number of them.
 *
 * <p>The two formats take opposite branches of the pass. MusicXML states the ratio in
 * {@code <time-modification>}, so its tuplets arrive resolved and are judged by
 * {@code TupletValidator.validateStated}; {@code .mssw} has nowhere to record a ratio, so
 * its tuplets arrive unresolved and the ratio is derived from the beat. Different branches
 * reaching different verdicts for the same music would mean one file opening with a bracket
 * the other silently dropped.
 */
class TupletLoadParityTest extends UnitTest {

    private static final int FIRST_LINE_INDEX = 0;

    /** An undotted written value. */
    private static final int NO_DOTS = 0;

    // The valid span: three eighths as a 3:2 eighth-note triplet at the default quarter beat.
    private static final int TRIPLET_NOTE_COUNT = 3;
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int TRIPLET_ANCHOR_INDEX = 0;
    private static final int TRIPLET_END_INDEX = 2;

    // The invalid span: two quarters under a 5. Their 192 ticks do not divide by five, so
    // there is no written value the printed number could stand for, whether the ratio is
    // read from the file or derived.
    private static final int INVALID_NOTE_COUNT = 2;
    private static final int INVALID_GRADE = 5;
    private static final int INVALID_NORMAL_NOTES = 4;
    private static final int INVALID_ANCHOR_INDEX = 3;
    private static final int INVALID_END_INDEX = 4;

    /** Both spans are authored; exactly one of them is expected to survive either reader. */
    private static final int AUTHORED_TUPLET_COUNT = 2;
    private static final int EXPECTED_DROPPED = 1;

    /** A tuplet reduced to the facts both readers must agree on. */
    private record SurvivingTuplet(int anchorIndex, int endIndex, int grade) {}

    /**
     * The document under test: an eighth-note triplet that must survive, followed by a
     * quintuplet over two quarters that must not.
     */
    private static Song authoredSong() {
        var song = new Song();
        var line = song.getLine(FIRST_LINE_INDEX);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < TRIPLET_NOTE_COUNT; i++) {
                line.addElement(ElementType.QUAVER.newInstance());
            }

            for (var i = 0; i < INVALID_NOTE_COUNT; i++) {
                line.addElement(ElementType.CROTCHET.newInstance());
            }

            line.addTuplet(new Tuplet(
                line.getElement(TRIPLET_ANCHOR_INDEX), line.getElement(TRIPLET_END_INDEX),
                TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.QUAVER, NO_DOTS));

            line.addTuplet(new Tuplet(
                line.getElement(INVALID_ANCHOR_INDEX), line.getElement(INVALID_END_INDEX),
                INVALID_GRADE, INVALID_NORMAL_NOTES, ElementType.QUAVER, NO_DOTS));
        });

        return song;
    }

    private static List<SurvivingTuplet> survivingTuplets(Song song) {
        return song.getLine(FIRST_LINE_INDEX).findRangeElements(Tuplet.class).stream()
            .map(tuplet -> new SurvivingTuplet(
                tuplet.getAnchorElementIndex(), tuplet.getEndElementIndex(), tuplet.getGrade()))
            .toList();
    }

    private static Song loadThroughMusicXml(Song song) throws Exception {
        var writer = new StringWriter();
        var printWriter = new PrintWriter(writer);
        MusicXmlWriter.writeSong(song, DocumentFonts.defaultFonts(), printWriter);
        printWriter.flush();

        return MusicXmlReader.read(new InputSource(new StringReader(writer.toString()))).song();
    }

    private static Song loadThroughLegacyFormat(Song song, Path directory) throws Exception {
        var file = directory.resolve("parity.mssw").toFile();

        try (var printWriter = new PrintWriter(Files.newBufferedWriter(file.toPath()))) {
            SongIO.writeSong(song, DocumentFonts.defaultFonts(), printWriter);
        }

        var result = SongLoader.load(file);

        if (!(result instanceof SongLoadResult.Success success)) {
            throw new AssertionError("the legacy document failed to load: " + result);
        }

        return success.song();
    }

    /**
     * The MusicXML reader trusts the ratio the file states; the legacy reader has none to
     * trust and derives it. Both must reach the same verdict for the same music.
     */
    @Test
    void testBothReadersKeepAndDropTheSameTuplets(@TempDir Path tempDir) throws Exception {
        var fromMusicXml = survivingTuplets(loadThroughMusicXml(authoredSong()));
        var fromLegacy = survivingTuplets(loadThroughLegacyFormat(authoredSong(), tempDir));

        var expected = List.of(
            new SurvivingTuplet(TRIPLET_ANCHOR_INDEX, TRIPLET_END_INDEX, TRIPLET_GRADE));

        assertAll(
            () -> assertThat(fromMusicXml)
                .as("MusicXML must keep the triplet and drop the quintuplet")
                .isEqualTo(expected),
            () -> assertThat(fromLegacy)
                .as("the legacy reader must reach the same verdict")
                .isEqualTo(expected),
            () -> assertThat(AUTHORED_TUPLET_COUNT - fromMusicXml.size())
                .as("MusicXML drop count")
                .isEqualTo(EXPECTED_DROPPED),
            () -> assertThat(AUTHORED_TUPLET_COUNT - fromLegacy.size())
                .as("legacy drop count")
                .isEqualTo(EXPECTED_DROPPED)
        );
    }

    /**
     * The surviving tuplet must also be resolved by both routes — MusicXML from the stated
     * ratio, the legacy format from the convention — or playback would scale one document's
     * notes and not the other's.
     */
    @Test
    void testTheSurvivingTupletIsResolvedTheSameWayByBothReaders(@TempDir Path tempDir)
        throws Exception {
        var fromMusicXml = onlyTuplet(loadThroughMusicXml(authoredSong()));
        var fromLegacy = onlyTuplet(loadThroughLegacyFormat(authoredSong(), tempDir));

        assertAll(
            () -> assertThat(fromMusicXml.getNormalNotes())
                .as("MusicXML states 3:2")
                .isEqualTo(TRIPLET_NORMAL_NOTES),
            () -> assertThat(fromLegacy.getNormalNotes())
                .as("the derived ratio at a quarter beat is also 3:2")
                .isEqualTo(TRIPLET_NORMAL_NOTES),
            () -> assertThat(fromMusicXml.getNoteValue())
                .as("MusicXML states an eighth-note value")
                .isEqualTo(ElementType.QUAVER),
            () -> assertThat(fromLegacy.getNoteValue())
                .as("the derived value is S/N, an eighth note")
                .isEqualTo(ElementType.QUAVER)
        );
    }

    private static Tuplet onlyTuplet(Song song) {
        return assertSingleTuplet(song.getLine(FIRST_LINE_INDEX));
    }

    private static Tuplet assertSingleTuplet(Line line) {
        var tuplets = line.findRangeElements(Tuplet.class);

        if (tuplets.size() != 1) {
            throw new AssertionError("expected exactly one surviving tuplet, got " + tuplets.size());
        }

        return tuplets.getFirst();
    }
}
