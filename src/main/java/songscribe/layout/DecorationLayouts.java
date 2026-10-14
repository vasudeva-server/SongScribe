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
 * The decoration layouts of one line: for every decorated element, the {@link DecorationLayout}
 * the stacking pass placed it at. A tuplet's is always a {@link Sloped}, an ending's a
 * {@link Volta}, a typeset decoration's a {@link Typeset}, and every other's a {@link Plain}.
 * <p>
 * The table is query-only; {@link Builder} is the only way to fill one, and nothing downstream
 * of a built result can write into it.
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

    /** Null if not computed. */
    public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
        return layouts.get(element);
    }

    public DecorationLayout requireDecorationLayout(LineElement element) {
        var decorationLayout = layouts.get(element);

        if (decorationLayout == null) {
            throw new IllegalStateException("no decoration layout recorded for " + element);
        }

        return decorationLayout;
    }

    public Map<LineElement, DecorationLayout> getDecorationLayouts() {
        return layouts;
    }

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

    public Map<Tuplet, Sloped> tuplets() {
        return tuplets;
    }

    public Map<Ending, Volta> endings() {
        return endings;
    }

    /**
     * @return {@code null} when {@code element} was not stacked on this line — the song's tempo
     *     mark and the attribution on any line but the first
     */
    public @Nullable Typeset getTypeset(LineElement element) {
        return typesets.get(element);
    }

    public Typeset requireTypeset(LineElement element) {
        var typeset = typesets.get(element);

        if (typeset == null) {
            throw new IllegalStateException("no typeset layout recorded for " + element);
        }

        return typeset;
    }

    /**
     * @return {@code null} when the element carries no such attachment or that attachment was not
     *     stacked
     */
    public @Nullable DecorationLayout findAttachmentDecorationLayout(
        StaffElement ownerElement,
        Class<? extends Attachment> attachmentType) {

        var byAttachmentType = layoutsByOwnerAndAttachmentType.get(ownerElement);
        return byAttachmentType == null ? null : byAttachmentType.get(attachmentType);
    }

    public int size() {
        return layouts.size();
    }

    /**
     * Fills a {@link DecorationLayouts}, which {@link #build} freezes. An element is recorded
     * through one kind of entry only.
     */
    public static final class Builder {

        private final Map<LineElement, Plain> plains = new HashMap<>();
        private final Map<LineElement, Typeset> typesets = new HashMap<>();
        private final Map<Tuplet, Sloped> tuplets = new HashMap<>();
        private final Map<Ending, Volta> endings = new HashMap<>();

        public void putPlain(LineElement element, Plain layout) {
            plains.put(element, layout);
        }

        public void putTypeset(LineElement element, Typeset layout) {
            typesets.put(element, layout);
        }

        public void putTuplet(Tuplet tuplet, Sloped layout) {
            tuplets.put(tuplet, layout);
        }

        public void putEnding(Ending ending, Volta layout) {
            endings.put(ending, layout);
        }

        /** Null if not yet recorded. */
        public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
            for (var layoutsOfKind : List.of(plains, typesets, tuplets, endings)) {
                var layout = layoutsOfKind.get(element);

                if (layout != null) {
                    return layout;
                }
            }

            return null;
        }

        public DecorationLayout requireDecorationLayout(LineElement element) {
            var decorationLayout = getDecorationLayout(element);

            if (decorationLayout == null) {
                throw new IllegalStateException("no decoration layout recorded for " + element);
            }

            return decorationLayout;
        }

        public void applyUserOffsets() {
            shiftByUserOffsets(plains, Plain::shiftedBy);
            shiftByUserOffsets(typesets, Typeset::shiftedBy);
            shiftByUserOffsets(tuplets, Sloped::shiftedBy);
            shiftByUserOffsets(endings, Volta::shiftedBy);
        }

        @FunctionalInterface
        private interface Shift<L extends DecorationLayout> {
            L shiftedBy(L layout, double dxSs, double dySs);
        }

        private static <E extends LineElement, L extends DecorationLayout> void shiftByUserOffsets(
            Map<E, L> layouts, Shift<L> shift) {

            layouts.replaceAll((element, layout) -> shift.shiftedBy(
                layout, element.getUserXOffsetSs(), element.getUserYOffsetSs()));
        }

        /** Later writes to this builder do not affect the returned table. */
        public DecorationLayouts build() {
            return new DecorationLayouts(this);
        }

        private Map<LineElement, DecorationLayout> allLayouts() {
            var all = new HashMap<LineElement, DecorationLayout>(plains);
            all.putAll(typesets);
            all.putAll(tuplets);
            all.putAll(endings);

            return all;
        }
    }
}
