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

package songscribe.ui.selection;

import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;

/**
 * Per-line selection state and query methods.
 * <p>
 * Each LineComponent owns a LineSelectionState that tracks which elements (if any)
 * are selected on that line, and whether the line itself is selected for deletion.
 */
public final class LineSelectionState {

    private final Line line;
    private Runnable selectionChangeCallback = () -> {};

    private int selectionBegin = -1;
    private int selectionEnd = -1;
    private int selectionAnchor = -1;
    private boolean lineSelected = false;
    private int selectedSlideElementIndex = -1;
    @Nullable
    private Ending selectedEnding = null;

    @Nullable
    private Boolean canTie = null;

    /** The existing {@link Tie} for the current selection, or {@code null} if none (add mode). */
    @Nullable
    private Tie existingTie = null;

    public LineSelectionState(Line line) {
        this.line = line;
    }

    // -------------------------------------------------------------------------
    // State accessors
    // -------------------------------------------------------------------------

    public int getSelectionBegin() {
        return selectionBegin;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    public boolean isLineSelected() {
        return lineSelected;
    }

    /**
     * Clears the slide and ending selections.
     * <p>
     * Slides and endings are decorations selected on their own, mutually exclusive with
     * each other and with any element or line selection. Every method that establishes a
     * different selection clears both, so they are cleared together here.
     */
    private void clearDecorationSelections() {
        selectedSlideElementIndex = -1;
        selectedEnding = null;
    }

    public void setLineSelected(boolean lineSelected) {
        this.lineSelected = lineSelected;

        if (lineSelected) {
            clearDecorationSelections();
        }

        selectionChangeCallback.run();
    }

    /**
     * Returns whether a slide is selected on this line.
     */
    public boolean hasSlideSelection() {
        return selectedSlideElementIndex != -1;
    }

    /**
     * Returns the element index of the selected slide, or -1 if none.
     */
    public int getSelectedSlideElementIndex() {
        return selectedSlideElementIndex;
    }

    /**
     * Selects the slide owned by the element at the given index,
     * clearing any element or line selection.
     */
    public void selectSlide(int elementIndex) {
        clearDecorationSelections();
        selectedSlideElementIndex = elementIndex;
        selectionBegin = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
        lineSelected = false;
        selectionChangeCallback.run();
    }

    /**
     * Returns whether the slide at the given element index is selected.
     */
    public boolean isSlideSelected(int elementIndex) {
        return selectedSlideElementIndex == elementIndex;
    }

    /**
     * Returns whether an ending is selected on this line.
     */
    public boolean hasEndingSelection() {
        return selectedEnding != null;
    }

    /**
     * Returns the selected ending, or null if none.
     */
    @Nullable
    public Ending getSelectedEnding() {
        return selectedEnding;
    }

    /**
     * Selects the given ending, clearing any element, line, or slide selection.
     */
    public void selectEnding(Ending ending) {
        clearDecorationSelections();
        selectedEnding = ending;
        selectionBegin = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
        lineSelected = false;
        selectionChangeCallback.run();
    }

    /**
     * Returns whether the given ending is selected.
     */
    public boolean isEndingSelected(Ending ending) {
        return selectedEnding == ending;
    }

    /**
     * Clears the slide or ending selection if it no longer refers to a live decoration
     * on this line — e.g. after an undo/redo that shifted element indices or removed
     * the selected ending outright. No-op if the current selection is still valid, or
     * if there is no slide/ending selection.
     *
     * @return whether the selection was cleared
     */
    public boolean revalidateDecorationSelection() {
        if (selectedSlideElementIndex != -1) {
            if (selectedSlideElementIndex >= line.effectiveElementCount()
                    || line.getElement(selectedSlideElementIndex).getSlide() == null) {
                clearSelection();
                return true;
            }
        } else if (selectedEnding != null) {
            if (!LineEndingSupport.findEndings(line).contains(selectedEnding)) {
                clearSelection();
                return true;
            }
        }

        return false;
    }

    @Nullable
    public Boolean getCanTie() {
        return canTie;
    }

    /**
     * Returns the existing {@link Tie} covering the current selection when the tie
     * should be removed (toggle-off), or {@code null} when a new tie should be added.
     * <p>
     * Only valid after a call to {@link #canToggleTie()} that returned {@code true}.
     */
    @Nullable
    public Tie getExistingTie() {
        return existingTie;
    }

    public void resetTieState() {
        canTie = null;
        existingTie = null;
    }

    public Line getLine() {
        return line;
    }

    void setSelectionChangeCallback(Runnable selectionChangeCallback) {
        this.selectionChangeCallback = selectionChangeCallback;
    }

    // -------------------------------------------------------------------------
    // Selection state methods
    // -------------------------------------------------------------------------

    /**
     * Clears the selection state on this line.
     */
    public void clearSelection() {
        selectionBegin = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
        lineSelected = false;
        clearDecorationSelections();
        selectionChangeCallback.run();
    }

    /**
     * Selects all elements on this line, excluding the song's
     * auto-maintained terminal.
     */
    public void selectAll() {
        var end = line.effectiveElementCount() - 1;

        if (end < 0) {
            return;
        }

        selectionBegin = 0;
        selectionEnd = end;
        selectionAnchor = 0;
        clearDecorationSelections();
        selectionChangeCallback.run();
    }

    /**
     * Returns whether any elements are selected on this line.
     */
    public boolean hasElementSelection() {
        return selectionBegin != -1;
    }

    /**
     * Returns whether the element at the given index is selected.
     */
    public boolean isElementSelected(int elementIndex) {
        return (elementIndex >= 0) && (selectionBegin <= elementIndex) && (elementIndex <= selectionEnd);
    }

    /**
     * Returns the number of elements in the current selection.
     */
    public int getSelectionSize() {
        if (selectionBegin == -1) {
            return 0;
        }

        return (selectionEnd - selectionBegin) + 1;
    }

    /**
     * Returns the current selection, or null if nothing is selected.
     */
    @Nullable
    public ElementSelection getSelection() {
        if (lineSelected) {
            if (line.effectiveElementCount() == 0) {
                return null;
            }

            return new ElementSelection(line, 0, line.effectiveElementCount() - 1);
        }

        if (selectionBegin != -1) {
            return new ElementSelection(line, selectionBegin, selectionEnd);
        }

        return null;
    }

    /**
     * Returns the single selected element if exactly one element is selected,
     * or null otherwise.
     */
    @Nullable
    public StaffElement getSingleSelectedElement() {
        if ((selectionBegin != -1) && (selectionBegin == selectionEnd)) {
            return line.getElement(selectionBegin);
        }

        return null;
    }

    /**
     * Sets the selection from a single click on an element.
     */
    public void setSelectionFromClick(int elementIndex) {
        selectionBegin = elementIndex;
        selectionEnd = elementIndex;
        selectionAnchor = elementIndex;
        clearDecorationSelections();
        selectionChangeCallback.run();
    }

    /**
     * Returns the selection anchor index, or -1 if no anchor is set.
     */
    public int getSelectionAnchor() {
        return selectionAnchor;
    }

    /**
     * Sets the selection anchor independently (used by drag selection).
     */
    public void setSelectionAnchor(int elementIndex) {
        selectionAnchor = elementIndex;
    }

    /**
     * Extends the selection from the anchor to the given element index.
     * The anchor stays unchanged.
     */
    public void extendSelectionTo(int elementIndex) {
        if (selectionAnchor == -1) {
            return;
        }

        selectionBegin = Math.min(selectionAnchor, elementIndex);
        selectionEnd = Math.max(selectionAnchor, elementIndex);
        clearDecorationSelections();
        selectionChangeCallback.run();
    }

    /**
     * Extends the selection to include the given element index (for drag selection).
     * If no selection exists yet, starts the selection at that index.
     */
    public void extendSelection(int elementIndex) {
        if (selectionBegin == -1) {
            selectionBegin = elementIndex;
        }

        selectionEnd = elementIndex;
        clearDecorationSelections();
        selectionChangeCallback.run();
    }

    /**
     * Sets the selection to the given inclusive range directly, moving the anchor to
     * {@code begin}. Used to re-derive a valid selection after a mutation — such as a
     * grace-note collapse — has shifted element indices out from under it.
     */
    public void setSelectionRange(int begin, int end) {
        selectionBegin = begin;
        selectionEnd = end;
        selectionAnchor = begin;
        clearDecorationSelections();
        selectionChangeCallback.run();
    }

    /**
     * Resets selection begin/end to -1 without touching lineSelected.
     * Used before recalculating selection from drag.
     */
    public void resetElementSelection() {
        selectionBegin = -1;
        selectionEnd = -1;
        selectionChangeCallback.run();
    }

    // -------------------------------------------------------------------------
    // Query methods for UI state
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the first element in the selection that is not a grace note,
     * or -1 if the selection is empty or holds nothing but grace notes.
     *
     * <p>Grace notes are transparent to beams and tuplets: the group spans the selection's
     * non-grace endpoints and any grace note in between stays outside the group, so a
     * grace/host pair may sit inside a beamed or tupleted selection (refs #592).
     */
    public int getNonGraceSelectionBegin() {
        if (!hasElementSelection()) {
            return -1;
        }

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            if (!line.getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns the index of the last element in the selection that is not a grace note,
     * or -1 if the selection is empty or holds nothing but grace notes.
     *
     * @see #getNonGraceSelectionBegin()
     */
    public int getNonGraceSelectionEnd() {
        if (!hasElementSelection()) {
            return -1;
        }

        for (var i = selectionEnd; i >= selectionBegin; i--) {
            if (!line.getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns whether the current selection can be beamed/unbeamed.
     */
    public boolean canToggleBeaming() {
        var beginIndex = getNonGraceSelectionBegin();
        var endIndex = getNonGraceSelectionEnd();

        // Fewer than two non-grace elements — there is nothing to join.
        if (beginIndex < 0 || beginIndex == endIndex) {
            return false;
        }

        //noinspection SimplifiableIfStatement
        if (!IntStream.rangeClosed(beginIndex, endIndex).allMatch(i -> {
            var type = line.getElement(i).getType();
            return type.isGraceNote() || type.isBeamable();
        })) {
            return false;
        }

        // Conflict: beaming would connect what a tie already connects.
        return !(shouldConnectBeamSelection(beginIndex, endIndex)
            && !shouldConnectTieSelection(beginIndex, endIndex));
    }

    /**
     * Returns whether the current selection can toggle a tie.
     * Also sets the {@code canTie} and {@code existingTie} fields.
     */
    public boolean canToggleTie() {
        if (getSelectionSize() != 2) {
            canTie = false;
            return false;
        }

        var beginNote = line.getElement(selectionBegin);
        var endNote = line.getElement(selectionEnd);

        if (!beginNote.getType().isPitchedNote() || !endNote.getType().isPitchedNote()) {
            canTie = false;
            return false;
        }

        if (beginNote.getPitch() != endNote.getPitch()) {
            canTie = false;
            return false;
        }

        var exactTie = line.findExactTie(selectionBegin, selectionEnd);
        var shouldConnect = exactTie == null;

        // Conflict: tying would connect what a beam already connects. The raw selection bounds
        // are correct here, unlike beaming/tupleting: a tie needs exactly two elements, so there
        // is no interior for a grace note to sit in, and the isPitchedNote() check above already
        // rejects a grace note at either endpoint (refs #592).
        if (shouldConnect && !shouldConnectBeamSelection(selectionBegin, selectionEnd)) {
            canTie = false;
            return false;
        }

        canTie = true;
        existingTie = exactTie;
        return true;
    }

    /**
     * Returns a {@link TupletToggleInfo} describing whether the selection can be
     * tupleted/untupleted, which tuplet currently covers the selection start, and
     * whether the selection covers that tuplet's full span.
     */
    @SuppressWarnings("ObjectEquality")
    public TupletToggleInfo canToggleTuplet() {
        var beginIndex = getNonGraceSelectionBegin();
        var endIndex = getNonGraceSelectionEnd();

        // Fewer than two non-grace elements — there is nothing to group.
        if (beginIndex < 0 || beginIndex == endIndex) {
            return new TupletToggleInfo(false, null, false);
        }

        Tuplet firstTuplet = null;

        for (var i = beginIndex; i <= endIndex; i++) {
            var type = line.getElement(i).getType();

            // A grace note rides along inside the span without joining the tuplet.
            if (type.isGraceNote()) {
                continue;
            }

            if (!type.isPitchedNote()) {
                return new TupletToggleInfo(false, null, false);
            }

            var currentTuplet = line.findTupletAt(i);

            if (i == beginIndex) {
                firstTuplet = currentTuplet;
            } else if (currentTuplet != firstTuplet) {
                return new TupletToggleInfo(false, null, false);
            }
        }

        var coversExisting = (firstTuplet != null)
            && (beginIndex == firstTuplet.getAnchorElementIndex())
            && (endIndex == firstTuplet.getEndElementIndex());

        return new TupletToggleInfo(true, firstTuplet, coversExisting);
    }

    /**
     * Returns whether the current selection can toggle trill.
     */
    public boolean canToggleTrill() {
        return selectionBegin != -1 &&
            line.getElements(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(element -> element.getType().isPitchedNote());
    }

    /**
     * Returns whether the stem direction can be modified, either by flipping it
     * or by restoring it to automatic.
     */
    public boolean canModifyStemDirection() {
        return getSelectionSize() != 0 &&
            line.getElements(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(element -> !element.getType().isRest());
    }

    /**
     * Returns whether a new beam should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing beam (remove mode).
     */
    private boolean shouldConnectBeamSelection(int beginIndex, int endIndex) {
        var beginBeam = line.findBeamAt(beginIndex);
        var endBeam = line.findBeamAt(endIndex);

        //noinspection ObjectEquality
        return (beginBeam == null) || (beginBeam != endBeam);
    }

    /**
     * Returns whether a new tie should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing tie (remove mode).
     */
    private boolean shouldConnectTieSelection(int beginIndex, int endIndex) {
        var beginTie = line.findTieAt(beginIndex);
        var endTie = line.findTieAt(endIndex);

        //noinspection ObjectEquality
        return (beginTie == null) || (beginTie != endTie);
    }
}
