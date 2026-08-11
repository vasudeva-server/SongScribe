/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.selection;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.dom.StaffElement;
import songscribe.lifecycle.Disposable;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;

/**
 * Keeps the toolbar actions in step with the score's selection.
 * <p>
 * A {@link UIAction} shows two things about the selection: whether it applies to what is
 * selected (enabled), and whether what is selected already carries its attribute (selected).
 * Both are derived here, and neither is a property of the selection itself — which is why none
 * of this lives on {@link SelectionCoordinator}, whose job is to know what is selected, not what
 * the toolbar should look like as a result.
 * <p>
 * <b>Reflecting and freezing are the same concern.</b> While something is selected, the actions
 * describe the selection rather than the user's standing choices — the duration button the user
 * picked before selecting, say. So the states are saved as a selection becomes active and
 * restored when it goes away, and reflection runs over exactly the same set of actions in
 * between. Splitting the two apart would leave the save/restore half owning a list of actions it
 * never reflects onto, for the sole benefit of the half that does.
 * <p>
 * The action lists are discovered once, by scanning {@link Actions} reflectively, and cached.
 */
public final class ActionReflector implements Disposable {

    /** The selection this reflects. Read-only: nothing here changes what is selected. */
    private final SelectionCoordinator coordinator;

    // Lazy-initialized list of all reflectable actions discovered from Actions.
    @Nullable
    private List<UIAction.Reflectable> reflectableActions = null;

    // Lazy-initialized list of all actions whose state is managed during selection.
    @Nullable
    private List<UIAction> managedActions = null;

    // Saved action states (selected + enabled) before a selection becomes active.
    private final Map<UIAction, ActionState> savedActionStates = new IdentityHashMap<>();

    // Last reflected selection range, used to skip redundant reflection.
    @Nullable
    private ElementSelection lastReflectedSelection = null;

    private record ActionState(boolean selected, boolean enabled) {}

    ActionReflector(SelectionCoordinator coordinator) {
        this.coordinator = coordinator;
        MessageCenter.subscribe(this);
    }

    /**
     * Removes this reflector from the message bus. Idempotent.
     *
     * <p>Called by {@link SelectionCoordinator#dispose()}, which owns this instance.
     */
    @Override
    public void dispose() {
        MessageCenter.unsubscribe(this);
    }

    // -------------------------------------------------------------------------
    // Action discovery
    // -------------------------------------------------------------------------

