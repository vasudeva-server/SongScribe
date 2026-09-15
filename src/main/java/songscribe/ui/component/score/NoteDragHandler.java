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

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.ViewPx;
import songscribe.engraving.Staff;
import songscribe.hit.HitTarget;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Mode;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;

/**
 * Handles press/drag/release for pitch-dragging a note head in SELECT mode, driving
 * {@link PitchShifter}'s single definition of moving a selection's pitch live across many mouse
 * steps. A dragged note has no pitch of its own at the staff position it lands on: it adopts
 * whatever the song's chain walk decides is in force there once the drag is committed on
 * release. There is no prompt and no fit gate, and the dragged anchor is played only after that
 * walk has run.
 * <p>
 * One instance per {@link LineComponent}. The owning component delegates its
 * mouse events here before passing them on to other handlers.
 */
class NoteDragHandler {

    private final LineComponent lc;

    private boolean dragActive = false;
    private boolean dragMoved = false;
    private boolean pressPreservedMultiSelection = false;
    private int dragElementIndex = -1;

    /** Original staff position of the directly dragged note — used to compute deltaSp. */
    private int originalDragStaffPositionSp;

    /**
     * Screen-absolute mouse Y (pixels) at drag start. The drag tracks mouse movement as a
     * delta from this anchor in screen coordinates, so an in-drag layout reflow — which can
     * shift both the staff within the line component and the line component within its
     * parent — cannot perturb the mouse→staff-position mapping.
     */
    private int dragPressScreenYPx;

    private int lastPlayedStaffPositionSp;
    @Nullable
    private Line dragLine;

    /** All notes (plus tie-chain expansions) that move together during drag. */
    private final List<PitchShifter.PitchShiftEntry> dragGroup = new ArrayList<>();

