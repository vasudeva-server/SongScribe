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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.MockEnvHelper;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;

class BarlineMenuTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private Song song;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        var env = MockEnvHelper.setupMockEnv(mainFrameMock);

        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(env.frame().getRootPane()).thenReturn(mockRootPane);

        song = new Song();
        when(env.score().isInitialized()).thenReturn(true);
        when(env.score().getSong()).thenReturn(song);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    @Test
    void testFinalDoubleBarlineItemReplacesTerminalWithoutConfirm() {
        var menu = new BarlineMenu();
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        var finalBarlineItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE));

        try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
            finalBarlineItem.getAction().actionPerformed(
                new ActionEvent(finalBarlineItem, ActionEvent.ACTION_PERFORMED, "final-double-barline")
            );

            optionDialogsMock.verifyNoInteractions();
        }

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    @Test
    void testFinalRightRepeatItemReplacesTerminalWithoutConfirm() {
        var menu = new BarlineMenu();
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
        var menu = new BarlineMenu();
        var finalBarlineItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE));
        var rightRepeatItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT));

        ((UIAction) finalBarlineItem.getAction()).updateEnabledState();
        ((UIAction) rightRepeatItem.getAction()).updateEnabledState();

        assertThat(finalBarlineItem.isSelected()).isTrue();
        assertThat(rightRepeatItem.isSelected()).isFalse();
    }

    @Test
    void testRadioSelectionReflectsCurrentTerminalForRightRepeat() {
        var menu = new BarlineMenu();
        var finalBarlineItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE));
        var rightRepeatItem = findMenuItemByText(menu, Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT));

        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        assertThat(finalBarlineItem.isSelected()).isFalse();
        assertThat(rightRepeatItem.isSelected()).isTrue();
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
