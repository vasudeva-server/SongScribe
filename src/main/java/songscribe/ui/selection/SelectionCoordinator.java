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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.dom.Song;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.ui.EndingConfirms;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.LineComponent;
import songscribe.dom.Beam;

/**
 * Lightweight score-level coordinator that tracks which line (if any) has
 * the active selection, and handles cross-line queries.
 * <p>
 * Replaces SelectionManager as the score-level selection object.
 */
public final class SelectionCoordinator {

    public record LyricSelection(StaffElement element, int verse) {}

    private final Supplier<Song> songSupplier;

    /** Registry of per-line selection states, keyed by line index. */
    private final Map<Integer, LineSelectionState> lineStates = new HashMap<>();

    /** Which line currently has the active selection, or -1 if none. */
    private int activeLineIndex = -1;
    @Nullable
    private LyricSelection lyricSelection = null;

    /**
     * The LineComponent that currently has an active rubber-band drag, or null.
     * Tracked so the global AWTEventListener can clean up if Swing fails to
     * deliver mouseReleased to the originating LineComponent.
     */
    @Nullable
    private LineComponent draggingLine = null;

    /** Whether the user is in select mode (shift held down or select mode active). */
    private boolean inSelectMode = false;

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

    // Content cache: lazily computed flags about what the current selection contains.
    @Nullable
    private ElementSelection contentCacheSelection = null;
    private boolean hasDurations;
    private boolean hasRests;

    // Applicability cache: maps action to whether it applies to any element in the selection.
    @Nullable
    private ElementSelection applicabilityCacheSelection = null;
    private final Map<UIAction.Reflectable, Boolean> applicabilityCache = new IdentityHashMap<>();

    private record ActionState(boolean selected, boolean enabled) {}

    /**
     * Global AWT listener that catches mouseReleased events which Swing sometimes
     * fails to deliver to a LineComponent during fast rubber-band drags.
     * Registered only while a drag is active and removed once the release is caught.
     */
    private final AWTEventListener globalMouseReleasedListener = event -> {
        if (event instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_RELEASED) {
            if (draggingLine != null) {
                if (draggingLine.isDraggingSelection()) {
                    draggingLine.clearDragRectangle();
                }

                draggingLine = null;
            }

            Toolkit.getDefaultToolkit().removeAWTEventListener(this.globalMouseReleasedListener);
        }
    };

    public SelectionCoordinator(Supplier<Song> songSupplier) {
        this.songSupplier = songSupplier;
        MessageCenter.subscribe(this);
    }

    // -------------------------------------------------------------------------
    // Line state registry
    // -------------------------------------------------------------------------

    /**
     * Registers a LineSelectionState for the given line index.
     * Called by LineComponent when it is set up.
     */
    public void registerLineState(int lineIndex, LineSelectionState state) {
        state.setSelectionChangeCallback(this::clearLyricSelection);
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
        clearLyricSelection();

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
        clearLyricSelection();

        if (activeLineIndex != -1) {
            var state = lineStates.get(activeLineIndex);

            if (state != null) {
                state.clearSelection();
            }
        }

        activeLineIndex = -1;
    }

    public void selectLyric(StaffElement element, int verse) {
        clearSelection();
        activeLineIndex = findLineIndex(element.getLine());
        lyricSelection = new LyricSelection(element, verse);
    }

    public void clearLyricSelection() {
        lyricSelection = null;
    }

    public @Nullable LyricSelection getLyricSelection() {
        return lyricSelection;
    }

    public boolean hasLyricSelection() {
        return lyricSelection != null;
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
    // Cross-line queries (needed for rendering and ScoreView API)
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
     * Returns whether the slide owned by the element at the given index
     * on the given line is selected.
     */
    public boolean isSlideSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.isSlideSelected(elementIndex);
    }

    public boolean isLyricSelected(StaffElement element, int verse, int lineIndex) {
        //noinspection SimplifiableIfStatement
        if (activeLineIndex != lineIndex || lyricSelection == null) {
            return false;
        }

        return lyricSelection.element() == element && lyricSelection.verse() == verse;
    }

