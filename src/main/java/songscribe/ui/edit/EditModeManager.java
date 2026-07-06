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

package songscribe.ui.edit;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.dom.Articulation;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Manages edit mode state for the ScoreView.
 * <p>
 * EditModeManager holds the state related to the preview element (the element that will be inserted),
 * including its type, duration, and accidentals. Also handles element creation, decoration,
 * and modification logic.
 */
public final class EditModeManager {

    @Nullable
    private static EditModeManager INSTANCE;

    /**
     * Creates the singleton instance and wires it to the given dependencies.
     * Must be called exactly once, during ScoreView construction.
     *
     * @param clipboardManager The clipboard manager for paste operations
     * @param selectionCoordinator The selection coordinator for selection operations
     * @param scoreActions Callback interface for ScoreView actions
     */
    public static void init(
        ClipboardManager clipboardManager,
        SelectionCoordinator selectionCoordinator,
        ScoreActions scoreActions
    ) {
        INSTANCE = new EditModeManager(clipboardManager, selectionCoordinator, scoreActions);
    }

    /** Returns the initialized instance; throws if {@link #init} has not been called. */
    private static EditModeManager instance() {
        if (INSTANCE == null) {
            throw RuntimeError.exit("EditModeManager not initialized — call init() first");
        }

        return INSTANCE;
    }

    /** Sets the singleton instance; intended for test teardown only. */
    static void setInstance(@Nullable EditModeManager value) {
        INSTANCE = value;
    }

    private final ScoreActions scoreActions;
    private final GraceModeManager graceModeManager;

    // Whether the preview element is visible (based on mouse position in mouse mode)
    private boolean previewElementIsVisible = false;

    // The current preview element that will be inserted
    @Nullable
    private StaffElement previewElement = null;

    // Whether to play a note sound when inserting elements
    private boolean playInsertedNote = true;

    private EditModeManager(
        ClipboardManager clipboardManager,
        SelectionCoordinator selectionCoordinator,
        ScoreActions scoreActions
    ) {
        this.scoreActions = scoreActions;
        graceModeManager = new GraceModeManager(this, selectionCoordinator);
    }

    /**
     * Returns the GraceModeManager that manages grace note pairing.
     */
    public static GraceModeManager getGraceModeManager() {
        return instance().graceModeManager;
    }

    // -------------------------------------------------------------------------
    // Preview element accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the preview element is currently visible.
     */
    public static boolean isPreviewElementVisible() {
        return instance().previewElementIsVisible;
    }

    /**
     * Sets whether the preview element is visible.
     *
     * @param visible true to show the preview element, false to hide it
     */
    public static void setPreviewElementVisible(boolean visible) {
        instance().previewElementIsVisible = visible;
    }

    /**
     * Returns the current preview element, or null if no preview element is set.
     */
    @Nullable
    public static StaffElement getPreviewElement() {
        return instance().previewElement;
    }

    /**
     * Sets the current preview element.
     *
     * @param previewElement The element to set as the preview element, or null to clear it
     */
    public static void setPreviewElement(@Nullable StaffElement previewElement) {
        instance().previewElement = previewElement;
        MessageCenter.post(new PreviewElementDidChangeNotification(previewElement));
    }

    /**
     * Returns whether a preview element is currently set.
     */
    public static boolean hasPreviewElement() {
        return instance().previewElement != null;
    }

    /**
     * Returns whether to play inserted notes.
     */
    public static boolean isPlayInsertedNote() {
        return instance().playInsertedNote;
    }

    /**
     * Sets whether to play inserted notes.
     *
     * @param playInsertedNote true to play inserted notes, false otherwise
     */
    public static void setPlayInsertedNote(boolean playInsertedNote) {
        instance().playInsertedNote = playInsertedNote;
    }

    // -------------------------------------------------------------------------
    // Preview element creation and decoration
    // -------------------------------------------------------------------------

    /**
     * Creates a new preview element based on the currently selected element type.
     *
     * @return The newly created preview element
     */
    public static StaffElement makePreviewElement() {
        var elementType = ElementType.CROTCHET;
        var durationAction = Actions.DURATION_ACTION_GROUP.getSelected();

        if (durationAction != null) {
            elementType = durationAction.getType();
        } else {
            var barAction = Actions.NON_DURATION_ACTION_GROUP.getSelected();

            if (barAction != null) {
                elementType = barAction.getType();
            }
        }

        return makePreviewElement(elementType);
    }

