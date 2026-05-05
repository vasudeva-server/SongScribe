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


import net.engio.mbassy.listener.Handler;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.Score;
import songscribe.util.StringUtils;

public final class TupletAction extends UIAction {

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

    public static TupletAction createDupletAction() {
        return new TupletAction(Tuplet.DUPLET);
    }

    public static TupletAction createTripletAction() {
        return new TupletAction(Tuplet.TRIPLET);
    }

    public static TupletAction createQuadrupletAction() {
        return new TupletAction(Tuplet.QUADRUPLET);
    }

    public static TupletAction createQuintupletAction() {
        return new TupletAction(Tuplet.QUINTUPLET);
    }

    public static TupletAction createSextupletAction() {
        return new TupletAction(Tuplet.SEXTUPLET);
    }

    public static TupletAction createSeptupletAction() {
        return new TupletAction(Tuplet.SEPTUPLET);
    }

    public static TupletAction createRemoveAction() {
        return new TupletAction(Tuplet.REMOVE);
    }

    private TupletAction(Tuplet tuplet) {
        super(
            getName(tuplet),
            "@\uF376",
            18,
            getName(tuplet).toLowerCase(),
            getTooltip(tuplet),
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.tuplet = tuplet;
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
        MusicSelectionDidChangeNotification message
    ) {
        handleChange(message.getScore());
    }

    @Override
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        var score = getScore();

        if (score != null) {
            handleChange(score);
        }
    }

    @Override
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        var score = getScore();

        if (score != null) {
            handleChange(score);
        }
    }

    private void handleChange(Score score) {
        if (!updateEnabledState()) {
            return;
        }

        var info = score.canToggleTuplet();

        if (!info.canToggle()) {
            setEnabled(false);
            return;
        }

        var existing = info.existing();

        if (tuplet == Tuplet.REMOVE) {
            setEnabled(existing != null);
        } else if (existing == null) {
            setEnabled(true);
        } else if (!info.coversExisting()) {
            // Strict sub-range of an existing tuplet: creating or changing
            // grade would silently destroy the existing tuplet and replace
            // it with a sub-range tuplet, so only REMOVE is meaningful.
            setEnabled(false);
        } else {
            // Full coverage: disable the action matching the existing grade
            // (clicking it would be a no-op); other add-actions perform a
            // grade change via remove + add.
            setEnabled(existing.getGrade() != tuplet.getSize());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new ToggleTupletCommand(this));
    }
}
