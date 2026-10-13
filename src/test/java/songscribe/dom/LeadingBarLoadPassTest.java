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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;

/**
 * {@link LeadingBarLoadPass#run}: a bar line that stands first in a freshly parsed song is
 * removed and reported on the line it stood on, with the key change behind it; the song's
 * terminal is never removed.
 */
class LeadingBarLoadPassTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;

    private static final LineRange ON_FIRST_LINE = new LineRange(FIRST_LINE, FIRST_LINE);
    private static final LineRange ON_SECOND_LINE = new LineRange(SECOND_LINE, SECOND_LINE);

    private static final List<ElementType> TWO_NOTES = List.of(ElementType.CROTCHET, ElementType.CROTCHET);

    /**
     * A song the pass loads, and what the pass must leave of it.
     *
     * @param description   the case, as the test's display name
     * @param song          builds the song, the way a parse leaves it
     * @param lineIndex     the line the first element of the song stands on
     * @param typesAfter    the types of that line's elements afterwards
     * @param movedKey      the key the line's own key must become, or {@code null} when the pass
     *                      leaves the line's own key alone
     * @param keyAfter      the key the line's elements sound in afterwards
     * @param reportedAt    the line of each report entry, in order of removal
     * @param reasonKey     the {@link Strings} key of each entry's description
     */
    private record LoadCase(
        String description,
        Supplier<Song> song,
        int lineIndex,
        List<ElementType> typesAfter,
        @Nullable Key movedKey,
        Key keyAfter,
        List<LineRange> reportedAt,
        String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadCases")
    void testEveryLeadingBarLineIsRemovedAndReportedOnItsLine(LoadCase testCase) {
        var song = testCase.song().get();
        var line = song.getLine(testCase.lineIndex());
        var keyBefore = line.getKey();
        var builder = new ChangeReport.Builder(song);

        song.withoutMutationTracking(() -> LeadingBarLoadPass.run(song, builder));

        assertThat(line.getElements().stream().map(StaffElement::getType)).containsExactlyElementsOf(testCase.typesAfter());
        assertThat(line.getRunningKey()).isEqualTo(testCase.keyAfter());
        assertThat(line.getKey()).isEqualTo(testCase.movedKey() == null ? keyBefore : testCase.movedKey());
        assertThat(builder.build().changes()).containsExactlyElementsOf(testCase.reportedAt().stream()
            .map(where -> new ReportedChange(
                Strings.REPORT_LEADING_BARS_REMOVED, Strings.get(testCase.reasonKey()), where))
            .toList());
    }

    static Stream<LoadCase> loadCases() {
        return Stream.of(
            new LoadCase("a song that starts with two bar lines loses both, one entry each",
                () -> compactSong(Key.NO_ACCIDENTALS, "| | 3 3"),
                FIRST_LINE, TWO_NOTES, null, Key.NO_ACCIDENTALS,
                List.of(ON_FIRST_LINE, ON_FIRST_LINE), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("a song that holds only its terminal keeps it and reports nothing",
                () -> buildSong(line -> {
                    line.setKey(Key.NO_ACCIDENTALS);
                    line.addElement(finalDoubleBarline());
                }),
                FIRST_LINE, List.of(ElementType.FINAL_DOUBLE_BARLINE), null, Key.NO_ACCIDENTALS,
                List.of(), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("an empty first line and a bar line starting the second: the entry is on the second",
                () -> compactSong(Key.NO_ACCIDENTALS, "", "| 3 3"),
                SECOND_LINE, TWO_NOTES, null, Key.NO_ACCIDENTALS,
                List.of(ON_SECOND_LINE), Strings.REPORT_REASON_LEADING_BAR),

            new LoadCase("a bar line with a key change behind it takes the key change along and moves its key onto the line",
                () -> compactSong(Key.NO_ACCIDENTALS, "| K:TWO_SHARPS 3 3"),
                FIRST_LINE, TWO_NOTES, Key.TWO_SHARPS, Key.TWO_SHARPS,
                List.of(ON_FIRST_LINE), Strings.REPORT_REASON_LEADING_BAR_KEY)
        );
    }
}
