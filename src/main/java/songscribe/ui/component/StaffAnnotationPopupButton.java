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

import module java.desktop;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.ui.action.Actions;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.util.GraphicUtils;

public class StaffAnnotationPopupButton extends PopupButton {

    public StaffAnnotationPopupButton() {
        super(Actions.STAFF_ANNOTATION_ACTIONS, null);
        setIcon(GraphicUtils.getScaledSVGIcon("plus.svg", 20, true));
        setToolTipText(Strings.get(Strings.TOOLTIP_STAFF_ANNOTATIONS));
        MessageCenter.subscribe(this);
    }

    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        // Enable only if one of the actions is enabled
        setEnabled(Actions.STAFF_ANNOTATION_ACTIONS.stream().anyMatch(AbstractAction::isEnabled));
    }
}
