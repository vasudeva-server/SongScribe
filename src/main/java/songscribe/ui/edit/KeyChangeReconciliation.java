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
 * <p>Three edits move a key: changing a line's own key, writing a mid-line key signature, and
 * deleting one. Each reaches the line it lands on plus every line inheriting from it, up to the
 * first line with a key of its own — the stopping rule in {@code docs/key-signatures.md} — which
 * makes them the only edits in the program whose accidental reconciliation spans more than one
 * line. What they share, and what lives here, is the sequence that reach forces:
 *
 * <ol>
 *   <li>reconcile the whole reach with nothing suppressed, which is pure and pre-mutation, so its
 *       removals can be read off before anyone is asked anything;</li>
 *   <li>ask the notator <b>once</b> about the restatements those removals strand — one dialog for
 *       the whole reach, never one per line;</li>
 *   <li>reconcile again under the accepted removals, but only when the answer moves the result;
 *       </li>
 *   <li>record the changes on every reached line, plus the accepted restatements sitting past the
 *       reach's end.</li>
 * </ol>
 *
 * <p>What differs between the three is only how the reach's <em>head</em> is reconciled — an
 * insertion or a deletion projects the line it lands on, while a line-key change modifies it in
 * place — which is why the reconciliation itself arrives as a {@link ReachReconciler} rather than
 * being spelled out here.
 */
public final class KeyChangeReconciliation {

    /** The first line of a reach is the line the edit lands on; the rest inherit from it. */
    private static final int HOST_LINE_INDEX = 0;

    private KeyChangeReconciliation() {
    }

    /**
     * Reconciles a key-moving edit over its whole reach, under the restatement removals the
     * notator has accepted.
     *
     * <p>Called twice per edit — once with {@link AccidentalReconciliation.RestatementRemoval#NONE}
     * to find what there is to ask about, and again with the answer — so it must be pure and read
     * the lines as they still stand.
     */
    @FunctionalInterface
    public interface ReachReconciler {

        /**
         * @param removal the restatements the notator accepted, or
         *     {@link AccidentalReconciliation.RestatementRemoval#NONE} on the first pass
         * @return one entry per reached line, the line the edit lands on first
         */
        List<AccidentalReconciliation.ReconciledLine> reconcile(
            AccidentalReconciliation.RestatementRemoval removal);
    }

    /**
     * Who records the line the edit lands on. An edit that removes elements has to capture that
     * line's note indices against the pre-removal line, which only it can do, so it records the
     * host itself and {@link #commit} must skip it. Carrying that in the value is what keeps a
     * caller from having to pick between two commit methods and silently recording the host's
     * changes twice, or not at all.
     */
    public enum HostOwner {

        /** {@link #commit} records the host line along with the rest of the reach. */
        RECONCILIATION,

        /** The caller has already recorded the host line; {@link #commit} skips it. */
        CALLER
    }

    /**
     * A key-moving edit's reconciliation, asked about and ready to commit.
     *
     * @param decision the notator's answer, which the caller must honor: a cancelled decision
     *     means the edit does not happen at all, and nothing has been mutated to undo
     * @param changes what to record on each reached line, the line the edit lands on first
     * @param hostOwner who records the line the edit lands on
     */
    public record Confirmed(
        AccidentalRestatements.Decision decision,
        List<AccidentalReconciliation.ReconciledLine> changes,
        HostOwner hostOwner) {

        /**
         * Nothing asked and nothing to record — for an edit that has already been reconciled as a
         * whole elsewhere and must not reconcile a second time, which is what a paste-replace's
         * deletion is.
         */
        public static final Confirmed PROCEED = new Confirmed(
            AccidentalRestatements.Decision.PROCEED, List.of(), HostOwner.RECONCILIATION);

        public Confirmed {
            changes = List.copyOf(changes);
        }

        /**
         * Returns this reconciliation with the host line left to the caller — for a deletion,
         * whose host line needs its note indices read before the elements go.
         *
         * @return the same decision and changes, with {@link #commit} skipping the host line
         */
        public Confirmed withHostRecordedByCaller() {
            return new Confirmed(decision, changes, HostOwner.CALLER);
        }

        /**
         * @return {@code true} when the notator cancelled, in which case the edit must not happen
         *     at all
         */
        public boolean isCancelled() {
            return decision.isCancelled();
        }

        /**
         * @return the changes owed by the line the edit lands on, empty when the reach is empty
         */
        public List<AccidentalReconciliation.AccidentalChange> hostChanges() {
            return changes.isEmpty() ? List.of() : changes.get(HOST_LINE_INDEX).changes();
        }

        /**
         * @return the lines {@link #commit} records, which is the whole reach unless the caller
         *     owns the host line
         */
        private List<AccidentalReconciliation.ReconciledLine> linesToRecord() {
            if (hostOwner == HostOwner.RECONCILIATION || changes.isEmpty()) {
                return changes;
            }

            return changes.subList(HOST_LINE_INDEX + 1, changes.size());
        }

        /**
         * @return every line the reach covers, in song order
         */
        private List<Line> lines() {
            return changes.stream().map(AccidentalReconciliation.ReconciledLine::line).toList();
        }
    }

