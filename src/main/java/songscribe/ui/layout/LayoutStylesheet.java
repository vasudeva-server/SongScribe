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
import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

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
        return UIManager.getColor("SongScribe.scorePage.screen.background");
    }

    /**
     * Returns the print/export background color of the score page (always white).
     * Read from UIManager to support theming; callers should not cache this value.
     */
    public static Color getPrintBackground() {
        return UIManager.getColor("SongScribe.scorePage.print.background");
    }

    // ==========================================================================
    // SECTION ELEMENTS (block flow layout)
    // ==========================================================================

    // --- Title ---
    /**
     * Padding inside title bounds (for hit testing)
     */
    public static final double TITLE_PADDING_SS = 0;

    /**
     * Margin from title bottom to next section
     */
    public static final double TITLE_MARGIN_BOTTOM_SS = 2.0;  // 16px

    // --- Attribution ---
    /**
     * Padding inside attribution bounds
     */
    public static final double ATTRIBUTION_PADDING_SS = 0;

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

    // --- Lyrics Block (below score) ---
    /**
     * Margin from score bottom to lyrics block
     */
    public static final double LYRICS_BLOCK_MARGIN_TOP_SS = 5.0;  // 40px

    /**
     * Margin from primary lyrics to Bangla lyrics
     */
    public static final double BANGLA_MARGIN_TOP_SS = 2.0;  // 16px

    /**
     * Margin from Bangla lyrics to translation
     */
    public static final double TRANSLATION_MARGIN_TOP_SS = 2.0;  // 16px

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

    // --- Annotation Region ---
    /**
     * Reserved vertical space for above-staff elements
     */
    public static final double ANNOTATION_REGION_MARGIN_SS = 3.0;  // 24px

    // --- Tempo ---
    /**
     * Padding around tempo marking (for hit testing)
     */
    public static final double TEMPO_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from reference point to tempo marking
     */
    public static final double TEMPO_MARGIN_SS = 1.0;  // 8px

    // --- Beat Change ---
    /**
     * Padding around beat/time signature change
     */
    public static final double BEAT_CHANGE_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from reference point to beat change
     */
    public static final double BEAT_CHANGE_MARGIN_SS = 1.0;  // 8px

    // --- First/Second Endings (Volta brackets) ---
    /**
     * Padding around ending bracket
     */
    public static final double ENDING_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from reference point to ending bracket
     */
    public static final double ENDING_MARGIN_SS = 0.75;  // 6px

    // --- Trill ---
    /**
     * Padding around trill marking
     */
    public static final double TRILL_PADDING_SS = 0.25;  // 2px

    // --- Fermata ---
    /**
     * Padding around fermata marking
     */
    public static final double FERMATA_PADDING_SS = 0.25;  // 2px

    // --- Annotations (text above staff) ---
    /**
     * Padding around text annotation
     */
    public static final double ANNOTATION_ABOVE_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from reference point to annotation
     */
    public static final double ANNOTATION_ABOVE_MARGIN_SS = 0.5;  // 4px

    // ==========================================================================
    // BELOW-STAFF ELEMENTS
    // ==========================================================================

    // --- Dynamics (p, f, ff, etc.) ---
    /**
     * Padding around dynamic marking
     */
    public static final double DYNAMICS_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from staff bottom to dynamics
     */
    public static final double DYNAMICS_MARGIN_SS = 1.0;  // 8px

    // --- Crescendo/Diminuendo hairpins ---
    /**
     * Padding around hairpin
     */
    public static final double CRESC_DIM_PADDING_SS = 0.125;  // 1px

    /**
     * Margin from reference point to hairpin
     */
    public static final double CRESC_DIM_MARGIN_SS = 0.5;  // 4px

    // --- Lyrics Row (under staff) ---
    /**
     * Padding around lyrics row
     */
    public static final double LYRICS_ROW_PADDING_SS = 0;

    /**
     * Margin from staff bottom to lyrics
     */
    public static final double LYRICS_ROW_MARGIN_SS = 1.0;  // 8px

    /**
     * Margin from lowest note bounds to lyrics baseline (ascent).
     * Per Phase 8 spec: ascent below lowest note bounding box.
     */
    public static final double LYRICS_BASELINE_MARGIN_SS = 1.25;  // 10px

    // ==========================================================================
    // NOTE ELEMENTS
    // ==========================================================================

    // --- Note (hit testing) ---
    /**
     * Padding around element for hit testing
     */
    public static final double ELEMENT_PADDING_SS = 0.5;  // 4px

    // --- Articulations ---
    /**
     * Padding around articulation marking
     */
    public static final double ARTICULATION_PADDING_SS = 0.25;  // 2px

    /**
     * Margin between stacked articulations (staccato -> accent)
     */
    public static final double ARTICULATION_INTER_MARGIN_SS = 0.375;  // 3px

    // --- Ties ---
    /**
     * Padding around tie curve
     */
    public static final double TIE_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from articulations to tie
     */
    public static final double TIE_MARGIN_SS = 0.75;  // 6px

    /**
     * Distance from note head to tie arc endpoint
     */
    public static final double TIE_NOTE_HEAD_OFFSET_SS = 0.125;  // 1px

    /**
     * Minimum arc height for ties
     */
    public static final double TIE_MIN_ARC_HEIGHT_SS = 0.75;  // 6px

    /**
     * Reference horizontal distance for arc height scaling
     */
    public static final double TIE_REFERENCE_DISTANCE_SS = 6.25;  // 50px

    /**
     * Arc height scaling factor for ties.
     * Arc height = minHeight + sqrt(distance / reference) * heightScale
     */
    public static final double TIE_HEIGHT_SCALE_SS = 0.5;  // 4px additional per sqrt unit

    /**
     * Margin from tie to articulations (area-based collision)
     */
    public static final double TIE_ARTICULATION_MARGIN_SS = 0.25;  // 2px

    // --- Tuplets ---
    /**
     * Padding around tuplet bracket
     */
    public static final double TUPLET_PADDING_SS = 0.25;  // 2px

    /**
     * Margin from reference point to tuplet bracket
     */
    public static final double TUPLET_MARGIN_SS = 0.625;  // 5px

    /**
     * Margin from beam to tuplet number (measured perpendicular to beam)
     */
    public static final double TUPLET_BEAM_MARGIN_SS = 0.25;  // 2px

    /**
     * Margin from highest note/articulation bounds to tuplet bracket
     */
    public static final double TUPLET_BRACKET_MARGIN_SS = 0.25;  // 2px

    /**
     * Minimum margin from staff top to tuplet bracket
     */
    public static final double TUPLET_MIN_STAFF_MARGIN_SS = 0.5;  // 4px

    /**
     * Gap on each side of tuplet number in bracket
     */
    public static final double TUPLET_NUMBER_GAP_SS = 0.125;  // 1px

    /**
     * Overhang of bracket beyond first and last note heads
     */
    public static final double TUPLET_BRACKET_OVERHANG_SS = 0.125;  // 1px

    /**
     * Scale factor for tuplet number in non-beamed (bracket) tuplets
     */
    public static final double TUPLET_BRACKET_NUMBER_SCALE = 0.9;  // 90% font size

    // --- Beams ---
    /**
     * Padding around beam
     */
    public static final double BEAM_PADDING_SS = 0;

    /**
     * Margin between beam levels (8th -> 16th)
     */
    public static final double BEAM_INTER_MARGIN_SS = 0.75;  // 6px

    // ==========================================================================
    // HORIZONTAL SPACING
    // ==========================================================================

    // --- Accidentals ---
    /**
     * Padding around accidental glyph
     */
    public static final double ACCIDENTAL_PADDING_SS = 0.375;  // 3px

    /**
     * Margin between accidental and note head
     */
    public static final double ACCIDENTAL_INTER_MARGIN_SS = 0.125;  // 1px

    // --- Note Type Spacing (right margin after note) ---
    /**
     * Right margin after semibreve (whole note)
     */
    public static final double SEMIBREVE_MARGIN_RIGHT_SS = 8.75;  // 70px

    /**
     * Right margin after minim (half note)
     */
    public static final double MINIM_MARGIN_RIGHT_SS = 6.25;  // 50px

    /**
     * Right margin after crotchet (quarter note)
     */
    public static final double CROTCHET_MARGIN_RIGHT_SS = 4.375;  // 35px

    /**
     * Right margin after quaver (eighth note)
     */
    public static final double QUAVER_MARGIN_RIGHT_SS = 3.125;  // 25px

    /**
     * Right margin after semiquaver (sixteenth note)
     */
    public static final double SEMIQUAVER_MARGIN_RIGHT_SS = 3.125;  // 25px

    /**
     * Right margin after barline
     */
    public static final double BARLINE_MARGIN_RIGHT_SS = 7.5;  // 60px

    /**
     * Right margin after breath mark
     */
    public static final double BREATH_MARK_MARGIN_RIGHT_SS = 1.875;  // 15px

    // --- Syllables ---
    /**
     * Horizontal padding around syllable text
     */
    public static final double SYLLABLE_PADDING_H_SS = 0.25;  // 2px

    /**
     * Left margin before syllable
     */
    public static final double SYLLABLE_MARGIN_LEFT_SS = 0.25;  // 2px

    /**
     * Right margin after syllable
     */
    public static final double SYLLABLE_MARGIN_RIGHT_SS = 0.25;  // 2px

    // --- First Note ---
    /**
     * X position of first note from left edge
     */
    public static final double FIRST_NOTE_X_SS = 12.5;  // 100px

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
     * Width of the treble clef symbol, derived from the SMuFL advance width.
     */
    public static final double CLEF_WIDTH_SS;

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
        return CLEF_WIDTH_SS + keyAccidentalCount * KEY_ACCIDENTAL_WIDTH_SS;
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
     * Grace notes borrow space from the main note's left side.
     * They must not push earlier notes leftward.
     * If insufficient space, grace notes compress (they are subordinate).
     */
    public static final double GRACE_NOTE_MIN_WIDTH_SS = 1.0;  // 8px per grace note

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

    /**
     * Minimum gap between adjacent beam groups or between a beam group and a rest.
     */
    public static final double BEAM_GROUP_EXTERNAL_GAP_SS = 0.5;  // 4px

    // ==========================================================================
    // HORIZONTAL SPACING - Barlines
    // ==========================================================================

    /**
     * Space before a barline.
     */
    public static final double BARLINE_GAP_BEFORE_SS = 1.0;  // 8px

    /**
     * Space after a barline.
     */
    public static final double BARLINE_GAP_AFTER_SS = 1.5;  // 12px

    // ==========================================================================
    // HORIZONTAL SPACING - Breath Marks
    // ==========================================================================

    /**
     * Slight space after a note with a breath mark.
     * Breath marks participate lightly in spacing.
     */
    public static final double BREATH_MARK_GAP_SS = 0.25;  // 2px

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
     * From abc2svg: {@code 20 / 8}.
     */
    public static final double VOLTA_TICK_HEIGHT_SS = 2.5;  // 20px

    /**
     * Initial Y margin above staff top for volta bracket positioning.
     * From abc2svg: {@code 5 / 8}.
     */
    public static final double VOLTA_MARGIN_SS = 0.625;  // 5px

    // ==========================================================================
    // VERTICAL STACKING - Lyrics
    // ==========================================================================

    /**
     * Distance from lowest note bounding area to lyrics baseline (ascent).
     * Per plan: lyrics are below the staff, below lowest note bounding area.
     */
    public static final double LYRICS_BASELINE_OFFSET_SS = 1.25;  // 10px

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
     * Stem width in staff-space units (from Bravura engraving defaults).
     */
    public static final double STEM_WIDTH_SS;

    /**
     * Stem anchor point for black noteheads (stem-up, south-east corner).
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_BLACK;

    /**
     * Stem anchor point for black noteheads (stem-down, north-west corner).
     */
    public static final GlyphAnchors.Anchor STEM_DOWN_NW_BLACK;

    /**
     * Stem anchor point for half noteheads (stem-up, south-east corner).
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_HALF;

    /**
     * Stem anchor point for half noteheads (stem-down, north-west corner).
     */
    public static final GlyphAnchors.Anchor STEM_DOWN_NW_HALF;

    /**
     * Stem anchor point for small black noteheads (stem-up, south-east corner).
     * Used for grace notes which use pre-sized small glyphs.
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_BLACK_SMALL;

    static {
        var metadata = SMuFLMetadata.getInstance();
        CLEF_WIDTH_SS = metadata.requireAdvanceWidth(SMuFLGlyph.G_CLEF);
        STEM_WIDTH_SS = metadata.getEngravingDefaults().stemThickness();

        var blackAnchors = metadata.requireAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        var halfAnchors = metadata.requireAnchors(SMuFLGlyph.NOTEHEAD_HALF);

        STEM_UP_SE_BLACK = blackAnchors.requireStemUpSE();
        STEM_DOWN_NW_BLACK = blackAnchors.requireStemDownNW();
        STEM_UP_SE_HALF = halfAnchors.requireStemUpSE();
        STEM_DOWN_NW_HALF = halfAnchors.requireStemDownNW();
        STEM_UP_SE_BLACK_SMALL = new GlyphAnchors.Anchor(
            STEM_UP_SE_BLACK.x() * GRACE_NOTE_SCALE,
            STEM_UP_SE_BLACK.y() * GRACE_NOTE_SCALE
        );

        LEDGER_LINE_EXTENSION_SS = metadata.getEngravingDefaults().legerLineExtension();
    }

    // ==========================================================================
    // LEDGER LINES
    // ==========================================================================

    /**
     * How far ledger lines extend beyond the notehead on each side, in staff-space units.
     */
    public static final double LEDGER_LINE_EXTENSION_SS;

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

        return LEDGER_LINE_EXTENSION_SS;
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
            anchor = isMinim ? STEM_UP_SE_HALF : STEM_UP_SE_BLACK;
        } else {
            anchor = isMinim ? STEM_DOWN_NW_HALF : STEM_DOWN_NW_BLACK;
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
