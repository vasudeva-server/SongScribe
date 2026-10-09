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

package songscribe.undo;

import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ChangeReport;
import songscribe.dom.Key;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.SongDidChangeNotification;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.StaffElementFactory.note;

/**
 * How the report of an edit travels through undo and redo: the undo of an edit that reported
 * changes delivers an empty report, because the undo reverses those changes along with the rest,
 * and the redo delivers a report equal to the edit's, because it makes them again.
 */
class UndoControllerTest extends UnitTest {

    private static final int TIED = 3;
    private static final int OTHER = 4;
    private static final int FIRST_LINE = 0;
    private static final int INSERTED_INDEX = 1;

    /** A line with a tie from its first note to its second, and a note after them. */
    private static final String TIED_PAIR = TIED + " ~ " + TIED + " " + OTHER;

    /** A replay target over one song, which takes no font change. */
    private record SongTarget(Song song) implements ReplayTarget {
        @Override
        public Song getSong() {
            return song;
        }

        @Override
        public void setFonts(DocumentFonts fonts) {
            throw new AssertionError("a replayed test batch set the document fonts");
        }
    }

    /** Takes the report of every song notification, empty or not, in arrival order. */
    private static final class ReportCollector {
        private final List<ChangeReport> reports = new ArrayList<>();
        private final MessageSubscription subscription = new MessageSubscription(this);

        @Handler
        void songDidChange(SongDidChangeNotification notification) {
            reports.add(notification.getReport());
        }

        void dispose() {
            subscription.dispose();
        }
    }

    @Test
    void testUndoDeliversAnEmptyReportAndRedoDeliversTheEditsReport() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var controller = new UndoController(() -> new SongTarget(song));
        var controllerSubscription = new MessageSubscription(controller);
        var collector = new ReportCollector();

        try {
            song.withModification(() -> line.addElement(INSERTED_INDEX, note(OTHER)));

            var editReport = collector.reports.getFirst();

            assertThat(editReport.isEmpty()).as("the edit breaks the tie and reports it").isFalse();

            controller.performUndo();

            assertThat(collector.reports).as("the undo delivers one report").hasSize(2);
            assertThat(collector.reports.get(1).isEmpty()).as("the undo's report").isTrue();

            controller.performRedo();

            assertThat(collector.reports).as("the redo delivers one report").hasSize(3);
            assertThat(collector.reports.get(2).changes()).as("the redo's report").isEqualTo(editReport.changes());
        } finally {
            collector.dispose();
            controllerSubscription.dispose();
        }
    }
}