    /**
     * Scans all static fields in Actions for UIAction instances matching the given predicate.
     */
    private <T> List<T> collectActions(Class<? extends T> type, Predicate<? super UIAction> filter) {
        var result = new ArrayList<T>();

        for (var field : Actions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                var value = field.get(null);

                if (value instanceof UIAction action) {
                    if (filter.test(action) && type.isInstance(action)) {
                        result.add(type.cast(action));
                    }
                } else if (value instanceof UIAction[] array) {
                    for (var action : array) {
                        if (filter.test(action) && type.isInstance(action)) {
                            result.add(type.cast(action));
                        }
                    }
                } else if (value instanceof List<?> list) {
                    for (var item : list) {
                        if (item instanceof UIAction action
                            && filter.test(action) && type.isInstance(action)) {
                            result.add(type.cast(action));
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                // Non-public fields are skipped
            }
        }

        return result;
    }

    private List<UIAction.Reflectable> getReflectableActions() {
        if (reflectableActions == null) {
            reflectableActions = collectActions(
                UIAction.Reflectable.class,
                action -> true
            );
        }

        return reflectableActions;
    }

    /**
     * Lazily discovers all actions whose state needs to be saved/restored during selection.
     * This includes reflectable actions (whose selected state reflects selection content)
     * and non-reflectable actions with DISABLE_WHEN_BAR_SELECTED (whose enabled state
     * may change due to mutual exclusivity).
     */
    private List<UIAction> getManagedActions() {
        if (managedActions == null) {
            managedActions = collectActions(
                UIAction.class,
                action -> action instanceof UIAction.Reflectable
                    || action.hasFlag(UIAction.Flag.DISABLE_WHEN_BAR_SELECTED)
            );
        }

        return managedActions;
    }

    public void setManagedActions(@Nullable List<UIAction> actions) {
        managedActions = actions;
    }

    // -------------------------------------------------------------------------
    // Action state save/restore
    // -------------------------------------------------------------------------

    /**
     * Saves the current selected and enabled state of all managed actions.
     * Does nothing if states have already been saved (prevents overwriting
     * a previous save).
     */
    public void saveActionStates() {
        if (!savedActionStates.isEmpty()) {
            return;
        }

        for (var action : getManagedActions()) {
            var selected = (action instanceof UIAction.Selectable selectable)
                && selectable.isSelected();
            savedActionStates.put(action, new ActionState(selected, action.isEnabled()));
        }
    }

    /**
     * Restores all managed actions to their previously saved states and clears
     * the saved state map. Does nothing if no states have been saved.
     */
    public void restoreActionStates() {
        if (savedActionStates.isEmpty()) {
            return;
        }

        for (var entry : savedActionStates.entrySet()) {
            var action = entry.getKey();
            var state = entry.getValue();

            if (action instanceof UIAction.Selectable selectable) {
                selectable.setSelected(state.selected());
            }

            action.setEnabled(state.enabled());
        }

        savedActionStates.clear();
    }

    /**
     * Restores only the selected state of all managed actions, leaving their enabled
     * state untouched, then clears the saved state map.
     * <p>
     * This is the correct restore after a mutation such as a delete. The saved enabled
     * states are stale — the song has changed, so each action must re-derive whether it
     * applies to the new content. The saved selected states are not stale: they record
     * which duration/bar button the user had chosen before selecting, which is user
     * intent rather than a property of the song.
     * <p>
     * Discarding the selected states instead would latch both duration action groups
     * empty for the rest of the session, because selection reflection deselects every
     * duration button for a non-uniform selection and nothing else ever reselects one.
     */
    public void restoreSelectedActionStates() {
        if (savedActionStates.isEmpty()) {
            return;
        }

        for (var entry : savedActionStates.entrySet()) {
            if (entry.getKey() instanceof UIAction.Selectable selectable) {
                selectable.setSelected(entry.getValue().selected());
            }
        }

        savedActionStates.clear();
    }

    /**
     * Clears saved action states without restoring them.
     * Used when the operation that saved states completes successfully
     * and the current state should be kept.
     */
    public void clearSavedActionStates() {
        savedActionStates.clear();
    }

    /**
     * Returns whether the saved action states map is empty.
     * Package-private for tests that verify clear/restore semantics.
     */
    boolean hasSavedActionStates() {
        return !savedActionStates.isEmpty();
    }

    /**
     * Restores only the actions that have the given flag to their previously
     * saved state, then clears all saved states. Actions without the flag
     * are left at their current state.
     */
    public void restoreActionStatesWithFlag(UIAction.Flag flag) {
        if (savedActionStates.isEmpty()) {
            return;
        }

        for (var entry : savedActionStates.entrySet()) {
            var action = entry.getKey();

            if (!action.hasFlag(flag)) {
                continue;
            }

            var state = entry.getValue();

            if (action instanceof UIAction.Selectable selectable) {
                selectable.setSelected(state.selected());
            }

            action.setEnabled(state.enabled());
        }

        savedActionStates.clear();
    }

    // -------------------------------------------------------------------------
    // Selection reflection
    // -------------------------------------------------------------------------

    @Handler(priority = Message.HIGH_PRIORITY)
    public void musicSelectionDidChangeSaveRestoreActionStates(MusicSelectionDidChangeNotification message) {
        var selection = coordinator.getSelection();

        if (selection == null) {
            // A decoration selection nulls out the element selection, but it is still a
            // selection — freeze the action states rather than restoring them as if the
            // selection had been cleared.
            if (coordinator.hasDecorationSelection()) {
                saveActionStates();
            } else {
                restoreActionStates();
            }
        } else if (!selection.equals(lastReflectedSelection)) {
            saveActionStates();
        }
    }

    /**
     * Reflects the current selection onto all reflectable toolbar actions.
     * Fires at LOW_PRIORITY so it runs after all UIAction handlers have processed
     * the selection-changed message.
     */
    @Handler
    public void musicSelectionDidChangeReflectSelection(MusicSelectionDidChangeNotification message) {
        triggerReflection();
    }

    /**
     * Discards the reflection guard, so the next reflection recomputes from the elements now
     * on the line rather than short-circuiting on an unchanged range.
     * <p>
     * Called by {@link SelectionCoordinator} alongside its own cache invalidation: an index
     * names a different element once a mutation has shifted, replaced or removed what was
     * there, so a range that compares equal to the last reflected one no longer describes the
     * same content.
     */
    void invalidateReflectionGuard() {
        lastReflectedSelection = null;
    }

    /**
     * Re-reflects action state when a mutation changes the content of the
     * selected line without changing the selection range.
     * <p>
     * An undo/redo (e.g. toggling a fermata or trill on the selected note, then
     * undoing it) reverts the element's attributes but leaves the selection range
     * intact. {@link #triggerReflection()} would short-circuit on the unchanged
     * range and leave toggle actions stuck in their pre-undo checked state, so we
     * clear the guard and force a fresh reflection.
     */
    @Handler
    public void songDidChangeReflectSelection(SongDidChangeNotification message) {
        var line = coordinator.getActiveLine();

        if (line != null && message.getLine() == line
                && coordinator.hasDecorationSelection()
                && coordinator.revalidateDecorationSelection()) {
            // A decoration selection carries no ElementSelection for getSelection() to
            // compare below, so undo/redo of a mutation on this line — which can shift
            // indices or remove the selected decoration outright — would otherwise go
            // unnoticed and leave the selection pointing at the wrong (or a dead) decoration.
            // The selection was just cleared: force a fresh reflection so any decoration
            // toolbar action that was showing selected/enabled resets to its default state.
            lastReflectedSelection = null;
            triggerReflection();
        }

        var selection = coordinator.getSelection();

        if (selection == null) {
            return;
        }

        // Only the mutations targeting the selected line can change the checked
        // state of the reflectable actions. Asked per mutation rather than through
        // getLine(), which reports no line at all for an edit spanning several — as an
        // accepted restatement removal does — and would leave the toolbar stuck in its
        // pre-undo state when such an edit is undone.
        if (!message.touchesLine(selection.line())) {
            return;
        }

        // Bypass the range-equality guard: the selection range is unchanged but
        // the underlying element attributes may not be.
        lastReflectedSelection = null;
        triggerReflection();
    }

    /**
     * Reflects the current selection onto all reflectable toolbar actions.
     * Package-private so tests can trigger reflection directly without a notification.
     */
    void triggerReflection() {
        var actions = getReflectableActions();

        // A line selection selects the line as a whole, not its content, so no action
        // applies. getSelection() synthesizes a full-line span for it, which would
        // otherwise reflect as if the user had selected every element on the line.
        if (coordinator.isLineSelected()) {
            // Null so the next content selection always re-reflects, even when its span
            // happens to equal the synthesized full-line span.
            lastReflectedSelection = null;

            for (var reflectable : actions) {
                reflectable.setSelected(false);
            }

            updateGraceNoteActionEnabled(false);
            return;
        }

        var selection = coordinator.getSelection();

        // Selection cleared
        if (selection == null) {
            lastReflectedSelection = null;
            return;
        }

        // Skip if the selection range is unchanged
        if (selection.equals(lastReflectedSelection)) {
            return;
        }

        lastReflectedSelection = selection;

        // Reflect selection attributes onto toolbar actions
        var line = selection.line();
        var hasGraceNote = false;

        for (var reflectable : actions) {
            var applicable = false;
            var matched = true;

            for (var i = selection.begin(); i <= selection.end(); i++) {
                var element = line.getElement(i);

                if (element.getType().isGraceNote()) {
                    hasGraceNote = true;
                }

                if (!reflectable.appliesTo(element)) {
                    continue;
                }

                applicable = true;

                if (!reflectable.matchesElement(element)) {
                    matched = false;
                    break;
                }
            }

            reflectable.setSelected(applicable && matched);
        }

        updateGraceNoteActionEnabled(hasGraceNote);
    }

    /**
     * Reflects a single element's attributes onto all reflectable toolbar actions.
     * Used when grace note pairing is complete to mirror the host note's attributes.
     */
    public void reflectElement(StaffElement element) {
        for (var reflectable : getReflectableActions()) {
            reflectable.setSelected(
                reflectable.appliesTo(element) && reflectable.matchesElement(element)
            );
        }

        updateGraceNoteActionEnabled(element.getType().isGraceNote());
    }

    // Grace notes can only be inserted, not applied to existing notes.
    // In select mode the action is unconditionally disabled.
    // Package-private so tests for rows 93/94 can exercise the logic directly.
    void updateGraceNoteActionEnabled(boolean hasGraceNote) {
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(!coordinator.isInSelectMode() && hasGraceNote);
    }
}
