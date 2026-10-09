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
import songscribe.message.Message;
import songscribe.message.MessageSubscription;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DurationWasSelectedNotification;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
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
 * Base class for all application actions. {@link #actionPerformed(ActionEvent)} is a
 * {@code final} template: subclasses override the {@link #performAction(ActionEvent)}
 * hook instead, and the template sets the Tier-A undo op-name on {@code UndoController}
 * around the hook so that whatever outermost modification bracket opens
 * <em>synchronously</em> during dispatch captures that name.
 *
 * <p>The template sets the pending op-name from {@link #getUndoOpName()}, calls
 * {@link #performAction(ActionEvent)}, and restores the prior pending name in a {@code finally}.
 * Any modification bracket the hook opens within that dispatch window captures the pending name as
 * it goes from depth 0 to depth 1, and posts it with the {@code SongDidChangeNotification} when the
 * bracket closes.
 *
 * <p>Capture works only for brackets opened on the synchronous dispatch stack (modal
 * dialogs block, so they qualify). A bracket opened off-stack ({@code invokeLater},
 * {@code Timer}, {@code SwingWorker}, or a non-modal dialog) opens after the
 * {@code finally} has cleared {@code pendingOpName}; such sites declare their name via
 * the labeled {@code withModification(String, Runnable)} overload (Tier B) instead.
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
         * it is disabled for as long as grace mode is in progress: between entering the mode and
         * pairing, the grace note sits on its line outside mutation tracking while the manager
         * holds its index, the host slot and the notes its chain walk restamped, so any action
         * that inserts, removes or replaces an element, replays undo, or replaces or serializes
         * the document invalidates what the manager holds. An action declares itself safe by
         * carrying this flag, and states beside it why it is.
         */
        ENABLED_IN_GRACE_MODE(1 << 12),
        DISABLE_IN_SELECT_MODE(1 << 13),
        OPENS_DIALOG(1 << 14),
        DISABLE_WHEN_MIDI_UNAVAILABLE(1 << 15),
        DISABLE_WHEN_LINE_SELECTED(1 << 16),
        DISABLE_WHEN_GRACE_DURATION_SELECTED(1 << 17),
        ENABLE_WHEN_LINE_SELECTED(1 << 18),
        REQUIRES_ANNOTATABLE_ELEMENT(1 << 19);

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

    /**
     * An action reflecting or editing one attribute of an element of type {@code E}. For
     * example, an accidental action's {@code E} is {@link songscribe.dom.NoteElement}; a
     * barline action's is {@link StaffElement} itself.
     */
    public interface Reflectable<E extends StaffElement> extends Selectable {
        /**
         * Narrows the given element to the type this action's attribute lives on, or returns
         * {@code null} when this action does not act on it at all — for example, an accidental
         * action admits no rest, and a barline action admits no note.
         *
         * @param element the element to test
         * @return the same element, narrowed, or {@code null} if this action does not apply to it
         */
        @Nullable E admit(StaffElement element);

        /**
         * Whether the given element has the attribute this action represents.
         *
         * @param element an element {@link #admit} returned
         * @return {@code true} when the element carries the attribute, so the action shows as
         *     selected for it
         */
        boolean matchesElement(E element);

        /**
         * Whether toggling this action on the element at {@code index} is allowed now:
         * selecting it where {@link #matchesElement} is {@code false}, deselecting it where it
         * is {@code true}. An action whose attribute can always be applied and removed inherits
         * the default; one whose toggle depends on the element's surroundings — an accidental,
         * which the chain at its staff position decides — overrides it.
         *
         * @param line  the line holding the element
         * @param index the element's index in {@code line}; the element there is one
         *              {@link #admit} returned
         * @return {@code true} when the toggle is allowed, which is what enables the action's
         *     control for that element; {@code true} by default
         */
        default boolean canToggle(Line line, int index) {
            return true;
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

    /** Whether an action's attribute is put on the elements it acts on or taken off them. */
    public enum Toggle {
        APPLY,
        REMOVE;

        /**
         * @param selected an action's selected state
         * @return {@link #APPLY} for a selected action, {@link #REMOVE} for one that is not
         */
        public static Toggle of(boolean selected) {
            return selected ? APPLY : REMOVE;
        }
    }

    /**
     * A reflectable action that can be applied across a selection. It decides what it does to
     * each element the selection holds, and hands the decision to {@link SelectionActionApplier}
     * as a plan to gate and run.
     */
    public interface SelectionApplicable<E extends StaffElement> extends Reflectable<E> {
        /**
         * Decides what this action does to {@code selection}. Mutates nothing: every change the
         * plan holds is measured on a detached stand-in, and the line changes only when the
         * plan's mutation runs.
         *
         * @param selection the elements to act on
         * @param toggle    whether to put this action's attribute on or take it off
         * @return the plan, or {@code null} when the action changes no element of
         *     {@code selection}
         */
        SelectionActionApplier.@Nullable Plan plan(ElementSelection selection, Toggle toggle);

        /**
         * Whether this action's change can make the column it touches wider, so applying it to
         * a selection must be refused when the line no longer fits.
         *
         * @return {@code false}; a {@link WidensColumn} answers {@code true}
         */
        default boolean widensColumn() {
            return false;
        }
    }

    /**
     * A reflectable action that modifies an element's attributes in place.
     */
    public interface ElementModifiable<E extends StaffElement> extends SelectionApplicable<E> {
        /**
         * Apply or remove this action's attribute on the given element. {@code line} and
         * {@code index} locate the element the call acts on or stands in for.
         *
         * @param element the element to modify, an element {@link #admit} returned
         * @param toggle  whether to put the attribute on or take it off
         */
        void applyToElement(Line line, int index, E element, Toggle toggle);

        /**
         * The set of {@link ElementField} tags identifying which element fields
         * this action mutates. Used to populate the {@code fields} set of the
         * emitted {@code ElementModification} mutation so subscribers can filter
         * to specific field changes without inspecting the before/after element.
         */
        EnumSet<ElementField> modifiedFields();

        /**
         * Plans one {@link Line#modifyElement} for each admitted element. Each element's
         * stand-in is a detached clone with the modification applied.
         */
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

        /**
         * Lands each planned change on the live element at its index, as one
         * {@link Line#modifyElement} apiece.
         */
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
     * A reflectable action whose change can make the column it touches wider — an accidental
     * glyph appearing to the left of the notehead, a dot to its right, a different notehead
     * altogether. Applying one to a selection must be refused when the line no longer fits, so
     * {@link SelectionActionApplier} measures the projected line before committing.
     *
     * <p>Actions that stack <em>vertically</em> above or below the note — a fermata, a dynamic
     * marking, an articulation — do not implement this: they cannot widen anything, and gating
     * them would refuse edits that always fit.
     *
     * <p>Declared here rather than tested for by listing concrete action classes at the call
     * site, so a new width-changing action states that fact where it is defined instead of
     * silently skipping the check.
     */
    public interface WidensColumn<E extends StaffElement> extends SelectionApplicable<E> {
        @Override
        default boolean widensColumn() {
            return true;
        }
    }

    /**
     * A reflectable action that replaces an element with a new instance
     * (e.g. changing duration requires a new StaffElement object).
     *
     * <p>Always widens: a replacement can be any element type, so its extent is unrelated to the
     * one it displaces.
     */
    public interface ElementReplaceable<E extends StaffElement> extends WidensColumn<E> {
        /**
         * Create a replacement element with this action's attribute applied.
         *
         * @param element the source element to base the replacement on, an element
         *                {@link #admit} returned
         * @return the replacement element
         */
        StaffElement createReplacement(E element);

        /**
         * Plans one {@link Line#replace} of a replacement over every admitted element. The
         * stand-in for each element is the new element of its pair. A replacement only ever
         * puts this action's attribute on, so {@link Toggle#REMOVE} plans nothing.
         */
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

    /**
     * Visits each element of {@code selection} that {@code action} admits, with its index, in
     * index order.
     */
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
     * The {@link Strings} key naming this action's Tier-A undo op-name, or {@code null}
     * if the action declares no static name. Assigned by the action's factory/constructor
     * (see {@code Actions.initialize}).
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
     * Releases this action's message-bus subscription.
     *
     * <p>Called by the owner that retires the action while the process continues. The action
     * constants have no such owner and are never disposed; the open-recent menu's actions are
     * rebuilt with the menu, and the menu controller disposes the outgoing set.
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
            enableFromInsertionPointMode() &&
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
     * A whole-line selection spans no element range, so its selection size is 0 and every
     * size requirement below rejects it. Actions carrying
     * {@link Flag#ENABLE_WHEN_LINE_SELECTED} treat it as a selection anyway.
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
     * Blanket disable while the user is picking an insertion point on a line — a paste
     * placement, a key-change placement, or any other client of
     * {@link InsertionPointMode}. Called first in {@link #updateEnabledState()}'s predicate
     * chain, so no per-action opt-in flag is involved: every action, the placing operation's
     * own shortcut included, is off until the placement is made or abandoned.
     *
     * @return {@code false} while a placement is pending, {@code true} otherwise
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
     * A whole-line selection reports itself as a selection of every element on the line,
     * so element-level actions would otherwise appear applicable. Actions carrying this
     * flag opt out of that.
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

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        updateEnabledState();
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void documentDidLoad(DocumentDidLoadNotification message) {
        updateEnabledState();
    }

    /**
     * If a selection is active and this action is {@link SelectionApplicable}, apply the action
     * to all applicable elements in the selection: put its attribute on when the action is
     * selected, take it off when it is not.
     * @return true if the action was applied to the selection (caller should skip normal flow)
     */
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
