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

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import songscribe.dom.Line;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleFallOnLastInsertionCommand;
import songscribe.message.command.ToggleGlissandoWithPreviousCommand;
import songscribe.message.command.ToggleTieWithPreviousCommand;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PitchShifter;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.util.UIUtils;

/**
 * Handles mouse and keyboard input that lands on the {@link ScoreView} itself rather
 * than on any score component.
 */
public final class ScoreInputHandler extends KeyAdapter
    implements MouseListener, MouseMotionListener, MouseWheelListener {

    private final InputHandlerCallback callback;

    public ScoreInputHandler(InputHandlerCallback callback) {
        this.callback = callback;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!UIUtils.isLeftClick(e)) {
            return;
        }

        // A click that reaches the view landed on no score component; each score
        // component handles its own.
        callback.cancelPlacementAndDeselect();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!UIUtils.isLeftClick(e)) {
            return;
        }

        callback.takeFocus();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Nothing to do: a preview element exists only over a line, and PreviewElementManager
        // raises the visible flag itself when a line is entered.
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Belt and braces: leaving the score normally exits the owning line first, which takes
        // its own preview down. This covers the cursor leaving without that exit being
        // delivered, where the preview would otherwise be left painted.
        LineComponent.clearPreviewElement();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Nothing to do: the view itself has no hover behaviour, and every point a line
        // responds to falls inside that line's own bounds, so the line is hovered directly.
    }

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

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            LineComponent.clearPreviewElement();
            LineComponent.setAltPressed(true);
            callback.repaint();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (EditModeManager.getGraceModeManager().isInProgress()) {
                EditModeManager.getGraceModeManager().keyPressed(e);
            } else if (EditModeManager.getInsertionPointMode().isInProgress()) {
                // First Escape cancels the pending placement and leaves any selection intact;
                // a second Escape then falls through to DeselectCommand below.
                EditModeManager.getInsertionPointMode().cancel();
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

    private static final int[] KEY_CODES = {
        KeyEvent.VK_UP,
        KeyEvent.VK_DOWN,
        KeyEvent.VK_LEFT,
        KeyEvent.VK_RIGHT,
        KeyEvent.VK_PAGE_UP,
        KeyEvent.VK_PAGE_DOWN,
        KeyEvent.VK_ENTER,
    };

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

        // The keystroke comes off the action the binding falls through to, not from a literal
        // repeated here: the fall-through only reaches the right command while the two agree,
        // and nothing else would notice if they stopped agreeing.
        registerLastInsertionBinding(bindings, inputMap, actionMap,
            Actions.TOGGLE_BEAM_ACTION, ToggleBeamWithPreviousCommand::new);
        registerLastInsertionBinding(bindings, inputMap, actionMap,
            Actions.GLISSANDO_ACTION, ToggleGlissandoWithPreviousCommand::new);
        registerLastInsertionBinding(bindings, inputMap, actionMap,
            Actions.FALL_ACTION, ToggleFallOnLastInsertionCommand::new);
        registerLastInsertionBinding(bindings, inputMap, actionMap,
            Actions.TOGGLE_TIE_ACTION, ToggleTieWithPreviousCommand::new);

        return bindings;
    }

    /**
     * The binding lives on the focused score, not on the target action: that action stays
     * disabled in edit mode, and a disabled action in the window's input map is skipped, so it
     * could never report failure.
     * <p>
     * The mode guard is an {@code isEnabled} override, not an early return from
     * {@code actionPerformed}: Swing stops at an enabled binding, so a guard that did nothing
     * would swallow the key and leave select mode with no working shortcut. Reporting it as
     * disabled lets the search reach {@code targetAction}'s own accelerator in the root pane.
     */
    private void registerLastInsertionBinding(
        Map<? super KeyStroke, Object> bindings,
        InputMap inputMap,
        ActionMap actionMap,
        UIAction targetAction,
        Supplier<? extends Message> commandFactory
    ) {
        registerKeyStroke(bindings, inputMap, actionMap, targetAction.getAccelerator(),
            new AbstractAction() {
                @Override
                public boolean isEnabled() {
                    // Grace mode and a pending placement own the keyboard while they are in
                    // progress, matching the precedence the Escape branch establishes above.
                    // The mode test comes first so the manager is not consulted outside edit mode.
                    return callback.getMode() == Mode.EDIT
                        && !EditModeManager.getGraceModeManager().isInProgress()
                        && !EditModeManager.getInsertionPointMode().isInProgress();
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

    private static class KeyAction extends AbstractAction {

        /** Signed staff-position delta that raises a pitch by one position (up on the staff). */
        private static final int RAISE_PITCH_DELTA_SP = -1;

        /** Signed staff-position delta that lowers a pitch by one position (down on the staff). */
        private static final int LOWER_PITCH_DELTA_SP = 1;

        private static final int MOVE_LEFT = -1;

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
            // Return/Enter completes a pending placement at the tracked insertion point;
            // with no tracked point it is a no-op and the placement stays pending. With no
            // placement pending it opens the lyric editor on the current selection.
            if (code == KeyEvent.VK_ENTER) {
                var insertionPointMode = EditModeManager.getInsertionPointMode();

                if (insertionPointMode.isInProgress()) {
                    insertionPointMode.place();
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
         * Stops at the line boundary — a selection never spans lines.
         */
        private void extendSelection(SelectionCoordinator coordinator) {
            var range = coordinator.getRange();

            if (range == null) {
                return;
            }

            var anchor = range.anchor();
            var bounds = range.range();
            var movingEnd = (anchor == bounds.begin()) ? bounds.end() : bounds.begin();
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

        private void handleSelectionArrow(SelectionCoordinator coordinator, ElementSelection selection) {
            var line = selection.line();
            var bounds = selection.range();

            switch (code) {
                case KeyEvent.VK_UP -> PitchShifter.shiftPitch(line, bounds, RAISE_PITCH_DELTA_SP);
                case KeyEvent.VK_DOWN -> PitchShifter.shiftPitch(line, bounds, LOWER_PITCH_DELTA_SP);
                case KeyEvent.VK_LEFT -> moveSelection(coordinator, selection, MOVE_LEFT);
                case KeyEvent.VK_RIGHT -> moveSelection(coordinator, selection, MOVE_RIGHT);
                default -> { }
            }

            // Up/Down repaint via the SongDidChangeNotification the pitch shift commits;
            // Left/Right repaint inside selectSingle.
        }

        private void moveSelection(SelectionCoordinator coordinator, ElementSelection selection, int direction) {
            var line = selection.line();
            var bounds = selection.range();
            var begin = bounds.begin();
            var end = bounds.end();
            var lineIndex = line.index();

            if (end > begin) {
                // Collapse the range toward the arrow, landing one step in from the trailing edge.
                var trailingEdge = (direction == MOVE_LEFT) ? end : begin;
                selectSingle(coordinator, lineIndex, trailingEdge + direction);
                return;
            }

            // Single element: the boundary is index 0 going left, and going right the last
            // selectable element — or the current index when it is already past that, which is
            // the case sitting on the terminal. Without folding in the current
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

        private void selectSingle(SelectionCoordinator coordinator, int lineIndex, int elementIndex) {
            if (!coordinator.selectSingleElement(lineIndex, elementIndex)) {
                return;
            }

            callback.selectionChanged();
            callback.repaint();
        }
    }
}
