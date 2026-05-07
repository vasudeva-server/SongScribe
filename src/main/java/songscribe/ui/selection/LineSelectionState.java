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

import songscribe.music.Span;
import songscribe.music.SpanSet;
import songscribe.music.TupletSpan;
import songscribe.music.Line;
import songscribe.music.StaffElement;

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

    @Nullable
    private SpanSet<?> tieSpanSet = null;

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

    @Nullable
    public SpanSet<?> getTieSpanSet() {
        return tieSpanSet;
    }

    public void resetTieState() {
        canTie = null;
        tieSpanSet = null;
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

        return !selectionWouldConflict(line.getBeamings(), line.getTies());
    }

    /**
     * Returns whether the current selection can toggle a tie.
     * Also sets the canTie and tieSpanSet fields.
     */
    public boolean canToggleTie() {
        if (getSelectionSize() != 2) {
            canTie = false;
            return false;
        }

        var ties = line.getTies();
        Span firstTieSpan = null;
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
                firstTieSpan = ties.findSpan(i);
            } else {
                //noinspection ObjectEquality
                if (ties.findSpan(i) != firstTieSpan) {
                    canTie = false;
                    return false;
                }
            }
        }

        if (selectionWouldConflict(ties, line.getBeamings())) {
            canTie = false;
            return false;
        }

        canTie = true;
        tieSpanSet = firstTieSpan != null ? ties : null;
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

        var tuplets = line.getTuplets();
        TupletSpan firstSpan = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            if (!line.getElement(i).getType().isPitchedNote()) {
                return new TupletToggleInfo(false, null, false);
            }

            var currentSpan = tuplets.findSpan(i);

            if (i == selectionBegin) {
                firstSpan = currentSpan;
            } else if (currentSpan != firstSpan) {
                return new TupletToggleInfo(false, null, false);
            }
        }

        var coversExisting = (firstSpan != null)
            && (selectionBegin == firstSpan.start)
            && (selectionEnd == firstSpan.end);

        return new TupletToggleInfo(true, firstSpan, coversExisting);
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
     * Returns whether adding {@code target} to the selection would conflict with an existing
     * span of the opposing type ({@code conflicting}). A conflict exists when the selection
     * endpoints would be newly connected by {@code target} but are already connected by
     * {@code conflicting}.
     */
    private boolean selectionWouldConflict(SpanSet<?> target, SpanSet<?> conflicting) {
        return shouldConnectSelection(target) && !shouldConnectSelection(conflicting);
    }

    /**
     * Returns whether the selection should connect (add) or disconnect (remove) a span.
     */
    public boolean shouldConnectSelection(SpanSet<?> spanSet) {
        var beginSpan = spanSet.findSpan(selectionBegin);
        var endSpan = spanSet.findSpan(selectionEnd);

        //noinspection ObjectEquality
        return (beginSpan == null) || (beginSpan != endSpan);
    }
}
