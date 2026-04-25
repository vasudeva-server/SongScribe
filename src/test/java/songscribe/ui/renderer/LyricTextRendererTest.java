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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.ui.layout.CompositionLayoutMetrics;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LyricBoxLayout;
import songscribe.ui.layout.LyricRenderMetrics;

class LyricTextRendererTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static CompositionLayoutMetrics metrics(double staffToLyricsGapSs, double lyricsLineHeightSs, int verseCount) {
        // staffTop=1, staffHeight=4 ⇒ staffBottom=5. verse 1 baseline = 5 + maxBelowContent + gap + 1*line
        return new CompositionLayoutMetrics(
            1.0,
            1.0,
            1.0,
            staffToLyricsGapSs,
            lyricsLineHeightSs,
            verseCount,
            verseCount * lyricsLineHeightSs,
            1.0 + 4.0 + 1.0 + staffToLyricsGapSs + verseCount * lyricsLineHeightSs
        );
    }

    @Test
    void testDrawsSingleBoxAtVerseBaseline() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(3.25, 2.0, 1, "do");
        var layoutResult = LayoutResult.builder().addLyricBox(element, box).build();

        var ctx = new ElementRenderContext(new Composition());
        ctx.setLayoutResult(layoutResult);
        ctx.setCompositionLayoutMetrics(metrics(1.0, 2.5, 1));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        var xCap = ArgumentCaptor.forClass(Float.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        var textCap = ArgumentCaptor.forClass(String.class);
        verify(g2, times(1)).drawString(textCap.capture(), xCap.capture(), yCap.capture());

        assertThat(textCap.getValue()).isEqualTo("do");
        assertThat(xCap.getValue().doubleValue()).isCloseTo(3.25, within(TOLERANCE));

        // Baseline for verse 1 = staffBottom(5) + below(1) + gap(1) + 1 * lineHeight(2.5) = 9.5
        assertThat(yCap.getValue().doubleValue()).isCloseTo(9.5, within(TOLERANCE));
    }

    @Test
    void testDrawsMultipleVersesAtDistinctBaselines() {
        var element = ElementType.CROTCHET.newInstance();
        var verse1 = new LyricBoxLayout(2.0, 1.5, 1, "v1");
        var verse2 = new LyricBoxLayout(2.0, 1.5, 2, "v2");
        var layoutResult = LayoutResult.builder()
            .addLyricBox(element, verse1)
            .addLyricBox(element, verse2)
            .build();

        var ctx = new ElementRenderContext(new Composition());
        ctx.setLayoutResult(layoutResult);
        ctx.setCompositionLayoutMetrics(metrics(1.0, 2.0, 2));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        var textCap = ArgumentCaptor.forClass(String.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(2)).drawString(textCap.capture(), anyFloat(), yCap.capture());

        assertThat(textCap.getAllValues()).containsExactly("v1", "v2");

        // staffBottom=5, below=1, gap=1, lineHeight=2.0 ⇒ verse1=9.0, verse2=11.0
        assertThat(yCap.getAllValues().get(0).doubleValue()).isCloseTo(9.0, within(TOLERANCE));
        assertThat(yCap.getAllValues().get(1).doubleValue()).isCloseTo(11.0, within(TOLERANCE));
    }

    @Test
    void testNoBoxesIsNoOp() {
        var element = ElementType.CROTCHET.newInstance();
        var layoutResult = LayoutResult.builder().build();

        var ctx = new ElementRenderContext(new Composition());
        ctx.setLayoutResult(layoutResult);
        ctx.setCompositionLayoutMetrics(metrics(0, 0, 0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        verifyNoInteractions(g2);
    }

    @Test
    void testDrawStringWidth() {
        // Sanity check that drawString receives the correct text argument irrespective of font scaling.
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(0.0, 1.0, 1, "re");
        var layoutResult = LayoutResult.builder().addLyricBox(element, box).build();

        var ctx = new ElementRenderContext(new Composition());
        ctx.setLayoutResult(layoutResult);
        ctx.setCompositionLayoutMetrics(metrics(0.5, 2.5, 1));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        verify(g2).setFont(any(Font.class));
        verify(g2).drawString(anyString(), anyFloat(), anyFloat());
    }
}
