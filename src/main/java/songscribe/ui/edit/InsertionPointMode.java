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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;

import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.ViewPx;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.message.MessageCenter;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.ui.component.InsertionPointOverlay;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.InsertionMarkerOverlay;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.util.UIUtils;

/**
 * The "pick a spot on a line" interaction, independent of what is put there.
 *
 * <h2>What a client is entitled to</h2>
 * A client that successfully enters the mode is called back exactly once on
 * {@link Client#insertionPointModeDidEnd}, with an {@link Outcome.Placed} or
 * {@link Outcome#CANCELLED}; the mode is already inactive when the call arrives.
 *
 * <p>{@link Client#insertionPointChosen} may run more than once before that: answering
 * {@link Placement#DECLINED} refuses that point and keeps the mode live. Every index handed to
 * a client is one its own {@link Client#acceptsInsertionIndex} accepted.
 *
 * <p>While the mode is active, presses on a line are inert, so the click that follows is
 * always a placement or a cancel. At most one placement is pending anywhere in the
 * application, so {@link #isActive()} can answer for callers that hold no reference.
 */
public final class InsertionPointMode {

    public interface Client {

        /** Asked once, on entry. */
        InsertionPointOverlay.Text overlayText();

        /**
         * Consulted on every mouse move, so it must be cheap and must not mutate anything.
         *
         * @param index the insertion index under the pointer, {@code 0} through
         *     {@code line.effectiveElementCount()}
         */
        boolean acceptsInsertionIndex(Line line, int index);

        /**
         * The banner and marker are still on screen, because a {@link Placement#DECLINED} answer
         * keeps placing. An error the user can retry from belongs here; a dialog that takes over
         * the operation belongs in {@link #insertionPointModeDidEnd}, after the banner is gone.
         */
        Placement insertionPointChosen(Line line, int index);

        /**
         * Called after the mode is inactive and the banner is down, so a client can undo what it
         * did to enter and run any part of the operation that must not share the screen with the
         * banner.
         */
        void insertionPointModeDidEnd(Outcome outcome);
    }

    public enum Placement {
        COMPLETED,
        DECLINED
    }

    public sealed interface Outcome {

        Outcome CANCELLED = new Cancelled();

        /** @param index the element the placement lands in front of */
        record Placed(Line line, int index) implements Outcome {}

        /** Use {@link #CANCELLED} rather than constructing one. */
        record Cancelled() implements Outcome {}
    }

    @Nullable
    private static InsertionPointMode instance = null;

    // Dependencies
    private final ScoreView scoreView;

    // State
    private boolean active = false;

    @Nullable
    private Client client = null;

    // The insertion point the next placement will use — the line and the index before which
    // the client's content lands. Tracked from mouse movement; null means the mouse has
    // not reached a position this client accepts, so Return is a no-op.
    @Nullable
    private LineComponent targetLineComponent = null;

    @Nullable
    private Integer targetIndex = null;

    // The banner naming the pending operation, and the layered-pane bounds listener that keeps
    // it full-bleed. Both are created in enter() and torn down in exit().
    @Nullable
    private InsertionPointOverlay overlay = null;

    @Nullable
    private ComponentListener overlayBoundsListener = null;

    // The insertion-point marker. One instance for the lifetime of this mode (and thus of
    // the owning ScoreView) — retargeted and shown/hidden below rather than recreated.
    private final InsertionMarkerOverlay insertionMarkerOverlay;

    public InsertionPointMode(ScoreView scoreView) {
        this.scoreView = scoreView;
        insertionMarkerOverlay = new InsertionMarkerOverlay(scoreView);
        instance = this;
    }

    /**
     * Not done in the constructor: the view has no layout manager yet, and headless views never
     * get one. {@code ScoreView.init()} calls this on the interactive path only.
     */
    public void installOverlay() {
        scoreView.addOverlay(insertionMarkerOverlay);
    }

    public static boolean isActive() {
        return instance != null && instance.active;
    }

    public boolean isInProgress() {
        return active;
    }

    /** Null when nothing is tracked. */
    @Nullable
    public LineComponent getTargetLineComponent() {
        return targetLineComponent;
    }

