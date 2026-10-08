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

import java.awt.Font;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.SongFactory.LineBuilder;
import songscribe.dom.Ss;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.SongFactory.setLineWidth;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Which lines a key edit is measured over, and that the measurement agrees with the layout the
 * edit leaves.
 *
 * <p>A change to a line's own key is measured at every line width in a range over two songs that
 * differ only in whether their dense second line inherits the edited key or has one of its own. A
 * second line that inherits is re-keyed by the edit and so is measured; one with a key of its own
 * is not. So an edit the inheriting song accepts is one the keyed song accepts too, and at some
 * width the inheriting song's second line is what refuses it.
 *
 * <p>A mid-line key change inserted or swapped is measured at every width against the song the
 * edit leaves, each of whose lines is solved as the committed layout solves it: the query accepts
 * exactly where every line of that song fits. Inserting a key change behind the barline it needs
 * and swapping an existing key change for it leave the same song, so the swap agreeing with that
 * song is what shows its column is measured in place of the old one rather than added beside it.
 */
class KeyEditFitCalculatorTest extends UnitTest {

    /** The key the first line establishes before the edit. */
    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** The key the edit establishes: the widest there is, so the header it puts on an inheriting line is too. */
    private static final Key EDITED_KEY = Key.SEVEN_SHARPS;

    /** The key the dense second line establishes in the song where it does not inherit. */
    private static final Key FOLLOWER_OWN_KEY = Key.ONE_FLAT;

    /** The key the swapped key change establishes before the edit: narrower than {@link #EDITED_KEY}. */
    private static final Key SWAPPED_OUT_KEY = Key.ONE_FLAT;

    /** How many notes the second line holds: far more than the first, so it is the line that overflows. */
    private static final int DENSE_NOTE_COUNT = 24;

    /** Between the first line's two notes, where an inserted key change needs a barline in front of it. */
    private static final int INSERTION_INDEX = 1;

    /** Where {@link songscribe.dom.SongFactory#notesAroundKeyChange} puts its key change. */
    private static final int KEY_CHANGE_INDEX = 2;

    private static final double NARROWEST_LINE_WIDTH_SS = 5.0;
    private static final double WIDEST_LINE_WIDTH_SS = 200.0;
    private static final double LINE_WIDTH_STEP_SS = 1.0;

    private static final int LYRICS_FONT_SIZE = 12;

    /** The syllable spacing the metrics carry; the notes here hold no lyrics, so it measures nothing. */
    private static final double SYLLABLE_GAP_SS = 0.25;

    /** A first line holding one note. */
    private static final LineBuilder ONE_NOTE = line -> line.addElement(crotchet());

    /** A first line holding two notes and nothing between them. */
    private static final LineBuilder TWO_NOTES = line -> {
        line.addElement(crotchet());
        line.addElement(crotchet());
    };

    /**
     * The line-key edit measured at one line width, over both songs.
     *
     * @param lineWidthSs            the line width measured at
     * @param inheritingFollowerFits whether the edit fits when the second line inherits
     * @param keyedFollowerFits      whether it fits when the second line has a key of its own
     */
    private record FitsAt(double lineWidthSs, boolean inheritingFollowerFits, boolean keyedFollowerFits) {}

    /**
     * One mid-line key edit, and the first line it is made on.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param firstLine   the first line's elements before the edit
     * @param editFits    the query measuring the edit on that line
     */
    private record MidLineEditCase(
        String description, LineBuilder firstLine, BiPredicate<Line, LyricRenderMetrics> editFits) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * A mid-line key edit measured at one line width, against the song it leaves.
     *
     * @param lineWidthSs  the line width measured at
     * @param editFits     whether the query accepts the edit
     * @param leftSongFits whether every line of the song the edit leaves fits
     */
    private record VerdictAt(double lineWidthSs, boolean editFits, boolean leftSongFits) {}

    private List<FitsAt> measurements;

    @BeforeAll
    void measureTheLineKeyEditAtEveryLineWidth() {
        var inheritingFollower = songWithDenseSecondLine(ONE_NOTE, null);
        var keyedFollower = songWithDenseSecondLine(ONE_NOTE, FOLLOWER_OWN_KEY);

        measurements = lineWidths()
            .mapToObj(lineWidthSs -> new FitsAt(
                lineWidthSs,
                lineKeyChangeFitsAt(inheritingFollower, lineWidthSs),
                lineKeyChangeFitsAt(keyedFollower, lineWidthSs)))
            .toList();
    }

