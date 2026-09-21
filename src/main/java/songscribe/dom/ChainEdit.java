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
package songscribe.dom;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.engraving.StaffPosition;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.DiminuendoRemoval;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;

/**
 * One edit as the chain walk sees it: where the song changed, in the coordinates of the
 * {@link AccidentalScanView view} the walk reads, and what the change can have moved. The
 * walk decides every note the edit can reach from this alone — see
 * {@link AccidentalChain#walk}.
 *
 * <p>The edited range is on the line at {@code lineIndex}, {@code from} inclusive to
 * {@code to} exclusive, indexed as the view answers after the edit: an insertion covers what it
 * inserted, a modification covers the one element, and a removal is the empty range at the
 * point it removed from.
 *
 * @param lineIndex the index of the edited line among the view's lines; for a removed line,
 *                  the index the following line now stands at
 * @param from      the first index of the edited range on that line
 * @param to        one past the last index of the edited range; equal to {@code from} for a
 *                  removal
 * @param vacated   every staff position a removed note held, and the position a moved note
 *                  held before it moved: the positions whose next note must be decided again
 *                  even though nothing in the range now stands at them
 * @param rekeys    whether the edit changed the key a later note is in — a key change
 *                  inserted, removed or altered, a line's own key changed, or a line with its
 *                  own key or a key change removed — so that every note up to the next key
 *                  change must be decided again
 * @invariant {@code 0 <= from <= to}
 */
public record ChainEdit(int lineIndex, int from, int to, Set<StaffPosition> vacated, boolean rekeys) {

    public ChainEdit {
        vacated = Set.copyOf(vacated);
    }

    /**
     * The edit that inserted {@code inserted} at {@code index}, the elements now standing at
     * {@code index..index + inserted.size() - 1}.
     *
     * @param lineIndex the edited line's index
     * @param index     the index the first inserted element stands at
     * @param inserted  the inserted elements, in line order
     * @return the edit, covering the inserted elements, vacating nothing, rekeying when any of
     *     them {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit inserted(int lineIndex, int index, List<? extends StaffElement> inserted) {
        return new ChainEdit(lineIndex, index, index + inserted.size(), Set.of(), anyRekeys(inserted));
    }

    /**
     * The edit that removed {@code removed} from {@code index}.
     *
     * @param lineIndex the edited line's index
     * @param index     the index the first removed element stood at, where the element after
     *                  the removed ones now stands
     * @param removed   the removed elements, in line order
     * @return the edit, an empty range at {@code index}, vacating the position of every removed
     *     note, rekeying when any removed element
     *     {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit removed(int lineIndex, int index, List<? extends StaffElement> removed) {
        return new ChainEdit(lineIndex, index, index, positionsOf(removed), anyRekeys(removed));
    }

    /**
     * The edit that swapped {@code removed} at {@code index} for {@code inserted}, which now
     * stands at {@code index..index + inserted.size() - 1}.
     *
     * @param lineIndex the edited line's index
     * @param index     the index the first removed element stood at and the first inserted one
     *                  stands at
     * @param removed   the removed elements, in line order
     * @param inserted  the inserted elements, in line order
     * @return the edit, covering the inserted elements, vacating the position of every removed
     *     note, rekeying when any removed or inserted element
     *     {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit replaced(int lineIndex, int index,
        List<? extends StaffElement> removed, List<? extends StaffElement> inserted) {

        return new ChainEdit(
            lineIndex, index, index + inserted.size(), positionsOf(removed),
            anyRekeys(removed) || anyRekeys(inserted)
        );
    }

    /**
     * The edit that changed the element at {@code index} in place from {@code before} to
     * {@code after}.
     *
     * @param lineIndex the edited line's index
     * @param index     the element's index
     * @param before    the element as it stood before the change
     * @param after     the element as it stands after it
     * @return the edit, covering the one element, vacating {@code before}'s position when it is
     *     a note and {@code after} is not a note at that same position, rekeying when either
     *     {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit modified(int lineIndex, int index, StaffElement before, StaffElement after) {
        var vacated = new HashSet<StaffPosition>();

        if ((before instanceof NoteElement noteBefore)
                && !((after instanceof NoteElement noteAfter)
                    && noteAfter.getStaffPosition().equals(noteBefore.getStaffPosition()))) {
            vacated.add(noteBefore.getStaffPosition());
        }

        return new ChainEdit(lineIndex, index, index + 1, vacated, endsInForce(before) || endsInForce(after));
    }

    /**
     * The edit that changed the key the line at {@code lineIndex} establishes at its own start.
     *
     * @param lineIndex the line's index
     * @return the edit, an empty range at the line's start, vacating nothing, rekeying
     */
    public static ChainEdit lineRekeyed(int lineIndex) {
        return new ChainEdit(lineIndex, 0, 0, Set.of(), true);
    }

