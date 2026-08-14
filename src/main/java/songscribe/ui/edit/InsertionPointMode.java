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

import java.awt.MouseInfo;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.ViewPx;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.message.MessageCenter;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.InsertionMarkerOverlay;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.util.UIUtils;

/**
 * The "pick a spot on a line" interaction, independent of what is going to be put there.
 * A client enters the mode, the user moves the mouse until the insertion marker sits where
 * they want it, and a click (or Return) hands that point back to the client.
 *
 * <h2>What a client is entitled to</h2>
 * A client that successfully enters the mode is called back <b>exactly once</b> on
 * {@link Client#insertionPointModeDidEnd}: with {@link EndReason#PLACED} for the placement
 * it completed, or with {@link EndReason#CANCELLED} when the user abandoned it. Never both,
 * never twice, never neither — every way out of the mode routes through the single
 * {@link #exit} funnel, and the mode is already inactive when the call arrives.
 *
 * <p>{@link Client#insertionPointChosen} may run more than once before that: a client that
 * answers {@link Placement#DECLINED} has refused that particular point (paste's "line full")
 * and the mode stays live for another try. At most one of those calls is terminal, and it is
 * the one that answers {@link Placement#COMPLETED}.
 *
 * <p>Every index handed to a client is one its own {@link Client#acceptsInsertionIndex}
 * accepted. The mode never invents an index the client rejected, and never tracks one — a
 * rejected position hides the marker and makes Return a no-op, exactly as an out-of-bounds
 * position does.
 *
 * <h2>What the mode owns and what it does not</h2>
 * The mode owns the tracked {@code (LineComponent, index)} pair, the mouse and keyboard
 * handling that moves it, the {@link InsertionMarkerOverlay} that draws it, and cancellation.
 * It owns the geometry of <em>where on a line an insertion point exists at all</em>: nothing
 * can go into the staff header or past the staff's right edge, whoever is placing it.
 *
 * <p>It does not own which of the remaining indices are legal — that is the client's
 * predicate — nor what is inserted, nor any operation-specific chrome such as paste's
 * {@code PasteOverlay} banner, which its client adds on entry and removes when told the mode
 * ended.
 *
 * <h2>Cross-cutting effects</h2>
 * Entering and leaving post {@link InsertionPointModeDidChangeNotification}, which is what
 * disables every action and suppresses the preview element while a placement is pending.
 * While the mode is active, presses on a line are inert — no line select, no lyric select,
 * no pitch drag — so the click that follows is always a placement or a cancel.
 *
 * <p>See section 5 of {@code docs/clipboard.md} for the state diagram and the division of
 * labour with {@link PasteModeManager}.
 *
 * <p>Mirrors {@link GraceModeManager}'s single-flag / static-instance shape: at most one
 * placement is pending anywhere in the application, so {@link #isActive()} can answer for
 * callers that hold no reference.
 */
public final class InsertionPointMode {

    /**
     * The operation a placement is being picked for. Implemented by whoever wants an index
     * from the user; see the class contract above for what the mode promises in return.
     */
    public interface Client {

        /**
         * Whether {@code index} is a legal insertion point for this client.
         *
         * <p>Consulted on every mouse move, so it must be cheap and must not mutate anything.
         * A rejected index is not tracked: the marker hides and Return stays a no-op until the
         * pointer reaches an accepted one.
         *
         * @param line the line the pointer is over
         * @param index the insertion index under the pointer — {@code 0} through
         *     {@code line.effectiveElementCount()}, the element it lands before
         * @return {@code true} if a placement may be made at {@code index}
         */
        boolean acceptsInsertionIndex(Line line, int index);

