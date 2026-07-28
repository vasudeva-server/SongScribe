/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.undo;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.StaffElement;

/**
 * Single home for assembling context-dependent Tier-B undo op-names (delete
 * category/plural/mixed, add-by-pen, slide subtype, lyric add/edit/delete) from
 * {@code Strings.*} constants. Every method here is a pure function of its
 * arguments — it reads no shared state and only resolves and returns a
 * {@link Strings} label — so the same {@code ElementType -> category} taxonomy is
 * decoded in exactly one place and is directly unit-testable.
 */
public final class OpNames {

    private OpNames() {
    }

    /**
     * Deletable element categories, sharing the same {@code ElementType}
     * classification used by insertion. Grace notes fold into {@link #NOTE} via
     * {@link ElementType#isNote()}.
     */
    private enum Category {
        NOTE, REST, BARLINE, REPEAT, BREATH_MARK
    }

    private static @Nullable Category categoryOf(ElementType type) {
        if (type.isNote()) {
            return Category.NOTE;
        }

        if (type.isRest()) {
            return Category.REST;
        }

        if (type.isBarLine()) {
            return Category.BARLINE;
        }

        if (type.isRepeat()) {
            return Category.REPEAT;
        }

        if (type.isBreathMark()) {
            return Category.BREATH_MARK;
        }

        return null;
    }

    /**
     * Names a deletion by the categories of the deleted elements: a single element
     * yields {@code Delete <Category>}; several elements of one category yield the
     * plural {@code Delete <Category>s}; a mix of categories (or any uncategorized
     * element) yields the generic {@code Delete Elements}. A note and its grace note
     * fold to the {@code Note} category.
     */
    public static String deleteLabel(List<ElementType> types) {
        Category common = null;
        var first = true;

        for (var type : types) {
            var category = categoryOf(type);

            if (first) {
                common = category;
                first = false;
            } else if (common != category) {
                common = null;
            }

            if (common == null) {
                break;
            }
        }

        if (common == null) {
            return Strings.get(Strings.ACTION_EDIT_OP_DELETE_ELEMENTS);
        }

        var singular = types.size() == 1;

        return switch (common) {
            case NOTE -> Strings.get(
                singular ? Strings.ACTION_EDIT_OP_DELETE_NOTE : Strings.ACTION_EDIT_OP_DELETE_NOTES);
            case REST -> Strings.get(
                singular ? Strings.ACTION_EDIT_OP_DELETE_REST : Strings.ACTION_EDIT_OP_DELETE_RESTS);
            case BARLINE -> Strings.get(
                singular ? Strings.ACTION_EDIT_OP_DELETE_BARLINE : Strings.ACTION_EDIT_OP_DELETE_BARLINES);
            case REPEAT -> Strings.get(
                singular ? Strings.ACTION_EDIT_OP_DELETE_REPEAT : Strings.ACTION_EDIT_OP_DELETE_REPEATS);
            case BREATH_MARK -> Strings.get(
                singular ? Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK : Strings.ACTION_EDIT_OP_DELETE_BREATH_MARKS);
        };
    }

    /**
     * Names an insertion by the pen (preview) element's type. Insertion is always a
     * single element, so the label is always singular. Grace notes get their own
     * {@code Add Grace Note} label rather than folding into {@code Note}.
     */
    public static String addLabel(ElementType type) {
        if (type.isGraceNote()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_GRACE_NOTE);
        }

        if (type.isNote()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_NOTE);
        }

        if (type.isRest()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_REST);
        }

        if (type.isBarLine()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_BARLINE);
        }

        if (type.isRepeat()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_REPEAT);
        }

        return Strings.get(Strings.ACTION_EDIT_OP_ADD_BREATH_MARK);
    }

    /**
     * Names a slide insertion by subtype: {@code Add Fall} when {@code fall} is
     * true, otherwise {@code Add Glissando}.
     */
    public static String addSlideLabel(boolean fall) {
        return Strings.get(fall ? Strings.ACTION_EDIT_OP_ADD_FALL : Strings.ACTION_EDIT_OP_ADD_GLISSANDO);
    }

    /**
     * Names a slide deletion by subtype: {@code Delete Fall} for a {@link
     * StaffElement.Fall}, otherwise {@code Delete Glissando}.
     */
    public static String deleteSlideLabel(StaffElement.Slide slide) {
        return Strings.get(slide instanceof StaffElement.Fall
            ? Strings.ACTION_EDIT_OP_DELETE_FALL
            : Strings.ACTION_EDIT_OP_DELETE_GLISSANDO);
    }

    /**
     * Names a hairpin deletion by subtype: {@code Delete Crescendo} for a {@link
     * Crescendo}, otherwise {@code Delete Diminuendo}.
     */
    public static String deleteHairpinLabel(Hairpin hairpin) {
        return Strings.get(hairpin instanceof Crescendo
            ? Strings.ACTION_EDIT_OP_DELETE_CRESCENDO
            : Strings.ACTION_EDIT_OP_DELETE_DIMINUENDO);
    }

    /**
     * Names an ending deletion.
     */
    public static String deleteEndingLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_DELETE_ENDING);
    }

    /**
     * Names a whole-line deletion.
     */
    public static String deleteLineLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_DELETE_LINE);
    }

    /**
     * Names a single-element lyric edit by before/after text emptiness: empty →
     * non-empty is {@code Add Lyric}; non-empty → empty is {@code Delete Lyric};
     * any other transition is {@code Edit Lyric}.
     */
    public static String lyricLabel(String beforeText, String afterText) {
        if (beforeText.isEmpty() && !afterText.isEmpty()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_LYRIC);
        }

        if (!beforeText.isEmpty() && afterText.isEmpty()) {
            return Strings.get(Strings.ACTION_EDIT_OP_DELETE_LYRIC);
        }

        return Strings.get(Strings.ACTION_EDIT_OP_EDIT_LYRIC);
    }
}
