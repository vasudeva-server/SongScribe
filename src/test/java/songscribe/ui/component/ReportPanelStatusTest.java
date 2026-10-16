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

import org.junit.jupiter.api.Test;

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
 * The status that {@link ReportPanel} publishes across a sequence of edits.
 */
class ReportPanelStatusTest extends UnitTest {

    private static final ChangeReport ONE_CHANGE = new ChangeReport(
        List.of(new ReportedChange(Strings.REPORT_BEAMS_REMOVED, "", null)));

    @Test
    void testAnUndoAfterChangesReturnsTheStatusToEmpty() {
        var panel = new ReportPanel(MockEnvHelper.setupMockEnv().frame());
        panel.songDidChange(edit(cleanSong(), ONE_CHANGE));

        // An undo carries an empty report.
        panel.songDidChange(edit(cleanSong(), ChangeReport.EMPTY));

        assertThat(panel.status().get()).isEqualTo(ReportStatus.EMPTY);
    }

    /**
     * The song holds a rest and no note, because the missing-lyric rule reports every note sung on
     * no syllable.
     */
    private static Song cleanSong() {
        return SongFactory.buildSong(line -> line.addElement(StaffElementFactory.crotchetRest()));
    }

    /**
     * The notification lints the song, so the panel reads a current lint report.
     */
    private static SongDidChangeNotification edit(Song song, ChangeReport report) {
        return new SongDidChangeNotification(List.of(), song, report, null);
    }
}
