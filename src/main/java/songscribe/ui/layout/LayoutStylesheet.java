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

import module java.desktop;

import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;
import songscribe.smufl.GlyphAnchors;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.renderer.LineThickness;
import songscribe.ui.FlatLafProps;

/**
 * Centralized layout constants (CSS-like stylesheet).
 * <p>
 * All spacing values are in staff-space (ss) units, where 1 ss equals
 * the distance between two adjacent staff lines. The conversion to pixels
 * is handled by {@link ScaleContext} (default: 1 ss = 8 px).
 * <p>
 * Terminology:
 * <ul>
 *   <li><b>Padding</b>: Expands element bounds for hit testing (inside element)</li>
 *   <li><b>Margin</b>: Space between elements, enforced by layout (outside element)</li>
 * </ul>
 */
@SuppressWarnings("NullAway.Init")
public final class LayoutStylesheet {

    private LayoutStylesheet() {
        // Prevent instantiation
    }

    // ==========================================================================
    // COLORS
    // ==========================================================================

    /**
     * Returns the screen background color of the score page.
     * Read from UIManager to support theming; callers should not cache this value.
     */
    public static Color getScreenBackground() {
        return FlatLafProps.get(FlatLafKeys.SCORE_PAGE_SCREEN_BACKGROUND);
    }

    /**
     * Returns the print/export background color of the score page (always white).
     * Read from UIManager to support theming; callers should not cache this value.
     */
    public static Color getPrintBackground() {
        return FlatLafProps.get(FlatLafKeys.SCORE_PAGE_PRINT_BACKGROUND);
    }

    // ==========================================================================
    // SECTION ELEMENTS (block flow layout)
    // ==========================================================================

    // --- Title ---
    /**
     * Margin from title bottom to next section
     */
    public static final double TITLE_MARGIN_BOTTOM_SS = 2.0;  // 16px

    // --- Attribution ---
    /**
     * Margin from attribution bottom to score
     */
    public static final double ATTRIBUTION_MARGIN_BOTTOM_SS = 2.0;  // 16px

    // --- Score (contains staff lines) ---
    /**
     * Margin from previous section to score top
     */
    public static final double SCORE_MARGIN_TOP_SS = 1.5;  // 12px

    // --- Line (staff system) ---
    /**
     * Margin between staff lines
     */
    public static final double LINE_MARGIN_BOTTOM_SS = 2.0;  // 16px

    // --- Footnotes ---
    /**
     * Minimum margin above footnotes section
     */
    public static final double FOOTNOTES_MIN_MARGIN_TOP_SS = 5.0;  // 40px

    // ==========================================================================
    // NOTE DECORATIONS (vertical stacking margin)
    // ==========================================================================

    /**
     * Vertical margin between hairpins and elements below during stacking.
     */
    public static final double HAIRPIN_MARGIN_SS = 1.0;  // 8px

    /**
     * Default vertical margin between note decorations during stacking.
     * Used for articulations, fermata, trill, and text dynamics.
     */
    public static final double NOTE_DECORATION_MARGIN_SS = 0.5;  // 4px

    /**
     * Vertical margin between single-note decorations and upward-arcing tie curves.
     * Smaller than {@link #NOTE_DECORATION_MARGIN_SS} since the tie arc already
     * provides visual separation from the notehead.
     */
    public static final double TIE_DECORATION_MARGIN_SS = 0.25;  // 2px

    // ==========================================================================
    // ABOVE-STAFF ELEMENTS (reference-based positioning)
    // ==========================================================================

    // --- Tempo ---
    /**
     * Margin from reference point to tempo marking
     */
    public static final double TEMPO_MARGIN_SS = 1.0;  // 4px

    // --- Beat Change ---
    /**
     * Margin from reference point to beat change
     */
    public static final double BEAT_CHANGE_MARGIN_SS = 1.0;  // 8px

    // --- First/Second Endings (Volta brackets) ---
    /**
     * Margin from reference point to ending bracket
     */
    public static final double ENDING_MARGIN_SS = 1.0;  // 8px

    // --- Annotations (text above staff) ---
    /**
     * Margin from reference point to annotation
     */
    public static final double ANNOTATION_ABOVE_MARGIN_SS = 0.5;  // 4px

    // ==========================================================================
    // BELOW-STAFF ELEMENTS
    // ==========================================================================

    // --- Crescendo/Diminuendo hairpins ---
    /**
     * Height of the hairpin opening
     */
    public static final double HAIRPIN_OPENING_HEIGHT_SS = 1.25;  // 10px

    // --- Lyrics Row (under staff) ---
    /**
     * Margin from staff bottom to lyrics
     */
    public static final double LYRICS_ROW_MARGIN_SS = 1.0;  // 8px

    // ==========================================================================
    // NOTE ELEMENTS
    // ==========================================================================

    // --- Tuplets ---
    /**
     * Margin from reference point to tuplet bracket
     */
    public static final double TUPLET_MARGIN_SS = 0.625;  // 5px

