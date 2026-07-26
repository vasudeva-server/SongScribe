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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.engraving.SMuFLConstants;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

class ElementColumnBuilderTest extends UnitTest {

    private static final int DOUBLE_DOT_COUNT = 2;

    // ElementType derives the grace head from the notehead bbox, ElementColumnBuilder from the
    // notehead width constant; the two round the same glyph metric independently.
    private static final double GRACE_HEAD_TOLERANCE_SS = 1e-9;

    // Arbitrary non-zero values used only as mock stubs to pin exact return path in buildColumn
    private static final double HYPHEN_GLYPH_WIDTH_SS = 0.5;
    private static final double NON_HYPHENATED_SPACE_WIDTH_SS = 0.3;

    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    private static StaffElement element(ElementType type) {
        return type.newInstance();
    }

    // T2: Beamed quaver right extent equals notehead-only extent (no flag contribution)
    @Test
    void testBeamedQuaverExtentEqualsNoteheadOnly() {
        var quaver = element(ElementType.QUAVER);
        var noteheadOnly = SMuFLConstants.NOTE_HEAD_WIDTH_SS;
        var extent = ElementColumnBuilder.calculateRightExtentSs(quaver, true, StaffElement.Direction.UP);

        assertThat(extent).isEqualTo(noteheadOnly);
    }

    // Right-most edge of the augmentation dot glyph relative to its own origin (ss).
    private static double dotGlyphRightSs() {
        return SMuFLMetadata.requireBBox(SMuFLGlyph.AUGMENTATION_DOT).right();
    }

    // Flag right extent for an unbeamed up-stem quaver, computed as ElementColumnBuilder does.
    private static double quaverFlagExtentSs() {
        return NoteGeometry.computeBaseStemGeometry(ElementType.QUAVER, StaffElement.Direction.UP).stemLeftXSs()
            + SMuFLMetadata.requireBBox(SMuFLGlyph.FLAG_8TH_UP).right();
    }

    // T6: Dotted quaver right extent is max(dot extent, flag) — the dot sits past the flag, so the
    // dot extent (first-dot X + dot glyph right edge) wins. Derived from the same geometry the
    // renderer draws (NoteGeometry.firstDotXSs), independently of ElementColumnBuilder.
    @Test
    void testDottedQuaverExtentIsMaxOfDotsAndFlag() {
        var dottedQuaver = element(ElementType.QUAVER);
        dottedQuaver.setDotCount(1);

        var firstDotXSs = NoteGeometry.firstDotXSs(ElementType.QUAVER, false, StaffElement.Direction.UP);
        var dotsExtent = firstDotXSs + dotGlyphRightSs();
        var expected = Math.max(dotsExtent, quaverFlagExtentSs());

        var actual = ElementColumnBuilder.calculateRightExtentSs(dottedQuaver, false, StaffElement.Direction.UP);

        assertThat(actual).isEqualTo(expected);
    }

    // Two-dot quaver right extent reaches the last (second) dot: first-dot X plus one dot-spacing
    // step, plus the dot glyph's right edge.
    @Test
    void testDoubleDottedQuaverExtentIncludesTwoDotPairs() {
        var doubleDottedQuaver = element(ElementType.QUAVER);
        doubleDottedQuaver.setDotCount(DOUBLE_DOT_COUNT);

        var firstDotXSs = NoteGeometry.firstDotXSs(ElementType.QUAVER, false, StaffElement.Direction.UP);
        var lastDotXSs = firstDotXSs + (DOUBLE_DOT_COUNT - 1) * NoteGeometry.DOT_SPACING_SS;
        var dotsExtent = lastDotXSs + dotGlyphRightSs();
        var expected = Math.max(dotsExtent, quaverFlagExtentSs());

        var actual = ElementColumnBuilder.calculateRightExtentSs(doubleDottedQuaver, false, StaffElement.Direction.UP);

        assertThat(actual).isEqualTo(expected);
    }

