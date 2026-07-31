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
import songscribe.dom.StaffElement;

/**
 * Answers "will this lyric still fit?" for the in-place lyric editor, so a widening edit can be
 * refused <em>before</em> it is written to the model (issue #449).
 * <p>
 * A wider syllable forces wider minimum spacing between columns (via {@link LyricLift}), which can
 * push the line past the staff margin. The committed layout only discovers this at paint time, and
 * by then it can only place the line on its collision floors and draw the tail clipped, in red
 * (refs #696) — the syllable the user just typed may be the part that is cut off. Refusing the edit
 * up front keeps a fitting line fitting; an already-overflowing line is deliberately not blocked,
 * so the user can still shorten a syllable to recover. This calculator runs
 * the identical horizontal solve ({@link ElementColumnBuilder} + {@link HorizontalSpacingCalculator#solveLine})
 * the committed layout runs, over the same columns, so a line the pre-check accepts is one the
 * layout can always place — the pre-check and the committed layout never disagree.
 */
public final class LyricEditFitCalculator {

    private LyricEditFitCalculator() {
    }

    /**
     * Returns whether the line's current content fits within {@code staffRightMarginSs} — the same
     * verdict {@link LayoutEngine#layout} reaches, computed from the same columns and solve. An
     * empty line (no columns) always fits.
     *
     * @param line               the line to solve
     * @param lyricRenderMetrics metrics for measuring the line's syllables
     * @param staffRightMarginSs the maximum allowed line width in staff spaces
     * @return {@code true} when the line fits (the solver does not report it infeasible)
     */
    public static boolean lineFits(Line line, LyricRenderMetrics lyricRenderMetrics, double staffRightMarginSs) {
        var columns = new ElementColumnBuilder(lyricRenderMetrics).buildColumns(line);

        return columns.isEmpty()
            || !HorizontalSpacingCalculator.solveLine(columns, line, staffRightMarginSs).isInfeasible();
    }

    /**
     * Returns whether the line would still fit if element {@code index}'s lyric were replaced by
     * {@code candidate} (the verse slot is taken from {@code candidate}). The candidate is written
     * onto the element only for the duration of the solve and restored before returning, so the
     * model is never left changed — nothing observes the transient state on the single-threaded EDT,
     * and no mutation is recorded.
     *
     * <p>The candidate's {@code syllabic} need only carry the correct continues-status
     * ({@link Lyric#syllabicContinues}): that is the sole part of the committed syllabic that affects
     * horizontal spacing (a hyphenated pair reserves the hyphen cell, a word-final pair a space),
     * and the successor-syllabic fix-up {@code setSyllableBoundary} performs does not change any gap
     * width. Syllable width itself depends only on the text.
     *
     * @param line               the line containing the edited element
     * @param index              the element index on the line
     * @param candidate          the candidate lyric to probe; its {@code verse} selects the slot
     * @param lyricRenderMetrics metrics for measuring the line's syllables
     * @param staffRightMarginSs the maximum allowed line width in staff spaces
     * @return {@code true} when the line fits with the candidate lyric in place
     */
    public static boolean lyricEditFits(
        Line line,
        int index,
        Lyric candidate,
        LyricRenderMetrics lyricRenderMetrics,
        double staffRightMarginSs) {

        var element = line.getElement(index);
        var verse = candidate.verse();
        var before = element.getLyricForVerse(verse);

        try {
            applyLyric(element, verse, candidate);
            return lineFits(line, lyricRenderMetrics, staffRightMarginSs);
        } finally {
            applyLyric(element, verse, before);
        }
    }

    /**
     * Writes {@code lyric} onto the element's verse slot, or clears the slot when {@code lyric} is
     * {@code null}. Used symmetrically to install the probe candidate and to restore the prior lyric.
     */
    private static void applyLyric(StaffElement element, int verse, @Nullable Lyric lyric) {
        if (lyric == null) {
            element.setLyricForVerse(verse, null, false, null, Lyric.Extend.NONE);
        } else {
            element.setLyricForVerse(verse, lyric.syllabic(), lyric.compound(), lyric.text(), lyric.extend());
        }
    }
}
