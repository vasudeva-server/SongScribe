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

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.music.Line;
import songscribe.music.TieSpan;
import songscribe.ui.component.Score;

class ElementRenderContextTest extends UnitTest {

    // T1: not in edit mode → Color.BLACK regardless of playing/selection state
    @Test
    void testNotEditModeReturnsBlack() {
        var ctx = new ElementRenderContext(new Song());
        // editMode defaults to false

        assertThat(ctx.getElementColor(0)).isEqualTo(Color.BLACK);
    }

    // T2: edit mode + element is playing → Score.getPlayingNoteColor()
    @Test
    void testPlayingElementReturnsPlayingColor() {
        var ctx = new ElementRenderContext(new Song());
        ctx.setEditMode(true);
        ctx.setPlayingNoteIndex(0);

        assertThat(ctx.getElementColor(0)).isEqualTo(Score.getPlayingNoteColor());
    }

    // T3: edit mode + element is in a tie with the playing note → Score.getPlayingNoteColor()
    @Test
    void testElementInPlayingTieReturnsPlayingColor() {
        var line = detachedLine();
        line.getTies().addSpan(new TieSpan(0, 2));

        var ctx = new ElementRenderContext(new Song());
        ctx.setEditMode(true);
        ctx.setCurrentLine(line);
        ctx.setPlayingNoteIndex(0);

        // Index 2 is in the [0, 2] tie but is not the playing note itself
        assertThat(ctx.getElementColor(2)).isEqualTo(Score.getPlayingNoteColor());
    }

    // T4: edit mode + element is selected → selectionColor
    @Test
    void testSelectedElementReturnsSelectionColor() {
        var ctx = new ElementRenderContext(new Song());
        RenderContextTestHelper.enableSelection(ctx, 0);
        ctx.setSelectionColor(Color.RED);

        assertThat(ctx.getElementColor(0)).isEqualTo(Color.RED);
    }

    // T5: edit mode, not playing, not selected → Color.BLACK
    @Test
    void testDefaultReturnsBlack() {
        var ctx = new ElementRenderContext(new Song());
        ctx.setEditMode(true);

        assertThat(ctx.getElementColor(0)).isEqualTo(Color.BLACK);
    }
}
