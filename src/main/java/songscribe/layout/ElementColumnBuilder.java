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

import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.engraving.SMuFLConstants;
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
     * Grace notehead width (ss) — the regular black notehead at {@link ElementType#GRACE_NOTE_SCALE},
     * which is exactly what {@code NoteRenderer.renderNoteHead} paints for a grace note (the
     * {@code NOTEHEAD_BLACK} glyph in the grace-scaled font).
     *
     * <p>Deliberately <em>not</em> SMuFL's {@code noteheadBlackSmall}: Bravura's {@code *Small}
     * optional glyphs are cue-<em>staff</em> glyphs, sized for a reduced staff rather than reduced
     * against a full-size one, so that bbox is 1.408 ss — wider than the full-size notehead it is
     * supposed to shrink, and 0.52 ss wider than the head actually drawn. Reserving it made every
     * grace column charge phantom ink into its gaps (refs #560).
     *
     * <p>The layout itself no longer reads this: it consults
     * {@code ElementType.GRACE_QUAVER.getElementWidthSs()}, which derives the same number by a
     * different route. The constant is kept as the independent cross-check the tests assert against,
     * so a regression back to {@code noteheadBlackSmall} fails a test instead of passing unnoticed.
     */
    public static final double GRACE_NOTE_HEAD_WIDTH_SS =
        SMuFLConstants.NOTE_HEAD_WIDTH_SS * ElementType.GRACE_NOTE_SCALE;

    // Non-hyphenated syllables reserve more than the bare space glyph, so consecutive words
    // read as clearly separated rather than crowded.
    static final double NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER = 1.5;

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
            columns.add(buildColumn(line.getElement(i), line, i));
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
        return buildColumn(element, line, line.getElementIndex(element));
    }

    /**
     * Builds a single ElementColumn for an element whose index on the line is already known.
     *
     * @param element      The element to process
     * @param line         The line containing the element (for beaming lookup)
     * @param elementIndex {@code element}'s index on {@code line}
     * @return The constructed ElementColumn
     */
    public ElementColumn buildColumn(StaffElement element, Line line, int elementIndex) {
        // Determine beam membership first — needed for right extent calculation. A grace
        // note sitting inside a beam span is not a member: the beam passes over it and it
        // keeps its own short stem and flag (refs #592).
        var beam = element.getType().isGraceNote() ? null : line.findBeamAt(elementIndex);
        return buildColumnForBeam(element, beam, line.getSong().getActiveVerse());
    }

    /**
     * Builds a column for an element not attached to any line — a fragment clone about to be
     * pasted. Carries the element's real syllable data (so a paste preview is spaced by the same
     * lyric rules as the committed layout) but no beam membership, since beam grouping is
     * determined by position on a line the element does not yet occupy.
     * <p>
     * The verse is passed in for the same reason: an element off a line cannot be asked which song
     * it belongs to, so it cannot find the active verse on its own.
     *
     * @param element     The element to process (not necessarily on any line)
     * @param activeVerse The verse being laid out, from {@link songscribe.dom.Song#getActiveVerse()}
     * @return The constructed ElementColumn, unbeamed
     */
    public ElementColumn buildDetachedColumn(StaffElement element, int activeVerse) {
        return buildColumnForBeam(element, null, activeVerse);
    }

    /**
     * Builds an ElementColumn for {@code element}, beamed iff {@code beam} is non-null.
     * A detached element (a fragment clone not yet on a line) has no beam membership.
     *
     * @param element     The element to process
     * @param beam        The beam {@code element} belongs to, or null if it is unbeamed
     * @param activeVerse The verse being laid out — the only one this column knows about
     * @return The constructed ElementColumn
     */
    private ElementColumn buildColumnForBeam(StaffElement element, @Nullable Beam beam, int activeVerse) {
        var beamed = beam != null;

        // Calculate horizontal extents
        var leftExtentSs = calculateLeftExtentSs(element);
        var rightExtentSs = calculateRightExtentSs(element, beamed, element.getDirection());
        var rightExtentExcludingAugmentationSs = calculateRightExtentExcludingAugmentationSs(element, beamed, element.getDirection());

        // Calculate stem positions
        var stemTopSs = calculateStemTopSs(element);
        var stemBottomSs = calculateStemBottomSs(element);

        // Get syllable and measure width
        var lyric = element.getLyricForVerse(activeVerse);
        var syllable = lyric != null ? lyric.text() : null;
        var syllableWidthSs = measureSyllableWidthSs(syllable);

        // Get grace notes (currently not implemented in data model)
        var graceNotes = getGraceNotes(element);

        var column = new ElementColumn(
            element,
            graceNotes,
            leftExtentSs,
            rightExtentSs,
            rightExtentExcludingAugmentationSs,
            stemTopSs,
            stemBottomSs,
            lyric,
            syllableWidthSs,
            beamed
        );

        if (syllable != null) {
            column.setSyllableFirstGraphemeWidthSs(lyricRenderMetrics.firstGraphemeWidthSs(syllable));
        }

        // Lyric centering must use the notehead alone, not the (possibly flag-inflated) right extent,
        // so a flag never shifts the syllable off the notehead. Non-note columns keep the default
        // (their augmentation-excluded right extent).
        if (element.getType().isNote()) {
            column.setNoteheadWidthSs(element.getType().getElementWidthSs());
        }

        var syllabic = lyric != null ? lyric.syllabic() : null;
        var isHyphenated = Lyric.syllabicContinues(syllabic);
        // Every column reserves a gap to the next syllable, so a syllable that follows a
        // lyric-less element still clears it by NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER times
        // the lyric space width. A hyphenated syllable
        // reserves the bare hyphen glyph advance — glyph plus its side bearings and nothing more,
        // so a lone hyphen sits in the tightest gap that still keeps the syllables apart. The
        // wider LyricRenderMetrics#preferredHyphenCellWidthSs is a distribution cell, not a
        // spacing requirement: LyricConnectorRenderer only steps up to it once a gap has room for
        // more than one hyphen (refs #638).
        var gapToNextSyllableSs = isHyphenated
            ? lyricRenderMetrics.hyphenWidthSs()
            : lyricRenderMetrics.spaceWidthSs() * NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER;
        // Ideal gap and hard collision floor coincide: this is already the tightest the syllables
        // may sit, so a lyric gap is never widened past what it needs, nor compressed below it.
        column.setMinGapToNextSyllableSs(gapToNextSyllableSs);
        column.setMinCollisionGapToNextSyllableSs(gapToNextSyllableSs);
        // Tag the column with its beam group (anchor index) so adjacent beam groups stay
        // distinct and the spacing calculator does not merge them.
        column.setBeamGroupId(beam != null ? beam.getAnchorElementIndex() : ElementColumn.NO_BEAM_GROUP);

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
        // Element head left edge is at xSs (the glyph origin), so the extent starts at 0
        var extentSs = 0.0;

        // Subtract accidental width if present
        if (element.getAccidental() != null) {
            var accidentalWidthSs = NoteGeometry.getAccidentalWidthSs(element);
            extentSs -= (accidentalWidthSs + NoteGeometry.ACCIDENTAL_PADDING_SS);
        }

        return extentSs;
    }

    /**
     * Calculates the right extent of an element column.
     * This includes the element head right edge, any dots, any fall reservation, and the flag
     * extent for unbeamed flagged elements (8th, 16th, 32nd, grace quaver).
     * <p>
     * Used for minimum spacing calculations, which must account for dots and fall to prevent overlap.
     *
     * @param element   The element
     * @param beamed    {@code true} if the element is part of a beam group (flags are suppressed)
     * @param direction The stem direction; affects which stem anchor is used
     * @return Right extent in ss relative to element head left edge (glyph origin)
     */
    public static double calculateRightExtentSs(StaffElement element, boolean beamed, StaffElement.Direction direction) {
        return calculateRightExtentInternal(element, beamed, direction, true);
    }

    /**
     * Calculates the right extent of an element column, excluding augmentation dots and fall.
     * <p>
     * Used for comfortable (default) spacing so dots and a fall do not push the next element
     * unless the minimum gap would otherwise be violated — mirroring how accidentals
     * on the left do not widen the gap beyond the comfortable default (refs #441, #492).
     *
     * @param element   The element
     * @param beamed    {@code true} if the element is part of a beam group (flags are suppressed)
     * @param direction The stem direction; affects which stem anchor is used
     * @return Right extent in ss relative to element head left edge (glyph origin), dots and fall excluded
     */
    public static double calculateRightExtentExcludingAugmentationSs(StaffElement element, boolean beamed, StaffElement.Direction direction) {
        return calculateRightExtentInternal(element, beamed, direction, false);
    }

    private static double calculateRightExtentInternal(
        StaffElement element,
        boolean beamed,
        StaffElement.Direction direction,
        boolean includeAugmentation) {

        var type = element.getType();

        // Non-note elements (rests, barlines, breath marks, repeats) use their
        // actual visual width — they have no dots or flags.
        if (!type.isNote()) {
            // A key signature is the one non-note type whose width is not a property of the type:
            // what it draws depends on the key it establishes and the key it cancels, so it comes
            // from the element. Every other non-note element reports exactly its type's width.
            return element instanceof KeyChangeElement keySignature
                ? keySignature.getContentWidthSs()
                : type.getElementWidthSs();
        }

        // Element head right edge — per type, so a whole note is measured as a whole note and a
        // grace note as the grace-scaled head.
        var noteheadRightExtentSs = type.getElementWidthSs();

        // Augmentation dots extend the right edge. Derived from the same positions the renderer
        // draws (see NoteGeometry.dotsRightExtentSs), so spacing reserves exactly what is painted —
        // the dots already clear the flag when an unbeamed up-stem one is present.
        var baseRightExtentSs = includeAugmentation
            ? NoteGeometry.dotsRightExtentSs(element, beamed, direction, noteheadRightExtentSs)
            : noteheadRightExtentSs;

        // Flag extent: only for unbeamed elements that have a flag
        var flagBBox = NoteGeometry.flagBBoxLocalSs(type, direction, beamed);
        var flagRightExtentSs = (flagBBox == null) ? 0.0 : flagBBox.getMaxX();

        var rightExtentSs = Math.max(baseRightExtentSs, flagRightExtentSs);

        // A fall's glyph hangs off the note's right edge, one gap past the column edge. Reserve that
        // gap plus the glyph's advance width so the next element (or a barline / end-of-line) does
        // not overlap it. Unlike a connecting glissando — whose room is reserved between two notes by
        // HorizontalSpacingCalculator's glissando reservation — a fall has no following note, so its
        // own column must carry the reservation.
        // Gated on includeAugmentation (like dots) so the fall only expands the minimum-spacing
        // extent, not the comfortable-spacing extent — the next element shifts only when the minimum
        // gap would otherwise be violated (refs #492).
        if (includeAugmentation && element.hasFall()) {
            var fallAdvanceWidthSs = SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
            // The fall anchors after the notehead/dots right edge, not the flag, so compute its
            // extent independently and take the max to avoid stacking it on top of the flag width.
            var fallRightExtentSs = baseRightExtentSs + NoteGeometry.FALL_GAP_SS + fallAdvanceWidthSs;
            rightExtentSs = Math.max(rightExtentSs, fallRightExtentSs);
        }

        return rightExtentSs;
    }

    // ==========================================================================
    // Stem Calculations
    // ==========================================================================

    /**
     * Calculates the Y position of the stem top.
     * For stem-up elements, this is the stem tip above the element head.
     * For stem-down or stemless elements, this is the top edge of the element's own glyph bounding
     * box ({@link ElementType#getNoteheadTopOffsetSs()}) — a rest reaches far higher than a
     * notehead, and a barline spans half the staff height — rather than a fixed notehead
     * half-height. See {@code docs/layout-geometry.md} for the annotated diagram.
     *
     * A stem forced into its unnatural direction is shortened (Ross &amp; Gourlay), so the reported
     * tip floats correspondingly lower — this keeps {@code getAbsoluteTopYSs()} on the real tip.
     *
     * <p>The direction and the length are the <em>drawn</em> ones
     * ({@link NoteGeometry#effectiveDirection}, {@link NoteGeometry#stemLengthSs}), so a grace note
     * — which always stems up on a shortened stem regardless of the direction it stores — reports
     * the tip it actually renders rather than a full-length stem in a direction it never draws.
     *
     * @param element The element
     * @return Stem top Y position in ss (relative to staff, negative = above)
     */
    double calculateStemTopSs(StaffElement element) {
        var elementType = element.getType();

        // Rests and stemless elements: use the element's own glyph bbox top
        if (!elementType.isNoteWithStem()) {
            return elementType.getNoteheadTopOffsetSs();
        }

        var direction = NoteGeometry.effectiveDirection(element);

        // Stem up: stem extends upward, so the top is the stem tip (shortened if forced)
        if (direction.isUp()) {
            var forcedShorteningSs = NoteGeometry.forcedShorteningSs(
                element.getStaffPosition(), direction, elementType.isGraceNote());
            return -(NoteGeometry.stemLengthSs(elementType) - forcedShorteningSs);
        }

        // Stem down: top is the notehead top
        return elementType.getNoteheadTopOffsetSs();
    }

    /**
     * Calculates the Y position of the stem bottom.
     * For stem-down elements, this is the stem tip below the element head.
     * For stem-up or stemless elements, this is the bottom edge of the element's own glyph bounding
     * box ({@link ElementType#getNoteheadBottomOffsetSs()}) — a rest reaches far lower than a
     * notehead, and a barline spans half the staff height — rather than a fixed notehead
     * half-height.
     *
     * A stem forced into its unnatural direction is shortened (Ross &amp; Gourlay), so the reported
     * tip floats correspondingly higher. Direction and length are the drawn ones — see
     * {@link #calculateStemTopSs}.
     *
     * @param element The element
     * @return Stem bottom Y position in ss (relative to staff, positive = below)
     */
    double calculateStemBottomSs(StaffElement element) {
        var elementType = element.getType();

        // Rests and stemless elements: use the element's own glyph bbox bottom
        if (!elementType.isNoteWithStem()) {
            return elementType.getNoteheadBottomOffsetSs();
        }

        var direction = NoteGeometry.effectiveDirection(element);

        // Stem down: stem extends downward, so the bottom is the stem tip (shortened if forced)
        if (direction.isDown()) {
            var forcedShorteningSs = NoteGeometry.forcedShorteningSs(
                element.getStaffPosition(), direction, elementType.isGraceNote());
            return NoteGeometry.stemLengthSs(elementType) - forcedShorteningSs;
        }

        // Stem up: bottom is the notehead bottom
        return elementType.getNoteheadBottomOffsetSs();
    }

    // ==========================================================================
    // Syllable Handling
    // ==========================================================================

    /**
     * Measures a syllable's advance width.
     *
     * @param syllable The syllable text, or null if the element carries none in the active verse
     * @return Syllable width in staff spaces, or 0 if there is no syllable
     */
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
