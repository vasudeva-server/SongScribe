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

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Centralized layout constants for the engraving system.
 * <p>
 * All spacing values are in staff-space (ss) units, where 1 ss equals
 * the distance between two adjacent staff lines. The conversion to pixels
 * is handled by {@link ScaleContext} (default: 1 ss = 8 px).
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
    // HORIZONTAL SPACING - Line Beginning
    // ==========================================================================

    /**
     * Width of the treble clef symbol.
     */
    public static final double CLEF_WIDTH_SS = 3.5;  // 28px

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
    public static double calculateFirstNoteXSs(int keyAccidentalCount) {
        return CLEF_WIDTH_SS + keyAccidentalCount * KEY_ACCIDENTAL_WIDTH_SS + FIRST_NOTE_OFFSET_SS;
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
     * Gap between grace notes in a group.
     */
    public static final double GRACE_NOTE_GAP_SS = 0.25;  // 2px

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
    // VERTICAL STACKING - Layer Margins
    // ==========================================================================

    /**
     * Margin from note bounds to articulations layer.
     */
    public static final double ARTICULATION_MARGIN_SS = 0.5;  // 4px

    /**
     * Margin from articulations to trill layer.
     */
    public static final double TRILL_MARGIN_SS = 0.25;  // 2px

    /**
     * Margin from trill to fermata layer.
     */
    public static final double FERMATA_MARGIN_SS = 0.25;  // 2px

    /**
     * Margin from fermata to dynamics layer.
     */
    public static final double DYNAMICS_MARGIN_SS = 0.25;  // 2px

    /**
     * Margin from dynamics to endings layer.
     */
    public static final double ENDING_MARGIN_SS = 0.5;  // 4px

    /**
     * Margin from endings to tempo layer.
     */
    public static final double TEMPO_MARGIN_SS = 0.5;  // 4px

    /**
     * Margin from tempo to annotations layer.
     */
    public static final double ANNOTATION_MARGIN_SS = 0.25;  // 2px

    /**
     * Margin from annotations to attribution layer.
     */
    public static final double ATTRIBUTION_MARGIN_SS = 0.5;  // 4px

    // ==========================================================================
    // VERTICAL STACKING - Lyrics
    // ==========================================================================

    /**
     * Distance from lowest note bounding area to lyrics baseline (ascent).
     * Per plan: lyrics are below the staff, below lowest note bounding area.
     */
    public static final double LYRICS_BASELINE_OFFSET_SS = 1.25;  // 10px

    // ==========================================================================
    // STAFF DIMENSIONS
    // ==========================================================================

    /**
     * Number of staff lines.
     */
    public static final int STAFF_LINE_COUNT = 5;

    /**
     * Total height of the 5-line staff (4 gaps of 1 ss each).
     */
    public static final double STAFF_HEIGHT_SS = 4.0;  // 32px

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
        STEM_WIDTH_SS = metadata.getEngravingDefaults().stemThickness();

        var blackAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        var halfAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_HALF);

        assert blackAnchors != null && blackAnchors.stemUpSE() != null && blackAnchors.stemDownNW() != null;
        assert halfAnchors != null && halfAnchors.stemUpSE() != null && halfAnchors.stemDownNW() != null;

        STEM_UP_SE_BLACK = blackAnchors.stemUpSE();
        STEM_DOWN_NW_BLACK = blackAnchors.stemDownNW();
        STEM_UP_SE_HALF = halfAnchors.stemUpSE();
        STEM_DOWN_NW_HALF = halfAnchors.stemDownNW();
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
    public static double getLedgerLineOverhangSs(@NotNull Note note) {
        if (Math.abs(note.getStaffPosition()) <= 5 || !note.getNoteType().drawStaveLongitude()) {
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
    @NotNull
    public static StemGeometry computeBaseStemGeometry(@NotNull NoteType noteType, boolean upper) {
        boolean isMinim = noteType == NoteType.MINIM;
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
