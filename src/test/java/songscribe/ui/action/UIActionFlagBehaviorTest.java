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

import songscribe.UnitTest;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.BaseDialog;
import songscribe.ui.edit.GraceModeManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class UIActionFlagBehaviorTest extends UnitTest {

    // -- OPENS_DIALOG flag enables/disables based on dialog visibility --

    @Test
    void testOpensDialogFlagDisablesActionWhenAnyDialogIsVisible() {
        try (var mainFrameMock = mockStatic(MainFrame.class);
             var ignored = mockStatic(BaseDialog.class)) {
            MockEnvHelper.setupMockEnv(mainFrameMock);
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(true);

            var action = createActionWithFlag(UIAction.Flag.OPENS_DIALOG);
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testOpensDialogFlagReenablesActionWhenAllDialogsClose() {
        try (var mainFrameMock = mockStatic(MainFrame.class);
             var ignored = mockStatic(BaseDialog.class)) {
            MockEnvHelper.setupMockEnv(mainFrameMock);
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(false);

            var action = createActionWithFlag(UIAction.Flag.OPENS_DIALOG);
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // -- action without OPENS_DIALOG is unaffected by dialog visibility --

    @Test
    void testActionWithoutOpensDialogFlagUnaffectedByDialogVisibility() {
        try (var mainFrameMock = mockStatic(MainFrame.class);
             var ignored = mockStatic(BaseDialog.class)) {
            MockEnvHelper.setupMockEnv(mainFrameMock);
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(true);

            var action = createActionWithFlag();
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // -- combined flags: OPENS_DIALOG + other disabling flags --

    @Test
    void testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses() {
        try (var mainFrameMock = mockStatic(MainFrame.class);
             var baseDialogMock = mockStatic(BaseDialog.class);
             var graceMock = mockStatic(GraceModeManager.class)) {
            MockEnvHelper.setupMockEnv(mainFrameMock);
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
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            MockEnvHelper.setupMockEnv(mainFrameMock);

            var action = new DialogOpenAction<>("Test Dialog", BaseDialog.class);

            assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isTrue();
        }
    }

    // -- helpers --

    private UIAction createActionWithFlag(UIAction.Flag... flags) {
        return new UIAction("Test", "test-cmd", flags);
    }
}
