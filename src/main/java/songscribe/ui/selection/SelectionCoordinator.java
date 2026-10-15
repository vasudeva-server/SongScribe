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

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.dom.Articulation;
import songscribe.dom.Attachment;
import songscribe.dom.Line;
import songscribe.dom.LineContent;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Tie;
import songscribe.hit.HitTarget;
import songscribe.lifecycle.Disposable;
import songscribe.message.Message;
import songscribe.message.MessageSubscription;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.Mode;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

/**
 * Score-level coordinator that owns the score's one selection and the line it sits on.
 * <p>
 * The selection lives in one field and has two shapes (see {@link Selection}); assigning either
 * drops the other. Nothing hands out a mutable selection for a caller to change behind this
 * class's back.
 * <p>
 * The song's auto-maintained terminal is reachable by a click ({@link #selectSingleElement})
 * and by nothing else: every other assignment goes through {@link #setRange}, whose clamp keeps
 * it out of any multi-element range. {@link Selection.Range} does not enforce this, so code
 * that assigns {@link #selected} without {@code setRange} re-opens the hole.
 *
 * <h2>Lifecycle</h2>
 * Constructing a coordinator puts two listeners on the message bus: itself and its
 * {@link ActionReflector}. The owner calls {@link #dispose()} when the {@link ScoreView} is
 * retired, which removes both; the coordinator must not be used afterwards.
 */
public final class SelectionCoordinator implements Disposable {

    private final ScoreView scoreView;

    private final Map<Integer, Line> lines = new HashMap<>();

    /** Which line currently has the selection, or null if none. */
    private @Nullable Integer activeLineIndex = null;

    /** On the line at {@code activeLineIndex}; null if nothing is selected. */
    @Nullable
    private Selection selected = null;

    private final SelectionDragTracker dragTracker = new SelectionDragTracker();

    private final ActionReflector actionReflector = new ActionReflector(this);

    private final SelectionCache<SelectionContent> contentCache = new SelectionCache<>();

    private final SelectionCache<Map<UIAction.Reflectable<?>, Boolean>> applicabilityCache =
        new SelectionCache<>();

    private final MessageSubscription subscription;

    public SelectionCoordinator(ScoreView scoreView) {
        this.scoreView = scoreView;
        subscription = new MessageSubscription(this);
    }

    /** Idempotent. The coordinator must not be used afterwards. */
    @Override
    public void dispose() {
        subscription.dispose();
        actionReflector.dispose();
    }

    public void registerLine(int lineIndex, Line line) {
        lines.put(lineIndex, line);
    }

    public void unregisterLine(int lineIndex) {
        lines.remove(lineIndex);
    }

    public void clearLines() {
        lines.clear();
        activeLineIndex = null;
    }

    /** Null if no line is registered at the given index. */
    @Nullable
    public Line getLine(int lineIndex) {
        return lines.get(lineIndex);
    }

    /** Null if no line has the selection. */
    public @Nullable Integer getActiveLineIndex() {
        return activeLineIndex;
    }

    /** Null if no line is active. */
    @Nullable
    public Line getActiveLine() {
        return lines.get(activeLineIndex);
    }

    /** Null if the selection is a target or there is none; a range is never empty. */
    public Selection.@Nullable Range getRange() {
        return (selected instanceof Selection.Range range) ? range : null;
    }

    /** Each caller handles its own notification and repaint. */
    public boolean selectSingleElement(int lineIndex, int elementIndex) {
        clearSelection();
        activateLine(lineIndex);

        var line = getActiveLine();

        if (line == null) {
            return false;
        }

        selected = Selection.Range.single(line, elementIndex);
        return true;
    }

    /** Clears the selection even when the line is already active. */
    public void activateLine(int lineIndex) {
        selected = null;
        activeLineIndex = lineIndex;
    }

    public void clearSelection() {
        selected = null;
        activeLineIndex = null;
    }

    /** Leaves its line active, so the next selection lands on it without being reactivated. */
    public void clearActiveSelection() {
        selected = null;
    }

    /** The caller has activated the target's line first. */
    public void select(HitTarget.Selectable target) {
        selected = new Selection.Target(target);
    }

    /** Clamped so a multi-element range never reaches the song's auto-maintained terminal. */
    private void setRange(Line line, ElementRange range, int anchor) {
        var clamped = range;

        if (range.size() > 1) {
            clamped = new ElementRange(range.begin(), Math.min(range.end(), line.effectiveElementCount() - 1));
            anchor = Math.min(anchor, clamped.end());
        }

        selected = new Selection.Range(line, clamped, anchor);
    }

    public void selectRange(ElementRange range) {
        var line = getActiveLine();

        if (line == null) {
            return;
        }

        setRange(line, range, range.begin());
    }

    public void extendSelectionTo(int elementIndex) {
        var range = getRange();

        if (range == null) {
            return;
        }

        var anchor = range.anchor();
        setRange(
            range.line(),
            new ElementRange(Math.min(anchor, elementIndex), Math.max(anchor, elementIndex)),
            anchor);
    }

