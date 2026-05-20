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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.component.ScoreView;

class BaseElementRendererTest extends UnitTest {

    // T1: line == null → preview element color (treated as preview)
    @Test
    void testGetDecorationColorNullLineReturnsPreviewColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var ctx = RenderContextTestHelper.newContext(new Song());
        // currentLine is null by default

        var color = BaseElementRenderer.getDecorationColor(element, ctx);

        assertThat(color).isEqualTo(ScoreView.getPreviewElementColor());
    }

    // T2: element not in line (index < 0) → preview element color
    @Test
    void testGetDecorationColorElementNotInLineReturnsPreviewColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var line = mock(Line.class);
        when(line.getElementIndex(element)).thenReturn(-1);

        var ctx = RenderContextTestHelper.newContext(new Song());
        ctx.setCurrentLine(line);

        var color = BaseElementRenderer.getDecorationColor(element, ctx);

        assertThat(color).isEqualTo(ScoreView.getPreviewElementColor());
    }

    // T3: element in line (index >= 0) → ctx.getElementColor(index) result
    @Test
    void testGetDecorationColorElementInLineReturnsCtxColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var line = mock(Line.class);
        when(line.getElementIndex(element)).thenReturn(0);

        var ctx = RenderContextTestHelper.newContext(new Song());
        ctx.setEditMode(true);
        ctx.setCurrentLine(line);
        ctx.setPlayingNoteIndex(0);  // element at index 0 is playing

        var color = BaseElementRenderer.getDecorationColor(element, ctx);

        assertThat(color).isEqualTo(ScoreView.getPlayingNoteColor());
    }
}
