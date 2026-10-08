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

package songscribe.ui.selection;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.FitRefusals;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;

/**
 * Applies one {@link UIAction.SelectionApplicable} to every element of a selection, in one undo
 * step.
 * <p>
 * The engine every reflectable toolbar action runs through, as opposed to the named musical
 * operations in {@code MusicEditOperations} — beaming, tying, hairpins — each of which is one
 * command with its own rules. The action decides what it does to the selection and hands that
 * over as a {@link Plan}; what happens to a plan is the same whichever action made it.
 * <p>
 * Static, and takes the selection as an argument, because nothing in the pipeline reads what is
 * currently selected: the caller already holds the selection, and the coordinator is reached
 * only for cache invalidation once the mutation has landed. Everything between those two points
 * is a function of the line and the plan alone.
 */
public final class SelectionActionApplier {

    private SelectionActionApplier() {}

    /**
     * One element an action will change, decided before anything is mutated.
     *
     * @param index   The element's index on the line, unchanged by the change
     * @param standIn The element as it will read afterwards: the new element of a replacement's
     *                pair for an {@link UIAction.ElementReplaceable}, a modified clone for an
     *                {@link UIAction.ElementModifiable}. Detached from the line, so the fit gate
     *                can measure it without anything having been mutated
     */
    public record PendingChange(int index, StaffElement standIn) {}

    /** Which wording refuses a plan whose line no longer fits. */
    public enum Refusal {
        /** The plan puts something on its elements; the alert names the element type. */
        INSERTION,
        /**
         * The plan takes something off its elements. A removal can still overflow the line, and
         * the insert phrasing would misdescribe it, so its wording names no operation.
         */
        REMOVAL
    }

    /**
     * What an action will do to a selection, decided before anything is mutated.
     *
     * @param changes      every element the action changes, non-empty and in index order
     * @param mutation     lands the changes on the line; run inside the modification bracket
     * @param widensColumn whether a change can make its column wider, so the line must be
     *                     measured before the mutation runs
     * @param refusal      the wording that refuses the plan when the line no longer fits
     */
    public record Plan(
        List<PendingChange> changes,
        Runnable mutation,
        boolean widensColumn,
        Refusal refusal) {

        /**
         * @throws IllegalArgumentException if {@code changes} is empty: an action that changes
         *     nothing has no plan
         */
        public Plan {
            if (changes.isEmpty()) {
                throw new IllegalArgumentException("A plan changes at least one element");
            }

            changes = List.copyOf(changes);
        }
    }

    /**
     * Applies an action to every element of {@code selection} that it changes. Wraps the whole
     * apply pass in a single modification bracket so all emitted mutations coalesce into one
     * {@code SongDidChangeNotification}, leaving the undo step's op-name to the pending one the
     * action set.
     * <p>
     * Runs in three passes, because the fit gate measures the line as the action will leave it,
     * which is known only once every change is decided:
     * <ol>
     *   <li><b>Decide</b> — the action's {@link UIAction.SelectionApplicable#plan}. Mutates
     *       nothing and opens no bracket.</li>
     *   <li><b>Gate</b> — over every element that changes, for a plan that widens a column: the
     *       chain walk over the line as the action will leave it, then the fit check with the
     *       walk's accidentals in place. Still mutates nothing, so a refusal leaves the score
     *       untouched and creates no undo step.</li>
     *   <li><b>Apply</b> — the plan's mutation, inside the modification bracket.</li>
     * </ol>
     * A no-op when the action changes no element of the selection.
     *
     * @param score     the view to parent a refusal alert on, the source of the song-wide lyric
     *                  metrics the fit gate measures syllable widths with, and the holder of the
     *                  coordinator whose selection caches go stale once the mutation lands
     * @param selection the selection to apply to
     * @param action    the action to apply
     * @param toggle    whether to put the action's attribute on or take it off
     */
    public static void apply(
        ScoreView score,
        ElementSelection selection,
        UIAction.SelectionApplicable<?> action,
        UIAction.Toggle toggle) {

        var plan = action.plan(selection, toggle);

        if (plan == null) {
            return;
        }

        var line = selection.line();

        if (plan.widensColumn()) {
            var verdict = judgeWithChainsWalked(line, plan.changes(), score);

            if (!FitRefusals.accepts(verdict, score, () -> refuse(plan, score))) {
                return;
            }
        }

        line.getSong().withModification(() -> {
            plan.mutation().run();
            score.getSelectionCoordinator().invalidateSelectionCaches();
        });
    }

    /** Tells the user the line has no room for {@code plan}, in the plan's wording. */
    private static void refuse(Plan plan, ScoreView score) {
        switch (plan.refusal()) {
            case INSERTION -> OptionDialogs.showErrorMessage(
                score,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                plan.changes().getFirst().standIn().getType().kind().label()
            );
            case REMOVAL -> OptionDialogs.showErrorMessage(
                score,
                Strings.ALERT_TITLE_LINE_TOO_FULL,
                Strings.ERROR_LINE_FULL_REMOVAL
            );
        }
    }

    /**
     * Pass 2 — the fit gate's verdict once the changes are in place, with every accidental the
     * chain walk stamps for them drawn. The walk runs over a projection of the line holding
     * each stand-in at its index, so a stand-in that changes what a note sounds is decided along
     * with every later note in its chain. The projected run spans the changes, from the first to
     * the last. Mutates no live note: a refusal leaves every live element as it was, and an
     * acceptance leaves each stand-in stamped as the walk decided.
     *
     * @param changes the changes to measure, non-empty and in index order
     */
    private static AccidentalFitGate.Verdict judgeWithChainsWalked(
        Line line, List<PendingChange> changes, ScoreView score) {

        var changed = new ElementRange(changes.getFirst().index(), changes.getLast().index());
        var begin = changed.begin();
        var inserted = new ArrayList<StaffElement>(line.getElements(changed));

        for (var change : changes) {
            inserted.set(change.index() - begin, change.standIn());
        }

        var restamps = ProjectedEdit.replacing(line, changed, inserted).walk();

        var standIns = changes.stream().map(PendingChange::standIn).toList();
        var lyricRenderMetrics = score.findLyricRenderMetrics();

        return AccidentalFitGate.judge(
            line, restamps, standIns, lyricRenderMetrics,
            () -> fitsAfterModification(line, changes, lyricRenderMetrics));
    }

    /**
     * The fit check itself: whether the line still fits once the changes are in place. Mutates
     * nothing: the projection is built from stand-ins, so a refusal leaves every live element as
     * it was.
     */
    private static boolean fitsAfterModification(
        Line line, List<PendingChange> changes, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var effectiveCount = line.effectiveElementCount();

        // Nothing but the auto-maintained terminal barline, which layout pins flush-right: there
        // is no chain to solve and nothing the change can push past the margin.
        if (effectiveCount == 0) {
            return true;
        }

        var projected = new ArrayList<StaffElement>(effectiveCount);

        for (var i = 0; i < effectiveCount; i++) {
            projected.add(line.getElement(i));
        }

        for (var change : changes) {
            // The auto-maintained terminal barline is not in the projection: layout pins it
            // flush-right, and its column is built from the live element.
            if (change.index() < effectiveCount) {
                projected.set(change.index(), change.standIn());
            }
        }

        return InsertionSpacingCalculator.fitsInPlace(line, projected, lyricRenderMetrics);
    }

}
