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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.layout.LayoutResult;
import songscribe.smufl.SMuFLGlyph;

class TrillRendererTest extends UnitTest {

    private static final TrillRenderer RENDERER = TrillRenderer.getInstance();

    // Arbitrary trill/wavy-line coordinates
    private static final double TRILL_TOP_Y_SS = -3.0;
    private static final double ANCHOR_LAYOUT_X_SS = 5.0;
    private static final double END_ELEMENT_X_SS = 15.0;

    // The wavy length is reconstructed from a scale factor and a segment count, so it carries a
    // little floating-point rounding rather than being exact.
    private static final double WAVY_LENGTH_TOLERANCE_SS = 1e-9;

    // ==========================================================================
    // drawWavyLine — segment count (row 29)
    // ==========================================================================

    @Test
    void testDrawWavyLineIsNoOpForNonPositiveLength() {
        // x2 <= x1: nothing should be drawn
        var g2Spy = spy(RenderContextTestHelper.realG2());

        RENDERER.drawWavyLine(g2Spy, 5.0, 0.0, 5.0, Color.BLACK);
        RENDERER.drawWavyLine(g2Spy, 5.0, 0.0, 3.0, Color.BLACK);

        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testDrawWavyLineWithLengthEqualToSegmentWidthUsesOneSegment() {
        // length = 1 × WIGGLE_SEGMENT_WIDTH_SS → segments = round(1.0) = 1
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var length = TrillRenderer.WIGGLE_SEGMENT_WIDTH_SS;

        RENDERER.drawWavyLine(g2Spy, 0.0, 0.0, length, Color.BLACK);

        verify(g2Spy, times(1)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testDrawWavyLineWithLengthForThreeSegmentsDrawsThreeTimes() {
        // length = 3 × WIGGLE_SEGMENT_WIDTH_SS → segments = 3
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var expectedSegments = 3;
        var length = expectedSegments * TrillRenderer.WIGGLE_SEGMENT_WIDTH_SS;

        RENDERER.drawWavyLine(g2Spy, 0.0, 0.0, length, Color.BLACK);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2Spy, times(expectedSegments)).drawString(
            glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getAllValues())
            .allMatch(s -> s.equals(SMuFLGlyph.WIGGLE_TRILL_FASTER.asString()));
    }

    @Test
    void testDrawWavyLineRoundingEdgeCaseAlwaysDrawsAtLeastOneSegment() {
        // A very short length (less than WIGGLE_SEGMENT_WIDTH_SS but > 0)
        // must still draw at least 1 segment due to Math.max(1, ...).
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var tinyLength = TrillRenderer.WIGGLE_SEGMENT_WIDTH_SS * 0.1;

        RENDERER.drawWavyLine(g2Spy, 0.0, 0.0, tinyLength, Color.BLACK);

        verify(g2Spy, times(1)).drawString(anyString(), anyFloat(), anyFloat());
    }

    // ==========================================================================
    // renderTrillAtPosition — branch on endNote (row 30)
    // ==========================================================================

    @Test
    void testRenderTrillAtPositionSingleNotePassesNaNEndXToRenderTrill() {
        // endNote is null → single-note trill path → renderTrill is called with NaN endXSs.
        // The distinguishing observable: renderTrill only calls drawWavyLine when endXSs
        // is not NaN, so for a single-note trill there should be NO WIGGLE_TRILL_FASTER drawString
        // call — only the ORNAMENT_TRILL glyph.
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var anchor = ElementType.CROTCHET.newInstance();
        var layoutResult = LayoutResult.builder().build();

        RENDERER.renderTrillAtPosition(
            g2Spy, anchor, null,
            ANCHOR_LAYOUT_X_SS, TRILL_TOP_Y_SS,
            Color.BLACK, layoutResult);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2Spy, atLeastOnce())
            .drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getAllValues())
            .anyMatch(s -> s.equals(SMuFLGlyph.ORNAMENT_TRILL.asString()));

        assertThat(glyphCaptor.getAllValues())
            .noneMatch(s -> s.equals(SMuFLGlyph.WIGGLE_TRILL_FASTER.asString()));
    }

