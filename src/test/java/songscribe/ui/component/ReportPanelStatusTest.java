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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ChangeReport;
import songscribe.dom.ReportedChange;
import songscribe.dom.Song;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElementFactory;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.MockEnvHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status that {@link ReportPanel} publishes: a lint finding outranks a change, and a change
 * outranks an empty list.
 */
class ReportPanelStatusTest extends UnitTest {

    private static final ChangeReport ONE_CHANGE = new ChangeReport(
        List.of(new ReportedChange(Strings.REPORT_BEAMS_REMOVED, "", null)));

    private ReportPanel panel;

    private enum Lint {
        CLEAN,
        FLAGGED
    }

    private record StatusCase(String description, Lint lint, ChangeReport report, ReportStatus expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    // Every notification replaces the whole status, so no test leaves state that the next one
    // reads. The panel subscribes to the class's bus and is never disposed, so one panel for
    // the class keeps the subscribers to one.
    @BeforeAll
    void createPanel() {
        panel = new ReportPanel(MockEnvHelper.setupMockEnv().frame());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusCases")
    void testStatusNamesTheMostUrgentKindOfRow(StatusCase testCase) {
        panel.songDidChange(edit(song(testCase.lint()), testCase.report()));

        assertThat(panel.status().get()).isEqualTo(testCase.expected());
    }

    static Stream<StatusCase> statusCases() {
        return Stream.of(
            new StatusCase("no finding and no change is empty",
                Lint.CLEAN, ChangeReport.EMPTY, ReportStatus.EMPTY),
            new StatusCase("a change with no finding is changes",
                Lint.CLEAN, ONE_CHANGE, ReportStatus.CHANGES),
            new StatusCase("a finding with no change is findings",
                Lint.FLAGGED, ChangeReport.EMPTY, ReportStatus.FINDINGS),
            new StatusCase("a finding with a change is findings",
                Lint.FLAGGED, ONE_CHANGE, ReportStatus.FINDINGS)
        );
    }

    @Test
    void testCasesCoverEveryStatus() {
        assertThat(statusCases().map(StatusCase::expected).distinct())
            .containsExactlyInAnyOrder(ReportStatus.values());
    }

    @Test
    void testAnUndoAfterChangesReturnsTheStatusToEmpty() {
        panel.songDidChange(edit(song(Lint.CLEAN), ONE_CHANGE));

        // An undo carries an empty report.
        panel.songDidChange(edit(song(Lint.CLEAN), ChangeReport.EMPTY));

        assertThat(panel.status().get()).isEqualTo(ReportStatus.EMPTY);
    }

    /**
     * A flagged song starts with a bar line, which the leading-bar rule reports. Both songs hold a
     * rest and no note, because the missing-lyric rule reports every note sung on no syllable.
     */
    private static Song song(Lint lint) {
        return SongFactory.buildSong(line -> {
            if (lint == Lint.FLAGGED) {
                line.addElement(StaffElementFactory.singleBarline());
            }

            line.addElement(StaffElementFactory.crotchetRest());
        });
    }

    /**
     * The notification lints the song, so the panel reads a current lint report.
     */
    private static SongDidChangeNotification edit(Song song, ChangeReport report) {
        return new SongDidChangeNotification(List.of(), song, report, null);
    }
}
