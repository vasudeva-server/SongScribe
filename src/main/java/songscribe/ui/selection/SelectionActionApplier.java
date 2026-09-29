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
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementChange;
import songscribe.dom.Line;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;

/**
 * Applies one {@link UIAction.Reflectable} to every element of a selection, in one undo step.
 * <p>
 * The engine every reflectable toolbar action runs through, as opposed to the named musical
 * operations in {@code MusicEditOperations} — beaming, tying, hairpins — each of which is one
 * command with its own rules. What varies here is only the action; the three passes below are
 * the same whichever one it is.
 * <p>
 * Static, and takes the selection as an argument, because nothing in the pipeline reads or
 * writes what is currently selected: {@link #apply} asks the coordinator for the selection
 * once, and for cache invalidation once the mutation has landed. Everything between those two
 * points is a function of the line and the action alone.
 */
public final class SelectionActionApplier {

    private SelectionActionApplier() {}

    /**
     * One element the action will change, decided before anything is mutated.
     *
     * @param index   The element's index on the line, unchanged by the change
     * @param standIn The element as it will read afterwards: the new element of a replacement's
     *                pair for an {@link UIAction.ElementReplaceable}, a modified clone for an
     *                {@link UIAction.ElementModifiable}. Detached from the line, so the fit gate
     *                can measure it without anything having been mutated
     */
    private record PendingChange(int index, StaffElement standIn) {}

    /**
     * Applies the given action to the coordinator's current selection. A no-op when nothing is
     * selected. See
     * {@link #apply(SelectionCoordinator, ElementSelection, UIAction.Reflectable, boolean,
     * ScoreView)}, which does the work and explains how.
     *
     * @param coordinator the coordinator holding the selection to apply to
     * @param action      the reflectable action to apply
     * @param selected    true to apply the attribute, false to remove it
     * @param score       the view to parent a refusal alert on, and the source of the
     *                    song-wide lyric metrics the fit gate measures syllable widths with; null in
     *                    tests, which space the projection as if the line had no lyrics
     */
    public static <E extends StaffElement> void apply(
        SelectionCoordinator coordinator,
        UIAction.Reflectable<E> action,
        boolean selected,
        @Nullable ScoreView score) {

        var selection = coordinator.getSelection();

        if (selection == null) {
            return;
        }

        apply(coordinator, selection, action, selected, score);
    }

    /**
     * Applies the given action to all applicable elements of a caller-supplied selection. Wraps
     * the entire apply pass in a single modification bracket so all emitted mutations coalesce
     * into one {@code SongDidChangeNotification}, leaving the undo step's op-name to the
     * pending one the action set.
     * <p>
     * Runs in three passes, because the fit gate measures the line as the action will leave it,
     * which is known only once every change is decided:
     * <ol>
     *   <li><b>Decide</b> — for an {@link UIAction.ElementReplaceable}, one
     *       {@link ElementChange.Replacement} over every admitted element of the selection. For an
     *       {@link UIAction.ElementModifiable}, each admitted element's modified stand-in. Mutates
     *       nothing and opens no bracket.</li>
     *   <li><b>Gate</b> — over every element that changes, for an action that widens a column:
     *       the chain walk over the line as the action will leave it, then the fit check with the
     *       walk's accidentals in place. Still mutates nothing, so a refusal leaves the score
     *       untouched and creates no undo step.</li>
     *   <li><b>Apply</b> — inside the modification bracket: one {@link Line#replace} of the
     *       final replacement, or one {@link Line#modifyElement} for each stand-in.</li>
     * </ol>
     *
     * @param coordinator the coordinator to invalidate selection caches on once the mutation lands
     * @param selection   the selection to apply to, supplied by the caller rather than read from
     *                    the coordinator
     * @param action      the reflectable action to apply
     * @param selected    true to apply the attribute, false to remove it
     * @param score       the view to parent a refusal alert on, and the source of the
     *                    song-wide lyric metrics the fit gate measures syllable widths with; null in
     *                    tests, which space the projection as if the line had no lyrics
     */
    public static <E extends StaffElement> void apply(
        SelectionCoordinator coordinator,
        ElementSelection selection,
        UIAction.Reflectable<E> action,
        boolean selected,
        @Nullable ScoreView score) {

        var line = selection.line();

        if (action instanceof UIAction.ElementReplaceable<?>) {
            // A replaceable action acts only when its attribute is applied.
            if (!selected) {
                return;
            }

            // Safe: action's own type parameter is E throughout this call, and
            // ElementReplaceable<?> here is a capture of that same E.
            @SuppressWarnings("unchecked")
            var replaceable = (UIAction.ElementReplaceable<E>) action;

            var replacement = replacementFor(replaceable, selection);

            if (replacement != null) {
                applyReplacement(coordinator, selection, action, score, replacement);
            }
        } else if (action instanceof UIAction.ElementModifiable<?>) {
            // Safe: action's own type parameter is E throughout this call, and
            // ElementModifiable<?> here is a capture of that same E.
            @SuppressWarnings("unchecked")
            var modifiable = (UIAction.ElementModifiable<E>) action;

            var changes = decideModifications(modifiable, selected, selection);

            gateAndApply(coordinator, selection, action, selected, score, changes, () -> {
                for (var change : changes) {
                    var index = change.index();

                    line.modifyElement(
                        index,
                        modifiable.modifiedFields(),
                        () -> {
                            var admitted = modifiable.admit(line.getElement(index));

                            if (admitted != null) {
                                modifiable.applyToElement(line, index, admitted, selected);
                            }
                        }
                    );
                }
            });
        }
    }

