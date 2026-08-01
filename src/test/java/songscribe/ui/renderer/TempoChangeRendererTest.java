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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.layout.LayoutResult;

class TempoChangeRendererTest extends UnitTest {

    private static final TempoChangeRenderer RENDERER = TempoChangeRenderer.getInstance();

    // Arbitrary element X for the frame (no override needed since we supply a DecorationLayout)
    private static final double DECO_X_SS = 3.0;
    private static final double DECO_Y_SS = -5.0;
    private static final double DECO_WIDTH_SS = 10.0;
    private static final double DECO_HEIGHT_SS = 2.0;

    /**
     * Calls renderInitialTempo, captures all drawString calls on a spy g2, and returns the
     * list of strings drawn. The last entry is always the tempoBuilder content.
     */
    private List<String> renderAndCaptureStrings(Tempo tempo) {
        var note = ElementType.CROTCHET.newInstance();
        var attachment = new TempoChangeAttachment(note, tempo);

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
        RENDERER.renderInitialTempo(g2Spy, note, tempo, invariants, frame);

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
        var lastDrawn = drawn.get(drawn.size() - 1);

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
        assertThat(drawn.get(0)).isEqualTo(description);
    }

}
