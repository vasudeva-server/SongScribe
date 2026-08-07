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
import java.util.function.Supplier;

import songscribe.dom.Line;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.ui.Mode;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PitchShifter;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleFallOnLastInsertionCommand;
import songscribe.message.command.ToggleGlissandoWithPreviousCommand;
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

        // A click on the ScoreView itself (not on any line) cancels paste mode and,
        // in select mode, clears the current selection — per-line clicks are handled
        // and consumed by LineComponent.
        if (EditModeManager.getPasteModeManager().isInProgress()) {
            EditModeManager.getPasteModeManager().cancel();
        }

        if (callback.getSelectionCoordinator().isInSelectMode()) {
            MessageCenter.post(new DeselectCommand());
        }

        callback.requestFocusInWindow();
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (!EditModeManager.isPreviewElementVisible() && (callback.getMode() == Mode.EDIT)) {
            EditModeManager.setPreviewElementVisible(true);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Belt and braces: leaving the score normally exits the owning line first, which takes
        // its own preview down. This covers the cursor leaving without that exit being
        // delivered, where the preview would otherwise be left painted.
        LineComponent.clearPreviewElement();

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
        // Nothing to do: the view itself has no hover behaviour, and every point a line
        // responds to now falls inside that line's own bounds, so the line is hovered directly.
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
            } else if (EditModeManager.getPasteModeManager().isInProgress()) {
                // First Escape cancels paste mode and leaves any selection intact;
                // a second Escape then falls through to DeselectCommand below.
                EditModeManager.getPasteModeManager().cancel();
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

        registerLastInsertionBinding(bindings, inputMap, actionMap,
            KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), ToggleBeamWithPreviousCommand::new);
        registerLastInsertionBinding(bindings, inputMap, actionMap,
            KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK), ToggleGlissandoWithPreviousCommand::new);
        registerLastInsertionBinding(bindings, inputMap, actionMap,
            KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), ToggleFallOnLastInsertionCommand::new);

        return bindings;
    }

    /**
     * Binds {@code keyStroke} to posting the command {@code commandFactory} produces, acting
     * on the last insertion — e.g. plain {@code b} to beam it with the one before it, Shift+G
     * to toggle its glissando, plain {@code f} to toggle its fall.
     * <p>
     * The binding lives here, on the focused score, rather than on the target action: that
     * action stays disabled in edit mode, and a disabled action bound to the window's input
     * map is skipped outright, so it could never report failure. This binding resolves first
     * regardless of any action's enabled state.
     * <p>
     * That precedence is exactly why the mode guard is an {@code isEnabled} override rather
     * than an early return from {@code actionPerformed}. Swing invokes an enabled binding and
     * stops searching, so a guard that ran and did nothing would still swallow the key —
     * leaving select mode, where the score view holds focus, with no working shortcut at all.
     * Reporting the binding as disabled instead lets the search continue to the root pane,
     * where the target action's own accelerator lives.
     */
    private void registerLastInsertionBinding(
        Map<? super KeyStroke, Object> bindings,
        InputMap inputMap,
        ActionMap actionMap,
        KeyStroke keyStroke,
        Supplier<? extends Message> commandFactory
    ) {
        registerKeyStroke(bindings, inputMap, actionMap, keyStroke,
            new AbstractAction() {
                @Override
                public boolean isEnabled() {
                    // Grace mode and paste mode own the keyboard while they are in progress,
                    // matching the precedence the Escape branch establishes above. The mode
                    // test comes first so the manager is not consulted outside edit mode.
                    return callback.getMode() == Mode.EDIT
                        && !EditModeManager.getGraceModeManager().isInProgress()
                        && !EditModeManager.getPasteModeManager().isInProgress();
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    MessageCenter.post(commandFactory.get());
                }
            });
    }

    private void registerBinding(
        Map<? super KeyStroke, Object> bindings, InputMap inputMap, ActionMap actionMap, int keyCode, int modifiers) {
        var keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        var shift = (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
        registerKeyStroke(bindings, inputMap, actionMap, keyStroke, new KeyAction(callback, keyCode, shift));
    }

    /**
     * Wires {@code action} to {@code keyStroke} on the component's maps, recording the
     * keystroke in {@code bindings} so the caller can disable it again later.
     */
    private static void registerKeyStroke(
        Map<? super KeyStroke, Object> bindings,
        InputMap inputMap,
        ActionMap actionMap,
        KeyStroke keyStroke,
        Action action
    ) {
        var actionKey = new Object();
        bindings.put(keyStroke, actionKey);
        inputMap.put(keyStroke, actionKey);
        actionMap.put(actionKey, action);
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
            // Return/Enter places the clipboard fragment at the tracked insertion point
            // while paste mode is active; with no tracked point it is a no-op and the
            // paste stays pending. Outside paste mode it opens the lyric editor on the
            // current selection.
            if (code == KeyEvent.VK_ENTER) {
                var pasteModeManager = EditModeManager.getPasteModeManager();

                if (pasteModeManager.isInProgress()) {
                    pasteModeManager.place();
                } else {
                    callback.editLyricOnSelection();
                }

                return;
            }

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
            var range = coordinator.getRange();

            if (range == null) {
                return;
            }

            var anchor = range.anchor();
            var movingEnd = (anchor == range.begin()) ? range.end() : range.begin();
            int target;

            if (code == KeyEvent.VK_LEFT) {
                target = movingEnd - 1;

                if (target < 0) {
                    return;
                }
            } else {
                target = movingEnd + 1;

                if (target > range.line().effectiveElementCount() - 1) {
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

            // The window parents the restatement prompt a pitch shift may raise, so it opens over
            // the score rather than against the screen.
            var window = callback.getWindow();

            switch (code) {
                case KeyEvent.VK_UP -> PitchShifter.shiftPitch(window, line, begin, end, RAISE_PITCH_DELTA_SP);
                case KeyEvent.VK_DOWN -> PitchShifter.shiftPitch(window, line, begin, end, LOWER_PITCH_DELTA_SP);
                case KeyEvent.VK_LEFT -> moveSelection(coordinator, selection, MOVE_LEFT);
                case KeyEvent.VK_RIGHT -> moveSelection(coordinator, selection, MOVE_RIGHT);
                default -> { }
            }

            // Up/Down repaint via the SongDidChangeNotification the pitch shift commits;
            // Left/Right repaint inside selectSingle.
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

            // Single element: the boundary is index 0 going left, and going right the last
            // selectable element — or the current index when it is already past that, which is
            // the case sitting on the terminal (issue #713). Without folding in the current
            // index, Right on the terminal would compute a boundary behind the cursor and step
            // off the end of the line.
            var current = begin;
            var boundary = (direction == MOVE_LEFT) ? 0 : Math.max(current, line.effectiveElementCount() - 1);

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
            if (!coordinator.selectSingleElement(lineIndex, elementIndex)) {
                return;
            }

            callback.selectionChanged();
            callback.repaint();
        }
    }
}
