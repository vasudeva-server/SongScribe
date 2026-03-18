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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.data.Interval;
import songscribe.data.IntervalSet;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.notification.CompositionDidChangeNotification;
import songscribe.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.util.RuntimeError;

/**
 * Lightweight score-level coordinator that tracks which line (if any) has
 * the active selection, and handles cross-line queries.
 * <p>
 * Replaces SelectionManager as the score-level selection object.
 */
public final class SelectionCoordinator {

    private final Supplier<Composition> compositionSupplier;

    /** Registry of per-line selection states, keyed by line index. */
    private final Map<Integer, LineSelectionState> lineStates = new HashMap<>();

    /** Which line currently has the active selection, or -1 if none. */
    private int activeLineIndex = -1;

    /** Whether the user is in select mode (shift held down or select mode active). */
    private boolean inSelectMode = false;

    // Lazy-initialized list of all reflectable actions discovered from Actions.
    private List<UIAction.Reflectable> reflectableActions = null;

    // Lazy-initialized list of all actions whose state is managed during selection.
    private List<UIAction> managedActions = null;

    // Saved action states (selected + enabled) before a selection becomes active.
    private final Map<UIAction, ActionState> savedActionStates = new IdentityHashMap<>();

    // Last reflected selection range, used to skip redundant reflection.
    private ElementSelection lastReflectedSelection = null;

    // Content cache: lazily computed flags about what the current selection contains.
    private ElementSelection contentCacheSelection = null;
    private boolean hasDurations;
    private boolean hasNonDurations;
    private boolean hasRests;

    // Applicability cache: maps action to whether it applies to any element in the selection.
    private ElementSelection applicabilityCacheSelection = null;
    private final Map<UIAction.Reflectable, Boolean> applicabilityCache = new IdentityHashMap<>();

    record ActionState(boolean selected, boolean enabled) {}

    public SelectionCoordinator(@NotNull Supplier<Composition> compositionSupplier) {
        this.compositionSupplier = compositionSupplier;
        MessageCenter.subscribe(this);
    }

    // -------------------------------------------------------------------------
    // Line state registry
    // -------------------------------------------------------------------------

    /**
     * Registers a LineSelectionState for the given line index.
     * Called by LineComponent when it is set up.
     */
    public void registerLineState(int lineIndex, @NotNull LineSelectionState state) {
        lineStates.put(lineIndex, state);
    }

    /**
     * Removes a LineSelectionState registration.
     */
    public void unregisterLineState(int lineIndex) {
        lineStates.remove(lineIndex);
    }

    /**
     * Clears all registered line states.
     */
    public void clearLineStates() {
        lineStates.clear();
        activeLineIndex = -1;
    }

    /**
     * Returns the LineSelectionState for the given line index, or null if not registered.
     */
    @Nullable
    public LineSelectionState getLineState(int lineIndex) {
        return lineStates.get(lineIndex);
    }

    // -------------------------------------------------------------------------
    // Active line management
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the line that currently has the active selection,
     * or -1 if no line has an active selection.
     */
    public int getActiveLineIndex() {
        return activeLineIndex;
    }

    /**
     * Returns the active LineSelectionState, or null if no line is active.
     */
    @Nullable
    public LineSelectionState getActiveSelection() {
        if (activeLineIndex == -1) {
            return null;
        }

        return lineStates.get(activeLineIndex);
    }

    /**
     * Activates the given line for selection. Clears the previous line's selection.
     */
    public void activateLine(int lineIndex) {
        if (activeLineIndex != -1 && activeLineIndex != lineIndex) {
            var previousState = lineStates.get(activeLineIndex);

            if (previousState != null) {
                previousState.clearSelection();
            }
        }

        activeLineIndex = lineIndex;
    }

    /**
     * Clears the active line's selection and resets activeLineIndex to -1.
     */
    public void clearSelection() {
        if (activeLineIndex != -1) {
            var state = lineStates.get(activeLineIndex);

            if (state != null) {
                state.clearSelection();
            }
        }

        activeLineIndex = -1;
    }

