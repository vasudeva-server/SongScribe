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
package songscribe.message.notification;

import songscribe.dom.ChangeReport;
import songscribe.message.Message;

/**
 * Carries the report of the changes an edit made to the song on its own, so the UI can tell the
 * user about them; {@code songscribe.dom} makes the changes and must not depend on the UI.
 *
 * <p>Posted at most once for each edit, only when the report is not empty, after the batch's
 * {@link SongDidChangeNotification} when the batch has one. Never posted during an undo or redo
 * replay, nor while mutation tracking is suspended.
 *
 * <p>Delivery is synchronous, inside the edit that made the changes: a subscriber runs before
 * the edit's caller regains control. A subscriber that shows a modal window must therefore defer
 * it, or the window blocks the edit part way through.
 */
public class ChangesWereReportedNotification extends Message {

    private final ChangeReport report;

    public ChangesWereReportedNotification(ChangeReport report) {
        this.report = report;
    }

    /**
     * Returns the edit's report.
     *
     * @return the report, holding at least one entry
     */
    public ChangeReport getReport() {
        return report;
    }
}
