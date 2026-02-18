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

import java.awt.event.*;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.message.BarSelectedMessage;
import songscribe.ui.message.DurationSelectedMessage;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.Message;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.message.RestModeChangedMessage;
import songscribe.ui.message.TextEditingChangedMessage;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlaybackStateChangedMessage;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

public class UIAction extends AbstractAction {

    public enum Flag {
        NONE(0),
        REQUIRES_EMPTY_SELECTION(1),
        REQUIRES_SELECTION(1 << 1),
        REQUIRES_SINGLE_SELECTION(1 << 2),
        REQUIRES_OPTIONAL_SINGLE_SELECTION(1 << 3),
        REQUIRES_MULTIPLE_SELECTION(1 << 4),
        REQUIRES_OPTIONAL_MULTIPLE_SELECTION(1 << 5),
        DISABLE_IN_REST_MODE(1 << 6),
        DISABLE_WHEN_PLAYING(1 << 7),
        DISABLE_WHEN_EDITING_TEXT(1 << 8),
        DISABLE_IN_ADJUSTMENT_MODE(1 << 9),
        DISABLE_WHEN_BAR_SELECTED(1 << 10),
        ENABLE_WHEN_DURATION_SELECTED(1 << 11),
        DISABLE_WHEN_COMPOSITION_EMPTY(1 << 12);

        private final int value;

        Flag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final String FONT_ICON_KEY = "font-icon";
    public static final String FONT_KEY = "font";

    private int flags = 0;

    public UIAction(String name, String actionCommand) {
        this(name, null, 0, actionCommand, null, false, 0, 0);
    }

    public UIAction(
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers
    ) {
        this(name, null, 0, actionCommand, null, false, virtualKey, modifiers);
    }

    public UIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        this(name, icon, size, actionCommand, tooltip, false, 0, 0);
    }

