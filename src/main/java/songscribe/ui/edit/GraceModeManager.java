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
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AccidentalChain;
import songscribe.dom.AccidentalState;
import songscribe.dom.ChainEdit;
import songscribe.dom.Cursor;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.LyricRun;
import songscribe.dom.DocumentScale;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.engraving.StaffPosition;
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
 */
public final class GraceModeManager {

    public enum State {
        INACTIVE,
        GRACE_NOTE,
        GRACE_NOTE_INSERT,
        GRACE_NOTE_PAIRED,
        FINISH
    }

    private static final int GRACE_SLOP_PX = 4;
    public static final long MIN_DRAG_MILLIS = 500;

    @Nullable
    private static GraceModeManager instance = null;

    // Dependencies
    private final SelectionCoordinator selectionCoordinator;

    // State
    private State state = State.INACTIVE;

    /** Present whenever {@link #state} is not {@link State#INACTIVE}. */
    private record ActiveGrace(NoteElement note, int index, Line line, LineComponent component) {}

    @Nullable
    private ActiveGrace activeGrace = null;

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

    // True when the untracked grace-note insertion left its neighbors unrepaired, so the
    // pairing owes the repairs. An append has no neighbor after it and owes none.
    private boolean insertionRepairsOwed = false;

    private record SavedAccidentalState(Line line, NoteElement.SavedAccidental saved) {}

    // The notes the untracked insertion's walk restamped, in walk order, until the pairing
    // records them or the abort drops them.
    private final List<SavedAccidentalState> savedAccidentalStates = new ArrayList<>();

    public GraceModeManager(SelectionCoordinator selectionCoordinator) {
        this.selectionCoordinator = selectionCoordinator;
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isInProgress();
    }

    public boolean isInProgress() {
        return state != State.INACTIVE;
    }

    public static boolean isPendingCancel(StaffElement element) {
        return instance != null && instance.pendingCancel && instance.isGraceNote(element);
    }

    /** Render-only state: the drag never mutates the element's slide. */
    public static boolean isPendingConnect(StaffElement element) {
        return instance != null && instance.pendingConnect && instance.isGraceNote(element);
    }

    private boolean isGraceNote(StaffElement element) {
        return activeGrace != null && element == activeGrace.note();
    }

    public static boolean hasPendingConnect() {
        return instance != null && instance.pendingConnect;
    }

