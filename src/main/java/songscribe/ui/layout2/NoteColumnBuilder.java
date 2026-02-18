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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout.BeamGroup;

/**
 * Builds {@link NoteColumn} instances from a Line's notes.
 * <p>
 * This builder constructs the fundamental spacing units for the engraving system.
 * Each note (or rest, barline, etc.) becomes a NoteColumn with:
 * <ul>
 *   <li>Horizontal extents (left for accidentals/grace notes, right for dots)</li>
 *   <li>Stem positions (for beam coordination)</li>
 *   <li>Syllable text and measured width (for lyric-driven spacing)</li>
 *   <li>Beam group reference (for internal spacing coordination)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * var builder = new NoteColumnBuilder(g2, lyricsFont);
 * List<NoteColumn> columns = builder.buildColumns(line);
 * }</pre>
 */
public class NoteColumnBuilder {

    // Standard note head width (from Note.NORMAL_IMAGE_WIDTH)
    private static final double NOTE_HEAD_WIDTH = 18.0;

    // Half note head width (for left/right extent calculation)
    private static final double HALF_NOTE_HEAD = NOTE_HEAD_WIDTH / 2.0;

    // Dot width and spacing
    private static final double DOT_WIDTH = 4.0;
    private static final double DOT_GAP = 2.0;

    // Accidental widths (from Note.REAL_NATURAL_FLAT_SHARP_RECT)
    private static final double NATURAL_WIDTH = 6.0;
    private static final double FLAT_WIDTH = 7.0;
    private static final double SHARP_WIDTH = 8.0;
    private static final double DOUBLE_SHARP_WIDTH = 9.0;
    private static final double ACCIDENTAL_GAP = 2.0;

    // Stem length (approximate, actual varies with beaming)
    private static final double STEM_LENGTH = 28.0;

    private final Graphics2D g2;
    private final Font lyricsFont;
    private final FontMetrics lyricsFontMetrics;

    /**
     * Creates a new NoteColumnBuilder.
     *
     * @param g2         Graphics context for measuring text
     * @param lyricsFont Font used for lyrics (for measuring syllable widths)
     */
    public NoteColumnBuilder(@NotNull Graphics2D g2, @NotNull Font lyricsFont) {
        this.g2 = g2;
        this.lyricsFont = lyricsFont;
        this.lyricsFontMetrics = g2.getFontMetrics(lyricsFont);
    }

    /**
     * Builds NoteColumns for all notes in a line.
     *
     * @param line The line to process
     * @return List of NoteColumns in note order
     */
    public @NotNull List<NoteColumn> buildColumns(@NotNull Line line) {
        var noteCount = line.noteCount();

        if (noteCount == 0) {
            return Collections.emptyList();
        }

        var columns = new ArrayList<NoteColumn>(noteCount);

        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var column = buildColumn(note, line);
            columns.add(column);
        }

