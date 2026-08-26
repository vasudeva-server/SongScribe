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

package songscribe.ui.edit;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;

/**
 * Runs the reconciliation an edit that moves a key owes, over every line that key reaches.
 *
 * <p>Six edits move a key: changing a line's own key, writing a mid-line key change, changing one
 * already written, deleting elements, deleting a line, and pasting a fragment that carries a key
 * change. Each reaches the line it lands on plus every line inheriting from it, up to the first
 * line with a key of its own — the stopping rule in {@code docs/key-signatures.md} — which makes
 * them the only edits in the program whose accidental reconciliation spans more than one line.
 * What they share, and what lives here, is the sequence that reach forces:
 *
 * <ol>
 *   <li>reconcile the whole reach with nothing suppressed, which is pure and pre-mutation, so its
 *       removals can be read off before anyone is asked anything;</li>
 *   <li>ask the notator <b>once</b> about the restatements those removals strand — one dialog for
 *       the whole reach, never one per line;</li>
 *   <li>reconcile again under the accepted removals, but only when the answer moves the result;
 *       </li>
 *   <li>record the changes on every reached line, plus the accepted restatements sitting past the
 *       reach's end;</li>
 *   <li>remove the mid-line key changes the move strands — each one now restating the key already
 *       in effect before it, so it draws nothing — together with the elements they are paired
 *       with.</li>
 * </ol>
 *
 * <p>An edit describes its reach as a list of {@link AccidentalReconciliation.ReachedLine}, in
 * song order, and gets a {@link Confirmed} back. That value carries the reach it answered, so the
 * removals in step 5 travel with the answer to step 2 and cannot be lost at a method boundary
 * between them: a caller that reconciles in one method and mutates in another passes one value,
 * not a reconciliation plus a plan it has to remember to carry alongside.
 *
 * <p>Steps 4 and 5 are one call, {@link Confirmed#apply()}, because their order is not the
 * caller's to choose: recording reads each note's index off the live line, so it has to happen
 * while the elements the sweep is about to remove are still standing.
 */
public final class KeyChangeReconciliation {

    private KeyChangeReconciliation() {
    }

