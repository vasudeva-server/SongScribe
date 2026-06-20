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
package songscribe.ui.dialog;

import java.awt.Font;
import java.awt.Point;
import java.util.Collections;

import javax.swing.JDialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link FontDialog}: getData passes selectedFont to chooser,
 * setData harvests chooser font back into selectedFont, and showDialog returns
 * the initial font unchanged when the dialog is cancelled (setData not called).
 */
class FontDialogTest extends MainFrameMockTest {

    private static final int INITIAL_FONT_SIZE = 14;
    private static final int CHANGED_FONT_SIZE = 18;

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private FontDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        dialog = new FontDialog(mainFrame(), new Font(Font.SANS_SERIF, Font.PLAIN, INITIAL_FONT_SIZE));
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── Row 40: getData() — passes selectedFont to chooser.setSelectedFont ──

    @Test
    void testGetDataPassesSelectedFontToChooser() {
        dialog.getData();

        assertThat(dialog.chooser.getSelectedFont())
            .as("getData() sets chooser's selected font to the dialog's initial font")
            .isEqualTo(dialog.getSelectedFont());
    }

    // ── Row 41: setData() — harvests chooser.getSelectedFont() into selectedFont ──

    @Test
    void testSetDataHarvestsChooserFontIntoSelectedFont() {
        // First call getData() to populate the chooser with the initial font
        dialog.getData();

        // Change the chooser's font to something different
        var newFont = new Font(Font.MONOSPACED, Font.BOLD, CHANGED_FONT_SIZE);
        dialog.chooser.setSelectedFont(newFont);

        dialog.setData();

        assertThat(dialog.getSelectedFont())
            .as("setData() stores the chooser's current font into selectedFont")
            .isEqualTo(newFont);
    }

    // ── Row 42: showDialog — returns selectedFont unchanged when dialog is cancelled ──

    @Test
    void testShowDialogReturnsCancelledFont() {
        // Use a mock component to supply the initial font
        var initialFont = new Font(Font.SERIF, Font.ITALIC, INITIAL_FONT_SIZE);

        // mockConstruction(JDialog.class) prevents the real JDialog from blocking;
        // since no OK action is triggered, setData() is never called, so selectedFont
        // retains its initial value.
        try (var ignored = mockConstruction(JDialog.class,
                (d, ctx) -> BaseDialogTestHelper.configureMockDialog(d, new Point(0, 0)))) {
            var returned = FontDialog.showDialog(mainFrame(), initialFont);

            assertThat(returned)
                .as("showDialog returns the initial font unchanged when dialog is not confirmed")
                .isEqualTo(initialFont);
        }
    }
}
