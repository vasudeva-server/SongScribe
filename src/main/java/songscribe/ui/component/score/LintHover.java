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

package songscribe.ui.component.score;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.lint.LintMark;
import songscribe.lint.LintReport;
import songscribe.lint.Linter;
import songscribe.message.MessageCenter;
import songscribe.message.notification.HoveredLintMarkDidChangeNotification;

/**
 * The lint mark under the pointer, across every line of the score, announced with a
 * {@link HoveredLintMarkDidChangeNotification} each time it changes.
 * <p>
 * One owner for the whole score rather than one per line: a move from one line onto the next
 * compares the marks of two lines.
 */
final class LintHover {

    /** The mark last announced, or null when the pointer was on none. */
    private static @Nullable LintMark hoveredMark = null;

    /**
     * The report {@link #hoveredMark} came from. A new report counts as a change even for an equal
     * mark, because the Messages panel rebuilds its rows for a new report and loses their
     * selection.
     */
    private static LintReport hoveredReport = LintReport.EMPTY;

    private LintHover() {
    }

    /**
     * Records the mark now under the pointer, and announces it when it differs from the last mark,
     * or comes from a newer report.
     *
     * @param mark the mark under the pointer, or {@code null} when the pointer is on none
     */
    static void pointerIsOver(@Nullable LintMark mark) {
        var report = Linter.getReport();

        if (Objects.equals(mark, hoveredMark) && report == hoveredReport) {
            return;
        }

        hoveredMark = mark;
        hoveredReport = report;
        MessageCenter.post(new HoveredLintMarkDidChangeNotification(mark));
    }
}
