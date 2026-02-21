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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.ArticulationType;
import songscribe.music.Composition;
import songscribe.music.ForceArticulation;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.Control;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.PlayNoteThread;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Manages edit mode state for the Score.
 * <p>
 * EditModeManager holds the state related to the edit note (the note that will be inserted),
 * including its type, duration, and accidentals. Also handles note creation, decoration,
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
    private final IMainFrame mainFrame;
    private final ClipboardManager clipboardManager;
    private final SelectionCoordinator selectionCoordinator;
    private final ScoreActions scoreActions;

    // Whether the edit note is visible (based on mouse position in mouse mode)
    private boolean editNoteIsVisible = false;

    // The current edit note that will be inserted
    @Nullable
    private Note editNote = null;

    // Control state for paste operations
    @Nullable
    private Control prevPasteControl = null;

    // Whether to play a note sound when inserting notes
    private boolean playInsertingNote = true;

    /**
     * Creates a new EditModeManager and registers it as the global instance.
     *
     * @param compositionSupplier Supplier for getting the current composition
     * @param mainFrame The main frame for showing error messages
     * @param clipboardManager The clipboard manager for paste operations
     * @param selectionCoordinator The selection coordinator for selection operations
     * @param scoreActions Callback interface for Score actions
     */
    public EditModeManager(
        @NotNull Supplier<Composition> compositionSupplier,
        @NotNull IMainFrame mainFrame,
        @NotNull ClipboardManager clipboardManager,
        @NotNull SelectionCoordinator selectionCoordinator,
        @NotNull ScoreActions scoreActions
    ) {
        this.compositionSupplier = compositionSupplier;
        this.mainFrame = mainFrame;
        this.clipboardManager = clipboardManager;
        this.selectionCoordinator = selectionCoordinator;
        this.scoreActions = scoreActions;
        instance = this;
    }

    // -------------------------------------------------------------------------
    // Edit note accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the edit note is currently visible.
     */
    public boolean isEditNoteVisible() {
        return editNoteIsVisible;
    }

    /**
     * Sets whether the edit note is visible.
     *
     * @param visible true to show the edit note, false to hide it
     */
    public void setEditNoteVisible(boolean visible) {
        this.editNoteIsVisible = visible;
    }

    /**
     * Returns the current edit note, or null if no edit note is set.
     */
    @Nullable
    public Note getEditNote() {
        return editNote;
    }

    /**
     * Sets the current edit note.
     *
     * @param editNote The note to set as the edit note, or null to clear it
     */
    public void setEditNote(@Nullable Note editNote) {
        this.editNote = editNote;
    }

    /**
     * Returns whether an edit note is currently set.
     */
    public boolean hasEditNote() {
        return editNote != null;
    }

    /**
     * Returns whether to play inserting notes.
     */
    public boolean isPlayInsertingNote() {
        return playInsertingNote;
    }

    /**
     * Sets whether to play inserting notes.
     *
     * @param playInsertingNote true to play inserting notes, false otherwise
     */
    public void setPlayInsertingNote(boolean playInsertingNote) {
        this.playInsertingNote = playInsertingNote;
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
    // Edit note creation and decoration
    // -------------------------------------------------------------------------

    /**
     * Creates a new edit note based on the currently selected note type.
     *
     * @return The newly created edit note
     */
    @NotNull
    public Note makeEditNote() {
        var noteType = NoteType.CROTCHET;
        var durationAction = Actions.DURATION_ACTION_GROUP.getSelected();

        if (durationAction != null) {
            noteType = durationAction.getType();
        } else {
            var barAction = Actions.NON_DURATION_ACTION_GROUP.getSelected();

            if (barAction != null) {
                noteType = barAction.getType();
            }
        }

        return makeEditNote(noteType);
    }

    /**
     * Creates a new edit note of the given type.
     *
     * @param noteType The type of note to create
     * @return The newly created edit note
     */
    @NotNull
    public Note makeEditNote(@NotNull NoteType noteType) {
        // Make a new note or rest of the given type
        var type = noteType;

        if (Actions.REST_ACTION.isSelected()) {
            type = NoteType.valueOf(noteType.name() + "_REST");
        }

        var note = type.newInstance();
        decorateNote(note);
        return note;
    }

    /**
     * Decorates a note with the currently selected accidentals, dots, and articulations.
     *
     * @param note The note to decorate
     */
    public void decorateNote(@NotNull Note note) {
        var dotAction = Actions.DOT_ACTION_GROUP.getSelected();
        note.setDotCount(
            (dotAction != null) ? dotAction.getDotLevel().ordinal() : 0
        );

        // Rests don't get any other decorations
        if (note.getNoteType().isRest()) {
            return;
        }

        var accidentalAction = Actions.ACCIDENTAL_ACTION_GROUP.getSelected();
        note.setAccidental(
            (accidentalAction != null)
                ? accidentalAction.getAccidental()
                : Note.Accidental.NONE
        );

        note.setAccidentalInParentheses(
            Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected()
        );

        note.setForceArticulation(
            Actions.ACCENT_ACTION.isSelected() ? ForceArticulation.ACCENT : null
        );

        var durationArticulationAction =
            Actions.ARTICULATION_ACTION_GROUP.getSelected();

        note.setDurationArticulation(
            (durationArticulationAction != null)
                ? durationArticulationAction.getArticulation()
                : null
        );

        note.setFermata(Actions.FERMATA_ACTION.isSelected());

        // Dual-write: populate new Articulation list alongside old properties
        note.clearArticulations();

        if (Actions.ACCENT_ACTION.isSelected()) {
            note.addArticulation(
                new Articulation(note, ArticulationType.ACCENT)
            );
        }

        if (durationArticulationAction != null) {
            note.addArticulation(
                new Articulation(note, ArticulationType.STACCATO)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Note modification operations
    // -------------------------------------------------------------------------

    /**
     * Handles special note types that require custom insertion behavior.
     * Returns true if the note was modified (and no further insertion should occur).
     *
     * @param line The line to insert into
     * @param noteIndex The index at which to insert
     * @return true if the note was modified, false if normal insertion should proceed
     */
    public boolean noteWasModified(@NotNull Line line, int noteIndex) {
        scoreActions.clearSelection();

        // If the active note is glissando, it needs different handling
        if (editNote.getNoteType() == NoteType.GLISSANDO) {
            if (noteIndex > 0) {
                line
                    .getNote(noteIndex - 1)
                    .setGlissando(editNote.getStaffPosition());
            }

            return true;
        }

        if (
            (editNote.getNoteType() == NoteType.REPEAT_LEFT) &&
                ((noteIndex - 1) >= 0) &&
                (line.getNote(noteIndex - 1).getNoteType() == NoteType.REPEAT_RIGHT)
        ) {
            var repeatLeftRight = NoteType.REPEAT_LEFT_RIGHT.newInstance();
            repeatLeftRight.setXPos(line.getNote(noteIndex - 1).getXPos());
            line.setNote(noteIndex - 1, repeatLeftRight);
            return true;
        }

        if (
            (editNote.getNoteType() == NoteType.REPEAT_RIGHT) &&
                (noteIndex < line.noteCount()) &&
                (line.getNote(noteIndex).getNoteType() == NoteType.REPEAT_LEFT)
        ) {
            var repeatLeftRight = NoteType.REPEAT_LEFT_RIGHT.newInstance();
            repeatLeftRight.setXPos(line.getNote(noteIndex).getXPos());
            line.setNote(noteIndex, repeatLeftRight);
            return true;
        }

        if (editNote.getNoteType() == NoteType.PASTE) {
            // If the user tries to insert into triplet, they will get an error message.
            var iv = line.getTuplets().findInterval(noteIndex - 1);

            if ((iv != null) && ((noteIndex - 1) < iv.getEnd())) {
                mainFrame.showErrorMessage("Cannot insert into a triplet.");
                return true;
            }

            line.removeInterval(noteIndex - 1, noteIndex);
            var diff =
                ((noteIndex == line.noteCount())
                    ? (int) Math.round(InsertionSpacingCalculator.calculateAppendPositionSs(
                    line, clipboardManager.getFirstNote()))
                    : line.getNote(noteIndex).getXPos()) -
                    clipboardManager.getFirstNote().getXPos();
            var copySize = clipboardManager.getSize();

            for (var i = 0; i < copySize; i++) {
                var note = clipboardManager.getNote(i);
                note.setXPos(note.getXPos() + diff);
                line.addNote(noteIndex + i, note.clone());
            }

            line.pasteIntervals(clipboardManager.getIntervalsCopyBuffer(), noteIndex);

            // Calculate shift for notes after the pasted block
            var afterPasteIndex = noteIndex + copySize;
            int shift = 0;

            if (afterPasteIndex < line.noteCount()) {
                var lastPastedNote = line.getNote(afterPasteIndex - 1);
                var firstNoteAfter = line.getNote(afterPasteIndex);
                shift = (int) Math.round(
                    InsertionSpacingCalculator.calculateNextNoteXSs(
                        lastPastedNote, firstNoteAfter) -
                        firstNoteAfter.getXPos());
                shift = Math.max(0, shift);
            }

            for (var i = afterPasteIndex; i < line.noteCount(); i++) {
                line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
            }

            var control = scoreActions.getControl();
            scoreActions.setControl(prevPasteControl);

            for (var i = noteIndex; i < (noteIndex + copySize); i++) {
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

    /**
     * Called after a note has been inserted or modified.
     * Updates the edit note for the next insertion and triggers necessary updates.
     *
     * @param line The line that was modified
     * @param noteIndex The index of the note that was inserted/modified
     */
    public void editNoteDidChange(@NotNull Line line, int noteIndex) {
        //mainFrame.getUndoManager().undoableEditHappened(new UndoableEditEvent(this, new
        // ModifyUndoableEdit(oldNote, oldNoteInfo, xIndex)));

        // Capture the inserted note before editNote is updated for the next insertion.
        var insertedNote = line.getNote(noteIndex);
        var shouldPlayNote = playInsertingNote && insertedNote.getNoteType().isNote();

        Note nextNote;

        if (editNote.getNoteType().isGraceNote()) {
            nextNote = NoteType.GLISSANDO.newInstance();
        } else if (editNote.getNoteType() == NoteType.GLISSANDO) {
            NoteType nextNoteType;

            if (!line.getNote(noteIndex).getNoteType().isGraceNote()) {
                nextNoteType = line.getNote(noteIndex).getNoteType();
            } else if (noteIndex > 0) {
                nextNoteType = line.getNote(noteIndex - 1).getNoteType();
            } else {
                nextNoteType = NoteType.CROTCHET;
            }

            nextNote = nextNoteType.newInstance();
        } else {
            nextNote = editNote.getNoteType().newInstance();
        }

        // After inserting a note, turn off fermata and accidental parentheses
        Actions.FERMATA_ACTION.setSelected(false);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);

        // Add any other note decorations
        decorateNote(nextNote);
        scoreActions.setEditNote(nextNote);
        LyricsProcessor.spellLyrics(line);
        scoreActions.drawWidthIfWiderLine(line, false);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.SCORE,
            LayoutChangeMessage.ChangeType.CONTENT,
            false,
            line
        ));

        scoreActions.repaint();

        if (shouldPlayNote) {
            new PlayNoteThread(insertedNote.getPitch()).start();
        }
    }

}
