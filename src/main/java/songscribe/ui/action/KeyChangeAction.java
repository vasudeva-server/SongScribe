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

import module java.desktop;


import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.KeyChangeDialog;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;

/**
 * Writes a key change into the middle of a line: the user picks the spot, then names the key.
 *
 * <p>Invoking the action does not open a dialog — it enters {@link InsertionPointMode}, the same
 * "click to place" interaction a paste with no selection uses, with this action as the client.
 * The index the user settles on is one {@link #acceptsInsertionIndex} accepted, and the dialog
 * opens on it. There is no position guard on the action itself: every position rule this
 * operation has lives in that predicate, where the user sees it as a marker that appears and
 * disappears rather than as a command that will not run.
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
     * Whether a key change may be written at {@code index} on {@code line}.
     *
     * <p>Three positions are refused; every other index the mode's own geometry offers is taken.
     *
     * <ul>
     *   <li><b>Index 0.</b> A {@link KeyChangeElement} is never the first element of a line —
     *       its position invariant — and a key change there would only restate what the line's
     *       header already says.</li>
     *   <li><b>The end of the line</b> — the index of its last element,
     *       {@code effectiveElementCount() - 1}, and the position past it,
     *       {@code effectiveElementCount()}. A key change at the first governs the single element
     *       that follows it and one at the second governs nothing at all; either way the next
     *       line's own key states the change better, and it is drawn where a reader expects it.
     *       Both are refused, because a rule that turned away the position governing one element
     *       while taking the one governing none would be answering a question it had not asked.</li>
     *   <li><b>Every index touching a key change already on the line</b>: immediately before the
     *       barline it sits behind, between that barline and it, and immediately after it. The
     *       barline and the key signature are one unit — {@link Line#effectiveBegin} deletes them
     *       together — so all three go, not only the two that touch the key signature. Refusing
     *       only those two would still let a second barline-plus-key be dropped directly in front
     *       of the first.</li>
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
        var effectiveCount = line.effectiveElementCount();

        if (index <= 0 || index >= effectiveCount - 1) {
            return false;
        }

        for (var pairIndex = index - 1; pairIndex <= index + 1; pairIndex++) {
            if (holdsKeySignature(line, pairIndex)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether the element at {@code index} is a key change. The barline in front of it is not
     * tested: an index adjacent to a key signature is refused however that key signature got
     * there, and testing for the barline would make the refusal depend on an invariant this
     * predicate exists to keep rather than to rely on.
     */
    private static boolean holdsKeySignature(Line line, int index) {
        return index >= 0
            && index < line.effectiveElementCount()
            && line.getElement(index).getType() == ElementType.KEY_CHANGE;
    }

    /**
     * Opens the key dialog on the position the user picked.
     *
     * <p>Always completes the placement. Cancelling the dialog is a decision about the key
     * change, not about the position — the same reading paste gives a declined
     * ending-invalidation confirm — so the placement ends either way and a second key change
     * starts from the action again.
     *
     * @param line the line the user picked
     * @param index the accepted insertion index within {@code line}
     * @return {@link InsertionPointMode.Placement#COMPLETED}, always
     */
    @Override
    public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
        new KeyChangeDialog(getMainFrame()).showFor(line, index);
        return InsertionPointMode.Placement.COMPLETED;
    }

    /**
     * Nothing to take down. This client raises no chrome of its own — the insertion marker is the
     * mode's and the dialog is modal — so both ways out of the mode leave nothing behind.
     */
    @Override
    public void insertionPointModeDidEnd(InsertionPointMode.EndReason reason) {
    }
}
