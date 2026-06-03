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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.layout.StaffExtents;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;

class ScoreInputHandlerTest extends UnitTest {

    // -------------------------------------------------------------------
    // Row 62: mousePressed / mouseReleased is a no-op when !isPopupTrigger
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PopupTrigger {

        @Test
        void testMousePressedIsNoOpWhenPopupTriggerIsFalse() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var event = mouseEvent(MouseEvent.MOUSE_PRESSED, false);

            handler.mousePressed(event);

            verify(callback, never()).getEditPopup();
        }

        @Test
        void testMouseReleasedIsNoOpWhenPopupTriggerIsFalse() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var event = mouseEvent(MouseEvent.MOUSE_RELEASED, false);

            handler.mouseReleased(event);

            verify(callback, never()).getEditPopup();
        }
    }

    // -------------------------------------------------------------------
    // Row 65: keyPressed(ESCAPE) when grace mode is in progress delegates
    // to GraceModeManager.keyPressed
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EscapeKeyPressed {

        @Test
        void testKeyPressedEscapeDelegatesToGraceModeManagerWhenInProgress() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(true);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);

                var event = keyEvent(KeyEvent.VK_ESCAPE);
                handler.keyPressed(event);

                verify(graceModeManager).keyPressed(event);
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 66: keyReleased(ALT) sets altPressed=false via LineComponent
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AltKeyReleased {

        @Test
        void testKeyReleasedAltSetsAltPressedFalse() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);

            try (MockedStatic<LineComponent> lc = mockStatic(LineComponent.class)) {
                handler.keyReleased(keyEvent(KeyEvent.VK_ALT));

                lc.verify(() -> LineComponent.setAltPressed(false));
            }
        }
    }

    // -------------------------------------------------------------------
    // Rows 67-69: KeyAction.handlePitchAdjustment
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PitchAdjustment {

        @Test
        void testHandlePitchAdjustmentUpDecrementsStaffPosition() {
            var callback = mockEditKeyboardCallback();
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var note = ElementType.CROTCHET.newInstance();
            // default staffPosition = 0, well within upper bound of -10

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPreviewElement).thenReturn(note);

                var upAction = component.getActionMap().get(
                    component.getInputMap(JPanel.WHEN_FOCUSED).get(
                        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
                upAction.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
            }

            assertThat(note.getStaffPosition()).isEqualTo(-1);
        }

        @Test
        void testHandlePitchAdjustmentUpIsNoOpAtUpperBound() {
            var callback = mockEditKeyboardCallback();
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var note = ElementType.CROTCHET.newInstance();
            // upper bound: staffPosition < -(STAFF_LINES_ABOVE + 2) * 2 = -10
            final int upperBound = -(StaffExtents.STAFF_LINES_ABOVE + 2) * 2;
            note.setStaffPosition(upperBound - 1);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPreviewElement).thenReturn(note);

                var upAction = component.getActionMap().get(
                    component.getInputMap(JPanel.WHEN_FOCUSED).get(
                        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
                upAction.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
            }

            // Position should remain unchanged — already past the upper bound
            assertThat(note.getStaffPosition()).isEqualTo(upperBound - 1);
        }

        @Test
        void testHandlePitchAdjustmentDownIncrementsStaffPosition() {
            var callback = mockEditKeyboardCallback();
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var note = ElementType.CROTCHET.newInstance();
            // default staffPosition = 0, well within lower bound of 12

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPreviewElement).thenReturn(note);

                var downAction = component.getActionMap().get(
                    component.getInputMap(JPanel.WHEN_FOCUSED).get(
                        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
                downAction.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
            }

            assertThat(note.getStaffPosition()).isEqualTo(1);
        }

        @Test
        void testHandlePitchAdjustmentDownIsNoOpAtLowerBound() {
            var callback = mockEditKeyboardCallback();
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var note = ElementType.CROTCHET.newInstance();
            // lower bound: staffPosition > (STAFF_LINES_BELOW + 2) * 2 = 12
            final int lowerBound = (StaffExtents.STAFF_LINES_BELOW + 2) * 2;
            note.setStaffPosition(lowerBound + 1);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPreviewElement).thenReturn(note);

                var downAction = component.getActionMap().get(
                    component.getInputMap(JPanel.WHEN_FOCUSED).get(
                        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
                downAction.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
            }

            // Position should remain unchanged — already past the lower bound
            assertThat(note.getStaffPosition()).isEqualTo(lowerBound + 1);
        }

        @Test
        void testHandlePitchAdjustmentIsNoOpWhenModeIsNotEdit() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);
            when(callback.getControl()).thenReturn(Control.KEYBOARD);
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var note = ElementType.CROTCHET.newInstance();
            final int initialPosition = note.getStaffPosition();

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPreviewElement).thenReturn(note);

                var upAction = component.getActionMap().get(
                    component.getInputMap(JPanel.WHEN_FOCUSED).get(
                        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
                upAction.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
            }

            assertThat(note.getStaffPosition()).isEqualTo(initialPosition);
        }

        @Test
        void testHandlePitchAdjustmentIsNoOpWhenControlIsNotKeyboard() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.EDIT);
            when(callback.getControl()).thenReturn(Control.MOUSE);
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var note = ElementType.CROTCHET.newInstance();
            final int initialPosition = note.getStaffPosition();

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPreviewElement).thenReturn(note);

                var upAction = component.getActionMap().get(
                    component.getInputMap(JPanel.WHEN_FOCUSED).get(
                        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
                upAction.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
            }

            assertThat(note.getStaffPosition()).isEqualTo(initialPosition);
        }
    }

    // -------------------------------------------------------------------
    // Row 70: installKeyBindings registers one binding per key code
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InstallKeyBindings {

        @Test
        void testInstallKeyBindingsRegistersOneBindingPerKeyCode() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();

            var bindings = handler.installKeyBindings(component);

            final int expectedBindingCount = 7; // KEY_CODES.length in ScoreInputHandler
            assertThat(bindings).hasSize(expectedBindingCount);

            var inputMap = component.getInputMap(JPanel.WHEN_FOCUSED);
            var actionMap = component.getActionMap();

            for (var entry : bindings.entrySet()) {
                KeyStroke keystroke = entry.getKey();
                Object actionKey = entry.getValue();

                assertThat(inputMap.get(keystroke)).isEqualTo(actionKey);
                assertThat(actionMap.get(actionKey)).isNotNull();
            }
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private MouseEvent mouseEvent(int id, boolean popupTrigger) {
        return new MouseEvent(
            mock(Component.class), id, 0L, 0, 0, 0, 0, 0, 1, popupTrigger, MouseEvent.BUTTON1
        );
    }

    private KeyEvent keyEvent(int keyCode) {
        return new KeyEvent(
            mock(Component.class), KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED
        );
    }

    private InputHandlerCallback mockEditKeyboardCallback() {
        var callback = mock(InputHandlerCallback.class);
        when(callback.getMode()).thenReturn(Mode.EDIT);
        when(callback.getControl()).thenReturn(Control.KEYBOARD);
        return callback;
    }
}
