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
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;
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

    public static TupletAction createDupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.DUPLET);
    }

    public static TupletAction createTripletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.TRIPLET);
    }

    public static TupletAction createQuadrupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.QUADRUPLET);
    }

    public static TupletAction createQuintupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.QUINTUPLET);
    }

    public static TupletAction createSextupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.SEXTUPLET);
    }

    public static TupletAction createSeptupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.SEPTUPLET);
    }

    public static TupletAction createRemoveAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, Tuplet.REMOVE);
    }

    private TupletAction(MainFrame mainFrame, Tuplet tuplet) {
        super(
            mainFrame,
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
        var ctrl = message.getScoreViewController();

        if (ctrl != null) {
            handleChange(ctrl);
        }
    }

    @Override
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        var ctrl = getScoreViewController();

        if (ctrl != null) {
            handleChange(ctrl);
        }
    }

    @Override
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        var ctrl = getScoreViewController();

        if (ctrl != null) {
            handleChange(ctrl);
        }
    }

    private void handleChange(ScoreViewController ctrl) {
        if (!updateEnabledState()) {
            return;
        }

        var info = ctrl.canToggleTuplet();

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

    /**
     * Resolves the Tier-A undo op-name from the current selection state. Because this
     * runs on every dispatch (including no-selection ones that only set mode state), it
     * null-guards the selection and falls back to the action's base add-size key when
     * there is no controller or no existing tuplet — never dereferencing a null element.
     *
     * <ul>
     *   <li>Remove action → {@code Remove Tuplet}.</li>
     *   <li>No existing tuplet at the selection → {@code Add <Size>}.</li>
     *   <li>Existing tuplet present (different grade chosen) → {@code Change Tuplet Grade}.</li>
     * </ul>
     */
    @Override
    public String getUndoOpName() {
        if (tuplet == Tuplet.REMOVE) {
            return Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TUPLET);
        }

        var baseAddKey = baseAddKey();
        var ctrl = getScoreViewController();

        if (ctrl == null) {
            return Strings.get(baseAddKey);
        }

        var existing = ctrl.canToggleTuplet().existing();

        if (existing != null) {
            return Strings.get(Strings.ACTION_EDIT_OP_CHANGE_TUPLET_GRADE);
        }

        return Strings.get(baseAddKey);
    }

    private String baseAddKey() {
        return switch (tuplet) {
            case DUPLET -> Strings.ACTION_EDIT_OP_ADD_DUPLET;
            case TRIPLET -> Strings.ACTION_EDIT_OP_ADD_TRIPLET;
            case QUADRUPLET -> Strings.ACTION_EDIT_OP_ADD_QUADRUPLET;
            case QUINTUPLET -> Strings.ACTION_EDIT_OP_ADD_QUINTUPLET;
            case SEXTUPLET -> Strings.ACTION_EDIT_OP_ADD_SEXTUPLET;
            case SEPTUPLET -> Strings.ACTION_EDIT_OP_ADD_SEPTUPLET;
            case REMOVE -> Strings.ACTION_EDIT_OP_REMOVE_TUPLET;
        };
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new ToggleTupletCommand(this));
    }
}
