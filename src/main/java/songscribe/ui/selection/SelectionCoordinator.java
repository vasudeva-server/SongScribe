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

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.ui.Mode;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

/**
 * Score-level coordinator that owns the score's one selection, tracks which line it sits on,
 * and answers the cross-line queries built on it.
 * <p>
 * <b>There is one selection in the score, and it has two shapes.</b> Both live in one field,
 * so they exclude each other structurally: assigning either is the whole of dropping the
 * other. See {@link Selection} for what the shapes are and why neither can express the other.
 * <p>
 * That one field is also the one place the selection can change, which is what lets repaint,
 * notification and cache invalidation all hang off assignment. Nothing hands out a mutable
 * selection object for a caller to change behind this class's back.
 * <p>
 * {@link #lines} is the second half of the picture: it maps a line index to the {@link Line}
 * at that index, so {@code activeLineIndex} can name a line without holding one. That
 * registration is what a {@code LineComponent} does when it takes on a line, and it is
 * deliberately all a {@code LineComponent} contributes — the selection itself outlives any
 * number of {@code LineComponent.setLine} rebuilds because it never lived on one.
 * <p>
 * <b>What is selected, and nothing downstream of it.</b> Three things follow from a selection
 * without being part of it, and each is its own class reached from here:
 * {@link ActionReflector} for what the toolbar shows about it, {@link SelectionActionApplier}
 * for applying an action across it, and {@link SelectionDragTracker} for finishing the drag
 * that made it. The dependency runs one way — each reads the selection through this class,
 * and none of them can change it.
 */
public final class SelectionCoordinator {

    /** The score this coordinator selects within. Its mode is the source of truth for
     *  {@link #isInSelectMode()}. */
    private final ScoreView scoreView;

    /** The line at each line index, registered by that index's {@link LineComponent}. */
    private final Map<Integer, Line> lines = new HashMap<>();

    /** Which line currently has the selection, or -1 if none. */
    private int activeLineIndex = -1;

    /**
     * The one thing selected in the score, on the line at {@code activeLineIndex}, or null if
     * nothing is selected.
     */
    @Nullable
    private Selection selected = null;

    /** Cleans up after a rubber-band drag Swing failed to finish. */
    private final SelectionDragTracker dragTracker = new SelectionDragTracker();

    /** Keeps the toolbar actions in step with whatever this coordinator has selected. */
    private final ActionReflector actionReflector = new ActionReflector(this);

    // Content cache: lazily computed flags about what the current selection contains.
    @Nullable
    private ElementSelection contentCacheSelection = null;
    private boolean hasDurations;
    private boolean hasRests;

    // Applicability cache: maps action to whether it applies to any element in the selection.
    @Nullable
    private ElementSelection applicabilityCacheSelection = null;
    private final Map<UIAction.Reflectable, Boolean> applicabilityCache = new IdentityHashMap<>();

    public SelectionCoordinator(ScoreView scoreView) {
        this.scoreView = scoreView;
        MessageCenter.subscribe(this);
    }

    /**
     * Detaches this coordinator and everything it subscribed on its own behalf from the bus.
     * <p>
     * Constructing a coordinator puts two listeners on the bus, not one — the
     * {@link ActionReflector} it owns subscribes itself — so unsubscribing the coordinator
     * object alone would leave the reflector handling notifications for a coordinator the
     * test has finished with. A test that drives a coordinator directly and wants it off the
     * bus calls this rather than {@code MessageCenter.unsubscribe(coordinator)}.
     */
    public void unsubscribeForTest() {
        MessageCenter.unsubscribe(this);
        MessageCenter.unsubscribe(actionReflector);
    }

    // -------------------------------------------------------------------------
    // Line registry
    // -------------------------------------------------------------------------

    /**
     * Registers the line at the given index. Called by {@link LineComponent} when it takes
     * on a line, and again if its score is set afterwards.
     */
    public void registerLine(int lineIndex, Line line) {
        lines.put(lineIndex, line);
    }

    /**
     * Removes a line registration.
     */
    public void unregisterLine(int lineIndex) {
        lines.remove(lineIndex);
    }

    /**
     * Forgets every registered line and deactivates.
     */
    public void clearLines() {
        lines.clear();
        activeLineIndex = -1;
    }

    /**
     * Returns the line registered at the given index, or null if none is.
     */
    @Nullable
    public Line getLine(int lineIndex) {
        return lines.get(lineIndex);
    }

