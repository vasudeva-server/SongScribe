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

import module java.desktop;

import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.dom.Song;
import songscribe.dom.ElementLocation;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.layout.ColumnSpan;
import songscribe.ui.component.MainFrame;
import songscribe.ui.Mode;
import songscribe.ui.edit.EditModeManager;
import songscribe.message.notification.ApplicationDidBecomeActiveNotification;
import songscribe.message.notification.ApplicationDidEnterBackgroundNotification;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.InsertionPointModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.engraving.Staff;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.playback.PlaybackController;

/**
 * Tracks where the hover preview sits for {@link LineComponent}: which line carries it, which
 * insertion slot and staff position it resolves to, and whether the pointer is over an existing
 * note head.
 * <p>
 * This class owns all static cross-instance state for that tracking — only one preview element can
 * be active across all LineComponents at a time — and answers every "should this be drawn"
 * question that depends on it. The work that hangs off the tracking state lives elsewhere:
 * {@link PreviewElementInserter} turns a click into an edit, {@link PreviewOverlayRegistry} owns
 * the overlay components, and {@link PreviewCursorHider} takes the system cursor away over the
 * preview.
 */
public final class PreviewElementManager {

    // ==========================================================================
    // Static State
    // ==========================================================================

    /**
     * The LineComponent that currently has the preview element.
     * <p>
     * Only one line can show the preview element at a time. When the mouse moves
     * to a different line, the old line is repainted to clear the preview element.
     */
    @Nullable
    private static LineComponent currentPreviewLine = null;

    /** Current insertion index (0 to elementCount inclusive). */
    private static int currentXIndex = -1;

    /** Current Y position on the staff (in staff position units, not pixels). */
    private static int currentStaffPosition = 0;

    /** The LineComponent the mouse is currently over (independent of insertion note state). */
    @Nullable
    private static LineComponent currentMouseLine = null;

    /** Whether the mouse X (in staff spaces) is within the horizontal bounds of a note head. */
    private static boolean xPosSsMatchesElement = false;

    /** Whether the mouse Y (staff position) is within the vertical bounds of that note head. */
    private static boolean yPosSpMatchesElement = false;

    /**
     * Runs after {@code ScoreViewController}'s layout-invalidating handler, so the pending
     * overlay restore sees an invalidated — and therefore recomputable — layout.
     */
    private static final int AFTER_LAYOUT_INVALIDATION_PRIORITY = Message.LOW_PRIORITY - 1;

    /** Last tracked mouse X in staff-space units; used by LineRenderer to avoid Swing getMousePosition() returning null. */
    private static double currentMouseXSs = 0.0;

    /** Strong reference to prevent GC by the weak-reference message bus; used for subscriptions. */
    private static final PreviewElementManager INSTANCE = new PreviewElementManager();

    static {
        MessageCenter.subscribe(INSTANCE);
    }

