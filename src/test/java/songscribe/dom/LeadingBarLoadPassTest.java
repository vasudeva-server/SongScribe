/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.loadCompactSong;
import static songscribe.dom.SongFactory.loadSong;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;

/**
 * {@link LeadingBarLoadPass#run}, as {@link SongLoad#run} runs it: a barline that stands first in a
 * freshly parsed song is removed and reported on the line it stood on, with the key change behind
 * it; the song's terminal is never removed.
 */
class LeadingBarLoadPassTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;

    private static final LineRange ON_FIRST_LINE = new LineRange(FIRST_LINE, FIRST_LINE);
    private static final LineRange ON_SECOND_LINE = new LineRange(SECOND_LINE, SECOND_LINE);

    /** The two notes and the final barline a case's last line keeps. */
    private static final List<ElementType> TWO_NOTES_AND_FINAL_BAR =
        List.of(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);

    /** Loads a case's song. */
    @FunctionalInterface
    private interface SongSource {
        SongLoad.LoadedSong<SongFactory.LoadBuild> load() throws StrandedKeyChangeException;
    }

    /**
     * A song the pass loads, and what the pass must leave of it.
     *
     * @param description   the case, as the test's display name
     * @param song          loads the song, the way a parse leaves it
     * @param lineIndex     the line the first element of the song stands on
     * @param typesAfter    the types of that line's elements afterwards
     * @param ownKeyAfter   the line's own key afterwards, {@code null} when it inherits
     * @param keyAfter      the key the line's elements sound in afterwards
     * @param reportedAt    the line of each report entry, in order of removal
     * @param reasonKey     the {@link Strings} key of each entry's description
     */
    private record LoadCase(
        String description,
        SongSource song,
        int lineIndex,
        List<ElementType> typesAfter,
        @Nullable Key ownKeyAfter,
        Key keyAfter,
        List<LineRange> reportedAt,
        String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadCases")
    void testEveryLeadingBarLineIsRemovedAndReportedOnItsLine(LoadCase testCase) throws StrandedKeyChangeException {
        var loaded = testCase.song().load();
        var line = loaded.song().getLine(testCase.lineIndex());

        assertThat(line.getElements().stream().map(StaffElement::getType)).containsExactlyElementsOf(testCase.typesAfter());
        assertThat(line.getRunningKey()).isEqualTo(testCase.keyAfter());
        assertThat(line.getKey()).isEqualTo(testCase.ownKeyAfter());
        assertThat(loaded.report().changes()).containsExactlyElementsOf(testCase.reportedAt().stream()
            .map(where -> new ReportedChange(
                ChangeHeading.LEADING_BARS_REMOVED, Strings.get(testCase.reasonKey()), where))
            .toList());
    }

    static Stream<LoadCase> loadCases() {
        return Stream.of(
            new LoadCase("a song that starts with two barlines loses both, one entry for the line",
                () -> loadCompactSong(Key.NO_ACCIDENTALS, "| | 3 3 F"),
                FIRST_LINE, TWO_NOTES_AND_FINAL_BAR, Key.NO_ACCIDENTALS, Key.NO_ACCIDENTALS,
                List.of(ON_FIRST_LINE), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("two barlines with a key change behind the second: the key change's key moves onto the line",
                () -> loadCompactSong(Key.NO_ACCIDENTALS, "| | K:TWO_SHARPS 3 3 F"),
                FIRST_LINE, TWO_NOTES_AND_FINAL_BAR, Key.TWO_SHARPS, Key.TWO_SHARPS,
                List.of(ON_FIRST_LINE), Strings.REPORT_REASON_LEADING_BAR_KEY),

            new LoadCase("a first line of only barlines and a barline starting the second: one entry per line",
                () -> loadCompactSong(Key.NO_ACCIDENTALS, "| |", "| 3 3 F"),
                SECOND_LINE, TWO_NOTES_AND_FINAL_BAR, null, Key.NO_ACCIDENTALS,
                List.of(ON_FIRST_LINE, ON_SECOND_LINE), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("a song that holds only its terminal keeps it and reports nothing",
                () -> loadSong(line -> {
                    line.setKey(Key.NO_ACCIDENTALS);
                    line.addElement(finalDoubleBarline());
                }),
                FIRST_LINE, List.of(ElementType.FINAL_DOUBLE_BARLINE), Key.NO_ACCIDENTALS, Key.NO_ACCIDENTALS,
                List.of(), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("an empty first line and a barline starting the second: the entry is on the second",
                () -> loadCompactSong(Key.NO_ACCIDENTALS, "", "| 3 3 F"),
                SECOND_LINE, TWO_NOTES_AND_FINAL_BAR, null, Key.NO_ACCIDENTALS,
                List.of(ON_SECOND_LINE), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("a barline with a key change behind it takes the key change along and moves its key onto the line",
                () -> loadCompactSong(Key.NO_ACCIDENTALS, "| K:TWO_SHARPS 3 3 F"),
                FIRST_LINE, TWO_NOTES_AND_FINAL_BAR, Key.TWO_SHARPS, Key.TWO_SHARPS,
                List.of(ON_FIRST_LINE), Strings.REPORT_REASON_LEADING_BAR_KEY)
        );
    }
}
