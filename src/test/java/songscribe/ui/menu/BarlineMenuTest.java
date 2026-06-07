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

package songscribe.ui.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;

class BarlineMenuTest extends MainFrameMockTest {

    private Song song;

    @BeforeEach
    void setUp() {
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);

        song = new Song();
        when(mockEnv().score().isInitialized()).thenReturn(true);
        when(mockEnv().score().getSong()).thenReturn(song);
    }

    @Test
    void testFinalRightRepeatItemReplacesTerminalWithoutConfirm() {
        var menu = new BarlineMenu(mainFrame());
        var rightRepeatItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT));

        try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
            rightRepeatItem.getAction().actionPerformed(
                new ActionEvent(rightRepeatItem, ActionEvent.ACTION_PERFORMED, "final-right-repeat")
            );

            optionDialogsMock.verifyNoInteractions();
        }

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.REPEAT_RIGHT);
    }

    @Test
    void testRadioSelectionReflectsCurrentTerminalForFinalBarline() {
        var menu = new BarlineMenu(mainFrame());
        var finalBarlineItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE));
        var rightRepeatItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT));

        ((UIAction) finalBarlineItem.getAction()).updateEnabledState();
        ((UIAction) rightRepeatItem.getAction()).updateEnabledState();

        assertThat(finalBarlineItem.isSelected()).isTrue();
        assertThat(rightRepeatItem.isSelected()).isFalse();
    }

    @Test
    void testRadioSelectionReflectsCurrentTerminalForRightRepeat() {
        var menu = new BarlineMenu(mainFrame());
        var finalBarlineItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE));
        var rightRepeatItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT));

        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        assertThat(finalBarlineItem.isSelected()).isFalse();
        assertThat(rightRepeatItem.isSelected()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Terminal items share a ButtonGroup — selecting one deselects the other
    // -------------------------------------------------------------------------

    @Test
    void testTerminalItemsAreMutuallyExclusive() {
        var menu = new BarlineMenu(mainFrame());
        var finalBarlineItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE));
        var rightRepeatItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT));

        // Pre-select right-repeat so the first transition is non-trivial.
        rightRepeatItem.setSelected(true);

        // Select the final-double-barline item; the right-repeat must deselect.
        finalBarlineItem.setSelected(true);
        assertThat(rightRepeatItem.isSelected()).isFalse();

        // Now select the right-repeat item; the final-double-barline must deselect.
        rightRepeatItem.setSelected(true);
        assertThat(finalBarlineItem.isSelected()).isFalse();
    }

    private JRadioButtonMenuItem findMenuItemByText(BarlineMenu menu, String text) {
        for (var i = 0; i < menu.getItemCount(); i++) {
            var item = menu.getItem(i);

            if (item instanceof JRadioButtonMenuItem radio && text.equals(radio.getText())) {
                return radio;
            }
        }

        throw new IllegalArgumentException("Menu item not found: " + text);
    }
}