    // -------------------------------------------------------------------------
    // Select mode
    // -------------------------------------------------------------------------

    public boolean isInSelectMode() {
        return inSelectMode;
    }

    public void setInSelectMode(boolean inSelectMode) {
        this.inSelectMode = inSelectMode;
    }

    // -------------------------------------------------------------------------
    // Cross-line queries (needed for rendering and Score API)
    // -------------------------------------------------------------------------

    /**
     * Returns whether the element at the given index on the given line is selected.
     * Delegates to the correct LineSelectionState.
     */
    public boolean isElementSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.hasElementSelection() && state.isElementSelected(elementIndex);
    }

    /**
     * Returns whether the staff line itself is selected (for deletion).
     */
    public boolean isLineSelected(int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.isLineSelected();
    }

    /**
     * Returns whether the glissando owned by the element at the given index
     * on the given line is selected.
     */
    public boolean isGlissandoSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.isGlissandoSelected(elementIndex);
    }

    /**
     * Returns whether any glissando is selected on the active line.
     */
    public boolean hasGlissandoSelection() {
        var state = getActiveSelection();
        return (state != null) && state.hasGlissandoSelection();
    }

    // -------------------------------------------------------------------------
    // Cross-line query methods
    // -------------------------------------------------------------------------

    /**
     * Returns whether a line can be deleted (a line is selected and there's more than one line).
     */
    public boolean canDeleteLine() {
        if (activeLineIndex == -1) {
            return false;
        }

        var state = lineStates.get(activeLineIndex);

        if (state == null || !state.isLineSelected()) {
            return false;
        }

        return compositionSupplier.get().lineCount() > 1;
    }

    /**
     * Returns whether tempo can be changed.
     */
    public boolean canChangeTempo() {
        var state = getActiveSelection();

        if (state == null) {
            return false;
        }

        var selectedElement = state.getSingleSelectedElement();

        return selectedElement != null;
    }

    // -------------------------------------------------------------------------
    // Convenience accessors that delegate to active LineSelectionState
    // -------------------------------------------------------------------------

    /**
     * Returns the number of elements in the active selection.
     */
    public int getSelectionSize() {
        var state = getActiveSelection();
        return (state != null) ? state.getSelectionSize() : 0;
    }

    /**
     * Returns the current selection, or null if nothing is selected.
     */
    @Nullable
    public ElementSelection getSelection() {
        var state = getActiveSelection();
        return (state != null) ? state.getSelection() : null;
    }

    /**
     * Returns the single selected element if exactly one element is selected,
     * or null otherwise.
     */
    @Nullable
    public StaffElement getSingleSelectedElement() {
        var state = getActiveSelection();
        return (state != null) ? state.getSingleSelectedElement() : null;
    }

    /**
     * Returns the index of the line that has a line selection (for deletion),
     * or -1 if no line is selected.
     */
    public int getSelectedLine() {
        if (activeLineIndex != -1) {
            var state = lineStates.get(activeLineIndex);

            if (state != null && state.isLineSelected()) {
                return activeLineIndex;
            }
        }

        return -1;
    }

    /**
     * Returns the elements in the active selection, or an empty list if nothing is selected.
     */
    public List<StaffElement> getSelectedElements() {
        var selection = getSelection();

        if (selection == null) {
            return List.of();
        }

        var line = selection.line();
        var elements = new ArrayList<StaffElement>(selection.end() - selection.begin() + 1);

        for (var i = selection.begin(); i <= selection.end(); i++) {
            elements.add(line.getElement(i));
        }

        return elements;
    }

    // -------------------------------------------------------------------------
    // Selection content queries
    // -------------------------------------------------------------------------

    /**
     * Returns whether there is an active element selection.
     */
    public boolean hasActiveSelection() {
        return getSelection() != null;
    }

    /**
     * Returns whether the current selection contains any duration notes (notes or rests).
     * Returns {@code false} if there is no active selection.
     */
    public boolean selectionHasDurations() {
        if (!hasActiveSelection()) {
            return false;
        }

        ensureContentComputed();
        return hasDurations;
    }

    /**
     * Returns whether the current selection contains any rests.
     * Returns {@code false} if there is no active selection.
     */
    public boolean selectionHasRests() {
        if (!hasActiveSelection()) {
            return false;
        }

        ensureContentComputed();
        return hasRests;
    }

    /**
     * Returns whether the given reflectable action is applicable to any element
     * in the current selection. Results are cached per selection.
     * Returns {@code false} if there is no active selection.
     */
    public boolean isApplicableToSelection(@NotNull UIAction.Reflectable action) {
        var selection = getSelection();

        if (selection == null) {
            return false;
        }

        // Invalidate cache if selection changed
        if (!selection.equals(applicabilityCacheSelection)) {
            applicabilityCacheSelection = selection;
            applicabilityCache.clear();
        }

        return applicabilityCache.computeIfAbsent(action, a -> {
            var line = selection.line();

            for (var i = selection.begin(); i <= selection.end(); i++) {
                if (a.appliesTo(line.getElement(i))) {
                    return true;
                }
            }

            return false;
        });
    }

    /**
     * Computes and caches the content flags for the current selection.
     * Short-circuits when all flags are set.
     */
    private void ensureContentComputed() {
        var selection = getSelection();

        if (selection == null || selection.equals(contentCacheSelection)) {
            return;
        }

        hasDurations = false;
        hasNonDurations = false;
        hasRests = false;
        contentCacheSelection = selection;

        var line = selection.line();

        for (var i = selection.begin(); i <= selection.end(); i++) {
            var elementType = line.getElement(i).getType();

            if (elementType.isDuration()) {
                hasDurations = true;

                if (elementType.isRest()) {
                    hasRests = true;
                }
            } else {
                hasNonDurations = true;
            }

            if (hasDurations && hasNonDurations && hasRests) {
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Toolbar reflection
    // -------------------------------------------------------------------------

    /**
     * Scans all static fields in Actions for UIAction instances matching the given predicate.
     */
    private <T> List<T> collectActions(Class<T> type, java.util.function.Predicate<UIAction> filter) {
        var result = new ArrayList<T>();

        for (var field : Actions.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
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

    /**
     * Applies the given action to all applicable elements in the selection.
     * @param action   the reflectable action to apply
     * @param selected true to apply the attribute, false to remove it
     */
    public void applyActionToSelection(UIAction.Reflectable action, boolean selected) {
        var selection = getSelection();
        if (selection == null) return;

        var line = selection.line();
        var needsIntervalCleanup = false;

        for (int i = selection.begin(); i <= selection.end(); i++) {
            var element = line.getElement(i);

            if (!action.appliesTo(element)) continue;

            if (action instanceof UIAction.ElementReplaceable replaceable) {
                var replacement = replaceable.createReplacement(element, selected);

                if (replacement == null) {
                    if (selected) {
                        RuntimeError.logNull(
                            null,
                            "Selection Error",
                            "createReplacement returned null for element " + i + " in selection",
                            "Could not change the duration of a selected element."
                        );
                    }

                    continue;
                }

                line.replaceElementQuietly(i, replacement);
                needsIntervalCleanup = true;
            } else if (action instanceof UIAction.ElementModifiable modifiable) {
                modifiable.applyToElement(element, selected);
            }
        }

        if (needsIntervalCleanup) {
            validateIntervals(line, selection.begin(), selection.end());
        }

        contentCacheSelection = null;
        applicabilityCacheSelection = null;
        applicabilityCache.clear();
        var composition = line.getComposition();
        composition.setModified(true);
        MessageCenter.post(new CompositionDidChangeNotification(CompositionDidChangeNotification.ChangeType.CONTENT, composition, line));
    }

    /**
     * Validates beam, tie, and tuplet intervals after batch element replacement.
     * Elements that changed type may invalidate intervals they belong to.
     */
    private void validateIntervals(Line line, int begin, int end) {
        repairIntervalSet(line.getBeamings(), line, begin, end,
            element -> element.getType().isBeamable());
        repairIntervalSet(line.getTies(), line, begin, end,
            element -> element.getType().isNote());
        repairIntervalSet(line.getTuplets(), line, begin, end,
            element -> element.getType().isDuration());
    }

    /**
     * Generic interval repair: finds intervals overlapping [begin, end] that contain
     * elements failing the validity predicate, removes them, and re-creates sub-intervals
     * for contiguous runs of valid elements (minimum 2 elements per sub-interval).
     */
    private <T extends Interval> void repairIntervalSet(
        IntervalSet<T> intervalSet, Line line, int begin, int end,
        Predicate<StaffElement> isValid) {

        var toProcess = new ArrayList<T>();

        for (var iter = intervalSet.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();

            if (interval.start <= end && interval.end >= begin) {
                toProcess.add(interval);
            }
        }

        for (var interval : toProcess) {
            boolean allValid = true;

            for (int i = interval.start; i <= interval.end; i++) {
                if (!isValid.test(line.getElement(i))) {
                    allValid = false;
                    break;
                }
            }

            if (allValid) {
                continue;
            }

            intervalSet.removeInterval(interval);

            // Find runs of valid elements and create sub-intervals
            int runStart = -1;

            for (int i = interval.start; i <= interval.end; i++) {
                if (isValid.test(line.getElement(i))) {
                    if (runStart == -1) {
                        runStart = i;
                    }
                } else {
                    if (runStart != -1 && (i - runStart) >= 2) {
                        @SuppressWarnings("unchecked")
                        var sub = (T) interval.copyRange(runStart, i - 1);
                        intervalSet.addInterval(sub);
                    }

                    runStart = -1;
                }
            }

            if (runStart != -1 && (interval.end - runStart) >= 1) {
                @SuppressWarnings("unchecked")
                var sub = (T) interval.copyRange(runStart, interval.end);
                intervalSet.addInterval(sub);
            }
        }
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
     * Clears saved action states without restoring them.
     * Used when the operation that saved states completes successfully
     * and the current state should be kept.
     */
    public void clearSavedActionStates() {
        savedActionStates.clear();
    }

    /**
     * Restores only the actions that have the given flag to their previously
     * saved state, then clears all saved states. Actions without the flag
     * are left at their current state.
     */
    public void restoreActionStatesWithFlag(@NotNull UIAction.Flag flag) {
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

    /**
     * Saves or restores action states at HIGH_PRIORITY, before UIAction handlers
     * run at MEDIUM_PRIORITY. This ensures save captures the true pre-selection
     * state and restore puts it back before updateEnabledState recomputes.
     */
    @Handler(priority = Message.HIGH_PRIORITY)
    public void musicSelectionDidChangeSaveRestoreActionStates(MusicSelectionDidChangeNotification message) {
        var selection = getSelection();

        if (selection == null) {
            restoreActionStates();
        } else if (!selection.equals(lastReflectedSelection)) {
            saveActionStates();
        }
    }

    /**
     * Reflects the current selection onto all reflectable toolbar actions.
     * Fires at LOW_PRIORITY so it runs after all UIAction handlers have processed
     * the selection-changed message.
     */
    @Handler(priority = Message.LOW_PRIORITY)
    public void musicSelectionDidChangeReflectSelection(MusicSelectionDidChangeNotification message) {
        var actions = getReflectableActions();
        var selection = getSelection();

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
    public void reflectElement(@NotNull StaffElement element) {
        for (var reflectable : getReflectableActions()) {
            reflectable.setSelected(
                reflectable.appliesTo(element) && reflectable.matchesElement(element)
            );
        }

        updateGraceNoteActionEnabled(element.getType().isGraceNote());
    }

    // Grace notes can only be inserted, not applied to existing notes.
    // In select mode the action is unconditionally disabled.
    private void updateGraceNoteActionEnabled(boolean hasGraceNote) {
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(!inSelectMode && hasGraceNote);
    }
}
