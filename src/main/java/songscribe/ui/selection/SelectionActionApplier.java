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
import songscribe.dom.Beam;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.ui.EndingConfirms;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.AccidentalRestatements;

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
     * Applies the given action to all applicable elements in the coordinator's selection.
     * Wraps the entire apply pass in a single modification bracket so all emitted
     * mutations coalesce into one {@code SongDidChangeNotification}.
     * <p>
     * Runs in three passes, because the ending confirms decide which elements actually change and
     * the fit gate cannot be built until that is known — gating the loop as it runs would
     * over-refuse, killing the whole action before its dialogs are even shown:
     * <ol>
     *   <li><b>Decide</b> — compute each applicable element's post-change stand-in and, for a
     *       replacement, resolve and confirm its ending effect. Mutates nothing and opens no
     *       bracket, which also keeps a dialog from being open while a modification bracket is.</li>
     *   <li><b>Reconcile and gate</b> — over the indices that proceed; still mutates nothing, so a
     *       refusal leaves the score untouched and creates no undo step.</li>
     *   <li><b>Apply</b> — inside the modification bracket.</li>
     * </ol>
     *
     * @param coordinator the coordinator holding the selection to apply to
     * @param action      the reflectable action to apply
     * @param selected    true to apply the attribute, false to remove it
     * @param score       the view to parent any ending-confirm dialogs on, and the source of the
     *                    song-wide lyric metrics the fit gate measures syllable widths with; null in
     *                    tests, which space the projection as if the line had no lyrics
     */
    public static void apply(
        SelectionCoordinator coordinator,
        UIAction.Reflectable action,
        boolean selected,
        @Nullable ScoreView score) {

        var selection = coordinator.getSelection();

        if (selection == null) {
            return;
        }

        var line = selection.line();
        var song = line.getSong();
        var changes = decideChanges(action, selected, score, selection);

        if (changes.isEmpty()) {
            return;
        }

        // Still the decide phase: the prompt must run before any modification bracket opens, for
        // the same reason the ending confirms above do.
        var decision = AccidentalRestatements.confirm(score, line, editedNotes(line, changes));

        if (decision.isCancelled()) {
            return;
        }

        // The accidentals this modification must make explicit so no pitch the user did not touch
        // changes — removing an explicit accidental is exactly as context-changing as adding one —
        // plus the ones it makes redundant and so takes away again, plus any restatement the user
        // accepted above.
        var accidentalChanges = AccidentalReconciliation.reconcileModification(
            line, intendedChanges(changes), decision.removal());

        if (action instanceof UIAction.WidensColumn
            && !fitsAfterModification(line, changes, accidentalChanges, score)) {
            OptionDialogs.showErrorMessage(
                score,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                changes.getFirst().standIn().getType().categoryName()
            );
            return;
        }

        // Only a replacement can invalidate a beam or a tuplet; an in-place modification leaves
        // element types alone.
        var needsSpanCleanup = action instanceof UIAction.ElementReplaceable;

        song.withModification(() -> {
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

                if (action instanceof UIAction.ElementReplaceable) {
                    // An Invalidate effect needs no compensation here: setElement removes the
                    // ending itself via isInvalidatedByReplacement.
                    line.setElement(index, change.standIn());
                } else if (action instanceof UIAction.ElementModifiable modifiable) {
                    line.modifyElement(
                        index,
                        modifiable.modifiedFields(),
                        () -> modifiable.applyToElement(line.getElement(index), selected)
                    );
                }
            }

            // Recorded in the same bracket so the toggle and its reconciliation are one undo step.
            AccidentalMaterializer.commit(line, accidentalChanges);

            // Accepted restatements on later lines join the same step.
            AccidentalRestatements.commitOtherLines(decision, line);

            if (needsSpanCleanup) {
                validateSpans(line, selection.begin(), selection.end());
            }

            coordinator.invalidateSelectionCaches();
        });
    }

    /**
     * Pass 1 — decides what the action does to each element of the selection, mutating nothing.
     * An index the user declines an ending confirm for is simply left out of the result, so the
     * gate and the apply pass see only the elements that really change.
     */
    private static List<PendingChange> decideChanges(
        UIAction.Reflectable action, boolean selected, @Nullable ScoreView score, ElementSelection selection) {

        var line = selection.line();
        var changes = new ArrayList<PendingChange>();

        for (var i = selection.begin(); i <= selection.end(); i++) {
            var element = line.getElement(i);

            if (!action.appliesTo(element)) {
                continue;
            }

            if (action instanceof UIAction.ElementReplaceable replaceable) {
                if (!selected) {
                    continue;
                }

                var replacement = replaceable.createReplacement(element, true);
                var effect = line.findEndingReplacementEffect(i, replacement);
                Ending.EndingEffect compensation = Ending.EndingEffect.None.INSTANCE;

                switch (effect) {
                    case Ending.EndingEffect.Invalidate _ -> {
                        if (!EndingConfirms.confirmInvalidation(score)) {
                            continue;
                        }
                        // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
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
            } else if (action instanceof UIAction.ElementModifiable modifiable) {
                // Safe on a detached clone: applyToElement takes the element as a parameter and
                // touches nothing else.
                var standIn = element.clone();
                modifiable.applyToElement(standIn, selected);
                changes.add(new PendingChange(i, standIn, Ending.EndingEffect.None.INSTANCE));
            }
        }

        return changes;
    }

    /**
     * The post-change state of every element this modification touches, as
     * {@link AccidentalReconciliation} describes it. These are the notes the user changed
     * deliberately, so they are never materialized themselves — only the notes that inherit
     * their context are.
     */
    private static List<AccidentalReconciliation.IntendedChange> intendedChanges(List<PendingChange> changes) {
        var intended = new ArrayList<AccidentalReconciliation.IntendedChange>(changes.size());

        for (var change : changes) {
            var standIn = change.standIn();
            intended.add(new AccidentalReconciliation.IntendedChange(
                change.index(), standIn.getAccidental(), standIn.getStaffPosition()));
        }

        return intended;
    }

    /**
     * This action's changes, described for the restatement prompt: each changed note's accidental
     * now, and the one its stand-in will carry instead — which is none for a toggle-off or for a
     * replacement that bears no accidental.
     *
     * <p>The staff position is the live note's, which is the position the accidental being removed
     * was written at. An in-place modification never moves it, but taking it from the stand-in
     * would state the wrong thing if one ever did.
     */
    private static List<AccidentalRestatements.EditedNote> editedNotes(
        Line line, List<PendingChange> changes) {

        var edited = new ArrayList<AccidentalRestatements.EditedNote>(changes.size());

        for (var change : changes) {
            var element = line.getElement(change.index());

            edited.add(new AccidentalRestatements.EditedNote(
                change.index(),
                element.getStaffPosition(),
                element.getAccidental(),
                change.standIn().getAccidental()));
        }

        return edited;
    }

    /**
     * Pass 2 — whether the line still fits once the changes and the accidentals they force are
     * in place. Mutates nothing: the projection is built from stand-ins and clones, so a refusal
     * leaves every live element as it was.
     */
    private static boolean fitsAfterModification(
        Line line,
        List<PendingChange> changes,
        List<AccidentalReconciliation.AccidentalChange> accidentalChanges,
        @Nullable ScoreView score) {

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

        // Accidental width is a layout input, so the gate has to measure the reconciled
        // accidentals — on clones, because the live notes must stay untouched until the gate
        // has accepted.
        for (var accidentalChange : accidentalChanges) {
            var index = line.getElementIndex(accidentalChange.note());

            if (index < effectiveCount) {
                var note = accidentalChange.note().clone();
                note.setAccidental(accidentalChange.accidental());
                projected.set(index, note);
            }
        }

        var lyricRenderMetrics = (score != null) ? score.findLyricRenderMetrics() : null;

        return InsertionSpacingCalculator.calculateModification(line, projected, lyricRenderMetrics)
            .fitsWithinLine(line.getSong().getLineWidthSs());
    }

    // Validates beam and tuplet spans after batch element replacement.
    //
    // Tie repair is omitted because Line.setElement already does it, on every
    // path: Tie.isInvalidatedByReplacement removes exactly the ties a replacement
    // makes illegal. Independently, no replacement reachable from this call site
    // can invalidate a tie in the first place — the replacement preserves pitch
    // and rest-ness, grace notes are disabled in select mode via
    // Flag.DISABLE_IN_SELECT_MODE, and ElementModifiable actions do not touch
    // element type — so nothing here relies on that second argument holding.
    private static void validateSpans(Line line, int begin, int end) {
        repairBeamings(line, begin, end);
        line.removeOverlappingTuplets(begin, end);
    }

    // Trim-and-kill repair for beams that overlap [begin, end] after a batch
    // element replacement. For each overlapping beam:
    //   1. Trim non-beamable elements from the left and right ends.
    //   2. If the trimmed span still contains a non-beamable element in the
    //      interior, kill the beam entirely (no replacement).
    //   3. If the trimmed span is identical to the original, no-op.
    //   4. If the trimmed span has fewer than two elements, kill without re-add.
    //   5. Otherwise, remove the original beam and add a new one for the
    //      trimmed sub-range.
    //
    // The bidirectional trim handles configurations where both ends become
    // non-beamable but the interior is still valid, leaving the logic resilient
    // to a future disjoint-selection capability.
    //
    // Behavior change vs. legacy repairSpanSet: a beam with a non-beamable
    // element in the middle is now killed entirely instead of being split into
    // sub-beams.
    private static void repairBeamings(Line line, int begin, int end) {
        var overlapping = line.findBeamsOverlapping(begin, end);

        for (var beam : overlapping) {
            var anchorIdx = beam.getAnchorElementIndex();
            var endIdx = beam.getEndElementIndex();
            var newStart = anchorIdx;

            while (newStart <= endIdx && !line.getElement(newStart).getType().isBeamable()) {
                newStart++;
            }

            var newEnd = endIdx;

            while (newEnd >= newStart && !line.getElement(newEnd).getType().isBeamable()) {
                newEnd--;
            }

            // No valid elements remain in the trimmed span — kill outright.
            if (newStart > newEnd) {
                line.removeBeaming(beam);
                continue;
            }

            // Check for any non-beamable element in the interior of the trimmed span.
            // Grace notes are exempt: they are not beam members, so a beam legitimately
            // passes over one (refs #592).
            var hasInteriorInvalid = false;

            for (var i = newStart; i <= newEnd; i++) {
                var type = line.getElement(i).getType();

                if (!type.isBeamable() && !type.isGraceNote()) {
                    hasInteriorInvalid = true;
                    break;
                }
            }

            if (hasInteriorInvalid) {
                line.removeBeaming(beam);
                continue;
            }

            // Trimmed span is identical to the original — no-op.
            if (newStart == anchorIdx && newEnd == endIdx) {
                continue;
            }

            // Fewer than two elements — kill without re-add.
            if (newEnd - newStart < 1) {
                line.removeBeaming(beam);
                continue;
            }

            // Trimmed span shrank but is all valid — replace with the truncated beam.
            line.removeBeaming(beam);
            line.addBeaming(new Beam(line.getElement(newStart), line.getElement(newEnd)));
        }
    }
}
