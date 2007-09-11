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

import java.util.Arrays;
import java.util.List;

import javax.swing.*;

import net.engio.mbassy.listener.Handler;

import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.util.GraphicUtils;

public class InsertPopupButton extends PopupButton {

    private static final List<UIAction> ACTIONS = Arrays.asList(
        Actions.ANNOTATION_ACTION,
        Actions.TEMPO_CHANGE_ACTION,
        Actions.BEAT_CHANGE_ACTION,
        Actions.KEY_SIGNATURE_CHANGE_ACTION
    );

    public InsertPopupButton() {
        super(ACTIONS, null);
        setIcon(GraphicUtils.getScaledSVGIcon("plus.svg", 20));
        setToolTipText(
            "<html><strong>Insert</strong><br>Insert other elements</html>"
        );
        MessageCenter.subscribe(this);
    }

    @Handler
    public void musicSelectionDidChange(MusicSelectionChangedMessage message) {
        // Enable only if one of the actions is enabled
        setEnabled(ACTIONS.stream().anyMatch(AbstractAction::isEnabled));
    }
}
