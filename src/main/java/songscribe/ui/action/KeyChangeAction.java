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
package songscribe.ui.action;

import java.awt.event.ActionEvent;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.ui.component.InsertionPointOverlay;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.KeyChangeDialogController;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;

/**
 * Writes a key change into the middle of a line: the user picks the spot, then names the key.
 *
 * <p>Invoking the action does not open a dialog — it enters {@link InsertionPointMode}, the same
 * "click to place" interaction a paste with no selection uses, with this action as the client.
 * The mode raises the banner {@link #overlayText} names and tracks the marker; the index the
 * user settles on is one {@link #acceptsInsertionIndex} accepted. There is no position guard on
 * the action itself: every position rule this operation has lives in that predicate, where the
 * user sees it as a marker that appears and disappears rather than as a command that will not
 * run.
 *
 * <p>The two halves are strictly sequential, and the dialog is the second one: picking the point
 * only accepts it ({@link #insertionPointChosen}), and the dialog opens from
 * {@link #insertionPointModeDidEnd}, which hands the point back once the mode has taken its
 * banner and marker down. Opening it from the callback that chose the point would put a modal
 * dialog in front of a banner still reading "Click or Return to insert, Esc to cancel", telling
 * the user to do something the dialog no longer lets them do.
 *
 * <p>See {@code docs/key-signatures.md} for what a mid-line key change means, and
 * {@link KeyChangeElement}'s position invariant for why some positions are refused.
 */
public final class KeyChangeAction extends UIAction implements InsertionPointMode.Client {

    public static KeyChangeAction createAction(MainFrame mainFrame) {
        return new KeyChangeAction(mainFrame);
    }

    private KeyChangeAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_KEY_CHANGE),
            null,
            0,
            "key-signature-change",
            Strings.get(Strings.ACTION_KEY_CHANGE_TOOLTIP),
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE,
            Flag.OPENS_DIALOG
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        EditModeManager.getInsertionPointMode().enter(this);
    }

    // -------------------------------------------------------------------------
    // InsertionPointMode.Client
    // -------------------------------------------------------------------------

    /**
     * @return the key-change banner's wording
     */
    @Override
    public InsertionPointOverlay.Text overlayText() {
        return new InsertionPointOverlay.Text(
            Strings.get(Strings.INSERTION_MODE_KEY_CHANGE_TITLE),
            Strings.get(Strings.INSERTION_MODE_KEY_CHANGE_HINT));
    }

    /**
     * Whether a key change may be written at {@code index} on {@code line}.
     *
     * <p>Three positions are refused; every other index the mode's own geometry offers is taken.
     *
     * <ul>
     *   <li><b>Index 0.</b> A {@link KeyChangeElement} is never the first element of a line —
     *       its position invariant — and a key change there would only restate what the line's
     *       header already says.</li>
     *   <li><b>Every index past the line's last note</b>, {@link Line#lastNoteIndex()}. A key
     *       change there governs no note on this line, so it says nothing the next line's own key
     *       does not say, and the cautionary already draws that key at this line's end. The test
     *       is the last note rather than the last element, because what a key change is worth is
     *       the pitches it moves: the index of the last note is taken, and the positions before a
     *       trailing barline, a trailing rest, or the end of the line are refused alike. A line
     *       holding no note at all offers no position.</li>
     *   <li><b>The two indices flanking a key change already on the line</b>: immediately before
     *       the barline it sits behind, and immediately after it. The barline and the key
     *       signature are one unit — {@link Line#effectiveBegin} deletes them together — so the
     *       position in front of the pair goes as well as the one behind it, or a second
     *       barline-plus-key could be dropped directly in front of the first. The third index of
     *       that neighborhood, the gap inside the pair, is {@link Line#canInsertElementAt}'s and
     *       is refused before this predicate ever sees it: nothing goes there, whoever is
     *       placing.</li>
     * </ul>
     *
     * <p>Out-of-range indices answer {@code false} rather than throwing: the predicate is asked on
     * every mouse move, and "nowhere to put it" is what an unavailable position means.
     *
     * @param line the line the pointer is over
     * @param index the insertion index under the pointer, the element it would land before
     * @return {@code true} when a key change may be written at {@code index}
     */
    @Override
    public boolean acceptsInsertionIndex(Line line, int index) {
        return index > 0
            && index <= line.lastNoteIndex()
            && !line.isKeyChangeAt(index - 1)
            && !line.isKeyChangeAt(index + 1);
    }

    /**
     * Accepts the position the user picked and ends the placement. The dialog opens from
     * {@link #insertionPointModeDidEnd}, not here — see the class contract for why. Nothing is
     * recorded: the mode hands the point back with the outcome.
     *
     * <p>Always completes the placement: every index that reaches this method is one the
     * predicate accepted, and nothing else about a key change can refuse a position.
     *
     * @param line the line the user picked
     * @param index the accepted insertion index within {@code line}
     * @return {@link InsertionPointMode.Placement#COMPLETED}, always
     */
    @Override
    public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
        return InsertionPointMode.Placement.COMPLETED;
    }

    /**
     * Opens the key dialog on the point the placement completed on, now that the mode's banner
     * and marker are down. A cancelled placement opens nothing.
     *
     * <p>Cancelling the dialog is a decision about the key change, not about the position, so
     * nothing reopens: a second key change starts from the action again.
     *
     * @param outcome the point the placement completed on, or
     *     {@link InsertionPointMode.Outcome#CANCELLED}
     * @effects opens the key dialog on a completed placement
     */
    @Override
    public void insertionPointModeDidEnd(InsertionPointMode.Outcome outcome) {
        if (outcome instanceof InsertionPointMode.Outcome.Placed placed) {
            KeyChangeDialogController.addKeyChange(getMainFrame(), placed.line(), placed.index());
        }
    }
}