    private PreviewElementManager() {
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
     * Re-derives the tracked insertion position from the mouse's (unchanged) screen location
     * once a zoom change has been applied.
     * <p>
     * A keyboard/menu zoom (anchored at the viewport center, not the cursor) shifts which
     * document location sits under a stationary mouse — {@link #currentXIndex}/{@link
     * #currentStaffPosition} track document position, not screen position, so without this
     * they stay pinned to the pre-zoom location: the preview keeps rendering there (now
     * reprojected to new screen pixels by {@code ScoreView.zoomDidChangeRefreshOverlayBounds},
     * which only fixes the pixel conversion, not the underlying position) and a click at that
     * screen location resolves against a document position the cursor is no longer over.
     * Default priority — see the priority requirement documented on
     * {@link ZoomDidChangeNotification}.
     */
    @Handler
    public void zoomDidChange(ZoomDidChangeNotification message) {
        retargetMouseLineAndRestorePreviewElement();
    }

    /**
     * Re-derives the tracked insertion position once the last blocking dialog (e.g. Song
     * Settings) has closed.
     * <p>
     * A blocking dialog does not deliver mouse-moved events to the score underneath it, so the
     * mouse can end up anywhere by the time it closes — over a different note, a different
     * line, or off the score entirely — while the tracked state still reflects wherever it was
     * when the dialog opened. No-ops while a dialog is opening ({@code isVisible() == true}):
     * that transition doesn't change what the score looks like, just what the user can click.
     */
    @Handler
    public void dialogVisibilityDidChange(DialogVisibilityDidChangeNotification message) {
        if (message.isVisible()) {
            return;
        }

        retargetMouseLineAndRestorePreviewElement();
    }

    /**
     * Hides the preview element when the application moves to the background. Mirrors
     * {@link #clearPreviewElement} for the mouse-leaves-the-line case: nothing in the background
     * is tracking the pointer, so nothing should still claim an insertion position.
     */
    @Handler
    public void applicationDidEnterBackground(ApplicationDidEnterBackgroundNotification message) {
        hidePreviewElement(true);
    }

    /**
     * Re-derives the tracked insertion position and restores the preview element once the
     * application is genuinely usable again — see
     * {@link ApplicationDidBecomeActiveNotification}'s Javadoc for why this is later than raw
     * window activation and why the mouse position has to be re-resolved rather than assumed
     * unchanged.
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
     * Re-resolves {@link #currentMouseLine} against the mouse's current screen position and
     * re-derives the preview element's position from it — for any external change (zoom, dialog
     * dismissal, application activation) after which the mouse may now be over different
     * document content without ever having fired a real AWT mouse event of its own.
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
     * Finds the {@link LineComponent} (if any) the mouse is currently over, resolving the
     * {@link ScoreView} from {@link MainFrame} rather than from {@link #currentMouseLine} — the
     * whole point of this method is to recover from cases where the mouse may be somewhere
     * unexpected, and {@code currentMouseLine} is exactly the state that can no longer be
     * trusted. In particular, backgrounding the application delivers a native mouse-exited event
     * to whatever line the pointer was over, clearing {@code currentMouseLine} to null before
     * the app is ever reactivated — a self-referential lookup through it would always fail.
     * <p>
     * Cannot use {@link SwingUtilities#getDeepestComponentAt} from the score view downward: a
     * visible overlay (e.g. the hover preview itself) is a sibling of every {@code
     * LineComponent}, not a descendant of one, so when the cursor sits over the overlay —
     * exactly the case this method exists to handle — that walk would return the overlay and
     * never reach the line underneath it. Testing each line's own bounds sidesteps overlay
     * z-order entirely.
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

    /**
     * Creates {@code host}'s hover-preview overlays and registers them as children of the host.
     * Any dwell pending against the outgoing overlays is dropped first — see
     * {@link PreviewCursorHider#discard}.
     */
    public static void installOverlay(OverlayHost host) {
        PreviewCursorHider.discard();
        PreviewOverlayRegistry.install(host);
    }

    /**
     * Rebuilds every hover-preview overlay's ink and bounds from the current tracking state,
     * hiding each one that no longer applies.
     *
     * @see PreviewOverlayRegistry#previewDidChange
     */
    public static void previewElementDidChange() {
        PreviewOverlayRegistry.previewDidChange();
    }

    /**
     * Re-derives the insertion position from the real mouse location, then rebuilds the overlays'
     * ink for the newly-selected preview element type. Call this instead of
     * {@link #previewElementDidChange()} when the preview element's identity changes rather than
     * merely its decorations.
     *
     * @see PreviewOverlayRegistry#previewTypeDidChange
     */
    public static void previewElementTypeDidChange() {
        PreviewOverlayRegistry.previewTypeDidChange();
    }

    /**
     * Repaints the lines whose element colors this manager's tracking state feeds, so an element
     * that just stopped being the hover target reverts from
     * {@link LineInvariants#REPLACED_ELEMENT_COLOR} to black — and the one that just became it
     * turns red.
     * <p>
     * The overlays cannot do this themselves. They are siblings of the {@link LineComponent},
     * not children of it, so updating their bounds dirties only their own rectangles; the
     * highlighted element is painted by the line underneath and is never touched. The old
     * drawing-based mechanism repainted the line as a side effect of repainting the overlay ink
     * welded into it, which is what kept the highlight in sync — separating the overlay out
     * removed that side effect, and this restores it explicitly.
     *
     * @param previousLine the line tracked before the current update, repainted as well when the
     *                     pointer has crossed from one line to another so the highlight it left
     *                     behind is cleared
     */
    private static void repaintHighlightedElements(@Nullable LineComponent previousLine) {
        if (previousLine != null && previousLine != currentPreviewLine) {
            previousLine.repaint();
        }

        if (currentPreviewLine != null) {
            currentPreviewLine.repaint();
        }
    }

    /**
     * Returns whether the hover preview should be drawn on {@code lc}.
     * <p>
     * The single home for "is the preview visible": the component asks rather than re-assembling
     * the conditions, which is how they used to exist — as a chain of early returns inside the
     * renderer, far from the predicates they test.
     */
    static boolean shouldShowPreviewOn(LineComponent lc) {
        return activePreviewElementOn(lc) != null && lc.isPreviewElementVisible();
    }

    /**
     * Returns the index of the grace note that the host-note insertion preview on {@code lc}
     * would be connected to, or -1 when no connecting glissando should be drawn there.
     * <p>
     * The single home for "is the grace-host preview glissando visible". It rides on
     * {@link #shouldShowPreviewOn}: the connecting line only means anything while the host ghost
     * it runs to is itself on screen.
     */
    static int graceHostPreviewSourceIndexOn(LineComponent lc) {
        if (!shouldShowPreviewOn(lc)) {
            return -1;
        }

        return EditModeManager.getGraceModeManager().hostPreviewGraceIndexOn(lc);
    }

    /**
     * The preview element {@code lc} would draw, or null when {@code lc} carries no preview at
     * all. The prefix every preview gate shares: the right line, a mode that previews, a laid-out
     * line, and an element to preview.
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

    /**
     * Hides the preview element and optionally clears position tracking state.
     *
     * @param clear true to also clear position state (e.g., window deactivated);
     *              false to only hide the visual (e.g., hovering over an existing element head)
     */
    public static void hidePreviewElement(boolean clear) {
        if (clear) {
            clearPreviewElement();
        }

        EditModeManager.setPreviewElementVisible(false);

        // The visibility flag is part of the overlay's gate, so it has to be re-evaluated even
        // when nothing about the position changed.
        PreviewOverlayRegistry.previewDidChange();
    }

    /**
     * Clears the preview element from all lines.
     * <p>
     * Call this when exiting edit mode or when the mouse leaves the score area.
     */
    static void clearPreviewElement() {
        var previousPreviewLine = currentPreviewLine;

        if (currentPreviewLine != null) {
            currentPreviewLine = null;
            currentXIndex = -1;
            currentStaffPosition = 0;
            xPosSsMatchesElement = false;
            yPosSpMatchesElement = false;
        }

        // Unconditional: the mode-driven paths reach here with no preview line to clear, and the
        // overlay must still be taken down. currentPreviewLine is null by now, so this hides it.
        PreviewOverlayRegistry.previewDidChange();

        // The element that was the hover target is still painted red on the line it lives on,
        // and nothing is tracking it anymore to repaint it later.
        repaintHighlightedElements(previousPreviewLine);

        // Nothing is positioned to preview anymore, so it can no longer be visible;
        // this also clears the status bar's pitch/duration display.
        EditModeManager.setPreviewElementVisible(false);
    }

    /**
     * Sets whether the Alt key is currently pressed.
     *
     * @param pressed true if Alt is pressed
     */
    static void setAltPressed(boolean pressed) {
        // When Alt is released, re-trigger preview element from current mouse position
        if (!pressed) {
            restorePreviewElement(currentMouseLine);
        }
    }

    /**
     * Returns the current preview line, or null if no preview element is active.
     */
    @Nullable
    static LineComponent getCurrentInsertionLine() {
        return currentPreviewLine;
    }

    /**
     * Returns the LineComponent the mouse is currently over, or null when it is over none. Unlike
     * {@link #getCurrentInsertionLine()} this is independent of whether a preview element is
     * being shown there.
     */
    @Nullable
    static LineComponent getCurrentMouseLine() {
        return currentMouseLine;
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return currentXIndex;
    }

    /**
     * Returns the last tracked mouse X in staff-space units.
     */
    public static double getCurrentMouseXSs() {
        return currentMouseXSs;
    }

    /**
     * Returns the current insertion Y position.
     */
    static int getCurrentStaffPosition() {
        return currentStaffPosition;
    }

    /**
     * Returns the location of the element currently highlighted by preview element hover,
     * or null if the preview element's x-position does not match an existing element.
     */
    @Nullable
    public static ElementLocation getHoveredElementLocation() {
        if (!xPosSsMatchesElement || currentPreviewLine == null) {
            return null;
        }

        // A breath mark never replaces an existing element, so it never highlights one
        // as a replacement target.
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement != null && previewElement.getType().isBreathMark()) {
            return null;
        }

        // A grace note may never be replaced, so it never shows the red replacement highlight.
        if (isGraceNoteAt(currentPreviewLine.getLine(), currentXIndex)) {
            return null;
        }

        return new ElementLocation(currentPreviewLine.getLineIndex(), currentXIndex);
    }

    /**
     * Returns whether the given line currently has the preview element.
     */
    static boolean hasPreviewElement(LineComponent lc) {
        return currentPreviewLine == lc;
    }

    /**
     * Returns whether the mouse is currently hovering over an existing note head
     * (both X and Y match).
     */
    public static boolean isHoveringOverElementHead() {
        return xPosSsMatchesElement && yPosSpMatchesElement;
    }

    /**
     * Sets the current insertion X index (0 to elementCount inclusive).
     */
    static void setCurrentXIndex(int index) {
        currentXIndex = index;
    }

    /**
     * Sets the current preview line.
     */
    static void setCurrentPreviewLine(@Nullable LineComponent line) {
        currentPreviewLine = line;
    }

    /**
     * Sets the current Y position on the staff (in staff position units, not pixels).
     */
    static void setCurrentStaffPosition(int position) {
        currentStaffPosition = position;
    }

    /**
     * Returns whether the mouse X (in staff spaces) is within the horizontal bounds of a note head.
     */
    static boolean isXPosSsMatchesElement() {
        return xPosSsMatchesElement;
    }

    /**
     * Sets whether the mouse X (in staff spaces) is within the horizontal bounds of a note head.
     */
    static void setXPosSsMatchesElement(boolean matches) {
        xPosSsMatchesElement = matches;
    }

    /**
     * Returns the message-bus singleton (package-private for test support only), so tests can
     * invoke its {@code @Handler} methods directly without posting through the mocked bus.
     */
    static PreviewElementManager instance() {
        return INSTANCE;
    }

    /** Returns the installed hover-preview overlay, or null before {@link #installOverlay}. */
    static @Nullable PreviewElementOverlay getOverlay() {
        return PreviewOverlayRegistry.getOverlay();
    }

    /**
     * Returns the installed grace-host glissando-preview overlay, or null before
     * {@link #installOverlay}.
     */
    static @Nullable GraceGlissandoPreviewOverlay getGraceGlissandoOverlay() {
        return PreviewOverlayRegistry.getGraceGlissandoOverlay();
    }

    /**
     * Clears the installed overlays (package-private for test teardown), so a later test's
     * {@link #installOverlay} starts from a clean slate instead of reusing a previous test's
     * overlay instances.
     */
    static void resetOverlaysForTest() {
        PreviewOverlayRegistry.reset();
        PreviewCursorHider.discard();
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    /**
     * Returns whether the element at {@code elementIndex} on {@code line} is a grace note.
     * Grace notes may never be replaced by clicking through them with another preview
     * element, so this gates both the ghost preview visibility and the click handler.
     */
    static boolean isGraceNoteAt(@Nullable Line line, int elementIndex) {
        return line != null
            && line.hasIndex(elementIndex)
            && line.getElement(elementIndex).getType().isGraceNote();
    }

    // ==========================================================================
    // Delegation Entry Points (called from LineComponent mouse handlers)
    // ==========================================================================

    /**
     * Handles mouse movement over a line, updating preview element position.
     * Replaces the preview element logic formerly inline in {@code LineComponent.mouseMoved()}.
     */
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

        // Calculate Y position from mouse (in staff-space coordinates)
        var staffPosition = calculateStaffPositionFromMouse(mouseYSs, lc.getMiddleLineYSs());

        if (!isValidStaffPosition(staffPosition)) {
            // Mouse is outside valid range, clear insertion note if on this line
            if (currentPreviewLine == lc) {
                clearPreviewElement();
            }

            return;
        }

        // Calculate X index and element-head match from mouse using layout result
        var layoutResult = lc.getLayoutResult();
        var line = lc.getLine();

        if (layoutResult == null || line == null) {
            // Layout is being recalculated (e.g., mid-drag). Clear stale hover state
            // so that the next repaint does not show a highlight on the wrong element.
            xPosSsMatchesElement = false;
            yPosSpMatchesElement = false;
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
        var elementAtX = inGraceMode ? -1 : layoutResult.findElementAtXSs(mouseXSs, line, ColumnSpan.HEAD);

        // Suppress preview over the song's auto-maintained terminal (unless the
        // active preview element can legally replace it — exemption in isPositionBlockedByTerminal).
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, xIndex, elementAtX >= 0)) {
            clearPreviewElement();
            return;
        }

        var previewElement = EditModeManager.getPreviewElement();

        // Hide the preview element when the mouse is in the gap between an existing grace/host
        // pair — no element type may be inserted there, since it would break the pairing. Gated on
        // elementAtX < 0 so a click directly on the host's own head still replaces it normally;
        // only a plain gap-insert at the host's slot is blocked. isHostOfPairedGraceNote (unlike
        // isInsideGraceHostPair) does not also match the grace note's own slot, so inserting
        // immediately before the grace note — including when it is the line's first element —
        // remains allowed.
        if (previewElement != null && (elementAtX < 0) && line.isHostOfPairedGraceNote(xIndex)) {
            if (currentPreviewLine == lc) {
                clearPreviewElement();
            }

            return;
        }

        // Compute new position match flags before the early-return check, so that a
        // change in hover state (e.g., mouse slides from gap into element-head bounds at
        // the same xIndex) is not silently dropped.
        var newXMatch = elementAtX >= 0;
        var newYMatch = newXMatch
            && Math.abs(staffPosition - line.getElement(elementAtX).getStaffPosition()) <= 1;

        // A breath mark must be inserted between or after a note or rest; it may not sit at
        // index 0 or over an existing element (it never replaces one). At any such position
        // the ghost preview is hidden, and clicking there shows an alert. The red "will be
        // replaced" highlight is separately suppressed for breath marks in
        // getHoveredElementLocation, so newXMatch stays raw for the over-element-head check.
        var breathMarkBlocked = isBreathMarkInsertionBlocked(previewElement, xIndex, line, newXMatch);

        // A grace note may never be replaced by clicking through it with another element.
        // Hide the ghost preview over it; handleClick separately ignores the click.
        var graceNoteBlocked = isGraceNoteAt(line, elementAtX);

        // Split the five tracked fields by what they affect. The insertion index alone is
        // position: the glyphs the renderers would emit are identical, so the overlay only needs
        // a new translate. Everything else changes the drawn ink — the staff position flips the
        // stem direction and crosses the ledger-line threshold, and the hover flags change what
        // is drawn at all — so the display list has to be rebuilt. Both are step functions, so
        // rebuilds stay rare even though the mouse moves continuously.
        var xIndexChanged = xIndex != currentXIndex;
        var configurationChanged = lc != currentPreviewLine
            || staffPosition != currentStaffPosition
            || newXMatch != xPosSsMatchesElement
            || newYMatch != yPosSpMatchesElement;

        if (!xIndexChanged && !configurationChanged) {
            return;  // No change, no repaint
        }

        var previousPreviewLine = currentPreviewLine;

        // Update static state
        currentPreviewLine = lc;
        currentXIndex = xIndex;
        currentStaffPosition = staffPosition;
        xPosSsMatchesElement = newXMatch;
        yPosSpMatchesElement = newYMatch;

        // Always show the ghost preview — even when hovering over an existing element head.
        // The preview shows the user what pitch/type will replace the existing element.
        // Exceptions: breath marks must follow a note or rest, and grace notes may never
        // be replaced, so suppress the ghost in either case.
        var previewElementVisible = !breathMarkBlocked && !graceNoteBlocked;
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

    /**
     * Handles a click on the preview element, performing the appropriate action
     * (append, insert, or modify). Called from {@code LineComponent.mouseClicked()}.
     */
    public static void handleClick(LineComponent lc) {
        handleClick(lc, false);
    }

    /**
     * Like {@link #handleClick(LineComponent)}, but when {@code forceInsert} is {@code true}
     * always inserts rather than modifying an existing element at the same x position.
     * Used by grace mode, which must insert a new host note even when an existing note
     * occupies the locked insertion slot.
     *
     * @see PreviewElementInserter#handleClick
     */
    public static void handleClick(LineComponent lc, boolean forceInsert) {
        PreviewElementInserter.handleClick(lc, forceInsert);
    }

    /**
     * Handles mouse entering a line. Sets up cursor and preview element visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;

        if (shouldHandlePreviewElement(lc)) {
            EditModeManager.setPreviewElementVisible(true);
        }
    }

    /**
     * Handles mouse exiting a line. Clears cursor and preview element.
     */
    static void mouseExitedLine(LineComponent lc) {
        currentMouseLine = null;

        // Clear preview element when mouse leaves this line
        if (currentPreviewLine == lc) {
            clearPreviewElement();
        }

        EditModeManager.setPreviewElementVisible(false);
        PreviewOverlayRegistry.previewDidChange();
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    /**
     * Returns {@code true} when the given mouse position should be blocked because
     * it coincides with the song's auto-maintained terminal element.
     * Checks both "mouse is on the terminal element" and "mouse is at the
     * append position immediately after the terminal".
     * <p>
     * When the mouse is directly on the terminal and the active preview element can
     * legally replace it, the block is lifted so the user can see the ghost preview.
     */
    static boolean isPositionBlockedByTerminal(
        Song song, Line line, int xIndex, boolean xMatchesElement) {
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            return false;
        }

        var lastIdx = elementCount - 1;
        var terminalElement = line.getElement(lastIdx);

        if (!song.isAutoMaintainedTerminal(terminalElement, line)) {
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

    /**
     * Returns {@code true} when inserting a breath mark at {@code xIndex} should be
     * blocked. A breath mark attaches to the element immediately before it, which must
     * be a pitched note or a rest, and it never replaces an existing element. So it is
     * blocked at index 0 (no preceding element), directly after anything other than a
     * note or rest, while the cursor is over an existing element
     * ({@code overExistingElement}), when the preceding element is glissando-connected
     * to the following note (which would unexpectedly break the glissando), or when the
     * following element is already a breath mark (consecutive breath marks are
     * forbidden).
     */
    static boolean isBreathMarkInsertionBlocked(
        @Nullable StaffElement previewElement, int xIndex, Line line, boolean overExistingElement
    ) {
        if (previewElement == null || !previewElement.getType().isBreathMark()) {
            return false;
        }

        if (overExistingElement || xIndex == 0) {
            return true;
        }

        var precedingElement = line.getElement(xIndex - 1);
        var precedingType = precedingElement.getType();

        // isDuration() rejects an invalid host (e.g. a grace note); hasGlissando() rejects
        // a valid host that would have its glissando to the following note broken.
        return !precedingType.isDuration()
            || precedingElement.hasGlissando()
            || (xIndex < line.effectiveElementCount() && line.getElement(xIndex).getType().isBreathMark());
    }

    /**
     * Returns whether preview element handling should be active for the given line.
     * <p>
     * Requires: NOTE_EDIT mode and a preview element set.
     */
    static boolean shouldHandlePreviewElement(LineComponent lc) {
        if (!EditModeManager.hasPreviewElement()) {
            return false;
        }

        var scoreView = lc.getScoreView();

        return scoreView.getMode() == Mode.EDIT && !PlaybackController.isPlaying();
    }

    /**
     * Restores the preview element from the current mouse position on the given line.
     * Called when state changes (mode, playback, Alt key) may affect preview element visibility.
     */
    public static void restorePreviewElement(@Nullable LineComponent lc) {
        if (lc == null || !shouldHandlePreviewElement(lc)) {
            return;
        }

        var mousePos = lc.getMousePosition();

        if (mousePos == null) {
            return;
        }

        // Invalidate the cached xIndex so that the early-return guard in trackMouse
        // does not skip recomputation when the mouse position is unchanged but the
        // underlying notes have moved (e.g., after a drag finalises a pitch change).
        currentXIndex = -1;

        // Synthesize a MouseEvent and delegate to trackMouse
        var syntheticEvent = new MouseEvent(
            lc, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0,
            mousePos.x, mousePos.y, 0, false
        );
        trackMouse(lc, syntheticEvent);
    }

    /**
     * Calculates the staff position from a mouse Y coordinate.
     *
     * @param mouseYSs      Mouse Y coordinate in staff-space units
     * @param middleLineYSs Y coordinate of the middle staff line in staff-space units
     * @return Staff position
     */
    static int calculateStaffPositionFromMouse(double mouseYSs, double middleLineYSs) {
        return Staff.ssToSp(mouseYSs - middleLineYSs);
    }

    /**
     * Sets the staff position on an element: rests snap to their type's default
     * position, pitched notes use the given mouse-derived position.
     */
    static void applyStaffPosition(StaffElement element, int staffPositionSp) {
        if (element.getType().isRest()) {
            element.setStaffPosition(element.getType().getDefaultStaffPosition());
        } else {
            element.setStaffPosition(staffPositionSp);
        }
    }

    /**
     * Returns whether the given staff position is within the valid range for elements.
     *
     * @param staffPosition Staff position
     * @return true if the position is valid
     */
    static boolean isValidStaffPosition(int staffPosition) {
        return staffPosition >= Staff.MIN_STAFF_POSITION_SP
            && staffPosition <= Staff.MAX_STAFF_POSITION_SP;
    }
}
