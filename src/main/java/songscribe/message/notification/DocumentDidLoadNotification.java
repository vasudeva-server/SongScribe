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

package songscribe.message.notification;

import songscribe.dom.ChangeReport;
import songscribe.dom.Song;

/**
 * Posted when a document is installed as the one being edited: File &gt; Open, File &gt; New and
 * Revert to Saved. The report ({@link #getReport()}) is empty for File &gt; New.
 */
public class DocumentDidLoadNotification extends LintingNotification {

    public enum LoadChange {
        UNCHANGED,

        CHANGED;

        public static LoadChange of(ChangeReport report) {
            return report.isEmpty() ? UNCHANGED : CHANGED;
        }
    }

    private final ChangeReport report;

    public DocumentDidLoadNotification(Song song, ChangeReport report) {
        super(song);
        this.report = report;
    }

    public ChangeReport getReport() {
        return report;
    }

    public LoadChange getLoadChange() {
        return LoadChange.of(report);
    }
}
