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
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JOptionPane;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.ui.OptionDialogs;

/**
 * Asks the notator whether an edit that takes an explicit accidental away should also take away the
 * later notes that restate it, and turns the answer into the
 * {@link AccidentalReconciliation.RestatementRemoval} the reconciliation needs.
 *
 * <h2>Why this has to be asked</h2>
 * {@link AccidentalReconciliation}'s removal rule can never touch an accidental that was already
 * redundant when it was written, which is exactly what protects a deliberate restatement — and
 * restatements are the norm in this repertoire. A restatement of the accidental <em>being
 * removed</em> falls in the gap that leaves: on its own line it is not redundant at all, because
 * accidental context resets at the line boundary, so no arithmetic can tell whether it was meant
 * independently or only echoed the accidental now going away. The notator can, so they are asked.
 *
 * <h2>What the answer commits the caller to</h2>
 * <table border="1">
 *   <caption>The three answers</caption>
 *   <tr><td><b>Yes</b></td><td>Remove every restatement found, and suspend materialization at the
 *       removed accidentals' staff positions so the pitch change the user consented to actually
 *       propagates.</td></tr>
 *   <tr><td><b>No</b></td><td>Remove nothing extra. The ordinary removal rule still runs.</td></tr>
 *   <tr><td><b>Cancel</b></td><td>Abort the whole edit — nothing mutated, no undo step.</td></tr>
 * </table>
 *
 * <p>One prompt per edit, however many accidentals the edit removes, and never on an edit that only
 * <em>adds</em> an accidental: a later accidental made redundant by an addition is wanted here, not
 * surplus.
 *
 * <p>{@link #confirm} must run in an edit's decide phase, <b>before any modification bracket
 * opens</b> — the same rule the ending confirms follow, and for the same reason: a dialog must
 * never be open while a bracket is.
 *
 * <p>The dialog's suppressed default is No, so headless and test contexts behave exactly as they
 * did before this feature existed.
 */
public final class AccidentalRestatements {

    private AccidentalRestatements() {
        // Prevent instantiation - utility class with static methods only
    }

    /** What the notator answered. */
    public enum Answer {
        /** Abort the whole edit; nothing may be mutated. */
        CANCEL,

        /** Proceed with the edit, leaving every restatement alone. */
        NO,

        /** Proceed with the edit and remove the restatements. */
        YES
    }

    /**
     * One element an edit is about to change, described before anything is mutated: what explicit
     * accidental it carries now, and what it will carry <em>at that same staff position</em>
     * afterwards. Every caller describes its edit as a list of these and lets {@link #confirm} work
     * out which of them actually lose an accidental, so that rule lives in one place.
     *
     * @param index         The element's index on the line being edited
     * @param staffPosition The staff position the accidental is written at now. Stated separately
     *                      because a pitch shift moves the note before this is applied, and it is
     *                      the position the accidental <em>was</em> written at that matters
     * @param before        The explicit accidental the element carries now, or null for none
     * @param after         The explicit accidental it will carry at {@code staffPosition}
     *                      afterwards, or null when it will carry none there. A note that moves to
     *                      another staff position, or becomes something that cannot bear an
     *                      accidental, gives its accidental up and so passes null
     */
    public record EditedNote(
        int index,
        int staffPosition,
        StaffElement.@Nullable Accidental before,
        StaffElement.@Nullable Accidental after
    ) {}

    /**
     * One explicit accidental an edit really does take away — an {@link EditedNote} that
     * {@link #confirm} has decided against.
     */
    private record RemovedAccidental(int index, int staffPosition, StaffElement.Accidental accidental) {}

    /**
     * The notator's answer and everything the caller needs to honor it.
     *
     * @param answer  What they answered
     * @param removal The accepted restatements and the staff positions to suspend materialization
     *                at — {@link AccidentalReconciliation.RestatementRemoval#NONE} unless the
     *                answer was Yes
     * @param lines   Every line holding an accepted restatement, in song order. The caller
     *                reconciles each of these in addition to the line it is editing; empty unless
     *                the answer was Yes
     */
    public record Decision(
        Answer answer,
        AccidentalReconciliation.RestatementRemoval removal,
        List<Line> lines
    ) {

        /**
         * The edit removes nothing that needs consent, so it simply proceeds. Also what a caller
         * passes on a path this feature does not reach — the deletion half of a paste-replace,
         * which its own {@link #confirm} has already decided for as a whole.
         */
        public static final Decision PROCEED =
            new Decision(Answer.NO, AccidentalReconciliation.RestatementRemoval.NONE, List.of());

        /** Whether the caller must abandon the edit without mutating anything. */
        public boolean isCancelled() {
            return answer == Answer.CANCEL;
        }
    }

