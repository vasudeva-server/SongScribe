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

/**
 * Song-wide layout constants shared across all staff lines.
 * <p>
 * Every line in the song uses the same {@code totalLineHeightSs} so that
 * staff baselines are vertically consistent regardless of how many ledger lines or
 * stem extensions appear on any individual line.
 * <p>
 * When {@code verseCount == 0} the lyrics band is collapsed: {@code staffToLyricsGapSs}
 * and {@code lyricsBandHeightSs} are both 0.
 *
 * @param maxAboveStaffSs    staff-space amount above the staff top (= staff top Y within a line)
 * @param maxBelowStaffSs    staff-space amount below the staff bottom for non-lyric content
 *                           (line-component sizing reservation, includes the MIN_BELOW floor
 *                           for ledger-line capacity and the inter-line margin)
 * @param maxBelowContentSs  song-wide maximum of actual below-staff content extent across
 *                           all lines; the lyric-positioning anchor (no MIN_BELOW floor, no margin)
 * @param staffToLyricsGapSs distance from below-staff content to the first verse baseline;
 *                           equals the visual gap (to text top) plus the font ascent so that
 *                           the rendered text top sits exactly one visual gap below the content
 * @param lyricsLineHeightSs height allocated for each verse row
 * @param verseCount         number of verse rows present (0 collapses the lyrics band)
 * @param lyricsBandHeightSs total height of the lyrics band (= verseCount * lyricsLineHeightSs)
 * @param totalLineHeightSs  total height of a single staff line component
 */
public record SongLayoutMetrics(
    double maxAboveStaffSs,
    double maxBelowStaffSs,
    double maxBelowContentSs,
    double staffToLyricsGapSs,
    double lyricsLineHeightSs,
    int verseCount,
    double lyricsBandHeightSs,
    double totalLineHeightSs
) {

    /** Y position of the staff top within a line component (component-local coordinates). */
    public double staffTopYSsInLine() {
        return maxAboveStaffSs;
    }

    /** Y position of the staff bottom within a line component (component-local coordinates). */
    public double staffBottomYSsInLine() {
        return staffTopYSsInLine() + StaffExtents.STAFF_HEIGHT_SS;
    }

    /** Y position of the baseline for the given verse within a line component. */
    public double verseYSsInLine(int verse) {
        return staffBottomYSsInLine() + maxBelowContentSs + staffToLyricsGapSs + (verse - 1) * lyricsLineHeightSs;
    }
}
