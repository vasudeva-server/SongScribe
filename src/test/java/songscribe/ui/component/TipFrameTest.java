/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

/**
 * Unit tests for {@link TipFrame} covering:
 * <ul>
 *   <li>Row 55 — {@code showTip} reads sequentially and wraps index to 0
 *       when the buffer is empty (end of file)</li>
 *   <li>Row 56 — Previous-button handler decrements {@code index} by 2 before
 *       calling {@code showTip}; guards index > 1</li>
 *   <li>Row 57 — {@code closeWindow} persists the "show tips" checkbox state
 *       to {@code Prefs}</li>
 * </ul>
 *
 * <p>The test constructor {@code TipFrame(File)} is used to avoid the full
 * Swing layout, the initial {@code showTip()} call, and {@code setVisible(true)}.
 */
class TipFrameTest extends UnitTest {

    // The fixture file has exactly two tips (two non-empty lines):
    //   Line 0: "First tip line"
    //   Line 1: "Second tip line"
    private static final String FIRST_TIP = "First tip line";
    private static final String SECOND_TIP = "Second tip line";

    private File tipsFile;
    private TipFrame frame;

    @BeforeEach
    void setUp() throws URISyntaxException {
        var url = getClass().getClassLoader().getResource("tips-test");
        assertThat(url).as("tips-test fixture must be on the classpath").isNotNull();
        tipsFile = new File(url.toURI());
        frame = new TipFrame(tipsFile);
    }

    @AfterEach
    void tearDown() {
        Prefs.reset(PrefsKey.SHOW_TIPS);
        Prefs.reset(PrefsKey.TIP_INDEX);
        frame.dispose();
    }

    // -----------------------------------------------------------------------
    // Row 55: showTip reads sequentially and wraps to index 0 at end of file
    // -----------------------------------------------------------------------

    @Test
    void testShowTipReadsFirstTipAtIndexZero() throws IOException {
        frame.index = 0;
        frame.showTip();

        assertThat(frame.tipPane.getText()).isEqualTo(FIRST_TIP + "\n");
        assertThat(frame.index).isEqualTo(1);
    }

    @Test
    void testShowTipReadsSecondTipAtIndexOne() throws IOException {
        frame.index = 1;
        frame.showTip();

        assertThat(frame.tipPane.getText()).isEqualTo(SECOND_TIP + "\n");
        assertThat(frame.index).isEqualTo(2);
    }

    @Test
    void testShowTipWrapsIndexToZeroWhenPastEndOfFile() throws IOException {
        // index == 2 is past the last tip (the file has only tips at 0 and 1),
        // so tipBuffer will be empty and showTip must recurse with index = 0.
        frame.index = 2;
        frame.showTip();

        // After wrapping, we expect the first tip and index == 1.
        assertThat(frame.tipPane.getText()).isEqualTo(FIRST_TIP + "\n");
        assertThat(frame.index).isEqualTo(1);
    }

    @Test
    void testShowTipAdvancesIndexOnEachSuccessiveCall() throws IOException {
        frame.index = 0;
        frame.showTip();
        assertThat(frame.index).isEqualTo(1);

        frame.showTip();
        assertThat(frame.index).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Row 56: goToPreviousTip decrements index by 2 and guards index > 1
    // -----------------------------------------------------------------------

    @Test
    void testGoToPreviousTipDecrementsByTwoWhenIndexIsGreaterThanOne() throws IOException {
        // Set index to 2 (pointing past last tip, so previous should go back to index 0,
        // then showTip increments to 1, but the decrement by 2 from index=2 gives index=0).
        frame.index = 2;
        frame.goToPreviousTip();

        // index was 2, decremented by 2 → 0, then showTip reads tip 0 and increments to 1.
        assertThat(frame.tipPane.getText()).isEqualTo(FIRST_TIP + "\n");
        assertThat(frame.index).isEqualTo(1);
    }

    @Test
    void testGoToPreviousTipIsNoOpWhenIndexIsOne() {
        // index <= 1 means there is no previous tip to go back to.
        frame.index = 1;
        frame.goToPreviousTip();

        // No showTip() called — index unchanged and tipPane still empty.
        assertThat(frame.index).isEqualTo(1);
        assertThat(frame.tipPane.getText()).isEmpty();
    }

    @Test
    void testGoToPreviousTipIsNoOpWhenIndexIsZero() {
        frame.index = 0;
        frame.goToPreviousTip();

        assertThat(frame.index).isEqualTo(0);
        assertThat(frame.tipPane.getText()).isEmpty();
    }

    @Test
    void testGoToPreviousTipAtIndexThreeGoesBackToSecondTip() throws IOException {
        // index = 3 (has read all tips and then wrapped once already)
        // Decrement by 2 → index = 1, then showTip reads tip at index 1 (second tip),
        // incrementing index to 2.
        frame.index = 3;
        frame.goToPreviousTip();

        assertThat(frame.tipPane.getText()).isEqualTo(SECOND_TIP + "\n");
        assertThat(frame.index).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Row 57: closeWindow persists showTip checkbox state to Prefs
    // -----------------------------------------------------------------------

    @Test
    void testCloseWindowPersistsShowTipCheckedStateToPrefs() {
        frame.showTip.setSelected(true);
        frame.closeWindow();

        assertThat(Prefs.getBoolean(PrefsKey.SHOW_TIPS)).isTrue();
    }

    @Test
    void testCloseWindowPersistsShowTipUncheckedStateToPrefs() {
        frame.showTip.setSelected(false);
        frame.closeWindow();

        assertThat(Prefs.getBoolean(PrefsKey.SHOW_TIPS)).isFalse();
    }
}
