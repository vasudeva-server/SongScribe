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

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.ui.component.InsertionPointOverlay;
import songscribe.ui.component.ScoreView;
import songscribe.undo.UndoController;

/**
 * Paste's half of the "click to place" interaction: the clipboard fragment, what counts as a
 * valid paste index, and the insertion itself. Picking the index is
 * {@link InsertionPointMode}'s, and this class is one of its clients.
 *
 * <p>Cmd+V with no selection calls {@link #enter()}, which puts the score into the
 * insertion-point mode; the mode raises the banner {@link #overlayText} names. A click, or
 * Return over a tracked point, calls {@code tryInsertFragment}: {@code LINE_FULL} shows an
 * error dialog and declines the point, leaving the mode live for another try, while
 * {@code INSERTED} and {@code CANCELLED} complete it. Escape, a click outside any line, or the
 * app being backgrounded cancel it instead.
 *
 * <p>Every index the mode's geometry yields is a valid paste index, so
 * {@link #acceptsInsertionIndex} accepts them all — see its contract for why that is a real
 * rule and not a missing one.
 *
 * <p>See <i>Placing a paste by clicking</i> in {@code docs/clipboard.md} for the division of
 * labour between the mode and its clients.
 */
public final class PasteModeManager implements InsertionPointMode.Client {

    // Dependencies
    private final ScoreView scoreView;
    private final InsertionPointMode insertionPointMode;

    public PasteModeManager(ScoreView scoreView, InsertionPointMode insertionPointMode) {
        this.scoreView = scoreView;
        this.insertionPointMode = insertionPointMode;
    }

    /**
     * Starts a paste placement by entering {@link InsertionPointMode}. Called from
     * {@code handlePaste}'s no-selection branch, so the clipboard is already known non-empty
     * and the score already has focus. No-op when some other client's placement is already
     * pending.
     */
    public void enter() {
        insertionPointMode.enter(this);
    }

    // -------------------------------------------------------------------------
    // InsertionPointMode.Client
    // -------------------------------------------------------------------------

    /**
     * @return the paste-mode banner's wording
     */
    @Override
    public InsertionPointOverlay.Text overlayText() {
        return new InsertionPointOverlay.Text(
            Strings.get(Strings.INSERTION_MODE_PASTE_TITLE),
            Strings.get(Strings.INSERTION_MODE_PASTE_HINT));
    }

    /**
     * Accepts every index the mode offers. A fragment may be pasted before any element of a
     * line, including before the first and after the last, so paste adds nothing to the rules
     * the mode already applies for every client; everything they leave over is a paste target.
     *
     * <p>Whether the fragment actually fits is not decided here: it depends on the fragment's
     * width, which is measured by {@code tryInsertFragment} once a point has been chosen, and
     * a "line full" answer is retryable rather than a reason to refuse to track the point.
     *
     * @param line the line the pointer is over
     * @param index the insertion index under the pointer
     * @return {@code true}, always
     */
    @Override
    public boolean acceptsInsertionIndex(Line line, int index) {
        return true;
    }

    /**
     * Inserts the clipboard fragment at the chosen point in one modification bracket.
     *
     * <p>{@code LINE_FULL} declines the point: the error is already shown by
     * {@code tryInsertFragment} and nothing was mutated, so the mode stays live and the user
     * can pick a roomier spot. {@code EMPTY} declines it for the same reason — nothing was
     * mutated — leaving the placement pending rather than silently consuming the gesture.
     * {@code INSERTED} completes the placement, with the clipboard retained so another Cmd+V
     * starts a fresh paste. {@code CANCELLED} — the user declined the ending-invalidation
     * confirm — completes it too: declining is a decision about the paste, not about this
     * insertion point, unlike the retryable "line full" case.
     *
     * @param line the line the user picked
     * @param index the insertion index within {@code line}
     * @return {@link InsertionPointMode.Placement#DECLINED} when nothing was inserted and the
     *     user may try again, {@link InsertionPointMode.Placement#COMPLETED} otherwise
     */
    @Override
    public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
        var controller = scoreView.getController();

        if (controller == null) {
            return InsertionPointMode.Placement.DECLINED;
        }

        // Placement bypasses UIAction.actionPerformed (it is driven by a mouse click
        // or Return keypress, not a Cmd+V dispatch), so the Tier-A op-name capture
        // that PasteAction relies on must be set here around the bracket instead.
        var outcome = UndoController.withPendingOpNameResult(
            Strings.get(Strings.ACTION_EDIT_OP_PASTE),
            () -> line.withModificationResult(() -> controller.tryInsertFragment(line, index, null)));

        return switch (outcome) {
            case LINE_FULL, EMPTY -> InsertionPointMode.Placement.DECLINED;
            case INSERTED, CANCELLED -> InsertionPointMode.Placement.COMPLETED;
        };
    }

    /**
     * Nothing to take down, and nothing left to do with the point. Paste raises no chrome of its
     * own — the banner and the insertion marker are both the mode's — and it inserts the fragment
     * in {@link #insertionPointChosen}, where an error the user can retry from still has the
     * banner behind it.
     *
     * @param outcome ignored; paste has already acted on the point by the time this arrives
     */
    @Override
    public void insertionPointModeDidEnd(InsertionPointMode.Outcome outcome) {
    }
}