        return columns;
    }

    /**
     * Builds a single NoteColumn for a note.
     *
     * @param note The note to process
     * @param line The line containing the note (for beam group lookup)
     * @return The constructed NoteColumn
     */
    public @NotNull NoteColumn buildColumn(@NotNull Note note, @NotNull Line line) {
        // Calculate horizontal extents
        double leftExtent = calculateLeftExtent(note);
        double rightExtent = calculateRightExtent(note);

        // Calculate stem positions
        double stemTop = calculateStemTop(note);
        double stemBottom = calculateStemBottom(note);

        // Get syllable and measure width
        String syllable = getSyllable(note);
        double syllableWidth = measureSyllableWidth(syllable);

        // Find beam group (if any)
        BeamGroup beamGroup = findBeamGroup(note, line);

        // Get grace notes (currently not implemented in data model)
        List<Note> graceNotes = getGraceNotes(note);

        return new NoteColumn(
                note,
                graceNotes,
                leftExtent,
                rightExtent,
                stemTop,
                stemBottom,
                syllable,
                syllableWidth,
                beamGroup
        );
    }

    // ==========================================================================
    // Horizontal Extent Calculations
    // ==========================================================================

    /**
     * Calculates the left extent of a note column.
     * This includes the note head left edge plus any accidental.
     *
     * @param note The note
     * @return Left extent (negative value, extends left from note head center)
     */
    public static double calculateLeftExtent(@NotNull Note note) {
        // Start with half the note head width
        double extent = -HALF_NOTE_HEAD;

        // Add accidental width if present
        var accidental = note.getAccidental();

        if (accidental != Note.Accidental.NONE) {
            double accidentalWidth = getAccidentalWidth(accidental);
            extent -= (accidentalWidth + ACCIDENTAL_GAP);
        }

        return extent;
    }

    /**
     * Calculates the right extent of a note column.
     * This includes the note head right edge plus any dots.
     *
     * @param note The note
     * @return Right extent (positive value, extends right from note head center)
     */
    public static double calculateRightExtent(@NotNull Note note) {
        // Start with half the note head width
        double extent = HALF_NOTE_HEAD;

        // Add dot widths if present
        int dotCount = note.getDotCount();

        if (dotCount > 0) {
            // First dot: gap + dot
            extent += DOT_GAP + DOT_WIDTH;

            // Additional dots: gap + dot each
            for (var i = 1; i < dotCount; i++) {
                extent += DOT_GAP + DOT_WIDTH;
            }
        }

        return extent;
    }

    /**
     * Returns the width of an accidental.
     */
    public static double getAccidentalWidth(@NotNull Note.Accidental accidental) {
        return switch (accidental) {
            case NONE -> 0;
            case NATURAL, DOUBLE_NATURAL -> NATURAL_WIDTH;
            case FLAT, NATURAL_FLAT -> FLAT_WIDTH;
            case SHARP, NATURAL_SHARP -> SHARP_WIDTH;
            case DOUBLE_FLAT -> FLAT_WIDTH * 2;
            case DOUBLE_SHARP -> DOUBLE_SHARP_WIDTH;
        };
    }

    // ==========================================================================
    // Stem Calculations
    // ==========================================================================

    /**
     * Calculates the Y position of the stem top.
     * For stem-up notes, this is above the note head.
     * For stem-down or stemless notes, this is the note head top.
     *
     * @param note The note
     * @return Stem top Y position (relative to staff, negative = above)
     */
    private double calculateStemTop(@NotNull Note note) {
        var noteType = note.getNoteType();

        // Rests and stemless notes: use note head top
        if (!noteType.isNoteWithStem()) {
            return -HALF_NOTE_HEAD;
        }

        // Stem up: stem extends upward
        if (!note.isUpper()) {
            return -STEM_LENGTH;
        }

        // Stem down: top is just above note head
        return -HALF_NOTE_HEAD;
    }

    /**
     * Calculates the Y position of the stem bottom.
     * For stem-down notes, this is below the note head.
     * For stem-up or stemless notes, this is the note head bottom.
     *
     * @param note The note
     * @return Stem bottom Y position (relative to staff, positive = below)
     */
    private double calculateStemBottom(@NotNull Note note) {
        var noteType = note.getNoteType();

        // Rests and stemless notes: use note head bottom
        if (!noteType.isNoteWithStem()) {
            return HALF_NOTE_HEAD;
        }

        // Stem down: stem extends downward
        if (note.isUpper()) {
            return STEM_LENGTH;
        }

        // Stem up: bottom is just below note head
        return HALF_NOTE_HEAD;
    }

    // ==========================================================================
    // Syllable Handling
    // ==========================================================================

    /**
     * Gets the syllable text for a note.
     * Syllables are stored in note.acceleration.syllable.
     *
     * @param note The note
     * @return Syllable text, or null if none
     */
    private @Nullable String getSyllable(@NotNull Note note) {
        return note.acceleration.syllable;
    }

    /**
     * Measures the width of a syllable in pixels.
     *
     * @param syllable The syllable text
     * @return Width in pixels, or 0 if null/empty
     */
    private double measureSyllableWidth(@Nullable String syllable) {
        if (syllable == null || syllable.isEmpty()) {
            return 0;
        }

        return lyricsFontMetrics.stringWidth(syllable);
    }

    // ==========================================================================
    // Beam Group Lookup
    // ==========================================================================

    /**
     * Finds the beam group that contains a note.
     *
     * @param note The note to look up
     * @param line The line containing the note
     * @return The BeamGroup, or null if the note is not beamed
     */
    private @Nullable BeamGroup findBeamGroup(@NotNull Note note, @NotNull Line line) {
        for (var group : line.getBeamGroups()) {
            if (group.containsNote(note)) {
                return group;
            }
        }

        return null;
    }

    // ==========================================================================
    // Grace Notes
    // ==========================================================================

    /**
     * Gets the grace notes anchored to a main note.
     * <p>
     * Note: Grace notes are not yet fully implemented in the data model.
     * This method returns an empty list for now.
     *
     * @param note The main note
     * @return List of grace notes (empty for now)
     */
    private @NotNull List<Note> getGraceNotes(@NotNull Note note) {
        // TODO: Implement grace note retrieval when data model supports it
        // Grace notes would be stored on the main note or looked up from the line
        return Collections.emptyList();
    }
}
