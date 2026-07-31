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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;
import songscribe.layout.LineEndingSupport;

/**
 * Per-line selection state and query methods.
 * <p>
 * Each LineComponent owns a LineSelectionState that tracks which elements (if any)
 * are selected on that line, and whether the line itself is selected for deletion.
 * <p>
 * Two answers to "what is selected" live here on purpose, and they do not always
 * agree. Every query but one — {@link #getSelectionSize}, {@link #getSelection},
 * {@link #getSingleSelectedElement}, the tie/beam/tuplet toggles — reports the raw
 * {@code selectionBegin..selectionEnd} range, which is what a tie or a beam is built
 * from. {@link #isElementSelected} alone reports a wider set, taking in a trailing
 * breath mark; see its documentation for why. Making the two agree would change what
 * the toggles operate on, so the disagreement is the design, not a bug to fix.
 */
public final class LineSelectionState {

    /** Two adjacent notes, with nothing between them. */
    private static final int TIE_SELECTION_SIZE_WITHOUT_SEPARATOR = 2;

    /** Two notes with a single non-duration element between them (refs #527). */
    private static final int TIE_SELECTION_SIZE_WITH_SEPARATOR = 3;

    private final Line line;
    private Runnable selectionChangeCallback = () -> {};

    private int selectionBegin = -1;
    private int selectionEnd = -1;
    private int selectionAnchor = -1;
    private boolean lineSelected = false;

