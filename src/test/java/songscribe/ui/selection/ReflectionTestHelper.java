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

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.action.UIAction;

/**
 * Test utility for setting up a {@link SelectionCoordinator} with a {@link Line}
 * containing specific notes and a custom set of reflectable actions.
 * This bypasses the {@link songscribe.ui.action.Actions} reflection scan
 * that pulls in the full UI singleton graph.
 */
public final class ReflectionTestHelper {

    private ReflectionTestHelper() {
    }

    /**
     * Creates a SelectionCoordinator with a Line containing the given notes,
     * registered and activated at line index 0, with the given actions
     * injected as the reflectable actions list.
     */
    public static SelectionCoordinator createCoordinator(
            List<Note> notes,
            List<UIAction.Reflectable> actions
    ) {
        var line = new Line();

        for (var note : notes) {
            line.addNote(note);
        }

        var coordinator = new SelectionCoordinator(() -> null);
        var state = new LineSelectionState(line);
        coordinator.registerLineState(0, state);
        coordinator.activateLine(0);

        try {
            var field = SelectionCoordinator.class.getDeclaredField("reflectableActions");
            field.setAccessible(true);
            field.set(coordinator, new ArrayList<>(actions));
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject reflectableActions", e);
        }

        return coordinator;
    }

    /**
     * Selects notes [fromIndex..toIndex] inclusive on the coordinator's active line.
     */
    public static void selectRange(SelectionCoordinator coordinator, int fromIndex, int toIndex) {
        var state = coordinator.getActiveSelection();
        state.setSelectionFromClick(fromIndex);

        if (toIndex != fromIndex) {
            state.extendSelectionTo(toIndex);
        }
    }

    /**
     * Selects a single note on the coordinator's active line.
     */
    public static void selectNote(SelectionCoordinator coordinator, int noteIndex) {
        selectRange(coordinator, noteIndex, noteIndex);
    }

    /**
     * Clears the selection on the coordinator's active line.
     */
    public static void clearSelection(SelectionCoordinator coordinator) {
        coordinator.getActiveSelection().clearSelection();
    }
}
