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
import songscribe.dom.StaffElementRun;
import songscribe.ui.component.InsertionPointOverlay;
import songscribe.ui.component.ScoreView;
import songscribe.undo.UndoController;

/**
 * Paste's half of the "click to place" interaction: the clipboard fragment, what counts as a
 * valid paste index, and the insertion itself. Picking the index is
 * {@link InsertionPointMode}'s, and this class is one of its clients.
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
     * The clipboard is non-empty. No-op when some other client's placement is already pending.
     */
    public void enter() {
        insertionPointMode.enter(this);
    }

    // -------------------------------------------------------------------------
    // InsertionPointMode.Client
    // -------------------------------------------------------------------------

    @Override
    public InsertionPointOverlay.Text overlayText() {
        return new InsertionPointOverlay.Text(
            Strings.get(Strings.INSERTION_MODE_PASTE_TITLE),
            Strings.get(Strings.INSERTION_MODE_PASTE_HINT));
    }

    /**
     * Paste adds nothing to the rules the mode applies for every client. Whether the fragment
     * fits, the breath-mark rule and the song-start rule are decided by
     * {@code tryInsertFragment} once a point is chosen, and their refusals are retryable.
     */
    @Override
    public boolean acceptsInsertionIndex(Line line, int index) {
        return true;
    }

    /**
     * {@code LINE_FULL}, {@code DECLINED_BY_ALERT} and {@code EMPTY} decline the point: nothing
     * was mutated, so the mode stays live. {@code INSERTED} completes the placement and retains
     * the clipboard. {@code CANCELLED} (the user chose Cancel on a confirm) completes it too:
     * declining is a decision about the paste, not about this insertion point.
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
            () -> line.getSong().withModificationResult(
                () -> controller.tryInsertFragment(line, new StaffElementRun.InsertionTarget.AtIndex(index))));

        return switch (outcome) {
            case LINE_FULL, EMPTY, DECLINED_BY_ALERT -> InsertionPointMode.Placement.DECLINED;
            case INSERTED, CANCELLED -> InsertionPointMode.Placement.COMPLETED;
        };
    }

    @Override
    public void insertionPointModeDidEnd(InsertionPointMode.Outcome outcome) {
    }
}