    /** Package-private: test support only. */
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
     * A second client asking while a placement is pending is refused (returns false) and gets no
     * callbacks. The banner goes up after the target sync: a full-bleed overlay added first
     * would shadow the score from {@link UIUtils#getComponentUnderMouse}.
     */
    public boolean enter(Client client) {
        if (active) {
            return false;
        }

        this.client = client;
        setActive(true);
        syncTargetToMouse();
        raiseOverlay(client.overlayText());
        return true;
    }

    private void raiseOverlay(InsertionPointOverlay.Text text) {
        var layeredPane = MainFrame.getInstance().getLayeredPane();
        var newOverlay = new InsertionPointOverlay(scoreView, text);
        overlay = newOverlay;

        var newOverlayBoundsListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                newOverlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            }
        };
        overlayBoundsListener = newOverlayBoundsListener;

        layeredPane.addComponentListener(newOverlayBoundsListener);
        newOverlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        layeredPane.add(newOverlay, JLayeredPane.PALETTE_LAYER);

        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void dropOverlay() {
        var layeredPane = MainFrame.getInstance().getLayeredPane();
        var boundsListener = overlayBoundsListener;
        overlayBoundsListener = null;

        if (boundsListener != null) {
            layeredPane.removeComponentListener(boundsListener);
        }

        var currentOverlay = overlay;
        overlay = null;

        if (currentOverlay != null) {
            layeredPane.remove(currentOverlay);
            layeredPane.revalidate();
            layeredPane.repaint();
        }
    }

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

    public void cancel() {
        if (active) {
            exit(Outcome.CANCELLED);
        }
    }

    /**
     * The single teardown funnel every exit routes through. The mode is idle before the client
     * is called, so a client that re-enters from its callback starts a clean placement.
     */
    private void exit(Outcome outcome) {
        var endingClient = client;
        client = null;
        setActive(false);
        clearTarget();
        dropOverlay();

        if (endingClient != null) {
            endingClient.insertionPointModeDidEnd(outcome);
        }
    }

    // -------------------------------------------------------------------------
    // Mouse routing (consume-first from LineComponent)
    // -------------------------------------------------------------------------

    /** Returns true (consuming the event) whenever a placement is pending. */
    public boolean mouseMoved(LineComponent lineComponent, MouseEvent e) {
        if (!active) {
            return false;
        }

        updateTarget(lineComponent, e);
        return true;
    }

    /** Guarded on identity: the next line's {@code mouseMoved} may arrive first. */
    public void mouseExited(LineComponent lineComponent) {
        if (active && lineComponent == targetLineComponent) {
            clearTarget();
        }
    }

    /** Returns true (consuming the event) whenever a placement is pending. */
    public boolean mouseClicked(LineComponent lineComponent, MouseEvent e) {
        if (!active) {
            return false;
        }

        updateTarget(lineComponent, e);
        placeAtTarget();
        return true;
    }

    public void place() {
        if (!active) {
            return;
        }

        placeAtTarget();
    }

    private void clearTarget() {
        targetLineComponent = null;
        targetIndex = null;
        insertionMarkerOverlay.clearTarget();
    }

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
            || mouseXSs > line.getSong().getLineWidthSs().value()) {
            clearTarget();
            return;
        }

        // findInsertionIndex over an element head returns that element's index — never
        // on an element, always before N — and every return path is bounded by
        // effectiveElementCount(), so no clamping is needed.
        var index = layoutResult.findInsertionIndex(mouseXSs, line);

        // A slot inside a pair — between a key change and its barline, or between a grace
        // note and its host — is not an insertion point for anyone, whoever is placing.
        if (!line.canInsertElementAt(index)) {
            clearTarget();
            return;
        }

        // An index this client will not take is not a tracked point: hiding the marker is
        // what tells the user the position is unavailable, and it is what keeps place()
        // from ever handing the client an index it rejected.
        if (!currentClient.acceptsInsertionIndex(line, index)) {
            clearTarget();
            return;
        }

        if (lineComponent != targetLineComponent || targetIndex == null || index != targetIndex) {
            targetLineComponent = lineComponent;
            targetIndex = index;
            insertionMarkerOverlay.setTarget(lineComponent, index);
        }
    }

    /** Does nothing when no point is tracked. */
    private void placeAtTarget() {
        var lineComponent = targetLineComponent;
        var currentClient = client;

        if (lineComponent == null || targetIndex == null || currentClient == null) {
            return;
        }

        var line = lineComponent.getLine();

        if (line == null) {
            return;
        }

        var chosenIndex = targetIndex;

        if (currentClient.insertionPointChosen(line, chosenIndex) == Placement.COMPLETED) {
            exit(new Outcome.Placed(line, chosenIndex));
        }
    }
}
