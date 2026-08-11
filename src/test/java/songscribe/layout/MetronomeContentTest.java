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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Font;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.BeatChange;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.MetronomeAttachment;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tempo;
import songscribe.layout.MetronomeContent.GlyphItem;
import songscribe.layout.MetronomeContent.TextItem;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Tests for {@link MetronomeContent}, the single source of truth for metronome typesetting.
 * <p>
 * Every position and width assertion computes its expected value from the same SMuFL and design
 * primitives the production builder uses — the glyph advance width, {@code NOTE_SCALE}, the dot
 * advance and the measured width of the drawn string — rather than reading it back off the
 * content under test. That is deliberate: an assertion derived from the builder's own output is
 * self-consistent no matter what the builder computes, and issue #735 was precisely a wrong
 * combination of correct primitives.
 */
class MetronomeContentTest extends UnitTest {

    // Floating-point tolerance for double arithmetic involving float NOTE_SCALE.
    private static final double TOLERANCE = 1e-9;

    private static final int FONT_SIZE_PT = 12;
    private static final int LARGER_FONT_SIZE_PT = 24;
    private static final Font ANNOTATION_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE_PT);
    private static final Font LARGER_ANNOTATION_FONT =
        new Font(Font.SANS_SERIF, Font.PLAIN, LARGER_FONT_SIZE_PT);

    private static double crotchetAdvanceSs() {
        return SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_NOTE_QUARTER_UP)
            * MetronomeAttachment.NOTE_SCALE;
    }

    private static double dotAdvanceSs() {
        return SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT)
            * MetronomeAttachment.NOTE_SCALE;
    }

    private static double equalsWidthSs() {
        return ScaleContext.textWidthSs(ANNOTATION_FONT, MetronomeContent.EQUALS_STR).value();
    }

    // -------------------------------------------------------------------------
    // metronomeGlyphFor: 6 mapped note types + unmapped type throws
    // -------------------------------------------------------------------------

    static Stream<Arguments> mappedNoteGlyphs() {
        return Stream.of(
            Arguments.of(ElementType.SEMIBREVE, SMuFLGlyph.MET_NOTE_WHOLE),
            Arguments.of(ElementType.MINIM, SMuFLGlyph.MET_NOTE_HALF_UP),
            Arguments.of(ElementType.CROTCHET, SMuFLGlyph.MET_NOTE_QUARTER_UP),
            Arguments.of(ElementType.QUAVER, SMuFLGlyph.MET_NOTE_8TH_UP),
            Arguments.of(ElementType.SEMIQUAVER, SMuFLGlyph.MET_NOTE_16TH_UP),
            Arguments.of(ElementType.DEMI_SEMIQUAVER, SMuFLGlyph.MET_NOTE_32ND_UP)
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("mappedNoteGlyphs")
    void testMetronomeGlyphForReturnsMappedGlyph(ElementType type, SMuFLGlyph expectedGlyph) {
        assertThat(MetronomeContent.metronomeGlyphFor(type)).isEqualTo(expectedGlyph);
    }

    @Test
    void testMetronomeGlyphForThrowsForUnmappedType() {
        // SINGLE_BARLINE has no metronome glyph; RuntimeError.exit() is redirected
        // to AssertionError by UnitTest.suppressDialogs().
        assertThatThrownBy(() -> MetronomeContent.metronomeGlyphFor(ElementType.SINGLE_BARLINE))
            .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // dotAdvanceWidthSs: bbox advance × NOTE_SCALE
    // -------------------------------------------------------------------------

    @Test
    void testDotAdvanceWidthSsEqualsAugmentationDotAdvanceTimesNoteScale() {
        // Computed independently from the same two primitives, so the method is not its
        // own oracle.
        assertThat(MetronomeContent.dotAdvanceWidthSs())
            .isCloseTo(dotAdvanceSs(), within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // forBeatChange — undotted note advance sequence. The "=" offset is the
    // regression guard for divergence 2: the gap after the left glyph that the
    // old measuring path omitted for an undotted note.
    // -------------------------------------------------------------------------

    @Test
    void testForBeatChangeUndottedProducesNoteEqualsNoteSequence() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);

        assertThat(content.items()).hasSize(3);

        var leftNote = content.items().get(0);
        var equals = content.items().get(1);
        var rightNote = content.items().get(2);

        assertThat(leftNote).isInstanceOf(GlyphItem.class);
        assertThat(((GlyphItem) leftNote).glyph()).isEqualTo(SMuFLGlyph.MET_NOTE_QUARTER_UP);
        assertThat(leftNote.xSs()).isCloseTo(0, within(TOLERANCE));

        assertThat(equals).isInstanceOf(TextItem.class);
        // This equality is what actually guards divergence 1 — the renderer drawing " = "
        // while the measuring path measured a bare "=". Do not remove it as redundant with
        // the width assertions below; none of them can distinguish the two strings on its own.
        assertThat(((TextItem) equals).text()).isEqualTo(MetronomeContent.EQUALS_STR);

        assertThat(equals.xSs())
            .isCloseTo(crotchetAdvanceSs() + dotAdvanceSs(), within(TOLERANCE));

        assertThat(rightNote).isInstanceOf(GlyphItem.class);
        assertThat(((GlyphItem) rightNote).glyph()).isEqualTo(SMuFLGlyph.MET_NOTE_QUARTER_UP);
        assertThat(rightNote.xSs())
            .isCloseTo(crotchetAdvanceSs() + dotAdvanceSs() + equalsWidthSs(), within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // forBeatChange — total width, anchored to independently computed primitives
    // -------------------------------------------------------------------------

    @Test
    void testForBeatChangeUndottedWidthSsIsGlyphPlusGapPlusEqualsPlusGlyph() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);

        // The whole advance sequence, stated independently: left glyph, the gap that follows
        // an undotted note, the " = " as drawn, then the right glyph with no trailing gap.
        var expectedWidthSs =
            crotchetAdvanceSs() + dotAdvanceSs() + equalsWidthSs() + crotchetAdvanceSs();

        assertThat(content.widthSs()).isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // forBeatChange — collision regions (direct assertion that the hit rect
    // now reaches the right-hand note)
    // -------------------------------------------------------------------------

    @Test
    void testForBeatChangeProducesThreeRegionsWithRightNoteFlushToWidth() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);

        assertThat(content.regions()).hasSize(3);

        var regions = content.regions();

        assertThat(regions.get(0).xOffsetSs())
            .isLessThan(regions.get(1).xOffsetSs());
        assertThat(regions.get(1).xOffsetSs())
            .isLessThan(regions.get(2).xOffsetSs());

        var rightRegion = regions.get(2);

        assertThat(rightRegion.xOffsetSs() + rightRegion.widthSs())
            .isCloseTo(content.widthSs(), within(TOLERANCE));
    }

    /**
     * A dotted note spends two dot advances: one gap before the dot, one for the dot itself.
     * Getting that count wrong in either direction is divergence 2 of issue #735, so the dot's
     * position and the run's extent are both pinned to independently computed values.
     */
    @Test
    void testForBeatChangeDottedLeftNoteEmitsDotGlyphSharingBaselineAndOneLeftRegion() {
        var beatChange = new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);

        // Left note, its augmentation dot, the "=", the right note.
        assertThat(content.items()).hasSize(4);

        var leftNote = (GlyphItem) content.items().get(0);
        var leftDot = (GlyphItem) content.items().get(1);

        assertThat(leftDot.glyph()).isEqualTo(SMuFLGlyph.MET_AUGMENTATION_DOT);
        assertThat(leftDot.baselineOffsetSs())
            .isCloseTo(leftNote.baselineOffsetSs(), within(TOLERANCE));

        // The dot sits one gap advance past the note glyph, not flush against it.
        assertThat(leftDot.xSs())
            .isCloseTo(crotchetAdvanceSs() + dotAdvanceSs(), within(TOLERANCE));

        // Three regions: the left note+dot run, the "=", the right note.
        assertThat(content.regions()).hasSize(3);

        var leftRegion = content.regions().get(0);
        // Glyph, gap, dot — two dot advances past the note glyph in total.
        var expectedLeftRunEndSs = crotchetAdvanceSs() + 2 * dotAdvanceSs();

        assertThat(leftRegion.xOffsetSs()).isCloseTo(0, within(TOLERANCE));
        assertThat(leftRegion.xOffsetSs() + leftRegion.widthSs())
            .isCloseTo(expectedLeftRunEndSs, within(TOLERANCE));

        var expectedWidthSs =
            expectedLeftRunEndSs + equalsWidthSs() + crotchetAdvanceSs();

        assertThat(content.widthSs()).isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // forBeatChange — a final note owes no trailing gap, so the content ends
    // flush on its last ink
    // -------------------------------------------------------------------------

    @Test
    void testForBeatChangeDottedRightNoteEndsOnDotWithNoTrailingGap() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);
        var lastItem = content.items().get(content.items().size() - 1);

        assertThat(lastItem).isInstanceOf(GlyphItem.class);
        assertThat(((GlyphItem) lastItem).glyph()).isEqualTo(SMuFLGlyph.MET_AUGMENTATION_DOT);

        // Left glyph, its gap, the " = ", the right glyph, its gap, its dot — and nothing
        // after the dot, because the final note has nothing to separate from.
        var expectedWidthSs = crotchetAdvanceSs() + dotAdvanceSs() + equalsWidthSs()
            + crotchetAdvanceSs() + 2 * dotAdvanceSs();

        assertThat(content.widthSs()).isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    @Test
    void testForBeatChangeUndottedRightNoteLeavesNoTrailingGap() {
        var undottedRight = MetronomeContent.forBeatChange(
            new BeatChange(Duration.CROTCHET, Duration.CROTCHET), ANNOTATION_FONT);
        var lastRegion = undottedRight.regions().get(undottedRight.regions().size() - 1);

        // The width ends on the right glyph's own ink, so no dead space is reserved or
        // hit-tested past it.
        assertThat(undottedRight.widthSs())
            .isCloseTo(lastRegion.xOffsetSs() + lastRegion.widthSs(), within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Item vertical placement — every item carries its own baseline, so the
    // renderer decides nothing on either axis
    // -------------------------------------------------------------------------

    @Test
    void testGlyphAndTextItemsCarryTheirOwnBaselineOffsets() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);
        var leftNote = (GlyphItem) content.items().get(0);
        var equals = (TextItem) content.items().get(1);

        // A note glyph hangs from the top of its own bounding box.
        var expectedGlyphBaselineSs =
            -SMuFLMetadata.requireBBox(SMuFLGlyph.MET_NOTE_QUARTER_UP).top()
                * MetronomeAttachment.NOTE_SCALE;

        assertThat(leftNote.baselineOffsetSs())
            .isCloseTo(expectedGlyphBaselineSs, within(TOLERANCE));

        // Text sits on the note cap-height baseline.
        assertThat(equals.baselineOffsetSs())
            .isCloseTo(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // forTempo — shouldShowTempo() true/false, with/without description
    // -------------------------------------------------------------------------

    @Test
    void testForTempoShowingTempoProducesGlyphAndTwoTextItemsWithThreeRegions() {
        var tempo = new Tempo(Tempo.DEFAULT_BPM, Duration.CROTCHET, "Moderate", true);

        var content = MetronomeContent.forTempo(tempo, ANNOTATION_FONT);

        assertThat(content.items()).hasSize(3);
        assertThat(content.items().get(0)).isInstanceOf(GlyphItem.class);
        assertThat(content.items().get(1)).isInstanceOf(TextItem.class);
        assertThat(content.items().get(2)).isInstanceOf(TextItem.class);
        assertThat(((TextItem) content.items().get(2)).text())
            .isEqualTo(Tempo.DEFAULT_BPM + " Moderate");
        assertThat(content.regions()).hasSize(3);
        assertThat(content.widthSs()).isGreaterThan(0);
    }

    /**
     * With no description there is nothing to separate the BPM from, so no separator is drawn.
     * A trailing space would be measured, widening the marking's hit box and its stacking
     * reservation by a space with no ink under it.
     */
    @Test
    void testForTempoShowingTempoWithEmptyDescriptionDrawsBpmWithNoTrailingSpace() {
        var tempo = new Tempo(Tempo.DEFAULT_BPM, Duration.CROTCHET, "", true);

        var content = MetronomeContent.forTempo(tempo, ANNOTATION_FONT);
        var bpmItem = (TextItem) content.items().get(content.items().size() - 1);

        assertThat(bpmItem.text()).isEqualTo(String.valueOf(Tempo.DEFAULT_BPM));

        var expectedWidthSs = crotchetAdvanceSs() + dotAdvanceSs() + equalsWidthSs()
            + ScaleContext.textWidthSs(ANNOTATION_FONT, String.valueOf(Tempo.DEFAULT_BPM)).value();

        assertThat(content.widthSs()).isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    @Test
    void testForTempoNotShowingTempoWithDescriptionProducesSingleTextItemAtOrigin() {
        var tempo = new Tempo(Tempo.DEFAULT_BPM, Duration.CROTCHET, "Moderate", false);

        var content = MetronomeContent.forTempo(tempo, ANNOTATION_FONT);

        assertThat(content.items()).hasSize(1);
        assertThat(content.items().get(0)).isInstanceOf(TextItem.class);
        assertThat(content.items().get(0).xSs()).isCloseTo(0, within(TOLERANCE));
        assertThat(content.regions()).hasSize(1);
        assertThat(content.items()).noneMatch(GlyphItem.class::isInstance);
    }

    @Test
    void testForTempoNotShowingTempoWithEmptyDescriptionProducesNoContent() {
        var tempo = new Tempo(Tempo.DEFAULT_BPM, Duration.CROTCHET, "", false);

        var content = MetronomeContent.forTempo(tempo, ANNOTATION_FONT);

        assertThat(content.items()).isEmpty();
        assertThat(content.regions()).isEmpty();
        assertThat(content.widthSs()).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void testForTempoTextRegionCoversDescenderBelowQuarterNoteHeight() {
        var tempo = new Tempo(Tempo.DEFAULT_BPM, Duration.CROTCHET, "Moderate", false);

        var content = MetronomeContent.forTempo(tempo, ANNOTATION_FONT);
        var textRegion = content.regions().get(0);
        var ascentSs = ScaleContext.fontAscentSs(ANNOTATION_FONT).value();
        var descentSs = ScaleContext.fontDescentSs(ANNOTATION_FONT).value();

        assertThat(textRegion.yOffsetSs())
            .isCloseTo(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS - ascentSs, within(TOLERANCE));
        // The region reaches exactly one font descent below the baseline — not merely
        // "somewhere past the note height".
        assertThat(textRegion.yOffsetSs() + textRegion.heightSs())
            .isCloseTo(
                MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS + descentSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Font sensitivity: the content is measured with the font it is given,
    // not a hardcoded one
    // -------------------------------------------------------------------------

    @Test
    void testForBeatChangeWidthSsGrowsWithLargerFont() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET);

        var smallContent = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);
        var largeContent = MetronomeContent.forBeatChange(beatChange, LARGER_ANNOTATION_FONT);

        assertThat(largeContent.widthSs()).isGreaterThan(smallContent.widthSs());
    }

    @Test
    void testTextItemCarriesTheScaledFontSoTheRendererDerivesNothing() {
        var beatChange = new BeatChange(Duration.CROTCHET, Duration.CROTCHET);

        var content = MetronomeContent.forBeatChange(beatChange, ANNOTATION_FONT);
        var equals = (TextItem) content.items().get(1);

        assertThat(equals.scaledFont()).isEqualTo(ScaleContext.scaleFont(ANNOTATION_FONT));
    }
}
