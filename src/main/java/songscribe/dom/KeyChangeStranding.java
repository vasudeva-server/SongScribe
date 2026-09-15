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

import java.util.ArrayList;
import java.util.List;

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * The mid-line key changes an edit that moves a key strands, and the sweep that removes them.
 *
 * <p>A key change that restates the key already in effect draws nothing and is deliberately
 * not representable, so the edit that strands one removes it, together with the element it is
 * paired with, in the same undo step as the edit itself — see {@code docs/key-changes.md},
 * <em>A mid-line change never restates the key already in effect</em>. This type is that rule
 * in two halves: each factory is the pure, pre-mutation query that says what an edit will
 * strand, computed before anything commits; {@link #sweep} and its two deferred forms are the
 * removal, called inside the edit's modification bracket.
 *
 * <p>Nothing here is put to the notator. A stranded key change carries no information the
 * document does not already hold, so removing it needs no consent and is never refused.
 *
 * <p>Every factory asks the song what inherits from the line the edit lands on, supposing that
 * line leaves off in the key the edit will leave it in — {@link Song#inheritanceFrom} — so a
 * stranding reaches exactly the lines the song re-derives once the edit commits. A line the edit
 * leaves running in the key it already ran in has nothing stranded on it: a committed line never
 * holds a key change restating the key in effect where it stands, and a line whose running key is
 * unchanged has the same key in effect at every element.
 *
 * <p>Removing a stranded key change can neither create nor hide another, and moves the key in
 * effect nowhere — it deletes a step that steps to the value already held. That is what lets the
 * whole list be computed once, before the edit, without the edit's own removals invalidating it.
 *
 * @param lines every line the edit strands a key change on or could have, in song order, each
 *              carrying the ranges to remove from it
 */
public record KeyChangeStranding(List<Stranded> lines) {

    /**
     * One line a key move reaches, with what the move strands on it.
     *
     * @param line   the line, in its pre-edit state
     * @param ranges the inclusive ranges to remove from {@code line}, ascending and pairwise
     *               disjoint; each is a stranded key change widened to the pair it belongs to,
     *               as {@link StaffElementRun#redundantKeyChangeRanges(Key)} reports them. Empty
     *               when the move strands nothing on the line
     */
    public record Stranded(Line line, List<ElementRange> ranges) {

        public Stranded {
            ranges = List.copyOf(ranges);
        }

        /**
         * The stranding {@code runningKey} causes on {@code line} once the line runs in it.
         *
         * @param line       the line, in its pre-edit state
         * @param runningKey the key {@code line} will run in once the edit commits
         * @return the line and its stranded ranges
         */
        static Stranded under(Line line, Key runningKey) {
            return new Stranded(line, line.redundantKeyChangeRanges(runningKey));
        }
    }

    public KeyChangeStranding {
        lines = List.copyOf(lines);
    }

    /**
     * Returns what a change to {@code line}'s own key strands: on {@code line} itself, under the
     * key it will then run in, and on every line inheriting from it.
     *
     * <p>A change to a line's own key moves the key it leaves off in only when the line holds no
     * mid-line key change; one that does fixes its end key, so the change reaches no further
     * than that mid-line change does. That tail is derived here rather than asked of the caller.
     *
     * <p>Pure and pre-mutation: the lines are read as they stand and nothing is written to them.
     *
     * @param line       the line whose own key changes; one of its song's lines
     * @param runningKey the key {@code line} will run in once the change commits
     * @return the stranding, whose {@link #lines()} always begin with {@code line}
     */
    public static KeyChangeStranding ofLineKeyChange(Line line, Key runningKey) {
        var stranded = new ArrayList<Stranded>();
        stranded.add(Stranded.under(line, runningKey));
        stranded.addAll(inheritingLinesAfter(line, line.keyAtEndUnder(runningKey)));

        return new KeyChangeStranding(stranded);
    }

    /**
     * Returns what deleting {@code deletedLine} strands: on every line after it whose key the
     * deletion moves, and never on {@code deletedLine} itself, which the deletion takes away
     * whole.
     *
     * <p>The lines after it then inherit the key the line before it leaves off in. When nothing
     * precedes it, the line after it becomes the first line and materializes the key it was
     * already inheriting — {@code deletedLine}'s own end key — so no key moves and nothing is
     * stranded.
     *
     * <p>Pure and pre-mutation, like every factory here.
     *
     * @param deletedLine the line about to be deleted; one of its song's lines
     * @return the stranding; no line in it has a range when the deletion moves no key
     */
    public static KeyChangeStranding ofLineDeletion(Line deletedLine) {
        var lineLeavingOffInInheritedKey = deletedLine.isFirst()
            ? deletedLine
            : deletedLine.getSong().getLine(deletedLine.index() - 1);

        return new KeyChangeStranding(
            inheritingLinesAfter(deletedLine, lineLeavingOffInInheritedKey.keyAtEndOfLine()));
    }

    /**
     * Returns what an edit that adds, removes or replaces a mid-line key change on {@code line}
     * strands: {@code onLine} on {@code line} itself, then whatever the key the line then leaves
     * off in strands on every line inheriting from it.
     *
     * <p>Such an edit moves the key from its own index forward and leaves the key in effect at
     * every earlier position as it was, so what it strands along its own line is a question only
     * that edit can answer — {@link StaffElementRun#redundantKeyChangeRanges(int, Key)} bounded
     * to the indices after it — and it is passed in rather than derived. What it strands
     * downstream follows from the end key alone, and is derived here.
     *
     * <p>Pure and pre-mutation, like every factory here.
     *
     * @param line           the line the edit lands on; one of its song's lines
     * @param onLine         the ranges the edit strands on {@code line}, in pre-edit indices,
     *                       ascending and pairwise disjoint; empty when it strands nothing there
     * @param keyAtEndOfLine the key {@code line} will leave off in once the edit commits
     * @return the stranding, whose {@link #lines()} always begin with {@code line}
     */
    public static KeyChangeStranding ofMidLineEdit(
        Line line, List<ElementRange> onLine, Key keyAtEndOfLine) {

        var stranded = new ArrayList<Stranded>();
        stranded.add(new Stranded(line, onLine));
        stranded.addAll(inheritingLinesAfter(line, keyAtEndOfLine));

        return new KeyChangeStranding(stranded);
    }

    /**
     * Removes every stranded key change on every line here, each together with the element it
     * is paired with.
     *
     * <p>The document is briefly in a state where the key has not moved yet and a key change
     * that restates it is already gone. Nothing observes it: what a line inherits is derived
     * state the key move recomputes, and undo replays the bracket's mutations in reverse, each
     * restoring what it recorded.
     *
     * <p>An edit that goes on to move the elements of one of these lines uses
     * {@link #sweepExcept} and {@link #sweepDeferred} instead.
     *
     * @throws IllegalStateException if the song has neither an open modification bracket nor
     *     suspended mutation tracking
     * @effects mutates every line here that has a stranded range, into the open modification
     *     bracket
     */
    public void sweep() {
        for (var stranded : lines) {
            stranded.line().deleteRanges(stranded.ranges());
        }
    }

    /**
     * Sweeps every line here but {@code deferred}, whose own removals wait for
     * {@link #sweepDeferred}.
     *
     * <p>For an edit that goes on to move the elements of one line after this: the ranges it
     * strands there name pre-edit indices, so removing them now would take the wrong elements,
     * and removing them before the edit reads that line would move the indices the edit is
     * measuring against.
     *
     * @param deferred the line whose sweep waits
     * @throws IllegalStateException if the song has neither an open modification bracket nor
     *     suspended mutation tracking
     * @effects mutates every line here but {@code deferred} that has a stranded range, into the
     *     open modification bracket
     */
    public void sweepExcept(Line deferred) {
        for (var stranded : lines) {
            if (stranded.line() != deferred) {
                stranded.line().deleteRanges(stranded.ranges());
            }
        }
    }

    /**
     * Removes the stranded key changes on {@code deferred}, each range shifted by
     * {@code indexShift} first.
     *
     * <p>Every such range begins at or past the first index the edit leaves standing after
     * itself, so an edit that inserts or removes elements at that boundary moves all of them by
     * the same amount, and the shift is that amount: positive where the edit left more elements
     * ahead of them than it found, negative where it left fewer.
     *
     * @param deferred   the line whose sweep {@link #sweepExcept} deferred
     * @param indexShift how far its ranges have moved since the factory reported them
     * @throws IllegalStateException if the song has neither an open modification bracket nor
     *     suspended mutation tracking
     * @effects mutates {@code deferred}, into the open modification bracket, when it has a
     *     stranded range; nothing when {@code deferred} is not among the lines here
     */
    public void sweepDeferred(Line deferred, int indexShift) {
        for (var stranded : lines) {
            if (stranded.line() == deferred) {
                stranded.line().deleteRanges(
                    stranded.ranges().stream()
                        .map(range -> range.shiftedBy(indexShift))
                        .toList());
            }
        }
    }

    /**
     * The lines after {@code line} that run in the key it leaves off in, each with what that key
     * strands on it once {@code line} leaves off in {@code keyAtEndOfLine}, in song order.
     */
    private static List<Stranded> inheritingLinesAfter(Line line, Key keyAtEndOfLine) {
        return line.getSong().inheritanceFrom(line, keyAtEndOfLine).inheriting().stream()
            .map(inheritance -> Stranded.under(inheritance.line(), inheritance.inheritedKey()))
            .toList();
    }
}