    /**
     * The components of the lines other than this one that the drag group reaches, resolved
     * once at press. Non-empty only for a group formed across a cross-line tie.
     * <p>
     * Resolved at press rather than per drag event because the group is fixed for the whole
     * gesture, while {@code handleDrag} runs on every mouse movement that changes the pitch.
     * Each entry appears once however many of the group's notes sit on it, so one line is not
     * invalidated repeatedly.
     */
    private final List<LineComponent> foreignLineComponents = new ArrayList<>();

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
     * <p>
     * Takes the hit target the caller already resolved for this press rather than
     * hit-testing the point again. Reading the resolved target is also what keeps a lyric out
     * of this path: a lyric outranks an element, so a press on lyric text arrives here as a
     * lyric target and is declined, and text drawn over an element's rectangle cannot start a
     * pitch drag.
     */
    boolean handlePress(MouseEvent e, @Nullable HitTarget hitTarget) {
        var scoreView = lc.getScoreView();

        if (scoreView.getMode() != Mode.SELECT) {
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

        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        var hitIndex = LineComponent.elementIndexOf(hitTarget, line);

        if (hitIndex < 0) {
            return false;
        }

        if (!(line.getElement(hitIndex) instanceof NoteElement note)) {
            return false;
        }

        var coordinator = lc.getScoreView().getSelectionCoordinator();
        var range = coordinator.getRange();

        ElementRange dragRange;

        if (range != null && coordinator.isElementSelected(hitIndex, lc.getLineIndex())) {
            // Note is already part of the selection — preserve it for a potential drag.
            // Capture the existing selection range so the drag group spans all
            // originally-selected notes even after a collapse on release.
            dragRange = range.range();
            lc.getSelectionHandler().playNoteIfPitched(hitIndex);
            pressPreservedMultiSelection = true;
        } else {
            // Note is not in the current selection — replace selection with just this note.
            // Drag group is only the clicked note.
            dragRange = ElementRange.single(hitIndex);
            lc.getSelectionHandler().selectAndPlayElement(hitIndex);
            pressPreservedMultiSelection = false;
        }

        // Build the drag group from the captured selection range (which may be multi-note).
        // The press gate above admits any note (isNote), and buildPitchShiftGroup includes
        // every note in the range, so the group is never empty for an admitted hit.
        dragGroup.clear();
        dragGroup.addAll(PitchShifter.buildPitchShiftGroup(line, dragRange));
        resolveForeignLineComponents();

        // Save state for the drag and for a press+release without drag
        dragElementIndex = hitIndex;
        originalDragStaffPositionSp = note.getStaffPosition().value();
        lastPlayedStaffPositionSp = originalDragStaffPositionSp;
        dragLine = line;
        dragPressScreenYPx = e.getYOnScreen();

        PreviewElementManager.clearPreviewElement();
        EditModeManager.setPreviewElementVisible(false);

        dragActive = true;
        lc.repaint();
        return true;
    }

    /**
     * Handles mouse drag while a pitch-drag is active.
     */
    void handleDrag(MouseEvent e) {
        // Track mouse movement in screen-absolute pixels. Using a screen-Y delta instead of
        // a component-local coordinate decouples the drag from any layout reflow — neither
        // a change to aboveStaffSs (which shifts the staff within the line component) nor
        // a reposition of the line component within its parent can perturb the mapping.
        var deltaYPx = e.getYOnScreen() - dragPressScreenYPx;
        // The screen-Y delta is in view pixels; the view scale folds the zoom factor and the
        // fixed document scale into a single conversion, so a drag tracks on-screen motion 1:1.
        var deltaYSs = lc.getViewScale().toSs(new ViewPx(deltaYPx)).value();
        // A count of half spaces travelled, not a position: the mouse has moved this far from
        // where it was pressed, which says nothing about where on the grid that lands. What
        // bounds the travel is the group's own room to move, and clampDelta below is what
        // knows it.
        var deltaSp = Staff.ssToHalfSpaces(deltaYSs);
        var newPositionSp = originalDragStaffPositionSp + deltaSp;

        if (newPositionSp == lastPlayedStaffPositionSp) {
            return;
        }

        if (dragLine == null) {
            return;
        }

        // Clamp delta so no note in the group exits the valid staff range
        var clampedDelta = PitchShifter.clampDelta(dragGroup, deltaSp);

        // If the clamped delta lands on the last-played position, skip so a stationary
        // mouse does not retrigger the note.
        if (originalDragStaffPositionSp + clampedDelta == lastPlayedStaffPositionSp) {
            return;
        }

        // Shared with arrow-key shifting: move the whole group to its new staff positions.
        PitchShifter.moveGroup(dragLine, dragGroup, dragElementIndex, clampedDelta);

        lastPlayedStaffPositionSp = originalDragStaffPositionSp + clampedDelta;

        invalidateDraggedLines();
        dragMoved = true;
    }

    /**
     * Marks every line this drag moved a note on as needing a fresh layout.
     * <p>
     * A group formed across a cross-line tie moves the partner note in the adjacent line too,
     * and that line's tie geometry lives in its own cached {@code LayoutResult}. Repainting it
     * is not enough: the note head is redrawn at its new staff position while the tie is drawn
     * from the stale cached curve, leaving the tie pointing at where the note used to be. Only
     * the layout being recomputed puts the two back together.
     */
    private void invalidateDraggedLines() {
        lc.invalidateLayout();

        for (var lineComponent : foreignLineComponents) {
            lineComponent.invalidateLayout();
        }
    }

    /**
     * Resolves {@link #foreignLineComponents} for the group this press just built. Called once
     * per press; see that field for why it is not done per drag event.
     */
    private void resolveForeignLineComponents() {
        foreignLineComponents.clear();

        var ownLine = lc.getLine();
        var scoreView = lc.getScoreView();
        var seen = new LinkedHashSet<Line>();

        for (var entry : dragGroup) {
            var entryLine = entry.line();

            //noinspection ObjectEquality
            if (entryLine == ownLine || !seen.add(entryLine)) {
                continue;
            }

            var lineComponent = scoreView.getLineComponent(entryLine.index());

            if (lineComponent != null) {
                foreignLineComponents.add(lineComponent);
            }
        }
    }

    /**
     * Handles mouse release, committing the pitch change the drag already applied live.
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
            // The pitch mutations were already applied live during handleDrag, so
            // commitPitchShift records each PITCH/ACCIDENTAL ElementModification with the
            // press-time beforeClone, then the grace-note cleanup — one bracket, one undo step.
            PitchShifter.commitPitchShift(dragLine, dragGroup);

            // Nothing sounds during the live drag itself; the anchor is played only now, once
            // the song's chain walk run by commitPitchShift has decided what it sounds.
            if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
                PlayThread.sendNoteOn(dragLine.noteAt(dragElementIndex).getPitch());
                PitchShifter.scheduleAnchorNoteOff(dragLine, dragElementIndex);
            }
        }

        dragActive = false;
        dragElementIndex = -1;
        dragLine = null;
        dragGroup.clear();
        foreignLineComponents.clear();

        PreviewElementManager.restorePreviewElement(lc);
    }
}
