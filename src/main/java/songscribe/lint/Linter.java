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
 * <p>One linter belongs to one song. The song lints when a {@link LintingNotification} for it
 * is built; that class states the order this gives every reader of the report.
 *
 * <p>EDT only, by contract; reads the song only.
 */
public final class Linter {

    private final Song song;

    private LintReport report = LintReport.EMPTY;

    public Linter(Song song) {
        this.song = song;
    }

    public void lint() {
        var findings = new ArrayList<LintFinding>();

        for (var rule : LintRule.values()) {
            for (var violation : rule.check(song)) {
                findings.add(new LintFinding(rule, violation));
            }
        }

        report = new LintReport(findings);
    }

    public LintReport getReport() {
        return report;
    }

    public boolean hasFlag(LintRule.Flag flag) {
        return report.hasFlag(flag);
    }
}
