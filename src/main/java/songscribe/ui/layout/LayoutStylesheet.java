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
 * Centralized layout constants (CSS-like stylesheet).
 * <p>
 * All spacing values are defined in MU (measure units), where 1 MU = 4 pixels.
 * Values must be multiples of 0.25 so that {@code value * MU} is always an integer.
 * <p>
 * Common values:
 * <ul>
 *   <li>0.25 MU = 1px</li>
 *   <li>0.5 MU = 2px</li>
 *   <li>1.0 MU = 4px</li>
 *   <li>2.0 MU = 8px (1 staff line)</li>
 *   <li>4.0 MU = 16px (2 staff lines)</li>
 * </ul>
 * <p>
 * Terminology:
 * <ul>
 *   <li><b>Padding</b>: Expands element bounds for hit testing (inside element)</li>
 *   <li><b>Margin</b>: Space between elements, enforced by layout (outside element)</li>
 * </ul>
 */
public final class LayoutStylesheet {

    private LayoutStylesheet() {
        // Prevent instantiation
    }

    // ==========================================================================
    // BASE UNIT
    // ==========================================================================

    /**
     * Base measure unit: 1 MU = 4 pixels (like Tailwind's base unit)
     */
    public static final double MU = 4.0;

    /**
     * Converts measure units to pixels.
     * All MU values should be multiples of 0.25 so result is always an integer.
     *
     * @param muUnits Value in measure units
     * @return Value in pixels (rounded to nearest int)
     */
    public static int px(double muUnits) {
        return (int) Math.round(muUnits * MU);
    }

    // ==========================================================================
    // SECTION ELEMENTS (block flow layout)
    // ==========================================================================

    // --- Title ---
    /**
     * Padding inside title bounds (for hit testing)
     */
    public static final double TITLE_PADDING = 0;

    /**
     * Margin from title bottom to next section
     */
    public static final double TITLE_MARGIN_BOTTOM = 4.0;  // 16px

    // --- Attribution ---
    /**
     * Padding inside attribution bounds
     */
    public static final double ATTRIBUTION_PADDING = 0;

    /**
     * Margin from attribution bottom to score
     */
    public static final double ATTRIBUTION_MARGIN_BOTTOM = 4.0;  // 16px

    // --- Score (contains staff lines) ---
    /**
     * Margin from previous section to score top
     */
    public static final double SCORE_MARGIN_TOP = 3.0;  // 12px

    // --- Line (staff system) ---
    /**
     * Margin between staff lines
     */
    public static final double LINE_MARGIN_BOTTOM = 4.0;  // 16px

    // --- Lyrics Block (below score) ---
    /**
     * Margin from score bottom to lyrics block
     */
    public static final double LYRICS_BLOCK_MARGIN_TOP = 10.0;  // 40px

    /**
     * Margin from primary lyrics to Bangla lyrics
     */
    public static final double BANGLA_MARGIN_TOP = 4.0;  // 16px

    /**
     * Margin from Bangla lyrics to translation
     */
    public static final double TRANSLATION_MARGIN_TOP = 4.0;  // 16px

    // --- Footnotes ---
    /**
     * Minimum margin above footnotes section
     */
    public static final double FOOTNOTES_MIN_MARGIN_TOP = 10.0;  // 40px

    // ==========================================================================
    // ABOVE-STAFF ELEMENTS (reference-based positioning)
    // ==========================================================================

    // --- Annotation Region ---
    /**
     * Reserved vertical space for above-staff elements
     */
    public static final double ANNOTATION_REGION_MARGIN = 6.0;  // 24px

    /**
     * Pixel value of annotation margin (for backward compatibility)
     */
    public static final int ANNOTATION_MARGIN = px(ANNOTATION_REGION_MARGIN);

    // --- Tempo ---
    /**
     * Padding around tempo marking (for hit testing)
     */
    public static final double TEMPO_PADDING = 0.5;  // 2px

    /**
     * Margin from reference point to tempo marking
     */
    public static final double TEMPO_MARGIN = 2.0;  // 8px

    // --- Beat Change ---
    /**
     * Padding around beat/time signature change
     */
    public static final double BEAT_CHANGE_PADDING = 0.5;  // 2px

    /**
     * Margin from reference point to beat change
     */
    public static final double BEAT_CHANGE_MARGIN = 2.0;  // 8px

    // --- First/Second Endings (Volta brackets) ---
    /**
     * Padding around ending bracket
     */
    public static final double ENDING_PADDING = 0.5;  // 2px

    /**
     * Margin from reference point to ending bracket
     */
    public static final double ENDING_MARGIN = 1.5;  // 6px

    // --- Trill ---
    /**
     * Padding around trill marking
     */
    public static final double TRILL_PADDING = 0.5;  // 2px

    /**
     * Margin from reference point to trill
     */
    public static final double TRILL_MARGIN = 2.0;  // 8px

