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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;

/**
 * The fundamental horizontal spacing unit in the engraving system.
 * <p>
 * An ElementColumn represents a vertical slice of the staff containing:
 * <ul>
 *   <li>A primary element (note, rest, or barline)</li>
 *   <li>Optional grace notes (which borrow space from the left)</li>
 *   <li>Horizontal extents (left edge including accidentals/grace notes; right edge including augmentation for minimum spacing, excluding augmentation for comfortable spacing)</li>
 *   <li>Stem information (top/bottom for beam group coordination)</li>
 *   <li>Associated lyric syllable (which drives horizontal spacing per Gould/Ross)</li>
 *   <li>Beam membership flag (for internal spacing coordination)</li>
 * </ul>
 * <p>
 * Construction is two-phase: {@link ElementColumnBuilder} creates the column and immediately sets
 * {@link #minGapToNextSyllableSs}, {@link #minCollisionGapToNextSyllableSs},
 * {@link #noteheadWidthSs} (note columns only) and {@link #beamGroupId}, then
 * {@link HorizontalSpacingCalculator} sets {@link #xSs} once positions are computed.
 * Every other field is immutable. Use {@link ElementColumnBuilder} to create instances.
 */
public final class ElementColumn {

    /** Beam-group id for a column that is not part of any beam group. */
    public static final int NO_BEAM_GROUP = -1;

    private final StaffElement element;
    private final List<StaffElement> graceNotes;
    private final double leftExtentSs;
    private final double rightExtentSs;
    private final double rightExtentExcludingAugmentationSs;
    private final double stemTopSs;
    private final double stemBottomSs;
    private final @Nullable String syllable;
    private final double syllableWidthSs;
    private final boolean beamed;
    // Minimum required gap between this column's right edge and the next column's syllable
    // left edge. Always set by ElementColumnBuilder: lyric space width for non-hyphenated or
    // lyric-less columns, hyphen cell width for hyphenated ones.
    private double minGapToNextSyllableSs;
    // Hard collision floor for the gap to the next syllable — the closest two syllables may ever
    // come, honoured by the spring strut so lyrics never touch. Always set by ElementColumnBuilder:
    // one lyric space width for non-hyphenated or lyric-less columns (including melisma carriers),
    // the bare hyphen glyph width for hyphenated ones. Distinct from minGapToNextSyllableSs, which
    // is the wider comfortable/preferred gap the rest aims for.
    private double minCollisionGapToNextSyllableSs;
    // Identifies which beam group this column belongs to (the beam's anchor element index),
    // or NO_BEAM_GROUP when not beamed. Set by ElementColumnBuilder so the spacing calculator
    // can keep adjacent beam groups separate rather than merging them.
    private int beamGroupId = NO_BEAM_GROUP;
    // Computed X position of element head left edge (set by HorizontalSpacingCalculator)
    private double xSs = 0;
    // Notehead-only width (grace-reduced or standard head) in staff spaces, excluding stem, flag,
    // and augmentation dots. Used for lyric centring so neither a flag nor a dot shifts the syllable
    // off the notehead. Defaults to the augmentation-excluded right extent — correct for non-notes,
    // and for synthetic test columns that carry no flag — but ElementColumnBuilder overrides it with
    // the true notehead width for note columns (whose right extent may be flag-inflated).
    private double noteheadWidthSs;

    /**
     * Creates a new ElementColumn.
     * <p>
     * Use {@link ElementColumnBuilder} rather than calling this constructor directly.
     *
     * @param element                    The primary element (note, rest, barline, etc.)
     * @param graceNotes                 Grace notes anchored to this element (empty list if none)
     * @param leftExtentSs               Left extent relative to element head left edge; 0.0 without accidental, negative with one
     * @param rightExtentSs                    Right extent relative to element head left edge; equals element head width, plus augmentation if any
     * @param rightExtentExcludingAugmentationSs Right extent excluding augmentation (dots and fall); used for comfortable spacing
     * @param stemTopSs                  Top of stem (if stem up), or element head top if no stem
     * @param stemBottomSs               Bottom of stem (if stem down), or element head bottom if no stem
     * @param syllable                   Associated lyric syllable text (null if none)
     * @param syllableWidthSs            Measured width of syllable text in staff spaces (0 if no syllable)
     * @param beamed                     Whether this element is part of a beam group
     */
    public ElementColumn(
        StaffElement element,
        List<StaffElement> graceNotes,
        double leftExtentSs,
        double rightExtentSs,
        double rightExtentExcludingAugmentationSs,
        double stemTopSs,
        double stemBottomSs,
        @Nullable String syllable,
        double syllableWidthSs,
        boolean beamed) {
        this.element = element;
        this.graceNotes = List.copyOf(graceNotes);
        this.leftExtentSs = leftExtentSs;
        this.rightExtentSs = rightExtentSs;
        this.rightExtentExcludingAugmentationSs = rightExtentExcludingAugmentationSs;
        this.noteheadWidthSs = rightExtentExcludingAugmentationSs;
        this.stemTopSs = stemTopSs;
        this.stemBottomSs = stemBottomSs;
        this.syllable = syllable;
        this.syllableWidthSs = syllableWidthSs;
        this.beamed = beamed;
    }

