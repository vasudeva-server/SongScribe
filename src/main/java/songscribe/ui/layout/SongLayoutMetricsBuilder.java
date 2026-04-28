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

package songscribe.ui.layout;

import java.util.List;

/**
 * Reduces a list of per-line {@link LayoutResult}s into a single
 * {@link SongLayoutMetrics} that applies uniformly across all lines.
 * <p>
 * When no line reports any verses, the lyrics band collapses: {@code staffToLyricsGapSs},
 * {@code lyricsLineHeightSs}, and {@code lyricsBandHeightSs} are all zero.
 */
public final class SongLayoutMetricsBuilder {

    private SongLayoutMetricsBuilder() {}

    /**
     * Builds song-wide layout metrics from the per-line layout results.
     *
     * @param layouts       per-line layout results; may be empty
     * @param lyricAscentSs ascent of the lyrics font in staff spaces; added to the visual
     *                      gap so that {@code staffToLyricsGapSs} positions the baseline
     *                      correctly (gap is measured to the text top, not the baseline)
     * @return metrics object suitable for driving uniform line heights
     */
    public static SongLayoutMetrics build(List<LayoutResult> layouts, double lyricAscentSs) {
        var maxAboveStaffSs = LayoutStylesheet.MIN_ABOVE_STAFF_SS;
        var maxBelowStaffSs = LayoutStylesheet.MIN_BELOW_STAFF_SS + LayoutStylesheet.INTER_LINE_MARGIN_SS;
        var maxBelowContentSs = 0.0;
        var verseCount = 0;

        for (var layout : layouts) {
            maxAboveStaffSs = Math.max(maxAboveStaffSs, layout.getAboveStaffSs());
            maxBelowStaffSs = Math.max(maxBelowStaffSs, layout.getBelowStaffReservationSs());
            maxBelowContentSs = Math.max(maxBelowContentSs, layout.getBelowContentSs());

            if (layout.verseCount() > verseCount) {
                verseCount = layout.verseCount();
            }
        }

        var hasLyrics = verseCount > 0;
        var staffToLyricsGapSs = hasLyrics ? LayoutStylesheet.LYRICS_ROW_MARGIN_SS + lyricAscentSs : 0.0;
        var lyricsLineHeightSs = hasLyrics ? LayoutStylesheet.LYRICS_HEIGHT_SS : 0.0;
        var lyricsBandHeightSs = verseCount * lyricsLineHeightSs;
        var totalLineHeightSs = maxAboveStaffSs
            + LayoutStylesheet.STAFF_HEIGHT_SS
            + maxBelowStaffSs
            + staffToLyricsGapSs
            + lyricsBandHeightSs;

        return new SongLayoutMetrics(
            maxAboveStaffSs,
            maxBelowStaffSs,
            maxBelowContentSs,
            staffToLyricsGapSs,
            lyricsLineHeightSs,
            verseCount,
            lyricsBandHeightSs,
            totalLineHeightSs
        );
    }
}
