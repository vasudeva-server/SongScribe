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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.MetronomeContent;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

/**
 * Covers the color decision in {@link BeatChangeRenderer#render}: a beat change is selectable in
 * its own right, so its color is keyed on the attachment rather than on the note it hangs off.
 */
class BeatChangeRendererTest extends UnitTest {

    private static final BeatChangeRenderer RENDERER = BeatChangeRenderer.getInstance();

    private static final double DECORATION_X_SS = 3.0;
    private static final double DECORATION_Y_SS = -5.0;
    private static final double DECORATION_WIDTH_SS = 10.0;
    private static final double DECORATION_HEIGHT_SS = 2.0;

    /**
     * Renders a beat change with its attachment reported as selected or not, and returns the
     * colors in force at every glyph it drew. A beat change draws several glyphs — the two
     * duration notes and the equals sign between them — and all of them must agree.
     */
    private static List<Color> renderedGlyphColors(boolean selected) {
        var line = detachedLine();
        var note = crotchet();
        line.addElement(note);

        var attachment = new BeatChangeAttachment(
            note, new BeatChange(Duration.CROTCHET, Duration.QUAVER));
        note.addAttachment(attachment);

        var font = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        var content = MetronomeContent.forBeatChange(attachment.getBeatChange(), font);
        var decorationLayout = new LayoutResult.DecorationLayout(
            DECORATION_X_SS, DECORATION_Y_SS, 0.0, DECORATION_WIDTH_SS, DECORATION_HEIGHT_SS, 0.0,
            content);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(attachment, decorationLayout)
            .build();

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Attachment(attachment), 0))
            .thenReturn(selected);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setCurrentLine(line)
            .setSelectionProvider(selectionProvider)
            .build();

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var drawnColors = new ArrayList<Color>();
        doAnswer(invocation -> {
            drawnColors.add(g2Spy.getColor());
            return null;
        }).when(g2Spy).drawString(anyString(), anyFloat(), anyFloat());

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL.withElement(0, Double.NaN), note, g2Spy);

        assertThat(drawnColors).isNotEmpty();
        return drawnColors;
    }

    @Test
    void testRenderSelectedBeatChangeDrawsInTheSelectionColor() {
        assertThat(renderedGlyphColors(true)).containsOnly(ScoreView.getSelectionColor());
    }

    @Test
    void testRenderUnselectedBeatChangeDrawsInTheElementColor() {
        assertThat(renderedGlyphColors(false)).containsOnly(RenderingUtils.ELEMENT_COLOR);
    }

}
