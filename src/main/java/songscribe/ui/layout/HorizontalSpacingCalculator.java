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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.Line;
import songscribe.smufl.Engraving;
import songscribe.ui.renderer.GlissandoRenderer;
import songscribe.ui.renderer.NoteRenderer;

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
 *   <li>Calculate first note position (clef + key signature + offset, all in ss)</li>
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
     * Minimum horizontal gap between adjacent note columns.
     * This is the absolute minimum; lyric spacing may require more.
     */
    public static final double MIN_COLUMN_GAP_SS = 0.125;  // 1px
    /**
     * Default horizontal gap between adjacent note columns when no lyrics are present.
     * Provides comfortable spacing for music without lyrics.
     */
    public static final double DEFAULT_COLUMN_GAP_SS = 2.5;  // 20px
    /**
     * Minimum clearance for accidentals from previous column's right extent.
     * Accidentals only push spacing when this minimum would be violated.
     */
    public static final double ACCIDENTAL_CLEARANCE_SS = 0.125;  // 1px
    /**
     * Gap between a grace note and its host note.
     */
    public static final double GRACE_NOTE_GAP_SS = 2.0;  // 16px
    /**
     * Minimum internal spacing within a beam group (tight, regular spacing).
     * Beam groups may widen if lyrics under them require it.
     */
    public static final double BEAM_GROUP_MIN_INTERNAL_GAP_SS = 1.5;  // 12px
    /**
     * Distance from right extent of clef/key signature to first note column.
     * Per Gould/Ross: provides visual separation between staff beginning and music.
     */
    public static final double FIRST_NOTE_OFFSET_SS = 3.5;  // 28px
    /**
     * Width of each accidental in the key signature.
     */
    public static final double KEY_ACCIDENTAL_WIDTH_SS = 1.0;  // 8px

    /**
     * Calculates the X position of the first note in a line, in staff-space units.
     * <p>
     * Formula: clefWidth + keySignatureWidth + FIRST_NOTE_OFFSET
     *
     * @param keyAccidentalCount Number of accidentals in the key signature
     * @return X position in staff-space units where the first note should be placed
     */
    public static double calculateFirstElementXSs(int keyAccidentalCount) {
        return calculateHeaderRightEdgeSs(keyAccidentalCount) + FIRST_NOTE_OFFSET_SS;
    }

    /**
     * Returns the X position of the right edge of the staff header
     * (clef + optional key signature), in staff-space units.
     *
     * @param keyAccidentalCount Number of accidentals in the key signature
     * @return X position in staff-space units of the header's right edge
     */
    public static double calculateHeaderRightEdgeSs(int keyAccidentalCount) {
        return Engraving.G_CLEF_WIDTH_SS + keyAccidentalCount * KEY_ACCIDENTAL_WIDTH_SS;
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
    public void calculatePositions(List<ElementColumn> columns, Line line) {
        if (columns.isEmpty()) {
            return;
        }

        // Pre-process: identify beam groups and their column ranges
        var beamGroupRanges = identifyBeamGroupRanges(columns);

        // Calculate first note position
        var firstXSs = calculateFirstNoteXSs(line);
        columns.get(0).setXSs(firstXSs);

        // Process remaining columns
        for (var i = 1; i < columns.size(); i++) {
            // Check if we're starting a beam group
            var beamGroupRange = findBeamGroupStartingAt(beamGroupRanges, i);

            if (beamGroupRange != null) {
                // Process entire beam group
                var prevColumn = columns.get(i - 1);
                handleBeamGroup(columns, beamGroupRange, prevColumn.getXSs());
                i = beamGroupRange.end; // Skip to end of beam group
            } else {
                // Normal column-to-column spacing
                var prevColumn = columns.get(i - 1);
                var currColumn = columns.get(i);
                var nextXSs = calculateNextColumnXSs(prevColumn, currColumn);
                currColumn.setXSs(nextXSs);
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
     * @return X position in ss
     */
    private double calculateFirstNoteXSs(Line line) {
        return calculateFirstElementXSs(line.getKeyAccidentalCount());
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
    public static double calculateNextColumnXSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        // Grace note → host note: use tight grace note spacing
        if (prevColumn.getElement().getType().isGraceNote()) {
            var spacingSs = prevColumn.getRightExtentSs()
                + GRACE_NOTE_GAP_SS
                + Math.abs(currColumn.getLeftExtentSs());
            return prevColumn.getXSs() + spacingSs;
        }

        // Calculate minimum spacing (from previous column's right extent)
        var minimumSpacingSs = calculateMinimumColumnSpacingSs(prevColumn, currColumn);

        // Calculate lyric-driven spacing requirement
        var lyricSpacingSs = calculateLyricSpacingSs(prevColumn, currColumn);

        // Use default comfortable spacing as a floor, then expand further if lyrics require it.
        // This prevents the case where only one side has a lyric from producing
        // tighter-than-default note-head-to-note-head spacing.
        var defaultSpacingSs = calculateDefaultColumnSpacingSs(prevColumn, currColumn);
        var requiredSpacingSs = Math.max(minimumSpacingSs, Math.max(defaultSpacingSs, lyricSpacingSs));

        // Calculate tentative X position
        var nextXSs = prevColumn.getXSs() + requiredSpacingSs;

        // Check accidental clearance and push right if needed
        if (needsAccidentalPush(prevColumn, currColumn, nextXSs)) {
            var accidentalClearanceSs = ACCIDENTAL_CLEARANCE_SS;
            var prevRightEdgeSs = prevColumn.getRightEdgeXSs();
            var currAccidentalLeftSs = nextXSs + currColumn.getLeftExtentSs();
            var neededPushSs = prevRightEdgeSs + accidentalClearanceSs - currAccidentalLeftSs;

            if (neededPushSs > 0) {
                nextXSs += neededPushSs;
            }
        }

        // Check glissando spacing: ensure enough horizontal room for the glissando
        var spacingSs = nextXSs - prevColumn.getXSs();
        spacingSs = ensureGlissandoSpacing(prevColumn, currColumn, spacingSs);
        nextXSs = prevColumn.getXSs() + spacingSs;

        return nextXSs;
    }

    /**
     * Calculates minimum spacing between columns based on geometric extents.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Minimum spacing in ss
     */
    private static double calculateMinimumColumnSpacingSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        // Distance from previous column's center to current column's center
        // = previous column's right extent + MIN_COLUMN_GAP + abs(current column's left extent)
        return prevColumn.getRightExtentSs() + MIN_COLUMN_GAP_SS + Math.abs(currColumn.getLeftExtentSs());
    }

    /**
     * Calculates spacing requirement driven by lyric syllables.
     * <p>
     * Formula: (prevSyllableWidth / 2) + MIN_SYLLABLE_GAP + (currSyllableWidth / 2)
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Lyric spacing in ss, or 0 if no syllables
     */
    private static double calculateLyricSpacingSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        if (!prevColumn.hasSyllable() && !currColumn.hasSyllable()) {
            return 0;
        }

        var prevHalfWidthSs = prevColumn.hasSyllable() ? prevColumn.getSyllableWidthSs() / 2.0 : 0;
        var currHalfWidthSs = currColumn.hasSyllable() ? currColumn.getSyllableWidthSs() / 2.0 : 0;
        var gapSs = prevColumn.hasSyllable()
            ? prevColumn.getMinGapToNextSyllableSs()
            : LyricRenderMetrics.MIN_SYLLABLE_GAP_SS;

        return prevHalfWidthSs + gapSs + currHalfWidthSs;
    }

    /**
     * Calculates default spacing for columns when no lyrics are present.
     * <p>
     * Uses DEFAULT_COLUMN_GAP to provide comfortable spacing without lyrics.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Default spacing in ss
     */
    private static double calculateDefaultColumnSpacingSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        return prevColumn.getRightExtentSs() + DEFAULT_COLUMN_GAP_SS + Math.abs(currColumn.getLeftExtentSs());
    }

    /**
     * Checks if the current column needs to be pushed right for accidental clearance.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @param currXSs    Tentative X position for current column in staff spaces
     * @return true if accidental clearance would be violated
     */
    private static boolean needsAccidentalPush(
        ElementColumn prevColumn,
        ElementColumn currColumn,
        double currXSs) {

        // Only check if current note has an accidental
        return currColumn.getElement().getAccidental() != null;
    }

    /**
     * Ensures minimum horizontal spacing for a glissando between two columns.
     * Computes ledger-line-inclusive extents on-the-fly via the shared helper
     * on {@link GlissandoRenderer}. Returns the input spacing unchanged if no glissando
     * or if there is already enough room.
     * <p>
     * Geometry:
     * <pre>
     *   prev note origin          curr note origin
     *       |                         |
     *       |-- rightExtent -->|      |
     *       |            overhang-->| |<--overhang
     *       |                   |<->| |
     *       |                   gap   |
     *       |<------- spacingSs ----->|
     * </pre>
     * {@code glissRightExtent = max(rightExtent, noteheadWidth + overhang)}
     * <br>
     * {@code glissLeftExtent = min(leftExtent, -overhang)}
     * <br>
     * {@code gap = spacingSs + glissLeftExtent(curr) - glissRightExtent(prev)}
     * <br>
     * The glissando must fit within "gap".
     */
    private static double ensureGlissandoSpacing(
        ElementColumn prev, ElementColumn curr, double spacingSs
    ) {
        if (!prev.hasGlissando()) {
            return spacingSs;
        }

        // Compute ledger-line-inclusive extents on-the-fly
        var prevOverhang = NoteRenderer.getLedgerLineOverhangSs(prev.getElement());
        var prevGlissRight = prev.getRightExtentSs();

        if (prevOverhang > 0) {
            var noteheadWidthSs = NoteRenderer.getNoteheadRightEdgeSs(prev.getElement());
            prevGlissRight = Math.max(prevGlissRight, noteheadWidthSs + prevOverhang);
        }

        var currOverhang = NoteRenderer.getLedgerLineOverhangSs(curr.getElement());
        var currGlissLeft = curr.getLeftExtentSs();

        if (currOverhang > 0) {
            currGlissLeft = Math.min(currGlissLeft, -currOverhang);
        }

        var gap = spacingSs + currGlissLeft - prevGlissRight;
        var needed = GlissandoRenderer.MIN_HORIZONTAL_RESERVATION_SS;

        if (gap < needed) {
            spacingSs += (needed - gap);
        }

        return spacingSs;
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

        BeamGroupRange(int start, int end) {
            this.start = start;
            this.end = end;
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
    private List<BeamGroupRange> identifyBeamGroupRanges(List<ElementColumn> columns) {
        var ranges = new ArrayList<BeamGroupRange>();

        for (var i = 0; i < columns.size(); i++) {
            if (!columns.get(i).isBeamed()) {
                continue;
            }

            // Find the end of this consecutive beamed run
            var start = i;
            var end = i;

            for (var j = i + 1; j < columns.size(); j++) {
                if (columns.get(j).isBeamed()) {
                    end = j;
                } else {
                    break;
                }
            }

            ranges.add(new BeamGroupRange(start, end));
            i = end; // Skip to end of this group
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
    private @Nullable BeamGroupRange findBeamGroupStartingAt(
        List<? extends BeamGroupRange> ranges,
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
     * @param prevColumnXSs  X position in staff spaces of the column before the beam group
     */
    private void handleBeamGroup(
        List<ElementColumn> columns,
        BeamGroupRange range,
        double prevColumnXSs) {

        var beamColumns = columns.subList(range.start, range.end + 1);
        var columnCount = beamColumns.size();

        if (columnCount == 0) {
            return;
        }

        // Calculate where first column of beam group should go
        var prevColumn = columns.get(range.start - 1);
        var firstBeamColumn = beamColumns.get(0);
        var startXSs = calculateNextColumnXSs(prevColumn, firstBeamColumn);

        if (columnCount == 1) {
            // Single column "beam group" - just use normal spacing
            beamColumns.get(0).setXSs(startXSs);
            return;
        }

        // Step 1: Calculate tight internal spacing
        var tightGapSs = BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        var tightPositions = new ArrayList<Double>();
        var currentXSs = startXSs;

        tightPositions.add(currentXSs);

        for (var i = 1; i < columnCount; i++) {
            var prev = beamColumns.get(i - 1);
            var curr = beamColumns.get(i);

            // Use tight gap, ignoring syllables
            var spacingSs = prev.getRightExtentSs() + tightGapSs + Math.abs(curr.getLeftExtentSs());
            spacingSs = ensureGlissandoSpacing(prev, curr, spacingSs);
            currentXSs += spacingSs;
            tightPositions.add(currentXSs);
        }

        var tightTotalWidthSs = currentXSs - startXSs;

        // Step 2: Calculate lyric-driven width requirement
        double lyricRequiredWidthSs = 0;

        for (var i = 1; i < columnCount; i++) {
            var prev = beamColumns.get(i - 1);
            var curr = beamColumns.get(i);
            var lyricSpacingSs = calculateLyricSpacingSs(prev, curr);

            if (lyricSpacingSs > 0) {
                lyricRequiredWidthSs += lyricSpacingSs;
            } else {
                // No lyrics, use minimum spacing
                lyricRequiredWidthSs += calculateMinimumColumnSpacingSs(prev, curr);
            }
        }

        // Step 3: Determine final positions
        if (lyricRequiredWidthSs <= tightTotalWidthSs) {
            // Tight spacing is sufficient
            for (var i = 0; i < columnCount; i++) {
                beamColumns.get(i).setXSs(tightPositions.get(i));
            }
        } else {
            // Need to expand - distribute expansion evenly
            var expansionNeededSs = lyricRequiredWidthSs - tightTotalWidthSs;
            var expansionPerGapSs = expansionNeededSs / (columnCount - 1);

            beamColumns.get(0).setXSs(startXSs);
            currentXSs = startXSs;

            for (var i = 1; i < columnCount; i++) {
                var tightSpacingSs = tightPositions.get(i) - tightPositions.get(i - 1);
                var expandedSpacingSs = tightSpacingSs + expansionPerGapSs;

                currentXSs += expandedSpacingSs;
                beamColumns.get(i).setXSs(currentXSs);
            }
        }
    }
}
