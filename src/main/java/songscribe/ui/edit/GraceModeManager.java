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

package songscribe.ui.edit;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.LyricRun;
import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.layout.ElementColumnBuilder;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.selection.SelectionCoordinator;

import static songscribe.message.MessageCenter.post;

/**
 * Manages the grace note pairing state machine.
 *
 * <p>A mouse-down while a grace action is selected moves {@code INACTIVE} to {@code GRACE_NOTE},
 * provided the room check passes. From there a drag-left or Escape cancels; a drag-right onto an
 * existing next note pairs directly ({@code GRACE_NOTE_PAIRED}); and a click, or a drag-right with
 * no next note, enters {@code GRACE_NOTE_INSERT} to preview a host note the user has yet to create.
 * Entering that state computes the host-note insertion preview once and holds it fixed until the
 * user clicks or cancels. {@code GRACE_NOTE_INSERT} either cancels (click miss or Escape) or commits to
 * {@code GRACE_NOTE_PAIRED} on a left-click or line change. Both cancel and success paths run
 * through {@code FINISH}, which tears the interaction down and returns to {@code INACTIVE}.
 */
public final class GraceModeManager {

    public enum State {
        INACTIVE,
        GRACE_NOTE,
        GRACE_NOTE_INSERT,
        GRACE_NOTE_PAIRED,
        FINISH
    }

    public static final int GRACE_SLOP_PX = 4;
    public static final long MIN_DRAG_MILLIS = 500;

    @Nullable
    private static GraceModeManager instance = null;

    // Dependencies
    private final SelectionCoordinator selectionCoordinator;

    // State
    private State state = State.INACTIVE;

    @Nullable
    private StaffElement graceNote;

    private int graceNoteIndex = -1;

    @Nullable
    private Line graceLine;

    @Nullable
    private LineComponent graceLineComponent;

    // Cached host-note insertion preview. Computed once on entry to GRACE_NOTE_INSERT
    // (the locked x, grace index, preview element and layout are all fixed until the
    // user clicks or cancels, so recomputing per paint frame would be wasted work).
    private InsertionSpacingCalculator.@Nullable InsertionResult cachedHostInsertion;

    @Nullable
    private Point mouseDownPoint;

    private long mouseDownTime;

    // Set on entry to GRACE_NOTE_INSERT to suppress the mouseClicked event that
    // fires as part of the same click cycle that triggered the transition.
    private boolean justEnteredInsert = false;

    // True when a drag-left exceeds GRACE_SLOP_PX, indicating the grace note
    // will be cancelled on mouse-up. Used by LineRenderer to draw the note in red.
    private boolean pendingCancel = false;

    // True when a drag-right exceeds GRACE_SLOP_PX past the grace note's right
    // edge and an eligible host note exists. A preview glissando is drawn while true.
    private boolean pendingConnect = false;

    // True only while the untracked grace-note insertion is in flight, telling the
    // insertion path to leave the repairs around the insertion point to us.
    private boolean deferringInsertionRepairs = false;

    // True when the insertion path took that offer, so the pairing owes the repairs.
    private boolean insertionRepairsOwed = false;

    public GraceModeManager(SelectionCoordinator selectionCoordinator) {
        this.selectionCoordinator = selectionCoordinator;
        instance = this;
    }

    /**
     * Returns whether any GraceModeManager instance is currently active.
     * Used by UIAction.enableFromGraceModeState() to check grace mode status.
     */
    public static boolean isActive() {
        return instance != null && instance.isInProgress();
    }

    public boolean isInProgress() {
        return state != State.INACTIVE;
    }

    /**
     * Returns whether the given element is a grace note flagged for cancellation.
     * Used by {@code LineRenderer.getElementColor()} to draw the note in red.
     */
    public static boolean isPendingCancel(StaffElement element) {
        return instance != null && instance.pendingCancel && element == instance.graceNote;
    }

    /**
     * Returns whether the given element is a grace note with a pending drag-right connect.
     * Used by {@code LineRenderer.renderSlides()} to draw a preview glissando — render-only
     * state, so the drag never mutates the element's slide (see {@code mouseDragged}).
     */
    public static boolean isPendingConnect(StaffElement element) {
        return instance != null && instance.pendingConnect && element == instance.graceNote;
    }

