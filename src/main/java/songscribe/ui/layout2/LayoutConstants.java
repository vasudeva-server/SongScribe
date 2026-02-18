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

/**
 * Centralized layout constants for the engraving system.
 * <p>
 * All spacing values are defined in MU (measure units), where 1 MU = 4 pixels.
 * This follows the Gould/Ross principle that spacing is non-proportional and
 * lyric-driven rather than based on rhythmic duration.
 * <p>
 * Key principles from Gould/Ross:
 * <ul>
 *   <li>Spacing is non-proportional - rhythmic value does not determine horizontal distance</li>
 *   <li>Lyrics dominate spacing decisions - optical clarity outweighs rhythmic proportionality</li>
 *   <li>Uneven spacing is expected and correct</li>
 *   <li>Accidentals only increase spacing when minimum clearance would be violated</li>
 * </ul>
 */
public final class LayoutConstants {

    private LayoutConstants() {
        // Prevent instantiation
    }

    // ==========================================================================
    // BASE UNIT
    // ==========================================================================

    /**
     * Base measure unit: 1 MU = 4 pixels.
     */
    public static final double MU = 4.0;

    /**
     * Converts measure units to pixels.
     *
     * @param mu Value in measure units
     * @return Value in pixels
     */
    public static double px(double mu) {
        return mu * MU;
    }

    /**
     * Converts measure units to pixels, rounded to nearest integer.
     *
     * @param mu Value in measure units
     * @return Value in pixels (rounded)
     */
    public static int pxInt(double mu) {
        return (int) Math.round(mu * MU);
    }

    // ==========================================================================
    // HORIZONTAL SPACING - Note Columns
    // ==========================================================================

    /**
     * Distance from right extent of clef/key signature to first note column.
     * Per Gould/Ross: provides visual separation between staff beginning and music.
     */
    public static final double FIRST_NOTE_OFFSET = 11.5;  // 46px

    /**
     * Minimum horizontal gap between adjacent note columns.
     * This is the absolute minimum; lyric spacing may require more.
     */
    public static final double MIN_COLUMN_GAP = 0.25;  // 1px

    /**
     * Minimum horizontal gap between syllables.
     * This ensures lyric text remains readable.
     * Note: This value will be tuned empirically during implementation.
     */
    public static final double MIN_SYLLABLE_GAP = 0.5;  // 2px (TBD)

    /**
     * Minimum clearance for accidentals from previous column's right extent.
     * Accidentals only push spacing when this minimum would be violated.
     */
    public static final double ACCIDENTAL_CLEARANCE = 0.25;  // 1px

    // ==========================================================================
    // HORIZONTAL SPACING - Grace Notes
    // ==========================================================================

    /**
     * Grace notes borrow space from the main note's left side.
     * They must not push earlier notes leftward.
     * If insufficient space, grace notes compress (they are subordinate).
     */
    public static final double GRACE_NOTE_MIN_WIDTH = 2.0;  // 8px per grace note

    /**
     * Gap between grace notes in a group.
     */
    public static final double GRACE_NOTE_GAP = 0.5;  // 2px

    // ==========================================================================
    // HORIZONTAL SPACING - Beam Groups
    // ==========================================================================

    /**
     * Minimum internal spacing within a beam group (tight, regular spacing).
     * Beam groups may widen if lyrics under them require it.
     */
    public static final double BEAM_GROUP_MIN_INTERNAL_GAP = 3.0;  // 12px

    /**
     * Minimum gap between adjacent beam groups or between a beam group and a rest.
     */
    public static final double BEAM_GROUP_EXTERNAL_GAP = 1.0;  // 4px

    // ==========================================================================
    // HORIZONTAL SPACING - Barlines
    // ==========================================================================

    /**
     * Space before a barline.
     */
    public static final double BARLINE_GAP_BEFORE = 2.0;  // 8px

    /**
     * Space after a barline.
     */
    public static final double BARLINE_GAP_AFTER = 3.0;  // 12px

    // ==========================================================================
    // HORIZONTAL SPACING - Breath Marks
    // ==========================================================================

    /**
     * Slight space after a note with a breath mark.
     * Breath marks participate lightly in spacing.
     */
    public static final double BREATH_MARK_GAP = 0.5;  // 2px

    // ==========================================================================
    // VERTICAL STACKING - Layer Margins
    // ==========================================================================

    /**
     * Margin from note bounds to articulations layer.
     */
    public static final double ARTICULATION_MARGIN = 1.0;  // 4px

    /**
     * Margin from articulations to trill layer.
     */
    public static final double TRILL_MARGIN = 0.5;  // 2px

    /**
     * Margin from trill to fermata layer.
     */
    public static final double FERMATA_MARGIN = 0.5;  // 2px

    /**
     * Margin from fermata to dynamics layer.
     */
    public static final double DYNAMICS_MARGIN = 0.5;  // 2px

    /**
     * Margin from dynamics to endings layer.
     */
    public static final double ENDING_MARGIN = 1.0;  // 4px

    /**
     * Margin from endings to tempo layer.
     */
    public static final double TEMPO_MARGIN = 1.0;  // 4px

    /**
     * Margin from tempo to annotations layer.
     */
    public static final double ANNOTATION_MARGIN = 0.5;  // 2px

    /**
     * Margin from annotations to attribution layer.
     */
    public static final double ATTRIBUTION_MARGIN = 1.0;  // 4px

    // ==========================================================================
    // VERTICAL STACKING - Lyrics
    // ==========================================================================

    /**
     * Distance from lowest note bounding area to lyrics baseline (ascent).
     * Per plan: lyrics are below the staff, 2.5 MU below lowest note bounding area.
     */
    public static final double LYRICS_BASELINE_OFFSET = 2.5;  // 10px

    // ==========================================================================
    // STAFF DIMENSIONS
    // ==========================================================================

    /**
     * Pixels between staff lines.
     */
    public static final int STAFF_LINE_SPACING = 8;

    /**
     * Number of staff lines.
     */
    public static final int STAFF_LINE_COUNT = 5;

    /**
     * Total height of the 5-line staff (4 gaps).
     */
    public static final int STAFF_HEIGHT = (STAFF_LINE_COUNT - 1) * STAFF_LINE_SPACING;  // 32px

    // ==========================================================================
    // LINE JUSTIFICATION
    // ==========================================================================

    /**
     * When compressing to fit margin, gaps cannot go below this ratio of their
     * calculated values. Below this, the note is rejected.
     */
    public static final double MIN_COMPRESSION_RATIO = 0.5;

    /**
     * Absolute minimum column gap during compression.
     * Even under maximum compression, gaps cannot go below this.
     */
    public static final double COMPRESSED_MIN_COLUMN_GAP = 0.25;  // 1px

    /**
     * Absolute minimum syllable gap during compression.
     */
    public static final double COMPRESSED_MIN_SYLLABLE_GAP = 0.25;  // 1px
}
