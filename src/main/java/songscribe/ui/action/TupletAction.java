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

import org.jetbrains.annotations.NotNull;

import net.engio.mbassy.listener.Handler;

import songscribe.ui.message.Message;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.message.ToggleTupletMessage;
import songscribe.util.StringUtils;

public class TupletAction extends UIAction {

    public enum Tuplet {
        REMOVE(0),
        DUPLET(2),
        TRIPLET(3),
        QUADRUPLET(4),
        QUINTUPLET(5),
        SEXTUPLET(6),
        SEPTUPLET(7);

        private final int size;

        Tuplet(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }
    }

    private final Tuplet tuplet;

    public TupletAction(Tuplet tuplet) {
        super(
            getName(tuplet),
            "@\uF376",
            18,
            getName(tuplet).toLowerCase(),
            getTooltip(tuplet)
        );
        this.tuplet = tuplet;
        setFlags(
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    public Tuplet getTuplet() {
        return tuplet;
    }

    private static String getName(Tuplet tuplet) {
        return StringUtils.capitalizeSentence(tuplet.name());
    }

    private static String getTooltip(Tuplet tuplet) {
        return ((tuplet == Tuplet.REMOVE) ? "Remove" : "Create") + " tuplet from selection";
    }

    // Set priority to HIGH so that the action is updated before
    // the enabled state of the container is checked.
    @Override
    @Handler(priority = Message.HIGH_PRIORITY)
    public void musicSelectionDidChange(
        @NotNull MusicSelectionChangedMessage message
    ) {
        if (updateEnabledState()) {
            var toggleInfo = message.getScore().canToggleTuplet();
            var canToggle = toggleInfo.getFirst();
            var isTupleted = toggleInfo.getSecond();

            // If the selection is already tupleted, enable the "Remove" action
            setEnabled(canToggle && ((tuplet != Tuplet.REMOVE) || isTupleted));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new ToggleTupletMessage(this));
    }
}
