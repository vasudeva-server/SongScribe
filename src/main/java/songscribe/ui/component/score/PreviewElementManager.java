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
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.formdev.flatlaf.util.SystemInfo;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.dom.Beam;
import songscribe.dom.Song;
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.ui.EndingConfirms;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.TempoChangeDialog;
import songscribe.ui.Mode;
import songscribe.ui.platform.mac.MacCursor;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.undo.OpNames;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.edit.EditModeManager;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.dom.ScaleContext;
import songscribe.message.notification.ApplicationDidBecomeActiveNotification;
import songscribe.message.notification.ApplicationDidEnterBackgroundNotification;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PasteModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.engraving.Staff;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.Debounce;

/**
 * Manages the preview element subsystem for {@link LineComponent}.
 * <p>
 * This class owns all static cross-instance state for preview element tracking,
 * cursor management, and element mutation logic. Only one preview element can be
 * active across all LineComponents at a time.
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
     * Runs after ScoreViewController's layout-invalidating handler so the pending prompt
     * sees an invalidated (recomputable) layout for the just-inserted note.
     */
    private static final int TEMPO_PROMPT_PRIORITY = Message.LOW_PRIORITY - 1;

    /**
     * A first-note tempo prompt awaiting the commit of its enclosing modification bracket.
     * The anchor is the first element of the first line (the grace note, when one leads).
     * Set inside the insertion bracket, consumed by {@link #showPendingTempoPrompt}.
     */
    private record PendingTempoPrompt(LineComponent lineComponent, Line line, StaffElement anchor) {}

    /** The pending first-note tempo prompt, or null when none is queued. */
    @Nullable
    private static PendingTempoPrompt pendingTempoPrompt = null;

    /**
     * True when {@link #previewElementTypeDidChange()} ran while a modification bracket was
     * open and had to defer re-anchoring the overlay until the bracket closes and layout is
     * fresh. Consumed by {@link #applyPendingOverlayRestore}.
     */
    private static boolean pendingOverlayRestore = false;

    /** The slide zone determined by mouse position (null if no valid zone). */
    private static @Nullable SlideZone currentSlideZone = null;

    /** Last tracked mouse X in staff-space units; used by LineRenderer to avoid Swing getMousePosition() returning null. */
    private static double currentMouseXSs = 0.0;

    /**
     * The component that draws the hover preview, or null before a host has installed one
     * (headless converters never do). Static like the rest of this class's state: only one line
     * across all hosts can carry the preview at a time.
     */
    @Nullable
    private static PreviewElementOverlay overlay = null;

    /**
     * The component that draws the fall-tool hover preview, or null before a host has installed
     * one. Mutually exclusive with {@link #glissandoOverlay}: {@link #shouldShowSlidePreviewOn}
     * guarantees only one {@link SlideZone} is ever current at a time.
     */
    @Nullable
    private static FallPreviewOverlay fallOverlay = null;

    /**
     * The component that draws the glissando-tool hover preview, or null before a host has
     * installed one. Mutually exclusive with {@link #fallOverlay}.
     */
    @Nullable
    private static GlissandoPreviewOverlay glissandoOverlay = null;

    /**
     * The component that draws the connecting glissando between a grace note and the host-note
     * insertion preview, or null before a host has installed one. Independent of the two
     * slide-tool previews: it is gated on grace mode rather than on a {@link SlideZone}, and the
     * note-head preview it connects to is showing at the same time.
     */
    @Nullable
    private static GraceGlissandoPreviewOverlay graceGlissandoOverlay = null;

    /**
     * How long the pointer must stay put before the cursor is hidden over the hover preview.
     * Long enough that the arrow stays available all the while the mouse is in motion, short
     * enough that it is out of the way by the time the user is looking at the ghost they are
     * about to place.
     */
    private static final int CURSOR_HIDE_DWELL_MS = 100;

    /**
     * How often the dwell deadline is tested. Bounds how late the hide can be
     * ({@link #CURSOR_HIDE_DWELL_MS} plus this), which for a cursor that is about to disappear
     * anyway is well below noticing.
     */
    private static final int CURSOR_HIDE_POLL_MS = 25;

    /**
     * Runs once the pointer has been still for {@link #CURSOR_HIDE_DWELL_MS}, retriggered by
     * every mouse move so it never fires while the mouse is moving. Created on first use:
     * headless converters install no overlay and never arm it.
     */
    @Nullable
    private static Debounce cursorHideDwell = null;

    /**
     * The pointer's screen position as of the last event treated as a real mouse move, held as
     * loose ints so the per-move comparison costs no allocation.
     * <p>
     * An event that lands on the pixel the pointer is already on is not a move:
     * {@link #restorePreviewElement} synthesizes MOUSE_MOVED at the current position for reasons
     * that have nothing to do with the user's hand, and AWT re-delivers every real move a second
     * time. Either would otherwise bring the cursor back and re-arm the dwell that is meant to be
     * taking it away.
     */
    private static int lastMouseScreenXPx;

    /** @see #lastMouseScreenXPx */
    private static int lastMouseScreenYPx;

    /** Whether {@link #lastMouseScreenXPx}/{@link #lastMouseScreenYPx} hold a real position yet. */
    private static boolean hasLastMouseScreenPoint;

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
    public void pasteModeDidChange(PasteModeDidChangeNotification message) {
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
     * Exactly one of each exists for the host's lifetime; none is ever recreated, only
     * retargeted.
     */
    public static void installOverlay(OverlayHost host) {
        discardCursorHideDwell();

        overlay = new PreviewElementOverlay(host);
        fallOverlay = new FallPreviewOverlay(host);
        glissandoOverlay = new GlissandoPreviewOverlay(host);
        graceGlissandoOverlay = new GraceGlissandoPreviewOverlay(host);

        for (var installed : installedOverlays()) {
            host.addOverlay(installed);
        }
    }

    /**
     * Rebuilds every hover-preview overlay's ink and bounds from the current tracking state,
     * hiding each one that no longer applies. Call whenever anything the renderers read has
     * changed — the preview element itself, its decorations, the staff position it sits at, or
     * the slide zone a slide tool is previewing. Each overlay's own gate decides whether it is
     * the one that should be visible, so a single call here keeps them all in sync without the
     * caller having to know which one currently applies.
     */
    public static void previewElementDidChange() {
        for (var installed : installedOverlays()) {
            installed.previewDidChange(currentPreviewLine);
        }
    }

    /**
     * The installed overlays, or an empty list before {@link #installOverlay} (headless
     * converters never install any). They are all created and cleared together, so the
     * all-or-nothing check covers each of them.
     */
    private static List<RecordedInkOverlay> installedOverlays() {
        if (overlay == null
            || fallOverlay == null
            || glissandoOverlay == null
            || graceGlissandoOverlay == null) {
            return List.of();
        }

        return List.of(overlay, fallOverlay, glissandoOverlay, graceGlissandoOverlay);
    }

    /**
     * Re-derives the insertion position from the real mouse location, then rebuilds the
     * overlays' ink for the newly-selected preview element type.
     * <p>
     * Call this instead of {@link #previewElementDidChange()} when the preview element's
     * identity changes (e.g. a toolbar tool selection, or the next-insertion element set up
     * after a click) rather than merely its decorations — otherwise the overlay re-anchors to
     * whatever {@link #currentXIndex}/{@link #currentStaffPosition} were last computed, which
     * can be stale relative to the line's current layout.
     * <p>
     * When called from inside an open modification bracket — the post-insertion case, where
     * {@link EditModeManager#previewElementDidChange} sets up the next preview element while
     * {@code line.withModification(...)} is still open — the current {@link LayoutResult} is
     * one insertion out of date (invalidated only when the bracket closes), so re-anchoring
     * here would land on the just-inserted element and fall back to an empty-line offset. The
     * re-anchor is deferred to {@link #applyPendingOverlayRestore}, run from {@link
     * #songDidChange} once the bracket has closed and layout can be made fresh again.
     */
    public static void previewElementTypeDidChange() {
        if (currentMouseLine != null && currentMouseLine.getScoreView().getSong().isModifying()) {
            pendingOverlayRestore = true;
            return;
        }

        restorePreviewElement(currentMouseLine);
        previewElementDidChange();
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
     * Re-anchors the preview overlay without rebuilding its ink. Only legal when the glyphs the
     * renderers would emit are unchanged and just the insertion X moved.
     */
    private static void previewElementDidMove() {
        if (overlay != null) {
            overlay.previewDidMove(currentPreviewLine);
        }

        // This is the path on which the grace-host glissando first appears. Entering the insert
        // phase hides the preview element (enterGraceNote) and then restores it
        // (enterGraceNoteInsert), which resets currentXIndex to -1 and re-enters trackMouse — so
        // the next move reports a changed x index while the configuration is unchanged, and lands
        // here. The visibility flag that restore flipped is not one of the fields
        // configurationChanged tracks, so nothing else would rebuild this overlay's ink.
        //
        // Its own X never moves while grace mode holds it (trackMouse pins the mouse X to the
        // locked insertion slot), so off this transition the rebuild is a no-op beyond a rejected
        // gate check.
        if (graceGlissandoOverlay != null) {
            graceGlissandoOverlay.previewDidChange(currentPreviewLine);
        }
    }

    /**
     * Restores the system cursor and retriggers the deadline that takes it away again.
     * <p>
     * Called from {@link #trackMouse} on every mouse move over a line, whether or not the preview
     * itself moved: the arrow comes back the instant the mouse moves, and each move pushes the
     * deadline out, so the hide can only land once the pointer has settled.
     * <p>
     * Driven from the mouse alone. Every other caller of {@link #previewElementDidChange()} (a
     * tool change, a song edit) rebuilds the ink without the pointer having moved, and the
     * cursor's visibility is not theirs to change.
     */
    private static void cursorDidMove(MouseEvent e) {
        var previewOverlay = overlay;

        if (previewOverlay == null) {
            return;
        }

        // Screen rather than component coordinates, so a move that scrolls the score under a
        // stationary pointer still reads as "not a move". Both are plain field reads on a real
        // AWT event — the peer fills them in at construction.
        var screenXPx = e.getXOnScreen();
        var screenYPx = e.getYOnScreen();

        if (hasLastMouseScreenPoint && screenXPx == lastMouseScreenXPx && screenYPx == lastMouseScreenYPx) {
            return;
        }

        hasLastMouseScreenPoint = true;
        lastMouseScreenXPx = screenXPx;
        lastMouseScreenYPx = screenYPx;
        previewOverlay.setHidesCursor(false);

        var dwell = cursorHideDwell;

        if (dwell == null) {
            dwell = Debounce.polling(
                CURSOR_HIDE_DWELL_MS,
                CURSOR_HIDE_POLL_MS,
                PreviewElementManager::hideCursorOverPreview
            );
            cursorHideDwell = dwell;
        }

        dwell.trigger();
    }

    /**
     * Hides the system cursor over the preview, if there is still a preview to obscure.
     * <p>
     * The visibility check covers a preview taken down between the deadline being armed and this
     * running: the mouse can leave the line, or anything can clear the preview element, while it
     * is pending. There would be nothing left to obscure, and the cursor would stay suppressed
     * until the next mouse move.
     */
    private static void hideCursorOverPreview() {
        var previewOverlay = overlay;

        if (previewOverlay == null || !previewOverlay.isVisible()) {
            return;
        }

        previewOverlay.setHidesCursor(true);

        // The Swing-level hide above is what every other platform acts on, but macOS applies a
        // cursor change only in a cursor-update context, so one made while the pointer is standing
        // still — which a dwell-driven hide always is — never reaches the screen. Ask AppKit
        // directly instead; it also restores the pointer on the next movement by itself, which is
        // the same rule cursorDidMove applies on the Swing side.
        if (SystemInfo.isMacOS) {
            MacCursor.hideUntilMouseMoves();
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
        var previewElement = activePreviewElementOn(lc);

        // A slide tool's placeholder has no note head at all; the slide preview components draw
        // for it instead.
        return previewElement != null
            && !isSlidePlaceholder(previewElement)
            && lc.isPreviewElementVisible();
    }

    /**
     * Returns whether the slide-tool hover preview for {@code zone} should be drawn on
     * {@code lc}. {@link SlidePreviewOverlay}'s two subclasses each call this with their own
     * zone, so at most one is ever visible.
     * <p>
     * The single home for "is this slide preview visible". The predicates live here, on the
     * manager, so the overlays call rather than re-assemble them.
     */
    static boolean shouldShowSlidePreviewOn(LineComponent lc, SlideZone zone) {
        if (currentPreviewLine != lc) {
            return false;
        }

        var previewElement = EditModeManager.getPreviewElement();

        if (!isSlidePlaceholder(previewElement) || !shouldShowSlidePreview() || getSlideZone() != zone) {
            return false;
        }

        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        var sourceIndex = currentXIndex - 1;

        return !sourceAlreadyHasSlide(line, sourceIndex, zone);
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
        previewElementDidChange();
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
            currentSlideZone = null;
        }

        // Unconditional: the mode-driven paths reach here with no preview line to clear, and the
        // overlay must still be taken down. currentPreviewLine is null by now, so this hides it.
        previewElementDidChange();

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

        // A slide tool never replaces an existing element either — it attaches a
        // slide to a note. Instead of the red replacement highlight, the connected
        // notes are drawn in the preview color (see isSlidePreviewNote).
        if (isSlidePlaceholder(previewElement)) {
            return null;
        }

        // A grace note may never be replaced, so it never shows the red replacement highlight.
        if (isGraceNoteAt(currentPreviewLine.getLine(), currentXIndex)) {
            return null;
        }

        return new ElementLocation(currentPreviewLine.getLineIndex(), currentXIndex);
    }

    /**
     * The notes a previewed slide would connect to, and the line they live on. A connecting
     * glissando highlights both the source note and the target note to its right; a fall
     * highlights only the source, so {@code targetIndex} is -1. {@link #NONE} means no preview.
     */
    public record SlidePreviewNotes(int lineIndex, int sourceIndex, int targetIndex) {
        public static final SlidePreviewNotes NONE = new SlidePreviewNotes(-1, -1, -1);

        /** Returns whether the element at {@code (atLineIndex, atElementIndex)} is highlighted. */
        public boolean highlights(int atLineIndex, int atElementIndex) {
            if (atLineIndex != lineIndex) {
                return false;
            }

            // sourceIndex/targetIndex are -1 when absent (a fall has no target; NONE has
            // neither). Guard so a negative query index never matches an absent endpoint.
            return (sourceIndex >= 0 && atElementIndex == sourceIndex)
                || (targetIndex >= 0 && atElementIndex == targetIndex);
        }
    }

    /**
     * Resolves which notes the currently previewed slide would connect to. Computed once so a
     * caller can reuse it across every element on a line instead of re-resolving per element.
     * <p>
     * Returns {@link SlidePreviewNotes#NONE} when no slide preview is being shown. The
     * conditions mirror those in {@link #shouldShowSlidePreviewOn}: the highlight is only shown
     * when the preview slide itself is drawn, which excludes the case where the source note
     * already carries this slide type.
     */
    public static SlidePreviewNotes getSlidePreviewNotes() {
        if (!shouldShowSlidePreview() || currentPreviewLine == null) {
            return SlidePreviewNotes.NONE;
        }

        // shouldShowSlidePreview() above already guarantees the zone is non-null.
        var type = currentSlideZone;
        var line = currentPreviewLine.getLine();
        var sourceIndex = currentXIndex - 1;

        if (line == null || sourceIndex < 0) {
            return SlidePreviewNotes.NONE;
        }

        // No preview line is drawn (and thus no highlight) when the source note already
        // carries this slide type.
        if (sourceAlreadyHasSlide(line, sourceIndex, type)) {
            return SlidePreviewNotes.NONE;
        }

        // A connecting glissando also highlights the target note immediately to the right.
        var targetIndex = type == SlideZone.GLISSANDO ? currentXIndex : -1;

        return new SlidePreviewNotes(currentPreviewLine.getLineIndex(), sourceIndex, targetIndex);
    }

    /**
     * Returns whether the note at {@code (lineIndex, elementIndex)} is one the currently previewed
     * slide would connect to. Prefer {@link #getSlidePreviewNotes()} when checking several
     * elements on the same line so the resolution runs only once.
     */
    public static boolean isSlidePreviewNote(int lineIndex, int elementIndex) {
        return getSlidePreviewNotes().highlights(lineIndex, elementIndex);
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
     * Returns whether a slide preview line should be drawn.
     * <p>
     * True when the mouse is in a valid slide zone (not over a note head, not to the
     * left of the first note). The preview is shown for both insertion and removal modes.
     */
    public static boolean shouldShowSlidePreview() {
        return currentSlideZone != null;
    }

    /**
     * Returns the current slide zone type, or null if no valid zone exists.
     */
    static @Nullable SlideZone getSlideZone() {
        return currentSlideZone;
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
     * Sets the current slide zone type (package-private for test setup).
     */
    static void setCurrentSlideZone(@Nullable SlideZone zone) {
        currentSlideZone = zone;
    }

    /**
     * Clears any queued first-note tempo prompt (package-private for test teardown, so a
     * prompt left pending by one test cannot leak into the next).
     */
    static void clearPendingTempoPrompt() {
        pendingTempoPrompt = null;
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
        return overlay;
    }

    /** Returns the installed fall-preview overlay, or null before {@link #installOverlay}. */
    static @Nullable FallPreviewOverlay getFallOverlay() {
        return fallOverlay;
    }

    /**
     * Returns the installed grace-host glissando-preview overlay, or null before
     * {@link #installOverlay}.
     */
    static @Nullable GraceGlissandoPreviewOverlay getGraceGlissandoOverlay() {
        return graceGlissandoOverlay;
    }

    /**
     * Clears the installed overlays (package-private for test teardown), so a later test's
     * {@link #installOverlay} starts from a clean slate instead of reusing a previous test's
     * overlay instances.
     */
    static void resetOverlaysForTest() {
        overlay = null;
        fallOverlay = null;
        glissandoOverlay = null;
        graceGlissandoOverlay = null;
        discardCursorHideDwell();
    }

    /**
     * Drops any pending cursor-hide dwell, along with the pointer position it was armed against.
     * <p>
     * Called wherever the overlays are replaced or cleared. A pending dwell resolves
     * {@link #overlay} when it fires rather than when it was armed, so one left outstanding
     * across a swap would act on whichever overlay happens to be installed by then — the next
     * test's, or the next converter host's. The remembered position has to go with it: leave it
     * behind and the first move afterwards can land on the same screen pixel, read as a
     * duplicate, and be ignored.
     */
    private static void discardCursorHideDwell() {
        if (cursorHideDwell != null) {
            cursorHideDwell.cancel();
            cursorHideDwell = null;
        }

        hasLastMouseScreenPoint = false;
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    /**
     * Returns whether the given element is a slide placeholder (the insertion
     * element created when a slide tool is selected).
     */
    static boolean isSlidePlaceholder(@Nullable StaffElement element) {
        return element != null && element.getType() == ElementType.SLIDE;
    }

    /**
     * Returns whether the element at {@code elementIndex} on {@code line} is a grace note.
     * Grace notes may never be replaced by clicking through them with another preview
     * element, so this gates both the ghost preview visibility and the click handler.
     */
    static boolean isGraceNoteAt(@Nullable Line line, int elementIndex) {
        return line != null
            && elementIndex >= 0
            && elementIndex < line.elementCount()
            && line.getElement(elementIndex).getType().isGraceNote();
    }

    /**
     * Returns whether the source note at {@code sourceIndex} on {@code line} already carries the
     * slide {@code zone} represents. When it does, no preview slide is drawn (and thus no preview
     * highlight) — the note already has what the tool would add. A null {@code zone} (no zone)
     * trivially matches nothing.
     */
    static boolean sourceAlreadyHasSlide(
        Line line, int sourceIndex, @Nullable SlideZone zone
    ) {
        return zone != null && zone.matches(line.getElement(sourceIndex));
    }

    /**
     * Returns the slide zone corresponding to the currently selected action,
     * or null if neither slide action is selected.
     */
    private static @Nullable SlideZone getSelectedSlideZone() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected instanceof ElementTypeAction eta) {
            return eta.getSlideZone();
        }

        return null;
    }

    /**
     * Computes the slide zone type for the given intended type.
     * <p>
     * Returns null if the mouse is to the left of the first note (no source note),
     * or if {@code intendedZone} is null. Otherwise validates whether the given zone
     * can be inserted at the given index: {@code GLISSANDO} requires a pitched note to the
     * right with a different pitch, {@code FALL} only requires a pitched source note.
     * Only pitched notes can be a slide source or target — bar lines, grace
     * notes, rests, and other non-pitched elements are rejected.
     *
     * @param line         The line containing the notes
     * @param xIndex       Insertion index from {@link LayoutResult#findInsertionIndex}
     * @param intendedZone The slide zone to validate, or null
     * @return The zone, or null if no valid zone
     */
    static @Nullable SlideZone computeSlideZone(
        Line line,
        int xIndex,
        @Nullable SlideZone intendedZone) {
        // xIndex=0 means to the left of the first note — no source note to draw from
        if (xIndex <= 0 || line.elementCount() == 0) {
            return null;
        }

        if (intendedZone == null) {
            return null;
        }

        // Only pitched notes can be a slide source or target
        var sourceElement = line.getElement(xIndex - 1);

        if (!sourceElement.getType().isPitchedNote()) {
            return null;
        }

        if (intendedZone == SlideZone.GLISSANDO) {
            // A connecting glissando requires a note to the right
            if (xIndex >= line.elementCount()) {
                return null;
            }

            var targetElement = line.getElement(xIndex);

            // Target must be a pitched note (bar lines, grace notes, rests, etc. are invalid)
            if (!targetElement.getType().isPitchedNote()) {
                return null;
            }

            // Same-pitch connected glissando is musically meaningless
            if (sourceElement.getPitch() == targetElement.getPitch()) {
                return null;
            }
        }

        return intendedZone;
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

        cursorDidMove(e);

        // Convert the view-pixel event coordinates to document pixels at this single choke
        // point. Both the real path (LineComponent.mouseMoved) and the synthetic path
        // (restorePreviewElement) funnel through here, so the ss math below stays on the
        // fixed document scale.
        var viewScale = lc.getViewScale();
        var mouseYss = ScaleContext.pxToSs(viewScale.toDocPx(new ViewPx(e.getY())).value());

        // In grace mode, lock the x-position to the host note slot
        var graceModeManager = EditModeManager.getGraceModeManager();
        var inGraceMode = graceModeManager.isInProgress();
        double mouseXss;

        if (inGraceMode) {
            mouseXss = graceModeManager.getLockedInsertionXSs();
        } else {
            mouseXss = ScaleContext.pxToSs(viewScale.toDocPx(new ViewPx(e.getX())).value());
        }

        currentMouseXSs = mouseXss;

        // Calculate Y position from mouse (in staff-space coordinates)
        var staffPosition = calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

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
            : layoutResult.findInsertionIndex(mouseXss, line);

        // In grace mode the locked x coincides with an existing note that will be
        // shifted (not replaced), so suppress the element-at-x match to avoid
        // painting it red as if it were the replacement target.
        var elementAtX = inGraceMode ? -1 : layoutResult.findElementAtXSs(mouseXss, line);

        // Suppress preview over the song's auto-maintained terminal (unless the
        // active preview element can legally replace it — exemption in isPositionBlockedByTerminal).
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, xIndex, elementAtX >= 0)) {
            clearPreviewElement();
            return;
        }

        var previewElement = EditModeManager.getPreviewElement();

        // Hide the preview element when a grace note is selected and the mouse
        // is between an existing grace/host pair — insertion there is not allowed.
        if (previewElement != null
            && previewElement.getType().isGraceNote()
            && line.isInsideGraceHostPair(xIndex)) {
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

        // Compute slide zone before change detection so zone changes trigger repaints.
        // Only compute when not over an element head (elementAtX < 0), as hovering over an
        // element head means there is no valid slide target to the left.
        SlideZone newSlideZone = null;

        if (isSlidePlaceholder(previewElement) && elementAtX < 0) {
            var intendedZone = getSelectedSlideZone();

            if (intendedZone != null) {
                newSlideZone = computeSlideZone(line, xIndex, intendedZone);
            }
        }

        // Split the six tracked fields by what they affect. The insertion index alone is
        // position: the glyphs the renderers would emit are identical, so the overlay only needs
        // a new translate. Everything else changes the drawn ink — the staff position flips the
        // stem direction and crosses the ledger-line threshold, the hover flags and the slide
        // zone change what is drawn at all — so the display list has to be rebuilt. Both are step
        // functions, so rebuilds stay rare even though the mouse moves continuously.
        var xIndexChanged = xIndex != currentXIndex;
        var configurationChanged = lc != currentPreviewLine
            || staffPosition != currentStaffPosition
            || newXMatch != xPosSsMatchesElement
            || newYMatch != yPosSpMatchesElement
            || newSlideZone != currentSlideZone;

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
        currentSlideZone = newSlideZone;

        if (isSlidePlaceholder(previewElement)) {
            // No note-head preview for slide tool — the slide overlays draw the preview line.
            // The overlay still has to be told, so it takes itself down when the tool changed
            // from a note to a slide while the preview was showing.
            previewElementDidChange();
            repaintHighlightedElements(previousPreviewLine);
            return;
        }

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
            previewElementDidChange();
        } else {
            previewElementDidMove();
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
     */
    public static void handleClick(LineComponent lc, boolean forceInsert) {
        if (!shouldHandlePreviewElement(lc)) {
            return;
        }

        // Only handle if this line has the preview element
        var line = lc.getLine();

        if (currentPreviewLine != lc || line == null) {
            return;
        }

        var previewElement = EditModeManager.getPreviewElement();

        if (isSlidePlaceholder(previewElement)) {
            var zone = currentSlideZone;

            if (zone == null) {
                return;  // No valid zone = click is a no-op
            }

            var noteIndex = currentXIndex - 1;

            if (zone == SlideZone.FALL
                    && !InsertionSpacingCalculator.hasRoomForFall(line, noteIndex, lc.getLyricRenderMetrics())) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_INSERT_ERROR,
                    Strings.ERROR_LINE_FULL_FALL
                );
                return;
            }

            var sourceNote = line.getElement(noteIndex);

            // A fall replaces the glissando (slides are mutually exclusive), which un-pairs
            // a grace note. Capture the pairing first — applyTo destroys the evidence.
            var wasPairedGraceNote = line.isPairedGraceNote(noteIndex);

            line.withModification(OpNames.addSlideLabel(zone == SlideZone.FALL), () -> {
                line.modifyElement(noteIndex, ElementField.SLIDE, () -> zone.applyTo(sourceNote));

                // Un-pairing dissolves the automatic melisma. Both elements survive, so
                // the syllable simply stays on the now-ordinary former grace note.
                if (wasPairedGraceNote) {
                    line.syncGraceHostMelisma(noteIndex);
                }
            });

            // The source note now already carries this slide, so sourceAlreadyHasSlide flips
            // the gate off — the overlay has to be told so it takes itself down immediately
            // rather than waiting for the next mouse move.
            previewElementDidChange();
            return;  // Stay in slide mode
        }

        // Belt-and-braces: block clicks that would try to insert past the auto-maintained
        // terminal. trackMouse already clears the preview at these positions.
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, currentXIndex, xPosSsMatchesElement)) {
            return;
        }

        // Route a direct click on the terminal to replaceTerminal when the active preview
        // element can legally replace it. This bypasses the normal insertion path entirely.
        if (xPosSsMatchesElement) {
            if (previewElement != null
                    && isDirectClickOnTerminal(song, line, currentXIndex)) {
                var previewType = previewElement.getType();

                if (song.canReplaceTerminal(previewType)) {
                    song.replaceTerminal(previewType);
                    return;
                }
            }
        }

        if (isBreathMarkInsertionBlocked(previewElement, currentXIndex, line, xPosSsMatchesElement)) {
            return;
        }

        // A grace note may never be replaced — ignore the click.
        if (xPosSsMatchesElement && isGraceNoteAt(line, currentXIndex)) {
            return;
        }

        // The song's initial tempo anchors on the first element of the first line — a
        // leading grace note keeps it. But when that first element is a grace note (an
        // ornament), prompting for the tempo makes sense only once the host note exists,
        // so the dialog is deferred until the first line gains its first pitched note.
        var isFirstLine = song.indexOfLine(line) == 0;
        var hadPitchedNoteBefore = isFirstLine && line.firstPitchedElement() != null;

        // shouldHandlePreviewElement (checked at entry) guarantees a preview element;
        // this guard proves that to the null-checker before deriving the op-name.
        if (previewElement == null) {
            return;
        }

        // Decided once and used both to ask and to act, so the question can never be asked about a
        // click that then does something else.
        var appends = currentXIndex == line.elementCount();
        var replacesExisting = !appends && !forceInsert && xPosSsMatchesElement;

        // Replacing a note takes its explicit accidental away, so this path asks the same question
        // every other removal path does — here, before the bracket opens, since a dialog must never
        // be open inside one. An append or an insert removes nothing and is never asked.
        var decision = replacesExisting
            ? confirmReplacementRestatements(lc, line, currentXIndex, previewElement)
            : AccidentalRestatements.Decision.PROCEED;

        if (decision.isCancelled()) {
            return;
        }

        // Determine action based on position. Wrap in a modification bracket so the
        // line.add/setElement calls inside actually accumulate mutations and fire a
        // SongDidChangeNotification, which the ScoreViewController uses to
        // invalidate the line's cached layout.
        line.withModification(OpNames.addLabel(previewElement.getType()), () -> {
            if (appends) {
                addPreviewElement(lc, line);
            } else if (replacesExisting) {
                modifyExistingElement(lc, currentXIndex, line, decision);
            } else {
                insertElement(lc, currentXIndex, line);
            }

            // Record the tempo anchor while the bracket is still open, but defer showing
            // the dialog to songDidChange (see showPendingTempoPrompt). The layout is only
            // invalidated when the outermost bracket closes, and grace mode nests this
            // insertion inside a larger bracket, so showing the dialog synchronously here
            // would render the note against a stale layout (at x = 0) behind the dialog.
            //
            // Gate on the first pitched note appearing (so a leading grace note alone does
            // not prompt), but anchor the tempo on the first element — the grace note keeps
            // the tempo, matching attachInitialTempoIfNeeded.
            if (isFirstLine && !hadPitchedNoteBefore && line.firstPitchedElement() != null) {
                pendingTempoPrompt = new PendingTempoPrompt(lc, line, line.getElement(0));
            }
        });
    }

    /**
     * Shows the tempo dialog for a pending first-note anchor, if any. Invoked from
     * {@link #songDidChange} once the outermost modification bracket has committed, so the
     * layout has been invalidated and can be recomputed to give the note its final
     * x-position before the modal dialog blocks the EDT.
     */
    static void showPendingTempoPrompt() {
        var prompt = pendingTempoPrompt;

        if (prompt == null) {
            return;
        }

        pendingTempoPrompt = null;

        // Recompute now: ScoreViewController's higher-priority handler has just invalidated
        // the layout, so ensureLayout positions the inserted note before the dialog appears.
        prompt.lineComponent().ensureLayout();
        TempoChangeDialog.showForElement(MainFrame.getInstance(), prompt.anchor(), prompt.line());
    }

    /**
     * Re-anchors the preview overlay for a pending click-to-insert, if any. Invoked from
     * {@link #songDidChange} once the outermost modification bracket has committed, so the
     * layout has been invalidated and can be recomputed to place the preview against the
     * just-inserted element rather than a stale {@link LayoutResult} that predates it.
     */
    static void applyPendingOverlayRestore() {
        if (!pendingOverlayRestore) {
            return;
        }

        pendingOverlayRestore = false;

        if (currentMouseLine == null) {
            return;
        }

        // Recompute now: ScoreViewController's higher-priority handler has just invalidated
        // the layout, so ensureLayout gives the just-inserted element its final position
        // before the preview is re-anchored against it.
        currentMouseLine.ensureLayout();
        restorePreviewElement(currentMouseLine);
        previewElementDidChange();
    }

    @Handler(priority = TEMPO_PROMPT_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        applyPendingOverlayRestore();
        showPendingTempoPrompt();
    }

    /**
     * Handles mouse entering a line. Sets up cursor and preview element visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;

        if (shouldHandlePreviewElement(lc)) {
            var previewElement = EditModeManager.getPreviewElement();

            if (!isSlidePlaceholder(previewElement)) {
                EditModeManager.setPreviewElementVisible(true);
            }
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
        previewElementDidChange();
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    /**
     * Returns {@code true} when {@code xIndex} points to the auto-maintained terminal
     * element on {@code line}.
     */
    private static boolean isDirectClickOnTerminal(Song song, Line line, int xIndex) {
        return xIndex < line.elementCount()
            && song.isAutoMaintainedTerminal(line.getElement(xIndex), line);
    }

    /**
     * Returns {@code true} when the given mouse position should be blocked because
     * it coincides with the song's auto-maintained terminal element.
     * Checks both "mouse is on the terminal element" and "mouse is at the
     * append position immediately after the terminal".
     * <p>
     * When the mouse is directly on the terminal and the active preview element can
     * legally replace it, the block is lifted so the user can see the ghost preview.
     */
    private static boolean isPositionBlockedByTerminal(
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
        if (!precedingType.isDuration() || precedingElement.hasGlissando()) {
            return true;
        }

        return xIndex < line.effectiveElementCount() && line.getElement(xIndex).getType().isBreathMark();
    }

    /**
     * Returns whether preview element handling should be active for the given line.
     * <p>
     * Requires: NOTE_EDIT mode and a preview element set.
     */
    private static boolean shouldHandlePreviewElement(LineComponent lc) {
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
     * @param mouseYss      Mouse Y coordinate in staff-space units
     * @param middleLineYSs Y coordinate of the middle staff line in staff-space units
     * @return Staff position
     */
    static int calculateStaffPositionFromMouse(double mouseYss, double middleLineYSs) {
        return Staff.ssToSp(mouseYss - middleLineYSs);
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

    // ==========================================================================
    // Element Mutation Methods
    // ==========================================================================

    /**
     * Calculates the insertion result for adding an element to a line.
     * <p>
     * The line is free to compress to absorb the new element, so the error fires only when the
     * spring solver reports the line INFEASIBLE — it overflows the margin even with every gap
     * squeezed down to its collision floor. This runs <em>before</em> any mutation, so a rejected
     * insert leaves the line exactly as it was; there is nothing half-applied to compensate for.
     *
     * @param lc      The LineComponent whose line is being inserted into
     * @param line    The line to insert into
     * @param element The element to insert
     * @param index   The insertion index
     * @param layout  Layout result for position lookup; null falls back to {@code xOffset}
     * @return The insertion result, or null if the line is full
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult calculateInsertionOrShowError(
        LineComponent lc, Line line, StaffElement element, int index, @Nullable LayoutResult layout
    ) {
        var insertion = InsertionSpacingCalculator.calculateInsertion(
            line, element, index, layout, lc.getLyricRenderMetrics());
        var song = line.getSong();

        if (!insertion.fitsWithinLine(song.getLineWidthSs())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                element.getType().categoryName()
            );
            return null;
        }

        return insertion;
    }

    /**
     * Reconciles the accidentals this insertion changes, applies them, and returns the insertion
     * result — or null when the insertion is refused, in which case nothing has been mutated.
     * <p>
     * The inserted element itself is never materialized: it is a note the user is creating, so it
     * has no pitch it "had" and the invariant (every note keeps the pitch it had, unless the user
     * changed that note) does not reach it. That is what the empty prior-accidental list below
     * says — {@link AccidentalReconciliation} reads an empty list as "no source context, never
     * materialize these elements themselves". What does need reconciling is the other direction:
     * an element arriving with an explicit accidental changes the context reaching the following
     * notes at its staff position — and so does a barline or repeat, which carries no accidental of
     * its own but cancels every accidental before it.
     *
     * @param lc             The LineComponent whose line is being inserted into
     * @param line           The line to insert into
     * @param previewElement The element being inserted
     * @param index          The insertion index
     * @param confirmed      Any further confirmation the caller requires, run only once the
     *                       element is known to fit; false means the insertion is refused
     * @return The insertion result, or null when refused
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult materializeAndCalculateInsertion(
        LineComponent lc, Line line, StaffElement previewElement, int index, BooleanSupplier confirmed) {

        // Reconcile unconditionally: an element with no explicit accidental of its own can still
        // change the context reaching the notes after it (a barline or repeat cancels every prior
        // accidental), so "no accidental" never meant "nothing to reconcile". This pass is cheap —
        // one line's elements, once per insertion — so it is not worth guessing when to skip it.
        var accidentalChanges = AccidentalReconciliation.reconcile(new AccidentalReconciliation.InsertionRegion(
            line, index, null, List.of(previewElement), List.of(), List.of()));

        // The gate runs inside the materializer, with the accidentals applied so the projection
        // measures the right widths, so its result has to escape the lambda through a holder.
        var insertion = new InsertionSpacingCalculator.InsertionResult[1];

        var accepted = AccidentalMaterializer.applyIfAccepted(
            line, accidentalChanges, List.of(previewElement), () -> {
                var result = calculateInsertionOrShowError(lc, line, previewElement, index, lc.getLayoutResult());

                if ((result == null) || !confirmed.getAsBoolean()) {
                    return false;
                }

                insertion[0] = result;
                return true;
            });

        return accepted ? insertion[0] : null;
    }

    /**
     * Adds a preview element to the end of the line.
     *
     * @param lc   The LineComponent
     * @param line The line to add the element to
     */
    private static void addPreviewElement(LineComponent lc, Line line) {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return;
        }

        var elementCount = line.elementCount();

        if (EditModeManager.elementWasModified(line, elementCount)) {
            // A stale-preview bailout, not the main insertion path: elementWasModified's
            // REPEAT_LEFT/RIGHT merge case aside, this branch does not add the element the
            // rest of this method would have inserted, so there is no guarantee a
            // SongDidChangeNotification follows to drive a deferred setup — call synchronously.
            EditModeManager.previewElementDidChange(line, elementCount - 1);
            return;
        }

        // Appending needs no further confirmation: nothing follows the new element, so it can
        // invalidate no ending.
        var insertion = materializeAndCalculateInsertion(lc, line, previewElement, elementCount, () -> true);

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.ssToRoundedPx(insertion.insertedElementXSs()));
        line.addElement(previewElement);

        var newLastIndex = line.elementCount() - 1;
        applyAutomaticBeaming(line, newLastIndex);
        EditModeManager.previewElementDidChange(line, newLastIndex);
    }

    @Nullable
    private static StaffElement validateAndGetPreviewElement(LineComponent lc, Line line, int elementIndex) {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        if (EditModeManager.elementWasModified(line, elementIndex)) {
            // Same reasoning as addPreviewElement's identical guard: no guaranteed
            // SongDidChangeNotification follows this bailout, so call synchronously.
            EditModeManager.previewElementDidChange(line, elementIndex);
            return null;
        }

        return previewElement;
    }

    private static void insertElement(LineComponent lc, int xIndex, Line line) {
        var previewElement = validateAndGetPreviewElement(lc, line, xIndex);

        if (previewElement == null) {
            return;
        }

        // If inserting into a tuplet, remove it — the new element changes the rhythmic grouping.
        var tuplet = line.findTupletAt(xIndex - 1);

        if ((tuplet != null) && ((xIndex - 1) < tuplet.getEndElementIndex())) {
            line.removeTuplet(tuplet);
        }

        // The ending confirm runs inside the insertion gate: declining it must leave the line
        // exactly as it was, on the same terms as a refusal for want of room.
        var insertion = materializeAndCalculateInsertion(lc, line, previewElement, xIndex,
            () -> !line.hasEndingInvalidatedByInsertion(xIndex, previewElement.getType())
                || EndingConfirms.confirmInvalidation(lc));

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.ssToRoundedPx(insertion.insertedElementXSs()));

        line.adjustSyllablesForNeighborChange(xIndex - 1, null);
        line.adjustExtendsForInsertion(xIndex);
        line.addElement(xIndex, previewElement);
        line.adjustSyllablesForSuccessorAfterInsertion(xIndex);

        // A connecting glissando joins a note to the note that immediately follows it.
        // Inserting another pitched note simply re-targets it, but inserting anything else
        // (rest, breath mark, grace note) leaves it with no valid target, so remove it from
        // the preceding note.
        if (!previewElement.getType().isPitchedNote() && xIndex > 0) {
            var precedingElement = line.getElement(xIndex - 1);

            if (precedingElement.hasGlissando()) {
                precedingElement.removeSlide();
            }
        }

        // A pitched insertion between a grace note and its host re-targets the glissando, so
        // the inserted element is the new host. adjustExtendsForInsertion removed the melisma
        // that pointed at the old host — carrier and all, leaving it an ordinary note free to
        // take a syllable of its own — so re-establish the melisma against the new host. The
        // non-pitched case fell through the glissando strip above and no longer reads as paired.
        if (line.isPairedGraceNote(xIndex - 1)) {
            line.syncGraceHostMelisma(xIndex - 1);
        }

        var shift = ScaleContext.ssToRoundedPx(insertion.shiftForSubsequentElementsSs());

        for (var i = xIndex + 1; i < line.effectiveElementCount(); i++) {
            var element = line.getElement(i);
            element.setXOffsetPx(element.getXOffsetPx() + shift);
        }

        applyAutomaticBeaming(line, xIndex);

        EditModeManager.previewElementDidChange(line, xIndex);
    }

    /**
     * Applies automatic beaming for the element at the given index.
     * Scans backward from the element to find beamable neighbors and creates
     * a beam span if the rhythmic grouping conditions are met.
     *
     * @param line         The line containing the element
     * @param elementIndex The index of the just-inserted element
     */
    private static void applyAutomaticBeaming(Line line, int elementIndex) {
        var element = line.getElement(elementIndex);
        var insertedType = element.getType();

        if (
            !insertedType.isBeamable() ||
                (elementIndex < 1) ||
                (line.findTupletAt(elementIndex - 1) != null)
        ) {
            return;
        }

        var sum = 0;

        for (var i = elementIndex - 1; i >= 0; i--) {
            var elementType = line.getElement(i).getType();

            if (elementType == ElementType.QUAVER) {
                sum += 2;
            } else if (
                (elementType == ElementType.SEMIQUAVER) ||
                    (elementType == ElementType.DEMI_SEMIQUAVER)
            ) {
                sum += 1;
            } else {
                break;
            }

            var beam = line.findBeamAt(i);

            if ((beam != null) && (beam.getAnchorElementIndex() == i)) {
                break;
            }
        }

        if (
            ((insertedType == ElementType.QUAVER) &&
                (sum > 0) &&
                ((sum % 2) == 0) &&
                ((sum % 4) != 0)) ||
                (((insertedType == ElementType.SEMIQUAVER) ||
                    (insertedType == ElementType.DEMI_SEMIQUAVER)) &&
                    (sum > 0) &&
                    ((sum % 4) != 0))
        ) {
            line.addBeaming(new Beam(
                line.getElement(elementIndex - 1),
                line.getElement(elementIndex)
            ));
        }
    }

    /**
     * Asks whether replacing the element at {@code elementIndex} should also take away the later
     * notes that restate the accidental it removes.
     *
     * <p>The replacement carries the old accidental's staff position forward only when it lands at
     * that same position as a pitched note — a replacement that is not a pitched note bears no
     * accidental at all, whatever the preview holds, since applying the staff position snaps it to
     * its default place too. Anything else gives the old accidental up.
     *
     * <p>Must be called before the modification bracket opens, and its answer honored — Cancel
     * means the click does nothing at all.
     */
    private static AccidentalRestatements.Decision confirmReplacementRestatements(
        LineComponent lc, Line line, int elementIndex, StaffElement previewElement) {

        var existing = line.getElement(elementIndex);
        var staffPosition = existing.getStaffPosition();
        var landsAtSamePosition = previewElement.getType().isPitchedNote()
            && (currentStaffPosition == staffPosition);

        return AccidentalRestatements.confirm(
            lc,
            line,
            List.of(new AccidentalRestatements.EditedNote(
                elementIndex,
                staffPosition,
                existing.getAccidental(),
                landsAtSamePosition ? previewElement.getAccidental() : null)));
    }

    /**
     * Replaces an existing element with the current preview element's type and pitch.
     * Note-entry decorations the user sets on the preview element via the toolbar/menu
     * (accidental, dot count, articulations) are taken from the preview. Other
     * decorations not settable on the preview (trill, annotation, tempo/beat change,
     * dynamic attachments, lyrics, fermata, x position) are preserved from the existing element.
     * Called when the user clicks on an existing element head with the preview element active.
     *
     * @param lc           The LineComponent
     * @param elementIndex The index of the element to replace
     * @param line         The line containing the element
     * @param decision     The restatements the user accepted for this replacement, from
     *                     {@link #confirmReplacementRestatements}
     */
    private static void modifyExistingElement(
        LineComponent lc, int elementIndex, Line line, AccidentalRestatements.Decision decision) {

        var previewElement = validateAndGetPreviewElement(lc, line, elementIndex);

        if (previewElement == null) {
            return;
        }

        // Deep-copy the existing element under the new type to carry over all decorations
        // (fermata, trill, annotation, tempo/beat change, articulations, attachments, lyrics,
        // x position), then override with the preview's note-entry attributes.
        var existing = line.getElement(elementIndex);
        var previewType = previewElement.getType();
        var replacement = new StaffElement(previewType, existing);
        replacement.setDotCount(previewElement.getDotCount());
        replacement.setAccidental(previewElement.getAccidental());
        replacement.setAccidentalInParentheses(previewElement.isAccidentalInParentheses());
        replacement.setStemDirectionAuto(previewElement.isStemDirectionAuto());

        // Articulations are a note-entry decoration the user sets on the preview element via the
        // toolbar/menu, exactly like the accidental above. Carry them over from the preview,
        // overriding any inherited from the existing element. Other attachments (dynamics,
        // annotations, trills, fermata) are not preview-settable, so they remain copied from
        // the existing element by the copy constructor.
        replacement.copyArticulationsFrom(previewElement);

        // Rests snap to their default staff position; pitched notes use the mouse Y position
        applyStaffPosition(replacement, currentStaffPosition);

        if (replacement.isStemDirectionAuto()) {
            replacement.setDirection(StaffElement.defaultDirection(replacement));
        } else {
            replacement.setDirection(previewElement.getDirection());
        }

        // Remove all beam spans touching this element — the new element type may differ
        var beam = line.findBeamAt(elementIndex);

        while (beam != null) {
            line.removeBeaming(beam);
            beam = line.findBeamAt(elementIndex);
        }

        // Remove all tie range elements touching this element
        var tie = line.findTieAt(elementIndex);

        while (tie != null) {
            line.removeTie(tie);
            tie = line.findTieAt(elementIndex);
        }

        // Remove any containing tuplet if the duration type or dot count changes —
        // the replacement would make the tuplet rhythmically invalid.
        if (existing.getType() != previewType
                || existing.getDotCount() != replacement.getDotCount()) {
            line.removeOverlappingTuplets(elementIndex, elementIndex);
        }

        // Check whether replacing this element would affect a first-second ending,
        // and show the appropriate confirmation dialog if so.
        var endingEffect = LineEndingSupport.findEndingReplacementEffect(line, elementIndex, replacement);

        switch (endingEffect) {
            case Ending.EndingEffect.Invalidate _ -> {
                if (!EndingConfirms.confirmInvalidation(lc)) {
                    return;
                }
                // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
            }
            case Ending.EndingEffect.CompensateEnd ce -> {
                if (!EndingConfirms.confirmCompensateEnd(lc, ce)) {
                    return;
                }
                EndingConfirms.applyCompensatingEndChange(line, ce);
            }
            case Ending.EndingEffect.CompensateSplit cs -> {
                if (!EndingConfirms.confirmCompensateSplit(lc, cs, previewType)) {
                    return;
                }
                EndingConfirms.applyCompensatingSplitChange(line, cs);
            }
            case Ending.EndingEffect.None _ -> {}
        }

        // Every bail-out is behind us, so the replacement is going to happen: record the accepted
        // restatements in the same bracket, making them and the replacement one undo step. Ahead of
        // setElement so the scan still reads the pre-replacement line, and because undo replays a
        // step in reverse — the accidentals come back last, once the old element is back.
        AccidentalRestatements.commitAllLines(decision);

        // Replace the element entirely (line.setElement marks the song modified)
        line.setElement(elementIndex, replacement);

        applyAutomaticBeaming(line, elementIndex);

        // Also check the element after the replaced one: when elementIndex is the start of a beam,
        // applyAutomaticBeaming only scans backward and misses the forward neighbor.
        if (elementIndex + 1 < line.elementCount()) {
            applyAutomaticBeaming(line, elementIndex + 1);
        }

        // Grace note cleanup: if the preceding element is a paired grace note (grace + connected
        // glissando to the replaced host), and the replacement is not a pitched note, remove the
        // grace note. For pitched note replacements the glissando reattaches automatically since
        // setElement preserves the element index.
        if (line.isHostOfPairedGraceNote(elementIndex)
                && !previewType.isPitchedNote()) {
            var graceNoteIndex = elementIndex - 1;
            var graceNote = line.getElement(graceNoteIndex);

            // setElement carried the old host's lyrics onto the replacement, which is not a
            // pitched note and has no business holding a melisma carrier. Strip the glissando
            // first so the sync sees an unpaired grace and converges to teardown, removing the
            // carrier outright instead of leaving an empty lyric behind on the replacement.
            // No hand-back here: the host the syllable would return to no longer exists.
            line.modifyElement(graceNoteIndex, ElementField.SLIDE, graceNote::removeSlide);
            line.syncGraceHostMelisma(graceNoteIndex);

            // Any melisma reaching past this pair still has to be unwound before the grace
            // note leaves the line, and the removal must be tracked for undo.
            line.adjustExtendsForDeletion(graceNoteIndex);
            line.removeElement(graceNoteIndex);
            elementIndex--;
        }

        EditModeManager.previewElementDidChange(line, elementIndex);
    }
}