    /**
     * Returns whether any grace note has a pending drag-right connect, regardless of
     * element. Lets {@code LineRenderer} skip its per-element scan in the common case
     * of no active grace-mode drag.
     */
    public static boolean hasPendingConnect() {
        return instance != null && instance.pendingConnect;
    }

    /**
     * Hands the neighbor repairs around the insertion now in flight over to grace mode, and
     * returns whether it took them. True only while grace mode inserts its grace note, which
     * it does with mutation tracking suspended: a repair made there would be applied but never
     * recorded, so undo could not put back the broken lyric chain or the stripped glissando.
     * Grace mode re-runs the repairs inside the bracket that makes the pairing undoable — see
     * {@link LyricRun#repairNeighborsAfterUntrackedInsertion}.
     *
     * <p>Asking and taking are one call because they must never come apart: a caller that
     * skipped the repairs without recording that it had would drop them silently, with undo
     * left unable to restore the neighbors and nothing to point at the cause. Only an insertion
     * path calls this, and only where it is about to insert — an append or a replacement
     * disturbs no neighbor, so it asks nothing and owes nothing.
     */
    public static boolean deferInsertionRepairs() {
        if (instance == null || !instance.deferringInsertionRepairs) {
            return false;
        }

        instance.insertionRepairsOwed = true;
        return true;
    }

    /**
     * Returns the rightmost X (in pixels) at which a drag is considered a cancel gesture,
     * i.e. the grace note's left edge minus {@link #GRACE_SLOP_PX}. Returns -1 if
     * no grace note is active or layout is unavailable.
     */
    public static int getCancelThresholdPx() {
        return instance != null ? instance.internalGetCancelThresholdPx() : -1;
    }

    /**
     * Returns the leftmost X (in pixels) at which a drag is considered a connect gesture,
     * i.e. the grace note's right edge plus {@link #GRACE_SLOP_PX}. Returns -1 if
     * no grace note is active or layout is unavailable.
     */
    public static int getConnectThresholdPx() {
        return instance != null ? instance.internalGetConnectThresholdPx() : -1;
    }

    /**
     * Returns the locked insertion x-position in staff spaces for the host note.
     * Computed dynamically from the grace note's current layout position plus
     * one column width to the right. Returns 0 if the grace note or layout is null.
     */
    public double getLockedInsertionXSs() {
        if (graceNote == null || graceLine == null || graceLineComponent == null) {
            return 0;
        }

        var layout = graceLineComponent.getLayoutResult();

        if (layout == null) {
            return 0;
        }

        var graceColumn = layout.getElementColumn(graceNote);

        if (graceColumn == null) {
            return 0;
        }

        var previewElement = EditModeManager.getPreviewElement();
        var hostLeftExtentSs = previewElement != null
            ? ElementColumnBuilder.calculateLeftExtentSs(previewElement)
            : 0;

        return graceColumn.getXSs()
            + graceColumn.getRightExtentSs()
            + HorizontalSpacingCalculator.GRACE_HOST_REST_SS
            + Math.abs(hostLeftExtentSs);
    }

    /**
     * Returns the {@link LineComponent} that contains the grace note, or null if not in progress.
     */
    public @Nullable LineComponent getGraceLineComponent() {
        return graceLineComponent;
    }

    /**
     * Returns the index at which the host note will be inserted — the slot immediately
     * after the grace note. This is the single definition of the host slot; callers must
     * not recompute it from the grace note's index.
     * <p>
     * Valid throughout grace mode, i.e. whenever {@link #isInProgress()} is true: the grace
     * note's index is recorded before the state leaves {@link State#INACTIVE} and is only
     * cleared after it returns there.
     */
    public int getHostInsertionIndex() {
        return graceNoteIndex + 1;
    }

    /**
     * Returns the index of the grace note whose host-note insertion preview is showing on
     * {@code lineComponent}, or -1 when no host preview is showing there.
     * <p>
     * Gates the preview glissando between the already-inserted grace note and the host ghost
     * (refs #650): while the host is only a preview the grace note carries no slide of its own,
     * so the connecting line is render-only state derived from here rather than from the DOM.
     * The index rides along with the predicate so {@link #graceNoteIndex} needs no accessor of
     * its own outside this package.
     */
    public int hostPreviewGraceIndexOn(LineComponent lineComponent) {
        return isShowingHostPreviewOn(lineComponent) ? graceNoteIndex : -1;
    }

