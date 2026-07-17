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

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.Trill;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.LineSelectionState;

/**
 * Checkable action that toggles a {@link Trill} over the current selection.
 * <p>
 * The checked state reflects whether the selection overlaps any existing
 * {@code Trill} range. Checking adds one trill spanning the selection (clamped
 * to the outermost pitched notes); unchecking removes every trill overlapping
 * the selection. The apply step is line-scoped, so this extends
 * {@link SelectableUIAction} rather than the per-element reflection base.
 */
public final class TrillAction extends SelectableUIAction {

    public static TrillAction createAction(MainFrame mainFrame) {
        return new TrillAction(mainFrame);
    }

    private TrillAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_TRILL),
            null,
            0,
            "trill",
            Strings.get(Strings.ACTION_TRILL_TOOLTIP),
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_TOGGLE_TRILL);
    }

    @Override
    protected boolean enableFromSelection(boolean activeSelection, ScoreView scoreView) {
        if (!super.enableFromSelection(activeSelection, scoreView)) {
            return false;
        }

        if (!activeSelection) {
            return true;
        }

        var state = activeSelectionOf(scoreView);

        return state != null && state.canToggleTrill();
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        // Let the base recompute enabled from the flags (REQUIRES_SELECTION,
        // grace mode, etc.).
        super.musicSelectionDidChange(message);
        refreshSelectedState();
    }

    @Override
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        // A trill added/removed by undo/redo leaves the selection range intact,
        // so no MusicSelectionDidChangeNotification fires; recompute the checked
        // state here as well, or the menu item stays stale after undo.
        super.songDidChange(message);
        refreshSelectedState();
    }

    /**
     * Sets the checked state to reflect whether the current selection overlaps
     * any existing trill.
     */
    private void refreshSelectedState() {
        var scoreView = getScoreView();
        var state = (scoreView != null) ? activeSelectionOf(scoreView) : null;

        if (state == null) {
            setSelected(false);
            return;
        }

        var begin = state.getSelectionBegin();
        var end = state.getSelectionEnd();

        if (begin == -1) {
            setSelected(false);
            return;
        }

        setSelected(state.getLine().hasTrillOverlapping(begin, end));
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleOnKeyboardShortcut(e);

        var state = activeSelectionOf(requireScoreView());

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var begin = state.getSelectionBegin();
        var end = state.getSelectionEnd();

        line.withModification(() -> {
            if (isSelected()) {
                addTrillSpanningSelection(line, begin, end);
            } else {
                line.removeTrillsOverlapping(begin, end);
            }
        });
    }

    @Nullable
    private LineSelectionState activeSelectionOf(ScoreView scoreView) {
        return scoreView.getSelectionCoordinator().getActiveSelection();
    }

    private void addTrillSpanningSelection(Line line, int begin, int end) {
        // Enablement (canToggleTrill) already guarantees a pitched note in the
        // range; the empty check is a defensive fallback for that invariant.
        var pitchedNotes = line.getElements(begin, end).stream()
            .filter(element -> element.getType().isPitchedNote())
            .toList();

        if (pitchedNotes.isEmpty()) {
            return;
        }

        line.addTrill(new Trill(pitchedNotes.getFirst(), pitchedNotes.getLast()));
    }
}