    // -------------------------------------------------------------------------
    // Active line management
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the line that currently has the selection,
     * or -1 if no line does.
     */
    public int getActiveLineIndex() {
        return activeLineIndex;
    }

    /**
     * Returns the line the selection sits on, or null if no line is active.
     * <p>
     * Answers whatever shape the selection has, including none — a decoration target and a
     * whole-line selection both sit on a line without carrying an index range, and the
     * active line is how they are cleared, revalidated and reflected. Callers that
     * specifically want an index range ask {@link #getRange} instead.
     */
    @Nullable
    public Line getActiveLine() {
        return lines.get(activeLineIndex);
    }

    /**
     * Returns the selected index range, or null if the selection is a target or there is
     * none. A range is never empty: nothing selected is null, not a zero-width range.
     */
    public Selection.@Nullable Range getRange() {
        return (selected instanceof Selection.Range range) ? range : null;
    }

    /**
     * Collapses the selection to a single element at {@code elementIndex} on the line
     * at {@code lineIndex}. Returns whether a line was registered at that index to select on.
     * Shared by the mouse click-to-select path and arrow-key navigation; each caller handles
     * its own notification and repaint.
     */
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

    /**
     * Activates the given line for selection, dropping whatever was selected.
     * <p>
     * Clears unconditionally, including when the line is already active: activating a line is
     * the start of selecting on it, and every caller assigns a selection immediately after or
     * has just cleared one. Keeping a range alive across it would mean the shape of what was
     * selected decided whether it survived, which is the distinction this class no longer draws.
     */
    public void activateLine(int lineIndex) {
        selected = null;
        activeLineIndex = lineIndex;
    }

    /**
     * Clears the selection and deactivates the line it was on.
     */
    public void clearSelection() {
        selected = null;
        activeLineIndex = -1;
    }

    /**
     * Clears what is selected while leaving its line active, so the next selection lands on
     * the same line without being reactivated. What a rubber band that caught nothing, and a
     * selection that a mutation invalidated, both leave behind.
     */
    public void clearActiveSelection() {
        selected = null;
    }

    /**
     * Makes the given target the sole selection on the active line.
     * <p>
     * Whatever was selected before — an index range, another target, the staff line — is
     * dropped by the assignment itself: one field holds one selection, so there is nothing
     * further to clear. The caller is responsible for having activated the target's line
     * first.
     */
    public void select(HitTarget target) {
        selected = new Selection.Target(target);
    }

    /**
     * Selects {@code begin..end} on the active line, anchored at {@code anchor}, or clears the
     * selection if {@code begin} is -1. The line stays active either way.
     * <p>
     * The one entry point for setting a range: a click, a drag, an arrow-key extension and a
     * post-mutation re-derivation all land here, so a range replaces a target by the same
     * assignment that sets it.
     */
    public void selectRange(int begin, int end, int anchor) {
        var line = getActiveLine();

        if (line == null) {
            return;
        }

        selected = (begin == -1) ? null : new Selection.Range(line, begin, end, anchor);
    }

    /**
     * Selects {@code begin..end} on the active line, anchored at {@code begin}.
     */
    public void selectRange(int begin, int end) {
        selectRange(begin, end, begin);
    }

    /**
     * Extends the selection from its anchor to {@code elementIndex}, leaving the anchor where
     * it is. No-op when the selection is not a range, since there is no anchor to extend from.
     */
    public void extendSelectionTo(int elementIndex) {
        var range = getRange();

        if (range == null) {
            return;
        }

        var anchor = range.anchor();
        selected = new Selection.Range(
            range.line(),
            Math.min(anchor, elementIndex),
            Math.max(anchor, elementIndex),
            anchor);
    }

    /**
     * Selects every element on the active line, excluding the song's auto-maintained terminal.
     * <p>
     * A whole-line selection is swapped for its elements in the process, because both are the
     * same field. An empty line has nothing to swap to, so its selection stands.
     */
    public void selectAll() {
        var line = getActiveLine();

        if (line == null) {
            return;
        }

        var end = line.effectiveElementCount() - 1;

        if (end < 0) {
            return;
        }

        selected = new Selection.Range(line, 0, end, 0);
    }

