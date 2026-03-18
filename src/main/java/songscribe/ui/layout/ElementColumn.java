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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;

/**
 * The fundamental horizontal spacing unit in the engraving system.
 * <p>
 * An ElementColumn represents a vertical slice of the staff containing:
 * <ul>
 *   <li>A primary element (note, rest, or barline)</li>
 *   <li>Optional grace notes (which borrow space from the left)</li>
 *   <li>Horizontal extents (left edge including accidentals/grace notes, right edge including dots)</li>
 *   <li>Stem information (top/bottom for beam group coordination)</li>
 *   <li>Associated lyric syllable (which drives horizontal spacing per Gould/Ross)</li>
 *   <li>Beam membership flag (for internal spacing coordination)</li>
 * </ul>
 * <p>
 * This class is immutable after construction. Use {@link ElementColumnBuilder} to create instances.
 */
public final class ElementColumn {

    private final StaffElement element;
    private final List<StaffElement> graceNotes;
    private final double leftExtentSs;
    private final double rightExtentSs;
    private final double stemTopSs;
    private final double stemBottomSs;
    private final @Nullable String syllable;
    private final double syllableWidthSs;
    private final boolean beamed;
    // Computed X position of element head left edge (set by HorizontalSpacingCalculator)
    private double xSs = 0;

    /**
     * Creates a new ElementColumn.
     * <p>
     * Use {@link ElementColumnBuilder} rather than calling this constructor directly.
     *
     * @param element         The primary element (note, rest, barline, etc.)
     * @param graceNotes      Grace notes anchored to this element (empty list if none)
     * @param leftExtentSs    Left extent relative to element head left edge; 0.0 without accidental, negative with one
     * @param rightExtentSs   Right extent relative to element head left edge; equals element head width, plus dots if any
     * @param stemTopSs       Top of stem (if stem up), or element head top if no stem
     * @param stemBottomSs    Bottom of stem (if stem down), or element head bottom if no stem
     * @param syllable        Associated lyric syllable text (null if none)
     * @param syllableWidthSs Measured width of syllable text in staff spaces (0 if no syllable)
     * @param beamed          Whether this element is part of a beam group
     */
    public ElementColumn(
        StaffElement element,
        List<StaffElement> graceNotes,
        double leftExtentSs,
        double rightExtentSs,
        double stemTopSs,
        double stemBottomSs,
        @Nullable String syllable,
        double syllableWidthSs,
        boolean beamed) {
        this.element = element;
        this.graceNotes = List.copyOf(graceNotes);
        this.leftExtentSs = leftExtentSs;
        this.rightExtentSs = rightExtentSs;
        this.stemTopSs = stemTopSs;
        this.stemBottomSs = stemBottomSs;
        this.syllable = syllable;
        this.syllableWidthSs = syllableWidthSs;
        this.beamed = beamed;
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
     * Equal to the element head width with no dots; larger when dots are present.
     */
    public double getRightExtentSs() {
        return rightExtentSs;
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
     * Returns whether this element has an outgoing glissando.
     */
    @SuppressWarnings("ObjectEquality")
    public boolean hasGlissando() {
        return element.getGlissando() != StaffElement.NO_GLISSANDO;
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
     * @param x X position in staff spaces
     */
    void setXSs(double x) {
        this.xSs = x;
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("ElementColumn{");
        sb.append("element=").append(element.getType());

        if (hasGraceNotes()) {
            sb.append(", graceNotes=").append(graceNotes.size());
        }

        sb.append(", extent=[").append(leftExtentSs).append(", ").append(rightExtentSs).append("]");

        if (hasSyllable()) {
            sb.append(", syllable='").append(syllable).append("'");
            sb.append(", syllableWidth=").append(syllableWidthSs);
        }

        if (isBeamed()) {
            sb.append(", beamed");
        }

        sb.append(", x=").append(xSs);
        sb.append("}");

        return sb.toString();
    }
}