    // #441: excluding augmentation on a dotted FLAGGED note must strip the dot yet still account for
    // the flag via Math.max — the extent equals the undotted quaver's flag-driven extent and still
    // exceeds the bare notehead, proving the dot path is suppressed without discarding the flag.
    @Test
    void testRightExtentExcludingAugmentationStripsDotButKeepsFlagForDottedQuaver() {
        var dottedQuaver = element(ElementType.QUAVER);
        dottedQuaver.setDotCount(1);

        var flagExtent =
            NoteGeometry.computeBaseStemGeometry(ElementType.QUAVER, StaffElement.Direction.UP).stemLeftXSs()
                + SMuFLMetadata.requireBBox(SMuFLGlyph.FLAG_8TH_UP).right();
        var expectedExcludingAugmentation = Math.max(SMuFLConstants.NOTE_HEAD_WIDTH_SS, flagExtent);

        var excludingAugmentation = ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(dottedQuaver, false, StaffElement.Direction.UP);

        assertThat(excludingAugmentation).isEqualTo(expectedExcludingAugmentation);
        // Flag still wins the Math.max — the extent is more than the bare notehead alone
        assertThat(excludingAugmentation).isGreaterThan(SMuFLConstants.NOTE_HEAD_WIDTH_SS);
    }

    // #441: calculateRightExtentExcludingAugmentationSs for a dotted note equals the undotted right extent
    @Test
    void testRightExtentExcludingAugmentationEqualsUndottedExtentForDottedCrotchet() {
        var plain = element(ElementType.CROTCHET);
        var dotted = element(ElementType.CROTCHET);
        dotted.setDotCount(1);

        var plainExtent = ElementColumnBuilder.calculateRightExtentSs(plain, false, StaffElement.Direction.UP);
        var dottedExcludingAugmentation = ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(dotted, false, StaffElement.Direction.UP);

        // Excluding augmentation must match the plain (undotted) right extent
        assertThat(dottedExcludingAugmentation).isEqualTo(plainExtent);
    }

    // #441: augmentation exclusion must be unconditional, not gated on dotCount == 1 — a
    // double-dotted note's excluding-augmentation extent must still equal the undotted extent.
    @Test
    void testRightExtentExcludingAugmentationIgnoresAllDotsForDoubleDottedCrotchet() {
        var plain = element(ElementType.CROTCHET);
        var doubleDotted = element(ElementType.CROTCHET);
        doubleDotted.setDotCount(DOUBLE_DOT_COUNT);

        var plainExtent = ElementColumnBuilder.calculateRightExtentSs(plain, false, StaffElement.Direction.UP);
        var excludingAugmentation =
            ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(doubleDotted, false, StaffElement.Direction.UP);

        assertThat(excludingAugmentation).isEqualTo(plainExtent);
    }

    // #441: augmentation exclusion must also apply to the small-notehead grace-note path — a dotted
    // grace quaver's excluding-augmentation extent must equal the undotted grace quaver's extent.
    @Test
    void testRightExtentExcludingAugmentationStripsDotForDottedGraceQuaver() {
        var plainGrace = element(ElementType.GRACE_QUAVER);
        var dottedGrace = element(ElementType.GRACE_QUAVER);
        dottedGrace.setDotCount(1);

        var plainExtent = ElementColumnBuilder.calculateRightExtentSs(plainGrace, false, StaffElement.Direction.UP);
        var excludingAugmentation =
            ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(dottedGrace, false, StaffElement.Direction.UP);

        assertThat(excludingAugmentation).isEqualTo(plainExtent);
    }

