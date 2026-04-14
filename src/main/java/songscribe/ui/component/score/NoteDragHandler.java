/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component.score;

import module java.desktop;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;

/**
 * Handles press/drag/release for pitch-dragging a note head in SELECT mode.
 * <p>
 * One instance per {@link LineComponent}. The owning component delegates its
 * mouse events here before passing them on to other handlers.
 */
class NoteDragHandler {

    /**
     * Captures the original state of a single note in the drag group.
     * {@code beforeClone} is a snapshot taken at press time, used as the
     * {@link ElementModification#beforeElement()} for the eventual mutation
     * record (the actual element is mutated incrementally during drag).
     */
    private record DragEntry(int index, int originalStaffPositionSp, boolean originalUpper, StaffElement beforeClone) {}

    private final LineComponent lc;

    private boolean dragActive = false;
    private boolean dragMoved = false;
    private boolean pressHandled = false;
    private boolean pressPreservedMultiSelection = false;
    private int dragElementIndex = -1;

    /** Original staff position of the directly dragged note — used to compute deltaSp. */
    private int originalDragStaffPositionSp;

    private int lastPlayedStaffPositionSp;
    @Nullable
    private Line dragLine;

    /** All notes (plus tie-chain expansions) that move together during drag. */
    private final List<DragEntry> dragGroup = new ArrayList<>();

    NoteDragHandler(LineComponent lc) {
        this.lc = lc;
    }

    // ======================================================================
    // Accessors
    // ======================================================================

    boolean isDragActive() {
        return dragActive;
    }

    int getDragElementIndex() {
        return dragElementIndex;
    }

    // ======================================================================
    // Mouse event handlers
    // ======================================================================

    /**
     * Handles a mouse press. Returns {@code true} if a pitch-drag was initiated
     * and the event should not be processed further.
     */
    boolean handlePress(MouseEvent e) {
        pressHandled = false;

        var score = lc.getScore();

        if (score == null || score.getMode() != Mode.SELECT) {
            return false;
        }

        if (MidiController.isPlaying()) {
            return false;
        }

        // Shift+click extends selection — let the selection handler manage it
        if (e.isShiftDown()) {
            return false;
        }

        dragMoved = false;

        var hitIndex = ElementHitTest.hitTestElement(lc, e.getPoint());

        if (hitIndex == -1) {
            return false;
        }

        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        var note = line.getElement(hitIndex);

        if (!note.getType().isNote()) {
            return false;
        }

        var selectionState = lc.getLineSelectionState();

        int dragBegin;
        int dragEnd;

        if (selectionState != null && selectionState.isElementSelected(hitIndex)) {
            // Note is already part of the selection — preserve it for a potential drag.
            // Capture the existing selection range so the drag group spans all
            // originally-selected notes even after a collapse on release.
            dragBegin = selectionState.getSelectionBegin();
            dragEnd = selectionState.getSelectionEnd();
            lc.getSelectionHandler().playNoteIfPitched(hitIndex);
            pressPreservedMultiSelection = true;
        } else {
            // Note is not in the current selection — replace selection with just this note.
            // Drag group is only the clicked note.
            dragBegin = hitIndex;
            dragEnd = hitIndex;
            lc.getSelectionHandler().selectAndPlayElement(hitIndex);
            pressPreservedMultiSelection = false;
        }

        // Build the drag group from the captured selection range (which may be multi-note)
        dragGroup.clear();

        // Collect all unique indices, expanding each selected note's tie chain
        var groupIndices = new LinkedHashSet<Integer>();

        for (var i = dragBegin; i <= dragEnd; i++) {
            var element = line.getElement(i);

            if (!element.getType().isNote()) {
                continue;
            }

            var tie = line.getTies().findInterval(i);

            if (tie != null) {
                for (var j = tie.getStart(); j <= tie.getEnd(); j++) {
                    groupIndices.add(j);
                }
            } else {
                groupIndices.add(i);
            }
        }

        // Fall back to just the dragged note if nothing was collected
        if (groupIndices.isEmpty()) {
            groupIndices.add(hitIndex);
        }

        for (var idx : groupIndices) {
            var groupNote = line.getElement(idx);
            dragGroup.add(new DragEntry(idx, groupNote.getStaffPosition(), groupNote.isUpper(), groupNote.clone()));
        }

        // Save state for possible revert on a press+release without drag
        dragElementIndex = hitIndex;
        originalDragStaffPositionSp = note.getStaffPosition();
        lastPlayedStaffPositionSp = originalDragStaffPositionSp;
        dragLine = line;

        PreviewElementManager.clearPreviewElement();

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setPreviewElementVisible(false);
        }

