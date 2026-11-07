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
package songscribe.ui;

import java.awt.Component;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.lint.LintRule;

/** A way the song leaves the editor, which {@link LintRule.Flag#BLOCKS_OUTPUT} restricts. */
public enum Output {

    SAVE {
        @Override
        String titleKey() {
            return Strings.ALERT_TITLE_SAVE;
        }

        @Override
        String blockedOutputKey() {
            return Strings.ALERT_OUTPUT_PRINT_OR_EXPORT;
        }

        @Override
        @Nullable String questionKey() {
            return Strings.ALERT_OUTPUT_SAVE_ANYWAY;
        }
    },

    PRINT {
        @Override
        String titleKey() {
            return Strings.ALERT_TITLE_PRINT;
        }

        @Override
        String blockedOutputKey() {
            return Strings.ALERT_OUTPUT_PRINT;
        }
    };

    abstract String titleKey();

    abstract String blockedOutputKey();

    /** @return the question that lets the user make this output anyway, or {@code null} if it is refused */
    @Nullable String questionKey() {
        return null;
    }

    /**
     * When this returns {@code false}, the user was already told why, so the caller shows
     * nothing more. Where dialogs are suppressed, a save is allowed and every other output is
     * refused.
     */
    public boolean allowed(Component parent, Song song) {
        if (!song.getLinter().getReport().hasFlag(LintRule.Flag.BLOCKS_OUTPUT)) {
            return true;
        }

        var titleKey = titleKey();
        var blockedOutput = Strings.get(blockedOutputKey());
        var questionKey = questionKey();

        if (questionKey == null) {
            OptionDialogs.showWarningMessage(parent, titleKey, Strings.ALERT_OUTPUT_WITH_ERRORS, blockedOutput, "");
            return false;
        }

        var confirm = new OptionDialogs.Confirm(
            titleKey,
            Strings.ALERT_OUTPUT_WITH_ERRORS,
            OptionDialogs.ConfirmButtons.YES_NO,
            OptionDialogs.MessageType.WARNING,
            OptionDialogs.Answer.YES,
            blockedOutput,
            Strings.get(questionKey)
        );

        return OptionDialogs.showConfirmDialog(parent, confirm) == OptionDialogs.Answer.YES;
    }
}