    // #492: calculateRightExtentSs with a fall includes FALL_GAP_SS + fall advance width beyond the
    // base extent, so minimum spacing reserves room for the glyph.
    @Test
    void testRightExtentSsIncludesFall() {
        var plain = element(ElementType.CROTCHET);
        var withFall = element(ElementType.CROTCHET);
        withFall.setFall();

        var plainExtent = ElementColumnBuilder.calculateRightExtentSs(plain, false, StaffElement.Direction.UP);
        var fallAdvanceWidthSs = SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
        var expected = plainExtent + NoteGeometry.FALL_GAP_SS + fallAdvanceWidthSs;

        var actualExtent = ElementColumnBuilder.calculateRightExtentSs(withFall, false, StaffElement.Direction.UP);

        assertThat(actualExtent).isEqualTo(expected);
    }

    // #492: calculateRightExtentExcludingAugmentationSs with a fall must equal the plain (non-fall) extent —
    // the fall must not inflate comfortable/default spacing, only the minimum-spacing floor.
    @Test
    void testRightExtentExcludingAugmentationExcludesFall() {
        var plain = element(ElementType.CROTCHET);
        var withFall = element(ElementType.CROTCHET);
        withFall.setFall();

        var plainExtent = ElementColumnBuilder.calculateRightExtentSs(plain, false, StaffElement.Direction.UP);
        var fallExcludingExtent = ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(withFall, false, StaffElement.Direction.UP);

        assertThat(fallExcludingExtent).isEqualTo(plainExtent);
    }

    /**
     * The grace notehead spacing reserves must be the one the renderer paints: the regular black
     * notehead in the grace-scaled font. Cross-checked against {@link ElementType}'s independently
     * derived element bounds, which the renderer's own geometry is built from.
     *
     * <p>Guards the #560 regression: this used to read SMuFL's {@code noteheadBlackSmall} bbox, but
     * Bravura's {@code *Small} glyphs are cue-<em>staff</em> glyphs — that box is 1.408 ss, wider
     * than the full-size notehead it is supposed to shrink, so every grace column charged 0.52 ss of
     * ink that is never drawn.
     */
    @Test
    void testGraceNoteheadWidthIsTheGraceScaledHeadTheRendererPaints() {
        assertThat(ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS)
            .as("spacing must reserve the grace head that is actually drawn")
            .isCloseTo(ElementType.GRACE_QUAVER.getElementWidthSs(), within(GRACE_HEAD_TOLERANCE_SS));
        assertThat(ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS)
            .as("a grace head is narrower than a full-size one")
            .isLessThan(SMuFLConstants.NOTE_HEAD_WIDTH_SS);
    }

    // T5: Grace quaver uses the small notehead + small-stem anchor, regular uses full-size — exact values
    @Test
    void testGraceQuaverExtentSmallerThanRegularQuaver() {
        var graceQuaver = element(ElementType.GRACE_QUAVER);
        var regularQuaver = element(ElementType.QUAVER);

        var graceStemLeftXSs =
            NoteGeometry.computeBaseStemGeometry(ElementType.GRACE_QUAVER, StaffElement.Direction.UP).stemLeftXSs();
        var flagBBoxRight = SMuFLMetadata.requireBBox(SMuFLGlyph.FLAG_8TH_UP).right();
        var expectedGrace = Math.max(
            ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS,
            NoteGeometry.getGraceFlagOriginXSs(graceStemLeftXSs) + ElementType.GRACE_NOTE_SCALE * flagBBoxRight);
        var expectedRegular = Math.max(
            SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            NoteGeometry.computeBaseStemGeometry(ElementType.QUAVER, StaffElement.Direction.UP).stemLeftXSs() + flagBBoxRight);

        var graceExtent = ElementColumnBuilder.calculateRightExtentSs(graceQuaver, false, StaffElement.Direction.UP);
        var regularExtent = ElementColumnBuilder.calculateRightExtentSs(regularQuaver, false, StaffElement.Direction.UP);

        assertThat(graceExtent).isEqualTo(expectedGrace);
        assertThat(regularExtent).isEqualTo(expectedRegular);
        assertThat(graceExtent).isLessThan(regularExtent);
    }

