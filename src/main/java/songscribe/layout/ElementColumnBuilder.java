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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Builds {@link ElementColumn} instances from a Line's elements.
 */
public class ElementColumnBuilder {

    /**
     * Grace notehead width: the regular black notehead at {@link ElementType#GRACE_NOTE_SCALE}.
     * Deliberately <em>not</em> SMuFL's {@code noteheadBlackSmall}, a cue-staff glyph wider than
     * the head actually drawn. The layout does not read this; it is the independent cross-check
     * the tests assert against.
     */
    public static final double GRACE_NOTE_HEAD_WIDTH_SS =
        SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).widthSs() * ElementType.GRACE_NOTE_SCALE;

    // Non-hyphenated syllables reserve more than the bare space glyph, so consecutive words
    // read as clearly separated rather than crowded.
    static final double NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER = 1.5;

    private final LyricRenderMetrics lyricRenderMetrics;

    public ElementColumnBuilder(LyricRenderMetrics lyricRenderMetrics) {
        this.lyricRenderMetrics = lyricRenderMetrics;
    }

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

    public ElementColumn buildColumn(StaffElement element, Line line, int elementIndex) {
        // Determine beam membership first — needed for right extent calculation. A grace
        // note sitting inside a beam span is not a member: the beam passes over it and it
        // keeps its own short stem and flag.
        var beam = line.spanRegistry().findBeamJoining(elementIndex);
        return buildColumnForBeam(element, beam, line.getSong().getActiveScript());
    }

    /**
     * Carries the element's real syllable data but no beam membership, since beam grouping is
     * determined by position on a line the element does not yet occupy. The script is passed in
     * because an element off a line cannot find the active script.
     */
    public ElementColumn buildDetachedColumn(StaffElement element, Song.Script activeScript) {
        return buildColumnForBeam(element, null, activeScript);
    }

    private ElementColumn buildColumnForBeam(StaffElement element, @Nullable Beam beam, Song.Script activeScript) {
        var beaming = Beaming.of(beam);
        var extents = new ElementColumn.Extents(
            calculateLeftExtentSs(element),
            calculateRightExtentSs(element, beaming),
            calculateRightExtentExcludingAugmentationSs(element, beaming),
            calculateStemTopSs(element),
            calculateStemBottomSs(element));

        var lyric = element.getLyricForScript(activeScript);
        var measuredLyric = lyric != null
            ? new ElementColumn.MeasuredLyric(lyric, measureSyllableWidthSs(lyric.text()))
            : null;
        var column = new ElementColumn(element, extents, measuredLyric, beam);

        if (lyric != null) {
            column.setSyllableFirstGraphemeWidthSs(lyricRenderMetrics.firstGraphemeWidthSs(lyric.text()));
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
        // more than one hyphen.
        var gapToNextSyllableSs = isHyphenated
            ? lyricRenderMetrics.hyphenWidthSs()
            : lyricRenderMetrics.spaceWidthSs() * NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER;
        // Ideal gap and hard collision floor coincide: this is already the tightest the syllables
        // may sit, so a lyric gap is never widened past what it needs, nor compressed below it.
        column.setMinGapToNextSyllableSs(gapToNextSyllableSs);
        column.setMinCollisionGapToNextSyllableSs(gapToNextSyllableSs);

        return column;
    }

    /**
     * @return the column; its X is unset
     */
    static ElementColumn buildLightweightColumn(StaffElement element) {
        var extents = new ElementColumn.Extents(
            calculateLeftExtentSs(element),
            calculateRightExtentSs(element, Beaming.UNBEAMED),
            calculateRightExtentExcludingAugmentationSs(element, Beaming.UNBEAMED),
            0,
            0);

        return new ElementColumn(element, extents, null, null);
    }

    // ==========================================================================
    // Horizontal Extent Calculations
    // ==========================================================================

    public static double calculateLeftExtentSs(StaffElement element) {
        // Element head left edge is at xSs (the glyph origin), so the extent starts at 0
        var extentSs = 0.0;

        // Subtract accidental width if present
        if (element instanceof NoteElement note && note.drawnAccidental() != null) {
            var accidentalWidthSs = NoteGeometry.getAccidentalWidthSs(note);
            extentSs -= (accidentalWidthSs + NoteGeometry.ACCIDENTAL_PADDING_SS);
        }

        return extentSs;
    }

    /** Includes dots and fall, for minimum spacing. */
    public static double calculateRightExtentSs(StaffElement element, Beaming beaming) {
        return calculateRightExtentInternal(element, beaming, Augmentation.INCLUDED);
    }

    /** Excludes dots and fall, for comfortable (default) spacing. */
    public static double calculateRightExtentExcludingAugmentationSs(
        StaffElement element, Beaming beaming) {

        return calculateRightExtentInternal(element, beaming, Augmentation.EXCLUDED);
    }

    private enum Augmentation {
        INCLUDED,
        EXCLUDED
    }

    /**
     * The direction that decides a note's right extent is the drawn one
     * ({@link NoteGeometry#effectiveDirection}).
     */
    private static double calculateRightExtentInternal(
        StaffElement element,
        Beaming beaming,
        Augmentation augmentation) {

        var includeAugmentation = augmentation == Augmentation.INCLUDED;

        // Non-note elements (rests, barlines, breath marks, repeats) use their
        // actual visual width — they have no dots or flags.
        if (!(element instanceof NoteElement note)) {
            return element.getGlyphWidthSs();
        }

        var direction = NoteGeometry.effectiveDirection(note);

        // Element head right edge — per type, so a whole note is measured as a whole note and a
        // grace note as the grace-scaled head.
        var noteheadRightExtentSs = note.getType().getElementWidthSs();

        // Augmentation dots extend the right edge. Derived from the same positions the renderer
        // draws (see NoteGeometry.dotsRightExtentSs), so spacing reserves exactly what is painted —
        // the dots already clear the flag when an unbeamed up-stem one is present.
        var baseRightExtentSs = includeAugmentation
            ? NoteGeometry.dotsRightExtentSs(note, beaming, direction, noteheadRightExtentSs)
            : noteheadRightExtentSs;

        // Flag extent: only for unbeamed elements that have a flag
        var flagBBox = NoteGeometry.flagBBoxLocalSs(note, direction, beaming);
        var flagRightExtentSs = (flagBBox == null) ? 0.0 : flagBBox.getMaxX();

        var rightExtentSs = Math.max(baseRightExtentSs, flagRightExtentSs);

        // A fall's glyph hangs off the note's right edge, one gap past the column edge. Reserve that
        // gap plus the glyph's advance width so the next element (or a barline / end-of-line) does
        // not overlap it. Unlike a connecting glissando — whose room is reserved between two notes by
        // HorizontalSpacingCalculator's glissando reservation — a fall has no following note, so its
        // own column must carry the reservation.
        // Gated on the augmentation (like dots) so the fall only expands the minimum-spacing
        // extent, not the comfortable-spacing extent — the next element shifts only when the minimum
        // gap would otherwise be violated.
        if (includeAugmentation && note.hasFall()) {
            var fallAdvanceWidthSs = SMuFLMetadata.advanceWidthSs(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
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
     * The direction and the length are the <em>drawn</em> ones
     * ({@link NoteGeometry#effectiveDirection}, {@link NoteGeometry#stemLengthSs}), so
     * {@code getAbsoluteTopYSs()} stays on the real tip. See {@code docs/layout-geometry.md}.
     */
    double calculateStemTopSs(StaffElement element) {
        var elementType = element.getType();

        // Rests and stemless elements: use the element's own glyph bbox top
        if (!(element instanceof NoteElement note) || !elementType.isNoteWithStem()) {
            return elementType.getNoteheadTopOffsetSs();
        }

        var direction = NoteGeometry.effectiveDirection(note);

        // Stem up: stem extends upward, so the top is the stem tip (shortened if forced)
        if (direction.isUp()) {
            return -(NoteGeometry.stemLengthSs(elementType) - NoteGeometry.forcedShorteningSs(note));
        }

        // Stem down: top is the notehead top
        return elementType.getNoteheadTopOffsetSs();
    }

    /**
     * Direction and length are the drawn ones — see {@link #calculateStemTopSs}.
     */
    double calculateStemBottomSs(StaffElement element) {
        var elementType = element.getType();

        // Rests and stemless elements: use the element's own glyph bbox bottom
        if (!(element instanceof NoteElement note) || !elementType.isNoteWithStem()) {
            return elementType.getNoteheadBottomOffsetSs();
        }

        var direction = NoteGeometry.effectiveDirection(note);

        // Stem down: stem extends downward, so the bottom is the stem tip (shortened if forced)
        if (direction.isDown()) {
            return NoteGeometry.stemLengthSs(elementType) - NoteGeometry.forcedShorteningSs(note);
        }

        // Stem up: bottom is the notehead bottom
        return elementType.getNoteheadBottomOffsetSs();
    }

    // ==========================================================================
    // Syllable Handling
    // ==========================================================================

    private double measureSyllableWidthSs(String syllable) {
        if (syllable.isEmpty()) {
            return 0;
        }

        return lyricRenderMetrics.lyricBoxWidthSs(syllable);
    }
}