    /**
     * Works out which of {@code edited} actually lose an explicit accidental, scans forward for
     * restatements of each, and — when there is at least one — asks the notator what to do about
     * them.
     *
     * <p>Every element of {@code edited} is excluded from the scan whether or not it loses
     * anything: the edit is about to change all of them, so none may be offered back to the user or
     * allowed to stand in for a cancellation.
     *
     * <p>Mutates nothing, and shows nothing when the scan comes up empty — which is the common
     * case, so the ordinary edit is not made to pay for this feature with a dialog.
     *
     * @param parent The component to parent the dialog on, or null when there is no owning window
     * @param line   The line being edited. Every {@link EditedNote} indexes into it; an edit whose
     *               removals span two lines does not exist, because a removal is always something
     *               one selection or one click does
     * @param edited The elements this edit changes, described pre-mutation
     * @return The decision, which is always a plain proceed when nothing was found
     */
    public static Decision confirm(@Nullable Component parent, Line line, List<EditedNote> edited) {
        if (edited.isEmpty()) {
            return Decision.PROCEED;
        }

        // Identity semantics come for free: StaffElement and Line override neither equals nor
        // hashCode. Insertion-ordered so the offered notes stay in song order, since the removals
        // are scanned in song order and each scan runs forward.
        var excluded = new LinkedHashSet<StaffElement>();
        var removed = new ArrayList<RemovedAccidental>();

        for (var note : edited) {
            var element = line.getElement(note.index());
            excluded.add(element);

            var before = note.before();

            // A grace note sits outside the accidental-context system entirely — the reconciliation
            // walk skips every element that is not a pitched note — so its accidental never lent
            // anything to a later note and cannot have been restated.
            if ((before != null) && element.getType().isPitchedNote() && !keepsAccidental(note)) {
                removed.add(new RemovedAccidental(note.index(), note.staffPosition(), before));
            }
        }

        if (removed.isEmpty()) {
            return Decision.PROCEED;
        }

        var notes = new LinkedHashSet<StaffElement>();
        var lines = new LinkedHashSet<Line>();
        var suppressedStaffPositions = new LinkedHashSet<Integer>();
        var song = line.getSong();

        for (var removal : removed) {
            suppressedStaffPositions.add(removal.staffPosition());

            var restatements = AccidentalReconciliation.findRestatements(
                song, line, removal.index(), removal.staffPosition(), removal.accidental(), excluded);

            for (var restatement : restatements) {
                if (notes.add(restatement.note())) {
                    lines.add(restatement.line());
                }
            }
        }

        if (notes.isEmpty()) {
            return Decision.PROCEED;
        }

        var answer = OptionDialogs.showConfirmDialog(
            parent,
            Strings.CONFIRM_TITLE_ACCIDENTAL_RESTATEMENTS,
            Strings.CONFIRM_ACCIDENTAL_RESTATEMENTS,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (answer == JOptionPane.CANCEL_OPTION) {
            return new Decision(
                Answer.CANCEL, AccidentalReconciliation.RestatementRemoval.NONE, List.of());
        }

        if (answer != JOptionPane.YES_OPTION) {
            return Decision.PROCEED;
        }

        return new Decision(
            Answer.YES,
            new AccidentalReconciliation.RestatementRemoval(notes, suppressedStaffPositions),
            List.copyOf(lines));
    }

    /**
     * Reconciles and records the accepted restatements sitting on every line <em>other</em> than
     * the one being edited. The edited line needs no call of its own: its restatements travel
     * through the reconciliation the edit already runs, which is what the
     * {@link AccidentalReconciliation.RestatementRemoval} is passed to.
     *
     * <p>Must be called inside the edit's modification bracket, so the whole thing — the edit, the
     * removals across lines, and the reconciliation on each — is one undo step. A bracket opened on
     * a {@link Line} delegates to its {@link songscribe.dom.Song}, so mutations on a sibling line
     * coalesce into the same batch.
     *
     * <p>A line the edit does not otherwise touch cannot need a fit gate: this only ever clears
     * accidentals there, and suppression keeps it from materializing any, so the line can only get
     * narrower.
     *
     * @param decision   The notator's decision, from {@link #confirm}
     * @param editedLine The line the edit itself is reconciling, which is skipped here
     */
    public static void commitOtherLines(Decision decision, Line editedLine) {
        commit(decision, editedLine);
    }

    /**
     * As {@link #commitOtherLines}, for an edit that runs no reconciliation of its own: every line
     * holding an accepted restatement is reconciled here, the edited one included.
     *
     * <p>Only the click-replace path needs this. It has never reconciled — replacing a note is the
     * one mutation #676 left uncovered — so there is no reconciliation for its own line's
     * restatements to travel through.
     *
     * <p>Call it from the same place a reconciliation would go: inside the edit's modification
     * bracket, and before the line is mutated, so the scan reads the pre-edit line exactly as every
     * other call site does.
     */
    public static void commitAllLines(Decision decision) {
        commit(decision, null);
    }

    private static void commit(Decision decision, @Nullable Line skipped) {
        for (var line : decision.lines()) {
            if (line == skipped) {
                continue;
            }

            AccidentalMaterializer.commit(line, AccidentalReconciliation.reconcileModification(
                line, List.of(), decision.removal()));
        }
    }

    /**
     * Whether {@code note} still carries an explicit accidental once the edit is done. Called only
     * for a note that has one now.
     *
     * <p>Losing it outright counts as a removal even when it was a natural. A natural cancels an
     * earlier sharp or flat, so taking one away changes what the note sounds exactly as taking a
     * sharp away does, and a later note can restate it exactly the same way. An accidental
     * <em>replaced</em> by another is compared by sound rather than identity, but with only
     * {@code null} and {@code NATURAL} left to sound alike, that comparison is definitional now —
     * there is no longer a distinct pair of glyphs it needs to fold together.
     */
    private static boolean keepsAccidental(EditedNote note) {
        var after = note.after();

        if (after == null) {
            return false;
        }

        return StaffElement.getPitchAdjustment(after) == StaffElement.getPitchAdjustment(note.before());
    }

    /**
     * A deletion of {@code [begin, end]} on {@code line}, described for {@link #confirm}: every
     * element in the range is going away, so each carries whatever accidental it has now and none
     * afterwards. Shared by the range delete, the cut and the paste-replace, which differ in what
     * they do next but not in what they remove.
     */
    public static List<EditedNote> inDeletedRange(Line line, int begin, int end) {
        var edited = new ArrayList<EditedNote>();

        for (var i = begin; i <= end && i < line.effectiveElementCount(); i++) {
            var element = line.getElement(i);

            edited.add(new EditedNote(i, element.getStaffPosition(), element.getAccidental(), null));
        }

        return edited;
    }
}
