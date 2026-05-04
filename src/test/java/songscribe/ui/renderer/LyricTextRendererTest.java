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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.layout.SongLayoutMetrics;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LyricBoxLayout;
import songscribe.ui.layout.LyricRenderMetrics;

class LyricTextRendererTest extends UnitTest {

    private static final double PX_PER_SS = 8.0;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static int toPx(double ss) {
        return (int) Math.round(ss * PX_PER_SS);
    }

    private static SongLayoutMetrics metrics(double staffToLyricsGapSs, double lyricsLineHeightSs, int verseCount) {
        // staffTop=1, staffHeight=4 ⇒ staffBottom=5. verse 1 baseline = 5 + maxBelowContent + gap
        return new SongLayoutMetrics(
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

        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(layoutResult);
        ctx.setSongLayoutMetrics(metrics(1.0, 2.5, 1));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        var xCap = ArgumentCaptor.forClass(Integer.class);
        var yCap = ArgumentCaptor.forClass(Integer.class);
        var textCap = ArgumentCaptor.forClass(String.class);
        verify(g2, times(1)).drawString(textCap.capture(), xCap.capture(), yCap.capture());

        assertThat(textCap.getValue()).isEqualTo("do");
        assertThat(xCap.getValue()).isEqualTo(toPx(3.25));

        // Baseline for verse 1 = staffBottom(5) + below(1) + gap(1) + 0 * lineHeight(2.5) = 7.0
        assertThat(yCap.getValue()).isEqualTo(toPx(7.0));
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

        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(layoutResult);
        ctx.setSongLayoutMetrics(metrics(1.0, 2.0, 2));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        var textCap = ArgumentCaptor.forClass(String.class);
        var yCap = ArgumentCaptor.forClass(Integer.class);
        verify(g2, times(2)).drawString(textCap.capture(), anyInt(), yCap.capture());

        assertThat(textCap.getAllValues()).containsExactly("v1", "v2");

        // staffBottom=5, below=1, gap=1, lineHeight=2.0 ⇒ verse1=7.0, verse2=9.0
        assertThat(yCap.getAllValues().get(0)).isEqualTo(toPx(7.0));
        assertThat(yCap.getAllValues().get(1)).isEqualTo(toPx(9.0));
    }

    @Test
    void testNoBoxesIsNoOp() {
        var element = ElementType.CROTCHET.newInstance();
        var layoutResult = LayoutResult.builder().build();

        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(layoutResult);
        ctx.setSongLayoutMetrics(metrics(0, 0, 0));

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

        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(layoutResult);
        ctx.setSongLayoutMetrics(metrics(0.5, 2.5, 1));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        verify(g2).setFont(any(Font.class));
        verify(g2).drawString(anyString(), anyInt(), anyInt());
    }

    // T28
    @Test
    void testSkipsActivelyEditedElementButRendersOthers() {
        var activeElement = ElementType.CROTCHET.newInstance();
        var otherElement = ElementType.CROTCHET.newInstance();

        var activeBox = new LyricBoxLayout(1.0, 1.5, 1, "la");
        var otherBox = new LyricBoxLayout(3.0, 1.5, 1, "sol");

        var layoutResult = LayoutResult.builder()
            .addLyricBox(activeElement, activeBox)
            .addLyricBox(otherElement, otherBox)
            .build();

        var lyricRenderMetrics = new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

        var activeCtx = new ElementRenderContext(new Song());
        activeCtx.setLayoutResult(layoutResult);
        activeCtx.setSongLayoutMetrics(metrics(1.0, 2.5, 1));
        activeCtx.setLyricRenderMetrics(lyricRenderMetrics);
        activeCtx.setActivelyEditedElement(activeElement);

        var g2Active = mock(Graphics2D.class);
        LyricTextRenderer.getInstance().render(activeElement, g2Active, activeCtx);
        verifyNoInteractions(g2Active);

        var otherCtx = new ElementRenderContext(new Song());
        otherCtx.setLayoutResult(layoutResult);
        otherCtx.setSongLayoutMetrics(metrics(1.0, 2.5, 1));
        otherCtx.setLyricRenderMetrics(lyricRenderMetrics);
        otherCtx.setActivelyEditedElement(activeElement);

        var g2Other = mock(Graphics2D.class);
        LyricTextRenderer.getInstance().render(otherElement, g2Other, otherCtx);
        verify(g2Other).drawString(anyString(), anyInt(), anyInt());
    }

    @Test
    void testSelectedLyricPaintsInSelectionColor() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.5, 1, "v1");
        var layoutResult = LayoutResult.builder().addLyricBox(element, box).build();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(layoutResult);
        ctx.setSongLayoutMetrics(metrics(1.0, 2.0, 1));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));
        ctx.setSelectionProvider(selectionProvider);
        when(selectionProvider.isLyricSelected(element, 1, 0)).thenReturn(true);

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        verify(g2).setColor(Score.getSelectionColor());
        verify(g2).drawString("v1", toPx(2.0), toPx(7.0));
    }

    @Test
    void testSelectedElementPaintsLyricInSelectionColor() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.5, 1, "v1");
        var layoutResult = LayoutResult.builder().addLyricBox(element, box).build();
        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(layoutResult);
        ctx.setSongLayoutMetrics(metrics(1.0, 2.0, 1));
        ctx.setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0));
        ctx.setCurrentElementIndex(0);
        RenderContextTestHelper.enableSelection(ctx, 0);

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(element, g2, ctx);

        verify(g2).setColor(Score.getSelectionColor());
    }
}
