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

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.ui.layout.BeamGroup;

/**
 * The fundamental horizontal spacing unit in the engraving system.
 * <p>
 * A NoteColumn represents a vertical slice of the staff containing:
 * <ul>
 *   <li>A primary element (note, rest, or barline)</li>
 *   <li>Optional grace notes (which borrow space from the left)</li>
 *   <li>Horizontal extents (left edge including accidentals/grace notes, right edge including dots)</li>
 *   <li>Stem information (top/bottom for beam group coordination)</li>
 *   <li>Associated lyric syllable (which drives horizontal spacing per Gould/Ross)</li>
 *   <li>Beam group membership (for internal spacing coordination)</li>
 * </ul>
 * <p>
 * This class is immutable after construction. Use {@link NoteColumnBuilder} to create instances.
 */
public final class NoteColumn {

    private final @NotNull Note note;
    private final @NotNull List<Note> graceNotes;
    private final double leftExtentSs;
    private final double rightExtentSs;
    private final double stemTopSs;
    private final double stemBottomSs;
    private final @Nullable String syllable;
    private final double syllableWidthSs;
    private final @Nullable BeamGroup beamGroup;

    // Computed X position (set by HorizontalSpacingCalculator)
    private double xSs = 0;

    /**
     * Creates a new NoteColumn.
     * <p>
     * Use {@link NoteColumnBuilder} rather than calling this constructor directly.
     *
     * @param note          The primary note (or rest, barline, etc.)
     * @param graceNotes    Grace notes anchored to this note (empty list if none)
     * @param leftExtentSs    Left edge relative to note head center (includes accidental + grace notes)
     * @param rightExtentSs   Right edge relative to note head center (includes dots)
     * @param stemTopSs       Top of stem (if stem up), or note head top if no stem
     * @param stemBottomSs    Bottom of stem (if stem down), or note head bottom if no stem
     * @param syllable        Associated lyric syllable text (null if none)
     * @param syllableWidthSs Measured width of syllable text in staff spaces (0 if no syllable)
     * @param beamGroup       Beam group this note belongs to (null if unbeamed)
     */
    public NoteColumn(
        @NotNull Note note,
        @NotNull List<Note> graceNotes,
        double leftExtentSs,
        double rightExtentSs,
        double stemTopSs,
        double stemBottomSs,
        @Nullable String syllable,
        double syllableWidthSs,
        @Nullable BeamGroup beamGroup) {
        this.note = note;
        this.graceNotes = List.copyOf(graceNotes);
        this.leftExtentSs = leftExtentSs;
        this.rightExtentSs = rightExtentSs;
        this.stemTopSs = stemTopSs;
        this.stemBottomSs = stemBottomSs;
        this.syllable = syllable;
        this.syllableWidthSs = syllableWidthSs;
        this.beamGroup = beamGroup;
    }

    // ==========================================================================
    // Primary Element
    // ==========================================================================

    /**
     * Returns the primary note (or rest, barline, etc.) for this column.
     */
    public @NotNull Note getNote() {
        return note;
    }

    /**
     * Returns whether this column represents a rest.
     */
    public boolean isRest() {
        return note.getNoteType().isRest();
    }

    /**
     * Returns whether this column represents a barline.
     */
    public boolean isBarline() {
        return note.getNoteType().isBarLine();
    }

    // ==========================================================================
    // Grace Notes
    // ==========================================================================

    /**
     * Returns the grace notes anchored to this column.
     * Grace notes borrow space from the main note's left side.
     *
     * @return Unmodifiable list of grace notes (empty if none)
     */
    public @NotNull List<Note> getGraceNotes() {
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
     * Returns the left extent relative to the note head center.
     * This includes accidentals and grace notes (negative value = extends left).
     */
    public double getLeftExtentSs() {
        return leftExtentSs;
    }

    /**
     * Returns the right extent relative to the note head center.
     * This includes dots (positive value = extends right).
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
     * Returns the Y position of the stem top (for stem-up notes or beaming).
     * For notes without stems, returns the note head top.
     */
    public double getStemTopSs() {
        return stemTopSs;
    }

    /**
     * Returns the Y position of the stem bottom (for stem-down notes or beaming).
     * For notes without stems, returns the note head bottom.
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
     * Returns the beam group this note belongs to.
     *
     * @return BeamGroup, or null if this note is not beamed
     */
    public @Nullable BeamGroup getBeamGroup() {
        return beamGroup;
    }

    /**
     * Returns whether this note is part of a beam group.
     */
    public boolean isBeamed() {
        return beamGroup != null;
    }

    // ==========================================================================
    // X Position (set by HorizontalSpacingCalculator)
    // ==========================================================================

    /**
     * Returns the X position of this column's note head center.
     * This is set by the HorizontalSpacingCalculator during layout.
     */
    public double getXSs() {
        return xSs;
    }

    /**
     * Sets the X position of this column's note head center.
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
        sb.append("NoteColumn{");
        sb.append("note=").append(note.getNoteType());

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
