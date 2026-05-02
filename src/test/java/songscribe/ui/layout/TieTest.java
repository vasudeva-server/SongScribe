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

class TieTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    private static Tie createTie() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return new Tie(anchor, end);
    }

    @Test
    void testContentHeightSsMatchesStylesheetConstant() {
        var tie = createTie();

        assertThat(tie.getContentHeightSs())
            .isEqualTo(Tie.TIE_ARC_HEIGHT_SS);
    }

    @Test
    void testContentHeightPxIsToPixelsOfSs() {
        var tie = createTie();
        var scale = ScaleContext.getInstance();

        assertThat(tie.getContentHeightPx())
            .isCloseTo(scale.toPixels(Tie.TIE_ARC_HEIGHT_SS),
                within(EPSILON));
    }
}
