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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.smufl.Engraving;


/**
 * Calculates horizontal X positions for note columns following Gould/Ross engraving principles.
 * <p>
 * Key principles:
 * <ul>
 *   <li>Non-proportional spacing - rhythmic value does not determine horizontal distance</li>
 *   <li>Lyric-driven spacing - syllable widths determine column gaps</li>
 *   <li>Minimum gap measured to a column's leftmost glyph - a note shifts for an accidental only when needed</li>
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
     * Minimum horizontal gap between adjacent note columns — the single minimum gap
     * applied wherever a gap can be squeezed (normal spacing and justification alike).
     * A column's left extent includes its accidental, so the minimum spacing is measured
     * to the leftmost glyph (the accidental when one is present); this is what makes a
     * note shift right only when the accidental would otherwise come closer than this gap
     * to the previous element. Lyric spacing may require more.
     */
    public static final double MIN_COLUMN_GAP_SS = 1.0;  // 8px
    /**
     * Default horizontal gap between adjacent note columns when no lyrics are present.
     * Provides comfortable spacing for music without lyrics.
     */
    public static final double DEFAULT_COLUMN_GAP_SS = 2.5;  // 20px
    /**
     * Gap between a grace note and its host note.
     */
    public static final double GRACE_NOTE_GAP_SS = 2.0;  // 16px
    /**
     * Tight internal spacing within a beam group, used between two beamed notes that are both
     * shorter than an eighth note (sixteenths and faster). A pair touching an eighth note (or
     * longer) instead packs at {@link #DEFAULT_COLUMN_GAP_SS}, the same gap as unbeamed notes;
     * the longer note of the pair governs (refs #418).
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
     * How much further left the renderer places an accidental than the layout's left extent does.
     * The renderer pads by {@link NoteGeometry#ACCIDENTAL_PADDING_SS} while the layout's left
     * extent uses the smaller {@link ElementColumnBuilder#ACCIDENTAL_GAP_SS}. A glissando reserves
     * against the renderer's edge, so this delta is added to the reserved gap (refs #443).
     */
    private static final double ACCIDENTAL_RENDER_LEFT_DELTA_SS =
        NoteGeometry.ACCIDENTAL_PADDING_SS - ElementColumnBuilder.ACCIDENTAL_GAP_SS;

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

        // Calculate first note position, anchoring the column's left edge — not just the note
        // head — at FIRST_NOTE_OFFSET from the header, so accidentals don't crowd the key signature.
        var firstXSs = calculateFirstNoteXSs(line);
        var firstColumn = columns.getFirst();
        firstXSs -= firstColumn.getLeftExtentSs();
        firstColumn.setXSs(firstXSs);

        var startIndex = 1;

        // A beam group anchored at the very first column must still get beam-internal spacing.
        // The main loop starts at index 1, so it would never see a group that starts at column 0;
        // handle that group here, anchoring it at the already-positioned first note.
        var firstBeamGroupRange = findBeamGroupStartingAt(beamGroupRanges, 0);

        if (firstBeamGroupRange != null) {
            handleBeamGroup(columns, firstBeamGroupRange, firstXSs);
            startIndex = firstBeamGroupRange.end + 1;
        }

        // Process remaining columns
        for (var i = startIndex; i < columns.size(); i++) {
            // Check if we're starting a beam group
            var beamGroupRange = findBeamGroupStartingAt(beamGroupRanges, i);

            if (beamGroupRange != null) {
                // Process entire beam group
                var prevColumn = columns.get(i - 1);
                var startXSs = calculateNextColumnXSs(prevColumn, columns.get(beamGroupRange.start));
                handleBeamGroup(columns, beamGroupRange, startXSs);
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

        double spacingSs;

        if (prevColumn.getElement().getType().isGraceNote()) {
            // Grace note → host note: use tight grace note spacing. The comfortable gap is measured
            // to the host note head — the accidental is excluded so it does not widen the gap — while
            // the geometric minimum, which does use the full left extent, acts as a hard floor to
            // ensure no overlap (refs #418).
            var comfortableSpacingSs = prevColumn.getRightExtentSs() + GRACE_NOTE_GAP_SS;
            var minimumSpacingSs = calculateMinimumColumnSpacingSs(prevColumn, currColumn);
            spacingSs = Math.max(comfortableSpacingSs, minimumSpacingSs);
        } else {
            // Calculate minimum spacing (from previous column's right extent)
            var minimumSpacingSs = calculateMinimumColumnSpacingSs(prevColumn, currColumn);

            // Calculate lyric-driven spacing requirement
            var lyricSpacingSs = calculateLyricSpacingSs(prevColumn, currColumn);

            // Use default comfortable spacing as a floor, then expand further if lyrics require it.
            // This prevents the case where only one side has a lyric from producing
            // tighter-than-default note-head-to-note-head spacing. The minimum-spacing floor (which
            // uses the full left extent, accidental included) already keeps MIN_COLUMN_GAP to the
            // current column's leftmost glyph, so a note shifts right only when its accidental would
            // otherwise crowd the previous element — no separate accidental push is needed.
            var defaultSpacingSs = calculateDefaultColumnSpacingSs(prevColumn);
            spacingSs = Math.max(minimumSpacingSs, Math.max(defaultSpacingSs, lyricSpacingSs));
        }

        // Ensure enough horizontal room for a connecting glissando. This applies to both grace→host
        // and regular note→note pairs: a glissando on the previous note must clear the next note's
        // left-side glyphs (accidental included) and still keep its minimum visible length (refs #443).
        spacingSs = ensureGlissandoSpacing(prevColumn, currColumn, spacingSs);

        return prevColumn.getXSs() + spacingSs;
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
     * Formula: (prevSyllableWidth / 2) + prevColumn.minGapToNextSyllable + (currSyllableWidth / 2)
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
        // Every column reserves a gap to the next syllable (the lyric space width, or the hyphen
        // cell width for hyphenated syllables), so this holds even when the previous element
        // carries no lyric of its own.
        var gapSs = prevColumn.getMinGapToNextSyllableSs();

        return prevHalfWidthSs + gapSs + currHalfWidthSs;
    }

    /**
     * Calculates default spacing for columns when no lyrics are present.
     * <p>
     * Uses DEFAULT_COLUMN_GAP to provide comfortable spacing without lyrics. The comfortable
     * gap is measured to the current note head, not to its accidental or augmentation dots,
     * so neither pushes the next element beyond the comfortable default. The minimum-spacing
     * floor — which uses the full right extent including dots — takes over and shifts the
     * next element only when the dot would otherwise come closer than {@link #MIN_COLUMN_GAP_SS}
     * to it. This mirrors how accidentals on the left are handled (refs #418, #441).
     *
     * @param prevColumn Previous column
     * @return Default spacing in ss
     */
    private static double calculateDefaultColumnSpacingSs(ElementColumn prevColumn) {
        return prevColumn.getRightExtentExcludingAugmentationSs() + DEFAULT_COLUMN_GAP_SS;
    }

    /**
     * Ensures minimum horizontal spacing for a glissando between two columns.
     * Returns the input spacing unchanged if no glissando or if there is already enough room.
     * <p>
     * Computes: {@code gap = spacingSs + currLeft - prevRight}, where {@code currLeft} is
     * adjusted by {@link #ACCIDENTAL_RENDER_LEFT_DELTA_SS} when the current note has an accidental
     * (the renderer places the accidental slightly further left than the layout extent).
     * If {@code gap < }{@link NoteGeometry#MIN_GLISSANDO_RESERVATION_SS}, spacing is widened
     * to close the difference. Ledger lines are excluded from both extents.
     */
    private static double ensureGlissandoSpacing(
        ElementColumn prev, ElementColumn curr, double spacingSs
    ) {
        if (!prev.hasGlissando()) {
            return spacingSs;
        }

        var prevGlissRight = prev.getRightExtentSs();
        var currElement = curr.getElement();
        var currGlissLeft = curr.getLeftExtentSs();

        // The glissando line ends at the next note's left-side area edge. The renderer places the
        // accidental slightly further left than the layout left extent does, so reserve against the
        // renderer's edge — otherwise the reserved gap is too small and the line falls just below
        // its minimum visible length (refs #443).
        if (currElement.getAccidental() != null) {
            currGlissLeft -= ACCIDENTAL_RENDER_LEFT_DELTA_SS;
        }

        var gap = spacingSs + currGlissLeft - prevGlissRight;
        var needed = NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;

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
    private record BeamGroupRange(int start, int end) {
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
            var startColumn = columns.get(i);

            if (!startColumn.isBeamed()) {
                continue;
            }

            // Extend the run only while consecutive columns belong to the SAME beam group.
            // Two adjacent beam groups (e.g. a quaver group followed by a semiquaver group)
            // must not be merged, or the first note of the next group would be spaced as if
            // it continued the previous beam (refs #418).
            var start = i;
            var end = i;
            var beamGroupId = startColumn.getBeamGroupId();

            for (var j = i + 1; j < columns.size(); j++) {
                var nextColumn = columns.get(j);

                if (nextColumn.isBeamed() && nextColumn.getBeamGroupId() == beamGroupId) {
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
        List<BeamGroupRange> ranges,
        int index) {

        for (var range : ranges) {
            if (range.start == index) {
                return range;
            }
        }

        return null;
    }

    /**
     * Returns the comfortable internal gap between two adjacent beamed columns.
     * The longer note of the pair governs the gap: a pair touching an eighth note (or longer)
     * packs at the default note-to-note gap, while the tighter beam-internal gap applies only
     * when both notes are shorter than an eighth (sixteenths or faster). {@link LayoutEngine#beamCount}
     * returns 1 for an eighth note (quaver) and a larger value for shorter notes (refs #418).
     *
     * @param prev Previous beamed column
     * @param curr Current beamed column
     * @return Internal gap in ss
     */
    private static double beamInternalGapSs(ElementColumn prev, ElementColumn curr) {
        var bothShorterThanEighth = LayoutEngine.beamCount(prev.getElement()) > 1
            && LayoutEngine.beamCount(curr.getElement()) > 1;

        return bothShorterThanEighth ? BEAM_GROUP_MIN_INTERNAL_GAP_SS : DEFAULT_COLUMN_GAP_SS;
    }

    /**
     * Handles spacing for a beam group.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Place the first column at the caller-supplied start position</li>
     *   <li>Calculate tight internal spacing (ignore lyrics)</li>
     *   <li>Calculate each gap's lyric-driven spacing requirement</li>
     *   <li>Distribute any total expansion evenly to keep the beam regular, while clamping each
     *       gap up to its own lyric requirement so wide syllables never overlap (refs #445)</li>
     * </ol>
     *
     * @param columns        All columns
     * @param range          Beam group range
     * @param startXSs       X position in staff spaces of the beam group's first column
     */
    private void handleBeamGroup(
        List<ElementColumn> columns,
        BeamGroupRange range,
        double startXSs) {

        var beamColumns = columns.subList(range.start, range.end + 1);
        var columnCount = beamColumns.size();

        if (columnCount == 0) {
            return;
        }

        if (columnCount == 1) {
            // Single column "beam group" - just use normal spacing
            beamColumns.getFirst().setXSs(startXSs);
            return;
        }

        // Step 1: Calculate baseline internal spacing (before any lyric expansion)
        var tightPositions = new ArrayList<Double>();
        var currentXSs = startXSs;

        tightPositions.add(currentXSs);

        for (var i = 1; i < columnCount; i++) {
            var prev = beamColumns.get(i - 1);
            var curr = beamColumns.get(i);

            // Use the per-pair beam-internal gap, ignoring syllables. The comfortable gap is
            // measured to the note head — the accidental is excluded so it does not inflate
            // beam-internal spacing — while the minimum, which uses the full left extent, is a
            // hard geometric floor (refs #418).
            var internalGapSs = beamInternalGapSs(prev, curr);
            var comfortableSpacingSs = prev.getRightExtentSs() + internalGapSs;
            var minimumSpacingSs = calculateMinimumColumnSpacingSs(prev, curr);
            var spacingSs = Math.max(comfortableSpacingSs, minimumSpacingSs);
            spacingSs = ensureGlissandoSpacing(prev, curr, spacingSs);
            currentXSs += spacingSs;
            tightPositions.add(currentXSs);
        }

        var tightTotalWidthSs = currentXSs - startXSs;

        // Step 2: Calculate the lyric-driven spacing requirement for each gap. Keeping the
        // per-gap values (not just their sum) lets Step 3 guarantee that every gap individually
        // clears its own syllables, rather than trusting an even share of the total (refs #445).
        var lyricRequiredPerGapSs = new ArrayList<Double>();

        for (var i = 1; i < columnCount; i++) {
            var prev = beamColumns.get(i - 1);
            var curr = beamColumns.get(i);
            var lyricSpacingSs = calculateLyricSpacingSs(prev, curr);
            // No lyrics under this gap, fall back to the geometric minimum.
            var gapRequirementSs = lyricSpacingSs > 0
                ? lyricSpacingSs
                : calculateMinimumColumnSpacingSs(prev, curr);

            lyricRequiredPerGapSs.add(gapRequirementSs);
        }

        var lyricRequiredWidthSs = lyricRequiredPerGapSs.stream().mapToDouble(Double::doubleValue).sum();

        // Step 3: Determine final positions. Distribute any total expansion evenly to keep the
        // beam looking regular, but never let a gap fall below its own lyric requirement —
        // distributing only the total can starve a gap whose syllables are wider than average
        // (the narrow semiquaver-to-semiquaver gap), which is what made lyrics overlap (refs #445).
        var expansionNeededSs = lyricRequiredWidthSs - tightTotalWidthSs;
        var expansionPerGapSs = expansionNeededSs > 0 ? expansionNeededSs / (columnCount - 1) : 0;

        beamColumns.getFirst().setXSs(startXSs);
        currentXSs = startXSs;

        for (var i = 1; i < columnCount; i++) {
            var tightSpacingSs = tightPositions.get(i) - tightPositions.get(i - 1);
            var spacingSs = Math.max(tightSpacingSs + expansionPerGapSs, lyricRequiredPerGapSs.get(i - 1));

            currentXSs += spacingSs;
            beamColumns.get(i).setXSs(currentXSs);
        }
    }
}
