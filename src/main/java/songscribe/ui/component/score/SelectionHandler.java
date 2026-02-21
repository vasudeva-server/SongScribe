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

import java.awt.*;
import java.awt.event.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayNoteThread;

/**
 * Handles selection, hit-testing, and drag logic for a {@link LineComponent}.
 * <p>
 * Each {@code LineComponent} owns one instance. Drag state (rectangle, start point)
 * lives here; mouse event handlers in {@code LineComponent} delegate to this class.
 */
class SelectionHandler {

    private final LineComponent lc;
    private boolean dragging = false;
    private final Point dragStart = new Point();
    private final Rectangle dragRectangle = new Rectangle();

    SelectionHandler(@NotNull LineComponent lc) {
        this.lc = lc;
    }

    // ======================================================================
    // Accessors
    // ======================================================================

    boolean isDragging() {
        return dragging;
    }

    Rectangle getDragRectangle() {
        return dragRectangle;
    }

    // ======================================================================
    // Public delegation entry points
    // ======================================================================

    void handlePress(@NotNull MouseEvent e) {
        dragging = false;
        dragStart.setLocation(e.getPoint());
        dragRectangle.setBounds(0, 0, 0, 0);

        var lineSelectionState = lc.getLineSelectionState();

        // Don't clear selection on shift+click (preserve for extend)
        if (!e.isShiftDown() || lineSelectionState.getSelectionAnchor() == -1) {
            lc.getScore().clearSelection();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
        }
    }

    void handleDrag(@NotNull MouseEvent e) {
        dragging = true;

        // Clamp coordinates to component bounds
        var x = Math.max(0, Math.min(e.getX(), lc.getWidth() - 1));
        var y = Math.max(0, Math.min(e.getY(), lc.getHeight() - 1));

        dragRectangle.setBounds(
            Math.min(dragStart.x, x),
            Math.min(dragStart.y, y),
            Math.abs(dragStart.x - x),
            Math.abs(dragStart.y - y)
        );

        calculateLineSelectionFromDrag(dragRectangle);
        lc.repaint();
    }

    /**
     * Handles a click event for selection.
     *
     * @return true if the event was handled (selection was active), false to fall through
     */
    boolean handleClick(@NotNull MouseEvent e) {
        if (!isSelectionActive(e)) {
            return false;
        }

        var score = lc.getScore();
        var line = lc.getLine();
        var lineSelectionState = lc.getLineSelectionState();

        if (e.isShiftDown() && lineSelectionState.getSelectionAnchor() != -1) {
            // Shift+click: extend selection from anchor to clicked note
            var hitIndex = hitTestNote(e.getPoint());

            if (hitIndex != -1) {
                lineSelectionState.extendSelectionTo(hitIndex);
                score.selectionChanged();
                lc.repaint();
            }
        } else {
            score.clearSelection();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }

            calculateLineSelectionFromClick(e.getPoint());
            score.selectionChanged();

            // Play single selected note
            if (lineSelectionState.getSelectionSize() == 1) {
                var note = line.getNote(lineSelectionState.getSelectionBegin());

                if (note.getNoteType().isNote()) {
                    new PlayNoteThread(note.getPitch()).start();
                }
            }
        }

