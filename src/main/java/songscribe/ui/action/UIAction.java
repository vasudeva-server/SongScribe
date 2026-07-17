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

import java.util.Arrays;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.DurationWasSelectedNotification;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.message.mutation.ElementField;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.dialog.BaseDialog;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.undo.UndoController;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * Base class for all application actions. {@link #actionPerformed(ActionEvent)} is a
 * {@code final} template: subclasses override the {@link #performAction(ActionEvent)}
 * hook instead, and the template sets the Tier-A undo op-name on {@code UndoController}
 * around the hook so that whatever outermost modification bracket opens
 * <em>synchronously</em> during dispatch captures that name.
 *
 * <pre>
 * actionPerformed(e)  // final template                            ┐
 *   ├─ UndoController.setPendingOpName(getUndoOpName())            │
 *   │                                                              │ dispatch
 *   ├─ performAction(e)  // subclass hook                          │ window
 *   │     └─ …edit… → song.withModification(() -> {                │
 *   │            depth 0 → 1: capturedOpName = pending op-name     │  bracket
 *   │            applyChange(mutation, mutator)                    │  captures
 *   │        })  // bracket closes → SongDidChangeNotification     │  the name
 *   │                                                              │
 *   └─ finally: UndoController.setPendingOpName(priorOpName)  ─────┘  restore
 * </pre>
 *
 * <p>Capture works only for brackets opened on the synchronous dispatch stack (modal
 * dialogs block, so they qualify). A bracket opened off-stack ({@code invokeLater},
 * {@code Timer}, {@code SwingWorker}, or a non-modal dialog) opens after the
 * {@code finally} has cleared {@code pendingOpName}; such sites declare their name via
 * the labeled {@code withModification(String, Runnable)} overload (Tier B) instead.
 */
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
        DISABLE_WHEN_SONG_EMPTY(1 << 12),
        DISABLE_IN_GRACE_MODE(1 << 13),
        DISABLE_IN_SELECT_MODE(1 << 14),
        OPENS_DIALOG(1 << 15),
        DISABLE_WHEN_MIDI_UNAVAILABLE(1 << 16);

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

        /**
         * Whether this action matches the slide attached to the given element.
         * Only glissando/fall actions override this to return true.
         */
        default boolean matchesSlide(StaffElement element) {
            return false;
        }
    }

    /**
     * Marks an action whose corresponding native macOS application menu item
     * should be managed by {@code MacNativeMenuController}. The controller
     * discovers all {@code AppMenuAction} instances via
     * {@link Actions#getAppMenuActions()} and matches them against native
     * {@code NSMenuItem} titles using the prefix returned by
     * {@link #getNativeMenuTitle()}.
     */
    @FunctionalInterface
    public interface AppMenuAction {
        /**
         * Returns a title prefix used to locate this action's corresponding
         * native NSMenuItem via {@code startsWith} matching. For example,
         * "About" matches the native "About SongScribe" item.
         */
        String getNativeMenuTitle();
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

        /**
         * The set of {@link ElementField} tags identifying which element fields
         * this action mutates. Used to populate the {@code fields} set of the
         * emitted {@code ElementModification} mutation so subscribers can filter
         * to specific field changes without inspecting the before/after element.
         */
        EnumSet<ElementField> modifiedFields();
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
         * @return the replacement element
         */
        StaffElement createReplacement(StaffElement element, boolean selected);
    }

    private static final Logger LOG = LoggerFactory.getLogger(UIAction.class);

    public static final String FONT_ICON_KEY = "font-icon";
    public static final String FONT_KEY = "font";

    private final MainFrame mainFrame;

    private int flags = 0;

    /**
     * The {@link Strings} key naming this action's Tier-A undo op-name, or {@code null}
     * if the action declares no static name. Assigned by the action's factory/constructor
     * (see {@code Actions.initialize}).
     */
    private @Nullable String undoOpNameKey;

    public UIAction(MainFrame mainFrame, @Nullable String name, @Nullable String actionCommand, Flag... flags) {
        this(mainFrame, name, null, 0, actionCommand, null, 0, 0, flags);
    }

    public UIAction(
        MainFrame mainFrame,
        @Nullable String name,
        @Nullable String actionCommand,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        this(mainFrame, name, null, 0, actionCommand, null, virtualKey, modifiers, flags);
    }

    public UIAction(
        MainFrame mainFrame,
        @Nullable String name,
        @Nullable String icon,
        int size,
        @Nullable String actionCommand,
        @Nullable String tooltip,
        Flag... flags
    ) {
        this(mainFrame, name, icon, size, actionCommand, tooltip, 0, 0, flags);
    }

    public UIAction(
        MainFrame mainFrame,
        @Nullable String name,
        @Nullable String icon,
        int size,
        @Nullable String actionCommand,
        @Nullable String tooltip,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(name);
        if (mainFrame == null) throw new IllegalArgumentException("mainFrame must not be null");
        this.mainFrame = mainFrame;
        putValue(ACTION_COMMAND_KEY, actionCommand);
        putValue(SHORT_DESCRIPTION, tooltip);
        setIcon(icon, size);

        if (virtualKey != 0) {
            putValue(
                ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(virtualKey, modifiers)
            );

            UIUtils.addAction(mainFrame.getRootPane(), this);
        }

        MessageCenter.subscribe(this);
        setFlags(flags);
    }

    protected static Flag[] withFlags(Flag[] base, Flag... extra) {
        var merged = Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, merged, base.length, extra.length);
        return merged;
    }

    protected MainFrame getMainFrame() {
        return mainFrame;
    }

    protected @Nullable ScoreView getScoreView() {
        return mainFrame.getScoreView();
    }

    protected @Nullable ScoreViewController getScoreViewController() {
        var scoreView = getScoreView();
        return scoreView != null ? scoreView.getController() : null;
    }

    protected ScoreView requireScoreView() {
        return mainFrame.requireScoreView();
    }

    protected Song getSong() {
        return mainFrame.requireScoreView().getSong();
    }

    public void setFlags(Flag... flags) {
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
                hasFlag(Flag.DISABLE_WHEN_SONG_EMPTY)
        ) {
            setEnabled(false);
        }
    }

    public boolean hasFlag(Flag flag) {
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
            var svgIcon = GraphicUtils.getScaledSVGIcon(icon, size, true);
            putValue(LARGE_ICON_KEY, svgIcon);
        } else {
            // Should be a tagged Unicode icon
            var info = UIUtils.getTaggedString(icon);
            putValue(FONT_ICON_KEY, info.text());
            var font = info.font();

            if (font == null) {
                return;
            }

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

    public boolean perform(@Nullable Object source) {
        actionPerformed(
            new ActionEvent(
                (source != null) ? source : this,
                ActionEvent.ACTION_PERFORMED,
                getActionCommand()
            )
        );
        return true;
    }

    /**
     * Sets the {@link Strings} key naming this action's Tier-A undo op-name.
     * Called by the action's factory/constructor.
     */
    protected void setUndoOpNameKey(@Nullable String undoOpNameKey) {
        this.undoOpNameKey = undoOpNameKey;
    }

    /**
     * Returns this action's Tier-A undo op-name (the resolved {@link Strings} value),
     * or {@code null} if none is declared. Overridden by actions whose label depends on
     * runtime state (e.g. tuplet/barline actions).
     */
    public @Nullable String getUndoOpName() {
        return undoOpNameKey == null ? null : Strings.get(undoOpNameKey);
    }

    /**
     * The final dispatch template. Sets the Tier-A op-name on the current {@link Song}
     * around {@link #performAction(ActionEvent)} so the outermost bracket that opens
     * synchronously during the hook captures it, restoring the prior value afterwards
     * (including on exception). Subclasses override {@code performAction}, not this.
     */
    @Override
    public final void actionPerformed(ActionEvent e) {
        var scoreView = getScoreView();

        // When no initialized song exists no modification bracket can open, so there
        // is nothing to capture; skip straight to the hook (and avoid getSong() throwing).
        if (scoreView == null || !scoreView.isInitialized()) {
            performAction(e);
            return;
        }

        var priorOpName = UndoController.getPendingOpName();
        UndoController.setPendingOpName(getUndoOpName());

        try {
            performAction(e);
        } finally {
            UndoController.setPendingOpName(priorOpName);
        }
    }

    /**
     * Performs the action. Default is a no-op. Subclasses override this instead of
     * {@link #actionPerformed(ActionEvent)}. Selectable actions should call
     * {@code toggleOnKeyboardShortcut(e)} for keyboard shortcut support.
     */
    protected void performAction(ActionEvent e) {
    }

    public boolean updateEnabledState() {
        // If an action is going to be enabled based on a single flag,
        // we have to check the entire context to see if the action can in fact be enabled.
        var scoreView = getScoreView();

        if (scoreView == null) {
            setEnabled(false);
            return false;
        }

        var activeSelection = hasActiveSelection();
        var enable =
            enableInAdjustmentMode(scoreView) &&
                enableInSelectMode(scoreView) &&
                enableFromTextEditingState() &&
                enableFromPlaybackState() &&
                enableFromMidiState() &&
                enableFromDialogVisibility() &&
                enableFromGraceModeState() &&
                enableInRestMode() &&
                enableFromSelectionSize(scoreView) &&
                enableFromBarSelection(activeSelection) &&
                enableFromSelection(activeSelection, scoreView) &&
                enableFromDurationSelection(activeSelection) &&
                enableFromSongState();
        setEnabled(enable);
        return enable;
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void modeDidChange(ModeDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableInAdjustmentMode(ScoreView score) {
        return (
            !hasFlag(Flag.DISABLE_IN_ADJUSTMENT_MODE) ||
                !score.getMode().isAdjustmentMode()
        );
    }

    protected boolean enableInSelectMode(ScoreView score) {
        return (
            !hasFlag(Flag.DISABLE_IN_SELECT_MODE) ||
                !score.getSelectionCoordinator().isInSelectMode()
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        updateEnabledState();
    }

    protected boolean enableFromSelectionSize(ScoreView score) {
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
    public void restModeDidChange(RestModeDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableInRestMode() {
        //noinspection SimplifiableIfStatement
        if (!hasFlag(Flag.DISABLE_IN_REST_MODE)) {
            return true;
        }

        return !Actions.REST_ACTION.isSelected() && !requireScoreView().getSelectionCoordinator().selectionHasRests();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableFromPlaybackState() {
        return !hasFlag(Flag.DISABLE_WHEN_PLAYING) || !PlaybackController.isPlaying();
    }

    protected boolean enableFromMidiState() {
        return !hasFlag(Flag.DISABLE_WHEN_MIDI_UNAVAILABLE) || MidiController.isAvailable();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void dialogVisibilityDidChange(DialogVisibilityDidChangeNotification message) {
        if (hasFlag(Flag.OPENS_DIALOG)) {
            updateEnabledState();
        }
    }

    private boolean enableFromDialogVisibility() {
        return !hasFlag(Flag.OPENS_DIALOG) || !BaseDialog.isAnyBlockingDialogVisible();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void graceModeStateDidChange(GraceModeStateDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableFromGraceModeState() {
        var result = !hasFlag(Flag.DISABLE_IN_GRACE_MODE) || !GraceModeManager.isActive();
        return result;
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void textEditingDidChange(TextEditingDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableFromTextEditingState() {
        return (
            !hasFlag(Flag.DISABLE_WHEN_EDITING_TEXT) || !UIUtils.isEditingTextIn(getMainFrame())
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    protected void barWasSelected(BarWasSelectedNotification message) {
        updateEnabledState();
    }

    protected boolean enableFromBarSelection(boolean activeSelection) {
        //noinspection SimplifiableIfStatement
        if (activeSelection) {
            return true;
        }

        return (
            !hasFlag(Flag.DISABLE_WHEN_BAR_SELECTED) ||
                !Actions.NON_DURATION_ACTION_GROUP.anySelected()
        );
    }

    protected boolean enableFromSelection(boolean activeSelection, ScoreView scoreView) {
        if (!activeSelection) {
            return true;
        }

        var coordinator = scoreView.getSelectionCoordinator();

        if (this instanceof Reflectable reflectable) {
            return coordinator.isApplicableToSelection(reflectable);
        }

        //noinspection SimplifiableIfStatement
        if (hasFlag(Flag.DISABLE_WHEN_BAR_SELECTED)) {
            return coordinator.selectionHasDurations();
        }

        return true;
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void durationWasSelected(DurationWasSelectedNotification message) {
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
                (duration != Actions.FALL_ACTION)
        );
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        updateEnabledState();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void documentDidLoad(DocumentDidLoadNotification message) {
        updateEnabledState();
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

        var score = requireScoreView();
        var coordinator = score.getSelectionCoordinator();
        var selection = coordinator.getSelection();

        if (selection == null) {
            return false;
        }

        coordinator.applyActionToSelection(reflectable, reflectable.isSelected(), score);
        return true;
    }

    private boolean hasActiveSelection() {
        return requireScoreView()
            .getSelectionCoordinator()
            .hasActiveSelection();
    }

    protected boolean enableFromSongState() {
        if (!hasFlag(Flag.DISABLE_WHEN_SONG_EMPTY)) {
            return true;
        }

        var score = getScoreView();
        return score != null && score.isInitialized() && !score.getSong().isEmpty();
    }
}
