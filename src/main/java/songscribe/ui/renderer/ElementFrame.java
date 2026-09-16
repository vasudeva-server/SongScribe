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
 * Per-element rendering state, built fresh for each element a {@code LineRenderer}
 * visits (and shared for line-level passes via {@link #LINE_LEVEL}).
 * <p>
 * Together with {@code LineInvariants} this replaces the mutable per-element setters
 * that {@code ElementRenderContext} exposed: an immutable frame cannot carry stale
 * state from a previous element.
 * <p>
 * {@link #decorationLayoutsFor} memoizes its result: every renderer that draws a note's
 * decorations calls it against the same frame instance for the same element and
 * invariants, so the underlying table — a full preview stacking pass when an override X
 * is active — is computed at most once per frame.
 */
public final class ElementFrame {

    /**
     * The grace-note insertion preview's displacement of the elements from {@code fromIndex}
     * onward, by {@code shiftSs} staff spaces. Created once per paint at line level and shared
     * by reference into every per-element frame of that paint.
     *
     * @param fromIndex first element index (inclusive) to displace
     * @param shiftSs   displacement in staff spaces
     */
    public record PreviewShift(int fromIndex, double shiftSs) {

        /** Returns whether this shift displaces the element at {@code index}. */
        public boolean displaces(int index) {
            return index >= fromIndex;
        }
    }

    /** Canonical frame for line-level passes: no element index, no override, no preview shift. */
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

    /**
     * Returns a line-level frame (no element index, no override) carrying the given
     * grace-note insertion preview shift. {@code LineRenderer} threads this shift into
     * every per-element frame it builds for the paint.
     *
     * @param fromIndex first element index (inclusive) to displace
     * @param shiftSs   preview shift amount in staff spaces
     */
    public static ElementFrame lineLevelWithPreviewShift(int fromIndex, double shiftSs) {
        return new ElementFrame(
            LINE_LEVEL.currentElementIndex(),
            LINE_LEVEL.overrideElementXSs(),
            new PreviewShift(fromIndex, shiftSs)
        );
    }

    /**
     * Returns a per-element frame for {@code elementIndex} with the given X override,
     * sharing this frame's preview shift. Pass {@link Double#NaN} for
     * {@code overrideXSs} when the element uses its laid-out position.
     *
     * @param elementIndex index of the element within the line, or {@code null} for a
     *                     line-level pass
     * @param overrideXSs  exact X in staff spaces, or {@link Double#NaN} for no override
     */
    public ElementFrame withElement(@Nullable Integer elementIndex, double overrideXSs) {
        return new ElementFrame(elementIndex, overrideXSs, previewShift);
    }

    /**
     * Index of the element being rendered within the line, or {@code null} during
     * line-level passes.
     */
    public @Nullable Integer currentElementIndex() {
        return currentElementIndex;
    }

    /**
     * Exact X coordinate in staff spaces to use instead of the laid-out position, or
     * {@link Double#NaN} when no override applies.
     */
    public double overrideElementXSs() {
        return overrideElementXSs;
    }

    /** Returns the grace-note insert preview's displacement, or null when no shift applies. */
    public @Nullable PreviewShift previewShift() {
        return previewShift;
    }

    /** Returns whether an override element X is active (i.e. {@link #overrideElementXSs} is not NaN). */
    public boolean hasOverrideElementX() {
        return !Double.isNaN(overrideElementXSs);
    }

    /**
     * Returns the X at which {@code element} is drawn, in staff spaces: this frame's override
     * when it carries one — the position an insertion preview is being dragged through — and the
     * element's solved position otherwise.
     *
     * <p>Every renderer that draws an element at a horizontal position asks this, so a preview
     * moves all of them together. The value is returned exactly as laid out, with no
     * device-pixel snapping.
     *
     * @param element    the element being drawn
     * @param invariants the line invariants carrying the solved layout
     * @return the element's X in staff spaces
     */
    public double resolveElementXSs(StaffElement element, LineInvariants invariants) {
        if (hasOverrideElementX()) {
            return overrideElementXSs;
        }

        return invariants.getLayoutResult().getElementXSs(element);
    }

    /**
     * Returns the decoration layouts {@code element}'s note-attached decorations are drawn from
     * in this frame: for a note carrying this frame's override — the position an insertion
     * preview is being dragged through — the layouts the preview stacks for that note alone,
     * and the line's solved table otherwise.
     *
     * <p>Every renderer of a note-attached decoration asks this, so a previewed note's
     * articulations, fermata and dynamic stack where the committed note's would. A previewed
     * element that is not a note has no such decorations to preview, so it reads the line's
     * table like any other element. Those renderers all ask against the same frame instance
     * for the same element and invariants, so the result is computed once per frame and
     * cached rather than re-running the stacking pass for each renderer.
     *
     * @param element    the element whose decorations are being drawn
     * @param invariants the line invariants carrying the solved layout
     * @return the decoration layouts to draw from
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
