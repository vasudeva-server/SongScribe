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

import songscribe.model.Line;
import songscribe.model.StaffElement;
import songscribe.ui.layout.Tie;
import songscribe.ui.layout.Tuplet;

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
    private int selectedGlissandoElementIndex = -1;

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

    public void setLineSelected(boolean lineSelected) {
        this.lineSelected = lineSelected;

        if (lineSelected) {
            selectedGlissandoElementIndex = -1;
        }

        selectionChangeCallback.run();
    }

    /**
     * Returns whether a glissando is selected on this line.
     */
    public boolean hasGlissandoSelection() {
        return selectedGlissandoElementIndex != -1;
    }

    /**
     * Returns the element index of the selected glissando, or -1 if none.
     */
    public int getSelectedGlissandoElementIndex() {
        return selectedGlissandoElementIndex;
    }

    /**
     * Selects the glissando owned by the element at the given index,
     * clearing any element or line selection.
     */
    public void selectGlissando(int elementIndex) {
        selectedGlissandoElementIndex = elementIndex;
        selectionBegin = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
        lineSelected = false;
        selectionChangeCallback.run();
    }

    /**
     * Returns whether the glissando at the given element index is selected.
     */
    public boolean isGlissandoSelected(int elementIndex) {
        return selectedGlissandoElementIndex == elementIndex;
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
        selectedGlissandoElementIndex = -1;
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
        selectedGlissandoElementIndex = -1;
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
        selectedGlissandoElementIndex = -1;
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
        selectedGlissandoElementIndex = -1;
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
        selectedGlissandoElementIndex = -1;
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
     * Returns whether the current selection can be beamed/unbeamed.
     */
    public boolean canToggleBeaming() {
        if (getSelectionSize() < 2) {
            return false;
        }

        //noinspection SimplifiableIfStatement
        if (!IntStream.rangeClosed(selectionBegin, selectionEnd).allMatch(
                i -> line.getElement(i).getType().isBeamable())) {
            return false;
        }

        // Conflict: beaming would connect what a tie already connects.
        return !(shouldConnectBeamSelection() && !shouldConnectTieSelection());
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

        Tie firstTie = null;
        Integer firstPitch = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            var note = line.getElement(i);

            if (!note.getType().isPitchedNote()) {
                canTie = false;
                return false;
            }

            if (firstPitch == null) {
                firstPitch = note.getPitch();
            } else if (note.getPitch() != firstPitch) {
                canTie = false;
                return false;
            }

            if (i == selectionBegin) {
                firstTie = line.findTieAt(i);
            } else {
                //noinspection ObjectEquality
                if (line.findTieAt(i) != firstTie) {
                    canTie = false;
                    return false;
                }
            }
        }

        // Conflict: tying would connect what a beam already connects.
        if (shouldConnectTieSelection() && !shouldConnectBeamSelection()) {
            canTie = false;
            return false;
        }

        canTie = true;
        existingTie = firstTie;
        return true;
    }

    /**
     * Returns a {@link TupletToggleInfo} describing whether the selection can be
     * tupleted/untupleted, which tuplet currently covers the selection start, and
     * whether the selection covers that tuplet's full span.
     */
    @SuppressWarnings("ObjectEquality")
    public TupletToggleInfo canToggleTuplet() {
        if (getSelectionSize() < 2) {
            return new TupletToggleInfo(false, null, false);
        }

        Tuplet firstTuplet = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            if (!line.getElement(i).getType().isPitchedNote()) {
                return new TupletToggleInfo(false, null, false);
            }

            var currentTuplet = line.findTupletAt(i);

            if (i == selectionBegin) {
                firstTuplet = currentTuplet;
            } else if (currentTuplet != firstTuplet) {
                return new TupletToggleInfo(false, null, false);
            }
        }

        var coversExisting = (firstTuplet != null)
            && (selectionBegin == firstTuplet.getAnchorElementIndex())
            && (selectionEnd == firstTuplet.getEndElementIndex());

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
     * Returns whether the stem direction can be flipped.
     */
    public boolean canFlipStemDirection() {
        return getSelectionSize() != 0 &&
            line.getElements(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(element -> !element.getType().isRest());
    }

    /**
     * Returns whether a new beam should connect the selection endpoints (add mode), as opposed
     * to the selection already being covered by an existing beam (remove mode).
     */
    private boolean shouldConnectBeamSelection() {
        var beginBeam = line.findBeamAt(selectionBegin);
        var endBeam = line.findBeamAt(selectionEnd);

        //noinspection ObjectEquality
        return (beginBeam == null) || (beginBeam != endBeam);
    }

    /**
     * Returns whether a new tie should connect the selection endpoints (add mode), as opposed
     * to the selection already being covered by an existing tie (remove mode).
     */
    private boolean shouldConnectTieSelection() {
        var beginTie = line.findTieAt(selectionBegin);
        var endTie = line.findTieAt(selectionEnd);

        //noinspection ObjectEquality
        return (beginTie == null) || (beginTie != endTie);
    }
}
