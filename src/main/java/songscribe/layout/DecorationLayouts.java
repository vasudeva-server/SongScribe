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

package songscribe.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Attachment;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult.DecorationLayout;

/**
 * The decoration layouts of one line: for every decorated element — an articulation, an
 * attachment, a span, the attribution — the {@link DecorationLayout} the stacking pass placed
 * it at.
 * <p>
 * All positions are in staff-space units, in the line's layout space.
 * <p>
 * This table is query-only: {@link #getDecorationLayout}, {@link #requireDecorationLayout},
 * {@link #getDecorationLayouts}, {@link #getDecorationLayoutsByType} and
 * {@link #findAttachmentDecorationLayout} are its whole surface, and none of them can add or
 * replace an entry. {@link Builder} is the only way to fill one: the stacking pass records each
 * placement through {@link Builder#putDecorationLayout} as it runs, and {@link Builder#build}
 * freezes the recorded entries into the table an instance of this class wraps. A
 * {@link LayoutResult} holds only that frozen table, so every reader past the stacking pass — the
 * renderers, the hit regions, the insertion preview — only ever queries it; nothing downstream of
 * a built result can write into it.
 * <p>
 * A table also stands on its own, without a layout result: the insertion preview stacks one
 * note's decorations into a fresh {@link Builder} and reads the frozen table {@link Builder#build}
 * returns, having no line to lay out.
 */
public final class DecorationLayouts {

    private final Map<LineElement, DecorationLayout> layouts;
    private final Map<StaffElement, Map<Class<? extends Attachment>, DecorationLayout>>
        layoutsByOwnerAndAttachmentType;
    private final Map<Class<?>, List<Map.Entry<LineElement, DecorationLayout>>> layoutsByExactType;

    private DecorationLayouts(Map<LineElement, DecorationLayout> layouts) {
        this.layouts = Map.copyOf(layouts);

        var byOwnerAndAttachmentType =
            new HashMap<StaffElement, Map<Class<? extends Attachment>, DecorationLayout>>();
        var byExactType = new HashMap<Class<?>, List<Map.Entry<LineElement, DecorationLayout>>>();

        for (var entry : this.layouts.entrySet()) {
            var element = entry.getKey();

            byExactType.computeIfAbsent(element.getClass(), type -> new ArrayList<>()).add(entry);

            if (element instanceof Attachment attachment && attachment.owner() != null) {
                byOwnerAndAttachmentType.computeIfAbsent(attachment.owner(), owner -> new HashMap<>())
                    .put(attachment.getClass(), entry.getValue());
            }
        }

        layoutsByOwnerAndAttachmentType = Map.copyOf(byOwnerAndAttachmentType);
        layoutsByExactType = Map.copyOf(byExactType);
    }

