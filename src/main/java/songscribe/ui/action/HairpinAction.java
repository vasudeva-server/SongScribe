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

import java.awt.event.ActionEvent;
import javax.swing.Action;

import songscribe.Strings;
import songscribe.dom.Hairpin;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddHairpinCommand;
import songscribe.ui.component.MainFrame;
import songscribe.ui.selection.RangeQueries.HairpinResolution;

public final class HairpinAction extends UIAction {

    private final Hairpin.Kind kind;

    public static HairpinAction createCrescendoAction(MainFrame mainFrame) {
        return new HairpinAction(mainFrame, Hairpin.Kind.CRESCENDO);
    }

    public static HairpinAction createDiminuendoAction(MainFrame mainFrame) {
        return new HairpinAction(mainFrame, Hairpin.Kind.DIMINUENDO);
    }

    private HairpinAction(MainFrame mainFrame, Hairpin.Kind kind) {
        // DISABLE_IN_REST_MODE is deliberately absent. UIAction.enableInRestMode() is a
        // conjunction: it disables both while the rest tool is armed and whenever the
        // selection contains a rest — and a selection with a rest inside it, between
        // two notes, must stay enabled. Because super.updateEnabledState() runs before
        // resolveHairpinAction(), no resolution logic could recover from it. Leaving the
        // flag off also makes the items available while the rest tool is armed, which
        // is intended: input mode governs the next *inserted* element, not what a
        // hairpin drawn over an existing selection may cover. resolveHairpinAction()
        // answers for rests through Hairpin.spanning: rests inside the selection are
        // fine, and a rest at either end makes the selection INELIGIBLE.
        super(
            mainFrame,
            Strings.get(new HairpinResolution.Ineligible(kind).nameKey()),
            null,
            0,
            kind == Hairpin.Kind.CRESCENDO ? "add-crescendo" : "add-diminuendo",
            Strings.get(new HairpinResolution.Ineligible(kind).tooltipKey()),
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
        this.kind = kind;
    }

    public Hairpin.Kind getKind() {
        return kind;
    }

    /**
     * Single writer of both the enabled flag and the menu label. Every disabling path relabels
     * before returning, because nothing else resets the label.
     */
    @Override
    public boolean updateEnabledState() {
        var ineligible = new HairpinResolution.Ineligible(kind);

        if (!super.updateEnabledState()) {
            applyLabel(ineligible);
            return false;
        }

        var ctrl = getScoreViewController();

        if (ctrl == null) {
            // Without a controller the state cannot be resolved, and super has already
            // enabled the action from flags alone — exactly the flag-only enable this
            // override exists to prevent.
            applyLabel(ineligible);
            setEnabled(false);
            return false;
        }

        var resolution = ctrl.resolveHairpinAction(kind);
        applyLabel(resolution);
        setEnabled(resolution instanceof HairpinResolution.Applicable);
        return true;
    }

    private void applyLabel(HairpinResolution resolution) {
        putValue(Action.NAME, Strings.get(resolution.nameKey()));
        putValue(Action.SHORT_DESCRIPTION, Strings.get(resolution.tooltipKey()));
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new AddHairpinCommand(kind));
    }
}
