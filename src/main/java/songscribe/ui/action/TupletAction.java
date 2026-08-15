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

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;
import songscribe.util.StringUtils;

public final class TupletAction extends UIAction {

    public enum Tuplet {
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

    private static final String TOOLTIP = "Create tuplet from selection";

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

    private TupletAction(MainFrame mainFrame, Tuplet tuplet) {
        super(
            mainFrame,
            getName(tuplet),
            "@\uF376",
            18,
            getName(tuplet).toLowerCase(),
            TOOLTIP,
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
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
        var existing = info.existing();

        // Availability and selection are separate properties of the same shared action, so
        // the menu and the toolbar popup both pick them up with no rebuild. The existing
        // grade stays checked even when the span could not be turned into that grade again
        // — otherwise a selection that visibly is a tuplet would show nothing checked.
        putValue(SELECTED_KEY, (existing != null) && (existing.getGrade() == tuplet.getSize()));
        setEnabled(info.validGrades().contains(tuplet.getSize()));
    }

    /**
     * Resolves the Tier-A undo op-name from the current selection state. Because this
     * runs on every dispatch (including no-selection ones that only set mode state), it
     * null-guards the selection and falls back to the action's base add-size key when
     * there is no controller or no existing tuplet — never dereferencing a null element.
     *
     * <ul>
     *   <li>No existing tuplet at the selection → {@code Add <Size>}.</li>
     *   <li>Existing tuplet present (different grade chosen) → {@code Change Tuplet Grade}.</li>
     * </ul>
     */
    @Override
    public String getUndoOpName() {
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
        };
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new ToggleTupletCommand(this));
    }
}
