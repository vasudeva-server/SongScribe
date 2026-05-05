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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static songscribe.ui.layout.LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX;

import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.ui.component.Score;
import songscribe.ui.layout.SongLayoutMetrics;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LyricConnectorLayout;
import songscribe.ui.layout.LyricConnectorLayout.Kind;
import songscribe.ui.layout.LyricRenderMetrics;

class LyricConnectorRendererTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final double HYPHEN_WIDTH_SS = 0.875;
    private static final LyricRenderMetrics TEST_LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, HYPHEN_WIDTH_SS, 0.0);

    private static SongLayoutMetrics metrics(double staffToLyricsGapSs, double lyricsLineHeightSs, int verseCount) {
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

    private static ElementRenderContext contextWith(
        List<LyricConnectorLayout> connectors,
        SongLayoutMetrics metrics
    ) {
        var builder = LayoutResult.builder();

        for (var connector : connectors) {
            builder.addLyricConnector(connector);
        }

        var ctx = new ElementRenderContext(new Song());
        ctx.setLayoutResult(builder.build());
        ctx.setSongLayoutMetrics(metrics);
        ctx.setLyricRenderMetrics(TEST_LYRIC_RENDER_METRICS);
        return ctx;
    }

    @Test
    void testHyphenDrawnCenteredAtMidpoint() {
        var connector = new LyricConnectorLayout(10.0, 11.5, 1, Kind.HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var ctx = contextWith(List.of(connector), metrics(1.0, 2.5, 1));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        var xCap = ArgumentCaptor.forClass(Float.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(1)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), yCap.capture());

        var center = (10.0 + 11.5) / 2.0;

        assertThat(xCap.getValue().doubleValue()).isCloseTo(center - HYPHEN_WIDTH_SS / 2.0, within(TOLERANCE));

        // Verse 1 baseline: staffBottom(5) + below(1) + gap(1) + 0 * lineHeight(2.5) = 7.0
        assertThat(yCap.getValue().doubleValue()).isCloseTo(7.0, within(TOLERANCE));
    }

    @Test
    void testDanglingHyphenDrawnCenteredInGap() {
        var connector = new LyricConnectorLayout(8.0, 12.0, 1, Kind.DANGLING_HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var ctx = contextWith(List.of(connector), metrics(1.0, 2.5, 1));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        var xCap = ArgumentCaptor.forClass(Float.class);
        var yCap = ArgumentCaptor.forClass(Float.class);
        verify(g2, times(1)).drawGlyphVector(any(GlyphVector.class), xCap.capture(), yCap.capture());

        var center = (8.0 + 12.0) / 2.0;

        assertThat(xCap.getValue().doubleValue()).isCloseTo(center - HYPHEN_WIDTH_SS / 2.0, within(TOLERANCE));
        assertThat(yCap.getValue().doubleValue()).isCloseTo(7.0, within(TOLERANCE));
    }

    @Test
    void testExtenderDrawnFromStartToEnd() {
        var connector = new LyricConnectorLayout(5.0, 20.0, 1, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var ctx = contextWith(List.of(connector), metrics(0.5, 2.0, 1));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(1)).draw(lineCap.capture());

        var drawn = (Line2D.Double) lineCap.getValue();

        assertThat(drawn.x1).isCloseTo(5.0, within(TOLERANCE));
        assertThat(drawn.x2).isCloseTo(20.0, within(TOLERANCE));

        // Verse 1 baseline: staffBottom(5) + below(1) + gap(0.5) + 0 * lineHeight(2.0) = 6.5
        assertThat(drawn.y1).isCloseTo(6.5, within(TOLERANCE));
        assertThat(drawn.y2).isCloseTo(6.5, within(TOLERANCE));
    }

    @Test
    void testDanglingExtenderDrawnFromStartToEnd() {
        var connector = new LyricConnectorLayout(5.0, 15.0, 1, Kind.DANGLING_EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var ctx = contextWith(List.of(connector), metrics(0.5, 2.0, 1));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(1)).draw(lineCap.capture());

        var drawn = (Line2D.Double) lineCap.getValue();

        assertThat(drawn.x1).isCloseTo(5.0, within(TOLERANCE));
        assertThat(drawn.x2).isCloseTo(15.0, within(TOLERANCE));

        // Verse 1 baseline: staffBottom(5) + below(1) + gap(0.5) + 0 * lineHeight(2.0) = 6.5
        assertThat(drawn.y1).isCloseTo(6.5, within(TOLERANCE));
        assertThat(drawn.y2).isCloseTo(6.5, within(TOLERANCE));
    }

    @Test
    void testDistinctVersesRenderAtDistinctY() {
        var verse1 = new LyricConnectorLayout(0.0, 10.0, 1, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var verse2 = new LyricConnectorLayout(0.0, 10.0, 2, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var ctx = contextWith(List.of(verse1, verse2), metrics(1.0, 2.0, 2));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(2)).draw(lineCap.capture());

        var first = (Line2D.Double) lineCap.getAllValues().get(0);
        var second = (Line2D.Double) lineCap.getAllValues().get(1);

        // below=1, gap=1, lineHeight=2.0 ⇒ verse1 baseline = 7.0, verse2 baseline = 9.0
        assertThat(first.y1).isCloseTo(7.0, within(TOLERANCE));
        assertThat(second.y1).isCloseTo(9.0, within(TOLERANCE));
    }

    @Test
    void testExtenderUsesExtenderStroke() {
        var hyphen = new LyricConnectorLayout(0.0, 4.0, 1, Kind.HYPHEN, NO_SOURCE_ELEMENT_INDEX);
        var extender = new LyricConnectorLayout(0.0, 4.0, 1, Kind.EXTENDER, NO_SOURCE_ELEMENT_INDEX);
        var ctx = contextWith(List.of(hyphen, extender), metrics(1.0, 2.0, 1));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        var strokeCap = ArgumentCaptor.forClass(Stroke.class);
        verify(g2, atLeastOnce()).setStroke(strokeCap.capture());

        // Only the extender calls setStroke; hyphen uses drawGlyphVector.
        var actualStrokes = strokeCap.getAllValues().stream()
            .filter(s -> s instanceof BasicStroke)
            .map(s -> (BasicStroke) s)
            .toList();

        assertThat(actualStrokes).hasSize(1);
        assertThat(actualStrokes.get(0).getLineWidth()).isEqualTo(0.1045f);
    }

    @Test
    void testSelectedSourceElementRendersConnectorInSelectionColor() {
        var connector = new LyricConnectorLayout(5.0, 20.0, 1, Kind.EXTENDER, 0);
        var ctx = contextWith(List.of(connector), metrics(0.5, 2.0, 1));
        RenderContextTestHelper.enableSelection(ctx, 0);
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        verify(g2).setColor(Score.getSelectionColor());
    }

    @Test
    void testNoConnectorsIsNoOp() {
        var ctx = contextWith(List.of(), metrics(0, 0, 0));
        var g2 = mock(Graphics2D.class);

        LyricConnectorRenderer.getInstance().render(g2, ctx);

        verifyNoInteractions(g2);
    }
}
