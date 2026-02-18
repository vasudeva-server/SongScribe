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

import java.util.Collections;
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
    private final double leftExtent;
    private final double rightExtent;
    private final double stemTop;
    private final double stemBottom;
    private final @Nullable String syllable;
    private final double syllableWidth;
    private final @Nullable BeamGroup beamGroup;

    // Computed X position (set by HorizontalSpacingCalculator)
    private double x = 0;

    /**
     * Creates a new NoteColumn.
     * <p>
     * Use {@link NoteColumnBuilder} rather than calling this constructor directly.
     *
     * @param note          The primary note (or rest, barline, etc.)
     * @param graceNotes    Grace notes anchored to this note (empty list if none)
     * @param leftExtent    Left edge relative to note head center (includes accidental + grace notes)
     * @param rightExtent   Right edge relative to note head center (includes dots)
     * @param stemTop       Top of stem (if stem up), or note head top if no stem
     * @param stemBottom    Bottom of stem (if stem down), or note head bottom if no stem
     * @param syllable      Associated lyric syllable text (null if none)
     * @param syllableWidth Measured width of syllable text in pixels (0 if no syllable)
     * @param beamGroup     Beam group this note belongs to (null if unbeamed)
     */
    public NoteColumn(
            @NotNull Note note,
            @NotNull List<Note> graceNotes,
            double leftExtent,
            double rightExtent,
            double stemTop,
            double stemBottom,
            @Nullable String syllable,
            double syllableWidth,
            @Nullable BeamGroup beamGroup) {
        this.note = note;
        this.graceNotes = List.copyOf(graceNotes);
        this.leftExtent = leftExtent;
        this.rightExtent = rightExtent;
        this.stemTop = stemTop;
        this.stemBottom = stemBottom;
        this.syllable = syllable;
        this.syllableWidth = syllableWidth;
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
    public double getLeftExtent() {
        return leftExtent;
    }

    /**
     * Returns the right extent relative to the note head center.
     * This includes dots (positive value = extends right).
     */
    public double getRightExtent() {
        return rightExtent;
    }

    /**
     * Returns the total width of this column (leftExtent + rightExtent).
     */
    public double getWidth() {
        return Math.abs(leftExtent) + rightExtent;
    }

    /**
     * Returns the absolute left edge X position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getLeftEdgeX() {
        return x + leftExtent;
    }

    /**
     * Returns the absolute right edge X position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getRightEdgeX() {
        return x + rightExtent;
    }

    // ==========================================================================
    // Stem Information
    // ==========================================================================

    /**
     * Returns the Y position of the stem top (for stem-up notes or beaming).
     * For notes without stems, returns the note head top.
     */
    public double getStemTop() {
        return stemTop;
    }

    /**
     * Returns the Y position of the stem bottom (for stem-down notes or beaming).
     * For notes without stems, returns the note head bottom.
     */
    public double getStemBottom() {
        return stemBottom;
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
    public double getSyllableWidth() {
        return syllableWidth;
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
    public double getX() {
        return x;
    }

    /**
     * Sets the X position of this column's note head center.
     * Called by the HorizontalSpacingCalculator during layout.
     *
     * @param x X position in pixels
     */
    void setX(double x) {
        this.x = x;
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

        sb.append(", extent=[").append(leftExtent).append(", ").append(rightExtent).append("]");

        if (hasSyllable()) {
            sb.append(", syllable='").append(syllable).append("'");
            sb.append(", syllableWidth=").append(syllableWidth);
        }

        if (isBeamed()) {
            sb.append(", beamed");
        }

        sb.append(", x=").append(x);
        sb.append("}");

        return sb.toString();
    }
}
