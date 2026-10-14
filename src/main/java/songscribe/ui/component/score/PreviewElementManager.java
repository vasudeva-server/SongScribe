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

package songscribe.ui.component.score;

import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.SwingUtilities;

import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Listener;
import net.engio.mbassy.listener.References;
import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementLocation;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.dom.ViewPx;
import songscribe.engraving.StaffPosition;
import songscribe.layout.ElementSeat;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.ApplicationDidBecomeActiveNotification;
import songscribe.message.notification.ApplicationDidEnterBackgroundNotification;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.PlaybackController;

/**
 * Tracks where the hover preview sits for {@link LineComponent}. Only one preview element can be
 * active across all LineComponents at a time.
 *
 * <p><b>Lifecycle:</b> the main window constructs one instance at startup, which subscribes to
 * the message bus for the life of the process and is never disposed.
 */
@Listener(references = References.Strong)
public final class PreviewElementManager {

    // ==========================================================================
    // Static State
    // ==========================================================================

    /**
     * Where the preview element is hovering.
     */
    public record PreviewHover(
        LineComponent line,
        int insertionIndex,
        StaffPosition staffPosition,
        boolean overElementX,
        boolean overElementHead) {
    }

    /**
     * Null when no line shows the preview element.
     */
    private static @Nullable PreviewHover hover = null;

    @Nullable
    private static LineComponent currentMouseLine = null;

    /**
     * Runs after {@code ScoreViewController}'s layout-invalidating handler, so the pending overlay
     * restore sees an invalidated layout.
     */
    private static final int AFTER_LAYOUT_INVALIDATION_PRIORITY = Message.LOW_PRIORITY - 1;

    /** Used by LineRenderer to avoid Swing getMousePosition() returning null. */
    private static double currentMouseXSs = 0.0;

    public PreviewElementManager() {
        MessageSubscription.addProcessListener(this);
    }

    @Handler
    public void modeDidChange(ModeDidChangeNotification message) {
        if (message.getMode() != Mode.EDIT) {
            clearPreviewElement();
            EditModeManager.setPreviewElement(null);
        } else {
            restorePreviewElement(currentMouseLine);
        }
    }