    // ==========================================================================
    // STAFF DIMENSIONS
    // ==========================================================================

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
     * Height of 5-line staff (4 gaps of 1 ss each)
     */
    public static final double STAFF_HEIGHT_SS = 4.0;  // 32px

    /**
     * Staff position offset: half of one staff space.
     * Used to convert between staff positions and Y coordinates.
     */
    public static final double STAFF_POSITION_OFFSET_SS = 0.5;  // 4px

    // ==========================================================================
    // LINE ELEMENT DEFAULT Y POSITIONS (relative to middleLineY, in ss)
    // ==========================================================================
    // These are the default Y offsets from the middle staff line (B line).
    // Negative values = above staff, positive = below staff.
    // Used by Line.java for initial values and by layout calculation for offset values.

    /**
     * Default tempo Y for first line (-5 staff spaces above middle)
     */
    public static final double TEMPO_DEFAULT_Y_FIRST_LINE_SS = -5.0;  // -40px

    /**
     * Default tempo Y for subsequent lines (-3 staff spaces above middle)
     */
    public static final double TEMPO_DEFAULT_Y_OTHER_LINES_SS = -3.0;  // -24px

    /**
     * Default beat change Y position (-3 staff spaces above middle)
     */
    public static final double BEAT_CHANGE_DEFAULT_Y_SS = -3.0;  // -24px

    /**
     * Default lyrics Y position (below staff)
     */
    public static final double LYRICS_DEFAULT_Y_SS = 6.25;  // 50px

    /**
     * Default first/second ending Y position (above staff)
     */
    public static final double ENDING_DEFAULT_Y_SS = -3.125;  // -25px

    /**
     * Default trill Y position (above staff)
     */
    public static final double TRILL_DEFAULT_Y_SS = -3.375;  // -27px

    // ==========================================================================
    // HORIZONTAL SPACING - Line Beginning
    // ==========================================================================

    /** X position of the treble clef at the start of a staff line, in staff-space units. */
    public static final double CLEF_X_POSITION_SS = 0.625;  // 5px

    /**
     * Width of each accidental in the key signature.
     */
    public static final double KEY_ACCIDENTAL_WIDTH_SS = 1.0;  // 8px

    /**
     * Distance from right extent of clef/key signature to first note column.
     * Per Gould/Ross: provides visual separation between staff beginning and music.
     */
    public static final double FIRST_NOTE_OFFSET_SS = 3.5;  // 28px

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

    // ==========================================================================
    // HORIZONTAL SPACING - Note Columns
    // ==========================================================================

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
     * Minimum horizontal gap between syllables.
     * This ensures lyric text remains readable.
     * Note: This value will be tuned empirically during implementation.
     */
    public static final double MIN_SYLLABLE_GAP_SS = 0.25;  // 2px (TBD)

    /**
     * Minimum clearance for accidentals from previous column's right extent.
     * Accidentals only push spacing when this minimum would be violated.
     */
    public static final double ACCIDENTAL_CLEARANCE_SS = 0.125;  // 1px

    // ==========================================================================
    // HORIZONTAL SPACING - Grace Notes
    // ==========================================================================

    /**
     * Gap between a grace note and its host note.
     */
    public static final double GRACE_NOTE_GAP_SS = 2.0;  // 16px

    // ==========================================================================
    // HORIZONTAL SPACING - Beam Groups
    // ==========================================================================

    /**
     * Minimum internal spacing within a beam group (tight, regular spacing).
     * Beam groups may widen if lyrics under them require it.
     */
    public static final double BEAM_GROUP_MIN_INTERNAL_GAP_SS = 1.5;  // 12px

    // ==========================================================================
    // VERTICAL STACKING - Collision Detection
    // ==========================================================================

    /**
     * Number of horizontal steps in the y-extent array used for collision detection.
     * Matches abc2svg's step count. Each step covers lineWidth / YSTEP staff-space units.
     */
    public static final int YSTEP = 128;

    // ==========================================================================
    // VERTICAL STACKING - Volta Brackets
    // ==========================================================================

    /**
     * Height of volta bracket tick marks in staff-space units.
     */
    public static final double VOLTA_TICK_HEIGHT_SS = 2.0;

    // ==========================================================================
    // VERTICAL STACKING - Lyrics
    // ==========================================================================

    /**
     * Distance from lowest note bounding area to lyrics baseline (ascent).
     * Per plan: lyrics are below the staff, below lowest note bounding area.
     */
    public static final double LYRICS_BASELINE_OFFSET_SS = 1.25;  // 10px

    /**
     * Approximate height of a single lyrics row (to be measured from actual font in later phases).
     */
    public static final double LYRICS_HEIGHT_SS = 2.5;  // ~20px

    /**
     * Vertical margin between the bottom of one staff line and the top of the next.
     */
    public static final double INTER_LINE_MARGIN_SS = 1.25;  // 10px

    // ==========================================================================
    // LINE JUSTIFICATION
    // ==========================================================================

    /**
     * Absolute minimum column gap during compression.
     * Even under maximum compression, gaps cannot go below this.
     */
    public static final double COMPRESSED_MIN_COLUMN_GAP_SS = 0.125;  // 1px

