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

import java.awt.*;
import java.awt.event.*;
import java.util.Objects;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.debug.DebugInspector;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.menu.DebugState;
import songscribe.ui.message.DeselectMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.MidiController;
import songscribe.util.UIUtils;

/**
 * Callback interface for ScoreInputHandler to communicate with Score.
 */
interface InputHandlerCallback {

    void repaint();

    Control getControl();

    Mode getMode();

    JPopupMenu getEditPopup();

    boolean requestFocusInWindow();
}

/**
 * Handles mouse and keyboard input for the Score component.
 * <p>
 * Manages debug inspector, popup triggers, focus, and playback guards.
 * Selection handling (click-to-select, drag-to-select, Alt-switch) is
 * handled by {@link LineComponent}.
 */
public final class ScoreInputHandler
    implements MouseListener, MouseMotionListener, KeyListener {

    private final InputHandlerCallback callback;
    private final EditModeManager editModeManager;

    public ScoreInputHandler(
        @NotNull InputHandlerCallback callback,
        @NotNull EditModeManager editModeManager
    ) {
        this.callback = callback;
        this.editModeManager = editModeManager;
    }

    //***************************
    // MouseListener methods
    //***************************
    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        callback.requestFocusInWindow();

        if (MidiController.isPlaying()) {
            return;
        }
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (e.isPopupTrigger()) {
            callback.getEditPopup().show((Component) e.getSource(), e.getX(), e.getY());
        }
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (e.isPopupTrigger()) {
            callback.getEditPopup().show((Component) e.getSource(), e.getX(), e.getY());
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (
            !editModeManager.isInsertionElementVisible() &&
                (callback.getControl() == Control.MOUSE) &&
                (callback.getMode() == Mode.EDIT)
        ) {
            editModeManager.setInsertionElementVisible(true);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            DebugState.setHoveredElement(null);
            DebugState.setMousePosition(null);
            callback.repaint();
        }

        if (
            editModeManager.isInsertionElementVisible() &&
                (callback.getControl() == Control.MOUSE) &&
                (callback.getMode() == Mode.EDIT)
        ) {
            editModeManager.setInsertionElementVisible(false);
            callback.repaint();
        }
    }

    //******************************
    // MouseMotionListener methods
    //******************************
    @Override
    public void mouseDragged(MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Inspector hover tracking - only repaint if hovered element changes
        if (DebugState.isInspectorEnabled()) {
            var oldHoveredElement = DebugState.getHoveredElement();
            DebugState.setMousePosition(new Point(e.getX(), e.getY()));
            DebugInspector.updateInspectorHover(e.getX(), e.getY());
            var newHoveredElement = DebugState.getHoveredElement();

            // Only repaint if the hovered element actually changed
            if (!Objects.equals(oldHoveredElement, newHoveredElement)) {
                DebugInspector.logInspectorHover(newHoveredElement);
                callback.repaint();
            }
        }
    }

    //***********************
    // KeyListener methods
    //***********************
    @Override
    public void keyPressed(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            LineComponent.clearInsertionElement();
            LineComponent.setAltPressed(true);
            callback.repaint();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE
            && callback.getMode() == Mode.SELECT
            && !UIUtils.isEditingText()) {
            MessageCenter.post(new DeselectMessage());
        }
    }

    @Override
    public void keyReleased(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            LineComponent.setAltPressed(false);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    //***********************
    // Helper methods
    //***********************

    /**
     * Action for handling keyboard input in keyboard control mode.
     */
    static class KeyAction extends AbstractAction {

        private final InputHandlerCallback callback;
        private final EditModeManager editModeManager;
        private final int code;

        KeyAction(
            @NotNull InputHandlerCallback callback,
            @NotNull EditModeManager editModeManager,
            int code
        ) {
            this.callback = callback;
            this.editModeManager = editModeManager;
            this.code = code;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if ((callback.getMode() != Mode.EDIT) || (callback.getControl() != Control.KEYBOARD)) {
                return;
            }

            // TODO: Keyboard mode navigation needs to be re-implemented.
            // The old position tracking system (NotePosition) has been removed.
            // Keyboard mode will need a new implementation that works with
            // LineComponent's insertion tracking or a separate keyboard-specific system.

            // For now, keyboard mode is disabled. Only UP/DOWN for pitch adjustment remain functional.
            var insertionNote = editModeManager.getInsertionElement();

            if (insertionNote != null) {
                if (code == KeyEvent.VK_UP) {
                    if (insertionNote.getStaffPosition() >= (-(Score.STAFF_LINES_ABOVE + 2) * 2)) {
                        insertionNote.setStaffPosition(insertionNote.getStaffPosition() - 1);
                        callback.repaint();
                    }
                } else if (code == KeyEvent.VK_DOWN) {
                    if (insertionNote.getStaffPosition() <= ((Score.STAFF_LINES_BELOW + 2) * 2)) {
                        insertionNote.setStaffPosition(insertionNote.getStaffPosition() + 1);
                        callback.repaint();
                    }
                }
            }
        }
    }
}