    /**
     * A first line in {@link #DOCUMENT_KEY} holding what {@code firstLine} adds, and a second line
     * holding {@link #DENSE_NOTE_COUNT} notes.
     *
     * @param firstLine      adds the first line's elements
     * @param followerOwnKey the key the second line establishes, or null to leave it inheriting
     */
    private static Song songWithDenseSecondLine(LineBuilder firstLine, @Nullable Key followerOwnKey) {
        return buildSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                firstLine.build(line);
            },
            line -> {
                if (followerOwnKey != null) {
                    line.setKey(followerOwnKey);
                }

                for (var noteCount = 0; noteCount < DENSE_NOTE_COUNT; noteCount++) {
                    line.addElement(crotchet());
                }
            });
    }

    private static DoubleStream lineWidths() {
        return DoubleStream.iterate(
            NARROWEST_LINE_WIDTH_SS,
            lineWidthSs -> lineWidthSs <= WIDEST_LINE_WIDTH_SS,
            lineWidthSs -> lineWidthSs + LINE_WIDTH_STEP_SS);
    }

    private static boolean lineKeyChangeFitsAt(Song song, double lineWidthSs) {
        setLineWidth(song, lineWidthSs);

        return KeyEditFitCalculator.lineKeyChangeFits(song.getLine(0), EDITED_KEY, metrics());
    }

    /** Whether every line of {@code song} fits, as the committed layout solves it. */
    private static boolean everyLineFits(Song song) {
        var staffRightMarginSs = song.getLineWidthSs().value();

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            if (!LyricEditFitCalculator.lineFits(song.getLine(lineIndex), metrics(), staffRightMarginSs)) {
                return false;
            }
        }

        return true;
    }

    private static LyricRenderMetrics metrics() {
        var font = new Font(Font.SANS_SERIF, Font.PLAIN, LYRICS_FONT_SIZE);

        return new LyricRenderMetrics(font, font, SYLLABLE_GAP_SS, SYLLABLE_GAP_SS, 0.0);
    }

    static Stream<MidLineEditCase> midLineEditCases() {
        return Stream.of(
            new MidLineEditCase(
                "a key change inserted between two notes, behind the barline it needs",
                TWO_NOTES,
                (line, lyricRenderMetrics) -> KeyEditFitCalculator.midLineKeyChangeInsertionFits(
                    line, INSERTION_INDEX, EDITED_KEY, lyricRenderMetrics)),
            new MidLineEditCase(
                "a key change behind a barline swapped for one establishing a wider key",
                line -> notesAroundKeyChange(line, SWAPPED_OUT_KEY),
                (line, lyricRenderMetrics) -> KeyEditFitCalculator.midLineKeyChangeSwapFits(
                    line, KEY_CHANGE_INDEX, EDITED_KEY, lyricRenderMetrics)));
    }

    @Test
    void testAnEditThatFitsOverAnInheritingLineFitsWhenThatLineHasAKeyOfItsOwn() {
        assertThat(measurements).allSatisfy(fitsAt ->
            assertThat(!fitsAt.inheritingFollowerFits() || fitsAt.keyedFollowerFits())
                .as("at a line width of %s ss", fitsAt.lineWidthSs())
                .isTrue());
    }

    @Test
    void testALineInheritingTheEditedKeyIsMeasuredAndCanRefuseTheEdit() {
        assertThat(measurements).anySatisfy(fitsAt -> {
            assertThat(fitsAt.keyedFollowerFits()).isTrue();
            assertThat(fitsAt.inheritingFollowerFits()).isFalse();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("midLineEditCases")
    void testAMidLineKeyEditIsAcceptedExactlyWhereTheSongItLeavesFits(MidLineEditCase testCase) {
        var songBefore = songWithDenseSecondLine(testCase.firstLine(), null);
        var songLeft = songWithDenseSecondLine(line -> notesAroundKeyChange(line, EDITED_KEY), null);

        var verdicts = lineWidths()
            .mapToObj(lineWidthSs -> {
                setLineWidth(songBefore, lineWidthSs);
                setLineWidth(songLeft, lineWidthSs);

                return new VerdictAt(
                    lineWidthSs,
                    testCase.editFits().test(songBefore.getLine(0), metrics()),
                    everyLineFits(songLeft));
            })
            .toList();

        assertThat(verdicts).allSatisfy(verdict ->
            assertThat(verdict.editFits())
                .as("at a line width of %s ss", verdict.lineWidthSs())
                .isEqualTo(verdict.leftSongFits()));
        assertThat(verdicts)
            .as("the widths measured include one where the edit overflows and one where it fits")
            .anySatisfy(verdict -> assertThat(verdict.leftSongFits()).isFalse())
            .anySatisfy(verdict -> assertThat(verdict.leftSongFits()).isTrue());
    }
}
