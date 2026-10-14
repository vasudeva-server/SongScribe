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

package songscribe.ui.component.score;

import java.util.List;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.edit.EditModeManager;

/**
 * An insertion of the preview element that its gate accepted and that has not landed yet.
 * Nothing is mutated until it lands.
 * <p>
 * Only {@link PreviewElementInserter} creates one, and only after the gate, so holding one is
 * proof the insertion may go ahead. It is valid until an element is next inserted into or removed
 * from the line; landing it after that inserts at an index the gate did not judge.
 */
public final class AcceptedInsertion {

    private final Line line;
    private final int index;
    private final StaffElement element;
    private final boolean appends;

    AcceptedInsertion(Line line, int index, StaffElement element) {
        this.line = line;
        this.index = index;
        this.element = element;
        this.appends = index == line.elementCount();
    }

    public boolean appends() {
        return appends;
    }

    /**
     * The caller must hold a modification bracket.
     */
    public StaffElement land() {
        if (!appends) {
            line.repairNeighborsBeforeInsertion(index, List.of(element));
        }

        line.addElement(index, element);

        if (!appends) {
            line.repairNeighborsAfterInsertion(index, element);
        }

        return landed();
    }

    /**
     * For a caller that inserts with mutation tracking suspended, where a repair would never be
     * recorded. The caller owes the repairs and runs them inside the bracket that records the
     * insertion — see {@link songscribe.dom.LyricRun#repairNeighborsAfterUntrackedInsertion}.
     */
    public StaffElement landWithoutRepairs() {
        line.addElement(index, element);
        return landed();
    }

    private StaffElement landed() {
        EditModeManager.previewElementDidChange(line, index);
        return element;
    }
}
