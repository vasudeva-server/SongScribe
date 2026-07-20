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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.message.MessageCenter;
import songscribe.ui.Mode;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PitchShifter;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.message.command.DeselectCommand;
import songscribe.util.UIUtils;

/**
 * Handles mouse and keyboard input for the ScoreView component.
 * <p>
 * Manages popup triggers, focus, and playback guards.
 * Selection handling (click-to-select, drag-to-select, Alt-switch) is
 * handled by {@link LineComponent}.
 */
public final class ScoreInputHandler extends KeyAdapter
    implements MouseListener, MouseMotionListener, MouseWheelListener {

    private final InputHandlerCallback callback;

    public ScoreInputHandler(InputHandlerCallback callback) {
        this.callback = callback;
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
        if (!EditModeManager.isPreviewElementVisible() && (callback.getMode() == Mode.EDIT)) {
            EditModeManager.setPreviewElementVisible(true);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (EditModeManager.isPreviewElementVisible() && (callback.getMode() == Mode.EDIT)) {
            EditModeManager.setPreviewElementVisible(false);
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
    // MouseWheelListener methods
    //***********************
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if ((e.getModifiersEx() & UIUtils.MENU_SHORTCUT_MASK) != UIUtils.MENU_SHORTCUT_MASK) {
            callback.forwardWheelScroll(e);
            return;
        }

        // The menu-shortcut modifier held during a wheel/two-finger-scroll gesture is the
        // platform zoom convention (Cmd+scroll on macOS, Ctrl+scroll elsewhere).
        e.consume();
        callback.zoomByWheel(e.getPreciseWheelRotation(), e.getPoint());
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
            if (EditModeManager.getGraceModeManager().isInProgress()) {
                EditModeManager.getGraceModeManager().keyPressed(e);
            } else if (callback.getMode() == Mode.SELECT) {
                var window = callback.getWindow();

                if (window == null || !UIUtils.isEditingTextIn(window)) {
                    MessageCenter.post(new DeselectCommand());
                }
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
            registerBinding(bindings, inputMap, actionMap, keyCode, 0);
        }

        // Shift+Left/Right extend or shrink an existing selection.
        registerBinding(bindings, inputMap, actionMap, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
        registerBinding(bindings, inputMap, actionMap, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);

        return bindings;
    }

    private void registerBinding(
        Map<KeyStroke, Object> bindings, InputMap inputMap, ActionMap actionMap, int keyCode, int modifiers) {
        var actionKey = new Object();
        var keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        var shift = (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
        bindings.put(keyStroke, actionKey);
        inputMap.put(keyStroke, actionKey);
        actionMap.put(actionKey, new KeyAction(callback, keyCode, shift));
    }

    /**
     * Action for handling arrow-key input on the score.
     */
    private static class KeyAction extends AbstractAction {

        /** Signed staff-position delta that raises a pitch by one position (up on the staff). */
        private static final int RAISE_PITCH_DELTA_SP = -1;

        /** Signed staff-position delta that lowers a pitch by one position (down on the staff). */
        private static final int LOWER_PITCH_DELTA_SP = 1;

        /** Signed element-index delta for a leftward (previous-element) selection move. */
        private static final int MOVE_LEFT = -1;

        /** Signed element-index delta for a rightward (next-element) selection move. */
        private static final int MOVE_RIGHT = 1;

        private final InputHandlerCallback callback;
        private final int code;
        private final boolean shift;

        KeyAction(InputHandlerCallback callback, int code, boolean shift) {
            this.callback = callback;
            this.code = code;
            this.shift = shift;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var coordinator = callback.getSelectionCoordinator();
            var selection = coordinator.getSelection();

            // Shift+Left/Right extend the active selection; with no selection there is
            // nothing to extend.
            if (shift) {
                if (selection != null) {
                    extendSelection(coordinator);
                }

                return;
            }

            // With an active selection, arrow keys control that selection.
            if (selection != null) {
                handleSelectionArrow(coordinator, selection);
            }
        }

        /**
         * Shift+Left/Right: move the non-anchor end of the selection one element in the
         * arrow's direction, extending or shrinking the range. Computes the target index
         * and delegates the actual selection change to the shared
         * {@link InputHandlerCallback#extendSelectionTo(int)}. Stops at the line
         * boundary — a selection never spans lines.
         */
        private void extendSelection(SelectionCoordinator coordinator) {
            var state = coordinator.getActiveSelection();

            if (state == null || state.getSelectionAnchor() == -1) {
                return;
            }

            var anchor = state.getSelectionAnchor();
            var movingEnd = (anchor == state.getSelectionBegin()) ? state.getSelectionEnd() : state.getSelectionBegin();
            int target;

            if (code == KeyEvent.VK_LEFT) {
                target = movingEnd - 1;

                if (target < 0) {
                    return;
                }
            } else {
                target = movingEnd + 1;

                if (target > state.getLine().effectiveElementCount() - 1) {
                    return;
                }
            }

            callback.extendSelectionTo(target);
        }

        /**
         * Arrow-key behavior while a selection is active: Up/Down shift the
         * selection's pitch by one staff position (through the same mutation path
         * as a mouse drag); Left/Right move the selection to the neighboring
         * element, crossing to the adjacent line at a boundary.
         */
        private void handleSelectionArrow(SelectionCoordinator coordinator, ElementSelection selection) {
            var line = selection.line();
            var begin = selection.begin();
            var end = selection.end();

            switch (code) {
                case KeyEvent.VK_UP ->
                        applyPitchShift(coordinator, PitchShifter.shiftPitch(line, begin, end, RAISE_PITCH_DELTA_SP));
                case KeyEvent.VK_DOWN ->
                        applyPitchShift(coordinator, PitchShifter.shiftPitch(line, begin, end, LOWER_PITCH_DELTA_SP));
                case KeyEvent.VK_LEFT -> moveSelection(coordinator, selection, MOVE_LEFT);
                case KeyEvent.VK_RIGHT -> moveSelection(coordinator, selection, MOVE_RIGHT);
                default -> { }
            }

            // Up/Down repaint via the SongDidChangeNotification the pitch shift commits;
            // Left/Right repaint inside selectSingle.
        }

        /**
         * Re-syncs the active selection after a pitch shift that collapsed a grace
         * note into its host (or vice versa). {@code adjustedRange} is null when the
         * selection's original {@code [begin, end]} range is still valid — nothing
         * removed. Otherwise it's the range adjusted for the removed element's index
         * shift, or {@code {-1, -1}} if the whole range was removed.
         */
        private void applyPitchShift(SelectionCoordinator coordinator, int @Nullable [] adjustedRange) {
            if (adjustedRange == null) {
                return;
            }

            var state = coordinator.getActiveSelection();

            if (state == null) {
                return;
            }

            if (adjustedRange[0] == -1) {
                state.clearSelection();
            } else {
                state.setSelectionRange(adjustedRange[0], adjustedRange[1]);
            }
        }

        /**
         * Left/Right arrow on a selection, unified by {@code direction} (−1 left, +1
         * right):
         * <ul>
         *   <li>a multi-element selection collapses to a single element one step inward
         *       from its trailing edge (Left → {@code end − 1}, Right → {@code begin + 1});
         *   <li>a single selection moves one element in {@code direction};
         *   <li>at the line boundary it crosses to the adjacent line's nearest element,
         *       if any.
         * </ul>
         */
        private void moveSelection(SelectionCoordinator coordinator, ElementSelection selection, int direction) {
            var line = selection.line();
            var begin = selection.begin();
            var end = selection.end();
            var lineIndex = coordinator.getActiveLineIndex();

            if (end > begin) {
                // Collapse the range toward the arrow, landing one step in from the trailing edge.
                var trailingEdge = (direction == MOVE_LEFT) ? end : begin;
                selectSingle(coordinator, lineIndex, trailingEdge + direction);
                return;
            }

            // Single element: the boundary is index 0 going left, the last element going right.
            var current = begin;
            var boundary = (direction == MOVE_LEFT) ? 0 : line.effectiveElementCount() - 1;

            if (current != boundary) {
                selectSingle(coordinator, lineIndex, current + direction);
                return;
            }

            crossToAdjacentLine(coordinator, line, lineIndex, direction);
        }

        /**
         * Crosses a single selection at the line boundary to the adjacent line in
         * {@code direction}: the previous line's last element going left, the next
         * line's first element going right. No-op if there is no adjacent line or it
         * has no elements.
         */
        private void crossToAdjacentLine(SelectionCoordinator coordinator, Line line, int lineIndex, int direction) {
            var adjacentIndex = lineIndex + direction;
            var song = line.getSong();

            if (adjacentIndex < 0 || adjacentIndex >= song.lineCount()) {
                return;
            }

            var adjacentLine = song.getLine(adjacentIndex);
            var elementCount = adjacentLine.effectiveElementCount();

            if (elementCount == 0) {
                return;
            }

            // Land on the element nearest the boundary just crossed: the last going left, first going right.
            var landingIndex = (direction == MOVE_LEFT) ? elementCount - 1 : 0;
            selectSingle(coordinator, adjacentIndex, landingIndex);
        }

        /**
         * Collapses the selection to a single element at {@code elementIndex} on the
         * line at {@code lineIndex} via the shared
         * {@link SelectionCoordinator#selectSingleElement(int, int)} primitive (the
         * same one the mouse click-to-select path uses), then notifies and repaints.
         */
        private void selectSingle(SelectionCoordinator coordinator, int lineIndex, int elementIndex) {
            if (coordinator.selectSingleElement(lineIndex, elementIndex) == null) {
                return;
            }

            callback.selectionChanged();
            callback.repaint();
        }
    }
}
