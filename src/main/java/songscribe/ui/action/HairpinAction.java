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

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddHairpinCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.component.MainFrame;

public final class HairpinAction extends UIAction {

    private final boolean isCrescendo;

    public static HairpinAction createCrescendoAction(MainFrame mainFrame) {
        return new HairpinAction(mainFrame, true);
    }

    public static HairpinAction createDiminuendoAction(MainFrame mainFrame) {
        return new HairpinAction(mainFrame, false);
    }

    private HairpinAction(MainFrame mainFrame, boolean isCrescendo) {
        super(
            mainFrame,
            Strings.get(isCrescendo ? Strings.ACTION_HAIRPIN_CRESCENDO : Strings.ACTION_HAIRPIN_DIMINUENDO),
            null,
            0,
            isCrescendo ? "add-crescendo" : "add-diminuendo",
            Strings.get(isCrescendo ? Strings.ACTION_HAIRPIN_CRESCENDO_TOOLTIP : Strings.ACTION_HAIRPIN_DIMINUENDO_TOOLTIP),
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_IN_REST_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.isCrescendo = isCrescendo;
    }

    public boolean isCrescendo() {
        return isCrescendo;
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        handleChange();
    }

    @Override
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        handleChange();
    }

    @Override
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        handleChange();
    }

    private void handleChange() {
        updateEnabledState();
    }

    /**
     * Single writer of both the enabled flag and the menu label: the flags alone
     * cannot tell an add from an extend, and a flag-only enable would both mislabel
     * the item and allow a one-element hairpin.
     */
    @Override
    public boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            return false;
        }

        var ctrl = getScoreViewController();

        if (ctrl == null) {
            // Without a controller the state cannot be resolved, and super has already
            // enabled the action from flags alone — exactly the flag-only enable this
            // override exists to prevent.
            setEnabled(false);
            return false;
        }

        applyHairpinState(ctrl.resolveHairpinAction().state());
        return true;
    }

    private void applyHairpinState(MusicEditOperations.HairpinActionState state) {
        var extendState = isCrescendo
            ? MusicEditOperations.HairpinActionState.EXTEND_CRESCENDO
            : MusicEditOperations.HairpinActionState.EXTEND_DIMINUENDO;
        var isExtend = state == extendState;
        var canAdd = state == MusicEditOperations.HairpinActionState.CAN_ADD;

        String nameKey;
        String tooltipKey;

        if (isExtend) {
            nameKey = isCrescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND
                : Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND;
            tooltipKey = isCrescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND_TOOLTIP
                : Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND_TOOLTIP;
        } else {
            nameKey = isCrescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO
                : Strings.ACTION_HAIRPIN_DIMINUENDO;
            tooltipKey = isCrescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO_TOOLTIP
                : Strings.ACTION_HAIRPIN_DIMINUENDO_TOOLTIP;
        }

        putValue(Action.NAME, Strings.get(nameKey));
        putValue(Action.SHORT_DESCRIPTION, Strings.get(tooltipKey));
        setEnabled(canAdd || isExtend);
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new AddHairpinCommand(isCrescendo));
    }
}
