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
import songscribe.message.MessageCenter;
import songscribe.ui.Mode;
import songscribe.message.notification.ModeDidChangeNotification;

public final class ModeAction extends SelectableUIAction {

    private final Mode mode;

    public static ModeAction createSelectModeAction() {
        return new ModeAction(
            Mode.SELECT,
            Strings.get(Strings.ACTION_MODE_SELECT), "@\uF3D2", 22,
            "select-mode", Strings.get(Strings.ACTION_MODE_SELECT_TOOLTIP)
        );
    }

    public static ModeAction createEditModeAction() {
        return new ModeAction(
            Mode.EDIT,
            Strings.get(Strings.ACTION_MODE_EDIT), "@\uEF63", 22,
            "edit-mode", Strings.get(Strings.ACTION_MODE_EDIT_TOOLTIP)
        );
    }

    public static ModeAction createAdjustMusicModeAction() {
        return new ModeAction(
            Mode.ADJUSTMENT,
            Strings.get(Strings.ACTION_MODE_MUSIC_ADJUST), "mode-note-adjustment.svg", 26,
            "adjust-note-mode", Strings.get(Strings.ACTION_MODE_MUSIC_ADJUST_TOOLTIP)
        );
    }

    public static ModeAction createAdjustLyricsModeAction() {
        return new ModeAction(
            Mode.LYRICS_ADJUSTMENT,
            Strings.get(Strings.ACTION_MODE_LYRICS_ADJUST), "mode-lyrics-adjustment.svg", 26,
            "adjust-lyrics-mode", Strings.get(Strings.ACTION_MODE_LYRICS_ADJUST_TOOLTIP)
        );
    }

    public static ModeAction createAdjustVerticalModeAction() {
        return new ModeAction(
            Mode.VERTICAL_ADJUSTMENT,
            Strings.get(Strings.ACTION_MODE_VERTICAL_ADJUST), "mode-vertical-adjustment.svg", 26,
            "adjust-vertical-mode", Strings.get(Strings.ACTION_MODE_VERTICAL_ADJUST_TOOLTIP)
        );
    }

    private ModeAction(
        Mode mode,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        this(mode, name, icon, size, actionCommand, tooltip, 0, 0);
    }

    private ModeAction(
        Mode mode,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            virtualKey,
            modifiers,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        toggleOnKeyboardShortcut(e);
        MessageCenter.post(new ModeDidChangeNotification(this));
    }
}
