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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
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

    private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();

    // Note head width from SMuFL noteheadBlack bounding box (ss)
    private static final double NOTE_HEAD_WIDTH_SS =
        METADATA.getBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();

    // Half note head width (for left/right extent calculation) (ss)
    static final double HALF_NOTE_HEAD_SS = NOTE_HEAD_WIDTH_SS / 2.0;

    // Dot width and spacing (ss)
    private static final double DOT_WIDTH_SS = 0.5;
    private static final double DOT_GAP_SS = 0.25;

    // Accidental widths from SMuFL advance widths (ss)
    private static final Map<Note.Accidental, Double> ACCIDENTAL_WIDTHS = computeAccidentalWidths();
    private static final double ACCIDENTAL_GAP_SS = 0.25;

    // SMuFL standard stem length (ss)
    private static final double STEM_LENGTH_SS = 3.5;

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
        double leftExtentSs = calculateLeftExtentSs(note);
        double rightExtentSs = calculateRightExtentSs(note);

        // Calculate stem positions
        double stemTopSs = calculateStemTopSs(note);
        double stemBottomSs = calculateStemBottomSs(note);

        // Get syllable and measure width
        String syllable = getSyllable(note);
        double syllableWidthSs = measureSyllableWidthSs(syllable);

        // Find beam group (if any)
        BeamGroup beamGroup = findBeamGroup(note, line);

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
     * @return Left extent in ss (negative value, extends left from note head center)
     */
    public static double calculateLeftExtentSs(@NotNull Note note) {
        // Start with half the note head width
        double extentSs = -HALF_NOTE_HEAD_SS;

        // Add accidental width if present
        var accidental = note.getAccidental();

        if (accidental != Note.Accidental.NONE) {
            double accidentalWidthSs = getAccidentalWidthSs(accidental);
            extentSs -= (accidentalWidthSs + ACCIDENTAL_GAP_SS);
        }

        return extentSs;
    }

    /**
     * Calculates the right extent of a note column.
     * This includes the note head right edge plus any dots.
     *
     * @param note The note
     * @return Right extent in ss (positive value, extends right from note head center)
     */
    public static double calculateRightExtentSs(@NotNull Note note) {
        // Start with half the note head width
        double extentSs = HALF_NOTE_HEAD_SS;

        // Add dot widths if present
        int dotCount = note.getDotCount();

        if (dotCount > 0) {
            // First dot: gap + dot
            extentSs += DOT_GAP_SS + DOT_WIDTH_SS;

            // Additional dots: gap + dot each
            for (var i = 1; i < dotCount; i++) {
                extentSs += DOT_GAP_SS + DOT_WIDTH_SS;
            }
        }

        return extentSs;
    }

    /**
     * Returns the width of an accidental in ss, derived from SMuFL advance widths.
     */
    public static double getAccidentalWidthSs(@NotNull Note.Accidental accidental) {
        return ACCIDENTAL_WIDTHS.getOrDefault(accidental, 0.0);
    }

    /**
     * Computes the width of each accidental in ss using SMuFL advance widths.
     * Compound accidentals (double natural, natural+flat, natural+sharp) sum individual widths.
     */
    private static @NotNull Map<Note.Accidental, Double> computeAccidentalWidths() {
        double naturalW = advanceWidthSs(SMuFLGlyph.ACCIDENTAL_NATURAL);
        double flatW = advanceWidthSs(SMuFLGlyph.ACCIDENTAL_FLAT);
        double sharpW = advanceWidthSs(SMuFLGlyph.ACCIDENTAL_SHARP);

        var widths = new EnumMap<Note.Accidental, Double>(Note.Accidental.class);
        widths.put(Note.Accidental.NATURAL, naturalW);
        widths.put(Note.Accidental.FLAT, flatW);
        widths.put(Note.Accidental.SHARP, sharpW);
        widths.put(Note.Accidental.DOUBLE_NATURAL, naturalW + naturalW);
        widths.put(Note.Accidental.DOUBLE_FLAT, advanceWidthSs(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT));
        widths.put(Note.Accidental.DOUBLE_SHARP, advanceWidthSs(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP));
        widths.put(Note.Accidental.NATURAL_FLAT, naturalW + flatW);
        widths.put(Note.Accidental.NATURAL_SHARP, naturalW + sharpW);

        return widths;
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
            return -STEM_LENGTH_SS;
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
            return STEM_LENGTH_SS;
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