    /**
     * Returns whether the host-note insertion preview is showing on {@code lineComponent}, i.e.
     * the grace note is waiting for the host the user is positioning.
     */
    boolean isShowingHostPreviewOn(LineComponent lineComponent) {
        return state == State.GRACE_NOTE_INSERT && lineComponent == graceLineComponent;
    }

    /**
     * Returns the host-note insertion preview cached on entry to {@link State#GRACE_NOTE_INSERT}.
     * Null if not in that state or the host note would not fit on the line (in which case
     * {@link #enterGraceNoteInsert} would have aborted and reset the state).
     */
    public InsertionSpacingCalculator.@Nullable InsertionResult getHostInsertionPreview() {
        return cachedHostInsertion;
    }

    /**
     * Computes the host-note insertion preview using the current locked state.
     * Returns null if state is incomplete (preview/line/component missing) or the
     * host note would not fit on the line.
     */
    private InsertionSpacingCalculator.@Nullable InsertionResult computeHostInsertion() {
        if (graceLine == null || graceLineComponent == null) {
            return null;
        }

        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        var result = InsertionSpacingCalculator.calculateInsertion(
            graceLine, previewElement, getHostInsertionIndex(),
            graceLineComponent.getLayoutResult(), graceLineComponent.getLyricRenderMetrics()
        );

        if (!result.fitsWithinLine(graceLine.getSong().getLineWidthSs())) {
            return null;
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Public event methods
    // -------------------------------------------------------------------------

    public boolean mousePressed(LineComponent lineComponent, MouseEvent e) {
        if (state == State.GRACE_NOTE_INSERT) {
            return true;  // Consume — prevent selection/drag handling during insert phase
        }

        if (state != State.INACTIVE) {
            return false;
        }

        if (e.getButton() != MouseEvent.BUTTON1) {
            return false;
        }

        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null || previewElement.getType() != ElementType.GRACE_QUAVER) {
            return false;
        }

        var line = lineComponent.getLine();

        if (line == null) {
            return false;
        }

        var xIndex = PreviewElementManager.getCurrentXIndex();

        if (line.isInsideGraceHostPair(xIndex)) {
            return true;
        }

        if (xIndex < 0) {
            return false;
        }

        if (!InsertionSpacingCalculator.hasRoomForGraceNote(
                line, xIndex, lineComponent.getLayoutResult(), lineComponent.getLyricRenderMetrics())) {
            OptionDialogs.showErrorMessage(
                SwingUtilities.getWindowAncestor(lineComponent),
                Strings.ALERT_TITLE_GRACE_NOTE_ERROR,
                Strings.ERROR_GRACE_NOTE_NO_ROOM
            );
            return true;
        }

        enterGraceNote(lineComponent, e);
        return true;
    }

    public boolean mouseReleased(LineComponent lineComponent, MouseEvent e) {
        if (state == State.GRACE_NOTE_INSERT) {
            return true;  // Consume — actual click logic is in mouseClicked
        }

        if (state != State.GRACE_NOTE) {
            return false;
        }

        if (mouseDownPoint == null || graceNote == null || graceLine == null) {
            abort();
            return true;
        }

        var dx = e.getXOnScreen() - mouseDownPoint.x;
        var dy = e.getYOnScreen() - mouseDownPoint.y;
        var isDrag = System.currentTimeMillis() - mouseDownTime >= MIN_DRAG_MILLIS;

        // Click (< slop in both axes, or too fast to be a drag): transition to GRACE_NOTE_INSERT.
        // Suppress the mouseClicked that Swing fires as part of the same click cycle.
        if (!isDrag || (Math.abs(dx) < GRACE_SLOP_PX && Math.abs(dy) < GRACE_SLOP_PX)) {
            enterGraceNoteInsert(true);
            return true;
        }

        // Drag left past notehead: cancel
        if (pendingCancel) {
            abort();
            return true;
        }

        // Drag right past right edge with eligible host note: connect
        if (pendingConnect) {
            enterGraceNotePaired(true, lineComponent);
            return true;
        }

        // Drag without connecting — treat as click (no mouseClicked follows a drag)
        enterGraceNoteInsert(false);
        return true;
    }

    public boolean mouseMoved(LineComponent lineComponent, MouseEvent e) {
        if (state != State.GRACE_NOTE) {
            return false;
        }

        PreviewElementManager.hidePreviewElement(false);
        return true;
    }

    public boolean mouseDragged(LineComponent lineComponent, MouseEvent e) {
        if (state == State.GRACE_NOTE_INSERT) {
            return true;  // No drag behavior in insert phase
        }

        if (state != State.GRACE_NOTE) {
            return false;
        }

        PreviewElementManager.hidePreviewElement(false);

        var wasPendingCancel = pendingCancel;
        var wasPendingConnect = pendingConnect;
        var isDrag = System.currentTimeMillis() - mouseDownTime >= MIN_DRAG_MILLIS;
        pendingCancel = isDrag && isMouseLeftOfGraceNote(e);
        pendingConnect = isDrag && !pendingCancel
            && isMouseRightOfGraceNote(e) && hasEligibleHostNote();

        // The pending glissando is drawn from this flag by LineRenderer — the grace note's
        // slide state must not be touched here: an untracked mid-drag mutation would leak
        // into the before-state clone of the commit path's tracked SLIDE modification,
        // making undo of the connect step leave the glissando behind.
        if ((pendingCancel != wasPendingCancel || pendingConnect != wasPendingConnect)
            && graceLineComponent != null) {
            graceLineComponent.repaint();
        }

        return true;
    }

    public boolean mouseClicked(LineComponent lineComponent, MouseEvent e) {
        if (!isInProgress()) {
            return false;
        }

        if (state != State.GRACE_NOTE_INSERT) {
            // Active in another state — consume to prevent normal insertion
            return true;
        }

        // Suppress the mouseClicked that fires as part of the same click cycle
        // that triggered the GRACE_NOTE -> GRACE_NOTE_INSERT transition.
        if (justEnteredInsert) {
            justEnteredInsert = false;
            return true;
        }

        if (e.getButton() != MouseEvent.BUTTON1) {
            return true;
        }

        // Cancel if click is on a different line
        if (lineComponent != graceLineComponent) {
            abort();
            return true;
        }

        // Cancel if click is >= GRACE_SLOP_PX to the left of the grace note's left edge
        if (isMouseLeftOfGraceNote(e)) {
            abort();
            return true;
        }

        // enterGraceNotePaired validates the pitch, records the retroactive grace-note
        // insertion, inserts the host note, and connects the glissando inside a single
        // modification bracket — one undoable step for the whole pairing.
        enterGraceNotePaired(false, lineComponent);
        return true;
    }

    public void keyPressed(KeyEvent e) {
        if (!isInProgress()) {
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            abort();
        }

        // Duration/embellishment keys pass through to normal handling
    }

    // -------------------------------------------------------------------------
    // Private transition methods
    // -------------------------------------------------------------------------

    private void enterGraceNote(
        LineComponent lineComponent,
        MouseEvent e
    ) {
        // Save action states before modifying anything
        selectionCoordinator.getActionReflector().saveActionStates();

        var line = lineComponent.getLine();

        if (line == null) {
            abort();
            return;
        }

        // Insert the grace note via the existing insertion code path, but WITHOUT
        // recording a mutation: a lone grace note is not undoable. It only becomes part
        // of an undo step once it is paired with a host note (see enterGraceNotePaired,
        // which retroactively records the insertion into the pairing's bracket).
        // The insertion's repairs to the neighboring elements would be lost the same way, so
        // they are deferred to the pairing bracket (see enterGraceNotePaired), not made here.
        deferringInsertionRepairs = true;

        try {
            // forceInsert=true: a grace note must always be inserted before whatever the cursor
            // is over, never replace it — clicking directly on an existing note's head would
            // otherwise take handleClick's replace branch and silently destroy that note, which
            // enterGraceNotePaired below has no way to recover on undo (it only records an
            // ElementInsertion).
            line.getSong().withoutMutationTracking(
                () -> PreviewElementManager.handleClick(lineComponent, true));
        } finally {
            deferringInsertionRepairs = false;
        }

        // No SongDidChangeNotification fires while tracking is suspended, so invalidate
        // the line's layout directly to lay out and render the new grace note.
        lineComponent.invalidateLayout();

        graceLineComponent = lineComponent;
        graceLine = line;

        // The grace note is the most recently inserted element at the current x index
        var insertedIndex = PreviewElementManager.getCurrentXIndex();

        if (!line.hasIndex(insertedIndex)) {
            // Insertion failed (e.g. triplet boundary)
            abort();
            return;
        }

        // handleClick inserts at currentXIndex, but after insertion the element
        // count has increased. The inserted element is at the index that was
        // currentXIndex before the click. Since handleClick may have appended
        // (currentXIndex == elementCount before insert), we need to check the
        // last element in that case.
        graceNoteIndex = Math.min(insertedIndex, line.elementCount() - 1);
        graceNote = line.getElement(graceNoteIndex);

        // Deselect all embellishment actions. This must precede the duration selection
        // below: that selection synchronously rebuilds the preview element, decorating it
        // from whatever toggles are selected at that moment. Clearing afterwards would
        // leave the already-built preview — the very object handleClick inserts as the
        // host — carrying the grace note's decorations.
        Actions.clearNoteDecorations();

        // Select crotchet duration for the host note via the action group so the
        // grace note button is properly deselected.
        Actions.DURATION_ACTION_GROUP.select(Actions.QUARTER_NOTE_ACTION, this);

        // Set state before posting the message so that enableFromGraceModeState()
        // sees the correct state when disabling DISABLE_IN_GRACE_MODE actions.
        state = State.GRACE_NOTE;

        // Post message to disable flagged actions
        post(new GraceModeStateDidChangeNotification(true));

        // Hide the preview element that was shown by the DurationSelectedMessage
        // handler triggered by perform() above. It will be re-shown at the correct
        // locked x-position when entering GRACE_NOTE_INSERT.
        PreviewElementManager.hidePreviewElement(false);

        // Record mouse down point and time for drag detection (screen coords for slop)
        mouseDownPoint = new Point(e.getXOnScreen(), e.getYOnScreen());
        mouseDownTime = System.currentTimeMillis();
    }

    private void enterGraceNoteInsert(boolean suppressNextClick) {
        state = State.GRACE_NOTE_INSERT;
        justEnteredInsert = suppressNextClick;

        // Compute the locked x-position. Returns 0 if the grace note's layout is
        // unavailable (e.g., layout in progress), which signals that we should abort.
        var lockedXSs = getLockedInsertionXSs();

        if (lockedXSs == 0) {
            abort();
            return;
        }

        // graceLineComponent is guaranteed non-null here: getLockedInsertionXSs()
        // returns 0 when it is null, so reaching this point means it is non-null.
        if (graceLineComponent == null) {
            throw new IllegalStateException("graceLineComponent must be non-null here");
        }

        // Check whether the host note will fit before locking into insert mode, and
        // cache the result so per-frame preview rendering does not repeat the math.
        var hostInsertion = computeHostInsertion();

        if (hostInsertion == null) {
            // Save the component reference before abort() clears graceLineComponent.
            var component = graceLineComponent;
            abort();
            OptionDialogs.showErrorMessage(
                SwingUtilities.getWindowAncestor(component),
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_GRACE_NOTE_HOST_NO_ROOM
            );
            return;
        }

        cachedHostInsertion = hostInsertion;
        PreviewElementManager.restorePreviewElement(graceLineComponent);
    }

    /**
     * Commits the grace-note pairing. When {@code connectNext} is true the grace note
     * connects to the already-existing next pitched note (drag-right); when false a new
     * host note is inserted at the locked x position (click in GRACE_NOTE_INSERT).
     */
    private void enterGraceNotePaired(boolean connectNext, LineComponent lineComponent) {
        var note = graceNote;
        var line = graceLine;
        var component = graceLineComponent;

        if (note == null || line == null || component == null) {
            abort();
            return;
        }

        var hostNoteIndex = getHostInsertionIndex();

        // Determine the host note's pitch up front. For a drag-right connection the host
        // already exists on the line; for a host-note insertion the pitch comes from the
        // preview element about to be inserted. Validating before opening the modification
        // bracket lets a same-pitch pairing be rejected leaving no undo step.
        int hostStaffPosition;

        if (connectNext) {
            if (hostNoteIndex >= line.elementCount()) {
                abort();
                return;
            }

            hostStaffPosition = line.getElement(hostNoteIndex).getStaffPosition();
        } else {
            var previewElement = EditModeManager.getPreviewElement();

            if (previewElement == null) {
                abort();
                return;
            }

            hostStaffPosition = previewElement.getStaffPosition();
        }

        // Grace note and host note must have different pitches.
        if (note.getStaffPosition() == hostStaffPosition) {
            // abort() removes the grace note before showErrorMessage pumps the EDT, so no
            // intervening repaint renders its (never-added) connecting slide.
            abort();

            OptionDialogs.showErrorMessage(
                SwingUtilities.getWindowAncestor(component),
                Strings.ALERT_TITLE_GRACE_NOTE_ERROR,
                Strings.ERROR_GRACE_NOTE_SAME_PITCH
            );

            return;
        }

        // A single modification bracket coalesces the whole pairing into one undoable
        // SongDidChangeNotification: the retroactive grace-note insertion, the optional
        // host-note insertion, and the connecting glissando.
        line.withModification(() -> {
            // The grace note was inserted without tracking; record its insertion now so
            // undo removes it too. The element is already on the line, so the mutator is
            // empty (the PitchShifter pattern) — the record exists only to drive undo/redo.
            line.applyChange(new ElementInsertion(line, graceNoteIndex, note), () -> {});

            // The repairs the insertion deferred, recorded here so undo can put back the
            // syllabic and melisma chains the grace note broke, and the connecting glissando
            // it took away from the note in front of it. They run before the host insertion
            // below, while the element after the grace note is still the one the grace note
            // was inserted in front of.
            if (insertionRepairsOwed) {
                line.repairNeighborsAfterUntrackedInsertion(graceNoteIndex);
            }

            if (!connectNext) {
                // Insert the host note at the locked x position. The preview element was
                // built undecorated on entry to grace mode (see enterGraceNote), so the
                // host arrives as a plain note.
                PreviewElementManager.handleClick(lineComponent, true);
            }

            var hostNote = line.getElement(hostNoteIndex);

            // Connect the grace note to the host note with a connecting glissando.
            line.modifyElement(graceNoteIndex, ElementField.SLIDE, note::setGlissando);

            if (connectNext) {
                // The syllable of a grace-host pair belongs to the grace note, so an
                // existing host lyric moves onto it. A host note inserted just above has
                // no lyric to hand over.
                line.transferLyrics(hostNoteIndex, graceNoteIndex);
            }

            // Runs after the glissando so the pair is established and the sync converges
            // to a melisma running from the grace across its host.
            line.syncGraceHostMelisma(graceNoteIndex);

            // Mirror the host note's attributes onto the toolbar.
            selectionCoordinator.getActionReflector().reflectElement(hostNote);
        });

        commit();
    }

    /**
     * Cancels the in-progress grace operation, leaving no undo step. The grace note was
     * inserted without mutation tracking, so it is removed the same way — a cancelled
     * operation must not appear in the undo history.
     */
    private void abort() {
        var line = graceLine;
        var component = graceLineComponent;
        var idx = graceNoteIndex;

        if (graceNote != null && line != null && idx != -1) {
            line.getSong().withoutMutationTracking(() -> line.removeElement(idx));

            // No SongDidChangeNotification fires while tracking is suspended, so
            // invalidate the line's layout directly to reflect the removal.
            if (component != null) {
                component.invalidateLayout();
            }
        }

        resetState();
    }

    /**
     * Finalizes a successful grace-note pairing. The pairing's mutations were already
     * recorded by {@link #enterGraceNotePaired}'s modification bracket; this only tears
     * down the grace-mode UI state.
     */
    private void commit() {
        resetState();
    }

    /**
     * Restores action states and clears all grace-mode tracking fields. Shared teardown
     * for {@link #abort()} and {@link #commit()}.
     */
    private void resetState() {
        // Restore only DISABLE_IN_GRACE_MODE actions to their pre-grace-mode state.
        // All other actions (duration, embellishments, etc.) keep their current state.
        selectionCoordinator.getActionReflector().restoreActionStatesWithFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE);

        // Set INACTIVE before posting the message so that enableFromGraceModeState()
        // sees the correct state when re-enabling DISABLE_IN_GRACE_MODE actions.
        state = State.INACTIVE;

        post(new GraceModeStateDidChangeNotification(false));

        // Re-enable the grace note action (it was deselected when entering grace mode
        // and the GraceModeStateChangedMessage handler may have left it disabled).
        // Use invokeLater to ensure the setEnabled call (which can trigger button
        // repaints via property change listeners) always runs on the EDT.
        SwingUtilities.invokeLater(() -> Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(true));

        graceNote = null;
        graceNoteIndex = -1;
        graceLine = null;
        graceLineComponent = null;
        cachedHostInsertion = null;
        mouseDownPoint = null;
        mouseDownTime = 0;
        justEnteredInsert = false;
        pendingCancel = false;
        pendingConnect = false;
        deferringInsertionRepairs = false;
        insertionRepairsOwed = false;

        // The host preview's connecting glissando is gated on the state cleared just above, and
        // nothing else takes the overlays down on the way out of grace mode: a commit would leave
        // the preview line doubling the real glissando it just became, and an abort would leave
        // one running to a grace note that has already been removed.
        PreviewElementManager.previewElementDidChange();
    }

