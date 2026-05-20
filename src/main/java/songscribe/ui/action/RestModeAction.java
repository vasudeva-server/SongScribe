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
import songscribe.dom.StaffElement;
import songscribe.message.notification.RestModeDidChangeNotification;

public final class RestModeAction extends SelectableUIAction implements UIAction.Reflectable {

    public static RestModeAction createAction() {
        return new RestModeAction();
    }

    private RestModeAction() {
        super(
            Strings.get(Strings.ACTION_REST_MODE),
            "@\uF371",
            22,
            "rest-mode",
            Strings.get(Strings.ACTION_REST_MODE_TOOLTIP),
            KeyEvent.VK_R,
            0,
            Flag.REQUIRES_EMPTY_SELECTION,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.ENABLE_WHEN_DURATION_SELECTED,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    @Override
    public boolean appliesTo(StaffElement element) {
        return element.getType().isDuration();
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.getType().isRest();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        toggleOnKeyboardShortcut(e);
        MessageCenter.post(new RestModeDidChangeNotification());
    }
}
