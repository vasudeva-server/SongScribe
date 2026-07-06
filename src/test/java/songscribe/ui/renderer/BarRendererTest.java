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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.smufl.SMuFLGlyph;

class BarRendererTest extends UnitTest {

    private static final BarRenderer RENDERER = BarRenderer.getInstance();

    // X position override for driving the render() path without a LayoutResult entry
    private static final double ELEMENT_X_SS = 5.0;

    private static final double TOLERANCE = 1e-9;

    /**
     * Renders the given element type and returns a spy on the Graphics2D so callers
     * can verify which drawing calls were made.
     */
    private Graphics2D renderAndSpy(ElementType type) {
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var note = type.newInstance();
        var frame = ElementFrame.LINE_LEVEL.withElement(0, ELEMENT_X_SS);

        RENDERER.render(invariants, frame, note, g2Spy);

        return g2Spy;
    }

    // ==========================================================================
    // renderBarLineOrRepeat — switch cases (row 23)
    // ==========================================================================

    @Test
    void testRenderSingleBarlineCallsOneFillAndNoDrawString() {
        // Single barline: one thin bar, no repeat dots
        var g2Spy = renderAndSpy(ElementType.SINGLE_BARLINE);

        verify(g2Spy, times(1)).fill(any());
        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testRenderDoubleBarlineCallsTwoFillsAndNoDrawString() {
        // Double barline: two thin bars, no repeat dots
        var g2Spy = renderAndSpy(ElementType.DOUBLE_BARLINE);

        verify(g2Spy, times(2)).fill(any());
        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testRenderFinalDoubleBarlineCallsTwoFillsAndNoDrawString() {
        // Final double barline: thin bar + thick bar, no repeat dots
        var g2Spy = renderAndSpy(ElementType.FINAL_DOUBLE_BARLINE);

        verify(g2Spy, times(2)).fill(any());
        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testRenderRepeatLeftCallsTwoFillsAndOneRepeatDotsDrawString() {
        // Left repeat: thick | sep | thin | sep | dots
        var g2Spy = renderAndSpy(ElementType.REPEAT_LEFT);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy, times(2)).fill(any());
        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getValue())
            .isEqualTo(SMuFLGlyph.REPEAT_DOTS.asString());
    }

    @Test
    void testRenderRepeatRightCallsTwoFillsAndOneRepeatDotsDrawString() {
        // Right repeat: dots | sep | thin | sep | thick
        var g2Spy = renderAndSpy(ElementType.REPEAT_RIGHT);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy, times(2)).fill(any());
        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getValue())
            .isEqualTo(SMuFLGlyph.REPEAT_DOTS.asString());
    }

    @Test
    void testRenderRepeatLeftRightCallsThreeFillsAndTwoRepeatDotsDrawStrings() {
        // Left-right repeat: dots | thin | thick | thin | dots
        var g2Spy = renderAndSpy(ElementType.REPEAT_LEFT_RIGHT);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy, times(3)).fill(any());
        verify(g2Spy, times(2)).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getAllValues())
            .allMatch(s -> s.equals(SMuFLGlyph.REPEAT_DOTS.asString()));
    }

    // ==========================================================================
    // drawRightRepeat — returned x value (row 24)
    // ==========================================================================

    @Test
    void testDrawRightRepeatReturnsXAfterThickBar() {
        // drawRightRepeat: dots | sep | thin | sep | thick; returns x + thick
        // Starting from x = 0:
        //   after dots:  x = 0 + REPEAT_DOTS_ADVANCE_WIDTH_SS + sep
        //   after thin:  x += thin + sep
        //   after thick: x += thick  → returned value
        var g2 = RenderContextTestHelper.realG2();
        var thin = LineThickness.THIN_BARLINE_SS;
        var thick = LineThickness.THICK_BARLINE_SS;
        var sep = LineThickness.BARLINE_SEPARATION_SS;
        final double startX = 0.0;
        final double topY = -2.0;
        final double bottomY = 2.0;

        var x = startX + SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS + sep;
        x += thin + sep;
        x += thick;
        var expectedX = x;

        var actualX = BarRenderer.drawRightRepeat(g2, startX, thin, thick, topY, bottomY, sep);

        assertThat(actualX).isCloseTo(expectedX, within(TOLERANCE));
    }
}