    public void selectAll() {
        var line = getActiveLine();

        if (line == null) {
            return;
        }

        var lastSelectableIndex = line.effectiveElementCount() - 1;

        // Nothing selectable: an empty line, or a last line holding only the terminal.
        if (lastSelectableIndex < 0) {
            return;
        }

        setRange(line, new ElementRange(0, lastSelectableIndex), 0);
    }

    /** Activates the lyric's line itself; the caller need not. */
    public void selectLyric(StaffElement element, Song.Script script) {
        var line = element.requireParentLine();

        clearSelection();
        activeLineIndex = findLineIndex(line);
        select(new HitTarget.Lyric(element, script));
    }

    public boolean isInSelectMode() {
        return scoreView.getMode() == Mode.SELECT;
    }

    public boolean isElementSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex == null || activeLineIndex != lineIndex || elementIndex < 0) {
            return false;
        }

        var range = getRange();
        return (range != null) && range.contains(elementIndex);
    }

    public boolean isLineSelected() {
        return selected instanceof Selection.Target(HitTarget.StaffLine _);
    }

    public boolean isLineSelected(int lineIndex) {
        return activeLineIndex != null && activeLineIndex == lineIndex && isLineSelected();
    }

    /**
     * Null if the selection is an index range or there is none. A whole-line selection reads as
     * {@link HitTarget.StaffLine}, not null.
     */
    public HitTarget.@Nullable Selectable getSelectedTarget() {
        return (selected instanceof Selection.Target(var target)) ? target : null;
    }

    /**
     * A tie can cross a line break, so it is selected on {@code lineIndex} only if that line
     * holds one of its notes. The switch is exhaustive on purpose: a new {@link HitTarget} kind
     * must be decided here.
     */
    public boolean isSelected(HitTarget target, int lineIndex) {
        if (target instanceof HitTarget.Tie tieTarget) {
            var line = getLine(lineIndex);
            return isSelectedTarget(target) && line != null && tieTarget.isDrawnOn(line);
        }

        if (activeLineIndex == null || activeLineIndex != lineIndex) {
            return false;
        }

        return switch (target) {
            case HitTarget.Element(var element) -> {
                var range = getRange();

                // The index has to be derived here because the caller supplied only the element.
                // Callers that already hold the index use isElementRangeSelected directly, which
                // skips this scan. A clicked element can be on a different line than the range's.
                yield range != null
                    && range.line().contains(element)
                    && range.contains(range.line().getElementIndex(element));
            }
            case HitTarget.Tie _ -> throw new AssertionError("HitTarget.Tie is handled above the gate");

            // A grace-note glissando is refused with an explanation and the attribution is a
            // double-click target only, so neither can be what a Selection.Target holds.
            case HitTarget.GraceGlissando _, HitTarget.Attribution _ -> false;

            case HitTarget.Selectable _ -> isSelectedTarget(target);
        };
    }

    public boolean isElementRangeSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex == null || activeLineIndex != lineIndex || elementIndex < 0) {
            return false;
        }

        var range = getRange();

        return range != null && range.contains(elementIndex);
    }

    private boolean isSelectedTarget(HitTarget target) {
        return selected instanceof Selection.Target(var selectedTarget)
            && target.equals(selectedTarget);
    }

    /**
     * Excludes {@link HitTarget.StaffLine} (Delete removes the whole line, not one notation) and
     * {@link HitTarget.Lyric} (Delete reaches it through its own branch).
     */
    public boolean hasDecorationSelection() {
        var target = getSelectedTarget();

        return target != null
            && !(target instanceof HitTarget.StaffLine)
            && !(target instanceof HitTarget.Lyric);
    }

    public boolean revalidateDecorationSelection() {
        var line = getActiveLine();
        var target = getSelectedTarget();

        if (line == null || target == null || target.isLiveOn(line)) {
            return false;
        }

        // The line stays active: only what was selected on it went stale.
        clearActiveSelection();
        return true;
    }

    /**
     * Mutations are folded front-to-back: each record's indices are relative to the state the
     * previous record left behind. The final end-of-range check is a backstop for structural
     * changes that never reached the mutation record; do not remove it as unreachable.
     *
     * <p>Must run ahead of every other reader of the range for the notification: a subscriber
     * that outranks it and reads the range sees positions that no longer exist.
     */
    public void revalidateElementSelection(SongDidChangeNotification notification) {
        var range = getRange();

        if (range == null) {
            return;
        }

        for (var mutation : notification.getMutations()) {
            if (mutation instanceof LineDeletion deletion && deletion.deletedLine() == range.line()) {
                clearSelection();
                return;
            }

            range = range.splicedFor(mutation);

            if (range == null) {
                clearActiveSelection();
                return;
            }
        }

        if (range.range().end() >= range.line().elementCount()) {
            clearActiveSelection();
            return;
        }

        selected = range;
    }

    @SuppressWarnings("ObjectEquality")
    private @Nullable Integer findLineIndex(Line line) {
        for (var entry : lines.entrySet()) {
            if (entry.getValue() == line) {
                return entry.getKey();
            }
        }

        return null;
    }

    /** Deleting the sole remaining line is allowed. */
    public boolean canDeleteLine() {
        return getSelectedLine() != null;
    }

    /** Forbidden on the song's first element: the song's own tempo is edited in Song Settings. */
    public boolean canChangeTempo() {
        var selectedElement = getSingleSelectedElement();

        if (selectedElement == null) {
            return false;
        }

        return selectedElement != scoreView.getSong().firstElement();
    }

    public int getSelectionSize() {
        var range = getRange();
        return (range != null) ? range.range().size() : 0;
    }

    /**
     * Null if nothing spanning is selected. A whole-line selection is reported as a span over
     * every element on the line; an empty line reads as no selection.
     */
    @Nullable
    public ElementSelection getSelection() {
        if (isLineSelected()) {
            var line = getActiveLine();

            if (line == null) {
                return null;
            }

            var elementCount = line.effectiveElementCount();

            if (elementCount == 0) {
                return null;
            }

            return new ElementSelection(line, new ElementRange(0, elementCount - 1));
        }

        var range = getRange();
        return (range != null) ? range.toElementSelection() : null;
    }

    /** Null unless exactly one element is selected. */
    @Nullable
    public StaffElement getSingleSelectedElement() {
        var range = getRange();
        return (range != null) ? range.singleElement() : null;
    }

    /** Null if no line is selected. */
    public @Nullable Integer getSelectedLine() {
        return isLineSelected() ? activeLineIndex : null;
    }

    public boolean hasLineSelection() {
        return getSelectedLine() != null;
    }

    public List<StaffElement> getSelectedElements() {
        var selection = getSelection();

        if (selection == null) {
            return List.of();
        }

        return new ArrayList<>(selection.line().getElements(selection.range()));
    }

    public boolean hasActiveSelection() {
        return getSelection() != null;
    }

    public boolean isTerminalSelected() {
        var element = getSingleSelectedElement();
        return element != null && Song.isTerminalOfItsSong(element);
    }

    public boolean selectionHasDurations() {
        var content = selectionContent();
        return content != null && content.hasDurations();
    }

    public boolean selectionHasRests() {
        var content = selectionContent();
        return content != null && content.hasRests();
    }

    private @Nullable SelectionContent selectionContent() {
        if (!hasActiveSelection()) {
            return null;
        }

        var selection = getSelection();
        return selection == null ? null : contentCache.get(selection, SelectionContent::of);
    }

    private record SelectionContent(boolean hasDurations, boolean hasRests) {

        static SelectionContent of(ElementSelection selection) {
            var hasDurations = false;

            for (var element : selection.line().getElements(selection.range())) {
                var elementType = element.getType();

                if (elementType.isRest()) {
                    return new SelectionContent(true, true);
                }

                if (elementType.isDuration() || elementType.isGraceNote()) {
                    hasDurations = true;
                }
            }

            return new SelectionContent(hasDurations, false);
        }
    }

    /** True when any selected element can, as in {@link #isApplicableToSelection}. */
    public boolean selectionCanCarryAnnotation() {
        var selection = getSelection();

        if (selection == null) {
            return false;
        }

        return selection.line().getElements(selection.range()).stream()
            .anyMatch(element -> element.getType().canCarryAnnotation());
    }

    /**
     * Applicable when the action admits at least one selected element and
     * {@link UIAction.Reflectable#canToggle} holds for every element it admits.
     */
    public <E extends StaffElement> boolean isApplicableToSelection(UIAction.Reflectable<E> action) {
        var selection = getSelection();

        if (selection == null) {
            return false;
        }

        var applicability = applicabilityCache.get(selection, ignored -> new IdentityHashMap<>());

        return applicability.computeIfAbsent(action, ignored -> {
            var line = selection.line();
            var bounds = selection.range();
            var applicable = false;

            for (var i = bounds.begin(); i <= bounds.end(); i++) {
                if (action.admit(line.getElement(i)) == null) {
                    continue;
                }

                if (!action.canToggle(line, i)) {
                    return false;
                }

                applicable = true;
            }

            return applicable;
        });
    }

    public ActionReflector getActionReflector() {
        return actionReflector;
    }

    public SelectionDragTracker getDragTracker() {
        return dragTracker;
    }

    /**
     * The caches are keyed by line and index range, and an index names a different element
     * once a mutation shifts or removes what was there. Runs ahead of
     * {@link UIAction#songDidChange}, which re-derives each action's enabled state from them.
     */
    @Handler(priority = Message.HIGH_PRIORITY)
    public void songDidChangeInvalidateCaches(SongDidChangeNotification message) {
        invalidateSelectionCaches();
    }

    /** Package-private so {@link SelectionActionApplier} can call it inside its modification bracket. */
    void invalidateSelectionCaches() {
        contentCache.invalidate();
        applicabilityCache.invalidate();
        actionReflector.invalidateReflectionGuard();
    }
}
