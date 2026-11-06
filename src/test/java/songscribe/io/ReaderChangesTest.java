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

package songscribe.io;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ChangeHeading;
import songscribe.dom.LineRange;
import songscribe.dom.PendingMarks;
import songscribe.dom.ReportedChange;
import songscribe.dom.Song;
import songscribe.dom.SongFactory.LoadBuild;
import songscribe.dom.SongLoad;
import songscribe.dom.StrandedKeyChangeException;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.addLines;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * {@link ReaderChanges#reportTo}: the entries it reports, and their order.
 */
class ReaderChangesTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;

    private static final ReportedChange ACCIDENTALS_ENTRY = new ReportedChange(
        ChangeHeading.ACCIDENTALS_CONVERTED, Strings.get(Strings.REPORT_ACCIDENTALS_DETAIL), null);

    /** What a case records, on a song of three lines. */
    @FunctionalInterface
    private interface Recording {
        void record(ReaderChanges changes, Song song);
    }

    private record ReportCase(String description, Recording recording, List<ReportedChange> expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static Stream<ReportCase> reportCases() {
        return Stream.of(
            new ReportCase(
                "the accidental entry comes first, then each line in the order of its first lost lyric",
                (changes, song) -> {
                    changes.lyricDropped(song.getLine(THIRD_LINE));
                    changes.accidentalConverted();
                    changes.lyricDropped(song.getLine(FIRST_LINE));
                },
                List.of(ACCIDENTALS_ENTRY, lyricEntry(THIRD_LINE), lyricEntry(FIRST_LINE))),
            new ReportCase(
                "two lost lyrics on one line give one entry",
                (changes, song) -> {
                    changes.lyricDropped(song.getLine(SECOND_LINE));
                    changes.lyricDropped(song.getLine(SECOND_LINE));
                },
                List.of(lyricEntry(SECOND_LINE))),
            new ReportCase(
                "no recorded change gives no entry",
                (_, _) -> {
                },
                List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reportCases")
    void testReportedEntriesFollowTheFixedOrder(ReportCase testCase) throws StrandedKeyChangeException {
        var loaded = SongLoad.<LoadBuild, RuntimeException, RuntimeException>run(song -> {
            addLines(song,
                line -> line.addElement(crotchet()),
                line -> line.addElement(crotchet()),
                line -> line.addElement(crotchet()));

            var changes = new ReaderChanges();
            testCase.recording().record(changes, song);
            changes.reportTo(song);

            return new LoadBuild(new PendingMarks(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        });

        assertThat(loaded.report().changes()).containsExactlyElementsOf(testCase.expected());
    }

    private static ReportedChange lyricEntry(int lineIndex) {
        return new ReportedChange(
            ChangeHeading.LYRICS_REMOVED,
            Strings.get(Strings.REPORT_LYRICS_DETAIL),
            new LineRange(lineIndex, lineIndex));
    }
}
