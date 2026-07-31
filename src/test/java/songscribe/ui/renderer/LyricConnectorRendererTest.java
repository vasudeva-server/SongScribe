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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static songscribe.layout.LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.jspecify.annotations.Nullable;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.layout.ElementColumnTestHelper;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricConnectorLayout;
import songscribe.layout.LyricConnectorLayout.Kind;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.component.ScoreView;

class LyricConnectorRendererTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final double HYPHEN_WIDTH_SS = 0.875;

    // Hyphen chain preview: an unclosed chain opened at CHAIN_START_XSS, whose layout-assigned end
    // is the next element at CHAIN_NEXT_ELEMENT_XSS, while the lyric editor sits further along the
    // line at EDITOR_COLUMN_XSS.
    private static final int EDITED_VERSE = 1;
    private static final int UNEDITED_VERSE = 2;
    private static final double NO_COLUMN_XSS = -1.0;
    // A second, unedited-verse chain occupying its own X span left of the edited one, so the two
    // are told apart by X: both draw on the same baseline now that only one verse is on the page.
    private static final double OTHER_CHAIN_START_XSS = 2.0;
    private static final double OTHER_CHAIN_NEXT_ELEMENT_XSS = 4.0;
    private static final double CHAIN_START_XSS = 8.0;
    private static final double CHAIN_NEXT_ELEMENT_XSS = 12.0;
    private static final double EDITOR_COLUMN_XSS = 20.0;
    private static final double EDITOR_COLUMN_BEFORE_CHAIN_XSS = 2.0;
    private static final double NO_LEFT_EXTENT_SS = 0.0;

    /**
     * A left extent wide enough that the edited column's left edge is tellable apart from its X and
     * from its right edge, which all coincide when the column has no extents.
     */
    private static final double EDITOR_COLUMN_LEFT_EXTENT_SS = -1.5;

    /**
     * An editor sitting close enough past the chain start that the gap fits only one hyphen, so the
     * preview takes the single-centered-hyphen fallback over its own gap rather than over the
     * connector's.
     */
    private static final double EDITOR_COLUMN_ONE_HYPHEN_AWAY_XSS = 10.0;

    // Synthetic line geometry. The above-staff content is below the painted floor, so the
    // staff is drawn at MIN_ABOVE_STAFF_SS rather than at the measured 1 ss — and the verse
    // baselines hang off that painted staff, which is where the connectors actually appear.
    private static final double CONTENT_ABOVE_STAFF_SS = 1.0;
    private static final double CONTENT_BELOW_STAFF_SS = 1.0;
    private static final double STAFF_TO_LYRICS_GAP_SS = 1.0;
    private static final double NARROW_STAFF_TO_LYRICS_GAP_SS = 0.5;

    /**
     * Painted staff bottom plus the below-staff content: the anchor every verse baseline
     * hangs off.
     */
    private static final double LYRICS_ANCHOR_YSS =
        Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS + CONTENT_BELOW_STAFF_SS;

    private static final double LYRIC_BASELINE_SS = LYRICS_ANCHOR_YSS + STAFF_TO_LYRICS_GAP_SS;
    private static final double NARROW_LYRIC_BASELINE_SS =
        LYRICS_ANCHOR_YSS + NARROW_STAFF_TO_LYRICS_GAP_SS;

    private static LineInvariants.Builder builderWith(
        List<LyricConnectorLayout> connectors,
        double staffToLyricsGapSs
    ) {
        return builderWith(connectors, staffToLyricsGapSs, null, NO_COLUMN_XSS);
    }

    /**
     * As {@link #builderWith(List, double)}, but with the lyric editor open on {@code editedElement}.
     * A non-negative {@code editedColumnXSs} also lays that element out at that X, standing in for an
     * editor open on a note of this line; {@link #NO_COLUMN_XSS} leaves it unlaid-out, standing in for
     * an editor open on another line.
     */
    private static LineInvariants.Builder builderWith(
        List<LyricConnectorLayout> connectors,
        double staffToLyricsGapSs,
        @Nullable StaffElement editedElement,
        double editedColumnXSs
    ) {
        return builderWith(connectors, staffToLyricsGapSs, editedElement, editedColumnXSs, NO_LEFT_EXTENT_SS);
    }

    /**
     * As {@link #builderWith(List, double, StaffElement, double)}, but the edited element's column
     * also reaches {@code editedColumnLeftExtentSs} to the left of its X, as a column with a
     * leading accidental does. Left extents are negative, so the column's left edge — the measure
     * the chain preview is supposed to end at — lands left of {@code editedColumnXSs}.
     */
    private static LineInvariants.Builder builderWith(
        List<LyricConnectorLayout> connectors,
        double staffToLyricsGapSs,
        @Nullable StaffElement editedElement,
        double editedColumnXSs,
        double editedColumnLeftExtentSs
    ) {
        var layoutBuilder = LayoutResult.builder()
            .setContentAboveStaffSs(CONTENT_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(CONTENT_BELOW_STAFF_SS);

        for (var connector : connectors) {
            layoutBuilder.addLyricConnector(connector);
        }

        if (editedElement != null && editedColumnXSs >= 0) {
            layoutBuilder.putElementColumn(
                editedElement,
                ElementColumnTestHelper.columnAt(editedElement, editedColumnXSs, editedColumnLeftExtentSs));
        }

        return RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutBuilder.build())
            .setActivelyEditedElement(editedElement)
            .setActivelyEditedVerse(editedElement != null ? EDITED_VERSE : LineInvariants.NO_VERSE)
            .setLyricRenderMetrics(
                new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, HYPHEN_WIDTH_SS, 0.0, staffToLyricsGapSs));
    }

    private static StaffElement note() {
        return ElementType.CROTCHET.newInstance();
    }

    @Test
    void testHyphenDrawnCenteredAtMidpoint() {
        var connector = new LyricConnectorLayout(10.0, 11.5, 1, Kind.HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var xCap = ArgumentCaptor.forClass(Float.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(1)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), yCap.capture());

        var center = (10.0 + 11.5) / 2.0;

        assertThat(xCap.getValue().doubleValue()).isCloseTo(center - HYPHEN_WIDTH_SS / 2.0, within(TOLERANCE));

        assertThat(yCap.getValue().doubleValue()).isCloseTo(LYRIC_BASELINE_SS, within(TOLERANCE));
    }

    @Test
    void testDanglingHyphenDrawnCenteredInGap() {
        var connector = new LyricConnectorLayout(8.0, 12.0, 1, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var xCap = ArgumentCaptor.forClass(Float.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(1)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), yCap.capture());

        var center = (8.0 + 12.0) / 2.0;

        assertThat(xCap.getValue().doubleValue()).isCloseTo(center - HYPHEN_WIDTH_SS / 2.0, within(TOLERANCE));
        assertThat(yCap.getValue().doubleValue()).isCloseTo(LYRIC_BASELINE_SS, within(TOLERANCE));
    }

    @Test
    void testExtenderDrawnFromStartToEnd() {
        var connector = new LyricConnectorLayout(5.0, 20.0, 1, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(connector), NARROW_STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(1)).draw(lineCap.capture());

        var drawn = (Line2D.Double) lineCap.getValue();

        assertThat(drawn.x1).isCloseTo(5.0, within(TOLERANCE));
        assertThat(drawn.x2).isCloseTo(20.0, within(TOLERANCE));

        assertThat(drawn.y1).isCloseTo(NARROW_LYRIC_BASELINE_SS, within(TOLERANCE));
        assertThat(drawn.y2).isCloseTo(NARROW_LYRIC_BASELINE_SS, within(TOLERANCE));
    }

    @Test
    void testDanglingExtenderDrawnFromStartToEnd() {
        var connector = new LyricConnectorLayout(5.0, 15.0, 1, Kind.DANGLING_EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(connector), NARROW_STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(1)).draw(lineCap.capture());

        var drawn = (Line2D.Double) lineCap.getValue();

        assertThat(drawn.x1).isCloseTo(5.0, within(TOLERANCE));
        assertThat(drawn.x2).isCloseTo(15.0, within(TOLERANCE));

        assertThat(drawn.y1).isCloseTo(NARROW_LYRIC_BASELINE_SS, within(TOLERANCE));
        assertThat(drawn.y2).isCloseTo(NARROW_LYRIC_BASELINE_SS, within(TOLERANCE));
    }

    // Whichever verse is active draws on the one lyric baseline — a second-language connector is
    // not a second row, so it lands exactly where a first-verse connector would.
    @Test
    void testConnectorFromANonFirstVerseRendersOnTheSameBaseline() {
        var connector = new LyricConnectorLayout(
            0.0, 10.0, UNEDITED_VERSE, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(1)).draw(lineCap.capture());

        var drawn = (Line2D.Double) lineCap.getValue();

        assertThat(drawn.y1).isCloseTo(LYRIC_BASELINE_SS, within(TOLERANCE));
        assertThat(drawn.y2).isCloseTo(LYRIC_BASELINE_SS, within(TOLERANCE));
    }

    @Test
    void testExtenderUsesExtenderStroke() {
        var hyphen = new LyricConnectorLayout(0.0, 4.0, 1, Kind.HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var extender = new LyricConnectorLayout(0.0, 4.0, 1, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(hyphen, extender), STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var strokeCap = ArgumentCaptor.forClass(Stroke.class);
        verify(g2, atLeastOnce()).setStroke(strokeCap.capture());

        // Only the extender calls setStroke; hyphen uses drawGlyphVector.
        var actualStrokes = strokeCap.getAllValues().stream()
            .filter(s -> s instanceof BasicStroke)
            .map(s -> (BasicStroke) s)
            .toList();

        assertThat(actualStrokes).hasSize(1);
        assertThat(actualStrokes.getFirst().getLineWidth()).isEqualTo(0.1045f);
    }

    @Test
    void testSelectedSourceElementRendersConnectorInSelectionColor() {
        var connector = new LyricConnectorLayout(5.0, 20.0, 1, Kind.EXTENDER, 0);
        var builder = builderWith(List.of(connector), NARROW_STAFF_TO_LYRICS_GAP_SS);
        RenderContextTestHelper.enableSelection(builder, 0);
        var invariants = builder.build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        verify(g2).setColor(ScoreView.getSelectionColor());
    }

    @Test
    void testNoConnectorsIsNoOp() {
        var invariants = builderWith(List.of(), STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        verifyNoInteractions(g2);
    }

    // ======================================================================
    // drawHyphen count > 1 — multiple evenly-spaced hyphens (Row 25)
    // ======================================================================

    @Test
    void testHyphenMultiple_wideGap_drawsCorrectCountAndPositions() {
        // preferredCellWidthSs = HYPHEN_WIDENING_FACTOR * HYPHEN_WIDTH_SS
        //                      = 1.75 * 0.875 = 1.53125
        // gapSs = endX - startX = 5.0 - 0.0 = 5.0
        // count = floor(5.0 / 1.53125) = floor(3.265) = 3
        // offsetSs = (5.0 - 3 * 1.53125) / 2 = (5.0 - 4.59375) / 2 = 0.203125
        // cell centers: offsetSs + 0.5*cell, offsetSs + 1.5*cell, offsetSs + 2.5*cell
        //   = 0.203125 + 0.765625 = 0.96875
        //   = 0.203125 + 2.296875 = 2.5
        //   = 0.203125 + 3.828125 = 4.03125
        // drawGlyphVector x = cellCenter - HYPHEN_WIDTH_SS / 2
        //   = 0.96875 - 0.4375, 2.5 - 0.4375, 4.03125 - 0.4375
        //   = 0.53125, 2.0625, 3.59375
        var connector = new LyricConnectorLayout(0.0, 5.0, 1, Kind.HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        var xCap = ArgumentCaptor.forClass(Float.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(3)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), yCap.capture());

        var preferredCellWidthSs = LyricRenderMetrics.HYPHEN_WIDENING_FACTOR * HYPHEN_WIDTH_SS;
        var gapSs = 5.0;
        var count = (int) Math.floor(gapSs / preferredCellWidthSs);
        var offsetSs = (gapSs - count * preferredCellWidthSs) / 2.0;
        var xs = xCap.getAllValues();

        // Verify all three X positions match the formula
        for (var i = 0; i < count; i++) {
            var cellCenterXSs = offsetSs + (i + 0.5) * preferredCellWidthSs;
            var expectedX = cellCenterXSs - HYPHEN_WIDTH_SS / 2.0;
            assertThat(xs.get(i).doubleValue()).isCloseTo(expectedX, within(TOLERANCE));
        }

        // All hyphens are at the same verse baseline (verse 1)
        yCap.getAllValues().forEach(y ->
            assertThat(y.doubleValue()).isCloseTo(LYRIC_BASELINE_SS, within(TOLERANCE)));
    }

    // ======================================================================
    // Hyphen chain preview — an unclosed chain engraved up to the open editor
    // ======================================================================

    @Test
    void testDanglingHyphenEngravedUpToOpenEditor() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants =
            builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, EDITOR_COLUMN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // The chain runs to the editor, not to the connector's own end, so it is a distributed run
        // over CHAIN_START_XSS..EDITOR_COLUMN_XSS rather than one hyphen centered in the first gap.
        assertHyphenRunBetween(g2, CHAIN_START_XSS, EDITOR_COLUMN_XSS);
    }

    @Test
    void testDanglingHyphenEndsAtEditedColumnLeftEdgeNotItsX() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(
            List.of(connector),
            STAFF_TO_LYRICS_GAP_SS,
            editedElement,
            EDITOR_COLUMN_XSS,
            EDITOR_COLUMN_LEFT_EXTENT_SS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // The edited column reaches left of its own X, so its left edge, X and right edge are three
        // different numbers. The chain must stop at the left edge: ending at the column's X or right
        // edge instead would run the hyphens under the note's leading accidental.
        assertHyphenRunBetween(g2, CHAIN_START_XSS, EDITOR_COLUMN_XSS + EDITOR_COLUMN_LEFT_EXTENT_SS);
    }

    @Test
    void testDanglingHyphenIgnoresEditorExactlyAtChainStart() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(
            List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, CHAIN_START_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // The editor sits exactly on the syllable that opened the chain, leaving no gap to engrave.
        // Previewing a zero-width run here would drop the hyphen at the chain start instead of at
        // the midpoint of the chain's own gap.
        assertSingleHyphenCenteredInChainGap(g2);
    }

    @Test
    void testDanglingHyphenPreviewTooNarrowForTwoHyphensDrawsOneCenteredOnTheEditor() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(
            List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, EDITOR_COLUMN_ONE_HYPHEN_AWAY_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // Only one hyphen fits, so the run collapses to a single hyphen — but centered in the gap up
        // to the editor, not in the connector's own wider gap.
        assertSingleHyphenCenteredBetween(g2, CHAIN_START_XSS, EDITOR_COLUMN_ONE_HYPHEN_AWAY_XSS);
    }

    @Test
    void testClosedHyphenRunUnaffectedByOpenEditor() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants =
            builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, EDITOR_COLUMN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // Growing toward the editor belongs to unclosed chains only. A hyphen run already closed by
        // its following syllable keeps its own span, however far along the line the editor sits.
        assertHyphenRunBetween(g2, CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS);
    }

    @Test
    void testExtenderUnaffectedByOpenEditor() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var invariants =
            builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, EDITOR_COLUMN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // The preview stretches hyphen chains only; an extender keeps the span layout gave it.
        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(1)).draw(lineCap.capture());

        var drawn = (Line2D.Double) lineCap.getValue();

        assertThat(drawn.x1).isCloseTo(CHAIN_START_XSS, within(TOLERANCE));
        assertThat(drawn.x2).isCloseTo(CHAIN_NEXT_ELEMENT_XSS, within(TOLERANCE));
    }

    @Test
    void testPreviewAppliesOnlyToTheEditedVerseInTheSamePass() {
        var editedElement = note();
        var editedVerseChain = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        // Placed left of the editor, so nothing but the verse mismatch can hold its preview back.
        var otherVerseChain = new LyricConnectorLayout(
            OTHER_CHAIN_START_XSS, OTHER_CHAIN_NEXT_ELEMENT_XSS, UNEDITED_VERSE,
            Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(
            List.of(editedVerseChain, otherVerseChain),
            STAFF_TO_LYRICS_GAP_SS,
            editedElement,
            EDITOR_COLUMN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        // Both chains draw on the one lyric baseline, so they are told apart by the disjoint X spans
        // they occupy. Only the edited verse's chain may grow toward the editor.
        var xCap = ArgumentCaptor.forClass(Float.class);
        var previewedCount = hyphenCountBetween(CHAIN_START_XSS, EDITOR_COLUMN_XSS);

        verify(g2, times(previewedCount + 1)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), anyFloat());

        var editedVerseXs = new ArrayList<Double>();
        var otherVerseXs = new ArrayList<Double>();

        for (var x : xCap.getAllValues()) {
            if (x < CHAIN_START_XSS) {
                otherVerseXs.add(x.doubleValue());
            } else {
                editedVerseXs.add(x.doubleValue());
            }
        }

        assertHyphenXsCloseTo(editedVerseXs, expectedHyphenRunXs(CHAIN_START_XSS, EDITOR_COLUMN_XSS));

        var unpreviewedCenterXSs = (OTHER_CHAIN_START_XSS + OTHER_CHAIN_NEXT_ELEMENT_XSS) / 2.0;

        assertThat(otherVerseXs).hasSize(1);
        assertThat(otherVerseXs.getFirst())
            .isCloseTo(unpreviewedCenterXSs - HYPHEN_WIDTH_SS / 2.0, within(TOLERANCE));
    }

    @Test
    void testDanglingHyphenIgnoresEditorInAnotherVerse() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, UNEDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants =
            builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, EDITOR_COLUMN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        assertSingleHyphenCenteredInChainGap(g2);
    }

    @Test
    void testDanglingHyphenIgnoresEditorBeforeChainStart() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants = builderWith(
            List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, EDITOR_COLUMN_BEFORE_CHAIN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        assertSingleHyphenCenteredInChainGap(g2);
    }

    @Test
    void testDanglingHyphenIgnoresEditorOnAnotherLine() {
        var editedElement = note();
        var connector = new LyricConnectorLayout(
            CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS, EDITED_VERSE, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var invariants =
            builderWith(List.of(connector), STAFF_TO_LYRICS_GAP_SS, editedElement, NO_COLUMN_XSS).build();
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, invariants, ElementFrame.LINE_LEVEL);

        assertSingleHyphenCenteredInChainGap(g2);
    }

    /** Asserts the unpreviewed rendering: one hyphen centered in the chain's own layout gap. */
    private static void assertSingleHyphenCenteredInChainGap(Graphics2D g2) {
        assertSingleHyphenCenteredBetween(g2, CHAIN_START_XSS, CHAIN_NEXT_ELEMENT_XSS);
    }

    /** Asserts exactly one hyphen was drawn, centered between {@code startXSs} and {@code endXSs}. */
    private static void assertSingleHyphenCenteredBetween(Graphics2D g2, double startXSs, double endXSs) {
        var xCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(1)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), anyFloat());

        var centerXSs = (startXSs + endXSs) / 2.0;

        assertThat(xCap.getValue().doubleValue())
            .isCloseTo(centerXSs - HYPHEN_WIDTH_SS / 2.0, within(TOLERANCE));
    }

    /**
     * Asserts the hyphens drawn are exactly the distributed run that spans {@code startXSs} to
     * {@code endXSs} — both the number of them and where each one sits.
     */
    private static void assertHyphenRunBetween(Graphics2D g2, double startXSs, double endXSs) {
        var expectedXs = expectedHyphenRunXs(startXSs, endXSs);
        var xCap = ArgumentCaptor.forClass(Float.class);

        verify(g2, times(expectedXs.size())).drawGlyphVector(any(GlyphVector.class), xCap.capture(), anyFloat());

        var actualXs = xCap.getAllValues().stream().map(Float::doubleValue).toList();

        assertHyphenXsCloseTo(actualXs, expectedXs);
    }

    /** Asserts {@code actualXs} matches {@code expectedXs} in order, within the usual tolerance. */
    private static void assertHyphenXsCloseTo(List<Double> actualXs, List<Double> expectedXs) {
        assertThat(actualXs).hasSameSizeAs(expectedXs);

        for (var i = 0; i < expectedXs.size(); i++) {
            assertThat(actualXs.get(i)).isCloseTo(expectedXs.get(i), within(TOLERANCE));
        }
    }

    /** Returns how many hyphens the renderer distributes across {@code startXSs}..{@code endXSs}. */
    private static int hyphenCountBetween(double startXSs, double endXSs) {
        return (int) Math.floor((endXSs - startXSs) / preferredHyphenCellWidthSs());
    }

    /** Returns the left edge of every hyphen in the run spanning {@code startXSs}..{@code endXSs}. */
    private static List<Double> expectedHyphenRunXs(double startXSs, double endXSs) {
        var preferredCellWidthSs = preferredHyphenCellWidthSs();
        var gapSs = endXSs - startXSs;
        var count = hyphenCountBetween(startXSs, endXSs);
        var offsetSs = (gapSs - count * preferredCellWidthSs) / 2.0;
        var xs = new ArrayList<Double>();

        for (var i = 0; i < count; i++) {
            var cellCenterXSs = startXSs + offsetSs + (i + 0.5) * preferredCellWidthSs;
            xs.add(cellCenterXSs - HYPHEN_WIDTH_SS / 2.0);
        }

        return xs;
    }

    private static double preferredHyphenCellWidthSs() {
        return LyricRenderMetrics.HYPHEN_WIDENING_FACTOR * HYPHEN_WIDTH_SS;
    }
}
