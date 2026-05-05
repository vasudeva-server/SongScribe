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

package songscribe.smufl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class EngravingTest extends UnitTest {

    // T6: G_CLEF_WIDTH_SS is derived from the SMuFL advance width, not hardcoded
    @Test
    void testGClefWidthMatchesSmuflAdvanceWidth() {
        var expected = SMuFLMetadata.getInstance().requireAdvanceWidth(SMuFLGlyph.G_CLEF);
        assertThat(Engraving.G_CLEF_WIDTH_SS).isEqualTo(expected);
    }
}
