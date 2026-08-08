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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;

import javax.swing.JPopupMenu;

/**
 * Helpers shared by the tests for {@link BasePopupButton}'s subclasses.
 */
final class PopupButtonTestSupport {

    private PopupButtonTestSupport() {
    }

    /**
     * Opens the button's popup and returns it. {@code JPopupMenu.show} needs the invoker to be
     * showing on screen, which a detached button never is, so set the invoker and toggle
     * visibility directly instead.
     */
    static JPopupMenu showPopup(BasePopupButton button) {
        var popup = button.requirePopup();
        popup.setInvoker(button);
        popup.setVisible(true);

        assertThat(popup.isVisible()).as("popup did not open").isTrue();

        return popup;
    }

    /**
     * Fires the button's own action, the way clicking the button itself does.
     */
    static void clickButton(BasePopupButton button) {
        button.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, ""));
    }
}
