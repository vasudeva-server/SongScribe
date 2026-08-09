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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.layout.LayoutResult;

/**
 * Covers the one decision {@link SongTempoMarkRenderer#render} makes: whether there is anything
 * to draw at all.
 *
 * <p>{@code LineRenderer} hands the mark to this renderer for every paint of line 0 without
 * checking anything but the line index, so this guard is the only thing standing between a song
 * whose tempo renders as nothing and a null dereference on every repaint.
 */
class SongTempoMarkRendererTest extends UnitTest {

    private static final SongTempoMarkRenderer RENDERER = SongTempoMarkRenderer.getInstance();

    private static final double DECORATION_X_SS = 4.0;
    private static final double DECORATION_Y_SS = -6.0;
    private static final double DECORATION_WIDTH_SS = 12.0;
    private static final double DECORATION_HEIGHT_SS = 2.0;

    /**
     * Renders the song's tempo mark and returns every string it drew. When {@code stacked} is
     * false the mark is given no decoration layout, standing in for a line the stacker skipped.
     */
    private static List<String> renderedStrings(Song song, boolean stacked) {
        var line = detachedLine();
        var mark = song.getTempoMarkElement();
        var builder = LayoutResult.builder();

        if (stacked) {
            builder.putDecorationLayout(mark, new LayoutResult.DecorationLayout(
                DECORATION_X_SS, DECORATION_Y_SS, DECORATION_WIDTH_SS, DECORATION_HEIGHT_SS, 0.0));
        }

        var invariants = RenderContextTestHelper.newContext(song)
            .setLayoutResult(builder.build())
            .setCurrentLine(line)
            .build();

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var drawnStrings = new ArrayList<String>();
        doAnswer(invocation -> {
            drawnStrings.add(invocation.getArgument(0));
            return null;
        }).when(g2Spy).drawString(anyString(), anyFloat(), anyFloat());

        RENDERER.render(
            invariants, ElementFrame.LINE_LEVEL.withElement(0, Double.NaN), mark, g2Spy);

        return drawnStrings;
    }

    @Test
    void testAMarkThatWasNeverStackedDrawsNothing() {
        // A tempo with the metronome hidden and no description has zero content width, so the
        // stacker skips it and no decoration layout exists. Drawing anyway would dereference a
        // layout that is not there.
        assertThat(renderedStrings(new Song(), false))
            .as("a mark the stacker skipped must draw nothing")
            .isEmpty();
    }

    @Test
    void testAStackedMarkDrawsTheSongsCurrentTempo() {
        // The mark reads the tempo from the song at paint time rather than caching it, so a
        // tempo set after the mark existed must be the one that appears.
        var song = new Song();
        var bpm = 152;
        var description = "Allegro";
        song.setTempo(new Tempo(bpm, Tempo.DEFAULT_TYPE, description, true));

        assertThat(renderedStrings(song, true))
            .as("the mark must draw the song's current beats-per-minute and description")
            .anySatisfy(drawn -> assertThat(drawn).contains(String.valueOf(bpm), description));
    }
}
