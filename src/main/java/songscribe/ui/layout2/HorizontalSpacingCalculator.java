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

package songscribe.ui.layout2;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.BeamGroup;

/**
 * Calculates horizontal X positions for note columns following Gould/Ross engraving principles.
 * <p>
 * Key principles:
 * <ul>
 *   <li>Non-proportional spacing - rhythmic value does not determine horizontal distance</li>
 *   <li>Lyric-driven spacing - syllable widths determine column gaps</li>
 *   <li>Minimum clearance for accidentals - only increases spacing when needed</li>
 *   <li>Beam group coordination - tight internal spacing unless lyrics force expansion</li>
 * </ul>
 * <p>
 * Algorithm:
 * <ol>
 *   <li>Calculate first note position (11.5 MU from clef/key signature right extent)</li>
 *   <li>For each subsequent column:
 *     <ul>
 *       <li>Calculate minimum spacing based on previous column's right extent</li>
 *       <li>Calculate lyric spacing requirement (syllable widths + gap)</li>
 *       <li>Take maximum of minimum and lyric spacing</li>
 *       <li>Check accidental clearance and push right if needed</li>
 *     </ul>
 *   </li>
 *   <li>Special handling for beam groups:
 *     <ul>
 *       <li>Calculate tight internal spacing first</li>
 *       <li>Expand evenly if lyrics require more space</li>
 *       <li>Maintain external gaps from adjacent elements</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var calculator = new HorizontalSpacingCalculator();
 * calculator.calculatePositions(columns, line);
 * // columns now have X positions set
 * }</pre>
 */
public class HorizontalSpacingCalculator {

    /**
     * Creates a new HorizontalSpacingCalculator.
     */
    public HorizontalSpacingCalculator() {
    }

    /**
     * Calculates and sets X positions for all columns in a line.
     * <p>
     * This is the main entry point. After calling this method, each NoteColumn
     * will have its X position set via setX().
     *
     * @param columns List of NoteColumns to position (in note order)
     * @param line    The line containing these columns (for key signature info)
     */
    public void calculatePositions(@NotNull List<NoteColumn> columns, @NotNull Line line) {
        if (columns.isEmpty()) {
            return;
        }

        // Pre-process: identify beam groups and their column ranges
        var beamGroupRanges = identifyBeamGroupRanges(columns);

        // Calculate first note position
        double firstX = calculateFirstNoteX(line);
        columns.get(0).setX(firstX);

        // Process remaining columns
        for (var i = 1; i < columns.size(); i++) {
            // Check if we're starting a beam group
            var beamGroupRange = findBeamGroupStartingAt(beamGroupRanges, i);

            if (beamGroupRange != null) {
                // Process entire beam group
                var prevColumn = columns.get(i - 1);
                handleBeamGroup(columns, beamGroupRange, prevColumn.getX());
                i = beamGroupRange.end; // Skip to end of beam group
            }
            else {
                // Normal column-to-column spacing
                var prevColumn = columns.get(i - 1);
                var currColumn = columns.get(i);
                double nextX = calculateNextColumnX(prevColumn, currColumn);
                currColumn.setX(nextX);
            }
        }
    }

    // ==========================================================================
    // First Note Positioning
    // ==========================================================================

    /**
     * Calculates the X position of the first note in a line.
     *
     * @param line The line (for key signature info)
     * @return X position in pixels
     */
    private double calculateFirstNoteX(@NotNull Line line) {
        return LayoutConstants.calculateFirstNoteX(line.getKeyAccidentalCount());
    }

    // ==========================================================================
    // Column-to-Column Spacing
    // ==========================================================================