    /**
     * Absolute minimum syllable gap during compression.
     */
    public static final double COMPRESSED_MIN_SYLLABLE_GAP_SS = 0.125;  // 1px

    // ==========================================================================
    // SMuFL STEM ANCHORS
    // ==========================================================================

    /**
     * Scale factor applied to grace notes relative to regular notes.
     * Grace notes use the regular glyphs drawn with a scaled-down Bravura font.
     */
    public static final float GRACE_NOTE_SCALE = 0.75f;

    /**
     * SMuFL standard stem length in staff-space units.
     */
    public static final double STEM_LENGTH_SS = 3.5;

    /**
     * Stem length for grace notes in staff-space units.
     */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

    /**
     * Augmentation dot width in staff-space units.
     */
    public static final double DOT_WIDTH_SS = 0.5;

    /**
     * Gap between the notehead right edge and the first augmentation dot, in staff-space units.
     */
    public static final double DOT_GAP_SS = 0.25;

    /**
     * Gap between an accidental and the notehead, in staff-space units.
     */
    public static final double ACCIDENTAL_GAP_SS = 0.25;

    /**
     * Stem width in staff-space units (LilyPond multiplier-derived).
     */
    public static final double STEM_WIDTH_SS;

    /**
     * Stem anchor point for small black noteheads (stem-up, south-east corner).
     * Used for grace notes which use pre-sized small glyphs.
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_BLACK_SMALL;

    static {
        STEM_WIDTH_SS = LineThickness.getInstance().stemSs();
        STEM_UP_SE_BLACK_SMALL = new GlyphAnchors.Anchor(
            Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() * GRACE_NOTE_SCALE,
            Engraving.NOTEHEAD_BLACK_STEM_UP_SE.y() * GRACE_NOTE_SCALE
        );
    }

    // ==========================================================================
    // LEDGER LINES
    // ==========================================================================

    /**
     * Returns the ledger line overhang for a note, or 0 if the note has no ledger lines.
     * This is the distance the ledger lines extend beyond the notehead on each side.
     *
     * @param note The note to check
     * @return The overhang in staff-space units, or 0 if no ledger lines are needed
     */
    public static double getLedgerLineOverhangSs(StaffElement note) {
        if (Math.abs(note.getStaffPosition()) <= 5 || !note.getType().drawStaveLongitude()) {
            return 0.0;
        }

        return Engraving.LEDGER_LINE_EXTENSION_SS;
    }

    // ==========================================================================
    // STEM GEOMETRY
    // ==========================================================================

    /**
     * Base stem geometry computed from SMuFL anchor data, before any rendering-specific
     * adjustments (device-pixel snapping, beam lengthening, etc.).
     *
     * @param stemLeftXSs Left edge of the stem in staff spaces (relative to notehead origin)
     * @param anchorYSs   Y position where the stem meets the notehead
     * @param lengthSs    Stem length in staff spaces (without beam lengthening)
     */
    public record StemGeometry(double stemLeftXSs, double anchorYSs, double lengthSs) {

        /**
         * Returns the Y position of the stem tip (the end away from the notehead).
         *
         * @param upper true for stem-up (tip above notehead), false for stem-down
         * @return stem tip Y in staff spaces
         */
        public double stemTipYSs(boolean upper) {
            return upper ? anchorYSs - lengthSs : anchorYSs + lengthSs;
        }
    }

    /**
     * Computes the base stem geometry for a note type and direction.
     * This is the shared anchor selection and positioning logic used by both
     * {@code NoteRenderer} (for drawing) and {@code GlissandoRenderer} (for area building).
     *
     * @param noteType The note type (determines anchor and stem length)
     * @param upper    true for stem-up, false for stem-down
     * @return The base stem geometry
     */
    public static StemGeometry computeBaseStemGeometry(ElementType noteType, boolean upper) {
        boolean isMinim = noteType == ElementType.MINIM;
        boolean isGrace = noteType.isGraceNote();

        GlyphAnchors.Anchor anchor;

        if (isGrace) {
            anchor = STEM_UP_SE_BLACK_SMALL;
        } else if (upper) {
            anchor = isMinim ? Engraving.NOTEHEAD_HALF_STEM_UP_SE : Engraving.NOTEHEAD_BLACK_STEM_UP_SE;
        } else {
            anchor = isMinim ? Engraving.NOTEHEAD_HALF_STEM_DOWN_NW : Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW;
        }

        double anchorX = anchor.x();

        // Stem left edge: for up-stems, the anchor marks the RIGHT edge of the stem;
        // for down-stems, the anchor marks the LEFT edge but the notehead is shifted
        // left by STEM_WIDTH_SS/2, so we compensate.
        double stemLeftX = upper
            ? anchorX - STEM_WIDTH_SS
            : anchorX - STEM_WIDTH_SS / 2;

        double stemLength = isGrace ? GRACE_NOTE_STEM_LENGTH_SS : STEM_LENGTH_SS;

        return new StemGeometry(stemLeftX, anchor.y(), stemLength);
    }
}
