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

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.notification.CompositionDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.music.ArticulationType;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.Dialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Manages edit mode state for the Score.
 * <p>
 * EditModeManager holds the state related to the insertion element (the element that will be inserted),
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

    // Dependencies
    private final Supplier<Composition> compositionSupplier;
    private final ClipboardManager clipboardManager;
    private final SelectionCoordinator selectionCoordinator;
    private final ScoreActions scoreActions;
    private final GraceModeManager graceModeManager;

    // Whether the insertion element is visible (based on mouse position in mouse mode)
    private boolean insertionElementIsVisible = false;

    // The current insertion element that will be inserted
    @Nullable
    private StaffElement insertionElement = null;

    // Control state for paste operations
    @Nullable
    private Control prevPasteControl = null;

    // Whether to play a note sound when inserting elements
    private boolean playInsertedNote = true;

    /**
     * Creates a new EditModeManager and registers it as the global instance.
     *
     * @param compositionSupplier Supplier for getting the current composition
     * @param clipboardManager The clipboard manager for paste operations
     * @param selectionCoordinator The selection coordinator for selection operations
     * @param scoreActions Callback interface for Score actions
     */
    public EditModeManager(
        Supplier<Composition> compositionSupplier,
        ClipboardManager clipboardManager,
        SelectionCoordinator selectionCoordinator,
        ScoreActions scoreActions
    ) {
        this.compositionSupplier = compositionSupplier;
        this.clipboardManager = clipboardManager;
        this.selectionCoordinator = selectionCoordinator;
        this.scoreActions = scoreActions;
        this.graceModeManager = new GraceModeManager(this, selectionCoordinator);
        instance = this;
    }

    /**
     * Returns the GraceModeManager that manages grace note pairing.
     */
    public GraceModeManager getGraceModeManager() {
        return graceModeManager;
    }

    // -------------------------------------------------------------------------
    // Insertion element accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the insertion element is currently visible.
     */
    public boolean isInsertionElementVisible() {
        return insertionElementIsVisible;
    }

    /**
     * Sets whether the insertion element is visible.
     *
     * @param visible true to show the insertion element, false to hide it
     */
    public void setInsertionElementVisible(boolean visible) {
        this.insertionElementIsVisible = visible;
    }

    /**
     * Returns the current insertion element, or null if no insertion element is set.
     */
    @Nullable
    public StaffElement getInsertionElement() {
        return insertionElement;
    }

    /**
     * Sets the current insertion element.
     *
     * @param insertionElement The element to set as the insertion element, or null to clear it
     */
    public void setInsertionElement(@Nullable StaffElement insertionElement) {
        this.insertionElement = insertionElement;
    }

    /**
     * Returns whether an insertion element is currently set.
     */
    public boolean hasInsertionElement() {
        return insertionElement != null;
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

    /**
     * Sets the control state to restore after paste operations.
     *
     * @param prevPasteControl The control state to restore
     */
    public void setPrevPasteControl(@Nullable Control prevPasteControl) {
        this.prevPasteControl = prevPasteControl;
    }

    // -------------------------------------------------------------------------
    // Insertion element creation and decoration
    // -------------------------------------------------------------------------

    /**
     * Creates a new insertion element based on the currently selected element type.
     *
     * @return The newly created insertion element
     */
    public StaffElement makeInsertionElement() {
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

        return makeInsertionElement(elementType);
    }

    /**
     * Creates a new insertion element of the given type.
     *
     * @param elementType The type of element to create
     * @return The newly created insertion element
     */
    public StaffElement makeInsertionElement(ElementType elementType) {
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
                : StaffElement.Accidental.NONE
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
        if (insertionElement == null) {
            return false;
        }

        scoreActions.clearSelection();

        if (
            (insertionElement.getType() == ElementType.REPEAT_LEFT) &&
                ((elementIndex - 1) >= 0) &&
                (line.getElement(elementIndex - 1).getType() == ElementType.REPEAT_RIGHT)
        ) {
            var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
            repeatLeftRight.setXPosSs(line.getElement(elementIndex - 1).getXPosSs());
            line.setElement(elementIndex - 1, repeatLeftRight);
            return true;
        }

        if (
            (insertionElement.getType() == ElementType.REPEAT_RIGHT) &&
                (elementIndex < line.elementCount()) &&
                (line.getElement(elementIndex).getType() == ElementType.REPEAT_LEFT)
        ) {
            var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
            repeatLeftRight.setXPosSs(line.getElement(elementIndex).getXPosSs());
            line.setElement(elementIndex, repeatLeftRight);
            return true;
        }

        if (insertionElement.getType() == ElementType.PASTE) {
            // If the user tries to insert into triplet, they will get an error message.
            var iv = line.getTuplets().findInterval(elementIndex - 1);

            if ((iv != null) && ((elementIndex - 1) < iv.getEnd())) {
                Dialogs.showErrorMessage(
                    null,
                    Strings.get(Strings.DIALOG_TITLE_EDIT_ERROR),
                    Strings.get(Strings.ERROR_TRIPLET_INSERT)
                );
                return true;
            }

            line.removeInterval(elementIndex - 1, elementIndex);
            var diff =
                ((elementIndex == line.elementCount())
                    ? (int) Math.round(InsertionSpacingCalculator.calculateAppendPositionSs(
                    line, clipboardManager.getFirstElement()))
                    : line.getElement(elementIndex).getXPosSs()) -
                    clipboardManager.getFirstElement().getXPosSs();
            var copySize = clipboardManager.getSize();

            for (var i = 0; i < copySize; i++) {
                var element = clipboardManager.getElement(i);
                element.setXPosSs(element.getXPosSs() + diff);
                line.addElement(elementIndex + i, element.clone());
            }

            var intervalsCopy = clipboardManager.getIntervalsCopyBuffer();

            if (intervalsCopy != null) {
                line.pasteIntervals(intervalsCopy, elementIndex);
            }

            // Calculate shift for elements after the pasted block
            var afterPasteIndex = elementIndex + copySize;
            int shift = 0;

            if (afterPasteIndex < line.elementCount()) {
                var lastPastedElement = line.getElement(afterPasteIndex - 1);
                var firstElementAfter = line.getElement(afterPasteIndex);
                shift = (int) Math.round(
                    InsertionSpacingCalculator.calculateNextElementXSs(
                        lastPastedElement, firstElementAfter) -
                        firstElementAfter.getXPosSs());
                shift = Math.max(0, shift);
            }

            for (var i = afterPasteIndex; i < line.elementCount(); i++) {
                line.getElement(i).setXPosSs(line.getElement(i).getXPosSs() + shift);
            }

            var control = scoreActions.getControl();

            if (prevPasteControl != null) {
                scoreActions.setControl(prevPasteControl);
            }

            for (var i = elementIndex; i < (elementIndex + copySize); i++) {
                var interval = line.getBeamings().findInterval(i);

                if (interval != null) {
                    i = interval.getEnd();
                }
            }

            selectionCoordinator.setInSelectMode(true);
            return true;
        }

        return false;
    }

    public void insertionElementDidChange(Line line, int elementIndex) {
        if (insertionElement == null) {
            return;
        }

        // Capture the inserted element before insertionElement is updated for the next insertion.
        var insertedElement = line.getElement(elementIndex);
        var shouldPlayNote = playInsertedNote && insertedElement.getType().isNote();

        var nextElement = insertionElement.getType().newInstance();

        // After inserting an element, turn off fermata and accidental parentheses
        Actions.FERMATA_ACTION.setSelected(false);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);

        // Add any other element decorations
        decorateElement(nextElement);
        scoreActions.setInsertionElement(nextElement);
        LyricsProcessor.spellLyrics(line);
        scoreActions.drawWidthIfWiderLine(line, false);

        var composition = compositionSupplier.get();
        MessageCenter.post(new CompositionDidChangeNotification(CompositionDidChangeNotification.ChangeType.CONTENT, composition, line));

        scoreActions.repaint();

        if (shouldPlayNote) {
            new PlayThread(insertedElement.getPitch()).start();
        }
    }

}
