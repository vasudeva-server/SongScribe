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

package songscribe.ui.action;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.dialog.BaseDialog;
import songscribe.ui.edit.GraceModeManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class UIActionFlagBehaviorTest extends MainFrameMockTest {

    // -- OPENS_DIALOG flag enables/disables based on dialog visibility --

    @Test
    void testOpensDialogFlagDisablesActionWhenAnyDialogIsVisible() {
        try (var ignored = mockStatic(BaseDialog.class)) {
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(true);

            var action = createActionWithFlag(UIAction.Flag.OPENS_DIALOG);
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testOpensDialogFlagReenablesActionWhenAllDialogsClose() {
        try (var ignored = mockStatic(BaseDialog.class)) {
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(false);

            var action = createActionWithFlag(UIAction.Flag.OPENS_DIALOG);
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // -- action without OPENS_DIALOG is unaffected by dialog visibility --

    @Test
    void testActionWithoutOpensDialogFlagUnaffectedByDialogVisibility() {
        try (var ignored = mockStatic(BaseDialog.class)) {
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(true);

            var action = createActionWithFlag();
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // -- combined flags: OPENS_DIALOG + other disabling flags --

    @Test
    void testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses() {
        try (var baseDialogMock = mockStatic(BaseDialog.class);
             var graceMock = mockStatic(GraceModeManager.class)) {
            baseDialogMock.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(false);
            graceMock.when(GraceModeManager::isActive).thenReturn(true);

            var action = createActionWithFlag(
                UIAction.Flag.OPENS_DIALOG,
                UIAction.Flag.DISABLE_IN_GRACE_MODE
            );
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }
    }

    // -- DialogOpenAction does NOT auto-set OPENS_DIALOG --

    @Test
    void testDialogOpenActionAutoSetsOpensDialogFlag() {
        var action = new DialogOpenAction<>(mainFrame(), "Test Dialog", BaseDialog.class);

        assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isTrue();
    }

    // -- null mainFrame is rejected --

    @Test
    @SuppressWarnings("NullAway")
    void testConstructorRejectsNullMainFrame() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new UIAction(null, "Test", null, 0, "test", "Test")
        );
    }

    // -- helpers --

    private UIAction createActionWithFlag(UIAction.Flag... flags) {
        return new UIAction(mainFrame(), "Test", "test-cmd", flags);
    }
}