    /**
     * The edit that removed {@code removed} from the song, the line that followed it now
     * standing at {@code lineIndex}.
     *
     * @param lineIndex the index the following line now stands at; the line count when the
     *                  removed line was the last
     * @param removed   the removed line
     * @return the edit, an empty range at the start of the line at {@code lineIndex}, vacating
     *     the position of every note the removed line held, rekeying when the removed line had
     *     its own key or held a key change
     */
    public static ChainEdit lineRemoved(int lineIndex, Line removed) {
        var elements = removed.getElements();

        return new ChainEdit(
            lineIndex, 0, 0, positionsOf(elements), (removed.getKey() != null) || anyRekeys(elements)
        );
    }

    /**
     * The edit a recorded mutation made, in the live song's coordinates, or null when the
     * mutation changes nothing the walk reads.
     *
     * <p>Every element and line mutation that moves a note, a key or an accidental maps to one
     * of the factories above. An {@link ElementModification} counts only when its fields include
     * {@link ElementField#PITCH}, {@link ElementField#ACCIDENTAL} or {@link ElementField#KEY},
     * and not when it is the walk's own restamp — its fields exactly
     * {@code {ACCIDENTAL}}, its after element a {@link AccidentalState.Stamped Stamped} note,
     * and its before element a note at the same staff position — because the walk has already
     * decided that note. A line insertion is the insertion of the line's elements, rekeying also
     * when the line has its own key, since every later line that inherits then starts in it.
     *
     * <p>The switch is exhaustive with no default arm, so a new mutation kind does not compile
     * until it is classified here.
     *
     * @param mutation the recorded mutation
     * @return the edit, or null for a mutation that moves no note, key or accidental: a span,
     *     tie, beaming, tuplet or hairpin mutation, a layout, font, lyrics or metadata change,
     *     an element modification of other fields, or the walk's own restamp
     */
    public static @Nullable ChainEdit of(Mutation mutation) {
        return switch (mutation) {
            case ElementInsertion insertion ->
                inserted(insertion.line().index(), insertion.index(), List.of(insertion.element()));
            case ElementDeletion deletion ->
                removed(deletion.line().index(), deletion.range().begin(), deletion.deletedElements());
            case ElementReplacement replacement -> replaced(
                replacement.line().index(), replacement.index(),
                List.of(replacement.oldElement()), List.of(replacement.newElement())
            );
            case ElementModification modification -> ofModification(modification);
            case LineInsertion insertion -> {
                var line = insertion.line();
                var edit = inserted(line.index(), 0, line.getElements());

                yield (line.getKey() == null)
                    ? edit
                    : new ChainEdit(edit.lineIndex(), edit.from(), edit.to(), edit.vacated(), true);
            }
            case LineDeletion deletion -> lineRemoved(deletion.lineIndex(), deletion.deletedLine());
            case LineKeyChange keyChange -> lineRekeyed(keyChange.line().index());
            case LineLayoutChange _, SpanAddition _, SpanRemoval _, BeamingAddition _, BeamingRemoval _,
                 TieAddition _, TieRemoval _, TupletAddition _, TupletRemoval _,
                 CrescendoAddition _, CrescendoRemoval _, DiminuendoAddition _, DiminuendoRemoval _,
                 MetadataChange _, FontChange _, LayoutChange _, LyricsChange _ -> null;
        };
    }

    private static final EnumSet<ElementField> WALKED_FIELDS =
        EnumSet.of(ElementField.PITCH, ElementField.ACCIDENTAL, ElementField.KEY);

    private static final EnumSet<ElementField> RESTAMP_FIELDS = EnumSet.of(ElementField.ACCIDENTAL);

    private static @Nullable ChainEdit ofModification(ElementModification modification) {
        var fields = modification.fields();

        if (Collections.disjoint(fields, WALKED_FIELDS)) {
            return null;
        }

        var before = modification.beforeElement();
        var after = modification.afterElement();

        if (isRestamp(fields, before, after)) {
            return null;
        }

        return modified(modification.line().index(), modification.index(), before, after);
    }

    private static boolean isRestamp(EnumSet<ElementField> fields, StaffElement before, StaffElement after) {
        return fields.equals(RESTAMP_FIELDS)
            && (after instanceof NoteElement noteAfter)
            && (noteAfter.accidentalState() instanceof AccidentalState.Stamped)
            && (before instanceof NoteElement noteBefore)
            && noteBefore.getStaffPosition().equals(noteAfter.getStaffPosition());
    }

    private static Set<StaffPosition> positionsOf(List<? extends StaffElement> elements) {
        var positions = new HashSet<StaffPosition>();

        for (var element : elements) {
            if (element instanceof NoteElement note) {
                positions.add(note.getStaffPosition());
            }
        }

        return positions;
    }

    private static boolean anyRekeys(List<? extends StaffElement> elements) {
        return elements.stream().anyMatch(ChainEdit::endsInForce);
    }

    private static boolean endsInForce(StaffElement element) {
        return element.getType().endsAccidentalsInForce();
    }
}