    // --- Fermata ---
    /**
     * Padding around fermata marking
     */
    public static final double FERMATA_PADDING = 0.5;  // 2px

    /**
     * Margin from note to fermata
     */
    public static final double FERMATA_MARGIN = 1.0;  // 4px

    // --- Annotations (text above staff) ---
    /**
     * Padding around text annotation
     */
    public static final double ANNOTATION_ABOVE_PADDING = 0.5;  // 2px

    /**
     * Margin from reference point to annotation
     */
    public static final double ANNOTATION_ABOVE_MARGIN = 1.0;  // 4px

    // ==========================================================================
    // BELOW-STAFF ELEMENTS
    // ==========================================================================

    // --- Dynamics (p, f, ff, etc.) ---
    /**
     * Padding around dynamic marking
     */
    public static final double DYNAMICS_PADDING = 0.5;  // 2px

    /**
     * Margin from staff bottom to dynamics
     */
    public static final double DYNAMICS_MARGIN = 2.0;  // 8px

    // --- Crescendo/Diminuendo hairpins ---
    /**
     * Padding around hairpin
     */
    public static final double CRESC_DIM_PADDING = 0.25;  // 1px

    /**
     * Margin from reference point to hairpin
     */
    public static final double CRESC_DIM_MARGIN = 1.0;  // 4px

    // --- Lyrics Row (under staff) ---
    /**
     * Padding around lyrics row
     */
    public static final double LYRICS_ROW_PADDING = 0;

    /**
     * Margin from staff bottom to lyrics
     */
    public static final double LYRICS_ROW_MARGIN = 2.0;  // 8px

    /**
     * Margin from lowest note bounds to lyrics baseline (ascent).
     * Per Phase 8 spec: ascent 2.5 MU below lowest note bounding box.
     */
    public static final double LYRICS_BASELINE_MARGIN = 2.5;  // 10px

    // ==========================================================================
    // NOTE ELEMENTS
    // ==========================================================================

    // --- Note (hit testing) ---
    /**
     * Padding around note for hit testing
     */
    public static final double NOTE_PADDING = 1.0;  // 4px

    // --- Articulations ---
    /**
     * Padding around articulation marking
     */
    public static final double ARTICULATION_PADDING = 0.5;  // 2px

    /**
     * Margin from note head to first articulation
     */
    public static final double ARTICULATION_MARGIN_FROM_NOTE = 1.0;  // 4px

    /**
     * Margin between stacked articulations (staccato → accent)
     */
    public static final double ARTICULATION_INTER_MARGIN = 0.75;  // 3px

    // --- Ties ---
    /**
     * Padding around tie curve
     */
    public static final double TIE_PADDING = 0.5;  // 2px

    /**
     * Margin from articulations to tie
     */
    public static final double TIE_MARGIN = 1.5;  // 6px

    /**
     * Distance from note head to tie arc endpoint
     */
    public static final double TIE_NOTE_HEAD_OFFSET = 0.25;  // 1px

    /**
     * Minimum arc height for ties
     */
    public static final double TIE_MIN_ARC_HEIGHT = 1.5;  // 6px

    /**
     * Reference horizontal distance for arc height scaling (in pixels)
     */
    public static final double TIE_REFERENCE_DISTANCE = 50.0;  // 50px baseline

    /**
     * Arc height scaling factor for ties.
     * Arc height = minHeight + sqrt(distance / reference) * heightScale
     */
    public static final double TIE_HEIGHT_SCALE = 4.0;  // 4px additional per sqrt unit

    /**
     * Margin from tie to articulations (area-based collision)
     */
    public static final double TIE_ARTICULATION_MARGIN = 0.5;  // 2px

    // --- Tuplets ---
    /**
     * Padding around tuplet bracket
     */
    public static final double TUPLET_PADDING = 0.5;  // 2px

    /**
     * Margin from reference point to tuplet bracket
     */
    public static final double TUPLET_MARGIN = 1.25;  // 5px

    /**
     * Margin from beam to tuplet number (measured perpendicular to beam)
     */
    public static final double TUPLET_BEAM_MARGIN = 0.5;  // 2px

    /**
     * Margin from highest note/articulation bounds to tuplet bracket
     */
    public static final double TUPLET_BRACKET_MARGIN = 0.5;  // 2px

    /**
     * Minimum margin from staff top to tuplet bracket
     */
    public static final double TUPLET_MIN_STAFF_MARGIN = 1.0;  // 4px

    /**
     * Gap on each side of tuplet number in bracket (in pixels)
     */
    public static final double TUPLET_NUMBER_GAP = 1.0;  // 1px

    /**
     * Overhang of bracket beyond first and last note heads (in pixels)
     */
    public static final double TUPLET_BRACKET_OVERHANG = 1.0;  // 1px