    // T3: Non-flagged types (CROTCHET, MINIM, SEMIBREVE) are unchanged by beamed/upper
    @Test
    void testNonFlaggedTypesUnchanged() {
        for (var type : new ElementType[]{ElementType.CROTCHET, ElementType.MINIM, ElementType.SEMIBREVE}) {
            var n = element(type);
            var noteheadOnly = SMuFLConstants.NOTE_HEAD_WIDTH_SS;
            var extentUnbeamed = ElementColumnBuilder.calculateRightExtentSs(n, false, StaffElement.Direction.UP);
            var extentBeamed = ElementColumnBuilder.calculateRightExtentSs(n, true, StaffElement.Direction.UP);

            assertThat(extentUnbeamed)
                .as("Unbeamed %s should equal notehead extent", type)
                .isEqualTo(noteheadOnly);
            assertThat(extentBeamed)
                .as("Beamed %s should equal notehead extent", type)
                .isEqualTo(noteheadOnly);
        }
    }

    // T4: Stem-up and stem-down unbeamed quaver each use their own stem anchor + flag — exact values
    @Test
    void testStemUpVsStemDownProduceDifferentExtents() {
        var quaver = element(ElementType.QUAVER);

        var expectedUp = Math.max(
            SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            NoteGeometry.computeBaseStemGeometry(ElementType.QUAVER, StaffElement.Direction.UP).stemLeftXSs()
                + SMuFLMetadata.requireBBox(SMuFLGlyph.FLAG_8TH_UP).right());
        var expectedDown = Math.max(
            SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            NoteGeometry.computeBaseStemGeometry(ElementType.QUAVER, StaffElement.Direction.DOWN).stemLeftXSs()
                + SMuFLMetadata.requireBBox(SMuFLGlyph.FLAG_8TH_DOWN).right());

        var upExtent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, StaffElement.Direction.UP);
        var downExtent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, StaffElement.Direction.DOWN);

        assertThat(upExtent).isEqualTo(expectedUp);
        assertThat(downExtent).isEqualTo(expectedDown);
        assertThat(upExtent).isNotEqualTo(downExtent);
    }

    // T1: Unbeamed quaver right extent > notehead-only extent
    @Test
    void testUnbeamedQuaverExtentExceedsNoteheadOnly() {
        var quaver = element(ElementType.QUAVER);
        var noteheadOnly = SMuFLConstants.NOTE_HEAD_WIDTH_SS;
        var extent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, StaffElement.Direction.UP);

        assertThat(extent).isGreaterThan(noteheadOnly);
    }

    // Row 18: REST + BARLINE right extent = type.getElementWidthSs() — no flag/dot adjustments
    @Test
    void testRestAndBarlineRightExtentEqualsElementWidth() {
        for (var type : new ElementType[]{ElementType.CROTCHET_REST, ElementType.SINGLE_BARLINE}) {
            var el = element(type);

            assertThat(ElementColumnBuilder.calculateRightExtentSs(el, false, StaffElement.Direction.UP))
                .as("Right extent of %s should equal element width", type)
                .isEqualTo(type.getElementWidthSs());
        }
    }

    // Row 19: calculateLeftExtentSs = 0 with no accidental
    @Test
    void testLeftExtentIsZeroWithoutAccidental() {
        var note = element(ElementType.CROTCHET);

        assertThat(ElementColumnBuilder.calculateLeftExtentSs(note)).isEqualTo(0.0);
    }

    // Row 19: calculateLeftExtentSs = -(accW + ACCIDENTAL_PADDING_SS) when accidental present
    @Test
    void testLeftExtentIsNegativeWithAccidental() {
        var note = element(ElementType.CROTCHET);
        note.setAccidental(StaffElement.Accidental.SHARP);

        var accidentalWidthSs = NoteGeometry.getAccidentalWidthSs(note);
        var expected = -(accidentalWidthSs + NoteGeometry.ACCIDENTAL_PADDING_SS);

        assertThat(ElementColumnBuilder.calculateLeftExtentSs(note)).isEqualTo(expected);
    }

    // Row 20: buildColumn minGap = hyphenWidthSs for BEGIN and MIDDLE syllabic types
    @Test
    void testBuildColumnMinGapIsHyphenGlyphWidthForHyphenatedSyllable() {
        for (var syllabic : new Lyric.Syllabic[]{Lyric.Syllabic.BEGIN, Lyric.Syllabic.MIDDLE}) {
            var metrics = mock(LyricRenderMetrics.class);
            when(metrics.hyphenWidthSs()).thenReturn(HYPHEN_GLYPH_WIDTH_SS);
            when(metrics.lyricBoxWidthSs(anyString())).thenReturn(0.0);

            var line = detachedLine();
            var note = element(ElementType.CROTCHET);
            note.setLyricForVerse(1, syllabic, false, "syl", Lyric.Extend.NONE);
            line.addElement(note);

            var column = new ElementColumnBuilder(metrics).buildColumn(note, line);

            assertThat(column.getMinGapToNextSyllableSs())
                .as("minGap for syllabic %s should equal the hyphen glyph advance", syllabic)
                .isEqualTo(HYPHEN_GLYPH_WIDTH_SS);
        }
    }

    // Row 20: buildColumn minGap = spaceWidthSs * NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER for
    // END and SINGLE syllabic types
    @Test
    void testBuildColumnMinGapIsSpaceWidthForNonHyphenatedSyllable() {
        for (var syllabic : new Lyric.Syllabic[]{Lyric.Syllabic.END, Lyric.Syllabic.SINGLE}) {
            var metrics = mock(LyricRenderMetrics.class);
            when(metrics.spaceWidthSs()).thenReturn(NON_HYPHENATED_SPACE_WIDTH_SS);
            when(metrics.lyricBoxWidthSs(anyString())).thenReturn(0.0);

            var line = detachedLine();
            var note = element(ElementType.CROTCHET);
            note.setLyricForVerse(1, syllabic, false, "word", Lyric.Extend.NONE);
            line.addElement(note);

            var column = new ElementColumnBuilder(metrics).buildColumn(note, line);

            assertThat(column.getMinGapToNextSyllableSs())
                .as("minGap for syllabic %s should equal space width times the multiplier", syllabic)
                .isEqualTo(NON_HYPHENATED_SPACE_WIDTH_SS
                    * ElementColumnBuilder.NON_HYPHENATED_GAP_SPACE_WIDTH_MULTIPLIER);
        }
    }

    // Row 21: buildColumns on an empty line returns an empty list
    @Test
    void testBuildColumnsEmptyLineReturnsEmptyList() {
        var metrics = mock(LyricRenderMetrics.class);
        var builder = new ElementColumnBuilder(metrics);
        var line = detachedLine();

        assertThat(builder.buildColumns(line)).isEmpty();
    }

    // #418 builder tagging (end-to-end): buildColumns tags each column with its beam group via
    // a real Line + Beam range elements, and two adjacent beam groups receive distinct ids so
    // the spacing calculator keeps them apart.
    @Test
    void testBuildColumnsTagsAdjacentBeamGroupsWithDistinctIds() {
        var line = lineWith(
            ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.QUAVER,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER
        );
        // Two separate beam groups: the quavers and, immediately after, the semiquavers
        line.addBeaming(new Beam(line.getElement(1), line.getElement(2)));
        line.addBeaming(new Beam(line.getElement(3), line.getElement(4)));

        var columns = new ElementColumnBuilder(mock(LyricRenderMetrics.class)).buildColumns(line);

        // Unbeamed crotchet has no beam group
        assertThat(columns.get(0).isBeamed()).isFalse();
        assertThat(columns.get(0).getBeamGroupId()).isEqualTo(ElementColumn.NO_BEAM_GROUP);

        // First group: both quavers beamed and share one id
        assertThat(columns.get(1).isBeamed()).isTrue();
        assertThat(columns.get(2).isBeamed()).isTrue();
        assertThat(columns.get(2).getBeamGroupId()).isEqualTo(columns.get(1).getBeamGroupId());

        // Second group: both semiquavers beamed and share one id
        assertThat(columns.get(3).isBeamed()).isTrue();
        assertThat(columns.get(4).isBeamed()).isTrue();
        assertThat(columns.get(4).getBeamGroupId()).isEqualTo(columns.get(3).getBeamGroupId());

        // The two groups are distinct, so the calculator will not merge them
        assertThat(columns.get(3).getBeamGroupId()).isNotEqualTo(columns.get(1).getBeamGroupId());
    }

    // #592: a grace note sitting inside a beam span is not a beam member — the beam passes over
    // it, so its column stays unbeamed and keeps its own flag.
    @Test
    void testBuildColumnsLeavesAGraceNoteInsideABeamSpanUnbeamed() {
        var line = lineWith(ElementType.QUAVER, ElementType.GRACE_QUAVER, ElementType.QUAVER);
        line.addBeaming(new Beam(line.getElement(0), line.getElement(2)));

        var columns = new ElementColumnBuilder(mock(LyricRenderMetrics.class)).buildColumns(line);

        assertThat(columns.get(0).isBeamed()).isTrue();
        assertThat(columns.get(2).isBeamed()).isTrue();
        assertThat(columns.get(1).isBeamed()).isFalse();
        assertThat(columns.get(1).getBeamGroupId()).isEqualTo(ElementColumn.NO_BEAM_GROUP);
    }

    // Row 22: calculateStemTop/BottomSs for stem-up, stem-down, and stemless elements
    @Nested
    class StemGeometry {

        private static final double TOLERANCE = 1e-9;

        // Below the middle line: defaultDirection is UP, so an up-stem here is natural (unshortened)
        // and a down-stem here is forced.
        private static final int BELOW_MIDDLE_LINE_STAFF_POSITION = 2;

        // Above the middle line (but not at the exact boundary): an up-stem here is still forced
        // (sp <= 0), and further from the middle line than sp=0, so the shortening differs from it.
        private static final int ABOVE_MIDDLE_LINE_STAFF_POSITION = -2;

        private final ElementColumnBuilder builder = new ElementColumnBuilder(mock(LyricRenderMetrics.class));

        @Test
        void testStemTopSsStemDown() {
            // Default direction is DOWN (stem-down), sp=0 -> natural (forcedDown needs sp>0)
            // -> top = element head top = -HALF_NOTE_HEAD_SS
            var note = element(ElementType.CROTCHET);
            assertThat(builder.calculateStemTopSs(note)).isEqualTo(-ElementColumnBuilder.HALF_NOTE_HEAD_SS);
        }

        @Test
        void testStemTopSsStemUpAtMiddleLineIsForcedAndShortened() {
            // isUpper=true, sp=0 -> forced (defaultDirection at sp<=0 is DOWN) -> top is the
            // shortened tip, not the full natural length.
            var note = element(ElementType.CROTCHET);
            note.setUpper(true);
            var expectedShorteningSs = NoteGeometry.forcedShorteningSs(0, StaffElement.Direction.UP, false);

            assertThat(builder.calculateStemTopSs(note))
                .isCloseTo(-(SMuFLConstants.STEM_LENGTH_SS - expectedShorteningSs), within(TOLERANCE));
        }

        @Test
        void testStemTopSsForcedUpStemFartherFromMiddleLineShortensMore() {
            var note = element(ElementType.CROTCHET);
            note.setStaffPosition(ABOVE_MIDDLE_LINE_STAFF_POSITION);
            note.setUpper(true);
            var expectedShorteningSs = NoteGeometry.forcedShorteningSs(
                ABOVE_MIDDLE_LINE_STAFF_POSITION, StaffElement.Direction.UP, false);

            assertThat(builder.calculateStemTopSs(note))
                .isCloseTo(-(SMuFLConstants.STEM_LENGTH_SS - expectedShorteningSs), within(TOLERANCE));
        }

        @Test
        void testStemTopSsNaturalUpStemBelowMiddleLineIsFullLength() {
            // sp > 0: defaultDirection is UP, so an up-stem here is natural -> unshortened.
            var note = element(ElementType.CROTCHET);
            note.setStaffPosition(BELOW_MIDDLE_LINE_STAFF_POSITION);
            note.setUpper(true);

            assertThat(builder.calculateStemTopSs(note)).isEqualTo(-SMuFLConstants.STEM_LENGTH_SS);
        }

        @Test
        void testStemTopSsStemless() {
            // Rest has no stem: top = -HALF_NOTE_HEAD_SS
            var rest = element(ElementType.CROTCHET_REST);
            assertThat(builder.calculateStemTopSs(rest)).isEqualTo(-ElementColumnBuilder.HALF_NOTE_HEAD_SS);
        }

        @Test
        void testStemBottomSsStemDown() {
            // Default direction is DOWN (stem-down), sp=0 -> natural -> bottom = stem tip = STEM_LENGTH_SS
            var note = element(ElementType.CROTCHET);
            assertThat(builder.calculateStemBottomSs(note)).isEqualTo(SMuFLConstants.STEM_LENGTH_SS);
        }

        @Test
        void testStemBottomSsForcedDownStemBelowMiddleLineIsShortened() {
            // sp > 0, direction DOWN -> forced (defaultDirection at sp>0 is UP) -> bottom is shortened.
            var note = element(ElementType.CROTCHET);
            note.setStaffPosition(BELOW_MIDDLE_LINE_STAFF_POSITION);
            var expectedShorteningSs = NoteGeometry.forcedShorteningSs(
                BELOW_MIDDLE_LINE_STAFF_POSITION, StaffElement.Direction.DOWN, false);

            assertThat(builder.calculateStemBottomSs(note))
                .isCloseTo(SMuFLConstants.STEM_LENGTH_SS - expectedShorteningSs, within(TOLERANCE));
        }

        @Test
        void testStemBottomSsStemUp() {
            // isUpper=true, sp=0 -> bottom = element head bottom = HALF_NOTE_HEAD_SS, unaffected by
            // the (top-side) forced shortening.
            var note = element(ElementType.CROTCHET);
            note.setUpper(true);
            assertThat(builder.calculateStemBottomSs(note)).isEqualTo(ElementColumnBuilder.HALF_NOTE_HEAD_SS);
        }

        @Test
        void testStemBottomSsStemless() {
            // Rest has no stem: bottom = HALF_NOTE_HEAD_SS
            var rest = element(ElementType.CROTCHET_REST);
            assertThat(builder.calculateStemBottomSs(rest)).isEqualTo(ElementColumnBuilder.HALF_NOTE_HEAD_SS);
        }

        @Test
        void testStemTopSsGraceNoteIsTheGraceTipWhateverDirectionItStores() {
            // A grace note always draws an up stem, on the shorter grace stem, and is never
            // forced-shortened — so its baked top is the grace tip even when it stores DOWN.
            var grace = element(ElementType.GRACE_QUAVER);
            grace.setDirection(StaffElement.Direction.DOWN);

            assertThat(builder.calculateStemTopSs(grace)).isEqualTo(-SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS);
        }

        @Test
        void testStemBottomSsGraceNoteIsTheNoteheadBottomWhateverDirectionItStores() {
            var grace = element(ElementType.GRACE_QUAVER);
            grace.setDirection(StaffElement.Direction.DOWN);

            assertThat(builder.calculateStemBottomSs(grace)).isEqualTo(ElementColumnBuilder.HALF_NOTE_HEAD_SS);
        }
    }
}
