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

import module java.desktop;
import static songscribe.message.MessageCenter.post;

import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.layout.ElementColumnBuilder;
import songscribe.ui.layout.HorizontalSpacingCalculator;
import songscribe.ui.layout.InsertionSpacingCalculator;
import songscribe.ui.layout.ScaleContext;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Manages the grace note pairing state machine.
 * <pre>
 *                          mouseDown + grace action selected
 *                          + room check passes
 *                     ┌─────────────────────────┐
 *                     │                         ▼
 *               ┌──────────┐            ┌─────────────┐
 *               │ INACTIVE │            │ GRACE_NOTE  │
 *               └──────────┘            └──────┬──────┘
 *                     ▲                        │
 *                     │              ┌─────────┼──────────┐
 *                     │              │         │          │
 *                     │         drag-left   click/    drag-right
 *                     │         or Esc     no next    + next note
 *                     │              │      note       exists
 *                     │              │         │          │
 *                     │              │         ▼          │
 *                     │              │  ┌────────────┐    │
 *                     │              │  │ GRACE_NOTE │    │
 *                     │              │  │  _INSERT   │    │
 *                     │              │  └─────┬──────┘    │
 *                     │              │        │           │
 *                     │              │   click│left-click │
 *                     │              │   Esc  │line change│
 *                     │              │   ┌────┼────┐      │
 *                     │              │   │    │    │      │
 *                     │              ▼   ▼    ▼    │      ▼
 *                     │         ┌──────────┐  ┌──────────────┐
 *                     │         │ FINISH   │  │ GRACE_NOTE   │
 *                     │         │ (cancel) │  │   _PAIRED    │
 *                     │         └────┬─────┘  └──────┬───────┘
 *                     │              │               │
 *                     │              │               ▼
 *                     │              │         ┌──────────┐
 *                     │              └────────►│ FINISH   │
 *                     │                        │(success) │
 *                     │                        └────┬─────┘
 *                     │                             │
 *                     └─────────────────────────────┘
 * </pre>
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
    private final EditModeManager editModeManager;
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

    public GraceModeManager(
        EditModeManager editModeManager,
        SelectionCoordinator selectionCoordinator
    ) {
        this.editModeManager = editModeManager;
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

        var previewElement = editModeManager.getPreviewElement();
        var hostLeftExtentSs = previewElement != null
            ? ElementColumnBuilder.calculateLeftExtentSs(previewElement)
            : 0;

        return graceColumn.getXSs()
            + graceColumn.getRightExtentSs()
            + HorizontalSpacingCalculator.GRACE_NOTE_GAP_SS
            + Math.abs(hostLeftExtentSs);
    }

    /**
     * Returns the {@link LineComponent} that contains the grace note, or null if not in progress.
     */
    public @Nullable LineComponent getGraceLineComponent() {
        return graceLineComponent;
    }

    /**
     * Returns the index at which the host note will be inserted (= graceNoteIndex + 1).
     * Only meaningful when in {@link State#GRACE_NOTE_INSERT} state.
     */
    public int getHostInsertionIndex() {
        return graceNoteIndex + 1;
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

        var previewElement = editModeManager.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        var result = InsertionSpacingCalculator.calculateInsertion(
            graceLine, previewElement, graceNoteIndex + 1, graceLineComponent.getLayoutResult()
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

        var previewElement = editModeManager.getPreviewElement();

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

        if (!InsertionSpacingCalculator.hasRoomForGraceNote(line, xIndex, lineComponent.getLayoutResult())) {
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
            finish(true);
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
            finish(true);
            return true;
        }

        // Drag right past right edge with eligible host note: connect
        if (pendingConnect) {
            enterGraceNotePaired(true);
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

        hidePreviewAndSetDefaultCursor(lineComponent);
        return true;
    }

    public boolean mouseDragged(LineComponent lineComponent, MouseEvent e) {
        if (state == State.GRACE_NOTE_INSERT) {
            return true;  // No drag behavior in insert phase
        }

        if (state != State.GRACE_NOTE) {
            return false;
        }

        hidePreviewAndSetDefaultCursor(lineComponent);

        var wasPendingCancel = pendingCancel;
        var isDrag = System.currentTimeMillis() - mouseDownTime >= MIN_DRAG_MILLIS;
        pendingCancel = isDrag && isMouseLeftOfGraceNote(e);
        pendingConnect = isDrag && !pendingCancel
            && isMouseRightOfGraceNote(e) && hasEligibleHostNote();

        var hasGlissando = graceNote != null
            && graceNote.getGlissando() != null;

        if (pendingConnect && !hasGlissando && graceNote != null) {
            graceNote.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        } else if (!pendingConnect && hasGlissando && graceNote != null) {
            graceNote.removeGlissando();
        }

        if ((pendingCancel != wasPendingCancel || pendingConnect != hasGlissando)
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
            finish(true);
            return true;
        }

        // Cancel if click is >= GRACE_SLOP_PX to the left of the grace note's left edge
        if (isMouseLeftOfGraceNote(e)) {
            finish(true);
            return true;
        }

        // Coalesce the host-note insertion, the grace-note glissando connection,
        // and the subsequent enterGraceNotePaired/finish work into a single
        // SongDidChangeNotification.
        var line = graceLine;

        if (line == null) {
            return true;
        }

        line.withModification(() -> {
            // Insert the host note at the locked x position
            PreviewElementManager.handleClick(lineComponent, true);

            // Connect grace note to host note with a CONNECTED glissando
            var note = graceNote;

            if (note != null) {
                line.modifyElement(
                    graceNoteIndex,
                    ElementField.GLISSANDO,
                    () -> note.setGlissando(StaffElement.Glissando.Type.CONNECTED)
                );
            }

            enterGraceNotePaired(false);
        });
        return true;
    }

    public boolean keyPressed(KeyEvent e) {
        if (!isInProgress()) {
            return false;
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            finish(true);
            return true;
        }

        // Duration/embellishment keys pass through to normal handling
        return false;
    }

    // -------------------------------------------------------------------------
    // Private transition methods
    // -------------------------------------------------------------------------

    private void enterGraceNote(
        LineComponent lineComponent,
        MouseEvent e
    ) {
        // Save action states before modifying anything
        selectionCoordinator.saveActionStates();
        selectionCoordinator.setInSelectMode(false);

        // Insert the grace note via the existing insertion code path
        PreviewElementManager.handleClick(lineComponent);

        // Record state
        var line = lineComponent.getLine();

        if (line == null) {
            finish(true);
            return;
        }

        graceLineComponent = lineComponent;
        graceLine = line;

        // The grace note is the most recently inserted element at the current x index
        var insertedIndex = PreviewElementManager.getCurrentXIndex();

        if (insertedIndex < 0 || insertedIndex >= line.elementCount()) {
            // Insertion failed (e.g. triplet boundary)
            finish(true);
            return;
        }

        // handleClick inserts at currentXIndex, but after insertion the element
        // count has increased. The inserted element is at the index that was
        // currentXIndex before the click. Since handleClick may have appended
        // (currentXIndex == elementCount before insert), we need to check the
        // last element in that case.
        graceNoteIndex = Math.min(insertedIndex, line.elementCount() - 1);
        graceNote = line.getElement(graceNoteIndex);

        // Select crotchet duration for the host note via the action group so the
        // grace note button is properly deselected.
        Actions.DURATION_ACTION_GROUP.select(Actions.QUARTER_NOTE_ACTION, this);

        // Deselect all embellishment actions
        Actions.DOT_ACTION_GROUP.clearSelection();
        Actions.ACCIDENTAL_ACTION_GROUP.clearSelection();
        Actions.ARTICULATION_ACTION_GROUP.clearSelection();
        Actions.FERMATA_ACTION.setSelected(false);

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
            finish(true);
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
            finish(true);
            OptionDialogs.showErrorMessage(
                SwingUtilities.getWindowAncestor(graceLineComponent),
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_GRACE_NOTE_HOST_NO_ROOM
            );
            return;
        }

        cachedHostInsertion = hostInsertion;
        PreviewElementManager.restorePreviewElement(graceLineComponent);
    }

    private void enterGraceNotePaired(boolean connectNext) {
        var note = graceNote;
        var line = graceLine;

        if (note == null || line == null) {
            finish(true);
            return;
        }

        var hostNoteIndex = graceNoteIndex + 1;

        if (hostNoteIndex >= line.elementCount()) {
            finish(true);
            return;
        }

        var hostNote = line.getElement(hostNoteIndex);

        line.withModification(() -> {
            // Grace note and host note must have different pitches
            if (note.getStaffPosition() == hostNote.getStaffPosition()) {
                // If the host note was just inserted (not connecting to existing),
                // remove it before cancelling.
                if (!connectNext) {
                    line.removeElement(hostNoteIndex);
                }

                OptionDialogs.showErrorMessage(
                    SwingUtilities.getWindowAncestor(graceLineComponent),
                    Strings.ALERT_TITLE_GRACE_NOTE_ERROR,
                    Strings.ERROR_GRACE_NOTE_SAME_PITCH
                );

                finish(true);
                return;
            }

            if (connectNext) {
                // Coming from GRACE_NOTE (drag-right): connect to the existing next pitched note.
                // When coming from GRACE_NOTE_INSERT, the glissando was already added in mouseClicked.
                line.modifyElement(
                    graceNoteIndex,
                    ElementField.GLISSANDO,
                    () -> note.setGlissando(StaffElement.Glissando.Type.CONNECTED)
                );
            }

            // Mirror the host note's attributes onto the toolbar
            selectionCoordinator.reflectElement(hostNote);

            finish(false);
        });
    }

    private void finish(boolean cancel) {
        if (cancel && graceNote != null && graceLine != null && graceNoteIndex != -1) {
            // Wrap in withModification so the deletion is recorded even when finish()
            // runs outside an outer bracket (e.g. via Esc / drag-left cancel paths).
            // Brackets nest, so this is a no-op if a caller already opened one.
            var line = graceLine;
            var idx = graceNoteIndex;
            line.withModification(() -> line.removeElement(idx));
        }

        // Restore only DISABLE_IN_GRACE_MODE actions to their pre-grace-mode state.
        // All other actions (duration, embellishments, etc.) keep their current state.
        selectionCoordinator.restoreActionStatesWithFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE);

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
        var threshold = internalGetCancelThresholdPx();
        return threshold >= 0 && e.getX() <= threshold;
    }

    /**
     * Returns whether the mouse is at least {@link #GRACE_SLOP_PX} to the right
     * of the grace note's right edge. Used for connect detection during drag.
     */
    private boolean isMouseRightOfGraceNote(MouseEvent e) {
        var threshold = internalGetConnectThresholdPx();
        return threshold >= 0 && e.getX() >= threshold;
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
        return ScaleContext.getInstance().toRoundedPixels(graceXSs) - GRACE_SLOP_PX;
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
        return ScaleContext.getInstance().toRoundedPixels(rightEdgeSs) + GRACE_SLOP_PX;
    }

    /**
     * Returns whether the grace note has an eligible host note immediately
     * following it (a pitched note at the next index).
     */
    private boolean hasEligibleHostNote() {
        if (graceNote == null || graceLine == null) {
            return false;
        }

        var nextIndex = graceNoteIndex + 1;
        return nextIndex < graceLine.elementCount()
            && graceLine.getElement(nextIndex).getType().isPitchedNote();
    }

    private void hidePreviewAndSetDefaultCursor(LineComponent lineComponent) {
        PreviewElementManager.hidePreviewElement(false);
        lineComponent.setCursor(Cursor.getDefaultCursor());
    }
}
