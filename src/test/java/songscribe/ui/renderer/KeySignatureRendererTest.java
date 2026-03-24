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
import static org.mockito.Mockito.verifyNoInteractions;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Composition;
import songscribe.music.KeyType;
import songscribe.ui.layout.KeySignature;

class KeySignatureRendererTest extends UnitTest {

    // T4: renderElement() is a no-op when hasAccidentals() is false (C major)
    @Test
    void testRenderIsNoOpForCMajor() {
        var g2 = mock(Graphics2D.class);
        var ctx = new ElementRenderContext(new Composition());
        var keySig = new KeySignature(KeyType.NONE, 0);

        KeySignatureRenderer.getInstance().render(keySig, g2, ctx);

        verifyNoInteractions(g2);
    }
}
