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
 * One edit as the chain walk sees it: where the song changed, in the coordinates of the song the
 * walk reads, and what the change can have moved. The walk decides every note the edit can
 * reach from this alone — see {@link AccidentalChain#walk}.
 *
 * <p>The edited range covers the elements of one line from {@link #start()} up to
 * {@link #end()}, indexed as the song stands after the edit: an insertion covers what it inserted, a
 * modification covers the one element, and a removal is the empty range at the point it removed
 * from.
 *
 * <p>Every edit comes from one of the factories here, each of which states what its edit means;
 * the constructor is private so that no edit exists that a factory did not describe.
 *
 * <p>Immutable.
 */
public final class ChainEdit {

    /**
     * How far the walk goes past the edited range.
     */
    public enum Reach {
        /**
         * To the first untouched note at each position the edit reaches: after that note what
         * is in force is its own sounding alteration, which the edit did not change.
         */
        TO_UNTOUCHED_NOTE,

        /**
         * To the next key change, because the edit changed the key a later note is in — a key
         * change inserted, removed or altered, a line's own key changed, or a line with its own
         * key or a key change removed — and every note up to there is decided against a key
         * that may have moved.
         */
        TO_NEXT_KEY_CHANGE;

        private static Reach of(boolean rekeys) {
            return rekeys ? TO_NEXT_KEY_CHANGE : TO_UNTOUCHED_NOTE;
        }
    }

    private final Cursor start;
    private final int length;
    private final Set<StaffPosition> vacated;
    private final Reach reach;

    private ChainEdit(Cursor start, int length, Set<StaffPosition> vacated, Reach reach) {
        this.start = start;
        this.length = length;
        this.vacated = Set.copyOf(vacated);
        this.reach = reach;
    }

    /**
     * @return where the edited range starts; for a removed line, the start of the line that now
     *     stands at the removed line's index
     */
    public Cursor start() {
        return start;
    }

    /**
     * @return the place one past the edited range, on its line
     */
    public Cursor end() {
        return start.plus(length);
    }

    /**
     * @return every staff position a removed note held, and the position a moved note held
     *     before it moved: the positions whose next note must be decided again even though
     *     nothing in the range now stands at them; unmodifiable
     */
    public Set<StaffPosition> vacated() {
        return vacated;
    }

    /**
     * @return how far the walk goes past the edited range
     */
    public Reach reach() {
        return reach;
    }

    /**
     * The edit that inserted {@code inserted} at {@code start}, the elements now standing at
     * {@code start.index()..start.index() + inserted.size() - 1}.
     *
     * @param start    the place the first inserted element stands at
     * @param inserted the inserted elements, in line order
     * @return the edit, covering the inserted elements, vacating nothing, reaching the next key
     *     change when any of them {@link ElementType#endsAccidentalsInForce() ends what is in
     *     force}
     */
    public static ChainEdit inserted(Cursor start, List<? extends StaffElement> inserted) {
        return new ChainEdit(start, inserted.size(), Set.of(), reachOf(inserted));
    }

    /**
     * The edit that removed {@code removed} from {@code start}.
     *
     * @param start   the place the first removed element stood at, where the element after the
     *                removed ones now stands
     * @param removed the removed elements, in line order
     * @return the edit, an empty range at {@code start}, vacating the position of every removed
     *     note, reaching the next key change when any removed element
     *     {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit removed(Cursor start, List<? extends StaffElement> removed) {
        return new ChainEdit(start, 0, positionsOf(removed), reachOf(removed));
    }

    /**
     * The edit that swapped {@code removed} at {@code start} for {@code inserted}, which now
     * stands at {@code start.index()..start.index() + inserted.size() - 1}.
     *
     * @param start    the place the first removed element stood at and the first inserted one
     *                 stands at
     * @param removed  the removed elements, in line order
     * @param inserted the inserted elements, in line order
     * @return the edit, covering the inserted elements, vacating the position of every removed
     *     note, reaching the next key change when any removed or inserted element
     *     {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit replaced(
        Cursor start, List<? extends StaffElement> removed, List<? extends StaffElement> inserted) {

        return new ChainEdit(
            start, inserted.size(), positionsOf(removed), Reach.of(anyRekeys(removed) || anyRekeys(inserted))
        );
    }

    /**
     * The edit that changed the element at {@code at} in place from {@code before} to
     * {@code after}.
     *
     * @param at     the element's place
     * @param before the element as it stood before the change
     * @param after  the element as it stands after it
     * @return the edit, covering the one element, vacating {@code before}'s position when it is
     *     a note and {@code after} is not a note at that same position, reaching the next key
     *     change when either {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit modified(Cursor at, StaffElement before, StaffElement after) {
        var vacated = new HashSet<StaffPosition>();

        if ((before instanceof NoteElement noteBefore)
                && !((after instanceof NoteElement noteAfter)
                    && noteAfter.getStaffPosition().equals(noteBefore.getStaffPosition()))) {
            vacated.add(noteBefore.getStaffPosition());
        }

        return new ChainEdit(at, 1, vacated, Reach.of(endsInForce(before) || endsInForce(after)));
    }

    /**
     * The edit that changed the key the line at {@code lineIndex} establishes at its own start.
     *
     * @param lineIndex the line's index
     * @return the edit, an empty range at the line's start, vacating nothing, reaching the next
     *     key change
     */
    public static ChainEdit lineRekeyed(int lineIndex) {
        return rekeyingFrom(new Cursor(lineIndex, 0));
    }

    /**
     * The edit that put a key in force at {@code at} without moving any element: what the load
     * pass starts from at the song's first element, at every key change and at every line with
     * its own key.
     *
     * @param at the place the key takes effect at
     * @return the edit, an empty range at that place, vacating nothing, reaching the next key
     *     change
     */
    public static ChainEdit rekeyingFrom(Cursor at) {
        return new ChainEdit(at, 0, Set.of(), Reach.TO_NEXT_KEY_CHANGE);
    }

    /**
     * The edit that inserted {@code line} into its song, with every element it holds.
     *
     * @param line the inserted line; one of its song's lines
     * @return the edit, covering the line's elements, vacating nothing, reaching the next key
     *     change when the line has its own key — every later line that inherits then starts in
     *     it — or when any of its elements
     *     {@link ElementType#endsAccidentalsInForce() ends what is in force}
     */
    public static ChainEdit lineInserted(Line line) {
        var elements = line.getElements();

        return new ChainEdit(
            new Cursor(line.index(), 0), elements.size(), Set.of(),
            Reach.of((line.getKey() != null) || anyRekeys(elements))
        );
    }

    /**
     * The edit that removed {@code removed} from the song, the line that followed it now
     * standing at {@code lineIndex}.
     *
     * @param lineIndex the index the following line now stands at; the line count when the
     *                  removed line was the last
     * @param removed   the removed line
     * @return the edit, an empty range at the start of the line at {@code lineIndex}, vacating
     *     the position of every note the removed line held, reaching the next key change when
     *     the removed line had its own key or held a key change
     */
    public static ChainEdit lineRemoved(int lineIndex, Line removed) {
        var elements = removed.getElements();

        return new ChainEdit(
            new Cursor(lineIndex, 0), 0, positionsOf(elements),
            Reach.of((removed.getKey() != null) || anyRekeys(elements))
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
     * {@code {ACCIDENTAL}}, its after element a note holding a {@link ChainedAccidental}, and
     * its before element a note at the same staff position — because the walk has already
     * decided that note.
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
                inserted(placeOn(insertion.line(), insertion.index()), List.of(insertion.element()));
            case ElementDeletion deletion ->
                removed(placeOn(deletion.line(), deletion.range().begin()), deletion.deletedElements());
            case ElementReplacement replacement -> replaced(
                placeOn(replacement.line(), replacement.index()),
                List.of(replacement.oldElement()), List.of(replacement.newElement())
            );
            case ElementModification modification -> ofModification(modification);
            case LineInsertion insertion -> lineInserted(insertion.line());
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

        return modified(placeOn(modification.line(), modification.index()), before, after);
    }

    /** The place at {@code index} on {@code line}, one of its song's lines. */
    private static Cursor placeOn(Line line, int index) {
        return new Cursor(line.index(), index);
    }

    private static boolean isRestamp(EnumSet<ElementField> fields, StaffElement before, StaffElement after) {
        return fields.equals(RESTAMP_FIELDS)
            && (after instanceof NoteElement noteAfter)
            && (noteAfter.accidentalState() instanceof ChainedAccidental)
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

    private static Reach reachOf(List<? extends StaffElement> elements) {
        return Reach.of(anyRekeys(elements));
    }

    private static boolean anyRekeys(List<? extends StaffElement> elements) {
        return elements.stream().anyMatch(ChainEdit::endsInForce);
    }

    private static boolean endsInForce(StaffElement element) {
        return element.getType().endsAccidentalsInForce();
    }
}