    /** The selected slide, ending, or hairpin, or null if no decoration is selected. */
    @Nullable
    private SelectedDecoration selectedDecoration = null;

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
            selectedDecoration = null;
        }

        selectionChangeCallback.run();
    }

    /**
     * Returns the selected decoration, or null if no decoration is selected.
     */
    @Nullable
    public SelectedDecoration getSelectedDecoration() {
        return selectedDecoration;
    }

    /**
     * Returns whether a decoration — a slide, ending, or hairpin — is selected on this line.
     */
    public boolean hasDecorationSelection() {
        return selectedDecoration != null;
    }

    /**
     * Makes the given decoration the sole selection, clearing any element or line selection.
     */
    public void selectDecoration(SelectedDecoration decoration) {
        selectedDecoration = decoration;
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
        return selectedDecoration instanceof SelectedDecoration.SlideSelection(var selectedIndex)
            && selectedIndex == elementIndex;
    }

    /**
     * Returns whether the given line element is the currently selected decoration —
     * the selected ending or hairpin. A slide is an attribute of an element rather than
     * an element of its own, so it never answers true here; see {@link #isSlideSelected}.
     */
    public boolean isDecorationSelected(LineElement element) {
        return switch (selectedDecoration) {
            case null -> false;
            case SelectedDecoration.SlideSelection _ -> false;
            case SelectedDecoration.EndingSelection(var ending) -> element == ending;
            case SelectedDecoration.HairpinSelection(var hairpin) -> element == hairpin;
        };
    }

    /**
     * Clears the decoration selection if it no longer refers to a live decoration on this
     * line — e.g. after an undo/redo that shifted element indices or removed the selected
     * decoration outright. No-op if the current selection is still valid, or if there is no
     * decoration selection.
     *
     * @return whether the selection was cleared
     */
    public boolean revalidateDecorationSelection() {
        return switch (selectedDecoration) {
            case null -> false;

            case SelectedDecoration.SlideSelection(var elementIndex) -> clearIfStale(
                elementIndex >= line.effectiveElementCount()
                    || line.getElement(elementIndex).getSlide() == null
            );

            case SelectedDecoration.EndingSelection(var ending) ->
                clearIfStale(!LineEndingSupport.findEndings(line).contains(ending));

            case SelectedDecoration.HairpinSelection(var hairpin) ->
                clearIfStale(!line.getRangeElements().contains(hairpin));
        };
    }

    /**
     * Drops the whole selection when {@code stale} says it no longer refers to anything
     * live on the line. Shared tail of the {@code revalidate*} methods.
     *
     * @return whether the selection was cleared
     */
    private boolean clearIfStale(boolean stale) {
        if (!stale) {
            return false;
        }

        clearSelection();
        return true;
    }

    /**
     * Clears the element selection if its range no longer fits the line — e.g. after an
     * undo that removed an element the selection covered. No-op if the range is still in
     * bounds, or if there is no element selection.
     *
     * <p>Bounded by {@link Line#elementCount()} rather than
     * {@link Line#effectiveElementCount()}: what makes a range unusable is that it can no
     * longer be indexed at all. Whether a selection may reach the song-owned terminal is a
     * separate question, decided where the selection is made.
     *
     * @return whether the selection was cleared
     */
    public boolean revalidateElementSelection() {
        // Every caller that sets the range leaves selectionBegin <= selectionEnd, so the
        // end alone bounds it. That ordering is a habit of the callers, not something this
        // class checks — a caller that selected backwards would slip past this guard.
        return clearIfStale(hasElementSelection() && selectionEnd >= line.elementCount());
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
        selectedDecoration = null;
        selectionChangeCallback.run();
    }

    /**
     * Selects all elements on this line, excluding the song's
     * auto-maintained terminal.
     * <p>
     * A whole-line selection is dropped in the process: {@link #getSelection} answers
     * from {@code lineSelected} first, so leaving it set would keep reporting a line
     * selection and make the swap invisible.
     */
    public void selectAll() {
        var end = line.effectiveElementCount() - 1;

        if (end < 0) {
            return;
        }

        selectionBegin = 0;
        selectionEnd = end;
        selectionAnchor = 0;
        lineSelected = false;
        selectedDecoration = null;
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
     *
     * <p>A breath mark immediately after the selection counts as selected. It is owned by
     * the element before it and goes wherever that element goes — a deletion or a copy of
     * the selection carries it along ({@link Line#effectiveDeleteEnd}) — so it has to read
     * as selected too, or deleting the selection would take away an element the user never
     * saw highlighted (refs #698).
     */
    public boolean isElementSelected(int elementIndex) {
        if (elementIndex < 0 || !hasElementSelection()) {
            return false;
        }

        return (selectionBegin <= elementIndex) && (elementIndex <= line.effectiveDeleteEnd(selectionEnd));
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
        selectedDecoration = null;
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
        selectedDecoration = null;
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
        selectedDecoration = null;
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
        selectedDecoration = null;
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
     * Returns whether {@code type} may sit between two tied notes.
     *
     * <p>Non-duration elements take no time, so the notes on either side stay adjacent in
     * the music even though an element separates them on the staff. A final double barline
     * is the exception: it ends the piece, so nothing may sound across it (refs #527).
     */
    private static boolean isTieSeparator(ElementType type) {
        return type.isNonDuration() && type != ElementType.FINAL_DOUBLE_BARLINE;
    }

    /**
     * Returns whether the current selection can toggle a tie.
     * Also sets the {@code canTie} and {@code existingTie} fields.
     *
     * <p>A tie joins two notes of the same pitch, which may be adjacent or separated by a
     * single non-duration element such as a barline or repeat (refs #527).
     */
    public boolean canToggleTie() {
        var selectionSize = getSelectionSize();

        if (selectionSize != TIE_SELECTION_SIZE_WITHOUT_SEPARATOR
            && selectionSize != TIE_SELECTION_SIZE_WITH_SEPARATOR) {
            canTie = false;
            return false;
        }

        var beginNote = line.getElement(selectionBegin);
        var endNote = line.getElement(selectionEnd);

        if (!beginNote.getType().isPitchedNote() || !endNote.getType().isPitchedNote()) {
            canTie = false;
            return false;
        }

        if (selectionSize == TIE_SELECTION_SIZE_WITH_SEPARATOR
            && !isTieSeparator(line.getElement(selectionBegin + 1).getType())) {
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
        // are correct here, unlike beaming/tupleting: the only interior a tie allows holds a
        // single separator, which can never be a grace note, and the isPitchedNote() check
        // above already rejects a grace note at either endpoint (refs #592).
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
     * tupleted/untupleted, which tuplet numbers it could actually become, which tuplet
     * currently covers the selection start, and whether the selection covers that
     * tuplet's full span.
     * <p>
     * Rests are welcome inside a new tuplet — they contribute their written duration
     * exactly as notes do — so only grace notes are skipped. Anything else the span may
     * contain (a barline, a breath mark, a fermata) is left for the validator to reject,
     * which it does by leaving the grade out of {@code validGrades}.
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
            // A grace note rides along inside the span without joining the tuplet.
            if (line.getElement(i).getType().isGraceNote()) {
                continue;
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

        // A strict sub-range of a tuplet has no creation decision to offer: making a tuplet
        // of it would silently destroy the tuplet it sits inside. The tuplet is still
        // reported so removal stays available.
        if ((firstTuplet != null) && !coversExisting) {
            return new TupletToggleInfo(false, firstTuplet, false);
        }

        return new TupletToggleInfo(
            true, validGradesFor(beginIndex, endIndex), firstTuplet, coversExisting);
    }

    /**
     * Returns the tuplet numbers this span could be notated as.
     * <p>
     * The span is measured once and the six candidate grades are then tested against that
     * measurement: resolving the beat walks back through the song, and this runs on every
     * document edit, not only when the selection changes.
     */
    private Set<Integer> validGradesFor(int beginIndex, int endIndex) {
        var song = line.getSong();
        var context = TupletValidator.describeSpan(
            song, line, song.indexOfLine(line), beginIndex, endIndex);
        var grades = new HashSet<Integer>();

        // The range comes from the model, not from the menu's list of actions: what a
        // tuplet number may be is a fact about tuplets, and a selection query must not
        // change because someone reorders a menu.
        for (var grade = TupletValidator.MIN_GRADE; grade <= TupletValidator.MAX_GRADE; grade++) {
            if (TupletValidator.validate(context, grade, TupletValidator.Strictness.STRICT).valid()) {
                grades.add(grade);
            }
        }

        return grades;
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
     * or by restoring it to automatic. Only notes that actually carry a stem
     * qualify — rests and whole notes have none.
     */
    public boolean canModifyStemDirection() {
        return getSelectionSize() != 0 &&
            line.getElements(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(element -> element.getType().isNoteWithStem());
    }

    /**
     * Returns whether a new beam should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing beam (remove mode).
     */
    private boolean shouldConnectBeamSelection(int beginIndex, int endIndex) {
        return !line.sameBeamAt(beginIndex, endIndex);
    }

    /**
     * Returns whether a new tie should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing tie (remove mode).
     */
    private boolean shouldConnectTieSelection(int beginIndex, int endIndex) {
        return !line.sameTieAt(beginIndex, endIndex);
    }
}