    /**
     * Creates a new preview element of the given type.
     *
     * @param elementType The type of element to create
     * @return The newly created preview element
     */
    public static StaffElement makePreviewElement(ElementType elementType) {
        // Make a new element or rest of the given type
        var type = elementType;

        if (Actions.REST_ACTION.isSelected() && elementType.isPitchedNote()) {
            type = ElementType.valueOf(elementType.name() + "_REST");
        }

        var element = type.newInstance();
        decorateElement(element);
        return element;
    }

    /**
     * Decorates an element with the currently selected accidentals, dots, and articulations.
     *
     * @param element The element to decorate
     */
    public static void decorateElement(StaffElement element) {
        var dotAction = Actions.DOT_ACTION_GROUP.getSelected();
        element.setDotCount(
            (dotAction != null) ? dotAction.getDotLevel().ordinal() + 1 : 0
        );

        // Rests don't get any other decorations
        if (element.getType().isRest()) {
            return;
        }

        var accidentalAction = Actions.ACCIDENTAL_ACTION_GROUP.getSelected();
        element.setAccidental(
            (accidentalAction != null)
                ? accidentalAction.getAccidental()
                : null
        );

        element.setAccidentalInParentheses(
            Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected()
        );

        element.clearArticulations();

        if (Actions.ACCENT_ACTION.isSelected()) {
            element.addArticulation(
                new Articulation(element, ArticulationType.ACCENT)
            );
        }

        var durationArticulationAction =
            Actions.ARTICULATION_ACTION_GROUP.getSelected();

        if (durationArticulationAction != null) {
            element.addArticulation(
                new Articulation(element, ArticulationType.STACCATO)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Element modification operations
    // -------------------------------------------------------------------------

    /**
     * Handles special element types that require custom insertion behavior.
     * Returns true if the element was modified (and no further insertion should occur).
     *
     * @param line The line to insert into
     * @param elementIndex The index at which to insert
     * @return true if the element was modified, false if normal insertion should proceed
     */
    public static boolean elementWasModified(Line line, int elementIndex) {
        var inst = instance();

        if (inst.previewElement == null) {
            return false;
        }

        inst.scoreActions.clearSelection();

        if (inst.previewElement.getType() == ElementType.REPEAT_LEFT && (elementIndex - 1) >= 0) {
            var prevElement = line.getElement(elementIndex - 1);

            if (prevElement.getType() == ElementType.REPEAT_RIGHT) {
                var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
                repeatLeftRight.setXOffsetPx(prevElement.getXOffsetPx());
                line.setElement(elementIndex - 1, repeatLeftRight);
                return true;
            }
        }

        if (inst.previewElement.getType() == ElementType.REPEAT_RIGHT && elementIndex < line.elementCount()) {
            var nextElement = line.getElement(elementIndex);

            if (nextElement.getType() == ElementType.REPEAT_LEFT) {
                var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
                repeatLeftRight.setXOffsetPx(nextElement.getXOffsetPx());
                line.setElement(elementIndex, repeatLeftRight);
                return true;
            }
        }

        return false;
    }

    public static void previewElementDidChange(Line line, int elementIndex) {
        var inst = instance();

        if (inst.previewElement == null) {
            return;
        }

        // Capture the inserted element before previewElement is updated for the next insertion.
        var insertedElement = line.getElement(elementIndex);
        var shouldPlayNote = inst.playInsertedNote && insertedElement.getType().isNote();

        var nextElement = inst.previewElement.getType().newInstance();

        // After inserting an element, turn off accidental parentheses
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);

        // Add any other element decorations
        decorateElement(nextElement);
        inst.scoreActions.setPreviewElement(nextElement);
        inst.scoreActions.drawWidthIfWiderLine(line, false);
        inst.scoreActions.repaint();

        if (shouldPlayNote) {
            new PlayThread(insertedElement.getPitch()).start();
        }
    }

}
