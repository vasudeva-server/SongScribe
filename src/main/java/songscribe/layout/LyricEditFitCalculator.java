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

package songscribe.layout;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Answers "will this lyric still fit?" for the in-place lyric editor, so a widening edit can be
 * refused <em>before</em> it is written to the model. An already-overflowing line is deliberately
 * not blocked, so the user can still shorten a syllable to recover. It runs the same horizontal
 * solve as the committed layout, so a line it accepts is one the layout can always place.
 */
public final class LyricEditFitCalculator {

    private LyricEditFitCalculator() {
    }

    /**
     * An empty line always fits.
     */
    public static boolean lineFits(Line line, LyricRenderMetrics lyricRenderMetrics, double staffRightMarginSs) {
        var columns = new ElementColumnBuilder(lyricRenderMetrics).buildColumns(line);

        return columns.isEmpty()
            || !HorizontalSpacingCalculator.solveLine(columns, line, staffRightMarginSs).isInfeasible();
    }

    /**
     * The candidate is written onto the element only for the duration of the solve and restored
     * before returning; EDT-only, and no mutation is recorded. The candidate's {@code syllabic}
     * need only carry the correct continues-status ({@link Lyric#syllabicContinues}).
     */
    public static boolean lyricEditFits(
        Line line,
        int index,
        Lyric candidate,
        LyricRenderMetrics lyricRenderMetrics,
        double staffRightMarginSs) {

        var element = line.getElement(index);
        var script = candidate.script();
        var before = element.getLyricForScript(script);

        try {
            applyLyric(element, script, candidate);
            return lineFits(line, lyricRenderMetrics, staffRightMarginSs);
        } finally {
            applyLyric(element, script, before);
        }
    }

    private static void applyLyric(StaffElement element, Song.Script script, @Nullable Lyric lyric) {
        if (lyric == null) {
            element.removeLyricForScript(script);
        } else {
            element.putLyric(lyric);
        }
    }
}