    @Test
    void testRenderTrillAtPositionMultiNoteDrawsWavyLineGlyphs() {
        // endNote != null && endNote != anchor → multi-note trill path.
        // endXSs = layoutResult.getElementXSs(endNote) + NOTE_HEAD_WIDTH_SS
        // renderTrill is called with a valid endXSs, so drawWavyLine runs and
        // WIGGLE_TRILL_FASTER glyph strings appear when endXSs > wavyStartX.
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var anchor = ElementType.CROTCHET.newInstance();
        var endNote = ElementType.CROTCHET.newInstance();

        // Use a mock LayoutResult so we can control the endNote X position.
        // END_ELEMENT_X_SS is large enough that endXSs > wavyStartX = ANCHOR_LAYOUT_X_SS + TRILL_ADVANCE_WIDTH_SS.
        var layoutResult = mock(LayoutResult.class);
        when(layoutResult.getElementXSs(endNote)).thenReturn(END_ELEMENT_X_SS);

        RENDERER.renderTrillAtPosition(
            g2Spy, anchor, endNote,
            ANCHOR_LAYOUT_X_SS, TRILL_TOP_Y_SS,
            Color.BLACK, layoutResult);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2Spy, atLeastOnce())
            .drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getAllValues())
            .anyMatch(s -> s.equals(SMuFLGlyph.ORNAMENT_TRILL.asString()));

        assertThat(glyphCaptor.getAllValues())
            .anyMatch(s -> s.equals(SMuFLGlyph.WIGGLE_TRILL_FASTER.asString()));
    }

    /**
     * The wavy line stops at the end note's right edge, measured with that note's own head width.
     * Ending on a whole note therefore runs the line further right than ending on a quarter note at
     * the same X — before #694 both used the black-notehead width, so the line stopped short of a
     * whole note's ink and appeared to end in mid-air before reaching the note it targets.
     *
     * <p>The drawn length is only observable through the transform {@code drawWavyLine} applies, so
     * it is reconstructed here: the method tiles {@code segments} glyphs of
     * {@code WIGGLE_SEGMENT_WIDTH_SS} each under a horizontal scale chosen so the total equals the
     * real length.
     */
    @Test
    void testRenderTrillWavyLineReachesTheEndNotesOwnRightEdge() {
        var blackRightSs = ElementType.CROTCHET.getElementWidthSs();
        var wholeRightSs = ElementType.SEMIBREVE.getElementWidthSs();

        assertThat(wholeRightSs)
            .as("precondition: the whole notehead really is the wider glyph")
            .isGreaterThan(blackRightSs);

        assertThat(wavyLineLengthSs(ElementType.SEMIBREVE) - wavyLineLengthSs(ElementType.CROTCHET))
            .describedAs("the wavy line grows by the whole head's extra width, nothing else")
            .isCloseTo(wholeRightSs - blackRightSs, within(WAVY_LENGTH_TOLERANCE_SS));
    }

    /**
     * Renders a multi-note trill ending on {@code endType} and returns the wavy line's drawn length
     * in staff spaces, reconstructed from the tiling {@code drawWavyLine} performs.
     */
    private static double wavyLineLengthSs(ElementType endType) {
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var anchor = ElementType.CROTCHET.newInstance();
        var endNote = endType.newInstance();

        var layoutResult = mock(LayoutResult.class);
        when(layoutResult.getElementXSs(endNote)).thenReturn(END_ELEMENT_X_SS);

        RENDERER.renderTrillAtPosition(
            g2Spy, anchor, endNote,
            ANCHOR_LAYOUT_X_SS, TRILL_TOP_Y_SS,
            Color.BLACK, layoutResult);

        var scaleCaptor = ArgumentCaptor.forClass(Double.class);
        verify(g2Spy).scale(scaleCaptor.capture(), eq(1d));

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2Spy, atLeastOnce())
            .drawString(glyphCaptor.capture(), anyFloat(), anyFloat());
        var segments = glyphCaptor.getAllValues().stream()
            .filter(s -> s.equals(SMuFLGlyph.WIGGLE_TRILL_FASTER.asString()))
            .count();

        assertThat(segments).as("the multi-note path must draw at least one wiggle").isPositive();

        return scaleCaptor.getValue() * segments * TrillRenderer.WIGGLE_SEGMENT_WIDTH_SS;
    }

    @Test
    void testRenderTrillAtPositionEndNoteEqualToAnchorIsSingleNotePath() {
        // endNote == anchor → treated as single-note trill (no wavy line)
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var anchor = ElementType.CROTCHET.newInstance();
        var layoutResult = LayoutResult.builder().build();

        RENDERER.renderTrillAtPosition(
            g2Spy, anchor, anchor,
            ANCHOR_LAYOUT_X_SS, TRILL_TOP_Y_SS,
            Color.BLACK, layoutResult);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2Spy, atLeastOnce())
            .drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getAllValues())
            .noneMatch(s -> s.equals(SMuFLGlyph.WIGGLE_TRILL_FASTER.asString()));
    }
}