    @Handler
    public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
        if (PlaybackController.isPlaying()) {
            clearPreviewElement();
        } else {
            restorePreviewElement(currentMouseLine);
        }
    }

    /**
     * A keyboard/menu zoom shifts which document location sits under a stationary mouse; the
     * {@link PreviewHover} tracks document position, so without this it stays pinned to the
     * pre-zoom location. Default priority — see {@link ZoomDidChangeNotification}.
     */
    @Handler
    public void zoomDidChange(ZoomDidChangeNotification message) {
        retargetMouseLineAndRestorePreviewElement();
    }

    /**
     * A blocking dialog delivers no mouse-moved events to the score, so the tracked state is
     * stale when it closes. No-ops while a dialog opens: that does not change what the score
     * looks like.
     */
    @Handler
    public void dialogVisibilityDidChange(DialogVisibilityDidChangeNotification message) {
        if (message.isVisible()) {
            return;
        }

        retargetMouseLineAndRestorePreviewElement();
    }

    /**
     * Nothing tracks the pointer in the background, so nothing may still claim an insertion
     * position.
     */
    @Handler
    public void applicationDidEnterBackground(ApplicationDidEnterBackgroundNotification message) {
        hidePreviewElement(true);
    }

    /**
     * See {@link ApplicationDidBecomeActiveNotification} for why this is later than window
     * activation.
     */
    @Handler
    public void applicationDidBecomeActive(ApplicationDidBecomeActiveNotification message) {
        retargetMouseLineAndRestorePreviewElement();
    }

    @Handler(priority = AFTER_LAYOUT_INVALIDATION_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        PreviewOverlayRegistry.applyPendingRestore();
    }

    /**
     * For an external change after which the mouse may be over different content without having
     * fired an AWT mouse event.
     */
    private static void retargetMouseLineAndRestorePreviewElement() {
        var previousLine = currentMouseLine;
        currentMouseLine = retargetMouseLine();

        if (currentMouseLine == null) {
            if (previousLine != null) {
                clearPreviewElement();
            }

            return;
        }

        restorePreviewElement(currentMouseLine);
    }

    /**
     * Null if the mouse is over no line. The {@link ScoreView} comes from {@link MainFrame}, not
     * {@link #currentMouseLine}, which is the state this method recovers from. Tests each line's
     * own bounds because {@link SwingUtilities#getDeepestComponentAt} would return an overlay,
     * a sibling of every {@code LineComponent}, instead of the line beneath it.
     */
    private static @Nullable LineComponent retargetMouseLine() {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null) {
            return null;
        }

        var mousePos = scoreView.getMousePosition();

        if (mousePos == null) {
            return null;
        }

        var lineCount = scoreView.getSong().lineCount();

        for (var lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            var lineComponent = scoreView.getLineComponent(lineIndex);

            if (lineComponent == null) {
                continue;
            }

            var localPoint = SwingUtilities.convertPoint(scoreView, mousePos, lineComponent);

            if (lineComponent.contains(localPoint)) {
                return lineComponent;
            }
        }

        return null;
    }

    @Handler
    public void insertionPointModeDidChange(InsertionPointModeDidChangeNotification message) {
        if (message.isActive()) {
            clearPreviewElement();
        } else {
            restorePreviewElement(currentMouseLine);
        }
    }

    // ==========================================================================
    // Overlay
    // ==========================================================================

    public static void installOverlay(OverlayHost host) {
        PreviewCursorHider.discard();
        PreviewOverlayRegistry.install(host);
    }

    public static void previewElementDidChange() {
        PreviewOverlayRegistry.previewDidChange();
    }

    /**
     * Call instead of {@link #previewElementDidChange()} when the preview element's identity
     * changes, not merely its decorations.
     */
    public static void previewElementTypeDidChange() {
        PreviewOverlayRegistry.previewTypeDidChange();
    }

    /**
     * The overlays are siblings of the {@link LineComponent}, so updating their bounds never
     * repaints the highlighted element the line paints.
     */
    private static void repaintHighlightedElements(@Nullable LineComponent previousLine) {
        var currentLine = getCurrentInsertionLine();

        if (previousLine != null && previousLine != currentLine) {
            previousLine.repaint();
        }

        if (currentLine != null) {
            currentLine.repaint();
        }
    }

    /**
     * The single home for "is the preview visible".
     */
    static boolean shouldShowPreviewOn(LineComponent lc) {
        return activePreviewElementOn(lc) != null && lc.isPreviewElementVisible();
    }

    /**
     * Null when no connecting glissando should be drawn there. The single home for "is the
     * grace-host preview glissando visible".
     */
    static @Nullable Integer graceHostPreviewSourceIndexOn(LineComponent lc) {
        if (!shouldShowPreviewOn(lc)) {
            return null;
        }

        return EditModeManager.getGraceModeManager().hostPreviewGraceIndexOn(lc);
    }

    /**
     * Null when {@code lc} carries no preview at all.
     */
    private static @Nullable StaffElement activePreviewElementOn(LineComponent lc) {
        if (!hasPreviewElement(lc) || lc.getScoreView().getMode() == Mode.SELECT) {
            return null;
        }

        if (lc.getLayoutResult() == null) {
            return null;
        }

        return lc.getPreviewElement();
    }

    // ==========================================================================
    // Public Static API
    // ==========================================================================

    public static void hidePreviewElement(boolean clear) {
        if (clear) {
            clearPreviewElement();
        }

        EditModeManager.setPreviewElementVisible(false);

        // The visibility flag is part of the overlay's gate, so it has to be re-evaluated even
        // when nothing about the position changed.
        PreviewOverlayRegistry.previewDidChange();
    }

    static void clearPreviewElement() {
        var previousPreviewLine = getCurrentInsertionLine();
        hover = null;

        // Unconditional: the mode-driven paths reach here with no preview line to clear, and the
        // overlay must still be taken down. The hover is null by now, so this hides it.
        PreviewOverlayRegistry.previewDidChange();

        // The element that was the hover target is still painted red on the line it lives on,
        // and nothing is tracking it anymore to repaint it later.
        repaintHighlightedElements(previousPreviewLine);

        // Nothing is positioned to preview anymore, so it can no longer be visible;
        // this also clears the status bar's pitch/duration display.
        EditModeManager.setPreviewElementVisible(false);
    }

    static void setAltPressed(boolean pressed) {
        // When Alt is released, re-trigger preview element from current mouse position
        if (!pressed) {
            restorePreviewElement(currentMouseLine);
        }
    }

    /** Null if no preview element is active. */
    @Nullable
    static LineComponent getCurrentInsertionLine() {
        return hover == null ? null : hover.line();
    }

    /** Null when no line shows the preview element. */
    public static @Nullable PreviewHover hover() {
        return hover;
    }

    /**
     * Null when the mouse is over none. Unlike {@link #getCurrentInsertionLine()}, independent of
     * whether a preview element is shown there.
     */
    @Nullable
    static LineComponent getCurrentMouseLine() {
        return currentMouseLine;
    }

    public static double getCurrentMouseXSs() {
        return currentMouseXSs;
    }

    /** Null if the preview element's x-position does not match an existing element. */
    @Nullable
    public static ElementLocation getHoveredElementLocation() {
        var current = hover;

        if (current == null || !current.overElementX()) {
            return null;
        }

        // Nothing replaces an existing element without a preview element, and a breath mark
        // never replaces one, so neither highlights one as a replacement target.
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null || previewElement.getType().isBreathMark()) {
            return null;
        }

        // An element the preview element may not replace never shows the red replacement
        // highlight.
        var line = current.line().getLine();

        if (line != null && !line.canReplaceElementAt(current.insertionIndex(), previewElement.getType())) {
            return null;
        }

        return new ElementLocation(current.line().getLineIndex(), current.insertionIndex());
    }

    static boolean hasPreviewElement(LineComponent lc) {
        return getCurrentInsertionLine() == lc;
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    // ==========================================================================
    // Delegation Entry Points (called from LineComponent mouse handlers)
    // ==========================================================================

    static void trackMouse(LineComponent lc, MouseEvent e) {
        if (e.isAltDown()) {
            clearPreviewElement();
            return;
        }

        if (!shouldHandlePreviewElement(lc)) {
            return;
        }

        PreviewCursorHider.cursorDidMove(e);

        // Convert the view-pixel event coordinates to staff spaces at this single choke point.
        // Both the real path (LineComponent.mouseMoved) and the synthetic path
        // (restorePreviewElement) funnel through here, so everything below works in staff
        // spaces and never sees the zoom factor again.
        var viewScale = lc.getViewScale();
        var mouseYSs = viewScale.toSs(new ViewPx(e.getY())).value();

        // In grace mode, lock the x-position to the host note slot
        var graceModeManager = EditModeManager.getGraceModeManager();
        var inGraceMode = graceModeManager.isInProgress();
        double mouseXSs;

        if (inGraceMode) {
            mouseXSs = graceModeManager.getLockedInsertionXSs();
        } else {
            mouseXSs = viewScale.toSs(new ViewPx(e.getX())).value();
        }

        currentMouseXSs = mouseXSs;

        // Distance of the mouse from the middle staff line, the form the pitch grid is
        // addressed in.
        var mouseOffsetSs = mouseYSs - lc.getMiddleLineYSs();
        var resolvedPosition = StaffPosition.atSs(mouseOffsetSs);

        if (resolvedPosition == null) {
            // Mouse is outside valid range, clear insertion note if on this line
            if (hasPreviewElement(lc)) {
                clearPreviewElement();
            }

            return;
        }

        var staffPosition = resolvedPosition;

        // Calculate X index and element-head match from mouse using layout result
        var layoutResult = lc.getLayoutResult();
        var line = lc.getLine();

        if (layoutResult == null || line == null) {
            // Layout is being recalculated (e.g., mid-drag). A stale hover is no hover: drop it
            // so that the next repaint does not show a highlight on the wrong element.
            hover = null;
            return;
        }

        // A host note belongs in the slot immediately after its grace note, so in grace mode the
        // index comes from the pairing rather than from the locked x. The two disagree when a
        // breath mark trails the grace note: it occupies that slot, and the locked x — which sits
        // a fixed gap past the grace note — resolves past it. Inserting there would leave the
        // breath mark between the pair, with the glissando pointing at it instead of the host.
        var xIndex = inGraceMode
            ? graceModeManager.getHostInsertionIndex()
            : layoutResult.findInsertionIndex(mouseXSs, line);

        // In grace mode the locked x coincides with an existing note that will be
        // shifted (not replaced), so suppress the element-at-x match to avoid
        // painting it red as if it were the replacement target.
        var elementAtX = inGraceMode ? null : layoutResult.findElementAtXSs(mouseXSs, line);

        // Suppress preview over the song's auto-maintained terminal (unless the
        // active preview element can legally replace it — exemption in isPositionBlockedByTerminal).
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, xIndex, elementAtX != null)) {
            clearPreviewElement();
            return;
        }

        var previewElement = EditModeManager.getPreviewElement();

        // Hide the preview element at a gap slot where a one-element run of the preview element
        // may not be inserted — the rule every placement path shares, in
        // StaffElementRun.canInsertRunAt: the slot lands inside a pair, a breath mark would not
        // follow a duration element or would stand in front of another breath mark, or a
        // non-duration element would stand in front of a breath mark. Gated on elementAtX == null
        // so a click directly on a head still takes the replacement path, which
        // canReplaceElementAt rules on below; only a plain gap-insert is blocked here.
        if (previewElement != null && elementAtX == null && !line.canInsertRunAt(xIndex, List.of(previewElement))) {
            if (hasPreviewElement(lc)) {
                clearPreviewElement();
            }

            return;
        }

        // Compute new position match flags before the early-return check, so that a
        // change in hover state (e.g., mouse slides from gap into element-head bounds at
        // the same xIndex) is not silently dropped.
        var newXMatch = elementAtX != null;
        var newYMatch = elementAtX != null
            && Math.abs(staffPosition.value() - ElementSeat.seatSp(line.getElement(elementAtX)).value()) <= 1;

        // A breath mark never replaces an existing element, and it is not placed where a
        // glissando reaches the slot. At any such position the ghost preview is hidden, and
        // handleClick ignores the click. The red "will be replaced" highlight is separately
        // suppressed for breath marks in getHoveredElementLocation, so newXMatch stays raw for
        // the over-element-head check.
        var breathMarkBlocked = isBreathMarkInsertionBlocked(previewElement, xIndex, line, newXMatch);

        // Some elements may never be replaced by clicking through them with another element —
        // a grace note, a key change, the barline a key change sits behind — and a bar line may
        // not replace the first element of the song. Hide the ghost preview over them;
        // handleClick separately ignores the click.
        var replacementBlocked = elementAtX != null && previewElement != null
            && !line.canReplaceElementAt(elementAtX, previewElement.getType());

        var candidate = new PreviewHover(lc, xIndex, staffPosition, newXMatch, newYMatch);
        var previous = hover;

        if (candidate.equals(previous)) {
            return;  // No change, no repaint
        }

        // Split the hover's components by what they affect. The insertion index alone is
        // position: the glyphs the renderers would emit are identical, so the overlay only needs
        // a new translate. Everything else changes the drawn ink — the staff position flips the
        // stem direction and crosses the ledger-line threshold, and the hover flags change what
        // is drawn at all — so the display list has to be rebuilt. Both are step functions, so
        // rebuilds stay rare even though the mouse moves continuously.
        var configurationChanged = previous == null
            || lc != previous.line()
            || !staffPosition.equals(previous.staffPosition())
            || newXMatch != previous.overElementX()
            || newYMatch != previous.overElementHead();

        var previousPreviewLine = previous == null ? null : previous.line();
        hover = candidate;

        // Always show the ghost preview — even when hovering over an existing element head.
        // The preview shows the user what pitch/type will replace the existing element.
        // Exceptions: breath marks must follow a note or rest, and some elements may never be
        // replaced, so suppress the ghost in either case.
        var previewElementVisible = !breathMarkBlocked && !replacementBlocked;
        EditModeManager.setPreviewElementVisible(previewElementVisible);

        // Rests snap to their default staff position; pitched notes follow the mouse Y
        if (previewElement != null) {
            applyStaffPosition(previewElement, staffPosition);

            // Only report content for a visible preview; when hidden, setPreviewElementVisible
            // has already cleared the status bar and this must not overwrite that.
            if (previewElementVisible) {
                MessageCenter.post(new PreviewElementDidChangeNotification(previewElement, line, xIndex));
            }
        }

        if (configurationChanged) {
            PreviewOverlayRegistry.previewDidChange();
        } else {
            PreviewOverlayRegistry.previewDidMove();
        }

        repaintHighlightedElements(previousPreviewLine);
    }

    public static void handleClick(LineComponent lc) {
        PreviewElementInserter.handleClick(lc);
    }

    /**
     * @return {@code null} when the insertion may not go ahead
     */
    public static @Nullable AcceptedInsertion acceptForGraceMode(LineComponent lc) {
        return PreviewElementInserter.acceptForGraceMode(lc);
    }

    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;

        if (shouldHandlePreviewElement(lc)) {
            EditModeManager.setPreviewElementVisible(true);
        }
    }

    static void mouseExitedLine(LineComponent lc) {
        currentMouseLine = null;

        // Clear preview element when mouse leaves this line
        if (hasPreviewElement(lc)) {
            clearPreviewElement();
        }

        EditModeManager.setPreviewElementVisible(false);
        PreviewOverlayRegistry.previewDidChange();
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    static boolean isPositionBlockedByTerminal(
        Song song, Line line, int xIndex, boolean xMatchesElement) {
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            return false;
        }

        var lastIdx = elementCount - 1;
        var terminalElement = line.getElement(lastIdx);

        if (!song.isTerminal(terminalElement, line)) {
            return false;
        }

        if (xMatchesElement && xIndex == lastIdx) {
            // Mouse is directly on the terminal. Lift the block when the active preview
            // element can legally replace it; otherwise keep it blocked.
            var previewElement = EditModeManager.getPreviewElement();
            return previewElement == null
                || !song.canReplaceTerminal(previewElement.getType());
        }

        // Mouse is at the append slot immediately after the terminal — always blocked.
        return xIndex == elementCount;
    }

    static boolean isBreathMarkInsertionBlocked(
        @Nullable StaffElement previewElement, int xIndex, Line line, boolean overExistingElement
    ) {
        if (previewElement == null || !previewElement.getType().isBreathMark()) {
            return false;
        }

        return overExistingElement || !line.canLeadRunAt(xIndex) || line.hasGlissandoInto(xIndex);
    }

    static boolean shouldHandlePreviewElement(LineComponent lc) {
        if (!EditModeManager.hasPreviewElement()) {
            return false;
        }

        var scoreView = lc.getScoreView();

        return scoreView.getMode() == Mode.EDIT && !PlaybackController.isPlaying();
    }

    /**
     * The preview element is not a hit region, so a competing click gesture asks here whether
     * the preview owns the click. {@link PreviewElementInserter#handleClick} may still decline
     * an owned click; the competing gesture must stand down either way. Point-dependent:
     * {@link #trackMouse} clears the preview outside {@link StaffPosition#MIN} ..
     * {@link StaffPosition#MAX}.
     */
    static boolean isPreviewClickTarget(LineComponent lc) {
        return shouldHandlePreviewElement(lc) && hasPreviewElement(lc);
    }

    public static void restorePreviewElement(@Nullable LineComponent lc) {
        if (lc == null || !shouldHandlePreviewElement(lc)) {
            return;
        }

        var mousePos = lc.getMousePosition();

        if (mousePos == null) {
            return;
        }

        // Drop the hover so that trackMouse recomputes it: its early-return guard would otherwise
        // skip the recomputation when the mouse position is unchanged but the underlying notes
        // have moved (e.g., after a drag finalizes a pitch change). The line the old hover held
        // is repainted when it is another line, as trackMouse does when the pointer crosses lines.
        var previousPreviewLine = getCurrentInsertionLine();
        hover = null;

        if (previousPreviewLine != null && previousPreviewLine != lc) {
            previousPreviewLine.repaint();
        }

        // Synthesize a MouseEvent and delegate to trackMouse
        var syntheticEvent = new MouseEvent(
            lc, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0,
            mousePos.x, mousePos.y, 0, false
        );
        trackMouse(lc, syntheticEvent);
    }

    static void applyStaffPosition(StaffElement element, StaffPosition staffPosition) {
        if (element instanceof NoteElement note) {
            note.setStaffPosition(staffPosition);
        }
    }
}
