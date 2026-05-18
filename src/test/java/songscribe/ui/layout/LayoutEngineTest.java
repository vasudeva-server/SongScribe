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
import songscribe.font.DocumentFonts;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.smufl.Engraving;

@SuppressWarnings("DataFlowIssue")
class LayoutEngineTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double CLEF_X_POSITION_SS = 0.625;
    private static final double TOLERANCE = 0.001;

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var hyphenWidthSs = ScaleContext.textWidthSs(lyricsFont, "-");
        var spaceWidthSs = ScaleContext.textWidthSs(lyricsFont, " ");
        var metrics = new LyricRenderMetrics(
            lyricsFont, ScaleContext.scaleFont(lyricsFont), hyphenWidthSs, spaceWidthSs);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultsFromPrefs());
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
        var result = require(engine().layout(detachedLine()), "LayoutResult");
        var clef = require(result.getClef(), "Clef");

        assertThat(clef.getXSs()).isCloseTo(CLEF_X_POSITION_SS, within(TOLERANCE));
    }

    // T2: layout() stores a KeySignature immediately after the clef with correct key data
    @Test
    void testLayoutStoresKeySignatureAfterClef() {
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(3);

        var result = require(engine().layout(line), "LayoutResult");
        var keySig = require(result.getKeySignature(), "KeySignature");

        var expectedXSs = CLEF_X_POSITION_SS
            + Engraving.G_CLEF_WIDTH_SS
            + new Clef().getMarginRightSs();

        assertThat(keySig.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
        assertThat(keySig.getKeyType()).isEqualTo(KeyType.SHARPS);
        assertThat(keySig.getAccidentalCount()).isEqualTo(3);
    }

    // T3: On the last line, the final barline is positioned flush-right
    @Test
    void testFinalBarlineFlushRightOnLastLine() {
        var song = new Song();
        var line = song.getLine(0);
        var finalBarline = line.getElement(line.elementCount() - 1);

        var result = require(engine().layout(line, true), "LayoutResult");
        var expectedXSs = ElementType.terminalFlushRightXSs(STAFF_RIGHT_MARGIN_SS, ElementType.FINAL_DOUBLE_BARLINE);

        assertThat(result.getElementXSs(finalBarline)).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // T3b: On the last line with a REPEAT_RIGHT terminal, the terminal is flush-right
    @Test
    void testRightRepeatTerminalFlushRightOnLastLine() {
        var song = new Song();
        song.replaceTerminal(ElementType.REPEAT_RIGHT);
        var line = song.getLine(0);
        var terminal = line.getElement(line.elementCount() - 1);

        assertThat(terminal.getType()).isEqualTo(ElementType.REPEAT_RIGHT);

        var result = require(engine().layout(line, true), "LayoutResult");
        var expectedXSs = ElementType.terminalFlushRightXSs(STAFF_RIGHT_MARGIN_SS, ElementType.REPEAT_RIGHT);

        assertThat(result.getElementXSs(terminal)).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // T4: On a non-last line, the final barline is not flush-right
    @Test
    void testFinalBarlineNotFlushRightOnNonLastLine() {
        var song = new Song();
        var line = song.getLine(0);
        var finalBarline = line.getElement(line.elementCount() - 1);

        var result = require(engine().layout(line, false), "LayoutResult");
        var flushRightXSs = ElementType.terminalFlushRightXSs(STAFF_RIGHT_MARGIN_SS, ElementType.FINAL_DOUBLE_BARLINE);

        assertThat(result.getElementXSs(finalBarline)).isNotCloseTo(flushRightXSs, within(TOLERANCE));
    }
}