    /**
     * A key-moving edit's reconciliation, asked about and ready to commit.
     *
     * @param decision            the notator's answer, which the caller must honor: a cancelled
     *                            decision means the edit does not happen at all, and nothing has
     *                            been mutated to undo
     * @param reach               the lines this answers, in song order, each carrying the ranges
     *                            the move strands on it
     * @param changes             what to record on each reached line, in the same order
     * @param lineRecordedByCaller the one line the caller records itself, or null when
     *                            {@link #apply()} records them all
     */
    public record Confirmed(
        AccidentalRestatements.Decision decision,
        List<AccidentalReconciliation.ReachedLine> reach,
        List<AccidentalReconciliation.ReconciledLine> changes,
        @Nullable Line lineRecordedByCaller) {

        /**
         * Nothing asked, nothing to record and nothing stranded — for an edit that has already
         * been reconciled as a whole elsewhere and must not reconcile a second time, which is what
         * a paste-replace's deletion is.
         */
        public static final Confirmed PROCEED = new Confirmed(
            AccidentalRestatements.Decision.PROCEED, List.of(), List.of(), null);

        // Written out rather than compact: NullAway does not carry the type-use @Nullable
        // annotation from the record header onto the synthesized canonical constructor.
        public Confirmed(
            AccidentalRestatements.Decision decision,
            List<AccidentalReconciliation.ReachedLine> reach,
            List<AccidentalReconciliation.ReconciledLine> changes,
            @Nullable Line lineRecordedByCaller) {

            this.decision = decision;
            this.reach = List.copyOf(reach);
            this.changes = List.copyOf(changes);
            this.lineRecordedByCaller = lineRecordedByCaller;
        }

        /**
         * Returns this reconciliation with {@code line} left to the caller to record — for an edit
         * that removes elements from that line, whose note indices have to be read before the
         * elements go, which is {@link AccidentalMaterializer#applyIfAccepted} rather than
         * {@link AccidentalMaterializer#commit}.
         *
         * <p>Naming the line rather than a position is what keeps the two from disagreeing: the
         * caller records the line it holds, and {@link #apply()} skips that same line.
         *
         * @param line the line the caller records itself
         * @return the same answer and the same reach, with {@link #apply()} skipping {@code line}
         */
        public Confirmed withChangesRecordedByCaller(Line line) {
            return new Confirmed(decision, reach, changes, line);
        }

        /**
         * @return whether the notator cancelled, in which case the caller must abandon the edit
         *     entirely — nothing here has been mutated
         */
        public boolean isCancelled() {
            return decision.isCancelled();
        }

        /**
         * @param line the line to look up
         * @return the accidentals {@code line} owes, empty when this reach does not cover it
         */
        public List<AccidentalReconciliation.AccidentalChange> changesFor(Line line) {
            for (var reconciled : changes) {
                if (reconciled.line() == line) {
                    return reconciled.changes();
                }
            }

            return List.of();
        }

        /**
         * Records every reached line's accidentals and then removes the key changes the move
         * strands, in that order. Call inside the edit's modification bracket, and <b>before</b>
         * the edit itself, so undo — which replays a step in reverse — puts the accidentals back
         * only once the notes they sit on are back.
         *
         * <p>The order is not the caller's to choose, which is why this is one call: recording
         * reads each note's index off the live line, so it has to run while the elements the sweep
         * removes are still on it.
         *
         * <p>A caller that has to defer one line's sweep until after it has moved that line's
         * elements uses {@link #commit()}, {@link #sweepExcept} and {@link #sweepDeferred}
         * instead.
         *
         * @effects mutates every reached line, into the open modification bracket
         */
        public void apply() {
            commit();
            sweep();
        }

        /**
         * Records the reached lines' accidentals, plus the accepted restatements sitting past the
         * reach's end. The line named by {@link #withChangesRecordedByCaller} is skipped, since
         * the caller has recorded it against its own pre-removal indices.
         *
         * <p>Runs before any sweep, and never after one: every note recorded here is read off the
         * live line to find the index its mutation names.
         *
         * @effects mutates every recorded line, into the open modification bracket
         */
        public void commit() {
            for (var reconciled : changes) {
                if (reconciled.line() != lineRecordedByCaller) {
                    AccidentalMaterializer.commit(reconciled.line(), reconciled.changes());
                }
            }

            // Every line of the reach is skipped here: what is left is the lines the edit never
            // reached, which hold accepted restatements only because the scan runs to the end of
            // the song.
            AccidentalRestatements.commitOtherLines(decision, lines());
        }

        /**
         * Removes the key changes the move strands on every reached line, each together with the
         * element it is paired with.
         *
         * <p>The document is briefly in a state where the key has not moved yet and a key change
         * that restates it is already gone. Nothing observes it: what a line inherits is derived
         * state the key move recomputes, and undo replays the bracket's mutations in reverse, each
         * restoring what it recorded.
         *
         * @effects mutates every reached line the move strands a key change on, into the open
         *     modification bracket
         */
        public void sweep() {
            for (var reached : reach) {
                reached.line().deleteRanges(reached.removedRanges());
            }
        }

        /**
         * Sweeps every reached line but {@code deferred}, whose own removals wait for
         * {@link #sweepDeferred}.
         *
         * <p>For an edit that goes on to move the elements of one line after this: the ranges it
         * strands there name pre-edit indices, so removing them now would take the wrong elements,
         * and removing them before the edit reads that line would move the indices the edit is
         * measuring against.
         *
         * @param deferred the line whose sweep waits
         * @effects mutates every reached line but {@code deferred}, into the open modification
         *     bracket
         */
        public void sweepExcept(Line deferred) {
            for (var reached : reach) {
                if (reached.line() != deferred) {
                    reached.line().deleteRanges(reached.removedRanges());
                }
            }
        }

        /**
         * Removes the key changes the move strands on {@code deferred}, each range shifted by
         * {@code indexShift} first.
         *
         * <p>Every such range begins at or past the first index the edit leaves standing after
         * itself, so an edit that inserts or removes elements at that boundary moves all of them
         * by the same amount, and the shift is that amount: positive where the edit left more
         * elements ahead of them than it found, negative where it left fewer.
         *
         * @param deferred   the line whose sweep was deferred
         * @param indexShift how far its ranges have moved since the reach reported them
         * @effects mutates {@code deferred}, into the open modification bracket
         */
        public void sweepDeferred(Line deferred, int indexShift) {
            for (var reached : reach) {
                if (reached.line() == deferred) {
                    reached.line().deleteRanges(
                        reached.removedRanges().stream()
                            .map(range -> range.shiftedBy(indexShift))
                            .toList());
                }
            }
        }

        /**
         * @return every line the reach covers, in song order
         */
        private List<Line> lines() {
            return reach.stream().map(AccidentalReconciliation.ReachedLine::line).toList();
        }
    }

    /**
     * Reconciles {@code reach}, asks the notator once about every restatement it strands, and
     * returns what to commit.
     *
     * <p>Mutates nothing, whatever the answer, and must be called <b>before</b> the edit's
     * modification bracket opens — a dialog may never be open inside one, and a cancelled answer
     * has to leave no undo step behind.
     *
     * @param parent the component to parent the dialog on, or null when there is no owning window
     * @param alsoEdited what this edit changes that the reconciliation cannot see — the elements a
     *     deletion removes, which carry accidentals of their own. Empty for an edit that only
     *     moves the key. Folded into the same dialog, so one Delete asks once
     * @param reach the lines the edit reaches, in song order
     * @return the decision, the changes to record and the reach they answer, which the caller must
     *     abandon entirely when {@link Confirmed#isCancelled()}
     */
    public static Confirmed confirm(
        @Nullable Component parent,
        List<AccidentalRestatements.EditedLine> alsoEdited,
        List<AccidentalReconciliation.ReachedLine> reach) {

        var reconciled = AccidentalReconciliation.reconcileReach(
            reach, AccidentalReconciliation.RestatementRemoval.NONE);

        var edited = new ArrayList<>(alsoEdited);
        edited.addAll(AccidentalRestatements.accidentalsClearedBy(reconciled));

        var decision = AccidentalRestatements.confirm(parent, edited);

        if (decision.isCancelled()) {
            return new Confirmed(decision, reach, List.of(), null);
        }

        // Reconciled a second time only when the answer moves the result: accepted restatements
        // are cleared by the same walk everything else travels.
        var changes = decision.answer() == AccidentalRestatements.Answer.YES
            ? AccidentalReconciliation.reconcileReach(reach, decision.removal())
            : reconciled;

        return new Confirmed(decision, reach, changes, null);
    }
}
