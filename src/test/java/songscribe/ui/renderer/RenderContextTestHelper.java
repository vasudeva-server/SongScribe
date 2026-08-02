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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import module java.desktop;

import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.hit.HitTarget;
import songscribe.font.FontKey;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.component.score.LineComponent;

/**
 * Shared setup for renderer tests that need a {@link LineInvariants}
 * configured for selection-aware coloring.
 */
final class RenderContextTestHelper {

    private RenderContextTestHelper() {}

    /**
     * Creates a real {@link Graphics2D} backed by a headless buffered image so
     * that transform, font, color, and drawing operations inside renderers do not
     * throw. The image dimensions are large enough for any renderer test.
     */
    static Graphics2D realG2() {
        var img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    /**
     * Returns a builder seeded with placeholder layout fields so callers only need to
     * configure the state they care about. Tests that exercise lyric layout should
     * override the layout setters with real values.
     */
    static LineInvariants.Builder newContext(Song song) {
        var fonts = DocumentFonts.defaultFonts();
        var lyricFont = fonts.getFont(FontKey.LYRICS);
        return LineInvariants.builder(song, fonts)
            .setLayoutResult(LayoutResult.builder().build())
            .setLyricRenderMetrics(new LyricRenderMetrics(lyricFont, lyricFont, 0, 0, 0));
    }

    /**
     * Installs a mock {@link LineComponent.SelectionProvider} reporting the element at
     * {@code selectedElementIndex} of {@code line} as selected on line 0, and makes
     * {@code line} the builder's current line.
     * <p>
     * Both selection queries are stubbed because a note can be asked about either way, and a
     * real selection answers both: by index, which is how a drawn element asks, and by
     * {@link songscribe.hit.HitTarget}, which is how a caller holding only the element asks.
     * Stubbing one alone would make the mock report a state the real coordinator cannot be in.
     * <p>
     * The line is required so the index can be mapped to the element sitting at it.
     */
    static void enableSelection(LineInvariants.Builder builder, Line line, int selectedElementIndex) {
        enableRangeSelection(builder, line, selectedElementIndex, selectedElementIndex);
    }

    /**
     * The multi-element form of {@link #enableSelection}: reports every element in
     * {@code beginIndex..endIndex} as selected on line 0, and makes {@code line} the builder's
     * current line.
     * <p>
     * Takes a contiguous range rather than a set of indices because that is the only shape a
     * real selection has. A mock that reported scattered elements as selected would put the
     * code under test in a state the coordinator cannot produce.
     */
    static void enableRangeSelection(
        LineInvariants.Builder builder, Line line, int beginIndex, int endIndex) {

        var selectionProvider = mock(LineComponent.SelectionProvider.class);

        for (var i = beginIndex; i <= endIndex; i++) {
            when(selectionProvider.isSelected(new HitTarget.Element(line.getElement(i)), 0))
                .thenReturn(true);
            when(selectionProvider.isElementRangeSelected(i, 0)).thenReturn(true);
        }

        builder.setCurrentLine(line).setSelectionProvider(selectionProvider);
    }
}
