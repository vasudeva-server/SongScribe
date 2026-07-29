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
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.engraving.Staff;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricBoxLayout;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class LyricTextRendererTest extends UnitTest {

    private static final double PX_PER_SS = 8.0;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    // Synthetic line geometry. The above-staff content is below the painted floor, so the
    // staff is drawn at MIN_ABOVE_STAFF_SS rather than at the measured 1 ss — and the verse
    // baselines hang off that painted staff, which is where the text actually appears.
    private static final double CONTENT_ABOVE_STAFF_SS = 1.0;
    private static final double CONTENT_BELOW_STAFF_SS = 1.0;
    private static final double STAFF_TO_LYRICS_GAP_SS = 1.0;

    /** The lyric baseline: painted staff bottom + below-staff content + the staff-to-lyrics gap. */
    private static final double LYRIC_BASELINE_SS =
        Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS + CONTENT_BELOW_STAFF_SS + STAFF_TO_LYRICS_GAP_SS;

    // The second language a song's lyrics can be written in. Its syllables draw on the same
    // baseline as the first one's, because only one verse is ever on the page.
    private static final int SECOND_VERSE = 2;

    private static int toPx(double ss) {
        return (int) Math.round(ss * PX_PER_SS);
    }

    /** A layout carrying this test's synthetic above/below-staff geometry. */
    private static LayoutResult.Builder layoutBuilder() {
        return LayoutResult.builder()
            .setContentAboveStaffSs(CONTENT_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(CONTENT_BELOW_STAFF_SS);
    }

    private static LyricRenderMetrics lyricRenderMetrics() {
        return new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, STAFF_TO_LYRICS_GAP_SS);
    }

    @Test
    void testDrawsSingleBoxAtVerseBaseline() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(3.25, 2.0, 1, "do");
        var layoutResult = layoutBuilder().addLyricBox(element, box).build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics())
            .build();

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, element, g2);

        var xCap = ArgumentCaptor.forClass(Integer.class);
        var yCap = ArgumentCaptor.forClass(Integer.class);
        var textCap = ArgumentCaptor.forClass(String.class);
        verify(g2, times(1)).drawString(textCap.capture(), xCap.capture(), yCap.capture());

        assertThat(textCap.getValue()).isEqualTo("do");
        assertThat(xCap.getValue()).isEqualTo(toPx(3.25));

        assertThat(yCap.getValue()).isEqualTo(toPx(LYRIC_BASELINE_SS));
    }

    // Whichever verse is active draws on the one lyric baseline — a second-language syllable is
    // not a second row, so it lands exactly where a first-verse syllable would.
    @Test
    void testDrawsANonFirstVerseOnTheSameBaseline() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.5, SECOND_VERSE, "un");
        var layoutResult = layoutBuilder().addLyricBox(element, box).build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics())
            .build();

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, element, g2);

        var textCap = ArgumentCaptor.forClass(String.class);
        var yCap = ArgumentCaptor.forClass(Integer.class);
        verify(g2, times(1)).drawString(textCap.capture(), anyInt(), yCap.capture());

        assertThat(textCap.getValue()).isEqualTo("un");
        assertThat(yCap.getValue()).isEqualTo(toPx(LYRIC_BASELINE_SS));
    }

    @Test
    void testNoBoxesIsNoOp() {
        var element = ElementType.CROTCHET.newInstance();
        var layoutResult = layoutBuilder().build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .build();

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, element, g2);

        verifyNoInteractions(g2);
    }

    @Test
    void testDrawStringWidth() {
        // Sanity check that drawString receives the correct text argument irrespective of font scaling.
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(0.0, 1.0, 1, "re");
        var layoutResult = layoutBuilder().addLyricBox(element, box).build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics())
            .build();

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, element, g2);

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

        var layoutResult = layoutBuilder()
            .addLyricBox(activeElement, activeBox)
            .addLyricBox(otherElement, otherBox)
            .build();

        var lyricRenderMetrics = lyricRenderMetrics();

        var activeInv = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics)
            .setActivelyEditedElement(activeElement)
            .build();

        var g2Active = mock(Graphics2D.class);
        LyricTextRenderer.getInstance().render(activeInv, ElementFrame.LINE_LEVEL, activeElement, g2Active);
        verifyNoInteractions(g2Active);

        var otherInv = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics)
            .setActivelyEditedElement(activeElement)
            .build();

        var g2Other = mock(Graphics2D.class);
        LyricTextRenderer.getInstance().render(otherInv, ElementFrame.LINE_LEVEL, otherElement, g2Other);
        verify(g2Other).drawString(anyString(), anyInt(), anyInt());
    }

    @Test
    void testSelectedLyricPaintsInSelectionColor() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.5, 1, "v1");
        var layoutResult = layoutBuilder().addLyricBox(element, box).build();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics())
            .setSelectionProvider(selectionProvider)
            .build();

        when(selectionProvider.isLyricSelected(element, 1, 0)).thenReturn(true);

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, element, g2);

        verify(g2).setColor(ScoreView.getSelectionColor());
        verify(g2).drawString("v1", toPx(2.0), toPx(LYRIC_BASELINE_SS));
    }

    @Test
    void testSelectedElementPaintsLyricInSelectionColor() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.5, 1, "v1");
        var layoutResult = layoutBuilder().addLyricBox(element, box).build();

        var builder = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(lyricRenderMetrics());
        RenderContextTestHelper.enableSelection(builder, 0);
        var invariants = builder.build();
        var frame = new ElementFrame(0, Double.NaN, -1, 0.0);

        var g2 = mock(Graphics2D.class);

        LyricTextRenderer.getInstance().render(invariants, frame, element, g2);

        verify(g2).setColor(ScoreView.getSelectionColor());
    }
}
