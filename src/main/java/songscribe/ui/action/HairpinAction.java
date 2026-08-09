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
import songscribe.dom.Hairpin;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddHairpinCommand;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.component.MainFrame;

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
        // selection contains a rest — and the latter is exactly the "note, rest"
        // selection this action must support. Because super.updateEnabledState() runs
        // before resolveHairpinAction(), no resolution logic could recover from it.
        // Dropping the flag also makes the items available while the rest tool is
        // armed, which is intended: input mode governs the next *inserted* element,
        // not what a hairpin drawn over an existing selection may cover.
        // resolveHairpinAction() is now the sole authority on rests — interior rests
        // are fine, a trailing rest is a valid endpoint, a leading rest is INELIGIBLE.
        super(
            mainFrame,
            Strings.get(kind == Hairpin.Kind.CRESCENDO ? Strings.ACTION_HAIRPIN_CRESCENDO : Strings.ACTION_HAIRPIN_DIMINUENDO),
            null,
            0,
            kind == Hairpin.Kind.CRESCENDO ? "add-crescendo" : "add-diminuendo",
            Strings.get(kind == Hairpin.Kind.CRESCENDO ? Strings.ACTION_HAIRPIN_CRESCENDO_TOOLTIP : Strings.ACTION_HAIRPIN_DIMINUENDO_TOOLTIP),
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.kind = kind;
    }

    public Hairpin.Kind getKind() {
        return kind;
    }

    /**
     * Single writer of both the enabled flag and the menu label: the flags alone
     * cannot tell an add from an extend, and a flag-only enable would both mislabel
     * the item and allow a one-element hairpin.
     * <p>
     * Every disabling path relabels before returning. The label is this class's to
     * maintain and nothing else resets it, so an early return that skipped it would
     * strand "Extend Crescendo" on the menu after the selection that justified it
     * was gone — a greyed-out promise to extend a hairpin that is no longer in play.
     */
    @Override
    public boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            applyLabel(false);
            return false;
        }

        var ctrl = getScoreViewController();

        if (ctrl == null) {
            // Without a controller the state cannot be resolved, and super has already
            // enabled the action from flags alone — exactly the flag-only enable this
            // override exists to prevent.
            applyLabel(false);
            setEnabled(false);
            return false;
        }

        applyHairpinState(ctrl.resolveHairpinAction(kind).state());
        return true;
    }

    private void applyHairpinState(MusicEditOperations.HairpinActionState state) {
        var isExtend = state == MusicEditOperations.HairpinActionState.EXTEND;
        applyLabel(isExtend);
        setEnabled(isExtend || state == MusicEditOperations.HairpinActionState.CAN_ADD);
    }

    /**
     * Sets the menu text and tooltip to either the extend or the add wording.
     */
    private void applyLabel(boolean isExtend) {
        String nameKey;
        String tooltipKey;
        var isCrescendo = kind == Hairpin.Kind.CRESCENDO;

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
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new AddHairpinCommand(kind));
    }
}