    /**
     * Calculates the X position for the next column based on the previous column.
     * <p>
     * This is the single source of truth for note spacing calculations.
     * Both layout and insertion note positioning use this method.
     *
     * @param prevColumn Previous column (must have X position already set)
     * @param currColumn Current column
     * @return X position for current column
     */
    public static double calculateNextColumnX(
            @NotNull NoteColumn prevColumn,
            @NotNull NoteColumn currColumn) {

        // Calculate minimum spacing (from previous column's right extent)
        double minimumSpacing = calculateMinimumColumnSpacing(prevColumn, currColumn);

        // Calculate lyric-driven spacing requirement
        double lyricSpacing = calculateLyricSpacing(prevColumn, currColumn);

        // If no lyrics, use default spacing; otherwise use lyric spacing
        double requiredSpacing;
        if (lyricSpacing > 0) {
            requiredSpacing = Math.max(minimumSpacing, lyricSpacing);
        } else {
            // No lyrics - use default comfortable spacing
            double defaultSpacing = calculateDefaultColumnSpacing(prevColumn, currColumn);
            requiredSpacing = Math.max(minimumSpacing, defaultSpacing);
        }

        // Calculate tentative X position
        double nextX = prevColumn.getX() + requiredSpacing;

        // Check accidental clearance and push right if needed
        if (needsAccidentalPush(prevColumn, currColumn, nextX)) {
            double accidentalClearance = LayoutConstants.px(LayoutConstants.ACCIDENTAL_CLEARANCE);
            double prevRightEdge = prevColumn.getRightEdgeX();
            double currAccidentalLeft = nextX + currColumn.getLeftExtent();
            double neededPush = prevRightEdge + accidentalClearance - currAccidentalLeft;

            if (neededPush > 0) {
                nextX += neededPush;
            }
        }

        return nextX;
    }

    /**
     * Calculates minimum spacing between columns based on geometric extents.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Minimum spacing in pixels
     */
    private static double calculateMinimumColumnSpacing(
            @NotNull NoteColumn prevColumn,
            @NotNull NoteColumn currColumn) {

        // Distance from previous column's center to current column's center
        // = previous column's right extent + MIN_COLUMN_GAP + abs(current column's left extent)
        double gap = LayoutConstants.px(LayoutConstants.MIN_COLUMN_GAP);
        return prevColumn.getRightExtent() + gap + Math.abs(currColumn.getLeftExtent());
    }

    /**
     * Calculates spacing requirement driven by lyric syllables.
     * <p>
     * Formula: (prevSyllableWidth / 2) + MIN_SYLLABLE_GAP + (currSyllableWidth / 2)
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Lyric spacing in pixels, or 0 if no syllables
     */
    private static double calculateLyricSpacing(
            @NotNull NoteColumn prevColumn,
            @NotNull NoteColumn currColumn) {

        if (!prevColumn.hasSyllable() && !currColumn.hasSyllable()) {
            return 0;
        }

        double prevHalfWidth = prevColumn.hasSyllable() ? prevColumn.getSyllableWidth() / 2.0 : 0;
        double currHalfWidth = currColumn.hasSyllable() ? currColumn.getSyllableWidth() / 2.0 : 0;
        double minGap = LayoutConstants.px(LayoutConstants.MIN_SYLLABLE_GAP);

        return prevHalfWidth + minGap + currHalfWidth;
    }

    /**
     * Calculates default spacing for columns when no lyrics are present.
     * <p>
     * Uses DEFAULT_COLUMN_GAP to provide comfortable spacing without lyrics.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Default spacing in pixels
     */
    private static double calculateDefaultColumnSpacing(
            @NotNull NoteColumn prevColumn,
            @NotNull NoteColumn currColumn) {

        double gap = LayoutConstants.px(LayoutConstants.DEFAULT_COLUMN_GAP);
        return prevColumn.getRightExtent() + gap + Math.abs(currColumn.getLeftExtent());
    }

    /**
     * Checks if the current column needs to be pushed right for accidental clearance.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @param currX      Tentative X position for current column
     * @return true if accidental clearance would be violated
     */
    private static boolean needsAccidentalPush(
            @NotNull NoteColumn prevColumn,
            @NotNull NoteColumn currColumn,
            double currX) {

        // Only check if current note has an accidental
        if (currColumn.getNote().getAccidental() == Note.Accidental.NONE) {
            return false;
        }

        return true; // Let calculateNextColumnX handle the actual push calculation
    }

    // ==========================================================================
    // Beam Group Handling
    // ==========================================================================

    /**
     * Represents a range of columns that form a beam group.
     */
    private static class BeamGroupRange {
        final int start;
        final int end;
        final BeamGroup group;

        BeamGroupRange(int start, int end, BeamGroup group) {
            this.start = start;
            this.end = end;
            this.group = group;
        }
    }

