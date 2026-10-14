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

import songscribe.Strings;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ArticulationType;
import songscribe.dom.Attachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Slide;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;

/**
 * Names an edit for the Edit menu, in the words the user would use for what they just
 * did.
 *
 * <p>Every method is a pure function of its arguments, so a label may be computed before the
 * edit, after it, or not used.
 *
 * <p>Elements are {@code Delete}d; slides, ties, beams, tuplets, trills, accidentals,
 * articulations and attachments are {@code Remove}d.
 */
public final class OpNames {

    private OpNames() {
    }

    /**
     * Names a deletion by the kinds of the deleted elements: a single element yields the
     * singular {@code Delete <Kind>}; several elements that all share one delete label yield
     * the plural {@code Delete <Kind>s}; a mix yields the generic {@code Delete Elements}.
     * A note and its grace note share the {@code Note} label — {@link #deleteKey} folds the
     * two kinds — so deleting them together is {@code Delete Notes} rather than the generic
     * label.
     *
     * <p>{@code types} is what the user selected, not what the deletion will take: a key
     * change drags the barline it sits behind out with it, and that barline is in a
     * category of its own. Naming the step from the widened range would report
     * {@code Delete Elements} for a gesture the user made on one key change.
     *
     * @param types the types of the elements being deleted, in any order; must not be
     *              empty — a deletion of nothing has no name, and nothing is promised for
     *              an empty list
     * @return the localized name for the deletion
     */
    public static String deleteLabel(List<ElementType> types) {
        var common = deleteKey(types.getFirst().kind());

        for (var type : types) {
            if (!common.equals(deleteKey(type.kind()))) {
                return Strings.get(Strings.ACTION_EDIT_OP_DELETE_ELEMENTS);
            }
        }

        return Strings.get(common, types.size());
    }

    /** A grace note deletes as a note. */
    private static String deleteKey(ElementType.Kind kind) {
        return switch (kind) {
            case NOTE, GRACE_NOTE -> Strings.ACTION_EDIT_OP_DELETE_NOTE;
            case REST -> Strings.ACTION_EDIT_OP_DELETE_REST;
            case BARLINE -> Strings.ACTION_EDIT_OP_DELETE_BARLINE;
            case REPEAT -> Strings.ACTION_EDIT_OP_DELETE_REPEAT;
            case BREATH_MARK -> Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK;
            case KEY_CHANGE -> Strings.ACTION_EDIT_OP_DELETE_KEY_CHANGE;
        };
    }

    /**
     * Names an insertion by the type of the element being inserted. Insertion is always of
     * a single element, so the label is always singular. A grace note is named
     * {@code Add Grace Note} rather than folding into {@code Add Note}.
     *
     * @param type the type being inserted; must belong to one of the categories the pen
     *             can insert — a note, a rest, a barline, a repeat, a breath mark or a key
     *             change
     * @return the localized name for the insertion
     */
    public static String addLabel(ElementType type) {
        if (type.isGraceNote()) {
            return Strings.get(Strings.ACTION_EDIT_OP_ADD_GRACE_NOTE);
        }

        return Strings.get(
            switch (type.kind()) {
                case NOTE, GRACE_NOTE -> Strings.ACTION_EDIT_OP_ADD_NOTE;
                case REST -> Strings.ACTION_EDIT_OP_ADD_REST;
                case BARLINE -> Strings.ACTION_EDIT_OP_ADD_BARLINE;
                case REPEAT -> Strings.ACTION_EDIT_OP_ADD_REPEAT;
                case BREATH_MARK -> Strings.ACTION_EDIT_OP_ADD_BREATH_MARK;
                case KEY_CHANGE -> Strings.ACTION_EDIT_OP_ADD_KEY;
            });
    }

    public static String addSlideLabel(Slide slide) {
        return Strings.get(slide == Slide.FALL
            ? Strings.ACTION_EDIT_OP_ADD_FALL
            : Strings.ACTION_EDIT_OP_ADD_GLISSANDO);
    }

    /** Worded as a removal, not a {@code Delete}, because a slide is not an element of its own. */
    public static String deleteSlideLabel(Slide slide) {
        return Strings.get(slide == Slide.FALL
            ? Strings.ACTION_EDIT_OP_DELETE_FALL
            : Strings.ACTION_EDIT_OP_DELETE_GLISSANDO);
    }

    public static String deleteHairpinLabel(Hairpin hairpin) {
        return Strings.get(hairpin.getKind() == Hairpin.Kind.CRESCENDO
            ? Strings.ACTION_EDIT_OP_DELETE_CRESCENDO
            : Strings.ACTION_EDIT_OP_DELETE_DIMINUENDO);
    }

    public static String deleteEndingLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_DELETE_ENDING);
    }

    public static String deleteLineLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_DELETE_LINE);
    }

    public static String removeTieLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TIE);
    }

    public static String removeBeamLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_BEAM);
    }

    public static String removeTupletLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TUPLET);
    }

    public static String removeTrillLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TRILL);
    }

    public static String removeAccidentalLabel() {
        return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_ACCIDENTAL);
    }

    public static String removeArticulationLabel(ArticulationType type) {
        return Strings.get(switch (type) {
            case STACCATO -> Strings.ACTION_EDIT_OP_REMOVE_STACCATO;
            case ACCENT -> Strings.ACTION_EDIT_OP_REMOVE_ACCENT;
        });
    }

    /** The kinds that also have a dialog share its Remove-button key: both reach the same edit. */
    public static String removeAttachmentLabel(Attachment attachment) {
        return Strings.get(switch (attachment) {
            case FermataAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_FERMATA;
            case DynamicAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_DYNAMIC;
            case AnnotationAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION;
            case TempoChangeAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_TEMPO_CHANGE;
            case BeatChangeAttachment _ -> Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE;
        });
    }

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