        lc.repaint();
        return true;
    }

    void handleRelease() {
        if (dragging) {
            dragging = false;
            dragRectangle.setBounds(0, 0, 0, 0);
            lc.getScore().selectionChanged();
            lc.repaint();
        }
    }

    // ======================================================================
    // Guard
    // ======================================================================

    /**
     * Returns whether selection handling should be active for the given event.
     * <p>
     * Selection is active when:
     * <ul>
     *   <li>Score is available and not in an adjustment mode</li>
     *   <li>Not playing back</li>
     *   <li>In SELECT mode (alt-click switches to SELECT mode permanently)</li>
     * </ul>
     *
     * @param e The mouse event (alt key check retained for backwards compatibility)
     * @return true if selection handling should be active
     */
    boolean isSelectionActive(@NotNull MouseEvent e) {
        var score = lc.getScore();
        var line = lc.getLine();

        if (score == null || line == null) {
            return false;
        }

        if (MidiController.isPlaying()) {
            return false;
        }

        var mode = score.getMode();

        if (mode == Mode.NOTE_ADJUSTMENT || mode == Mode.VERTICAL_ADJUSTMENT
            || mode == Mode.LYRICS_ADJUSTMENT) {
            return false;
        }

        return mode == Mode.SELECT || e.isAltDown();
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    private int hitTestNote(@NotNull Point point) {
        var line = lc.getLine();
        var helper = new Rectangle();

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);
            buildNoteHitRect(note, noteIndex, helper);

            if (helper.contains(point)) {
                return noteIndex;
            }
        }

        return -1;
    }

    private void buildNoteHitRect(@NotNull Note note, int noteIndex, @NotNull Rectangle out) {
        var line = lc.getLine();

        if (line.getBeamings().findInterval(noteIndex) != null) {
            out.setBounds(
                note.isUpper() ? NoteType.CROTCHET.getRealUpNoteRect() : NoteType.CROTCHET.getRealDownNoteRect()
            );
        } else {
            out.setBounds(
                note.isUpper() ? note.getRealUpNoteRect() : note.getRealDownNoteRect()
            );
        }

        // Get note X from LayoutResult (staff-space) and convert to pixels, so the
        // hit rect is in pixel coordinates consistent with the mouse-event dragRect.
        var layoutResult = lc.getLayoutResult();
        var noteXss = layoutResult != null ? layoutResult.getNoteXSs(note) : 0.0;
        var noteXpx = (int) Math.round(ScaleContext.getInstance().toPixels(noteXss));
        var noteY = lc.getMiddleLineYPx() + (int) (note.getStaffPosition() * Score.NOTE_Y_OFFSET_PX);
        out.translate(noteXpx, noteY - Note.HOT_SPOT.y);
    }

    private void calculateLineSelectionFromClick(@NotNull Point clickPoint) {
        var score = lc.getScore();
        var line = lc.getLine();
        var lineSelectionState = lc.getLineSelectionState();
        var coordinator = score.getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());
        lineSelectionState.clearSelection();

        var helper = new Rectangle();

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);
            buildNoteHitRect(note, noteIndex, helper);

            if (helper.contains(clickPoint)) {
                lineSelectionState.setSelectionFromClick(noteIndex);
                return;
            }
        }

        // No note was hit — check proximity to staff lines for line selection
        var clickYss = ScaleContext.getInstance().fromPixels(clickPoint.y);

        if (Math.abs(clickYss - lc.getMiddleLineYSs()) <= 2.0) {
            lineSelectionState.setLineSelected(true);
        }
    }

    private void calculateLineSelectionFromDrag(@NotNull Rectangle dragRect) {
        var score = lc.getScore();
        var line = lc.getLine();
        var lineSelectionState = lc.getLineSelectionState();
        var coordinator = score.getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());
        lineSelectionState.clearSelection();

        var helper = new Rectangle();

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);
            buildNoteHitRect(note, noteIndex, helper);

            if (dragRect.intersects(helper)) {
                lineSelectionState.extendSelection(noteIndex);
            }
        }

        // Set anchor to the selection end nearest the drag start point
        if (lineSelectionState.hasNoteSelection()) {
            var anchorIndex = hitTestNote(dragStart);

            if (anchorIndex != -1) {
                lineSelectionState.setSelectionAnchor(anchorIndex);
            } else {
                var begin = lineSelectionState.getSelectionBegin();
                var end = lineSelectionState.getSelectionEnd();
                var beginNote = line.getNote(begin);
                var endNote = line.getNote(end);
                var distToBegin = Math.abs(dragStart.x - beginNote.getXPosSs());
                var distToEnd = Math.abs(dragStart.x - endNote.getXPosSs());
                lineSelectionState.setSelectionAnchor(distToBegin <= distToEnd ? begin : end);
            }
        }
    }
}
