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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

import net.engio.mbassy.listener.Handler;

import songscribe.message.CompositionChangedMessage;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.music.Composition;
import songscribe.music.StaffElement;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.message.BarSelectedMessage;
import songscribe.ui.message.DurationSelectedMessage;
import songscribe.ui.message.GraceModeStateChangedMessage;
import songscribe.ui.message.ModeChangedMessage;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.message.RestModeChangedMessage;
import songscribe.ui.message.TextEditingChangedMessage;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlaybackStateChangedMessage;
import songscribe.util.GraphicUtils;
import songscribe.util.Log;
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
        DISABLE_WHEN_COMPOSITION_EMPTY(1 << 12),
        DISABLE_IN_GRACE_MODE(1 << 13),
        DISABLE_IN_SELECT_MODE(1 << 14);

        private final int value;

        Flag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Marks an action that has a toggle selected state (e.g. accidentals, note durations).
     * Actions that do not implement this interface have no selected state.
     */
    public interface Selectable {
        boolean isSelected();

        void setSelected(boolean selected);

        /**
         * Toggles the selected state when the action is invoked via a keyboard
         * shortcut (source is JRootPane). Swing buttons handle toggling
         * themselves, but keyboard shortcuts need explicit toggling.
         */
        default void toggleOnKeyboardShortcut(ActionEvent e) {
            if (e.getSource() instanceof JRootPane) {
                setSelected(!isSelected());
            }
        }
    }

    public interface Reflectable extends Selectable {
        /**
         * Whether this action's attribute is applicable to the given element.
         * For example, accidental actions return false for rests;
         * barline actions return false for notes.
         */
        boolean appliesTo(StaffElement element);

        /**
         * Whether the given element has the attribute this action represents.
         * Only called when appliesTo() returns true.
         */
        boolean matchesElement(StaffElement element);
    }

    /**
     * A reflectable action that modifies an element's attributes in place.
     */
    public interface ElementModifiable extends Reflectable {
        /**
         * Apply or remove this action's attribute on the given element.
         * @param element  the element to modify
         * @param selected true to apply the attribute, false to remove it
         */
        void applyToElement(StaffElement element, boolean selected);
    }

    /**
     * A reflectable action that replaces an element with a new instance
     * (e.g. changing duration requires a new StaffElement object).
     */
    public interface ElementReplaceable extends Reflectable {
        /**
         * Create a replacement element with this action's attribute applied.
         * @param element  the source element to base the replacement on
         * @param selected true to apply the attribute, false to skip
         * @return the replacement element, or null if no replacement is needed
         */
        @Nullable StaffElement createReplacement(StaffElement element, boolean selected);
    }

    public static final String FONT_ICON_KEY = "font-icon";
    public static final String FONT_KEY = "font";

    private final MainFrame mainFrame;

    private int flags = 0;

    public UIAction(String name, String actionCommand) {
        this(name, null, 0, actionCommand, null, 0, 0);
    }

    public UIAction(
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers
    ) {
        this(name, null, 0, actionCommand, null, virtualKey, modifiers);
    }

    public UIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        this(name, icon, size, actionCommand, tooltip, 0, 0);
    }

    public UIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(name);
        mainFrame = MainFrame.getInstance();
        putValue(ACTION_COMMAND_KEY, actionCommand);
        putValue(SHORT_DESCRIPTION, tooltip);
        setIcon(icon, size);

        if (virtualKey != 0) {
            putValue(
                ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(virtualKey, modifiers)
            );

            UIUtils.addAction(this);
        }

        MessageCenter.subscribe(this);
    }

    protected MainFrame getMainFrame() {
        return mainFrame;
    }

    protected Score getScore() {
        return mainFrame.getScore();
    }

    protected Composition getComposition() {
        return mainFrame.getScore().getComposition();
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
                Log.warning("Icon not found: " + icon);
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
        // Subclasses override this. Selectable actions should call
        // toggleOnKeyboardShortcut(e) for keyboard shortcut support.
    }

    public boolean updateEnabledState() {
        // If an action is going to be enabled based on a single flag,
        // we have to check the entire context to see if the action can in fact be enabled.
        var score = getScore();
        var activeSelection = hasActiveSelection();
        var enable =
            enableInAdjustmentMode(score) &&
                enableInSelectMode(score) &&
                enableFromTextEditingState() &&
                enableFromPlaybackState() &&
                enableFromGraceModeState() &&
                enableInRestMode() &&
                enableFromSelectionSize(score) &&
                enableFromBarSelection(activeSelection) &&
                enableFromSelection(activeSelection, score) &&
                enableFromDurationSelection(activeSelection) &&
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

    protected boolean enableInSelectMode(Score score) {
        return (
            !hasFlag(Flag.DISABLE_IN_SELECT_MODE) ||
                !score.getSelectionCoordinator().isInSelectMode()
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void musicSelectionDidChange(
        @NotNull MusicSelectionChangedMessage message
    ) {
        updateEnabledState();
    }

    protected boolean enableFromSelectionSize(@NotNull Score score) {
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
    public void graceModeStateDidChange(GraceModeStateChangedMessage message) {
        updateEnabledState();
    }

    protected boolean enableFromGraceModeState() {
        return !hasFlag(Flag.DISABLE_IN_GRACE_MODE) || !GraceModeManager.isActive();
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

    protected boolean enableFromBarSelection(boolean activeSelection) {
        if (activeSelection) {
            return true;
        }

        return (
            !hasFlag(Flag.DISABLE_WHEN_BAR_SELECTED) ||
                !Actions.NON_DURATION_ACTION_GROUP.anySelected()
        );
    }

    protected boolean enableFromSelection(boolean activeSelection, Score score) {
        if (!activeSelection) {
            return true;
        }

        var coordinator = score.getSelectionCoordinator();

        if (this instanceof Reflectable reflectable) {
            return coordinator.isApplicableToSelection(reflectable);
        }

        if (hasFlag(Flag.DISABLE_WHEN_BAR_SELECTED)) {
            return coordinator.selectionHasDurations();
        }

        return true;
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void durationWasSelected(DurationSelectedMessage message) {
        updateEnabledState();
    }

    @SuppressWarnings("ObjectEquality")
    protected boolean enableFromDurationSelection(boolean activeSelection) {
        if (activeSelection) {
            return true;
        }

        if (!hasFlag(Flag.ENABLE_WHEN_DURATION_SELECTED)) {
            return true;
        }

        var duration = Actions.DURATION_ACTION_GROUP.getSelected();
        return (
            (duration != Actions.GRACE_EIGHTH_NOTE_ACTION) &&
                (duration != Actions.GLISSANDO_ACTION) &&
                (duration != Actions.SLIDE_OUT_ACTION)
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void compositionDidChange(CompositionChangedMessage message) {
        if (message.getChangeTypes().stream().anyMatch(getRelevantChangeTypes()::contains)) {
            updateEnabledState();
        }
    }

    /**
     * Returns the set of CompositionChangedMessage.ChangeType values that are relevant
     * to this action. Subclasses can override to narrow the filter.
     */
    protected EnumSet<CompositionChangedMessage.ChangeType> getRelevantChangeTypes() {
        return EnumSet.of(
            CompositionChangedMessage.ChangeType.CONTENT,
            CompositionChangedMessage.ChangeType.STRUCTURE,
            CompositionChangedMessage.ChangeType.FULL
        );
    }

    /**
     * If a selection is active and this action is Reflectable, apply the action
     * to all applicable notes in the selection.
     * @return true if the action was applied to the selection (caller should skip normal flow)
     */
    protected boolean applyToSelectionIfActive() {
        if (!(this instanceof Reflectable reflectable)) {
            return false;
        }

        var score = getScore();
        var coordinator = score.getSelectionCoordinator();
        var selection = coordinator.getSelection();

        if (selection == null) {
            return false;
        }

        coordinator.applyActionToSelection(reflectable, reflectable.isSelected());
        return true;
    }

    private boolean hasActiveSelection() {
        return getScore()
            .getSelectionCoordinator()
            .hasActiveSelection();
    }

    protected boolean enableFromCompositionState() {
        if (!hasFlag(Flag.DISABLE_WHEN_COMPOSITION_EMPTY)) {
            return true;
        }

        var composition = getComposition();
        return composition != null && !composition.isEmpty();
    }
}