        pressHandled = true;
        dragActive = true;
        lc.repaint();
        return true;
    }

    /**
     * Handles mouse drag while a pitch-drag is active.
     */
    void handleDrag(MouseEvent e) {
        var mouseYss = ScaleContext.getInstance().fromPixels(e.getY());
        var newPositionSp = PreviewElementManager.calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

        if (newPositionSp == lastPlayedStaffPositionSp) {
            return;
        }

        if (dragLine == null) {
            return;
        }

        // Compute raw delta from the dragged note's original position
        var deltaSp = newPositionSp - originalDragStaffPositionSp;

        // Clamp delta so no note in the group exits the valid staff range
        var minDelta = Integer.MIN_VALUE;
        var maxDelta = Integer.MAX_VALUE;

        for (var entry : dragGroup) {
            minDelta = Math.max(minDelta, PreviewElementManager.MIN_STAFF_POSITION_SP - entry.originalStaffPositionSp());
            maxDelta = Math.min(maxDelta, PreviewElementManager.MAX_STAFF_POSITION_SP - entry.originalStaffPositionSp());
        }

        deltaSp = Math.clamp(deltaSp, minDelta, maxDelta);

        // If the clamped delta produces no movement, skip
        if (originalDragStaffPositionSp + deltaSp == lastPlayedStaffPositionSp) {
            return;
        }

        boolean playSelected = Prefs.getInstance().getBoolean(PrefsKey.PLAY_SELECTED_NOTE);

        // Send NOTE_OFF for the pitch we were playing
        var oldNote = dragLine.getElement(dragElementIndex);

        if (playSelected) {
            PlayThread.sendNoteOff(oldNote.getPitch());
        }

        // Apply clamped delta to all group entries
        for (var entry : dragGroup) {
            var groupNote = dragLine.getElement(entry.index());
            groupNote.setStaffPosition(entry.originalStaffPositionSp() + deltaSp);
            groupNote.setUpper(Score.defaultUpperNote(groupNote));
        }

        // Play NOTE_ON for the new pitch of the dragged note
        var newPitch = dragLine.getElement(dragElementIndex).getPitch();

        if (playSelected) {
            PlayThread.sendNoteOn(newPitch);
        }
        lastPlayedStaffPositionSp = originalDragStaffPositionSp + deltaSp;

        lc.invalidateLayout();
        lc.repaint();
        dragMoved = true;
    }

    /**
     * Handles mouse release, finalizing or reverting the pitch change.
     */
    void handleRelease() {
        if (dragLine == null) {
            return;
        }

        if (!dragMoved && pressPreservedMultiSelection) {
            // Click without drag on a preserved selection — collapse to the clicked note.
            // Use selectElementAtIndex (not selectAndPlayElement) so we don't re-play the note
            // that was already played on press. Explicit repaint ensures the visual updates
            // immediately — selectionChanged() posts a message but does not trigger a LineComponent repaint.
            lc.getSelectionHandler().selectElementAtIndex(dragElementIndex);
            lc.repaint();
        }

        if (dragMoved) {
            if (Prefs.getInstance().getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
                // The last drag noteOn is still sounding — schedule a noteOff after the standard duration
                new PlayThread(dragLine.getElement(dragElementIndex).getPitch(), false).start();
            }

            // Coalesce all finalize mutations into a single CompositionDidChangeNotification.
            // The pitch mutations were already applied during handleDrag, so each PITCH
            // ElementModification carries an empty mutator and the press-time beforeClone.
            // The follow-up cleanup steps (glissando removal, grace-note removal) emit
            // their own mutations into the same bracket.
            var line = dragLine;
            line.withModification(() -> {
                for (var entry : dragGroup) {
                    line.applyChange(
                            new ElementModification(line, entry.index(), EnumSet.of(ElementField.PITCH), entry.beforeClone()),
                            () -> {}
                    );
                }

                // Remove connected glissandos that became unison after the pitch drag
                for (var entry : dragGroup) {
                    removeUnisonConnectedGlissandos(line, entry.index());
                }

                // Grace note validity checks — iterate in reverse index order to avoid index shifting from removals
                var sortedEntries = dragGroup.stream()
                        .sorted((a, b) -> Integer.compare(b.index(), a.index()))
                        .toList();

                for (var entry : sortedEntries) {
                    var idx = entry.index();
                    var element = line.getElement(idx);

                    if (element.getType().isGraceNote()
                            && idx + 1 < line.elementCount()
                            && element.getPitch() == line.getElement(idx + 1).getPitch()) {
                        // Grace note dragged to the same pitch as its following note — remove the grace note
                        OptionDialogs.showWarningMessage(
                                null,
                                Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                                Strings.WARNING_GRACE_NOTE_SAME_PITCH
                        );
                        line.removeElement(idx);
                    } else if (!element.getType().isGraceNote()) {
                        // Host note dragged to the same pitch as its preceding grace note — remove the grace note
                        int graceIdx = line.precedingGraceNoteIndex(idx);

                        if (graceIdx >= 0
                                && line.getElement(graceIdx).getPitch() == element.getPitch()) {
                            OptionDialogs.showWarningMessage(
                                    null,
                                    Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                                    Strings.WARNING_GRACE_NOTE_SAME_PITCH
                            );
                            line.removeElement(graceIdx);
                        }
                    }
                }
            });
            // TODO: push to undo stack when undo system is re-enabled
        }

        dragActive = false;
        dragElementIndex = -1;
        dragLine = null;
        dragGroup.clear();

        PreviewElementManager.restorePreviewElement(lc);
    }

    /**
     * Removes connected glissandos that became unison after a pitch drag.
     * Checks the glissando FROM the dragged note (to the next note) and
     * the glissando TO the dragged note (from the previous note). Each
     * removal is emitted as a {@link ElementModification} carrying the
     * pre-removal clone of the affected element. Must be called inside an
     * open modification bracket on {@code line.getComposition()}.
     */
    private static void removeUnisonConnectedGlissandos(Line line, int elementIndex) {
        var element = line.getElement(elementIndex);

        // Glissando FROM the dragged note to the next note
        var glissando = element.getGlissando();

        if (glissando != null
            && glissando.type == StaffElement.Glissando.Type.CONNECTED
            && elementIndex + 1 < line.elementCount()
            && element.getPitch() == line.getElement(elementIndex + 1).getPitch()) {
            line.modifyElement(elementIndex, ElementField.GLISSANDO, element::removeGlissando);
        }

        // Glissando TO the dragged note from the previous note
        if (elementIndex > 0) {
            var prev = line.getElement(elementIndex - 1);
            var prevGlissando = prev.getGlissando();

            if (prevGlissando != null
                && prevGlissando.type == StaffElement.Glissando.Type.CONNECTED
                && prev.getPitch() == element.getPitch()) {
                line.modifyElement(elementIndex - 1, ElementField.GLISSANDO, prev::removeGlissando);
            }
        }
    }
}
