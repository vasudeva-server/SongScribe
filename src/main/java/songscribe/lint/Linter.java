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

import songscribe.dom.Song;
import songscribe.message.notification.LintingNotification;

/**
 * Runs every {@link LintRule} over one song and holds the result until the next run.
 *
 * <p>One linter belongs to one song. Only the {@link Song} constructor creates it, and the song
 * hands it out through {@link Song#getLinter()}.
 *
 * <p>The song lints when a {@link LintingNotification} for it is built; that class states the
 * order this gives every reader of the report.
 *
 * <p>The linter has no bus subscription and posts no notification. It knows nothing of the UI:
 * the UI asks it for the report ({@link #getReport()}, {@link #hasFlag}).
 *
 * <p>EDT only, by contract: it runs on the thread that owns the song, and reads the song only.
 */
public final class Linter {

    private final Song song;

    private LintReport report = LintReport.EMPTY;

    /**
     * Creates the linter of {@code song}. Called only by the {@link Song} constructor.
     *
     * @param song the song this linter lints; read only, never changed
     */
    public Linter(Song song) {
        this.song = song;
    }

    /**
     * Lints the song with every rule and makes the result the current report.
     *
     * @invariant the findings are in {@link LintRule} declaration order and, within a rule, in
     *     song order
     * @effects replaces the current report; reads the song and changes nothing in it
     */
    public void lint() {
        var findings = new ArrayList<LintFinding>();

        for (var rule : LintRule.values()) {
            for (var violation : rule.check(song)) {
                findings.add(new LintFinding(rule, violation));
            }
        }

        report = new LintReport(findings);
    }

    /**
     * The report of the last lint run.
     *
     * @return the current report; {@link LintReport#EMPTY} before the first run
     */
    public LintReport getReport() {
        return report;
    }

    /**
     * Whether some finding in the current report blocks what {@code flag} names.
     *
     * @param flag what a finding may block
     * @return {@link LintReport#hasFlag} of {@link #getReport()}
     */
    public boolean hasFlag(LintRule.Flag flag) {
        return report.hasFlag(flag);
    }
}
