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
import songscribe.dom.ElementChange;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.EndingConfirms;
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
     * @param index        The element's index on the line, unchanged by the modification
     * @param standIn      The element as it will read afterwards: the replacement for an
     *                     {@link UIAction.ElementReplaceable}, a modified clone for an
     *                     {@link UIAction.ElementModifiable}. Detached from the line, so the fit
     *                     gate can measure it without anything having been mutated
     * @param compensation The ending change the user confirmed for this index, applied in the
     *                     apply pass because it mutates the line, or
     *                     {@link Ending.EndingEffect.None#INSTANCE} when there is none
     */
    private record PendingChange(int index, StaffElement standIn, Ending.EndingEffect compensation) {}

    /**
     * Applies the given action to the coordinator's current selection. A no-op when nothing is
     * selected. See
     * {@link #apply(SelectionCoordinator, ElementSelection, UIAction.Reflectable, boolean,
     * ScoreView)}, which does the work and explains how.
     *
     * @param coordinator the coordinator holding the selection to apply to
     * @param action      the reflectable action to apply
     * @param selected    true to apply the attribute, false to remove it
     * @param score       the view to parent any ending-confirm dialogs on, and the source of the
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
     * Runs in three passes, because the ending confirms decide which elements actually change and
     * the fit gate cannot be built until that is known — gating the loop as it runs would
     * over-refuse, killing the whole action before its dialogs are even shown:
     * <ol>
     *   <li><b>Decide</b> — compute each applicable element's post-change stand-in and, for a
     *       replacement, resolve and confirm its ending effect. Mutates nothing and opens no
     *       bracket, which also keeps a dialog from being open while a modification bracket is.</li>
     *   <li><b>Gate</b> — over the indices that proceed, for an action that widens a column: the
     *       chain walk over the line as the action will leave it, then the fit check with the
     *       walk's accidentals in place. Still mutates nothing, so a refusal leaves the score
     *       untouched and creates no undo step.</li>
     *   <li><b>Apply</b> — inside the modification bracket.</li>
     * </ol>
     *
     * @param coordinator the coordinator to invalidate selection caches on once the mutation lands
     * @param selection   the selection to apply to, supplied by the caller rather than read from
     *                    the coordinator
     * @param action      the reflectable action to apply
     * @param selected    true to apply the attribute, false to remove it
     * @param score       the view to parent any ending-confirm dialogs on, and the source of the
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
        var changes = decideChanges(action, selected, score, selection);

        if (changes.isEmpty()) {
            return;
        }

        if (action instanceof UIAction.WidensColumn<?>
            && !fitsWithChainsWalked(selection, changes, score)) {
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
            for (var change : changes) {
                var index = change.index();
                var compensation = change.compensation();

                // The compensating ending changes mutate the line, so they belong here rather
                // than beside the confirm that authorized them.
                if (compensation instanceof Ending.EndingEffect.CompensateEnd compensateEnd) {
                    EndingConfirms.applyCompensatingEndChange(line, compensateEnd);
                } else if (compensation instanceof Ending.EndingEffect.CompensateSplit compensateSplit) {
                    EndingConfirms.applyCompensatingSplitChange(line, compensateSplit);
                }

                if (action instanceof UIAction.ElementReplaceable<?>) {
                    // An Invalidate effect needs no compensation here: setElement removes the
                    // ending itself through Ending.outcomeFor.
                    line.setElement(index, change.standIn());
                } else if (action instanceof UIAction.ElementModifiable<?>) {
                    // Safe: action's own type parameter is E throughout this call, and
                    // ElementModifiable<?> here is a capture of that same E.
                    @SuppressWarnings("unchecked")
                    var modifiable = (UIAction.ElementModifiable<E>) action;

                    line.modifyElement(
                        index,
                        modifiable.modifiedFields(),
                        () -> {
                            var admitted = action.admit(line.getElement(index));

                            if (admitted != null) {
                                modifiable.applyToElement(line, index, admitted, selected);
                            }
                        }
                    );
                }
            }

            coordinator.invalidateSelectionCaches();
        });
    }

    /**
     * Pass 1 — decides what the action does to each element of the selection, mutating nothing.
     * An index the user declines an ending confirm for is simply left out of the result, so the
     * gate and the apply pass see only the elements that really change.
     */
    private static <E extends StaffElement> List<PendingChange> decideChanges(
        UIAction.Reflectable<E> action, boolean selected, @Nullable ScoreView score, ElementSelection selection) {

        var line = selection.line();
        var bounds = selection.range();
        var changes = new ArrayList<PendingChange>();

        for (var i = bounds.begin(); i <= bounds.end(); i++) {
            var admitted = action.admit(line.getElement(i));

            if (admitted == null) {
                continue;
            }

            if (action instanceof UIAction.ElementReplaceable<?>) {
                if (!selected) {
                    continue;
                }

                // Safe: action's own type parameter is E throughout this call, and
                // ElementReplaceable<?> here is a capture of that same E.
                @SuppressWarnings("unchecked")
                var replaceable = (UIAction.ElementReplaceable<E>) action;

                var replacement = replaceable.createReplacement(admitted, true);
                var effect = new ElementChange.Replacement(line, i, replacement).endingEffect();
                Ending.EndingEffect compensation = Ending.EndingEffect.None.INSTANCE;

                switch (effect) {
                    case Ending.EndingEffect.Invalidate _ -> {
                        if (!EndingConfirms.confirmInvalidation(score)) {
                            continue;
                        }
                        // proceed: line.setElement removes the ending through Ending.outcomeFor
                    }
                    case Ending.EndingEffect.CompensateEnd compensateEnd -> {
                        if (!EndingConfirms.confirmCompensateEnd(score, compensateEnd)) {
                            continue;
                        }
                        compensation = compensateEnd;
                    }
                    case Ending.EndingEffect.CompensateSplit compensateSplit -> {
                        if (!EndingConfirms.confirmCompensateSplit(score, compensateSplit, replacement.getType())) {
                            continue;
                        }
                        compensation = compensateSplit;
                    }
                    case Ending.EndingEffect.None _ -> {}
                }

                changes.add(new PendingChange(i, replacement, compensation));
            } else if (action instanceof UIAction.ElementModifiable<?>) {
                // Safe: action's own type parameter is E throughout this call, and
                // ElementModifiable<?> here is a capture of that same E.
                @SuppressWarnings("unchecked")
                var modifiable = (UIAction.ElementModifiable<E>) action;

                // Safe on a detached clone: applyToElement takes the element as a parameter and
                // touches nothing else. admit() narrows it back to E: a clone shares its
                // source's class, so it is admitted by the same rule the source was.
                var standIn = admitted.clone();
                var admittedStandIn = action.admit(standIn);

                if (admittedStandIn == null) {
                    continue;
                }

                modifiable.applyToElement(line, i, admittedStandIn, selected);
                changes.add(new PendingChange(i, standIn, Ending.EndingEffect.None.INSTANCE));
            }
        }

        return changes;
    }

    /**
     * Pass 2 — whether the line still fits once the changes are in place, with every accidental
     * the chain walk stamps for them drawn. The walk runs over a projection of the line holding
     * each stand-in at its index, so a stand-in that changes what a note sounds is decided along
     * with every later note in its chain. Mutates no live note: a refusal leaves every live
     * element as it was, and an acceptance leaves each stand-in stamped as the walk decided.
     */
    private static boolean fitsWithChainsWalked(
        ElementSelection selection, List<PendingChange> changes, @Nullable ScoreView score) {

        var line = selection.line();
        var selected = selection.range();
        var begin = selected.begin();
        var inserted = new ArrayList<StaffElement>(line.getElements(selected));

        for (var change : changes) {
            inserted.set(change.index() - begin, change.standIn());
        }

        var restamps = ProjectedEdit.replacing(line, selected, inserted).walk();

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
