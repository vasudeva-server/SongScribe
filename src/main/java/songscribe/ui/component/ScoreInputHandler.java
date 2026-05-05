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

import module java.desktop;

import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import songscribe.message.MessageCenter;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.StaffExtents;
import songscribe.message.command.DeselectCommand;
import songscribe.util.UIUtils;

/**
 * Handles mouse and keyboard input for the Score component.
 * <p>
 * Manages popup triggers, focus, and playback guards.
 * Selection handling (click-to-select, drag-to-select, Alt-switch) is
 * handled by {@link LineComponent}.
 */
public final class ScoreInputHandler extends KeyAdapter
    implements MouseListener, MouseMotionListener {

    private final InputHandlerCallback callback;
    private final EditModeManager editModeManager;

    public ScoreInputHandler(
        InputHandlerCallback callback,
        EditModeManager editModeManager
    ) {
        this.callback = callback;
        this.editModeManager = editModeManager;
    }

    //***************************
    // MouseListener methods
    //***************************
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        callback.requestFocusInWindow();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            var popup = callback.getEditPopup();

            if (popup != null) {
                popup.show((Component) e.getSource(), e.getX(), e.getY());
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            var popup = callback.getEditPopup();

            if (popup != null) {
                popup.show((Component) e.getSource(), e.getX(), e.getY());
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (
            !editModeManager.isPreviewElementVisible() &&
                (callback.getControl() == Control.MOUSE) &&
                (callback.getMode() == Mode.EDIT)
        ) {
            editModeManager.setPreviewElementVisible(true);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (
            editModeManager.isPreviewElementVisible() &&
                (callback.getControl() == Control.MOUSE) &&
                (callback.getMode() == Mode.EDIT)
        ) {
            editModeManager.setPreviewElementVisible(false);
            callback.repaint();
        }
    }

    //******************************
    // MouseMotionListener methods
    //******************************
    @Override
    public void mouseDragged(MouseEvent e) {
        // Interface requires mouseDragged to be implemented
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Interface requires mouseMoved to be implemented
    }

    //***********************
    // KeyListener methods
    //***********************
    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            LineComponent.clearPreviewElement();
            LineComponent.setAltPressed(true);
            callback.repaint();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (editModeManager.getGraceModeManager().isInProgress()) {
                editModeManager.getGraceModeManager().keyPressed(e);
            } else if (callback.getMode() == Mode.SELECT && !UIUtils.isEditingText()) {
                MessageCenter.post(new DeselectCommand());
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            LineComponent.setAltPressed(false);
        }
    }

    //***********************
    // Helper methods
    //***********************

    private static final int[] KEY_CODES = {
        KeyEvent.VK_UP,
        KeyEvent.VK_DOWN,
        KeyEvent.VK_LEFT,
        KeyEvent.VK_RIGHT,
        KeyEvent.VK_PAGE_UP,
        KeyEvent.VK_PAGE_DOWN,
        KeyEvent.VK_ENTER,
    };

    /**
     * Registers key bindings on {@code component} and returns the keystroke→action-key
     * map so the caller can temporarily disable them (e.g. during text editing).
     */
    Map<KeyStroke, Object> installKeyBindings(JComponent component) {
        var bindings = new LinkedHashMap<KeyStroke, Object>();
        var inputMap = component.getInputMap(JComponent.WHEN_FOCUSED);
        var actionMap = component.getActionMap();

        for (var keyCode : KEY_CODES) {
            var actionKey = new Object();
            var keyStroke = KeyStroke.getKeyStroke(keyCode, 0);
            bindings.put(keyStroke, actionKey);
            inputMap.put(keyStroke, actionKey);
            actionMap.put(actionKey, new KeyAction(callback, editModeManager, keyCode));
        }

        return bindings;
    }

    /**
     * Action for handling keyboard input in keyboard control mode.
     */
    private static class KeyAction extends AbstractAction {

        private final InputHandlerCallback callback;
        private final EditModeManager editModeManager;
        private final int code;

        KeyAction(
            InputHandlerCallback callback,
            EditModeManager editModeManager,
            int code
        ) {
            this.callback = callback;
            this.editModeManager = editModeManager;
            this.code = code;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            handlePitchAdjustment();
        }

        private void handlePitchAdjustment() {
            if ((callback.getMode() != Mode.EDIT) || (callback.getControl() != Control.KEYBOARD)) {
                return;
            }

            // TODO: Keyboard mode navigation needs to be re-implemented.
            // The old position tracking system (NotePosition) has been removed.
            // Keyboard mode will need a new implementation that works with
            // LineComponent's insertion tracking or a separate keyboard-specific system.
            // When wiring caret navigation, consult Song.isInteractable(element, line)
            // so the caret skips the auto-maintained terminal on the last line.

            // For now, keyboard mode is disabled. Only UP/DOWN for pitch adjustment remain functional.
            var insertionNote = editModeManager.getPreviewElement();

            if (insertionNote != null) {
                if (code == KeyEvent.VK_UP) {
                    if (insertionNote.getStaffPosition() >= (-(StaffExtents.STAFF_LINES_ABOVE + 2) * 2)) {
                        insertionNote.setStaffPosition(insertionNote.getStaffPosition() - 1);
                        callback.repaint();
                    }
                } else if (code == KeyEvent.VK_DOWN) {
                    if (insertionNote.getStaffPosition() <= ((StaffExtents.STAFF_LINES_BELOW + 2) * 2)) {
                        insertionNote.setStaffPosition(insertionNote.getStaffPosition() + 1);
                        callback.repaint();
                    }
                }
            }
        }
    }
}
