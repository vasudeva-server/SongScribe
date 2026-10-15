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

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElement;
import songscribe.lifecycle.Disposable;
import songscribe.message.Message;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;

/**
 * Keeps the toolbar actions in step with the score's selection.
 * <p>
 * While something is selected, the actions describe the selection rather than the user's
 * standing choices, so their states are saved as a selection becomes active and restored when
 * it goes away. Reflection runs over exactly the same set of actions as the save and restore.
 */
public final class ActionReflector implements Disposable {

    /** Read-only: nothing here changes what is selected. */
    private final SelectionCoordinator coordinator;

    @Nullable
    private List<UIAction.Reflectable<?>> reflectableActions = null;

    @Nullable
    private List<UIAction> managedActions = null;

    private final Map<UIAction, ActionState> savedActionStates = new IdentityHashMap<>();

    @Nullable
    private ElementSelection lastReflectedSelection = null;

    private record ActionState(boolean selected, boolean enabled) {}

    private final MessageSubscription subscription;

    ActionReflector(SelectionCoordinator coordinator) {
        this.coordinator = coordinator;
        subscription = new MessageSubscription(this);
    }

    /** Idempotent. */
    @Override
    public void dispose() {
        subscription.dispose();
    }

    private <T> List<T> collectActions(Class<? extends T> type, Predicate<? super UIAction> filter) {
        return Actions.allActions().stream()
            .filter(action -> filter.test(action) && type.isInstance(action))
            .<T>map(type::cast)
            .toList();
    }

    private List<UIAction.Reflectable<?>> getReflectableActions() {
        if (reflectableActions == null) {
            // A class literal names only the raw interface, so discovery cannot see each
            // implementor's type argument. Nothing below needs to: every use captures the
            // wildcard through reflectionOf, which is where the argument has to stay consistent.
            @SuppressWarnings("unchecked")
            var discovered =
                (List<UIAction.Reflectable<?>>) (List<?>) collectActions(
                    UIAction.Reflectable.class,
                    action -> true
                );

            reflectableActions = discovered;
        }

        return reflectableActions;
    }

    /**
     * Includes non-reflectable actions with DISABLE_WHEN_BAR_SELECTED, whose enabled state
     * may change due to mutual exclusivity.
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
     * The restore after a mutation such as a delete: saved enabled states are stale and
     * re-derived, saved selected states are the user's intent and are kept. Discarding them
     * would latch both duration action groups empty for the rest of the session.
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

    public void clearSavedActionStates() {
        savedActionStates.clear();
    }

    /**
     * The restore for a mode that disables every action lacking {@code flag}, such as grace mode
     * with {@link UIAction.Flag#ENABLED_IN_GRACE_MODE}; actions carrying it keep their current
     * state.
     */
    public void restoreActionStatesLackingFlag(UIAction.Flag flag) {
        if (savedActionStates.isEmpty()) {
            return;
        }

        for (var entry : savedActionStates.entrySet()) {
            var action = entry.getKey();

            if (action.hasFlag(flag)) {
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

    /** Fires at LOW_PRIORITY so it runs after all UIAction handlers. */
    @Handler
    public void musicSelectionDidChangeReflectSelection(MusicSelectionDidChangeNotification message) {
        triggerReflection();
    }

    /**
     * Called with {@link SelectionCoordinator}'s cache invalidation: after a mutation a range
     * equal to the last reflected one may no longer describe the same content.
     */
    void invalidateReflectionGuard() {
        lastReflectedSelection = null;
    }

    /**
     * An undo/redo reverts attributes but leaves the range intact, so {@link #triggerReflection()}
     * would short-circuit and leave toggle actions stuck; the guard is cleared.
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
        // getLine(), which reports no line at all for an edit spanning several — as a key
        // change's stranding sweep or the chain walk's restamps on later lines do — and would
        // leave the toolbar stuck in its pre-undo state when such an edit is undone.
        if (!message.touchesLine(selection.line())) {
            return;
        }

        // Bypass the range-equality guard: the selection range is unchanged but
        // the underlying element attributes may not be.
        lastReflectedSelection = null;
        triggerReflection();
    }

    private void triggerReflection() {
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

        if (selection == null) {
            lastReflectedSelection = null;
            return;
        }

        if (selection.equals(lastReflectedSelection)) {
            return;
        }

        lastReflectedSelection = selection;

        var line = selection.line();
        var bounds = selection.range();
        var hasGraceNote = false;

        for (var reflectable : actions) {
            var applicable = false;
            var matched = true;

            for (var i = bounds.begin(); i <= bounds.end(); i++) {
                var element = line.getElement(i);

                if (element.getType().isGraceNote()) {
                    hasGraceNote = true;
                }

                var reflection = reflectionOf(reflectable, element);

                if (reflection == Reflection.NOT_ADMITTED) {
                    continue;
                }

                applicable = true;

                if (reflection == Reflection.DIFFERS) {
                    matched = false;
                    break;
                }
            }

            reflectable.setSelected(applicable && matched);
        }

        updateGraceNoteActionEnabled(hasGraceNote);
    }

    public void reflectElement(StaffElement element) {
        for (var reflectable : getReflectableActions()) {
            reflectable.setSelected(reflectionOf(reflectable, element) == Reflection.MATCHES);
        }

        updateGraceNoteActionEnabled(element.getType().isGraceNote());
    }

    private enum Reflection {
        NOT_ADMITTED,
        MATCHES,
        DIFFERS
    }

    private static <E extends StaffElement> Reflection reflectionOf(
        UIAction.Reflectable<E> reflectable, StaffElement element) {

        var admitted = reflectable.admit(element);

        if (admitted == null) {
            return Reflection.NOT_ADMITTED;
        }

        return reflectable.matchesElement(admitted) ? Reflection.MATCHES : Reflection.DIFFERS;
    }

    /**
     * Grace notes can only be inserted, not applied to existing notes, so in select mode the
     * action is disabled regardless of {@code hasGraceNote}. Package-private so tests can
     * exercise the logic directly.
     */
    void updateGraceNoteActionEnabled(boolean hasGraceNote) {
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(!coordinator.isInSelectMode() && hasGraceNote);
    }
}
