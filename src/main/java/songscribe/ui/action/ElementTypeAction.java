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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.DurationWasSelectedNotification;
import songscribe.ui.component.MainFrame;

public class ElementTypeAction extends StickyUIAction implements UIAction.ElementReplaceable<StaffElement> {

    public enum Role {DURATION, NON_DURATION}

    // Shared by every duration entry, the grace quaver included.
    private static final Flag[] DURATION_FLAGS = new Flag[]{
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_WHEN_EDITING_TEXT,
    };

    // The six plain durations stay live in grace mode: with no staff selection possible there
    // they reach neither replaceTerminalIfSelected nor applyToSelectionIfActive and only re-arm
    // the preview duration, which is how the host note's duration is chosen — enterGraceNote
    // itself selects one through DURATION_ACTION_GROUP, which runs perform.
    private static final Flag[] PLAIN_DURATION_FLAGS = withFlags(DURATION_FLAGS, Flag.ENABLED_IN_GRACE_MODE);

    static final Flag[] NON_DURATION_FLAGS = new Flag[]{
        Flag.DISABLE_IN_REST_MODE,
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_WHEN_EDITING_TEXT,
    };

    private final ElementType type;
    private final Role role;

    public static ElementTypeAction createGraceEighthNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.GRACE_QUAVER,
            Strings.get(Strings.ACTION_DURATION_GRACE), "grace.svg", 26,
            "duration-grace-eighth", Strings.get(Strings.ACTION_DURATION_GRACE_TOOLTIP),
            KeyEvent.VK_G, 0,
            null,
            withFlags(DURATION_FLAGS, Flag.DISABLE_IN_REST_MODE, Flag.DISABLE_IN_SELECT_MODE)
        );
    }

    public static ElementTypeAction createThirtySecondNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.DEMI_SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND), "@\uF36B", 18,
            "duration-thirty-second", Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND_TOOLTIP),
            KeyEvent.VK_1, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            PLAIN_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createSixteenthNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_SIXTEENTH), "@\uF36A", 18,
            "duration-sixteenth", Strings.get(Strings.ACTION_DURATION_SIXTEENTH_TOOLTIP),
            KeyEvent.VK_2, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            PLAIN_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createEighthNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.QUAVER,
            Strings.get(Strings.ACTION_DURATION_EIGHTH), "@\uF369", 18,
            "duration-eighth", Strings.get(Strings.ACTION_DURATION_EIGHTH_TOOLTIP),
            KeyEvent.VK_3, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            PLAIN_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createQuarterNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.CROTCHET,
            Strings.get(Strings.ACTION_DURATION_QUARTER), "@\uF368", 18,
            "duration-quarter", Strings.get(Strings.ACTION_DURATION_QUARTER_TOOLTIP),
            KeyEvent.VK_4, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            PLAIN_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createHalfNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.MINIM,
            Strings.get(Strings.ACTION_DURATION_HALF), "@\uF367", 18,
            "duration-half", Strings.get(Strings.ACTION_DURATION_HALF_TOOLTIP),
            KeyEvent.VK_5, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            PLAIN_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createWholeNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.DURATION, ElementType.SEMIBREVE,
            Strings.get(Strings.ACTION_DURATION_WHOLE), "@\uF366", 18,
            "duration-whole", Strings.get(Strings.ACTION_DURATION_WHOLE_TOOLTIP),
            KeyEvent.VK_6, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            PLAIN_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createLeftRepeatAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.NON_DURATION, ElementType.REPEAT_LEFT,
            Strings.get(Strings.ACTION_REPEAT_LEFT), "@\uEF68", 24,
            "left-repeat", Strings.get(Strings.ACTION_REPEAT_LEFT_TOOLTIP),
            KeyEvent.VK_L, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createRightRepeatAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.NON_DURATION, ElementType.REPEAT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_RIGHT), "@\uF345", 24,
            "right-repeat", Strings.get(Strings.ACTION_REPEAT_RIGHT_TOOLTIP),
            KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createLeftRightRepeatAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.NON_DURATION, ElementType.REPEAT_LEFT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT), "@\uF34B", 24,
            "left-right-repeat", Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT_TOOLTIP),
            0, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createDoubleBarlineAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.NON_DURATION, ElementType.DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_DOUBLE), "@\uF347", 24,
            "double-barline", Strings.get(Strings.ACTION_BARLINE_DOUBLE_TOOLTIP),
            KeyEvent.VK_D, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createSingleBarlineAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.NON_DURATION, ElementType.SINGLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_SINGLE), "@\uF346", 24,
            "single-barline", Strings.get(Strings.ACTION_BARLINE_SINGLE_TOOLTIP),
            0, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createBreathMarkAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Role.NON_DURATION, ElementType.BREATH_MARK,
            Strings.get(Strings.ACTION_BREATH_MARK), null, 0,
            "breath-mark", Strings.get(Strings.ACTION_BREATH_MARK_TOOLTIP),
            KeyEvent.VK_COMMA, 0,
            Strings.ACTION_EDIT_OP_ADD_BREATH_MARK,
            NON_DURATION_FLAGS
        );
    }

    protected ElementTypeAction(
        MainFrame mainFrame,
        Role role,
        ElementType type,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        @Nullable String undoOpNameKey,
        Flag... flags
    ) {
        super(mainFrame, name, icon, size, actionCommand, tooltip, virtualKey, modifiers, flags);
        this.role = role;
        this.type = type;
        setUndoOpNameKey(undoOpNameKey);
    }

    public ElementType getType() {
        return type;
    }

    /**
     * The song-terminal type this entry stands for, or {@code null} if it stands for none, which
     * leaves the entry disabled and unchecked while the terminal is selected.
     */
    private @Nullable ElementType terminalType() {
        return type.isValidSongTerminal() ? type : null;
    }

    @Override
    public @Nullable StaffElement admit(StaffElement element) {
        if (role == Role.DURATION) {
            return element.getType().isDuration() ? element : null;
        }

        if (type == ElementType.BREATH_MARK) {
            return element.getType() == ElementType.BREATH_MARK ? element : null;
        }

        if (!element.getType().isBar()) {
            return null;
        }

        // The terminal accepts only the two valid terminal types, so only the two entries
        // standing for one of them apply to it.
        if (Song.isTerminalOfItsSong(element)) {
            return type.isValidSongTerminal() ? element : null;
        }

        return element;
    }

    // The terminal needs no case of its own: an entry stands only for its own type, so
    // comparing against that type answers the terminal too. It is admit() that keeps the
    // entries standing for no terminal type from ever being asked.
    @Override
    public boolean matchesElement(StaffElement element) {
        return element.getType().toNote() == type;
    }

    @Override
    public StaffElement createReplacement(StaffElement element) {
        var targetType = element.getType().isRest() ? type.toRest() : type.toNote();
        return StaffElement.convertedFrom(targetType, element);
    }

    private boolean isBarLineOrRepeatReplace() {
        return role == Role.NON_DURATION && type.isBar();
    }

    @Override
    public @Nullable String getUndoOpName() {
        if (!isBarLineOrRepeatReplace()) {
            return super.getUndoOpName();
        }

        var scoreView = getScoreView();

        if (scoreView == null || !scoreView.isInitialized()) {
            return super.getUndoOpName();
        }

        var selectedElements = scoreView.getSelectionCoordinator().getSelectedElements();

        if (selectedElements.isEmpty()) {
            return super.getUndoOpName();
        }

        var selectedType = selectedElements.getFirst().getType();

        if (selectedType.isRepeat()) {
            return Strings.get(Strings.ACTION_EDIT_OP_CHANGE_REPEAT);
        }

        if (selectedType.isBarLine()) {
            return Strings.get(Strings.ACTION_EDIT_OP_CHANGE_BARLINE);
        }

        return super.getUndoOpName();
    }

    /**
     * Must intercept ahead of {@link #applyToSelectionIfActive()}, whose replacement
     * {@code Line.setElement} refuses for the terminal slot.
     */
    private boolean replaceTerminalIfSelected() {
        var terminalType = terminalType();

        if (terminalType == null
                || !requireScoreView().getSelectionCoordinator().isTerminalSelected()) {
            return false;
        }

        getSong().replaceTerminal(terminalType);
        return true;
    }

    @Override
    protected void performAction(ActionEvent e) {
        if (!doActionPerformed(e)) {
            return;
        }

        if (replaceTerminalIfSelected()) {
            return;
        }

        if (applyToSelectionIfActive()) {
            return;
        }

        if (role == Role.DURATION) {
            MessageCenter.post(new DurationWasSelectedNotification(type));
        } else {
            MessageCenter.post(new BarWasSelectedNotification(type));
        }
    }
}
