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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeMap;
import java.util.function.ObjIntConsumer;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.ElementChange;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.lifecycle.Disposable;
import songscribe.lint.LintRule;
import songscribe.message.Message;
import songscribe.message.MessageSubscription;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.DurationWasSelectedNotification;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.message.notification.LintingNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.dialog.BaseDialog;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionActionApplier;
import songscribe.undo.UndoController;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * Base class for all application actions. Subclasses override
 * {@link #performAction(ActionEvent)}, never {@link #actionPerformed(ActionEvent)}.
 *
 * <p>The undo op-name is captured only by a modification bracket opened synchronously during
 * dispatch (a modal dialog qualifies). A bracket opened off-stack ({@code invokeLater},
 * {@code Timer}, {@code SwingWorker}, a non-modal dialog) declares its name with the labeled
 * {@code withModification(String, Runnable)} overload instead.
 */
public class UIAction extends AbstractAction implements Disposable {

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
        DISABLE_WHEN_BAR_SELECTED(1 << 9),
        ENABLE_WHEN_DURATION_SELECTED(1 << 10),
        DISABLE_WHEN_SONG_EMPTY(1 << 11),
        /**
         * Keeps the action enabled while {@link GraceModeManager#isActive()}. An action lacking
         * it is disabled during grace mode, because one that inserts, removes or replaces an
         * element, replays undo, or replaces or serializes the document invalidates what the
         * manager holds. An action carrying it states beside it why it is safe.
         */
        ENABLED_IN_GRACE_MODE(1 << 12),
        DISABLE_IN_SELECT_MODE(1 << 13),
        OPENS_DIALOG(1 << 14),
        DISABLE_WHEN_MIDI_UNAVAILABLE(1 << 15),
        DISABLE_WHEN_LINE_SELECTED(1 << 16),
        DISABLE_WHEN_GRACE_DURATION_SELECTED(1 << 17),
        ENABLE_WHEN_LINE_SELECTED(1 << 18),
        REQUIRES_ANNOTATABLE_ELEMENT(1 << 19),
        DISABLE_WHEN_PLAYBACK_BLOCKED(1 << 20);

        private final int value;

        Flag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public interface Selectable {
        boolean isSelected();

        void setSelected(boolean selected);

        /**
         * Swing buttons toggle themselves; keyboard shortcuts do not.
         */
        default void toggleOnKeyboardShortcut(ActionEvent e) {
            if (e.getSource() instanceof JRootPane) {
                setSelected(!isSelected());
            }
        }
    }

    public interface Reflectable<E extends StaffElement> extends Selectable {
        /**
         * Returns {@code null} when this action does not act on the element at all.
         */
        @Nullable E admit(StaffElement element);

        /**
         * @param element an element {@link #admit} returned
         */
        boolean matchesElement(E element);

        /**
         * @param index the element's index in {@code line}; the element there is one
         *              {@link #admit} returned
         */
        default boolean canToggle(Line line, int index) {
            return true;
        }
    }

    @FunctionalInterface
    public interface AppMenuAction {
        /**
         * A title prefix, matched with {@code startsWith}: "About" matches "About SongScribe".
         */
        String getNativeMenuTitle();
    }

    public enum Toggle {
        APPLY,
        REMOVE;

        public static Toggle of(boolean selected) {
            return selected ? APPLY : REMOVE;
        }
    }

    public interface SelectionApplicable<E extends StaffElement> extends Reflectable<E> {
        /**
         * Mutates nothing: the line changes only when the plan's mutation runs.
         *
         * @return the plan, or {@code null} when the action changes no element of
         *     {@code selection}
         */
        SelectionActionApplier.@Nullable Plan plan(ElementSelection selection, Toggle toggle);

        default boolean widensColumn() {
            return false;
        }
    }

    public interface ElementModifiable<E extends StaffElement> extends SelectionApplicable<E> {
        /**
         * {@code line} and {@code index} locate the element the call acts on or stands in for.
         *
         * @param element an element {@link #admit} returned
         */
        void applyToElement(Line line, int index, E element, Toggle toggle);

        EnumSet<ElementField> modifiedFields();

        @Override
        default SelectionActionApplier.@Nullable Plan plan(ElementSelection selection, Toggle toggle) {
            var line = selection.line();
            var changes = new ArrayList<SelectionActionApplier.PendingChange>();

            forEachAdmitted(this, selection, (admitted, index) -> {
                // Safe on a detached clone: applyToElement takes the element as a parameter and
                // touches nothing else. admit() narrows it back to E: a clone shares its
                // source's class, so it is admitted by the same rule the source was.
                var standIn = admitted.clone();
                var admittedStandIn = admit(standIn);

                if (admittedStandIn != null) {
                    applyToElement(line, index, admittedStandIn, toggle);
                    changes.add(new SelectionActionApplier.PendingChange(index, standIn));
                }
            });

            if (changes.isEmpty()) {
                return null;
            }

            var refusal = (toggle == Toggle.APPLY)
                ? SelectionActionApplier.Refusal.INSERTION
                : SelectionActionApplier.Refusal.REMOVAL;

            return new SelectionActionApplier.Plan(
                changes, () -> modifyAll(line, changes, toggle), widensColumn(), refusal);
        }

        private void modifyAll(
            Line line, List<SelectionActionApplier.PendingChange> changes, Toggle toggle) {

            for (var change : changes) {
                var index = change.index();

                line.modifyElement(
                    index,
                    modifiedFields(),
                    () -> {
                        var admitted = admit(line.getElement(index));

                        if (admitted != null) {
                            applyToElement(line, index, admitted, toggle);
                        }
                    }
                );
            }
        }
    }

    /**
     * A reflectable action whose change can make the column it touches wider, so applying it to a
     * selection is refused when the line no longer fits. An action that only stacks vertically
     * above or below the note does not implement this.
     */
    public interface WidensColumn<E extends StaffElement> extends SelectionApplicable<E> {
        @Override
        default boolean widensColumn() {
            return true;
        }
    }

    /**
     * Always widens: a replacement can be any element type, so its extent is unrelated to the
     * one it displaces.
     */
    public interface ElementReplaceable<E extends StaffElement> extends WidensColumn<E> {
        /**
         * @param element an element {@link #admit} returned
         */
        StaffElement createReplacement(E element);

        @Override
        default SelectionActionApplier.@Nullable Plan plan(ElementSelection selection, Toggle toggle) {
            if (toggle == Toggle.REMOVE) {
                return null;
            }

            var newElements = new TreeMap<Integer, StaffElement>();

            forEachAdmitted(
                this, selection, (admitted, index) -> newElements.put(index, createReplacement(admitted)));

            if (newElements.isEmpty()) {
                return null;
            }

            var line = selection.line();
            var replacement = new ElementChange.Replacement(line, newElements);
            var changes = newElements.entrySet().stream()
                .map(pair -> new SelectionActionApplier.PendingChange(pair.getKey(), pair.getValue()))
                .toList();

            return new SelectionActionApplier.Plan(
                changes,
                () -> line.replace(replacement),
                widensColumn(),
                SelectionActionApplier.Refusal.INSERTION);
        }
    }

    private static <E extends StaffElement> void forEachAdmitted(
        Reflectable<E> action, ElementSelection selection, ObjIntConsumer<E> visit) {

        var line = selection.line();
        var bounds = selection.range();

        for (var i = bounds.begin(); i <= bounds.end(); i++) {
            var admitted = action.admit(line.getElement(i));

            if (admitted != null) {
                visit.accept(admitted, i);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(UIAction.class);

    public static final String FONT_ICON_KEY = "font-icon";
    public static final String FONT_KEY = "font";

    private final MainFrame mainFrame;

    private int flags = 0;

    /**
     * {@code null} if the action declares no static name.
     */
    private @Nullable String undoOpNameKey;

    private final MessageSubscription subscription;

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

        setFlags(flags);
        subscription = new MessageSubscription(this);
    }

    /**
     * Called by the owner that retires the action while the process continues. The action
     * constants have no such owner and are never disposed.
     */
    @Override
    public void dispose() {
        subscription.dispose();
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
        return mainFrame.getScoreViewController();
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
            var svgIcon = GraphicUtils.getScaledThemedSVGIcon(icon, size);
            putValue(LARGE_ICON_KEY, svgIcon);
        } else {
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

    protected void setUndoOpNameKey(@Nullable String undoOpNameKey) {
        this.undoOpNameKey = undoOpNameKey;
    }

    /**
     * Returns {@code null} if none is declared.
     */
    public @Nullable String getUndoOpName() {
        return undoOpNameKey == null ? null : Strings.get(undoOpNameKey);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var scoreView = getScoreView();

        // When no initialized song exists no modification bracket can open, so there
        // is nothing to capture; skip straight to the hook (and avoid getSong() throwing).
        if (scoreView == null || !scoreView.isInitialized()) {
            performAction(e);
            return;
        }

        UndoController.withPendingOpName(getUndoOpName(), () -> performAction(e));
    }

    /**
     * Selectable actions should call {@code toggleOnKeyboardShortcut(e)} for keyboard
     * shortcut support.
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
            enableFromInsertionPointMode() &&
                enableInSelectMode(scoreView) &&
                enableFromTextEditingState() &&
                enableFromPlaybackState() &&
                enableFromPlaybackBlock() &&
                enableFromMidiState() &&
                enableFromDialogVisibility() &&
                enableFromGraceModeState() &&
                enableInRestMode() &&
                enableFromSelectionSize(scoreView) &&
                enableFromBarSelection(activeSelection) &&
                enableFromSelection(activeSelection, scoreView) &&
                enableFromDurationSelection(activeSelection) &&
                enableFromLineSelection(scoreView) &&
                enableFromSongState();
        setEnabled(enable);
        return enable;
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void modeDidChange(ModeDidChangeNotification message) {
        updateEnabledState();
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

    /**
     * A whole-line selection has size 0; {@link Flag#ENABLE_WHEN_LINE_SELECTED} actions treat it
     * as a selection anyway.
     */
    protected boolean enableFromSelectionSize(ScoreView score) {
        if (hasFlag(Flag.ENABLE_WHEN_LINE_SELECTED)
                && score.getSelectionCoordinator().hasLineSelection()) {
            return true;
        }

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

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void prefsDidChange(PrefsDidChangeNotification message) {
        var key = message.getKey();

        if (hasFlag(Flag.DISABLE_WHEN_PLAYBACK_BLOCKED)
                && (key == PrefsKey.PLAY_WITH_REPEATS || key == PrefsKey.ALL)) {
            updateEnabledState();
        }
    }

    /**
     * Reads the preference, not the playback controller, so the answer does not depend on which
     * handler of the preference change runs first.
     */
    protected boolean enableFromPlaybackBlock() {
        return !hasFlag(Flag.DISABLE_WHEN_PLAYBACK_BLOCKED)
            || PlaybackController.isPlaying()
            || !Prefs.getBoolean(PrefsKey.PLAY_WITH_REPEATS)
            || !hasPlaybackBlockingFinding();
    }

    private boolean hasPlaybackBlockingFinding() {
        var score = getScoreView();

        return score != null
            && score.isInitialized()
            && score.getSong().getLinter().hasFlag(LintRule.Flag.BLOCKS_PLAYBACK);
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
        return !hasFlag(Flag.OPENS_DIALOG) || !BaseDialog.isAnyDialogVisible();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void graceModeStateDidChange(GraceModeStateDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableFromGraceModeState() {
        return hasFlag(Flag.ENABLED_IN_GRACE_MODE) || !GraceModeManager.isActive();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void insertionPointModeDidChange(InsertionPointModeDidChangeNotification message) {
        updateEnabledState();
    }

    /**
     * Every action, the placing operation's own shortcut included, is off until the placement
     * is made or abandoned; no per-action flag opts out.
     */
    protected boolean enableFromInsertionPointMode() {
        return !InsertionPointMode.isActive();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void textEditingDidChange(TextEditingDidChangeNotification message) {
        updateEnabledState();
    }

    protected boolean enableFromTextEditingState() {
        return (
            !hasFlag(Flag.DISABLE_WHEN_EDITING_TEXT) || !UIUtils.isEditingTextIn(mainFrame)
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

        if (this instanceof Reflectable<?> reflectable) {
            return coordinator.isApplicableToSelection(reflectable);
        }

        if (hasFlag(Flag.DISABLE_WHEN_BAR_SELECTED)) {
            return coordinator.selectionHasDurations();
        }

        //noinspection SimplifiableIfStatement
        if (hasFlag(Flag.REQUIRES_ANNOTATABLE_ELEMENT)) {
            return coordinator.selectionCanCarryAnnotation();
        }

        return true;
    }

    /**
     * A whole-line selection reports every element on the line as selected; actions carrying
     * the flag opt out of that.
     */
    protected boolean enableFromLineSelection(ScoreView scoreView) {
        return (
            !hasFlag(Flag.DISABLE_WHEN_LINE_SELECTED) ||
                !scoreView.getSelectionCoordinator().hasLineSelection()
        );
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

        // A grace note is always a single undotted pitched note, so rests and dots cannot
        // apply to it. Accidentals and articulations can, and stay enabled so the grace
        // note can be decorated before it is placed.
        return !(hasFlag(Flag.DISABLE_WHEN_GRACE_DURATION_SELECTED)
            && (duration == Actions.GRACE_EIGHTH_NOTE_ACTION));
    }

    /**
     * The notification's construction has already linted the song.
     */
    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void linting(LintingNotification message) {
        updateEnabledState();
    }

    protected boolean applyToSelectionIfActive() {
        if (!(this instanceof SelectionApplicable<?> applicable)) {
            return false;
        }

        var score = requireScoreView();
        var selection = score.getSelectionCoordinator().getSelection();

        if (selection == null) {
            return false;
        }

        SelectionActionApplier.apply(score, selection, applicable, Toggle.of(applicable.isSelected()));
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
