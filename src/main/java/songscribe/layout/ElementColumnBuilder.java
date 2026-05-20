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

import module java.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;


/**
 * Builds {@link ElementColumn} instances from a Line's elements.
 * <p>
 * This builder constructs the fundamental spacing units for the engraving system.
 * Each element (note, rest, barline, etc.) becomes an ElementColumn with:
 * <ul>
 *   <li>Horizontal extents (left for accidentals/grace notes, right for dots)</li>
 *   <li>Stem positions (for beam coordination)</li>
 *   <li>Syllable text and measured width (for lyric-driven spacing)</li>
 *   <li>Beam group reference (for internal spacing coordination)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * var builder = new ElementColumnBuilder(lyricRenderMetrics);
 * List<ElementColumn> columns = builder.buildColumns(line);
 * }</pre>
 */
public class ElementColumnBuilder {

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

    // Small note head width from SMuFL noteheadBlackSmall bounding box (ss)
    public static final double NOTE_HEAD_SMALL_WIDTH_SS =
        SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK_SMALL).width();

    // Half note head width (for left/right extent calculation) (ss)
    static final double HALF_NOTE_HEAD_SS = Engraving.NOTE_HEAD_WIDTH_SS / 2.0;

    private final LyricRenderMetrics lyricRenderMetrics;

    /**
     * Creates a new ElementColumnBuilder.
     *
     * @param lyricRenderMetrics Song-wide lyric render metrics (font + glyph widths)
     */
    public ElementColumnBuilder(LyricRenderMetrics lyricRenderMetrics) {
        this.lyricRenderMetrics = lyricRenderMetrics;
    }

    /**
     * Builds ElementColumns for all elements in a line.
     *
     * @param line The line to process
     * @return List of ElementColumns in element order
     */
    public List<ElementColumn> buildColumns(Line line) {
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            return Collections.emptyList();
        }

        var columns = new ArrayList<ElementColumn>(elementCount);

        for (var i = 0; i < elementCount; i++) {
            var element = line.getElement(i);
            var column = buildColumn(element, line);
            columns.add(column);
        }

        return columns;
    }

    /**
     * Builds a single ElementColumn for an element.
     *
     * @param element The element to process
     * @param line    The line containing the element (for beaming lookup)
     * @return The constructed ElementColumn
     */
    public ElementColumn buildColumn(StaffElement element, Line line) {
        // Determine beam membership first — needed for right extent calculation
        var elementIndex = line.getElementIndex(element);
        var beamed = line.findBeamAt(elementIndex) != null;

        // Calculate horizontal extents
        var leftExtentSs = calculateLeftExtentSs(element);
        var rightExtentSs = calculateRightExtentSs(element, beamed, element.isUpper());

        // Calculate stem positions
        var stemTopSs = calculateStemTopSs(element);
        var stemBottomSs = calculateStemBottomSs(element);

        // Get syllable and measure width
        var syllable = getSyllable(element);
        var syllableWidthSs = measureSyllableWidthSs(syllable);

        // Get grace notes (currently not implemented in data model)
        var graceNotes = getGraceNotes(element);

        var column = new ElementColumn(
            element,
            graceNotes,
            leftExtentSs,
            rightExtentSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllableWidthSs,
            beamed
        );

        var mainLyric = element.getMainLyric();

        if (mainLyric != null) {
            var syllabic = mainLyric.syllabic();
            var isHyphenated = syllabic == Lyric.Syllabic.BEGIN || syllabic == Lyric.Syllabic.MIDDLE;
            column.setMinGapToNextSyllableSs(isHyphenated
                ? lyricRenderMetrics.preferredHyphenCellWidthSs()
                : lyricRenderMetrics.spaceWidthSs());
        }

        return column;
    }

    // ==========================================================================
    // Horizontal Extent Calculations
    // ==========================================================================

    /**
     * Calculates the left extent of an element column.
     * This includes any accidental to the left of the element head.
     *
     * @param element The element
     * @return Left extent in ss relative to element head left edge (glyph origin);
     *         0.0 with no accidental, negative when an accidental is present
     */
    public static double calculateLeftExtentSs(StaffElement element) {
        // Element head left edge is at xSs (the glyph origin), so the base extent is 0
        var extentSs = 0.0;

        // Add accidental width if present
        if (element.getAccidental() != null) {
            double accidentalWidthSs = NoteGeometry.getAccidentalWidthSs(element);
            extentSs -= (accidentalWidthSs + ACCIDENTAL_GAP_SS);
        }

        return extentSs;
    }

    /**
     * Calculates the right extent of an element column.
     * This includes the element head right edge, any dots, and the flag extent for unbeamed
     * flagged elements (8th, 16th, 32nd, grace quaver).
     *
     * @param element The element
     * @param beamed  {@code true} if the element is part of a beam group (flags are suppressed)
     * @param upper   {@code true} if the stem goes up; affects which stem anchor is used
     * @return Right extent in ss relative to element head left edge (glyph origin)
     */
    public static double calculateRightExtentSs(StaffElement element, boolean beamed, boolean upper) {
        var type = element.getType();

        // Non-note elements (rests, barlines, breath marks, repeats) use their
        // actual visual width — they have no dots or flags.
        if (!type.isNote()) {
            return type.getElementWidthSs();
        }

        // Element head right edge: use small notehead width for grace notes
        var noteheadRightExtent = getNoteheadRightExtent(element, type);

        // Flag extent: only for unbeamed elements that have a flag
        var flagGlyph = type.getFlagGlyph(upper);
        var flagRightExtent = 0.0;

        if (!beamed && flagGlyph != null) {
            var flagAdvanceWidthSs = SMuFLMetadata.getAdvanceWidthOrZero(flagGlyph);

            // Grace notes always stem up, use the small notehead anchor.
            // Use explicit if/else instead of a nested ternary for clarity.
            double stemAnchorX;

            if (type.isGraceNote()) {
                stemAnchorX = NoteGeometry.STEM_UP_SE_BLACK_SMALL.x();
            } else if (upper) {
                stemAnchorX = Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x();
            } else {
                stemAnchorX = Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW.x();
            }

            flagRightExtent = stemAnchorX + flagAdvanceWidthSs;
        }

        return Math.max(noteheadRightExtent, flagRightExtent);
    }

    private static double getNoteheadRightExtent(StaffElement element, ElementType type) {
        var noteheadRightExtent = type.isGraceNote()
            ? NOTE_HEAD_SMALL_WIDTH_SS
            : Engraving.NOTE_HEAD_WIDTH_SS;

        // Add dot widths if present
        var dotCount = element.getDotCount();

        if (dotCount > 0) {
            // First dot: gap + dot
            noteheadRightExtent += DOT_GAP_SS + DOT_WIDTH_SS;

            // Additional dots: gap + dot each
            for (var i = 1; i < dotCount; i++) {
                noteheadRightExtent += DOT_GAP_SS + DOT_WIDTH_SS;
            }
        }

        return noteheadRightExtent;
    }

    // ==========================================================================
    // Stem Calculations
    // ==========================================================================

    /**
     * Calculates the Y position of the stem top.
     * For stem-up elements, this is above the element head.
     * For stem-down or stemless elements, this is the element head top.
     *
     * @param element The element
     * @return Stem top Y position in ss (relative to staff, negative = above)
     */
    private double calculateStemTopSs(StaffElement element) {
        var elementType = element.getType();

        // Rests and stemless elements: use element head top
        if (!elementType.isNoteWithStem()) {
            return -HALF_NOTE_HEAD_SS;
        }

        // Stem up: stem extends upward
        if (!element.isUpper()) {
            return -NoteGeometry.STEM_LENGTH_SS;
        }

        // Stem down: top is just above element head
        return -HALF_NOTE_HEAD_SS;
    }

    /**
     * Calculates the Y position of the stem bottom.
     * For stem-down elements, this is below the element head.
     * For stem-up or stemless elements, this is the element head bottom.
     *
     * @param element The element
     * @return Stem bottom Y position in ss (relative to staff, positive = below)
     */
    private double calculateStemBottomSs(StaffElement element) {
        var elementType = element.getType();

        // Rests and stemless elements: use element head bottom
        if (!elementType.isNoteWithStem()) {
            return HALF_NOTE_HEAD_SS;
        }

        // Stem down: stem extends downward
        if (element.isUpper()) {
            return NoteGeometry.STEM_LENGTH_SS;
        }

        // Stem up: bottom is just below element head
        return HALF_NOTE_HEAD_SS;
    }

    // ==========================================================================
    // Syllable Handling
    // ==========================================================================

    /**
     * Gets the syllable text for an element.
     *
     * @param element The element
     * @return Syllable text, or null if none
     */
    private @Nullable String getSyllable(StaffElement element) {
        var lyric = element.getMainLyric();
        return lyric != null ? lyric.text() : null;
    }

    private double measureSyllableWidthSs(@Nullable String syllable) {
        if (syllable == null || syllable.isEmpty()) {
            return 0;
        }

        return lyricRenderMetrics.lyricBoxWidthSs(syllable);
    }

    // ==========================================================================
    // Grace Notes
    // ==========================================================================

    /**
     * Gets the grace notes anchored to a main element.
     * <p>
     * Note: Grace notes are not yet fully implemented in the data model.
     * This method returns an empty list for now.
     *
     * @param element The main element
     * @return List of grace notes (empty for now)
     */
    private List<StaffElement> getGraceNotes(StaffElement element) {
        // TODO: Implement grace note retrieval when data model supports it
        // Grace notes would be stored on the main element or looked up from the line
        return Collections.emptyList();
    }
}
