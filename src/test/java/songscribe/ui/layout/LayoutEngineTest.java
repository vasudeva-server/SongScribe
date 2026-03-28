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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.KeyType;
import songscribe.music.Line;

class LayoutEngineTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 0.001;

    private static LayoutEngine engine() {
        var g2 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        return new LayoutEngine(g2, new Font("Dialog", Font.PLAIN, 12), STAFF_RIGHT_MARGIN_SS);
    }

    /** Asserts value is not null and returns it non-null for NullAway. */
    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // T1: layout() stores a Clef at CLEF_X_POSITION_SS
    @Test
    void testLayoutStoresClefAtStandardPosition() {
        var result = require(engine().layout(new Line()), "LayoutResult");
        var clef = require(result.getClef(), "Clef");

        assertThat(clef.getXSs()).isCloseTo(LayoutStylesheet.CLEF_X_POSITION_SS, within(TOLERANCE));
    }

    // T2: layout() stores a KeySignature immediately after the clef with correct key data
    @Test
    void testLayoutStoresKeySignatureAfterClef() {
        var line = new Line();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(3);

        var result = require(engine().layout(line), "LayoutResult");
        var keySig = require(result.getKeySignature(), "KeySignature");

        double expectedXSs = LayoutStylesheet.CLEF_X_POSITION_SS
            + LayoutStylesheet.CLEF_WIDTH_SS
            + new Clef().getMarginRightSs();

        assertThat(keySig.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
        assertThat(keySig.getKeyType()).isEqualTo(KeyType.SHARPS);
        assertThat(keySig.getAccidentalCount()).isEqualTo(3);
    }
}
