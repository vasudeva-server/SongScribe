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

package songscribe.ui.component;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.ChangeReport;
import songscribe.dom.ChangeHeading;
import songscribe.dom.ReportedChange;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElementFactory;
import songscribe.lint.LintReport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReportStatus#of}: a lint finding outranks a change, and a change outranks nothing.
 */
class ReportStatusTest extends UnitTest {

    private static final ChangeReport ONE_CHANGE = new ChangeReport(
        List.of(new ReportedChange(ChangeHeading.BEAMS_REMOVED, "", null)));

    private static final LintReport ONE_FINDING = lintedReport(line -> line.addElement(StaffElementFactory.singleBarline()));

    private record StatusCase(String description, LintReport lint, ChangeReport changes, ReportStatus expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusCases")
    void testStatusNamesTheMostUrgentKindOfEntry(StatusCase testCase) {
        assertThat(ReportStatus.of(testCase.lint(), testCase.changes())).isEqualTo(testCase.expected());
    }

    static Stream<StatusCase> statusCases() {
        return Stream.of(
            new StatusCase("no finding and no change is empty",
                LintReport.EMPTY, ChangeReport.EMPTY, ReportStatus.EMPTY),
            new StatusCase("a change with no finding is changes",
                LintReport.EMPTY, ONE_CHANGE, ReportStatus.CHANGES),
            new StatusCase("a finding with no change is findings",
                ONE_FINDING, ChangeReport.EMPTY, ReportStatus.FINDINGS),
            new StatusCase("a finding with a change is findings",
                ONE_FINDING, ONE_CHANGE, ReportStatus.FINDINGS)
        );
    }

    @Test
    void testCasesCoverEveryStatus() {
        assertThat(statusCases().map(StatusCase::expected).distinct())
            .containsExactlyInAnyOrder(ReportStatus.values());
    }

    private static LintReport lintedReport(SongFactory.LineBuilder line) {
        var linter = SongFactory.buildSong(line).getLinter();
        linter.lint();

        return linter.getReport();
    }
}
