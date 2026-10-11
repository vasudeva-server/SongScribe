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

package songscribe.lint;

import java.util.ArrayList;

import net.engio.mbassy.listener.Handler;

import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.LintReportDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Owns the lint report of the open document: runs every {@link LintRule} over the song and
 * holds the result until the next run.
 *
 * <p>The report is current after every change to the song. Every edit, undo and redo posts a
 * {@link SongDidChangeNotification}, and the linter lints at once on each, so a reader never
 * sees a report older than the last change. Whoever installs a new document lints it.
 *
 * <p>The linter knows nothing of the UI. The UI asks it for the report ({@link #getReport()},
 * {@link #hasFlag}) and repaints on {@link LintReportDidChangeNotification}.
 *
 * <p>EDT only, by contract: it runs on the thread that owns the song, and reads the song only.
 *
 * <p><b>Lifecycle:</b> created the first time any of its methods is called, it subscribes to
 * the message bus for the life of the process and is never disposed. The static instance keeps
 * it reachable.
 */
public final class Linter {

    private static final Linter INSTANCE = new Linter();

    private LintReport report = LintReport.EMPTY;

    private Linter() {
        MessageSubscription.addProcessListener(this);
    }

    /**
     * Lints {@code song} with every rule and makes the result the current report.
     *
     * @param song the song to lint; read only, never changed
     * @return the new current report
     * @invariant the findings are in {@link LintRule} declaration order and, within a rule, in
     *     song order
     * @effects replaces the current report, then posts {@link LintReportDidChangeNotification}
     *     whether or not the findings changed
     */
    public static LintReport lint(Song song) {
        var findings = new ArrayList<LintFinding>();

        for (var rule : LintRule.values()) {
            for (var violation : rule.check(song)) {
                findings.add(new LintFinding(rule, violation));
            }
        }

        var lintReport = new LintReport(findings);
        INSTANCE.report = lintReport;
        MessageCenter.post(new LintReportDidChangeNotification());
        return lintReport;
    }

    /**
     * The report of the last lint run.
     *
     * @return the current report; {@link LintReport#EMPTY} before the first run
     */
    public static LintReport getReport() {
        return INSTANCE.report;
    }

    /**
     * Whether some finding in the current report blocks what {@code flag} names.
     *
     * @return {@link LintReport#hasFlag} of {@link #getReport()}
     */
    public static boolean hasFlag(LintRule.Flag flag) {
        return getReport().hasFlag(flag);
    }

    /**
     * Lints the song the edit changed. Only a document song posts this notification: a song
     * being parsed runs with mutation tracking suspended.
     */
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        lint(message.getSong());
    }
}
