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
import songscribe.music.EndingValidationResult;
import songscribe.ui.component.ScoreView;

public final class FirstSecondEndingAction extends UIAction {

    public static final FirstSecondEndingAction MAKE_ENDING_ACTION = new FirstSecondEndingAction();

    private @Nullable EndingValidationResult cachedResult;

    private FirstSecondEndingAction() {
        super(
            Strings.get(Strings.ACTION_ENDING_MAKE),
            "first-second-ending",
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    public void validate(ScoreView score) {
        cachedResult = score.canMakeFirstSecondEnding();
        setEnabled(cachedResult.isValid());
    }

    public @Nullable EndingValidationResult getCachedResult() {
        return cachedResult;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new FirstSecondEndingCommand());
    }
}