    /**
     * Returns whether any slide is selected on the active line.
     */
    public boolean hasSlideSelection() {
        var state = getActiveSelection();
        return (state != null) && state.hasSlideSelection();
    }

    private int findLineIndex(Line line) {
        for (var entry : lineStates.entrySet()) {
            if (entry.getValue().getLine() == line) {
                return entry.getKey();
            }
        }

        return -1;
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
        return state != null && state.isLineSelected() && songSupplier.get().lineCount() > 1;
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
    public boolean isApplicableToSelection(UIAction.Reflectable action) {
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
        var hasNonDurations = false;
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

    /**
     * Applies the given action to all applicable elements in the selection.
     * Wraps the entire pass in a single modification bracket so all emitted
     * mutations coalesce into one {@code SongDidChangeNotification}.
     * @param action   the reflectable action to apply
     * @param selected true to apply the attribute, false to remove it
     * @param parent   the component to use as the dialog parent for any ending-confirm dialogs
     */
    public void applyActionToSelection(UIAction.Reflectable action, boolean selected, @Nullable Component parent) {
        var selection = getSelection();

        if (selection == null) {
            return;
        }

        var line = selection.line();
        var song = line.getSong();

        song.withModification(() -> {
            var needsSpanCleanup = false;

            for (var i = selection.begin(); i <= selection.end(); i++) {
                var element = line.getElement(i);

                if (!action.appliesTo(element)) {
                    continue;
                }

                if (action instanceof UIAction.ElementReplaceable replaceable) {
                    if (!selected) {
                        continue;
                    }

                    var replacement = replaceable.createReplacement(element, true);
                    var effect = LineEndingSupport.findEndingReplacementEffect(line, i, replacement);

                    switch (effect) {
                        case Ending.EndingEffect.Invalidate _ -> {
                            if (!EndingConfirms.confirmInvalidation(parent)) {
                                continue;
                            }
                            // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
                        }
                        case Ending.EndingEffect.CompensateEnd ce -> {
                            if (!EndingConfirms.confirmCompensateEnd(parent, ce)) {
                                continue;
                            }
                            EndingConfirms.applyCompensatingEndChange(line, ce);
                        }
                        case Ending.EndingEffect.CompensateSplit cs -> {
                            if (!EndingConfirms.confirmCompensateSplit(parent, cs, replacement.getType())) {
                                continue;
                            }
                            EndingConfirms.applyCompensatingSplitChange(line, cs);
                        }
                        case Ending.EndingEffect.None _ -> {}
                    }

                    line.setElement(i, replacement);
                    needsSpanCleanup = true;
                } else if (action instanceof UIAction.ElementModifiable modifiable) {
                    var index = i;
                    line.modifyElement(
                        index,
                        modifiable.modifiedFields(),
                        () -> modifiable.applyToElement(line.getElement(index), selected)
                    );
                }
            }

            if (needsSpanCleanup) {
                validateSpans(line, selection.begin(), selection.end());
            }

            contentCacheSelection = null;
            applicabilityCacheSelection = null;
            applicabilityCache.clear();
        });
    }

    // Validates beam and tuplet spans after batch element replacement.
    //
    // Tie repair is omitted: under the invariants enforced by the call site,
    // no reachable replacement can invalidate an existing tie. The replacement
    // preserves pitch and rest-ness; grace notes are disabled in select mode
    // via Flag.DISABLE_IN_SELECT_MODE; and ElementModifiable actions do not
    // touch element type.
    private void validateSpans(Line line, int begin, int end) {
        repairBeamings(line, begin, end);
        line.removeOverlappingTuplets(begin, end);
    }

    // Trim-and-kill repair for beams that overlap [begin, end] after a batch
    // element replacement. For each overlapping beam:
    //   1. Trim non-beamable elements from the left and right ends.
    //   2. If the trimmed span still contains a non-beamable element in the
    //      interior, kill the beam entirely (no replacement).
    //   3. If the trimmed span is identical to the original, no-op.
    //   4. If the trimmed span has fewer than two elements, kill without re-add.
    //   5. Otherwise, remove the original beam and add a new one for the
    //      trimmed sub-range.
    //
    // The bidirectional trim handles configurations where both ends become
    // non-beamable but the interior is still valid, leaving the logic resilient
    // to a future disjoint-selection capability.
    //
    // Behavior change vs. legacy repairSpanSet: a beam with a non-beamable
    // element in the middle is now killed entirely instead of being split into
    // sub-beams.
    private void repairBeamings(Line line, int begin, int end) {
        var overlapping = line.findBeamsOverlapping(begin, end);

        for (var beam : overlapping) {
            var anchorIdx = beam.getAnchorElementIndex();
            var endIdx = beam.getEndElementIndex();
            var newStart = anchorIdx;

            while (newStart <= endIdx && !line.getElement(newStart).getType().isBeamable()) {
                newStart++;
            }

            var newEnd = endIdx;

            while (newEnd >= newStart && !line.getElement(newEnd).getType().isBeamable()) {
                newEnd--;
            }

            // No valid elements remain in the trimmed span — kill outright.
            if (newStart > newEnd) {
                line.removeBeaming(beam);
                continue;
            }

            // Check for any non-beamable element in the interior of the trimmed span.
            var hasInteriorInvalid = false;

            for (var i = newStart; i <= newEnd; i++) {
                if (!line.getElement(i).getType().isBeamable()) {
                    hasInteriorInvalid = true;
                    break;
                }
            }

            if (hasInteriorInvalid) {
                line.removeBeaming(beam);
                continue;
            }

            // Trimmed span is identical to the original — no-op.
            if (newStart == anchorIdx && newEnd == endIdx) {
                continue;
            }

            // Fewer than two elements — kill without re-add.
            if (newEnd - newStart < 1) {
                line.removeBeaming(beam);
                continue;
            }

            // Trimmed span shrank but is all valid — replace with the truncated beam.
            line.removeBeaming(beam);
            line.addBeaming(new Beam(line.getElement(newStart), line.getElement(newEnd)));
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
     * Returns whether the saved action states map is empty.
     * Package-private for tests that verify clear/restore semantics.
     */
    boolean hasSavedActionStates() {
        return !savedActionStates.isEmpty();
    }

    /**
     * Returns the LineComponent that currently has an active rubber-band drag, or null.
     * Package-private for tests that verify drag-cleanup semantics.
     */
    @Nullable
    LineComponent getDraggingLine() {
        return draggingLine;
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

    /**
     * Called by {@link LineComponent} when a rubber-band drag begins,
     * so the coordinator can install the safety-net AWTEventListener.
     */
    public void dragDidStart(LineComponent lineComponent) {
        draggingLine = lineComponent;
        Toolkit.getDefaultToolkit().addAWTEventListener(
            globalMouseReleasedListener, AWTEvent.MOUSE_EVENT_MASK
        );
    }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void musicSelectionDidChangeSaveRestoreActionStates(MusicSelectionDidChangeNotification message) {
        var selection = getSelection();

        if (selection == null) {
            if (hasSlideSelection()) {
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
    @Handler()
    public void musicSelectionDidChangeReflectSelection(MusicSelectionDidChangeNotification message) {
        triggerReflection();
    }

    /**
     * Reflects the current selection onto all reflectable toolbar actions.
     * Package-private so tests can trigger reflection directly without a notification.
     */
    void triggerReflection() {
        var actions = getReflectableActions();
        var selection = getSelection();

        // Selection cleared
        if (selection == null) {
            lastReflectedSelection = null;

            if (hasSlideSelection()) {
                reflectSlideSelection();
            }

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
     * Reflects a standalone slide selection onto toolbar actions.
     * The matching slide action is selected and enabled; all others are disabled.
     */
    private void reflectSlideSelection() {
        var state = getActiveSelection();

        if (state == null) {
            return;
        }

        var elementIndex = state.getSelectedSlideElementIndex();
        var element = state.getLine().getElement(elementIndex);

        if (element.getSlide() == null) {
            return;
        }

        for (var reflectable : getReflectableActions()) {
            var matches = reflectable.matchesSlide(element);
            ((UIAction) reflectable).setEnabled(matches);
            reflectable.setSelected(matches);
        }
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
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(!inSelectMode && hasGraceNote);
    }
}
