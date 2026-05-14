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

package songscribe.ui.layout;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TupletTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    private static Tuplet createTuplet() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return new Tuplet(anchor, end, 3);
    }

    @Test
    void testContentHeightSsMatchesStylesheetConstant() {
        var tuplet = createTuplet();

        assertThat(tuplet.getContentHeightSs())
            .isEqualTo(Tuplet.TUPLET_BRACKET_HEIGHT_SS);
    }

    @Test
    void testContentHeightPxIsToPixelsOfSs() {
        var tuplet = createTuplet();
        var scale = ScaleContext.getInstance();

        assertThat(tuplet.getContentHeightPx())
            .isCloseTo(scale.ssToPx(Tuplet.TUPLET_BRACKET_HEIGHT_SS),
                within(EPSILON));
    }
}