    /**
     * Identifies all beam group ranges in the column list.
     * <p>
     * Pre-processing approach (Option B from plan): find all beam groups first,
     * then apply spacing with beam group awareness.
     *
     * @param columns List of columns
     * @return List of beam group ranges
     */
    private @NotNull List<BeamGroupRange> identifyBeamGroupRanges(@NotNull List<NoteColumn> columns) {
        var ranges = new ArrayList<BeamGroupRange>();

        for (var i = 0; i < columns.size(); i++) {
            var column = columns.get(i);

            if (!column.isBeamed()) {
                continue;
            }

            var beamGroup = column.getBeamGroup();

            // Check if we've already processed this beam group
            boolean alreadyProcessed = ranges.stream()
                    .anyMatch(range -> range.group == beamGroup);

            if (alreadyProcessed) {
                continue;
            }

            // Find start and end indices for this beam group
            int start = i;
            int end = i;

            for (var j = i + 1; j < columns.size(); j++) {
                if (columns.get(j).getBeamGroup() == beamGroup) {
                    end = j;
                }
                else {
                    break;
                }
            }

            ranges.add(new BeamGroupRange(start, end, beamGroup));
        }

        return ranges;
    }

    /**
     * Finds a beam group range that starts at the specified index.
     *
     * @param ranges Beam group ranges
     * @param index  Column index
     * @return BeamGroupRange if found, null otherwise
     */
    private BeamGroupRange findBeamGroupStartingAt(
            @NotNull List<BeamGroupRange> ranges,
            int index) {

        for (var range : ranges) {
            if (range.start == index) {
                return range;
            }
        }

        return null;
    }

    /**
     * Handles spacing for a beam group.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Calculate where beam group should start (based on previous column)</li>
     *   <li>Calculate tight internal spacing (ignore lyrics)</li>
     *   <li>Check if lyrics under beam group require expansion</li>
     *   <li>If expansion needed, distribute evenly across columns</li>
     * </ol>
     *
     * @param columns        All columns
     * @param range          Beam group range
     * @param prevColumnX    X position of the column before the beam group
     */
    private void handleBeamGroup(
            @NotNull List<NoteColumn> columns,
            @NotNull BeamGroupRange range,
            double prevColumnX) {

        var beamColumns = columns.subList(range.start, range.end + 1);
        int columnCount = beamColumns.size();

        if (columnCount == 0) {
            return;
        }

        // Calculate where first column of beam group should go
        var prevColumn = columns.get(range.start - 1);
        var firstBeamColumn = beamColumns.get(0);
        double startX = calculateNextColumnX(prevColumn, firstBeamColumn);

        if (columnCount == 1) {
            // Single column "beam group" - just use normal spacing
            beamColumns.get(0).setX(startX);
            return;
        }

        // Step 1: Calculate tight internal spacing
        double tightGap = LayoutConstants.px(LayoutConstants.BEAM_GROUP_MIN_INTERNAL_GAP);
        var tightPositions = new ArrayList<Double>();
        double currentX = startX;

        tightPositions.add(currentX);

        for (var i = 1; i < columnCount; i++) {
            var prev = beamColumns.get(i - 1);
            var curr = beamColumns.get(i);

            // Use tight gap, ignoring syllables
            double spacing = prev.getRightExtent() + tightGap + Math.abs(curr.getLeftExtent());
            currentX += spacing;
            tightPositions.add(currentX);
        }

        double tightTotalWidth = currentX - startX;

        // Step 2: Calculate lyric-driven width requirement
        double lyricRequiredWidth = 0;

        for (var i = 1; i < columnCount; i++) {
            var prev = beamColumns.get(i - 1);
            var curr = beamColumns.get(i);
            double lyricSpacing = calculateLyricSpacing(prev, curr);

            if (lyricSpacing > 0) {
                lyricRequiredWidth += lyricSpacing;
            }
            else {
                // No lyrics, use minimum spacing
                lyricRequiredWidth += calculateMinimumColumnSpacing(prev, curr);
            }
        }

        // Step 3: Determine final positions
        if (lyricRequiredWidth <= tightTotalWidth) {
            // Tight spacing is sufficient
            for (var i = 0; i < columnCount; i++) {
                beamColumns.get(i).setX(tightPositions.get(i));
            }
        }
        else {
            // Need to expand - distribute expansion evenly
            double expansionNeeded = lyricRequiredWidth - tightTotalWidth;
            double expansionPerGap = expansionNeeded / (columnCount - 1);

            beamColumns.get(0).setX(startX);
            currentX = startX;

            for (var i = 1; i < columnCount; i++) {
                double tightSpacing = tightPositions.get(i) - tightPositions.get(i - 1);
                double expandedSpacing = tightSpacing + expansionPerGap;

                currentX += expandedSpacing;
                beamColumns.get(i).setX(currentX);
            }
        }
    }
}