    /**
     * Reconciles {@code reconcileReach}, asks the notator once about every restatement it strands,
     * and returns what to commit.
     *
     * <p>Mutates nothing, whatever the answer, and must be called <b>before</b> the edit's
     * modification bracket opens — a dialog may never be open inside one, and a cancelled answer
     * has to leave no undo step behind.
     *
     * @param parent the component to parent the dialog on, or null when there is no owning window
     * @param alsoEdited what this edit changes that the reconciliation cannot see — the elements a
     *     deletion removes, which carry accidentals of their own. Empty for an edit that only
     *     moves the key. Folded into the same dialog, so one Delete asks once
     * @param reconcileReach the edit's own reconciliation over its whole reach
     * @return the decision and the changes to record, which the caller must abandon entirely when
     *     {@link Confirmed#isCancelled()}
     */
    public static Confirmed confirm(
        @Nullable Component parent,
        List<AccidentalRestatements.EditedLine> alsoEdited,
        ReachReconciler reconcileReach) {

        var reconciled = reconcileReach.reconcile(AccidentalReconciliation.RestatementRemoval.NONE);

        var edited = new ArrayList<>(alsoEdited);
        edited.addAll(AccidentalRestatements.accidentalsClearedBy(reconciled));

        var decision = AccidentalRestatements.confirm(parent, edited);

        if (decision.isCancelled()) {
            return new Confirmed(decision, List.of(), HostOwner.RECONCILIATION);
        }

        // Reconciled a second time only when the answer moves the result: accepted restatements
        // are cleared by the same walk everything else travels.
        var changes = decision.answer() == AccidentalRestatements.Answer.YES
            ? reconcileReach.reconcile(decision.removal())
            : reconciled;

        return new Confirmed(decision, changes, HostOwner.RECONCILIATION);
    }

    /**
     * Records the reached lines' changes, plus the accepted restatements sitting past the reach's
     * end. Call inside the edit's modification bracket, and <b>before</b> the edit itself, so
     * undo — which replays a step in reverse — puts the accidentals back only once the notes they
     * sit on are back.
     *
     * <p>Which lines those are is {@code confirmed}'s to say. An edit that removes elements has to
     * capture its host line's note indices against the pre-removal line, which is
     * {@link AccidentalMaterializer#applyIfAccepted} rather than
     * {@link AccidentalMaterializer#commit}; such a caller records the host itself and marks the
     * reconciliation {@link Confirmed#withHostRecordedByCaller()}, so the host is skipped here
     * rather than recorded twice. The inheriting lines lose no element, so each is an ordinary
     * in-place modification either way.
     *
     * @param confirmed the reconciliation to record
     * @effects mutates every recorded line, into the open modification bracket
     */
    public static void commit(Confirmed confirmed) {
        for (var reconciledLine : confirmed.linesToRecord()) {
            AccidentalMaterializer.commit(reconciledLine.line(), reconciledLine.changes());
        }

        // Every line of the reach is skipped here, host included: what is left is the lines the
        // edit never reached, which hold accepted restatements only because the scan runs to the
        // end of the song.
        AccidentalRestatements.commitOtherLines(confirmed.decision(), confirmed.lines());
    }
}
