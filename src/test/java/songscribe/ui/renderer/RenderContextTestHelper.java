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
     * The line is required because an element's color is resolved by naming the element as a
     * {@link songscribe.hit.HitTarget}, so the index has to be mapped back to the element
     * sitting at it.
     */
    static void enableSelection(LineInvariants.Builder builder, Line line, int selectedElementIndex) {
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(
            new HitTarget.Element(line.getElement(selectedElementIndex)), 0)).thenReturn(true);
        builder.setCurrentLine(line).setSelectionProvider(selectionProvider);
    }
}
