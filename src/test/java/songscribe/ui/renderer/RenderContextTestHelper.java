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

import songscribe.font.DocumentFonts;
import songscribe.dom.Song;
import songscribe.ui.component.score.LineComponent;

/**
 * Shared setup for renderer tests that need an {@link ElementRenderContext}
 * configured for selection-aware coloring.
 */
final class RenderContextTestHelper {

    private RenderContextTestHelper() {}

    /** Builds a context for the given song with fonts seeded from prefs. */
    static ElementRenderContext newContext(Song song) {
        return new ElementRenderContext(song, DocumentFonts.defaultsFromPrefs());
    }

    /**
     * Puts {@code ctx} into edit mode and installs a mock {@link LineComponent.SelectionProvider}
     * that reports {@code selectedElementIndex} on line 0 as selected. Returns the mock so
     * callers can stub additional behavior.
     */
    static void enableSelection(ElementRenderContext ctx, int selectedElementIndex) {
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isElementSelected(selectedElementIndex, 0)).thenReturn(true);
        ctx.setEditMode(true);
        ctx.setSelectionProvider(selectionProvider);
    }
}
