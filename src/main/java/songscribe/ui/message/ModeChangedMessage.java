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

package songscribe.ui.message;

import songscribe.message.Message;

import org.jetbrains.annotations.NotNull;
import songscribe.ui.Mode;
import songscribe.ui.action.ModeAction;

public class ModeChangedMessage extends Message {

    private final ModeAction action;
    private final boolean adjustmentMode;

    public ModeChangedMessage(ModeAction action) {
        this.action = action;
        adjustmentMode = action.getActionCommand().startsWith("adjust-");
    }

    public ModeAction getAction() {
        return action;
    }

    public Mode getMode() {
        return action.getMode();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isAdjustmentMode() {
        return adjustmentMode;
    }

    @NotNull
    @Override
    public String toString() {
        return (
            super.toString() +
            "(mode = '" +
            getMode() +
            "', isAdjustmentMode = " +
            adjustmentMode +
            ')'
        );
    }
}