        /**
         * Places whatever this client is placing at the point the user picked.
         *
         * <p>{@code index} is one {@link #acceptsInsertionIndex} accepted. The client is free
         * to do the whole operation here, dialogs and modification brackets included; the mode
         * has not torn anything down yet, so its chrome is still on screen.
         *
         * @param line the line the user picked
         * @param index the accepted insertion index within {@code line}
         * @return {@link Placement#COMPLETED} to end the mode, or {@link Placement#DECLINED} to
         *     leave it live so the user can pick again
         */
        Placement insertionPointChosen(Line line, int index);

        /**
         * Reports that the placement is over, once and only once, after the mode has already
         * gone inactive and dropped its tracked point. This is where a client tears down
         * whatever it put up in order to enter.
         *
         * @param reason whether the mode ended on a completed placement or a cancellation
         */
        void insertionPointModeDidEnd(EndReason reason);
    }

    /** What a client did with the index it was offered. */
    public enum Placement {
        /** The placement is done; the mode ends. */
        COMPLETED,

        /** This point was refused; the mode stays live for another try. */
        DECLINED
    }

    /** Why the mode ended. */
    public enum EndReason {
        /** A client completed a placement. */
        PLACED,

        /** The user abandoned the placement — Escape, a click off any line, or backgrounding. */
        CANCELLED
    }

    /** The index that means "nothing is tracked", so Return has nowhere to place. */
    private static final int NO_INDEX = -1;

    @Nullable
    private static InsertionPointMode instance = null;

    // Dependencies
    private final ScoreView scoreView;

    // State
    private boolean active = false;

    @Nullable
    private Client client = null;

    // The insertion point the next placement will use — the line and the index before which
    // the client's content lands. Tracked from mouse movement; NO_INDEX means the mouse has
    // not reached a position this client accepts, so Return is a no-op.
    @Nullable
    private LineComponent targetLineComponent = null;

    private int targetIndex = NO_INDEX;

    // The insertion-point marker. One instance for the lifetime of this mode (and thus of
    // the owning ScoreView) — retargeted and shown/hidden below rather than recreated.
    private final InsertionMarkerOverlay insertionMarkerOverlay;

    public InsertionPointMode(ScoreView scoreView) {
        this.scoreView = scoreView;
        insertionMarkerOverlay = new InsertionMarkerOverlay(scoreView);
        instance = this;
    }

    /**
     * Registers the insertion marker as a child of the owning view. Deliberately not done in
     * the constructor: this mode is built from {@code ScoreView}'s constructor, before the
     * view has a layout manager to register an absolute-bounds child against, and headless
     * views never acquire one at all. {@code ScoreView.init()} calls this on the interactive
     * path only, mirroring {@link PreviewElementManager#installOverlay}.
     */
    public void installOverlay() {
        scoreView.addOverlay(insertionMarkerOverlay);
    }

    /**
     * Whether a placement is pending anywhere in the application, for callers that hold no
     * reference to the mode — {@code UIAction.enableFromInsertionPointMode()} and
     * {@code ScoreViewController.handlePasteboardOp}.
     *
     * @return {@code true} while some client is waiting for the user to pick an index
     */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    /** Sets the singleton instance; intended for test teardown only. */
    static void setInstance(@Nullable InsertionPointMode value) {
        instance = value;
    }

    /** @return {@code true} while this mode is waiting for the user to pick an index */
    public boolean isInProgress() {
        return active;
    }

    /**
     * The line the marker is currently on.
     *
     * @return the tracked line component, or null when nothing is tracked
     */
    @Nullable
    public LineComponent getTargetLineComponent() {
        return targetLineComponent;
    }

    /**
     * The index the marker is currently drawn before, which is what {@link #place()} would
     * use. Non-negative exactly when {@link #getTargetLineComponent()} is non-null.
     *
     * @return the tracked insertion index, or -1 when nothing is tracked
     */
    public int getTargetIndex() {
        return targetIndex;
    }

    /** Returns the insertion-point marker overlay. Package-private: test support only. */
    InsertionMarkerOverlay getInsertionMarkerOverlay() {
        return insertionMarkerOverlay;
    }