    /**
     * Creates a new ElementColumn where the right extent excluding augmentation equals the right extent.
     * <p>
     * Package-private so it is reachable only from tests that do not care about augmentation-driven
     * spacing. Production callers must use the full constructor and pass the correct
     * {@code rightExtentExcludingAugmentationSs}.
     */
    ElementColumn(
        StaffElement element,
        List<StaffElement> graceNotes,
        double leftExtentSs,
        double rightExtentSs,
        double stemTopSs,
        double stemBottomSs,
        @Nullable String syllable,
        double syllableWidthSs,
        boolean beamed) {
        this(element, graceNotes, leftExtentSs, rightExtentSs, rightExtentSs,
            stemTopSs, stemBottomSs, syllable, syllableWidthSs, beamed);
    }

    // ==========================================================================
    // Primary Element
    // ==========================================================================

    /**
     * Returns the primary element (note, rest, barline, etc.) for this column.
     */
    public StaffElement getElement() {
        return element;
    }

    /**
     * Returns whether this column represents a rest.
     */
    public boolean isRest() {
        return element.getType().isRest();
    }

    /**
     * Returns whether this column represents a barline.
     */
    public boolean isBarline() {
        return element.getType().isBarLine();
    }

    /**
     * Returns whether this column represents a grace note.
     */
    public boolean isGraceNote() {
        return element.getType().isGraceNote();
    }

    // ==========================================================================
    // Grace Notes
    // ==========================================================================

    /**
     * Returns the grace notes anchored to this column.
     * Grace notes borrow space from the main element's left side.
     *
     * @return Unmodifiable list of grace notes (empty if none)
     */
    public List<StaffElement> getGraceNotes() {
        return graceNotes;
    }

    /**
     * Returns whether this column has grace notes.
     */
    public boolean hasGraceNotes() {
        return !graceNotes.isEmpty();
    }

    // ==========================================================================
    // Horizontal Extents
    // ==========================================================================

    /**
     * Returns the left extent relative to the element head left edge (glyph origin).
     * 0.0 with no accidental; negative when an accidental is present (extends further left).
     */
    public double getLeftExtentSs() {
        return leftExtentSs;
    }

    /**
     * Returns the right extent relative to the element head left edge (glyph origin).
     * Equal to the element head width with no dots or fall; larger when dots or a fall are present.
     */
    public double getRightExtentSs() {
        return rightExtentSs;
    }

    /**
     * Returns the right extent excluding augmentation dots and fall.
     * Used for comfortable (default) spacing so dots and a fall do not push the next element
     * unless the minimum gap would otherwise be violated.
     */
    public double getRightExtentExcludingAugmentationSs() {
        return rightExtentExcludingAugmentationSs;
    }

    /**
     * Returns the total width of this column (leftExtent + rightExtent).
     */
    public double getWidthSs() {
        return Math.abs(leftExtentSs) + rightExtentSs;
    }

    /**
     * Returns the absolute left edge X position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getLeftEdgeXSs() {
        return xSs + leftExtentSs;
    }

    /**
     * Returns the absolute right edge X position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getRightEdgeXSs() {
        return xSs + rightExtentSs;
    }

    /**
     * Returns the notehead's own width in staff spaces — the reduced notehead for grace notes, the
     * standard notehead for other notes — excluding the stem, flag, and augmentation dots. Lyric
     * centring uses this so neither a flag nor a dot shifts the syllable off the notehead (the
     * Gould/Ross rule, which already excluded dots, extended to the flag). Set by
     * {@link ElementColumnBuilder} for note columns; otherwise the augmentation-excluded right extent.
     */
    public double getNoteheadWidthSs() {
        return noteheadWidthSs;
    }

    /**
     * Sets the notehead-only width. Called by {@link ElementColumnBuilder} for note columns so lyric
     * centring uses the head width rather than the flag-inflated right extent.
     */
    public void setNoteheadWidthSs(double noteheadWidthSs) {
        this.noteheadWidthSs = noteheadWidthSs;
    }

