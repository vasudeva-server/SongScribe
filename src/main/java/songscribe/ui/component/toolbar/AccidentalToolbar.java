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

package songscribe.ui.component.toolbar;

import songscribe.ui.action.Actions;
import songscribe.ui.component.ToolbarToggleButton;

public class AccidentalToolbar extends Toolbar {

    public AccidentalToolbar() {
        add(new ToolbarToggleButton(Actions.DOUBLE_FLAT_ACTION));
        add(new ToolbarToggleButton(Actions.FLAT_ACTION));
        add(new ToolbarToggleButton(Actions.NATURAL_ACTION));
        add(new ToolbarToggleButton(Actions.SHARP_ACTION));
    }
}
