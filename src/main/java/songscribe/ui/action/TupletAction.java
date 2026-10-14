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
import java.util.Locale;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.dom.TupletGrade;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.notification.LintingNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;

public final class TupletAction extends UIAction {

    private final TupletGrade grade;

    public static TupletAction createDupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, TupletGrade.DUPLET);
    }

    public static TupletAction createTripletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, TupletGrade.TRIPLET);
    }

    public static TupletAction createQuadrupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, TupletGrade.QUADRUPLET);
    }

    public static TupletAction createQuintupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, TupletGrade.QUINTUPLET);
    }

    public static TupletAction createSextupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, TupletGrade.SEXTUPLET);
    }

    public static TupletAction createSeptupletAction(MainFrame mainFrame) {
        return new TupletAction(mainFrame, TupletGrade.SEPTUPLET);
    }

    private TupletAction(MainFrame mainFrame, TupletGrade grade) {
        super(
            mainFrame,
            getName(grade),
            "@\uF376",
            18,
            grade.name().toLowerCase(Locale.ROOT),
            Strings.get(Strings.ACTION_TUPLET_TOOLTIP),
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
        this.grade = grade;
    }

    public TupletGrade getGrade() {
        return grade;
    }

    private static String getName(TupletGrade grade) {
        return Strings.get(switch (grade) {
            case DUPLET -> Strings.ACTION_TUPLET_DUPLET;
            case TRIPLET -> Strings.ACTION_TUPLET_TRIPLET;
            case QUADRUPLET -> Strings.ACTION_TUPLET_QUADRUPLET;
            case QUINTUPLET -> Strings.ACTION_TUPLET_QUINTUPLET;
            case SEXTUPLET -> Strings.ACTION_TUPLET_SEXTUPLET;
            case SEPTUPLET -> Strings.ACTION_TUPLET_SEPTUPLET;
        });
    }

    // Set priority to HIGH so that the action is updated before
    // the enabled state of the container is checked.
    @Override
    @Handler(priority = Message.HIGH_PRIORITY)
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        var ctrl = getScoreViewController();

        if (ctrl != null) {
            handleChange(ctrl);
        }
    }

    @Override
    @Handler
    public void linting(LintingNotification message) {
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

        // Availability and selection are separate properties of the same shared action, so
        // the menu and the toolbar popup both pick them up with no rebuild. The existing
        // grade stays checked even when the span could not be turned into that grade again
        // — otherwise a selection that visibly is a tuplet would show nothing checked.
        putValue(SELECTED_KEY, info.existingGrade() == grade);
        setEnabled(info.validGrades().contains(grade));
    }

    @Override
    public String getUndoOpName() {
        var baseAddKey = baseAddKey();
        var ctrl = getScoreViewController();

        if (ctrl == null) {
            return Strings.get(baseAddKey);
        }

        if (ctrl.canToggleTuplet().existingGrade() != null) {
            return Strings.get(Strings.ACTION_EDIT_OP_CHANGE_TUPLET_GRADE);
        }

        return Strings.get(baseAddKey);
    }

    private String baseAddKey() {
        return switch (grade) {
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
        MessageCenter.post(new ToggleTupletCommand(grade));
    }
}
