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
 */
public final class SelectionActionApplier {

    private SelectionActionApplier() {}

    /** @param standIn detached from the line, so the fit gate can measure it before any mutation */
    public record PendingChange(int index, StaffElement standIn) {}

    public enum Refusal {
        INSERTION,
        REMOVAL
    }

    /**
     * What an action will do to a selection, decided before anything is mutated.
     *
     * @param changes in index order
     */
    public record Plan(
        List<PendingChange> changes,
        Runnable mutation,
        boolean widensColumn,
        Refusal refusal) {

        public Plan {
            if (changes.isEmpty()) {
                throw new IllegalArgumentException("A plan changes at least one element");
            }

            changes = List.copyOf(changes);
        }
    }

    /**
     * The undo step's op-name is the pending one the action set. A refusal leaves the score
     * untouched and creates no undo step.
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
     * Mutates no live note: a refusal leaves every live element as it was.
     *
     * @param changes non-empty and in index order
     */
    private static AccidentalFitGate.Verdict judgeWithChainsWalked(
        Line line, List<PendingChange> changes, ScoreView score) {

        var changed = new ElementRange(changes.getFirst().index(), changes.getLast().index());
        var begin = changed.begin();
        var inserted = new ArrayList<StaffElement>(line.getElements(changed));

        for (var change : changes) {
            inserted.set(change.index() - begin, change.standIn());
        }

        var restamps = ProjectedEdit.replacing(line, changed, inserted, line.getRunningKey()).walk();

        var standIns = changes.stream().map(PendingChange::standIn).toList();
        var lyricRenderMetrics = score.findLyricRenderMetrics();

        return AccidentalFitGate.judge(
            line, restamps, standIns, lyricRenderMetrics,
            () -> fitsAfterModification(line, changes, lyricRenderMetrics));
    }

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