    /**
     * Returns the decoration layout for an above-staff decoration element.
     *
     * @param element The decoration element to look up
     * @return The decoration layout, or null if not computed
     */
    public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
        return layouts.get(element);
    }

    /**
     * Returns the decoration layout recorded for an element that must have been stacked already.
     *
     * @param element the decoration element to look up
     * @return the recorded decoration layout
     * @throws IllegalStateException if no layout is recorded for {@code element}
     */
    public DecorationLayout requireDecorationLayout(LineElement element) {
        var decorationLayout = layouts.get(element);

        if (decorationLayout == null) {
            throw new IllegalStateException("no decoration layout recorded for " + element);
        }

        return decorationLayout;
    }

    /**
     * Returns the whole table: every decorated element and the layout recorded for it.
     *
     * @return an unmodifiable view, in no promised order
     */
    public Map<LineElement, DecorationLayout> getDecorationLayouts() {
        return layouts;
    }

    /**
     * Returns every decorated element of class {@code type} with its layout, so a renderer or a
     * hit test can take all of one kind in one pass.
     *
     * @param type the element class to select
     * @return the matching entries, in no promised order; empty when the table holds none
     */
    public <T extends LineElement> List<Map.Entry<T, DecorationLayout>> getDecorationLayoutsByType(
        Class<? extends T> type) {

        var result = new ArrayList<Map.Entry<T, DecorationLayout>>();

        for (var bucket : layoutsByExactType.entrySet()) {
            if (type.isAssignableFrom(bucket.getKey())) {
                for (var entry : bucket.getValue()) {
                    result.add(Map.entry(type.cast(entry.getKey()), entry.getValue()));
                }
            }
        }

        return result;
    }

    /**
     * Returns the layout of the attachment of class {@code attachmentType} on
     * {@code ownerElement}, for a caller that holds the element rather than the attachment. An
     * element carries at most one attachment of any class, so the answer is the layout rather
     * than one of several.
     *
     * @param ownerElement   the element the attachment is on
     * @param attachmentType the attachment class to look for
     * @return its layout, or {@code null} when the element carries no such attachment or that
     *     attachment was not stacked
     */
    public @Nullable DecorationLayout findAttachmentDecorationLayout(
        StaffElement ownerElement,
        Class<? extends Attachment> attachmentType) {

        var byAttachmentType = layoutsByOwnerAndAttachmentType.get(ownerElement);
        return byAttachmentType == null ? null : byAttachmentType.get(attachmentType);
    }

    /**
     * Returns how many decorated elements the table holds.
     *
     * @return the count of elements with a recorded layout; 0 for an empty table
     */
    public int size() {
        return layouts.size();
    }

    /**
     * Fills a {@link DecorationLayouts}: the stacking pass records each element's placement
     * through {@link #putDecorationLayout} as it runs — placing an element again, a manual offset
     * applied after stacking, replaces the record — and {@link #build} freezes the table as it
     * then stands into the {@link DecorationLayouts} the builder returns.
     */
    public static final class Builder {

        private final Map<LineElement, DecorationLayout> layouts = new HashMap<>();

        /**
         * Records the computed layout of a decoration element, replacing any layout already
         * recorded for it.
         *
         * @param element          The decoration element
         * @param decorationLayout The computed decoration geometry
         * @effects afterwards {@link #getDecorationLayout} answers {@code decorationLayout} for
         *          {@code element}, and so does the built {@link DecorationLayouts}
         */
        public void putDecorationLayout(LineElement element, DecorationLayout decorationLayout) {
            layouts.put(element, decorationLayout);
        }

        /**
         * Returns the decoration layout recorded so far for a decoration element.
         *
         * @param element The decoration element to look up
         * @return The decoration layout, or null if not yet recorded
         */
        public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
            return layouts.get(element);
        }

        /**
         * Returns the decoration layout recorded for an element that must have been stacked
         * already.
         *
         * @param element the decoration element to look up
         * @return the recorded decoration layout
         * @throws IllegalStateException if no layout is recorded for {@code element}
         */
        public DecorationLayout requireDecorationLayout(LineElement element) {
            var decorationLayout = layouts.get(element);

            if (decorationLayout == null) {
                throw new IllegalStateException("no decoration layout recorded for " + element);
            }

            return decorationLayout;
        }

        /**
         * Returns every layout recorded so far, for a caller that must revisit them before the
         * table is frozen — a manual-offset pass that rewrites each entry in place.
         *
         * @return an unmodifiable view, in no promised order; a caller that writes through
         *     {@link #putDecorationLayout} while iterating copies it first
         */
        public Map<LineElement, DecorationLayout> getDecorationLayouts() {
            return Map.copyOf(layouts);
        }

        /**
         * Freezes the entries recorded so far into a queryable table.
         *
         * @return a {@link DecorationLayouts} that cannot be written to; later calls to
         *     {@link #putDecorationLayout} on this builder do not affect it
         */
        public DecorationLayouts build() {
            return new DecorationLayouts(layouts);
        }
    }
}