    // -------------------------------------------------------------------------
    // Private helper methods
    // -------------------------------------------------------------------------

    /**
     * Returns whether the mouse is at least {@link #GRACE_SLOP_PX} to the left
     * of the grace note's left edge. Used for cancel detection during drag and
     * click in the insert phase.
     */
    private boolean isMouseLeftOfGraceNote(MouseEvent e) {
        if (graceLineComponent == null) {
            return false;
        }

        var threshold = internalGetCancelThresholdPx();
        // The threshold is a fixed-scale document pixel; convert the view-pixel event x
        // to document pixels before comparing.
        var mouseXDocPx = graceLineComponent.getScoreView().getViewScale().toDocPx(new ViewPx(e.getX())).roundedPx();
        return threshold >= 0 && mouseXDocPx <= threshold;
    }

    /**
     * Returns whether the mouse is at least {@link #GRACE_SLOP_PX} to the right
     * of the grace note's right edge. Used for connect detection during drag.
     */
    private boolean isMouseRightOfGraceNote(MouseEvent e) {
        if (graceLineComponent == null) {
            return false;
        }

        var threshold = internalGetConnectThresholdPx();
        // The threshold is a fixed-scale document pixel; convert the view-pixel event x
        // to document pixels before comparing.
        var mouseXDocPx = graceLineComponent.getScoreView().getViewScale().toDocPx(new ViewPx(e.getX())).roundedPx();
        return threshold >= 0 && mouseXDocPx >= threshold;
    }