    /** Returns 0 if grace mode is not in progress or the layout is unavailable. */
    public double getLockedInsertionXSs() {
        var grace = activeGrace;

        if (grace == null) {
            return 0;
        }

        var layout = grace.component().getLayoutResult();

        if (layout == null) {
            return 0;
        }

        var graceColumn = layout.getElementColumn(grace.note());

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

    /** Null if not in progress. */
    public @Nullable LineComponent getGraceLineComponent() {
        return activeGrace != null ? activeGrace.component() : null;
    }

    private ActiveGrace requireActiveGrace() {
        var grace = activeGrace;

        if (grace == null) {
            throw new IllegalStateException("grace mode is not in progress");
        }

        return grace;
    }

    /**
     * The single definition of the host slot; callers must not recompute it from the grace
     * note's index. Valid whenever {@link #isInProgress()} is true.
     */
    public int getHostInsertionIndex() {
        return requireActiveGrace().index() + 1;
    }

    /** Null when no host preview is showing on {@code lineComponent}. */
    public @Nullable Integer hostPreviewGraceIndexOn(LineComponent lineComponent) {
        return isShowingHostPreviewOn(lineComponent) ? requireActiveGrace().index() : null;
    }

    boolean isShowingHostPreviewOn(LineComponent lineComponent) {
        return state == State.GRACE_NOTE_INSERT && lineComponent == getGraceLineComponent();
    }

    /** Null if not in {@link State#GRACE_NOTE_INSERT} or the host note would not fit on the line. */
    public InsertionSpacingCalculator.@Nullable InsertionResult getHostInsertionPreview() {
        return cachedHostInsertion;
    }

    /** Null if no preview element is showing or the host note would not fit on the line. */
    private InsertionSpacingCalculator.@Nullable InsertionResult computeHostInsertion(ActiveGrace grace) {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        var line = grace.line();
        var component = grace.component();

        var result = InsertionSpacingCalculator.calculateInsertion(
            line, previewElement, getHostInsertionIndex(),
            component.requireLayoutResult(), component.getLyricRenderMetrics()
        );

        if (!result.fitsWithinLine(line.getSong().getLineWidthSs().value())) {
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

        var hover = PreviewElementManager.hover();

        if (hover == null) {
            return false;
        }

        var xIndex = hover.insertionIndex();

        // The grace note and its host land as a pair that ends in a note, so only the slots
        // inside an existing pair are refused — the same rule the insertion itself applies.
        if (!line.canInsertElementAt(xIndex)) {
            return true;
        }

        if (!InsertionSpacingCalculator.hasRoomForGraceNote(
                line, xIndex, lineComponent.requireLayoutResult(), lineComponent.getLyricRenderMetrics())) {
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

        if (mouseDownPoint == null) {
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
        if (pendingCancel != wasPendingCancel || pendingConnect != wasPendingConnect) {
            requireActiveGrace().component().repaint();
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
        if (lineComponent != getGraceLineComponent()) {
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

        var accepted = PreviewElementManager.acceptForGraceMode(lineComponent);

        if (accepted == null) {
            abort();
            return;
        }

        // Insert the grace note through the note-entry gate, but WITHOUT recording a
        // mutation: a lone grace note is not undoable. It only becomes part of an undo step
        // once it is paired with a host note (see enterGraceNotePaired, which retroactively
        // records the insertion into the pairing's bracket). The insertion's repairs to the
        // neighboring elements would be lost the same way, so they are left to the pairing
        // bracket, not made here.
        insertionRepairsOwed = !accepted.appends();
        var inserted = line.getSong().withoutMutationTrackingResult(accepted::landWithoutRepairs);

        // No SongDidChangeNotification fires while tracking is suspended, so invalidate
        // the line's layout directly to lay out and render the new grace note.
        lineComponent.invalidateLayout();

        // The insertion placed the grace quaver the preview held, which is a note.
        if (!(inserted instanceof NoteElement insertedGraceNote)) {
            throw new IllegalStateException("grace insertion landed a non-note: " + inserted);
        }

        var graceNoteIndex = line.getElementIndex(insertedGraceNote);

        activeGrace = new ActiveGrace(insertedGraceNote, graceNoteIndex, line, lineComponent);

        // The suspended insertion ran no chain walk, so the grace note and every note its
        // landing reaches are still undecided. Walk now and stamp them plainly — this is not
        // yet an undo step — keeping what each held so the pairing can record the restamp.
        var song = line.getSong();

        var restamps = AccidentalChain.walk(
            song, ChainEdit.inserted(new Cursor(line.index(), graceNoteIndex), List.of(insertedGraceNote)));

        for (var restamp : restamps) {
            savedAccidentalStates.add(
                new SavedAccidentalState(restamp.line(), restamp.note().saveAccidentalState()));
            restamp.stamp();
        }

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
        // sees the correct state when disabling the actions lacking ENABLED_IN_GRACE_MODE.
        state = State.GRACE_NOTE;

        // Post message to disable every action not declared safe in grace mode
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

        var grace = requireActiveGrace();

        // Check whether the host note will fit before locking into insert mode, and
        // cache the result so per-frame preview rendering does not repeat the math.
        var hostInsertion = computeHostInsertion(grace);

        if (hostInsertion == null) {
            abort();
            OptionDialogs.showErrorMessage(
                SwingUtilities.getWindowAncestor(grace.component()),
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_GRACE_NOTE_HOST_NO_ROOM
            );
            return;
        }

        cachedHostInsertion = hostInsertion;
        PreviewElementManager.restorePreviewElement(grace.component());
    }

    private void enterGraceNotePaired(boolean connectNext, LineComponent lineComponent) {
        var grace = requireActiveGrace();
        var note = grace.note();
        var line = grace.line();
        var component = grace.component();
        var graceNoteIndex = grace.index();
        var hostNoteIndex = getHostInsertionIndex();

        // Determine the host note's pitch up front. For a drag-right connection the host
        // already exists on the line; for a host-note insertion the pitch comes from the
        // preview element about to be inserted. Validating before opening the modification
        // bracket lets a same-pitch pairing be rejected leaving no undo step.
        StaffPosition hostStaffPosition;

        if (connectNext) {
            if (hostNoteIndex >= line.elementCount()
                || !(line.getElement(hostNoteIndex) instanceof NoteElement host)) {
                abort();
                return;
            }

            hostStaffPosition = host.getStaffPosition();
        } else {
            if (!(EditModeManager.getPreviewElement() instanceof NoteElement previewNote)) {
                abort();
                return;
            }

            hostStaffPosition = previewNote.getStaffPosition();
        }

        // Grace note and host note must have different pitches.
        if (note.getStaffPosition().equals(hostStaffPosition)) {
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

        // A host note to insert passes its gate before the bracket opens, with its pitch now
        // known, so its accidental is measured. A host the line has no room for leaves nothing
        // recorded: the gate has told the user, and the grace note goes with the interaction.
        var hostInsertion = connectNext ? null : PreviewElementManager.acceptForGraceMode(lineComponent);

        if (!connectNext && hostInsertion == null) {
            abort();
            return;
        }

        // A single modification bracket coalesces the whole pairing into one undoable
        // SongDidChangeNotification: the retroactive grace-note insertion, the optional
        // host-note insertion, and the connecting glissando.
        line.getSong().withModification(() -> {
            // The grace note was inserted without tracking; record its insertion now so
            // undo removes it too. The element is already on the line, so the mutator is
            // empty (the PitchShifter pattern) — the record exists only to drive undo/redo.
            line.applyChange(new ElementInsertion(line, graceNoteIndex, note), () -> {});

            // The states the insertion's untracked walk decided are already on the notes, so
            // the song's own walk after that record changes nothing. Each is put back to what
            // it replaced and recorded from there, so undo restores the state the walk
            // replaced, not the one it decided.
            for (var savedState : savedAccidentalStates) {
                var saved = savedState.saved();
                var restampedNote = saved.note();
                var restampedLine = savedState.line();
                var decided = restampedNote.saveAccidentalState();
                saved.restore();

                restampedLine.modifyElement(
                    restampedLine.getElementIndex(restampedNote),
                    ElementField.ACCIDENTAL,
                    decided::restore
                );
            }

            // The repairs the insertion deferred, recorded here so undo can put back the
            // syllabic and melisma chains the grace note broke, and the connecting glissando
            // it took away from the note in front of it. They run before the host insertion
            // below, while the element after the grace note is still the one the grace note
            // was inserted in front of.
            if (insertionRepairsOwed) {
                line.repairNeighborsAfterUntrackedInsertion(graceNoteIndex);
            }

            // Insert the host note at the locked x position. The preview element was built
            // undecorated on entry to grace mode (see enterGraceNote), so the host arrives
            // carrying only the duration, dots, accidental and articulations the user chose
            // while the mode was in progress. A drag-right connection's host is already there.
            var hostNote = hostInsertion != null ? hostInsertion.land() : line.getElement(hostNoteIndex);

            // Connect the grace note to the host note with a connecting glissando.
            line.modifyElement(graceNoteIndex, ElementField.SLIDE, () -> note.setSlide(Slide.GLISSANDO));

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

    /** Leaves no undo step: a cancelled operation must not appear in the undo history. */
    private void abort() {
        var grace = activeGrace;

        if (grace != null) {
            var line = grace.line();
            var idx = grace.index();
            var song = line.getSong();
            song.withoutMutationTracking(() -> line.removeElement(idx));

            // The suspended removal ran no chain walk either; the notes the insertion's walk
            // restamped are walked back the same way, plainly, leaving no undo step.
            AccidentalChain.walk(song, ChainEdit.removed(new Cursor(line.index(), idx), List.of(grace.note())))
                .forEach(AccidentalChain.Restamp::stamp);

            // No SongDidChangeNotification fires while tracking is suspended, so
            // invalidate the line's layout directly to reflect the removal.
            grace.component().invalidateLayout();
        }

        resetState();
    }

    /** Records nothing: {@link #enterGraceNotePaired}'s modification bracket already did. */
    private void commit() {
        resetState();
    }

    private void resetState() {
        // Restore only the actions grace mode disabled — those lacking ENABLED_IN_GRACE_MODE —
        // to their pre-grace-mode state. The actions that stayed live (duration, embellishments,
        // etc.) keep the state the user gave them during the mode.
        selectionCoordinator.getActionReflector().restoreActionStatesLackingFlag(UIAction.Flag.ENABLED_IN_GRACE_MODE);

        // Set INACTIVE before posting the message so that enableFromGraceModeState()
        // sees the correct state when re-enabling the actions lacking ENABLED_IN_GRACE_MODE.
        state = State.INACTIVE;

        post(new GraceModeStateDidChangeNotification(false));

        // Re-enable the grace note action (it was deselected when entering grace mode
        // and the GraceModeStateChangedMessage handler may have left it disabled).
        // Use invokeLater to ensure the setEnabled call (which can trigger button
        // repaints via property change listeners) always runs on the EDT.
        SwingUtilities.invokeLater(() -> Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(true));

        activeGrace = null;
        cachedHostInsertion = null;
        mouseDownPoint = null;
        mouseDownTime = 0;
        justEnteredInsert = false;
        pendingCancel = false;
        pendingConnect = false;
        insertionRepairsOwed = false;
        savedAccidentalStates.clear();

        // The host preview's connecting glissando is gated on the state cleared just above, and
        // nothing else takes the overlays down on the way out of grace mode: a commit would leave
        // the preview line doubling the real glissando it just became, and an abort would leave
        // one running to a grace note that has already been removed.
        PreviewElementManager.previewElementDidChange();
    }

    // -------------------------------------------------------------------------
    // Private helper methods
    // -------------------------------------------------------------------------

    private boolean isMouseLeftOfGraceNote(MouseEvent e) {
        var grace = requireActiveGrace();
        var threshold = cancelThresholdPx(grace);
        return threshold != null && mouseXDocPx(grace, e) <= threshold;
    }

    private boolean isMouseRightOfGraceNote(MouseEvent e) {
        var grace = requireActiveGrace();
        var threshold = connectThresholdPx(grace);
        return threshold != null && mouseXDocPx(grace, e) >= threshold;
    }

    private static int mouseXDocPx(ActiveGrace grace, MouseEvent e) {
        return grace.component().getScoreView().getViewScale().toDocPx(new ViewPx(e.getX())).positionPx();
    }

    /** Null when layout is unavailable. */
    private static @Nullable Integer cancelThresholdPx(ActiveGrace grace) {
        var layout = grace.component().getLayoutResult();

        if (layout == null) {
            return null;
        }

        var graceXSs = layout.getElementXSs(grace.note());
        return DocumentScale.ssToPx(graceXSs).positionPx() - GRACE_SLOP_PX;
    }

    /** Null when layout is unavailable. */
    private static @Nullable Integer connectThresholdPx(ActiveGrace grace) {
        var layout = grace.component().getLayoutResult();

        if (layout == null) {
            return null;
        }

        var graceColumn = layout.getElementColumn(grace.note());

        if (graceColumn == null) {
            return null;
        }

        var rightEdgeSs = graceColumn.getXSs() + graceColumn.getRightExtentSs();
        return DocumentScale.ssToPx(rightEdgeSs).positionPx() + GRACE_SLOP_PX;
    }

    private boolean hasEligibleHostNote() {
        var line = requireActiveGrace().line();
        var nextIndex = getHostInsertionIndex();
        return nextIndex < line.elementCount()
            && line.getElement(nextIndex).getType().isPitchedNote();
    }
}
