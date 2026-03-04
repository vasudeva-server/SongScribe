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

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.renderer.NoteRenderer;

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

    private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();

    // Note head width from SMuFL noteheadBlack bounding box (ss)
    public static final double NOTE_HEAD_WIDTH_SS =
        METADATA.getBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();

    // Small note head width from SMuFL noteheadBlackSmall bounding box (ss)
    public static final double NOTE_HEAD_SMALL_WIDTH_SS =
        METADATA.getBBox(SMuFLGlyph.NOTEHEAD_BLACK_SMALL).width();

    // Half note head width (for left/right extent calculation) (ss)
    static final double HALF_NOTE_HEAD_SS = NOTE_HEAD_WIDTH_SS / 2.0;

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
     * @param line The line containing the note (for beaming lookup)
     * @return The constructed NoteColumn
     */
    public @NotNull NoteColumn buildColumn(@NotNull Note note, @NotNull Line line) {
        // Determine beam membership first — needed for right extent calculation
        int noteIndex = line.getNoteIndex(note);
        boolean beamed = line.getBeamings().findInterval(noteIndex) != null;

        // Calculate horizontal extents
        double leftExtentSs = calculateLeftExtentSs(note);
        double rightExtentSs = calculateRightExtentSs(note, beamed, note.isUpper());

        // Calculate stem positions
        double stemTopSs = calculateStemTopSs(note);
        double stemBottomSs = calculateStemBottomSs(note);

        // Get syllable and measure width
        String syllable = getSyllable(note);
        double syllableWidthSs = measureSyllableWidthSs(syllable);

        // Get grace notes (currently not implemented in data model)
        List<Note> graceNotes = getGraceNotes(note);

        return new NoteColumn(
            note,
            graceNotes,
            leftExtentSs,
            rightExtentSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllableWidthSs,
            beamed
        );
    }

    // ==========================================================================
    // Horizontal Extent Calculations
    // ==========================================================================

    /**
     * Calculates the left extent of a note column.
     * This includes any accidental to the left of the note head.
     *
     * @param note The note
     * @return Left extent in ss relative to note head left edge (glyph origin);
     *         0.0 with no accidental, negative when an accidental is present
     */
    public static double calculateLeftExtentSs(@NotNull Note note) {
        // Note head left edge is at xSs (the glyph origin), so the base extent is 0
        double extentSs = 0.0;

        // Add accidental width if present
        if (note.getAccidental() != Note.Accidental.NONE) {
            double accidentalWidthSs = NoteRenderer.getAccidentalWidthSs(note);
            extentSs -= (accidentalWidthSs + LayoutConstants.ACCIDENTAL_GAP_SS);
        }

        return extentSs;
    }

    /**
     * Calculates the right extent of a note column.
     * This includes the note head right edge, any dots, and the flag extent for unbeamed
     * flagged notes (8th, 16th, 32nd, grace quaver).
     *
     * @param note   The note
     * @param beamed {@code true} if the note is part of a beam group (flags are suppressed)
     * @param upper  {@code true} if the stem goes up; affects which stem anchor is used
     * @return Right extent in ss relative to note head left edge (glyph origin)
     */
    public static double calculateRightExtentSs(@NotNull Note note, boolean beamed, boolean upper) {
        // Note head right edge: use small notehead width for grace notes
        double noteheadRightExtent = note.getNoteType().isGraceNote()
            ? NOTE_HEAD_SMALL_WIDTH_SS
            : NOTE_HEAD_WIDTH_SS;

        // Add dot widths if present
        int dotCount = note.getDotCount();

        if (dotCount > 0) {
            // First dot: gap + dot
            noteheadRightExtent += LayoutConstants.DOT_GAP_SS + LayoutConstants.DOT_WIDTH_SS;

            // Additional dots: gap + dot each
            for (var i = 1; i < dotCount; i++) {
                noteheadRightExtent += LayoutConstants.DOT_GAP_SS + LayoutConstants.DOT_WIDTH_SS;
            }
        }

        // Flag extent: only for unbeamed notes that have a flag
        var flagGlyph = note.getNoteType().getFlagGlyph(upper);
        double flagRightExtent = 0.0;

        if (!beamed && flagGlyph != null) {
            double flagAdvanceWidthSs = advanceWidthSs(flagGlyph);

            // Grace notes always stem up, use the small notehead anchor
            double stemAnchorX = note.getNoteType().isGraceNote()
                ? LayoutConstants.STEM_UP_SE_BLACK_SMALL.x()
                : (upper ? LayoutConstants.STEM_UP_SE_BLACK.x() : LayoutConstants.STEM_DOWN_NW_BLACK.x());

            flagRightExtent = stemAnchorX + flagAdvanceWidthSs;
        }

        return Math.max(noteheadRightExtent, flagRightExtent);
    }

    private static double advanceWidthSs(SMuFLGlyph glyph) {
        var width = METADATA.getAdvanceWidth(glyph);
        return width != null ? width : 0.0;
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
     * @return Stem top Y position in ss (relative to staff, negative = above)
     */
    private double calculateStemTopSs(@NotNull Note note) {
        var noteType = note.getNoteType();

        // Rests and stemless notes: use note head top
        if (!noteType.isNoteWithStem()) {
            return -HALF_NOTE_HEAD_SS;
        }

        // Stem up: stem extends upward
        if (!note.isUpper()) {
            return -LayoutConstants.STEM_LENGTH_SS;
        }

        // Stem down: top is just above note head
        return -HALF_NOTE_HEAD_SS;
    }

    /**
     * Calculates the Y position of the stem bottom.
     * For stem-down notes, this is below the note head.
     * For stem-up or stemless notes, this is the note head bottom.
     *
     * @param note The note
     * @return Stem bottom Y position in ss (relative to staff, positive = below)
     */
    private double calculateStemBottomSs(@NotNull Note note) {
        var noteType = note.getNoteType();

        // Rests and stemless notes: use note head bottom
        if (!noteType.isNoteWithStem()) {
            return HALF_NOTE_HEAD_SS;
        }

        // Stem down: stem extends downward
        if (note.isUpper()) {
            return LayoutConstants.STEM_LENGTH_SS;
        }

        // Stem up: bottom is just below note head
        return HALF_NOTE_HEAD_SS;
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
        return note.properties.syllable;
    }

    /**
     * Measures the width of a syllable in staff-space units.
     * Text is measured in pixels via FontMetrics, then converted to ss.
     *
     * @param syllable The syllable text
     * @return Width in ss, or 0 if null/empty
     */
    private double measureSyllableWidthSs(@Nullable String syllable) {
        if (syllable == null || syllable.isEmpty()) {
            return 0;
        }

        double widthPx = lyricsFontMetrics.stringWidth(syllable);
        return ScaleContext.getInstance().fromPixels(widthPx);
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