    private int internalGetCancelThresholdPx() {
        if (graceNote == null || graceLineComponent == null) {
            return -1;
        }

        var layout = graceLineComponent.getLayoutResult();

        if (layout == null) {
            return -1;
        }

        var graceXSs = layout.getElementXSs(graceNote);
        return DocumentScale.ssToRoundedPx(graceXSs) - GRACE_SLOP_PX;
    }

    private int internalGetConnectThresholdPx() {
        if (graceNote == null || graceLineComponent == null) {
            return -1;
        }

        var layout = graceLineComponent.getLayoutResult();

        if (layout == null) {
            return -1;
        }

        var graceColumn = layout.getElementColumn(graceNote);

        if (graceColumn == null) {
            return -1;
        }

        var rightEdgeSs = graceColumn.getXSs() + graceColumn.getRightExtentSs();
        return DocumentScale.ssToRoundedPx(rightEdgeSs) + GRACE_SLOP_PX;
    }

    /**
     * Returns whether the grace note has an eligible host note immediately
     * following it (a pitched note at the next index).
     */
    private boolean hasEligibleHostNote() {
        if (graceNote == null || graceLine == null) {
            return false;
        }

        var nextIndex = getHostInsertionIndex();
        return nextIndex < graceLine.elementCount()
            && graceLine.getElement(nextIndex).getType().isPitchedNote();
    }
}
