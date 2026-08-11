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
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ArticulationType;
import songscribe.dom.Attachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;

/**
 * Names an edit for the Edit menu, in the words the user would use for what they just
 * did. Each method takes what the edit acted on and returns the finished, localized
 * operation name — {@code "Delete Notes"}, {@code "Add Grace Note"} — which the call site
 * declares as its modification bracket's label and which the Edit menu shows after
 * {@code "Undo"} or {@code "Redo"}.
 *
 * <p>Every method here is a pure function of its arguments: it reads no shared state,
 * touches no model, and its result depends on nothing but what it was passed. A caller
 * may therefore compute a label before the edit, after it, or not use it at all.
 *
 * <p>The wording distinguishes deleting a thing from taking a decoration off the thing
 * that carries it: elements are {@code Delete}d, while slides, ties, beams, tuplets,
 * trills, accidentals, articulations and attachments are {@code Remove}d.
 *
 * <p>This is the only place the {@code ElementType} → category taxonomy behind those
 * names is decoded, so the categories the insertion and deletion labels use cannot drift
 * apart. These are the Tier-B names of {@code docs/undo.md}; a step with no declared name
 * falls back to {@code UndoController}'s own mutation-type label instead.
 */
public final class OpNames {

    private OpNames() {
    }

    /**
     * The element categories the labels distinguish. Grace notes fold into {@link #NOTE}
     * via {@link ElementType#isNote()}; {@link #addLabel} names them separately by asking
     * before it classifies, because inserting a grace note is a distinct operation to the
     * user while deleting one is not.
     */
    private enum Category {
        NOTE, REST, BARLINE, REPEAT, BREATH_MARK
    }

    /**
     * The single classifier both labels are built on: the category {@code type} belongs to,
     * or {@code null} for a type in none of them.
     */
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
     * Names a deletion by the categories of the deleted elements: a single element yields
     * the singular {@code Delete <Category>}; several elements that all share one category
     * yield the plural {@code Delete <Category>s}; a mix of categories, or any element in
     * no category, yields the generic {@code Delete Elements}. A note and its grace note
     * share the {@code Note} category, so deleting them together is {@code Delete Notes}
     * rather than the generic label.
     *
     * @param types the types of the elements being deleted, in any order; must not be
     *              empty — a deletion of nothing has no name, and nothing is promised for
     *              an empty list
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

        return Strings.get(
            switch (common) {
                case NOTE -> singular ?
                    Strings.ACTION_EDIT_OP_DELETE_NOTE :
                    Strings.ACTION_EDIT_OP_DELETE_NOTES;
                case REST -> singular ?
                    Strings.ACTION_EDIT_OP_DELETE_REST :
                    Strings.ACTION_EDIT_OP_DELETE_RESTS;
                case BARLINE -> singular ?
                    Strings.ACTION_EDIT_OP_DELETE_BARLINE :
                    Strings.ACTION_EDIT_OP_DELETE_BARLINES;
                case REPEAT -> singular ?
                    Strings.ACTION_EDIT_OP_DELETE_REPEAT :
                    Strings.ACTION_EDIT_OP_DELETE_REPEATS;
                case BREATH_MARK -> singular ?
                    Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK :
                    Strings.ACTION_EDIT_OP_DELETE_BREATH_MARKS;
            });
    }

    /**
     * Names an insertion by the type of the element being inserted. Insertion is always of
     * a single element, so the label is always singular. A grace note is named
     * {@code Add Grace Note} rather than folding into {@code Add Note}.
     *
     * @param type the type being inserted; must belong to one of the categories the pen
     *             can insert — a note, a rest, a barline, a repeat or a breath mark
     * @throws IllegalArgumentException if {@code type} is in none of those categories,
     *                                  which no insertion the user can perform produces.
     *                                  A new {@link ElementType} category fails here
     *                                  rather than being labelled as something it is not.
     */
    public static String addLabel(ElementType type) {
        if (type.isGraceNote()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_GRACE_NOTE);
        }

        var category = categoryOf(type);

        if (category == null) {
            throw new IllegalArgumentException("No add label for element type " + type);
        }

        return Strings.get(
            switch (category) {
                case NOTE -> Strings.ACTION_EDIT_OP_ADD_NOTE;
                case REST -> Strings.ACTION_EDIT_OP_ADD_REST;
                case BARLINE -> Strings.ACTION_EDIT_OP_ADD_BARLINE;
                case REPEAT -> Strings.ACTION_EDIT_OP_ADD_REPEAT;
                case BREATH_MARK -> Strings.ACTION_EDIT_OP_ADD_BREATH_MARK;
            });
    }

    /**
     * Names a slide insertion by the kind of slide being attached: {@code Add Fall} for
     * {@link SlideZone#FALL}, {@code Add Glissando} for {@link SlideZone#GLISSANDO}.
     */
    public static String addSlideLabel(SlideZone zone) {
        return Strings.get(zone == SlideZone.FALL
            ? Strings.ACTION_EDIT_OP_ADD_FALL
            : Strings.ACTION_EDIT_OP_ADD_GLISSANDO);
    }

    /**
     * Names a slide deletion by subtype: {@code Remove Fall} for a {@link
     * StaffElement.Fall}, otherwise {@code Remove Glissando}. A slide is taken off the note that
     * carries it rather than deleted as an element of its own, so it is worded as a removal
     * even though the sibling labels here read {@code Delete}.
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
        return Strings.get(hairpin.getKind() == Hairpin.Kind.CRESCENDO
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
     * Names a tie removal.
     */
    public static String removeTieLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TIE);
    }

    /**
     * Names a beam removal.
     */
    public static String removeBeamLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_BEAM);
    }

    /**
     * Names a tuplet removal, which the user reaches by selecting the tuplet's number or
     * bracket and deleting it.
     */
    public static String removeTupletLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TUPLET);
    }

    /**
     * Names a trill removal.
     */
    public static String removeTrillLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TRILL);
    }

    /**
     * Names an accidental removal.
     */
    public static String removeAccidentalLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_ACCIDENTAL);
    }

    /**
     * Names an articulation removal by type: {@code Remove Staccato} or
     * {@code Remove Accent}.
     */
    public static String removeArticulationLabel(ArticulationType type) {
        return Strings.get(switch (type) {
            case STACCATO -> Strings.ACTION_EDIT_OP_REMOVE_STACCATO;
            case ACCENT -> Strings.ACTION_EDIT_OP_REMOVE_ACCENT;
        });
    }

    /**
     * Names an attachment removal by kind. The switch is exhaustive by sealing on
     * {@link Attachment}, so a new attachment kind fails to compile here.
     * <p>
     * The three kinds that also have a dialog share that dialog's Remove-button key, because
     * the Delete key and the dialog reach the same edit.
     */
    public static String removeAttachmentLabel(Attachment attachment) {
        return Strings.get(switch (attachment) {
            case FermataAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_FERMATA;
            case DynamicAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_DYNAMIC;
            case AnnotationAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION;
            case TempoChangeAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_TEMPO_CHANGE;
            case BeatChangeAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE;
        });
    }

    /**
     * Names a single-element lyric edit by what the syllable had and what it now has:
     * empty → non-empty is {@code Add Lyric}, non-empty → empty is {@code Delete Lyric},
     * and every other transition — including a no-op edit that leaves the text unchanged —
     * is {@code Edit Lyric}.
     *
     * @param beforeText the syllable's text before the edit, {@code ""} if it had none
     * @param afterText  the syllable's text after the edit, {@code ""} if it now has none
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
