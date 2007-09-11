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

import net.engio.mbassy.listener.Handler;

import songscribe.ui.message.BarSelectedMessage;
import songscribe.ui.message.Message;
import songscribe.ui.message.MessageCenter;

/**
 * An action group that manages the duration actions.
 * Duration actions are mutually exclusive with any of the bar actions.
 */
public class DurationActionGroup extends ActionGroup<DurationAction> {

    DurationActionGroup(DurationAction... actions) {
        super(actions);
        MessageCenter.subscribe(this);
    }

    // It's important for this to happen first, so that the duration actions
    // are deselected before any other handlers are called.
    @Handler(priority = Message.HIGH_PRIORITY)
    public void barWasSelected(BarSelectedMessage message) {
        clearSelection();
    }
}
