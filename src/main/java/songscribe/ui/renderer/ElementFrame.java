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

package songscribe.ui.renderer;

import org.jspecify.annotations.Nullable;

import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.layout.DecorationLayouts;
import songscribe.layout.stacking.NoteAttachedStacker;

/**
 * Per-element rendering state, built fresh for each element a {@code LineRenderer} visits
 * (shared for line-level passes via {@link #LINE_LEVEL}). {@link #decorationLayoutsFor}
 * memoizes its result, so the stacking pass runs at most once per frame.
 */
public final class ElementFrame {

    /**
     * The grace-note insertion preview's displacement of the elements from {@code fromIndex}
     * onward, by {@code shiftSs} staff spaces.
     */
    public record PreviewShift(int fromIndex, double shiftSs) {

        public boolean displaces(int index) {
            return index >= fromIndex;
        }
    }

    public static final ElementFrame LINE_LEVEL = new ElementFrame(null, Double.NaN, null);

    private final @Nullable Integer currentElementIndex;
    private final double overrideElementXSs;
    private final @Nullable PreviewShift previewShift;

    private @Nullable StaffElement decorationLayoutsElement;
    private @Nullable LineInvariants decorationLayoutsInvariants;
    private @Nullable DecorationLayouts decorationLayoutsCache;

    private ElementFrame(
        @Nullable Integer currentElementIndex, double overrideElementXSs, @Nullable PreviewShift previewShift
    ) {
        this.currentElementIndex = currentElementIndex;
        this.overrideElementXSs = overrideElementXSs;
        this.previewShift = previewShift;
    }

    public static ElementFrame lineLevelWithPreviewShift(int fromIndex, double shiftSs) {
        return new ElementFrame(
            LINE_LEVEL.currentElementIndex(),
            LINE_LEVEL.overrideElementXSs(),
            new PreviewShift(fromIndex, shiftSs)
        );
    }

    /**
     * @param elementIndex index of the element within the line, or {@code null} for a
     *                     line-level pass
     * @param overrideXSs  exact X in staff spaces, or {@link Double#NaN} for no override
     */
    public ElementFrame withElement(@Nullable Integer elementIndex, double overrideXSs) {
        return new ElementFrame(elementIndex, overrideXSs, previewShift);
    }

    /**
     * {@code null} during line-level passes.
     */
    public @Nullable Integer currentElementIndex() {
        return currentElementIndex;
    }

    /**
     * {@link Double#NaN} when no override applies.
     */
    public double overrideElementXSs() {
        return overrideElementXSs;
    }

    /** Null when no shift applies. */
    public @Nullable PreviewShift previewShift() {
        return previewShift;
    }

    public boolean hasOverrideElementX() {
        return !Double.isNaN(overrideElementXSs);
    }

    /**
     * Every renderer that draws an element at a horizontal position asks this, so a preview
     * moves all of them together. No device-pixel snapping.
     */
    public double resolveElementXSs(StaffElement element, LineInvariants invariants) {
        if (hasOverrideElementX()) {
            return overrideElementXSs;
        }

        return invariants.getLayoutResult().getElementXSs(element);
    }

    /**
     * Every renderer of a note-attached decoration asks this, so a previewed note's
     * decorations stack where the committed note's would.
     */
    public DecorationLayouts decorationLayoutsFor(StaffElement element, LineInvariants invariants) {
        if (decorationLayoutsCache == null
            || decorationLayoutsElement != element
            || decorationLayoutsInvariants != invariants) {
            decorationLayoutsElement = element;
            decorationLayoutsInvariants = invariants;
            decorationLayoutsCache = computeDecorationLayoutsFor(element, invariants);
        }

        return decorationLayoutsCache;
    }

    private DecorationLayouts computeDecorationLayoutsFor(StaffElement element, LineInvariants invariants) {
        if (hasOverrideElementX() && element instanceof NoteElement note) {
            return NoteAttachedStacker.computePreviewDecorationLayouts(note, overrideElementXSs);
        }

        return invariants.getLayoutResult().decorationLayouts();
    }
}
