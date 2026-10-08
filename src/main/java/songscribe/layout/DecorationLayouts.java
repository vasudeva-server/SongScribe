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
import songscribe.dom.Ending;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;
import songscribe.layout.LayoutResult.DecorationLayout;
import songscribe.layout.LayoutResult.DecorationLayout.Plain;
import songscribe.layout.LayoutResult.DecorationLayout.Sloped;
import songscribe.layout.LayoutResult.DecorationLayout.Typeset;
import songscribe.layout.LayoutResult.DecorationLayout.Volta;

/**
 * The decoration layouts of one line: for every decorated element — an articulation, an
 * attachment, a span, the attribution — the {@link DecorationLayout} the stacking pass placed
 * it at.
 * <p>
 * All positions are in staff-space units, in the line's layout space.
 * <p>
 * Each kind of layout is an entry of its own type: a tuplet's is always a {@link Sloped}, an
 * ending's always a {@link Volta}, a typeset decoration's always a {@link Typeset}, and every other
 * decoration's a {@link Plain}. The typed reads — {@link #tuplets}, {@link #endings} and
 * {@link #getTypeset} — hand each back as the type it was recorded as, so no reader tests or casts
 * the member it holds. The general reads answer every entry as a {@link DecorationLayout}, for a
 * reader that needs only the bounds.
 * <p>
 * This table is query-only: none of its methods can add or replace an entry. {@link Builder} is
 * the only way to fill one: the stacking pass records each placement through the builder's typed
 * entries as it runs, and {@link Builder#build} freezes the recorded entries into the table an
 * instance of this class wraps. A {@link LayoutResult} holds only that frozen table, so every
 * reader past the stacking pass — the renderers, the hit regions, the insertion preview — only
 * ever queries it; nothing downstream of a built result can write into it.
 * <p>
 * A table also stands on its own, without a layout result: the insertion preview stacks one
 * note's decorations into a fresh {@link Builder} and reads the frozen table {@link Builder#build}
 * returns, having no line to lay out.
 */
public final class DecorationLayouts {

    private final Map<LineElement, DecorationLayout> layouts;
    private final Map<Tuplet, Sloped> tuplets;
    private final Map<Ending, Volta> endings;
    private final Map<LineElement, Typeset> typesets;
    private final Map<StaffElement, Map<Class<? extends Attachment>, DecorationLayout>>
        layoutsByOwnerAndAttachmentType;
    private final Map<Class<?>, List<Map.Entry<LineElement, DecorationLayout>>> layoutsByExactType;