    /**
     * Returns the absolute X of the notehead centre, excluding the flag and augmentation dots.
     * Used to horizontally anchor lyrics so neither a flag nor a dot shifts the lyric position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getNoteheadCenterXSs() {
        return xSs + getNoteheadWidthSs() / 2.0;
    }

    // ==========================================================================
    // Stem Information
    // ==========================================================================

    /**
     * Returns the Y position of the stem top (for stem-up elements or beaming).
     * For elements without stems, returns the element head top.
     */
    public double getStemTopSs() {
        return stemTopSs;
    }

    /**
     * Returns the Y position of the stem bottom (for stem-down elements or beaming).
     * For elements without stems, returns the element head bottom.
     */
    public double getStemBottomSs() {
        return stemBottomSs;
    }

    /**
     * Returns the absolute layout-Y top of this column: the element's staff position converted
     * to ss, plus the note-local stem top extent. Single source of truth for the absolute top used
     * when building skyline contours.
     */
    public double getAbsoluteTopYSs() {
        return Staff.spToSs(getElement().getStaffPosition()) + getStemTopSs();
    }

    // ==========================================================================
    // Syllable (Lyric)
    // ==========================================================================

    /**
     * Returns the associated lyric syllable text.
     *
     * @return Syllable text, or null if no syllable
     */
    public @Nullable String getSyllable() {
        return syllable;
    }

    /**
     * Returns whether this column has an associated syllable.
     */
    public boolean hasSyllable() {
        return syllable != null && !syllable.isEmpty();
    }

    /**
     * Returns the measured width of the syllable text in pixels.
     *
     * @return Syllable width, or 0 if no syllable
     */
    public double getSyllableWidthSs() {
        return syllableWidthSs;
    }

    /**
     * Returns the minimum required gap between this column's right edge and the next
     * column's syllable left edge. Equals the lyric space width for non-hyphenated or
     * lyric-less columns; equals the hyphen cell width for hyphenated ones.
     */
    public double getMinGapToNextSyllableSs() {
        return minGapToNextSyllableSs;
    }

    void setMinGapToNextSyllableSs(double minGapToNextSyllableSs) {
        this.minGapToNextSyllableSs = minGapToNextSyllableSs;
    }

    /**
     * Returns the hard collision floor for the gap to the next syllable — the closest two
     * syllables may ever come. Equals one lyric space width for non-hyphenated or lyric-less
     * columns (including melisma carriers); equals the bare hyphen glyph width for hyphenated
     * ones. Narrower than {@link #getMinGapToNextSyllableSs()}, the comfortable gap the rest aims
     * for; the spring strut honours this floor so lyrics never touch.
     */
    public double getMinCollisionGapToNextSyllableSs() {
        return minCollisionGapToNextSyllableSs;
    }

    void setMinCollisionGapToNextSyllableSs(double minCollisionGapToNextSyllableSs) {
        this.minCollisionGapToNextSyllableSs = minCollisionGapToNextSyllableSs;
    }

    // ==========================================================================
    // Beam Group
    // ==========================================================================

    /**
     * Returns whether this element is part of a beam group.
     */
    public boolean isBeamed() {
        return beamed;
    }

    /**
     * Returns the identifier of the beam group this column belongs to, or
     * {@link #NO_BEAM_GROUP} if it is not beamed. Adjacent beam groups have distinct ids so
     * the spacing calculator keeps them separate.
     */
    public int getBeamGroupId() {
        return beamGroupId;
    }

    void setBeamGroupId(int beamGroupId) {
        this.beamGroupId = beamGroupId;
    }

    /**
     * Returns whether this element has an outgoing glissando.
     */
    public boolean hasGlissando() {
        return element.hasGlissando();
    }

    // ==========================================================================
    // X Position (set by HorizontalSpacingCalculator)
    // ==========================================================================

    /**
     * Returns the X position of this column's element head left edge (glyph origin).
     * This is set by the HorizontalSpacingCalculator during layout.
     */
    public double getXSs() {
        return xSs;
    }

    /**
     * Sets the X position of this column's element head left edge (glyph origin).
     * Called by the HorizontalSpacingCalculator during layout.
     *
     * @param xSs X position in staff spaces
     */
    void setXSs(double xSs) {
        this.xSs = xSs;
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        var sb = new StringBuilder(200);
        sb.append("ElementColumn{");
        sb.append("element=").append(element.getType());

        if (hasGraceNotes()) {
            sb.append(", graceNotes=").append(graceNotes.size());
        }

        sb.append(", extent=[").append(leftExtentSs).append(", ").append(rightExtentSs).append(']');

        if (hasSyllable()) {
            sb.append(", syllable='").append(syllable).append('\'');
            sb.append(", syllableWidth=").append(syllableWidthSs);
        }

        if (beamed) {
            sb.append(", beamed");
        }

        sb.append(", x=").append(xSs);
        sb.append('}');

        return sb.toString();
    }
}
