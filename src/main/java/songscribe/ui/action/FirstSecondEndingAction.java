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

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.dom.EndingValidationResult;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;

public final class FirstSecondEndingAction extends UIAction {

    private @Nullable EndingValidationResult cachedResult;

    FirstSecondEndingAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_ENDING_MAKE),
            "first-second-ending",
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    public void validate(ScoreViewController ctrl) {
        cachedResult = ctrl.canMakeFirstSecondEnding();
        setEnabled(cachedResult.isValid());
    }

    public @Nullable EndingValidationResult getCachedResult() {
        return cachedResult;
    }

    public void setCachedResult(@Nullable EndingValidationResult result) {
        this.cachedResult = result;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new FirstSecondEndingCommand());
    }
}