    private void setActive(boolean active) {
        this.active = active;
        MessageCenter.post(new InsertionPointModeDidChangeNotification(active));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Enters the mode on {@code client}'s behalf, so the next click or Return on a line
     * resolves to an index for it.
     *
     * <p>At most one placement is pending at a time: a second client asking while one is
     * already running is refused rather than displacing it, and is told so by the return value
     * so it can skip whatever it was going to put on screen. A refused client gets no
     * callbacks at all — the exactly-once promise covers clients that actually entered.
     *
     * <p>{@link #syncTargetToMouse()} shows the marker on the spot when a target is already
     * resolvable (the pointer is already over a line), by routing through {@link #updateTarget},
     * the same path a real {@code mouseMoved} takes. A client that adds chrome of its own must
     * add it <em>after</em> this call returns: a full-bleed overlay added first would be the
     * topmost hit and would shadow the score from {@link UIUtils#getComponentUnderMouse}.
     *
     * @param client the operation the chosen index will be reported to
     * @return {@code true} if the mode entered, {@code false} if a placement was already pending
     */
    public boolean enter(Client client) {
        if (active) {
            return false;
        }

        this.client = client;
        setActive(true);
        syncTargetToMouse();
        return true;
    }

    /**
     * Locates the LineComponent currently under the mouse pointer, if any, and
     * immediately tracks it as the insertion target so the marker appears the
     * instant the mode is entered rather than waiting for the first real
     * {@code mouseMoved} event.
     */
    private void syncTargetToMouse() {
        var component = UIUtils.getComponentUnderMouse();

        if (!(component instanceof LineComponent lineComponent)) {
            return;
        }

        var mousePosition = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(mousePosition, lineComponent);

        var syntheticEvent = new MouseEvent(
            lineComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
            mousePosition.x, mousePosition.y, 0, false);

        updateTarget(lineComponent, syntheticEvent);
    }

    /**
     * Abandons the pending placement without choosing anything: Escape, a click off any line,
     * or the app being backgrounded. The client is told {@link EndReason#CANCELLED}. No-op
     * when no placement is pending.
     */
    public void cancel() {
        if (active) {
            exit(EndReason.CANCELLED);
        }
    }

    /**
     * The single teardown funnel every exit routes through: reset the flag and post the
     * notification (via {@link #setActive}), drop the tracked insertion point (via
     * {@link #clearTarget}, which also hides the marker), and only then tell the client the
     * mode ended.
     *
     * <p>The client reference is taken and cleared before the callback, so the mode is fully
     * idle by the time the client runs — a client that re-enters from its own teardown starts
     * a clean placement rather than a nested one.
     *
     * <p>Open-coding teardown per exit path would be five paths of duplicated steps, and a
     * missed step is invisible: the marker would be left painted over a score with no
     * placement pending, or a client would never hear that its placement ended and would leave
     * its chrome up for good. Mirrors {@code GraceModeManager.finish(boolean)}.
     */
    private void exit(EndReason reason) {
        var endingClient = client;
        client = null;
        setActive(false);
        clearTarget();

        if (endingClient != null) {
            endingClient.insertionPointModeDidEnd(reason);
        }
    }

    // -------------------------------------------------------------------------
    // Mouse routing (consume-first from LineComponent)
    // -------------------------------------------------------------------------

    /**
     * Tracks the insertion point under the mouse.
     *
     * @param lineComponent the line the pointer is over
     * @param e the motion event, in {@code lineComponent}'s coordinate space
     * @return {@code true} (consuming the event) whenever a placement is pending, so the normal
     *     preview-element handling is skipped
     */
    public boolean mouseMoved(LineComponent lineComponent, MouseEvent e) {
        if (!active) {
            return false;
        }

        updateTarget(lineComponent, e);
        return true;
    }

    /**
     * Drops the tracked insertion point when the mouse leaves the line that owns it, so
     * the marker disappears while the pointer is between lines or off the score entirely.
     * Guarded on identity: when the pointer crosses directly into another line, that line's
     * {@code mouseMoved} may arrive first, and this exit must not undo it.
     */
    public void mouseExited(LineComponent lineComponent) {
        if (active && lineComponent == targetLineComponent) {
            clearTarget();
        }
    }

    /**
     * Reports the clicked insertion point to the client.
     *
     * @param lineComponent the line that was clicked
     * @param e the click event, in {@code lineComponent}'s coordinate space
     * @return {@code true} (consuming the event) whenever a placement is pending
     */
    public boolean mouseClicked(LineComponent lineComponent, MouseEvent e) {
        if (!active) {
            return false;
        }

        updateTarget(lineComponent, e);
        placeAtTarget();
        return true;
    }

    /**
     * Reports the currently tracked insertion point to the client (the Return/Enter path).
     * With no tracked point — the mouse never entered a line, or sits somewhere the client
     * rejects — this is a no-op and the placement stays pending. No-op when no placement is
     * pending.
     */
    public void place() {
        if (!active) {
            return;
        }

        placeAtTarget();
    }

    /**
     * Drops the tracked insertion point and hides the marker. The mode stays active — Return
     * simply becomes a no-op again until the mouse re-enters an acceptable insertion position.
     */
    private void clearTarget() {
        targetLineComponent = null;
        targetIndex = NO_INDEX;
        insertionMarkerOverlay.setTarget(null, NO_INDEX);
    }

    /**
     * Recomputes the insertion index under the mouse and, when it or the line changes,
     * retargets the marker. {@code InsertionMarkerOverlay}'s height is identical on every line,
     * so a line change only ever repositions the marker — Swing dirties its old and new bounds
     * automatically when it moves, with no explicit repaint call needed.
     */
    private void updateTarget(LineComponent lineComponent, MouseEvent e) {
        var line = lineComponent.getLine();
        var layoutResult = lineComponent.getLayoutResult();
        var currentClient = client;

        if (line == null || layoutResult == null || currentClient == null) {
            return;
        }

        var mouseXSs = lineComponent.getScoreView().getViewScale().toSs(new ViewPx(e.getX())).value();

        // Nothing can be inserted into the staff header or past the staff's right edge,
        // whatever is being placed, so outside that span there is no insertion point to show.
        if (HorizontalSpacingCalculator.isWithinHeaderXSs(mouseXSs, line)
            || mouseXSs > line.getSong().getLineWidthSs()) {
            clearTarget();
            return;
        }

        // findInsertionIndex over an element head returns that element's index — never
        // on an element, always before N — and every return path is bounded by
        // effectiveElementCount(), so no clamping is needed.
        var index = layoutResult.findInsertionIndex(mouseXSs, line);

        // An index this client will not take is not a tracked point: hiding the marker is
        // what tells the user the position is unavailable, and it is what keeps place()
        // from ever handing the client an index it rejected.
        if (!currentClient.acceptsInsertionIndex(line, index)) {
            clearTarget();
            return;
        }

        if (lineComponent != targetLineComponent || index != targetIndex) {
            targetLineComponent = lineComponent;
            targetIndex = index;
            insertionMarkerOverlay.setTarget(lineComponent, index);
        }
    }

    /**
     * Hands the tracked point to the client and ends the mode unless the client declines it.
     * Silently does nothing when there is no tracked point — the caller has already decided a
     * placement was asked for, and "no point yet" is a normal state, not an error.
     */
    private void placeAtTarget() {
        var lineComponent = targetLineComponent;
        var currentClient = client;

        if (lineComponent == null || targetIndex < 0 || currentClient == null) {
            return;
        }

        var line = lineComponent.getLine();

        if (line == null) {
            return;
        }

        if (currentClient.insertionPointChosen(line, targetIndex) == Placement.COMPLETED) {
            exit(EndReason.PLACED);
        }
    }
}
