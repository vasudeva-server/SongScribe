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

import songscribe.ui.message.FirstSecondEndingMessage;
import songscribe.message.MessageCenter;

public class FirstSecondEndingAction extends UIAction {

    private final boolean makeEnding;

    public FirstSecondEndingAction(boolean makeEnding) {
        super(
            Strings.get(makeEnding ? Strings.ACTION_ENDING_MAKE : Strings.ACTION_ENDING_REMOVE),
            "first-second-ending"
        );
        this.makeEnding = makeEnding;
        setFlags(
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_IN_REST_MODE,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
        // TODO: Determine if make action should be enabled/disabled based on selection
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new FirstSecondEndingMessage(makeEnding));
    }
}
