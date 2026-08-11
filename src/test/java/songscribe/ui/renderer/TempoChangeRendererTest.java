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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.MetronomeContent;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class TempoChangeRendererTest extends UnitTest {

    private static final TempoChangeRenderer RENDERER = TempoChangeRenderer.getInstance();

    // Arbitrary element X for the frame (no override needed since we supply a DecorationLayout)
    private static final double DECO_X_SS = 3.0;
    private static final double DECO_Y_SS = -5.0;
    private static final double DECO_WIDTH_SS = 10.0;
    private static final double DECO_HEIGHT_SS = 2.0;

    /**
     * Renders {@code tempo}, captures all drawString calls on a spy g2, and returns the
     * list of strings drawn. The last entry is always the tempoBuilder content.
     */
    private List<String> renderAndCaptureStrings(Tempo tempo) {
        var note = crotchet();
        var attachment = new TempoChangeAttachment(note, tempo);
        note.addAttachment(attachment);

        var font = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        var content = MetronomeContent.forTempo(tempo, font);
        var decorationLayout = new LayoutResult.DecorationLayout(
            DECO_X_SS, DECO_Y_SS, 0.0, DECO_WIDTH_SS, DECO_HEIGHT_SS, 0.0, content);

        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(attachment, decorationLayout)
            .build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .build();
        var frame = ElementFrame.LINE_LEVEL.withElement(0, Double.NaN);

        var g2Spy = spy(RenderContextTestHelper.realG2());
        RENDERER.render(invariants, frame, note, g2Spy);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(g2Spy, atLeastOnce()).drawString(captor.capture(), anyFloat(), anyFloat());

        return captor.getAllValues();
    }

    // ==========================================================================
    // renderTempoChange — StringBuilder content branches (row 36)
    // ==========================================================================

    @Test
    void testRenderTempoChangeWithShowTempoIncludesVisibleTempoAndDescription() {
        // showTempo=true: tempoBuilder builds "{visibleTempo} {description}"
        // drawDurationEquals() is called before the text drawString, so there
        // will be multiple drawString calls; the last one contains the tempoBuilder content.
        final var visibleTempo = 120;
        final var description = "Allegro";
        var tempo = new Tempo(visibleTempo, Duration.CROTCHET, description, true);

        var drawn = renderAndCaptureStrings(tempo);

        // The final drawString call contains the StringBuilder content.
        var lastDrawn = drawn.getLast();

        assertThat(lastDrawn).contains(String.valueOf(visibleTempo));
        assertThat(lastDrawn).contains(description);
    }

    @Test
    void testRenderTempoChangeWithoutShowTempoDrawsOnlyDescription() {
        // showTempo=false: tempoBuilder builds only "{description}";
        // drawDurationEquals() is NOT called, so exactly 1 drawString call.
        final var description = "Andante";
        var tempo = new Tempo(0, Duration.CROTCHET, description, false);

        var drawn = renderAndCaptureStrings(tempo);

        assertThat(drawn).hasSize(1);
        assertThat(drawn.getFirst()).isEqualTo(description);
    }


    // ==========================================================================
    // Selection color
    // ==========================================================================

    /**
     * Renders a tempo change with the attachment reported as selected or not, and returns the
     * color in force when its description was drawn. {@code showTempo} is off so exactly one
     * string is drawn and there is no ambiguity about which color is being read.
     */
    private static Color renderedTempoColor(boolean selected) {
        final var description = "Andante";
        var line = detachedLine();
        var note = crotchet();
        line.addElement(note);

        var tempo = new Tempo(0, Duration.CROTCHET, description, false);
        var attachment = new TempoChangeAttachment(note, tempo);
        note.addAttachment(attachment);

        var font = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        var content = MetronomeContent.forTempo(tempo, font);
        var decorationLayout = new LayoutResult.DecorationLayout(
            DECO_X_SS, DECO_Y_SS, 0.0, DECO_WIDTH_SS, DECO_HEIGHT_SS, 0.0, content);
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
        }).when(g2Spy).drawString(eq(description), anyFloat(), anyFloat());

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL.withElement(0, Double.NaN), note, g2Spy);

        assertThat(drawnColors).hasSize(1);
        return drawnColors.getFirst();
    }

    @Test
    void testRenderTempoChangeSelectedDrawsInTheSelectionColor() {
        assertThat(renderedTempoColor(true)).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testRenderTempoChangeUnselectedDrawsInTheElementColor() {
        assertThat(renderedTempoColor(false)).isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }

    @Test
    void testRenderWithNullContentThrows() {
        // A DecorationLayout present but carrying no MetronomeContent is a layout bug: the
        // renderer must surface it rather than silently drawing nothing. This covers the
        // shared MetronomeRenderer.renderAttachment path, which BeatChangeRenderer uses too.
        var note = crotchet();
        var tempo = new Tempo(120, Duration.CROTCHET, "Allegro", true);
        var attachment = new TempoChangeAttachment(note, tempo);
        note.addAttachment(attachment);

        var decorationLayout = new LayoutResult.DecorationLayout(
            DECO_X_SS, DECO_Y_SS, DECO_WIDTH_SS, DECO_HEIGHT_SS, 0.0);

        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(attachment, decorationLayout)
            .build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .build();
        var frame = ElementFrame.LINE_LEVEL.withElement(0, Double.NaN);

        var g2Spy = spy(RenderContextTestHelper.realG2());

        assertThatThrownBy(() -> RENDERER.render(invariants, frame, note, g2Spy))
            .isInstanceOf(IllegalStateException.class);
    }

}