    private DecorationLayouts(Builder builder) {
        tuplets = Map.copyOf(builder.tuplets);
        endings = Map.copyOf(builder.endings);
        typesets = Map.copyOf(builder.typesets);
        layouts = Map.copyOf(builder.allLayouts());

        var byOwnerAndAttachmentType =
            new HashMap<StaffElement, Map<Class<? extends Attachment>, DecorationLayout>>();
        var byExactType = new HashMap<Class<?>, List<Map.Entry<LineElement, DecorationLayout>>>();

        for (var entry : layouts.entrySet()) {
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
     * Returns the decoration layout recorded for an element, whatever its kind.
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
     * Returns every placed tuplet with its sloped layout.
     *
     * @return an unmodifiable map, in no promised order; empty when the line places no tuplet
     */
    public Map<Tuplet, Sloped> tuplets() {
        return tuplets;
    }

    /**
     * Returns every placed ending with its layout and brackets.
     *
     * @return an unmodifiable map, in no promised order; empty when the line places no ending
     */
    public Map<Ending, Volta> endings() {
        return endings;
    }

    /**
     * Returns the typeset layout of a decoration that draws content the layout pass typeset for
     * it: a metronome marking, the song's tempo mark or the attribution block.
     *
     * @param element the typeset decoration to look up
     * @return its layout, or {@code null} when {@code element} was not stacked on this line — the
     *     song's tempo mark and the attribution on any line but the first
     */
    public @Nullable Typeset getTypeset(LineElement element) {
        return typesets.get(element);
    }

    /**
     * Returns the typeset layout of a decoration that must have been stacked on this line.
     *
     * @param element the typeset decoration to look up
     * @return its layout
     * @throws IllegalStateException if no typeset layout is recorded for {@code element}
     */
    public Typeset requireTypeset(LineElement element) {
        var typeset = typesets.get(element);

        if (typeset == null) {
            throw new IllegalStateException("no typeset layout recorded for " + element);
        }

        return typeset;
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
     * through the entry of its kind as it runs — {@link #putPlain}, {@link #putTypeset},
     * {@link #putTuplet} or {@link #putEnding} — and {@link #build} freezes the table as it then
     * stands into the {@link DecorationLayouts} the builder returns.
     * <p>
     * An element is recorded through one kind of entry only: placing it again through that entry
     * replaces its record, and {@link #applyUserOffsets} rewrites every record in place.
     */
    public static final class Builder {

        private final Map<LineElement, Plain> plains = new HashMap<>();
        private final Map<LineElement, Typeset> typesets = new HashMap<>();
        private final Map<Tuplet, Sloped> tuplets = new HashMap<>();
        private final Map<Ending, Volta> endings = new HashMap<>();

        /**
         * Records the layout of a decoration that draws itself from the model, replacing any
         * plain layout already recorded for it.
         *
         * @param element the decoration element
         * @param layout  its placed bounds
         * @effects afterwards {@link #getDecorationLayout} answers {@code layout} for
         *          {@code element}, and so does the built {@link DecorationLayouts}
         */
        public void putPlain(LineElement element, Plain layout) {
            plains.put(element, layout);
        }

        /**
         * Records the layout of a decoration that draws typeset content, replacing any typeset
         * layout already recorded for it.
         *
         * @param element the decoration element
         * @param layout  its placed bounds and content
         * @effects afterwards {@link #getDecorationLayout} answers {@code layout} for
         *          {@code element}, and the built {@link DecorationLayouts} answers it from both
         *          {@link DecorationLayouts#getDecorationLayout} and
         *          {@link DecorationLayouts#getTypeset}
         */
        public void putTypeset(LineElement element, Typeset layout) {
            typesets.put(element, layout);
        }

        /**
         * Records the layout of a tuplet, replacing any layout already recorded for it.
         *
         * @param tuplet the tuplet
         * @param layout its placed, sloped bounds
         * @effects afterwards {@link #getDecorationLayout} answers {@code layout} for
         *          {@code tuplet}, and the built {@link DecorationLayouts} holds it in
         *          {@link DecorationLayouts#tuplets}
         */
        public void putTuplet(Tuplet tuplet, Sloped layout) {
            tuplets.put(tuplet, layout);
        }

        /**
         * Records the layout of an ending, replacing any layout already recorded for it.
         *
         * @param ending the ending
         * @param layout its placed bounds and brackets
         * @effects afterwards {@link #getDecorationLayout} answers {@code layout} for
         *          {@code ending}, and the built {@link DecorationLayouts} holds it in
         *          {@link DecorationLayouts#endings}
         */
        public void putEnding(Ending ending, Volta layout) {
            endings.put(ending, layout);
        }

        /**
         * Returns the decoration layout recorded so far for a decoration element, whatever its
         * kind.
         *
         * @param element The decoration element to look up
         * @return The decoration layout, or null if not yet recorded
         */
        public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
            for (var layoutsOfKind : List.of(plains, typesets, tuplets, endings)) {
                var layout = layoutsOfKind.get(element);

                if (layout != null) {
                    return layout;
                }
            }

            return null;
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
            var decorationLayout = getDecorationLayout(element);

            if (decorationLayout == null) {
                throw new IllegalStateException("no decoration layout recorded for " + element);
            }

            return decorationLayout;
        }

        /**
         * Moves every layout recorded so far by its element's user offsets,
         * {@link LineElement#getUserXOffsetSs()} and {@link LineElement#getUserYOffsetSs()}.
         * <p>
         * Each entry is shifted through its own member's {@code shiftedBy}, so it stays the type it
         * was recorded as and keeps every component that member carries.
         *
         * @effects every record is replaced by its copy shifted by its element's offsets
         */
        public void applyUserOffsets() {
            shiftByUserOffsets(plains, Plain::shiftedBy);
            shiftByUserOffsets(typesets, Typeset::shiftedBy);
            shiftByUserOffsets(tuplets, Sloped::shiftedBy);
            shiftByUserOffsets(endings, Volta::shiftedBy);
        }

        /** One kind's {@code shiftedBy}, which returns the kind it was given. */
        @FunctionalInterface
        private interface Shift<L extends DecorationLayout> {
            L shiftedBy(L layout, double dxSs, double dySs);
        }

        private static <E extends LineElement, L extends DecorationLayout> void shiftByUserOffsets(
            Map<E, L> layouts, Shift<L> shift) {

            layouts.replaceAll((element, layout) -> shift.shiftedBy(
                layout, element.getUserXOffsetSs(), element.getUserYOffsetSs()));
        }

        /**
         * Freezes the entries recorded so far into a queryable table.
         *
         * @return a {@link DecorationLayouts} that cannot be written to; later writes to this
         *     builder do not affect it
         */
        public DecorationLayouts build() {
            return new DecorationLayouts(this);
        }

        /** Every entry recorded so far, of every kind, keyed by element. */
        private Map<LineElement, DecorationLayout> allLayouts() {
            var all = new HashMap<LineElement, DecorationLayout>(plains);
            all.putAll(typesets);
            all.putAll(tuplets);
            all.putAll(endings);

            return all;
        }
    }
}
