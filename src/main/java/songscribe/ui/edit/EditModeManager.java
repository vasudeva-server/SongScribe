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

import songscribe.music.ArticulationType;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.layout.Articulation;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Manages edit mode state for the Score.
 * <p>
 * EditModeManager holds the state related to the preview element (the element that will be inserted),
 * including its type, duration, and accidentals. Also handles element creation, decoration,
 * and modification logic. Extracted from Score.java as part of Phase 6 of the Score Cleanup refactoring.
 */
public final class EditModeManager {

    // Static instance for global access (temporary approach for Phase 2 refactoring)
    @Nullable
    private static EditModeManager instance = null;

    /**
     * Returns the current EditModeManager instance.
     * <p>
     * This static accessor allows components to access the edit mode state
     * without requiring explicit wiring through the component hierarchy.
     *
     * @return The current instance, or null if not yet created
     */
    @Nullable
    public static EditModeManager getInstance() {
        return instance;
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

    /**
     * Creates a new EditModeManager and registers it as the global instance.
     *
     * @param clipboardManager The clipboard manager for paste operations
     * @param selectionCoordinator The selection coordinator for selection operations
     * @param scoreActions Callback interface for Score actions
     */
    public EditModeManager(
        ClipboardManager clipboardManager,
        SelectionCoordinator selectionCoordinator,
        ScoreActions scoreActions
    ) {
        // Dependencies
        this.scoreActions = scoreActions;
        graceModeManager = new GraceModeManager(this, selectionCoordinator);
        instance = this;
    }

    /**
     * Returns the GraceModeManager that manages grace note pairing.
     */
    public GraceModeManager getGraceModeManager() {
        return graceModeManager;
    }

    // -------------------------------------------------------------------------
    // Preview element accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the preview element is currently visible.
     */
    public boolean isPreviewElementVisible() {
        return previewElementIsVisible;
    }

    /**
     * Sets whether the preview element is visible.
     *
     * @param visible true to show the preview element, false to hide it
     */
    public void setPreviewElementVisible(boolean visible) {
        this.previewElementIsVisible = visible;
    }

    /**
     * Returns the current preview element, or null if no preview element is set.
     */
    @Nullable
    public StaffElement getPreviewElement() {
        return previewElement;
    }

    /**
     * Sets the current preview element.
     *
     * @param previewElement The element to set as the preview element, or null to clear it
     */
    public void setPreviewElement(@Nullable StaffElement previewElement) {
        this.previewElement = previewElement;
    }

    /**
     * Returns whether a preview element is currently set.
     */
    public boolean hasPreviewElement() {
        return previewElement != null;
    }

    /**
     * Returns whether to play inserted notes.
     */
    public boolean isPlayInsertedNote() {
        return playInsertedNote;
    }

    /**
     * Sets whether to play inserted notes.
     *
     * @param playInsertedNote true to play inserted notes, false otherwise
     */
    public void setPlayInsertedNote(boolean playInsertedNote) {
        this.playInsertedNote = playInsertedNote;
    }

    // -------------------------------------------------------------------------
    // Preview element creation and decoration
    // -------------------------------------------------------------------------

    /**
     * Creates a new preview element based on the currently selected element type.
     *
     * @return The newly created preview element
     */
    public StaffElement makePreviewElement() {
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
    public StaffElement makePreviewElement(ElementType elementType) {
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
    public void decorateElement(StaffElement element) {
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

        element.setFermata(Actions.FERMATA_ACTION.isSelected());

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
    public boolean elementWasModified(Line line, int elementIndex) {
        if (previewElement == null) {
            return false;
        }

        scoreActions.clearSelection();

        if (
            (previewElement.getType() == ElementType.REPEAT_LEFT) &&
                ((elementIndex - 1) >= 0) &&
                (line.getElement(elementIndex - 1).getType() == ElementType.REPEAT_RIGHT)
        ) {
            var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
            repeatLeftRight.setXOffsetPx(line.getElement(elementIndex - 1).getXOffsetPx());
            line.setElement(elementIndex - 1, repeatLeftRight);
            return true;
        }

        if (
            (previewElement.getType() == ElementType.REPEAT_RIGHT) &&
                (elementIndex < line.elementCount()) &&
                (line.getElement(elementIndex).getType() == ElementType.REPEAT_LEFT)
        ) {
            var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
            repeatLeftRight.setXOffsetPx(line.getElement(elementIndex).getXOffsetPx());
            line.setElement(elementIndex, repeatLeftRight);
            return true;
        }

        return false;
    }

    public void previewElementDidChange(Line line, int elementIndex) {
        if (previewElement == null) {
            return;
        }

        // Capture the inserted element before previewElement is updated for the next insertion.
        var insertedElement = line.getElement(elementIndex);
        var shouldPlayNote = playInsertedNote && insertedElement.getType().isNote();

        var nextElement = previewElement.getType().newInstance();

        // After inserting an element, turn off fermata and accidental parentheses
        Actions.FERMATA_ACTION.setSelected(false);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);

        // Add any other element decorations
        decorateElement(nextElement);
        scoreActions.setPreviewElement(nextElement);
        scoreActions.drawWidthIfWiderLine(line, false);
        scoreActions.repaint();

        if (shouldPlayNote) {
            new PlayThread(insertedElement.getPitch()).start();
        }
    }

}