    /**
     * Makes the lyric on {@code element} in verse {@code verse} the sole selection, activating
     * the line the element sits on.
     * <p>
     * Unlike every other target, a lyric names the line it belongs to, so this resolves and
     * activates that line itself rather than requiring the caller to have done it.
     */
    public void selectLyric(StaffElement element, int verse) {
        var line = element.getParentLine();

        // Selecting a lyric on an element in no line is meaningless, and there is no
        // line index to activate. Bail before disturbing the existing selection.
        if (line == null) {
            return;
        }

        clearSelection();
        activeLineIndex = findLineIndex(line);
        select(new HitTarget.Lyric(element, verse));
    }

    // -------------------------------------------------------------------------
    // Select mode
    // -------------------------------------------------------------------------

    /**
     * Whether the score is in select mode. Derived from the score's mode rather than cached,
     * so there is no second copy of the fact to keep in step with it.
     */
    public boolean isInSelectMode() {
        return scoreView.getMode() == Mode.SELECT;
    }

    // -------------------------------------------------------------------------
    // Cross-line queries (needed for rendering and ScoreView API)
    // -------------------------------------------------------------------------

    /**
     * Returns whether the element at the given index on the given line is selected.
     */
    public boolean isElementSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex != lineIndex || elementIndex < 0) {
            return false;
        }

        var range = getRange();
        return (range != null) && range.contains(elementIndex);
    }

    /**
     * Returns whether the staff line itself is the current selection. The readable name for
     * a {@link HitTarget.StaffLine} target, which is how a line selection is held.
     */
    public boolean isLineSelected() {
        return selected instanceof Selection.Target(HitTarget.StaffLine _);
    }

    /**
     * Returns whether the staff line at the given index is selected (for deletion).
     */
    public boolean isLineSelected(int lineIndex) {
        return activeLineIndex == lineIndex && isLineSelected();
    }

    /**
     * Returns the selected target, or null if the selection is an index range or there is none.
     * <p>
     * A whole-line selection reads as {@link HitTarget.StaffLine} here rather than as null.
     * Callers that mean "a decoration is selected" want {@link #hasDecorationSelection}.
     */
    @Nullable
    public HitTarget getSelectedTarget() {
        return (selected instanceof Selection.Target(var target)) ? target : null;
    }

    /**
     * Returns whether the given target is the current selection on the given line.
     * <p>
     * Every {@link HitTarget} is a record over object references, so the general case compares
     * what the target names rather than where it sits on the line.
     * <p>
     * One kind is answered from the other selection shape, so that callers need only this one
     * query: a note is selected through the index range (clicking one collapses the range onto
     * it). That predates {@link HitTarget} and stays as it is — see {@link Selection}.
     * <p>
     * The leading {@code activeLineIndex} gate is kind-dependent. Every {@link HitTarget} kind
     * except {@link HitTarget.Tie} lives on exactly one line, so asking about any other line
     * must answer {@code false} — the gate short-circuits that before the switch runs. A tie
     * can be cross-line (#493): one {@link songscribe.dom.Tie} object present in both lines'
     * span lists, half drawn by each. Both lines' repaints ask this method with their own line
     * index (see {@code LineRenderer.buildInvariants}), so the tie case answers instead from
     * {@link songscribe.dom.Tie#isIn} against the line at {@code lineIndex} — record equality
     * alone would report the tie selected on every line in the song.
     * <p>
     * The switch is exhaustive on purpose. A {@code default} arm would silently answer
     * {@code false} for a variant added later, which is exactly how the staff line went
     * unanswered before it was folded in.
     */
    public boolean isSelected(HitTarget target, int lineIndex) {
        if (target instanceof HitTarget.Tie(var tie)) {
            var line = getLine(lineIndex);
            return isSelectedTarget(target) && line != null && tie.isIn(line);
        }

        if (activeLineIndex != lineIndex) {
            return false;
        }

        return switch (target) {
            case HitTarget.Element(var element) -> {
                var range = getRange();

                // The index has to be derived here because the caller supplied only the element.
                // Callers that already hold the index use isElementRangeSelected directly, which
                // skips this scan.
                yield range != null && range.contains(range.line().getElementIndex(element));
            }
            case HitTarget.Lyric _ -> isSelectedTarget(target);
            case HitTarget.Slide _ -> isSelectedTarget(target);
            case HitTarget.GraceGlissando _ -> isSelectedTarget(target);
            case HitTarget.Hairpin _ -> isSelectedTarget(target);
            case HitTarget.Ending _ -> isSelectedTarget(target);
            case HitTarget.StaffLine _ -> isSelectedTarget(target);
            case HitTarget.Articulation _ -> isSelectedTarget(target);
            case HitTarget.Attachment _ -> isSelectedTarget(target);
            case HitTarget.Accidental _ -> isSelectedTarget(target);
            case HitTarget.Tie _ -> throw new AssertionError("HitTarget.Tie is handled above the gate");
            case HitTarget.Beam _ -> isSelectedTarget(target);
            case HitTarget.Trill _ -> isSelectedTarget(target);
            case HitTarget.Tuplet _ -> isSelectedTarget(target);
        };
    }

    /**
     * Returns whether the element at {@code elementIndex} on the line at {@code lineIndex} falls
     * inside the selected index range.
     * <p>
     * Separate from {@link #isSelected} because a range is not a thing a click addresses — it is
     * the other selection shape (see {@link Selection}) — and because every caller that draws an
     * element already holds its index. Routing those callers through {@link #isSelected} would
     * mean wrapping the index into a {@link HitTarget.Element} only for the answer to derive it
     * back by scanning the line, once per drawn element on every repaint.
     *
     * @param elementIndex the element's index on the line; out of range yields false
     * @param lineIndex    the line the element sits on
     */
    public boolean isElementRangeSelected(int elementIndex, int lineIndex) {
        if (activeLineIndex != lineIndex || elementIndex < 0) {
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
     * Returns whether a decoration — a slide, ending, hairpin, articulation and so on — is
     * selected.
     * <p>
     * An index range is not a decoration, and neither are two of the targets. A
     * {@link HitTarget.StaffLine} selects the line as a whole: this predicate decides whether
     * Delete removes one notation or the entire line, so answering true for it would delete a
     * notation instead of the line. A {@link HitTarget.Lyric} selects text rather than
     * notation, and Delete reaches it through its own branch; it has never counted here, and
     * the callers that want it ask for it by name.
     */
    public boolean hasDecorationSelection() {
        var target = getSelectedTarget();

        return target != null
            && !(target instanceof HitTarget.StaffLine)
            && !(target instanceof HitTarget.Lyric);
    }

    /**
     * Clears the selection if its target no longer refers to something live on its line —
     * e.g. after an undo/redo that removed the selected notation outright. No-op if the
     * current selection is still valid, or if it is not a target.
     * <p>
     * One rule covers every {@link HitTarget} variant, rather than one arm per variant.
     * {@link HitTarget#owner()} names the element the target hangs off, so walking to the
     * root of the parent chain and asking {@link #isOnLine} whether that root is still on
     * the line answers for an articulation on a note, a tie, a hairpin and a note itself
     * alike.
     *
     * @return whether the selection was cleared
     */
    public boolean revalidateDecorationSelection() {
        var line = getActiveLine();
        var target = getSelectedTarget();

        // A null owner means the target is the staff line itself, which cannot go stale.
        var owner = (target == null) ? null : target.owner();

        if (line == null || owner == null || isOnLine(owner, line)) {
            return false;
        }

        // The line stays active: only what was selected on it went stale.
        clearActiveSelection();
        return true;
    }

    /**
     * Clears the selection if it is a range that no longer fits its line — e.g. after an undo
     * that removed an element the range covered. No-op for a target selection, which
     * {@link #revalidateDecorationSelection} answers for instead.
     * <p>
     * The line stays active, as it does for a stale target: only what was selected on it
     * became unusable.
     *
     * @return whether the selection was cleared
     */
    public boolean revalidateElementSelection() {
        var range = getRange();

        if (range == null || range.fitsLine()) {
            return false;
        }

        clearActiveSelection();
        return true;
    }

    /**
     * Returns whether {@code element} still hangs off {@code line}.
     * <p>
     * Sub-elements — an articulation, a fermata — carry no line of their own, so the walk
     * climbs to the root of the parent chain and asks about that element instead.
     * <p>
     * A staff element answers from its own {@code parentLine} field, which
     * {@link Line#detach} clears when the line drops it, so a reference comparison against
     * {@code line} settles it in constant time.
     * <p>
     * A span has no such field — it is never attached, and {@link Span#isIn} derives
     * parentage from its endpoints, which a removal leaves exactly where they were. So a
     * span is asked of the line's span list instead, the only record of a span having been
     * removed. That list is short, it is scanned once per mutation rather than per element,
     * and it is what makes a cross-line tie answer yes for <em>both</em> of the lines it is
     * drawn across: a removal takes it out of both lists together, and until then both hold
     * it.
     */
    private static boolean isOnLine(LineElement element, Line line) {
        var root = element;

        for (var parent = root.getParentElement(); parent != null; parent = root.getParentElement()) {
            root = parent;
        }

        if (root instanceof Span span) {
            return line.getSpans().contains(span);
        }

        return root.getParentLine() == line;
    }

    @SuppressWarnings("ObjectEquality")
    private int findLineIndex(Line line) {
        for (var entry : lines.entrySet()) {
            if (entry.getValue() == line) {
                return entry.getKey();
            }
        }

        return -1;
    }

    // -------------------------------------------------------------------------
    // Cross-line query methods
    // -------------------------------------------------------------------------

    /**
     * Returns whether a line can be deleted (a line is selected). Deleting the sole
     * remaining line is allowed — {@link songscribe.dom.Song#removeLine} replaces it
     * with a fresh empty line so the song always has at least one line.
     */
    public boolean canDeleteLine() {
        return getSelectedLine() != -1;
    }

    /**
     * Returns whether tempo can be changed.
     */
    public boolean canChangeTempo() {
        return getSingleSelectedElement() != null;
    }

    // -------------------------------------------------------------------------
    // Selection shape queries
    // -------------------------------------------------------------------------

    /**
     * Returns the number of elements in the selection, or 0 if it is not a range.
     */
    public int getSelectionSize() {
        var range = getRange();
        return (range != null) ? range.size() : 0;
    }

    /**
     * Returns the current selection as an index span, or null if nothing spanning is selected.
     * <p>
     * A whole-line selection carries no index range of its own, so it is reported as a span
     * over every element on the line. An empty line has nothing to span and reads as no
     * selection at all.
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

            return new ElementSelection(line, 0, elementCount - 1);
        }

        var range = getRange();
        return (range != null) ? range.toElementSelection() : null;
    }

    /**
     * Returns the single selected element if exactly one element is selected,
     * or null otherwise.
     */
    @Nullable
    public StaffElement getSingleSelectedElement() {
        var range = getRange();
        return (range != null) ? range.singleElement() : null;
    }

    /**
     * Returns the index of the line that has a line selection (for deletion),
     * or -1 if no line is selected.
     */
    public int getSelectedLine() {
        return isLineSelected() ? activeLineIndex : -1;
    }

    /**
     * Returns whether any line has a line selection.
     */
    public boolean hasLineSelection() {
        return getSelectedLine() != -1;
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

            if (elementType.isDuration() || elementType.isGraceNote()) {
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
     * Returns the reflector that keeps the toolbar actions in step with this selection.
     */
    public ActionReflector getActionReflector() {
        return actionReflector;
    }

    // -------------------------------------------------------------------------
    // Rubber-band drag tracking
    // -------------------------------------------------------------------------

    /**
     * Returns the tracker that cleans up after a rubber-band drag Swing failed to finish.
     */
    public SelectionDragTracker getDragTracker() {
        return dragTracker;
    }

    // -------------------------------------------------------------------------
    // Cache invalidation
    // -------------------------------------------------------------------------

    /**
     * Drops every cached answer about the current selection as soon as the song changes.
     * <p>
     * The caches are keyed by {@link ElementSelection} — a line plus an index range — and
     * an index names a different element once a mutation has shifted, replaced, or removed
     * what was there. Delete a selected barline and click the note that slides into its
     * index and the key is the same one the barline was answered under, so the note would
     * be reported as carrying no duration and applying to no action, leaving every
     * note-only button disabled.
     * <p>
     * Runs ahead of {@link UIAction#songDidChange}, which re-derives
     * each action's enabled state from these caches.
     */
    @Handler(priority = Message.HIGH_PRIORITY)
    public void songDidChangeInvalidateCaches(SongDidChangeNotification message) {
        invalidateSelectionCaches();
    }

    /**
     * Discards the cached selection queries and the reflection guard, so the next query
     * and the next reflection both recompute from the elements now on the line.
     * <p>
     * Package-private so {@link SelectionActionApplier} can call it from inside its
     * modification bracket, which is where a batch apply's caches go stale.
     */
    void invalidateSelectionCaches() {
        contentCacheSelection = null;
        applicabilityCacheSelection = null;
        applicabilityCache.clear();
        actionReflector.invalidateReflectionGuard();
    }
}
