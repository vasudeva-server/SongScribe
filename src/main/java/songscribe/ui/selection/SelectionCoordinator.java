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

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.dom.Ending;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.dom.Beam;

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

    /**
     * The LineComponent that currently has an active rubber-band drag, or null.
     * Tracked so the global AWTEventListener can clean up if Swing fails to
     * deliver mouseReleased to the originating LineComponent.
     */
    @Nullable
    private LineComponent draggingLine = null;

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

    public SelectionCoordinator(ScoreView scoreView) {
        this.scoreView = scoreView;
        MessageCenter.subscribe(this);
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

    AWTEventListener getGlobalMouseReleasedListener() {
        return globalMouseReleasedListener;
    }

    /**
     * One element the action will change, decided before anything is mutated.
     *
     * @param index        The element's index on the line, unchanged by the modification
     * @param standIn      The element as it will read afterwards: the replacement for an
     *                     {@link UIAction.ElementReplaceable}, a modified clone for an
     *                     {@link UIAction.ElementModifiable}. Detached from the line, so the fit
     *                     gate can measure it without anything having been mutated
     * @param compensation The ending change the user confirmed for this index, applied in the
     *                     apply pass because it mutates the line, or
     *                     {@link Ending.EndingEffect.None#INSTANCE} when there is none
     */
    private record PendingChange(int index, StaffElement standIn, Ending.EndingEffect compensation) {}

    /**
     * Applies the given action to all applicable elements in the selection.
     * Wraps the entire apply pass in a single modification bracket so all emitted
     * mutations coalesce into one {@code SongDidChangeNotification}.
     * <p>
     * Runs in three passes, because the ending confirms decide which elements actually change and
     * the fit gate cannot be built until that is known — gating the loop as it runs would
     * over-refuse, killing the whole action before its dialogs are even shown:
     * <ol>
     *   <li><b>Decide</b> — compute each applicable element's post-change stand-in and, for a
     *       replacement, resolve and confirm its ending effect. Mutates nothing and opens no
     *       bracket, which also keeps a dialog from being open while a modification bracket is.</li>
     *   <li><b>Reconcile and gate</b> — over the indices that proceed; still mutates nothing, so a
     *       refusal leaves the score untouched and creates no undo step.</li>
     *   <li><b>Apply</b> — inside the modification bracket.</li>
     * </ol>
     *
     * @param action   the reflectable action to apply
     * @param selected true to apply the attribute, false to remove it
     * @param score    the view to parent any ending-confirm dialogs on, and the source of the
     *                 song-wide lyric metrics the fit gate measures syllable widths with; null in
     *                 tests, which space the projection as if the line had no lyrics
     */
    public void applyActionToSelection(
        UIAction.Reflectable action, boolean selected, @Nullable ScoreView score) {
        var selection = getSelection();

        if (selection == null) {
            return;
        }

        var line = selection.line();
        var song = line.getSong();
        var changes = decideChanges(action, selected, score, selection);

        if (changes.isEmpty()) {
            return;
        }

        // Still the decide phase: the prompt must run before any modification bracket opens, for
        // the same reason the ending confirms above do.
        var decision = AccidentalRestatements.confirm(score, line, editedNotes(line, changes));

        if (decision.isCancelled()) {
            return;
        }

        // The accidentals this modification must make explicit so no pitch the user did not touch
        // changes — removing an explicit accidental is exactly as context-changing as adding one —
        // plus the ones it makes redundant and so takes away again, plus any restatement the user
        // accepted above.
        var accidentalChanges = AccidentalReconciliation.reconcileModification(
            line, intendedChanges(changes), decision.removal());

        if (action instanceof UIAction.WidensColumn
            && !fitsAfterModification(line, changes, accidentalChanges, score)) {
            OptionDialogs.showErrorMessage(
                score,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                changes.getFirst().standIn().getType().categoryName()
            );
            return;
        }

        // Only a replacement can invalidate a beam or a tuplet; an in-place modification leaves
        // element types alone.
        var needsSpanCleanup = action instanceof UIAction.ElementReplaceable;

        song.withModification(() -> {
            for (var change : changes) {
                var index = change.index();
                var compensation = change.compensation();

                // The compensating ending changes mutate the line, so they belong here rather
                // than beside the confirm that authorized them.
                if (compensation instanceof Ending.EndingEffect.CompensateEnd compensateEnd) {
                    EndingConfirms.applyCompensatingEndChange(line, compensateEnd);
                } else if (compensation instanceof Ending.EndingEffect.CompensateSplit compensateSplit) {
                    EndingConfirms.applyCompensatingSplitChange(line, compensateSplit);
                }

                if (action instanceof UIAction.ElementReplaceable) {
                    // An Invalidate effect needs no compensation here: setElement removes the
                    // ending itself via isInvalidatedByReplacement.
                    line.setElement(index, change.standIn());
                } else if (action instanceof UIAction.ElementModifiable modifiable) {
                    line.modifyElement(
                        index,
                        modifiable.modifiedFields(),
                        () -> modifiable.applyToElement(line.getElement(index), selected)
                    );
                }
            }

            // Recorded in the same bracket so the toggle and its reconciliation are one undo step.
            AccidentalMaterializer.commit(line, accidentalChanges);

            // Accepted restatements on later lines join the same step.
            AccidentalRestatements.commitOtherLines(decision, line);

            if (needsSpanCleanup) {
                validateSpans(line, selection.begin(), selection.end());
            }

            invalidateSelectionCaches();
        });
    }

    /**
     * Pass 1 — decides what the action does to each element of the selection, mutating nothing.
     * An index the user declines an ending confirm for is simply left out of the result, so the
     * gate and the apply pass see only the elements that really change.
     */
    private static List<PendingChange> decideChanges(
        UIAction.Reflectable action, boolean selected, @Nullable ScoreView score, ElementSelection selection) {

        var line = selection.line();
        var changes = new ArrayList<PendingChange>();

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
                var effect = line.findEndingReplacementEffect(i, replacement);
                Ending.EndingEffect compensation = Ending.EndingEffect.None.INSTANCE;

                switch (effect) {
                    case Ending.EndingEffect.Invalidate _ -> {
                        if (!EndingConfirms.confirmInvalidation(score)) {
                            continue;
                        }
                        // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
                    }
                    case Ending.EndingEffect.CompensateEnd compensateEnd -> {
                        if (!EndingConfirms.confirmCompensateEnd(score, compensateEnd)) {
                            continue;
                        }
                        compensation = compensateEnd;
                    }
                    case Ending.EndingEffect.CompensateSplit compensateSplit -> {
                        if (!EndingConfirms.confirmCompensateSplit(score, compensateSplit, replacement.getType())) {
                            continue;
                        }
                        compensation = compensateSplit;
                    }
                    case Ending.EndingEffect.None _ -> {}
                }

                changes.add(new PendingChange(i, replacement, compensation));
            } else if (action instanceof UIAction.ElementModifiable modifiable) {
                // Safe on a detached clone: applyToElement takes the element as a parameter and
                // touches nothing else.
                var standIn = element.clone();
                modifiable.applyToElement(standIn, selected);
                changes.add(new PendingChange(i, standIn, Ending.EndingEffect.None.INSTANCE));
            }
        }

        return changes;
    }

    /**
     * The post-change state of every element this modification touches, as
     * {@link AccidentalReconciliation} describes it. These are the notes the user changed
     * deliberately, so they are never materialized themselves — only the notes that inherit
     * their context are.
     */
    private static List<AccidentalReconciliation.IntendedChange> intendedChanges(List<PendingChange> changes) {
        var intended = new ArrayList<AccidentalReconciliation.IntendedChange>(changes.size());

        for (var change : changes) {
            var standIn = change.standIn();
            intended.add(new AccidentalReconciliation.IntendedChange(
                change.index(), standIn.getAccidental(), standIn.getStaffPosition()));
        }

        return intended;
    }

    /**
     * This action's changes, described for the restatement prompt: each changed note's accidental
     * now, and the one its stand-in will carry instead — which is none for a toggle-off or for a
     * replacement that bears no accidental.
     *
     * <p>The staff position is the live note's, which is the position the accidental being removed
     * was written at. An in-place modification never moves it, but taking it from the stand-in
     * would state the wrong thing if one ever did.
     */
    private static List<AccidentalRestatements.EditedNote> editedNotes(
        Line line, List<PendingChange> changes) {

        var edited = new ArrayList<AccidentalRestatements.EditedNote>(changes.size());

        for (var change : changes) {
            var element = line.getElement(change.index());

            edited.add(new AccidentalRestatements.EditedNote(
                change.index(),
                element.getStaffPosition(),
                element.getAccidental(),
                change.standIn().getAccidental()));
        }

        return edited;
    }

    /**
     * Pass 2 — whether the line still fits once the changes and the accidentals they force are
     * in place. Mutates nothing: the projection is built from stand-ins and clones, so a refusal
     * leaves every live element as it was.
     */
    private static boolean fitsAfterModification(
        Line line,
        List<PendingChange> changes,
        List<AccidentalReconciliation.AccidentalChange> accidentalChanges,
        @Nullable ScoreView score) {

        var effectiveCount = line.effectiveElementCount();

        // Nothing but the auto-maintained terminal barline, which layout pins flush-right: there
        // is no chain to solve and nothing the change can push past the margin.
        if (effectiveCount == 0) {
            return true;
        }

        var projected = new ArrayList<StaffElement>(effectiveCount);

        for (var i = 0; i < effectiveCount; i++) {
            projected.add(line.getElement(i));
        }

        for (var change : changes) {
            // The auto-maintained terminal barline is not in the projection: layout pins it
            // flush-right, and its column is built from the live element.
            if (change.index() < effectiveCount) {
                projected.set(change.index(), change.standIn());
            }
        }

        // Accidental width is a layout input, so the gate has to measure the reconciled
        // accidentals — on clones, because the live notes must stay untouched until the gate
        // has accepted.
        for (var accidentalChange : accidentalChanges) {
            var index = line.getElementIndex(accidentalChange.note());

            if (index < effectiveCount) {
                var note = accidentalChange.note().clone();
                note.setAccidental(accidentalChange.accidental());
                projected.set(index, note);
            }
        }

        var lyricRenderMetrics = (score != null) ? score.findLyricRenderMetrics() : null;

        return InsertionSpacingCalculator.calculateModification(line, projected, lyricRenderMetrics)
            .fitsWithinLine(line.getSong().getLineWidthSs());
    }

    // Validates beam and tuplet spans after batch element replacement.
    //
    // Tie repair is omitted because Line.setElement already does it, on every
    // path: Tie.isInvalidatedByReplacement removes exactly the ties a replacement
    // makes illegal. Independently, no replacement reachable from this call site
    // can invalidate a tie in the first place — the replacement preserves pitch
    // and rest-ness, grace notes are disabled in select mode via
    // Flag.DISABLE_IN_SELECT_MODE, and ElementModifiable actions do not touch
    // element type — so nothing here relies on that second argument holding.
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
            // Grace notes are exempt: they are not beam members, so a beam legitimately
            // passes over one (refs #592).
            var hasInteriorInvalid = false;

            for (var i = newStart; i <= newEnd; i++) {
                var type = line.getElement(i).getType();

                if (!type.isBeamable() && !type.isGraceNote()) {
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
            // A decoration selection nulls out the element selection, but it is still a
            // selection — freeze the action states rather than restoring them as if the
            // selection had been cleared.
            if (hasDecorationSelection()) {
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
     */
    private void invalidateSelectionCaches() {
        contentCacheSelection = null;
        applicabilityCacheSelection = null;
        applicabilityCache.clear();
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
        var line = getActiveLine();

        if (line != null && message.getLine() == line
                && hasDecorationSelection()
                && revalidateDecorationSelection()) {
            // A decoration selection carries no ElementSelection for getSelection() to
            // compare below, so undo/redo of a mutation on this line — which can shift
            // indices or remove the selected decoration outright — would otherwise go
            // unnoticed and leave the selection pointing at the wrong (or a dead) decoration.
            // The selection was just cleared: force a fresh reflection so any decoration
            // toolbar action that was showing selected/enabled resets to its default state.
            lastReflectedSelection = null;
            triggerReflection();
        }

        var selection = getSelection();

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
        if (isLineSelected()) {
            // Null so the next content selection always re-reflects, even when its span
            // happens to equal the synthesized full-line span.
            lastReflectedSelection = null;

            for (var reflectable : actions) {
                reflectable.setSelected(false);
            }

            updateGraceNoteActionEnabled(false);
            return;
        }

        var selection = getSelection();

        // Selection cleared
        if (selection == null) {
            lastReflectedSelection = null;

            if (getSelectedTarget() instanceof HitTarget.Slide(var owner)) {
                reflectSlideSelection(owner);
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
    private void reflectSlideSelection(StaffElement element) {
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
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(!isInSelectMode() && hasGraceNote);
    }
}