    /**
     * Passes 2 and 3 for a decided replacement: the fit gate reads one stand-in for each of its
     * pairs, so it measures the line as the action will leave it, and the apply pass lands the
     * whole replacement as one change.
     */
    private static void applyReplacement(
        SelectionCoordinator coordinator,
        ElementSelection selection,
        UIAction.Reflectable<?> action,
        @Nullable ScoreView score,
        ElementChange.Replacement replacement) {

        var changes = replacement.newElements().entrySet().stream()
            .map(pair -> new PendingChange(pair.getKey(), pair.getValue()))
            .toList();

        gateAndApply(
            coordinator, selection, action, true, score, changes,
            () -> replacement.line().replace(replacement));
    }

    /**
     * Passes 2 and 3 — gates {@code changes} for an action that widens a column, then runs
     * {@code mutation} inside the modification bracket. A no-op when {@code changes} is empty.
     */
    private static void gateAndApply(
        SelectionCoordinator coordinator,
        ElementSelection selection,
        UIAction.Reflectable<?> action,
        boolean selected,
        @Nullable ScoreView score,
        List<PendingChange> changes,
        Runnable mutation) {

        if (changes.isEmpty()) {
            return;
        }

        var line = selection.line();

        if (action instanceof UIAction.WidensColumn<?> && !fitsWithChainsWalked(line, changes, score)) {
            // A removal can still overflow the line, so it gets its own operation-neutral wording
            // instead of the insert phrasing, which would misdescribe a deletion.
            if (selected) {
                OptionDialogs.showErrorMessage(
                    score,
                    Strings.ALERT_TITLE_INSERT_ERROR,
                    Strings.ERROR_LINE_FULL_ELEMENT,
                    changes.getFirst().standIn().getType().kind().label()
                );
            } else {
                OptionDialogs.showErrorMessage(
                    score,
                    Strings.ALERT_TITLE_LINE_TOO_FULL,
                    Strings.ERROR_LINE_FULL_REMOVAL
                );
            }

            return;
        }

        line.getSong().withModification(() -> {
            mutation.run();
            coordinator.invalidateSelectionCaches();
        });
    }

    /**
     * Pass 1 for an {@link UIAction.ElementReplaceable} — one replacement over every admitted
     * element of the selection. Mutates nothing.
     *
     * @return the replacement to apply, or {@code null} when no element is admitted
     */
    private static <E extends StaffElement> ElementChange.@Nullable Replacement replacementFor(
        UIAction.ElementReplaceable<E> replaceable, ElementSelection selection) {

        var line = selection.line();
        var bounds = selection.range();
        var newElements = new TreeMap<Integer, StaffElement>();

        for (var i = bounds.begin(); i <= bounds.end(); i++) {
            var admitted = replaceable.admit(line.getElement(i));

            if (admitted != null) {
                newElements.put(i, replaceable.createReplacement(admitted, true));
            }
        }

        if (newElements.isEmpty()) {
            return null;
        }

        return new ElementChange.Replacement(line, newElements);
    }

    /**
     * Pass 1 for an {@link UIAction.ElementModifiable} — each admitted element's stand-in: a
     * detached clone with the modification applied. Mutates nothing.
     */
    private static <E extends StaffElement> List<PendingChange> decideModifications(
        UIAction.ElementModifiable<E> modifiable, boolean selected, ElementSelection selection) {

        var line = selection.line();
        var bounds = selection.range();
        var changes = new ArrayList<PendingChange>();

        for (var i = bounds.begin(); i <= bounds.end(); i++) {
            var admitted = modifiable.admit(line.getElement(i));

            if (admitted == null) {
                continue;
            }

            // Safe on a detached clone: applyToElement takes the element as a parameter and
            // touches nothing else. admit() narrows it back to E: a clone shares its
            // source's class, so it is admitted by the same rule the source was.
            var standIn = admitted.clone();
            var admittedStandIn = modifiable.admit(standIn);

            if (admittedStandIn == null) {
                continue;
            }

            modifiable.applyToElement(line, i, admittedStandIn, selected);
            changes.add(new PendingChange(i, standIn));
        }

        return changes;
    }

    /**
     * Pass 2 — whether the line still fits once the changes are in place, with every accidental
     * the chain walk stamps for them drawn. The walk runs over a projection of the line holding
     * each stand-in at its index, so a stand-in that changes what a note sounds is decided along
     * with every later note in its chain. The projected run spans the changes, from the first to
     * the last. Mutates no live note: a refusal leaves every live element as it was, and an acceptance leaves each
     * stand-in stamped as the walk decided.
     *
     * @param changes the changes to measure, non-empty and in index order
     */
    private static boolean fitsWithChainsWalked(
        Line line, List<PendingChange> changes, @Nullable ScoreView score) {

        var changed = new ElementRange(changes.getFirst().index(), changes.getLast().index());
        var begin = changed.begin();
        var inserted = new ArrayList<StaffElement>(line.getElements(changed));

        for (var change : changes) {
            inserted.set(change.index() - begin, change.standIn());
        }

        var restamps = ProjectedEdit.replacing(line, changed, inserted).walk();

        var standIns = changes.stream().map(PendingChange::standIn).toList();
        var lyricRenderMetrics = (score != null) ? score.findLyricRenderMetrics() : null;

        return AccidentalFitGate.fits(
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
