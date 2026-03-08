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

import songscribe.music.ForceArticulation;
import songscribe.music.StaffElement;

public class ForceArticulationAction extends NoteOnlyAction {

    private final ForceArticulation articulation;

    public ForceArticulationAction(
        ForceArticulation articulation,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        super(name, icon, size, actionCommand, tooltip);
        this.articulation = articulation;
    }

    public ForceArticulation getArticulation() {
        return articulation;
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.getForceArticulation() == articulation;
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        element.setForceArticulation(selected ? articulation : null);
    }
}