    /**
     * Scale factor for tuplet number in non-beamed (bracket) tuplets
     */
    public static final double TUPLET_BRACKET_NUMBER_SCALE = 0.9;  // 90% font size

    // --- Beams ---
    /**
     * Padding around beam
     */
    public static final double BEAM_PADDING = 0;

    /**
     * Margin between beam levels (8th → 16th)
     */
    public static final double BEAM_INTER_MARGIN = 1.5;  // 6px

    // ==========================================================================
    // HORIZONTAL SPACING
    // ==========================================================================

    // --- Accidentals ---
    /**
     * Padding around accidental glyph
     */
    public static final double ACCIDENTAL_PADDING = 0.75;  // 3px

    /**
     * Margin between accidental and note head
     */
    public static final double ACCIDENTAL_INTER_MARGIN = 0.25;  // 1px

    // --- Note Type Spacing (right margin after note) ---
    /**
     * Right margin after semibreve (whole note)
     */
    public static final double SEMIBREVE_MARGIN_RIGHT = 17.5;  // 70px

    /**
     * Right margin after minim (half note)
     */
    public static final double MINIM_MARGIN_RIGHT = 12.5;  // 50px

    /**
     * Right margin after crotchet (quarter note)
     */
    public static final double CROTCHET_MARGIN_RIGHT = 8.75;  // 35px

    /**
     * Right margin after quaver (eighth note)
     */
    public static final double QUAVER_MARGIN_RIGHT = 6.25;  // 25px

    /**
     * Right margin after semiquaver (sixteenth note)
     */
    public static final double SEMIQUAVER_MARGIN_RIGHT = 6.25;  // 25px

    /**
     * Right margin after barline
     */
    public static final double BARLINE_MARGIN_RIGHT = 15.0;  // 60px

    /**
     * Right margin after breath mark
     */
    public static final double BREATH_MARK_MARGIN_RIGHT = 3.75;  // 15px

    // --- Syllables ---
    /**
     * Horizontal padding around syllable text
     */
    public static final double SYLLABLE_PADDING_H = 0.5;  // 2px

    /**
     * Left margin before syllable
     */
    public static final double SYLLABLE_MARGIN_LEFT = 0.5;  // 2px

    /**
     * Right margin after syllable
     */
    public static final double SYLLABLE_MARGIN_RIGHT = 0.5;  // 2px

    // --- First Note ---
    /**
     * X position of first note from left edge
     */
    public static final double FIRST_NOTE_X = 25.0;  // 100px

    // ==========================================================================
    // STAFF DIMENSIONS
    // ==========================================================================

    /**
     * Pixels between staff lines
     */
    public static final int STAFF_LINE_Y_OFFSET = 8;

    /**
     * Number of staff lines
     */
    public static final int STAFF_LINE_COUNT = 5;

    /**
     * Staff lines above middle line for ledger lines
     */
    public static final int STAFF_LINES_ABOVE = 3;

    /**
     * Staff lines below middle line for ledger lines
     */
    public static final int STAFF_LINES_BELOW = 4;

    /**
     * Height of 5-line staff (4 gaps × 8 pixels)
     */
    public static final int STAFF_HEIGHT = 4 * STAFF_LINE_Y_OFFSET;  // 32px

    /**
     * Note Y offset (half of staff line spacing)
     */
    public static final double NOTE_Y_OFFSET = STAFF_LINE_Y_OFFSET / 2.0;  // 4px

    // ==========================================================================
    // LINE ELEMENT DEFAULT Y POSITIONS (relative to middleLineY)
    // ==========================================================================
    // These are the default Y offsets from the middle staff line (B line).
    // Negative values = above staff, positive = below staff.
    // Used by Line.java for initial values and by layout calculation for offset values.
    // Note: These may be replaced by dynamic calculation in future phases.

    /**
     * Default tempo Y for first line (-5 staff lines above middle)
     */
    public static final int TEMPO_DEFAULT_Y_FIRST_LINE = -5 * STAFF_LINE_Y_OFFSET;  // -40px

    /**
     * Default tempo Y for subsequent lines (-3 staff lines above middle)
     */
    public static final int TEMPO_DEFAULT_Y_OTHER_LINES = -3 * STAFF_LINE_Y_OFFSET;  // -24px

    /**
     * Default beat change Y position (-3 staff lines above middle)
     */
    public static final int BEAT_CHANGE_DEFAULT_Y = -3 * STAFF_LINE_Y_OFFSET;  // -24px

    /**
     * Default lyrics Y position (below staff)
     */
    public static final int LYRICS_DEFAULT_Y = 50;

    /**
     * Default first/second ending Y position (above staff)
     */
    public static final int ENDING_DEFAULT_Y = -25;

    /**
     * Default trill Y position (above staff)
     */
    public static final int TRILL_DEFAULT_Y = -27;
}
