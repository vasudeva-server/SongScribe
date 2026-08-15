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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.InsertLineCommand;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class InsertLineAction extends UIAction {

    /** Where the new line goes. The two relative variants need a selected line; the first does not. */
    public enum Type {
        ADD_AT_END,
        INSERT_BEFORE,
        INSERT_AFTER,
    }

    private final Type type;

    public static InsertLineAction createAddLineAction(MainFrame mainFrame) {
        return new InsertLineAction(
            mainFrame, Strings.get(Strings.MENU_SONG_LINE_AT_END), Type.ADD_AT_END);
    }

    public static InsertLineAction createInsertLineBeforeAction(MainFrame mainFrame) {
        return new InsertLineAction(
            mainFrame, Strings.get(Strings.MENU_SONG_LINE_BEFORE), Type.INSERT_BEFORE);
    }

    public static InsertLineAction createInsertLineAfterAction(MainFrame mainFrame) {
        return new InsertLineAction(
            mainFrame, Strings.get(Strings.MENU_SONG_LINE_AFTER), Type.INSERT_AFTER);
    }

    private InsertLineAction(MainFrame mainFrame, String name, Type type) {
        super(
            mainFrame,
            name,
            getActionCommand(type),
            KeyEvent.VK_ENTER,
            getAcceleratorModifiers(type),
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.type = type;
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_INSERT_LINE);
    }

    private static String getActionCommand(Type type) {
        return switch (type) {
            case ADD_AT_END -> "add-line";
            case INSERT_BEFORE -> "insert-line-before";
            case INSERT_AFTER -> "insert-line-after";
        };
    }

    /**
     * All three variants are Return with the menu shortcut key; the two that act
     * relative to the selected line add a distinguishing modifier.
     */
    private static int getAcceleratorModifiers(Type type) {
        return switch (type) {
            case ADD_AT_END -> UIUtils.MENU_SHORTCUT_MASK;
            case INSERT_BEFORE -> UIUtils.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK;
            case INSERT_AFTER -> UIUtils.MENU_SHORTCUT_MASK | InputEvent.ALT_DOWN_MASK;
        };
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new InsertLineCommand(type));
    }

    @Override
    public boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            return false;
        }

        if (type == Type.ADD_AT_END) {
            return true;
        }

        var isEnabled = requireScoreView().getSelectionCoordinator().hasLineSelection();
        setEnabled(isEnabled);
        return isEnabled;
    }
}