    public UIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        boolean isToggle
    ) {
        this(name, icon, size, actionCommand, tooltip, isToggle, 0, 0);
    }

    public UIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        boolean isToggle,
        int virtualKey,
        int modifiers
    ) {
        super(name);
        putValue(ACTION_COMMAND_KEY, actionCommand);
        putValue(SHORT_DESCRIPTION, tooltip);
        setIcon(icon, size);

        if (isToggle) {
            setSelected(false);
        }

        if (virtualKey != 0) {
            putValue(
                ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(virtualKey, modifiers)
            );

            UIUtils.addAction(this);
        }

        MessageCenter.subscribe(this);
    }

    public void setFlags(@NotNull Flag... flags) {
        for (var flag : flags) {
            this.flags |= flag.getValue();
        }

        // We assume setFlags is called in the constructor. If any flags
        // require a selection, we assume there is no selection and disable
        // the action.
        if (
            hasFlag(Flag.REQUIRES_SELECTION) ||
            hasFlag(Flag.REQUIRES_SINGLE_SELECTION) ||
            hasFlag(Flag.REQUIRES_MULTIPLE_SELECTION) ||
            hasFlag(Flag.DISABLE_WHEN_COMPOSITION_EMPTY)
        ) {
            setEnabled(false);
        }
    }

    public boolean hasFlag(@NotNull Flag flag) {
        return (flags & flag.getValue()) != 0;
    }

    public void setIcon(@Nullable String icon, int size) {
        if (icon == null) {
            return;
        }

        /*
          icon can be:
          - A tagged Unicode character representing an icon
            in the FontUtils.ICON_FONT or FontUtils.NOTE_FONT
          - An SVG icon filename
         */
        if (icon.endsWith(".svg")) {
            var svgIcon = GraphicUtils.getScaledSVGIcon(icon, size);

            if (svgIcon != null) {
                putValue(LARGE_ICON_KEY, svgIcon);
            } else {
                System.out.println("Icon not found: " + icon);
            }
        } else {
            // Should be a tagged Unicode icon
            var info = UIUtils.getTaggedString(icon);
            putValue(FONT_ICON_KEY, info.text());
            var font = info.font();

            if (font.getSize() == size) {
                putValue(FONT_KEY, font);
            } else {
                putValue(FONT_KEY, font.deriveFont((float) size));
            }
        }
    }

    public boolean isToggle() {
        return isSelected() != null;
    }

    public String getName() {
        return (String) getValue(NAME);
    }

    public void setName(String name) {
        putValue(NAME, name);
    }

    public String getActionCommand() {
        return (String) getValue(ACTION_COMMAND_KEY);
    }

    public void setActionCommand(String actionCommand) {
        putValue(ACTION_COMMAND_KEY, actionCommand);
    }

    @Nullable
    public Icon getLargeIcon() {
        return (Icon) getValue(LARGE_ICON_KEY);
    }

    public KeyStroke getAccelerator() {
        return (KeyStroke) getValue(ACCELERATOR_KEY);
    }

    public void setSelected(boolean selected) {
        putValue(SELECTED_KEY, selected);
    }

    public Boolean isSelected() {
        return (Boolean) getValue(SELECTED_KEY);
    }

    // Set the selected state AND invoke the action
    public void select(Object source) {
        setSelected(true);
        perform(source);
    }

    public void perform(Object source) {
        actionPerformed(
            new ActionEvent(
                (source != null) ? source : this,
                ActionEvent.ACTION_PERFORMED,
                getActionCommand()
            )
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Toggle the selected state if it's a toggle action invoked by a keyboard shortcut
        if (isToggle() && (e.getSource() instanceof JRootPane)) {
            setSelected(!isSelected());
        }
        // Subclasses should override and call super.actionPerformed(e)
        // if the action is a toggle action.
    }

    protected boolean updateEnabledState() {
        // If an action is going to be enabled based on a single flag,
        // we have to check the entire context to see if the action can in fact be enabled.
        var score = MainFrame.getInstance().getScore();
        var enable =
            enableInAdjustmentMode(score) &&
            enableFromTextEditingState() &&
            enableFromPlaybackState() &&
            enableInRestMode() &&
            enableFromSelection(score) &&
            enableFromBarSelection() &&
            enableFromDurationSelection() &&
            enableFromCompositionState();
        setEnabled(enable);
        return enable;
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void modeDidChange(ModeChangedMessage message) {
        updateEnabledState();
    }

    protected boolean enableInAdjustmentMode(Score score) {
        return (
            !hasFlag(Flag.DISABLE_IN_ADJUSTMENT_MODE) ||
            !score.getMode().isAdjustmentMode()
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void musicSelectionDidChange(
        @NotNull MusicSelectionChangedMessage message
    ) {
        updateEnabledState();
    }

    protected boolean enableFromSelection(@NotNull Score score) {
        var size = score.getSelectionSize();

        if (hasFlag(Flag.REQUIRES_SELECTION)) {
            return size > 0;
        }

        if (hasFlag(Flag.REQUIRES_EMPTY_SELECTION)) {
            return size == 0;
        }

        if (hasFlag(Flag.REQUIRES_SINGLE_SELECTION)) {
            return size == 1;
        }

        if (hasFlag(Flag.REQUIRES_OPTIONAL_SINGLE_SELECTION)) {
            return size <= 1;
        }

        if (hasFlag(Flag.REQUIRES_MULTIPLE_SELECTION)) {
            return size > 1;
        }

        return (
            !hasFlag(Flag.REQUIRES_OPTIONAL_MULTIPLE_SELECTION) ||
            (size == 0) ||
            (size > 1)
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void restModeDidChange(RestModeChangedMessage message) {
        updateEnabledState();
    }

    protected boolean enableInRestMode() {
        return (
            !hasFlag(Flag.DISABLE_IN_REST_MODE) ||
            !Actions.REST_ACTION.isSelected()
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void playbackStateDidChange(PlaybackStateChangedMessage message) {
        updateEnabledState();
    }

    protected boolean enableFromPlaybackState() {
        return (
            !hasFlag(Flag.DISABLE_WHEN_PLAYING) ||
            (PlaybackController.getState() !=
                PlaybackController.PlaybackState.PLAYING)
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void textEditingDidChange(TextEditingChangedMessage message) {
        updateEnabledState();
    }

    protected boolean enableFromTextEditingState() {
        return (
            !hasFlag(Flag.DISABLE_WHEN_EDITING_TEXT) || !UIUtils.isEditingText()
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    protected void barWasSelected(BarSelectedMessage message) {
        updateEnabledState();
    }

    protected boolean enableFromBarSelection() {
        return (
            !hasFlag(Flag.DISABLE_WHEN_BAR_SELECTED) ||
            !Actions.BAR_ACTION_GROUP.anySelected()
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void durationWasSelected(DurationSelectedMessage message) {
        updateEnabledState();
    }

    @SuppressWarnings("ObjectEquality")
    protected boolean enableFromDurationSelection() {
        if (!hasFlag(Flag.ENABLE_WHEN_DURATION_SELECTED)) {
            return true;
        }

        var duration = Actions.DURATION_ACTION_GROUP.getSelected();
        return (
            (duration != Actions.GRACE_EIGHTH_NOTE_ACTION) &&
            (duration != Actions.GRACE_SIXTEENTH_NOTE_ACTION) &&
            (duration != Actions.GLISSANDO_ACTION)
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void layoutDidChange(LayoutChangeMessage message) {
        updateEnabledState();
    }

    protected boolean enableFromCompositionState() {
        if (!hasFlag(Flag.DISABLE_WHEN_COMPOSITION_EMPTY)) {
            return true;
        }

        var composition = MainFrame.getInstance().getScore().getComposition();
        return !composition.isEmpty();
    }
}
