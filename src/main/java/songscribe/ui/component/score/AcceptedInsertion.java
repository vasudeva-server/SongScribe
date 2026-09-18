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
 * An insertion of the preview element that its gate accepted and that has not landed yet: the
 * chain walk has stamped the element as it will sound, the line has room for it, and every
 * confirmation was given. Nothing is mutated until it lands.
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

    /**
     * Whether the element lands past the line's last element.
     *
     * @return {@code true} when nothing follows the element once it lands, so no neighbor after
     *     it needs a repair
     */
    public boolean appends() {
        return appends;
    }

    /**
     * Inserts the element and repairs its neighbors: the lyric chains and the glissando the
     * insertion breaks. An append has no neighbor after it and repairs nothing.
     *
     * @return the element inserted
     * @effects the repairs and the insertion post through the line's primitives, so the caller
     *     holds a modification bracket
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
     * Inserts the element and leaves its neighbors as they are, for a caller that inserts with
     * mutation tracking suspended: a repair made there would be applied but never recorded, so
     * undo could not put the neighbors back. The caller owes the repairs, and runs them inside
     * the bracket that records the insertion — see
     * {@link songscribe.dom.LyricRun#repairNeighborsAfterUntrackedInsertion}.
     *
     * @return the element inserted
     */
    public StaffElement landWithoutRepairs() {
        line.addElement(index, element);
        return landed();
    }

    /** Re-arms the preview for the next click, and returns the element that landed. */
    private StaffElement landed() {
        EditModeManager.previewElementDidChange(line, index);
        return element;
    }
}
